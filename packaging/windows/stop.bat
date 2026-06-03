@echo off
setlocal EnableExtensions

cd /d "%~dp0"

echo Stopping OrderSplitMerge...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-service.ps1" >nul 2>&1

for /f "tokens=2 delims==" %%p in ('wmic process where "CommandLine like '%%order-split-merge.jar%%'" get ProcessId /format:list 2^>nul ^| find "ProcessId"') do (
    taskkill /F /PID %%p >nul 2>&1
)

timeout /t 1 /nobreak >nul
echo Stopped.
