package com.agri.trace.dto;

import lombok.Data;
import java.util.List;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesDTO {

    @NotNull(message = "请选择批次")
    private Long batchId;

    @NotBlank(message = "请填写销售市场")
    private String marketName;

    @NotNull(message = "请选择销售日期")
    private LocalDate salesDate;

    @NotNull(message = "请填写销售单价")
    @DecimalMin(value = "0.01", message = "单价必须大于 0")
    private BigDecimal price;

    @NotNull(message = "请填写销售数量")
    @DecimalMin(value = "0.01", message = "数量必须大于 0")
    private BigDecimal quantity;

    private String unit = "kg";
    private String buyerName;
    private String buyerPhone;

    /** 现场实证：图片/视频文件 id 列表，提交后证据哈希随业务摘要一并上链 */
    private List<Long> evidenceIds;
}
