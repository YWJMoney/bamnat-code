// 2.0.0 [RVR-21] (검토 SD-07, 스튜디오 제작 오류 점검 5장 S6-4) 판 호환 시험의 서버 쪽: 장터 작품 목록(tools/market_works.py가 만든
// app/build/market-works.json)의 작품마다, 서버가 올릴 때처럼 읽고(cleanRecipe), 그 작품이 나오는 가장 옛 앱(올릴 때 minApp과
// 작품이 쓴 부품·기능 장치의 판 minAppOf 중 큰 것, 장터 목록의 min_app)과 앱 판마다 실제로 받는 몸통(servedOut: 회사 급여 빼기, 2.0.0 전
// 앱에는 안전한 달 넘김)과 hash를 적어요. 옛 core가 그 몸통을 읽는 것은 tools/old_core_check.py가 해요.
//
//   node tools/served-works.mjs <market-works.json> <out.json> 50,51,52,53
//
// 2.0.0 전 앱의 '작품 열기'는 app=을 보내지 않아요(recipes.js turnSafeOut 주석): 그 앱이 여는 몸통은 app=0으로 만든 것이에요.
// 목록(min_app)은 그 앱의 versionCode로 걸러요.
import { readFileSync, writeFileSync } from "node:fs";
// index.js first: recipes.js → photo-guard.js → index.js is a cycle that only loads cleanly from this end (market-seed.mjs).
import "../src/index.js";
import { cleanRecipe, minAppOf, servedOut, RecipeError, TURN_SAFE_APP } from "../src/recipes.js";

export async function served(works, apps) {
  const out = [];
  for (const w of works) {
    let clean;
    try {
      clean = await cleanRecipe(w.recipe);
    } catch (e) {
      if (!(e instanceof RecipeError)) throw e;
      out.push({ key: w.key, refused: `${e.code}${e.path ? ` (${e.path})` : ""}` });
      continue;
    }
    const parts = minAppOf(clean.text);
    const need = Math.max(w.minApp ?? 0, parts ?? 0) || null;
    const byApp = {};
    for (const app of apps) {
      // What the phone opens: before TURN_SAFE_APP its 작품 열기 sends no app=.
      const s = await servedOut(clean.text, clean.hash, app >= TURN_SAFE_APP ? app : 0);
      byApp[app] = { listed: need === null || need <= app, ...(s.body === clean.text ? {} : { body: s.body }), hash: s.hash };
    }
    out.push({ key: w.key, minApp: need, partsMinApp: parts, text: clean.text, hash: clean.hash, apps: byApp });
  }
  return out;
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith("served-works.mjs")) {
  const [inPath, outPath, appsArg] = process.argv.slice(2);
  if (!inPath || !outPath || !appsArg) {
    console.error("node tools/served-works.mjs <market-works.json> <out.json> 50,51,52,53");
    process.exit(2);
  }
  const apps = appsArg.split(",").map(Number).filter((n) => Number.isInteger(n) && n > 0);
  const { works } = JSON.parse(readFileSync(inPath, "utf8"));
  const res = await served(works, apps);
  writeFileSync(outPath, JSON.stringify({ apps, works: res }));
  const refused = res.filter((r) => r.refused).length;
  console.log(`서버 쪽: 작품 ${res.length}개, 서버가 거절 ${refused}개, 판 ${apps.join("·")}`);
}
