package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫码溯源结果（消费者可见）
 */
@Data
public class TraceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceCode;
    private String batchNo;
    private String productName;
    private String variety;
    private String origin;
    private String farmName;
    private String farmerName;
    private String villageName;
    private LocalDate plantingDate;
    private LocalDate harvestDate;
    private BigDecimal quantity;
    private String unit;
    private String status;
    private Integer abnormalFlag;

    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private boolean chainEnabled;
    private String chainMessage;

    /** 质检结论汇总 */
    private String qualityResult;
    private String qualityReportNo;
    private String qualityOrg;

    private List<TimelineItem> timeline = new ArrayList<>();
}
