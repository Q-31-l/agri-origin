package com.agri.trace.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构：code=0 表示成功
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public R() {
    }

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS, "操作成功", data);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(ResultCode.SUCCESS, message, data);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(ResultCode.BAD_REQUEST, message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
