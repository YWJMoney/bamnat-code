// 2.0.0 [RVD-24] SN-07 (스튜디오 제작 오류 조사): the 판 호환표 missed 1.38's memo.page·memo.page.rule·memo.photo and 1.39's night.*, so a work
// with one was listed to phones that refuse the whole work. The table is its own file now (parts-since.js, core will write it, 갈래 M), and when
// it changes the hourly job measures the works already up again, raising min_app only. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { one, all, stmt, batch } from "../src/index.js";
import { partsMinApp, minAppOf, recheckMinApp, PART_IDS, PARTS_SINCE } from "../src/recipes.js";

const parts = (...lines) => ({ pieces: [{ kind: "parts", parts: lines }] });

test("[RVD-24] SN-07 memo.page·memo.page.rule·memo.photo need 1.38 (52), night.* 1.39 (53); every name in the table is the server's", () => {
  for (const id of ["memo.page", "memo.page.rule", "memo.photo"]) assert.equal(partsMinApp(parts({ id, fill: 1 })), 52, id);
  for (const id of ["night", "night.date", "night.clock", "night.line", "night.card", "night.button", "night.morning"]) {
    assert.equal(partsMinApp(parts({ id, fill: 1 })), 53, id);
  }
  assert.equal(partsMinApp(parts({ id: "memo.page", fill: 1 }, { id: "night.clock", fill: 1 })), 53);
  assert.equal(partsMinApp(parts({ id: "memo.card", fill: 1 })), null);
  for (const id of Object.keys(PARTS_SINCE.ids)) assert.ok(PART_IDS.includes(id), id);
  // A 버튼 or 값 named like a part is not the part.
  assert.equal(partsMinApp({ pieces: [{ kind: "logic", buttons: [{ id: "night", label: "밤", where: "cal", do: "note" }] }] }), null);
  assert.equal(minAppOf(JSON.stringify(parts({ id: "memo.photo", fill: 1 }))), 52);
  assert.equal(minAppOf("not json"), null);
});

test("[RVD-24] fonts, motions and fills core marks newer are found where a work holds them", () => {
  const saved = { fonts: PARTS_SINCE.fonts, motions: PARTS_SINCE.motions, fills: PARTS_SINCE.fills };
  try {
    Object.assign(PARTS_SINCE, { fonts: { jua: 54 }, motions: { "tn:stack": 54 }, fills: { "pt.confetti": 54, sh: 0, gl: 54 } });
    assert.equal(partsMinApp({ pieces: [{ kind: "font", font: "jua" }] }), 54);
    assert.equal(partsMinApp({ pieces: [{ kind: "font", font: "sans" }] }), null);
    assert.equal(partsMinApp({ pieces: [{ kind: "motion", motion: "td:pulse|tn:stack" }] }), 54);
    assert.equal(partsMinApp({ pieces: [{ kind: "look", looks: "bw:5|ff:jua/fl:so.FF000000" }] }), 54);
    assert.equal(partsMinApp({ pieces: [{ kind: "screen", screens: "all~fl:pt.confetti.FF000000.FFFFFFFF.8.0|ft:sans" }] }), 54);
    assert.equal(partsMinApp({ pieces: [{ kind: "chip", paint: "gl.FF000000.8" }] }), 54);
    assert.equal(partsMinApp({ pieces: [{ kind: "chip", paint: "pt.dots.FF000000.FFFFFFFF.8.0" }] }), null);
    // A title or summary that happens to be a font's name is not a font.
    assert.equal(partsMinApp({ title: "jua", pieces: [{ kind: "note", text: "jua" }] }), null);
  } finally {
    Object.assign(PARTS_SINCE, saved);
  }
});

test("[RVD-24] SN-07 works already up are measured again once the table changes: raised only, written down, then still", async (t) => {
  const { server, device, from } = await import("./helpers.mjs");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call, env } = server({ plaza: true });
  const post = async (n, title, lines) => {
    const r = await call("POST", "/api/recipes", { device: device(n), scope: "public",
      recipe: { format: "bamnat-recipe", v: 1, title, summary: "", styles: ["SHIFT"], pieces: [{ kind: "parts", parts: lines }] } }, from(n));
    assert.equal(r.status, 201, JSON.stringify(r.data));
    return r.data.code;
  };
  const memo = await post(1, "메모 종이 노랑", [{ id: "memo.page", fill: 4294961979 }]);
  const night = await post(2, "밤 시계 크게", [{ id: "night.clock", fill: 4278190080 }]);
  const plain = await post(3, "칸 파랑", [{ id: "cal.cell", fill: 4282339765 }]);
  const held = await post(4, "밤 카드 출시 묶음", [{ id: "night.card", fill: 4278190080 }]);
  // New works take the new table at once.
  const minApp = async (code) => (await env.DB.prepare("SELECT min_app FROM recipes WHERE code = ?").bind(code).first()).min_app;
  assert.deepEqual([await minApp(memo), await minApp(night), await minApp(plain)], [52, 53, null]);
  const codes = async (app) => (await call("GET", `/api/recipes?style=SHIFT&sort=new&app=${app}`)).data.items.map((i) => i.code).sort();

  // What the server held before this table: memo·night as if any app read them; one a 출시 묶음 holds for 2.0.0 (54).
  await env.DB.prepare("UPDATE recipes SET min_app = NULL WHERE code IN (?, ?)").bind(memo, night).run();
  await env.DB.prepare("UPDATE recipes SET min_app = 54 WHERE code = ?").bind(held).run();
  assert.deepEqual(await codes(51), [memo, night, plain].sort());

  const d = { one, all, stmt, batch };
  // Two works an hour: four works, then one more hour that finds none and stops.
  assert.equal(await recheckMinApp(d, env, Date.now(), 2, 1) + await recheckMinApp(d, env, Date.now(), 2, 1), 2);
  assert.equal(await recheckMinApp(d, env, Date.now(), 2, 1), 0);
  assert.ok(await env.DB.prepare("SELECT value FROM settings WHERE key = 'recipe_min_app_sig'").first());
  assert.deepEqual([await minApp(memo), await minApp(night), await minApp(plain), await minApp(held)], [52, 53, null, 54]);
  assert.deepEqual(await codes(51), [plain]);
  assert.deepEqual(await codes(52), [memo, plain].sort());
  assert.deepEqual(await codes(53), [memo, night, plain].sort());
  const notes = await env.DB.prepare("SELECT code, reason, actor FROM recipe_actions WHERE action = 'min_app' ORDER BY code").all();
  assert.deepEqual(notes.results.map((r) => [r.code, r.actor]), [[memo, "자동 정리"], [night, "자동 정리"]].sort());
  assert.match(notes.results.find((r) => r.code === memo).reason, /판 호환표: 앱 모두 → 52부터/);

  // Done: the next hours do nothing until the table changes again.
  await env.DB.prepare("UPDATE recipes SET min_app = NULL WHERE code = ?").bind(memo).run();
  assert.equal(await recheckMinApp(d, env, Date.now()), 0);
  const saved = PARTS_SINCE.fonts;
  try {
    PARTS_SINCE.fonts = { jua: 54 };
    assert.equal(await recheckMinApp(d, env, Date.now()), 1);
    assert.equal(await minApp(memo), 52);
  } finally {
    PARTS_SINCE.fonts = saved;
  }
});
