#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""agri-origin 全角色全流程真实上链验收脚本（在 ECS 本机执行）"""
import base64
import json
import sys
import time
import datetime as dt

import requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session()
S.trust_env = False  # 忽略 http_proxy，避免被环境代理劫持

RESULTS = []
TOKENS = {}


def login(user, pwd="123456"):
    r = S.post(BASE + "/auth/login", json={"username": user, "password": pwd}, timeout=15)
    j = r.json()
    if j.get("code") != 0:
        raise RuntimeError("登录失败 %s: %s" % (user, j))
    TOKENS[user] = j["data"]["token"]
    return TOKENS[user]


def call(method, path, user=None, expect=0, **kw):
    headers = {}
    if user:
        headers["Authorization"] = "Bearer " + TOKENS[user]
    kw.setdefault("timeout", 60)
    r = S.request(method, BASE + path, headers=headers, **kw)
    try:
        j = r.json()
    except Exception:
        j = {"raw": r.text[:200]}
    return r.status_code, j


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else "  -> " + str(detail)))
    return ok


def today():
    return dt.date.today().isoformat()


def now_str():
    return dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def upload(user, text):
    headers = {"Authorization": "Bearer " + TOKENS[user]}
    # 1x1 PNG，用作"现场照片"占位；服务端只接受图片/视频/PDF
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
    files = {"file": ("e2e-%d.png" % int(time.time() * 1000), png, "image/png")}
    r = S.post(BASE + "/file/upload", headers=headers, files=files, timeout=30)
    j = r.json()
    if j.get("code") != 0:
        raise RuntimeError("上传失败: %s" % j)
    return j["data"]


def batch_of(user, bid):
    _, j = call("GET", "/batch/%d" % bid, user)
    return j.get("data", {}).get("batch", {})


def main():
    suffix = str(int(time.time()))[-6:]
    print("=== 0. 八个角色登录 ===")
    for u in ["admin", "producer01", "farmer01", "village01", "inspector01",
              "logistics01", "seller01", "regulator01"]:
        login(u)
        print("  ok %s" % u)

    print("\n=== 1. 链状态 ===")
    st, j = call("GET", "/chain/status", "admin")
    chain = j.get("data", {})
    start_block = chain.get("blockNumber") or 0
    check("链已就绪", chain.get("ready") is True, chain)
    check("合约地址已配置", str(chain.get("contractAddress", "")).startswith("0x"), chain.get("contractAddress"))

    print("\n=== 2. 正向链路：建档 → 核验 → 农事 → 采收 → 质检 → 物流 → 销售 ===")
    st, j = call("POST", "/batch", "producer01", json={
        "productId": 1, "farmId": 1, "farmerId": 5,
        "plantingDate": today(), "quantity": 300, "unit": "kg",
        "remark": "E2E 验收批次 " + suffix})
    if not check("创建批次", j.get("code") == 0, j):
        return finish()
    bid = j["data"]
    b = batch_of("producer01", bid)
    check("批次建档已上链", b.get("chainStatus") == "SUCCESS" and (b.get("blockNumber") or 0) > 0,
          "chainStatus=%s block=%s tx=%s" % (b.get("chainStatus"), b.get("blockNumber"), b.get("chainTxHash")))
    batch_no, trace_code = b.get("batchNo"), b.get("traceCode")

    st, j = call("POST", "/batch/%d/audit" % bid, "village01", json={"auditStatus": "APPROVED", "auditRemark": "E2E 核验通过"})
    check("村级核验通过", j.get("code") == 0, j)

    f_plant = upload("farmer01", "E2E 农事现场照片")
    f_harvest = upload("farmer01", "E2E 采收现场照片")
    st, j = call("POST", "/planting", "farmer01", json={
        "batchId": bid, "recordType": "FERTILIZE", "operationDate": today(),
        "content": "E2E 追肥 5kg/亩", "materialName": "平衡水溶肥", "dosage": "5kg/亩",
        "evidenceIds": [f_plant["id"]]})
    planting_id = j.get("data")
    check("农事记录上链", j.get("code") == 0, j)
    st, j = call("GET", "/planting/batch/%d" % bid, "farmer01")
    pl = (j.get("data") or [{}])[0] if j.get("data") else {}
    check("农事记录带真实交易哈希", pl.get("chainStatus") == "SUCCESS" and str(pl.get("chainTxHash", "")).startswith("0x"),
          "chainStatus=%s tx=%s block=%s" % (pl.get("chainStatus"), pl.get("chainTxHash"), pl.get("blockNumber")))

    # 重复提交应 409
    st, j = call("POST", "/planting", "farmer01", json={
        "batchId": bid, "recordType": "FERTILIZE", "operationDate": today(), "content": "重复提交"})
    check("重复农事被拦截(409)", st == 409, "http=%s code=%s" % (st, j.get("code")))

    st, j = call("POST", "/harvest", "farmer01", json={
        "batchId": bid, "harvestDate": today(), "quantity": 300, "unit": "kg",
        "qualityLevel": "A", "weather": "晴", "evidenceIds": [f_harvest["id"]]})
    check("采收登记上链", j.get("code") == 0, j)
    hid = j.get("data")
    b = batch_of("producer01", bid)
    check("采收后批次转为 HARVESTED", b.get("status") == "HARVESTED", b.get("status"))

    qf = upload("inspector01", "E2E 质检报告 PASS")
    st, j = call("POST", "/quality", "inspector01", json={
        "batchId": bid, "reportNo": "E2E-PASS-" + suffix, "inspectDate": today(),
        "inspectResult": "PASS", "standardNo": "GB 2763-2021",
        "conclusion": "农残与重金属项目全部合格", "fileName": qf["fileName"],
        "filePath": qf["filePath"], "fileHash": qf["fileHash"], "fileSize": qf["fileSize"],
        "evidenceIds": [qf["id"]]})
    qid = j.get("data")
    check("质检报告上链", j.get("code") == 0, j)
    b = batch_of("producer01", bid)
    check("质检合格后批次转 QUALIFIED", b.get("status") == "QUALIFIED", b.get("status"))

    f_logi = upload("logistics01", "E2E 物流交接单")
    st, j = call("POST", "/logistics", "logistics01", json={
        "batchId": bid, "logisticsNo": "E2E-LG-" + suffix, "fromOrg": "溪头村果蔬专业合作社",
        "toOrg": "永辉超市闽侯旗舰店", "vehicleNo": "闽A88888", "driverName": "郑海",
        "driverPhone": "13800000008", "departTime": now_str(), "temperature": "4℃",
        "status": "DEPARTED", "evidenceIds": [f_logi["id"]]})
    lid = j.get("data")
    check("物流交接上链", j.get("code") == 0, j)
    b = batch_of("producer01", bid)
    check("发运后批次转 IN_TRANSIT", b.get("status") == "IN_TRANSIT", b.get("status"))

    f_sale = upload("seller01", "E2E 销售凭证")
    st, j = call("POST", "/sales", "seller01", json={
        "batchId": bid, "marketName": "永辉超市闽侯旗舰店", "salesDate": today(),
        "price": 8.5, "quantity": 100, "unit": "kg", "buyerName": "散客",
        "evidenceIds": [f_sale["id"]]})
    sid = j.get("data")
    check("销售登记上链", j.get("code") == 0, j)
    b = batch_of("producer01", bid)
    check("部分售出后批次转 ON_SALE", b.get("status") == "ON_SALE", b.get("status"))

    print("\n=== 3. 监管复核（质检/物流/销售三条审核链） ===")
    for name, path in [("质检", "/quality/%d/audit" % qid),
                       ("物流", "/logistics/%d/audit" % lid),
                       ("销售", "/sales/%d/audit" % sid)]:
        st, j = call("POST", path, "regulator01", json={"auditStatus": "APPROVED", "auditRemark": "E2E 监管复核通过"})
        check("%s记录监管复核" % name, j.get("code") == 0, j)

    print("\n=== 4. 消费者溯源（免登录） ===")
    r = S.get(BASE + "/trace/" + trace_code, timeout=20)
    tj = r.json()
    data = tj.get("data", {})
    timeline = data.get("timeline") or []
    trust = data.get("trust") or {}
    check("溯源免登录可查", tj.get("code") == 0, tj.get("message"))
    check("时间轴节点齐全(>=6)", len(timeline) >= 6, "节点数=%d" % len(timeline))
    chained = [t for t in timeline if str(t.get("txHash", "")).startswith("0x")]
    check("时间轴带链上凭证", len(chained) >= 5, "带 txHash 节点=%d" % len(chained))
    check("可信度评分已生成", trust.get("grade") is not None, trust)
    print("     可信度=%s/100 等级=%s(%s)" % (trust.get("score"), trust.get("grade"), trust.get("gradeText")))

    print("\n=== 5. 链上链下一致性校验 ===")
    st, j = call("GET", "/chain/verify/" + batch_no, "regulator01")
    v = j.get("data", {})
    check("一致性校验通过", v.get("passed") is True, v)

    print("\n=== 6. 更正留痕 ===")
    f_corr = upload("producer01", "E2E 更正佐证")
    st, j = call("POST", "/correction", "producer01", json={
        "batchId": bid, "bizType": "PLANTING", "bizId": planting_id,
        "reason": "投入品用量录入有误", "content": "实际用量应为 4kg/亩", "evidenceIds": [f_corr["id"]]})
    check("更正说明上链", j.get("code") == 0, j)
    r = S.get(BASE + "/trace/" + trace_code, timeout=20)
    tl2 = r.json()["data"]["timeline"]
    check("溯源时间轴出现更正节点", any(t.get("type") == "CORRECTION" for t in tl2),
          [t.get("type") for t in tl2])

    print("\n=== 7. 异常链路：质检不合格 → 熔断 → 拦截销售 → 处置 → 结案 ===")
    st, j = call("POST", "/batch", "producer01", json={
        "productId": 2, "farmId": 1, "farmerId": 5,
        "plantingDate": today(), "quantity": 200, "remark": "E2E 异常批次 " + suffix})
    bid2 = j.get("data")
    check("创建异常测试批次", j.get("code") == 0, j)
    b2 = batch_of("producer01", bid2)
    call("POST", "/batch/%d/audit" % bid2, "village01", json={"auditStatus": "APPROVED"})
    st, j = call("POST", "/harvest", "farmer01", json={
        "batchId": bid2, "harvestDate": today(), "quantity": 200, "qualityLevel": "B"})
    check("异常批次采收登记", j.get("code") == 0, j)

    qf2 = upload("inspector01", "E2E 质检报告 FAIL")
    st, j = call("POST", "/quality", "inspector01", json={
        "batchId": bid2, "reportNo": "E2E-FAIL-" + suffix, "inspectDate": today(),
        "inspectResult": "FAIL", "standardNo": "GB 2763-2021",
        "conclusion": "毒死蜱残留 0.061mg/kg，超出限量值 0.02mg/kg",
        "fileName": qf2["fileName"], "filePath": qf2["filePath"],
        "fileHash": qf2["fileHash"], "fileSize": qf2["fileSize"], "evidenceIds": [qf2["id"]]})
    check("不合格报告上链", j.get("code") == 0, j)
    b2 = batch_of("producer01", bid2)
    check("不合格批次自动熔断 ABNORMAL", b2.get("status") == "ABNORMAL", b2.get("status"))

    f_sale = upload("seller01", "E2E 销售凭证")
    st, j = call("POST", "/sales", "seller01", json={
        "batchId": bid2, "marketName": "永辉超市", "salesDate": today(),
        "price": 9.9, "quantity": 10, "unit": "kg"})
    check("异常批次销售被拦截", j.get("code") != 0 and st >= 400, "http=%s msg=%s" % (st, j.get("message")))

    st, j = call("GET", "/abnormal/page?page=1&size=50", "regulator01")
    items = j.get("data", {}).get("records", [])
    target = [x for x in items if x.get("batchId") == bid2]
    if not check("生成待处置任务", len(target) == 1, "匹配=%d" % len(target)):
        return finish()
    did = target[0]["id"]
    check("待处置任务已上链(链上异常标记)", target[0].get("chainStatus") == "SUCCESS",
          "chainStatus=%s tx=%s" % (target[0].get("chainStatus"), target[0].get("chainTxHash")))

    f_disp = upload("regulator01", "E2E 处置现场照片")
    st, j = call("POST", "/abnormal/%d/dispose" % did, "regulator01", json={
        "disposalMeasure": "封存全部产品并暂停采收", "disposalResult": "已封存，复检合格后解封",
        "evidenceIds": [f_disp["id"]]})
    check("异常处置上链", j.get("code") == 0, j)

    st, j = call("GET", "/abnormal/%d/trace" % did, "regulator01")
    tvo = j.get("data", {})
    check("上下游追溯可用", j.get("code") == 0 and tvo.get("batch") is not None,
          "upstream=%d" % len(tvo.get("upstream") or []))

    st, j = call("POST", "/abnormal/batch/%d/close" % bid2, "regulator01")
    check("异常批次结案上链", j.get("code") == 0, j)
    b2 = batch_of("regulator01", bid2)
    check("结案后批次状态 CLOSED", b2.get("status") == "CLOSED", b2.get("status"))

    print("\n=== 8. 越权与权限负例 ===")
    st, j = call("POST", "/quality", "farmer01", json={
        "batchId": bid, "reportNo": "X", "inspectDate": today(), "inspectResult": "PASS"})
    check("农户调质检接口被拦截(403)", st == 403, "http=%s" % st)

    st, j = call("GET", "/user/page?page=1&size=5", "farmer01")
    check("农户查用户列表被拦截(403)", st == 403, "http=%s" % st)

    st, j = call("GET", "/batch/page?page=1&size=50&villageId=2", "admin")
    others = (j.get("data", {}).get("records") or [])
    if others:
        st, j = call("GET", "/batch/%d" % others[0]["id"], "farmer01")
        check("农户读他人批次被拦截(403)", st == 403, "http=%s" % st)

    if others:
        st, j = call("POST", "/batch/%d/audit" % others[0]["id"], "village01",
                     json={"auditStatus": "APPROVED"})
        check("村管核验其他村批次被拦截(403)", st == 403, "http=%s" % st)

    st, j = call("GET", "/chain/page?page=1&size=5", "producer01")
    check("生产主体查存证流水被拦截(403)", st == 403, "http=%s" % st)

    st, j = call("POST", "/chain/repair/batches", "seller01")
    check("销售商调补注册接口被拦截(403)", st == 403, "http=%s" % st)

    st, j = call("POST", "/chain/repair/batches", "regulator01")
    check("监管方可执行存量补注册", j.get("code") == 0, j)

    print("\n=== 9. 数据范围隔离 ===")
    for u, label in [("farmer01", "农户"), ("producer01", "生产主体"),
                     ("village01", "村管"), ("regulator01", "监管")]:
        st, j = call("GET", "/batch/page?page=1&size=1", u)
        tot = j.get("data", {}).get("total")
        st2, j2 = call("GET", "/stats/overview", u)
        stat = j2.get("data", {}).get("totalBatches")
        check("%s 看板与列表口径一致" % label, tot == stat, "list=%s stats=%s" % (tot, stat))

    print("\n=== 10. 结束区块高度 ===")
    st, j = call("GET", "/chain/status", "admin")
    end_block = j.get("data", {}).get("blockNumber") or 0
    check("区块高度递增(全程真实出块)", end_block > start_block,
          "%s -> %s（新增 %s 个区块）" % (start_block, end_block, end_block - start_block))

    return finish()


def finish():
    print("\n" + "=" * 62)
    total = len(RESULTS)
    passed = sum(1 for _, ok, _ in RESULTS if ok)
    print("验收结果：%d/%d 通过" % (passed, total))
    failed = [r for r in RESULTS if not r[1]]
    if failed:
        print("失败项：")
        for n, _, d in failed:
            print("  - %s -> %s" % (n, d))
    print("=" * 62)
    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
