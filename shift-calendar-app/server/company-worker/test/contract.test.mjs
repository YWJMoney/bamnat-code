// 1.28 통합 [FLAG-34]~[FLAG-38] The phone and the server as one system: the server's flag book is core's (generated),
// the answers the phone reads are written as fixtures the app's JVM tests parse with the real LaunchConfig.parse and
// FlagEval (app/src/test/java/com/bamnat/app/flags/ServerContractTest.kt), and the 사용 통계 body the app builds is
// taken here. `npm test`; after a change to /api/config or /api/passes write the fixtures again with
// `UPDATE_FIXTURES=1 npm test` and run the app test (it fails until both sides agree).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";
import worker, { FLAG_CATALOG, notifyAdmins } from "../src/index.js";
import { CORE_FLAGS, CORE_FREE, CORE_POLICY, CORE_NAMES } from "../src/flag-catalog.js";
import { adminLib } from "../src/admin-lib.js";
import { bundle } from "../tools/bundle.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const FIXTURES = join(here, "../../../app/src/test/resources/server-fixtures");
const APP = { "x-admin": "1", origin: ORIGIN };
const DAY = 24 * 60 * 60 * 1000;
const L = adminLib();

/** Writes [name] with UPDATE_FIXTURES=1, otherwise says how to when the server's answer moved away from it. */
function fixture(name, value) {
  const file = join(FIXTURES, name);
  const text = `${JSON.stringify(value, null, 2)}\n`;
  if (process.env.UPDATE_FIXTURES === "1") {
    mkdirSync(FIXTURES, { recursive: true });
    writeFileSync(file, text);
    return;
  }
  assert.ok(existsSync(file), `${file} is missing: UPDATE_FIXTURES=1 npm test, then the app's ServerContractTest`);
  assert.equal(readFileSync(file, "utf8"), text, `${name} is not what the server answers now: UPDATE_FIXTURES=1 npm test, then run the app's ServerContractTest`);
}

async function member(call, owner, perms, email = "minji@example.com", ip = 40) {
  const inv = await call("POST", "/api/admin/invites", { email, perms }, owner);
  await call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "민지", password: "minji-contract-2026" }, { ...APP, ...from(ip) });
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: "minji-contract-2026" }, { ...APP, ...from(ip) });
  return { id, headers: { cookie: res.headers.get("set-cookie").split(";")[0], ...APP } };
}

const MANAGER = { review: "edit", inquiries: "edit", dev: "edit", devStatus: "edit", companies: "edit", rewards: "edit", settings: "edit", community: "edit",
  takedown: "edit", ops: "edit", metrics: "view", shares: "edit", audit: "view", flags: "edit", flagOff: "none" };

test("[FLAG-34] the server's flag book is core's: same rows, words, conditions, 처리방침 seed and condition names", () => {
  assert.equal(FLAG_CATALOG, CORE_FLAGS, "index.js serves the generated book itself");
  for (const f of CORE_FLAGS) {
    assert.deepEqual(L.checkConditions(f.conditions).list, f.conditions, `${f.key}: conditions as the editor writes them back`);
    assert.ok(["money", "type", "law", "server", "stats"].includes(f.group), `${f.key} group`);
  }
  // The developer app's evaluator names conditions the way the phone's 진단 screen does.
  assert.deepEqual(L.CONSENT_NAMES, CORE_NAMES.consent);
  assert.deepEqual(L.PROFILE_NAMES, CORE_NAMES.profile);
  assert.deepEqual(L.MANUAL_NAMES, CORE_NAMES.manual);
  assert.deepEqual(L.WORK_NAMES, CORE_NAMES.work);
  assert.ok(new RegExp(CORE_FREE.pattern).test("free.MONEY_STATS"));
  // The bundle carries the book once, without an import or export left.
  const out = bundle();
  assert.equal(out.split("const CORE_FLAGS = [").length, 2);
  assert.ok(!/^export const CORE_/m.test(out));
});

test("[FLAG-34] /api/config's calendar keeps core's dates for the versions the app ships with", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-26T01:00:00Z") });
  const { call } = server();
  const policy = (await call("GET", "/api/config")).data.policy;
  for (const p of CORE_POLICY) assert.deepEqual(policy.find((x) => x.version === p.version), p);
  const owner = await signIn(call);
  const detail = (await call("GET", "/api/admin/flags/free.MONEY_STATS", undefined, owner)).data;
  assert.equal(detail.name, CORE_FREE.name.replace(CORE_FREE.slot, "돈 통계"));
});

test("[FLAG-35] /api/config and /api/passes as the phone reads them, state by state (fixtures for the app's ServerContractTest)", async (t) => {
  const at = (s) => Date.parse(s);
  t.mock.timers.enable({ apis: ["Date"], now: at("2026-10-26T01:00:00Z") });
  const { call, DB } = server();
  const config = async () => (await call("GET", "/api/config")).data;
  const passes = async (n) => (await call("GET", "/api/passes", undefined, { "x-device": device(n), ...from(n) })).data;

  // 1. A server just deployed: nothing overridden, only billing·ads (waiting on Play) sent with their confirmations.
  fixture("fresh.json", { state: "fresh server, 2026-10-26", config: await config(), passes: await passes(1) });

  // 2. A 1.27 server's settings, moved once: 정식 출시 on → billing·ads forced on, a free feature, a switch off.
  {
    const old = server();
    old.DB.db.exec("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    const put = old.DB.db.prepare("INSERT INTO settings (key, value) VALUES (?, ?)");
    put.run("billing_live", "1");
    put.run("free_features", JSON.stringify(["MONEY_STATS"]));
    put.run("switches_off", JSON.stringify(["PLAZA_WRITE"]));
    fixture("migrated-1.27.json", { state: "1.27 settings moved (정식 출시 on, MONEY_STATS free, PLAZA_WRITE off), 2026-10-26",
      config: (await old.call("GET", "/api/config")).data });
  }

  // 3. Billing waiting: the 3.1 day passed and one of the two Play confirmations is in.
  t.mock.timers.setTime(at("2026-11-09T01:00:00Z"));
  let owner = await signIn(call);
  await call("POST", "/api/admin/checks/play.production", { done: true, note: "출시 개요에서 봄" }, owner);
  t.mock.timers.setTime(at("2026-11-12T03:00:00Z"));
  owner = await signIn(call);
  fixture("billing-one-confirmation.json", { state: "2026-11-12, play.production only", config: await config() });

  // 4. Both confirmations: billing on for every phone not held by 3.1; the launch is written.
  await call("POST", "/api/admin/checks/play.subscription", { done: true }, owner);
  fixture("billing-confirmed.json", { state: "2026-11-12, both Play confirmations", config: await config() });

  // 5. Force off with why and until, a hidden way of working, an app range; the owner's phone and a member's.
  await call("POST", "/api/admin/flags/billing/force", { force: "off", why: "결제 오류 조사 중", until: "2026-11-20" }, owner);
  await call("POST", "/api/admin/flags/plaza.write/force", { force: "off", why: "도배" }, owner);
  await call("POST", "/api/admin/types", { type: "INVEST", show: false, why: "캡처 인식 검증 중" }, owner);
  assert.equal((await call("POST", "/api/admin/flags/share.create/range", { minApp: 40, maxApp: null, why: "39의 링크 오류" }, owner)).status, 200);
  const { headers: m } = await member(call, owner, MANAGER);
  const oc = (await call("POST", "/api/admin/me/link-code", {}, owner)).data.code;
  await call("POST", "/api/admin-link", { code: oc }, { "x-device": device(2), ...from(2) });
  const mc = (await call("POST", "/api/admin/me/link-code", {}, m)).data.code;
  await call("POST", "/api/admin-link", { code: mc }, { "x-device": device(3), ...from(3) });
  fixture("force-off.json", { state: "2026-11-12, billing forced off until 11-20, plaza.write off, INVEST hidden, share.create from 40",
    config: await config(), ownerPasses: await passes(2), memberPasses: await passes(3) });

  // 6. After 3.2: stats.more can turn on for a phone that said both yeses.
  await call("POST", "/api/admin/flags/billing/force", { force: null }, owner);
  t.mock.timers.setTime(at("2026-11-28T03:00:00Z"));
  owner = await signIn(call);
  fixture("after-3.2.json", { state: "2026-11-28, billing back to its conditions, after 3.2", config: await config() });

  // What the server itself says about those states (the app test checks the phone's side of the same answers).
  const migrated = JSON.parse(readFileSync(join(FIXTURES, "migrated-1.27.json"), "utf8")).config;
  assert.deepEqual([migrated.billingLive, migrated.freeFeatures, migrated.off], [true, ["MONEY_STATS"], ["PLAZA_WRITE"]]);
  const forced = JSON.parse(readFileSync(join(FIXTURES, "force-off.json"), "utf8"));
  assert.deepEqual(forced.config.flags["share.create"], { force: null, why: "", conditions: null, until: null, minApp: 40, maxApp: null, confirmed: {} });
  assert.deepEqual([forced.ownerPasses.admin.owner, forced.memberPasses.admin?.owner ?? false], [true, false]);
  assert.equal(DB.db.prepare("SELECT min_app FROM flag_overrides WHERE key = 'share.create'").get().min_app, 40);
});

test("[FLAG-35] the 사용 통계 body the app builds (flagsOn) is taken, and only per-key totals are kept", async (t) => {
  const file = join(FIXTURES, "events-from-app.json");
  assert.ok(existsSync(file), "events-from-app.json is written by the app's ServerContractTest (BAMNAT_WRITE_FIXTURES=1)");
  const { at, body } = JSON.parse(readFileSync(file, "utf8"));
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse(at) });
  const { call, DB } = server();
  assert.ok(Array.isArray(body.flagsOn) && body.flagsOn.includes("stats.more"));
  assert.equal((await call("POST", "/api/events", body)).status, 204);
  // A second phone with the same list, and one with a key this server does not know.
  assert.equal((await call("POST", "/api/events", { ...body, flagsOn: [...body.flagsOn, "made.up"] }, from(2))).status, 204);
  const rows = DB.db.prepare("SELECT day, app, flag, phones FROM usage_flags ORDER BY flag").all().map((r) => ({ ...r }));
  const day = body.days.map((d) => d.day).sort().at(-1);
  assert.deepEqual(rows.map((r) => r.flag), ["*", ...body.flagsOn].sort());
  for (const r of rows) assert.deepEqual([r.day, r.app, r.phones], [day, body.app, 2]);
  // Nothing that tells a phone apart is kept: the table has no such column, and the body had none to keep.
  assert.deepEqual(DB.db.prepare("PRAGMA table_info(usage_flags)").all().map((c) => c.name), ["day", "app", "flag", "phones"]);
  assert.ok(!/device|install/i.test(JSON.stringify(body)));
});

test("[FLAG-36] an override's app range: kept, checked, sent, evaluated for a preview phone, in the history", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-26T01:00:00Z") });
  const { call } = server();
  const owner = await signIn(call);
  const range = (key, body, who = owner) => call("POST", `/api/admin/flags/${key}/range`, body, who);
  assert.equal((await range("plaza.write", { minApp: 40, maxApp: 39, why: "x" })).data.error, "app");
  assert.equal((await range("plaza.write", { minApp: 1.5, why: "x" })).data.error, "app");
  assert.equal((await range("plaza.write", { minApp: 40 })).data.error, "why");
  const d = (await range("plaza.write", { minApp: 39, maxApp: 45, why: "40부터 새 편집기" })).data;
  assert.deepEqual([d.override.minApp, d.override.maxApp], [39, 45]);
  assert.equal(d.history[0].action, "flag_range");
  assert.deepEqual((await call("GET", "/api/config")).data.flags["plaza.write"], { force: null, why: "", conditions: null, until: null, minApp: 39, maxApp: 45, confirmed: {} });
  // A force keeps the range; the range clears to nothing.
  await call("POST", "/api/admin/flags/plaza.write/force", { force: "off", why: "도배" }, owner);
  assert.equal((await call("GET", "/api/config")).data.flags["plaza.write"].minApp, 39);
  await call("POST", "/api/admin/flags/plaza.write/force", { force: null }, owner);
  await range("plaza.write", { minApp: null, maxApp: null, why: "범위 없앰" });
  assert.equal((await call("GET", "/api/config")).data.flags["plaza.write"], undefined);
  // The shared evaluator: a phone outside the range is off before a preview, inside follows the rest.
  const def = FLAG_CATALOG.find((f) => f.key === "plaza.write");
  assert.equal(L.evalFlag(def, { minApp: 40 }, { now: Date.now(), policy: [], checks: {}, phone: { app: 39, admin: true, preview: true } }).source, "app");
  assert.equal(L.evalFlag(def, { minApp: 39 }, { now: Date.now(), policy: [], checks: {}, phone: { app: 39 } }).on, true);
  // Who may: as for conditions. A legal flag's range is the owner's; a member without 기능 관리 편집 cannot.
  const { headers: m } = await member(call, owner, { ...MANAGER, flags: "view" });
  assert.equal((await range("plaza.write", { minApp: 40, why: "x" }, m)).status, 403);
  const { headers: e } = await member(call, owner, MANAGER, "junho@example.com", 41);
  assert.equal((await range("billing", { minApp: 40, why: "x" }, e)).data.area, "owner");
  assert.equal((await range("plaza.write", { minApp: 40, why: "편집 권한" }, e)).status, 200);
});

test("[FLAG-37] D101: a legal flag's day-before news reaches an admin inside quiet hours; other news waits", async (t) => {
  const eve = Date.parse("2026-11-09T14:00:00Z"); // 11월 9일 23시(한국), 방해 금지 22~7시
  t.mock.timers.enable({ apis: ["Date"], now: eve });
  const keys = await vapidKeys();
  const { call, env } = server(keys);
  const owner = await signIn(call);
  await call("POST", "/api/admin/me/push", { subscription: subscription(1), kinds: ["flags", "inquiry"], quiet: { from: 22, to: 7 } }, owner);
  const sent = [];
  t.mock.method(globalThis, "fetch", async (url) => (sent.push(url), new Response(null, { status: 201 })));
  assert.deepEqual(await notifyAdmins(env, "inquiry", { title: "새 문의" }), { sent: 0, gone: 0 }, "quiet for a question");
  assert.deepEqual(await notifyAdmins(env, "flags", { title: "일반 기능 소식" }), { sent: 0, gone: 0 }, "quiet for a flag's other news");
  await call("POST", "/api/admin/checks/play.production", { done: true }, owner);
  await call("POST", "/api/admin/checks/play.subscription", { done: true }, owner);
  assert.equal(sent.length, 2, "내일 결제·광고가 켜져요: two pushes at 23:00 inside quiet hours");
  // 0시: turned on, again inside quiet hours.
  t.mock.timers.tick(60 * 60 * 1000);
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: Date.now() }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
  assert.equal(sent.length, 4);
});

test("[FLAG-38] a developer app cached before 1.28 switches off without why: it works and the record says so", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-26T01:00:00Z") });
  const { call, DB } = server();
  const owner = await signIn(call);
  const res = await call("POST", "/api/admin/switches", { key: "PLAZA_WRITE", off: true }, owner);
  assert.equal(res.status, 200);
  assert.ok(res.data.switches.find((s) => s.key === "PLAZA_WRITE").off);
  assert.equal((await call("GET", "/api/config")).data.flags["plaza.write"].why, "옛 관리자 앱에서 끔");
  const history = (await call("GET", "/api/admin/flags/plaza.write", undefined, owner)).data.history;
  assert.equal(history[0].why, "옛 관리자 앱에서 끔");
  const audit = DB.db.prepare("SELECT action, detail FROM audit WHERE action IN ('switch_off', 'flag_force_off') ORDER BY id").all().map((r) => ({ ...r }));
  assert.deepEqual(audit.map((a) => a.action), ["flag_force_off", "switch_off"]);
  assert.match(audit[1].detail, /옛 관리자 앱에서 끔/);
  // Back on needs no why either (the 1.27 button), and a why given is kept as given.
  assert.equal((await call("POST", "/api/admin/switches", { key: "PLAZA_WRITE", off: false }, owner)).status, 200);
  await call("POST", "/api/admin/switches", { key: "PLAZA_WRITE", off: true, why: "도배" }, owner);
  assert.equal((await call("GET", "/api/config")).data.flags["plaza.write"].why, "도배");
});

// ---- web push keys and a browser subscription (the server only needs a valid key to encrypt for) ----

const b64u = (bytes) => Buffer.from(bytes).toString("base64url");

async function vapidKeys() {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const pub = new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey));
  const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
  return { VAPID_PUBLIC_KEY: b64u(pub), VAPID_PRIVATE_KEY: jwk.d, VAPID_SUBJECT: "mailto:ops@bamnat.example" };
}

const browserKey = await (async () => {
  const pair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  return b64u(new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey)));
})();

const subscription = (n) => ({ endpoint: `https://fcm.googleapis.com/fcm/send/contract-${n}`, keys: { p256dh: browserKey, auth: b64u(crypto.getRandomValues(new Uint8Array(16))) } });
