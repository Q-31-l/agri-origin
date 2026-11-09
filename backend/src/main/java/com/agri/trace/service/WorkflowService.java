package com.agri.trace.service;

import com.agri.trace.common.Constants;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.BizAbnormalDisposalMapper;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizHarvestRecordMapper;
import com.agri.trace.mapper.BizLogisticsRecordMapper;
import com.agri.trace.mapper.BizPlantingRecordMapper;
import com.agri.trace.mapper.BizQualityReportMapper;
import com.agri.trace.mapper.BizSalesRecordMapper;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.PermissionCache;
import com.agri.trace.security.UserContext;
import com.agri.trace.vo.SubmitNoticeVO;
import com.agri.trace.vo.WorkflowStageVO;
import com.agri.trace.vo.WorkflowTodoVO;
import com.agri.trace.vo.WorkflowVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核流引擎：把"提交 → 等待谁审 → 谁在什么时候审过 → 下一环节"这条链算清楚。
 *
 * <p>设计要点：
 * <ol>
 *   <li>进度只认数据，不认前端写死的顺序：每个阶段的完成度由业务表实际记录推导，
 *       所以农户直接手写作物自动建档、跳过生产主体建档这类真实业务也能正确显示。</li>
 *   <li>待办与进度同源：{@link #todo()} 与 {@link #progress(Long)} 使用同一套阶段规则与
 *       数据范围，避免"进度说等待村管、村管待办里却没有"。</li>
 *   <li>只回答"该谁做"，不代替业务校验：真正的拦截仍在各 Service 的 create/audit 里。</li>
 * </ol>
 */
@Service
public class WorkflowService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String ST_DONE = "DONE";
    private static final String ST_PENDING = "PENDING";
    private static final String ST_REJECTED = "REJECTED";
    private static final String ST_TODO = "TODO";
    private static final String ST_ABNORMAL = "ABNORMAL";

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
    private SysUserMapper userMapper;
    @Resource
    private BatchService batchService;
    @Resource
    private PermissionCache permissionCache;

    // ------------------------------------------------------------------
    // 一、批次维度：全链路进度
    // ------------------------------------------------------------------

    public WorkflowVO progress(Long batchId) {
        BizBatch batch = batchService.getAuthorized(batchId);

        List<WorkflowStageVO> stages = new ArrayList<>();
        stages.add(batchStage(batch));
        stages.add(batchAuditStage(batch));
        stages.add(plantingStage(batch));
        stages.add(harvestStage(batch));
        stages.add(qualityStage(batch));
        stages.add(logisticsStage(batch));
        stages.add(salesStage(batch));

        int done = 0;
        int advanced = 0;
        for (WorkflowStageVO s : stages) {
            if (ST_DONE.equals(s.getStatus())) {
                done++;
                advanced++;
            } else if (ST_PENDING.equals(s.getStatus()) || ST_ABNORMAL.equals(s.getStatus())) {
                // 已提交、正在等审核，流程上已经推进到这一环
                advanced++;
            }
        }

        WorkflowVO vo = new WorkflowVO();
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setProductName(batch.getProductName());
        vo.setVillageName(batch.getVillageName());
        vo.setBatchStatus(batch.getStatus());
        vo.setAuditStatus(batch.getAuditStatus());
        vo.setStages(stages);
        vo.setDoneCount(done);
        vo.setAdvancedCount(advanced);
        vo.setTotalCount(stages.size());
        vo.setPercent(stages.isEmpty() ? 0 : Math.round(advanced * 100f / stages.size()));

        WorkflowStageVO current = null;
        for (WorkflowStageVO s : stages) {
            if (!ST_DONE.equals(s.getStatus())) {
                current = s;
                break;
            }
        }
        if (current == null) {
            vo.setCurrentStage("全流程完成");
            vo.setCurrentStageCode("DONE");
            vo.setTip("全流程已完成并存证，可对外销售与扫码溯源");
        } else {
            vo.setCurrentStage(current.getTitle());
            vo.setCurrentStageCode(current.getCode());
            vo.setWaitingRole(ST_TODO.equals(current.getStatus()) ? current.getSubmitRole() : current.getAuditRole());
            vo.setWaitingNames(current.getWaitingNames());
            vo.setTip(current.getTip());
        }
        if (Integer.valueOf(1).equals(batch.getAbnormalFlag())
                || Constants.BATCH_ABNORMAL.equals(batch.getStatus())) {
            String prefix = "批次已被标记异常，等待监管人员处置。";
            vo.setTip(prefix + (vo.getTip() == null ? "" : vo.getTip()));
        }
        return vo;
    }

    private WorkflowStageVO baseStage(String code, String title, String submitRole, String auditRole) {
        WorkflowStageVO s = new WorkflowStageVO();
        s.setCode(code);
        s.setTitle(title);
        s.setSubmitRole(submitRole);
        s.setAuditRole(auditRole);
        s.setCount(0);
        return s;
    }

    /** ① 批次建档 */
    private WorkflowStageVO batchStage(BizBatch batch) {
        WorkflowStageVO s = baseStage("BATCH", "批次建档", "生产主体 / 农户", "村级管理员");
        s.setStatus(ST_DONE);
        s.setSubmitBy(nameOr(batch.getCreateBy(), batch.getFarmerName()));
        s.setSubmitTime(fmt(batch.getCreateTime()));
        s.setCount(1);
        s.setTip("批次 " + batch.getBatchNo() + " 已建档：" + batch.getProductName());
        return s;
    }

    /** ② 村级核验 */
    private WorkflowStageVO batchAuditStage(BizBatch batch) {
        WorkflowStageVO s = baseStage("BATCH_AUDIT", "村级核验", "生产主体 / 农户", "村级管理员");
        s.setSubmitBy(nameOr(batch.getCreateBy(), batch.getFarmerName()));
        s.setSubmitTime(fmt(batch.getCreateTime()));
        s.setCount(1);
        if (Constants.AUDIT_APPROVED.equals(batch.getAuditStatus())) {
            s.setStatus(ST_DONE);
            s.setAuditBy(batch.getAuditBy());
            s.setAuditTime(fmt(batch.getAuditTime()));
            s.setAuditRemark(batch.getAuditRemark());
            s.setTip("已通过村级核验");
        } else if (Constants.AUDIT_REJECTED.equals(batch.getAuditStatus())) {
            s.setStatus(ST_REJECTED);
            s.setAuditBy(batch.getAuditBy());
            s.setAuditTime(fmt(batch.getAuditTime()));
            s.setAuditRemark(batch.getAuditRemark());
            s.setTip("村级核验未通过" + (isEmpty(batch.getAuditRemark()) ? "，请修改后重新提交" : "：" + batch.getAuditRemark()));
        } else {
            s.setStatus(ST_PENDING);
            List<String> names = roleUserNames(Constants.ROLE_VILLAGE, batch.getVillageId());
            s.setWaitingNames(join(names));
            s.setTip(pendingTip(batch.getVillageName(), names, "村级管理员"));
        }
        return s;
    }

    /** ③ 农事记录 */
    private WorkflowStageVO plantingStage(BizBatch batch) {
        WorkflowStageVO s = baseStage("PLANTING", "农事记录", "农户", "村级管理员");
        List<BizPlantingRecord> list = plantingMapper.selectList(Wrappers.<BizPlantingRecord>lambdaQuery()
                .eq(BizPlantingRecord::getBatchId, batch.getId())
                .orderByAsc(BizPlantingRecord::getId));
        s.setCount(list.size());
        if (list.isEmpty()) {
            s.setStatus(ST_TODO);
            s.setTip("等待农户录入农事记录");
            return s;
        }
        Set<String> submitters = new LinkedHashSet<>();
        LocalDateTime lastSubmit = null;
        int pending = 0;
        int rejected = 0;
        String lastAuditBy = null;
        LocalDateTime lastAuditTime = null;
        for (BizPlantingRecord r : list) {
            // 提交人与提交时间要按"全部记录"统计，不能只在 PENDING 时收集：
            // 否则农事全部审核通过后，进度里这一环只剩审核人，看不到是谁提交的。
            if (!isEmpty(r.getOperatorName())) {
                submitters.add(r.getOperatorName());
            }
            if (r.getCreateTime() != null && (lastSubmit == null || r.getCreateTime().isAfter(lastSubmit))) {
                lastSubmit = r.getCreateTime();
            }
            if (Constants.AUDIT_PENDING.equals(r.getAuditStatus())) {
                pending++;
            } else if (Constants.AUDIT_REJECTED.equals(r.getAuditStatus())) {
                rejected++;
            } else {
                lastAuditBy = r.getAuditBy();
                lastAuditTime = r.getAuditTime();
            }
        }
        s.setSubmitBy(join(new ArrayList<>(submitters)));
        s.setSubmitTime(fmt(lastSubmit));
        if (pending > 0) {
            s.setStatus(ST_PENDING);
            List<String> names = roleUserNames(Constants.ROLE_VILLAGE, batch.getVillageId());
            s.setWaitingNames(join(names));
            s.setTip("已提交 " + pending + " 条农事记录，" + waitingText(batch.getVillageName(), names, "村级管理员"));
        } else if (rejected > 0) {
            s.setStatus(ST_REJECTED);
            s.setTip(rejected + " 条农事记录被驳回，请修正后重新提交");
        } else {
            s.setStatus(ST_DONE);
            s.setAuditBy(lastAuditBy);
            s.setAuditTime(fmt(lastAuditTime));
            s.setTip("共 " + list.size() + " 条农事记录，已全部通过村管审核");
        }
        return s;
    }

    /** ④ 采收登记 */
    private WorkflowStageVO harvestStage(BizBatch batch) {
        WorkflowStageVO s = baseStage("HARVEST", "采收登记", "农户", "村级管理员");
        List<BizHarvestRecord> list = harvestMapper.selectList(Wrappers.<BizHarvestRecord>lambdaQuery()
                .eq(BizHarvestRecord::getBatchId, batch.getId())
                .orderByDesc(BizHarvestRecord::getId));
        s.setCount(list.size());
        if (list.isEmpty()) {
            s.setStatus(ST_TODO);
            s.setTip("等待农户提交采收登记");
            return s;
        }
        // 以最新一条为准：被驳回后重新提交时，旧的驳回记录不应再影响状态
        BizHarvestRecord r = list.get(0);
        s.setSubmitBy(r.getOperatorName());
        s.setSubmitTime(fmt(r.getCreateTime() == null ? null : r.getCreateTime()));
        s.setAuditBy(r.getAuditBy());
        s.setAuditTime(fmt(r.getAuditTime()));
        s.setAuditRemark(r.getAuditRemark());
        if (Constants.AUDIT_PENDING.equals(r.getAuditStatus())) {
            s.setStatus(ST_PENDING);
            List<String> names = roleUserNames(Constants.ROLE_VILLAGE, batch.getVillageId());
            s.setWaitingNames(join(names));
            s.setTip("已提交采收 " + qty(r.getQuantity()) + r.getUnit() + "，" + waitingText(batch.getVillageName(), names, "村级管理员"));
        } else if (Constants.AUDIT_REJECTED.equals(r.getAuditStatus())) {
            s.setStatus(ST_REJECTED);
            s.setTip("采收登记被驳回" + (isEmpty(r.getAuditRemark()) ? "，请修正后重新提交" : "：" + r.getAuditRemark()));
        } else {
            s.setStatus(ST_DONE);
            s.setTip("采收 " + qty(r.getQuantity()) + r.getUnit() + " 已通过村管审核");
        }
        return s;
    }

    /** ⑤ 质量检测 */
    private WorkflowStageVO qualityStage(BizBatch batch) {
        WorkflowStageVO s = baseStage("QUALITY", "质量检测", "检测机构", "监管人员");
        List<BizQualityReport> list = qualityMapper.selectList(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getBatchId, batch.getId())
                .orderByDesc(BizQualityReport::getId));
        s.setCount(list.size());
        if (list.isEmpty()) {
            s.setStatus(ST_TODO);
            s.setTip("等待检测机构上传质检报告");
            return s;
        }
        BizQualityReport r = list.get(0);
        s.setSubmitBy(r.getInspectorName());
        s.setSubmitTime(fmt(r.getCreateTime()));
        s.setAuditBy(r.getAuditBy());
        s.setAuditTime(fmt(r.getAuditTime()));
        s.setAuditRemark(r.getAuditRemark());
        boolean fail = "FAIL".equals(r.getInspectResult());
        if (fail) {
            s.setStatus(ST_ABNORMAL);
            s.setTip("质检不合格（报告 " + r.getReportNo() + "），批次已熔断，等待监管人员处置");
        } else if (Constants.AUDIT_PENDING.equals(r.getAuditStatus())) {
            s.setStatus(ST_PENDING);
            List<String> names = roleUserNames(Constants.ROLE_REGULATOR, null);
            s.setWaitingNames(join(names));
            s.setTip("质检报告 " + r.getReportNo() + " 已上传（合格），" + waitingText(null, names, "监管人员", "复核"));
        } else if (Constants.AUDIT_REJECTED.equals(r.getAuditStatus())) {
            s.setStatus(ST_REJECTED);
            s.setTip("质检报告被退回" + (isEmpty(r.getAuditRemark()) ? "" : "：" + r.getAuditRemark()));
        } else {
            s.setStatus(ST_DONE);
            s.setTip("质检合格（报告 " + r.getReportNo() + "）");
        }
        return s;
    }

    /** ⑥ 物流运输 */
    private WorkflowStageVO logisticsStage(BizBatch batch) {
        WorkflowStageVO s = baseStage("LOGISTICS", "物流运输", "物流企业", "监管人员");
        List<BizLogisticsRecord> list = logisticsMapper.selectList(Wrappers.<BizLogisticsRecord>lambdaQuery()
                .eq(BizLogisticsRecord::getBatchId, batch.getId())
                .orderByDesc(BizLogisticsRecord::getId));
        s.setCount(list.size());
        if (list.isEmpty()) {
            s.setStatus(ST_TODO);
            s.setTip("等待物流企业登记运输交接");
            return s;
        }
        BizLogisticsRecord r = list.get(0);
        s.setSubmitBy(r.getCarrierName());
        s.setSubmitTime(fmt(r.getCreateTime()));
        s.setAuditBy(r.getAuditBy());
        s.setAuditTime(fmt(r.getAuditTime()));
        s.setAuditRemark(r.getAuditRemark());
        if (Constants.AUDIT_PENDING.equals(r.getAuditStatus())) {
            s.setStatus(ST_PENDING);
            List<String> names = roleUserNames(Constants.ROLE_REGULATOR, null);
            s.setWaitingNames(join(names));
            s.setTip("运单 " + r.getLogisticsNo() + " 已登记，" + waitingText(null, names, "监管人员", "复核"));
        } else if (Constants.AUDIT_REJECTED.equals(r.getAuditStatus())) {
            s.setStatus(ST_REJECTED);
            s.setTip("运单被退回" + (isEmpty(r.getAuditRemark()) ? "" : "：" + r.getAuditRemark()));
        } else {
            s.setStatus(ST_DONE);
            s.setTip("运单 " + r.getLogisticsNo() + " 已通过复核");
        }
        return s;
    }

    /** ⑦ 终端销售 */
    private WorkflowStageVO salesStage(BizBatch batch) {
        WorkflowStageVO s = baseStage("SALES", "终端销售", "销售商", "监管人员");
        List<BizSalesRecord> list = salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(BizSalesRecord::getBatchId, batch.getId())
                .orderByDesc(BizSalesRecord::getId));
        s.setCount(list.size());
        if (list.isEmpty()) {
            s.setStatus(ST_TODO);
            s.setTip("等待销售商登记销售");
            return s;
        }
        BizSalesRecord r = list.get(0);
        s.setSubmitBy(r.getSellerName());
        s.setSubmitTime(fmt(r.getCreateTime()));
        s.setAuditBy(r.getAuditBy());
        s.setAuditTime(fmt(r.getAuditTime()));
        s.setAuditRemark(r.getAuditRemark());
        if (Constants.AUDIT_PENDING.equals(r.getAuditStatus())) {
            s.setStatus(ST_PENDING);
            List<String> names = roleUserNames(Constants.ROLE_REGULATOR, null);
            s.setWaitingNames(join(names));
            s.setTip("销售记录（" + r.getMarketName() + "）已登记，" + waitingText(null, names, "监管人员", "复核"));
        } else if (Constants.AUDIT_REJECTED.equals(r.getAuditStatus())) {
            s.setStatus(ST_REJECTED);
            s.setTip("销售记录被退回" + (isEmpty(r.getAuditRemark()) ? "" : "：" + r.getAuditRemark()));
        } else {
            s.setStatus(ST_DONE);
            s.setTip("销售记录已通过复核");
        }
        return s;
    }

    // ------------------------------------------------------------------
    // 二、待办：只看"确实轮到我审"的数据
    // ------------------------------------------------------------------

    public WorkflowTodoVO todo() {
        LoginUser user = UserContext.require();
        WorkflowTodoVO vo = new WorkflowTodoVO();
        vo.setRoleCode(user.getRoleCode());
        List<Long> visible = batchService.visibleBatchIds(user);
        boolean restricted = visible != null;
        boolean emptyScope = restricted && visible.isEmpty();

        List<WorkflowTodoVO.Group> groups = new ArrayList<>();

        // ① 批次村级核验：只推给村管与管理员。
        // 监管虽然也持有 batch:audit（可介入越级核验），但"村级核验"不是他的日常职责，
        // 放进待办只会把 45 条里的 9 条无关批次顶到最上面，掩盖真正等他复核的质检/物流/销售。
        boolean villageDuty = Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                || Constants.ROLE_ADMIN.equals(user.getRoleCode());
        if (villageDuty && hasPerm(user, "batch:audit") && !emptyScope) {
            LambdaQueryWrapper<BizBatch> w = Wrappers.<BizBatch>lambdaQuery()
                    .eq(BizBatch::getAuditStatus, Constants.AUDIT_PENDING)
                    .orderByDesc(BizBatch::getId);
            if (restricted) {
                w.in(BizBatch::getId, visible);
            }
            long total = batchMapper.selectCount(w);
            WorkflowTodoVO.Group g = newGroup("BATCH", "批次核验", "PC_BATCH_LIST", total);
            for (BizBatch b : batchMapper.selectList(w.last("limit 20"))) {
                WorkflowTodoVO.Item it = newItem(b.getId(), b.getId(), b.getBatchNo(), b.getProductName(),
                        b.getFarmerName(), b.getCreateTime());
                it.setSummary("新建批次 " + b.getProductName() + "，预计 " + qty(b.getQuantity()) + b.getUnit());
                it.setFrom(b.getVillageName());
                g.getItems().add(it);
            }
            addIfAny(groups, g);
        }

        // ② 农事审核（含尚未建档批次的"手写作物"记录）
        if (hasPerm(user, "planting:audit") && !emptyScope) {
            LambdaQueryWrapper<BizPlantingRecord> w = Wrappers.<BizPlantingRecord>lambdaQuery()
                    .eq(BizPlantingRecord::getAuditStatus, Constants.AUDIT_PENDING)
                    .orderByDesc(BizPlantingRecord::getId);
            if (restricted) {
                List<Long> farmerIds = Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                        ? farmerIdsOfVillage(user.getVillageId()) : Collections.emptyList();
                if (farmerIds.isEmpty()) {
                    w.in(BizPlantingRecord::getBatchId, visible);
                } else {
                    w.and(x -> x.in(BizPlantingRecord::getBatchId, visible)
                            .or(y -> y.isNull(BizPlantingRecord::getBatchId)
                                    .in(BizPlantingRecord::getOperatorId, farmerIds)));
                }
            }
            long total = plantingMapper.selectCount(w);
            WorkflowTodoVO.Group g = newGroup("PLANTING", "农事记录审核", "PLANTING_LIST", total);
            Map<Long, BizBatch> cache = new HashMap<>();
            for (BizPlantingRecord r : plantingMapper.selectList(w.last("limit 20"))) {
                BizBatch b = r.getBatchId() == null ? null : batchOf(cache, r.getBatchId());
                WorkflowTodoVO.Item it = newItem(r.getId(), r.getBatchId(),
                        b == null ? "待建档" : b.getBatchNo(),
                        b == null ? r.getProductName() : b.getProductName(),
                        r.getOperatorName(), r.getCreateTime());
                it.setSummary(recordTypeLabel(r.getRecordType()) + "：" + nullSafe(r.getContent()));
                it.setFrom(b == null ? "手写作物·审核通过后自动建档" : b.getVillageName());
                g.getItems().add(it);
            }
            addIfAny(groups, g);
        }

        // ③ 采收审核
        if (hasPerm(user, "harvest:audit") && !emptyScope) {
            LambdaQueryWrapper<BizHarvestRecord> w = Wrappers.<BizHarvestRecord>lambdaQuery()
                    .eq(BizHarvestRecord::getAuditStatus, Constants.AUDIT_PENDING)
                    .orderByDesc(BizHarvestRecord::getId);
            if (restricted) {
                w.in(BizHarvestRecord::getBatchId, visible);
            }
            long total = harvestMapper.selectCount(w);
            WorkflowTodoVO.Group g = newGroup("HARVEST", "采收登记审核", "HARVEST_LIST", total);
            Map<Long, BizBatch> cache = new HashMap<>();
            for (BizHarvestRecord r : harvestMapper.selectList(w.last("limit 20"))) {
                BizBatch b = batchOf(cache, r.getBatchId());
                WorkflowTodoVO.Item it = newItem(r.getId(), r.getBatchId(), r.getBatchNo(),
                        b == null ? null : b.getProductName(), r.getOperatorName(),
                        r.getCreateTime() == null ? null : r.getCreateTime());
                it.setSummary("采收 " + qty(r.getQuantity()) + r.getUnit() + "，等级 " + nullSafe(r.getQualityLevel()));
                it.setFrom(b == null ? null : b.getVillageName());
                g.getItems().add(it);
            }
            addIfAny(groups, g);
        }

        // ④⑤⑥ 质检 / 物流 / 销售复核
        groups.addAll(reviewGroups(user, visible, restricted, emptyScope));

        // ⑦ 异常处置
        if (hasPerm(user, "abnormal:dispose") && !emptyScope) {
            LambdaQueryWrapper<BizAbnormalDisposal> w = Wrappers.<BizAbnormalDisposal>lambdaQuery()
                    .eq(BizAbnormalDisposal::getStatus, Constants.ABNORMAL_HANDLING)
                    .orderByDesc(BizAbnormalDisposal::getId);
            if (restricted) {
                w.in(BizAbnormalDisposal::getBatchId, visible);
            }
            long total = abnormalMapper.selectCount(w);
            WorkflowTodoVO.Group g = newGroup("ABNORMAL", "异常处置", "ABNORMAL", total);
            Map<Long, BizBatch> cache = new HashMap<>();
            for (BizAbnormalDisposal r : abnormalMapper.selectList(w.last("limit 20"))) {
                BizBatch b = batchOf(cache, r.getBatchId());
                WorkflowTodoVO.Item it = newItem(r.getId(), r.getBatchId(), r.getBatchNo(),
                        b == null ? null : b.getProductName(), r.getHandlerName(), r.getHandleTime());
                it.setSummary(nullSafe(r.getDescription()));
                it.setFrom(b == null ? null : b.getVillageName());
                g.getItems().add(it);
            }
            addIfAny(groups, g);
        }

        int total = 0;
        for (WorkflowTodoVO.Group g : groups) {
            total += g.getCount();
        }
        vo.setTotal(total);
        vo.setGroups(groups);
        return vo;
    }

    private List<WorkflowTodoVO.Group> reviewGroups(LoginUser user, List<Long> visible,
                                                    boolean restricted, boolean emptyScope) {
        List<WorkflowTodoVO.Group> groups = new ArrayList<>();
        if (emptyScope) {
            return groups;
        }
        if (hasPerm(user, "quality:audit")) {
            LambdaQueryWrapper<BizQualityReport> w = Wrappers.<BizQualityReport>lambdaQuery()
                    .eq(BizQualityReport::getAuditStatus, Constants.AUDIT_PENDING)
                    .orderByDesc(BizQualityReport::getId);
            if (restricted) {
                w.in(BizQualityReport::getBatchId, visible);
            }
            long total = qualityMapper.selectCount(w);
            WorkflowTodoVO.Group g = newGroup("QUALITY", "质检报告复核", "QUALITY_LIST", total);
            Map<Long, BizBatch> cache = new HashMap<>();
            for (BizQualityReport r : qualityMapper.selectList(w.last("limit 20"))) {
                BizBatch b = batchOf(cache, r.getBatchId());
                WorkflowTodoVO.Item it = newItem(r.getId(), r.getBatchId(), r.getBatchNo(),
                        b == null ? null : b.getProductName(), r.getInspectorName(), r.getCreateTime());
                it.setSummary("报告 " + r.getReportNo() + "，结论 " + ("PASS".equals(r.getInspectResult()) ? "合格" : "不合格"));
                it.setFrom(r.getInspectOrg());
                g.getItems().add(it);
            }
            addIfAny(groups, g);
        }
        if (hasPerm(user, "logistics:audit")) {
            LambdaQueryWrapper<BizLogisticsRecord> w = Wrappers.<BizLogisticsRecord>lambdaQuery()
                    .eq(BizLogisticsRecord::getAuditStatus, Constants.AUDIT_PENDING)
                    .orderByDesc(BizLogisticsRecord::getId);
            if (restricted) {
                w.in(BizLogisticsRecord::getBatchId, visible);
            }
            long total = logisticsMapper.selectCount(w);
            WorkflowTodoVO.Group g = newGroup("LOGISTICS", "物流记录复核", "LOGISTICS_LIST", total);
            Map<Long, BizBatch> cache = new HashMap<>();
            for (BizLogisticsRecord r : logisticsMapper.selectList(w.last("limit 20"))) {
                BizBatch b = batchOf(cache, r.getBatchId());
                WorkflowTodoVO.Item it = newItem(r.getId(), r.getBatchId(), r.getBatchNo(),
                        b == null ? null : b.getProductName(), r.getCarrierName(), r.getCreateTime());
                it.setSummary("运单 " + r.getLogisticsNo() + "：" + nullSafe(r.getFromOrg()) + " → " + nullSafe(r.getToOrg()));
                it.setFrom(r.getCarrierName());
                g.getItems().add(it);
            }
            addIfAny(groups, g);
        }
        if (hasPerm(user, "sales:audit")) {
            LambdaQueryWrapper<BizSalesRecord> w = Wrappers.<BizSalesRecord>lambdaQuery()
                    .eq(BizSalesRecord::getAuditStatus, Constants.AUDIT_PENDING)
                    .orderByDesc(BizSalesRecord::getId);
            if (restricted) {
                w.in(BizSalesRecord::getBatchId, visible);
            }
            long total = salesMapper.selectCount(w);
            WorkflowTodoVO.Group g = newGroup("SALES", "销售记录复核", "SALES_LIST", total);
            Map<Long, BizBatch> cache = new HashMap<>();
            for (BizSalesRecord r : salesMapper.selectList(w.last("limit 20"))) {
                BizBatch b = batchOf(cache, r.getBatchId());
                WorkflowTodoVO.Item it = newItem(r.getId(), r.getBatchId(), r.getBatchNo(),
                        b == null ? null : b.getProductName(), r.getSellerName(), r.getCreateTime());
                it.setSummary(r.getMarketName() + "：" + qty(r.getQuantity()) + r.getUnit() + " × ¥" + r.getPrice());
                it.setFrom(r.getSellerName());
                g.getItems().add(it);
            }
            addIfAny(groups, g);
        }
        return groups;
    }

    // ------------------------------------------------------------------
    // 三、提交后的"等待谁审核"提示
    // ------------------------------------------------------------------

    public SubmitNoticeVO notice(String bizType, Long bizId) {
        String type = bizType == null ? "" : bizType.toUpperCase();
        SubmitNoticeVO vo = new SubmitNoticeVO();
        vo.setBizType(type);
        vo.setBizId(bizId);

        BizBatch batch = null;
        String title = "提交成功";
        String nextStep = null;
        Long villageId = null;

        switch (type) {
            case "BATCH": {
                batch = batchMapper.selectById(bizId);
                title = "批次已提交，等待村级核验";
                nextStep = "村管核验通过后即可录入农事记录";
                break;
            }
            case "PLANTING": {
                BizPlantingRecord r = plantingMapper.selectById(bizId);
                if (r != null) {
                    title = "农事记录已提交";
                    if (r.getBatchId() != null) {
                        batch = batchMapper.selectById(r.getBatchId());
                    } else {
                        SysUser farmer = userMapper.selectById(r.getOperatorId());
                        villageId = farmer == null ? null : farmer.getVillageId();
                        nextStep = "村管审核通过后自动建立批次档案并上链";
                    }
                }
                break;
            }
            case "HARVEST": {
                BizHarvestRecord r = harvestMapper.selectById(bizId);
                if (r != null) {
                    batch = batchMapper.selectById(r.getBatchId());
                    title = "采收登记已提交";
                    nextStep = "村管审核通过后可提交质量检测";
                }
                break;
            }
            case "QUALITY": {
                BizQualityReport r = qualityMapper.selectById(bizId);
                if (r != null) {
                    batch = batchMapper.selectById(r.getBatchId());
                    title = "质检报告已提交";
                    nextStep = "监管复核通过后可登记物流运输";
                }
                break;
            }
            case "LOGISTICS": {
                BizLogisticsRecord r = logisticsMapper.selectById(bizId);
                if (r != null) {
                    batch = batchMapper.selectById(r.getBatchId());
                    title = "物流记录已提交";
                    nextStep = "监管复核通过后可登记销售";
                }
                break;
            }
            case "SALES": {
                BizSalesRecord r = salesMapper.selectById(bizId);
                if (r != null) {
                    batch = batchMapper.selectById(r.getBatchId());
                    title = "销售记录已提交";
                    nextStep = "监管复核通过后本批次全流程闭环";
                }
                break;
            }
            default:
                break;
        }

        if (batch != null) {
            vo.setBatchId(batch.getId());
            vo.setBatchNo(batch.getBatchNo());
            vo.setProductName(batch.getProductName());
            villageId = batch.getVillageId();
        }

        boolean toVillage = "BATCH".equals(type) || "PLANTING".equals(type) || "HARVEST".equals(type);
        String role = toVillage ? Constants.ROLE_VILLAGE : Constants.ROLE_REGULATOR;
        List<String> names = roleUserNames(role, toVillage ? villageId : null);
        String villageName = batch == null ? null : batch.getVillageName();

        vo.setTitle(title);
        vo.setWaitingRole(roleLabel(role));
        vo.setWaitingNames(join(names));
        vo.setNextStep(nextStep);
        vo.setMessage(pendingTip(toVillage ? villageName : null, names, roleLabel(role)));
        return vo;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private void addIfAny(List<WorkflowTodoVO.Group> groups, WorkflowTodoVO.Group g) {
        if (g.getCount() > 0 || !g.getItems().isEmpty()) {
            groups.add(g);
        }
    }

    private WorkflowTodoVO.Group newGroup(String type, String title, String route, long count) {
        WorkflowTodoVO.Group g = new WorkflowTodoVO.Group();
        g.setType(type);
        g.setTitle(title);
        g.setRoute(route);
        g.setCount((int) count);
        return g;
    }

    private WorkflowTodoVO.Item newItem(Long id, Long batchId, String batchNo, String productName,
                                        String submitBy, LocalDateTime submitTime) {
        WorkflowTodoVO.Item it = new WorkflowTodoVO.Item();
        it.setId(id);
        it.setBatchId(batchId);
        it.setBatchNo(batchNo);
        it.setProductName(productName);
        it.setSubmitBy(submitBy);
        it.setSubmitTime(submitTime);
        it.setStatus(Constants.AUDIT_PENDING);
        return it;
    }

    private BizBatch batchOf(Map<Long, BizBatch> cache, Long id) {
        if (id == null) {
            return null;
        }
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        BizBatch b = batchMapper.selectById(id);
        cache.put(id, b);
        return b;
    }

    private List<Long> farmerIdsOfVillage(Long villageId) {
        if (villageId == null) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (SysUser u : userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId)
                .eq(SysUser::getRoleCode, Constants.ROLE_FARMER)
                .eq(SysUser::getVillageId, villageId))) {
            ids.add(u.getId());
        }
        return ids;
    }

    /** 某角色（可按村过滤）的启用账号真实姓名，用于"等待 王秀兰 审核" */
    private List<String> roleUserNames(String roleCode, Long villageId) {
        LambdaQueryWrapper<SysUser> w = Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getRealName)
                .eq(SysUser::getRoleCode, roleCode)
                .eq(SysUser::getStatus, 1)
                .eq(villageId != null, SysUser::getVillageId, villageId)
                .orderByAsc(SysUser::getId);
        List<String> names = new ArrayList<>();
        for (SysUser u : userMapper.selectList(w)) {
            if (!isEmpty(u.getRealName()) && !names.contains(u.getRealName())) {
                names.add(u.getRealName());
            }
        }
        return names;
    }

    private String pendingTip(String villageName, List<String> names, String roleLabel) {
        return "已提交，" + waitingText(villageName, names, roleLabel, "审核");
    }

    private String pendingTip(String villageName, List<String> names, String roleLabel, String verb) {
        return "已提交，" + waitingText(villageName, names, roleLabel, verb);
    }

    /** "等待溪头村 王秀兰（村级管理员）审核" —— 不带"已提交"，供拼接在前置描述之后 */
    private String waitingText(String villageName, List<String> names, String roleLabel) {
        return waitingText(villageName, names, roleLabel, "审核");
    }

    private String waitingText(String villageName, List<String> names, String roleLabel, String verb) {
        StringBuilder sb = new StringBuilder("等待");
        if (!isEmpty(villageName)) {
            sb.append(villageName).append(" ");
        }
        if (names.isEmpty()) {
            sb.append(roleLabel).append(verb).append("（该角色暂未配置账号，请联系管理员开通）");
        } else {
            sb.append(join(names)).append("（").append(roleLabel).append("）").append(verb);
        }
        return sb.toString();
    }


    private String nameOr(Long userId, String fallback) {
        if (userId != null) {
            SysUser u = userMapper.selectById(userId);
            if (u != null && !isEmpty(u.getRealName())) {
                return u.getRealName();
            }
        }
        return fallback;
    }

    private String roleLabel(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case Constants.ROLE_VILLAGE: return "村级管理员";
            case Constants.ROLE_REGULATOR: return "监管人员";
            case Constants.ROLE_INSPECTOR: return "检测机构";
            case Constants.ROLE_LOGISTICS: return "物流企业";
            case Constants.ROLE_SELLER: return "销售商";
            case Constants.ROLE_FARMER: return "农户";
            case Constants.ROLE_PRODUCER: return "生产主体";
            case Constants.ROLE_ADMIN: return "系统管理员";
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

    /**
     * 权限判断必须走 PermissionCache：JWT 里的 LoginUser 只带身份，不带权限点，
     * 拦截器校验 @RequirePerm 用的也是这份缓存。若在这里读 user.getPermissions()，
     * 权限集合恒为空，待办会永远返回 0 条——"接口通了但什么也不显示"。
     */
    private boolean hasPerm(LoginUser user, String perm) {
        if (user == null) {
            return false;
        }
        if (Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            return true;
        }
        return permissionCache.has(user.getRoleCode(), perm);
    }

    private String qty(java.math.BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    private String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DT_FMT);
    }

    /**
     * 姓名拼接：超过 3 人只列前 3 个，其余折成"等 N 人"。
     * 一个村配了七八个村管时，把全名都塞进"等待 XX 审核"会撑破提示条，
     * 提交方真正需要知道的是"在等哪一级、大概几个人"。
     */
    private String join(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        if (names.size() <= 3) {
            return String.join("、", names);
        }
        return String.join("、", names.subList(0, 3)) + " 等 " + names.size() + " 人";
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
