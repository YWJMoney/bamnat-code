import { all, batch, playVitals, stmt } from "./index.js";
import { flagStore, serverEval } from "./flags.js";
import { jobState, speedState } from "./ops-watch.js";
import { kstDay } from "./usage.js";
import { DAY } from "./units.js";

// ---- 1.39 [STAT2-01~05] 숫자 › 한눈에 ----
//
// 사용자 2026-11-08 "통계쪽 혁신 … 어플의 정상도 사용자 이탈 회복 진입 등 모든 것을 전반적으로 볼수 있도록, 레이아웃도 디자인 전문가
// 입장에서 최정상급". 한 화면에 세 덩어리:
//   정상도 [STAT2-01]   비정상 종료·ANR(Play), 서버 오류율·느린 5%, 열린 사고, 정기 작업, 새 오류, 문제 신고 추세, 낮은 별점을 하나의 신호
//                       (정상·주의·나쁨)와 그 이유로.
//   사용자 흐름 [STAT2-02]  사용 통계를 켠 휴대폰이 이름 없이 보내는 life_* 날 수(처리방침 3.12, 깃발 stats.life)로 진입(처음 연 날),
//                       머묾(1·7·30일째 다시 연 비율, 그날 들어온 휴대폰 대비), 회복(7일·30일 넘게 쉬다 돌아옴), 이탈 어림(1 − 30일 머묾).
//   초기 모드 [STAT2-03] 하루 활성이 30대 아래면 '데이터 부족' 칸 대신 Play가 본 사용자·스토어 리뷰·문의처럼 있는 숫자를 앞에.

export const GLANCE = {
  days: 28,
  /** 이 아래면 초기 모드: 사용 통계 숫자로 비율을 말하기엔 적어요. */
  earlyPhones: 30,
  /** Play 기준(비정상 종료 1.09%, ANR 0.47%)과 우리 안쪽 기준(0.5%, 0.2%). */
  crash: { bad: 1.09, warn: 0.5 },
  anr: { bad: 0.47, warn: 0.2 },
  /** 24시간 서버 오류율(%)과 느린 5%(ms). */
  errRate: { bad: 2, warn: 0.5 },
  p95: { warn: 1500 },
};

/** 머묾을 재는 날째(설치한 날이 0일째). 앱 core LifeMarks와 같아요. */
export const LIFE_DAYS = [1, 7, 30];
/** 1.39 [STAT2-02] 사용 통계의 life_* 이벤트(앱 core UsageEvent와 같아요). 깃발 stats.life가 꺼져 있으면 서버가 받지 않아요. */
export const LIFE_EVENTS = ["life_new", "life_d1", "life_d7", "life_d30", "life_back7", "life_back30"];

/** Play가 주는 비율(0.0123)을 %로 소수 둘째 자리까지. */
const pct2 = (v) => (v === null || v === undefined || !Number.isFinite(v) ? null : Math.round(v * 10000) / 100);
const round1 = (v) => (v === null || v === undefined || !Number.isFinite(v) ? null : Math.round(v * 10) / 10);

/** 날 목록 [from]..[to] (KST 날, 둘 다 포함). */
function dayList(from, to) {
  const out = [];
  for (let d = Date.parse(`${from}T00:00:00+09:00`); kstDay(d) <= to; d += DAY) out.push(kstDay(d));
  return out;
}

/**
 * 머묾: N일째 다시 연 휴대폰 ÷ N일 전에 처음 연 휴대폰. 두 날이 모두 기간 안에 있는 날만 더해요(기간 끝 N일은 아직 몰라요).
 * 처음 연 휴대폰이 없으면 null.
 */
export function retention(list, per, n) {
  let base = 0;
  let kept = 0;
  for (let i = n; i < list.length; i++) {
    const came = per.life_new?.get(list[i - n]) ?? 0;
    if (!came) continue;
    base += came;
    kept += Math.min(came, per[`life_d${n}`]?.get(list[i]) ?? 0);
  }
  return base ? { rate: round1((kept / base) * 100), base, kept } : { rate: null, base: 0, kept: 0 };
}

/**
 * 주마다 머묾(코호트): 기간을 끝에서부터 7일씩 묶어, 그 주에 처음 연 휴대폰이 1·7·30일째 다시 연 비율. 아직 그날이 안 온 칸은 null.
 * 가장 최근 주가 맨 위예요.
 */
export function cohorts(list, per) {
  const out = [];
  for (let end = list.length; end > 0; end -= 7) {
    const start = Math.max(0, end - 7);
    let came = 0;
    for (let i = start; i < end; i++) came += per.life_new?.get(list[i]) ?? 0;
    const row = { from: list[start], to: list[end - 1], new: came };
    for (const n of LIFE_DAYS) {
      let base = 0;
      let kept = 0;
      for (let i = start; i < end; i++) {
        if (i + n >= list.length) continue;
        const c = per.life_new?.get(list[i]) ?? 0;
        base += c;
        kept += Math.min(c, per[`life_d${n}`]?.get(list[i + n]) ?? 0);
      }
      row[`d${n}`] = base ? round1((kept / base) * 100) : null;
    }
    out.push(row);
  }
  return out;
}

/** 좋아짐·나빠짐: 최근 7일 평균과 그 전 7일 평균의 차이(%). 그 전이 0이면 null. */
export function weekTrend(series) {
  const avg = (a) => a.reduce((n, v) => n + v, 0) / Math.max(1, a.length);
  const now = avg(series.slice(-7));
  const before = avg(series.slice(-14, -7));
  return before ? round1(((now - before) / before) * 100) : null;
}

/** 정상도: 신호마다 { key, tone, text, to }와 가장 나쁜 것 하나. */
export function healthSignals({ vitals, speed, incident, jobs, newErrors, bugs, lowReviews }) {
  const out = [];
  const add = (key, tone, text, to) => out.push({ key, tone, text, to });
  if (incident) add("incident", "bad", "사고가 열려 있어요", "#/incident");
  if (vitals && !vitals.error && vitals.crash !== null && vitals.crash !== undefined) {
    const v = vitals.crash * 100;
    if (v >= GLANCE.crash.bad) add("crash", "bad", `비정상 종료 ${round1(v)}% · Play 기준 ${GLANCE.crash.bad}% 넘음`, "#/health");
    else if (v >= GLANCE.crash.warn) add("crash", "warn", `비정상 종료 ${round1(v)}% · 안쪽 기준 ${GLANCE.crash.warn}% 넘음`, "#/health");
  }
  if (vitals && !vitals.error && vitals.anr !== null && vitals.anr !== undefined) {
    const v = vitals.anr * 100;
    if (v >= GLANCE.anr.bad) add("anr", "bad", `ANR ${round1(v)}% · Play 기준 ${GLANCE.anr.bad}% 넘음`, "#/health");
    else if (v >= GLANCE.anr.warn) add("anr", "warn", `ANR ${round1(v)}% · 안쪽 기준 ${GLANCE.anr.warn}% 넘음`, "#/health");
  }
  const t = speed?.total;
  if (t?.n) {
    const rate = (t.err / t.n) * 100;
    if (rate >= GLANCE.errRate.bad) add("errors", "bad", `24시간 서버 오류 ${t.err}번(${round1(rate)}%)`, "#/errors");
    else if (rate >= GLANCE.errRate.warn) add("errors", "warn", `24시간 서버 오류 ${t.err}번(${round1(rate)}%)`, "#/errors");
    if ((t.p95 ?? 0) > GLANCE.p95.warn) add("speed", "warn", `느린 5%가 ${t.p95}ms예요`, "#/server");
  }
  const failing = (jobs ?? []).filter((j) => j.state === "fail" || j.state === "stale");
  if (failing.length) add("jobs", failing.some((j) => j.state === "fail") ? "bad" : "warn", `정기 작업 ${failing.length}개 ${failing.some((j) => j.state === "fail") ? "실패" : "멈춤"}`, "#/server");
  if (newErrors) add("newErrors", "warn", `새 오류 ${newErrors}가지(하루)`, "#/errors");
  if (bugs && bugs.now >= 3 && bugs.now >= bugs.before * 2) add("bugs", "warn", `문제 신고가 늘었어요(7일 ${bugs.now}건, 그 전 ${bugs.before}건)`, "#/inquiries");
  if (lowReviews) add("reviews", "warn", `답 안 한 낮은 별점 리뷰 ${lowReviews}개`, "#/reviews");
  const tone = out.some((s) => s.tone === "bad") ? "bad" : out.some((s) => s.tone === "warn") ? "warn" : "ok";
  return { tone, signals: out.sort((a, b) => (a.tone === b.tone ? 0 : a.tone === "bad" ? -1 : 1)) };
}

/** /api/admin/glance: 정상도·사용자 흐름·초기 모드. 지표 권한. */
export async function glance(env, now = Date.now()) {
  const to = kstDay(now - DAY);
  const from = kstDay(now - GLANCE.days * DAY);
  const list = dayList(from, to);
  // 머묾은 30일째까지 봐야 하니 진입·머묾 날 수는 30일 더 앞에서부터 읽어요(주마다 머묾은 최근 8주).
  const lifeFrom = kstDay(now - (GLANCE.days + 30) * DAY);
  const longList = dayList(lifeFrom, to);
  const vitalsP = playVitals(env, now).catch(() => null);
  const [speed, jobs, store] = await Promise.all([speedState(env, now, 24).catch(() => null), jobState(env, now).catch(() => []), flagStore(env)]);
  const q = (await batch(env, [
    stmt(env, "SELECT day, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY day", from, to),
    stmt(env, `SELECT day, event, SUM(phones) AS phones FROM usage_events WHERE day BETWEEN ? AND ? AND event IN (${LIFE_EVENTS.map(() => "?").join(", ")})
      GROUP BY day, event`, lifeFrom, to, ...LIFE_EVENTS),
    stmt(env, "SELECT id FROM incidents WHERE closed_at IS NULL LIMIT 1"),
    stmt(env, "SELECT COUNT(*) AS n FROM error_groups WHERE status IN ('new', 'again') AND last_at > ?", now - DAY),
    stmt(env, `SELECT SUM(CASE WHEN created_at >= ? THEN 1 ELSE 0 END) AS now, SUM(CASE WHEN created_at < ? THEN 1 ELSE 0 END) AS before
      FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ?`, now - 7 * DAY, now - 7 * DAY, now - 14 * DAY),
    stmt(env, `SELECT COUNT(*) AS n, AVG(stars) AS stars, SUM(CASE WHEN status <> 'replied' THEN 1 ELSE 0 END) AS open,
      SUM(CASE WHEN status <> 'replied' AND stars <= 2 THEN 1 ELSE 0 END) AS low, SUM(CASE WHEN at >= ? THEN 1 ELSE 0 END) AS week FROM store_reviews`, now - 7 * DAY),
    stmt(env, "SELECT COUNT(*) AS n FROM inquiries WHERE created_at >= ?", now - 7 * DAY),
  ]).catch(() => null))?.map((r) => r.results ?? []) ?? [[], [], [], [{}], [{}], [{}], [{}]];
  const [usageRows, lifeRows, incRows, errRows, bugRows, reviewRows, inqRows] = q;
  const vitals = await vitalsP;

  const active = new Map(usageRows.map((r) => [r.day, r.phones ?? 0]));
  const per = Object.fromEntries(LIFE_EVENTS.map((e) => [e, new Map()]));
  for (const r of lifeRows) per[r.event]?.set(r.day, r.phones ?? 0);
  const series = (m) => list.map((d) => m.get(d) ?? 0);
  const sum = (a) => a.reduce((n, v) => n + v, 0);
  const activeSeries = series(active);
  const last7 = activeSeries.slice(-7);
  const daily = Math.round(sum(last7) / Math.max(1, last7.length));
  const lifeOn = serverEval(store, "stats.life", now).on;
  const newSeries = series(per.life_new);
  const backSeries = list.map((d) => (per.life_back7.get(d) ?? 0) + (per.life_back30.get(d) ?? 0));
  const keep = Object.fromEntries(LIFE_DAYS.map((n) => [`d${n}`, retention(longList, per, n)]));
  const bugs = { now: bugRows[0]?.now ?? 0, before: bugRows[0]?.before ?? 0 };
  const rv = reviewRows[0] ?? {};
  const health = healthSignals({ vitals, speed, incident: Boolean(incRows[0]), jobs, newErrors: errRows[0]?.n ?? 0, bugs, lowReviews: rv.low ?? 0 });
  return {
    at: now, from, to, days: list,
    health: { ...health,
      vitals: vitals && !vitals.error ? { crash: pct2(vitals.crash), anr: pct2(vitals.anr), users: vitals.users ?? null, day: vitals.day ?? null }
        : vitals?.error ? { error: vitals.error } : null,
      server: speed?.total?.n ? { n: speed.total.n, err: speed.total.err, rate: round1((speed.total.err / speed.total.n) * 100), p50: speed.total.p50, p95: speed.total.p95 } : null,
      bugs },
    flow: {
      lifeOn, active: activeSeries, daily, activeTrend: weekTrend(activeSeries), newPhones: newSeries, newTrend: weekTrend(newSeries), back: backSeries,
      cohorts: cohorts(longList, per).slice(0, 8),
      totals: { new: sum(newSeries), back7: sum(series(per.life_back7)), back30: sum(series(per.life_back30)) },
      keep, churn: keep.d30.rate === null ? null : round1(100 - keep.d30.rate),
    },
    early: daily < GLANCE.earlyPhones ? {
      daily, playUsers: vitals && !vitals.error ? vitals.users ?? null : null, playDay: vitals?.day ?? null,
      reviews: { n: rv.n ?? 0, stars: rv.stars === null || rv.stars === undefined ? null : round1(rv.stars), open: rv.open ?? 0, week: rv.week ?? 0 },
      inquiries: inqRows[0]?.n ?? 0,
    } : null,
    note: "활성·흐름 숫자는 '사용 통계 보내기'를 켠 휴대폰만, 휴대폰을 가리지 않고 날마다 센 수예요.",
  };
}
