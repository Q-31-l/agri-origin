package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 链上存证流水（每次上链一条，失败也留痕）
 */
@Data
@TableName("chain_record")
public class ChainRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private String contractAddress;
    /** 合约内记录下标，批次登记为 null */
    private Integer chainIndex;
    /** BATCH/PLANTING/HARVEST/QUALITY/LOGISTICS/SALES/ABNORMAL/DISPOSAL/CORRECTION */
    private String bizType;
    private Long bizId;
    /** 链上事件类型，与 bizType 一致或更细 */
    private String eventType;
    private String dataHash;
    private String fileHash;
    private String txHash;
    private Long blockNumber;
    private Long gasUsed;
    /** SUCCESS / FAILED / SKIPPED */
    private String status;
    /** 1 表示该存证对应的业务记录已被管理员撤销，校验时跳过且不计入总数 */
    private Integer revoked;
    private String revokeReason;
    private String errorMsg;
    private LocalDateTime createTime;
}
