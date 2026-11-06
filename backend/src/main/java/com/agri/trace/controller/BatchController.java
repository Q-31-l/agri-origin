package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.BatchDTO;
import com.agri.trace.dto.BatchUpdateDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.BatchService;
import com.agri.trace.service.ChainService;
import com.agri.trace.vo.BatchDetailVO;
import com.agri.trace.vo.ChainVerifyVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Tag(name = "批次全生命周期")
@RestController
@RequestMapping("/batch")
public class BatchController {

    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;

    @Operation(summary = "批次分页查询（支持按批次 / 主体 / 时间筛选，按角色自动收窄数据范围）")
    @RequirePerm("batch:list")
    @GetMapping("/page")
    public R<PageResult<BizBatch>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Integer abnormalFlag,
                                        @RequestParam(required = false) Long villageId,
                                        @RequestParam(required = false) Long farmId,
                                        @RequestParam(required = false) Long farmerId,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate) {
        return R.ok(batchService.page(page, size, keyword, status, abnormalFlag,
                villageId, farmId, farmerId, startDate, endDate));
    }

    @Operation(summary = "批次下拉选项（usable=true 只返回可录入的：指定状态 + 已核验 + 无异常）")
    @RequirePerm("batch:list")
    @GetMapping("/options")
    public R<List<BizBatch>> options(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) Boolean usable) {
        return R.ok(batchService.listForSelect(status, usable));
    }

    @Operation(summary = "批次详情（全流程数据 + 时间轴 + 存证流水）")
    @RequirePerm("batch:detail")
    @GetMapping("/{id}")
    public R<BatchDetailVO> detail(@PathVariable Long id) {
        return R.ok(batchService.detail(id));
    }

    @Operation(summary = "创建批次（自动生成批次编码与溯源码并上链登记）")
    @RequirePerm("batch:create")
    @RequireRoles({Constants.ROLE_PRODUCER, Constants.ROLE_ADMIN})
    @PostMapping
    public R<Long> create(@Valid @RequestBody BatchDTO dto) {
        return R.ok("批次创建成功", batchService.create(dto));
    }

    @Operation(summary = "修改批次备注（建档要素不可改，须走更正说明）")
    @RequirePerm("batch:update")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody BatchUpdateDTO dto) {
        batchService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "村级批次核验")
    @RequirePerm("batch:audit")
    @RequireRoles({Constants.ROLE_VILLAGE, Constants.ROLE_REGULATOR, Constants.ROLE_ADMIN})
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        batchService.audit(id, dto);
        return R.ok();
    }

    @Operation(summary = "批次链上链下一致性校验")
    @RequirePerm("chain:verify")
    @GetMapping("/{id}/verify")
    public R<ChainVerifyVO> verify(@PathVariable Long id) {
        return R.ok(chainService.verifyBatch(batchService.getAuthorized(id).getBatchNo()));
    }
}
