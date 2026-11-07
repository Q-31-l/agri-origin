package com.agri.trace.service;

import com.agri.trace.common.Constants;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.entity.ChainRecord;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.BaseVillageMapper;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizQualityReportMapper;
import com.agri.trace.mapper.ChainRecordMapper;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.agri.trace.vo.StatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

/**
 * 数据看板
 */
@Service
public class StatsService {

    @Resource
    private BizBatchMapper batchMapper;
    @Resource
    private BizQualityReportMapper qualityMapper;
    @Resource
    private ChainRecordMapper chainRecordMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private BaseVillageMapper villageMapper;
    @Resource
    private BatchService batchService;

    public StatsVO overview() {
        LoginUser user = UserContext.require();
        StatsVO vo = new StatsVO();

        // 数据范围三要素：农户→本人，村管→本村，生产主体→本人建档，监管/管理员→全量
        Long farmerId = null;
        Long villageId = null;
        Long createBy = null;
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            farmerId = user.getUserId();
        } else if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            villageId = user.getVillageId();
        } else if (Constants.ROLE_PRODUCER.equals(user.getRoleCode())) {
            createBy = user.getUserId();
        }

        vo.setTotalBatches(batchMapper.selectCount(scope(user)));
        vo.setPlantingBatches(count(user, Constants.BATCH_PLANTING));
        vo.setQualifiedBatches(count(user, Constants.BATCH_QUALIFIED));
        vo.setInTransitBatches(count(user, Constants.BATCH_IN_TRANSIT));
        vo.setOnSaleBatches(count(user, Constants.BATCH_ON_SALE));
        vo.setSoldBatches(count(user, Constants.BATCH_SOLD));
        vo.setAbnormalBatches(count(user, Constants.BATCH_ABNORMAL));
        vo.setTodayBatches(batchMapper.selectCount(scope(user)
                .ge(BizBatch::getCreateTime, LocalDate.now().atStartOfDay())));

        // 农户总数：村管口径限本村，其余角色为平台全量
        vo.setTotalFarmers(userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleCode, Constants.ROLE_FARMER)
                .eq(villageId != null, SysUser::getVillageId, villageId)));
        vo.setTotalVillages(villageId == null ? villageMapper.selectCount(Wrappers.emptyWrapper()) : 1L);

        // 质检报告与合格率必须与批次范围同口径，否则会出现"总量 2 条、合格率基于全站 7 条"的错位
        List<Long> visibleBatchIds = null;
        List<String> visibleBatchNos = null;
        if (farmerId != null || villageId != null || createBy != null) {
            visibleBatchIds = batchService.visibleBatchIds(user);
            if (visibleBatchIds == null) {
                visibleBatchIds = java.util.Collections.emptyList();
            }
            // 存证表只有 batch_no，看板上的存证条数同样要按范围收窄，
            // 否则农户看到的是全站存证总量（口径与列表不一致）
            visibleBatchNos = batchService.visibleBatchNos(user);
            if (visibleBatchNos == null) {
                visibleBatchNos = java.util.Collections.emptyList();
            }
        }
        LambdaQueryWrapper<BizQualityReport> reportScope = Wrappers.lambdaQuery();
        if (visibleBatchIds != null) {
            if (visibleBatchIds.isEmpty()) {
                vo.setTotalReports(0L);
                vo.setQualifiedRate(0);
                vo.setTotalChainRecords(0L);
                vo.setChainSuccess(0L);
                vo.setChainFailed(0L);
                vo.setChainSkipped(0L);
                vo.setChainSuccessRate(0);
                vo.setStatusDistribution(batchMapper.countByStatus(farmerId, villageId, createBy));
                vo.setVillageDistribution(batchMapper.countByVillage(farmerId, villageId, createBy));
                vo.setTrend(batchMapper.countByDay(farmerId, villageId, createBy));
                return vo;
            }
            reportScope.in(BizQualityReport::getBatchId, visibleBatchIds);
        }
        vo.setTotalReports(qualityMapper.selectCount(reportScope));
        vo.setTotalChainRecords(chainCount(visibleBatchNos, null));

        long success = chainCount(visibleBatchNos, Constants.CHAIN_SUCCESS);
        long failed = chainCount(visibleBatchNos, Constants.CHAIN_FAILED);
        long skipped = chainCount(visibleBatchNos, Constants.CHAIN_SKIPPED);
        vo.setChainSuccess(success);
        vo.setChainFailed(failed);
        vo.setChainSkipped(skipped);
        long totalChain = success + failed + skipped;
        vo.setChainSuccessRate(totalChain == 0 ? 0 : round(success * 100.0 / totalChain));

        LambdaQueryWrapper<BizQualityReport> passScope = Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getInspectResult, "PASS");
        if (visibleBatchIds != null) {
            passScope.in(BizQualityReport::getBatchId, visibleBatchIds);
        }
        Long passCount = qualityMapper.selectCount(passScope);
        long reports = vo.getTotalReports();
        vo.setQualifiedRate(reports == 0 ? 0 : round((passCount == null ? 0 : passCount) * 100.0 / reports));

        vo.setStatusDistribution(batchMapper.countByStatus(farmerId, villageId, createBy));
        vo.setVillageDistribution(batchMapper.countByVillage(farmerId, villageId, createBy));
        vo.setTrend(batchMapper.countByDay(farmerId, villageId, createBy));
        return vo;
    }

    /**
     * 官网首页用的公开概览：免登录可读。
     *
     * <p>只返回全平台聚合数字，不含任何批次明细、用户姓名、联系方式等敏感字段，
     * 因此可以安全地对匿名访问者开放。数据范围固定为"全量"，不随登录用户变化。</p>
     */
    public java.util.Map<String, Object> publicOverview() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("batchCount", batchMapper.selectCount(Wrappers.emptyWrapper()));
        map.put("abnormalCount", batchMapper.selectCount(Wrappers.<BizBatch>lambdaQuery()
                .eq(BizBatch::getStatus, Constants.BATCH_ABNORMAL)));
        map.put("villageCount", villageMapper.selectCount(Wrappers.emptyWrapper()));
        map.put("farmerCount", userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleCode, Constants.ROLE_FARMER)));

        long total = chainCount(null, null);
        long success = chainCount(null, Constants.CHAIN_SUCCESS);
        map.put("chainRecordCount", total);
        map.put("chainSuccess", success);
        map.put("chainSuccessRate", total == 0 ? 0 : round(success * 100.0 / total));

        // 生产端统计的"最近一次存证时间"，比浏览器当前时间更能说明环境仍在运行
        ChainRecord latest = chainRecordMapper.selectOne(Wrappers.<ChainRecord>lambdaQuery()
                .orderByDesc(ChainRecord::getId).last("limit 1"));
        map.put("lastChainTime", latest == null ? null : latest.getCreateTime());
        return map;
    }

    /**
     * 数据范围：农户看自己，村级管理员看本村，生产主体看本人建档，其余全量。
     * 必须与 BatchService.applyScope 保持完全一致，否则会出现"看板 15 条、列表 12 条"的错位。
     */
    private LambdaQueryWrapper<BizBatch> scope(LoginUser user) {
        LambdaQueryWrapper<BizBatch> wrapper = Wrappers.lambdaQuery();
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getFarmerId, user.getUserId());
        } else if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getVillageId, user.getVillageId());
        } else if (Constants.ROLE_PRODUCER.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getCreateBy, user.getUserId());
        }
        return wrapper;
    }

    private long count(LoginUser user, String status) {
        return batchMapper.selectCount(scope(user).eq(BizBatch::getStatus, status));
    }

    /**
     * 存证条数统计。
     *
     * @param batchNos 可见批次号；null 表示不受限（监管方、管理员）
     * @param status   存证状态；null 表示不限状态
     */
    private long chainCount(List<String> batchNos, String status) {
        if (batchNos != null && batchNos.isEmpty()) {
            return 0L;
        }
        LambdaQueryWrapper<ChainRecord> wrapper = Wrappers.<ChainRecord>lambdaQuery()
                .eq(status != null, ChainRecord::getStatus, status)
                .in(batchNos != null, ChainRecord::getBatchNo, batchNos);
        Long count = chainRecordMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
