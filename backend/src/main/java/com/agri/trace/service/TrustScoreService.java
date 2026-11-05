package com.agri.trace.service;

import com.agri.trace.common.Constants;
import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizQualityReport;
import com.agri.trace.vo.BatchDetailVO;
import com.agri.trace.vo.TrustScoreVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 溯源可信度评分：AgriTrust-5D
 *
 * <p>传统溯源系统只回答"这条记录有没有上链"，是/否二值，消费者依然无法判断
 * 一份溯源档案到底有多可靠。这里把可信度拆成五个可观测维度加权计分：
 *
 * <pre>
 *   流程完整度  30   种植/采收/质检/物流/销售 五环节各 6 分
 *   链上存证度  30   批次本体 15 分 + 各环节记录上链率 ×15 分
 *   质量合规性  20   最近一次检测 PASS=20 / 无检测=8 / FAIL=0
 *   异常洁净度  15   未处置异常每条 -8、已处置每条 -3、数据更正每次 -2
 *   信息完备度   5   农场/农户/产地/数量/采收日期 各 1 分
 * </pre>
 *
 * <p>纯内存计算、只读、无副作用，且每处扣分都会落到 deductions 里，
 * 前端可以逐条展示"为什么扣分"，避免评分变成黑箱。
 */
@Service
public class TrustScoreService {

    private static final int W_FLOW = 30;
    private static final int W_CHAIN = 30;
    private static final int W_QUALITY = 20;
    private static final int W_ABNORMAL = 15;
    private static final int W_INFO = 5;

    public TrustScoreVO evaluate(BatchDetailVO detail) {
        TrustScoreVO vo = new TrustScoreVO();
        List<String> deductions = new ArrayList<>();
        List<String> stages = requiredStages(detail.getBatch());

        TrustScoreVO.Dimension flow = scoreFlow(detail, stages, deductions);
        TrustScoreVO.Dimension chain = scoreChain(detail, deductions);
        TrustScoreVO.Dimension quality = scoreQuality(detail, stages, deductions);
        TrustScoreVO.Dimension abnormal = scoreAbnormal(detail, deductions);
        TrustScoreVO.Dimension info = scoreInfo(detail, stages, deductions);

        vo.getDimensions().add(flow);
        vo.getDimensions().add(chain);
        vo.getDimensions().add(quality);
        vo.getDimensions().add(abnormal);
        vo.getDimensions().add(info);

        int total = flow.getScore() + chain.getScore() + quality.getScore()
                + abnormal.getScore() + info.getScore();
        total = Math.max(0, Math.min(100, total));
        vo.setScore(total);

        if (total >= 85) {
            vo.setGrade("A");
            vo.setGradeText("高度可信");
            vo.setSummary("关键环节齐全、链上存证完整，可作为可信溯源凭证");
        } else if (total >= 70) {
            vo.setGrade("B");
            vo.setGradeText("基本可信");
            vo.setSummary("主体信息可信，个别环节记录或存证尚有欠缺");
        } else if (total >= 50) {
            vo.setGrade("C");
            vo.setGradeText("存疑");
            vo.setSummary("存在环节缺失或异常记录，建议向生产主体核实");
        } else {
            vo.setGrade("D");
            vo.setGradeText("不可信");
            vo.setSummary("溯源档案严重不完整，不建议作为质量依据");
        }

        vo.setDeductions(deductions);
        vo.setScope("按「" + stageName(detail.getBatch().getStatus()) + "」阶段应完成的 "
                + stages.size() + " 个环节评估");
        return vo;
    }

    private String stageName(String status) {
        if (status == null) {
            return "种植";
        }
        switch (status) {
            case Constants.BATCH_PLANTING:
                return "种植";
            case Constants.BATCH_HARVESTED:
                return "采收";
            case Constants.BATCH_QUALIFIED:
                return "质检合格";
            case Constants.BATCH_IN_TRANSIT:
                return "运输";
            case Constants.BATCH_ON_SALE:
                return "销售";
            case Constants.BATCH_SOLD:
                return "已售罄";
            case Constants.BATCH_ABNORMAL:
                return "异常管控";
            case Constants.BATCH_CLOSED:
                return "已关闭";
            default:
                return "种植";
        }
    }

    // ---------------- 阶段识别 ----------------

    /**
     * 按批次当前状态推导"理应已经完成"的环节。
     *
     * <p>一个还在种植期的批次没有采收、质检、物流记录是正常的，
     * 不能因此判它"不可信"。只有走到该环节却缺记录，才是真的缺。
     */
    private List<String> requiredStages(BizBatch batch) {
        String status = batch.getStatus() == null ? Constants.BATCH_PLANTING : batch.getStatus();
        List<String> stages = new ArrayList<>();
        stages.add("planting");
        if (Constants.BATCH_PLANTING.equals(status)) {
            return stages;
        }
        stages.add("harvest");
        if (Constants.BATCH_HARVESTED.equals(status)) {
            return stages;
        }
        stages.add("quality");
        if (Constants.BATCH_QUALIFIED.equals(status) || Constants.BATCH_ABNORMAL.equals(status)) {
            return stages;
        }
        stages.add("logistics");
        if (Constants.BATCH_IN_TRANSIT.equals(status)) {
            return stages;
        }
        stages.add("sales");
        return stages;
    }

    private String stageLabel(String key) {
        switch (key) {
            case "planting":
                return "农事记录";
            case "harvest":
                return "采收记录";
            case "quality":
                return "质检报告";
            case "logistics":
                return "物流记录";
            default:
                return "销售记录";
        }
    }

    // ---------------- 流程完整度 ----------------

    private TrustScoreVO.Dimension scoreFlow(BatchDetailVO d, List<String> stages, List<String> deductions) {
        int done = 0;
        List<String> missing = new ArrayList<>();

        for (String stage : stages) {
            if (hasStage(d, stage)) {
                done++;
            } else {
                missing.add(stageLabel(stage));
            }
        }

        int score = Math.round((float) W_FLOW * done / stages.size());
        String note = done + "/" + stages.size() + " 个应完成环节";
        if (!missing.isEmpty()) {
            note += "，缺" + String.join("、", missing);
            deductions.add("缺少" + String.join("、", missing) + "（-" + (W_FLOW - score) + "）");
        }
        return new TrustScoreVO.Dimension("flow", "流程完整度", score, W_FLOW, note);
    }

    private boolean hasStage(BatchDetailVO d, String stage) {
        switch (stage) {
            case "planting":
                return !d.getPlantings().isEmpty();
            case "harvest":
                return !d.getHarvests().isEmpty();
            case "quality":
                return !d.getQualities().isEmpty();
            case "logistics":
                return !d.getLogistics().isEmpty();
            default:
                return !d.getSales().isEmpty();
        }
    }

    // ---------------- 链上存证度 ----------------

    private TrustScoreVO.Dimension scoreChain(BatchDetailVO d, List<String> deductions) {
        // 链服务未启用（演示模式）时不做真实上链，按基础分计，
        // 避免环境原因把整条溯源判成不可信，同时如实告知消费者"这一环没验到"
        if (!d.isChainEnabled()) {
            deductions.add("演示模式：链服务未启用，未做真实上链校验（-12）");
            return new TrustScoreVO.Dimension("chain", "链上存证度", 18, W_CHAIN, "演示模式未接入链服务");
        }

        BizBatch batch = d.getBatch();
        int batchPart;
        String status = batch.getChainStatus();
        if (Constants.CHAIN_SUCCESS.equals(status)) {
            batchPart = 15;
        } else if (Constants.CHAIN_SKIPPED.equals(status)) {
            batchPart = 9;
        } else {
            batchPart = 0;
            deductions.add("批次档案未完成上链存证（-15）");
        }

        int total = 0;
        double credit = 0;
        for (String s : collectChainStatus(d)) {
            total++;
            if (Constants.CHAIN_SUCCESS.equals(s)) {
                credit += 1;
            } else if (Constants.CHAIN_SKIPPED.equals(s)) {
                // 演示模式下链服务未启用，按 60% 计分，避免离线演示被判为不可信
                credit += 0.6;
            }
        }

        int ratePart = total == 0 ? 0 : (int) Math.round(15.0 * credit / total);
        if (total > 0 && ratePart < 15) {
            deductions.add("环节记录上链率 " + Math.round(100.0 * credit / total) + "%，未全部存证（-"
                    + (15 - ratePart) + "）");
        }

        String note = total == 0
                ? "暂无环节记录"
                : "上链 " + Math.round(credit) + "/" + total + " 条";
        return new TrustScoreVO.Dimension("chain", "链上存证度", batchPart + ratePart, W_CHAIN, note);
    }

    private List<String> collectChainStatus(BatchDetailVO d) {
        List<String> list = new ArrayList<>();
        d.getPlantings().forEach(r -> list.add(r.getChainStatus()));
        d.getHarvests().forEach(r -> list.add(r.getChainStatus()));
        d.getQualities().forEach(r -> list.add(r.getChainStatus()));
        d.getLogistics().forEach(r -> list.add(r.getChainStatus()));
        d.getSales().forEach(r -> list.add(r.getChainStatus()));
        return list;
    }

    // ---------------- 质量合规性 ----------------

    private TrustScoreVO.Dimension scoreQuality(BatchDetailVO d, List<String> stages, List<String> deductions) {
        List<BizQualityReport> reports = d.getQualities();
        if (reports.isEmpty()) {
            // 还没走到检测环节的批次，不因"未检测"扣分
            if (!stages.contains("quality")) {
                return new TrustScoreVO.Dimension("quality", "质量合规性", W_QUALITY, W_QUALITY, "尚未进入检测环节");
            }
            deductions.add("尚未上传质检报告（-12）");
            return new TrustScoreVO.Dimension("quality", "质量合规性", W_QUALITY - 12, W_QUALITY, "未经检测");
        }

        BizQualityReport latest = reports.get(reports.size() - 1);
        String result = latest.getInspectResult();
        if ("PASS".equalsIgnoreCase(result)) {
            return new TrustScoreVO.Dimension("quality", "质量合规性", W_QUALITY, W_QUALITY,
                    "最近检测合格" + (latest.getReportNo() == null ? "" : " · " + latest.getReportNo()));
        }
        if ("FAIL".equalsIgnoreCase(result)) {
            deductions.add("最近一次质检不合格（-20）");
            return new TrustScoreVO.Dimension("quality", "质量合规性", 0, W_QUALITY, "最近检测不合格");
        }
        return new TrustScoreVO.Dimension("quality", "质量合规性", W_QUALITY - 8, W_QUALITY, "检测结论不明确");
    }

    // ---------------- 异常洁净度 ----------------

    private TrustScoreVO.Dimension scoreAbnormal(BatchDetailVO d, List<String> deductions) {
        int score = W_ABNORMAL;
        int handling = 0;
        int done = 0;

        for (BizAbnormalDisposal item : d.getDisposals()) {
            if (Constants.ABNORMAL_DONE.equals(item.getStatus())) {
                done++;
                score -= 3;
            } else {
                handling++;
                score -= 8;
            }
        }
        if (handling > 0) {
            deductions.add("存在 " + handling + " 条处置中的异常（每条 -8）");
        }
        if (done > 0) {
            deductions.add("发生过 " + done + " 次已处置异常（每条 -3）");
        }

        int corrections = d.getCorrections().size();
        if (corrections > 0) {
            score -= corrections * 2;
            deductions.add("发生过 " + corrections + " 次数据更正（每次 -2）");
        }

        score = Math.max(0, score);
        String note;
        if (handling == 0 && done == 0 && corrections == 0) {
            note = "无异常记录";
        } else {
            note = "异常 " + (handling + done) + " 条（处置中 " + handling + "）";
        }
        return new TrustScoreVO.Dimension("abnormal", "异常洁净度", score, W_ABNORMAL, note);
    }

    // ---------------- 信息完备度 ----------------

    private TrustScoreVO.Dimension scoreInfo(BatchDetailVO d, List<String> stages, List<String> deductions) {
        BizBatch b = d.getBatch();
        int score = 0;
        int full = 0;
        List<String> missing = new ArrayList<>();

        full++;
        if (notBlank(b.getFarmName())) {
            score++;
        } else {
            missing.add("农场");
        }
        full++;
        if (notBlank(b.getFarmerName())) {
            score++;
        } else {
            missing.add("农户");
        }
        full++;
        if (notBlank(b.getVillageName())) {
            score++;
        } else {
            missing.add("产地");
        }
        full++;
        if (b.getQuantity() != null && b.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            score++;
        } else {
            missing.add("产量");
        }
        // 采收日期只在已经进入采收阶段后才要求
        if (stages.contains("harvest")) {
            full++;
            if (b.getHarvestDate() != null) {
                score++;
            } else {
                missing.add("采收日期");
            }
        }

        // full 会随阶段变化（未采收时不要求采收日期），按比例折算到满分 5 分
        int dimScore = Math.round((float) W_INFO * score / full);
        if (!missing.isEmpty()) {
            deductions.add("档案信息缺失：" + String.join("、", missing) + "（-" + (W_INFO - dimScore) + "）");
        }
        return new TrustScoreVO.Dimension("info", "信息完备度", dimScore, W_INFO,
                missing.isEmpty() ? "关键字段齐全" : "缺 " + String.join("、", missing));
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
