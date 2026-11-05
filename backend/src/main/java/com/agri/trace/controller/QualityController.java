package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.QualityDTO;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.QualityService;
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
import java.util.List;

@Tag(name = "质量检测")
@RestController
@RequestMapping("/quality")
public class QualityController {

    @Resource
    private QualityService qualityService;

    @Operation(summary = "质检报告分页查询")
    @RequirePerm("quality:list")
    @GetMapping("/page")
    public R<PageResult<BizQualityReport>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long batchId,
                                                @RequestParam(required = false) String batchNo,
                                                @RequestParam(required = false) String result) {
        return R.ok(qualityService.page(page, size, batchId, batchNo, result));
    }

    @Operation(summary = "按批次查询质检报告")
    @RequirePerm("quality:list")
    @GetMapping("/batch/{batchId}")
    public R<List<BizQualityReport>> listByBatch(@PathVariable Long batchId) {
        return R.ok(qualityService.listByBatch(batchId));
    }

    @Operation(summary = "上传质检报告并判定（不合格自动熔断并上链）")
    @RequirePerm("quality:create")
    @RequireRoles({Constants.ROLE_INSPECTOR, Constants.ROLE_ADMIN})
    @PostMapping
    public R<Long> create(@Valid @RequestBody QualityDTO dto) {
        return R.ok("质检报告提交成功", qualityService.create(dto));
    }

    @Operation(summary = "质检报告审核")
    @RequirePerm("quality:audit")
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        qualityService.audit(id, dto);
        return R.ok();
    }
}
