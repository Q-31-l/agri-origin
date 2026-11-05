package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.LogisticsDTO;
import com.agri.trace.entity.BizLogisticsRecord;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.LogisticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Tag(name = "物流运输")
@RestController
@RequestMapping("/logistics")
public class LogisticsController {

    @Resource
    private LogisticsService logisticsService;

    @Operation(summary = "物流记录分页查询")
    @RequirePerm("logistics:list")
    @GetMapping("/page")
    public R<PageResult<BizLogisticsRecord>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Long batchId,
                                                  @RequestParam(required = false) String batchNo) {
        return R.ok(logisticsService.page(page, size, batchId, batchNo));
    }

    @Operation(summary = "物流交接登记（异常批次禁止发运）")
    @RequirePerm("logistics:create")
    @RequireRoles({Constants.ROLE_LOGISTICS, Constants.ROLE_ADMIN})
    @PostMapping
    public R<Long> create(@Valid @RequestBody LogisticsDTO dto) {
        return R.ok("物流交接登记成功", logisticsService.create(dto));
    }

    @Operation(summary = "物流记录审核")
    @RequirePerm("logistics:audit")
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        logisticsService.audit(id, dto);
        return R.ok();
    }
}
