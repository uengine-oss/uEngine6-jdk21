param([switch]$Foreground, [string]$LoginFilePath)
$ErrorActionPreference = 'Stop'
$transcriptStarted = $false
try {
    $logDirectory = Join-Path $PSScriptRoot 'logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $runLog = Join-Path $logDirectory ('demo-launcher-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.log')
    Start-Transcript -LiteralPath $runLog -Force | Out-Null
    $transcriptStarted = $true
    Write-Host "Run log: $runLog"
    Write-Host 'Step 1/4: locating Node.js...'
    $nodeExe = (Get-Command node -ErrorAction Stop).Source
    Write-Host 'Checking uBank API connection (up to 60 seconds)...'
    $apiReady = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try {
            $response = Invoke-WebRequest 'http://localhost:5374/api/session' -SkipHttpErrorCheck -TimeoutSec 2
            if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 401) { $apiReady = $true; break }
            Write-Host "uBank API not ready: HTTP $($response.StatusCode)"
        } catch { Write-Host 'uBank API connection unavailable.' }
        Start-Sleep -Seconds 1
    }
    if (-not $apiReady) { throw 'uBank API is not ready. Run restart-plus200 from the Suhyup BMT folder, wait for completion, then open the demo again. See corebank-web/corebank-api logs.' }
    Write-Host 'Step 2/4: checking the previous launcher...'
    $launcher = Join-Path $PSScriptRoot 'open-demo-windows.mjs'
    $existing = Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($launcher) }
    if ($existing) {
        & (Join-Path $PSScriptRoot 'close-demo-windows.ps1')
        if (-not $?) { throw 'Could not close the previous demo launcher.' }
    }
    $demoState = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'uEngineDemoWindows'
    New-Item -ItemType Directory -Path $demoState -Force | Out-Null
    $loginFile = Join-Path $demoState 'login-password.xml'
    if ($env:UBANK_DEMO_LOGIN_FILE) {
        $loginFile = $env:UBANK_DEMO_LOGIN_FILE
    }
    if ($LoginFilePath) { $loginFile = $LoginFilePath }
    Write-Host "Login file: $loginFile"
    if (-not (Test-Path -LiteralPath $loginFile)) {
        throw "Saved login file not found: $loginFile (Windows user: $([Environment]::UserName))."
    }
    Write-Host 'Step 3/4: reading the saved login...'
    $loginSecret = Import-Clixml -LiteralPath $loginFile
    $loginCredential = New-Object System.Management.Automation.PSCredential('demo', $loginSecret)
    $env:UBANK_DEMO_PASSWORD = $loginCredential.GetNetworkCredential().Password
    Write-Host 'Step 4/4: starting the browser windows...'
    try {
        if ($Foreground) {
            Write-Host 'Opening demo windows. Progress and errors will appear below.'
            & $nodeExe $launcher 2>&1 | ForEach-Object { Write-Host "$_" }
            if ($LASTEXITCODE -ne 0) { throw "Demo launcher failed (exit $LASTEXITCODE)." }
            return
        }
        Start-Process -FilePath $nodeExe -ArgumentList ('"' + $launcher + '"') -WindowStyle Hidden `
        -WorkingDirectory $PSScriptRoot -RedirectStandardOutput (Join-Path $demoState 'launcher.log') `
        -RedirectStandardError (Join-Path $demoState 'launcher-error.log')
    } finally {
        Remove-Item Env:UBANK_DEMO_PASSWORD -ErrorAction SilentlyContinue
    }
} catch {
    if ($Foreground) {
        Write-Host $_.Exception.Message -ForegroundColor Red
        exit 1
    }
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'uBank demo launcher') | Out-Null
    exit 1
} finally {
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
}
