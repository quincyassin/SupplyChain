#!/usr/bin/env bash
# 本地开发：启动 Spring Boot 后端（需 JDK 17+、MySQL 已就绪）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${MYSQL_ROOT_PASSWORD:-root}}"

if [ -z "${JAVA_HOME:-}" ]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 21)"
  fi
fi

MYSQL_RUNNING=0
if command -v docker >/dev/null 2>&1; then
  if docker compose ps mysql 2>/dev/null | grep -q "Up"; then
    MYSQL_RUNNING=1
  fi
fi

if [ "$MYSQL_RUNNING" -eq 0 ]; then
  echo "[提示] MySQL 容器未运行，正在执行 init-mysql.sh ..."
  bash "$(dirname "$0")/init-mysql.sh"
else
  echo "[提示] 若缺表: bash scripts/init-mysql.sh ；已有库补字段: bash scripts/migrate-mysql.sh"
fi

echo "[启动] 后端 http://localhost:8080 （数据库密码来自 SPRING_DATASOURCE_PASSWORD）"
mvn spring-boot:run
