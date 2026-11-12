#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""上传门户首页到 /opt/agri-origin/web/portal/index.html（先传 .new 再原子替换，保留上一版）。

用法：
    export AGRI_HOST=1.2.3.4 AGRI_PWD='***'
    python scripts/deploy-portal.py

门户首页是独立静态页，不属于 H5 产物，所以单独一个脚本。
只在改了 portal/index.html 时才需要跑；H5 发布用 scripts/deploy-h5.py。
"""
import os
import sys
import time

import paramiko

HOST = os.environ.get("AGRI_HOST", "")
PORT = int(os.environ.get("AGRI_PORT", "22"))
USER = os.environ.get("AGRI_USER", "root")
PWD = os.environ.get("AGRI_PWD", "")
LOCAL = "C:/Users/30786/Desktop/毕设农产品/portal/index.html"
DEST_DIR = "/opt/agri-origin/web/portal"


def main():
    if not HOST or not PWD:
        print("[ERR] 需要 AGRI_HOST / AGRI_PWD")
        return 2

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, port=PORT, username=USER, password=PWD, timeout=20)
    s = c.open_sftp()

    size = os.path.getsize(LOCAL)
    print("[1/3] 上传 %.1f KB -> %s/index.html.new" % (size / 1024.0, DEST_DIR))
    t = time.time()
    s.put(LOCAL, DEST_DIR + "/index.html.new")
    print("      完成，用时 %.1fs" % (time.time() - t))

    stamp = time.strftime("%Y%m%d-%H%M%S")
    cmd = (
        "set -e; cd %s; "
        "if [ -f index.html ]; then cp -p index.html index.html.%s; fi; "
        "mv index.html.new index.html; "
        "ls -l index.html; "                                            
        "ls -1t index.html.2* 2>/dev/null | tail -n +4 | xargs -r rm -f"
    ) % (DEST_DIR, stamp)
    print("[2/3] 原子替换并保留最近 3 份备份")
    _in, out, err = c.exec_command(cmd)
    print(out.read().decode("utf-8", "ignore").strip())
    e = err.read().decode("utf-8", "ignore").strip()
    if e:
        print("      stderr:", e)

    print("[3/3] 校验线上大小")
    _in, out, err = c.exec_command("stat -c %%s %s/index.html" % DEST_DIR)
    remote = out.read().decode().strip()
    print("      本地 %d / 线上 %s %s" % (size, remote, "一致" if remote == str(size) else "不一致"))

    s.close()
    c.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
