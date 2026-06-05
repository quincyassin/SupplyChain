#!/usr/bin/env bash
# 等待本机服务在 8080 端口就绪
set -euo pipefail

URI="${1:-http://127.0.0.1:8080}"
MAX_ATTEMPTS="${2:-60}"

for ((attempt = 0; attempt < MAX_ATTEMPTS; attempt++)); do
  if curl -fsS -o /dev/null --max-time 2 "$URI" 2>/dev/null; then
    exit 0
  fi
  sleep 1
done

exit 1
