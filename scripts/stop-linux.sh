#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="docker-compose.deploy.yml"

if docker compose version >/dev/null 2>&1; then
  docker compose -f "$COMPOSE_FILE" down
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f "$COMPOSE_FILE" down
else
  echo "未找到 Docker Compose"
  exit 1
fi

echo "服务已停止（数据库数据卷 mysql_data 已保留）"
