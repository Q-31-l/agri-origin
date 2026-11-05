package com.agri.trace.service;

import com.agri.trace.common.Constants;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizCorrection;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.entity.ChainRecord;
import com.agri.trace.mapper.BizAbnormalDisposalMapper;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizCorrectionMapper;
import com.agri.trace.mapper.BizHarvestRecordMapper;
import com.agri.trace.mapper.BizLogisticsRecordMapper;
import com.agri.trace.mapper.BizPlantingRecordMapper;
import com.agri.trace.mapper.BizQualityReportMapper;
import com.agri.trace.mapper.BizSalesRecordMapper;
import com.agri.trace.mapper.ChainRecordMapper;
import com.agri.trace.sdk.ChainResult;
import com.agri.trace.sdk.TraceContractService;
import com.agri.trace.vo.ChainVerifyVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 存证流水与一致性校验
 */
@Slf4j
@Service
public class ChainService {

    @Resource
    private ChainRecordMapper chainRecordMapper;
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
    private BizAbnormalDisposalMapper abnormalMapper;
    @Resource
    private BizCorrectionMapper correctionMapper;
    @Resource
    private FileService fileService;
    @Resource
    private TraceContractService contractService;
    @Resource
    private com.agri.trace.websocket.TraceWebSocketHandler wsHandler;

    /**
     * 保存存证流水（成功与失败都留痕）
     */
    public void saveRecord(String batchNo, String bizType, Long bizId, String eventType,
                           String dataHash, String fileHash, ChainResult result) {
        ChainRecord record = new ChainRecord();
        record.setBatchNo(batchNo);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setEventType(eventType);
        record.setDataHash(dataHash);
        record.setFileHash(fileHash);
        record.setChainIndex(result.getIndex());
        record.setStatus(result.getStatus());
        record.setTxHash(result.getTxHash());
        record.setBlockNumber(result.getBlockNumber());
        record.setGasUsed(result.getGasUsed());
        record.setErrorMsg(result.isSuccess() ? null : result.getMessage());
        record.setContractAddress(contractService.getContractAddress());
        chainRecordMapper.insert(record);

        // 实时通知三端：上链结果变化后列表/详情自动刷新
        wsHandler.broadcast("chain", batchNo, eventType, result.isSuccess()
                ? "存证成功 · " + record.getStatus()
                : "存证未成功 · " + record.getStatus());
    }

    public PageResult<ChainRecord> page(long page, long size, String batchNo, String status) {
        LambdaQueryWrapper<ChainRecord> wrapper = Wrappers.<ChainRecord>lambdaQuery()
                .like(batchNo != null && !batchNo.isEmpty(), ChainRecord::getBatchNo, batchNo)
                .eq(status != null && !status.isEmpty(), ChainRecord::getStatus, status)
                .orderByDesc(ChainRecord::getId);
        return PageResult.of(chainRecordMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<ChainRecord> listByBatch(String batchNo) {
        return chainRecordMapper.selectList(Wrappers.<ChainRecord>lambdaQuery()
                .eq(ChainRecord::getBatchNo, batchNo)
                .orderByAsc(ChainRecord::getId));
    }

    /**
     * 链上链下一致性校验：摘要重算 + 交易回执 + 链上比对
     */
    public ChainVerifyVO verifyBatch(String batchNo) {
        ChainVerifyVO vo = new ChainVerifyVO();
        vo.setBatchNo(batchNo);
        vo.setVerifiedAt(LocalDateTime.now());
        vo.setChainEnabled(contractService.isEnabled());

        List<ChainRecord> records = listByBatch(batchNo);
        vo.setTotalRecords(records.size());

        if (!contractService.isEnabled()) {
            vo.setPassed(false);
            vo.setMessage("系统处于演示模式（未启用链上存证），无法完成链上校验");
            return vo;
        }
        if (records.isEmpty()) {
            vo.setPassed(false);
            vo.setMessage("该批次暂无存证记录，无法校验");
            return vo;
        }

        int matched = 0;
        for (ChainRecord record : records) {
            ChainVerifyVO.Item item = new ChainVerifyVO.Item();
            item.setName(labelOf(record.getBizType()) + "#" + record.getBizId());
            item.setBizType(record.getBizType());
            item.setTxHash(record.getTxHash());
            item.setChainIndex(record.getChainIndex());
            item.setChainHash(record.getDataHash());

            if (!Constants.CHAIN_SUCCESS.equals(record.getStatus())) {
                item.setMatched(false);
                item.setMessage("该记录未成功上链：" + (record.getErrorMsg() == null ? record.getStatus() : record.getErrorMsg()));
                vo.getItems().add(item);
                continue;
            }

            String localHash = recompute(record);
            item.setLocalHash(localHash);
            item.setHashMatched(localHash != null && localHash.equals(record.getDataHash()));
            item.setReceiptOk(contractService.checkReceipt(record.getTxHash()));

            Boolean onChain = record.getChainIndex() == null ? Boolean.TRUE
                    : contractService.verifyRecord(batchNo, record.getChainIndex(), localHash);
            boolean onChainOk = Boolean.TRUE.equals(onChain);

            item.setMatched(item.isHashMatched() && item.isReceiptOk() && onChainOk);
            item.setMessage(item.isMatched() ? "校验通过"
                    : (!item.isHashMatched() ? "链下数据摘要与存证不一致"
                    : (!item.isReceiptOk() ? "交易回执状态异常" : "链上摘要比对失败")));
            if (item.isMatched()) {
                matched++;
            }
            vo.getItems().add(item);
        }

        vo.setMatchedRecords(matched);
        boolean allSuccess = records.stream().allMatch(r -> Constants.CHAIN_SUCCESS.equals(r.getStatus()));
        vo.setPassed(allSuccess && matched == records.size());
        vo.setMessage(vo.isPassed() ? "全部 " + matched + " 条记录校验通过，链上链下数据一致"
                : "存在 " + (records.size() - matched) + " 条记录校验失败，请核查");
        return vo;
    }

    /**
     * 公共溯源页的轻量校验：只校验摘要与回执，不逐条读取链上记录
     */
    public ChainVerifyVO verifyBatchLight(String batchNo) {
        ChainVerifyVO vo = new ChainVerifyVO();
        vo.setBatchNo(batchNo);
        vo.setVerifiedAt(LocalDateTime.now());
        vo.setChainEnabled(contractService.isEnabled());
        List<ChainRecord> records = listByBatch(batchNo);
        vo.setTotalRecords(records.size());
        if (!contractService.isEnabled()) {
            vo.setPassed(false);
            vo.setMessage("演示模式：未启用链上存证");
            return vo;
        }
        int matched = 0;
        for (ChainRecord record : records) {
            if (!Constants.CHAIN_SUCCESS.equals(record.getStatus())) {
                continue;
            }
            String localHash = recompute(record);
            if (localHash != null && localHash.equals(record.getDataHash())
                    && contractService.checkReceipt(record.getTxHash())) {
                matched++;
            }
        }
        vo.setMatchedRecords(matched);
        vo.setPassed(!records.isEmpty() && matched == records.size());
        vo.setMessage(vo.isPassed() ? "存证校验通过" : "存证校验未通过");
        return vo;
    }

    /**
     * 按业务类型重算链下摘要
     */
    public String recompute(ChainRecord record) {
        Long bizId = record.getBizId();
        switch (record.getBizType()) {
            case Constants.EVENT_BATCH: {
                BizBatch batch = batchMapper.selectOne(Wrappers.<BizBatch>lambdaQuery()
                        .eq(BizBatch::getBatchNo, record.getBatchNo()).last("limit 1"));
                return batch == null ? null : DigestHelper.batch(batch);
            }
            case Constants.EVENT_PLANTING: {
                BizPlantingRecord r = plantingMapper.selectById(bizId);
                return r == null ? null : DigestHelper.planting(r);
            }
            case Constants.EVENT_HARVEST: {
                BizHarvestRecord r = harvestMapper.selectById(bizId);
                return r == null ? null : DigestHelper.harvest(r);
            }
            case Constants.EVENT_QUALITY: {
                BizQualityReport r = qualityMapper.selectById(bizId);
                return r == null ? null : DigestHelper.quality(r);
            }
            case Constants.EVENT_LOGISTICS: {
                BizLogisticsRecord r = logisticsMapper.selectById(bizId);
                return r == null ? null : DigestHelper.logistics(r);
            }
            case Constants.EVENT_SALES: {
                BizSalesRecord r = salesMapper.selectById(bizId);
                return r == null ? null : DigestHelper.sales(r);
            }
            case Constants.EVENT_ABNORMAL: {
                BizAbnormalDisposal r = abnormalMapper.selectById(bizId);
                return r == null ? null : DigestHelper.abnormal(r);
            }
            case Constants.EVENT_DISPOSAL: {
                BizAbnormalDisposal r = abnormalMapper.selectById(bizId);
                if (r == null) {
                    return null;
                }
                // 与 AbnormalService.dispose 同口径：证据根哈希必须参与复算，
                // 否则带图/带视频的处置在"一键校验"时会被误判为不一致
                String evidenceHash = fileService.evidenceHash(
                        fileService.listEvidence(Constants.EVENT_DISPOSAL, bizId));
                return DigestHelper.disposal(r, evidenceHash);
            }
            case Constants.EVENT_CORRECTION: {
                BizCorrection r = correctionMapper.selectById(bizId);
                return r == null ? null : DigestHelper.correction(r);
            }
            case Constants.EVENT_CLOSE:
                return DigestHelper.close(record.getBatchNo());
            default:
                return null;
        }
    }

    public List<ChainRecord> listFailed() {
        return chainRecordMapper.selectList(Wrappers.<ChainRecord>lambdaQuery()
                .eq(ChainRecord::getStatus, Constants.CHAIN_FAILED)
                .orderByDesc(ChainRecord::getId)
                .last("limit 20"));
    }

    private String labelOf(String bizType) {
        switch (bizType) {
            case Constants.EVENT_BATCH: return "批次登记";
            case Constants.EVENT_PLANTING: return "农事记录";
            case Constants.EVENT_HARVEST: return "采收记录";
            case Constants.EVENT_QUALITY: return "质检报告";
            case Constants.EVENT_LOGISTICS: return "物流交接";
            case Constants.EVENT_SALES: return "销售记录";
            case Constants.EVENT_ABNORMAL: return "异常标记";
            case Constants.EVENT_DISPOSAL: return "异常处置";
            case Constants.EVENT_CORRECTION: return "更正说明";
            case Constants.EVENT_CLOSE: return "批次结案";
            default: return "存证";
        }
    }
}
