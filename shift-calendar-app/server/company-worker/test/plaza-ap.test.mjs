// [PAYF-08] 1.29 묶음 AP: 급여 방식 찾기 finds a company's pay way even when its main 대표 주기 came without one (the pay came with
// another 사업장's), saying only which setup, its department and how many said 맞아요 (결정 D79). Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { payWayOf } from "../src/index.js";
import { server, report, setup, from } from "./helpers.mjs";

/** The shifts and an order, no pay: a cycle shared on its own. */
function cycle() {
  const s = setup();
  delete s.pay;
  s.rotation = { anchor: "2026-10-01", steps: ["주간", "주간", "휴무", "휴무", "야간", "야간", "휴무", "휴무"] };
  return s;
}

test("[PAYF-08] pure: the main lead's own pay needs nothing more; else the most trusted other lead with a pay way", () => {
  const withPay = { setup: { pay: {} } };
  const without = { setup: {} };
  assert.deepEqual(payWayOf([withPay], withPay), {});
  assert.deepEqual(payWayOf([without], without), {}, "no pay anywhere");
  assert.deepEqual(payWayOf([], undefined), {});
  const a = { id: "a", dept: "1공장", persons: 1, setup: { pay: {} } };
  const b = { id: "b", dept: "2공장", persons: 3, setup: { pay: {} } };
  const doubted = { id: "c", dept: "3공장", persons: 9, setup: { pay: {} }, doubted: true };
  assert.deepEqual(payWayOf([without, a, b, doubted], without), { payWay: { setup: "b", dept: "2공장", ok: 3 } });
});

test("[PAYF-08] the search names another 사업장's pay way when the company's own 대표 주기 has none", async () => {
  const { call } = server();
  const first = (await call("POST", "/api/reports", report(1, { company: "가상정밀", setup: cycle() }), from(1))).data;
  const c = first.company.id;
  let hit = (await call("GET", "/api/companies?q=" + encodeURIComponent("가상정밀"))).data.companies[0];
  assert.equal(hit.lead.pay, false);
  assert.equal(hit.lead.payWay, undefined, "no pay way anywhere yet");

  // Another 사업장 sends its setup with the pay.
  await call("POST", "/api/reports", report(2, { company: "가상정밀", dept: "온산공장", setup: setup() }), from(2));
  const page = (await call("GET", `/api/companies/${c}`)).data;
  const site = page.setups.find((s) => s.dept === "온산공장");
  hit = (await call("GET", "/api/companies?q=" + encodeURIComponent("가상정밀"))).data.companies[0];
  assert.equal(hit.lead.pay, false, "older apps still read the main 대표 주기's own");
  assert.equal(hit.lead.dept, "");
  assert.deepEqual(hit.lead.payWay, { setup: site.id, dept: "온산공장", ok: 1 });
  // Never a value of the pay (결정 D79).
  assert.equal(JSON.stringify(hit.lead).includes("rules"), false);
});
