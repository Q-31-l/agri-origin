-- ---------------------------------------------------------------------------
-- 行政村补「经纬度」字段，用于注册页"按定位推荐最近村庄"
--
-- 背景：注册要求农户/生产主体必须选一个所属村，但自助注册的人不一定知道
-- 自己的村在系统里叫什么。给村补上村部经纬度后，注册页优先用设备定位算出
-- 最近的村自动填入；定位失败再退到手动选择或自填。这比"上来就让人翻列表"
-- 更接近真实使用。
--
-- 坐标系：GCJ02（国内地图标准），与前端 utils/geo.js 统一。
-- ---------------------------------------------------------------------------

ALTER TABLE `base_village`
  ADD COLUMN `lat` DOUBLE DEFAULT NULL COMMENT '村委会纬度（GCJ02）' AFTER `address`,
  ADD COLUMN `lng` DOUBLE DEFAULT NULL COMMENT '村委会经度（GCJ02）' AFTER `lat`;

-- 闽侯县白沙镇两个村的村部近似坐标（演示数据，GCJ02）
UPDATE `base_village` SET `lat` = 26.196000, `lng` = 119.113000 WHERE `id` = 1;
UPDATE `base_village` SET `lat` = 26.203000, `lng` = 119.126000 WHERE `id` = 2;
