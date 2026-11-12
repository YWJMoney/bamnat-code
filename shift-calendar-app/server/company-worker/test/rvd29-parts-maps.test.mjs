// 2.0.0 [RVD-29] 갈래 S RVS-04: core writes the 판 호환표 (parts-since.js, PartsSinceExportTest) with three more maps. An older app (1.36~1.39)
// leaves out a 조각 종류 it does not know, but drops a field it does not know and writes the work again (its hash no longer matches, RCP-07)
// and refuses the whole work over one 고른 값 it does not know. partsMinApp reads all three, and a 부품 줄's order (its children by their last
// word). When the reading changes the hourly job measures the works already up again, even with the same table. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { one, all, stmt, batch } from "../src/index.js";
import { partsMinApp, minAppOf, recheckMinApp, sha256hex, PARTS_SINCE, FX_SINCE_APP, MIN_APP_READING } from "../src/recipes.js";

/** Runs [body] with [maps] put into the table, then puts the table back. */
function withMaps(maps, body) {
  const saved = Object.fromEntries(Object.keys(maps).map((k) => [k, PARTS_SINCE[k]]));
  try {
    Object.assign(PARTS_SINCE, maps);
    body();
  } finally {
    for (const [k, v] of Object.entries(saved)) {
      if (v === undefined) delete PARTS_SINCE[k];
      else PARTS_SINCE[k] = v;
    }
  }
}

test("[RVD-29] a 조각 종류, a field (also one inside an 항목) and a 고른 값 core marks newer each raise what the work needs", () => {
  withMaps({ pieces: { ledger: 51 }, fields: { "logic.values": 53, "logic.rules.when": 54 }, picks: { "logic.rules.on=value": 53, "chip.days=sat": 54 } }, () => {
    assert.equal(partsMinApp({ pieces: [{ kind: "ledger", rows: [] }] }), 51);
    assert.equal(partsMinApp({ pieces: [{ kind: "logic", values: [{ key: "a", label: "가" }] }] }), 53);
    assert.equal(partsMinApp({ pieces: [{ kind: "logic", rules: [{ on: "tap", when: "x" }] }] }), 54);
    assert.equal(partsMinApp({ pieces: [{ kind: "logic", rules: [{ on: "value" }] }] }), 53);
    assert.equal(partsMinApp({ pieces: [{ kind: "logic", rules: [{ on: "tap" }] }] }), null);
    // A list of picked values: each value is a pick.
    assert.equal(partsMinApp({ pieces: [{ kind: "chip", days: ["mon", "sat"] }] }), 54);
    assert.equal(partsMinApp({ pieces: [{ kind: "chip", days: ["mon"] }] }), null);
    // The highest of all the work's pieces.
    assert.equal(partsMinApp({ pieces: [{ kind: "ledger" }, { kind: "chip", days: ["sat"] }] }), 54);
    // Names only count where they stand: a title, a field of another kind or a value of another field is not them.
    assert.equal(partsMinApp({ title: "ledger", pieces: [{ kind: "note", text: "ledger" }, { kind: "chip", values: [] }, { kind: "logic", label: "value" }] }), null);
    assert.equal(minAppOf(JSON.stringify({ pieces: [{ kind: "logic", values: [] }] })), 53);
  });
  // Before core writes the three maps (or a table without them) nothing changes.
  withMaps({ pieces: undefined, fields: undefined, picks: undefined }, () => {
    assert.equal(partsMinApp({ pieces: [{ kind: "logic", values: [], rules: [{ on: "value" }] }] }), null);
  });
});

test("[RVD-29] a 부품 줄's order names children an older app may not know: page under app is memo.page (52), task under widget 1.39's (53)", () => {
  const order = (id, o) => partsMinApp({ pieces: [{ kind: "parts", parts: [{ id, order: o }] }] });
  assert.equal(order("app", "page"), 52);
  assert.equal(order("widget", "head,task"), 53);
  assert.equal(order("app", "night"), 53);
  assert.equal(order("widget", "head,cell"), null);
  // A child name the parent does not have is no part (the server refuses such an order on its own).
  assert.equal(order("cal.cell", "task"), null);
});

test("[RVD-29] a server that measured the works with the old reading measures them again, even with the same table", async (t) => {
  const { server, device, from } = await import("./helpers.mjs");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call, env } = server({ plaza: true });
  const r = await call("POST", "/api/recipes", { device: device(1), scope: "public", recipe: { format: "bamnat-recipe", v: 1, title: "위젯 할 일 먼저",
    summary: "", styles: ["SHIFT"], pieces: [{ kind: "parts", parts: [{ id: "widget", order: "task,head" }] }] } }, from(1));
  assert.equal(r.status, 201, JSON.stringify(r.data));
  const minApp = async () => (await env.DB.prepare("SELECT min_app FROM recipes WHERE code = ?").bind(r.data.code).first()).min_app;
  assert.equal(await minApp(), 53);
  // As 976d112d (11-12) left it: measured without order, signed without the reading.
  const before = (await sha256hex(JSON.stringify([PARTS_SINCE, FX_SINCE_APP]))).slice(0, 16);
  await env.DB.prepare("UPDATE recipes SET min_app = NULL WHERE code = ?").bind(r.data.code).run();
  await env.DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('recipe_min_app_sig', ?)").bind(before).run();
  const d = { one, all, stmt, batch };
  assert.equal(await recheckMinApp(d, env, Date.now()), 1);
  assert.equal(await minApp(), 53);
  assert.ok(MIN_APP_READING >= 2);
  // Then still until the table or the reading changes again.
  assert.equal(await recheckMinApp(d, env, Date.now()), 0);
});
