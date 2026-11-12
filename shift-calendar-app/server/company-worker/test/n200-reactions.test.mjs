// 2.0.0 갈래 N [RVN-14] HQ-51 알림함: /api/digest의 reactions, 내가 올린 것에 다른 분이 남긴 반응을 한 가지에 한 줄로(글마다 답 수·나도요,
// 제보한 규칙·시세의 맞아요·달라요, 부탁의 좋아요·만들게요, 공모에 뽑힘). 누가 했는지는 없고 몇 번인지만, 차단한 분의 답은 빠져요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { deviceHash } from "../src/index.js";
import { makerId } from "../src/recipes.js";
import { server, device, from, report, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-20T03:00:00Z");
const mine = (n) => ({ "x-device": device(n) });
const write = (call, n, body, extra = {}) =>
  call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
const digest = async (call, n, since) => (await call("GET", `/api/digest?since=${new Date(since).toISOString()}`, undefined, mine(n))).data;
const of = (d, kind) => d.reactions.filter((r) => r.kind === kind);

test("HQ-51: answers and 나도요 on my post come as one line per post, and a thread I answered tells me of what came after", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const since = Date.now();
  t.mock.timers.tick(1000);
  const a = (await write(call, 1, "야간 끝나고 잠 어떻게 자요?", { kind: "question" })).data.id;
  const b = (await write(call, 1, "주간 끝나고 운동해요")).data.id;
  t.mock.timers.tick(60e3);
  await write(call, 2, "낮잠 두 번 나눠 자요", { parent: a });
  t.mock.timers.tick(60e3);
  await write(call, 3, "암막 커튼이 최고예요", { parent: a });
  await write(call, 1, "고마워요!", { parent: a });
  for (const n of [2, 3]) await call("POST", `/api/posts/${a}/vote`, { device: device(n), ok: true }, from(n));
  // A 비공감 alone is no news to bring.
  await call("POST", `/api/posts/${b}/vote`, { device: device(4), ok: false }, from(4));
  t.mock.timers.tick(1000);
  const d = await digest(call, 1, since);
  assert.deepEqual(of(d, "answer").map((r) => [r.post, r.room, r.setup, r.count]), [[a, "style-shift", "room", 2]]);
  assert.deepEqual(of(d, "agree").map((r) => [r.post, r.up, r.down]), [[a, 2, 0]]);
  // 2 answered first: 3's answer and 1's thanks came after it.
  assert.deepEqual(of(await digest(call, 2, since), "thread").map((r) => [r.post, r.count]), [[a, 2]]);
  // Others hear nothing of my post; nothing new after now.
  assert.deepEqual((await digest(call, 5, since)).reactions, []);
  assert.deepEqual((await digest(call, 1, Date.now())).reactions, []);
  // 1 blocked 3: 3's answer is left out of the count (as the post's own read leaves it out).
  await DB.prepare("INSERT INTO recipe_blocks (device, maker, at) VALUES (?, ?, ?)")
    .bind(await deviceHash(device(1)), await makerId(await deviceHash(device(3))), NOW).run();
  assert.deepEqual(of(await digest(call, 1, since), "answer").map((r) => r.count), [1]);
});

test("HQ-51: a rule's talk answers too, and 맞아요·달라요 on what I reported or the rate I sent", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const since = Date.now();
  t.mock.timers.tick(1000);
  const sent = (await call("POST", "/api/reports", report(1), from(1))).data;
  const company = sent.company.id;
  const talk = (await call("POST", "/api/posts", { device: device(1), company, setup: sent.setup, body: "야간 수당 50%가 맞나요?" }, from(1))).data.id;
  await call("POST", "/api/posts", { device: device(2), company, setup: sent.setup, body: "네 저희도 50%예요", parent: talk }, from(2));
  await call("POST", "/api/votes", { device: device(2), company, setup: sent.setup, ok: true }, from(2));
  await call("POST", "/api/votes", { device: device(3), company, setup: sent.setup, ok: false, why: "pay" }, from(3));
  const rate = (await call("POST", "/api/rates", { device: device(1), job: "construction", region: "31", trade: "용접", unit: "day", amount: 250000 }, from(1))).data.id;
  await call("POST", `/api/rates/${rate}/vote`, { device: device(4), ok: true }, from(4));
  t.mock.timers.tick(1000);
  const d = await digest(call, 1, since);
  assert.deepEqual(of(d, "answer").map((r) => [r.post, r.room, r.count]), [[talk, company, 1]]);
  assert.deepEqual(of(d, "report_vote").map((r) => [r.company.id, r.setup, r.up, r.down]), [[company, sent.setup, 1, 1]]);
  assert.deepEqual(of(d, "rate_vote").map((r) => [r.rate, r.trade, r.up]), [[rate, "용접", 1]]);
  // Newest first, every line in the shape 내 작품 반응 shares (HQ-52): kind, target, title, count, at.
  assert.deepEqual(d.reactions.map((r) => r.at), [...d.reactions.map((r) => r.at)].sort().reverse());
  for (const r of d.reactions) {
    assert.deepEqual(["kind", "target", "title", "count", "at"].filter((k) => r[k] === undefined), [], JSON.stringify(r));
    assert.ok(r.count > 0);
  }
  assert.equal(of(d, "report_vote")[0].target, `${company}/${sent.setup}`);
});

test("HQ-51: a 부탁's likes and someone taking it on, and my entry picked in 이달의 공모", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  const since = Date.now();
  t.mock.timers.tick(1000);
  const q = (await call("POST", "/api/recipes/requests", { device: device(1), style: "SHIFT", body: "간호사 3교대 알람 세트 있었으면 좋겠어요" }, from(1))).data.id;
  const act = (n, what) => call("POST", `/api/recipes/requests/${q}/${what}`, { device: device(n) }, from(n));
  await act(2, "like");
  await act(3, "like");
  await act(4, "take");
  // My entry in this month's contest, picked by the operator.
  await DB.prepare("INSERT INTO village_contests (month, style, theme, picks, at) VALUES (?, ?, ?, '[]', ?)").bind("2026-11", "SHIFT", "야간 달력", Date.now()).run();
  await DB.prepare("INSERT INTO village_entries (month, style, code, device, at) VALUES (?, ?, ?, ?, ?)")
    .bind("2026-11", "SHIFT", "ABCD2345", await deviceHash(device(1)), Date.now()).run();
  await DB.prepare("INSERT INTO village_entries (month, style, code, device, at) VALUES (?, ?, ?, ?, ?)")
    .bind("2026-11", "SHIFT", "EFGH6789", await deviceHash(device(5)), Date.now()).run();
  await DB.prepare("UPDATE village_contests SET picks = ?, at = ? WHERE month = ? AND style = ?").bind(JSON.stringify(["ABCD2345"]), Date.now(), "2026-11", "SHIFT").run();
  t.mock.timers.tick(1000);
  const d = await digest(call, 1, since);
  assert.deepEqual(of(d, "request_like").map((r) => [r.request, r.count]), [[q, 2]]);
  assert.deepEqual(of(d, "request_take").map((r) => r.request), [q]);
  assert.deepEqual(of(d, "contest_pick").map((r) => [r.code, r.month, r.theme]), [["ABCD2345", "2026-11", "야간 달력"]]);
  // 5's entry was not picked: nothing for 5.
  assert.deepEqual(of(await digest(call, 5, since), "contest_pick"), []);
});

test("HQ-51: paused 광장 소식 pauses the reactions too", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const h = await signIn(call);
  const since = Date.now();
  t.mock.timers.tick(1000);
  const a = (await write(call, 1, "오늘 야간 끝!")).data.id;
  await write(call, 2, "수고했어요", { parent: a });
  assert.equal(of(await digest(call, 1, since), "answer").length, 1);
  await call("POST", "/api/admin/switches", { key: "PLAZA_NEWS", off: true, why: "시험: 잠시 끔" }, h);
  const d = await digest(call, 1, since);
  assert.equal(d.plazaPaused, true);
  assert.deepEqual(d.reactions, []);
});
