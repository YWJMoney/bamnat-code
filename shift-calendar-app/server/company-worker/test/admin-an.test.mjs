// AN 1.28 [ADMUX-37][ADMUX-38] The developer app's light calls: /badges (the menu numbers, kept a short while per
// admin), /today (the 오늘 screen only), /basics (what rarely changes), and the indexes they read.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";

const APP = { "x-admin": "1", origin: ORIGIN };
const SUPPORT = { review: "none", inquiries: "edit", dev: "edit", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };

async function member(call, owner, perms = SUPPORT, email = "minji@example.com", ip = 40) {
  const inv = await call("POST", "/api/admin/invites", { email, perms }, owner);
  assert.equal(inv.status, 201, inv.text);
  const token = inv.data.link.split("#")[1];
  assert.equal((await call("POST", "/api/admin/join", { token, name: "민지", password: "minji-answers-2026" }, { ...APP, ...from(ip) })).status, 201);
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: "minji-answers-2026" }, { ...APP, ...from(ip) });
  assert.equal(res.status, 200, res.text);
  return { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
}

/** A question written straight into the database, so no request tells the server something changed. */
function addQuestion(DB, n, at = Date.now() - 60000) {
  const id = `q${String(n).padStart(21, "0")}`;
  DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, ?, 'bug', '{}', 'open', ?, ?)").run(id, `h${n}`, at, at);
  DB.db.prepare("INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)").run(id, `알람이 늦게 울려요 ${n}`, at);
  return id;
}

test("[ADMUX-37] /badges has the same numbers as /overview, and nothing of the home screen", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  for (let n = 0; n < 3; n++) addQuestion(DB, n);
  await call("POST", "/api/inquiries", { v: 1, device: device(9), kind: "bug", text: "앱에서 보낸 문의예요", info: { app: "1.27.0 (90)" } }, from(9));
  const b = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  const o = (await call("GET", "/api/admin/overview", undefined, owner)).data;
  for (const k of ["pending", "asks", "flagged", "inquiries", "devTodo", "owed", "flaggedPosts", "versionConflicts"]) {
    assert.equal(typeof b[k], "number", k);
    assert.equal(b[k], o[k], k);
  }
  assert.equal(b.inquiries, 4);
  assert.deepEqual(b.admins, o.admins);
  assert.equal(b.opsLeft, o.opsLeft);
  assert.equal(b.billingLive, o.billingLive);
  assert.equal(b.build, o.build);
  assert.deepEqual(b.me, o.me);
  // Only numbers: the lists and words stay with /today and /basics.
  for (const k of ["queueInquiries", "recent", "settings", "sla", "reasons", "todo", "assigned"]) assert.ok(!(k in b), k);
  assert.ok(JSON.stringify(b).length < JSON.stringify(o).length / 2, "much lighter than /overview");
});

test("[ADMUX-37] /badges is kept 15 seconds per admin, and anything sent makes it fresh", async (t) => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const support = await member(call, owner);
  const count = async (h) => (await call("GET", "/api/admin/badges", undefined, h)).data.inquiries;
  assert.equal(await count(owner), 0);
  assert.equal(await count(support), 0);
  addQuestion(DB, 1);
  // Still the kept number: nothing went through the server.
  assert.equal(await count(owner), 0);
  assert.equal(await count(support), 0);
  // A request that sends something (here a wrong one) clears what was kept, for everyone.
  assert.equal((await call("POST", "/api/admin/inquiries/nope/reply", { text: "x" }, owner)).status, 404);
  assert.equal(await count(owner), 1);
  assert.equal(await count(support), 1);
  // And after 15 seconds the numbers are read again by themselves.
  addQuestion(DB, 2);
  assert.equal(await count(owner), 1);
  t.mock.timers.enable({ apis: ["Date"], now: Date.now() + 16_000 });
  assert.equal(await count(owner), 2);
});

test("[ADMUX-37] /badges counts only what the admin can see, kept apart per admin", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const support = await member(call, owner);
  addQuestion(DB, 1);
  const o = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  const s = (await call("GET", "/api/admin/badges", undefined, support)).data;
  assert.equal(o.me.owner, true);
  assert.equal(s.me.owner, false);
  assert.equal(s.inquiries, 1);
  assert.equal(typeof s.devTodo, "number");
  for (const k of ["pending", "asks", "flagged", "owed", "flaggedPosts", "versionConflicts", "admins", "opsLeft"]) assert.ok(!(k in s), k);
  assert.ok("admins" in o && "owed" in o);
  // Not for anyone signed out.
  assert.equal((await call("GET", "/api/admin/badges", undefined, APP)).status, 401);
  assert.equal((await call("GET", "/api/admin/today", undefined, APP)).status, 401);
  assert.equal((await call("GET", "/api/admin/basics", undefined, APP)).status, 401);
});

test("[ADMUX-33][ADMUX-37] /today is 받은함's list with 오늘's numbers; /basics the words and settings", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const support = await member(call, owner);
  addQuestion(DB, 1);
  addQuestion(DB, 2, Date.now() - 3 * 24 * 60 * 60 * 1000);
  for (const h of [owner, support]) {
    const d = (await call("GET", "/api/admin/today", undefined, h)).data;
    const inbox = (await call("GET", "/api/admin/inbox", undefined, h)).data;
    assert.deepEqual({ ...d.todo, now: "" }, { ...inbox, now: "" });
    assert.ok(d.todo.items.length >= 2);
    assert.equal(d.inquiriesToday, 1);
    assert.equal(typeof d.doneToday, "number");
    assert.equal(typeof d.slaLateToday, "number");
    assert.ok(Array.isArray(d.assigned));
  }
  const s = (await call("GET", "/api/admin/today", undefined, support)).data;
  assert.ok(!("recent" in s) && !("reports" in s) && !("companies" in s), "only what 민지 may see");
  assert.ok(Array.isArray((await call("GET", "/api/admin/today", undefined, owner)).data.recent));
  const b = (await call("GET", "/api/admin/basics", undefined, support)).data;
  for (const k of ["settings", "sla", "doubt", "reasons", "answers", "hideReasons", "flagReasons"]) assert.ok(k in b, k);
  assert.equal(typeof b.settings.adoptPersons, "number");
});

test("[ADMUX-38] the four new indexes are made and used", async () => {
  const { call, DB } = server();
  await signIn(call);
  const names = DB.db.prepare("SELECT name FROM sqlite_master WHERE type = 'index'").all().map((r) => r.name);
  for (const n of ["inquiries_created", "inquiries_bad", "posts_flagged", "audit_action"]) assert.ok(names.includes(n), n);
  const plan = (sql, ...p) => DB.db.prepare(`EXPLAIN QUERY PLAN ${sql}`).all(...p).map((r) => r.detail).join(" | ");
  assert.match(plan("SELECT COUNT(*) AS n FROM inquiries WHERE created_at >= ?", 0), /inquiries_created/);
  assert.match(plan("SELECT COUNT(*) AS n FROM inquiries WHERE rating = -1 AND status != 'closed'"), /inquiries_bad/);
  assert.match(plan("SELECT COUNT(*) AS n FROM posts WHERE kept = 0 AND (flags > 0 OR hide_reason = 'flags')"), /posts_flagged/);
  assert.match(plan("SELECT id FROM audit WHERE action = ? ORDER BY id DESC LIMIT 50", "adopt"), /audit_action/);
});

// ---- 1.28 통합: AN의 /badges와 AM의 기능 관리 ----

test("[ADMUX-40][FLAG-28] /badges carries 기능 관리's number and 오늘's legal-flag card like /overview, only with the flags area", async () => {
  const { call } = server();
  const owner = await signIn(call);
  const support = await member(call, owner, { ...SUPPORT, ops: "edit", flags: "none" });
  const b = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  const o = (await call("GET", "/api/admin/overview", undefined, owner)).data;
  assert.equal(typeof b.flagsWaiting, "number");
  assert.equal(b.flagsWaiting, o.flagsDue);
  assert.deepEqual(b.flagAlerts, []);
  assert.equal(b.billingLive, false);
  // Forcing billing on is a write: the kept numbers are fresh at once, with the 24-hour card and billingLive.
  assert.equal((await call("POST", "/api/admin/flags/billing/force", { force: "on", why: "Play 승인" }, owner)).status, 200);
  const after = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  assert.deepEqual(after.flagAlerts.map((a) => a.key), ["billing"]);
  assert.equal(after.billingLive, true);
  // 운영 without 기능 관리 (flags none): no flag numbers, and the flag screens are closed.
  const s = (await call("GET", "/api/admin/badges", undefined, support)).data;
  assert.ok(!("flagsWaiting" in s) && !("flagAlerts" in s));
  assert.equal(s.billingLive, true);
  assert.equal((await call("GET", "/api/admin/flags", undefined, support)).status, 403);
});

test("[ADMUX-40][FLAG-30] /effects: every flag with its share of phones and problems before and after, for 지표 readers", async () => {
  const { call } = server();
  const owner = await signIn(call);
  await call("POST", "/api/admin/flags/plaza.write/force", { force: "off", why: "글 신고가 몰려요" }, owner);
  const e = (await call("GET", "/api/admin/effects", undefined, owner)).data;
  const keys = e.flags.map((f) => f.key);
  for (const k of ["billing", "ads", "type.shift", "plaza.write", "stats.more"]) assert.ok(keys.includes(k), k);
  assert.equal(e.flagsReach, true);
  const pw = e.flags.find((f) => f.key === "plaza.write");
  assert.equal(pw.state, "forced_off");
  assert.ok(pw.compare === null || typeof pw.compare.bugsBefore === "number");
  const support = await member(call, owner);
  assert.equal((await call("GET", "/api/admin/effects", undefined, support)).status, 403);
  const reader = await member(call, owner, { ...SUPPORT, metrics: "view", flags: "none" }, "reader@example.com", 41);
  const r = (await call("GET", "/api/admin/effects", undefined, reader)).data;
  assert.equal(r.flagsReach, false);
  assert.ok(r.flags.length > 10);
});
