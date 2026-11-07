// 1.39 [MKW-05] 정상급 출시 작품 묶음: `node docs/recipes/market-139/make/index.mjs` → docs/recipes/market-139/*.json.
// 다음은 `cd server/company-worker && node tools/market-seed.mjs --set 139` → docs/recipes/market-139.json(시험 서버 --market이 읽어요).
// 실서버에 넣는 일은 1.39 출시 날 사용자 확인 뒤(release/1.39.0/사용자-할-일/작품제작.md).
import { readFileSync, writeFileSync, readdirSync, unlinkSync, existsSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { lint } from "../../market-136/make/lint.mjs";
import { KEEP } from "./curate.mjs";

const HERE = fileURLToPath(new URL(".", import.meta.url));
const OUT = fileURLToPath(new URL("..", import.meta.url));
const RECIPES = fileURLToPath(new URL("../..", import.meta.url));

// 파일 → 묶음 파일 이름 (테마는 여러 파일을 한 theme.json으로).
const OUT_OF = (f) => (f.startsWith("themes-") ? "theme.json" : f.startsWith("tab-calendar") ? "tab-calendar.json"
  : f.startsWith("tab-") ? f.replace(/\.mjs$/, ".json") : f === "tool-parts.mjs" ? "part.json" : f.startsWith("tool-") ? "logic.json"
  : f.startsWith("fx-") ? "fx.json" : null);
const files = {};
for (const f of readdirSync(HERE).filter((x) => /^(themes|tab|tool|fx)-.*\.mjs$/.test(x)).sort()) {
  const out = OUT_OF(f);
  if (!out) continue;
  (files[out] ??= []).push(...(await import(`./${f}`)).default.map((w) => ({ ...w, from: f })));
}

// [MKW-06] 고르기: curate.mjs KEEP에 있는 것만 묶음에 넣어요(구간이 없으면 모두).
let cut = 0;
for (const [name, list] of Object.entries(files)) {
  const keep = KEEP[name.replace(/\.json$/, "")];
  if (!keep) continue;
  const set = new Set(keep);
  const missing = keep.filter((t) => !list.some((w) => w.recipe.title === t));
  if (missing.length) throw new Error(`curate.mjs ${name}에 없는 제목: ${missing.join(", ")}`);
  files[name] = list.filter((w) => set.has(w.recipe.title));
  cut += list.length - files[name].length;
}

// 이미 장터에 있는 제목은 쓰지 않아요(같은 제목은 그 작품의 새 판이 돼요).
const taken = new Map();
for (const f of readdirSync(RECIPES).filter((x) => x.endsWith(".json") && x !== "market-139.json")) {
  const v = JSON.parse(readFileSync(join(RECIPES, f), "utf8"));
  for (const e of Array.isArray(v) ? v : v.items ?? []) { const r = e.recipe ?? e; if (r?.title) taken.set(r.title, f); }
}
for (const d of ["market-136", "market-138", "market-138-studio", "market-138-refresh"]) {
  if (!existsSync(join(RECIPES, d))) continue;
  for (const f of readdirSync(join(RECIPES, d)).filter((x) => x.endsWith(".json"))) {
    const v = JSON.parse(readFileSync(join(RECIPES, d, f), "utf8"));
    for (const e of Array.isArray(v) ? v : [v]) { const r = e.recipe ?? e; if (r?.title) taken.set(r.title, `${d}/${f}`); }
  }
}

const problems = [];
const seen = new Set();
// 이 도구가 쓰는 파일만 지우고 다시 써요(다른 갈래가 같은 폴더에 둔 night.json 같은 것은 그대로).
const MINE = /^(theme|tab-(calendar|pay|money|memo|alarm|widget)|part|logic|fx)\.json$/;
for (const f of readdirSync(OUT).filter((x) => MINE.test(x))) unlinkSync(join(OUT, f));
let n = 0;
for (const [name, list] of Object.entries(files)) {
  for (const w of list) {
    const t = w.recipe.title;
    if (seen.has(t)) problems.push(`${w.from} · ${t}: 제목이 겹쳐요`);
    if (taken.has(t)) problems.push(`${w.from} · ${t}: 이미 장터에 있는 제목이에요(${taken.get(t)})`);
    seen.add(t);
    const stateOk = w.recipe.pieces.some((p) => p.kind === "parts" && p.parts.some((l) => /^(sat|sun|dark|sel)$/.test(l.on ?? "")));
    problems.push(...lint(w.recipe).filter((x) => !(stateOk && x.startsWith("수식"))).map((p) => `${w.from} · ${t}: ${p}`));
    if (w.problems?.length && w.from.startsWith("themes-")) problems.push(`${w.from} · ${t}: 글자 대비 ${w.problems.join(", ")}`);
  }
  // 추천(⭐)은 구간마다 6개 안팎(장터 갈래 권고, 테마는 8): 고른 것이 많으면 유형이 고르게 남도록 띄엄띄엄 남겨요.
  const tops = list.filter((w) => w.showcase);
  const cap = name === "theme.json" ? 8 : 6;
  const keep = new Set(tops.length <= cap ? tops : Array.from({ length: cap }, (_, i) => tops[Math.floor((i * tops.length) / cap)]));
  // 장터 넣기에 쓰는 것만(recipe·showcase). 분위기(mood)는 장터가 look 색으로 계산해요.
  writeFileSync(join(OUT, name), JSON.stringify(list.map((w) => ({ recipe: w.recipe, ...(keep.has(w) ? { showcase: true } : {}) })), null, 1) + "\n");
  n += list.length;
}
const top = readdirSync(OUT).filter((x) => MINE.test(x)).reduce((a, f) => a + JSON.parse(readFileSync(join(OUT, f), "utf8")).filter((w) => w.showcase).length, 0);
console.log(`고르기로 뺀 것 ${cut}개. 작품 ${n}개(⭐ ${top})를 ${Object.keys(files).length}개 파일에 썼어요: ${Object.entries(files).map(([k, v]) => `${k} ${v.length}`).join(", ")}`);
for (const p of problems) console.log(`살필 것: ${p}`);
if (problems.length) process.exitCode = 1;
