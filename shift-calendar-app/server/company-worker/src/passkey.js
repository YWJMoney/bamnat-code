import { json, one, randomId, readJson, run } from "./index.js";

// ---- 1.38 [ADM3-01] 패스키(지문·얼굴) 로그인 ----
//
// 보고서 ADM3-01. 휴대폰(갤럭시 폴드)의 지문으로 관리자 앱에 들어와요. 비밀번호 로그인은 그대로 있고, 패스키는 내 계정 › 이 기기에서
// 로그인한 뒤에만 더해요. WebAuthn: 등록은 ES256(P-256) 공개 키만 받아 남기고(attestation 'none'), 로그인은 서명을 서버가 확인해요.
// 한 번 쓴 도전값(challenge)은 지우고 5분이 지나면 못 써요. 공개 키만 남기니 새는 비밀이 없어요.

export const PASSKEY = { ttlMs: 5 * 60 * 1000, perAdmin: 5 };

export const PASSKEY_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS admin_passkeys (id TEXT PRIMARY KEY, admin TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', jwk TEXT NOT NULL,
    counter INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, last_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS admin_passkeys_admin ON admin_passkeys (admin)",
];

export const b64u = {
  enc: (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""),
  dec: (text) => {
    const s = String(text ?? "").replace(/-/g, "+").replace(/_/g, "/");
    if (!/^[A-Za-z0-9+/]*$/.test(s)) throw new Error("b64");
    const bin = atob(s + "===".slice((s.length + 3) % 4));
    return Uint8Array.from(bin, (c) => c.charCodeAt(0));
  },
};

/** 작은 CBOR 읽개(WebAuthn에 나오는 것만: 정수, 바이트, 글, 배열, 맵, 참·거짓). [at]부터 하나를 읽어 {v, at}. */
export function cbor(bytes, at = 0) {
  const b = bytes[at];
  const major = b >> 5;
  const info = b & 31;
  let p = at + 1;
  let n = info;
  if (info === 24) n = bytes[p++];
  else if (info === 25) { n = (bytes[p] << 8) | bytes[p + 1]; p += 2; }
  else if (info === 26) { n = ((bytes[p] << 24) >>> 0) + ((bytes[p + 1] << 16) | (bytes[p + 2] << 8) | bytes[p + 3]); p += 4; }
  else if (info > 26) throw new Error("cbor");
  if (major === 0) return { v: n, at: p };
  if (major === 1) return { v: -1 - n, at: p };
  if (major === 2) return { v: bytes.slice(p, p + n), at: p + n };
  if (major === 3) return { v: new TextDecoder().decode(bytes.slice(p, p + n)), at: p + n };
  if (major === 4) {
    const list = [];
    for (let i = 0; i < n; i++) { const r = cbor(bytes, p); list.push(r.v); p = r.at; }
    return { v: list, at: p };
  }
  if (major === 5) {
    const map = new Map();
    for (let i = 0; i < n; i++) { const k = cbor(bytes, p); const v = cbor(bytes, k.at); map.set(k.v, v.v); p = v.at; }
    return { v: map, at: p };
  }
  if (major === 7 && (info === 20 || info === 21)) return { v: info === 21, at: p };
  throw new Error("cbor");
}

const pkSha = async (bytes) => new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
const pkSame = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);

/** DER ECDSA 서명 → WebCrypto가 받는 r||s(64바이트). */
export function derToRaw(der) {
  if (der[0] !== 0x30) throw new Error("der");
  let p = 2;
  const part = () => {
    if (der[p] !== 0x02) throw new Error("der");
    const len = der[p + 1];
    let v = der.slice(p + 2, p + 2 + len);
    p += 2 + len;
    while (v.length > 32 && v[0] === 0) v = v.slice(1);
    const out = new Uint8Array(32);
    out.set(v, 32 - v.length);
    return out;
  };
  const r = part();
  const s = part();
  const raw = new Uint8Array(64);
  raw.set(r);
  raw.set(s, 32);
  return raw;
}

/** authenticatorData의 앞부분: rpIdHash가 맞는지, 사용자가 눌렀는지(UP), 서명 횟수. */
async function pkAuthData(auth, rpId) {
  if (auth.length < 37) throw new Error("auth");
  if (!pkSame(auth.slice(0, 32), await pkSha(new TextEncoder().encode(rpId)))) throw new Error("rp");
  const flags = auth[32];
  if (!(flags & 0x01)) throw new Error("presence");
  const counter = ((auth[33] << 24) >>> 0) + ((auth[34] << 16) | (auth[35] << 8) | auth[36]);
  return { flags, counter };
}

async function pkTake(env, kind, challenge, now) {
  if (!/^[A-Za-z0-9_-]{20,64}$/.test(challenge ?? "")) return false;
  const key = `pk|${kind}|${challenge}`;
  const row = await one(env, "SELECT expires FROM counters WHERE key = ?", key);
  await run(env, "DELETE FROM counters WHERE key = ?", key);
  return Boolean(row && row.expires > now);
}

async function pkMake(env, kind, now) {
  const challenge = randomId(32);
  await run(env, "INSERT OR REPLACE INTO counters (key, count, expires) VALUES (?, 1, ?)", `pk|${kind}|${challenge}`, now + PASSKEY.ttlMs);
  return challenge;
}

async function pkClientData(text, type, origin, env, kind, now) {
  const raw = b64u.dec(text);
  const c = JSON.parse(new TextDecoder().decode(raw));
  if (c.type !== type) throw new Error("type");
  if (c.origin !== origin) throw new Error("origin");
  if (!(await pkTake(env, kind, c.challenge, now))) throw new Error("challenge");
  return raw;
}

/** 내 계정 › 패스키 목록. */
export async function passkeyList(env, actor) {
  const rows = await env.DB.prepare("SELECT id, name, created_at, last_at FROM admin_passkeys WHERE admin = ? ORDER BY created_at").bind(actor.id).all();
  return { items: (rows.results ?? []).map((r) => ({ id: r.id, name: r.name, createdAt: r.created_at, lastAt: r.last_at })), max: PASSKEY.perAdmin };
}

/** 로그인한 관리자: GET·POST /me/passkeys…. 등록 시작은 도전값과 이름, 등록은 공개 키를 남겨요. */
export async function passkeyMine(request, env, url, path, method, actor, now) {
  if (path === "/me/passkeys" && method === "GET") return json(await passkeyList(env, actor));
  if (path === "/me/passkeys/start" && method === "POST") {
    const mine = await passkeyList(env, actor);
    if (mine.items.length >= PASSKEY.perAdmin) return json({ error: "too_many" }, 409);
    return json({ challenge: await pkMake(env, `reg|${actor.id}`, now), rpId: url.hostname, user: { id: b64u.enc(new TextEncoder().encode(actor.id)), name: actor.email || "owner",
      displayName: actor.name ?? "관리자" }, exclude: mine.items.map((x) => x.id) });
  }
  if (path === "/me/passkeys" && method === "POST") {
    const body = await readJson(request, 8192);
    try {
      await pkClientData(body?.clientDataJSON, "webauthn.create", url.origin, env, `reg|${actor.id}`, now);
      const att = cbor(b64u.dec(body?.attestationObject)).v;
      const auth = att.get("authData");
      const { flags } = await pkAuthData(auth, url.hostname);
      if (!(flags & 0x40)) throw new Error("no_cred");
      const len = (auth[53] << 8) | auth[54];
      const id = b64u.enc(auth.slice(55, 55 + len));
      const key = cbor(auth, 55 + len).v;
      if (key.get(1) !== 2 || key.get(3) !== -7 || key.get(-1) !== 1) throw new Error("alg");
      const jwk = { kty: "EC", crv: "P-256", x: b64u.enc(key.get(-2)), y: b64u.enc(key.get(-3)) };
      await crypto.subtle.importKey("jwk", jwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
      const name = String(body?.name ?? "").replace(/[\u0000-\u001f]/g, " ").trim().slice(0, 40) || "이 기기";
      await run(env, "INSERT INTO admin_passkeys (id, admin, name, jwk, counter, created_at) VALUES (?, ?, ?, ?, 0, ?)", id, actor.id, name, JSON.stringify(jwk), now);
      return json({ ok: true, id });
    } catch (e) {
      return json({ error: "passkey", why: String(e?.message ?? e).slice(0, 40) }, 400);
    }
  }
  const m = path.match(/^\/me\/passkeys\/([A-Za-z0-9_-]{16,400})\/delete$/);
  if (m && method === "POST") {
    const r = await run(env, "DELETE FROM admin_passkeys WHERE id = ? AND admin = ?", m[1], actor.id);
    return r?.meta?.changes ? json({ ok: true }) : json({ error: "not_found" }, 404);
  }
  return null;
}

/** 로그인 전: POST /passkey/start → 도전값. */
export async function passkeyStart(env, url, now) {
  return json({ challenge: await pkMake(env, "login", now), rpId: url.hostname });
}

/**
 * 로그인 전: POST /passkey/login {id, clientDataJSON, authenticatorData, signature}. 맞으면 그 패스키의 관리자 id(주인은 'owner'), 아니면 null.
 * 서명 횟수가 줄면(복제된 열쇠) 받지 않아요(0만 쓰는 기기는 늘 0이라 괜찮아요).
 */
export async function passkeyCheck(request, env, url, now) {
  const body = await readJson(request, 8192);
  try {
    const row = await one(env, "SELECT * FROM admin_passkeys WHERE id = ?", String(body?.id ?? "").slice(0, 400));
    if (!row) return null;
    const cd = await pkClientData(body.clientDataJSON, "webauthn.get", url.origin, env, "login", now);
    const auth = b64u.dec(body.authenticatorData);
    const { counter } = await pkAuthData(auth, url.hostname);
    const key = await crypto.subtle.importKey("jwk", JSON.parse(row.jwk), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    const signed = new Uint8Array(auth.length + 32);
    signed.set(auth);
    signed.set(await pkSha(cd), auth.length);
    const ok = await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, derToRaw(b64u.dec(body.signature)), signed);
    if (!ok) return null;
    if (row.counter > 0 && counter <= row.counter) return null;
    await run(env, "UPDATE admin_passkeys SET counter = ?, last_at = ? WHERE id = ?", counter, now, row.id);
    return { admin: row.admin, name: row.name };
  } catch {
    return null;
  }
}
