package com.agri.trace.common;

import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizCorrection;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.sdk.HashUtil;

/**
 * 业务摘要计算规则（链上链下共用，任何字段变更都必须同步此规则）
 * 说明：只取业务稳定字段，不含 createTime / updateTime 等运行时字段，
 *      保证任意时刻可复算出相同摘要。
 */
public final class DigestHelper {

    private DigestHelper() {
    }

    public static String batch(BizBatch b) {
        return HashUtil.digestOf("BATCH", b.getBatchNo(), b.getTraceCode(), b.getProductId(),
                b.getProductName(), b.getFarmerId(), b.getFarmId(), b.getVillageId(),
                b.getPlantingDate(), b.getQuantity(), b.getUnit());
    }

    public static String planting(BizPlantingRecord r) {
        return HashUtil.digestOf("PLANTING", r.getBatchNo(), r.getRecordType(), r.getOperationDate(),
                r.getContent(), r.getMaterialName(), r.getDosage(), r.getOperatorId(), r.getImages());
    }

    public static String harvest(BizHarvestRecord r) {
        return HashUtil.digestOf("HARVEST", r.getBatchNo(), r.getHarvestDate(), r.getQuantity(),
                r.getUnit(), r.getQualityLevel(), r.getOperatorId(), r.getImages());
    }

    public static String quality(BizQualityReport r) {
        return HashUtil.digestOf("QUALITY", r.getBatchNo(), r.getReportNo(), r.getInspectResult(),
                r.getInspectDate(), r.getInspectorId(), r.getFileHash(), r.getConclusion());
    }

    public static String logistics(BizLogisticsRecord r) {
        return HashUtil.digestOf("LOGISTICS", r.getBatchNo(), r.getLogisticsNo(), r.getFromOrg(),
                r.getToOrg(), r.getCarrierId(), r.getVehicleNo(), r.getDepartTime(),
                r.getArriveTime(), r.getFileHash());
    }

    public static String sales(BizSalesRecord r) {
        return HashUtil.digestOf("SALES", r.getBatchNo(), r.getSellerId(), r.getSalesDate(),
                r.getPrice(), r.getQuantity(), r.getBuyerName());
    }

    public static String abnormal(BizAbnormalDisposal r) {
        return HashUtil.digestOf("ABNORMAL", r.getBatchNo(), r.getAbnormalType(), r.getDescription(),
                r.getSourceId());
    }

    public static String disposal(BizAbnormalDisposal r) {
        return disposal(r, null);
    }

    /**
     * 处置摘要：证据根哈希一并参与计算。
     * 这样链上那枚哈希不仅覆盖"处置措施/结果"文本，也覆盖全部实证图片与视频，
     * 任何一张图被替换都会让链上链下摘要对不上。
     */
    public static String disposal(BizAbnormalDisposal r, String evidenceHash) {
        return HashUtil.digestOf("DISPOSAL", r.getBatchNo(), r.getDisposalMeasure(),
                r.getDisposalResult(), r.getHandlerId(), r.getHandleTime(),
                evidenceHash == null ? "" : evidenceHash);
    }

    public static String correction(BizCorrection c) {
        return HashUtil.digestOf("CORRECTION", c.getBatchNo(), c.getBizType(), c.getBizId(),
                c.getReason(), c.getContent(), c.getOperatorId());
    }

    /**
     * 结案摘要：仅与批次号相关，可随时复算
     */
    public static String close(String batchNo) {
        return HashUtil.digestOf("CLOSE", batchNo);
    }
}
