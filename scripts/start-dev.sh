#!/usr/bin/env bash
# 本地一键开发：MySQL 建表 + 前端 + 后端（Ctrl+C 同时结束前端）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

bash "$SCRIPT_DIR/init-mysql.sh"

FRONTEND_PID=""
cleanup() {
  if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "[启动] 前端 http://localhost:5173"
bash "$SCRIPT_DIR/start-frontend.sh" &
FRONTEND_PID=$!

echo "[启动] 后端 http://localhost:8080"
bash "$SCRIPT_DIR/start-backend.sh"
