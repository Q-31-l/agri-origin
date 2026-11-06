package com.agri.trace.service;

import com.agri.trace.common.Constants;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.vo.BatchDetailVO;
import com.agri.trace.vo.ChainVerifyVO;
import com.agri.trace.vo.TraceVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 扫码溯源（消费者免登录）
 */
@Service
public class TraceService {

    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;
    @Resource
    private TrustScoreService trustScoreService;

    /** 示例溯源码：公开数据，供溯源页做"试试这些码"的引导 */
    public List<String> samples(int limit) {
        return batchService.sampleTraceCodes(limit);
    }

    public TraceVO query(String code) {
        BizBatch batch = batchService.getByCode(code);
        BatchDetailVO detail = batchService.detailPublic(batch);

        TraceVO vo = new TraceVO();
        vo.setTraceCode(batch.getTraceCode());
        vo.setBatchNo(batch.getBatchNo());
        vo.setProductName(batch.getProductName());
        vo.setVariety(batch.getVariety());
        vo.setOrigin((batch.getVillageName() == null ? "" : batch.getVillageName())
                + (batch.getFarmName() == null ? "" : batch.getFarmName()));
        vo.setFarmName(batch.getFarmName());
        vo.setFarmerName(batch.getFarmerName());
        vo.setVillageName(batch.getVillageName());
        vo.setPlantingDate(batch.getPlantingDate());
        vo.setHarvestDate(batch.getHarvestDate());
        vo.setQuantity(batch.getQuantity());
        vo.setUnit(batch.getUnit());
        vo.setStatus(batch.getStatus());
        vo.setAbnormalFlag(batch.getAbnormalFlag());
        vo.setChainStatus(batch.getChainStatus());
        vo.setChainTxHash(batch.getChainTxHash());
        vo.setBlockNumber(batch.getBlockNumber());
        vo.setChainEnabled(detail.isChainEnabled());
        vo.setChainMessage(detail.getChainMessage());
        vo.setTimeline(detail.getTimeline());
        // assemble 时已算过，这里直接复用，避免重复计算
        vo.setTrust(detail.getTrust() != null ? detail.getTrust() : trustScoreService.evaluate(detail));

        List<BizQualityReport> reports = detail.getQualities();
        if (!reports.isEmpty()) {
            BizQualityReport latest = reports.get(reports.size() - 1);
            vo.setQualityResult(latest.getInspectResult());
            vo.setQualityReportNo(latest.getReportNo());
            vo.setQualityOrg(latest.getInspectOrg());
        }
        if (Constants.BATCH_ABNORMAL.equals(batch.getStatus())) {
            vo.setQualityResult("FAIL");
        }
        return vo;
    }

    public ChainVerifyVO verify(String code) {
        BizBatch batch = batchService.getByCode(code);
        return chainService.verifyBatchLight(batch.getBatchNo());
    }
}
