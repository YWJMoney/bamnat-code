// 2.0.0 [RVL-90] (검토 I12c-03 '멈춤 신호를 문의 없이 개발로', 처리방침 3.14 문장표 6, 1-9항 ⑩, 깃발 stats.crash): 사용 통계를 켠
// 휴대폰이 앱이 멈춘 곳(밤낮 코드 위 다섯 줄·줄인 값·종류·하루 횟수)을 실으면, 3.14 시행일부터만 날짜·판·위치별로 더하고, 새 판에서만 생긴
// 멈춘 곳이 관측 판 개선 신호가 돼요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, from, signIn } from "./helpers.mjs";
import { CRASH_MARKS, crashMarkRows, crashSig } from "../src/usage.js";
import { obsNewCrashes } from "../src/observe.js";

const bundle = (app, day, crash) => ({ v: 1, app, days: [{ day, events: { open: 1 }, recent: [] }], crash });
const mark = (day, at, kind = "crash", n = 1) => ({ day, kind, sig: crashSig(kind, at), at, n });

async function policy314(DB) {
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.14": { posted: "2026-10-01", effective: "2026-11-01", heldUntil: null, unfavourable: false } })).run();
}

test("[RVL-90] the place's value is the app's own (core CrashMarks.sig), and only code names, five lines, five places a day pass", () => {
  // The same value core CrashMarksTest pins on the phone side.
  assert.equal(crashSig("crash", ["PayViewModel.save:212", "a.b.c.d:12"]), "261ed75a");
  const now = Date.parse("2026-12-22T03:00:00Z");
  const ok = mark("2026-12-21", ["PayViewModel.save:212", "a.b.c.d:12"]);
  assert.deepEqual(crashMarkRows([ok], now), [["2026-12-21", "crash", ok.sig, "PayViewModel.save:212\na.b.c.d:12", 1]]);
  // A message, a space or a sixth line, a value the lines do not give, a kind or a day it may not be: left out.
  assert.deepEqual(crashMarkRows([
    mark("2026-12-21", ["boom: 010-1234-5678"]),
    mark("2026-12-21", ["a b"]),
    mark("2026-12-21", ["a.b:1", "a.b:2", "a.b:3", "a.b:4", "a.b:5", "a.b:6"]),
    { ...ok, sig: "00000000" },
    { ...ok, kind: "oom", sig: crashSig("oom", ok.at) },
    { ...ok, day: "2026-11-01" },
    { ...ok, n: 0 },
    { ...ok, n: CRASH_MARKS.count + 1 },
  ], now), []);
  const many = Array.from({ length: 8 }, (_, i) => mark("2026-12-21", [`a.b.c:${i}`]));
  assert.equal(crashMarkRows(many, now).length, CRASH_MARKS.perDay);
});

test("[RVL-90] before 처리방침 3.14 nothing of it is kept; after it, by day·판·place with phones and times, and a new 판's place is a signal", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-22T03:00:00Z") });
  const { call, DB } = server();
  const at = ["PayViewModel.save:212", "PayScreen.body:40"];
  assert.equal((await call("POST", "/api/events", bundle("2.0.0", "2026-12-21", [mark("2026-12-21", at)]), from(1))).status, 204);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_crash").get().n, 0, "before 3.14");
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_events WHERE event = 'open'").get().n, 1, "the rest still counts");

  await policy314(DB);
  // 2.0.0 had one place; 2.0.1 brings a new one on three phones and the old one again.
  await call("POST", "/api/events", bundle("2.0.0", "2026-12-20", [mark("2026-12-20", at, "crash", 2)]), from(2));
  for (let i = 0; i < 3; i++) {
    const r = await call("POST", "/api/events", bundle("2.0.1", "2026-12-21", [mark("2026-12-21", ["a.b.c.d:7", "e.f:2"], "anr", 1 + i), mark("2026-12-21", at)]), from(10 + i));
    assert.equal(r.status, 204);
  }
  assert.deepEqual(DB.db.prepare("SELECT app, kind, phones, total FROM usage_crash ORDER BY app, kind").all().map((r) => ({ ...r })), [
    { app: "2.0.0", kind: "crash", phones: 1, total: 2 },
    { app: "2.0.1", kind: "anr", phones: 3, total: 6 },
    { app: "2.0.1", kind: "crash", phones: 3, total: 3 },
  ]);

  const admin = await signIn(call);
  const o = (await call("GET", "/api/admin/observe", undefined, admin)).data;
  assert.deepEqual(o.newCrashes.map((c) => [c.app, c.kind, c.top, c.phones, c.before]), [["2.0.1", "anr", "a.b.c.d:7", 3, true]],
    "the place 2.0.0 had too is not new");
  const s = o.found.find((f) => f.kind === "crash-new");
  assert.ok(s?.on, "three phones make it a signal");
  assert.match(s.title, /새 판에서 생긴 응답 없음: 2\.0\.1 a\.b\.c\.d:7 · 3대 6번/);
});

test("[RVL-90] without an older 판's record the signal says it can't tell new from old", () => {
  const now = Date.parse("2026-12-22T03:00:00Z");
  const rows = [{ app: "2.0.0", kind: "crash", sig: "1234abcd", at: "X.y:1\nX.z:2", phones: 4, total: 9, first: "2026-12-21", last: "2026-12-21" }];
  assert.deepEqual(obsNewCrashes(rows, now).map((c) => [c.top, c.at.length, c.before]), [["X.y:1", 2, false]]);
  assert.deepEqual(obsNewCrashes([], now), []);
});
