// 2.0.0 [RVS-18] 갈래 S SI-04: the studio's '작품 크기·호환' chip (core RecipeNeeds) says which app a work needs before it goes up. core
// writes, for every work kept in docs/recipes, what it reads (fixtures/min-app-cases.json, MinAppAgreesTest); partsMinApp must read the same
// from the same file, or the chip would promise a phone the 장터 then hides the work from. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import "../src/index.js"; // recipes.js is read through index.js (their tables import each other)
import { partsMinApp } from "../src/recipes.js";

const cases = JSON.parse(readFileSync(new URL("./fixtures/min-app-cases.json", import.meta.url), "utf8"));
const docs = new URL("../../../docs/recipes/", import.meta.url);

test("[RVS-18] partsMinApp reads each kept work as the studio's chip does", () => {
  assert.ok(cases.length > 100, `${cases.length} cases`);
  const files = new Map();
  const wrong = [];
  for (const c of cases) {
    if (!files.has(c.file)) files.set(c.file, JSON.parse(readFileSync(new URL(c.file, docs), "utf8")));
    const f = files.get(c.file);
    const recipe = c.at < 0 ? f : f[c.at].recipe;
    const got = partsMinApp(recipe);
    if (got !== c.need) wrong.push(`${c.file}#${c.at}: core ${c.need}, server ${got}`);
  }
  assert.deepEqual(wrong, []);
  // Not all one answer: the table tells works apart.
  assert.ok(new Set(cases.map((c) => c.need)).size >= 2);
});
