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

/** 2.0.0 [RVN-24] 검토 SVC-105: '바꾼 것'·사고 타임라인이 읽는 활동 기록 이름. */
export const CHANGE_ACTIONS = ["shipped_version", "price_table", "launch_recorded", "auto_rest", "auto_resume", "release_start", "notice_remove", "rule_promote",
  "incident_open", "incident_close", "server_deployed", "share_deployed", "switch_on", "switch_off", "admin_update", "admin_remove", "change_note"];

/** 2.0.0 [RVN-24] 검토 SVC-105: 서버가 스스로 알 수 없는 바뀜을 관리자가 한 줄로 적어 둬요(판 건강 › 바꾼 것 › 적어 두기). */
export const CHANGE_KINDS = { site: "사이트 서버 배포", remote: "remote/ 파일 바꿈", pages: "공개 페이지 바꿈", company: "회사 서버 배포", share: "공유 서버 배포", other: "그 밖의 바뀜" };

/** 점검표 단계: key, 이름, 할 일, 저절로 알 수 있으면 auto, 열 곳. 순서가 곧 할 순서예요. */
export const RELEASE_STEPS = [
  // 2.0.0 [RVN-24] 검토 I22cc-03: Play 연결 열쇠가 있으면 매시 읽는 프로덕션 트랙(loop.js play_track)으로 저절로 체크하고 비율을 증거로 적어요.
  { key: "play", name: "Play 올림", what: "Play Console › 프로덕션에 새 판을 올리고 출시를 시작해요(단계적 출시면 비율도 적어 두기).", to: "#/loop", auto: true },
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
  return r ? runOf(r) : null;
}

function runOf(r) {
  let steps = {};
  try {
    steps = JSON.parse(r.steps ?? "{}") ?? {};
  } catch {
    steps = {};
  }
  return { version: r.version, startedAt: r.started_at, doneAt: r.done_at, by: r.by, steps };
}

/**
 * 저절로 알 수 있는 단계가 읽는 것: 저장된 판, 그 판에 묶인 문의, 재료·장터 대기, 공지. 2.0.0 [RVN-24] 검토 SVC-102·SVC-106: 점검표마다
 * 다시 읽지 않고(목록 12판 × 5~6번 → 한 번) 한 번 읽어 모든 열린 점검표가 같이 써요.
 */
async function autoShared(env) {
  const shipped = await shippedVersion(env);
  let waiting = null;
  try {
    waiting = shipped ? await shipWaiting(env, shipped.v) : [];
  } catch {
    waiting = null;
  }
  const [mats, market] = (await batch(env, [
    stmt(env, "SELECT state, COUNT(*) AS n FROM studio_materials GROUP BY state"),
    stmt(env, "SELECT COUNT(*) AS n, MIN(release_at) AS at FROM recipes WHERE state = 'waiting'"),
  ]).catch(() => [{ results: [] }, { results: [] }])).map((r) => r.results ?? []);
  const notices = await noticeRows(env).catch(() => []);
  // I22cc-03: 자동 순환이 매시 읽어 둔 Play 프로덕션 트랙(Play 연결 열쇠가 없으면 비어 있어요).
  let track = null;
  try {
    track = JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'play_track'"))?.value ?? "null");
  } catch {
    track = null;
  }
  return { shipped, waiting, mats, market, notices, track: track?.at ? track : null };
}

/** 2.0.0 [RVN-24] 검토 SVC-102: '공지' 단계는 업데이트 소식(바뀐 것 보기 단추)만 세요. 점검 안내·사고 공지로는 끝나지 않아요. */
export function isUpdateNotice(n) {
  return n?.action?.kind === "WHATS_NEW";
}

/**
 * 저절로 알 수 있는 단계의 지금 모습: done(끝), hint(보여 줄 숫자·말), ready(판 건강: 하루가 지나 볼 수 있음), bad(사람이 봐야 할 나빠짐 한 줄).
 * 2.0.0 [RVN-24] 검토 I22cc-03: Play 트랙과 판 건강 판정(매시 [releaseTick]이 적은 steps.health.seen·bad)도 여기서 읽어요.
 */
function autoState(v, run_, now, sh) {
  const { shipped } = sh;
  const out = {};
  const t = sh.track;
  if (!t) out.play = { done: false, hint: "Play 연결 열쇠가 없어 손으로 체크해요" };
  else {
    const at = (x) => Boolean(x && version(x) && version(v) && compareVersions(version(x), version(v)) >= 0);
    const pct = t.rolling?.fraction != null ? Math.round(t.rolling.fraction * 1000) / 10 : null;
    out.play = t.halted === v ? { done: false, hint: `Play에서 ${v} 출시를 멈췄어요`, bad: `Play에서 ${v} 출시가 멈춰 있어요` }
      : at(t.done) ? { done: true, hint: `Play 프로덕션 ${t.done} 100%` }
      : at(t.rolling?.v) ? { done: true, hint: `Play 단계적 출시 ${t.rolling.v}${pct != null ? ` ${pct}%` : ""}` }
      : { done: false, hint: `Play 프로덕션은 아직 ${t.done ?? "—"}${t.rolling?.v ? ` · 단계적 ${t.rolling.v}` : ""}` };
  }
  const shippedHere = Boolean(shipped && version(shipped.v) && compareVersions(version(shipped.v), version(v)) >= 0);
  out.ship = { done: shippedHere, hint: shipped ? `지금 저장된 판 ${shipped.v}` : "아직 저장한 판이 없어요" };
  if (sh.waiting === null) out.answers = { done: false, hint: "셀 수 없어요" };
  else {
    const waiting = shippedHere ? sh.waiting : [];
    out.answers = { done: shippedHere && waiting.length === 0, hint: shippedHere ? (waiting.length ? `답을 기다리는 문의 ${waiting.length}건` : "기다리는 문의 없음") : "나간 판을 저장하면 셀 수 있어요" };
  }
  const m = Object.fromEntries(sh.mats.map((x) => [x.state, x.n]));
  out.materials = { done: (m.waiting ?? 0) === 0 && ((m.ok ?? 0) + (m.hidden ?? 0)) > 0, hint: `공개 ${m.ok ?? 0} · 대기 ${m.waiting ?? 0} · 숨김 ${m.hidden ?? 0}` };
  const w = sh.market[0]?.n ?? 0;
  out.market = { done: w === 0 || Boolean(sh.market[0]?.at), hint: w ? `공개 대기 ${w}개${sh.market[0]?.at ? " · 예약됨" : ""}` : "공개 대기 없음" };
  const since = run_?.startedAt ?? now - 7 * DAY;
  const fresh = sh.notices.filter((x) => isUpdateNotice(x) && x.from >= since - DAY && x.until > now);
  out.notice = { done: fresh.length > 0, hint: fresh.length ? `업데이트 소식 ‘${String(fresh[0].title ?? "").slice(0, 40)}’${fresh.length > 1 ? ` 외 ${fresh.length - 1}개` : ""}` : "아직 업데이트 소식이 없어요(점검·사고 공지는 세지 않아요)" };
  const shipAt = shipped && shippedHere ? shipped.at : null;
  // SVC-102: 손으로 '했어요'를 눌러도 나간 지 하루가 지나기 전에 누른 것은 끝이 아니에요(그때는 단추도 서버도 받지 않아요).
  const ready = Boolean(shipAt && now - shipAt >= DAY);
  const h = run_?.steps?.health ?? {};
  const mine = h.doneAt;
  // I22cc-03: 하루가 지난 뒤 서버가 판 건강을 견줘 '나빠지지 않음'(seen.ok)이면 저절로 끝, 나빠지면(bad) 사람이 볼 단계로 남아요.
  const okSeen = Boolean(ready && h.seen?.ok && h.seen.at >= shipAt + DAY);
  const bad = ready && !mine && h.bad?.hint ? h.bad.hint : null;
  out.health = { ready, done: Boolean(ready && ((mine && mine >= shipAt + DAY) || okSeen)), ...(bad ? { bad } : {}),
    hint: shipAt ? (ready ? (okSeen ? h.seen.hint : bad ?? h.wait?.hint ?? "하루가 지났어요. 판 건강을 봐 주세요") : `나간 지 ${Math.floor((now - shipAt) / HOUR)}시간 · 24시간 뒤에 볼 수 있어요`) : "나간 판 저장 뒤 하루" };
  return out;
}

/** 2.0.0 [RVN-24] 검토 SVC-102: 끝난 점검표는 끝낸 순간의 자동 단계를 굳혀 둔 것(snap)을 보여 주고 다시 계산하지 않아요. */
function frozen(r) {
  return Object.fromEntries(RELEASE_STEPS.filter((s) => s.auto).map((s) => {
    const snap = r.steps[s.key]?.snap;
    // 굳혀 두기 전에 끝난 점검표(2.0.0 앞): 끝날 때 모두 끝이었으니 끝으로.
    return [s.key, snap ? { done: Boolean(snap.done), hint: snap.hint ?? "", ready: true } : { done: true, hint: "끝낸 뒤라 다시 세지 않아요", ready: true }];
  }));
}

/**
 * 2.0.0 [RVN-24] 검토 I22cc-03·U22c-04: 저절로 끝난 단계는 처음 끝을 본 때와 그때 본 것(증거)을 steps[key].seen에 굳혀 적어요. 굳힌 단계는 뒤에
 * 숫자가 바뀌어도(공지 기간이 끝남, 다음 판 재료가 대기로 들어옴) 이 판에서는 끝으로 남고, 화면은 '확인 시각 · 증거'를 보여 줘요. 판 건강은
 * [healthVerdict]가 따로 적어요. 바뀐 것이 있으면 참.
 */
function freezeSeen(r, auto, now) {
  let changed = false;
  for (const s of RELEASE_STEPS.filter((x) => x.auto && x.key !== "health")) {
    const a = auto[s.key];
    const cur = r.steps[s.key] ?? {};
    if (a?.done && !cur.seen && !cur.doneAt) {
      r.steps[s.key] = { ...cur, seen: { at: now, hint: String(a.hint ?? "").slice(0, 120) } };
      changed = true;
    }
  }
  return changed;
}

function viewOf(r, auto) {
  const steps = RELEASE_STEPS.map((s) => {
    const mine = r.steps[s.key] ?? {};
    const a = auto[s.key];
    // I22cc-03: 나빠짐(Play에서 멈춤, 판 건강이 앞 판보다 나쁨)은 사람이 '했어요'를 누를 때까지 끝이 아니에요.
    const bad = mine.doneAt ? null : a?.bad ?? null;
    const seen = s.key === "health" ? (a?.done && mine.seen?.ok ? mine.seen : null) : mine.seen ?? null;
    const done = s.key === "health" ? Boolean(a?.done) : Boolean(mine.doneAt) || (!bad && (Boolean(a?.done) || Boolean(seen)));
    return { key: s.key, name: s.name, what: s.what, to: s.to, auto: Boolean(s.auto), hint: a?.hint ?? "", done, skipped: Boolean(mine.skipped),
      doneAt: mine.doneAt ?? null, by: mine.by ?? "", note: mine.note ?? "", autoDone: done && !mine.doneAt,
      // U22c-04: 저절로 끝난 단계가 무엇을 보고 언제 체크됐는지.
      seenAt: done && !mine.doneAt ? seen?.at ?? null : null, evidence: done && !mine.doneAt ? seen?.hint ?? "" : "", bad,
      ...(s.key === "health" ? { ready: Boolean(a?.ready) } : {}) };
  });
  const left = steps.filter((s) => !s.done && !s.skipped).length;
  return { ...r, steps, left, bad: steps.filter((s) => s.bad && !s.skipped).length, next: steps.find((s) => !s.done && !s.skipped)?.key ?? null };
}

/**
 * 남은 일이 0이면 끝낸 때(done_at)를 적고 그 순간의 자동 단계를 굳혀(snap) 두고, 끝난 뒤 남은 일이 다시 생기면 지워요. [r.steps]를 고치고 새 done_at을
 * 돌려줘요. 사람이 단계를 누를 때(POST)와 매시 [releaseTick]이 같이 써요(2.0.0 [RVN-24] I22cc-03).
 */
function settle(r, live, now) {
  let doneAt = r.doneAt ?? null;
  if (live.left === 0 && !doneAt) {
    doneAt = now;
    for (const st of live.steps.filter((x) => x.auto)) r.steps[st.key] = { ...(r.steps[st.key] ?? {}), snap: { done: st.done, hint: st.hint } };
  } else if (live.left > 0 && doneAt) {
    doneAt = null;
    for (const k of Object.keys(r.steps)) if (r.steps[k]?.snap) { const { snap, ...rest } = r.steps[k]; r.steps[k] = rest; }
  }
  return doneAt;
}

/** 한 판의 점검표(없으면 null). [sh]는 목록이 한 번 읽어 넘겨요. */
export async function releaseView(env, v, now = Date.now(), sh = null) {
  const r = await readRun(env, v);
  return r ? viewFor(env, r, now, sh) : null;
}

async function viewFor(env, r, now, sh) {
  if (r.doneAt) return viewOf(r, frozen(r));
  return viewOf(r, autoState(r.version, r, now, sh ?? (await autoShared(env))));
}

/** 점검표 목록: 판이 높은 것 먼저. 2.0.0 [RVN-24] SVC-102·106: 점검표 줄은 한 번에, 자동 단계는 열린 점검표가 있을 때 한 번만 읽어요. */
export async function releaseList(env, now = Date.now()) {
  const rows = await all(env, "SELECT * FROM release_runs ORDER BY started_at DESC LIMIT 12");
  const runs = rows.map(runOf);
  const sh = runs.some((r) => !r.doneAt) ? await autoShared(env) : null;
  const list = [];
  for (const r of runs) list.push(await viewFor(env, r, now, sh));
  return { items: list, steps: RELEASE_STEPS.map((s) => ({ key: s.key, name: s.name })) };
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
    const sh = await autoShared(env);
    // 2.0.0 [RVN-24] 검토 SVC-102: 판 건강은 나간 판을 저장하고 하루가 지나야 '했어요'를 받아요.
    if (step.key === "health" && body.done === true && !autoState(r.version, r, now, sh).health.ready) return json({ error: "health_early", field: "step" }, 409);
    const cur = r.steps[step.key] ?? {};
    const next = { ...cur };
    if (body.done === true) Object.assign(next, { doneAt: now, by: actor, skipped: false });
    if (body.done === false) Object.assign(next, { doneAt: null, skipped: false });
    if (body.skip === true) Object.assign(next, { skipped: true, doneAt: null, by: actor });
    if (typeof body.note === "string") next.note = body.note.replace(/[\u0000-\u001f]/g, " ").slice(0, 300);
    r.steps[step.key] = next;
    // SVC-102: 끝났는지는 굳혀 둔 것이 아니라 지금 모습으로 다시 봐요. 끝나면 그 순간의 자동 단계를 굳혀 적고(snap), 되돌려 다시 남은 일이
    // 생기면 끝난 때(done_at)와 굳힌 것을 지워 '열린 점검표' 수에 다시 들어가요.
    const auto = autoState(r.version, r, now, sh);
    freezeSeen(r, auto, now);
    const live = viewOf({ ...r, doneAt: null }, auto);
    const doneAt = settle(r, live, now);
    await run(env, "UPDATE release_runs SET steps = ?, done_at = ? WHERE version = ?", JSON.stringify(r.steps), doneAt, m[1]);
    await audit(env, now, "release_step", m[1], `${step.name} ${body.done === true ? "끝" : body.skip === true ? "건너뜀" : body.done === false ? "되돌림" : "메모"}`);
    return json(doneAt ? viewOf({ ...r, doneAt }, frozen(r)) : live);
  }
  return null;
}

/**
 * 2.0.0 [RVN-24] 검토 I22cc-03: 나간 지 하루가 지난 판의 건강을 서버가 견줘 적어요. 앞 판과 같은 '나간 뒤 N일'끼리 견줄 휴대폰-날이 넉넉하면
 * 나빠지지 않음(seen.ok, 저절로 끝) 또는 나빠짐(bad, 사람이 볼 단계)으로 한 번 굳히고, 모자라면 까닭(wait)만 적고 다음 시간에 다시 봐요.
 * 사람이 이미 '했어요'를 눌렀거나 판정이 났으면 다시 보지 않아요. 바뀐 것이 있으면 참.
 */
async function healthVerdict(env, r, now, sh) {
  const h = r.steps.health ?? {};
  if (h.doneAt || h.skipped || h.seen || h.bad) return false;
  if (!autoState(r.version, r, now, sh).health.ready) return false;
  const res = await versionHealth(env, now, 14, null, { focus: r.version });
  const when = res.window ? `나간 뒤 ${res.window}일끼리` : `지난 ${res.days}일`;
  let next;
  if (res.latest === r.version && res.worse.length) next = { ...h, bad: { at: now, hint: res.worse.join(" · ").slice(0, 200) } };
  else if (res.latest === r.version && res.enough) next = { ...h, seen: { at: now, ok: true, hint: `${res.prev}보다 문제 신고·문의가 나빠지지 않았어요(${when})` } };
  else {
    const hint = res.latest !== r.version ? "이 판을 쓰는 휴대폰 숫자가 아직 없어 사람이 봐 주세요"
      : !res.prev ? "견줄 앞 판이 없어 사람이 봐 주세요" : `휴대폰-날이 판마다 ${HEALTH_MIN}보다 적어 숫자로 견줄 수 없어요. 사람이 봐 주세요`;
    if (h.wait?.hint === hint) return false;
    next = { ...h, wait: { at: now, hint } };
  }
  r.steps.health = next;
  return true;
}

/**
 * 2.0.0 [RVN-24] 검토 I22cc-03 '출시 점검표를 스스로 채우는 출시 열차': 매시 열린 점검표마다 저절로 알 수 있는 단계의 증거를 굳히고(Play 트랙
 * 판·비율, 업데이트 소식 제목, 재료·장터 수, 묶인 문의 수), 하루 뒤 판 건강을 견줘 적고, 남은 일이 0이면 점검표를 끝내요. 사람이 볼 것은
 * 손으로만 하는 단계(기능 일정 확인)와 '나빠짐'이 뜬 단계뿐이에요. 활동 기록의 누가는 비어(자동) 있어요.
 */
export async function releaseTick(env, now = Date.now()) {
  const rows = await all(env, "SELECT * FROM release_runs WHERE done_at IS NULL ORDER BY started_at DESC LIMIT 12");
  if (!rows.length) return { open: 0 };
  const sh = await autoShared(env);
  let finished = 0;
  let changed = 0;
  for (const row of rows) {
    const r = runOf(row);
    const judged = await healthVerdict(env, r, now, sh);
    const auto = autoState(r.version, r, now, sh);
    const froze = freezeSeen(r, auto, now);
    const live = viewOf({ ...r, doneAt: null }, auto);
    const doneAt = settle(r, live, now);
    if (!judged && !froze && !doneAt) continue;
    changed++;
    await run(env, "UPDATE release_runs SET steps = ?, done_at = ? WHERE version = ? AND done_at IS NULL", JSON.stringify(r.steps), doneAt, r.version);
    if (judged && r.steps.health?.bad) await audit(env, now, "release_step", r.version, `판 건강 나빠짐 · ${r.steps.health.bad.hint.slice(0, 80)}`);
    if (doneAt) {
      finished++;
      await audit(env, now, "release_step", r.version, "모든 단계 끝(저절로)");
    }
  }
  return { open: rows.length, changed, finished };
}

// ---- 판 건강 비교 [ADM3-15] ----


/** 2.0.0 [RVN-15] 검토 SVC-103: 판 건강에서 두 판을 견줄 때 판마다 휴대폰-날이 이만큼은 있어야 해요(30은 며칠 사이 몇 대로 흔들렸어요). */
export const HEALTH_MIN = 100;

/**
 * 판마다 지난 [days]일: 사용 통계를 보낸 휴대폰-날(활성), 문의·문제 신고(판별), 그 비율. 서버 오류는 판을 모르니 날짜로만, Play 비정상 종료·ANR은
 * 서버에 서비스 계정이 있으면 전체 값으로. 새 판을 그 앞 판과 견줘 나빠지면 worse.
 * 2.0.0 [RVN-15] 검토 SVC-103: '새 판'은 가장 높은 판 번호가 아니라 [focus](단계적 출시 중인 판, 자동 순환이 줘요) 또는 서버가 아는 나간 판이에요.
 * 그보다 높은 판(시험판)은 표에만 있고 견주지 않아요. 두 판은 같은 '나간 뒤 N일'(N은 새 판이 나간 날수, 최대 [days]) 구간끼리 견줘요. 나간 때를
 * 모르는 판은 지난 [days]일 전체로.
 */
export async function versionHealth(env, now = Date.now(), days = 14, vitals = null, { focus = null } = {}) {
  const from = now - days * DAY;
  const far = now - 60 * DAY;
  const [usage, inq, errors, grouped, usageDays, inqTimes, ships] = (await batch(env, [
    stmt(env, "SELECT app, SUM(phones) AS phones, COUNT(DISTINCT day) AS days FROM usage_days WHERE day >= ? GROUP BY app", kstDay(from)),
    stmt(env, `SELECT json_extract(info, '$.app') AS app, kind, COUNT(*) AS n FROM inquiries WHERE created_at >= ? GROUP BY app, kind`, from),
    stmt(env, "SELECT key, count FROM counters WHERE key LIKE 'errors|%' AND key >= ?", `errors|${kstDay(from)}`),
    // 1.39 [ADM3-43] 같은 날들 가운데 오류 묶음에 담긴 수(1.38부터). 다르면 화면이 '묶음에 N'으로 함께 보여요.
    stmt(env, "SELECT COALESCE(SUM(count), 0) AS n FROM error_days WHERE day >= ?", kstDay(from)),
    stmt(env, "SELECT app, day, SUM(phones) AS phones FROM usage_days WHERE day >= ? GROUP BY app, day", kstDay(far)),
    stmt(env, `SELECT json_extract(info, '$.app') AS app, kind, created_at AS at FROM inquiries WHERE created_at >= ? LIMIT 5000`, far),
    stmt(env, "SELECT target AS v, MIN(at) AS at FROM audit WHERE action = 'shipped_version' GROUP BY target"),
  ])).map((r) => r.results ?? []);
  const shipped = focus ? null : await shippedVersion(env).catch(() => null);
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
  const top = version(focus ?? shipped?.v ?? "");
  const cmp = top ? known.filter((r) => compareVersions(version(r.app), top) <= 0) : known;
  const [newest, older] = top && cmp[0] && compareVersions(version(cmp[0].app), top) !== 0 ? [null, null] : cmp;
  // 같은 '나간 뒤 N일' 구간: 판마다 나간 때(나간 판 저장 기록, 자동 순환의 출시 감지도 같은 기록을 남겨요).
  const shipAt = new Map(ships.map((x) => [String(x.v), Number(x.at)]));
  if (shipped && !shipAt.has(shipped.v) && shipped.at) shipAt.set(shipped.v, Number(shipped.at));
  const n = newest && shipAt.get(newest.app) ? Math.max(1, Math.min(days, Math.floor((now - shipAt.get(newest.app)) / DAY))) : null;
  const span = (r) => {
    const at = n ? shipAt.get(r.app) : null;
    if (!at) return r;
    const d0 = kstDay(at);
    const d1 = kstDay(at + n * DAY);
    const phoneDays = usageDays.filter((u) => String(u.app ?? "").split(" ")[0] === r.app && u.day >= d0 && u.day < d1).reduce((a, u) => a + (u.phones ?? 0), 0);
    const mine = inqTimes.filter((q) => String(q.app ?? "").split(" ")[0] === r.app && q.at >= at && q.at < at + n * DAY);
    const bugs = mine.filter((q) => q.kind === "bug" || q.kind === "problem").length;
    return { ...r, phoneDays, inquiries: mine.length, bugs, bugRate: phoneDays ? Math.round((bugs / phoneDays) * 100000) / 100 : null,
      inquiryRate: phoneDays ? Math.round((mine.length / phoneDays) * 100000) / 100 : null };
  };
  const latest = newest ? span(newest) : null;
  const prev = older && n && shipAt.get(older.app) ? span(older) : older;
  const worse = [];
  if (latest && prev && latest.phoneDays >= HEALTH_MIN && prev.phoneDays >= HEALTH_MIN) {
    const when = n && prev !== older ? ` · 나간 뒤 ${n}일끼리` : "";
    if ((latest.bugRate ?? 0) > (prev.bugRate ?? 0) * 1.5 && latest.bugs >= 3) worse.push(`문제 신고가 ${prev.app}보다 많아요(휴대폰-날 1000당 ${latest.bugRate} 대 ${prev.bugRate}${when})`);
    if ((latest.inquiryRate ?? 0) > (prev.inquiryRate ?? 0) * 1.5 && latest.inquiries >= 5) worse.push(`문의가 ${prev.app}보다 많아요${when ? `(${when.slice(3)})` : ""}`);
  }
  const serverErrors = errors.reduce((n, e) => n + (e.count ?? 0), 0);
  // I22cc-03: 두 판 모두 견줄 만큼 숫자가 있었는지(점검표가 '나빠지지 않음'을 적을 수 있는지).
  const enough = Boolean(latest && prev && latest.phoneDays >= HEALTH_MIN && prev.phoneDays >= HEALTH_MIN);
  return { days, versions: [...known, ...list.filter((r) => !version(r.app))], latest: latest?.app ?? null, prev: prev?.app ?? null, worse, enough, serverErrors, window: n,
    groupedErrors: grouped[0]?.n ?? 0, vitals };
}

// ---- 바뀐 일 [ADM3-33] ----

/** 그래프에 찍을 '이날 바꾼 것': 나간 판, 기능 켬·끔, 공지, 가격표, 처리방침, 쉬기. [from]~[to] ms. */
export async function changeMarks(env, from, to) {
  // 2.0.0 [RVN-24] 검토 SVC-105: 이름이 실제로 적히는 것과 같아야 해요. flag_force·flag_policy는 아무도 적지 않는 이름이라 지웠어요(기능 강제·처리방침
  // 날짜는 flag_history로 와요). 스위치 끄고 켜기, 관리자 권한, 공유 서버 배포(매시 판을 보고 적음), 손으로 적어 둔 바뀜(사이트 서버·remote/ 파일)을 더해요.
  const [auditRows, flagRows] = (await batch(env, [
    stmt(env, `SELECT at, action, target, detail FROM audit WHERE at >= ? AND at < ? AND action IN (${CHANGE_ACTIONS.map(() => "?").join(", ")}) ORDER BY at DESC LIMIT 400`,
      from, to, ...CHANGE_ACTIONS),
    // SVC-105: 긴 기간이면 최근 것부터 200(전에는 앞에서 200이라 최근 바뀜이 잘렸어요).
    stmt(env, "SELECT at, key, action FROM flag_history WHERE at >= ? AND at < ? ORDER BY at DESC LIMIT 200", from, to),
  ]).catch(() => [{ results: [] }, { results: [] }])).map((r) => r.results ?? []);
  const words = { shipped_version: "판 나감", price_table: "가격표", launch_recorded: "정식 출시", auto_rest: "D1 쉬기", auto_resume: "쉬기 끝", release_start: "출시 점검표",
    notice_remove: "공지 내림", rule_promote: "규칙 올림", incident_open: "사고 시작", incident_close: "사고 끝",
    // 1.39 [STAT2-05] 서버가 새 판으로 처음 뜬 때(사고 때 가장 먼저 의심할 일).
    server_deployed: "서버 배포", share_deployed: "공유 서버 배포", switch_off: "기능 끔", switch_on: "기능 켬", admin_update: "관리자 권한 바꿈",
    admin_remove: "관리자 지움", change_note: "" };
  // 2.0.0 [RVN-02] 사고의 목표는 안쪽 번호라, 사고 줄은 번호 대신 무엇이 보였는지(신호·한 일) 앞 40자를 붙여요.
  // SVC-105: 관리자 줄은 메일 주소(target) 대신 무엇을 바꿨는지만, 스위치는 이름(detail), 적어 둔 바뀜은 종류와 한 줄.
  const what = (r) => r.action.startsWith("incident_") ? `· ${[...String(r.detail ?? "")].slice(0, 40).join("")}`
    : r.action === "admin_update" ? `· ${String(r.detail ?? "").slice(0, 40)}` : r.action === "admin_remove" ? ""
    : r.action.startsWith("switch_") ? String(r.detail ?? r.target ?? "").split(" · ")[0]
    : r.action === "change_note" ? `${CHANGE_KINDS[r.target] ?? "바뀜"} · ${String(r.detail ?? "").slice(0, 60)}` : r.target ?? "";
  const marks = auditRows.map((r) => ({ at: r.at, day: kstDay(r.at), kind: r.action, text: `${words[r.action] ?? r.action} ${what(r)}`.trim() }));
  // SVC-105: 스위치 끄고 켜기는 기능 강제(flag_history)도 같은 때 적으니, 그 줄은 사람이 읽는 스위치 줄 하나만 남겨요.
  const switched = new Set(auditRows.filter((r) => r.action.startsWith("switch_")).map((r) => r.at));
  for (const f of flagRows) if (!switched.has(f.at)) marks.push({ at: f.at, day: kstDay(f.at), kind: "flag", text: `기능 ${f.key} ${f.action}` });
  const notices = await noticeRows(env).catch(() => []);
  for (const n of notices) if (n.from >= from && n.from < to) marks.push({ at: n.from, day: kstDay(n.from), kind: "notice", text: `공지 ${String(n.title ?? "").slice(0, 20)}` });
  return marks.sort((a, b) => a.at - b.at).slice(-200);
}
