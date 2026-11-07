// 1.39 [MKW-01] 정상급 테마 한 벌을 짜는 틀: 작품마다 '미술 방향' 하나(글꼴 짝·채우기·부품 디자인)를 받아, 장터 넘겨 보기의 여섯 화면
// (달력 → 가계부 → 메모 → 급여 → 위젯 → 알람)을 같은 디자인으로 채워요. 1.38 테마(배경 한 장 + 유리 카드)와 달리 머리·요일·칸·탭 막대·카드·
// 메모 종이·가계부 줄·급여 합계·위젯을 부품 설계도로 하나하나 그려요. 값만 담아요(D106): 그림은 밤낮 재료와 앱이 그리는 채우기뿐이에요.
import { c, H, recipe, shiftLook, ALL, ROSTER, tok } from "../../market-136/make/lib.mjs";
import { KITS, noteFor } from "../../market-136/make/build-theme.mjs";
import { use } from "../../market-138-studio/make/mat.mjs";

/** "#RRGGBB" + alpha 0..255 → "AARRGGBB" for paints. */
export const A = (h, a) => a.toString(16).padStart(2, "0").toUpperCase() + h.slice(1).toUpperCase();
/** Solid paint. */
export const so = (h, a = 255) => `so.${A(h, a)}`;
/** Linear gradient: angle, colours ("#RRGGBB" or [hex, alpha] or [hex, alpha, at]). */
const stop = (s) => (Array.isArray(s) ? A(s[0], s[1] ?? 255) + (s[2] !== undefined ? `@${s[2]}` : "") : A(s, 255));
export const lg = (angle, ...stops) => `lg.${angle}.${stops.map(stop).join(",")}`;
export const rg = (cx, cy, r, ...stops) => `rg.${cx}.${cy}.${r}.${stops.map(stop).join(",")}`;
export const sg = (cx, cy, turn, ...stops) => `sg.${cx}.${cy}.${turn}.${stops.map(stop).join(",")}`;
export const pt = (kind, ink, ground, size = 100, angle = 0) => `pt.${kind}.${stop(ink)}.${stop(ground)}.${size}.${angle}`;
export const tx = (kind, tint, strength = 40) => `tx.${kind}.${stop(tint)}.${strength}`;
export const gl = (tint, a, blur = 16) => `gl.${A(tint, a)}.${blur}`;
export const sh = (kind, colors, speed = 2) => `sh.${kind}.${colors.map(stop).join(",")}.${speed}`;

/** Relative luminance and contrast (WCAG), to keep every text readable. */
export function lum(h) {
  const v = [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255).map((x) => (x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4));
  return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
}
export const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m); return (x + 0.05) / (y + 0.05); };

const ROLE_BY_STYLE = { SHIFT: ["day", "eve", "night", "off"], DAY: ["day", "other", "long", "off"], HOURLY: ["day", "eve", "night", "off"] };

/** A cell look ("칸 디자인") from short options. */
export function cellLook(o) {
  return [
    o.bg ? `bg:${o.bg === "shift" || o.bg === "none" ? o.bg : H(o.bg)}` : null, o.ba !== undefined ? `ba:${o.ba}` : null,
    o.bd ? `bd:${o.bd}` : null, o.bc ? `bc:${H(o.bc)}` : null, o.cr !== undefined ? `cr:${o.cr}` : null, o.dp ? `dp:${o.dp}` : null,
    o.ds ? `ds:${o.ds}` : null, o.dw ? `dw:${o.dw}` : null, o.dsh ? `dsh:${o.dsh}` : null, o.df ? `df:${H(o.df)}` : null,
    o.dc ? `dc:${H(o.dc)}` : null, o.mk ? `mk:${o.mk}` : null, o.mp ? `mp:${o.mp}` : null, o.pd !== undefined ? `pd:${o.pd}` : null,
    o.fl ? `fl:${o.fl}` : null, o.bw ? `bw:${o.bw}` : null, o.bo !== undefined ? `bo:${o.bo}` : null, o.ff ? `ff:${o.ff}` : null,
  ].filter(Boolean).join("|");
}

/**
 * 테마 한 벌. s = {
 *   title, style (null: 모든 유형), mood (밝은·어두운·파스텔·선명한·차분한), emoji, summary, wow, changes, who, note, top,
 *   p: { bg, card, ink, sub, accent, accent2, onAccent, line, dark, sat, sun, holiday, income, expense, day, eve, night, off, long, other },
 *   font: { body, title, date, memo, num },
 *   paint: { screen, card, head, tabs, cell, paper, widget, total }, photo: 배경 재료 key, dim,
 *   r: { card, cell, tabs, btn }, tabsFloat, titleSize, titleTrack, glow (제목 빛 색),
 *   cal: { size, badge, date, text, grid }, looks: [cellLook…], pattern, today,
 *   parts: [더 얹을 부품 줄], cells: [조건 줄], decor: [꾸밈], buttons, rules, tpls, alarms, view, motion, memo, money, widget, pay, extra
 * }
 */
export function theme(s) {
  const style = s.style ?? null;
  const kit = style ? KITS[style] : null;
  const p = { sub: null, line: null, onAccent: "#FFFFFF", ...s.p };
  p.sub = p.sub ?? (p.dark ? "#A9ADBA" : "#5F6170");
  p.line = p.line ?? (p.dark ? "#33384A" : "#E2DED6");
  const f = { body: "notosanskr", ...s.font };
  f.title = f.title ?? f.body;
  f.date = f.date ?? f.body;
  f.memo = f.memo ?? f.body;
  f.num = f.num ?? f.date;
  const pa = s.paint ?? {};
  const r = { card: 16, cell: 10, tabs: 24, btn: 22, ...(s.r ?? {}) };
  const roster = style && ROSTER.includes(style);

  // ---- 화면 색·배경 (모든 화면, 알람은 한 판 어둡게) ----
  const all = [`bg:${H(p.bg)}`];
  if (s.photo) all.push(`ph:${use(s.photo)}`, `dm:${s.dim ?? (p.dark ? 20 : 10)}`);
  if (pa.screen) all.push(`fl:${pa.screen}`);
  all.push(`cd:${H(p.card)}`, `ik:${H(p.ink)}`, `ac:${H(p.accent)}`, `ft:${f.body}`);
  const scr = [`all~${all.join("|")}`];
  for (const [k, v] of Object.entries(s.screens ?? {})) scr.push(`${k}~${v}`);
  const alarmBg = p.alarmBg ?? (p.dark ? p.bg : "#0E0F14");
  const alarm = [`bg:${H(alarmBg)}`, `fl:${pa.alarm ?? (p.dark && pa.screen ? pa.screen : lg(180, alarmBg, p.alarmTo ?? "#1B1D27"))}`, `ik:FFFFFFFF`,
    `ac:${H(p.alarmAccent ?? p.accent)}`, `ft:${f.title}`];
  if (s.photo && s.alarmPhoto !== false) alarm.push(`ph:${use(s.photo)}`, `dm:${Math.min(80, (s.dim ?? 20) + 40)}`);
  if (!s.screens?.alarm) scr.push(`alarm~${alarm.join("|")}`);
  const payStyles = style ? [style] : ["SHIFT", "DAY", "HOURLY", "FIELD"];
  const payWord = (st) => `${st}~tt:${s.pay?.tt ?? "xl"}|pl:${H(p.payCard ?? p.card)}|mn:${H(p.accent)}|rw:${s.pay?.rw ?? "roomy"}|dy:1`;
  const screenLook = { kind: "screenLook", screens: scr.join("/"),
    ...(style === "BUSINESS" || style === "INVEST" ? {} : { pay: payStyles.map(payWord).join("/") }) };

  // ---- 달력 ----
  const calTheme = { kind: "calTheme", today: c(p.today ?? p.accent), sat: c(p.sat ?? "#3D7BD9"), sun: c(p.sun ?? "#D9473D"),
    holiday: c(p.holiday ?? p.sun ?? "#D9473D"), cell: s.cell ?? "tint" };
  const calView = { kind: "calView", size: "tall", badge: "small", date: "center", text: "normal", grid: "none", ...(s.cal ?? {}) };
  if (s.looks?.length) calView.looks = s.looks.join("/");
  if (s.pattern) calView.pattern = s.pattern;
  if (s.today !== undefined) calView.today = s.today;

  // ---- 부품 설계도: 앱·달력·메모·가계부·급여·위젯 ----
  const card = pa.card ? { paint: pa.card } : { fill: c(p.card) };
  const lineOf = (o) => (o ? { line: o.line ?? "solid", lineW: o.w ?? 1, lineC: c(o.c ?? p.line), ...(o.a !== undefined ? { lineA: o.a } : {}) } : {});
  const glow = s.glow ? { tshadow: s.glowR ?? 8, tshadowC: c(s.glow), tshadowX: 0, tshadowY: 0 } : {};
  const shadow = s.shadow ?? (p.dark ? 0 : 2);
  const lines = [
    { id: "app", font: f.body, ink: c(p.ink) },
    { id: "app.bar", fill: c("#00000000"), shadow: 0 },
    { id: "app.bar.title", font: f.title, weight: s.titleWeight ?? 800, ink: c(p.ink), track: s.titleTrack ?? 0 },
    { id: "app.tabs", ...(pa.tabs ? { paint: pa.tabs } : { fill: c(p.card) }), r: r.tabs, ...(s.tabsFloat !== false ? { out: 10 } : {}),
      shadow: s.tabsShadow ?? (p.dark ? 4 : 6), ...lineOf(s.tabsLine) },
    { id: "app.tab", ink: c(p.sub) },
    { id: "app.tab", on: "sel", ink: c(p.tabSel ?? p.accent), weight: 800 },
    { id: "app.card", ...card, r: r.card, shadow, ...(s.cardShadowC ? { shadowC: c(s.cardShadowC) } : {}), ...lineOf(s.cardLine) },
    { id: "app.button", fill: c(p.accent), ink: c(p.onAccent), r: r.btn, weight: 800, font: f.title },
    { id: "app.chip", r: r.btn, ...lineOf({ c: p.line }), ink: c(p.ink) },
    { id: "app.sheet", fill: c(p.sheet ?? p.card), r: Math.max(r.card, 20) },
    { id: "cal.head", ...(pa.head ? { paint: pa.head } : { fill: c("#00000000") }), r: r.card, pad: 12, out: 6, ...lineOf(s.headLine) },
    { id: "cal.head.title", font: f.title, size: s.titleSize ?? 24, weight: s.titleWeight ?? 800, ink: c(p.headInk ?? p.ink), track: s.titleTrack ?? 0, ...glow },
    { id: "cal.head.nav", ink: c(p.headInk ?? p.ink) },
    { id: "cal.head.today", ink: c(p.accent), weight: 800 },
    { id: "cal.week.day", font: f.date, size: 11, weight: 700, ink: c(p.sub), track: s.weekTrack ?? 6 },
    { id: "cal.week.day", on: "sun", ink: c(p.sun ?? "#D9473D") },
    { id: "cal.week.day", on: "sat", ink: c(p.sat ?? "#3D7BD9") },
    { id: "cal.cell", r: r.cell, ...(pa.cell ? { paint: pa.cell } : {}), ...lineOf(s.cellLine) },
    { id: "cal.cell", on: "today", ...(pa.today ? { paint: pa.today } : {}), line: "solid", lineW: 2, lineC: c(p.todayLine ?? p.accent), motion: s.todayMotion ?? "glow" },
    { id: "cal.cell", on: "dim", alpha: 35 },
    { id: "cal.cell.date", font: f.date, weight: s.dateWeight ?? 700, ink: c(p.ink) },
    { id: "cal.cell.date", on: "sun", ink: c(p.sun ?? "#D9473D") },
    { id: "cal.cell.date", on: "sat", ink: c(p.sat ?? "#3D7BD9") },
    { id: "cal.cell.date", on: "holiday", ink: c(p.holiday ?? p.sun ?? "#D9473D") },
    { id: "cal.cell.mark", font: f.title, weight: 800 },
    { id: "cal.cell.memo", ink: c(p.sub), size: 9 },
    { id: "cal.foot", ...card, r: r.card },
    { id: "memo.head", fill: c("#00000000") },
    { id: "memo.card", ...(pa.memoCard ? { paint: pa.memoCard } : card), r: r.card, shadow, ...lineOf(s.memoLine ?? s.cardLine) },
    { id: "memo.card.title", font: f.memo, weight: 800, ink: c(p.ink), size: 15 },
    { id: "memo.card.body", font: f.memo, ink: c(p.sub) },
    { id: "memo.card.date", font: f.date, ink: c(p.accent), weight: 700, size: 11 },
    { id: "memo.page", ...(pa.paper ? { paint: pa.paper } : { fill: c(p.paper ?? p.card) }), font: f.memo, ink: c(p.paperInk ?? p.ink) },
    { id: "memo.page.rule", line: s.rule ?? "solid", lineW: 1, lineC: c(p.ruleC ?? p.line) },
    { id: "money.head", ...(pa.head ? { paint: pa.head } : card), r: r.card, ink: c(p.headInk ?? p.ink) },
    { id: "money.day", ...(pa.band ? { paint: pa.band } : { fill: c(p.band ?? p.card) }), r: Math.min(12, r.card), padX: 12, font: f.date, ink: c(p.ink), weight: 700 },
    { id: "money.row", r: Math.min(12, r.card) },
    { id: "money.amount", font: f.num, weight: 800 },
    { id: "money.amount", on: "out", ink: c(p.expense ?? "#C4402F") },
    { id: "money.amount", on: "in", ink: c(p.income ?? "#2C8A4B") },
    { id: "pay.head", font: f.title, ink: c(p.ink) },
    { id: "pay.total", ...(pa.total ? { paint: pa.total } : { fill: c(p.accent) }), r: r.card + 4, ink: c(p.totalInk ?? p.onAccent), font: f.num, shadow: shadow + 2 },
    { id: "pay.row", font: f.body },
    { id: "pay.row", on: "in", ink: c(p.income ?? "#2C8A4B") },
    { id: "pay.row", on: "out", ink: c(p.expense ?? "#C4402F") },
    { id: "widget", fill: c(p.widgetBg ?? p.card), ...(s.widgetA !== undefined ? { fillA: s.widgetA } : {}), r: Math.min(28, r.card + 6), font: f.body, ink: c(p.ink) },
    { id: "widget.head", font: f.title, weight: 800, ink: c(p.widgetHead ?? p.accent) },
    { id: "widget.cell", on: "today", fill: c(p.accent), ink: c(p.onAccent) },
    { id: "widget.cell", on: "sun", ink: c(p.sun ?? "#D9473D") },
  ];
  for (const [role, col] of Object.entries(s.roleCells ?? {})) lines.push({ id: "cal.cell", on: `role.${role}`, ...col });
  const parts = { kind: "parts", parts: merge([...lines, ...(s.parts ?? []), ...(s.cells ?? [])]) };
  if (parts.parts.length > 96) throw new Error(`${s.title}: 부품 줄이 ${parts.parts.length}개예요(96까지)`);

  // ---- 다른 화면 ----
  const memoLook = { kind: "memoLook", layout: "grid2", card: "own", cardColor: c(p.card), border: s.memoLine || s.cardLine ? "thin" : "none",
    corner: Math.min(24, r.card), lines: 4, title: "m", bold: true, font: f.memo, date: true, bg: c(p.bg), ...(s.memo ?? {}) };
  const moneyLook = { kind: "moneyLook", rows: "roomy", amount: "l", mark: "emoji", income: c(p.income ?? "#2C8A4B"), expense: c(p.expense ?? "#C4402F"),
    dayHead: "band", ...(s.money ?? {}) };
  const widget = { kind: "widget", alpha: s.widgetAlpha ?? 94, theme: p.dark ? "dark" : "light", memo: true, bg: c(p.widgetBg ?? p.card), ink: c(p.ink),
    corner: Math.min(28, r.card + 6), text: "m", ...(s.widget ?? {}) };
  const view = { kind: "tabView", ...(kit?.view ?? { cal: "month", memo: "board", memoBy: "tag", money: "cal" }), ...(s.view ?? {}) };
  const feel = { kind: "appFeel", theme: p.dark ? "dark" : "light", accent: c(p.accent), motion: s.motion ?? "tn:fade|td:glow|sp:n",
    ...(s.textStep ? { textStep: s.textStep } : {}) };
  const shift = roster ? shiftLook(style, { day: p.day ?? p.accent, eve: p.eve ?? "#E8892B", night: p.night ?? "#3F4AA8", off: p.off ?? "#9A9AA6" }) : null;
  if (shift && s.labels) shift.items = shift.items.map((it) => ({ ...it, ...(s.labels[it.role] ?? {}) }));
  if (shift && p.long && style === "DAY") shift.items = shift.items.map((it) => (it.role === "long" ? { ...it, color: c(p.long) } : it));
  if (shift && p.other && style === "DAY") shift.items = shift.items.map((it) => (it.role === "other" ? { ...it, color: c(p.other) } : it));

  const rules = [...(s.rules ?? []), ...(kit && s.kitRules !== false ? kit.rules : [])];
  const seen = new Set();
  const ruleItems = rules.filter((x) => !seen.has(x.name) && seen.add(x.name)).slice(0, 12);
  const tpls = [...(s.tpls ?? []), ...(kit && s.kitTpls !== false ? kit.tpls : [])];
  const seenT = new Set();
  const tplItems = tpls.filter((t) => !seenT.has(t.title) && seenT.add(t.title)).slice(0, 8);
  const roleIcons = roster && s.roleIcons ? Object.entries(s.roleIcons).filter(([ro]) => ROLE_BY_STYLE[style].includes(ro)).map(([role, key]) => ({ role, image: use(key) })) : [];

  const pieces = [
    screenLook, calView, calTheme, shift,
    roleIcons.length ? { kind: "photoLook", icons: roleIcons, corners: s.iconCorners ?? "round" } : null,
    memoLook, moneyLook, widget, view, feel, parts,
    s.decor?.length ? { kind: "decor", items: s.decor } : null,
    ruleItems.length || s.buttons?.length ? { kind: "logic", ...(s.buttons?.length ? { buttons: s.buttons } : {}), ...(ruleItems.length ? { rules: ruleItems } : {}) } : null,
    tplItems.length ? { kind: "memoTpl", items: tplItems } : null,
    roster && s.alarms !== null && kit ? (s.alarms ?? kit.alarms) : null,
    kit && s.piece !== null ? (s.piece ?? kit.piece ?? null) : null,
    ...(s.extra ?? []),
  ].filter(Boolean);
  const tokens = p.dark
    ? [["바탕", p.bg], ["카드", p.card], ["글자", p.ink], ["포인트", p.accent], ["선", p.line]]
    : [["바탕", p.bg, "#121212"], ["카드", p.card, "#1E1E1E"], ["글자", p.ink, "#ECECEC"], ["포인트", p.accent, p.accent], ["선", p.line, "#3A3A3A"]];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who ?? kit?.who ?? "어느 일을 하든 앱 전체를 한 번에 바꾸고 싶은 분",
      changes: s.changes, note: s.note ?? (style ? noteFor(style) : "기록은 그대로예요. 규칙은 넣기 전에 한 번 묻고, 스튜디오 › 규칙·단추에서 끄거나 되돌릴 수 있어요"),
      styles: style ? [style] : (s.styles ?? ALL), cover: s.cover ?? [p.coverA ?? p.bg, p.accent, s.emoji], tokens, inputs: s.inputs ?? kit?.inputs, pieces }),
    mood: s.mood,
    ...(s.top ? { showcase: true } : {}),
  };
}

/** Lines with the same (id, on, when) folded into one, later fields winning (같은 줄 두 번은 서버가 거절해요). */
export function merge(list) {
  const m = new Map();
  for (const l of list) {
    const k = `${l.id}|${l.on ?? ""}|${l.when ?? ""}`;
    m.set(k, { ...(m.get(k) ?? {}), ...l });
  }
  return [...m.values()];
}

/** Every text colour a theme sets, checked against the ground it sits on (WCAG 4.5:1 small text, 3:1 big). */
export function readable(s) {
  const p = s.p;
  const out = [];
  const need = (what, ink, ground, min = 4.5) => { if (ink && ground && contrast(ink, ground) < min) out.push(`${what} ${contrast(ink, ground).toFixed(2)}:1`); };
  need("글자/카드", p.ink, p.card);
  need("보조 글자/카드", p.sub ?? (p.dark ? "#A9ADBA" : "#5F6170"), p.card, 3);
  need("단추 글자", p.onAccent ?? "#FFFFFF", p.accent, 3);
  need("머리 글자/바탕", p.headInk ?? p.ink, p.headGround ?? p.bg, 3);
  return out;
}
