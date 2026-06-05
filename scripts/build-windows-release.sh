# 在 macOS / Linux 上预构建 standalone JAR（Inno Setup 需在 Windows 上执行）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "=========================================="
echo "  分单发单助手 - 预构建 standalone JAR"
echo "=========================================="

echo "==> 构建前端"
cd frontend
npm ci
npm run build

echo "==> 打包后端 (-Pstandalone)"
cd "$ROOT_DIR"
mvn -B -Pstandalone -DskipTests package

JAR="$(ls target/order-split-merge-*.jar 2>/dev/null | grep -v original | head -1)"
echo ""
echo "JAR 已生成: $JAR"
echo ""
echo "完整 Windows 安装包请在 Windows 上运行:"
echo "  powershell -ExecutionPolicy Bypass -File scripts\\build-windows-release.ps1"
echo ""
echo "或仅生成 staging / zip:"
echo "  powershell ... -SkipInstaller"
echo ""
echo "macOS 单机 zip 请运行:"
echo "  bash scripts/build-mac-release.sh"
