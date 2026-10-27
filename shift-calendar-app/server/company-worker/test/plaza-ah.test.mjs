// [PLAZA-14][PLAZA-15] 1.27 묶음 AH: each department's 대표 주기 (the first setup leads at once, another order under its
// own 사업장 name leads there), 맞아요·달라요 with where it is wrong, one vote per phone, and too many 달라요 taking a
// version out of the lead for the next trusted one. Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { leadOf, doubted, DOUBT } from "../src/index.js";
import { server, report, setup, device, from, signIn, adminHome } from "./helpers.mjs";

const as = (n) => ({ ...from(n), "x-device": device(n) });

/** The shifts and an order, no pay: what the app sends when a cycle is shared (계획 AH-D6). */
function cycle(steps = ["주간", "주간", "휴무", "휴무", "야간", "야간", "휴무", "휴무"]) {
  const s = setup();
  delete s.pay;
  s.rotation = { anchor: "2026-10-01", steps };
  return s;
}
const LONG = ["주간", "주간", "주간", "휴무", "휴무", "휴무", "야간", "야간", "야간", "휴무", "휴무", "휴무"];
// BD 1.30 [PLZD-04] Another order of the same shape (8 days, 주간·야간): a version one department may hold beside the first.
const ALT = ["주간", "주간", "야간", "야간", "휴무", "휴무", "휴무", "휴무"];

const vote = (call, n, company, grp, ok, why) =>
  call("POST", "/api/votes", { device: device(n), company, setup: grp, ok, ...(why ? { why } : {}) }, from(n));

test("[PLAZA-14] pure: the lead is the adopted one, else the most 지지, never a doubted one", () => {
  assert.equal(leadOf([{ id: "a", adopted: false, support: 2 }, { id: "b", adopted: false, support: 3 }]), "b");
  assert.equal(leadOf([{ id: "a", adopted: true, support: 1 }, { id: "b", adopted: false, support: 9 }]), "a");
  assert.equal(leadOf([{ id: "a", adopted: true, support: 9, doubted: true }, { id: "b", adopted: false, support: 1 }]), "b");
  assert.equal(leadOf([{ id: "a", adopted: false, support: 2 }, { id: "b", adopted: false, support: 2 }]), "a", "a tie keeps the earlier");
  assert.equal(leadOf([{ id: "a", adopted: true, support: 2, doubted: true }]), null);
  // 5 votes at least and more than 40% 달라요 (AH-D7).
  assert.deepEqual(DOUBT, { votes: 5, share: 40 });
  assert.equal(doubted({ persons: 2, bad: 2 }), false, "four votes are too few");
  assert.equal(doubted({ persons: 3, bad: 2 }), false, "40% is not more than 40%");
  assert.equal(doubted({ persons: 2, bad: 3 }), true);
  assert.equal(doubted({ persons: 5, bad: 4 }), true);
  assert.equal(doubted({ persons: 5, bad: 3 }), false);
});

test("[PLAZA-14] the first cycle leads at once; another order under a 사업장 name leads there; the search says so", async () => {
  const { call } = server();
  const first = (await call("POST", "/api/reports", report(1, { company: "가상칼텍스", setup: cycle() }), from(1))).data;
  assert.equal(first.first, true);
  const c = first.company.id;
  let page = (await call("GET", `/api/companies/${c}`, undefined, as(1))).data;
  assert.equal(page.setups.length, 1);
  assert.equal(page.setups[0].lead, true, "the first one is the 대표 주기 at once");
  assert.deepEqual(page.setups[0].opinion, { ok: 0, bad: 0, why: { order: 0, time: 0, pay: 0, other: 0 } });

  // Another phone, another order, its own 사업장: a 대표 주기 of its own; the company's own stays.
  const site = (await call("POST", "/api/reports", report(2, { company: "가상칼텍스", dept: "온산공장", setup: cycle(LONG) }), from(2))).data;
  assert.equal(site.first, true, "the first of its 사업장");
  page = (await call("GET", `/api/companies/${c}`, undefined, as(2))).data;
  const byDept = Object.fromEntries(page.setups.map((s) => [s.dept, s]));
  assert.equal(byDept[""].lead, true);
  assert.equal(byDept["온산공장"].lead, true);
  assert.equal(byDept["온산공장"].setup.rotation.steps.length, 12);

  // The search's line: the company-wide 대표 주기 with its order, no pay, and how many phones are on the company.
  const hit = (await call("GET", "/api/companies?q=" + encodeURIComponent("가상칼"))).data.companies[0];
  assert.equal(hit.users, 2);
  assert.equal(hit.sites, 2);
  assert.deepEqual(hit.lead.steps, cycle().rotation.steps);
  assert.deepEqual(hit.lead.offs, ["휴무"]);
  assert.equal(hit.lead.pay, false);
  assert.equal(hit.lead.dept, "");
  assert.equal(hit.lead.ok, 1);
  // 초성 finds it with the same line.
  const cho = (await call("GET", "/api/companies?q=" + encodeURIComponent("ㄱㅅㅋ"))).data.companies[0];
  assert.deepEqual(cho.lead.steps, cycle().rotation.steps);

  // Two orders in one department: the one more people stand behind leads. (BD 1.30: of one shape; a 12-day order here is
  // another 사업장's and waits for the developer, plaza-guard.test.mjs.)
  await call("POST", "/api/reports", report(3, { company: "가상칼텍스", setup: cycle(ALT) }), from(3));
  await call("POST", "/api/reports", report(4, { company: "가상칼텍스", setup: cycle(ALT) }), from(4));
  page = (await call("GET", `/api/companies/${c}`)).data;
  const whole = page.setups.filter((s) => s.dept === "");
  assert.equal(whole.length, 2);
  assert.deepEqual(whole.find((s) => s.lead).setup.rotation.steps, ALT, "two phones behind the other order");
});

test("[PLAZA-15] one vote per phone, changed by voting again, with where a 달라요 says it is wrong", async () => {
  const { call } = server();
  const a = (await call("POST", "/api/reports", report(1, { company: "의견상사", setup: cycle() }), from(1))).data;
  const c = a.company.id;
  assert.equal((await vote(call, 5, c, a.setup, false, "order")).status, 200);
  assert.equal((await vote(call, 6, c, a.setup, false, "pay")).status, 200);
  // An app before 1.27 says only "wrong": it counts as 그 밖.
  await vote(call, 7, c, a.setup, false, "wrong");
  let s = (await call("GET", `/api/companies/${c}`, undefined, as(5))).data.setups[0];
  assert.deepEqual(s.opinion, { ok: 0, bad: 3, why: { order: 1, time: 0, pay: 1, other: 1 }, mine: "order" });
  // The same phone again: its vote changes, never a second one.
  await vote(call, 5, c, a.setup, true);
  await vote(call, 5, c, a.setup, true);
  s = (await call("GET", `/api/companies/${c}`, undefined, as(5))).data.setups[0];
  assert.deepEqual(s.opinion, { ok: 1, bad: 2, why: { order: 0, time: 0, pay: 1, other: 1 }, mine: "ok" });
  assert.equal(s.persons, 2, "the sender and one 맞아요");
  assert.equal(s.bad, 2);
  // Another phone reads no "mine".
  assert.equal((await call("GET", `/api/companies/${c}`, undefined, as(9))).data.setups[0].opinion.mine, undefined);
});

test("[PLAZA-15] too many 달라요 take the 대표 out; more 맞아요 bring it back; a waiting version takes over by its own right", async () => {
  const { call, DB } = server();
  // An adopted rule: two phones on two networks (the tests adopt with two people).
  const a = (await call("POST", "/api/reports", report(1, { company: "대표상사", setup: cycle() }), from(1))).data;
  const c = a.company.id;
  await call("POST", "/api/reports", report(2, { company: "대표상사", setup: cycle() }), from(2));
  await vote(call, 3, c, a.setup, true);
  let page = (await call("GET", `/api/companies/${c}`)).data;
  assert.equal(page.setups[0].status, "adopted");
  assert.equal(page.setups[0].lead, true);
  // Another order (of the same shape, BD 1.30) waits in the same department.
  const b = (await call("POST", "/api/reports", report(4, { company: "대표상사", setup: cycle(ALT) }), from(4))).data;
  assert.equal(b.status, "pending");

  // 3 맞아요 (two senders, one vote) and 2 달라요: 40% is not more than 40%, it still leads.
  await vote(call, 5, c, a.setup, false, "order");
  await vote(call, 6, c, a.setup, false, "order");
  page = (await call("GET", `/api/companies/${c}`)).data;
  assert.equal(page.setups.find((s) => s.id === a.setup).lead, true);
  // A third 달라요: 3 of 6 is 50%: out of the lead; the waiting one leads instead.
  await vote(call, 7, c, a.setup, false, "time");
  page = (await call("GET", `/api/companies/${c}`)).data;
  const rule = page.setups.find((s) => s.id === a.setup);
  assert.equal(rule.doubted, true);
  assert.equal(rule.lead, undefined);
  assert.equal(rule.status, "adopted", "still the adopted rule until another is adopted");
  assert.equal(page.setups.find((s) => s.id === b.setup).lead, true);
  // The search line says so too.
  const hit = (await call("GET", "/api/companies?q=" + encodeURIComponent("대표상사"))).data.companies[0];
  assert.deepEqual(hit.lead.steps, ALT);

  // More 맞아요 bring it back: 5 of 8 (37.5% 달라요).
  await vote(call, 8, c, a.setup, true);
  await vote(call, 9, c, a.setup, true);
  page = (await call("GET", `/api/companies/${c}`)).data;
  assert.equal(page.setups.find((s) => s.id === a.setup).lead, true);
  assert.equal(page.setups.find((s) => s.id === a.setup).doubted, undefined);

  // Doubted again, and the waiting order gathers enough on its own: adopted without outweighing the rule in use.
  for (const n of [10, 11, 12]) await vote(call, n, c, a.setup, false, "order");
  await vote(call, 13, c, b.setup, true);
  await vote(call, 14, c, b.setup, true);
  page = (await call("GET", `/api/companies/${c}`)).data;
  const now = page.setups.find((s) => s.status === "adopted");
  assert.equal(now.id, b.setup, "the next trusted version is the department's rule now");
  assert.equal(now.lead, true);
  const sources = DB.db.prepare("SELECT grp, source, superseded FROM rules WHERE company_id = ? ORDER BY version").all(c);
  assert.deepEqual(sources.map((r) => [r.grp === a.setup ? "a" : "b", r.source, r.superseded]), [["a", "auto", 1], ["b", "rival", 0]]);

  // The developer app: 달라요 by where, the rule out of the lead marked, and the rule itself on the settings page.
  const h = await signIn(call);
  const info = (await call("GET", `/api/admin/companies/${c}`, undefined, h)).data;
  const old = info.rules.find((r) => r.version === 1);
  assert.deepEqual(old.why, { order: 5, time: 1, pay: 0, other: 0 });
  assert.equal(old.doubted, undefined, "an earlier version is not marked");
  assert.equal((await adminHome(call, h)).data.doubt.share, 40);
});
