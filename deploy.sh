#!/bin/bash
# ============================================================
# deploy.sh - 로컬에서 특정 서비스만 수동 배포할 때 사용
# 사전 준비: aws configure (IAM 키 설정)
# 사용법: ./deploy.sh order-service v1.2.3
# ============================================================

set -e

SERVICE=${1:?"Usage: ./deploy.sh <service-name> [tag]"}
TAG=${2:-latest}
REGION="ap-northeast-2"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
CLUSTER="msa-demo-cluster"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo -e "${YELLOW}== $SERVICE 배포 시작 (tag: $TAG) ==${NC}"

# 1) Gradle 빌드
echo "▶ Gradle 빌드..."
./gradlew :${SERVICE}:bootJar -x test --no-daemon

# 2) Docker 빌드
echo "▶ Docker 빌드..."
docker build -f ${SERVICE}/Dockerfile -t ${SERVICE}:${TAG} .

# 3) ECR 로그인 + Push
echo "▶ ECR Push..."
aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $ECR_REGISTRY

docker tag ${SERVICE}:${TAG} $ECR_REGISTRY/${SERVICE}:${TAG}
docker tag ${SERVICE}:${TAG} $ECR_REGISTRY/${SERVICE}:latest
docker push $ECR_REGISTRY/${SERVICE}:${TAG}
docker push $ECR_REGISTRY/${SERVICE}:latest

# 4) 태스크 정의 업데이트
echo "▶ ECS 태스크 정의 업데이트..."
TASK_DEF=$(aws ecs describe-task-definition \
  --task-definition $SERVICE \
  --region $REGION \
  --query 'taskDefinition' \
  --output json)

NEW_TASK_DEF=$(echo $TASK_DEF | python3 -c "
import json, sys
t = json.load(sys.stdin)
for c in t['containerDefinitions']:
    if c['name'] == '$SERVICE':
        c['image'] = '$ECR_REGISTRY/$SERVICE:$TAG'
for k in ['taskDefinitionArn','revision','status','requiresAttributes',
          'compatibilities','registeredAt','registeredBy']:
    t.pop(k, None)
print(json.dumps(t))")

NEW_REV=$(aws ecs register-task-definition \
  --region $REGION \
  --cli-input-json "$NEW_TASK_DEF" \
  --query 'taskDefinition.revision' \
  --output text)

echo "  새 태스크 정의: $SERVICE:$NEW_REV"

# 5) ECS 서비스 업데이트 (롤링 배포)
echo "▶ ECS 서비스 업데이트..."
aws ecs update-service \
  --cluster $CLUSTER \
  --service $SERVICE \
  --task-definition "$SERVICE:$NEW_REV" \
  --region $REGION \
  --force-new-deployment > /dev/null

# 6) 안정화 대기
echo "▶ 배포 안정화 대기 (최대 5분)..."
aws ecs wait services-stable \
  --cluster $CLUSTER \
  --services $SERVICE \
  --region $REGION

echo -e "${GREEN}== 배포 완료: $SERVICE:$TAG (revision=$NEW_REV) ==${NC}"
