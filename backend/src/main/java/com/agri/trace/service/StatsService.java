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

    public StatsVO overview() {
        LoginUser user = UserContext.require();
        StatsVO vo = new StatsVO();

        vo.setTotalBatches(batchMapper.selectCount(scope(user)));
        vo.setPlantingBatches(count(user, Constants.BATCH_PLANTING));
        vo.setQualifiedBatches(count(user, Constants.BATCH_QUALIFIED));
        vo.setInTransitBatches(count(user, Constants.BATCH_IN_TRANSIT));
        vo.setOnSaleBatches(count(user, Constants.BATCH_ON_SALE));
        vo.setSoldBatches(count(user, Constants.BATCH_SOLD));
        vo.setAbnormalBatches(count(user, Constants.BATCH_ABNORMAL));
        vo.setTodayBatches(batchMapper.selectCount(scope(user)
                .ge(BizBatch::getCreateTime, LocalDate.now().atStartOfDay())));

        vo.setTotalFarmers(userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleCode, Constants.ROLE_FARMER)));
        vo.setTotalVillages(villageMapper.selectCount(Wrappers.emptyWrapper()));
        vo.setTotalReports(qualityMapper.selectCount(Wrappers.emptyWrapper()));
        vo.setTotalChainRecords(chainRecordMapper.selectCount(Wrappers.emptyWrapper()));

        long success = chainRecordMapper.selectCount(Wrappers.<ChainRecord>lambdaQuery()
                .eq(ChainRecord::getStatus, Constants.CHAIN_SUCCESS));
        long failed = chainRecordMapper.selectCount(Wrappers.<ChainRecord>lambdaQuery()
                .eq(ChainRecord::getStatus, Constants.CHAIN_FAILED));
        long skipped = chainRecordMapper.selectCount(Wrappers.<ChainRecord>lambdaQuery()
                .eq(ChainRecord::getStatus, Constants.CHAIN_SKIPPED));
        vo.setChainSuccess(success);
        vo.setChainFailed(failed);
        vo.setChainSkipped(skipped);
        long totalChain = success + failed + skipped;
        vo.setChainSuccessRate(totalChain == 0 ? 0 : round(success * 100.0 / totalChain));

        Long passCount = qualityMapper.selectCount(Wrappers.<BizQualityReport>lambdaQuery()
                .eq(BizQualityReport::getInspectResult, "PASS"));
        long reports = vo.getTotalReports();
        vo.setQualifiedRate(reports == 0 ? 0 : round((passCount == null ? 0 : passCount) * 100.0 / reports));

        vo.setStatusDistribution(batchMapper.countByStatus());
        vo.setVillageDistribution(batchMapper.countByVillage());
        vo.setTrend(batchMapper.countByDay());
        return vo;
    }

    /**
     * 数据范围：农户看自己，村级管理员看本村，其余全量
     */
    private LambdaQueryWrapper<BizBatch> scope(LoginUser user) {
        LambdaQueryWrapper<BizBatch> wrapper = Wrappers.lambdaQuery();
        if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getFarmerId, user.getUserId());
        } else if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            wrapper.eq(BizBatch::getVillageId, user.getVillageId());
        }
        return wrapper;
    }

    private long count(LoginUser user, String status) {
        return batchMapper.selectCount(scope(user).eq(BizBatch::getStatus, status));
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
