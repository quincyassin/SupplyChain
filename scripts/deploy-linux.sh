#!/usr/bin/env bash
# Linux 一键部署（需已安装 Docker 与 Docker Compose）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

APP_PORT="${APP_PORT:-80}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
COMPOSE_FILE="docker-compose.deploy.yml"

echo "=========================================="
echo "  电商订单分单系统 - Linux 部署"
echo "=========================================="

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未检测到 Docker，请先安装："
  echo "  https://docs.docker.com/engine/install/"
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD=(docker-compose)
else
  echo "[错误] 未检测到 Docker Compose，请安装 Docker Compose 插件"
  exit 1
fi

export APP_PORT MYSQL_ROOT_PASSWORD

echo "[1/4] 构建并启动容器（首次约 3～10 分钟；仅改代码且 pom 未变时通常 1～3 分钟）..."
"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" up -d --build

echo "[2/4] 等待 MySQL 就绪..."
sleep 5

echo "[3/4] 执行数据库增量迁移..."
export COMPOSE_FILE
bash "${ROOT_DIR}/scripts/migrate-mysql.sh"

echo "[4/4] 等待应用就绪..."
sleep 5

BACKEND_OK=0
if command -v curl >/dev/null 2>&1; then
  for _ in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:${APP_PORT}/api/orders/fields" >/dev/null 2>&1; then
      BACKEND_OK=1
      break
    fi
    sleep 2
  done
fi

echo "部署完成"
echo ""
echo "  访问地址: http://localhost:${APP_PORT}"
echo "  MySQL:    localhost:3306（仅容器内网，未映射宿主机端口）"
echo "  默认库:   order_split_merge / 用户 root / 密码 ${MYSQL_ROOT_PASSWORD}"
echo ""
echo "常用命令:"
echo "  查看日志: ${COMPOSE_CMD[*]} -f ${COMPOSE_FILE} logs -f"
echo "  库表升级: COMPOSE_FILE=${COMPOSE_FILE} bash scripts/migrate-mysql.sh"
echo "  停止服务: bash scripts/stop-linux.sh"
echo ""

if [ "$BACKEND_OK" -eq 0 ]; then
  echo "[提示] 后端仍在启动中，若无法访问请稍等片刻后刷新，或查看日志"
fi
