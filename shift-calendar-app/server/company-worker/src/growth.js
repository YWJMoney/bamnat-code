import { all, audit, batch, iso, json, one, randomId, readJson, run, stmt } from "./index.js";
import { DAY } from "./units.js";
import { grams, likeness, titleFrom } from "./inbox.js";
import { grantMakerReward } from "./maker-reward.js";

// ---- 1.36 자가 발전 바퀴 [GRW-01]~[GRW-06] (사용자 2026-11-01 "자가 발전을 자동으로", 2차 조사 4장) ----
//
//   1 요구가 들어와요   스튜디오 "원하는 꾸미기가 없나요?" (문의 kind "studio", 어느 영역·도구에서 눌렀는지가 [스튜디오 › 영역 › 도구]로
//                       붙어요) 와 요청 판에서 14일 동안 아무도 못 만든 요청.
//   2 저절로 묶어요     비슷한 요구는 한 묶음(studio_needs)이 되고, 부탁한 휴대폰 수를 세요(같은 휴대폰은 한 번). 낱말 비교는 문의 정리함과
//                       같은 likeness예요.
//   3 사용자 부품으로   부탁한 분이 셋이 되면 장터 요청 판에 '여러 분이 부탁했어요'로 저절로 올라가요. 누가 만들어 '완성'하면 묶음은 '풀림'이
//     먼저 풀려요       되고, 부탁한 분 모두의 문의에 답이 가요(앱의 새 답 알림).
//   4 승격 사다리       장터 작품을 실제로 쓴 사람 수와 7일 유지율(Rank와 같은 숫자, 표 수가 아님)로: 모두의 도구 → 기본 도구 후보.
//   5 개발자는 맨 끝만  관리자 앱의 두 목록('조립으로 못 푸는 요구', '기본 도구 후보')에서 정식 손잡이로 만들 것만 골라요.
//   6 손잡이 지도       요구가 많은 영역·도구 순위와 출시 노트 줄("여러분이 부탁해서 생긴 꾸미기", "모두의 도구에서 기본이 된 것").
//
// 사람이 판단하는 곳은 5번 하나예요. 나머지는 숫자 기준으로 매시간(growthTick) 저절로 돌아요. 누가 부탁했는지는 휴대폰 해시만 있고
// 장터에는 묶음 제목만 나가요(문의 글은 보낼 때 연락처·금액을 이미 거절했어요).

export const GROWTH = {
  /** 이만큼 같은 요구가 모이면 장터 요청 판에 저절로 올라가요. */
  marketAskers: 3,
  /** 요청 판에서 이만큼 아무도 '만들게요'를 하지 않으면 스튜디오 요구로도 와요. */
  staleDays: 14,
  /** 묶음을 고르는 비슷함 (문의 정리함 SORTING.same과 같음); 같은 영역이면 조금 더해요. */
  same: 0.42,
  sameArea: 0.1,
  /** 한 번에 비교하는 열린 묶음 수. */
  needs: 300,
  /** 모두의 도구: 쓴 사람과 7일 유지율(%) 이상, 신고 없음. */
  toolUsers: 50,
  toolKept: 40,
  /** 기본 도구 후보: 더 많이, 더 오래. */
  candidateUsers: 300,
  candidateKept: 50,
};

export const NEED_STATES = ["open", "market", "solved", "building", "official", "declined"];
export const NEED_STATE_NAMES = { open: "모이는 중", market: "장터에 올림", solved: "사용자 부품으로 풀림", building: "정식으로 만드는 중",
  official: "기본 손잡이로 나옴", declined: "하지 않기로 함" };
export const TIER_NAMES = { 2: "모두의 도구", 3: "기본 도구 후보", 4: "기본 도구로 채택" };
const GRW_STYLES = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];

export const GROWTH_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS studio_needs (id TEXT PRIMARY KEY, title TEXT NOT NULL, area TEXT NOT NULL DEFAULT '', tool TEXT NOT NULL DEFAULT '',
    style TEXT, words TEXT NOT NULL DEFAULT '', askers INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL DEFAULT 'open', request_id TEXT, code TEXT,
    note TEXT NOT NULL DEFAULT '', version TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS studio_needs_state ON studio_needs (state, askers)",
  `CREATE TABLE IF NOT EXISTS studio_need_links (src TEXT NOT NULL, ref TEXT NOT NULL, need_id TEXT NOT NULL, device TEXT NOT NULL, text TEXT NOT NULL DEFAULT '',
    at INTEGER NOT NULL, told INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (src, ref))`,
  "CREATE INDEX IF NOT EXISTS studio_need_links_need ON studio_need_links (need_id)",
  // [GRW-05] 승격 사다리: 2 모두의 도구, 3 기본 도구 후보, 4 기본 도구로 채택(운영자). later: 운영자가 '다음에'로 미룬 후보.
  `CREATE TABLE IF NOT EXISTS recipe_tiers (code TEXT PRIMARY KEY, tier INTEGER NOT NULL, since INTEGER NOT NULL, later INTEGER NOT NULL DEFAULT 0,
    version TEXT, note TEXT NOT NULL DEFAULT '')`,
];
/** [GRW-03] 요청 판 한 줄이 묶음에서 왔으면 부탁한 분 수. */
export const GROWTH_COLUMNS = ["ALTER TABLE recipe_requests ADD COLUMN askers INTEGER NOT NULL DEFAULT 0"];

/** [GRW-01] "[스튜디오 › 달력 칸 › 색] 글" → { area, tool, body }. 다른 머리말이면 영역 없이 글 전체. */
export function parseWhere(text) {
  const t = String(text ?? "");
  const m = t.match(/^\s*\[스튜디오(?:\s*›\s*([^›\]]{1,30}))?(?:\s*›\s*([^\]]{1,40}))?\]\s*/);
  if (!m) return { area: "", tool: "", body: t.trim() };
  return { area: (m[1] ?? "").trim(), tool: (m[2] ?? "").trim(), body: t.slice(m[0].length).trim() };
}

/**
 * [GRW-02] 요구 하나를 가장 비슷한 열린 묶음에 붙이거나 새 묶음을 만들어요. 같은 휴대폰이 같은 묶음에 두 번 부탁해도 한 명이에요.
 * Returns the need's id.
 */
export async function attachNeed(env, { src, ref, device, text, area = "", tool = "", style = null, now }) {
  if (await one(env, "SELECT need_id FROM studio_need_links WHERE src = ? AND ref = ?", src, ref)) return null;
  const body = String(text ?? "").trim();
  const g = grams(`${area} ${tool} ${body}`);
  const open = await all(env, "SELECT id, area, words FROM studio_needs WHERE state IN ('open', 'market') ORDER BY updated_at DESC LIMIT ?", GROWTH.needs);
  let best = null;
  let score = 0;
  for (const n of open) {
    const s = likeness(g, grams(n.words)) + (area && n.area === area ? GROWTH.sameArea : 0);
    if (s > score) [best, score] = [n, s];
  }
  const st = GRW_STYLES.includes(style) ? style : null;
  const short = [...body].slice(0, 200).join("");
  if (best && score >= GROWTH.same) {
    const seen = await one(env, "SELECT 1 AS x FROM studio_need_links WHERE need_id = ? AND device = ?", best.id, device);
    await batch(env, [
      stmt(env, "INSERT INTO studio_need_links (src, ref, need_id, device, text, at) VALUES (?, ?, ?, ?, ?, ?)", src, ref, best.id, device, short, now),
      // Words of the first few asks only, so one long group does not swallow everything.
      stmt(env, `UPDATE studio_needs SET askers = askers + ?, updated_at = ?, style = COALESCE(style, ?),
        words = CASE WHEN length(words) < 600 THEN words || ' ' || ? ELSE words END WHERE id = ?`, seen ? 0 : 1, now, st, short, best.id),
    ]);
    return best.id;
  }
  const id = `n${randomId(11)}`;
  await batch(env, [
    stmt(env, `INSERT INTO studio_needs (id, title, area, tool, style, words, askers, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)`,
      id, titleFrom(body) || "(내용 없음)", area, tool, st, `${area} ${tool} ${short}`.trim(), now, now),
    stmt(env, "INSERT INTO studio_need_links (src, ref, need_id, device, text, at) VALUES (?, ?, ?, ?, ?, ?)", src, ref, id, device, short, now),
  ]);
  return id;
}

/** [GRW-01] A studio inquiry just sent: into the groups (never fails the question). */
export async function needFromInquiry(env, id, device, kind, text, info, now) {
  if (kind !== "studio") return;
  try {
    const w = parseWhere(text);
    const needId = await attachNeed(env, { src: "inq", ref: id, device, text: w.body, area: w.area, tool: w.tool, style: info?.studio ?? null, now });
    // PC 확인 8d290ec3: 보낸 휴대폰에 '2/3명 모였어요'를 보여 줘요.
    const n = needId && await one(env, "SELECT askers, state FROM studio_needs WHERE id = ?", needId);
    return n ? { askers: n.askers, goal: GROWTH.marketAskers, state: n.state } : undefined;
  } catch (e) {
    console.error("growth attach", e && e.stack ? e.stack : String(e));
  }
}

/** [GRW-03] Words for the asker when a group is solved or comes out as a 손잡이. */
export function toldText(need, how) {
  if (how === "solved") {
    return `부탁하신 꾸미기("${need.title}")를 다른 분이 만들어 장터에 올렸어요. 마을 › 장터에서 코드 ${need.code}로 찾아 입어 볼 수 있어요. 고마워요!`;
  }
  if (how === "declined") {
    return `부탁하신 꾸미기("${need.title}")는 지금은 넣기 어려워요. ${need.note} 같은 부탁이 더 모이면 다시 볼게요. 의견 고마워요!`;
  }
  return `부탁하신 꾸미기("${need.title}")가 ${need.version ? `${need.version} 판에서 ` : "다음 판에서 "}스튜디오의 기본 손잡이로 나와요. 여러분이 부탁해서 생긴 꾸미기로 출시 노트에도 적을게요.`;
}

/** Answers every asker of [need] not told yet; 문의는 '답함'이 되어 앱의 새 답 알림이 떠요. */
async function tellAskers(env, need, how, now) {
  const links = await all(env, "SELECT src, ref FROM studio_need_links WHERE need_id = ? AND told = 0 AND src = 'inq'", need.id);
  const text = toldText(need, how);
  const stmts = [];
  for (const l of links) {
    stmts.push(stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) SELECT id, 'admin', ?, '밤낮', ? FROM inquiries WHERE id = ?", text, now, l.ref));
    stmts.push(stmt(env, "UPDATE inquiries SET status = 'answered', updated_at = ? WHERE id = ?", now, l.ref));
  }
  stmts.push(stmt(env, "UPDATE studio_need_links SET told = 1 WHERE need_id = ? AND src = 'inq'", need.id));
  if (stmts.length) await batch(env, stmts);
  return links.length;
}

/** [GRW-03] A request on the 요청 판 got its 완성: the group it came from is 풀림, and its askers hear. */
export async function needSolved(env, requestId, code, now) {
  const rows = await all(env, `SELECT DISTINCT n.* FROM studio_needs n WHERE n.request_id = ?
    OR n.id IN (SELECT need_id FROM studio_need_links WHERE src = 'req' AND ref = ?)`, requestId, requestId);
  for (const n of rows) {
    if (!["open", "market"].includes(n.state)) continue;
    await run(env, "UPDATE studio_needs SET state = 'solved', code = ?, updated_at = ? WHERE id = ?", code, now, n.id);
    await tellAskers(env, { ...n, code }, "solved", now);
  }
}

/** [GRW-05] Where a recipe stands on the 승격 사다리 by its numbers (null: plain 장터 작품). */
export function tierOf(r) {
  if (!r || r.flags > 0 || r.state !== "visible" || r.scope !== "public") return null;
  const kept = r.applied > 0 ? (100 * r.kept7) / r.applied : 0;
  if (r.applied >= GROWTH.candidateUsers && kept >= GROWTH.candidateKept) return 3;
  if (r.applied >= GROWTH.toolUsers && kept >= GROWTH.toolKept) return 2;
  return null;
}

/** [GRW-02][GRW-03][GRW-05] 매시간: 오래된 요청을 요구로, 모인 묶음을 장터로, 사다리를 다시. */
export async function growthTick(env, now) {
  // 1 요청 판에서 14일 동안 아무도 '만들게요'를 하지 않은 것.
  const stale = await all(env, `SELECT id, device, style, body FROM recipe_requests WHERE state = 'waiting' AND hidden = 0 AND device != 'operator'
    AND created_at < ? AND id NOT IN (SELECT ref FROM studio_need_links WHERE src = 'req') LIMIT 100`, now - GROWTH.staleDays * DAY);
  for (const q of stale) await attachNeed(env, { src: "req", ref: q.id, device: q.device, text: q.body, style: q.style, now });
  // 2 셋 이상 모인 묶음: 장터 요청 판에 '여러 분이 부탁했어요'.
  const ready = await all(env, "SELECT * FROM studio_needs WHERE state = 'open' AND askers >= ? AND request_id IS NULL LIMIT 50", GROWTH.marketAskers);
  for (const n of ready) await toMarket(env, n, now);
  // 모인 묶음의 요청 판 줄은 부탁한 분 수를 따라가요.
  await run(env, `UPDATE recipe_requests SET askers = (SELECT askers FROM studio_needs WHERE request_id = recipe_requests.id)
    WHERE device = 'operator' AND id IN (SELECT request_id FROM studio_needs WHERE request_id IS NOT NULL)`);
  await ladderTick(env, now);
}

async function toMarket(env, n, now) {
  const id = `q${randomId(12)}`;
  const body = [...`${n.area ? `[${n.area}${n.tool ? ` › ${n.tool}` : ""}] ` : ""}${n.title}`].slice(0, 200).join("");
  await batch(env, [
    // style ALL: every kind of work's 요청 판 shows it (rcRequestList), unless the asks said one kind.
    stmt(env, "INSERT INTO recipe_requests (id, device, style, body, askers, created_at, updated_at) VALUES (?, 'operator', ?, ?, ?, ?, ?)",
      id, n.style ?? "ALL", body, n.askers, now, now),
    stmt(env, "UPDATE studio_needs SET state = 'market', request_id = ?, updated_at = ? WHERE id = ?", id, now, n.id),
  ]);
  return id;
}

/** [GRW-05] 사다리: 숫자로 오르고(2·3), 숫자가 떨어지면 내려와요. 운영자가 채택한 4는 그대로예요. */
export async function ladderTick(env, now) {
  const rows = await all(env, `SELECT code, owner, state, scope, applied, kept7, flags FROM recipes WHERE state = 'visible' AND scope = 'public' AND applied >= ?`,
    GROWTH.toolUsers);
  const tiers = new Map((await all(env, "SELECT code, tier FROM recipe_tiers")).map((t) => [t.code, t.tier]));
  const stmts = [];
  const seen = new Set();
  for (const r of rows) {
    const t = tierOf(r);
    seen.add(r.code);
    const had = tiers.get(r.code);
    if (had === 4) continue;
    // [GRW-09] 처음 모두의 도구가 되면 만든 분께 프로 30일 (작품마다 한 번, grantMakerReward가 두 번을 막아요).
    if (t && !had) await grantMakerReward(env, r.owner, "tool", r.code, now);
    if (t && had !== t) stmts.push(stmt(env, "INSERT INTO recipe_tiers (code, tier, since) VALUES (?, ?, ?) ON CONFLICT(code) DO UPDATE SET tier = excluded.tier, since = excluded.since", r.code, t, now));
    if (!t && had) stmts.push(stmt(env, "DELETE FROM recipe_tiers WHERE code = ?", r.code));
  }
  for (const [code, tier] of tiers) if (tier !== 4 && !seen.has(code)) stmts.push(stmt(env, "DELETE FROM recipe_tiers WHERE code = ?", code));
  if (stmts.length) await batch(env, stmts);
}

// ---- the app's side ----

/** [GRW-05] GET /api/recipes/tools?style=: 모두의 도구 (and 기본 도구 후보·채택), most used first. Nothing about who. */
export async function toolsList(env, url) {
  const style = GRW_STYLES.includes(url.searchParams.get("style")) ? url.searchParams.get("style") : null;
  const rows = await all(env, `SELECT r.code, r.title, r.summary, r.styles, r.applied, r.kept7, r.maker, t.tier FROM recipe_tiers t JOIN recipes r ON r.code = t.code
    WHERE r.state = 'visible' AND r.scope = 'public' ${style ? "AND (',' || r.styles || ',') LIKE ?" : ""} ORDER BY r.applied DESC LIMIT 40`,
  ...(style ? [`%,${style},%`] : []));
  return json({ items: rows.map((r) => ({ code: r.code, title: r.title, summary: r.summary, styles: r.styles.split(",").filter(Boolean), users: r.applied,
    kept: r.applied ? Math.trunc((100 * r.kept7) / r.applied) : null, maker: r.maker ?? null, tier: Math.min(r.tier, 3), tierName: TIER_NAMES[Math.min(r.tier, 3)] })) },
  200, { "cache-control": "public, max-age=300" });
}

export async function growthRoute(request, env, path, url) {
  if (path === "/api/recipes/tools" && request.method === "GET") return toolsList(env, url);
  return null;
}

// ---- the admin app's side (/api/admin/growth/…) ----

async function needView(env, n) {
  const links = await all(env, "SELECT src, ref, text, at FROM studio_need_links WHERE need_id = ? ORDER BY at LIMIT 5", n.id);
  const total = await one(env, "SELECT COUNT(*) AS n FROM studio_need_links WHERE need_id = ?", n.id);
  return { id: n.id, title: n.title, area: n.area, tool: n.tool, style: n.style ?? null, askers: n.askers, asks: total.n, state: n.state, stateName: NEED_STATE_NAMES[n.state],
    request: n.request_id ?? null, code: n.code ?? null, note: n.note, version: n.version ?? null, at: iso(n.created_at), updated: iso(n.updated_at),
    samples: links.map((l) => ({ src: l.src, ref: l.ref, text: l.text, at: iso(l.at) })) };
}

/** [GRW-06] 손잡이 지도: 영역·도구마다 부탁한 분 수와 상태별 묶음 수, 많은 순. */
export async function handleMap(env) {
  const rows = await all(env, "SELECT area, tool, state, COUNT(*) AS needs, SUM(askers) AS askers FROM studio_needs GROUP BY area, tool, state");
  const by = new Map();
  for (const r of rows) {
    const key = `${r.area}|${r.tool}`;
    const x = by.get(key) ?? { area: r.area || "(영역 없음)", tool: r.tool, askers: 0, needs: 0, waiting: 0, solved: 0, official: 0 };
    x.askers += r.askers ?? 0;
    x.needs += r.needs;
    if (["open", "market", "building"].includes(r.state)) x.waiting += r.askers ?? 0;
    if (r.state === "solved") x.solved += r.needs;
    if (r.state === "official") x.official += r.needs;
    by.set(key, x);
  }
  return [...by.values()].sort((a, b) => b.waiting - a.waiting || b.askers - a.askers).slice(0, 50);
}

/** [GRW-06] 출시 노트 두 줄: 그 판에 나온 요구와 채택된 도구(만든 분 이름은 별명이 있으면 별명). */
export async function releaseLines(env, version) {
  const needs = await all(env, "SELECT title, askers FROM studio_needs WHERE state = 'official' AND version = ? ORDER BY askers DESC", version);
  const tools = await all(env, `SELECT r.title, n.nick FROM recipe_tiers t JOIN recipes r ON r.code = t.code LEFT JOIN nicks n ON n.device = r.owner
    WHERE t.tier = 4 AND t.version = ?`, version);
  const lines = [];
  if (needs.length) lines.push(`여러분이 부탁해서 생긴 꾸미기: ${needs.map((n) => `${n.title}(${n.askers}명)`).join(", ")}`);
  if (tools.length) lines.push(`모두의 도구에서 기본이 된 것: ${tools.map((t) => `${t.title}${t.nick ? `(만든 분 ${t.nick})` : ""}`).join(", ")}`);
  return lines;
}

/** [GRW-05] 기본 도구 후보와 모두의 도구, 판단에 필요한 숫자(쓴 사람·7일 유지율·신고·요청에서 왔는지). */
async function toolsAdmin(env, show) {
  const where = show === "tools" ? "t.tier = 2" : show === "adopted" ? "t.tier = 4" : show === "later" ? "t.tier = 3 AND t.later = 1" : "t.tier = 3 AND t.later = 0";
  const rows = await all(env, `SELECT r.code, r.title, r.styles, r.applied, r.kept7, r.flags, r.likes, r.maker, t.tier, t.since, t.version, t.note,
      (SELECT COUNT(*) FROM studio_needs n WHERE n.code = r.code) AS needs, nk.nick
    FROM recipe_tiers t JOIN recipes r ON r.code = t.code LEFT JOIN nicks nk ON nk.device = r.owner WHERE ${where} ORDER BY r.applied DESC LIMIT 100`);
  return rows.map((r) => ({ code: r.code, title: r.title, styles: r.styles.split(",").filter(Boolean), users: r.applied, kept: r.applied ? Math.trunc((100 * r.kept7) / r.applied) : 0,
    flags: r.flags, likes: r.likes, maker: r.maker ?? null, nick: r.nick ?? null, tier: r.tier, tierName: TIER_NAMES[r.tier], since: iso(r.since), version: r.version ?? null,
    note: r.note, fromNeeds: r.needs }));
}

const GRW_VERSION = /^\d{1,2}\.\d{1,2}(\.\d{1,3})?$/;

export async function growthAdmin(request, env, url, path, now) {
  const method = request.method;
  if (path === "/growth/needs" && method === "GET") {
    const state = NEED_STATES.includes(url.searchParams.get("state")) ? url.searchParams.get("state") : null;
    // 기본: 아직 끝나지 않은 것 (모이는 중·장터·만드는 중), 부탁한 분 많은 순. unsolved: 장터에 올렸는데 14일 넘게 아무도 못 푼 것.
    const show = url.searchParams.get("show");
    const where = state ? "state = ?" : show === "unsolved" ? "state = 'market' AND updated_at < ?" : show === "ended" ? "state IN ('solved', 'official', 'declined')" : "state IN ('open', 'market', 'building')";
    const args = state ? [state] : show === "unsolved" ? [now - GROWTH.staleDays * DAY] : [];
    const rows = await all(env, `SELECT * FROM studio_needs WHERE ${where} ORDER BY askers DESC, updated_at DESC LIMIT 100`, ...args);
    const counts = Object.fromEntries((await all(env, "SELECT state, COUNT(*) AS n FROM studio_needs GROUP BY state")).map((r) => [r.state, r.n]));
    const unsolved = await one(env, "SELECT COUNT(*) AS n FROM studio_needs WHERE state = 'market' AND updated_at < ?", now - GROWTH.staleDays * DAY);
    const items = [];
    for (const n of rows) items.push(await needView(env, n));
    return json({ items, counts: { ...counts, unsolved: unsolved.n }, rules: GROWTH, states: NEED_STATE_NAMES });
  }
  let m = path.match(/^\/growth\/needs\/(n[A-Za-z0-9]{11})$/);
  if (m && method === "POST") {
    const n = await one(env, "SELECT * FROM studio_needs WHERE id = ?", m[1]);
    if (!n) return json({ error: "not_found" }, 404);
    const body = await readJson(request, 2048);
    const note = typeof body.note === "string" ? [...body.note.trim()].slice(0, 300).join("") : n.note;
    let told = 0;
    switch (body.action) {
      case "market":
        if (n.request_id || !["open"].includes(n.state)) return json({ error: "state" }, 409);
        await toMarket(env, n, now);
        break;
      case "build":
        await run(env, "UPDATE studio_needs SET state = 'building', note = ?, updated_at = ? WHERE id = ?", note, now, n.id);
        break;
      case "official": {
        const version = typeof body.version === "string" && GRW_VERSION.test(body.version) ? body.version : null;
        if (!version) return json({ error: "version", field: "version" }, 400);
        await run(env, "UPDATE studio_needs SET state = 'official', version = ?, note = ?, updated_at = ? WHERE id = ?", version, note, now, n.id);
        told = await tellAskers(env, { ...n, version }, "official", now);
        break;
      }
      case "decline":
        if (!note) return json({ error: "note", field: "note" }, 400);
        await run(env, "UPDATE studio_needs SET state = 'declined', note = ?, updated_at = ? WHERE id = ?", note, now, n.id);
        told = await tellAskers(env, { ...n, note }, "declined", now);
        break;
      case "reopen":
        await run(env, "UPDATE studio_needs SET state = CASE WHEN request_id IS NULL THEN 'open' ELSE 'market' END, updated_at = ? WHERE id = ?", now, n.id);
        break;
      case "title": {
        const t = typeof body.title === "string" ? [...body.title.trim()].slice(0, 60).join("") : "";
        if (t.length < 2) return json({ error: "title", field: "title" }, 400);
        await run(env, "UPDATE studio_needs SET title = ?, updated_at = ? WHERE id = ?", t, now, n.id);
        if (n.request_id) await run(env, "UPDATE recipe_requests SET body = ? WHERE id = ? AND device = 'operator'", t, n.request_id);
        break;
      }
      case "merge": {
        // 같은 요구가 둘로 묶였으면 하나로: 부탁한 분은 휴대폰 기준으로 다시 세요.
        const into = typeof body.into === "string" ? await one(env, "SELECT * FROM studio_needs WHERE id = ?", body.into) : null;
        if (!into || into.id === n.id) return json({ error: "into", field: "into" }, 400);
        await batch(env, [
          stmt(env, "UPDATE studio_need_links SET need_id = ? WHERE need_id = ?", into.id, n.id),
          stmt(env, "UPDATE studio_needs SET askers = (SELECT COUNT(DISTINCT device) FROM studio_need_links WHERE need_id = ?), updated_at = ? WHERE id = ?", into.id, now, into.id),
          stmt(env, "DELETE FROM studio_needs WHERE id = ?", n.id),
          ...(n.request_id ? [stmt(env, "UPDATE recipe_requests SET hidden = 1 WHERE id = ? AND device = 'operator'", n.request_id)] : []),
        ]);
        break;
      }
      default:
        return json({ error: "action", field: "action" }, 400);
    }
    await audit(env, now, `need_${body.action}`, n.id, n.title);
    const after = await one(env, "SELECT * FROM studio_needs WHERE id = ?", body.action === "merge" ? body.into : n.id);
    return json({ ...(await needView(env, after)), told });
  }
  if (path === "/growth/map" && method === "GET") {
    const version = url.searchParams.get("version");
    return json({ items: await handleMap(env), lines: version && GRW_VERSION.test(version) ? await releaseLines(env, version) : [] });
  }
  if (path === "/growth/tools" && method === "GET") {
    const show = ["candidates", "tools", "adopted", "later"].includes(url.searchParams.get("show")) ? url.searchParams.get("show") : "candidates";
    const counts = Object.fromEntries((await all(env, "SELECT tier, later, COUNT(*) AS n FROM recipe_tiers GROUP BY tier, later"))
      .map((r) => [r.tier === 2 ? "tools" : r.tier === 4 ? "adopted" : r.later ? "later" : "candidates", r.n]));
    return json({ items: await toolsAdmin(env, show), counts, rules: GROWTH });
  }
  m = path.match(/^\/growth\/tools\/([A-Za-z0-9]{8})$/);
  if (m && method === "POST") {
    const t = await one(env, "SELECT * FROM recipe_tiers WHERE code = ?", m[1]);
    if (!t) return json({ error: "not_found" }, 404);
    const body = await readJson(request, 1024);
    const note = typeof body.note === "string" ? [...body.note.trim()].slice(0, 300).join("") : t.note;
    if (body.action === "adopt") {
      const version = typeof body.version === "string" && GRW_VERSION.test(body.version) ? body.version : null;
      if (!version) return json({ error: "version", field: "version" }, 400);
      await run(env, "UPDATE recipe_tiers SET tier = 4, later = 0, version = ?, note = ? WHERE code = ?", version, note, t.code);
      // [GRW-09] 기본 도구로 채택: 만든 분께 프로 90일 (이름은 출시 노트 줄과 프로필 배지).
      const owner = await one(env, "SELECT owner FROM recipes WHERE code = ?", t.code);
      if (owner) await grantMakerReward(env, owner.owner, "adopted", t.code, now);
    } else if (body.action === "later") {
      await run(env, "UPDATE recipe_tiers SET later = 1, note = ? WHERE code = ?", note, t.code);
    } else if (body.action === "undo") {
      // 채택·미룸 되돌리기: 숫자로 정해지는 단계로 돌아가요.
      await run(env, "UPDATE recipe_tiers SET tier = CASE WHEN tier = 4 THEN 3 ELSE tier END, later = 0, version = NULL WHERE code = ?", t.code);
    } else return json({ error: "action", field: "action" }, 400);
    await audit(env, now, `tool_${body.action}`, t.code, body.version ?? "");
    return json({ code: t.code, done: body.action });
  }
  return null;
}

/** [GRW-07] 할 일함이 읽는 두 갈래: 장터에서도 못 푼 요구와 기본 도구 후보. */
export async function growthWork(env, now) {
  const [needs, tools] = await Promise.all([
    all(env, "SELECT id, title, askers, state, created_at, updated_at FROM studio_needs WHERE state IN ('open', 'market') AND askers >= ? ORDER BY askers DESC LIMIT 100", 1),
    all(env, "SELECT t.code, t.since, r.title, r.applied FROM recipe_tiers t JOIN recipes r ON r.code = t.code WHERE t.tier = 3 AND t.later = 0 LIMIT 50"),
  ]);
  return { needs, tools, staleBefore: now - GROWTH.staleDays * DAY };
}
