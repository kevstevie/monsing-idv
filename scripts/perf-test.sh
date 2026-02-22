#!/usr/bin/env bash
# =============================================================
# Monsing 성능 테스트 실행 스크립트
# =============================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INFLUXDB_URL="http://localhost:8086/k6"
BASE_URL="${BASE_URL:-http://localhost:8090}"

# JWT secret: application-app.yml (app 프로파일이 local보다 우선)
JWT_SECRET="${JWT_SECRET:-xl32frB+bFvlJ4/lgWRSmCqufDua1fEFuX+XAI7Nki57y8a63RGfCFEfjGwG+ZR352FypLWyoLSVF58JwFQuAg==}"

# ── 옵션 파싱 ────────────────────────────────────────────────
NO_INFLUX=false
SCRIPT="${PROJECT_ROOT}/k6/main.js"

for arg in "$@"; do
  case $arg in
    --no-influx) NO_INFLUX=true ;;
    --script=*)  SCRIPT="${arg#--script=}" ;;
    -h|--help)
      echo "사용법: $0 [옵션]"
      echo ""
      echo "옵션:"
      echo "  --no-influx        InfluxDB 없이 터미널 출력만"
      echo "  --script=<path>    특정 시나리오만 실행 (기본: k6/main.js)"
      echo "  -h, --help         도움말"
      echo ""
      echo "환경 변수:"
      echo "  BASE_URL           API 서버 주소 (기본: http://localhost:8090)"
      echo "  JWT_SECRET         JWT 시크릿 (기본: application-app.yml 값)"
      echo ""
      echo "예시:"
      echo "  $0                                          # 전체 시나리오"
      echo "  $0 --no-influx                              # 터미널 출력만"
      echo "  $0 --script=k6/scenarios/teachers.js        # 선생님 API만"
      echo "  BASE_URL=http://localhost:8090 $0           # 서버 주소 변경"
      exit 0
      ;;
  esac
done

# ── k6 설치 확인 ─────────────────────────────────────────────
if ! command -v k6 &>/dev/null; then
  echo "[오류] k6가 설치되지 않았습니다."
  echo ""
  echo "설치 방법:"
  echo "  macOS:  brew install k6"
  echo "  Docker: docker run --rm -i grafana/k6 ..."
  echo "  공식:   https://k6.io/docs/get-started/installation/"
  exit 1
fi

echo "========================================"
echo "  Monsing 성능 테스트 시작"
echo "========================================"
echo "  대상 서버  : ${BASE_URL}"
echo "  스크립트   : ${SCRIPT}"
echo "  InfluxDB  : $( [[ "$NO_INFLUX" == "true" ]] && echo "비활성화" || echo "${INFLUXDB_URL}" )"
echo "========================================"
echo ""

# ── k6 실행 ──────────────────────────────────────────────────
if [[ "$NO_INFLUX" == "true" ]]; then
  k6 run \
    -e BASE_URL="${BASE_URL}" \
    -e JWT_SECRET="${JWT_SECRET}" \
    "${SCRIPT}"
else
  k6 run \
    -e BASE_URL="${BASE_URL}" \
    -e JWT_SECRET="${JWT_SECRET}" \
    --out "influxdb=${INFLUXDB_URL}" \
    "${SCRIPT}"
fi

echo ""
echo "========================================"
echo "  테스트 완료"
echo "  Grafana 대시보드: http://localhost:3000"
echo "========================================"
