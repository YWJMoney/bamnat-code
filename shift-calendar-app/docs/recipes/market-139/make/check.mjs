// 1.39 [MKW-01] 테마 점검: 글자 대비(readable)와 분위기 칩 고르기, 제목 겹침을 한 번에 봐요. `node docs/recipes/market-139/make/check.mjs`
import { readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import "../../../../server/company-worker/src/index.js";
import { sectionOf } from "../../../../server/company-worker/src/recipes.js";
const here = fileURLToPath(new URL(".", import.meta.url));
const moods = {}, styles = {}, titles = new Map();
let n = 0, top = 0;
const want = (f) => f.startsWith("themes-") ? { scope: "theme" } : f.startsWith("tab-") ? { scope: "tab", tab: f.split("-")[1].replace(/\.mjs$/, "") } : {};
const per = {};
for (const f of readdirSync(here).filter((f) => /^(themes|tab|tool|fx)-.*\.mjs$/.test(f)).sort()) {
  for (const w of (await import(`./${f}`)).default) {
    const sec = sectionOf(w.recipe), wt = want(f);
    if (wt.scope && sec.scope !== wt.scope) console.log(`구간 다름 ${f} ${w.recipe.title}: ${JSON.stringify(sec)}`);
    if (wt.tab && (sec.tabs.length !== 1 || sec.tabs[0] !== wt.tab)) console.log(`탭 다름 ${f} ${w.recipe.title}: ${JSON.stringify(sec)}`);
    const key = f.replace(/-[a-z]\.mjs$|\.mjs$/, "");
    per[key] = (per[key] ?? 0) + 1;
    if (!f.startsWith("themes-")) { if (titles.has(w.recipe.title)) console.log(`제목 겹침: ${w.recipe.title}`); titles.set(w.recipe.title, f); continue; }
    n++; if (w.showcase) top++;
    moods[w.mood] = (moods[w.mood] ?? 0) + 1;
    const st = (w.recipe.styles ?? []).length > 1 ? "모두" : w.recipe.styles[0];
    styles[st] = (styles[st] ?? 0) + 1;
    if (titles.has(w.recipe.title)) console.log(`제목 겹침: ${w.recipe.title} (${titles.get(w.recipe.title)}, ${f})`);
    titles.set(w.recipe.title, f);
    if (w.problems?.length) console.log(`대비 ${f} ${w.recipe.title}: ${w.problems.join(", ")}`);
  }
}
console.log(`테마 ${n}개, 대표 ${top}개`, per);
console.log("분위기", moods);
console.log("유형", styles);
