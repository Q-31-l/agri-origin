package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DisposeDTO {

    @NotBlank(message = "请填写处置措施")
    private String disposalMeasure;

    @NotBlank(message = "请填写处置结果")
    private String disposalResult;

    private String abnormalType;
}
