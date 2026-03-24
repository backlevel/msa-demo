\xEF\xBB\xBF# ============================================================
# Redis Cache Test Script (Windows PowerShell)
# Run: .\test-cache.ps1
# ============================================================

$BASE = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json; charset=utf-8" }

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Yellow }
function Write-Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Info($msg) { Write-Host "  [i]  $msg" -ForegroundColor Cyan }

# Prepare test product
Write-Step "0. Prepare Test Product"
$p = Invoke-RestMethod -Uri "$BASE/products" -Method POST -Headers $headers `
  -Body '{"name":"Cache Test Item","price":10000,"stock":50}'
$pid = $p.id
Write-Ok "Product created: id=$pid"

# CACHE MISS: first call
Write-Step "1. CACHE MISS - First call (DB query + save to Redis)"
$t1 = Measure-Command { Invoke-RestMethod -Uri "$BASE/products/$pid" | Out-Null }
Write-Ok "Response: $([math]::Round($t1.TotalMilliseconds))ms"
Write-Info "Check Docker logs for: [Cache MISS] products::$pid"

# CACHE HIT: second call
Write-Step "2. CACHE HIT - Second call (returned from Redis, no DB query)"
$t2 = Measure-Command { Invoke-RestMethod -Uri "$BASE/products/$pid" | Out-Null }
Write-Ok "Response: $([math]::Round($t2.TotalMilliseconds))ms"
Write-Info "No SELECT query in Docker logs"

Write-Host ""
Write-Ok "MISS=$([math]::Round($t1.TotalMilliseconds))ms vs HIT=$([math]::Round($t2.TotalMilliseconds))ms"

# Check Redis keys
Write-Step "3. Check Redis Keys"
docker exec msa-demo-ver3-redis-1 redis-cli KEYS "*products*"

# CACHE EVICT: decrease stock
Write-Step "4. CACHE EVICT - Decrease stock invalidates cache"
Invoke-RestMethod -Uri "$BASE/products/$pid/stock?quantity=5" -Method PATCH | Out-Null
Write-Ok "Stock decreased -> products::$pid cache deleted"
Write-Info "Check logs for: [Cache] products::$pid deleted"

# After evict: MISS again
Write-Step "5. After evict - CACHE MISS again"
$t3 = Measure-Command { Invoke-RestMethod -Uri "$BASE/products/$pid" | Out-Null }
Write-Ok "Response: $([math]::Round($t3.TotalMilliseconds))ms"
Write-Info "Check logs for: [Cache MISS] again"

# TTL check
Write-Step "6. TTL Check (products TTL = 10 min = 600 sec)"
docker exec msa-demo-ver3-redis-1 redis-cli TTL "products::$pid"
Write-Info "Number above = remaining TTL in seconds (close to 600 = OK)"

Write-Host "`n=== Cache Test Completed ===" -ForegroundColor Green
