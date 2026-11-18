// 1.36 [LIM-10] 쓰기 제한 (사용자 결정 2026-10-31 "제한을 두지마. 신고가 많은 사용자는 제한을 두고 기간이 지나면 풀어 주고, 제한 받은 횟수가
// 늘수록 더 줄이는 방식"): nobody has a daily cap on writing (레시피 올리기, 이야기, 광장 글, 요청). Each post or recipe that reports hid
// (or an admin hid) is a strike; enough strikes in a while restrict that install for a time, each restriction stricter than the one
// before, and a long quiet stretch steps it back down. Only the install's hash is kept.
import { DAY } from "./units.js";
import { careAutoWarn, careOn } from "./community-care.js";

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

/**
 * 1.39 [CARE-06] 더 잘게, 확인한 것만 (사용자 2026-11-07 "2회는 너무 타이트, 실제로 잘못된 행동을 하고 있을 때 제재"): 마을 돌봄 깃발이 켜지면
 * (처리방침 3.12·광장 운영 규칙 다음 판 시행일) 이 규칙을 써요. 신고로 먼저 가려진 것은 '확인 대기'로만 두고 운영자가 가린 채 둔 것만 세요.
 * 60일 안 1번 안내, 2번 경고(막는 것 없음), 3번부터 한 단계씩, 제한을 받은 뒤 60일 안에는 1번마다 다음 단계.
 * 2.0.0 [RVD-17] 검토 LEGAL-07 (사용자 결정 2026-11-12 '서버를 60일로'): 광장 운영 규칙 6항 "한 번 제한을 받은 뒤 60일 안에 또 확인되면 바로 다음
 * 단계"대로, 제한이 끝난 뒤 60일이 지나면 다시 3번부터 세요(1.39는 단계가 내려가기 전 180일 동안 1번마다 올렸어요). 180일 조용하면 한 단계씩
 * 내려가는 것은 그대로예요.
 */
export const WRITE_LIMITS_V2 = {
  windowDays: 60,
  strikes: 3,
  /** After a restriction (its step not yet forgotten), each confirmed one within [againDays] of its end steps up. */
  again: 1,
  againDays: 60,
  steps: WRITE_LIMITS.steps,
  forgetDays: 180,
  /** Report-hidden strikes wait for the operator; the ones under [strikes] bring a gentle 안내·경고 (community-care.js). */
  care: true,
};

/**
 * 2.0.0 [RVD-18] 검토 LEGAL-02 (사용자 결정 2026-11-12): 처리방침 3.12 1-4항 "3.12 시행일부터는 운영자가 확인해 가린 것만 셉니다"를 그날부터.
 * 마을 돌봄(12-09) 전에도 신고로 가려진 것은 '확인 대기'로 두고, 셈과 단계는 1.36 그대로(30일 안 2번)예요.
 */
export const WRITE_LIMITS_CONFIRMED = { ...WRITE_LIMITS, confirmedOnly: true };

/** The rules in force now: the finer ones once 마을 돌봄 is on, else 1.36's counting only what the operator confirmed once 3.12 is in force. */
export async function writeRules(d, env, now) {
  if (await careOn(d, env, now)) return WRITE_LIMITS_V2;
  return d.legalOpen && (await d.legalOpen(env, "community.confirmed", now)) ? WRITE_LIMITS_CONFIRMED : WRITE_LIMITS;
}

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
export const WRITE_LIMITS_COLUMNS = ["ALTER TABLE write_strikes ADD COLUMN hidden_by TEXT",
  // 1.39 [CARE-06] 0: hidden by reports, waiting for the operator (counts nowhere until confirmed).
  "ALTER TABLE write_strikes ADD COLUMN confirmed INTEGER NOT NULL DEFAULT 1"];

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
  const strikes = await d.all(env, "SELECT kind, hidden_by FROM write_strikes WHERE device = ? AND confirmed = 1 AND at > ? AND at <= ?",
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
 * restriction starting at 0 a day also goes into `muted`, which every write already refuses. 1.39 [CARE-06]: with 마을 돌봄 on, one the
 * reports hid waits for the operator ([confirmStrike]).
 */
export async function addStrike(d, env, dev, kind, ref, now, rules = null, by = null) {
  if (!dev) return null;
  rules ??= await writeRules(d, env, now);
  const waits = Boolean(rules.care || rules.confirmedOnly) && by !== "operator";
  // 2.0.0 [RVQ-27] 검토 SVC-58: one waiting strike for one thing; hidden again before the operator looked does not add another.
  if (waits && (await d.one(env, "SELECT 1 AS x FROM write_strikes WHERE device = ? AND kind = ? AND ref = ? AND confirmed = 0", dev, kind, String(ref).slice(0, 80)))) return null;
  await d.run(env, "INSERT INTO write_strikes (device, kind, ref, at, hidden_by, confirmed) VALUES (?, ?, ?, ?, ?, ?)",
    dev, kind, String(ref).slice(0, 80), now, by, waits ? 0 : 1);
  if (waits) return null;
  return weigh(d, env, dev, kind, ref, now, rules, by);
}

/** After a counted strike: the next step when there are enough, else (마을 돌봄) a gentle 안내 or 경고. */
async function weigh(d, env, dev, kind, ref, now, rules, by) {
  const last = await d.one(env, "SELECT step, since, until, times FROM write_limits WHERE device = ?", dev);
  if (last && last.until > now) return null;
  // A strike already counted toward a restriction does not count again.
  const from = Math.max(now - rules.windowDays * DAY, last ? last.since : 0);
  const n = (await d.one(env, "SELECT COUNT(*) AS n FROM write_strikes WHERE device = ? AND confirmed = 1 AND at > ?", dev, from))?.n ?? 0;
  // [RVD-17] Only within [againDays] after the last restriction ended does one more step up; after that it is [strikes] again.
  const recent = last && (!rules.againDays || now - last.until <= rules.againDays * DAY);
  const need = rules.again && recent && stepAfter(last, now, rules) > 0 ? rules.again : rules.strikes;
  if (n < need) {
    // 1.39 [CARE-01] 막기 전에 부드럽게: 처음은 안내, 그다음은 '한 번 더면 며칠 줄어요' 경고.
    if (rules.care) await careAutoWarn(d, env, dev, kind, ref, by, now, { n, need, again: Boolean(recent) });
    return null;
  }
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

/**
 * 1.39 [CARE-06] The operator looked at something the reports hid and kept it hidden: its waiting strike counts now (and may bring the
 * 안내·경고 or the next step). Nothing when none waits (마을 돌봄 off, or it was counted already).
 */
export async function confirmStrike(d, env, ref, now) {
  const row = await d.one(env, "SELECT id, device, kind FROM write_strikes WHERE ref = ? AND confirmed = 0 ORDER BY id DESC LIMIT 1", String(ref).slice(0, 80));
  if (!row) return null;
  await d.run(env, "UPDATE write_strikes SET confirmed = 1 WHERE id = ?", row.id);
  return weigh(d, env, row.device, row.kind, ref, now, await writeRules(d, env, now), null);
}

/** A strike for something the operator hid (not reports): the same, said as the operator's. */
export function operatorStrike(d, env, dev, kind, ref, now) {
  return addStrike(d, env, dev, kind, ref, now, null, "operator");
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
      (SELECT COUNT(*) FROM write_strikes s WHERE s.device = l.device AND s.confirmed = 1) AS strikes
    FROM write_limits l WHERE l.until > ? ORDER BY l.since DESC LIMIT 100`, now - 30 * DAY);
  return rows.map((r) => ({ who: r.device.slice(0, 12), step: r.step, since: new Date(r.since).toISOString(), until: new Date(r.until).toISOString(),
    perDay: r.per_day, times: r.times, strikes: r.strikes, now: r.until > now }));
}

/**
 * 1.36 보안 점검 M10: a strike taken back (what it hid is shown again; the caller deleted its row). A restriction on now that the
 * strikes left before it began would not have started ends now, a step back (it should not have stepped up), with the 쓰기 막기 it
 * started, so a false report does not keep its maker restricted.
 */
export async function strikeTakenBack(d, env, dev, now, rules = null) {
  if (!dev) return null;
  const l = await d.one(env, "SELECT since FROM write_limits WHERE device = ? AND until > ?", dev, now);
  if (!l) return null;
  rules ??= await writeRules(d, env, now);
  const n = (await d.one(env, "SELECT COUNT(*) AS n FROM write_strikes WHERE device = ? AND confirmed = 1 AND at > ? AND at <= ?",
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
