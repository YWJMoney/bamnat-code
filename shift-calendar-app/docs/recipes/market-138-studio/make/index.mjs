// 1.38 [STM-07] 스튜디오 재료로 만든 장터 작품: `node docs/recipes/market-138-studio/make/index.mjs` → docs/recipes/market-138-studio/*.json.
// 다음은 `cd server/company-worker && node tools/market-seed.mjs --set 138s --check`.
import { readFileSync, writeFileSync, readdirSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { lint } from "../../market-136/make/lint.mjs";
import { USED, ALL_KEYS } from "./mat.mjs";

const OUT = fileURLToPath(new URL("..", import.meta.url));
const RECIPES = fileURLToPath(new URL("../..", import.meta.url));
const files = {};
for (const [file, mod] of [["theme.json", "./themes.mjs"], ["tab-calendar.json", "./tab-calendar.mjs"], ["tab-memo.json", "./tab-memo.mjs"],
  ["tab-money.json", "./tab-money.mjs"], ["tab-pay.json", "./tab-pay.mjs"], ["tab-widget.json", "./tab-widget.mjs"], ["tab-alarm.json", "./tab-alarm.mjs"],
  ["part.json", "./parts.mjs"], ["logic.json", "./logic.mjs"]]) {
  try {
    files[file] = (await import(mod)).default;
  } catch (e) {
    if (e.code !== "ERR_MODULE_NOT_FOUND" || !String(e.message).includes(mod.slice(2))) throw e;
  }
}

// 이미 장터에 있는 제목은 쓰지 않아요(같은 제목은 그 작품의 새 판이 돼요).
const taken = new Set();
for (const f of readdirSync(RECIPES).filter((x) => x.endsWith(".json"))) {
  const v = JSON.parse(readFileSync(join(RECIPES, f), "utf8"));
  for (const e of Array.isArray(v) ? v : v.items ?? []) { const r = e.recipe ?? e; if (r?.title) taken.add(r.title); }
}
for (const d of ["market-136", "market-138"]) {
  for (const f of readdirSync(join(RECIPES, d)).filter((x) => x.endsWith(".json"))) {
    const v = JSON.parse(readFileSync(join(RECIPES, d, f), "utf8"));
    for (const e of Array.isArray(v) ? v : [v]) { const r = e.recipe ?? e; if (r?.title) taken.add(r.title); }
  }
}

const problems = [];
const seen = new Set();
for (const f of readdirSync(OUT).filter((x) => x.endsWith(".json"))) unlinkSync(join(OUT, f));
let n = 0;
for (const [name, list] of Object.entries(files)) {
  for (const w of list) {
    const t = w.recipe.title;
    if (seen.has(t)) problems.push(`${name} · ${t}: 제목이 겹쳐요`);
    if (taken.has(t)) problems.push(`${name} · ${t}: 이미 장터에 있는 제목이에요`);
    seen.add(t);
    // 부품 작품의 요일·어두운 화면·고른 상태 줄도 그때그때 바뀌는 상태 줄이에요(1.36 lint는 role·오늘·공휴일·돈만 봐요).
    const stateOk = w.recipe.pieces.some((p) => p.kind === "parts" && p.parts.some((l) => /^(sat|sun|dark|sel)$/.test(l.on ?? "")));
    problems.push(...lint(w.recipe).filter((x) => !(stateOk && x.startsWith("수식"))).map((p) => `${name} · ${t}: ${p}`));
  }
  writeFileSync(join(OUT, name), JSON.stringify(list, null, 1) + "\n");
  n += list.length;
}
const top = Object.values(files).flat().filter((w) => w.showcase).length;
console.log(`작품 ${n}개(⭐ ${top})를 ${Object.keys(files).length}개 파일에 썼어요 · 재료 ${USED.size}/${ALL_KEYS.length}개를 써요`);
for (const p of problems) console.log(`살필 것: ${p}`);
if (problems.length) process.exitCode = 1;
