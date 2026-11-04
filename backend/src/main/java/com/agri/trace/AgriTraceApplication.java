package com.agri.trace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 农产品质量安全溯源系统启动类
 */
@EnableAsync
@EnableTransactionManagement
@MapperScan("com.agri.trace.mapper")
@SpringBootApplication
public class AgriTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgriTraceApplication.class, args);
    }
}
