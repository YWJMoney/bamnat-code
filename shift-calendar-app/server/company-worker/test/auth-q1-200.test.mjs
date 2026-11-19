// 2.0.0 [RVQ-05~09] 검토 SRV-05·06·07·08·19·22(나), U21-03·04·05: 관리자 로그인·패스키·로그인 목록.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn, from, ORIGIN, PASSWORD } from "./helpers.mjs";
import { b64u } from "../src/passkey.js";

const APP = { "x-admin": "1", origin: ORIGIN };
const enc = (o) => b64u.enc(new TextEncoder().encode(JSON.stringify(o)));
const sha = async (b) => new Uint8Array(await crypto.subtle.digest("SHA-256", b));
const RP = new URL(ORIGIN).hostname;

function cb(v) {
  const head = (major, n) => (n < 24 ? [(major << 5) | n] : n < 256 ? [(major << 5) | 24, n] : [(major << 5) | 25, n >> 8, n & 255]);
  if (typeof v === "number") return Uint8Array.from(v >= 0 ? head(0, v) : head(1, -1 - v));
  if (typeof v === "string") { const b = new TextEncoder().encode(v); return Uint8Array.from([...head(3, b.length), ...b]); }
  if (v instanceof Uint8Array) return Uint8Array.from([...head(2, v.length), ...v]);
  if (v instanceof Map) { const parts = [Uint8Array.from(head(5, v.size))]; for (const [k, x] of v) parts.push(cb(k), cb(x)); return Uint8Array.from(parts.flatMap((p) => [...p])); }
  throw new Error("cb");
}

async function authData(flags, counter, credId = null, jwk = null) {
  const rp = await sha(new TextEncoder().encode(RP));
  const c = [counter >>> 24, (counter >> 16) & 255, (counter >> 8) & 255, counter & 255];
  let extra = [];
  if (credId) {
    const cose = cb(new Map([[1, 2], [3, -7], [-1, 1], [-2, b64u.dec(jwk.x)], [-3, b64u.dec(jwk.y)]]));
    extra = [...new Uint8Array(16), credId.length >> 8, credId.length & 255, ...credId, ...cose];
  }
  return Uint8Array.from([...rp, flags, ...c, ...extra]);
}

async function register(s, owner, { flags = 0x45, crossOrigin, password } = {}) {
  const keys = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const jwk = await crypto.subtle.exportKey("jwk", keys.publicKey);
  const credId = crypto.getRandomValues(new Uint8Array(32));
  const start = await s.call("POST", "/api/admin/me/passkeys/start", password === undefined ? {} : { password }, owner);
  if (start.status !== 200) return { start };
  const att = cb(new Map([["fmt", "none"], ["attStmt", new Map()], ["authData", await authData(flags, 0, credId, jwk)]]));
  const cd = { type: "webauthn.create", challenge: start.data.challenge, origin: ORIGIN, ...(crossOrigin === undefined ? {} : { crossOrigin }) };
  const reg = await s.call("POST", "/api/admin/me/passkeys", { name: "폴드", clientDataJSON: enc(cd), attestationObject: b64u.enc(att) },
    { ...owner, "user-agent": "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36" });
  return { start, reg, keys, credId };
}

function toDer(raw) {
  const int = (b) => { let i = 0; while (i < b.length - 1 && b[i] === 0) i++; let v = [...b.slice(i)]; if (v[0] & 0x80) v = [0, ...v]; return [0x02, v.length, ...v]; };
  const body = [...int(raw.slice(0, 32)), ...int(raw.slice(32))];
  return Uint8Array.from([0x30, body.length, ...body]);
}

async function assertion(s, keys, credId, counter, flags = 0x05) {
  const c = (await s.call("POST", "/api/admin/passkey/start", {}, APP)).data;
  const cd = new TextEncoder().encode(JSON.stringify({ type: "webauthn.get", challenge: c.challenge, origin: ORIGIN }));
  const ad = await authData(flags, counter);
  const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, keys.privateKey, Uint8Array.from([...ad, ...(await sha(cd))])));
  return { id: b64u.enc(credId), clientDataJSON: b64u.enc(cd), authenticatorData: b64u.enc(ad), signature: b64u.enc(toDer(raw)) };
}

test("[RVQ-06] SRV-05: a passkey without user verification (UV) or from another site's frame is refused, to make and to sign in", async () => {
  const s = server();
  const owner = await signIn(s.call);
  const noUv = await register(s, owner, { flags: 0x41 });
  assert.equal(noUv.reg.status, 400);
  assert.equal(noUv.reg.data.why, "verification");
  const framed = await register(s, owner, { crossOrigin: true });
  assert.equal(framed.reg.status, 400);
  assert.equal(framed.reg.data.why, "cross_origin");
  const ok = await register(s, owner);
  assert.equal(ok.reg.status, 200, ok.reg.text);
  // 로그인: UP만 있고 UV가 없는 서명은 401.
  assert.equal((await s.call("POST", "/api/admin/passkey/login", await assertion(s, ok.keys, ok.credId, 1, 0x01), APP)).status, 401);
  assert.equal((await s.call("POST", "/api/admin/passkey/login", await assertion(s, ok.keys, ok.credId, 2), APP)).status, 200);
});

test("[RVQ-06] SRV-06·U21-03: adding a passkey needs a recent sign-in or the password, and both adding and removing are logged", async () => {
  const s = server();
  const owner = await signIn(s.call);
  const first = await register(s, owner);
  assert.equal(first.reg.status, 200);
  // 로그인한 지 10분이 지나면 비밀번호를 한 번 더.
  await s.DB.prepare("UPDATE admin_sessions SET created_at = created_at - 11 * 60 * 1000").run();
  assert.equal((await s.call("GET", "/api/admin/me/passkeys", undefined, owner)).data.needPassword, true);
  assert.equal((await register(s, owner)).start.status, 401);
  assert.equal((await register(s, owner, { password: "not-the-password" })).start.data.error, "reauth");
  const second = await register(s, owner, { password: PASSWORD });
  assert.equal(second.reg.status, 200, second.reg.text);
  const list = (await s.call("GET", "/api/admin/me/passkeys", undefined, owner)).data;
  assert.equal(list.items.length, 2);
  assert.equal(list.items[1].lastDevice, "Chrome · Android");
  assert.equal((await s.call("POST", `/api/admin/me/passkeys/${list.items[0].id}/delete`, {}, owner)).status, 200);
  const log = await s.DB.prepare("SELECT action, detail FROM audit WHERE action LIKE 'passkey_%' OR (action = 'login_failed' AND detail = '') ORDER BY id").all();
  assert.deepEqual(log.results.filter((r) => r.action.startsWith("passkey_")).map((r) => r.action), ["passkey_add", "passkey_add", "passkey_remove"]);
  assert.ok((await s.DB.prepare("SELECT COUNT(*) AS n FROM audit WHERE action = 'login_failed' AND target = 'reauth'").first()).n >= 1);
});

test("[RVQ-05] SRV-08 (나): wrong passwords sent all at once still lock at five", async () => {
  const s = server();
  const got = await Promise.all(Array.from({ length: 12 }, () => s.call("POST", "/api/admin/login", { password: "wrong-wrong-wrong" }, { ...APP, ...from(70) })));
  const wrong = got.filter((r) => r.status === 401).length;
  assert.ok(wrong <= 5, `checked ${wrong} passwords`);
  assert.equal(got.filter((r) => r.status === 429).length, 12 - wrong);
  // 맞는 비밀번호도 잠긴 곳에서는 안 돼요.
  assert.equal((await s.call("POST", "/api/admin/login", { password: PASSWORD }, { ...APP, ...from(70) })).status, 429);
  // 다른 곳에서는 들어와요.
  assert.equal((await s.call("POST", "/api/admin/login", { password: PASSWORD }, { ...APP, ...from(71) })).status, 200);
});

test("[RVQ-05] SRV-08 (다): a wrong second-step code counts toward the hour's total too", async () => {
  const s = server();
  const owner = await signIn(s.call);
  await s.DB.prepare("INSERT INTO admin_totp (admin, secret, on_at, used_step, created_at) VALUES ('owner', 'JBSWY3DPEHPK3PXP', 1, -1, 1)").run();
  const r = await s.call("POST", "/api/admin/login", { password: PASSWORD, code: "000000" }, { ...APP, ...from(72) });
  assert.equal(r.data.error, "code_wrong");
  assert.equal((await s.DB.prepare("SELECT count FROM counters WHERE key = 'login|all'").first())?.count, 1);
  assert.ok(owner.cookie);
});

test("[RVQ-07] SRV-07: changing the owner's password ends the sign-ins made with the old one", async () => {
  const s = server();
  const owner = await signIn(s.call);
  assert.equal((await s.call("GET", "/api/admin/me", undefined, owner)).status, 200);
  s.env.ADMIN_PASSWORD = "a-brand-new-owner-password";
  assert.equal((await s.call("GET", "/api/admin/me", undefined, owner)).status, 401);
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM admin_sessions").first()).n, 0);
  // 이 판 전에 연 로그인(표시 없음)은 처음 쓸 때 표시를 받아요.
  const again = await s.call("POST", "/api/admin/login", { password: "a-brand-new-owner-password" }, APP);
  const cookie = again.headers.get("set-cookie").split(";")[0];
  await s.DB.prepare("UPDATE admin_sessions SET pw = ''").run();
  assert.equal((await s.call("GET", "/api/admin/me", undefined, { ...APP, cookie })).status, 200);
  assert.notEqual((await s.DB.prepare("SELECT pw FROM admin_sessions").first()).pw, "");
  // 표시는 비밀번호도, 비밀번호의 그냥 해시도 아니에요.
  assert.ok(!s.DB.dump().includes("a-brand-new-owner-password"));
});

test("[RVQ-07] U21-04: my sign-ins, one by one, with the device as two words", async () => {
  const s = server();
  const phone = await s.call("POST", "/api/admin/login", { password: PASSWORD }, { ...APP, "user-agent": "Mozilla/5.0 (Linux; Android 16; SM-F966N) SamsungBrowser/28.0 Chrome/130 Mobile Safari/537.36" });
  const pc = await s.call("POST", "/api/admin/login", { password: PASSWORD }, { ...APP, "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36 Edg/140.0" });
  const p = { ...APP, cookie: phone.headers.get("set-cookie").split(";")[0] };
  const c = { ...APP, cookie: pc.headers.get("set-cookie").split(";")[0] };
  const list = (await s.call("GET", "/api/admin/me/sessions", undefined, p)).data.items;
  assert.equal(list.length, 2);
  assert.equal(list[0].current, true);
  assert.equal(list[0].device, "삼성 인터넷 · Android");
  assert.equal(list[1].device, "Edge · Windows");
  assert.equal(list[1].via, "password");
  assert.ok(!s.DB.dump().includes("SM-F966N"), "the full user agent is not kept");
  // 지금 이 로그인은 여기서 못 끊어요(로그아웃으로). 다른 것은 끊겨요.
  assert.equal((await s.call("POST", `/api/admin/me/sessions/${list[0].id}/end`, {}, p)).status, 409);
  const after = await s.call("POST", `/api/admin/me/sessions/${list[1].id}/end`, {}, p);
  assert.equal(after.data.items.length, 1);
  assert.equal((await s.call("GET", "/api/admin/me", undefined, c)).status, 401);
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM audit WHERE action = 'session_end'").first()).n, 1);
});

test("[RVQ-08] SRV-19: an old 20,000-round hash is made again at 100,000 when the admin signs in", async () => {
  const s = server();
  const owner = await signIn(s.call);
  const email = "rounds@example.com";
  const inv = await s.call("POST", "/api/admin/invites", { email, perms: {} }, owner);
  const pw = "rounds-test-password-1";
  assert.equal((await s.call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "회차", password: pw }, APP)).status, 201);
  const id = (await s.DB.prepare("SELECT id, pw FROM admins WHERE email = ?").bind(email).first()).id;
  assert.equal((await s.call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner)).status, 200);
  assert.match((await s.DB.prepare("SELECT pw FROM admins WHERE id = ?").bind(id).first()).pw, /^pbkdf2\$100000\$/);
  // 2만 번으로 만든 해시(1.39까지)로 바꿔 두고 로그인.
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(pw), "PBKDF2", false, ["deriveBits"]);
  const bits = new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: 20000 }, key, 256));
  const b64 = (b) => btoa(String.fromCharCode(...b));
  await s.DB.prepare("UPDATE admins SET pw = ? WHERE id = ?").bind(`pbkdf2$20000$${b64(salt)}$${b64(bits)}`, id).run();
  const r = await s.call("POST", "/api/admin/login", { email, password: pw }, { ...APP, ...from(80) });
  assert.equal(r.status, 200, r.text);
  assert.match((await s.DB.prepare("SELECT pw FROM admins WHERE id = ?").bind(id).first()).pw, /^pbkdf2\$100000\$/);
  assert.equal((await s.call("POST", "/api/admin/login", { email, password: pw }, { ...APP, ...from(81) })).status, 200);
});

test("[RVQ-09] SRV-22 (나): removing an admin removes their passkeys and second step too", async () => {
  const s = server();
  const owner = await signIn(s.call);
  const inv = await s.call("POST", "/api/admin/invites", { email: "gone@example.com", perms: {} }, owner);
  await s.call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "떠남", password: "gone-password-123" }, APP);
  const id = (await s.DB.prepare("SELECT id FROM admins WHERE email = 'gone@example.com'").first()).id;
  await s.DB.prepare("INSERT INTO admin_passkeys (id, admin, name, jwk, created_at) VALUES ('pk-gone-000000000000', ?, '폰', '{}', 1)").bind(id).run();
  await s.DB.prepare("INSERT INTO admin_totp (admin, secret, on_at, used_step, created_at) VALUES (?, 'S', 1, -1, 1)").bind(id).run();
  assert.equal((await s.call("POST", `/api/admin/admins/${id}/remove`, {}, owner)).status, 200);
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM admin_passkeys WHERE admin = ?").bind(id).first()).n, 0);
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM admin_totp WHERE admin = ?").bind(id).first()).n, 0);
});

test("[RVQ-05] U21-05: the owner signing in with only a password is red on 오늘, and a left-on 2FA reset too", async () => {
  const s = server();
  const owner = await signIn(s.call);
  const alerts = async () => ((await s.call("GET", "/api/admin/today", undefined, owner)).data.alerts ?? []).map((a) => a.key);
  assert.ok((await alerts()).includes("owner:single"));
  await s.DB.prepare("INSERT INTO admin_totp (admin, secret, on_at, used_step, created_at) VALUES ('owner', 'S', 1, -1, 1)").run();
  const now = await alerts();
  assert.ok(!now.includes("owner:single"));
  assert.ok(now.includes("passkey:none"));
  s.env.ADMIN_2FA_RESET = "1";
  assert.ok((await alerts()).includes("owner:2fa-reset"));
});
