// AQ 1.29 [PHOTO-09][PHOTO-10] '사진이 달라요' counts: only day, screen, reason, app version and a number; anything else is
// refused; the same bundle twice counts once; a network is rate limited; read back in 지표; gone after 400 days. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { PHOTO_SCREENS, PHOTO_REASONS } from "../src/index.js";
import { server, from, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-10-27T03:00:00Z");
const body = (counts, extra = {}) => ({ v: 1, app: "1.29.0", counts, ...extra });
const row = (screen = "payslip", reason = "numbers", n = 1, day = "2026-10-27") => ({ day, screen, reason, n });

test("[PHOTO-09] the app's screens and reasons and the server's are the same codes", async () => {
  const { readFileSync } = await import("node:fs");
  const kotlin = readFileSync(new URL("../../../core/src/main/kotlin/com/bamnat/core/stats/PhotoWrong.kt", import.meta.url), "utf8");
  const codes = (name) => {
    const block = kotlin.slice(kotlin.indexOf(`enum class ${name}`));
    return [...block.slice(0, block.indexOf(";\n")).matchAll(/^\s+[A-Z_]+\("([a-z_]+)", "([^"]+)"\)/gm)].map((m) => [m[1], m[2]]);
  };
  assert.deepEqual(codes("PhotoScreen"), Object.entries(PHOTO_SCREENS));
  assert.deepEqual(codes("PhotoWrongReason"), Object.entries(PHOTO_REASONS));
});

test("[PHOTO-10] counts are added per day, version, screen and reason, and nothing ties them to a phone", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  assert.equal((await call("POST", "/api/photo-wrong", body([row(), row("payslip", "numbers", 2), row("roster", "missing", 1, "2026-10-26")]), from(1))).status, 204);
  assert.equal((await call("POST", "/api/photo-wrong", body([row("payslip", "numbers", 3)]), from(2))).status, 204);
  const rows = DB.db.prepare("SELECT day, app, screen, reason, total FROM photo_wrong ORDER BY day, screen").all().map((r) => ({ ...r }));
  assert.deepEqual(rows, [
    { day: "2026-10-26", app: "1.29.0", screen: "roster", reason: "missing", total: 1 },
    { day: "2026-10-27", app: "1.29.0", screen: "payslip", reason: "numbers", total: 6 },
  ]);
  assert.ok(!DB.dump().includes("198.51.100"));
  // The same bundle sent again (a timeout) counts once.
  const again = await call("POST", "/api/photo-wrong", body([row("payslip", "numbers", 3)]), from(2));
  assert.equal(again.data.again, true);
  assert.equal(DB.db.prepare("SELECT SUM(total) AS n FROM photo_wrong").get().n, 7);
});

test("[PHOTO-10] anything but codes and numbers is refused", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  const bad = [
    body([row("photo")]), body([row("payslip", "it was blurry")]), body([row("payslip", "numbers", 0)]), body([row("payslip", "numbers", 100)]),
    body([row("payslip", "numbers", 1.5)]), body([row("payslip", "numbers", 1, "2026-12-01")]), body([row("payslip", "numbers", 1, "2026-07-01")]),
    body([]), body(Array.from({ length: 61 }, () => row())), body([row()], { v: 2 }), body([row()], { app: "new" }), body([{ ...row(), text: undefined, day: 5 }]),
  ];
  for (const b of bad) assert.equal((await call("POST", "/api/photo-wrong", b, from(3))).status, 400, JSON.stringify(b).slice(0, 120));
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM photo_wrong").get().n, 0);
  // Extra fields (a photo, words) are never stored: the table has no place for them.
  assert.equal((await call("POST", "/api/photo-wrong", body([{ ...row(), photo: "AAAA", text: "기본급" }]), from(4))).status, 204);
  assert.ok(!DB.dump().includes("기본급"));
});

test("[PHOTO-10] one network sends at most 30 bundles a day", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  for (let i = 1; i <= 30; i++) assert.equal((await call("POST", "/api/photo-wrong", body([row("payslip", "numbers", i)]), from(5))).status, 204);
  assert.equal((await call("POST", "/api/photo-wrong", body([row("payslip", "numbers", 31)]), from(5))).status, 429);
  // Another network still can.
  assert.equal((await call("POST", "/api/photo-wrong", body([row("roster", "other")]), from(6))).status, 204);
});

test("[PHOTO-10] 지표 reads them back by day, screen and version; they go after 400 days", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB, env } = server({ plaza: true });
  await call("POST", "/api/photo-wrong", body([row("payslip", "numbers", 4, "2026-10-26"), row("payslip", "missing", 1, "2026-10-26"), row("rules", "names", 2, "2026-10-25")]), from(1));
  await call("POST", "/api/photo-wrong", body([row("roster", "nothing", 1, "2026-10-26")], { app: "1.28.0" }), from(2));
  const admin = await signIn(call);
  const m = (await call("GET", "/api/admin/metrics?days=7", undefined, admin)).data;
  assert.equal(m.photoWrong.total, 8);
  assert.deepEqual(m.photoWrong.days, [{ day: "2026-10-25", total: 2 }, { day: "2026-10-26", total: 6 }]);
  assert.deepEqual(m.photoWrong.screens[0], { screen: "payslip", label: "명세서 사진", total: 5, reasons: { numbers: 4, missing: 1 } });
  assert.deepEqual(m.photoWrong.apps, [{ app: "1.29.0", total: 7 }, { app: "1.28.0", total: 1 }]);
  DB.db.prepare("INSERT INTO photo_wrong VALUES ('2025-09-01', '1.29.0', 'payslip', 'numbers', 3)").run();
  const { default: worker } = await import("../src/index.js");
  await worker.scheduled({}, env, { waitUntil: (p) => p });
  await new Promise((r) => setTimeout(r, 50));
  const days = DB.db.prepare("SELECT DISTINCT day FROM photo_wrong ORDER BY day").all().map((r) => r.day);
  assert.deepEqual(days, ["2026-10-25", "2026-10-26"]);
});
