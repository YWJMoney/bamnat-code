// 2.0.0 [RVQ-55] 검토 SVC-16: 마을 쉬기·의견 쉬기를 앱만 막고 서버는 받던 쓰기 길(따르기, 부탁 좋아요, 만든 분 소개·얼굴, 모음,
// 사진, 글꼴, 이달의 공모 넣기)을 서버도 막아요. 거두기(그만 따르기·좋아요 취소·공모에서 빼기)와 숨기기는 그대로예요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { deviceHash } from "../src/index.js";
import { server, device, from, signIn } from "./helpers.mjs";

const NOV30 = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400e3;
const recipe = (title) => ({
  format: "bamnat-recipe", v: 1, title, summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
});

async function setup(t) {
  t.mock.timers.enable({ apis: ["Date"], now: NOV30 });
  const s = server({ plaza: true });
  const admin = await signIn(s.call);
  assert.equal((await s.call("POST", "/api/admin/village/contests", { style: "SHIFT", theme: "야간 근무용 어두운 테마" }, admin)).status, 200);
  const as = (n) => (method, path, body = {}) => s.call(method, path, { device: device(n), ...body }, from(n));
  const hold = async (n, kind) => s.DB.prepare(
    "INSERT INTO care_notes (id, device, kind, reason, note, days, at, until, by, ref, lifted_at) VALUES (?, ?, ?, 'abuse', NULL, 3, ?, ?, 'operator', NULL, NULL)")
    .bind(`h${n}${kind}`, await deviceHash(device(n)), kind, NOV30 - 1000, NOV30 + 3 * DAY).run();
  return { ...s, as, hold };
}

const muted = (r, scope) => {
  assert.equal(r.status, 403, JSON.stringify(r.data));
  assert.equal(r.data.error, "muted");
  assert.equal(r.data.hold.scope, scope);
};

test("[RVQ-55] SVC-16 마을 쉬기 중에는 서버가 마을 쓰기를 모두 막고, 거두기는 그대로 돼요", async (t) => {
  const { as, hold } = await setup(t);
  const me = as(1);
  // 쉬기 전에 해 둔 것: 작품 하나를 공모에 넣고, 남의 부탁에 좋아요, 운영자 따르기.
  const code = (await me("POST", "/api/recipes", { recipe: recipe("야간 다크"), scope: "public" })).data.code;
  assert.equal((await me("POST", "/api/village/contest/enter", { style: "SHIFT", code })).status, 201);
  const q = (await as(2)("POST", "/api/recipes/requests", { style: "SHIFT", body: "간호사 3교대 알람 세트 있었으면 좋겠어요" })).data.id;
  assert.equal((await me("POST", `/api/recipes/requests/${q}/like`)).status, 200);
  assert.equal((await me("POST", "/api/recipes/makers/operator/follow")).status, 200);
  const other = (await me("POST", "/api/recipes", { recipe: recipe("밤 하늘"), scope: "public" })).data.code;

  await hold(1, "community");
  // 새로 쓰는 것은 모두 403 muted(옛 앱도 아는 말).
  muted(await me("POST", "/api/village/contest/enter", { style: "SHIFT", code: other }), "community");
  muted(await me("POST", `/api/recipes/requests/${q}/take`), "community");
  muted(await me("POST", "/api/recipes/makers/operator/follow"), "community");
  muted(await me("POST", "/api/recipes/makers/me", { bio: "야간 근무용 테마를 만들어요" }), "community");
  muted(await me("POST", "/api/recipes/makers/me", { color: 3 }), "community");
  muted(await me("POST", "/api/recipes/sets", { title: "야간 모음", codes: [code] }), "community");
  muted(await me("POST", "/api/recipes/images", { webp: "UklGRg==" }), "community");
  muted(await me("POST", "/api/recipes/fonts", { name: "밤 글꼴", license: "OFL" }), "community");

  // 거두기는 돼요: 공모에서 빼기, 좋아요 취소, 그만 따르기.
  assert.equal((await me("POST", "/api/village/contest/enter", { style: "SHIFT", code, on: false })).data.entered, false);
  assert.equal((await me("POST", `/api/recipes/requests/${q}/like`, { on: false })).status, 200);
  assert.equal((await me("POST", "/api/recipes/makers/operator/follow", { on: false })).data.following, false);
  // 쉬지 않는 분은 그대로.
  assert.equal((await as(3)("POST", "/api/recipes/makers/operator/follow")).status, 200);
});

test("[RVQ-55] SVC-16 의견 쉬기는 남이 읽는 글(소개·모음)만 막고, 따르기·좋아요·공모 넣기는 돼요", async (t) => {
  const { as, hold } = await setup(t);
  const me = as(1);
  const code = (await me("POST", "/api/recipes", { recipe: recipe("야간 다크"), scope: "public" })).data.code;
  const q = (await as(2)("POST", "/api/recipes/requests", { style: "SHIFT", body: "간호사 3교대 알람 세트 있었으면 좋겠어요" })).data.id;
  await hold(1, "opinion");
  muted(await me("POST", "/api/recipes/makers/me", { bio: "야간 근무용 테마를 만들어요" }), "opinion");
  muted(await me("POST", "/api/recipes/sets", { title: "야간 모음", codes: [code] }), "opinion");
  assert.equal((await me("POST", "/api/recipes/makers/operator/follow")).status, 200);
  assert.equal((await me("POST", `/api/recipes/requests/${q}/like`)).status, 200);
  assert.equal((await me("POST", "/api/village/contest/enter", { style: "SHIFT", code })).status, 201);
});

test("[RVQ-55] SVC-16 이달의 공모 넣기는 한 네트워크가 하루 30번까지예요", async (t) => {
  const { call } = await setup(t);
  const net = { "cf-connecting-ip": "198.51.100.77" };
  let last;
  for (let i = 0; i < 31; i++) last = await call("POST", "/api/village/contest/enter", { device: device(100 + i), style: "SHIFT", code: "ABCD2345" }, net);
  assert.equal(last.status, 429, JSON.stringify(last.data));
});
