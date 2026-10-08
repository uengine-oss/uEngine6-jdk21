param([switch]$Running)
$ErrorActionPreference = 'Stop'
$expectedImage = 'sha256:a476861e6bfdd0530a450f84abfdffadee3696259a3dc093a2e36dfb85fd6fd0'
$expectedJar = '16de5b28a5408a87d4ff3a4ef1eea515076330a2de3e2104c7f68421f69a8250'
try {
    $actualImage = docker image inspect uengine-process-service:keycloak-postgres --format '{{.Id}}'
    if ($LASTEXITCODE -ne 0 -or $actualImage -ne $expectedImage) {
        throw "Unexpected process-service image: $actualImage. Reload the verified update archive."
    }
    if ($Running) {
        $runningImage = docker inspect bmt-plus200-process-service-1 --format '{{.Image}}'
        if ($LASTEXITCODE -ne 0 -or $runningImage -ne $expectedImage) {
            throw "Running container uses a different image: $runningImage"
        }
        $jarHash = docker exec bmt-plus200-process-service-1 sha256sum /process.jar
        if ($LASTEXITCODE -ne 0 -or ($jarHash -split '\s+')[0] -ne $expectedJar) {
            throw 'Running process.jar does not match the verified demo runtime.'
        }
        Write-Host "Verified process.jar SHA256: $expectedJar"
    }
    Write-Host "Verified demo runtime: receipt-recovery-20261008 / $expectedImage"
    exit 0
} catch {
    Write-Host "RUNTIME VERSION ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
