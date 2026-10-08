$ErrorActionPreference = 'Stop'
try {
    $launcher = Join-Path $PSScriptRoot 'open-demo-windows.mjs'
    $demoState = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'uEngineDemoWindows'
    $config = Get-Content (Join-Path $PSScriptRoot 'demo-windows.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $profiles = @(@($config.windows.id) + @('process') | Select-Object -Unique) | ForEach-Object { Join-Path $demoState $_ }
    $processes = Get-CimInstance Win32_Process
    $runners = @($processes | Where-Object { $_.Name -eq 'node.exe' -and $_.CommandLine -and $_.CommandLine.Contains($launcher) })
    $browsers = @($processes | Where-Object {
        if ($_.Name -ne 'msedge.exe' -or -not $_.CommandLine) { return $false }
        if ($_.CommandLine -match '--user-data-dir=(?:"([^"]+)"|(\S+))') {
            $profile = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
            return $profiles -contains $profile.TrimEnd('\')
        }
        return $false
    })
    foreach ($browser in $browsers) {
        $window = Get-Process -Id $browser.ProcessId -ErrorAction SilentlyContinue
        if ($window) {
            try {
                if ($window.MainWindowHandle -ne 0) { [void]$window.CloseMainWindow() }
            } catch {
                if (Get-Process -Id $browser.ProcessId -ErrorAction SilentlyContinue) { throw }
            }
        }
    }
    foreach ($browser in $browsers) {
        $remaining = Get-Process -Id $browser.ProcessId -ErrorAction SilentlyContinue
        if ($remaining) {
            if (-not $remaining.WaitForExit(3000)) {
                # Only the validated demo profile's browser tree is terminated.
                & taskkill.exe /PID $browser.ProcessId /T /F | Out-Null
            }
        }
    }
    foreach ($runner in $runners) { Stop-Process -Id $runner.ProcessId -Force -ErrorAction SilentlyContinue }
    Write-Host 'Demo windows and launcher closed. Services and saved profiles are unchanged.'
} catch {
    Write-Error $_
    exit 1
}
