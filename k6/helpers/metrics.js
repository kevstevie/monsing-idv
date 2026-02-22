/**
 * 성공/4xx 응답 시간을 별도로 추적하는 커스텀 메트릭
 *
 * - http_req_duration_success : 2xx 응답 소요시간
 * - http_req_duration_4xx     : 4xx 응답 소요시간 (예상된 클라이언트 에러)
 * - 5xx + 네트워크 에러는 기록하지 않음 → http_req_failed 로 추적됨
 *
 * 사용법:
 *   import http from 'k6/http';
 *   import { recordDuration } from '../helpers/metrics.js';
 *
 *   // 모듈 레벨(init phase)에서 설정
 *   http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));
 *
 *   export default function() {
 *     const res = http.get(url);
 *     recordDuration(res);
 *   }
 */
import { Trend } from 'k6/metrics';

/** 2xx 성공 응답 소요시간 (ms) */
export const durationSuccess = new Trend('http_req_duration_success', true);

/** 4xx 예상 에러 응답 소요시간 (ms) */
export const duration4xx = new Trend('http_req_duration_4xx', true);

/**
 * HTTP 응답을 성공/4xx로 분류해 메트릭에 기록한다.
 * 5xx 및 네트워크 에러는 무시한다.
 *
 * @param {object} res - k6 HTTP Response 객체
 */
export function recordDuration(res) {
  const d = res.timings.duration;
  if (res.status >= 200 && res.status < 400) {
    durationSuccess.add(d);
  } else if (res.status >= 400 && res.status < 500) {
    duration4xx.add(d);
  }
}
