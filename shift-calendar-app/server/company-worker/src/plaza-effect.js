import { all, iso, json } from "./index.js";
import { checkinDay } from "./plaza-checkin.js";
import { DAY } from "./units.js";

// ---- 1.39 [PLZC-33] 광장 효과를 재는 눈 (관리자 앱 › 커뮤니티 › 마을 운영) ----
//
// Sums only, per kind of work's plaza: posts a day (this 30 days and the 30 before, to see 처리방침 3.12 against 1.38), questions and how
// many got an answer from someone else and how fast, posts with a 근무 태그, 나도요, 오늘 출근, and whether first-time writers came back
// to write again within 14 days. No one is named or counted alone; the 1.40 논의 starts from these numbers.

export const EFFECT_LIMITS = { days: 30, back: 14, questions: 3000 };

const effectMedian = (xs) => {
  if (!xs.length) return null;
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : Math.round((s[m - 1] + s[m]) / 2);
};

/** One plaza's numbers over the [days] before [now]. */
async function roomEffect(env, room, now) {
  const span = EFFECT_LIMITS.days * DAY;
  const from = now - span;
  const [r] = await all(env, `SELECT
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = 'room' AND parent IS NULL AND created_at >= ?2) AS posts,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = 'room' AND parent IS NULL AND created_at >= ?3 AND created_at < ?2) AS before,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = 'room' AND parent IS NOT NULL AND created_at >= ?2) AS answers,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = 'room' AND created_at >= ?2 AND tag IS NOT NULL) AS tagged,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = 'room' AND created_at >= ?2) AS all_posts,
      (SELECT COALESCE(SUM(agree), 0) FROM posts WHERE company_id = ?1 AND grp = 'room' AND created_at >= ?2) AS metoo,
      (SELECT COUNT(DISTINCT device) FROM posts WHERE company_id = ?1 AND grp = 'room' AND created_at >= ?2) AS writers,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = 'room' AND created_at >= ?2 AND card IS NOT NULL) AS cards`,
  room, from, from - span);
  // Questions and their first answer from someone else.
  const qs = await all(env, `SELECT q.created_at AS at, (SELECT MIN(a.created_at) FROM posts a WHERE a.parent = q.id AND a.device <> q.device
      AND a.status = 'shown') AS first FROM posts q WHERE q.company_id = ? AND q.grp = 'room' AND q.parent IS NULL AND q.kind = 'question'
      AND q.created_at >= ? ORDER BY q.created_at DESC LIMIT ${EFFECT_LIMITS.questions}`, room, from);
  const answered = qs.filter((q) => q.first);
  // First-time writers of this plaza 14~44 days ago: did they write again within 14 days?
  const firsts = await all(env, `SELECT f.device, f.first, (SELECT COUNT(*) FROM posts p WHERE p.company_id = ?1 AND p.grp = 'room'
      AND p.device = f.device AND p.created_at > f.first AND p.created_at <= f.first + ?4) AS again
    FROM (SELECT device, MIN(created_at) AS first FROM posts WHERE company_id = ?1 AND grp = 'room' GROUP BY device) f
    WHERE f.first >= ?2 AND f.first < ?3`, room, now - (EFFECT_LIMITS.back + EFFECT_LIMITS.days) * DAY, now - EFFECT_LIMITS.back * DAY,
  EFFECT_LIMITS.back * DAY);
  const checkins = await all(env, "SELECT day, kind, COUNT(*) AS n FROM plaza_checkin WHERE room = ? GROUP BY day, kind", room);
  const today = checkinDay(now);
  return {
    postsPerDay: Math.round(((r?.posts ?? 0) / EFFECT_LIMITS.days) * 10) / 10,
    postsPerDayBefore: Math.round(((r?.before ?? 0) / EFFECT_LIMITS.days) * 10) / 10,
    posts: r?.posts ?? 0, answers: r?.answers ?? 0, writers: r?.writers ?? 0, metoo: r?.metoo ?? 0, cards: r?.cards ?? 0,
    tagged: r?.all_posts ? Math.round((r.tagged / r.all_posts) * 100) : null,
    questions: qs.length,
    answeredPct: qs.length ? Math.round((answered.length / qs.length) * 100) : null,
    firstAnswerMin: effectMedian(answered.map((q) => Math.round((q.first - q.at) / 60000))),
    newWriters: firsts.length,
    cameBackPct: firsts.length ? Math.round((firsts.filter((f) => f.again > 0).length / firsts.length) * 100) : null,
    checkinToday: checkins.filter((c) => c.day === today).reduce((s, c) => s + c.n, 0),
  };
}

/** GET /api/admin/plaza/effect: every kind of work's plaza, sums only. */
export async function plazaEffectAdmin(request, env, path, now, rooms) {
  if (path !== "/plaza/effect" || request.method !== "GET") return null;
  const items = [];
  for (const [room, name] of Object.entries(rooms)) items.push({ room, name, ...(await roomEffect(env, room, now)) });
  return json({ at: iso(now), days: EFFECT_LIMITS.days, back: EFFECT_LIMITS.back, items });
}


