package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.R;
import com.agri.trace.dto.RevokeDTO;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.RevokeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 管理员兜底处置：撤销不合规的业务记录与实证文件。
 *
 * <p>与各业务 Controller 分开的原因：这些动作不受"谁提交、谁审核"的限制，
 * 也不属于任何一条正常流程，集中在一个入口既便于审计，也避免在各业务接口上
 * 挂一堆只有管理员才走得到的分支。</p>
 */
@Tag(name = "管理员兜底处置")
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Resource
    private RevokeService revokeService;

    @Operation(summary = "撤销业务记录（仅系统管理员）")
    @RequirePerm("record:revoke")
    @RequireRoles(Constants.ROLE_ADMIN)
    @PostMapping("/revoke")
    public R<Map<String, Object>> revoke(@RequestBody RevokeDTO dto) {
        Map<String, Object> detail = revokeService.revokeRecord(
                dto.getBizType(), dto.getBizId(), dto.getReason(), Boolean.TRUE.equals(dto.getWithEvidence()));
        String status = String.valueOf(detail.get("statusAfter"));
        return R.ok("记录已撤销，批次当前阶段：" + statusLabel(status), detail);
    }

    @Operation(summary = "撤销单张实证文件（仅系统管理员）")
    @RequirePerm("file:revoke")
    @RequireRoles(Constants.ROLE_ADMIN)
    @PostMapping("/file/{id}/revoke")
    public R<Void> revokeFile(@PathVariable Long id,
                              @RequestParam(required = false) String reason) {
        revokeService.revokeFile(id, reason);
        return R.<Void>ok("文件已撤销，不再对外展示", null);
    }

    /** 阶段码转中文，避免把 HARVESTED 这类枚举直接抛到提示语里 */
    private String statusLabel(String status) {
        if (status == null) {
            return "-";
        }
        switch (status) {
            case Constants.BATCH_PLANTING:
                return "种植中";
            case Constants.BATCH_HARVESTED:
                return "已采收";
            case Constants.BATCH_QUALIFIED:
                return "质检合格";
            case Constants.BATCH_IN_TRANSIT:
                return "运输中";
            case Constants.BATCH_ON_SALE:
                return "销售中";
            case Constants.BATCH_SOLD:
                return "已售罄";
            case Constants.BATCH_ABNORMAL:
                return "异常";
            case Constants.BATCH_CLOSED:
                return "已结案";
            default:
                return status;
        }
    }
}
