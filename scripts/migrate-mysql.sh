#!/usr/bin/env bash
# 在 Docker MySQL 上执行 docs/migrations/*.sql（已有库升级，可重复执行）
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
MYSQL_DATABASE="${MYSQL_DATABASE:-order_split_merge}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
MIGRATIONS_DIR="${ROOT_DIR}/docs/migrations"

echo "=========================================="
echo "  MySQL 增量迁移"
echo "=========================================="

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未检测到 Docker"
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

if [ ! -d "$MIGRATIONS_DIR" ]; then
  echo "[错误] 找不到迁移目录: $MIGRATIONS_DIR"
  exit 1
fi

MIGRATION_FILES=()
while IFS= read -r migration_path; do
  MIGRATION_FILES+=("$migration_path")
done < <(find "$MIGRATIONS_DIR" -maxdepth 1 -name '*.sql' -type f | sort)

if [ "${#MIGRATION_FILES[@]}" -eq 0 ]; then
  echo "[提示] 无迁移脚本，跳过"
  exit 0
fi

if ! "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" ps mysql 2>/dev/null | grep -q "Up"; then
  echo "[错误] MySQL 容器未运行，请先启动："
  echo "  ${COMPOSE_CMD[*]} -f ${COMPOSE_FILE} up -d mysql"
  exit 1
fi

READY=0
for _ in $(seq 1 30); do
  if "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" exec -T mysql \
    mysqladmin ping -h127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" -eq 0 ]; then
  echo "[错误] MySQL 未就绪，请查看: ${COMPOSE_CMD[*]} -f ${COMPOSE_FILE} logs mysql"
  exit 1
fi

echo "Compose 文件: ${COMPOSE_FILE}"
echo "目标数据库:   ${MYSQL_DATABASE}"
echo "迁移目录:     docs/migrations/"
echo ""

for sql_file in "${MIGRATION_FILES[@]}"; do
  echo "[迁移] $(basename "$sql_file")"
  "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" exec -T mysql \
    mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" <"$sql_file"
done

echo ""
echo "  迁移完成（共 ${#MIGRATION_FILES[@]} 个脚本）"
