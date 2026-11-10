#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""管理员撤销能力验证：逆序保护、状态回退、存证校验不受影响（在 ECS 本机执行）"""
import base64
import datetime as dt
import sys
import time

import requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session()
S.trust_env = False

PASS, FAIL = [], []
TOKENS = {}


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append(name)
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (("  -> " + str(detail)) if detail and not ok else ""))
    return ok


def login(user, pwd="123456"):
    r = S.post(BASE + "/auth/login", json={"username": user, "password": pwd}, timeout=15)
    j = r.json()
    if j.get("code") != 0:
        raise RuntimeError("登录失败 %s: %s" % (user, j))
    TOKENS[user] = j["data"]["token"]
    return TOKENS[user]


def call(method, path, user=None, **kw):
    headers = {"Authorization": "Bearer " + TOKENS[user]} if user else {}
    kw.setdefault("timeout", 90)
    r = S.request(method, BASE + path, headers=headers, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:200]}


def today():
    return dt.date.today().isoformat()


def upload(user, text):
    headers = {"Authorization": "Bearer " + TOKENS[user]}
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
    files = {"file": ("rv-%d.png" % int(time.time() * 1000), png, "image/png")}
    r = S.post(BASE + "/file/upload", headers=headers, files=files, timeout=30)
    j = r.json()
    if j.get("code") != 0:
        raise RuntimeError("上传失败: %s" % j)
    return j["data"]


def batch_of(user, bid):
    _, j = call("GET", "/batch/%d" % bid, user)
    return (j.get("data") or {}).get("batch", {})


def main():
    suffix = str(int(time.time()))[-6:]
    for u in ["admin", "producer01", "farmer01", "village01",
              "inspector01", "regulator01"]:
        login(u)

    print("=== 1. 准备：建批次并走到质检合格 ===")
    st, j = call("POST", "/batch", "producer01", json={
        "productId": 1, "farmId": 1, "farmerId": 5,
        "plantingDate": today(), "quantity": 300, "unit": "kg",
        "remark": "撤销验证批次 " + suffix})
    if not check("创建批次", j.get("code") == 0, j):
        return finish()
    bid = j["data"]
    bno = batch_of("producer01", bid).get("batchNo")
    print("  批次号 %s (id=%s)" % (bno, bid))

    st, j = call("POST", "/batch/%d/audit" % bid, "village01",
                 json={"auditStatus": "APPROVED", "auditRemark": "撤销验证核验"})
    check("村级核验", j.get("code") == 0, j)

    f1 = upload("farmer01", "农事照片")
    st, j = call("POST", "/planting", "farmer01", json={
        "batchId": bid, "recordType": "FERTILIZE", "operationDate": today(),
        "content": "撤销验证 追肥", "materialName": "水溶肥", "dosage": "3kg/亩",
        "evidenceIds": [f1["id"]]})
    pid = j.get("data")
    check("提交农事", j.get("code") == 0, j)
    st, j = call("POST", "/planting/%d/audit" % pid, "village01",
                 json={"auditStatus": "APPROVED", "auditRemark": "撤销验证农事通过"})
    check("农事审核通过", j.get("code") == 0, j)

    f2 = upload("farmer01", "采收照片")
    st, j = call("POST", "/harvest", "farmer01", json={
        "batchId": bid, "harvestDate": today(), "quantity": 300, "unit": "kg",
        "qualityLevel": "A", "weather": "晴", "evidenceIds": [f2["id"]]})
    hid = j.get("data")
    check("提交采收", j.get("code") == 0, j)
    check("采收提交后批次仍为种植中（状态只在审核通过时推进）",
          batch_of("producer01", bid).get("status") == "PLANTING",
          batch_of("producer01", bid).get("status"))

    st, j = call("POST", "/harvest/%d/audit" % hid, "village01",
                 json={"auditStatus": "APPROVED", "auditRemark": "撤销验证采收通过"})
    check("采收审核通过", j.get("code") == 0, j)
    check("批次转为已采收", batch_of("producer01", bid).get("status") == "HARVESTED",
          batch_of("producer01", bid).get("status"))

    qf = upload("inspector01", "质检报告")
    st, j = call("POST", "/quality", "inspector01", json={
        "batchId": bid, "reportNo": "RV-" + suffix, "inspectDate": today(),
        "inspectResult": "PASS", "standardNo": "GB 2763-2021",
        "conclusion": "撤销验证 全部合格", "fileName": qf["fileName"],
        "filePath": qf["filePath"], "fileHash": qf["fileHash"], "fileSize": qf["fileSize"],
        "evidenceIds": [qf["id"]]})
    qid = j.get("data")
    check("上传质检报告", j.get("code") == 0, j)
    st, j = call("POST", "/quality/%d/audit" % qid, "regulator01",
                 json={"auditStatus": "APPROVED", "auditRemark": "撤销验证质检复核"})
    check("质检复核通过", j.get("code") == 0, j)
    check("批次转为质检合格", batch_of("producer01", bid).get("status") == "QUALIFIED",
          batch_of("producer01", bid).get("status"))

    st, v0 = call("GET", "/chain/verify/%s" % bno, "admin")
    total0 = (v0.get("data") or {}).get("totalRecords")
    check("撤销前一致性校验通过", (v0.get("data") or {}).get("passed") is True, v0.get("data", {}).get("message"))

    print("\n=== 2. 逆序保护：还有质检时不得撤采收 ===")
    st, j = call("POST", "/admin/revoke", "admin", json={
        "bizType": "HARVEST", "bizId": hid, "reason": "试图越级撤销"})
    check("存在下游质检时拒绝撤销采收", j.get("code") != 0, j)
    check("拒绝原因可读", "质检" in str(j.get("message", "")), j.get("message"))

    print("\n=== 3. 撤销质检 → 批次回退到已采收 ===")
    st, j = call("POST", "/admin/revoke", "admin", json={
        "bizType": "QUALITY", "bizId": qid, "reason": "报告为非本批次作物", "withEvidence": True})
    check("撤销质检记录", j.get("code") == 0, j)
    check("批次回退为已采收", batch_of("producer01", bid).get("status") == "HARVESTED",
          batch_of("producer01", bid).get("status"))

    st, j = call("GET", "/quality/page?batchId=%d" % bid, "admin")
    ids = [r.get("id") for r in ((j.get("data") or {}).get("records") or [])]
    check("质检列表不再显示已撤销记录", qid not in ids, ids)

    print("\n=== 4. 证据撤销后不再可下载 ===")
    st, _ = call("GET", "/file/download/%s" % qf["id"], "admin")
    check("已撤销证据禁止下载(403)", st == 403, "http=%s" % st)

    st, j = call("GET", "/file/evidence?bizType=QUALITY&bizId=%d" % qid, "admin")
    check("证据接口不再返回已撤销文件", len(j.get("data") or []) == 0, j.get("data"))

    print("\n=== 5. 撤销采收 → 批次回退到种植中 ===")
    st, j = call("POST", "/admin/revoke", "admin", json={
        "bizType": "HARVEST", "bizId": hid, "reason": "采收日期与实际不符", "withEvidence": False})
    check("撤销采收记录", j.get("code") == 0, j)
    b = batch_of("producer01", bid)
    check("批次回退为种植中", b.get("status") == "PLANTING", b.get("status"))
    check("采收日期已清空", not b.get("harvestDate"), b.get("harvestDate"))

    print("\n=== 6. 撤销后一致性校验仍然通过 ===")
    st, v1 = call("GET", "/chain/verify/%s" % bno, "admin")
    d1 = v1.get("data") or {}
    check("撤销后校验通过（撤销不算篡改）", d1.get("passed") is True, d1.get("message"))
    check("校验分母已剔除撤销记录", d1.get("totalRecords") == total0 - 2,
          "before=%s after=%s" % (total0, d1.get("totalRecords")))

    print("\n=== 7. 撤销后的进度与状态同源 ===")
    st, j = call("GET", "/workflow/progress/%d" % bid, "admin")
    d = j.get("data") or {}
    check("进度可读", j.get("code") == 0, j)
    check("进度的待审环节与批次状态不矛盾", d.get("percent", 0) < 100, d.get("percent"))

    print("\n=== 8. 收尾：清理验证批次 ===")
    st, j = call("POST", "/admin/revoke", "admin", json={
        "bizType": "PLANTING", "bizId": pid, "reason": "验收收尾清理", "withEvidence": True})
    check("撤销农事记录", j.get("code") == 0, j)

    print("\n===== 结果：通过 %d，失败 %d =====" % (len(PASS), len(FAIL)))
    print("CLEANUP_BATCH_ID=%s" % bid)
    if FAIL:
        for f in FAIL:
            print("  失败项：" + f)
        return 1
    return 0


def finish():
    print("\n===== 准备阶段失败 =====")
    return 1


if __name__ == "__main__":
    sys.exit(main())
