#!/usr/bin/env bash
# =============================================================
# Monsing 로컬 Docker 환경 종료 스크립트
# =============================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.local.yml"

VOLUMES=false
for arg in "$@"; do
  case $arg in
    -v|--volumes)
      VOLUMES=true
      ;;
    -h|--help)
      echo "사용법: $0 [옵션]"
      echo ""
      echo "옵션:"
      echo "  -v, --volumes  볼륨 데이터까지 삭제 (DB 초기화)"
      echo "  -h, --help     도움말 출력"
      exit 0
      ;;
  esac
done

echo "========================================"
echo "  Monsing 로컬 Docker 환경 종료"
echo "========================================"

if [ "$VOLUMES" = true ]; then
  echo "컨테이너 및 볼륨(DB 데이터) 삭제 중..."
  docker compose -f "$COMPOSE_FILE" down --volumes
  echo "볼륨 삭제 완료 (다음 시작 시 스키마 재적용됨)"
else
  echo "컨테이너 종료 중 (데이터 보존)..."
  docker compose -f "$COMPOSE_FILE" down
fi

echo "완료"
