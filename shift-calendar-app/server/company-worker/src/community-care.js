// 1.39 [CARE-01~06] 마을 돌봄: 부드러운 경고 → 며칠 쉬기 (사용자 2026-11-07 "악성 유저는 차단을 통해 며칠 의견 제시나 커뮤니티 사용
// 중단을 해야 한다. 그 전에 미리 경고도 해야 한다, 부드럽게"). 설계: docs/plans/1.39-마을-돌봄-경고-제한-설계.md.
//
// - warn: 안내·경고. 첫 가려짐에 서버가 저절로(by "auto"), 또는 운영자가(by "operator"). 아무것도 막지 않고 앱이 마을을 열 때 한 번 보여요.
// - opinion: 의견 쉬기 N일. 남이 보는 글(광장 글·답글, 토론 글, 작품 이야기, 요청, 안좋아요 의견, 급여 정보 댓글·보완 요청)을 못 써요.
// - community: 마을 쉬기 N일. 위에 더해 마을의 모든 쓰기와 투표. `muted`(reason 'care')도 함께 써서 지금 있는 쓰기 길이 그대로 막혀요.
// 읽기, 테마 입기, 내 앱, 문의하기(이의 제기)는 언제나 그대로예요. 설치 해시만 두고, 끝난 지 180일 뒤 지워요.
import { DAY } from "./units.js";

export const CARE = {
  /** 운영자가 고르는 쉬기 기간(일). 운영 규칙 6항의 30일 안. */
  days: [1, 3, 7, 14, 30],
  /** 쉬기 전에 이 기간 안의 경고(저절로 또는 운영자)가 있어야 해요. */
  warnWithinDays: 90,
  /** 앱이 마을을 열 때 보여 주는 읽지 않은 경고의 나이. */
  showDays: 30,
  /** 끝난(또는 풀린) 기록을 두는 기간. */
  keepDays: 180,
  /** 운영자 한 줄. */
  noteMax: 200,
  /** 1.39 조사 ⑧: 같은 글을 이 시간 안에 또(같은 곳이든 다른 곳이든) 쓰면 쉬어 가요. */
  repeatMinutes: 10,
};

/** 이유 코드와 관리자 앱의 이름. 앱은 코드로 부드러운 문장을 골라요(CommunityCare.kt). */
export const CARE_REASONS = {
  abuse: "욕설·비방",
  harass: "괴롭힘·따라다니기",
  spam: "광고·도배",
  false: "거짓 정보",
  personal: "개인 정보 드러냄",
  sexual: "성적인 내용",
  illegal: "불법 정보",
  vote: "투표·좋아요 조작",
  other: "그 밖의 운영 원칙",
  // 서버가 저절로(운영자가 확인해 가린 글이 쌓일 때).
  auto_first: "확인된 첫 가려짐 안내",
  auto_again: "한 번 더면 제한 경고",
};

/** 경고 없이 바로 쉬게 할 수 있는 이유(바로 막아야 하는 것). */
export const CARE_SEVERE = ["personal", "sexual", "illegal"];

export const CARE_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS care_notes (id TEXT PRIMARY KEY, device TEXT NOT NULL, kind TEXT NOT NULL, reason TEXT NOT NULL, note TEXT,
    days INTEGER, at INTEGER NOT NULL, until INTEGER, seen_at INTEGER, lifted_at INTEGER, by TEXT NOT NULL, ref TEXT, told INTEGER NOT NULL DEFAULT 0)`,
  "CREATE INDEX IF NOT EXISTS care_notes_device ON care_notes (device, at)",
  "CREATE INDEX IF NOT EXISTS care_notes_until ON care_notes (until)",
  // 1.39 [CARE-10] 해명(문의로): 경고·쉬기·신고 누적 제한 하나마다 한 번. 문의 하나와 이어지고, 운영자의 답(accepted: 풀어 줌, kept: 그대로).
  `CREATE TABLE IF NOT EXISTS care_appeals (id TEXT PRIMARY KEY, device TEXT NOT NULL, target TEXT NOT NULL, inquiry_id TEXT NOT NULL, at INTEGER NOT NULL,
    result TEXT, decided_at INTEGER, UNIQUE (device, target))`,
  "CREATE INDEX IF NOT EXISTS care_appeals_inquiry ON care_appeals (inquiry_id)",
];

const HOLDS = ["opinion", "community"];
const isoOf = (ms) => (ms == null ? null : new Date(ms).toISOString());

/**
 * 켜짐: 깃발 community.care(LAW). 처리방침 3.12·광장 운영 규칙 다음 판 시행일(관리자 앱 › 기능 관리 › 일정)에 저절로 켜져요(사용자 2026-11-07
 * "1.39 출시 때 자동으로 켜지게"). 꺼져 있으면 자동 안내도, 운영자의 경고·쉬기도 새로 만들지 않고, 신고 누적 제한은 1.36 그대로예요.
 * 이미 건 쉬기는 끝나거나 풀 때까지 그대로예요. 로컬 시험은 최고 관리자가 기능 관리에서 강제로 켜요.
 */
export async function careOn(d, env, now = Date.now()) {
  return Boolean(d.legalOpen && (await d.legalOpen(env, "community.care", now)));
}

/** 지금 걸린 쉬기 중 가장 넓고(마을 > 의견) 가장 늦게 끝나는 것, 없으면 null. */
async function activeHold(d, env, dev, now) {
  if (!dev) return null;
  const rows = await d.all(env, `SELECT id, kind, reason, note, days, at, until FROM care_notes
    WHERE device = ? AND kind IN ('opinion', 'community') AND lifted_at IS NULL AND until > ? ORDER BY until DESC`, dev, now);
  if (!rows.length) return null;
  return rows.find((r) => r.kind === "community") ?? rows[0];
}

/**
 * 새 쓰기 길에 붙이는 한 줄(형제 스레드: 급여 정보 댓글·보완 요청·좋아요도 이것으로). [scope] "opinion"은 글·댓글·의견, "community"는
 * 투표·올리기 같은 그 밖의 마을 쓰기. 막히면 403 Response, 아니면 null. 옛 앱도 알아듣게 error는 "muted"예요.
 */
export async function careGate(d, env, dev, scope, now) {
  const h = await activeHold(d, env, dev, now);
  if (!h) return null;
  if (scope === "community" && h.kind !== "community") return null;
  return d.json({ error: "muted", until: isoOf(h.until), hold: { scope: h.kind, until: isoOf(h.until), reason: h.reason } }, 403);
}

/**
 * 1.39 조사 ⑧ 같은 글 반복: 마을 돌봄이 켜져 있고, 같은 휴대폰이 똑같은 글을 [CARE.repeatMinutes]분 안에 이미 썼으면 409 "repeat".
 * 이미 있는 글과 견주기만 하고, 글의 지문 같은 새 기록은 남기지 않아요. 아니면 null.
 */
export async function careRepeat(d, env, dev, text, now) {
  if (!dev || !(await careOn(d, env, now))) return null;
  const same = await d.one(env, "SELECT 1 AS x FROM posts WHERE device = ? AND body = ? AND created_at > ? LIMIT 1", dev, text, now - CARE.repeatMinutes * 60000);
  return same ? d.json({ error: "repeat", field: "body", minutes: CARE.repeatMinutes }, 409) : null;
}

/**
 * 앱이 마을을 열 때: 읽지 않은 경고 하나(가장 최근), 지금 쉬기, 신고 누적 제한의 해명 상태.
 * 1.39 조사 ① '이유서 한 장'(DSA 17조 모양): 무엇에 대한 것인지(about: post|talk|recipe|request|…), 사람이 확인했는지(checked),
 * 어떤 약속인지(rule), 언제까지인지, 해명을 보냈는지(appeal {result: null 기다림 | accepted | kept})와 답 약속(answerHours).
 */
export async function careStanding(d, env, dev, now) {
  if (!dev) return {};
  const [warn, hold, limit] = await Promise.all([
    d.one(env, `SELECT id, reason, note, at, by, ref FROM care_notes WHERE device = ? AND kind = 'warn' AND seen_at IS NULL AND lifted_at IS NULL AND at > ?
      ORDER BY at DESC LIMIT 1`, dev, now - CARE.showDays * DAY),
    activeHold(d, env, dev, now),
    d.one(env, "SELECT since FROM write_limits WHERE device = ? AND until > ?", dev, now),
  ]);
  const keys = [warn?.id, hold?.id, limit ? `limit:${limit.since}` : null].filter(Boolean);
  const appeals = keys.length
    ? await d.all(env, `SELECT target, result FROM care_appeals WHERE device = ? AND target IN (${keys.map(() => "?").join(", ")})`, dev, ...keys) : [];
  const appealOf = (key) => { const a = appeals.find((x) => x.target === key); return a ? { appeal: { result: a.result ?? null } } : {}; };
  const sheet = (r) => ({
    ...(r.ref ? { about: r.ref.split(":")[0] } : {}),
    // 저절로 가는 안내도 운영자가 확인해 가린 글에서만 생겨요(신고만으로 가려진 것은 세지 않아요).
    checked: true, rule: RULE,
    ...appealOf(r.id),
  });
  return {
    ...(warn ? { warning: { id: warn.id, reason: warn.reason, ...(warn.note ? { note: warn.note } : {}), by: warn.by, at: isoOf(warn.at), ...sheet(warn) } } : {}),
    ...(hold ? { hold: { id: hold.id, scope: hold.kind, reason: hold.reason, ...(hold.note ? { note: hold.note } : {}), days: hold.days,
      since: isoOf(hold.at), until: isoOf(hold.until), ...sheet(hold) } } : {}),
    ...(limit ? { limitAppeal: appealOf(`limit:${limit.since}`).appeal ?? null } : {}),
    ...(warn || hold || limit ? { answerHours: APPEAL.answerHours } : {}),
  };
}

/** 앱 안내 창이 가리키는 약속(광장 운영 규칙의 경고·쉬기 항). */
const RULE = "광장 운영 규칙 6항";

/** 경고를 읽었어요(확인했어요). 그 휴대폰의 것만. */
export async function careSeen(d, env, dev, id, now) {
  if (!dev || typeof id !== "string" || !/^c[A-Za-z0-9]{15}$/.test(id)) return false;
  const r = await d.run(env, "UPDATE care_notes SET seen_at = ? WHERE id = ? AND device = ? AND seen_at IS NULL", now, id, dev);
  return (r?.meta?.changes ?? 0) > 0;
}

/** 소식(밤낮 소식) 한 줄: kind 'care', result warn|opinion|community|lifted|ended. */
function news(d, env, dev, result, now) {
  return d.stmt(env, "INSERT INTO plaza_results (device, kind, post_id, result, company_id, at) VALUES (?, 'care', NULL, ?, NULL, ?)", dev, result, now);
}

/** 안내·경고 하나. [by] "auto" 또는 "operator". 만든 기록을 돌려줘요. */
export async function careWarn(d, env, dev, { reason, note = null, by = "operator", ref = null }, now) {
  const id = `c${d.randomId(15)}`;
  await d.batch(env, [
    d.stmt(env, `INSERT INTO care_notes (id, device, kind, reason, note, days, at, until, by, ref) VALUES (?, ?, 'warn', ?, ?, NULL, ?, NULL, ?, ?)`,
      id, dev, reason, note, now, by, ref),
    news(d, env, dev, "warn", now),
  ]);
  return { id, kind: "warn", reason, at: isoOf(now) };
}

/**
 * 1.39 [CARE-06] write-limits weigh()가 부르는 자동 안내: 운영자가 확인한 가려짐이 제한에 아직 못 미칠 때. [level] { n, need, again }:
 * 제한까지 하나 남았으면 '한 번 더면 며칠 줄어요' 경고(auto_again), 아니면 안내(auto_first). 같은 사람에게 하루 한 번까지.
 */
export async function careAutoWarn(d, env, dev, kind, ref, by, now, level = { n: 1, need: 3, again: false }) {
  if (!dev || !(await careOn(d, env, now))) return null;
  const recent = await d.one(env, "SELECT 1 AS x FROM care_notes WHERE device = ? AND kind = 'warn' AND by = 'auto' AND at > ? LIMIT 1", dev, now - DAY);
  if (recent) return null;
  const reason = level.again || level.n >= level.need - 1 ? "auto_again" : "auto_first";
  return careWarn(d, env, dev, { reason, by: "auto", ref: `${kind}:${String(ref).slice(0, 60)}${by === "operator" ? ":op" : ""}` }, now);
}

/** 이 사람이 쉬기 전에 받은 경고가 있는지(운영자나 저절로, 90일 안). */
async function warnedLately(d, env, dev, now) {
  return Boolean(await d.one(env, "SELECT 1 AS x FROM care_notes WHERE device = ? AND kind = 'warn' AND at > ? LIMIT 1", dev, now - CARE.warnWithinDays * DAY));
}

/**
 * 쉬기 N일. 경고가 없으면 { error: "warn_first" }(심각한 이유는 예외). 마을 쉬기는 `muted`에도 넣어 모든 쓰기 길이 막혀요.
 */
export async function careHold(d, env, dev, { scope, days, reason, note = null, ref = null }, now) {
  if (!HOLDS.includes(scope)) return { error: "scope" };
  if (!CARE.days.includes(days)) return { error: "days" };
  if (!CARE_SEVERE.includes(reason) && !(await warnedLately(d, env, dev, now))) return { error: "warn_first" };
  const id = `c${d.randomId(15)}`;
  const until = now + days * DAY;
  await d.batch(env, [
    d.stmt(env, `INSERT INTO care_notes (id, device, kind, reason, note, days, at, until, by, ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'operator', ?)`,
      id, dev, scope, reason, note, days, now, until, ref),
    ...(scope === "community" ? [d.stmt(env, `INSERT INTO muted (device, until, reason) VALUES (?, ?, 'care')
      ON CONFLICT (device) DO UPDATE SET until = MAX(muted.until, excluded.until), reason = CASE WHEN excluded.until >= muted.until THEN 'care' ELSE muted.reason END`,
    dev, until)] : []),
    news(d, env, dev, scope, now),
  ]);
  return { id, kind: scope, reason, days, until: isoOf(until) };
}

/** 풀기(쉬기) 또는 거두기(경고). 마을 쉬기가 넣은 `muted`도 빼요(다른 쉬기가 남아 있으면 그 끝으로). */
export async function careLift(d, env, id, now) {
  const r = typeof id === "string" && /^c[A-Za-z0-9]{15}$/.test(id) ? await d.one(env, "SELECT * FROM care_notes WHERE id = ?", id) : null;
  if (!r || r.lifted_at) return null;
  const stmts = [d.stmt(env, "UPDATE care_notes SET lifted_at = ? WHERE id = ?", now, id)];
  if (r.kind === "community") {
    const other = await d.one(env, `SELECT MAX(until) AS until FROM care_notes WHERE device = ? AND kind = 'community' AND lifted_at IS NULL AND until > ? AND id != ?`,
      r.device, now, id);
    stmts.push(other?.until
      ? d.stmt(env, "UPDATE muted SET until = ? WHERE device = ? AND reason = 'care'", other.until, r.device)
      : d.stmt(env, "DELETE FROM muted WHERE device = ? AND reason = 'care'", r.device));
  }
  if (r.kind !== "warn" && (r.until ?? 0) > now) stmts.push(news(d, env, r.device, "lifted", now));
  await d.batch(env, stmts);
  return { id, lifted: true, kind: r.kind };
}

/** 날마다 정리: 끝난 의견 쉬기는 소식 한 번(마을 쉬기는 `muted`가 끝날 때 'unmuted'로 알려요), 끝난 지 180일 지난 기록은 지움. */
export function carePrune(d, env, now) {
  return [
    d.stmt(env, `INSERT INTO plaza_results (device, kind, post_id, result, company_id, at)
      SELECT device, 'care', NULL, 'ended', NULL, until FROM care_notes WHERE kind = 'opinion' AND told = 0 AND lifted_at IS NULL AND until <= ?`, now),
    d.stmt(env, "UPDATE care_notes SET told = 1 WHERE told = 0 AND kind IN ('opinion', 'community') AND (until <= ? OR lifted_at IS NOT NULL)", now),
    d.stmt(env, "DELETE FROM care_notes WHERE COALESCE(lifted_at, until, at) < ?", now - CARE.keepDays * DAY),
  ];
}

/** 이 사람에게 추천하는 다음 단계: 경고 → 의견 3일 → 마을 7일 → 마을 30일. */
export function careNext(history, now) {
  const recent = history.filter((h) => h.at > now - CARE.keepDays * DAY);
  const warned = recent.some((h) => h.kind === "warn" && h.at > now - CARE.warnWithinDays * DAY);
  const opinion = recent.some((h) => h.kind === "opinion");
  const community = recent.filter((h) => h.kind === "community").length;
  if (!warned && !opinion && !community) return { kind: "warn" };
  if (!opinion && !community) return { kind: "opinion", days: 3 };
  if (!community) return { kind: "community", days: 7 };
  return { kind: "community", days: 30 };
}

/**
 * 관리자 앱의 사람 찾기: 글 번호(광장·토론·작품 이야기), 작품 코드, 휴대폰 표시(6~12자), 문의 번호 중 하나로 그 설치의 해시.
 * { device } | { missing } | { ambiguous }.
 */
export async function careTarget(d, env, q) {
  const s = (k) => (typeof q[k] === "string" ? q[k].trim() : "");
  const post = s("post");
  if (post) {
    const r = /^p[A-Za-z0-9]{15}$/.test(post) ? await d.one(env, "SELECT device FROM posts WHERE id = ?", post) : null;
    return r ? { device: r.device, ref: `post:${post}` } : { missing: true };
  }
  const recipe = s("recipe");
  if (recipe) {
    const code = recipe.toUpperCase();
    const r = /^[2-9A-HJ-NP-Z]{8}$/.test(code) ? await d.one(env, "SELECT owner FROM recipes WHERE code = ?", code) : null;
    return r ? { device: r.owner, ref: `recipe:${code}` } : { missing: true };
  }
  const inquiry = s("inquiry");
  if (inquiry) {
    const r = /^[A-Za-z0-9]{8,32}$/.test(inquiry) ? await d.one(env, "SELECT device FROM inquiries WHERE id = ?", inquiry) : null;
    return r ? { device: r.device, ref: `inquiry:${inquiry}` } : { missing: true };
  }
  const mark = s("mark").toLowerCase();
  if (/^[0-9a-f]{6,64}$/.test(mark)) {
    const like = `${mark}%`;
    const rows = await d.all(env, `SELECT DISTINCT device FROM (
      SELECT device FROM posts WHERE device LIKE ?1 UNION SELECT owner AS device FROM recipes WHERE owner LIKE ?1
      UNION SELECT device FROM inquiries WHERE device LIKE ?1 UNION SELECT device FROM write_limits WHERE device LIKE ?1
      UNION SELECT device FROM care_notes WHERE device LIKE ?1) LIMIT 3`, like);
    if (!rows.length) return { missing: true };
    if (rows.length > 1) return { ambiguous: rows.length };
    return { device: rows[0].device };
  }
  return { missing: true };
}

const noteView = (r, now) => ({
  id: r.id, kind: r.kind, reason: r.reason, reasonText: CARE_REASONS[r.reason] ?? r.reason, ...(r.note ? { note: r.note } : {}), by: r.by,
  at: isoOf(r.at), ...(r.days ? { days: r.days } : {}), ...(r.until ? { until: isoOf(r.until) } : {}), seen: Boolean(r.seen_at),
  ...(r.seen_at ? { seenAt: isoOf(r.seen_at) } : {}), ...(r.lifted_at ? { liftedAt: isoOf(r.lifted_at) } : {}),
  active: r.kind !== "warn" && !r.lifted_at && (r.until ?? 0) > now, ...(r.ref ? { ref: r.ref } : {}), who: r.device.slice(0, 12),
});

/** 관리자 앱 사람 하나: 기록, 가려진 글 수, 신고 누적 제한, 추천 다음 단계. 해시 전체는 내보내지 않아요. */
export async function carePerson(d, env, dev, now) {
  const [notes, strikes, limit, posts, appeals] = await Promise.all([
    d.all(env, "SELECT * FROM care_notes WHERE device = ? ORDER BY at DESC LIMIT 30", dev),
    d.one(env, "SELECT COUNT(*) AS n FROM write_strikes WHERE device = ? AND at > ?", dev, now - CARE.keepDays * DAY),
    d.one(env, "SELECT step, until, per_day FROM write_limits WHERE device = ? AND until > ?", dev, now),
    d.one(env, "SELECT COUNT(*) AS n, SUM(CASE WHEN status = 'hidden' THEN 1 ELSE 0 END) AS hidden FROM posts WHERE device = ?", dev),
    d.all(env, "SELECT target, result, inquiry_id FROM care_appeals WHERE device = ?", dev),
  ]);
  // 받아들인 해명의 경고·쉬기는 다음 단계를 셀 때 빼요.
  const accepted = new Set(appeals.filter((a) => a.result === "accepted").map((a) => a.target));
  return {
    who: dev.slice(0, 12), mark: dev.slice(0, 6), notes: notes.map((r) => noteView(r, now)), strikes: strikes?.n ?? 0,
    posts: posts?.n ?? 0, hiddenPosts: posts?.hidden ?? 0,
    limit: limit ? { step: limit.step, until: isoOf(limit.until), perDay: limit.per_day } : null,
    next: careNext(notes.filter((r) => !accepted.has(r.id)), now),
  };
}

/** 관리자 앱 목록: 지금 쉬는 사람, 최근 30일 경고(읽음 여부), 최근 30일에 끝난 쉬기. */
export async function careList(d, env, now) {
  const rows = await d.all(env, `SELECT * FROM care_notes WHERE (kind != 'warn' AND lifted_at IS NULL AND until > ?1) OR at > ?2 OR COALESCE(lifted_at, until, 0) > ?2
    ORDER BY at DESC LIMIT 200`, now, now - 30 * DAY);
  const all = rows.map((r) => noteView(r, now));
  return {
    on: await careOn(d, env, now),
    rules: { days: CARE.days, warnWithinDays: CARE.warnWithinDays, keepDays: CARE.keepDays, severe: CARE_SEVERE, reasons: CARE_REASONS },
    holds: all.filter((x) => x.active),
    warnings: all.filter((x) => x.kind === "warn"),
    ended: all.filter((x) => x.kind !== "warn" && !x.active),
  };
}

// ---- 1.39 [CARE-10] 해명하기: 문의로 해명하면 운영자가 읽고 풀어 줘요 ----
//
// 사용자 2026-11-07 "문의를 통해 사용자가 해명을 한다면 풀어주는 길도 열어줘야 해": 마을을 열 때의 안내 창과 쉬는 중 한 줄에 '해명하기'가
// 있어요. 쓴 글은 보통 문의(나만 보는 문의)로 들어가 운영자의 문의함에 '해명' 표시와 함께 오고, 운영자가 받아들이면 그 경고·쉬기·제한이
// 풀리고 그 건은 앞으로 세지 않아요. 쉬는 동안에도 해명은 언제나 보낼 수 있어요(쉬기는 해명을 보는 동안 그대로).

export const APPEAL = { min: 10, max: 1000, answerHours: 48 };

/** What [target] ("c…" a 경고·쉬기, or "limit" the 신고 누적 제한) is for this phone now, or null when there is nothing to explain. */
async function appealTarget(d, env, dev, target, now) {
  if (target === "limit") {
    const l = await d.one(env, "SELECT step, since, until FROM write_limits WHERE device = ? AND until > ?", dev, now);
    return l ? { kind: "limit", key: `limit:${l.since}`, text: `신고 누적 쓰기 제한 ${l.step}단계(${new Date(l.until).toISOString().slice(0, 10)}까지)` } : null;
  }
  // 1.39 조사 ③(4): 신고로(또는 운영자가) 가려진 내 글을 '다시 보여 주세요'. 받아들이면 글이 다시 보이고 그 건은 세지 않아요.
  const post = typeof target === "string" ? target.match(/^post:(p[A-Za-z0-9]{15})$/) : null;
  if (post) {
    const p = await d.one(env, "SELECT id, body, status FROM posts WHERE id = ? AND device = ?", post[1], dev);
    if (!p || p.status !== "hidden") return null;
    const head = [...p.body.replace(/\s+/g, " ")].slice(0, 40).join("");
    return { kind: "post", key: `post:${p.id}`, text: `가려진 글 다시 보여 주세요 · “${head}${[...p.body].length > 40 ? "…" : ""}”`, repost: true };
  }
  if (typeof target !== "string" || !/^c[A-Za-z0-9]{15}$/.test(target)) return null;
  const r = await d.one(env, "SELECT * FROM care_notes WHERE id = ? AND device = ? AND lifted_at IS NULL", target, dev);
  if (!r || (r.kind !== "warn" && (r.until ?? 0) <= now)) return null;
  const what = r.kind === "warn" ? (r.by === "auto" ? "마을 안내" : "마을 경고") : r.kind === "opinion" ? `의견 쉬기 ${r.days}일` : `마을 쉬기 ${r.days}일`;
  return { kind: r.kind, key: r.id, text: `${what} · ${CARE_REASONS[r.reason] ?? r.reason}` };
}

/**
 * POST /api/community/appeal {device, target, text}: 해명 하나를 나만 보는 문의로. 같은 것에는 한 번(이어 쓰기는 그 문의에서). 돌려주는 것:
 * { inquiry, answerHours }. [makeInquiry] index.js가 문의를 만드는 함수(문의 한도·알림을 그대로 따라요).
 */
export async function careAppeal(d, env, dev, body, now, makeInquiry) {
  // 해명 기록(care_appeals)은 처리방침 3.12의 '마을 경고·쉬기' 줄과 함께 시작해요. 그 전에는 보통 문의로 받아요.
  if (!(await careOn(d, env, now))) return { error: "off", status: 409 };
  const t = await appealTarget(d, env, dev, body.target, now);
  if (!t) return { error: "not_found", status: 404 };
  const text = typeof body.text === "string" ? body.text.normalize("NFC").replace(/\r\n?/g, "\n").trim() : "";
  if ([...text].length < APPEAL.min || [...text].length > APPEAL.max) return { error: "text", status: 400 };
  const had = await d.one(env, "SELECT inquiry_id FROM care_appeals WHERE device = ? AND target = ?", dev, t.key);
  if (had) return { error: "already", inquiry: had.inquiry_id, status: 409 };
  const made = await makeInquiry(`[${t.repost ? "다시 보여 주세요" : "해명"}] ${t.text}\n\n${text}`);
  if (made.error) return made;
  await d.run(env, "INSERT INTO care_appeals (id, device, target, inquiry_id, at) VALUES (?, ?, ?, ?, ?)", `a${d.randomId(15)}`, dev, t.key, made.id, now);
  return { inquiry: made.id, answerHours: APPEAL.answerHours };
}

/** 관리자 앱 문의 하나에 붙는 해명: 무엇에 대한 것인지, 지금 상태, 결정. 해명 문의가 아니면 null. */
export async function careAppealOf(d, env, inquiryId, now) {
  const a = await d.one(env, "SELECT * FROM care_appeals WHERE inquiry_id = ?", inquiryId);
  if (!a) return null;
  let about = null;
  if (a.target.startsWith("post:")) {
    const p = await d.one(env, "SELECT id, status, hide_reason, body FROM posts WHERE id = ?", a.target.slice(5));
    about = p ? { kind: "post", id: p.id, status: p.status, hideReason: p.hide_reason, body: [...p.body].slice(0, 300).join("") } : { kind: "gone" };
  } else if (a.target.startsWith("limit:")) {
    const l = await d.one(env, "SELECT step, since, until FROM write_limits WHERE device = ?", a.device);
    about = { kind: "limit", step: l?.step ?? null, until: isoOf(l?.until), active: Boolean(l && String(l.since) === a.target.slice(6) && l.until > now) };
  } else {
    const r = await d.one(env, "SELECT * FROM care_notes WHERE id = ?", a.target);
    about = r ? noteView(r, now) : { kind: "gone" };
  }
  return { id: a.id, at: isoOf(a.at), result: a.result, decidedAt: isoOf(a.decided_at), about, who: a.device.slice(0, 12) };
}

/**
 * 운영자의 결정. accept: 그 경고·쉬기는 거두고(세지 않음), 신고 누적 제한이면 지금 끝내고 그 제한을 만든 확인 건들을 지워 한 단계 내려요.
 * keep: 그대로. 답 글은 운영자가 문의 답으로 따로 써요(관리자 앱이 결과에 맞는 답 초안을 채워요).
 */
export async function careAppealDecide(d, env, inquiryId, result, now) {
  if (result !== "accepted" && result !== "kept") return { error: "result" };
  const a = await d.one(env, "SELECT * FROM care_appeals WHERE inquiry_id = ?", inquiryId);
  if (!a) return { error: "not_found" };
  const stmts = [d.stmt(env, "UPDATE care_appeals SET result = ?, decided_at = ? WHERE id = ?", result, now, a.id)];
  if (result === "accepted" && a.target.startsWith("post:")) {
    // 글을 다시 보이게 하는 것은 index.js가 운영자의 '다시 보이기'(moderatePost show)로 해요: 신고·세는 건·제한이 함께 풀려요.
    await d.batch(env, stmts);
    return { id: a.id, result, post: a.target.slice(5) };
  }
  if (result === "accepted") {
    if (a.target.startsWith("limit:")) {
      const since = Number(a.target.slice(6));
      stmts.push(
        d.stmt(env, "UPDATE write_limits SET until = MIN(until, ?), step = MAX(0, step - 1), times = MAX(0, times - 1) WHERE device = ? AND since = ?", now, a.device, since),
        d.stmt(env, "DELETE FROM write_strikes WHERE device = ? AND at <= ? AND at > ?", a.device, since, since - 60 * DAY),
        d.stmt(env, "DELETE FROM muted WHERE device = ? AND reason = 'strikes'", a.device),
        news(d, env, a.device, "lifted", now),
      );
    } else {
      const r = await d.one(env, "SELECT * FROM care_notes WHERE id = ?", a.target);
      if (r && !r.lifted_at) {
        await d.batch(env, stmts);
        await careLift(d, env, r.id, now);
        return { id: a.id, result };
      }
    }
  }
  await d.batch(env, stmts);
  return { id: a.id, result };
}
