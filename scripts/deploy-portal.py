#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""部署官网总门户：上传静态页 + 更新 nginx（门户接管 / 与 /portal/，H5 保持原路径）。

用法：
    export AGRI_HOST=1.2.3.4 AGRI_PWD='***'
    python scripts/deploy-portal.py

做了什么：
  1. 上传 portal/index.html → /opt/agri-origin/web/portal/index.html
  2. 备份并替换 /etc/nginx/sites-available/default（含门户 location）
  3. nginx -t 通过才 reload
  4. 自检：/ 返回门户、/portal/ 返回门户、/pages/launch/index 仍返回 H5
"""
import os
import sys
import time

import paramiko

HOST = os.environ.get("AGRI_HOST", "")
PORT = int(os.environ.get("AGRI_PORT", "22"))
USER = os.environ.get("AGRI_USER", "root")
PWD = os.environ.get("AGRI_PWD", "")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCAL_HTML = os.path.join(ROOT, "portal", "index.html")
LOCAL_NGINX = os.path.join(ROOT, "ops", "nginx-agri-origin.conf")

REMOTE_DIR = "/opt/agri-origin/web/portal"
REMOTE_HTML = REMOTE_DIR + "/index.html"
NGINX_SITE = "/etc/nginx/sites-available/default"


def main():
    if not HOST or not PWD:
        print("[ERR] 需要环境变量 AGRI_HOST / AGRI_PWD")
        return 2
    if not os.path.exists(LOCAL_HTML):
        print("[ERR] 缺少 " + LOCAL_HTML)
        return 2

    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(HOST, port=PORT, username=USER, password=PWD, timeout=25,
                banner_timeout=30, auth_timeout=30, look_for_keys=False, allow_agent=False)

    def run(cmd, timeout=120):
        _i, out, err = cli.exec_command(cmd, timeout=timeout)
        o = out.read().decode("utf-8", "replace")
        e = err.read().decode("utf-8", "replace")
        return out.channel.recv_exit_status(), (o + e).strip()

    try:
        code, out = run("mkdir -p %s && echo ok" % REMOTE_DIR)
        print("[%s] 目录: %s" % ("OK" if code == 0 else "ERR", out))

        sftp = cli.open_sftp()
        sftp.put(LOCAL_HTML, REMOTE_HTML)
        sftp.close()
        code, out = run("ls -l %s && md5sum %s" % (REMOTE_HTML, REMOTE_HTML))
        print("[OK] 页面上传:\n%s" % out)

        stamp = time.strftime("%Y%m%d-%H%M%S")
        run("cp -a %s %s.bak.%s" % (NGINX_SITE, NGINX_SITE, stamp))
        print("[OK] nginx 配置已备份: %s.bak.%s" % (NGINX_SITE, stamp))

        sftp = cli.open_sftp()
        sftp.put(LOCAL_NGINX, NGINX_SITE)
        sftp.close()

        code, out = run("nginx -t")
        print("[%s] nginx -t:\n%s" % ("OK" if code == 0 else "ERR", out))
        if code != 0:
            run("cp -a %s.bak.%s %s" % (NGINX_SITE, stamp, NGINX_SITE))
            print("[ERR] 配置校验失败，已回滚，未 reload")
            return 3

        code, out = run("systemctl reload nginx && echo reloaded")
        print("[%s] %s" % ("OK" if code == 0 else "ERR", out))

        time.sleep(1)
        print("\n=== 自检 ===")
        for name, path, expect in [
            ("门户 /", "/", "农安链"),
            ("门户 /portal/", "/portal/", "农安链"),
            ("业务系统 H5", "/pages/launch/index", "html"),
        ]:
            code, out = run(
                "curl -sk -o /tmp/p.html -w '%%{http_code}' https://127.0.0.1%s "
                "-H 'Host: 8-147-61-0.sslip.io' && grep -c '%s' /tmp/p.html" % (path, expect))
            print("  %-16s -> %s" % (name, out.replace("\n", " ")))

        return 0
    finally:
        cli.close()


if __name__ == "__main__":
    sys.exit(main())
