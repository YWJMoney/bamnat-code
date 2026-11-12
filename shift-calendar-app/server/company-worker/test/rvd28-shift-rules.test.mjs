// 2.0.0 [RVD-28] SD-08 (스튜디오 제작 오류 조사 뿌리 R6, 설계 S7-1) 근무 종류 한 규칙표: the app, the share server, the company server and the
// 장터 each wrote a kind of day's rules apart and drifted (PC-92 그림, PC-94 휴게, SN-16 글자, SN-17 회사 작품). Now core ShiftRules writes
// src/shift-rules.js (ShiftRulesExportTest) and every server reads it. Here: the servers count and cut letters as core does (its own
// SHIFT_RULE_SAMPLES), and a kind of day at every edge of the rule goes through all three servers. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { SHIFT_RULES, SHIFT_RULE_SAMPLES } from "../src/shift-rules.js";
import { graphemes, letters, cutLetters } from "../src/letters.js";
import { cleanSetup, iconOk } from "../src/company-setup.js";
import { EMOJI } from "../src/emoji-set.js";
import { server, device, from, setup } from "./helpers.mjs";
import { validate as linkCheck, iconOk as linkIconOk } from "../../share-worker/src/index.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const withName = (name) => JSON.parse(JSON.stringify(setup()).replaceAll("주간", name));
const withLabel = (label) => ({ ...setup(), shiftTypes: setup().shiftTypes.map((t, i) => (i === 0 ? { ...t, label } : t)) });

test("[RVD-28] SD-08 both servers read the one file core writes", () => {
  const here = readFileSync(new URL("../src/shift-rules.js", import.meta.url), "utf8");
  assert.equal(readFileSync(new URL("../../share-worker/src/shift-rules.js", import.meta.url), "utf8"), here);
  assert.match(here, /^\/\/ \[RVD-28\] 만든 파일이에요/);
  assert.deepEqual([SHIFT_RULES.types, SHIFT_RULES.label.letters, SHIFT_RULES.label.units, SHIFT_RULES.rest.max], [24, 2, 16, 720]);
});

test("[RVD-28] SD-08 letters are split, counted and cut as core does, for every sample core wrote", () => {
  for (const x of SHIFT_RULE_SAMPLES) {
    assert.deepEqual(letters(x.s), x.letters, x.s);
    assert.equal(graphemes(x.s), x.letters.length, x.s);
    assert.equal(cutLetters(x.s, SHIFT_RULES.label.letters, SHIFT_RULES.label.units), x.label, x.s);
    assert.equal(cutLetters(x.s, SHIFT_RULES.name.letters, SHIFT_RULES.name.units), x.name, x.s);
  }
});

test("[RVD-28] SD-08 the company server: a 근무 글자 is kept or cut as core cuts it, a name core takes is taken, never less than before", () => {
  for (const x of SHIFT_RULE_SAMPLES) {
    const label = cleanSetup(withLabel(x.s)).shiftTypes[0].label;
    assert.equal(label, x.s.length <= SHIFT_RULES.label.before.company ? x.s : x.label, x.s);
    let name;
    try {
      name = cleanSetup(withName(x.s)).shiftTypes[0].name;
    } catch (e) {
      name = e.code;
    }
    assert.equal(name, x.nameFits || x.s.length <= SHIFT_RULES.name.before.company ? x.s : "setup", x.s);
  }
});

test("[RVD-28] SD-08 every drawn icon and every emoji the app's picker gives is a 그림 both servers keep", () => {
  for (const k of SHIFT_RULES.icon.keys) assert.ok(iconOk(k) && linkIconOk(k), k);
  let n = 0;
  for (const e of EMOJI) {
    const icon = SHIFT_RULES.icon.prefix + e;
    assert.ok(iconOk(icon), e);
    assert.ok(linkIconOk(icon), e);
    n++;
  }
  assert.ok(n > 1000, String(n));
});

/** A file with 24 kinds of day, each at an edge of the rule: the longest names and 근무 글자, a 그림 of each kind, a 12-hour 휴게, two parts. */
function edges() {
  const labels = ["🧑🏻‍❤️‍💋‍🧑🏼", "👩‍⚕️👍🏿", "🇰🇷🌙", "1️⃣2️⃣", "🏴\u{E0067}\u{E0062}\u{E0073}\u{E0063}\u{E0074}\u{E007F}", "주간", "D1", ""];
  const icons = [...SHIFT_RULES.icon.keys, "e:🧑🏻‍❤️‍💋‍🧑🏼", "e:🏳️‍⚧️", "e:#️⃣", "e:🇰🇷", null];
  const types = Array.from({ length: SHIFT_RULES.types }, (_, i) => ({
    name: i === 0 ? "👩‍⚕️".repeat(16) : i === 1 ? "가".repeat(SHIFT_RULES.name.letters) : `${"나".repeat(17)}${String(i).padStart(2, "0")}`,
    label: labels[i % labels.length],
    color: i === 2 ? SHIFT_RULES.color.max : i === 3 ? 0 : 4280391411,
    start: "07:00", end: "07:00", break: i === 4 ? SHIFT_RULES.rest.max : 60, breakStart: "12:00", off: false,
    icon: icons[i % icons.length],
  }));
  // Two parts: 08:00~20:00 stopping 12:00~13:00 (the gap at its shortest), 30 minutes of 휴게 from 15:00.
  types[5] = { ...types[5], start: "08:00", end: "20:00", break: SHIFT_RULES.gap.min * 2 + 30, breakStart: "12:00", gapStart: "12:00",
    gap: SHIFT_RULES.gap.min * 2, rest: 30, restStart: "15:00" };
  return { ...setup(), shiftTypes: types, extraKindShiftSets: {}, rotation: { anchor: "2026-10-01", steps: types.map((t) => t.name) } };
}

const hex = (argb) => `#${(argb & 0xffffff).toString(16).padStart(6, "0").toUpperCase()}`;

test("[RVD-28] SD-08 a kind of day at every edge of the rule goes through the company server, the 장터 and the share server whole", async (t) => {
  const file = edges();
  // The company server keeps every field as sent.
  const kept = cleanSetup(file).shiftTypes;
  for (const [i, k] of kept.entries()) {
    const sent = file.shiftTypes[i];
    assert.deepEqual([k.name, k.label, k.color, k.icon, k.break], [sent.name, sent.label, sent.color, sent.icon, sent.break], sent.name);
  }
  assert.deepEqual([kept[5].gap, kept[5].rest], [60, 30]);
  // The 장터 takes it as a 회사 근무 주기 작품.
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const r = await call("POST", "/api/recipes", { device: device(1), scope: "public", recipe: { format: "bamnat-recipe", v: 1, title: "규칙 끝 주기",
    summary: "", styles: ["SHIFT"], pieces: [{ kind: "company", setup: JSON.stringify(file) }] } }, from(1));
  assert.equal(r.status, 201, JSON.stringify(r.data));
  // The share server keeps the same names, 근무 글자 (the empty one is the app's: it sends the name's first letter), 그림 and 휴게.
  const types = file.shiftTypes.map((s, i) => ({ id: i + 1, name: s.name, short: s.label || [...s.name][0], color: hex(s.color),
    time: `${s.start}-${s.end}`, ...(s.icon ? { icon: s.icon } : {}), rest: s.rest ?? s.break,
    ...(s.gap ? { gap: `${s.gapStart}-13:00`, restStart: s.restStart } : {}) }));
  const v = linkCheck({ v: 1, title: "규칙 끝", ttlDays: 30, types, days: types.map((x, i) => ({ date: `2026-12-${String(i + 1).padStart(2, "0")}`, type: x.id })) });
  assert.equal(v.error, undefined);
  for (const [i, k] of v.schedule.types.entries()) {
    assert.deepEqual([k.name, k.short, k.icon ?? null, k.rest], [types[i].name, types[i].short, types[i].icon ?? null, types[i].rest], types[i].name);
  }
});
