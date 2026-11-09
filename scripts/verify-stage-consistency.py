#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核对"外层状态"与"内层审核进度"是否同源一致（在服务器本机执行）。"""
import json
import requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session()
S.trust_env = False


def login(user, pwd="123456"):
    r = S.post(BASE + "/auth/login", json={"username": user, "password": pwd}, timeout=15).json()
    if r.get("code") != 0:
        raise RuntimeError("登录失败 %s: %s" % (user, r))
    return r["data"]["token"]


tok = login("admin")
H = {"Authorization": "Bearer " + tok}

detail = S.get(BASE + "/batch/91", headers=H, timeout=30).json()["data"]
prog = S.get(BASE + "/workflow/progress/91", headers=H, timeout=30).json()["data"]

b = detail["batch"]
print("批次:", b["batchNo"], "| 外层状态 status =", b["status"], "| auditStatus =", b["auditStatus"])
print("进度:", prog["percent"], "% | 当前环节 =", prog["currentStage"], "| 等待 =", prog.get("waitingRole"))
print("-" * 70)
for st in prog["stages"]:
    print("%-8s %-10s %-8s %s" % (st["code"], st["title"], st["status"], (st.get("tip") or "")[:60]))

print("-" * 70)
print("外层是否声称已完成采收:", b["status"] in ("HARVESTED", "QUALIFIED", "IN_TRANSIT", "ON_SALE", "SOLD"))
harvest = [x for x in prog["stages"] if x["code"] == "HARVEST"][0]
print("内层采收环节状态:", harvest["status"], "| 两者一致:", (b["status"] in ("HARVESTED", "QUALIFIED", "IN_TRANSIT", "ON_SALE", "SOLD")) == (harvest["status"] == "DONE"))

# 溯源公开接口：不得包含待审核记录
code = b["traceCode"]
pub = S.get(BASE + "/trace/" + code, timeout=30).json()
print("-" * 70)
if pub.get("code") == 0:
    d = pub["data"]
    print("溯源页 采收条数:", len(d.get("harvests") or []), "质检:", len(d.get("qualities") or []),
          "物流:", len(d.get("logistics") or []), "销售:", len(d.get("sales") or []))
    print("溯源时间轴节点:", len(d.get("timeline") or []))
else:
    print("溯源接口返回:", pub)
