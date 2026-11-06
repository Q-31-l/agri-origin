-- =============================================================
--  增量补丁：更正说明补充权限点（2026-11-06）
--
--  适用对象：已经导入过 init.sql + data.sql 的**存量库**。
--  全新建库无需执行（init.sql + data.sql 已是修复后状态）。
--
--  背景：POST /correction 原先只有 @RequireRoles 角色门禁，没有权限点，
--  意味着一经授予角色就永久拥有"提交更正说明"的能力，管理员无法在权限
--  管理页单独收回；而其余写接口全部是"角色 + 权限点"双约束，此处属例外。
--
--  本次改动：
--   1) 新增权限点 correction:create；
--   2) 授予 ADMIN / REGULATOR / PRODUCER / VILLAGE_ADMIN（与代码里的
--      @RequireRoles 白名单一致，行为不变，只是变得可授权、可收回）。
--
--  ⚠ 执行顺序：**必须先执行本补丁、再替换 jar**。反过来会让接口在
--    补丁执行前返回 403（缺 correction:create）。
--  执行后需重启后端（PermissionCache 启动时加载角色权限）。
-- =============================================================
SET NAMES utf8mb4;
USE `agri_trace`;

-- 1) 补齐权限点
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `module`, `path`, `method`)
SELECT 'correction:create', '提交更正说明', 'BUTTON', 'correction', '/correction', 'POST'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code` = 'correction:create');

-- 2) 授予与代码 @RequireRoles 一致的角色
INSERT IGNORE INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('ADMIN', 'correction:create'),
('REGULATOR', 'correction:create'),
('PRODUCER', 'correction:create'),
('VILLAGE_ADMIN', 'correction:create');

-- 3) 校验输出：两个计数都应为 0，权限点总数应为 42
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
WHERE p.`id` IS NULL
UNION ALL
SELECT 'correction:create 授权数', COUNT(*) FROM `sys_role_permission`
WHERE `perm_code` = 'correction:create';
