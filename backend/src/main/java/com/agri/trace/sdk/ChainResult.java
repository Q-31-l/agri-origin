package com.agri.trace.sdk;

import lombok.Data;

import java.io.Serializable;

/**
 * 上链结果
 */
@Data
public class ChainResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    /** SUCCESS / FAILED / SKIPPED */
    private String status;
    private String txHash;
    private Long blockNumber;
    private Long gasUsed;
    private Integer index;
    private String message;

    public static ChainResult success(String txHash, Long blockNumber, Long gasUsed) {
        ChainResult r = new ChainResult();
        r.setSuccess(true);
        r.setStatus("SUCCESS");
        r.setTxHash(txHash);
        r.setBlockNumber(blockNumber);
        r.setGasUsed(gasUsed);
        r.setMessage("存证成功");
        return r;
    }

    public static ChainResult skipped(String message) {
        ChainResult r = new ChainResult();
        r.setSuccess(false);
        r.setStatus("SKIPPED");
        r.setMessage(message);
        return r;
    }

    public static ChainResult failed(String message) {
        ChainResult r = new ChainResult();
        r.setSuccess(false);
        r.setStatus("FAILED");
        r.setMessage(message);
        return r;
    }
}
