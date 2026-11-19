// 2.0.0 [RVQ-56] 검토 SX-03 장터 '입은 뒤' 숫자와 T21-02 유형별 기능 쓰임. 둘 다 처리방침 3.14의 30일 줄(4줄·7줄, 깃발 market.wear·
// stats.kind_use)이라, 관리자 앱 일정에 3.14 시행일이 들어가기 전에는 서버가 세지 않아요. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";
import { WEAR, wearOf } from "../src/recipes.js";
import { obsGaps, obsKinds, obsWear } from "../src/observe.js";
import { flagDef } from "../src/flags.js";

const DAY = 86400e3;
const BEFORE = Date.parse("2026-12-10T03:00:00Z");
const AFTER = Date.parse("2026-12-22T03:00:00Z");
const recipe = (title) => ({
  format: "bamnat-recipe", v: 1, title, summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
});
const policy314 = (DB) => DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
  .bind(JSON.stringify({ "3.14": { posted: "2026-11-21", effective: "2026-12-21", heldUntil: null, unfavourable: false } })).run();
const bundle = (day, events, extra = {}) => ({ v: 1, app: "2.0.0", days: [{ day, events, recent: [] }], ...extra });

test("[RVQ-56] 두 깃발은 처리방침 3.14(30일 줄) 줄이에요", () => {
  for (const key of ["market.wear", "stats.kind_use"]) {
    const f = flagDef(key);
    assert.ok(f, key);
    assert.equal(f.default, false);
    assert.deepEqual(f.conditions.map((c) => c.type), ["date", "policy", "consent"]);
    assert.ok(f.conditions.every((c) => c.type === "consent" || c.policy === "3.14" || c.version === "3.14"), JSON.stringify(f.conditions));
  }
  assert.equal(WEAR.flag, "market.wear");
});

test("[RVQ-56] SX-03 3.14 전에는 '입은 뒤'를 세지 않고, 뒤에는 작품·날마다 합계만, 관리자 앱에는 비율만 가요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: BEFORE });
  const { call, DB } = server({ plaza: true });
  let admin = await signIn(call);
  const code = (await call("POST", "/api/recipes", { device: device(1), recipe: recipe("야간 다크"), scope: "public" }, from(1))).data.code;
  await DB.prepare("UPDATE recipes SET state = 'visible' WHERE code = ?").bind(code).run();
  const send = (n, body) => call("POST", `/api/recipes/${code}/applied`, body, from(n));
  const counts = () => DB.prepare("SELECT SUM(applied) AS applied, SUM(wear_n) AS wear_n, SUM(off_soon) AS off_soon, SUM(text_back) AS text_back FROM recipe_counts WHERE code = ?").bind(code).first();
  const wearIn = async () => {
    const r = await call("GET", `/api/admin/recipes?tab=one&code=${code}`, undefined, admin);
    assert.equal(r.status, 200, JSON.stringify(r.data));
    return r.data.items[0].wear;
  };

  // 3.14 전: 입기는 쓰는 분으로만, 입은 뒤 둘은 '세지 않음'(앱이 다시 보내지 않게 200).
  assert.deepEqual((await send(1, { kind: "applied", wear: true })).data, { ok: true });
  const off = await send(2, { kind: "offSoon" });
  assert.equal(off.status, 200);
  assert.deepEqual(off.data, { ok: true, counted: false });
  assert.deepEqual({ ...(await counts()) }, { applied: 1, wear_n: 0, off_soon: 0, text_back: 0 });
  assert.deepEqual(await wearIn(), { state: "off" });
  assert.equal((await send(3, { kind: "nope" })).status, 400);

  // 3.14 시행일(12-21) 뒤.
  await policy314(DB);
  t.mock.timers.setTime(AFTER);
  admin = await signIn(call);
  for (let i = 0; i < 25; i++) assert.equal((await send(10 + i, { kind: "applied", wear: true })).status, 200);
  await send(40, { kind: "applied" }); // wear 없는 옛 앱의 입기는 기준에 들지 않아요.
  for (let i = 0; i < 12; i++) await send(50 + i, { kind: "offSoon" });
  for (let i = 0; i < 3; i++) await send(70 + i, { kind: "textBack" });
  const c = await counts();
  assert.deepEqual({ ...c }, { applied: 27, wear_n: 25, off_soon: 12, text_back: 3 });
  const r = await DB.prepare("SELECT applied, kept7 FROM recipes WHERE code = ?").bind(code).first();
  assert.equal(r.applied, 27, "작품 줄의 쓰는 분은 입기만");
  assert.deepEqual(await wearIn(), { state: "ok", offSoon: 0.48, textBack: 0.12, watch: true });
  // 한 네트워크는 한 작품에 하루 몇 번까지(쓰는 분과 같은 한도).
  for (let i = 0; i < 5; i++) await call("POST", `/api/recipes/${code}/applied`, { kind: "offSoon" }, { "cf-connecting-ip": "198.51.100.9" });
  assert.equal((await counts()).off_soon, 15);

  // 개선 신호: 관리자 앱 › 장터의 그 작품으로 가는 줄.
  const o = (await call("GET", "/api/admin/observe", undefined, await signIn(call))).data;
  const sig = o.found.find((s) => s.kind === "recipe-wear");
  assert.ok(sig, JSON.stringify(o.found.map((s) => s.kind)));
  assert.equal(sig.subject, code);
  assert.equal(sig.to, `#/recipes?tab=one&code=${code}`);
  assert.match(sig.title, /30분 안에 뺐어요/);
  assert.ok(!o.gaps.some((g) => g.key === "at-314"));
});

test("[RVQ-56] SX-03 입기가 적으면 'few', 선 아래면 신호가 없어요", () => {
  assert.deepEqual(wearOf(true, { wear_n: 19, off_soon: 19 }), { state: "few" });
  assert.deepEqual(wearOf(true, { wear_n: 40, off_soon: 4, text_back: 2 }), { state: "ok", offSoon: 0.1, textBack: 0.05, watch: false });
  assert.deepEqual(wearOf(false, { wear_n: 400, off_soon: 400 }), { state: "off" });
  const rows = [{ code: "A", title: "가", wear_n: 40, off_soon: 4, text_back: 13 }, { code: "B", title: "나", wear_n: 40, off_soon: 30, text_back: 0 },
    { code: "C", title: "다", wear_n: 10, off_soon: 10, text_back: 0 }];
  const w = obsWear(rows);
  assert.deepEqual(w.map((x) => [x.code, x.text, x.value, x.base]), [["B", false, 75, 40], ["A", true, 33, 30]]);
});

test("[RVQ-56] T21-02 3.14 전에는 유형별로 첫 열기·머묾만, 뒤에는 그날 쓴 기능도 유형별로 세고 관측 판이 덜 쓰는 기능을 보여요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: BEFORE });
  const { call, DB } = server();
  await call("GET", "/api/terms");
  // 3.12(유형별 흐름)는 이미 시행.
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.12": { posted: "2026-09-01", effective: "2026-10-01", heldUntil: null, unfavourable: false } })).run();
  const send = (n, day, events, style) => call("POST", "/api/events", bundle(day, events, { style }), from(n));
  assert.equal((await send(1, "2026-12-09", { open: 1, pay_view: 2 }, "SHIFT")).status, 204);
  assert.deepEqual(DB.db.prepare("SELECT event FROM usage_kinds ORDER BY event").all().map((r) => r.event), ["open"], "3.14 전에는 기능 없이");

  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.12": { posted: "2026-09-01", effective: "2026-10-01", heldUntil: null, unfavourable: false },
      "3.14": { posted: "2026-11-21", effective: "2026-12-21", heldUntil: null, unfavourable: false } })).run();
  t.mock.timers.setTime(AFTER);
  // 교대 30대는 급여를 잘 보고, 가게 25대는 거의 안 봐요.
  // (같은 몸은 하루 한 번만 세서, 연 횟수를 휴대폰마다 다르게 적어요.)
  for (let i = 0; i < 30; i++) await send(100 + i, "2026-12-21", { open: 1 + i, pay_view: 1, disc_shown: 1 }, "SHIFT");
  for (let i = 0; i < 25; i++) await send(200 + i, "2026-12-21", i < 2 ? { open: 1 + i, pay_view: 1 } : { open: 1 + i }, "BUSINESS");
  const rows = DB.db.prepare("SELECT style, event, SUM(phones) AS n FROM usage_kinds WHERE day = '2026-12-21' GROUP BY style, event ORDER BY style, event").all()
    .map((r) => `${r.style}:${r.event}:${r.n}`);
  assert.deepEqual(rows, ["BUSINESS:open:25", "BUSINESS:pay_view:2", "SHIFT:open:30", "SHIFT:pay_view:30"], "날짜·유형·기능별 휴대폰 수만, 카드 합계(disc_)는 빼고");

  const admin = await signIn(call);
  const o = (await call("GET", "/api/admin/observe", undefined, admin)).data;
  const shop = o.kinds.rows.find((r) => r.style === "BUSINESS");
  assert.equal(shop.use.few, false);
  assert.deepEqual(shop.use.low.map((x) => [x.event, x.rate]), [["pay_view", 8]]);
  assert.deepEqual(o.kinds.rows.find((r) => r.style === "SHIFT").use.low, []);
  assert.equal(o.kinds.all.use, undefined);
});

test("[RVQ-56] 3.14 전에는 관측 빈틈에 '3.14 30일 줄에 저절로 켜져요' 한 줄, 기능 쓰임이 없으면 덜 쓰는 기능도 없어요", () => {
  const gaps = obsGaps({ sample: { daily: 50, lifeOn: true }, kinds: { rows: [], all: {} }, keep: { rows: [1] }, flags314: { kindUse: false, wear: false } });
  const g = gaps.find((x) => x.key === "at-314");
  assert.equal(g.state, "wait");
  assert.match(g.title, /유형별 기능 쓰임 · 장터 입은 뒤 숫자/);
  const days = Array.from({ length: 61 }, (_, i) => new Date(AFTER - (60 - i) * DAY + 9 * 3600e3).toISOString().slice(0, 10));
  const k = obsKinds([{ day: days.at(-1), style: "SHIFT", event: "open", phones: 30 }], days);
  assert.equal(k.rows[0].use, undefined);
  const few = obsKinds([{ day: days.at(-1), style: "SHIFT", event: "open", phones: 5 }, { day: days.at(-1), style: "SHIFT", event: "pay_view", phones: 1 }], days);
  assert.deepEqual(few.rows[0].use, { open: 5, few: true, low: [] });
});
