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
 * 质检报告
 */
@Data
@TableName("biz_quality_report")
public class BizQualityReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    private String reportNo;
    private String inspectOrg;
    private Long inspectorId;
    private String inspectorName;
    private LocalDate inspectDate;
    /** PASS / FAIL */
    private String inspectResult;
    private String standardNo;
    private String itemsJson;
    private String conclusion;

    private String fileName;
    private String filePath;
    private String fileHash;
    private Long fileSize;

    private String dataHash;
    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private LocalDateTime createTime;

    private String auditStatus;
    private String auditRemark;
    private String auditBy;
    private LocalDateTime auditTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
