// A 1.32 (사용자 2026-10-28, 계획/1.32-요청-검토보고서.md A): the server's half of the bug fixes. Companies and phones are made up.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, device, from, setup } from "./helpers.mjs";

const vote = (call, n, company, grp, extra = {}) =>
  call("POST", "/api/votes", { device: device(n), company, setup: grp, ok: false, why: "pay", ...extra }, from(n));

test("[FIX132-02] a 달라요 on a version that was replaced says so and names the version in use", async () => {
  const { call } = server();
  const first = [];
  for (const n of [1, 2, 6]) first.push(await call("POST", "/api/reports", report(n), from(n)));
  const company = first[0].data.company.id;
  const oldGrp = first[0].data.setup;
  const other = setup();
  other.pay.rules.extra[3].percent = 0.12;
  let newer;
  for (const n of [3, 4, 5, 7]) newer = await call("POST", "/api/reports", report(n, { setup: other }), from(n));
  assert.equal(newer.data.status, "adopted");
  const res = await vote(call, 9, company, oldGrp);
  assert.equal(res.status, 409);
  assert.equal(res.data.error, "superseded");
  assert.deepEqual(res.data.current, { setup: newer.data.setup, version: 2 });
  // The version in use takes the vote.
  assert.equal((await vote(call, 9, company, newer.data.setup)).status, 200);
});

test("[FIX132-02] an unknown version is still not_found", async () => {
  const { call } = server();
  const r = await call("POST", "/api/reports", report(1), from(1));
  const res = await vote(call, 2, r.data.company.id, "0123456789abcdef");
  assert.equal(res.status, 404);
  assert.equal(res.data.error, "not_found");
});
