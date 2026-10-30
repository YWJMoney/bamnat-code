// AX 1.30 The developer app's numbers that move: 7-day trends and the alerts on 오늘 (ADMX-01, ADMX-07), what the
// alerts push (ADMX-10), one search over companies, questions, 개발사항 and admins (ADMX-08) and the extras of 지표
// (ADMX-07). Kept apart from index.js so the 1.30 work on the plaza's reads does not meet this in one file; it reads the
// database through env.DB only and takes what it needs from index.js as arguments, so tools/bundle.mjs inlines it.

const AX_DAY = 86400000;
/** A KST day (YYYY-MM-DD) of [ms]. */
const axKst = (ms) => new Date(ms + 9 * 3600000).toISOString().slice(0, 10);

/**
 * [ADMX-01] Daily counts of the last seven KST days (index 6 is today), as statements for /today's one trip:
 * [sql, ...params] each, read only for the areas [may] holds ({inquiries, review, community, metrics}).
 */
export function trendQueries(may, start) {
  const from = start - 6 * AX_DAY;
  const byDay = (table, col, where = "") =>
    [`SELECT CAST((${col} - ?) / ${AX_DAY} AS INTEGER) AS d, COUNT(*) AS n FROM ${table} WHERE ${col} >= ?${where} GROUP BY d`, from, from];
  const q = {};
  if (may.inquiries) {
    q.tr_inquiries = byDay("inquiries", "created_at");
    q.tr_bugs = byDay("inquiries", "created_at", " AND kind = 'bug'");
  }
  if (may.review) {
    q.tr_reports = byDay("reports", "created_at", " AND status != 'ask'");
    q.tr_doubts = byDay("votes", "at", " AND ok = 0");
    // BD 1.30 [PLZD-04] sends a setup shaped unlike its department's rule as pending with reason 'shape'.
    q.ax_shape = ["SELECT COUNT(*) AS n FROM (SELECT DISTINCT company_id, grp FROM reports WHERE status = 'pending' AND reason = 'shape')"];
  }
  if (may.community) q.tr_flags = byDay("post_flags", "at");
  if (may.metrics) q.tr_errors = ["SELECT key, count FROM counters WHERE key BETWEEN ? AND ?", `errors|${axKst(from)}`, `errors|${axKst(start)}`];
  // 1.35 [FREE-10] Themes opened with no ad because none could come, all reasons, per day the phones counted them.
  if (may.metrics) q.tr_themeFree = ["SELECT day, SUM(total) AS n FROM usage_events WHERE event LIKE 'theme_free_%' AND day BETWEEN ? AND ? GROUP BY day", axKst(from), axKst(start)];
  return q;
}

/** [ADMX-01] The trends from what [trendQueries] read: { inquiries: [7 numbers], … } for the areas read. */
export function trendFrom(got, start) {
  const out = {};
  const seven = (rows) => {
    const list = [0, 0, 0, 0, 0, 0, 0];
    for (const r of rows ?? []) if (r.d >= 0 && r.d <= 6) list[r.d] += r.n;
    return list;
  };
  for (const k of ["inquiries", "bugs", "reports", "doubts", "flags"]) if (got[`tr_${k}`]) out[k] = seven(got[`tr_${k}`]);
  if (got.tr_themeFree) out.themeFree = Array.from({ length: 7 }, (_, i) => got.tr_themeFree.find((r) => r.day === axKst(start - (6 - i) * AX_DAY))?.n ?? 0);
  if (got.tr_errors) {
    const days = Array.from({ length: 7 }, (_, i) => `errors|${axKst(start - (6 - i) * AX_DAY)}`);
    out.errors = days.map((key) => got.tr_errors.find((r) => r.key === key)?.count ?? 0);
  }
  return out;
}

/** What a 할 일 item is, in the alert's words. */
const AX_TODO_NAMES = { inquiry: "문의", group: "제보", ask: "앱에 없는 방식", doubted: "달라요 달린 규칙", hidden_post: "가려진 글", flagged_post: "신고된 글",
  rate: "임금표", dev: "개발사항", rewards: "보상", ops: "이달 운영", admin: "관리자 승인", phone: "관리자 휴대폰" };

/** Today against yesterday: at least [min] today and [times] yesterday (yesterday 0 counts as 1). */
const axSpiked = (list, min, times) => Boolean(list) && list[6] >= min && list[6] >= times * Math.max(1, list[5]);

/**
 * [ADMX-01][ADMX-07] What needs a look now, the worst first: each { key, kind, tone, title, meta, to, n }. [kind] is the
 * push kind it goes out as (ADMX-10). Rules: 기한 넘긴 일; 정할 판; 모양이 다른 판; 달라요 많은 규칙 (3 or more);
 * 문제 신고 twice yesterday's (3 or more); 서버 오류 three times yesterday's (5 or more); 달라요 twice the week's daily
 * mean (3 or more); 광고 없이 연 테마 yesterday twice the five days before (10 or more).
 */
export function alertsFrom({ trend = {}, todo = [], now = Date.now(), versions = 0, shape = 0 }) {
  const out = [];
  const late = todo.filter((x) => x.due && Date.parse(x.due) < now);
  if (late.length) {
    out.push({ key: "late", kind: "late", tone: "bad", n: late.length, title: `기한 넘긴 일 ${late.length}건`,
      meta: [...new Set(late.map((x) => AX_TODO_NAMES[x.type] ?? "할 일"))].slice(0, 3).join(" · "), to: "#/home?view=today" });
  }
  if (axSpiked(trend.bugs, 3, 2)) {
    out.push({ key: "bugs", kind: "spike", tone: "bad", n: trend.bugs[6], title: `문제 신고가 늘었어요 · 오늘 ${trend.bugs[6]}건`,
      meta: `어제 ${trend.bugs[5]}건`, to: "#/inquiries?status=all&kind=bug" });
  }
  if (axSpiked(trend.errors, 5, 3)) {
    out.push({ key: "errors", kind: "spike", tone: "bad", n: trend.errors[6], title: `서버 오류가 늘었어요 · 오늘 ${trend.errors[6]}번`,
      meta: `어제 ${trend.errors[5]}번`, to: "#/metrics?days=7" });
  }
  // 1.35 [FREE-10] Phones send the days before today, so yesterday (index 5) is the last whole day: 10 or more free
  // theme days and twice the mean of the five days before means the ads are not coming for many (사용자 2026-10-30 16:00).
  if (trend.themeFree) {
    const before = trend.themeFree.slice(0, 5).reduce((n, v) => n + v, 0) / 5;
    if (trend.themeFree[5] >= 10 && trend.themeFree[5] >= 2 * Math.max(1, before)) {
      out.push({ key: "themeFree", kind: "spike", tone: "warn", n: trend.themeFree[5], title: `광고 없이 열어 준 테마가 늘었어요 · 어제 ${trend.themeFree[5]}번`,
        meta: `그 전 5일 하루 평균 ${Math.round(before * 10) / 10}번 · 이유는 지표에서`, to: "#/metrics?days=7" });
    }
  }
  if (trend.doubts) {
    const week = trend.doubts.slice(0, 6).reduce((n, v) => n + v, 0) / 6;
    if (trend.doubts[6] >= 3 && trend.doubts[6] >= 2 * Math.max(1, week)) {
      out.push({ key: "doubts", kind: "doubt", tone: "warn", n: trend.doubts[6], title: `달라요가 늘었어요 · 오늘 ${trend.doubts[6]}개`,
        meta: `지난 6일 하루 평균 ${Math.round(week * 10) / 10}개`, to: "#/review?show=flagged" });
    }
  }
  const doubted = todo.filter((x) => x.type === "doubted" && (x.n ?? 0) >= 3);
  if (doubted.length) {
    out.push({ key: "doubted", kind: "doubt", tone: "warn", n: doubted.length, title: `달라요 많은 규칙 ${doubted.length}개`,
      meta: doubted.slice(0, 2).map((x) => x.company ?? x.title).join(" · "), to: "#/review?show=flagged" });
  }
  if (versions) out.push({ key: "versions", kind: "versions", tone: "warn", n: versions, title: `정할 판 ${versions}곳`, meta: "한 부서에 판이 둘 이상이에요", to: "#/versions" });
  if (shape) out.push({ key: "shape", kind: "versions", tone: "warn", n: shape, title: `모양이 다른 판 ${shape}건`, meta: "교대 수나 주기 길이가 대표 판과 달라요", to: "#/review?show=shape" });
  return out;
}

/**
 * [ADMX-08] One search for the developer app's 찾기·이동: companies (name or other name), questions (their words or
 * number), 개발사항 (title or body) and admins (the owner's), each up to [limit] with how many there are and where the
 * whole list is. Only what this admin may see; [h] is { can, normName } from index.js.
 */
export async function adminSearch(env, actor, raw, h, limit = 3) {
  const q = String(raw ?? "").trim().slice(0, 40);
  if ([...q].length < 2) return { q, groups: [] };
  const like = `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  const norm = `%${h.normName(q).replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  const enc = encodeURIComponent(q);
  const plan = [];
  if (h.can(actor, "companies")) {
    plan.push(["companies", "회사", `#/companies?q=${enc}`, `SELECT c.id, c.name, c.hidden, c.merged_into,
        (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS rules,
        COUNT(*) OVER () AS total
      FROM companies c WHERE c.norm LIKE ?1 ESCAPE '\\' OR EXISTS (SELECT 1 FROM aliases a WHERE a.company_id = c.id AND a.norm LIKE ?1 ESCAPE '\\')
      ORDER BY c.merged_into IS NOT NULL, c.created_at DESC LIMIT ${limit}`, [norm],
    (r) => ({ label: r.name, hint: [r.rules ? `규칙 ${r.rules}` : "규칙 없음", r.hidden ? "숨김" : "", r.merged_into ? "합쳐짐" : ""].filter(Boolean).join(" · "), to: `#/companies/${r.id}` })]);
  }
  if (h.can(actor, "inquiries")) {
    plan.push(["inquiries", "문의", `#/inquiries?status=all&q=${enc}`, `SELECT i.id, i.kind, i.status, i.updated_at,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first, COUNT(*) OVER () AS total
      FROM inquiries i WHERE i.id = ?2 OR EXISTS (SELECT 1 FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.text LIKE ?1 ESCAPE '\\')
      ORDER BY i.status = 'open' DESC, i.updated_at DESC LIMIT ${limit}`, [like, q],
    (r) => ({ label: [...String(r.first ?? "")].slice(0, 60).join("") || "(내용 없음)", hint: `${{ open: "답 대기", answered: "답함", closed: "닫힘" }[r.status] ?? ""}`, to: `#/inquiries/${r.id}` })]);
  }
  if (h.can(actor, "dev")) {
    plan.push(["dev", "개발사항", `#/dev?status=all&q=${enc}`, `SELECT id, title, status, COUNT(*) OVER () AS total FROM dev_items
      WHERE deleted_at IS NULL AND (title LIKE ?1 ESCAPE '\\' OR body LIKE ?1 ESCAPE '\\') ORDER BY updated_at DESC LIMIT ${limit}`, [like],
    (r) => ({ label: r.title, hint: { todo: "접수", doing: "개발중", done: "완료", rejected: "반려" }[r.status] ?? "", to: `#/dev/${r.id}` })]);
  }
  if (actor.owner) {
    plan.push(["admins", "관리자", "#/admins", `SELECT id, name, email, status, COUNT(*) OVER () AS total FROM admins
      WHERE name LIKE ?1 ESCAPE '\\' OR email LIKE ?1 ESCAPE '\\' ORDER BY name LIMIT ${limit}`, [like],
    (r) => ({ label: r.name, hint: `${r.email}${r.status === "pending" ? " · 승인 대기" : r.status === "suspended" ? " · 멈춤" : ""}`, to: "#/admins" })]);
  }
  if (!plan.length) return { q, groups: [] };
  const res = await env.DB.batch(plan.map(([, , , sql, args]) => env.DB.prepare(sql).bind(...args)));
  return {
    q,
    groups: plan.map(([kind, label, more, , , row], i) => {
      const rows = res[i].results ?? [];
      return { kind, label, total: rows[0]?.total ?? 0, more, items: rows.map(row) };
    }).filter((g) => g.total),
  };
}

/**
 * [ADMX-07] 지표's extras for the period [from]..[to] (KST days): the app versions first seen in it (the charts mark the
 * day each came out) and the plaza and question counts of this period and the one just before, for the change shown
 * beside each panel's big number.
 */
export async function metricExtras(env, from, to) {
  const start = Date.parse(`${from}T00:00:00+09:00`);
  const end = Date.parse(`${to}T00:00:00+09:00`) + AX_DAY;
  const len = end - start;
  const count = (table, col, where = "") => `SELECT
      SUM(CASE WHEN ${col} >= ?1 THEN 1 ELSE 0 END) AS now, SUM(CASE WHEN ${col} < ?1 THEN 1 ELSE 0 END) AS before
    FROM ${table} WHERE ${col} >= ?2 AND ${col} < ?3${where}`;
  const parts = {
    reports: count("reports", "created_at", " AND status != 'ask'"),
    applied: count("uses", "at"),
    inquiries: count("inquiries", "created_at"),
    bugs: count("inquiries", "created_at", " AND kind = 'bug'"),
    posts: count("posts", "created_at"),
  };
  const keys = Object.keys(parts);
  const res = await env.DB.batch([
    env.DB.prepare("SELECT app, MIN(day) AS day FROM usage_days WHERE day <= ? GROUP BY app HAVING MIN(day) >= ? ORDER BY day").bind(to, from),
    env.DB.prepare("SELECT key, count FROM counters WHERE key BETWEEN ? AND ?").bind(`errors|${axKst(start - len)}`, `errors|${to}`),
    ...keys.map((k) => env.DB.prepare(parts[k]).bind(start, start - len, end)),
  ]);
  const compare = {};
  keys.forEach((k, i) => {
    const r = res[i + 2].results?.[0] ?? {};
    compare[k] = { now: r.now ?? 0, before: r.before ?? 0 };
  });
  const errs = res[1].results ?? [];
  compare.errors = {
    now: errs.filter((r) => r.key.slice(7) >= from).reduce((n, r) => n + r.count, 0),
    before: errs.filter((r) => r.key.slice(7) < from).reduce((n, r) => n + r.count, 0),
  };
  return { releases: (res[0].results ?? []).map((r) => ({ app: r.app, day: r.day })), compare };
}
