package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.DisposeDTO;
import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.AbnormalService;
import com.agri.trace.vo.AbnormalTraceVO;
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

@Tag(name = "异常批次管控")
@RestController
@RequestMapping("/abnormal")
public class AbnormalController {

    @Resource
    private AbnormalService abnormalService;

    @Operation(summary = "异常批次分页查询")
    @RequirePerm("abnormal:list")
    @GetMapping("/page")
    public R<PageResult<BizAbnormalDisposal>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String batchNo,
                                                   @RequestParam(required = false) String status) {
        return R.ok(abnormalService.page(page, size, batchNo, status));
    }

    @Operation(summary = "异常处置登记（处置结果上链）")
    @RequirePerm("abnormal:dispose")
    @RequireRoles({Constants.ROLE_REGULATOR, Constants.ROLE_ADMIN})
    @PostMapping("/{id}/dispose")
    public R<Void> dispose(@PathVariable Long id, @Valid @RequestBody DisposeDTO dto) {
        abnormalService.dispose(id, dto);
        return R.ok();
    }

    @Operation(summary = "上下游追溯与责任主体定位")
    @RequirePerm("abnormal:trace")
    @GetMapping("/{id}/trace")
    public R<AbnormalTraceVO> trace(@PathVariable Long id) {
        return R.ok(abnormalService.trace(id));
    }

    @Operation(summary = "异常批次结案")
    @RequirePerm("abnormal:dispose")
    @RequireRoles({Constants.ROLE_REGULATOR, Constants.ROLE_ADMIN})
    @PostMapping("/batch/{batchId}/close")
    public R<Void> close(@PathVariable Long batchId) {
        abnormalService.close(batchId);
        return R.ok();
    }
}
