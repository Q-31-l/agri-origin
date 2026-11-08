#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证「手写产品名自动建批次」：不选固定品类，直接手写作物名建档。

在服务器本机执行。验证点：
  1. 手写"玉米"建档（不传 productId）→ 成功，productName=玉米
  2. 再手写"玉米"建档 → 同名复用产品，但批次号必须不同（上链专属，不能一样）
  3. 手写"高粱"建档 → 新建产品，批次照常生成
  4. 三个批次 batchNo 互不相同
"""
import sys, time, requests

BASE = "http://127.0.0.1:8080/api"
S = requests.Session(); S.trust_env = False


def api(method, path, token=None, **kw):
    h = {"Authorization": "Bearer " + token} if token else {}
    r = S.request(method, BASE + path, headers=h, timeout=30, **kw)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:200]}


def login(username):
    st, j = api("POST", "/auth/login", json={"username": username, "password": "123456"})
    return (j.get("data") or {}).get("token")


def main():
    # admin 不受村过滤、可建批次，用它验证手写建批次最干净（producer01 未绑村看不到农户）
    token = login("admin")
    print("登录:", bool(token))

    # 取一个农户做种植农户（建批次必须指定农户或地块）
    st, j = api("GET", "/user/options?roleCode=FARMER", token)
    farmers = j.get("data") or []
    farmer_id = farmers[0].get("id") if farmers else None
    print("农户:", farmer_id, "（可选", len(farmers), "人）")

    today = time.strftime("%Y-%m-%d")
    batch_nos = []

    def create_and_get(payload):
        st, j = api("POST", "/batch", token, json=payload)
        bid = j.get("data")
        if not isinstance(bid, int):
            print("  建批次失败:", st, j.get("message"), bid)
            return None
        st2, j2 = api("GET", "/batch/%d" % bid, token)
        b = (j2.get("data") or {}).get("batch") or (j2.get("data") or {})
        batch_nos.append(b.get("batchNo"))
        print("  batchNo=%s  product=%s  variety=%s" % (b.get("batchNo"), b.get("productName"), b.get("variety")))
        return b

    print("\n[1] 手写「玉米」建档（不传 productId）：")
    create_and_get({"productName": "玉米", "farmerId": farmer_id, "plantingDate": today,
                    "quantity": 100, "unit": "kg", "remark": "手写产品验证1"})

    print("\n[2] 再手写「玉米」建档（同名产品，批次号必须不同）：")
    create_and_get({"productName": "玉米", "farmerId": farmer_id, "plantingDate": today,
                    "quantity": 200, "unit": "kg", "remark": "手写产品验证2"})

    print("\n[3] 手写「高粱」建档（全新产品，带品种）：")
    create_and_get({"productName": "高粱", "variety": "糯高粱", "farmerId": farmer_id,
                    "plantingDate": today, "quantity": 50, "unit": "kg", "remark": "手写产品验证3"})

    print("\n批次号列表:", batch_nos)
    uniq = len(batch_nos) == len(set(batch_nos))
    print("批次号唯一（不能一样）:", "PASS" if uniq and len(batch_nos) == 3 else "FAIL")
    return 0 if uniq and len(batch_nos) == 3 else 1


if __name__ == "__main__":
    sys.exit(main())
