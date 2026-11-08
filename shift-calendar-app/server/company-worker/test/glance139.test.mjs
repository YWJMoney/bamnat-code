// 1.39 [STAT2-01~05] 숫자 › 한눈에(사용자 2026-11-08 "통계쪽 혁신 … 정상도 사용자 이탈 회복 진입"): 정상도 신호, 진입·머묾·회복
// (life_*, 깃발 stats.life = 처리방침 3.12), 초기 모드, 서버 배포 자동 기록. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, from, signIn } from "./helpers.mjs";
import { healthSignals, retention } from "../src/glance.js";

const bundle = (day, events, app = "1.39.0") => ({ v: 1, app, days: [{ day, events, recent: [] }] });

async function policy312(DB, effective = "2026-11-01") {
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.12": { posted: "2026-10-01", effective, heldUntil: null, unfavourable: false } })).run();
}

test("[STAT2-01] health signals: nothing wrong is ok, Play's line is bad, the inner line is warn, bad sorts first", () => {
  assert.deepEqual(healthSignals({}), { tone: "ok", signals: [] });
  const h = healthSignals({
    vitals: { crash: 0.006, anr: 0.005 }, // 0.6% (inner line 0.5%), 0.5% (Play's 0.47%)
    speed: { total: { n: 1000, err: 3, p95: 1800 } },
    jobs: [{ state: "ok" }, { state: "stale" }],
    newErrors: 2, bugs: { now: 4, before: 1 }, lowReviews: 1,
  });
  assert.equal(h.tone, "bad");
  assert.equal(h.signals[0].key, "anr");
  assert.equal(h.signals[0].tone, "bad");
  const keys = h.signals.map((s) => s.key);
  for (const k of ["crash", "speed", "jobs", "newErrors", "bugs", "reviews"]) assert.ok(keys.includes(k), k);
  assert.equal(h.signals.find((s) => s.key === "crash").tone, "warn");
  assert.ok(!keys.includes("errors"), "0.3% of requests is under the 0.5% line");
  // Play's numbers missing or refused say nothing either way; an open incident is always bad.
  assert.equal(healthSignals({ vitals: { error: "denied" } }).tone, "ok");
  assert.equal(healthSignals({ incident: true }).signals[0].to, "#/incident");
});

test("[STAT2-02] retention counts only days whose N-th day is in the window, and never more than came", () => {
  const list = ["2026-11-01", "2026-11-02", "2026-11-03"];
  const per = {
    life_new: new Map([["2026-11-01", 10], ["2026-11-02", 4], ["2026-11-03", 7]]),
    life_d1: new Map([["2026-11-02", 6], ["2026-11-03", 9]]),
  };
  // 11-03's new phones have no day 1 yet; 11-02's day 1 has 9 but only 4 came, so 4.
  assert.deepEqual(retention(list, per, 1), { rate: 71.4, base: 14, kept: 10 });
  assert.deepEqual(retention(list, { life_new: new Map() }, 7), { rate: null, base: 0, kept: 0 });
});

test("[STAT2-02] life_* counts are dropped before 처리방침 3.12 and read as 진입·머묾·회복 after", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-08T03:00:00Z") });
  const { call, DB } = server();
  assert.equal((await call("POST", "/api/events", bundle("2026-11-05", { open: 1, life_new: 1 }), from(1))).status, 204);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_events WHERE event LIKE 'life_%'").get().n, 0, "before 3.12");
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_events WHERE event = 'open'").get().n, 1, "the rest still counts");
  await policy312(DB);
  // Three phones came on 11-05, two opened again the next day; one came back after a week on 11-07.
  // The same bundle twice in a day is counted once ([STAT-05]), so each phone opened the app a different number of times.
  for (let i = 0; i < 3; i++) await call("POST", "/api/events", bundle("2026-11-05", { open: 2 + i, life_new: 1 }), from(10 + i));
  for (let i = 0; i < 2; i++) await call("POST", "/api/events", bundle("2026-11-06", { open: 5 + i, life_d1: 1 }), from(20 + i));
  await call("POST", "/api/events", bundle("2026-11-07", { open: 9, life_back7: 1 }), from(30));
  const admin = await signIn(call);
  const d = (await call("GET", "/api/admin/glance", undefined, admin)).data;
  assert.equal(d.flow.lifeOn, true);
  assert.equal(d.flow.totals.new, 3);
  assert.deepEqual(d.flow.keep.d1, { rate: 66.7, base: 3, kept: 2 });
  assert.equal(d.flow.keep.d30.rate, null);
  assert.equal(d.flow.churn, null, "no 30th day yet, so no churn guess");
  assert.equal(d.flow.totals.back7, 1);
  assert.equal(d.days.length, 28);
  assert.equal(d.flow.active.length, 28);
  // Few phones: 초기 모드 puts Play users, reviews and inquiries first.
  assert.ok(d.early);
  assert.equal(typeof d.early.inquiries, "number");
  assert.deepEqual(Object.keys(d.health).sort(), ["bugs", "server", "signals", "tone", "vitals"]);
  // The 지표 area reads it; a phone's events are never listed one by one ('최근 28일' leaves life_* out).
  const m = (await call("GET", "/api/admin/metrics?days=7", undefined, admin)).data;
  assert.ok(!(m.recent ?? []).some((r) => String(r.event ?? r.key ?? r).startsWith("life_")));
});

test("[STAT2-05] a new server version is written once to 활동 기록 and shows in 사고's '바꾼 것'", async () => {
  const { call, DB } = server();
  await call("GET", "/api/config");
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM audit WHERE action = 'server_deployed'").get().n, 0, "a new database is not a deploy");
  // The next isolate after a deploy sees a different version than the one kept.
  await DB.prepare("UPDATE settings SET value = 'old' WHERE key IN ('schema', 'server_version')").run();
  const { default: worker } = await import("../src/index.js");
  // A fresh isolate: the schema check is kept per database handle.
  const env = { DB: Object.create(DB), RATE_SALT: "test-salt" };
  await worker.fetch(new Request("https://company.example/api/config"), env);
  await worker.fetch(new Request("https://company.example/api/config"), env);
  const rows = DB.db.prepare("SELECT detail FROM audit WHERE action = 'server_deployed'").all();
  assert.equal(rows.length, 1);
  assert.match(rows[0].detail, /old/);
  const { changeMarks } = await import("../src/release-desk.js");
  const marks = await changeMarks({ DB, RATE_SALT: "test-salt" }, Date.now() - 3600e3, Date.now() + 1000);
  assert.ok(marks.some((m) => m.kind === "server_deployed" && m.text.startsWith("서버 배포")));
});
