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
 * 销售记录
 */
@Data
@TableName("biz_sales_record")
public class BizSalesRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    private Long sellerId;
    private String sellerName;
    private String marketName;
    private LocalDate salesDate;
    private BigDecimal price;
    private BigDecimal quantity;
    private String unit;
    private String buyerName;
    private String buyerPhone;

    private String dataHash;
    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private LocalDateTime createTime;

    private String auditStatus;
    private String auditRemark;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
