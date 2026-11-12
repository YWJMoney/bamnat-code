import { all, audit, batch, can, deviceHash, deviceId, iso, json, network, networkCheck, notifyAdmins, one, overLimit, readJson, refuse, run, stmt, remoteState, APP_CODES } from "./index.js";
import { pushReady } from "./push.js";
import { DAY, HOUR } from "./units.js";
import { PLAY, playReady, playToken, rewardRules, REWARD_RULES, REWARD_MOST } from "./money.js";
import { DEV, actorName, cut, devEvent, devUpdate, forgetInquiries, plainText, inquiryText, INQUIRY, BILLING_RECORD, SLEEP_URGENT, URGENT, triage } from "./inquiries.js";
import { compareVersions, inboxAnswer, titleFrom, version } from "./inbox.js";
import { askOwner } from "./ledger.js";
import { DESK, broadcastRisk, isOut, markTold, resumeShip, shipNow, shippedVersion, shipText } from "./desk.js";
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
  /**
   * [LOOP-32] Play 트랙의 'completed'는 검토 중에도 같아요(Play API는 검토·게시 상태를 알려 주지 않아요, 1.39 실서버 11-08).
   * 그래서 실제로 나갔는지는 그 판으로 쓰는 휴대폰으로 봐요: 지난 며칠 중 하루라도 이만큼(테스트 트랙 사람보다 많게),
   * 그리고 앞 판 하루 휴대폰의 이 비율 이상이면 나간 것으로 봐요. 그 전에도 사람이 Play Console에서 보고 '게시됨 확인'을 누를 수 있어요.
   */
  liveMin: 30,
  liveShare: 0.05,
  liveDays: 3,
  /**
   * [LOOP-33] 사용자 2026-11-08 '30대가 떠야지 인지하는 거야?': 위 휴대폰은 사용 통계에 동의한 폰만이라(실서버 하루 1대 남짓) 1.38이
   * 게시되고도 0/30대에 멈췄어요. 그래서 동의와 상관없이 Play 보고서(판별 설치)도 봐요: 트랙에서 100%를 본 날부터 그 판 번호로 새로 깔거나
   * 올린 기기가 이만큼이면 나간 것으로 봐요. 테스트 트랙 사람은 그 전에 이미 올렸으니 이 수에 들지 않아요. 보고서는 하루 이틀 늦어요.
   */
  playMin: 3,
  /** 정기 앱 업데이트 주기(사용자 2026-11-07: 2주 → 2026-11-08: 1주). */
  trainDays: 7,
  /** 알려진 문제판: 공개 제목이 있고 묶인 문의·나도 겪어요가 이만큼이면 보여요(한 사람만의 일은 안 보이게). */
  knownMin: 2,
  knownMax: 12,
  /** 고친 뒤 알려진 문제판에 '고쳤어요'로 남는 날. */
  knownFixedDays: 14,
  knownTitle: 60,
  /** 한 폰이 하루에 누를 수 있는 '나도 겪어요'. */
  knownPerDay: 10,
  /** 2.0.0 [RVQ-31] 검토 SVC-27: 한 네트워크가 하루에 누를 수 있는 '나도 겪어요'(가족·회사 와이파이를 생각해 폰 셋쯤). */
  knownPerNetworkDay: 30,
  /** 믿을수록 더 맡기기: 한 종류에서 연속으로 손대지 않고 나간 Claude 답이 이만큼이면 '바로 보내기'를 제안해요. */
  streak: 20,
  /** 요금: 사람(최고 관리자)만 정해요. later = 더 논의. */
  tiers: { free: "무료", pro: "프로", pass: "묶음 7일권(광고로 열기)", trial: "처음 며칠 무료 뒤 프로", later: "더 논의" },
  sizes: { fix: "고침", polish: "다듬기", feature: "새 기능" },
  /**
   * [LOOP-35] 요금을 물을지 가르는 기준(사용자 2026-11-09 '기존 기능의 오류나 개선은 동일하게 무료, 새 기능에 한해서만 무료·프로를 묻기').
   * 고침·다듬기는 기존 기능이라 묻지 않고 그 기능과 같은 요금(무료면 무료)으로 가요. 서버로만 닿는 새 기능은 관리자·운영용이라 묻지 않아요.
   * 최고 관리자는 항목마다 기준을 직접 바꿀 수 있어요(자동 판단이 틀릴 때).
   */
  basis: { new: "새 기능", existing: "기존 기능 고침·다듬기", internal: "운영·관리자용", unknown: "분류 전" },
  /** 요금 칸마다 무엇이 되는지(관리자 앱 고르는 단추 옆 설명). */
  tierHelp: {
    free: "모든 사용자가 바로 써요. 한 번 무료로 연 것은 다시 잠그지 않아요.",
    pro: "1년 이용권(자동 결제 없음)이 있는 사람만 써요. 무료 사용자에게는 미리보기와 안내만 보여요.",
    pass: "광고를 보면 7일 동안 열려요. 프로를 맛보게 하는 길이라 무료 사용자도 쓸 수는 있어요.",
    trial: "처음 며칠은 모두 쓰고, 그 뒤에는 프로만 써요. 습관이 되면 값어치가 큰 기능에 맞아요.",
    later: "지금 정하지 않고 다음 논의로 미뤄요. 정하기 전에는 이 기능의 스위치를 켤 수 없어요.",
  },
  routes: { server: "서버", remote: "원격 자료", flag: "기능 스위치", app: "정기 앱 업데이트", hotfix: "긴급 앱 업데이트" },
  /** 알려진 문제를 보일 근무 유형(앱 WorkStyle). */
  styles: { SHIFT: "교대", DAY: "일근", HOURLY: "알바", FIELD: "현장", BUSINESS: "자영업", INVEST: "투자" },
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
  // 2.0.0 [RVQ-31] 검토 SVC-27: '나도 겪어요'로 묶인 줄의 무리(무작위 이름). 그 개발사항에 그날 처음 누른 네트워크만 새 무리, 같은 네트워크의 다음 것은
  // 'x'(세지 않음). 네트워크 해시는 줄에 남기지 않고 하루짜리 한도 칸으로만 봐요. 문의로 묶인 줄은 ''(문의 하나가 한 표).
  "ALTER TABLE dev_links ADD COLUMN cluster TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN track TEXT NOT NULL DEFAULT ''",
  // PC 1.39 G10 확인 제안: 알려진 문제가 보일 근무 유형(빈칸 = 모두). 앱 WorkStyle 이름을 쉼표로.
  "ALTER TABLE dev_items ADD COLUMN styles TEXT NOT NULL DEFAULT ''",
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
  // [LOOP-35] 요금 기준을 사람이 정했으면 new·existing·internal(빈칸 = 크기·전달 길로 자동).
  "ALTER TABLE dev_items ADD COLUMN basis TEXT NOT NULL DEFAULT ''",
  // [LOOP-36] 관리자가 직접 넣은 것(또는 사용자 말을 Claude가 옮긴 것): 'admin'이면 1순위.
  "ALTER TABLE dev_items ADD COLUMN origin TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN public_title TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN known INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE dev_items ADD COLUMN pr INTEGER",
  "ALTER TABLE dev_items ADD COLUMN sha TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN test TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN check_text TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN live_at INTEGER",
  // [LOOP-39] 출시 2주 뒤 숫자로 본 효과: better·same·worse·none, 잰 때, 근거(JSON).
  "ALTER TABLE dev_items ADD COLUMN effect TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE dev_items ADD COLUMN effect_at INTEGER",
  "ALTER TABLE dev_items ADD COLUMN effect_note TEXT NOT NULL DEFAULT ''",
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
export const loopTrain = (env) => readSetting(env, "loop_train", { start: "2026-11-23", days: LOOP.trainDays });

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
    return { done: done?.v ?? null, ...(done?.codes?.length ? { doneCodes: done.codes } : {}), rolling: rolling ? { v: rolling.v, fraction: rolling.fraction } : null, halted: halted?.v ?? null, at: now };
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
  const shipped = await shippedVersion(env);
  const newer = (v) => v && (!shipped || compareVersions(version(v), version(shipped.v)) > 0);
  // [LOOP-32] 트랙 100%는 '검토 중일 수 있음'(review)으로만 적어요. 보류 시계는 실제로 나간 것을 본 뒤에 시작해요.
  if (newer(track.done)) {
    if (sw.autoShip) await run(env, "INSERT OR IGNORE INTO loop_ships (version, seen_at, until, status) VALUES (?, ?, 0, 'review')", track.done, now);
    track.seen = await liveSeen(env, track.done, now, shipped?.v, track.doneCodes);
  }
  await saveSetting(env, "play_track", track);
  let held = null;
  for (const r of await all(env, "SELECT version FROM loop_ships WHERE status = 'review'").catch(() => [])) {
    if (!newer(r.version)) {
      await run(env, "UPDATE loop_ships SET status = 'done', done_at = ?, by = ?, result = ? WHERE version = ? AND status = 'review'", now, "자동", "이미 저장된 판", r.version);
      continue;
    }
    const seen = r.version === track.done && track.seen ? track.seen : await liveSeen(env, r.version, now, shipped?.v);
    const why = seen.phones >= seen.need ? `이 판으로 쓰는 휴대폰 ${seen.phones}대` : `Play 보고서: 이 판을 새로 깔거나 올린 기기 ${seen.play}대`;
    if (seen.live && sw.autoShip && await holdShip(env, r.version, now, why)) held = r.version;
  }
  let advice = null;
  if (track.rolling && sw.halt) {
    // 2.0.0 [RVN-15] 검토 SVC-103: 단계적 출시 중인 판을 그 앞 판과(시험판이 섞여도) 견줘요.
    const h = await versionHealth(env, now, 14, null, { focus: track.rolling.v });
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

/**
 * [LOOP-32] 이 판으로 쓰는 휴대폰 수(usage_days, 지난 [LOOP.liveDays]일 중 가장 많은 하루)와 나갔다고 볼 기준.
 * 앞 판([prev])이 있으면 그 판 하루 휴대폰의 [LOOP.liveShare] 이상이어야 해요(테스트 트랙 사람만으로는 넘지 않게).
 */
export async function liveSeen(env, v, now, prev = null, codes = null) {
  const rows = await all(env, "SELECT app, MAX(phones) AS most FROM usage_days WHERE day >= ? AND app IN (?, ?) GROUP BY app",
    kstDay(now - LOOP.liveDays * DAY), v, prev ?? v).catch(() => []);
  const most = (a) => rows.find((r) => r.app === a)?.most ?? 0;
  const phones = most(v);
  const need = Math.max(LOOP.liveMin, prev && prev !== v ? Math.ceil(most(prev) * LOOP.liveShare) : 0);
  const play = await playMoved(env, v, now, codes);
  return { phones, need, live: phones >= need || (play !== null && play >= LOOP.playMin), ...(play !== null ? { play, playNeed: LOOP.playMin } : {}) };
}

/**
 * [LOOP-33] Play 보고서(판별 설치)에서 트랙 100%를 본 날(한국 날짜)부터 이 판 번호로 새로 깔거나 올린 기기. 보고서나 판 번호를 모르면 null.
 * 판 번호는 트랙 줄(versionCodes)에서, 없으면 마지막으로 읽은 트랙에서 가져와요.
 */
async function playMoved(env, v, now, codes) {
  const [ship, reports, track] = await Promise.all([one(env, "SELECT seen_at FROM loop_ships WHERE version = ?", v).catch(() => null),
    one(env, "SELECT value FROM settings WHERE key = 'play_reports'").catch(() => null),
    codes?.length ? null : one(env, "SELECT value FROM settings WHERE key = 'play_track'").catch(() => null)]);
  const read = (row) => {
    try {
      return row ? JSON.parse(row.value) : null;
    } catch {
      return null;
    }
  };
  const t = read(track);
  const want = new Set((codes?.length ? codes : t?.done === v ? t.doneCodes ?? [] : []).map(Number).filter(Number.isFinite));
  const moves = read(reports)?.value?.versions?.moves;
  if (!want.size || !Array.isArray(moves)) return null;
  const from = kstDay(ship?.seen_at ?? now);
  return moves.filter((m) => Array.isArray(m) && want.has(Number(m[1])) && String(m[0]) >= from).reduce((n, m) => n + (Number(m[2]) || 0), 0);
}

/** [LOOP-32] '검토 중일 수 있음' 판을 실제로 나갔다고 보고 1시간 보류를 시작해요. 시작했으면 참. */
async function holdShip(env, v, now, why) {
  const r = await run(env, "UPDATE loop_ships SET status = 'waiting', until = ? WHERE version = ? AND status = 'review'", now + LOOP.shipHold, v);
  if (!r.meta.changes) return false;
  const dry = await shipNow(env, v, { dry: true }, now);
  await audit(env, now, "loop_ship_seen", v, `${why} · 문의 ${dry.answers}건 · 공지 ${dry.notice ? 1 : 0} · 장터 작품 ${dry.market}개`);
  const goes = [`문의 ${dry.answers}건 안내`, dry.notice && "업데이트 소식 공지", dry.market && `장터 작품 ${dry.market}개 공개`].filter(Boolean);
  await notifyAdmins(env, "inquiry", { title: `${v} 출시를 확인했어요`,
    body: `${why}. 1시간 뒤 ${goes.join(", ")}가 나가요. 멈추려면 눌러 주세요`, url: "/admin/#/loop" }, now, { ignoreQuiet: true });
  return true;
}

/** 5분마다·매시: 보류가 끝난 출시 안내를 보내요. 스스로 안내 스위치가 꺼져 있으면 보내지 않아요(사람이 '지금 보내기'). */
export async function shipTick(env, now = Date.now(), answer) {
  // 1.40 [RVD-11] 관리자 단추든 자동이든, 중간에 끊긴 나간 판 저장은 남은 안내부터 이어요.
  await resumeShip(env, now, answer ?? autoReply).catch((e) => console.error(`resume ship: ${e?.message ?? e}`));
  if (!(await loopSwitches(env)).autoShip) return [];
  const rows = await all(env, "SELECT * FROM loop_ships WHERE status = 'waiting' AND until <= ?", now).catch(() => []);
  const out = [];
  // 2.0.0 [RVQ-28] 열쇠로 요청한 판은 그 이름(예: 'Claude 요청')으로 남아요.
  for (const r of rows) out.push(await sendShip(env, r.version, now, answer ?? autoReply, r.by || "자동"));
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
      stmt(env, "UPDATE inquiries SET status = 'open', updated_at = ? WHERE id = ?", now, row.id),
      // 다시 고친 판이 나가면 이 문의에도 다시 안내가 가고 해결됐나요를 다시 물어요(markTold가 fixed를 되돌려요).
      stmt(env, "UPDATE dev_links SET told_at = NULL WHERE inquiry_id = ?", row.id));
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

/**
 * 2.0.0 [RVQ-31] 검토 SVC-27: 개발사항에 묶인 목소리 수(SQL, 개발사항 별칭 d). 문의는 하나가 한 표, '나도 겪어요'는 서로 다른 네트워크마다 한 표
 * (설치 번호만 바꿔 누른 것은 늘지 않아요). 알려진 문제판에 보일지와 개발 대기열 점수가 이것으로 정해져요.
 */
export const VOICES_SQL = `((SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id AND l.cluster = '')
  + (SELECT COUNT(DISTINCT l.cluster) FROM dev_links l WHERE l.item_id = d.id AND l.cluster NOT IN ('', 'x')))`;

const knownCache = new WeakMap();

/** 앱 문의 화면 위 '지금 알려진 문제': 공개 제목이 있는 개발사항 중 여럿이 겪는 것, 고친 뒤 14일까지. */
export async function knownList(env, dev, now) {
  let base = knownCache.get(env.DB ?? env);
  if (!base || base.until <= now) {
    const shipped = await shippedVersion(env);
    const rows = await all(env, `SELECT d.id, d.public_title, d.status, d.released_in, d.live_at, d.known, d.updated_at, d.styles,
        ${VOICES_SQL} AS asks,
        (SELECT COALESCE(SUM(i.same), 0) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id) AS same
      FROM dev_items d WHERE d.deleted_at IS NULL AND d.public_title != '' AND d.known >= 0 AND d.status IN ('todo', 'doing', 'done')
      ORDER BY d.updated_at DESC LIMIT 200`).catch(() => []);
    const items = [];
    for (const d of rows) {
      if (d.known !== 1 && d.asks + d.same < LOOP.knownMin) continue;
      const out = d.status === "done" && (d.live_at || (d.released_in && isOut(d.released_in, shipped)));
      if (out && now - d.updated_at > LOOP.knownFixedDays * DAY) continue;
      const state = d.status === "todo" ? "checking" : d.status === "doing" || !out ? "fixing" : "fixed";
      items.push({ id: d.id, title: d.public_title, state, version: state === "fixed" ? d.released_in ?? null : null, me: d.asks + d.same,
        styles: String(d.styles ?? "").split(",").filter(Boolean) });
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
    // 2.0.0 [RVQ-31] 검토 SVC-27: 폰 하루 한도에 네트워크 하루 한도와 새 설치 한도를 더해요.
    const net = await network(request, env, now);
    const hit = await overLimit(env, now, [["device", `known|${dev}`, LOOP.knownPerDay, DAY], ["network", `knownnet|${net.day}`, LOOP.knownPerNetworkDay, DAY],
      ...(await networkCheck(env, dev, net, "known", LOOP.knownPerNetworkDay, now))]);
    if (hit) return hit;
    // 같은 네트워크는 그날 그 개발사항에 한 표: 처음이면 새 무리, 아니면 'x'. 누른 분은 다른 분과 똑같이 고친 판 안내를 받아요.
    const cluster = (await overLimit(env, now, [["network", `knownvote|${id}|${net.day}`, 1, DAY]])) ? "x" : `c${crypto.getRandomValues(new Uint8Array(8)).reduce((s, b) => s + b.toString(16).padStart(2, "0"), "")}`;
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
      stmt(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at, cluster) VALUES (?, ?, '알려진 문제판', ?, ?)", id, qid, now, cluster),
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

/** 개발사항 하나의 점수: 묶인 문의 3, 나도 겪어요·같아요 1, 급함 5, 걸린 판 1, 고친 뒤 다시 생김 6. [RVQ-31] asks는 목소리 수(VOICES_SQL)예요. */
export function impact(d) {
  return (d.asks ?? 0) * 3 + (d.same ?? 0) + (d.urgent ?? 0) * 5 + (d.versions ?? 0) + (d.again ?? 0) * 6;
}

/** Claude가 한 바퀴에 가져갈 순서: 끝나지 않은 개발사항을 점수 순으로. */
export async function devQueue(env, limit = 60) {
  const rows = await all(env, `SELECT d.id, d.title, d.status, d.track, d.size, d.route, d.tier, d.basis, d.origin, d.handed_at, d.created_at, d.updated_at,
      ${VOICES_SQL} AS asks, (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS linked,
      (SELECT COALESCE(SUM(i.same), 0) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id) AS same,
      (SELECT COUNT(DISTINCT json_extract(i.info, '$.app')) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id AND json_valid(i.info)) AS versions,
      (SELECT COUNT(*) FROM dev_history h WHERE h.item_id = d.id AND h.action = 'fixed_no') AS again,
      (SELECT GROUP_CONCAT(m.text, ' ') FROM dev_links l JOIN inquiry_messages m ON m.inquiry_id = l.inquiry_id AND m.sender = 'user' WHERE l.item_id = d.id) AS words
    FROM dev_items d WHERE d.deleted_at IS NULL AND d.status IN ('todo', 'doing') LIMIT 400`).catch(() => []);
  // 급함 말(알람·앱 실행·데이터·결제)과 수면 긴급 넷(사용자 11-07 승인)은 문의 쪽(inquiries.js)과 같은 것을 써요.
  return rows.map((d) => {
    const urgent = URGENT.test(d.words ?? "") || SLEEP_URGENT.test(d.words ?? "") ? 1 : 0;
    const x = { id: d.id, title: d.title, status: d.status, statusName: DEV.statuses[d.status], track: d.track, size: d.size, route: d.route, tier: d.tier,
      handed: Boolean(d.handed_at), asks: d.asks, linked: d.linked, same: d.same, versions: d.versions, again: d.again, urgent, createdAt: iso(d.created_at),
      first: d.origin === "admin", basis: basisOf(d).key };
    return { ...x, score: impact(x) };
  // [LOOP-36] 사용자가 직접 넣은 것이 늘 먼저(점수와 상관없이), 그 안에서는 점수 순.
  }).sort((a, b) => Number(b.first) - Number(a.first) || b.score - a.score || a.createdAt.localeCompare(b.createdAt)).slice(0, limit);
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
    errors: results.filter((r) => r.error).length, held: results.filter((r) => r.held).length };
  await run(env, "INSERT INTO loop_syncs (at, sha, version, pr, n, result) VALUES (?, ?, ?, ?, ?, ?)", now, sha, ver ?? "", pr, items.length, JSON.stringify(summary));
  await audit(env, now, "loop_sync", `${items.length}줄`, `${sha.slice(0, 8)} ${ver ?? ""} · 새 ${summary.made} · 바뀜 ${summary.changed} · 답 ${summary.told}${summary.errors ? ` · 오류 ${summary.errors}` : ""}${summary.held ? ` · 글 검사로 뺀 줄 ${summary.held}` : ""}`);
  const live = await liveTick(env, now, answer);
  return json({ ...summary, live: live.live, results });
}

/** 근무 유형 목록 → "SHIFT,DAY"(모르는 이름은 버려요, 빈칸 = 모두). */
export const styleList = (v) => [...new Set((Array.isArray(v) ? v : String(v ?? "").split(",")).map((x) => String(x).trim()).filter((x) => Object.hasOwn(LOOP.styles, x)))].join(",");
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
  if (x.styles !== undefined) put("styles", styleList(x.styles));
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
  // 2.0.0 [RVQ-28] 검토 SVC-10: 글 검사에 걸린 공지 한 줄·출시 답은 그 칸만 빼고(상태·판은 그대로 반영) 결과에 적어요.
  const held = [];
  for (const k of ["news", "shipReply"]) {
    if (x[k] === undefined) continue;
    if (x[k] && broadcastRisk(x[k])) held.push(k);
    else upd[k] = x[k] || null;
  }
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
  return { id: row.id, track: row.track, made, changed, told, ...(held.length ? { held } : {}) };
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

/** [LOOP-35] 요금을 물을 새 기능(SQL, 개발사항 별칭 d): 사람이 '새 기능'으로 정했거나, 자동이면 크기 '새 기능'이고 서버로만 닿지 않는 것. */
export const ASK_TIER_SQL = "(d.basis = 'new' OR (d.basis = '' AND d.size = 'feature' AND d.route != 'server'))";

/** [LOOP-35] 요금 기준 하나: 무엇으로 봤는지(key), 사람이 정했는지(auto false), 왜 그렇게 봤는지(why). */
export function basisOf(r) {
  const size = LOOP.sizes[r.size] ?? "";
  if (r.basis && Object.hasOwn(LOOP.basis, r.basis) && r.basis !== "unknown") {
    return { key: r.basis, name: LOOP.basis[r.basis], auto: false, why: "최고 관리자가 직접 정했어요" };
  }
  if (r.size === "fix" || r.size === "polish") {
    return { key: "existing", name: LOOP.basis.existing, auto: true, why: `크기가 '${size}'이라 이미 있는 기능을 고치거나 다듬는 일이에요. 그 기능과 같은 요금 그대로 가요(묻지 않아요).` };
  }
  if (r.size === "feature" && r.route === "server") {
    return { key: "internal", name: LOOP.basis.internal, auto: true, why: "새 기능이지만 서버로만 닿아요(관리자 앱·운영·통계). 앱 사용자가 돈을 내고 쓰는 기능이 아니라 묻지 않아요." };
  }
  if (r.size === "feature") {
    return { key: "new", name: LOOP.basis.new, auto: true, why: "크기가 '새 기능'이고 앱 사용자에게 닿아요. 처음 생기는 기능이라 무료·프로를 정해야 해요." };
  }
  return { key: "unknown", name: LOOP.basis.unknown, auto: true, why: "크기(고침·다듬기·새 기능)가 아직 정해지지 않았어요. 크기를 정하면 저절로 나뉘어요." };
}

/** [LOOP-35] 요금이 실제로 어떻게 되는지: 정한 값, 아니면 기준에서 나온 값. */
export function tierEffective(r, basis = basisOf(r)) {
  if (r.tier && Object.hasOwn(LOOP.tiers, r.tier)) return { key: r.tier, name: LOOP.tiers[r.tier], decided: r.tier !== "later" };
  if (basis.key === "existing") return { key: "same", name: "무료(기존과 같음)", decided: true };
  if (basis.key === "internal") return { key: "none", name: "요금 없음(운영용)", decided: true };
  return { key: "", name: basis.key === "new" ? "아직 안 정함" : "분류 전", decided: false };
}

/** 요금을 정할 개발사항: 물을 새 기능인데 아직 정하지 않았거나 '더 논의'인 것(반려 빼고). */
export async function tierPending(env) {
  return all(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d
    WHERE d.deleted_at IS NULL AND ${ASK_TIER_SQL} AND d.status != 'rejected' AND d.tier IN ('', 'later') ORDER BY d.origin = 'admin' DESC, d.updated_at DESC LIMIT 100`).catch(() => []);
}

/** [LOOP-10] 스위치를 켜기 전에: 이 기능 스위치에 묶인 새 기능이 요금이 정해지지 않았으면 그 개발사항 하나. */
export async function tierGate(env, flagKey) {
  return one(env, `SELECT d.id, d.title, d.tier FROM dev_items d WHERE d.deleted_at IS NULL AND ${ASK_TIER_SQL} AND d.flag = ? AND d.tier IN ('', 'later') AND d.status != 'rejected' LIMIT 1`, flagKey).catch(() => null);
}

/** [LOOP-35] POST /loop/basis/<id> {basis: new|existing|internal|"" (자동), note}: 최고 관리자만. 자동 판단이 틀린 것을 바로잡아요. */
export async function setBasis(request, env, id, now) {
  if (!env.actor?.owner || env.actor?.key) return json({ error: "no_permission", area: "owner" }, 403);
  const body = await readJson(request, 2048);
  const basis = body.basis === "" || body.basis === null ? "" : ["new", "existing", "internal"].includes(body.basis) ? body.basis : refuse("basis", "basis");
  const note = plainText(body.note, 300, "note");
  const row = await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL", id);
  if (!row) return json({ error: "not_found" }, 404);
  if (basis === (row.basis ?? "")) return null;
  await batch(env, [stmt(env, "UPDATE dev_items SET basis = ?, updated_at = ? WHERE id = ?", basis, Math.max(now, row.updated_at + 1), id),
    devEvent(env, id, now, "basis", { basis: [row.basis || "", basis], note })]);
  await audit(env, now, "loop_basis", row.title, `${basis ? LOOP.basis[basis] : "자동"}${note ? ` · ${note}` : ""}`);
  return null;
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

/** [LOOP-35] 요금 판단 화면의 고르기 칸: 정할 것·정한 새 기능·기존 기능(무료 그대로)·운영용·분류 전. */
export const TIER_VIEWS = { ask: "정할 것", decided: "정한 새 기능", existing: "기존 기능 고침·다듬기", internal: "운영·관리자용", unknown: "분류 전", all: "모두" };
const tierView = (b, t) => (b.key === "new" ? (t.decided ? "decided" : "ask") : b.key);

/**
 * GET /loop/tiers?view=: 개발사항마다 요금 기준(새 기능만 묻고 기존 기능 고침·다듬기는 무료 그대로)과 정한 값, 새 기능은 관측치까지.
 * 끝난 지 오래된 것(완료 뒤 60일)은 빼요.
 */
export async function tierBoard(env, now, params = new URLSearchParams()) {
  const view = Object.hasOwn(TIER_VIEWS, params.get("view") ?? "") ? params.get("view") : "ask";
  const rows = await all(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d
    WHERE d.deleted_at IS NULL AND d.status != 'rejected' AND (d.status != 'done' OR d.updated_at > ?)
    ORDER BY d.origin = 'admin' DESC, (d.tier IN ('', 'later')) DESC, d.updated_at DESC LIMIT 300`, now - 60 * DAY).catch(() => []);
  const store = await flagStore(env).catch(() => null);
  const counts = Object.fromEntries(Object.keys(TIER_VIEWS).map((k) => [k, 0]));
  const out = [];
  for (const d of rows) {
    const b = basisOf(d);
    const t = tierEffective(d, b);
    const v = tierView(b, t);
    counts[v]++;
    counts.all++;
    if (view !== "all" && view !== v) continue;
    const asked = b.key === "new";
    // 목록에 없는 스위치 이름(오타·지운 스위치)이어도 화면이 깨지지 않게.
    let on = null;
    try {
      on = d.flag && store ? serverEval(store, d.flag, now).on : null;
    } catch {
      on = null;
    }
    out.push({ id: d.id, title: d.title, track: d.track, status: d.status, statusName: DEV.statuses[d.status], flag: d.flag || null, flagOn: on,
      preview: [...String(d.body ?? "")].slice(0, 200).join(""), size: d.size || null, sizeName: LOOP.sizes[d.size] ?? null, route: d.route || null, routeName: LOOP.routes[d.route] ?? null,
      first: d.origin === "admin", basis: b, view: v, effective: t,
      tier: d.tier || null, tierName: d.tier ? LOOP.tiers[d.tier] : null, tierBy: d.tier_by || null, tierAt: d.tier_at ? iso(d.tier_at) : null, tierNote: d.tier_note,
      ask: d.tier_ask || null, askName: d.tier_ask ? LOOP.tiers[d.tier_ask] : null, why: d.tier_why, onBeforeTier: Boolean(asked && on && (!d.tier || d.tier === "later")),
      // 관측치는 요금을 물을 새 기능에만(기존 기능·운영용은 물을 일이 없어요). 아래에서 한꺼번에 읽어요.
      observe: null, _d: asked ? d : null });
  }
  // [ADM3-52] 항목마다 차례로 읽으면 DB를 그만큼 오가요: 한꺼번에.
  await Promise.all(out.map(async (x) => {
    if (x._d) x.observe = await tierObservation(env, x._d, now).catch(() => null);
    delete x._d;
  }));
  return { tiers: LOOP.tiers, tierHelp: LOOP.tierHelp, basisNames: LOOP.basis, views: TIER_VIEWS, view, counts, items: out };
}

/** 결정함: 최고 관리자 확인 문의(Claude 초안과 함께)와 요금을 정할 새 기능. */
/**
 * [LOOP-19][LOOP-20] 답을 기다리는 문의(열림, 마지막 말이 사용자) 가운데 급함(알람·앱 실행·데이터·결제·수면 긴급 넷)과
 * 최고 관리자 확인(환불·법·개인정보·화남·약관·회사 방식). 결정함과 메뉴 숫자(/badges)가 같은 판단을 써요.
 */
export const WAITING_SQL = `SELECT i.id, i.kind, i.info, i.created_at,
    (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first,
    (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last
  FROM inquiries i WHERE i.status = 'open' ORDER BY i.created_at DESC LIMIT 300`;

/** [WAITING_SQL]의 줄에서 급함과 최고 관리자 확인을 가려요(메뉴 숫자는 같은 batch로 읽고 여기서 셈). */
export function waitingFrom(rows) {
  const waiting = (rows ?? []).filter((r) => r.last === "user");
  const info = (r) => {
    try {
      return JSON.parse(r.info || "{}");
    } catch {
      return {};
    }
  };
  const urgent = [];
  for (const r of waiting) {
    const t = triage(r.kind, r.first, info(r));
    if (t.urgency === "urgent") urgent.push({ ...r, topic: t.topicName });
  }
  return { urgent, owner: waiting.filter((r) => askOwner(r.kind, r.first)) };
}

export async function waitingFlags(env) {
  return waitingFrom(await all(env, WAITING_SQL).catch(() => []));
}

export async function decideBox(env, actor, now, ledgerQuery) {
  // [ADM3-52] 서로 기다릴 일이 없는 읽기는 한꺼번에(실서버 결정함 p50 3.2초였어요).
  const [led, tiers, back, halts, waits] = await Promise.all([
    ledgerQuery(env, actor, now, new URLSearchParams("view=owner")).catch(() => ({ items: [] })),
    actor?.owner ? tierPending(env) : [],
    // [LOOP-16] '아직이에요'로 다시 열린 개발사항(아직 끝나지 않은 것)과 [LOOP-03] 최근 7일 출시 멈춤 권장(PC 1.39 G10 확인: 결정함에 안 보였음).
    all(env, `SELECT d.id, d.title, d.track, d.status, COUNT(*) AS n, MAX(h.at) AS last FROM dev_history h
      JOIN dev_items d ON d.id = h.item_id AND d.deleted_at IS NULL AND d.status IN ('todo', 'doing')
      WHERE h.action = 'fixed_no' GROUP BY d.id ORDER BY last DESC LIMIT 20`).catch(() => []),
    all(env, "SELECT target, detail, at FROM audit WHERE action = 'loop_halt_advice' AND at > ? ORDER BY at DESC LIMIT 5", now - 7 * DAY).catch(() => []),
    // [LOOP-19] 급함 문의는 맨 위에(문의 권한이 있는 사람에게만 제목까지).
    can(actor, "inquiries") ? waitingFlags(env) : { urgent: [] },
  ]);
  const asks = (led.items ?? []).filter((r) => r.type === "inquiry");
  const drafts = asks.length ? await all(env, `SELECT * FROM loop_drafts WHERE key IN (${asks.map(() => "?").join(",")})`, ...asks.map((r) => r.key)) : [];
  const byKey = new Map(drafts.map((x) => [x.key, x]));
  const urgent = waits.urgent.slice(0, 20);
  return {
    urgent: urgent.map((r) => ({ id: r.id, title: titleFrom(r.first ?? ""), topic: r.topic ?? null, at: iso(r.created_at) })),
    back: back.map((d) => ({ id: d.id, title: d.title, track: d.track || null, status: d.status, statusName: DEV.statuses[d.status], times: d.n, at: iso(d.last) })),
    halts: halts.map((h) => ({ version: h.target, why: h.detail, at: iso(h.at) })),
    asks: asks.slice(0, 40).map((r) => ({ key: r.key, id: r.id, no: r.no, title: r.summary || r.title, why: r.ownerAsk?.name ?? r.answer?.why ?? "", due: r.due, late: r.late,
      draft: byKey.get(r.key) ? { text: byKey.get(r.key).text, why: byKey.get(r.key).why, by: byKey.get(r.key).by, at: iso(byKey.get(r.key).at) } : null })),
    tiers: tiers.map((d) => ({ id: d.id, title: d.title, track: d.track, asks: d.asks, ask: d.tier_ask || null, askName: d.tier_ask ? LOOP.tiers[d.tier_ask] : null, why: d.tier_why,
      later: d.tier === "later", note: d.tier_note })),
    tierNames: LOOP.tiers,
  };
}

// ---- [LOOP-39] 숫자 → 개발 → 효과 한 줄 (사용자 11-09 승인 '혁신 두 가지' 둘째) ----
//
// 고친 것이 실제로 닿은 뒤(서버·원격·스위치는 live_at, 앱은 그 판 이상이 나간 날) 2주가 지나면, 그 개발사항의 숫자를 앞뒤 2주로 견줘
// '좋아졌어요·그대로예요·나빠졌어요'를 스스로 적어요. 잴 이벤트(events)가 있으면 그 이벤트를 쓴 휴대폰 비율, 없으면 묶인 문의가
// 나간 뒤에도 다시 왔는지('아직이에요'·새로 묶인 문의)와 '해결됐어요'로 봐요. 나빠졌으면 최고 관리자 오늘 화면 주의 필요에 떠요.

export const EFFECT = {
  days: 14,
  /** 이벤트 비율이 이만큼(%) 넘게 오르내려야 좋아짐·나빠짐. */
  move: 10,
  /** 앞뒤 각각 이만큼 휴대폰·날(사용 통계를 켠 폰)이 있어야 이벤트로 판정해요. */
  phoneDays: 30,
  names: { better: "좋아졌어요", same: "그대로예요", worse: "나빠졌어요", none: "잴 숫자가 없어요" },
  tones: { better: "ok", same: "warn", worse: "bad", none: "" },
  /** 한 번에 보는 수(매시). */
  batch: 20,
};

/** 개발사항 [d]가 사용자에게 닿은 때(ms) 또는 null. [ships]는 '나간 판' 기록 [{ v, at }] 오래된 것부터. */
export function reachedAt(d, ships) {
  if (["server", "remote", "flag"].includes(d.route) && d.live_at) return d.live_at;
  const want = version(d.released_in ?? "");
  if (!want) return d.live_at ?? null;
  return ships.find((s) => version(s.v) && compareVersions(version(s.v), want) >= 0)?.at ?? null;
}

/** 이벤트 비율과 다시 온 문의로 판정 한 줄. 순수 함수라 시험에서 바로 봐요. */
export function effectVerdict({ events, before, after, beforeDays, afterDays, back = 0, yes = 0, asks = 0 }) {
  const enough = events && beforeDays >= EFFECT.phoneDays && afterDays >= EFFECT.phoneDays;
  const rb = enough ? before / beforeDays : null;
  const ra = enough ? after / afterDays : null;
  const pct = enough ? (rb ? Math.round(((ra - rb) / rb) * 100) : ra > 0 ? 100 : 0) : null;
  let effect;
  if (pct !== null) effect = pct >= EFFECT.move ? "better" : pct <= -EFFECT.move ? "worse" : "same";
  else if (back > 0) effect = "same";
  else if (yes > 0 || asks > 0) effect = "better";
  else effect = "none";
  // 숫자는 좋아져도 같은 문의가 다시 오면 '그대로'예요.
  if (effect === "better" && back > 0 && back >= yes) effect = "same";
  const why = [pct !== null ? `${events} 쓰는 비율 ${pct > 0 ? "+" : ""}${pct}%` : events ? "사용 통계가 모자라요" : "",
    asks ? `묶인 문의 ${asks}건` : "", yes ? `해결됐어요 ${yes}` : "", back ? `다시 옴 ${back}` : ""].filter(Boolean).join(" · ");
  return { effect, pct, why };
}

/** 매시: 닿은 지 2주가 지난 개발사항의 효과를 적어요. 적은 수. */
export async function effectTick(env, now = Date.now()) {
  const rows = await all(env, `SELECT id, title, route, released_in, live_at, events, size, updated_at FROM dev_items
    WHERE deleted_at IS NULL AND status = 'done' AND effect = '' AND updated_at > ? ORDER BY updated_at LIMIT 200`, now - 180 * DAY).catch(() => []);
  if (!rows.length) return 0;
  const ships = (await all(env, "SELECT target AS v, MIN(at) AS at FROM audit WHERE action = 'shipped_version' GROUP BY target").catch(() => []))
    .filter((s) => version(s.v)).sort((a, b) => a.at - b.at);
  let n = 0;
  for (const d of rows) {
    if (n >= EFFECT.batch) break;
    const at = reachedAt(d, ships);
    if (!at || at + EFFECT.days * DAY > now) continue;
    const span = EFFECT.days * DAY;
    const events = String(d.events ?? "").split(",").filter(Boolean);
    const [base, used, links, hist] = (await batch(env, [
      stmt(env, "SELECT SUM(CASE WHEN day < ? THEN phones ELSE 0 END) AS b, SUM(CASE WHEN day >= ? THEN phones ELSE 0 END) AS a FROM usage_days WHERE day >= ? AND day < ?",
        kstDay(at), kstDay(at), kstDay(at - span), kstDay(at + span)),
      events.length ? stmt(env, `SELECT SUM(CASE WHEN day < ? THEN phones ELSE 0 END) AS b, SUM(CASE WHEN day >= ? THEN phones ELSE 0 END) AS a FROM usage_events
        WHERE day >= ? AND day < ? AND event IN (${events.map(() => "?").join(",")})`, kstDay(at), kstDay(at), kstDay(at - span), kstDay(at + span), ...events)
        : stmt(env, "SELECT 0 AS b, 0 AS a"),
      stmt(env, `SELECT COUNT(*) AS asks, SUM(i.created_at > ?) AS late, SUM(i.fixed = 1) AS yes FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ?`, at + DAY, d.id),
      stmt(env, "SELECT COUNT(*) AS n FROM dev_history WHERE item_id = ? AND action = 'fixed_no' AND at > ?", d.id, at),
    ]).catch(() => [{ results: [{}] }, { results: [{}] }, { results: [{}] }, { results: [{}] }])).map((r) => r.results?.[0] ?? {});
    const v = effectVerdict({ events: events.join(","), before: used.b ?? 0, after: used.a ?? 0, beforeDays: base.b ?? 0, afterDays: base.a ?? 0,
      back: (links.late ?? 0) + (hist.n ?? 0), yes: links.yes ?? 0, asks: links.asks ?? 0 });
    const note = { from: iso(at), days: EFFECT.days, pct: v.pct, why: v.why };
    const r = await run(env, "UPDATE dev_items SET effect = ?, effect_at = ?, effect_note = ? WHERE id = ? AND effect = ''", v.effect, now, JSON.stringify(note), d.id).catch(() => null);
    if (!r?.meta?.changes) continue;
    await batch(env, [devEvent(loopActor(env, "효과 판정"), d.id, now, "effect", { effect: v.effect, why: v.why })]).catch(() => null);
    await audit(env, now, "dev_effect", d.title, `${EFFECT.names[v.effect]}${v.why ? ` · ${v.why}` : ""}`).catch(() => null);
    n++;
  }
  return n;
}

/** 개발사항 화면의 효과 한 줄(아직이면 언제 재는지). */
export function effectOf(r) {
  if (r.status !== "done") return null;
  if (!r.effect) return { key: "", name: "출시 2주 뒤에 재요", tone: "", why: "" };
  let note = {};
  try {
    note = JSON.parse(r.effect_note || "{}");
  } catch {
    note = {};
  }
  return { key: r.effect, name: EFFECT.names[r.effect] ?? r.effect, tone: EFFECT.tones[r.effect] ?? "", why: note.why ?? "", from: note.from ?? null, at: r.effect_at ? iso(r.effect_at) : null };
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
  if (kind === "company" || kind === "billing" || BILLING_RECORD.test(String(text ?? ""))) return false;
  return true;
}

// ---- 화면과 길 ----

// ---- [LOOP-18] 연결 점검 ----

/** [LOOP-32] 트랙 100% 판이 실제로 나갔는지 한 마디로. */
const seenWords = (s) => (s.live ? `실제로 나감(휴대폰 ${s.phones}대${s.play !== undefined ? ` · Play 보고서 ${s.play}대` : ""})`
  : `검토 중이거나 아직 안 나갔을 수 있어요(이 판 휴대폰 ${s.phones}/${s.need}대${s.play !== undefined ? ` · Play 보고서 ${s.play}/${s.playNeed}대` : ""})`);

/**
 * 실제 Play·GitHub에 처음 붙기 전에 한 화면에서: 무엇이 됐고 무엇이 빠졌는지 한 줄씩. play가 참이면 Play 트랙을 한 번 읽어 봐요
 * (편집을 열고 읽고 지움, 확정하지 않음). 값(열쇠·비밀값)은 보이지 않고 있는지만.
 */
export async function loopCheck(env, now, { play = false } = {}) {
  const [sw, train, track, sync, syncKey, opsKey, job] = await Promise.all([
    loopSwitches(env), loopTrain(env), readSetting(env, "play_track", {}),
    one(env, "SELECT * FROM loop_syncs ORDER BY id DESC LIMIT 1").catch(() => null),
    one(env, "SELECT last_at FROM admin_keys WHERE scope = 'sync' AND revoked_at IS NULL AND expires_at > ? ORDER BY created_at DESC LIMIT 1", now).catch(() => null),
    one(env, "SELECT last_at FROM admin_keys WHERE scope = 'ops' AND revoked_at IS NULL AND expires_at > ? ORDER BY created_at DESC LIMIT 1", now).catch(() => null),
    // [LOOP-32] 매시 17분 Play 읽기의 마지막 성공·실패(ops-watch.js job_runs).
    one(env, "SELECT ok_at, fail_at, error FROM job_runs WHERE step = 'play'").catch(() => null),
  ]);
  const lines = [];
  const line = (key, name, state, text, fix = "") => lines.push({ key, name, state, text, ...(fix ? { fix } : {}) });
  if (!playReady(env)) line("play", "Play 출시 읽기", "bad", "서버 비밀값 PLAY_SERVICE_ACCOUNT가 없어요", "사용자 할 일 G10 1장: 서비스 계정에 '앱 정보 보기' 권한");
  else if (play) {
    try {
      const r = await readProduction(env, now);
      const shipped = await shippedVersion(env);
      const fresh = r?.done && (!shipped || compareVersions(version(r.done), version(shipped.v)) > 0);
      const seen = fresh ? await liveSeen(env, r.done, now, shipped?.v) : null;
      line("play", "Play 출시 읽기", "ok", `방금 읽었어요 · Play 트랙 100% ${r?.done ?? "없음"}${fresh ? ` · ${seenWords(seen)}` : ""}${r?.rolling ? ` · 단계적 ${r.rolling.v} ${Math.round((r.rolling.fraction ?? 0) * 100)}%` : ""}`);
      // [LOOP-32] 읽은 것은 남겨요(새로 고쳐도 '한 번도 읽지 않았어요'로 돌아가지 않게). 보류 안내는 매시 작업만 세워요.
      if (r) await saveSetting(env, "play_track", { ...r, ...(seen ? { seen } : {}) });
    } catch (e) {
      line("play", "Play 출시 읽기", "bad", String(e?.message ?? e).slice(0, 200), "Play Console › 사용자 및 권한에서 서비스 계정 권한을 봐요");
    }
  } else if (track.at) line("play", "Play 출시 읽기", "ok", `마지막으로 읽음 ${iso(track.at)} · Play 트랙 100% ${track.done ?? "없음"}${track.seen ? ` · ${seenWords(track.seen)}` : ""}`);
  else line("play", "Play 출시 읽기", "warn", "아직 한 번도 읽지 않았어요", "'Play까지 점검'을 눌러 한 번 읽어 봐요");
  if (playReady(env) && job?.fail_at && job.fail_at > (job.ok_at ?? 0)) {
    line("playJob", "매시 Play 읽기", "bad", `${iso(job.fail_at)}에 실패했어요 · ${String(job.error || "").slice(0, 160)}`, "관리 › 서버 상태의 정기 작업 'play'를 봐요");
  }
  line("autoShip", "스스로 출시 안내", sw.autoShip ? "ok" : "warn", sw.autoShip ? "켜짐 · 100% 출시를 보면 1시간 보류 뒤 안내" : "꺼짐 · '나간 판 저장'을 사람이 눌러요");
  line("syncKey", "동기화 열쇠", syncKey ? "ok" : "bad", syncKey ? (syncKey.last_at ? `있음 · 마지막 사용 ${iso(syncKey.last_at)}` : "있음 · 아직 안 씀") : "없어요",
    syncKey ? "" : "아래 저장소 동기화 › 동기화 열쇠 만들기 → GitHub Secret BAMNAT_SYNC_KEY");
  if (!sw.sync) line("sync", "저장소 동기화", "warn", "받기가 꺼져 있어요");
  else if (!sync) line("sync", "저장소 동기화", "warn", "아직 한 번도 안 왔어요", "GitHub Secret BAMNAT_SYNC_KEY·Variable BAMNAT_API를 넣고 main에 합치면 와요");
  else {
    const r = JSON.parse(sync.result || "{}");
    line("sync", "저장소 동기화", r.errors ? "warn" : "ok", `마지막 ${iso(sync.at)}${sync.pr ? ` · PR #${sync.pr}` : ""}${sync.version ? ` · ${sync.version}` : ""} · ${sync.n}줄${r.errors ? ` · 오류 ${r.errors}` : ""}`,
      r.errors ? "GitHub › Actions › dev-status 로그와 dev-status.jsonl 줄을 봐요" : "");
  }
  line("train", "정기 출시 열차", "ok", `다음 출발 ${nextTrain(train, now)} · ${train.days}일마다`);
  line("update", "인앱 업데이트 기준", sw.urgentMin || sw.flexibleMin ? "ok" : "warn",
    sw.urgentMin || sw.flexibleMin ? `긴급 ${sw.urgentMin || "—"} · 정기 ${sw.flexibleMin || "—"}` : "0이라 업데이트를 권하지 않아요", sw.urgentMin || sw.flexibleMin ? "" : "새 판을 낼 때 정기 기준 versionCode를 넣어요");
  line("push", "관리자 폰 알림", pushReady(env) ? "ok" : "bad", pushReady(env) ? "됨 · 급함 문의는 조용한 시간에도 와요" : "VAPID 비밀값이 없어 급함 문의 알림이 폰에 안 와요", pushReady(env) ? "" : "관리 › 서버 상태의 빠진 설정");
  line("ops", "Claude 운영 열쇠", opsKey ? "ok" : "warn", opsKey ? (opsKey.last_at ? `있음 · 마지막 장부 읽기 ${iso(opsKey.last_at)}` : "있음 · 아직 안 씀") : "없어요 · Claude가 장부를 스스로 읽지 못해요",
    opsKey ? "" : "사용자 할 일 G10 3장");
  const worst = lines.some((x) => x.state === "bad") ? "bad" : lines.some((x) => x.state === "warn") ? "warn" : "ok";
  return { at: iso(now), state: worst, lines, played: Boolean(play && playReady(env)) };
}

// ---- [LOOP-22] 판별 고침 성적표 ----

/** 최근 판마다: 고친 개발사항·묶인 문의·안내한 문의·해결됐어요·아직이에요(다시 열림)·나도 겪어요·첫 답까지 평균 시간. */
export async function scoreCard(env, most = 6) {
  const base = await all(env, `SELECT d.released_in AS v, COUNT(DISTINCT d.id) AS items, COUNT(DISTINCT l.inquiry_id) AS asks,
      COUNT(DISTINCT CASE WHEN l.told_at IS NOT NULL THEN l.inquiry_id END) AS told,
      COUNT(DISTINCT CASE WHEN i.fixed = 1 THEN i.id END) AS yes,
      AVG((SELECT MIN(m.at) FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin') - i.created_at) AS firstMs
    FROM dev_items d LEFT JOIN dev_links l ON l.item_id = d.id LEFT JOIN inquiries i ON i.id = l.inquiry_id
    WHERE d.deleted_at IS NULL AND d.status = 'done' AND d.released_in IS NOT NULL AND d.released_in != '' GROUP BY d.released_in`).catch(() => []);
  const hist = await all(env, `SELECT d.released_in AS v, h.action, COUNT(*) AS n FROM dev_history h JOIN dev_items d ON d.id = h.item_id
    WHERE d.deleted_at IS NULL AND d.status = 'done' AND h.action IN ('fixed_no', 'me_too') GROUP BY d.released_in, h.action`).catch(() => []);
  const count = (v, a) => hist.find((h) => h.v === v && h.action === a)?.n ?? 0;
  return base.filter((r) => version(r.v)).sort((a, b) => compareVersions(version(b.v), version(a.v))).slice(0, most).map((r) => {
    const no = count(r.v, "fixed_no");
    return { version: r.v, items: r.items, asks: r.asks, told: r.told, yes: r.yes, no, meToo: count(r.v, "me_too"),
      solved: r.yes + no ? Math.round((r.yes / (r.yes + no)) * 100) : null, firstHours: r.firstMs == null ? null : Math.round((r.firstMs / HOUR) * 10) / 10 };
  });
}

/** GET /loop: 흐름 화면의 자동 순환 칸. */
export async function loopState(env, now) {
  // [ADM3-52] 아래 읽기도 모두 한꺼번에(실서버 자동 순환 p50 3.2초였어요).
  const [sw, train, track, ships, syncs, shipped, urgent, notLive, keys, scores, auto, rewards] = await Promise.all([
    loopSwitches(env), loopTrain(env), readSetting(env, "play_track", {}),
    all(env, "SELECT * FROM loop_ships ORDER BY seen_at DESC LIMIT 6").catch(() => []),
    all(env, "SELECT * FROM loop_syncs ORDER BY id DESC LIMIT 8").catch(() => []),
    shippedVersion(env),
    one(env, `SELECT COUNT(*) AS n FROM inquiries i WHERE i.status = 'open' AND i.kind IN ('bug', 'problem')
      AND (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) = 'user'`).catch(() => ({ n: 0 })),
    all(env, `SELECT id, title, route, remote, flag FROM dev_items WHERE deleted_at IS NULL AND status = 'done' AND live_at IS NULL AND route IN ('server', 'remote', 'flag') LIMIT 30`).catch(() => []),
    all(env, "SELECT id, label, scope, last_at, expires_at, revoked_at FROM admin_keys WHERE scope = 'sync' ORDER BY created_at DESC LIMIT 3").catch(() => []),
    scoreCard(env), autonomy(env), rewardRules(env),
  ]);
  // 2.0.0 [RVA-05] 검토 ADM-06: 인앱 업데이트 기준 칸 옆에 '지금 나간 번호'와 최근 7일 폰이 어느 번호에 있는지. 판 번호는 옛 표 + 문의가 실어 온
  // '1.39.0 (53)' + Play 트랙에서 읽은 번호로 알아요(원격 파일 화면과 같은 방법). 모르는 판은 code null.
  const [apps, learned] = await Promise.all([
    all(env, "SELECT app, SUM(phones) AS phones FROM usage_days WHERE day >= ? GROUP BY app", kstDay(now - 7 * DAY)).catch(() => []),
    all(env, "SELECT DISTINCT json_extract(info, '$.app') AS app FROM inquiries WHERE json_valid(info) AND created_at > ? LIMIT 200", now - 90 * DAY).catch(() => []),
  ]);
  const codes = { ...APP_CODES };
  for (const { app } of learned) {
    const m = /^(\d{1,2}\.\d{1,3}\.\d{1,3}) \((\d{1,6})\)$/.exec(app ?? "");
    if (m) codes[m[1]] = Number(m[2]);
  }
  const liveCode = Math.min(...(track.doneCodes ?? []).map(Number).filter((c) => Number.isInteger(c) && c > 0 && c < 100_000));
  const liveName = track.done ?? shipped?.v ?? null;
  if (liveName && Number.isFinite(liveCode)) codes[liveName] = liveCode;
  return {
    now: iso(now), switches: sw, train: { ...train, next: nextTrain(train, now) }, play: { ready: Boolean(playReady(env)), ...track, at: track.at ? iso(track.at) : null },
    shipped, ships: ships.map((s) => ({ version: s.version, status: s.status, seenAt: iso(s.seen_at), until: iso(s.until), doneAt: s.done_at ? iso(s.done_at) : null, by: s.by, result: s.result })),
    syncs: syncs.map((s) => ({ at: iso(s.at), sha: s.sha, version: s.version, pr: s.pr, n: s.n, result: JSON.parse(s.result || "{}") })),
    waitingLive: notLive.map((d) => ({ id: d.id, title: d.title, route: d.route, routeName: LOOP.routes[d.route], want: d.route === "remote" ? d.remote : d.route === "flag" ? d.flag : "다음 서버 배포" })),
    syncKeys: keys.map((k) => ({ id: k.id, label: k.label, lastAt: k.last_at ? iso(k.last_at) : null, alive: !k.revoked_at && k.expires_at > now })),
    urgentWaiting: urgent?.n ?? 0, scores, autonomy: auto, rewards, routes: LOOP.routes, sizes: LOOP.sizes,
    live: { name: liveName, code: liveName ? codes[liveName] ?? null : null, phones: apps.map((a) => ({ app: a.app, code: codes[a.app] ?? null, phones: a.phones })) },
  };
}

/** /loop… 길. 없는 길이면 null. [answer](env, inquiryId, text, now)는 '자동 정리'로 답하기. */
export async function loopRoute(request, env, url, path, method, now, { answer, ledgerQuery }) {
  if (path === "/loop" && method === "GET") return json(await loopState(env, now));
  if (path === "/loop/queue" && method === "GET") return json({ items: await devQueue(env, Math.min(200, Number(url.searchParams.get("limit")) || 60)) });
  if (path === "/loop/decide" && method === "GET") return json(await decideBox(env, env.actor, now, ledgerQuery));
  if (path === "/loop/tiers" && method === "GET") return json(await tierBoard(env, now, url.searchParams));
  if (path === "/loop/autonomy" && method === "GET") return json(await autonomy(env));
  if (path === "/loop/check" && method === "GET") return json(await loopCheck(env, now, { play: url.searchParams.get("play") === "1" && !env.actor?.key }));
  let m = path.match(/^\/loop\/ship\/(\d{1,2}\.\d{1,2}\.\d{1,3})\/(cancel|now|live)$/);
  if (m && method === "POST") {
    if (env.actor?.key) return json({ error: "no_permission" }, 403);
    // [LOOP-32] '검토 중일 수 있음' 판은 멈추기와 '게시됨 확인'(사람이 Play Console에서 본 뒤)만 돼요.
    const row = await one(env, "SELECT * FROM loop_ships WHERE version = ? AND status IN ('review', 'waiting')", m[1]);
    if (!row || (m[2] === "now" && row.status !== "waiting") || (m[2] === "live" && row.status !== "review")) return json({ error: "not_found" }, 404);
    if (m[2] === "live") {
      await holdShip(env, m[1], now, `${actorName(env)}님이 Play Console에서 게시를 확인`);
      return json({ version: m[1], status: "waiting", until: iso(now + LOOP.shipHold) });
    }
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
  m = path.match(/^\/loop\/basis\/(d[A-Za-z0-9]{15})$/);
  if (m && method === "POST") {
    const r = await setBasis(request, env, m[1], now);
    return r ?? json({ ok: true });
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
