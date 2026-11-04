package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.service.ChainService;
import com.agri.trace.service.StatsService;
import com.agri.trace.vo.StatsVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@Tag(name = "数据统计")
@RestController
@RequestMapping("/stats")
public class StatsController {

    @Resource
    private StatsService statsService;
    @Resource
    private ChainService chainService;

    @Operation(summary = "看板总览")
    @RequirePerm("stats:overview")
    @GetMapping("/overview")
    public R<StatsVO> overview() {
        return R.ok(statsService.overview());
    }

    @Operation(summary = "近期上链失败记录")
    @RequirePerm("chain:list")
    @GetMapping("/chain-failed")
    public R<List<com.agri.trace.entity.ChainRecord>> chainFailed() {
        return R.ok(chainService.listFailed());
    }
}
