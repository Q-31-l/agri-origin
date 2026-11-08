-- ---------------------------------------------------------------------------
-- 实名认证 + 注册审核 + 系统管理员切换
--
-- 1. sys_user 增加身份证号（实名认证）、注册审核状态字段。
--    农户/生产/检测/物流/销售自助注册 → audit_status=NULL（无需审核，直接启用）；
--    村级管理员/监管人员自助注册 → audit_status=PENDING（待系统管理员审核），
--    status=0（未激活，不能登录），审核通过后才 status=1。
-- 2. 去除模拟系统管理员 admin，改由邱连春(qiulian)担任唯一系统管理员。
-- ---------------------------------------------------------------------------

ALTER TABLE `sys_user`
  ADD COLUMN `id_card` VARCHAR(18) DEFAULT NULL COMMENT '身份证号（实名认证）' AFTER `phone`,
  ADD COLUMN `audit_status` VARCHAR(16) DEFAULT NULL COMMENT '注册审核状态：PENDING/APPROVED/REJECTED，NULL=无需审核' AFTER `status`;

-- 停用模拟系统管理员 admin（保留记录，不能登录）
UPDATE `sys_user` SET `status` = 0 WHERE `username` = 'admin';
