#!/bin/bash

# ============================================================
# MSA Demo - Load Test Runner (Linux/WSL2 Bash)
# K6 부하 테스트 실행 스크립트
# Run: chmod +x run-load-test.sh && ./run-load-test.sh
# ============================================================

# 기본 설정 (Argument 처리)
BASE_URL=${1:-"http://localhost:8080/api"}
AI_URL=${2:-"http://localhost:8090/ai"}
SCENARIO=${3:-"all"}
INSTALL_K6=false

# 함수 정의: 메시지 출력용
write_step() { echo -e "\n\033[1;33m=== $1 ===\033[0m"; }
write_ok()   { echo -e "  [OK] $1" | grep --color=always "OK"; }
write_info() { echo -e "  [i]  $1" | grep --color=always "i"; }
write_fail() { echo -e "  [X]  $1" | grep --color=always "X"; }

# 1. 도구 확인 (k6, jq, curl)
write_step "1. Tool Check (k6, jq, curl)"

# k6 체크
if ! command -v k6 &> /dev/null; then
    write_fail "k6 not found. Please install k6 first using the commands we discussed."
    exit 1
fi
write_ok "k6 found: $(k6 version | awk '{print $2}')"

# jq 체크 (JSON 파싱용)
if ! command -v jq &> /dev/null; then
    write_info "jq not found. Installing jq..."
    sudo apt-get update && sudo apt-get install -y jq
fi
write_ok "jq found"

# 2. 서비스 헬스 체크
write_step "2. Service Health Check"
HEALTH_URL="${BASE_URL%/*}/actuator/health" # gateway url에서 api 제거

response=$(curl -s -m 5 "$HEALTH_URL")
if [ $? -eq 0 ]; then
    status=$(echo $response | jq -r '.status' 2>/dev/null)
    write_ok "Gateway: $status"
else
    write_fail "Gateway not responding at $BASE_URL"
    write_info "Make sure docker compose is running: docker compose up"
    exit 1
fi

# 3. Synthetic Data 사전 생성
write_step "3. Pre-generate Synthetic Data"
write_info "Creating initial users and products for load test..."

# 유저 5명 생성
for i in {1..5}
do
    curl -s -X POST "$BASE_URL/users" \
         -H "Content-Type: application/json" \
         -d "{\"name\":\"LoadUser$i\", \"email\":\"loaduser$i@test.com\"}" > /dev/null
done
write_ok "5 test users created"

# 상품 5개 생성
products=(
    '{"name":"TestProduct1", "price":10000, "stock":9999}'
    '{"name":"TestProduct2", "price":20000, "stock":9999}'
    '{"name":"TestProduct3", "price":30000, "stock":9999}'
    '{"name":"TestProduct4", "price":50000, "stock":9999}'
    '{"name":"TestProduct5", "price":100000, "stock":9999}'
)

for p in "${products[@]}"
do
    curl -s -X POST "$BASE_URL/products" \
         -H "Content-Type: application/json" \
         -d "$p" > /dev/null
done
write_ok "5 test products created (stock=9999)"

# 4. K6 부하 테스트 실행
write_step "4. Running K6 Load Test"
write_info "Scenario: $SCENARIO"
write_info "Target: $BASE_URL"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULT_FILE="load-test/result_$TIMESTAMP.json"

# 환경 변수 설정
export BASE_URL=$BASE_URL
export AI_URL=$AI_URL

write_info "Starting test... (this will take ~5 minutes)"
echo ""

# k6 실행
k6 run \
  --out "json=$RESULT_FILE" \
  load-test/k6-load-test.js

# 5. 결과 확인
write_step "5. Test Results"
if [ -f "$RESULT_FILE" ]; then
    write_ok "Results saved: $RESULT_FILE"

    SUMMARY_FILE="load-test/result-summary.json"
    if [ -f "$SUMMARY_FILE" ]; then
        echo ""
        total=$(jq -r '.totalRequests' $SUMMARY_FILE)
        failed=$(jq -r '.failedRequests' $SUMMARY_FILE)
        p95=$(jq -r '.p95ResponseTime' $SUMMARY_FILE)
        success_rate=$(jq -r '.orderSuccessRate' $SUMMARY_FILE)

        echo -e "  Total Requests    : $total"
        echo -e "  Failed Requests   : $failed"
        echo -e "  P95 Response Time : ${p95%.*}ms"
        echo -e "  Order Success Rate: $(echo "$success_rate * 100" | bc 2>/dev/null || echo "0")%"
    fi
else
    write_fail "Result file not found"
fi

echo -e "\n\033[1;32m=== Load Test Completed ===\033[0m"