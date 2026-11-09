-- ============================================================================
-- 历史数据清理：越级提交 / 结案遗留的待审记录
--
-- 旧版不校验"前置环节是否已审核通过"，早期演示数据里存在：
--   · 批次还在种植/已采收，却已经有运单、销售记录在等监管复核；
--   · 质检结论不合格、批次已结案，报告却永远挂在待审。
-- 这些记录既不可能被正常审核，也会让待办列表和进度条显示互相矛盾的信息。
--
-- 处理原则：
--   · 前置环节未通过的记录标记为 REJECTED（未实际流转，理由留痕），不删除、不改业务数据；
--   · 结案批次的不合格报告标记为 APPROVED（监管已确认不合格结论，批次已处置完毕）；
--   · 正常等待复核的记录（已采收等质检复核、运输中等待销售复核）一律不动。
--
-- 幂等：可重复执行。
-- ============================================================================

USE agri_trace;

-- 1. 质检：批次尚未采收通过，却提交了质检报告 —— 越级，标记无效
UPDATE biz_quality_report q
JOIN biz_batch b ON b.id = q.batch_id
SET q.audit_status = 'REJECTED',
    q.audit_by = 'system',
    q.audit_time = NOW(),
    q.audit_remark = '历史数据清理：批次当时尚未通过采收审核，该报告不在有效流程内'
WHERE q.audit_status = 'PENDING'
  AND b.status IN ('PLANTING');

-- 2. 质检：批次已因不合格结案，报告结论确认为不合格 —— 补记复核结论
UPDATE biz_quality_report q
JOIN biz_batch b ON b.id = q.batch_id
SET q.audit_status = 'APPROVED',
    q.audit_by = 'system',
    q.audit_time = NOW(),
    q.audit_remark = '历史数据补录：不合格结论已确认，批次已完成异常处置并结案'
WHERE q.audit_status = 'PENDING'
  AND q.inspect_result = 'FAIL'
  AND b.status = 'CLOSED';

-- 3. 物流：批次尚未通过质检复核就登记了运单 —— 越级，标记无效
UPDATE biz_logistics_record l
JOIN biz_batch b ON b.id = l.batch_id
SET l.audit_status = 'REJECTED',
    l.audit_by = 'system',
    l.audit_time = NOW(),
    l.audit_remark = '历史数据清理：批次当时尚未通过质检复核，该运单未实际流转'
WHERE l.audit_status = 'PENDING'
  AND b.status IN ('PLANTING', 'HARVESTED');

-- 4. 销售：批次尚未完成物流复核就登记了销售 —— 越级，标记无效
UPDATE biz_sales_record s
JOIN biz_batch b ON b.id = s.batch_id
SET s.audit_status = 'REJECTED',
    s.audit_by = 'system',
    s.audit_time = NOW(),
    s.audit_remark = '历史数据清理：批次当时尚未完成物流复核，该销售记录未实际成交'
WHERE s.audit_status = 'PENDING'
  AND b.status IN ('PLANTING', 'HARVESTED', 'QUALIFIED');

-- 核对：剩余待审记录应当每一环都"卡在对的地方"（批次状态 = 该环节的前置状态）
SELECT b.status AS batch_status, 'quality' AS stage, COUNT(*) AS cnt
FROM biz_quality_report q JOIN biz_batch b ON b.id = q.batch_id
WHERE q.audit_status = 'PENDING' GROUP BY b.status
UNION ALL
SELECT b.status, 'logistics', COUNT(*)
FROM biz_logistics_record l JOIN biz_batch b ON b.id = l.batch_id
WHERE l.audit_status = 'PENDING' GROUP BY b.status
UNION ALL
SELECT b.status, 'sales', COUNT(*)
FROM biz_sales_record s JOIN biz_batch b ON b.id = s.batch_id
WHERE s.audit_status = 'PENDING' GROUP BY b.status;
