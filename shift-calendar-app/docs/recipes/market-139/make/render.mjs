// 1.39 [MKW-02] 그려 볼 작품 고르기: `node docs/recipes/market-139/make/render.mjs [파일 이름 일부] [제목 일부…]`
// → 서버 검사(cleanRecipe)를 거친 작품만 app/build/works-render.json에 써요. 다음은
// `bash ./gradlew :app:testDebugUnitTest --tests '*WorksRenderTest*'` → app/build/screenshots/works/<이름>.png.
import { writeFileSync, mkdirSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import "../../../../server/company-worker/src/index.js";
import { cleanRecipe } from "../../../../server/company-worker/src/recipes.js";

const here = fileURLToPath(new URL(".", import.meta.url));
const [fileHint, ...titles] = process.argv.slice(2);
const files = readdirSync(here).filter((f) => /^(themes|tab|tool|fx)-.*\.mjs$/.test(f) && (!fileHint || fileHint === "all" || f.includes(fileHint)));
const out = [];
for (const f of files) {
  const list = (await import(`./${f}`)).default;
  for (const [i, w] of list.entries()) {
    const r = w.recipe;
    if (titles.length && !titles.some((t) => r.title.includes(t))) continue;
    try {
      await cleanRecipe(JSON.stringify(r));
    } catch (e) {
      console.log(`거절: ${r.title} · ${e.code ?? e.message} ${e.path ?? ""}`);
      continue;
    }
    const name = `${f.replace(/\.mjs$/, "")}_${String(i).padStart(2, "0")}`;
    // 탭별 작품은 그 탭 얼굴 하나만(장터 넘겨 보기도 그 탭부터 보여요).
    const tab = f.startsWith("tab-") ? { calendar: "calendar", pay: "pay", money: "money", memo: "memos", widget: "widget", alarm: "alarm" }[f.split("-")[1]] : null;
    const faces = w.faces ?? tab;
    out.push({ name, recipe: r, ...(faces ? { faces } : {}), ...(w.style ? { style: w.style } : {}) });
  }
}
const dir = fileURLToPath(new URL("../../../../app/build/", import.meta.url));
mkdirSync(dir, { recursive: true });
writeFileSync(dir + "works-render.json", JSON.stringify(out));
console.log(`그려 볼 작품 ${out.length}개: ${out.map((o) => o.name).join(", ")}`);
