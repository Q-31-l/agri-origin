package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
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

    public PageResult<BizLogisticsRecord> page(long page, long size, Long batchId, String batchNo) {
        LambdaQueryWrapper<BizLogisticsRecord> wrapper = Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(batchId != null, BizLogisticsRecord::getBatchId, batchId)
                .like(batchNo != null && !batchNo.isEmpty(), BizLogisticsRecord::getBatchNo, batchNo)
                .orderByDesc(BizLogisticsRecord::getId);
        if (Constants.ROLE_LOGISTICS.equals(UserContext.roleCode())) {
            wrapper.eq(BizLogisticsRecord::getCarrierId, UserContext.userId());
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
        record.setChainStatus(Constants.CHAIN_NONE);
        logisticsMapper.insert(record);

        String dataHash = DigestHelper.logistics(record);
        record.setDataHash(dataHash);
        ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_LOGISTICS,
                String.valueOf(record.getId()), dataHash, record.getFileHash(), user.getRealName());
        record.setChainStatus(chain.getStatus());
        record.setChainTxHash(chain.getTxHash());
        record.setBlockNumber(chain.getBlockNumber());
        logisticsMapper.updateById(record);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_LOGISTICS, record.getId(),
                "LOGISTICS_HANDOVER", dataHash, record.getFileHash(), chain);

        if (Constants.BATCH_QUALIFIED.equals(batch.getStatus())) {
            batch.setStatus(Constants.BATCH_IN_TRANSIT);
            Db.updated(batchMapper.updateById(batch));
        }
        return record.getId();
    }
}
