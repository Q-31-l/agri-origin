package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.LogisticsDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizLogisticsRecordMapper;
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
 * 物流交接
 */
@Service
public class LogisticsService {

    @Resource
    private BizLogisticsRecordMapper logisticsMapper;
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

    public PageResult<BizLogisticsRecord> page(long page, long size, Long batchId, String batchNo) {
        LambdaQueryWrapper<BizLogisticsRecord> wrapper = Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(batchId != null, BizLogisticsRecord::getBatchId, batchId)
                .like(batchNo != null && !batchNo.isEmpty(), BizLogisticsRecord::getBatchNo, batchNo)
                .orderByDesc(BizLogisticsRecord::getId);
        if (Constants.ROLE_LOGISTICS.equals(UserContext.roleCode())) {
            wrapper.eq(BizLogisticsRecord::getCarrierId, UserContext.userId());
        } else {
            // 非物流角色按批次数据范围收窄：生产主体只能看到自己建档批次的运输记录
            java.util.List<Long> visible = batchService.visibleBatchIds(UserContext.require());
            if (visible != null) {
                if (visible.isEmpty()) {
                    return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
                }
                wrapper.in(BizLogisticsRecord::getBatchId, visible);
            }
        }
        return PageResult.of(logisticsMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Transactional
    public Long create(LogisticsDTO dto) {
        LoginUser user = UserContext.require();
        if (!Constants.ROLE_LOGISTICS.equals(user.getRoleCode())
                && !Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            throw BizException.forbidden("仅物流企业可登记物流交接信息");
        }
        BizBatch batch = batchService.getById(dto.getBatchId());
        if (Constants.BATCH_ABNORMAL.equals(batch.getStatus()) || Integer.valueOf(1).equals(batch.getAbnormalFlag())) {
            throw BizException.status("异常批次已被熔断，禁止发运");
        }
        if (!Constants.BATCH_QUALIFIED.equals(batch.getStatus())
                && !Constants.BATCH_IN_TRANSIT.equals(batch.getStatus())) {
            throw BizException.status("仅质检合格批次可进入物流环节");
        }
        Long exists = logisticsMapper.selectCount(Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(BizLogisticsRecord::getLogisticsNo, dto.getLogisticsNo()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("运单号已存在，请勿重复提交");
        }

        BizLogisticsRecord record = new BizLogisticsRecord();
        record.setBatchId(batch.getId());
        record.setBatchNo(batch.getBatchNo());
        record.setLogisticsNo(dto.getLogisticsNo());
        record.setFromOrg(dto.getFromOrg());
        record.setToOrg(dto.getToOrg());
        record.setCarrierId(user.getUserId());
        record.setCarrierName(user.getOrgName());
        record.setVehicleNo(dto.getVehicleNo());
        record.setDriverName(dto.getDriverName());
        record.setDriverPhone(dto.getDriverPhone());
        record.setDepartTime(dto.getDepartTime());
        record.setArriveTime(dto.getArriveTime());
        record.setTemperature(dto.getTemperature());
        record.setStatus(dto.getArriveTime() == null ? "DEPARTED" : "ARRIVED");
        record.setHandoverFile(dto.getHandoverFile());
        record.setFileHash(dto.getFileHash());
        record.setRemark(dto.getRemark());
        record.setAuditStatus(Constants.AUDIT_PENDING);
        record.setChainStatus(Constants.CHAIN_NONE);
        logisticsMapper.insert(record);


        // 实证绑定：图片/视频文件哈希聚合为证据根哈希，随业务摘要一并上链
        java.util.List<com.agri.trace.vo.EvidenceVO> evidences = fileService.bindEvidence(
                dto.getEvidenceIds(), Constants.EVENT_LOGISTICS, record.getId());
        String evidenceHash = fileService.evidenceHash(evidences);

        String dataHash = DigestHelper.logistics(record, evidenceHash);
        record.setDataHash(dataHash);
        // 合约写方法带 batchExists 校验：演示/历史批次可能从未上链，先补注册再写记录
        batchService.ensureChainRegistered(batch, user.getRealName());
        ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_LOGISTICS,
                String.valueOf(record.getId()), dataHash, evidenceHash, user.getRealName());
        record.setChainStatus(chain.getStatus());
        record.setChainTxHash(chain.getTxHash());
        record.setBlockNumber(chain.getBlockNumber());
        logisticsMapper.updateById(record);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_LOGISTICS, record.getId(),
                "LOGISTICS_HANDOVER", dataHash, evidenceHash, chain);

        // 运单登记不推进批次状态：监管复核通过后才置为 IN_TRANSIT（见 audit），
        // 否则销售商能在运单未认可时就开始登记销售，审核链被绕过。
        return record.getId();
    }


    @Transactional
    public void audit(Long id, AuditDTO dto) {
        BizLogisticsRecord record = logisticsMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("物流记录不存在");
        }
        if (!Constants.AUDIT_PENDING.equals(record.getAuditStatus())) {
            throw BizException.conflict("该记录已审核，请勿重复操作");
        }
        record.setAuditStatus(dto.getAuditStatus());
        record.setAuditRemark(dto.getAuditRemark());
        record.setAuditBy(UserContext.require().getRealName());
        record.setAuditTime(java.time.LocalDateTime.now());
        logisticsMapper.updateById(record);

        // 复核通过才进入"运输中"，此时销售商的下拉里才会出现该批次（准入校验在 SalesService.create）
        if (Constants.AUDIT_APPROVED.equals(dto.getAuditStatus()) && record.getBatchId() != null) {
            BizBatch batch = batchMapper.selectById(record.getBatchId());
            if (batch != null
                    && !Constants.BATCH_ABNORMAL.equals(batch.getStatus())
                    && !Constants.BATCH_CLOSED.equals(batch.getStatus())
                    && !Constants.BATCH_IN_TRANSIT.equals(batch.getStatus())
                    && !Constants.BATCH_ON_SALE.equals(batch.getStatus())
                    && !Constants.BATCH_SOLD.equals(batch.getStatus())) {
                batch.setStatus(Constants.BATCH_IN_TRANSIT);
                Db.updated(batchMapper.updateById(batch));
            }
        }
    }
}
