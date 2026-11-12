// 2.0.0 [RVR-20] (검토 TEST-18) 이달의 주제 공모: the ways the one test of 1.36 did not walk — picking again (what it does to the
// makers' rewards), the month's three, a retry, a hidden work, the month's last minute (KST) and an admin without the right.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";

const NOV30 = Date.parse("2026-11-30T03:00:00Z"); // 12:00 KST
const APP = { "x-admin": "1", origin: ORIGIN };
const PERMS = { review: "none", inquiries: "none", dev: "none", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "view", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none", flags: "none", flagOff: "none" };

const recipe = (title) => ({
  format: "bamnat-recipe", v: 1, title, summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
});

async function contest(t, now = NOV30) {
  t.mock.timers.enable({ apis: ["Date"], now });
  const s = server({ plaza: true });
  const admin = await signIn(s.call);
  assert.equal((await s.call("POST", "/api/admin/village/contests", { style: "SHIFT", theme: "야간 근무용 어두운 테마" }, admin)).status, 200);
  const make = async (n, title) => (await s.call("POST", "/api/recipes", { device: device(n), recipe: recipe(title), scope: "public" }, from(n))).data.code;
  const enter = (n, code) => s.call("POST", "/api/village/contest/enter", { device: device(n), style: "SHIFT", code }, from(n));
  const see = (n) => s.call("GET", "/api/village/contest?style=SHIFT", undefined, { ...from(n), "x-device": device(n) });
  const pick = (codes, extra = {}) => s.call("POST", "/api/admin/village/contests/pick", { month: "2026-11", style: "SHIFT", codes, ...extra }, admin);
  const hide = (code) => s.DB.prepare("UPDATE recipes SET state = 'hidden', hidden_reason = 'admin' WHERE code = ?").bind(code).run();
  return { ...s, admin, make, enter, see, pick, hide };
}

test("[RVR-20] 다시 고르기: 저장 전 미리 보기가 새 보상과 빠지는 작품의 보상(대기·지급)을 말하고, 숨긴 작품은 못 골라요", async (t) => {
  const { call, admin, make, enter, pick, hide } = await contest(t);
  const a = await make(1, "야간 다크");
  const b = await make(2, "밤 하늘");
  for (const [n, code] of [[1, a], [2, b]]) assert.equal((await enter(n, code)).status, 201);
  assert.equal((await pick([a])).data.rewarded, 1);

  // Before saving: b's maker would get a new reward, a's waiting one stays (cancel it in 보상), and nothing changed yet.
  const dry = (await pick([b], { dry: true })).data;
  assert.deepEqual([dry.added, dry.dropped], [[{ code: b, reward: "new" }], [{ code: a, reward: "owed" }]]);
  const list = (await call("GET", "/api/admin/village/contests", undefined, admin)).data;
  assert.deepEqual(list.items.find((x) => x.month === "2026-11" && x.style === "SHIFT").picks, [a], "a dry run saves nothing");
  assert.equal((await call("GET", "/api/admin/rewards?status=owed&kind=maker", undefined, admin)).data.rewards.length, 1, "nor makes a reward");

  // Once a's is paid, the question says it cannot be taken back.
  const owed = (await call("GET", "/api/admin/rewards?status=owed&kind=maker", undefined, admin)).data.rewards;
  assert.equal((await call("POST", `/api/admin/rewards/${owed[0].id}`, { status: "paid" }, admin)).status, 200);
  assert.deepEqual((await pick([b], { dry: true })).data.dropped, [{ code: a, reward: "paid" }]);
  const saved = (await pick([b])).data;
  assert.deepEqual([saved.picks, saved.rewarded, saved.dropped], [[b], 1, [{ code: a, reward: "paid" }]]);
  // Picked again: a's maker had theirs, no second one.
  const again = (await pick([b, a], { dry: true })).data;
  assert.deepEqual(again.added, [{ code: a, reward: "before" }]);
  assert.equal((await pick([b, a])).data.rewarded, 0);

  // A work taken down after it was entered cannot be picked (it would show nowhere).
  await hide(b);
  assert.equal((await pick([b])).data.error, "codes");
  assert.equal((await pick([a])).status, 200);
});

test("[RVR-20] 한 달 세 개: 다시 보내도 201, 내려간 작품은 자리를 비우고 내 목록과 이름 목록이 같아요", async (t) => {
  const { make, enter, see, hide } = await contest(t);
  const mine = [];
  for (const title of ["하나", "둘", "셋", "넷"]) mine.push(await make(1, title));
  for (const code of mine.slice(0, 3)) assert.equal((await enter(1, code)).status, 201);
  assert.equal((await enter(1, mine[3])).data.error, "too_many");
  // The answer was lost and the phone sends the first again: it is in, not 'too many'.
  const retry = await enter(1, mine[0]);
  assert.deepEqual([retry.status, retry.data.entered], [201, true]);

  await hide(mine[1]);
  const seen = (await see(1)).data;
  assert.deepEqual(seen.mine.slice().sort(), [mine[0], mine[2]].sort(), "only the ones still up");
  assert.deepEqual(seen.mineTitles.map((m) => m.code).sort(), seen.mine.slice().sort(), "the same list, each with its name");
  assert.equal(seen.entries, 2);
  assert.equal((await enter(1, mine[3])).status, 201, "the hidden one's place is free again");
});

test("[RVR-20] 달 넘김: 11월 30일 23:59(한국 시각)에 넣은 것은 11월, 00:00부터는 12월이에요", async (t) => {
  const { make, enter, see } = await contest(t);
  const code = await make(1, "자정 직전");
  t.mock.timers.setTime(Date.parse("2026-11-30T14:59:00Z")); // 23:59 KST
  const late = await enter(1, code);
  assert.deepEqual([late.status, late.data.month], [201, "2026-11"]);
  t.mock.timers.setTime(Date.parse("2026-11-30T15:00:00Z")); // 12월 1일 00:00 KST
  assert.equal((await enter(1, code)).status, 404, "December has no theme yet");
  const dec = (await see(1)).data;
  assert.deepEqual([dec.month, dec.theme, dec.mine], ["2026-12", null, []]);
});

test("[RVR-20] 공모 고르기는 마을 편집 권한이 있는 관리자만", async (t) => {
  const { call, admin, make, enter } = await contest(t);
  const a = await make(1, "야간 다크");
  await enter(1, a);
  const inv = await call("POST", "/api/admin/invites", { email: "viewer@example.com", perms: PERMS }, admin);
  assert.equal(inv.status, 201, inv.text);
  const joined = await call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "보기만", password: "viewer-only-2026" }, { ...APP, ...from(41) });
  assert.equal(joined.status, 201, joined.text);
  const id = (await call("GET", "/api/admin/admins", undefined, admin)).data.admins.find((x) => x.email === "viewer@example.com").id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, admin);
  const res = await call("POST", "/api/admin/login", { email: "viewer@example.com", password: "viewer-only-2026" }, { ...APP, ...from(41) });
  const viewer = { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
  assert.equal((await call("GET", "/api/admin/village/contests", undefined, viewer)).status, 200, "may look");
  for (const extra of [{}, { dry: true }]) {
    const r = await call("POST", "/api/admin/village/contests/pick", { month: "2026-11", style: "SHIFT", codes: [a], ...extra }, viewer);
    assert.equal(r.status, 403, JSON.stringify(extra));
  }
  assert.equal((await call("POST", "/api/admin/village/contests", { style: "SHIFT", theme: "바꿈" }, viewer)).status, 403);
});
