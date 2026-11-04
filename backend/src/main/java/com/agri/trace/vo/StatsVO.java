package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据看板统计
 */
@Data
public class StatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long totalBatches;
    private long plantingBatches;
    private long qualifiedBatches;
    private long inTransitBatches;
    private long onSaleBatches;
    private long soldBatches;
    private long abnormalBatches;
    private long todayBatches;

    private long totalFarmers;
    private long totalVillages;
    private long totalReports;
    private long totalChainRecords;

    private long chainSuccess;
    private long chainFailed;
    private long chainSkipped;

    /** 质检合格率（%） */
    private double qualifiedRate;
    /** 上链成功率（%） */
    private double chainSuccessRate;

    private List<Map<String, Object>> statusDistribution = new ArrayList<>();
    private List<Map<String, Object>> villageDistribution = new ArrayList<>();
    private List<Map<String, Object>> trend = new ArrayList<>();
}
