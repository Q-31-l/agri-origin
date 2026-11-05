package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.dto.PlantingDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizPlantingRecord;
import com.agri.trace.mapper.BizPlantingRecordMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.ChainResult;
import com.agri.trace.sdk.TraceContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 农事记录：归属校验与重复提交校验
 */
@ExtendWith(MockitoExtension.class)
class PlantingServiceTest {

    @Mock
    private BizPlantingRecordMapper plantingMapper;
    @Mock
    private BatchService batchService;
    @Mock
    private ChainService chainService;
    @Mock
    private TraceContractService contractService;
    @Mock
    private FileService fileService;

    @InjectMocks
    private PlantingService plantingService;

    private BizBatch batch;

    @BeforeEach
    void setUp() {
        UserContext.set(LoginUser.builder()
                .userId(5L).username("farmer01").realName("张大山")
                .roleCode(Constants.ROLE_FARMER).villageId(1L).farmId(1L)
                .build());
        batch = new BizBatch();
        batch.setId(1L);
        batch.setBatchNo("AG20260512001");
        batch.setFarmerId(5L);
        batch.setStatus(Constants.BATCH_PLANTING);
        batch.setAuditStatus(Constants.AUDIT_APPROVED);
        batch.setAbnormalFlag(0);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private PlantingDTO dto() {
        PlantingDTO dto = new PlantingDTO();
        dto.setBatchId(1L);
        dto.setRecordType("FERTILIZE");
        dto.setOperationDate(LocalDate.now().minusDays(1));
        dto.setContent("追施水溶肥 5kg/亩");
        dto.setMaterialName("平衡型水溶肥");
        dto.setDosage("5kg/亩");
        return dto;
    }

    @Test
    void shouldRejectOtherFarmerBatch() {
        batch.setFarmerId(6L);
        when(batchService.getById(1L)).thenReturn(batch);

        BizException ex = assertThrows(BizException.class, () -> plantingService.create(dto()));
        assertEquals(403, ex.getCode());
    }

    @Test
    void shouldRejectDuplicateSubmission() {
        when(batchService.getById(1L)).thenReturn(batch);
        when(plantingMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> plantingService.create(dto()));
        assertEquals(409, ex.getCode());
        assertEquals("该批次当日的同类农事记录已存在，请勿重复提交", ex.getMessage());
    }

    @Test
    void shouldRejectUnapprovedBatch() {
        batch.setAuditStatus(Constants.AUDIT_PENDING);
        when(batchService.getById(1L)).thenReturn(batch);

        BizException ex = assertThrows(BizException.class, () -> plantingService.create(dto()));
        assertEquals(4001, ex.getCode());
    }

    @Test
    void shouldSaveAndMarkSkippedWhenChainDisabled() {
        when(batchService.getById(1L)).thenReturn(batch);
        when(plantingMapper.selectCount(any())).thenReturn(0L);
        // 无实证材料时证据根哈希为空串，摘要退化为纯业务字段
        when(fileService.bindEvidence(any(), anyString(), any())).thenReturn(java.util.Collections.emptyList());
        when(fileService.evidenceHash(any())).thenReturn("");
        doAnswer(invocation -> {
            BizPlantingRecord r = invocation.getArgument(0);
            r.setId(100L);
            return 1;
        }).when(plantingMapper).insert(any(BizPlantingRecord.class));
        when(contractService.addRecord(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(ChainResult.skipped("演示模式"));

        Long id = plantingService.create(dto());

        assertNotNull(id);
        assertEquals(100L, id);
    }
}
