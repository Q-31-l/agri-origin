package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.entity.BaseVillage;
import com.agri.trace.mapper.BaseVillageMapper;
import com.agri.trace.sdk.TraceContractService;
import com.agri.trace.service.StatsService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Resource
    private BaseVillageMapper villageMapper;

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

    /**
     * 注册页的"所属村"下拉（免登录）。
     *
     * <p>只返回 id 与名称：注册发生在登录之前，调不了 {@code /system/village/list}；
     * 而村名本身是公开行政区划信息，不涉及任何业务数据。地址、负责人等字段
     * 一概不出这个接口，避免公开面被拿来枚举联系人。</p>
     */
    @Operation(summary = "行政村选项（免登录，注册页使用）")
    @GetMapping("/villages")
    public R<List<Map<String, Object>>> villages() {
        List<BaseVillage> list = villageMapper.selectList(Wrappers.<BaseVillage>lambdaQuery()
                .orderByAsc(BaseVillage::getId));
        List<Map<String, Object>> options = new ArrayList<>(list.size());
        for (BaseVillage v : list) {
            Map<String, Object> m = new LinkedHashMap<>(6);
            m.put("id", v.getId());
            m.put("villageName", v.getVillageName());
            m.put("regionName", v.getRegionName());
            // 经纬度是公开行政区划信息，注册页用它做"最近村庄"推荐；
            // 不涉及任何业务数据，不会扩大联系人等敏感信息的暴露面
            m.put("lat", v.getLat());
            m.put("lng", v.getLng());
            options.add(m);
        }
        return R.ok(options);
    }
}
