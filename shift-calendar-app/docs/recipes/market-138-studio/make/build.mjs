// 1.38 [STM-07] 밤낮 배경 한 장으로 짜는 테마 한 벌: 배경 사진(화면 전체) + 그 배경에서 뽑은 색 + 비치는 유리 카드 + 밤낮 색 아이콘(근무 그림·달력 머리·칸 스티커·위젯)
// + 유형 기본(알람·틀·규칙·탭 보기·일 조각, 1.36 KITS) + 그날 값으로 바뀌는 칸 수식.
import { H, c, recipe, shiftLook, calTheme, memoLook, moneyLook, widget, appFeel, baseParts, baseTokens, parts, decor, logic, memoTpl, tabView, ROSTER, ALL }
  from "../../market-136/make/lib.mjs";
import { KITS, calView, noteFor } from "../../market-136/make/build-theme.mjs";
import { PALETTE, use, headPic, cellPic, widgetPic } from "./mat.mjs";

const A = (h, a) => a.toString(16).padStart(2, "0").toUpperCase() + h.slice(1).toUpperCase();

/** The palette of background [key], with [o] over it. */
export function pal(key, o = {}) {
  const b = PALETTE[key];
  if (!b) throw new Error(`배경 색이 없어요: ${key}`);
  const p = { ...b, line: b.dark ? "#3A3D4A" : "#E3E1EA", ...o };
  // Glass over the picture: light themes a milky card, dark ones a smoky card (뒤 배경이 비쳐요).
  p.glass = p.glass ?? (p.dark ? `gl.${A(p.card, 0xA6)}.22` : `gl.${A("#FFFFFF", 0xC4)}.22`);
  p.headPaint = p.headPaint ?? (p.dark ? `gl.${A(p.card, 0x8C)}.26` : `gl.${A("#FFFFFF", 0xA8)}.26`);
  p.headInk = p.headInk ?? p.ink;
  p.cellPaint = p.cellPaint ?? (p.dark ? `gl.${A(p.card, 0x66)}.10` : `gl.${A("#FFFFFF", 0x8C)}.10`);
  p.offBg = p.offBg ?? (p.dark ? p.card : "#F4F3F8");
  return p;
}

const ROLE_BY_STYLE = { SHIFT: ["day", "eve", "night", "off"], DAY: ["day", "other", "long", "off"], HOURLY: ["day", "eve", "night", "off"] };

/** 화면 색: the background picture on every screen, the colours over it, the font. */
function screens(bgKey, p, style, dim) {
  const all = [`bg:${H(p.bg)}`, `ph:${use(bgKey)}`, `dm:${dim}`, `cd:${H(p.card)}`, `ik:${H(p.ink)}`, `ac:${H(p.accent)}`];
  if (p.font) all.push(`ft:${p.font}`);
  const s = [`all~${all.join("|")}`];
  // 알람 화면은 한 판 더 어둡게(잠 깰 때 눈부시지 않게).
  s.push(`alarm~bg:${H(p.dark ? p.bg : "#101018")}|ph:${use(bgKey)}|dm:${Math.min(80, dim + 35)}|ik:FFFFFFFF`);
  const out = { kind: "screenLook", screens: s.join("/") };
  if (style) out.pay = `${style}~tt:xl|pl:${H(p.card)}|mn:${H(p.accent)}|rw:roomy|dy:1`;
  return out;
}

/**
 * One theme on 밤낮 배경 [s.bg]. s = { bg, style (null: 모든 유형), title, summary, wow, changes, who?, p (색 덮어쓰기), font, dim,
 *   icons: { head, roles: {night:'moon',…}, today, holiday, widget }, cells (칸 수식 줄), dated, rules, buttons, tpls, view, cards, feel, extra, cal }.
 */
export function studioTheme(s) {
  const style = s.style ?? null;
  const kit = style ? KITS[style] : null;
  const p = pal(s.bg, { ...(s.p ?? {}), ...(s.font ? { font: s.font, titleFont: s.titleFont ?? s.font } : {}) });
  const roster = style && ROSTER.includes(style);
  const ic = s.icons ?? {};
  const deco = [
    ic.head ? headPic(ic.head) : null,
    ic.head2 ? headPic(ic.head2, { x: 820, w: 22, h: 22, rot: 352 }) : null,
    ic.today ? cellPic(ic.today, "today") : null,
    ic.holiday ? cellPic(ic.holiday, "holiday", { x: 200 }) : null,
    ...Object.entries(roster ? {} : ic.roles ?? {}).map(([role, key]) => cellPic(key, `role.${role}`, { x: 200, y: 800, w: 12, h: 12 })),
    ic.widget ? widgetPic(ic.widget) : null,
    ...(s.decor ?? []),
  ].filter(Boolean);
  const roleIcons = roster && ic.roles ? Object.entries(ic.roles).filter(([r]) => ROLE_BY_STYLE[style].includes(r)).map(([role, key]) => ({ role, image: use(key) })) : [];
  const glassParts = [
    { id: "app.card", paint: p.glass, line: "solid", lineW: 1, lineC: c(p.dark ? "#33FFFFFF" : "#66FFFFFF"), shadow: 0 },
    { id: "app.tabs", paint: p.glass, r: 26, out: 8, shadow: 3 },
    { id: "app.bar", fill: c(p.dark ? "#00000000" : "#00FFFFFF"), shadow: 0 },
    { id: "memo.card", paint: p.glass, line: "solid", lineW: 1, lineC: c(p.dark ? "#33FFFFFF" : "#80FFFFFF"), shadow: 0 },
    { id: "money.day", paint: p.glass },
    { id: "widget", fill: c(p.card), r: 24 },
  ];
  const rules = [...(s.rules ?? []), ...(kit && s.keepKitRules !== false ? kit.rules : [])];
  const seenRule = new Set();
  const ruleItems = rules.filter((r) => !seenRule.has(r.name) && seenRule.add(r.name)).slice(0, 12);
  const tpls = [...(s.tpls ?? []), ...(kit && s.keepKitTpls !== false ? kit.tpls : [])];
  const seenTpl = new Set();
  const tplItems = tpls.filter((t) => !seenTpl.has(t.title) && seenTpl.add(t.title)).slice(0, 8);
  const pieces = [
    screens(s.bg, p, style, s.dim ?? (p.dark ? 15 : 0)),
    calView(p, { size: "tall", ...(s.cal ?? {}) }),
    calTheme(p, s.cell ?? "tint"),
    roster ? shiftLook(style, { day: p.day ?? p.accent, eve: p.eve ?? "#FB8C00", night: p.night ?? "#3949AB", off: p.off ?? "#9E9E9E", ...(s.shift ?? {}) }) : null,
    roleIcons.length ? { kind: "photoLook", icons: roleIcons, corners: "round" } : null,
    memoLook(p, { card: "own", border: "none", corner: 18, ...(s.memo ?? {}) }),
    moneyLook(p, s.money),
    widget(p, { alpha: 90, corner: 24, ...(s.widget ?? {}) }),
    tabView({ ...(kit?.view ?? { cal: "month", memo: "board", memoBy: "tag", money: "cal" }), ...(s.view ?? {}) }),
    appFeel(p, { motion: "tn:fade|td:glow|sp:n", ...(s.feel ?? {}) }),
    parts([...baseParts(p, { tabsR: 26 }), ...glassParts, ...(s.parts ?? []), ...(s.cells ?? []), ...(s.dated ?? [])]),
    deco.length ? decor(deco) : null,
    ruleItems.length || s.buttons?.length ? logic({ buttons: s.buttons, rules: ruleItems }) : null,
    tplItems.length ? memoTpl(tplItems) : null,
    s.cards ? { kind: "cards", ...s.cards } : null,
    roster && s.alarms !== null && kit ? (s.alarms ?? kit.alarms) : null,
    kit && s.piece !== null ? (s.piece ?? kit.piece ?? null) : null,
    ...(s.extra ?? []),
  ].filter(Boolean);
  const changes = [`배경 ‘${PALETTE[s.bg].name}’과 그 색에서 뽑은 글자·포인트 색`, "뒤가 비치는 유리 카드·탭 막대", ...(s.changes ?? [])];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who ?? kit?.who ?? "어느 일을 하든 앱 전체를 한 번에 바꾸고 싶은 분", changes,
      note: s.note ?? (style ? noteFor(style) : "기록은 그대로예요. 규칙은 넣기 전에 한 번 묻고, 스튜디오 › 규칙·단추에서 끄거나 되돌릴 수 있어요"),
      styles: style ? [style] : (s.styles ?? ALL), cover: s.cover ?? [p.top ?? p.bg, p.accent, s.emoji ?? "✨"], tokens: baseTokens(p), inputs: s.inputs ?? kit?.inputs, pieces }),
    ...(s.top ? { showcase: true } : {}),
  };
}
