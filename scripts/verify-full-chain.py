#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全链路真实验收：五个身份自助注册 → 建地块 → 建批次 → 农事 → 采收 → 质检 → 物流 → 销售 → 扫码溯源

在服务器本机执行（BASE 默认 127.0.0.1:8080）。这条脚本要回答的问题只有一个：
"新注册的人，能不能真的把一批菜从种到卖走完，并且每一步都上链？"

注意：注册接口按 IP 限流（每小时 5 个成功账号），本脚本正好用完 5 个额度。
如果中途失败要重跑，先 `systemctl restart agri-backend` 清掉计数，再删掉 vtest 前缀账号。

用法：python3 scripts/verify-full-chain.py
"""
import base64
import json
import os
import sys
import time

import requests

BASE = os.environ.get("AGRI_BASE", "http://127.0.0.1:8080/api")
S = requests.Session()
S.trust_env = False

# 1×1 透明 PNG：够小，但确实是合法图片，能通过扩展名与摘要校验
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

results = []
TOKENS = {}
IDS = {}
TRACE_CODE = ""
BATCH_NO = ""


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else "  -> " + str(detail)[:400]))


def api(method, path, token=None, **kw):
    h = {"Authorization": "Bearer " + token} if token else {}
    r = S.request(method, BASE + path, headers=h, timeout=30, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:300]}


def upload(token, filename, content, biz_type):
    files = {"file": (filename, content, "application/octet-stream")}
    st, j = api("POST", "/file/upload", token, files=files, data={"bizType": biz_type})
    return (j.get("data") or {}) if st == 200 else {}


def register(role, username, real_name, password, **extra):
    body = {"username": username, "password": password, "realName": real_name, "roleCode": role}
    body.update(extra)
    st, j = api("POST", "/auth/register", json=body)
    data = j.get("data") or {}
    if data.get("token"):
        TOKENS[role] = data["token"]
        IDS[role] = (data.get("user") or {}).get("id")
    return st, j, data


def main():
    global TRACE_CODE, BATCH_NO
    suffix = str(int(time.time()))[-5:]
    today = time.strftime("%Y-%m-%d")

    print("\n== 1. 五个身份自助注册（溯源链上每一环） ==")
    cases = [
        ("PRODUCER", "qhhz" + suffix, "林青禾", "farm2026",
         {"orgName": "青禾家庭农场", "villageId": 1}),
        ("FARMER", "cmn" + suffix, "陈小满", "farm2026",
         {"villageId": 1}),
        ("INSPECTOR", "jczx" + suffix, "周敏", "jc2026",
         {"orgName": "闽侯县农产品质量安全检测中心"}),
        ("LOGISTICS", "wlgs" + suffix, "黄志强", "wl2026",
         {"orgName": "闽运冷链物流有限公司"}),
        ("SELLER", "xsd" + suffix, "郑晓宁", "xs2026",
         {"orgName": "永辉超市闽侯甘蔗店"}),
    ]
    for role, uname, rname, pwd, extra in cases:
        st, j, data = register(role, uname, rname, pwd, **extra)
        user = data.get("user") or {}
        check("注册 %s（%s）" % (role, rname),
              st == 200 and j.get("code") == 0 and bool(data.get("token"))
              and user.get("roleCode") == role,
              (st, j))

    # 村与机构名必须落到账号上，否则后续溯源页的责任主体是空的
    st, j = api("GET", "/auth/profile", TOKENS["PRODUCER"])
    p = j.get("data") or {}
    check("生产主体带村（%s）与机构（%s）" % (p.get("villageName"), p.get("orgName")),
          p.get("villageId") == 1 and p.get("orgName") == "青禾家庭农场", p)
    st, j = api("GET", "/auth/profile", TOKENS["INSPECTOR"])
    check("检测机构带机构名", (j.get("data") or {}).get("orgName") == "闽侯县农产品质量安全检测中心", j)

    print("\n== 2. 生产主体登记地块（只能登记在本村） ==")
    st, j = api("POST", "/farm", TOKENS["PRODUCER"], json={
        "farmName": "青禾家庭农场·溪头一号田",
        "farmerId": IDS["FARMER"],
        "villageId": 2,  # 故意传别的村，服务端应改回自己的村
        "address": "溪头村一组后山垅",
        "area": 12.5,
        "soilType": "壤土",
        "waterSource": "山泉灌溉",
    })
    farm_id = j.get("data")
    check("新注册主体可建地块", st == 200 and j.get("code") == 0, (st, j))

    st, j = api("GET", "/farm/list", TOKENS["PRODUCER"])
    farm = next((f for f in (j.get("data") or []) if f.get("id") == farm_id), None)
    check("地块被强制落在本村（溪头村）", farm and farm.get("villageName") == "溪头村", farm)

    print("\n== 3. 建批次（自动生成批次号 + 溯源码 + 上链登记） ==")
    st, j = api("POST", "/batch", TOKENS["PRODUCER"], json={
        "productId": 1,
        "farmId": farm_id,
        "plantingDate": today,
        "quantity": 600,
        "unit": "kg",
        "remark": "自助注册全链路验收：五个新账号依次录完",
    })
    # 创建接口返回的是 { batch: {...} }，与详情同构；直接取 id 会拿到一整只对象
    created = (j.get("data") or {}).get("batch") or (j.get("data") or {})
    batch_id = created.get("id")
    st2, j2 = api("GET", "/batch/%s" % batch_id, TOKENS["PRODUCER"])
    b = (j2.get("data") or {}).get("batch") or (j2.get("data") or {})
    BATCH_NO = b.get("batchNo")
    TRACE_CODE = b.get("traceCode")
    check("批次创建成功（%s）" % BATCH_NO, st == 200 and bool(BATCH_NO), (st, j2))
    check("新批次处于待核验", b.get("auditStatus") == "PENDING", b.get("auditStatus"))

    print("\n== 4. 村级核验前，农户不能录农事（流程顺序真的生效） ==")
    ev = upload(TOKENS["FARMER"], "plant.png", PNG, "PLANTING")
    st, j = api("POST", "/planting", TOKENS["FARMER"], json={
        "batchId": batch_id, "recordType": "IRRIGATE", "operationDate": today,
        "content": "核验前试录", "evidenceIds": [ev.get("id")],
    })
    check("未核验批次被拒绝录农事", st != 200 and "核验" in (j.get("message") or ""), (st, j))

    print("\n== 5. 村管核验（既有账号） ==")
    st, j = api("POST", "/auth/login", json={"username": "village01", "password": "123456"})
    vtoken = (j.get("data") or {}).get("token")
    st, j = api("POST", "/batch/%s/audit" % batch_id, vtoken,
                json={"auditStatus": "APPROVED", "auditRemark": "地块与产品相符，同意建档"})
    check("批次核验通过", st == 200 and j.get("code") == 0, (st, j))

    print("\n== 6. 农户：现场实证 + 农事记录 ==")
    st, j = api("POST", "/planting", TOKENS["FARMER"], json={
        "batchId": batch_id,
        "recordType": "IRRIGATE",
        "operationDate": today,
        "content": "滴灌 40 分钟，土壤湿度 62%",
        "materialName": "山泉水",
        "dosage": "约 3 吨",
        "location": "溪头村一组后山垅 · 一号田",
        "evidenceIds": [ev.get("id")],
    })
    check("农事记录提交并上链", st == 200 and j.get("code") == 0, (st, j))

    harvest_ev = upload(TOKENS["FARMER"], "harvest.png", PNG, "HARVEST")
    st, j = api("POST", "/harvest", TOKENS["FARMER"], json={
        "batchId": batch_id,
        "harvestDate": today,
        "quantity": 580,
        "unit": "kg",
        "qualityLevel": "A",
        "weather": "晴 22℃",
        "remark": "人工采收，无机械损伤",
        "evidenceIds": [harvest_ev.get("id")],
    })
    check("采收信息提交并上链", st == 200 and j.get("code") == 0, (st, j))

    print("\n== 7. 检测机构：上传报告 + 判定 ==")
    pdf = upload(TOKENS["INSPECTOR"], "report.pdf", b"%PDF-1.4\n% test report\n", "QUALITY")
    st, j = api("POST", "/quality", TOKENS["INSPECTOR"], json={
        "batchId": batch_id,
        "reportNo": "JC" + suffix,
        "inspectDate": today,
        "inspectResult": "PASS",
        "standardNo": "GB 2763-2021",
        "conclusion": "所检项目均符合限量要求",
        "fileName": pdf.get("fileName"),
        "filePath": pdf.get("filePath"),
        "fileHash": pdf.get("fileHash"),
        "fileSize": pdf.get("fileSize"),
    })
    check("质检报告提交并上链", st == 200 and j.get("code") == 0, (st, j))

    st, j = api("GET", "/batch/%s" % batch_id, TOKENS["PRODUCER"])
    check("判定合格后批次进入待运输", (j.get("data") or {}).get("status") == "QUALIFIED",
          (j.get("data") or {}).get("status"))

    print("\n== 8. 物流企业：运输交接 ==")
    st, j = api("POST", "/logistics", TOKENS["LOGISTICS"], json={
        "batchId": batch_id,
        "logisticsNo": "YD" + suffix,
        "fromOrg": "溪头村集货点",
        "toOrg": "永辉超市闽侯甘蔗店",
        "vehicleNo": "闽A·8K521",
        "driverName": "黄志强",
        "driverPhone": "1380000" + suffix[:4],
        "departTime": "2026-11-07 09:30:00",
        "arriveTime": "2026-11-07 11:00:00",
        "temperature": "4℃",
    })
    check("物流交接登记并上链", st == 200 and j.get("code") == 0, (st, j))

    print("\n== 9. 销售商：销售核销 ==")
    st, j = api("POST", "/sales", TOKENS["SELLER"], json={
        "batchId": batch_id,
        "marketName": "永辉超市闽侯甘蔗店",
        "salesDate": today,
        "price": 6.8,
        "quantity": 580,
        "unit": "kg",
        "buyerName": "门店自营",
    })
    check("销售登记并上链", st == 200 and j.get("code") == 0, (st, j))

    print("\n== 10. 消费者扫码（免登录）+ 链上复核 ==")
    st, j = api("GET", "/trace/%s" % TRACE_CODE)
    t = j.get("data") or {}
    timeline = t.get("timeline") or []
    kinds = [x.get("type") or x.get("title") for x in timeline]
    check("溯源页免登录可查", st == 200 and t.get("traceCode") == TRACE_CODE, (st, j))
    check("履历覆盖 种植→采收→质检→物流→销售（%d 条）" % len(timeline), len(timeline) >= 5, kinds)
    check("溯源页展示的农户是自助注册的（陈小满）", t.get("farmerName") == "陈小满", t.get("farmerName"))
    check("溯源页展示的检测机构正确", t.get("qualityOrg") == "闽侯县农产品质量安全检测中心", t.get("qualityOrg"))

    st, admin = api("POST", "/auth/login", json={"username": "admin", "password": "123456"})
    atoken = (admin.get("data") or {}).get("token")
    st, j = api("GET", "/chain/verify/%s" % BATCH_NO, atoken)
    v = j.get("data") or {}
    check("链上链下一致性校验通过", st == 200 and v.get("passed") is True and v.get("totalRecords", 0) >= 5, (st, j))

    st, j = api("GET", "/chain/page?page=1&size=50&batchNo=%s" % BATCH_NO, atoken)
    recs = (j.get("data") or {}).get("records") or []
    statuses = {r.get("status") for r in recs}
    check("该批次全部存证成功（%d 条，状态 %s）" % (len(recs), statuses),
          len(recs) >= 5 and statuses == {"SUCCESS"}, statuses)

    ok = sum(1 for _n, v2 in results if v2)
    print("\n结果：%d/%d 通过" % (ok, len(results)))
    failed = [n for n, v2 in results if not v2]
    if failed:
        print("失败项：" + "；".join(failed))
    print("CHAIN_SUMMARY " + json.dumps({
        "total": len(results), "passed": ok,
        "batchNo": BATCH_NO, "traceCode": TRACE_CODE,
    }, ensure_ascii=False))
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
