#!/usr/bin/env bash
# =============================================================
# Monsing API별 순차 스트레스 테스트 — 한계점 탐색
# =============================================================
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRESS_SOLO_DIR="${PROJECT_ROOT}/k6/stress/solo"
INFLUXDB_URL="http://localhost:8086/k6"
BASE_URL="${BASE_URL:-http://localhost:8090}"
CHAT_URL="${CHAT_URL:-http://localhost:8080}"
JWT_SECRET="${JWT_SECRET:-xl32frB+bFvlJ4/lgWRSmCqufDua1fEFuX+XAI7Nki57y8a63RGfCFEfjGwG+ZR352FypLWyoLSVF58JwFQuAg==}"

NO_INFLUX=false
SKIP_CHAT=false
COOLDOWN=20   # 스트레스 테스트 후 서버 안정화 대기 (초)

for arg in "$@"; do
  case $arg in
    --no-influx)  NO_INFLUX=true ;;
    --skip-chat)  SKIP_CHAT=true ;;
    -h|--help)
      echo "사용법: $0 [옵션]"
      echo ""
      echo "옵션:"
      echo "  --no-influx   InfluxDB 없이 터미널 출력만"
      echo "  --skip-chat   WebSocket 스트레스 테스트 건너뜀"
      echo "  -h, --help    도움말"
      echo ""
      echo "환경 변수:"
      echo "  BASE_URL      API 서버 주소 (기본: http://localhost:8090)"
      echo "  CHAT_URL      채팅 서버 주소 (기본: http://localhost:8080)"
      echo "  JWT_SECRET    JWT 시크릿"
      echo ""
      echo "예상 소요 시간: API 10개 × 3분30초 + WebSocket 3분30초 + 쿨다운 ≈ 45분"
      exit 0
      ;;
  esac
done

# ── k6 설치 확인 ─────────────────────────────────────────────
if ! command -v k6 &>/dev/null; then
  echo "[오류] k6가 설치되지 않았습니다. brew install k6"
  exit 1
fi

# ── 실행할 스크립트 목록 (순서대로) ──────────────────────────
HTTP_SCRIPTS=(
  "teachers-list.js"
  "teacher-detail.js"
  "courses.js"
  "lessons.js"
  "feedback-items.js"
  "feedback-item-detail.js"
  "feedback-my.js"
  "feedback-tickets.js"
  "my-lessons.js"
  "records-my.js"
)

TOTAL_HTTP=${#HTTP_SCRIPTS[@]}
if [[ "$SKIP_CHAT" == "true" ]]; then
  TOTAL=$TOTAL_HTTP
else
  TOTAL=$((TOTAL_HTTP + 1))
fi
PASSED=0
FAILED=0
FAILED_LIST=()

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

# ── 헤더 출력 ────────────────────────────────────────────────
echo "========================================"
echo "  Monsing API별 순차 스트레스 테스트"
echo "========================================"
echo "  API 서버   : ${BASE_URL}"
echo "  Chat 서버  : ${CHAT_URL}"
echo "  스크립트 수: ${TOTAL}개"
echo "  InfluxDB  : $( [[ "$NO_INFLUX" == "true" ]] && echo "비활성화" || echo "${INFLUXDB_URL}" )"
echo "  Chat 테스트: $( [[ "$SKIP_CHAT" == "true" ]] && echo "건너뜀" || echo "포함" )"
echo ""
echo "  [부하 프로파일] 0→100→300→500→1000 VU, think time 0.1s"
echo "  [쿨다운] 테스트 사이 ${COOLDOWN}초 대기"
echo ""
echo "  ⚠  스트레스 테스트는 서버 한계를 의도적으로 초과합니다."
echo "     임계값 실패가 발생해도 정상 — Grafana 그래프에 집중하세요."
echo "========================================"
echo ""

START_TIME=$(date +%s)

# ── HTTP API 순차 실행 ────────────────────────────────────────
for i in "${!HTTP_SCRIPTS[@]}"; do
  SCRIPT_NAME="${HTTP_SCRIPTS[$i]}"
  SCRIPT_PATH="${STRESS_SOLO_DIR}/${SCRIPT_NAME}"
  STEP=$((i + 1))

  echo "────────────────────────────────────────"
  echo "  [${STEP}/${TOTAL}] ${SCRIPT_NAME}"
  echo "────────────────────────────────────────"

  if run_k6 "${SCRIPT_PATH}"; then
    PASSED=$((PASSED + 1))
    echo "  ✓ 임계값 통과"
  else
    FAILED=$((FAILED + 1))
    FAILED_LIST+=("${SCRIPT_NAME}")
    echo "  ✗ 임계값 초과 (예상 범위 — 결과 수치 확인)"
  fi

  # 다음 테스트 전 서버 안정화 대기
  if [[ $STEP -lt $TOTAL ]]; then
    echo "  → ${COOLDOWN}초 대기 후 다음 테스트..."
    sleep "${COOLDOWN}"
    echo ""
  fi
done

# ── WebSocket 스트레스 ────────────────────────────────────────
if [[ "$SKIP_CHAT" == "false" ]]; then
  WS_STEP=$((TOTAL_HTTP + 1))

  echo "────────────────────────────────────────"
  echo "  [${WS_STEP}/${TOTAL}] chat-ws.js (WebSocket)"
  echo "────────────────────────────────────────"

  if run_k6 "${STRESS_SOLO_DIR}/chat-ws.js"; then
    PASSED=$((PASSED + 1))
    echo "  ✓ 임계값 통과"
  else
    FAILED=$((FAILED + 1))
    FAILED_LIST+=("chat-ws.js")
    echo "  ✗ 임계값 초과 (예상 범위 — 결과 수치 확인)"
  fi
else
  echo "  [WebSocket] 건너뜀 (--skip-chat)"
fi

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
MINUTES=$((ELAPSED / 60))
SECONDS_REM=$((ELAPSED % 60))

# ── 최종 결과 ────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  스트레스 테스트 완료"
echo "========================================"
echo "  총 소요 시간 : ${MINUTES}분 ${SECONDS_REM}초"
echo "  임계값 통과  : ${PASSED}/${TOTAL}"
echo "  임계값 초과  : ${FAILED}/${TOTAL}"

if [[ ${#FAILED_LIST[@]} -gt 0 ]]; then
  echo ""
  echo "  임계값 초과 API (한계 구간 도달):"
  for name in "${FAILED_LIST[@]}"; do
    echo "    ✗ ${name}"
  done
fi

echo ""
echo "  ※ 임계값 초과는 서버 한계 구간에서 정상입니다."
echo "     각 API별 Grafana 그래프에서 RPS·p95·에러율이"
echo "     몇 VU에서 무너지는지 확인하세요."
echo "  Grafana: http://localhost:3000"
echo "========================================"

# 스트레스 테스트는 임계값 초과해도 exit 0 (관찰 목적)
exit 0
