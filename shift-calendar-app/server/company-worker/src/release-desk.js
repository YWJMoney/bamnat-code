import { all, audit, batch, json, one, readJson, refuse, run, stmt } from "./index.js";
import { shippedVersion, shipWaiting } from "./desk.js";
import { compareVersions, version } from "./inbox.js";
import { kstDay, noticeRows } from "./usage.js";
import { DAY, HOUR } from "./units.js";

// ---- 1.38 [ADM3-14] 출시 날 점검표, [ADM3-15] 판 건강 비교, [ADM3-33] 바뀐 일 표시 ----
//
// 보고서 3-1항 W5, 3-2항 O4·O7. 출시 날 할 일이 흐름·기능 관리·장터·공지에 흩어져 있던 것을 판마다 한 장으로 모으고, 저절로 알 수 있는
// 단계(나간 판 저장, 묶인 문의 답, 재료·장터 공개 대기)는 서버가 스스로 체크해요. 단계 이름은 1.38 Play 출시 사용자 할 일 문서 9장과 같아요.

export const RELEASE_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS release_runs (version TEXT PRIMARY KEY, started_at INTEGER NOT NULL, steps TEXT NOT NULL DEFAULT '{}',
    done_at INTEGER, by TEXT NOT NULL DEFAULT '')`,
];

/** 점검표 단계: key, 이름, 할 일, 저절로 알 수 있으면 auto, 열 곳. 순서가 곧 할 순서예요. */
export const RELEASE_STEPS = [
  { key: "play", name: "Play 올림", what: "Play Console › 프로덕션에 새 판을 올리고 출시를 시작해요(단계적 출시면 비율도 적어 두기).", to: null },
  // 1.39 [ADM3-38] 나간 판 저장·묶인 문의 답의 [열기]는 저장 단추가 있는 오늘 › 흐름으로(장부에는 release 보기가 없어요).
  { key: "ship", name: "나간 판 저장", what: "Play에 나간 판을 저장해요. 그래야 앱에 '반영'과 '업데이트하세요' 답이 맞게 나가요.", to: "#/flow", auto: true },
  { key: "flags", name: "기능 일정 확인", what: "출시 › 기능 관리 › 일정에서 이 판과 함께 켜질 기능과 날짜(처리방침 시행일)를 확인해요.", to: "#/flags?view=timeline" },
  { key: "materials", name: "스튜디오 재료 잠금", what: "출시 › 스튜디오 재료에서 묶음마다 공개·무료·광고를 정해요(아이콘 모두 무료, 배경 3분의 1 무료).", to: "#/materials", auto: true },
  { key: "market", name: "장터 작품 공개", what: "사람과 글 › 마을 장터 › 출시 대기에서 공개를 기다리는 작품을 지금 공개하거나 예약해요.", to: "#/recipes?tab=waiting", auto: true },
  { key: "notice", name: "공지", what: "출시 › 앱 공지에 '업데이트 소식'과 처리방침 개정 알림을 올려요.", to: "#/notices", auto: true },
  { key: "answers", name: "묶인 문의 답 보내기", what: "고친 판이 나가기를 기다리던 문의에 답이 다 갔는지 봐요(나간 판 저장 때 함께 보내요).", to: "#/flow", auto: true },
  { key: "health", name: "24시간 뒤 판 건강", what: "하루 뒤 숫자 › 판 건강에서 문의·문제 신고·서버 오류·비정상 종료가 이전 판보다 나빠지지 않았는지 봐요.", to: "#/health", auto: true },
];

const VER = /^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$/;

async function readRun(env, v) {
  const r = await one(env, "SELECT * FROM release_runs WHERE version = ?", v);
  if (!r) return null;
  let steps = {};
  try {
    steps = JSON.parse(r.steps ?? "{}") ?? {};
  } catch {
    steps = {};
  }
  return { version: r.version, startedAt: r.started_at, doneAt: r.done_at, by: r.by, steps };
}

/** 저절로 알 수 있는 단계의 지금 모습: done(끝), hint(보여 줄 숫자·말). */
async function autoState(env, v, run_, now) {
  const shipped = await shippedVersion(env);
  const out = {};
  const shippedHere = Boolean(shipped && version(shipped.v) && compareVersions(version(shipped.v), version(v)) >= 0);
  out.ship = { done: shippedHere, hint: shipped ? `지금 저장된 판 ${shipped.v}` : "아직 저장한 판이 없어요" };
  try {
    const waiting = shippedHere ? await shipWaiting(env, shipped.v) : [];
    out.answers = { done: shippedHere && waiting.length === 0, hint: shippedHere ? (waiting.length ? `답을 기다리는 문의 ${waiting.length}건` : "기다리는 문의 없음") : "나간 판을 저장하면 셀 수 있어요" };
  } catch {
    out.answers = { done: false, hint: "셀 수 없어요" };
  }
  const [mats, market] = (await batch(env, [
    stmt(env, "SELECT state, COUNT(*) AS n FROM studio_materials GROUP BY state"),
    stmt(env, "SELECT COUNT(*) AS n, MIN(release_at) AS at FROM recipes WHERE state = 'waiting'"),
  ]).catch(() => [{ results: [] }, { results: [] }])).map((r) => r.results ?? []);
  const m = Object.fromEntries(mats.map((x) => [x.state, x.n]));
  out.materials = { done: (m.waiting ?? 0) === 0 && ((m.ok ?? 0) + (m.hidden ?? 0)) > 0, hint: `공개 ${m.ok ?? 0} · 대기 ${m.waiting ?? 0} · 숨김 ${m.hidden ?? 0}` };
  const w = market[0]?.n ?? 0;
  out.market = { done: w === 0 || Boolean(market[0]?.at), hint: w ? `공개 대기 ${w}개${market[0]?.at ? " · 예약됨" : ""}` : "공개 대기 없음" };
  const notices = await noticeRows(env).catch(() => []);
  const since = run_?.startedAt ?? now - 7 * DAY;
  const fresh = notices.filter((x) => x.from >= since - DAY && x.until > now);
  out.notice = { done: fresh.length > 0, hint: fresh.length ? `점검표를 연 뒤 올린 공지 ${fresh.length}개` : "아직 새 공지가 없어요" };
  const shipAt = shipped && shippedHere ? shipped.at : null;
  out.health = { done: Boolean(shipAt && now - shipAt >= DAY && run_?.steps?.health?.doneAt), hint: shipAt ? (now - shipAt >= DAY ? "하루가 지났어요. 판 건강을 봐 주세요" : `나간 지 ${Math.floor((now - shipAt) / HOUR)}시간`) : "나간 판 저장 뒤 하루" };
  return out;
}

/** 한 판의 점검표(없으면 null). */
export async function releaseView(env, v, now = Date.now()) {
  const r = await readRun(env, v);
  if (!r) return null;
  const auto = await autoState(env, v, r, now);
  const steps = RELEASE_STEPS.map((s) => {
    const mine = r.steps[s.key] ?? {};
    const a = auto[s.key];
    const done = Boolean(mine.doneAt) || (s.key !== "health" && Boolean(a?.done));
    return { key: s.key, name: s.name, what: s.what, to: s.to, auto: Boolean(s.auto), hint: a?.hint ?? "", done, skipped: Boolean(mine.skipped),
      doneAt: mine.doneAt ?? null, by: mine.by ?? "", note: mine.note ?? "", autoDone: Boolean(a?.done) && !mine.doneAt };
  });
  const left = steps.filter((s) => !s.done && !s.skipped).length;
  return { ...r, steps, left, next: steps.find((s) => !s.done && !s.skipped)?.key ?? null };
}

/** 점검표 목록: 판이 높은 것 먼저. */
export async function releaseList(env, now = Date.now()) {
  const rows = await all(env, "SELECT version FROM release_runs ORDER BY started_at DESC LIMIT 12");
  const list = [];
  for (const r of rows) list.push(await releaseView(env, r.version, now));
  return { items: list.filter(Boolean), steps: RELEASE_STEPS.map((s) => ({ key: s.key, name: s.name })) };
}

/** POST /release {version}: 새 점검표. POST /release/<v> {step, done|skip|note}: 단계 하나. */
export async function releaseRoute(request, env, path, method, now) {
  if (path === "/release" && method === "GET") return json(await releaseList(env, now));
  if (path === "/release" && method === "POST") {
    const body = await readJson(request, 1024);
    const v = String(body?.version ?? "").trim();
    if (!VER.test(v)) refuse("version", "version");
    const actor = env.actor?.name ?? "관리자";
    await run(env, "INSERT INTO release_runs (version, started_at, steps, by) VALUES (?, ?, '{}', ?) ON CONFLICT (version) DO NOTHING", v, now, actor);
    await audit(env, now, "release_start", v, "출시 날 점검표");
    return json(await releaseView(env, v, now));
  }
  const m = path.match(/^\/release\/([0-9.]{5,11})$/);
  if (m && method === "GET") {
    const v = await releaseView(env, m[1], now);
    return v ? json(v) : json({ error: "not_found" }, 404);
  }
  if (m && method === "POST") {
    const r = await readRun(env, m[1]);
    if (!r) return json({ error: "not_found" }, 404);
    const body = await readJson(request, 2048);
    const step = RELEASE_STEPS.find((s) => s.key === body?.step);
    if (!step) refuse("step", "step");
    const actor = env.actor?.name ?? "관리자";
    const cur = r.steps[step.key] ?? {};
    const next = { ...cur };
    if (body.done === true) Object.assign(next, { doneAt: now, by: actor, skipped: false });
    if (body.done === false) Object.assign(next, { doneAt: null, skipped: false });
    if (body.skip === true) Object.assign(next, { skipped: true, doneAt: null, by: actor });
    if (typeof body.note === "string") next.note = body.note.replace(/[\u0000-\u001f]/g, " ").slice(0, 300);
    r.steps[step.key] = next;
    const view = { ...r };
    await run(env, "UPDATE release_runs SET steps = ? WHERE version = ?", JSON.stringify(r.steps), m[1]);
    const after = await releaseView(env, m[1], now);
    if (after.left === 0 && !view.doneAt) await run(env, "UPDATE release_runs SET done_at = ? WHERE version = ?", now, m[1]);
    await audit(env, now, "release_step", m[1], `${step.name} ${body.done === true ? "끝" : body.skip === true ? "건너뜀" : body.done === false ? "되돌림" : "메모"}`);
    return json(after.left === 0 ? await releaseView(env, m[1], now) : after);
  }
  return null;
}

// ---- 판 건강 비교 [ADM3-15] ----


/**
 * 판마다 지난 [days]일: 사용 통계를 보낸 휴대폰-날(활성), 문의·문제 신고(판별), 그 비율. 서버 오류는 판을 모르니 날짜로만, Play 비정상 종료·ANR은
 * 서버에 서비스 계정이 있으면 전체 값으로. 가장 새 판을 그 앞 판과 견줘 나빠지면 worse.
 */
export async function versionHealth(env, now = Date.now(), days = 14, vitals = null) {
  const from = now - days * DAY;
  const [usage, inq, errors, grouped] = (await batch(env, [
    stmt(env, "SELECT app, SUM(phones) AS phones, COUNT(DISTINCT day) AS days FROM usage_days WHERE day >= ? GROUP BY app", kstDay(from)),
    stmt(env, `SELECT json_extract(info, '$.app') AS app, kind, COUNT(*) AS n FROM inquiries WHERE created_at >= ? GROUP BY app, kind`, from),
    stmt(env, "SELECT key, count FROM counters WHERE key LIKE 'errors|%' AND key >= ?", `errors|${kstDay(from)}`),
    // 1.39 [ADM3-43] 같은 날들 가운데 오류 묶음에 담긴 수(1.38부터). 다르면 화면이 '묶음에 N'으로 함께 보여요.
    stmt(env, "SELECT COALESCE(SUM(count), 0) AS n FROM error_days WHERE day >= ?", kstDay(from)),
  ])).map((r) => r.results ?? []);
  const map = new Map();
  const row = (app) => {
    const k = String(app ?? "모름").split(" ")[0] || "모름";
    if (!map.has(k)) map.set(k, { app: k, phoneDays: 0, days: 0, inquiries: 0, bugs: 0 });
    return map.get(k);
  };
  for (const u of usage) {
    const r = row(u.app);
    r.phoneDays += u.phones ?? 0;
    r.days = Math.max(r.days, u.days ?? 0);
  }
  for (const q of inq) {
    const r = row(q.app);
    r.inquiries += q.n ?? 0;
    if (q.kind === "bug" || q.kind === "problem") r.bugs += q.n ?? 0;
  }
  const list = [...map.values()].map((r) => ({ ...r, bugRate: r.phoneDays ? Math.round((r.bugs / r.phoneDays) * 100000) / 100 : null,
    inquiryRate: r.phoneDays ? Math.round((r.inquiries / r.phoneDays) * 100000) / 100 : null }));
  const known = list.filter((r) => version(r.app)).sort((a, b) => compareVersions(version(b.app), version(a.app)));
  const [latest, prev] = known;
  const worse = [];
  if (latest && prev && latest.phoneDays >= 30 && prev.phoneDays >= 30) {
    if ((latest.bugRate ?? 0) > (prev.bugRate ?? 0) * 1.5 && latest.bugs >= 3) worse.push(`문제 신고가 ${prev.app}보다 많아요(휴대폰-날 1000당 ${latest.bugRate} 대 ${prev.bugRate})`);
    if ((latest.inquiryRate ?? 0) > (prev.inquiryRate ?? 0) * 1.5 && latest.inquiries >= 5) worse.push(`문의가 ${prev.app}보다 많아요`);
  }
  const serverErrors = errors.reduce((n, e) => n + (e.count ?? 0), 0);
  return { days, versions: [...known, ...list.filter((r) => !version(r.app))], latest: latest?.app ?? null, prev: prev?.app ?? null, worse, serverErrors,
    groupedErrors: grouped[0]?.n ?? 0, vitals };
}

// ---- 바뀐 일 [ADM3-33] ----

/** 그래프에 찍을 '이날 바꾼 것': 나간 판, 기능 켬·끔, 공지, 가격표, 처리방침, 쉬기. [from]~[to] ms. */
export async function changeMarks(env, from, to) {
  const [auditRows, flagRows] = (await batch(env, [
    stmt(env, `SELECT at, action, target, detail FROM audit WHERE at >= ? AND at < ? AND action IN ('shipped_version', 'price_table', 'launch_recorded', 'auto_rest',
      'auto_resume', 'release_start', 'notice_remove', 'flag_force', 'flag_policy', 'rule_promote', 'incident_open', 'incident_close', 'server_deployed') ORDER BY at`, from, to),
    stmt(env, "SELECT at, key, action FROM flag_history WHERE at >= ? AND at < ? ORDER BY at LIMIT 200", from, to),
  ]).catch(() => [{ results: [] }, { results: [] }])).map((r) => r.results ?? []);
  const words = { shipped_version: "판 나감", price_table: "가격표", launch_recorded: "정식 출시", auto_rest: "D1 쉬기", auto_resume: "쉬기 끝", release_start: "출시 점검표",
    notice_remove: "공지 내림", flag_force: "기능 강제", flag_policy: "처리방침 날짜", rule_promote: "규칙 올림", incident_open: "사고 시작", incident_close: "사고 끝",
    // 1.39 [STAT2-05] 서버가 새 판으로 처음 뜬 때(사고 때 가장 먼저 의심할 일).
    server_deployed: "서버 배포" };
  const marks = auditRows.map((r) => ({ at: r.at, day: kstDay(r.at), kind: r.action, text: `${words[r.action] ?? r.action} ${r.target ?? ""}`.trim() }));
  for (const f of flagRows) marks.push({ at: f.at, day: kstDay(f.at), kind: "flag", text: `기능 ${f.key} ${f.action}` });
  const notices = await noticeRows(env).catch(() => []);
  for (const n of notices) if (n.from >= from && n.from < to) marks.push({ at: n.from, day: kstDay(n.from), kind: "notice", text: `공지 ${String(n.title ?? "").slice(0, 20)}` });
  return marks.sort((a, b) => a.at - b.at).slice(-200);
}
