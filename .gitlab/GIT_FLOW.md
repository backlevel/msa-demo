# Git Flow 브랜치 전략

## 브랜치 구조

```
main          ← 운영 배포 (태그 기반 릴리즈)
 └── develop  ← 통합 개발 브랜치
      ├── feature/order-saga-pattern    ← 기능 개발
      ├── feature/redis-cache           ← 기능 개발
      ├── hotfix/order-status-bug       ← 긴급 패치
      └── release/v1.2.0               ← 릴리즈 준비
```

## 브랜치별 CI/CD 규칙

| 브랜치       | test | build | push ECR | deploy ECS |
|-------------|:----:|:-----:|:--------:|:----------:|
| feature/*   |  ✔   |       |          |            |
| develop     |  ✔   |  ✔    |          |            |
| main        |  ✔   |  ✔    |    ✔     |  ✔ (수동)  |

## 작업 흐름

### 1. 기능 개발
```bash
git checkout develop
git pull origin develop
git checkout -b feature/새기능명

# 개발 후
git add .
git commit -m "feat: 주문 Saga 패턴 구현"
git push origin feature/새기능명

# GitLab에서 develop 으로 MR 생성
```

### 2. MR(Merge Request) 규칙
- 최소 1명 이상 코드 리뷰 후 머지
- CI 파이프라인(test) 통과 필수
- WIP: 붙이면 자동 머지 방지
- 스쿼시 머지 권장 (커밋 히스토리 정리)

### 3. 릴리즈
```bash
git checkout -b release/v1.2.0 develop
# 버전 번호, CHANGELOG 업데이트
git checkout main
git merge release/v1.2.0
git tag -a v1.2.0 -m "v1.2.0 릴리즈"
git push origin main --tags
```

### 4. 긴급 패치
```bash
git checkout -b hotfix/긴급버그 main
# 수정 후
git checkout main && git merge hotfix/긴급버그
git checkout develop && git merge hotfix/긴급버그
```

## 커밋 메시지 컨벤션

```
<타입>: <제목>

[본문]

[이슈 번호]
```

| 타입       | 설명                    |
|-----------|------------------------|
| feat      | 새 기능 추가            |
| fix       | 버그 수정               |
| refactor  | 리팩토링                |
| test      | 테스트 추가/수정         |
| docs      | 문서 수정               |
| chore     | 빌드/설정 변경          |
| perf      | 성능 개선               |

예시:
```
feat: order-service Kafka Saga 패턴 구현

- OrderSagaOrchestrator 추가
- order.created 이벤트 발행
- stock.decreased/failed 수신 후 상태 전환

Closes #42
```
