import { test } from "node:test";
import assert from "node:assert/strict";
import "../src/index.js";
import { partsMinApp, fxPartsOf as fxPartsOfLink } from "../src/recipes.js";
import { FORMULA_NAMES, fxReadsLink } from "../src/formula.js";

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

// 1.39 [LINK-03] 새 하루 상태·새 수식 이름을 쓰는 작품은 프로 장치 "link" (core FxPro.links/readsLink와 같은 판단).

test("link-139: new day states and names are the pro part link", () => {
  const parts = (lines) => ({ pieces: [{ kind: "parts", parts: lines }] });
  assert.deepEqual(fxPartsOfLink(parts([{ id: "cal.cell", on: "role.night" }])), []);
  assert.deepEqual(fxPartsOfLink(parts([{ id: "cal.cell", on: "cover" }])), ["link"]);
  assert.deepEqual(fxPartsOfLink(parts([{ id: "cal.cell", when: "남은할일 > 0" }])), ["link"]);
  assert.deepEqual(fxPartsOfLink(parts([{ id: "cal.cell", bg: { fx: '만약(대타, "금", 없음)' } }])), ["link"]);
  assert.deepEqual(fxPartsOfLink(parts([{ id: "cal.cell", when: "야간" }])), []);
  assert.deepEqual(fxPartsOfLink({ pieces: [{ kind: "decor", items: [{ on: "extra" }] }] }), ["link"]);
  assert.deepEqual(fxPartsOfLink({ pieces: [{ kind: "decor", items: [{ on: "weekend" }] }] }), []);
  assert.deepEqual(fxPartsOfLink({ pieces: [{ kind: "logic", rules: [{ on: "open", when: "잠잔시간 < 6", do: "toast" }] }] }), ["link"]);
  assert.equal(fxReadsLink("오늘 {추가근무시간}시간", true), true);
  assert.equal(fxReadsLink("근무시간 > 8"), false);
});
