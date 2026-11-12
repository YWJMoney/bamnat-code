// 1.39 [PLZC-40] 제작자 표식 (사용자 2026-11-07 "유명한 제작자나 이달의 제작자 같은 사람들은 특별 태그나 이름 색상 … 인플루언서 같은 느낌"):
// 🏅 이달의 제작자(운영자가 고른 분, 또는 이번 달 쓰는 분이 가장 많은 셋)와 ⭐ 인기 제작자(팔로워 MARK_LIMITS.popular명 이상).
// 2.0.0 [RVQ-27] 검토 SVC-59: 🏅는 운영자가 고른 분만(센 셋은 관리자 앱 후보), ⭐는 이웃(3일) 이상인 팔로워만 세요.
// 광장 글과 장터 작품이 같은 표식을 써요(장터 스레드와 공용, 앱 core MakerMark). 깃발 plaza.people(처리방침 3.12 ⑤)이 켜진 뒤에만,
// 마을 돌봄의 경고·쉬기·쓰기 제한 중인 분은 표식이 쉬어요(careQuietMany). 팔로워 수는 프로필에 이미 보이는 값이라 늘 실어요.
import { careQuietMany } from "./community-care.js";
import { DAY, HOUR } from "./units.js";

export const MARK_LIMITS = { popular: 30, month: 3 };
const markMonth = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 7);

/**
 * Each of [makers] (public maker ids) with {mark?: "month"|"popular", followers}. [d] needs all and legalOpen (recipeDeps). Hidden or
 * closed profiles get nothing. Reads 90 ids at a time (D1 takes 100 values).
 */
export async function makerMarks(d, env, makers, now = Date.now()) {
  const out = new Map();
  const ids = [...new Set((makers ?? []).filter((m) => typeof m === "string" && m))];
  if (!ids.length) return out;
  const on = await d.legalOpen(env, "plaza.people", now);
  const month = markMonth(now);
  // 2.0.0 [RVQ-27] 검토 SVC-59: 🏅 is the operator's picks only (the month's counts are candidates in the admin app), so no fake install
  // buys it and no card opened sums the whole month again.
  const picked = on ? await d.all(env, "SELECT DISTINCT maker FROM recipe_month_makers WHERE month = ?", month) : [];
  const monthly = new Set(picked.map((r) => r.maker));
  // ⭐ counts the followers who are 이웃 or more (3 days on the 나눔 판, or a work the operator approved); the number on the card stays all.
  const settled = now - 3 * DAY;
  const devices = new Map();
  for (let i = 0; i < ids.length; i += 90) {
    const part = ids.slice(i, i + 90);
    const q = part.map(() => "?").join(",");
    const [who, follows] = await Promise.all([
      d.all(env, `SELECT maker, device FROM recipe_makers WHERE maker IN (${q}) AND hidden = 0 AND closed = 0`, ...part),
      d.all(env, `SELECT f.maker, COUNT(*) AS n, SUM(CASE WHEN t.first_at <= ? OR t.approved >= 1 THEN 1 ELSE 0 END) AS sure
        FROM recipe_follows f LEFT JOIN recipe_trust t ON t.device = f.device WHERE f.maker IN (${q}) GROUP BY f.maker`, settled, ...part),
    ]);
    for (const r of who) devices.set(r.maker, r.device);
    for (const r of follows) if (devices.has(r.maker)) out.set(r.maker, { followers: r.n, sure: r.sure ?? 0 });
  }
  for (const m of devices.keys()) if (!out.has(m)) out.set(m, { followers: 0, sure: 0 });
  if (!on) return out;
  const quiet = await careQuietMany(d, env, [...devices.values()], now);
  for (const [m, dev] of devices) {
    if (quiet.has(dev)) continue;
    const f = out.get(m);
    if (monthly.has(m)) f.mark = "month";
    else if (f.sure >= MARK_LIMITS.popular) f.mark = "popular";
  }
  return out;
}

/** The fields a card carries for [maker] from [marks]: {mark?, followers?}. */
export function markFields(marks, maker) {
  const m = maker ? marks.get(maker) : null;
  if (!m) return {};
  return { followers: m.followers, ...(m.mark ? { mark: m.mark } : {}) };
}
