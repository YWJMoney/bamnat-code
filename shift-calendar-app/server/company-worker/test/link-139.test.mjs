import { test } from "node:test";
import assert from "node:assert/strict";
import "../src/index.js";
import { partsMinApp } from "../src/recipes.js";
import { FORMULA_NAMES } from "../src/formula.js";

// 1.39 [LINK-01][LINK-02] 작품과 근무 입력·모든 탭 연동: the new day states and formula names the phone's core knows.
test("[LINK-01] 근무 입력 상태 (대근·특근·연차·직접 바꾼 날) are listed to 1.39 (53) and newer only, parts and 꾸밈 층 alike", () => {
  assert.equal(partsMinApp({ pieces: [{ kind: "parts", parts: [{ id: "cal.cell", on: "role.night" }] }] }), null);
  for (const on of ["leave", "extra", "cover", "edited"]) {
    assert.equal(partsMinApp({ pieces: [{ kind: "parts", parts: [{ id: "cal.cell", on }] }] }), 53, on);
    assert.equal(partsMinApp({ pieces: [{ kind: "decor", items: [{ kind: "emoji", glyph: "⭐", at: "cal.cell", on }] }] }), 53, on);
  }
  assert.equal(partsMinApp({ pieces: [{ kind: "decor", items: [{ at: "cal.cell", on: "role.off" }] }] }), null);
});

test("[LINK-02] the formula names match the phone's (추가근무·대근·할 일·수면·알람)", () => {
  const ko = new Set(FORMULA_NAMES.map((n) => n[0]));
  for (const n of ["추가근무", "추가근무시간", "근무표시간", "대근", "직접바꿈", "남은할일", "늦은할일", "잠잔시간", "근무알람"]) assert.ok(ko.has(n), n);
});
