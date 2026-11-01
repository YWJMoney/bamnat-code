// 1.36 장터 네 구간(테마·탭·부품·규칙·단추): a recipe of only parts is a 부품, one with buttons/rules is 규칙·단추. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../src/index.js";
import { sectionOf, MARKET_SCOPES } from "../src/recipes.js";

test("[GRW-market] parts-only and logic recipes get their own sections, with the tab they act on", () => {
  assert.deepEqual(MARKET_SCOPES, ["theme", "tab", "part", "logic"]);
  const parts = { pieces: [{ kind: "parts", parts: [{ id: "cal.head.title" }, { id: "cal.head" }] }] };
  assert.deepEqual(sectionOf(parts), { scope: "part", tabs: ["calendar"], unit: "part:cal.head" });
  assert.deepEqual(sectionOf({ pieces: [{ kind: "tabView", memo: {}, memoSort: {} }] }), { scope: "part", tabs: ["memo"], unit: "memo.view" });
  // Lines in two units are not one 부품: they fall back to 테마/탭.
  assert.notEqual(sectionOf({ pieces: [{ kind: "parts", parts: [{ id: "memo.checkbar" }, { id: "money.total" }] }] }).scope, "part");
  const logic = { pieces: [{ kind: "logic", buttons: [{ where: "day", do: "addMoney" }], rules: [{ do: "notify" }] }] };
  assert.deepEqual(sectionOf(JSON.stringify(logic)), { scope: "logic", tabs: ["alarm", "calendar"] });
  assert.deepEqual(sectionOf({ pieces: [{ kind: "logic", buttons: [{ where: "money", do: "addIncome" }], rules: [{ on: "memo", do: "screen", arg: "pay" }] }] }).tabs, ["memo", "money"]);
  assert.deepEqual(sectionOf({ pieces: [{ kind: "logic", rules: [{ do: "screen", arg: "pay" }] }] }).tabs, ["pay"]);
  const both = { pieces: [...parts.pieces, ...logic.pieces] };
  assert.equal(sectionOf(both).scope, "logic");
  assert.deepEqual(sectionOf("not json"), { scope: null, tabs: [] });
});
