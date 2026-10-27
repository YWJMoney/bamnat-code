// AM 1.28 [FLAG-20]~[FLAG-33] 기능 관리(다크머지): the catalog, /api/config for old and new apps, moving the 1.27 switches,
// the launch day written once, who may force what (결정 D97), checks on what is sent, the hourly job's news (D99) and
// which flags phones had on: `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";
import worker, { FLAG_CATALOG, FLAG_KEYS } from "../src/index.js";
import { adminLib } from "../src/admin-lib.js";

const APP = { "x-admin": "1", origin: ORIGIN };
const DAY = 24 * 60 * 60 * 1000;
const NOW = Date.parse("2026-10-26T01:00:00Z");
const L = adminLib();

/** 통합계획 2-1: the keys the app's core FlagCatalog has too, stats.more the integrator added, and photo.wrong (AQ 1.29). */
const CONTRACT = ["billing", "ads", "type.shift", "type.day", "type.hourly", "type.field", "type.business", "type.invest", "leave.hourly",
  "plaza.write", "company.report", "inquiry.photos", "share.create", "usage.stats", "plaza.news", "stats.more", "photo.wrong" /* AQ 1.29 */];

const MANAGER = { review: "edit", inquiries: "edit", dev: "edit", devStatus: "edit", companies: "edit", rewards: "edit", settings: "edit", community: "edit",
  takedown: "edit", ops: "edit", metrics: "view", shares: "edit", audit: "view", flags: "edit", flagOff: "none" };

/** An admin the owner invited with [perms] and approved, signed in. */
async function member(call, owner, perms = MANAGER, email = "minji@example.com", ip = 40) {
  const inv = await call("POST", "/api/admin/invites", { email, perms }, owner);
  await call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "민지", password: "minji-flags-2026" }, { ...APP, ...from(ip) });
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: "minji-flags-2026" }, { ...APP, ...from(ip) });
  return { id, headers: { cookie: res.headers.get("set-cookie").split(";")[0], ...APP } };
}

const cron = async (env, now) => {
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
};

test("[FLAG-20] the server's catalog has the contract's keys, each with its words, and legal rows wait on a policy", () => {
  assert.deepEqual([...FLAG_KEYS].sort(), [...CONTRACT].sort());
  for (const f of FLAG_CATALOG) {
    for (const field of ["name", "what", "whyOff", "owner", "since"]) assert.ok(f[field], `${f.key} ${field}`);
    assert.ok(f.basis && Object.values(f.basis).some(Boolean), `${f.key} basis`);
    assert.ok(["NORMAL", "LEGAL"].includes(f.risk));
    assert.deepEqual(L.checkConditions(f.conditions).list, f.conditions, `${f.key} conditions are what the API accepts`);
    if (f.risk === "LEGAL") assert.ok(f.conditions.some((c) => c.type === "policy" || (c.type === "date" && c.policy)), `${f.key} policy`);
  }
  // The two that replace 정식 출시 wait on the 3.1 effective day, the 3.1 hold and Play (core FlagCatalog.launch).
  const billing = FLAG_CATALOG.find((f) => f.key === "billing");
  assert.deepEqual(billing.conditions.map((c) => c.type), ["date", "policy", "manual", "manual"]);
  assert.equal(FLAG_CATALOG.find((f) => f.key === "leave.hourly").conditions[0].from, "2027-06-10");
});

test("[FLAG-22] /api/config: 1.27 apps read the same fields; 1.28 apps read flags, policy, serverTime and what was confirmed", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const cfg = (await call("GET", "/api/config")).data;
  assert.equal(cfg.billingLive, false);
  assert.deepEqual([cfg.freeFeatures, cfg.off, cfg.launchedAt], [[], [], null]);
  assert.equal(cfg.serverTime, new Date(NOW).toISOString());
  // Only what the server overrides: the flags waiting on a confirmation (unconfirmed yet), nothing for the rest.
  assert.deepEqual(Object.keys(cfg.flags).sort(), ["ads", "billing"]);
  assert.deepEqual(cfg.flags.billing, { force: null, why: "", conditions: null, until: null, minApp: null, maxApp: null, confirmed: {} });
  assert.deepEqual(cfg.policy.find((p) => p.version === "3.2"), { version: "3.2", posted: "2026-10-28", effective: "2026-11-27", heldUntil: null });

  const owner = await signIn(call);
  assert.equal((await call("POST", "/api/admin/checks/play.production", { done: true, note: "콘솔에서 봄" }, owner)).status, 200);
  await call("POST", "/api/admin/flags/plaza.write/force", { force: "off", why: "도배", until: "2026-10-28" }, owner);
  await call("POST", "/api/admin/flags/free.MONEY_STATS/force", { force: "on", why: "돈 통계 모두에게" }, owner);
  const after = (await call("GET", "/api/config")).data;
  assert.deepEqual(Object.keys(after.flags.billing.confirmed), ["play.production"]);
  assert.deepEqual(after.flags["plaza.write"], { force: "off", why: "도배", conditions: null, until: "2026-10-27T15:00:00.000Z", minApp: null, maxApp: null, confirmed: {} });
  assert.equal(after.flags["free.MONEY_STATS"].force, "on");
  assert.deepEqual([after.off, after.freeFeatures], [["PLAZA_WRITE"], ["MONEY_STATS"]]);
  // The server stops taking what is forced off (the old switch), and the force ends by itself.
  t.mock.timers.tick(2 * DAY);
  assert.deepEqual((await call("GET", "/api/config")).data.off, []);
});

test("[FLAG-23] the 1.27 settings become overrides once: 정식 출시 on, 무료로 연 기능, switches off and the hourly job's rests", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  DB.db.exec("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  const put = DB.db.prepare("INSERT INTO settings (key, value) VALUES (?, ?)");
  put.run("billing_live", "1");
  put.run("free_features", JSON.stringify(["MONEY_STATS", "not a feature"]));
  put.run("switches_off", JSON.stringify(["PLAZA_WRITE", "USAGE_STATS"]));
  put.run("auto_rested", JSON.stringify([{ key: "USAGE_STATS", since: NOW - DAY }]));
  const cfg = (await call("GET", "/api/config")).data;
  assert.equal(cfg.billingLive, true);
  assert.deepEqual(cfg.freeFeatures, ["MONEY_STATS"]);
  assert.deepEqual([...cfg.off].sort(), ["PLAZA_WRITE", "USAGE_STATS"]);
  const rows = DB.db.prepare("SELECT key, force, auto FROM flag_overrides ORDER BY key").all().map((r) => ({ ...r }));
  assert.deepEqual(rows, [
    { key: "ads", force: "on", auto: 0 }, { key: "billing", force: "on", auto: 0 }, { key: "free.MONEY_STATS", force: "on", auto: 0 },
    { key: "plaza.write", force: "off", auto: 0 }, { key: "usage.stats", force: "off", auto: 1 },
  ]);
  // The launch day is written by the first answer that finds billing on.
  assert.equal(cfg.launchedAt, new Date(NOW).toISOString());
  // Again: nothing more (the guard row), even with the old rows still there.
  await call("GET", "/api/config");
  const owner = await signIn(call);
  await call("POST", "/api/admin/flags/plaza.write/force", { force: null }, owner);
  assert.deepEqual((await call("GET", "/api/config")).data.off, ["USAGE_STATS"]);
  assert.ok(DB.db.prepare("SELECT value FROM settings WHERE key = 'flags_migrated'").get().value);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM audit WHERE action = 'flag_migrate'").get().n, 1);
});

// ---- web push, copied from admin-server.test.mjs ----

const b64u = (bytes) => Buffer.from(bytes).toString("base64url");

async function vapidKeys() {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const pub = new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey));
  const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
  return { VAPID_PUBLIC_KEY: b64u(pub), VAPID_PRIVATE_KEY: jwk.d, VAPID_SUBJECT: "mailto:ops@bamnat.example" };
}

async function browser(n = 1) {
  const pair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const ua = new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey));
  const auth = crypto.getRandomValues(new Uint8Array(16));
  const hmac = async (key, data) => new Uint8Array(await crypto.subtle.sign("HMAC", await crypto.subtle.importKey("raw", key, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]), data));
  const cat = (...p) => Uint8Array.from(p.flatMap((x) => [...x]));
  const te = new TextEncoder();
  return {
    subscription: { endpoint: `https://fcm.googleapis.com/fcm/send/browser-${n}`, keys: { p256dh: b64u(ua), auth: b64u(auth) } },
    async read(body) {
      const bytes = new Uint8Array(body);
      const salt = bytes.slice(0, 16);
      const idlen = bytes[20];
      const as = bytes.slice(21, 21 + idlen);
      const sealed = bytes.slice(21 + idlen);
      const theirs = await crypto.subtle.importKey("raw", as, { name: "ECDH", namedCurve: "P-256" }, false, []);
      const shared = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: theirs }, pair.privateKey, 256));
      const ikm = await hmac(await hmac(auth, shared), cat(te.encode("WebPush: info\0"), ua, as, [1]));
      const prk = await hmac(salt, ikm);
      const cek = (await hmac(prk, cat(te.encode("Content-Encoding: aes128gcm\0"), [1]))).slice(0, 16);
      const nonce = (await hmac(prk, cat(te.encode("Content-Encoding: nonce\0"), [1]))).slice(0, 12);
      const key = await crypto.subtle.importKey("raw", cek, "AES-GCM", false, ["decrypt"]);
      const plain = new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: nonce }, key, sealed));
      return JSON.parse(new TextDecoder().decode(plain.slice(0, -1)));
    },
  };
}

test("[FLAG-25][FLAG-28] the day before a legal flag turns on by itself admins hear of it; at midnight it turns on, the launch is written once", async (t) => {
  const eve = Date.parse("2026-11-09T14:00:00Z"); // 11월 9일 23시(한국)
  t.mock.timers.enable({ apis: ["Date"], now: eve });
  const { call, env } = server(await vapidKeys());
  const owner = await signIn(call);
  const b = await browser(1);
  assert.equal((await call("POST", "/api/admin/me/push", { subscription: b.subscription, kinds: ["flags"] }, owner)).data.ready, true);
  const sent = [];
  t.mock.method(globalThis, "fetch", async (url, init) => {
    sent.push(init.body);
    return new Response(null, { status: 201 });
  });
  const titles = async () => Promise.all(sent.splice(0).map(async (x) => (await b.read(x)).title));

  await call("POST", "/api/admin/checks/play.production", { done: true }, owner);
  assert.deepEqual(await titles(), [], "one confirmation left: nothing turns on tomorrow");
  await call("POST", "/api/admin/checks/play.subscription", { done: true }, owner);
  assert.deepEqual((await titles()).sort(), ["내일 결제(프로 구독)이(가) 저절로 켜져요", "내일 광고(띠·보상형)이(가) 저절로 켜져요"]);
  let list = (await call("GET", "/api/admin/flags", undefined, owner)).data;
  const billing = list.flags.find((f) => f.key === "billing");
  assert.deepEqual([billing.state, billing.next, billing.daysLeft], ["waiting", "2026-11-10", 1]);
  await cron(env, eve);
  assert.deepEqual(await titles(), [], "told once");
  assert.equal((await call("GET", "/api/config")).data.billingLive, false);

  // 0시 KST: on by itself; the launch is that moment, and for 24 hours the home screen offers to force it off.
  t.mock.timers.tick(60 * 60 * 1000);
  await cron(env, Date.now());
  assert.deepEqual((await titles()).sort(), ["결제(프로 구독)이(가) 켜졌어요", "광고(띠·보상형)이(가) 켜졌어요"]);
  const cfg = (await call("GET", "/api/config")).data;
  assert.equal(cfg.billingLive, true);
  assert.equal(cfg.launchedAt, "2026-11-09T15:00:00.000Z");
  const home = (await call("GET", "/api/admin/overview", undefined, owner)).data;
  assert.deepEqual(home.flagAlerts.map((a) => a.key).sort(), ["ads", "billing"]);
  t.mock.timers.tick(DAY + 1000);
  assert.deepEqual((await call("GET", "/api/admin/overview", undefined, owner)).data.flagAlerts, []);

  // Off and on again: the launch stays the first moment.
  await call("POST", "/api/admin/flags/billing/force", { force: "off", why: "결제 오류" }, owner);
  assert.equal((await call("GET", "/api/config")).data.billingLive, false);
  await call("POST", "/api/admin/flags/billing/force", { force: null }, owner);
  assert.equal((await call("GET", "/api/config")).data.launchedAt, "2026-11-09T15:00:00.000Z");
  const log = (await call("GET", "/api/admin/audit", undefined, owner)).data.entries.map((e) => e.action);
  assert.equal(log.filter((a) => a === "launch_recorded").length, 1);
  assert.ok(log.includes("flag_auto_on"));
  // The flag's own history keeps before and after.
  const detail = (await call("GET", "/api/admin/flags/billing", undefined, owner)).data;
  const off = detail.history.find((h) => h.action === "flag_force_off");
  assert.deepEqual([off.before, off.after.force, off.why, off.actor], [null, "off", "결제 오류", "최고 관리자"]);
  assert.equal(detail.launchedAt, Date.parse("2026-11-09T15:00:00Z"));
  list = (await call("GET", "/api/admin/flags", undefined, owner)).data;
  assert.equal(list.flags.find((f) => f.key === "billing").state, "on");
});

test("[FLAG-27] D97: forcing off is the owner's or flagOff's; forcing on and legal confirmations only the owner's", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  const { id, headers: m } = await member(call, owner);
  const reach = (await call("GET", "/api/admin/flags", undefined, m)).data.reach;
  assert.deepEqual(reach, { owner: false, edit: true, off: false });
  const no = async (path, body) => {
    const res = await call("POST", path, body, m);
    assert.equal(res.status, 403, path);
    return res.data.area;
  };
  assert.equal(await no("/api/admin/flags/plaza.write/force", { force: "off", why: "도배" }), "flagOff");
  assert.equal(await no("/api/admin/switches", { key: "PLAZA_WRITE", off: true, why: "도배" }), "flagOff");
  assert.equal(await no("/api/admin/flags/free.MONEY_STATS/force", { force: "on", why: "열기" }), "owner");
  assert.equal(await no("/api/admin/flags/billing/conditions", { conditions: [], why: "빨리" }), "owner");
  assert.equal(await no("/api/admin/checks/play.production", { done: true }), "owner");
  assert.equal(await no("/api/admin/policy", { version: "3.2", posted: "2026-10-28", effective: "2026-11-27", why: "x" }), "owner");
  assert.equal(await no("/api/admin/flags/billing/thanks", { thanksMonths: 2 }), "owner");
  // A flag that is not legal: conditions are 기능 관리 바꾸기.
  const changed = await call("POST", "/api/admin/flags/leave.hourly/conditions", { conditions: [{ type: "date", from: "2027-06-10" }, { type: "rollout", percent: 10 }], why: "먼저 10%" }, m);
  assert.equal(changed.status, 200);
  assert.equal(changed.data.override.by, "민지");

  // The owner gives the yes/no permission: now the member may force off (and lift a force on), never force on.
  const perms = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.id === id).perms;
  assert.equal(perms.flagOff, "none");
  assert.equal((await call("POST", `/api/admin/admins/${id}`, { perms: { ...perms, flagOff: "edit" } }, owner)).status, 200);
  assert.equal((await call("GET", "/api/admin/flags", undefined, m)).data.reach.off, true);
  assert.equal((await call("POST", "/api/admin/flags/plaza.write/force", { force: "off", why: "도배", reviewAt: "2026-10-30" }, m)).status, 200);
  await call("POST", "/api/admin/flags/free.MONEY_STATS/force", { force: "on", why: "열기" }, owner);
  assert.equal((await call("POST", "/api/admin/flags/free.MONEY_STATS/force", { force: null }, m)).status, 200);
  assert.equal(await no("/api/admin/flags/billing/force", { force: "on", why: "출시" }), "owner");
  // A legal flag forced off by an admin: lifting it back to its conditions is the owner's.
  assert.equal((await call("POST", "/api/admin/flags/billing/force", { force: "off", why: "사고" }, m)).status, 200);
  assert.equal(await no("/api/admin/flags/billing/force", { force: null }), "owner");

  // Looking only (문의 담당): the list, nothing else.
  const support = { review: "view", inquiries: "edit", dev: "edit", devStatus: "none", companies: "view", rewards: "none", settings: "none", community: "view",
    takedown: "none", ops: "view", metrics: "none", shares: "view", audit: "none", flags: "view", flagOff: "none" };
  const { id: sid, headers: s } = await member(call, owner, support, "help@example.com", 41);
  assert.equal((await call("GET", "/api/admin/flags", undefined, s)).status, 200);
  assert.equal((await call("GET", "/api/admin/types", undefined, s)).status, 200);
  assert.equal((await call("POST", "/api/admin/flags/leave.hourly/conditions", { conditions: null, why: "되돌림" }, s)).status, 403);
  // An admin from before 1.28 (no 기능 관리 in the stored permissions) who could see 운영 can look too.
  DB.db.prepare("UPDATE admins SET perms = ? WHERE id = ?").run(JSON.stringify({ ...support, flags: undefined, flagOff: undefined }), sid);
  assert.equal((await call("GET", "/api/admin/flags", undefined, s)).status, 200);
  DB.db.prepare("UPDATE admins SET perms = ? WHERE id = ?").run(JSON.stringify({ ...support, ops: "none", flags: undefined }), sid);
  assert.equal((await call("GET", "/api/admin/flags", undefined, s)).status, 403);
});

test("[FLAG-21] what is sent is checked: why, until, 다시 볼 날, conditions with the first mistake, unknown keys", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const post = (path, body) => call("POST", `/api/admin${path}`, body, owner);
  assert.equal((await post("/flags/plaza.write/force", { force: "maybe", why: "x" })).data.error, "force");
  assert.equal((await post("/flags/plaza.write/force", { force: "off" })).data.error, "why");
  assert.equal((await post("/flags/plaza.write/force", { force: "off", why: "x", until: "2026-10-01" })).data.error, "flag_until");
  assert.equal((await post("/flags/plaza.write/force", { force: "off", why: "x", until: "2028-01-01" })).data.error, "flag_until");
  assert.equal((await post("/flags/plaza.write/force", { force: "off", why: "x", reviewAt: "2026-13-01" })).data.error, "review_at");
  assert.equal((await call("GET", "/api/admin/flags/nope", undefined, owner)).status, 404);
  assert.equal((await post("/flags/free.lower/force", { force: "on", why: "x" })).status, 404);
  assert.equal((await post("/checks/made.up", { done: true })).status, 404);
  const bad = await post("/flags/leave.hourly/conditions", { conditions: [{ type: "date", from: "2027-06-10" }, { type: "weather" }], why: "x" });
  assert.deepEqual([bad.status, bad.data.error, bad.data.detail], [400, "conditions", "2번째 조건: 모르는 종류예요"]);
  const manual = await post("/flags/leave.hourly/conditions", { conditions: [{ type: "manual", key: "made.up" }], why: "x" });
  assert.match(manual.data.detail, /모르는 확인 이름/);
  assert.equal((await post("/flags/leave.hourly/conditions", { conditions: [{ type: "date", from: "2027-01-01" }] })).data.error, "why");

  // Good conditions reach the phones as written, and back to the app's own with null.
  const text = "date >= 2027-01-01\npolicy 3.2 or rollout 20%\nworkType SHIFT,DAY";
  const parsed = L.parseConditions(text);
  const ok = await post("/flags/leave.hourly/conditions", { conditions: parsed.list, why: "법 시행 앞당김(시험)" });
  assert.equal(ok.status, 200);
  assert.equal(L.conditionText(ok.data.conditions), text);
  assert.deepEqual((await call("GET", "/api/config")).data.flags["leave.hourly"].conditions, parsed.list);
  await post("/flags/leave.hourly/conditions", { conditions: null, why: "되돌림" });
  assert.equal((await call("GET", "/api/config")).data.flags["leave.hourly"], undefined);
});

test("[FLAG-32] ways of working: hidden one by one, never the last one", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const types = ["INVEST", "BUSINESS", "FIELD", "HOURLY", "DAY"];
  for (const type of types) assert.equal((await call("POST", "/api/admin/types", { type, show: false, why: "아직 다듬는 중" }, owner)).status, 200);
  const last = await call("POST", "/api/admin/types", { type: "SHIFT", show: false, why: "x" }, owner);
  assert.deepEqual([last.status, last.data.error], [409, "last_type"]);
  const view = (await call("GET", "/api/admin/types", undefined, owner)).data.types;
  assert.deepEqual(view.filter((x) => x.on).map((x) => x.style), ["SHIFT"]);
  assert.equal((await call("GET", "/api/config")).data.flags["type.invest"].force, "off");
  assert.equal((await call("POST", "/api/admin/types", { type: "INVEST", show: true }, owner)).status, 200);
  assert.equal((await call("GET", "/api/config")).data.flags["type.invest"], undefined);
  assert.equal((await call("POST", "/api/admin/types", { type: "NIGHT", show: true }, owner)).data.error, "type");
  const log = (await call("GET", "/api/admin/audit", undefined, owner)).data.entries.map((e) => e.action);
  assert.equal(log.filter((a) => a === "type_hide").length, 5);
  assert.equal(log.filter((a) => a === "type_show").length, 1);
});

test("[FLAG-29][FLAG-33] the owner moves a policy's dates; too little notice is said on the flag", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const statsMore = async () => (await call("GET", "/api/admin/flags", undefined, owner)).data.flags.find((f) => f.key === "stats.more");
  assert.deepEqual((await statsMore()).warnings, []);
  assert.equal((await call("POST", "/api/admin/policy", { version: "3.2", posted: "2026-10-28", effective: "2026-10-20", why: "x" }, owner)).data.error, "policy");
  const moved = await call("POST", "/api/admin/policy", { version: "3.2", posted: "2026-10-28", effective: "2026-11-15", unfavourable: true, why: "검토 끝나 앞당김" }, owner);
  assert.equal(moved.status, 200);
  const row = await statsMore();
  assert.match(row.warnings[0], /18일.*30일/);
  assert.equal(row.next, "2026-11-15", "the date condition follows the calendar");
  assert.deepEqual((await call("GET", "/api/config")).data.policy.find((p) => p.version === "3.2"), { version: "3.2", posted: "2026-10-28", effective: "2026-11-15", heldUntil: null });
  assert.ok((await call("GET", "/api/admin/overview", undefined, owner)).data.flagsDue >= 1);
});

test("[FLAG-26] /api/passes says whether the phone was linked by the owner", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const { headers: m } = await member(call, owner);
  const oc = (await call("POST", "/api/admin/me/link-code", {}, owner)).data.code;
  await call("POST", "/api/admin-link", { code: oc }, { "x-device": device(1) });
  const mc = (await call("POST", "/api/admin/me/link-code", {}, m)).data.code;
  await call("POST", "/api/admin-link", { code: mc }, { "x-device": device(2), ...from(2) });
  assert.deepEqual((await call("GET", "/api/passes", undefined, { "x-device": device(1) })).data.admin, { status: "approved", name: "최고 관리자", owner: true });
  assert.equal((await call("GET", "/api/passes", undefined, { "x-device": device(2) })).data.admin.owner, false);
});

test("[FLAG-30] which flags were on comes in as day totals, no phone told apart; unknown keys are dropped", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const day = "2026-10-25";
  const bundle = (extra) => ({ v: 1, app: "1.27.0", days: [{ day, events: { open: 1 } }], ...extra });
  assert.equal((await call("POST", "/api/events", bundle({ flagsOn: ["billing", "type.shift", "made.up"], startup: { container: 120, activity: 300, firstMonth: 800 } }))).status, 204);
  assert.equal((await call("POST", "/api/events", bundle({ days: [{ day, events: { open: 2 }, flagsOn: ["type.shift"] }] }), from(2))).status, 204);
  assert.equal((await call("POST", "/api/events", bundle({}), from(3))).status, 204, "a phone without stats.more sends no list");
  assert.equal((await call("POST", "/api/events", bundle({ flagsOn: "billing" }), from(4))).data.error, "flagsOn");
  const rows = DB.db.prepare("SELECT flag, phones FROM usage_flags ORDER BY flag").all().map((r) => ({ ...r }));
  assert.deepEqual(rows, [{ flag: "*", phones: 2 }, { flag: "billing", phones: 1 }, { flag: "type.shift", phones: 2 }]);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_flags WHERE app <> '1.27.0'").get().n, 0);
  const owner = await signIn(call);
  const list = (await call("GET", "/api/admin/flags", undefined, owner)).data;
  assert.equal(list.statsPhones, 2);
  assert.equal(list.flags.find((f) => f.key === "billing").onShare, 50);
  const detail = (await call("GET", "/api/admin/flags/type.shift", undefined, owner)).data;
  assert.deepEqual(detail.versions, [{ app: "1.27.0", code: 38, phones: 2, share: 100 }]);
});

test("[FLAG-21] the shared evaluator: order of the steps, a legal flag forced on still waits for its policy, what varies per phone", () => {
  const policy = [{ version: "3.1", posted: "2026-10-27", effective: "2026-11-10", heldUntil: "2026-11-24" }];
  const billing = FLAG_CATALOG.find((f) => f.key === "billing");
  const at = (d) => Date.parse(`${d}T00:00:00+09:00`);
  const checks = { "play.production": {}, "play.subscription": {} };
  // Before the effective day: waiting on the date, 15 days.
  let ev = L.evalFlag(billing, null, { now: at("2026-10-26"), policy, checks });
  assert.deepEqual([ev.state, ev.next], ["waiting", "2026-11-10"]);
  // The day: on for every phone not held by 3.1 (that varies per phone until 11-24).
  ev = L.evalFlag(billing, null, { now: at("2026-11-10"), policy, checks });
  assert.deepEqual([ev.on, ev.conditions[1].met], [true, "phone"]);
  // A phone installed before 3.1 that did not agree: held, even with a force on.
  const held = { app: 39, agreed: "3.0", installed: "2026-10-01", consents: {} };
  ev = L.evalFlag(billing, { force: "on", why: "출시" }, { now: at("2026-11-12"), policy, checks, phone: held });
  assert.deepEqual([ev.on, ev.state], [false, "waiting"]);
  ev = L.evalFlag(billing, { force: "on", why: "출시" }, { now: at("2026-11-12"), policy, checks, phone: { ...held, agreed: "3.1" } });
  assert.equal(ev.state, "forced_on");
  // Force off wins over everything, a preview included.
  ev = L.evalFlag(billing, { force: "off", why: "사고" }, { now: at("2026-12-01"), policy, checks, phone: { ...held, admin: true, preview: true } });
  assert.equal(ev.state, "forced_off");
  // Outside the app range: off before a preview.
  const leave = FLAG_CATALOG.find((f) => f.key === "leave.hourly");
  ev = L.evalFlag(leave, { conditions: [{ type: "app", min: 40, max: null }] }, { now: at("2026-12-01"), policy, checks, phone: { app: 39, admin: true, preview: true } });
  assert.equal(ev.source, "app");
  // ads: Google's consent not asked yet counts (AL-D5), a no turns it off.
  const ads = FLAG_CATALOG.find((f) => f.key === "ads");
  const fine = { app: 39, agreed: "3.1", consents: {} };
  assert.equal(L.evalFlag(ads, null, { now: at("2026-11-12"), policy, checks, phone: fine }).on, true);
  assert.equal(L.evalFlag(ads, null, { now: at("2026-11-12"), policy, checks, phone: { ...fine, consents: { ads: false } } }).on, false);
  // The editor's language round trips and says the first mistake.
  assert.match(L.parseConditions("date >= 2026-11-10\nweather sunny").error, /2째 줄/);
  assert.deepEqual(L.parseConditions("date >= policy 3.1\nconsent ads?").list, [{ type: "date", policy: "3.1" }, { type: "consent", key: "ads", unknownMeets: true }]);
});
