# 离线激活码发码说明

## 流程

1. 客户在 **系统配置 → 软件授权** 复制机器码（`machineId`）发给你。
2. 你用本机私钥脚本生成激活码，离线发给客户。
3. 客户在软件授权页粘贴激活码完成激活。

## 发码工具位置

发码脚本与私钥放在 **与 product 同级** 的 `license/` 目录（独立仓库/文件夹，不进入 product Git）：

```text
Desktop/
├── product/     # 本仓库
└── license/     # 发码工具 + 私钥
    ├── generate-keypair.sh
    ├── generate-license.sh
    └── license-private.pem
```

## 一次性：生成密钥对

```bash
cd ../license
chmod +x generate-keypair.sh generate-license.sh
./generate-keypair.sh
```

- 私钥：`../license/license-private.pem`（**不要提交 Git**）
- 公钥：`src/main/resources/license/license-public.pem`（打进 JAR）

若仓库已包含公钥，新环境只需将私钥放到 `../license/` 即可发码。

## 发码

交互式（推荐）：

```bash
cd ../license
./generate-license.sh
```

按提示输入机器码和过期时间：

```text
机器码为: <粘贴客户机器码>
过期时间为: 2027-12-31
```

也支持命令行参数：

```bash
cd ../license
./generate-license.sh \
  --machine-id <客户机器码，64位hex，可带横线> \
  --expire 2027-12-31
```

若 product 不在默认路径 `../product`：

```bash
export LICENSE_PRODUCT_ROOT=/path/to/product
./generate-license.sh --machine-id <machineId> --expire 2027-12-31
```

永久授权示例：

```bash
./generate-license.sh --machine-id <machineId> --expire 2099-12-31
```

输出：

- `activationCode`：发给客户的原始激活码（**推荐**）
- `activationCodeDisplay`：带 `-` 分段显示，便于阅读

**注意**：通过微信等发送时，激活码里的 `+` 可能变成空格；软件已自动修复。仍建议用文件或邮件原文发送。

## 绑机规则

- 一码一机：激活码内 `machineId` 必须与客户本机一致。
- 到期日含当天（`Asia/Shanghai`）。
- 换机需重新发码。

## 平台

| 平台 | 指纹来源 |
|------|----------|
| Windows | MachineGuid + C: 卷序列号 |
| macOS | IOPlatformUUID + 硬件序列号 |

## 配置文件

| 环境 | `app.license.enforced` |
|------|------------------------|
| 开发（`application.yml`） | `false` |
| Windows 安装版（`application-standalone.yml`） | `true` |

授权文件路径：`./data/license/license.dat`
