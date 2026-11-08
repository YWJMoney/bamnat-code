// 1.38 [STM-08] 기능별 작품 틀: 한 탭만 바꾸는 작품(달력·메모·가계부·근무급여·위젯·알람)과 부품·규칙 작품. 화면 배경은 밤낮 배경, 꾸밈은 밤낮 색 아이콘,
// 상호작용은 feats.mjs의 조각에서 와요. 소개(wow·바뀌는 것)는 조각 설명으로 지어서 동작과 어긋나지 않아요.
import { H, c, recipe, calTheme, memoLook, moneyLook, parts, decor, memoTpl, tabView, ALL } from "../../market-136/make/lib.mjs";
import { calView } from "../../market-136/make/build-theme.mjs";
import { pal } from "./build.mjs";
import { PALETTE, use, headPic, cellPic, bgPic, widgetPic } from "./mat.mjs";

const lines = (feats) => feats.flatMap((x) => x.lines ?? []);
const says = (feats) => feats.map((x) => x.say).filter(Boolean);
const tags = (feats) => feats.map((x) => x.tag).filter(Boolean);

/** Inputs the features read by label (칸("월 예산", …)), numbered after the work's own. */
function inputsOf(own = [], feats = []) {
  const out = [...own];
  for (const x of feats) for (const i of x.inputs ?? []) {
    if (out.some((o) => o.label === i.label)) continue;
    let n = 1;
    while (out.some((o) => o.id === `i${n}`)) n++;
    out.push({ id: `i${n}`, ...i });
  }
  return out.length ? out : undefined;
}

/** One screen's look on 밤낮 배경 [bg]: the picture, its colours, the font. */
function screen(key, bg, p, font, dim) {
  return `${key}~bg:${H(p.bg)}|ph:${use(bg)}|dm:${dim ?? (p.dark ? 15 : 0)}|cd:${H(p.card)}|ik:${H(p.ink)}|ac:${H(p.accent)}${font ? `|ft:${font}` : ""}`;
}

function finish(s, p, pieces, feats, note, visual) {
  const wow = s.wow ?? [visual, ...says(feats)].filter(Boolean).join(" ");
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow, who: s.who, changes: [...(s.changes ?? []), ...tags(feats)], note: s.note ?? note,
      styles: s.styles ?? ALL, cover: s.cover ?? [p.top ?? p.bg, p.accent, s.emoji ?? "✨"], tokens: s.tokens, inputs: inputsOf(s.inputs, feats), pieces: pieces.filter(Boolean) }),
    ...(s.top ? { showcase: true } : {}),
  };
}

const bgName = (k) => PALETTE[k].name;

// ---- 달력 ----
/** s = { title, summary, styles, bg, font, icons:{head, today, holiday, roles:{night:'moon'}}, feats, cal (calView 옵션), board, top, emoji } */
export function calWork(s) {
  const p = pal(s.bg, s.p);
  const ic = s.icons ?? {};
  const feats = s.feats ?? [];
  const base = [
    { id: "cal.head", paint: p.headPaint, r: 18, pad: 10, out: 6 },
    { id: "cal.head.title", ...(s.font ? { font: s.titleFont ?? s.font } : {}), size: 22, weight: 800, ink: c(p.headInk) },
    { id: "cal.week.day", on: "sun", ink: c(p.dark ? "#FF8A80" : "#E53935") }, { id: "cal.week.day", on: "sat", ink: c(p.dark ? "#82B1FF" : "#1E88E5") },
    { id: "cal.cell", r: 12 },
    { id: "cal.cell", on: "today", line: "solid", lineW: 2, lineC: c(p.accent), motion: "glow" },
    { id: "cal.cell", on: "dim", alpha: 45 },
    ...(s.font ? [{ id: "cal.cell.date", font: s.font, weight: 600 }] : []),
  ];
  const deco = [
    ic.head ? headPic(ic.head) : null, ic.head2 ? headPic(ic.head2, { x: 830, w: 22, h: 22 }) : null,
    ic.today ? cellPic(ic.today, "today") : null, ic.holiday ? cellPic(ic.holiday, "holiday", { x: 200 }) : null,
    ...Object.entries(ic.roles ?? {}).map(([role, key]) => cellPic(key, `role.${role}`, { x: 200, y: 800, w: 12, h: 12 })),
    ...(s.decor ?? []),
  ].filter(Boolean);
  const pieces = [
    { kind: "screenLook", screens: screen("calendar", s.bg, p, s.font, s.dim) },
    calView(p, { size: "tall", ...(s.cal ?? {}) }), calTheme(p, s.cell ?? "tint"),
    parts([...base, ...lines(feats)]), deco.length ? decor(deco) : null, s.board ? tabView({ cal: s.board }) : null,
  ];
  const icons = [ic.head, ic.today, ...Object.values(ic.roles ?? {})].filter(Boolean).length;
  return finish(s, p, pieces, feats, "달력 모양만 바뀌어요. 근무·메모·가계부 기록과 다른 탭은 그대로예요",
    `달력 화면에 밤낮 배경 ‘${bgName(s.bg)}’이 깔리고 칸이 뒤가 비치는 유리가 돼요.${icons ? " 밤낮 색 아이콘이 달력 머리와 칸에 떠요." : ""}`);
}

// ---- 메모 ----
/** s = { title, summary, styles, bg, font, icon (화면 구석 스티커), look, tpls, view:{memo, memoBy, memoSort}, feats, cards } */
export function memoWork(s) {
  const p = pal(s.bg, s.p);
  const feats = s.feats ?? [];
  const base = [
    { id: "memo.head", paint: p.headPaint, pad: 8, ...(s.font ? { font: s.titleFont ?? s.font } : {}), ink: c(p.headInk) },
    { id: "memo.card", paint: p.glass, r: 18, line: "solid", lineW: 1, lineC: c(p.dark ? "#33FFFFFF" : "#80FFFFFF"), shadow: 0 },
    { id: "memo.card.title", weight: 700, ...(s.font ? { font: s.font } : {}) },
    { id: "memo.tags", ink: c(p.accent) },
  ];
  const deco = [s.icon ? bgPic(s.icon, "bg.memo", { x: 880, y: 70, w: 52, h: 52, rot: 8 }) : null, ...(s.decor ?? [])].filter(Boolean);
  const pieces = [
    { kind: "screenLook", screens: screen("memo", s.bg, p, s.font, s.dim) },
    memoLook({ ...p, ...(s.font ? { memoFont: s.font } : {}) }, { card: "own", border: "none", corner: 18, ...(s.look ?? {}) }),
    s.tpls?.length ? memoTpl(s.tpls) : null, s.view ? tabView(s.view) : null,
    parts([...base, ...lines(feats)]), deco.length ? decor(deco) : null, s.cards ? { kind: "cards", memo: s.cards } : null,
  ];
  const tplWords = s.tpls?.length ? ` 틀 ${s.tpls.length}개(${s.tpls.map((t) => t.title).join("·")})를 메모 + 에서 바로 써요.` : "";
  return finish(s, p, pieces, feats, "메모장 모양과 틀만 더해요. 있던 메모는 그대로이고, 틀은 쓸 때만 메모가 돼요",
    `메모장에 밤낮 배경 ‘${bgName(s.bg)}’이 깔리고 카드가 유리처럼 비쳐요.${tplWords}`);
}

// ---- 가계부 ----
/** s = { title, summary, styles, bg, font, icon, look, cards, view, feats } */
export function moneyWork(s) {
  const p = pal(s.bg, s.p);
  const feats = s.feats ?? [];
  const base = [
    { id: "money.head", paint: p.headPaint, r: 18, pad: 12, ...(s.font ? { font: s.titleFont ?? s.font } : {}), ink: c(p.headInk) },
    { id: "money.day", paint: p.glass, r: 10, padX: 10, weight: 700 },
    { id: "money.amount", on: "out", ink: c(p.dark ? "#FF8A80" : "#C62828"), weight: 700, ...(s.numFont ? { font: s.numFont } : {}) },
    { id: "money.amount", on: "in", ink: c(p.dark ? "#69F0AE" : "#2E7D32"), weight: 700, ...(s.numFont ? { font: s.numFont } : {}) },
  ];
  const deco = [s.icon ? bgPic(s.icon, "bg.money", { x: 880, y: 70, w: 52, h: 52, rot: 352 }) : null, ...(s.decor ?? [])].filter(Boolean);
  const pieces = [
    { kind: "screenLook", screens: screen("money", s.bg, p, s.font, s.dim) },
    moneyLook({ ...p, income: p.dark ? "#69F0AE" : "#2E7D32", expense: p.dark ? "#FF8A80" : "#C62828" }, s.look),
    s.cards ? { kind: "cards", money: s.cards } : null, s.view ? tabView({ money: s.view }) : null,
    parts([...base, ...lines(feats)]), deco.length ? decor(deco) : null,
  ];
  const cardWords = s.cards ? " 가계부 맨 위 카드 순서를 이 작품에 맞게 바꿔요." : "";
  return finish(s, p, pieces, feats, "가계부 모양과 카드만 바뀌어요. 적은 돈 기록은 그대로예요",
    `가계부에 밤낮 배경 ‘${bgName(s.bg)}’이 깔리고 날짜 머리가 유리처럼 비쳐요.${cardWords}`);
}

// ---- 근무·급여 ----
const PAID = ["SHIFT", "DAY", "HOURLY"];
/** s = { title, summary, styles, bg, font, numFont, icon, stats, hourly, feats } */
export function payWork(s) {
  const p = pal(s.bg, s.p);
  const feats = s.feats ?? [];
  const styles = s.styles ?? PAID;
  const base = [
    { id: "pay.head", ...(s.font ? { font: s.titleFont ?? s.font } : {}), weight: 700, ink: c(p.ink) },
    { id: "pay.total", paint: s.totalPaint ?? `lg.120.FF${p.accent.slice(1)},FF${(p.dark ? p.card : p.ink).slice(1)}`, r: 20, pad: 16, ink: c("#FFFFFF"), size: 30, weight: 800,
      ...(s.numFont ? { font: s.numFont } : {}) },
    { id: "pay.row", padY: 6 }, { id: "pay.row", on: "out", ink: c(p.dark ? "#FF8A80" : "#C62828") }, { id: "pay.row", on: "in", ink: c(p.dark ? "#69F0AE" : "#1B5E20") },
  ];
  const payLook = styles.map((k) => `${k}~tt:xl|pl:${H(p.card)}|mn:${H(p.accent)}|rw:roomy|op:1|dy:1`).join("/");
  const deco = [s.icon ? bgPic(s.icon, "bg.stats", { x: 880, y: 70, w: 52, h: 52, rot: 8 }) : null].filter(Boolean);
  const pieces = [
    { kind: "screenLook", screens: screen("stats", s.bg, p, s.font, s.dim), pay: payLook },
    s.stats ? { kind: "cards", stats: s.stats } : null, s.hourly ? { kind: "hourly", ...s.hourly } : null,
    parts([...base, ...lines(feats)]), deco.length ? decor(deco) : null,
  ];
  return finish({ ...s, styles }, p, pieces, feats, "근무·급여 화면 모양과 카드만 바뀌어요. 근무 기록과 급여 설정은 그대로예요",
    `근무·급여 화면에 밤낮 배경 ‘${bgName(s.bg)}’이 깔리고, 이달 급여가 큰 글씨 그라데이션 상자에 떠요.`);
}

// ---- 위젯 ----
/** s = { title, summary, styles, bg (위젯 배경 그림, 없으면 색만), p, icons:[key…], roles:{night: '#…'}, feats(widget.* 줄), text } */
export function widgetWork(s) {
  const p = s.bg ? pal(s.bg, s.p) : { ...s.p };
  const feats = s.feats ?? [];
  const roleLines = Object.entries(s.roles ?? {}).map(([role, col]) => ({ id: "widget.cell", on: `role.${role}`, fill: c(col), fillA: 40 }));
  const base = [
    { id: "widget", fill: c(p.card), fillA: s.alpha ?? 92, r: s.r ?? 22, ...(s.font ? { font: s.font } : {}), pad: 8 },
    { id: "widget.head", ink: c(p.ink), weight: 800, size: 14, ...(s.titleFont ? { font: s.titleFont } : {}) },
    { id: "widget.cell", r: 8, ink: c(p.ink) },
    { id: "widget.cell", on: "today", fill: c(p.accent), ink: c(p.onAccent ?? "#FFFFFF"), weight: 800 },
    { id: "widget.cell", on: "sun", ink: c(p.dark ? "#FF8A80" : "#E53935") }, { id: "widget.cell", on: "sat", ink: c(p.dark ? "#82B1FF" : "#1E88E5") },
    { id: "widget.cell", on: "dim", alpha: 40 },
    { id: "widget.cell", on: "holiday", ink: c(p.dark ? "#FF8A80" : "#E53935"), deco: "under", weight: 700 },
    ...roleLines,
  ];
  const ics = s.icons ?? [];
  const deco = ics.map((k, i) => widgetPic(k, i === 0 ? {} : { x: 905 - i * 70, y: 140, w: 18, h: 18 }));
  const pieces = [
    { kind: "widget", alpha: s.alpha ?? 92, theme: p.dark ? "dark" : "light", memo: s.memo ?? true, bg: c(p.card), ink: c(p.ink), corner: s.r ?? 22, text: s.text ?? "m" },
    s.bg ? { kind: "photoLook", widget: use(s.bg) } : null,
    parts([...base, ...lines(feats)]), deco.length ? decor(deco) : null,
  ];
  const roleWords = Object.keys(s.roles ?? {}).length ? "근무 종류마다 칸 색이 달라 이번 주가 한눈에 보여요. " : "";
  return finish(s, p, pieces, feats, "홈 화면 위젯 모양만 바뀌어요. 위젯은 다음에 새로 그려질 때(자정, 근무를 바꿀 때) 바뀌어요",
    `${s.bg ? `위젯 바탕에 밤낮 배경 ‘${bgName(s.bg)}’이 깔리고 ` : ""}${ics.length ? "밤낮 색 아이콘이 구석에 떠요. " : ""}${roleWords}오늘 칸은 포인트 색으로 빛나요.`);
}

// ---- 알람 ----
const MISSION = { none: "", math: "끌 때 간단한 덧셈 문제를 풀어요.", shake: "끌 때 휴대폰을 흔들어요." };
/** s = { title, summary, styles, bg, byRole:[[role, ...before]], snooze, ring, gradual, mission, font } */
export function alarmWork(s) {
  const p = pal(s.bg, s.p);
  const screens = `alarm~bg:${H(p.dark ? p.bg : "#101018")}|ph:${use(s.bg)}|dm:${s.dim ?? (p.dark ? 25 : 45)}|cd:${H(p.dark ? p.card : "#1D1D26")}|ik:FFFFFFFF|ac:${H(p.accent)}${s.font ? `|ft:${s.font}` : ""}`;
  const a = { kind: "alarms", byRole: s.byRole.map(([role, ...before]) => ({ role, before })), snooze: s.snooze ?? 5, ring: s.ring ?? 3,
    ...(s.gradual ? { gradual: true } : {}), ...(s.vibrate === false ? {} : { vibrate: true }), mission: s.mission ?? "none" };
  const ROLE = { day: "주간", eve: "저녁", night: "야간", long: "당직", off: "휴무", other: "기타" };
  const when = s.byRole.map(([role, ...before]) => `${s.roleWord?.[role] ?? ROLE[role]} ${before.map((m) => (m % 60 === 0 ? `${m / 60}시간` : m > 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`)).join("·")} 전`).join(", ");
  const auto = [`알람: ${when}`];
  if (s.gradual) auto.push("소리가 작게 시작해 점점 커져요");
  if (MISSION[a.mission]) auto.push(MISSION[a.mission]);
  auto.push(`다시 울림 ${a.snooze}분, 한 번에 ${a.ring}분`);
  const wow = s.wow ?? `${s.lead ? s.lead + " " : ""}근무마다 알람이 ${when}에 맞춰져요. 알람 화면엔 밤낮 배경 ‘${bgName(s.bg)}’이 어둡게 깔려 잠 깰 때 눈부시지 않아요.`;
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow, who: s.who, changes: [...(s.changes ?? []), ...auto],
      note: s.note ?? "근무 알람 설정과 알람 화면만 바뀌어요. 이미 켜 둔 근무 알람은 새 설정으로 다시 맞춰져요", styles: s.styles, cover: s.cover ?? [p.top ?? p.bg, p.accent, s.emoji ?? "⏰"],
      pieces: [{ kind: "screenLook", screens }, a] }),
    ...(s.top ? { showcase: true } : {}),
  };
}

// ---- 부품 (한 단위) ----
export function partWork(s) {
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? "이 부품 하나만 바뀌어요. 다른 꾸미기와 기록은 그대로예요",
      styles: s.styles ?? ALL, cover: s.cover, inputs: s.inputs, pieces: [parts(s.lines)] }),
    ...(s.top ? { showcase: true } : {}),
  };
}

// ---- 규칙·단추 ----
export function logicWork(s) {
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes,
      note: s.note ?? "꾸미기는 바뀌지 않아요. 규칙은 스튜디오 › 규칙·단추에서 하나씩 끄거나 지울 수 있고, 규칙이 쓴 것은 되돌릴 수 있어요",
      styles: s.styles ?? ALL, cover: s.cover, inputs: s.inputs, pieces: [{ kind: "logic", ...(s.buttons?.length ? { buttons: s.buttons } : {}), ...(s.rules?.length ? { rules: s.rules } : {}) }] }),
    ...(s.top ? { showcase: true } : {}),
  };
}
