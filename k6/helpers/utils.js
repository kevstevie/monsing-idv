/**
 * [min, max] 범위의 랜덤 정수
 */
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * 커서 기반 페이징의 lastId를 랜덤하게 선택
 * 절반 확률로 0(첫 페이지), 나머지는 범위 내 랜덤
 */
export function randomCursor(max) {
  return Math.random() < 0.5 ? 0 : randomInt(0, max);
}

/**
 * 응답 상태 코드 확인 및 태그 부착
 * @returns {boolean} 성공 여부
 */
export function checkStatus(res, expectedStatus = 200) {
  return res.status === expectedStatus;
}

/** 공통 JSON 헤더 */
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** UUID v4 생성 */
export function randomUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}
