/**
 * MSA Demo - K6 Load Test Script
 * Synthetic Data 자동 생성 + 시나리오별 부하 테스트
 *
 * 실행: k6 run load-test/k6-load-test.js
 * 리포트: k6 run --out json=result.json load-test/k6-load-test.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomIntBetween, randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// ── 커스텀 메트릭 ─────────────────────────────────────────
const orderSuccessRate   = new Rate('order_success_rate');
const sagaConfirmedRate  = new Rate('saga_confirmed_rate');
const cacheHitRate       = new Rate('cache_hit_rate');
const apiErrorCount      = new Counter('api_error_count');
const orderCreateTrend   = new Trend('order_create_duration');
const productQueryTrend  = new Trend('product_query_duration');

// ── 설정 ──────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const AI_URL   = __ENV.AI_URL   || 'http://localhost:8090/ai';

// ── 부하 시나리오 정의 ────────────────────────────────────
export const options = {
  scenarios: {
    // 시나리오 1: 점진적 부하 증가 (Ramp Up)
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },  // 0 → 10 VU
        { duration: '1m',  target: 30 },  // 10 → 30 VU
        { duration: '30s', target: 0  },  // 30 → 0 VU
      ],
      gracefulRampDown: '10s',
    },

    // 시나리오 2: 스파이크 테스트 (갑작스러운 트래픽 급증)
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 },  // 순간 50 VU
        { duration: '30s', target: 50 },  // 유지
        { duration: '10s', target: 0  },  // 복구
      ],
      startTime: '2m30s',  // ramp_up 완료 후 시작
    },

    // 시나리오 3: 일정 부하 유지 (Soak Test - 짧은 버전)
    soak_test: {
      executor: 'constant-vus',
      vus: 10,
      duration: '1m',
      startTime: '3m30s',
    },
  },

  thresholds: {
    // 전체 요청의 95%가 1초 이내 응답
    'http_req_duration': ['p(95)<1000'],
    // 주문 생성 95% 2초 이내
    'order_create_duration': ['p(95)<2000'],
    // 상품 조회 95% 500ms 이내 (캐시 효과)
    'product_query_duration': ['p(95)<500'],
    // 주문 성공률 95% 이상
    'order_success_rate': ['rate>0.95'],
    // HTTP 에러율 5% 미만
    'http_req_failed': ['rate<0.05'],
  },
};

// ── Synthetic Data 생성기 ─────────────────────────────────

const PRODUCT_NAMES = [
  '노트북', '마우스', '키보드', '모니터', '헤드셋',
  '웹캠', 'USB허브', '외장SSD', '스피커', '태블릿'
];

const USER_NAMES = [
  'TestUser', 'LoadTester', 'VirtualUser', 'SyntheticUser',
  'PerfTester', 'BenchUser', 'StressUser', 'SimUser'
];

function generateUser() {
  const name = randomItem(USER_NAMES) + randomIntBetween(1, 9999);
  return {
    name: name,
    email: `${name.toLowerCase()}@loadtest.com`
  };
}

function generateProduct() {
  const name = randomItem(PRODUCT_NAMES) + '_' + randomIntBetween(1, 999);
  return {
    name: name,
    price: randomIntBetween(10000, 2000000),
    stock: randomIntBetween(10, 200)
  };
}

function generateOrder(userId, productId) {
  return {
    userId: userId,
    productId: productId,
    quantity: randomIntBetween(1, 5)
  };
}

// ── 공통 헤더 ─────────────────────────────────────────────
const JSON_HEADERS = {
  'Content-Type': 'application/json',
};

// ── 테스트 메인 함수 ──────────────────────────────────────
export default function () {
  // 각 VU가 랜덤하게 시나리오 실행
  const scenario = randomIntBetween(1, 4);

  switch (scenario) {
    case 1: testUserFlow();    break;
    case 2: testProductFlow(); break;
    case 3: testOrderFlow();   break;
    case 4: testCacheFlow();   break;
  }
}

// ── 시나리오 1: 유저 생성 및 조회 플로우 ─────────────────
function testUserFlow() {
  group('User Flow', () => {
    // 유저 생성
    const createRes = http.post(
      `${BASE_URL}/users`,
      JSON.stringify(generateUser()),
      { headers: JSON_HEADERS }
    );

    const created = check(createRes, {
      'user created - status 201': (r) => r.status === 201,
      'user created - has id':     (r) => r.json('id') !== undefined,
    });

    if (!created) {
      apiErrorCount.add(1);
      return;
    }

    const userId = createRes.json('id');

    // 유저 단건 조회
    const getRes = http.get(`${BASE_URL}/users/${userId}`);
    check(getRes, {
      'get user - status 200': (r) => r.status === 200,
      'get user - correct id': (r) => r.json('id') === userId,
    });

    // 전체 유저 목록 조회
    const listRes = http.get(`${BASE_URL}/users`);
    check(listRes, {
      'list users - status 200': (r) => r.status === 200,
    });
  });

  sleep(randomIntBetween(1, 3));
}

// ── 시나리오 2: 상품 생성 및 조회 플로우 ─────────────────
function testProductFlow() {
  group('Product Flow', () => {
    // 상품 생성
    const createRes = http.post(
      `${BASE_URL}/products`,
      JSON.stringify(generateProduct()),
      { headers: JSON_HEADERS }
    );

    check(createRes, {
      'product created - status 201': (r) => r.status === 201,
    });

    // 전체 상품 조회 (캐시 테스트)
    const start = Date.now();
    const listRes = http.get(`${BASE_URL}/products`);
    productQueryTrend.add(Date.now() - start);

    check(listRes, {
      'list products - status 200': (r) => r.status === 200,
    });

    // 상품 ID 1 반복 조회 (캐시 히트 유도)
    const singleRes = http.get(`${BASE_URL}/products/1`);
    const isHit = singleRes.timings.duration < 100;  // 100ms 미만이면 캐시 히트로 간주
    cacheHitRate.add(isHit);

    check(singleRes, {
      'get product - status 200': (r) => r.status === 200,
    });
  });

  sleep(randomIntBetween(1, 2));
}

// ── 시나리오 3: 주문 생성 (Saga 패턴) 플로우 ─────────────
function testOrderFlow() {
  group('Order Flow (Saga)', () => {
    // 주문 생성 (기존 데이터 사용: userId=1, productId=1)
    const orderStart = Date.now();
    const createRes = http.post(
      `${BASE_URL}/orders`,
      JSON.stringify(generateOrder(1, 1)),
      { headers: JSON_HEADERS }
    );
    orderCreateTrend.add(Date.now() - orderStart);

    const orderCreated = check(createRes, {
      'order created - status 201': (r) => r.status === 201,
      'order created - PENDING':    (r) => r.json('status') === 'PENDING',
    });

    orderSuccessRate.add(orderCreated ? 1 : 0);

    if (!orderCreated) {
      apiErrorCount.add(1);
      return;
    }

    const orderId = createRes.json('id');

    // Saga 처리 대기
    sleep(2);

    // 주문 상태 확인 (CONFIRMED 여부)
    const statusRes = http.get(`${BASE_URL}/orders/${orderId}`);
    const confirmed = check(statusRes, {
      'order status confirmed': (r) => r.json('status') === 'CONFIRMED',
    });

    sagaConfirmedRate.add(confirmed ? 1 : 0);
  });

  sleep(randomIntBetween(2, 5));
}

// ── 시나리오 4: 캐시 효과 집중 테스트 ────────────────────
function testCacheFlow() {
  group('Cache Flow', () => {
    // 동일 상품을 연속 3회 조회 (2회차부터 캐시 히트)
    for (let i = 0; i < 3; i++) {
      const productId = randomIntBetween(1, 4);
      const start = Date.now();
      const res = http.get(`${BASE_URL}/products/${productId}`);
      const duration = Date.now() - start;
      productQueryTrend.add(duration);

      check(res, {
        'cache query - status 200': (r) => r.status === 200,
      });

      // 2, 3번째 조회는 캐시 히트 기대
      if (i > 0) {
        cacheHitRate.add(duration < 100 ? 1 : 0);
      }

      sleep(0.1);
    }
  });

  sleep(randomIntBetween(1, 2));
}

// ── 테스트 완료 후 요약 ───────────────────────────────────
export function handleSummary(data) {
  const summary = {
    testDate: new Date().toISOString(),
    totalRequests: data.metrics['http_reqs'] ? data.metrics['http_reqs'].values.count : 0,
    failedRequests: data.metrics['http_req_failed'] ? data.metrics['http_req_failed'].values.passes : 0,
    p95ResponseTime: data.metrics['http_req_duration'] ? data.metrics['http_req_duration'].values['p(95)'] : 0,
    orderSuccessRate: data.metrics['order_success_rate'] ? data.metrics['order_success_rate'].values.rate : 0,
    sagaConfirmedRate: data.metrics['saga_confirmed_rate'] ? data.metrics['saga_confirmed_rate'].values.rate : 0,
    cacheHitRate: data.metrics['cache_hit_rate'] ? data.metrics['cache_hit_rate'].values.rate : 0,
  };

  return {
    'stdout': JSON.stringify(summary, null, 2),
    'load-test/result-summary.json': JSON.stringify(summary, null, 2),
  };
}
