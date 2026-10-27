// BD 1.30 [PLZD-04][PLZD-05][PLZD-07] 광장 중복·변별: a version of another shape waits for the developer and is never shown, a
// phone's second version of a company within a day replaces its first, the same rule again counts as one more 맞아요, and a
// company page says what each folded version changes. Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { pgShape, pgPeriod, pgSameShape, pgShapeWords, pgChanges, pgShown } from "../src/plaza-guard.js";
import { normName } from "../src/index.js";
import { server, report, setup, device, from, signIn } from "./helpers.mjs";

const as = (n) => ({ ...from(n), "x-device": device(n) });

/** The shifts and an order, no pay: what the app sends when a cycle is shared. */
function cycle(steps = ["주간", "주간", "휴무", "휴무", "야간", "야간", "휴무", "휴무"]) {
  const s = setup();
  delete s.pay;
  s.rotation = { anchor: "2026-10-01", steps };
  return s;
}
/** Three kinds of work over twelve days: another 사업장's order, never the next version of a 2교대 one. */
function three() {
  const s = cycle(["주간", "주간", "오후", "오후", "야간", "야간", "휴무", "휴무", "휴무", "휴무", "휴무", "휴무"]);
  s.shiftTypes.splice(1, 0, { name: "오후", label: "오", color: 4280391411, start: "14:00", end: "22:00", break: 30, off: false });
  return s;
}
const SAME_SHAPE = ["주간", "주간", "야간", "야간", "휴무", "휴무", "휴무", "휴무"];

const send = (call, n, extra) => call("POST", "/api/reports", report(n, { company: "모양상사", ...extra }), from(n));

test("[PLZD-04] pure: the shape of the work, as core SameSetup reads it", () => {
  assert.deepEqual(pgShape(cycle()), { works: 2, days: 8, weekly: false, roster: false });
  assert.deepEqual(pgShape(three()), { works: 3, days: 12, weekly: false, roster: false });
  // Written twice over it is one turn; two names with the same times are one kind of work.
  assert.deepEqual(pgShape(cycle([...SAME_SHAPE, ...SAME_SHAPE])), pgShape(cycle()));
  const twin = cycle(["주간", "주간B", "야간", "야간"]);
  twin.shiftTypes.push({ name: "주간B", start: "07:00", end: "19:00", break: 60, off: false });
  assert.equal(pgShape(twin).works, 2);
  assert.deepEqual(pgPeriod([1, 2, 1, 2]), [1, 2]);
  const weekly = { ...cycle(), weekly: ["주간", "주간", "주간", "주간", "주간", null, null] };
  assert.deepEqual(pgShape(weekly), { works: 1, days: 7, weekly: true, roster: false });
  assert.deepEqual(pgShape({ ...cycle(), roster: true }).roster, true);
  assert.equal(pgShape({ shiftTypes: [] }), null, "pay alone says nothing of the work");
  assert.equal(pgSameShape(null, pgShape(cycle())), true);
  assert.equal(pgSameShape(pgShape(three()), pgShape(cycle())), false);
  assert.equal(pgShapeWords(pgShape(three())), "3교대 · 12일 순서");
  assert.equal(pgShapeWords(pgShape(weekly)), "요일별 · 한 가지 근무");
  assert.equal(pgShown("p"), "(p.reason IS NULL OR p.reason != 'shape')");
});

test("[PLZD-04] a 3교대 sent to a 2교대 department waits for the developer and is shown nowhere", async () => {
  const { call } = server();
  const a = (await send(call, 1, { setup: cycle() })).data;
  const c = a.company.id;
  assert.equal(a.held, undefined);
  const held = await send(call, 2, { setup: three() });
  assert.equal(held.status, 201);
  assert.equal(held.data.status, "pending");
  assert.equal(held.data.held, "shape");
  assert.deepEqual(held.data.shape, { lead: "2교대 · 8일 순서", mine: "3교대 · 12일 순서" });
  assert.equal(held.data.reason.code, "shape");
  assert.match(held.data.reason.text, /부서 이름/);
  assert.equal(held.data.first, false);
  // Its sender reads the same later (내가 보낸 정보).
  assert.equal((await call("GET", `/api/reports/${held.data.id}`)).data.reason.code, "shape");

  // Not on the company page, the room, the search's count and 대표, nor the board.
  const page = (await call("GET", `/api/companies/${c}`, undefined, as(3))).data;
  assert.deepEqual(page.setups.map((s) => s.id), [a.setup]);
  const hit = (await call("GET", "/api/companies?q=" + encodeURIComponent("모양상사"))).data.companies[0];
  assert.equal(hit.pending, 1);
  assert.equal(hit.sites, 1);
  assert.equal(hit.lead.setup, a.setup);
  const board = (await call("GET", "/api/board?tab=voting", undefined, { "cache-control": "no-cache" })).data;
  assert.ok(board.items.every((i) => i.setup !== held.data.setup));
  const room = (await call("GET", `/api/companies/${c}/room`, undefined, as(1))).data;
  assert.ok(room.others.every((o) => o.id !== held.data.setup));

  // More phones behind it still do not adopt it by themselves.
  await send(call, 3, { setup: three() });
  await send(call, 4, { setup: three() });
  assert.equal((await call("GET", `/api/reports/${held.data.id}`)).data.status, "pending");

  // The developer's 검토 shows it as 모양이 다른 판.
  const headers = await signIn(call);
  const queue = (await call("GET", "/api/admin/queue", undefined, headers)).data;
  assert.equal(queue.groups.find((g) => g.setup === held.data.setup).shape, true);
});

test("[PLZD-02] under its own department name it is that department's first version", async () => {
  const { call } = server();
  const a = (await send(call, 1, { setup: cycle() })).data;
  const site = (await send(call, 2, { dept: "온산공장", setup: three() })).data;
  assert.equal(site.held, undefined);
  assert.equal(site.first, true);
  const page = (await call("GET", `/api/companies/${a.company.id}`)).data;
  assert.deepEqual(page.setups.map((s) => s.dept).sort(), ["", "온산공장"]);
  // The same shape with other values is a version of the department, as before.
  const other = (await send(call, 3, { setup: cycle(SAME_SHAPE) })).data;
  assert.equal(other.held, undefined);
});

test("[PLZD-04] a department's version people say is wrong may be followed by one of another shape", async () => {
  const { call } = server();
  const a = (await send(call, 1, { setup: cycle() })).data;
  for (const n of [5, 6, 7, 8, 9]) {
    await call("POST", "/api/votes", { device: device(n), company: a.company.id, setup: a.setup, ok: false, why: "order" }, from(n));
  }
  const next = (await send(call, 2, { setup: three() })).data;
  assert.equal(next.held, undefined);
  assert.equal(next.reason, null);
});

test("[PLZD-05] a phone's second version of a company within a day takes the place of its first", async () => {
  const { call, DB } = server();
  const first = (await send(call, 1, { dept: "A팀", setup: cycle() })).data;
  const second = (await send(call, 1, { dept: "B팀", setup: cycle(SAME_SHAPE) })).data;
  assert.deepEqual(second.replaced, [{ id: first.id, dept: "A팀" }]);
  assert.equal((await call("GET", `/api/reports/${first.id}`)).data.status, "replaced");
  const page = (await call("GET", `/api/companies/${first.company.id}`)).data;
  assert.deepEqual(page.setups.map((s) => s.dept), ["B팀"]);
  // A day on, a version for another department stands beside it.
  await DB.prepare("UPDATE reports SET created_at = created_at - ? WHERE id = ?").bind(25 * 60 * 60 * 1000, second.id).run();
  const third = (await send(call, 1, { dept: "C팀", setup: cycle() })).data;
  assert.equal(third.replaced, undefined);
  assert.equal((await call("GET", `/api/reports/${second.id}`)).data.status, "pending");
  // The same again is still the earlier report, not a replacement.
  const again = (await send(call, 1, { dept: "C팀", setup: cycle() })).data;
  assert.equal(again.again, true);
  assert.equal(again.id, third.id);
  // Work of another shape under another name is another 사업장's (1.27 AH): it stands beside, nothing is replaced.
  const site = (await send(call, 1, { dept: "온산공장", setup: three() })).data;
  assert.equal(site.replaced, undefined);
  assert.equal((await call("GET", `/api/reports/${third.id}`)).data.status, "pending");
});

test("[PLZD-01] the rule in use sent again by another phone counts as one more 맞아요 and says so", async () => {
  const { call } = server();
  const a = (await send(call, 1, { setup: cycle() })).data;
  await send(call, 2, { setup: cycle() });
  await call("POST", "/api/votes", { device: device(4), company: a.company.id, setup: a.setup, ok: true }, from(4));
  const page = (await call("GET", `/api/companies/${a.company.id}`)).data;
  assert.equal(page.setups[0].status, "adopted");
  const same = (await send(call, 3, { setup: cycle() })).data;
  assert.equal(same.same, true);
  assert.equal(same.status, "adopted");
  assert.equal(same.persons, 4);
  // An app before 1.30 reads the same answer it always did: every key it knew is there.
  for (const k of ["id", "status", "company", "dept", "setup", "points", "persons", "threshold", "reason", "first", "newInfo"]) assert.ok(k in same, k);
});

test("[PLZD-07] a folded version says what it changes against its department's 대표, in AX's words", async () => {
  const { call } = server();
  const a = (await send(call, 1, { setup: cycle() })).data;
  await send(call, 2, { setup: cycle() });
  const moved = cycle();
  moved.shiftTypes[0] = { ...moved.shiftTypes[0], start: "08:00", end: "20:00" };
  const b = (await send(call, 3, { setup: moved })).data;
  const page = (await call("GET", `/api/companies/${a.company.id}`)).data;
  const lead = page.setups.find((s) => s.id === a.setup);
  const other = page.setups.find((s) => s.id === b.setup);
  assert.equal(lead.changes, undefined);
  assert.equal(other.changes.against, a.setup);
  assert.match(other.changes.summary, /주간/);
  const fact = other.changes.facts.changed.find((f) => f.label === "근무·주간");
  assert.ok(fact.tokens.some((t) => t.kind === "add" && t.t.includes("08:00")));
  // pure: one version alone has nothing to say.
  assert.deepEqual(pgChanges([{ id: "x", dept: "", lines: [] }], normName), [{ id: "x", dept: "", lines: [] }]);
});
