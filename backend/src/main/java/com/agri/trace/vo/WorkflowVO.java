package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批次全链路审核进度：从建档、村级核验，到农事、采收、质检、物流、销售，
 * 逐级给出「谁提交了什么 / 现在卡在谁那里 / 谁在什么时候审的」。
 */
@Data
public class WorkflowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long batchId;
    private String batchNo;
    private String productName;
    private String villageName;
    private String batchStatus;
    private String auditStatus;

    /** 当前所处环节标题 */
    private String currentStage;
    private String currentStageCode;
    /** 当前等待谁处理（角色名） */
    private String waitingRole;
    /** 当前等待的具体人（真实姓名） */
    private String waitingNames;
    /** 一句话进度描述，页面直接显示 */
    private String tip;

    /** 已通过审核的环节数 */
    private Integer doneCount;
    /** 已推进（已提交或已通过）的环节数，进度条与百分比按它算 */
    private Integer advancedCount;
    private Integer totalCount;
    /** 0-100，已推进环节占比 */
    private Integer percent;

    private List<WorkflowStageVO> stages = new ArrayList<>();
}
