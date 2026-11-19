// 2.0.0 [RVD-26] SN-16: what a person counts as one letter (core RecipeText.graphemes), for the 장터 (recipes.js) and the company server's
// 근무 글자 (company-setup.js) alike. The share server keeps a copy (share-worker/src/index.js letters) that its test (rvd26-short.test.mjs) holds to this one.
// [RVD-28] SD-08: letters() and cut() are core ShiftRules.letters·cut (cutLetters); test/rvd28-shift-rules.test.mjs holds them to core's own SHIFT_RULE_SAMPLES.
export const ZWJ = 0x200d;
export const MARK = /[\p{Mn}\p{Me}]/u;

/** core RecipeText.graphemes: what a person counts as one letter. */
export function graphemes(s) {
  const cps = [...s].map((c) => c.codePointAt(0));
  let n = 0;
  let flagHalf = false;
  for (let i = 0; i < cps.length; i++) {
    const cp = cps[i];
    if (cp === ZWJ) {
      i++;
    } else if ((cp >= 0xfe00 && cp <= 0xfe0f) || (cp >= 0x1f3fb && cp <= 0x1f3ff) || (cp >= 0xe0020 && cp <= 0xe007f) || MARK.test(String.fromCodePoint(cp))) {
      // adds nothing
    } else if (cp >= 0x1f1e6 && cp <= 0x1f1ff) {
      if (!flagHalf) n++;
      flagHalf = !flagHalf;
      continue;
    } else n++;
    flagHalf = false;
  }
  return n;
}


/**
 * 2.0.0 [RVD-28] SD-08 core ShiftRules.letters: the letters [graphemes] counts, cut apart. A joiner and what it joins, a variation selector, a
 * skin tone, a combining mark or a tag letter go with the letter before them (marks with none before them, with the next), and a flag's two
 * letters are one.
 */
export function letters(s) {
  const out = [];
  let lead = "";
  const join = (x) => { if (out.length) out[out.length - 1] += x; else lead += x; };
  const push = (x) => { out.push(lead + x); lead = ""; };
  const cps = [...s];
  let flagHalf = false;
  for (let i = 0; i < cps.length; i++) {
    const cp = cps[i].codePointAt(0);
    if (cp === ZWJ) {
      join(cps[i] + (cps[i + 1] ?? ""));
      i++;
    } else if ((cp >= 0xfe00 && cp <= 0xfe0f) || (cp >= 0x1f3fb && cp <= 0x1f3ff) || (cp >= 0xe0020 && cp <= 0xe007f) || MARK.test(cps[i])) {
      join(cps[i]);
    } else if (cp >= 0x1f1e6 && cp <= 0x1f1ff) {
      if (flagHalf) join(cps[i]); else push(cps[i]);
      flagHalf = !flagHalf;
      continue;
    } else push(cps[i]);
    flagHalf = false;
  }
  return out;
}

/** [RVD-28] core ShiftRules.cut: [s] when it has at most [max] letters in [units] code units, else as many of its first letters as fit. */
export function cutLetters(s, max, units) {
  if (s.length <= units && graphemes(s) <= max) return s;
  let out = "";
  for (const l of letters(s).slice(0, max)) {
    if ((out + l).length > units) break;
    out += l;
  }
  return out;
}
