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

REM 升级安装后必须先停旧进程，否则 8080 仍由旧 JAR 提供服务
call "%~dp0stop.bat" >nul 2>&1

start "" "%JAVA_EXE%" -Djava.awt.headless=false -Dspring.profiles.active=standalone -jar "%JAR_FILE%"

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
set "APP_URL=http://127.0.0.1:8080"
echo 服务已就绪，正在打开浏览器...

REM 360 浏览器在 Win10 上 http 协议关联常异常，优先按安装路径直接启动
if exist "%ProgramFiles(x86)%\360\360se6\Application\360se.exe" (
    start "" "%ProgramFiles(x86)%\360\360se6\Application\360se.exe" "%APP_URL%"
    goto open_browser_done
)
if exist "%ProgramFiles%\360\360se6\Application\360se.exe" (
    start "" "%ProgramFiles%\360\360se6\Application\360se.exe" "%APP_URL%"
    goto open_browser_done
)
if exist "%ProgramFiles(x86)%\360\360se\Application\360se.exe" (
    start "" "%ProgramFiles(x86)%\360\360se\Application\360se.exe" "%APP_URL%"
    goto open_browser_done
)
if exist "%ProgramFiles(x86)%\360\360Chrome\Chrome\Application\360chrome.exe" (
    start "" "%ProgramFiles(x86)%\360\360Chrome\Chrome\Application\360chrome.exe" "%APP_URL%"
    goto open_browser_done
)
if exist "%LOCALAPPDATA%\360Chrome\Chrome\Application\360chrome.exe" (
    start "" "%LOCALAPPDATA%\360Chrome\Chrome\Application\360chrome.exe" "%APP_URL%"
    goto open_browser_done
)

rundll32 url.dll,FileProtocolHandler "%APP_URL%"
powershell -NoProfile -Command "Start-Process '%APP_URL%'" >nul 2>&1
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" "%APP_URL%"
    goto open_browser_done
)
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" "%APP_URL%"
    goto open_browser_done
)

echo [提示] 无法自动打开浏览器，请手动访问 %APP_URL%
pause

:open_browser_done
exit /b 0
