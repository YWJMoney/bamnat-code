// 2.0.0 검토 갈래 D 2차 [RVD-17][RVD-18]: 글쓰기 제한 뒤 다시 세는 기간은 공개 운영 규칙대로 60일, 그리고 마을 돌봄 깃발을 둘로 나눠
// '운영자가 확인한 신고만 센다'는 처리방침 3.12 시행일부터, 마을 쉬기는 2026-12-09부터(사용자 결정 카드 2026-11-12 03:46Z·03:50Z,
// 검토 보고서 LEGAL-02·03·07). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, device, from, signIn } from "./helpers.mjs";

const DAY = 24 * 3600e3;
const mine = (n) => ({ "x-device": device(n) });

async function talk(call) {
  const a = await call("POST", "/api/reports", report(1), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  let k = 0;
  const say = (n, body = `나쁜 말 ${++k}번째예요`) => call("POST", "/api/posts", { device: device(n), company: c, setup: g, body }, from(n));
  const flag = (id, n) => call("POST", `/api/posts/${id}/flag`, { device: device(n), reason: "abuse" }, from(n));
  const standing = (n) => call("GET", "/api/community/standing", undefined, mine(n));
  const read = (n) => call("GET", `/api/companies/${c}/setups/${g}/posts`, undefined, mine(n));
  /** The operator hides one more of [n]'s posts (a fresh sign-in each time: these tests move many days). */
  const hide = async (n) => {
    const sent = await say(n);
    assert.equal(sent.status, 201, JSON.stringify(sent.data));
    const r = await call("POST", `/api/admin/posts/${sent.data.id}`, { action: "hide", reason: "abuse" }, await signIn(call));
    assert.equal(r.status, 200, JSON.stringify(r.data));
  };
  return { say, flag, standing, read, hide };
}

/** 처리방침 3.12 시행일(관리자 앱 › 기능 관리 › 일정, 실서버와 같은 11-09). */
async function policy312(call) {
  const r = await call("POST", "/api/admin/policy", { confirm: true, version: "3.12", posted: "2026-11-09", effective: "2026-11-09", heldUntil: "2026-12-09", why: "시험" }, await signIn(call));
  assert.equal(r.status, 200, JSON.stringify(r.data));
}

test("[RVD-17] LEGAL-07: within 60 days after a restriction ends one more steps up; after 60 days it is 3 again; 180 quiet days still step down", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-10T03:00:00Z") });
  const { call } = server({ plaza: true });
  await policy312(call);
  const { say, standing, hide } = await talk(call);
  // Three the operator confirmed: the first restriction (3 days, 5 a day).
  for (let i = 0; i < 3; i++) await hide(2);
  assert.equal((await standing(2)).data.limit.perDay, 5);
  // 30 days after it ended: one more is the next step at once (7 days, 2 a day).
  t.mock.timers.tick(3 * DAY + 30 * DAY);
  await hide(2);
  assert.equal((await standing(2)).data.limit.perDay, 2);
  // 100 days after that one ended: one or two only bring the 안내·경고; the third is the next step (30 days 쓰기 막기).
  t.mock.timers.tick(7 * DAY + 100 * DAY);
  await hide(2);
  assert.equal((await standing(2)).data.limit, undefined);
  await hide(2);
  assert.equal((await standing(2)).data.limit, undefined);
  assert.equal((await say(2, "아직 쓸 수 있어요")).status, 201);
  await hide(2);
  const muted = await say(2, "막힌 동안 쓰기");
  assert.deepEqual([muted.status, muted.data.error], [403, "muted"]);
  // 200 quiet days after the block: one step down (3 → 2), and again three are needed, so the next is 3 again (not 4).
  t.mock.timers.tick(30 * DAY + 200 * DAY);
  await hide(2);
  await hide(2);
  assert.equal((await standing(2)).data.limit, undefined);
  await hide(2);
  assert.equal((await say(2, "다시 막혔어요")).status, 403);
  const list = (await call("GET", "/api/admin/write-limits", undefined, await signIn(call))).data;
  assert.deepEqual([list.rules.windowDays, list.rules.strikes, list.rules.again, list.rules.againDays], [60, 3, 1, 60]);
  const row = list.items.find((r) => r.now);
  assert.deepEqual([row.step, row.times], [3, 4]);
});

test("[RVD-18] LEGAL-02: from 3.12's day (11-09) to 12-09, report-hidden posts wait for the operator; counting stays 1.36's and there is no 마을 쉬기 yet", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  const { call } = server({ plaza: true });
  await policy312(call);
  const { say, flag, standing, read } = await talk(call);
  const a = (await say(2, "여기 사람들 다 바보예요")).data.id;
  const b = (await say(2, "또 바보 같은 소리네요")).data.id;
  for (const id of [a, b]) for (const n of [3, 4, 5]) await flag(id, n);
  // Before 3.12 two report-hidden posts restricted at once; now nothing until the operator looks.
  assert.equal((await read(2)).data.limit, undefined);
  assert.deepEqual((await standing(2)).data, {});
  const h = await signIn(call);
  const care = (await call("GET", "/api/admin/care", undefined, h)).data;
  assert.deepEqual([care.on, care.confirmed, care.unconfirmed.n], [false, true, 2]);
  // 마을 쉬기·경고 wait for 12-09.
  assert.equal((await call("POST", "/api/admin/care/warn", { post: a, reason: "abuse" }, h)).status, 409);
  // The operator keeps the first hidden: one counts, still under 1.36's two.
  await call("POST", `/api/admin/posts/${a}`, { action: "hide", reason: "abuse" }, h);
  assert.equal((await read(2)).data.limit, undefined);
  // Keeps the second hidden: two in 30 days, 1.36's first step (3 days, 5 a day), and no 안내 (that is 마을 돌봄's).
  await call("POST", `/api/admin/posts/${b}`, { action: "hide", reason: "abuse" }, h);
  const limit = (await read(2)).data.limit;
  assert.deepEqual([limit.perDay, limit.n], [5, 2]);
  assert.equal((await standing(2)).data.warning, undefined);
  const rules = (await call("GET", "/api/admin/write-limits", undefined, h)).data.rules;
  assert.deepEqual([rules.strikes, rules.windowDays, rules.confirmedOnly, rules.care], [2, 30, true, undefined]);
  // Found wrong instead: shown again, its waiting strike goes with it.
  const c = (await say(6, "야간 끝나고 다들 잘 쉬세요")).data.id;
  for (const n of [3, 4, 5]) await flag(c, n);
  await call("POST", `/api/admin/posts/${c}`, { action: "show" }, h);
  assert.equal((await read(6)).data.limit, undefined);
});

test("[RVD-18] LEGAL-03: 마을 돌봄 waits for 12-09 in the code even with 3.12 in force, and turns on by itself then", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-08T14:59:00Z") });
  const { call } = server({ plaza: true });
  await policy312(call);
  const h = await signIn(call);
  const at = async () => (await call("GET", "/api/admin/care", undefined, h)).data;
  assert.deepEqual([(await at()).on, (await at()).confirmed], [false, true]);
  // 12-09 00:00 KST is 12-08 15:00Z, and the flags read the server's day in KST.
  t.mock.timers.tick(2 * 60e3);
  assert.deepEqual([(await at()).on, (await at()).confirmed], [true, true]);
  const rules = (await call("GET", "/api/admin/write-limits", undefined, h)).data.rules;
  assert.deepEqual([rules.strikes, rules.windowDays, rules.care], [3, 60, true]);
});
