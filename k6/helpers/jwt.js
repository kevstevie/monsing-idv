import crypto   from 'k6/crypto';
import encoding from 'k6/encoding';

/**
 * HS512 JWT 토큰 생성
 * AuthTokenPayload: { id: Long }  →  subject: JSON.stringify({ id })
 * JJWT의 Keys.hmacShaKeyFor(secret.toByteArray()) 동작과 동일하게
 * secret 문자열의 UTF-8 바이트를 HMAC-SHA512 키로 사용
 */
export function generateToken(memberId, secret) {
  const header  = { alg: 'HS512', typ: 'JWT' };
  const now     = Math.floor(Date.now() / 1000);
  const payload = {
    sub: JSON.stringify({ id: memberId }),  // AuthTokenPayload 직렬화 형식
    iat: now,
    exp: now + 864000,                       // 10일 (application-local.yml 기준)
  };

  const h   = encoding.b64encode(JSON.stringify(header),  'rawurl');
  const p   = encoding.b64encode(JSON.stringify(payload), 'rawurl');
  const msg = `${h}.${p}`;
  const sig = crypto.hmac('sha512', secret, msg, 'base64rawurl');

  return `${msg}.${sig}`;
}

/**
 * Authorization 헤더 객체 반환
 */
export function authHeader(token) {
  return { Authorization: `Bearer ${token}` };
}
