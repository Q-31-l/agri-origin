#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐身份自助注册审核：五个身份各注册一遍，逐个走核心功能，检查信息获取与数据范围是否合理。

在服务器本机执行（BASE 默认 127.0.0.1:8080）。相比 verify-full-chain.py，这条脚本更聚焦
"注册后的新账号到底能不能合理地把功能用起来"，并额外审核三件事：
  1. 注册页村庄接口是否带经纬度（定位推荐依赖它）；
  2. 农户/生产主体没村能不能自填村（同名复用 / 无则新建）；
  3. 各身份的数据范围与越权拦截是否合理（生产主体只能选本村农户/地块、农户只能看自己批次）。

注意：注册接口按 IP 限流（每小时 5 个成功账号），本脚本会注册 6 个账号，
在第 6 个之前自动 systemctl restart agri-backend 清计数（脚本需在服务器 root 下跑）。

用法：python3 scripts/audit-5-roles.py
"""
import base64
import json
import os
import subprocess
import sys
import time

import requests

BASE = os.environ.get("AGRI_BASE", "http://127.0.0.1:8080/api")
S = requests.Session()
S.trust_env = False

PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

results = []
TOKENS = {}
IDS = {}


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


def register(role, username, real_name, password, **extra):
    body = {"username": username, "password": password, "realName": real_name, "roleCode": role}
    body.update(extra)
    st, j = api("POST", "/auth/register", json=body)
    data = j.get("data") or {}
    if data.get("token"):
        TOKENS[role] = data["token"]
        IDS[role] = (data.get("user") or {}).get("id")
    return st, j, data


def reset_register_limit():
    print("\n== 0. 重启后端，清注册限流计数（为第 6 个自填村账号腾额度） ==")
    r = subprocess.run(["systemctl", "restart", "agri-backend"], capture_output=True, text=True)
    time.sleep(12)
    st, _ = api("GET", "/chain/status")
    print("      后端状态码：", st)


def main():
    suffix = str(int(time.time()))[-5:]
    today = time.strftime("%Y-%m-%d")

    print("\n== 1. 村庄接口带经纬度（注册页定位推荐依赖） ==")
    st, j = api("GET", "/public/villages")
    villages = j.get("data") or []
    with_coord = [v for v in villages if v.get("lat") is not None and v.get("lng") is not None]
    check("村庄列表可匿名获取", st == 200 and len(villages) >= 1, (st, len(villages)))
    check("村庄带经纬度（%d/%d）" % (len(with_coord), len(villages)),
          len(with_coord) == len(villages) and len(with_coord) >= 1,
          {v.get("villageName"): (v.get("lat"), v.get("lng")) for v in villages})
    check("村庄接口不泄露负责人电话等敏感字段",
          all(("phone" not in v and "managerPhone" not in v) for v in villages))

    print("\n== 2. 五身份自助注册（农户用「自填村名」验证同名复用） ==")
    # 农户故意传 villageName=溪头村、不给 villageId，应被解析成已存在的 id=1
    cases = [
        ("FARMER", "nczh" + suffix, "陈小满", "farm2026", {"villageName": "溪头村"}),
        ("PRODUCER", "sczt" + suffix, "林青禾", "farm2026", {"orgName": "青禾家庭农场", "villageId": 1}),
        ("INSPECTOR", "jcjg" + suffix, "周敏", "jc2026", {"orgName": "闽侯县农产品质量安全检测中心"}),
        ("LOGISTICS", "wlqy" + suffix, "黄志强", "wl2026", {"orgName": "闽运冷链物流有限公司"}),
        ("SELLER", "xss" + suffix, "郑晓宁", "xs2026", {"orgName": "永辉超市闽侯甘蔗店"}),
    ]
    for role, uname, rname, pwd, extra in cases:
        st, j, data = register(role, uname, rname, pwd, **extra)
        user = data.get("user") or {}
        check("注册 %s（%s）" % (role, rname),
              st == 200 and j.get("code") == 0 and bool(data.get("token"))
              and user.get("roleCode") == role, (st, j))

    # 农户自填"溪头村"应落到 id=1，而不是新建一个重复村
    st, j = api("GET", "/auth/profile", TOKENS["FARMER"])
    f = j.get("data") or {}
    check("农户自填村名「溪头村」→ 复用已有村（id=1）",
          f.get("villageId") == 1 and f.get("villageName") == "溪头村", f)
    st, j = api("GET", "/auth/profile", TOKENS["PRODUCER"])
    check("生产主体带村+机构名", (j.get("data") or {}).get("villageId") == 1
          and (j.get("data") or {}).get("orgName") == "青禾家庭农场", j)

    print("\n== 3. 新农户注册后批次为空（预期），被指派后才能看到 ==")
    st, j = api("GET", "/batch/page", TOKENS["FARMER"])
    total_before = (j.get("data") or {}).get("total", -1)
    check("新农户批次列表为空（还未被指派）", st == 200 and total_before == 0, (st, total_before))

    print("\n== 4. 数据范围：生产主体只能看到本村农户与地块 ==")
    # 溪头村(id=1)已有生产主体/农户账号，再手动给一个"别的村农户"也看不到——通过 options 验证：
    # 生产主体不带 villageId 调 /user/options?roleCode=FARMER，返回的必须全是本村(id=1)
    st, j = api("GET", "/user/options?roleCode=FARMER", TOKENS["PRODUCER"])
    opts = j.get("data") or []
    same_village = all((u.get("villageId") == 1) for u in opts)
    check("生产主体可选农户全部是本村（%d 人）" % len(opts), st == 200 and same_village,
          opts if not same_village else "")
    st, j = api("GET", "/farm/list", TOKENS["PRODUCER"])
    farms = j.get("data") or []
    check("生产主体可选地块全部是本村（%d 块）" % len(farms),
          st == 200 and all((f.get("villageId") == 1) for f in farms))

    print("\n== 5. 生产主体建地块 → 建批次（指派给刚注册的农户） ==")
    st, j = api("POST", "/farm", TOKENS["PRODUCER"], json={
        "farmName": "青禾·审核专用一号田",
        "farmerId": IDS["FARMER"],
        "villageId": 2,  # 故意传别的村，服务端应改回自己的村
        "address": "溪头村审核地块",
        "area": 8.0,
        "soilType": "壤土",
        "waterSource": "山泉",
    })
    farm_id = j.get("data")
    check("新注册生产主体可建地块", st == 200 and j.get("code") == 0, (st, j))

    st, j = api("POST", "/batch", TOKENS["PRODUCER"], json={
        "productId": 1, "farmId": farm_id, "plantingDate": today,
        "quantity": 500, "unit": "kg", "remark": "审核：生产主体指派给新注册农户",
    })
    # POST /batch 返回 R<Long>，data 直接就是批次 id
    batch_id = j.get("data")
    check("批次创建成功并指派给农户", st == 200 and isinstance(batch_id, int), (st, j))

    st, j = api("GET", "/batch/page", TOKENS["FARMER"])
    total_after = (j.get("data") or {}).get("total", -1)
    check("农户被指派后能看到批次（%d）" % total_after, st == 200 and total_after >= 1, (st, total_after))

    print("\n== 6. 越权拦截：不该能做的操作应 403 ==")
    st, j = api("POST", "/batch", TOKENS["FARMER"], json={"productId": 1, "farmerId": IDS["FARMER"], "plantingDate": today, "quantity": 1})
    check("农户不能建批次", st == 403, st)
    st, j = api("POST", "/farm", TOKENS["INSPECTOR"], json={"farmName": "x", "farmerId": IDS["FARMER"]})
    check("检测机构不能建农场", st == 403, st)
    st, j = api("POST", "/quality", TOKENS["LOGISTICS"], json={"batchId": batch_id})
    check("物流企业不能上传质检", st == 403, st)

    print("\n== 7. 自填新建村：填一个系统里没有的村，应自动建档并绑定 ==")
    reset_register_limit()
    new_village = "后垅村" + suffix
    st, j, data = register("FARMER", "zxnc" + suffix, "自填测试", "farm2026", villageName=new_village, regionName="白沙镇")
    user = data.get("user") or {}
    check("自填新建村可注册成功", st == 200 and j.get("code") == 0, (st, j))
    check("新建村名已落到账号（%s）" % user.get("villageName"),
          user.get("villageName") == new_village and user.get("villageId") is not None, user)
    # 再用同名注册一次，应复用同一个村 id，而不是再建一条
    st2, j2, data2 = register("FARMER", "zxnc2" + suffix, "自填复用", "farm2026", villageName=new_village)
    user2 = data2.get("user") or {}
    check("同名村再次自填 → 复用同一个村 id",
          st2 == 200 and user2.get("villageId") == user.get("villageId"),
          (user.get("villageId"), user2.get("villageId")))

    ok = sum(1 for _n, v in results if v)
    print("\n结果：%d/%d 通过" % (ok, len(results)))
    failed = [n for n, v in results if not v]
    if failed:
        print("失败项：\n  - " + "\n  - ".join(failed))
    print("AUDIT_SUMMARY " + json.dumps({"total": len(results), "passed": ok}, ensure_ascii=False))
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
