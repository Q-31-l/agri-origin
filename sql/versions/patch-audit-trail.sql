-- ---------------------------------------------------------------------------
-- 审核留痕：各业务环节补齐「审核人 / 审核时间」
--
-- 背景：批次（biz_batch）已有 audit_by / audit_time，但农事、采收、质检、物流、
-- 销售五类记录只有 audit_status / audit_remark。审核完成后无法回答
-- 「谁在什么时候审的」——农户端只能看到"已通过"，看不到审核人和时间，
-- 上级复核也无从追溯。补两列与批次保持同一口径。
-- ---------------------------------------------------------------------------

ALTER TABLE `biz_planting_record`
  ADD COLUMN `audit_by` VARCHAR(50) DEFAULT NULL COMMENT '审核人姓名' AFTER `audit_remark`;

ALTER TABLE `biz_harvest_record`
  ADD COLUMN `audit_by` VARCHAR(50) DEFAULT NULL COMMENT '审核人姓名' AFTER `audit_remark`,
  ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间' AFTER `audit_by`;

ALTER TABLE `biz_quality_report`
  ADD COLUMN `audit_by` VARCHAR(50) DEFAULT NULL COMMENT '复核人姓名' AFTER `audit_remark`,
  ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT '复核时间' AFTER `audit_by`;

ALTER TABLE `biz_logistics_record`
  ADD COLUMN `audit_by` VARCHAR(50) DEFAULT NULL COMMENT '复核人姓名' AFTER `audit_remark`,
  ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT '复核时间' AFTER `audit_by`;

ALTER TABLE `biz_sales_record`
  ADD COLUMN `audit_by` VARCHAR(50) DEFAULT NULL COMMENT '复核人姓名' AFTER `audit_remark`,
  ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT '复核时间' AFTER `audit_by`;
