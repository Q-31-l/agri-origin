package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class CorrectionDTO {

    @NotNull(message = "请选择批次")
    private Long batchId;

    @NotBlank(message = "请选择更正的业务类型")
    private String bizType;

    @NotNull(message = "请选择被更正的记录")
    private Long bizId;

    @NotBlank(message = "请填写更正原因")
    private String reason;

    @NotBlank(message = "请填写更正说明")
    private String content;

    /** 更正时补充的实证：图片/视频文件 id 列表，证据哈希随更正摘要一并上链 */
    private List<Long> evidenceIds;
}
