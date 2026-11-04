package com.agri.trace.common;

import lombok.Getter;

/**
 * 业务异常：统一由 GlobalExceptionHandler 转换为标准响应
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BAD_REQUEST;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ResultCode.CONFLICT, message);
    }

    public static BizException status(String message) {
        return new BizException(ResultCode.STATUS_INVALID, message);
    }

    public static BizException chain(String message) {
        return new BizException(ResultCode.CHAIN_ERROR, message);
    }
}
