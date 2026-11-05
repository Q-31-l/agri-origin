package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.SalesDTO;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.SalesService;
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

@Tag(name = "销售登记")
@RestController
@RequestMapping("/sales")
public class SalesController {

    @Resource
    private SalesService salesService;

    @Operation(summary = "销售记录分页查询")
    @RequirePerm("sales:list")
    @GetMapping("/page")
    public R<PageResult<BizSalesRecord>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long batchId,
                                              @RequestParam(required = false) String batchNo) {
        return R.ok(salesService.page(page, size, batchId, batchNo));
    }

    @Operation(summary = "销售登记（异常批次强制拦截）")
    @RequirePerm("sales:create")
    @RequireRoles({Constants.ROLE_SELLER, Constants.ROLE_ADMIN})
    @PostMapping
    public R<Long> create(@Valid @RequestBody SalesDTO dto) {
        return R.ok("销售登记成功", salesService.create(dto));
    }

    @Operation(summary = "销售记录审核")
    @RequirePerm("sales:audit")
    @RequireRoles({Constants.ROLE_REGULATOR, Constants.ROLE_ADMIN})
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        salesService.audit(id, dto);
        return R.ok();
    }
}
