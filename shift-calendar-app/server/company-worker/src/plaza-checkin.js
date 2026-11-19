import { all, deviceHash, deviceId, json, network, networkCheck, one, allow, overLimit, readJson, run } from "./index.js";
import { integrityGate } from "./integrity.js";
import { DAY, HOUR } from "./units.js";

// ---- 1.39 [PLZC-21] 오늘 출근 한 번 누르기 (처리방침 3.12 ②) ----
//
// In 교대 광장 "오늘 야간 출근 ✊" (or 주간), in 현장 광장 "오늘 현장 나가요" / "비로 공쳤어요": one tap, no post, and the room sees
// '오늘 야간 출근 n명 · 함께 버텨요'. Only the day (한국 날짜), the room and the kind are kept, by the install's hash, and only for
// two days. Only while plaza.people is on.

export const PLAZA_CHECKIN_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS plaza_checkin (day TEXT NOT NULL, room TEXT NOT NULL, device TEXT NOT NULL, kind TEXT NOT NULL,
    at INTEGER NOT NULL, PRIMARY KEY (day, room, device))`,
  // 2.0.0 [RVN-24] 검토 I22ca-02 광장 읽기 요약 표: 방마다 머리 숫자(오늘 글·답 기다리는 질문·근무 중)와 오늘 출근 수를 한 줄로. 1분 안이면
  // 열 때 이 줄 하나만 읽고(여는 사람마다 넷을 세던 것), 글을 쓰거나 출근을 누르면 그 줄을 지워 다음 여는 분이 새로 세요. 누가 썼는지는 없어요.
  `CREATE TABLE IF NOT EXISTS plaza_pulse (room TEXT NOT NULL, grp TEXT NOT NULL, day TEXT NOT NULL, today INTEGER NOT NULL, waiting INTEGER NOT NULL,
    working INTEGER NOT NULL, checkin TEXT, at INTEGER NOT NULL, PRIMARY KEY (room, grp))`,
];
/** I22ca-02: how long a room's 요약 줄 is used before it is counted again. */
export const PULSE_MS = 60 * 1000;
export const CHECKIN_KINDS = { "style-shift": ["day", "night"], "style-field": ["go", "rain"] };
// 2.0.0 [RVN-24] 검토 SVC-93: a network's taps a day too (a new install's hourly share of it, and the ceilings), as other writes.
export const CHECKIN_LIMITS = { perHour: 20, keepDays: 2, perNetworkDay: 300 };

/** The Korean date of [now], as 2026-11-07. */
export function checkinDay(now) {
  return new Date(now + 9 * HOUR).toISOString().slice(0, 10);
}

/** Today's taps in [room] by kind, and [me]'s ({counts: {kind: n}, mine}); null for a room without 오늘 출근. */
export async function plazaCheckins(env, room, me, now = Date.now()) {
  return plazaCheckinsOf(env, room, me, now);
}

async function plazaCheckinsOf(env, room, me, now) {
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
  // 2.0.0 [RVN-24] 검토 SVC-93: '오늘 야간 출근 n명' is a count others see, so a new install id is not a new tap for free: the network's
  // limit beside the install's, and Play's verdict first while 무결성 확인 is on (a take-back needs neither).
  if (kind !== null) {
    const gate = await integrityGate({ one, allow, json }, env, dev, now);
    if (gate) return gate;
  }
  const net = await network(request, env, now);
  const hit = await overLimit(env, now, [["device", `checkin|${dev}`, CHECKIN_LIMITS.perHour, HOUR],
    ...(kind === null ? [] : await networkCheck(env, dev, net, "checkin", CHECKIN_LIMITS.perNetworkDay, now))]);
  if (hit) return hit;
  const day = checkinDay(now);
  if (kind === null) await run(env, "DELETE FROM plaza_checkin WHERE day = ? AND room = ? AND device = ?", day, body.room, dev);
  else await run(env, `INSERT INTO plaza_checkin (day, room, device, kind, at) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (day, room, device) DO UPDATE SET kind = excluded.kind, at = excluded.at`, day, body.room, dev, kind, now);
  // Nothing older than two days stays. I22ca-02: the room's 요약 줄 is counted again by the next who opens it.
  await run(env, "DELETE FROM plaza_checkin WHERE day < ?", checkinDay(now - CHECKIN_LIMITS.keepDays * DAY));
  await run(env, "DELETE FROM plaza_pulse WHERE room = ?", body.room);
  return json({ ok: true, ...(await plazaCheckins(env, body.room, dev, now)) });
}

/**
 * 2.0.0 [RVN-24] 검토 I22ca-02: a kind of work's plaza's head numbers and 오늘 출근 counts, from its 요약 줄 [row] (read with the page's
 * batch) while it is under [PULSE_MS] old and of today, else counted with [count] (peoplePulse) and written back for the next opener.
 * [mine] is this phone's own tap, read with the batch. Returns {pulse, checkin} as the plaza's answer carries them.
 */
export async function plazaPulseOf(env, { room, grp, row, mine, now, count }) {
  const day = checkinDay(now);
  const kinds = CHECKIN_KINDS[room];
  if (row && row.day === day && now - row.at < PULSE_MS) {
    let counts = null;
    try {
      counts = row.checkin ? JSON.parse(row.checkin) : null;
    } catch {
      counts = null;
    }
    return { pulse: { today: row.today, waiting: row.waiting, working: row.working }, checkin: kinds && counts ? { counts, ...(mine ? { mine } : {}) } : null };
  }
  const [pulse, checkin] = await Promise.all([count(), kinds ? plazaCheckinsOf(env, room, null, now) : null]);
  await run(env, `INSERT INTO plaza_pulse (room, grp, day, today, waiting, working, checkin, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (room, grp) DO UPDATE SET day = excluded.day, today = excluded.today, waiting = excluded.waiting, working = excluded.working,
      checkin = excluded.checkin, at = excluded.at`, room, grp, day, pulse.today, pulse.waiting, pulse.working, checkin ? JSON.stringify(checkin.counts) : null, now)
    .catch(() => null);
  return { pulse, checkin: checkin ? { counts: checkin.counts, ...(mine ? { mine } : {}) } : null };
}
