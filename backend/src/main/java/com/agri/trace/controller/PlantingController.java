package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.PlantingDTO;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.PlantingService;
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

@Tag(name = "农事记录")
@RestController
@RequestMapping("/planting")
public class PlantingController {

    @Resource
    private PlantingService plantingService;

    @Operation(summary = "农事记录分页查询")
    @RequirePerm("planting:list")
    @GetMapping("/page")
    public R<PageResult<BizPlantingRecord>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) Long batchId,
                                                 @RequestParam(required = false) String batchNo) {
        return R.ok(plantingService.page(page, size, batchId, batchNo));
    }

    @Operation(summary = "按批次查询农事记录")
    @RequirePerm("planting:list")
    @GetMapping("/batch/{batchId}")
    public R<List<BizPlantingRecord>> listByBatch(@PathVariable Long batchId) {
        return R.ok(plantingService.listByBatch(batchId));
    }

    @Operation(summary = "农户提交农事记录（含现场照片，自动上链）")
    @RequirePerm("planting:create")
    @RequireRoles({Constants.ROLE_FARMER, Constants.ROLE_ADMIN})
    @PostMapping
    public R<Long> create(@Valid @RequestBody PlantingDTO dto) {
        return R.ok("农事记录提交成功", plantingService.create(dto));
    }

    @Operation(summary = "村级管理员审核农事记录")
    @RequirePerm("planting:audit")
    @RequireRoles({Constants.ROLE_VILLAGE, Constants.ROLE_ADMIN})
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        plantingService.audit(id, dto);
        return R.ok();
    }
}
