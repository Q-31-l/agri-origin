#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核对批次列表里的"待审环节"标记与详情页进度是否一致。"""
import requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session()
S.trust_env = False


def login(u, p="123456"):
    r = S.post(BASE + "/auth/login", json={"username": u, "password": p}, timeout=15).json()
    return r["data"]["token"]


H = {"Authorization": "Bearer " + login("admin")}
j = S.get(BASE + "/batch/page?page=1&size=50", headers=H, timeout=30).json()
rows = j["data"]["records"]
print("批次总数:", j["data"]["total"])
print("%-16s %-12s %-12s %s" % ("批次号", "状态", "待审环节", "验收"))
print("-" * 70)
bad = 0
for b in rows[:15]:
    pend = b.get("pendingStage") or "-"
    print("%-16s %-12s %-12s" % (b["batchNo"], b["status"], pend))
    # 有 pendingStage 时，详情进度里该环节必须是 PENDING
    if pend != "-":
        p = S.get(BASE + "/workflow/progress/%d" % b["id"], headers=H, timeout=30).json()["data"]
        stage = [s for s in p["stages"] if s["title"] == pend]
        ok = bool(stage) and stage[0]["status"] in ("PENDING", "REJECTED")
        if not ok:
            bad += 1
            print("   !! 进度里对不上:", [(s["title"], s["status"]) for s in p["stages"]])
print("-" * 70)
print("待审标记与进度不一致的条数:", bad)
