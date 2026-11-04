package com.agri.trace.sdk;

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
import java.io.File;
import java.io.IOException;
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

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            initError = "链上存证未启用（chain.enabled=false，演示模式）";
            log.warn("{}，业务数据将标记为 SKIPPED", initError);
            return;
        }
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
            log.info("FISCO-BCOS 客户端初始化成功：group={}, contract={}, block={}",
                    properties.getGroupId(), contractAddress, client.getBlockNumber());
        } catch (Exception e) {
            ready = false;
            initError = "链客户端初始化失败：" + e.getMessage();
            log.error(initError, e);
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

    public boolean isReady() {
        return ready;
    }

    public String getInitError() {
        return initError;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public Long getBlockNumber() {
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
        if (!ready || txHash == null) {
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
        if (!ready) {
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
            return new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
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
        File file = resource.getFile();
        Files.copy(file.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
