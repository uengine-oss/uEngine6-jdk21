param(
    [string]$Python = "py",
    [switch]$NoClean
)

$ErrorActionPreference = "Stop"
$AgentRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $AgentRoot

$VenvRoot = Join-Path $AgentRoot ".venv-build"
if (-not $NoClean -and (Test-Path $VenvRoot)) {
    Remove-Item -Recurse -Force $VenvRoot
}

if (-not (Test-Path (Join-Path $VenvRoot "Scripts\python.exe"))) {
    if ($Python -eq "py") {
        & py -3 -m venv $VenvRoot
    } else {
        & $Python -m venv $VenvRoot
    }
    if ($LASTEXITCODE -ne 0) { throw "Python virtual environment creation failed." }
}

$VenvPython = Join-Path $VenvRoot "Scripts\python.exe"
$PythonBits = & $VenvPython -c "import struct; print(struct.calcsize('P') * 8)"
if ($LASTEXITCODE -ne 0 -or $PythonBits.Trim() -ne "64") {
    throw "A 64-bit Python interpreter is required for the Windows x64 agent."
}
& $VenvPython -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed." }
& $VenvPython -m pip install -r requirements-tray.txt
if ($LASTEXITCODE -ne 0) { throw "RPA agent dependency installation failed." }

# Chromium itself is installed into the user's Playwright cache on first agent launch.
# Keeping it out of the one-file package makes the artifact smaller and upgradeable.
$BundledBrowsers = Join-Path $VenvRoot "Lib\site-packages\playwright\driver\package\.local-browsers"
if (Test-Path $BundledBrowsers) {
    Remove-Item -Recurse -Force $BundledBrowsers
}

& $VenvPython -m PyInstaller --clean --noconfirm uengine-rpa-agent.spec
if ($LASTEXITCODE -ne 0) { throw "PyInstaller build failed." }

$ExePath = Join-Path $AgentRoot "dist\uengine-rpa-agent.exe"
if (-not (Test-Path $ExePath)) { throw "Windows executable was not created: $ExePath" }

$SelfTest = Start-Process -FilePath $ExePath -ArgumentList "--uengine-self-test" -Wait -PassThru
if ($SelfTest.ExitCode -ne 0) { throw "Packaged agent self-test failed with exit code $($SelfTest.ExitCode)." }

$ZipPath = Join-Path $AgentRoot "dist\uengine-rpa-agent-windows-x64.zip"
Compress-Archive -Path $ExePath -DestinationPath $ZipPath -Force
$Hash = (Get-FileHash -Algorithm SHA256 $ExePath).Hash

Write-Host "Built: $ExePath"
Write-Host "Archive: $ZipPath"
Write-Host "SHA256: $Hash"
