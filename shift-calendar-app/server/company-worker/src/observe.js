import { audit, batch, can, json, one, playVitals, randomId, readJson, refuse, run, stmt } from "./index.js";
import { DEV, actorName, devEvent, plainText } from "./inquiries.js";
import { playReady, playToken } from "./money.js";
import { serverEval, flagStore } from "./flags.js";
import { EVENTS, SETUP_STEPS, kstDay } from "./usage.js";
import { retention } from "./glance.js";
import { MISS_PLACES } from "./growth.js";
import { DAY, HOUR } from "./units.js";

// ---- 1.39 [OBS2-01~08] 관측: 앱 전체 사용자의 유입·활성·이탈·품질과, 거기서 찾은 개선 신호 ----
//
// 사용자 2026-11-08 "이제 사용자가 어떤 상황이고 어플은 잘 되고 있는지 사용자 유입 이탈 등 모든 상황을 관측할 수 있는 시스템",
// "관측 통계 부분을 빈틈 없이 만들어 … 문의 개선요청 + 통계를 통한 완전한 자가 발전", "수집할 데이터나 통계치를 더 만들어서 …
// 통찰도". 범위는 관측 카드(11-08 11:59)에서 사용자가 고른 B: 처리방침을 바꾸지 않는 것(Play가 주는 숫자, 서버 요청 합계)
// + 첫 설정에서 사용 통계를 묻기(앱 StatsAsk). 유형별 흐름처럼 새로 모으는 것은 '관측 빈틈'에 적고 사용자에게 물어요.
//
// 출처가 셋이라 숫자마다 어디서 왔는지와 얼마나 늦는지를 같이 적어요.
//   Play 보고서 [OBS2-01]  Play Console이 Cloud Storage에 매일 두는 CSV(설치·삭제·설치 기기, 판별, 스토어 방문·설치 전환과 출처,
//                        별점). 모든 휴대폰, 1~2일 늦음, 날짜는 Play 기준. 서비스 계정에 'Play 앱 정보 보기·대량 보고서 다운로드'
//                        권한과 PLAY_REPORTS_BUCKET(보고서 저장소 이름)이 있어야 해요.
//   Play vitals [OBS2-02] Android 진단을 켠 휴대폰의 하루 사용자 수(distinctUsers)와 비정상 종료·ANR. 이미 정상도가 읽던 것을 날마다로.
//   서버 [OBS2-03]        앱이 하루 한 번 이상 부르는 /api/config 요청 수(req_stats, 14일). 휴대폰을 구별하지 않아 '대수'가 아니라
//                        '요청 수'예요. 늦지 않아서 오늘 무슨 일이 생겼는지는 이것이 먼저 보여요.
//   표본               '사용 통계 보내기'를 켠 휴대폰(usage_days·usage_events). 머묾·기능 쓰임은 여기서만 보여요.
//
// 개선 신호 [OBS2-04]: 위 숫자에서 규칙으로 찾은 문제(삭제 급증, 전환 하락, 새 판으로 안 넘어옴, 기능 쓰임 반토막, 찾았는데 없던 말 …).
// 매시 작업이 settings 'obs_signals'에 쌓고, 관리자 앱과 Claude 열쇠가 '개발사항 만들기·지켜보기·아님'으로 처리해요. 개발사항이 된
// 신호는 그 숫자가 사흘 동안 다시 안 나타나면 '좋아짐'과 그때 값을 남겨, 고친 것이 숫자로 확인돼요(자가 발전의 마지막 칸).
// 관측 빈틈 [OBS2-05]: 지금 숫자로 답하지 못하는 질문과 그 답에 필요한 데이터·처리방침 여부, 효과를 잴 이벤트 없이 나간 개발사항.

export const OBS = {
  days: 28,
  /** 1.39 [OBS2-08] 유형별 흐름·기능과 머묾은 61일(30일째 머묾과 30일 창이 끝난 날까지). */
  longDays: 61,
  /** [OBS2-09] Play 권한은 저장한 뒤 퍼지는 데 하루까지 걸려요: 처음 막힌 뒤 이만큼은 '기다리는 것'으로. */
  permWaitMs: 26 * HOUR,
  /** [OBS2-09] '지금 다시 확인'은 이보다 자주 Google에 묻지 않아요. */
  refreshMs: 2 * 60 * 1000,
  /** 기능과 머묾: 쓴 휴대폰이 이보다 적은 기능은 비율을 보이지 않아요. */
  keepMin: 20,
  /** 유형별 흐름 신호: 7일째를 알 수 있는 처음 연 휴대폰이 이만큼 있어야 견줘요. */
  kindMin: 30,
  scope: "https://www.googleapis.com/auth/devstorage.read_only",
  storage: "https://storage.googleapis.com/storage/v1/b",
  /** 보고서는 하루 한 번 바뀌어요: 12시간(실패는 1시간) 둬요. */
  keepMs: 12 * HOUR,
  failMs: HOUR,
  /** 보고서 날짜 줄은 이만큼 남겨요(석 달 파일을 읽어 합쳐요). */
  keepDays: 70,
  months: 3,
  /** 개선 신호: 최대 수, 끝난(좋아짐·아님) 신호를 지우는 날, 안 보인 지 이만큼이면 '좋아짐'. */
  signalsMax: 60,
  signalDoneDays: 30,
  clearMs: 3 * DAY,
  /** 출처 순위 칸 수, 기능 쓰임·찾았는데 없던 말 신호 수. */
  sources: 8,
  featureSignals: 3,
  missSignals: 5,
  timeoutMs: 8000,
};

const OBS_FILES = {
  installs: (pkg, ym) => `stats/installs/installs_${pkg}_${ym}_overview.csv`,
  versions: (pkg, ym) => `stats/installs/installs_${pkg}_${ym}_app_version.csv`,
  store: (pkg, ym) => `stats/store_performance/store_performance_${pkg}_${ym}_traffic_source.csv`,
  ratings: (pkg, ym) => `stats/ratings/ratings_${pkg}_${ym}_overview.csv`,
};

const obsRound1 = (v) => (v === null || v === undefined || !Number.isFinite(v) ? null : Math.round(v * 10) / 10);
const obsSum = (a) => a.reduce((n, v) => n + (v ?? 0), 0);
const obsAvg = (a) => (a.length ? obsSum(a) / a.length : 0);

/** PLAY_REPORTS_BUCKET: 'pubsite_prod_0123…'(숫자는 개발자 계정 ID, 옛 계정은 pubsite_prod_rev_…), 'gs://pubsite_prod_0123…/stats/installs/' 어느 쪽이든 저장소 이름만. 아니면 null. */
export function obsBucket(env) {
  const raw = typeof env.PLAY_REPORTS_BUCKET === "string" ? env.PLAY_REPORTS_BUCKET.trim() : "";
  const name = raw.replace(/^gs:\/\//, "").split("/")[0];
  return /^[a-z0-9][a-z0-9._-]{2,221}$/.test(name) ? name : null;
}

/** Play 보고서 CSV는 UTF-16LE(BOM 있음)예요. BOM 없는 UTF-16과 UTF-8도 읽어요. */
export function obsText(buf) {
  const b = new Uint8Array(buf);
  if (b[0] === 0xff && b[1] === 0xfe) return new TextDecoder("utf-16le").decode(b.subarray(2));
  if (b[0] === 0xfe && b[1] === 0xff) return new TextDecoder("utf-16be").decode(b.subarray(2));
  if (b.length > 3 && b[1] === 0 && b[3] === 0) return new TextDecoder("utf-16le").decode(b);
  return new TextDecoder("utf-8").decode(b).replace(/^﻿/, "");
}

/** CSV → 줄마다 { 머리글(소문자): 값 }. 따옴표 안 쉼표·줄바꿈과 "" 를 읽어요. */
export function obsCsv(text) {
  const rows = [];
  let row = [];
  let cell = "";
  let quoted = false;
  const s = String(text ?? "");
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (quoted) {
      if (c === '"' && s[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') quoted = false;
      else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === ",") {
      row.push(cell);
      cell = "";
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && s[i + 1] === "\n") i++;
      row.push(cell);
      cell = "";
      if (row.some((x) => x !== "")) rows.push(row);
      row = [];
    } else cell += c;
  }
  row.push(cell);
  if (row.some((x) => x !== "")) rows.push(row);
  const head = (rows.shift() ?? []).map((h) => h.trim().toLowerCase());
  return rows.map((r) => Object.fromEntries(head.map((h, i) => [h, (r[i] ?? "").trim()])));
}

/** 줄에서 먼저 있는 머리글의 숫자('1,234', '12.5%'). 없거나 숫자가 아니면 null. */
export function obsNum(row, ...names) {
  for (const n of names) {
    if (!(n in row)) continue;
    const v = Number(String(row[n]).replace(/[,%\s]/g, ""));
    if (String(row[n]).trim() !== "" && Number.isFinite(v)) return v;
  }
  return null;
}

/** Play 보고서의 날짜('2026-11-06', 'Nov 6, 2026') → '2026-11-06'. 못 읽으면 null. */
export function obsDay(v) {
  const s = String(v ?? "").trim();
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
  const t = Date.parse(`${s} UTC`);
  return Number.isFinite(t) ? new Date(t).toISOString().slice(0, 10) : null;
}

/** 'YYYYMM' 목록: 이번 달부터 [n]달 전까지(UTC). */
export function obsMonths(now, n = OBS.months) {
  const d = new Date(now);
  const out = [];
  for (let i = 0; i < n; i++) {
    const m = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - i, 1));
    out.push(`${m.getUTCFullYear()}${String(m.getUTCMonth() + 1).padStart(2, "0")}`);
  }
  return out;
}

/**
 * 보고서 CSV 묶음 → 날짜 줄·출처·판. [files]는 { installs: [csvText…], versions, store, ratings }(없는 달은 빠져요).
 * 같은 날짜가 여러 줄이면 더해요(출처 파일은 날짜×출처마다 한 줄).
 */
export function obsParse(files, keepDays = OBS.keepDays) {
  const days = new Map();
  const dayOf = (d) => {
    if (!days.has(d)) days.set(d, { day: d, installs: null, uninstalls: null, base: null, visitors: null, acq: null, rating: null });
    return days.get(d);
  };
  const add = (o, k, v) => {
    if (v !== null) o[k] = (o[k] ?? 0) + v;
  };
  for (const text of files.installs ?? []) {
    for (const r of obsCsv(text)) {
      const d = obsDay(r.date);
      if (!d) continue;
      const o = dayOf(d);
      add(o, "installs", obsNum(r, "install events", "daily device installs", "daily user installs"));
      add(o, "uninstalls", obsNum(r, "uninstall events", "daily device uninstalls", "daily user uninstalls"));
      const base = obsNum(r, "active device installs", "total user installs");
      if (base !== null) o.base = base;
    }
  }
  const bySource = new Map();
  const storeRows = [];
  for (const text of files.store ?? []) {
    for (const r of obsCsv(text)) {
      const d = obsDay(r.date);
      if (!d) continue;
      const visitors = obsNum(r, "store listing visitors");
      const acq = obsNum(r, "store listing acquisitions");
      const o = dayOf(d);
      add(o, "visitors", visitors);
      add(o, "acq", acq);
      const utm = (r["utm source"] ?? "").trim();
      const source = utm && utm !== "-" ? `utm:${utm}` : (r["traffic source"] || "기타").trim();
      storeRows.push({ day: d, source, visitors: visitors ?? 0, acq: acq ?? 0 });
    }
  }
  for (const text of files.ratings ?? []) {
    for (const r of obsCsv(text)) {
      const d = obsDay(r.date);
      if (!d) continue;
      const v = obsNum(r, "daily average rating");
      if (v !== null && v > 0) dayOf(d).rating = v;
      const total = obsNum(r, "total average rating");
      if (total !== null && total > 0) dayOf(d).total = total;
    }
  }
  const versionRows = [];
  for (const text of files.versions ?? []) {
    for (const r of obsCsv(text)) {
      const d = obsDay(r.date);
      const code = obsNum(r, "app version code");
      if (!d || code === null) continue;
      versionRows.push({ day: d, code, active: obsNum(r, "active device installs"), installs: obsNum(r, "install events", "daily device installs", "daily device upgrades"),
        uninstalls: obsNum(r, "uninstall events", "daily device uninstalls", "daily user uninstalls"),
        // [LOOP-33] 그날 이 판을 새로 깔거나 이 판으로 올린 기기(동의와 상관없이 Play가 세요): 자동 순환이 '실제로 나갔는지' 봐요.
        moved: (obsNum(r, "install events", "daily device installs") ?? 0) + (obsNum(r, "daily device upgrades") ?? 0) });
    }
  }
  const list = [...days.values()].sort((a, b) => (a.day < b.day ? -1 : 1)).slice(-keepDays);
  const lastDay = list.at(-1)?.day ?? null;
  // 출처: 마지막 28일 합, 방문 많은 순.
  const from = lastDay ? new Date(Date.parse(`${lastDay}T00:00:00Z`) - 27 * DAY).toISOString().slice(0, 10) : null;
  for (const r of storeRows) {
    if (from && r.day < from) continue;
    const x = bySource.get(r.source) ?? { source: r.source, visitors: 0, acq: 0 };
    x.visitors += r.visitors;
    x.acq += r.acq;
    bySource.set(r.source, x);
  }
  const sources = [...bySource.values()].sort((a, b) => b.visitors - a.visitors || b.acq - a.acq).slice(0, OBS.sources);
  // 판: 가장 최근 날짜의 판별 설치 기기, 판마다 처음 보인 날.
  const first = {};
  for (const r of versionRows) if ((r.active ?? r.installs ?? 0) > 0 && (!first[r.code] || r.day < first[r.code])) first[r.code] = r.day;
  const vDay = versionRows.reduce((m, r) => (r.day > m ? r.day : m), "");
  // [OBS2-09] 판마다 최근 7일 삭제: '어느 판에서 지우는지'(지운 이유 대신, 동의 없이 Play가 세는 것).
  const out7 = new Map();
  const from7 = vDay ? new Date(Date.parse(`${vDay}T00:00:00Z`) - 6 * DAY).toISOString().slice(0, 10) : "";
  for (const r of versionRows) if (r.day >= from7 && r.uninstalls) out7.set(r.code, (out7.get(r.code) ?? 0) + r.uninstalls);
  const vRows = versionRows.filter((r) => r.day === vDay).map((r) => ({ code: r.code, n: r.active ?? r.installs ?? 0, out7: out7.get(r.code) ?? 0 }))
    .sort((a, b) => b.code - a.code);
  // [LOOP-33] 최근 21일, 판마다 하루 새로 깔거나 올린 기기 [날, 판 번호, 대수].
  const from21 = vDay ? new Date(Date.parse(`${vDay}T00:00:00Z`) - 20 * DAY).toISOString().slice(0, 10) : "";
  const moves = versionRows.filter((r) => r.day >= from21 && r.moved > 0).map((r) => [r.day, r.code, r.moved]);
  return { days: list, lastDay, sources, versions: vDay ? { day: vDay, rows: vRows, first, moves } : null };
}

const reportsKept = new WeakMap();

async function obsGet(token, bucket, path) {
  const res = await fetch(`${OBS.storage}/${encodeURIComponent(bucket)}/o/${encodeURIComponent(path)}?alt=media`, {
    headers: { authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(OBS.timeoutMs),
  });
  if (res.status === 404) return null;
  if (res.status === 401 || res.status === 403) throw Object.assign(new Error("denied"), { code: "denied" });
  if (!res.ok) throw Object.assign(new Error("unreachable"), { code: "unreachable" });
  return obsText(await res.arrayBuffer());
}

/**
 * [OBS2-01] Play 보고서: 서비스 계정이나 저장소 이름이 없으면 { state: "off" | "nobucket" }. 받은 것은 메모리와 settings
 * 'play_reports'에 12시간(실패 1시간). 이전에 받은 날짜 줄과 합쳐 70일까지 남겨요.
 */
export async function playReports(env, now = Date.now(), force = false) {
  const account = playReady(env);
  if (!account) return { state: "off" };
  const bucket = obsBucket(env);
  if (!bucket) return { state: "nobucket" };
  const key = env.DB ?? env;
  const kept = reportsKept.get(key);
  if (!force && kept && kept.until > now) return kept.value;
  const saved = await one(env, "SELECT value FROM settings WHERE key = 'play_reports'").catch(() => null);
  let before = null;
  try {
    before = saved ? JSON.parse(saved.value) : null;
  } catch {
    before = null;
  }
  if (!force && before?.until > now && before.value) {
    reportsKept.set(key, before);
    return before.value;
  }
  const pkg = env.PLAY_PACKAGE || "com.bamnat.app";
  let value;
  try {
    const token = await playToken(env, account, now, OBS.scope);
    const months = obsMonths(now);
    const jobs = [];
    for (const [kind, name] of Object.entries(OBS_FILES)) for (const ym of months) jobs.push(obsGet(token, bucket, name(pkg, ym)).then((text) => [kind, text]));
    const files = { installs: [], versions: [], store: [], ratings: [] };
    for (const [kind, text] of await Promise.all(jobs)) if (text !== null) files[kind].push(text);
    const got = obsParse(files);
    // 지난번 날짜 줄 중 이번에 없는 날(석 달보다 앞)은 이어 붙여요.
    const old = (before?.value?.days ?? []).filter((d) => !got.days.some((x) => x.day === d.day));
    const days = [...old, ...got.days].sort((a, b) => (a.day < b.day ? -1 : 1)).slice(-OBS.keepDays);
    value = { state: "ok", at: now, okSince: before?.value?.state === "ok" ? before.value.okSince ?? now : now, days, lastDay: days.at(-1)?.day ?? null, sources: got.sources, versions: got.versions,
      files: Object.fromEntries(Object.entries(files).map(([k, v]) => [k, v.length > 0])) };
  } catch (e) {
    const state = e?.code === "denied" ? "denied" : "unreachable";
    // [OBS2-09] 처음 막힌 때: 권한을 막 저장했다면 퍼지는 동안(하루까지)은 할 일이 아니라 기다리는 것이에요.
    const deniedSince = state === "denied" ? (before?.value?.state === "denied" && before.value.deniedSince) || now : null;
    value = { state, at: now, ...(deniedSince ? { deniedSince } : {}), ...(before?.value?.days ? { days: before.value.days, lastDay: before.value.lastDay,
      sources: before.value.sources, versions: before.value.versions, stale: true } : {}) };
  }
  const keep = { value, until: now + (value.state === "ok" ? OBS.keepMs : OBS.failMs) };
  reportsKept.set(key, keep);
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('play_reports', ?)", JSON.stringify(keep)).catch(() => {});
  return value;
}

/** 날 목록(KST) [n]일, 어제까지. */
function obsDays(now, n = OBS.days) {
  const out = [];
  for (let i = n; i >= 1; i--) out.push(kstDay(now - i * DAY));
  return out;
}

/** 'YYYY-MM-DD'에서 [n]일 앞. */
const obsBack = (day, n) => new Date(Date.parse(`${day}T00:00:00Z`) - n * DAY).toISOString().slice(0, 10);

/** 끝 날 [end]까지 [n]일의 Play 날짜 목록. */
function obsPlayDays(end, n = OBS.days) {
  if (!end) return [];
  const out = [];
  for (let i = n - 1; i >= 0; i--) out.push(obsBack(end, i));
  return out;
}

/** 최근 [a]개 평균과 그 앞 [b]개 평균. */
function obsWindow(series, a, b) {
  const s = series.filter((v) => v !== null && v !== undefined);
  return { now: obsAvg(s.slice(-a)), before: obsAvg(s.slice(-(a + b), -a)), n: s.length };
}

/**
 * [OBS2-04] 개선 신호 규칙. 규칙마다 { kind, subject, on(지금 문제인지), value(지금 값), unit, better('up'|'down'), base, tone, title, why,
 * dev(개발사항 제목 추천), to }. on이 아니어도 값을 돌려줘서, 개발사항이 된 신호가 사라질 때 '그때 값'을 남겨요.
 */
export function obsRules(b) {
  const out = [];
  const push = (x) => out.push({ subject: "", tone: "warn", unit: "", ...x });
  const p = b.play;
  if (p?.days?.length) {
    const un = p.days.map((d) => d.uninstalls);
    const w = obsWindow(un, 3, 14);
    if (w.n >= 7) {
      push({ kind: "uninstall-spike", value: obsRound1(w.now), base: obsRound1(w.before), better: "down", unit: "/일",
        on: w.now >= 3 && w.now >= Math.max(1, w.before) * 2, tone: "bad",
        title: `앱 삭제가 늘었어요: 최근 3일 하루 ${obsRound1(w.now)}, 그 전 2주 ${obsRound1(w.before)}`,
        why: "Play 보고서의 하루 삭제 수예요. 새 판·알림·광고 직후라면 그 변화부터 봐요.",
        dev: "앱 삭제가 늘어난 이유 찾기(최근 판·알림·광고 점검)", to: "#/glance" });
    }
    const last7 = p.days.slice(-7);
    const ins = obsSum(last7.map((d) => d.installs));
    const uns = obsSum(last7.map((d) => d.uninstalls));
    if (last7.length >= 7) {
      push({ kind: "net-loss", value: ins - uns, base: 0, better: "up", unit: "대",
        on: uns >= 5 && uns > ins, title: `최근 7일 설치 ${ins}, 삭제 ${uns}: 쓰는 휴대폰이 줄고 있어요`,
        why: "삭제가 설치보다 많으면 유입보다 이탈을 먼저 고쳐요.", dev: "설치보다 삭제가 많은 이유 찾기(첫 1분·첫 주 경험)", to: "#/glance" });
    }
    const vis = p.days.map((d) => d.visitors);
    const acq = p.days.map((d) => d.acq);
    const v7 = obsSum(vis.slice(-7));
    const a7 = obsSum(acq.slice(-7));
    const v21 = obsSum(vis.slice(-28, -7));
    const a21 = obsSum(acq.slice(-28, -7));
    if (v7 && v21) {
      const now = (a7 / v7) * 100;
      const before = (a21 / v21) * 100;
      push({ kind: "store-conv", value: obsRound1(now), base: obsRound1(before), better: "up", unit: "%",
        on: v7 >= 30 && before > 0 && now < before * 0.7,
        title: `스토어 방문 → 설치가 ${obsRound1(now)}%로 떨어졌어요(그 전 3주 ${obsRound1(before)}%)`,
        why: "스토어 등록정보(첫 사진·짧은 설명)나 별점이 바뀌었는지 봐요.", dev: "스토어 등록정보 전환 점검(첫 사진·짧은 설명·별점)", to: "#/glance" });
    }
    const rated = p.days.slice(-7).map((d) => d.rating).filter((v) => v);
    if (rated.length >= 3) {
      const r = obsAvg(rated);
      push({ kind: "rating-low", value: obsRound1(r), base: 3.5, better: "up", unit: "점", on: r < 3.5,
        title: `최근 7일 별점 평균 ${obsRound1(r)}점`, why: "낮은 별점 리뷰에 답하고, 같은 불만을 개발사항으로 묶어요.",
        dev: "낮은 별점 리뷰의 공통 불만 고치기", to: "#/reviews" });
    }
    const v = p.versions;
    if (v?.rows?.length) {
      const total = obsSum(v.rows.map((r) => r.n));
      const top = v.rows[0];
      const since = v.first?.[top.code];
      const share = total ? (top.n / total) * 100 : 0;
      const age = since ? Math.round((Date.parse(`${v.day}T00:00:00Z`) - Date.parse(`${since}T00:00:00Z`)) / DAY) : 0;
      push({ kind: "version-stuck", subject: String(top.code), value: obsRound1(share), base: 50, better: "up", unit: "%",
        on: total >= 10 && age >= 7 && share < 50,
        title: `새 판(${top.code})으로 넘어온 휴대폰이 ${obsRound1(share)}%예요(나온 지 ${age}일)`,
        why: "인앱 업데이트 권하기(LOOP-13)가 그 판에 걸려 있는지, Play 단계적 출시가 멈췄는지 봐요.",
        dev: `새 판 ${top.code}로 넘어오게 하기(인앱 업데이트 권하기 점검)`, to: "#/health" });
    }
  }
  const a = b.active;
  if (a?.play?.length) {
    const w = obsWindow(a.play.map((d) => d.n), 7, 7);
    if (w.n >= 14) {
      push({ kind: "active-drop", value: obsRound1(w.now), base: obsRound1(w.before), better: "up", unit: "명",
        on: w.before >= 10 && w.now <= w.before * 0.7, tone: "bad",
        title: `하루 사용자가 ${obsRound1(w.now)}명으로 줄었어요(그 전 주 ${obsRound1(w.before)}명)`,
        why: "Play가 본 하루 사용자(Android 진단을 켠 휴대폰)예요. 판·알림·서버 오류를 같이 봐요.", dev: "하루 사용자 감소 원인 찾기", to: "#/glance" });
    }
  }
  if (a?.config?.length) {
    const w = obsWindow(a.config.map((d) => d.n), 3, 10);
    if (w.n >= 10) {
      push({ kind: "config-drop", value: obsRound1(w.now), base: obsRound1(w.before), better: "up", unit: "번/일",
        on: w.before >= 30 && w.now <= w.before * 0.6, tone: "bad",
        title: `앱이 서버에 오는 횟수가 하루 ${obsRound1(w.now)}번으로 줄었어요(그 전 ${obsRound1(w.before)}번)`,
        why: "늦지 않는 숫자라 오늘 일어난 일을 먼저 보여요. 앱이 서버에 닿지 못하는지(오류·주소)도 봐요.",
        dev: "앱 → 서버 요청 감소 원인 찾기(연결·판·사용자)", to: "#/server" });
    }
  }
  const v = b.quality?.vitals;
  if (v && !v.error) {
    if (v.crash !== null && v.crash !== undefined) {
      push({ kind: "crash", value: v.crash, base: 0.5, better: "down", unit: "%", on: v.crash >= 0.5, tone: v.crash >= 1.09 ? "bad" : "warn",
        title: `비정상 종료 ${v.crash}%(28일, Play)`, why: "판 건강에서 어느 판·기기에서 늘었는지 봐요.", dev: "비정상 종료 줄이기(판 건강의 상위 원인)", to: "#/health" });
    }
    if (v.anr !== null && v.anr !== undefined) {
      push({ kind: "anr", value: v.anr, base: 0.2, better: "down", unit: "%", on: v.anr >= 0.2, tone: v.anr >= 0.47 ? "bad" : "warn",
        title: `ANR ${v.anr}%(28일, Play)`, why: "앱이 멈춰 '응답 없음'이 뜬 비율이에요.", dev: "ANR 줄이기(판 건강의 상위 원인)", to: "#/health" });
    }
  }
  const s = b.sample;
  if (s?.keep?.d1?.base >= 30) {
    push({ kind: "keep-d1", value: s.keep.d1.rate, base: 30, better: "up", unit: "%", on: s.keep.d1.rate < 30,
      title: `처음 연 다음 날 다시 연 휴대폰이 ${s.keep.d1.rate}%예요`, why: "첫 설정 뒤 첫날 경험(달력이 찼는지, 알람이 맞는지)을 봐요. 사용 통계 표본이에요.",
      dev: "첫날 머묾 올리기(첫 설정 끝·첫 알람 점검)", to: "#/glance" });
  }
  // 1.39 [OBS2-08] 유형별 흐름: 한 유형의 7일째 머묾이 전체의 70% 아래.
  const k = b.kinds;
  if (k?.all?.d7?.rate !== null && k?.all?.d7?.rate !== undefined && k.all.d7.base >= OBS.kindMin) {
    for (const r of k.rows) {
      if ((r.d7?.base ?? 0) < OBS.kindMin || r.d7.rate === null) continue;
      push({ kind: "kind-keep-low", subject: r.style, value: r.d7.rate, base: k.all.d7.rate, better: "up", unit: "%", on: r.d7.rate < k.all.d7.rate * 0.7, tone: "bad",
        title: `${r.name} 유형의 7일째 머묾이 ${r.d7.rate}%로 전체 ${k.all.d7.rate}%보다 낮아요`,
        why: "그 유형의 첫 설정·첫 주에 맞지 않는 말·기능이 있는지 봐요(유형별 둘러보기). 사용 통계 표본이에요.",
        dev: `${r.name} 유형 첫 주 이탈 줄이기(첫 설정·홈·알람 둘러보기)`, to: "#/glance" });
    }
  }
  for (const f of (s?.features ?? []).slice(0, OBS.featureSignals)) {
    push({ kind: "feature-fall", subject: f.event, value: f.now, base: f.before, better: "up", unit: "대", on: true,
      title: `'${f.name}' 쓰는 휴대폰이 반 넘게 줄었어요(7일 ${f.now}, 그 전 ${f.before})`,
      why: "최근 판에서 그 기능 가는 길·모양이 바뀌었는지 봐요. 사용 통계 표본이에요.", dev: `'${f.name}' 쓰임이 줄어든 이유 찾기`, to: "#/metrics" });
  }
  for (const m of (b.voice?.misses ?? []).slice(0, OBS.missSignals)) {
    push({ kind: "search-miss", subject: `${m.place}:${m.word}`, value: m.n, base: 3, better: "down", unit: "번", on: m.n >= 3,
      title: `'${m.word}'를 찾았는데 없었어요 ${m.n}번(${m.placeName ?? m.place})`, why: "사용자가 직접 찾다가 못 찾은 말이에요. 기능이 없거나, 있는데 이름이 달라요.",
      dev: `'${m.word}' 찾기 길 만들기(없으면 기능, 있으면 이름·검색어)`, to: "#/growth" });
  }
  return out;
}

/**
 * [OBS2-04] 저장된 신호에 이번에 찾은 것을 합쳐요. 같은 종류·대상이 열려 있으면 고치고, 없으면 새로. 사흘 넘게 안 보인 신호는
 * '좋아짐'(cleared)과 그때 값(after). 끝난 것(좋아짐·아님)은 30일 뒤에 지우고, 많으면 오래된 끝난 것부터.
 */
export function obsMerge(stored, found, now) {
  const list = (stored ?? []).map((s) => ({ ...s }));
  const open = (s) => s.state !== "dismiss" && !s.cleared;
  for (const f of found) {
    const s = list.find((x) => x.kind === f.kind && x.subject === f.subject && (open(x) || (x.state === "dismiss" && now - x.at < OBS.signalDoneDays * DAY)));
    if (!f.on) {
      if (s && open(s)) s.now = f.value;
      continue;
    }
    if (s) {
      if (s.state === "dismiss") continue;
      Object.assign(s, { tone: f.tone, title: f.title, why: f.why, value: f.value, base: f.base, unit: f.unit, better: f.better, last: now, now: f.value });
      continue;
    }
    // 좋아졌다가 다시 나타나면 그 기록을 이어요(다시 나타남).
    const was = list.find((x) => x.kind === f.kind && x.subject === f.subject && x.cleared);
    if (was) {
      Object.assign(was, { cleared: null, after: null, back: (was.back ?? 0) + 1, tone: f.tone, title: f.title, why: f.why, value: f.value, now: f.value,
        base: f.base, last: now, state: was.state === "dev" ? "dev" : "new" });
      continue;
    }
    list.push({ key: `${f.kind}${f.subject ? `:${f.subject}` : ""}:${kstDay(now)}`, kind: f.kind, subject: f.subject, tone: f.tone, title: f.title, why: f.why,
      dev: f.dev, to: f.to, unit: f.unit, better: f.better, value: f.value, base: f.base, now: f.value, first: now, last: now, at: now, state: "new" });
  }
  for (const s of list) {
    if (open(s) && now - s.last >= OBS.clearMs) {
      s.cleared = now;
      s.after = s.now ?? null;
    }
  }
  const done = (s) => !open(s);
  const kept = list.filter((s) => !(done(s) && now - (s.cleared ?? s.at) > OBS.signalDoneDays * DAY));
  kept.sort((x, y) => Number(done(x)) - Number(done(y)) || y.last - x.last);
  return kept.slice(0, OBS.signalsMax);
}

/** 신호 목록 읽기(settings 'obs_signals'). */
async function obsStored(env) {
  const row = await one(env, "SELECT value FROM settings WHERE key = 'obs_signals'").catch(() => null);
  try {
    return row ? JSON.parse(row.value).list ?? [] : [];
  } catch {
    return [];
  }
}
const obsSave = (env, list) => run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)", JSON.stringify({ list }));

/** 배지: 아직 아무도 보지 않은 신호 수(index.js BADGE_SQL, 숫자 권한). */
export const OBS_BADGE_SQL = {
  obsNew: ["metrics", `SELECT COUNT(*) AS n FROM json_each(COALESCE((SELECT value FROM settings WHERE key = 'obs_signals'), '{"list":[]}'), '$.list')
    WHERE json_extract(value, '$.state') = 'new' AND json_extract(value, '$.cleared') IS NULL`],
};

/**
 * [OBS2-05] 관측 빈틈: 지금 숫자로 답하지 못하는 질문. 연결이 빠진 것(고치는 법), 표본이 적은 것, 처리방침이 필요한 것(사용자 결정),
 * 효과를 잴 이벤트 없이 진행·완료된 개발사항. 처리방침이 필요한 줄은 policy: true(Claude가 제안만, 사용자 카드로).
 */
export function obsGaps(b) {
  const out = [];
  // [OBS2-09] 사용자 2026-11-08 '이미 해결된 것도 보이네': state todo(할 일) · wait(기다리면 저절로 풀림) · decided(사용자가 정함).
  const add = (key, title, need, how, policy = false, state = "todo") => out.push({ key, title, need, how, policy, state });
  const now = b.at ?? Date.now();
  const r = b.play;
  if (r?.state === "off") add("play-account", "Play 숫자를 아직 못 읽어요", "Play 서비스 계정(PLAY_SERVICE_ACCOUNT)", "관리 › 서버 상태의 설정 칸을 따라 넣어요");
  else if (r?.state === "nobucket") add("play-bucket", "설치·삭제·스토어 전환·출처가 비어 있어요", "Play 보고서 저장소 이름(PLAY_REPORTS_BUCKET)",
    "Play Console › 보고서 다운로드 › 통계에서 'Cloud Storage URI 복사' → 서버 변수 PLAY_REPORTS_BUCKET, 서비스 계정에 '앱 정보 보기 및 대량 보고서 다운로드' 권한");
  else if (r?.state === "denied") {
    const since = r.deniedSince ?? now;
    const left = since + OBS.permWaitMs - now;
    if (left > 0) add("play-denied", `Play 보고서 권한이 퍼지는 중이에요(약 ${Math.max(1, Math.ceil(left / HOUR))}시간 안에)`, "기다리기만 하면 돼요",
      "권한을 저장한 뒤 Google 쪽에 퍼지는 데 하루까지 걸려요. 서버가 1시간마다 다시 읽고, '지금 다시 확인'으로 바로 볼 수도 있어요", false, "wait");
    else add("play-denied", "Play 보고서 저장소가 하루 넘게 서비스 계정을 막고 있어요", "'앱 정보 보기 및 대량 보고서 다운로드' 권한",
      "Play Console › 사용자 및 권한에서 서비스 계정에 그 권한이 '모든 앱'(또는 밤낮)으로 저장됐는지, PLAY_REPORTS_BUCKET이 그 계정의 저장소인지 봐요");
  }
  else if (r?.state === "ok" && !r.files?.store) add("play-store", "스토어 방문·전환·출처 파일이 없어요", "Play 보고서의 store_performance 파일", "Play Console에서 그 보고서가 나오는지 보고, 이름이 바뀌었으면 알려 주세요");
  if (b.quality?.vitals?.error === "denied") add("vitals-denied", "비정상 종료·하루 사용자가 비어 있어요", "Play Developer Reporting 권한", "서비스 계정에 '앱 품질 정보 보기' 권한을 줘요");
  if ((b.sample?.daily ?? 0) < 30) add("sample-small", `사용 통계를 보내는 휴대폰이 하루 ${b.sample?.daily ?? 0}대라 머묾·기능 쓰임이 흔들려요`,
    "1.39 출시(첫 설정 끝 사용 통계 묻기 카드)", "1.39가 퍼지면 늘어요. 그동안 전체 사용자 칸(Play·서버 요청)은 동의와 상관없이 모든 휴대폰을 세요", false, "wait");
  // [OBS2-09] 처리방침 3.12 시행일에 함께 켜지는 셋은 한 줄로.
  const at312 = [];
  if (b.sample && !b.sample.lifeOn) at312.push("진입·머묾·회복");
  // 1.39 [OBS2-08] 사용자 2026-11-08 결정 '유형별·기능 둘만': 둘은 처리방침 3.12에 넣어 시행일에 켜져요. 지운 이유는 넣지 않았어요.
  if (!b.kinds) at312.push("유형별 흐름");
  if (!b.keep) at312.push("기능과 머묾");
  if (at312.length) add("at-312", `${at312.join(" · ")}은 처리방침 3.12 시행일에 저절로 켜져요`, "처리방침 3.12 시행(사용자 결정대로 문장 넣음)",
    "기능 관리 › 일정에 3.12 날짜가 들어가면 그날부터 모여요. 그 전에는 휴대폰도 보내지 않아요", false, "wait");
  if (b.kinds && (b.kinds.all?.d7?.base ?? 0) < OBS.kindMin) add("by-type", `유형별 흐름 표본이 아직 적어요(7일째를 알 수 있는 처음 연 휴대폰 ${b.kinds.all?.d7?.base ?? 0}대)`,
    `${OBS.kindMin}대 이상`, "시행일 뒤 몇 주가 지나면 차요", false, "wait");
  if (b.keep && !b.keep.rows.length) add("feature-keep", "기능과 머묾 표본이 아직 적어요", `기능마다 쓴 휴대폰 ${OBS.keepMin}대 이상(7일 창이 끝난 날)`,
    "시행일 뒤 1~2주가 지나면 차요", false, "wait");
  add("why-leave", "지운 이유는 새로 모으지 않아요", "사용자 결정 2026-11-08(지우기 전 마지막 화면 빼기)",
    "대신 Play 하루 삭제 수와 판마다 최근 7일 삭제(전체 사용자 › 판별), 삭제 급증 신호, 1~2점 리뷰·문의로 봐요", true, "decided");
  const noEvents = b.dev?.noEvents ?? [];
  if (noEvents.length) add("dev-events", `효과를 잴 이벤트 없이 진행·완료된 개발사항 ${b.dev.noEventsCount}개`, "개발사항마다 events(쓰임을 셀 이벤트 이름)",
    `dev-status.jsonl에 events를 적으면 요금 판단·기능별 효과가 그 숫자를 써요. 예: ${noEvents.map((d) => d.title).slice(0, 3).join(" · ")}`);
  return out;
}

// 1.39 [OBS2-08] 유형별 흐름·기능과 머묾 ---------------------------------------------------------------------------------
/** 일하는 방식의 짧은 이름(앱 WorkStyle 순서). */
export const OBS_STYLE_NAMES = { SHIFT: "교대", DAY: "일근", HOURLY: "알바", FIELD: "현장", BUSINESS: "자영업", INVEST: "투자" };

/** [OBS.longDays]일 날 목록(KST, 오늘 포함). */
export function obsLongDays(now) {
  const out = [];
  for (let i = OBS.longDays - 1; i >= 0; i--) out.push(kstDay(now - i * DAY));
  return out;
}

/**
 * 유형별 흐름: usage_kinds 줄({day, style, event, phones})을 유형마다 진입(처음 연 휴대폰)과 1·7·30일째 머묾으로. 숫자 › 한눈에의 머묾과 같은 셈
 * (glance retention)이고, all은 유형을 보낸 휴대폰 전체예요.
 */
export function obsKinds(rows, list) {
  const per = new Map();
  for (const r of rows) {
    const s = per.get(r.style) ?? {};
    const m = (s[r.event] ??= new Map());
    m.set(r.day, (m.get(r.day) ?? 0) + (r.phones ?? 0));
    per.set(r.style, s);
  }
  const all = {};
  for (const s of per.values()) {
    for (const [e, m] of Object.entries(s)) {
      const a = (all[e] ??= new Map());
      for (const [d, n] of m) a.set(d, (a.get(d) ?? 0) + n);
    }
  }
  const row = (style, s) => {
    let n = 0;
    for (const v of s.life_new?.values() ?? []) n += v;
    return { style, name: OBS_STYLE_NAMES[style] ?? "전체", new: n, d1: retention(list, s, 1), d7: retention(list, s, 7), d30: retention(list, s, 30) };
  };
  return { rows: SETUP_STEPS.setup.filter((st) => per.has(st)).map((st) => row(st, per.get(st))), all: row("ALL", all) };
}

/**
 * 기능과 머묾: 그날 그 기능을 쓴 휴대폰(usage_events) 가운데 7일 안(또는 30일 안)에 다시 연 휴대폰(usage_keep)의 비율. 창이 끝난 날만 더하고,
 * [since](깃발이 켜진 날) 앞의 날은 빼요. 'open'은 그날 연 휴대폰 전체라 기준(base)이에요. lift7은 기준과의 차이(%p).
 */
export function obsKeep(useRows, keepRows, list, since, names = {}) {
  const n = list.length;
  const after = (d) => !since || d >= since;
  const c7 = new Set(list.slice(Math.max(0, n - 38), Math.max(0, n - 8)).filter(after));
  const c30 = new Set(list.slice(0, Math.max(0, n - 31)).filter(after));
  const by = new Map();
  const get = (e) => {
    if (!by.has(e)) by.set(e, { event: e, users7: 0, back7: 0, users30: 0, back30: 0 });
    return by.get(e);
  };
  for (const r of useRows) {
    if (/^life_/.test(r.event)) continue;
    if (c7.has(r.day)) get(r.event).users7 += r.phones ?? 0;
    if (c30.has(r.day)) get(r.event).users30 += r.phones ?? 0;
  }
  for (const r of keepRows) {
    if (/^life_/.test(r.event)) continue;
    const x = get(r.event);
    if (c7.has(r.day) && r.win === 7) x.back7 += r.phones ?? 0;
    if (c30.has(r.day)) x.back30 += r.phones ?? 0;
  }
  const pct = (a, b) => (b ? obsRound1((Math.min(a, b) / b) * 100) : null);
  const rows = [...by.values()].map((x) => ({ event: x.event, name: names[x.event] ?? x.event, users: x.users7, rate7: pct(x.back7, x.users7),
    users30: x.users30, rate30: pct(x.back30, x.users30) }));
  const base = rows.find((r) => r.event === "open") ?? null;
  for (const r of rows) r.lift7 = base?.rate7 !== null && base?.rate7 !== undefined && r.rate7 !== null ? obsRound1(r.rate7 - base.rate7) : null;
  const shown = rows.filter((r) => r.event !== "open" && r.users >= OBS.keepMin).sort((a, b) => (b.lift7 ?? -1e9) - (a.lift7 ?? -1e9));
  const days7 = [...c7].sort();
  return { base, rows: shown, small: rows.filter((r) => r.event !== "open" && r.users > 0 && r.users < OBS.keepMin).length,
    from: days7[0] ?? null, to: days7.at(-1) ?? null };
}

/** 사용 통계 표본의 기능 이벤트 중 최근 7일 쓰는 휴대폰이 그 전 7일의 반 아래로 줄어든 것(그 전 20대 이상). */
export function obsFeatureFalls(rows, from7, names = {}) {
  const by = new Map();
  for (const r of rows) {
    if (/^life_/.test(r.event)) continue;
    const x = by.get(r.event) ?? { event: r.event, now: 0, before: 0 };
    if (r.day >= from7) x.now += r.phones ?? 0;
    else x.before += r.phones ?? 0;
    by.set(r.event, x);
  }
  return [...by.values()].filter((x) => x.before >= 20 && x.now < x.before * 0.5)
    .sort((a, b) => b.before - b.now - (a.before - a.now)).map((x) => ({ ...x, name: names[x.event] ?? x.event }));
}

/**
 * /api/admin/observe: 관측 판 한 장. 한 번의 batch로 서버·표본·목소리·개발사항을 읽고, Play 보고서·vitals는 둔 것을 써요
 * (오래됐으면 그때 다시 받아요).
 */
export async function observe(env, now = Date.now(), { names = EVENTS } = {}) {
  const days = obsDays(now);
  const from = days[0];
  const to = days.at(-1);
  const from7 = days.at(-7);
  const [reports, vitals, store, stored] = await Promise.all([playReports(env, now).catch(() => ({ state: "unreachable" })),
    playVitals(env, now).catch(() => null), flagStore(env).catch(() => null), obsStored(env)]);
  const q = (await batch(env, [
    stmt(env, "SELECT substr(hour, 1, 10) AS day, SUM(n) AS n, SUM(err) AS err FROM req_stats WHERE route = '/api/config' AND hour >= ? GROUP BY day", `${from}T00`),
    stmt(env, "SELECT day, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY day", from, to),
    stmt(env, "SELECT day, event, SUM(phones) AS phones FROM usage_events WHERE day BETWEEN ? AND ? GROUP BY day, event", days.at(-14), to),
    stmt(env, `SELECT word, place, SUM(n) AS n FROM search_misses WHERE week >= ? GROUP BY word, place HAVING SUM(n) >= 2 ORDER BY n DESC LIMIT 10`,
      kstDay(now - 21 * DAY).slice(0, 10)),
    stmt(env, `SELECT COUNT(*) AS n, SUM(CASE WHEN created_at >= ? THEN 1 ELSE 0 END) AS week FROM inquiries WHERE created_at >= ?`, now - 7 * DAY, now - 28 * DAY),
    stmt(env, `SELECT COUNT(*) AS n, AVG(stars) AS stars, SUM(CASE WHEN at >= ? THEN 1 ELSE 0 END) AS week FROM store_reviews`, now - 7 * DAY),
    stmt(env, `SELECT id, title FROM dev_items WHERE deleted_at IS NULL AND status IN ('doing', 'done') AND COALESCE(events, '') = '' ORDER BY updated_at DESC LIMIT 5`),
    stmt(env, `SELECT COUNT(*) AS n FROM dev_items WHERE deleted_at IS NULL AND status IN ('doing', 'done') AND COALESCE(events, '') = ''`),
  ]).catch(() => null))?.map((r) => r.results ?? []) ?? [[], [], [], [], [{}], [{}], [], [{}]];
  const [configRows, usageRows, eventRows, missRows, inqRows, revRows, noEvRows, noEvCount] = q;
  const cfg = new Map(configRows.map((r) => [r.day, r.n ?? 0]));
  const sample = new Map(usageRows.map((r) => [r.day, r.phones ?? 0]));
  const sampleSeries = days.map((d) => sample.get(d) ?? 0);
  // 서버 요청은 14일만 남아요: 그 앞 날은 null(없음)로, 0과 가려요.
  const cfgFrom = kstDay(now - 14 * DAY);
  const config = days.filter((d) => d >= cfgFrom).map((d) => ({ day: d, n: cfg.get(d) ?? 0 }));
  const pd = reports.days ?? [];
  const playDays = obsPlayDays(reports.lastDay);
  const byDay = new Map(pd.map((d) => [d.day, d]));
  const playRows = playDays.map((d) => byDay.get(d) ?? { day: d, installs: null, uninstalls: null, base: null, visitors: null, acq: null, rating: null });
  const vSeries = (vitals && !vitals.error ? vitals.series ?? [] : []).map((x) => ({ day: x.day, n: x.users }));
  const misses = missRows.map((m) => ({ word: m.word, place: m.place, placeName: MISS_PLACES[m.place] ?? m.place, n: m.n }));
  const vit = vitals && !vitals.error ? { crash: vitals.crash === null ? null : Math.round(vitals.crash * 10000) / 100,
    anr: vitals.anr === null ? null : Math.round(vitals.anr * 10000) / 100, users: vitals.users ?? null, day: vitals.day ?? null } : vitals?.error ? { error: vitals.error } : null;
  const lifeOn = store ? serverEval(store, "stats.life", now).on : false;
  // 1.39 [OBS2-08] 유형별 흐름·기능과 머묾: 깃발(처리방침 3.12)이 켜진 뒤에만 읽어요.
  const kindOn = store ? serverEval(store, "stats.kind", now).on : false;
  const keepOn = store ? serverEval(store, "stats.keep", now).on : false;
  const long = obsLongDays(now);
  const more = kindOn || keepOn ? (await batch(env, [
    ...(kindOn ? [stmt(env, "SELECT day, style, event, SUM(phones) AS phones FROM usage_kinds WHERE day >= ? GROUP BY day, style, event", long[0])] : []),
    ...(keepOn ? [stmt(env, "SELECT day, event, SUM(phones) AS phones FROM usage_events WHERE day BETWEEN ? AND ? GROUP BY day, event", long[0], long.at(-9)),
      stmt(env, "SELECT day, event, win, SUM(phones) AS phones FROM usage_keep WHERE day >= ? GROUP BY day, event, win", long[0])] : []),
  ]).catch(() => null))?.map((r) => r.results ?? []) ?? null : null;
  const keepState = store?.state?.get("stats.keep");
  const keepSince = keepState?.on_now && Number.isFinite(Number(keepState.since)) ? kstDay(Number(keepState.since)) : null;
  const kinds = kindOn && more ? obsKinds(more[0], long) : null;
  const keep = keepOn && more ? obsKeep(more.at(-2), more.at(-1), long, keepSince, names) : null;
  const last7 = sampleSeries.slice(-7);
  const board = {
    at: now, days, play: { state: reports.state, stale: reports.stale ?? false, lastDay: reports.lastDay ?? null, days: playRows, sources: reports.sources ?? [],
      versions: reports.versions ?? null, files: reports.files ?? null, at: reports.at ?? null, deniedSince: reports.deniedSince ?? null },
    active: { play: vSeries, config },
    quality: { vitals: vit },
    sample: { daily: Math.round(obsAvg(last7)), series: sampleSeries, lifeOn, features: obsFeatureFalls(eventRows, from7, names) },
    voice: { misses, inquiries: { n: inqRows[0]?.n ?? 0, week: inqRows[0]?.week ?? 0 },
      reviews: { n: revRows[0]?.n ?? 0, stars: revRows[0]?.stars === null || revRows[0]?.stars === undefined ? null : obsRound1(revRows[0].stars), week: revRows[0]?.week ?? 0 } },
    dev: { noEvents: noEvRows.map((d) => ({ id: d.id, title: d.title })), noEventsCount: noEvCount[0]?.n ?? 0 },
    kinds,
    keep,
  };
  board.found = obsRules(board);
  board.signals = stored;
  board.gaps = obsGaps(board);
  return board;
}

/** 매시 작업 [OBS2-04]: 관측 판을 다시 읽어 신호를 합쳐 둬요. 새로 생긴 신호 수. */
export async function observeTick(env, now = Date.now()) {
  const b = await observe(env, now);
  const before = new Set(b.signals.map((s) => s.key));
  const list = obsMerge(b.signals, b.found, now);
  await obsSave(env, list);
  return list.filter((s) => !before.has(s.key)).length;
}

const OBS_ACTS = { dev: "개발사항으로", watch: "지켜보기", dismiss: "아님", reopen: "다시 열기" };

/**
 * GET /observe (숫자 권한) · GET /observe/signals · POST /observe/signals {key, act, title?, note?}.
 * act dev는 개발사항을 만들어 그 번호를 신호에 달아요(개발사항 고치기 권한). 아님은 이유(note)를 남겨 30일 동안 같은 신호를 다시 띄우지 않아요.
 */
export async function observeRoute(request, env, path, method, now) {
  const actor = env.actor;
  if (!can(actor, "metrics", "view")) return json({ error: "no_permission", area: "metrics" }, 403);
  if (path === "/observe" && method === "GET") return json(await observe(env, now));
  if (path === "/observe/signals" && method === "GET") return json({ list: await obsStored(env) });
  // [OBS2-09] '지금 다시 확인': Play 보고서를 바로 다시 읽어요(2분에 한 번까지).
  if (path === "/observe/refresh" && method === "POST") {
    if (!can(actor, "metrics", "edit") && !can(actor, "dev", "edit")) return json({ error: "no_permission", area: "metrics" }, 403);
    const last = await one(env, "SELECT value FROM settings WHERE key = 'play_reports'").catch(() => null);
    let at = 0;
    try {
      at = JSON.parse(last?.value ?? "{}")?.value?.at ?? 0;
    } catch {
      at = 0;
    }
    const r = now - at < OBS.refreshMs ? await playReports(env, now) : await playReports(env, now, true);
    return json({ ok: true, state: r.state, at: r.at ?? null, again: now - at < OBS.refreshMs });
  }
  if (path !== "/observe/signals" || method !== "POST") return null;
  const body = await readJson(request, 8 * 1024);
  const act = Object.hasOwn(OBS_ACTS, body.act) ? body.act : refuse("act", "act");
  const list = await obsStored(env);
  const s = list.find((x) => x.key === body.key);
  if (!s) return json({ error: "not_found" }, 404);
  // 보기만 하는 관리자는 상태를 못 바꿔요: 숫자 고치기나 개발사항 고치기 권한.
  if (!can(actor, "metrics", "edit") && !can(actor, "dev", "edit")) return json({ error: "no_permission", area: "dev" }, 403);
  const note = body.note === undefined ? "" : plainText(body.note, 300, "note");
  if (act === "dev") {
    if (!can(actor, "dev", "edit")) return json({ error: "no_permission", area: "dev" }, 403);
    if (s.devId) return json({ error: "already", dev: s.devId }, 409);
    const title = plainText(body.title ?? s.dev ?? s.title, DEV.title, "title", true);
    const text = plainText(`관측 신호 ${s.key}\n${s.title}\n${s.why}\n지금 ${s.now ?? s.value}${s.unit} · 기준 ${s.base}${s.unit}${note ? `\n${note}` : ""}`, DEV.body, "body");
    const id = `d${randomId(15)}`;
    await batch(env, [
      stmt(env, `INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, assignee, assignee_name, created_at, updated_at, deleted_at)
        VALUES (?, ?, ?, 'todo', '', ?, ?, NULL, NULL, ?, ?, NULL)`, id, title, text, actor.id, actorName(env), now, now),
      devEvent(env, id, now, "create", { title, from: "관측 신호" }),
    ]);
    Object.assign(s, { state: "dev", devId: id, by: actorName(env), at: now });
  } else if (act === "reopen") Object.assign(s, { state: s.devId ? "dev" : "new", cleared: null, after: null, by: actorName(env), at: now });
  else Object.assign(s, { state: act, by: actorName(env), at: now, ...(note ? { note } : {}) });
  await obsSave(env, list);
  await audit(env, now, "obs_signal", s.title.slice(0, 80), `${OBS_ACTS[act]}${s.devId && act === "dev" ? ` ${s.devId}` : ""}${note ? ` · ${note}` : ""}`);
  return json({ signal: s });
}
