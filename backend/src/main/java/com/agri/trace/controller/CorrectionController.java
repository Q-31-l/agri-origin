package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.CorrectionDTO;
import com.agri.trace.entity.BizCorrection;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.CorrectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Tag(name = "数据更正说明")
@RestController
@RequestMapping("/correction")
public class CorrectionController {

    @Resource
    private CorrectionService correctionService;

    @Operation(summary = "提交更正说明（追加式，不覆盖原始记录）")
    @RequirePerm("correction:create")
    @RequireRoles({Constants.ROLE_PRODUCER, Constants.ROLE_VILLAGE, Constants.ROLE_REGULATOR, Constants.ROLE_ADMIN})
    @PostMapping
    public R<Long> create(@Valid @RequestBody CorrectionDTO dto) {
        return R.ok("更正说明已记录并上链", correctionService.create(dto));
    }

    @Operation(summary = "更正记录分页查询")
    @RequirePerm("batch:list")
    @GetMapping("/page")
    public R<PageResult<BizCorrection>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) Long batchId) {
        return R.ok(correctionService.page(page, size, batchId));
    }
}
