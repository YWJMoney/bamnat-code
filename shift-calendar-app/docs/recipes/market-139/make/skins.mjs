// 1.39 [MKW-03] 탭별 작품의 미술 방향(바탕·글자·포인트 색, 글꼴 짝, 채우기, 모서리)을 달력 작품에서 이어 받아요. 같은 이름의 달력과
// 급여·메모·가계부·위젯·알람 작품이 한 세트처럼 어울리고, 탭마다 그 탭에 맞는 기능은 따로 더해요.
import { SPECS as A } from "./tab-calendar-a.mjs";
import { SPECS as B } from "./tab-calendar-b.mjs";
import { theme } from "./art.mjs";

const ALL = [...A, ...B];
const KEEP = ["style", "styles", "mood", "emoji", "p", "font", "r", "titleSize", "titleTrack", "titleWeight", "glow", "glowR", "shadow", "photo", "dim", "cardLine"];

/** The art direction of the calendar work [title]: colours, fonts, corners and its screen fill (not its cells, decor or view). */
export function skin(title) {
  const s = ALL.find((x) => x.title === title);
  if (!s) throw new Error(`skin: 달력 작품 '${title}'이 없어요`);
  const out = Object.fromEntries(Object.entries(s).filter(([k]) => KEEP.includes(k)));
  out.paint = s.paint?.screen ? { screen: s.paint.screen } : {};
  return out;
}

/** A work for one [tab] in the art direction of calendar work [s.from], with its own fields over it. */
export function tabWork(tab, s) {
  const { from, paint, p, font, ...rest } = s;
  const k = from ? skin(from) : {};
  return theme({ ...k, ...rest, p: { ...(k.p ?? {}), ...(p ?? {}) }, font: { ...(k.font ?? {}), ...(font ?? {}) },
    paint: { ...(k.paint ?? {}), ...(paint ?? {}) }, only: tab });
}
