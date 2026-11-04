package com.agri.trace.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 全局时间序列化配置。
 *
 * <p>背景：Spring Boot 的 {@code spring.jackson.date-format} 只作用于 {@link java.util.Date}，
 * 对 JSR-310 的 {@code LocalDate / LocalDateTime / LocalTime} 无效，默认走 ISO-8601
 * （{@code 2026-05-12T06:00:00}）。这带来两个问题：</p>
 *
 * <ol>
 *   <li>返回给前端的时间带 {@code T}，表格里直接显示 {@code 2026-05-12T06:00:00}，不美观；</li>
 *   <li>接收参数时只认 ISO，接口文档中示例写的 {@code 2026-05-12 06:00:00} 会直接反序列化失败。</li>
 * </ol>
 *
 * <p>因此这里统一为：</p>
 * <ul>
 *   <li>输出：{@code yyyy-MM-dd HH:mm:ss}（日期为 {@code yyyy-MM-dd}，时间为 {@code HH:mm:ss}）</li>
 *   <li>输入：同时接受 {@code yyyy-MM-dd HH:mm:ss} 与 ISO {@code yyyy-MM-ddTHH:mm:ss}，
 *       前端 el-date-picker 两种写法都能正常工作</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern(TIME_PATTERN);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsr310Customizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("agri-jsr310");

            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
            module.addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));

            module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));

            // 用 modulesToInstall 而非 modules：后者会清空自动注册的 JavaTimeModule，
            // 导致 Instant / Duration 等其他 JSR-310 类型失去默认处理
            builder.modulesToInstall(module);
        };
    }

    /**
     * 兼容 {@code 2026-05-12 06:00:00} 与 {@code 2026-05-12T06:00:00} 两种写法。
     * 反序列化只做格式归一，不做时区换算——业务时间统一按 Asia/Shanghai 理解。
     */
    static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            String text = parser.getText();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            String normalized = text.trim();
            // 空格分隔改为 ISO 的 T，交给标准解析器处理
            if (normalized.length() > 10 && normalized.charAt(10) == ' ') {
                normalized = normalized.substring(0, 10) + "T" + normalized.substring(11);
            }
            try {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e) {
                // 仅传日期（如 2026-05-12）时按当天零点处理，避免前端漏传时间导致 500
                try {
                    return LocalDate.parse(normalized, DATE_FORMATTER).atStartOfDay();
                } catch (DateTimeParseException ignored) {
                    throw new IOException("时间格式不正确，应为 yyyy-MM-dd HH:mm:ss 或 "
                            + "yyyy-MM-ddTHH:mm:ss，实际收到：" + text, e);
                }
            }
        }
    }
}
