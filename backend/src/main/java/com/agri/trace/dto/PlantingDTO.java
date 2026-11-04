package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class PlantingDTO {

    @NotNull(message = "请选择批次")
    private Long batchId;

    @NotBlank(message = "请选择农事类型")
    private String recordType;

    @NotNull(message = "请选择作业日期")
    private LocalDate operationDate;

    @NotBlank(message = "请填写农事内容")
    private String content;

    private String materialName;
    private String dosage;
    /** 现场照片，逗号分隔的 URL */
    private String images;
    private String gps;
}
