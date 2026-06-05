分单发单助手 - macOS 使用说明
==============================

【启动】
  双击 start.command。
  首次若提示「无法打开」或「来自身份不明的开发者」：
    1. 在 Finder 中右键 start.command → 打开 → 仍要打开
    2. 或：系统设置 → 隐私与安全性 → 仍要打开
  浏览器会自动打开 http://localhost:8080

【停止】
  双击 stop.command。

【数据位置】
  程序数据（数据库）：安装目录/data/db/
  Excel 导出文件：桌面/testData/

【备份】
  复制整个 data 文件夹即可备份订单与配置。

【升级】
  停止服务后，用新版本覆盖 app/、jre/ 及脚本，保留 data/ 文件夹即可。

【日志】
  安装目录/logs/app.log

【端口占用】
  默认使用 8080 端口。若被占用，请联系技术支持。

【系统要求】
  macOS 12 及以上（Intel 或 Apple 芯片）
  无需安装 Java、MySQL、Docker 或 Node.js

【软件授权】
  系统配置 → 软件授权 → 复制机器码发给技术支持获取激活码。
