// 2.0.0 [RVM-28] HQ-52: 내 작품에 사람들이 한 것(좋아요·후기·의견·아이디어·질문·받아 쓴 분·심사 결과)을 알림함 한 모양으로. `npm test`.
import "./helpers.mjs";
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");

const recipe = (extra = {}) => ({
  format: "bamnat-recipe", v: 1, title: "야간 많은 교대 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
  ...extra,
});
const up = (call, n, extra = {}) => call("POST", "/api/recipes", { device: device(n), recipe: recipe(extra), scope: "public" }, from(n));
const dev = (n) => ({ "x-device": device(n) });
const COMMON = ["kind", "target", "title", "count", "at"];

test("[RVM-28] HQ-52 내 작품 반응: 작품마다 좋아요 한 줄, 글은 글마다, 받아 쓴 분, 심사 결과; 안좋아요·신고·누가는 없음", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const since = new Date(AFTER - 1000).toISOString();
  const a = (await up(call, 1, { title: "내 교대 세트" })).data.code;
  const b = (await up(call, 1, { title: "돌려받을 세트" })).data.code;
  await call("POST", `/api/admin/recipes/${a}/approve`, {}, admin);
  await call("POST", `/api/admin/recipes/${b}/return`, { note: "사진 속 글자가 흐려요" }, admin);
  t.mock.timers.tick(60_000);
  for (const n of [2, 3]) assert.equal((await call("POST", `/api/recipes/${a}/vote`, { device: device(n), value: 1 }, from(n))).status, 200);
  await call("POST", `/api/recipes/${a}/vote`, { device: device(4), value: -1, reason: "hard" }, from(4));
  const review = (await call("POST", `/api/recipes/${a}/talk`, { device: device(3), body: "한 달째 잘 쓰고 있어요", kind: "review", usingDays: 30 }, from(3))).data.id;
  const idea = (await call("POST", `/api/recipes/${a}/talk`, { device: device(2), body: "주말 색도 바꿀 수 있게 해 주세요", kind: "idea" }, from(2))).data.id;
  for (const n of [2, 3]) await call("POST", `/api/recipes/${a}/applied`, { kind: "applied" }, from(n));
  await call("POST", `/api/recipes/${a}/flag`, { device: device(5), reason: "spam" }, from(5));

  const box = (await call("GET", `/api/recipes/makers/me/inbox?since=${since}`, undefined, dev(1))).data;
  for (const line of [...box.likes, ...box.talk, ...box.uses, ...box.decided]) for (const k of COMMON) assert.ok(k in line, `${k} in ${JSON.stringify(line)}`);
  // 좋아요: one line a work, the 안좋아요 not counted nor told.
  assert.deepEqual(box.likes.map((x) => [x.kind, x.target, x.title, x.count]), [["like", a, "내 교대 세트", 2]]);
  // 후기·아이디어: each its own line, newest first, with the post to open.
  assert.deepEqual(box.talk.map((x) => [x.kind, x.target, x.post]), [["idea", a, idea], ["review", a, review]]);
  // 받아 쓴 분: since the day of since, with the whole number.
  assert.deepEqual(box.uses.map((x) => [x.kind, x.target, x.count, x.total]), [["use", a, 2, 2]]);
  // 심사 결과: 공개됨, 돌려받음과 그 까닭. The report is never told.
  const decided = Object.fromEntries(box.decided.map((x) => [x.target, x]));
  assert.equal(decided[a].state, "visible");
  assert.deepEqual([decided[b].state, decided[b].note], ["returned", "사진 속 글자가 흐려요"]);
  const json = JSON.stringify(box);
  assert.equal(json.includes("spam") || json.includes("dislike") || json.includes('"down"'), false, "no 안좋아요·신고");
  assert.equal([2, 3, 4, 5].some((n) => json.includes(device(n))), false, "never who");
  // Asking again from the server's time: nothing new but the day's 받아 쓴 분 (counted by the day; the phone says only the new ones).
  const again = (await call("GET", `/api/recipes/makers/me/inbox?since=${box.at}`, undefined, dev(1))).data;
  assert.deepEqual([again.likes.length, again.talk.length, again.decided.length], [0, 0, 0]);
  assert.deepEqual(again.uses.map((x) => x.total), [2]);
  // Someone else's works are not mine.
  const other = (await call("GET", `/api/recipes/makers/me/inbox?since=${since}`, undefined, dev(2))).data;
  assert.deepEqual([other.likes.length, other.talk.length, other.uses.length, other.decided.length], [0, 0, 0, 0]);
});

test("[RVM-28] 가려짐은 까닭(신고·운영)과 함께, 다시 보이면 '다시 보임'", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const since = new Date(AFTER - 1000).toISOString();
  const a = (await up(call, 1, { title: "가려질 세트" })).data.code;
  await call("POST", `/api/admin/recipes/${a}/approve`, {}, admin);
  t.mock.timers.tick(60_000);
  await call("POST", `/api/admin/recipes/${a}/hide`, { note: "다른 분 사진이 들어 있어요" }, admin);
  let box = (await call("GET", `/api/recipes/makers/me/inbox?since=${since}`, undefined, dev(1))).data;
  assert.deepEqual(box.decided.map((x) => [x.state, x.why, x.note]), [["hidden", "admin", "다른 분 사진이 들어 있어요"]]);
  t.mock.timers.tick(60_000);
  await call("POST", `/api/admin/recipes/${a}/show`, {}, admin);
  box = (await call("GET", `/api/recipes/makers/me/inbox?since=${since}`, undefined, dev(1))).data;
  assert.deepEqual(box.decided.map((x) => [x.state, x.again === true]), [["visible", true]]);
});
