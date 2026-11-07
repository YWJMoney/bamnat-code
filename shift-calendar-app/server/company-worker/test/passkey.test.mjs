// 1.38 [ADM3-01] 패스키(지문) 로그인: 진짜 P-256 열쇠로 등록·로그인을 끝까지, 그리고 틀린 서명·다른 주소·쓴 도전값·줄어든 횟수는 막혀요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn, ORIGIN } from "./helpers.mjs";
import { b64u, cbor, derToRaw } from "../src/passkey.js";

const enc = (o) => b64u.enc(new TextEncoder().encode(JSON.stringify(o)));
const sha = async (b) => new Uint8Array(await crypto.subtle.digest("SHA-256", b));
const RP = new URL(ORIGIN).hostname;

/** 아주 작은 CBOR 쓰개(시험용: 정수·바이트·글·맵). */
function cb(v) {
  const head = (major, n) => (n < 24 ? [(major << 5) | n] : n < 256 ? [(major << 5) | 24, n] : [(major << 5) | 25, n >> 8, n & 255]);
  if (typeof v === "number") return Uint8Array.from(v >= 0 ? head(0, v) : head(1, -1 - v));
  if (typeof v === "string") { const b = new TextEncoder().encode(v); return Uint8Array.from([...head(3, b.length), ...b]); }
  if (v instanceof Uint8Array) return Uint8Array.from([...head(2, v.length), ...v]);
  if (v instanceof Map) { const parts = [Uint8Array.from(head(5, v.size))]; for (const [k, x] of v) parts.push(cb(k), cb(x)); return Uint8Array.from(parts.flatMap((p) => [...p])); }
  throw new Error("cb");
}

async function authData(counter, credId = null, jwk = null) {
  const rp = await sha(new TextEncoder().encode(RP));
  const flags = credId ? 0x45 : 0x05;
  const c = [counter >>> 24, (counter >> 16) & 255, (counter >> 8) & 255, counter & 255];
  let extra = [];
  if (credId) {
    const cose = cb(new Map([[1, 2], [3, -7], [-1, 1], [-2, b64u.dec(jwk.x)], [-3, b64u.dec(jwk.y)]]));
    extra = [...new Uint8Array(16), credId.length >> 8, credId.length & 255, ...credId, ...cose];
  }
  return Uint8Array.from([...rp, flags, ...c, ...extra]);
}

/** WebCrypto의 r||s 서명을 DER로(브라우저 패스키가 주는 모양). */
function toDer(raw) {
  const int = (b) => { let i = 0; while (i < b.length - 1 && b[i] === 0) i++; let v = [...b.slice(i)]; if (v[0] & 0x80) v = [0, ...v]; return [0x02, v.length, ...v]; };
  const body = [...int(raw.slice(0, 32)), ...int(raw.slice(32))];
  return Uint8Array.from([0x30, body.length, ...body]);
}

test("[ADM3-01] 패스키: 등록하고 지문으로 들어와요, 틀리면 막혀요", async () => {
  const s = server();
  const owner = await signIn(s.call);
  const keys = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", keys.publicKey);
  const credId = crypto.getRandomValues(new Uint8Array(32));

  // 등록
  const start = (await s.call("POST", "/api/admin/me/passkeys/start", {}, owner)).data;
  assert.equal(start.rpId, RP);
  const att = cb(new Map([["fmt", "none"], ["attStmt", new Map()], ["authData", await authData(0, credId, jwk)]]));
  assert.equal(cbor(att).v.get("fmt"), "none");
  const reg = await s.call("POST", "/api/admin/me/passkeys", { name: "폴드", clientDataJSON: enc({ type: "webauthn.create", challenge: start.challenge, origin: ORIGIN }),
    attestationObject: b64u.enc(att) }, owner);
  assert.equal(reg.status, 200, reg.text);
  // 같은 도전값은 두 번 못 써요.
  const again = await s.call("POST", "/api/admin/me/passkeys", { clientDataJSON: enc({ type: "webauthn.create", challenge: start.challenge, origin: ORIGIN }), attestationObject: b64u.enc(att) }, owner);
  assert.equal(again.status, 400);
  const list = (await s.call("GET", "/api/admin/me/passkeys", undefined, owner)).data;
  assert.equal(list.items.length, 1);
  assert.equal(list.items[0].name, "폴드");

  const pub = { "x-admin": "1", origin: ORIGIN };
  async function assertion(counter, { origin = ORIGIN, key = keys.privateKey, id = b64u.enc(credId) } = {}) {
    const c = (await s.call("POST", "/api/admin/passkey/start", {}, pub)).data;
    const cd = new TextEncoder().encode(JSON.stringify({ type: "webauthn.get", challenge: c.challenge, origin }));
    const ad = await authData(counter);
    const signed = Uint8Array.from([...ad, ...(await sha(cd))]);
    const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, signed));
    assert.deepEqual(derToRaw(toDer(raw)), raw);
    return { id, clientDataJSON: b64u.enc(cd), authenticatorData: b64u.enc(ad), signature: b64u.enc(toDer(raw)) };
  }
  const ok = await s.call("POST", "/api/admin/passkey/login", await assertion(1), pub);
  assert.equal(ok.status, 200, ok.text);
  const cookie = ok.headers.get("set-cookie").split(";")[0];
  const me = await s.call("GET", "/api/admin/badges", undefined, { cookie });
  assert.equal(me.data.me.owner, true, "signed in as the owner");
  // 다른 열쇠의 서명, 다른 주소, 줄어든 횟수는 막혀요.
  const other = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign"]);
  assert.equal((await s.call("POST", "/api/admin/passkey/login", await assertion(2, { key: other.privateKey }), pub)).status, 401);
  assert.equal((await s.call("POST", "/api/admin/passkey/login", await assertion(3, { origin: "https://evil.example" }), pub)).status, 401);
  assert.equal((await s.call("POST", "/api/admin/passkey/login", await assertion(1), pub)).status, 401, "counter went back");
  assert.equal((await s.call("POST", "/api/admin/passkey/login", await assertion(5), pub)).status, 200);
  // 지우면 못 들어와요.
  assert.equal((await s.call("POST", `/api/admin/me/passkeys/${list.items[0].id}/delete`, {}, owner)).status, 200);
  assert.equal((await s.call("POST", "/api/admin/passkey/login", await assertion(6), pub)).status, 401);
});
