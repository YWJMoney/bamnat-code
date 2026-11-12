// 2.0.0 [RVD-26] SN-16: what a person counts as one letter (core RecipeText.graphemes), for the 장터 (recipes.js) and the company server's
// 근무 글자 (company-setup.js) alike. The share server keeps a copy (share-worker/src/index.js letters) that its test (rvd26-short.test.mjs) holds to this one.
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

