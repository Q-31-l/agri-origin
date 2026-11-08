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
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.BizPlantingRecordMapper;
import com.agri.trace.mapper.SysUserMapper;
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
    private SysUserMapper userMapper;
    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;
    @Resource
    private FileService fileService;

    public PageResult<BizPlantingRecord> page(long page, long size, Long batchId, String batchNo) {
        LambdaQueryWrapper<BizPlantingRecord> wrapper = Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(batchId != null, BizPlantingRecord::getBatchId, batchId)
                .like(batchNo != null && !batchNo.isEmpty(), BizPlantingRecord::getBatchNo, batchNo)
                .orderByDesc(BizPlantingRecord::getId);
        LoginUser user = UserContext.require();
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            wrapper.eq(BizPlantingRecord::getOperatorId, user.getUserId());
        } else {
            // 村管看本村、生产主体看本人建档批次的农事记录
            List<Long> visible = batchService.visibleBatchIds(user);
            if (visible != null) {
                if (visible.isEmpty()) {
                    return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
                }
                wrapper.in(BizPlantingRecord::getBatchId, visible);
            }
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
        if (dto.getOperationDate().isAfter(java.time.LocalDate.now())) {
            throw BizException.badRequest("作业日期不能晚于今天");
        }

        // 两种建档方式：①关联已有批次（生产主体已建档、已核验）；②未指派批次时手写作物名，
        // 提交后由村管审核，审核通过自动生成批次。
        BizBatch batch = null;
        if (dto.getBatchId() != null) {
            batch = batchService.getById(dto.getBatchId());
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
            // 重复提交校验：同批次 + 同类型 + 同日期
            Long duplicate = plantingMapper.selectCount(Wrappers.<BizPlantingRecord>lambdaQuery()
                    .eq(BizPlantingRecord::getBatchId, dto.getBatchId())
                    .eq(BizPlantingRecord::getRecordType, dto.getRecordType())
                    .eq(BizPlantingRecord::getOperationDate, dto.getOperationDate()));
            if (duplicate != null && duplicate > 0) {
                throw BizException.conflict("该批次当日的同类农事记录已存在，请勿重复提交");
            }
        } else {
            if (dto.getProductName() == null || dto.getProductName().trim().isEmpty()) {
                throw BizException.badRequest("请选择批次，或手写作物名（如：草莓 / 玉米）");
            }
        }

        BizPlantingRecord record = new BizPlantingRecord();
        record.setBatchId(batch == null ? null : batch.getId());
        record.setBatchNo(batch == null ? null : batch.getBatchNo());
        record.setProductName(dto.getProductName() == null ? null : dto.getProductName().trim());
        record.setRecordType(dto.getRecordType());
        record.setOperationDate(dto.getOperationDate());
        record.setContent(dto.getContent());
        record.setMaterialName(dto.getMaterialName());
        record.setDosage(dto.getDosage());
        record.setOperatorId(user.getUserId());
        record.setOperatorName(user.getRealName());
        record.setImages(dto.getImages());
        record.setGps(dto.getGps());
        record.setLocation(dto.getLocation());
        record.setAuditStatus(Constants.AUDIT_PENDING);
        record.setChainStatus(Constants.CHAIN_NONE);
        plantingMapper.insert(record);

        // 实证绑定：图片/视频文件哈希聚合为证据根哈希，随业务摘要一并上链
        java.util.List<com.agri.trace.vo.EvidenceVO> evidences = fileService.bindEvidence(
                dto.getEvidenceIds(), Constants.EVENT_PLANTING, record.getId());
        String evidenceHash = fileService.evidenceHash(evidences);
        String dataHash = DigestHelper.planting(record, evidenceHash);
        record.setDataHash(dataHash);
        plantingMapper.updateById(record);

        // 有关联批次时立即上链；无批次的上链放在村管审核通过、自动建档批次之后补做
        if (batch != null) {
            batchService.ensureChainRegistered(batch, user.getRealName());
            ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_PLANTING,
                    String.valueOf(record.getId()), dataHash, evidenceHash, user.getRealName());
            record.setChainStatus(chain.getStatus());
            record.setChainTxHash(chain.getTxHash());
            record.setBlockNumber(chain.getBlockNumber());
            plantingMapper.updateById(record);
            chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_PLANTING, record.getId(),
                    "PLANTING_RECORD", dataHash, evidenceHash, chain);
        }

        return record.getId();
    }

    @Transactional
    public void audit(Long id, AuditDTO dto) {
        BizPlantingRecord record = plantingMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("农事记录不存在");
        }
        LoginUser user = UserContext.require();
        if (!Constants.AUDIT_PENDING.equals(record.getAuditStatus())) {
            throw BizException.conflict("该记录已审核，请勿重复操作");
        }

        // 无批次：农户未指派批次、手写作物直接提交，村管审核通过后自动建档批次并补上链
        if (record.getBatchId() == null) {
            if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
                SysUser farmer = userMapper.selectById(record.getOperatorId());
                if (farmer == null || user.getVillageId() == null
                        || !user.getVillageId().equals(farmer.getVillageId())) {
                    throw BizException.forbidden("无权审核其他村的农事记录");
                }
            }
            record.setAuditStatus(dto.getAuditStatus());
            record.setAuditRemark(dto.getAuditRemark());
            if (Constants.AUDIT_APPROVED.equals(dto.getAuditStatus())) {
                BizBatch batch = batchService.createAutoFromPlanting(record, user.getRealName());
                record.setBatchId(batch.getId());
                record.setBatchNo(batch.getBatchNo());
                java.util.List<com.agri.trace.vo.EvidenceVO> evs = fileService.listEvidence(
                        Constants.EVENT_PLANTING, record.getId());
                String evidenceHash = fileService.evidenceHash(evs);
                ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_PLANTING,
                        String.valueOf(record.getId()), record.getDataHash(), evidenceHash, user.getRealName());
                record.setChainStatus(chain.getStatus());
                record.setChainTxHash(chain.getTxHash());
                record.setBlockNumber(chain.getBlockNumber());
                chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_PLANTING, record.getId(),
                        "PLANTING_RECORD", record.getDataHash(), evidenceHash, chain);
            }
            plantingMapper.updateById(record);
            return;
        }

        // 有关联批次：走原有逻辑
        BizBatch batch = batchService.getAuthorized(record.getBatchId());
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                && !user.getVillageId().equals(batch.getVillageId())) {
            throw BizException.forbidden("无权审核其他村的农事记录");
        }
        record.setAuditStatus(dto.getAuditStatus());
        record.setAuditRemark(dto.getAuditRemark());
        plantingMapper.updateById(record);
    }
}
