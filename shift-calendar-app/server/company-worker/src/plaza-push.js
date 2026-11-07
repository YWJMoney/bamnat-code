import { all, deviceHash, deviceId, json, overLimit, readJson, run } from "./index.js";
import { fcmTellDevices } from "./fcm.js";
import { PEOPLE_FORMS } from "./plaza-people.js";
import { HOUR } from "./units.js";

// ---- 1.39 [PLZC-41] 광장 답 바로 알림 (사용자 결정 12번, 2026-11-07 19:21; 처리방침 3.12 ⑥) ----
//
// When someone else answers my question in a kind of work's 광장, my phone hears {kind: "plaza_answer"} at once; when a new question
// with my 근무형태 is written in a 광장 I chose to watch (설정 끄고 켬, 처음엔 꺼짐), {kind: "plaza_question"}. Like [PUSH-01] the message
// carries no words: the phone asks the server itself and words its own notice. A phone hears one of each kind at most every
// PLAZA_PUSH.answerGap / questionGap. Only while plaza.people is on; the token is the one 문의 답 알림 already keeps (push_phones).

export const PLAZA_PUSH_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS plaza_watch (device TEXT NOT NULL, room TEXT NOT NULL, form TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (device, room))`,
  `CREATE INDEX IF NOT EXISTS plaza_watch_form ON plaza_watch (room, form)`,
  `CREATE TABLE IF NOT EXISTS plaza_pushed (device TEXT NOT NULL, kind TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (device, kind))`,
];
export const PLAZA_PUSH = { answerGap: HOUR / 12, questionGap: 3 * HOUR, watchers: 200, perHour: 30 };
const PLAZA_PUSH_ROOMS = { "style-shift": "SHIFT", "style-day": "DAY" };

/**
 * POST /api/plaza/watch {device, room, form}: tell this phone of new questions with [form] in [room] (교대·일근 광장, a 근무형태 that
 * room's tags may carry). DELETE /api/plaza/watch {device, room}: stop. [on] says whether plaza.people is on.
 */
export async function plazaWatchRoute(request, env, on) {
  const body = await readJson(request, 512);
  const id = deviceId(body?.device);
  if (!id) return json({ error: "device", field: "device" }, 400);
  const dev = await deviceHash(id);
  const style = PLAZA_PUSH_ROOMS[body.room];
  if (!style) return json({ error: "room", field: "room" }, 400);
  if (request.method === "DELETE") {
    await run(env, "DELETE FROM plaza_watch WHERE device = ? AND room = ?", dev, body.room);
    return json({ ok: true });
  }
  if (!on) return json({ error: "not_yet", field: "plaza.people" }, 403);
  const now = Date.now();
  const hit = await overLimit(env, now, [["device", `pwatch|${dev}`, PLAZA_PUSH.perHour, HOUR]]);
  if (hit) return hit;
  if (!(PEOPLE_FORMS[style] ?? []).includes(body.form)) return json({ error: "form", field: "form" }, 400);
  await run(env, `INSERT INTO plaza_watch (device, room, form, at) VALUES (?, ?, ?, ?)
    ON CONFLICT (device, room) DO UPDATE SET form = excluded.form, at = excluded.at`, dev, body.room, body.form, now);
  return json({ ok: true });
}

/** The phones of [devices] not told of [kind] within [gap], marked as told now. */
async function plazaPushDue(env, devices, kind, gap, now) {
  if (!devices.length) return [];
  const recent = new Set();
  for (let i = 0; i < devices.length; i += 90) {
    const part = devices.slice(i, i + 90);
    for (const r of await all(env, `SELECT device FROM plaza_pushed WHERE kind = ? AND at > ? AND device IN (${part.map(() => "?").join(",")})`,
      kind, now - gap, ...part)) recent.add(r.device);
  }
  const due = devices.filter((d) => !recent.has(d));
  for (const d of due) {
    await run(env, `INSERT INTO plaza_pushed (device, kind, at) VALUES (?, ?, ?) ON CONFLICT (device, kind) DO UPDATE SET at = excluded.at`, d, kind, now);
  }
  return due;
}

/**
 * After a post in a kind of work's 광장 by [writer]: an answer tells the question's writer; a question with a 근무형태 tells those who
 * watch it there. Returns {kind, devices} told (for tests), never throws.
 */
export async function plazaPushAfter(env, { room, writer, parent, kind, form, on }, now = Date.now()) {
  if (!on) return null;
  try {
    if (parent) {
      const q = (await all(env, "SELECT device, kind, status FROM posts WHERE id = ?", parent))[0];
      if (!q || q.kind !== "question" || q.status !== "shown" || q.device === writer) return null;
      const due = await plazaPushDue(env, [q.device], "plaza_answer", PLAZA_PUSH.answerGap, now);
      await fcmTellDevices(env, due, "plaza_answer", now);
      return { kind: "plaza_answer", devices: due };
    }
    if (kind !== "question" || !form || !PLAZA_PUSH_ROOMS[room]) return null;
    const watchers = (await all(env, `SELECT device FROM plaza_watch WHERE room = ? AND form = ? AND device <> ? ORDER BY at DESC LIMIT ${PLAZA_PUSH.watchers}`,
      room, form, writer)).map((r) => r.device);
    const due = await plazaPushDue(env, watchers, "plaza_question", PLAZA_PUSH.questionGap, now);
    await fcmTellDevices(env, due, "plaza_question", now);
    return { kind: "plaza_question", devices: due };
  } catch (e) {
    console.error("plaza push", e && e.stack ? e.stack : String(e));
    return null;
  }
}
