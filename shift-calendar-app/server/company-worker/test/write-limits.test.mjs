// 1.36 [LIM-10] 신고 누적 쓰기 제한 (사용자 2026-10-31 "제한을 두지마. 신고가 많은 사용자는 제한을 두고 기간이 지나면 풀어 주고, 제한 받은
// 횟수가 늘수록 더 줄이는 방식"): no daily cap; posts that reports (or the developer) hid restrict the writer for a while, each time
// more strictly; the admin app sees and lifts it. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { stepAfter, WRITE_LIMITS } from "../src/write-limits.js";
import { server, report, device, from, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-05T03:00:00Z");
const DAY = 24 * 3600e3;
const mine = (n) => ({ "x-device": device(n) });

async function talk(call) {
  const a = await call("POST", "/api/reports", report(1), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  const say = (n, body) => call("POST", "/api/posts", { device: device(n), company: c, setup: g, body }, from(n));
  const read = (n) => call("GET", `/api/companies/${c}/setups/${g}/posts`, undefined, mine(n));
  const flag = (id, n) => call("POST", `/api/posts/${id}/flag`, { device: device(n), reason: "abuse" }, from(n));
  return { say, read, flag };
}

test("[LIM-10] no cap without strikes; two hidden posts restrict to 5 a day for 3 days, then it ends by itself", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const { say, read, flag } = await talk(call);
  for (let i = 0; i < 25; i++) assert.equal((await say(2, `야간 이야기 ${i}번째예요`)).status, 201);
  assert.equal((await read(2)).data.left, undefined);
  // Two of the writer's posts hidden by three reports each.
  for (const body of ["여기 사람들 다 바보예요", "또 바보 같은 소리네요"]) {
    const id = (await say(2, body)).data.id;
    for (const n of [3, 4, 5]) await flag(id, n);
  }
  const shown = (await read(2)).data;
  assert.equal(shown.limit.perDay, 5);
  assert.equal(shown.left.posts, 5);
  assert.equal(shown.limit.until, new Date(NOW + 3 * DAY).toISOString());
  for (let i = 0; i < 5; i++) assert.equal((await say(2, `조심해서 쓰는 글 ${i}`)).status, 201);
  const refused = await say(2, "여섯 번째 글이에요");
  assert.equal(refused.status, 429);
  assert.equal(refused.data.why, "device");
  assert.equal((await read(2)).data.left.posts, 0);
  // Others are not touched.
  assert.equal((await say(6, "저는 그대로 써요")).status, 201);
  assert.equal((await read(6)).data.left, undefined);
  // After 3 days it is lifted without anyone doing anything.
  t.mock.timers.tick(3 * DAY + 1000);
  assert.equal((await read(2)).data.left, undefined);
  for (let i = 0; i < 8; i++) assert.equal((await say(2, `다시 자유롭게 ${i}`)).status, 201);
});

test("[LIM-10] each restriction is stricter: 5 a day, 2 a day, then 쓰기 막기 30 days; the admin app sees and lifts it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  const { say, read } = await talk(call);
  let round = 0;
  const strikeTwice = async () => {
    round++;
    for (const body of [`광고 글이에요 ${round}회 하나`, `광고 글이에요 ${round}회 둘`]) {
      const sent = await say(2, body);
      assert.equal(sent.status, 201, JSON.stringify(sent.data));
      const id = sent.data.id;
      // The admin sign-in lasts less than these days apart, so each round signs in again.
      assert.equal((await call("POST", `/api/admin/posts/${id}`, { action: "hide", reason: "spam" }, await signIn(call))).status, 200);
    }
  };
  await strikeTwice();
  assert.equal((await read(2)).data.limit.perDay, 5);
  t.mock.timers.tick(4 * DAY);
  await strikeTwice();
  assert.equal((await read(2)).data.limit.perDay, 2);
  t.mock.timers.tick(8 * DAY);
  await strikeTwice();
  const muted = await say(2, "막힌 동안 쓰기");
  assert.equal(muted.status, 403);
  assert.equal(muted.data.error, "muted");
  const h2 = await signIn(call);
  const list = (await call("GET", "/api/admin/write-limits", undefined, h2)).data;
  assert.equal(list.rules.strikes, 2);
  const row = list.items.find((r) => r.now);
  assert.deepEqual([row.step, row.perDay, row.times, row.strikes], [3, 0, 3, 6]);
  assert.equal(JSON.stringify(list).includes(device(2)), false);
  // 풀기: writing opens again now, and the next restriction still starts from where this one was.
  assert.equal((await call("POST", `/api/admin/write-limits/${row.who}/lift`, {}, h2)).data.lifted, true);
  assert.equal((await say(2, "풀린 뒤 첫 글이에요")).status, 201);
  assert.equal((await call("POST", "/api/admin/write-limits/000000000000/lift", {}, h2)).status, 404);
});

test("[LIM-10] a long quiet stretch steps it back down, and a post shown again no longer counts", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  assert.equal(stepAfter(null, NOW), 0);
  const last = { step: 3, until: NOW };
  assert.equal(stepAfter(last, NOW + (WRITE_LIMITS.forgetDays - 1) * DAY), 3);
  assert.equal(stepAfter(last, NOW + WRITE_LIMITS.forgetDays * DAY), 2);
  assert.equal(stepAfter(last, NOW + 10 * WRITE_LIMITS.forgetDays * DAY), 0);
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  const { say, read } = await talk(call);
  const a = (await say(2, "광고 글이에요 하나")).data.id;
  await call("POST", `/api/admin/posts/${a}`, { action: "hide", reason: "spam" }, h);
  // The developer looked again and showed it: its strike is gone, so one more hidden post is not two.
  await call("POST", `/api/admin/posts/${a}`, { action: "show" }, h);
  const b = (await say(2, "광고 글이에요 둘")).data.id;
  await call("POST", `/api/admin/posts/${b}`, { action: "hide", reason: "spam" }, h);
  assert.equal((await read(2)).data.limit, undefined);
});
