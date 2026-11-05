// 1.38 Phase 3 분석판 (사용자 2026-11-05 "관리자 앱에도 넣어서 분석할 수 있게"): 팀·직원 요금제 by band (Play-confirmed purchases the
// server already keeps), and the 팀·가게 events from 사용 통계 (consenting phones only, daily counts). No team, shop, name, amount or
// staff detail is here, and none is asked of the phone.
import { all, batch, stmt } from "./index.js";

export const WORK_PRODUCTS = ["bamnat_team", "bamnat_staff"];
const WHO = { bamnat_team: "팀", bamnat_staff: "직원" };
const PLAN = /^c([0-9]{1,4})-(monthly|yearly)$/;
const PASS = /^bamnat_(team|staff)_pass_([0-9]{1,4})$/;

/** Each band's 월·연 구독 and 이용권: running, in trial, set to end, started and stopped in the period. */
export async function phase3Metrics(env, start, end, now, ev) {
  const [subRows, passRows] = (await batch(env, [
    stmt(env, `SELECT product, base_plan AS plan,
      SUM(CASE WHEN state IN ('ACTIVE', 'GRACE') AND expiry > ? AND trial = 0 THEN 1 ELSE 0 END) AS paid,
      SUM(CASE WHEN state IN ('ACTIVE', 'GRACE') AND expiry > ? AND trial = 1 THEN 1 ELSE 0 END) AS trials,
      SUM(CASE WHEN state IN ('ACTIVE', 'GRACE') AND expiry > ? AND auto_renew = 0 THEN 1 ELSE 0 END) AS ending,
      SUM(CASE WHEN created_at >= ? AND created_at < ? THEN 1 ELSE 0 END) AS started,
      SUM(CASE WHEN stopped_at >= ? AND stopped_at < ? THEN 1 ELSE 0 END) AS stopped
      FROM subs WHERE product IN (${WORK_PRODUCTS.map(() => "?").join(", ")}) GROUP BY product, base_plan`,
    now, now, now, start, end, start, end, ...WORK_PRODUCTS),
    stmt(env, `SELECT product, SUM(CASE WHEN purchased_at > ? THEN 1 ELSE 0 END) AS running,
      SUM(CASE WHEN purchased_at >= ? AND purchased_at < ? THEN 1 ELSE 0 END) AS bought
      FROM year_passes WHERE state = 'ACTIVE' AND product <> 'bamnat_pro_pass_1y' GROUP BY product`, now - 365 * 86400000, start, end),
  ])).map((x) => x.results);
  const bands = new Map();
  const band = (who, cap) => {
    const k = `${who}|${cap}`;
    if (!bands.has(k)) bands.set(k, { who, cap, monthly: 0, yearly: 0, trials: 0, ending: 0, started: 0, stopped: 0, passes: 0, passesBought: 0 });
    return bands.get(k);
  };
  for (const r of subRows) {
    const m = PLAN.exec(r.plan ?? "");
    const b = band(WHO[r.product] ?? r.product, m ? Number(m[1]) : 0);
    if (m?.[2] === "yearly") b.yearly += r.paid ?? 0; else b.monthly += r.paid ?? 0;
    b.trials += r.trials ?? 0; b.ending += r.ending ?? 0; b.started += r.started ?? 0; b.stopped += r.stopped ?? 0;
  }
  for (const r of passRows) {
    const m = PASS.exec(r.product ?? "");
    if (!m) continue;
    const b = band(m[1] === "team" ? "팀" : "직원", Number(m[2]));
    b.passes += r.running ?? 0; b.passesBought += r.bought ?? 0;
  }
  const list = [...bands.values()].sort((a, b) => (a.who === b.who ? a.cap - b.cap : a.who === "팀" ? -1 : 1));
  const n = (e) => ev.get(e)?.phones ?? 0;
  const t = (e) => ev.get(e)?.total ?? 0;
  const paidOf = (who) => list.filter((b) => b.who === who).reduce((s, b) => s + b.monthly + b.yearly + b.passes, 0);
  return {
    bands: list,
    // 무료 맛보기 → 유료: phones that confirmed a team (or closed a ledger) in the period, against the plans running now.
    team: { made: t("team_made"), confirms: t("team_confirm"), confirmPhones: n("team_confirm"), links: t("team_links"), planView: n("plan_view_team"), paid: paidOf("팀") },
    shop: { closed: t("ledger_closed"), closePhones: n("ledger_closed"), slips: t("slip_sent"), planView: n("plan_view_staff"), paid: paidOf("직원") },
    funnel: { manage: n("manage_open"), hintShown: n("manage_hint_shown"), hintOpen: n("manage_hint_open"), planView: n("plan_view_team") + n("plan_view_staff"), buyTap: n("plan_buy_tap"),
      started: list.reduce((s, b) => s + b.started + b.passesBought, 0) },
    errors: t("manage_error"),
  };
}
