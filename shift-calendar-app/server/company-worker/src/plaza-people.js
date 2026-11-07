// 1.39 [PLZC] 광장을 사람 먼저로 (사용자 2026-11-07 "광장은 급여를 나누는 것 말고는 하는 것이 전혀 없어 … 유형별 태그 … 준 블라인드").
// 설계: 프로젝트 계획/1.39-광장-커뮤니티-설계.md. 깃발 plaza.people(처리방침 3.12, 1-4항)이 켜지기 전에는 태그를 받지도 주지도 않아요.
//
// [PLZC-10] 근무 태그: a post in a kind of work's 광장 may carry what the writer's calendar says about how they work, as keys from the
// lists below only (no words of the writer's own), and the months since this server first saw the phone (device_seen, the server's
// own count; never the phone's). Kept on the post as it was when written, so a writer changing it shows.
// [PLZC-12] 머리 숫자: today's posts, questions still without an answer and writers at work in the last 3 hours, from posts kept anyway.
// [PLZC-03] 더 불러오기: a room's posts before a time, a page at a time.
import { DAY, HOUR } from "./units.js";

export const PEOPLE_COLUMNS = [
  // 1.39 [PLZC-10] 근무 태그 (JSON {s, f?, i?, m?, n?}) and its 근무형태 alone, for 나와 같은 근무.
  "ALTER TABLE posts ADD COLUMN tag TEXT",
  "ALTER TABLE posts ADD COLUMN tag_form TEXT",
  // 1.39 [PLZC-20] 근무 카드: this month so far by the writer's calendar (JSON {d, w, n, s, o}), no dates.
  "ALTER TABLE posts ADD COLUMN card TEXT",
  // 1.39 [PLZC-31] 근무형태별 투표 결과: the voter's 근무형태 at the time (none without a tag).
  "ALTER TABLE poll_votes ADD COLUMN form TEXT",
];

/** The kinds of work, as the app's WorkStyle names. */
export const PEOPLE_STYLES = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
/** The 근무형태 each kind of work may carry (core PlazaTags.formsFor). */
export const PEOPLE_FORMS = {
  SHIFT: ["THREE_SHIFT", "TWO_SHIFT", "FOUR_TEAM", "NURSE", "ON_CALL", "DAY_ON_DAY_OFF", "PUBLIC"],
  DAY: ["OFFICE", "ON_CALL"],
};
/** The 업종 무리 a writer may show (core IndustryGroups keys, 그 밖의 업종 left out); only for 교대·일근. */
export const PEOPLE_INDUSTRIES = ["fire", "coast", "police", "correction", "hospital", "rail", "airport", "transit", "watch", "power", "semi",
  "chem", "steel", "auto", "food", "logistics", "maker", "hotel", "weather"];
const PEOPLE_NOW_STYLES = ["SHIFT", "DAY", "FIELD", "HOURLY"];
/** Which kind of work each kind of work's room is for. */
const PEOPLE_ROOM_STYLE = { "style-shift": "SHIFT", "style-day": "DAY", "style-field": "FIELD", "style-shop": "BUSINESS",
  "style-hourly": "HOURLY", "style-invest": "INVEST" };
const PEOPLE_ROOMS = Object.keys(PEOPLE_ROOM_STYLE);
export const PEOPLE_LIMITS = { page: 60, workingHours: 3 };

/**
 * The tag a post in [room] carries, from what the app sent ([raw]); null for none. Keys off the lists are refused, as anything
 * malformed is; a tag whose kind of work is not the room's is refused too (교대 광장 takes only 교대 tags). [refuse] throws.
 */
export function peopleTag(raw, room, refuse) {
  if (raw === undefined || raw === null) return null;
  if (typeof raw !== "object" || Array.isArray(raw)) refuse("tag", "tag");
  const keys = Object.keys(raw);
  if (keys.some((k) => !["s", "f", "i", "n", "m"].includes(k))) refuse("tag", "tag");
  if (raw.m !== undefined && typeof raw.m !== "boolean") refuse("tag", "tag");
  const style = PEOPLE_ROOM_STYLE[room];
  if (!style || raw.s !== style) refuse("tag", "tag");
  const out = { s: style };
  if (raw.f !== undefined) {
    if (!(PEOPLE_FORMS[style] ?? []).includes(raw.f)) refuse("tag", "tag");
    out.f = raw.f;
  }
  if (raw.i !== undefined) {
    if (!["SHIFT", "DAY"].includes(style) || !PEOPLE_INDUSTRIES.includes(raw.i)) refuse("tag", "tag");
    out.i = raw.i;
  }
  if (raw.n !== undefined) {
    if (!PEOPLE_NOW_STYLES.includes(style) || !["night", "day"].includes(raw.n)) refuse("tag", "tag");
    out.n = raw.n;
  }
  // m: true asks the server to add the months it has seen this phone (the writer may hide them).
  if (raw.m === true) Object.defineProperty(out, "wantsMonths", { value: true, enumerable: false });
  return out;
}

/** Whole months from [first] to [now] (0 within the first month); null without a first day. */
export function peopleMonths(first, now) {
  if (!Number.isFinite(first) || first > now) return null;
  return Math.floor((now - first) / (30 * DAY));
}

/** A post's tag as the app reads it, or null (an old post, a company's room, or the flag off). */
export function peopleTagOut(row, on) {
  if (!on || !row.tag) return null;
  try {
    const t = JSON.parse(row.tag);
    return t && typeof t === "object" && typeof t.s === "string" ? t : null;
  } catch {
    return null;
  }
}

/**
 * [PLZC-15][PLZC-16] For the writers of a page ([devices], install hashes): when each first wrote in any kind of work's plaza (a post
 * then is their 첫 글), and how many of their answers someone else's question picked as 도움 됐어요. Read 90 at a time (D1 takes 100 values).
 */
export async function peopleFacts(all, env, devices) {
  const first = new Map();
  const helped = new Map();
  const list = [...new Set(devices)];
  for (let i = 0; i < list.length; i += 90) {
    const part = list.slice(i, i + 90);
    const q = part.map(() => "?").join(",");
    const [a, b] = await Promise.all([
      all(env, `SELECT device, MIN(created_at) AS first FROM posts WHERE grp = 'room' AND company_id IN (${PEOPLE_ROOMS.map((r) => `'${r}'`).join(",")})
        AND parent IS NULL AND device IN (${q}) GROUP BY device`, ...part),
      all(env, `SELECT a.device, COUNT(*) AS n FROM posts q JOIN posts a ON a.id = q.answer WHERE q.answer IS NOT NULL AND q.status = 'shown'
        AND a.status = 'shown' AND a.device <> q.device AND a.device IN (${q}) GROUP BY a.device`, ...part),
    ]);
    for (const r of a) first.set(r.device, r.first);
    for (const r of b) helped.set(r.device, r.n);
  }
  return { first, helped };
}

/** [PLZC-12] The room's numbers for its head line, from its kept posts: since KST midnight, open questions, writers at work lately. */
export async function peoplePulse(one, env, room, now, grp) {
  const midnight = Math.floor((now + 9 * HOUR) / DAY) * DAY - 9 * HOUR;
  const r = await one(env, `SELECT
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = ?2 AND status = 'shown' AND parent IS NULL AND created_at >= ?3) AS today,
      (SELECT COUNT(*) FROM posts q WHERE q.company_id = ?1 AND q.grp = ?2 AND q.status = 'shown' AND q.parent IS NULL AND q.kind = 'question'
        AND q.created_at >= ?5
        AND NOT EXISTS (SELECT 1 FROM posts a WHERE a.parent = q.id AND a.status = 'shown' AND a.device <> q.device)) AS waiting,
      (SELECT COUNT(DISTINCT device) FROM posts WHERE company_id = ?1 AND grp = ?2 AND status = 'shown' AND created_at >= ?4
        AND tag LIKE '%"n":%') AS working`,
  room, grp, midnight, now - PEOPLE_LIMITS.workingHours * HOUR, now - 14 * DAY);
  return { today: r?.today ?? 0, waiting: r?.waiting ?? 0, working: r?.working ?? 0 };
}

/** 1.39 [PLZC-31] Fewest voters a 근무형태 needs to be shown apart; fewer are put together as 그 밖의 근무 (or left out). */
export const POLL_SPLIT_MIN = 5;

/**
 * 1.39 [PLZC-31] 근무형태별 투표 결과 of the polls [ids]: Map id → [{f, counts}] (f null: 그 밖의 근무), only groups of POLL_SPLIT_MIN or
 * more, and only where at least one 근무형태 stands on its own. Nobody's own vote shows.
 */
export async function pollByForm(all, env, ids, options) {
  const out = new Map();
  if (!ids.length) return out;
  const rows = await all(env, `SELECT post_id, form, choice, COUNT(*) AS n FROM poll_votes WHERE post_id IN (${ids.map(() => "?").join(",")})
    GROUP BY post_id, form, choice`, ...ids);
  for (const id of ids) {
    const size = options.get(id) ?? 0;
    const groups = new Map();
    for (const r of rows.filter((x) => x.post_id === id && x.choice < size)) {
      const g = groups.get(r.form ?? "") ?? Array(size).fill(0);
      g[r.choice] += r.n;
      groups.set(r.form ?? "", g);
    }
    const sum = (a) => a.reduce((x, y) => x + y, 0);
    const named = [...groups].filter(([f, c]) => f && sum(c) >= POLL_SPLIT_MIN).sort((a, b) => sum(b[1]) - sum(a[1]));
    if (!named.length) continue;
    const rest = Array(size).fill(0);
    for (const [f, c] of groups) if (!named.some(([g]) => g === f)) c.forEach((n, i) => { rest[i] += n; });
    out.set(id, [...named.map(([f, counts]) => ({ f, counts })), ...(sum(rest) >= POLL_SPLIT_MIN ? [{ f: null, counts: rest }] : [])]);
  }
  return out;
}

/** 1.39 [PLZC-20] The kinds of work whose plaza takes a 근무 카드 (the ones that count days at work). */
export const CARD_STYLES = ["SHIFT", "DAY", "FIELD", "HOURLY"];

/**
 * 1.39 [PLZC-20] 근무 카드: this month so far by the writer's calendar, numbers only. d days of the month gone, w days at work, n of them
 * nights, s the longest run of days at work, o days off. Anything else is refused.
 */
export function peopleCard(raw, room, refuse) {
  if (raw === undefined || raw === null) return null;
  if (typeof raw !== "object" || Array.isArray(raw) || !CARD_STYLES.includes(PEOPLE_ROOM_STYLE[room])) refuse("card");
  const keys = ["d", "w", "n", "s", "o"];
  if (Object.keys(raw).some((k) => !keys.includes(k))) refuse("card");
  const c = {};
  for (const k of keys) {
    const v = raw[k];
    if (!Number.isInteger(v) || v < 0 || v > 31) refuse("card");
    c[k] = v;
  }
  if (c.d < 1 || c.w + c.o > c.d || c.n > c.w || c.s > c.w) refuse("card");
  return c;
}
