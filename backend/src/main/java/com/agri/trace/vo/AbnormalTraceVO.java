package com.agri.trace.vo;

import com.agri.trace.entity.BizAbnormalDisposal;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.vo.EvidenceVO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 异常批次上下游追溯结果
 */
@Data
public class AbnormalTraceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BizBatch batch;
    private List<BizAbnormalDisposal> disposals = new ArrayList<>();
    /** 上游：同农户/同地块历史批次 */
    private List<BizBatch> upstream = new ArrayList<>();
    /** 下游：物流与销售流向 */
    private List<Map<String, Object>> downstream = new ArrayList<>();
    /** 责任主体定位 */
    private List<Map<String, Object>> responsibilities = new ArrayList<>();
    /** 每条处置记录的实证材料（key = 处置记录 id），含异常上报与处置两个阶段的图片/视频 */
    private Map<Long, List<EvidenceVO>> evidences = new java.util.LinkedHashMap<>();
}
