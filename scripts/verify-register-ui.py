#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""注册入口的界面验收（本机执行，用 Edge 无头跑线上页面）。

做什么：
  1. 桌面 1440×900：登录页应出现「立即注册」→ 点进去 → 注册页截图 → 填表提交 → 落到 PC 看板
  2. 手机 390×844：登录页与注册页的移动版式截图
  3. 门户首页：Web 入口卡里应同时有「立即进入」和「注册账号」

用法：
    python scripts/verify-register-ui.py
环境变量：AGRI_SITE（默认 https://8-130-105-150.sslip.io）
产物：docs/assets/screenshots/register-*.png
"""
import os
import re
import sys
import time

from playwright.sync_api import sync_playwright

SITE = os.environ.get("AGRI_SITE", "https://8-130-105-150.sslip.io")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOT = os.path.join(ROOT, "docs", "assets", "screenshots")
os.makedirs(SHOT, exist_ok=True)

ok, fail = [], []


def check(name, cond, detail=""):
    (ok if cond else fail).append(name)
    print(("  [PASS] " if cond else "  [FAIL] ") + name + ("" if cond else "  -> " + str(detail)[:200]))


def pick_village(page):
    """点开自绘的选村面板，选第一项。"""
    page.locator("text=所属村").first.click()
    page.wait_for_selector(".v-item", timeout=8000)
    page.locator(".v-item").first.click()
    page.wait_for_timeout(400)
    return page.locator(".v-item").count() == 0


def main():
    suffix = str(int(time.time()))[-6:]
    username = "uifarmer" + suffix

    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)

        # ---------- 桌面 ----------
        ctx = browser.new_context(viewport={"width": 1440, "height": 900},
                                  ignore_https_errors=True)
        page = ctx.new_page()
        page.goto(SITE + "/pages/auth/login", wait_until="networkidle")
        page.wait_for_timeout(1200)
        check("PC 登录页有「立即注册」", page.locator("text=立即注册").count() > 0)
        page.screenshot(path=os.path.join(SHOT, "register-01-login-pc.png"))

        page.locator("text=立即注册").first.click()
        page.wait_for_timeout(1500)
        page.screenshot(path=os.path.join(SHOT, "register-02-form-pc.png"))
        check("PC 注册页打开", "/pages/auth/register" in page.url, page.url)
        check("PC 注册页有身份选择", page.locator("text=销售商").count() > 0)

        # 填表
        inputs = page.locator("input")
        inputs.nth(0).fill("界面验收农户")          # 姓名
        inputs.nth(1).fill("139" + suffix + "00")   # 手机号
        inputs.nth(2).fill(username)                # 账号
        inputs.nth(3).fill("ui2026pass")            # 密码
        inputs.nth(4).fill("ui2026pass")            # 确认密码
        picked = pick_village(page)
        check("PC 注册页可选村", picked)
        page.screenshot(path=os.path.join(SHOT, "register-03-filled-pc.png"))

        page.locator("text=注册并登录").first.click()
        page.wait_for_timeout(4000)
        check("PC 注册后进入看板", "/pages-pc/dashboard" in page.url, page.url)
        page.screenshot(path=os.path.join(SHOT, "register-04-after-pc.png"))

        # ---------- 手机 ----------
        m = browser.new_context(viewport={"width": 390, "height": 844},
                                ignore_https_errors=True, is_mobile=True,
                                has_touch=True, device_scale_factor=2)
        mp = m.new_page()
        mp.goto(SITE + "/pages/auth/login", wait_until="networkidle")
        mp.wait_for_timeout(1200)
        mp.screenshot(path=os.path.join(SHOT, "register-05-login-mobile.png"))
        check("移动登录页有注册入口", mp.locator("text=自助注册").count() > 0)

        mp.locator("text=自助注册").first.click()
        mp.wait_for_timeout(1500)
        mp.screenshot(path=os.path.join(SHOT, "register-06-form-mobile.png"), full_page=True)
        check("移动注册页打开", "/pages/auth/register" in mp.url, mp.url)

        # ---------- 门户 ----------
        pp = ctx.new_page()
        pp.goto(SITE + "/", wait_until="networkidle")
        pp.wait_for_timeout(800)
        check("门户入口卡有「注册账号」", pp.locator("a.entry-btn.ghost").count() > 0)
        pp.locator("#entry").scroll_into_view_if_needed()
        pp.wait_for_timeout(600)
        pp.screenshot(path=os.path.join(SHOT, "register-07-portal-entry.png"))

        browser.close()

    print("\n结果：%d 通过 / %d 失败" % (len(ok), len(fail)))
    if fail:
        print("失败项：" + "；".join(fail))
    print("UI_USERNAME " + username)
    return 0 if not fail else 1


if __name__ == "__main__":
    sys.exit(main())
