import { all, batch, one, stmt } from "./index.js";
import { DAY } from "./units.js";

// ---- 1.38 [ADM3-11] 사람 옆 패널(휴대폰 한 대를 중심으로) ----
//
// 보고서 3-3항 M1: 문의한 사람이 프로인지, 전에 무엇을 보냈는지, 쓰기 제한이 있는지를 한 화면에서. 기준은 설치마다의 해시(device)뿐이고
// 이름·연락처는 없어요(새로 모으지 않음). 관리자 앱은 해시 앞 6자('보낸 휴대폰')만 보여 주고, 해시 전체는 화면에 내보내지 않아요.
// 문의(inquiries 권한)를 가진 사람만 열어요. 결제·동의 칸은 최고 관리자나 보상(rewards) 권한이 있을 때만 채워요.

const PERSON_MARK = /^[0-9a-f]{6}$/;
const PERSON_INQ = /^[A-Za-z0-9]{8,32}$/;

/** 문의 번호나 '보낸 휴대폰' 6자로 그 설치의 해시를 찾아요. 6자가 둘 이상에 맞으면 ambiguous. */
export async function findDevice(env, { inquiry, mark }) {
  if (inquiry && PERSON_INQ.test(inquiry)) {
    const r = await one(env, "SELECT device FROM inquiries WHERE id = ?", inquiry);
    return r ? { device: r.device } : { missing: true };
  }
  if (mark && PERSON_MARK.test(mark)) {
    const rows = await all(env, `SELECT DISTINCT device FROM (
      SELECT device FROM inquiries WHERE device LIKE ?1 UNION SELECT device FROM subs WHERE device LIKE ?1
      UNION SELECT device FROM posts WHERE device LIKE ?1 UNION SELECT device FROM reports WHERE device LIKE ?1) LIMIT 3`, `${mark}%`);
    if (!rows.length) return { missing: true };
    if (rows.length > 1) return { ambiguous: rows.length };
    return { device: rows[0].device };
  }
  return { missing: true };
}

const ago = (now, at) => (at ? Math.max(0, Math.floor((now - at) / DAY)) : null);

/** 한 설치의 모든 것(관리자가 볼 수 있는 만큼). [money]는 결제·동의를 볼 수 있는지. */
export async function personView(env, device, now, { money = false } = {}) {
  const [inq, reports, posts, recipes, limit, strikes, integrity, members, invites, subs, passes, rewards, consents, share, care] = (await batch(env, [
    stmt(env, `SELECT i.id, i.kind, i.status, i.created_at, i.updated_at, i.info, i.billing_at,
      (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first
      FROM inquiries i WHERE i.device = ? ORDER BY i.created_at DESC LIMIT 30`, device),
    stmt(env, "SELECT status, COUNT(*) AS n FROM reports WHERE device = ? GROUP BY status", device),
    stmt(env, "SELECT status, COUNT(*) AS n, SUM(flags) AS flags FROM posts WHERE device = ? GROUP BY status", device),
    stmt(env, "SELECT state, COUNT(*) AS n, SUM(flags) AS flags FROM recipes WHERE owner = ? GROUP BY state", device),
    stmt(env, "SELECT step, since, until, per_day, times FROM write_limits WHERE device = ?", device),
    stmt(env, "SELECT kind, COUNT(*) AS n, MAX(at) AS last FROM write_strikes WHERE device = ? GROUP BY kind", device),
    stmt(env, "SELECT ok, why, at FROM integrity WHERE device = ?", device),
    stmt(env, "SELECT COUNT(*) AS n FROM members WHERE device = ? AND left = 0", device),
    stmt(env, "SELECT status, COUNT(*) AS n FROM invite_uses WHERE inviter = ? GROUP BY status", device),
    stmt(env, "SELECT product, base_plan, state, expiry, trial, auto_renew, consent_at, stopped_at, created_at FROM subs WHERE device = ? ORDER BY created_at DESC LIMIT 10", device),
    stmt(env, "SELECT product, state, purchased_at FROM year_passes WHERE device = ? ORDER BY purchased_at DESC LIMIT 10", device),
    stmt(env, "SELECT kind, SUM(days) AS days, COUNT(*) AS n, MAX(given_at) AS last FROM day_rewards WHERE device = ? GROUP BY kind", device),
    stmt(env, "SELECT kind, at FROM consents WHERE device = ? ORDER BY at DESC LIMIT 10", device),
    stmt(env, "SELECT guest IS NOT NULL AS gave, guest_at FROM pro_shares WHERE owner = ?", device),
    // 1.39 [CARE-05] 마을 돌봄: 경고 수와 지금 쉬기.
    stmt(env, `SELECT kind, COUNT(*) AS n, MAX(CASE WHEN kind != 'warn' AND lifted_at IS NULL AND until > ? THEN until END) AS until
      FROM care_notes WHERE device = ? GROUP BY kind`, now, device),
  ])).map((r) => r.results ?? []);
  const sumBy = (rows) => Object.fromEntries(rows.map((r) => [r.status ?? r.state ?? r.kind, r.n]));
  let app = null;
  let type = null;
  for (const q of inq) {
    try {
      const info = JSON.parse(q.info ?? "{}");
      app ??= info.app ?? null;
      type ??= info.type ?? info.workType ?? null;
    } catch {
      // 옛 문의.
    }
  }
  const activeSub = subs.find((s) => ["ACTIVE", "GRACE"].includes(s.state) && (s.expiry ?? 0) > now);
  const activePass = passes.find((p) => p.state === "ACTIVE" && p.purchased_at + 365 * DAY > now);
  const lim = limit[0];
  const pro = activeSub ? { by: "구독", product: activeSub.product, plan: activeSub.base_plan, trial: Boolean(activeSub.trial), renews: Boolean(activeSub.auto_renew),
    until: activeSub.expiry } : activePass ? { by: "1년 이용권", product: activePass.product, until: activePass.purchased_at + 365 * DAY } : null;
  const flags = [];
  if (lim && lim.until > now) flags.push({ tone: "bad", text: `쓰기 제한 ${lim.step}단계 · ${Math.ceil((lim.until - now) / DAY)}일 남음` });
  const careWarns = care.find((c) => c.kind === "warn")?.n ?? 0;
  const careHold = care.filter((c) => c.until).sort((a, b) => (a.kind === "community" ? -1 : 1))[0];
  if (careHold) flags.push({ tone: "bad", text: `${careHold.kind === "community" ? "마을 쉬기" : "의견 쉬기"} · ${Math.ceil((careHold.until - now) / DAY)}일 남음` });
  else if (careWarns) flags.push({ tone: "warn", text: `마을 경고 ${careWarns}번` });
  if (integrity[0] && !integrity[0].ok) flags.push({ tone: "warn", text: `Play 확인 안 됨(${integrity[0].why || "이유 모름"})` });
  if (pro) flags.push({ tone: "info", text: pro.trial ? "프로 체험 중" : `프로(${pro.by})` });
  const refundAsks = inq.filter((q) => q.billing_at || /환불|결제/.test(q.first ?? "")).length;
  if (refundAsks) flags.push({ tone: "warn", text: `결제·환불 문의 ${refundAsks}번` });
  return {
    mark: device.slice(0, 6), app, type, flags,
    inquiries: inq.map((q) => ({ id: q.id, kind: q.kind, status: q.status, at: q.created_at, days: ago(now, q.created_at), first: String(q.first ?? "").slice(0, 80), billing: Boolean(q.billing_at) })),
    counts: {
      reports: sumBy(reports), posts: sumBy(posts), postFlags: posts.reduce((n, r) => n + (r.flags ?? 0), 0), recipes: sumBy(recipes),
      recipeFlags: recipes.reduce((n, r) => n + (r.flags ?? 0), 0), strikes: Object.fromEntries(strikes.map((s) => [s.kind, s.n])), companies: members[0]?.n ?? 0,
      invited: sumBy(invites),
    },
    limit: lim ? { step: lim.step, until: lim.until, active: lim.until > now, perDay: lim.per_day, times: lim.times } : null,
    integrity: integrity[0] ? { ok: Boolean(integrity[0].ok), why: integrity[0].why, at: integrity[0].at } : null,
    money: money ? {
      pro,
      subs: subs.map((s) => ({ product: s.product, plan: s.base_plan, state: s.state, expiry: s.expiry, trial: Boolean(s.trial), renews: Boolean(s.auto_renew),
        consentAt: s.consent_at, stoppedAt: s.stopped_at, at: s.created_at })),
      passes: passes.map((p) => ({ product: p.product, state: p.state, at: p.purchased_at })),
      rewards: rewards.map((r) => ({ kind: r.kind, days: r.days, n: r.n, last: r.last })),
      consents: consents.map((c) => ({ kind: c.kind, at: c.at })),
      gaveShare: Boolean(share[0]?.gave),
    } : null,
  };
}
