package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 当前用户的审核待办。
 *
 * <p>按业务环节分组（批次核验 / 农事 / 采收 / 质检 / 物流 / 销售 / 异常处置），
 * 只返回"确实轮到他审"的数据：既要角色与权限点齐备，也要落在他的数据范围内。
 * 这样村管不会看到邻村的待办，监管不会看到已经处置完的异常。
 */
@Data
public class WorkflowTodoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int total;
    private String roleCode;
    private List<Group> groups = new ArrayList<>();

    @Data
    public static class Group implements Serializable {
        private static final long serialVersionUID = 1L;

        /** BATCH / PLANTING / HARVEST / QUALITY / LOGISTICS / SALES / ABNORMAL */
        private String type;
        private String title;
        /** 点进去跳到哪一页 */
        private String route;
        private int count;
        private List<Item> items = new ArrayList<>();
    }

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private Long batchId;
        private String batchNo;
        private String productName;
        private String submitBy;
        private LocalDateTime submitTime;
        private String status;
        /** 一句话摘要：农事类型、采收重量、检测结论等 */
        private String summary;
        /** 数据来自谁（村名 / 机构名），方便村管与监管判断归属 */
        private String from;
    }
}
