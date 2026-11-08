// 1.39 [TASK-07] 할 일 위젯 부품: a maker's recipe with the new parts or states is listed only to apps that read them. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../src/index.js";
import { partsMinApp, PART_IDS, PARTS_SINCE } from "../src/recipes.js";

test("[TASK-07] 할 일 위젯 부품 is a part, and older apps do not see recipes that use it", () => {
  for (const id of Object.keys(PARTS_SINCE.ids)) assert.ok(PART_IDS.includes(id), id);
  assert.equal(partsMinApp({ pieces: [{ kind: "parts", parts: [{ id: "widget.cell", on: "today" }] }] }), null);
  assert.equal(partsMinApp({ pieces: [{ kind: "parts", parts: [{ id: "widget.task", fill: 1 }] }] }), 53);
  assert.equal(partsMinApp({ pieces: [{ kind: "parts", parts: [{ id: "widget", on: "done" }] }] }), 53);
  // A decor item's own `on` is not a part's state.
  assert.equal(partsMinApp({ pieces: [{ kind: "decor", items: [{ on: "done" }] }] }), null);
});

test("[TASK-07] a 1.39 widget part is listed to 1.39 (53) and newer only, and its new 판 keeps that", async (t) => {
  const { server, device, from } = await import("./helpers.mjs");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call } = server({ plaza: true });
  const post = async (n, title, parts) => {
    const r = await call("POST", "/api/recipes", { device: device(n), scope: "public",
      recipe: { format: "bamnat-recipe", v: 1, title, summary: "", styles: ["SHIFT"], pieces: [{ kind: "parts", parts }] } }, from(n));
    assert.equal(r.status, 201, JSON.stringify(r.data));
    return r.data.code;
  };
  const old = await post(1, "위젯 칸 파랑", [{ id: "widget.cell", on: "today", fill: 4282339765 }]);
  const task = await post(2, "할 일 줄 분홍", [{ id: "widget.task", on: "done", fill: 4294940928 }]);
  const codes = async (app) => (await call("GET", `/api/recipes?style=SHIFT&sort=new${app ? `&app=${app}` : ""}`)).data.items.map((i) => i.code).sort();
  assert.deepEqual(await codes(52), [old]);
  assert.deepEqual(await codes(0), [old]);
  assert.deepEqual(await codes(53), [old, task].sort());
});
