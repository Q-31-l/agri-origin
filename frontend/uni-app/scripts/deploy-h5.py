#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 dist/build/h5 同步到服务器 Web 根目录（先传临时目录再原子替换）。

用法：
    export AGRI_HOST=1.2.3.4 AGRI_PWD='***'   # 口令只从环境变量读，不写进仓库
    python scripts/deploy-h5.py            # 上传并切换
    python scripts/deploy-h5.py --dry-run  # 只统计文件数，不连接

设计要点：
- 先完整传到 h5.new，成功后 mv 旧目录为 h5.bak.<时间戳>，再把 h5.new 改名 —— 
  避免传到一半 nginx 直接对外提供残缺产物。
- 只保留最近 3 份备份，防止磁盘被历史产物吃满（每份约 6MB）。
"""
import os
import sys
import time

import paramiko

HOST = os.environ.get("AGRI_HOST", "")
PORT = int(os.environ.get("AGRI_PORT", "22"))
USER = os.environ.get("AGRI_USER", "root")
PWD = os.environ.get("AGRI_PWD", "")

WEB_ROOT = "/opt/agri-origin/web"
TMP_DIR = WEB_ROOT + "/h5.new"
LOCAL = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                     "dist", "build", "h5")


def walk(local_root):
    for base, _dirs, files in os.walk(local_root):
        for name in files:
            full = os.path.join(base, name)
            yield full, os.path.relpath(full, local_root).replace(os.sep, "/")


def main():
    dry = "--dry-run" in sys.argv
    if not HOST or not PWD:
        print("[ERR] 需要环境变量 AGRI_HOST / AGRI_PWD（不要把口令写进仓库）")
        return 2

    if not os.path.isdir(LOCAL):
        print("[ERR] 本地产物不存在，先跑 npm run build:h5: %s" % LOCAL)
        return 2

    items = list(walk(LOCAL))
    total = sum(os.path.getsize(p) for p, _ in items)
    print("[OK] 本地产物 %d 个文件 / %.1f MB" % (len(items), total / 1048576.0))
    if dry:
        return 0

    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(HOST, port=PORT, username=USER, password=PWD, timeout=20,
                banner_timeout=30, auth_timeout=30,
                look_for_keys=False, allow_agent=False)
    try:
        def run(cmd):
            _in, out, err = cli.exec_command(cmd, timeout=180)
            o = out.read().decode("utf-8", "replace")
            e = err.read().decode("utf-8", "replace")
            return out.channel.recv_exit_status(), o.strip(), e.strip()

        run("rm -rf %s && mkdir -p %s" % (TMP_DIR, TMP_DIR))

        sftp = cli.open_sftp()
        done = 0
        for local_path, rel in items:
            remote = TMP_DIR + "/" + rel
            # 逐级建目录（SFTP 不会替你建父目录）
            parts = rel.split("/")[:-1]
            cur = TMP_DIR
            for seg in parts:
                cur = cur + "/" + seg
                try:
                    sftp.stat(cur)
                except IOError:
                    sftp.mkdir(cur)
            with open(local_path, "rb") as fh:
                # 必须用 putfo：put() 在部分服务器上 getcwd() 为 None 会抛 ENOENT
                sftp.putfo(fh, remote)
            done += 1
            if done % 40 == 0:
                print("  ... %d/%d" % (done, len(items)))
        sftp.close()
        print("[OK] 上传完成 %d 个文件" % done)

        # 权威性校验：临时目录里文件数必须与本地一致，否则不切换
        _c, o, _e = run("find %s -type f | wc -l" % TMP_DIR)
        remote_cnt = int(o or 0)
        if remote_cnt != len(items):
            print("[ERR] 远端 %d != 本地 %d，放弃切换（线上仍是旧版本）"
                  % (remote_cnt, len(items)))
            return 3

        stamp = time.strftime("%Y%m%d-%H%M%S")
        run("cd %s && (mv h5 h5.bak.%s || true) && mv h5.new h5 && chmod -R a+rX h5"
            % (WEB_ROOT, stamp))
        # 只留最近 3 份备份
        run("cd %s && ls -1dt h5.bak.* 2>/dev/null | tail -n +4 | xargs -r rm -rf" % WEB_ROOT)

        _c, o, _e = run("ls -1dt %s/h5.bak.* 2>/dev/null | head -1" % WEB_ROOT)
        print("[OK] 已切换，旧版本备份：%s" % (o or "(无)"))
        _c, o, _e = run("ls -1 %s/h5 | head -20" % WEB_ROOT)
        print("[OK] 新目录内容:\n%s" % o)
        return 0
    finally:
        cli.close()


if __name__ == "__main__":
    sys.exit(main())
