#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""一条命令发布前端：H5 产物 + 门户首页。

用法：
    python scripts/deploy.py              # 交互输入密码（不回显）
    AGRI_PWD='***' python scripts/deploy.py   # 也可以在环境变量里给

为什么要两个脚本：
    dist/build/h5 是 uni-app 产物，由 frontend/uni-app/scripts/deploy-h5.py 上传；
    portal/index.html 是独立静态页（官网门户），不在这份产物里，单独上传。
    两者都是"先传临时目录、成功后再原子替换"，传一半断了不会把线上打成半残，
    并且各自保留历史备份。

其它环境变量：
    AGRI_HOST  服务器地址，默认 8.130.105.150
    AGRI_USER  登录用户，默认 root
    AGRI_PORT  SSH 端口，默认 22
"""
import getpass
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOST = os.environ.get("AGRI_HOST") or "8.130.105.150"
USER = os.environ.get("AGRI_USER") or "root"

STEPS = [
    ("H5 产物  dist/build/h5", os.path.join(ROOT, "frontend", "uni-app", "scripts", "deploy-h5.py")),
    ("门户首页 portal/index.html", os.path.join(ROOT, "scripts", "deploy-portal.py")),
]


def main():
    pwd = os.environ.get("AGRI_PWD")
    if not pwd:
        try:
            pwd = getpass.getpass("服务器 %s@%s 的密码（输入时不显示）：" % (USER, HOST))
        except (KeyboardInterrupt, EOFError):
            print("\n已取消")
            return 2
    if not pwd:
        print("[ERR] 密码为空")
        return 2

    env = dict(os.environ, AGRI_HOST=HOST, AGRI_USER=USER, AGRI_PWD=pwd)
    failed = []
    for label, script in STEPS:
        print("\n=== 上传 %s" % label)
        if not os.path.exists(script):
            print("[ERR] 找不到脚本 %s" % script)
            failed.append(label)
            continue
        code = subprocess.call([sys.executable, script], cwd=ROOT, env=env)
        if code != 0:
            print("[ERR] %s 失败（退出码 %d）" % (label, code))
            failed.append(label)

    print("\n=== 结果")
    if failed:
        print("失败：%s" % "、".join(failed))
        return 1
    print("全部上传完成。可用下面的地址复核：")
    print("  官网门户  https://%s/portal/" % HOST)
    print("  业务系统  https://%s/pages/launch/index" % HOST)
    return 0


if __name__ == "__main__":
    sys.exit(main())
