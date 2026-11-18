// ---- 2.0.0 갈래 N [RVN-14] HQ-51 알림함: 내 것에 온 반응 (사용자 11-12 20:38~20:40 KST) ----
//
// What others did to what this phone put out since [since], one line per thing (a post, a reported rule set, a rate, a request,
// a contest entry), so the app's 알림함 shows "내 글에 답 3" as one row that opens that post, and counts it on the bell. Worked out
// from what the server already keeps (every vote, like and answer has its time): nothing new is stored and the phone sends nothing
// new (its install id and since, as 밤낮 소식 already does). Who did it is never said, only how many; writers this phone blocked
// are left out of answers. Rides on /api/digest, the one ask the app already makes.

import { all, STYLE_ROOMS } from "./index.js";
import { makerId } from "./recipes.js";

/** The most rows of each kind, newest first. */
export const REACTIONS_MAX = 20;

/** Answers read to group them in JS (a blocked writer's are left out one by one). */
const ANSWER_ROWS = 300;

/**
 * The lines, newest first. Every line has {kind, target, title, count, at}: the thing's id, its name as the row says it, how many
 * reactions since and the last one's time (the same shape as 내 작품 반응, /api/recipes/makers/me/inbox, HQ-52). Then by kind:
 * - answer {post, room, setup}: answers under my post (a kind of work's 광장, a company's room, a rule's talk; ①②), by post so the
 *   row opens it (RVN-06 ?post=).
 * - thread {post, room, setup}: new answers, after my first, under someone else's post I answered (⑦).
 * - agree {post, room, setup, postKind?, up, down}: 나도요·공감 on my post, 맞아요·달라요 on my proposal (③); a post with only 비공감
 *   is left out, and a reported rule set's own post goes as report_vote instead, not twice.
 * - report_vote {company, setup, dept, up, down}: 맞아요·달라요 on a rule set I reported (④); target "<company>/<setup>".
 * - rate_vote {rate, trade, region, up, down}: on a rate I sent (④).
 * - request_like {request, body}, request_take {request, body}: on a 부탁 I wrote (⑤).
 * - contest_pick {code, month, theme}: my entry was picked in 이달의 공모 (⑥).
 */
export async function reactionItems(env, dev, since, now) {
  const [answers, threads, votes, reportVotes, rateVotes, likes, takes, picks, blockRows] = await Promise.all([
    all(env, `SELECT q.id, q.company_id, q.grp, c.name, a.device, a.created_at AS at FROM posts q
        JOIN posts a ON a.parent = q.id AND a.status = 'shown' AND a.device != q.device AND a.created_at > ? AND a.created_at <= ?
        LEFT JOIN companies c ON c.id = q.company_id
      WHERE q.device = ? AND q.status = 'shown' AND q.parent IS NULL AND q.company_id != 'recipe'
      ORDER BY a.created_at DESC LIMIT ${ANSWER_ROWS}`, since, now, dev),
    // Under a post I answered: what came after my first answer there, from anyone but me (its writer's thanks too).
    all(env, `SELECT q.id, q.company_id, q.grp, c.name, a.device, a.created_at AS at FROM posts q
        JOIN posts a ON a.parent = q.id AND a.status = 'shown' AND a.device != ?3 AND a.created_at > ?1 AND a.created_at <= ?2
        LEFT JOIN companies c ON c.id = q.company_id
      WHERE q.id IN (SELECT parent FROM posts WHERE device = ?3 AND parent IS NOT NULL AND status = 'shown')
        AND q.status = 'shown' AND q.device != ?3 AND q.company_id != 'recipe'
        AND a.created_at > (SELECT MIN(m.created_at) FROM posts m WHERE m.parent = q.id AND m.device = ?3 AND m.status = 'shown')
      ORDER BY a.created_at DESC LIMIT ${ANSWER_ROWS}`, since, now, dev),
    all(env, `SELECT p.id, p.company_id, p.grp, p.kind, c.name, SUM(v.ok = 1) AS up, SUM(v.ok = 0) AS down, MAX(v.at) AS at FROM posts p
        JOIN post_votes v ON v.post_id = p.id AND v.device != p.device AND v.at > ? AND v.at <= ?
        LEFT JOIN companies c ON c.id = p.company_id
      WHERE p.device = ? AND p.status = 'shown' AND p.report_id IS NULL AND p.company_id != 'recipe'
      GROUP BY p.id ORDER BY at DESC LIMIT ${REACTIONS_MAX}`, since, now, dev),
    all(env, `SELECT r.company_id, c.name, r.grp, MAX(r.dept) AS dept, SUM(v.ok = 1) AS up, SUM(v.ok = 0) AS down, MAX(v.at) AS at FROM reports r
        JOIN votes v ON v.company_id = r.company_id AND v.grp = r.grp AND v.device != r.device AND v.at > ? AND v.at <= ?
        LEFT JOIN companies c ON c.id = r.company_id
      WHERE r.device = ? AND r.status IN ('pending', 'adopted')
      GROUP BY r.company_id, r.grp ORDER BY at DESC LIMIT ${REACTIONS_MAX}`, since, now, dev),
    all(env, `SELECT r.id, r.trade, r.region, SUM(v.ok = 1) AS up, SUM(v.ok = 0) AS down, MAX(v.at) AS at FROM rates r
        JOIN rate_votes v ON v.rate_id = r.id AND v.device != r.device AND v.at > ? AND v.at <= ?
      WHERE r.device = ? GROUP BY r.id ORDER BY at DESC LIMIT ${REACTIONS_MAX}`, since, now, dev),
    all(env, `SELECT q.id, q.body, COUNT(*) AS n, MAX(l.at) AS at FROM recipe_requests q
        JOIN recipe_request_likes l ON l.id = q.id AND l.device != q.device AND l.at > ? AND l.at <= ?
      WHERE q.device = ? AND q.hidden = 0 GROUP BY q.id ORDER BY at DESC LIMIT ${REACTIONS_MAX}`, since, now, dev),
    all(env, `SELECT id, body, updated_at AS at FROM recipe_requests
      WHERE device = ? AND state = 'making' AND maker IS NOT NULL AND maker != device AND hidden = 0 AND updated_at > ? AND updated_at <= ?
      ORDER BY updated_at DESC LIMIT ${REACTIONS_MAX}`, dev, since, now),
    all(env, `SELECT e.code, e.month, c.theme, c.picks, c.at, r.title FROM village_entries e
        JOIN village_contests c ON c.month = e.month AND c.style = e.style AND c.at > ? AND c.at <= ?
        LEFT JOIN recipes r ON r.code = e.code
      WHERE e.device = ? ORDER BY c.at DESC LIMIT ${REACTIONS_MAX}`, since, now, dev),
    all(env, "SELECT maker FROM recipe_blocks WHERE device = ?", dev).catch(() => []),
  ]);
  const blocked = new Set(blockRows.map((b) => b.maker));
  const iso = (ms) => new Date(ms).toISOString();
  // Answers by post, a blocked writer's left out (as the post's own read leaves them out, RVN-04).
  const byPost = async (rows) => {
    const out = new Map();
    for (const a of rows) {
      if (blocked.size && blocked.has(await makerId(a.device))) continue;
      const line = out.get(a.id) ?? { post: a.id, room: a.company_id, setup: a.grp, ...(a.name ? { company: a.name } : {}), count: 0, at: a.at };
      line.count += 1;
      line.at = Math.max(line.at, a.at);
      out.set(a.id, line);
    }
    return [...out.values()].slice(0, REACTIONS_MAX).map((l) => ({ ...l, at: iso(l.at) }));
  };
  const place = (room, name) => STYLE_ROOMS[room] ?? name ?? "";
  const items = [
    ...(await byPost(answers)).map((l) => ({ kind: "answer", target: l.post, title: place(l.room, l.company), ...l })),
    ...(await byPost(threads)).map((l) => ({ kind: "thread", target: l.post, title: place(l.room, l.company), ...l })),
    // A 비공감 alone is not news to bring to someone; on a proposal both count (모이면 규칙이 바뀌어요).
    ...votes.filter((v) => (v.up ?? 0) > 0 || v.kind === "proposal").map((v) => ({ kind: "agree", target: v.id, title: place(v.company_id, v.name),
      count: (v.up ?? 0) + (v.down ?? 0), post: v.id, room: v.company_id, setup: v.grp, ...(v.name ? { company: v.name } : {}),
      ...(v.kind && v.kind !== "talk" ? { postKind: v.kind } : {}), up: v.up ?? 0, down: v.down ?? 0, at: iso(v.at) })),
    ...reportVotes.map((v) => ({ kind: "report_vote", target: `${v.company_id}/${v.grp}`, title: v.name ?? "", count: (v.up ?? 0) + (v.down ?? 0),
      company: { id: v.company_id, name: v.name ?? "" }, setup: v.grp, dept: v.dept ?? "", up: v.up ?? 0, down: v.down ?? 0, at: iso(v.at) })),
    ...rateVotes.map((v) => ({ kind: "rate_vote", target: v.id, title: v.trade, count: (v.up ?? 0) + (v.down ?? 0),
      rate: v.id, trade: v.trade, region: v.region, up: v.up ?? 0, down: v.down ?? 0, at: iso(v.at) })),
    ...likes.map((l) => ({ kind: "request_like", target: l.id, title: l.body.slice(0, 60), count: l.n, request: l.id, body: l.body.slice(0, 60), at: iso(l.at) })),
    ...takes.map((t) => ({ kind: "request_take", target: t.id, title: t.body.slice(0, 60), count: 1, request: t.id, body: t.body.slice(0, 60), at: iso(t.at) })),
    ...picks.filter((p) => JSON.parse(p.picks).includes(p.code))
      .map((p) => ({ kind: "contest_pick", target: p.code, title: p.title ?? "", count: 1, code: p.code, month: p.month, theme: p.theme, at: iso(p.at) })),
  ];
  return items.sort((a, b) => b.at.localeCompare(a.at));
}
