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

test("[MKP-01] 1.39 장터 로딩: body=1 sends each card's written form and hash; a work read with no phone is the same as with one", async (t) => {
  const { device, from } = await import("./helpers.mjs");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call } = server({ plaza: true });
  const recipe = { format: "bamnat-recipe", v: 1, title: "야간 많은 교대 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
    pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }] };
  const a = await call("POST", "/api/recipes", { device: device(1), recipe, scope: "public" }, from(1));
  assert.equal(a.status, 201);
  const plain = await call("GET", "/api/recipes?style=SHIFT&sort=new");
  assert.equal(plain.data.items[0].body, undefined, "without body=1 the list is as before");
  const full = await call("GET", "/api/recipes?style=SHIFT&sort=new&body=1");
  const item = full.data.items[0];
  assert.equal(item.code, a.data.code);
  assert.equal(item.hash, a.data.hash);
  const shown = await call("GET", `/api/recipes/${a.data.code}`);
  assert.equal(shown.status, 200);
  assert.equal(shown.data.body, item.body);
  assert.equal(shown.data.hash, item.hash);
  assert.equal(shown.data.mine, false);
  // The maker still sees their own (named by the phone), never a copy kept for everyone.
  const mine = await call("GET", `/api/recipes/${a.data.code}`, undefined, { "x-device": device(1) });
  assert.equal(mine.data.mine, true);
});

test("[MKC-01] 1.39 장터 분류: 탭별 작품은 꾸미기·기능, 도구는 부품·자동 규칙·단추; scope=tool이 부품과 규칙·단추를 함께", async (t) => {
  const { madeOf, toolsOf } = await import("../src/recipes.js");
  assert.equal(madeOf({ pieces: [{ kind: "memoLook" }] }), "look");
  assert.equal(madeOf({ pieces: [{ kind: "memoTpl" }] }), "func");
  assert.equal(madeOf({ pieces: [{ kind: "memoLook" }, { kind: "memoTpl" }] }), "both");
  assert.equal(madeOf({ pieces: [{ kind: "shiftLook" }] }), "look", "근무 꾸미기는 색·글자라 꾸미기예요");
  assert.equal(madeOf("x"), null);
  assert.deepEqual(toolsOf({ pieces: [{ kind: "logic", rules: [{}], buttons: [{}] }] }, "logic"), ["rule", "button"]);
  assert.deepEqual(toolsOf({ pieces: [{ kind: "logic", buttons: [{}] }] }, "logic"), ["button"]);
  assert.deepEqual(toolsOf({ pieces: [{ kind: "parts", parts: [{ id: "cal.head" }] }] }, "part"), ["part"]);
  assert.deepEqual(toolsOf({ pieces: [{ kind: "memoLook" }] }, "tab"), []);

  const { device, from } = await import("./helpers.mjs");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call } = server({ plaza: true });
  const post = async (n, title, pieces) => {
    const r = await call("POST", "/api/recipes", { device: device(n), scope: "public",
      recipe: { format: "bamnat-recipe", v: 1, title, summary: "", styles: ["SHIFT"], pieces } }, from(n));
    assert.equal(r.status, 201, JSON.stringify(r.data));
    return r.data.code;
  };
  const look = await post(1, "메모 종이 분홍", [{ kind: "memoLook", paper: "lined" }]);
  const tpl = await post(2, "출근 체크 메모 틀", [{ kind: "memoTpl", items: [{ title: "출근 체크", check: ["물"] }] }]);
  const part = await post(3, "달력 머리 별", [{ kind: "parts", parts: [{ id: "cal.head", fill: 4294940928 }] }]);
  const rule = await post(4, "야간 끝 알림", [{ kind: "logic", rules: [{ name: "야간 끝", on: "end", do: "notify", arg: "수고했어요" }] }]);
  const button = await post(5, "커피 단추", [{ kind: "logic", buttons: [{ label: "커피", where: "money", do: "addMoney", arg: "커피" }] }]);
  const codes = async (q) => (await call("GET", `/api/recipes?style=SHIFT&sort=new&${q}`)).data.items.map((i) => i.code).sort();
  assert.deepEqual(await codes("scope=tab&tab=memo"), [look, tpl].sort());
  assert.deepEqual(await codes("scope=tab&tab=memo&made=look"), [look]);
  assert.deepEqual(await codes("scope=tab&tab=memo&made=func"), [tpl]);
  assert.deepEqual(await codes("scope=tool"), [part, rule, button].sort());
  assert.deepEqual(await codes("scope=tool&tool=rule"), [rule]);
  assert.deepEqual(await codes("scope=tool&tool=button"), [button]);
  assert.deepEqual(await codes("scope=tool&tool=part"), [part]);
  assert.deepEqual(await codes("scope=tool&tab=money"), [button]);
  const items = (await call("GET", "/api/recipes?style=SHIFT&sort=new&scope=tool")).data.items;
  assert.deepEqual(items.find((i) => i.code === rule).tools, ["rule"]);
  assert.equal(items.find((i) => i.code === part).scope, "part");
  const tabItems = (await call("GET", "/api/recipes?style=SHIFT&sort=new&scope=tab")).data.items;
  assert.equal(tabItems.find((i) => i.code === tpl).made, "func");
  assert.equal(tabItems.find((i) => i.code === look).made, "look");
});
