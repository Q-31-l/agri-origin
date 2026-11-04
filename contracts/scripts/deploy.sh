#!/usr/bin/env bash
# =============================================================
#  agri-origin · 合约部署脚本
#
#  前置条件：
#    1. 已接入 FISCO-BCOS 2.9.x 节点，节点 certificate 已放到
#       backend/src/main/resources/fisco/conf/{ca.crt,sdk.crt,sdk.key}
#    2. backend/src/main/resources/fisco/config.toml 中 peers 指向真实节点
#    3. application.yml 中 chain.enabled=true（部署阶段允许 contract-address 暂空）
#    4. 本机已安装 JDK 8 与 Maven
#
#  用法：
#    bash contracts/scripts/deploy.sh
#    bash contracts/scripts/deploy.sh --group-id 1 --private-key <hex>
#
#  部署成功后：
#    把输出的合约地址写入 application.yml 的 chain.contract-address
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
JAR="${ROOT_DIR}/backend/target/agri-trace-backend.jar"

echo "==> 仓库根目录: ${ROOT_DIR}"

if [ ! -f "${JAR}" ]; then
  echo "==> 未找到后端 Jar，开始构建..."
  ( cd "${ROOT_DIR}/backend" && mvn -DskipTests clean package )
fi

if [ ! -f "${JAR}" ]; then
  echo "[FAIL] 构建失败，未生成 ${JAR}" >&2
  exit 1
fi

echo "==> 开始部署合约..."
cd "${ROOT_DIR}"
java -cp "${JAR}" \
     -Dloader.main=com.agri.trace.sdk.ContractDeployTool \
     org.springframework.boot.loader.PropertiesLauncher "$@"
