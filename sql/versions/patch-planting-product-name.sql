-- ---------------------------------------------------------------------------
-- 农事记录补「手写作物名」字段，支撑"农户录农事→村管审核→自动建档批次"
--
-- 背景：原来农事记录必须挂在已有批次上（batch_id 必填），而批次又要等村级
-- 核验才有，形成"必填却要等审核"的矛盾。现在允许农户未指派批次时直接手写
-- 作物名（草莓/玉米/高粱…）提交，村管审核通过后据此自动建档批次。
-- ---------------------------------------------------------------------------

ALTER TABLE `biz_planting_record`
  ADD COLUMN `product_name` VARCHAR(64) DEFAULT NULL COMMENT '手写作物名（未指派批次时，审核通过据此建档批次）'
  AFTER `batch_no`;

-- 未指派批次时 batch_id/batch_no 为空，必须允许 NULL
ALTER TABLE `biz_planting_record`
  MODIFY COLUMN `batch_id` BIGINT DEFAULT NULL COMMENT '所属批次（可空）',
  MODIFY COLUMN `batch_no` VARCHAR(64) DEFAULT NULL COMMENT '批次号（可空）';
