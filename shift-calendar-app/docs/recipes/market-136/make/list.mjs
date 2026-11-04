// 작품 목록 문서: `node docs/recipes/market-136/make/list.mjs [나갈 파일]` → 기본은 docs/recipes/market-136/목록.md.
// docs/recipes/market-136.json(market-seed가 쓴 묶음)을 읽어 장터 구간별로 제목·유형·한 줄 소개를 적어요.
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import "../../../../server/company-worker/src/index.js";
import { sectionOf } from "../../../../server/company-worker/src/recipes.js";

const W = JSON.parse(readFileSync(new URL("../../market-136.json", import.meta.url), "utf8"));
const OUT = process.argv[2] ?? fileURLToPath(new URL("../목록.md", import.meta.url));
const SW = { SHIFT: "교대", DAY: "일근", HOURLY: "알바", FIELD: "현장", BUSINESS: "자영업", INVEST: "투자" };
const NAME = { theme: "테마(종합)", "tab:calendar": "기능별 · 달력", "tab:pay": "기능별 · 근무·급여", "tab:money": "기능별 · 가계부", "tab:memo": "기능별 · 메모",
  "tab:alarm": "기능별 · 알람", "tab:widget": "기능별 · 위젯", part: "부품", logic: "규칙·단추" };

const groups = {};
let after = 0;
for (const w of W) {
  const r = w.recipe;
  const s = sectionOf(r);
  const k = s.scope === "tab" ? `tab:${s.tabs[0]}` : s.scope;
  const fix = (r.inputs ?? []).length > 0;
  if (fix) after++;
  const who = r.styles.length === 6 ? "모두" : r.styles.map((x) => SW[x]).join("·");
  (groups[k] ??= []).push(`| ${w.showcase ? "⭐ " : ""}${r.title} | ${who} | ${r.summary}${fix ? " ※" : ""} |`);
}
let md = `# 1.36 장터 출시 작품 목록 (${W.length}개)\n\n`;
md += "⭐ = 보여주기(유형마다 5, 모두 30). ※ = 넣을 때 받는 사람이 값(시급·일당·예산·목표 등)을 정하는 작품";
md += ` (${after}개). 만드는 법은 make/README.md.\n\n`;
for (const k of Object.keys(NAME)) {
  const g = groups[k] ?? [];
  md += `## ${NAME[k]} (${g.length})\n\n| 작품 | 유형 | 한 줄 소개 |\n|---|---|---|\n${g.join("\n")}\n\n`;
}
writeFileSync(OUT, md);
console.log(`목록 ${W.length}개 (받는 칸 ${after}개) → ${OUT}`);
