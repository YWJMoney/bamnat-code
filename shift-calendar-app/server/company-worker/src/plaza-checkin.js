import { all, deviceHash, deviceId, json, overLimit, readJson, run } from "./index.js";
import { DAY, HOUR } from "./units.js";

// ---- 1.39 [PLZC-21] 오늘 출근 한 번 누르기 (처리방침 3.12 ②) ----
//
// In 교대 광장 "오늘 야간 출근 ✊" (or 주간), in 현장 광장 "오늘 현장 나가요" / "비로 공쳤어요": one tap, no post, and the room sees
// '오늘 야간 출근 n명 · 함께 버텨요'. Only the day (한국 날짜), the room and the kind are kept, by the install's hash, and only for
// two days. Only while plaza.people is on.

export const PLAZA_CHECKIN_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS plaza_checkin (day TEXT NOT NULL, room TEXT NOT NULL, device TEXT NOT NULL, kind TEXT NOT NULL,
    at INTEGER NOT NULL, PRIMARY KEY (day, room, device))`,
];
export const CHECKIN_KINDS = { "style-shift": ["day", "night"], "style-field": ["go", "rain"] };
export const CHECKIN_LIMITS = { perHour: 20, keepDays: 2 };

/** The Korean date of [now], as 2026-11-07. */
export function checkinDay(now) {
  return new Date(now + 9 * HOUR).toISOString().slice(0, 10);
}

/** Today's taps in [room] by kind, and [me]'s ({counts: {kind: n}, mine}); null for a room without 오늘 출근. */
export async function plazaCheckins(env, room, me, now = Date.now()) {
  const kinds = CHECKIN_KINDS[room];
  if (!kinds) return null;
  const day = checkinDay(now);
  const rows = await all(env, "SELECT kind, COUNT(*) AS n FROM plaza_checkin WHERE day = ? AND room = ? GROUP BY kind", day, room);
  const counts = Object.fromEntries(kinds.map((k) => [k, rows.find((r) => r.kind === k)?.n ?? 0]));
  const mine = me ? (await all(env, "SELECT kind FROM plaza_checkin WHERE day = ? AND room = ? AND device = ?", day, room, me))[0]?.kind : undefined;
  return { counts, ...(mine ? { mine } : {}) };
}

/** POST /api/plaza/checkin {device, room, kind}: today's tap (kind null takes it back); today's counts come back. */
export async function plazaCheckinRoute(request, env, on) {
  if (!on) return json({ error: "not_yet", field: "plaza.people" }, 403);
  const body = await readJson(request, 512);
  const id = deviceId(body?.device);
  if (!id) return json({ error: "device", field: "device" }, 400);
  const dev = await deviceHash(id);
  const kinds = CHECKIN_KINDS[body.room];
  if (!kinds) return json({ error: "room", field: "room" }, 400);
  const kind = body.kind ?? null;
  if (kind !== null && !kinds.includes(kind)) return json({ error: "kind", field: "kind" }, 400);
  const now = Date.now();
  const hit = await overLimit(env, now, [["device", `checkin|${dev}`, CHECKIN_LIMITS.perHour, HOUR]]);
  if (hit) return hit;
  const day = checkinDay(now);
  if (kind === null) await run(env, "DELETE FROM plaza_checkin WHERE day = ? AND room = ? AND device = ?", day, body.room, dev);
  else await run(env, `INSERT INTO plaza_checkin (day, room, device, kind, at) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (day, room, device) DO UPDATE SET kind = excluded.kind, at = excluded.at`, day, body.room, dev, kind, now);
  // Nothing older than two days stays.
  await run(env, "DELETE FROM plaza_checkin WHERE day < ?", checkinDay(now - CHECKIN_LIMITS.keepDays * DAY));
  return json({ ok: true, ...(await plazaCheckins(env, body.room, dev, now)) });
}
