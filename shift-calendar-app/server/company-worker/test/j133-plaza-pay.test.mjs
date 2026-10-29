// 1.33 [J133-82] 광장 급여 써 보기: someone who does not use a company sees its shifts only (결정 D79), so a proposal from them
// has no pay part. The company's pay way must stay with the next version instead of being dropped. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, setup, device, from } from "./helpers.mjs";

test("[J133-82] a shifts-only proposal keeps the pay way it never saw", async () => {
  const { call } = server({ plaza: true });
  const a = await call("POST", "/api/reports", report(1), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  // Phone 9 never joined: it is given the shifts and the order, no pay.
  const page = await call("GET", `/api/companies/${c}`, undefined, { "x-device": device(9) });
  const seen = page.data.setups.find((s) => s.id === g).setup;
  assert.equal(page.data.setups.find((s) => s.id === g).payHidden, true);
  assert.equal(seen.pay, undefined);
  const changed = { ...seen, shiftTypes: seen.shiftTypes.map((t) => (t.name === "주간" ? { ...t, start: "08:00", end: "20:00" } : t)) };
  const sent = await call("POST", "/api/posts", { device: device(9), company: c, setup: g, body: "주간이 8시부터예요", kind: "proposal",
    change: [{ field: "shift", now: "주간 07:00~19:00", want: "주간 08:00~20:00", why: "" }], proposal: changed }, from(9));
  assert.equal(sent.status, 201);
  // Phone 1 uses the company: the proposal it reads has the new shift time and the company's pay as it was.
  const posts = (await call("GET", `/api/companies/${c}/setups/${g}/posts`, undefined, { "x-device": device(1) })).data.posts;
  const text = JSON.parse(posts.find((p) => p.kind === "proposal").proposal.text);
  assert.equal(text.shiftTypes.find((t) => t.name === "주간").start, "08:00");
  assert.equal(text.pay.monthlyHours, setup().pay.monthlyHours);
  assert.equal(text.pay.fiveOrMore, true);
});

test("[J133-82] a proposal that brings its own pay part keeps it", async () => {
  const { call } = server({ plaza: true });
  const a = await call("POST", "/api/reports", report(1), from(1));
  const better = setup();
  better.pay.monthlyHours = 209;
  const sent = await call("POST", "/api/posts", { device: device(1), company: a.data.company.id, setup: a.data.setup, body: "209시간이에요",
    kind: "proposal", change: [{ field: "hours", want: "209시간" }], proposal: better }, from(1));
  assert.equal(sent.status, 201);
  const posts = (await call("GET", `/api/companies/${a.data.company.id}/setups/${a.data.setup}/posts`, undefined, { "x-device": device(1) })).data.posts;
  assert.equal(JSON.parse(posts.find((p) => p.kind === "proposal").proposal.text).pay.monthlyHours, 209);
});
