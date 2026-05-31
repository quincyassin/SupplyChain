# 电商分单合单系统

Spring Boot + MySQL + React 全栈项目，支持上传 Excel 订单文件，按**商家**字段进行**分单**或**合单**处理。

## 功能说明

| 功能 | 说明 |
|------|------|
| **预览** | 解析 Excel，按商家统计行数与金额合计 |
| **分单 (SPLIT)** | 按商家分组，每个商家生成独立 Excel，打包为 ZIP 下载，并分配新子订单号 |
| **合单 (MERGE)** | 按商家 + SKU 合并数量与金额，生成合并订单行 |
| **历史记录** | 处理任务写入 MySQL，前端可查看最近 20 条 |

## Excel 模板格式

首行为表头，**列顺序可任意**。上传后在前端「表头映射与排序」中：

- **挑选**：勾选需要的系统字段，并映射到 Excel 对应列
- **排序**：用上下箭头调整导出 Excel 的列顺序

系统支持字段：订单号、商家（必填）、商品名称、SKU、数量、单价、金额、收货人、收货地址、联系电话

示例数据：

| 订单号 | 商家 | 商品名称 | SKU | 数量 | 单价 | 金额 | 收货人 | 收货地址 | 联系电话 |
|--------|------|----------|-----|------|------|------|--------|----------|----------|
| O20250526001 | 旗舰店A | 蓝牙耳机 | SKU-A01 | 2 | 99.00 | 198.00 | 张三 | 北京市朝阳区 | 13800000001 |
| O20250526002 | 旗舰店B | 手机壳 | SKU-B01 | 1 | 29.00 | 29.00 | 李四 | 上海市浦东新区 | 13800000002 |
| O20250526003 | 旗舰店A | 充电线 | SKU-A02 | 3 | 19.00 | 57.00 | 王五 | 广州市天河区 | 13800000003 |

生成示例文件：

```bash
mvn -q test -Dtest=SampleExcelGeneratorTest
# 输出: docs/sample-orders.xlsx
```

## 技术栈

- **后端**: Java 17, Spring Boot 3.2, Spring Data JPA, Apache POI 5.2
- **数据库**: MySQL 8
- **前端**: React 18, TypeScript, Vite 5, Ant Design 5

## 一键部署（推荐，自带运行环境）

通过 **Docker** 打包 MySQL 8、Java 17 后端、Nginx 前端，无需在宿主机单独安装 JDK / Node / MySQL。

**前置条件**：已安装 [Docker](https://docs.docker.com/get-docker/)（Windows 用 [Docker Desktop](https://www.docker.com/products/docker-desktop/)）

### Linux / macOS

```bash
chmod +x scripts/deploy-linux.sh scripts/stop-linux.sh
bash scripts/deploy-linux.sh
```

### Windows

双击 `scripts/deploy-windows.bat`，或在 PowerShell 中：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\deploy-windows.ps1
```

部署成功后浏览器访问：**http://localhost**（默认 80 端口）

可选配置：复制 `.env.example` 为 `.env`，修改端口或数据库密码：

```bash
APP_PORT=8088
MYSQL_ROOT_PASSWORD=your_password
```

停止服务：

```bash
# Linux
bash scripts/stop-linux.sh

# Windows
powershell -ExecutionPolicy Bypass -File scripts\stop-windows.ps1
```

---

## 本地开发启动

### 1. 启动 MySQL 并建表

```bash
# 推荐：启动容器 + 执行全量建表
bash scripts/init-mysql.sh

# 或仅启动容器（首次会自动执行 docs/docker-init/01-schema.sql）
docker compose up -d
```

默认连接：`localhost:3306`，库名 `order_split_merge`，用户 `root`，密码 `root`（可在 `.env` 中改 `MYSQL_ROOT_PASSWORD`）。

本地后端密码与 MySQL 对齐：复制 `.env.example` 为 `.env`，设置 `SPRING_DATASOURCE_PASSWORD`（与 `MYSQL_ROOT_PASSWORD` 一致）。

**数据表**（`ddl-auto=update` 也会自动补表，建议用脚本显式初始化）：

| 表名 | 说明 |
|------|------|
| `platform_mapping_template` | 平台表头映射（系统配置 → 表头映射） |
| `merchant_config` | 商家名称与关键字（系统配置 → 商家配置） |
| `process_task` | 分单任务记录 |
| `import_order` | 当日导入订单明细 |

建表脚本：

- 全量：`docs/schema-all.sql`
- 分表：`docs/schema-platform_mapping_template.sql`、`docs/schema-merchant_config.sql`、`docs/schema-process_task.sql`、`docs/schema-import_order.sql`
- Docker 首次初始化：`docs/docker-init/01-schema.sql`

**已有库升级**（例如补 `import_order.platform` 列，可重复执行）：

```bash
# 本地开发（docker-compose.yml）
bash scripts/migrate-mysql.sh

# Linux 生产部署后单独升级
COMPOSE_FILE=docker-compose.deploy.yml bash scripts/migrate-mysql.sh
```

迁移脚本目录：`docs/migrations/`（按文件名顺序执行）。`init-mysql.sh` 与 `deploy-linux.sh` 会在建表/部署后自动跑迁移。

### 2. 启动后端

```bash
bash scripts/start-backend.sh
# 或一键：bash scripts/start-dev.sh（MySQL + 前端 + 后端）
```

API 地址：`http://localhost:8080`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/orders/read-headers` | POST | 读取表头并返回推荐映射（multipart: file） |
| `/api/orders/preview` | POST | 预览商家分布（multipart: file, mapping 可选 JSON） |
| `/api/orders/split` | POST | 分单并下载 ZIP（内含多个 Excel） |
| `/api/orders/merge` | POST | 合单并下载结果 Excel |
| `/api/orders/tasks` | GET | 任务历史列表 |

### 3. 启动前端

```bash
bash scripts/start-frontend.sh
# 首次需: cd frontend && npm install
```

访问：`http://localhost:5173`

## 项目结构

```
product/
├── pom.xml
├── docker-compose.yml
├── src/main/java/com/ecommerce/ordersplit/
│   ├── controller/     # REST API
│   ├── service/        # Excel 解析、分单合单逻辑
│   ├── entity/         # JPA 实体
│   └── ...
├── frontend/           # React 前端
└── docs/               # 示例 Excel（运行测试后生成）
```

## 业务规则

### 分单
- 以「商家」为维度分组
- 每个商家生成一个独立 `.xlsx` 文件（文件名即商家名）
- 全部 Excel 打包为 `.zip` 供下载
- 为每条明细生成新订单号：`SPLIT-{时间戳}-{序号}`

### 合单
- 先按「商家」分组
- 同商家内按「SKU」（为空则用商品名称）合并
- 数量、金额累加；单价按合并后金额/数量反算
- 新订单号：`MERGE-{时间戳}-{序号}`

## 测试

```bash
mvn test
```

## 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker（可选，用于 MySQL）
