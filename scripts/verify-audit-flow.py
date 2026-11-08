#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证「注册审核 + 农户录农事自动建档批次」全链路。

在服务器本机执行。覆盖：
  1. 村级管理员自助注册 → pendingAudit（待审核，不能登录）
  2. 监管人员自助注册 → pendingAudit
  3. 系统管理员(qiulian)审核通过村级管理员 → 账号启用，可登录
  4. 农户(farmer01)录农事，手写"大豆"、不选批次 → 提交成功（待审核）
  5. 村管(village01)审核该农事通过 → 自动建档批次（productName=大豆）
  6. 农户能看到新生成的批次
  7. 模拟管理员 admin 已停用（登录被拒）
"""
import subprocess
import sys
import time
import requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session()
S.trust_env = False


def api(method, path, token=None, **kw):
    h = {"Authorization": "Bearer " + token} if token else {}
    r = S.request(method, BASE + path, headers=h, timeout=30, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:300]}


def login(username, password):
    st, j = api("POST", "/auth/login", json={"username": username, "password": password})
    return (j.get("data") or {}).get("token")


def main():
    suffix = str(int(time.time()))[-5:]
    today = time.strftime("%Y-%m-%d")
    results = []

    def check(name, ok, detail=""):
        results.append(ok)
        print(("  [PASS] " if ok else "  [FAIL] ") + name + ("" if ok else "  -> " + str(detail)[:300]))

    # 清注册限流
    subprocess.run(["systemctl", "restart", "agri-backend"], capture_output=True)
    time.sleep(12)

    print("\n== 1. 村级管理员自助注册（填身份证号，待审核） ==")
    st, j = api("POST", "/auth/register", json={
        "username": "cun" + suffix, "password": "farm2026", "realName": "测试村管",
        "idCard": "350121199001011234", "roleCode": "VILLAGE_ADMIN", "villageId": 1})
    d = j.get("data") or {}
    check("注册返回 pendingAudit 且无 token", st == 200 and d.get("pendingAudit") is True and not d.get("token"), (st, d))
    # 待审核不能登录
    st2, j2 = api("POST", "/auth/login", json={"username": "cun" + suffix, "password": "farm2026"})
    check("待审核账号不能登录", st2 != 200, st2)

    print("\n== 2. 监管人员自助注册（实名+机构，待审核） ==")
    st, j = api("POST", "/auth/register", json={
        "username": "jg" + suffix, "password": "farm2026", "realName": "测试监管",
        "idCard": "350121199002022345", "roleCode": "REGULATOR", "orgName": "测试监管部门"})
    d = j.get("data") or {}
    check("监管注册返回 pendingAudit", st == 200 and d.get("pendingAudit") is True, (st, d))

    print("\n== 3. 系统管理员(qiulian)审核 ==")
    qt = login("qiulian", "321@110")
    check("qiulian 登录", bool(qt))
    st, j = api("GET", "/user/pending", qt)
    pending = j.get("data") or []
    names = [u.get("username") for u in pending]
    check("待审核列表含新注册的村级+监管（%d 条）" % len(pending),
          "cun" + suffix in names and "jg" + suffix in names, names)
    target = next((u for u in pending if u.get("username") == "cun" + suffix), None)
    if target:
        st, j = api("POST", "/user/%d/audit?approve=true" % target.get("id"), qt)
        check("审核通过村级管理员", st == 200 and j.get("code") == 0, (st, j))
    ct = login("cun" + suffix, "farm2026")
    check("审核通过后村级管理员可登录", bool(ct), ct)

    print("\n== 4. 农户录农事（手写'大豆'，不选批次） ==")
    ft = login("farmer01", "123456")
    check("farmer01 登录", bool(ft))
    # 先传一个实证文件
    png = bytes.fromhex("89504e470d0a1a0a0000000d49484452")[:0] or None
    # 用最小 PNG
    import base64
    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
    st, j = api("POST", "/file/upload", ft, files={"file": ("a.png", png, "application/octet-stream")}, data={"bizType": "PLANTING"})
    ev = (j.get("data") or {}).get("id")
    check("上传实证文件", st == 200 and bool(ev), (st, j))

    st, j = api("POST", "/planting", ft, json={
        "productName": "大豆", "recordType": "SOWING", "operationDate": today,
        "content": "手写大豆播种定植", "evidenceIds": [ev]})
    planting_id = j.get("data")
    check("手写大豆录农事（无批次）提交成功", st == 200 and isinstance(planting_id, int), (st, j))

    print("\n== 5. 村管审核农事 → 自动建档批次 ==")
    vt = login("village01", "123456")
    check("village01 登录", bool(vt))
    st, j = api("POST", "/planting/%d/audit" % planting_id, vt, json={"auditStatus": "APPROVED", "auditRemark": "属实"})
    check("村管审核农事通过", st == 200 and j.get("code") == 0, (st, j))

    st, j = api("GET", "/batch/page", ft)
    recs = (j.get("data") or {}).get("records") or []
    total = (j.get("data") or {}).get("total")
    soy = [b for b in recs if b.get("productName") == "大豆"]
    check("审核后自动生成'大豆'批次（农户可见 %d 个批次）" % total, total >= 1 and soy, (total, [b.get("productName") for b in recs]))

    print("\n== 6. admin 已停用 ==")
    st, j = api("POST", "/auth/login", json={"username": "admin", "password": "123456"})
    check("模拟管理员 admin 已停用（登录被拒）", st != 200, st)

    ok = sum(results)
    print("\n结果：%d/%d 通过" % (ok, len(results)))
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
