// 1.40 [DISC-06] 안 써 본 기능 알려 주기의 합계 4가지(사용자 결정 2026-11-11 '합계 4가지만'): disc_*는 깃발 stats.disc(처리방침 3.13)가
// 켜지기 전에는 버리고, 그 뒤에는 다른 사용 통계처럼 날마다 횟수로만 받아요. 어떤 기능을 알렸는지는 받지 않아요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, from } from "./helpers.mjs";
import { EVENTS } from "../src/usage.js";

const bundle = (day, events, app = "1.40.0") => ({ v: 1, app, days: [{ day, events, recent: [] }] });

async function policy313(DB, effective = "2026-11-01") {
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.13": { posted: "2026-10-01", effective, heldUntil: null, unfavourable: false } })).run();
}

test("[DISC-06] the four totals have names, and no feature names come with them", () => {
  for (const k of ["disc_shown", "disc_try", "disc_later", "disc_off"]) assert.ok(EVENTS[k], k);
  assert.equal(Object.keys(EVENTS).filter((k) => k.startsWith("disc_")).length, 4);
});

test("[DISC-06] disc_* are dropped before 처리방침 3.13 and counted after", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  const { call, DB } = server();
  assert.equal((await call("POST", "/api/events", bundle("2026-11-18", { open: 1, disc_shown: 1 }), from(1))).status, 204);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_events WHERE event LIKE 'disc_%'").get().n, 0, "before 3.13");
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_events WHERE event = 'open'").get().n, 1, "the rest still counts");
  await policy313(DB);
  await call("POST", "/api/events", bundle("2026-11-19", { open: 2, disc_shown: 1, disc_try: 1, disc_unknown_feature: 1 }), from(2));
  const names = DB.db.prepare("SELECT DISTINCT event FROM usage_events WHERE event LIKE 'disc_%' ORDER BY event").all().map((r) => r.event);
  assert.deepEqual(names, ["disc_shown", "disc_try"]);
});

test("[DISC-06] 그만 보기 over 5% or 열어 봄 under 3% of 50+ phones becomes an 개선 신호; fewer phones say nothing", async () => {
  const { obsDisc, obsRules } = await import("../src/observe.js");
  const rows = (shown, tried, off) => [
    { day: "2026-11-18", event: "disc_shown", phones: shown }, { day: "2026-11-18", event: "disc_try", phones: tried },
    { day: "2026-11-18", event: "disc_off", phones: off }, { day: "2026-11-18", event: "open", phones: 999 },
  ];
  assert.equal(obsDisc([{ day: "2026-11-18", event: "open", phones: 3 }]), null);
  const bad = obsDisc(rows(100, 2, 8));
  assert.deepEqual(bad, { shown: 100, tried: 2, later: 0, off: 8, tryRate: 2, offRate: 8 });
  const found = obsRules({ disc: bad });
  assert.equal(found.find((f) => f.kind === "disc-off-high").on, true);
  assert.equal(found.find((f) => f.kind === "disc-try-low").on, true);
  const fine = obsRules({ disc: obsDisc(rows(100, 20, 2)) });
  assert.ok(fine.filter((f) => f.kind.startsWith("disc-")).every((f) => !f.on));
  assert.equal(obsRules({ disc: obsDisc(rows(40, 0, 10)) }).filter((f) => f.kind.startsWith("disc-")).length, 0, "too few phones");
});
