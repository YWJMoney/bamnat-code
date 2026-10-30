// BC 1.30 [ITEM-04][PROP-01] 수당 종류(회사 공통 · 사람마다 · %)와 고치자는 제안의 설정 본문.
//
// 사용자 2026-10-27: 수당마다 정액인지, 사람마다 다른지, 어떤 금액의 %인지 작성자가 정하고, 쓰는 사람은 사람마다 다른 칸만 적어요.
// 결정 D102: 회사 공통 정액(FIXED)의 금액만 광장에 올라와요. 사람마다 다른(PERSONAL) 칸의 금액은 받지 않아요.
// index.js의 item()·canonical()·itemText()와 talk()가 이 파일의 함수를 불러요. 이 파일은 index.js를 불러오지 않아요(tools/bundle.mjs가
// 한 파일로 합쳐요).

/** Who sets an allowance's amount (core ItemShare). An allowance from before 1.30 has none and is PERSONAL. */
export const ITEM_SHARES = ["FIXED", "PERSONAL", "PERCENT"];
/** What a percent allowance is a percent of (core BonusBasis). */
export const ITEM_PERCENT_OF = ["BASE", "BASE_FIXED", "ORDINARY"];
/** Most a company's one amount may be, in 원. */
export const ITEM_FIXED_MAX = 10000000;
/** Most a percent may be, 5 = 500% (the app's ItemShares.MAX_PERCENT). */
export const ITEM_PERCENT_MAX = 5;

const PERCENT_OF_NAMES = { BASE: "기본급", BASE_FIXED: "기본급+고정수당", ORDINARY: "통상임금" };

/**
 * [ITEM-04] The 1.30 fields of one allowance [i] onto the checked [out]: kept only when not PERSONAL, so every setup from
 * before keeps its fingerprint. A percent is paid every month. An amount comes only as a company's one amount
 * (`fixedAmount` with FIXED); anywhere else it is refused with "amount", like every amount before 1.30. [bad] throws.
 */
export function readItemShare(i, out, bad) {
  const share = i.share ?? "PERSONAL";
  if (!ITEM_SHARES.includes(share)) bad("setup");
  if (share === "PERCENT") {
    const p = i.percent;
    if (out.cadence !== "MONTHLY" || typeof p !== "number" || !Number.isFinite(p) || p <= 0 || p > ITEM_PERCENT_MAX) bad("setup");
    const of = i.percentOf ?? "BASE";
    if (!ITEM_PERCENT_OF.includes(of)) bad("setup");
    out.share = share;
    out.percent = p;
    out.percentOf = of;
  } else if (share === "FIXED") {
    out.share = share;
  }
  if (i.fixedAmount !== undefined && i.fixedAmount !== null) {
    if (share !== "FIXED" || !Number.isInteger(i.fixedAmount) || i.fixedAmount < 1 || i.fixedAmount > ITEM_FIXED_MAX) bad("amount");
    out.fixedAmount = i.fixedAmount;
  }
  return out;
}

/**
 * [FIN130-15] The privacy policy version that lets a company's one amount (FIXED fixedAmount) come to the 광장 (core FlagCatalog
 * plaza.fixed_amount). 3.4 named it; [PRIV-05] 결정 D111 brings it forward to 3.9's date (정식 출시일).
 */
export const ITEM_FIXED_POLICY = "3.9";

/**
 * [FIN130-15] Before [ITEM_FIXED_POLICY] is in force the server takes no amount at all: a checked setup's company amounts are
 * dropped, quietly (not refused), so an older app or one whose plaza.fixed_amount was forced on still sends; the allowance stays
 * 회사 공통 and each person types it, as before 1.30. Changes [setup] in place and returns it.
 */
export function withoutFixedAmounts(setup) {
  for (const i of setup?.pay?.items ?? []) delete i.fixedAmount;
  return setup;
}

/** [ITEM-04] The 1.30 fields that make two allowances different rules (canonical); nothing for PERSONAL. */
export function itemShareCanon(i) {
  if (!i.share) return {};
  return {
    share: i.share,
    ...(i.share === "PERCENT" ? { percent: i.percent, percentOf: i.percentOf } : {}),
    ...(i.share === "FIXED" ? { fixedAmount: i.fixedAmount ?? null } : {}),
  };
}

const pctText = (x) => `${Math.round(x * 1000) / 10}%`;

/**
 * [ITEM-04] Who sets the amount, the same words as the app's SetupFacts.shareText: "회사 공통 100,000원", "기본급의 10%", and
 * "통상임금" when it is in it. Empty for a PERSONAL allowance out of 통상임금.
 */
export function itemShareText(i) {
  const share = i.share === "FIXED" ? `회사 공통${i.fixedAmount > 0 ? ` ${i.fixedAmount.toLocaleString("en-US")}원` : ""}`
    : i.share === "PERCENT" && typeof i.percent === "number" ? `${PERCENT_OF_NAMES[i.percentOf] ?? "기본급"}의 ${pctText(i.percent)}` : "";
  return [share, i.ordinary ? "통상임금" : ""].filter(Boolean).join(" · ");
}

/** Most proposals' setups read at once. */
const MAX_PROPOSALS = 50;

/**
 * [PROP-01] A member's talk: each proposal post carries its setup as the app reads a company's (`text`, the company named),
 * so 이 제안으로 내 월급 계산해 보기 works from the post itself, also when the company page lists no more than three versions.
 * One read for every proposal of the talk. [all] is index.js's query helper; [company] is { id, name }.
 */
export async function withProposalTexts(all, env, company, posts, { withoutPay = false } = {}) {
  const grps = [...new Set(posts.filter((p) => p.proposal?.setup).map((p) => p.proposal.setup))].slice(0, MAX_PROPOSALS);
  if (!grps.length) return posts;
  const rows = await all(env, `SELECT grp, setup FROM reports WHERE company_id = ? AND grp IN (${grps.map(() => "?").join(", ")})
    GROUP BY grp`, company.id, ...grps);
  const texts = new Map(rows.map((r) => {
    const setup = { ...JSON.parse(r.setup), company: company.name };
    // [J133-91] A writer who is not a member gets their own proposal back without a pay way they were never shown.
    if (withoutPay) delete setup.pay;
    return [r.grp, JSON.stringify(setup)];
  }));
  for (const p of posts) {
    const text = p.proposal?.setup ? texts.get(p.proposal.setup) : undefined;
    if (text) p.proposal.text = text;
  }
  return posts;
}
