import { batch, iso, json, stmt } from "./index.js";
import { checkinDay } from "./plaza-checkin.js";
import { DAY } from "./units.js";

// ---- 1.39 [PLZC-33] 광장 효과를 재는 눈 (관리자 앱 › 커뮤니티 › 마을 운영) ----
//
// Sums only, per kind of work's plaza: posts a day (this 30 days and the 30 before, to see 처리방침 3.12 against 1.38), questions and how
// many got an answer from someone else and how fast, posts with a 근무 태그, 나도요, 오늘 출근, and whether first-time writers came back
// to write again within 14 days. No one is named or counted alone; the 1.40 논의 starts from these numbers.
// 2.0.0 [RVN-24] 검토 SVC-95: only what is shown counts (a run of 도배 hidden by the server or a post hidden after reports no longer makes
// '글이 늘었어요'); what was hidden is a number of its own. The 태그 share is of top posts, and a share of fewer than [EFFECT_LIMITS.few]
// comes back null with its sample so the developer app says '아직 적음'. SVC-106: every plaza's reads go in one batch.

export const EFFECT_LIMITS = { days: 30, back: 14, questions: 3000, few: 10 };

const effectMedian = (xs) => {
  if (!xs.length) return null;
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : Math.round((s[m - 1] + s[m]) / 2);
};

/** [part] of [whole] in %, or null under [EFFECT_LIMITS.few] (아직 적음). */
const share = (part, whole) => (whole >= EFFECT_LIMITS.few ? Math.round((part / whole) * 100) : null);

/** The four reads of one plaza's numbers over the [days] before [now], as statements for one batch. */
function roomReads(env, room, now) {
  const span = EFFECT_LIMITS.days * DAY;
  const from = now - span;
  const shown = "grp = 'room' AND status = 'shown'";
  return [
    stmt(env, `SELECT
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND ${shown} AND parent IS NULL AND created_at >= ?2) AS posts,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND ${shown} AND parent IS NULL AND created_at >= ?3 AND created_at < ?2) AS before,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND ${shown} AND parent IS NOT NULL AND created_at >= ?2) AS answers,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND ${shown} AND parent IS NULL AND created_at >= ?2 AND tag IS NOT NULL) AS tagged,
      (SELECT COALESCE(SUM(agree), 0) FROM posts WHERE company_id = ?1 AND ${shown} AND created_at >= ?2) AS metoo,
      (SELECT COUNT(DISTINCT device) FROM posts WHERE company_id = ?1 AND ${shown} AND created_at >= ?2) AS writers,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND ${shown} AND created_at >= ?2 AND card IS NOT NULL) AS cards,
      (SELECT COUNT(*) FROM posts WHERE company_id = ?1 AND grp = 'room' AND status <> 'shown' AND created_at >= ?2) AS hidden`,
    room, from, from - span),
    // Questions and their first answer from someone else.
    stmt(env, `SELECT q.created_at AS at, (SELECT MIN(a.created_at) FROM posts a WHERE a.parent = q.id AND a.device <> q.device
      AND a.status = 'shown') AS first FROM posts q WHERE q.company_id = ? AND q.grp = 'room' AND q.status = 'shown' AND q.parent IS NULL
      AND q.kind = 'question' AND q.created_at >= ? ORDER BY q.created_at DESC LIMIT ${EFFECT_LIMITS.questions}`, room, from),
    // First-time writers of this plaza 14~44 days ago (by what is shown): did they write again within 14 days?
    stmt(env, `SELECT f.device, f.first, (SELECT COUNT(*) FROM posts p WHERE p.company_id = ?1 AND p.${shown}
      AND p.device = f.device AND p.created_at > f.first AND p.created_at <= f.first + ?4) AS again
    FROM (SELECT device, MIN(created_at) AS first FROM posts WHERE company_id = ?1 AND ${shown} GROUP BY device) f
    WHERE f.first >= ?2 AND f.first < ?3`, room, now - (EFFECT_LIMITS.back + EFFECT_LIMITS.days) * DAY, now - EFFECT_LIMITS.back * DAY,
    EFFECT_LIMITS.back * DAY),
    stmt(env, "SELECT COUNT(*) AS n FROM plaza_checkin WHERE room = ? AND day = ?", room, checkinDay(now)),
  ];
}

/** One plaza's numbers from its four reads. */
function roomEffect([sums, questions, firsts, checkins]) {
  const r = sums.results?.[0] ?? {};
  const qs = questions.results ?? [];
  const fs = firsts.results ?? [];
  const answered = qs.filter((q) => q.first);
  return {
    postsPerDay: Math.round(((r.posts ?? 0) / EFFECT_LIMITS.days) * 10) / 10,
    postsPerDayBefore: Math.round(((r.before ?? 0) / EFFECT_LIMITS.days) * 10) / 10,
    posts: r.posts ?? 0, answers: r.answers ?? 0, writers: r.writers ?? 0, metoo: r.metoo ?? 0, cards: r.cards ?? 0, hidden: r.hidden ?? 0,
    tagged: share(r.tagged ?? 0, r.posts ?? 0),
    questions: qs.length,
    answeredPct: share(answered.length, qs.length),
    firstAnswerMin: effectMedian(answered.map((q) => Math.round((q.first - q.at) / 60000))),
    newWriters: fs.length,
    cameBackPct: share(fs.filter((f) => f.again > 0).length, fs.length),
    checkinToday: checkins.results?.[0]?.n ?? 0,
  };
}

/** GET /api/admin/plaza/effect: every kind of work's plaza, sums only. */
export async function plazaEffectAdmin(request, env, path, now, rooms) {
  if (path !== "/plaza/effect" || request.method !== "GET") return null;
  const list = Object.entries(rooms);
  const read = await batch(env, list.flatMap(([room]) => roomReads(env, room, now)));
  const items = list.map(([room, name], i) => ({ room, name, ...roomEffect(read.slice(i * 4, i * 4 + 4)) }));
  return json({ at: iso(now), days: EFFECT_LIMITS.days, back: EFFECT_LIMITS.back, few: EFFECT_LIMITS.few, items });
}


