#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""自助注册验收（在服务器本机执行：BASE 默认 127.0.0.1:8080）

覆盖点：
  1. 匿名取村列表（注册页下拉的数据来源）
  2. 农户注册 → 直接拿到 JWT，角色/村/权限正确
  3. 销售商注册 → 机构名落到 orgName
  4. 拒绝项：账号重复、密码=账号、身份越界（ADMIN）、农户不选村、销售商不填机构
  5. 数据隔离：新农户看不到任何存量批次（N 条 vs 管理员全量），但接口本身是 200
  6. 注册后的账号能正常改密码/退出，说明凭证链路完整
  7. 限流：超过单 IP 阈值返回 429（放在最后，避免影响前面的用例；跑完请重启后端清计数）

用法：python3 scripts/verify-self-register.py
"""
import json
import os
import sys
import time

import requests

BASE = os.environ.get("AGRI_BASE", "http://127.0.0.1:8080/api")
S = requests.Session()
S.trust_env = False

results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else "  -> " + str(detail)[:400]))


def api(method, path, token=None, **kw):
    h = {"Authorization": "Bearer " + token} if token else {}
    r = S.request(method, BASE + path, headers=h, timeout=20, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:300]}


def main():
    suffix = str(int(time.time()))[-6:]
    farmer_u = "vtest_farmer_" + suffix
    seller_u = "vtest_seller_" + suffix

    print("\n== 1. 匿名取村列表 ==")
    st, j = api("GET", "/public/villages")
    villages = j.get("data") or []
    check("免登录可读村列表", st == 200 and j.get("code") == 0, j)
    check("村列表非空且字段最小化", len(villages) > 0
          and set(villages[0].keys()) == {"id", "villageName", "regionName"}, villages[:1])
    village_id = villages[0]["id"] if villages else None
    village_name = villages[0]["villageName"] if villages else ""

    print("\n== 2. 农户自助注册 ==")
    st, j = api("POST", "/auth/register", json={
        "username": farmer_u, "password": "farm2026", "realName": "验收农户甲",
        "phone": "139" + suffix + "00", "roleCode": "FARMER", "villageId": village_id,
    })
    data = j.get("data") or {}
    ftoken = data.get("token")
    check("注册成功并直接签发 token", st == 200 and j.get("code") == 0 and bool(ftoken), j)
    check("角色固定为 FARMER", (data.get("user") or {}).get("roleCode") == "FARMER", data.get("user"))
    check("所属村回填正确", (data.get("user") or {}).get("villageName") == village_name,
          (data.get("user") or {}).get("villageName"))
    perms = data.get("permissions") or []
    check("权限按角色下发（有 planting:create，无 user:list）",
          "planting:create" in perms and "user:list" not in perms, perms)

    print("\n== 3. 销售商自助注册 ==")
    st, j = api("POST", "/auth/register", json={
        "username": seller_u, "password": "sell2026", "realName": "验收销售乙",
        "roleCode": "SELLER", "orgName": "验收生鲜门店",
    })
    sdata = j.get("data") or {}
    check("销售商注册成功", st == 200 and j.get("code") == 0 and bool(sdata.get("token")), j)
    check("机构名写入 orgName", (sdata.get("user") or {}).get("orgName") == "验收生鲜门店",
          sdata.get("user"))

    print("\n== 4. 拒绝项 ==")
    st, j = api("POST", "/auth/register", json={
        "username": farmer_u, "password": "farm2026", "realName": "重复账号",
        "roleCode": "FARMER", "villageId": village_id,
    })
    check("账号重复被拒（409）", st == 409, (st, j))

    st, j = api("POST", "/auth/register", json={
        "username": "same_pwd_" + suffix, "password": "same_pwd_" + suffix,
        "realName": "弱口令", "roleCode": "FARMER", "villageId": village_id,
    })
    check("密码与账号相同被拒", st == 400 and "相同" in (j.get("message") or ""), (st, j))

    st, j = api("POST", "/auth/register", json={
        "username": "vt_admin_" + suffix, "password": "admin2026",
        "realName": "越权测试", "roleCode": "ADMIN",
    })
    check("自助注册不能开管理员（403）", st == 403, (st, j))

    st, j = api("POST", "/auth/register", json={
        "username": "vt_novil_" + suffix, "password": "novillage",
        "realName": "未选村", "roleCode": "FARMER",
    })
    check("农户未选村被拒", st == 400 and "村" in (j.get("message") or ""), (st, j))

    st, j = api("POST", "/auth/register", json={
        "username": "vt_noorg_" + suffix, "password": "noorg2026",
        "realName": "未填机构", "roleCode": "SELLER",
    })
    check("销售商未填机构被拒", st == 400 and "机构" in (j.get("message") or ""), (st, j))

    st, j = api("POST", "/auth/register", json={
        "username": "ab", "password": "abc123", "realName": "账号太短",
        "roleCode": "FARMER", "villageId": village_id,
    })
    check("账号格式不合规被拒（参数校验）", st == 400, (st, j))

    print("\n== 5. 注册即登录 + 数据隔离 ==")
    st, j = api("GET", "/auth/profile", token=ftoken)
    check("新账号可直接读 profile", st == 200 and (j.get("data") or {}).get("username") == farmer_u, j)

    st, j = api("GET", "/batch/page?page=1&size=10", token=ftoken)
    mine = (j.get("data") or {}).get("total")
    check("新农户批次列表可访问但为空（未被指派）", st == 200 and mine == 0, (st, j))

    st, admin = api("POST", "/auth/login", json={"username": "admin", "password": "123456"})
    atoken = (admin.get("data") or {}).get("token")
    st2, j2 = api("GET", "/batch/page?page=1&size=10", token=atoken)
    all_total = (j2.get("data") or {}).get("total")
    check("管理员仍能看到全量批次（隔离而非接口坏了）",
          st2 == 200 and isinstance(all_total, int) and all_total > 0, (st2, j2))
    print("      新农户可见 %s 条 / 管理员可见 %s 条" % (mine, all_total))

    st, j = api("GET", "/user/page?page=1&size=10", token=ftoken)
    check("新农户访问用户管理被 403 拦截", st == 403, (st, j))

    print("\n== 6. 注册后的凭证链路 ==")
    st, j = api("POST", "/auth/login", json={"username": farmer_u, "password": "farm2026"})
    check("用注册时的密码可再次登录", st == 200 and j.get("code") == 0, j)
    tok2 = (j.get("data") or {}).get("token")
    st, j = api("PUT", "/auth/password", token=tok2,
                json={"oldPassword": "farm2026", "newPassword": "farm2027"})
    check("可自行修改密码", st == 200 and j.get("code") == 0, j)
    st, j = api("POST", "/auth/login", json={"username": farmer_u, "password": "farm2027"})
    check("新密码生效", st == 200 and j.get("code") == 0, j)

    print("\n== 7. 限流（放到最后） ==")
    codes = []
    for i in range(7):
        st, j = api("POST", "/auth/register", json={
            "username": "vt_f%6s_%d" % (suffix, i), "password": "flood2026",
            "realName": "限流测试", "roleCode": "FARMER", "villageId": village_id,
        })
        codes.append(st)
    check("超出单 IP 阈值后返回 429", 429 in codes, codes)

    ok = sum(1 for _n, v in results if v)
    print("\n结果：%d/%d 通过" % (ok, len(results)))
    failed = [n for n, v in results if not v]
    if failed:
        print("失败项：" + "；".join(failed))
    print("SIM_SUMMARY " + json.dumps({"total": len(results), "passed": ok}))
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
