# Windows 一键部署（需已安装 Docker Desktop）
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RootDir

if (-not $env:APP_PORT) { $env:APP_PORT = "80" }
if (-not $env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD = "root" }

$ComposeFile = "docker-compose.deploy.yml"

Write-Host "=========================================="
Write-Host "  电商订单分单系统 - Windows 部署"
Write-Host "=========================================="

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[错误] 未检测到 Docker，请先安装 Docker Desktop："
    Write-Host "  https://www.docker.com/products/docker-desktop/"
    exit 1
}

docker compose version 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] 请使用已包含 Compose 的 Docker Desktop"
    exit 1
}

Write-Host "[1/4] 构建并启动容器（首次较慢，请耐心等待）..."
docker compose -f $ComposeFile up -d --build

Write-Host "[2/4] 等待 MySQL 就绪..."
Start-Sleep -Seconds 5

Write-Host "[3/4] 执行数据库增量迁移..."
$env:COMPOSE_FILE = $ComposeFile
powershell -ExecutionPolicy Bypass -File (Join-Path $RootDir "scripts\migrate-mysql.ps1")

Write-Host "[4/4] 等待应用就绪..."
Start-Sleep -Seconds 5

Write-Host "部署完成"
Write-Host ""
Write-Host "  访问地址: http://localhost:$($env:APP_PORT)"
Write-Host "  默认 MySQL 密码: $($env:MYSQL_ROOT_PASSWORD)"
Write-Host ""
Write-Host "常用命令:"
Write-Host "  查看日志: docker compose -f $ComposeFile logs -f"
Write-Host "  库表升级: `$env:COMPOSE_FILE='$ComposeFile'; powershell -File scripts\migrate-mysql.ps1"
Write-Host "  停止服务: powershell -ExecutionPolicy Bypass -File scripts\stop-windows.ps1"
Write-Host ""
