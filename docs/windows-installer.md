# Windows 安装包

## 给最终用户（客户）

**只需一步**：双击 `OrderSplitMerge_Setup_1.0.0.exe` 安装，然后从桌面启动。

不需要安装 Java、MySQL、Node.js、Docker 或任何开发工具。

---

## 给你（发布安装包）

**你本地也不需要装 Node / JDK / Maven / Inno Setup。**

用 GitHub Actions 在云端自动构建：

### 方式 A：手动触发（推荐）

1. 把代码推到 GitHub
2. 打开仓库 → **Actions** → **Build Windows Installer** → **Run workflow**
3. 等约 5～10 分钟
4. 进入本次运行 → **Artifacts** → 下载 `OrderSplitMerge-Setup`
5. 把里面的 `.exe` 发给用户

### 方式 B：打 tag 自动发布

```bash
git tag v1.0.0
git push origin v1.0.0
```

Actions 会自动构建，并把安装包挂到 **GitHub Releases** 页面。

---

## 安装包内容

用户安装后目录结构：

```
OrderSplitMerge\
├── jre\              # 内置 Java（用户无感）
├── app\              # 程序 JAR（含前端页面）
├── data\db\          # 数据库（备份此目录）
├── logs\
├── 启动.bat
└── 停止.bat
```

Excel 导出仍在 **桌面 `testData\`**。

---

## 本地构建（可选，一般不需要）

仅在你坚持本机打包容时使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-windows-release.ps1
```

需要自行安装 Node.js、JDK 17、Maven、Inno Setup 6。

---

## 与 Docker 版对比

| | Inno Setup 单机版 | deploy-windows.bat |
|--|-------------------|---------------------|
| 用户操作 | 安装 exe 即用 | 需 Docker Desktop |
| 你打包容 | GitHub Actions 一键 | 需 Docker |
