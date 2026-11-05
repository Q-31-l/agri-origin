package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.DisposeDTO;
import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.mapper.BizAbnormalDisposalMapper;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizLogisticsRecordMapper;
import com.agri.trace.mapper.BizSalesRecordMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.ChainResult;
import com.agri.trace.sdk.TraceContractService;
import com.agri.trace.vo.AbnormalTraceVO;
import com.agri.trace.vo.EvidenceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常批次管控：处置、结案、上下游追溯
 */
@Service
public class AbnormalService {

    @Resource
    private BizAbnormalDisposalMapper abnormalMapper;
    @Resource
    private BizBatchMapper batchMapper;
    @Resource
    private BizLogisticsRecordMapper logisticsMapper;
    @Resource
    private BizSalesRecordMapper salesMapper;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;
    @Resource
    private com.agri.trace.websocket.TraceWebSocketHandler wsHandler;
    @Resource
    private FileService fileService;

    public PageResult<BizAbnormalDisposal> page(long page, long size, String batchNo, String status) {
        LambdaQueryWrapper<BizAbnormalDisposal> wrapper = Wrappers.<BizAbnormalDisposal>lambdaQuery()
                .like(batchNo != null && !batchNo.isEmpty(), BizAbnormalDisposal::getBatchNo, batchNo)
                .eq(status != null && !status.isEmpty(), BizAbnormalDisposal::getStatus, status)
                .orderByDesc(BizAbnormalDisposal::getId);
        return PageResult.of(abnormalMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Transactional
    public void dispose(Long id, DisposeDTO dto) {
        BizAbnormalDisposal disposal = abnormalMapper.selectById(id);
        if (disposal == null) {
            throw BizException.notFound("异常处置记录不存在");
        }
        if (Constants.ABNORMAL_DONE.equals(disposal.getStatus())) {
            throw BizException.conflict("该异常已处置完成，请勿重复提交");
        }
        LoginUser user = UserContext.require();
        disposal.setDisposalMeasure(dto.getDisposalMeasure());
        disposal.setDisposalResult(dto.getDisposalResult());
        disposal.setHandlerId(user.getUserId());
        disposal.setHandlerName(user.getRealName());
        disposal.setHandleTime(LocalDateTime.now());
        disposal.setStatus(Constants.ABNORMAL_DONE);

        // 实证绑定：图片 / 视频先挂到本条处置记录，再算"证据根哈希"并入上链摘要。
        // 于是链上存证覆盖的不只是处置文字，还有每一张现场照片、每一段处置视频。
        List<EvidenceVO> evidences = fileService.bindEvidence(
                dto.getEvidenceIds(), Constants.EVENT_DISPOSAL, disposal.getId());
        String evidenceHash = fileService.evidenceHash(evidences);

        String disposalHash = DigestHelper.disposal(disposal, evidenceHash);
        disposal.setDisposalHash(disposalHash);

        // 先落库做版本比对，再上链。
        // 顺序很关键：并发的第二个人会在这里就拿到 409，
        // 而不是在链上留下一条重复的"处置完成"存证（链上记录删不掉）。
        Db.updated(abnormalMapper.updateById(disposal));

        BizBatch batch = batchMapper.selectById(disposal.getBatchId());
        ChainResult chain = contractService.disposeAbnormal(disposal.getBatchNo(),
                dto.getDisposalResult(), disposalHash, user.getRealName());
        disposal.setChainStatus(chain.getStatus());
        disposal.setChainTxHash(chain.getTxHash());
        disposal.setBlockNumber(chain.getBlockNumber());
        Db.updated(abnormalMapper.updateById(disposal));
        chainService.saveRecord(disposal.getBatchNo(), Constants.EVENT_DISPOSAL, disposal.getId(),
                "ABNORMAL_DISPOSAL", disposalHash, evidenceHash, chain);

        if (batch != null && Constants.BATCH_ABNORMAL.equals(batch.getStatus())) {
            batch.setAbnormalFlag(1);
            Db.updated(batchMapper.updateById(batch));
        }

        wsHandler.broadcast("abnormal", disposal.getBatchNo(), "DISPOSE", "异常处置已登记");
    }

    /**
     * 结案：处置完成后方可结案，结案后批次永久留痕
     */
    @Transactional
    public void close(Long batchId) {
        BizBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw BizException.notFound("批次不存在");
        }
        if (!Constants.BATCH_ABNORMAL.equals(batch.getStatus())) {
            throw BizException.status("仅异常管控中的批次可结案");
        }
        Long unfinished = abnormalMapper.selectCount(Wrappers.<BizAbnormalDisposal>lambdaQuery()
                .eq(BizAbnormalDisposal::getBatchId, batchId)
                .ne(BizAbnormalDisposal::getStatus, Constants.ABNORMAL_DONE));
        if (unfinished != null && unfinished > 0) {
            throw BizException.status("存在未完成的处置任务，无法结案");
        }
        LoginUser user = UserContext.require();
        String closeHash = DigestHelper.close(batch.getBatchNo());
        // 同上：先落库再上链，避免并发结案在链上留下两条闭合存证
        batch.setStatus(Constants.BATCH_CLOSED);
        Db.updated(batchMapper.updateById(batch));
        ChainResult chain = contractService.closeBatch(batch.getBatchNo(), closeHash, user.getRealName());
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_CLOSE, batch.getId(),
                "BATCH_CLOSE", closeHash, null, chain);

        wsHandler.broadcast("batch", batch.getBatchNo(), "CLOSE", "异常批次已结案");
    }

    /**
     * 反向追溯：上游来源 + 下游流向 + 责任主体
     */
    public AbnormalTraceVO trace(Long disposalId) {
        BizAbnormalDisposal disposal = abnormalMapper.selectById(disposalId);
        if (disposal == null) {
            throw BizException.notFound("异常处置记录不存在");
        }
        BizBatch batch = batchMapper.selectById(disposal.getBatchId());
        if (batch == null) {
            throw BizException.notFound("批次不存在");
        }

        AbnormalTraceVO vo = new AbnormalTraceVO();
        vo.setBatch(batch);
        List<BizAbnormalDisposal> disposals = abnormalMapper.selectList(
                Wrappers.<BizAbnormalDisposal>lambdaQuery()
                        .eq(BizAbnormalDisposal::getBatchId, batch.getId())
                        .orderByAsc(BizAbnormalDisposal::getId));
        vo.setDisposals(disposals);

        // 每条处置的实证材料：异常上报（ABNORMAL）与处置完成（DISPOSAL）两阶段合并
        List<Long> disposalIds = new ArrayList<>();
        for (BizAbnormalDisposal d : disposals) {
            disposalIds.add(d.getId());
        }
        Map<String, List<EvidenceVO>> reported = fileService.evidenceMap(Constants.EVENT_ABNORMAL, disposalIds);
        Map<String, List<EvidenceVO>> handled = fileService.evidenceMap(Constants.EVENT_DISPOSAL, disposalIds);
        Map<Long, List<EvidenceVO>> evidenceByDisposal = new LinkedHashMap<>();
        for (Long did : disposalIds) {
            List<EvidenceVO> merged = new ArrayList<>();
            merged.addAll(reported.getOrDefault(Constants.EVENT_ABNORMAL + "#" + did, new ArrayList<>()));
            merged.addAll(handled.getOrDefault(Constants.EVENT_DISPOSAL + "#" + did, new ArrayList<>()));
            if (!merged.isEmpty()) {
                evidenceByDisposal.put(did, merged);
            }
        }
        vo.setEvidences(evidenceByDisposal);

        // 上游：同农户/同地块的更早批次
        vo.setUpstream(batchMapper.selectList(Wrappers.<BizBatch>lambdaQuery()
                .ne(BizBatch::getId, batch.getId())
                .and(w -> {
                    w.eq(BizBatch::getFarmerId, batch.getFarmerId());
                    if (batch.getFarmId() != null) {
                        w.or().eq(BizBatch::getFarmId, batch.getFarmId());
                    }
                })
                .lt(BizBatch::getId, batch.getId())
                .orderByDesc(BizBatch::getId)
                .last("limit 10")));

        // 下游：物流与销售流向
        List<Map<String, Object>> downstream = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<BizLogisticsRecord> logistics = logisticsMapper.selectList(Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(BizLogisticsRecord::getBatchId, batch.getId()));
        for (BizLogisticsRecord r : logistics) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "物流");
            item.put("time", r.getDepartTime() == null ? "" : r.getDepartTime().format(fmt));
            item.put("from", r.getFromOrg());
            item.put("to", r.getToOrg());
            item.put("carrier", r.getCarrierName());
            item.put("detail", "运单 " + r.getLogisticsNo() + "，车辆 " + r.getVehicleNo());
            downstream.add(item);
        }
        List<BizSalesRecord> sales = salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(BizSalesRecord::getBatchId, batch.getId()));
        for (BizSalesRecord r : sales) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "销售");
            item.put("time", r.getSalesDate() == null ? "" : r.getSalesDate().toString());
            item.put("from", r.getSellerName());
            item.put("to", r.getMarketName());
            item.put("carrier", r.getSellerName());
            item.put("detail", "数量 " + r.getQuantity() + r.getUnit() + "，单价 ¥" + r.getPrice());
            downstream.add(item);
        }
        vo.setDownstream(downstream);

        // 责任主体定位
        List<Map<String, Object>> responsibilities = new ArrayList<>();
        responsibilities.add(responsibility("生产责任", batch.getFarmerName(),
                "农户（" + batch.getVillageName() + "）", "农事投入品使用与采收执行"));
        if (batch.getVillageName() != null) {
            responsibilities.add(responsibility("网格监管责任", batch.getVillageName() + "村委会",
                    "村级管理员", "辖区农户数据审核与批次核验"));
        }
        if (disposal.getSourceType() != null && "QUALITY".equals(disposal.getSourceType())) {
            responsibilities.add(responsibility("检测责任", "检测机构",
                    "检测机构", "质检报告编号 " + disposal.getSourceId()));
        }
        if (!logistics.isEmpty()) {
            responsibilities.add(responsibility("运输责任", logistics.get(0).getCarrierName(),
                    "物流企业", "运输温控与交接"));
        }
        if (!sales.isEmpty()) {
            responsibilities.add(responsibility("销售责任", sales.get(0).getSellerName(),
                    "销售商", "销售登记与批次核销"));
        }
        vo.setResponsibilities(responsibilities);
        return vo;
    }

    private Map<String, Object> responsibility(String duty, String subject, String role, String detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("duty", duty);
        map.put("subject", subject);
        map.put("role", role);
        map.put("detail", detail);
        return map;
    }
}
