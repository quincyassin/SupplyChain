# Stop OrderSplitMerge javaw process by JAR name
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Name -eq 'javaw.exe' -and
        $_.CommandLine -like '*order-split-merge.jar*'
    } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

exit 0
