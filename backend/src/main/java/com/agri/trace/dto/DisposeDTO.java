package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class DisposeDTO {

    @NotBlank(message = "请填写处置措施")
    private String disposalMeasure;

    @NotBlank(message = "请填写处置结果")
    private String disposalResult;

    private String abnormalType;

    /** 实证材料：/file/upload 返回的文件 id 列表（图片 / 视频），其哈希并入上链摘要 */
    private List<Long> evidenceIds;
}
