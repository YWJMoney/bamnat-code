// 1.30 마무리 [FIN130-11]~[FIN130-17] Where BD's 'held for its shape' meets the rest of the server, a company's one amount
// before 처리방침 3.4, and 달라진 점 of an allowance whose amount changed hands. Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { stableOne } from "../src/index.js";
import { diffFacts, factsOf, lineChanges } from "../src/plaza-diff.js";
import { withoutFixedAmounts } from "../src/plaza-items.js";
import { server, report, setup, device, from, signIn } from "./helpers.mjs";

/** The shifts and an order, no pay: what the app sends when a cycle is shared. */
function cycle(steps = ["주간", "주간", "휴무", "휴무", "야간", "야간", "휴무", "휴무"]) {
  const s = setup();
  delete s.pay;
  s.rotation = { anchor: "2026-10-01", steps };
  return s;
}
/** Three kinds of work over twelve days: held when the department's 대표 is a 2교대. */
function three() {
  const s = cycle(["주간", "주간", "오후", "오후", "야간", "야간", "휴무", "휴무", "휴무", "휴무", "휴무", "휴무"]);
  s.shiftTypes.splice(1, 0, { name: "오후", label: "오", color: 4280391411, start: "14:00", end: "22:00", break: 30, off: false });
  return s;
}
const OTHER = ["주간", "주간", "야간", "야간", "휴무", "휴무", "휴무", "휴무"];

/** A version 4 setup with 식대 one amount for everyone (회사 공통 100,000원). */
function withMeal() {
  const s = setup();
  s.v = 4;
  s.pay.items.push({ id: "meal", name: "식대", amount: { entries: [], raise: null }, cadence: "MONTHLY", types: [], ordinary: true,
    taxable: true, months: [], fixedOvertime: 0, festivals: [], festivalBefore: false, share: "FIXED", fixedAmount: 100000 });
  return s;
}

const send = (call, n, extra) => call("POST", "/api/reports", report(n, { company: "모양상사", ...extra }), from(n));
const as = (n) => ({ ...from(n), "x-device": device(n) });

test("[FIN130-11] a held version is counted once (모양이 다른 판), never as 여러 판, and is never live nor recommended", async () => {
  const { call } = server();
  const owner = await signIn(call);
  const a = (await send(call, 1, { setup: cycle() })).data;
  const held = (await send(call, 2, { setup: three() })).data;
  assert.equal(held.held, "shape");
  const badges = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  assert.equal(badges.versionConflicts, 0, "one version people see is no conflict");
  const today = (await call("GET", "/api/admin/today", undefined, owner)).data;
  assert.equal(today.versionConflicts, 0);
  assert.equal(today.alerts.some((x) => x.key === "versions"), false);
  // 여러 판 (history) lists it, marked, and never as the one to keep.
  const v = (await call("GET", "/api/admin/versions?show=history", undefined, owner)).data;
  assert.equal(v.conflicts, 0);
  const dept = v.items.find((i) => i.company.id === a.company.id);
  assert.equal(dept.conflict, false);
  const card = dept.candidates.find((c) => c.setup === held.setup);
  assert.equal(card.shape, true);
  assert.equal(card.recommended, false);
  assert.equal(dept.candidates.find((c) => c.setup === a.setup).shape, undefined);
  // A third, visible version of the same shape makes the two visible ones a conflict; the held one still is not chosen.
  await send(call, 3, { setup: cycle(OTHER) });
  const after = (await call("GET", "/api/admin/versions", undefined, owner)).data;
  const d2 = after.items.find((i) => i.company.id === a.company.id);
  assert.equal(d2.conflict, true);
  assert.equal(d2.candidates.find((c) => c.setup === held.setup).recommended, false);
  assert.equal((await call("GET", "/api/admin/badges", undefined, owner)).data.versionConflicts, 1);
});

test("[FIN130-12] a held version takes no votes or talk, and neither keeps the next one from being first nor resets 안정", async () => {
  const { call, DB, env } = server();
  const a = (await send(call, 1, { setup: cycle() })).data;
  const held = (await send(call, 2, { setup: three() })).data;
  const c = a.company.id;
  const vote = await call("POST", "/api/votes", { device: device(5), company: c, setup: held.setup, ok: true }, from(5));
  // A 1.32 [FIX132-02] The vote says why: held for review (409 held), not "not found".
  assert.equal(vote.status, 409);
  assert.equal(vote.data.error, "held");
  assert.equal((await call("GET", `/api/companies/${c}/setups/${held.setup}/posts`, undefined, as(2))).status, 404);
  const post = await call("POST", "/api/posts", { device: device(2), company: c, setup: held.setup, body: "이 판이 맞아요" }, from(2));
  assert.equal(post.status, 404);
  // The visible one is open as before.
  assert.equal((await call("POST", "/api/votes", { device: device(5), company: c, setup: a.setup, ok: true }, from(5))).status, 200);
  // 안정: the held version sent later is no change of the department.
  const born = DB.db.prepare("SELECT MIN(created_at) AS t FROM reports WHERE grp = ?").get(a.setup).t;
  const s = await stableOne(env, c, "", a.setup, Date.now());
  assert.equal(s.since, born);

  // A company whose only version so far is held: the next visible one is still that department's first.
  const b = (await send(call, 3, { company: "모양물산", setup: cycle() })).data;
  DB.db.prepare("UPDATE reports SET reason = 'shape' WHERE id = ?").run(b.id);
  const next = (await send(call, 4, { company: "모양물산", setup: cycle(OTHER) })).data;
  assert.equal(next.held, undefined);
  assert.equal(next.first, true);
});

test("[FIN130-13] 투표 중 on the job plazas counts what the board shows, not a held version", async () => {
  const { call } = server();
  const voting = async () => (await call("GET", "/api/plazas", undefined, { "cache-control": "no-cache" })).data.jobs.find((j) => j.key === "factory").voting;
  await send(call, 1, { job: "factory", setup: cycle() });
  assert.equal(await voting(), 1);
  const held = (await send(call, 2, { job: "factory", setup: three() })).data;
  assert.equal(held.held, "shape");
  assert.equal(await voting(), 1);
});

test("[FIN130-14] a held version the developer adopts no longer tells its sender it waits for its shape", async () => {
  const { call } = server();
  const owner = await signIn(call);
  await send(call, 1, { setup: cycle() });
  const held = (await send(call, 2, { setup: three() })).data;
  assert.equal((await call("GET", `/api/reports/${held.id}`)).data.reason.code, "shape");
  const done = await call("POST", `/api/admin/groups/${held.company.id}/${held.setup}/adopt`, {}, owner);
  assert.equal(done.status, 200, done.text);
  const now = (await call("GET", `/api/reports/${held.id}`)).data;
  assert.equal(now.status, "adopted");
  assert.equal(now.reason, null);
});

test("[FIN130-15] a company's one amount is taken only once 처리방침 3.4 is in force, quietly dropped before", async (t) => {
  const { call, DB } = server({ plaza: true });
  const meal = async (company, n) => {
    const r = await call("POST", "/api/reports", report(n, { company, setup: withMeal() }), from(n));
    assert.equal(r.status, 201, r.text);
    const page = (await call("GET", `/api/companies/${r.data.company.id}`, undefined, as(n))).data;
    return page.setups[0].setup.pay.items.find((i) => i.name === "식대");
  };
  // Before 2026-11-27 (the calendar's 3.4): no refusal, the allowance stays 회사 공통, its amount does not come.
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-26T14:00:00Z") });
  const before = await meal("금액상사", 1);
  assert.equal(before.share, "FIXED");
  assert.equal("fixedAmount" in before, false);
  // From the day it is in force (KST), it comes.
  t.mock.timers.setTime(Date.parse("2026-11-26T15:00:00Z"));
  assert.equal((await meal("금액물산", 2)).fixedAmount, 100000);
  // The owner moved 3.4 later in the developer app: the server follows its own calendar.
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.4": { posted: "2026-10-28", effective: "2026-12-20", heldUntil: null, unfavourable: false } })).run();
  assert.equal("fixedAmount" in (await meal("금액전자", 3)), false);
  t.mock.timers.reset();
  // pure
  const s = withMeal();
  assert.equal(withoutFixedAmounts(s), s);
  assert.ok(s.pay.items.every((i) => !("fixedAmount" in i)));
  assert.equal(withoutFixedAmounts(cycle()).pay, undefined);
});

test("[FIN130-15] a proposal's setup also comes without the amount before 3.4", async (t) => {
  const { call } = server({ plaza: true });
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-01T03:00:00Z") });
  const a = await call("POST", "/api/reports", report(1), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  const better = withMeal();
  better.pay.monthlyHours = 209;
  const sent = await call("POST", "/api/posts", { device: device(1), company: c, setup: g, body: "식대가 있어요", kind: "proposal",
    change: [{ field: "hours", now: "180시간", want: "209시간", why: "" }], proposal: better }, from(1));
  assert.equal(sent.status, 201, sent.text);
  const posts = (await call("GET", `/api/companies/${c}/setups/${g}/posts`, undefined, as(1))).data.posts;
  const item = JSON.parse(posts.find((p) => p.kind === "proposal").proposal.text).pay.items.find((i) => i.name === "식대");
  assert.equal(item.share, "FIXED");
  assert.equal("fixedAmount" in item, false);
  t.mock.timers.reset();
});

test("[FIN130-16] 달라진 점: 회사 공통 → 사람마다 says so, and a name with brackets stays whole", () => {
  const fixed = [["수당", "식대(매달 회사 공통 100,000원 · 통상임금) · 자격수당(매달)"]];
  const personal = [["수당", "식대(매달 통상임금) · 자격수당(매달)"]];
  const d = lineChanges(fixed, personal);
  assert.equal(d.facts.changed.length, 1);
  const c = d.facts.changed[0];
  assert.equal(c.label, "수당·식대");
  assert.equal(c.after, "매달 사람마다 · 통상임금");
  assert.ok(c.tokens.some((tk) => tk.kind === "del" && tk.t.includes("회사 공통")));
  assert.ok(c.tokens.some((tk) => tk.kind === "add" && tk.t.includes("사람마다")));
  assert.equal(d.summary, "수당·식대 회사 공통 100,000원→사람마다");
  // And back: 사람마다 → 회사 공통, an allowance with no 통상임금.
  const back = diffFacts([["수당·교통비", "매달"]], [["수당·교통비", "매달 회사 공통 50,000원"]]);
  assert.equal(back.changed[0].before, "매달 사람마다");
  assert.equal(back.summary, "수당·교통비 사람마다→회사 공통 50,000원");
  // A change that keeps who sets it is as before.
  assert.equal(diffFacts([["수당·식대", "매달"]], [["수당·식대", "근무일마다"]]).changed[0].before, "매달");
  // "식대(중식)" is one allowance's name.
  assert.deepEqual(factsOf([["수당", "식대(중식)(매달 회사 공통 100,000원) · 상여(2·4월)"]]),
    [["수당·식대(중식)", "매달 회사 공통 100,000원"], ["수당·상여", "2·4월"]]);
});

test("[FIN130-17] a setup whose representative report is held but another of it is shown is adopted like any other", async () => {
  const { call, DB } = server();
  const first = (await send(call, 1, { setup: cycle() })).data;
  // The first report of it is held (and, checked with a payslip, the one the setup is shown from).
  DB.db.prepare("UPDATE reports SET reason = 'shape', verified = 1 WHERE id = ?").run(first.id);
  const second = (await send(call, 2, { setup: cycle() })).data;
  assert.equal(second.setup, first.setup);
  assert.equal(second.held, undefined);
  await call("POST", "/api/votes", { device: device(4), company: first.company.id, setup: first.setup, ok: true }, from(4));
  assert.equal((await call("GET", `/api/reports/${second.id}`)).data.status, "adopted");
  // Adopted, the held one's note went with it ([FIN130-14]).
  assert.equal((await call("GET", `/api/reports/${first.id}`)).data.reason, null);

  // Held all through: still the developer's to place, however many agree.
  const lone = (await send(call, 5, { company: "모양물산", setup: cycle() })).data;
  DB.db.prepare("UPDATE reports SET reason = 'shape' WHERE company_id = ?").run(lone.company.id);
  for (const n of [6, 7, 8]) await call("POST", "/api/votes", { device: device(n), company: lone.company.id, setup: lone.setup, ok: true }, from(n));
  assert.equal((await call("GET", `/api/reports/${lone.id}`)).data.status, "pending");
});
