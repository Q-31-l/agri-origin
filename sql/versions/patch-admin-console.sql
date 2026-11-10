-- ---------------------------------------------------------------------------
-- 管理员控制台：账号启停/删除 与 违规记录撤销
--
-- 背景：系统管理员需要在不动业务链路的前提下具备两类"兜底"能力：
--   1) 账号治理：查看全部账号、启停、删除建错的账号
--   2) 内容治理：撤销上传的不合规实证（如人像照、与批次无关的照片）
--      以及被误审通过的记录，且不受"谁审过"的限制
--
-- 设计要点：撤销是「链下标记」，不物理删除任何一行。
--   链上存证按设计不可删除，撤销只表达"该条不再作为有效证据展示与计分"。
--   一致性校验会跳过已撤销记录，且证据摘要仍按原始文件集合重算，
--   因此撤销动作本身不会让历史存证显示为"被篡改"。
--
-- 全部语句幂等，可重复执行。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------- 1. 字段
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'revoked') = 0,
  'ALTER TABLE `sys_file`
     ADD COLUMN `revoked` TINYINT NOT NULL DEFAULT 0 COMMENT ''1 已被管理员撤销'' AFTER `upload_by`,
     ADD COLUMN `revoked_by` VARCHAR(50) DEFAULT NULL COMMENT ''撤销人姓名'' AFTER `revoked`,
     ADD COLUMN `revoked_time` DATETIME DEFAULT NULL COMMENT ''撤销时间'' AFTER `revoked_by`,
     ADD COLUMN `revoke_reason` VARCHAR(255) DEFAULT NULL COMMENT ''撤销理由'' AFTER `revoked_time`',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_record' AND COLUMN_NAME = 'revoked') = 0,
  'ALTER TABLE `chain_record`
     ADD COLUMN `revoked` TINYINT NOT NULL DEFAULT 0 COMMENT ''1 对应业务记录已被撤销'' AFTER `status`,
     ADD COLUMN `revoke_reason` VARCHAR(255) DEFAULT NULL COMMENT ''撤销理由'' AFTER `revoked`',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 撤销后按批次号查"有效存证"要快，避免每次全表过滤
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_record' AND INDEX_NAME = 'idx_batch_revoked') = 0,
  'ALTER TABLE `chain_record` ADD KEY `idx_batch_revoked` (`batch_no`, `revoked`)',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- 2. 权限项
-- user:disable  账号启停（管理员；村级管理员仅限本村农户，沿用既有村域校验）
-- user:delete   账号删除（仅管理员）
-- record:revoke 撤销业务记录（仅管理员）
-- file:revoke   撤销实证文件（仅管理员）
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `module`, `path`, `method`)
SELECT 'user:disable', '账号启用/禁用', 'BUTTON', 'system', '/user/{id}/status', 'PUT'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code` = 'user:disable');

INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `module`, `path`, `method`)
SELECT 'user:delete', '账号删除', 'BUTTON', 'system', '/user/{id}', 'DELETE'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code` = 'user:delete');

INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `module`, `path`, `method`)
SELECT 'record:revoke', '撤销业务记录', 'BUTTON', 'system', '/admin/revoke', 'POST'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code` = 'record:revoke');

INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `module`, `path`, `method`)
SELECT 'file:revoke', '撤销实证文件', 'BUTTON', 'system', '/admin/file/{id}/revoke', 'POST'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code` = 'file:revoke');

-- ---------------------------------------------------------------- 3. 授权
-- 管理员始终持有全部权限：新增权限项后补齐差量，避免"接口能用、菜单不显示"
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`)
SELECT 'ADMIN', p.`perm_code` FROM `sys_permission` p
 WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp
                    WHERE rp.`role_code` = 'ADMIN' AND rp.`perm_code` = p.`perm_code`);

-- 村级管理员可启停本村农户账号：与其已有的 user:update 权限配套，
-- 但删除、撤销记录属于跨村兜底权力，不下放
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`)
SELECT 'VILLAGE_ADMIN', 'user:disable'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission`
                    WHERE `role_code` = 'VILLAGE_ADMIN' AND `perm_code` = 'user:disable');

-- ---------------------------------------------------------------- 4. 自检
SELECT (SELECT COUNT(*) FROM `sys_permission`) AS 权限总数,
       (SELECT COUNT(*) FROM `sys_role_permission` WHERE `role_code` = 'ADMIN') AS 管理员授权数,
       (SELECT COUNT(*) FROM `sys_permission` p
         WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp
                            WHERE rp.`role_code` = 'ADMIN' AND rp.`perm_code` = p.`perm_code`)) AS 管理员缺权限;
