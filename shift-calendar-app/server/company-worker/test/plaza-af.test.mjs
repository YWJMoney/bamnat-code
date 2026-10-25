// [PLAZA-07][PLAZA-08][PLAZA-09][PLAZA-10] 1.26 묶음 AF (결정 D79): 회사 참여, 참여한 휴대폰에만 급여 방식, 회사 방과 안정,
// 첫 제공자의 첫 판과 새 정보 보상. Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { shiftLines, shiftsOnly, describe } from "../src/index.js";
import { server, report, setup, device, from, signIn } from "./helpers.mjs";

const DAY = 24 * 60 * 60 * 1000;
const as = (n) => ({ ...from(n), "x-device": device(n) });

/** Shifts and their order only: what a phone that did not join a company may know. */
function cycleOnly() {
  const s = setup();
  delete s.pay;
  return s;
}

test("[PLAZA-08] a phone that did not join sees shifts and 급여 방식 있음, never the pay; an old app likewise", async () => {
  const { call } = server();
  const a = await call("POST", "/api/reports", report(1, { company: "참여상사" }), from(1));
  const c = a.data.company.id;
  for (const headers of [{}, as(9)]) {
    const page = (await call("GET", `/api/companies/${c}`, undefined, headers)).data;
    assert.equal(page.member, false);
    const s = page.setups[0];
    assert.equal(s.setup.pay, undefined);
    assert.deepEqual(s.setup.rotation.steps, setup().rotation.steps);
    assert.deepEqual(s.setup.shiftTypes.map((t) => t.name), ["주간", "야간", "휴무"]);
    assert.deepEqual([s.pay, s.payHidden], [true, true]);
    assert.deepEqual(s.lines.map(([l]) => l), ["근무", "순서 8일", "연장·휴무일 근무"]);
    assert.ok(!JSON.stringify(page).includes("가산"), "no pay words for a non-member");
  }
  // The sender joined by sending: the pay comes.
  const mine = (await call("GET", `/api/companies/${c}`, undefined, as(1))).data;
  assert.equal(mine.member, true);
  assert.equal(mine.setups[0].setup.pay.rules.extra[3].unit, "BASE_SHARE");
  assert.equal(mine.setups[0].payHidden, undefined);
  assert.ok(mine.setups[0].lines.some(([l]) => l === "가산율"));
  // Pure helpers.
  assert.equal(shiftsOnly({ a: 1, pay: { x: 1 } }).pay, undefined);
  assert.deepEqual(shiftLines(describe(setup())).map(([l]) => l), ["근무", "순서 8일", "연장·휴무일 근무"]);
});

test("[PLAZA-07] applying is joining, a calendar keeps its company 30 days, a phone joins two at most", async () => {
  const { call, DB } = server();
  const ids = [];
  for (const [n, name] of [[1, "가나상사"], [2, "다라상사"], [3, "마바상사"]]) ids.push((await call("POST", "/api/reports", report(n, { company: name }), from(n))).data);
  const join = (company, cal) => call("POST", "/api/members", { device: device(9), company, cal }, from(9));
  // An app before 1.26 applies without saying a calendar: it joins for "app".
  const used = (await call("POST", "/api/uses", { device: device(9), company: ids[0].company.id, setup: ids[0].setup }, from(9))).data;
  assert.equal(used.member.joined, true);
  assert.equal((await call("GET", `/api/companies/${ids[0].company.id}`, undefined, as(9))).data.member, true);
  // Another company on the same calendar: not for 30 days.
  const wait = await join(ids[1].company.id, "app");
  assert.equal(wait.status, 409);
  assert.equal(wait.data.error, "member_wait");
  assert.ok(Date.parse(wait.data.until) > Date.now() + 29 * DAY);
  // Another calendar may join another company, but not a third.
  assert.equal((await join(ids[1].company.id, "2")).data.joined, true);
  const limit = await join(ids[2].company.id, "3");
  assert.deepEqual([limit.status, limit.data.error], [409, "member_limit"]);
  // The same company again is no change.
  assert.equal((await join(ids[1].company.id, "2")).data.joined, true);
  let mine = (await call("GET", "/api/members", undefined, { "x-device": device(9) })).data;
  assert.deepEqual(mine.members.map((m) => [m.company.name, m.cal]), [["가나상사", "app"], ["다라상사", "2"]]);
  // Leaving keeps the 30 days; once they are over, the calendar takes another company.
  await call("DELETE", "/api/members", { device: device(9), company: ids[0].company.id }, from(9));
  assert.equal((await call("GET", `/api/companies/${ids[0].company.id}`, undefined, as(9))).data.member, false);
  assert.equal((await join(ids[2].company.id, "app")).data.error, "member_wait");
  DB.db.prepare("UPDATE members SET since = since - ? WHERE cal = 'app'").run(31 * DAY);
  assert.equal((await join(ids[2].company.id, "app")).data.joined, true);
  // 모두 지우기 takes the companies too.
  await call("DELETE", "/api/mine", undefined, { "x-device": device(9) });
  mine = (await call("GET", "/api/members", undefined, { "x-device": device(9) })).data;
  assert.deepEqual(mine.members, []);
});

test("[PLAZA-07] a member reading pay too often reads as anyone else for the day", async () => {
  const { call } = server();
  // A phone of its own: the count is kept in the Worker's memory for the day.
  const a = await call("POST", "/api/reports", report(77, { company: "많이읽는상사" }), from(77));
  let last;
  for (let i = 0; i < 121; i++) last = (await call("GET", `/api/companies/${a.data.company.id}`, undefined, as(77))).data;
  assert.equal(last.member, false);
  assert.equal(last.limited, true);
  assert.equal(last.setups[0].setup.pay, undefined);
});

test("[PLAZA-08] the history and the talk keep their pay lines for members", async () => {
  const { call } = server();
  const first = [];
  for (const n of [1, 2]) first.push(await call("POST", "/api/reports", report(n, { company: "이력상사", verified: n === 1 }), from(n)));
  const c = first[0].data.company.id;
  const other = setup();
  other.pay.rules.night = 0.6;
  other.shiftTypes[0].end = "18:00";
  for (const n of [3, 4]) await call("POST", "/api/reports", report(n, { company: "이력상사", setup: other, amends: undefined }), from(n));
  const h = await signIn(call);
  const page = (await call("GET", `/api/companies/${c}`, undefined, as(3))).data;
  const newer = page.setups.find((s) => s.status === "pending");
  await call("POST", `/api/admin/groups/${c}/${newer.id}/adopt`, {}, h);
  const outside = (await call("GET", `/api/companies/${c}/history?setup=${newer.id}`)).data;
  assert.equal(outside.member, false);
  assert.equal(outside.versions[0].body.pay, undefined);
  assert.deepEqual(outside.versions[0].changes.added.map(([l]) => l), ["근무"]);
  const inside = (await call("GET", `/api/companies/${c}/history?setup=${newer.id}`, undefined, as(1))).data;
  assert.equal(inside.versions[0].body.pay.rules.night, 0.6);
  assert.ok(inside.versions[0].changes.added.some(([l]) => l === "가산율"));
  // A proposal: its shift line is for everyone, its pay line for members.
  const change = [{ field: "shift", now: "주간 07:00~19:00", want: "주간 07:00~18:00" }, { field: "night", now: "야간 50%", want: "야간 60%" }];
  await call("POST", "/api/posts", { device: device(2), company: c, setup: newer.id, body: "야간이 올랐어요", kind: "proposal", change }, from(2));
  const read = (headers) => call("GET", `/api/companies/${c}/setups/${newer.id}/posts`, undefined, headers);
  assert.deepEqual((await read(as(8))).data.posts[0].change.map((l) => l.field), ["shift"]);
  assert.deepEqual((await read(as(1))).data.posts[0].change.map((l) => l.field), ["shift", "night"]);
});

test("[PLAZA-09] a company's room is for members: my version, the others with what differs, and 안정", async () => {
  const { call, DB } = server();
  const a = await call("POST", "/api/reports", report(1, { company: "방상사" }), from(1));
  const c = a.data.company.id;
  assert.deepEqual([a.data.first, a.data.member.joined], [true, true]);
  assert.equal((await call("GET", `/api/companies/${c}/room`, undefined, as(7))).status, 403);
  let room = (await call("GET", `/api/companies/${c}/room`, undefined, as(1))).data;
  assert.deepEqual([room.base.setup.pay !== undefined, room.base.first, room.base.mine, room.others.length, room.stable], [true, true, true, 0, false]);
  // Two others say 야간 is 60%: one card's worth, with who stands behind it.
  const other = setup();
  other.pay.rules.night = 0.6;
  for (const n of [2, 3]) {
    const r = await call("POST", "/api/reports", report(n, { company: "방상사", setup: other }), from(n));
    assert.equal(r.data.first, false);
  }
  room = (await call("GET", `/api/companies/${c}/room`, undefined, as(1))).data;
  assert.equal(room.others.length, 1);
  assert.equal(room.others[0].persons, 2);
  assert.equal(room.others[0].setup.pay.rules.night, 0.6);
  assert.deepEqual(room.others[0].changes.added, [["가산율", "야간 60%"]]);
  assert.deepEqual(room.others[0].changes.removed, [["가산율", "법대로"]]);
  // 광장 소식 to the member: something new in the company.
  const news = (await call("GET", `/api/plaza/news?since=${new Date(Date.now() - 60000).toISOString()}`, undefined, { "x-device": device(1) })).data;
  assert.deepEqual(news.items.filter((i) => i.kind === "room").map((i) => [i.company.name, i.count]), [["방상사", 1]]);
  // 안정: three phones on one version and nothing new for 30 days.
  const grp = room.others[0].id;
  for (const n of [5, 6, 7]) await call("POST", "/api/uses", { device: device(n), company: c, setup: grp }, from(n));
  room = (await call("GET", `/api/companies/${c}/room`, undefined, as(5))).data;
  assert.equal(room.base.id, grp);
  assert.equal(room.stable, false);
  DB.db.prepare("UPDATE reports SET created_at = created_at - ?").run(31 * DAY);
  DB.db.prepare("UPDATE rules SET adopted_at = adopted_at - ?").run(31 * DAY);
  room = (await call("GET", `/api/companies/${c}/room`, undefined, as(5))).data;
  assert.equal(room.stable, true);
  const page = (await call("GET", `/api/companies/${c}`, undefined, as(5))).data;
  assert.equal(page.setups.find((s) => s.id === grp).stable, true);
});

test("[PLAZA-10] a company not found starts from its first sender: shifts only are found and used, the pass only with pay", async () => {
  const { call } = server({ plaza: true });
  // Shifts only: findable at once by part of the name, and another phone can take them before joining.
  const shifts = await call("POST", "/api/reports", report(1, { company: "새로운물류", setup: cycleOnly() }), from(1));
  assert.deepEqual([shifts.status, shifts.data.first, shifts.data.newInfo, shifts.data.reward], [201, true, false, null]);
  const hit = (await call("GET", "/api/companies?q=" + encodeURIComponent("새로운"))).data.companies;
  assert.deepEqual(hit.map((h) => [h.name, h.first, h.pending]), [["새로운물류", true, 1]]);
  const page = (await call("GET", `/api/companies/${hit[0].id}`, undefined, as(5))).data;
  assert.deepEqual([page.setups[0].status, page.setups[0].persons, page.setups[0].pay], ["pending", 1, false]);
  assert.deepEqual(page.setups[0].setup.rotation.steps, setup().rotation.steps);
  // The first with pay for the company's department: new information, a pass of one month.
  const pay = await call("POST", "/api/reports", report(2, { company: "새로운물류" }), from(2));
  assert.deepEqual([pay.data.first, pay.data.newInfo, pay.data.reward], [false, true, { months: 1, status: "paid" }]);
  const passes = (await call("GET", "/api/passes", undefined, { "x-device": device(2) })).data;
  assert.deepEqual(passes.items.map((p) => [p.months, p.why]), [[1, "rule"]]);
  // Pay again for the same department, from someone else: nothing new.
  const again = await call("POST", "/api/reports", report(3, { company: "새로운물류" }), from(3));
  assert.deepEqual([again.data.newInfo, again.data.reward], [false, null]);
  // Another department is new; a second new thing the same day waits for the developer.
  const dept = await call("POST", "/api/reports", report(2, { company: "새로운물류", dept: "포장팀" }), from(2));
  assert.deepEqual([dept.data.first, dept.data.newInfo, dept.data.reward], [true, true, { months: 1, status: "owed" }]);
  // A rule for a whole job earns nothing as new information.
  const topic = await call("POST", "/api/reports", report(4, { topic: true, job: "construction" }), from(4));
  assert.equal(topic.data.newInfo, false);
  // Terms say what it earns.
  assert.equal((await call("GET", "/api/terms")).data.firstMonths, 1);
});

test("[PLAZA-10] new information is paid at most three times a year", async () => {
  const { call, DB } = server({ plaza: true });
  const statuses = [];
  for (let i = 0; i < 4; i++) {
    const r = await call("POST", "/api/reports", report(1, { company: `한해상사${"가나다라"[i]}` }), from(1));
    statuses.push(r.data.reward.status);
    // 100 days apart, so neither the day's nor the 90-day cap counts: only the year's.
    DB.db.prepare("UPDATE rewards SET paid_at = paid_at - ? WHERE status = 'paid'").run(100 * DAY);
  }
  assert.deepEqual(statuses, ["paid", "paid", "paid", "owed"]);
});

test("[PLAZA-08] the board lists rule sets without any pay value", async () => {
  const { call } = server();
  for (const n of [1, 2]) await call("POST", "/api/reports", report(n, { company: "판상사", verified: n === 1 }), from(n));
  const board = (await call("GET", "/api/board?tab=adopted")).data;
  assert.equal(board.items.length, 1);
  const text = JSON.stringify(board);
  for (const word of ["가산", "rules", "BASE_SHARE", "\"setup\":{"]) assert.ok(!text.includes(word), word);
  assert.equal(board.items[0].pay, true);
});

test("[PLAZA-13] the developer app counts members per company", async () => {
  const { call } = server();
  const a = await call("POST", "/api/reports", report(1, { company: "관리상사" }), from(1));
  await call("POST", "/api/uses", { device: device(2), company: a.data.company.id, setup: a.data.setup }, from(2));
  const h = await signIn(call);
  const list = (await call("GET", "/api/admin/companies?q=관리상사", undefined, h)).data;
  assert.equal(list.companies[0].members, 2);
  const info = (await call("GET", `/api/admin/companies/${a.data.company.id}`, undefined, h)).data;
  assert.equal(info.members, 2);
});
