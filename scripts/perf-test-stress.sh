#!/usr/bin/env bash
# =============================================================
# Monsing 스트레스 테스트 — 시스템 한계점 탐색
# =============================================================
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRESS_DIR="${PROJECT_ROOT}/k6/stress"
INFLUXDB_URL="http://localhost:8086/k6?pushInterval=5s"
BASE_URL="${BASE_URL:-http://localhost:8090}"
CHAT_URL="${CHAT_URL:-http://localhost:8080}"
JWT_SECRET="${JWT_SECRET:-xl32frB+bFvlJ4/lgWRSmCqufDua1fEFuX+XAI7Nki57y8a63RGfCFEfjGwG+ZR352FypLWyoLSVF58JwFQuAg==}"

NO_INFLUX=false
SKIP_CHAT=false

for arg in "$@"; do
  case $arg in
    --no-influx)  NO_INFLUX=true ;;
    --skip-chat)  SKIP_CHAT=true ;;
    -h|--help)
      echo "사용법: $0 [옵션]"
      echo ""
      echo "옵션:"
      echo "  --no-influx   InfluxDB 없이 터미널 출력만"
      echo "  --skip-chat   채팅 스트레스 테스트 건너뜀"
      echo "  -h, --help    도움말"
      echo ""
      echo "환경 변수:"
      echo "  BASE_URL      API 서버 주소 (기본: http://localhost:8090)"
      echo "  CHAT_URL      채팅 서버 주소 (기본: http://localhost:8080)"
      echo "  JWT_SECRET    JWT 시크릿"
      exit 0
      ;;
  esac
done

if ! command -v k6 &>/dev/null; then
  echo "[오류] k6가 설치되지 않았습니다. brew install k6"
  exit 1
fi

# ── k6 실행 함수 ─────────────────────────────────────────────
run_k6() {
  local script="$1"
  if [[ "$NO_INFLUX" == "true" ]]; then
    k6 run \
      -e BASE_URL="${BASE_URL}" \
      -e CHAT_URL="${CHAT_URL}" \
      -e JWT_SECRET="${JWT_SECRET}" \
      "${script}"
  else
    k6 run \
      -e BASE_URL="${BASE_URL}" \
      -e CHAT_URL="${CHAT_URL}" \
      -e JWT_SECRET="${JWT_SECRET}" \
      --out "influxdb=${INFLUXDB_URL}" \
      "${script}"
  fi
}

# ── 헤더 ─────────────────────────────────────────────────────
echo "========================================"
echo "  Monsing 스트레스 테스트"
echo "========================================"
echo "  API 서버   : ${BASE_URL}"
echo "  Chat 서버  : ${CHAT_URL}"
echo "  InfluxDB  : $( [[ "$NO_INFLUX" == "true" ]] && echo "비활성화" || echo "${INFLUXDB_URL}" )"
echo ""
echo "  [부하 프로파일]"
echo "  REST API:   0→100→300→500→1000 VU (총 3분 30초)"
echo "  WebSocket:  0→50→150→300→500 VU  (총 3분 30초)"
echo "========================================"
echo ""
echo "  ⚠️  스트레스 테스트는 서버 한계를 의도적으로 초과합니다."
echo "     임계값 실패가 발생해도 정상입니다 — 결과 수치에 집중하세요."
echo ""

START_TIME=$(date +%s)
PASSED=0
FAILED=0

# ── 1. REST API 스트레스 ─────────────────────────────────────
echo "────────────────────────────────────────"
echo "  [1/2] REST API 스트레스 (api-stress.js)"
echo "────────────────────────────────────────"

if run_k6 "${STRESS_DIR}/api-stress.js"; then
  PASSED=$((PASSED + 1))
  echo "  ✓ 임계값 통과"
else
  FAILED=$((FAILED + 1))
  echo "  ✗ 임계값 초과 (예상 범위 — 결과 수치 확인)"
fi

# ── 2. 채팅 WebSocket 스트레스 ──────────────────────────────
if [[ "$SKIP_CHAT" == "false" ]]; then
  echo ""
  echo "  → 20초 대기 후 채팅 스트레스 테스트..."
  sleep 20

  echo "────────────────────────────────────────"
  echo "  [2/2] 채팅 WebSocket 스트레스 (chat-stress.js)"
  echo "────────────────────────────────────────"

  if run_k6 "${STRESS_DIR}/chat-stress.js"; then
    PASSED=$((PASSED + 1))
    echo "  ✓ 임계값 통과"
  else
    FAILED=$((FAILED + 1))
    echo "  ✗ 임계값 초과 (예상 범위 — 결과 수치 확인)"
  fi
else
  echo "  [2/2] 채팅 스트레스 건너뜀 (--skip-chat)"
fi

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
MINUTES=$((ELAPSED / 60))
SECONDS=$((ELAPSED % 60))

# ── 최종 결과 ────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  스트레스 테스트 완료"
echo "========================================"
echo "  총 소요 시간 : ${MINUTES}분 ${SECONDS}초"
echo "  임계값 통과  : ${PASSED}개"
echo "  임계값 초과  : ${FAILED}개"
echo ""
echo "  ※ 임계값 초과는 서버 한계 구간에서 정상"
echo "     Grafana에서 RPS·에러율·응답시간 그래프를 확인하세요"
echo "  Grafana: http://localhost:3000"
echo "========================================"

# 스트레스 테스트는 임계값 초과해도 exit 0 (관찰 목적)
exit 0
