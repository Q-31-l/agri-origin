#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证：账号禁用 / 改角色后，已签发的旧令牌是否立即失效或按新角色生效"""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080/api"
PASS, FAIL = [0], [0]


def check(name, ok, detail=""):
    (PASS if ok else FAIL)[0] += 1
    print("  [%s] %s %s" % ("PASS" if ok else "FAIL", name, detail))


def sql(q):
    out = subprocess.run(["mysql", "-uroot", "-proot", "agri_trace", "-N", "-e", q],
                         capture_output=True, text=True)
    return [l.split("\t") for l in out.stdout.strip().split("\n") if l.strip()]


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def login(u):
    st, j = call("POST", "/auth/login", body={"username": u, "password": "123456"})
    return j["data"]["token"] if st == 200 else None


farmer_token = login("farmer02")
admin_token = login("admin")
print("=== 前置：farmer02 正常可用 ===")
check("farmer02 登录成功", farmer_token is not None)
st, _ = call("GET", "/batch/page?page=1&size=1", farmer_token)
check("farmer02 可访问列表(200)", st == 200, "http=%s" % st)

row = sql("SELECT id, real_name, phone, role_code, org_name, IFNULL(village_id,0), IFNULL(farm_id,0) "
          "FROM sys_user WHERE username='farmer02'")[0]
uid, real_name, phone, role_code, org_name, village_id, farm_id = row

print("\n=== 1. 管理员禁用账号后，旧令牌是否立即失效 ===")
body = {"username": "farmer02", "realName": real_name, "phone": phone,
        "roleCode": role_code, "orgName": org_name,
        "villageId": int(village_id) or None, "farmId": int(farm_id) or None, "status": 0}
st, j = call("PUT", "/user/%s" % uid, admin_token, body)
check("禁用操作成功", st == 200, "http=%s %s" % (st, j.get("message")))
st, j = call("GET", "/batch/page?page=1&size=1", farmer_token)
check("旧令牌立即被拒(401)", st == 401, "http=%s %s" % (st, j.get("message")))
check("禁用账号无法重新登录", login("farmer02") is None)

print("\n=== 2. 恢复账号后重新登录可用 ===")
body["status"] = 1
st, j = call("PUT", "/user/%s" % uid, admin_token, body)
check("启用操作成功", st == 200, "http=%s" % st)
time.sleep(1)
new_token = login("farmer02")
check("重新登录成功", new_token is not None)
st, _ = call("GET", "/batch/page?page=1&size=1", new_token)
check("新令牌可访问(200)", st == 200, "http=%s" % st)

print("\n=== 3. 改角色后旧令牌是否按新角色鉴权 ===")
# 把 farmer02 临时降级为 SELLER，旧令牌原本是 FARMER，应立即失去 farm 侧写权限
body2 = dict(body, roleCode="SELLER")
st, j = call("PUT", "/user/%s" % uid, admin_token, body2)
check("改角色成功", st == 200, "http=%s %s" % (st, j.get("message")))
st, j = call("GET", "/planting/page?page=1&size=1", new_token)
check("旧令牌按新角色(SELLER)被拒种养列表(403)", st == 403, "http=%s %s" % (st, j.get("message")))

print("\n=== 4. 还原为农户 ===")
st, j = call("PUT", "/user/%s" % uid, admin_token, body)
check("还原成功", st == 200, "http=%s" % st)
db = sql("SELECT status, role_code FROM sys_user WHERE id=%s" % uid)[0]
check("库中状态已还原", db[0] == "1" and db[1] == "FARMER", "status=%s role=%s" % (db[0], db[1]))

print("\n" + "=" * 60)
print("结果：%d 通过 / %d 失败" % (PASS[0], FAIL[0]))
print("=" * 60)
raise SystemExit(1 if FAIL[0] else 0)
