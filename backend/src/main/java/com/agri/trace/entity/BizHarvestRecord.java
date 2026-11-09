package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采收记录
 */
@Data
@TableName("biz_harvest_record")
public class BizHarvestRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    private LocalDate harvestDate;
    private BigDecimal quantity;
    private String unit;
    private String qualityLevel;
    private String weather;

    private Long operatorId;
    private String operatorName;
    private String images;
    private String remark;

    private String auditStatus;
    private String auditRemark;
    private String auditBy;
    private LocalDateTime auditTime;

    private String dataHash;
    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private LocalDateTime createTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
