import { audit, batch, json, one, readJson, refuse, run, stmt } from "./index.js";
import { MONEY } from "./money.js";
import { DAY } from "./units.js";
import { kstDay } from "./usage.js";

// ---- 1.38 [ADM3-12] 돈 화면: 구독·이용권·체험·해지·환불, 팀·직원 요금제, 광고 실패로 무료로 연 횟수 ----
//
// 보고서 3-2항 O1. 서버가 이미 Play와 맞춰 둔 기록(subs·year_passes·day_rewards)만 세요. 새로 모으는 것은 없어요.
// 금액은 '가격표 기준 추정'이에요: Play는 실제 가격을 서버에 알려 주지 않아서, 최고 관리자가 Play Console 가격을 가격표에 적어 두면
// 그 값으로 곱해요(수수료·세금 전). 실제 돈은 Play Console › 수익 보고서가 정답이에요.

/** 가격표 기본값(원). 프로 값은 Play Console에서 정한 뒤 관리자 앱에서 적어요(0이면 '가격 미입력'). */
export const PRICE_DEFAULT = {
  bamnat_pro: { monthly: 0, yearly: 0 },
  bamnat_pro_pass_1y: { pass: 0 },
  team: MONEY.workPlans.team.map((b) => ({ cap: b.cap, monthly: b.monthly, yearly: b.yearly, pass: b.pass })),
  staff: MONEY.workPlans.staff.map((b) => ({ cap: b.cap, monthly: b.monthly, yearly: b.yearly, pass: b.pass })),
  fee: 15,
};

const MB_PLAN = /^c([0-9]{1,4})-(monthly|yearly)$/;
const MB_WORK_PASS = /^bamnat_(team|staff)_pass_([0-9]{1,4})$/;

export async function priceTable(env) {
  try {
    const v = JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'price_table'"))?.value ?? "null");
    return v && typeof v === "object" ? { ...PRICE_DEFAULT, ...v } : PRICE_DEFAULT;
  } catch {
    return PRICE_DEFAULT;
  }
}

/** 한 결제가 가격표로 얼마인지(원). 모르면 0. */
export function priceOf(table, product, plan) {
  if (MB_WORK_PASS.test(product ?? "")) {
    const [, who, cap] = MB_WORK_PASS.exec(product);
    return table[who]?.find((b) => b.cap === Number(cap))?.pass ?? 0;
  }
  if (product === "bamnat_pro_pass_1y") return table.bamnat_pro_pass_1y?.pass ?? 0;
  const m = MB_PLAN.exec(plan ?? "");
  if (product === "bamnat_team" || product === "bamnat_staff") {
    const who = product === "bamnat_team" ? "team" : "staff";
    return m ? table[who]?.find((b) => b.cap === Number(m[1]))?.[m[2]] ?? 0 : 0;
  }
  if (/yearly|annual|year/.test(plan ?? "")) return table.bamnat_pro?.yearly ?? 0;
  return table.bamnat_pro?.monthly ?? 0;
}

const productName = (product, plan) => {
  if (product === "bamnat_pro_pass_1y") return "프로 1년 이용권";
  const wp = MB_WORK_PASS.exec(product ?? "");
  if (wp) return `${wp[1] === "team" ? "팀" : "직원"} ${wp[2]}명 1년 이용권`;
  const m = MB_PLAN.exec(plan ?? "");
  if (product === "bamnat_team" || product === "bamnat_staff") return `${product === "bamnat_team" ? "팀" : "직원"} ${m ? `${m[1]}명 ${m[2] === "yearly" ? "연" : "월"}` : ""}`.trim();
  return `프로 ${/year|annual/.test(plan ?? "") ? "연간" : "월간"}`;
};

/** [days]일 동안의 돈 화면. */
export async function moneyBoard(env, now = Date.now(), days = 28) {
  const n = Math.min(Math.max(Number(days) || 28, 7), 120);
  const start = now - n * DAY;
  const table = await priceTable(env);
  const [subs, passes, rewards, events] = (await batch(env, [
    stmt(env, "SELECT product, base_plan, state, expiry, trial, auto_renew, stopped_at, created_at FROM subs"),
    stmt(env, "SELECT product, state, purchased_at, checked_at FROM year_passes WHERE purchased_at > ?", now - 400 * DAY),
    stmt(env, "SELECT kind, given_at, days FROM day_rewards WHERE given_at >= ?", start),
    stmt(env, `SELECT day, event, SUM(total) AS total FROM usage_events WHERE day >= ? AND event IN ('paywall_view', 'trial_start', 'subscribe', 'theme_free_no_fill',
      'theme_free_timeout', 'theme_free_show_failed', 'theme_free_not_ready', 'theme_free_other', 'plan_view_team', 'plan_view_staff', 'plan_buy_tap') GROUP BY day, event`, kstDay(start)),
  ])).map((r) => r.results ?? []);
  const dayList = Array.from({ length: n }, (_, i) => kstDay(now - (n - 1 - i) * DAY));
  const idx = new Map(dayList.map((d, i) => [d, i]));
  const zero = () => new Array(n).fill(0);
  const series = { started: zero(), trials: zero(), stopped: zero(), passes: zero(), refunds: zero(), amount: zero(), paywall: zero(), adFails: zero() };
  const live = subs.filter((s) => ["ACTIVE", "GRACE"].includes(s.state) && (s.expiry ?? 0) > now);
  const byProduct = new Map();
  const add = (key, field, v = 1) => {
    const x = byProduct.get(key) ?? { name: key, paid: 0, trials: 0, ending: 0, started: 0, stopped: 0, passes: 0, refunds: 0, amount: 0, price: 0 };
    x[field] += v;
    byProduct.set(key, x);
    return x;
  };
  for (const s of live) {
    const name = productName(s.product, s.base_plan);
    const x = add(name, s.trial ? "trials" : "paid");
    x.price = priceOf(table, s.product, s.base_plan);
    if (!s.auto_renew) add(name, "ending");
  }
  for (const s of subs) {
    const name = productName(s.product, s.base_plan);
    const i = idx.get(kstDay(s.created_at));
    if (s.created_at >= start && i !== undefined) {
      if (s.trial) {
        series.trials[i] += 1;
        add(name, "started");
      } else {
        series.started[i] += 1;
        add(name, "started");
        const p = priceOf(table, s.product, s.base_plan);
        series.amount[i] += p;
        add(name, "amount", p);
      }
    }
    const j = s.stopped_at ? idx.get(kstDay(s.stopped_at)) : undefined;
    if (s.stopped_at >= start && j !== undefined) {
      series.stopped[j] += 1;
      add(name, "stopped");
    }
  }
  for (const p of passes) {
    const name = productName(p.product);
    const i = idx.get(kstDay(p.purchased_at));
    if (p.purchased_at >= start && i !== undefined) {
      series.passes[i] += 1;
      add(name, "passes");
      if (p.state === "ACTIVE") {
        const v = priceOf(table, p.product);
        series.amount[i] += v;
        add(name, "amount", v);
      }
    }
    // 1년 안에 EXPIRED가 된 이용권은 환불이에요(readPass: purchaseState 1). 알게 된 날(checked_at)로 세요.
    if (p.state === "EXPIRED" && p.checked_at >= start && p.checked_at < p.purchased_at + 365 * DAY) {
      const k = idx.get(kstDay(p.checked_at));
      if (k !== undefined) series.refunds[k] += 1;
      add(name, "refunds");
    }
  }
  const ev = {};
  for (const e of events) {
    ev[e.event] = (ev[e.event] ?? 0) + (e.total ?? 0);
    const i = idx.get(e.day);
    if (i === undefined) continue;
    if (e.event === "paywall_view") series.paywall[i] += e.total ?? 0;
    if (e.event.startsWith("theme_free_")) series.adFails[i] += e.total ?? 0;
  }
  const sum = (a) => a.reduce((x, y) => x + y, 0);
  const paid = live.filter((s) => !s.trial).length;
  const monthly = live.filter((s) => !s.trial).reduce((m, s) => {
    const p = priceOf(table, s.product, s.base_plan);
    return m + (/yearly|annual|year/.test(s.base_plan ?? "") ? Math.round(p / 12) : p);
  }, 0);
  const missingPrice = [...byProduct.values()].some((x) => (x.paid || x.started || x.passes) && !x.price && !x.amount);
  return {
    days: dayList, series, table,
    now: { paid, trials: live.filter((s) => s.trial).length, ending: live.filter((s) => !s.auto_renew).length, passes: passes.filter((p) => p.state === "ACTIVE" && p.purchased_at + 365 * DAY > now).length, monthly },
    period: { started: sum(series.started), trials: sum(series.trials), stopped: sum(series.stopped), passes: sum(series.passes), refunds: sum(series.refunds),
      amount: sum(series.amount), afterFee: Math.round(sum(series.amount) * (1 - (table.fee ?? 15) / 100)), paywall: ev.paywall_view ?? 0, trialTaps: ev.trial_start ?? 0,
      subscribeTaps: ev.subscribe ?? 0, adFails: sum(series.adFails), rewardDays: rewards.reduce((n, r) => n + (r.days ?? 0), 0), planViews: (ev.plan_view_team ?? 0) + (ev.plan_view_staff ?? 0),
      planTaps: ev.plan_buy_tap ?? 0 },
    products: [...byProduct.values()].sort((a, b) => b.paid + b.passes - (a.paid + a.passes)),
    adFailReasons: Object.fromEntries(Object.entries(ev).filter(([k]) => k.startsWith("theme_free_"))),
    missingPrice,
  };
}

/** 가격표 저장(최고 관리자). 숫자만, 0~1,000,000원. */
export async function savePriceTable(request, env, now) {
  const body = await readJson(request, 8 * 1024);
  const num = (v) => {
    const x = Math.round(Number(v));
    if (!Number.isFinite(x) || x < 0 || x > 1_000_000) refuse("price", "price");
    return x;
  };
  const bands = (list, fallback) => (Array.isArray(list) ? list : fallback).slice(0, 8).map((b) => ({ cap: num(b.cap), monthly: num(b.monthly), yearly: num(b.yearly), pass: num(b.pass) }));
  const table = {
    bamnat_pro: { monthly: num(body?.bamnat_pro?.monthly ?? 0), yearly: num(body?.bamnat_pro?.yearly ?? 0) },
    bamnat_pro_pass_1y: { pass: num(body?.bamnat_pro_pass_1y?.pass ?? 0) },
    team: bands(body?.team, PRICE_DEFAULT.team), staff: bands(body?.staff, PRICE_DEFAULT.staff),
    fee: Math.min(30, num(body?.fee ?? 15)),
  };
  await run(env, "INSERT INTO settings (key, value) VALUES ('price_table', ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value", JSON.stringify(table));
  await audit(env, now, "price_table", "money", `프로 월 ${table.bamnat_pro.monthly} · 연 ${table.bamnat_pro.yearly} · 이용권 ${table.bamnat_pro_pass_1y.pass}`);
  return json({ ok: true, table });
}

/** 아침 요약에 넣을 어제 숫자. */
export async function moneyYesterday(env, now = Date.now()) {
  const day = kstDay(now - DAY);
  const from = Date.parse(`${day}T00:00:00+09:00`);
  const to = from + DAY;
  const r = await one(env, `SELECT (SELECT COUNT(*) FROM subs WHERE created_at >= ?1 AND created_at < ?2 AND trial = 0) AS started,
    (SELECT COUNT(*) FROM subs WHERE created_at >= ?1 AND created_at < ?2 AND trial = 1) AS trials,
    (SELECT COUNT(*) FROM subs WHERE stopped_at >= ?1 AND stopped_at < ?2) AS stopped,
    (SELECT COUNT(*) FROM year_passes WHERE purchased_at >= ?1 AND purchased_at < ?2) AS passes`, from, to);
  return { day, ...(r ?? {}) };
}

