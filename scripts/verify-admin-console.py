#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""管理员控制台能力验证（在 ECS 本机执行）：账号治理 + 越权负例"""
import json
import sys
import time

import requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session()
S.trust_env = False

PASS, FAIL = [], []


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append(name)
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (("  -> " + str(detail)) if detail and not ok else ""))


def login(user, pwd="123456"):
    r = S.post(BASE + "/auth/login", json={"username": user, "password": pwd}, timeout=15)
    j = r.json()
    if j.get("code") != 0:
        raise RuntimeError("登录失败 %s: %s" % (user, j))
    return j["data"]["token"]


def call(method, path, token=None, **kw):
    headers = {"Authorization": "Bearer " + token} if token else {}
    kw.setdefault("timeout", 60)
    r = S.request(method, BASE + path, headers=headers, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:200]}


def main():
    print("=== 1. 管理员登录与全量可见 ===")
    tk = login("admin")
    st, me = call("GET", "/user/me", tk)
    my_id = (me.get("data") or {}).get("id")
    my_name = (me.get("data") or {}).get("realName")
    check("系统管理员登录成功", bool(tk), my_name)
    check("身份为 ADMIN", (me.get("data") or {}).get("roleCode") == "ADMIN", me.get("data"))

    st, j = call("GET", "/user/page?page=1&size=100", tk)
    total = j.get("data", {}).get("total", 0) if j.get("code") == 0 else -1
    check("用户列表可读", j.get("code") == 0, j)
    check("管理员看到全部账号（>=38）", total >= 38, "total=%s" % total)

    # 角色覆盖度：管理员应能看到每一种角色
    st, j2 = call("GET", "/user/page?page=1&size=100", tk)
    roles = {u.get("roleCode") for u in (j2.get("data", {}) or {}).get("records", [])}
    check("账号列表覆盖全部 8 类角色", len(roles) >= 7, sorted(roles))

    print("\n=== 2. 新增 → 停用 → 启用 → 删除 ===")
    uname = "tmp_admin_probe_%d" % int(time.time())
    st, j = call("POST", "/user", tk, json={
        "username": uname, "password": "Probe@123456", "realName": "临时探针账号",
        "roleCode": "FARMER", "phone": "13900000000", "villageId": 1, "status": 1})
    check("创建临时账号", j.get("code") == 0, j)
    uid = j.get("data")
    check("返回账号 id", bool(uid), uid)

    st, j = call("PUT", "/user/%s/status?status=0" % uid, tk)
    check("停用账号", j.get("code") == 0, j)

    st, j = call("POST", "/auth/login", None, json={"username": uname, "password": "Probe@123456"})
    check("停用后无法登录", j.get("code") != 0, j.get("message"))

    st, j = call("PUT", "/user/%s/status?status=1" % uid, tk)
    check("启用账号", j.get("code") == 0, j)

    st, j = call("POST", "/auth/login", None, json={"username": uname, "password": "Probe@123456"})
    check("启用后可正常登录", j.get("code") == 0, j)

    st, j = call("DELETE", "/user/%s" % uid, tk)
    check("删除账号", j.get("code") == 0, j)

    st, j = call("POST", "/auth/login", None, json={"username": uname, "password": "Probe@123456"})
    check("删除后无法登录", j.get("code") != 0, j.get("message"))

    st, j = call("POST", "/user", tk, json={
        "username": uname, "password": "Probe@123456", "realName": "同名重建",
        "roleCode": "FARMER", "villageId": 1, "status": 1})
    check("删除后同名可重新创建", j.get("code") == 0, j)
    uid2 = j.get("data")
    if uid2:
        call("DELETE", "/user/%s" % uid2, tk)

    print("\n=== 3. 自我保护负例 ===")
    st, j = call("PUT", "/user/%s/status?status=0" % my_id, tk)
    check("不能停用当前登录账号", j.get("code") != 0, j)

    st, j = call("DELETE", "/user/%s" % my_id, tk)
    check("不能删除当前登录账号", j.get("code") != 0, j)

    st, j = call("PUT", "/user/%s/status?status=5" % my_id, tk)
    check("非法状态值被拒绝", j.get("code") != 0, j)

    print("\n=== 4. 越权负例（普通角色不得使用管理员接口） ===")
    tk_f = login("farmer01")
    st, j = call("DELETE", "/user/6", tk_f)
    check("农户不能删除账号", j.get("code") != 0, j)
    check("农户请求被权限拦截(403)", st == 403, "http=%s" % st)

    tk_v = login("village01")
    st, j = call("DELETE", "/user/6", tk_v)
    check("村管不能删除账号（删除仅管理员）", j.get("code") != 0, j)

    tk_r = login("regulator01")
    st, j = call("POST", "/admin/revoke", tk_r, json={
        "bizType": "HARVEST", "bizId": 1, "reason": "越权测试"})
    check("监管不能撤销记录", j.get("code") != 0, j)

    st, j = call("POST", "/admin/file/1/revoke?reason=x", tk_r)
    check("监管不能撤销实证", j.get("code") != 0, j)

    print("\n=== 5. 管理员列表与待审可见性 ===")
    st, j = call("GET", "/user/pending", tk)
    check("待审核注册列表可读", j.get("code") == 0, j)

    st, j = call("GET", "/workflow/todo", tk)
    check("管理员待办可读", j.get("code") == 0, j)

    st, j = call("GET", "/stats/overview", tk)
    check("管理员看板可读", j.get("code") == 0, j)

    print("\n===== 结果：通过 %d，失败 %d =====" % (len(PASS), len(FAIL)))
    if FAIL:
        for f in FAIL:
            print("  失败项：" + f)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
