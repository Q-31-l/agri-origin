package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 批次修改入参。
 *
 * <p>批次的产品、地块、农户、种植日期、产量属于"建档要素"，直接决定归属、
 * 数据范围与链上摘要，建档后不可改；确需修正必须走更正说明（追加式留痕）。
 * 因此修改接口只接受备注，不再要求客户端回传上述字段。
 */
@Data
public class BatchUpdateDTO {

    @Size(max = 255, message = "备注最多 255 字")
    private String remark;
}
