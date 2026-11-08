import { all, audit, can, iso, json, one, readJson, refuse, run } from "./index.js";
import { actorName, plainText } from "./inquiries.js";

// ---- 1.39 [ADM3-49] Claude 초안: 제보 검토·보상·회사 합치기 (실서버 둘러보기 B1·C2, 사용자 11-08 '클로드 열쇠로 제보 검토 보상 초안까지') ----
//
// 운영 열쇠(Claude)는 검토함·회사·보상을 읽고, 이 길(/drafts)로 '추천'만 적어요. 채택·거절·되돌리기, 보상 지급, 회사 합치기는
// 열쇠로 막혀 있고(desk.js KEY_POST) 관리자 앱에서 사람이 추천을 보고 버튼을 한 번 눌러야 일어나요. 사람이 정하면 추천과 같았는지
// 활동 기록 'claude_draft_done'에 남겨, 조종석의 일치율(넓히기 관문: 20건 이상·90% 이상이면 사용자에게 물음)이 그것을 읽어요.
// 초안은 자동 순환의 loop_drafts 표를 같이 써요(key 앞머리로 갈라요: group: · reward: · merge:). 표 모양은 그대로예요.

export const DRAFT_GATE = { least: 20, agree: 90 };

const KINDS = {
  group: { area: "review", re: /^group:[A-Za-z0-9]{12}:[0-9a-f]{16}$/, picks: ["adopt", "reject"] },
  reward: { area: "rewards", re: /^reward:w[A-Za-z0-9]{22}$/, picks: ["paid", "void"] },
  merge: { area: "companies", re: /^merge:[A-Za-z0-9]{12}:[A-Za-z0-9]{12}$/, picks: ["merge", "skip"] },
};
const REJECT_REASONS = ["different", "unclear", "wrong", "duplicate", "inappropriate"];

export const groupDraftKey = (company, grp) => `group:${company}:${grp}`;
export const rewardDraftKey = (id) => `reward:${id}`;
export const mergeDraftKey = (from, into) => `merge:${from}:${into}`;

function keyOf(body) {
  const kind = Object.hasOwn(KINDS, body?.kind) ? body.kind : refuse("kind", "kind");
  const key = kind === "group" ? groupDraftKey(body.company, body.grp) : kind === "reward" ? rewardDraftKey(body.id) : mergeDraftKey(body.from, body.into);
  return KINDS[kind].re.test(key) ? { kind, key } : refuse("target", "target");
}

/** 공개 자료 주소: https만, 다섯 개까지. */
function sourcesOf(v) {
  if (v === undefined || v === null) return [];
  if (!Array.isArray(v) || v.length > 5) refuse("sources", "sources");
  return v.map((u) => (typeof u === "string" && u.length <= 300 && /^https:\/\/[^\s<>"]+$/.test(u) ? u : refuse("sources", "sources")));
}

async function targetExists(env, kind, body) {
  if (kind === "group") return one(env, "SELECT 1 AS ok FROM reports WHERE company_id = ? AND grp = ? AND status = 'pending' LIMIT 1", body.company, body.grp);
  if (kind === "reward") return one(env, "SELECT 1 AS ok FROM rewards WHERE id = ? AND status = 'owed'", body.id);
  if (body.from === body.into) return null;
  const n = await one(env, "SELECT COUNT(*) AS n FROM companies WHERE id IN (?, ?) AND merged_into IS NULL", body.from, body.into);
  return n?.n === 2 ? n : null;
}

const parse = (row) => {
  try {
    return { ...JSON.parse(row.text), by: row.by, at: iso(row.at) };
  } catch {
    return null;
  }
};

/** loop_drafts 줄들 → key별 초안. */
export const draftMap = (rows) => new Map((rows ?? []).map((r) => [r.key, parse(r)]).filter(([, d]) => d));

/** 초안 몇 개를 key로: 화면 목록(보상·비슷한 회사)에 붙일 때. */
export async function draftsFor(env, keys) {
  if (!keys.length) return new Map();
  return draftMap(await all(env, `SELECT key, text, by, at FROM loop_drafts WHERE key IN (${keys.map(() => "?").join(",")})`, ...keys).catch(() => []));
}

/**
 * 사람이 정한 뒤: 그 일의 초안이 있으면 추천과 같았는지 남기고 지워요. [pick]은 사람이 고른 것(adopt·reject·paid·void·merge·skip).
 * 초안이 없으면 아무것도 하지 않아요.
 */
export async function settleDraft(env, key, pick, now) {
  const row = await one(env, "SELECT key, text, by, at FROM loop_drafts WHERE key = ?", key).catch(() => null);
  if (!row) return;
  const d = parse(row) ?? {};
  await run(env, "DELETE FROM loop_drafts WHERE key = ?", key);
  await audit(env, now, "claude_draft_done", key, d.pick === pick ? "같음" : `다름(추천 ${d.pick ?? "?"}, 정함 ${pick})`).catch(() => {});
}

/** 조종석: 열린 초안 수, 사람이 정한 초안과 같았던 비율(최근 90일), 넓히기 관문. */
export async function draftStats(env, now = Date.now()) {
  const [open, done] = await Promise.all([
    one(env, "SELECT COUNT(*) AS n FROM loop_drafts WHERE key LIKE 'group:%' OR key LIKE 'reward:%' OR key LIKE 'merge:%'").catch(() => ({ n: 0 })),
    all(env, "SELECT detail FROM audit WHERE action = 'claude_draft_done' AND at >= ?", now - 90 * 864e5).catch(() => []),
  ]);
  const same = done.filter((d) => d.detail === "같음").length;
  const agree = done.length ? Math.round((same / done.length) * 100) : null;
  return { open: open?.n ?? 0, done: done.length, same, agree, gate: { ...DRAFT_GATE, ready: done.length >= DRAFT_GATE.least && (agree ?? 0) >= DRAFT_GATE.agree } };
}

/**
 * GET /drafts: 열린 초안 모두(종류마다 그 권한이 있는 것만). POST /drafts {kind, company+grp | id | from+into, pick, reason?, note, sources?, months?}
 * 은 초안을 적고, {..., pick: null}은 지워요. 적는 데는 그 칸을 볼 권한이면 돼요(정하는 일은 따로 '고치기' 권한).
 */
export async function draftRoute(request, env, method, now) {
  const actor = env.actor;
  if (method === "GET") {
    const rows = await all(env, "SELECT key, text, by, at FROM loop_drafts WHERE key LIKE 'group:%' OR key LIKE 'reward:%' OR key LIKE 'merge:%' ORDER BY at DESC LIMIT 200");
    const drafts = rows.map((r) => ({ key: r.key, ...parse(r) })).filter((d) => d.kind && can(actor, KINDS[d.kind].area, "view"));
    return json({ drafts, stats: await draftStats(env, now) });
  }
  if (method !== "POST") return json({ error: "not_found" }, 404);
  const body = await readJson(request, 8192);
  const { kind, key } = keyOf(body);
  if (!can(actor, KINDS[kind].area, "view")) return json({ error: "no_permission", area: KINDS[kind].area }, 403);
  if (body.pick === null) {
    await run(env, "DELETE FROM loop_drafts WHERE key = ?", key);
    await audit(env, now, "claude_draft", key, "지움");
    return json({ key, draft: null });
  }
  const pick = KINDS[kind].picks.includes(body.pick) ? body.pick : refuse("pick", "pick");
  if (!(await targetExists(env, kind, body))) return json({ error: "not_found" }, 404);
  const draft = { kind, pick, note: plainText(body.note, 500, "note", true), sources: sourcesOf(body.sources) };
  if (kind === "group" && pick === "reject") draft.reason = REJECT_REASONS.includes(body.reason) ? body.reason : refuse("reason", "reason");
  if (kind === "reward" && pick === "paid" && body.months !== undefined) {
    draft.months = Number.isInteger(body.months) && body.months >= 1 && body.months <= 12 ? body.months : refuse("months", "months");
  }
  if (kind === "group") Object.assign(draft, { company: body.company, grp: body.grp });
  if (kind === "reward") draft.id = body.id;
  if (kind === "merge") Object.assign(draft, { from: body.from, into: body.into });
  const by = actorName(env);
  await run(env, "INSERT OR REPLACE INTO loop_drafts (key, text, why, by, at) VALUES (?, ?, ?, ?, ?)", key, JSON.stringify(draft), draft.note.slice(0, 300), by, now);
  await audit(env, now, "claude_draft", key, `${pick} · ${draft.note.slice(0, 80)}`);
  return json({ key, draft: { ...draft, by, at: iso(now) } });
}
