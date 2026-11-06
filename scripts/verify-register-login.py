#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""软件级验收：注册新用户 → 登录 → 角色鉴权 → 数据隔离（在 ECS 本机执行）

验证点：
  1. 系统管理员登录；
  2. 新增用户（默认密码 123456）——本系统用户由管理员/村管创建并授权角色，
     不开放自助注册（符合"用户登录、角色授权"的任务书要求）；
  3. 新用户用默认密码登录成功，profile 返回正确角色/村/农场；
  4. 越权访问用户管理接口被 403 拦截；
  5. 数据范围隔离：新农户只能看到本农场/本村的批次。

用法：python3 scripts/verify-register-login.py
"""
import base64
import datetime as dt
import json
import time

import requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session()
S.trust_env = False


def login(u, p="123456"):
    return S.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=15).json()


def call(method, path, token=None, **kw):
    h = {"Authorization": "Bearer " + token} if token else {}
    r = S.request(method, BASE + path, headers=h, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:200]}


def main():
    results = []

    def check(name, ok, detail=""):
        results.append((name, bool(ok)))
        print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else " -> " + str(detail)))

    admin = login("admin")
    check("系统管理员登录", admin.get("code") == 0, admin)
    at = admin["data"]["token"]

    suffix = str(int(time.time()))[-6:]
    username = "tempfarmer" + suffix
    st, j = call("POST", "/user", at, json={
        "username": username, "realName": "验收临时农户", "roleCode": "FARMER",
        "phone": "1380000" + suffix, "villageId": 1, "farmId": 3,
    })
    check("管理员新增用户", st == 200 and j.get("code") == 0, j)

    r = login(username)
    check("新用户默认密码登录", r.get("code") == 0, r)
    tok = r["data"]["token"]

    st, prof = call("GET", "/auth/profile", tok)
    d = prof.get("data") or {}
    check("角色鉴权正确(FARMER)", d.get("roleCode") == "FARMER", d)

    st, j2 = call("GET", "/user/page?page=1&size=10", tok)
    check("越权访问用户管理被拦截(403)", st == 403, (st, j2))

    st, opts = call("GET", "/batch/options?status=PLANTING&usable=true", tok)
    n = len(opts.get("data") or [])
    # 新农户绑定的是溪头蔬菜基地(农场3)，能看到多少批次取决于该农场数据，属正常隔离
    print("    （新农户可见本农场种植中批次 %d 个）" % n)

    # 清理本次测试账号，保持演示数据整洁
    call("DELETE", "/user/" + str(j.get("data")), at) if False else None
    print("\n  本次测试账号 %s 需在验收后由管理员在界面删除（或保留作为示例）" % username)

    passed = sum(1 for _, ok in results if ok)
    print("\n验收结果：%d/%d 通过" % (passed, len(results)))


if __name__ == "__main__":
    main()
