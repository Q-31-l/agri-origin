-- =============================================================
--  增量补丁：乐观锁版本号
--  适用：已按旧版 init.sql 建库、不希望重建数据的场景
--  用法：mysql -uroot -proot agri_trace < sql/patch-optimistic-lock.sql
--  说明：init.sql 已包含同样字段，全新建库无需执行本文件
--
--  为什么是这三张表：
--    biz_batch              批次状态流转（核验 / 采收 / 质检 / 物流 / 销售 / 结案）是并发热点
--    biz_abnormal_disposal  处置登记，同一异常可能被两个人同时点"提交"
--    base_farm              档案编辑，PC 端多人同时改同一地块
--  其余表只追加、不覆盖，不存在丢失更新问题，不引入版本号以免无谓的 UPDATE 失败。
-- =============================================================
SET NAMES utf8mb4;
USE `agri_trace`;

DROP PROCEDURE IF EXISTS `patch_optimistic_lock`;
DELIMITER $$
CREATE PROCEDURE `patch_optimistic_lock`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'agri_trace' AND TABLE_NAME = 'biz_batch'
                   AND COLUMN_NAME = 'version') THEN
    ALTER TABLE `biz_batch`
      ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `update_time`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'agri_trace' AND TABLE_NAME = 'biz_abnormal_disposal'
                   AND COLUMN_NAME = 'version') THEN
    ALTER TABLE `biz_abnormal_disposal`
      ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `create_time`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'agri_trace' AND TABLE_NAME = 'base_farm'
                   AND COLUMN_NAME = 'version') THEN
    ALTER TABLE `base_farm`
      ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `status`;
  END IF;
END$$
DELIMITER ;

CALL `patch_optimistic_lock`();
DROP PROCEDURE IF EXISTS `patch_optimistic_lock`;
