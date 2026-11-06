#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WeBASE-Front HTTP 调用 FISCO BCOS 2.x 合约 · 全流程演示脚本

不依赖任何区块链 SDK，仅用 HTTP POST 调 WeBASE-Front 的 /trans/handle，
打通「探活 → 只读查询 → 写交易 → 回执校验 → 事件/计数」全流程。

演示两个对象（同一条链）：
  1. 生产合约 Traceability（后端 Java SDK 部署）—— 只读查询，证明 HTTP 能读生产数据；
  2. 演示合约 StorageCenter（本脚本经 WeBASE-Front 部署）—— 读写回执事件全流程。

前置：
  - WeBASE-Front 已运行（本环境 https://<站点>/WeBASE-Front，Basic 认证 webase/Agri@2026）
  - WeBASE-Front 本地密钥库已有签名用户（本环境 userName=agri-admin）
  - StorageCenter 已部署（脚本内 STORAGE_ADDR；部署见 contracts/demo/ 与部署文档）

用法（在可访问 WeBASE-Front 的机器上）：
  export FRONT_BASE=http://127.0.0.1:5002/WeBASE-Front
  export FRONT_USER=webase FRONT_PASS='Agri@2026'
  python3 scripts/webase-http-demo.py
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("FRONT_BASE", "http://127.0.0.1:5002/WeBASE-Front")
USER = os.environ.get("FRONT_USER", "webase")
PASSWORD = os.environ.get("FRONT_PASS", "Agri@2026")

# 签名用户（必须是 WeBASE-Front 本地密钥库中的地址）
SIGN_USER = "0xf19449c2228cc1b9fe36d9c00c65080479c8bdc3"
# 生产合约（Java SDK 部署）
TRACE_ADDR = "0x0426e8fbda2b5e61c64c56773ab251e03f4bbedb"
# 演示合约（本脚本部署）
STORAGE_ADDR = "0x26e59c7c12e540b7ced0a3a4ecdd8edc65335aa9"

HERE = os.path.dirname(os.path.abspath(__file__))
SRC_PATH = os.path.join(HERE, "..", "contracts", "demo", "StorageCenter.sol")

AUTH = base64.b64encode(("%s:%s" % (USER, PASSWORD)).encode()).decode()


def req(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json",
                                        "Authorization": "Basic " + AUTH})
    try:
        txt = urllib.request.urlopen(r, timeout=120).read().decode()
    except urllib.error.HTTPError as e:
        raise RuntimeError("HTTP %s on %s -> %s" % (e.code, path, e.read().decode()[:200]))
    try:
        return json.loads(txt)
    except Exception:
        return txt


def compile_storage():
    src = open(SRC_PATH, encoding="utf-8").read()
    # 本机 WeBASE-Front 服务端编译器固定 0.4.25，源码兼容，仅改版本声明
    src = src.replace("pragma solidity 0.5.2;", "pragma solidity ^0.4.25;")
    comp = req("POST", "/contract/contractCompile",
               {"contractName": "StorageCenter",
                "solidityBase64": base64.b64encode(src.encode()).decode()})
    return json.loads(comp["contractAbi"]), src


def handle(addr, contract_name, contract_path, abi, func_name, params, user=SIGN_USER):
    return req("POST", "/trans/handle", {
        "groupId": "1", "user": user, "contractName": contract_name,
        "contractPath": contract_path, "version": "", "funcName": func_name,
        "funcParam": params, "contractAddress": addr, "contractAbi": abi,
        "useAes": False, "useCns": False, "cnsName": "",
    })


def main():
    print("=" * 62)
    print("WeBASE-Front HTTP 调用 FISCO BCOS 2.x 合约 · 全流程演示")
    print("=" * 62)

    # 1. 探活
    print("\n[1] 探活：WeBASE-Front 版本 =", req("GET", "/version"))

    # 2. 生产合约只读（证明 HTTP 能读生产数据，同一条链）
    print("\n[2] 生产合约 Traceability 只读（HTTP，不上链）：")
    import json as _j
    trace_abi = _j.load(open(os.path.join(HERE, "..", "contracts", "abi", "Traceability.abi"), encoding="utf-8"))
    print("    getBatchCount() =", handle(TRACE_ADDR, "Traceability", "", trace_abi, "getBatchCount", []))
    print("    batchExistsOf(AG20260515002) =",
          handle(TRACE_ADDR, "Traceability", "", trace_abi, "batchExistsOf", ["AG20260515002"]))
    print("    owner() =", handle(TRACE_ADDR, "Traceability", "", trace_abi, "owner", []))

    # 3. 演示合约：编译取 ABI
    abi, _src = compile_storage()
    print("\n[3] 演示合约 StorageCenter（%s）ABI 条数 = %d" % (STORAGE_ADDR, len(abi)))

    # 4. 只读
    print("\n[4] 只读 owner() =", handle(STORAGE_ADDR, "StorageCenter", "demo", abi, "owner", []))
    print("    写前 getDataInfoByHash(hash-001) =",
          handle(STORAGE_ADDR, "StorageCenter", "demo", abi, "getDataInfoByHash", ["hash-001"]))

    # 5. 写交易
    print("\n[5] 写交易 writeData：")
    r = handle(STORAGE_ADDR, "StorageCenter", "demo", abi, "writeData",
               ["hash-001", "Hello FISCO BCOS Traceability", "uploader=agri-admin;size=35B"])
    if isinstance(r, dict) and "transactionHash" in r:
        print("    txHash   =", r.get("transactionHash"))
        print("    区块     =", r.get("blockNumber"), " status =", r.get("status"),
              " statusOK =", r.get("statusOK"))
        print("    gasUsed  =", r.get("gasUsed"), " 事件日志 =", len(r.get("logs") or []), "条")
    else:
        print("    ", json.dumps(r, ensure_ascii=False)[:300])

    # 6. 写后读回 + 计数
    print("\n[6] 写后读回 getDataInfoByHash =",
          handle(STORAGE_ADDR, "StorageCenter", "demo", abi, "getDataInfoByHash", ["hash-001"]))
    r2 = handle(STORAGE_ADDR, "StorageCenter", "demo", abi, "accessData", ["hash-001"])
    if isinstance(r2, dict) and "transactionHash" in r2:
        print("    accessData 区块 =", r2.get("blockNumber"), " statusOK =", r2.get("statusOK"))
    print("    计数 getDataAccessCount =",
          handle(STORAGE_ADDR, "StorageCenter", "demo", abi, "getDataAccessCount", ["hash-001"]))

    # 7. 回执复核
    print("\n[7] 回执复核（transactionReceipt）：")
    tx = r.get("transactionHash") if isinstance(r, dict) else None
    if tx:
        rc = req("GET", "/1/web3/transactionReceipt/" + tx)
        d = rc.get("data") if isinstance(rc, dict) and isinstance(rc.get("data"), dict) else rc
        if isinstance(d, dict):
            print("    status =", d.get("status"), " 区块 =", d.get("blockNumber"),
                  " statusOK =", d.get("statusOK"))

    print("\n" + "=" * 62)
    print("全流程演示完成：查询 → 写入 → 上链 → 回执 → 事件/计数 全部打通")
    print("=" * 62)


if __name__ == "__main__":
    sys.exit(main())
