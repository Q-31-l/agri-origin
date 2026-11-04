package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.PlantingDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.entity.ChainRecord;
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
 * 农事记录（种植生长信息）
 */
@Service
public class PlantingService {

    @Resource
    private BizPlantingRecordMapper plantingMapper;
    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;

    public PageResult<BizPlantingRecord> page(long page, long size, Long batchId, String batchNo) {
        LambdaQueryWrapper<BizPlantingRecord> wrapper = Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(batchId != null, BizPlantingRecord::getBatchId, batchId)
                .like(batchNo != null && !batchNo.isEmpty(), BizPlantingRecord::getBatchNo, batchNo)
                .orderByDesc(BizPlantingRecord::getId);
        LoginUser user = UserContext.require();
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            wrapper.eq(BizPlantingRecord::getOperatorId, user.getUserId());
        }
        return PageResult.of(plantingMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<BizPlantingRecord> listByBatch(Long batchId) {
        return plantingMapper.selectList(Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(BizPlantingRecord::getBatchId, batchId)
                .orderByAsc(BizPlantingRecord::getOperationDate));
    }

    @Transactional
    public Long create(PlantingDTO dto) {
        LoginUser user = UserContext.require();
        if (!Constants.ROLE_FARMER.equals(user.getRoleCode())
                && !Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            throw BizException.forbidden("仅农户可提交农事记录");
        }
        BizBatch batch = batchService.getById(dto.getBatchId());
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())
                && !user.getUserId().equals(batch.getFarmerId())) {
            throw BizException.forbidden("无权操作其他农户的批次");
        }
        if (Constants.BATCH_ABNORMAL.equals(batch.getStatus()) || Integer.valueOf(1).equals(batch.getAbnormalFlag())) {
            throw BizException.status("异常批次已锁定，禁止新增农事记录");
        }
        if (!Constants.BATCH_PLANTING.equals(batch.getStatus())) {
            throw BizException.status("当前批次已进入后续环节，无法新增农事记录");
        }
        if (!Constants.AUDIT_APPROVED.equals(batch.getAuditStatus())) {
            throw BizException.status("批次尚未通过村级核验，暂不可录入农事数据");
        }
        if (dto.getOperationDate().isAfter(java.time.LocalDate.now())) {
            throw BizException.badRequest("作业日期不能晚于今天");
        }

        // 重复提交校验：同批次 + 同类型 + 同日期
        Long duplicate = plantingMapper.selectCount(Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(BizPlantingRecord::getBatchId, dto.getBatchId())
                .eq(BizPlantingRecord::getRecordType, dto.getRecordType())
                .eq(BizPlantingRecord::getOperationDate, dto.getOperationDate()));
        if (duplicate != null && duplicate > 0) {
            throw BizException.conflict("该批次当日的同类农事记录已存在，请勿重复提交");
        }

        BizPlantingRecord record = new BizPlantingRecord();
        record.setBatchId(batch.getId());
        record.setBatchNo(batch.getBatchNo());
        record.setRecordType(dto.getRecordType());
        record.setOperationDate(dto.getOperationDate());
        record.setContent(dto.getContent());
        record.setMaterialName(dto.getMaterialName());
        record.setDosage(dto.getDosage());
        record.setOperatorId(user.getUserId());
        record.setOperatorName(user.getRealName());
        record.setImages(dto.getImages());
        record.setGps(dto.getGps());
        record.setAuditStatus(Constants.AUDIT_PENDING);
        record.setChainStatus(Constants.CHAIN_NONE);
        plantingMapper.insert(record);

        String dataHash = DigestHelper.planting(record);
        record.setDataHash(dataHash);
        ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_PLANTING,
                String.valueOf(record.getId()), dataHash, null, user.getRealName());
        record.setChainStatus(chain.getStatus());
        record.setChainTxHash(chain.getTxHash());
        record.setBlockNumber(chain.getBlockNumber());
        plantingMapper.updateById(record);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_PLANTING, record.getId(),
                "PLANTING_RECORD", dataHash, null, chain);

        return record.getId();
    }

    @Transactional
    public void audit(Long id, AuditDTO dto) {
        BizPlantingRecord record = plantingMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("农事记录不存在");
        }
        BizBatch batch = batchService.getAuthorized(record.getBatchId());
        LoginUser user = UserContext.require();
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                && !user.getVillageId().equals(batch.getVillageId())) {
            throw BizException.forbidden("无权审核其他村的农事记录");
        }
        if (!Constants.AUDIT_PENDING.equals(record.getAuditStatus())) {
            throw BizException.conflict("该记录已审核，请勿重复操作");
        }
        record.setAuditStatus(dto.getAuditStatus());
        record.setAuditRemark(dto.getAuditRemark());
        plantingMapper.updateById(record);
    }
}
