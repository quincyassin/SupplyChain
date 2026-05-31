# Windows：在 Docker MySQL 上执行 docs/migrations/*.sql
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RootDir

if (-not $env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD = "root" }
if (-not $env:MYSQL_DATABASE) { $env:MYSQL_DATABASE = "order_split_merge" }
if (-not $env:COMPOSE_FILE) { $env:COMPOSE_FILE = "docker-compose.yml" }

$MigrationsDir = Join-Path $RootDir "docs\migrations"

Write-Host "=========================================="
Write-Host "  MySQL 增量迁移"
Write-Host "=========================================="

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[错误] 未检测到 Docker"
    exit 1
}

if (-not (Test-Path $MigrationsDir)) {
    Write-Host "[错误] 找不到迁移目录: $MigrationsDir"
    exit 1
}

$files = Get-ChildItem -Path $MigrationsDir -Filter "*.sql" | Sort-Object Name
if ($files.Count -eq 0) {
    Write-Host "[提示] 无迁移脚本，跳过"
    exit 0
}

$mysqlUp = docker compose -f $env:COMPOSE_FILE ps mysql 2>$null | Select-String "Up"
if (-not $mysqlUp) {
    Write-Host "[错误] MySQL 容器未运行，请先启动 mysql 服务"
    exit 1
}

Write-Host "Compose 文件: $($env:COMPOSE_FILE)"
Write-Host "目标数据库:   $($env:MYSQL_DATABASE)"
Write-Host ""

foreach ($file in $files) {
    Write-Host "[迁移] $($file.Name)"
    Get-Content -Raw -Path $file.FullName | docker compose -f $env:COMPOSE_FILE exec -T mysql `
        mysql -uroot "-p$($env:MYSQL_ROOT_PASSWORD)" $env:MYSQL_DATABASE
}

Write-Host ""
Write-Host "  迁移完成（共 $($files.Count) 个脚本）"
