// 2.0.0 [I60a-01] (검토 2026-11-12 I60a-01, TEST-05·07) 서버 시험은 시계를 고정하고 돌아요: `server({ t })`가 기본 시각(TEST_NOW)에
// 멈춘 시계로 시작해요. 날짜 폭탄 4건은 모두 '고정 안 함' 또는 '고정과 진짜가 섞임'이었어요. 이 시험은 진짜 시계로 도는 파일이
// 늘지 않게 봐요: 새 시험 파일은 server({ t })·fixClock(t)·t.mock.timers 가운데 하나로 시계를 정해요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { TEST_NOW, fixClock, server } from "./helpers.mjs";

const here = fileURLToPath(new URL(".", import.meta.url));

/** 2026-11-12에 진짜 시계로 돌던 파일(시계를 정하면 여기서 빼요). 이 목록은 줄기만 해요. */
const REAL_CLOCK = [
  "admin-at.test.mjs",
  "admin-ax.test.mjs",
  "admin-more139.test.mjs",
  "ai.test.mjs",
  "auth-q1-200.test.mjs",
  "claude-drafts139.test.mjs",
  "earned-days.test.mjs",
  "fix132.test.mjs",
  "g7-139.test.mjs",
  "hq08-two-parts.test.mjs",
  "inbox.test.mjs",
  "j133-plaza-pay.test.mjs",
  "n200-incident.test.mjs",
  "observe139.test.mjs",
  "old-pay-140.test.mjs",
  "ops-fix139.test.mjs",
  "passkey.test.mjs",
  "perf-rounds.test.mjs",
  "plaza-af.test.mjs",
  "plaza-ah.test.mjs",
  "plaza-ap.test.mjs",
  "plaza-guard.test.mjs",
  "plaza-x.test.mjs",
  "plaza.test.mjs",
  "push.test.mjs",
  "review-d.test.mjs",
  "schema-compat.test.mjs",
  "search-as.test.mjs",
  "secx132.test.mjs",
  "servers139.test.mjs",
  "speed-sp1.test.mjs",
  "studio-materials.test.mjs",
  "talk-proposal-text.test.mjs",
];

const FIXES = /mock\.timers|fixClock\(|server\(\s*\{[^}]*\bt\b/;

test("진짜 시계로 도는 서버 시험 파일이 늘지 않아요", () => {
  const files = readdirSync(here).filter((n) => n.endsWith(".test.mjs"));
  const real = files.filter((f) => {
    const text = readFileSync(here + f, "utf8");
    return /\bserver\(/.test(text) && !FIXES.test(text);
  });
  const fresh = real.filter((f) => !REAL_CLOCK.includes(f));
  assert.deepEqual(fresh, [], "새 시험 파일은 server({ t })로 시계를 고정해요(날짜가 바뀌어도 같은 답)");
  const fixedNow = REAL_CLOCK.filter((f) => files.includes(f) && !real.includes(f));
  assert.deepEqual(fixedNow, [], "시계를 정한 파일은 REAL_CLOCK 목록에서 빼요");
  const gone = REAL_CLOCK.filter((f) => !files.includes(f));
  assert.deepEqual(gone, [], "없어진 파일은 목록에서 빼요");
});

test("server({ t })는 기본 시각에 멈춘 시계로 시작하고, SQL의 지금도 같아요", async (t) => {
  const { DB, call } = server({ t });
  assert.equal(Date.now(), TEST_NOW);
  await call("GET", "/api/terms");
  const row = await DB.prepare("SELECT CAST(strftime('%s', 'now') AS INTEGER) * 1000 AS ms").first();
  assert.equal(row.ms, TEST_NOW);
  t.mock.timers.tick(86400e3);
  assert.equal(Date.now(), TEST_NOW + 86400e3, "시험이 시계를 움직일 수 있어요");
});

test("시각을 주면 그 시각, 시험이 먼저 켠 시계는 그대로", (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2027-01-01T00:00:00Z") });
  server({ t, at: "2030-01-01T00:00:00Z" });
  assert.equal(Date.now(), Date.parse("2027-01-01T00:00:00Z"), "이미 켠 시계를 덮어쓰지 않아요");
});

test("fixClock은 시각을 돌려줘요", (t) => {
  assert.equal(fixClock(t, "2027-12-31T14:59:00Z"), Date.parse("2027-12-31T14:59:00Z"));
});

test("기본 시각은 날짜 약속에서 먼 평일 한낮(KST)", () => {
  const kst = new Date(TEST_NOW + 9 * 3600e3);
  assert.equal(kst.getUTCHours(), 12);
  assert.ok(kst.getUTCDay() >= 1 && kst.getUTCDay() <= 5, "평일");
  assert.ok(kst.getUTCDate() >= 10 && kst.getUTCDate() <= 20, "달 가운데(달 넘김·말일 계산에서 멂)");
});
