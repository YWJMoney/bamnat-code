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
// 그려 보기(브런치 메뉴판 리넨 50, 달항아리 종이 55): 올이 굵은 결(리넨·캔버스·펠트)은 반으로, 모든 결은 16까지만 — 더 세면 얼룩처럼 보여요.
// 그려 보기(새벽 커밋·블루프린트 급여 합계가 두 줄로 꺾임): 폭이 넓은 고정폭·장식 숫자 글꼴은 합계를 한 단계 작게.
const WIDE_NUM = new Set(["spacemono", "jetbrainsmono", "robotomono", "vt323", "pressstart", "orbitron", "silkscreen", "nanumgothiccoding", "monoton", "bungee"]);
const COARSE = new Set(["linen", "canvas", "felt"]);
export const tx = (kind, tint, strength = 40) => `tx.${kind}.${stop(tint)}.${Math.min(16, COARSE.has(kind) ? Math.min(12, Math.max(4, Math.round(strength / 3))) : strength)}`;
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
  const alarm = [`bg:${H(alarmBg)}`, `fl:${pa.alarm ?? (p.dark && pa.screen ? pa.screen : lg(180, alarmBg, p.alarmTo ?? "#1B1D27"))}`, `cd:${H(p.alarmCard ?? (p.dark ? p.card : "#262833"))}`, `ik:FFFFFFFF`,
    `ac:${H(p.alarmAccent ?? (contrast(p.accent, alarmBg) < 3 ? "#F2F2F2" : p.accent))}`, `ft:${f.alarm ?? (f.body === "nanumpen" ? "gaegu" : f.body)}`]; // 막 깬 눈에 가는 펜 글씨는 안 읽혀요(렌더 확인): 같은 손글씨 결의 개구로
  if (s.photo && s.alarmPhoto !== false) alarm.push(`ph:${use(s.photo)}`, `dm:${Math.min(80, (s.dim ?? 20) + 40)}`);
  if (!s.screens?.alarm) scr.push(`alarm~${alarm.join("|")}`);
  const payStyles = style ? [style] : ["SHIFT", "DAY", "HOURLY", "FIELD"];
  const payWord = (st) => `${st}~tt:${s.pay?.tt ?? (WIDE_NUM.has(f.num) ? "l" : "xl")}|pl:${H(p.payCard ?? p.card)}|mn:${H(p.accent)}|rw:${s.pay?.rw ?? "roomy"}|dy:1`;
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
    { id: "app", font: f.body },
    { id: "app.bar", paint: so(p.bg, 0), shadow: 0 },
    { id: "app.bar.title", font: f.title, weight: s.titleWeight ?? 800, ink: c(p.ink), track: s.titleTrack ?? 0 },
    { id: "app.tabs", ...(pa.tabs ? { paint: pa.tabs } : { fill: c(p.card) }), r: r.tabs, ...(s.tabsFloat !== false ? { out: 10 } : {}),
      shadow: s.tabsShadow ?? (p.dark ? 4 : 6), ...lineOf(s.tabsLine) },
    { id: "app.tab", ink: c(p.sub) },
    { id: "app.tab", on: "sel", ink: c(p.tabSel ?? p.accent), weight: 800, ...(p.tabPill ? { fill: c(p.tabPill) } : {}) },
    { id: "app.card", ...card, r: r.card, shadow, ...(s.cardShadowC ? { shadowC: c(s.cardShadowC) } : {}), ...lineOf(s.cardLine) },
    { id: "app.button", fill: c(p.accent), ink: c(p.onAccent), r: r.btn, weight: 800, font: f.title },
    { id: "app.chip", r: r.btn, ...lineOf({ c: p.line }), ink: c(p.ink) },
    { id: "app.sheet", fill: c(p.sheet ?? p.card), r: Math.max(r.card, 20) },
    // 그려 보기(블루프린트·영수증 머리 글씨가 제목과 겹침): 머리에 작은 글씨 꾸밈이 있으면 위아래를 넉넉히 두어 제목 위 빈자리에 서게 해요.
    { id: "cal.head", paint: pa.head ?? so(p.bg, 0), r: r.card, pad: 12, ...(s.decor?.some((d) => d.text && d.at === "cal.head") ? { padY: 24 } : {}), out: 6, ...lineOf(s.headLine) },
    { id: "cal.head.title", font: f.title, size: s.titleSize ?? 24, weight: s.titleWeight ?? 800, ink: c(p.headInk ?? p.ink), track: s.titleTrack ?? 0, ...glow },
    { id: "cal.head.nav", ink: c(p.headInk ?? p.ink) },
    { id: "cal.head.today", ink: c(p.accent), weight: 800 },
    { id: "cal.week.day", font: f.date, size: 11, weight: 700, ink: c(p.sub), track: s.weekTrack ?? 6 },
    { id: "cal.week.day", on: "sun", ink: c(p.sun ?? "#D9473D") },
    { id: "cal.week.day", on: "sat", ink: c(p.sat ?? "#3D7BD9") },
    { id: "cal.cell", r: r.cell, ...(pa.cell ? { paint: pa.cell } : {}), ...lineOf(s.cellLine) },
    { id: "cal.cell", on: "today", ...(pa.today ? { paint: pa.today } : {}), line: "solid", lineW: 2, lineC: c(p.todayLine ?? p.accent), motion: s.todayMotion ?? "glow" },
    { id: "cal.cell", on: "dim", alpha: 35 },
    // 그려 보기(픽셀 시급 칸 채움 위 흰 날짜): 날짜 부품에 글자색을 주면 앱이 칸 색·오늘 동그라미에 맞춰 고르는 글자색을 덮어써요.
    // 평일 날짜는 화면 글자색(ik)을 그대로 따라가게 두고, 주말·공휴일 색만 줘요.
    { id: "cal.cell.date", font: f.date, weight: s.dateWeight ?? 700 },
    { id: "cal.cell.date", on: "sun", ink: c(p.sun ?? "#D9473D") },
    { id: "cal.cell.date", on: "sat", ink: c(p.sat ?? "#3D7BD9") },
    { id: "cal.cell.date", on: "holiday", ink: c(p.holiday ?? p.sun ?? "#D9473D") },
    { id: "cal.cell.mark", font: f.title, weight: 800 },
    { id: "cal.cell.memo", ink: c(p.sub), size: 9 },
    { id: "cal.foot", ...card, r: r.card },
    { id: "memo.head", paint: so(p.bg, 0) },
    { id: "memo.card", ...(pa.memoCard ? { paint: pa.memoCard } : card), r: r.card, shadow, ...lineOf(s.memoLine ?? s.cardLine) },
    { id: "memo.card.title", font: f.memo, weight: 800, size: 15 },
    { id: "memo.card.body", font: f.memo },
    { id: "memo.card.date", font: f.date, ink: c(p.accent), fill: c(p.accent), fillA: 16, weight: 700, size: 11 },
    { id: "memo.page", ...(pa.paper ? { paint: pa.paper } : { fill: c(p.paper ?? p.card) }), font: f.memo, ink: c(p.paperInk ?? p.ink) },
    { id: "memo.page.rule", line: s.rule ?? "solid", lineW: 1, lineC: c(p.ruleC ?? p.line) },
    { id: "money.head", ...(pa.head ? { paint: pa.head } : card), r: r.card, ink: c(p.headInk ?? p.ink) },
    { id: "money.day", ...(pa.band ? { paint: pa.band } : { fill: c(p.band ?? p.card) }), r: Math.min(12, r.card), padX: 12, font: f.date, ink: c(p.ink), weight: 700 },
    { id: "money.row", r: Math.min(12, r.card) },
    { id: "money.amount", font: f.num, weight: 800 },
    { id: "money.amount", on: "out", ink: c(p.expense ?? "#C4402F") },
    { id: "money.amount", on: "in", ink: c(p.income ?? "#2C8A4B") },
    { id: "pay.head", font: f.title, ink: c(p.ink) },
    { id: "pay.total", ...(pa.total ? { paint: pa.total } : { fill: c(p.accent) }), r: r.card + 4, ink: c(p.totalInk ?? (pa.total && p.dark ? p.ink : p.onAccent)), font: f.num, shadow: shadow + 2 },
    { id: "pay.row", font: f.body },
    { id: "pay.row", on: "in", ink: c(p.income ?? (p.dark ? "#6BD68F" : "#2C8A4B")) },
    { id: "pay.row", on: "out", ink: c(p.expense ?? (p.dark ? "#FF8F80" : "#C4402F")) },
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

  const rules = [...(s.rules ?? []), ...(kit && s.kitRules !== false && !s.only ? kit.rules : [])];
  const seen = new Set();
  const ruleItems = rules.filter((x) => !seen.has(x.name) && seen.add(x.name)).slice(0, 12);
  const tpls = [...(s.tpls ?? []), ...(kit && s.kitTpls !== false && !s.only ? kit.tpls : [])];
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
  const shown = s.only ? onlyTab(s.only, pieces, s) : pieces;
  const tokens = p.dark
    ? [["바탕", p.bg], ["카드", p.card], ["글자", p.ink], ["포인트", p.accent], ["선", p.line]]
    : [["바탕", p.bg, "#121212"], ["카드", p.card, "#1E1E1E"], ["글자", p.ink, "#ECECEC"], ["포인트", p.accent, p.accent], ["선", p.line, "#3A3A3A"]];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who ?? kit?.who ?? "어느 일을 하든 앱 전체를 한 번에 바꾸고 싶은 분",
      changes: s.changes, note: s.note ?? (s.only ? TAB_NOTE[s.only] : style ? noteFor(style) : "기록은 그대로예요. 규칙은 넣기 전에 한 번 묻고, 스튜디오 › 규칙·단추에서 끄거나 되돌릴 수 있어요"),
      styles: style ? [style] : (s.styles ?? ALL), cover: s.cover ?? [p.coverA ?? p.bg, p.accent, s.emoji], tokens: s.only ? undefined : tokens,
      inputs: s.inputs ?? (s.only ? undefined : kit?.inputs), pieces: shown }),
    mood: s.mood,
    problems: readable(s),
    ...(s.top ? { showcase: true } : {}),
  };
}

/**
 * 1.39 [MKW-03] 탭별 작품: 테마와 같은 미술 방향으로 그리되 [tab] 한 화면에 닿는 조각만 남겨요(장터 '탭별 작품' 구간은 그 탭 조각만).
 * calendar(달력 화면·칸·근무 아이콘·cal.* 부품) · memo · money · pay(근무·급여) · widget · alarm. 규칙·단추·틀은 작품이 직접 준 것만.
 */
const TAB_NOTE = {
  calendar: "달력 화면 모양만 바뀌어요. 근무·메모·가계부 기록과 다른 탭은 그대로예요",
  memo: "메모장 모양과 틀만 더해요. 있던 메모는 그대로이고, 틀은 쓸 때만 메모가 돼요",
  money: "가계부 모양만 바뀌어요. 적은 돈 기록은 그대로예요",
  pay: "근무·급여 화면 모양만 바뀌어요. 근무 기록과 급여 설정은 그대로예요",
  widget: "홈 화면 위젯 모양만 바뀌어요. 위젯은 다음에 새로 그려질 때(자정, 근무를 바꿀 때) 바뀌어요",
  alarm: "근무 알람 시각과 알람 화면만 바뀌어요. 근무 기록은 그대로이고, 알람 설정에서 언제든 바꿀 수 있어요",
};
const TAB = {
  calendar: { screen: ["calendar"], kinds: ["calView", "calTheme", "photoLook"], prefix: ["cal."], decor: ["cal.", "bg.calendar"], view: ["cal"] },
  memo: { screen: ["memo"], kinds: ["memoLook", "memoTpl"], prefix: ["memo."], decor: ["bg.memo"], view: ["memo", "memoBy", "memoSort"] },
  money: { screen: ["money"], kinds: ["moneyLook"], prefix: ["money."], decor: ["bg.money"], view: ["money"] },
  pay: { screen: ["stats"], kinds: [], prefix: ["pay."], decor: ["bg.stats"], view: [] },
  widget: { screen: [], kinds: ["widget"], prefix: ["widget"], decor: ["widget"], view: [] },
  alarm: { screen: ["alarm"], kinds: ["alarms"], prefix: [], decor: [], view: [] },
};
function onlyTab(tab, pieces, s) {
  const t = TAB[tab];
  if (!t) throw new Error(`${s.title}: 모르는 탭 ${tab}`);
  const out = [];
  for (const pc of pieces) {
    if (pc.kind === "screenLook") {
      const scr = pc.screens.split("/").filter((x) => t.screen.includes(x.split("~")[0]));
      // 화면 하나만 바꿀 땐 그 화면 말('all' 없이)로: all의 바탕·글꼴을 그 화면 줄에 합쳐요.
      const all = pc.screens.split("/").find((x) => x.startsWith("all~"))?.slice(4);
      const merged = t.screen.map((k) => {
        const own = scr.find((x) => x.startsWith(k + "~"))?.slice(k.length + 1);
        if (k === "alarm") return own ? `${k}~${own}` : null;
        const words = new Map((all ?? "").split("|").filter(Boolean).map((w) => [w.split(":")[0], w]));
        for (const w of (own ?? "").split("|").filter(Boolean)) words.set(w.split(":")[0], w);
        return words.size ? `${k}~${[...words.values()].join("|")}` : null;
      }).filter(Boolean);
      if (merged.length || (tab === "pay" && pc.pay)) out.push({ kind: "screenLook", ...(merged.length ? { screens: merged.join("/") } : {}), ...(tab === "pay" && pc.pay ? { pay: pc.pay } : {}) });
      continue;
    }
    if (pc.kind === "parts") {
      const keep = pc.parts.filter((l) => t.prefix.some((x) => l.id === x.replace(/\.$/, "") || l.id.startsWith(x)));
      if (keep.length) out.push({ kind: "parts", parts: keep });
      continue;
    }
    if (pc.kind === "decor") {
      const keep = pc.items.filter((d) => t.decor.some((x) => d.at === x || d.at?.startsWith(x)));
      if (keep.length) out.push({ kind: "decor", items: keep });
      continue;
    }
    // 장터 구간(server sectionOf): 아이콘 모서리(corners)·규칙(logic)은 앱 전체로 읽혀 '테마'가 되니 탭별 작품엔 넣지 않아요.
    if (pc.kind === "photoLook") { const { corners, ...rest } = pc; if ((tab === "calendar" && rest.icons) || (tab === "widget" && rest.widget)) out.push(rest); continue; }
    if (pc.kind === "tabView") {
      const v = Object.fromEntries(Object.entries(pc).filter(([k]) => t.view.includes(k)));
      if (Object.keys(v).length && s.view) out.push({ kind: "tabView", ...v });
      continue;
    }
    if (pc.kind === "logic") continue;
    if (pc.kind === "shiftLook") { if (tab === "calendar" && s.shiftColors) out.push(pc); continue; }
    if (t.kinds.includes(pc.kind)) out.push(pc);
  }
  return [...out, ...(s.tabExtra ?? [])];
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
  need("급여 수입 줄", p.income ?? (p.dark ? "#6BD68F" : "#2C8A4B"), p.bg, 3);
  need("급여 공제 줄", p.expense ?? (p.dark ? "#FF8F80" : "#C4402F"), p.bg, 3);
  const tabs = s.paint?.tabs;
  const tabGround = typeof tabs === "string" && tabs.startsWith("so.") ? "#" + tabs.slice(5, 11) : p.card;
  need("고른 탭", p.tabSel ?? p.accent, p.tabPill ?? tabGround, 3);
  return out;
}
