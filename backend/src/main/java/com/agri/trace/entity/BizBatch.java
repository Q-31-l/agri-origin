package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 农产品批次（业务主线）
 */
@Data
@TableName("biz_batch")
public class BizBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次唯一编码 AG + yyyyMMdd + 3 位序列 */
    private String batchNo;
    /** 溯源唯一码（二维码内容） */
    private String traceCode;

    private Long productId;
    private String productName;
    private String variety;

    private Long farmId;
    private String farmName;
    private Long farmerId;
    private String farmerName;
    private Long villageId;
    private String villageName;

    private LocalDate plantingDate;
    private LocalDate harvestDate;
    private BigDecimal quantity;
    private String unit;

    /** PLANTING/HARVESTED/QUALIFIED/IN_TRANSIT/ON_SALE/SOLD/ABNORMAL/CLOSED */
    private String status;
    /** PENDING/APPROVED/REJECTED 村级核验 */
    private String auditStatus;
    private String auditBy;
    private LocalDateTime auditTime;
    private String auditRemark;

    private Integer abnormalFlag;

    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;

    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号：核验 / 采收 / 质检 / 物流 / 销售 / 结案等状态流转都要带版本比对，
     * 避免两人同时操作时后写覆盖前写。更新影响行数为 0 即代表已被他人修改。
     */
    @Version
    private Integer version;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
