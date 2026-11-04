// 1.36 [RUL-01] The server reads and runs formulas the same as core (docs/formula-cases.json, written by core FormulaCasesTest).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { parseFormula, evalFormula, formulaScope, valueText, formulaTextProblem, FORMULA } from "../src/formula.js";

const all = JSON.parse(readFileSync(new URL("../../../docs/formula-cases.json", import.meta.url), "utf8"));
const scope = formulaScope(all.vars, all.inputs);
const truthy = (v) => (v.t === "num" ? v.v !== 0 : v.t === "text" || v.t === "many" ? v.v.length > 0 : v.t === "bool" ? v.v : false);

test("every shared formula case reads and runs as on the phone", () => {
  for (const c of all.cases) {
    const p = parseFormula(c.src);
    let got;
    if (!p.ok) got = { code: p.error.code, from: p.error.from, ok: false };
    else {
      const v = evalFormula(c.src, scope);
      got = v === null ? { fails: true, ok: true } : { ok: true, text: valueText(v), truthy: truthy(v), type: v.t };
    }
    assert.deepEqual(got, c.want, c.src);
  }
});

test("limits and formulas inside text", () => {
  assert.equal(parseFormula("1+".repeat(400) + "1").error.code, "long");
  assert.equal(FORMULA.maxLen, 600);
  assert.equal(formulaTextProblem("야간 {이번주야간}번"), null);
  assert.equal(formulaTextProblem("야간 {근무"), "brace");
  assert.equal(formulaTextProblem("{근뮤}"), "name");
});
