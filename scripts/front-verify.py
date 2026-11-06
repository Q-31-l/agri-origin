"""用 WeBASE-Front 的 REST 接口回读业务交易，证明它看到的是同一条链的真实数据"""
import base64
import json
import os
import subprocess
import urllib.error
import urllib.request

# 默认走本机直连；走 nginx 反代时改成 http://<IP>/WeBASE-Front 并填 Basic 账号
FRONT = os.environ.get("FRONT_BASE", "http://127.0.0.1:5002/WeBASE-Front")
USER = os.environ.get("FRONT_USER", "webase")
PASSWORD = os.environ.get("FRONT_PASS", "Agri@2026")
AUTH = base64.b64encode(("%s:%s" % (USER, PASSWORD)).encode()).decode()


def front(path):
    req = urllib.request.Request(FRONT + path, headers={"Authorization": "Basic " + AUTH})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"__http_error__": e.code}


def sql(q):
    out = subprocess.run(["mysql", "-uroot", "-proot", "agri_trace", "-N", "-e", q],
                         capture_output=True, text=True).stdout.strip()
    return [l.split("\t") for l in out.split("\n") if l]


rows = sql("SELECT tx_hash, block_number, biz_type, batch_no FROM chain_record "
           "WHERE status='SUCCESS' AND tx_hash IS NOT NULL ORDER BY id DESC LIMIT 1")
tx, block, biz, batch = rows[0]
print("业务存证: %s..." % tx[:24])
print("  批次=%s 类型=%s 区块=%s" % (batch, biz, block))

bn = front("/1/web3/blockNumber")
print("Front 区块高度:", bn.get("data") if isinstance(bn, dict) else bn)

blk = front("/1/web3/blockByNumber/%s" % block)
data = blk.get("data") if isinstance(blk, dict) and isinstance(blk.get("data"), dict) else blk
if not isinstance(data, dict):
    print("区块查询返回:", str(blk)[:160])
else:
    txs = data.get("transactions") or []
    print("区块 %s 交易数: %d" % (block, len(txs)))
    match = [t for t in txs if (t.get("hash") or "").lower() == tx.lower()]
    print("命中业务交易:", "是" if match else "否")
    if match:
        t = match[0]
        print("  from=%s" % (t.get("from") or "")[:20])
        print("  to=%s" % (t.get("to") or "")[:20])
        print("  input 长度=%d 字节" % (len((t.get("input") or "").replace("0x", "")) // 2))

for path in ("/1/web3/transReceipt/%s" % tx, "/1/web3/transactionReceipt/%s" % tx):
    rc = front(path)
    if "__http_error__" not in rc:
        rdata = rc.get("data") if isinstance(rc, dict) and isinstance(rc.get("data"), dict) else rc
        if isinstance(rdata, dict):
            print("回执: status=%s 区块=%s gasUsed=%s" % (
                rdata.get("status"), rdata.get("blockNumber"), rdata.get("gasUsed")))
            break

info = front("/1/web3/nodeInfo")
idata = info.get("data") if isinstance(info, dict) and info.get("data") else info
print("节点信息:", json.dumps(idata, ensure_ascii=False)[:200] if idata else str(info)[:160])

cnt = front("/1/web3/blockTransCnt/%s" % block)
print("区块交易计数接口:", json.dumps(cnt, ensure_ascii=False)[:120])
