# MSA Demo - Spring Cloud 포트폴리오 프로젝트

Spring Boot 3 + Spring Cloud 기반의 마이크로서비스 아키텍처 데모입니다.

## 기술 스택

| 역할 | 기술 |
|------|------|
| 빌드 | Gradle 8.5 (멀티 프로젝트) |
| 서비스 디스커버리 | Spring Cloud Netflix Eureka |
| API 게이트웨이 | Spring Cloud Gateway |
| 서비스 간 통신 | OpenFeign + Resilience4j (CircuitBreaker + Retry) |
| 캐싱 | Redis (@Cacheable / @CacheEvict, TTL 전략) |
| 메시지 큐 | Apache Kafka + Saga 패턴 (분산 트랜잭션) |
| 중앙 설정 관리 | Spring Cloud Config Server |
| 데이터베이스 | H2 인메모리 (개발용) |
| CI/CD | GitLab CI + AWS ECS Fargate |

## 프로젝트 구조

```
msa-demo/
├── docker-compose.yml          # 전체 서비스 오케스트레이션
├── build.gradle                # 루트 Gradle (공통 설정)
├── settings.gradle             # 멀티 프로젝트 설정
│
├── config-server/              # 중앙 설정 서버 (8888) - 독립 실행
├── eureka-server/              # 서비스 레지스트리 (8761)
├── api-gateway/                # 단일 진입점 (8080)
├── order-service/              # 주문 서비스 (랜덤 포트)
│   └── Saga Orchestrator       # Kafka 기반 분산 트랜잭션 조율
├── user-service/               # 유저 서비스 (랜덤 포트)
└── product-service/            # 상품 서비스 (랜덤 포트)
    └── StockEventHandler       # Kafka 재고 차감 이벤트 처리
```

## 포트 정보

| 서비스 | 호스트 포트 | 비고 |
|--------|------------|------|
| API Gateway | 8080 | 모든 요청의 단일 진입점 |
| Eureka Server | 8761 | 서비스 레지스트리 대시보드 |
| Config Server | 8888 | 독립 실행 (의존 서비스 없음) |
| Redis | 6379 | 캐시 서버 |
| Kafka | 19092 | 외부 접속용 (내부: 29092) |

## 실행 방법 (Windows)

### 사전 조건
- Docker Desktop 설치 및 실행
- PowerShell 5.1 이상

### 실행

```powershell
# 프로젝트 폴더로 이동
cd C:\workspace\msa-demo-ver3

# 전체 서비스 빌드 & 실행 (첫 실행 시 10~15분 소요)
docker compose up --build

# 실행 상태 확인 (다른 터미널)
docker compose ps -a
```

### 정상 실행 확인

모든 서비스가 `Up` 상태가 되어야 합니다.
`eureka-server`가 `healthy` 상태가 된 후 나머지 서비스들이 순서대로 시작됩니다.

```powershell
# Eureka 대시보드 - 등록된 서비스 목록 확인
# 브라우저에서 http://localhost:8761 접속
```

---

## API 테스트 (Windows PowerShell)

모든 요청은 `http://localhost:8080/api` 를 통해 Gateway로 들어갑니다.

### User Service

```powershell
# 유저 생성
Invoke-RestMethod -Uri "http://localhost:8080/api/users" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"name":"홍길동","email":"hong@test.com"}' | ConvertTo-Json

# 전체 유저 조회
Invoke-RestMethod -Uri "http://localhost:8080/api/users" | ConvertTo-Json

# 유저 단건 조회
Invoke-RestMethod -Uri "http://localhost:8080/api/users/1" | ConvertTo-Json
```

### Product Service

```powershell
# 상품 생성
Invoke-RestMethod -Uri "http://localhost:8080/api/products" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"name":"노트북","price":1200000,"stock":50}' | ConvertTo-Json

# 전체 상품 조회
Invoke-RestMethod -Uri "http://localhost:8080/api/products" | ConvertTo-Json

# 재고 차감
Invoke-RestMethod -Uri "http://localhost:8080/api/products/1/stock?quantity=5" `
  -Method PATCH | ConvertTo-Json
```

### Order Service (Saga 패턴)

```powershell
# 주문 생성 (내부적으로 Kafka Saga 실행)
# 1. 주문 저장 (PENDING)
# 2. Kafka → order.created 발행
# 3. Product Service 재고 차감
# 4. stock.decreased 수신 → CONFIRMED
Invoke-RestMethod -Uri "http://localhost:8080/api/orders" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"userId":1,"productId":1,"quantity":2}' | ConvertTo-Json

# 잠시 후 상태 확인 (PENDING → CONFIRMED)
Invoke-RestMethod -Uri "http://localhost:8080/api/orders/1" | ConvertTo-Json

# 유저별 주문 목록
Invoke-RestMethod -Uri "http://localhost:8080/api/orders/user/1" | ConvertTo-Json

# 주문 확정
Invoke-RestMethod -Uri "http://localhost:8080/api/orders/1/confirm" `
  -Method PATCH | ConvertTo-Json

# 주문 취소
Invoke-RestMethod -Uri "http://localhost:8080/api/orders/1/cancel" `
  -Method PATCH | ConvertTo-Json
```

### Redis 캐시 확인

```powershell
# 캐시된 키 목록 조회
Invoke-RestMethod -Uri "http://localhost:8080/api/products/cache/keys" | ConvertTo-Json

# 캐시 통계
Invoke-RestMethod -Uri "http://localhost:8080/api/products/cache/stats" | ConvertTo-Json

# Redis CLI로 직접 확인
docker exec msa-demo-ver3-redis-1 redis-cli KEYS "*"
docker exec msa-demo-ver3-redis-1 redis-cli TTL "products::1"
```

### Kafka 토픽 확인

```powershell
# 생성된 토픽 목록
docker exec msa-demo-ver3-kafka-1 kafka-topics `
  --bootstrap-server localhost:19092 --list

# order.created 메시지 실시간 모니터링
docker exec msa-demo-ver3-kafka-1 kafka-console-consumer `
  --bootstrap-server localhost:19092 `
  --topic order.created --from-beginning
```

---

## Saga 패턴 흐름

```
주문 생성 요청 (POST /api/orders)
    │
    ▼
Order Service  ──[order.created]──▶  Product Service
(PENDING 저장)                        재고 차감 시도
    │                                     │
    │◀──[stock.decreased]─────────── 성공 (재고 충분)
    │◀──[stock.failed]────────────── 실패 (재고 부족)
    │
    ├── 성공 → CONFIRMED
    └── 실패 → CANCELLED (보상 트랜잭션)
```

**시나리오 테스트:**

```powershell
# 시나리오 1: 정상 주문 (재고 충분)
Invoke-RestMethod -Uri "http://localhost:8080/api/orders" `
  -Method POST -ContentType "application/json" `
  -Body '{"userId":1,"productId":1,"quantity":2}' | ConvertTo-Json

Start-Sleep -Seconds 3

Invoke-RestMethod -Uri "http://localhost:8080/api/orders/1" | ConvertTo-Json
# 결과: status = "CONFIRMED"

# 시나리오 2: 재고 부족 → 보상 트랜잭션
Invoke-RestMethod -Uri "http://localhost:8080/api/orders" `
  -Method POST -ContentType "application/json" `
  -Body '{"userId":1,"productId":1,"quantity":9999}' | ConvertTo-Json

Start-Sleep -Seconds 3

Invoke-RestMethod -Uri "http://localhost:8080/api/orders/2" | ConvertTo-Json
# 결과: status = "CANCELLED"
```

---

## Redis 캐싱 전략

| 서비스 | 캐시명 | TTL | 무효화 시점 |
|--------|--------|-----|-------------|
| product | `products::{id}` | 10분 | 재고 차감 |
| product | `products-all::all` | 5분 | 상품 생성·재고 차감 |
| user | `users::{id}` | 30분 | 유저 생성 |
| user | `users-all::all` | 5분 | 유저 생성 |
| order | `orders::{id}` | 5분 | 확정·취소 |
| order | `orders-by-user::{userId}` | 3분 | 주문 생성·확정·취소 |

---

## 트러블슈팅 (Windows 환경)

| 증상 | 원인 | 해결 |
|------|------|------|
| `ports are not available: 9411` | Windows가 해당 포트 예약 | Zipkin은 `profiles: tracing`으로 분리됨. 기본 실행 시 불필요 |
| `gradlew not found` | Gradle wrapper 바이너리 없음 | Dockerfile에서 `gradle:8.5-jdk17-alpine` 이미지 사용 (wrapper 불필요) |
| `duplicate key spring` | `application.yml`에 `spring:` 키 중복 | 단일 `spring:` 블록 안에 redis/kafka 설정 통합 |
| `Table not found` | `data.sql`이 JPA보다 먼저 실행 | `defer-datasource-initialization: true` 추가 |
| `config-server unhealthy` | alpine 이미지에 curl 없음 | healthcheck를 wget으로 교체, config-server 의존성 제거 |
| `eureka-server 대기` | config-server depends_on | config-server 의존성 제거 (독립 실행으로 변경) |

---

## Zipkin 분산 추적 사용 시

기본 실행에서는 Zipkin이 비활성화되어 있습니다.
필요할 경우 아래 명령으로 Zipkin 포함 실행:

```powershell
docker compose --profile tracing up --build
# 접속: http://localhost:19411
```

각 서비스의 `application.yml`에서 샘플링 활성화도 필요합니다:
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 0.0 → 1.0 으로 변경
```
