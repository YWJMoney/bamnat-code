// BC 1.30 [ITEM-04] 수당 종류(회사 공통 · 사람마다 · %): what a version 4 setup may carry, and what it still may not. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { cleanSetup, readReport, fingerprint, describe as words } from "../src/index.js";
import { setup, report } from "./helpers.mjs";

/** The test setup as 1.30 sends it: 식대 one amount for everyone, 기본급2 10% of 기본급, 자격수당 each person's own. */
function v4() {
  const s = setup();
  s.v = 4;
  s.pay.items.push(
    { id: "meal", name: "식대", amount: { entries: [], raise: null }, cadence: "MONTHLY", types: [], ordinary: true, taxable: true,
      months: [], fixedOvertime: 0, festivals: [], festivalBefore: false, share: "FIXED", fixedAmount: 100000 },
    { id: "base2", name: "기본급2", amount: { entries: [], raise: null }, cadence: "MONTHLY", types: [], ordinary: true, taxable: true,
      months: [], fixedOvertime: 0, festivals: [], festivalBefore: false, share: "PERCENT", percent: 0.1, percentOf: "BASE" },
  );
  return s;
}

const refusedWith = (change, code) => {
  const s = v4();
  change(s);
  assert.throws(() => readReport(report(1, { setup: s })), (e) => e.code === code && e.field === "items", code);
};

test("[ITEM-04] a version 4 setup keeps who sets each amount, and only a company's one amount", () => {
  const clean = cleanSetup(v4());
  assert.equal(clean.v, 4);
  const [, license, , meal, base2] = clean.pay.items;
  assert.deepEqual([meal.share, meal.fixedAmount, meal.amount.entries], ["FIXED", 100000, []]);
  assert.deepEqual([base2.share, base2.percent, base2.percentOf], ["PERCENT", 0.1, "BASE"]);
  // 사람마다 is what every allowance before 1.30 was: nothing is added to it.
  assert.equal("share" in license, false);
  assert.equal("fixedAmount" in license, false);
});

test("[ITEM-04] a person's own amount is still refused: an amount comes only as FIXED's fixedAmount", () => {
  refusedWith((s) => { s.pay.items[1].fixedAmount = 57500; }, "amount");
  refusedWith((s) => { s.pay.items[1].share = "PERSONAL"; s.pay.items[1].fixedAmount = 57500; }, "amount");
  refusedWith((s) => { s.pay.items[4].fixedAmount = 100000; }, "amount");
  refusedWith((s) => { s.pay.items[3].fixedAmount = 0; }, "amount");
  refusedWith((s) => { s.pay.items[3].fixedAmount = 10000001; }, "amount");
  refusedWith((s) => { s.pay.items[3].fixedAmount = 1000.5; }, "amount");
  refusedWith((s) => { s.pay.items[3].amount = { entries: [{ from: "2026-01-01", value: 100000 }], raise: null }; }, "amount");
  // A percent is paid every month, more than 0 and at most 500%, of one of three amounts.
  refusedWith((s) => { s.pay.items[4].percent = 0; }, "setup");
  refusedWith((s) => { s.pay.items[4].percent = 6; }, "setup");
  refusedWith((s) => { delete s.pay.items[4].percent; }, "setup");
  refusedWith((s) => { s.pay.items[4].percentOf = "HOURLY"; }, "setup");
  refusedWith((s) => { s.pay.items[4].cadence = "PER_WORK_DAY"; }, "setup");
  refusedWith((s) => { s.pay.items[4].share = "SOMETIMES"; }, "setup");
  const newer = v4();
  newer.v = 5;
  assert.throws(() => cleanSetup(newer), (e) => e.code === "setup_version");
});

test("[ITEM-04] only a setup that says who sets an amount is 4, so 1.29 still reads every other one", () => {
  const plain = setup();
  plain.v = 4;
  assert.equal(cleanSetup(plain).v, 2);
  const said = setup();
  said.v = 4;
  said.pay.items[1].share = "PERSONAL";
  assert.equal(cleanSetup(said).v, 2);
});

test("[ITEM-04] a setup from 1.29 (version 3 and before) reads and fingerprints as it always did", async () => {
  const old = setup();
  const clean = cleanSetup(old);
  assert.ok(clean.pay.items.every((i) => !("share" in i) && !("percent" in i) && !("fixedAmount" in i)));
  // The fingerprint of a setup with no 1.30 fields is the one 1.29 made (the same grp, the same votes).
  const sameAgain = setup();
  sameAgain.v = 4;
  assert.equal(await fingerprint(cleanSetup(sameAgain)), await fingerprint(clean));
  // PERSONAL said out loud is the same rule as saying nothing.
  const said = setup();
  said.pay.items[1].share = "PERSONAL";
  assert.equal(await fingerprint(cleanSetup(said)), await fingerprint(clean));
});

test("[ITEM-04] who sets the amount, the percent and the company's amount make another rule", async () => {
  const a = await fingerprint(cleanSetup(v4()));
  const other = async (change) => {
    const s = v4();
    change(s);
    return fingerprint(cleanSetup(s));
  };
  assert.notEqual(await other((s) => { s.pay.items[3].fixedAmount = 120000; }), a);
  assert.notEqual(await other((s) => { s.pay.items[4].percent = 0.15; }), a);
  assert.notEqual(await other((s) => { s.pay.items[4].percentOf = "ORDINARY"; }), a);
  assert.notEqual(await other((s) => { s.pay.items[1].share = "FIXED"; }), a);
  // Names, ids and order still do not.
  assert.equal(await other((s) => { s.pay.items.reverse(); s.pay.items[0].id = "x"; }), a);
});

test("[ITEM-04] the developer reads who sets each amount in the app's words", () => {
  const lines = Object.fromEntries(words(cleanSetup(v4())));
  assert.equal(lines["수당"], "상여(2·4·6·8·10·12월 기본급 100%) · 자격수당(매달 통상임금) · 야간수당(야간마다) · " +
    "식대(매달 회사 공통 100,000원 · 통상임금) · 기본급2(매달 기본급의 10% · 통상임금)");
});
