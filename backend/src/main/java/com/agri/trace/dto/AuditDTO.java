package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AuditDTO {

    @NotBlank(message = "请选择审核结果")
    private String auditStatus;

    private String auditRemark;
}
