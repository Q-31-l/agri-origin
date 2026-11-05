package com.agri.trace.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 溯源可信度评分（AgriTrust-5D）
 *
 * 把「能不能溯源」这个二值判断，升级成「有多可信」的量化指标：
 * 五个维度分别计分、加权求和，并把每一处扣分的原因原样列出，
 * 消费者看到的不是一个模糊的星级，而是"哪一环掉了分、为什么"。
 */
@Data
public class TrustScoreVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0-100 综合得分 */
    private int score;
    /** A / B / C / D */
    private String grade;
    /** 高度可信 / 基本可信 / 存疑 / 不可信 */
    private String gradeText;
    /** 一句话结论 */
    private String summary;
    /** 本次评估的考核范围（按批次所处阶段） */
    private String scope;
    /** 五维明细 */
    private List<Dimension> dimensions = new ArrayList<>();
    /** 扣分原因（可直接展示给消费者） */
    private List<String> deductions = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimension implements Serializable {

        private static final long serialVersionUID = 1L;

        private String key;
        private String name;
        private int score;
        private int max;
        private String note;
    }
}
