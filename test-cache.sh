#!/bin/bash
# ============================================================
# Redis 캐시 동작 테스트 스크립트
# Cache HIT / MISS / EVICT 를 직접 확인
# 실행: chmod +x test-cache.sh && ./test-cache.sh
# ============================================================

BASE_URL="http://localhost:8080/api"
PRODUCT_URL="http://localhost:8080/api/products"   # Gateway 경유
CACHE_URL=""   # product-service 직접 포트는 동적이므로 docker exec 방식 사용

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo_step()  { echo -e "\n${YELLOW}━━━ $1 ━━━${NC}"; }
echo_ok()    { echo -e "${GREEN}  ✔ $1${NC}"; }
echo_info()  { echo -e "${CYAN}  ℹ $1${NC}"; }
echo_cache() { echo -e "${CYAN}  [REDIS]${NC} $1"; }

# ── 준비: 상품 생성 ─────────────────────────────────────────
echo_step "0. 테스트 데이터 준비"
P1=$(curl -s -X POST "$PRODUCT_URL" \
  -H "Content-Type: application/json" \
  -d '{"name":"캐시 테스트 상품 A","price":10000,"stock":50}')
P1_ID=$(echo "$P1" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo_ok "상품 생성: id=$P1_ID"

# ── CACHE MISS: 첫 번째 조회 (DB 조회 후 Redis 저장) ────────
echo_step "1. CACHE MISS - 첫 번째 조회"
echo_info "캐시에 없으므로 DB 조회 후 Redis에 저장됩니다."
time curl -s "$PRODUCT_URL/$P1_ID" | python3 -m json.tool 2>/dev/null
echo_ok "→ 로그에서 '[Cache MISS] products::$P1_ID' 확인 가능"

# ── CACHE HIT: 두 번째 조회 (Redis에서 즉시 반환) ───────────
echo_step "2. CACHE HIT - 두 번째 조회"
echo_info "Redis에서 바로 반환 (DB 쿼리 없음, 더 빠름)"
time curl -s "$PRODUCT_URL/$P1_ID" | python3 -m json.tool 2>/dev/null
echo_ok "→ 로그에 DB 쿼리(Hibernate SQL)가 출력되지 않음"

# ── Redis에서 실제 키 확인 ───────────────────────────────────
echo_step "3. Redis 저장 키 확인"
echo_info "docker exec으로 redis-cli 직접 조회"
docker exec msa-demo-redis-1 redis-cli KEYS "*products*" 2>/dev/null \
  || docker exec $(docker ps -qf "name=redis") redis-cli KEYS "*products*" 2>/dev/null \
  || echo_info "docker exec 접근 불가 - /api/products/cache/keys 엔드포인트로 확인하세요"

# ── CACHE EVICT: 재고 차감 후 캐시 무효화 확인 ──────────────
echo_step "4. CACHE EVICT - 재고 차감으로 캐시 무효화"
echo_info "재고 차감 → products::$P1_ID + products-all 키 삭제"
curl -s -X PATCH "$PRODUCT_URL/$P1_ID/stock?quantity=5" | python3 -m json.tool 2>/dev/null
echo_ok "→ 로그에서 '[Cache] products::$P1_ID ... 캐시 삭제' 확인"

# ── 무효화 후 재조회: 다시 CACHE MISS ───────────────────────
echo_step "5. 무효화 후 재조회 - 다시 CACHE MISS"
echo_info "캐시가 evict 되었으므로 다시 DB 조회"
time curl -s "$PRODUCT_URL/$P1_ID" | python3 -m json.tool 2>/dev/null
echo_ok "→ 로그에 Hibernate SQL + '[Cache MISS]' 다시 출력"

# ── products-all 캐시 테스트 ────────────────────────────────
echo_step "6. products-all 캐시 테스트"
echo_info "전체 목록 첫 번째 조회 (MISS)"
time curl -s "$PRODUCT_URL" > /dev/null
echo_ok "첫 번째 조회 완료"

echo_info "전체 목록 두 번째 조회 (HIT - 더 빠름)"
time curl -s "$PRODUCT_URL" > /dev/null
echo_ok "두 번째 조회 완료"

# ── TTL 확인 ─────────────────────────────────────────────────
echo_step "7. TTL 확인 (redis-cli)"
echo_info "products 키 TTL = 10분(600초), products-all = 5분(300초)"
docker exec $(docker ps -qf "name=redis") redis-cli TTL "products::$P1_ID" 2>/dev/null \
  && echo_ok "위 숫자가 TTL(초)" \
  || echo_info "redis-cli: docker exec \$(docker ps -qf 'name=redis') redis-cli TTL 'products::$P1_ID'"

echo -e "\n${GREEN}━━━ 테스트 완료 ━━━${NC}"
echo ""
echo "캐시 동작 흐름 요약:"
echo "  MISS  → DB 조회 + Redis 저장 (로그에 SELECT 쿼리 출력)"
echo "  HIT   → Redis 반환 (DB 쿼리 없음, 더 빠름)"
echo "  EVICT → 데이터 변경 시 캐시 삭제 → 다음 조회는 MISS"
echo ""
echo "Zipkin에서 응답 시간 차이 확인: http://localhost:9411"
