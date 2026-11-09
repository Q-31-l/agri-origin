package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 审核流中的一个阶段（批次维度）。
 *
 * <p>状态取值：
 * <ul>
 *   <li>{@code DONE} 已完成并通过审核</li>
 *   <li>{@code PENDING} 已提交，等待审核（前端渲染成"等待 XX 审核"）</li>
 *   <li>{@code REJECTED} 被驳回，需提交方修正后重新提交</li>
 *   <li>{@code TODO} 尚未开始（前置环节未完成或还没人录入）</li>
 *   <li>{@code ABNORMAL} 该环节产生异常（质检不合格）</li>
 * </ul>
 */
@Data
public class WorkflowStageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BATCH / BATCH_AUDIT / PLANTING / HARVEST / QUALITY / LOGISTICS / SALES */
    private String code;
    private String title;
    private String status;

    /** 提交人（可能多人，用顿号连接） */
    private String submitBy;
    private String submitTime;
    /** 审核人 / 审核时间 / 审核意见 */
    private String auditBy;
    private String auditTime;
    private String auditRemark;

    /** 该环节由谁负责提交（角色名），用于"等待 XX 提交" */
    private String submitRole;
    /** 该环节由谁审核（角色名） */
    private String auditRole;
    /** 当前等待审核人的真实姓名，如"王秀兰" */
    private String waitingNames;
    /** 一句话说明，直接展示给用户 */
    private String tip;
    /** 记录条数（农事可能多条） */
    private Integer count;
}
