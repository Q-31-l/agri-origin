package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.CorrectionDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizCorrection;
import com.agri.trace.mapper.BizCorrectionMapper;
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

/**
 * 更正说明：追加式纠错，保留全部历史，不覆盖原始记录
 */
@Service
public class CorrectionService {

    @Resource
    private BizCorrectionMapper correctionMapper;
    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;
    @Resource
    private FileService fileService;

    public PageResult<BizCorrection> page(long page, long size, Long batchId) {
        LambdaQueryWrapper<BizCorrection> wrapper = Wrappers.<BizCorrection>lambdaQuery()
                .eq(batchId != null, BizCorrection::getBatchId, batchId)
                .orderByDesc(BizCorrection::getId);
        // 更正说明里带着"改了什么、为什么改"，比业务记录更敏感，必须同样按数据范围收窄
        java.util.List<Long> visible = batchService.visibleBatchIds(UserContext.require());
        if (visible != null) {
            if (visible.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
            }
            wrapper.in(BizCorrection::getBatchId, visible);
        }
        return PageResult.of(correctionMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Transactional
    public Long create(CorrectionDTO dto) {
        LoginUser user = UserContext.require();
        BizBatch batch = batchService.getAuthorized(dto.getBatchId());
        if (Constants.BATCH_CLOSED.equals(batch.getStatus())) {
            throw BizException.status("批次已结案，不可再提交更正");
        }

        BizCorrection correction = new BizCorrection();
        correction.setBatchId(batch.getId());
        correction.setBatchNo(batch.getBatchNo());
        correction.setBizType(dto.getBizType());
        correction.setBizId(dto.getBizId());
        correction.setReason(dto.getReason());
        correction.setContent(dto.getContent());
        correction.setOperatorId(user.getUserId());
        correction.setOperatorName(user.getRealName());
        correction.setChainStatus(Constants.CHAIN_NONE);
        correctionMapper.insert(correction);

        // 更正留痕：更正说明也可附实证（图片/视频），证据根哈希随更正摘要一并上链
        java.util.List<com.agri.trace.vo.EvidenceVO> evidences = fileService.bindEvidence(
                dto.getEvidenceIds(), Constants.EVENT_CORRECTION, correction.getId());
        String evidenceHash = fileService.evidenceHash(evidences);

        String dataHash = DigestHelper.correction(correction, evidenceHash);
        correction.setDataHash(dataHash);
        // 合约写方法带 batchExists 校验：演示/历史批次可能从未上链，先补注册再写记录
        batchService.ensureChainRegistered(batch, user.getRealName());
        ChainResult chain = contractService.addCorrection(batch.getBatchNo(), dto.getBizType(),
                String.valueOf(dto.getBizId()), dataHash, user.getRealName());
        correction.setChainStatus(chain.getStatus());
        correction.setChainTxHash(chain.getTxHash());
        correction.setBlockNumber(chain.getBlockNumber());
        correctionMapper.updateById(correction);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_CORRECTION, correction.getId(),
                "DATA_CORRECTION", dataHash, evidenceHash, chain);
        return correction.getId();
    }
}
