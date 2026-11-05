package com.agri.trace.common;

/**
 * 全局常量：角色、状态、链上事件类型
 */
public final class Constants {

    private Constants() {
    }

    // ---------------- 角色 ----------------
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_REGULATOR = "REGULATOR";
    public static final String ROLE_PRODUCER = "PRODUCER";
    public static final String ROLE_VILLAGE = "VILLAGE_ADMIN";
    public static final String ROLE_FARMER = "FARMER";
    public static final String ROLE_INSPECTOR = "INSPECTOR";
    public static final String ROLE_LOGISTICS = "LOGISTICS";
    public static final String ROLE_SELLER = "SELLER";

    // ---------------- 批次状态 ----------------
    public static final String BATCH_PLANTING = "PLANTING";
    public static final String BATCH_HARVESTED = "HARVESTED";
    public static final String BATCH_QUALIFIED = "QUALIFIED";
    public static final String BATCH_IN_TRANSIT = "IN_TRANSIT";
    public static final String BATCH_ON_SALE = "ON_SALE";
    public static final String BATCH_SOLD = "SOLD";
    public static final String BATCH_ABNORMAL = "ABNORMAL";
    public static final String BATCH_CLOSED = "CLOSED";

    // ---------------- 审核状态 ----------------
    public static final String AUDIT_PENDING = "PENDING";
    public static final String AUDIT_APPROVED = "APPROVED";
    public static final String AUDIT_REJECTED = "REJECTED";

    // ---------------- 异常处置状态 ----------------
    public static final String ABNORMAL_HANDLING = "HANDLING";
    public static final String ABNORMAL_DONE = "DONE";

    // ---------------- 链上存证状态 ----------------
    public static final String CHAIN_NONE = "NONE";
    public static final String CHAIN_SUCCESS = "SUCCESS";
    public static final String CHAIN_FAILED = "FAILED";
    public static final String CHAIN_SKIPPED = "SKIPPED";

    // ---------------- 链上事件类型 ----------------
    public static final String EVENT_BATCH = "BATCH";
    public static final String EVENT_PLANTING = "PLANTING";
    public static final String EVENT_HARVEST = "HARVEST";
    public static final String EVENT_QUALITY = "QUALITY";
    public static final String EVENT_LOGISTICS = "LOGISTICS";
    public static final String EVENT_SALES = "SALES";
    public static final String EVENT_ABNORMAL = "ABNORMAL";
    public static final String EVENT_DISPOSAL = "DISPOSAL";
    public static final String EVENT_CORRECTION = "CORRECTION";
    public static final String EVENT_CLOSE = "CLOSE";

    public static final String TRACE_URL_PREFIX = "/trace/";
}
