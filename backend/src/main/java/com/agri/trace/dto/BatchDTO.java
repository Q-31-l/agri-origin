package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BatchDTO {

    /** 已有产品品类的 id。为 null 时可改填 productName 手写产品 */
    private Long productId;

    /** 手写产品名（productId 为空时使用），如"草莓 / 玉米 / 高粱" */
    private String productName;

    /** 可选：手写产品的品种，如"红颜 / 糯玉米" */
    private String variety;

    private Long farmId;

    private Long farmerId;

    @NotNull(message = "请选择种植日期")
    private LocalDate plantingDate;

    @NotNull(message = "请填写预计产量")
    @DecimalMin(value = "0.01", message = "产量必须大于 0")
    private BigDecimal quantity;

    private String unit = "kg";

    private String remark;
}
