// 1.39 [SETUP-20] (사용자 결정 2026-11-07 '추천대로', 발굴 G6 4-1): 첫 설정 누른 횟수 in the usage bundle, only once 처리방침 3.12 is
// in force (the stats.setup row's date), as one bucket edge per kind of work, read back in 지표. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, from, signIn } from "./helpers.mjs";

// The same bundle twice in a day is counted once ([STAT-05]), so each phone opened the app a different number of times.
const bundle = (setup, opens = 1, day = "2026-11-20", app = "1.39.0") => ({ v: 1, app, days: [{ day, events: { open: opens }, recent: [] }], setup });

async function policy312(DB, effective = "2026-11-15") {
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.12": { posted: "2026-10-15", effective, heldUntil: null, unfavourable: false } })).run();
}

test("[SETUP-20] setup rows take only a known kind of work and a bucket edge", async () => {
  const { setupRows } = await import("../src/index.js");
  assert.deepEqual(setupRows({ style: "SHIFT", taps: 20 }), [["setup", "SHIFT", 20]]);
  assert.deepEqual(setupRows({ style: "SHIFT", taps: 17 }), []);
  assert.deepEqual(setupRows({ style: "NURSE", taps: 20 }), []);
  assert.deepEqual(setupRows({ style: "DAY", taps: "20" }), []);
  assert.deepEqual(setupRows(null), []);
  assert.deepEqual(setupRows([1]), []);
});

test("[SETUP-20] setup taps are left out before 처리방침 3.12 is in force and counted per kind after, read back in 지표", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-21T03:00:00Z") });
  const { call, DB } = server();
  assert.equal((await call("POST", "/api/events", bundle({ style: "SHIFT", taps: 20 }), from(1))).status, 204);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_startup WHERE kind = 'setup'").get().n, 0, "before 3.12");
  await policy312(DB);
  const sends = [["SHIFT", 20], ["SHIFT", 20], ["SHIFT", 40], ["BUSINESS", 10]];
  for (let i = 0; i < sends.length; i++) {
    const [style, taps] = sends[i];
    assert.equal((await call("POST", "/api/events", bundle({ style, taps }, 2 + i), from(10 + i))).status, 204);
  }
  // A wrong part is left out, the rest of the bundle counts.
  assert.equal((await call("POST", "/api/events", bundle({ style: "SHIFT", taps: 13 }, 9), from(20))).status, 204);
  const rows = DB.db.prepare("SELECT step, bucket, phones FROM usage_startup WHERE kind = 'setup' ORDER BY step, bucket").all().map((r) => ({ ...r }));
  assert.deepEqual(rows, [
    { step: "BUSINESS", bucket: 10, phones: 1 },
    { step: "SHIFT", bucket: 20, phones: 2 },
    { step: "SHIFT", bucket: 40, phones: 1 },
  ]);
  const admin = await signIn(call);
  const v = (await call("GET", "/api/admin/metrics?days=7", undefined, admin)).data.startup.versions.find((x) => x.app === "1.39.0");
  assert.deepEqual(v.setup.SHIFT, { phones: 3, p50: 20, p90: 40 });
  assert.deepEqual(v.setup.BUSINESS, { phones: 1, p50: 10, p90: 10 });
});
