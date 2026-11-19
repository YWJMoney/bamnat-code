import { all, audit, json, one, readJson, run } from "./index.js";
import { draftsFor, mergeDraftKey, settleDraft } from "./claude-drafts.js";

// ---- 1.39 [ADM3-45] 비슷한 회사 합치기 제안 (실서버 둘러보기 11-08 B2) ----
//
// 실서버에 'lg전자 칠러공장'·'lg전자 평택칠러공장'·'LG전자(칠러)'가 따로 있었어요. 제보·규칙·참여가 갈라지면 어느 쪽도 사람이 모이지 않아요.
// 이름을 맞춰 보는 것(normName 위에서 '공장·사업장·본사…'를 더 뺌)으로 짝을 찾고, 관리자가 '합치기' 또는 '다른 회사예요'를 고르게 해요.
// 합치기는 회사 화면의 합치기(/companies/:id/merge)와 같고, '다른 회사예요'는 settings에 짝을 남겨 다시 묻지 않아요.

/** 회사 이름에서 장소를 가리키는 말을 더 빼요(normName 결과에). */
const PLACE = /(공장|사업장|사업소|본사|본점|지사|지점|센터|캠퍼스)/gu;
export const simpleName = (norm) => String(norm ?? "").replace(PLACE, "");

/** 같은 회사일 수 있는 이유, 아니면 null. [a]·[b]는 simpleName. */
export function sameish(a, b) {
  if (!a || !b) return null;
  const [s, l] = a.length <= b.length ? [a, b] : [b, a];
  if (s.length < 3) return null;
  if (s === l) return "이름이 같아요";
  if (l.includes(s)) return "한 이름이 다른 이름 안에 있어요";
  let p = 0;
  while (p < s.length && s[p] === l[p]) p++;
  const rest = s.slice(p);
  if (p >= 3 && rest.length >= 2 && l.slice(p).includes(rest)) return "앞이 같고 나머지가 들어 있어요";
  return null;
}

const similarPairKey = (x, y) => [x, y].sort().join("|");

async function skipped(env) {
  try {
    return new Set(JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'company_not_same'"))?.value ?? "[]"));
  } catch {
    return new Set();
  }
}

/** 2.0.0 [RVN-24] 검토 SVC-106: 매시 미리 찾아 두는 짝 수(다른 회사예요로 빠질 것까지 넉넉히). */
const FOUND_MAX = 80;
/** 미리 찾은 짝이 이보다 오래면 화면이 한 번 다시 찾아요(매시 작업이 멈춘 때). */
const FOUND_STALE = 3 * 3600e3;

/**
 * 같은 회사일 수 있는 짝을 찾아요(회사 1,500곳을 서로 견줌, 최대 110만 번). 2.0.0 [RVN-24] 검토 SVC-106: 전에는 화면을 열 때와 '다른 회사예요'를
 * 누를 때마다 했어요. 이제 매시 작업(similar)이 한 번 찾아 settings 'company_similar'에 두고, 화면은 읽기만 해요.
 */
export async function findSimilar(env, now = Date.now()) {
  const rows = await all(env, "SELECT id, norm FROM companies WHERE merged_into IS NULL ORDER BY created_at DESC LIMIT 1500");
  const names = rows.map((r) => simpleName(r.norm));
  const pairs = [];
  for (let i = 0; i < rows.length && pairs.length < FOUND_MAX; i++) {
    for (let j = i + 1; j < rows.length && pairs.length < FOUND_MAX; j++) {
      const why = sameish(names[i], names[j]);
      if (why) pairs.push({ a: rows[i].id, b: rows[j].id, why });
    }
  }
  await run(env, "INSERT INTO settings (key, value) VALUES ('company_similar', ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value", JSON.stringify({ at: now, pairs }));
  return pairs.length;
}

/** 미리 찾은 짝. 그 뒤에 새 회사가 생겼거나 오래됐으면 그때만 다시 찾아요. */
async function found(env, now) {
  try {
    const [row, newest] = await Promise.all([one(env, "SELECT value FROM settings WHERE key = 'company_similar'"),
      one(env, "SELECT MAX(created_at) AS at FROM companies")]);
    const v = JSON.parse(row?.value ?? "null");
    if (v && Array.isArray(v.pairs) && now - (v.at ?? 0) < FOUND_STALE && (newest?.at ?? 0) <= (v.at ?? 0)) return v.pairs;
  } catch {
    // 다시 찾아요
  }
  await findSimilar(env, now);
  return JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'company_similar'"))?.value ?? "{}").pairs ?? [];
}

/** 짝 목록: 제보가 많은 쪽을 남길 쪽(into)으로. 많아야 [limit]쌍. SVC-106: 미리 찾은 짝에서 '다른 회사예요'를 빼고, 남은 회사의 숫자만 한 번에 읽어요. */
export async function similarCompanies(env, limit = 12, now = Date.now()) {
  const skip = await skipped(env);
  const want = (await found(env, now)).filter((p) => !skip.has(similarPairKey(p.a, p.b)));
  const ids = [...new Set(want.flatMap((p) => [p.a, p.b]))];
  const rows = ids.length ? await all(env, `SELECT c.id, c.name,
      (SELECT COUNT(*) FROM reports p WHERE p.company_id = c.id) AS reports,
      (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS rules,
      (SELECT COUNT(*) FROM members m WHERE m.company_id = c.id AND m.left = 0) AS members
    FROM companies c WHERE c.merged_into IS NULL AND c.id IN (${ids.map(() => "?").join(",")})`, ...ids) : [];
  const by = new Map(rows.map((r) => [r.id, r]));
  const card = (c) => ({ id: c.id, name: c.name, reports: c.reports, rules: c.rules, members: c.members });
  const pairs = [];
  for (const p of want) {
    const x = by.get(p.a);
    const y = by.get(p.b);
    // 그사이 합쳐진 회사는 빠져요.
    if (!x || !y) continue;
    const [keep, from] = x.reports + x.members >= y.reports + y.members ? [x, y] : [y, x];
    pairs.push({ into: card(keep), from: card(from), why: p.why });
    if (pairs.length >= limit) break;
  }
  // 1.39 [ADM3-49] 짝마다 Claude 초안(합칠까요?)이 있으면 함께. 어느 쪽으로 적었든 찾아요.
  const keys = pairs.flatMap((p) => [mergeDraftKey(p.from.id, p.into.id), mergeDraftKey(p.into.id, p.from.id)]);
  const drafts = await draftsFor(env, keys);
  for (const p of pairs) {
    const d = drafts.get(mergeDraftKey(p.from.id, p.into.id)) ?? drafts.get(mergeDraftKey(p.into.id, p.from.id));
    if (d) p.draft = d;
  }
  return { pairs };
}

/** '다른 회사예요': 이 짝은 다시 묻지 않아요(최근 500쌍까지). */
export async function skipSimilar(request, env, now) {
  const body = await readJson(request);
  const a = String(body?.a ?? "");
  const b = String(body?.b ?? "");
  if (!/^[A-Za-z0-9]{12}$/.test(a) || !/^[A-Za-z0-9]{12}$/.test(b) || a === b) return json({ error: "bad_pair" }, 400);
  const list = [...(await skipped(env))].filter((k) => k !== similarPairKey(a, b));
  list.push(similarPairKey(a, b));
  await run(env, "INSERT INTO settings (key, value) VALUES ('company_not_same', ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value",
    JSON.stringify(list.slice(-500)));
  await audit(env, now, "company_not_same", `${a}|${b}`).catch(() => {});
  await settleDraft(env, mergeDraftKey(a, b), "skip", now);
  await settleDraft(env, mergeDraftKey(b, a), "skip", now);
  return json(await similarCompanies(env));
}
