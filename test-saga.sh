#!/bin/bash
# ============================================================
# Kafka Saga 패턴 테스트 스크립트
# 성공 / 재고부족(보상 트랜잭션) 두 가지 시나리오 시연
# ============================================================

BASE_URL="http://localhost:8080/api"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo_step() { echo -e "\n${YELLOW}━━━ $1 ━━━${NC}"; }
echo_ok()   { echo -e "${GREEN}  ✔ $1${NC}"; }
echo_info() { echo -e "${CYAN}  ℹ $1${NC}"; }
echo_warn() { echo -e "${RED}  ✘ $1${NC}"; }

sleep_and_check() {
  echo_info "Kafka 비동기 처리 대기 중 (3초)..."
  sleep 3
}

# ── 사전 준비 ────────────────────────────────────────────────
echo_step "0. 테스트 데이터 준비"

USER=$(curl -s -X POST "$BASE_URL/users" \
  -H "Content-Type: application/json" \
  -d '{"name":"사가테스터","email":"saga@test.com"}')
USER_ID=$(echo "$USER" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo_ok "유저 생성: id=$USER_ID"

# 재고 10개짜리 상품
PRODUCT=$(curl -s -X POST "$BASE_URL/products" \
  -H "Content-Type: application/json" \
  -d '{"name":"Saga 테스트 상품","price":5000,"stock":10}')
PRODUCT_ID=$(echo "$PRODUCT" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo_ok "상품 생성: id=$PRODUCT_ID (재고=10)"

# ── 시나리오 1: 정상 주문 (재고 충분) ───────────────────────
echo_step "시나리오 1 - 정상 Saga 흐름 (재고 충분)"
echo_info "흐름: createOrder → [Kafka: order.created] → 재고차감 → [Kafka: stock.decreased] → CONFIRMED"

ORDER1=$(curl -s -X POST "$BASE_URL/orders" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":$USER_ID,\"productId\":$PRODUCT_ID,\"quantity\":3}")
ORDER1_ID=$(echo "$ORDER1" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
INITIAL_STATUS=$(echo "$ORDER1" | grep -o '"status":"[A-Z]*"' | grep -o '[A-Z]*')
echo_ok "주문 생성 완료: id=$ORDER1_ID, 초기상태=$INITIAL_STATUS (PENDING 예상)"

sleep_and_check

RESULT1=$(curl -s "$BASE_URL/orders/$ORDER1_ID")
FINAL_STATUS=$(echo "$RESULT1" | grep -o '"status":"[A-Z]*"' | grep -o '[A-Z]*')
echo "$RESULT1" | python3 -m json.tool 2>/dev/null
if [ "$FINAL_STATUS" = "CONFIRMED" ]; then
  echo_ok "✔ Saga 성공: PENDING → CONFIRMED"
else
  echo_warn "상태: $FINAL_STATUS (Kafka 처리 시간 더 필요할 수 있음)"
fi

# ── 시나리오 2: 재고 부족 → 보상 트랜잭션 ───────────────────
echo_step "시나리오 2 - 재고 부족 → 보상 트랜잭션"
echo_info "흐름: createOrder(qty=999) → [Kafka: order.created] → 재고부족 → [Kafka: stock.failed] → CANCELLED"

ORDER2=$(curl -s -X POST "$BASE_URL/orders" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":$USER_ID,\"productId\":$PRODUCT_ID,\"quantity\":999}")
ORDER2_ID=$(echo "$ORDER2" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo_ok "주문 생성: id=$ORDER2_ID, 수량=999 (재고=10이므로 부족)"

sleep_and_check

RESULT2=$(curl -s "$BASE_URL/orders/$ORDER2_ID")
FINAL_STATUS2=$(echo "$RESULT2" | grep -o '"status":"[A-Z]*"' | grep -o '[A-Z]*')
echo "$RESULT2" | python3 -m json.tool 2>/dev/null
if [ "$FINAL_STATUS2" = "CANCELLED" ]; then
  echo_ok "✔ 보상 트랜잭션 성공: PENDING → CANCELLED"
else
  echo_warn "상태: $FINAL_STATUS2 (예상: CANCELLED)"
fi

# ── Kafka 토픽 메시지 확인 ───────────────────────────────────
echo_step "Kafka 토픽 확인"
KAFKA_CONTAINER=$(docker ps --format '{{.Names}}' | grep kafka | grep -v zookeeper | head -1)
if [ -n "$KAFKA_CONTAINER" ]; then
  echo_info "토픽 목록:"
  docker exec "$KAFKA_CONTAINER" kafka-topics \
    --bootstrap-server localhost:9092 --list 2>/dev/null
else
  echo_info "kafka 컨테이너명 확인 후 직접 실행:"
  echo "  docker exec <kafka-container> kafka-topics --bootstrap-server localhost:9092 --list"
fi

echo ""
echo_info "메시지 확인 명령어:"
echo "  docker exec \$KAFKA kafka-console-consumer \\"
echo "    --bootstrap-server localhost:9092 \\"
echo "    --topic order.created --from-beginning"

echo ""
echo_info "Zipkin에서 Saga 전체 흐름 추적:"
echo "  http://localhost:9411 → traceId로 order→product 호출 체인 확인"

echo -e "\n${GREEN}━━━ Saga 테스트 완료 ━━━${NC}"
