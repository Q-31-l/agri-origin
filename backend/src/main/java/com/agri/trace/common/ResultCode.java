package com.agri.trace.common;

/**
 * 统一业务状态码
 */
public final class ResultCode {

    private ResultCode() {
    }

    public static final int SUCCESS = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int SERVER_ERROR = 500;

    /** 上链失败 */
    public static final int CHAIN_ERROR = 5001;
    /** 链上链下数据不一致 */
    public static final int CHAIN_INCONSISTENT = 5002;
    /** 业务状态不允许当前操作 */
    public static final int STATUS_INVALID = 4001;
}
