import { BUDGET_WARN, LIMITS, audit, batch, can, cfPlan, json, one, readJson, stmt } from "./index.js";
import { WATCH_SCHEMA, errorState, healthBoard, jobRow, requiredSetup, setErrorState, speedState } from "./ops-watch.js";
import { moneyBoard, moneyYesterday, savePriceTable } from "./money-board.js";
import { findDevice, personView } from "./person.js";
import { RELEASE_SCHEMA, changeMarks, releaseRoute, versionHealth } from "./release-desk.js";
import { KEEP_SCHEMA, backupTick, keepRoute, requestTick } from "./keep.js";
import { securityView } from "./security-board.js";
import { REVIEW_SCHEMA, reviewCounts, reviewRoute, reviewTick } from "./store-reviews.js";
import { INCIDENT_SCHEMA, incidentBadge, incidentRoute, incidentTick } from "./incident.js";
import { DAY, HOUR } from "./units.js";
import { draftStats } from "./claude-drafts.js";

// ---- 1.38 [ADM3] 관리자 앱 개편의 서버 쪽 길 하나 ----
//
// 보고서 /mnt/project-files/조사/1.38-관리자앱-전면리뷰-보고서.md, 사용자 2026-11-06 "전부 진행". 새 화면들이 읽는 길을 여기 모아요.
//   /watch/health, /watch/errors(/<sig>)  서버 상태·오류 묶음 [ADM3-05·13·19]       최고 관리자
//   /money, /money/prices                 돈 화면·가격표 [ADM3-12]                  최고 관리자
//   /person?inquiry=|mark=                사람 옆 패널 [ADM3-11]                    문의 권한(결제 칸은 주인·보상 권한)
//   /release(/<v>)                        출시 날 점검표 [ADM3-14]                  운영(ops) 권한
//   /health/versions, /changes            판 건강·바뀐 일 [ADM3-15·33]              지표 권한
//   /data-requests, /backup               데이터 요청·백업 [ADM3-16]               최고 관리자
//   /security                             보안 한 장 [RVQ-18]                       최고 관리자
//   /reviews                              스토어 리뷰 [ADM3-18]                     문의 권한(Play로 보내기는 주인)
//   /incident                             사고 모드 [ADM3-32]                       최고 관리자
//   /cockpit                              Claude 조종석 [ADM3-31]                   문의 권한
//   /story/<dev id>                       개발사항 이야기 [ADM3-34]                 개발사항 권한

export const OPS_SCHEMA = [...WATCH_SCHEMA, ...RELEASE_SCHEMA, ...KEEP_SCHEMA, ...REVIEW_SCHEMA, ...INCIDENT_SCHEMA];

/** index.js areaFor가 먼저 묻는 곳: 이 길이면 그 권한(undefined면 이 모듈 길이 아님). null은 로그인만, "owner"는 주인만. */
export function opsArea(path) {
  // 1.39 [ADM3-52] 응답 속도는 숫자를 보는 사람·Claude 열쇠도 읽어요(주소·건수·걸린 시간뿐, 개인 정보 없음). 로딩 개선을 숫자로 재려고.
  if (path === "/watch/speed") return "metrics";
  if (path.startsWith("/watch/") || path === "/money" || path.startsWith("/money/") || path === "/data-requests" || path.startsWith("/data-requests/")
    || path === "/backup" || path.startsWith("/backup/") || path === "/incident" || path.startsWith("/incident/") || path === "/security") return "owner";
  if (path === "/person") return "inquiries";
  if (path === "/release" || path.startsWith("/release/")) return "ops";
  if (path === "/health/versions" || path === "/changes") return "metrics";
  if (path === "/reviews" || path.startsWith("/reviews/")) return "inquiries";
  if (path === "/cockpit") return "inquiries";
  if (path.startsWith("/story/")) return "dev";
  return undefined;
}

/** 매시 작업에 더하는 단계: 리뷰, 백업, 사고, 데이터 요청 기한. [vitals]는 Play 값을 읽는 함수. */
export const opsSteps = (env, vitals) => [
  ["reviews", () => reviewTick(env, Date.now())],
  ["backup", () => backupTick(env, Date.now())],
  ["incident", async () => incidentTick(env, Date.now(), vitals ? await vitals().catch(() => null) : null)],
  ["requests", () => requestTick(env, Date.now())],
];

/**
 * 배지에 더하는 숫자: index.js BADGE_SQL에 들어가 메뉴 숫자와 같은 한 번의 batch로 읽어요([area, sql], 볼 수 있는 사람에게만).
 * 사고는 열린 수(맨 위 띠), 데이터 요청은 기한이 이틀 안이거나 넘은 수도 따로.
 */
export const OPS_BADGE_SQL = {
  errorsNew: ["owner", "SELECT COUNT(*) AS n FROM error_groups WHERE status IN ('new', 'again')"],
  requestsOpen: ["owner", "SELECT COUNT(*) AS n FROM data_requests WHERE status = 'open'"],
  requestsLate: ["owner", "SELECT COUNT(*) AS n FROM data_requests WHERE status = 'open' AND due_at < (CAST(strftime('%s', 'now') AS INTEGER) + 2 * 86400) * 1000"],
  incidentOpen: ["owner", "SELECT COUNT(*) AS n FROM incidents WHERE closed_at IS NULL"],
  reviewsWaiting: ["inquiries", "SELECT COUNT(*) AS n FROM store_reviews WHERE status <> 'replied'"],
  releaseOpen: ["ops", "SELECT COUNT(*) AS n FROM release_runs WHERE done_at IS NULL"],
};

/**
 * 오늘 › 주의 필요에 더하는 것(주인에게만): 꼭 필요한 설정, 정기 작업, 새 오류, 데이터 요청 기한, 사고, 낮은 별점 리뷰.
 * 오늘 화면이 한 번의 batch로 읽게 [OPS_ALERT_SQL]을 index.js today()가 함께 보내고, [opsAlertsFrom]이 그 답으로 줄을 만들어요.
 */
export const OPS_ALERT_SQL = (now) => ({
  ops_jobs: ["SELECT step, ok_at, fail_at, error FROM job_runs"],
  ops_errors: ["SELECT COUNT(*) AS n FROM error_groups WHERE status IN ('new', 'again') AND last_at > ?", now - DAY],
  ops_requests: ["SELECT COUNT(*) AS n FROM data_requests WHERE status = 'open' AND due_at < ?", now + 2 * DAY],
  ops_incident: ["SELECT id, signals FROM incidents WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1"],
  ops_reviews: ["SELECT COUNT(*) AS n FROM store_reviews WHERE status <> 'replied' AND stars <= 2"],
  // 1.39 [ADM3-42] 지금 잠긴 로그인(네트워크·관리자 계정). 내가 잠겼으면 내 계정에서 풀고, 아니면 누가 맞혀 보는 중일 수 있어요.
  ops_logins: ["SELECT COUNT(*) AS n FROM counters WHERE key LIKE 'login|%' AND key <> 'login|all' AND count >= ? AND expires > ?", LIMITS.loginFailures, now],
  // 1.39 [ADM3-44] 실서버 둘러보기(11-08) A9·B4·B8·B9: 잠기기 전의 연달아 틀림, 오래 기다린 보상, 오래 안 쓴 관리자, 최고 관리자 패스키.
  ops_login_tries: [`SELECT COUNT(*) AS places, COALESCE(SUM(count), 0) AS n FROM counters WHERE key LIKE 'login|%' AND key <> 'login|all'
    AND count >= ? AND count < ? AND expires > ?`, LOGIN_TRIES, LIMITS.loginFailures, now],
  ops_owed: ["SELECT COUNT(*) AS n, MIN(created_at) AS since FROM rewards WHERE status = 'owed' AND created_at < ?", now - REWARD_DAYS * DAY],
  ops_idle_admins: ["SELECT COUNT(*) AS n FROM admins WHERE status = 'active' AND COALESCE(last_login, approved_at, created_at) < ?", now - IDLE_ADMIN_DAYS * DAY],
  ops_passkey: ["SELECT COUNT(*) AS n FROM admin_passkeys WHERE admin = 'owner'"],
  // 2.0.0 [RVQ-05] 검토 SRV-08·U21-05: 최고 관리자의 두 번째 확인(켬)이 있는지.
  ops_second: ["SELECT COUNT(*) AS n FROM admin_totp WHERE admin = 'owner' AND on_at IS NOT NULL"],
  // 2.0.0 [RVQ-12] 검토 SVC-07: 오늘 D1 쓰기(UTC 날, D1의 하루)와 정기 작업 한 호출의 질의 수.
  ops_meter: ["SELECT count FROM counters WHERE key = ?", `meter|${new Date(now).toISOString().slice(0, 10)}`],
  ops_jobq: ["SELECT value FROM settings WHERE key = 'job_queries'"],
});

/**
 * 1.39 [ADM3-44] 한 곳에서 이만큼 틀리면(잠기기 전) 알려요(휴대폰 알림 '급증'으로도). 드릴 보상은 이 날 수(알림 '기한'), 관리자는 이 날 수 넘게
 * 안 쓰면. 패스키·안 쓴 관리자는 'nudge'라 휴대폰 알림으로는 가지 않고 오늘 화면에만 있어요.
 */
/** 1.39 [ADM3-44] 한 곳에서 이만큼 틀리면(잠기기 전) 알려요. 드릴 보상은 이 날 수, 관리자는 이 날 수 넘게 안 쓰면. */
export const LOGIN_TRIES = 3;
export const REWARD_DAYS = 7;
export const IDLE_ADMIN_DAYS = 30;

export function opsAlertsFrom(env, got, now = Date.now()) {
  const out = [];
  const setup = requiredSetup(env);
  const inc = got.ops_incident?.[0];
  if (inc) {
    let first = "";
    try {
      first = JSON.parse(inc.signals ?? "[]")[0]?.text ?? "";
    } catch {
      first = "";
    }
    out.push({ key: `incident:${inc.id}`, kind: "incident", tone: "bad", title: "사고가 열려 있어요", meta: first, to: "#/incident" });
  }
  if (got.ops_requests?.[0]?.n) out.push({ key: `requests:${got.ops_requests[0].n}`, kind: "incident", tone: "bad", title: `데이터 요청 ${got.ops_requests[0].n}건 기한 임박·넘김`, meta: "개인정보·환불·법 신고", to: "#/requests" });
  if (setup.missing.length) out.push({ key: `setup:${setup.missing.join(",")}`, kind: "incident", tone: "bad", title: `꼭 필요한 설정 ${setup.missing.length}개 빠짐`, meta: setup.missing.join(" · "), to: "#/server" });
  const bad = (got.ops_jobs ?? []).map((r) => jobRow(r, now, env)).filter((j) => j.state === "fail" || j.state === "stale");
  if (bad.length) out.push({ key: `jobs:${bad.map((j) => j.step).join(",")}`, kind: "incident", tone: "bad", title: `정기 작업 ${bad.length}개 ${bad.some((j) => j.state === "fail") ? "실패" : "멈춤"}`, meta: bad.map((j) => j.name).join(" · "), to: "#/server" });
  if (got.ops_errors?.[0]?.n) out.push({ key: `errors:${got.ops_errors[0].n}`, kind: "spike", tone: "warn", title: `새 오류 ${got.ops_errors[0].n}가지`, meta: "하루 안에 난 서버 오류 묶음", to: "#/errors" });
  if (got.ops_reviews?.[0]?.n) out.push({ key: `reviews:${got.ops_reviews[0].n}`, kind: "spike", tone: "warn", title: `답 안 한 낮은 별점 리뷰 ${got.ops_reviews[0].n}개`, meta: "별 1~2개", to: "#/reviews" });
  if (got.ops_logins?.[0]?.n) out.push({ key: `logins:${got.ops_logins[0].n}`, kind: "spike", tone: "warn", title: `로그인 잠긴 곳 ${got.ops_logins[0].n}곳`,
    meta: "1시간 뒤 저절로 풀려요. 내가 잠긴 거면 내 계정 › 로그인에서 바로 풀어요", to: "#/account" });
  const tries = got.ops_login_tries?.[0];
  if (tries?.places) out.push({ key: `login-tries:${tries.places}:${tries.n}`, kind: "spike", tone: "warn", title: `로그인 실패가 연달아 있어요(${tries.places}곳, ${tries.n}번)`,
    meta: `1시간 안에 ${LIMITS.loginFailures}번이면 잠겨요. 내가 아니면 Cloudflare에서 ADMIN_PASSWORD를 바꿔요`, to: "#/audit" });
  const owed = got.ops_owed?.[0];
  if (owed?.n) out.push({ key: `owed:${owed.n}`, kind: "late", tone: "warn", title: `드릴 보상 ${owed.n}개가 ${REWARD_DAYS}일 넘게 기다려요`,
    meta: owed.since ? `가장 오래된 것 ${Math.floor((now - owed.since) / DAY)}일째` : "", to: "#/rewards" });
  if (got.ops_idle_admins?.[0]?.n) out.push({ key: `idle-admins:${got.ops_idle_admins[0].n}`, kind: "nudge", tone: "warn",
    title: `${IDLE_ADMIN_DAYS}일 넘게 안 쓴 관리자 ${got.ops_idle_admins[0].n}명`, meta: "안 쓰는 계정은 멈춰 두면 안전해요(관리 › 관리자)", to: "#/admins" });
  // 2.0.0 [RVQ-05] 검토 SRV-08·U21-05: 최고 관리자는 두 번째 확인이나 패스키 중 하나는 꼭. 둘 다 없으면 비밀번호 하나에 기대니 붉게(맨 위 쪽).
  const second = Boolean(got.ops_second?.[0]?.n);
  const passkey = Boolean(got.ops_passkey?.[0]?.n);
  if (got.ops_second && !second && got.ops_passkey && !passkey) {
    out.unshift({ key: "owner:single", kind: "incident", tone: "bad", title: "최고 관리자가 비밀번호 하나로만 들어와요",
      meta: "두 번째 확인이나 패스키 중 하나를 켜요(내 계정). 비밀번호가 새면 바로 들어올 수 있어요", to: "#/account" });
  } else if (got.ops_passkey && !passkey) {
    out.push({ key: "passkey:none", kind: "nudge", tone: "warn", title: "최고 관리자 패스키가 없어요",
      meta: "지문·얼굴로 들어오면 비밀번호 잠김·피싱 걱정이 줄어요(내 계정 › 패스키)", to: "#/account" });
  }
  // 2.0.0 [RVQ-12] 검토 SVC-07: 요금제의 하루 몫·호출당 질의 상한의 80%를 넘으면(90%에서 덜 중요한 것부터 쉬어요).
  const plan = cfPlan(env);
  const used = Number(got.ops_meter?.[0]?.count ?? 0);
  if (got.ops_meter && used >= plan.d1WritesDay * BUDGET_WARN) {
    out.push({ key: `budget:d1:${new Date(now).toISOString().slice(0, 10)}`, kind: "spike", tone: "warn",
      title: `오늘 D1 쓰기가 하루 몫의 ${Math.floor((used / plan.d1WritesDay) * 100)}%예요`,
      meta: `${plan.name} 하루 몫 ${plan.d1WritesDay.toLocaleString("ko-KR")}번. 90%면 사용 통계·문의 사진부터 내일까지 쉬어요`, to: "#/server" });
  }
  let jobq = {};
  try {
    jobq = JSON.parse(got.ops_jobq?.[0]?.value ?? "{}");
  } catch {
    jobq = {};
  }
  const today = new Date(now).toISOString().slice(0, 10);
  const heavy = Object.entries(jobq).filter(([, q]) => q?.day === today && Number(q.top ?? q.n) >= plan.queriesPerCall * BUDGET_WARN);
  if (heavy.length) {
    out.push({ key: `budget:queries:${heavy.map(([k]) => k).join(",")}`, kind: "incident", tone: "bad",
      title: `정기 작업 한 번이 D1 질의 ${Math.max(...heavy.map(([, q]) => Number(q.top ?? q.n)))}번을 썼어요`,
      meta: `상한 ${plan.queriesPerCall}번을 넘으면 그 뒤 단계가 실패해요. 단계를 47분 쪽으로 옮기거나 묶어 읽게 고쳐요`, to: "#/server" });
  }
  if (second && env?.ADMIN_2FA_RESET === "1") {
    out.unshift({ key: "owner:2fa-reset", kind: "incident", tone: "bad", title: "최고 관리자 두 번째 확인이 꺼진 채예요",
      meta: "Cloudflare의 ADMIN_2FA_RESET이 1이에요. 휴대폰을 찾았으면 지워요", to: "#/account" });
  }
  return out;
}

/** [ADM3-31] Claude 조종석: 보류 중인 Claude 답(보내기 전 멈출 수 있음), 오늘 Claude가 한 일, Claude 차례 수. */
export async function cockpit(env, now = Date.now()) {
  const drafts = await draftStats(env, now).catch(() => null);
  const start = Date.parse(`${new Date(now + 9 * HOUR).toISOString().slice(0, 10)}T00:00:00+09:00`);
  const [held, done, keys] = (await batch(env, [
    stmt(env, `SELECT h.id, h.inquiry_id, h.text, h.close, h.until, h.at, (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = h.inquiry_id AND m.sender = 'user'
      ORDER BY m.id DESC LIMIT 1) AS ask, i.kind FROM held_replies h LEFT JOIN inquiries i ON i.id = h.inquiry_id ORDER BY h.until LIMIT 50`),
    stmt(env, `SELECT at, action, target, detail FROM audit WHERE actor_id LIKE 'key:%' AND at >= ? ORDER BY id DESC LIMIT 60`, start - DAY),
    stmt(env, "SELECT label, last_at, day, n, writes, expires_at, revoked_at FROM admin_keys ORDER BY created_at DESC LIMIT 3"),
  ]).catch(() => [{ results: [] }, { results: [] }, { results: [] }])).map((r) => r.results ?? []);
  return {
    held: held.map((h) => ({ id: h.id, inquiry: h.inquiry_id, text: h.text, close: Boolean(h.close), until: h.until, at: h.at, ask: String(h.ask ?? "").slice(0, 120), kind: h.kind })),
    done: done.map((d) => ({ at: d.at, action: d.action, target: d.target, detail: d.detail })),
    today: done.filter((d) => d.at >= start).length,
    // 1.39 [ADM3-44] B10: 열쇠로 오늘 읽기만 한 횟수(장부·문의 읽기). 고친 일은 활동 기록에 남고, 읽기는 열쇠 카운터에만 있어요.
    reads: keys.reduce((n, k) => n + (k.day === new Date(now + 9 * HOUR).toISOString().slice(0, 10) ? Math.max(0, (k.n ?? 0) - (k.writes ?? 0)) : 0), 0),
    keys: keys.map((k) => ({ label: k.label, lastAt: k.last_at, n: k.day && k.day === new Date(now + 9 * HOUR).toISOString().slice(0, 10) ? k.n : 0, alive: !k.revoked_at && k.expires_at > now })),
    // 1.39 [ADM3-49] 제보·보상·합치기 초안: 열린 수, 사람이 정한 것과 같았던 비율, 넓히기 관문.
    drafts,
  };
}

/** [ADM3-34] 개발사항 하나의 이야기: 문의가 들어오고 → 묶이고 → 상태가 바뀌고 → 판이 나가고 → 답이 간 순서. */
export async function story(env, id) {
  const item = await one(env, "SELECT id, title, status, released_in, created_at, created_by_name FROM dev_items WHERE id = ?", id);
  if (!item) return null;
  const [links, hist] = (await batch(env, [
    stmt(env, `SELECT l.inquiry_id, l.at, l.by, l.told_at, i.created_at AS asked, i.kind FROM dev_links l LEFT JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ? ORDER BY l.at`, id),
    stmt(env, "SELECT at, actor, action, detail FROM dev_history WHERE item_id = ? ORDER BY id", id),
  ])).map((r) => r.results ?? []);
  const ship = await one(env, "SELECT at, target FROM audit WHERE action = 'shipped_version' AND target = ? ORDER BY id DESC LIMIT 1", item.released_in ?? "").catch(() => null);
  const ev = [];
  for (const l of links) {
    if (l.asked) ev.push({ at: l.asked, kind: "ask", text: `문의 들어옴 (${l.kind ?? "문의"})`, ref: l.inquiry_id });
    ev.push({ at: l.at, kind: "link", text: `개발사항에 묶음 · ${l.by}`, ref: l.inquiry_id });
    if (l.told_at) ev.push({ at: l.told_at, kind: "told", text: "고친 판이 나왔다고 답함", ref: l.inquiry_id });
  }
  ev.push({ at: item.created_at, kind: "made", text: `개발사항 만듦 · ${item.created_by_name}` });
  for (const h of hist) {
    let d = {};
    try {
      d = JSON.parse(h.detail ?? "{}");
    } catch {
      d = {};
    }
    if (h.action === "link" || h.action === "unlink") continue;
    const STATUS = { todo: "접수", doing: "개발중", done: "완료", rejected: "반려", handed: "Claude에게 넘김" };
    const NAMES = { create: "만듦", edit: "고침", assign: "담당 바뀜", delete: "지움", restore: "되살림", reply: "묶인 문의에 답", result: "개발 결과",
      // 1.39 [LOOP] 자동 순환이 남기는 것.
      sync: `저장소에서 받음${d.pr ? ` · PR #${d.pr}` : ""}${d.version ? ` · ${d.version}` : ""}`, live: "업데이트 없이 실제로 닿음", phone_told: `고친 판(${d.app ?? ""})으로 연 폰에 안내`,
      fixed_yes: "문의한 분: 해결됐어요", fixed_no: "문의한 분: 아직이에요(다시 열림)", me_too: "알려진 문제판: 나도 겪어요",
      effect: `효과 판정: ${({ better: "좋아졌어요", same: "그대로예요", worse: "나빠졌어요", none: "잴 숫자가 없어요" })[d.effect] ?? ""}${d.why ? ` · ${d.why}` : ""}`,
      tier: `요금 ${d.tier?.[1] ?? ""}${d.note ? ` · ${d.note}` : ""}`, release: `반영 판 ${d.releasedIn?.[1] ?? ""}` };
    const what = h.action === "status" ? `상태 ${STATUS[d.status?.[0]] ?? d.status?.[0] ?? ""} → ${STATUS[d.status?.[1]] ?? d.status?.[1] ?? ""}`
      : h.action === "note" ? `기록 ${String(d.text ?? "").slice(0, 60)}` : NAMES[h.action] ?? h.action;
    if (h.action === "create") continue;
    ev.push({ at: h.at, kind: h.action, text: `${what} · ${h.actor}` });
  }
  if (ship) ev.push({ at: ship.at, kind: "ship", text: `${ship.target} 판이 나감` });
  ev.sort((a, b) => a.at - b.at);
  return { id: item.id, title: item.title, status: item.status, releasedIn: item.released_in, events: ev,
    days: ev.length ? Math.round(((ev.at(-1).at - ev[0].at) / DAY) * 10) / 10 : 0 };
}

/** 아침 9시 요약에 더하는 주인 몫의 말(없으면 ""). */
export async function morningExtra(env, now = Date.now()) {
  const parts = [];
  try {
    const m = await moneyYesterday(env, now);
    if (m.started || m.passes || m.trials || m.stopped) parts.push(`어제 구독 ${m.started ?? 0}·이용권 ${m.passes ?? 0}·체험 ${m.trials ?? 0}·해지 ${m.stopped ?? 0}`);
    const e = await one(env, "SELECT COUNT(*) AS n FROM error_groups WHERE status IN ('new', 'again')");
    if (e?.n) parts.push(`새 오류 ${e.n}가지`);
    const rv = await reviewCounts(env);
    if (rv.waiting) parts.push(`리뷰 답 ${rv.waiting}${rv.low ? `(낮은 별 ${rv.low})` : ""}`);
    const r = await one(env, "SELECT COUNT(*) AS n FROM data_requests WHERE status = 'open'");
    if (r?.n) parts.push(`데이터 요청 ${r.n}`);
    const inc = await incidentBadge(env);
    if (inc) parts.unshift("사고가 열려 있어요");
  } catch {
    // 표가 아직 없어요.
  }
  return parts.join(" · ");
}

/** 새 길의 처리. 아니면 null. [vitals]는 Play 값을 읽는 함수. */
export async function opsRoute(request, env, url, path, method, now, vitals) {
  const actor = env.actor;
  if (path === "/watch/health" && method === "GET") return json(await healthBoard(env, now));
  if (path === "/watch/speed" && method === "GET") return json(await speedState(env, now, Math.min(168, Number(url.searchParams.get("hours")) || 24)));
  if (path === "/watch/errors" && method === "GET") return json(await errorState(env, now, { status: url.searchParams.get("status") ?? "open" }));
  let m = path.match(/^\/watch\/errors\/([a-z0-9]{1,13})$/);
  if (m && method === "POST") {
    const body = await readJson(request, 2048);
    if (!(await setErrorState(env, m[1], body?.status, body?.note, now))) return json({ error: "not_found" }, 404);
    await audit(env, now, "error_state", m[1], `${body?.status ?? ""} ${String(body?.note ?? "").slice(0, 80)}`);
    return json({ ok: true });
  }
  if (path === "/money" && method === "GET") return json(await moneyBoard(env, now, url.searchParams.get("days")));
  if (path === "/money/prices" && method === "POST") return savePriceTable(request, env, now);
  if (path === "/person" && method === "GET") {
    const found = await findDevice(env, { inquiry: url.searchParams.get("inquiry"), mark: url.searchParams.get("mark") });
    if (found.ambiguous) return json({ error: "ambiguous", n: found.ambiguous }, 409);
    if (!found.device) return json({ error: "not_found" }, 404);
    return json(await personView(env, found.device, now, { money: Boolean(actor?.owner || can(actor, "rewards")) }));
  }
  if (path === "/release" || path.startsWith("/release/")) {
    if (method !== "GET" && !can(actor, "ops", "edit")) return json({ error: "no_permission", area: "ops" }, 403);
    const r = await releaseRoute(request, env, path, method, now);
    if (r) return r;
  }
  if (path === "/health/versions" && method === "GET") return json(await versionHealth(env, now, Math.min(60, Number(url.searchParams.get("days")) || 14), await vitals().catch(() => null)));
  if (path === "/changes" && method === "GET") {
    const to = Number(url.searchParams.get("to")) || now;
    const from = Number(url.searchParams.get("from")) || to - 28 * DAY;
    return json({ marks: await changeMarks(env, Math.max(from, to - 400 * DAY), to) });
  }
  if (path === "/data-requests" || path.startsWith("/data-requests/") || path === "/backup" || path.startsWith("/backup/")) {
    const r = await keepRoute(request, env, url, path, method, now);
    if (r) return r;
  }
  // 2.0.0 [RVQ-18] 검토 I21b-01: 보안 한 장.
  if (path === "/security" && method === "GET") return json(await securityView(env, now));
  if (path === "/reviews" || path.startsWith("/reviews/")) {
    const r = await reviewRoute(request, env, url, path, method, now);
    if (r) return r;
  }
  if (path === "/incident" || path.startsWith("/incident/")) {
    const r = await incidentRoute(request, env, path, method, now, vitals);
    if (r) return r;
  }
  if (path === "/cockpit" && method === "GET") return json(await cockpit(env, now));
  m = path.match(/^\/story\/(d[A-Za-z0-9]{15})$/);
  if (m && method === "GET") {
    const s = await story(env, m[1]);
    return s ? json(s) : json({ error: "not_found" }, 404);
  }
  return null;
}

