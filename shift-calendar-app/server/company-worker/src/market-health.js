import { audit, can, json, one, readJson, run } from "./index.js";
import { DAY } from "./units.js";

// ---- 2.0.0 [RVA-56] 검토 SX-04: 장터 건강 (스튜디오 제작 오류 점검 6-3) ----
//
// 빌드·서버가 바뀔 때마다 CI가 장터 작품 전체를 넣어 찍고(SD-07 tools/market_shots.py) 옛 앱 core로 열어 봐요(tools/old_core_check.py).
// tools/market_health.py가 그 둘을 한 장(market-health.json)으로 묶고, Claude(운영 열쇠)나 관리자가 이 길로 올려요.
//   POST /api/admin/recipes/health  {format: "bamnat-market-health", v: 1, build, at, works, opened, listed, broken[], changed[], unchanged[]}
//   GET  /api/admin/recipes/health   마지막 결과와 지난 결과 몇 개(숫자만)
// 깨진 작품(넘어짐·옛 앱에서 안 열림·두 서버가 거절)은 운영 장부에 '깨진 작품' 줄로 저절로 올라와요. 다음 결과에서 나으면 그 줄은 사라져요.
// 달라진 작품·효과가 사라졌을 수 있는 작품은 장터 › 장터 건강에서 봐요(실패 아님: 일부러 바꾼 화면도 있어요).
// 사람 정보는 없어요: 작품 이름(file#n)·제목·까닭 한 줄·빌드 커밋뿐이에요.

export const MARKET_HEALTH = { max: 400, keepRuns: 10, line: 160, staleDays: 14 };
const HEALTH_KINDS = { error: "넣다 넘어짐", old: "옛 앱에서 안 열림", refused: "서버가 거절", drift: "되돌린 뒤 달력이 다름" };

const healthClean = (v, n = MARKET_HEALTH.line) => String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, n);
/** 장부 줄 번호: file#n → file.n (장부 번호 칸이 받는 글자만). */
export const healthKey = (key) => healthClean(key, 100).replace(/[^A-Za-z0-9._:-]/g, ".");

function readHealthReport(body) {
  if (!body || body.format !== "bamnat-market-health" || body.v !== 1) return null;
  const list = (x, f) => (Array.isArray(x) ? x.slice(0, MARKET_HEALTH.max).map(f).filter((r) => r.key) : []);
  return {
    build: healthClean(body.build, 40), at: Number.isFinite(Date.parse(body.at)) ? Date.parse(body.at) : null,
    works: Math.max(0, Number(body.works) || 0), listed: Math.max(0, Number(body.listed) || 0), opened: Math.max(0, Number(body.opened) || 0),
    broken: list(body.broken, (r) => ({ key: healthClean(r?.key, 100), title: healthClean(r?.title, 80), kind: Object.hasOwn(HEALTH_KINDS, r?.kind) ? r.kind : "error",
      why: healthClean(r?.why), app: Number.isInteger(r?.app) ? r.app : null })),
    changed: list(body.changed, (r) => ({ key: healthClean(r?.key, 100), title: healthClean(r?.title, 80), d: Math.round((Number(r?.d) || 0) * 10) / 10 })).slice(0, 60),
    unchanged: list(body.unchanged, (r) => ({ key: healthClean(r?.key, 100), title: healthClean(r?.title, 80) })).slice(0, 60),
  };
}

async function healthStored(env) {
  const row = await one(env, "SELECT value FROM settings WHERE key = 'market_health'").catch(() => null);
  try {
    const v = row ? JSON.parse(row.value) : null;
    return v && typeof v === "object" ? v : { runs: [] };
  } catch {
    return { runs: [] };
  }
}

/** 마지막 결과(없으면 null)와 지난 결과 숫자. */
export async function marketHealth(env, now = Date.now()) {
  const s = await healthStored(env);
  const last = s.last ?? null;
  return { last, stale: last ? now - last.at > MARKET_HEALTH.staleDays * DAY : true, runs: s.runs ?? [], kinds: HEALTH_KINDS };
}

/** 운영 장부의 '깨진 작품' 줄(장터를 돌보는 사람에게만). */
export async function marketHealthRows(env, actor) {
  if (!can(actor, "community")) return [];
  const last = (await healthStored(env)).last;
  if (!last?.broken?.length) return [];
  return last.broken.map((b) => ({ key: `market_broken:${healthKey(b.key)}`, type: "market_broken", id: healthKey(b.key), kind: b.kind, kindName: "깨진 작품",
    title: `${b.title || b.key} · ${HEALTH_KINDS[b.kind] ?? b.kind}`, text: [b.why, b.app ? `옛 앱 ${b.app}` : "", `빌드 ${last.build}`].filter(Boolean).join(" · "),
    path: null, app: null, at: last.at, last: last.at, same: 0, link: "#/recipes?tab=health", meta: b.key, stage: "intake",
    // 깨진 작품은 다음 출시를 막아요: 결과가 온 뒤 이틀 안에.
    dueAt: last.at + 2 * DAY }));
}

/** GET·POST /recipes/health. 다른 길이면 null. */
export async function marketHealthRoute(request, env, path, method, now) {
  if (path !== "/recipes/health") return null;
  if (method === "GET") return json(await marketHealth(env, now));
  if (method !== "POST") return null;
  if (!can(env.actor, "community", "edit")) return json({ error: "no_permission", area: "community" }, 403);
  const r = readHealthReport(await readJson(request, 512 * 1024));
  if (!r || !r.build) return json({ error: "format", field: "format" }, 400);
  const at = r.at ?? now;
  const s = await healthStored(env);
  const last = { ...r, at, by: env.actor?.name ?? "" };
  const runs = [{ build: r.build, at, works: r.works, broken: r.broken.length, changed: r.changed.length, unchanged: r.unchanged.length }, ...(s.runs ?? [])]
    .slice(0, MARKET_HEALTH.keepRuns);
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('market_health', ?)", JSON.stringify({ last, runs }));
  await audit(env, now, "market_health", r.build, `작품 ${r.works} · 깨짐 ${r.broken.length} · 달라짐 ${r.changed.length}`);
  return json({ ok: true, broken: r.broken.length, changed: r.changed.length }, 201);
}
