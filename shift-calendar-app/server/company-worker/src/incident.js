import { all, audit, json, notifyAdmins, one, randomId, readJson, run } from "./index.js";
import { changeMarks } from "./release-desk.js";
import { JOB_NAMES, watchHour } from "./ops-watch.js";
import { DAY, HOUR } from "./units.js";

// ---- 1.38 [ADM3-32] 사고 모드 ----
//
// 보고서 4-3항. 서버 오류·문제 신고가 갑자기 늘거나 정기 작업이 계속 실패하거나 Play 비정상 종료·ANR이 기준을 넘으면 '사고'를 열어요.
// 관리자 앱 맨 위에 빨간 띠 하나, 누르면 한 화면에: 무엇이 늘었는지, 그 전 48시간에 바꾼 것(판·기능·공지), 끌 수 있는 스위치, 공지 초안,
// 그동안 들어온 문제 신고(개발사항 하나로 묶기). 닫기는 사람이 해요(무엇을 했는지 한 줄과 함께). docs/incident-response.md를 단추로 옮긴 것.

export const INCIDENT = {
  /** 지난 한 시간(끝난 시각 칸 하나) 서버 오류가 이 수 이상이고 7일 시간 평균의 [errX]배 이상. */
  errMin: 10, errX: 5,
  /** 지난 한 시간 500 비율(요청 [rateN] 이상일 때). */
  rate: 0.05, rateN: 50,
  /** 지난 6시간 문제 신고가 이 수 이상이고 지난 7일 6시간 평균의 [bugX]배 이상. 2.0.0 [RVN-01] 건수와 함께 서로 다른 휴대폰 [bugDevices]대 이상(SVC-82). */
  bugMin: 5, bugX: 3, bugDevices: 3,
  /** 같은 정기 작업 단계가 이만큼 연달아 실패. */
  jobFails: 3,
  /** Play 기준(나쁜 행동 기준). 2.0.0 [RVN-01] 하루 사용자가 [vitalsUsers] 아래면 한두 명으로도 넘으니 신호로 보지 않아요(SVC-81). */
  crash: 0.0109, anr: 0.0047, vitalsUsers: 100,
  /** 2.0.0 [RVN-01] 닫을 때 있던 신호가 이만큼 더 나빠지면 다시 열어요(SVC-81). */
  worse: 1.3,
  /** 2.0.0 [RVN-02] 하루 넘게 열린 사고는 하루 한 번 '닫아 주세요'(SVC-83). */
  remindMs: DAY,
  /** 2.0.0 [RVN-02] 매시 작업이 이만큼 안 돌았으면 5분 작업이 알려요, 5분 작업이 이만큼 안 돌았으면 매시 작업이 알려요(서로 지켜보기). */
  hourlyStaleMs: 90 * 60 * 1000, fiveStaleMs: 30 * 60 * 1000,
};

/**
 * 2.0.0 [RVN-01] 밤에도 울리는 신호: 지금 서버가 망가지고 있다는 뜻이라 기다리면 안 되는 것. 28일 Play 지표·문제 신고는 아침에 봐도 돼요
 * (조용한 시간을 지켜요, SVC-81). 화면의 '확실'·'의심'도 이것으로 나눠요(I22ca-01).
 */
export const INCIDENT_URGENT = new Set(["errors", "rate", "jobs", "stale"]);

export const INCIDENT_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS incidents (id TEXT PRIMARY KEY, opened_at INTEGER NOT NULL, closed_at INTEGER, signals TEXT NOT NULL DEFAULT '[]',
    note TEXT NOT NULL DEFAULT '', by TEXT NOT NULL DEFAULT '', auto INTEGER NOT NULL DEFAULT 1)`,
  // 2.0.0 [RVN-02] 열린 채 하루가 지나 다시 알린 때(SVC-83).
  "ALTER TABLE incidents ADD COLUMN reminded_at INTEGER",
];

/**
 * 지금 보이는 사고 신호들. [vitals]는 Play 값(있으면).
 * 2.0.0 [RVN-01] 신호마다 [v](견주는 값), [n](표본), [was](평소·어제 값 한 줄), [sure](밤에도 울릴 만큼 확실한지)를 같이 줘요.
 */
export async function incidentSignals(env, now = Date.now(), vitals = null) {
  const out = [];
  // SVC-84: '지난 한 시간'은 끝난 시각 칸 하나예요(지금 시각 17분까지 더하면 평균보다 늘 크게 나와요).
  const hour = watchHour(now - HOUR);
  const [last, base] = await Promise.all([
    one(env, "SELECT SUM(n) AS n, SUM(err) AS err FROM req_stats WHERE hour = ?", hour).catch(() => null),
    one(env, "SELECT SUM(err) AS err, COUNT(DISTINCT hour) AS hours FROM req_stats WHERE hour >= ? AND hour < ?", watchHour(now - 7 * DAY), hour).catch(() => null),
  ]);
  const avg = base?.hours ? (base.err ?? 0) / base.hours : 0;
  if ((last?.err ?? 0) >= INCIDENT.errMin && (last.err >= avg * INCIDENT.errX)) {
    out.push({ key: "errors", text: `서버 오류 한 시간 ${last.err}번(평소 ${avg.toFixed(1)}번)`, v: last.err, n: last.n ?? 0, was: `평소 ${avg.toFixed(1)}번` });
  } else if ((last?.n ?? 0) >= INCIDENT.rateN && last.err / last.n >= INCIDENT.rate) {
    out.push({ key: "rate", text: `요청의 ${Math.round((last.err / last.n) * 100)}%가 서버 오류`, v: last.err / last.n, n: last.n, was: `평소 한 시간 ${avg.toFixed(1)}번` });
  }
  // SVC-82: 건수만 세면 한 사람이 5건으로 사고를 열 수 있어요. 서로 다른 휴대폰 수도 같이 봐요.
  const [bugs, bugBase] = await Promise.all([
    one(env, "SELECT COUNT(*) AS n, COUNT(DISTINCT device) AS phones FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ?", now - 6 * HOUR),
    one(env, "SELECT COUNT(*) AS n FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ? AND created_at < ?", now - 7 * DAY, now - 6 * HOUR),
  ]);
  const bugAvg = (bugBase?.n ?? 0) / ((7 * 24 - 6) / 6);
  if ((bugs?.n ?? 0) >= INCIDENT.bugMin && (bugs.phones ?? 0) >= INCIDENT.bugDevices && bugs.n >= bugAvg * INCIDENT.bugX) {
    out.push({ key: "bugs", text: `6시간 문제 신고 ${bugs.n}건, 휴대폰 ${bugs.phones}대(평소 ${bugAvg.toFixed(1)}건)`, v: bugs.n, n: bugs.phones, was: `평소 ${bugAvg.toFixed(1)}건` });
  }
  const jobs = await all(env, "SELECT step, fails, ok_at, fail_at FROM job_runs WHERE fail_at IS NOT NULL AND (ok_at IS NULL OR fail_at > ok_at)").catch(() => []);
  // 매시 작업이라 마지막 성공 뒤 (jobFails - 1)시간 넘게 실패만 했으면 연달아 jobFails번이에요.
  const stuck = jobs.filter((j) => (j.ok_at ? j.fail_at - j.ok_at >= (INCIDENT.jobFails - 1) * HOUR : j.fails >= INCIDENT.jobFails));
  if (stuck.length) out.push({ key: "jobs", text: `정기 작업이 계속 실패: ${stuck.map((j) => JOB_NAMES[j.step] ?? j.step).join(", ")}`, v: stuck.length, n: stuck.length, was: "" });
  // SVC-83: 5분 작업(보류 끝난 답·자동 출시 안내)이 멈추면 매시 작업이 알려요.
  const five = await one(env, "SELECT last_at FROM job_runs WHERE step = ?", FIVE_STEP).catch(() => null);
  if (five?.last_at && now - five.last_at > INCIDENT.fiveStaleMs) {
    out.push({ key: "stale", text: `5분 작업이 ${Math.round((now - five.last_at) / 60000)}분째 안 돌아요(보류 끝난 답·자동 출시 안내)`, v: now - five.last_at, n: 1, was: "" });
  }
  if (vitals && !vitals.error) {
    // SVC-81: 하루 사용자가 적으면 한두 명의 비정상 종료로도 기준을 넘어요. 그때는 신호로 보지 않아요.
    const enough = vitals.users == null || vitals.users >= INCIDENT.vitalsUsers;
    const users = vitals.users != null ? `하루 사용자 ${vitals.users}명` : "";
    if (enough && vitals.crash != null && vitals.crash >= INCIDENT.crash) {
      out.push({ key: "crash", text: `비정상 종료 ${(vitals.crash * 100).toFixed(2)}%(기준 1.09%)`, v: vitals.crash, n: vitals.users ?? 0, was: users });
    }
    if (enough && vitals.anr != null && vitals.anr >= INCIDENT.anr) {
      out.push({ key: "anr", text: `ANR ${(vitals.anr * 100).toFixed(2)}%(기준 0.47%)`, v: vitals.anr, n: vitals.users ?? 0, was: users });
    }
  }
  return out.map((s) => ({ ...s, sure: INCIDENT_URGENT.has(s.key) }));
}

/** 2.0.0 [RVN-02] 5분 작업의 정기 작업 이름(job_runs). */
export const FIVE_STEP = "five";

const openOne = (env) => one(env, "SELECT * FROM incidents WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1").catch(() => null);

const parseJson = (text, fallback) => {
  try {
    return JSON.parse(text ?? "") ?? fallback;
  } catch {
    return fallback;
  }
};

/**
 * 2.0.0 [RVN-01] 닫을 때 있던 신호는 '다시 묻지 않기'에 들어가요(SVC-81): 그 신호가 한 번 꺼졌다 다시 켜지거나, 닫을 때보다 [INCIDENT.worse]배
 * 나빠지거나, 고른 날 수가 지나야 다시 사고가 돼요. settings 'incident_mutes' {key: {v, until, at}}.
 */
async function mutesOf(env) {
  const row = await one(env, "SELECT value FROM settings WHERE key = 'incident_mutes'").catch(() => null);
  return parseJson(row?.value, {}) ?? {};
}

const saveMutes = (env, mutes) => run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('incident_mutes', ?)", JSON.stringify(mutes));

/** [signals] 가운데 다시 묻지 않기로 한 것을 빼고, 꺼진 신호의 '다시 묻지 않기'는 지워요(until이 없을 때). */
export function unmuted(signals, mutes, now) {
  const keys = new Set(signals.map((s) => s.key));
  const next = {};
  for (const [k, m] of Object.entries(mutes ?? {})) {
    if (m.until && m.until <= now) continue; // 고른 날 수가 지났어요.
    if (!m.until && !keys.has(k)) continue; // 한 번 꺼졌으니 다음에 켜지면 새 일이에요.
    next[k] = m;
  }
  const live = signals.filter((s) => !next[s.key] || (Number.isFinite(s.v) && Number.isFinite(next[s.key].v) && s.v >= next[s.key].v * INCIDENT.worse));
  return { live, mutes: next };
}

/**
 * 매시 작업: 신호가 있고 열린 사고가 없으면 열고 바로 알려요. 신호가 사라져도 닫지는 않아요(사람이 닫아요).
 * 2.0.0 [RVN-01][RVN-02] 닫은 뒤 같은 신호로 다시 열지 않고(SVC-81), 밤에는 급한 신호만 울리고, 열린 사고에 새 종류의 신호가 붙으면 한 번 더,
 * 하루 넘게 열려 있으면 하루 한 번 알려요(SVC-83). 열린 사고의 신호 글은 지금 값으로, 꺼진 것은 '지금은 잠잠해요'로 남겨요.
 */
export async function incidentTick(env, now = Date.now(), vitals = null) {
  const seen = await incidentSignals(env, now, vitals);
  const before = await mutesOf(env);
  const { live: signals, mutes } = unmuted(seen, before, now);
  if (JSON.stringify(mutes) !== JSON.stringify(before)) await saveMutes(env, mutes).catch(() => {});
  const open = await openOne(env);
  if (open) {
    const had = parseJson(open.signals, []);
    const keys = new Set(seen.map((s) => s.key));
    const kept = had.filter((s) => !keys.has(s.key)).map((s) => ({ ...s, off: true }));
    const merged = [...seen.map((s) => ({ ...s, off: false })), ...kept];
    if (JSON.stringify(merged) !== JSON.stringify(had)) await run(env, "UPDATE incidents SET signals = ? WHERE id = ?", JSON.stringify(merged), open.id);
    const fresh = seen.filter((s) => !had.some((h) => h.key === s.key));
    if (fresh.length) {
      await notifyAdmins(env, "incident", { title: "열린 사고에 새 신호가 붙었어요", body: fresh.map((s) => s.text).join(" · "), url: "/admin/#/incident", urgent: true },
        now, { ignoreQuiet: fresh.some((s) => s.sure) });
    } else if (now - Math.max(open.opened_at, open.reminded_at ?? 0) >= INCIDENT.remindMs) {
      await run(env, "UPDATE incidents SET reminded_at = ? WHERE id = ?", now, open.id);
      await notifyAdmins(env, "incident", { title: "사고가 하루 넘게 열려 있어요", body: "고쳤으면 무엇을 했는지 적고 닫아 주세요. 열린 동안에는 같은 종류의 새 신호를 따로 알리지 않아요.",
        url: "/admin/#/incident" }, now);
    }
    return { open: open.id, signals: seen.length };
  }
  if (!signals.length) return { open: null, signals: 0 };
  const id = `x${randomId(15)}`;
  await run(env, "INSERT INTO incidents (id, opened_at, signals) VALUES (?, ?, ?)", id, now, JSON.stringify(signals.map((s) => ({ ...s, off: false }))));
  await audit(env, now, "incident_open", id, signals.map((s) => s.text).join(" · ").slice(0, 280));
  await notifyAdmins(env, "incident", { title: signals.some((s) => s.sure) ? "사고 같아요" : "사고일 수 있어요", body: signals.map((s) => s.text).join(" · "),
    url: "/admin/#/incident", urgent: true }, now, { ignoreQuiet: signals.some((s) => s.sure) });
  return { open: id, signals: signals.length };
}

/**
 * 2.0.0 [RVN-02] 5분 작업이 한 번 돌 때: 매시 작업이 [INCIDENT.hourlyStaleMs](90분) 넘게 안 돌았으면 급한 알림 한 번. 같은 멈춤에는
 * 다시 알리지 않고(마지막 매시 작업 시각을 적어 둠), 매시 작업이 다시 돌면 풀려 다음 멈춤에 또 알려요. 매시 작업 안의 '사고 살피기'는
 * 매시 작업이 죽으면 스스로를 알릴 수 없어서 서로 지켜봐요(SVC-83, 총괄 11-12).
 */
export async function hourlyWatch(env, now = Date.now()) {
  const last = await one(env, "SELECT MAX(last_at) AS at FROM job_runs WHERE step <> ?", FIVE_STEP).catch(() => null);
  if (!last?.at || now - last.at <= INCIDENT.hourlyStaleMs) return { stale: false };
  const told = await one(env, "SELECT value FROM settings WHERE key = 'hourly_stale_told'").catch(() => null);
  if (told?.value === String(last.at)) return { stale: true, told: false };
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('hourly_stale_told', ?)", String(last.at));
  await notifyAdmins(env, "incident", { title: "매시 작업이 멈췄어요", body: `${Math.round((now - last.at) / 60000)}분째 안 돌았어요. 출시 안내·기능 날짜·백업·사고 살피기가 함께 멈춰요. 관리 › 서버 상태를 봐 주세요`,
    url: "/admin/#/server", urgent: true }, now, { ignoreQuiet: true });
  return { stale: true, told: true };
}

/** 2.0.0 [RVN-02] /api/admin/health?jobs=1이 내는 숫자: 마지막 매시 작업이 몇 분 전인지(운영 장부 자동 처리가 하루 두 번 봐요, I22cb-03). */
export async function hourlyAgo(env, now = Date.now()) {
  const last = await one(env, "SELECT MAX(last_at) AS at FROM job_runs WHERE step <> ?", FIVE_STEP).catch(() => null);
  return last?.at ? Math.max(0, Math.round((now - last.at) / 60000)) : null;
}

/** 사고 화면: 열린 사고(있으면), 지금 신호, 지난 48시간 바꾼 것, 그동안 문제 신고, 공지 초안, 최근 닫은 사고. */
export async function incidentView(env, now = Date.now(), vitals = null) {
  const open = await openOne(env);
  const from = (open?.opened_at ?? now) - 48 * HOUR;
  const [signals, marks, bugs, past, mutes] = await Promise.all([
    incidentSignals(env, now, vitals),
    changeMarks(env, from, now),
    all(env, `SELECT i.id, i.created_at, json_extract(i.info, '$.app') AS app, (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first
      FROM inquiries i WHERE i.kind IN ('bug', 'problem') AND i.created_at >= ? ORDER BY i.created_at DESC LIMIT 40`, open?.opened_at ? open.opened_at - 6 * HOUR : now - DAY),
    all(env, "SELECT * FROM incidents WHERE closed_at IS NOT NULL ORDER BY opened_at DESC LIMIT 10").catch(() => []),
    mutesOf(env),
  ]);
  const read = (r) => {
    let s = [];
    try {
      s = JSON.parse(r.signals ?? "[]");
    } catch {
      s = [];
    }
    return { id: r.id, openedAt: r.opened_at, closedAt: r.closed_at, signals: s, note: r.note, by: r.by, auto: Boolean(r.auto) };
  };
  return {
    // 2.0.0 [RVN-01] 다시 묻지 않기로 한 신호: 화면에 무엇을 언제까지 조용히 두는지 보여요(U22c-01).
    open: open ? read(open) : null, signals: signals.map((s) => ({ ...s, muted: Boolean(mutes[s.key]) && !(s.v >= mutes[s.key].v * INCIDENT.worse) })),
    mutes: Object.entries(mutes).map(([key, m]) => ({ key, until: m.until ?? null, at: m.at ?? null })),
    // 2.0.0 [RVN-02] '그 전 48시간에 바꾼 것'에 이 사고가 열린 일 자체는 빼요.
    marks: marks.filter((m) => !(open && m.kind === "incident_open" && m.at >= open.opened_at)), bugs: bugs.map((b) => ({ id: b.id, at: b.created_at, app: b.app, first: String(b.first ?? "").slice(0, 80) })),
    past: past.map(read),
    notice: "지금 일부 기능이 잘 안 될 수 있어요. 원인을 찾고 있고, 고치는 대로 다시 알려 드릴게요. 불편을 드려 죄송해요.",
    steps: ["무엇이 늘었는지 보기", "바로 전에 바꾼 것 되돌리기(판·기능)", "필요하면 기능 강제로 끄기", "공지 올리기", "문제 신고를 개발사항 하나로 묶기", "고친 뒤 무엇을 했는지 적고 닫기"],
  };
}

/** POST /incident {open: true, note} 직접 열기, POST /incident/<id>/close {note}. */
export async function incidentRoute(request, env, path, method, now, vitals) {
  if (path === "/incident" && method === "GET") return json(await incidentView(env, now, await vitals()));
  if (path === "/incident" && method === "POST") {
    const body = await readJson(request, 2048);
    if (await openOne(env)) return json({ error: "open" }, 409);
    const id = `x${randomId(15)}`;
    const note = String(body?.note ?? "").slice(0, 300);
    await run(env, "INSERT INTO incidents (id, opened_at, signals, note, by, auto) VALUES (?, ?, '[]', ?, ?, 0)", id, now, note, env.actor?.name ?? "관리자");
    await audit(env, now, "incident_open", id, note || "직접 엶");
    return json({ id });
  }
  const m = path.match(/^\/incident\/(x[A-Za-z0-9]{15})\/close$/);
  if (m && method === "POST") {
    const body = await readJson(request, 4096);
    const note = String(body?.note ?? "").replace(/[\u0000-\u001f]/g, " ").trim().slice(0, 1000);
    if (!note) return json({ error: "note", field: "note" }, 400);
    // 2.0.0 [RVN-01] 닫을 때 지금 있는 신호는 다시 묻지 않기에 넣어요(SVC-81, U22c-01): days 0은 '한 번 꺼질 때까지', 1·3·7·14는 그 날 수 동안.
    const days = [0, 1, 3, 7, 14].includes(Number(body?.days)) ? Number(body.days) : 0;
    const row = await one(env, "SELECT signals FROM incidents WHERE id = ? AND closed_at IS NULL", m[1]);
    if (!row) return json({ error: "not_found" }, 404);
    const r = await run(env, "UPDATE incidents SET closed_at = ?, note = ?, by = ? WHERE id = ? AND closed_at IS NULL", now, note, env.actor?.name ?? "관리자", m[1]);
    if (!r?.meta?.changes) return json({ error: "not_found" }, 404);
    const current = await incidentSignals(env, now, await vitals().catch(() => null)).catch(() => []);
    const mutes = await mutesOf(env);
    for (const s of current) mutes[s.key] = { v: s.v, until: days ? now + days * DAY : null, at: now };
    for (const s of parseJson(row.signals, [])) if (!mutes[s.key] && days) mutes[s.key] = { v: s.v, until: now + days * DAY, at: now };
    await saveMutes(env, mutes);
    await audit(env, now, "incident_close", m[1], `${note.slice(0, 180)}${days ? ` · ${days}일 다시 묻지 않기` : ""}`);
    return json({ ok: true, muted: Object.keys(mutes), days });
  }
  return null;
}

/** 배지·맨 위 띠: 열린 사고(id, 언제, 신호 첫 줄). */
export async function incidentBadge(env) {
  const o = await openOne(env);
  if (!o) return null;
  let first = "";
  try {
    first = JSON.parse(o.signals ?? "[]")[0]?.text ?? "";
  } catch {
    first = "";
  }
  return { id: o.id, openedAt: o.opened_at, first };
}
