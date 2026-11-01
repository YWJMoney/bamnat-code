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
import { join } from "node:path";
import { cleanRecipe, tabsOf, scopeOf, RecipeError } from "../src/recipes.js";
import * as recipes from "../src/recipes.js";

const ROOT = new URL("../../../docs/recipes/", import.meta.url).pathname;
const DIR = join(ROOT, "market-136");
const OUT = join(ROOT, "market-136.json");
export const BATCH = "1.36 출시 작품";
/** 1.36's versionCode (gradle.properties bamnat.versionCode): older apps never see these. */
export const MIN_APP = 49;
export const GOALS = { theme: 48, "tab:calendar": 20, "tab:pay": 20, "tab:money": 20, "tab:memo": 20, "tab:alarm": 20, "tab:widget": 20, part: 20, logic: 20 };

/** The 장터 구간 a written recipe falls in: the server's own sectionOf when it has one (갈래 F), else 테마 / 기능별 탭. */
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

export async function build(works) {
  const refused = [];
  const entries = [];
  const count = Object.fromEntries(Object.keys(GOALS).map((k) => [k, 0]));
  const names = new Set();
  for (const w of works) {
    try {
      const c = await cleanRecipe(w.recipe);
      if (names.has(c.recipe.title)) throw new RecipeError("same_title", "title");
      names.add(c.recipe.title);
      const key = sectionKey(c.text);
      count[key] = (count[key] ?? 0) + 1;
      entries.push({ recipe: JSON.parse(c.text), hold: true, replace: true, batch: BATCH, minApp: MIN_APP, ...(w.showcase ? { showcase: true } : {}) });
    } catch (e) {
      if (!(e instanceof RecipeError)) throw e;
      refused.push(`${w.file} · ${w.recipe?.title ?? "?"}: ${e.code} ${e.path}`);
    }
  }
  const short = Object.entries(GOALS).filter(([k, n]) => (count[k] ?? 0) < n).map(([k, n]) => `${k} ${count[k] ?? 0}/${n}`);
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

if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2);
  const { entries, refused, count, short } = await build(readWorks());
  console.log(`작품 ${entries.length}개 · 구간 ${Object.entries(count).map(([k, n]) => `${k} ${n}`).join(", ")}`);
  for (const r of refused) console.log(`거절: ${r}`);
  if (short.length) console.log(`목표 모자람: ${short.join(", ")}`);
  if (!args.includes("--check")) {
    writeFileSync(OUT, JSON.stringify(entries, null, 1) + "\n");
    console.log(`썼어요: ${OUT}`);
  }
  const at = args.indexOf("--post");
  if (at >= 0) console.log(`로컬 서버 출시 대기에 ${await post(args[at + 1], entries)}개 넣었어요`);
  if (refused.length || (args.includes("--check") && short.length)) process.exit(1);
}
