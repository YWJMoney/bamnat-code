// 2.0.0 [RVD-20] HQ-08 하루 두 구간 근무(결정 19번, 설계 4-4절, 갈래 H #451): a company setup keeps a shift's gap and its own break, and an app
// before 2.0.0 still reads "break" (the break and the gap together). The file stays v4. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { cleanSetup, readReport, fingerprint, describe as words } from "../src/index.js";
import { server, setup, report, from } from "./helpers.mjs";

/** The test setup with 점심, 11:00~15:00 and 17:00~21:00, a 30-minute break from 13:00, as H's CompanyShare writes it. */
function twoParts(change = (t) => t) {
  const s = setup();
  s.rotation.steps[0] = "점심";
  s.shiftTypes.push(change({ name: "점심", label: "점", color: 4280391411, start: "11:00", end: "21:00", break: 150, breakStart: "15:00",
    off: false, rest: 30, restStart: "13:00", gapStart: "15:00", gap: 120 }));
  return s;
}
const refused = (change) => assert.throws(() => cleanSetup(twoParts(change)), (e) => e.code === "setup" && e.field === "shiftTypes");

test("[RVD-20] HQ-08 a shift in two parts keeps its gap and its own break, and the file stays what an older app reads", () => {
  const clean = cleanSetup(twoParts());
  const lunch = clean.shiftTypes.find((t) => t.name === "점심");
  assert.deepEqual(lunch, { name: "점심", label: "점", color: 4280391411, start: "11:00", end: "21:00", break: 150, breakStart: "15:00",
    off: false, icon: null, gapStart: "15:00", gap: 120, rest: 30, restStart: "13:00" });
  // Nothing about a gap is added to a shift in one part, and the version is not raised.
  assert.equal("gap" in clean.shiftTypes[0], false);
  assert.equal(clean.v, 2);
  // Over a night too: 20:00~05:00 with a gap from 00:00 for an hour, no break of its own.
  const night = cleanSetup(twoParts((t) => ({ ...t, start: "20:00", end: "05:00", break: 60, breakStart: "00:00", gapStart: "00:00", gap: 60,
    rest: 0, restStart: null }))).shiftTypes.find((t) => t.name === "점심");
  assert.deepEqual([night.gapStart, night.gap, night.rest, night.restStart], ["00:00", 60, 0, null]);
});

test("[RVD-20] HQ-08 a long gap may make 'break' pass 12 hours; a shift in one part still may not", () => {
  // 06:00~23:00 with a 10-hour gap from 09:00 and an hour's break: "break" 660 + 60.
  const long = cleanSetup(twoParts((t) => ({ ...t, start: "06:00", end: "23:00", break: 780, breakStart: "09:00", gapStart: "09:00", gap: 720, rest: 60,
    restStart: "20:00" }))).shiftTypes.find((t) => t.name === "점심");
  assert.equal(long.break, 780);
  const plain = setup();
  plain.shiftTypes[0].break = 721;
  assert.throws(() => cleanSetup(plain), (e) => e.code === "setup");
});

test("[RVD-20] HQ-08 what an older app reads must be the same day: break = rest + gap, breakStart = gapStart, the gap inside the shift", () => {
  refused((t) => ({ ...t, break: 120 }));
  refused((t) => ({ ...t, breakStart: "13:00" }));
  refused((t) => ({ ...t, gapStart: "10:00", breakStart: "10:00" }));
  refused((t) => ({ ...t, gapStart: "20:40", breakStart: "20:40", gap: 30, break: 60 }));
  refused((t) => ({ ...t, gapStart: "11:10", breakStart: "11:10" }));
  refused((t) => ({ ...t, gap: 20, break: 50 }));
  refused((t) => ({ ...t, gap: 120.5, break: 150.5 }));
  refused((t) => ({ ...t, gapStart: "3pm" }));
  refused((t) => ({ ...t, restStart: "25:00" }));
  refused((t) => ({ ...t, rest: 721, break: 841 }));
  refused((t) => { const { gapStart, ...rest } = t; return rest; });
  refused((t) => ({ ...t, start: null, end: null }));
  refused((t) => ({ ...t, off: true }));
  // Through the report path as the app sends it, the same refusal.
  assert.throws(() => readReport(report(1, { setup: twoParts((t) => ({ ...t, break: 90 })) })), (e) => e.code === "setup");
});

test("[RVD-20] HQ-08 two setups that only add up to the same 'break' are not the same rule, and the admin app says the two times", async () => {
  const a = cleanSetup(twoParts());
  const b = cleanSetup(twoParts((t) => ({ ...t, rest: 60, gap: 90, break: 150 })));
  const plain = cleanSetup(twoParts((t) => ({ name: t.name, label: t.label, color: t.color, start: t.start, end: t.end, break: 150, breakStart: "15:00", off: false })));
  const prints = await Promise.all([a, b, plain].map(fingerprint));
  assert.equal(new Set(prints).size, 3);
  // A setup without any gap keeps the fingerprint it had before 2.0.0 (worked out on f33c16 9be7ec6f, before this change).
  assert.equal(await fingerprint(cleanSetup(setup())), "d8b5a49843cca14542d4f8b3f0f232d1f5eb04d574be3a1ce2e2d44b94549fb1");
  const line = words(a).find(([label]) => label === "근무")[1];
  assert.match(line, /점심 11:00~15:00, 17:00~21:00 휴게 30분/);
  assert.match(line, /주간 07:00~19:00 휴게 60분/);
});

test("[RVD-20] HQ-08 a report with a shift in two parts is taken and kept whole; one from an older app is taken as before", async () => {
  const { call, DB } = server();
  const sent = await call("POST", "/api/reports", report(1, { setup: twoParts() }), from(1));
  assert.equal(sent.status, 201);
  const kept = JSON.parse((await DB.prepare("SELECT setup FROM reports").first()).setup);
  const lunch = kept.shiftTypes.find((t) => t.name === "점심");
  assert.deepEqual([lunch.break, lunch.breakStart, lunch.gapStart, lunch.gap, lunch.rest, lunch.restStart], [150, "15:00", "15:00", 120, 30, "13:00"]);
  assert.equal((await call("POST", "/api/reports", report(2), from(2))).status, 201);
});
