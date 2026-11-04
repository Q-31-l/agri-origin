package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.ResultCode;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.BatchDTO;
import com.agri.trace.entity.BaseFarm;
import com.agri.trace.entity.BaseProduct;
import com.agri.trace.entity.BaseVillage;
import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizCorrection;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.entity.ChainRecord;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.BaseFarmMapper;
import com.agri.trace.mapper.BaseProductMapper;
import com.agri.trace.mapper.BaseVillageMapper;
import com.agri.trace.mapper.BizAbnormalDisposalMapper;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizCorrectionMapper;
import com.agri.trace.mapper.BizHarvestRecordMapper;
import com.agri.trace.mapper.BizLogisticsRecordMapper;
import com.agri.trace.mapper.BizPlantingRecordMapper;
import com.agri.trace.mapper.BizQualityReportMapper;
import com.agri.trace.mapper.BizSalesRecordMapper;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.ChainResult;
import com.agri.trace.sdk.TraceContractService;
import com.agri.trace.vo.BatchDetailVO;
import com.agri.trace.vo.TimelineItem;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次全生命周期管理
 */
@Service
public class BatchService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
    private BaseProductMapper productMapper;
    @Resource
    private BaseFarmMapper farmMapper;
    @Resource
    private BaseVillageMapper villageMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;

    public PageResult<BizBatch> page(long page, long size, String keyword, String status,
                                     Integer abnormalFlag, Long villageId) {
        return page(page, size, keyword, status, abnormalFlag, villageId, null, null, null, null);
    }

    /**
     * 批次分页查询：支持按批次（keyword）、主体（farmId / farmerId）、时间（startDate~endDate，按建批次时间）筛选。
     * 主体与时间筛选在数据范围（applyScope）之上叠加，均为 AND 关系，不会突破角色可见范围。
     */
    public PageResult<BizBatch> page(long page, long size, String keyword, String status,
                                     Integer abnormalFlag, Long villageId, Long farmId, Long farmerId,
                                     String startDate, String endDate) {
        LambdaQueryWrapper<BizBatch> wrapper = Wrappers.<BizBatch>lambdaQuery()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(BizBatch::getBatchNo, keyword).or()
                        .like(BizBatch::getProductName, keyword).or()
                        .like(BizBatch::getFarmerName, keyword))
                .eq(status != null && !status.isEmpty(), BizBatch::getStatus, status)
                .eq(abnormalFlag != null, BizBatch::getAbnormalFlag, abnormalFlag)
                .eq(villageId != null, BizBatch::getVillageId, villageId)
                .eq(farmId != null, BizBatch::getFarmId, farmId)
                .eq(farmerId != null, BizBatch::getFarmerId, farmerId)
                .apply(startDate != null && !startDate.isEmpty(), "DATE(create_time) >= {0}", startDate)
                .apply(endDate != null && !endDate.isEmpty(), "DATE(create_time) <= {0}", endDate)
                .orderByDesc(BizBatch::getId);
        applyScope(wrapper, UserContext.require());
        return PageResult.of(batchMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<BizBatch> listForSelect(String status) {
        LoginUser user = UserContext.require();
        LambdaQueryWrapper<BizBatch> wrapper = Wrappers.<BizBatch>lambdaQuery()
                .eq(status != null && !status.isEmpty(), BizBatch::getStatus, status)
                .orderByDesc(BizBatch::getId)
                .last("limit 200");
        applyScope(wrapper, user);
        return batchMapper.selectList(wrapper);
    }

    public BizBatch getByNo(String batchNo) {
        BizBatch batch = batchMapper.selectOne(Wrappers.<BizBatch>lambdaQuery()
                .eq(BizBatch::getBatchNo, batchNo).last("limit 1"));
        if (batch == null) {
            throw BizException.notFound("批次不存在：" + batchNo);
        }
        return batch;
    }

    public BizBatch getById(Long id) {
        BizBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw BizException.notFound("批次不存在");
        }
        return batch;
    }

    /**
     * 校验当前用户是否有权查看该批次
     */
    public BizBatch getAuthorized(Long id) {
        BizBatch batch = getById(id);
        LoginUser user = UserContext.require();
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())
                && !user.getUserId().equals(batch.getFarmerId())) {
            throw BizException.forbidden("无权查看其他农户的批次");
        }
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                && !user.getVillageId().equals(batch.getVillageId())) {
            throw BizException.forbidden("无权查看其他村的批次");
        }
        return batch;
    }

    @Transactional
    public Long create(BatchDTO dto) {
        LoginUser user = UserContext.require();
        if (!Constants.ROLE_PRODUCER.equals(user.getRoleCode())
                && !Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            throw BizException.forbidden("仅生产主体可创建批次");
        }

        BaseProduct product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw BizException.notFound("产品不存在");
        }

        BaseFarm farm = dto.getFarmId() == null ? null : farmMapper.selectById(dto.getFarmId());
        Long farmerId = dto.getFarmerId() != null ? dto.getFarmerId()
                : (farm == null ? null : farm.getFarmerId());
        if (farmerId == null) {
            throw BizException.badRequest("请指定种植农户或地块");
        }
        SysUser farmer = userMapper.selectById(farmerId);
        if (farmer == null) {
            throw BizException.notFound("农户不存在");
        }
        Long villageId = farm != null ? farm.getVillageId() : farmer.getVillageId();

        BizBatch batch = new BizBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setTraceCode("TC" + batch.getBatchNo().substring(2));
        batch.setProductId(product.getId());
        batch.setProductName(product.getProductName());
        batch.setVariety(product.getVariety());
        batch.setFarmId(farm == null ? null : farm.getId());
        batch.setFarmName(farm == null ? null : farm.getFarmName());
        batch.setFarmerId(farmerId);
        batch.setFarmerName(farmer.getRealName());
        batch.setVillageId(villageId);
        if (villageId != null) {
            BaseVillage village = villageMapper.selectById(villageId);
            batch.setVillageName(village == null ? null : village.getVillageName());
        }
        batch.setPlantingDate(dto.getPlantingDate());
        batch.setQuantity(dto.getQuantity());
        batch.setUnit(dto.getUnit() == null ? "kg" : dto.getUnit());
        batch.setStatus(Constants.BATCH_PLANTING);
        batch.setAuditStatus(Constants.AUDIT_PENDING);
        batch.setAbnormalFlag(0);
        batch.setChainStatus(Constants.CHAIN_NONE);
        batch.setRemark(dto.getRemark());
        batch.setCreateBy(user.getUserId());
        batchMapper.insert(batch);

        String dataHash = DigestHelper.batch(batch);
        ChainResult chain = contractService.registerBatch(batch, dataHash, user.getRealName());
        batch.setChainStatus(chain.getStatus());
        batch.setChainTxHash(chain.getTxHash());
        batch.setBlockNumber(chain.getBlockNumber());
        batchMapper.updateById(batch);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_BATCH, batch.getId(),
                "BATCH_REGISTER", dataHash, null, chain);

        return batch.getId();
    }

    /**
     * 批次核心字段不可修改：仅允许补充备注，录入错误走"更正说明"
     */
    @Transactional
    public void update(Long id, BatchDTO dto) {
        BizBatch batch = getAuthorized(id);
        if (!Constants.BATCH_PLANTING.equals(batch.getStatus())) {
            throw BizException.status("批次已进入后续流转，不可修改；如有错误请提交更正说明");
        }
        if (Constants.AUDIT_APPROVED.equals(batch.getAuditStatus())) {
            throw BizException.status("批次已通过村级核验，不可修改；如有错误请提交更正说明");
        }
        batch.setRemark(dto.getRemark());
        batchMapper.updateById(batch);
    }

    /**
     * 村级批次核验
     */
    @Transactional
    public void audit(Long id, AuditDTO dto) {
        BizBatch batch = getById(id);
        LoginUser user = UserContext.require();
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                && !user.getVillageId().equals(batch.getVillageId())) {
            throw BizException.forbidden("无权核验其他村的批次");
        }
        if (!Constants.AUDIT_PENDING.equals(batch.getAuditStatus())) {
            throw BizException.conflict("该批次已核验，请勿重复操作");
        }
        if (!Constants.AUDIT_APPROVED.equals(dto.getAuditStatus())
                && !Constants.AUDIT_REJECTED.equals(dto.getAuditStatus())) {
            throw BizException.badRequest("审核结果仅支持 APPROVED / REJECTED");
        }
        batch.setAuditStatus(dto.getAuditStatus());
        batch.setAuditBy(user.getRealName());
        batch.setAuditTime(LocalDateTime.now());
        batch.setAuditRemark(dto.getAuditRemark());
        batchMapper.updateById(batch);
    }

    /**
     * 批次详情：聚合全流程数据与时间轴
     */
    public BatchDetailVO detail(Long id) {
        return assemble(getAuthorized(id));
    }

    /**
     * 公共溯源使用：不做登录用户数据范围校验（仅暴露脱敏后的时间轴）
     */
    public BatchDetailVO detailPublic(BizBatch batch) {
        return assemble(batch);
    }

    public BizBatch getByCode(String code) {
        BizBatch batch = batchMapper.selectOne(Wrappers.<BizBatch>lambdaQuery()
                .and(w -> w.eq(BizBatch::getTraceCode, code).or().eq(BizBatch::getBatchNo, code))
                .last("limit 1"));
        if (batch == null) {
            throw BizException.notFound("溯源码无效或批次不存在");
        }
        return batch;
    }

    private BatchDetailVO assemble(BizBatch batch) {
        Long id = batch.getId();
        BatchDetailVO vo = new BatchDetailVO();
        vo.setBatch(batch);
        vo.setChainEnabled(contractService.isEnabled());
        vo.setChainMessage(contractService.getChainMessage());

        vo.setPlantings(plantingMapper.selectList(Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(BizPlantingRecord::getBatchId, id).orderByAsc(BizPlantingRecord::getOperationDate)));
        vo.setHarvests(harvestMapper.selectList(Wrappers.<BizHarvestRecord>lambdaQuery()
                .eq(BizHarvestRecord::getBatchId, id).orderByAsc(BizHarvestRecord::getId)));
        vo.setQualities(qualityMapper.selectList(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getBatchId, id).orderByAsc(BizQualityReport::getId)));
        vo.setLogistics(logisticsMapper.selectList(Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(BizLogisticsRecord::getBatchId, id).orderByAsc(BizLogisticsRecord::getId)));
        vo.setSales(salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(BizSalesRecord::getBatchId, id).orderByAsc(BizSalesRecord::getId)));
        vo.setDisposals(abnormalMapper.selectList(Wrappers.<BizAbnormalDisposal>lambdaQuery()
                .eq(BizAbnormalDisposal::getBatchId, id).orderByAsc(BizAbnormalDisposal::getId)));
        vo.setCorrections(correctionMapper.selectList(Wrappers.<BizCorrection>lambdaQuery()
                .eq(BizCorrection::getBatchId, id).orderByAsc(BizCorrection::getId)));
        vo.setChainRecords(chainService.listByBatch(batch.getBatchNo()));
        vo.setTimeline(buildTimeline(vo));
        return vo;
    }

    /**
     * 时间轴构建（批次详情与公共溯源共用）
     */
    public List<TimelineItem> buildTimeline(BatchDetailVO vo) {
        BizBatch batch = vo.getBatch();
        Map<String, ChainRecord> chainMap = new HashMap<>();
        for (ChainRecord record : vo.getChainRecords()) {
            chainMap.put(record.getBizType() + "#" + record.getBizId(), record);
        }

        List<TimelineItem> items = new ArrayList<>();
        TimelineItem created = new TimelineItem();
        created.setTime(batch.getCreateTime() == null ? "" : batch.getCreateTime().format(DATETIME_FMT));
        created.setType(Constants.EVENT_BATCH);
        created.setTitle("批次建档");
        created.setOperator(batch.getFarmerName());
        created.setStatus(batch.getAuditStatus());
        created.setDetail("创建批次 " + batch.getBatchNo() + "，产品：" + batch.getProductName()
                + "，预计产量 " + batch.getQuantity() + batch.getUnit());
        attachChain(created, chainMap.get(Constants.EVENT_BATCH + "#" + batch.getId()));
        items.add(created);

        for (BizPlantingRecord r : vo.getPlantings()) {
            TimelineItem item = new TimelineItem();
            item.setTime(r.getOperationDate() == null ? "" : r.getOperationDate().format(DATE_FMT));
            item.setType(Constants.EVENT_PLANTING);
            item.setTitle("农事记录 · " + recordTypeLabel(r.getRecordType()));
            item.setOperator(r.getOperatorName());
            item.setStatus(r.getAuditStatus());
            item.setDetail(r.getContent() + (r.getMaterialName() == null ? ""
                    : "（投入品：" + r.getMaterialName() + " " + (r.getDosage() == null ? "" : r.getDosage()) + "）"));
            attachChain(item, chainMap.get(Constants.EVENT_PLANTING + "#" + r.getId()));
            items.add(item);
        }

        for (BizHarvestRecord r : vo.getHarvests()) {
            TimelineItem item = new TimelineItem();
            item.setTime(r.getHarvestDate() == null ? "" : r.getHarvestDate().format(DATE_FMT));
            item.setType(Constants.EVENT_HARVEST);
            item.setTitle("采收登记");
            item.setOperator(r.getOperatorName());
            item.setStatus(r.getAuditStatus());
            item.setDetail("采收 " + r.getQuantity() + r.getUnit() + "，等级 " + r.getQualityLevel());
            attachChain(item, chainMap.get(Constants.EVENT_HARVEST + "#" + r.getId()));
            items.add(item);
        }

        for (BizQualityReport r : vo.getQualities()) {
            TimelineItem item = new TimelineItem();
            item.setTime(r.getInspectDate() == null ? "" : r.getInspectDate().format(DATE_FMT));
            item.setType(Constants.EVENT_QUALITY);
            item.setTitle("质量检测 · " + ("PASS".equals(r.getInspectResult()) ? "合格" : "不合格"));
            item.setOperator(r.getInspectOrg());
            item.setStatus(r.getInspectResult());
            item.setDetail("报告编号 " + r.getReportNo() + "；" + (r.getConclusion() == null ? "" : r.getConclusion()));
            attachChain(item, chainMap.get(Constants.EVENT_QUALITY + "#" + r.getId()));
            items.add(item);
        }

        for (BizLogisticsRecord r : vo.getLogistics()) {
            TimelineItem item = new TimelineItem();
            item.setTime(r.getDepartTime() == null ? "" : r.getDepartTime().format(DATETIME_FMT));
            item.setType(Constants.EVENT_LOGISTICS);
            item.setTitle("物流交接");
            item.setOperator(r.getCarrierName());
            item.setStatus(r.getStatus());
            item.setDetail(r.getFromOrg() + " → " + r.getToOrg() + "，运单 " + r.getLogisticsNo()
                    + "，车辆 " + r.getVehicleNo());
            attachChain(item, chainMap.get(Constants.EVENT_LOGISTICS + "#" + r.getId()));
            items.add(item);
        }

        for (BizSalesRecord r : vo.getSales()) {
            TimelineItem item = new TimelineItem();
            item.setTime(r.getSalesDate() == null ? "" : r.getSalesDate().format(DATE_FMT));
            item.setType(Constants.EVENT_SALES);
            item.setTitle("终端销售");
            item.setOperator(r.getSellerName());
            item.setStatus("SOLD");
            item.setDetail(r.getMarketName() + "，单价 ¥" + r.getPrice() + "，数量 " + r.getQuantity() + r.getUnit());
            attachChain(item, chainMap.get(Constants.EVENT_SALES + "#" + r.getId()));
            items.add(item);
        }

        for (BizAbnormalDisposal r : vo.getDisposals()) {
            TimelineItem item = new TimelineItem();
            item.setTime(r.getHandleTime() == null ? "" : r.getHandleTime().format(DATETIME_FMT));
            item.setType(Constants.EVENT_ABNORMAL);
            item.setTitle("异常管控");
            item.setOperator(r.getHandlerName());
            item.setStatus(r.getStatus());
            item.setDetail(r.getDescription());
            attachChain(item, chainMap.get(Constants.EVENT_ABNORMAL + "#" + r.getId()));
            items.add(item);

            if (r.getDisposalResult() != null) {
                TimelineItem disposal = new TimelineItem();
                disposal.setTime(r.getHandleTime() == null ? "" : r.getHandleTime().format(DATETIME_FMT));
                disposal.setType(Constants.EVENT_DISPOSAL);
                disposal.setTitle("异常处置");
                disposal.setOperator(r.getHandlerName());
                disposal.setStatus(r.getStatus());
                disposal.setDetail("措施：" + r.getDisposalMeasure() + "；结果：" + r.getDisposalResult());
                attachChain(disposal, chainMap.get(Constants.EVENT_DISPOSAL + "#" + r.getId()));
                items.add(disposal);
            }
        }

        for (BizCorrection c : vo.getCorrections()) {
            TimelineItem item = new TimelineItem();
            item.setTime(c.getCreateTime() == null ? "" : c.getCreateTime().format(DATETIME_FMT));
            item.setType(Constants.EVENT_CORRECTION);
            item.setTitle("更正说明");
            item.setOperator(c.getOperatorName());
            item.setStatus("RECORDED");
            item.setDetail("更正 " + c.getBizType() + "#" + c.getBizId() + "，原因：" + c.getReason()
                    + "；说明：" + c.getContent());
            attachChain(item, chainMap.get(Constants.EVENT_CORRECTION + "#" + c.getId()));
            items.add(item);
        }

        items.sort(Comparator.comparing(TimelineItem::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    private void attachChain(TimelineItem item, ChainRecord record) {
        if (record == null) {
            item.setChainStatus(Constants.CHAIN_SKIPPED);
            return;
        }
        item.setDataHash(record.getDataHash());
        item.setFileHash(record.getFileHash());
        item.setTxHash(record.getTxHash());
        item.setBlockNumber(record.getBlockNumber());
        item.setChainStatus(record.getStatus());
    }

    private void applyScope(LambdaQueryWrapper<BizBatch> wrapper, LoginUser user) {
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getFarmerId, user.getUserId());
        } else if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getVillageId, user.getVillageId());
        } else if (Constants.ROLE_PRODUCER.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getCreateBy, user.getUserId());
        }
    }

    /**
     * 批次编码：AG + yyyyMMdd + 3 位当日序号
     */
    public synchronized String generateBatchNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "AG" + date;
        long count = batchMapper.selectCount(Wrappers.<BizBatch>lambdaQuery()
                .likeRight(BizBatch::getBatchNo, prefix));
        for (int i = 1; i <= 999; i++) {
            String candidate = prefix + String.format("%03d", count + i);
            Long exists = batchMapper.selectCount(Wrappers.<BizBatch>lambdaQuery()
                    .eq(BizBatch::getBatchNo, candidate));
            if (exists == null || exists == 0) {
                return candidate;
            }
        }
        throw new BizException(ResultCode.SERVER_ERROR, "当日批次编码已用尽");
    }

    private String recordTypeLabel(String type) {
        if (type == null) {
            return "农事";
        }
        switch (type) {
            case "SOIL": return "整地施肥";
            case "SOWING": return "播种定植";
            case "FERTILIZE": return "追肥";
            case "IRRIGATE": return "灌溉";
            case "PEST": return "病虫害防治";
            case "WEED": return "除草";
            default: return "其他农事";
        }
    }
}
