# Open the app URL: system default browser first, then 360 by path if that fails.
param(
    [string]$Url = 'http://127.0.0.1:8080'
)

function Test-StartViaCmd([string]$TargetUrl) {
    $process = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "start", '""', $TargetUrl `
        -Wait -PassThru -WindowStyle Hidden -ErrorAction SilentlyContinue
    return ($null -ne $process -and $process.ExitCode -eq 0)
}

function Test-StartViaRundll32([string]$TargetUrl) {
    try {
        Start-Process "rundll32.exe" -ArgumentList "url.dll,FileProtocolHandler $TargetUrl" -ErrorAction Stop
        return $true
    }
    catch {
        return $false
    }
}

function Test-StartBrowserExe([string]$BrowserPath, [string]$TargetUrl) {
    if (-not (Test-Path -LiteralPath $BrowserPath)) {
        return $false
    }
    Start-Process -FilePath $BrowserPath -ArgumentList $TargetUrl
    return $true
}

# 1. Prefer the system default browser (Edge / Chrome / registered default)
if (Test-StartViaCmd $Url) { exit 0 }
if (Test-StartViaRundll32 $Url) { exit 0 }

try {
    Start-Process $Url -ErrorAction Stop
    exit 0
}
catch {
    # Default handler failed, try explicit browser paths below
}

# 2. 360 Browser http handler is often broken on Win10; launch exe directly
$browser360Paths = @(
    "${env:ProgramFiles(x86)}\360\360se6\Application\360se.exe",
    "$env:ProgramFiles\360\360se6\Application\360se.exe",
    "${env:ProgramFiles(x86)}\360\360se\Application\360se.exe",
    "${env:ProgramFiles(x86)}\360\360Chrome\Chrome\Application\360chrome.exe",
    "$env:LOCALAPPDATA\360Chrome\Chrome\Application\360chrome.exe"
)

foreach ($browserPath in $browser360Paths) {
    if (Test-StartBrowserExe $browserPath $Url) { exit 0 }
}

# 3. Last resort: common browsers by install path
$fallbackBrowserPaths = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe"
)

foreach ($browserPath in $fallbackBrowserPaths) {
    if (Test-StartBrowserExe $browserPath $Url) { exit 0 }
}

Write-Host "[INFO] Cannot open browser automatically. Visit $Url"
exit 1
