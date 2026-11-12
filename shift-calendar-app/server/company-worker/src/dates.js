// 2.0.0 [RVA-59] 다가오는 날짜: 날짜 약속 장부(ops/dates.json → dates-built.js, 갈래 R RVR-38)를 지금 시각으로 셈해 자동 순환에 보여요.
import { kstDay } from "./usage.js";

const DAY_MS = 86_400_000;
/** KST 날짜 두 개(YYYY-MM-DD) 사이의 날 수(b − a). */
const between = (a, b) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / DAY_MS);
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * 장부 줄마다 오늘(KST)부터 남은 날. 지난 날은 빼요(오늘은 0). [gap]이 있는 줄은 [gap.from]까지 [gap.warnDays]일 안이면 '빈틈'으로 따로.
 * 읽지 못하는 줄(날짜 모양이 틀림)은 건너뛰어요(화면이 깨지지 않게).
 */
export function datesNow(rows, now) {
  const today = kstDay(now);
  const upcoming = [];
  const gaps = [];
  for (const r of Array.isArray(rows) ? rows : []) {
    if (!r || !DATE.test(String(r.date ?? ""))) continue;
    const left = between(today, r.date);
    if (left >= 0) upcoming.push({ id: String(r.id ?? ""), date: r.date, left, what: String(r.what ?? ""), area: String(r.area ?? ""), before: String(r.before ?? "") });
    const g = r.gap;
    if (g && DATE.test(String(g.from ?? "")) && Number.isFinite(g.warnDays)) {
      const until = between(today, g.from);
      if (until <= g.warnDays) gaps.push({ id: String(r.id ?? ""), from: g.from, left: until, what: String(g.what ?? ""), before: String(r.before ?? "") });
    }
  }
  upcoming.sort((a, b) => a.left - b.left || a.id.localeCompare(b.id));
  gaps.sort((a, b) => a.left - b.left);
  return { today, upcoming, gaps, soon: upcoming.filter((x) => x.left <= 14).length };
}
