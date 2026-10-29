import { adminLib } from "./admin-lib.js";
import { CORE_FEATURES, CORE_FLAGS, CORE_FREE, CORE_POLICY } from "./flag-catalog.js";
import { APP_CODES, all, audit, batch, bugSplit, can, given, history, iso, json, list, notifyAdmins, one, readJson, refuse, run, stmt, versions } from "./index.js";
import { actorName, plainText } from "./inquiries.js";
import { FEATURE, launchState } from "./money.js";
import { ITEM_FIXED_POLICY } from "./plaza-items.js";
import { DAY } from "./units.js";
import { kstDay } from "./usage.js";
// AM 1.28 시작
// ---- 기능 플래그(다크머지) [FLAG-20]~[FLAG-33] ----
//
// Every feature that ships switched off, waits for a date, a policy or a person's confirmation, or can be switched off
// in an incident is one row of FLAG_CATALOG (the same keys as the app's core FlagCatalog, 묶음 AL; a test compares the
// keys with the contract, 통합계획 2-1). The phone decides by itself from the conditions it was built with; the server
// only sends what overrides them (/api/config flags) and keeps who changed what, when and why. The order of the
// decision is in src/admin-lib.js evalFlag, which this server and the developer app's 미리 보기 share.

export const FL = adminLib();

/** The six 기능 스위치 of 1.22 (SWITCHES) as flags, for apps up to 1.27 that read /api/config off. */
export const SWITCH_FLAGS = { "plaza.write": "PLAZA_WRITE", "company.report": "COMPANY_REPORT", "inquiry.photos": "INQUIRY_PHOTOS",
  "share.create": "SHARE_CREATE", "usage.stats": "USAGE_STATS", "plaza.news": "PLAZA_NEWS" };

export const TYPE_FLAGS = { SHIFT: "type.shift", DAY: "type.day", HOURLY: "type.hourly", FIELD: "type.field", BUSINESS: "type.business", INVEST: "type.invest" };

/**
 * [FLAG-20] The catalog. 이름·하는 일·꺼 둔 이유·근거·위험·기본값·조건·정리 예정 판 are what the developer app shows and the
 * conditions the app ships with. 1.28 통합 [FLAG-34]: they come from the app's core FlagCatalog through the generated
 * src/flag-catalog.js (core FlagCatalogExportTest writes it and fails while it is old), so there is one book.
 */
export const FLAG_CATALOG = CORE_FLAGS;

/** The keys of the contract (통합계획 2-1) and the integrator's stats.more; free.<FEATURE> are made on demand. */
export const FLAG_KEYS = FLAG_CATALOG.map((f) => f.key);

// AU 1.29
/**
 * [FLAG-20] 프로 features the server may open to everyone (app FeatureGate.Feature), as free.<FEATURE>.
 * [NXT-03] The names are core's ProFeatures (CORE_FEATURES, made by FlagCatalogExportTest), so the developer app and
 * the phone's 진단 name a free.<FEATURE> row the same way.
 */
export const FREE_FEATURES = CORE_FEATURES;
// AU 1.29 끝
/** [FLAG-34] free.<FEATURE>, the same pattern as core FlagCatalog.free. */
export const FREE_KEY = new RegExp(CORE_FREE.pattern);

/** A flag by key: the catalog's, or a free.<FEATURE> made from core's words and the feature's Korean name; null for anything else. */
export function flagDef(key) {
  const known = FLAG_CATALOG.find((f) => f.key === key);
  if (known) return known;
  const m = FREE_KEY.exec(key ?? "");
  if (!m) return null;
  const label = FREE_FEATURES[m[1]] ?? m[1];
  const fill = (t) => t.replaceAll(CORE_FREE.slot, label);
  const { pattern, slot, ...row } = CORE_FREE;
  return { ...row, key, name: fill(CORE_FREE.name), what: fill(CORE_FREE.what), whyOff: fill(CORE_FREE.whyOff) };
}

/**
 * [FLAG-33] 처리방침 판마다 게시일·시행일·동의 없이도 시행되는 날(heldUntil). The developer app can change these (the owner),
 * so a date moved again needs no app release. unfavourable: 이용자에게 불리한 변경(30일 전 고지, 처리방침 9항).
 * [FLAG-34] The versions the app ships with (core PolicyCalendar.SEED: 3.1, 3.2) take their dates from it; 3.2's are a
 * placeholder until 순번 459 fixes them (관리자 앱에서 고쳐요).
 */
export const POLICY_UNFAVOURABLE = { "2.5": true, "3.0": true, "3.1": false, "3.2": true, "3.3": false };
export const POLICY_CALENDAR = [
  { version: "2.5", posted: "2026-10-22", effective: "2026-11-21", heldUntil: null },
  { version: "3.0", posted: "2026-10-25", effective: "2026-11-24", heldUntil: null },
  ...CORE_POLICY,
].map((p) => ({ ...p, unfavourable: POLICY_UNFAVOURABLE[p.version] ?? false }));

/** [FLAG-25] The newest release this server knows (APP_CODES), to tell which flags are past their 정리 예정 판. */
export const latestRelease = () => Object.keys(APP_CODES).sort(FL.versionCompare).at(-1);

export const FLAG_LIMITS = { why: 300, untilDays: 365, history: 100 };

export const flagMigrations = new WeakMap();

/**
 * [FLAG-23] Once per database: the 1.27 settings become overrides. 정식 출시 on → billing and ads forced on (the
 * same as before: both started together), 무료로 연 기능 → free.<FEATURE> forced on, 기능 스위치 off → forced off (the
 * hourly job's own rests keep auto). The old rows stay, unread, and a second run changes nothing.
 */
export function migrateFlags(env) {
  let done = flagMigrations.get(env.DB);
  if (!done) {
    done = (async () => {
      if ((await one(env, "SELECT value FROM settings WHERE key = 'flags_migrated'"))?.value) return;
      const rows = await all(env, "SELECT key, value FROM settings WHERE key IN ('billing_live', 'free_features', 'switches_off', 'auto_rested')");
      const get = (k) => rows.find((r) => r.key === k)?.value;
      const list = (k) => {
        try {
          const v = JSON.parse(get(k) ?? "[]");
          return Array.isArray(v) ? v : [];
        } catch {
          return [];
        }
      };
      const now = Date.now();
      const put = [];
      const add = (key, force, why, auto = 0) => put.push(stmt(env, `INSERT OR IGNORE INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto)
        VALUES (?, ?, ?, NULL, NULL, NULL, '옮김(1.28)', 'system', ?, ?)`, key, force, why, now, auto));
      if (get("billing_live") === "1") {
        add("billing", "on", "1.27까지의 정식 출시 스위치가 켜져 있었어요");
        add("ads", "on", "1.27까지의 정식 출시 스위치가 켜져 있었어요");
      }
      for (const f of list("free_features")) if (typeof f === "string" && FEATURE.test(f)) add(`free.${f}`, "on", "1.27까지 무료로 연 기능이었어요");
      const auto = new Set(list("auto_rested").map((r) => r?.key));
      for (const s of list("switches_off")) {
        const key = Object.keys(SWITCH_FLAGS).find((k) => SWITCH_FLAGS[k] === s);
        if (key) add(key, "off", auto.has(s) ? "오늘 사용이 많아 내일까지 쉬어요(자동)" : "1.27까지의 기능 스위치로 꺼져 있었어요", auto.has(s) ? 1 : 0);
      }
      put.push(stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('flags_migrated', ?)", iso(now)));
      await batch(env, put);
      if (put.length > 1) {
        await run(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, 'flag_migrate', '', ?, '', '')", now, `${put.length - 1}개를 기능 관리로 옮겼어요`);
      }
    })().catch((e) => {
      flagMigrations.delete(env.DB);
      throw e;
    });
    flagMigrations.set(env.DB, done);
  }
  return done;
}

/** An override row as the API and evalFlag see it. */
export function readOverride(r) {
  if (!r) return null;
  let conditions = null;
  try {
    conditions = r.conditions ? JSON.parse(r.conditions) : null;
  } catch {
    conditions = null;
  }
  return { force: r.force === "on" || r.force === "off" ? r.force : null, why: r.why ?? "", conditions: Array.isArray(conditions) ? conditions : null,
    until: r.until ? iso(r.until) : null, reviewAt: r.review_at ?? null, by: r.by_name ?? "", byId: r.by_id ?? "", at: iso(r.at), auto: r.auto === 1,
    // 1.28 통합 [FLAG-36] AL-D3: outside this versionCode range the phone turns the flag off (판단 2단계).
    minApp: r.min_app ?? null, maxApp: r.max_app ?? null };
}

/**
 * [FIN130-15] Whether 처리방침 [ITEM_FIXED_POLICY] is in force today (KST), by the calendar as the owner set it in the developer
 * app: a company's one amount (fixedAmount) is taken only from then. A version not in the calendar is not in force.
 */
export async function fixedAmountsOpen(env, now = Date.now()) {
  const raw = (await one(env, "SELECT value FROM settings WHERE key = 'policy_overrides'"))?.value;
  const p = mergePolicy(raw).find((x) => x.version === ITEM_FIXED_POLICY);
  return Boolean(p?.effective) && kstDay(now) >= p.effective;
}

/** [FLAG-33] The calendar with the owner's changes, and versions the owner added. */
export function mergePolicy(raw) {
  let over = {};
  try {
    over = JSON.parse(raw ?? "{}") ?? {};
  } catch {
    over = {};
  }
  const out = POLICY_CALENDAR.map((p) => ({ ...p, ...(over[p.version] ?? {}), changed: Object.hasOwn(over, p.version) }));
  for (const [version, p] of Object.entries(over)) if (!out.some((x) => x.version === version)) out.push({ version, heldUntil: null, unfavourable: false, ...p, changed: true });
  return out.sort((a, b) => FL.versionCompare(a.version, b.version));
}

/** [FLAG-21] Everything a decision needs, in one trip: overrides, confirmations, the policy calendar, what the server saw. */
export const FLAG_STORE_SQL = {
  flagOverrides: "SELECT * FROM flag_overrides",
  flagChecks: "SELECT key, by_name, by_id, at, note FROM flag_checks",
  flagPolicy: "SELECT value FROM settings WHERE key = 'policy_overrides'",
  flagState: "SELECT key, on_now, since, warned FROM flag_state",
};

export async function flagStore(env) {
  await migrateFlags(env);
  const [o, c, p, s] = (await batch(env, Object.values(FLAG_STORE_SQL).map((sql) => env.DB.prepare(sql)))).map((r) => r.results);
  return storeFrom(o, c, p, s);
}

/** The store from the rows of FLAG_STORE_SQL (also read inside the home screen's one batch). */
export function storeFrom(o, c, p, s) {
  return {
    overrides: new Map(o.map((r) => [r.key, readOverride(r)])),
    checks: Object.fromEntries(c.map((r) => [r.key, { by: r.by_name, byId: r.by_id, at: iso(r.at), note: r.note ?? "" }])),
    policy: mergePolicy(p[0]?.value),
    state: new Map(s.map((r) => [r.key, r])),
  };
}

/** Every flag there is: the catalog and the free.<FEATURE> rows that have an override. */
export const flagKeys = (store) => [...FLAG_KEYS, ...[...store.overrides.keys()].filter((k) => !FLAG_KEYS.includes(k) && flagDef(k))];

/** The server's view of [key] at [now]: conditions that depend on a phone do not hold it back. */
export const serverEval = (store, key, now = Date.now()) => FL.evalFlag(flagDef(key), store.overrides.get(key) ?? null,
  { now, policy: store.policy, checks: store.checks, phone: null });

/** [FLAG-24] Whether one of the six switch flags is forced off now; read on every write it guards. */
export async function switchOff(env, name) {
  await migrateFlags(env);
  const key = Object.keys(SWITCH_FLAGS).find((k) => SWITCH_FLAGS[k] === name);
  const r = await one(env, "SELECT force, until FROM flag_overrides WHERE key = ?", key);
  return FL.forceOf(r ? { force: r.force, until: r.until ? iso(r.until) : null } : null, Date.now()) === "off";
}

/** The manual keys a condition list waits on (inside '또는' too). */
export const manualKeys = (list) => [...new Set((list ?? []).flatMap((c) => (c.type === "manual" ? [c.key] : c.type === "any" ? manualKeys(c.of) : [])))];

/**
 * [FLAG-22] What /api/config says about flags: only what the server overrides, as 통합계획 2-1 shapes them, plus
 * confirmed (AL-D2): the manual facts the developer confirmed, for every flag that waits on one (a phone with no
 * answer from the server treats a manual condition as unknown, so such a flag is always sent).
 */
export function configFlags(store, now) {
  const out = {};
  for (const key of flagKeys(store)) {
    const def = flagDef(key);
    const o = store.overrides.get(key) ?? null;
    const force = FL.forceOf(o, now);
    const manual = manualKeys(o?.conditions ?? def.conditions);
    const ranged = o?.minApp != null || o?.maxApp != null;
    if (!force && !o?.conditions && !manual.length && !ranged) continue;
    const confirmed = Object.fromEntries(manual.filter((k) => store.checks[k]).map((k) => [k, store.checks[k].at]));
    // 1.28 통합 [FLAG-36] minApp/maxApp (AL-D3) go with every flag sent; null is no end.
    // S 1.32 [SECX-04] The admin's 이유 stays in the developer app: the phone shows no flag reasons any more, so none is sent.
    out[key] = { force, why: "", conditions: o?.conditions ?? null, until: force && o.until ? o.until : null,
      minApp: o?.minApp ?? null, maxApp: o?.maxApp ?? null, confirmed };
  }
  return out;
}

/** [FLAG-22] The calendar as the app reads it. */
export const configPolicy = (store) => store.policy.map((p) => ({ version: p.version, posted: p.posted, effective: p.effective, heldUntil: p.heldUntil ?? null }));

/** [FLAG-22] For apps up to 1.27: free.<FEATURE> forced on, and the six switches forced off by their old names. */
export function legacyFlags(store, now) {
  const free = [];
  const off = [];
  for (const key of flagKeys(store)) {
    const m = FREE_KEY.exec(key);
    if (m && serverEval(store, key, now).on) free.push(m[1]);
    if (SWITCH_FLAGS[key] && FL.forceOf(store.overrides.get(key), now) === "off") off.push(SWITCH_FLAGS[key]);
  }
  return { free, off };
}

/**
 * [FLAG-25] The first moment billing is on by the server's view is the launch (launchedAt): thank-you months and passes
 * count from it. Written once; never moved again, whatever happens to the flag later.
 */
export async function recordLaunch(env, store, now) {
  if (!serverEval(store, "billing", now).on) return null;
  const had = await one(env, "SELECT value FROM settings WHERE key = 'launched_at'");
  if (had && Number(had.value) > 0) return Number(had.value);
  await run(env, "INSERT OR IGNORE INTO settings (key, value) VALUES ('launched_at', ?)", String(now));
  const kept = Number((await one(env, "SELECT value FROM settings WHERE key = 'launched_at'"))?.value);
  if (kept === now) await audit(env, now, "launch_recorded", "billing", `정식 출시일 ${iso(now)}`);
  return kept;
}

/**
 * [FLAG-25][FLAG-28] The hourly job (17분) and every change: forces whose until passed end, a flag the server sees turn on
 * or off is written down (when, for the 24 hours of 홈 경고), the launch is recorded, and admins with 기능 관리 hear of a
 * legal flag the day before it turns on by itself and once it did (D99).
 */
export async function flagTick(env, now = Date.now(), { push = true } = {}) {
  let store = await flagStore(env);
  const ended = [...store.overrides.entries()].filter(([, o]) => (o.force && o.until && Date.parse(o.until) <= now));
  for (const [key, o] of ended) {
    await run(env, "UPDATE flag_overrides SET force = NULL, until = NULL WHERE key = ?", key);
    await flagHistory(env, now, key, "flag_expire", o, { ...o, force: null, until: null }, "정한 기한이 끝났어요", { system: true });
  }
  if (ended.length) store = await flagStore(env);
  const news = [];
  for (const key of flagKeys(store)) {
    const def = flagDef(key);
    const ev = serverEval(store, key, now);
    const was = store.state.get(key);
    if (!was) {
      await run(env, "INSERT OR IGNORE INTO flag_state (key, on_now, since, warned) VALUES (?, ?, ?, NULL)", key, ev.on ? 1 : 0, now);
    } else if ((was.on_now === 1) !== ev.on) {
      await run(env, "UPDATE flag_state SET on_now = ?, since = ? WHERE key = ?", ev.on ? 1 : 0, now, key);
      await run(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, ?, ?, ?, '', '')", now,
        ev.on ? "flag_auto_on" : "flag_auto_off", key, ev.reason.slice(0, 300));
      if (def.risk === "LEGAL" && ev.on) news.push({ title: `${def.name}이(가) 켜졌어요`, body: "24시간 동안 홈 맨 위에서 한 번에 강제로 끌 수 있어요.", url: `/admin/#/flags/${key}`, legal: true });
    }
    // D99: the day before a legal flag would turn on by itself (its date comes, nothing else left).
    if (def.risk === "LEGAL" && !ev.on && ev.state === "waiting") {
      const tomorrow = serverEval(store, key, now + DAY);
      const day = kstDay(now + DAY);
      if (tomorrow.on && was?.warned !== day) {
        await run(env, "UPDATE flag_state SET warned = ? WHERE key = ?", day, key);
        news.push({ title: `내일 ${def.name}이(가) 저절로 켜져요`, body: `${day} 0시(한국 시간)에 켜져요. 미루려면 기능 관리에서 강제로 꺼 주세요.`, url: `/admin/#/flags/${key}`, legal: true });
      }
    }
  }
  await recordLaunch(env, store, now);
  // [FLAG-37] D101: these two are a legal flag's, so they go inside an admin's quiet time too.
  if (push) for (const { legal, ...n } of news) await notifyAdmins(env, "flags", { ...n, urgent: true }, now, { ignoreQuiet: legal === true });
  return news;
}

/** [FLAG-21] One change, kept whole (before and after) for the flag's own history, and a line in 활동 기록. */
export async function flagHistory(env, now, key, action, before, after, why, { system = false } = {}) {
  const who = system ? "" : env.actor ? actorName(env) : "";
  const id = system ? "" : env.actor?.id ?? "";
  await run(env, "INSERT INTO flag_history (key, at, action, actor, actor_id, before, after, why) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", key, now, action,
    who, id, before === null || before === undefined ? null : JSON.stringify(before), after === null || after === undefined ? null : JSON.stringify(after), why ?? "");
  await run(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, ?, ?, ?, ?, ?)", now, action, key, String(why ?? "").slice(0, 300), who, id);
}

/** [FLAG-30] Of the phones that sent which flags were on (stats.more), how many had each, per flag, over [days] days. */
export async function flagShares(env, now, days = 7) {
  const from = kstDay(now - days * DAY);
  const rows = await all(env, "SELECT flag, SUM(phones) AS phones FROM usage_flags WHERE day >= ? GROUP BY flag", from);
  const base = rows.find((r) => r.flag === "*")?.phones ?? 0;
  return { base, share: Object.fromEntries(rows.filter((r) => r.flag !== "*").map((r) => [r.flag, base ? Math.round((1000 * r.phones) / base) / 10 : null])) };
}

/** [FLAG-21][ADMUX-21] One flag as the list and the detail show it. */
export function flagRow(store, key, now, shares) {
  const def = flagDef(key);
  const o = store.overrides.get(key) ?? null;
  const ev = serverEval(store, key, now);
  const conds = o?.conditions ?? def.conditions;
  const release = latestRelease();
  const legal = def.risk === "LEGAL";
  const manual = (c) => manualKeys([c]);
  return {
    key, name: def.name, group: def.group, what: def.what, whyOff: def.whyOff, basis: def.basis, risk: def.risk, default: def.default, sunset: def.sunset,
    owner: def.owner, since: def.since, switchKey: SWITCH_FLAGS[key] ?? null, conditions: conds, catalogConditions: def.conditions, override: o,
    on: ev.on, state: ev.state, source: ev.source, reason: ev.reason, next: ev.next, daysLeft: FL.daysLeft(ev.next, now),
    checklist: ev.conditions.map((x) => ({ label: x.label, met: x.met, type: x.c.type, keys: manual(x.c) })),
    warnings: FL.noticeWarnings(def, conds, store.policy),
    sunsetDue: Boolean(def.sunset && release && FL.versionCompare(release, def.sunset) >= 0),
    reviewDue: Boolean(o?.reviewAt && o.reviewAt <= kstDay(now)),
    onShare: shares?.share?.[key] ?? null,
    onSince: store.state.get(key)?.since ? iso(store.state.get(key).since) : null,
    alert: Boolean(legal && ev.on && store.state.get(key)?.since && now - store.state.get(key).since < DAY),
    manualKeys: manualKeys(conds),
  };
}

/** Who may do what to a flag (D97): the owner everything; 기능 강제로 끄기 (flagOff) for force off; the rest by area. */
export function flagMay(actor, def, step, current) {
  if (actor.owner) return true;
  const legal = def.risk === "LEGAL";
  if (step === "on") return false;
  if (step === "off") return can(actor, "flagOff", "edit");
  if (step === "clear") return current === "on" ? can(actor, "flagOff", "edit") : !legal && can(actor, "flagOff", "edit");
  if (step === "conditions") return !legal && can(actor, "flags", "edit");
  return false;
}

/** What the screens tell an admin about their own reach, so buttons lock with the reason instead of failing. */
export const flagReach = (actor) => ({ owner: Boolean(actor.owner), edit: can(actor, "flags", "edit"), off: Boolean(actor.owner || can(actor, "flagOff", "edit")) });

/** [ADMUX-21] The list, with the calendar and the confirmations. */
export async function flagList(env, actor, now) {
  const store = await flagStore(env);
  const shares = await flagShares(env, now);
  return {
    serverTime: iso(now), release: latestRelease(), reach: flagReach(actor), policy: store.policy, checks: store.checks, manualNames: FL.MANUAL_NAMES,
    freeFeatures: FREE_FEATURES, statsPhones: shares.base,
    flags: flagKeys(store).map((k) => flagRow(store, k, now, shares)),
  };
}

/** [ADMUX-22] One flag: the row, its whole history, phones it is on for by app version, and problems before and after. */
export async function flagDetail(env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "not_found" }, 404);
  const store = await flagStore(env);
  const shares = await flagShares(env, now);
  const row = flagRow(store, key, now, shares);
  const pivot = store.state.get(key)?.since ?? now;
  const from = kstDay(pivot - 7 * DAY);
  const to = kstDay(pivot + 7 * DAY);
  const [history, versions, bugs, errors, series] = (await batch(env, [
    stmt(env, "SELECT * FROM flag_history WHERE key = ? ORDER BY id DESC LIMIT ?", key, FLAG_LIMITS.history),
    stmt(env, `SELECT f.app, SUM(f.phones) AS on_phones, (SELECT SUM(b.phones) FROM usage_flags b WHERE b.flag = '*' AND b.app = f.app AND b.day >= ?) AS base
      FROM usage_flags f WHERE f.flag = ? AND f.day >= ? GROUP BY f.app ORDER BY on_phones DESC`, kstDay(now - 7 * DAY), key, kstDay(now - 7 * DAY)),
    stmt(env, "SELECT created_at FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ? AND created_at < ?", pivot - 7 * DAY, pivot + 7 * DAY),
    stmt(env, "SELECT key, count FROM counters WHERE key >= ? AND key <= ?", `errors|${from}`, `errors|${to}`),
    stmt(env, `SELECT f.day, f.phones, (SELECT SUM(b.phones) FROM usage_flags b WHERE b.flag = '*' AND b.day = f.day) AS base
      FROM (SELECT day, SUM(phones) AS phones FROM usage_flags WHERE flag = ? AND day >= ? GROUP BY day) f ORDER BY f.day`, key, kstDay(now - 28 * DAY)),
  ])).map((r) => r.results);
  const errBefore = errors.filter((e) => e.key.slice(7) < kstDay(pivot)).reduce((n, e) => n + e.count, 0);
  const errAfter = errors.filter((e) => e.key.slice(7) >= kstDay(pivot)).reduce((n, e) => n + e.count, 0);
  const parse = (v) => {
    try {
      return v ? JSON.parse(v) : null;
    } catch {
      return null;
    }
  };
  return json({
    ...row, serverTime: iso(now), reach: flagReach(actor), policy: store.policy, checks: store.checks, manualNames: FL.MANUAL_NAMES,
    statsPhones: shares.base, launchedAt: key === "billing" ? (await launchState(env)).launchedAt : undefined,
    thanksMonths: key === "billing" ? (await launchState(env)).thanksMonths : undefined,
    history: history.map((h) => ({ id: h.id, at: iso(h.at), action: h.action, actor: h.actor || "시스템", actorId: h.actor_id || null, before: parse(h.before), after: parse(h.after), why: h.why })),
    versions: versions.map((v) => ({ app: v.app, code: APP_CODES[v.app] ?? null, phones: v.on_phones, share: v.base ? Math.round((1000 * v.on_phones) / v.base) / 10 : null })),
    series: series.map((x) => ({ day: x.day, share: x.base ? Math.round((1000 * x.phones) / x.base) / 10 : null, phones: x.phones })),
    compare: { pivot: iso(pivot), days: 7, bugsBefore: bugs.filter((b) => b.created_at < pivot).length, bugsAfter: bugs.filter((b) => b.created_at >= pivot).length,
      errorsBefore: errBefore, errorsAfter: errAfter, measured: store.state.has(key) },
  });
}

/** A day (YYYY-MM-DD, KST) or a moment as ms, at most a year ahead and after now; null when not given. */
export function readUntil(v, field, now) {
  if (v === undefined || v === null || v === "") return null;
  const ms = typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) ? Date.parse(`${v}T00:00:00+09:00`) : Date.parse(String(v));
  if (!Number.isFinite(ms) || ms <= now || ms > now + FLAG_LIMITS.untilDays * DAY) refuse(field, field);
  return ms;
}

/**
 * 1.28 통합 [FLAG-36] AL-D3: the app versionCode range of a flag (both null: every version), with why. Outside it the
 * phone turns the flag off before anything else but a force off (판단 2단계). Who may: as for its conditions.
 */
export async function setFlagRange(request, env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "flag", field: "key" }, 404);
  if (!flagMay(actor, def, "conditions")) return json({ error: "no_permission", area: def.risk === "LEGAL" ? "owner" : "flags" }, 403);
  const body = await readJson(request, 2048);
  const code = (v, field) => (v === undefined || v === null || v === "" ? null : Number.isInteger(v) && v > 0 && v < 1_000_000 ? v : refuse("app", field));
  const minApp = code(body.minApp, "minApp");
  const maxApp = code(body.maxApp, "maxApp");
  if (minApp !== null && maxApp !== null && maxApp < minApp) refuse("app", "maxApp");
  const why = plainText(body.why, FLAG_LIMITS.why, "why", true);
  const store = await flagStore(env);
  const before = store.overrides.get(key) ?? null;
  await run(env, `INSERT INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto, min_app, max_app) VALUES (?, NULL, ?, NULL, NULL, NULL, ?, ?, ?, 0, ?, ?)
    ON CONFLICT (key) DO UPDATE SET min_app = excluded.min_app, max_app = excluded.max_app, by_name = excluded.by_name, by_id = excluded.by_id, at = excluded.at`,
  key, why, actorName(env), actor.id, now, minApp, maxApp);
  const after = readOverride(await one(env, "SELECT * FROM flag_overrides WHERE key = ?", key));
  await flagHistory(env, now, key, "flag_range", before, after, why);
  await flagTick(env, now);
  return flagDetail(env, actor, key, now);
}

/** [FLAG-21][FLAG-27][ADMUX-23] Force on, force off (reason required, 다시 볼 날), or back to the conditions. */
export async function setFlagForce(request, env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "flag", field: "key" }, 404);
  const body = await readJson(request, 4096);
  const force = body.force === "on" || body.force === "off" ? body.force : body.force === null ? null : refuse("force", "force");
  const store = await flagStore(env);
  const before = store.overrides.get(key) ?? null;
  const current = FL.forceOf(before, now);
  const step = force ?? "clear";
  if (!flagMay(actor, def, step, current)) {
    return json({ error: "no_permission", area: step === "off" || (step === "clear" && current === "on") ? "flagOff" : "owner" }, 403);
  }
  const why = plainText(body.why, FLAG_LIMITS.why, "why", force !== null);
  const until = force ? readUntil(body.until, "flag_until", now) : null;
  const realDay = (v) => typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(`${v}T00:00:00Z`))
    && new Date(`${v}T00:00:00Z`).toISOString().slice(0, 10) === v;
  const review = force === "off" && body.reviewAt ? (realDay(body.reviewAt) && body.reviewAt >= kstDay(now) && body.reviewAt <= kstDay(now + FLAG_LIMITS.untilDays * DAY)
    ? body.reviewAt : refuse("review_at", "reviewAt")) : null;
  // [FLAG-32] At least one way of working stays to choose.
  if (force === "off" && def.group === "type") {
    const left = Object.values(TYPE_FLAGS).filter((k) => k !== key && serverEval(store, k, now).on);
    if (!left.length) return json({ error: "last_type", field: "key" }, 409);
  }
  await run(env, `INSERT INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, 0)
    ON CONFLICT (key) DO UPDATE SET force = excluded.force, why = excluded.why, until = excluded.until, review_at = excluded.review_at,
      by_name = excluded.by_name, by_id = excluded.by_id, at = excluded.at, auto = 0`,
  key, force, why, until, review, actorName(env), actor.id, now);
  const after = readOverride(await one(env, "SELECT * FROM flag_overrides WHERE key = ?", key));
  const action = def.group === "type" && force !== "on" ? (force === "off" ? "type_hide" : "type_show") : force === "off" ? "flag_force_off" : force === "on" ? "flag_force_on" : "flag_clear";
  await flagHistory(env, now, key, action, before, after, why || (force === null ? "조건대로 되돌림" : ""));
  await flagTick(env, now);
  return flagDetail(env, actor, key, now);
}

/** [FLAG-21][ADMUX-23] The conditions changed (checked), or back to the app's own (null). */
export async function setFlagConditions(request, env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "flag", field: "key" }, 404);
  if (!flagMay(actor, def, "conditions")) return json({ error: "no_permission", area: def.risk === "LEGAL" ? "owner" : "flags" }, 403);
  const body = await readJson(request, 8192);
  let conditions = null;
  if (body.conditions !== null && body.conditions !== undefined) {
    const checked = FL.checkConditions(body.conditions);
    if (checked.error) return json({ error: "conditions", field: "conditions", detail: checked.error }, 400);
    const unknown = JSON.stringify(checked.list).match(/"type":"manual","key":"([^"]+)"/g)?.map((m) => m.slice(23, -1)).filter((k) => !Object.hasOwn(FL.MANUAL_NAMES, k)) ?? [];
    if (unknown.length) return json({ error: "conditions", field: "conditions", detail: `모르는 확인 이름이에요: ${unknown[0]}` }, 400);
    conditions = checked.list;
  }
  const why = plainText(body.why, FLAG_LIMITS.why, "why", true);
  const store = await flagStore(env);
  const before = store.overrides.get(key) ?? null;
  await run(env, `INSERT INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto) VALUES (?, NULL, ?, ?, NULL, NULL, ?, ?, ?, 0)
    ON CONFLICT (key) DO UPDATE SET conditions = excluded.conditions, by_name = excluded.by_name, by_id = excluded.by_id, at = excluded.at`,
  key, why, conditions ? JSON.stringify(conditions) : null, actorName(env), actor.id, now);
  const after = readOverride(await one(env, "SELECT * FROM flag_overrides WHERE key = ?", key));
  await flagHistory(env, now, key, "flag_conditions", before, after, why);
  await flagTick(env, now);
  return flagDetail(env, actor, key, now);
}

/** [FLAG-21][FLAG-27] 확인했어요 on a manual condition (or taken back): the owner's when any legal flag waits on it. */
export async function setFlagCheck(request, env, actor, name, now) {
  if (!Object.hasOwn(FL.MANUAL_NAMES, name)) return json({ error: "manual", field: "key" }, 404);
  // The flags that wait on it now: the catalog's conditions or the ones the developer app put in their place.
  const store = await flagStore(env);
  const users = flagKeys(store).map(flagDef).filter((f) => manualKeys(store.overrides.get(f.key)?.conditions ?? f.conditions).includes(name));
  const legal = users.some((f) => f.risk === "LEGAL");
  if (!actor.owner && (legal || !can(actor, "flags", "edit"))) return json({ error: "no_permission", area: legal ? "owner" : "flags" }, 403);
  const body = await readJson(request, 2048);
  if (typeof body.done !== "boolean") refuse("done", "done");
  const note = plainText(body.note, FLAG_LIMITS.why, "note");
  const was = await one(env, "SELECT * FROM flag_checks WHERE key = ?", name);
  if (body.done) {
    await run(env, "INSERT OR REPLACE INTO flag_checks (key, by_name, by_id, at, note) VALUES (?, ?, ?, ?, ?)", name, actorName(env), actor.id, now, note);
  } else {
    await run(env, "DELETE FROM flag_checks WHERE key = ?", name);
  }
  for (const f of users.length ? users : [{ key: name }]) {
    await flagHistory(env, now, f.key, body.done ? "flag_check" : "flag_uncheck", was ? { check: name, at: iso(was.at), by: was.by_name } : null,
      body.done ? { check: name, at: iso(now), by: actorName(env) } : null, `${FL.MANUAL_NAMES[name]}${note ? ` · ${note}` : ""}`);
  }
  await flagTick(env, now);
  return json(await flagList(env, actor, now));
}

/** [FLAG-33] The owner moves a policy version's dates (or adds a version), with why. */
export async function setPolicy(request, env, actor, now) {
  if (!actor.owner) return json({ error: "no_permission", area: "owner" }, 403);
  const body = await readJson(request, 2048);
  if (!/^\d{1,2}\.\d{1,2}$/.test(body.version ?? "")) refuse("policy", "version");
  const day = (v, field, optional = false) => (optional && (v === null || v === undefined || v === "") ? null
    : typeof v === "string" && /^20\d\d-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(`${v}T00:00:00Z`)) ? v : refuse("policy", field));
  const posted = day(body.posted, "posted");
  const effective = day(body.effective, "effective");
  const heldUntil = day(body.heldUntil, "heldUntil", true);
  if (effective < posted || (heldUntil && heldUntil < effective)) refuse("policy", "effective");
  const why = plainText(body.why, FLAG_LIMITS.why, "why", true);
  const raw = (await one(env, "SELECT value FROM settings WHERE key = 'policy_overrides'"))?.value;
  let over = {};
  try {
    over = JSON.parse(raw ?? "{}") ?? {};
  } catch {
    over = {};
  }
  const before = mergePolicy(raw).find((p) => p.version === body.version) ?? null;
  over[body.version] = { posted, effective, heldUntil, unfavourable: Boolean(body.unfavourable) };
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)", JSON.stringify(over));
  await flagHistory(env, now, `policy.${body.version}`, "policy_edit", before, over[body.version], why);
  await flagTick(env, now);
  return json(await flagList(env, actor, now));
}

/** [FLAG-25] 감사 보상 개월 (thanksMonths), the owner's, now on billing's screen. */
export async function setThanks(request, env, actor, now) {
  if (!actor.owner) return json({ error: "no_permission", area: "owner" }, 403);
  const body = await readJson(request, 1024);
  if (!Number.isInteger(body.thanksMonths) || body.thanksMonths < 0 || body.thanksMonths > 12) refuse("thanksMonths", "thanksMonths");
  const before = (await launchState(env)).thanksMonths;
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('thanks_months', ?)", String(body.thanksMonths));
  if (before !== body.thanksMonths) await flagHistory(env, now, "billing", "launch_thanks", { thanksMonths: before }, { thanksMonths: body.thanksMonths }, `감사 보상 ${body.thanksMonths}개월`);
  return flagDetail(env, actor, "billing", now);
}

/** [FLAG-32][ADMUX-26] The six ways of working, shown or hidden. */
export async function typeList(env, actor, now) {
  const store = await flagStore(env);
  return {
    reach: flagReach(actor),
    types: Object.entries(TYPE_FLAGS).map(([style, key]) => ({ style, key, ...flagRow(store, key, now, null), label: FL.WORK_NAMES[style] })),
  };
}

/** [FLAG-21]~[FLAG-33] /api/admin/flags, /flags/:key(/force|/conditions|/thanks), /checks/:name, /policy, /types. */
export async function flagRoute(request, env, url, path, method, actor, now) {
  if (path === "/flags" && method === "GET") return json(await flagList(env, actor, now));
  if (path === "/policy" && method === "POST") return setPolicy(request, env, actor, now);
  if (path === "/types" && method === "GET") return json(await typeList(env, actor, now));
  if (path === "/types" && method === "POST") {
    const body = await readJson(request, 2048);
    const key = TYPE_FLAGS[body.type] ?? refuse("type", "type");
    if (typeof body.show !== "boolean") refuse("show", "show");
    const res = await setFlagForce(new Request(request.url, { method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ force: body.show ? null : "off", why: body.why ?? "" }) }), env, actor, key, now);
    return res.status === 200 ? json(await typeList(env, actor, now)) : res;
  }
  let m = path.match(/^\/checks\/([a-z0-9.]{3,60})$/);
  if (m && method === "POST") return setFlagCheck(request, env, actor, m[1], now);
  m = path.match(/^\/flags\/([A-Za-z0-9._]{2,50})(\/force|\/conditions|\/range|\/thanks)?$/);
  if (!m || !flagDef(m[1])) return json({ error: "not_found" }, 404);
  if (method === "GET" && !m[2]) return flagDetail(env, actor, m[1], now);
  if (method === "POST" && m[2] === "/force") return setFlagForce(request, env, actor, m[1], now);
  if (method === "POST" && m[2] === "/conditions") return setFlagConditions(request, env, actor, m[1], now);
  if (method === "POST" && m[2] === "/range") return setFlagRange(request, env, actor, m[1], now);
  if (method === "POST" && m[2] === "/thanks" && m[1] === "billing") return setThanks(request, env, actor, now);
  return json({ error: "not_found" }, 404);
}

/** [ADMUX-27][FLAG-28] The home screen's part: legal flags on for less than 24 hours, and how many flags want a look. */
export async function flagOverview(env, actor, now = Date.now(), given = null) {
  if (!can(actor, "flags")) return {};
  const store = given ?? (await flagStore(env));
  const rows = flagKeys(store).map((k) => flagRow(store, k, now, null));
  const due = rows.filter((r) => r.reviewDue || r.warnings.length || r.sunsetDue
    || (r.state === "waiting" && r.checklist.every((c) => c.met !== false || c.type === "manual"))).length;
  return {
    flagAlerts: rows.filter((r) => r.alert && r.state !== "forced_off").map((r) => ({ key: r.key, name: r.name, since: r.onSince, reason: r.reason })),
    // flagsWaiting: the same number by the name AN's 출시 hub badge reads.
    flagsDue: due, flagsWaiting: due,
  };
}
/**
 * 1.28 통합 [ADMUX-40][FLAG-30] 분석 › 기능별 효과: every flag with the share of phones it was on for (stats.more, last
 * 7 days) and, once the server saw it turn on or off, problem reports and server errors 7 days before and after,
 * the same numbers the flag's own page shows (flagDetail), for all flags in one trip.
 */
export async function flagEffects(env, actor, now) {
  const store = await flagStore(env);
  const shares = await flagShares(env, now);
  const rows = flagKeys(store).map((k) => flagRow(store, k, now, shares));
  const measured = rows.filter((r) => store.state.get(r.key)?.since);
  const pivots = measured.map((r) => store.state.get(r.key).since);
  const lo = pivots.length ? Math.min(...pivots) - 7 * DAY : now;
  const hi = pivots.length ? Math.max(...pivots) + 7 * DAY : now;
  const [bugs, errors, flips] = pivots.length ? (await batch(env, [
    stmt(env, "SELECT created_at, info FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ? AND created_at < ?", lo, hi),
    stmt(env, "SELECT key, count FROM counters WHERE key >= ? AND key <= ?", `errors|${kstDay(lo)}`, `errors|${kstDay(hi)}`),
    // AU 1.29 [NXT-07]: whether `since` is a turn the server saw (flagTick's audit line) or only when it first looked.
    stmt(env, "SELECT target, at FROM audit WHERE action IN ('flag_auto_on', 'flag_auto_off') AND at >= ?", lo + 7 * DAY),
  ])).map((r) => r.results) : [[], [], []];
  const around = (pivot) => {
    const b = bugs.filter((x) => x.created_at >= pivot - 7 * DAY && x.created_at < pivot + 7 * DAY);
    const e = errors.filter((x) => x.key.slice(7) >= kstDay(pivot - 7 * DAY) && x.key.slice(7) <= kstDay(pivot + 7 * DAY));
    return { pivot: iso(pivot), days: 7, bugsBefore: b.filter((x) => x.created_at < pivot).length, bugsAfter: b.filter((x) => x.created_at >= pivot).length,
      errorsBefore: e.filter((x) => x.key.slice(7) < kstDay(pivot)).reduce((n, x) => n + x.count, 0),
      errorsAfter: e.filter((x) => x.key.slice(7) >= kstDay(pivot)).reduce((n, x) => n + x.count, 0) };
  };
  return {
    serverTime: iso(now), statsPhones: shares.base, flagsReach: can(actor, "flags"),
    flags: rows.map((r) => ({ key: r.key, name: r.name, group: r.group, risk: r.risk, state: r.state, on: r.on, onShare: r.onShare,
      compare: store.state.get(r.key)?.since ? { ...around(store.state.get(r.key).since), split: bugSplit(store, r, bugs, flips) } : null })),
  };
}
// AM 1.28 끝
