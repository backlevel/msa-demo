# ============================================================
# MSA Demo - Load Test Runner (Windows PowerShell)
# K6 부하 테스트 실행 스크립트
# Run: .\load-test\run-load-test.ps1
# ============================================================

param(
    [string]$BaseUrl   = "http://localhost:8080/api",
    [string]$AiUrl     = "http://localhost:8090/ai",
    [string]$Scenario  = "all",   # all | ramp | spike | soak
    [switch]$Install               # K6 자동 설치
)

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Yellow }
function Write-Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Info($msg) { Write-Host "  [i]  $msg" -ForegroundColor Cyan }
function Write-Fail($msg) { Write-Host "  [X]  $msg" -ForegroundColor Red }

# K6 설치 확인
Write-Step "1. K6 Install Check"
$k6Exists = Get-Command k6 -ErrorAction SilentlyContinue
if (-not $k6Exists) {
    if ($Install) {
        Write-Info "K6 not found. Installing via winget..."
        winget install k6 --id k6.k6 --silent
        Write-Ok "K6 installed"
    } else {
        Write-Fail "K6 not found. Install first:"
        Write-Host "  winget install k6 --id k6.k6"
        Write-Host "  or visit: https://k6.io/docs/getting-started/installation/"
        exit 1
    }
}
Write-Ok "K6 found: $(k6 version)"

# 서비스 헬스 체크
Write-Step "2. Service Health Check"
try {
    $health = Invoke-RestMethod "$BaseUrl/../actuator/health" -TimeoutSec 5
    Write-Ok "Gateway: $($health.status)"
} catch {
    Write-Fail "Gateway not responding at $BaseUrl"
    Write-Info "Make sure docker compose is running: docker compose up"
    exit 1
}

# Synthetic Data 사전 생성
Write-Step "3. Pre-generate Synthetic Data"
Write-Info "Creating initial users and products for load test..."

$headers = @{ "Content-Type" = "application/json" }

# 유저 5명 생성
for ($i = 1; $i -le 5; $i++) {
    try {
        Invoke-RestMethod "$BaseUrl/users" -Method POST -Headers $headers `
          -Body "{`"name`":`"LoadUser$i`",`"email`":`"loaduser$i@test.com`"}" | Out-Null
    } catch { }
}
Write-Ok "5 test users created"

# 상품 5개 생성
$products = @(
    @{name="TestProduct1"; price=10000; stock=9999},
    @{name="TestProduct2"; price=20000; stock=9999},
    @{name="TestProduct3"; price=30000; stock=9999},
    @{name="TestProduct4"; price=50000; stock=9999},
    @{name="TestProduct5"; price=100000; stock=9999}
)
foreach ($p in $products) {
    try {
        Invoke-RestMethod "$BaseUrl/products" -Method POST -Headers $headers `
          -Body ($p | ConvertTo-Json) | Out-Null
    } catch { }
}
Write-Ok "5 test products created (stock=9999)"

# K6 부하 테스트 실행
Write-Step "4. Running K6 Load Test"
Write-Info "Scenario: $Scenario"
Write-Info "Target: $BaseUrl"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultFile = "load-test/result_$timestamp.json"

$env:BASE_URL = $BaseUrl
$env:AI_URL   = $AiUrl

Write-Info "Starting test... (this will take ~5 minutes)"
Write-Host ""

k6 run `
  --out "json=$resultFile" `
  load-test/k6-load-test.js

Write-Step "5. Test Results"
if (Test-Path $resultFile) {
    Write-Ok "Results saved: $resultFile"

    # 간단 요약 출력
    if (Test-Path "load-test/result-summary.json") {
        $summary = Get-Content "load-test/result-summary.json" | ConvertFrom-Json
        Write-Host ""
        Write-Host "  Total Requests    : $($summary.totalRequests)" -ForegroundColor White
        Write-Host "  Failed Requests   : $($summary.failedRequests)" -ForegroundColor White
        Write-Host "  P95 Response Time : $([math]::Round($summary.p95ResponseTime))ms" -ForegroundColor White
        Write-Host "  Order Success Rate: $([math]::Round($summary.orderSuccessRate * 100, 1))%" -ForegroundColor White
        Write-Host "  Saga Confirm Rate : $([math]::Round($summary.sagaConfirmedRate * 100, 1))%" -ForegroundColor White
        Write-Host "  Cache Hit Rate    : $([math]::Round($summary.cacheHitRate * 100, 1))%" -ForegroundColor White
    }
} else {
    Write-Fail "Result file not found"
}

Write-Host "`n=== Load Test Completed ===" -ForegroundColor Green
