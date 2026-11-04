package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 链上链下一致性校验结果
 */
@Data
public class ChainVerifyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String batchNo;
    private boolean chainEnabled;
    private boolean passed;
    private String message;
    private LocalDateTime verifiedAt;
    private int totalRecords;
    private int matchedRecords;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String bizType;
        private String localHash;
        private String chainHash;
        private String txHash;
        private Integer chainIndex;
        private boolean hashMatched;
        private boolean receiptOk;
        private boolean matched;
        private String message;
    }
}
