// 1.39 [OBS2-08] 유형별 흐름·기능과 머묾 (사용자 2026-11-08 관측 데이터 카드 '유형별·기능 둘만', 처리방침 3.12 1-9항,
// 깃발 stats.kind·stats.keep). 시행일 전에는 서버가 받지 않고, 뒤에는 날짜·판·방식(또는 기능·창)별 휴대폰 수만 쌓아요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, from, signIn } from "./helpers.mjs";
import { keepRows, kindOf } from "../src/usage.js";
import { obsGaps, obsKeep, obsKinds, obsRules } from "../src/observe.js";

const DAY = 86400000;
const bundle = (day, events, extra = {}) => ({ v: 1, app: "1.39.0", days: [{ day, events, recent: [] }], ...extra });
const list = (end, n) => Array.from({ length: n }, (_, i) => new Date(Date.parse(`${end}T00:00:00Z`) - (n - 1 - i) * DAY).toISOString().slice(0, 10));

async function policy312(DB, effective = "2026-10-01") {
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.12": { posted: "2026-09-01", effective, heldUntil: null, unfavourable: false } })).run();
}

test("[OBS2-08] the bundle's style is one of the six kinds; keep rows take only real feature names, windows and days", () => {
  assert.equal(kindOf("SHIFT"), "SHIFT");
  assert.equal(kindOf("shift"), null);
  assert.equal(kindOf({}), null);
  const now = Date.parse("2026-11-08T03:00:00Z");
  assert.deepEqual(keepRows([
    { day: "2026-11-05", win: 7, events: ["open", "pay_view", "pay_view", "life_new", "nope"] },
    { day: "2026-10-20", win: 30, events: ["backup"] },
    { day: "2026-09-01", win: 7, events: ["open"] }, // older than 40 days
    { day: "2026-11-05", win: 14, events: ["open"] }, // not a window
    { day: "11/05", win: 7, events: ["open"] },
    "x",
  ], now), [["2026-11-05", 7, "open"], ["2026-11-05", 7, "pay_view"], ["2026-10-20", 30, "backup"]]);
  assert.deepEqual(keepRows("no"), []);
});

test("[OBS2-08] 유형별 흐름 and 기능과 머묾 are read like 머묾: the window must be over, and 'open' is everyone that day", () => {
  const days = list("2026-11-08", 61);
  const at = (i) => days[i];
  // Two kinds. SHIFT: 10 new on day 0, 8 back on day 7. DAY: 10 new on day 0, 2 back on day 7.
  const k = obsKinds([
    { day: at(0), style: "SHIFT", event: "life_new", phones: 10 }, { day: at(7), style: "SHIFT", event: "life_d7", phones: 8 },
    { day: at(0), style: "DAY", event: "life_new", phones: 10 }, { day: at(7), style: "DAY", event: "life_d7", phones: 2 },
  ], days);
  assert.deepEqual(k.rows.map((r) => [r.name, r.new, r.d7.rate]), [["교대", 10, 80], ["일근", 10, 20]]);
  assert.deepEqual(k.all.d7, { rate: 50, base: 20, kept: 10 });

  // Day 40 (its 7-day window is over): 100 phones opened, 40 came back in a week; 30 used 급여 and 21 of them came back.
  // Day 58 is in the last week: its window is not over, so it is left out.
  const use = [{ day: at(40), event: "open", phones: 100 }, { day: at(40), event: "pay_view", phones: 30 }, { day: at(40), event: "backup", phones: 5 },
    { day: at(58), event: "pay_view", phones: 50 }, { day: at(40), event: "life_new", phones: 9 }];
  const back = [{ day: at(40), event: "open", win: 7, phones: 40 }, { day: at(40), event: "pay_view", win: 7, phones: 21 },
    { day: at(58), event: "pay_view", win: 7, phones: 50 }];
  const kp = obsKeep(use, back, days, null, { pay_view: "급여 보기" });
  assert.deepEqual(kp.base.rate7, 40);
  assert.deepEqual(kp.rows.map((r) => [r.name, r.users, r.rate7, r.lift7]), [["급여 보기", 30, 70, 30]]);
  assert.equal(kp.small, 1, "backup had 5 phones: too few to show");
  // Before the flag was on nothing counts.
  assert.equal(obsKeep(use, back, days, at(50)).rows.length, 0);

  // A kind well under everyone's 7th day is a 개선 신호 (needs 30 phones on both sides).
  const many = obsKinds([
    { day: at(0), style: "SHIFT", event: "life_new", phones: 60 }, { day: at(7), style: "SHIFT", event: "life_d7", phones: 36 },
    { day: at(0), style: "HOURLY", event: "life_new", phones: 40 }, { day: at(7), style: "HOURLY", event: "life_d7", phones: 4 },
  ], days);
  const found = obsRules({ kinds: many }).filter((f) => f.kind === "kind-keep-low" && f.on);
  assert.deepEqual(found.map((f) => [f.subject, f.value, f.base]), [["HOURLY", 10, 40]]);
  assert.match(found[0].dev, /알바 유형 첫 주/);

  // 관측 빈틈: before the flags, where they come from; after, only while there are too few.
  const before = obsGaps({ sample: { daily: 50, lifeOn: true }, kinds: null, keep: null });
  assert.match(before.find((g) => g.key === "by-type").need, /3\.12/);
  assert.match(before.find((g) => g.key === "feature-keep").need, /3\.12/);
  assert.deepEqual(before.filter((g) => g.policy).map((g) => g.key), ["why-leave"]);
  const after = obsGaps({ sample: { daily: 50, lifeOn: true }, kinds: many, keep: kp });
  assert.ok(!after.some((g) => g.key === "by-type" || g.key === "feature-keep"));
});

test("[OBS2-08] the server drops style and keep before 처리방침 3.12 and counts them after; the 관측 판 reads them", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-08T03:00:00Z") });
  const { call, DB } = server();
  const extra = { style: "SHIFT", keep: [{ day: "2026-11-05", win: 7, events: ["open", "pay_view"] }] };
  assert.equal((await call("POST", "/api/events", bundle("2026-11-07", { open: 1, life_d7: 1 }, extra), from(1))).status, 204);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_kinds").get().n, 0, "before 3.12");
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_keep").get().n, 0, "before 3.12");
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_events WHERE event = 'open'").get().n, 1, "the rest still counts");

  await policy312(DB);
  for (let i = 0; i < 2; i++) {
    assert.equal((await call("POST", "/api/events", bundle("2026-11-07", { open: 2 + i, life_d7: 1, pay_view: 1 }, extra), from(10 + i))).status, 204);
  }
  await call("POST", "/api/events", bundle("2026-11-07", { open: 9 }, { style: "nope", keep: [{ day: "2026-11-05", win: 9, events: ["open"] }] }), from(30));
  assert.deepEqual(DB.db.prepare("SELECT day, style, event, phones FROM usage_kinds ORDER BY event").all().map((r) => ({ ...r })),
    [{ day: "2026-11-07", style: "SHIFT", event: "life_d7", phones: 2 }, { day: "2026-11-07", style: "SHIFT", event: "open", phones: 2 }],
    "only the 진입·머묾 marks and open, never 급여");
  assert.deepEqual(DB.db.prepare("SELECT day, event, win, phones FROM usage_keep ORDER BY event").all().map((r) => ({ ...r })),
    [{ day: "2026-11-05", event: "open", win: 7, phones: 2 }, { day: "2026-11-05", event: "pay_view", win: 7, phones: 2 }]);

  const admin = await signIn(call);
  const o = (await call("GET", "/api/admin/observe", undefined, admin)).data;
  assert.deepEqual(o.kinds.rows.map((r) => r.style), ["SHIFT"]);
  assert.ok(Array.isArray(o.keep.rows), "기능과 머묾 is there once stats.keep is on");
  assert.ok(o.gaps.some((g) => g.key === "feature-keep" && /적어요/.test(g.title)), "a window not over yet reads as too few");
});
