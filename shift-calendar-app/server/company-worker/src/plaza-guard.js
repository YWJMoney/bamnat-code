// BD 1.30 [PLZD-04][PLZD-05][PLZD-07] 광장 중복·변별: the server's half of what the app's SendGuard asks before it sends.
//
// 사용자 2026-10-27: "광장에 이전 버젼과 동일한 내용이면 다시 올리지 못하게 … 한개 회사를 가지고 여러개의 규칙을 보내는 사람이 존재함
// 변별력을 가져야한다 … 근무주기가 2교대인데 3교대를 넣어서 내용이 달라진것을 올리는등 기존 근무주기가 다르거나 비정상 사용을 금지"
// and "같은 회사의 여러개의 정보들이 있으면 도대체 뭐가 맞는지 보기도 힘들고 설정도 어렵다".
//
//  - A setup whose work has another shape than its department's (how many kinds of work, how long the order, 요일별 or 근무표;
//    core SameSetup.Shape is the same rule) waits as pending with reports.reason = 'shape': kept for the developer's 검토
//    ('모양이 다른 판', AX 1.30), never shown on the company page, the search, the 대표 or the board, never adopted by itself.
//  - The same phone sending another version to the same company within a day replaces what it sent before (any department):
//    one phone, one version a day, so one person's many versions no longer stand side by side.
//  - The company page says what each folded version changed against its department's 대표, in AX's words (plaza-diff.js).
// index.js report()·readReport()·leads()·company()·search() call these; this file does not import index.js (tools/bundle.mjs
// puts it in one file). Database reads come in as [db] { one, all }, as plaza-items.js takes its [all].
import { lineChanges } from "./plaza-diff.js";

/** reports.reason of a version held for its shape. */
export const PG_HELD = "shape";
/** A phone's second version of a company within this long takes the place of the first. */
export const PG_REPLACE_MS = 24 * 60 * 60 * 1000;
/** What a sender's app says of a held version (reportView's reason text). */
export const PG_REASONS = { shape: "근무 모양이 이 부서 대표 판과 달라 개발자가 확인한 뒤 보여요. 다른 부서라면 부서 이름을 넣어 다시 보내 주세요" };

/** SQL: a report people may see (not held for its shape). [a] is the reports table's alias, if any. */
export const pgShown = (a = "") => {
  const c = a ? `${a}.reason` : "reason";
  return `(${c} IS NULL OR ${c} != '${PG_HELD}')`;
};

/** One turn of [steps]: the shortest block that, said again and again, is all of it. */
export function pgPeriod(steps) {
  const n = steps.length;
  for (let p = 1; p <= n; p++) {
    if (n % p !== 0) continue;
    let ok = true;
    for (let i = p; i < n && ok; i++) ok = steps[i] === steps[i - p];
    if (ok) return steps.slice(0, p);
  }
  return steps;
}

/**
 * [PLZD-04] The shape of a checked setup's work: { works, days, weekly, roster }, or null when it says nothing of the work.
 * Two shifts of the same times are one kind of work; a day off or none is a rest. The app's SameSetup.shape reads the same.
 */
export function pgShape(setup) {
  const sig = new Map((setup?.shiftTypes ?? []).map((t) => [t.name, t.off ? null : `${t.start ?? ""}-${t.end ?? ""}`]));
  const work = (n) => (typeof n === "string" ? (sig.has(n) ? sig.get(n) : `?${n}`) : null);
  const kinds = (list) => new Set(list.filter((x) => x !== null)).size;
  if (setup?.roster) return { works: kinds([...sig.values()]), days: 0, weekly: false, roster: true };
  if (Array.isArray(setup?.weekly) && setup.weekly.length === 7) return { works: kinds(setup.weekly.map(work)), days: 7, weekly: true, roster: false };
  const steps = (setup?.rotation?.steps ?? []).map(work);
  if (!steps.length) return null;
  return { works: kinds(steps), days: pgPeriod(steps).length, weekly: false, roster: false };
}

/** Whether two shapes can be one department's: the same, or one says nothing of the work. */
export const pgSameShape = (a, b) => !a || !b || (a.works === b.works && a.days === b.days && a.weekly === b.weekly && a.roster === b.roster);

/** "2교대 · 4일 순서", as the app says it. */
export function pgShapeWords(s) {
  if (!s) return "";
  const works = s.works >= 2 ? `${s.works}교대` : s.works === 1 ? "한 가지 근무" : "쉬는 날만";
  return s.roster ? "매달 근무표" : s.weekly ? `요일별 · ${works}` : `${works} · ${s.days}일 순서`;
}

/**
 * [PLZD-04][PLZD-05] What report() needs to know before it stores [r] (readReport's) as [grp] from [dev]:
 *  - [held]: its work has another shape than the department's rule in use, or (none in use) than the first version others
 *    sent for it, unless too many said 달라요 to that one ([db].doubted(grp)); then [lead] and [mine] say both shapes in words.
 *  - [replaced]: this phone's other versions of the company still waiting from the last day ({id, dept}), which it replaces.
 */
export async function pgGuard(db, env, companyId, r, dev, grp, now) {
  const [active, first, recent] = await Promise.all([
    db.one(env, `SELECT grp, setup FROM rules WHERE company_id = ? AND dept_norm = ? AND withdrawn = 0 AND superseded = 0
      ORDER BY version DESC LIMIT 1`, companyId, r.deptNorm),
    db.one(env, `SELECT grp, setup FROM reports WHERE company_id = ? AND dept_norm = ? AND device != ? AND status = 'pending' AND ${pgShown()}
      ORDER BY created_at LIMIT 1`, companyId, r.deptNorm, dev),
    db.all(env, `SELECT id, dept FROM reports WHERE company_id = ? AND device = ? AND status = 'pending' AND grp != ? AND created_at > ?
      ORDER BY created_at`, companyId, dev, grp, now - PG_REPLACE_MS),
  ]);
  const base = active ?? first;
  const lead = base && base.grp !== grp ? pgShape(JSON.parse(base.setup)) : null;
  const mine = pgShape(r.setup);
  // A department's version people say is wrong (달라요 많음) may be followed by one of another shape: that is how it gets fixed.
  const held = Boolean(lead) && !pgSameShape(lead, mine) && !(db.doubted && (await db.doubted(base.grp)));
  return { held, ...(held ? { lead: pgShapeWords(lead), mine: pgShapeWords(mine) } : {}), replaced: recent.map((x) => ({ id: x.id, dept: x.dept })) };
}

/**
 * [PLZD-07] Each version on a company page that is not its department's 대표 gets [changes]: what it says against the 대표, as
 * AX's lineChanges says it (added, removed, summary, facts with word tokens). [key] is the department's normName. The app shows
 * these as they are and makes no diff of its own. Older apps never read the key.
 */
export function pgChanges(setups, key) {
  const byDept = new Map();
  for (const s of setups) byDept.set(key(s.dept ?? ""), [...(byDept.get(key(s.dept ?? "")) ?? []), s]);
  for (const list of byDept.values()) {
    if (list.length < 2) continue;
    const lead = list.find((s) => s.lead) ?? list.find((s) => s.status === "adopted") ?? list[0];
    for (const s of list) if (s !== lead) s.changes = { against: lead.id, ...lineChanges(lead.lines ?? [], s.lines ?? []) };
  }
  return setups;
}
