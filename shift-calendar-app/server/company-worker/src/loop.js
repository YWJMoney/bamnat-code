import { all, audit, batch, deviceHash, deviceId, iso, json, notifyAdmins, one, overLimit, readJson, refuse, run, stmt, remoteState, APP_CODES } from "./index.js";
import { DAY, HOUR } from "./units.js";
import { PLAY, playReady, playToken, rewardRules, REWARD_RULES, REWARD_MOST } from "./money.js";
import { DEV, actorName, cut, devEvent, devUpdate, forgetInquiries, plainText, inquiryText, INQUIRY, BILLING_RECORD } from "./inquiries.js";
import { compareVersions, inboxAnswer, version } from "./inbox.js";
import { DESK, isOut, markTold, shipNow, shippedVersion, shipText } from "./desk.js";
import { versionHealth } from "./release-desk.js";
import { flagStore, serverEval } from "./flags.js";
import { kstDay } from "./usage.js";
import { SERVER_BUILT } from "./dev-built.js";

// ---- 1.39 [LOOP] 문의 → 개발 → 출시 → 답이 스스로 도는 바퀴 (사용자 2026-11-07, 계획 docs/plans/1.39-G10-자동순환.md) ----
//
// 1.38까지는 관리자가 '나간 판 저장'을 눌러야 묶인 문의에 답이 갔어요. 1.39부터는
//   - 서버가 Play 프로덕션 트랙을 매시 읽어, 판이 100% 나가면 1시간 보류 뒤 스스로 '나간 판 저장'을 해요 [LOOP-01][LOOP-02].
//     단계적 출시 중 새 판이 앞 판보다 나빠지면 '출시 멈춤 권장'을 알려요 [LOOP-03]. 셋 다 끌 수 있어요 [LOOP-04].
//   - 저장소의 ops/dev-status.jsonl(문의 글 없이 개발 상태만)이 main에 합쳐지면 GitHub Action이 좁은 '동기화 열쇠'로 보내요 [LOOP-05].
//     고친 판은 그 커밋의 versionName이에요. 앱 업데이트 없이 고친 것(서버·원격 자료·기능 스위치)은 실제로 닿은 때 바로 답해요 [LOOP-06].
//   - 앱이 문의를 받아 올 때 판을 보내면, 고친 판으로 연 그 폰에만 바로 안내하고 [LOOP-15] '해결됐나요'를 물어요 [LOOP-16].
//   - 알려진 문제판과 '나도 겪어요' [LOOP-17], 영향도 점수 [LOOP-08], 결정함(최고 관리자 확인·요금) [LOOP-09][LOOP-10],
//     요금 판단 관측치 [LOOP-11], 믿을수록 더 맡기기 [LOOP-12], 쓰면 쌓이는 날 세분 [LOOP-14], 정기 출시 열차 [LOOP-07].
// 문의 원문·사진·폰 정보는 서버 밖으로 나가지 않아요(처리방침 5항). 저장소에는 d 번호·추적 번호·Claude가 쓴 요약만.

export const LOOP = {
  /** 100% 출시를 본 뒤 자동 안내까지 기다리는 시간(그 사이 취소·지금 보내기). */
  shipHold: HOUR,
  /** 정기 앱 업데이트 주기(사용자 2026-11-07: 2주). */
  trainDays: 14,
  /** 알려진 문제판: 공개 제목이 있고 묶인 문의·나도 겪어요가 이만큼이면 보여요(한 사람만의 일은 안 보이게). */
  knownMin: 2,
  knownMax: 12,
  /** 고친 뒤 알려진 문제판에 '고쳤어요'로 남는 날. */
  knownFixedDays: 14,
  knownTitle: 60,
  /** 한 폰이 하루에 누를 수 있는 '나도 겪어요'. */
  knownPerDay: 10,
  /** 믿을수록 더 맡기기: 한 종류에서 연속으로 손대지 않고 나간 Claude 답이 이만큼이면 '바로 보내기'를 제안해요. */
  streak: 20,
  /** 요금: 사람(최고 관리자)만 정해요. later = 더 논의. */
  tiers: { free: "무료", pro: "프로", pass: "묶음 7일권(광고로 열기)", trial: "처음 며칠 무료 뒤 프로", later: "더 논의" },
  sizes: { fix: "고침", polish: "다듬기", feature: "새 기능" },
  routes: { server: "서버", remote: "원격 자료", flag: "기능 스위치", app: "정기 앱 업데이트", hotfix: "긴급 앱 업데이트" },
  piles: { fixed: "이미 고침", known: "개발 목록에 있음", answered: "전에 답한 문의와 같음", faq: "자주 쓰는 답", declined: "반려된 요청", again: "고친 뒤 다시 생김", new: "새 문제" },
  syncItems: 200,
};

export const LOOP_SCHEMA = [
  // 판마다 자동 출시 안내 하나: 기다림(waiting) → 보냄(done) | 취소(cancelled).
  `CREATE TABLE IF NOT EXISTS loop_ships (version TEXT PRIMARY KEY, seen_at INTEGER NOT NULL, until INTEGER NOT NULL, status TEXT NOT NULL,
    by TEXT NOT NULL DEFAULT '', done_at INTEGER, result TEXT NOT NULL DEFAULT '')`,
  // 저장소 동기화 기록.
  `CREATE TABLE IF NOT EXISTS loop_syncs (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, sha TEXT NOT NULL DEFAULT '', version TEXT NOT NULL DEFAULT '',
    pr INTEGER, n INTEGER NOT NULL DEFAULT 0, result TEXT NOT NULL DEFAULT '')`,
  // 결정함의 Claude 초안(최고 관리자 확인 문의).
  `CREATE TABLE IF NOT EXISTS loop_drafts (key TEXT PRIMARY KEY, text TEXT NOT NULL, why TEXT NOT NULL DEFAULT '', by TEXT NOT NULL, at INTEGER NOT NULL)`,
  // Claude 답 보류가 어떻게 끝났는지(종류마다): sent·edited·cancelled. 믿을수록 더 맡기기의 숫자.
  `CREATE TABLE IF NOT EXISTS loop_held_log (id INTEGER PRIMARY KEY AUTOINCREMENT, pile TEXT NOT NULL DEFAULT '', outcome TEXT NOT NULL, at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS loop_held_log_pile ON loop_held_log (pile, id)",
];

export const LOOP_COLUMNS = [
  "ALTER TABLE dev_items ADD COLUMN track TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN size TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN route TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN remote TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN flag TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN events TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN tier TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN tier_by TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN tier_at INTEGER",
  "ALTER TABLE dev_items ADD COLUMN tier_note TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN tier_ask TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN tier_why TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN public_title TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN known INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE dev_items ADD COLUMN pr INTEGER",
  "ALTER TABLE dev_items ADD COLUMN sha TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN test TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN check_text TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN live_at INTEGER",
  "ALTER TABLE inquiries ADD COLUMN fixed INTEGER",
  "ALTER TABLE inquiries ADD COLUMN fixed_at INTEGER",
  "ALTER TABLE inquiries ADD COLUMN via TEXT",
  "ALTER TABLE held_replies ADD COLUMN pile TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE held_replies ADD COLUMN edited INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE admin_keys ADD COLUMN scope TEXT NOT NULL DEFAULT 'ops'",
];

// ---- 설정 ----

/** 매시·5분 작업과 앱에서 온 일: 기록(audit·개발사항 이야기)에 '자동'·[name]으로 남게. 답하기(answer)에는 원래 env를 써요. */
const loopActor = (env, name = "자동") => (env.actor ? env : Object.assign(Object.create(env), { actor: { id: "", name, owner: false, perms: {}, auto: true } }));
/** 사람·열쇠가 없는 자리의 답: 1.35 자동 대응처럼 '자동 정리'로(앱에는 보통 답으로 보여요). */
export const autoReply = (env, id, text, now) => inboxAnswer(env, id, text, now, "자동 정리");
/** 답 하나가 실패해도(하루 한도 등) 바퀴는 계속 돌아요. */
const tryAnswer = async (answer, env, id, text, now) => {
  try {
    return await answer(env, id, text, now);
  } catch {
    return false;
  }
};

const readSetting = async (env, key, fallback) => {
  const row = await one(env, "SELECT value FROM settings WHERE key = ?", key).catch(() => null);
  if (!row) return fallback;
  try {
    return { ...fallback, ...JSON.parse(row.value) };
  } catch {
    return fallback;
  }
};
const saveSetting = (env, key, value) => run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", key, JSON.stringify(value));

/** [LOOP-04] 자동 멈춤 스위치: 처음엔 모두 켜짐(서비스 계정·열쇠가 없으면 어차피 아무것도 안 해요). */
export const SWITCH_DEFAULT = { autoShip: true, sync: true, phone: true, halt: true, instant: [], urgentMin: 0, flexibleMin: 0 };
export const loopSwitches = (env) => readSetting(env, "loop_switches", SWITCH_DEFAULT);
/** [LOOP-07] 정기 출시 열차: 첫 출발일과 주기. */
export const loopTrain = (env) => readSetting(env, "loop_train", { start: "2026-11-20", days: LOOP.trainDays });

/** 다음 정기 출발일(KST 'YYYY-MM-DD'): [start]부터 [days]마다, 오늘 이후 가장 가까운 날. */
export function nextTrain(train, now) {
  const start = Date.parse(`${train.start}T00:00:00+09:00`);
  const days = Math.max(1, Number(train.days) || LOOP.trainDays);
  if (!Number.isFinite(start)) return null;
  const today = Date.parse(`${kstDay(now)}T00:00:00+09:00`);
  if (today <= start) return train.start;
  const n = Math.ceil((today - start) / (days * DAY));
  return kstDay(start + n * days * DAY);
}
const dayWords = (d) => (d ? `${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일` : "");

// ---- [LOOP-01][LOOP-02][LOOP-03] Play 트랙과 자동 출시 안내 ----

/** 'NN (1.39.0)'·'1.39.0'·versionCode에서 versionName. */
export function releaseName(rel) {
  const m = String(rel?.name ?? "").match(/(\d{1,2}\.\d{1,2}\.\d{1,3})/);
  if (m) return m[1];
  const code = Math.max(0, ...(rel?.versionCodes ?? []).map(Number).filter(Number.isFinite));
  const hit = Object.entries(APP_CODES).find(([, c]) => c === code);
  return hit ? hit[0] : null;
}

/** 프로덕션 트랙 읽기: 수정(edit)을 열고 트랙만 읽은 뒤 닫아요. 아무것도 바꾸지 않아요. */
export async function readProduction(env, now) {
  const account = playReady(env);
  if (!account) return null;
  const pkg = encodeURIComponent(env.PLAY_PACKAGE || "com.bamnat.app");
  const auth = { authorization: `Bearer ${await playToken(env, account, now, PLAY.scope)}` };
  const opened = await fetch(`${PLAY.api}/${pkg}/edits`, { method: "POST", headers: { ...auth, "content-type": "application/json" }, body: "{}", signal: AbortSignal.timeout(8000) });
  if (opened.status === 403) throw new Error("play_denied: Play Console › 사용자 및 권한에서 서비스 계정에 '앱 정보 보기'를 주세요");
  if (!opened.ok) throw new Error(`play_edit ${opened.status}`);
  const edit = (await opened.json()).id;
  try {
    const res = await fetch(`${PLAY.api}/${pkg}/edits/${encodeURIComponent(edit)}/tracks/production`, { headers: auth, signal: AbortSignal.timeout(8000) });
    if (!res.ok) throw new Error(`play_track ${res.status}`);
    const track = await res.json();
    const releases = (track.releases ?? []).map((r) => ({ v: releaseName(r), status: r.status, fraction: r.userFraction ?? (r.status === "completed" ? 1 : null),
      codes: (r.versionCodes ?? []).map(Number) }));
    const done = releases.filter((r) => r.status === "completed" && version(r.v)).sort((a, b) => compareVersions(version(b.v), version(a.v)))[0] ?? null;
    const rolling = releases.find((r) => r.status === "inProgress" && version(r.v)) ?? null;
    const halted = releases.find((r) => r.status === "halted" && version(r.v)) ?? null;
    return { done: done?.v ?? null, rolling: rolling ? { v: rolling.v, fraction: rolling.fraction } : null, halted: halted?.v ?? null, at: now };
  } finally {
    await fetch(`${PLAY.api}/${pkg}/edits/${encodeURIComponent(edit)}`, { method: "DELETE", headers: auth, signal: AbortSignal.timeout(8000) }).catch(() => null);
  }
}

/** 매시: 트랙을 읽어 남기고, 100% 나간 새 판이면 1시간 보류 안내를 세우고, 단계적 출시가 나빠지면 알려요. */
export async function playTick(env, now = Date.now()) {
  env = loopActor(env);
  const sw = await loopSwitches(env);
  const track = await readProduction(env, now);
  if (!track) return { skipped: "no_play" };
  await saveSetting(env, "play_track", track);
  const shipped = await shippedVersion(env);
  let held = null;
  if (track.done && sw.autoShip && (!shipped || compareVersions(version(track.done), version(shipped.v)) > 0)) {
    const r = await run(env, "INSERT OR IGNORE INTO loop_ships (version, seen_at, until, status) VALUES (?, ?, ?, 'waiting')", track.done, now, now + LOOP.shipHold);
    if (r.meta.changes) {
      held = track.done;
      const dry = await shipNow(env, track.done, { dry: true }, now);
      await audit(env, now, "loop_ship_seen", track.done, `문의 ${dry.answers}건 · 공지 ${dry.notice ? 1 : 0}`);
      await notifyAdmins(env, "inquiry", { title: `${track.done} 출시를 확인했어요`,
        body: `1시간 뒤 문의 ${dry.answers}건 안내${dry.notice ? "와 업데이트 소식 공지" : ""}가 나가요. 멈추려면 눌러 주세요`, url: "/admin/#/flow" }, now, { ignoreQuiet: true });
    }
  }
  let advice = null;
  if (track.rolling && sw.halt) {
    const h = await versionHealth(env, now, 14);
    if (h.latest === track.rolling.v && h.worse.length) {
      const said = await one(env, "SELECT id FROM audit WHERE action = 'loop_halt_advice' AND target = ?", track.rolling.v);
      if (!said) {
        advice = track.rolling.v;
        await audit(env, now, "loop_halt_advice", track.rolling.v, h.worse.join(" · "));
        await notifyAdmins(env, "incident", { title: `${track.rolling.v} 출시 멈춤을 생각해 보세요`,
          body: `${h.worse[0]}. Play Console에서 단계적 출시를 멈출지 정해 주세요`, url: "/admin/#/health", urgent: true }, now, { ignoreQuiet: true });
      }
    }
  }
  return { track, held, advice };
}

/** 5분마다·매시: 보류가 끝난 출시 안내를 보내요. */
export async function shipTick(env, now = Date.now(), answer) {
  const rows = await all(env, "SELECT * FROM loop_ships WHERE status = 'waiting' AND until <= ?", now).catch(() => []);
  const out = [];
  for (const r of rows) out.push(await sendShip(env, r.version, now, answer ?? autoReply, "자동"));
  return out;
}

async function sendShip(env, v, now, answer = autoReply, by) {
  const shipped = await shippedVersion(env);
  const fresh = !shipped || compareVersions(version(v), version(shipped.v)) > 0;
  const r = fresh ? await shipNow(env, v, { notify: true, notice: true, by: `${by} 출시 안내` }, now, answer) : { answered: 0, notice: null };
  const result = fresh ? `문의 ${r.answered}건 안내${r.notice ? " · 공지" : ""}` : "이미 저장된 판";
  await run(env, "UPDATE loop_ships SET status = 'done', done_at = ?, by = ?, result = ? WHERE version = ?", now, by, result, v);
  await audit(env, now, "loop_ship_sent", v, `${by} · ${result}`);
  return { version: v, ...r };
}

// ---- [LOOP-06] 앱 업데이트 없이 고친 것: 실제로 닿은 때 ----

const remoteWant = (s) => {
  const m = String(s ?? "").match(/^([a-z0-9-]{2,30})@(\d{1,9})$/);
  return m ? { kind: m[1], version: Number(m[2]) } : null;
};

/** 완료됐지만 아직 닿지 않은 서버·원격 자료·스위치 개발사항이 지금 닿았는지 보고, 닿았으면 묶인 문의에 바로 답해요. */
export async function liveTick(env, now = Date.now(), answer = autoReply) {
  const rows = await all(env, `SELECT * FROM dev_items WHERE deleted_at IS NULL AND status = 'done' AND live_at IS NULL AND route IN ('server', 'remote', 'flag')`).catch(() => []);
  if (!rows.length) return { live: 0 };
  let remote = null;
  let store = null;
  let live = 0;
  let told = 0;
  for (const d of rows) {
    let ok = false;
    if (d.route === "server") ok = SERVER_BUILT.includes(d.id) || (d.track && SERVER_BUILT.includes(d.track));
    else if (d.route === "remote") {
      const want = remoteWant(d.remote);
      remote ??= await remoteState(env, now, true).catch(() => null);
      const f = want && remote?.files?.find((x) => x.kind === want.kind);
      ok = Boolean(f && f.valid && Number(f.version) >= want.version);
    } else if (d.route === "flag" && d.flag) {
      store ??= await flagStore(env);
      ok = serverEval(store, d.flag, now).on === true;
    }
    if (!ok) continue;
    await batch(env, [stmt(env, "UPDATE dev_items SET live_at = ?, updated_at = ? WHERE id = ?", now, Math.max(now, d.updated_at + 1), d.id),
      devEvent(loopActor(env), d.id, now, "live", { route: d.route })]);
    live++;
    told += await tellLinked(env, d, now, answer, liveText(d));
  }
  if (live) await audit(loopActor(env), now, "loop_live", `${live}개`, `문의 ${told}건`);
  return { live, told };
}

const liveText = (d) => d.ship_reply || "기다려 주셔서 고마워요. 말씀해 주신 내용을 고쳐 두었고, 앱 업데이트 없이 지금 바로 적용됐어요. 그대로 써 보시고 해결됐는지 알려 주세요.";

/** 묶여 아직 안내를 받지 않은 열린 문의에 [text]를 보내고 '알림 보냄'으로 적어요. */
async function tellLinked(env, d, now, answer, text) {
  const rows = await all(env, `SELECT l.inquiry_id FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ? AND l.told_at IS NULL AND i.status != 'closed'`, d.id);
  let n = 0;
  const told = [];
  for (const r of rows) if (await tryAnswer(answer, env, r.inquiry_id, text, now)) {
    n++;
    told.push(r.inquiry_id);
  }
  await markTold(env, d.id, told, now);
  return n;
}

// ---- [LOOP-15] 고친 판으로 연 그 폰에만 ----

/** 앱이 내 문의를 받아 올 때(x-app: "1.39.0 (53)"): 이 폰이 이미 고친 판이면 묶인 문의에 바로 안내해요. */
export async function phoneArrived(env, dev, appHeader, now, answer = autoReply) {
  const app = version(String(appHeader ?? "").split(" ")[0]);
  if (!app) return 0;
  const sw = await loopSwitches(env);
  if (!sw.phone) return 0;
  const rows = await all(env, `SELECT i.id AS inquiry, d.id AS item, d.released_in, d.ship_reply FROM inquiries i JOIN dev_links l ON l.inquiry_id = i.id AND l.told_at IS NULL
    JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL AND d.status = 'done' AND d.released_in IS NOT NULL
    WHERE i.device = ? AND i.status != 'closed' LIMIT 20`, dev).catch(() => []);
  let n = 0;
  for (const r of rows) {
    const fixed = version(r.released_in);
    if (!fixed || compareVersions(app, fixed) < 0) continue;
    const text = r.ship_reply || `기다려 주셔서 정말 고마워요. 지금 쓰시는 ${r.released_in} 버전에 말씀해 주신 내용을 고쳐 두었어요. 써 보시고 해결됐는지 아래에서 알려 주세요.`;
    if (!(await tryAnswer(answer, env, r.inquiry, text, now))) continue;
    n++;
    await markTold(env, r.item, [r.inquiry], now);
    await batch(env, [devEvent(loopActor(env), r.item, now, "phone_told", { inquiry: r.inquiry, app: app.join(".") })]).catch(() => null);
  }
  return n;
}

// ---- [LOOP-16] 해결됐나요 ----

/** 문의한 분이 '해결됐어요'(ok true)·'아직이에요'(false)를 누를 수 있는지: 묶인 개발사항이 끝났고 안내를 받았을 때. */
export async function fixedAsk(env, inquiryId) {
  const r = await one(env, `SELECT COUNT(*) AS n FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL
    WHERE l.inquiry_id = ? AND l.told_at IS NOT NULL`, inquiryId).catch(() => null);
  return (r?.n ?? 0) > 0;
}

export async function fixedAnswer(request, env, row, now) {
  env = loopActor(env, "문의한 분");
  const body = await readJson(request, 512);
  if (body.ok !== true && body.ok !== false) refuse("ok", "ok");
  if (!(await fixedAsk(env, row.id))) return json({ error: "not_fixed" }, 409);
  const items = await all(env, `SELECT d.* FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL WHERE l.inquiry_id = ? AND l.told_at IS NOT NULL`, row.id);
  const out = [stmt(env, "UPDATE inquiries SET fixed = ?, fixed_at = ? WHERE id = ?", body.ok ? 1 : -1, now, row.id)];
  for (const d of items) out.push(devEvent(env, d.id, now, body.ok ? "fixed_yes" : "fixed_no", { inquiry: row.id }));
  if (!body.ok) {
    // 다시 생김: 문의는 다시 기다림, 개발사항은 접수로(다음 바퀴 맨 앞). 판·안내 기록은 그대로 남겨요.
    out.push(stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)", row.id,
      "고친 판으로 써 봤는데 아직 해결되지 않았어요. (앱에서 '아직이에요'를 눌렀어요)", now),
      stmt(env, "UPDATE inquiries SET status = 'open', updated_at = ? WHERE id = ?", now, row.id));
    for (const d of items.filter((x) => x.status === "done")) {
      out.push(stmt(env, "UPDATE dev_items SET status = 'todo', reason = '', live_at = NULL, handed_at = NULL, updated_at = ? WHERE id = ?", Math.max(now, d.updated_at + 1), d.id),
        devEvent(env, d.id, now, "status", { status: ["done", "todo"], why: "문의한 분이 아직 해결되지 않았다고 알림" }));
    }
  }
  await batch(env, out);
  await audit(env, now, body.ok ? "loop_fixed_yes" : "loop_fixed_no", row.id, items.map((d) => d.id).join(" "));
  if (!body.ok) await notifyAdmins(env, "urgent", { title: "고친 뒤에도 그대로라고 해요", body: items[0]?.title ?? "개발사항이 다시 열렸어요", url: "/admin/#/desk?view=claude" }, now);
  return null;
}

// ---- [LOOP-17] 알려진 문제판 ----

const knownCache = new WeakMap();

/** 앱 문의 화면 위 '지금 알려진 문제': 공개 제목이 있는 개발사항 중 여럿이 겪는 것, 고친 뒤 14일까지. */
export async function knownList(env, dev, now) {
  let base = knownCache.get(env.DB ?? env);
  if (!base || base.until <= now) {
    const shipped = await shippedVersion(env);
    const rows = await all(env, `SELECT d.id, d.public_title, d.status, d.released_in, d.live_at, d.known, d.updated_at,
        (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks,
        (SELECT COALESCE(SUM(i.same), 0) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id) AS same
      FROM dev_items d WHERE d.deleted_at IS NULL AND d.public_title != '' AND d.known >= 0 AND d.status IN ('todo', 'doing', 'done')
      ORDER BY d.updated_at DESC LIMIT 200`).catch(() => []);
    const items = [];
    for (const d of rows) {
      if (d.known !== 1 && d.asks + d.same < LOOP.knownMin) continue;
      const out = d.status === "done" && (d.live_at || (d.released_in && isOut(d.released_in, shipped)));
      if (out && now - d.updated_at > LOOP.knownFixedDays * DAY) continue;
      const state = d.status === "todo" ? "checking" : d.status === "doing" || !out ? "fixing" : "fixed";
      items.push({ id: d.id, title: d.public_title, state, version: state === "fixed" ? d.released_in ?? null : null, me: d.asks + d.same });
      if (items.length >= LOOP.knownMax) break;
    }
    base = { items, until: now + 60 * 1000 };
    knownCache.set(env.DB ?? env, base);
  }
  const mine = dev ? new Set((await all(env, `SELECT l.item_id FROM inquiries i JOIN dev_links l ON l.inquiry_id = i.id WHERE i.device = ? AND i.via = 'known'`, dev).catch(() => []))
    .map((r) => r.item_id)) : new Set();
  return { items: base.items.map((x) => ({ ...x, mine: mine.has(x.id) })) };
}

/** '나도 겪어요': 그 개발사항에 묶인 짧은 문의 하나로(다른 문의와 같이 고친 판 안내·해결됐나요를 받아요). on: false면 그 문의를 지워요. */
export async function knownMe(request, env, dev, id, now) {
  const body = await readJson(request, 1024);
  const d = await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL AND public_title != '' AND known >= 0", id);
  if (!d) return json({ error: "not_found" }, 404);
  const had = await one(env, `SELECT i.id FROM inquiries i JOIN dev_links l ON l.inquiry_id = i.id AND l.item_id = ? WHERE i.device = ? AND i.via = 'known'`, id, dev);
  if (body.on === false) {
    if (had) await batch(env, forgetInquiries(env, "id = ?", had.id));
  } else if (!had) {
    const hit = await overLimit(env, now, [["device", `known|${dev}`, LOOP.knownPerDay, DAY]]);
    if (hit) return hit;
    const app = typeof body.app === "string" && /^[0-9. ()]{1,24}$/.test(body.app) ? body.app : undefined;
    const qid = crypto.getRandomValues(new Uint8Array(22)).reduce((s, b) => s + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[b % 62], "");
    const fixed = d.status === "done" && (d.live_at || d.released_in);
    // 바로 답 한 줄을 붙여 둬요: 관리자 차례로 쌓이지 않고, 고친 판이 나가면 이 문의에도 안내가 가요(desk.js shipTargets).
    const reply = fixed ? `알려 주셔서 고마워요. 이 문제는 ${d.released_in ? `${d.released_in} 버전에서 ` : ""}고쳐 두었어요. 앱을 업데이트한 뒤에도 그대로면 아래 '아직이에요'를 눌러 주세요.`
      : "알려 주셔서 고마워요. 같은 일을 겪는 분이 여럿이라 먼저 고치고 있어요. 고친 업데이트가 나오면 이 문의에서 바로 알려 드릴게요.";
    await batch(env, [
      stmt(env, "INSERT INTO inquiries (id, device, kind, info, status, public, created_at, updated_at, via) VALUES (?, ?, 'bug', ?, 'answered', 0, ?, ?, 'known')",
        qid, dev, JSON.stringify(app ? { app } : {}), now, now),
      stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)", qid, `[알려진 문제] ${d.public_title}`, now),
      stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, '자동 정리', ?)", qid, reply, now + 1),
      stmt(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, '알려진 문제판', ?)", id, qid, now),
      devEvent(loopActor(env, "알려진 문제판"), id, now, "me_too", { inquiry: qid }),
    ]);
    if (fixed) await markTold(env, id, [qid], now);
  }
  knownCache.delete(env.DB ?? env);
  return json(await knownList(env, dev, now));
}

/** GET /api/known, POST /api/known/<d…>/me {on, app}. */
export async function knownRoute(request, env, path) {
  const device = deviceId(request.headers.get("x-device"));
  const dev = device ? await deviceHash(device) : null;
  const now = Date.now();
  if (path === "/api/known" && request.method === "GET") return json(await knownList(env, dev, now), 200, { "cache-control": "no-store" });
  const m = path.match(/^\/api\/known\/(d[A-Za-z0-9]{15})\/me$/);
  if (m && request.method === "POST") {
    if (!dev) return json({ error: "device", field: "device" }, 400);
    return knownMe(request, env, dev, m[1], now);
  }
  return json({ error: "not_found" }, 404);
}

// ---- [LOOP-13] Play 인앱 업데이트 ----

/** /api/config의 update: urgentMin보다 낮은 versionCode는 바로 업데이트(긴급), flexibleMin보다 낮으면 앱 안에서 한 번 권해요(정기). 0이면 안 함. */
export async function updatePolicy(env) {
  const sw = await loopSwitches(env).catch(() => SWITCH_DEFAULT);
  return { urgentMin: sw.urgentMin ?? 0, flexibleMin: sw.flexibleMin ?? 0 };
}

// ---- [LOOP-08] 영향도 점수 ----

/** 개발사항 하나의 점수: 묶인 문의 3, 나도 겪어요·같아요 1, 급함 5, 걸린 판 1, 고친 뒤 다시 생김 6. */
export function impact(d) {
  return (d.asks ?? 0) * 3 + (d.same ?? 0) + (d.urgent ?? 0) * 5 + (d.versions ?? 0) + (d.again ?? 0) * 6;
}

/** Claude가 한 바퀴에 가져갈 순서: 끝나지 않은 개발사항을 점수 순으로. */
export async function devQueue(env, limit = 60) {
  const rows = await all(env, `SELECT d.id, d.title, d.status, d.track, d.size, d.route, d.tier, d.handed_at, d.created_at, d.updated_at,
      (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks,
      (SELECT COALESCE(SUM(i.same), 0) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id) AS same,
      (SELECT COUNT(DISTINCT json_extract(i.info, '$.app')) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id AND json_valid(i.info)) AS versions,
      (SELECT COUNT(*) FROM dev_history h WHERE h.item_id = d.id AND h.action = 'fixed_no') AS again,
      (SELECT GROUP_CONCAT(m.text, ' ') FROM dev_links l JOIN inquiry_messages m ON m.inquiry_id = l.inquiry_id AND m.sender = 'user' WHERE l.item_id = d.id) AS words
    FROM dev_items d WHERE d.deleted_at IS NULL AND d.status IN ('todo', 'doing') LIMIT 400`).catch(() => []);
  const URGENT_WORDS = /안\s*울|못\s*(들|일어)|놓쳤|늦게\s*울|지각|안\s*열|안\s*켜|튕|강제\s*종료|꺼져|데이터.{0,6}(사라|없어|날아)|사라졌|환불|결제.{0,6}(안|됐|두\s*번)/;
  return rows.map((d) => {
    const urgent = URGENT_WORDS.test(d.words ?? "") ? 1 : 0;
    const x = { id: d.id, title: d.title, status: d.status, statusName: DEV.statuses[d.status], track: d.track, size: d.size, route: d.route, tier: d.tier,
      handed: Boolean(d.handed_at), asks: d.asks, same: d.same, versions: d.versions, again: d.again, urgent, createdAt: iso(d.created_at) };
    return { ...x, score: impact(x) };
  }).sort((a, b) => b.score - a.score || a.createdAt.localeCompare(b.createdAt)).slice(0, limit);
}

// ---- [LOOP-05] 저장소 → 서버 동기화 ----

const SYNC_STATUS = { doing: "doing", check: "doing", done: "done", rejected: "rejected", todo: "todo" };
const ID = /^d[A-Za-z0-9]{15}$/;
const TRACK = /^[A-Z][A-Z0-9]{0,9}-\d{1,4}$/;

/** POST /dev/sync {sha, version, pr, items:[{id?, track, title?, size, status, route, remote?, flag?, events?, news?, shipReply?, tierAsk?, tierWhy?,
 * publicTitle?, test?, check?, reason?, pr?}]}: main에 합쳐진 줄들. 요금(tier)은 여기서 정하지 못해요. */
export async function syncDev(request, env, now, answer) {
  const sw = await loopSwitches(env);
  if (!sw.sync) return json({ error: "sync_off" }, 409);
  const body = await readJson(request, 256 * 1024);
  const ver = body.version === undefined || body.version === null || body.version === "" ? null : String(body.version).trim();
  if (ver !== null && !/^\d{1,2}\.\d{1,2}\.\d{1,3}$/.test(ver)) refuse("version", "version");
  const sha = typeof body.sha === "string" && /^[0-9a-f]{7,40}$/.test(body.sha) ? body.sha : "";
  const pr = Number.isInteger(body.pr) && body.pr > 0 ? body.pr : null;
  const items = Array.isArray(body.items) && body.items.length <= LOOP.syncItems ? body.items : refuse("items", "items");
  const results = [];
  for (const raw of items) {
    try {
      results.push(await syncOne(env, raw, { ver, sha, pr }, now, answer));
    } catch (e) {
      results.push({ id: raw?.id ?? null, track: raw?.track ?? null, error: e?.code ?? e?.message ?? "error" });
    }
  }
  const summary = { made: results.filter((r) => r.made).length, changed: results.filter((r) => r.changed).length, told: results.reduce((n, r) => n + (r.told ?? 0), 0),
    errors: results.filter((r) => r.error).length };
  await run(env, "INSERT INTO loop_syncs (at, sha, version, pr, n, result) VALUES (?, ?, ?, ?, ?, ?)", now, sha, ver ?? "", pr, items.length, JSON.stringify(summary));
  await audit(env, now, "loop_sync", `${items.length}줄`, `${sha.slice(0, 8)} ${ver ?? ""} · 새 ${summary.made} · 바뀜 ${summary.changed} · 답 ${summary.told}${summary.errors ? ` · 오류 ${summary.errors}` : ""}`);
  const live = await liveTick(env, now, answer);
  return json({ ...summary, live: live.live, results });
}

const pick = (v, set) => (typeof v === "string" && Object.hasOwn(set, v) ? v : "");

async function syncOne(env, x, { ver, sha, pr }, now, answer) {
  if (!x || typeof x !== "object") refuse("item", "items");
  const track = typeof x.track === "string" && TRACK.test(x.track) ? x.track : "";
  let row = typeof x.id === "string" && ID.test(x.id) ? await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL", x.id) : null;
  if (!row && track) row = await one(env, "SELECT * FROM dev_items WHERE track = ? AND deleted_at IS NULL ORDER BY created_at LIMIT 1", track);
  let made = false;
  if (!row) {
    if (!track && !x.title) refuse("item", "items");
    const title = plainText(x.title ?? track, DEV.title, "title", true);
    const id = `d${crypto.getRandomValues(new Uint8Array(15)).reduce((s, b) => s + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[b % 62], "")}`;
    await batch(env, [stmt(env, `INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, created_at, updated_at, handed_at, track)
        VALUES (?, ?, '', 'todo', '', ?, '저장소', ?, ?, ?, ?)`, id, title, env.actor?.id ?? "sync", now, now, now, track),
      devEvent(env, id, now, "create", { title, from: "저장소" })]);
    row = await one(env, "SELECT * FROM dev_items WHERE id = ?", id);
    made = true;
  }
  // 저장소가 아는 칸(요금은 빼고): 바뀐 것만.
  const set = {};
  const put = (col, v) => {
    if (v !== undefined && v !== (row[col] ?? "")) set[col] = v;
  };
  if (track) put("track", track);
  put("size", x.size === undefined ? undefined : pick(x.size, LOOP.sizes));
  put("route", x.route === undefined ? undefined : pick(x.route, LOOP.routes));
  if (x.remote !== undefined) put("remote", remoteWant(x.remote) ? x.remote : "");
  if (x.flag !== undefined) put("flag", typeof x.flag === "string" && /^[A-Za-z0-9._]{2,50}$/.test(x.flag) ? x.flag : "");
  if (x.events !== undefined) put("events", (Array.isArray(x.events) ? x.events : []).filter((e) => typeof e === "string" && /^[a-z0-9_]{2,40}$/.test(e)).slice(0, 8).join(","));
  if (x.tierAsk !== undefined) put("tier_ask", pick(x.tierAsk, LOOP.tiers));
  if (x.tierWhy !== undefined) put("tier_why", plainText(x.tierWhy, 600, "tierWhy"));
  if (x.publicTitle !== undefined) put("public_title", x.publicTitle ? inquiryText(x.publicTitle).slice(0, LOOP.knownTitle) : "");
  // 알려진 문제판: true 늘 보임, false 늘 숨김, 없으면 여럿이 겪을 때만.
  if (x.known !== undefined) put("known", x.known === true ? 1 : x.known === false ? -1 : 0);
  if (x.test !== undefined) put("test", plainText(x.test, 200, "test"));
  if (x.check !== undefined) put("check_text", plainText(x.check, 1000, "check"));
  const itemPr = Number.isInteger(x.pr) && x.pr > 0 ? x.pr : pr;
  if (itemPr && itemPr !== row.pr) set.pr = itemPr;
  if (sha && sha !== row.sha) set.sha = sha;
  const fields = Object.keys(set);
  if (fields.length) {
    await batch(env, [stmt(env, `UPDATE dev_items SET ${fields.map((c) => `${c} = ?`).join(", ")}, updated_at = ? WHERE id = ?`, ...fields.map((c) => set[c]), Math.max(now, row.updated_at + 1), row.id),
      devEvent(env, row.id, now, "sync", { fields, pr: itemPr ?? null, sha: sha.slice(0, 12) || null, version: ver })]);
    row = await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id);
  }
  // 상태·고친 판·출시 답·공지는 기존 길(devUpdate)로: 기록·보상·충돌 막기가 그대로예요.
  const upd = {};
  const status = typeof x.status === "string" ? SYNC_STATUS[x.status] : undefined;
  if (status && status !== row.status) upd.status = status;
  if (status === "rejected") upd.reason = x.reason || "저장소에서 반려";
  const route = row.route;
  if (status === "done" && ["app", "hotfix", ""].includes(route) && ver && ver !== row.released_in) upd.releasedIn = ver;
  if (x.news !== undefined) upd.news = x.news || null;
  if (x.shipReply !== undefined) upd.shipReply = x.shipReply || null;
  let changed = made || fields.length > 0;
  if (Object.keys(upd).length) {
    const r = await devUpdate(env, row, upd, now);
    if (r === true) changed = true;
    row = await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id);
  }
  if (x.status === "check" && x.check && x.check !== row.check_text) await batch(env, [devEvent(env, row.id, now, "note", { text: `PC 확인 대기: ${cut(String(x.check))}` })]).catch(() => null);
  // 앱 판으로 고쳤지만 아직 안 나간 것: 기다리는 문의에 '다음 정기 업데이트'를 날짜와 함께(한 번만, 이미 답이 마지막이면 그대로).
  let told = 0;
  if (upd.status === "done" && row.released_in && ["app", "hotfix", ""].includes(route)) {
    const shipped = await shippedVersion(env);
    if (!isOut(row.released_in, shipped)) {
      told = await tellLater(env, row, route, now, answer);
    }
  }
  return { id: row.id, track: row.track, made, changed, told };
}

/** 출시 전 답(DESK.later를 담아 판이 나갈 때 다시 알림): 기다리는 문의에만, 다음 정기 출발일과 함께. */
export function laterWords(v, route, train) {
  const when = route === "hotfix" ? `곧 나올 ${v} 긴급` : train ? `${v} 버전으로 ${dayWords(train)} 정기` : `${v}`;
  return `불편을 드려 죄송해요. 말씀해 주신 내용은 원인을 찾아 고쳐 두었고, ${when} ${DESK.later}. 업데이트가 나오는 대로 이 문의에서 바로 알려 드릴게요. 조금만 기다려 주세요.`;
}

async function tellLater(env, row, route, now, answer) {
  const text = laterWords(row.released_in, route, nextTrain(await loopTrain(env), now));
  const waiting = await all(env, `SELECT i.id FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ? AND i.status != 'closed'
    AND (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) = 'user'`, row.id);
  let told = 0;
  for (const w of waiting) if (await tryAnswer(answer, env, w.id, text, now)) told++;
  return told;
}

// ---- [LOOP-09][LOOP-10] 결정함과 요금 ----

/** 요금을 정할 개발사항: 새 기능인데 아직 정하지 않았거나 '더 논의'인 것(반려 빼고). */
export async function tierPending(env) {
  return all(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d
    WHERE d.deleted_at IS NULL AND d.size = 'feature' AND d.status != 'rejected' AND d.tier IN ('', 'later') ORDER BY d.updated_at DESC LIMIT 100`).catch(() => []);
}

/** [LOOP-10] 스위치를 켜기 전에: 이 기능 스위치에 묶인 새 기능이 요금이 정해지지 않았으면 그 개발사항 하나. */
export async function tierGate(env, flagKey) {
  return one(env, `SELECT id, title, tier FROM dev_items WHERE deleted_at IS NULL AND size = 'feature' AND flag = ? AND tier IN ('', 'later') AND status != 'rejected' LIMIT 1`, flagKey).catch(() => null);
}

/** POST /dev/<id>/tier {tier, note}: 최고 관리자만(사용자 2026-11-07 "무료 프로는 사용자 논의를 거쳐야지만"). */
export async function setTier(request, env, id, now) {
  if (!env.actor?.owner || env.actor?.key) return json({ error: "no_permission", area: "owner" }, 403);
  const body = await readJson(request, 2048);
  const tier = pick(body.tier, LOOP.tiers) || refuse("tier", "tier");
  const note = plainText(body.note, 300, "note");
  const row = await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL", id);
  if (!row) return json({ error: "not_found" }, 404);
  await batch(env, [stmt(env, "UPDATE dev_items SET tier = ?, tier_by = ?, tier_at = ?, tier_note = ?, updated_at = ? WHERE id = ?", tier, actorName(env), now, note, Math.max(now, row.updated_at + 1), id),
    devEvent(env, id, now, "tier", { tier: [row.tier || "", tier], note })]);
  await audit(env, now, "loop_tier", row.title, `${LOOP.tiers[tier]}${note ? ` · ${note}` : ""}`);
  return null;
}

/** [LOOP-11] 요금 판단 관측치: 요청 수, 쓰는 수(사용 통계를 켠 폰), 켜진 폰 비율, 켜기 앞뒤 14일의 프로 화면·체험·구독·문제 신고. */
export async function tierObservation(env, d, now) {
  const events = String(d.events ?? "").split(",").filter(Boolean);
  const since = kstDay(now - 28 * DAY);
  const [reqs, base, used, flagged, flagOn] = (await batch(env, [
    stmt(env, `SELECT COUNT(*) AS asks, COALESCE(SUM(i.same), 0) AS same, SUM(i.via = 'known') AS metoo, SUM(i.kind = 'idea') AS ideas
      FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ?`, d.id),
    stmt(env, "SELECT COALESCE(SUM(phones), 0) AS phones, COUNT(DISTINCT day) AS days FROM usage_days WHERE day >= ?", since),
    events.length ? stmt(env, `SELECT event, SUM(phones) AS phones, SUM(total) AS total, MAX(recent) AS recent FROM usage_events WHERE day >= ? AND event IN (${events.map(() => "?").join(",")}) GROUP BY event`, since, ...events)
      : stmt(env, "SELECT NULL AS event WHERE 0"),
    d.flag ? stmt(env, "SELECT COALESCE(SUM(phones), 0) AS phones FROM usage_flags WHERE day >= ? AND flag = ?", since, d.flag) : stmt(env, "SELECT 0 AS phones"),
    d.flag ? stmt(env, "SELECT since FROM flag_state WHERE key = ?", d.flag) : stmt(env, "SELECT NULL AS since WHERE 0"),
  ]).catch(() => [{ results: [{}] }, { results: [{}] }, { results: [] }, { results: [{}] }, { results: [] }])).map((r) => r.results ?? []);
  const pivot = flagOn[0]?.since ?? null;
  let around = null;
  if (pivot) {
    const from = kstDay(pivot - 14 * DAY);
    const to = kstDay(pivot + 14 * DAY);
    const rows = await all(env, `SELECT day < ? AS before, event, SUM(total) AS total FROM usage_events WHERE day >= ? AND day <= ?
      AND event IN ('paywall_view', 'trial_start', 'subscribe') GROUP BY before, event`, kstDay(pivot), from, to).catch(() => []);
    const bugs = await all(env, `SELECT created_at < ? AS before, COUNT(*) AS n FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ? AND created_at < ? GROUP BY before`,
      pivot, pivot - 14 * DAY, pivot + 14 * DAY).catch(() => []);
    const side = (b) => Object.fromEntries(["paywall_view", "trial_start", "subscribe"].map((e) => [e, rows.find((r) => Boolean(r.before) === b && r.event === e)?.total ?? 0]));
    around = { since: iso(pivot), days: 14, before: { ...side(true), bugs: bugs.find((r) => r.before)?.n ?? 0 }, after: { ...side(false), bugs: bugs.find((r) => !r.before)?.n ?? 0 } };
  }
  const phoneDays = base[0]?.phones ?? 0;
  return {
    requests: { asks: reqs[0]?.asks ?? 0, same: reqs[0]?.same ?? 0, meToo: reqs[0]?.metoo ?? 0, ideas: reqs[0]?.ideas ?? 0 },
    statsPhoneDays: phoneDays, statsDays: base[0]?.days ?? 0,
    usage: used.filter((u) => u.event).map((u) => ({ event: u.event, phoneDays: u.phones, total: u.total, recent: u.recent, share: phoneDays ? Math.round((u.phones / phoneDays) * 1000) / 10 : null })),
    flag: d.flag ? { key: d.flag, onPhoneDays: flagged[0]?.phones ?? 0, share: phoneDays ? Math.round(((flagged[0]?.phones ?? 0) / phoneDays) * 1000) / 10 : null, since: pivot ? iso(pivot) : null } : null,
    around,
    note: events.length ? null : "이 기능의 사용 이벤트(events)가 아직 정해지지 않았어요. dev-status 줄에 events를 적으면 쓰는 수가 보여요.",
  };
}

/** GET /loop/tiers: 요금을 정할 것과 정한 것, 각각 관측치와 함께. */
export async function tierBoard(env, now) {
  const rows = await all(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d
    WHERE d.deleted_at IS NULL AND d.size = 'feature' AND d.status != 'rejected' ORDER BY (d.tier IN ('', 'later')) DESC, d.updated_at DESC LIMIT 60`).catch(() => []);
  const store = await flagStore(env).catch(() => null);
  const out = [];
  for (const d of rows) {
    const on = d.flag && store ? serverEval(store, d.flag, now).on : null;
    out.push({ id: d.id, title: d.title, track: d.track, status: d.status, statusName: DEV.statuses[d.status], flag: d.flag || null, flagOn: on,
      tier: d.tier || null, tierName: d.tier ? LOOP.tiers[d.tier] : null, tierBy: d.tier_by || null, tierAt: d.tier_at ? iso(d.tier_at) : null, tierNote: d.tier_note,
      ask: d.tier_ask || null, askName: d.tier_ask ? LOOP.tiers[d.tier_ask] : null, why: d.tier_why, onBeforeTier: Boolean(on && (!d.tier || d.tier === "later")),
      observe: await tierObservation(env, d, now) });
  }
  return { tiers: LOOP.tiers, items: out };
}

/** 결정함: 최고 관리자 확인 문의(Claude 초안과 함께)와 요금을 정할 새 기능. */
export async function decideBox(env, actor, now, ledgerQuery) {
  const led = await ledgerQuery(env, actor, now, new URLSearchParams("view=owner")).catch(() => ({ items: [] }));
  const asks = (led.items ?? []).filter((r) => r.type === "inquiry");
  const drafts = asks.length ? await all(env, `SELECT * FROM loop_drafts WHERE key IN (${asks.map(() => "?").join(",")})`, ...asks.map((r) => r.key)) : [];
  const byKey = new Map(drafts.map((x) => [x.key, x]));
  const tiers = actor?.owner ? await tierPending(env) : [];
  return {
    asks: asks.slice(0, 40).map((r) => ({ key: r.key, id: r.id, no: r.no, title: r.summary || r.title, why: r.ownerAsk?.name ?? r.answer?.why ?? "", due: r.due, late: r.late,
      draft: byKey.get(r.key) ? { text: byKey.get(r.key).text, why: byKey.get(r.key).why, by: byKey.get(r.key).by, at: iso(byKey.get(r.key).at) } : null })),
    tiers: tiers.map((d) => ({ id: d.id, title: d.title, track: d.track, asks: d.asks, ask: d.tier_ask || null, askName: d.tier_ask ? LOOP.tiers[d.tier_ask] : null, why: d.tier_why,
      later: d.tier === "later", note: d.tier_note })),
    tierNames: LOOP.tiers,
  };
}

// ---- [LOOP-12] 믿을수록 더 맡기기 ----

export async function heldLog(env, pile, outcome, now) {
  await run(env, "INSERT INTO loop_held_log (pile, outcome, at) VALUES (?, ?, ?)", String(pile ?? ""), outcome, now).catch(() => null);
}

/** 종류마다: 보낸 수·고친 수·취소 수, 마지막으로 사람이 손댄 뒤 연속으로 그대로 나간 수, 바로 보내기 제안. */
export async function autonomy(env) {
  const sw = await loopSwitches(env);
  const rows = await all(env, "SELECT pile, outcome FROM loop_held_log ORDER BY id DESC LIMIT 3000").catch(() => []);
  const piles = Object.keys(LOOP.piles).map((pile) => {
    const mine = rows.filter((r) => r.pile === pile);
    let streak = 0;
    for (const r of mine) {
      if (r.outcome === "instant") continue;
      if (r.outcome !== "sent") break;
      streak++;
    }
    const instant = sw.instant.includes(pile);
    return { pile, name: LOOP.piles[pile], sent: mine.filter((r) => r.outcome === "sent").length, edited: mine.filter((r) => r.outcome === "edited").length,
      cancelled: mine.filter((r) => r.outcome === "cancelled").length, quick: mine.filter((r) => r.outcome === "instant").length, streak, instant, suggest: !instant && streak >= LOOP.streak };
  });
  return { piles, need: LOOP.streak };
}

/** Claude 답이 보류 없이 바로 가도 되는지: 그 종류를 사람이 켰고, 최고 관리자 확인이 아니고, 결제·환불 말이 없을 때. */
export async function instantOk(env, pile, kind, text) {
  if (!pile) return false;
  const sw = await loopSwitches(env);
  if (!sw.instant.includes(pile)) return false;
  if (kind === "company" || BILLING_RECORD.test(String(text ?? ""))) return false;
  return true;
}

// ---- 화면과 길 ----

/** GET /loop: 흐름 화면의 자동 순환 칸. */
export async function loopState(env, now) {
  const [sw, train, track, ships, syncs, shipped, urgent] = await Promise.all([
    loopSwitches(env), loopTrain(env), readSetting(env, "play_track", {}),
    all(env, "SELECT * FROM loop_ships ORDER BY seen_at DESC LIMIT 6").catch(() => []),
    all(env, "SELECT * FROM loop_syncs ORDER BY id DESC LIMIT 8").catch(() => []),
    shippedVersion(env),
    one(env, `SELECT COUNT(*) AS n FROM inquiries i WHERE i.status = 'open' AND i.kind IN ('bug', 'problem')
      AND (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) = 'user'`).catch(() => ({ n: 0 })),
  ]);
  const notLive = await all(env, `SELECT id, title, route, remote, flag FROM dev_items WHERE deleted_at IS NULL AND status = 'done' AND live_at IS NULL AND route IN ('server', 'remote', 'flag') LIMIT 30`).catch(() => []);
  const keys = await all(env, "SELECT id, label, scope, last_at, expires_at, revoked_at FROM admin_keys WHERE scope = 'sync' ORDER BY created_at DESC LIMIT 3").catch(() => []);
  return {
    now: iso(now), switches: sw, train: { ...train, next: nextTrain(train, now) }, play: { ready: Boolean(playReady(env)), ...track, at: track.at ? iso(track.at) : null },
    shipped, ships: ships.map((s) => ({ version: s.version, status: s.status, seenAt: iso(s.seen_at), until: iso(s.until), doneAt: s.done_at ? iso(s.done_at) : null, by: s.by, result: s.result })),
    syncs: syncs.map((s) => ({ at: iso(s.at), sha: s.sha, version: s.version, pr: s.pr, n: s.n, result: JSON.parse(s.result || "{}") })),
    waitingLive: notLive.map((d) => ({ id: d.id, title: d.title, route: d.route, routeName: LOOP.routes[d.route], want: d.route === "remote" ? d.remote : d.route === "flag" ? d.flag : "다음 서버 배포" })),
    syncKeys: keys.map((k) => ({ id: k.id, label: k.label, lastAt: k.last_at ? iso(k.last_at) : null, alive: !k.revoked_at && k.expires_at > now })),
    urgentWaiting: urgent?.n ?? 0, autonomy: await autonomy(env), rewards: await rewardRules(env), routes: LOOP.routes, sizes: LOOP.sizes,
  };
}

/** /loop… 길. 없는 길이면 null. [answer](env, inquiryId, text, now)는 '자동 정리'로 답하기. */
export async function loopRoute(request, env, url, path, method, now, { answer, ledgerQuery }) {
  if (path === "/loop" && method === "GET") return json(await loopState(env, now));
  if (path === "/loop/queue" && method === "GET") return json({ items: await devQueue(env, Math.min(200, Number(url.searchParams.get("limit")) || 60)) });
  if (path === "/loop/decide" && method === "GET") return json(await decideBox(env, env.actor, now, ledgerQuery));
  if (path === "/loop/tiers" && method === "GET") return json(await tierBoard(env, now));
  if (path === "/loop/autonomy" && method === "GET") return json(await autonomy(env));
  let m = path.match(/^\/loop\/ship\/(\d{1,2}\.\d{1,2}\.\d{1,3})\/(cancel|now)$/);
  if (m && method === "POST") {
    if (env.actor?.key) return json({ error: "no_permission" }, 403);
    const row = await one(env, "SELECT * FROM loop_ships WHERE version = ? AND status = 'waiting'", m[1]);
    if (!row) return json({ error: "not_found" }, 404);
    if (m[2] === "cancel") {
      await run(env, "UPDATE loop_ships SET status = 'cancelled', by = ?, done_at = ? WHERE version = ?", actorName(env), now, m[1]);
      await audit(env, now, "loop_ship_cancel", m[1]);
      return json({ version: m[1], status: "cancelled" });
    }
    return json(await sendShip(env, m[1], now, answer, actorName(env)));
  }
  if (path === "/loop/play" && method === "POST") {
    if (env.actor?.key) return json({ error: "no_permission" }, 403);
    return json(await playTick(env, now).catch((e) => ({ error: String(e?.message ?? e).slice(0, 200) })));
  }
  if (path === "/loop/switches" && method === "POST") {
    if (!env.actor?.owner || env.actor?.key) return json({ error: "no_permission", area: "owner" }, 403);
    const body = await readJson(request, 2048);
    const was = await loopSwitches(env);
    const next = { ...was };
    for (const k of ["autoShip", "sync", "phone", "halt"]) if (typeof body[k] === "boolean") next[k] = body[k];
    if (Array.isArray(body.instant)) next.instant = body.instant.filter((p) => Object.hasOwn(LOOP.piles, p) && p !== "new");
    for (const k of ["urgentMin", "flexibleMin"]) if (body[k] !== undefined) next[k] = Number.isInteger(body[k]) && body[k] >= 0 && body[k] < 1e7 ? body[k] : refuse(k, k);
    await saveSetting(env, "loop_switches", next);
    const diff = Object.keys(next).filter((k) => JSON.stringify(next[k]) !== JSON.stringify(was[k]));
    await audit(env, now, "loop_switches", diff.join(" ") || "같음", diff.map((k) => `${k}=${JSON.stringify(next[k])}`).join(" "));
    return json(next);
  }
  if (path === "/loop/train" && method === "POST") {
    if (!env.actor?.owner || env.actor?.key) return json({ error: "no_permission", area: "owner" }, 403);
    const body = await readJson(request, 1024);
    const start = typeof body.start === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.start) && Number.isFinite(Date.parse(body.start)) ? body.start : refuse("start", "start");
    const days = Number.isInteger(body.days) && body.days >= 7 && body.days <= 60 ? body.days : refuse("days", "days");
    await saveSetting(env, "loop_train", { start, days });
    await audit(env, now, "loop_train", start, `${days}일마다`);
    return json({ start, days, next: nextTrain({ start, days }, now) });
  }
  if (path === "/loop/rewards" && method === "POST") {
    if (!env.actor?.owner || env.actor?.key) return json({ error: "no_permission", area: "owner" }, 403);
    const body = await readJson(request, 1024);
    const next = {};
    for (const k of Object.keys(REWARD_RULES)) {
      const v = body[k] === undefined ? (await rewardRules(env))[k] : body[k];
      next[k] = Number.isInteger(v) && v >= 0 && v <= REWARD_MOST[k] ? v : refuse(k, k);
    }
    await saveSetting(env, "reward_rules", next);
    await audit(env, now, "loop_rewards", "쓰면 쌓이는 날", Object.entries(next).map(([k, v]) => `${k}=${v}`).join(" "));
    return json(next);
  }
  if (path === "/loop/drafts" && method === "POST") {
    const body = await readJson(request, 8192);
    const key = typeof body.key === "string" && /^inquiry:[A-Za-z0-9]{22}$/.test(body.key) ? body.key : refuse("key", "key");
    if (body.text === null) {
      await run(env, "DELETE FROM loop_drafts WHERE key = ?", key);
      return json({ key, draft: null });
    }
    const text = plainText(body.text, INQUIRY.text, "text", true);
    const why = plainText(body.why, 300, "why");
    await run(env, "INSERT OR REPLACE INTO loop_drafts (key, text, why, by, at) VALUES (?, ?, ?, ?, ?)", key, text, why, actorName(env), now);
    await audit(env, now, "loop_draft", key.slice(8), why);
    return json({ key, draft: { text, why } });
  }
  m = path.match(/^\/loop\/tier\/(d[A-Za-z0-9]{15})$/);
  if (m && method === "GET") {
    const d = await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL", m[1]);
    return d ? json(await tierObservation(env, d, now)) : json({ error: "not_found" }, 404);
  }
  if (m && method === "POST") {
    const r = await setTier(request, env, m[1], now);
    return r ?? json({ ok: true });
  }
  return null;
}
