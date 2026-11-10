package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.entity.ChainRecord;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizHarvestRecordMapper;
import com.agri.trace.mapper.BizLogisticsRecordMapper;
import com.agri.trace.mapper.BizPlantingRecordMapper;
import com.agri.trace.mapper.BizQualityReportMapper;
import com.agri.trace.mapper.BizSalesRecordMapper;
import com.agri.trace.mapper.ChainRecordMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理员兜底撤销：处理"已被误审通过的不合规记录"。
 *
 * <p>设计上的三条硬约束，都是为了不把现有链路改坏：</p>
 * <ol>
 *   <li><b>只做链下标记，不动物理行</b>：业务记录走既有的 {@code deleted} 逻辑删除，
 *       存证流水打 {@code revoked} 标记。链上数据按设计不可删，
 *       撤销表达的是"不再作为有效证据采信"，而不是"历史没发生过"。</li>
 *   <li><b>逆序撤销</b>：上游记录一旦被撤，下游记录就失去了存在前提
 *       （采收撤了，质检报告凭什么出）。因此要求先撤下游，再撤上游，
 *       避免出现"有质检无采收"的断裂链路——这正是上一轮刚刚修掉的状态机漏洞。</li>
 *   <li><b>撤销后重算批次阶段</b>：批次状态由"当前仍然有效的已审核记录"推导，
 *       而不是直接减一。否则会出现外层显示"已采收"、里层一条记录都没有的老问题。</li>
 * </ol>
 *
 * <p>撤销动作本身由 {@code OperationLogAspect} 自动记入操作日志（含操作人、IP、理由）。</p>
 */
@Slf4j
@Service
public class RevokeService {

    @Resource
    private BizBatchMapper batchMapper;
    @Resource
    private BizPlantingRecordMapper plantingMapper;
    @Resource
    private BizHarvestRecordMapper harvestMapper;
    @Resource
    private BizQualityReportMapper qualityMapper;
    @Resource
    private BizLogisticsRecordMapper logisticsMapper;
    @Resource
    private BizSalesRecordMapper salesMapper;
    @Resource
    private ChainRecordMapper chainRecordMapper;
    @Resource
    private FileService fileService;

    /**
     * 撤销一条业务记录。
     *
     * @return 受影响摘要（批次号、回退前后的阶段、被撤销的证据数量）
     */
    @Transactional
    public Map<String, Object> revokeRecord(String bizType, Long bizId, String reason, boolean withEvidence) {
        if (bizType == null || bizId == null) {
            throw BizException.badRequest("请指定要撤销的记录类型与编号");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw BizException.badRequest("请填写撤销理由，便于事后追溯");
        }
        String type = bizType.trim().toUpperCase();
        LoginUser user = UserContext.require();

        Long batchId = resolveBatchId(type, bizId);
        BizBatch batch = batchId == null ? null : batchMapper.selectById(batchId);
        if (batch == null) {
            throw BizException.notFound("记录所属批次不存在，无法撤销");
        }

        assertNoDownstream(type, batchId);

        // 取证：撤销前先把证据清单取出来，删记录后 biz_id 关联仍在，但语义上已失效
        int revokedFiles = 0;
        if (withEvidence) {
            revokedFiles = fileService.revokeByBiz(eventTypeOf(type), bizId, reason.trim(), user.getRealName());
        }

        removeRecord(type, bizId);
        markChainRevoked(type, bizId, reason.trim());

        String before = batch.getStatus();
        recalcBatchStage(batch);
        log.info("管理员撤销记录：type={}, bizId={}, batch={}, {} → {}, 操作人={}, 理由={}",
                type, bizId, batch.getBatchNo(), before, batch.getStatus(), user.getUsername(), reason.trim());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batch.getBatchNo());
        result.put("statusBefore", before);
        result.put("statusAfter", batch.getStatus());
        result.put("revokedFiles", revokedFiles);
        return result;
    }

    /** 单独撤销一张实证文件（如农户误传的人像照） */
    @Transactional
    public void revokeFile(Long fileId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BizException.badRequest("请填写撤销理由，便于事后追溯");
        }
        LoginUser user = UserContext.require();
        fileService.revokeFile(fileId, reason.trim(), user.getRealName());
    }

    // ------------------------------------------------------------------ 内部

    private Long resolveBatchId(String type, Long bizId) {
        switch (type) {
            case "PLANTING": {
                BizPlantingRecord r = plantingMapper.selectById(bizId);
                return r == null ? null : r.getBatchId();
            }
            case "HARVEST": {
                BizHarvestRecord r = harvestMapper.selectById(bizId);
                return r == null ? null : r.getBatchId();
            }
            case "QUALITY": {
                BizQualityReport r = qualityMapper.selectById(bizId);
                return r == null ? null : r.getBatchId();
            }
            case "LOGISTICS": {
                BizLogisticsRecord r = logisticsMapper.selectById(bizId);
                return r == null ? null : r.getBatchId();
            }
            case "SALES": {
                BizSalesRecord r = salesMapper.selectById(bizId);
                return r == null ? null : r.getBatchId();
            }
            default:
                throw BizException.badRequest("不支持撤销的记录类型：" + type);
        }
    }

    private void removeRecord(String type, Long bizId) {
        switch (type) {
            case "PLANTING":
                plantingMapper.deleteById(bizId);
                break;
            case "HARVEST":
                harvestMapper.deleteById(bizId);
                break;
            case "QUALITY":
                qualityMapper.deleteById(bizId);
                break;
            case "LOGISTICS":
                logisticsMapper.deleteById(bizId);
                break;
            case "SALES":
                salesMapper.deleteById(bizId);
                break;
            default:
                throw BizException.badRequest("不支持撤销的记录类型：" + type);
        }
    }

    private String eventTypeOf(String type) {
        switch (type) {
            case "PLANTING":
                return Constants.EVENT_PLANTING;
            case "HARVEST":
                return Constants.EVENT_HARVEST;
            case "QUALITY":
                return Constants.EVENT_QUALITY;
            case "LOGISTICS":
                return Constants.EVENT_LOGISTICS;
            case "SALES":
                return Constants.EVENT_SALES;
            default:
                return type;
        }
    }

    /**
     * 逆序保护：下游还有有效记录时不允许撤上游。
     *
     * <p>"有效"指未删除、且不是被驳回的草稿。驳回记录本就不成立，不构成存在前提。</p>
     */
    private void assertNoDownstream(String type, Long batchId) {
        switch (type) {
            case "HARVEST": {
                long n = countAlive(qualityMapper.selectCount(Wrappers.<BizQualityReport>lambdaQuery()
                        .eq(BizQualityReport::getBatchId, batchId)
                        .ne(BizQualityReport::getAuditStatus, Constants.AUDIT_REJECTED)));
                if (n > 0) {
                    throw BizException.conflict("该批次已有质检报告（" + n + " 条），请先撤销质检记录再撤销采收");
                }
                break;
            }
            case "QUALITY": {
                long n = countAlive(logisticsMapper.selectCount(Wrappers.<BizLogisticsRecord>lambdaQuery()
                        .eq(BizLogisticsRecord::getBatchId, batchId)
                        .ne(BizLogisticsRecord::getAuditStatus, Constants.AUDIT_REJECTED)));
                if (n > 0) {
                    throw BizException.conflict("该批次已有物流记录（" + n + " 条），请先撤销物流记录再撤销质检");
                }
                break;
            }
            case "LOGISTICS": {
                long n = countAlive(salesMapper.selectCount(Wrappers.<BizSalesRecord>lambdaQuery()
                        .eq(BizSalesRecord::getBatchId, batchId)
                        .ne(BizSalesRecord::getAuditStatus, Constants.AUDIT_REJECTED)));
                if (n > 0) {
                    throw BizException.conflict("该批次已有销售记录（" + n + " 条），请先撤销销售记录再撤销物流");
                }
                break;
            }
            default:
                // 农事与销售处于两端，撤销不破坏上下游前提
                break;
        }
    }

    private long countAlive(Long count) {
        return count == null ? 0L : count;
    }

    /** 链上存证无法删除，改为打撤销标记，校验时跳过 */
    private void markChainRevoked(String type, Long bizId, String reason) {
        List<ChainRecord> records = chainRecordMapper.selectList(Wrappers.<ChainRecord>lambdaQuery()
                .eq(ChainRecord::getBizType, type)
                .eq(ChainRecord::getBizId, bizId));
        for (ChainRecord record : records) {
            record.setRevoked(1);
            record.setRevokeReason(reason);
            chainRecordMapper.updateById(record);
        }
    }

    /**
     * 按"当前仍然有效的已审核记录"重新推导批次阶段。
     *
     * <p>与各业务 Service 在审核通过时推进状态的规则完全一致（下游优先），
     * 因此撤销任意一条记录后，批次都会落回它真实所处的环节。</p>
     *
     * <p>异常批次与结案批次不参与重算：前者由处置流程接管，后者是终态，
     * 回退它们会凭空造出一个"已结案却又在流通"的状态。</p>
     */
    private void recalcBatchStage(BizBatch batch) {
        if (Constants.BATCH_ABNORMAL.equals(batch.getStatus())
                || Constants.BATCH_CLOSED.equals(batch.getStatus())) {
            return;
        }
        Long bid = batch.getId();

        List<BizSalesRecord> sales = salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(BizSalesRecord::getBatchId, bid)
                .eq(BizSalesRecord::getAuditStatus, Constants.AUDIT_APPROVED));
        if (!sales.isEmpty()) {
            BigDecimal sold = sales.stream().map(BizSalesRecord::getQuantity)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean soldOut = batch.getQuantity() != null && sold.compareTo(batch.getQuantity()) >= 0;
            updateStatus(batch, soldOut ? Constants.BATCH_SOLD : Constants.BATCH_ON_SALE);
            return;
        }

        long logistics = countAlive(logisticsMapper.selectCount(Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(BizLogisticsRecord::getBatchId, bid)
                .eq(BizLogisticsRecord::getAuditStatus, Constants.AUDIT_APPROVED)));
        if (logistics > 0) {
            updateStatus(batch, Constants.BATCH_IN_TRANSIT);
            return;
        }

        long quality = countAlive(qualityMapper.selectCount(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getBatchId, bid)
                .eq(BizQualityReport::getAuditStatus, Constants.AUDIT_APPROVED)
                .eq(BizQualityReport::getInspectResult, "PASS")));
        if (quality > 0) {
            updateStatus(batch, Constants.BATCH_QUALIFIED);
            return;
        }

        long harvest = countAlive(harvestMapper.selectCount(Wrappers.<BizHarvestRecord>lambdaQuery()
                .eq(BizHarvestRecord::getBatchId, bid)
                .eq(BizHarvestRecord::getAuditStatus, Constants.AUDIT_APPROVED)));
        if (harvest > 0) {
            updateStatus(batch, Constants.BATCH_HARVESTED);
            return;
        }

        // 没有任何有效环节：回到种植中。采收日期必须一并清空，
        // 否则详情页会显示"种植中，但采收日期是 5 月 2 日"这种自相矛盾的信息
        if (batch.getHarvestDate() != null) {
            batch.setHarvestDate(null);
            batchMapper.update(null, Wrappers.<BizBatch>lambdaUpdate()
                    .eq(BizBatch::getId, bid)
                    .set(BizBatch::getHarvestDate, null));
        }
        updateStatus(batch, Constants.BATCH_PLANTING);
    }

    private void updateStatus(BizBatch batch, String status) {
        if (Objects.equals(batch.getStatus(), status)) {
            return;
        }
        batch.setStatus(status);
        batchMapper.update(null, Wrappers.<BizBatch>lambdaUpdate()
                .eq(BizBatch::getId, batch.getId())
                .set(BizBatch::getStatus, status));
    }
}
