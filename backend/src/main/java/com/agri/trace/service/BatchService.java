package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.ResultCode;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.BatchDTO;
import com.agri.trace.dto.BatchUpdateDTO;
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
import com.agri.trace.vo.EvidenceVO;
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
    @Resource
    private com.agri.trace.websocket.TraceWebSocketHandler wsHandler;
    @Resource
    private TrustScoreService trustScoreService;
    @Resource
    private FileService fileService;

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

    /**
     * 示例溯源码（免登录场景专用）。
     *
     * <p>溯源页要放几个"试试这些溯源码"做引导，之前直接调 /batch/page，
     * 未登录访客拿到 401 后被请求拦截器整页踢到登录页——公开页面不能依赖
     * 需要鉴权的接口。这里只取编码本身，不含任何业务数据，可安全公开。
     */
    public List<String> sampleTraceCodes(int limit) {
        int n = Math.min(Math.max(limit, 1), 10);
        List<BizBatch> list = batchMapper.selectList(Wrappers.<BizBatch>lambdaQuery()
                .select(BizBatch::getTraceCode)
                .isNotNull(BizBatch::getTraceCode)
                .ne(BizBatch::getTraceCode, "")
                .orderByDesc(BizBatch::getId)
                .last("limit " + n));
        List<String> codes = new ArrayList<>(list.size());
        for (BizBatch b : list) {
            codes.add(b.getTraceCode());
        }
        return codes;
    }

    /**
     * 按批次号取批次，并校验当前用户可见（越权抛 403）。
     * 供 /chain/verify/{batchNo} 这类"以批次号为入参"的接口复用。
     */
    public BizBatch getAuthorizedByNo(String batchNo) {
        BizBatch batch = getByNo(batchNo);
        getAuthorized(batch.getId());
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
     * 校验当前用户是否有权查看该批次。
     *
     * <p>与列表查询的 {@link #applyScope} 保持同一套口径：农户看本人、村管看本村、
     * 生产主体看本人建档，监管方与管理员不受限。列表收窄而详情不收窄会形成越权读取，
     * 这里必须逐条堵住。
     */
    public BizBatch getAuthorized(Long id) {
        BizBatch batch = getById(id);
        LoginUser user = UserContext.require();
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())
                && !user.getUserId().equals(batch.getFarmerId())) {
            throw BizException.forbidden("无权查看其他农户的批次");
        }
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                && (user.getVillageId() == null || !user.getVillageId().equals(batch.getVillageId()))) {
            throw BizException.forbidden("无权查看其他村的批次");
        }
        if (Constants.ROLE_PRODUCER.equals(user.getRoleCode())
                && !user.getUserId().equals(batch.getCreateBy())) {
            throw BizException.forbidden("无权查看其他主体建档的批次");
        }
        return batch;
    }

    /**
     * 当前用户在业务表上可见的批次 id 集合，用于质检/物流/销售等"子表"查询收窄。
     *
     * @return null 表示不受限（监管方、管理员），此时调用方不加 in 条件
     */
    public List<Long> visibleBatchIds(LoginUser user) {
        LambdaQueryWrapper<BizBatch> wrapper = Wrappers.<BizBatch>lambdaQuery().select(BizBatch::getId);
        if (!scopeByRole(wrapper, user)) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (BizBatch b : batchMapper.selectList(wrapper)) {
            ids.add(b.getId());
        }
        return ids;
    }

    /**
     * 当前用户可见的批次号集合。存证表 chain_record 只落 batch_no、没有 batch_id，
     * 看板上的存证条数/成功率必须按批次号收窄，否则农户会看到全站存证总量。
     *
     * @return null 表示不受限（监管方、管理员）
     */
    public List<String> visibleBatchNos(LoginUser user) {
        LambdaQueryWrapper<BizBatch> wrapper = Wrappers.<BizBatch>lambdaQuery().select(BizBatch::getBatchNo);
        if (!scopeByRole(wrapper, user)) {
            return null;
        }
        List<String> nos = new ArrayList<>();
        for (BizBatch b : batchMapper.selectList(wrapper)) {
            nos.add(b.getBatchNo());
        }
        return nos;
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
        // 插入时 version 由数据库默认值 0 兜底，实体里仍是 null；
        // 回填后紧接着的上链状态回写才会带上版本条件，避免 MyBatis-Plus 打 warn 并跳过锁
        batch.setVersion(0);

        String dataHash = DigestHelper.batch(batch);
        ChainResult chain = contractService.registerBatch(batch, dataHash, user.getRealName());
        batch.setChainStatus(chain.getStatus());
        batch.setChainTxHash(chain.getTxHash());
        batch.setBlockNumber(chain.getBlockNumber());
        Db.updated(batchMapper.updateById(batch));
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_BATCH, batch.getId(),
                "BATCH_REGISTER", dataHash, null, chain);

        return batch.getId();
    }

    /**
     * 确保批次已在链上注册（懒注册）。
     * <p>
     * 合约除 registerBatch 外的所有写方法都带 batchExists 校验，而演示种子数据、
     * 历史批次可能从未上链，不补注册会让农事、采收、质检、物流、销售、异常处置、
     * 更正、关闭等所有环节的写操作全部 revert（前端表现为"交易执行失败"）。
     * 这里在首次写入前按需补注册，成功后回写链状态并记录存证流水。
     */
    @Transactional
    public void ensureChainRegistered(BizBatch batch, String operatorName) {
        if (batch == null || !contractService.isEnabled()) {
            return;
        }
        if (Constants.CHAIN_SUCCESS.equals(batch.getChainStatus())) {
            return;
        }
        // 链上可能已存在而本地状态未回写，先探测一次，避免 registerBatch 撞 "batch already exists"
        Boolean onChain = contractService.batchExistsOnChain(batch.getBatchNo());
        if (Boolean.TRUE.equals(onChain)) {
            batch.setChainStatus(Constants.CHAIN_SUCCESS);
            Db.updated(batchMapper.updateById(batch));
            return;
        }
        String dataHash = DigestHelper.batch(batch);
        ChainResult chain = contractService.registerBatch(batch, dataHash, operatorName);
        if (!chain.isSuccess()) {
            throw BizException.chain("批次 " + batch.getBatchNo() + " 链上注册失败：" + chain.getMessage());
        }
        batch.setChainStatus(chain.getStatus());
        batch.setChainTxHash(chain.getTxHash());
        batch.setBlockNumber(chain.getBlockNumber());
        Db.updated(batchMapper.updateById(batch));
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_BATCH, batch.getId(),
                "BATCH_REGISTER", dataHash, null, chain);
    }

    /**
     * 运维修复：把所有尚未上链的历史/演示批次补注册到链上。
     * <p>
     * 逐条独立处理（本方法不加事务），单条失败不影响其余批次；
     * 链上已存在的批次只回写本地状态，不重复注册。
     */
    public Map<String, Object> repairUnchainedBatches(String operatorName) {
        List<BizBatch> list = batchMapper.selectList(Wrappers.<BizBatch>lambdaQuery()
                .and(w -> w.ne(BizBatch::getChainStatus, Constants.CHAIN_SUCCESS)
                        .or().isNull(BizBatch::getChainStatus))
                .orderByAsc(BizBatch::getId));
        int repaired = 0;
        int already = 0;
        List<String> failed = new ArrayList<>();
        for (BizBatch b : list) {
            try {
                if (Boolean.TRUE.equals(contractService.batchExistsOnChain(b.getBatchNo()))) {
                    b.setChainStatus(Constants.CHAIN_SUCCESS);
                    Db.updated(batchMapper.updateById(b));
                    already++;
                    continue;
                }
                ensureChainRegistered(b, operatorName);
                repaired++;
            } catch (Exception e) {
                failed.add(b.getBatchNo() + "：" + e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", list.size());
        result.put("repaired", repaired);
        result.put("alreadyOnChain", already);
        result.put("failed", failed.size());
        result.put("failedDetails", failed);
        return result;
    }

    /**
     * 批次核心字段不可修改：仅允许补充备注，录入错误走"更正说明"
     */
    @Transactional
    public void update(Long id, BatchUpdateDTO dto) {
        BizBatch batch = getAuthorized(id);
        if (!Constants.BATCH_PLANTING.equals(batch.getStatus())) {
            throw BizException.status("批次已进入后续流转，不可修改；如有错误请提交更正说明");
        }
        if (Constants.AUDIT_APPROVED.equals(batch.getAuditStatus())) {
            throw BizException.status("批次已通过村级核验，不可修改；如有错误请提交更正说明");
        }
        batch.setRemark(dto.getRemark());
        Db.updated(batchMapper.updateById(batch));
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
        // 两位村管同时点"通过"时，后到的那次会在这里拿到 0 行 → 409，而不是把前一次的结果悄悄覆盖
        Db.updated(batchMapper.updateById(batch));

        wsHandler.broadcast("batch", batch.getBatchNo(), "AUDIT",
                Constants.AUDIT_APPROVED.equals(dto.getAuditStatus()) ? "批次已核验通过" : "批次核验未通过");
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
        vo.setTrust(trustScoreService.evaluate(vo));
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

        // 实证索引：key = "业务类型#记录id"，一次批量查齐，避免逐条 N+1
        Map<String, List<EvidenceVO>> evidence = new HashMap<>();
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_PLANTING, idsOfPlanting(vo)));
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_HARVEST, idsOfHarvest(vo)));
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_QUALITY, idsOfQuality(vo)));
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_LOGISTICS, idsOfLogistics(vo)));
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_SALES, idsOfSales(vo)));
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_ABNORMAL, idsOfDisposal(vo)));
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_DISPOSAL, idsOfDisposal(vo)));
        evidence.putAll(fileService.evidenceMap(Constants.EVENT_CORRECTION, idsOfCorrection(vo)));

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
            item.setEvidences(evidence.get(Constants.EVENT_PLANTING + "#" + r.getId()));
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
            item.setEvidences(evidence.get(Constants.EVENT_HARVEST + "#" + r.getId()));
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
            item.setEvidences(evidence.get(Constants.EVENT_QUALITY + "#" + r.getId()));
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
            item.setEvidences(evidence.get(Constants.EVENT_LOGISTICS + "#" + r.getId()));
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
            item.setEvidences(evidence.get(Constants.EVENT_SALES + "#" + r.getId()));
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
            item.setEvidences(evidence.get(Constants.EVENT_ABNORMAL + "#" + r.getId()));
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
                disposal.setEvidences(evidence.get(Constants.EVENT_DISPOSAL + "#" + r.getId()));
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
            item.setEvidences(evidence.get(Constants.EVENT_CORRECTION + "#" + c.getId()));
            items.add(item);
        }

        items.sort(Comparator.comparing(TimelineItem::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    private List<Long> idsOfPlanting(BatchDetailVO vo) {
        List<Long> ids = new ArrayList<>();
        for (BizPlantingRecord r : vo.getPlantings()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private List<Long> idsOfHarvest(BatchDetailVO vo) {
        List<Long> ids = new ArrayList<>();
        for (BizHarvestRecord r : vo.getHarvests()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private List<Long> idsOfQuality(BatchDetailVO vo) {
        List<Long> ids = new ArrayList<>();
        for (BizQualityReport r : vo.getQualities()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private List<Long> idsOfLogistics(BatchDetailVO vo) {
        List<Long> ids = new ArrayList<>();
        for (BizLogisticsRecord r : vo.getLogistics()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private List<Long> idsOfSales(BatchDetailVO vo) {
        List<Long> ids = new ArrayList<>();
        for (BizSalesRecord r : vo.getSales()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private List<Long> idsOfDisposal(BatchDetailVO vo) {
        List<Long> ids = new ArrayList<>();
        for (BizAbnormalDisposal r : vo.getDisposals()) {
            ids.add(r.getId());
        }
        return ids;
    }

    private List<Long> idsOfCorrection(BatchDetailVO vo) {
        List<Long> ids = new ArrayList<>();
        for (BizCorrection c : vo.getCorrections()) {
            ids.add(c.getId());
        }
        return ids;
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

    /**
     * 数据范围的唯一实现：列表、子表、看板、存证流水全部走这里，避免各处口径漂移。
     *
     * @return true 表示已按角色收窄（可能收窄为空集）；false 表示不受限（监管方、管理员）
     */
    private boolean scopeByRole(LambdaQueryWrapper<BizBatch> wrapper, LoginUser user) {
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getFarmerId, user.getUserId());
            return true;
        }
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            if (user.getVillageId() == null) {
                // 未绑定村落的村管不应看到任何批次。用恒假条件表达，
                // 不能写成 village_id = null（SQL 里永远不成立，却依赖 NULL 语义、也容易被误读）
                wrapper.apply("1 = 0");
            } else {
                wrapper.eq(BizBatch::getVillageId, user.getVillageId());
            }
            return true;
        }
        if (Constants.ROLE_PRODUCER.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getCreateBy, user.getUserId());
            return true;
        }
        return false;
    }

    private void applyScope(LambdaQueryWrapper<BizBatch> wrapper, LoginUser user) {
        scopeByRole(wrapper, user);
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
