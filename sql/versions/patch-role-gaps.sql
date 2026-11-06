-- =============================================================
-- 角色可用性补丁：修掉"有权限没入口 / 有页面没权限"两类缺口
--
-- 背景：把 9 个角色逐个登录、遍历全部可见菜单后发现，绝大多数页面正常，
-- 但有两处是真断的：
--
--   1) 农户点批次详情的「一致性校验」→ 403
--      原因：FARMER 的矩阵里没有 chain:verify，而按钮没做权限隐藏。
--      选择授权而非隐藏：农户是批次第一责任人，能自证"我的记录真在链上"
--      是该角色的核心诉求。越权风险已封死——
--        /batch/{id}/verify      走 getAuthorized(id)，按 farmer_id 收窄
--        /chain/verify/{batchNo} 走 getAuthorizedByNo(batchNo)，本次新增
--
--   2) 生产主体打开「农场档案」→ 403 /village/list
--      原因：农场必须选所属行政村，但 PRODUCER 没有 village:list。
--      行政村只是 id + 名称的公共字典，无敏感性，应当授予。
--
-- 另配套的后端改动（不在本文件）：
--   - 新增 GET /system/user/options（权限 farm:list），只返回 id/姓名/所属村。
--     原先农场页用 /user/list 拉农户下拉，那需要 user:list，会把手机号
--     一并暴露给生产主体；改成最小字段集接口后两边都干净。
--
-- 幂等：uk_role_perm 唯一索引 + INSERT IGNORE，可重复执行。
-- =============================================================

INSERT IGNORE INTO `sys_role_permission` (`role_code`, `perm_code`) VALUES
  ('FARMER',   'chain:verify'),
  ('PRODUCER', 'village:list');

-- 校验：确认上述两条已生效
SELECT rp.role_code, rp.perm_code
FROM `sys_role_permission` rp
WHERE (rp.role_code = 'FARMER'   AND rp.perm_code = 'chain:verify')
   OR (rp.role_code = 'PRODUCER' AND rp.perm_code = 'village:list');
