package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.DigestHelper;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.AuditDTO;
import com.agri.trace.dto.SalesDTO;
import com.agri.trace.entity.BizBatch;
import com.agri.trace.entity.BizSalesRecord;
import com.agri.trace.mapper.BizBatchMapper;
import com.agri.trace.mapper.BizSalesRecordMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.ChainResult;
import com.agri.trace.sdk.TraceContractService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * 终端销售
 */
@Service
public class SalesService {

    @Resource
    private BizSalesRecordMapper salesMapper;
    @Resource
    private BizBatchMapper batchMapper;
    @Resource
    private BatchService batchService;
    @Resource
    private ChainService chainService;
    @Resource
    private TraceContractService contractService;
    @Resource
    private FileService fileService;

    public PageResult<BizSalesRecord> page(long page, long size, Long batchId, String batchNo) {
        LambdaQueryWrapper<BizSalesRecord> wrapper = Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(batchId != null, BizSalesRecord::getBatchId, batchId)
                .like(batchNo != null && !batchNo.isEmpty(), BizSalesRecord::getBatchNo, batchNo)
                .orderByDesc(BizSalesRecord::getId);
        if (Constants.ROLE_SELLER.equals(UserContext.roleCode())) {
            wrapper.eq(BizSalesRecord::getSellerId, UserContext.userId());
        } else {
            // 非销售角色按批次数据范围收窄：生产主体只能看到自己建档批次的销售记录
            List<Long> visible = batchService.visibleBatchIds(UserContext.require());
            if (visible != null) {
                if (visible.isEmpty()) {
                    return PageResult.of(java.util.Collections.emptyList(), 0, page, size);
                }
                wrapper.in(BizSalesRecord::getBatchId, visible);
            }
        }
        return PageResult.of(salesMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Transactional
    public Long create(SalesDTO dto) {
        LoginUser user = UserContext.require();
        if (!Constants.ROLE_SELLER.equals(user.getRoleCode())
                && !Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            throw BizException.forbidden("仅销售商可登记销售信息");
        }
        BizBatch batch = batchService.getById(dto.getBatchId());

        // 异常批次熔断：核心验收点
        if (Constants.BATCH_ABNORMAL.equals(batch.getStatus())
                || Integer.valueOf(1).equals(batch.getAbnormalFlag())) {
            throw new BizException(com.agri.trace.common.ResultCode.CONFLICT,
                    "该批次质检不合格已被熔断，禁止进入销售环节");
        }
        if (Constants.BATCH_CLOSED.equals(batch.getStatus())) {
            throw BizException.status("该批次已结案，不可销售");
        }
        if (Constants.BATCH_SOLD.equals(batch.getStatus())) {
            throw BizException.conflict("该批次已售罄");
        }
        if (!Constants.BATCH_IN_TRANSIT.equals(batch.getStatus())
                && !Constants.BATCH_ON_SALE.equals(batch.getStatus())) {
            throw BizException.status("仅已完成物流交接的批次可销售");
        }

        Long duplicate = salesMapper.selectCount(Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(BizSalesRecord::getBatchId, dto.getBatchId())
                .eq(BizSalesRecord::getSellerId, user.getUserId())
                .eq(BizSalesRecord::getSalesDate, dto.getSalesDate()));
        if (duplicate != null && duplicate > 0) {
            throw BizException.conflict("该批次当日已有销售记录，请勿重复提交");
        }

        List<BizSalesRecord> sold = salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                .eq(BizSalesRecord::getBatchId, batch.getId()));
        BigDecimal soldQty = sold.stream().map(BizSalesRecord::getQuantity)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = soldQty.add(dto.getQuantity());
        if (batch.getQuantity() != null && total.compareTo(batch.getQuantity()) > 0) {
            throw BizException.conflict("销售数量超出批次库存，剩余 " + batch.getQuantity().subtract(soldQty) + batch.getUnit());
        }

        BizSalesRecord record = new BizSalesRecord();
        record.setBatchId(batch.getId());
        record.setBatchNo(batch.getBatchNo());
        record.setSellerId(user.getUserId());
        record.setSellerName(user.getOrgName());
        record.setMarketName(dto.getMarketName());
        record.setSalesDate(dto.getSalesDate());
        record.setPrice(dto.getPrice());
        record.setQuantity(dto.getQuantity());
        record.setUnit(dto.getUnit() == null ? batch.getUnit() : dto.getUnit());
        record.setBuyerName(dto.getBuyerName());
        record.setBuyerPhone(dto.getBuyerPhone());
        record.setAuditStatus(Constants.AUDIT_PENDING);
        record.setChainStatus(Constants.CHAIN_NONE);
        salesMapper.insert(record);


        // 实证绑定：图片/视频文件哈希聚合为证据根哈希，随业务摘要一并上链
        java.util.List<com.agri.trace.vo.EvidenceVO> evidences = fileService.bindEvidence(
                dto.getEvidenceIds(), Constants.EVENT_SALES, record.getId());
        String evidenceHash = fileService.evidenceHash(evidences);

        String dataHash = DigestHelper.sales(record, evidenceHash);
        record.setDataHash(dataHash);
        // 合约写方法带 batchExists 校验：演示/历史批次可能从未上链，先补注册再写记录
        batchService.ensureChainRegistered(batch, user.getRealName());
        ChainResult chain = contractService.addRecord(batch.getBatchNo(), Constants.EVENT_SALES,
                String.valueOf(record.getId()), dataHash, evidenceHash, user.getRealName());
        record.setChainStatus(chain.getStatus());
        record.setChainTxHash(chain.getTxHash());
        record.setBlockNumber(chain.getBlockNumber());
        salesMapper.updateById(record);
        chainService.saveRecord(batch.getBatchNo(), Constants.EVENT_SALES, record.getId(),
                "SALES_RECORD", dataHash, evidenceHash, chain);

        boolean soldOut = batch.getQuantity() != null && total.compareTo(batch.getQuantity()) >= 0;
        batch.setStatus(soldOut ? Constants.BATCH_SOLD : Constants.BATCH_ON_SALE);
        Db.updated(batchMapper.updateById(batch));
        return record.getId();
    }

    public BigDecimal soldQuantity(Long batchId) {
        return salesMapper.selectList(Wrappers.<BizSalesRecord>lambdaQuery()
                        .eq(BizSalesRecord::getBatchId, batchId))
                .stream().map(BizSalesRecord::getQuantity)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    @Transactional
    public void audit(Long id, AuditDTO dto) {
        BizSalesRecord record = salesMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("销售记录不存在");
        }
        if (!Constants.AUDIT_PENDING.equals(record.getAuditStatus())) {
            throw BizException.conflict("该记录已审核，请勿重复操作");
        }
        record.setAuditStatus(dto.getAuditStatus());
        record.setAuditRemark(dto.getAuditRemark());
        record.setAuditBy(UserContext.require().getRealName());
        record.setAuditTime(java.time.LocalDateTime.now());
        salesMapper.updateById(record);
    }
}
