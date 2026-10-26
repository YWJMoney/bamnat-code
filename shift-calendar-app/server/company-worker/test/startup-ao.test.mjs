// AO 1.28 [PERF-16][PERF-17] Startup times in the usage bundle: taken only as bucket edges, a wrong part left out
// (the day's counts still count), summed per day, app version, kind and step, and read back in 지표 as the middle
// value and the 90th percentile per version. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { STARTUP_BUCKETS, startupRows } from "../src/index.js";
import { server, from, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-10-22T03:00:00Z");
const bundle = (startup, extra = {}) => ({ v: 1, app: "1.28.0", days: [{ day: "2026-10-21", events: { open: 2 }, recent: [] }], startup, ...extra });

test("[PERF-17] the app's buckets and the server's are the same list", async () => {
  const { readFileSync } = await import("node:fs");
  const kotlin = readFileSync(new URL("../../../app/src/main/java/com/bamnat/app/diag/StartupStats.kt", import.meta.url), "utf8");
  const list = kotlin.match(/BUCKETS = longArrayOf\(([^)]*)\)/)[1].split(",").map((x) => Number(x.trim().replaceAll("_", "")));
  assert.deepEqual(list, STARTUP_BUCKETS);
});

test("[PERF-17] only bucket edges of known steps are taken", () => {
  assert.deepEqual(startupRows({ container: 300, activity: 200, firstMonth: 1500, warm: { container: 100, firstMonth: 300, activity: 100 } }), [
    ["cold", "container", 300], ["cold", "activity", 200], ["cold", "firstMonth", 1500],
    ["warm", "container", 100], ["warm", "firstMonth", 300],
  ]);
  // An exact time, a string, a negative number or an unknown step is left out; the rest stays.
  assert.deepEqual(startupRows({ container: 1234, activity: "200", firstMonth: 2000, cpu: 100, warm: [300] }), [["cold", "firstMonth", 2000]]);
  assert.deepEqual(startupRows(null), []);
  assert.deepEqual(startupRows([1, 2]), []);
  assert.deepEqual(startupRows({ firstMonth: -100 }), []);
});

test("[PERF-17] startup times come in with the day's counts and go out as middle values per version", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  // Without a startup part (no yes to 3.2, an older app) the bundle counts as before.
  assert.equal((await call("POST", "/api/events", bundle(undefined), from(1))).status, 204);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_startup").get().n, 0);
  // A wrong startup part never refuses the counts.
  assert.equal((await call("POST", "/api/events", bundle("fast"), from(2))).status, 204);
  assert.equal((await call("POST", "/api/events", bundle({ firstMonth: 1234 }), from(3))).status, 204);
  assert.equal(DB.db.prepare("SELECT SUM(phones) AS n FROM usage_days").get().n, 3);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM usage_startup").get().n, 0);
  // 10 phones: first month 1000 ms for 5, 1500 for 4, 6000 for 1.
  const times = [1000, 1000, 1000, 1000, 1000, 1500, 1500, 1500, 1500, 6000];
  for (const [i, ms] of times.entries()) {
    const r = await call("POST", "/api/events", bundle({ container: 100, activity: 200, firstMonth: ms, warm: { container: 100, firstMonth: 300 } },
      { days: [{ day: "2026-10-20", events: { open: 1 } }, { day: "2026-10-21", events: { open: i + 1 } }] }), from(10 + i));
    assert.equal(r.status, 204);
  }
  // Counted once per bundle, on its newest day; nothing ties a row to a phone.
  const rows = DB.db.prepare("SELECT day, app, kind, step, bucket, phones FROM usage_startup WHERE step = 'firstMonth' AND kind = 'cold' ORDER BY bucket").all().map((r) => ({ ...r }));
  assert.deepEqual(rows, [
    { day: "2026-10-21", app: "1.28.0", kind: "cold", step: "firstMonth", bucket: 1000, phones: 5 },
    { day: "2026-10-21", app: "1.28.0", kind: "cold", step: "firstMonth", bucket: 1500, phones: 4 },
    { day: "2026-10-21", app: "1.28.0", kind: "cold", step: "firstMonth", bucket: 6000, phones: 1 },
  ]);
  assert.ok(!DB.dump().includes("198.51.100"));
  // An older version's phones too.
  await call("POST", "/api/events", bundle({ firstMonth: 3000 }, { app: "1.27.0" }), from(40));
  const admin = await signIn(call);
  const m = (await call("GET", "/api/admin/metrics?days=7", undefined, admin)).data;
  assert.deepEqual(m.startup.versions.map((v) => v.app), ["1.28.0", "1.27.0"]);
  const v = m.startup.versions[0];
  assert.deepEqual(v.cold.firstMonth, { phones: 10, p50: 1000, p90: 1500 });
  assert.deepEqual(v.cold.container, { phones: 10, p50: 100, p90: 100 });
  assert.deepEqual(v.warm.firstMonth, { phones: 10, p50: 300, p90: 300 });
  assert.equal(v.warm.activity, undefined);
  assert.deepEqual(m.startup.versions[1].cold, { firstMonth: { phones: 1, p50: 3000, p90: 3000 } });
  assert.equal(m.startup.few, 30);
});

test("[PERF-17] startup counts go after 400 days with the rest", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB, env } = server({ plaza: true });
  await call("POST", "/api/events", bundle({ firstMonth: 1000 }), from(1));
  DB.db.prepare("INSERT INTO usage_startup VALUES ('2025-09-01', '1.13.0', 'cold', 'firstMonth', 1000, 3)").run();
  // The daily clean-up runs with the scheduled event.
  const { default: worker } = await import("../src/index.js");
  await worker.scheduled({}, env, { waitUntil: (p) => p });
  await new Promise((r) => setTimeout(r, 50));
  const days = DB.db.prepare("SELECT DISTINCT day FROM usage_startup").all().map((r) => r.day);
  assert.deepEqual(days, ["2026-10-21"]);
});
