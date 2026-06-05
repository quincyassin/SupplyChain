#!/usr/bin/env bash
# macOS 单机版发布包：前端 + standalone JAR + 便携 JRE + 启动脚本
# 用法: bash scripts/build-mac-release.sh
# 可选: --skip-jre-download  --arch aarch64|x64  --skip-zip
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

ensure_build_java() {
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
      export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    elif /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
      export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    fi
  fi
  if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
    echo "[错误] 构建需要 JDK 17+，请安装后重试"
    exit 1
  fi
  export PATH="$JAVA_HOME/bin:$PATH"
  local java_major
  java_major="$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
  if [[ -z "$java_major" || "$java_major" -lt 17 ]]; then
    echo "[错误] 当前 JAVA_HOME 版本过低（需要 17+）：$JAVA_HOME"
    exit 1
  fi
  echo "使用 JDK: $JAVA_HOME"
}

ensure_build_java

APP_VERSION="1.0.0"
STAGING_DIR="$ROOT_DIR/release/staging"
DIST_DIR="$ROOT_DIR/release/dist"
JRE_CACHE_DIR="$ROOT_DIR/packaging/jre-cache"
PACKAGING_DIR="$ROOT_DIR/packaging/mac"

SKIP_JRE_DOWNLOAD=0
SKIP_ZIP=0
TARGET_ARCH=""

usage() {
  cat <<'EOF'
用法: bash scripts/build-mac-release.sh [选项]

选项:
  --skip-jre-download   使用 packaging/jre-cache 中已下载的 JRE
  --arch aarch64|x64    指定 JRE 架构（默认按本机自动检测）
  --skip-zip            仅生成 release/staging，不打 zip
  -h, --help            显示帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-jre-download)
      SKIP_JRE_DOWNLOAD=1
      shift
      ;;
    --arch)
      TARGET_ARCH="${2:-}"
      shift 2
      ;;
    --skip-zip)
      SKIP_ZIP=1
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "[错误] 未知参数: $1"
      usage
      exit 1
      ;;
  esac
done

detect_arch() {
  case "$(uname -m)" in
    arm64)
      echo "aarch64"
      ;;
    x86_64)
      echo "x64"
      ;;
    *)
      echo "[错误] 不支持的 Mac 架构: $(uname -m)"
      exit 1
      ;;
  esac
}

resolve_jre_url() {
  local arch="$1"
  case "$arch" in
    aarch64)
      echo "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jre/hotspot/normal/eclipse?project=jdk"
      ;;
    x64)
      echo "https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jre/hotspot/normal/eclipse?project=jdk"
      ;;
    *)
      echo "[错误] 不支持的 JRE 架构: $arch（仅 aarch64 / x64）"
      exit 1
      ;;
  esac
}

find_jre_home() {
  local extract_dir="$1"
  local java_bin
  java_bin="$(find "$extract_dir" -type f -path '*/Contents/Home/bin/java' 2>/dev/null | head -1)"
  if [[ -n "$java_bin" ]]; then
    dirname "$(dirname "$java_bin")"
    return 0
  fi
  java_bin="$(find "$extract_dir" -type f -path '*/bin/java' 2>/dev/null | head -1)"
  if [[ -n "$java_bin" ]]; then
    dirname "$java_bin"
    return 0
  fi
  return 1
}

write_step() {
  echo ""
  echo "==> $1"
}

if [[ -z "$TARGET_ARCH" ]]; then
  TARGET_ARCH="$(detect_arch)"
fi

echo "=========================================="
echo "  分单发单助手 - macOS 发布包构建"
echo "  版本: $APP_VERSION  架构: $TARGET_ARCH"
echo "=========================================="

write_step "1/5 构建前端"
cd "$ROOT_DIR/frontend"
if [[ ! -d node_modules ]]; then
  npm ci
else
  echo "node_modules 已存在，跳过 npm ci"
fi
npm run build

write_step "2/5 打包后端（standalone profile，含前端 static）"
cd "$ROOT_DIR"
mvn -B -Pstandalone -DskipTests package

JAR_FILE="$(find "$ROOT_DIR/target" -maxdepth 1 -name 'order-split-merge-*.jar' ! -name '*original*' | head -1)"
if [[ -z "$JAR_FILE" || ! -f "$JAR_FILE" ]]; then
  echo "[错误] 未找到 target/order-split-merge-*.jar"
  exit 1
fi

write_step "3/5 准备便携 JRE 17"
JRE_ARCH_DIR="$JRE_CACHE_DIR/mac-$TARGET_ARCH"
JRE_TAR="$JRE_ARCH_DIR/temurin-jre17-mac-$TARGET_ARCH.tar.gz"
JRE_EXTRACT_DIR="$JRE_ARCH_DIR/extracted"
JRE_READY_MARKER="$JRE_EXTRACT_DIR/.ready"

mkdir -p "$JRE_ARCH_DIR"

if [[ "$SKIP_JRE_DOWNLOAD" -eq 0 ]]; then
  if [[ ! -f "$JRE_TAR" ]]; then
    echo "下载 Eclipse Temurin JRE 17（${TARGET_ARCH}，约 40MB）..."
    JRE_URL="$(resolve_jre_url "$TARGET_ARCH")"
    curl -fL "$JRE_URL" -o "$JRE_TAR"
  fi
  if [[ ! -s "$JRE_TAR" ]] || [[ "$(wc -c <"$JRE_TAR" | tr -d ' ')" -lt 30000000 ]]; then
    rm -f "$JRE_TAR"
    echo "[错误] JRE 下载文件过小，可能下载失败"
    exit 1
  fi
  if [[ ! -f "$JRE_READY_MARKER" ]]; then
    rm -rf "$JRE_EXTRACT_DIR"
    mkdir -p "$JRE_EXTRACT_DIR"
    tar -xzf "$JRE_TAR" -C "$JRE_EXTRACT_DIR"
    touch "$JRE_READY_MARKER"
  fi
else
  if [[ ! -f "$JRE_READY_MARKER" ]]; then
    echo "[错误] 未找到 JRE 缓存，请去掉 --skip-jre-download 或手动放入 packaging/jre-cache"
    exit 1
  fi
fi

JRE_HOME="$(find_jre_home "$JRE_EXTRACT_DIR" || true)"
if [[ -z "$JRE_HOME" || ! -x "$JRE_HOME/bin/java" ]]; then
  echo "[错误] JRE 解压目录结构异常，未找到 bin/java"
  exit 1
fi

write_step "4/5 组装 staging 目录"
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/app" "$STAGING_DIR/data/db" "$STAGING_DIR/logs"

cp "$JAR_FILE" "$STAGING_DIR/app/order-split-merge.jar"
cp -R "$JRE_HOME" "$STAGING_DIR/jre"

MAC_SCRIPTS=(
  "start.command"
  "stop.command"
  "wait-ready.sh"
  "stop-service.sh"
)
for script_name in "${MAC_SCRIPTS[@]}"; do
  source_path="$PACKAGING_DIR/$script_name"
  if [[ ! -f "$source_path" ]]; then
    echo "[错误] 缺少脚本: packaging/mac/$script_name"
    exit 1
  fi
  cp "$source_path" "$STAGING_DIR/$script_name"
  chmod +x "$STAGING_DIR/$script_name"
done
cp "$PACKAGING_DIR/README.txt" "$STAGING_DIR/README.txt"

if [[ ! -f "$STAGING_DIR/app/order-split-merge.jar" ]]; then
  echo "[错误] staging 缺少 app/order-split-merge.jar"
  exit 1
fi
if [[ ! -x "$STAGING_DIR/jre/bin/java" ]]; then
  echo "[错误] staging 缺少 jre/bin/java"
  exit 1
fi

echo "staging 路径: $STAGING_DIR"

if [[ "$SKIP_ZIP" -eq 1 ]]; then
  echo ""
  echo "已跳过 zip（--skip-zip）"
  echo "可将 release/staging 目录直接分发，或手动打包。"
  exit 0
fi

write_step "5/5 打包 zip"
RELEASE_NAME="OrderSplitMerge-mac-${TARGET_ARCH}-${APP_VERSION}"
RELEASE_FOLDER="$DIST_DIR/$RELEASE_NAME"
ZIP_FILE="$DIST_DIR/${RELEASE_NAME}.zip"

rm -rf "$RELEASE_FOLDER" "$ZIP_FILE"
mkdir -p "$DIST_DIR"
cp -R "$STAGING_DIR" "$RELEASE_FOLDER"
chmod +x "$RELEASE_FOLDER"/*.command "$RELEASE_FOLDER"/*.sh 2>/dev/null || true

(
  cd "$DIST_DIR"
  zip -r "$ZIP_FILE" "$(basename "$RELEASE_FOLDER")" >/dev/null
)

echo ""
echo "=========================================="
echo "  构建完成"
echo "=========================================="
echo "  zip:     $ZIP_FILE"
echo "  文件夹:  $RELEASE_FOLDER"
echo "  staging: $STAGING_DIR"
echo ""
echo "  发给用户后：解压 → 双击 start.command"
echo ""
