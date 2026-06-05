# macOS 单机发布包

## 给最终用户（客户）

**只需两步**：

1. 解压 `OrderSplitMerge-mac-*.zip`
2. 双击 `start.command` 启动（浏览器自动打开）

不需要安装 Java、MySQL、Node.js、Docker 或任何开发工具。

首次若 macOS 拦截脚本运行：在 Finder 中 **右键 start.command → 打开 → 仍要打开**。

---

## 给你（发布安装包）

### 本机构建（推荐在 Mac 上执行）

```bash
chmod +x scripts/build-mac-release.sh
bash scripts/build-mac-release.sh
```

产物：

- `release/dist/OrderSplitMerge-mac-aarch64-1.0.0.zip`（Apple 芯片）
- 或 `OrderSplitMerge-mac-x64-1.0.0.zip`（Intel）

指定架构（例如在 Apple 芯片 Mac 上打 Intel 包）：

```bash
bash scripts/build-mac-release.sh --arch x64
```

仅生成目录、不打 zip：

```bash
bash scripts/build-mac-release.sh --skip-zip
```

### GitHub Actions 云端构建

1. 推送代码到 GitHub
2. 打开仓库 → **Actions** → **Build macOS Release** → **Run workflow**
3. 选择架构（`aarch64` / `x64` / `both`）
4. 下载 Artifacts 中的 zip 发给用户

打 tag 推送时也会自动构建并挂到 **GitHub Releases**。

---

## 安装包内容

用户解压后目录结构：

```
OrderSplitMerge-mac-aarch64-1.0.0/
├── jre/              # 内置 Java（用户无感）
├── app/              # 程序 JAR（含前端页面）
├── data/db/          # 数据库（备份此目录）
├── logs/
├── start.command     # 启动
├── stop.command      # 停止
└── README.txt
```

Excel 导出在 **桌面 `testData/`**。

---

## 与 Docker 版对比

| | macOS 单机 zip | Docker 部署 |
|--|----------------|-------------|
| 本机选文件夹 | ✅ | ❌ |
| 打开导出目录 | ✅ | ❌ |
| 用户依赖 | 无 | Docker Desktop |
| 适用场景 | Mac 终端用户 | Linux 服务器 / 演示 |

---

## 与 Windows 安装包对比

| | macOS zip | Windows Inno Setup |
|--|-----------|-------------------|
| 安装方式 | 解压即用 | 安装 exe |
| 内置 JRE | ✅ | ✅ |
| 数据库 | H2 文件库 | H2 文件库 |
| 软件授权 | ✅ | ✅ |
