#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""在服务器上编译后端 jar 并热替换（本机没有 JDK/Maven，只能借服务器构建）。

用法：
    export AGRI_HOST=1.2.3.4 AGRI_PWD='***'   # 口令只从环境变量读，不写进仓库
    python scripts/deploy-backend.py            # 上传源码 → mvn package → 换 jar → 重启
    python scripts/deploy-backend.py --skip-build  # 只重启（jar 已在位时用）

为什么要传源码而不是传 jar：本机无 JDK/Maven，jar 必须由服务器上的
mvn 3.6.3 + JDK 8 产出，才能和线上运行环境一致。
"""
import hashlib
import os
import sys
import time

import paramiko

HOST = os.environ.get("AGRI_HOST", "")
PORT = int(os.environ.get("AGRI_PORT", "22"))
USER = os.environ.get("AGRI_USER", "root")
PWD = os.environ.get("AGRI_PWD", "")

BUILD_DIR = "/root/agri-build/backend"
APP_DIR = "/opt/agri-origin/app"
JAR = APP_DIR + "/agri-trace-backend.jar"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCAL_BACKEND = os.path.join(ROOT, "backend")


def sha256_local(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def walk(root):
    for base, _d, files in os.walk(root):
        for name in files:
            full = os.path.join(base, name)
            yield full, os.path.relpath(full, root).replace(os.sep, "/")


def main():
    skip_build = "--skip-build" in sys.argv
    if not HOST or not PWD:
        print("[ERR] 需要环境变量 AGRI_HOST / AGRI_PWD（不要把口令写进仓库）")
        return 2

    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(HOST, port=PORT, username=USER, password=PWD, timeout=20,
                banner_timeout=30, auth_timeout=30,
                look_for_keys=False, allow_agent=False)
    try:
        def run(cmd, timeout=900):
            _i, out, err = cli.exec_command(cmd, timeout=timeout)
            o = out.read().decode("utf-8", "replace")
            e = err.read().decode("utf-8", "replace")
            return out.channel.recv_exit_status(), o, e

        if not skip_build:
            run("rm -rf %s && mkdir -p %s/src" % (BUILD_DIR, BUILD_DIR))
            sftp = cli.open_sftp()
            n = 0
            src_root = os.path.join(LOCAL_BACKEND, "src")
            items = list(walk(src_root))
            for local_path, rel in items:
                remote = BUILD_DIR + "/src/" + rel
                cur = BUILD_DIR + "/src"
                for seg in rel.split("/")[:-1]:
                    cur = cur + "/" + seg
                    try:
                        sftp.stat(cur)
                    except IOError:
                        sftp.mkdir(cur)
                with open(local_path, "rb") as fh:
                    sftp.putfo(fh, remote)
                n += 1
            with open(os.path.join(LOCAL_BACKEND, "pom.xml"), "rb") as fh:
                sftp.putfo(fh, BUILD_DIR + "/pom.xml")
            sftp.close()
            print("[OK] 源码上传 %d 个文件" % n)

            print("[..] mvn package（首次可能几分钟）")
            code, o, e = run("cd %s && mvn -q -B -DskipTests package 2>&1 | tail -25" % BUILD_DIR)
            tail = (o or e).strip()
            if tail:
                print(tail)
            code, o, e = run("ls -l %s/target/*.jar 2>/dev/null | grep -v original" % BUILD_DIR)
            if code != 0 or ".jar" not in o:
                print("[ERR] 编译失败，线上未改动")
                return 3
            print("[OK] 产物: %s" % o.strip())

        # 备份 + 替换 + 重启
        stamp = time.strftime("%Y%m%d-%H%M%S")
        run("cp -a %s %s.bak.%s 2>/dev/null || true" % (JAR, JAR, stamp))
        code, o, e = run("cp -f %s/target/agri-trace-backend*.jar %s && sha256sum %s"
                         % (BUILD_DIR, JAR, JAR))
        print("[OK] 换 jar: %s" % o.strip())

        run("systemctl restart agri-backend")
        print("[..] 等待后端就绪")
        ok = False
        for _ in range(30):
            time.sleep(2)
            # 必须直连 8080：站点的 80 端口已 301 到 HTTPS，curl 不加 -L 只会拿到 301
            code, o, e = run("curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/chain/status")
            if o.strip() in ("200", "401"):
                ok = True
                break
        code, o, e = run("systemctl is-active agri-backend")
        print("[%s] 单元状态: %s" % ("OK" if ok else "ERR", o.strip()))

        code, o, e = run("grep -c ' ERROR ' %s/backend.log 2>/dev/null || echo 0" % APP_DIR)
        print("[OK] 启动日志 ERROR 行数: %s（全量历史，非本次）" % o.strip())
        code, o, e = run("tail -n 400 %s/backend.log | grep -E 'Started AgriTraceApplication| ERROR ' | tail -5" % APP_DIR)
        if o.strip():
            print("[OK] 近期日志:\n%s" % o.strip())
        return 0 if ok else 4
    finally:
        cli.close()


if __name__ == "__main__":
    sys.exit(main())
