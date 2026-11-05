-- =============================================================
--  增量补丁：微信登录支持（小程序 + App 双端）
--  适用：已按旧版 init.sql 建库、不希望重建数据的场景
--  用法：mysql -uroot -proot agri_trace < sql/patch-wx-login.sql
--  说明：init.sql 已包含同样字段，全新建库无需执行本文件
--        本脚本幂等，可重复执行
-- =============================================================
SET NAMES utf8mb4;
USE `agri_trace`;

-- 1. 新增微信身份字段（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程做幂等）
DROP PROCEDURE IF EXISTS `patch_wx_login`;
DELIMITER $$
CREATE PROCEDURE `patch_wx_login`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'agri_trace' AND TABLE_NAME = 'sys_user'
                   AND COLUMN_NAME = 'wx_openid') THEN
    ALTER TABLE `sys_user`
      ADD COLUMN `wx_openid`  VARCHAR(64) DEFAULT NULL COMMENT '微信小程序 openid' AFTER `avatar`,
      ADD COLUMN `wx_unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信开放平台 unionid' AFTER `wx_openid`,
      ADD COLUMN `wx_bound_at` DATETIME   DEFAULT NULL COMMENT '微信绑定时间' AFTER `wx_unionid`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = 'agri_trace' AND TABLE_NAME = 'sys_user'
                   AND INDEX_NAME = 'uk_wx_openid') THEN
    ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_wx_openid` (`wx_openid`);
  END IF;

  -- 微信 App 端（开放平台）openid，与小程序 openid 是两个不同的值，必须分开存
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'agri_trace' AND TABLE_NAME = 'sys_user'
                   AND COLUMN_NAME = 'wx_app_openid') THEN
    ALTER TABLE `sys_user`
      ADD COLUMN `wx_app_openid`   VARCHAR(64) DEFAULT NULL COMMENT '微信 App（开放平台）openid' AFTER `wx_openid`,
      ADD COLUMN `wx_app_bound_at` DATETIME    DEFAULT NULL COMMENT '微信 App 绑定时间' AFTER `wx_bound_at`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = 'agri_trace' AND TABLE_NAME = 'sys_user'
                   AND INDEX_NAME = 'uk_wx_app_openid') THEN
    ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_wx_app_openid` (`wx_app_openid`);
  END IF;
END$$
DELIMITER ;

CALL `patch_wx_login`();
DROP PROCEDURE IF EXISTS `patch_wx_login`;
