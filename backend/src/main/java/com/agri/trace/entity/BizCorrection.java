package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据更正说明（追加式，不覆盖原始记录）
 */
@Data
@TableName("biz_correction")
public class BizCorrection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    /** PLANTING/HARVEST/QUALITY/LOGISTICS/SALES */
    private String bizType;
    private Long bizId;
    private String reason;
    private String content;

    private Long operatorId;
    private String operatorName;

    private String dataHash;
    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private LocalDateTime createTime;
}
