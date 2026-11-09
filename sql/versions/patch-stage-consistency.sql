-- ============================================================================
-- 修复"外层状态已推进、内层记录仍待审核"的历史数据
--
-- 背景：旧版业务 Service 在"提交"时就推进了 biz_batch.status（采收提交即 HARVESTED、
-- 质检上传即 QUALIFIED、运单登记即 IN_TRANSIT、销售登记即 ON_SALE），但对应业务记录
-- 仍是 audit_status=PENDING。于是批次详情外层显示"已采收/检测合格"，而审核进度里
-- 还在"待审核"，两套口径互相打架。
--
-- 新版代码已改为"审核通过才推进状态"。本脚本把历史数据按同一口径重算一遍：
--   已通过的销售记录      -> ON_SALE / SOLD
--   已通过的物流记录      -> IN_TRANSIT
--   已通过的合格质检报告  -> QUALIFIED
--   已通过的采收记录      -> HARVESTED
--   其余                  -> PLANTING
-- 异常管控(ABNORMAL)与已结案(CLOSED)不属于流程推进，原样保留。
--
-- 幂等：可重复执行，结果一致。
-- ============================================================================

USE agri_trace;

DROP TEMPORARY TABLE IF EXISTS tmp_batch_stage;
CREATE TEMPORARY TABLE tmp_batch_stage AS
SELECT
    b.id,
    CASE
        WHEN EXISTS (SELECT 1 FROM biz_sales_record s
                      WHERE s.batch_id = b.id AND s.audit_status = 'APPROVED')
             AND b.quantity IS NOT NULL AND b.quantity > 0
             AND (SELECT IFNULL(SUM(s2.quantity), 0) FROM biz_sales_record s2
                   WHERE s2.batch_id = b.id AND s2.audit_status <> 'REJECTED') >= b.quantity
            THEN 'SOLD'
        WHEN EXISTS (SELECT 1 FROM biz_sales_record s3
                      WHERE s3.batch_id = b.id AND s3.audit_status = 'APPROVED')
            THEN 'ON_SALE'
        WHEN EXISTS (SELECT 1 FROM biz_logistics_record l
                      WHERE l.batch_id = b.id AND l.audit_status = 'APPROVED')
            THEN 'IN_TRANSIT'
        WHEN EXISTS (SELECT 1 FROM biz_quality_report q
                      WHERE q.batch_id = b.id AND q.audit_status = 'APPROVED' AND q.inspect_result = 'PASS')
            THEN 'QUALIFIED'
        WHEN EXISTS (SELECT 1 FROM biz_harvest_record h
                      WHERE h.batch_id = b.id AND h.audit_status = 'APPROVED')
            THEN 'HARVESTED'
        ELSE 'PLANTING'
    END AS new_status
FROM biz_batch b
WHERE b.status NOT IN ('ABNORMAL', 'CLOSED');

-- 采收日期只在采收通过审核后才有意义：驳回或未审核的批次不应带着采收日期
UPDATE biz_batch b
JOIN tmp_batch_stage t ON t.id = b.id
SET b.harvest_date = NULL
WHERE t.new_status = 'PLANTING';

UPDATE biz_batch b
JOIN tmp_batch_stage t ON t.id = b.id
SET b.status = t.new_status
WHERE b.status <> t.new_status;

DROP TEMPORARY TABLE IF EXISTS tmp_batch_stage;

-- 核对：重算后仍处于非终态但存在待审核记录的批次（应当只剩"当前卡点"这一条待审）
SELECT b.batch_no, b.status,
       (SELECT COUNT(*) FROM biz_harvest_record h
         WHERE h.batch_id = b.id AND h.audit_status = 'PENDING') AS harvest_pending,
       (SELECT COUNT(*) FROM biz_quality_report q
         WHERE q.batch_id = b.id AND q.audit_status = 'PENDING') AS quality_pending,
       (SELECT COUNT(*) FROM biz_logistics_record l
         WHERE l.batch_id = b.id AND l.audit_status = 'PENDING') AS logistics_pending,
       (SELECT COUNT(*) FROM biz_sales_record s
         WHERE s.batch_id = b.id AND s.audit_status = 'PENDING') AS sales_pending
FROM biz_batch b
WHERE b.status NOT IN ('ABNORMAL', 'CLOSED')
HAVING (harvest_pending + quality_pending + logistics_pending + sales_pending) > 0;
