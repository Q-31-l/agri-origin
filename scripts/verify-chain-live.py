#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真实上链端到端验证。

验证链条：登录 → 取可操作批次 → 上传现场实证 → 提交农事记录 → 产生链上交易
        → 区块高度递增 → WeBASE-Front 能回读该笔交易 → 溯源页能查到这条记录

只读的部分用后端接口即可；回执一环额外走 WeBASE-Front，证明"控制台看到的
就是业务写上去的那笔交易"，而不是只在数据库里改了个状态。
"""
import base64
import datetime as dt
import json
import sys
import time

import requests

BASE = "http://127.0.0.1:8080/api"
FRONT = "http://127.0.0.1:5002/WeBASE-Front"
FRONT_AUTH = ("webase", "Agri@2026")
S = requests.Session()
S.trust_env = False

ok_count = 0
fail_count = 0


def check(name, cond, detail=""):
    global ok_count, fail_count
    if cond:
        ok_count += 1
        print("  [PASS] %-38s %s" % (name, detail))
    else:
        fail_count += 1
        print("  [FAIL] %-38s %s" % (name, detail))
    return cond


def login(user, pwd="123456"):
    j = S.post(BASE + "/auth/login", json={"username": user, "password": pwd}, timeout=15).json()
    assert j.get("code") == 0, j
    return j["data"]["token"]


print("=" * 68)
print("真实上链端到端验证  %s" % dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
print("=" * 68)

# ---------- 0. 提交前的链上基线 ----------
tok_admin = login("admin")
HA = {"Authorization": "Bearer " + tok_admin}
st0 = S.get(BASE + "/chain/status", headers=HA, timeout=20).json()["data"]
block0 = st0.get("blockNumber")
stat0 = st0.get("chainStat") or {}
print("\n[0] 链上基线：区块 %s ｜ 存证 %s 笔（成功 %s / 失败 %s / 跳过 %s）"
      % (block0, stat0.get("total"), stat0.get("success"), stat0.get("failed"), stat0.get("skipped")))
check("链服务就绪", st0.get("ready") is True, "nodeVersion=%s" % st0.get("nodeVersion"))
check("合约地址已配置", bool(st0.get("contractAddress")), str(st0.get("contractAddress"))[:24] + "…")

# ---------- 1. 以农户身份准备数据 ----------
print("\n[1] 农户 farmer01 登录")
tok = login("farmer01")
H = {"Authorization": "Bearer " + tok}
profile = S.get(BASE + "/auth/profile", headers=H, timeout=15).json()["data"]
print("    身份：%s / %s" % (profile.get("realName"), profile.get("roleName")))
check("角色为 FARMER", profile.get("roleCode") == "FARMER", profile.get("roleCode"))

opts = (S.get(BASE + "/batch/options", params={"status": "PLANTING", "usable": "true"},
              headers=H, timeout=15).json().get("data") or [])
check("存在可操作的种植中批次", len(opts) > 0, "共 %d 个" % len(opts))
if not opts:
    sys.exit(1)
b = opts[0]
bid = b.get("id")
bno = b.get("batchNo") or ""
print("    目标批次：%s（%s / %s）" % (bno, b.get("productName"), b.get("farmName") or "-"))

# ---------- 2. 上传现场实证 ----------
png = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
up = S.post(BASE + "/file/upload", headers=H,
            files={"file": ("verify-%d.png" % int(time.time() * 1000), png, "image/png")},
            timeout=30).json()
evid = up["data"]["id"] if isinstance(up.get("data"), dict) else up.get("data")
check("现场实证上传成功", up.get("code") == 0 and bool(evid), "fileId=%s" % evid)

# ---------- 3. 先验证防重：同日同批次同类型应被拦截 ----------
stamp = int(time.time())
body = {
    "batchId": bid,
    "recordType": "FERTILIZE",
    "operationDate": dt.date.today().isoformat(),
    "content": "上链真实性验证 #%d" % stamp,
    "materialName": "平衡水溶肥",
    "dosage": "4kg/亩",
    "location": "溪头村 · 基地一号地块",
    "evidenceIds": [evid],
}
print("\n[2] 防重复提交校验（业务规则，不是故障）")
dup = dict(body)
dup["content"] = "重复提交测试 #%d" % stamp
dup_res = S.post(BASE + "/planting", json=dup, headers=H, timeout=60).json()
check("同日同类农事被拦截", dup_res.get("code") != 0,
      str(dup_res.get("message"))[:32])

# ---------- 4. 换一种农事类型，真正提交一次 ----------
print("\n[3] 提交农事记录（灌溉，含现场实证）")
body["recordType"] = "IRRIGATE"
body["content"] = "上链真实性验证 · 灌溉 #%d" % stamp
body["dosage"] = "20m³/亩"
res = S.post(BASE + "/planting", json=body, headers=H, timeout=90).json()
rid = res.get("data")
if res.get("code") == 0:
    check("农事记录提交成功", True, "recordId=%s" % rid)
elif "已存在" in str(res.get("message")):
    # 同一天重复跑本脚本时该类型也已存在，退回"最近一条存证"继续验证链路
    check("农事记录提交成功", True, "今日已提交过，改用最近一条存证验证")
    rid = None
else:
    check("农事记录提交成功", False, str(res.get("message")))
    sys.exit(1)

# ---------- 5. 等待上链并核对 ----------
print("\n[4] 等待上链结果")
rec = None
for i in range(20):
    time.sleep(1.2)
    # 必须用管理员令牌查存证流水：农户没有 chain:list 权限，用农户令牌会 403
    page = S.get(BASE + "/chain/page", params={"page": 1, "size": 10, "batchNo": bno},
                 headers=HA, timeout=20).json().get("data") or {}
    rows = page.get("records") or []
    for r in rows:
        # 存证表里的 bizType 是业务类型（PLANTING），eventType 是更细的事件名
        # （PLANTING_RECORD）——按 bizType 匹配才不会漏
        if r.get("bizType") != "PLANTING":
            continue
        if rid is not None and str(r.get("bizId")) != str(rid):
            continue
        rec = r
        break
    if rec:
        break

if not rec:
    check("找到本次存证记录", False, "20s 内未查到")
    sys.exit(1)

check("找到本次存证记录", True, "id=%s" % rec.get("id"))
check("链上状态 SUCCESS", rec.get("status") == "SUCCESS", rec.get("status"))
check("有交易哈希", bool(rec.get("txHash")), str(rec.get("txHash"))[:42] + "…")
check("有区块高度", bool(rec.get("blockNumber")), "block %s" % rec.get("blockNumber"))
print("    dataHash = %s" % str(rec.get("dataHash"))[:48])

# ---------- 5. 区块高度必须递增 ----------
# 只有真正新提交了记录才要求出块；同日重跑时复用旧存证，块高自然不变
st1 = S.get(BASE + "/chain/status", headers=HA, timeout=20).json()["data"]
block1 = st1.get("blockNumber")
if rid is not None:
    check("区块高度递增", block1 is not None and block0 is not None and block1 > block0,
          "%s → %s" % (block0, block1))
else:
    check("区块高度（复用既有存证，不要求新增）", block1 is not None, "block %s" % block1)

# ---------- 6. WeBASE-Front 回读该区块 ----------
print("\n[5] WeBASE-Front 独立回读（证明链上真有这笔交易）")
try:
    r = requests.get(FRONT + "/1/web3/blockByNumber/%s" % rec.get("blockNumber"),
                     auth=FRONT_AUTH, timeout=25, verify=False)
    raw = r.json()
    # 这个接口返回的是裸区块对象（number/hash/transactions 直接在顶层），
    # 不像其它接口那样套一层 data ——要兼容两种形态
    blk = raw.get("data") if isinstance(raw.get("data"), dict) and raw.get("data") else raw
    txs = blk.get("transactions") or []
    hashes = {(x.get("hash") or x.get("transactionHash")) if isinstance(x, dict) else x for x in txs}
    found = rec.get("txHash") in hashes
    check("控制台能读到该区块", r.status_code == 200 and bool(blk.get("number")),
          "HTTP %s, block %s" % (r.status_code, blk.get("number")))
    check("区块内含本次交易哈希", found,
          "区块 %s 共 %d 笔" % (rec.get("blockNumber"), len(txs)))
except Exception as e:
    check("控制台能读到该区块", False, str(e)[:60])

# ---------- 7. 溯源页回读 ----------
print("\n[6] 溯源页回读（消费者视角）")
detail = S.get(BASE + "/batch/" + str(bid), headers=H, timeout=20).json().get("data") or {}
# 详情接口返回 {batch: {...}, plantings: [...], timeline: [...]} 的嵌套结构
batch_obj = detail.get("batch") or {}
trace_code = batch_obj.get("traceCode") or detail.get("traceCode")
if trace_code:
    t = S.get(BASE + "/trace/" + trace_code, timeout=20).json().get("data") or {}
    timeline = t.get("timeline") or []
    hit = [x for x in timeline if "上链真实性验证" in str(x.get("detail", ""))]
    check("溯源接口可匿名访问", bool(t.get("traceCode")), trace_code)
    check("时间轴包含验证农事记录", len(hit) > 0,
          "共 %d 个环节，命中 %d 条" % (len(timeline), len(hit)))
    if t.get("trust"):
        tr = t["trust"]
        print("    可信度：%s 分 / %s 级（%s）" % (tr.get("score"), tr.get("grade"), tr.get("gradeText")))
else:
    check("批次已生成溯源码", False, "traceCode 为空（该批次可能尚未销售）")

# ---------- 汇总 ----------
print("\n" + "=" * 68)
print("结果：%d 项通过，%d 项失败" % (ok_count, fail_count))
print("=" * 68)
sys.exit(0 if fail_count == 0 else 1)
