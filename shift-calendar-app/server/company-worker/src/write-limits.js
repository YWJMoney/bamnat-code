// 1.36 [LIM-10] 쓰기 제한 (사용자 결정 2026-10-31 "제한을 두지마. 신고가 많은 사용자는 제한을 두고 기간이 지나면 풀어 주고, 제한 받은 횟수가
// 늘수록 더 줄이는 방식"): nobody has a daily cap on writing (레시피 올리기, 이야기, 광장 글, 요청). Each post or recipe that reports hid
// (or an admin hid) is a strike; enough strikes in a while restrict that install for a time, each restriction stricter than the one
// before, and a long quiet stretch steps it back down. Only the install's hash is kept.
import { DAY } from "./units.js";

export const WRITE_LIMITS = {
  /** Strikes within this many days count together. */
  windowDays: 30,
  /** This many strikes in the window start the next step. */
  strikes: 2,
  /** Each step in turn: how long, and how many writes a day of each kind meanwhile (0: none). */
  steps: [{ days: 3, perDay: 5 }, { days: 7, perDay: 2 }, { days: 30, perDay: 0 }, { days: 90, perDay: 0 }],
  /** Each this many days without a restriction takes one step off. */
  forgetDays: 180,
};

export const WRITE_LIMITS_SCHEMA = [
  "CREATE TABLE IF NOT EXISTS write_strikes (id INTEGER PRIMARY KEY AUTOINCREMENT, device TEXT NOT NULL, kind TEXT NOT NULL, ref TEXT NOT NULL, at INTEGER NOT NULL)",
  "CREATE INDEX IF NOT EXISTS write_strikes_device ON write_strikes (device, at)",
  `CREATE TABLE IF NOT EXISTS write_limits (device TEXT PRIMARY KEY, step INTEGER NOT NULL, since INTEGER NOT NULL, until INTEGER NOT NULL,
    per_day INTEGER NOT NULL, times INTEGER NOT NULL DEFAULT 1)`,
];

/**
 * 1.36 PC 확인 보안 6차 (운영자가 가렸는데 '신고가 쌓여'라고 나와요): who hid what a strike is for, "operator" or null (reports, and
 * strikes from before this column).
 */
export const WRITE_LIMITS_COLUMNS = ["ALTER TABLE write_strikes ADD COLUMN hidden_by TEXT"];

/** The step a restriction would start from now, after the quiet time since the last one ended. */
export function stepAfter(last, now, rules = WRITE_LIMITS) {
  if (!last) return 0;
  const quiet = Math.max(0, now - last.until);
  return Math.max(0, last.step - Math.floor(quiet / (rules.forgetDays * DAY)));
}

/**
 * This install's restriction now, as { perDay, until, step, by, n, kinds }, or null when it may write freely. [by] is who hid the things
 * the restriction is for ("operator", "reports" or "both"), [n] how many and [kinds] what they were (post, talk, recipe, bio, set,
 * profile), so the app says why (PC 확인 보안 6차).
 */
export async function writeAllowance(d, env, dev, now, rules = WRITE_LIMITS) {
  if (!dev) return null;
  const row = await d.one(env, "SELECT step, since, until, per_day FROM write_limits WHERE device = ? AND until > ?", dev, now);
  if (!row) return null;
  const strikes = await d.all(env, "SELECT kind, hidden_by FROM write_strikes WHERE device = ? AND at > ? AND at <= ?",
    dev, row.since - rules.windowDays * DAY, row.since);
  const op = strikes.filter((x) => x.hidden_by === "operator").length;
  const by = !strikes.length ? null : op === strikes.length ? "operator" : op === 0 ? "reports" : "both";
  return { perDay: row.per_day, until: row.until, step: row.step, by, n: strikes.length, kinds: [...new Set(strikes.map((x) => x.kind))] };
}

/** The restriction as the app reads it (the 쓰기 제한 line). */
export function limitJson(a, extra = {}) {
  return { until: new Date(a.until).toISOString(), perDay: a.perDay, ...extra, ...(a.by ? { by: a.by, n: a.n, kinds: a.kinds } : {}) };
}

/**
 * One strike for [dev]: [kind] ("post", "talk", "recipe") and what was hidden. Returns the restriction it started, or null. A
 * restriction starting at 0 a day also goes into `muted`, which every write already refuses.
 */
export async function addStrike(d, env, dev, kind, ref, now, rules = WRITE_LIMITS, by = null) {
  if (!dev) return null;
  const last = await d.one(env, "SELECT step, since, until, times FROM write_limits WHERE device = ?", dev);
  // A strike already counted toward a restriction does not count again.
  const from = Math.max(now - rules.windowDays * DAY, last ? last.since : 0);
  await d.run(env, "INSERT INTO write_strikes (device, kind, ref, at, hidden_by) VALUES (?, ?, ?, ?, ?)", dev, kind, String(ref).slice(0, 80), now, by);
  if (last && last.until > now) return null;
  const n = (await d.one(env, "SELECT COUNT(*) AS n FROM write_strikes WHERE device = ? AND at > ?", dev, from))?.n ?? 0;
  if (n < rules.strikes) return null;
  const step = Math.min(stepAfter(last, now, rules) + 1, rules.steps.length);
  const s = rules.steps[step - 1];
  const until = now + s.days * DAY;
  await d.batch(env, [
    d.stmt(env, `INSERT INTO write_limits (device, step, since, until, per_day, times) VALUES (?, ?, ?, ?, ?, 1)
      ON CONFLICT (device) DO UPDATE SET step = excluded.step, since = excluded.since, until = excluded.until, per_day = excluded.per_day,
        times = write_limits.times + 1`, dev, step, now, until, s.perDay),
    ...(s.perDay === 0 ? [d.stmt(env, `INSERT INTO muted (device, until, reason) VALUES (?, ?, 'strikes')
      ON CONFLICT (device) DO UPDATE SET until = MAX(muted.until, excluded.until), reason = excluded.reason`, dev, until)] : []),
  ]);
  return { step, until, perDay: s.perDay };
}

/** A strike for something the operator hid (not reports): the same, said as the operator's. */
export function operatorStrike(d, env, dev, kind, ref, now) {
  return addStrike(d, env, dev, kind, ref, now, WRITE_LIMITS, "operator");
}

/**
 * The device limit for one kind of write: none without a restriction; with one, its writes a day (key [key]). Returns a refusal
 * Response, or null. 0 a day is `muted`, refused before this.
 */
export async function writeLimitHit(d, env, dev, key, now) {
  const a = await writeAllowance(d, env, dev, now);
  if (!a) return null;
  if (a.perDay <= 0) return d.json({ error: "muted", until: new Date(a.until).toISOString() }, 403);
  return d.overLimit(env, now, [["device", `${key}|${dev}`, a.perDay, DAY]]);
}

/** What the app shows while restricted: { until, perDay, left } for [key], or nothing. */
export async function writeLeft(d, env, dev, key, now) {
  const a = await writeAllowance(d, env, dev, now);
  if (!a || a.perDay <= 0) return {};
  const used = await d.one(env, "SELECT count FROM counters WHERE key = ? AND expires > ?", `${key}|${dev}`, now);
  return { limit: limitJson(a, { left: Math.max(0, a.perDay - (used?.count ?? 0)) }) };
}

/** For the admin app: installs restricted now or in the last 30 days, newest first, with their strikes. */
export async function restrictions(d, env, now) {
  const rows = await d.all(env, `SELECT l.device, l.step, l.since, l.until, l.per_day, l.times,
      (SELECT COUNT(*) FROM write_strikes s WHERE s.device = l.device) AS strikes
    FROM write_limits l WHERE l.until > ? ORDER BY l.since DESC LIMIT 100`, now - 30 * DAY);
  return rows.map((r) => ({ who: r.device.slice(0, 12), step: r.step, since: new Date(r.since).toISOString(), until: new Date(r.until).toISOString(),
    perDay: r.per_day, times: r.times, strikes: r.strikes, now: r.until > now }));
}

/**
 * 1.36 보안 점검 M10: a strike taken back (what it hid is shown again; the caller deleted its row). A restriction on now that the
 * strikes left before it began would not have started ends now, a step back (it should not have stepped up), with the 쓰기 막기 it
 * started, so a false report does not keep its maker restricted.
 */
export async function strikeTakenBack(d, env, dev, now, rules = WRITE_LIMITS) {
  if (!dev) return null;
  const l = await d.one(env, "SELECT since FROM write_limits WHERE device = ? AND until > ?", dev, now);
  if (!l) return null;
  const n = (await d.one(env, "SELECT COUNT(*) AS n FROM write_strikes WHERE device = ? AND at > ? AND at <= ?",
    dev, l.since - rules.windowDays * DAY, l.since))?.n ?? 0;
  if (n >= rules.strikes) return null;
  await d.batch(env, [
    d.stmt(env, "UPDATE write_limits SET until = ?, step = MAX(0, step - 1), times = MAX(0, times - 1) WHERE device = ?", now, dev),
    d.stmt(env, "DELETE FROM muted WHERE device = ? AND reason = 'strikes'", dev),
  ]);
  return { lifted: true };
}

/**
 * The admin app's 풀기: ends [who]'s restriction now (and the 쓰기 막기 it started), keeping its step so the next one is still
 * stricter. [who] is the 12 letters `restrictions` shows; null when none or more than one install matches.
 */
export async function liftLimit(d, env, who, now) {
  if (!/^[0-9a-f]{12}$/.test(who)) return null;
  const rows = await d.all(env, "SELECT device FROM write_limits WHERE substr(device, 1, 12) = ? AND until > ?", who, now);
  if (rows.length !== 1) return null;
  const dev = rows[0].device;
  await d.batch(env, [
    d.stmt(env, "UPDATE write_limits SET until = ? WHERE device = ?", now, dev),
    d.stmt(env, "DELETE FROM muted WHERE device = ? AND reason = 'strikes'", dev),
  ]);
  return { who, lifted: true };
}
