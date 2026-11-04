package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HarvestDTO {

    @NotNull(message = "请选择批次")
    private Long batchId;

    @NotNull(message = "请选择采收日期")
    private LocalDate harvestDate;

    @NotNull(message = "请填写采收数量")
    @DecimalMin(value = "0.01", message = "采收数量必须大于 0")
    private BigDecimal quantity;

    private String unit = "kg";
    private String qualityLevel = "A";
    private String weather;
    private String images;
    private String remark;
}
