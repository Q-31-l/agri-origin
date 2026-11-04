package com.agri.trace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 链配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "chain")
public class ChainProperties {

    /** 是否启用链上存证；false 为演示模式（存证标记 SKIPPED） */
    private boolean enabled = false;

    /** SDK 配置文件，支持 classpath: 前缀 */
    private String configFile = "classpath:fisco/config.toml";

    private int groupId = 1;

    private String contractAddress = "";

    /** 合约 ABI 路径，支持 classpath: 前缀 */
    private String abiPath = "classpath:contracts/Traceability.abi";

    /** SDK 账户私钥（hex），留空则使用 SDK 配置文件中的账户 */
    private String privateKey = "";

    /** true：上链失败抛出异常并回滚业务；false：落库但标记 FAILED */
    private boolean strict = true;

    private long timeoutMs = 5000;
}
