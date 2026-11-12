import { all, audit, one, randomId, run } from "./index.js";

// ---- 1.36 [GRW-09] 만든 분 보상 (개발계획 902, 사용자 2026-11-01 12:29 KST 가안) ----
//
//   이달의 주제에 뽑힘        프로 30일(1달)
//   '모두의 도구'로 승격       프로 30일(1달), 작품마다 한 번
//   '기본 도구'로 채택         프로 90일(3달) + 출시 노트 이름(growth.js releaseLines) + 프로필 배지
// 2.0.0부터 모두 '드릴 보상'(owed)에 넣고 보상 관리자가 확인한 뒤 지급해요. 한 사람이 1년(365일) 동안 받는 보상은 종류를 합쳐 12달까지예요(money.js REWARD_CAP).
// 현금·옮길 수 있는 포인트는 주지 않아요('하지 않기' 목록). 지급할 때 프로 구독 중이면 Play 결제를 뒤로 미뤄 붙이고(deferFor),
// 아니면 보상 기간으로 쌓여요(money.js stack, 친구 초대와 같은 rewards 줄, rule_id 'maker').

export const MAKER_REWARD = {
  kinds: { contest: { months: 1, memo: "이달의 주제에 뽑힘" }, tool: { months: 1, memo: "모두의 도구로 승격" }, adopted: { months: 3, memo: "기본 도구로 채택" } },
  capMonthsYear: 12,
};

/**
 * [GRW-09] 만든 분께 드릴 프로 기간을 '드릴 보상'에 넣어요. [ref] 하나(작품 코드·공모 달)마다 한 번이에요. Returns { given: false, owed: true, months }
 * or null when the install is the operator's or it was asked before.
 * 2.0.0 [RVD-13] 사용자 결정 2026-11-12 '확인 뒤 지급': 검토 SVC-05('쓰는 분' 숫자는 한 사람이 부풀릴 수 있어요)와 SVC-98(커뮤니티 권한만으로
 * 채택·주제 고르기를 하면 보상 권한 없이 나갔어요) 때문에 바로 '지급됨'으로 두지 않아요. 보상 관리자가 관리자 앱 › 보상에서 작품의
 * 숫자를 보고 지급하면(setReward) 그때 이용권이 생기거나 Play 결제가 미뤄져요. 1년 12달은 모든 보상을 합쳐 지급할 때 봐요(money.js REWARD_CAP).
 */
export async function grantMakerReward(env, dev, kind, ref, now) {
  const k = MAKER_REWARD.kinds[kind];
  if (!k || !dev || dev === "operator") return null;
  const reportId = `maker:${kind}:${ref}`;
  if (await one(env, "SELECT id FROM rewards WHERE report_id = ?", reportId)) return null;
  const id = `m${randomId(21)}`;
  // The app's 보상 이용권 line reads the part before " (": what for and which work.
  const work = await one(env, "SELECT title FROM recipes WHERE code = ?", String(ref).split(":").pop());
  const title = work?.title ? ` · ${String(work.title).replace(/\s*\(.*$/, "").slice(0, 24)}` : "";
  await run(env, `INSERT OR IGNORE INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at, deferred)
    VALUES (?, ?, 'maker', ?, ?, 'owed', ?, ?, NULL, 0)`, id, reportId, dev, k.months, `${k.memo}${title} (${ref})`, now);
  await audit(env, now, "maker_reward_owed", id, `${kind}:${ref}`);
  return { given: false, owed: true, capped: false, months: k.months };
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
