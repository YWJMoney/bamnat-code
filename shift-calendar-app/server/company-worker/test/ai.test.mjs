// AI 1.27 [PLAZA-30][PLAZA-32][PLAZA-33][PLAZA-35]: 명세서 tick with what it rests on, 회사 정보 광장 (members only),
// the plazas of each kind of work, and 광장 소식 of new posts. Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readProof, proofCounts } from "../src/index.js";
import { server, report, device, from, signIn, slipProof } from "./helpers.mjs";

const as = (n) => ({ ...from(n), "x-device": device(n) });

test("[PLAZA-30] a 명세서 tick counts only with a matched month; an older app's bare tick is a plain report kept as a claim", async () => {
  const { call, DB } = server();
  // 1.27 with a matched month: 2 points and 명세서 확인.
  const a = await call("POST", "/api/reports", report(1, { company: "근거상사", verified: true }), from(1));
  assert.equal(a.status, 201);
  assert.deepEqual([a.data.points, a.data.verified], [2, true]);
  // A month that did not match: sent as it is, counted as a plain report.
  const b = await call("POST", "/api/reports", report(2, { company: "근거상사", verified: true, proof: slipProof(false) }), from(2));
  assert.deepEqual([b.data.points, b.data.verified], [3, false]);
  // An app before 1.27: the tick has nothing behind it.
  const c = await call("POST", "/api/reports", report(3, { company: "근거상사", dept: "2팀", verified: true, proof: null }), from(3));
  assert.deepEqual([c.data.points, c.data.verified], [1, false]);
  const rows = await DB.prepare("SELECT device, verified, claim, proof, points FROM reports ORDER BY created_at").all();
  assert.deepEqual(rows.results.map((r) => [r.verified, r.claim, r.points]), [[1, 0, 2], [0, 0, 1], [0, 1, 1]]);
  assert.match(rows.results[0].proof, /^\d{4}-\d{2}\|match$/);
  assert.match(rows.results[1].proof, /\|gap$/);
  // Nothing but the month and yes or no: an amount in the proof is not read, a wrong month is refused.
  assert.equal((await call("POST", "/api/reports", report(4, { verified: true, proof: { month: "2019-01", match: true } }), from(4))).data.error, "proof");
  assert.equal((await call("POST", "/api/reports", report(4, { verified: true, proof: { month: "2026-13", match: true } }), from(4))).data.error, "proof");
  assert.equal((await call("POST", "/api/reports", report(4, { verified: true, proof: { month: slipProof().month } }), from(4))).data.error, "proof");
  assert.equal((await call("POST", "/api/reports", report(4, { verified: true, proof: slipProof(true, -1) }), from(4))).data.error, "proof");
  assert.equal(proofCounts(readProof({ month: slipProof().month, match: true, gross: 3000000 })), true);
  assert.equal(JSON.stringify(readProof({ month: slipProof().month, match: true, gross: 3000000 })).includes("3000000"), false);
  // 맞았어요 · 명세서로 확인: the same rule.
  const vote = (n, extra) => call("POST", "/api/votes", { device: device(n), company: a.data.company.id, setup: a.data.setup, ok: true, verified: true, ...extra }, from(n));
  await vote(5, { proof: slipProof() });
  await vote(6, {});
  const votes = await DB.prepare("SELECT verified, claim FROM votes ORDER BY at").all();
  assert.deepEqual(votes.results.map((v) => [v.verified, v.claim]), [[1, 0], [0, 1]]);
});

test("[PLAZA-32] 회사 정보 광장: only the company's members read and write, a day's posts are held, flags and the developer hide", async () => {
  const { call } = server();
  const sent = (await call("POST", "/api/reports", report(1, { company: "토의상사" }), from(1))).data;
  const id = sent.company.id;
  const path = `/api/companies/${id}/setups/room/posts`;
  const write = (n, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: id, setup: "room", body, ...extra }, from(n));
  // The sender joined by sending: an empty room, ten posts a day.
  let room = (await call("GET", path, undefined, as(1))).data;
  assert.deepEqual([room.posts.length, room.room, room.member, room.left.posts], [0, "company", true, 10]);
  const first = await write(1, "야간 끝나고 주차장 통근버스 몇 시에 와요?", { kind: "question" });
  assert.equal(first.status, 201);
  // A phone that did not join, and an app with no install id, cannot read it or write in it.
  assert.equal((await call("GET", path, undefined, as(2))).status, 403);
  assert.equal((await call("GET", path, undefined, from(2))).status, 400);
  assert.equal((await write(2, "저도 궁금해요")).status, 403);
  // Joining (applying the setup) opens it: the post is there, the writer is 참여자 n, never named.
  await call("POST", "/api/uses", { device: device(2), company: id, setup: sent.setup, cal: "main" }, from(2));
  room = (await call("GET", path, undefined, as(2))).data;
  assert.equal(room.posts.length, 1);
  assert.equal(room.posts[0].kind, "question");
  assert.equal(room.posts[0].who, 1);
  assert.equal(JSON.stringify(room).includes(device(1)), false);
  const reply = await write(2, "6시 40분에 와요", { parent: first.data.id });
  assert.equal(reply.status, 201);
  assert.equal((await call("GET", path, undefined, as(1))).data.posts[1].parent, first.data.id);
  // The words rules of every post: no amount, phone or mail.
  assert.equal((await write(2, "기본급 2,800,000원 받아요")).data.error, "amount");
  // Ten a day for one phone.
  for (let i = 0; i < 9; i++) assert.equal((await write(2, `교대 이야기 ${i}번째예요`)).status, 201);
  assert.equal((await write(2, "열한 번째 글이에요")).status, 429);
  assert.equal((await call("GET", path, undefined, as(2))).data.left.posts, 0);
  // Three people's 신고 hide a post until the developer looks; the developer sees it as 회사 정보 광장 and can hide any.
  for (const n of [3, 4]) await call("POST", "/api/uses", { device: device(n), company: id, setup: sent.setup, cal: "main" }, from(n));
  const bad = (await write(3, "저 사람 진짜 이상해요")).data.id;
  for (const n of [1, 2, 4]) await call("POST", `/api/posts/${bad}/flag`, { device: device(n), reason: "abuse" }, from(n));
  assert.equal((await call("GET", path, undefined, as(1))).data.posts.some((p) => p.id === bad), false);
  const h = await signIn(call);
  const list = (await call("GET", "/api/admin/posts?status=room", undefined, h)).data;
  assert.ok(list.posts.every((p) => p.setup === "room" && p.room === "company" && p.company.name === "토의상사"));
  const whole = (await call("GET", `/api/admin/posts?company=${id}&setup=room`, undefined, h)).data;
  assert.equal(whole.status, "talk");
  assert.equal(whole.posts.length, 12);
  assert.equal((await call("POST", `/api/admin/posts/${first.data.id}`, { action: "hide", reason: "offtopic" }, h)).data.status, "hidden");
  assert.equal((await call("GET", path, undefined, as(2))).data.posts.some((p) => p.id === first.data.id), false);
  // 내가 보낸 것 모두 지우기 takes the room posts with the rest.
  const gone = (await call("DELETE", "/api/mine", undefined, as(2))).data;
  assert.equal(gone.posts, 10);
});

test("[PLAZA-33] the plaza of each kind of work: any phone with the app, the 투자 이야기 takes no 종목 추천", async () => {
  const { call } = server();
  const write = (n, place, body) => call("POST", "/api/posts", { device: device(n), company: place, setup: "room", body }, from(n));
  assert.equal((await write(1, "style-field", "인천 쪽 타일 단가 요즘 어때요?")).status, 201);
  const field = (await call("GET", "/api/companies/style-field/setups/room/posts", undefined, as(2))).data;
  assert.deepEqual([field.room, field.company.name, field.posts.length], ["style", "현장 광장", 1]);
  assert.equal((await call("GET", "/api/companies/style-field/setups/room/posts", undefined, from(2))).status, 400);
  assert.equal((await write(2, "style-shop", "평일 점심 손님이 지난달보다 10% 늘었어요")).status, 201);
  assert.equal((await write(2, "style-invest", "분할 매수할 때 몇 번에 나눠 사세요?")).status, 201);
  // [RCP-34] 1.34: 교대·일근 have their own plaza too.
  assert.equal((await write(2, "style-shift", "야간 끝나고 잠 어떻게 자요?")).status, 201);
  assert.equal((await write(2, "style-day", "점심시간 1시간 다 쉬세요?")).status, 201);
  const shift = (await call("GET", "/api/companies/style-shift/setups/room/posts", undefined, as(3))).data;
  assert.deepEqual([shift.room, shift.company.name, shift.posts.length], ["style", "교대 광장", 1]);
  for (const advice of ["리딩방 들어오시면 수익 보장해요", "급등주 추천 드려요 오픈채팅으로 오세요", "이 종목 추천 드립니다"]) {
    assert.equal((await write(3, "style-invest", advice)).data.error, "invest_advice", advice);
  }
  assert.equal((await write(3, "style-nothing", "아무 데나")).status, 404);
  // A rule-set id is not a room, and a topic is not a company with a room.
  const t = (await call("POST", "/api/reports", report(4, { company: "", job: "construction", topic: true }), from(4))).data;
  assert.equal((await write(4, t.company.id, "공통 규칙 이야기")).status, 404);
});

test("[PLAZA-35] 광장 소식: new posts in a joined company's room, not one's own, as one line with the count", async () => {
  const { call } = server();
  const sent = (await call("POST", "/api/reports", report(1, { company: "소식상사" }), from(1))).data;
  await call("POST", "/api/uses", { device: device(2), company: sent.company.id, setup: sent.setup, cal: "main" }, from(2));
  const since = new Date(Date.now() - 1000).toISOString();
  for (const [n, body] of [[1, "첫째 이야기예요"], [1, "둘째 이야기예요"], [2, "셋째 이야기예요"]]) {
    assert.equal((await call("POST", "/api/posts", { device: device(n), company: sent.company.id, setup: "room", body }, from(n))).status, 201);
  }
  const news = (await call("GET", `/api/plaza/news?since=${since}`, undefined, as(2))).data.items.filter((i) => i.kind === "room_posts");
  assert.deepEqual(news.map((i) => [i.company.name, i.count]), [["소식상사", 2]]);
  const mine = (await call("GET", `/api/plaza/news?since=${since}`, undefined, as(1))).data.items.filter((i) => i.kind === "room_posts");
  assert.deepEqual(mine.map((i) => i.count), [1]);
});
