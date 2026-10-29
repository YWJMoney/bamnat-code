// [ADMUX-12]~[ADMUX-19] 1.25 묶음 AA: the developer app made whole. Each test goes from the request the app
// makes to what the server then holds or answers, numbers included (계획 규칙 11 끝에서 끝).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { generateKeyPairSync } from "node:crypto";
import worker, { REMOTE, metricRange, inquiryStats, wilson } from "../src/index.js";
import { server, report, setup, device, from, signIn, ORIGIN, adminHome } from "./helpers.mjs";

const NOW = Date.parse("2026-10-25T03:00:00Z");
const DAY = 864e5;
const kst = (ms) => new Date(ms + 9 * 3600e3).toISOString().slice(0, 10);
const APP = { "x-admin": "1", origin: ORIGIN };

/** An approved admin with [perms]; returns the headers their requests carry. */
async function member(call, DB, owner, email, perms) {
  const inv = await call("POST", "/api/admin/invites", { email, perms }, owner);
  await call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: email.split("@")[0], password: "a-long-password-123" }, APP);
  const id = DB.db.prepare("SELECT id FROM admins WHERE email = ?").get(email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const login = await call("POST", "/api/admin/login", { email, password: "a-long-password-123" }, APP);
  return { cookie: login.headers.get("set-cookie").split(";")[0], ...APP };
}

const NONE = { review: "none", inquiries: "none", dev: "none", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };

/** Global fetch answered by [handler] for the test, then put back. */
function fakeFetch(t, handler) {
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init) => handler(String(url), init ?? {});
  t.after(() => { globalThis.fetch = real; });
}

test("[ADMUX-13] a period: 7~90 days back from yesterday, or two dates up to 180 days and never past yesterday", () => {
  const at = (q) => metricRange(new URL(`https://x/?${q}`), NOW);
  assert.deepEqual(at("days=7"), { from: "2026-10-18", to: "2026-10-24", days: 7, beforeFrom: "2026-10-11", beforeTo: "2026-10-17" });
  assert.equal(at("days=500").days, 90);
  assert.deepEqual(at("from=2026-10-01&to=2026-10-10"), { from: "2026-10-01", to: "2026-10-10", days: 10, beforeFrom: "2026-09-21", beforeTo: "2026-09-30" });
  // Asked into the future: it stops at yesterday. Reversed: one day. Too long: the last 180.
  assert.equal(at("from=2026-10-20&to=2026-12-31").to, "2026-10-24");
  assert.equal(at("from=2026-10-20&to=2026-10-01").days, 1);
  assert.deepEqual([at("from=2025-01-01&to=2026-10-24").days, at("from=2025-01-01&to=2026-10-24").from], [180, "2026-04-28"]);
  assert.equal(at("from=junk&to=2026-10-01").days, 28);
});

test("[ADMUX-14] first answers: the middle and 9-in-10 minutes, kept targets counting late unanswered ones, the users' word", () => {
  const sla = { slaBug: 8, slaQuestion: 24, slaIdea: 72, slaOther: 72 };
  const t0 = NOW - 10 * DAY;
  const rows = [
    { kind: "bug", created_at: t0, first: t0 + 30 * 60e3, rating: 1 },
    { kind: "bug", created_at: t0, first: t0 + 9 * 3600e3, rating: -1 }, // past 8 hours
    { kind: "question", created_at: t0, first: t0 + 2 * 3600e3, rating: 1 },
    { kind: "idea", created_at: t0, first: null, rating: null }, // 10 days unanswered: late
    { kind: "idea", created_at: NOW - 3600e3, first: null, rating: null }, // an hour old: not due yet
  ];
  const s = inquiryStats(rows, sla, [kst(t0), kst(NOW - 3600e3)], NOW);
  assert.deepEqual([s.received, s.answered, s.median, s.p90], [5, 3, 120, 540]);
  assert.deepEqual([s.due, s.kept], [4, 50]);
  assert.deepEqual([s.good, s.bad, s.rated], [2, 1, 66.7]);
  assert.deepEqual(s.series.received, [4, 1]);
  assert.deepEqual(s.kinds.find((k) => k.kind === "bug"), { kind: "bug", n: 2, answered: 2, median: 30, target: 8 });
});

test("[ADMUX-13][ADMUX-14][ADMUX-16] 지표 from the server: app versions, the plaza, answer times and server errors in the chosen days", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  for (let i = 1; i <= 7; i++) {
    DB.db.prepare("INSERT INTO usage_days VALUES (?, '1.24.0', 30), (?, '1.23.0', 10)").run(kst(NOW - i * DAY), kst(NOW - i * DAY));
  }
  // Three coworkers send the same setup five days ago; it is adopted, a phone applies it, a post is flagged.
  const a = await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/reports", report(2), from(2));
  await call("POST", "/api/reports", report(6), from(6));
  const post = await call("POST", "/api/posts", { device: device(3), company: a.data.company.id, setup: a.data.setup, body: "야간은 22시부터예요" }, from(3));
  await call("POST", `/api/posts/${post.data.id}/flag`, { device: device(4), reason: "spam" }, from(4));
  await call("POST", "/api/uses", { device: device(5), company: a.data.company.id, setup: a.data.setup }, from(5));
  const ago5 = NOW - 5 * DAY;
  for (const tbl of ["reports", "posts"]) DB.db.prepare(`UPDATE ${tbl} SET created_at = ?`).run(ago5);
  DB.db.prepare("UPDATE rules SET adopted_at = ?").run(ago5);
  DB.db.prepare("UPDATE post_flags SET at = ?").run(ago5);
  DB.db.prepare("UPDATE uses SET at = ?").run(ago5);
  // A bug report from 1.24.0 answered in 90 minutes, two days ago.
  const q = NOW - 2 * DAY;
  DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at, rating) VALUES ('q000000000000000000001', 'd', 'bug', ?, 'answered', ?, ?, 1)")
    .run(JSON.stringify({ app: "1.24.0 (35)" }), q, q);
  DB.db.prepare("INSERT INTO inquiry_messages (inquiry_id, sender, text, at) VALUES ('q000000000000000000001', 'admin', '고쳤어요', ?)").run(q + 90 * 60e3);
  DB.db.prepare("INSERT INTO counters VALUES (?, 3, ?)").run(`errors|${kst(NOW - 3 * DAY)}`, NOW + DAY);
  const m = (await call("GET", "/api/admin/metrics?days=7", undefined, owner)).data;
  assert.deepEqual([m.from, m.to, m.days, m.dayList.length], ["2026-10-18", "2026-10-24", 7, 7]);
  assert.deepEqual([m.active.daily, m.active.week], [40, 40]);
  assert.deepEqual(m.active.versions.map((v) => [v.app, v.code, v.share]), [["1.24.0", 35, 75], ["1.23.0", 34, 25]]);
  assert.deepEqual([m.plaza.reports, m.plaza.people, m.plaza.adopted, m.plaza.posts, m.plaza.flags, m.plaza.applied], [3, 3, 1, 1, 1, 1]);
  assert.equal(m.plaza.series.reports[m.dayList.indexOf(kst(ago5))], 3);
  assert.deepEqual([m.inquiries.received, m.inquiries.median, m.inquiries.kept, m.inquiries.rated], [1, 90, 100, 100]);
  assert.deepEqual(m.errors.bugs, [{ app: "1.24.0 (35)", n: 1 }]);
  assert.equal(m.errors.serverTotal, 3);
  assert.equal(m.errors.server[m.dayList.indexOf(kst(NOW - 3 * DAY))], 3);
  // The same by dates: only the last two days hold the question, none of the plaza.
  const two = (await call("GET", `/api/admin/metrics?from=${kst(NOW - 2 * DAY)}&to=${kst(NOW - DAY)}`, undefined, owner)).data;
  assert.deepEqual([two.days, two.plaza.reports, two.inquiries.received], [2, 0, 1]);
});

test("[ADMUX-16] a request the worker cannot answer is counted for 지표 › 오류", async () => {
  const { call, DB, env } = server();
  await call("GET", "/api/terms");
  const realPrepare = DB.prepare;
  DB.prepare = function (sql) {
    if (!sql.startsWith("INSERT INTO counters")) throw new Error("D1 went away");
    return realPrepare.call(this, sql);
  };
  const err = console.error;
  console.error = () => {};
  try {
    const res = await worker.fetch(new Request(`${ORIGIN}/api/board?tab=all`, { headers: { "cf-connecting-ip": "203.0.113.7" } }), env);
    assert.equal(res.status, 500);
  } finally {
    DB.prepare = realPrepare;
    console.error = err;
  }
  const row = DB.db.prepare("SELECT key, count FROM counters WHERE key LIKE 'errors|%'").get();
  assert.equal(row.count, 1);
  assert.match(row.key, /^errors\|\d{4}-\d{2}-\d{2}$/);
});

test("[ADMUX-16] Play's crash and ANR rates fill the G0 gates when the service account may read them", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const account = JSON.stringify({ client_email: "bamnat@example.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const asked = [];
  let denied = false;
  fakeFetch(t, async (url, init) => {
    if (url === "https://oauth2.googleapis.com/token") {
      const claims = JSON.parse(Buffer.from(decodeURIComponent(init.body.split("assertion=")[1]).split(".")[1], "base64url").toString());
      asked.push(claims.scope);
      return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }));
    }
    assert.match(url, /^https:\/\/playdeveloperreporting\.googleapis\.com\/v1beta1\/apps\/com\.bamnat\.app\/(crash|anr)RateMetricSet:query$/);
    if (denied) return new Response("{}", { status: 403 });
    const body = JSON.parse(init.body);
    assert.equal(body.timelineSpec.aggregationPeriod, "DAILY");
    assert.equal(body.timelineSpec.startTime.timeZone.id, "America/Los_Angeles");
    const crash = url.includes("crash");
    const metric = body.metrics[0];
    return new Response(JSON.stringify({ rows: [{ startTime: { year: 2026, month: 10, day: 22 },
      metrics: [{ metric, decimalValue: { value: crash ? "0.0042" : "0.0061" } }, { metric: "distinctUsers", decimalValue: { value: "812" } }] }] }));
  });
  const { call } = server({ PLAY_SERVICE_ACCOUNT: account });
  const owner = await signIn(call);
  const m = (await call("GET", "/api/admin/metrics", undefined, owner)).data;
  const g = Object.fromEntries(m.gates.filter((x) => x.gate === "G0").map((x) => [x.name, [x.value, x.status]]));
  assert.deepEqual(g["비정상 종료"], [0.42, "pass"]);
  assert.deepEqual(g.ANR, [0.61, "fail"]);
  assert.deepEqual(g["테스터"], [null, "play"]);
  assert.deepEqual(m.errors.play, { users: 812, day: "2026-10-22", error: null });
  assert.deepEqual(asked, ["https://www.googleapis.com/auth/playdeveloperreporting"]);
  // Without 앱 정보 보기 Play says 403: the gates say what to switch on.
  denied = true;
  const other = server({ PLAY_SERVICE_ACCOUNT: account });
  const o2 = await signIn(other.call);
  const d = (await other.call("GET", "/api/admin/metrics", undefined, o2)).data;
  assert.deepEqual(d.gates.filter((x) => x.gate === "G0").map((x) => x.status), ["denied", "denied", "play"]);
  assert.equal(d.errors.play.error, "denied");
});

test("[ADMUX-15] remote files: version, signature checked with the app's keys, and the share of phones that can take each", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const dir = new URL("../../../../remote/v1/", import.meta.url);
  const read = (name) => readFileSync(new URL(name, dir));
  fakeFetch(t, async (url) => {
    assert.ok(url.startsWith("https://ywjmoney.github.io/-/remote/v1/"), url);
    const name = url.split("/").pop();
    if (name.startsWith("alarm-guide")) return new Response("", { status: 404 });
    // The site serves a wages file someone changed after signing.
    if (name === "wages.json") return new Response(String(read(name)).replace("\"format\"", " \"format\""));
    // [LEDX-24] 1.32: merchants.json is published before its .sig2 (the remote key is kept off this project).
    try { return new Response(read(name)); } catch { return new Response("", { status: 404 }); }
  });
  const { call, DB } = server();
  const owner = await signIn(call);
  for (let i = 1; i <= 7; i++) {
    DB.db.prepare("INSERT INTO usage_days VALUES (?, '1.22.0', 10), (?, '1.23.0', 20), (?, '1.30.0', 10)").run(kst(NOW - i * DAY), kst(NOW - i * DAY), kst(NOW - i * DAY));
  }
  let r = (await call("GET", "/api/admin/remote", undefined, owner)).data;
  const f = Object.fromEntries(r.files.map((x) => [x.kind, x]));
  assert.equal(r.files.length, 10);
  assert.equal(f.merchants.valid, false, "not signed yet: the app keeps the dictionary it ships with");
  const rates = JSON.parse(read("rates.json"));
  assert.deepEqual([f.rates.version, f.rates.minApp, f.rates.valid], [rates.version, rates.minApp, true]);
  assert.equal(f.wages.valid, false, "a file changed after signing does not check out");
  assert.equal(f["alarm-guide"].found, false);
  // minApp 33: 1.22.0 (33) and 1.23.0 (34) can, 1.30.0 is a code the server does not know yet.
  assert.deepEqual([f.rates.reach, f.rates.unknown], [75, 70]);
  assert.equal(r.phones, 280);
  // A question that carried "1.30.0 (41)" teaches the server that code.
  DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES ('q000000000000000000002', 'd', 'bug', ?, 'open', ?, ?)")
    .run(JSON.stringify({ app: "1.30.0 (41)" }), NOW - DAY, NOW - DAY);
  r = (await call("GET", "/api/admin/remote?fresh=1", undefined, owner)).data;
  assert.equal(r.files.find((x) => x.kind === "rates").reach, 100);
});

test("[ADMUX-15] the server trusts the same remote keys as the app", () => {
  const kotlin = readFileSync(new URL("../../../core/src/main/kotlin/com/bamnat/core/remote/RemoteFile.kt", import.meta.url), "utf8");
  const block = kotlin.slice(kotlin.indexOf("object RemoteKeys"));
  assert.deepEqual([...block.matchAll(/"([A-Za-z0-9+/=]{44})"/g)].map((m) => m[1]), REMOTE.keys);
  const kinds = kotlin.match(/val KINDS = listOf\(([^)]*)\)/)[1].split(",").map((k) => k.trim());
  assert.equal(kinds.length, REMOTE.kinds.length);
});

test("[ADMUX-17] 받은함: everything waiting in this admin's areas, nearest deadline first, and 맡기 from there", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  const a = await call("POST", "/api/reports", report(1, { company: "가나다화학" }), from(1));
  const bug = await call("POST", "/api/inquiries", { v: 1, device: device(7), kind: "bug", text: "알람이 안 울려요", info: { app: "1.24.0 (35)" } }, from(7));
  const idea = await call("POST", "/api/inquiries", { v: 1, device: device(8), kind: "idea", text: "주 시작을 일요일로", info: { app: "1.24.0 (35)" } }, from(8));
  const post = await call("POST", "/api/posts", { device: device(3), company: a.data.company.id, setup: a.data.setup, body: "이상한 글" }, from(3));
  for (const n of [4, 5, 6]) await call("POST", `/api/posts/${post.data.id}/flag`, { device: device(n), reason: "abuse" }, from(n));
  const box = (await call("GET", "/api/admin/today", undefined, owner)).data.todo;
  const types = box.items.map((x) => x.type);
  // 문제 8시간, 제보 7일, 제안 72시간, 가려진 글 30일; the month's chores at its end.
  assert.deepEqual(types.filter((x) => x !== "ops"), ["inquiry", "inquiry", "group", "hidden_post"]);
  assert.equal(box.items[0].id, bug.data.id);
  assert.equal(box.items[1].id, idea.data.id);
  assert.equal(box.items.find((x) => x.type === "hidden_post").due, new Date(DB.db.prepare("SELECT hidden_at FROM posts").get().hidden_at + 30 * DAY).toISOString());
  assert.deepEqual([box.counts.today, box.counts.late], [1, 0]);
  // 맡기 from the list: the question is mine, and 받은함 says so.
  assert.equal((await call("POST", `/api/admin/inquiries/${bug.data.id}/assign`, { to: "me" }, owner)).status, 200);
  const mine = (await call("GET", "/api/admin/today", undefined, owner)).data.todo;
  assert.equal(mine.items.find((x) => x.id === bug.data.id).mine, true);
  assert.equal(mine.counts.mine, 1);
  // Eight hours on, the bug is late: the menu badge (overview) counts it.
  t.mock.timers.tick(9 * 3600e3);
  assert.equal((await adminHome(call, owner)).data.inboxLate, 1);
  // An admin with only 문의 sees only questions.
  const helper = await member(call, DB, owner, "help@example.com", { ...NONE, inquiries: "edit" });
  const theirs = (await call("GET", "/api/admin/today", undefined, helper)).data.todo;
  assert.deepEqual([...new Set(theirs.items.map((x) => x.type))], ["inquiry"]);
});

test("[ADMUX-18] 여러 판: the rule in use, a version waiting and an earlier one side by side; an earlier one back as the rule", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  // v1 adopted by three coworkers, then v2 (12%), then a third (15%) waits.
  const v1 = await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/reports", report(2), from(2));
  await call("POST", "/api/reports", report(6), from(6));
  const twelve = setup();
  twelve.pay.rules.extra[3].percent = 0.12;
  const v2 = await call("POST", "/api/reports", report(3, { setup: twelve }), from(3));
  await call("POST", `/api/admin/groups/${v2.data.company.id}/${v2.data.setup}/adopt`, {}, owner);
  const fifteen = setup();
  fifteen.pay.rules.extra[3].percent = 0.15;
  await call("POST", "/api/reports", report(5, { setup: fifteen }), from(5));
  // Three phones still use v1.
  for (const n of [11, 12, 13]) DB.db.prepare("INSERT INTO uses VALUES (?, ?, ?, ?)").run(v1.data.company.id, v1.data.setup, `use${n}`, NOW - DAY);
  let d = (await call("GET", "/api/admin/versions", undefined, owner)).data;
  assert.equal(d.conflicts, 1);
  assert.equal(d.items.length, 1);
  const states = d.items[0].candidates.map((c) => [c.state, c.version ?? null]);
  assert.deepEqual(states, [["active", 2], ["pending", null], ["previous", 1]]);
  const prev = d.items[0].candidates[2];
  assert.equal(prev.uses, 3);
  assert.ok(d.items[0].candidates[1].changes.added.some(([label, text]) => label === "대근" && /15%/.test(text)));
  assert.equal(d.items[0].candidates.filter((c) => c.recommended).length, 1);
  // The overview counts the department for the menu badge.
  assert.equal((await adminHome(call, owner)).data.versionConflicts, 1);
  // v1 back: a new v3 with v1's setup; v2 is an earlier one now; nothing is deleted.
  const c = await call("POST", `/api/admin/rules/${prev.rule}/promote`, {}, owner);
  assert.equal(c.status, 200);
  const live = c.data.rules.filter((r) => !r.superseded && !r.withdrawn);
  assert.deepEqual(live.map((r) => [r.version, r.source]), [[3, "promote"]]);
  assert.equal(c.data.rules.length, 3);
  const rules = DB.db.prepare("SELECT version, grp, superseded FROM rules ORDER BY version").all();
  assert.equal(rules[2].grp, v1.data.setup);
  assert.deepEqual(rules.map((r) => r.superseded), [1, 1, 0]);
  assert.equal(DB.db.prepare("SELECT action FROM audit WHERE action = 'rule_promote'").get().action, "rule_promote");
  // The phone on v2 hears of the new version through 광장 소식; the ones on v1 already have its setup.
  DB.db.prepare("INSERT INTO uses VALUES (?, ?, ?, ?)").run(v2.data.company.id, v2.data.setup, "a".repeat(64), NOW - DAY);
  // Only an earlier version can come back, and only with 광장 규칙 내리기.
  const active = DB.db.prepare("SELECT id FROM rules WHERE superseded = 0").get().id;
  assert.equal((await call("POST", `/api/admin/rules/${active}/promote`, {}, owner)).status, 409);
  const reviewer = await member(call, DB, owner, "rev@example.com", { ...NONE, companies: "edit", review: "edit" });
  const old = DB.db.prepare("SELECT id FROM rules WHERE version = 2").get().id;
  assert.equal((await call("POST", `/api/admin/rules/${old}/promote`, {}, reviewer)).status, 403);
  d = (await call("GET", "/api/admin/versions?show=history", undefined, owner)).data;
  assert.equal(d.show, "history");
});

test("[ADMUX-18] the Wilson share does not jump on two votes", () => {
  assert.equal(wilson(0, 0), 0);
  assert.ok(wilson(2, 2) < wilson(40, 50), "2 of 2 is less sure than 40 of 50");
  assert.equal(wilson(40, 50), 67);
});

test("[ADMUX-19] 광장 일괄: many posts and 임금표 entries at once, each as if alone, and only within one's permission", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  const owner = await signIn(call);
  const a = await call("POST", "/api/reports", report(1), from(1));
  const say = async (n, body) => (await call("POST", "/api/posts", { device: device(n), company: a.data.company.id, setup: a.data.setup, body }, from(n))).data.id;
  const ids = [await say(2, "첫 글이에요"), await say(3, "둘째 글이에요"), await say(4, "셋째 글이에요")];
  for (const id of ids) await call("POST", `/api/posts/${id}/flag`, { device: device(9), reason: "spam" }, from(9));
  let r = await call("POST", "/api/admin/posts/bulk", { ids: ids.slice(0, 2), action: "hide", reason: "spam" }, owner);
  assert.deepEqual([r.status, r.data.done.length], [200, 2]);
  assert.deepEqual(DB.db.prepare("SELECT status FROM posts ORDER BY created_at, rowid").all().map((p) => p.status), ["hidden", "hidden", "shown"]);
  r = await call("POST", "/api/admin/posts/bulk", { ids: [...ids, "pAAAAAAAAAAAAAAA"], action: "show" }, owner);
  assert.deepEqual([r.data.done.length, r.data.skipped], [3, [{ id: "pAAAAAAAAAAAAAAA", error: "not_found" }]]);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM post_flags").get().n, 0);
  r = await call("POST", "/api/admin/posts/bulk", { ids: [ids[2]], action: "delete" }, owner);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM posts").get().n, 2);
  assert.equal((await call("POST", "/api/admin/posts/bulk", { ids: [], action: "show" }, owner)).status, 400);
  assert.equal((await call("POST", "/api/admin/posts/bulk", { ids: [ids[0]], action: "nuke" }, owner)).status, 400);
  assert.ok(DB.db.prepare("SELECT 1 FROM audit WHERE action = 'post_bulk'").get());
  // 임금표: adopt two, then a 토론 고치기 admin without 광장 규칙 내리기 cannot hide an adopted one.
  const rate = async (n, trade) => (await call("POST", "/api/rates", { device: device(n), job: "construction", region: "31", trade, unit: "day", amount: 250000 }, from(n))).data.id;
  const rates = [await rate(20, "형틀목수"), await rate(21, "철근공")];
  r = await call("POST", "/api/admin/rates/bulk", { ids: rates, action: "adopted" }, owner);
  assert.deepEqual(r.data.done, rates);
  assert.deepEqual(DB.db.prepare("SELECT status FROM rates").all().map((x) => x.status), ["adopted", "adopted"]);
  const mod = await member(call, DB, owner, "mod@example.com", { ...NONE, community: "edit" });
  r = await call("POST", "/api/admin/rates/bulk", { ids: rates, action: "hidden" }, mod);
  assert.deepEqual([r.data.done.length, r.data.skipped.map((x) => x.error)], [0, ["no_permission", "no_permission"]]);
  assert.equal((await call("POST", "/api/admin/posts/bulk", { ids: [ids[0]], action: "show" }, await member(call, DB, owner, "view@example.com", { ...NONE, community: "view" }))).status, 403);
});
