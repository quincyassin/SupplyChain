#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

echo "正在停止分单宝..."
bash "$ROOT_DIR/stop-service.sh"
sleep 1
echo "已停止。"
