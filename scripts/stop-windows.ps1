# Windows 停止服务
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RootDir

$ComposeFile = "docker-compose.deploy.yml"

if (docker compose version 2>$null) {
  docker compose -f $ComposeFile down
} else {
  docker-compose -f $ComposeFile down
}

Write-Host "服务已停止（数据库数据卷 mysql_data 已保留）"
