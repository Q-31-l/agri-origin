package com.agri.trace.sdk;

import cn.hutool.crypto.digest.DigestUtil;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 摘要工具：所有上链摘要统一使用 SHA-256（小写十六进制）
 */
public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256(String content) {
        return DigestUtil.sha256Hex(content == null ? "" : content);
    }

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    public static String sha256(InputStream in) {
        try {
            return DigestUtil.sha256Hex(in);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    /**
     * 规范化拼接：null -> ""，用 | 分隔，保证链下可复算。
     * BigDecimal 统一 stripTrailingZeros().toPlainString()：
     * 上链时实体里的 100（scale=0）与落库后读回的 100.00（DECIMAL scale=2）
     * 会被规范化成同一个 "100"，避免摘要因数值精度表示不同而对不上。
     */
    public static String join(Object... parts) {
        if (parts == null) {
            return "";
        }
        return Stream.of(parts)
                .map(HashUtil::normalize)
                .collect(Collectors.joining("|"));
    }

    private static String normalize(Object p) {
        if (p == null) {
            return "";
        }
        if (p instanceof BigDecimal) {
            return ((BigDecimal) p).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(p);
    }

    public static String digestOf(Object... parts) {
        return sha256(join(parts).getBytes(StandardCharsets.UTF_8));
    }
}
