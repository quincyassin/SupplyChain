#!/usr/bin/env bash
# macOS 单机版发布包：前端 + standalone JAR + 便携 JRE + 启动脚本
# 用法: bash scripts/build-mac-release.sh
# 可选: --use-local-jre  --jre-archive <path>  --skip-jre-download  --arch aarch64|x64  --skip-zip
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
USE_LOCAL_JRE=0
TARGET_ARCH=""
JRE_ARCHIVE_PATH=""

usage() {
  cat <<'EOF'
用法: bash scripts/build-mac-release.sh [选项]

选项:
  --use-local-jre       使用本机 JDK 17 作为便携运行时（网络差时推荐）
  --jre-archive <path>  使用已下载的 Temurin JRE .tar.gz（可重复执行）
  --skip-jre-download   使用 packaging/jre-cache 中已下载的 JRE
  --arch aarch64|x64    指定 JRE 架构（默认按本机自动检测）
  --skip-zip            仅生成 release/staging，不打 zip
  -h, --help            显示帮助

网络无法下载 JRE 时，可任选其一：
  1) bash scripts/build-mac-release.sh --use-local-jre
  2) 浏览器下载 Temurin JRE 17 .tar.gz 后：
     bash scripts/build-mac-release.sh --jre-archive ~/Downloads/OpenJDK17U-jre_*.tar.gz
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --use-local-jre)
      USE_LOCAL_JRE=1
      shift
      ;;
    --jre-archive)
      JRE_ARCHIVE_PATH="${2:-}"
      if [[ -z "$JRE_ARCHIVE_PATH" ]]; then
        echo "[错误] --jre-archive 需要指定文件路径"
        exit 1
      fi
      shift 2
      ;;
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

# 备用固定版本（api.adoptium.net 不可达时尝试 GitHub Releases 直链）
JRE_FALLBACK_VERSION="17.0.15+6"
JRE_FALLBACK_TAG="jdk-17.0.15%2B6"

resolve_jre_urls() {
  local arch="$1"
  case "$arch" in
    aarch64)
      echo "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jre/hotspot/normal/eclipse?project=jdk"
      echo "https://github.com/adoptium/temurin17-binaries/releases/download/${JRE_FALLBACK_TAG}/OpenJDK17U-jre_aarch64_mac_hotspot_17.0.15_6.tar.gz"
      ;;
    x64)
      echo "https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jre/hotspot/normal/eclipse?project=jdk"
      echo "https://github.com/adoptium/temurin17-binaries/releases/download/${JRE_FALLBACK_TAG}/OpenJDK17U-jre_x64_mac_hotspot_17.0.15_6.tar.gz"
      ;;
    *)
      echo "[错误] 不支持的 JRE 架构: $arch（仅 aarch64 / x64）" >&2
      return 1
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

detect_java_machine() {
  local java_bin="$1"
  local arch
  arch="$(file "$java_bin" 2>/dev/null | awk -F': ' '{print $2}')"
  if [[ "$arch" == *arm64* ]]; then
    echo "aarch64"
  elif [[ "$arch" == *x86_64* ]]; then
    echo "x64"
  else
    echo "unknown"
  fi
}

validate_jre_archive() {
  local archive="$1"
  local size
  if [[ ! -f "$archive" ]]; then
    return 1
  fi
  size="$(wc -c <"$archive" | tr -d ' ')"
  if [[ "$size" -lt 30000000 ]]; then
    echo "[错误] JRE 文件过小（${size} 字节），可能下载不完整" >&2
    return 1
  fi
  if ! tar -tzf "$archive" >/dev/null 2>&1; then
    echo "[错误] JRE 文件不是有效的 .tar.gz：$archive" >&2
    return 1
  fi
}

can_reach_url() {
  local url="$1"
  curl -fsSIL --http1.1 --connect-timeout 8 --max-time 12 "$url" >/dev/null 2>&1
}

download_jre_archive() {
  local dest="$1"
  shift
  local url attempt
  rm -f "$dest"
  for url in "$@"; do
    echo "下载源: $url"
    if ! can_reach_url "$url"; then
      echo "[跳过] 无法连接该地址（终端访问外网受限时常见）"
      continue
    fi
    for attempt in 1 2; do
      echo "下载尝试 ${attempt}/2 ..."
      if curl -fL --http1.1 --retry 2 --retry-delay 3 --connect-timeout 30 --max-time 1800 \
        -C - --progress-bar "$url" -o "$dest"; then
        if validate_jre_archive "$dest"; then
          return 0
        fi
      fi
      echo "[警告] 下载未完成或文件损坏"
      rm -f "$dest"
      sleep 1
    done
  done
  return 1
}

print_jre_download_help() {
  local arch="$1"
  local host_arch
  host_arch="$(detect_arch)"

  echo ""
  echo "[提示] 终端无法下载 JRE（curl 访问 adoptium.net / github.com 超时或卡住）。"
  echo "       这通常是网络/防火墙限制，不是打包脚本本身的问题。"
  echo ""

  if [[ "$arch" == "aarch64" ]]; then
    cat <<EOF
请任选一种方式后继续：

  方式 A（推荐，M 芯片 + 本机已装 JDK 17）：
    bash scripts/build-mac-release.sh --use-local-jre

  方式 B（浏览器手动下载 .tar.gz 后）：
    bash scripts/build-mac-release.sh --jre-archive ~/Downloads/OpenJDK17U-jre_aarch64_mac_*.tar.gz

  方式 C（GitHub Actions 云端构建）：
    仓库 Actions → Build macOS Release → 架构选 aarch64
EOF
  else
    cat <<EOF
你在 ${host_arch} 电脑上打 Intel（x64）包，本机 JDK 是 arm64，不能用 --use-local-jre。

请任选一种方式：

  方式 A（推荐，浏览器下载 Intel JRE 后）：
    1. 用浏览器打开（比终端 curl 更容易走代理）：
       https://adoptium.net/zh-CN/temurin/releases/?version=17&os=mac&arch=x64&package=jre
    2. 下载 OpenJDK17U-jre_x64_mac_hotspot_*.tar.gz（约 36MB，不要 .pkg）
    3. 执行：
       bash scripts/build-mac-release.sh --arch x64 --jre-archive ~/Downloads/OpenJDK17U-jre_x64_mac_*.tar.gz

  方式 B（GitHub Actions 云端构建，无需本机下载 JRE）：
    仓库 Actions → Build macOS Release → 架构选 x64

  方式 C（请他人代下 JRE 压缩包，用 U 盘/网盘拷到本机后执行方式 A 第 3 步）
EOF
  fi
}

warn_intel_build_on_apple_silicon() {
  local host_arch="$1"
  local target_arch="$2"
  if [[ "$host_arch" == "aarch64" && "$target_arch" == "x64" \
    && "$USE_LOCAL_JRE" -eq 0 && -z "$JRE_ARCHIVE_PATH" ]]; then
    echo ""
    echo "[说明] 在 M 芯片 Mac 上打 Intel 包：需单独准备 x64 版 JRE（不能用 --use-local-jre）。"
    echo "       若终端下载一直超时，请用浏览器下载 .tar.gz 后加 --jre-archive，或用 GitHub Actions。"
    echo ""
  fi
}

write_step() {
  echo ""
  echo "==> $1"
}

if [[ -z "$TARGET_ARCH" ]]; then
  TARGET_ARCH="$(detect_arch)"
fi

HOST_ARCH="$(detect_arch)"
warn_intel_build_on_apple_silicon "$HOST_ARCH" "$TARGET_ARCH"

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
JRE_HOME=""

if [[ "$USE_LOCAL_JRE" -eq 1 ]]; then
  local_java_arch="$(detect_java_machine "$JAVA_HOME/bin/java")"
  if [[ "$local_java_arch" != "$TARGET_ARCH" && "$local_java_arch" != "unknown" ]]; then
    echo "[错误] 本机 JDK 架构为 ${local_java_arch}，与目标 ${TARGET_ARCH} 不一致"
    echo "       Intel 包请使用 --arch x64 的 JDK，或改用 --jre-archive 指定对应架构 JRE"
    exit 1
  fi
  JRE_HOME="$JAVA_HOME"
  echo "使用本机 JDK 作为便携运行时: $JRE_HOME"
elif [[ -n "$JRE_ARCHIVE_PATH" ]]; then
  if [[ ! -f "$JRE_ARCHIVE_PATH" ]]; then
    echo "[错误] 找不到 JRE 文件: $JRE_ARCHIVE_PATH"
    exit 1
  fi
  mkdir -p "$JRE_ARCH_DIR"
  echo "使用本地 JRE 压缩包: $JRE_ARCHIVE_PATH"
  cp "$JRE_ARCHIVE_PATH" "$JRE_TAR"
  if ! validate_jre_archive "$JRE_TAR"; then
    exit 1
  fi
  rm -rf "$JRE_EXTRACT_DIR"
  mkdir -p "$JRE_EXTRACT_DIR"
  tar -xzf "$JRE_TAR" -C "$JRE_EXTRACT_DIR"
  touch "$JRE_READY_MARKER"
  JRE_HOME="$(find_jre_home "$JRE_EXTRACT_DIR" || true)"
else
  mkdir -p "$JRE_ARCH_DIR"

  if [[ "$SKIP_JRE_DOWNLOAD" -eq 0 ]]; then
    if [[ ! -f "$JRE_TAR" ]] || ! validate_jre_archive "$JRE_TAR" 2>/dev/null; then
      echo "下载 Eclipse Temurin JRE 17（${TARGET_ARCH}，约 40MB）..."
      JRE_URLS=()
      while IFS= read -r jre_url; do
        [[ -n "$jre_url" ]] && JRE_URLS+=("$jre_url")
      done <<< "$(resolve_jre_urls "$TARGET_ARCH")"
      if ! download_jre_archive "$JRE_TAR" "${JRE_URLS[@]}"; then
        print_jre_download_help "$TARGET_ARCH"
        exit 1
      fi
    fi
    if [[ ! -f "$JRE_READY_MARKER" ]]; then
      rm -rf "$JRE_EXTRACT_DIR"
      mkdir -p "$JRE_EXTRACT_DIR"
      tar -xzf "$JRE_TAR" -C "$JRE_EXTRACT_DIR"
      touch "$JRE_READY_MARKER"
    fi
  else
    if [[ ! -f "$JRE_READY_MARKER" ]]; then
      echo "[错误] 未找到 JRE 缓存，请去掉 --skip-jre-download 或改用 --use-local-jre"
      exit 1
    fi
  fi

  JRE_HOME="$(find_jre_home "$JRE_EXTRACT_DIR" || true)"
fi

if [[ -z "$JRE_HOME" || ! -x "$JRE_HOME/bin/java" ]]; then
  echo "[错误] JRE 目录异常，未找到 bin/java"
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
