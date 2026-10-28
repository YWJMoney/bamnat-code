import { SWITCH_FLAGS, flagDef, flagStore, migrateFlags, serverEval, setFlagForce, switchOff } from "./flags.js";
import { all, allow, appVersions, audit, batch, given, inquiryStats, iso, item, json, list, metricRange, network, one, overLimit, playVitals, randomId, readJson, refuse, run, sha256, shareStats, stmt, versions } from "./index.js";
import { SLA_SQL, plainText, readSla } from "./inquiries.js";
import { DAY, HOUR } from "./units.js";
// ---- running the service, 1.22 [SRV-04][SRV-05][SRV-06][STAT-01][COMM-09][COMM-10][ADMIN-13][ADMIN-14] ----

/** A day as people in Korea count it (KST, no daylight saving), "2026-10-22". */
export const kstDay = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);

/**
 * [SRV-04] What the developer app can switch off for every phone at once. The app hides the way in and
 * says it is resting; the server stops its own part too, so an older app cannot go around it.
 */
export const SWITCHES = {
  PLAZA_WRITE: "광장 글쓰기(글·답글·제안·임금표)",
  COMPANY_REPORT: "회사 규칙 보내기",
  INQUIRY_PHOTOS: "문의 사진 받기",
  SHARE_CREATE: "근무표 링크 새로 만들기",
  USAGE_STATS: "사용 통계 받기",
  PLAZA_NEWS: "광장 소식 알림",
};

/** [SRV-04] A notice the app shows as one line over the calendar; tapping it opens the rest. */
export const NOTICE = { max: 10, title: 40, body: 500, levels: ["info", "important"], longestDays: 90 };

/**
 * [SRV-09] Who a notice is for, chosen on the phone: the server only checks the shape and sends every
 * notice to every app, which keeps those that fit (maker, Android version, way of working, alarms on).
 * Nothing about the phone comes here. And what its button opens in the app, or an https page.
 */
export const AUDIENCE = {
  makers: 10, maker: /^[a-z0-9][a-z0-9 _-]{0,19}$/,
  workTypes: ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"],
  features: ["ALARM", "SHARE", "PAY", "PLAZA", "BACKUP"],
  sdk: [21, 99],
};
export const NOTICE_ACTIONS = ["ALARM_CHECK", "BACKUP", "PRO", "INQUIRY", "SHARE", "PLAZA", "WHATS_NEW", "URL"];

/** [SRV-09] A notice's audience as the developer app sends it; null for everyone. */
export function readAudience(a) {
  if (a === undefined || a === null) return null;
  if (typeof a !== "object" || Array.isArray(a)) refuse("audience", "audience");
  const out = {};
  if (a.makers !== undefined) {
    if (!Array.isArray(a.makers) || a.makers.length > AUDIENCE.makers) refuse("audience", "makers");
    const makers = a.makers.map((m) => String(m).trim().toLowerCase());
    if (!makers.every((m) => AUDIENCE.maker.test(m))) refuse("audience", "makers");
    if (makers.length) out.makers = [...new Set(makers)];
  }
  for (const k of ["minSdk", "maxSdk"]) {
    if (a[k] === undefined || a[k] === null) continue;
    if (!Number.isInteger(a[k]) || a[k] < AUDIENCE.sdk[0] || a[k] > AUDIENCE.sdk[1]) refuse("audience", k);
    out[k] = a[k];
  }
  if (out.minSdk && out.maxSdk && out.maxSdk < out.minSdk) refuse("audience", "maxSdk");
  for (const [k, allowed] of [["workTypes", AUDIENCE.workTypes], ["features", AUDIENCE.features]]) {
    if (a[k] === undefined) continue;
    if (!Array.isArray(a[k]) || !a[k].every((v) => allowed.includes(v))) refuse("audience", k);
    if (a[k].length) out[k] = [...new Set(a[k])];
  }
  return Object.keys(out).length ? out : null;
}

/** [SRV-09] A notice's button: a place in the app, or an https page with a short label. */
export function readAction(a) {
  if (a === undefined || a === null) return null;
  if (typeof a !== "object" || !NOTICE_ACTIONS.includes(a.kind)) refuse("action", "action");
  const label = a.label === undefined || a.label === null || a.label === "" ? null : plainText(a.label, 12, "action", true);
  if (a.kind !== "URL") return { kind: a.kind, ...(label ? { label } : {}) };
  let url;
  try {
    url = new URL(String(a.url ?? ""));
  } catch {
    refuse("action", "url");
  }
  if (url.protocol !== "https:" || String(a.url).length > 300) refuse("action", "url");
  return { kind: "URL", url: url.toString(), ...(label ? { label } : {}) };
}

/** [SRV-04] The notices as stored. */
export async function noticeRows(env) {
  const row = await one(env, "SELECT value FROM settings WHERE key = 'notices'");
  try {
    const v = JSON.parse(row?.value ?? "[]");
    return (Array.isArray(v) ? v : []).filter((n) => n && typeof n.id === "string" && Number.isFinite(n.from) && Number.isFinite(n.until));
  } catch {
    return [];
  }
}

export async function opsState(env) {
  // AM 1.28 [FLAG-24] what is switched off is the six switch flags forced off.
  await migrateFlags(env);
  const [notices, forced] = await Promise.all([noticeRows(env), all(env, "SELECT key, until FROM flag_overrides WHERE force = 'off'")]);
  const now = Date.now();
  return { notices, off: forced.filter((r) => SWITCH_FLAGS[r.key] && !(r.until && r.until <= now)).map((r) => SWITCH_FLAGS[r.key]) };
}

/** [SRV-04] Whether the developer switched [name] off. AM 1.28 [FLAG-24]: the flag's force, read alone. */
export const paused = (env, name) => switchOff(env, name);

export const noticeView = (n) => ({ id: n.id, title: n.title, body: n.body, level: n.level, from: iso(n.from), until: iso(n.until),
  minApp: n.minApp ?? null, maxApp: n.maxApp ?? null, audience: n.audience ?? null, action: n.action ?? null });

/** A notice as the developer app sends it; the app version range is the app's versionCode, either end open. */
export function readNotice(body, now) {
  const title = plainText(body.title, NOTICE.title, "title", true);
  const text = plainText(body.body, NOTICE.body, "body", true);
  const level = NOTICE.levels.includes(body.level) ? body.level : refuse("level", "level");
  const from = Date.parse(body.from ?? "");
  const until = Date.parse(body.until ?? "");
  if (!Number.isFinite(from)) refuse("from", "from");
  if (!Number.isFinite(until) || until <= from || until < now || until - from > NOTICE.longestDays * DAY) refuse("until", "until");
  const version = (v, field) => {
    if (v === undefined || v === null || v === "") return null;
    return Number.isInteger(v) && v > 0 && v < 1_000_000 ? v : refuse("app", field);
  };
  const minApp = version(body.minApp, "minApp");
  const maxApp = version(body.maxApp, "maxApp");
  if (minApp !== null && maxApp !== null && maxApp < minApp) refuse("app", "maxApp");
  return { title, body: text, level, from, until, minApp, maxApp, audience: readAudience(body.audience), action: readAction(body.action) };
}

export async function noticeList(env, now) {
  const s = await opsState(env);
  return {
    // [ADMUX-07] The newest app this server has heard from, for the notice form's 앱 버전 칸.
    latestApp: await latestApp(env, now),
    notices: s.notices.sort((a, b) => b.from - a.from).map((n) => ({ ...noticeView(n), state: now < n.from ? "later" : now < n.until ? "on" : "ended" })),
    switches: Object.entries(SWITCHES).map(([key, label]) => ({ key, label, off: s.off.includes(key) })),
  };
}

/** [ADMUX-07] The highest app version (name and versionCode) in questions of the last 60 days, or null. */
export async function latestApp(env, now) {
  const rows = await all(env, `SELECT DISTINCT json_extract(info, '$.app') AS app FROM inquiries WHERE created_at > ? AND json_valid(info)
    ORDER BY created_at DESC LIMIT 300`, now - 60 * DAY);
  let best = null;
  for (const { app } of rows) {
    const m = String(app ?? "").match(/^\s*([\d.]+)\s*\((\d+)\)/);
    if (m && (!best || Number(m[2]) > best.code)) best = { name: m[1], code: Number(m[2]) };
  }
  return best;
}

/** Adds a notice, or changes one with the same id. Ended ones are dropped when the list is full. */
export async function saveNotice(request, env, now) {
  const body = await readJson(request, 4096);
  const n = readNotice(body, now);
  const s = await opsState(env);
  let list = s.notices.filter((x) => x.until > now - 30 * DAY);
  const id = typeof body.id === "string" && /^n[A-Za-z0-9]{9}$/.test(body.id) ? body.id : null;
  if (id && !list.some((x) => x.id === id)) return json({ error: "not_found" }, 404);
  if (id) list = list.map((x) => (x.id === id ? { id, ...n } : x));
  else {
    if (list.length >= NOTICE.max) list = list.filter((x) => x.until > now);
    if (list.length >= NOTICE.max) refuse("too_many", "notices");
    list.push({ id: `n${randomId(9)}`, ...n });
  }
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('notices', ?)", JSON.stringify(list));
  await audit(env, now, id ? "notice_edit" : "notice_add", n.title);
  return json(await noticeList(env, now));
}

export async function removeNotice(env, id, now) {
  const s = await opsState(env);
  const gone = s.notices.find((x) => x.id === id);
  if (!gone) return json({ error: "not_found" }, 404);
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('notices', ?)", JSON.stringify(s.notices.filter((x) => x.id !== id)));
  await audit(env, now, "notice_remove", gone.title);
  return json(await noticeList(env, now));
}

/** [FLAG-38] What a 끄기 from a developer app cached before 1.28 (no why) is recorded with. */
export const OLD_APP_WHY = "옛 관리자 앱에서 끔";

/**
 * [SRV-04] 기능 스위치, kept for the 운영 screen's old buttons: AM 1.28 [FLAG-24] it forces the switch's flag off (with why)
 * or back to its conditions, under the same rule as 기능 관리 (결정 D97: forcing off is the owner's or flagOff's).
 */
export async function setSwitch(request, env, now) {
  const body = await readJson(request, 1024);
  if (!Object.hasOwn(SWITCHES, body.key)) refuse("key", "key");
  if (typeof body.off !== "boolean") refuse("off", "off");
  const key = Object.keys(SWITCH_FLAGS).find((k) => SWITCH_FLAGS[k] === body.key);
  // 1.28 통합 [FLAG-38] A developer app cached before 1.28 sends no why: its 끄기 still works, and the record says where it came from.
  const given = typeof body.why === "string" ? body.why.trim() : "";
  const old = body.off && !given;
  const res = await setFlagForce(new Request("https://switch/", { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ force: body.off ? "off" : null, why: old ? OLD_APP_WHY : given }) }), env, env.actor, key, now);
  if (res.status !== 200) return res;
  await audit(env, now, body.off ? "switch_off" : "switch_on", body.key, old ? `${SWITCHES[body.key]} · ${OLD_APP_WHY}` : SWITCHES[body.key]);
  return json(await noticeList(env, now));
}

/**
 * [ADMIN-14] The year's chores, month by month, the same list as docs/ops-calendar.md (a test keeps the
 * two together). Shown on the developer app's home screen in place of mail (결정 D1: no mail key).
 */
export const OPS_CALENDAR = [
  [1, "rates-new-year", "새해 최저임금·4대 보험 요율이 앱 계산과 맞는지 확인", "최저임금법 제10조 제2항, 국민건강보험법 시행령 제44조, 노인장기요양보험법 시행령 제4조"],
  [1, "wages-jan", "1월 공표 시중노임단가를 remote/v1/wages.json에 넣기(판 올리고 서명)", "대한건설협회 시중노임단가(1월·9월 공표)"],
  [1, "holidays-this-year", "올해 공휴일·대체공휴일이 달력과 맞는지 확인", "관공서의 공휴일에 관한 규정 제2조·제3조"],
  [3, "tax-table", "근로소득 간이세액표가 바뀌었으면 remote/v1/taxtable.json 고치기(판 올리고 서명)", "소득세법 시행령 별표 2(2026년 개정은 3월 1일 이후 원천징수분부터)"],
  [3, "privacy-review", "처리방침·데이터 보안 답안을 지금 서버와 대조", "개인정보 보호법 제30조, docs/server-data.md"],
  [3, "incident-drill", "유출 대응 절차서를 한 번 따라 해 보기(연락 순서·공지 문구)", "개인정보 보호법 제34조, docs/incident-response.md"],
  [4, "android-preview", "새 Android 미리보기에서 알람·알림 점검", "Play 대상 API 수준 요건(매년 8월 31일)"],
  [5, "store-listing", "스토어 등록정보·화면 사진이 지금 앱과 맞는지 확인", "Google Play 메타데이터 정책"],
  [6, "pension-prepare", "7월부터 쓰는 국민연금 기준소득월액 상하한 준비", "국민연금법 시행령 제5조"],
  [6, "labor-law-amendment", "2027-06-10 시행 근로기준법 개정(조문 번호 이동, 시간 단위 연차)에 맞춰 앱의 법 조문 글귀와 연차 계산을 확인", "근로기준법 개정(2027-06-10 시행), docs/tracker.csv LEAVE-12"],
  [7, "pension-apply", "국민연금 기준소득월액 상하한을 remote/v1/rates.json에 넣어 7월 급여부터 맞추기(판 올리고 서명)", "국민연금법 시행령 제5조"],
  [8, "minwage-next", "다음 해 최저임금 결정(8월 5일까지)을 앱에 넣을 준비", "최저임금법 제8조 제1항"],
  [8, "target-api", "대상 API 수준 마감(8월 31일) 전에 업데이트 출시", "Google Play 대상 API 수준 요건"],
  [9, "wages-sep", "9월 공표 시중노임단가를 remote/v1/wages.json에 넣기(판 올리고 서명)", "대한건설협회 시중노임단가(1월·9월 공표)"],
  [9, "holidays-next", "다음 해 공휴일·대체공휴일·선거일 확인", "관공서의 공휴일에 관한 규정"],
  [10, "health-next", "다음 해 건강보험료율이 결정되면 remote/v1/rates.json에 넣기(결정 시기는 해마다 확인)", "국민건강보험법 시행령 제44조"],
  [11, "ltc-next", "다음 해 장기요양보험료율이 결정되면 remote/v1/rates.json에 넣기(2027년 요율은 결정되는 대로)", "노인장기요양보험법 시행령 제4조"],
  [12, "release-new-year", "새해 요율·최저임금·공휴일을 넣은 업데이트를 1월 1일 전에 출시", "위 항목들"],
  [12, "server-data", "서버 최소 데이터 점검(지울 것은 지우기)", "개인정보 보호법 제21조, docs/server-data.md"],
].map(([month, key, text, source]) => ({ month, key, text, source }));

/** One month's chores with which of them are ticked ([done] is the stored list of keys). */
export function opsItems(month, done) {
  let ticked = [];
  try {
    ticked = JSON.parse(done ?? "[]");
  } catch {
    ticked = [];
  }
  const m = Number(month.slice(5, 7));
  return OPS_CALENDAR.filter((i) => i.month === m).map((i) => ({ key: i.key, text: i.text, source: i.source, done: ticked.includes(i.key) }));
}

export const MONTH = /^20\d\d-(0[1-9]|1[0-2])$/;

export async function opsMonth(env, month, now) {
  const m = MONTH.test(month ?? "") ? month : kstDay(now).slice(0, 7);
  const row = await one(env, "SELECT value FROM settings WHERE key = ?", `ops:${m}`);
  return { month: m, items: opsItems(m, row?.value) };
}

export async function checkOps(request, env, now) {
  const body = await readJson(request, 1024);
  if (!MONTH.test(body.month ?? "")) refuse("month", "month");
  const item = OPS_CALENDAR.find((i) => i.key === body.key && i.month === Number(body.month.slice(5, 7)));
  if (!item) refuse("key", "key");
  if (typeof body.done !== "boolean") refuse("done", "done");
  const row = await one(env, "SELECT value FROM settings WHERE key = ?", `ops:${body.month}`);
  const was = opsItems(body.month, row?.value).filter((i) => i.done).map((i) => i.key);
  const next = body.done ? [...new Set([...was, item.key])] : was.filter((k) => k !== item.key);
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", `ops:${body.month}`, JSON.stringify(next));
  await audit(env, now, body.done ? "ops_done" : "ops_undo", body.month, item.text);
  return json(await opsMonth(env, body.month, now));
}

// [STAT-01] Usage statistics (결정 D2·D42): only phones whose user said yes send, once a day, the days
// before today as event names and counts. The app keeps the same list (core UsageEvents.kt); a name the
// server does not know is skipped, so a newer app still sends the rest.

/**
 * [STAT-05] Up to 120 bundles a day from one network: a mobile carrier puts thousands of phones behind
 * one address, so the old 6 dropped most of them. The same bundle sent twice (a retry) counts once a day.
 */
export const USAGE = { days: 7, keepDays: 400, count: 1000, bundlesPerNetworkDay: 1200, bytes: 8 * 1024 };

/** Event names and what the dashboard calls them. */
export const EVENTS = {
  open: "앱 열기",
  pay_view: "급여 화면",
  pay_setup: "급여 설정 마침",
  pay_report: "급여 리포트",
  paywall_view: "프로 화면 보기",
  trial_start: "무료 체험 시작",
  subscribe: "구독 시작",
  pro_sub: "프로 구독 중",
  share_made: "근무표 링크 만들기",
  share_received: "받은 근무표 넣기",
  plaza_post: "광장 글쓰기",
  plaza_apply: "광장 규칙 적용",
  company_report: "회사 규칙 보내기",
  inquiry: "문의 보내기",
  backup: "백업 만들기",
  alarm_rang: "근무 알람 울림",
  ics_export: "캘린더로 내보내기",
  // [STAT-06] 1.23: a notice opened (how often, never which or by whom).
  notice_open: "공지 열기",
};

export async function usage(request, env) {
  // Switched off, the server takes nothing and the app does not try again until tomorrow.
  if (await paused(env, "USAGE_STATS")) return new Response(null, { status: 204 });
  const body = await readJson(request, USAGE.bytes);
  if (body.v !== 1) refuse("version", "v");
  if (typeof body.app !== "string" || !/^\d{1,2}\.\d{1,3}\.\d{1,3}$/.test(body.app)) refuse("app", "app");
  if (!Array.isArray(body.days) || body.days.length < 1 || body.days.length > USAGE.days) refuse("days", "days");
  const now = Date.now();
  const net = await network(request, env, now);
  const limitHit = await overLimit(env, now, [["network", `events|${net.day}`, USAGE.bundlesPerNetworkDay, DAY]]);
  if (limitHit) return limitHit;
  // [STAT-05] A bundle already counted today (the phone sent it again after a timeout) is not counted twice.
  const bundle = (await sha256(`events|${kstDay(now)}|${JSON.stringify(body)}`)).slice(0, 32);
  if (!(await allow(env, `bundle|${bundle}`, 1, DAY, now))) return json({ ok: true, again: true });
  // A phone's day can be a day off Korea's; one ahead of tomorrow or older than the week is not a day it kept.
  // [STAT-05] Such a day is left out and the rest counted, instead of refusing the whole bundle.
  const latest = kstDay(now + DAY);
  const oldest = kstDay(now - (USAGE.days + 1) * DAY);
  const days = new Set();
  const rows = [];
  let skipped = 0;
  for (const d of body.days) {
    if (!d || typeof d !== "object" || typeof d.day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(d.day)) refuse("day", "day");
    if (d.day > latest || d.day < oldest || days.has(d.day)) {
      skipped++;
      continue;
    }
    days.add(d.day);
    if (!d.events || typeof d.events !== "object" || Array.isArray(d.events)) refuse("events", "events");
    const recent = d.recent ?? [];
    if (!Array.isArray(recent) || recent.length > Object.keys(EVENTS).length * 2) refuse("recent", "recent");
    const counts = new Map();
    for (const [name, n] of Object.entries(d.events)) {
      if (!Number.isInteger(n) || n < 1 || n > USAGE.count) refuse("count", "events");
      if (Object.hasOwn(EVENTS, name)) counts.set(name, n);
    }
    const lately = new Set(recent.filter((e) => typeof e === "string" && Object.hasOwn(EVENTS, e)));
    for (const e of counts.keys()) lately.add(e);
    for (const e of lately) rows.push([d.day, e, counts.has(e) ? 1 : 0, counts.get(e) ?? 0, 1]);
  }
  const app = body.app;
  if (days.size === 0) return json({ ok: true, skipped });
  // AM 1.28 [FLAG-30] Which flags were on (only phones with stats.more on send it): per day, the list sent with the day or,
  // once for the bundle, flagsOn at the top for its newest day. '*' counts the phones that sent a list at all.
  const listOf = (v) => {
    if (v === undefined || v === null) return null;
    if (!Array.isArray(v) || v.length > 200) refuse("flagsOn", "flagsOn");
    return [...new Set(v.filter((k) => typeof k === "string" && k.length <= 60 && flagDef(k)))];
  };
  const flagDays = new Map();
  const top = listOf(body.flagsOn);
  if (top) flagDays.set([...days].sort().at(-1), top);
  for (const d of body.days) if (days.has(d.day) && d.flagsOn !== undefined) flagDays.set(d.day, listOf(d.flagsOn) ?? []);
  const flagRows = [...flagDays].flatMap(([day, keys]) => [[day, "*"], ...keys.map((k) => [day, k])]);
  const stmts = [...days].map((day) => stmt(env, `INSERT INTO usage_days (day, app, phones) VALUES (?, ?, 1)
    ON CONFLICT (day, app) DO UPDATE SET phones = phones + 1`, day, app));
  // AO 1.28 [PERF-17] Startup times, counted once on the bundle's newest day; a wrong part is left out, the rest counts.
  const startupDay = [...days].sort().at(-1);
  // 1.31 [STAT-08] Lookup times only once 처리방침 3.5 is in force (stats.lookup's date); before it they are left out.
  const lookups = lookupRows(body.lookups);
  const lookupOn = lookups.length > 0 && serverEval(await flagStore(env), "stats.lookup", now).on;
  for (const [kind, step, bucket] of [...startupRows(body.startup), ...(lookupOn ? lookups : [])]) {
    stmts.push(stmt(env, `INSERT INTO usage_startup (day, app, kind, step, bucket, phones) VALUES (?, ?, ?, ?, ?, 1)
      ON CONFLICT (day, app, kind, step, bucket) DO UPDATE SET phones = phones + 1`, startupDay, app, kind, step, bucket));
  }
  // D1 binds at most 100 values a query: 16 rows of six.
  for (let i = 0; i < rows.length; i += 16) {
    const part = rows.slice(i, i + 16);
    stmts.push(stmt(env, `INSERT INTO usage_events (day, app, event, phones, total, recent) VALUES ${part.map(() => "(?, ?, ?, ?, ?, ?)").join(", ")}
      ON CONFLICT (day, app, event) DO UPDATE SET phones = phones + excluded.phones, total = total + excluded.total, recent = recent + excluded.recent`,
    ...part.flatMap(([day, e, phones, total, recent]) => [day, app, e, phones, total, recent])));
  }
  for (let i = 0; i < flagRows.length; i += 25) {
    const part = flagRows.slice(i, i + 25);
    stmts.push(stmt(env, `INSERT INTO usage_flags (day, app, flag, phones) VALUES ${part.map(() => "(?, ?, ?, 1)").join(", ")}
      ON CONFLICT (day, app, flag) DO UPDATE SET phones = phones + 1`, ...part.flatMap(([day, k]) => [day, app, k])));
  }
  await batch(env, stmts);
  return skipped ? json({ ok: true, skipped }) : new Response(null, { status: 204 });
}

// AO 1.28 시작 ------------------------------------------------------------------------------------------------
/**
 * [PERF-16][PERF-17] The bucket edges (ms) the app rounds a startup time up to (app diag/StartupStats.BUCKETS): only
 * these values are taken, so no phone's exact time is kept. 20000 means over 15 seconds.
 */
export const STARTUP_BUCKETS = [100, 200, 300, 400, 500, 700, 1000, 1500, 2000, 3000, 4000, 6000, 10000, 15000, 20000];
/** What each kind of opening may carry: a cold one from the process start, a warm one from the screen. */
export const STARTUP_STEPS = { cold: ["container", "activity", "firstMonth"], warm: ["container", "firstMonth"] };
/**
 * 1.31 [STAT-08] N4: how long looking things up took on the phone (only phones with stats.lookup on, 처리방침 3.5), per
 * day the middle value rounded up to a [STARTUP_BUCKETS] edge: from the tap to what was waited for ([wait]) and the
 * server's answers by kind of path ([call], the app's NetTimings.Kind). Never what was looked up.
 */
export const LOOKUP_STEPS = { wait: ["find", "company", "plaza"], call: ["search", "company", "plaza", "talk", "write"] };

/**
 * [PERF-17] `startup: {container, activity, firstMonth, warm: {container, firstMonth}}` → [kind, step, bucket] rows.
 * Anything else (a missing part, a number off the edges, an unknown key) is left out without refusing the bundle.
 */
export function startupRows(startup) {
  const rows = [];
  if (!startup || typeof startup !== "object" || Array.isArray(startup)) return rows;
  const take = (kind, part) => {
    if (!part || typeof part !== "object" || Array.isArray(part)) return;
    for (const step of STARTUP_STEPS[kind]) {
      const v = part[step];
      if (Number.isInteger(v) && STARTUP_BUCKETS.includes(v)) rows.push([kind, step, v]);
    }
  };
  take("cold", startup);
  take("warm", startup.warm);
  return rows;
}

/** 1.31 [STAT-08] `lookups: {wait: {find, company, plaza}, call: {search, …}}` → [kind, step, bucket] rows, the same way. */
export function lookupRows(lookups) {
  const rows = [];
  if (!lookups || typeof lookups !== "object" || Array.isArray(lookups)) return rows;
  for (const [kind, steps] of Object.entries(LOOKUP_STEPS)) {
    const part = lookups[kind];
    if (!part || typeof part !== "object" || Array.isArray(part)) continue;
    for (const step of steps) if (Number.isInteger(part[step]) && STARTUP_BUCKETS.includes(part[step])) rows.push([kind, step, part[step]]);
  }
  return rows;
}

/** [PERF-17] The bucket where [share] of the phones are at or under, from {bucket → phones}. */
export function bucketQuantile(hist, total, share) {
  let seen = 0;
  for (const b of STARTUP_BUCKETS) {
    seen += hist.get(b) ?? 0;
    if (seen >= total * share) return b;
  }
  return STARTUP_BUCKETS.at(-1);
}

export const appOrder = (a, b) => {
  const pa = a.split(".").map(Number);
  const pb = b.split(".").map(Number);
  for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pb[i] - pa[i];
  return 0;
};

/**
 * [PERF-17] 지표 › 앱 시작 시간: per app version (newest first, at most 6) and kind, each step's phones, middle value and
 * 90th percentile (the bucket's upper edge: "at most"). Under 30 phones a version says 데이터 부족 in the admin app.
 */
export async function startupMetrics(env, from, to) {
  const rows = await all(env, `SELECT app, kind, step, bucket, SUM(phones) AS phones FROM usage_startup
    WHERE day BETWEEN ? AND ? GROUP BY app, kind, step, bucket`, from, to);
  const byApp = new Map();
  for (const r of rows) {
    const key = `${r.app}|${r.kind}|${r.step}`;
    if (!byApp.has(r.app)) byApp.set(r.app, new Map());
    const hist = byApp.get(r.app);
    if (!hist.has(key)) hist.set(key, new Map());
    hist.get(key).set(r.bucket, (hist.get(key).get(r.bucket) ?? 0) + r.phones);
  }
  const versions = [...byApp.keys()].sort(appOrder).slice(0, 6).map((app) => {
    const out = { app };
    for (const [kind, steps] of Object.entries({ ...STARTUP_STEPS, ...LOOKUP_STEPS })) {
      const part = {};
      for (const step of steps) {
        const hist = byApp.get(app).get(`${app}|${kind}|${step}`);
        if (!hist) continue;
        const phones = [...hist.values()].reduce((n, v) => n + v, 0);
        part[step] = { phones, p50: bucketQuantile(hist, phones, 0.5), p90: bucketQuantile(hist, phones, 0.9) };
      }
      if (Object.keys(part).length) out[kind] = part;
    }
    return out;
  });
  return { versions, few: 30 };
}
// AO 1.28 끝 --------------------------------------------------------------------------------------------------

// AQ 1.29 시작 ------------------------------------------------------------------------------------------------
/**
 * [PHOTO-09][PHOTO-10] The screens with a '사진이 달라요' button and the reasons it offers, the same codes as the app's
 * core/stats/PhotoWrong.kt (PhotoScreen, PhotoWrongReason). Anything else is refused.
 */
export const PHOTO_SCREENS = {
  roster: "근무표 사진", payslip: "명세서 사진", rules: "회사 규칙 사진", pay_table: "호봉표 사진",
  invest: "투자 캡처", tax: "세금 서류 사진", receipt: "영수증 사진",
};
export const PHOTO_REASONS = {
  numbers: "숫자가 틀려요", missing: "빠진 칸이 있어요", extra: "없는 칸이 생겼어요", names: "이름이나 종류가 틀려요",
  nothing: "거의 읽지 못했어요",
  hourly: "통상시급이 달라요", hours: "기준 시간이 달라요", // AV 1.30 [PHOTO-28] 명세서로 계산한 통상시급·기준 시간이 틀렸을 때
  other: "다른 점이 있어요",
};
/** Rows a bundle may carry, days a phone keeps them (the app's PhotoWrongBook), presses a row, bundles a network a day. */
export const PHOTO_WRONG = { rows: 60, keepDays: 60, count: 99, perNetworkDay: 300, bytes: 8 * 1024 };

/**
 * [PHOTO-10] `{v:1, app:"1.29.0", counts:[{day, screen, reason, n}]}`, sent by the app once 처리방침 3.3 lets it (flag
 * photo.wrong). Added to the day's totals; the same bundle sent again the same day (after a timeout) counts once.
 */
export async function photoWrong(request, env) {
  const body = await readJson(request, PHOTO_WRONG.bytes);
  if (body.v !== 1) refuse("version", "v");
  if (typeof body.app !== "string" || !/^\d{1,2}\.\d{1,3}\.\d{1,3}$/.test(body.app)) refuse("app", "app");
  if (!Array.isArray(body.counts) || body.counts.length < 1 || body.counts.length > PHOTO_WRONG.rows) refuse("counts", "counts");
  const now = Date.now();
  const latest = kstDay(now + DAY);
  const oldest = kstDay(now - (PHOTO_WRONG.keepDays + 1) * DAY);
  const rows = new Map();
  for (const c of body.counts) {
    if (!c || typeof c !== "object" || Array.isArray(c)) refuse("count", "counts");
    if (typeof c.day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(c.day) || c.day > latest || c.day < oldest) refuse("day", "day");
    if (typeof c.screen !== "string" || !Object.hasOwn(PHOTO_SCREENS, c.screen)) refuse("screen", "screen");
    if (typeof c.reason !== "string" || !Object.hasOwn(PHOTO_REASONS, c.reason)) refuse("reason", "reason");
    if (!Number.isInteger(c.n) || c.n < 1 || c.n > PHOTO_WRONG.count) refuse("n", "n");
    const key = `${c.day}|${c.screen}|${c.reason}`;
    rows.set(key, Math.min(PHOTO_WRONG.count, (rows.get(key) ?? 0) + c.n));
  }
  const net = await network(request, env, now);
  const limitHit = await overLimit(env, now, [["network", `photo-wrong|${net.day}`, PHOTO_WRONG.perNetworkDay, DAY]]);
  if (limitHit) return limitHit;
  const bundle = (await sha256(`photo-wrong|${kstDay(now)}|${JSON.stringify(body)}`)).slice(0, 32);
  if (!(await allow(env, `bundle|${bundle}`, 1, DAY, now))) return json({ ok: true, again: true });
  const list = [...rows].map(([k, n]) => [...k.split("|"), n]);
  const stmts = [];
  // D1 binds at most 100 values a query: 20 rows of five.
  for (let i = 0; i < list.length; i += 20) {
    const part = list.slice(i, i + 20);
    stmts.push(stmt(env, `INSERT INTO photo_wrong (day, app, screen, reason, total) VALUES ${part.map(() => "(?, ?, ?, ?, ?)").join(", ")}
      ON CONFLICT (day, app, screen, reason) DO UPDATE SET total = total + excluded.total`,
    ...part.flatMap(([day, screen, reason, n]) => [day, body.app, screen, reason, n])));
  }
  await batch(env, stmts);
  return new Response(null, { status: 204 });
}

/** [PHOTO-10] 지표 › 사진이 달라요: totals per day, per screen (with its reasons) and per app version, from..to. */
export async function photoWrongMetrics(env, from, to) {
  const rows = await all(env, `SELECT day, app, screen, reason, SUM(total) AS total FROM photo_wrong
    WHERE day BETWEEN ? AND ? GROUP BY day, app, screen, reason`, from, to);
  const days = new Map();
  const screens = new Map();
  const apps = new Map();
  let total = 0;
  for (const r of rows) {
    total += r.total;
    days.set(r.day, (days.get(r.day) ?? 0) + r.total);
    apps.set(r.app, (apps.get(r.app) ?? 0) + r.total);
    if (!screens.has(r.screen)) screens.set(r.screen, { screen: r.screen, label: PHOTO_SCREENS[r.screen] ?? r.screen, total: 0, reasons: {} });
    const s = screens.get(r.screen);
    s.total += r.total;
    s.reasons[r.reason] = (s.reasons[r.reason] ?? 0) + r.total;
  }
  return {
    total,
    days: [...days].sort((a, b) => a[0].localeCompare(b[0])).map(([day, n]) => ({ day, total: n })),
    screens: [...screens.values()].sort((a, b) => b.total - a.total),
    apps: [...apps].sort((a, b) => appOrder(a[0], b[0])).map(([app, n]) => ({ app, total: n })),
    reasons: PHOTO_REASONS,
  };
}
// AQ 1.29 끝 --------------------------------------------------------------------------------------------------

/**
 * [ADMIN-13] The gate numbers from what consenting phones sent over the last [days] full days. A share
 * is phone-days that did it within 28 days over all phone-days: about how many of the phones in use
 * do it, without knowing which phones they are. Under 30 phones a day it says 데이터 부족.
 * [ADMUX-13]~[ADMUX-16] 1.25: any period up to 180 days (from·to), how many phones on which app, the
 * plaza, how fast questions get their first answer, server errors and, with Play's numbers, crashes.
 */
export async function metrics(env, url, now) {
  const r = metricRange(url, now);
  const { from, to, days } = r;
  const start = Date.parse(`${from}T00:00:00+09:00`);
  const end = Date.parse(`${to}T00:00:00+09:00`) + DAY;
  const lastWeek = kstDay(Date.parse(`${to}T00:00:00+09:00`) - 6 * DAY);
  const byDay = (col) => `strftime('%Y-%m-%d', (${col} + 32400000) / 1000, 'unixepoch')`;
  const [dayRows, eventRows, subs, beforeRows, versionRows, reportRows, ruleRows, postRows, flagRows, hiddenRows, useRows, rateRows, inquiryRows, errorRows, bugRows, slaRows] = (await batch(env, [
    stmt(env, "SELECT day, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY day ORDER BY day", from, to),
    stmt(env, `SELECT event, SUM(phones) AS phones, SUM(total) AS total, SUM(recent) AS recent FROM usage_events
      WHERE day BETWEEN ? AND ? GROUP BY event`, from, to),
    stmt(env, `SELECT SUM(CASE WHEN trial = 1 THEN 1 ELSE 0 END) AS trials, SUM(CASE WHEN trial = 0 THEN 1 ELSE 0 END) AS paid,
      SUM(CASE WHEN auto_renew = 0 THEN 1 ELSE 0 END) AS ending FROM subs WHERE state IN ('ACTIVE', 'GRACE') AND expiry > ?`, now),
    // [ADMUX-07] The period just before, day by day, for the chart's dashed line.
    stmt(env, "SELECT day, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY day", r.beforeFrom, r.beforeTo),
    // [ADMUX-13] Which app the phones of the period's last seven days run.
    stmt(env, "SELECT app, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY app ORDER BY phones DESC", lastWeek, to),
    // [ADMUX-14] The plaza, day by day.
    stmt(env, `SELECT ${byDay("created_at")} AS day, COUNT(*) AS n, COUNT(DISTINCT device) AS people FROM reports WHERE created_at >= ? AND created_at < ? GROUP BY day`, start, end),
    stmt(env, `SELECT ${byDay("adopted_at")} AS day, COUNT(*) AS n FROM rules WHERE adopted_at >= ? AND adopted_at < ? GROUP BY day`, start, end),
    stmt(env, `SELECT ${byDay("created_at")} AS day, COUNT(*) AS n FROM posts WHERE created_at >= ? AND created_at < ? GROUP BY day`, start, end),
    stmt(env, `SELECT ${byDay("at")} AS day, COUNT(*) AS n FROM post_flags WHERE at >= ? AND at < ? GROUP BY day`, start, end),
    stmt(env, "SELECT COUNT(*) AS n FROM posts WHERE hide_reason = 'flags' AND hidden_at >= ? AND hidden_at < ?", start, end),
    stmt(env, `SELECT ${byDay("at")} AS day, COUNT(*) AS n FROM uses WHERE at >= ? AND at < ? GROUP BY day`, start, end),
    stmt(env, "SELECT COUNT(*) AS n, SUM(CASE WHEN status = 'adopted' THEN 1 ELSE 0 END) AS adopted FROM rates WHERE created_at >= ? AND created_at < ?", start, end),
    // [ADMUX-14] Each question of the period and when it first got an answer.
    stmt(env, `SELECT i.kind, i.created_at, i.rating, (SELECT MIN(m.at) FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin') AS first
      FROM inquiries i WHERE i.created_at >= ? AND i.created_at < ?`, start, end),
    // [ADMUX-16] Requests the worker answered with 500, counted per Korean day.
    stmt(env, "SELECT key, count FROM counters WHERE key BETWEEN ? AND ?", `errors|${from}`, `errors|${to}`),
    stmt(env, `SELECT COALESCE(json_extract(info, '$.app'), '') AS app, COUNT(*) AS n FROM inquiries
      WHERE kind = 'bug' AND created_at >= ? AND created_at < ? AND json_valid(info) GROUP BY app ORDER BY n DESC LIMIT 10`, start, end),
    env.DB.prepare(SLA_SQL),
  ])).map((x) => x.results);
  const was = new Map(beforeRows.map((x) => [x.day, x.phones]));
  const phoneDays = dayRows.reduce((n, x) => n + x.phones, 0);
  const daily = dayRows.length ? Math.round(phoneDays / dayRows.length) : 0;
  const ev = new Map(eventRows.map((x) => [x.event, x]));
  const share = (e) => (phoneDays ? (ev.get(e)?.recent ?? 0) / phoneDays : null);
  const enough = daily >= 30;
  const gate = (name, rule, e, least) => {
    const v = share(e);
    return { name, rule, value: v === null ? null : Math.round(v * 1000) / 10, status: !enough || v === null ? "few" : v * 100 >= least ? "pass" : "fail" };
  };
  const s = subs[0] ?? {};
  const week = dayRows.filter((x) => x.day >= lastWeek);
  const vitals = await playVitals(env, now);
  const vital = (name, rule, key, least) => {
    const v = vitals?.[key];
    if (v === undefined || v === null) return { name, rule, value: null, status: vitals?.error ? "denied" : "play" };
    return { name, rule, value: Math.round(v * 10000) / 100, status: vitals.users < 100 ? "few" : v * 100 < least ? "pass" : "fail" };
  };
  const dayList = [];
  for (let d = start; d < end; d += DAY) dayList.push(kstDay(d));
  const series = (rows) => {
    const m = new Map(rows.map((x) => [x.day, x.n]));
    return dayList.map((day) => m.get(day) ?? 0);
  };
  const total = (rows) => rows.reduce((n, x) => n + x.n, 0);
  const sla = readSla(slaRows);
  return {
    from, to, days, phoneDays, daily, enough,
    series: dayRows.map((x) => ({ day: x.day, phones: x.phones })),
    // The same day [days] earlier for each day of the series, 0 when nothing came that day.
    before: dayRows.map((x) => was.get(kstDay(Date.parse(`${x.day}T00:00:00+09:00`) - days * DAY)) ?? 0),
    events: Object.entries(EVENTS).map(([event, label]) => {
      const e = ev.get(event);
      return { event, label, phones: e?.phones ?? 0, total: e?.total ?? 0, share: share(event) === null ? null : Math.round(share(event) * 1000) / 10 };
    }),
    gates: [
      // [ADMUX-16] Play's own numbers when the service account may read them; until then the row says where to look.
      { gate: "G0", ...vital("비정상 종료", "1.09% 미만(내부 0.5%)", "crash", 1.09) },
      { gate: "G0", ...vital("ANR", "0.47% 미만(내부 0.2%)", "anr", 0.47) },
      { gate: "G0", name: "테스터", rule: "12명이 14일", value: null, status: "play" },
      { gate: "G1", ...gate("급여 화면을 본 사용자", "30% 이상", "pay_view", 30) },
      { gate: "G2", ...gate("근무표 링크를 만든 사용자", "15% 이상", "share_made", 15) },
      { gate: "G2", ...gate("유료 구독 사용자", "2.0% 이상", "pro_sub", 2) },
    ],
    funnel: {
      paywall: ev.get("paywall_view")?.phones ?? 0, trial: ev.get("trial_start")?.total ?? 0, subscribe: ev.get("subscribe")?.total ?? 0,
      trials: s.trials ?? 0, paid: s.paid ?? 0, ending: s.ending ?? 0,
    },
    shares: await shareStats(env, days),
    // [ADMUX-13] Phone-days only (결정 D42): the same phone on seven days counts seven times, so a week is an average.
    active: {
      daily, week: week.length ? Math.round(week.reduce((n, x) => n + x.phones, 0) / week.length) : 0,
      most: dayRows.reduce((m, x) => (x.phones > (m?.phones ?? -1) ? x : m), null),
      versions: appVersions(versionRows),
    },
    dayList,
    plaza: {
      reports: total(reportRows), people: reportRows.reduce((n, x) => n + x.people, 0), adopted: total(ruleRows), posts: total(postRows),
      flags: total(flagRows), hidden: hiddenRows[0]?.n ?? 0, applied: total(useRows), rates: rateRows[0]?.n ?? 0, ratesAdopted: rateRows[0]?.adopted ?? 0,
      series: { reports: series(reportRows), adopted: series(ruleRows), posts: series(postRows), flags: series(flagRows), applied: series(useRows) },
    },
    inquiries: inquiryStats(inquiryRows, sla, dayList, now),
    errors: {
      server: series(errorRows.map((x) => ({ day: x.key.slice(7), n: x.count }))),
      serverTotal: errorRows.reduce((n, x) => n + x.count, 0),
      bugs: bugRows.map((x) => ({ app: x.app || "모름", n: x.n })),
      play: vitals ? { users: vitals.users ?? null, day: vitals.day ?? null, error: vitals.error ?? null } : null,
    },
  };
}

