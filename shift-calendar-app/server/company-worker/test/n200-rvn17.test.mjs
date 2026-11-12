// 2.0.0 갈래 N 3차 [RVN-17]: 회사를 잘못 골랐을 때 한 번 풀기(COMP-12·U10a-12), 광장 방 글의 금액(COMP-14). Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { MEMBER, contact, personal } from "../src/index.js";
import { server, report, device, from, signIn } from "./helpers.mjs";

const DAY = 24 * 60 * 60 * 1000;

test("[RVN-17] COMP-12 a calendar held by a company picked by mistake is freed once a year with '잘못 골랐어요', and the wait says which company", async (t) => {
  // [RVR-13] One clock: the server's Date, fixed here and moved on by hand.
  const at = Date.parse("2026-12-01T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: at });
  const { call } = server();
  const ids = [];
  for (const [n, name] of [[1, "가나상사"], [2, "다라상사"], [3, "마바상사"]]) ids.push((await call("POST", "/api/reports", report(n, { company: name }), from(n))).data);
  const join = (company, cal = "app") => call("POST", "/api/members", { device: device(9), company, cal }, from(9));
  const undo = (cal = "app") => call("POST", "/api/members/undo", { device: device(9), cal }, from(9));
  // Before joining, the page says the 30 days and that a mistake can be undone.
  const page = (await call("GET", `/api/companies/${ids[0].company.id}`, undefined, from(9))).data;
  assert.deepEqual(page.join, { perPhone: 2, waitDays: 30, undo: true });
  assert.equal((await join(ids[0].company.id)).data.joined, true);
  // A look before choosing (dry): nothing is written, and it already says the calendar is held.
  const look = await call("POST", "/api/members", { device: device(9), company: ids[1].company.id, cal: "app", dry: true }, from(9));
  assert.deepEqual([look.status, look.data.error, look.data.held.name, look.data.undo], [409, "member_wait", "가나상사", true]);
  const free = await call("POST", "/api/members", { device: device(9), company: ids[1].company.id, cal: "other", dry: true }, from(9));
  assert.deepEqual([free.status, free.data.free, free.data.joined], [200, true, false]);
  assert.equal((await call("GET", "/api/members", undefined, { "x-device": device(9) })).data.members.length, 1, "a look joins nothing");
  // Another company on the same calendar: the wait names the company holding it and offers the undo.
  const wait = await join(ids[1].company.id);
  assert.deepEqual([wait.status, wait.data.error, wait.data.held.name, wait.data.undo], [409, "member_wait", "가나상사", true]);
  // 잘못 골랐어요: freed at once, and the other company joins.
  const freed = await undo();
  assert.deepEqual([freed.status, freed.data.undone, freed.data.company], [200, true, ids[0].company.id]);
  assert.equal((await call("GET", `/api/companies/${ids[0].company.id}`, undefined, { "x-device": device(9) })).data.member, false);
  assert.equal((await join(ids[1].company.id)).data.joined, true);
  // Once a year: the next wait offers no undo, and asking anyway says when it opens again.
  const again = await join(ids[2].company.id);
  assert.deepEqual([again.data.error, again.data.undo], ["member_wait", undefined]);
  // PC-155 …and says why: the day the undo opens again (a look before choosing says the same).
  assert.equal(Date.parse(again.data.undoAgain), at + 365 * DAY);
  const lookAgain = await call("POST", "/api/members", { device: device(9), company: ids[2].company.id, cal: "app", dry: true }, from(9));
  assert.deepEqual([lookAgain.data.undo, Date.parse(lookAgain.data.undoAgain)], [undefined, at + 365 * DAY]);
  assert.equal(wait.data.undoAgain, undefined, "while the undo is open, no day is said");
  const used = await undo();
  assert.deepEqual([used.status, used.data.error], [409, "undo_used"]);
  assert.equal(Date.parse(used.data.again), at + 365 * DAY);
  // A calendar whose 30 days are over is free already: nothing is used up.
  t.mock.timers.tick(31 * DAY);
  assert.deepEqual((await undo()).data, { undone: false, free: true });
  // One value closes the way: no offer, and the undo says closed.
  MEMBER.undoOnce = false;
  try {
    const p = (await call("GET", `/api/companies/${ids[0].company.id}`, undefined, from(9))).data;
    assert.equal(p.join.undo, undefined);
    assert.equal((await call("POST", "/api/members/undo", { device: device(8), cal: "app" }, from(8))).status, 403);
  } finally {
    MEMBER.undoOnce = true;
  }
});

test("[RVN-17] COMP-14 a room's post takes amounts once 방 글에 금액 받기 is on; contacts never, a rule's talk keeps its rule", async () => {
  const { call } = server();
  const h = await signIn(call);
  const write = (n, place, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: place, setup: "room", body, ...extra }, from(n));
  // Off (the default until 운영 규칙 4항 changes): as before.
  assert.equal((await call("GET", "/api/terms")).data.roomMoney, false);
  assert.equal((await write(1, "style-shift", "야간 수당 한 달에 50만원 정도 나와요")).data.error, "amount");
  // On: the amount goes, and a poll may hold one.
  const on = await call("POST", "/api/admin/settings", { adoptPoints: 3, rewardMonths: 3, roomMoney: 1 }, h);
  assert.equal(on.data.roomMoney, 1);
  assert.equal((await call("GET", "/api/terms")).data.roomMoney, true);
  assert.equal((await write(1, "style-shift", "야간 수당 한 달에 50만원 정도 나와요")).status, 201);
  assert.equal((await write(2, "style-shift", "월급이 얼마쯤이에요?", { kind: "question", poll: ["200만원 아래", "200~300만원", "300만원 넘게"] })).status, 201);
  // A way to reach someone is still held back, and a big number is not taken for a phone number.
  assert.equal((await write(3, "style-shift", "궁금하면 010-1234-5678로 연락 주세요")).data.error, "phone");
  assert.equal((await write(3, "style-shift", "메일 a.b@example.com 주세요")).data.error, "email");
  assert.equal((await write(3, "style-shift", "연봉 300000000원이면 좋겠다")).status, 201);
  // A rule's talk is about the rule: amounts stay out there.
  const sent = (await call("POST", "/api/reports", report(4, { company: "규칙상사" }), from(4))).data;
  const talk = await call("POST", "/api/posts", { device: device(4), company: sent.company.id, setup: sent.setup, body: "기본급 250만원이에요" }, from(4));
  assert.equal(talk.data.error, "amount");
  // An admin app from before leaves it as it was; off again is one value.
  assert.equal((await call("POST", "/api/admin/settings", { adoptPoints: 3, rewardMonths: 3 }, h)).data.roomMoney, 1);
  assert.equal((await call("POST", "/api/admin/settings", { adoptPoints: 3, rewardMonths: 3, roomMoney: 0 }, h)).data.roomMoney, 0);
  assert.equal((await write(1, "style-shift", "주휴 포함 시급 12,000원이에요")).data.error, "amount");
  assert.equal((await call("POST", "/api/admin/settings", { adoptPoints: 3, rewardMonths: 3, roomMoney: 2 }, h)).data.error, "roomMoney");
});

test("[RVN-17] COMP-14 the room check: a real phone number's shape, not any run of digits", () => {
  for (const [t, want] of [["010-1234-5678", "phone"], ["+82 10 1234 5678", "phone"], ["02-123-4567", "phone"], ["031 1234 5678", "phone"],
    ["3억은 300000000원", null], ["1,500,000원", null], ["야간 50%", null], ["a@b.co", "email"]]) {
    assert.equal(contact(t), want, t);
  }
  // The rest of the server keeps its rule.
  assert.equal(personal("1,500,000원"), "amount");
});
