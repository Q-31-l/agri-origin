#!/usr/bin/env bash
# WeBASE-Front 1.5.5 控制台挂到 HTTPS + Basic 认证站点后的两处前端缺陷修复。
# 只补这两个确定的问题，不动厂商 jar、不改其它逻辑。
#
# 问题一：合约IDE 弹「Solc版本加载失败，请检查/static/js目录中是否存在对应的合约版本」
#   不是缺 solc 文件（v0.4.25.js 等 6 个文件都在）。前端把编译器地址写死成 http://：
#       "http://" + this.host + "/WeBASE-Front/static/js/v0.4.25.js"
#   并在 Web Worker 里 importScripts() 加载。HTTPS 页面下浏览器按混合内容直接拦截，
#   请求根本到不了 nginx。改成跟随页面协议 location.protocol 即可。
#
# 问题二：编译成功后合约列表仍是空、「合约调用/保存」偶发 401
#   前端个别接口写死 headers:{Authorization:"Token "+localStorage.getItem("token")||!1}。
#   未登录时 token 为 null，于是发出 Authorization: Token null，把 nginx 的 Basic 认证顶掉 → 401。
#   改成没有 token 就不带这个头，交给浏览器的 Basic 认证；有 token 时行为不变。
#
# 覆盖方式：启动 classpath 是 'conf/:apps/*:lib/*'，conf/ 排在 jar 之前，
#   因此把补丁文件放进 conf/static/static/js/ 即可覆盖 jar 内同名资源。
#   回滚：删掉 conf/static/static/js/ 下对应文件并重启即可。
#
# 用法：bash ops/webase-front-https-fix.sh [/opt/webase-front/webase-front]
set -euo pipefail

ROOT="${1:-/opt/webase-front/webase-front}"
SRC_DIR="$ROOT/static/static/js"
DST_DIR="$ROOT/conf/static/static/js"

[ -d "$SRC_DIR" ] || { echo "[ERR] 找不到 $SRC_DIR，请确认 WeBASE-Front 安装目录"; exit 1; }
mkdir -p "$DST_DIR"

patch_file() {
    local src="$1"
    local base; base=$(basename "$src")
    local dst="$DST_DIR/$base"
    cp -a "$src" "$DST_DIR/$base.bak.$(date +%Y%m%d-%H%M%S)"
    sed -e 's#"http://"+this\.host#location.protocol+"//"+this.host#g' \
        -e 's#headers:{Authorization:"Token "+localStorage.getItem("token")||!1}#headers:localStorage.getItem("token")?{Authorization:"Token "+localStorage.getItem("token")}:{}#g' \
        "$src" > "$dst"
    # 同步 .gz，避免哪天开了 gzip 静态化又把旧内容放回来
    if [ -f "$src.gz" ]; then gzip -9 -c "$dst" > "$dst.gz"; fi
    echo "[OK] $base"
    echo "     solc 协议相对化: $(grep -o 'location.protocol+"//"+this.host' "$dst" | wc -l) 处"
    echo "     Token 空头修复 : $(grep -o 'headers:localStorage.getItem("token")?{Authorization' "$dst" | wc -l) 处"
}

changed=0
for f in "$SRC_DIR"/*.js; do
    if grep -q '"http://"+this.host' "$f" || grep -q 'headers:{Authorization:"Token "+localStorage.getItem("token")' "$f"; then
        patch_file "$f"
        changed=$((changed + 1))
    fi
done

if [ "$changed" -eq 0 ]; then
    echo "[OK] 没有需要修复的文件（已打过补丁，或版本不同）"
else
    echo "[..] 重启 webase-front（静态资源在进程内有缓存，不重启不生效）"
    systemctl restart webase-front
    code=""
    for _ in $(seq 1 30); do
        sleep 2
        code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:5002/WeBASE-Front/ || true)
        [ "$code" = "200" ] && break
    done
    echo "[OK] 控制台 HTTP 状态: $code"
fi

cat <<'EOF'

验收（浏览器，务必用无痕窗口避免旧缓存）：
  1) 打开 https://<站点>/WeBASE-Front/#/contract
     → 不再弹「Solc版本加载失败」；版本下拉显示 v0.4.25
  2) 左侧 template 展开 → 点开 Evidence.sol → 点「编译」
     → 下方出现 contractName / abi / bytecodeBin 结果面板，且无 401
  3) 接口自证（应都是 200，而不是 401）：
     POST /WeBASE-Front/contract/save、POST /WeBASE-Front/method/add

  注意：「合约列表」页只查 contractStatus=2（已部署）的合约，只编译不部署时显示
  「暂无数据」是正常设计，不是没保存成功；本系统的合约由后端 SDK 部署，也不进这张表。

回滚：rm -f /opt/webase-front/webase-front/conf/static/static/js/*.js && systemctl restart webase-front
EOF
