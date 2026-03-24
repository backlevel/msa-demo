\xEF\xBB\xBF# ============================================================
# Kafka Saga Pattern Test (Windows PowerShell)
# Scenario 1: Success  /  Scenario 2: Compensation
# Run: .\test-saga.ps1
# ============================================================

$BASE = "http://localhost:8080/api"
$headers = @{ "Content-Type" = "application/json; charset=utf-8" }

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Yellow }
function Write-Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Info($msg) { Write-Host "  [i]  $msg" -ForegroundColor Cyan }
function Write-Fail($msg) { Write-Host "  [X]  $msg" -ForegroundColor Red }

# Prepare test data
Write-Step "0. Prepare Test Data"
$user = Invoke-RestMethod -Uri "$BASE/users" -Method POST -Headers $headers `
  -Body '{"name":"SagaTester","email":"saga@test.com"}'
$userId = $user.id
Write-Ok "User created: id=$userId"

$product = Invoke-RestMethod -Uri "$BASE/products" -Method POST -Headers $headers `
  -Body '{"name":"Saga Test Product","price":5000,"stock":10}'
$productId = $product.id
Write-Ok "Product created: id=$productId (stock=10)"

# Scenario 1: Normal order (enough stock)
Write-Step "Scenario 1 - Normal Saga Flow (stock sufficient)"
Write-Info "Flow: createOrder -> [Kafka: order.created] -> stock decrease -> [Kafka: stock.decreased] -> CONFIRMED"

$order1 = Invoke-RestMethod -Uri "$BASE/orders" -Method POST -Headers $headers `
  -Body "{`"userId`":$userId,`"productId`":$productId,`"quantity`":3}"
$order1Id = $order1.id
Write-Ok "Order created: id=$order1Id, status=$($order1.status)"

Write-Info "Waiting for Kafka async processing (3 sec)..."
Start-Sleep -Seconds 3

$result1 = Invoke-RestMethod -Uri "$BASE/orders/$order1Id"
$result1 | ConvertTo-Json
if ($result1.status -eq "CONFIRMED") {
    Write-Ok "Saga SUCCESS: PENDING -> CONFIRMED"
} else {
    Write-Fail "Status: $($result1.status) (may need more time)"
}

# Scenario 2: Out of stock -> Compensation transaction
Write-Step "Scenario 2 - Out of Stock -> Compensation Transaction (CANCELLED)"
Write-Info "Quantity=999 -> stock(10) not enough -> stock.failed -> CANCELLED"

$order2 = Invoke-RestMethod -Uri "$BASE/orders" -Method POST -Headers $headers `
  -Body "{`"userId`":$userId,`"productId`":$productId,`"quantity`":999}"
$order2Id = $order2.id
Write-Ok "Order created: id=$order2Id"

Write-Info "Waiting for Kafka async processing (3 sec)..."
Start-Sleep -Seconds 3

$result2 = Invoke-RestMethod -Uri "$BASE/orders/$order2Id"
$result2 | ConvertTo-Json
if ($result2.status -eq "CANCELLED") {
    Write-Ok "Compensation SUCCESS: PENDING -> CANCELLED"
} else {
    Write-Fail "Status: $($result2.status)"
}

# Check Kafka topics
Write-Step "Kafka Topics"
docker exec msa-demo-ver3-kafka-1 kafka-topics `
  --bootstrap-server localhost:19092 --list

Write-Host "`n=== Saga Test Completed ===" -ForegroundColor Green
Write-Host ""
Write-Host "Monitor Kafka messages in real-time:" -ForegroundColor Cyan
Write-Host "  docker exec msa-demo-ver3-kafka-1 kafka-console-consumer ``"
Write-Host "    --bootstrap-server localhost:19092 ``"
Write-Host "    --topic order.created --from-beginning"
