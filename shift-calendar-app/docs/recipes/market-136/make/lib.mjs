// 1.36 장터 출시 작품 생성기의 공통 부품 (docs/plans/1.36-장터-출시작품-기획.md).
// 작품은 값만 담아요. 여기 있는 함수는 같은 값을 여러 작품에 손으로 다시 쓰지 않으려는 것뿐이고, 결과 JSON이 정본이에요.

/** "#RRGGBB" or "#AARRGGBB" → ARGB number (레시피 색 칸). */
export const c = (h) => parseInt((h.length === 7 ? "FF" : "") + h.slice(1), 16);
/** "#RRGGBB" → "FFRRGGBB" (화면 색·칸 모양 글 안의 색). */
export const H = (h) => (h.length === 7 ? "FF" + h.slice(1) : h.slice(1)).toUpperCase();
/** A colour with its alpha (0..255) for paints: "#RRGGBB", 0x66 → "66RRGGBB". */
export const HA = (h, a) => a.toString(16).padStart(2, "0").toUpperCase() + h.slice(1).toUpperCase();

export const STYLE_WORD = { SHIFT: "교대", DAY: "일근", HOURLY: "알바", FIELD: "현장", BUSINESS: "자영업", INVEST: "투자" };
export const ALL = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
/** Styles a shiftLook / alarms piece fits (서버 RECIPE_PIECES). */
export const ROSTER = ["SHIFT", "DAY", "HOURLY"];

/** A recipe: the fields every work has. [wow] is the "이렇게까지?" first line, [changes] the 바뀌는 것 lines, [note] 주의할 점. */
export function recipe({ title, summary, wow, who, changes, note, styles, cover, tokens, inputs, pieces }) {
  const lines = [wow];
  if (who) lines.push(`누구에게 좋아요: ${who}`);
  if (changes?.length) lines.push("적용하면 바뀌는 것:", ...changes.map((x) => `· ${x}`));
  if (note) lines.push(`주의할 점: ${note}`);
  const r = { format: "bamnat-recipe", v: 1, title, summary, desc: lines.join("\n"), styles, pieces: pieces.filter(Boolean) };
  if (cover) r.cover = { a: c(cover[0]), b: c(cover[1]), dir: cover[3] ?? 2, emoji: cover[2] };
  if (tokens?.length) r.tokens = tokens.map(([name, light, dark]) => ({ name, light: c(light), dark: c(dark ?? light) }));
  if (inputs?.length) r.inputs = inputs;
  return r;
}

export const tok = (name) => ({ token: name });
export const fx = (src) => ({ fx: src });

// ---- 근무 모양 (shiftLook) per kind of work ----
export function shiftLook(style, p) {
  const k = {
    SHIFT: [["주간", "day", "주", p.day, "☀️"], ["저녁", "eve", "저", p.eve, "🌆"], ["야간", "night", "야", p.night, "🌙"], ["휴무", "off", "휴", p.off, "🛌"]],
    DAY: [["출근", "day", "출", p.day, "💼"], ["재택", "other", "재", p.eve, "🏠"], ["당직", "long", "당", p.night, "🌙"], ["휴무", "off", "휴", p.off, "🌿"]],
    HOURLY: [["오픈", "day", "오", p.day, "🌅"], ["마감", "eve", "마", p.eve, "🌇"], ["야간", "night", "야", p.night, "🌙"], ["휴무", "off", "휴", p.off, "🛌"]],
  }[style];
  if (!k) return null;
  return { kind: "shiftLook", items: k.map(([name, role, label, color, emoji]) => ({ name, role, label, color: c(color), emoji })) };
}

// ---- 화면 색 (screenLook) ----
export function screenLook(p, style, extra = {}) {
  const all = [`bg:${H(p.bg)}`, `cd:${H(p.card)}`, `ik:${H(p.ink)}`, `ac:${H(p.accent)}`];
  if (p.bgPaint) all.push(`fl:${p.bgPaint}`);
  if (p.font) all.push(`ft:${p.font}`);
  const screens = [`all~${all.join("|")}`];
  for (const [k, v] of Object.entries(extra)) screens.push(`${k}~${v}`);
  const out = { kind: "screenLook", screens: screens.join("/") };
  if (style) out.pay = `${style}~tt:${p.payBig ?? "xl"}|pl:${H(p.card2 ?? p.card)}|mn:${H(p.accent)}|rw:${p.rows ?? "roomy"}|dy:1`;
  return out;
}

export const calTheme = (p, cell = "tint") => ({ kind: "calTheme", today: c(p.today ?? p.accent), sat: c(p.sat ?? "#1E88E5"), sun: c(p.sun ?? "#E53935"),
  holiday: c(p.holiday ?? p.sun ?? "#E53935"), cell });

export const memoLook = (p, o = {}) => ({ kind: "memoLook", layout: o.layout ?? "grid2", card: o.card ?? "own", cardColor: c(p.card), border: o.border ?? "thin",
  corner: Math.min(24, o.corner ?? p.r ?? 14), lines: o.lines ?? 4, title: o.title ?? "m", ...(o.bold === false ? {} : { bold: true }), ...(p.memoFont ? { font: p.memoFont } : {}),
  date: true, bg: c(p.bg) });

export const moneyLook = (p, o = {}) => ({ kind: "moneyLook", rows: o.rows ?? "roomy", amount: o.amount ?? "l", mark: o.mark ?? "emoji",
  income: c(p.income ?? "#2E7D32"), expense: c(p.expense ?? "#C62828"), dayHead: o.dayHead ?? "band" });

export const widget = (p, o = {}) => ({ kind: "widget", alpha: o.alpha ?? 92, theme: p.dark ? "dark" : "light", memo: o.memo ?? true, bg: c(p.card),
  ink: c(p.ink), corner: o.corner ?? Math.min(28, p.r ?? 18), text: o.text ?? "m" });

export const appFeel = (p, o = {}) => ({ kind: "appFeel", theme: p.dark ? "dark" : "light", accent: c(p.accent), ...(o.textStep !== undefined ? { textStep: o.textStep } : {}),
  ...(o.compact ? { compact: true } : {}), ...(o.tap ? { tap: o.tap } : {}), ...(o.hold ? { hold: o.hold } : {}), motion: o.motion ?? "tn:fade|td:glow|sp:n" });

// ---- 부품 꾸미기 (parts): a base set every theme carries, then its own lines ----
/** The parts for the whole app in palette [p], with colours as tokens (받는 휴대폰의 어두운 화면에서 저절로 어두운 색). */
export function baseParts(p, o = {}) {
  const lines = [
    { id: "app.bar", fill: tok("바탕"), shadow: 0 },
    { id: "app.bar.title", ...(p.titleFont ? { font: p.titleFont } : {}), weight: 700, ink: tok("글자") },
    { id: "app.tabs", fill: tok("카드"), r: o.tabsR ?? 0, shadow: 2 },
    { id: "app.tab", on: "sel", ink: tok("포인트"), weight: 700 },
    { id: "app.card", fill: tok("카드"), r: p.r ?? 16, line: p.cardLine ? "solid" : "none", ...(p.cardLine ? { lineW: 1, lineC: tok("선") } : {}), shadow: p.shadow ?? 1 },
    { id: "app.button", fill: tok("포인트"), r: p.btnR ?? 24, ink: c(p.onAccent ?? "#FFFFFF"), weight: 700 },
    { id: "app.chip", r: 20, line: "solid", lineW: 1, lineC: tok("선") },
    { id: "cal.head", ...(p.headPaint ? { paint: p.headPaint } : { fill: tok("카드") }), r: p.r ?? 16, pad: 10, out: 6 },
    { id: "cal.head.title", ...(p.titleFont ? { font: p.titleFont } : {}), size: p.titleSize ?? 22, weight: 800, ink: c(p.headInk ?? p.ink),
      ...(p.titleShadow ? { tshadow: 6, tshadowC: c(p.titleShadow), tshadowX: 0, tshadowY: 0 } : {}) },
    { id: "cal.week.day", on: "sun", ink: c(p.sun ?? "#E53935") },
    { id: "cal.week.day", on: "sat", ink: c(p.sat ?? "#1E88E5") },
    { id: "cal.cell", r: p.cellR ?? 10 },
    { id: "cal.cell", on: "today", line: "solid", lineW: 2, lineC: tok("포인트"), motion: p.todayMotion ?? "glow" },
    { id: "cal.cell.date", ...(p.dateFont ? { font: p.dateFont } : {}), weight: 600 },
    { id: "cal.cell", on: "dim", alpha: 45 },
    { id: "memo.card", fill: tok("카드"), r: p.r ?? 16, shadow: p.shadow ?? 1 },
    { id: "memo.card.title", weight: 700, ...(p.memoFont ? { font: p.memoFont } : {}) },
    { id: "money.day", fill: tok("카드"), r: 10, padX: 10 },
    { id: "money.amount", on: "out", ink: c(p.expense ?? "#C62828"), weight: 700 },
    { id: "money.amount", on: "in", ink: c(p.income ?? "#2E7D32"), weight: 700 },
    { id: "pay.total", ...(p.headPaint ? { paint: p.headPaint } : { fill: tok("포인트") }), r: p.r ?? 16, ink: c(p.headInk ?? p.onAccent ?? "#FFFFFF") },
    { id: "widget", fill: c(p.card), r: Math.min(28, p.r ?? 18), ...(p.font ? { font: p.font } : {}) },
    { id: "widget.cell", on: "today", fill: c(p.accent), ink: c(p.onAccent ?? "#FFFFFF") },
  ];
  return lines;
}
export const baseTokens = (p) => p.dark
  ? [["바탕", p.bg], ["카드", p.card], ["글자", p.ink], ["포인트", p.accent], ["선", p.line ?? "#3A3A3A"]]
  : [["바탕", p.bg, p.darkBg ?? "#121212"], ["카드", p.card, p.darkCard ?? "#1E1E1E"], ["글자", p.ink, p.darkInk ?? "#ECECEC"],
    ["포인트", p.accent, p.darkAccent ?? p.accent], ["선", p.line ?? "#DDDDDD", p.darkLine ?? "#3A3A3A"]];

/** Lines with the same (id, on, when) folded into one, later fields winning (같은 줄 두 번은 서버가 거절해요). */
export function mergeLines(lines) {
  const m = new Map();
  for (const l of lines) {
    const k = `${l.id}|${l.on ?? ""}|${l.when ?? ""}`;
    m.set(k, { ...(m.get(k) ?? {}), ...l });
  }
  return [...m.values()];
}
export const parts = (lines) => ({ kind: "parts", parts: mergeLines(lines) });
export const decor = (items) => ({ kind: "decor", items });
export const logic = (o) => ({ kind: "logic", ...(o.buttons?.length ? { buttons: o.buttons } : {}), ...(o.rules?.length ? { rules: o.rules } : {}) });
export const memoTpl = (items) => ({ kind: "memoTpl", items });
export const tabView = (o) => ({ kind: "tabView", ...o });

// ---- 꾸밈 층 ----
/** A sticker on the days of [on] (근무 종류·오늘·요일·날), top right of the cell. */
export const cellSticker = (emoji, on, o = {}) => ({ emoji, at: "cal.cell", ...(on ? { on } : {}), x: o.x ?? 820, y: o.y ?? 200, w: o.w ?? 13, h: o.h ?? 13,
  ...(o.alpha ? { alpha: o.alpha } : {}) });
export const cellShape = (shape, on, color, o = {}) => ({ shape, at: "cal.cell", ...(on ? { on } : {}), x: o.x ?? 500, y: o.y ?? 500, w: o.w ?? 40, h: o.h ?? 40,
  fill: c(color), fillA: o.fillA ?? 25, ...(o.rot ? { rot: o.rot } : {}) });
export const headText = (text, color, o = {}) => ({ text, at: "cal.head", x: o.x ?? 840, y: o.y ?? 500, w: o.w ?? 110, h: o.h ?? 28, ink: c(color),
  size: o.size ?? 12, weight: o.weight ?? 700, ...(o.font ? { font: o.font } : {}), ...(o.fill ? { fill: c(o.fill), fillA: o.fillA ?? 100, r: o.r ?? 14 } : {}) });
export const headIcon = (icon, color, o = {}) => ({ icon, at: "cal.head", x: o.x ?? 930, y: o.y ?? 500, w: o.w ?? 22, h: o.h ?? 22, ink: c(color) });
export const headShape = (shape, color, o = {}) => ({ shape, at: "cal.head", x: o.x ?? 900, y: o.y ?? 300, w: o.w ?? 60, h: o.h ?? 60, fill: c(color),
  fillA: o.fillA ?? 30, ...(o.rot ? { rot: o.rot } : {}) });
export const bgShape = (screen, shape, color, o = {}) => ({ shape, at: `bg.${screen}`, x: o.x ?? 900, y: o.y ?? 80, w: o.w ?? 120, h: o.h ?? 120,
  fill: c(color), fillA: o.fillA ?? 18, ...(o.rot ? { rot: o.rot } : {}) });
export const widgetSticker = (emoji, o = {}) => ({ emoji, at: "widget", x: o.x ?? 900, y: o.y ?? 120, w: o.w ?? 18, h: o.h ?? 18 });
