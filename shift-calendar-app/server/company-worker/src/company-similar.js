import { all, audit, json, one, readJson, run } from "./index.js";

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

/** 짝 목록: 제보가 많은 쪽을 남길 쪽(into)으로. 많아야 [limit]쌍. */
export async function similarCompanies(env, limit = 12) {
  const rows = await all(env, `SELECT c.id, c.name, c.norm,
      (SELECT COUNT(*) FROM reports p WHERE p.company_id = c.id) AS reports,
      (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS rules,
      (SELECT COUNT(*) FROM members m WHERE m.company_id = c.id AND m.left = 0) AS members
    FROM companies c WHERE c.merged_into IS NULL ORDER BY c.created_at DESC LIMIT 1500`);
  const skip = await skipped(env);
  const names = rows.map((r) => simpleName(r.norm));
  const pairs = [];
  for (let i = 0; i < rows.length && pairs.length < limit; i++) {
    for (let j = i + 1; j < rows.length && pairs.length < limit; j++) {
      const why = sameish(names[i], names[j]);
      if (!why || skip.has(similarPairKey(rows[i].id, rows[j].id))) continue;
      const [keep, from] = rows[i].reports + rows[i].members >= rows[j].reports + rows[j].members ? [rows[i], rows[j]] : [rows[j], rows[i]];
      const card = (c) => ({ id: c.id, name: c.name, reports: c.reports, rules: c.rules, members: c.members });
      pairs.push({ into: card(keep), from: card(from), why });
    }
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
  return json(await similarCompanies(env));
}
