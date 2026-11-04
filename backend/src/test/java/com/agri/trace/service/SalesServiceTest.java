package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.dto.SalesDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizSalesRecordMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.TraceContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 异常批次销售熔断测试（核心验收点）
 */
@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock
    private BizSalesRecordMapper salesMapper;
    @Mock
    private BizBatchMapper batchMapper;
    @Mock
    private BatchService batchService;
    @Mock
    private ChainService chainService;
    @Mock
    private TraceContractService contractService;

    @InjectMocks
    private SalesService salesService;

    @BeforeEach
    void setUp() {
        UserContext.set(LoginUser.builder()
                .userId(9L).username("seller01").realName("吴晓芸")
                .roleCode(Constants.ROLE_SELLER).orgName("永辉超市闽侯旗舰店")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private SalesDTO dto() {
        SalesDTO dto = new SalesDTO();
        dto.setBatchId(2L);
        dto.setMarketName("永辉超市");
        dto.setSalesDate(LocalDate.now());
        dto.setPrice(new BigDecimal("9.90"));
        dto.setQuantity(new BigDecimal("10"));
        return dto;
    }

    @Test
    void abnormalBatchShouldBeBlocked() {
        BizBatch batch = new BizBatch();
        batch.setId(2L);
        batch.setBatchNo("AG20260515002");
        batch.setStatus(Constants.BATCH_ABNORMAL);
        batch.setAbnormalFlag(1);
        when(batchService.getById(2L)).thenReturn(batch);

        BizException ex = assertThrows(BizException.class, () -> salesService.create(dto()));
        assertTrue(ex.getMessage().contains("熔断"), "异常批次必须被销售环节拦截");
    }

    @Test
    void unqualifiedBatchShouldBeBlockedBeforeLogistics() {
        BizBatch batch = new BizBatch();
        batch.setId(1L);
        batch.setStatus(Constants.BATCH_QUALIFIED);
        batch.setAbnormalFlag(0);
        when(batchService.getById(1L)).thenReturn(batch);

        SalesDTO dto = dto();
        dto.setBatchId(1L);
        BizException ex = assertThrows(BizException.class, () -> salesService.create(dto));
        assertTrue(ex.getMessage().contains("物流"), "未完成物流交接不允许销售");
    }

    @Test
    void soldOutBatchShouldBeBlocked() {
        BizBatch batch = new BizBatch();
        batch.setId(3L);
        batch.setStatus(Constants.BATCH_SOLD);
        batch.setAbnormalFlag(0);
        when(batchService.getById(3L)).thenReturn(batch);

        SalesDTO dto = dto();
        dto.setBatchId(3L);
        BizException ex = assertThrows(BizException.class, () -> salesService.create(dto));
        assertEquals(409, ex.getCode());
    }
}
