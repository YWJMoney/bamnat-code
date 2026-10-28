// R131 1.31 [ADMX-16] 두 번째 확인: a six-digit code from an authenticator app (TOTP, RFC 6238 over HOTP, RFC 4226: HMAC-SHA-1,
// 30-second steps, six digits), the way Google Authenticator, Microsoft Authenticator and 1Password make them. An admin turns it on
// under 관리 › 내 계정·이 기기; from then on a right password still needs the code. Nothing here reads the database.
//
// tools/bundle.mjs puts this file in place of its import line, so its names share src/index.js's scope: each starts with `totp`.

const TOTP_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
/** Seconds a code stands for, and how many steps either side are still taken (a phone's clock a little off). */
export const TOTP_STEP = 30;
export const TOTP_DRIFT = 1;

/** A new secret: 20 random bytes as base32 (what authenticator apps read), 32 letters. */
export function totpSecret() {
  return totpBase32(crypto.getRandomValues(new Uint8Array(20)));
}

export function totpBase32(bytes) {
  let bits = 0;
  let value = 0;
  let out = "";
  for (const b of bytes) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += TOTP_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += TOTP_ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

export function totpBytes(text) {
  const clean = String(text).toUpperCase().replace(/[\s=-]/g, "");
  const out = [];
  let bits = 0;
  let value = 0;
  for (const ch of clean) {
    const v = TOTP_ALPHABET.indexOf(ch);
    if (v < 0) throw new Error("base32");
    value = (value << 5) | v;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  return new Uint8Array(out);
}

/** The six-digit code of [secret] at time step [step] (RFC 4226 5.3). */
export async function totpCode(secret, step) {
  const key = await crypto.subtle.importKey("raw", totpBytes(secret), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const msg = new Uint8Array(8);
  let n = step;
  for (let i = 7; i >= 0; i--) {
    msg[i] = n & 255;
    n = Math.floor(n / 256);
  }
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, msg));
  const off = mac[mac.length - 1] & 15;
  const bin = ((mac[off] & 127) << 24) | (mac[off + 1] << 16) | (mac[off + 2] << 8) | mac[off + 3];
  return String(bin % 1_000_000).padStart(6, "0");
}

/**
 * The step [code] is right for at [now] (within [TOTP_DRIFT] steps), or null. A step at or before [usedStep] is refused: the same
 * code is never taken twice.
 */
export async function totpCheck(secret, code, now, usedStep = -1) {
  if (typeof code !== "string" || !/^\d{6}$/.test(code.trim())) return null;
  const at = Math.floor(now / 1000 / TOTP_STEP);
  for (let d = -TOTP_DRIFT; d <= TOTP_DRIFT; d++) {
    const step = at + d;
    if (step <= usedStep) continue;
    if ((await totpCode(secret, step)) === code.trim()) return step;
  }
  return null;
}

/** What an authenticator app reads from a link or a QR code: otpauth://totp/밤낮 관리자:<who>?secret=…&issuer=밤낮 관리자. */
export function totpUri(secret, who) {
  const label = encodeURIComponent(`밤낮 관리자:${who}`);
  return `otpauth://totp/${label}?secret=${secret}&issuer=${encodeURIComponent("밤낮 관리자")}&algorithm=SHA1&digits=6&period=${TOTP_STEP}`;
}
