package com.agri.trace.vo;

import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizCorrection;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.entity.ChainRecord;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批次全量详情（PC 端批次详情页）
 */
@Data
public class BatchDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BizBatch batch;
    private List<BizPlantingRecord> plantings = new ArrayList<>();
    private List<BizHarvestRecord> harvests = new ArrayList<>();
    private List<BizQualityReport> qualities = new ArrayList<>();
    private List<BizLogisticsRecord> logistics = new ArrayList<>();
    private List<BizSalesRecord> sales = new ArrayList<>();
    private List<BizAbnormalDisposal> disposals = new ArrayList<>();
    private List<BizCorrection> corrections = new ArrayList<>();
    private List<ChainRecord> chainRecords = new ArrayList<>();
    private List<TimelineItem> timeline = new ArrayList<>();
    private boolean chainEnabled;
    private String chainMessage;
    /** 可信度评分（AgriTrust-5D） */
    private TrustScoreVO trust;
}
