package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.QualityDTO;
import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.mapper.BizAbnormalDisposalMapper;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizQualityReportMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.ChainResult;
import com.agri.trace.sdk.TraceContractService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 质量检测：报告上传、合格判定、不合格批次熔断
 */
@Service
public class QualityService {

    @Resource
    private BizQualityReportMapper qualityMapper;
    @Resource
    private BizBatchMapper batchMapper;
    @Resource
    private BizAbnormalDisposalMapper abnormalMapper;
    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;
    @Resource
    private FileService fileService;

    public PageResult<BizQualityReport> page(long page, long size, Long batchId, String batchNo, String result) {
        LambdaQueryWrapper<BizQualityReport> wrapper = Wrappers.<BizQualityReport>lambdaQuery()
                .eq(batchId != null, BizQualityReport::getBatchId, batchId)
                .like(batchNo != null && !batchNo.isEmpty(), BizQualityReport::getBatchNo, batchNo)
                .eq(result != null && !result.isEmpty(), BizQualityReport::getInspectResult, result)
                .orderByDesc(BizQualityReport::getId);
        // 数据范围：农户看本人、村管看本村、生产主体看本人建档批次，监管/管理员不受限
        List<Long> visible = batchService.visibleBatchIds(UserContext.require());
        if (visible != null) {
            if (visible.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
            wrapper.in(BizQualityReport::getBatchId, visible);
        }
        return PageResult.of(qualityMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<BizQualityReport> listByBatch(Long batchId) {
        return qualityMapper.selectList(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getBatchId, batchId)
                .orderByDesc(BizQualityReport::getId));
    }

    @Transactional
    public Long create(QualityDTO dto) {
        LoginUser user = UserContext.require();
        if (!Constants.ROLE_INSPECTOR.equals(user.getRoleCode())
                && !Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            throw BizException.forbidden("仅检测机构可上传质检报告");
        }
        BizBatch batch = batchService.getById(dto.getBatchId());
        if (Constants.BATCH_ABNORMAL.equals(batch.getStatus()) || Constants.BATCH_CLOSED.equals(batch.getStatus())) {
            throw BizException.status("该批次已处于异常管控/结案状态，不可重复检测");
        }
        if (!Constants.BATCH_HARVESTED.equals(batch.getStatus())
                && !Constants.BATCH_QUALIFIED.equals(batch.getStatus())) {
            throw BizException.status("仅已采收批次可提交质检报告");
        }
        if (!"PASS".equals(dto.getInspectResult()) && !"FAIL".equals(dto.getInspectResult())) {
            throw BizException.badRequest("检测结论仅支持 PASS / FAIL");
        }
        Long exists = qualityMapper.selectCount(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getReportNo, dto.getReportNo()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("检测报告编号已存在，请勿重复提交");
        }
        Long sameDay = qualityMapper.selectCount(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getBatchId, dto.getBatchId())
                .eq(BizQualityReport::getInspectDate, dto.getInspectDate()));
        if (sameDay != null && sameDay > 0) {
            throw BizException.conflict("该批次当日已提交质检报告，请勿重复提交");
        }
        if (dto.getFileHash() == null || dto.getFileHash().isEmpty()) {
            throw BizException.badRequest("请先上传质检报告文件，系统需对文件摘要上链存证");
        }

        BizQualityReport report = new BizQualityReport();
        report.setBatchId(batch.getId());
        report.setBatchNo(batch.getBatchNo());
        report.setReportNo(dto.getReportNo());
        report.setInspectOrg(user.getOrgName());
        report.setInspectorId(user.getUserId());
        report.setInspectorName(user.getRealName());
        report.setInspectDate(dto.getInspectDate());
        report.setInspectResult(dto.getInspectResult());
        report.setStandardNo(dto.getStandardNo());
        report.setItemsJson(dto.getItemsJson());
        report.setConclusion(dto.getConclusion());
        report.setFileName(dto.getFileName());
        report.setFilePath(dto.getFilePath());
        report.setFileHash(dto.getFileHash());
        report.setFileSize(dto.getFileSize());
        report.setAuditStatus(Constants.AUDIT_PENDING);
        report.setChainStatus(Constants.CHAIN_NONE);
        qualityMapper.insert(report);


        // 实证绑定：图片/视频文件哈希聚合为证据根哈希，随业务摘要一并上链
        java.util.List<com.agri.trace.vo.EvidenceVO> evidences = fileService.bindEvidence(
                dto.getEvidenceIds(), Constants.EVENT_QUALITY, report.getId());
        String evidenceHash = fileService.evidenceHash(evidences);

        String dataHash = DigestHelper.quality(report, evidenceHash);
        report.setDataHash(dataHash);
        // 合约写方法带 batchExists 校验：演示/历史批次可能从未上链，先补注册再写记录
        batchService.ensureChainRegistered(batch, user.getRealName());
        ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_QUALITY,
                String.valueOf(report.getId()), dataHash, evidenceHash, user.getRealName());
        report.setChainStatus(chain.getStatus());
        report.setChainTxHash(chain.getTxHash());
        report.setBlockNumber(chain.getBlockNumber());
        qualityMapper.updateById(report);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_QUALITY, report.getId(),
                "QUALITY_REPORT", dataHash, evidenceHash, chain);

        if ("PASS".equals(dto.getInspectResult())) {
            batch.setStatus(Constants.BATCH_QUALIFIED);
            Db.updated(batchMapper.updateById(batch));
        } else {
            markAbnormal(batch, report, user);
        }
        return report.getId();
    }

    /**
     * 不合格：批次熔断 + 生成待处置任务 + 链上异常标记
     */
    private void markAbnormal(BizBatch batch, BizQualityReport report, LoginUser inspector) {
        batch.setStatus(Constants.BATCH_ABNORMAL);
        batch.setAbnormalFlag(1);
        Db.updated(batchMapper.updateById(batch));

        BizAbnormalDisposal disposal = new BizAbnormalDisposal();
        disposal.setBatchId(batch.getId());
        disposal.setBatchNo(batch.getBatchNo());
        disposal.setSourceType("QUALITY");
        disposal.setSourceId(report.getId());
        disposal.setAbnormalType(inferAbnormalType(report));
        disposal.setDescription("质检报告 " + report.getReportNo() + " 判定不合格："
                + (report.getConclusion() == null ? "" : report.getConclusion()));
        disposal.setStatus("HANDLING");
        disposal.setHandlerId(inspector.getUserId());
        disposal.setHandlerName(inspector.getRealName());
        disposal.setHandleTime(LocalDateTime.now());
        disposal.setChainStatus(Constants.CHAIN_NONE);
        abnormalMapper.insert(disposal);
        disposal.setVersion(0);

        String abnormalHash = DigestHelper.abnormal(disposal);
        disposal.setAbnormalHash(abnormalHash);
        // 合约写方法带 batchExists 校验：演示/历史批次可能从未上链，先补注册再写记录
        batchService.ensureChainRegistered(batch, inspector.getRealName());
        ChainResult chain = contractService.markAbnormal(batch.getBatchNo(), disposal.getDescription(),
                abnormalHash, inspector.getRealName());
        disposal.setChainStatus(chain.getStatus());
        disposal.setChainTxHash(chain.getTxHash());
        disposal.setBlockNumber(chain.getBlockNumber());
        Db.updated(abnormalMapper.updateById(disposal));
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_ABNORMAL, disposal.getId(),
                "QUALITY_FAIL", abnormalHash, report.getFileHash(), chain);
    }

    private String inferAbnormalType(BizQualityReport report) {
        String text = (report.getConclusion() == null ? "" : report.getConclusion())
                + (report.getItemsJson() == null ? "" : report.getItemsJson());
        if (text.contains("农药") || text.contains("毒死蜱") || text.contains("残留")) {
            return "PESTICIDE";
        }
        if (text.contains("铅") || text.contains("镉") || text.contains("重金属")) {
            return "HEAVY_METAL";
        }
        if (text.contains("菌") || text.contains("微生物")) {
            return "MICROBE";
        }
        return "OTHER";
    }


    @Transactional
    public void audit(Long id, AuditDTO dto) {
        BizQualityReport report = qualityMapper.selectById(id);
        if (report == null) {
            throw BizException.notFound("质检报告不存在");
        }
        if (!Constants.AUDIT_PENDING.equals(report.getAuditStatus())) {
            throw BizException.conflict("该报告已审核，请勿重复操作");
        }
        report.setAuditStatus(dto.getAuditStatus());
        report.setAuditRemark(dto.getAuditRemark());
        qualityMapper.updateById(report);
    }
}
