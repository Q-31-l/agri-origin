package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.HarvestDTO;
import com.agri.trace.entity.BizHarvestRecord;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.HarvestService;
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

@Tag(name = "采收登记")
@RestController
@RequestMapping("/harvest")
public class HarvestController {

    @Resource
    private HarvestService harvestService;

    @Operation(summary = "采收记录分页查询")
    @RequirePerm("harvest:list")
    @GetMapping("/page")
    public R<PageResult<BizHarvestRecord>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long batchId,
                                                @RequestParam(required = false) String batchNo) {
        return R.ok(harvestService.page(page, size, batchId, batchNo));
    }

    @Operation(summary = "农户提交采收信息（一个批次仅一条，自动上链）")
    @RequirePerm("harvest:create")
    @RequireRoles({Constants.ROLE_FARMER, Constants.ROLE_ADMIN})
    @PostMapping
    public R<Long> create(@Valid @RequestBody HarvestDTO dto) {
        return R.ok("采收信息提交成功", harvestService.create(dto));
    }

    @Operation(summary = "村级管理员审核采收信息")
    @RequirePerm("harvest:audit")
    @RequireRoles({Constants.ROLE_VILLAGE, Constants.ROLE_ADMIN})
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        harvestService.audit(id, dto);
        return R.ok();
    }
}
