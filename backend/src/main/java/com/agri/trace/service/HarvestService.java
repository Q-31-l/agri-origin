package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.HarvestDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizHarvestRecordMapper;
import com.agri.trace.mapper.BizPlantingRecordMapper;
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
import java.util.List;

/**
 * 采收登记
 */
@Service
public class HarvestService {

    @Resource
    private BizHarvestRecordMapper harvestMapper;
    @Resource
    private BizPlantingRecordMapper plantingMapper;
    @Resource
    private BizBatchMapper batchMapper;
    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;
    @Resource
    private FileService fileService;

    public PageResult<BizHarvestRecord> page(long page, long size, Long batchId, String batchNo) {
        LambdaQueryWrapper<BizHarvestRecord> wrapper = Wrappers.<BizHarvestRecord>lambdaQuery()
                .eq(batchId != null, BizHarvestRecord::getBatchId, batchId)
                .like(batchNo != null && !batchNo.isEmpty(), BizHarvestRecord::getBatchNo, batchNo)
                .orderByDesc(BizHarvestRecord::getId);
        if (Constants.ROLE_FARMER.equals(UserContext.roleCode())) {
            wrapper.eq(BizHarvestRecord::getOperatorId, UserContext.userId());
        } else {
            // 村管看本村、生产主体看本人建档批次的采收记录
            List<Long> visible = batchService.visibleBatchIds(UserContext.require());
            if (visible != null) {
                if (visible.isEmpty()) {
                    return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
                }
                wrapper.in(BizHarvestRecord::getBatchId, visible);
            }
        }
        return PageResult.of(harvestMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Transactional
    public Long create(HarvestDTO dto) {
        LoginUser user = UserContext.require();
        if (!Constants.ROLE_FARMER.equals(user.getRoleCode())
                && !Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            throw BizException.forbidden("仅农户可提交采收信息");
        }
        BizBatch batch = batchService.getById(dto.getBatchId());
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())
                && !user.getUserId().equals(batch.getFarmerId())) {
            throw BizException.forbidden("无权操作其他农户的批次");
        }
        if (Constants.BATCH_ABNORMAL.equals(batch.getStatus())) {
            throw BizException.status("异常批次已锁定，禁止采收登记");
        }
        if (!Constants.AUDIT_APPROVED.equals(batch.getAuditStatus())) {
            throw BizException.status("批次尚未通过村级核验");
        }
        if (!Constants.BATCH_PLANTING.equals(batch.getStatus())) {
            throw BizException.status("该批次已采收，请勿重复提交");
        }

        // 采收是"这一季作业的收口"，前面录的农事必须已经被村管核实过；
        // 否则会出现"农事还挂着待审核、批次却已经采完卖完"，进度条永远关不掉。
        Long pendingPlanting = plantingMapper.selectCount(Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(BizPlantingRecord::getBatchId, batch.getId())
                .eq(BizPlantingRecord::getAuditStatus, Constants.AUDIT_PENDING));
        if (pendingPlanting != null && pendingPlanting > 0) {
            throw BizException.status("该批次还有 " + pendingPlanting
                    + " 条农事记录等待村管审核，审核通过后再提交采收");
        }

        // 一个批次仅允许一条「有效」采收记录：被村管驳回的那条不算占用，
        // 否则农户第一次填错被驳回后，永远被 409 挡在门外，批次卡死在种植中。
        Long exists = harvestMapper.selectCount(Wrappers.<BizHarvestRecord>lambdaQuery()
                .eq(BizHarvestRecord::getBatchId, dto.getBatchId())
                .ne(BizHarvestRecord::getAuditStatus, Constants.AUDIT_REJECTED));
        if (exists != null && exists > 0) {
            throw BizException.conflict("该批次已存在采收记录，请勿重复提交");
        }

        BizHarvestRecord record = new BizHarvestRecord();
        record.setBatchId(batch.getId());
        record.setBatchNo(batch.getBatchNo());
        record.setHarvestDate(dto.getHarvestDate());
        record.setQuantity(dto.getQuantity());
        record.setUnit(dto.getUnit() == null ? batch.getUnit() : dto.getUnit());
        record.setQualityLevel(dto.getQualityLevel());
        record.setWeather(dto.getWeather());
        record.setOperatorId(user.getUserId());
        record.setOperatorName(user.getRealName());
        record.setImages(dto.getImages());
        record.setRemark(dto.getRemark());
        record.setAuditStatus(Constants.AUDIT_PENDING);
        record.setChainStatus(Constants.CHAIN_NONE);
        harvestMapper.insert(record);


        // 实证绑定：图片/视频文件哈希聚合为证据根哈希，随业务摘要一并上链
        java.util.List<com.agri.trace.vo.EvidenceVO> evidences = fileService.bindEvidence(
                dto.getEvidenceIds(), Constants.EVENT_HARVEST, record.getId());
        String evidenceHash = fileService.evidenceHash(evidences);

        String dataHash = DigestHelper.harvest(record, evidenceHash);
        record.setDataHash(dataHash);
        // 合约写方法带 batchExists 校验：演示/历史批次可能从未上链，先补注册再写记录
        batchService.ensureChainRegistered(batch, user.getRealName());
        ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_HARVEST,
                String.valueOf(record.getId()), dataHash, evidenceHash, user.getRealName());
        record.setChainStatus(chain.getStatus());
        record.setChainTxHash(chain.getTxHash());
        record.setBlockNumber(chain.getBlockNumber());
        harvestMapper.updateById(record);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_HARVEST, record.getId(),
                "HARVEST_RECORD", dataHash, evidenceHash, chain);

        // 只记录采收日期这一客观事实，批次状态必须等村管审核通过后再推进（见 audit）。
        // 若在提交时就置为 HARVESTED，会出现"外层已采收、进度里待审核"的矛盾状态，
        // 并且质检机构能在采收未审核时直接出报告，整条审核链就失去了卡点。
        batch.setHarvestDate(dto.getHarvestDate());
        Db.updated(batchMapper.updateById(batch));
        return record.getId();
    }

    @Transactional
    public void audit(Long id, AuditDTO dto) {
        BizHarvestRecord record = harvestMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("采收记录不存在");
        }
        BizBatch batch = batchService.getAuthorized(record.getBatchId());
        LoginUser user = UserContext.require();
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                && !user.getVillageId().equals(batch.getVillageId())) {
            throw BizException.forbidden("无权审核其他村的采收记录");
        }
        if (!Constants.AUDIT_PENDING.equals(record.getAuditStatus())) {
            throw BizException.conflict("该记录已审核，请勿重复操作");
        }
        record.setAuditStatus(dto.getAuditStatus());
        record.setAuditRemark(dto.getAuditRemark());
        record.setAuditBy(user.getRealName());
        record.setAuditTime(java.time.LocalDateTime.now());
        harvestMapper.updateById(record);

        // 驳回：批次回退到种植中，农户可修正后重新提交；否则批次停在 HARVESTED，
        // 质检机构面对一条"未通过审核"的采收记录照样能出报告，链路就断了。
        if (Constants.AUDIT_REJECTED.equals(dto.getAuditStatus())) {
            batch.setHarvestDate(null);
            batch.setStatus(Constants.BATCH_PLANTING);
            Db.updated(batchMapper.updateById(batch));
        } else if (Constants.AUDIT_APPROVED.equals(dto.getAuditStatus())) {
            // 审核通过才是"真正采收完成"：批次状态在此时推进，外层标签与内层进度因此始终一致。
            batch.setHarvestDate(record.getHarvestDate());
            batch.setStatus(Constants.BATCH_HARVESTED);
            Db.updated(batchMapper.updateById(batch));
        }
    }
}
