package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.service.WorkflowService;
import com.agri.trace.vo.SubmitNoticeVO;
import com.agri.trace.vo.WorkflowTodoVO;
import com.agri.trace.vo.WorkflowVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 审核流：进度、待办、提交提示。
 *
 * <p>这三个接口解决同一个问题——"我提交了，然后呢？"
 * 提交方看进度，审核方看待办，中间不再靠口头或群里喊。
 */
@Tag(name = "审核流与待办")
@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    @Resource
    private WorkflowService workflowService;

    @Operation(summary = "批次全链路审核进度（每个环节：谁提交、谁审核、正在等谁）")
    @RequirePerm("batch:list")
    @GetMapping("/progress/{batchId}")
    public R<WorkflowVO> progress(@PathVariable Long batchId) {
        return R.ok(workflowService.progress(batchId));
    }

    @Operation(summary = "我的审核待办（按环节分组，只返回确实轮到我审的数据）")
    @GetMapping("/todo")
    public R<WorkflowTodoVO> todo() {
        return R.ok(workflowService.todo());
    }

    @Operation(summary = "提交后的等待审核提示：等待谁、通过后进入哪一环")
    @GetMapping("/notice")
    public R<SubmitNoticeVO> notice(@RequestParam String bizType, @RequestParam Long bizId) {
        return R.ok(workflowService.notice(bizType, bizId));
    }
}
