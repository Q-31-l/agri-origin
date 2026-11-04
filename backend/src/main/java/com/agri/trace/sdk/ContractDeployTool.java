package com.agri.trace.sdk;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.transaction.model.dto.TransactionResponse;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合约部署工具（命令行入口）
 *
 * <p>用法（在仓库根目录、已执行 mvn package 之后）：</p>
 * <pre>
 * java -cp backend/target/agri-trace-backend.jar \
 *      -Dloader.main=com.agri.trace.sdk.ContractDeployTool \
 *      org.springframework.boot.loader.PropertiesLauncher
 * </pre>
 *
 * <p>部署成功后把输出的合约地址写入 application.yml 的 chain.contract-address，
 * 并把 abi/bin 产物同步到 backend/src/main/resources/contracts/。</p>
 *
 * <p>参数（可选，覆盖 application.yml 配置）：<br>
 * --config-file  SDK 配置文件路径（toml）<br>
 * --group-id     群组编号，默认 1<br>
 * --abi          ABI 文件路径，默认 classpath:contracts/Traceability.abi<br>
 * --bin          BIN 文件路径，默认 chain-conf/Traceability.bin<br>
 * --private-key  部署账户私钥（十六进制，不带头部 0x）<br>
 * </p>
 */
@Slf4j
public class ContractDeployTool {

    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);
        try {
            Map<String, Object> chain = loadChainConfig();
            boolean enabled = Boolean.parseBoolean(String.valueOf(chain.getOrDefault("enabled", true)));
            if (!enabled) {
                System.err.println("[FAIL] application.yml 中 chain.enabled=false，无法部署合约。"
                        + "请先接入 FISCO-BCOS 节点并开启 chain.enabled。");
                System.exit(2);
            }

            Integer groupId = Integer.valueOf(params.getOrDefault("group-id",
                    String.valueOf(chain.getOrDefault("group-id", 1))));
            String configPath = params.get("config-file");
            if (configPath == null) {
                configPath = Boolean.parseBoolean(String.valueOf(chain.getOrDefault("extract-classpath-config", true)))
                        ? extractClasspathConfig()
                        : String.valueOf(chain.get("config-file"));
            }
            String abiPath = params.getOrDefault("abi", String.valueOf(chain.get("abi-path")));
            String binPath = params.getOrDefault("bin", "chain-conf/Traceability.bin");
            String privateKey = params.getOrDefault("private-key", String.valueOf(chain.get("private-key")));

            String abi = readText(abiPath);
            String bin = readText(binPath);
            if (bin.startsWith("0x")) {
                bin = bin.substring(2);
            }

            System.out.println(">>> 连接节点：config=" + configPath + ", group=" + groupId);
            BcosSDK sdk = BcosSDK.build(configPath);
            Client client = sdk.getClient(groupId);

            CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
            if (privateKey != null && !privateKey.trim().isEmpty() && !"null".equals(privateKey)) {
                keyPair = client.getCryptoSuite().createKeyPair(privateKey.trim());
            }

            AssembleTransactionProcessor processor =
                    TransactionProcessorFactory.createAssembleTransactionProcessor(client, keyPair);

            System.out.println(">>> 部署账户：" + keyPair.getAddress());
            TransactionResponse response = processor.deployAndGetResponse(abi, bin);
            if (response == null || response.getReturnCode() != 0) {
                System.err.println("[FAIL] 部署失败：" + (response == null ? "无响应" : response.getReturnMessage()));
                System.exit(3);
            }
            TransactionReceipt receipt = response.getTransactionReceipt();
            if (receipt == null || !receipt.isStatusOK()) {
                System.err.println("[FAIL] 部署回执异常：" + (receipt == null ? "无回执" : receipt.getStatus()));
                System.exit(4);
            }

            String contractAddress = response.getContractAddress();
            System.out.println("=================================================");
            System.out.println(" 合约部署成功");
            System.out.println(" 合约地址 : " + contractAddress);
            System.out.println(" 交易哈希 : " + receipt.getTransactionHash());
            System.out.println(" 区块高度 : " + receipt.getBlockNumber());
            System.out.println("=================================================");
            System.out.println("请将 chain.contract-address 更新为：" + contractAddress);

            sdk.stopAll();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[FAIL] 部署异常：" + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadChainConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            Object chain = root == null ? null : root.get("chain");
            if (chain instanceof Map) {
                result.putAll((Map<String, Object>) chain);
            }
        } catch (Exception e) {
            log.warn("读取 application.yml 失败，使用默认参数：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 从 classpath 释放 SDK 配置与证书（与 BcosClient 保持一致）
     */
    private static String extractClasspathConfig() throws IOException {
        Path baseDir = Paths.get(System.getProperty("user.dir"), "chain-conf");
        Path confDir = baseDir.resolve("conf");
        Files.createDirectories(confDir);

        Path target = baseDir.resolve("config.toml");
        copyResource("fisco/config.toml", target);
        for (String cert : Arrays.asList("ca.crt", "sdk.crt", "sdk.key")) {
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

        copyResource("contracts/Traceability.bin", baseDir.resolve("Traceability.bin"));
        return target.toAbsolutePath().toString();
    }

    private static void copyResource(String resourcePath, Path target) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("缺少文件：" + resourcePath);
        }
        File file = resource.getFile();
        Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String readText(String path) throws IOException {
        if (path == null) {
            throw new IOException("文件路径未配置");
        }
        if (path.startsWith("classpath:")) {
            ClassPathResource resource = new ClassPathResource(path.substring("classpath:".length()));
            try (InputStream in = resource.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8).trim();
            }
        }
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(key, args[i + 1]);
                    i++;
                } else {
                    map.put(key, "true");
                }
            }
        }
        return map;
    }
}
