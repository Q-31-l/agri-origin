package com.agri.trace.dto;

import lombok.Data;

/**
 * 管理员撤销业务记录的请求体
 */
@Data
public class RevokeDTO {

    /** PLANTING / HARVEST / QUALITY / LOGISTICS / SALES */
    private String bizType;

    private Long bizId;

    /** 撤销理由，必填：事后追责要能回答"为什么撤" */
    private String reason;

    /**
     * 是否连该记录下的实证文件一并撤销。
     * 默认 false —— 有些场景只想撤结论（如质检误判），照片本身仍需保留备查。
     */
    private Boolean withEvidence;
}
