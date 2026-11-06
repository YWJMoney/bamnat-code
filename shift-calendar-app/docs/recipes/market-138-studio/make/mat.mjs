// 1.38 [STM-07] 밤낮 재료(tools/materials/out)를 작품에서 쓰는 도우미: 아이콘·배경 이름 → 서버 그림 id, 배경마다 뽑은 색 한 벌.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const OUT = fileURLToPath(new URL("../../../../tools/materials/out/", import.meta.url));
const LIST = JSON.parse(readFileSync(OUT + "목록.json", "utf8")).items;
export const PALETTE = JSON.parse(readFileSync(OUT + "palette.json", "utf8"));
const BY_KEY = new Map(LIST.map((m) => [m.key, m]));

/** A 밤낮 재료 picture's server id by its key (아이콘 'moon', 배경 'n05'); unknown keys stop the build. */
export function mid(key) {
  const m = BY_KEY.get(key);
  if (!m) throw new Error(`밤낮 재료에 없어요: ${key}`);
  return m.id;
}
export const matName = (key) => BY_KEY.get(key)?.name;
export const isBg = (key) => BY_KEY.get(key)?.kind === "bg";
/** Every key used by the works, so index.mjs can say which materials no work uses yet. */
export const USED = new Set();
export const use = (key) => { USED.add(key); return mid(key); };
export const ALL_KEYS = LIST.map((m) => m.key);

// ---- 꾸밈 층의 재료 그림 ----
/**
 * A colour icon for the calendar's top. 1.38 PC 스튜디오 확인: 달력 머리 오른쪽(x 930)은 오늘·도구·통계 단추와 겹쳐서, 요일 줄의 글자 사이
 * (수·목 사이 x 571, 둘째는 화·수 사이 x 286)에 작게 놓아요. 요일 글자는 칸 가운데라 그 사이가 비어 있어요.
 */
export const headPic = (key, o = {}) => ({ photo: use(key), at: "cal.week", x: o.x ? 286 : 571, y: 500, w: 18, h: 18, ...(o.rot ? { rot: o.rot } : {}) });
/** A colour icon on the days of [on] (오늘·근무 종류·공휴일·요일·날짜), top right of the cell. */
export const cellPic = (key, on, o = {}) => ({ photo: use(key), at: "cal.cell", ...(on ? { on } : {}), x: o.x ?? 800, y: o.y ?? 220, w: o.w ?? 14, h: o.h ?? 14,
  ...(o.alpha ? { alpha: o.alpha } : {}) });
/** A colour icon on a screen's background (bg.<screen> or app), like a sticker in a corner. */
export const bgPic = (key, at, o = {}) => ({ photo: use(key), at, x: o.x ?? 880, y: o.y ?? 90, w: o.w ?? 56, h: o.h ?? 56, ...(o.rot ? { rot: o.rot } : {}),
  ...(o.alpha ? { alpha: o.alpha } : {}) });
/** A colour icon on the home-screen widget. */
export const widgetPic = (key, o = {}) => ({ photo: use(key), at: "widget", x: o.x ?? 905, y: o.y ?? 140, w: o.w ?? 22, h: o.h ?? 22 });
/** A 밤낮 배경 filling one screen ([screen] null: every screen). */
export const screenPic = (key, screen, o = {}) => ({ photo: use(key), at: screen ? `bg.${screen}` : "app", x: 500, y: 500, w: 140, h: 296, full: true,
  ...(o.alpha ? { alpha: o.alpha } : {}) });
