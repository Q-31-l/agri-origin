-- =============================================================
--  agri-origin · 基础测试数据
--  执行顺序：init.sql -> data.sql
--  所有演示账号密码均为 123456（BCrypt 10 轮）
-- =============================================================
SET NAMES utf8mb4;
USE `agri_trace`;

SET @pwd = '$2a$10$0HMubYrPte8lrKcwU.OYCeFAsNf95w9zQiAoQK7WHpUZSaFJQK2wC';

-- ---------------- 角色 ----------------
INSERT INTO `sys_role` (`role_code`, `role_name`, `data_scope`, `sort_no`, `remark`) VALUES
('ADMIN',        '系统管理员', 'ALL',     1, '平台运维与账号管理'),
('REGULATOR',    '监管人员',   'ALL',     2, '全量监管、异常处置与抽查'),
('PRODUCER',     '生产主体',   'ORG',     3, '产品与批次建档、全流程跟踪'),
('VILLAGE_ADMIN','村级管理员', 'VILLAGE', 4, '辖区农户管理、数据审核、批次核验'),
('FARMER',       '农户',       'SELF',    5, '农事记录、采收提交'),
('INSPECTOR',    '检测机构',   'ORG',     6, '质检报告上传与判定'),
('LOGISTICS',    '物流企业',   'ORG',     7, '物流交接登记'),
('SELLER',       '销售商',     'ORG',     8, '销售登记与批次核销');

-- ---------------- 权限点 ----------------
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `perm_type`, `module`, `path`, `method`) VALUES
('stats:overview',   '数据看板',       'MENU', 'stats',    '/stats/overview',        'GET'),
('batch:list',       '批次列表',       'API',  'batch',    '/batch/page',            'GET'),
('batch:detail',     '批次详情',       'API',  'batch',    '/batch/{id}',            'GET'),
('batch:create',     '创建批次',       'BUTTON','batch',   '/batch',                 'POST'),
('batch:update',     '编辑批次',       'BUTTON','batch',   '/batch/{id}',            'PUT'),
('batch:audit',      '批次核验',       'BUTTON','batch',   '/batch/{id}/audit',      'POST'),
('planting:list',    '农事记录查询',   'API',  'planting', '/planting/page',         'GET'),
('planting:create',  '农事记录提交',   'BUTTON','planting','/planting',              'POST'),
('planting:audit',   '农事记录审核',   'BUTTON','planting','/planting/{id}/audit',   'POST'),
('harvest:list',     '采收记录查询',   'API',  'harvest',  '/harvest/page',          'GET'),
('harvest:create',   '采收信息提交',   'BUTTON','harvest', '/harvest',               'POST'),
('harvest:audit',    '采收信息审核',   'BUTTON','harvest', '/harvest/{id}/audit',    'POST'),
('quality:list',     '质检报告查询',   'API',  'quality',  '/quality/page',          'GET'),
('quality:create',   '质检报告上传',   'BUTTON','quality', '/quality',               'POST'),
('logistics:list',   '物流记录查询',   'API',  'logistics','/logistics/page',        'GET'),
('logistics:create', '物流交接登记',   'BUTTON','logistics','/logistics',            'POST'),
('sales:list',       '销售记录查询',   'API',  'sales',    '/sales/page',            'GET'),
('sales:create',     '销售登记',       'BUTTON','sales',   '/sales',                 'POST'),
('trace:query',      '溯源查询',       'API',  'trace',    '/trace/{code}',          'GET'),
('abnormal:list',    '异常批次查询',   'API',  'abnormal', '/abnormal/page',         'GET'),
('abnormal:dispose', '异常处置',       'BUTTON','abnormal','/abnormal/{id}/dispose', 'POST'),
('abnormal:trace',   '上下游追溯',     'API',  'abnormal', '/abnormal/{id}/trace',   'GET'),
('chain:verify',     '一致性校验',     'API',  'chain',    '/chain/verify/{batchNo}', 'GET'),
('chain:list',       '存证流水查询',   'API',  'chain',    '/chain/page',            'GET'),
('user:list',        '用户查询',       'API',  'system',   '/user/page',             'GET'),
('user:create',      '用户新增',       'BUTTON','system',  '/user',                  'POST'),
('user:update',      '用户编辑',       'BUTTON','system',  '/user/{id}',             'PUT'),
('user:reset',       '重置密码',       'BUTTON','system',  '/user/{id}/password',    'PUT'),
('farm:list',        '农场查询',       'API',  'farm',     '/farm/page',             'GET'),
('farm:create',      '农场新增',       'BUTTON','farm',    '/farm',                  'POST'),
('farm:update',      '农场编辑',       'BUTTON','farm',    '/farm/{id}',             'PUT'),
('village:list',     '行政村查询',     'API',  'village',  '/village/list',          'GET'),
('village:create',   '行政村新增',     'BUTTON','village', '/village',               'POST'),
('product:list',     '产品品类查询',   'API',  'product',  '/product/list',          'GET'),
('product:create',   '产品品类新增',   'BUTTON','product', '/product',               'POST'),
('product:update',   '产品品类编辑',   'BUTTON','product', '/product/{id}',          'PUT'),
('log:list',         '操作日志查询',   'API',  'system',   '/log/page',              'GET'),
('file:upload',      '文件上传',       'API',  'file',     '/file/upload',           'POST');

-- ---------------- 角色-权限 ----------------
-- 系统管理员：全部权限
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`)
SELECT 'ADMIN', `perm_code` FROM `sys_permission`;

-- 监管人员
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('REGULATOR','stats:overview'),('REGULATOR','batch:list'),('REGULATOR','batch:detail'),
('REGULATOR','batch:audit'),('REGULATOR','planting:list'),('REGULATOR','harvest:list'),
('REGULATOR','quality:list'),('REGULATOR','logistics:list'),('REGULATOR','sales:list'),
('REGULATOR','trace:query'),('REGULATOR','abnormal:list'),('REGULATOR','abnormal:dispose'),
('REGULATOR','abnormal:trace'),('REGULATOR','chain:verify'),('REGULATOR','chain:list'),
('REGULATOR','log:list'),('REGULATOR','user:list'),('REGULATOR','product:list'),
('REGULATOR','farm:list'),('REGULATOR','village:list'),('REGULATOR','file:upload');

-- 生产主体
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('PRODUCER','stats:overview'),('PRODUCER','batch:list'),('PRODUCER','batch:detail'),
('PRODUCER','batch:create'),('PRODUCER','batch:update'),('PRODUCER','planting:list'),
('PRODUCER','harvest:list'),('PRODUCER','quality:list'),('PRODUCER','logistics:list'),
('PRODUCER','sales:list'),('PRODUCER','trace:query'),('PRODUCER','chain:verify'),
('PRODUCER','abnormal:list'),('PRODUCER','product:list'),('PRODUCER','product:create'),
('PRODUCER','product:update'),('PRODUCER','farm:list'),('PRODUCER','file:upload');

-- 村级管理员
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('VILLAGE_ADMIN','stats:overview'),('VILLAGE_ADMIN','batch:list'),('VILLAGE_ADMIN','batch:detail'),
('VILLAGE_ADMIN','batch:audit'),('VILLAGE_ADMIN','planting:list'),('VILLAGE_ADMIN','planting:audit'),
('VILLAGE_ADMIN','harvest:list'),('VILLAGE_ADMIN','harvest:audit'),('VILLAGE_ADMIN','farm:list'),
('VILLAGE_ADMIN','farm:create'),('VILLAGE_ADMIN','farm:update'),('VILLAGE_ADMIN','user:list'),
('VILLAGE_ADMIN','user:create'),('VILLAGE_ADMIN','user:update'),('VILLAGE_ADMIN','user:reset'),
('VILLAGE_ADMIN','trace:query'),('VILLAGE_ADMIN','chain:verify'),('VILLAGE_ADMIN','product:list'),
('VILLAGE_ADMIN','village:list'),('VILLAGE_ADMIN','file:upload');

-- 农户
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('FARMER','batch:list'),('FARMER','batch:detail'),('FARMER','planting:list'),
('FARMER','planting:create'),('FARMER','harvest:list'),('FARMER','harvest:create'),
('FARMER','trace:query'),('FARMER','file:upload'),('FARMER','product:list');

-- 检测机构
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('INSPECTOR','stats:overview'),('INSPECTOR','batch:list'),('INSPECTOR','batch:detail'),
('INSPECTOR','quality:list'),('INSPECTOR','quality:create'),('INSPECTOR','trace:query'),
('INSPECTOR','chain:verify'),('INSPECTOR','file:upload'),('INSPECTOR','product:list');

-- 物流企业
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('LOGISTICS','stats:overview'),('LOGISTICS','batch:list'),('LOGISTICS','batch:detail'),
('LOGISTICS','logistics:list'),('LOGISTICS','logistics:create'),('LOGISTICS','trace:query'),
('LOGISTICS','chain:verify'),('LOGISTICS','file:upload');

-- 销售商
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('SELLER','stats:overview'),('SELLER','batch:list'),('SELLER','batch:detail'),
('SELLER','sales:list'),('SELLER','sales:create'),('SELLER','trace:query'),
('SELLER','chain:verify'),('SELLER','file:upload');

-- ---------------- 用户 ----------------
INSERT INTO `sys_user` (`username`,`password`,`real_name`,`phone`,`role_code`,`org_name`,`village_id`,`farm_id`,`status`) VALUES
('admin',       @pwd, '系统管理员', '13800000001', 'ADMIN',        '平台运营中心',            NULL, NULL, 1),
('regulator01', @pwd, '陈立',       '13800000002', 'REGULATOR',    '闽侯县农业农村局',        NULL, NULL, 1),
('producer01',  @pwd, '林建国',     '13800000003', 'PRODUCER',     '溪头村果蔬专业合作社',    NULL, NULL, 1),
('village01',   @pwd, '王秀兰',     '13800000004', 'VILLAGE_ADMIN','溪头村村民委员会',        1,    NULL, 1),
('farmer01',    @pwd, '张大山',     '13800000005', 'FARMER',       '溪头村一组',              1,    1,    1),
('farmer02',    @pwd, '李桂花',     '13800000006', 'FARMER',       '上寨村二组',              2,    2,    1),
('inspector01', @pwd, '周敏',       '13800000007', 'INSPECTOR',    '闽侯县农产品质量检测中心', NULL, NULL, 1),
('logistics01', @pwd, '郑海',       '13800000008', 'LOGISTICS',    '福州鲜达冷链物流有限公司', NULL, NULL, 1),
('seller01',    @pwd, '吴晓芸',     '13800000009', 'SELLER',       '永辉超市闽侯旗舰店',      NULL, NULL, 1);

-- ---------------- 行政村 ----------------
INSERT INTO `base_village` (`id`,`village_code`,`village_name`,`region_code`,`region_name`,`address`,`manager_id`,`manager_name`,`remark`) VALUES
(1, '350121001', '溪头村', '350121', '福建省福州市闽侯县', '闽侯县白沙镇溪头村', 4, '王秀兰', '草莓、蔬菜主产区'),
(2, '350121002', '上寨村', '350121', '福建省福州市闽侯县', '闽侯县白沙镇上寨村', NULL, NULL, '脐橙主产区');

-- ---------------- 农场 ----------------
INSERT INTO `base_farm` (`id`,`farm_code`,`farm_name`,`farmer_id`,`farmer_name`,`village_id`,`village_name`,`address`,`area`,`soil_type`,`water_source`,`cert_no`,`status`) VALUES
(1, 'FARM-350121-001', '溪头一号草莓大棚', 5, '张大山', 1, '溪头村', '溪头村一组 12 号田', 8.50, '沙壤土', '山泉水灌溉', 'TD-2021-0081', 1),
(2, 'FARM-350121-002', '上寨脐橙园',       6, '李桂花', 2, '上寨村', '上寨村二组后山',     23.00, '红壤',   '水库引水',   'TD-2020-0157', 1);

-- ---------------- 产品 ----------------
INSERT INTO `base_product` (`id`,`product_code`,`product_name`,`category`,`variety`,`unit`,`shelf_life_days`,`origin_region`,`description`) VALUES
(1, 'P-STR-001', '红颜草莓', '水果', '红颜', 'kg', 5,  '福建省福州市闽侯县', '设施大棚栽培，无公害认证'),
(2, 'P-ORG-002', '脐橙',     '水果', '纽荷尔', 'kg', 30, '福建省福州市闽侯县', '山地果园，昼夜温差大'),
(3, 'P-VEG-003', '小白菜',   '蔬菜', '上海青', 'kg', 7,  '福建省福州市闽侯县', '露地种植，夏季主栽');

-- ---------------- 演示批次 ----------------
INSERT INTO `biz_batch`
(`id`,`batch_no`,`trace_code`,`product_id`,`product_name`,`variety`,`farm_id`,`farm_name`,`farmer_id`,`farmer_name`,`village_id`,`village_name`,
 `planting_date`,`harvest_date`,`quantity`,`unit`,`status`,`audit_status`,`audit_by`,`audit_time`,`audit_remark`,`abnormal_flag`,`chain_status`,`remark`,`create_by`)
VALUES
(1,'AG20260512001','TC20260512001',1,'红颜草莓','红颜',1,'溪头一号草莓大棚',5,'张大山',1,'溪头村',
 '2026-02-10','2026-05-10',1200.00,'kg','SOLD','APPROVED','王秀兰','2026-05-11 09:20:00','资料齐全，准予建档',0,'NONE','示范：全流程正常批次',3),
(2,'AG20260515002','TC20260515002',3,'小白菜','上海青',1,'溪头一号草莓大棚',5,'张大山',1,'溪头村',
 '2026-04-01','2026-05-14',600.00,'kg','ABNORMAL','APPROVED','王秀兰','2026-05-15 08:40:00','准予建档',1,'NONE','示范：质检不合格拦截批次',3),
(3,'AG20260601003','TC20260601003',2,'脐橙','纽荷尔',2,'上寨脐橙园',6,'李桂花',2,'上寨村',
 '2026-03-01',NULL,3000.00,'kg','PLANTING','PENDING',NULL,NULL,NULL,0,'NONE','示范：在种植中批次',3);

INSERT INTO `biz_planting_record`
(`batch_id`,`batch_no`,`record_type`,`operation_date`,`content`,`material_name`,`dosage`,`operator_id`,`operator_name`,`images`,`audit_status`,`audit_remark`)
VALUES
(1,'AG20260512001','SOIL','2026-02-08','深翻晒垡，每亩施入腐熟有机肥 300kg','商品有机肥','300kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(1,'AG20260512001','SOWING','2026-02-10','定植红颜草莓苗 8000 株，株距 20cm','脱毒种苗','8000株',5,'张大山',NULL,'APPROVED','记录属实'),
(1,'AG20260512001','FERTILIZE','2026-03-15','滴灌追施水溶肥，注意控制氮肥用量','平衡型水溶肥','5kg/亩',5,'张大山',NULL,'APPROVED',NULL),
(1,'AG20260512001','PEST','2026-04-02','悬挂黄板诱杀蚜虫，未使用化学农药','黄板','40张/亩',5,'张大山',NULL,'APPROVED',NULL);

INSERT INTO `biz_harvest_record`
(`batch_id`,`batch_no`,`harvest_date`,`quantity`,`unit`,`quality_level`,`weather`,`operator_id`,`operator_name`,`remark`,`audit_status`,`audit_remark`)
VALUES
(1,'AG20260512001','2026-05-10',1200.00,'kg','A','晴 24℃',5,'张大山','清晨采收，预冷后装筐','APPROVED','数量与地块产量相符');

INSERT INTO `biz_quality_report`
(`batch_id`,`batch_no`,`report_no`,`inspect_org`,`inspector_id`,`inspector_name`,`inspect_date`,`inspect_result`,`standard_no`,`items_json`,`conclusion`)
VALUES
(1,'AG20260512001','JC20260512-001','闽侯县农产品质量检测中心',7,'周敏','2026-05-12','PASS','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.008","limit":"0.02","unit":"mg/kg","result":"合格"},{"item":"铅","value":"0.05","limit":"0.1","unit":"mg/kg","result":"合格"},{"item":"镉","value":"0.01","limit":"0.05","unit":"mg/kg","result":"合格"}]',
 '所检项目符合 GB 2763-2021 限量要求，判定合格'),
(2,'AG20260515002','JC20260515-002','闽侯县农产品质量检测中心',7,'周敏','2026-05-15','FAIL','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.061","limit":"0.02","unit":"mg/kg","result":"不合格"},{"item":"铅","value":"0.04","limit":"0.1","unit":"mg/kg","result":"合格"}]',
 '毒死蜱残留超标，判定不合格，禁止销售');

INSERT INTO `biz_logistics_record`
(`batch_id`,`batch_no`,`logistics_no`,`from_org`,`to_org`,`carrier_id`,`carrier_name`,`vehicle_no`,`driver_name`,`driver_phone`,`depart_time`,`arrive_time`,`temperature`,`status`,`remark`)
VALUES
(1,'AG20260512001','YD20260512001','溪头村果蔬专业合作社','永辉超市闽侯旗舰店',8,'福州鲜达冷链物流有限公司','闽A·8K521','郑海','13900001111','2026-05-12 06:30:00','2026-05-12 09:10:00','2~8℃','ARRIVED','全程冷链，交接单已签字');

INSERT INTO `biz_sales_record`
(`batch_id`,`batch_no`,`seller_id`,`seller_name`,`market_name`,`sales_date`,`price`,`quantity`,`unit`,`buyer_name`)
VALUES
(1,'AG20260512001',9,'永辉超市闽侯旗舰店','永辉超市闽侯旗舰店','2026-05-13',39.90,200.00,'kg','门店零售');

INSERT INTO `biz_abnormal_disposal`
(`batch_id`,`batch_no`,`source_type`,`source_id`,`abnormal_type`,`description`,`disposal_measure`,`disposal_result`,`handler_id`,`handler_name`,`handle_time`,`status`)
VALUES
(2,'AG20260515002','QUALITY',2,'PESTICIDE','毒死蜱残留 0.061mg/kg，超出 GB 2763-2021 限量值 0.02mg/kg',
 '立即封存剩余 600kg 批次产品，禁止出库；追溯上游农事记录并对同地块产品抽样复检',
 NULL,2,'陈立','2026-05-15 15:00:00','HANDLING');

-- ---------------- 字典 ----------------
INSERT INTO `sys_dict` (`dict_type`,`dict_code`,`dict_label`,`sort_no`) VALUES
('batch_status','PLANTING','种植中',1),
('batch_status','HARVESTED','已采收',2),
('batch_status','QUALIFIED','质检合格',3),
('batch_status','IN_TRANSIT','运输中',4),
('batch_status','ON_SALE','在售',5),
('batch_status','SOLD','已售出',6),
('batch_status','ABNORMAL','异常管控',7),
('batch_status','CLOSED','已结案',8),
('record_type','SOIL','整地施肥',1),
('record_type','SOWING','播种定植',2),
('record_type','FERTILIZE','追肥',3),
('record_type','IRRIGATE','灌溉',4),
('record_type','PEST','病虫害防治',5),
('record_type','WEED','除草',6),
('record_type','OTHER','其他农事',7),
('abnormal_type','PESTICIDE','农药残留超标',1),
('abnormal_type','HEAVY_METAL','重金属超标',2),
('abnormal_type','MICROBE','微生物超标',3),
('abnormal_type','OTHER','其他原因',4),
('audit_status','PENDING','待审核',1),
('audit_status','APPROVED','审核通过',2),
('audit_status','REJECTED','审核驳回',3);
