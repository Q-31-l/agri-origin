package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("base_product")
public class BaseProduct {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private String productName;
    private String category;
    private String variety;
    private String unit;
    private Integer shelfLifeDays;
    private String originRegion;
    private String description;
    private LocalDateTime createTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
