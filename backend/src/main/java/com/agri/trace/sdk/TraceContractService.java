package com.agri.trace.sdk;

import com.agri.trace.config.ChainProperties;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合约业务封装：业务层只依赖本类，不直接接触 SDK
 */
@Slf4j
@Service
public class TraceContractService {

    @Resource
    private BcosClient bcosClient;
    @Resource
    private ChainProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isReady() {
        return properties.isEnabled() && bcosClient.isReady();
    }

    public String getChainMessage() {
        return bcosClient.getInitError();
    }

    public String getContractAddress() {
        return bcosClient.getContractAddress();
    }

    /** 节点二进制版本（如 2.9.1），用于看板展示"真实链版本" */
    public String getNodeVersion() {
        return bcosClient.getNodeVersion();
    }

    public Integer getGroupId() {
        return bcosClient.getGroupId();
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------
    public ChainResult registerBatch(BizBatch batch, String dataHash, String operatorName) {
        String origin = (batch.getVillageName() == null ? "" : batch.getVillageName())
                + (batch.getFarmName() == null ? "" : batch.getFarmName());
        return write("registerBatch", Arrays.asList(
                batch.getBatchNo(), batch.getTraceCode(), batch.getProductName(),
                origin, dataHash, operatorName == null ? "" : operatorName));
    }

    public ChainResult addRecord(String batchNo, String bizType, String bizId,
                                 String dataHash, String fileHash, String operatorName) {
        ChainResult result = write("addRecord", Arrays.asList(
                batchNo, bizType, bizId, dataHash,
                fileHash == null ? "" : fileHash,
                operatorName == null ? "" : operatorName));
        if (result.isSuccess()) {
            result.setIndex(recordCount(batchNo));
        }
        return result;
    }

    public ChainResult markAbnormal(String batchNo, String reason, String dataHash, String operatorName) {
        ChainResult result = write("markAbnormal", Arrays.asList(
                batchNo, truncate(reason), dataHash, operatorName == null ? "" : operatorName));
        if (result.isSuccess()) {
            result.setIndex(recordCount(batchNo));
        }
        return result;
    }

    public ChainResult disposeAbnormal(String batchNo, String resultText, String dataHash, String operatorName) {
        ChainResult result = write("disposeAbnormal", Arrays.asList(
                batchNo, truncate(resultText), dataHash, operatorName == null ? "" : operatorName));
        if (result.isSuccess()) {
            result.setIndex(recordCount(batchNo));
        }
        return result;
    }

    public ChainResult closeBatch(String batchNo, String dataHash, String operatorName) {
        return write("closeBatch", Arrays.asList(batchNo, dataHash,
                operatorName == null ? "" : operatorName));
    }

    public ChainResult addCorrection(String batchNo, String bizType, String bizId,
                                     String correctionHash, String operatorName) {
        ChainResult result = write("addCorrection", Arrays.asList(
                batchNo, bizType, bizId, correctionHash, operatorName == null ? "" : operatorName));
        if (result.isSuccess()) {
            result.setIndex(recordCount(batchNo));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 读操作
    // ------------------------------------------------------------------
    public Integer recordCount(String batchNo) {
        if (!isReady()) {
            return null;
        }
        try {
            List<Object> ret = bcosClient.call("getRecordCount", Collections.singletonList(batchNo));
            return ret.isEmpty() ? null : toBigInteger(ret.get(0)).intValue() - 1;
        } catch (Exception e) {
            log.warn("查询链上记录数失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 查询批次是否已在链上注册。
     * 合约除 registerBatch 外的所有写方法都带 batchExists 校验，
     * 批次未注册时 addRecord / markAbnormal 等会直接 revert。
     *
     * @return true=链上已存在，false=不存在，null=链不可用或查询失败
     */
    public Boolean batchExistsOnChain(String batchNo) {
        if (!isReady() || batchNo == null || batchNo.isEmpty()) {
            return null;
        }
        try {
            List<Object> ret = bcosClient.call("batchExistsOf", Collections.singletonList(batchNo));
            return !ret.isEmpty() && Boolean.parseBoolean(String.valueOf(ret.get(0)));
        } catch (Exception e) {
            log.warn("查询链上批次是否存在失败 batchNo={}: {}", batchNo, e.getMessage());
            return null;
        }
    }

    public Boolean verifyRecord(String batchNo, Integer index, String dataHash) {
        if (!isReady() || index == null) {
            return null;
        }
        try {
            List<Object> ret = bcosClient.call("verifyRecord",
                    Arrays.asList(batchNo, BigInteger.valueOf(index), dataHash));
            return !ret.isEmpty() && Boolean.parseBoolean(String.valueOf(ret.get(0)));
        } catch (Exception e) {
            log.warn("链上摘要校验失败: {}", e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> listRecords(String batchNo) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!isReady()) {
            return list;
        }
        try {
            List<Object> countRet = bcosClient.call("getRecordCount", Collections.singletonList(batchNo));
            int count = countRet.isEmpty() ? 0 : toBigInteger(countRet.get(0)).intValue();
            for (int i = 0; i < count; i++) {
                List<Object> ret = bcosClient.call("getRecord",
                        Arrays.asList(batchNo, BigInteger.valueOf(i)));
                if (ret.size() < 7) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", i);
                item.put("bizType", ret.get(0));
                item.put("bizId", ret.get(1));
                item.put("dataHash", ret.get(2));
                item.put("fileHash", ret.get(3));
                item.put("operatorName", ret.get(4));
                item.put("timestamp", toBigInteger(ret.get(5)).longValue() * 1000);
                item.put("abnormal", Boolean.parseBoolean(String.valueOf(ret.get(6))));
                list.add(item);
            }
        } catch (Exception e) {
            log.warn("读取链上记录失败: {}", e.getMessage());
        }
        return list;
    }

    public Map<String, Object> getBatch(String batchNo) {
        if (!isReady()) {
            return null;
        }
        try {
            List<Object> ret = bcosClient.call("getBatch", Collections.singletonList(batchNo));
            if (ret.size() < 8) {
                return null;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("traceCode", ret.get(0));
            map.put("productName", ret.get(1));
            map.put("origin", ret.get(2));
            map.put("latestHash", ret.get(3));
            map.put("status", ret.get(4));
            map.put("abnormalReason", ret.get(5));
            map.put("createdAt", toBigInteger(ret.get(6)).longValue() * 1000);
            map.put("recordCount", toBigInteger(ret.get(7)).intValue());
            return map;
        } catch (Exception e) {
            log.warn("读取链上批次失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean checkReceipt(String txHash) {
        return bcosClient.checkReceipt(txHash);
    }

    public Long getBlockNumber() {
        return bcosClient.getBlockNumber();
    }

    // ------------------------------------------------------------------
    private ChainResult write(String function, List<Object> params) {
        if (!properties.isEnabled()) {
            return ChainResult.skipped("演示模式：未启用链上存证，数据仅落库");
        }
        try {
            return bcosClient.sendTransaction(function, params);
        } catch (Exception e) {
            log.error("上链失败 function={}, batchNo={}, err={}", function,
                    params.isEmpty() ? "" : params.get(0), e.getMessage());
            String friendly = friendlyChainError(e.getMessage());
            if (properties.isStrict()) {
                throw BizException.chain("上链失败，操作已回滚：" + friendly);
            }
            return ChainResult.failed(friendly);
        }
    }

    /**
     * 合约 require 的英文原文直接抛给用户，界面上只会看到
     * 「交易执行失败：batch already exists」这种半截英文，既看不懂也不知道该怎么办。
     * 这里把已知的几种 revert 翻成可操作的提示；未知错误保留原文，方便排查。
     */
    private String friendlyChainError(String raw) {
        if (raw == null) {
            return "链上节点无响应，请稍后重试";
        }
        String lower = raw.toLowerCase();
        if (lower.contains("batch already exists")) {
            return "该批次号已在链上存证（历史批次占用），换个批次号重试即可";
        }
        if (lower.contains("batch not found")) {
            return "该批次尚未在链上登记，请先完成建档";
        }
        if (lower.contains("batch closed")) {
            return "批次已关闭，不能再追加记录";
        }
        if (lower.contains("already abnormal")) {
            return "批次已处于异常状态，无需重复标记";
        }
        if (lower.contains("batch not abnormal")) {
            return "批次当前不是异常状态，无法处置";
        }
        if (lower.contains("not authorized")) {
            return "链上账户未获授权，请联系管理员在合约中授权该账户";
        }
        if (lower.contains("timeout") || lower.contains("connect")) {
            return "链上节点连接超时，请检查 FISCO 节点状态";
        }
        return raw;
    }

    private BigInteger toBigInteger(Object o) {
        if (o instanceof BigInteger) {
            return (BigInteger) o;
        }
        String s = String.valueOf(o);
        return s.matches("\\d+") ? new BigInteger(s) : BigInteger.ZERO;
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
