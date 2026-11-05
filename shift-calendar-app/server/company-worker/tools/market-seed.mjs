// 1.36 [MKS-02] 장터 출시 작품 묶음 (docs/plans/1.36-장터-출시작품-기획.md): reads every work under docs/recipes/market-136/,
// checks each as the server will (cleanRecipe), counts the works in each 장터 구간 against the goal (20+ each, 테마 48+),
// and writes docs/recipes/market-136.json — the file the 관리자 앱's 출시 대기 takes whole, each entry held for its batch.
//
//   node tools/market-seed.mjs                      check and write the file
//   node tools/market-seed.mjs --check              check only (exit 1 on a refused work or a section short of its goal)
//   node tools/market-seed.mjs --post http://127.0.0.1:8788   also put them in a LOCAL server's 출시 대기 (ADMIN_PASSWORD from the env)
//
// Works are JSON files: one recipe, or an array of recipes / {recipe, showcase?} entries. Never posts to the real server: that is the
// operator's paste in the 관리자 앱, after the user's last say-so (CLAUDE.md 3-1).
import { readFileSync, readdirSync, writeFileSync, existsSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
// index.js first: recipes.js → photo-guard.js → index.js is a cycle that only loads cleanly from this end (as the tests do).
import "../src/index.js";
import { cleanRecipe, tabsOf, scopeOf, RecipeError } from "../src/recipes.js";
import * as recipes from "../src/recipes.js";

// fileURLToPath, not .pathname: on Windows the latter is "/C:/…" and URL-escaped (PC 확인 2026-11-05).
const ROOT = fileURLToPath(new URL("../../../docs/recipes/", import.meta.url));
const DIR = join(ROOT, "market-136");
const OUT = join(ROOT, "market-136.json");
export const BATCH = "1.36 출시 작품";
/** 1.36's versionCode (gradle.properties bamnat.versionCode): older apps never see these. */
export const MIN_APP = 49;
// 테마 68 = 유형별 48 + 앱 전체 20. '앱 전체'는 기능별 탭이 아니라 테마예요: 서버 scopeOf가 app이 든 작품을 theme로 보내고, 앱 장터의
// 기능별 칩에도 앱 전체가 없어요 (메인 스레드 결정 2026-11-03).
export const GOALS = { theme: 68, "tab:calendar": 20, "tab:pay": 20, "tab:money": 20, "tab:memo": 20, "tab:alarm": 20, "tab:widget": 20, part: 20, logic: 20 };

/** The 장터 구간 a written recipe falls in: the server's own sectionOf when it has one (갈래 F), else 테마 / 기능별 탭. */
/**
 * 1.38 [LEDH-18] 판마다 따로 내는 작품 묶음: `--set 138` reads docs/recipes/market-138/ (장터 첫 가계부 틀 넷) and holds them for
 * 1.38 apps (versionCode 52), as their own batch to release with 1.38.
 */
export const SETS = {
  136: { dir: DIR, out: OUT, batch: BATCH, minApp: MIN_APP, goals: GOALS },
  138: { dir: join(ROOT, "market-138"), out: join(ROOT, "market-138.json"), batch: "1.38 가계부 틀", minApp: 52, goals: { "tab:money": 4 } },
  // 1.38 [STM-07] 스튜디오 재료(밤낮 색 아이콘·배경)로 만든 테마 100·기능별 300 (docs/recipes/market-138-studio/make).
  "138s": { dir: join(ROOT, "market-138-studio"), out: join(ROOT, "market-138-studio.json"), batch: "1.38 스튜디오 작품", minApp: 52,
    goals: { theme: 100, "tab:calendar": 50, "tab:memo": 50, "tab:money": 50, "tab:pay": 40, "tab:widget": 35, "tab:alarm": 25, part: 30, logic: 20 } },
  // 1.38 [STM-09] 1.36 '다듬기' 작품에 밤낮 배경·아이콘을 얹은 같은 제목 새 판(make/refresh.mjs). 1.36 앱도 읽으니 minApp 없이,
  // 이미 장터에 있는 판은 넣는 즉시 바뀌어요: 1.38 출시 날 사용자 확인 뒤 넣어요.
  "138r": { dir: join(ROOT, "market-138-refresh"), out: join(ROOT, "market-138-refresh.json"), batch: "1.38 옛 작품 새 판", minApp: null,
    goals: { theme: 49, "tab:calendar": 22, "tab:memo": 11, "tab:money": 1, "tab:pay": 22, "tab:widget": 2 } },
};

export function sectionKey(text) {
  if (typeof recipes.sectionOf === "function") {
    const s = recipes.sectionOf(text);
    if (s?.scope === "tab") return `tab:${s.tabs[0]}`;
    if (s?.scope) return s.scope;
    return "none";
  }
  const tabs = tabsOf(text);
  const scope = scopeOf(tabs);
  return scope === "tab" ? `tab:${tabs[0]}` : scope ?? "none";
}

export function readWorks(dir = DIR) {
  if (!existsSync(dir)) return [];
  const out = [];
  for (const f of readdirSync(dir).filter((x) => x.endsWith(".json")).sort()) {
    const v = JSON.parse(readFileSync(join(dir, f), "utf8"));
    for (const x of Array.isArray(v) ? v : [v]) out.push({ file: f, ...(x.recipe ? x : { recipe: x }) });
  }
  return out;
}

export async function build(works, set = SETS[136]) {
  const refused = [];
  const entries = [];
  const count = Object.fromEntries(Object.keys(set.goals).map((k) => [k, 0]));
  const names = new Set();
  for (const w of works) {
    try {
      const c = await cleanRecipe(w.recipe);
      if (names.has(c.recipe.title)) throw new RecipeError("same_title", "title");
      names.add(c.recipe.title);
      const key = sectionKey(c.text);
      count[key] = (count[key] ?? 0) + 1;
      entries.push({ recipe: JSON.parse(c.text), hold: true, replace: true, batch: set.batch, ...(set.minApp ? { minApp: set.minApp } : {}), ...(w.showcase ? { showcase: true } : {}) });
    } catch (e) {
      if (!(e instanceof RecipeError)) throw e;
      refused.push(`${w.file} · ${w.recipe?.title ?? "?"}: ${e.code} ${e.path}`);
    }
  }
  const short = Object.entries(set.goals).filter(([k, n]) => (count[k] ?? 0) < n).map(([k, n]) => `${k} ${count[k] ?? 0}/${n}`);
  return { entries, refused, count, short };
}

async function post(base, entries) {
  const pw = process.env.ADMIN_PASSWORD;
  if (!pw) throw new Error("ADMIN_PASSWORD 가 없어요");
  if (!/^http:\/\/(127\.0\.0\.1|localhost|10\.0\.2\.2)(:\d+)?$/.test(base)) throw new Error("로컬 서버에만 넣어요");
  const login = await fetch(`${base}/api/admin/login`, { method: "POST", headers: { "content-type": "application/json", "x-admin": "1", origin: base },
    body: JSON.stringify({ password: pw }) });
  if (login.status !== 200) throw new Error(`login ${login.status}`);
  const cookie = login.headers.get("set-cookie").split(";")[0];
  let ok = 0;
  for (const e of entries) {
    const r = await fetch(`${base}/api/admin/recipes`, { method: "POST", headers: { "content-type": "application/json", "x-admin": "1", origin: base, cookie },
      body: JSON.stringify(e) });
    if (r.status < 300) ok++;
    else console.log(`못 넣음 ${e.recipe.title}: ${r.status} ${await r.text()}`);
  }
  return ok;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const args = process.argv.slice(2);
  const set = SETS[args.includes("--set") ? args[args.indexOf("--set") + 1] : 136];
  if (!set) throw new Error("--set 은 136, 138, 138s, 138r 중 하나예요");
  const { entries, refused, count, short } = await build(readWorks(set.dir), set);
  console.log(`작품 ${entries.length}개 · 구간 ${Object.entries(count).map(([k, n]) => `${k} ${n}`).join(", ")}`);
  for (const r of refused) console.log(`거절: ${r}`);
  if (short.length) console.log(`목표 모자람: ${short.join(", ")}`);
  if (!args.includes("--check")) {
    writeFileSync(set.out, JSON.stringify(entries, null, 1) + "\n");
    console.log(`썼어요: ${set.out}`);
  }
  const at = args.indexOf("--post");
  if (at >= 0) console.log(`로컬 서버 출시 대기에 ${await post(args[at + 1], entries)}개 넣었어요`);
  if (refused.length || (args.includes("--check") && short.length)) process.exit(1);
}
