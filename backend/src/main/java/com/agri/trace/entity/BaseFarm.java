package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("base_farm")
public class BaseFarm {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String farmCode;
    private String farmName;
    private Long farmerId;
    private String farmerName;
    private Long villageId;
    private String villageName;
    private String address;
    private BigDecimal area;
    private String soilType;
    private String waterSource;
    private String certNo;
    private Integer status;
    private LocalDateTime createTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
