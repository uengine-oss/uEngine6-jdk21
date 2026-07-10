#requires -Version 5.1
# HLI 모드 런처: 기본 8088/5173 환경을 안 끄고 병렬로 띄우기 위해
# gateway(8188) + keycloak 만 새 창에서 실행한다.
# process-service / definition-service 는 일부러 제외 — 별도 디버깅 가능하도록
# .vscode/launch.json 의 ProcessServiceApplication / DefinitionServiceApplication
# 항목을 F5 로 띄울 것.
#
# 게이트웨이의 frontend 라우트(routes[5])는 yml 을 건드리지 않고
# CLI 인자로 http://localhost:5273 로 오버라이드한다.

$ErrorActionPreference = "Stop"

$ROOT         = $PSScriptRoot
$KC_BAT       = Join-Path $ROOT "infra\keycloak\install\start-keycloak-installed.bat"
$GW_DIR       = Join-Path $ROOT "gateway"

$VITE_PORT    = 5273
$GATEWAY_PORT = 8188

# JDK21 폴백 (keycloak 스크립트와 동일 패턴). 이미 JAVA_HOME 이 잡혀있으면 그대로 사용.
if (-not $env:JAVA_HOME) {
    $jdk21 = "C:\Program Files\Java\jdk-21.0.10"
    if (Test-Path (Join-Path $jdk21 "bin\java.exe")) {
        $env:JAVA_HOME = $jdk21
        Write-Host "[info] JAVA_HOME -> $jdk21" -ForegroundColor DarkGray
    }
}
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin" } else { $null }

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " uEngine6  (HLI mode)"                              -ForegroundColor Cyan
Write-Host "   Gateway    : http://localhost:$GATEWAY_PORT"
Write-Host "   -> frontend: http://localhost:$VITE_PORT  (process-gpt-vue3-hli)"
Write-Host "   Keycloak   : http://localhost:8080"
Write-Host "   Excluded   : process-service, definition-service"
Write-Host "                (debug via .vscode/launch.json)"
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

# Keycloak 은 8080 공유 인스턴스. 이미 뜬 게 있으면 그걸 재사용 (H2 DB 락 회피).
$kc8080InUse = $null -ne (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
if ($kc8080InUse) {
    Write-Host "[1/2] Keycloak: 이미 8080 점유 중 -> 공유 인스턴스 재사용, 새로 띄우지 않음." -ForegroundColor DarkGray
} elseif (-not (Test-Path $KC_BAT)) {
    Write-Host "[warn] Keycloak start.bat not found: $KC_BAT" -ForegroundColor Yellow
} else {
    Write-Host "[1/2] Starting Keycloak in a new window..." -ForegroundColor Yellow
    $kcCmd = "title Keycloak (HLI) && `"$KC_BAT`""
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", $kcCmd | Out-Null
}

Write-Host "[2/2] Starting Gateway (port $GATEWAY_PORT, profile=hli) in a new window..." -ForegroundColor Yellow
# 포트/라우트 오버라이드는 application-hli.yml 에 정의돼 있다 (CLI 리스트 인덱스 바인딩 실패 회피).
$envSetup = if ($javaBin) {
    "set `"JAVA_HOME=$env:JAVA_HOME`" && set `"PATH=$javaBin;%PATH%`" && "
} else { "" }
$gwCmd = "title Gateway HLI ($GATEWAY_PORT) && cd /d `"$GW_DIR`" && ${envSetup}mvn spring-boot:run -Dspring-boot.run.profiles=hli"
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", $gwCmd | Out-Null

Write-Host ""
Write-Host "Launched. Open http://localhost:$GATEWAY_PORT once gateway is up." -ForegroundColor Green
Write-Host "Run 'start-hli.ps1' in process-gpt-vue3-hli to bring up the vite side."
