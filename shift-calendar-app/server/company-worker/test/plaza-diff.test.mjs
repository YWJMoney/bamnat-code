// AX 1.30 [ADMX-02] 판 차이 field by field (src/plaza-diff.js): describe()'s lines cut into facts, matched by label and
// compared word by word, with the one line every screen shows. Ten cases a rule's versions really differ by.
import { test } from "node:test";
import assert from "node:assert/strict";
import { describe } from "../src/index.js";
import { factsOf, diffFacts, tokenDiff, lineChanges } from "../src/plaza-diff.js";
import { setup } from "./helpers.mjs";

const changes = (edit) => {
  const b = setup();
  edit(b);
  return lineChanges(describe(setup()), describe(b));
};
const marked = (c, label) => c.facts.changed.find((x) => x.label === label).tokens.filter((t) => t.kind !== "same").map((t) => `${t.kind}:${t.t}`);

test("[ADMX-02] the same setup: nothing changed, no summary", () => {
  const c = changes(() => {});
  assert.deepEqual([c.added, c.removed, c.summary, c.facts.changed, c.facts.added, c.facts.removed], [[], [], "", [], [], []]);
  assert.equal(diffFacts(factsOf(describe(setup())), factsOf(describe(setup()))).same, true);
});

test("[ADMX-02] one day of the order: the day and what it became, not the whole line", () => {
  const c = changes((s) => { s.rotation.steps[3] = "주간"; s.rotation.steps[4] = "주간"; });
  assert.equal(c.summary, "순서 4·5일째 휴무·야간→주간");
  assert.deepEqual(marked(c, "순서"), ["del:휴무", "add:주간", "del:야간", "add:주간"]);
  // The 1.29 lines are still there for older apps.
  assert.equal(c.added.length, 1);
  assert.match(c.added[0][0], /^순서 8일$/);
});

test("[ADMX-02] a longer order says how many days", () => {
  const c = changes((s) => { s.rotation.steps.push("주간", "휴무"); });
  assert.equal(c.summary, "순서 8일→10일");
});

test("[ADMX-02] an allowance added and one taken out are named, each on its own", () => {
  const c = changes((s) => {
    s.pay.items.push({ ...s.pay.items[1], id: "meal", name: "식대" });
    s.pay.items = s.pay.items.filter((i) => i.id !== "night");
  });
  assert.deepEqual(c.facts.added.map(([l]) => l), ["수당·식대"]);
  assert.deepEqual(c.facts.removed.map(([l]) => l), ["수당·야간수당"]);
  assert.equal(c.summary, "수당·식대 매달 통상임금 더함 · 수당·야간수당 야간마다 뺌");
});

test("[ADMX-02] two allowances added are counted", () => {
  const c = changes((s) => { s.pay.items.push({ ...s.pay.items[1], id: "a", name: "식대" }, { ...s.pay.items[1], id: "b", name: "교통비" }); });
  assert.equal(c.summary, "수당 2개 더함");
});

test("[ADMX-02] a premium rate: 법대로 to 60%, only the number marked", () => {
  const c = changes((s) => { s.pay.rules.night = 0.6; });
  assert.equal(c.summary, "가산·야간 법대로→60%");
  assert.deepEqual(marked(c, "가산·야간"), ["del:법대로", "add:60%"]);
});

test("[ADMX-02] a kind's share of the base and its night premium", () => {
  const c = changes((s) => { s.pay.rules.extra[3].percent = 0.15; s.pay.rules.extra[3].adds[1].rate = 0.6; });
  assert.equal(c.summary, "대근 10%→15%, 50%→60%");
  assert.deepEqual(marked(c, "대근"), ["del:10%", "add:15%", "del:50%", "add:60%"]);
});

test("[ADMX-02] a shift's times: just that shift", () => {
  const c = changes((s) => { s.shiftTypes[0].start = "08:30"; s.shiftTypes[0].end = "17:30"; });
  assert.equal(c.summary, "근무·주간 07:00~19:00→08:30~17:30");
  assert.equal(c.facts.changed.length, 1);
});

test("[ADMX-02] many changes: four parts and how many more", () => {
  const c = changes((s) => {
    s.pay.rules.night = 0.6;
    s.pay.rules.ot = 0.6;
    s.pay.rules.hw = 0.6;
    s.pay.monthlyHours = 209;
    s.shiftTypes[1].break = 30;
  });
  assert.match(c.summary, / 외 1곳$/);
  assert.equal(c.summary.split(" · ").length, 4);
});

test("[ADMX-02] facts: one per shift, premium, kind and allowance; brackets keep a part whole", () => {
  const f = factsOf([["근무", "주간 07:00~19:00 휴게 60분 · 휴무(쉬는 날)"], ["가산율", "야간 60%"], ["수당", "식대(매달 · 통상임금) · 상여(2·4월)"], ["순서 4일", "주 주 야 야"]]);
  assert.deepEqual(f, [["근무·주간", "07:00~19:00 휴게 60분"], ["근무·휴무", "쉬는 날"], ["가산·연장", "법대로"], ["가산·야간", "60%"], ["가산·휴일", "법대로"],
    ["가산·휴일 8시간 넘게", "법대로"], ["수당·식대", "매달 · 통상임금"], ["수당·상여", "2·4월"], ["순서", "4일: 주 주 야 야"]]);
  // A long text is compared as two wholes, so a big setup never takes long.
  const long = Array.from({ length: 500 }, (_, i) => `w${i}`).join(" ");
  assert.deepEqual(tokenDiff(long, `${long} x`).map((t) => t.kind), ["del", "add"]);
});
