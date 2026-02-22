#!/usr/bin/env bash
# =============================================================
# Monsing API별 순차 성능 테스트
# =============================================================
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOLO_DIR="${PROJECT_ROOT}/k6/solo"
INFLUXDB_URL="http://localhost:8086/k6"
BASE_URL="${BASE_URL:-http://localhost:8090}"
JWT_SECRET="${JWT_SECRET:-xl32frB+bFvlJ4/lgWRSmCqufDua1fEFuX+XAI7Nki57y8a63RGfCFEfjGwG+ZR352FypLWyoLSVF58JwFQuAg==}"

NO_INFLUX=false
STOP_ON_FAIL=false
CHAT_URL="${CHAT_URL:-http://localhost:8080}"

for arg in "$@"; do
  case $arg in
    --no-influx)    NO_INFLUX=true ;;
    --stop-on-fail) STOP_ON_FAIL=true ;;
    -h|--help)
      echo "사용법: $0 [옵션]"
      echo ""
      echo "옵션:"
      echo "  --no-influx      InfluxDB 없이 터미널 출력만"
      echo "  --stop-on-fail   임계값 실패 시 중단 (기본: 계속 진행)"
      echo "  -h, --help       도움말"
      echo ""
      echo "환경 변수:"
      echo "  BASE_URL         API 서버 주소 (기본: http://localhost:8090)"
      echo "  JWT_SECRET       JWT 시크릿"
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
SCRIPTS=(
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
  "chat-messages.js"
  "chat-ws.js"
)

TOTAL=${#SCRIPTS[@]}
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
echo "  Monsing API별 순차 성능 테스트"
echo "========================================"
echo "  API 서버   : ${BASE_URL}"
echo "  Chat 서버  : ${CHAT_URL}"
echo "  스크립트 수: ${TOTAL}개"
echo "  InfluxDB  : $( [[ "$NO_INFLUX" == "true" ]] && echo "비활성화" || echo "${INFLUXDB_URL}" )"
echo "  실패 시    : $( [[ "$STOP_ON_FAIL" == "true" ]] && echo "중단" || echo "계속 진행" )"
echo "========================================"
echo ""

START_TIME=$(date +%s)

# ── 순차 실행 ────────────────────────────────────────────────
for i in "${!SCRIPTS[@]}"; do
  SCRIPT_NAME="${SCRIPTS[$i]}"
  SCRIPT_PATH="${SOLO_DIR}/${SCRIPT_NAME}"
  STEP=$((i + 1))

  echo ""
  echo "────────────────────────────────────────"
  echo "  [${STEP}/${TOTAL}] ${SCRIPT_NAME}"
  echo "────────────────────────────────────────"

  if run_k6 "${SCRIPT_PATH}"; then
    PASSED=$((PASSED + 1))
    echo "  ✓ PASSED"
  else
    FAILED=$((FAILED + 1))
    FAILED_LIST+=("${SCRIPT_NAME}")
    echo "  ✗ FAILED (임계값 초과)"

    if [[ "$STOP_ON_FAIL" == "true" ]]; then
      echo ""
      echo "  --stop-on-fail 옵션으로 인해 중단합니다."
      break
    fi
  fi

  # 다음 테스트 전 서버 안정화 대기 (마지막 스크립트 제외)
  if [[ $STEP -lt $TOTAL ]]; then
    echo "  → 10초 대기 후 다음 테스트..."
    sleep 10
  fi
done

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
MINUTES=$((ELAPSED / 60))
SECONDS=$((ELAPSED % 60))

# ── 최종 결과 ────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  최종 결과"
echo "========================================"
echo "  총 소요 시간 : ${MINUTES}분 ${SECONDS}초"
echo "  통과  : ${PASSED}/${TOTAL}"
echo "  실패  : ${FAILED}/${TOTAL}"

if [[ ${#FAILED_LIST[@]} -gt 0 ]]; then
  echo ""
  echo "  실패한 API:"
  for name in "${FAILED_LIST[@]}"; do
    echo "    ✗ ${name}"
  done
fi

echo ""
echo "  Grafana 대시보드: http://localhost:3000"
echo "========================================"

[[ $FAILED -eq 0 ]]
