package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ProductDTO {

    private Long id;

    @NotBlank(message = "请填写产品名称")
    private String productName;

    private String category;
    private String variety;
    private String unit = "kg";
    private Integer shelfLifeDays;
    private String originRegion;
    private String description;
}
