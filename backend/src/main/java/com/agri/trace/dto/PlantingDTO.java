package com.agri.trace.dto;

import lombok.Data;
import java.util.List;

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

    /** 现场实证：图片/视频文件 id 列表，提交后证据哈希随业务摘要一并上链 */
    private List<Long> evidenceIds;
}
