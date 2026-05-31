@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

echo 正在停止分单发单助手...

for /f "tokens=2 delims==" %%p in ('wmic process where "CommandLine like '%%order-split-merge.jar%%'" get ProcessId /format:list 2^>nul ^| find "ProcessId"') do (
    taskkill /F /PID %%p >nul 2>&1
)

timeout /t 1 /nobreak >nul
echo 已停止（若未运行则无需处理）。
