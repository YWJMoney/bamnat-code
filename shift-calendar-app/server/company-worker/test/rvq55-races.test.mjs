// 2.0.0 [RVQ-55] 검토 SVC-37: 읽고 따로 쓰던 곳의 겹침. 같은 휴대폰이 거의 동시에 두 번 누른 좋아요, 겹친 보상의 달 상한, 두 번 누른 해명,
// 마을 쉬기를 풀 때 사라지던 운영자 말 못 하기, 매시 작업과 겹친 개선 신호 누름. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { deviceHash } from "../src/index.js";
import { giveDays, rewardRules } from "../src/money.js";
import { obsUpdate } from "../src/observe.js";
import { server, report, device, from, signIn } from "./helpers.mjs";

const NOV30 = Date.parse("2026-11-30T03:00:00Z");
const DEC10 = Date.parse("2026-12-10T03:00:00Z");
const DAY = 86400e3;
const recipe = (title) => ({
  format: "bamnat-recipe", v: 1, title, summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
});

/**
 * 실제 D1처럼 문장 하나하나가 네트워크를 건너게(다음 차례로 미뤄) 두 요청이 읽기와 쓰기 사이에서 엇갈리게 해요. batch는 그대로 한 묶음이에요.
 */
function slowDb(DB, slower = /$^/) {
  const prep = DB.prepare;
  const batch = DB.batch.bind(DB);
  const tick = () => new Promise((r) => setImmediate(r));
  const wrap = (s, n) => {
    const wait = async () => { for (let i = 0; i < n; i++) await tick(); };
    return { ...s, orig: s, bind: (...v) => wrap(s.bind(...v), n),
      first: async (c) => { await wait(); return s.first(c); }, all: async () => { await wait(); return s.all(); }, run: async () => { await wait(); return s.run(); } };
  };
  // [slower]에 맞는 읽기(예: 지금 표 읽기)는 더 늦게 와요: 그사이 다른 요청이 그 자리를 지나가요.
  DB.prepare = (sql) => wrap(prep(sql), slower.test(sql) ? 20 : 1);
  DB.batch = async (list) => { await tick(); return batch(list.map((x) => x.orig ?? x)); };
}

test("[RVQ-55] SVC-37 같은 휴대폰의 좋아요를 거의 동시에 두 번 보내도 좋아요는 하나예요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOV30 });
  const { call, DB } = server({ plaza: true });
  const code = (await call("POST", "/api/recipes", { device: device(1), recipe: recipe("야간 다크"), scope: "public" }, from(1))).data.code;
  slowDb(DB, /FROM recipe_votes WHERE code = \? AND device = \?/);
  const vote = (n, value) => call("POST", `/api/recipes/${code}/vote`, { device: device(n), value, ...(value === -1 ? { reason: "thin", opinion: "설명이 조금 더 있으면 좋겠어요" } : {}) }, from(n));
  await Promise.all([vote(2, 1), vote(2, 1), vote(2, 1)]);
  const r = await DB.prepare("SELECT likes, dislikes FROM recipes WHERE code = ?").bind(code).first();
  assert.deepEqual([r.likes, r.dislikes], [1, 0]);
  // 바꾸고 거두는 것도 표 그대로.
  await Promise.all([vote(2, -1), vote(3, 1)]);
  await vote(3, null);
  const after = await DB.prepare("SELECT likes, dislikes FROM recipes WHERE code = ?").bind(code).first();
  assert.deepEqual([after.likes, after.dislikes], [0, 1], JSON.stringify(DB.db.prepare("SELECT value, held FROM recipe_votes").all()));
});

test("[RVQ-55] SVC-37 두 보상이 겹쳐도 한 달 상한을 넘지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOV30 });
  const { env, call } = server({ plaza: true });
  await call("GET", "/api/terms");
  const cap = (await rewardRules(env)).monthCap;
  slowDb(env.DB);
  const got = await Promise.all([giveDays(env, "bug", "bug:d1:x", "dev-x", cap, NOV30), giveDays(env, "idea", "idea:d2:x", "dev-x", cap, NOV30),
    giveDays(env, "bug", "bug:d3:x", "dev-x", 3, NOV30)]);
  assert.equal(got.reduce((a, b) => a + b, 0), cap);
  assert.equal((await env.DB.prepare("SELECT SUM(days) AS n FROM day_rewards WHERE device = 'dev-x'").first()).n, cap);
  // 같은 보상(같은 출처)은 한 번만.
  assert.equal(await giveDays(env, "bug", "bug:d1:y", "dev-y", 2, NOV30), 2);
  assert.equal(await giveDays(env, "bug", "bug:d1:y", "dev-y", 2, NOV30), 0);
});

test("[RVQ-55] SVC-37 해명을 두 번 빨리 눌러도 문의는 하나예요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: DEC10 });
  const { call, DB } = server({ plaza: true });
  const h = await signIn(call);
  assert.equal((await call("POST", "/api/admin/policy", { confirm: true, version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, h)).status, 200);
  const a = await call("POST", "/api/reports", report(1), from(1));
  const id = (await call("POST", "/api/posts", { device: device(2), company: a.data.company.id, setup: a.data.setup, body: "다들 일을 너무 못하네요" }, from(2))).data.id;
  await call("POST", `/api/admin/posts/${id}`, { action: "hide", reason: "abuse" }, h);
  slowDb(DB);
  const appeal = () => call("POST", "/api/community/appeal", { device: device(2), target: `post:${id}`, text: "같이 고쳐 보자는 뜻이었어요. 다시 봐 주세요." }, from(2));
  const [x, y] = await Promise.all([appeal(), appeal()]);
  assert.deepEqual([x.status, y.status].sort(), [201, 409]);
  const sent = x.status === 201 ? x : y;
  const other = x.status === 201 ? y : x;
  assert.equal(other.data.error, "already");
  const made = (await DB.prepare("SELECT id FROM inquiries").all()).results.map((r) => r.id);
  assert.deepEqual(made, [sent.data.inquiry]);
});

test("[RVQ-55] SVC-37 마을 쉬기를 풀어도 운영자가 건 말 못 하기는 남고, 글을 다시 보여도 마을 쉬기는 남아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: DEC10 });
  const { call, DB } = server({ plaza: true });
  const h = await signIn(call);
  assert.equal((await call("POST", "/api/admin/policy", { confirm: true, version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, h)).status, 200);
  const a = await call("POST", "/api/reports", report(1), from(1));
  const say = (body) => call("POST", "/api/posts", { device: device(2), company: a.data.company.id, setup: a.data.setup, body }, from(2));
  const dev = await deviceHash(device(2));
  const mutedRow = () => DB.prepare("SELECT reason, until FROM muted WHERE device = ?").bind(dev).first();
  // 운영자 말 못 하기(30일).
  const p1 = (await say("다들 일을 너무 못하네요")).data.id;
  assert.equal((await call("POST", `/api/admin/posts/${p1}`, { action: "mute", reason: "abuse" }, h)).status, 200);
  const adminUntil = (await mutedRow()).until;
  // 하루 뒤 그 위에 더 긴 마을 쉬기(심각한 이유라 경고 없이) — 덮어써요.
  t.mock.timers.tick(DAY);
  const hold = await call("POST", "/api/admin/care/hold", { post: p1, scope: "community", days: 30, reason: "illegal" }, h);
  if (hold.status !== 200) {
    // 이유 목록이 다르면 경고를 먼저.
    await call("POST", "/api/admin/care/warn", { post: p1, reason: "abuse" }, h);
  }
  const held = hold.status === 200 ? hold.data : (await call("POST", "/api/admin/care/hold", { post: p1, scope: "community", days: 30, reason: "abuse" }, h)).data;
  const careRow = await mutedRow();
  assert.equal(careRow.reason, "care");
  assert.ok(careRow.until > adminUntil);
  // 마을 쉬기를 풀면 운영자 말 못 하기가 되살아나요(전에는 줄이 통째로 지워졌어요).
  assert.equal((await call("POST", `/api/admin/care/${held.id}/lift`, {}, h)).status, 200);
  const back = await mutedRow();
  assert.deepEqual([back?.reason, back?.until], ["abuse", adminUntil]);
  // 다시 마을 쉬기를 걸고, 운영자가 글을 다시 보이면 말 못 하기만 풀리고 마을 쉬기는 남아요.
  const again = (await call("POST", "/api/admin/care/hold", { post: p1, scope: "community", days: 7, reason: held.reason }, h)).data;
  assert.ok(again.id);
  await call("POST", `/api/admin/posts/${p1}`, { action: "show" }, h);
  const left = await mutedRow();
  assert.equal(left?.reason, "care");
  assert.equal((await DB.prepare("SELECT COUNT(*) AS n FROM muted_kept WHERE device = ?").bind(dev).first()).n, 0);
});

test("[RVQ-55] SVC-37 매시 작업이 읽는 사이 관리자가 누른 것은 저장할 때 사라지지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOV30 });
  const { env, call, DB } = server({ plaza: true });
  const h = await signIn(call);
  const sig = { key: "s1", kind: "drop", subject: "x", title: "삭제가 늘었어요", why: "", state: "new", value: 1, base: 1, unit: "", at: NOV30 };
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").bind(JSON.stringify({ list: [sig] })).run();
  let pressed = false;
  const saved = await obsUpdate(env, async (list) => {
    if (!pressed) {
      pressed = true;
      // 매시 작업이 아직 읽는 동안 관리자가 '지켜보기'를 눌러요.
      const r = await call("POST", "/api/admin/observe/signals", { key: "s1", act: "watch" }, h);
      assert.equal(r.status, 200, JSON.stringify(r.data));
    }
    return list.map((s) => ({ ...s, value: 9 }));
  });
  assert.deepEqual([saved[0].state, saved[0].value], ["watch", 9]);
  const stored = JSON.parse((await DB.prepare("SELECT value FROM settings WHERE key = 'obs_signals'").first()).value).list;
  assert.deepEqual([stored[0].state, stored[0].value], ["watch", 9]);
});
