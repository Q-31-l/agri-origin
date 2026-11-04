package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BatchDTO {

    @NotNull(message = "请选择产品")
    private Long productId;

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
