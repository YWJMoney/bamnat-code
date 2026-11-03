// 1.36 장터 출시 작품 생성기: `node docs/recipes/market-136/make/index.mjs` → docs/recipes/market-136/*.json (구간별 파일).
// 다음은 `cd server/company-worker && node tools/market-seed.mjs --check`로 서버가 받는지와 구간 목표를 봐요.
// 작품 하나를 고치려면 여기 make/의 그 작품 줄을 고치고 다시 돌려요(JSON을 손으로 고치면 다음 생성 때 사라져요).
import { writeFileSync, readdirSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { lint } from "./lint.mjs";

const OUT = fileURLToPath(new URL("..", import.meta.url));
const files = {};
const add = (name, list) => { files[name] = list; };

const { SHIFT, DAY } = await import("./themes-shift-day.mjs");
add("theme-shift.json", SHIFT);
add("theme-day.json", DAY);
for (const [file, mod] of [["theme-hourly-field.json", "./themes-hourly-field.mjs"], ["theme-business-invest.json", "./themes-business-invest.mjs"],
  ["theme-app.json", "./themes-app.mjs"], ["tab-calendar.json", "./tab-calendar.mjs"], ["tab-memo.json", "./tab-memo.mjs"], ["tab-money.json", "./tab-money.mjs"],
  ["tab-pay.json", "./tab-pay.mjs"], ["tab-alarm.json", "./tab-alarm.mjs"], ["tab-widget.json", "./tab-widget.mjs"], ["part.json", "./parts.mjs"], ["logic.json", "./logic.mjs"]]) {
  try {
    add(file, (await import(mod)).default);
  } catch (e) {
    if (e.code !== "ERR_MODULE_NOT_FOUND") throw e;
  }
}

// 보여주기 30: 고친 1.35 작품 12(theme-showcase-plus.json) + 새 작품 18(SHOWCASE 제목). 다른 작품의 showcase 표시는 지워요.
const { PLUS, SHOWCASE } = await import("./showcase.mjs");
add("theme-showcase-plus.json", PLUS);
const missing = new Set(SHOWCASE);
for (const [name, list] of Object.entries(files)) {
  if (name === "theme-showcase-plus.json") continue;
  files[name] = list.map((w) => {
    const { showcase, ...rest } = w;
    if (!SHOWCASE.includes(w.recipe.title)) return rest;
    missing.delete(w.recipe.title);
    return { showcase: true, ...rest };
  });
}
if (missing.size) throw new Error(`보여주기 제목이 없어요: ${[...missing].join(", ")}`);

for (const f of readdirSync(OUT).filter((x) => x.endsWith(".json"))) unlinkSync(join(OUT, f));
let n = 0;
const problems = [];
for (const [name, list] of Object.entries(files)) {
  for (const w of list) problems.push(...lint(w.recipe).map((p) => `${name} · ${w.recipe.title}: ${p}`));
  writeFileSync(join(OUT, name), JSON.stringify(list, null, 1) + "\n");
  n += list.length;
}
console.log(`작품 ${n}개를 ${Object.keys(files).length}개 파일에 썼어요`);
for (const p of problems) console.log(`살필 것: ${p}`);
if (problems.length) process.exitCode = 1;
