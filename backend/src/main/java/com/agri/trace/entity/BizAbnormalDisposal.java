package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异常批次处置记录
 */
@Data
@TableName("biz_abnormal_disposal")
public class BizAbnormalDisposal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    /** QUALITY / SPOT_CHECK / REPORT */
    private String sourceType;
    private Long sourceId;
    private String abnormalType;
    private String description;
    private String disposalMeasure;
    private String disposalResult;

    private Long handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    /** HANDLING / DONE */
    private String status;

    /** 异常判定摘要（创建后不可变） */
    private String abnormalHash;
    /** 处置结果摘要（处置后写入） */
    private String disposalHash;

    private String dataHash;
    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private LocalDateTime createTime;

    /** 乐观锁版本号：同一异常被多人同时处置时，只允许第一个人成功 */
    @Version
    private Integer version;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
