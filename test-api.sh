#!/bin/bash
# ============================================================
# MSA Demo - API 테스트 스크립트
# 실행: chmod +x test-api.sh && ./test-api.sh
# ============================================================

BASE_URL="http://localhost:8080/api"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo_step() { echo -e "\n${YELLOW}▶ $1${NC}"; }
echo_ok()   { echo -e "${GREEN}✔ $1${NC}"; }
echo_fail() { echo -e "${RED}✘ $1${NC}"; }

# ── 1. 유저 생성 ──────────────────────────────────────────────
echo_step "1. 유저 생성"
USER=$(curl -s -X POST "$BASE_URL/users" \
  -H "Content-Type: application/json" \
  -d '{"name":"홍길동","email":"hong@test.com"}')
echo "$USER" | python3 -m json.tool 2>/dev/null || echo "$USER"
USER_ID=$(echo "$USER" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo_ok "생성된 유저 ID: $USER_ID"

# ── 2. 전체 유저 조회 ─────────────────────────────────────────
echo_step "2. 전체 유저 조회"
curl -s "$BASE_URL/users" | python3 -m json.tool 2>/dev/null

# ── 3. 상품 생성 ──────────────────────────────────────────────
echo_step "3. 상품 생성"
PRODUCT=$(curl -s -X POST "$BASE_URL/products" \
  -H "Content-Type: application/json" \
  -d '{"name":"스프링 부트 교재","price":35000,"stock":100}')
echo "$PRODUCT" | python3 -m json.tool 2>/dev/null || echo "$PRODUCT"
PRODUCT_ID=$(echo "$PRODUCT" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo_ok "생성된 상품 ID: $PRODUCT_ID"

# ── 4. 전체 상품 조회 ─────────────────────────────────────────
echo_step "4. 전체 상품 조회"
curl -s "$BASE_URL/products" | python3 -m json.tool 2>/dev/null

# ── 5. 주문 생성 (User Service + Product Service FeignClient 호출) ─
echo_step "5. 주문 생성 [FeignClient 호출 테스트]"
ORDER=$(curl -s -X POST "$BASE_URL/orders" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":$USER_ID,\"productId\":$PRODUCT_ID,\"quantity\":2}")
echo "$ORDER" | python3 -m json.tool 2>/dev/null || echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo_ok "생성된 주문 ID: $ORDER_ID"

# ── 6. 주문 단건 조회 ─────────────────────────────────────────
echo_step "6. 주문 단건 조회"
curl -s "$BASE_URL/orders/$ORDER_ID" | python3 -m json.tool 2>/dev/null

# ── 7. 유저별 주문 목록 ───────────────────────────────────────
echo_step "7. 유저($USER_ID)의 주문 목록"
curl -s "$BASE_URL/orders/user/$USER_ID" | python3 -m json.tool 2>/dev/null

# ── 8. 주문 확정 ──────────────────────────────────────────────
echo_step "8. 주문 확정"
curl -s -X PATCH "$BASE_URL/orders/$ORDER_ID/confirm" | python3 -m json.tool 2>/dev/null
echo_ok "주문 상태: CONFIRMED"

# ── 9. 주문 취소 (다른 주문으로 테스트) ──────────────────────
echo_step "9. 새 주문 생성 후 취소"
ORDER2=$(curl -s -X POST "$BASE_URL/orders" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":$USER_ID,\"productId\":$PRODUCT_ID,\"quantity\":1}")
ORDER2_ID=$(echo "$ORDER2" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
curl -s -X PATCH "$BASE_URL/orders/$ORDER2_ID/cancel" | python3 -m json.tool 2>/dev/null
echo_ok "주문 상태: CANCELLED"

# ── 10. Eureka 대시보드 확인 안내 ────────────────────────────
echo_step "10. 대시보드 URL"
echo "  Eureka    : http://localhost:8761"
echo "  Zipkin    : http://localhost:9411"
echo "  Gateway   : http://localhost:8080/actuator/gateway/routes"

echo -e "\n${GREEN}✔ 모든 테스트 완료!${NC}\n"
