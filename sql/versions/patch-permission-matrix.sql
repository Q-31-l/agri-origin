-- =============================================================
--  增量补丁：权限矩阵自洽性修复（2026-11-05）
--
--  适用对象：已经导入过 init.sql + data.sql 的**存量库**。
--  全新建库无需执行（init.sql + data.sql 已是修复后状态）。
--
--  本次修复的 5 个权限缺陷：
--   1) 代码里使用了 chain:repair 权限点，但 sys_permission 里根本不存在，
--      导致该接口只有 ADMIN 靠"管理员豁免"能调用，无法授权给其他角色，
--      权限管理页也看不到这一项。
--   2) 移动端农户工作台直接调用 /stats/overview，但 FARMER 未被授予
--      stats:overview，接口 403，首页"我的批次 / 异常 / 合格率"三张卡片全空。
--   3) VILLAGE_ADMIN 持有 quality:audit / logistics:audit / sales:audit，
--      却没有对应的 *:list，前端菜单被裁掉、列表 403，审核入口永远不可达；
--      且质检/物流/销售的复核本属监管职责，村级不应持有。
--   4) trace:query 权限点在 sys_permission 中定义、并授予了 7 个角色，
--      但 /trace/** 是免登录公开接口，代码从未校验该权限——属于"空转权限"，
--      会让人误以为溯源查询受权限保护。
--   5) PRODUCER 有 abnormal:list 却无 abnormal:trace，能看异常列表但点不开
--      上下游追溯；补授后服务端已加批次数据范围校验，不会越权看到他人批次。
--
--  执行后需重启后端（PermissionCache 在启动时加载角色权限）。
-- =============================================================
SET NAMES utf8mb4;
USE `agri_trace`;

-- 1) 补齐缺失的权限点
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `module`, `path`, `method`)
SELECT 'chain:repair', '存量批次补注册', 'BUTTON', 'chain', '/chain/repair/batches', 'POST'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code` = 'chain:repair');

-- 2) 授予 ADMIN / REGULATOR（ADMIN 在新建库中靠 SELECT 全量授权，存量库需显式补）
INSERT IGNORE INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('ADMIN', 'chain:repair'),
('REGULATOR', 'chain:repair');

-- 3) 农户补数据看板权限（服务端已按 farmer_id 收窄，不会看到他人数据）
INSERT IGNORE INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('FARMER', 'stats:overview');

-- 4) 生产主体补上下游追溯（服务端已加批次范围校验）
INSERT IGNORE INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('PRODUCER', 'abnormal:trace');

-- 5) 物流 / 销售补产品品类只读（登记时需选产品）
INSERT IGNORE INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('LOGISTICS', 'product:list'),
('SELLER', 'product:list');

-- 6) 收回村级管理员的非本职责审核权（悬空授权）
DELETE FROM `sys_role_permission`
WHERE `role_code` = 'VILLAGE_ADMIN'
  AND `perm_code` IN ('quality:audit', 'logistics:audit', 'sales:audit');

-- 7) 清理空转权限点 trace:query（公开接口，代码从不校验）
DELETE FROM `sys_role_permission` WHERE `perm_code` = 'trace:query';
DELETE FROM `sys_permission`      WHERE `perm_code` = 'trace:query';

-- 8) 校验输出
SELECT '权限点总数' AS item, COUNT(*) AS value FROM `sys_permission`
UNION ALL
SELECT '角色授权总数', COUNT(*) FROM `sys_role_permission`
UNION ALL
SELECT '悬空授权(有权限点无角色)', COUNT(*)
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp ON rp.`perm_code` = p.`perm_code`
WHERE rp.`id` IS NULL
UNION ALL
SELECT '孤儿授权(有角色无权限点)', COUNT(*)
FROM `sys_role_permission` rp
LEFT JOIN `sys_permission` p ON p.`perm_code` = rp.`perm_code`
WHERE p.`id` IS NULL;
