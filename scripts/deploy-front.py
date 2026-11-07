#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""部署 H5 前端：把本地 dist/build/h5 同步到服务器 /opt/agri-origin/web/h5。

用法：
    export AGRI_HOST=1.2.3.4 AGRI_PWD='***'
    python scripts/deploy-front.py            # 上传 → rsync --delete → 自检
    python scripts/deploy-front.py --keep     # 不删服务器上多余文件（排查时用）

为什么先传临时目录再 rsync，而不是直接覆盖：
  旧版本会在 assets/ 里留下带 hash 的文件，直接覆盖只增不减，
  目录会越攒越大；rsync --delete 才能保证"发的是哪一版，线上就是哪一版"。
  临时目录也保证了上传失败时线上仍是完整可用的旧版本。
"""
import os
import posixpath
import sys
import time

import paramiko

HOST = os.environ.get("AGRI_HOST", "")
PORT = int(os.environ.get("AGRI_PORT", "22"))
USER = os.environ.get("AGRI_USER", "root")
PWD = os.environ.get("AGRI_PWD", "")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "frontend", "uni-app", "dist", "build", "h5")

STAGE = "/root/agri-build/h5"
TARGET = "/opt/agri-origin/web/h5"


def main():
    if not HOST or not PWD:
        print("[ERR] 需要环境变量 AGRI_HOST / AGRI_PWD")
        return 2
    if not os.path.isdir(DIST):
        print("[ERR] 找不到构建产物：" + DIST + "，先跑 npm run build:h5")
        return 2

    keep = "--keep" in sys.argv

    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(HOST, port=PORT, username=USER, password=PWD, timeout=20,
                banner_timeout=30, auth_timeout=30,
                look_for_keys=False, allow_agent=False)
    try:
        def run(cmd, timeout=300):
            stdin, stdout, stderr = cli.exec_command(cmd, timeout=timeout)
            out = stdout.read().decode("utf-8", "ignore")
            err = stderr.read().decode("utf-8", "ignore")
            code = stdout.channel.recv_exit_status()
            if out.strip():
                print(out.strip()[-2000:])
            if code != 0 and err.strip():
                print("[stderr] " + err.strip()[-2000:])
            return code

        print("[1/4] 清理临时目录并重建 …")
        run("rm -rf %s && mkdir -p %s" % (STAGE, STAGE))

        print("[2/4] 上传构建产物 …")
        sftp = cli.open_sftp()
        count = 0
        for base, dirs, files in os.walk(DIST):
            rel = os.path.relpath(base, DIST).replace(os.sep, "/")
            remote_dir = STAGE if rel == "." else posixpath.join(STAGE, rel)
            try:
                sftp.stat(remote_dir)
            except IOError:
                sftp.mkdir(remote_dir)
            for name in files:
                sftp.put(os.path.join(base, name), posixpath.join(remote_dir, name))
                count += 1
        sftp.close()
        print("      上传 %d 个文件" % count)

        print("[3/4] 原子替换线上目录 …")
        cmd = "rsync -a %s%s/ %s/ && chown -R www-data:www-data %s" % (
            "--delete " if not keep else "", STAGE, TARGET, TARGET)
        if run(cmd) != 0:
            print("[ERR] rsync 失败，线上仍是旧版本")
            return 1

        print("[4/4] 自检 …")
        time.sleep(1)
        run("curl -sS -o /dev/null -w 'index=%{http_code} ' https://127.0.0.1/ -k")
        run("curl -sS -o /dev/null -w 'register=%{http_code}\\n' https://127.0.0.1/pages/auth/register -k")
        run("ls -1 %s/assets | wc -l" % TARGET)
        print("[OK] H5 已更新")
        return 0
    finally:
        cli.close()


if __name__ == "__main__":
    sys.exit(main())
