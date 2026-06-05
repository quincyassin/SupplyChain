#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

JAVA_EXE="$ROOT_DIR/jre/bin/java"
JAR_FILE="$ROOT_DIR/app/order-split-merge.jar"
LOG_DIR="$ROOT_DIR/logs"
DATA_DIR="$ROOT_DIR/data/db"
PID_FILE="$ROOT_DIR/.app.pid"
APP_URL="http://127.0.0.1:8080"

show_alert() {
  local message="$1"
  if command -v osascript >/dev/null 2>&1; then
    osascript -e "display alert \"分单发单助手\" message \"${message}\" as warning" >/dev/null 2>&1 || true
  fi
  echo "[错误] $message"
}

if [[ ! -x "$JAVA_EXE" ]]; then
  show_alert "未找到 Java 运行时：$JAVA_EXE\n请重新解压完整安装包。"
  exit 1
fi

if [[ ! -f "$JAR_FILE" ]]; then
  show_alert "未找到程序文件：$JAR_FILE"
  exit 1
fi

mkdir -p "$DATA_DIR" "$LOG_DIR"

bash "$ROOT_DIR/stop-service.sh" >/dev/null 2>&1 || true

echo "正在启动分单发单助手，请稍候..."
nohup "$JAVA_EXE" -Djava.awt.headless=false -Dspring.profiles.active=standalone -jar "$JAR_FILE" >>"$LOG_DIR/app.log" 2>&1 &
echo $! >"$PID_FILE"
disown

if ! bash "$ROOT_DIR/wait-ready.sh" "$APP_URL" 60; then
  show_alert "启动超时，请查看 logs/app.log"
  exit 1
fi

open "$APP_URL" || true

echo ""
echo "服务已启动：$APP_URL"
echo "Excel 导出目录：桌面 testData/"
echo "可关闭此窗口；停止服务请双击 stop.command"
echo ""
