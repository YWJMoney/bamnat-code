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

import { server } from "./helpers.mjs";
import { missWord } from "../src/growth.js";

test("[N13] 없다고 알리기: words are counted by week with no device, numbers and links are dropped, only twice-seen words show", async () => {
  assert.equal(missWord("  메모  줄무늬 "), "메모 줄무늬");
  assert.equal(missWord("010-1234-5678"), null);
  assert.equal(missWord("a"), null);
  assert.equal(missWord("http://x.y"), null);
  const { call, env } = server({ plaza: true });
  const post = (q, place = "settings") => call("POST", "/api/growth/miss", { place, q });
  assert.equal((await post("야간 수당 색")).data.counted, true);
  assert.equal((await post("01012345678")).data.counted, false);
  assert.equal((await post("아무거나", "nowhere")).data.counted, false);
  await post("야간 수당 색");
  await post("혼자만 쓴 말");
  const rows = await env.DB.prepare("SELECT * FROM search_misses").all();
  assert.ok(rows.results.every((r) => Object.keys(r).sort().join() === "n,place,week,word"));
  const { misses } = await import("../src/growth.js").then((g) => g.missList(env, Date.now()).then((m) => ({ misses: m })));
  assert.deepEqual(misses.map((m) => [m.word, m.n]), [["야간 수당 색", 2]]);
});

test("[N14][N15] 요청 판 14일 안내 and 고친 판 바꾼 곳", async () => {
  const { pieceChanges } = await import("../src/recipe-community.js");
  const a = JSON.stringify({ pieces: [{ kind: "calTheme", c: 1 }, { kind: "memoLook", m: 1 }] });
  const b = JSON.stringify({ pieces: [{ kind: "calTheme", c: 2 }, { kind: "alarms", x: 1 }] });
  assert.deepEqual(pieceChanges(a, b), [{ kind: "calTheme", how: "edit" }, { kind: "alarms", how: "add" }, { kind: "memoLook", how: "del" }]);
  assert.deepEqual(pieceChanges(a, a), []);
});
