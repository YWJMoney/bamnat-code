// R131 1.31 (사용자 2026-10-28: "도메인 제외 모든 문제점 개선점 혁신점 수용"): the server's half of the 1.30 detailed review
// (계획/1.30-상세리뷰-보고서.md R1~R8, I3·I5~I9, N1·N4·N6). Companies and phones are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, device, from } from "./helpers.mjs";

const ip = (i) => ({ "cf-connecting-ip": `198.51.${Math.floor(i / 200)}.${i % 200}` });

test("[SRCH-15] the very company typed is found when more than 200 names hold the same words", async () => {
  const { call } = server();
  for (let i = 1; i <= 205; i++) assert.ok((await call("POST", "/api/reports", report(1000 + i, { company: `가상화학${i}` }), ip(i))).status < 300);
  await call("POST", "/api/reports", report(5000, { company: "가상화학" }), ip(990));
  const s = await call("GET", `/api/companies?q=${encodeURIComponent("가상화학")}`);
  assert.equal(s.data.companies[0].name, "가상화학");
  assert.equal(s.data.companies.length, 20);
});

test("[SRCH-16] three letters or more are found through the FTS5 index, with renames and other names kept in it", async () => {
  const { call, DB } = server();
  await call("POST", "/api/reports", report(1, { company: "가상정밀공업" }), from(1));
  await call("POST", "/api/reports", report(2, { company: "다른회사" }), from(2));
  const n = DB.db.prepare("SELECT COUNT(*) AS n FROM company_fts WHERE company_fts MATCH ?").get('"정밀공"').n;
  assert.equal(n, 1);
  assert.deepEqual((await call("GET", `/api/companies?q=${encodeURIComponent("정밀공업")}`)).data.companies.map((c) => c.name), ["가상정밀공업"]);
  // Two letters read through, as before.
  assert.deepEqual((await call("GET", `/api/companies?q=${encodeURIComponent("정밀")}`)).data.companies.map((c) => c.name), ["가상정밀공업"]);
  // An other name added later is in the index at once (trigger).
  const id = DB.db.prepare("SELECT id FROM companies WHERE name = '가상정밀공업'").get().id;
  DB.db.prepare("INSERT INTO aliases (norm, company_id, name, cho, said) VALUES ('별명테스트', ?, '별명테스트', '', '')").run(id);
  assert.deepEqual((await call("GET", `/api/companies?q=${encodeURIComponent("별명테스")}`)).data.companies.map((c) => c.id), [id]);
});

test("[SRCH-16] without the index the search still answers", async () => {
  const { call, DB } = server();
  await call("POST", "/api/reports", report(1, { company: "가상정밀공업" }), from(1));
  // As a database where the FTS batch failed whole: no table and no triggers.
  for (const t of ["ci", "cu", "cd", "ai", "au", "ad"]) DB.db.exec(`DROP TRIGGER company_fts_${t}`);
  DB.db.exec("DROP TABLE company_fts");
  assert.equal((await call("GET", `/api/companies?q=${encodeURIComponent("정밀공업")}`)).data.companies.length, 1);
});

test("[LIM-01][LIM-02] one plant's Wi-Fi: 40 phones join a company the same day, and a refusal says why and when", async () => {
  const { call } = server();
  const a = (await call("POST", "/api/reports", report(1, { company: "가상병원" }), from(1))).data;
  for (let i = 0; i < 40; i++) {
    const r = await call("POST", "/api/members", { device: device(100 + i), company: a.company.id, cal: "main" }, from(7));
    assert.equal(r.status, 200, `${i}: ${r.text}`);
  }
  // One phone joining again and again is its own limit, not the network's.
  let last = null;
  for (let i = 0; i < 11; i++) last = await call("POST", "/api/members", { device: device(500), company: a.company.id, cal: "main" }, from(8));
  assert.equal(last.status, 429);
  assert.equal(last.data.why, "device");
  assert.ok(last.data.retryAfter > 0 && last.data.retryAfter <= 86400);
});

test("[SPD-21] the names part keeps its ETag while only counts change; counts have their own", async () => {
  const { call, env } = server();
  const a = (await call("POST", "/api/reports", report(1, { company: "가상화학" }), from(1))).data;
  const names = await call("GET", "/api/companies/all?part=names");
  assert.equal(names.data.part, "names");
  assert.deepEqual(Object.keys(names.data.companies[0]).sort(), ["aliases", "cho", "id", "name", "said"]);
  const counts = await call("GET", "/api/companies/counts");
  assert.equal(counts.data.counts[0].id, a.company.id);
  assert.ok("users" in counts.data.counts[0] && !("name" in counts.data.counts[0]));
  // A new user of the company: counts change, names do not.
  await call("POST", "/api/uses", { device: device(77), company: a.company.id, setup: a.setup, cal: "main" }, from(77));
  const later = Date.now() + 301_000;
  const realNow = Date.now;
  Date.now = () => later;
  try {
    const n2 = await call("GET", "/api/companies/all?part=names", undefined, { "if-none-match": names.headers.get("etag") });
    assert.equal(n2.status, 304);
    const c2 = await call("GET", "/api/companies/counts", undefined, { "if-none-match": counts.headers.get("etag") });
    assert.equal(c2.status, 200);
    assert.equal(c2.data.counts[0].users, counts.data.counts[0].users + 1);
  } finally {
    Date.now = realNow;
  }
  assert.ok(env);
});

test("[SPD-22] asks that come together while the whole list is read share one read", async () => {
  const { call, DB, env } = server();
  await call("POST", "/api/reports", report(1, { company: "가상화학" }), from(1));
  let reads = 0;
  const prepare = DB.prepare.bind(DB);
  env.DB = { ...DB, prepare: (sql) => { if (/FROM companies c WHERE c.hidden = 0 AND c.merged_into IS NULL AND c.kind/.test(sql)) reads++; return prepare(sql); } };
  const later = Date.now() + 301_000;
  const realNow = Date.now;
  Date.now = () => later;
  try {
    await Promise.all([1, 2, 3, 4].map((i) => call("GET", "/api/companies/all", undefined, from(10 + i))));
  } finally {
    Date.now = realNow;
  }
  assert.equal(reads, 1);
});

test("[SPD-23] a company's page and the plaza's lists answer 304 when the phone has them", async () => {
  const { call } = server();
  const a = (await call("POST", "/api/reports", report(1, { company: "가상화학" }), from(1))).data;
  for (const path of [`/api/companies/${a.company.id}`, "/api/board?tab=voting", "/api/terms", "/api/plazas"]) {
    const first = await call("GET", path);
    assert.equal(first.status, 200, path);
    const tag = first.headers.get("etag");
    assert.ok(tag, path);
    const again = await call("GET", path, undefined, { "if-none-match": tag });
    assert.equal(again.status, 304, path);
    assert.equal(again.text, "");
  }
});

// R131 1.31 [STAT-08] N4: lookup times in the usage bundle, only from 처리방침 3.5's date (the stats.lookup row's).
const lookBundle = (lookups, day = "2026-11-27", opens = 1) => ({ v: 1, app: "1.31.0", days: [{ day, events: { open: opens }, recent: [] }], lookups });

test("[STAT-08] lookup rows take only bucket edges of known kinds and steps", async () => {
  const { lookupRows } = await import("../src/index.js");
  assert.deepEqual(lookupRows({ wait: { find: 700, company: 1234, plaza: 2000, other: 100 }, call: { search: 300, admin: 100 }, cold: { firstMonth: 100 } }),
    [["wait", "find", 700], ["wait", "plaza", 2000], ["call", "search", 300]]);
  assert.deepEqual(lookupRows(null), []);
  assert.deepEqual(lookupRows([1]), []);
});

test("[STAT-08] lookup times are left out before 3.5 is in force and counted per bucket after, read back in 지표", async (t) => {
  const { signIn } = await import("./helpers.mjs");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-27T03:00:00Z") });
  const { call, DB } = server({ plaza: true });
  const parts = { wait: { find: 700, company: 1500 }, call: { search: 300, company: 500 } };
  assert.equal((await call("POST", "/api/events", lookBundle(parts, "2026-11-26"), from(1))).status, 204);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_startup WHERE kind IN ('wait', 'call')").get().n, 0, "before 3.5");
  t.mock.timers.setTime(Date.parse("2026-11-28T03:00:00Z"));
  for (let i = 0; i < 3; i++) assert.equal((await call("POST", "/api/events", lookBundle(parts, "2026-11-27", 1 + i), from(10 + i))).status, 204);
  const rows = DB.db.prepare("SELECT kind, step, bucket, phones FROM usage_startup WHERE kind IN ('wait', 'call') ORDER BY kind, step").all().map((r) => ({ ...r }));
  assert.deepEqual(rows, [
    { kind: "call", step: "company", bucket: 500, phones: 3 },
    { kind: "call", step: "search", bucket: 300, phones: 3 },
    { kind: "wait", step: "company", bucket: 1500, phones: 3 },
    { kind: "wait", step: "find", bucket: 700, phones: 3 },
  ]);
  const admin = await signIn(call);
  const v = (await call("GET", "/api/admin/metrics?days=7", undefined, admin)).data.startup.versions.find((x) => x.app === "1.31.0");
  assert.deepEqual(v.wait.find, { phones: 3, p50: 700, p90: 700 });
  assert.deepEqual(v.call.search, { phones: 3, p50: 300, p90: 300 });
});

test("[SPD-26] /api/hello answers empty at once, for the app to open its connection", async () => {
  const { call } = server();
  const r = await call("GET", "/api/hello");
  assert.equal(r.status, 204);
});

// R131 1.31 [ADMX-13] R8: the badges' 15-second memo stays through the phones' own writes (usage counts here) and goes on a
// write the badges count.
test("[ADMX-13] usage counts leave the badges' memo alone; an admin's write renews it", async () => {
  const { signIn } = await import("./helpers.mjs");
  const { call, DB } = server();
  const owner = await signIn(call);
  const before = (await call("GET", "/api/admin/badges", undefined, owner)).data.inquiries;
  const at = Date.now();
  DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, 'h1', 'bug', '{}', 'open', ?, ?)").run("q".padEnd(22, "1"), at, at);
  DB.db.prepare("INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', '알람이 늦어요', NULL, ?)").run("q".padEnd(22, "1"), at);
  assert.equal((await call("POST", "/api/events", lookBundle({}, new Date(at + 9 * 3600e3).toISOString().slice(0, 10)), from(3))).status, 204);
  assert.equal((await call("GET", "/api/admin/badges", undefined, owner)).data.inquiries, before, "a usage count is not a badge write");
  await call("POST", "/api/admin/me/push", { kinds: [] }, owner);
  assert.equal((await call("GET", "/api/admin/badges", undefined, owner)).data.inquiries, before + 1);
});

// R131 1.31 [ADMX-14] N6 하루 3분 정리: at 9 KST one push per admin who asked for it, with what waits, once a day.
test("[ADMX-14] the morning push says what waits, opens 큐 모드, and goes once a day at 9 KST", async (t) => {
  const { signIn } = await import("./helpers.mjs");
  const { digestTick, digestText } = await import("../src/index.js");
  assert.equal(digestText({ inquiries: 0, pending: 0, flaggedPosts: 0 }), null);
  assert.equal(digestText({ inquiries: 2, pending: 1 }), "답할 문의 2 · 정할 판 1. 눌러서 하나씩 끝내요");
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const b64u = (bytes) => Buffer.from(bytes).toString("base64url");
  const s = server({ VAPID_PUBLIC_KEY: b64u(new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey))),
    VAPID_PRIVATE_KEY: (await crypto.subtle.exportKey("jwk", pair.privateKey)).d, VAPID_SUBJECT: "mailto:ops@bamnat.example" });
  const owner = await signIn(s.call);
  assert.ok((await s.call("GET", "/api/admin/me/push", undefined, owner)).data.kinds.some((k) => k.key === "daily"));
  const ua = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const sub = { endpoint: "https://fcm.googleapis.com/fcm/send/r131-1", keys: { p256dh: b64u(new Uint8Array(await crypto.subtle.exportKey("raw", ua.publicKey))), auth: b64u(crypto.getRandomValues(new Uint8Array(16))) } };
  assert.equal((await s.call("POST", "/api/admin/me/push", { subscription: sub, kinds: ["daily"] }, owner)).status, 200);
  const at = Date.now();
  s.DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, 'h2', 'bug', '{}', 'open', ?, ?)").run("q".padEnd(22, "2"), at, at);
  s.DB.db.prepare("INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', '급여가 달라요', NULL, ?)").run("q".padEnd(22, "2"), at);
  const sent = [];
  t.mock.method(globalThis, "fetch", async (url) => (sent.push(String(url)), new Response(null, { status: 201 })));
  const nine = Date.parse("2026-11-02T00:10:00Z"); // 09:10 KST
  assert.equal((await digestTick(s.env, nine - 3600e3)).sent, 0, "08시 KST: not yet");
  assert.equal((await digestTick(s.env, nine)).sent, 1);
  assert.deepEqual(sent, [sub.endpoint]);
  assert.equal((await digestTick(s.env, nine + 60e3)).sent, 0, "once a day");
});

// R131 1.31 [ADMX-16] I9 두 번째 확인: RFC 6238's own example, then turning it on, a login that needs the code, no code twice.
test("[ADMX-16] TOTP codes are the RFC's; with 두 번째 확인 on a right password still needs the code, used once", async (t) => {
  const { signIn, PASSWORD, ORIGIN } = await import("./helpers.mjs");
  const { totpCode, totpBase32 } = await import("../src/totp.js");
  const rfc = totpBase32(new TextEncoder().encode("12345678901234567890"));
  assert.equal(rfc, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
  assert.equal(await totpCode(rfc, 1), "287082"); // RFC 6238 B: T = 59 s, 94287082
  assert.equal(await totpCode(rfc, Math.floor(1111111109 / 30)), "081804"); // 07081804
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-02T01:00:00Z") });
  const { call } = server();
  const owner = await signIn(call);
  const started = (await call("POST", "/api/admin/me/2fa", { do: "start" }, owner)).data;
  assert.match(started.uri, /^otpauth:\/\/totp\//);
  const step = () => Math.floor(Date.now() / 30000);
  const wrong = (await totpCode(started.secret, step())) === "000000" ? "111111" : "000000";
  assert.equal((await call("POST", "/api/admin/me/2fa", { do: "confirm", code: wrong }, owner)).status, 400, "a wrong code does not turn it on");
  const on = await call("POST", "/api/admin/me/2fa", { do: "confirm", code: await totpCode(started.secret, step()) }, owner);
  assert.equal(on.status, 200, on.text);
  assert.equal(on.data.on, true);
  const APP = { "x-admin": "1", origin: ORIGIN };
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD }, APP)).data.error, "code_needed");
  t.mock.timers.setTime(Date.now() + 30000);
  const code = await totpCode(started.secret, step());
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD, code }, APP)).status, 200);
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD, code }, APP)).data.error, "code_wrong", "the same code twice");
});
