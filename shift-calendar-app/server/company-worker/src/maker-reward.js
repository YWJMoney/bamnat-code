import { all, audit, iso, one, randomId, run } from "./index.js";
import { DAY } from "./units.js";
import { deferFor } from "./money.js";

// ---- 1.36 [GRW-09] 만든 분 보상 (개발계획 902, 사용자 2026-11-01 12:29 KST 가안) ----
//
//   이달의 주제에 뽑힘        프로 30일(1달)
//   '모두의 도구'로 승격       프로 30일(1달), 작품마다 한 번
//   '기본 도구'로 채택         프로 90일(3달) + 출시 노트 이름(growth.js releaseLines) + 프로필 배지
// 한 사람(설치)이 1년(365일) 동안 받는 것은 12달까지예요. 넘는 것은 'capped'로 남기고 주지 않아요.
// 현금·옮길 수 있는 포인트는 주지 않아요('하지 않기' 목록). 프로 구독 중이면 Play 결제를 뒤로 미뤄 붙이고(deferFor),
// 아니면 보상 기간으로 쌓여요(money.js stack, 친구 초대와 같은 rewards 줄, rule_id 'maker').

export const MAKER_REWARD = {
  kinds: { contest: { months: 1, memo: "이달의 주제에 뽑힘" }, tool: { months: 1, memo: "모두의 도구로 승격" }, adopted: { months: 3, memo: "기본 도구로 채택" } },
  capMonthsYear: 12,
};

/**
 * [GRW-09] 만든 분께 프로 기간을 줘요. [ref] 하나(작품 코드·공모 달)마다 한 번이에요. Returns { given, capped, months } or null when the
 * install is the operator's or it was given before.
 */
export async function grantMakerReward(env, dev, kind, ref, now) {
  const k = MAKER_REWARD.kinds[kind];
  if (!k || !dev || dev === "operator") return null;
  const reportId = `maker:${kind}:${ref}`;
  if (await one(env, "SELECT id FROM rewards WHERE report_id = ?", reportId)) return null;
  const year = await one(env, "SELECT COALESCE(SUM(months), 0) AS n FROM rewards WHERE device = ? AND rule_id = 'maker' AND status = 'paid' AND created_at > ?",
    dev, now - 365 * DAY);
  const capped = year.n + k.months > MAKER_REWARD.capMonthsYear;
  const id = `m${randomId(21)}`;
  let deferred = null;
  if (!capped) {
    try {
      deferred = await deferFor(env, dev, k.months, now);
    } catch (e) {
      console.error(`defer maker: ${e?.code ?? e}`);
    }
  }
  // The app's 보상 이용권 line reads the part before " (": what for and which work.
  const work = await one(env, "SELECT title FROM recipes WHERE code = ?", String(ref).split(":").pop());
  const title = work?.title ? ` · ${String(work.title).replace(/\s*\(.*$/, "").slice(0, 24)}` : "";
  const memo = `${k.memo}${title} (${ref})${deferred ? ` · Play 결제 미룸(다음 결제 ${iso(deferred).slice(0, 10)})` : ""}${capped ? " · 1년 12달 넘음" : ""}`;
  await run(env, `INSERT OR IGNORE INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at, deferred)
    VALUES (?, ?, 'maker', ?, ?, ?, ?, ?, ?, ?)`, id, reportId, dev, k.months, capped ? "capped" : "paid", memo, now, capped ? null : now, deferred ? 1 : 0);
  await audit(env, now, capped ? "maker_reward_capped" : "maker_reward", id, `${kind}:${ref}`);
  return { given: !capped, capped, months: k.months };
}

/** [GRW-09] 이달의 주제 공모에서 고른 작품들의 만든 분께 (마을 갈래가 고르기 저장 때 불러요). [month] "2026-11". */
export async function rewardContestPicks(env, codes, month, now) {
  const out = [];
  for (const code of codes) {
    const r = await one(env, "SELECT owner FROM recipes WHERE code = ?", code);
    if (r) out.push({ code, ...(await grantMakerReward(env, r.owner, "contest", `${month}:${code}`, now) ?? { given: false }) });
  }
  return out;
}

/** What a maker got, for the app's 프로 기간 내역 ('maker' rewards). */
export async function makerRewards(env, dev) {
  return all(env, "SELECT months, status, memo, created_at FROM rewards WHERE device = ? AND rule_id = 'maker' ORDER BY created_at DESC LIMIT 20", dev);
}
