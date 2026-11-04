package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.service.TraceService;
import com.agri.trace.vo.ChainVerifyVO;
import com.agri.trace.vo.TraceVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 公共溯源接口：消费者扫码免登录
 */
@Tag(name = "扫码溯源（公开）")
@RestController
@RequestMapping("/trace")
public class TraceController {

    @Resource
    private TraceService traceService;

    @Operation(summary = "按溯源码/批次号查询完整流转履历")
    @GetMapping("/{code}")
    public R<TraceVO> query(@PathVariable String code) {
        return R.ok(traceService.query(code));
    }

    @Operation(summary = "消费者侧存证校验")
    @GetMapping("/{code}/verify")
    public R<ChainVerifyVO> verify(@PathVariable String code) {
        return R.ok(traceService.verify(code));
    }
}
