import { all, run } from "./index.js";
import { DAY, HOUR } from "./units.js";

// ---- 1.38 [ADM3-05][ADM3-13][ADM3-19] 서버가 스스로 보는 눈: 정기 작업, 오류 묶음, 응답 속도 ----
//
// 사용자 2026-11-06 "관리성 관측성 유지보수성 업무성 … 1인 관리자 입장에서 모든 사항을 누락없이". 보고서
// /mnt/project-files/조사/1.38-관리자앱-전면리뷰-보고서.md 3-2항 O2·O3·O5.
//   정기 작업 [ADM3-05]  매시 작업의 단계마다 따로 잡아요. 한 단계가 실패해도 다음 단계는 돌고, 단계마다 마지막 성공·실패·걸린 시간이 남아요.
//   오류 묶음 [ADM3-13]  서버가 500으로 답한 요청을 주소(번호는 :id로)와 오류 첫 줄로 묶어 처음·마지막 본 때·날마다 건수·앱 판을 남겨요.
//                       휴대폰·사람을 가리키는 값은 남기지 않아요(주소의 번호는 지우고, 오류 글은 300자까지).
//   응답 속도 [ADM3-19]  /api/ 요청을 시간·주소마다 건수·500 수·걸린 시간 칸(50ms~3.2초)으로 세어 isolate 메모리에 모았다가 1분마다 더해요.

export const WATCH = {
  /** 걸린 시간 칸의 위쪽 끝(ms). 마지막 칸은 그보다 긴 것. */
  edges: [50, 100, 200, 400, 800, 1600, 3200],
  flushMs: 60 * 1000,
  keepStatsDays: 14,
  keepErrorDays: 90,
  /** 이 시간 안에 같은 단계가 성공하지 않았으면 '멈춤'. 매시 작업이라 3시간. */
  staleMs: 3 * HOUR,
  /** 오류 글·예시 길이. */
  message: 300,
  sample: 900,
  /** 한 시간 동안 메모리에 모을 주소 수(그 밖은 '기타'). */
  routes: 120,
};

export const WATCH_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS job_runs (step TEXT PRIMARY KEY, ok_at INTEGER, fail_at INTEGER, error TEXT NOT NULL DEFAULT '', ms INTEGER NOT NULL DEFAULT 0,
    runs INTEGER NOT NULL DEFAULT 0, fails INTEGER NOT NULL DEFAULT 0, last_at INTEGER)`,
  `CREATE TABLE IF NOT EXISTS error_groups (sig TEXT PRIMARY KEY, route TEXT NOT NULL, message TEXT NOT NULL, sample TEXT NOT NULL DEFAULT '',
    app TEXT NOT NULL DEFAULT '', first_at INTEGER NOT NULL, last_at INTEGER NOT NULL, total INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'new', note TEXT NOT NULL DEFAULT '', seen_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS error_groups_last ON error_groups (last_at)",
  "CREATE TABLE IF NOT EXISTS error_days (day TEXT NOT NULL, sig TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY (day, sig))",
  `CREATE TABLE IF NOT EXISTS req_stats (hour TEXT NOT NULL, route TEXT NOT NULL, n INTEGER NOT NULL, err INTEGER NOT NULL, ms_sum INTEGER NOT NULL,
    ms_max INTEGER NOT NULL, b0 INTEGER NOT NULL DEFAULT 0, b1 INTEGER NOT NULL DEFAULT 0, b2 INTEGER NOT NULL DEFAULT 0, b3 INTEGER NOT NULL DEFAULT 0,
    b4 INTEGER NOT NULL DEFAULT 0, b5 INTEGER NOT NULL DEFAULT 0, b6 INTEGER NOT NULL DEFAULT 0, b7 INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (hour, route))`,
];

const kst = (ms) => new Date(ms + 9 * HOUR).toISOString();
export const watchHour = (ms) => kst(ms).slice(0, 13);
export const watchDay = (ms) => kst(ms).slice(0, 10);

/** 주소에서 번호·글자 덩어리(휴대폰·문의·회사 번호)를 :id로. 같은 주소의 요청이 한 줄로 모여요. */
export function routeOf(path) {
  const p = String(path ?? "").split("?")[0].slice(0, 200);
  return p.split("/").map((s) => (s.length >= 6 && /\d/.test(s) && /^[A-Za-z0-9_.~-]+$/.test(s)) || s.length > 24 || /^\d+$/.test(s) ? ":id" : s).join("/") || "/";
}

/** 걸린 시간이 들어갈 칸(0..7). */
export const bucketOf = (ms) => {
  const i = WATCH.edges.findIndex((e) => ms <= e);
  return i < 0 ? WATCH.edges.length : i;
};

/** 칸 수에서 [q](0.5, 0.95) 지점의 걸린 시간: 그 칸의 위쪽 끝(마지막 칸은 가장 길었던 것). */
export function watchQuantile(buckets, q, max = 0) {
  const total = buckets.reduce((a, b) => a + b, 0);
  if (!total) return null;
  let seen = 0;
  for (let i = 0; i < buckets.length; i++) {
    seen += buckets[i];
    if (seen >= q * total) return i < WATCH.edges.length ? WATCH.edges[i] : Math.max(max, WATCH.edges.at(-1));
  }
  return max || null;
}

// ---- 응답 속도: isolate 메모리에 모았다가 1분마다 ----

const buffers = new WeakMap();

function bufferOf(env) {
  const key = env?.DB;
  if (!key) return null;
  let b = buffers.get(key);
  if (!b) {
    b = { rows: new Map(), at: Date.now() };
    buffers.set(key, b);
  }
  return b;
}

/** 요청 하나를 셈해요(/api/만). 실패해도 요청에는 영향이 없어요. */
export function recordRequest(env, path, status, ms, now = Date.now()) {
  if (!String(path).startsWith("/api/")) return;
  const b = bufferOf(env);
  if (!b) return;
  let route = routeOf(path);
  const hour = watchHour(now);
  if (!b.rows.has(`${hour}|${route}`) && b.rows.size >= WATCH.routes) route = "/api/(기타)";
  const k = `${hour}|${route}`;
  const r = b.rows.get(k) ?? { hour, route, n: 0, err: 0, sum: 0, max: 0, b: new Array(WATCH.edges.length + 1).fill(0) };
  const t = Math.max(0, Math.round(Number(ms) || 0));
  r.n += 1;
  if (status >= 500) r.err += 1;
  r.sum += t;
  r.max = Math.max(r.max, t);
  r.b[bucketOf(t)] += 1;
  b.rows.set(k, r);
}

/** 모은 것을 더해요: 1분이 지났거나 [force](정기 작업). */
export async function flushWatch(env, now = Date.now(), force = false) {
  const b = bufferOf(env);
  if (!b || !b.rows.size || (!force && now - b.at < WATCH.flushMs)) return 0;
  const rows = [...b.rows.values()];
  b.rows = new Map();
  b.at = now;
  const sql = `INSERT INTO req_stats (hour, route, n, err, ms_sum, ms_max, b0, b1, b2, b3, b4, b5, b6, b7) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
    ON CONFLICT (hour, route) DO UPDATE SET n = n + ?3, err = err + ?4, ms_sum = ms_sum + ?5, ms_max = MAX(ms_max, ?6), b0 = b0 + ?7, b1 = b1 + ?8,
    b2 = b2 + ?9, b3 = b3 + ?10, b4 = b4 + ?11, b5 = b5 + ?12, b6 = b6 + ?13, b7 = b7 + ?14`;
  await env.DB.batch(rows.map((r) => env.DB.prepare(sql).bind(r.hour, r.route, r.n, r.err, r.sum, r.max, ...r.b)));
  return rows.length;
}

// ---- 오류 묶음 ----

/** 오류의 첫 줄에서 번호·따옴표 안 값을 지워 같은 오류가 한 묶음이 되게. */
export function errorLine(e) {
  const first = String(e?.message ?? e ?? "오류").split("\n")[0];
  return first.replace(/"[^"]{0,200}"|'[^']{0,200}'/g, "…").replace(/\b[0-9a-f]{8,}\b/gi, ":id").replace(/\d{3,}/g, "N").slice(0, WATCH.message);
}

const sigOf = (route, line) => {
  let h = 5381;
  for (const c of `${route}|${line}`) h = ((h * 33) ^ c.charCodeAt(0)) >>> 0;
  return h.toString(36);
};

/** 500 하나를 묶음에 더해요. [app]은 앱이 보낸 판(있으면). */
export async function recordError(env, path, e, now = Date.now(), app = "") {
  if (!env?.DB) return;
  const route = routeOf(path);
  const line = errorLine(e);
  const sig = sigOf(route, line);
  const stack = String(e?.stack ?? "").split("\n").slice(1, 6).map((s) => s.trim()).join("\n").replace(/https?:\/\/\S+/g, "").slice(0, WATCH.sample);
  await env.DB.batch([
    env.DB.prepare(`INSERT INTO error_groups (sig, route, message, sample, app, first_at, last_at, total, status) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6, 1, 'new')
      ON CONFLICT (sig) DO UPDATE SET last_at = ?6, total = total + 1, app = CASE WHEN ?5 <> '' THEN ?5 ELSE app END,
      status = CASE WHEN status = 'fixed' THEN 'again' ELSE status END`).bind(sig, route, line, stack, String(app ?? "").slice(0, 20), now),
    env.DB.prepare(`INSERT INTO error_days (day, sig, count) VALUES (?1, ?2, 1) ON CONFLICT (day, sig) DO UPDATE SET count = count + 1`).bind(watchDay(now), sig),
  ]);
}

// ---- 정기 작업 ----

/**
 * 정기 작업 단계 하나를 돌려요. 실패해도 던지지 않고(다음 단계가 돌게) 기록만 남겨요. 돌려준 값은 단계의 값, 실패면 undefined.
 */
export async function runJob(env, step, fn, now = Date.now()) {
  const t0 = Date.now();
  try {
    const v = await fn();
    const ms = Date.now() - t0;
    await run(env, `INSERT INTO job_runs (step, ok_at, ms, runs, last_at) VALUES (?1, ?2, ?3, 1, ?2)
      ON CONFLICT (step) DO UPDATE SET ok_at = ?2, ms = ?3, runs = runs + 1, last_at = ?2`, step, now, ms).catch(() => {});
    return v;
  } catch (e) {
    console.error(`job ${step}: ${e?.stack ?? e}`);
    await run(env, `INSERT INTO job_runs (step, fail_at, error, ms, runs, fails, last_at) VALUES (?1, ?2, ?3, ?4, 1, 1, ?2)
      ON CONFLICT (step) DO UPDATE SET fail_at = ?2, error = ?3, ms = ?4, runs = runs + 1, fails = fails + 1, last_at = ?2`,
    step, now, errorLine(e), Date.now() - t0).catch(() => {});
    return undefined;
  }
}

/** 정기 작업 단계들을 차례로, 단계마다 따로. */
export async function runJobs(env, steps, now = Date.now()) {
  const out = {};
  for (const [step, fn] of steps) out[step] = await runJob(env, step, fn, now);
  return out;
}

/** 지운 지 오래된 숫자: 응답 속도 14일, 오류 날짜 90일, 90일 동안 다시 안 난 오류 묶음. */
export async function pruneWatch(env, now = Date.now()) {
  await env.DB.batch([
    env.DB.prepare("DELETE FROM req_stats WHERE hour < ?").bind(watchHour(now - WATCH.keepStatsDays * DAY)),
    env.DB.prepare("DELETE FROM error_days WHERE day < ?").bind(watchDay(now - WATCH.keepErrorDays * DAY)),
    env.DB.prepare("DELETE FROM error_groups WHERE last_at < ?").bind(now - WATCH.keepErrorDays * DAY),
  ]);
}

// ---- 관리자 앱에 보이는 것 ----

/** 이름 있는 정기 작업 단계(매시 17분 작업의 순서대로). 화면은 이 이름으로 보여요. */
export const JOB_NAMES = {
  prune: "오래된 기록 지우기", trials: "동의 없는 체험 멈추기", lapsed: "끝난 구독 다시 확인", rest: "D1 한도 전 쉬기", share: "공유 서버 스위치 맞추기",
  flags: "기능 날짜·출시 알림", alerts: "주의 알림 보내기", digest: "아침·저녁 요약 알림", growth: "스튜디오 요구 정리", held: "보류 끝난 Claude 답 보내기",
  play: "Play 출시 트랙 읽기", ship: "자동 출시 안내 보내기", live: "업데이트 없이 고친 것 닿음 보기",
  watch: "응답 속도 모으기·정리", reviews: "스토어 리뷰 가져오기", backup: "하루 백업", incident: "사고 살피기", requests: "데이터 요청 기한 알림",
};

/** job_runs 한 줄을 화면 모양으로. */
export function jobRow(r, now = Date.now()) {
  const failing = r.fail_at && (!r.ok_at || r.fail_at > r.ok_at);
  const stale = !failing && r.ok_at && now - r.ok_at > WATCH.staleMs && r.step !== "backup" && r.step !== "digest";
  return { step: r.step, name: JOB_NAMES[r.step] ?? r.step, okAt: r.ok_at ?? null, failAt: r.fail_at ?? null, error: failing ? r.error : "",
    ms: r.ms ?? 0, runs: r.runs ?? 0, fails: r.fails ?? 0, state: failing ? "fail" : stale ? "stale" : r.ok_at ? "ok" : "never" };
}

export async function jobState(env, now = Date.now()) {
  return (await all(env, "SELECT * FROM job_runs ORDER BY step")).map((r) => jobRow(r, now));
}

/** 응답 속도: 지난 [hours]시간, 주소마다(느린 것 먼저)와 시간마다. */
export async function speedState(env, now = Date.now(), hours = 24) {
  const from = watchHour(now - hours * HOUR);
  const rows = await all(env, "SELECT * FROM req_stats WHERE hour >= ? ORDER BY hour", from);
  const bucket = (r) => [r.b0, r.b1, r.b2, r.b3, r.b4, r.b5, r.b6, r.b7];
  const byRoute = new Map();
  const byHour = new Map();
  for (const r of rows) {
    for (const [map, key] of [[byRoute, r.route], [byHour, r.hour]]) {
      const x = map.get(key) ?? { key, n: 0, err: 0, sum: 0, max: 0, b: new Array(8).fill(0) };
      x.n += r.n;
      x.err += r.err;
      x.sum += r.ms_sum;
      x.max = Math.max(x.max, r.ms_max);
      bucket(r).forEach((v, i) => { x.b[i] += v; });
      map.set(key, x);
    }
  }
  const shape = (x) => ({ key: x.key, n: x.n, err: x.err, avg: x.n ? Math.round(x.sum / x.n) : 0, p50: watchQuantile(x.b, 0.5, x.max), p95: watchQuantile(x.b, 0.95, x.max), max: x.max });
  const all24 = [...byHour.values()].reduce((a, x) => ({ n: a.n + x.n, err: a.err + x.err, sum: a.sum + x.sum, max: Math.max(a.max, x.max), b: a.b.map((v, i) => v + x.b[i]) }),
    { n: 0, err: 0, sum: 0, max: 0, b: new Array(8).fill(0) });
  return {
    total: shape({ key: "all", ...all24 }),
    routes: [...byRoute.values()].map(shape).sort((a, b) => (b.p95 ?? 0) - (a.p95 ?? 0) || b.n - a.n).slice(0, 25),
    hours: [...byHour.values()].map(shape),
  };
}

/** 오류 묶음: 새로 난 것·다시 난 것 먼저, 날마다 건수 14일. */
export async function errorState(env, now = Date.now(), { status = "open" } = {}) {
  const where = status === "all" ? "" : status === "fixed" ? "WHERE status = 'fixed'" : "WHERE status <> 'fixed'";
  const groups = await all(env, `SELECT * FROM error_groups ${where} ORDER BY CASE status WHEN 'again' THEN 0 WHEN 'new' THEN 1 ELSE 2 END, last_at DESC LIMIT 60`);
  const from = watchDay(now - 13 * DAY);
  const days = await all(env, "SELECT day, sig, count FROM error_days WHERE day >= ?", from);
  const list = Array.from({ length: 14 }, (_, i) => watchDay(now - (13 - i) * DAY));
  const per = new Map();
  for (const d of days) {
    if (!per.has(d.sig)) per.set(d.sig, new Map());
    per.get(d.sig).set(d.day, d.count);
  }
  const daily = list.map((day) => days.filter((d) => d.day === day).reduce((n, d) => n + d.count, 0));
  return {
    days: list, daily,
    groups: groups.map((g) => ({ sig: g.sig, route: g.route, message: g.message, sample: g.sample, app: g.app, firstAt: g.first_at, lastAt: g.last_at,
      total: g.total, status: g.status, note: g.note, fresh: now - g.first_at < DAY, trend: list.map((day) => per.get(g.sig)?.get(day) ?? 0) })),
    counts: Object.fromEntries((await all(env, "SELECT status, COUNT(*) AS n FROM error_groups GROUP BY status")).map((r) => [r.status, r.n])),
  };
}

/** 오류 묶음 하나를 '봤어요'·'고쳤어요'(다시 나면 '다시 남')·'새로'로. */
export async function setErrorState(env, sig, status, note, now = Date.now()) {
  if (!["new", "seen", "fixed"].includes(status)) return false;
  const r = await run(env, "UPDATE error_groups SET status = ?, note = ?, seen_at = ? WHERE sig = ?", status, String(note ?? "").slice(0, 300), now, sig);
  return Boolean(r?.meta?.changes);
}

/** D1이 알려 주는 데이터베이스 크기(바이트). 알 수 없으면 null. */
export async function dbSize(env) {
  try {
    const r = await env.DB.prepare("SELECT 1").run();
    const n = Number(r?.meta?.size_after);
    return Number.isFinite(n) && n > 0 ? n : null;
  } catch {
    return null;
  }
}

/**
 * [ADM3-04] 1인 관리자에게 꼭 필요한 설정. need: 'must'(없으면 놓치는 것이 생김) / 'nice'. 이름만 보고 값은 보이지 않아요.
 * 화면 이름: 관리 › 서버 상태 '꼭 필요'.
 */
export function requiredSetup(env) {
  const has = (k) => typeof env[k] === "string" ? env[k].trim().length > 0 : Boolean(env[k]);
  const items = [
    { key: "DB", need: "must", ok: Boolean(env.DB), what: "데이터베이스(D1) 연결", lose: "아무것도 저장되지 않아요" },
    { key: "ADMIN_PASSWORD", need: "must", ok: typeof env.ADMIN_PASSWORD === "string" && env.ADMIN_PASSWORD.length >= 10, what: "최고 관리자 비밀번호(10자 이상)", lose: "관리자 앱에 들어올 수 없어요" },
    { key: "RATE_SALT", need: "must", ok: has("RATE_SALT"), what: "요청 한도용 소금 값", lose: "네트워크 한도가 약해져요" },
    { key: "VAPID_PUBLIC_KEY·VAPID_PRIVATE_KEY", need: "must", ok: has("VAPID_PUBLIC_KEY") && has("VAPID_PRIVATE_KEY"), what: "관리자 휴대폰 알림",
      lose: "새 문의·주의·사고·아침 요약 알림이 오지 않아 앱을 열어야만 알아요" },
    { key: "PLAY_SERVICE_ACCOUNT", need: "must", ok: has("PLAY_SERVICE_ACCOUNT"), what: "Play 서비스 계정(결제 확인·비정상 종료·ANR·스토어 리뷰)",
      lose: "결제를 Play와 맞춰 보지 못하고, 판 건강·스토어 리뷰가 비어요" },
    { key: "FCM_SERVICE_ACCOUNT", need: "nice", ok: has("FCM_SERVICE_ACCOUNT"), what: "앱 사용자에게 보내는 푸시(문의 답 알림)", lose: "문의 답이 앱을 열 때만 보여요" },
    { key: "BACKUP(R2)", need: "nice", ok: Boolean(env.BACKUP && typeof env.BACKUP.put === "function"), what: "하루 백업을 둘 R2 저장소", lose: "D1 되돌리기(Time Travel)만 쓸 수 있어요" },
    { key: "MAIL_API_KEY·MAIL_FROM", need: "nice", ok: has("MAIL_API_KEY") && has("MAIL_FROM"), what: "초대·확인 메일", lose: "초대 링크를 직접 전해야 해요" },
    { key: "SHARE_BASE_URL·SHARE_ADMIN_TOKEN", need: "nice", ok: has("SHARE_BASE_URL") && has("SHARE_ADMIN_TOKEN"), what: "공유 서버 신고·상태 보기", lose: "공유 링크 신고와 공유 서버 상태를 못 봐요" },
  ];
  return { items, missing: items.filter((i) => i.need === "must" && !i.ok).map((i) => i.key) };
}

/** 공유 서버가 살아 있는지(있을 때만): 걸린 시간과 답. */
export async function sharePing(env) {
  const base = typeof env.SHARE_BASE_URL === "string" ? env.SHARE_BASE_URL.trim().replace(/\/+$/, "") : "";
  if (!/^https?:\/\//.test(base)) return null;
  const t0 = Date.now();
  try {
    const res = await fetch(`${base}/api/health`, { signal: AbortSignal.timeout(4000) });
    // 1.39 [ADM3-41] A share server before 1.39 has no /api/health (404) though it is up: its robots.txt, which every
    // version answers, says it is alive. Anything else than 404 (5xx, a timeout) is still '답 없음'.
    if (res.status === 404) {
      const old = await fetch(`${base}/robots.txt`, { signal: AbortSignal.timeout(4000) });
      return { ok: old.ok, status: old.status, ms: Date.now() - t0, ...(old.ok ? { old: true } : {}) };
    }
    return { ok: res.ok, status: res.status, ms: Date.now() - t0 };
  } catch {
    return { ok: false, status: 0, ms: Date.now() - t0 };
  }
}

/** 관리 › 서버 상태가 한 번에 읽는 것. */
export async function healthBoard(env, now = Date.now()) {
  await flushWatch(env, now, true).catch(() => {});
  const [jobs, speed, errors, size, share] = await Promise.all([jobState(env, now), speedState(env, now), errorState(env, now), dbSize(env), sharePing(env)]);
  return { at: now, jobs, speed, errors: { counts: errors.counts, daily: errors.daily, days: errors.days, top: errors.groups.slice(0, 5) }, dbBytes: size,
    dbLimit: 10 * 1024 ** 3, share, setup: requiredSetup(env) };
}
