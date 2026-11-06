package com.agri.trace.dto;

import lombok.Data;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
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

    /** 作业地点（自动定位/农场档案带出），展示用文本 */
    private String location;

    /**
     * 现场实证：图片/视频文件 id 列表，提交后证据哈希随业务摘要一并上链。
     * 必须至少一条——没有实拍凭证的农事记录，上链也只是把一段文字钉死，
     * 对溯源没有证明力。前端拦一道，这里再拦一道，避免绕过页面直接调接口。
     */
    @NotEmpty(message = "请上传现场实证（图片或视频，至少 1 个）")
    private List<Long> evidenceIds;
}
