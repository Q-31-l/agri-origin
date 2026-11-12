"""修 HTTPS：给裸 IP 一条能落地的路，同时把正式证书换成完整链。

背景：
  · 证书只签给了 8-130-105-150.sslip.io（公共 CA 不给纯 IP 发证书）；
  · 直接用 https://8.130.105.150 打 443，SNI 里没有域名，拿到的是域名证书
    → 浏览器报 ERR_CERT_COMMON_NAME_INVALID（"你的连接不是专用连接"）；
  · nginx 现在只发叶子证书（agri.crt，链是断的），部分客户端会报权威问题。

做法：
  1. 生成只覆盖本 IP 的自签证书，做成 443 的 default_server，
     握手成功后 301 到有正式证书的域名 —— 用户点一次"继续前往"就能进；
  2. 域名站点改用 agri-fullchain.crt（叶子 + 中间证书），浏览器不再告警。
"""
import os
import subprocess
import sys

import paramiko

HOST = "8.130.105.150"
USER = "root"
PWD = os.environ["AGRI_PWD"]
CONF = "/etc/nginx/sites-available/default"

NEW_DEFAULT = """# 裸 IP 的 HTTPS 入口：公共 CA 不给 IP 签证书，这里用自签（SAN 只写本机 IP）接住握手，
# 立刻 301 到有正式证书的域名，避免用户停在"证书名字不对"的死页面上。
server {
    listen 443 ssl http2 default_server;
    listen [::]:443 ssl http2 default_server;
    server_name _;

    ssl_certificate     /etc/nginx/ssl/ip.crt;
    ssl_certificate_key /etc/nginx/ssl/ip.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    return 301 https://8-130-105-150.sslip.io$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name 8-130-105-150.sslip.io;
"""


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PWD, timeout=25)

    def run(cmd, show=True):
        _in, out, err = c.exec_command(cmd, timeout=120)
        o = out.read().decode("utf-8", "ignore")
        e = err.read().decode("utf-8", "ignore")
        if show:
            print("$ " + cmd)
            if o.strip():
                print(o.strip())
            if e.strip():
                print("[stderr] " + e.strip())
        return o, e

    print("=== 1. 备份配置")
    run("cp -a %s %s.bak.$(date +%%Y%%m%%d-%%H%%M%%S) && ls -1 %s.bak.* | tail -3" % (CONF, CONF, CONF))

    print("\n=== 2. 生成 IP 自签证书（SAN=IP:8.130.105.150，10 年）")
    run(
        "openssl req -x509 -nodes -newkey rsa:2048 -days 3650 "
        "-subj '/CN=8.130.105.150' -addext 'subjectAltName=IP:8.130.105.150' "
        "-keyout /etc/nginx/ssl/ip.key -out /etc/nginx/ssl/ip.crt && chmod 600 /etc/nginx/ssl/ip.key "
        "&& openssl x509 -in /etc/nginx/ssl/ip.crt -noout -subject -ext subjectAltName"
    )

    print("\n=== 3. 改站点配置")
    sftp = c.open_sftp()
    with sftp.open(CONF, "r") as fh:
        text = fh.read().decode("utf-8")
    orig = text

    old_block = """server {
    listen 443 ssl http2 default_server;
    listen [::]:443 ssl http2 default_server;
    server_name 8-130-105-150.sslip.io;
"""
    if old_block not in text:
        print("[ERR] 没找到 443 站点块，放弃修改")
        return 1
    text = text.replace(old_block, NEW_DEFAULT, 1)

    if "ssl_certificate     /etc/nginx/ssl/agri.crt;" not in text:
        print("[ERR] 没找到 agri.crt 配置行，放弃修改")
        return 1
    text = text.replace(
        "ssl_certificate     /etc/nginx/ssl/agri.crt;",
        "ssl_certificate     /etc/nginx/ssl/agri-fullchain.crt;",
        1,
    )

    if text == orig:
        print("[ERR] 配置没有变化")
        return 1
    with sftp.open(CONF, "w") as fh:
        fh.write(text.encode("utf-8"))
    sftp.close()
    print("已写入，%d -> %d 字节" % (len(orig), len(text)))

    print("\n=== 4. 语法检查并热加载")
    run("nginx -t && systemctl reload nginx && echo RELOADED")

    print("\n=== 5. 证书核对")
    run(
        "echo | openssl s_client -connect 127.0.0.1:443 -servername 8-130-105-150.sslip.io 2>/dev/null "
        "| openssl x509 -noout -subject -ext subjectAltName"
    )
    run(
        "echo | openssl s_client -connect 127.0.0.1:443 2>/dev/null "
        "| openssl x509 -noout -subject -ext subjectAltName"
    )
    run(
        "echo | openssl s_client -connect 127.0.0.1:443 -servername 8-130-105-150.sslip.io -showcerts 2>/dev/null "
        "| grep -c 'BEGIN CERTIFICATE'"
    )
    c.close()
    return 0


sys.exit(main())
