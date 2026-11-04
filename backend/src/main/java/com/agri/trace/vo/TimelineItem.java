package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 溯源时间轴节点
 */
@Data
public class TimelineItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务时间 yyyy-MM-dd HH:mm */
    private String time;
    /** BATCH/PLANTING/HARVEST/QUALITY/LOGISTICS/SALES/ABNORMAL/DISPOSAL/CORRECTION */
    private String type;
    private String title;
    private String operator;
    private String status;
    private String detail;
    private String dataHash;
    private String fileHash;
    private String txHash;
    private Long blockNumber;
    private String chainStatus;
}
