package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交成功后的"等待审核"提示。
 *
 * <p>农户/检测/物流/销售点提交后，只弹一句"提交成功"是不够的——
 * 他不知道下一步该等谁、要等多久、被驳回后谁来通知他。这里把
 * 「谁提交了什么 → 等待谁审核 → 通过后进入哪一环节」一次说清。
 */
@Data
public class SubmitNoticeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String bizType;
    private Long bizId;
    private Long batchId;
    private String batchNo;
    private String productName;

    /** 标题，如"农事记录已提交" */
    private String title;
    /** 正文，如"已提交，等待溪头村 王秀兰（村级管理员）审核" */
    private String message;
    private String waitingRole;
    private String waitingNames;
    /** 审核通过后进入的环节 */
    private String nextStep;
}
