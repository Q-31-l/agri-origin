package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
public class FarmDTO {

    private Long id;

    @NotBlank(message = "请填写农场名称")
    private String farmName;

    private Long farmerId;
    private Long villageId;
    private String address;
    private BigDecimal area;
    private String soilType;
    private String waterSource;
    private String certNo;
    private Integer status = 1;
}
