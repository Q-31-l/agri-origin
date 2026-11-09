-- ============================================================================
-- 历史数据补录：清理"流程已走完、审核记录却还挂着待审"的旧数据
--
-- 旧版在提交时就推进批次状态，导致一批演示/历史批次的业务记录从未被点过"通过"，
-- 但批次早已进入后续环节（甚至已销售）。这些残留的 PENDING 会让待办列表永远清不掉，
-- 也会让审核进度显示"采收待审核"而外层显示"销售中"。
--
-- 处理原则：
--   1. 只有当批次状态确实已经越过该环节时，才补记该环节的审核结果；
--   2. 补记留痕（audit_by=system + 备注），不冒充具体审核人；
--   3. 质检只补记结论为合格(PASS)的报告；不合格报告必须留给监管处置，不自动通过；
--   4. 业务摘要(data_hash)不含审核字段，补记审核状态不影响一致性校验。
--
-- 幂等：可重复执行。
-- ============================================================================

USE agri_trace;

-- 采收：批次已采收/已质检/运输中/在售/售罄
UPDATE biz_harvest_record h
JOIN biz_batch b ON b.id = h.batch_id
SET h.audit_status = 'APPROVED',
    h.audit_by = 'system',
    h.audit_time = NOW(),
    h.audit_remark = '历史数据补录：批次已完成采收流转，审核结果据实补记'
WHERE h.audit_status = 'PENDING'
  AND b.status IN ('HARVESTED', 'QUALIFIED', 'IN_TRANSIT', 'ON_SALE', 'SOLD');

-- 质检：批次已质检合格/运输中/在售/售罄，且报告结论为合格
UPDATE biz_quality_report q
JOIN biz_batch b ON b.id = q.batch_id
SET q.audit_status = 'APPROVED',
    q.audit_by = 'system',
    q.audit_time = NOW(),
    q.audit_remark = '历史数据补录：批次已完成质检流转，审核结果据实补记'
WHERE q.audit_status = 'PENDING'
  AND q.inspect_result = 'PASS'
  AND b.status IN ('QUALIFIED', 'IN_TRANSIT', 'ON_SALE', 'SOLD');

-- 物流：批次运输中/在售/售罄
UPDATE biz_logistics_record l
JOIN biz_batch b ON b.id = l.batch_id
SET l.audit_status = 'APPROVED',
    l.audit_by = 'system',
    l.audit_time = NOW(),
    l.audit_remark = '历史数据补录：批次已完成物流流转，审核结果据实补记'
WHERE l.audit_status = 'PENDING'
  AND b.status IN ('IN_TRANSIT', 'ON_SALE', 'SOLD');

-- 销售：批次在售/售罄
UPDATE biz_sales_record s
JOIN biz_batch b ON b.id = s.batch_id
SET s.audit_status = 'APPROVED',
    s.audit_by = 'system',
    s.audit_time = NOW(),
    s.audit_remark = '历史数据补录：批次已完成销售流转，审核结果据实补记'
WHERE s.audit_status = 'PENDING'
  AND b.status IN ('ON_SALE', 'SOLD');

-- 核对：订正后仍存在的待审记录，应当只剩各批次真正的"当前卡点"
SELECT 'harvest' AS stage, h.batch_no, h.audit_status, COUNT(*) AS cnt
FROM biz_harvest_record h WHERE h.audit_status = 'PENDING' GROUP BY h.batch_no
UNION ALL
SELECT 'quality', q.batch_no, q.audit_status, COUNT(*)
FROM biz_quality_report q WHERE q.audit_status = 'PENDING' GROUP BY q.batch_no
UNION ALL
SELECT 'logistics', l.batch_no, l.audit_status, COUNT(*)
FROM biz_logistics_record l WHERE l.audit_status = 'PENDING' GROUP BY l.batch_no
UNION ALL
SELECT 'sales', s.batch_no, s.audit_status, COUNT(*)
FROM biz_sales_record s WHERE s.audit_status = 'PENDING' GROUP BY s.batch_no;
