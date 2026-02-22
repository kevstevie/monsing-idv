#!/usr/bin/env bash
# =============================================================
# Monsing 로컬 Docker 환경 빌드 & 시작 스크립트
# =============================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.local.yml"

# ── 옵션 파싱 ────────────────────────────────────────────────
BUILD=true
INFRA_ONLY=false
NO_CACHE=false

for arg in "$@"; do
  case $arg in
    --no-build)   BUILD=false ;;
    --infra-only) INFRA_ONLY=true ;;
    --no-cache)   NO_CACHE=true ;;
    -h|--help)
      echo "사용법: $0 [옵션]"
      echo ""
      echo "옵션:"
      echo "  --no-build    JAR 빌드 건너뜀 (이미 빌드된 JAR 사용)"
      echo "  --infra-only  인프라 서비스만 시작 (MySQL/Redis/MongoDB/LocalStack)"
      echo "  --no-cache    Docker 레이어 캐시 없이 이미지 재빌드"
      echo "  -h, --help    도움말 출력"
      exit 0
      ;;
  esac
done

echo "========================================"
echo "  Monsing 로컬 Docker 환경 시작"
echo "========================================"

# ── 1. JAR 빌드 ──────────────────────────────────────────────
if [ "$BUILD" = true ] && [ "$INFRA_ONLY" = false ]; then
  echo ""
  echo "[1/2] Gradle 빌드 (테스트 제외)..."
  cd "$PROJECT_ROOT"
  ./gradlew :api:build :chat:build -x test --parallel
  echo "      빌드 완료"
else
  echo "[1/2] 빌드 건너뜀"
fi

# ── 2. Docker Compose 시작 ────────────────────────────────────
echo ""
if [ "$INFRA_ONLY" = true ]; then
  echo "[2/2] 인프라 서비스만 시작 중..."
  SERVICES="mysql redis mongodb localstack"
else
  echo "[2/2] 전체 서비스 시작 중..."
  SERVICES=""
fi

DOCKER_BUILD_FLAG=""
if [ "$NO_CACHE" = true ]; then
  DOCKER_BUILD_FLAG="--no-cache"
fi

docker compose -f "$COMPOSE_FILE" up \
  --build $DOCKER_BUILD_FLAG \
  --detach \
  $SERVICES

# ── 3. 상태 출력 ─────────────────────────────────────────────
echo ""
echo "========================================"
echo "  서비스 상태"
echo "========================================"
docker compose -f "$COMPOSE_FILE" ps

echo ""
echo "========================================"
echo "  접속 주소"
echo "========================================"
echo "  API Server  : http://localhost:8090"
echo "  Chat Server : http://localhost:8080"
echo "  Swagger UI  : http://localhost:8090/swagger-ui/index.html"
echo "  MySQL       : localhost:3306  (monsing / monsing)"
echo "  Redis       : localhost:6379"
echo "  MongoDB     : localhost:27017"
echo "  LocalStack  : http://localhost:4566"
echo ""
echo "  로그 확인  : docker compose -f docker-compose.local.yml logs -f [서비스명]"
echo "  종료       : ./scripts/local-down.sh"
echo "========================================"
