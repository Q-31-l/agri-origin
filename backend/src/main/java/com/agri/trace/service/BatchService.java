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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        PageResult<BizBatch> result = PageResult.of(batchMapper.selectPage(new Page<>(page, size), wrapper));
        fillPendingStage(result.getRecords());
        return result;
    }

    /**
     * 给列表补充"当前待审环节"：批次 status 只在审核通过后推进，
     * 所以只看 status 会漏掉"已提交、等审核"的环节。
     * 一次 in 查询按环节顺序取每批最早的那个待审环节，避免逐行查库。
     */
    private void fillPendingStage(List<BizBatch> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (BizBatch b : rows) {
            ids.add(b.getId());
        }
        Map<Long, String> pending = new HashMap<>();
        // 顺序即流程顺序：先命中先写入，最终留下的是流程上最靠前的待审环节
        for (BizHarvestRecord r : harvestMapper.selectList(Wrappers.<BizHarvestRecord>lambdaQuery()
                .select(BizHarvestRecord::getBatchId)
                .in(BizHarvestRecord::getBatchId, ids)
                .eq(BizHarvestRecord::getAuditStatus, Constants.AUDIT_PENDING))) {
            pending.putIfAbsent(r.getBatchId(), "采收登记");
        }
        for (BizQualityReport r : qualityMapper.selectList(Wrappers.<BizQualityReport>lambdaQuery()
                .select(BizQualityReport::getBatchId)
                .in(BizQualityReport::getBatchId, ids)
                .eq(BizQualityReport::getAuditStatus, Constants.AUDIT_PENDING))) {
            pending.putIfAbsent(r.getBatchId(), "质量检测");
        }
        for (BizLogisticsRecord r : logisticsMapper.selectList(Wrappers.<BizLogisticsRecord>lambdaQuery()
                .select(BizLogisticsRecord::getBatchId)
                .in(BizLogisticsRecord::getBatchId, ids)
                .eq(BizLogisticsRecord::getAuditStatus, Constants.AUDIT_PENDING))) {
            pending.putIfAbsent(r.getBatchId(), "物流运输");
        }
        for (BizSalesRecord r : salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                .select(BizSalesRecord::getBatchId)
                .in(BizSalesRecord::getBatchId, ids)
                .eq(BizSalesRecord::getAuditStatus, Constants.AUDIT_PENDING))) {
            pending.putIfAbsent(r.getBatchId(), "终端销售");
        }
        for (BizBatch b : rows) {
            // 已结案 / 异常熔断的批次不再流转，谈"待审"没有意义（进度里显示的是异常处置）
            if (Constants.BATCH_CLOSED.equals(b.getStatus()) || Constants.BATCH_ABNORMAL.equals(b.getStatus())) {
                continue;
            }
            // 村级核验是流程第一环，优先级最高
            if (Constants.AUDIT_PENDING.equals(b.getAuditStatus())) {
                b.setPendingStage("村级核验");
            } else if (pending.containsKey(b.getId())) {
                b.setPendingStage(pending.get(b.getId()));
            }
        }
    }

    public List<BizBatch> listForSelect(String status) {
        return listForSelect(status, null);
    }

    /**
     * 批次下拉。
     *
     * <p>{@code usable=true} 用于「新增业务记录」：只给能真正录进去的批次
     * （指定状态 + 已通过村级核验 + 无异常锁定）。以前这里不分用途，
     * 下拉里混着"已关闭""待核验"的批次，农户选中后点提交才被后端拒绝，
     * 属于典型的"能选不能存"。
     *
     * <p>{@code status} 支持逗号分隔多状态：复检要 HARVESTED+QUALIFIED，
     * 物流中转要 QUALIFIED+IN_TRANSIT，分批销售要 IN_TRANSIT+ON_SALE，
     * 只传单值时前端就会漏掉一半合法批次。
     */
    public List<BizBatch> listForSelect(String status, Boolean usable) {
        LoginUser user = UserContext.require();
        boolean onlyUsable = Boolean.TRUE.equals(usable);
        LambdaQueryWrapper<BizBatch> wrapper = Wrappers.<BizBatch>lambdaQuery();
        if (status != null && !status.trim().isEmpty()) {
            List<String> codes = new ArrayList<>();
            for (String s : status.split(",")) {
                if (!s.trim().isEmpty()) {
                    codes.add(s.trim());
                }
            }
            if (codes.size() == 1) {
                wrapper.eq(BizBatch::getStatus, codes.get(0));
            } else if (codes.size() > 1) {
                wrapper.in(BizBatch::getStatus, codes);
            }
        }
        wrapper.eq(onlyUsable, BizBatch::getAuditStatus, Constants.AUDIT_APPROVED)
                // abnormal_flag 可能是 NULL（历史数据），"<> 1" 会把 NULL 一起排除，
                // 必须显式写成 (IS NULL OR = 0)
                .and(onlyUsable, w -> w.isNull(BizBatch::getAbnormalFlag)
                        .or().eq(BizBatch::getAbnormalFlag, 0))
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
        // 多取一批候选再挑，避免"最近 N 条"里混进异常熔断/质检不合格的批次。
        // 示例码是访客点开的第一个样本，若满屏红字（质检不合格、异常熔断），
        // 会被当成系统坏了。
        int pool = Math.min(n * 8, 80);
        List<BizBatch> candidates = batchMapper.selectList(Wrappers.<BizBatch>lambdaQuery()
                // 显式 select 时 MP 不会自动带上主键，排序要用到 id，必须手动列出
                .select(BizBatch::getId, BizBatch::getTraceCode,
                        BizBatch::getStatus, BizBatch::getAbnormalFlag)
                .isNotNull(BizBatch::getTraceCode)
                .ne(BizBatch::getTraceCode, "")
                .orderByDesc(BizBatch::getId)
                .last("limit " + pool));

        List<BizBatch> good = new ArrayList<>(candidates.size());
        for (BizBatch b : candidates) {
            Integer flag = b.getAbnormalFlag();
            if (flag != null && flag != 0) {
                continue;
            }
            if (sampleRank(b.getStatus()) >= 9) {
                continue;
            }
            good.add(b);
        }
        // 已经走到流通环节的批次信息最完整，优先展示；其次才是"已闭环"的历史批次
        good.sort(java.util.Comparator
                .comparingInt((BizBatch b) -> sampleRank(b.getStatus()))
                .thenComparing(BizBatch::getId, java.util.Comparator.reverseOrder()));

        List<BizBatch> pick = good.isEmpty() ? candidates : good;
        List<String> codes = new ArrayList<>(n);
        for (BizBatch b : pick) {
            if (codes.size() >= n) {
                break;
            }
            codes.add(b.getTraceCode());
        }
        return codes;
    }

    /** 示例码的状态优先级：越靠近消费者、信息越完整越靠前；>=9 表示不适合当示例。 */
    private static int sampleRank(String status) {
        if (status == null) {
            return 9;
        }
        switch (status) {
            case "ON_SALE":
                return 0;
            case "SOLD":
                return 1;
            case "CLOSED":
                return 2;
            case "IN_TRANSIT":
                return 3;
            case "QUALIFIED":
                return 4;
            default:
                return 9;
        }
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

        BaseProduct product = resolveProduct(dto);
        if (product == null) {
            throw BizException.badRequest("请选择产品或手写产品名称");
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

        registerBatchOnChain(batch, user.getRealName());

        return batch.getId();
    }

    /**
     * 解析建批次的产品：优先用已有品类 id，手写产品名时"同名复用、无则新建"。
     *
     * <p>建批次不再要求先由管理员维护产品品类：生产主体手写"草莓 / 玉米 / 高粱"等
     * 任意作物即可建档，系统自动落到 base_product（同名复用避免重复），批次照常上链。
     * 这样农户想种什么就建什么，不被固定品类下拉卡住。</p>
     */
    private BaseProduct resolveProduct(BatchDTO dto) {
        if (dto.getProductId() != null) {
            BaseProduct p = productMapper.selectById(dto.getProductId());
            if (p == null) {
                throw BizException.notFound("产品不存在");
            }
            return p;
        }
        String name = dto.getProductName();
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        name = name.trim();
        if (name.length() > 30) {
            throw BizException.badRequest("产品名过长，请精简后填写");
        }
        BaseProduct existing = productMapper.selectOne(Wrappers.<BaseProduct>lambdaQuery()
                .eq(BaseProduct::getProductName, name).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        BaseProduct p = new BaseProduct();
        p.setProductCode("P-" + (System.currentTimeMillis() % 1000000));
        p.setProductName(name);
        p.setVariety(dto.getVariety());
        p.setUnit(dto.getUnit() == null ? "kg" : dto.getUnit());
        productMapper.insert(p);
        return p;
    }

    /**
     * 农户未指派批次、直接手写作物录农事，村管审核通过后据此自动建档批次。
     * 批次落在农户所属村，建档即视为已核验（村管审核农事通过 = 核验了这条生产记录）。
     */
    @Transactional
    public BizBatch createAutoFromPlanting(BizPlantingRecord record, String auditByName) {
        SysUser farmer = userMapper.selectById(record.getOperatorId());
        if (farmer == null) {
            throw BizException.notFound("农户不存在");
        }
        String productName = record.getProductName();
        BaseProduct product = resolveProductByName(productName);

        BizBatch batch = new BizBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setTraceCode("TC" + batch.getBatchNo().substring(2));
        batch.setProductId(product == null ? null : product.getId());
        batch.setProductName(productName);
        batch.setVariety(product == null ? null : product.getVariety());
        batch.setFarmerId(farmer.getId());
        batch.setFarmerName(farmer.getRealName());
        batch.setVillageId(farmer.getVillageId());
        if (farmer.getVillageId() != null) {
            BaseVillage village = villageMapper.selectById(farmer.getVillageId());
            batch.setVillageName(village == null ? null : village.getVillageName());
        }
        batch.setPlantingDate(record.getOperationDate());
        batch.setQuantity(java.math.BigDecimal.ZERO);
        batch.setUnit("kg");
        batch.setStatus(Constants.BATCH_PLANTING);
        batch.setAuditStatus(Constants.AUDIT_APPROVED);
        batch.setAuditBy(auditByName);
        batch.setAuditTime(LocalDateTime.now());
        batch.setAbnormalFlag(0);
        batch.setChainStatus(Constants.CHAIN_NONE);
        batch.setCreateBy(record.getOperatorId());
        batch.setRemark("农户手写作物录农事，村管审核通过自动建档");
        batchMapper.insert(batch);
        batch.setVersion(0);

        registerBatchOnChain(batch, farmer.getRealName());
        return batch;
    }

    /**
     * 建档上链（带换号重试）。
     *
     * <p>合约 {@code registerBatch} 的语义是"批次号存在即 revert"，而链上数据不可删除：
     * 只要历史上用过这个号（哪怕本地记录已删、库被重置过），这里就会拿到
     * {@code batch already exists}，strict 模式下直接抛异常、把整笔建档/审核事务回滚——
     * 表现就是村管点"通过"弹「上链失败，操作已回滚」。
     *
     * <p>号在 {@link #generateBatchNo()} 里已经做过链上探测，正常一次就成；
     * 这里再兜一层并发与探测失败的情况：撞号就换下一个可用号、重算摘要再写一次，
     * 最多 3 次。其它链上错误（未授权、链不可用等）原样抛出，不掩盖真问题。
     */
    private void registerBatchOnChain(BizBatch batch, String operatorName) {
        BizException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String dataHash = DigestHelper.batch(batch);
                ChainResult chain = contractService.registerBatch(batch, dataHash, operatorName);
                batch.setChainStatus(chain.getStatus());
                batch.setChainTxHash(chain.getTxHash());
                batch.setBlockNumber(chain.getBlockNumber());
                Db.updated(batchMapper.updateById(batch));
                chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_BATCH, batch.getId(),
                        "BATCH_REGISTER", dataHash, null, chain);
                return;
            } catch (BizException e) {
                if (!isBatchAlreadyExists(e.getMessage())) {
                    throw e;
                }
                last = e;
                log.warn("批次号 {} 在链上已被占用，换号后重试（第 {} 次）", batch.getBatchNo(), attempt + 1);
                String newNo = generateBatchNo();
                batch.setBatchNo(newNo);
                batch.setTraceCode("TC" + newNo.substring(2));
                Db.updated(batchMapper.updateById(batch));
                // 换号会走一次带版本条件的更新，版本号随之 +1；
                // 重新读一次，避免下一次回写链上状态时因版本过期拿到 0 行被判 409。
                BizBatch fresh = batchMapper.selectById(batch.getId());
                if (fresh != null) {
                    batch.setVersion(fresh.getVersion());
                }
            }
        }
        throw last;
    }

    private boolean isBatchAlreadyExists(String message) {
        if (message == null) {
            return false;
        }
        // TraceContractService 会把合约的英文 revert 翻成中文，两种写法都要认
        String lower = message.toLowerCase();
        return lower.contains("batch already exists") || message.contains("已在链上存证");
    }

    private BaseProduct resolveProductByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        name = name.trim();
        BaseProduct existing = productMapper.selectOne(Wrappers.<BaseProduct>lambdaQuery()
                .eq(BaseProduct::getProductName, name).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        BaseProduct p = new BaseProduct();
        p.setProductCode("P-" + (System.currentTimeMillis() % 1000000));
        p.setProductName(name);
        p.setUnit("kg");
        productMapper.insert(p);
        return p;
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
        ChainResult chain;
        try {
            chain = contractService.registerBatch(batch, dataHash, operatorName);
        } catch (BizException e) {
            // 探测失败（链抖动）时会漏判"链上已存在"，补注册再撞一次。
            // 这种情况批次本来就在链上，业务没必要回滚，回写状态即可。
            if (isBatchAlreadyExists(e.getMessage())
                    && Boolean.TRUE.equals(contractService.batchExistsOnChain(batch.getBatchNo()))) {
                log.warn("批次 {} 已在链上，跳过补注册并回写状态", batch.getBatchNo());
                batch.setChainStatus(Constants.CHAIN_SUCCESS);
                Db.updated(batchMapper.updateById(batch));
                return;
            }
            throw e;
        }
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
        return assemble(getAuthorized(id), false);
    }

    /**
     * 公共溯源使用：不做登录用户数据范围校验（仅暴露脱敏后的时间轴）
     */
    /**
     * 对外（扫码溯源、免登录）视图。
     *
     * <p>消费者只应看到"已经通过审核"的事实：待审核/被驳回的采收、质检、物流、销售记录
     * 一律不下发。否则会出现"管理端显示待审核、消费者扫码却看到已完成"的两套真相。
     */
    public BatchDetailVO detailPublic(BizBatch batch) {
        return assemble(batch, true);
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

    private BatchDetailVO assemble(BizBatch batch, boolean publicOnly) {
        Long id = batch.getId();
        BatchDetailVO vo = new BatchDetailVO();
        vo.setBatch(batch);
        vo.setChainEnabled(contractService.isEnabled());
        vo.setChainMessage(contractService.getChainMessage());

        // publicOnly 为真时只取审核通过的记录，保证对外展示口径与审核进度一致
        vo.setPlantings(plantingMapper.selectList(Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(BizPlantingRecord::getBatchId, id)
                .eq(publicOnly, BizPlantingRecord::getAuditStatus, Constants.AUDIT_APPROVED)
                .orderByAsc(BizPlantingRecord::getOperationDate)));
        vo.setHarvests(harvestMapper.selectList(Wrappers.<BizHarvestRecord>lambdaQuery()
                .eq(BizHarvestRecord::getBatchId, id)
                .eq(publicOnly, BizHarvestRecord::getAuditStatus, Constants.AUDIT_APPROVED)
                .orderByAsc(BizHarvestRecord::getId)));
        vo.setQualities(qualityMapper.selectList(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getBatchId, id)
                .eq(publicOnly, BizQualityReport::getAuditStatus, Constants.AUDIT_APPROVED)
                .orderByAsc(BizQualityReport::getId)));
        vo.setLogistics(logisticsMapper.selectList(Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(BizLogisticsRecord::getBatchId, id)
                .eq(publicOnly, BizLogisticsRecord::getAuditStatus, Constants.AUDIT_APPROVED)
                .orderByAsc(BizLogisticsRecord::getId)));
        vo.setSales(salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(BizSalesRecord::getBatchId, id)
                .eq(publicOnly, BizSalesRecord::getAuditStatus, Constants.AUDIT_APPROVED)
                .orderByAsc(BizSalesRecord::getId)));
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
        created.setStatus(statusLabel(batch.getAuditStatus()));
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
            item.setStatus(statusLabel(r.getAuditStatus()));
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
            item.setStatus(statusLabel(r.getAuditStatus()));
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
            item.setStatus(statusLabel(r.getInspectResult()));
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
            item.setStatus(statusLabel(r.getStatus()));
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
            item.setStatus("已售出");
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
            item.setStatus(statusLabel(r.getStatus()));
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
                disposal.setStatus(statusLabel(r.getStatus()));
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
        // 以当日最大序号为基准往后取，而不是按"条数 + 1"。
        // 条数会在删过批次后回退（库里只剩 001、011 时 count=2 → 生成 003），
        // 而链上批次永久存在、删不掉，003 可能正是历史遗留的号，一建就 revert。
        long max = 0;
        BizBatch last = batchMapper.selectOne(Wrappers.<BizBatch>lambdaQuery()
                .likeRight(BizBatch::getBatchNo, prefix)
                .orderByDesc(BizBatch::getBatchNo)
                .last("limit 1"));
        if (last != null && last.getBatchNo() != null && last.getBatchNo().length() > prefix.length()) {
            try {
                max = Long.parseLong(last.getBatchNo().substring(prefix.length()));
            } catch (NumberFormatException ignore) {
                // 尾段不是纯数字（历史脏数据）时退回从 1 开始找，不影响可用性
            }
        }
        for (int i = 1; i <= 999; i++) {
            String candidate = prefix + String.format("%03d", max + i);
            Long exists = batchMapper.selectCount(Wrappers.<BizBatch>lambdaQuery()
                    .eq(BizBatch::getBatchNo, candidate));
            if (exists != null && exists > 0) {
                continue;
            }
            // 链上还有"库已删、链仍在"的历史批次号，占着就不能再用：
            // 先问链再落库，避免走到 registerBatch 才被 revert。
            if (Boolean.TRUE.equals(contractService.batchExistsOnChain(candidate))) {
                log.warn("批次号 {} 已被链上历史批次占用，跳过", candidate);
                continue;
            }
            return candidate;
        }
        throw new BizException(ResultCode.SERVER_ERROR, "当日批次编码已用尽");
    }

    /**
     * 时间轴状态给消费者看，不能把 PENDING / SOLD / DEPARTED 这类枚举原样抛到页面上。
     * 未知值原样返回，将来新增状态时至少不会变成空白。
     */
    private String statusLabel(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        switch (code) {
            case Constants.AUDIT_PENDING: return "待核验";
            case Constants.AUDIT_APPROVED: return "已核验";
            case Constants.AUDIT_REJECTED: return "已驳回";
            case "PASS": return "合格";
            case "FAIL": return "不合格";
            case "DEPARTED": return "运输中";
            case "ARRIVED": return "已送达";
            case Constants.BATCH_SOLD: return "已售出";
            case Constants.ABNORMAL_HANDLING: return "处置中";
            case Constants.ABNORMAL_DONE: return "已处置";
            default: return code;
        }
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
