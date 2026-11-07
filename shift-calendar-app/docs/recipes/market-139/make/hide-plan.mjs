// 1.39 [MKW-06] 숨김 목록: 1.36·1.38 장터 작품 가운데 curate.mjs OLD_KEEP에 없는 것을 docs/recipes/market-139-refresh-plan.json에 적어요.
// 서버의 POST /api/admin/recipes/hide-titles({titles, dry, note})가 받는 모양({"hide":[{title, why}]})이고, 지우지 않고 숨겨요:
// 이미 받아 쓰는 사람의 작품은 그대로, 장터 목록·검색에서만 빠져요. 실서버는 1.39 출시 날 사용자 확인 뒤 관리자 앱에서.
//
//   node docs/recipes/market-139/make/hide-plan.mjs
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import "../../../../server/company-worker/src/index.js";
import { sectionOf } from "../../../../server/company-worker/src/recipes.js";
import { OLD_KEEP } from "./curate.mjs";

const R = fileURLToPath(new URL("../../", import.meta.url));
const OUT = R + "market-139-refresh-plan.json";
const WHY = {
  theme: "1.39 정상급 테마와 겹쳐요(달력 얼굴만 다르고 가계부·메모가 기본에 색만 입힌 판)",
  "tab:calendar": "기본 칸에 배경 한 장과 수식 하나라 1.39 달력 작품이 넘어서요",
  "tab:memo": "기본 메모 카드에 색과 틀만 바꾼 판이라 1.39 메모 작품(판·조건 테)이 넘어서요",
  "tab:money": "1.39 가계부 작품과 겹쳐요",
  "tab:pay": "1.39 급여 작품과 겹쳐요(큰 합계와 줄 구조가 같고 색만 다름)",
  "tab:widget": "1.39 위젯 작품과 겹쳐요",
  "tab:alarm": "1.39 알람 작품과 겹쳐요",
  part: "같은 특징의 부품이 하나 더 있어요(한 특징에 하나만 남김)",
  logic: "같은 일을 하는 규칙·단추가 하나 더 있어요(한 특징에 하나만 남김)",
};

// 1.38 정리에서 이미 숨긴 것은 빼고, 같은 제목은 뒤 파일(새 판)이 이겨요.
const done = new Set(existsSync(R + "market-138-refresh-plan.json") ? JSON.parse(readFileSync(R + "market-138-refresh-plan.json", "utf8")).hide.map((x) => x.title) : []);
const fresh = new Set(JSON.parse(readFileSync(R + "market-139.json", "utf8")).map((e) => e.recipe.title));
const seen = new Map();
for (const f of ["market-136.json", "market-138.json", "market-138-studio.json", "market-138-refresh.json"]) {
  for (const e of JSON.parse(readFileSync(R + f, "utf8"))) {
    const r = e.recipe;
    if (done.has(r.title) || fresh.has(r.title)) continue;
    const s = sectionOf(r);
    seen.set(r.title, { title: r.title, sec: s.scope === "tab" ? `tab:${s.tabs[0]}` : s.scope, from: f.replace(/^market-|\.json$/g, "") });
  }
}
const missing = Object.keys(WHY).filter((k) => !OLD_KEEP[k]);
if (missing.length) throw new Error(`curate.mjs OLD_KEEP에 아직 고르지 않은 구간: ${missing.join(", ")}`);
const keep = new Set(Object.values(OLD_KEEP).flat());
const unknown = [...keep].filter((t) => !seen.has(t));
if (unknown.length) throw new Error(`OLD_KEEP에 장터에 없는 제목: ${unknown.join(", ")}`);
const hide = [...seen.values()].filter((w) => !keep.has(w.title)).map((w) => ({ title: w.title, why: WHY[w.sec] ?? "1.39 작품과 겹쳐요", section: w.sec, from: w.from }));
writeFileSync(OUT, JSON.stringify({ note: "1.39 장터 고르기(MKW-06)", hide }, null, 1) + "\n");
const by = {};
for (const w of seen.values()) { by[w.sec] ??= [0, 0]; by[w.sec][keep.has(w.title) ? 0 : 1]++; }
console.log(`옛 작품 ${seen.size}개 · 남김 ${keep.size} · 숨김 ${hide.length}`);
for (const [k, [a, b]] of Object.entries(by)) console.log(`  ${k}: 남김 ${a}, 숨김 ${b}`);
console.log(`썼어요: ${OUT}`);
