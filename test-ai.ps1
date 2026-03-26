# ============================================================
# MSA Demo - AI Service Test (Windows PowerShell)
# Run: .\test-ai.ps1
# ============================================================

$BASE    = "http://localhost:8080/api"
$AI_BASE = "http://localhost:8090/ai"
$headers = @{ "Content-Type" = "application/json" }

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Yellow }
function Write-Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Info($msg) { Write-Host "  [i]  $msg" -ForegroundColor Cyan }
function Write-Fail($msg) { Write-Host "  [X]  $msg" -ForegroundColor Red }

# 1. AI Service Health Check
Write-Step "1. AI Service Health Check"
$health = Invoke-RestMethod -Uri "$AI_BASE/health"
$health | ConvertTo-Json
Write-Ok "AI Service: $($health.status)"

# 2. RAG Chat - 배송 문의
Write-Step "2. RAG Chat - Shipping Inquiry"
Write-Info "Question: When will my order arrive?"
$chat1 = Invoke-RestMethod -Uri "$AI_BASE/chat" -Method POST -Headers $headers -Body '{
  "userId": "user-001",
  "message": "주문한 상품이 언제 도착하나요?",
  "notifySlack": false
}'
$chat1 | ConvertTo-Json
Write-Ok "Session ID: $($chat1.sessionId)"
Write-Ok "Resolved: $($chat1.resolved)"

# 3. RAG Chat - 연속 대화 (컨텍스트 유지)
Write-Step "3. RAG Chat - Follow-up (Context Maintained)"
Write-Info "Using same sessionId for context"
$sessionId = $chat1.sessionId
$chat2 = Invoke-RestMethod -Uri "$AI_BASE/chat" -Method POST -Headers $headers -Body "{
  `"sessionId`": `"$sessionId`",
  `"userId`": `"user-001`",
  `"message`": `"반품은 어떻게 하나요?`",
  `"notifySlack`": false
}"
$chat2 | ConvertTo-Json

# 4. RAG Chat - 미해결 문의 (Slack 에스컬레이션)
Write-Step "4. RAG Chat - Unresolved -> Slack Escalation"
Write-Info "This should trigger Slack notification if configured"
$chat3 = Invoke-RestMethod -Uri "$AI_BASE/chat" -Method POST -Headers $headers -Body '{
  "userId": "user-002",
  "message": "결제가 됐는데 주문이 안보여요. 환불해주세요.",
  "notifySlack": true
}'
$chat3 | ConvertTo-Json
if ($chat3.slackNotified) {
    Write-Ok "Slack notification sent!"
} else {
    Write-Info "Slack not notified (check SLACK_BOT_TOKEN config)"
}

# 5. Direct Gemini Test
Write-Step "5. Direct Gemini API Test"
$gemini = Invoke-RestMethod -Uri "$AI_BASE/generate" -Method POST -Headers $headers -Body '{
  "prompt": "Spring Boot MSA 아키텍처의 장점을 3가지만 간단히 설명해주세요."
}'
$gemini | ConvertTo-Json

# 6. API Spec Generation - Single Endpoint
Write-Step "6. API Spec Auto-generation (Single Endpoint)"
Write-Info "Generating spec for POST /api/orders..."
$spec = Invoke-RestMethod -Uri "$AI_BASE/spec/generate" -Method POST -Headers $headers -Body '{
  "serviceName": "order-service",
  "method": "POST",
  "endpoint": "/api/orders",
  "requestBody": "{\"userId\": 1, \"productId\": 1, \"quantity\": 2}",
  "responseBody": "{\"id\": 1, \"userId\": 1, \"productId\": 1, \"quantity\": 2, \"status\": \"PENDING\"}",
  "description": "Kafka Saga 패턴으로 주문을 생성합니다."
}'
$spec | ConvertTo-Json -Depth 5
Write-Ok "Spec generated for: $($spec.method) $($spec.endpoint)"

# 7. Full API Spec Generation
Write-Step "7. Full API Spec Generation (All Endpoints)"
Write-Info "This may take 30-60 seconds (Gemini API calls per endpoint)..."
$allSpecs = Invoke-RestMethod -Uri "$AI_BASE/spec/generate-all" -Method POST -Headers $headers
Write-Ok "Generated specs for $($allSpecs.PSObject.Properties.Count) endpoints:"
foreach ($key in $allSpecs.PSObject.Properties.Name) {
    Write-Host "  - $key" -ForegroundColor Gray
}

Write-Host "`n=== AI Service Tests Completed ===" -ForegroundColor Green
Write-Host ""
Write-Host "Notes:" -ForegroundColor Cyan
Write-Host "  - Set GEMINI_API_KEY in .env for actual AI responses"
Write-Host "  - Set SLACK_BOT_TOKEN + SLACK_CHANNEL in .env for Slack alerts"
Write-Host "  - Load test: cd load-test && k6 run k6-load-test.js"
