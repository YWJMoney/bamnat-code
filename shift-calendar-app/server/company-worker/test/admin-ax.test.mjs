// AX 1.30 The developer app's server side: 오늘's 7-day trends and 주의 필요 (ADMX-01, ADMX-07), the company table's numbers
// and sort (ADMX-06), one search (ADMX-08), 여러 판 compared with the right version and their facts (ADMX-02, ADMX-03), the
// place for BD's setups of another shape (ADMX-04), a reward named by its company (ADMX-01), 지표's extras (ADMX-07) and
// the alerts as web push (ADMX-10).
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, setup, device, from, signIn, ORIGIN } from "./helpers.mjs";
import { alertTick } from "../src/index.js";

const APP = { "x-admin": "1", origin: ORIGIN };
const NONE = { review: "none", inquiries: "none", dev: "none", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };

async function member(call, owner, perms, email = "minji@example.com", ip = 40) {
  const inv = await call("POST", "/api/admin/invites", { email, perms: { ...NONE, ...perms } }, owner);
  assert.equal(inv.status, 201, inv.text);
  const token = inv.data.link.split("#")[1];
  assert.equal((await call("POST", "/api/admin/join", { token, name: "민지", password: "minji-answers-2026" }, { ...APP, ...from(ip) })).status, 201);
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: "minji-answers-2026" }, { ...APP, ...from(ip) });
  return { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
}

/** A question written straight into the database [ago] ms back. */
function question(DB, n, kind = "bug", ago = 60000) {
  const at = Date.now() - ago;
  const id = `q${String(n).padStart(21, "0")}`;
  DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, ?, ?, '{}', 'open', ?, ?)").run(id, `h${n}`, kind, at, at);
  DB.db.prepare("INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)").run(id, `달력 알람이 늦어요 ${n}`, at);
  return id;
}

test("[ADMX-01][ADMX-07] /today has seven days of each count and 주의 필요: problem reports twice yesterday's, 정할 판", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  // Today 4 problem reports, yesterday 1 (an hour after midnight KST yesterday is at most 47 hours back).
  for (let n = 0; n < 4; n++) question(DB, n);
  const kstNow = new Date(Date.now() + 9 * 3600e3);
  const yesterday = Date.UTC(kstNow.getUTCFullYear(), kstNow.getUTCMonth(), kstNow.getUTCDate() - 1, 3) - 9 * 3600e3;
  question(DB, 9, "bug", Date.now() - yesterday);
  question(DB, 10, "question", 30 * 86400e3);
  // A rule in use and a different setup waiting in the same department: 정할 판.
  await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/reports", report(2), from(2));
  const other = setup();
  other.pay.rules.extra[3].percent = 0.12;
  await call("POST", "/api/reports", report(3, { setup: other }), from(3));
  const t = (await call("GET", "/api/admin/today", undefined, owner)).data;
  assert.deepEqual(t.trend.bugs.slice(5), [1, 4]);
  assert.equal(t.trend.inquiries.length, 7);
  assert.equal(t.trend.inquiries.reduce((a, b) => a + b, 0), 5, "the month-old question is outside the week");
  assert.equal(t.trend.reports[6], 3);
  assert.ok(Array.isArray(t.trend.errors) && Array.isArray(t.trend.doubts) && Array.isArray(t.trend.flags));
  const keys = t.alerts.map((a) => a.key);
  assert.ok(keys.includes("bugs"), keys.join());
  assert.ok(keys.includes("versions"));
  const bugs = t.alerts.find((a) => a.key === "bugs");
  assert.deepEqual([bugs.kind, bugs.tone, bugs.to], ["spike", "bad", "#/inquiries?status=all&kind=bug"]);
  assert.match(bugs.title, /오늘 4건/);
  assert.equal(t.versionConflicts, 1);
  // An admin who answers questions only gets their counts, nothing of the plaza.
  const minji = await member(call, owner, { inquiries: "edit" });
  const m = (await call("GET", "/api/admin/today", undefined, minji)).data;
  assert.deepEqual(Object.keys(m.trend).sort(), ["bugs", "inquiries"]);
  assert.ok(!m.alerts.some((a) => a.key === "versions"));
});

test("[ADMX-06] the company table: earlier versions, 30-day uses, 맞았어요·달라요, sorted by a column", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const a = await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/reports", report(2), from(2));
  await call("POST", "/api/reports", report(6), from(6));
  await call("POST", "/api/reports", report(5, { company: "가나다화학" }), from(5));
  const id = a.data.company.id;
  await call("POST", "/api/votes", { device: device(7), company: id, setup: a.data.setup, ok: false, why: "pay" }, from(7));
  DB.db.prepare("INSERT OR IGNORE INTO uses (company_id, grp, device, at) VALUES (?, ?, ?, ?)").run(id, a.data.setup, "u1", Date.now());
  DB.db.prepare("INSERT OR IGNORE INTO uses (company_id, grp, device, at) VALUES (?, ?, ?, ?)").run(id, a.data.setup, "u2", Date.now() - 40 * 86400e3);
  const list = (await call("GET", "/api/admin/companies?sort=uses", undefined, owner)).data;
  assert.equal(list.sort, "uses");
  const top = list.companies[0];
  assert.equal(top.id, id);
  assert.deepEqual([top.rules, top.previous, top.uses30, top.bad], [1, 0, 1, 1]);
  assert.equal(typeof top.ok, "number");
  const byName = (await call("GET", "/api/admin/companies?sort=name", undefined, owner)).data.companies.map((c) => c.name);
  assert.deepEqual(byName, [...byName].sort());
  assert.equal((await call("GET", "/api/admin/companies?sort=drop", undefined, owner)).data.sort, "new", "an unknown sort is the default");
});

test("[ADMX-08] one search finds companies, questions and 개발사항, only in the areas the admin may see", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  await call("POST", "/api/reports", report(1, { company: "알람정밀" }), from(1));
  const q = question(DB, 1);
  await call("POST", "/api/admin/dev", { title: "알람 소리 고치기", body: "" }, owner);
  const all = (await call("GET", `/api/admin/search?q=${encodeURIComponent("알람")}`, undefined, owner)).data;
  assert.deepEqual(all.groups.map((g) => g.kind), ["companies", "inquiries", "dev"]);
  assert.equal(all.groups[1].items[0].to, `#/inquiries/${q}`);
  assert.equal(all.groups[0].more, `#/companies?q=${encodeURIComponent("알람")}`);
  assert.equal((await call("GET", `/api/admin/search?q=${q}`, undefined, owner)).data.groups[0].items[0].to, `#/inquiries/${q}`, "a question's number");
  assert.deepEqual((await call("GET", "/api/admin/search?q=알", undefined, owner)).data.groups, [], "two letters at least");
  const minji = await member(call, owner, { inquiries: "view" });
  const hers = (await call("GET", `/api/admin/search?q=${encodeURIComponent("알람")}`, undefined, minji)).data;
  assert.deepEqual(hers.groups.map((g) => g.kind), ["inquiries"]);
  // The owner also finds admins by name or mail.
  const admins = (await call("GET", "/api/admin/search?q=minji", undefined, owner)).data;
  assert.deepEqual(admins.groups.map((g) => g.kind), ["admins"]);
});

test("[ADMX-02][ADMX-03] 여러 판: the rule in use says what it changed from v1, a waiting one from the rule in use, v1 what 다시 대표로 changes", async () => {
  const { call } = server();
  const owner = await signIn(call);
  await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/reports", report(2), from(2));
  await call("POST", "/api/reports", report(6), from(6));
  const twelve = setup();
  twelve.pay.rules.extra[3].percent = 0.12;
  const v2 = await call("POST", "/api/reports", report(3, { setup: twelve }), from(3));
  await call("POST", `/api/admin/groups/${v2.data.company.id}/${v2.data.setup}/adopt`, {}, owner);
  const fifteen = setup();
  fifteen.pay.rules.extra[3].percent = 0.15;
  await call("POST", "/api/reports", report(4, { setup: fifteen }), from(4));
  const d = (await call("GET", "/api/admin/versions", undefined, owner)).data;
  const [item] = d.items;
  const by = Object.fromEntries(item.candidates.map((c) => [c.state, c]));
  assert.deepEqual([by.active.against, by.active.changes.summary], [{ state: "previous", version: 1 }, "대근 10%→12%"]);
  assert.deepEqual([by.pending.against, by.pending.changes.summary], [{ state: "active", version: 2 }, "대근 12%→15%"]);
  assert.deepEqual([by.previous.against, by.previous.changes.summary], [{ state: "active", version: 2 }, "대근 12%→10%"]);
  assert.ok(by.active.facts.some(([l]) => l === "근무·주간"), "facts for the 비교 표");
});

test("[ADMX-04] a setup BD keeps as another shape (reason 'shape') is marked on 검토 and counted on 오늘", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const r = await call("POST", "/api/reports", report(1, { company: "가나다화학" }), from(1));
  DB.db.prepare("UPDATE reports SET reason = 'shape' WHERE company_id = ?").run(r.data.company.id);
  const q = (await call("GET", "/api/admin/queue", undefined, owner)).data;
  assert.equal(q.groups[0].shape, true);
  const t = (await call("GET", "/api/admin/today", undefined, owner)).data;
  assert.deepEqual(t.alerts.find((a) => a.key === "shape")?.to, "#/review?show=shape");
});

test("[ADMX-01] 최근 활동 says a reward by its company, never its id", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const r = await call("POST", "/api/reports", report(1, { company: "가나다화학" }), from(1));
  const rep = DB.db.prepare("SELECT id FROM reports LIMIT 1").get().id;
  DB.db.prepare("INSERT INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at) VALUES ('wABCDEF', ?, '', 'd', 1, 'paid', '', ?)").run(rep, Date.now());
  DB.db.prepare("INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, 'reward_auto', 'wABCDEF', '1개월', '', '')").run(Date.now());
  assert.ok(r.data.company.id);
  const e = (await call("GET", "/api/admin/today", undefined, owner)).data.recent.find((x) => x.action === "reward_auto");
  assert.deepEqual([e.label, e.link], ["가나다화학 보상", "#/rewards"]);
});

test("[ADMX-07] 지표: app versions first seen in the period and this period against the one before", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const kst = (ms) => new Date(ms + 9 * 3600e3).toISOString().slice(0, 10);
  const ins = DB.db.prepare("INSERT INTO usage_days (day, app, phones) VALUES (?, ?, ?)");
  ins.run(kst(Date.now() - 60 * 86400e3), "1.28.0", 30);
  ins.run(kst(Date.now() - 3 * 86400e3), "1.28.0", 30);
  ins.run(kst(Date.now() - 3 * 86400e3), "1.30.0", 5);
  question(DB, 1, "bug", 2 * 86400e3);
  question(DB, 2, "bug", 10 * 86400e3);
  const m = (await call("GET", "/api/admin/metrics?days=7", undefined, owner)).data;
  assert.deepEqual(m.releases.map((r) => r.app), ["1.30.0"]);
  assert.deepEqual(m.compare.bugs, { now: 1, before: 1 });
  for (const k of ["reports", "applied", "inquiries", "posts", "errors"]) assert.equal(typeof m.compare[k].now, "number", k);
});

test("[ADMX-10] the alert kinds are offered by area, and alertTick sends each alert once a day", async (t) => {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const b64u = (bytes) => Buffer.from(bytes).toString("base64url");
  const env = { VAPID_PUBLIC_KEY: b64u(new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey))),
    VAPID_PRIVATE_KEY: (await crypto.subtle.exportKey("jwk", pair.privateKey)).d, VAPID_SUBJECT: "mailto:ops@bamnat.example" };
  const s = server(env);
  const { call, DB } = s;
  const owner = await signIn(call);
  const kinds = (await call("GET", "/api/admin/me/push", undefined, owner)).data.kinds.map((k) => k.key);
  for (const k of ["late", "versions", "doubt", "spike"]) assert.ok(kinds.includes(k), k);
  const minji = await member(call, owner, { inquiries: "edit" });
  const hers = (await call("GET", "/api/admin/me/push", undefined, minji)).data.kinds.map((k) => k.key);
  assert.ok(hers.includes("spike") && hers.includes("late") && !hers.includes("doubt") && !hers.includes("versions"));
  const ua = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const sub = { endpoint: "https://fcm.googleapis.com/fcm/send/ax-1", keys: { p256dh: b64u(new Uint8Array(await crypto.subtle.exportKey("raw", ua.publicKey))), auth: b64u(crypto.getRandomValues(new Uint8Array(16))) } };
  await call("POST", "/api/admin/me/push", { subscription: sub, kinds: ["spike"] }, owner);
  for (let n = 0; n < 4; n++) question(DB, n);
  const sent = [];
  t.mock.method(globalThis, "fetch", async (url) => (sent.push(url), new Response(null, { status: 201 })));
  await alertTick(s.env, Date.now());
  assert.equal(sent.length, 1, "문제 신고 급증, once");
  await alertTick(s.env, Date.now());
  assert.equal(sent.length, 1, "not again the same day");
});
