@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

set "JAVA_EXE=%~dp0jre\bin\javaw.exe"
set "JAR_FILE=%~dp0app\order-split-merge.jar"
set "LOG_DIR=%~dp0logs"
set "DATA_DIR=%~dp0data\db"

if not exist "%JAVA_EXE%" (
    echo [错误] 未找到内置 Java：%JAVA_EXE%
    echo 请重新安装「分单发单助手」。
    pause
    exit /b 1
)

if not exist "%JAR_FILE%" (
    echo [错误] 未找到程序文件：%JAR_FILE%
    pause
    exit /b 1
)

if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

rem 已在运行则直接打开浏览器
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if %ERRORLEVEL%==0 (
    start "" "http://localhost:8080"
    exit /b 0
)

start "" "%JAVA_EXE%" -Dspring.profiles.active=standalone -jar "%JAR_FILE%"

echo 正在启动，请稍候...
set /a WAIT=0
:wait_loop
timeout /t 1 /nobreak >nul
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if %ERRORLEVEL%==0 goto open_browser
set /a WAIT+=1
if %WAIT% LSS 60 goto wait_loop

echo [警告] 服务启动超时，请查看 logs\app.log
pause
exit /b 1

:open_browser
start "" "http://localhost:8080"
exit /b 0
