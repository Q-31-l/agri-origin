package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 种植 / 农事记录
 */
@Data
@TableName("biz_planting_record")
public class BizPlantingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    /** SOIL/SOWING/FERTILIZE/IRRIGATE/PEST/WEED/OTHER */
    private String recordType;
    private LocalDate operationDate;
    private String content;
    private String materialName;
    private String dosage;

    private Long operatorId;
    private String operatorName;
    private String images;
    private String gps;

    private String auditStatus;
    private String auditRemark;

    private String dataHash;
    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private LocalDateTime createTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
