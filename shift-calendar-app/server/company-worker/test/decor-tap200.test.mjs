// 2.0.0 [RVM-23] RCP-17·STU-19 (보이지 않는 꾸밈 단추, 칸을 덮는 꾸밈 단추): the server refuses a 꾸밈 단추 one cannot see or that is bigger
// than a button, as core DecorTapRule does (core ReceivedSafetyTest.theServerHoldsTheSameRule keeps the numbers the same). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import "./helpers.mjs"; // index.js first, as the worker loads (recipes.js and index.js import each other)
import { cleanRecipe, recipeCanonical } from "../src/recipes.js";
import { DECOR_TAP, decorSeen, decorTapProblem } from "../src/decor.js";

const star = { emoji: "⭐", at: "cal.cell", x: 100, y: 100, w: 20, h: 20, tap: "b0001" };
const recipe = (items) => ({
  format: "bamnat-recipe", v: 1, title: "꾸밈 단추 시험", summary: "", styles: ["SHIFT"],
  pieces: [{ kind: "decor", items }],
});
const code = async (items) => {
  try {
    await cleanRecipe(recipeCanonical(recipe(items)));
    return "ok";
  } catch (e) {
    return `${e.code}${e.field ? " " + e.field : ""}`;
  }
};

test("[RVM-23] a 꾸밈 단추 one can see and that is button-sized goes in", async () => {
  assert.equal(await code([star]), "ok");
  assert.equal(await code([star, { ...star, x: 300 }, { ...star, x: 500 }]), "ok");
  // A picture with no button can be anything.
  assert.equal(await code([{ ...star, tap: undefined, alpha: 5, w: 400, h: 400 }]), "ok");
});

test("[RVM-23] one hidden, nearly clear, bigger than a button, a whole-screen picture or a fourth on a part is refused", async () => {
  const bad = [
    [{ ...star, alpha: DECOR_TAP.seen - 1 }],
    [{ ...star, hide: true }],
    [{ ...star, w: DECOR_TAP.maxW + 1 }],
    [{ ...star, h: DECOR_TAP.maxH + 1 }],
    [{ ...star, at: "app", full: true }],
    [{ shape: "rect", at: "cal.cell", x: 0, y: 0, w: 40, h: 40, fill: 4278190080, fillA: 0, tap: "b0001" }],
    [star, { ...star, x: 200 }, { ...star, x: 300 }, { ...star, x: 400 }],
  ];
  for (const items of bad) {
    const got = await code(items);
    assert.ok(got.startsWith("decor_tap"), `${JSON.stringify(items.at(-1))} → ${got}`);
  }
  // Another part counts on its own.
  assert.equal(await code([star, { ...star, x: 200 }, { ...star, x: 300 }, { ...star, at: "cal.head" }]), "ok");
});

test("[RVM-23] what is seen follows the drawn colour", () => {
  const box = { shape: "rect", at: "cal.cell", w: 40, h: 40, tap: "b0001", fill: 4278190080, fillA: 0 };
  assert.equal(decorSeen(box), false);
  assert.equal(decorSeen({ ...box, line: "solid", lineW: 1.5 }), true);
  assert.equal(decorSeen({ ...box, fillA: 100 }), true);
  assert.equal(decorSeen({ text: "누르세요", at: "cal.cell", ink: 0x10FFFFFF }), false);
  assert.equal(decorTapProblem([{ ...star, tap: "" }, star]), -1);
});
