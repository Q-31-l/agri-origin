package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class QualityDTO {

    @NotNull(message = "请选择批次")
    private Long batchId;

    @NotBlank(message = "请填写检测报告编号")
    private String reportNo;

    @NotNull(message = "请选择检测日期")
    private LocalDate inspectDate;

    @NotBlank(message = "请选择检测结论")
    private String inspectResult;

    private String standardNo;
    private String itemsJson;
    private String conclusion;

    private String fileName;
    private String filePath;
    private String fileHash;
    private Long fileSize;
}
