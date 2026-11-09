package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流交接记录
 */
@Data
@TableName("biz_logistics_record")
public class BizLogisticsRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    private String logisticsNo;
    private String fromOrg;
    private String toOrg;

    private Long carrierId;
    private String carrierName;
    private String vehicleNo;
    private String driverName;
    private String driverPhone;

    private LocalDateTime departTime;
    private LocalDateTime arriveTime;
    private String temperature;
    /** DEPARTED / ARRIVED */
    private String status;
    private String handoverFile;
    private String fileHash;
    private String remark;

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
