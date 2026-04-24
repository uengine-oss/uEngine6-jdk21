# 로컬 Docker에 이미지 태그를 넣습니다 (Maven 빌드 + docker build, 선택적 frontend tar load)
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

Write-Host "==> [1/2] Maven JAR 빌드"
& mvn -pl process-service,definition-service,keycloak-gateway -am package -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> [2/2] docker build"
docker build -t process-service:v0.0.1 -f process-service/Dockerfile process-service
docker build -t definition-service:v0.0.1 -f definition-service/Dockerfile definition-service
docker build -t keycloak-gateway:0.0.5 -f keycloak-gateway/Dockerfile keycloak-gateway

$frontendTar = Join-Path $Root "infra\local-images\frontend-0.0.7.tar"
if (Test-Path $frontendTar) {
    Write-Host "==> optional: docker load $frontendTar"
    docker load -i $frontendTar
} else {
    Write-Host "==> optional: frontend:0.0.7 없음 — infra/local-images/frontend-0.0.7.tar 를 두거나 docker tag로 맞추세요."
}

Write-Host "==> 완료. 다음: cd infra; docker compose up -d"
