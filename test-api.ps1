\xEF\xBB\xBF# ============================================================
# MSA Demo - API Test Script (Windows PowerShell)
# Run: .\test-api.ps1
# ============================================================

$BASE = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json; charset=utf-8" }

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Yellow }
function Write-Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Info($msg) { Write-Host "  [i]  $msg" -ForegroundColor Cyan }

# 1. Create User
Write-Step "1. Create User"
$user = Invoke-RestMethod -Uri "$BASE/users" -Method POST -Headers $headers `
  -Body '{"name":"TestUser","email":"test@test.com"}'
$user | ConvertTo-Json
$userId = $user.id
Write-Ok "Created User ID: $userId"

# 2. Get All Users
Write-Step "2. Get All Users"
Invoke-RestMethod -Uri "$BASE/users" | ConvertTo-Json

# 3. Create Product
Write-Step "3. Create Product"
$product = Invoke-RestMethod -Uri "$BASE/products" -Method POST -Headers $headers `
  -Body '{"name":"Test Product","price":35000,"stock":100}'
$product | ConvertTo-Json
$productId = $product.id
Write-Ok "Created Product ID: $productId"

# 4. Get All Products
Write-Step "4. Get All Products"
Invoke-RestMethod -Uri "$BASE/products" | ConvertTo-Json

# 5. Create Order (Saga starts: PENDING -> CONFIRMED)
Write-Step "5. Create Order [Saga Pattern - PENDING -> CONFIRMED]"
Write-Info "Kafka async processing: PENDING -> CONFIRMED"
$order = Invoke-RestMethod -Uri "$BASE/orders" -Method POST -Headers $headers `
  -Body "{`"userId`":$userId,`"productId`":$productId,`"quantity`":2}"
$order | ConvertTo-Json
$orderId = $order.id
Write-Ok "Created Order ID: $orderId, Status: $($order.status)"

# 6. Wait for Saga and check status
Write-Step "6. Waiting for Saga (3 sec)"
Start-Sleep -Seconds 3
$orderResult = Invoke-RestMethod -Uri "$BASE/orders/$orderId"
$orderResult | ConvertTo-Json
if ($orderResult.status -eq "CONFIRMED") {
    Write-Ok "Saga success: PENDING -> CONFIRMED"
} else {
    Write-Host "  Status: $($orderResult.status)" -ForegroundColor Red
}

# 7. Get Orders by User
Write-Step "7. Get Orders by User (userId=$userId)"
Invoke-RestMethod -Uri "$BASE/orders/user/$userId" | ConvertTo-Json

# 8. Redis Cache Test
Write-Step "8. Redis Cache Test"
Write-Info "1st call = DB (Cache MISS), 2nd call = Redis (Cache HIT)"
$t1 = Measure-Command { Invoke-RestMethod -Uri "$BASE/products/$productId" | Out-Null }
Write-Info "1st call done - Cache MISS (check logs for SELECT query)"
$t2 = Measure-Command { Invoke-RestMethod -Uri "$BASE/products/$productId" | Out-Null }
Write-Info "2nd call done - Cache HIT (no DB query)"
Write-Ok "Time: MISS=$([math]::Round($t1.TotalMilliseconds))ms vs HIT=$([math]::Round($t2.TotalMilliseconds))ms"

# 9. Saga Compensation Test (out of stock)
Write-Step "9. Saga Compensation Test - Out of Stock -> CANCELLED"
Write-Info "Order quantity=9999 -> stock(100) not enough -> CANCELLED"
$order2 = Invoke-RestMethod -Uri "$BASE/orders" -Method POST -Headers $headers `
  -Body "{`"userId`":$userId,`"productId`":$productId,`"quantity`":9999}"
$order2Id = $order2.id
Start-Sleep -Seconds 3
$order2Result = Invoke-RestMethod -Uri "$BASE/orders/$order2Id"
$order2Result | ConvertTo-Json
if ($order2Result.status -eq "CANCELLED") {
    Write-Ok "Compensation success: PENDING -> CANCELLED"
} else {
    Write-Host "  Status: $($order2Result.status)" -ForegroundColor Red
}

# 10. Confirm Order
Write-Step "10. Confirm Order"
Invoke-RestMethod -Uri "$BASE/orders/$orderId/confirm" -Method PATCH | ConvertTo-Json
Write-Ok "Order status: CONFIRMED"

# Done
Write-Host "`n=== All Tests Completed ===" -ForegroundColor Green
Write-Host ""
Write-Host "Dashboards:" -ForegroundColor Cyan
Write-Host "  Eureka  : http://localhost:8761"
Write-Host "  Gateway : http://localhost:8080/actuator/gateway/routes"
Write-Host ""
Write-Host "Check Redis keys:" -ForegroundColor Cyan
Write-Host "  docker exec msa-demo-ver3-redis-1 redis-cli KEYS `"*`""
Write-Host ""
Write-Host "Check Kafka topics:" -ForegroundColor Cyan
Write-Host "  docker exec msa-demo-ver3-kafka-1 kafka-topics --bootstrap-server localhost:19092 --list"
