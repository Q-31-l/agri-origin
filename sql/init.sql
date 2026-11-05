-- =============================================================
--  agri-origin 农产品质量安全溯源系统 · 数据库初始化脚本
--  DBMS : MySQL 8.0+    Charset : utf8mb4
--  说明 : 仅包含结构，测试数据见 data.sql
-- =============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `agri_trace`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `agri_trace`;

-- -------------------------------------------------------------
-- 1. 权限体系
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code`   VARCHAR(32)  NOT NULL COMMENT '角色编码',
  `role_name`   VARCHAR(64)  NOT NULL COMMENT '角色名称',
  `data_scope`  VARCHAR(16)  NOT NULL DEFAULT 'SELF' COMMENT '数据范围 SELF/VILLAGE/ORG/ALL',
  `sort_no`     INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `perm_code`   VARCHAR(64)  NOT NULL COMMENT '权限编码，如 batch:create',
  `perm_name`   VARCHAR(64)  NOT NULL COMMENT '权限名称',
  `perm_type`   VARCHAR(16)  NOT NULL DEFAULT 'API' COMMENT 'MENU/BUTTON/API',
  `module`      VARCHAR(32)  DEFAULT NULL COMMENT '所属模块',
  `path`        VARCHAR(128) DEFAULT NULL COMMENT '前端路由或接口路径',
  `method`      VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP 方法',
  `sort_no`     INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点表';

DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `role_code`  VARCHAR(32) NOT NULL,
  `perm_code`  VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_code`, `perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `username`        VARCHAR(64)  NOT NULL COMMENT '登录账号',
  `password`        VARCHAR(128) NOT NULL COMMENT 'BCrypt 密文',
  `real_name`       VARCHAR(64)  NOT NULL COMMENT '真实姓名',
  `phone`           VARCHAR(20)  DEFAULT NULL,
  `avatar`          VARCHAR(255) DEFAULT NULL,
  `wx_openid`       VARCHAR(64)  DEFAULT NULL COMMENT '微信小程序 openid',
  `wx_app_openid`   VARCHAR(64)  DEFAULT NULL COMMENT '微信 App（开放平台）openid',
  `wx_unionid`      VARCHAR(64)  DEFAULT NULL COMMENT '微信开放平台 unionid',
  `wx_bound_at`     DATETIME     DEFAULT NULL COMMENT '微信小程序绑定时间',
  `wx_app_bound_at` DATETIME     DEFAULT NULL COMMENT '微信 App 绑定时间',
  `role_code`       VARCHAR(32)  NOT NULL COMMENT '角色编码',
  `org_name`        VARCHAR(128) DEFAULT NULL COMMENT '所属机构（检测机构/物流企业/销售商）',
  `village_id`      BIGINT       DEFAULT NULL COMMENT '所属村（农户/村管）',
  `farm_id`         BIGINT       DEFAULT NULL COMMENT '所属农场（农户）',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
  `last_login_time` DATETIME     DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_wx_openid` (`wx_openid`),
  UNIQUE KEY `uk_wx_app_openid` (`wx_app_openid`),
  KEY `idx_role` (`role_code`),
  KEY `idx_village` (`village_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -------------------------------------------------------------
-- 2. 基础档案
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `base_village`;
CREATE TABLE `base_village` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `village_code`  VARCHAR(32)  NOT NULL COMMENT '行政村编码',
  `village_name`  VARCHAR(64)  NOT NULL,
  `region_code`   VARCHAR(16)  DEFAULT NULL COMMENT '行政区划代码',
  `region_name`   VARCHAR(128) DEFAULT NULL COMMENT '省市区',
  `address`       VARCHAR(255) DEFAULT NULL,
  `manager_id`    BIGINT       DEFAULT NULL COMMENT '村级管理员用户ID',
  `manager_name`  VARCHAR(64)  DEFAULT NULL,
  `remark`        VARCHAR(255) DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_village_code` (`village_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政村表';

DROP TABLE IF EXISTS `base_farm`;
CREATE TABLE `base_farm` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `farm_code`    VARCHAR(32)   NOT NULL COMMENT '地块/农场编码',
  `farm_name`    VARCHAR(64)   NOT NULL,
  `farmer_id`    BIGINT        DEFAULT NULL COMMENT '农户用户ID',
  `farmer_name`  VARCHAR(64)   DEFAULT NULL,
  `village_id`   BIGINT        NOT NULL,
  `village_name` VARCHAR(64)   DEFAULT NULL,
  `address`      VARCHAR(255)  DEFAULT NULL,
  `area`         DECIMAL(10,2) DEFAULT NULL COMMENT '面积（亩）',
  `soil_type`    VARCHAR(32)   DEFAULT NULL,
  `water_source` VARCHAR(64)   DEFAULT NULL,
  `cert_no`      VARCHAR(64)   DEFAULT NULL COMMENT '土地确权/认证编号',
  `status`       TINYINT       NOT NULL DEFAULT 1,
  `version`      INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`      TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_farm_code` (`farm_code`),
  KEY `idx_farmer` (`farmer_id`),
  KEY `idx_village` (`village_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农场/地块表';

DROP TABLE IF EXISTS `base_product`;
CREATE TABLE `base_product` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `product_code`   VARCHAR(32)  NOT NULL,
  `product_name`   VARCHAR(64)  NOT NULL,
  `category`       VARCHAR(32)  DEFAULT NULL COMMENT '品类：蔬菜/水果/粮油',
  `variety`        VARCHAR(64)  DEFAULT NULL COMMENT '品种',
  `unit`           VARCHAR(16)  DEFAULT 'kg',
  `shelf_life_days` INT         DEFAULT NULL,
  `origin_region`  VARCHAR(128) DEFAULT NULL,
  `description`    VARCHAR(500) DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农产品品类表';

-- -------------------------------------------------------------
-- 3. 批次主表（业务主线）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `biz_batch`;
CREATE TABLE `biz_batch` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `batch_no`      VARCHAR(40)   NOT NULL COMMENT '批次唯一编码',
  `trace_code`    VARCHAR(64)   NOT NULL COMMENT '溯源唯一码（二维码内容）',
  `product_id`    BIGINT        NOT NULL,
  `product_name`  VARCHAR(64)   NOT NULL,
  `variety`       VARCHAR(64)   DEFAULT NULL,
  `farm_id`       BIGINT        DEFAULT NULL,
  `farm_name`     VARCHAR(64)   DEFAULT NULL,
  `farmer_id`     BIGINT        DEFAULT NULL COMMENT '农户ID',
  `farmer_name`   VARCHAR(64)   DEFAULT NULL,
  `village_id`    BIGINT        DEFAULT NULL,
  `village_name`  VARCHAR(64)   DEFAULT NULL,
  `planting_date` DATE          DEFAULT NULL,
  `harvest_date`  DATE          DEFAULT NULL,
  `quantity`      DECIMAL(12,2) DEFAULT NULL,
  `unit`          VARCHAR(16)   DEFAULT 'kg',
  `status`        VARCHAR(24)   NOT NULL DEFAULT 'PLANTING'
                  COMMENT 'PLANTING/HARVESTED/QUALIFIED/IN_TRANSIT/ON_SALE/SOLD/ABNORMAL/CLOSED',
  `audit_status`  VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `audit_by`      VARCHAR(64)   DEFAULT NULL,
  `audit_time`    DATETIME      DEFAULT NULL,
  `audit_remark`  VARCHAR(255)  DEFAULT NULL,
  `abnormal_flag` TINYINT       NOT NULL DEFAULT 0 COMMENT '1 异常批次',
  `chain_status`  VARCHAR(16)   NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SUCCESS/FAILED',
  `chain_tx_hash` VARCHAR(80)   DEFAULT NULL,
  `block_number`  BIGINT        DEFAULT NULL,
  `remark`        VARCHAR(500)  DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version`       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted`       TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  UNIQUE KEY `uk_trace_code` (`trace_code`),
  KEY `idx_status` (`status`),
  KEY `idx_farmer` (`farmer_id`),
  KEY `idx_village` (`village_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农产品批次表';

-- -------------------------------------------------------------
-- 4. 全流程业务记录
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `biz_planting_record`;
CREATE TABLE `biz_planting_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `batch_id`      BIGINT       NOT NULL,
  `batch_no`      VARCHAR(40)  NOT NULL,
  `record_type`   VARCHAR(24)  NOT NULL COMMENT 'SOIL/SOWING/FERTILIZE/IRRIGATE/PEST/WEED/OTHER',
  `operation_date` DATE        NOT NULL,
  `content`       VARCHAR(1000) NOT NULL COMMENT '农事内容',
  `material_name` VARCHAR(128) DEFAULT NULL COMMENT '投入品名称',
  `dosage`        VARCHAR(64)  DEFAULT NULL COMMENT '用量',
  `operator_id`   BIGINT       NOT NULL,
  `operator_name` VARCHAR(64)  DEFAULT NULL,
  `images`        VARCHAR(1000) DEFAULT NULL COMMENT '现场照片，逗号分隔',
  `gps`           VARCHAR(64)  DEFAULT NULL,
  `audit_status`  VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '村管审核状态',
  `audit_remark`  VARCHAR(255) DEFAULT NULL,
  `data_hash`     VARCHAR(80)  DEFAULT NULL COMMENT '业务摘要 SHA-256',
  `chain_status`  VARCHAR(16)  NOT NULL DEFAULT 'NONE',
  `chain_tx_hash` VARCHAR(80)  DEFAULT NULL,
  `block_number`  BIGINT       DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植/农事记录表';

DROP TABLE IF EXISTS `biz_harvest_record`;
CREATE TABLE `biz_harvest_record` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `batch_id`      BIGINT        NOT NULL,
  `batch_no`      VARCHAR(40)   NOT NULL,
  `harvest_date`  DATE          NOT NULL,
  `quantity`      DECIMAL(12,2) NOT NULL,
  `unit`          VARCHAR(16)   DEFAULT 'kg',
  `quality_level` VARCHAR(16)   DEFAULT 'A' COMMENT 'A/B/C',
  `weather`       VARCHAR(32)   DEFAULT NULL,
  `operator_id`   BIGINT        NOT NULL,
  `operator_name` VARCHAR(64)   DEFAULT NULL,
  `images`        VARCHAR(1000) DEFAULT NULL,
  `remark`        VARCHAR(500)  DEFAULT NULL,
  `audit_status`  VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
  `audit_remark`  VARCHAR(255)  DEFAULT NULL,
  `data_hash`     VARCHAR(80)   DEFAULT NULL,
  `chain_status`  VARCHAR(16)   NOT NULL DEFAULT 'NONE',
  `chain_tx_hash` VARCHAR(80)   DEFAULT NULL,
  `block_number`  BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采收记录表';

DROP TABLE IF EXISTS `biz_quality_report`;
CREATE TABLE `biz_quality_report` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `batch_id`       BIGINT       NOT NULL,
  `batch_no`       VARCHAR(40)  NOT NULL,
  `report_no`      VARCHAR(64)  NOT NULL COMMENT '检测报告编号',
  `inspect_org`    VARCHAR(128) NOT NULL,
  `inspector_id`   BIGINT       NOT NULL,
  `inspector_name` VARCHAR(64)  DEFAULT NULL,
  `inspect_date`   DATE         NOT NULL,
  `inspect_result` VARCHAR(16)  NOT NULL COMMENT 'PASS/FAIL',
  `standard_no`    VARCHAR(64)  DEFAULT NULL COMMENT '执行标准',
  `items_json`     TEXT         DEFAULT NULL COMMENT '检测项目明细 JSON',
  `conclusion`     VARCHAR(500) DEFAULT NULL,
  `file_name`      VARCHAR(255) DEFAULT NULL,
  `file_path`      VARCHAR(500) DEFAULT NULL,
  `file_hash`      VARCHAR(80)  DEFAULT NULL COMMENT '报告文件 SHA-256',
  `file_size`      BIGINT       DEFAULT NULL,
  `data_hash`      VARCHAR(80)  DEFAULT NULL,
  `chain_status`   VARCHAR(16)  NOT NULL DEFAULT 'NONE',
  `chain_tx_hash`  VARCHAR(80)  DEFAULT NULL,
  `block_number`   BIGINT       DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检报告表';

DROP TABLE IF EXISTS `biz_logistics_record`;
CREATE TABLE `biz_logistics_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `batch_id`      BIGINT       NOT NULL,
  `batch_no`      VARCHAR(40)  NOT NULL,
  `logistics_no`  VARCHAR(64)  NOT NULL COMMENT '运单号',
  `from_org`      VARCHAR(128) DEFAULT NULL,
  `to_org`        VARCHAR(128) DEFAULT NULL,
  `carrier_id`    BIGINT       NOT NULL,
  `carrier_name`  VARCHAR(128) DEFAULT NULL,
  `vehicle_no`    VARCHAR(32)  DEFAULT NULL,
  `driver_name`   VARCHAR(64)  DEFAULT NULL,
  `driver_phone`  VARCHAR(20)  DEFAULT NULL,
  `depart_time`   DATETIME     DEFAULT NULL,
  `arrive_time`   DATETIME     DEFAULT NULL,
  `temperature`   VARCHAR(32)  DEFAULT NULL COMMENT '冷链温度区间',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DEPARTED' COMMENT 'DEPARTED/ARRIVED',
  `handover_file` VARCHAR(500) DEFAULT NULL COMMENT '交接单照片',
  `file_hash`     VARCHAR(80)  DEFAULT NULL,
  `remark`        VARCHAR(500) DEFAULT NULL,
  `data_hash`     VARCHAR(80)  DEFAULT NULL,
  `chain_status`  VARCHAR(16)  NOT NULL DEFAULT 'NONE',
  `chain_tx_hash` VARCHAR(80)  DEFAULT NULL,
  `block_number`  BIGINT       DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_logistics_no` (`logistics_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流交接记录表';

DROP TABLE IF EXISTS `biz_sales_record`;
CREATE TABLE `biz_sales_record` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `batch_id`      BIGINT        NOT NULL,
  `batch_no`      VARCHAR(40)   NOT NULL,
  `seller_id`     BIGINT        NOT NULL,
  `seller_name`   VARCHAR(128)  DEFAULT NULL,
  `market_name`   VARCHAR(128)  DEFAULT NULL COMMENT '销售市场/门店',
  `sales_date`    DATE          NOT NULL,
  `price`         DECIMAL(10,2) DEFAULT NULL,
  `quantity`      DECIMAL(12,2) DEFAULT NULL,
  `unit`          VARCHAR(16)   DEFAULT 'kg',
  `buyer_name`    VARCHAR(64)   DEFAULT NULL,
  `buyer_phone`   VARCHAR(20)   DEFAULT NULL,
  `data_hash`     VARCHAR(80)   DEFAULT NULL,
  `chain_status`  VARCHAR(16)   NOT NULL DEFAULT 'NONE',
  `chain_tx_hash` VARCHAR(80)   DEFAULT NULL,
  `block_number`  BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售记录表';

-- -------------------------------------------------------------
-- 5. 异常处置 / 更正 / 存证
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `biz_abnormal_disposal`;
CREATE TABLE `biz_abnormal_disposal` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `batch_id`         BIGINT       NOT NULL,
  `batch_no`         VARCHAR(40)  NOT NULL,
  `source_type`      VARCHAR(24)  NOT NULL DEFAULT 'QUALITY' COMMENT 'QUALITY/SPOT_CHECK/REPORT',
  `source_id`        BIGINT       DEFAULT NULL COMMENT '来源质检报告ID',
  `abnormal_type`    VARCHAR(32)  NOT NULL COMMENT 'PESTICIDE/HEAVY_METAL/OTHER',
  `description`      VARCHAR(1000) NOT NULL,
  `disposal_measure` VARCHAR(1000) DEFAULT NULL COMMENT '处置措施',
  `disposal_result`  VARCHAR(1000) DEFAULT NULL COMMENT '处置结果',
  `handler_id`       BIGINT       DEFAULT NULL,
  `handler_name`     VARCHAR(64)  DEFAULT NULL,
  `handle_time`      DATETIME     DEFAULT NULL,
  `status`           VARCHAR(16)  NOT NULL DEFAULT 'HANDLING' COMMENT 'HANDLING/DONE',
  `abnormal_hash`    VARCHAR(80)  DEFAULT NULL COMMENT '异常判定摘要（不可变）',
  `disposal_hash`    VARCHAR(80)  DEFAULT NULL COMMENT '处置结果摘要（不可变）',
  `data_hash`        VARCHAR(80)  DEFAULT NULL,
  `chain_status`     VARCHAR(16)  NOT NULL DEFAULT 'NONE',
  `chain_tx_hash`    VARCHAR(80)  DEFAULT NULL,
  `block_number`     BIGINT       DEFAULT NULL,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted`          TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常批次处置表';

DROP TABLE IF EXISTS `biz_correction`;
CREATE TABLE `biz_correction` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `batch_id`      BIGINT       NOT NULL,
  `batch_no`      VARCHAR(40)  NOT NULL,
  `biz_type`      VARCHAR(32)  NOT NULL COMMENT 'PLANTING/HARVEST/QUALITY/LOGISTICS/SALES',
  `biz_id`        BIGINT       NOT NULL COMMENT '被更正的业务记录ID',
  `reason`        VARCHAR(500) NOT NULL COMMENT '更正原因',
  `content`       VARCHAR(1000) NOT NULL COMMENT '更正说明（追加，不覆盖原文）',
  `operator_id`   BIGINT       NOT NULL,
  `operator_name` VARCHAR(64)  DEFAULT NULL,
  `data_hash`     VARCHAR(80)  DEFAULT NULL,
  `chain_status`  VARCHAR(16)  NOT NULL DEFAULT 'NONE',
  `chain_tx_hash` VARCHAR(80)  DEFAULT NULL,
  `block_number`  BIGINT       DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据更正说明表';

DROP TABLE IF EXISTS `chain_record`;
CREATE TABLE `chain_record` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `batch_no`     VARCHAR(40)  NOT NULL,
  `contract_address` VARCHAR(80) DEFAULT NULL COMMENT '合约地址（支持多版本合约路由）',
  `chain_index`  INT          DEFAULT NULL COMMENT '记录在合约中的下标，用于一致性校验',
  `biz_type`     VARCHAR(32)  NOT NULL COMMENT 'BATCH/PLANTING/HARVEST/QUALITY/LOGISTICS/SALES/ABNORMAL/DISPOSAL/CORRECTION',
  `biz_id`       BIGINT       DEFAULT NULL,
  `event_type`   VARCHAR(32)  NOT NULL COMMENT '链上事件类型',
  `data_hash`    VARCHAR(80)  NOT NULL,
  `file_hash`    VARCHAR(80)  DEFAULT NULL,
  `tx_hash`      VARCHAR(80)  DEFAULT NULL,
  `block_number` BIGINT       DEFAULT NULL,
  `gas_used`     BIGINT       DEFAULT NULL,
  `status`       VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILED/SKIPPED',
  `error_msg`    VARCHAR(500) DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_tx` (`tx_hash`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链上存证流水表';

DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `file_name`     VARCHAR(128) NOT NULL COMMENT '存储文件名',
  `original_name` VARCHAR(255) DEFAULT NULL,
  `file_path`     VARCHAR(500) NOT NULL,
  `file_hash`     VARCHAR(80)  NOT NULL COMMENT 'SHA-256',
  `file_size`     BIGINT       DEFAULT NULL,
  `content_type`  VARCHAR(128) DEFAULT NULL,
  `biz_type`      VARCHAR(32)  DEFAULT NULL,
  `biz_id`        BIGINT       DEFAULT NULL,
  `upload_by`     BIGINT       DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_hash` (`file_hash`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       DEFAULT NULL,
  `username`    VARCHAR(64)  DEFAULT NULL,
  `role_code`   VARCHAR(32)  DEFAULT NULL,
  `module`      VARCHAR(64)  DEFAULT NULL,
  `action`      VARCHAR(128) DEFAULT NULL,
  `request_uri` VARCHAR(255) DEFAULT NULL,
  `method`      VARCHAR(16)  DEFAULT NULL,
  `params`      VARCHAR(2000) DEFAULT NULL,
  `ip`          VARCHAR(64)  DEFAULT NULL,
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 成功 0 失败',
  `error_msg`   VARCHAR(500) DEFAULT NULL,
  `cost_ms`     BIGINT       DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志表';

DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `dict_type`  VARCHAR(64) NOT NULL COMMENT '字典类型',
  `dict_code`  VARCHAR(64) NOT NULL,
  `dict_label` VARCHAR(64) NOT NULL,
  `sort_no`    INT         NOT NULL DEFAULT 0,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `remark`     VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_code` (`dict_type`, `dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典表';

SET FOREIGN_KEY_CHECKS = 1;
