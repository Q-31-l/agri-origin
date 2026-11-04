package com.agri.trace.common;

import com.agri.trace.entity.BizBatch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摘要算法单元测试：稳定复算 + 篡改可发现
 */
class DigestHelperTest {

    private BizBatch sample() {
        BizBatch batch = new BizBatch();
        batch.setBatchNo("AG20260512001");
        batch.setTraceCode("TC20260512001");
        batch.setProductId(1L);
        batch.setProductName("红颜草莓");
        batch.setFarmerId(5L);
        batch.setFarmId(1L);
        batch.setVillageId(1L);
        batch.setPlantingDate(LocalDate.of(2026, 2, 10));
        batch.setQuantity(new BigDecimal("1200.00"));
        batch.setUnit("kg");
        return batch;
    }

    @Test
    void batchDigestShouldBeStableAnd64Hex() {
        String first = DigestHelper.batch(sample());
        String second = DigestHelper.batch(sample());

        assertEquals(first, second, "同一业务数据多次计算结果必须一致");
        assertEquals(64, first.length(), "SHA-256 十六进制摘要长度应为 64");
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void batchDigestShouldChangeWhenTampered() {
        BizBatch batch = sample();
        String origin = DigestHelper.batch(batch);

        batch.setQuantity(new BigDecimal("1500.00"));
        String tampered = DigestHelper.batch(batch);

        assertNotEquals(origin, tampered, "关键字段被修改后摘要必须变化");
    }

    @Test
    void digestShouldTolerateNullFields() {
        BizBatch batch = sample();
        batch.setVariety(null);
        batch.setRemark(null);

        assertEquals(64, DigestHelper.batch(batch).length());
    }
}
