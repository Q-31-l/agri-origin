package com.agri.trace.sdk;

import cn.hutool.core.io.IoUtil;
import com.agri.trace.config.ChainProperties;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.transaction.model.dto.TransactionResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FISCO-BCOS 客户端封装（全系统唯一的链交互出口）
 *
 * - 启动时加载 SDK 配置、证书、ABI；
 * - 提供 sendTransaction / call / checkReceipt 三个原子能力；
 * - 链不可用时给出明确的 initError，绝不静默成功。
 */
@Slf4j
@Component
public class BcosClient {

    @Resource
    private ChainProperties properties;

    private BcosSDK bcosSDK;
    private Client client;
    private AssembleTransactionProcessor transactionProcessor;
    private CryptoKeyPair keyPair;
    private String abi;
    private String contractAddress;
    private volatile boolean ready = false;
    private volatile String initError = "链客户端未初始化";

    /** 两次重连尝试之间的最小间隔，避免节点宕机期间每个请求都去重建 SDK */
    private static final long RETRY_COOLDOWN_MS = 10_000L;
    /** 健康探测缓存时间：3 秒内的重复调用不再打网络 */
    private static final long HEALTH_TTL_MS = 3_000L;

    private volatile long lastAttemptAt = 0L;
    private volatile long lastHealthAt = 0L;
    private volatile boolean healthy = false;
    private int attempts = 0;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            initError = "链上存证未启用（chain.enabled=false，演示模式）";
            log.warn("{}，业务数据将标记为 SKIPPED", initError);
            return;
        }
        tryInit();
    }

    /**
     * 建立 / 重建 SDK 连接。
     *
     * <p>节点启动顺序不确定：后端可能先于链节点起来，或链节点在运行期重启。
     * 因此不能"初始化一次失败就永久不可用"，改为按需重连（带冷却）——
     * 链恢复后第一个业务请求会自动把连接建起来，无需重启后端进程。
     */
    private synchronized void tryInit() {
        if (ready) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttemptAt < RETRY_COOLDOWN_MS) {
            return;
        }
        lastAttemptAt = now;
        attempts++;
        BcosSDK previous = bcosSDK;
        try {
            String configPath = prepareConfigFile();
            bcosSDK = BcosSDK.build(configPath);
            client = bcosSDK.getClient(properties.getGroupId());

            keyPair = client.getCryptoSuite().getCryptoKeyPair();
            if (properties.getPrivateKey() != null && !properties.getPrivateKey().trim().isEmpty()) {
                keyPair = client.getCryptoSuite().createKeyPair(properties.getPrivateKey().trim());
            }
            transactionProcessor = TransactionProcessorFactory
                    .createAssembleTransactionProcessor(client, keyPair);

            this.abi = readAbi();
            this.contractAddress = properties.getContractAddress();
            if (contractAddress == null || !contractAddress.startsWith("0x")) {
                throw new IllegalStateException("chain.contract-address 未配置或格式错误");
            }
            ready = true;
            initError = null;
            healthy = true;
            lastHealthAt = System.currentTimeMillis();
            log.info("FISCO-BCOS 客户端初始化成功：group={}, contract={}, block={}",
                    properties.getGroupId(), contractAddress, client.getBlockNumber().getBlockNumber());
            if (previous != null) {
                try {
                    previous.stopAll();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            ready = false;
            initError = "链客户端初始化失败：" + e.getMessage();
            // 首次尝试用 error（便于排查启动期故障），后续重连降为 warn，避免刷屏
            if (attempts == 1) {
                log.error(initError, e);
            } else {
                log.warn("链客户端重连失败，{} 秒后再试：{}", RETRY_COOLDOWN_MS / 1000, e.getMessage());
            }
            try {
                if (bcosSDK != null) {
                    bcosSDK.stopAll();
                }
            } catch (Exception ignored) {
            }
            bcosSDK = null;
            client = null;
            transactionProcessor = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (bcosSDK != null) {
            try {
                bcosSDK.stopAll();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 是否可用。
     *
     * <p>不能只看 ready 这个"曾经连上过"的布尔值：节点宕机时 SDK 的连接对象还在，
     * ready 仍为 true，接口会显示"链正常"而实际交易全部超时。因此这里做一次
     * 带缓存的健康探测（默认 3 秒一次），探测失败即标记不可用，下个请求触发重连。
     */
    public boolean isReady() {
        if (!properties.isEnabled()) {
            return false;
        }
        if (!ensureInitialized()) {
            return false;
        }
        return probeHealth();
    }

    /** 仅保证客户端已构建（不探测链路），供健康探测自身与区块高度查询复用，避免递归 */
    private boolean ensureInitialized() {
        if (!ready) {
            tryInit();
        }
        return ready;
    }

    private boolean probeHealth() {
        long now = System.currentTimeMillis();
        if (now - lastHealthAt < HEALTH_TTL_MS) {
            return healthy;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - lastHealthAt < HEALTH_TTL_MS) {
                return healthy;
            }
            try {
                client.getBlockNumber();
                healthy = true;
            } catch (Exception e) {
                healthy = false;
                ready = false;
                initError = "链节点连接异常：" + e.getMessage();
                log.warn("链健康探测失败，标记为不可用，等待自动重连：{}", e.getMessage());
            }
            lastHealthAt = System.currentTimeMillis();
            return healthy;
        }
    }

    public String getInitError() {
        if (properties.isEnabled() && !ready) {
            tryInit();
        }
        return initError;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public Long getBlockNumber() {
        if (!properties.isEnabled() || !ensureInitialized()) {
            return null;
        }
        try {
            return client.getBlockNumber().getBlockNumber().longValue();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 发送写交易并校验回执状态
     */
    public ChainResult sendTransaction(String function, List<Object> params) {
        ensureReady();
        try {
            TransactionResponse response = transactionProcessor
                    .sendTransactionAndGetResponse(contractAddress, abi, function, params);
            if (response == null || response.getReturnCode() != 0) {
                String msg = response == null ? "无响应" : String.valueOf(response.getReturnMessage());
                throw new ChainException("交易执行失败：" + msg);
            }
            TransactionReceipt receipt = response.getTransactionReceipt();
            if (receipt == null) {
                throw new ChainException("未获取到交易回执");
            }
            if (!receipt.isStatusOK()) {
                throw new ChainException("交易回执状态异常：" + receipt.getStatus());
            }
            return ChainResult.success(receipt.getTransactionHash(),
                    parseLong(receipt.getBlockNumber()), parseLong(receipt.getGasUsed()));
        } catch (ChainException e) {
            throw e;
        } catch (Exception e) {
            throw new ChainException("链上交易异常：" + e.getMessage(), e);
        }
    }

    /**
     * 只读调用
     */
    public List<Object> call(String function, List<Object> params) {
        ensureReady();
        try {
            CallResponse response = transactionProcessor.sendCall(keyPair.getAddress(),
                    contractAddress, abi, function, params);
            if (response == null || response.getReturnCode() != 0) {
                throw new ChainException("链上查询失败：" + (response == null ? "无响应" : response.getReturnMessage()));
            }
            return response.getReturnObject() == null ? new ArrayList<>() : response.getReturnObject();
        } catch (ChainException e) {
            throw e;
        } catch (Exception e) {
            throw new ChainException("链上查询异常：" + e.getMessage(), e);
        }
    }

    /**
     * 校验交易回执是否成功（一致性校验用）
     */
    public boolean checkReceipt(String txHash) {
        if (txHash == null || !isReady() || client == null) {
            return false;
        }
        try {
            return client.getTransactionReceipt(txHash)
                    .getTransactionReceipt()
                    .map(TransactionReceipt::isStatusOK)
                    .orElse(false);
        } catch (Exception e) {
            log.warn("回执查询失败: tx={}, err={}", txHash, e.getMessage());
            return false;
        }
    }

    private void ensureReady() {
        if (!isReady()) {
            throw new ChainException(initError == null ? "链服务不可用" : initError);
        }
    }

    private Long parseLong(String hex) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        try {
            String v = hex.startsWith("0x") ? hex.substring(2) : hex;
            return new BigInteger(v, 16).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String readAbi() throws IOException {
        String path = properties.getAbiPath();
        if (path != null && path.startsWith("classpath:")) {
            ClassPathResource resource = new ClassPathResource(path.substring("classpath:".length()));
            // 打成 jar 后 classpath 资源没有真实文件路径，getFile() 会抛 FileNotFoundException，
            // 必须走 getInputStream()
            try (InputStream in = resource.getInputStream()) {
                return IoUtil.read(in, StandardCharsets.UTF_8);
            }
        }
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    /**
     * classpath 配置在打包为 jar 后无法直接交给 SDK，先释放到工作目录并改写证书绝对路径
     */
    private String prepareConfigFile() throws IOException {
        String configFile = properties.getConfigFile();
        if (configFile == null || configFile.trim().isEmpty()) {
            throw new IOException("chain.config-file 未配置");
        }
        if (!configFile.startsWith("classpath:")) {
            return configFile;
        }
        Path baseDir = Paths.get(System.getProperty("user.dir"), "chain-conf");
        Path confDir = baseDir.resolve("conf");
        Files.createDirectories(confDir);

        Path target = baseDir.resolve("config.toml");
        copyResource("fisco/config.toml", target);

        List<String> certs = Arrays.asList("ca.crt", "sdk.crt", "sdk.key");
        for (String cert : certs) {
            copyResource("fisco/conf/" + cert, confDir.resolve(cert));
        }

        String absCertPath = confDir.toAbsolutePath().toString().replace("\\", "/");
        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("certPath")) {
                lines.set(i, "certPath = \"" + absCertPath + "\"");
            }
        }
        Files.write(target, lines, StandardCharsets.UTF_8);
        return target.toAbsolutePath().toString();
    }

    private void copyResource(String resourcePath, Path target) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("缺少链证书文件：" + resourcePath
                    + "（请从节点 sdk 目录拷贝 ca.crt / sdk.crt / sdk.key 到 resources/fisco/conf/）");
        }
        // 同理：jar 内资源只能通过流复制
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
