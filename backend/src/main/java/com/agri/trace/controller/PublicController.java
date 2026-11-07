package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.sdk.TraceContractService;
import com.agri.trace.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 对外公开接口（官网首页使用，免登录）。
 *
 * <p>只暴露平台级聚合数字，用于首页"运行快照"展示真实数据；
 * 不返回批次明细、用户信息、联系方式等任何业务数据。</p>
 */
@Tag(name = "公开概览")
@RestController
@RequestMapping("/public")
public class PublicController {

    @Resource
    private StatsService statsService;

    @Resource
    private TraceContractService contractService;

    @Operation(summary = "平台运行快照（免登录）")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = statsService.publicOverview();
        // 区块高度取自链上，不是数据库计数——即使库被改，这个数字也对不上
        data.put("blockNumber", contractService.isReady() ? contractService.getBlockNumber() : null);
        data.put("chainReady", contractService.isReady());
        data.put("nodeVersion", contractService.getNodeVersion());
        data.put("groupId", contractService.getGroupId());
        return R.ok(data);
    }
}
