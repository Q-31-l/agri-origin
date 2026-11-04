package com.agri.trace.sdk;

import cn.hutool.crypto.digest.DigestUtil;

import java.io.InputStream;
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
     * 规范化拼接：null -> ""，用 | 分隔，保证链下可复算
     */
    public static String join(Object... parts) {
        if (parts == null) {
            return "";
        }
        return Stream.of(parts)
                .map(p -> p == null ? "" : String.valueOf(p))
                .collect(Collectors.joining("|"));
    }

    public static String digestOf(Object... parts) {
        return sha256(join(parts).getBytes(StandardCharsets.UTF_8));
    }
}
