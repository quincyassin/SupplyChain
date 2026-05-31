# Windows 安装包构建：前端 + standalone JAR + 便携 JRE + Inno Setup
# 用法: powershell -ExecutionPolicy Bypass -File scripts\build-windows-release.ps1
# 可选: -SkipJreDownload  -SkipInstaller

param(
    [switch]$SkipJreDownload,
    [switch]$SkipInstaller
)

$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$StagingDir = Join-Path $RootDir "release\staging"
$InstallerOutDir = Join-Path $RootDir "release\installer"
$JreCacheDir = Join-Path $RootDir "packaging\jre-cache"
$JreTargetDir = Join-Path $StagingDir "jre"
$AppVersion = "1.0.0"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Checked([string]$Label, [scriptblock]$Block) {
    Write-Host "--- $Label"
    & $Block
    if ($LASTEXITCODE -ne 0) {
        throw "$Label 失败 (exit $LASTEXITCODE)"
    }
}

Set-Location $RootDir

Write-Host "=========================================="
Write-Host "  分单发单助手 - Windows 安装包构建"
Write-Host "  版本: $AppVersion"
Write-Host "=========================================="

Write-Step "1/5 构建前端"
Set-Location (Join-Path $RootDir "frontend")
if (-not (Test-Path "node_modules")) {
    Invoke-Checked "npm ci" { npm ci }
} else {
    Write-Host "node_modules 已存在，跳过 npm ci"
}
Invoke-Checked "npm run build" { npm run build }

Write-Step "2/5 打包后端（standalone profile，含前端 static）"
Set-Location $RootDir
Invoke-Checked "mvn package" { mvn -B -Pstandalone -DskipTests package }

$JarFile = Get-ChildItem -Path (Join-Path $RootDir "target") -Filter "order-split-merge-*.jar" |
    Where-Object { $_.Name -notmatch "original" } |
    Select-Object -First 1
if (-not $JarFile) { throw "未找到 target\order-split-merge-*.jar" }

Write-Step "3/5 准备便携 JRE 17"
if (-not $SkipJreDownload) {
    New-Item -ItemType Directory -Force -Path $JreCacheDir | Out-Null
    $JreZip = Join-Path $JreCacheDir "temurin-jre17-windows-x64.zip"
    if (-not (Test-Path $JreZip)) {
        Write-Host "下载 Eclipse Temurin JRE 17（约 40MB）..."
        $JreUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"
        Invoke-WebRequest -Uri $JreUrl -OutFile $JreZip -UseBasicParsing
    }
    if ((Get-Item $JreZip).Length -lt 30MB) {
        Remove-Item $JreZip -Force
        throw "JRE 下载文件过小，可能下载失败"
    }
    $JreExtractDir = Join-Path $JreCacheDir "extracted"
    if (-not (Test-Path (Join-Path $JreExtractDir ".ready"))) {
        if (Test-Path $JreExtractDir) { Remove-Item -Recurse -Force $JreExtractDir }
        Expand-Archive -Path $JreZip -DestinationPath $JreExtractDir -Force
        New-Item -ItemType File -Path (Join-Path $JreExtractDir ".ready") -Force | Out-Null
    }
    $JreHome = Get-ChildItem -Path $JreExtractDir -Directory | Where-Object { $_.Name -match "^jdk-" } | Select-Object -First 1
    if (-not $JreHome) { throw "JRE 解压目录结构异常" }
    $script:CachedJreSource = $JreHome.FullName
} else {
    if (-not (Test-Path (Join-Path $JreCacheDir "extracted\.ready"))) {
        throw "未找到 JRE 缓存，请去掉 -SkipJreDownload 或手动放入 packaging\jre-cache"
    }
    $JreHome = Get-ChildItem -Path (Join-Path $JreCacheDir "extracted") -Directory | Select-Object -First 1
    $script:CachedJreSource = $JreHome.FullName
}

Write-Step "4/5 组装 staging 目录"
if (Test-Path $StagingDir) { Remove-Item -Recurse -Force $StagingDir }
New-Item -ItemType Directory -Force -Path (Join-Path $StagingDir "app") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $StagingDir "data\db") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $StagingDir "logs") | Out-Null

Copy-Item $JarFile.FullName (Join-Path $StagingDir "app\order-split-merge.jar") -Force
Copy-Item -Recurse $CachedJreSource (Join-Path $StagingDir "jre") -Force

$PackagingDir = Join-Path $RootDir "packaging\windows"
Copy-Item (Join-Path $PackagingDir "start.bat") $StagingDir -Force
Copy-Item (Join-Path $PackagingDir "stop.bat") $StagingDir -Force
Copy-Item (Join-Path $PackagingDir "README.txt") $StagingDir -Force

Write-Host "staging 内容:"
Get-ChildItem -Path $StagingDir -Recurse | Select-Object -First 20 | ForEach-Object { Write-Host "  $($_.FullName)" }
if (-not (Test-Path (Join-Path $StagingDir "app\order-split-merge.jar"))) {
    throw "staging 缺少 app\order-split-merge.jar"
}
if (-not (Test-Path (Join-Path $StagingDir "jre\bin\javaw.exe"))) {
    throw "staging 缺少 jre\bin\javaw.exe"
}
if (-not (Test-Path (Join-Path $StagingDir "start.bat"))) {
    throw "staging 缺少 start.bat"
}

if ($SkipInstaller) {
    Write-Host ""
    Write-Host "已跳过 Inno Setup（-SkipInstaller）"
    Write-Host "可将 release\staging 目录打成 zip 直接分发。"
    exit 0
}

Write-Step "5/5 编译 Inno Setup 安装包"
$LangFile = Join-Path $RootDir "installer\Languages\ChineseSimplified.isl"
if (-not (Test-Path $LangFile)) {
    Write-Host "下载 Inno Setup 简体中文语言包..."
    New-Item -ItemType Directory -Force -Path (Split-Path $LangFile) | Out-Null
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/jrsoftware/issrc/master/Files/Languages/Unofficial/ChineseSimplified.isl" `
        -OutFile $LangFile -UseBasicParsing
}
if (-not (Test-Path $LangFile)) {
    throw "Inno Setup 语言包不存在: $LangFile"
}
Write-Host "语言包: $LangFile ($((Get-Item $LangFile).Length) bytes)"

$InnoLangDir = Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\Languages"
if (-not (Test-Path $InnoLangDir)) {
    throw "Inno Setup Languages 目录不存在: $InnoLangDir"
}
Copy-Item $LangFile (Join-Path $InnoLangDir "ChineseSimplified.isl") -Force
Write-Host "已复制语言包到: $InnoLangDir\ChineseSimplified.isl"

$IsccCandidates = @(
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
)
$Iscc = $IsccCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $Iscc -and $env:GITHUB_ACTIONS -eq "true") {
    throw "CI 环境中未找到 ISCC.exe，请检查 Inno Setup 安装步骤"
}
if (-not $Iscc) {
    Write-Host "[提示] 未安装 Inno Setup 6，跳过 .exe 生成。" -ForegroundColor Yellow
    Write-Host "  推荐：推代码到 GitHub，在 Actions 里自动构建，无需本地安装任何工具。"
    Write-Host "  或下载 Inno Setup: https://jrsoftware.org/isdl.php"
    Write-Host ""
    Write-Host "staging 目录已就绪，可 zip 分发或手动编译 iss。"
    exit 0
}

New-Item -ItemType Directory -Force -Path $InstallerOutDir | Out-Null
$IssFile = Join-Path $RootDir "installer\order-split-setup.iss"
$IsccLog = Join-Path $InstallerOutDir "iscc.log"
Write-Host "ISCC: $Iscc"
Write-Host "ISS:  $IssFile"
Write-Host "LOG:  $IsccLog"
& $Iscc "/O$InstallerOutDir" "/Log=$IsccLog" $IssFile
$isccExit = $LASTEXITCODE
if (Test-Path $IsccLog) {
    Write-Host "----- ISCC 日志 -----"
    Get-Content $IsccLog | ForEach-Object { Write-Host $_ }
    Write-Host "---------------------"
}
if ($isccExit -ne 0) { throw "Inno Setup 编译失败 (exit $isccExit)" }

$SetupExe = Get-ChildItem -Path $InstallerOutDir -Filter "OrderSplitMerge_Setup_*.exe" | Select-Object -First 1
Write-Host ""
Write-Host "=========================================="
Write-Host "  构建完成"
Write-Host "=========================================="
Write-Host "  安装包: $($SetupExe.FullName)"
Write-Host "  staging: $StagingDir"
Write-Host ""
