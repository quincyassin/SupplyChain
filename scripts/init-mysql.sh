#!/usr/bin/env bash
# 启动 Docker MySQL 并执行全量建表脚本
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
SCHEMA_FILE="${ROOT_DIR}/docs/schema-all.sql"

echo "=========================================="
echo "  初始化 MySQL（Docker + 建表）"
echo "=========================================="

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未检测到 Docker，请先安装 Docker"
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD=(docker-compose)
else
  echo "[错误] 未检测到 Docker Compose"
  exit 1
fi

if [ ! -f "$SCHEMA_FILE" ]; then
  echo "[错误] 找不到建表脚本: $SCHEMA_FILE"
  exit 1
fi

echo "[1/4] 启动 MySQL 容器..."
"${COMPOSE_CMD[@]}" up -d mysql

echo "[2/4] 等待 MySQL 就绪..."
READY=0
for _ in $(seq 1 40); do
  if "${COMPOSE_CMD[@]}" exec -T mysql mysqladmin ping -h127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 2
done

if [ "$READY" -eq 0 ]; then
  echo "[错误] MySQL 启动超时，请查看: ${COMPOSE_CMD[*]} logs mysql"
  exit 1
fi

echo "[3/4] 执行建表脚本 schema-all.sql ..."
"${COMPOSE_CMD[@]}" exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <"$SCHEMA_FILE"

echo "[4/4] 执行增量迁移（补字段/索引，可重复）..."
bash "${ROOT_DIR}/scripts/migrate-mysql.sh"

echo ""
echo "  数据库: order_split_merge"
echo "  地址:   127.0.0.1:3306"
echo "  用户:   root / 密码: ${MYSQL_ROOT_PASSWORD}"
echo ""
echo "  全量建表: docs/schema-all.sql"
echo "  增量迁移: docs/migrations/*.sql  （单独升级: bash scripts/migrate-mysql.sh）"
echo "  分表脚本: docs/schema-*.sql"
echo ""
echo "  下一步: bash scripts/start-backend.sh"
