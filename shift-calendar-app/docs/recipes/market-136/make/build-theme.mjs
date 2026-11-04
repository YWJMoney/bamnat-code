// 테마(종합) 한 벌을 짜는 틀: 화면 색 · 달력 모양 · 근무 모양 · 메모장 · 가계부 · 위젯 · 탭 보기 · 앱 느낌 · 부품 · 꾸밈 층 · 규칙 · 메모 틀 · 알람 · 일 조각.
import { H, c, recipe, shiftLook, screenLook, calTheme, memoLook, moneyLook, widget, appFeel, baseParts, baseTokens, parts, decor, logic, memoTpl, tabView, ROSTER } from "./lib.mjs";
import { R, T } from "./kit.mjs";

/** 유형마다 기본: 알람·일 조각·메모 틀·규칙·탭 보기. 테마가 덮어써요. */
export const KITS = {
  SHIFT: {
    alarms: { kind: "alarms", byRole: [{ role: "day", before: [90, 60] }, { role: "eve", before: [90] }, { role: "night", before: [180, 60] }], snooze: 5, gradual: true, vibrate: true },
    tpls: [T.sbar, T.prep, T.shiftReview], rules: [R.nightSleep, R.nightRecover, R.streak], view: { cal: "month", memo: "board", memoBy: "shift", money: "cal" },
    who: "3교대·2교대로 밤낮이 바뀌는 분",
  },
  DAY: {
    alarms: { kind: "alarms", byRole: [{ role: "day", before: [80, 50] }, { role: "long", before: [120] }], snooze: 5, gradual: true },
    tpls: [T.todayPlan, T.meeting, T.weekRetro], rules: [R.friRetro, R.week52, R.openPlan], view: { cal: "month", memo: "table", memoSort: "p:마감", money: "list" },
    who: "평일 출근하는 직장인",
  },
  HOURLY: {
    alarms: { kind: "alarms", byRole: [{ role: "day", before: [60, 30] }, { role: "eve", before: [60] }, { role: "night", before: [90, 40] }], snooze: 5 },
    tpls: [{ title: "출근 체크", emoji: "🧢", check: ["유니폼·명찰", "근무 시작 기록", "오늘 할 일 듣기"] }, { title: "마감 인수", emoji: "🔦", check: ["시재", "정리·청소", "다음 근무자 전달"], body: "{근무} {근무시작}~{근무끝} · 이번 주 {이번주근무시간}시간" }, T.diary],
    rules: [R.weekHours15, R.dayLog], view: { cal: "month", memo: "list", money: "list" },
    piece: { kind: "hourly", wage: { input: "i1" }, five: true, weeklyPay: true, legalBreaks: true, night: 50, breakPay: "UNPAID", rounding: "AT_END", tax: "NONE" },
    inputs: [{ id: "i1", label: "시급", kind: "money", default: 10320, min: 0, hint: "내 시급으로 바꿔요" }],
    who: "시간제로 일하는 알바·파트타이머",
  },
  FIELD: {
    tpls: [T.fieldLog, T.safety, T.invoice], rules: [R.rainMemo, R.dayLog], view: { cal: "list", memo: "table", memoSort: "-p:금액", money: "board" },
    piece: { kind: "field", mode: "DAY_RATE", dayRate: { input: "i1" }, chips: [0.5, 1, 1.5, 2], ends: [{ end: "17:00", units: 1 }, { end: "19:00", units: 1.5 }, { end: "21:00", units: 2 }],
      kinds: ["본작업", "보수", "철거", "마감"], costs: ["자재비", "공구", "유류비", "식대", "주차비"] },
    inputs: [{ id: "i1", label: "일당", kind: "money", default: 180000, min: 0, hint: "하루 일당으로 바꿔요" }],
    who: "현장·프리랜서로 일감마다 일하는 분",
  },
  BUSINESS: {
    tpls: [T.shopOpen, T.closing, T.order], rules: [R.shopOpen, R.salesDay, R.closeCheck], view: { cal: "month", memo: "board", memoBy: "tag", money: "cal" },
    piece: { kind: "shop", open: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"], opens: "10:00", closes: "21:00", cardFee: 1.5, settleDays: 3,
      closing: ["시재 마감", "청소", "재료 정리", "문단속"], costs: ["재료비", "포장재", "임대료", "공과금", "인건비"] },
    who: "작은 가게를 꾸리는 사장님",
  },
  INVEST: {
    tpls: [T.trade, T.rebalance, T.study], rules: [R.tradeJournal, R.quarter], view: { cal: "month", memo: "table", memoSort: "-p:감정 점수", money: "table" },
    piece: { kind: "invest", rules: [{ text: "산 이유를 한 줄로 쓸 수 있을 때만 산다" }, { text: "한 번에 다 사지 않고 나눠 산다" }, { text: "빚내서 사지 않는다" }, { text: "뉴스를 본 날은 하루 기다린다" }],
      journal: ["산 이유", "판 이유", "원칙 지킴", "감정 점수"] },
    who: "원칙대로 투자 기록을 남기는 분",
  },
};

/** The calendar's own look: cell looks by turn (쉬는 날은 두 번째 모양). */
export function calView(p, o = {}) {
  const look0 = [`mk:${o.mark ?? p.mark ?? "fill"}`, `ba:${o.ba ?? 85}`, `cr:${Math.min(16, p.cellR ?? 10)}`, "dp:mc", `ds:${o.ds ?? "l"}`, "dw:b", "pd:2"];
  if (p.cellPaint) look0.push(`fl:${p.cellPaint}`);
  if (p.dateFont) look0.push(`ff:${p.dateFont}`);
  const look1 = [`bg:${H(p.offBg ?? p.card)}`, `cr:${Math.min(16, p.cellR ?? 10)}`, "dp:mc", `ds:${o.ds ?? "l"}`, `mk:${o.offMark ?? "dot"}`, "pd:2"];
  if (p.dateFont) look1.push(`ff:${p.dateFont}`);
  return { kind: "calView", size: o.size ?? "tall", badge: o.badge ?? "small", date: o.date ?? "center", text: o.text ?? "normal", grid: o.grid ?? "none",
    memo: o.memo ?? 2, ...(o.hours ? { hours: true } : {}), ...(o.spend ? { spend: true } : {}), ...(o.lunar ? { lunar: true } : {}),
    looks: `${look0.join("|")}/${look1.join("|")}`, pattern: o.pattern ?? "shift:off=1", today: o.today ?? 2 };
}

/**
 * One 테마. [s] = { style, title, summary, wow, who?, changes, note?, p (palette), cover:[a,b,emoji], cells (when 줄), dated (달력 밖 날짜 부품 줄),
 *   decor, rules, buttons, tpls, view, cal (calView 옵션), memo/money (모양 옵션), alarms, piece, inputs, extra (더 넣을 조각), feel }.
 */
export function theme(s) {
  const kit = KITS[s.style];
  const p = s.p;
  const rules = [...(s.rules ?? []), ...(s.keepKitRules === false ? [] : kit.rules)].slice(0, 12);
  const tpls = [...(s.tpls ?? []), ...(s.keepKitTpls === false ? [] : kit.tpls)];
  const seenTpl = new Set();
  const tplItems = tpls.filter((t) => !seenTpl.has(t.title) && seenTpl.add(t.title)).slice(0, 8);
  const roster = ROSTER.includes(s.style);
  const pieces = [
    screenLook(p, s.style, s.screens),
    calView(p, s.cal),
    calTheme(p, s.cell ?? "tint"),
    roster ? shiftLook(s.style, p) : null,
    memoLook(p, s.memo),
    moneyLook(p, s.money),
    widget(p, s.widget),
    tabView({ ...kit.view, ...(s.view ?? {}) }),
    appFeel(p, s.feel),
    parts([...baseParts(p, s.base), ...(s.cells ?? []), ...(s.dated ?? [])]),
    s.decor?.length ? decor(s.decor) : null,
    logic({ buttons: s.buttons, rules }),
    memoTpl(tplItems),
    roster && s.alarms !== null ? (s.alarms ?? kit.alarms) : null,
    s.piece === null ? null : (s.piece ?? kit.piece ?? null),
    ...(s.extra ?? []),
  ].filter(Boolean);
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who ?? kit.who, changes: s.changes, note: s.note ?? noteFor(s.style), styles: [s.style],
      cover: s.cover, tokens: baseTokens(p), inputs: s.inputs ?? kit.inputs, pieces }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

export const noteFor = (style) => `${["BUSINESS", "INVEST"].includes(style) ? "메모·가계부" : "근무·메모·가계부"} 기록은 그대로예요. 메모를 만들거나 메모에 한 줄 더하는 규칙은 넣기 전에 한 번 묻고, 스튜디오 › 규칙·단추에서 끄거나 되돌릴 수 있어요`;
