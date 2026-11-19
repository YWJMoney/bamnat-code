// 2.0.0 [RVN-24] 갈래 N 8차 (검토 22c 서버 광장·운영·푸시): 오늘 출근의 한도, 근무형태별 투표의 남은 묶음, 광장 효과 숫자, 답 모음, 출시 점검표,
// 사고 타임라인, 제작자 후보, 출시 노트 이름, 스튜디오 요구 상태. 발견마다 그 문제를 재현한 시험 하나(LOOP-21). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-18T03:00:00Z");
const DAY = 24 * 3600e3;

async function policyOn(call) {
  const h = await signIn(call);
  const r = await call("POST", "/api/admin/policy", { confirm: true, version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, h);
  assert.equal(r.status, 200, JSON.stringify(r.data));
  return h;
}

test("[RVN-24] SVC-93 '오늘 야간 출근 n명' cannot be filled with new install ids from one network", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  await policyOn(call);
  const net = { "cf-connecting-ip": "198.51.100.77" };
  // A new install's hourly share of the network's day (300 / 4), then 429 with the network named.
  let last = null;
  for (let n = 1; n <= 76; n++) last = await call("POST", "/api/plaza/checkin", { device: device(n), room: "style-shift", kind: "night" }, net);
  assert.equal(last.status, 429, JSON.stringify(last.data));
  assert.equal(last.data.why, "network");
  // Taking one's own tap back is never held by the network.
  const back = await call("POST", "/api/plaza/checkin", { device: device(1), room: "style-shift", kind: null }, net);
  assert.equal(back.status, 200, JSON.stringify(back.data));
  assert.equal(back.data.counts.night, 74);
});

test("[RVN-24] SVC-93 while 무결성 확인 asks, a tap waits for Play's verdict; taking it back does not", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ INTEGRITY_MODE: "require" });
  await policyOn(call);
  const r = await call("POST", "/api/plaza/checkin", { device: device(1), room: "style-shift", kind: "night" });
  assert.equal(r.status, 428);
  assert.equal(r.data.error, "integrity");
  const back = await call("POST", "/api/plaza/checkin", { device: device(1), room: "style-shift", kind: null });
  assert.equal(back.status, 200);
});

test("[RVN-24] SVC-93 a post may be reported for a 근무 태그 that is not so", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  await policyOn(call);
  const post = await call("POST", "/api/posts", { device: device(1), company: "style-shift", setup: "room", body: "소방 3교대 이야기예요", tag: { s: "SHIFT", f: "THREE_SHIFT", i: "fire" } },
    { "cf-connecting-ip": "198.51.100.1" });
  assert.equal(post.status, 201, JSON.stringify(post.data));
  const id = post.data.post?.id ?? post.data.id;
  const flag = await call("POST", `/api/posts/${id}/flag`, { device: device(2), reason: "tag" }, { "cf-connecting-ip": "198.51.100.2" });
  assert.equal(flag.status, 200, JSON.stringify(flag.data));
  const h = await signIn(call);
  // The developer app names it in words.
  const basics = await call("GET", "/api/admin/basics", undefined, h);
  assert.equal(basics.status, 200);
  assert.equal(basics.data.flagReasons.tag, "근무 태그가 사실과 달라요");
});

test("[RVN-24] SVC-94 근무형태별 투표: a rest of 1~4 never shows as whole − shown", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  await policyOn(call);
  const ask = await call("POST", "/api/posts", { device: device(1), company: "style-shift", setup: "room", body: "야간 끝나고 바로 자요?", kind: "question",
    poll: ["바로 자요", "좀 있다 자요"] }, { "cf-connecting-ip": "198.51.100.1" });
  const id = ask.data.id;
  const vote = (n, choice, f) => call("POST", `/api/posts/${id}/poll`, { device: device(n), choice, ...(f ? { tag: { s: "SHIFT", f } } : {}) },
    { "cf-connecting-ip": `198.51.100.${n}` });
  // Five 3교대, then one 당직: before, 3교대 stood apart and the whole poll gave the 당직's choice away (6 − 5).
  for (const n of [10, 11, 12, 13, 14]) await vote(n, 0, "THREE_SHIFT");
  let r = await vote(20, 1, "ON_CALL");
  assert.deepEqual(r.data.poll.counts, [5, 1]);
  assert.equal(r.data.poll.byForm, undefined, "no 근무형태 left on its own: no split");
  // Five 4조 2교대 more: the smallest shown (3교대, 5) takes the 당직 in as 그 밖의 근무, so every shown group is 5 or more and they add up.
  for (const n of [30, 31, 32, 33]) await vote(n, 1, "FOUR_TEAM");
  r = await vote(34, 0, "FOUR_TEAM");
  assert.deepEqual(r.data.poll.byForm, [{ f: "FOUR_TEAM", counts: [1, 4] }, { f: null, counts: [5, 1] }]);
  const sum = r.data.poll.byForm.reduce((a, g) => a.map((n, i) => n + g.counts[i]), [0, 0]);
  assert.deepEqual(sum, r.data.poll.counts);
  for (const g of r.data.poll.byForm) assert.ok(g.counts[0] + g.counts[1] >= 5, JSON.stringify(g));
});

test("[RVN-24] SVC-95 광장 효과 counts what is shown, the 태그 share of top posts, and says 아직 적음 under 10", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await policyOn(call);
  const at = (n) => ({ "cf-connecting-ip": `198.51.100.${n}` });
  const ids = [];
  for (let n = 1; n <= 10; n++) {
    const r = await call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body: `야간 뒤에 뭐 드세요 ${n}`, kind: "question",
      ...(n <= 5 ? { tag: { s: "SHIFT", f: "FOUR_TEAM" } } : {}) }, at(n));
    assert.equal(r.status, 201, JSON.stringify(r.data));
    ids.push(r.data.id);
  }
  for (let n = 0; n < 5; n++) {
    await call("POST", "/api/posts", { device: device(40 + n), company: "style-shift", setup: "room", body: "저는 죽 먹어요", parent: ids[n],
      tag: { s: "SHIFT", f: "FOUR_TEAM" } }, at(40 + n));
  }
  // One more post the server hid (도배, or 신고 after the operator looked): it is no longer a post of the day.
  const spam = await call("POST", "/api/posts", { device: device(90), company: "style-shift", setup: "room", body: "광고 글이에요 연락 주세요" }, at(90));
  await DB.prepare("UPDATE posts SET status = 'hidden', hidden_at = ? WHERE id = ?").bind(NOW, spam.data.id).run();
  const r = await call("GET", "/api/admin/plaza/effect", undefined, h);
  assert.equal(r.status, 200);
  const s = r.data.items.find((x) => x.room === "style-shift");
  assert.equal(s.posts, 10, "the hidden one is not counted");
  assert.equal(s.hidden, 1);
  assert.equal(s.writers, 15);
  assert.equal(s.tagged, 50, "5 of the 10 top posts; the 5 tagged answers are not in it");
  assert.equal(s.questions, 10);
  assert.equal(s.answeredPct, 50);
  assert.equal(s.cameBackPct, null, "no first writer 14 days back yet");
  assert.equal(r.data.few, 10);
  // Every plaza in one batch, not four reads each.
  assert.equal(r.data.items.length, 6);
});

test("[RVN-24] SVC-96 답 모음: a full room still takes a new title, 활동 기록 says which answer, a deleted answer leaves by itself", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await policyOn(call);
  const at = (n) => ({ "cf-connecting-ip": `198.51.100.${n % 250}` });
  const answers = [];
  for (let n = 0; n < 31; n++) {
    const q = await call("POST", "/api/posts", { device: device(1000 + n), company: "style-shift", setup: "room", body: `야간 질문이에요 ${n}`, kind: "question" }, at(n));
    const a = await call("POST", "/api/posts", { device: device(2000 + n), company: "style-shift", setup: "room", body: `이렇게 해 보세요 ${n}`, parent: q.data.id }, at(n + 100));
    answers.push(a.data.id);
  }
  for (let n = 0; n < 30; n++) {
    t.mock.timers.tick(1000);
    const r = await call("POST", "/api/admin/plaza/picks", { answer: answers[n], title: `선배 답 ${n}` }, h);
    assert.equal(r.status, 200, JSON.stringify(r.data));
  }
  // Full: a new one is refused with the oldest named, so the operator can make room.
  const full = await call("POST", "/api/admin/plaza/picks", { answer: answers[30], title: "새 답" }, h);
  assert.equal(full.status, 409);
  assert.equal(full.data.oldest.title, "선배 답 0");
  // A pick already there takes a new title (and a topic) even so.
  const retitle = await call("POST", "/api/admin/plaza/picks", { answer: answers[5], title: "야간 뒤 바로 잠드는 법", topic: "sleep" }, h);
  assert.equal(retitle.status, 200, JSON.stringify(retitle.data));
  assert.equal(retitle.data.updated, true);
  assert.equal((await call("POST", "/api/admin/plaza/picks", { answer: answers[5], title: "제목", topic: "lotto" }, h)).data.error, "topic");
  const app = (await call("GET", "/api/plaza/picks?room=style-shift", undefined, { "x-device": device(9) })).data.items;
  assert.equal(app.length, 30);
  assert.deepEqual(app.filter((p) => p.title === "야간 뒤 바로 잠드는 법").map((p) => p.topic), ["sleep"]);
  // 활동 기록: which answer, under what title.
  const log = await DB.prepare("SELECT action, target, detail FROM audit WHERE action LIKE 'plaza_pick%' ORDER BY rowid").all();
  const rows = log.results;
  assert.equal(rows.filter((r) => r.action === "plaza_pick").length, 30);
  const titled = rows.find((r) => r.action === "plaza_pick_title");
  assert.match(titled.detail, new RegExp(`답 ${answers[5]} ‘야간 뒤 바로 잠드는 법’ · 수면`));
  assert.equal(titled.target, "style-shift");
  // The writer deletes an answer: the next listing takes its pick out and says so once; 활동 기록 marks it 자동 (no admin).
  await DB.prepare("DELETE FROM posts WHERE id = ?").bind(answers[7]).run();
  let list = await call("GET", "/api/admin/plaza/picks", undefined, h);
  assert.deepEqual(list.data.removed, [{ room: "style-shift", title: "선배 답 7" }]);
  const shift = list.data.items.find((x) => x.room === "style-shift");
  assert.equal(shift.picks.length, 29);
  assert.equal(shift.full, false);
  list = await call("GET", "/api/admin/plaza/picks", undefined, h);
  assert.deepEqual(list.data.removed, []);
  const gone = (await DB.prepare("SELECT actor, actor_id, detail FROM audit WHERE action = 'plaza_pick_gone'").all()).results;
  assert.equal(gone.length, 1);
  assert.equal(gone[0].actor_id, "");
  // Removing one says which.
  const id = shift.picks.find((p) => p.title === "선배 답 3").id;
  assert.equal((await call("DELETE", `/api/admin/plaza/picks/${id}`, undefined, h)).status, 200);
  const removed = (await DB.prepare("SELECT detail FROM audit WHERE action = 'plaza_pick_remove'").all()).results;
  assert.match(removed[0].detail, /‘선배 답 3’/);
});
