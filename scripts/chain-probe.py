"""链健康探针：JSON-RPC 区块高度（result 为 16 进制）+ 节点版本 + 后端 chain/status

用法: python3 scripts/chain-probe.py
"""
import json
import urllib.request

RPC = "http://127.0.0.1:8545"


def rpc(method, params):
    data = json.dumps({"jsonrpc": "2.0", "method": method, "params": params, "id": 1}).encode()
    req = urllib.request.Request(RPC, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode())


def height():
    r = rpc("getBlockNumber", [1])
    return int(r["result"], 16)


if __name__ == "__main__":
    print("区块高度:", height())
    ver = rpc("getClientVersion", []).get("result", {})
    print("链版本:", ver.get("FISCO-BCOS Version") or ver)
    peers = rpc("getPeers", [1])["result"]
    print("连接节点数:", len(peers))
    print("共识视图:", rpc("getPbftView", [1])["result"])
    # 后端视角
    req = urllib.request.Request(
        "http://127.0.0.1:8080/api/auth/login",
        data=json.dumps({"username": "admin", "password": "123456"}).encode(),
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as r:
        token = json.loads(r.read().decode())["data"]["token"]
    req = urllib.request.Request("http://127.0.0.1:8080/api/chain/status",
                                 headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(req, timeout=15) as r:
        st = json.loads(r.read().decode())["data"]
    print("后端 chain/status: ready=%s block=%s 合约=%s" % (
        st.get("ready"), st.get("blockNumber"), st.get("contractAddress")))
