package com.agri.trace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地文件存储配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String uploadDir = "./uploads";
    private String allowedExt = "jpg,jpeg,png,webp,pdf,mp4,mov,m4v,webm";
    private long maxSize = 100 * 1024 * 1024L;
    private String publicPrefix = "/api/file/download/";
}
