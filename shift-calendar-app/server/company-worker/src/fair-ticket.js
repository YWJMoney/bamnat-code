// 2.0.0 [RVQ-59] 검토 I22c-03: 누구인지 모르게, 한 사람 한 번만 세는 '쓰는 분'. 서버가 설치마다 하루 한 장 '표'를 눈가림 서명(RSA
// 블라인드 서명, 전체 영역 해시)해 주면, 앱은 그 표를 작품을 넣을 때 함께 보내요. 서버는 서명할 때 표를 보지 못해 어떤 표가 어느 휴대폰
// 것인지 모르고, 받을 때는 '이 표를 이 작품에 쓴 적이 있는지'만 봐요(표 지문·작품·날, 30일). 깃발 market.fair_count(처리방침 3.15)와
// 비밀값 RECIPE_TICKET_KEY(RSA 2048 이상 개인 열쇠 JWK)가 있을 때만이에요. 앱 쪽 같은 계산은 core plaza/BlindTicket.kt.
const fairEnc = new TextEncoder();

export const TICKET = {
  flag: "market.fair_count",
  /** The ticket's words: `bamnat-ticket|1|<KST 날>|<32바이트 난수, base64url>` (the app makes it; the server never sees it until it is spent). */
  pattern: /^bamnat-ticket\|1\|(\d{4}-\d{2}-\d{2})\|[A-Za-z0-9_-]{43}$/,
  /** How long a ticket's 지문 stays with the works it counted on. */
  keepDays: 30,
  /** Tickets one install may have signed a KST day. */
  perDeviceDay: 1,
  /** Tickets one network may have signed a KST day (a ward's Wi-Fi is many phones). */
  perNetworkDay: 300,
};

export const fairB64 = (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
export function fairUnb64(s) {
  if (typeof s !== "string" || !/^[A-Za-z0-9_-]+$/.test(s) || s.length > 1400) return null;
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}
export const fairBig = (bytes) => (bytes.length ? BigInt(`0x${[...bytes].map((b) => b.toString(16).padStart(2, "0")).join("")}`) : 0n);
export function fairBytes(n, len) {
  const hex = n.toString(16).padStart(len * 2, "0");
  if (hex.length > len * 2) throw new Error("too big");
  return Uint8Array.from(hex.match(/../g), (h) => parseInt(h, 16));
}

export function fairModPow(base, exp, mod) {
  let r = 1n;
  let b = base % mod;
  let e = exp;
  while (e > 0n) {
    if (e & 1n) r = (r * b) % mod;
    e >>= 1n;
    b = (b * b) % mod;
  }
  return r;
}

/** The signing key from the secret RECIPE_TICKET_KEY (an RSA private JWK), or null when it is not set or not one. */
export function ticketKey(env) {
  let jwk;
  try { jwk = typeof env.RECIPE_TICKET_KEY === "string" ? JSON.parse(env.RECIPE_TICKET_KEY) : null; } catch { return null; }
  if (jwk?.kty !== "RSA") return null;
  const part = (k) => { const b = fairUnb64(jwk[k]); return b ? fairBig(b) : null; };
  const [n, e, d, p, q, dp, dq, qi] = ["n", "e", "d", "p", "q", "dp", "dq", "qi"].map(part);
  if (!n || !e || !d || !p || !q || !dp || !dq || !qi || n.toString(2).length < 2048) return null;
  const nBytes = Math.ceil(n.toString(16).length / 2);
  return { n, e, d, p, q, dp, dq, qi, nBytes, pub: { n: jwk.n, e: jwk.e } };
}

/** The ticket as a number below n: SHA-256 of (words ‖ 4-byte counter) end to end, cut to one byte shorter than n. */
export async function fairHash(words, nBytes) {
  const msg = fairEnc.encode(words);
  const out = new Uint8Array(nBytes - 1);
  for (let i = 0, at = 0; at < out.length; i++) {
    const block = new Uint8Array(msg.length + 4);
    block.set(msg);
    new DataView(block.buffer).setUint32(msg.length, i);
    const h = new Uint8Array(await crypto.subtle.digest("SHA-256", block));
    out.set(h.subarray(0, Math.min(32, out.length - at)), at);
    at += 32;
  }
  return fairBig(out);
}

/** Signs a blinded number (the server cannot see what is under it), with the key's CRT parts. */
export function blindSign(key, blinded) {
  if (blinded <= 1n || blinded >= key.n) return null;
  const m1 = fairModPow(blinded, key.dp, key.p);
  const m2 = fairModPow(blinded, key.dq, key.q);
  const h = (key.qi * (((m1 - m2) % key.p) + key.p)) % key.p;
  return m2 + h * key.q;
}

/** Whether [sig] signs [words] (the ticket as it is spent). */
export async function ticketSigned(key, words, sig) {
  if (sig <= 1n || sig >= key.n) return false;
  return fairModPow(sig, key.e, key.n) === (await fairHash(words, key.nBytes));
}

/** The ticket's 지문 as kept with the works it counted on: never the ticket itself. */
export async function ticketPrint(words) {
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", fairEnc.encode(`fair|${words}`)));
  return fairB64(h.subarray(0, 16));
}
