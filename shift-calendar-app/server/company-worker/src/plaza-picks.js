import { all, batch, iso, json, one, randomId, readJson, run, stmt } from "./index.js";
import { PEOPLE_FORMS } from "./plaza-people.js";
import { DAY } from "./units.js";

// ---- 1.39 [PLZC-22][PLZC-23] 광장 답 모음: 오래 남는 선배 답 ----
//
// The operator picks, from 관리자 앱 › 커뮤니티 › 마을 운영, answers that helped (질문한 분이 '도움 됐어요', or 나도요 3+) and gives each a
// short title; the app shows them in its kind of work's 광장 as '선배 답 모음' (my 근무형태 first where the question carried one). Only ids are kept: an answer or question hidden or deleted later drops out by itself. Nothing new about anyone.

export const PLAZA_PICKS_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS plaza_picks (id TEXT PRIMARY KEY, room TEXT NOT NULL, question TEXT NOT NULL, answer TEXT NOT NULL,
    title TEXT NOT NULL, form TEXT, at INTEGER NOT NULL)`,
  "CREATE UNIQUE INDEX IF NOT EXISTS plaza_picks_answer ON plaza_picks (answer)",
  "CREATE INDEX IF NOT EXISTS plaza_picks_room ON plaza_picks (room, at)",
];
// 2.0.0 [RVN-24] 검토 I22cb-02: a pick may name what it is about, so the app's 도움말 on that subject shows '같은 근무 분들의 답'.
export const PICKS_COLUMNS = ["ALTER TABLE plaza_picks ADD COLUMN topic TEXT"];
export const PICK_TOPICS = { alarm: "알람", pay: "급여", leave: "연차·휴가", sleep: "수면", roster: "근무표", money: "가계부·돈", health: "건강" };
export const PICKS_LIMITS = { title: 40, perRoom: 30, candidateDays: 120, candidates: 30, agree: 3 };
const PICK_ROOMS = { "style-shift": "SHIFT", "style-day": "DAY", "style-field": "FIELD", "style-shop": "BUSINESS", "style-hourly": "HOURLY", "style-invest": "INVEST" };

/** The picks of [room] still standing (both posts shown), newest first: {id, title, form?, topic?, question, answer}. */
export async function plazaPicks(env, room) {
  const rows = await all(env, `SELECT k.id, k.title, k.form, k.topic, k.at, q.body AS qbody, a.body AS abody FROM plaza_picks k
    JOIN posts q ON q.id = k.question AND q.status = 'shown' JOIN posts a ON a.id = k.answer AND a.status = 'shown'
    WHERE k.room = ? ORDER BY k.at DESC LIMIT ${PICKS_LIMITS.perRoom}`, room);
  return rows.map((r) => ({ id: r.id, title: r.title, ...(r.form ? { form: r.form } : {}), ...(r.topic ? { topic: r.topic } : {}),
    question: r.qbody, answer: r.abody }));
}

/** GET /api/plaza/picks?room=style-shift — the app's 선배 답 모음 (once plaza.people is on). */
export async function plazaPicksRoute(request, env, url, on) {
  const room = url.searchParams.get("room");
  if (!Object.hasOwn(PICK_ROOMS, room)) return json({ error: "room", field: "room" }, 400);
  if (!on) return json({ items: [] });
  return json({ items: await plazaPicks(env, room) }, 200, { "cache-control": "private, max-age=300" });
}

/**
 * The operator's side under /api/admin: GET /plaza/picks (each room's picks and answers worth picking), POST /plaza/picks
 * {answer, title, topic?} (the question and its 근무형태 come from the posts; an answer already picked gets the new title and topic),
 * DELETE /plaza/picks/:id. Null for other paths. [log](action, target, detail) writes 활동 기록.
 * 2.0.0 [RVN-24] 검토 SVC-96·U22c-06: a full room (30) still takes a new title for a pick it has, and says which pick is oldest so the
 * operator can make room; 활동 기록 says which answer and under what title; a pick whose answer or question is gone for good (the writer
 * deleted it) leaves the list by itself and the next listing says so once. SVC-106: every room's picks and candidates in one batch.
 */
export async function plazaPicksAdmin(request, env, path, now, rooms, log = async () => {}) {
  if (path === "/plaza/picks" && request.method === "GET") {
    const since = now - PICKS_LIMITS.candidateDays * DAY;
    const list = Object.entries(rooms);
    // Gone for good: the answer's or the question's row is not there any more (a hidden one may be shown again, so it stays, marked).
    const gone = await all(env, `SELECT k.id, k.room, k.title FROM plaza_picks k LEFT JOIN posts a ON a.id = k.answer LEFT JOIN posts q ON q.id = k.question
      WHERE a.id IS NULL OR q.id IS NULL`);
    if (gone.length) {
      await batch(env, gone.map((g) => stmt(env, "DELETE FROM plaza_picks WHERE id = ?", g.id)));
      for (const g of gone) await log("plaza_pick_gone", g.room, `${g.id} ‘${g.title}’`);
    }
    const read = await batch(env, list.flatMap(([room]) => [
      stmt(env, `SELECT k.id, k.title, k.form, k.topic, k.at, k.answer AS aid, a.body AS abody, a.status AS astatus FROM plaza_picks k
        LEFT JOIN posts a ON a.id = k.answer WHERE k.room = ? ORDER BY k.at DESC`, room),
      // Answers the asker chose, or 나도요 3+, under a question still shown, not picked yet.
      stmt(env, `SELECT a.id, a.body AS abody, a.agree, a.created_at, q.id AS qid, q.body AS qbody, q.tag_form,
        (q.answer = a.id) AS chosen FROM posts a JOIN posts q ON q.id = a.parent
        WHERE q.company_id = ? AND q.grp = 'room' AND q.kind = 'question' AND q.status = 'shown' AND a.status = 'shown' AND a.device <> q.device
          AND a.created_at >= ? AND (q.answer = a.id OR a.agree >= ?) AND NOT EXISTS (SELECT 1 FROM plaza_picks k WHERE k.answer = a.id)
        ORDER BY (q.answer = a.id) DESC, a.agree DESC, a.created_at DESC LIMIT ${PICKS_LIMITS.candidates}`, room, since, PICKS_LIMITS.agree),
    ]));
    const items = list.map(([room, name], i) => {
      const picks = read[i * 2].results ?? [];
      const candidates = read[i * 2 + 1].results ?? [];
      return {
        room, name, full: picks.length >= PICKS_LIMITS.perRoom,
        picks: picks.map((p) => ({ id: p.id, title: p.title, form: p.form, ...(p.topic ? { topic: p.topic } : {}), at: iso(p.at), answerId: p.aid,
          answer: p.abody ?? "", gone: p.astatus !== "shown" })),
        candidates: candidates.map((c) => ({ answer: c.id, question: c.qbody, body: c.abody, agree: c.agree, chosen: Boolean(c.chosen),
          form: c.tag_form, at: iso(c.created_at) })),
      };
    });
    return json({ items, forms: PEOPLE_FORMS, topics: PICK_TOPICS, perRoom: PICKS_LIMITS.perRoom,
      removed: gone.map((g) => ({ room: g.room, title: g.title })) });
  }
  if (path === "/plaza/picks" && request.method === "POST") {
    const body = await readJson(request, 1024);
    const title = typeof body?.title === "string" ? body.title.replace(/\s+/g, " ").trim() : "";
    if (!title || title.length > PICKS_LIMITS.title) return json({ error: "title", field: "title" }, 400);
    const topic = body.topic === undefined || body.topic === null || body.topic === "" ? null : body.topic;
    if (topic !== null && !Object.hasOwn(PICK_TOPICS, topic)) return json({ error: "topic", field: "topic" }, 400);
    const a = typeof body.answer === "string" ? await one(env, `SELECT a.id, a.status, q.id AS qid, q.kind, q.status AS qstatus, q.company_id,
      q.tag_form, (SELECT k.id FROM plaza_picks k WHERE k.answer = a.id) AS picked FROM posts a JOIN posts q ON q.id = a.parent WHERE a.id = ?`, body.answer) : null;
    if (!a || a.status !== "shown" || a.qstatus !== "shown" || a.kind !== "question" || !Object.hasOwn(rooms, a.company_id)) {
      return json({ error: "answer", field: "answer" }, 400);
    }
    // A pick already there only changes its title (and topic): the room's count stays as it is.
    if (!a.picked) {
      const count = await one(env, `SELECT COUNT(*) AS n, (SELECT id FROM plaza_picks WHERE room = ?1 ORDER BY at LIMIT 1) AS oldest,
        (SELECT title FROM plaza_picks WHERE room = ?1 ORDER BY at LIMIT 1) AS oldest_title FROM plaza_picks WHERE room = ?1`, a.company_id);
      if ((count?.n ?? 0) >= PICKS_LIMITS.perRoom) {
        return json({ error: "full", field: "answer", oldest: count.oldest ? { id: count.oldest, title: count.oldest_title } : null }, 409);
      }
    }
    const id = a.picked ?? `k${randomId(11)}`;
    await run(env, `INSERT INTO plaza_picks (id, room, question, answer, title, form, topic, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (answer) DO UPDATE SET title = excluded.title, topic = excluded.topic`, id, a.company_id, a.qid, a.id, title, a.tag_form ?? null,
    topic, now);
    await log(a.picked ? "plaza_pick_title" : "plaza_pick", a.company_id, `${id} 답 ${a.id} ‘${title}’${topic ? ` · ${PICK_TOPICS[topic]}` : ""}`);
    return json({ ok: true, room: a.company_id, id, updated: Boolean(a.picked) });
  }
  const m = path.match(/^\/plaza\/picks\/(k[A-Za-z0-9]{11})$/);
  if (m && request.method === "DELETE") {
    const k = await one(env, "SELECT room, answer, title FROM plaza_picks WHERE id = ?", m[1]);
    if (!k) return json({ error: "not_found" }, 404);
    await run(env, "DELETE FROM plaza_picks WHERE id = ?", m[1]);
    await log("plaza_pick_remove", k.room, `${m[1]} 답 ${k.answer} ‘${k.title}’`);
    return json({ ok: true });
  }
  return null;
}
