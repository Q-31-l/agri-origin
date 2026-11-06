-- =============================================================
--  agri-origin · 基础测试数据
--  执行顺序：init.sql -> data.sql
--  所有演示账号密码均为 123456（BCrypt 10 轮）
--
--  数据规模（对应实训任务书「素材准备」要求）：
--    · 8 个角色、42 个权限点、9 个演示账号
--    · 2 个行政村、2 个农场、3 种产品
--    · 12 个批次，其中 2 个检测异常批次（批次 2 农残超标、批次 6 重金属超标）
--    · 每个批次的业务节点记录均不少于 4 条：
--        农事 48 条 / 采收 8 条 / 质检 7 条 / 物流 4 条 / 销售 6 条 / 异常处置 2 条
--    · SOLD 批次的销售记录累计量等于批次数量，ON_SALE 批次为部分售出，状态与记录自洽
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
('abnormal:list',    '异常批次查询',   'API',  'abnormal', '/abnormal/page',         'GET'),
('abnormal:dispose', '异常处置',       'BUTTON','abnormal','/abnormal/{id}/dispose', 'POST'),
('abnormal:trace',   '上下游追溯',     'API',  'abnormal', '/abnormal/{id}/trace',   'GET'),
('chain:verify',     '一致性校验',     'API',  'chain',    '/chain/verify/{batchNo}', 'GET'),
('chain:list',       '存证流水查询',   'API',  'chain',    '/chain/page',            'GET'),
('chain:repair',     '存量批次补注册', 'BUTTON','chain',   '/chain/repair/batches',  'POST'),
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
('quality:audit',    '质检报告审核',   'BUTTON','quality',  '/quality/{id}/audit',    'POST'),
('logistics:audit',  '物流记录审核',   'BUTTON','logistics','/logistics/{id}/audit',  'POST'),
('sales:audit',      '销售记录审核',   'BUTTON','sales',    '/sales/{id}/audit',      'POST'),
('file:upload',      '文件上传',       'API',  'file',     '/file/upload',           'POST'),
('correction:create', '提交更正说明',   'BUTTON','correction','/correction',           'POST');

-- ---------------- 角色-权限 ----------------
-- 系统管理员：全部权限
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`)
SELECT 'ADMIN', `perm_code` FROM `sys_permission`;

-- 监管人员
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('REGULATOR','stats:overview'),('REGULATOR','batch:list'),('REGULATOR','batch:detail'),
('REGULATOR','batch:audit'),('REGULATOR','planting:list'),('REGULATOR','harvest:list'),
('REGULATOR','quality:list'),('REGULATOR','logistics:list'),('REGULATOR','sales:list'),
('REGULATOR','abnormal:list'),('REGULATOR','abnormal:dispose'),
('REGULATOR','abnormal:trace'),('REGULATOR','chain:verify'),('REGULATOR','chain:list'),
('REGULATOR','chain:repair'),
('REGULATOR','log:list'),('REGULATOR','user:list'),('REGULATOR','product:list'),
('REGULATOR','farm:list'),('REGULATOR','village:list'),('REGULATOR','file:upload'),
('REGULATOR','quality:audit'),('REGULATOR','logistics:audit'),('REGULATOR','sales:audit'),
('REGULATOR','correction:create');

-- 生产主体
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('PRODUCER','stats:overview'),('PRODUCER','batch:list'),('PRODUCER','batch:detail'),
('PRODUCER','batch:create'),('PRODUCER','batch:update'),('PRODUCER','planting:list'),
('PRODUCER','harvest:list'),('PRODUCER','quality:list'),('PRODUCER','logistics:list'),
('PRODUCER','sales:list'),('PRODUCER','chain:verify'),
('PRODUCER','abnormal:list'),('PRODUCER','product:list'),('PRODUCER','product:create'),
('PRODUCER','product:update'),('PRODUCER','farm:list'),('PRODUCER','file:upload'),
('PRODUCER','abnormal:trace'),('PRODUCER','correction:create'),
-- 农场档案必须选所属行政村，没有 village:list 连新建弹窗都填不完
('PRODUCER','village:list');

-- 村级管理员：只审农户侧数据（批次核验 / 农事 / 采收）。
-- 质检报告、物流交接、销售流水的复核属监管职责，不授予村级，避免出现
-- "有审核权限却看不到列表"的悬空授权（审核入口依赖对应 *:list）。
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('VILLAGE_ADMIN','stats:overview'),('VILLAGE_ADMIN','batch:list'),('VILLAGE_ADMIN','batch:detail'),
('VILLAGE_ADMIN','batch:audit'),('VILLAGE_ADMIN','planting:list'),('VILLAGE_ADMIN','planting:audit'),
('VILLAGE_ADMIN','harvest:list'),('VILLAGE_ADMIN','harvest:audit'),('VILLAGE_ADMIN','farm:list'),
('VILLAGE_ADMIN','farm:create'),('VILLAGE_ADMIN','farm:update'),('VILLAGE_ADMIN','user:list'),
('VILLAGE_ADMIN','user:create'),('VILLAGE_ADMIN','user:update'),('VILLAGE_ADMIN','user:reset'),
('VILLAGE_ADMIN','chain:verify'),('VILLAGE_ADMIN','product:list'),
('VILLAGE_ADMIN','village:list'),('VILLAGE_ADMIN','file:upload'),
('VILLAGE_ADMIN','correction:create');

-- 农户：stats:overview 必须授予——移动端工作台的"我的批次 / 异常批次 / 合格率"
-- 三个卡片直接读该接口，服务端已按 farmer_id 收窄，不授会造成首页数字全空。
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('FARMER','stats:overview'),('FARMER','batch:list'),('FARMER','batch:detail'),
('FARMER','planting:list'),('FARMER','planting:create'),('FARMER','harvest:list'),
('FARMER','harvest:create'),('FARMER','file:upload'),('FARMER','product:list'),
-- 农户是批次的第一责任人，批次详情页的「一致性校验」对他是核心能力。
-- 授权不会带来越权读取：/batch/{id}/verify 走 getAuthorized 按 farmer_id 收窄，
-- /chain/verify/{batchNo} 也已改为 getAuthorizedByNo。
('FARMER','chain:verify');

-- 检测机构
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('INSPECTOR','stats:overview'),('INSPECTOR','batch:list'),('INSPECTOR','batch:detail'),
('INSPECTOR','quality:list'),('INSPECTOR','quality:create'),
('INSPECTOR','chain:verify'),('INSPECTOR','file:upload'),('INSPECTOR','product:list');

-- 物流企业
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('LOGISTICS','stats:overview'),('LOGISTICS','batch:list'),('LOGISTICS','batch:detail'),
('LOGISTICS','logistics:list'),('LOGISTICS','logistics:create'),
('LOGISTICS','chain:verify'),('LOGISTICS','file:upload'),('LOGISTICS','product:list');

-- 销售商
INSERT INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
('SELLER','stats:overview'),('SELLER','batch:list'),('SELLER','batch:detail'),
('SELLER','sales:list'),('SELLER','sales:create'),
('SELLER','chain:verify'),('SELLER','file:upload'),('SELLER','product:list');

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
(2, 'FARM-350121-002', '上寨脐橙园',       6, '李桂花', 2, '上寨村', '上寨村二组后山',     23.00, '红壤',   '水库引水',   'TD-2020-0157', 1),
(3, 'FARM-350121-003', '溪头蔬菜基地',     5, '张大山', 1, '溪头村', '溪头村三组菜田',     15.20, '沙壤土', '山塘灌溉',   'TD-2021-0093', 1);

-- ---------------- 产品 ----------------
INSERT INTO `base_product` (`id`,`product_code`,`product_name`,`category`,`variety`,`unit`,`shelf_life_days`,`origin_region`,`description`) VALUES
(1, 'P-STR-001', '红颜草莓', '水果', '红颜', 'kg', 5,  '福建省福州市闽侯县', '设施大棚栽培，无公害认证'),
(2, 'P-ORG-002', '脐橙',     '水果', '纽荷尔', 'kg', 30, '福建省福州市闽侯县', '山地果园，昼夜温差大'),
(3, 'P-VEG-003', '小白菜',   '蔬菜', '上海青', 'kg', 7,  '福建省福州市闽侯县', '露地种植，夏季主栽');

-- ---------------- 演示批次 ----------------
-- ---------------- 演示批次（12 个，含 2 个检测异常批次） ----------------
INSERT INTO `biz_batch`
(`id`,`batch_no`,`trace_code`,`product_id`,`product_name`,`variety`,`farm_id`,`farm_name`,`farmer_id`,`farmer_name`,`village_id`,`village_name`,
 `planting_date`,`harvest_date`,`quantity`,`unit`,`status`,`audit_status`,`audit_by`,`audit_time`,`audit_remark`,`abnormal_flag`,`chain_status`,`remark`,`create_by`)
VALUES
(1,'AG20260512001','TC20260512001',1,'红颜草莓','红颜',1,'溪头一号草莓大棚',5,'张大山',1,'溪头村',
 '2026-02-10','2026-05-10',1200.00,'kg','SOLD','APPROVED','王秀兰','2026-05-11 09:20:00','资料齐全，准予建档',0,'NONE','示范：全流程正常批次',3),
(2,'AG20260515002','TC20260515002',3,'小白菜','上海青',3,'溪头蔬菜基地',5,'张大山',1,'溪头村',
 '2026-04-01','2026-05-14',600.00,'kg','ABNORMAL','APPROVED','王秀兰','2026-05-15 08:40:00','准予建档',1,'NONE','示范：农药残留超标拦截批次',3),
(3,'AG20260601003','TC20260601003',2,'脐橙','纽荷尔',2,'上寨脐橙园',6,'李桂花',2,'上寨村',
 '2026-03-01',NULL,3000.00,'kg','PLANTING','PENDING',NULL,NULL,NULL,0,'NONE','示范：种植中批次',3),
(4,'AG20260518004','TC20260518004',1,'红颜草莓','红颜',1,'溪头一号草莓大棚',5,'张大山',1,'溪头村',
 '2026-02-15','2026-05-16',800.00,'kg','SOLD','APPROVED','王秀兰','2026-05-17 10:05:00','抽检资料完整，准予建档',0,'NONE','示范：二茬草莓正常批次',3),
(5,'AG20260520005','TC20260520005',3,'小白菜','上海青',3,'溪头蔬菜基地',5,'张大山',1,'溪头村',
 '2026-04-05','2026-05-18',500.00,'kg','SOLD','APPROVED','王秀兰','2026-05-19 09:10:00','记录齐全，准予建档',0,'NONE','示范：叶菜正常批次',3),
(6,'AG20260522006','TC20260522006',2,'脐橙','纽荷尔',2,'上寨脐橙园',6,'李桂花',2,'上寨村',
 '2026-03-05','2026-05-20',2000.00,'kg','ABNORMAL','APPROVED','王秀兰','2026-05-21 14:30:00','准予建档',1,'NONE','示范：重金属超标熔断批次',3),
(7,'AG20260525007','TC20260525007',1,'红颜草莓','红颜',1,'溪头一号草莓大棚',5,'张大山',1,'溪头村',
 '2026-03-01','2026-05-23',650.00,'kg','QUALIFIED','APPROVED','王秀兰','2026-05-24 08:50:00','资料齐全，准予建档',0,'NONE','示范：质检合格待发运批次',3),
(8,'AG20260528008','TC20260528008',3,'小白菜','上海青',3,'溪头蔬菜基地',5,'张大山',1,'溪头村',
 '2026-04-10','2026-05-26',700.00,'kg','ON_SALE','APPROVED','王秀兰','2026-05-27 09:00:00','记录齐全，准予建档',0,'NONE','示范：销售中批次（部分售出）',3),
(9,'AG20260602009','TC20260602009',2,'脐橙','纽荷尔',2,'上寨脐橙园',6,'李桂花',2,'上寨村',
 '2026-03-15','2026-06-01',1500.00,'kg','HARVESTED','APPROVED','王秀兰','2026-06-02 10:20:00','采收记录属实，准予建档',0,'NONE','示范：已采收待送检批次',3),
(10,'AG20260605010','TC20260605010',1,'红颜草莓','红颜',1,'溪头一号草莓大棚',5,'张大山',1,'溪头村',
 '2026-03-20',NULL,900.00,'kg','PLANTING','PENDING',NULL,NULL,NULL,0,'NONE','示范：草莓生长期批次',3),
(11,'AG20260608011','TC20260608011',3,'小白菜','上海青',3,'溪头蔬菜基地',5,'张大山',1,'溪头村',
 '2026-04-25',NULL,550.00,'kg','PLANTING','PENDING',NULL,NULL,NULL,0,'NONE','示范：叶菜生长期批次',3),
(12,'AG20260610012','TC20260610012',2,'脐橙','纽荷尔',2,'上寨脐橙园',6,'李桂花',2,'上寨村',
 '2026-03-25',NULL,1800.00,'kg','PLANTING','PENDING',NULL,NULL,NULL,0,'NONE','示范：脐橙生长期批次',3);

-- 农事记录（每批次 4 条）
INSERT INTO `biz_planting_record`
(`batch_id`,`batch_no`,`record_type`,`operation_date`,`content`,`material_name`,`dosage`,`operator_id`,`operator_name`,`images`,`audit_status`,`audit_remark`)
VALUES
(1,'AG20260512001','SOIL','2026-02-08','深翻晒垡，每亩施入腐熟有机肥 300kg','商品有机肥','300kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(1,'AG20260512001','SOWING','2026-02-10','定植红颜草莓苗 8000 株，株距 20cm','脱毒种苗','8000株',5,'张大山',NULL,'APPROVED','记录属实'),
(1,'AG20260512001','FERTILIZE','2026-03-15','滴灌追施水溶肥，注意控制氮肥用量','平衡型水溶肥','5kg/亩',5,'张大山',NULL,'APPROVED',NULL),
(1,'AG20260512001','PEST','2026-04-02','悬挂黄板诱杀蚜虫，未使用化学农药','黄板','40张/亩',5,'张大山',NULL,'APPROVED',NULL),
(2,'AG20260515002','SOIL','2026-03-30','旋耕整地，起垄做畦，畦宽 1.2m','商品有机肥','200kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(2,'AG20260515002','SOWING','2026-04-01','直播小白菜，行距 15cm','上海青种子','0.5kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(2,'AG20260515002','FERTILIZE','2026-04-18','追施尿素提苗','尿素','8kg/亩',5,'张大山',NULL,'APPROVED',NULL),
(2,'AG20260515002','PEST','2026-05-02','喷施吡虫啉防治蚜虫（重点核查项）','10%吡虫啉可湿性粉剂','20g/亩',5,'张大山',NULL,'APPROVED','用药记录已登记'),
(3,'AG20260601003','SOIL','2026-02-25','果园深翻扩穴，增施有机肥改良红壤','羊粪有机肥','400kg/亩',6,'李桂花',NULL,'APPROVED','记录属实'),
(3,'AG20260601003','SOWING','2026-03-01','补植纽荷尔脐橙苗 120 株','嫁接苗','120株',6,'李桂花',NULL,'APPROVED','记录属实'),
(3,'AG20260601003','IRRIGATE','2026-04-10','花期滴灌一次，保持土壤湿度','水库引水','15m³/亩',6,'李桂花',NULL,'APPROVED',NULL),
(3,'AG20260601003','FERTILIZE','2026-05-06','施用复合肥促进幼果膨大','硫酸钾复合肥','3kg/株',6,'李桂花',NULL,'PENDING',NULL),
(4,'AG20260518004','SOIL','2026-02-12','二茬整地，清除上茬残株','商品有机肥','280kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(4,'AG20260518004','SOWING','2026-02-15','定植草莓苗 6000 株','脱毒种苗','6000株',5,'张大山',NULL,'APPROVED','记录属实'),
(4,'AG20260518004','FERTILIZE','2026-03-20','滴灌追施高钾水溶肥','高钾水溶肥','4kg/亩',5,'张大山',NULL,'APPROVED',NULL),
(4,'AG20260518004','WEED','2026-04-15','人工除草一次，不使用除草剂','—','—',5,'张大山',NULL,'APPROVED',NULL),
(5,'AG20260520005','SOIL','2026-04-02','整地做畦，铺设滴灌带','商品有机肥','200kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(5,'AG20260520005','SOWING','2026-04-05','直播小白菜','上海青种子','0.5kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(5,'AG20260520005','IRRIGATE','2026-04-20','早晚滴灌，避开高温时段','山泉水','10m³/亩',5,'张大山',NULL,'APPROVED',NULL),
(5,'AG20260520005','PEST','2026-05-04','使用防虫网物理隔离，未施化学农药','防虫网','—',5,'张大山',NULL,'APPROVED',NULL),
(6,'AG20260522006','SOIL','2026-03-02','果园行间生草，减少水土流失','—','—',6,'李桂花',NULL,'APPROVED','记录属实'),
(6,'AG20260522006','FERTILIZE','2026-03-08','施用含锌复合肥（重点核查项）','含锌复合肥','2kg/株',6,'李桂花',NULL,'APPROVED','肥料来源已登记'),
(6,'AG20260522006','IRRIGATE','2026-04-12','幼果期灌溉一次','水库引水','15m³/亩',6,'李桂花',NULL,'APPROVED',NULL),
(6,'AG20260522006','PEST','2026-04-28','悬挂诱虫灯防治柑橘潜叶蛾','诱虫灯','10盏/亩',6,'李桂花',NULL,'APPROVED',NULL),
(7,'AG20260525007','SOIL','2026-02-26','整地施基肥','商品有机肥','300kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(7,'AG20260525007','SOWING','2026-03-01','定植草莓苗 5000 株','脱毒种苗','5000株',5,'张大山',NULL,'APPROVED','记录属实'),
(7,'AG20260525007','FERTILIZE','2026-04-05','追施平衡型水溶肥','平衡型水溶肥','4kg/亩',5,'张大山',NULL,'APPROVED',NULL),
(7,'AG20260525007','PEST','2026-04-22','释放捕食螨生物防治红蜘蛛','捕食螨','2000只/亩',5,'张大山',NULL,'APPROVED',NULL),
(8,'AG20260528008','SOIL','2026-04-08','整地做畦','商品有机肥','200kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(8,'AG20260528008','SOWING','2026-04-10','直播小白菜','上海青种子','0.5kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(8,'AG20260528008','IRRIGATE','2026-04-25','滴灌补水','山泉水','10m³/亩',5,'张大山',NULL,'APPROVED',NULL),
(8,'AG20260528008','WEED','2026-05-08','人工除草','—','—',5,'张大山',NULL,'APPROVED',NULL),
(9,'AG20260602009','SOIL','2026-03-12','果园清园，修剪病枝','—','—',6,'李桂花',NULL,'APPROVED','记录属实'),
(9,'AG20260602009','FERTILIZE','2026-03-18','花前追肥','硫酸钾复合肥','2kg/株',6,'李桂花',NULL,'APPROVED',NULL),
(9,'AG20260602009','IRRIGATE','2026-04-20','果实膨大期灌溉','水库引水','15m³/亩',6,'李桂花',NULL,'APPROVED',NULL),
(9,'AG20260602009','PEST','2026-05-10','悬挂粘虫板防治橘小实蝇','粘虫板','30张/亩',6,'李桂花',NULL,'APPROVED',NULL),
(10,'AG20260605010','SOIL','2026-03-18','高温闷棚消毒，杀灭土传病菌','石灰氮','60kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(10,'AG20260605010','SOWING','2026-03-20','定植草莓苗 6500 株','脱毒种苗','6500株',5,'张大山',NULL,'APPROVED',NULL),
(10,'AG20260605010','IRRIGATE','2026-04-28','滴灌保持土壤湿润','山泉水','8m³/亩',5,'张大山',NULL,'APPROVED',NULL),
(10,'AG20260605010','FERTILIZE','2026-05-20','追施平衡型水溶肥','平衡型水溶肥','4kg/亩',5,'张大山',NULL,'PENDING',NULL),
(11,'AG20260608011','SOIL','2026-04-22','整地做畦，施基肥','商品有机肥','180kg/亩',5,'张大山',NULL,'APPROVED','记录属实'),
(11,'AG20260608011','SOWING','2026-04-25','直播小白菜','上海青种子','0.5kg/亩',5,'张大山',NULL,'APPROVED',NULL),
(11,'AG20260608011','IRRIGATE','2026-05-10','早晚滴灌','山泉水','10m³/亩',5,'张大山',NULL,'APPROVED',NULL),
(11,'AG20260608011','WEED','2026-05-25','人工除草','—','—',5,'张大山',NULL,'PENDING',NULL),
(12,'AG20260610012','SOIL','2026-03-22','果园扩穴改土','羊粪有机肥','350kg/亩',6,'李桂花',NULL,'APPROVED','记录属实'),
(12,'AG20260610012','SOWING','2026-03-25','补植脐橙苗 80 株','嫁接苗','80株',6,'李桂花',NULL,'APPROVED',NULL),
(12,'AG20260610012','IRRIGATE','2026-04-30','滴灌补水','水库引水','12m³/亩',6,'李桂花',NULL,'APPROVED',NULL),
(12,'AG20260610012','PEST','2026-05-18','释放天敌昆虫防治红蜘蛛','捕食螨','1500只/亩',6,'李桂花',NULL,'APPROVED',NULL);

-- 采收记录
INSERT INTO `biz_harvest_record`
(`batch_id`,`batch_no`,`harvest_date`,`quantity`,`unit`,`quality_level`,`weather`,`operator_id`,`operator_name`,`remark`,`audit_status`,`audit_remark`)
VALUES
(1,'AG20260512001','2026-05-10',1200.00,'kg','A','晴 24℃',5,'张大山','清晨采收，预冷后装筐','APPROVED','数量与地块产量相符'),
(2,'AG20260515002','2026-05-14',600.00,'kg','B','多云 26℃',5,'张大山','清晨采收，装筐入库待检','APPROVED','数量属实'),
(4,'AG20260518004','2026-05-16',800.00,'kg','A','晴 25℃',5,'张大山','清晨采收，冷链预冷 2 小时','APPROVED','数量与地块产量相符'),
(5,'AG20260520005','2026-05-18',500.00,'kg','A','阴 23℃',5,'张大山','清晨采收，去除黄叶','APPROVED','数量属实'),
(6,'AG20260522006','2026-05-20',2000.00,'kg','B','晴 28℃',6,'李桂花','分批采收，果实着色均匀','APPROVED','数量属实'),
(7,'AG20260525007','2026-05-23',650.00,'kg','A','晴 26℃',5,'张大山','清晨采收，预冷后装筐','APPROVED','数量属实'),
(8,'AG20260528008','2026-05-26',700.00,'kg','A','多云 24℃',5,'张大山','清晨采收，装筐入库','APPROVED','数量属实'),
(9,'AG20260602009','2026-06-01',1500.00,'kg','B','晴 29℃',6,'李桂花','分批采收，堆放待送检','APPROVED','数量属实');

-- 质检报告（1 合格批次 4 个、异常批次 2 个、待售批次各 1 个）
INSERT INTO `biz_quality_report`
(`id`,`batch_id`,`batch_no`,`report_no`,`inspect_org`,`inspector_id`,`inspector_name`,`inspect_date`,`inspect_result`,`standard_no`,`items_json`,`conclusion`,`file_name`,`file_hash`,`file_size`)
VALUES
(1,1,'AG20260512001','JC20260512-001','闽侯县农产品质量检测中心',7,'周敏','2026-05-12','PASS','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.008","limit":"0.02","unit":"mg/kg","result":"合格"},{"item":"铅","value":"0.05","limit":"0.1","unit":"mg/kg","result":"合格"},{"item":"镉","value":"0.01","limit":"0.05","unit":"mg/kg","result":"合格"}]',
 '所检项目符合 GB 2763-2021 限量要求，判定合格','JC20260512-001_红颜草莓_质检报告.pdf','85edf9559ed2da99f34759f2ebb24cd6ac2555c8f0da0634c26b313acc3586c6',31230),
(2,2,'AG20260515002','JC20260515-002','闽侯县农产品质量检测中心',7,'周敏','2026-05-15','FAIL','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.061","limit":"0.02","unit":"mg/kg","result":"不合格"},{"item":"铅","value":"0.04","limit":"0.1","unit":"mg/kg","result":"合格"}]',
 '毒死蜱残留超标，判定不合格，禁止销售','JC20260515-002_小白菜_质检报告.pdf','bc22b7400e439f5e31b0fbe9a03cafc97190b8eaec6ffb0d1ac35cd261ccfe87',32402),
(3,4,'AG20260518004','JC20260517-003','闽侯县农产品质量检测中心',7,'周敏','2026-05-17','PASS','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.010","limit":"0.02","unit":"mg/kg","result":"合格"},{"item":"镉","value":"0.02","limit":"0.05","unit":"mg/kg","result":"合格"}]',
 '所检项目符合限量要求，判定合格','JC20260517-003_红颜草莓_质检报告.pdf',NULL,NULL),
(4,5,'AG20260520005','JC20260519-004','闽侯县农产品质量检测中心',7,'周敏','2026-05-19','PASS','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.012","limit":"0.02","unit":"mg/kg","result":"合格"},{"item":"铅","value":"0.03","limit":"0.1","unit":"mg/kg","result":"合格"}]',
 '所检项目符合限量要求，判定合格','JC20260519-004_小白菜_质检报告.pdf',NULL,NULL),
(5,6,'AG20260522006','JC20260522-005','闽侯县农产品质量检测中心',7,'周敏','2026-05-22','FAIL','GB 2762-2022',
 '[{"item":"镉","value":"0.086","limit":"0.05","unit":"mg/kg","result":"不合格"},{"item":"铅","value":"0.07","limit":"0.1","unit":"mg/kg","result":"合格"}]',
 '镉含量超出 GB 2762-2022 限量值，判定不合格','JC20260522-005_脐橙_质检报告.pdf','848f75d007af1cb57cc06e251dc34891199a7bfd1786f943b37622e0ca80af2d',31895),
(6,7,'AG20260525007','JC20260525-006','闽侯县农产品质量检测中心',7,'周敏','2026-05-25','PASS','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.005","limit":"0.02","unit":"mg/kg","result":"合格"},{"item":"镉","value":"0.01","limit":"0.05","unit":"mg/kg","result":"合格"}]',
 '所检项目符合限量要求，判定合格','JC20260525-006_红颜草莓_质检报告.pdf',NULL,NULL),
(7,8,'AG20260528008','JC20260528-007','闽侯县农产品质量检测中心',7,'周敏','2026-05-28','PASS','GB 2763-2021',
 '[{"item":"毒死蜱","value":"0.014","limit":"0.02","unit":"mg/kg","result":"合格"},{"item":"铅","value":"0.04","limit":"0.1","unit":"mg/kg","result":"合格"}]',
 '所检项目符合限量要求，判定合格','JC20260528-007_小白菜_质检报告.pdf',NULL,NULL);

-- 物流交接记录
INSERT INTO `biz_logistics_record`
(`batch_id`,`batch_no`,`logistics_no`,`from_org`,`to_org`,`carrier_id`,`carrier_name`,`vehicle_no`,`driver_name`,`driver_phone`,`depart_time`,`arrive_time`,`temperature`,`status`,`remark`)
VALUES
(1,'AG20260512001','YD20260512001','溪头村果蔬专业合作社','永辉超市闽侯旗舰店',8,'福州鲜达冷链物流有限公司','闽A·8K521','郑海','13900001111','2026-05-12 06:30:00','2026-05-12 09:10:00','2~8℃','ARRIVED','全程冷链，交接单已签字'),
(4,'AG20260518004','YD20260518004','溪头村果蔬专业合作社','永辉超市闽侯旗舰店',8,'福州鲜达冷链物流有限公司','闽A·8K521','郑海','13900001111','2026-05-18 06:40:00','2026-05-18 09:05:00','2~8℃','ARRIVED','全程冷链，交接单已签字'),
(5,'AG20260520005','YD20260520005','溪头村果蔬专业合作社','闽侯甘蔗农产品批发市场',8,'福州鲜达冷链物流有限公司','闽A·3T118','林强','13900002222','2026-05-20 05:50:00','2026-05-20 07:30:00','4~10℃','ARRIVED','常温短驳，交接完成'),
(8,'AG20260528008','YD20260528008','溪头村果蔬专业合作社','永辉超市闽侯旗舰店',8,'福州鲜达冷链物流有限公司','闽A·3T118','林强','13900002222','2026-05-28 06:10:00','2026-05-28 08:40:00','4~10℃','ARRIVED','交接完成，无破损');

-- 销售记录（SOLD 批次累计销量等于批次数量，ON_SALE 批次为部分售出）
INSERT INTO `biz_sales_record`
(`batch_id`,`batch_no`,`seller_id`,`seller_name`,`market_name`,`sales_date`,`price`,`quantity`,`unit`,`buyer_name`)
VALUES
(1,'AG20260512001',9,'永辉超市闽侯旗舰店','永辉超市闽侯旗舰店','2026-05-13',39.90,900.00,'kg','门店零售'),
(1,'AG20260512001',9,'永辉超市闽侯旗舰店','福州大学城社区团购','2026-05-14',36.50,300.00,'kg','大学城社区团购'),
(4,'AG20260518004',9,'永辉超市闽侯旗舰店','永辉超市闽侯旗舰店','2026-05-19',42.00,800.00,'kg','门店零售'),
(5,'AG20260520005',9,'永辉超市闽侯旗舰店','闽侯甘蔗农产品批发市场','2026-05-21',6.80,300.00,'kg','批发档口'),
(5,'AG20260520005',9,'永辉超市闽侯旗舰店','永辉超市闽侯旗舰店','2026-05-21',9.90,200.00,'kg','门店零售'),
(8,'AG20260528008',9,'永辉超市闽侯旗舰店','永辉超市闽侯旗舰店','2026-05-29',8.50,250.00,'kg','门店零售');

-- 异常处置任务（2 个异常批次各 1 条）
INSERT INTO `biz_abnormal_disposal`
(`batch_id`,`batch_no`,`source_type`,`source_id`,`abnormal_type`,`description`,`disposal_measure`,`disposal_result`,`handler_id`,`handler_name`,`handle_time`,`status`)
VALUES
(2,'AG20260515002','QUALITY',2,'PESTICIDE','毒死蜱残留 0.061mg/kg，超出 GB 2763-2021 限量值 0.02mg/kg',
 '立即封存剩余 600kg 批次产品，禁止出库；追溯上游农事记录并对同地块产品抽样复检',
 NULL,2,'陈立','2026-05-15 15:00:00','HANDLING'),
(6,'AG20260522006','QUALITY',5,'HEAVY_METAL','镉含量 0.086mg/kg，超出 GB 2762-2022 限量值 0.05mg/kg',
 '封存全部 2000kg 产品并暂停该果园采收；排查肥料来源，对同批次地块土壤取样送检',
 '已完成封存，土壤取样复检中，暂缓该果园下一批次建档',2,'陈立','2026-05-23 10:30:00','DONE');


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

-- -------------------------------------------------------------
-- 建档时间分布：分散到最近 7 天，使看板「近 7 日批次趋势」有实际曲线
-- 使用相对时间，保证任何日期导入后统计图都有数据
-- -------------------------------------------------------------
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 6 DAY) WHERE `id` = 10;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 6 DAY) WHERE `id` = 11;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 5 DAY) WHERE `id` = 9;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 5 DAY) WHERE `id` = 3;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 4 DAY) WHERE `id` = 7;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE `id` = 12;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE `id` = 8;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 2 DAY) WHERE `id` = 5;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE `id` = 2;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE `id` = 6;
UPDATE `biz_batch` SET `create_time` = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE `id` = 4;
