@echo off
setlocal EnableExtensions

cd /d "%~dp0"

set "JAVA_EXE=%~dp0jre\bin\javaw.exe"
set "JAR_FILE=%~dp0app\order-split-merge.jar"
set "LOG_DIR=%~dp0logs"
set "DATA_DIR=%~dp0data\db"

if not exist "%JAVA_EXE%" goto missing_java
if not exist "%JAR_FILE%" goto missing_jar

if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

call "%~dp0stop.bat" >nul 2>&1

start "" "%JAVA_EXE%" -Djava.awt.headless=false -Dspring.profiles.active=standalone -jar "%JAR_FILE%"

echo Starting OrderSplitMerge, please wait...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0wait-ready.ps1" >nul 2>&1
if errorlevel 1 goto startup_timeout

echo Service is ready. Opening browser...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0open-browser.ps1" >nul 2>&1
exit /b 0

:missing_java
echo [ERROR] Java runtime not found: %JAVA_EXE%
echo Please reinstall OrderSplitMerge.
pause
exit /b 1

:missing_jar
echo [ERROR] Application not found: %JAR_FILE%
pause
exit /b 1

:startup_timeout
echo [WARN] Startup timeout. Check logs\app.log
pause
exit /b 1
