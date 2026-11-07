import { all, audit, json, notifyAdmins, one, randomId, readJson, run } from "./index.js";
import { changeMarks } from "./release-desk.js";
import { watchHour } from "./ops-watch.js";
import { DAY, HOUR } from "./units.js";

// ---- 1.38 [ADM3-32] 사고 모드 ----
//
// 보고서 4-3항. 서버 오류·문제 신고가 갑자기 늘거나 정기 작업이 계속 실패하거나 Play 비정상 종료·ANR이 기준을 넘으면 '사고'를 열어요.
// 관리자 앱 맨 위에 빨간 띠 하나, 누르면 한 화면에: 무엇이 늘었는지, 그 전 48시간에 바꾼 것(판·기능·공지), 끌 수 있는 스위치, 공지 초안,
// 그동안 들어온 문제 신고(개발사항 하나로 묶기). 닫기는 사람이 해요(무엇을 했는지 한 줄과 함께). docs/incident-response.md를 단추로 옮긴 것.

export const INCIDENT = {
  /** 지난 한 시간 서버 오류가 이 수 이상이고 7일 시간 평균의 [errX]배 이상. */
  errMin: 10, errX: 5,
  /** 지난 한 시간 500 비율(요청 [rateN] 이상일 때). */
  rate: 0.05, rateN: 50,
  /** 지난 6시간 문제 신고가 이 수 이상이고 지난 7일 6시간 평균의 [bugX]배 이상. */
  bugMin: 5, bugX: 3,
  /** 같은 정기 작업 단계가 이만큼 연달아 실패. */
  jobFails: 3,
  /** Play 기준(나쁜 행동 기준). */
  crash: 0.0109, anr: 0.0047,
};

export const INCIDENT_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS incidents (id TEXT PRIMARY KEY, opened_at INTEGER NOT NULL, closed_at INTEGER, signals TEXT NOT NULL DEFAULT '[]',
    note TEXT NOT NULL DEFAULT '', by TEXT NOT NULL DEFAULT '', auto INTEGER NOT NULL DEFAULT 1)`,
];

/** 지금 보이는 사고 신호들. [vitals]는 Play 값(있으면). */
export async function incidentSignals(env, now = Date.now(), vitals = null) {
  const out = [];
  const hour = watchHour(now - HOUR);
  const [last, base] = await Promise.all([
    one(env, "SELECT SUM(n) AS n, SUM(err) AS err FROM req_stats WHERE hour >= ?", hour).catch(() => null),
    one(env, "SELECT SUM(err) AS err, COUNT(DISTINCT hour) AS hours FROM req_stats WHERE hour >= ? AND hour < ?", watchHour(now - 7 * DAY), hour).catch(() => null),
  ]);
  const avg = base?.hours ? (base.err ?? 0) / base.hours : 0;
  if ((last?.err ?? 0) >= INCIDENT.errMin && (last.err >= avg * INCIDENT.errX)) out.push({ key: "errors", text: `서버 오류 한 시간 ${last.err}번(평소 ${avg.toFixed(1)}번)` });
  else if ((last?.n ?? 0) >= INCIDENT.rateN && last.err / last.n >= INCIDENT.rate) out.push({ key: "rate", text: `요청의 ${Math.round((last.err / last.n) * 100)}%가 서버 오류` });
  const [bugs, bugBase] = await Promise.all([
    one(env, "SELECT COUNT(*) AS n FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ?", now - 6 * HOUR),
    one(env, "SELECT COUNT(*) AS n FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ? AND created_at < ?", now - 7 * DAY, now - 6 * HOUR),
  ]);
  const bugAvg = (bugBase?.n ?? 0) / ((7 * 24 - 6) / 6);
  if ((bugs?.n ?? 0) >= INCIDENT.bugMin && bugs.n >= bugAvg * INCIDENT.bugX) out.push({ key: "bugs", text: `6시간 문제 신고 ${bugs.n}건(평소 ${bugAvg.toFixed(1)}건)` });
  const jobs = await all(env, "SELECT step, fails, ok_at, fail_at FROM job_runs WHERE fail_at IS NOT NULL AND (ok_at IS NULL OR fail_at > ok_at)").catch(() => []);
  // 매시 작업이라 마지막 성공 뒤 (jobFails - 1)시간 넘게 실패만 했으면 연달아 jobFails번이에요.
  const stuck = jobs.filter((j) => (j.ok_at ? j.fail_at - j.ok_at >= (INCIDENT.jobFails - 1) * HOUR : j.fails >= INCIDENT.jobFails));
  if (stuck.length) out.push({ key: "jobs", text: `정기 작업이 계속 실패: ${stuck.map((j) => j.step).join(", ")}` });
  if (vitals && !vitals.error) {
    if (vitals.crash != null && vitals.crash >= INCIDENT.crash) out.push({ key: "crash", text: `비정상 종료 ${(vitals.crash * 100).toFixed(2)}%(기준 1.09%)` });
    if (vitals.anr != null && vitals.anr >= INCIDENT.anr) out.push({ key: "anr", text: `ANR ${(vitals.anr * 100).toFixed(2)}%(기준 0.47%)` });
  }
  return out;
}

const openOne = (env) => one(env, "SELECT * FROM incidents WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1").catch(() => null);

/** 매시 작업: 신호가 있고 열린 사고가 없으면 열고 바로 알려요(조용한 시간도). 신호가 사라져도 닫지는 않아요(사람이 닫아요). */
export async function incidentTick(env, now = Date.now(), vitals = null) {
  const signals = await incidentSignals(env, now, vitals);
  const open = await openOne(env);
  if (open) {
    if (signals.length) await run(env, "UPDATE incidents SET signals = ? WHERE id = ?", JSON.stringify(signals), open.id);
    return { open: open.id, signals: signals.length };
  }
  if (!signals.length) return { open: null, signals: 0 };
  const id = `x${randomId(15)}`;
  await run(env, "INSERT INTO incidents (id, opened_at, signals) VALUES (?, ?, ?)", id, now, JSON.stringify(signals));
  await audit(env, now, "incident_open", id, signals.map((s) => s.text).join(" · ").slice(0, 280));
  await notifyAdmins(env, "incident", { title: "사고 같아요", body: signals.map((s) => s.text).join(" · "), url: "/admin/#/incident", urgent: true }, now, { ignoreQuiet: true });
  return { open: id, signals: signals.length };
}

/** 사고 화면: 열린 사고(있으면), 지금 신호, 지난 48시간 바꾼 것, 그동안 문제 신고, 공지 초안, 최근 닫은 사고. */
export async function incidentView(env, now = Date.now(), vitals = null) {
  const open = await openOne(env);
  const from = (open?.opened_at ?? now) - 48 * HOUR;
  const [signals, marks, bugs, past] = await Promise.all([
    incidentSignals(env, now, vitals),
    changeMarks(env, from, now),
    all(env, `SELECT i.id, i.created_at, json_extract(i.info, '$.app') AS app, (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first
      FROM inquiries i WHERE i.kind IN ('bug', 'problem') AND i.created_at >= ? ORDER BY i.created_at DESC LIMIT 40`, open?.opened_at ? open.opened_at - 6 * HOUR : now - DAY),
    all(env, "SELECT * FROM incidents WHERE closed_at IS NOT NULL ORDER BY opened_at DESC LIMIT 10").catch(() => []),
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
    open: open ? read(open) : null, signals, marks, bugs: bugs.map((b) => ({ id: b.id, at: b.created_at, app: b.app, first: String(b.first ?? "").slice(0, 80) })),
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
    const r = await run(env, "UPDATE incidents SET closed_at = ?, note = ?, by = ? WHERE id = ? AND closed_at IS NULL", now, note, env.actor?.name ?? "관리자", m[1]);
    if (!r?.meta?.changes) return json({ error: "not_found" }, 404);
    await audit(env, now, "incident_close", m[1], note.slice(0, 200));
    return json({ ok: true });
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
