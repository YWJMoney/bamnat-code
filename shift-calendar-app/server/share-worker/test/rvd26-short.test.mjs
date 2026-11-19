// 2.0.0 [RVD-26] SN-16 (스튜디오 제작 오류 조사): 근무 글자 is two letters as a person counts them (👩‍⚕️ is one) up to 16 code units, as the app
// and a 작품 keep it. The link took 4 code units and refused more, so a 작품's emoji label broke the link or came half. Now what 4 took goes as
// it was, a longer one is its first two letters, and nothing about a 근무 글자 stops the link (총괄 11-12). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { validate, letters, shortOf } from "../src/index.js";
import { graphemes } from "../../company-worker/src/letters.js";

const link = (short) => ({ v: 1, title: "민지", ttlDays: 30, types: [{ id: 1, name: "주간", short, color: "#1E88E5", time: "07:00-19:00" }],
  days: [{ date: "2026-12-01", type: 1 }] });

const SAMPLES = ["주", "주간", "DAY1", "👩‍⚕️", "👩‍⚕️👍🏿", "👩‍⚕️👍🏿🌙", "🇰🇷🌙", "🇰🇷🇯🇵🌙", "1️⃣2️⃣3️⃣", "#️⃣", "🧑🏻‍❤️‍💋‍🧑🏼", "🏳️‍⚧️",
  "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "👨‍👩‍👧‍👦👨‍👩‍👧‍👦", "éa", "주간근무입니다", "☀", "❤️🌙"];

test("[RVD-26] SN-16 the share server counts letters as the company server (core RecipeText.graphemes) does", () => {
  for (const s of SAMPLES) {
    assert.equal(letters(s).length, graphemes(s), s);
    assert.equal(letters(s).join(""), s, s);
  }
});

test("[RVD-26] SN-16 근무 글자: up to 4 code units as before, else the first two letters up to 16; never the link's end", () => {
  const cases = [["주", "주"], ["DAY1", "DAY1"], ["👩‍⚕️👍🏿", "👩‍⚕️👍🏿"], ["👩‍⚕️👍🏿🌙", "👩‍⚕️👍🏿"], ["🇰🇷🇯🇵🌙", "🇰🇷🇯🇵"], ["주간근무입니다", "주간"],
    ["🧑🏻‍❤️‍💋‍🧑🏼", "🧑🏻‍❤️‍💋‍🧑🏼"], ["👨‍👩‍👧‍👦👨‍👩‍👧‍👦", "👨‍👩‍👧‍👦"], ["1️⃣2️⃣3️⃣", "1️⃣2️⃣"]];
  for (const [given, kept] of cases) {
    assert.equal(shortOf(given), kept, given);
    const v = validate(link(given));
    assert.equal(v.error, undefined, given);
    assert.equal(v.schedule.types[0].short, kept, given);
  }
  // Empty or not text is still refused as before.
  for (const bad of ["", " ", null, 7]) assert.equal(validate(link(bad)).error, "type", JSON.stringify(bad));
});
