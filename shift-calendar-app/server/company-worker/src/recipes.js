// 1.34 [RCP-28]..[RCP-33] 나눔 판: recipes on the company server (계획 docs/plans/1.34-개발계획.md, 설계서 4-7·4-10).
//
// A recipe holds values only (결정 D106): cleanRecipe() keeps the fields core's PieceCatalog knows and refuses what is out of
// range, in the same order and with the same words as core RecipeCodec, so the phone and the server write the same bytes and
// the same SHA-256 (docs/recipe-cases.json holds the two together). What the server keeps:
//  - recipes: one row a recipe (its code, owner's install hash, the newest version's written form and hash, counts).
//  - recipe_versions: every version's written form (새 판, 1.35 3-way 반영 reads the one a phone took).
//  - recipe_votes: 좋아요·안좋아요 per install, a 안좋아요's reason (chip and short opinion, 결정 D107) seen by the maker only.
//  - recipe_counts: applied and kept-7-days per recipe and day, never per phone (처리방침 3.8 1-4항).
//  - recipe_trust: per install, when it first used the 나눔 판 and how many of its recipes the operator approved (D108).
// A recipe's talk is the posts table (company_id 'recipe', grp the code), so 신고 3번 가림, 가리기·지우기·30일 막기, 이 사람 글
// 안 보기 and 광장 소식 are the same as every plaza.
// This file does not import index.js (tools/bundle.mjs puts it in one file); what it needs comes in as [d].
import { EMOJI } from "./emoji-set.js";
import { DAY, HOUR } from "./units.js";
import { careGate, careRepeat } from "./community-care.js";
import { makerMarks, markFields } from "./maker-mark.js";
import { addStrike, confirmStrike, operatorStrike, strikeTakenBack, writeLeft, writeLimitHit } from "./write-limits.js";
import { FORMULA, formulaProblem, formulaTextProblem, fxCallKeys } from "./formula.js";
import { DECOR_ANCHORS, DECOR_ICONS, DECOR_LINES, DECOR_MAX, DECOR_SHAPES, decorFont, decorOn, decorPhoto, decorTap } from "./decor.js";
import { cellExtra, screenExtra, fillProblem, fontId, photoAdjust, photoFrame, motionProblem } from "./materials.js";
import { waitingPhotos } from "./photo-guard.js";

// ---- the format (core recipe/*.kt) ----

export const RECIPE = {
  format: "bamnat-recipe", version: 1, maxBytes: 32 * 1024, maxDepth: 8, maxPieces: 16, maxInputs: 20, maxTokens: 12,
  title: 30, summary: 60, desc: 600, tokenName: 10, inputLabel: 20,
};
const STYLES = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
const ALL_STYLES = new Set(STYLES);
export const WEEK_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const ROLES = ["day", "eve", "night", "long", "off", "other"];
const INPUT_KINDS = ["money", "number", "time", "color", "emoji", "choice", "text", "flag"];
const INPUT_ID = /^i([1-9]|1[0-9]|20)$/;
export const CODE = /^[2-9A-HJ-NP-Z]{8}$/;
const CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
const R_TIME = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;
const REPEAT = /^(M([1-9]|[12][0-9]|3[01])|W[1-7])$/;

export class RecipeError extends Error {
  constructor(code, path = "") {
    super(code);
    this.code = code;
    this.path = path;
  }
}
const fail = (code, path) => {
  throw new RecipeError(code, path);
};

// Field types, as core Field.
const T = (key, max, o = {}) => ({ t: "text", key, max, min: 0, lines: false, optional: false, input: false, guard: true, ...o });
const N = (key, lo, hi, o = {}) => ({ t: "num", key, lo, hi, decimals: 0, optional: false, input: false, ...o });
const M = (key, lo, hi, o = {}) => ({ t: "money", key, lo, hi, optional: false, ...o });
const C = (key, o = {}) => ({ t: "color", key, optional: false, ...o });
const E = (key, o = {}) => ({ t: "emoji", key, optional: true, input: true, ...o });
const F = (key, o = {}) => ({ t: "flag", key, optional: true, input: true, ...o });
const P = (key, values, o = {}) => ({ t: "pick", key, values, optional: false, input: false, ...o });
const K = (key, o = {}) => ({ t: "clock", key, optional: false, input: true, ...o });
const TS = (key, max, itemMax, o = {}) => ({ t: "texts", key, max, itemMax, optional: true, ...o });
const NS = (key, max, lo, hi, o = {}) => ({ t: "nums", key, max, lo, hi, decimals: 0, optional: true, ...o });
const D = (key, o = {}) => ({ t: "days", key, optional: true, ...o });
const I = (key, max, fields, o = {}) => ({ t: "items", key, max, fields, min: 0, optional: false, ...o });

const imageId = (s) => (s === "" || /^[0-9a-f]{16}$/.test(s) ? null : "image");

const LAYOUT_LINE = /^-?[A-Za-z][A-Za-z0-9]{0,23}(,-?[A-Za-z][A-Za-z0-9]{0,23}){0,39}$/;
const layoutLine = (s) => (s === "" || LAYOUT_LINE.test(s) ? null : "layout");
// [CVW-01] 달력 모양's order: the lines under a day's mark, each once (core CalendarView.orderProblem).
const CELL_LINES = ["holiday", "memo", "money", "hours", "spend", "lunar"];
const DAY_GROUPS = ["work", "extra", "leave", "alarm", "memo", "money", "body", "record"];
const dayOrder = (s) => (s === "" || (s.split(",").every((x) => DAY_GROUPS.includes(x)) && new Set(s.split(",")).size === s.split(",").length) ? null : "dayOrder");
const cellOrder = (s) => (s === "" || (s.split(",").every((x) => CELL_LINES.includes(x)) && new Set(s.split(",")).size === s.split(",").length) ? null : "order");

// [FREE-01..07] 1.35 꾸미기, as core FreeLook checks them (same parts, same limits).
const CELL_PART = /^(bg:([0-9A-F]{8}|shift|none)|ba:(100|[1-9]?[0-9])|bd:(none|thin|thick|dash)|bc:[0-9A-F]{8}|cr:(1[0-6]|[0-9])|dp:(tl|tc|tr|ml|mc|mr|bl|bc|br)|ds:(s|m|l|xl)|dw:[nb]|dsh:(none|circle|square)|df:[0-9A-F]{8}|dc:[0-9A-F]{8}|mk:(big|small|line|dot|fill|none)|mp:(side|below|bottom)|pd:[0-8])$/;
const PATTERN_RULE = /^(one|cycle:[2-6]|week:[0-5](,[0-5]){6}|shift:(day|eve|night|long|off|other)=[0-5](,(day|eve|night|long|off|other)=[0-5]){0,5})$/;
const SCREEN_PART = /^(bg:[0-9A-F]{8}|ph:[0-9a-f]{16}|dm:([0-7]?[0-9]|80)|cd:[0-9A-F]{8}|ik:[0-9A-F]{8}|ac:[0-9A-F]{8}|em:[0-9a-f]{2,6}(\+[0-9a-f]{2,6}){0,7})$/;
const SCREEN_KEY = /^(all|calendar|money|memo|stats|pay|plaza|settings|alarm|guarded|pay\.(SHIFT|DAY|HOURLY|FIELD|BUSINESS|INVEST))$/;
const PAY_PART = /^(tt:(m|l|xl)|pl:[0-9A-F]{8}|mn:[0-9A-F]{8}|rw:(compact|normal|roomy)|op:[01]|dy:[01])$/;
const onceEach = (parts) => new Set(parts.map((x) => x.split(":")[0])).size === parts.length;
const cellLooks = (s) => {
  if (s === "") return null;
  const looks = s.split("/");
  if (looks.length > 6) return "looks";
  for (const l of looks) {
    if (l === "") continue;
    const parts = l.split("|");
    if (!parts.every((x) => CELL_PART.test(x) || cellExtra(x)) || !onceEach(parts)) return "looks";
  }
  return null;
};
const cellPattern = (s) => (s === "" || PATTERN_RULE.test(s) ? null : "pattern");
const screenLooks = (s) => {
  if (s === "") return null;
  const entries = s.split("/");
  if (entries.length > 16) return "screens";
  const keys = entries.map((e) => (e.includes("~") ? e.slice(0, e.indexOf("~")) : ""));
  if (keys.some((k) => !SCREEN_KEY.test(k)) || new Set(keys).size !== keys.length) return "screens";
  for (const e of entries) {
    const parts = e.slice(e.indexOf("~") + 1).split("|").filter((x) => x !== "");
    if (!parts.every((x) => SCREEN_PART.test(x) || screenExtra(x)) || !onceEach(parts)) return "screens";
  }
  return null;
};
const PAY_KINDS = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
const payLooks = (s) => {
  if (s === "") return null;
  const entries = s.split("/");
  const keys = entries.map((e) => (e.includes("~") ? e.slice(0, e.indexOf("~")) : ""));
  if (keys.some((k) => !PAY_KINDS.includes(k)) || new Set(keys).size !== keys.length) return "pay";
  for (const e of entries) {
    const parts = e.slice(e.indexOf("~") + 1).split("|").filter((x) => x !== "");
    if (!parts.every((x) => PAY_PART.test(x)) || !onceEach(parts)) return "pay";
  }
  return null;
};

const companyCheck = (text) => {
  try {
    const v = JSON.parse(text);
    return v && typeof v === "object" && !Array.isArray(v) && v.format === "bamnat-company" ? null : "setup";
  } catch {
    return "setup";
  }
};

// [BLU-01] 1.36 부품 설계도 (docs/blueprint-format.md), as core BlueprintParts: the same ids, states and fields.
export const PART_IDS = [
  "app", "app.bar", "app.bar.title", "app.tabs", "app.tab", "app.button", "app.chip", "app.card", "app.sheet",
  "cal", "cal.head", "cal.head.title", "cal.head.nav", "cal.head.today", "cal.week", "cal.week.day", "cal.grid",
  "cal.cell", "cal.cell.date", "cal.cell.mark", "cal.cell.memo", "cal.cell.money", "cal.cell.hours", "cal.cell.spend",
  "cal.cell.lunar", "cal.cell.holiday", "cal.foot",
  // [TAB-04] 1.36 달력 판
  "cal.band", "cal.band.slot", "cal.line", "cal.line.row", "cal.line.bar",
  "cal.weekly", "cal.weekly.day", "cal.year", "cal.year.month",
  "memo.head", "memo.tags", "memo.card", "memo.card.title", "memo.card.body", "memo.card.date",
  // [TAB-01][TAB-02] 메모 속성·보드·표
  "memo.prop", "memo.board", "memo.board.col", "memo.board.head", "memo.table", "memo.table.head", "memo.table.row",
  "memo.cal", "memo.cal.cell",
  // [MPH-06] 1.38 메모 쓰기 화면: 종이, 종이 줄, 메모 사진
  "memo.page", "memo.page.rule", "memo.photo",
  "money.head", "money.day", "money.row", "money.amount",
  // [TAB-06] 가계부 판
  "money.cal", "money.cal.cell", "money.board", "money.board.col", "money.table", "money.table.row",
  "pay.head", "pay.total", "pay.row",
  "widget", "widget.head", "widget.cell",
  // 1.39 [FLOW-21] 밤 화면·아침 한 장
  "night", "night.date", "night.clock", "night.line", "night.card", "night.button", "night.morning",
  "widget.task", "widget.task.check", "widget.task.add",
];
const PART_STATES = ["role.day", "role.eve", "role.night", "role.long", "role.off", "role.other", "in", "out", "sat", "sun", "holiday", "dim", "today", "late", "done", "sel", "dark"];
const partParent = (id) => (id === "app" || id === "widget" ? null : !id.includes(".") ? "app"
  : PART_IDS.includes(id.slice(0, id.lastIndexOf("."))) ? id.slice(0, id.lastIndexOf(".")) : "app");
const PART_CHILDREN = {};
for (const id of PART_IDS) {
  const p = partParent(id);
  if (p) (PART_CHILDREN[p] ??= []).push(id.slice(id.lastIndexOf(".") + 1));
}
const FONT_ID = /^[a-z][a-z0-9-]{1,39}$/;
const PART_ORDER = /^[a-z]{1,12}(,[a-z]{1,12}){0,15}$/;
const partOrder = (s) => (PART_ORDER.test(s) && new Set(s.split(",")).size === s.split(",").length ? null : "order");
export const PART_STYLE = [
  C("fill", { optional: true }), N("fillA", 0, 100, { optional: true }),
  P("line", ["none", "solid", "dash", "dot", "double"], { optional: true }), N("lineW", 0, 8, { decimals: 1, optional: true }),
  C("lineC", { optional: true }), N("lineA", 0, 100, { optional: true }),
  N("r", 0, 48, { optional: true }), N("rTL", 0, 48, { optional: true }), N("rTR", 0, 48, { optional: true }),
  N("rBR", 0, 48, { optional: true }), N("rBL", 0, 48, { optional: true }),
  N("shadow", 0, 24, { optional: true }), C("shadowC", { optional: true }),
  N("pad", 0, 32, { optional: true }), N("padX", 0, 32, { optional: true }), N("padY", 0, 32, { optional: true }),
  N("out", 0, 32, { optional: true }),
  T("font", 40, { optional: true, guard: false, check: (s) => (FONT_ID.test(s) ? null : "font") }),
  N("size", 6, 96, { decimals: 1, optional: true }), N("weight", 100, 900, { optional: true }),
  C("ink", { optional: true }), N("track", -5, 30, { decimals: 1, optional: true }),
  N("leading", 80, 300, { optional: true }), N("stroke", 0, 4, { decimals: 1, optional: true }),
  C("strokeC", { optional: true }), F("italic", { input: false }), P("talign", ["start", "center", "end"], { optional: true }),
  // [BLU-08] 글자 그림자·밑줄 (누락 점검 N11).
  N("tshadow", 0, 12, { decimals: 1, optional: true }), C("tshadowC", { optional: true }),
  N("tshadowX", -12, 12, { decimals: 1, optional: true }), N("tshadowY", -12, 12, { decimals: 1, optional: true }),
  P("deco", ["none", "under", "strike", "both"], { optional: true }),
  N("lines", 1, 10, { optional: true }), N("alpha", 0, 100, { optional: true }), F("hide", { input: false }),
  P("dir", ["row", "col", "stack"], { optional: true }), N("gap", 0, 32, { optional: true }),
  P("align", ["start", "center", "end", "stretch"], { optional: true }),
  P("justify", ["start", "center", "end", "between", "around"], { optional: true }),
  T("order", 200, { optional: true, guard: false, check: partOrder }),
  P("place", ["tl", "tc", "tr", "ml", "mc", "mr", "bl", "bc", "br"], { optional: true }), N("grow", 0, 10, { optional: true }),
  N("w", 0, 600, { optional: true }), N("h", 0, 600, { optional: true }),
  // [MAT-01..07] 갈래 B 꾸미기 재료 (core BlueprintParts.STYLE_FIELDS와 같아요).
  T("paint", 80, { optional: true, guard: false, check: fillProblem }), T("linePaint", 80, { optional: true, guard: false, check: fillProblem }),
  T("photo", 60, { optional: true, guard: false, check: photoAdjust }), T("frame", 30, { optional: true, guard: false, check: photoFrame }),
  P("motion", ["none", "pulse", "glow", "ring", "bounce", "shimmer"], { optional: true }),
];
const partLines = (lines) => {
  const seen = new Set();
  for (const l of lines) {
    const k = `${l.id}|${l.on ?? ""}|${l.when ?? ""}`;
    if (seen.has(k)) return "dup";
    seen.add(k);
    if (typeof l.order === "string" && l.order.split(",").some((x) => !(PART_CHILDREN[l.id] ?? []).includes(x))) return "order";
  }
  return null;
};

// [TAB-07] core MemoProps.isLabel and MemoView.validBy·validSort: a 칸 name with a letter, at most three words, no link or separator.
const propLabel = (s) => s.length >= 1 && s.length <= 20 && /\p{L}/u.test(s) && s.split(" ").length <= 3 && !/http/i.test(s) && !/[/|:;]/.test(s);
const memoBy = (s) => ["tag", "shift", "check", "color"].includes(s) || (s.startsWith("p:") && propLabel(s.slice(2)));
const memoSort = (s) => s === "" || s === "title" || (s.replace(/^-/, "").startsWith("p:") && propLabel(s.replace(/^-/, "").slice(2)));

/** core PieceKind: key, the kinds of work it fits, its fields. */
// 1.36 [RUL-03][RUL-04] core formula/Logic.kt: where a button sits, the safe verbs, when a rule runs; a formula may compare amounts.
// 1.39 [FX-04] widget·tile·shortcut: 단추 위젯·빠른 설정·앱 바로가기 (프로 기능 장치).
const BUTTON_PLACES = ["day", "memo", "cal", "money", "decor", "widget", "tile", "shortcut"];
// 1.39 [FX-03] valAdd·valSet: 값 칸 (core LogicVerb).
const LOGIC_VERBS = ["notify", "memoTpl", "screen", "newMemo", "checkReset", "addMoney", "memoLine", "addIncome", "envelope", "valAdd", "valSet", "valStamp", "valText"];
const VALUE_VERBS = ["valAdd", "valSet", "valStamp", "valText"];
const VALUE_KINDS = ["num", "time", "text"];
const VALUE_RESETS = ["day", "week", "month", "never"];
// core MemoProps.isLabel, 12 letters (LogicValue.isName).
const valueName = (s) => (s.length <= 12 && propLabel(s) ? null : "name");
const RULE_TRIGGERS = ["tap", "start", "end", "daily", "memo", "open", "money", "hold", "value", "every", "alarmOff"];
const logicGuard = (s) => textProblemExcept(s, "amount") ?? (investAdvice(s) ? "invest_advice" : null);
const logicFormula = (s) => (s === "" ? null : formulaProblem(s) ?? logicGuard(s));
const logicText = (s) => (s === "" ? null : formulaTextProblem(s) ?? logicGuard(s));
// 1.39 [FX-11] One act of a 동작 줄 (core PieceCatalog LOGIC_ACT_MORE·LOGIC_ACT).
const LOGIC_ACT_MORE = [TS("then", 2, 12), N("after", 1, 720, { optional: true }), T("text", 24, { optional: true, guard: false, check: logicText })];
const LOGIC_ACT = [P("do", LOGIC_VERBS), T("arg", 120, { optional: true, guard: false, check: logicText }), M("amount", 0, 100_000_000, { optional: true }),
  N("by", -1_000_000, 1_000_000, { decimals: 2, optional: true }), ...LOGIC_ACT_MORE];
export const RECIPE_PIECES = {
  shiftLook: { styles: ["SHIFT", "DAY", "HOURLY"], fields: [I("items", 12, [T("name", 20, { min: 1 }), P("role", ROLES),
    T("label", 2, { min: 1, check: (s) => (graphemes(s) > 2 ? "label" : null) }), C("color"), E("emoji")], { min: 1 })] },
  calTheme: { styles: STYLES, fields: [C("today", { optional: true }), C("sat", { optional: true }), C("sun", { optional: true }),
    C("holiday", { optional: true }), P("cell", ["fill", "tint", "dot"], { optional: true })] },
  alarms: { styles: ["SHIFT", "DAY", "HOURLY"], fields: [I("byRole", 6, [P("role", ROLES), NS("before", 4, 0, 720, { optional: false })]),
    N("snooze", 1, 30, { optional: true }), N("ring", 1, 30, { optional: true }), F("gradual"), F("vibrate"),
    P("mission", ["none", "math", "shake"], { optional: true })] },
  memoTpl: { styles: STYLES, fields: [I("items", 8, [T("title", 30, { min: 1 }), E("emoji"), TS("check", 20, 60),
    T("body", 300, { lines: true, optional: true }), T("repeat", 4, { optional: true, check: (s) => (s === "" || REPEAT.test(s) ? null : "repeat") }),
    // 1.35 [CVW-10] 입력 칸
    I("asks", 8, [T("label", 20, { min: 1 }), P("type", ["text", "num", "star", "pick", "money", "date", "time", "check"]), TS("choices", 8, 16)], { optional: true })], { min: 1 })] },
  hourly: { styles: ["HOURLY"], fields: [M("wage", 0, 1_000_000, { optional: true }), F("five"), F("weeklyPay"), P("weekStart", WEEK_DAYS, { optional: true }),
    P("breakPay", ["UNPAID", "PAID", "AS_WORK"], { optional: true }), F("legalBreaks"), N("night", 0, 200, { optional: true }), F("partTime"),
    P("rounding", ["HOURLY_FIRST", "AT_END", "AT_END_UP", "AT_END_DOWN"], { optional: true }),
    P("tax", ["NONE", "WITHHOLDING_3_3", "INSURANCE_4"], { optional: true })] },
  field: { styles: ["FIELD"], fields: [P("mode", ["DAY_RATE", "PER_JOB", "PLATFORM"]), M("dayRate", 0, 5_000_000, { optional: true }),
    NS("chips", 5, 0.1, 5, { decimals: 2 }), I("ends", 6, [K("end"), N("units", 0.1, 5, { decimals: 2 })], { optional: true }),
    TS("kinds", 12, 20), TS("costs", 12, 20)] },
  shop: { styles: ["BUSINESS"], fields: [D("open"), K("opens", { optional: true }), K("closes", { optional: true }), F("holidaysClosed"),
    N("cardFee", 0, 5, { decimals: 2, optional: true }), N("settleDays", 0, 10, { optional: true }), F("simpleTax"),
    N("valueAdded", 5, 40, { optional: true }), TS("closing", 20, 60), TS("costs", 20, 20)] },
  invest: { styles: ["INVEST"], fields: [I("rules", 10, [T("text", 80, { min: 2, check: (s) => (investAdvice(s) ? "invest_advice" : null) })], { min: 1 }),
    TS("journal", 8, 20)] },
  company: { styles: ["SHIFT", "DAY"], fields: [T("setup", 24_000, { min: 2, lines: true, guard: false, check: companyCheck })] },
  // 1.35 [RCX-11]..[RCX-14]
  bundle: { styles: STYLES, fields: [I("items", 8, [T("code", 8, { min: 8, guard: false, check: (s) => (CODE.test(s) ? null : "code") }),
    T("note", 40, { optional: true })], { min: 2 })] },
  layout: { styles: STYLES, fields: [TS("tabs", 6, 10), T("calendar", 400, { optional: true, guard: false, check: layoutLine })] },
  cards: { styles: STYLES, fields: [T("stats", 400, { optional: true, guard: false, check: layoutLine }),
    T("money", 400, { optional: true, guard: false, check: layoutLine }), T("memo", 400, { optional: true, guard: false, check: layoutLine })] },
  // [STU-38] 1.36 위젯 꾸미기 넓히기: background and text colour, corners, text size.
  widget: { styles: STYLES, fields: [N("alpha", 30, 100, { optional: true }), P("theme", ["system", "light", "dark"], { optional: true }), F("memo"),
    C("bg", { optional: true }), C("ink", { optional: true }), N("corner", 0, 28, { optional: true }), P("text", ["s", "m", "l"], { optional: true })] },
  // 1.35 [RCX-30] 그림 꾸미기: pictures by their recipe_images id (each looked at before others see it).
  photoLook: { styles: STYLES, fields: [T("bg", 16, { optional: true, guard: false, check: imageId }), N("dim", 0, 80, { optional: true }),
    T("head", 16, { optional: true, guard: false, check: imageId }), T("widget", 16, { optional: true, guard: false, check: imageId }),
    P("corners", ["round", "soft", "square"], { optional: true }),
    I("icons", 6, [P("role", ROLES), T("image", 16, { min: 16, guard: false, check: imageId })], { optional: true }),
    // [MAT-03][MAT-04] 1.36 그림 조정·틀 모양.
    T("adjust", 60, { optional: true, guard: false, check: photoAdjust }), T("frame", 30, { optional: true, guard: false, check: photoFrame })] },
  // 1.35 [CVW-01] 달력 모양: cell size, mark, date place, text, lines, memo lines and the order of what stands under the mark.
  calView: { styles: STYLES, fields: [P("size", ["short", "normal", "tall", "huge"], { optional: true }), P("badge", ["big", "small", "line"], { optional: true }),
    P("date", ["center", "left"], { optional: true }), P("text", ["small", "normal", "large"], { optional: true }), P("grid", ["none", "line"], { optional: true }),
    N("memo", 0, 4, { optional: true }), F("hours"), F("lunar"), F("spend"), T("order", 80, { optional: true, guard: false, check: cellOrder }),
    // [FREE-01][FREE-02] 1.35 칸 디자이너: the cells' own looks, how they take turns, today's own.
    T("looks", 2400, { optional: true, guard: false, check: cellLooks }), T("pattern", 80, { optional: true, guard: false, check: cellPattern }),
    N("today", 0, 5, { optional: true })] },
  // 1.35 [CVW-07] 앱 동작: never the alarm's 끄기, 잠금, 동의, 결제·광고 or where a backup goes.
  appFeel: { styles: STYLES, fields: [P("theme", ["system", "light", "dark", "night"], { optional: true }), F("dynamic"), C("accent", { optional: true }),
    N("textStep", 0, 4, { optional: true }), F("compact"), F("clock12"), P("tap", ["preview", "sheet", "week"], { optional: true }),
    P("hold", ["menu", "memo", "open"], { optional: true }), T("dayOrder", 120, { optional: true, guard: false, check: dayOrder }),
    F("flow"), F("rest"), F("payPeriod"),
    // [MAT-07] 1.36 움직임.
    T("motion", 60, { optional: true, guard: false, check: motionProblem })] },
  // [FREE-04] 1.35 메모장 모양.
  memoLook: { styles: STYLES, fields: [P("layout", ["list", "grid2", "grid3", "sticky", "diary", "compact"], { optional: true }),
    P("card", ["own", "one", "none"], { optional: true }), C("cardColor", { optional: true }), P("border", ["none", "thin"], { optional: true }),
    N("corner", 0, 24, { optional: true }), N("lines", 1, 10, { optional: true }), P("title", ["s", "m", "l"], { optional: true }), F("bold"),
    // [MAT-05] 1.36 any app font or the user's own (core FontCatalog).
    T("font", 40, { optional: true, guard: false, check: fontId }), F("date"), C("bg", { optional: true })] },
  // [FREE-06] 1.35 가계부 모양.
  moneyLook: { styles: STYLES, fields: [P("rows", ["compact", "normal", "roomy"], { optional: true }), P("amount", ["s", "m", "l"], { optional: true }),
    P("mark", ["emoji", "dot", "none"], { optional: true }), C("income", { optional: true }), C("expense", { optional: true }),
    P("dayHead", ["line", "band", "big"], { optional: true })] },
  // [FREE-05][FREE-07] 1.35 화면 색·배경 (pictures by recipe_images id, looked at before others see them) and 급여 모양 by kind of work.
  screenLook: { styles: STYLES, fields: [T("screens", 4000, { optional: true, guard: false, check: screenLooks }),
    T("pay", 400, { optional: true, guard: false, check: payLooks })] },
  // [TAB-07] 1.36 탭 보기, as core PieceKind.TAB_VIEW (CalendarBoard·MemoView·LedgerBoard).
  tabView: { styles: STYLES, fields: [P("cal", ["month", "weekly", "list", "rota", "timeline", "yearly"], { optional: true }),
    P("memo", ["list", "board", "table", "cal", "gallery"], { optional: true }),
    T("memoBy", 22, { optional: true, guard: false, check: (s) => (memoBy(s) ? null : "memoBy") }),
    T("memoSort", 23, { optional: true, guard: false, check: (s) => (memoSort(s) ? null : "memoSort") }),
    P("money", ["list", "cal", "board", "table"], { optional: true })] },
  // [BLU-01] 1.36 부품 꾸미기.
  parts: { styles: STYLES, fields: [I("parts", 96, [P("id", PART_IDS), P("on", PART_STATES, { optional: true }),
    // 1.36 [RUL-02] 조건부 꾸미기: the line only on days its formula holds.
    T("when", FORMULA.maxLen, { optional: true, guard: false, check: (s) => logicFormula(s) }), ...PART_STYLE], { min: 1, check: partLines })] },
  // 1.36 [RUL-03][RUL-04] 수식·규칙·버튼 (core PieceKind.LOGIC, formula/Logic.kt): formulas read as core reads them, safe verbs only.
  logic: { styles: STYLES, fields: [
    // 누락 점검 N8: id (core LogicButton.ID), what a 꾸밈 항목 tap names.
    I("buttons", 8, [T("id", 12, { optional: true, guard: false, check: (s) => (/^[a-z0-9]{1,12}$/.test(s) ? null : "id") }), T("label", 16, { min: 1 }), E("emoji", { input: false }), P("where", BUTTON_PLACES), P("do", LOGIC_VERBS),
      T("arg", 120, { optional: true, guard: false, check: logicText }), M("amount", 0, 100_000_000, { optional: true }),
      T("show", FORMULA.maxLen, { optional: true, guard: false, check: logicFormula }), N("by", -1_000_000, 1_000_000, { decimals: 2, optional: true }),
      ...LOGIC_ACT_MORE,
      // 1.39 [FX-11] 그리고/아니면, 동작 줄 (core PieceCatalog LOGIC_ACT).
      T("if", FORMULA.maxLen, { optional: true, guard: false, check: logicFormula }), I("more", 3, LOGIC_ACT, { optional: true }), I("else", 3, LOGIC_ACT, { optional: true })], { optional: true }),
    I("rules", 12, [T("name", 20, { min: 1 }), P("on", RULE_TRIGGERS), N("min", -720, 720, { optional: true, input: true }), K("at", { optional: true, input: false }),
      P("role", ROLES, { optional: true }), T("when", FORMULA.maxLen, { optional: true, guard: false, check: logicFormula }), P("do", LOGIC_VERBS),
      T("arg", 120, { optional: true, guard: false, check: logicText }), M("amount", 0, 100_000_000, { optional: true }),
      N("by", -1_000_000, 1_000_000, { decimals: 2, optional: true }), TS("then", 2, 12),
      N("after", 1, 720, { optional: true }), T("text", 24, { optional: true, guard: false, check: logicText }),
      // 1.39 [FX-11] 동작 줄·아니면, [FX-12] 값이 바뀌면, [FX-15] 몇 분마다.
      I("more", 3, LOGIC_ACT, { optional: true }), I("else", 3, LOGIC_ACT, { optional: true }), T("of", 12, { optional: true, check: valueName }),
      P("edge", ["goal"], { optional: true }), K("until", { optional: true, input: false }), P("during", ["work"], { optional: true })], { optional: true }),
    // 1.39 [FX-03] 값 칸 (core LogicValue); [FX-19] 목표는 받는 사람 칸이어도 돼요.
    I("values", 8, [T("name", 12, { min: 1, check: valueName }), P("reset", VALUE_RESETS, { optional: true }),
      N("start", -1_000_000, 1_000_000, { decimals: 2, optional: true }), N("goal", 0, 1_000_000, { decimals: 2, optional: true, input: true }),
      T("unit", 4, { optional: true }), P("kind", VALUE_KINDS, { optional: true })], { optional: true }),
  ] },
  // [LAY-01] 1.36 꾸밈 층 (core Decor.FIELDS, same order and ranges).
  decor: { styles: STYLES, fields: [I("items", DECOR_MAX, [
    P("shape", DECOR_SHAPES, { optional: true }), P("icon", DECOR_ICONS, { optional: true }), E("emoji", { input: false }),
    T("text", 40, { optional: true, input: true }), P("at", DECOR_ANCHORS), T("on", 12, { optional: true, guard: false, check: decorOn }),
    N("x", 0, 1000), N("y", 0, 1000), N("w", 2, 400), N("h", 2, 400), N("rot", 0, 359, { optional: true }),
    C("fill", { optional: true }), N("fillA", 0, 100, { optional: true }),
    P("line", DECOR_LINES, { optional: true }), N("lineW", 0, 8, { decimals: 1, optional: true }), C("lineC", { optional: true }),
    N("r", 0, 48, { optional: true }), N("shadow", 0, 24, { optional: true }), T("font", 40, { optional: true, guard: false, check: decorFont }),
    N("size", 6, 96, { decimals: 1, optional: true }), N("weight", 100, 900, { optional: true }), C("ink", { optional: true }), F("italic", { input: false }),
    N("alpha", 0, 100, { optional: true }), F("hide", { input: false }), F("over", { input: false }), F("flip", { input: false }),
    F("lock", { input: false }), N("group", 0, 9, { optional: true }),
    T("photo", 16, { optional: true, guard: false, check: decorPhoto }), T("tap", 12, { optional: true, guard: false, check: decorTap }),
    F("full", { input: false }),
  ], { min: 1 })] },
  // 1.37 [LEDH-18] 가계부 틀: categories, budgets and empty envelopes; amounts are the receiver's.
  ledger: { styles: ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS"], fields: [TS("costs", 20, 20), TS("incomes", 12, 20),
    I("budgets", 12, [T("category", 20, { min: 1 }), M("amount", 0, 50_000_000)], { optional: true }),
    I("envelopes", 6, [T("name", 20, { min: 1 }), M("target", 0, 1_000_000_000, { optional: true }), N("share", 0, 100, { optional: true }), E("emoji")], { optional: true })] },
};
const PIECE_KEYS = Object.keys(RECIPE_PIECES);

// ---- words (core RecipeText) ----

const ZWJ = 0x200d;
const VS16 = 0xfe0f;
const HIDDEN = /[\p{Cc}\p{Cf}\p{Co}\p{Cn}\p{Cs}]/u;
const SO = /\p{So}/u;

function emojiLike(cp) {
  return cp >= 0x1f000 || (cp >= 0x2190 && cp <= 0x2bff) || (cp >= 0x2300 && cp <= 0x23ff) || cp === 0xa9 || cp === 0xae ||
    (cp >= 0x1f3fb && cp <= 0x1f3ff) || SO.test(String.fromCodePoint(cp));
}

/** core RecipeText.clean. */
export function cleanText(s, lines = false, maxLines = 20) {
  const cps = [...s.normalize("NFC")].map((c) => c.codePointAt(0));
  let out = "";
  cps.forEach((cp, i) => {
    if (cp === 10) out += lines ? "\n" : " ";
    else if (cp === 13) {
      // dropped
    } else if (cp === 9) out += " ";
    else if (cp === ZWJ) {
      if (i > 0 && i < cps.length - 1 && emojiLike(cps[i - 1]) && emojiLike(cps[i + 1])) out += String.fromCodePoint(cp);
    } else if (cp === VS16) {
      if (i > 0 && emojiLike(cps[i - 1])) out += String.fromCodePoint(cp);
    } else if (HIDDEN.test(String.fromCodePoint(cp)) || (cp >= 0xfff9 && cp <= 0xfffb) || (cp >= 0xe0000 && cp <= 0xe007f)) {
      // dropped
    } else out += String.fromCodePoint(cp);
  });
  let t = out.replace(/[  　]+/g, " ");
  if (lines) {
    t = t.split("\n").map((l) => l.trim()).join("\n").replace(/\n{3,}/g, "\n\n");
    const kept = t.split("\n");
    if (kept.length > maxLines) t = kept.slice(0, maxLines - 1).join("\n") + " " + kept.slice(maxLines - 1).join(" ");
  }
  return t.trim();
}

const MARK = /[\p{Mn}\p{Me}]/u;

/** core RecipeText.graphemes: what a person counts as one letter. */
export function graphemes(s) {
  const cps = [...s].map((c) => c.codePointAt(0));
  let n = 0;
  let flagHalf = false;
  for (let i = 0; i < cps.length; i++) {
    const cp = cps[i];
    if (cp === ZWJ) {
      i++;
    } else if ((cp >= 0xfe00 && cp <= 0xfe0f) || (cp >= 0x1f3fb && cp <= 0x1f3ff) || (cp >= 0xe0020 && cp <= 0xe007f) || MARK.test(String.fromCodePoint(cp))) {
      // adds nothing
    } else if (cp >= 0x1f1e6 && cp <= 0x1f1ff) {
      if (!flagHalf) n++;
      flagHalf = !flagHalf;
      continue;
    } else n++;
    flagHalf = false;
  }
  return n;
}

const PROBLEMS = [
  ["email", /[^\s@]+@[^\s@]+\.[a-z]{2,}/i],
  ["phone", /(\+?82[\s.-]?0?|0)\d{1,2}[\s.-]?\d{3,4}[\s.-]?\d{4}/],
  ["amount", /\d[\d,.]*\s*(원|만\s*원|천\s*원|억\s*원|won(?![A-Za-z0-9])|krw(?![A-Za-z0-9]))|[일이삼사오육칠팔구십백천]+\s*만\s*원|\d{1,3}(,\d{3})+|\d{5,}/i],
  ["link", /(https?|ftp)\s*:\s*\/\s*\/|www\s*\.|[a-z0-9-]+\s*\.\s*(com|net|org|kr|co|io|me|ly|gl|app|xyz|link|site|shop|kakao)(?![A-Za-z0-9])|bit\s*\.\s*ly|open\s*\.\s*kakao/i],
  ["chat", /(오픈\s*채팅|단톡\s*방?|카톡\s*방|카카오톡\s*방|텔레그램|텔레\s*방|디스코드|라인\s*아이디|카톡\s*아이디|문의는\s*개인)/],
];

/** core RecipeText.problem: why a text cannot go in a recipe or its talk. */
export function textProblem(text) {
  // [RCP-40] Read as it looks: full-width letters and dots (ｂｉｔ．ｌｙ) and 。 are the plain ones (core does the same).
  const t = String(text).normalize("NFKC").replace(/。/g, ".");
  for (const [code, re] of PROBLEMS) if (re.test(t)) return code;
  return null;
}

/** core RecipeText.problemExcept: as textProblem without [skip]. */
export function textProblemExcept(text, skip) {
  const t = String(text).normalize("NFKC").replace(/。/g, ".");
  for (const [code, re] of PROBLEMS) if (code !== skip && re.test(t)) return code;
  return null;
}

const R_INVEST_ADVICE = /리딩\s*방|리딩해|수익\s*(보장|확정)|원금\s*보장|무조건\s*(올라|오릅|상승|수익)|(카톡|카카오톡|텔레그램|오픈\s*채팅|단톡)\s*방?\s*(으로|에|들어|입장|초대)|급등\s*주\s*(추천|알려)|종목\s*(추천|찍어|알려)\s*(드|줄|해)/;
export const investAdvice = (text) => R_INVEST_ADVICE.test(text);

const BARE = new Map([...EMOJI].map((e) => [e.replace(/️/g, ""), e]));

/** core RecipeText.oneEmoji. */
export function oneEmoji(s) {
  if (typeof s !== "string") return null;
  const t = s.trim().normalize("NFC");
  if (!t) return null;
  if (EMOJI.has(t)) return t;
  return BARE.get(t.replace(/️/g, "")) ?? null;
}

// ---- names (core NameSkeleton) ----

const FOLD = new Map(Object.entries({
  а: "a", в: "b", е: "e", ё: "e", к: "k", м: "m", н: "h", о: "o", р: "p", с: "c", т: "t", у: "y", х: "x", ѕ: "s", і: "i", ј: "j", ԁ: "d", һ: "h",
  ԛ: "q", ԝ: "w", ɡ: "g", ı: "i", ӏ: "l", ո: "n", ս: "u", α: "a", β: "b", ε: "e", η: "n", ι: "i", κ: "k", ν: "v", ο: "o", ρ: "p", τ: "t",
  υ: "u", χ: "x", ω: "w", γ: "y", 0: "o", 1: "l", "|": "l", "!": "l", i: "l", 5: "s", $: "s", "@": "a", 3: "e", 8: "b",
}));
const WORDISH = /[\p{L}\p{Nd}]/u;
const SPACE = /\s/u;

/** core NameSkeleton.of: what two names that look alike have in common (UTS #39, the common confusables). */
export function nameSkeleton(name) {
  const n = cleanText(String(name ?? "")).normalize("NFKC").toLowerCase();
  let b = "";
  for (let i = 0; i < n.length; i++) {
    const c = n[i];
    if (SPACE.test(c)) continue;
    const f = FOLD.get(c);
    if (f !== undefined) b += f;
    else if (WORDISH.test(c)) b += c;
  }
  return b.replaceAll("rn", "m").replaceAll("vv", "w").replaceAll("cl", "d");
}

const RESERVED = ["관리자", "개발자", "운영자", "운영진", "운영팀", "개발팀", "bamnat", "admin", "official"].map(nameSkeleton);
const ALONE = new Set(["밤낮", "공식", "운영"].map(nameSkeleton));
const CLAIM = new RegExp(`(${nameSkeleton("밤낮")}|앱|레시피)(공식|팀|운영|관리|개발|본사|제공|인증)|공식(레시피|인증|추천|앱|배포|템플릿|틀|계정|${nameSkeleton("밤낮")})`);

/** core NameSkeleton.reserved: a name only the operator's account may use (설계서 4-7 S4). */
export function reservedName(name) {
  const s = nameSkeleton(name);
  return ALONE.has(s) || RESERVED.some((r) => s.includes(r)) || CLAIM.test(s);
}

/** core NameSkeleton.mixedScript: Cyrillic or Greek letters in a word with Latin or Hangul ones. */
export function mixedScript(text) {
  return String(text).split(/[\s\p{P}]+/u).some((w) => {
    const odd = /[\p{Script=Cyrillic}\p{Script=Greek}]/u.test(w);
    return odd && /[\p{Script=Latin}\p{Script=Hangul}]/u.test(w);
  });
}

// ---- reading (core RecipeCodec) ----

/** How deep [text]'s objects and arrays go, counted before parsing (S2). */
export function jsonDepth(text) {
  let d = 0;
  let max = 0;
  let inString = false;
  let escape = false;
  for (const c of text) {
    if (inString) {
      if (escape) escape = false;
      else if (c === "\\") escape = true;
      else if (c === '"') inString = false;
      continue;
    }
    if (c === '"') inString = true;
    else if (c === "{" || c === "[") max = Math.max(max, ++d);
    else if (c === "}" || c === "]") d--;
  }
  return max;
}

const isObj = (v) => v !== null && typeof v === "object" && !Array.isArray(v);
const isInt = (v) => typeof v === "number" && Number.isInteger(v);

function text(v, max, path, min = 0, lines = false, guard = true) {
  if (typeof v !== "string") fail("text", path);
  const t = guard ? cleanText(v, lines) : v.trim();
  const n = graphemes(t);
  if (n < min || n > max) fail("text", path);
  if (guard) {
    // [RCX-31] 1.35: 리딩방·종목 추천 is refused in every text of a recipe (제목, 설명, 칸 이름), not only in 투자 원칙.
    const why = textProblem(t) ?? (investAdvice(t) ? "invest_advice" : null);
    if (why) fail(why, path);
  }
  return t;
}

function color(v, path) {
  if (!isInt(v) || v < 0 || v > 0xffffffff) fail("color", path);
  return 0xff000000 + (v & 0xffffff);
}

function number(v, decimals, path) {
  if (typeof v !== "number" || !Number.isFinite(v)) fail("number", path);
  if (decimals === 0) {
    if (!Number.isInteger(v)) fail("number", path);
    return v;
  }
  const scale = 10 ** decimals;
  return Math.round(v * scale) / scale;
}

function inputValue(kind, v, path, min, max, choices) {
  switch (kind) {
    case "money":
      if (!isInt(v)) fail("number", path);
      if (v < 0 || (min !== null && v < min) || (max !== null && v > max) || v > 100_000_000) fail("range", path);
      return v;
    case "number": {
      const n = number(v, 2, path);
      if ((min !== null && n < min) || (max !== null && n > max)) fail("range", path);
      return n;
    }
    case "time":
      if (typeof v !== "string" || !R_TIME.test(v)) fail("time", path);
      return v;
    case "color":
      return color(v, path);
    case "emoji":
      return oneEmoji(v) ?? fail("emoji", path);
    case "choice":
      if (typeof v !== "string" || !choices.includes(v)) fail("choice", path);
      return v;
    case "text":
      return text(v, 60, path, 1);
    default:
      if (typeof v !== "boolean") fail("flag", path);
      return v;
  }
}

function readInput(o, path) {
  if (!isObj(o)) fail("inputs", path);
  if (typeof o.id !== "string" || !INPUT_ID.test(o.id)) fail("inputs", `${path}.id`);
  if (!INPUT_KINDS.includes(o.kind)) fail("inputs", `${path}.kind`);
  const label = text(o.label, RECIPE.inputLabel, `${path}.label`, 1);
  const min = o.min === undefined || o.min === null ? null : number(o.min, 2, `${path}.min`);
  const max = o.max === undefined || o.max === null ? null : number(o.max, 2, `${path}.max`);
  if (min !== null && max !== null && min > max) fail("inputs", `${path}.max`);
  const rawChoices = Array.isArray(o.choices) ? o.choices : [];
  if (rawChoices.length > 12) fail("inputs", `${path}.choices`);
  const choices = rawChoices.map((c, i) => text(c, 20, `${path}.choices.${i}`, 1));
  if (o.kind === "choice" && choices.length < 2) fail("inputs", `${path}.choices`);
  const def = o.default === undefined || o.default === null ? null : inputValue(o.kind, o.default, `${path}.default`, min, max, choices);
  const hint = text(o.hint ?? "", 60, `${path}.hint`);
  return { id: o.id, label, kind: o.kind, default: def, min, max, required: o.required === true, choices: o.kind === "choice" ? choices : [], hint };
}

function ref(f, v, path, ctx) {
  if (!isObj(v)) return null;
  const keys = Object.keys(v);
  if (keys.length !== 1) fail("ref", path);
  // 1.36 [RUL-02] a formula, only where a part's style takes one (core RecipeCodec.ref).
  if (typeof v.fx === "string") {
    if (!PART_STYLE.includes(f)) fail("ref", path);
    const t = v.fx.trim();
    if (t.length === 0 || t.length > FORMULA.maxLen) fail("text", path);
    const why = logicFormula(t);
    if (why) fail(why, path);
    return { fx: t };
  }
  if (typeof v.token === "string") {
    if (f.t !== "color" || !ctx.tokens.has(v.token)) fail("ref", path);
    return { token: v.token };
  }
  if (typeof v.input !== "string") fail("ref", path);
  const input = ctx.inputs.get(v.input);
  if (!input) fail("ref", path);
  const fits = {
    money: () => input.kind === "money",
    num: () => f.input && input.kind === "number",
    text: () => f.input && input.kind === "text",
    color: () => input.kind === "color",
    emoji: () => f.input && input.kind === "emoji",
    flag: () => f.input && input.kind === "flag",
    clock: () => f.input && input.kind === "time",
    pick: () => f.input && input.kind === "choice" && input.choices.every((c) => f.values.includes(c)),
  }[f.t];
  if (!fits || !fits()) fail("ref", path);
  return { input: v.input };
}

const empty = (v) => (Array.isArray(v) ? v.length === 0 : v === "");

function fields(schema, o, path, ctx) {
  const out = {};
  for (const f of schema) {
    const p = `${path}.${f.key}`;
    const v = o[f.key];
    if (v === undefined || v === null) {
      if (!f.optional) fail("missing", p);
      continue;
    }
    const r = ref(f, v, p, ctx);
    if (r) {
      out[f.key] = r;
      continue;
    }
    let value;
    switch (f.t) {
      case "text":
        value = text(v, f.max, p, f.min, f.lines, f.guard);
        if (f.check) {
          const why = f.check(value);
          if (why) fail(why, p);
        }
        break;
      case "num":
        value = number(v, f.decimals, p);
        if (value < f.lo || value > f.hi) fail("range", p);
        break;
      case "money":
        if (!isInt(v)) fail("number", p);
        if (v < f.lo || v > f.hi) fail("range", p);
        value = v;
        break;
      case "color":
        value = color(v, p);
        break;
      case "emoji":
        value = oneEmoji(v) ?? fail("emoji", p);
        break;
      case "flag":
        if (typeof v !== "boolean") fail("flag", p);
        value = v;
        break;
      case "pick":
        if (typeof v !== "string" || !f.values.includes(v)) fail("pick", p);
        value = v;
        break;
      case "clock":
        if (typeof v !== "string" || !R_TIME.test(v)) fail("time", p);
        value = v;
        break;
      case "texts":
        if (!Array.isArray(v) || v.length > f.max) fail("list", p);
        value = v.map((t, i) => text(t, f.itemMax, `${p}.${i}`, 1));
        break;
      case "nums": {
        if (!Array.isArray(v) || v.length > f.max) fail("list", p);
        const seen = [];
        v.forEach((n, i) => {
          const x = number(n, f.decimals, `${p}.${i}`);
          if (x < f.lo || x > f.hi) fail("range", `${p}.${i}`);
          if (!seen.includes(x)) seen.push(x);
        });
        value = seen;
        break;
      }
      case "days": {
        if (!Array.isArray(v)) fail("list", p);
        const ds = v.map((x) => (typeof x === "string" && WEEK_DAYS.includes(x) ? x : fail("pick", p)));
        value = [...new Set(ds)].sort((a, b) => WEEK_DAYS.indexOf(a) - WEEK_DAYS.indexOf(b));
        break;
      }
      case "items":
        if (!Array.isArray(v) || v.length > f.max || v.length < f.min) fail("list", p);
        value = v.map((item, i) => (isObj(item) ? fields(f.fields, item, `${p}.${i}`, ctx) : fail("list", `${p}.${i}`)));
        if (f.check) {
          const why = f.check(value);
          if (why) fail(why, p);
        }
        break;
      default:
        fail("field", p);
    }
    if ((f.t === "text" || f.t === "texts" || f.t === "items") && f.optional && empty(value)) continue;
    if (f.t === "nums" && f.optional && empty(value)) continue;
    out[f.key] = value;
  }
  return out;
}

/**
 * [FXR-04] 1.36 받는 칸을 수식이 읽기: the 칸 names a text reads through `칸("이름")` / `칸("이름", 기본값)` (`input(...)`), in any text of a
 * piece (a `when` line, a {"fx":…} value, a rule's 조건, a message with {…}). The same as core FormulaInputs.labels.
 */
const FORMULA_INPUT = /(?<![\p{L}\p{N}_])(?:칸|[iI][nN][pP][uU][tT])\s*\(\s*["“”'‘’]([^"“”'‘’]*)["“”'‘’]/gu;

export function formulaInputLabels(v, into = new Set()) {
  if (typeof v === "string") {
    if (v.includes("(")) for (const m of v.matchAll(FORMULA_INPUT)) { const l = m[1].trim(); if (l) into.add(l); }
  } else if (Array.isArray(v)) v.forEach((x) => formulaInputLabels(x, into));
  else if (isObj(v)) Object.values(v).forEach((x) => formulaInputLabels(x, into));
  return into;
}

function inputsUsed(v, into) {
  if (Array.isArray(v)) v.forEach((x) => inputsUsed(x, into));
  else if (isObj(v)) {
    if (typeof v.input === "string" && Object.keys(v).length === 1) into.add(v.input);
    else Object.values(v).forEach((x) => inputsUsed(x, into));
  }
}

/** The recipe [raw] (parsed JSON) as core RecipeCodec.clean keeps it, in core's toMap shape. Throws RecipeError. */
export function cleanRecipeObject(raw) {
  if (!isObj(raw)) fail("format");
  if (raw.format !== RECIPE.format) fail("format", "format");
  if (!isInt(raw.v)) fail("version", "v");
  if (raw.v > RECIPE.version) fail("update", "v");
  if (raw.v < 1) fail("version", "v");
  const title = text(raw.title, RECIPE.title, "title", 1);
  if (reservedName(title)) fail("name_taken", "title");
  const summary = text(raw.summary ?? "", RECIPE.summary, "summary");
  const desc = text(raw.desc ?? "", RECIPE.desc, "desc", 0, true);
  const rawTokens = Array.isArray(raw.tokens) ? raw.tokens : [];
  if (rawTokens.length > RECIPE.maxTokens) fail("tokens", "tokens");
  const tokens = rawTokens.map((t, i) => {
    if (!isObj(t)) fail("tokens", `tokens.${i}`);
    return { name: text(t.name, RECIPE.tokenName, `tokens.${i}.name`, 1), light: color(t.light, `tokens.${i}.light`), dark: color(t.dark, `tokens.${i}.dark`) };
  });
  if (new Set(tokens.map((t) => t.name)).size !== tokens.length) fail("tokens", "tokens");
  const rawInputs = Array.isArray(raw.inputs) ? raw.inputs : [];
  if (rawInputs.length > RECIPE.maxInputs) fail("inputs", "inputs");
  const inputs = rawInputs.map((o, i) => readInput(o, `inputs.${i}`));
  if (new Set(inputs.map((i) => i.id)).size !== inputs.length) fail("inputs", "inputs");
  const ctx = { inputs: new Map(inputs.map((i) => [i.id, i])), tokens: new Set(tokens.map((t) => t.name)) };
  if (!Array.isArray(raw.pieces)) fail("pieces", "pieces");
  if (raw.pieces.length > RECIPE.maxPieces) fail("pieces", "pieces");
  const seen = new Set();
  const pieces = [];
  raw.pieces.forEach((p, i) => {
    if (!isObj(p)) fail("pieces", `pieces.${i}`);
    const kind = typeof p.kind === "string" && PIECE_KEYS.includes(p.kind) ? p.kind : null;
    if (!kind) return; // a kind this server does not know
    if (seen.has(kind)) fail("pieces", `pieces.${i}`);
    seen.add(kind);
    pieces.push({ kind, ...fields(RECIPE_PIECES[kind].fields, p, `pieces.${i}`, ctx) });
  });
  if (!pieces.length) fail("pieces", "pieces");
  const fits = new Set(pieces.flatMap((p) => RECIPE_PIECES[p.kind].styles));
  const declared = new Set((Array.isArray(raw.styles) ? raw.styles : []).filter((s) => ALL_STYLES.has(s) && fits.has(s)));
  const styles = STYLES.filter((s) => declared.has(s));
  if (!styles.length) fail("styles", "styles");
  let cover = { a: 0xff3f51b5, b: 0xff9c27b0, dir: 0, emoji: "🗓️" };
  if (isObj(raw.cover)) {
    const c = raw.cover;
    const a = color(c.a, "cover.a");
    const b = color(c.b, "cover.b");
    const dir = isInt(c.dir) ? c.dir : 0;
    if (dir < 0 || dir > 3) fail("cover", "cover.dir");
    const emoji = typeof c.emoji === "string" ? oneEmoji(c.emoji) : null;
    if (!emoji) fail("emoji", "cover.emoji");
    cover = { a, b, dir, emoji };
    // [RCX-15] 1.35 그림 표지: an id of recipe_images.
    if (c.image !== undefined && c.image !== null) {
      if (typeof c.image !== "string" || !/^[0-9a-f]{16}$/.test(c.image)) fail("cover", "cover.image");
      cover.image = c.image;
    }
  }
  let parent = null;
  if (typeof raw.parent === "string") {
    if (!CODE.test(raw.parent)) fail("parent", "parent");
    parent = raw.parent;
  }
  const used = new Set();
  pieces.forEach((p) => inputsUsed(p, used));
  // [FXR-04] An input a formula reads by its name (칸("이름")) is used too, or it would go here and never reach the formula.
  const named = new Set();
  pieces.forEach((p) => formulaInputLabels(p, named));
  const out = { format: RECIPE.format, v: RECIPE.version, title };
  if (summary) out.summary = summary;
  if (desc) out.desc = desc;
  out.styles = styles;
  out.cover = cover;
  if (tokens.length) out.tokens = tokens;
  const kept = inputs.filter((i) => used.has(i.id) || named.has(i.label)).map((i) => {
    const o = { id: i.id, label: i.label, kind: i.kind };
    if (i.default !== null) o.default = i.default;
    if (i.min !== null) o.min = i.min;
    if (i.max !== null) o.max = i.max;
    if (i.required) o.required = true;
    if (i.choices.length) o.choices = i.choices;
    if (i.hint) o.hint = i.hint;
    return o;
  });
  if (kept.length) out.inputs = kept;
  out.pieces = pieces;
  if (parent) out.parent = parent;
  return out;
}

/** JSON with keys in order and nothing between tokens: core RecipeCodec.canonical. */
export function recipeCanonical(v) {
  if (v === null || v === undefined) return "null";
  if (typeof v === "boolean") return String(v);
  if (typeof v === "number") return Number.isInteger(v) ? String(v) : String(Math.round(v * 100) / 100);
  if (typeof v === "string") return JSON.stringify(v);
  if (Array.isArray(v)) return `[${v.map(recipeCanonical).join(",")}]`;
  const keys = Object.keys(v).filter((k) => v[k] !== null && v[k] !== undefined).sort();
  return `{${keys.map((k) => `${JSON.stringify(k)}:${recipeCanonical(v[k])}`).join(",")}}`;
}

/** [RCX-05] 1.35 The public id of a maker: one way from the install hash, 12 letters. 'operator' stays 'operator' (밤낮 운영). */
export async function makerId(owner) {
  if (owner === "operator") return "operator";
  return (await sha256hex(`maker|${owner}`)).slice(0, 12);
}

export async function sha256hex(text) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** [text] (as sent) read, cleaned and written: {recipe, text, hash}. Throws RecipeError. */
/** [PAYP-08] cleanRecipe's {recipe, text, hash} as others may read it. */
async function sharedOnly(clean) {
  const out = await sharedPayOut(clean.text, clean.hash);
  return out.body === clean.text ? {} : { text: out.body, hash: out.hash, recipe: JSON.parse(out.body) };
}

/**
 * 1.39 [PAYP-08] (사용자 2026-11-07 "급여 계산 정보는 공개되면 안될 정보"; 숨은 문제 발굴 P0-1 ①): the 근무 주기·급여 방식 piece
 * carries the company file, pay part and all. A recipe others can read (공개·링크) keeps its shifts and order, never the pay part:
 * shared pay ways go through the company 광장, which never shows them ([PAYP-01]). Taken out when a recipe is put up and again
 * when one put up before 1.39 is read, with its hash for the text as served. A 나만 보기 recipe is the maker's own and keeps it.
 */
export async function sharedPayOut(text, hash) {
  if (typeof text !== "string" || !text.includes('"company"')) return { body: text, hash };
  let v;
  try {
    v = JSON.parse(text);
  } catch {
    return { body: text, hash };
  }
  let changed = false;
  for (const p of Array.isArray(v?.pieces) ? v.pieces : []) {
    if (p?.kind !== "company" || typeof p.setup !== "string") continue;
    try {
      const file = JSON.parse(p.setup);
      if (file && typeof file === "object" && file.pay !== undefined) {
        delete file.pay;
        p.setup = JSON.stringify(file);
        changed = true;
      }
    } catch {
      // A piece cleanRecipe let in always parses; anything else is left as it is.
    }
  }
  if (!changed) return { body: text, hash };
  const out = recipeCanonical(v);
  return { body: out, hash: await sha256hex(out) };
}

export async function cleanRecipe(textOrObject) {
  const textIn = typeof textOrObject === "string" ? textOrObject : recipeCanonical(textOrObject);
  if (new TextEncoder().encode(textIn).length > RECIPE.maxBytes) fail("too_big");
  if (jsonDepth(textIn) > RECIPE.maxDepth) fail("too_deep");
  let raw;
  try {
    raw = JSON.parse(textIn);
  } catch {
    fail("json");
  }
  const recipe = cleanRecipeObject(raw);
  const out = recipeCanonical(recipe);
  return { recipe, text: out, hash: await sha256hex(out) };
}

// ---- ranking (core Rank) ----

const Z = 1.96;
export function recipeWilson(pos, n) {
  if (n <= 0) return 0;
  const p = pos / n;
  const z2 = Z * Z;
  return (p + z2 / (2 * n) - Z * Math.sqrt((p * (1 - p) + z2 / (4 * n)) / n)) / (1 + z2 / n);
}
export function rankGood(likes, dislikes, applied, kept7) {
  const n = likes + dislikes;
  if (n < 5) return null;
  const w = recipeWilson(likes, n);
  return applied >= 20 ? 0.7 * w + 0.3 * Math.min(1, Math.max(0, kept7 / applied)) : w;
}
export const rankHot = (likes, applied, hours) => Math.log(1 + likes + applied * 2) / Math.pow(Math.max(0, hours) + 2, 1.5);
export const keptPercent = (applied, kept7) => (applied < 20 ? null : Math.min(100, Math.max(0, Math.trunc((100 * kept7) / applied))));

// ---- the server's part ----

export const RECIPE_LIMITS = {
  /**
   * 1.36 [LIM-10] (사용자 2026-10-31 "제한을 두지마"): no daily cap for one install. Only strikes restrict one (write-limits.js);
   * one network's day still has a cap against a flood.
   */
  perNetworkDay: 60,
  /**
   * D108 (1.34) had a phone's first 3 public recipes wait for the operator. 1.35 [RCX-28] (사용자 2026-10-30 "3개는 관리자 승인을
   * 본다한 부분은 폐지해줘 자율성이 떨어져"): 0, a recipe is up at once; 신고 3번이면 먼저 가려지고(flagsToHide) 운영자가 봄.
   */
  reviewFirst: 0,
  votesPerHour: 60,
  /** S8: more votes than this from one network in an hour wait (held) and count nowhere until the operator lets them. */
  networkVotesHour: 30,
  appliedPerNetworkDay: 200,
  /** 1.36 보안 점검 M9: one network counts at most this many 넣기 (and as many 7일 유지) for one recipe a day; past it, not counted. */
  appliedPerRecipeNetworkDay: 3,
  flagsToHide: 3,
  opinion: [5, 120],
  talk: 500,
  pageSize: 30,
  edgeSeconds: 60,
};
export const DISLIKE_REASONS = { thin: "설명이 부족해요", odd: "내 달력에서 이상해요", same: "비슷한 게 이미 있어요", hard: "쓰기 어려워요", other: "그 밖의 것" };
export const TALK_KINDS = ["question", "opinion", "idea", "review"];
export const RECIPE_FLAG_REASONS = ["personal", "fake", "harm", "spam", "copy", "other"];
export const RECIPE_TABLE = "recipe";

export const RECIPE_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS recipes (code TEXT PRIMARY KEY, owner TEXT NOT NULL, title TEXT NOT NULL, title_skel TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '', styles TEXT NOT NULL, cover TEXT NOT NULL, scope TEXT NOT NULL, state TEXT NOT NULL,
    official INTEGER NOT NULL DEFAULT 0, public_data INTEGER NOT NULL DEFAULT 0, parent TEXT, version INTEGER NOT NULL, hash TEXT NOT NULL,
    body TEXT NOT NULL, likes INTEGER NOT NULL DEFAULT 0, dislikes INTEGER NOT NULL DEFAULT 0, wlikes REAL NOT NULL DEFAULT 0,
    wdislikes REAL NOT NULL DEFAULT 0, applied INTEGER NOT NULL DEFAULT 0, kept7 INTEGER NOT NULL DEFAULT 0, flags INTEGER NOT NULL DEFAULT 0,
    note TEXT NOT NULL DEFAULT '', hidden_reason TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, decided_at INTEGER, hidden_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS recipes_list ON recipes (state, scope, updated_at)",
  "CREATE INDEX IF NOT EXISTS recipes_owner ON recipes (owner)",
  "CREATE INDEX IF NOT EXISTS recipes_skel ON recipes (title_skel)",
  `CREATE TABLE IF NOT EXISTS recipe_versions (code TEXT NOT NULL, version INTEGER NOT NULL, hash TEXT NOT NULL, body TEXT NOT NULL,
    created_at INTEGER NOT NULL, PRIMARY KEY (code, version))`,
  `CREATE TABLE IF NOT EXISTS recipe_votes (code TEXT NOT NULL, device TEXT NOT NULL, value INTEGER NOT NULL, weight REAL NOT NULL,
    reason TEXT, opinion TEXT, held INTEGER NOT NULL DEFAULT 0, net TEXT, at INTEGER NOT NULL, PRIMARY KEY (code, device))`,
  "CREATE INDEX IF NOT EXISTS recipe_votes_device ON recipe_votes (device)",
  `CREATE TABLE IF NOT EXISTS recipe_counts (code TEXT NOT NULL, day TEXT NOT NULL, applied INTEGER NOT NULL DEFAULT 0,
    kept7 INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (code, day))`,
  `CREATE TABLE IF NOT EXISTS recipe_flags (code TEXT NOT NULL, device TEXT NOT NULL, reason TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (code, device))`,
  `CREATE TABLE IF NOT EXISTS recipe_trust (device TEXT PRIMARY KEY, first_at INTEGER NOT NULL, approved INTEGER NOT NULL DEFAULT 0,
    returned INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)`,
  // [RCP-33] 임시 조치 기록: every hide, show, return and delete of a recipe or its talk, by whom and why (정보통신망법 44조의2).
  `CREATE TABLE IF NOT EXISTS recipe_actions (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL, action TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT '', actor TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS recipe_actions_at ON recipe_actions (at)",
];
/** Columns on tables index.js made before 1.34. */
export const RECIPE_COLUMNS = [
  // [RCP-35] a recipe's talk: how many days the writer has used the recipe (the phone says), shown as 쓰는 중 N일.
  "ALTER TABLE posts ADD COLUMN using_days INTEGER",
  // [RCP-35][D107] a 안좋아요 on a talk post carries its reason too, seen by the post's writer only.
  "ALTER TABLE post_votes ADD COLUMN why TEXT",
  "ALTER TABLE post_votes ADD COLUMN note TEXT",
  // 1.36 보안 점검 [SEC-16] M10: whether a recipe report counted toward hiding it, and why not ("new": a 새싹 install, "net": a second
  // from one network today), for the operator.
  "ALTER TABLE recipe_flags ADD COLUMN counted INTEGER NOT NULL DEFAULT 1",
  "ALTER TABLE recipe_flags ADD COLUMN why TEXT",
  // 1.36 [MKS-01] 출시 대기: the operator's recipes put in ahead of an app update ('waiting' until release_at or 지금 공개),
  // shown only to apps at min_app or newer (the list's app=), in a named batch (출시 묶음) the operator releases at once.
  "ALTER TABLE recipes ADD COLUMN release_at INTEGER",
  "ALTER TABLE recipes ADD COLUMN min_app INTEGER",
  "ALTER TABLE recipes ADD COLUMN batch TEXT",
];

export function newCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  let s = "";
  for (const b of bytes) s += CODE_CHARS[b % 32];
  return s;
}

const kstDayOf = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);

/**
 * [FREE-08] What a list card draws its small calendar from (사용자 2026-10-30 "잘 만든 테마를 골라 사용"): the recipe's own colours,
 * kinds of day, first cell look, pattern and memo layout, so a theme is picked by eye. Only what the recipe carries; null if it
 * carries no look at all.
 */
export function lookOf(body) {
  let rec;
  try {
    rec = JSON.parse(body);
  } catch {
    return null;
  }
  const piece = (k) => (rec.pieces ?? []).find((p) => p.kind === k);
  const out = {};
  const screens = piece("screenLook")?.screens;
  if (typeof screens === "string" && screens) {
    const of = (key) => screens.split("/").find((e) => e.startsWith(key + "~"))?.slice(key.length + 1) ?? "";
    for (const part of [...of("all").split("|"), ...of("calendar").split("|")]) {
      const [k, v] = [part.slice(0, 2), part.slice(3)];
      if (k === "bg" || k === "cd" || k === "ik" || k === "ac") out[{ bg: "bg", cd: "card", ik: "ink", ac: "accent" }[k]] = v;
    }
  }
  const theme = piece("calTheme");
  for (const k of ["today", "sat", "sun"]) if (typeof theme?.[k] === "number") out[k] = theme[k];
  const days = piece("shiftLook")?.items;
  if (Array.isArray(days)) out.days = days.slice(0, 4).map((d) => ({ c: d.color && typeof d.color === "number" ? d.color : null, l: d.label ?? "", e: d.emoji ?? null, r: d.role }));
  const view = piece("calView");
  if (typeof view?.looks === "string" && view.looks) out.cells = view.looks.split("/").slice(0, 6).join("/");
  if (typeof view?.pattern === "string" && view.pattern) out.pattern = view.pattern;
  if (typeof view?.badge === "string") out.badge = view.badge;
  const memo = piece("memoLook");
  if (memo?.layout) out.memo = memo.layout;
  // [TAB-07] 1.36 탭 보기: the calendar board and memo view a 장터 card can say.
  const tv = piece("tabView");
  if (tv?.cal) out.board = tv.cal;
  if (tv?.memo) out.memoView = tv.memo;
  if (piece("photoLook")?.bg || (typeof screens === "string" && screens.includes("ph:"))) out.photo = true;
  return Object.keys(out).length ? out : null;
}

/** [FREE-08] 종류로 보기: what kind of piece a recipe must carry to show under each chip. */
export const RECIPE_KINDS = {
  skin: ["screenLook", "calTheme", "calView", "photoLook", "shiftLook", "decor"],
  layout: ["layout", "cards", "appFeel", "tabView"],
  memo: ["memoLook", "memoTpl"],
  money: ["moneyLook", "shop", "field", "ledger"],
  rules: ["alarms", "hourly", "invest", "company", "logic"],
};

const carries = (body, kinds) => kinds.some((k) => body.includes(`"kind":"${k}"`));

/**
 * [MKX-01] 1.36 장터 두 구간 (사용자 2026-10-31 "테마는 모든 것을 종합으로 만든 것이고 기능별은 한 개의 탭을 전문적으로"): the tabs a
 * recipe changes — calendar, pay (근무·급여·통계), money (가계부·장부), memo, alarm, widget, app (앱 전체 색·아래 탭·모서리·느낌).
 */
export const MARKET_TABS = ["calendar", "pay", "money", "memo", "alarm", "widget", "night"];
const SCREEN_TAB = { calendar: "calendar", memo: "memo", money: "money", stats: "pay", alarm: "alarm", all: "app", settings: "app", guarded: "app", plaza: "app" };

export function tabsOf(body) {
  let rec;
  try {
    rec = typeof body === "string" ? JSON.parse(body) : body;
  } catch {
    return [];
  }
  const out = new Set();
  for (const p of rec?.pieces ?? []) {
    switch (p.kind) {
      case "shiftLook": case "calTheme": case "calView": out.add("calendar"); break;
      case "memoLook": case "memoTpl": case "invest": out.add("memo"); break;
      case "moneyLook": case "shop": case "field": case "ledger": out.add("money"); break;
      case "hourly": case "company": out.add("pay"); break;
      case "alarms": out.add("alarm"); break;
      case "widget": out.add("widget"); break;
      case "appFeel": out.add("app"); break;
      // 1.36 [RUL-03] 수식·규칙·버튼: the app's, and the tabs its verbs touch (메모 틀·가계부).
      case "logic":
        out.add("app");
        for (const x of [...(p.buttons ?? []), ...(p.rules ?? [])]) {
          if (["memoTpl", "newMemo", "checkReset", "memoLine"].includes(x.do) || x.on === "memo" || x.where === "memo") out.add("memo");
          if (["addMoney", "addIncome", "envelope"].includes(x.do) || x.on === "money" || x.where === "money") out.add("money");
          if (x.where === "widget") out.add("widget");
        }
        break;
      case "tabView":
        if (p.cal) out.add("calendar");
        if (p.memo) out.add("memo");
        if (p.money) out.add("money");
        break;
      case "layout":
        if (p.calendar) out.add("calendar");
        if (Array.isArray(p.tabs) && p.tabs.length) out.add("app");
        break;
      case "cards":
        if (p.memo) out.add("memo");
        if (p.money) out.add("money");
        if (p.stats) out.add("pay");
        break;
      // [BLU-07] 부품 꾸미기: each line's screen by its part's first name.
      case "parts":
        for (const l of Array.isArray(p.parts) ? p.parts : []) {
          const root = String(l?.id ?? "").split(".")[0];
          const tab = { cal: "calendar", memo: "memo", money: "money", pay: "pay", app: "app", widget: "widget", night: "night" }[root];
          if (tab) out.add(tab);
        }
        break;
      case "photoLook":
        if (p.bg || p.head || p.icons) out.add("calendar");
        if (p.corners) out.add("app");
        if (p.widget) out.add("widget");
        break;
      case "screenLook":
        for (const e of String(p.screens ?? "").split("/")) {
          const key = e.split("~")[0];
          if (!key) continue;
          out.add(key.startsWith("pay.") ? "pay" : SCREEN_TAB[key] ?? "app");
        }
        if (p.pay) out.add("pay");
        break;
      // [LAY-01] 꾸밈 층: the tabs its items are pinned to.
      case "decor":
        for (const it of Array.isArray(p.items) ? p.items : []) {
          const at = String(it?.at ?? "");
          if (at.startsWith("cal.") || at === "bg.calendar") out.add("calendar");
          else if (at === "widget") out.add("widget");
          else if (at === "bg.memo") out.add("memo");
          else if (at === "bg.money") out.add("money");
          else if (at === "bg.stats" || at === "bg.pay") out.add("pay");
          else out.add("app");
        }
        break;
      default: break;
    }
  }
  return [...out].sort();
}

/** [BLU-07] A blueprint part's unit: cut at the second name (`cal.head.title` → `cal.head`), as BlueprintParts.unitOf. */
export function partUnit(id) {
  return String(id).split(".").slice(0, 2).join(".");
}

/**
 * [BLU-07] 1.36 부품 하나 (LookParts.partUnit): "part:<unit>" when the only piece is 부품 꾸미기 with every line in one unit, or one
 * tab's 판 ("cal.board", "memo.view", "money.board") when it is one tabView piece of one tab; else null. 장터 '부품' 구간 (scope `part`).
 */
export function partUnitOf(body) {
  let rec;
  try {
    rec = typeof body === "string" ? JSON.parse(body) : body;
  } catch {
    return null;
  }
  const pieces = rec?.pieces ?? [];
  if (pieces.length !== 1) return null;
  const p = pieces[0];
  if (p?.kind === "parts") {
    if (!Array.isArray(p.parts) || !p.parts.length) return null;
    const units = new Set(p.parts.map((l) => partUnit(l?.id ?? "")));
    return units.size === 1 ? "part:" + [...units][0] : null;
  }
  // 갈래 E's 판: one tab's TAB_VIEW keys.
  if (p?.kind === "tabView") {
    const keys = Object.keys(p).filter((k) => k !== "kind" && p[k] != null);
    if (!keys.length) return null;
    for (const [unit, ks] of Object.entries(VIEW_UNITS)) if (keys.every((k) => ks.includes(k))) return unit;
  }
  return null;
}

const VIEW_UNITS = { "cal.board": ["cal"], "memo.view": ["memo", "memoBy", "memoSort"], "money.board": ["money"] };

/** [MKX-01] 테마 (앱 전체를 한 벌로, 탭 둘 이상이나 앱 전체 색) or 기능별 탭 (탭 하나만); null for one that changes no tab (묶음). */
/** [RUL-07] 1.36 규칙·단추만 (LookParts.partUnit == "logic"): the only piece is the logic piece. 장터 '규칙·단추' 구간 (scope `logic`). */
export function logicOnly(body) {
  try {
    const rec = typeof body === "string" ? JSON.parse(body) : body;
    return rec?.pieces?.length === 1 && rec.pieces[0]?.kind === "logic";
  } catch {
    return false;
  }
}

export function scopeOf(tabs, body) {
  if (body !== undefined && logicOnly(body)) return "logic";
  if (!tabs.length) return null;
  return tabs.includes("app") || tabs.length >= 2 ? "theme" : "tab";
}

/**
 * 1.36 [MKX-05] 장터 네 구간 (사용자 2026-11-01 "테마는 종합, 가계부·달력·메모·근무 급여는 개별 카테고리, 그 밖의 부품·기능을 만든 것도
 * 해당 카테고리에 맞게 찾아서 스튜디오에서"): a recipe that carries only 부품 lines (A 갈래 piece "parts") is a 부품, one with
 * 수식·규칙·버튼 (D 갈래 piece "logic", with or without parts) is a 규칙·단추. Their tabs come from what they touch: a part's id
 * (cal.* → calendar, memo.* → memo, money.* → money, pay.* → pay, app.* → app, widget.* → widget), a button's place and a rule's verb.
 */
export const MARKET_SCOPES = ["theme", "tab", "part", "logic"];
const PART_TAB = { cal: "calendar", memo: "memo", money: "money", pay: "pay", stats: "pay", app: "app", widget: "widget", alarm: "alarm", night: "night" };
const LOGIC_TAB = { day: "calendar", cal: "calendar", calendar: "calendar", memo: "memo", memoTpl: "memo", newMemo: "memo", checkReset: "memo", memoLine: "memo",
  money: "money", addMoney: "money", addIncome: "money", envelope: "money", notify: "alarm", pay: "pay", stats: "pay", alarm: "alarm" };
// D 갈래: 'screen' 동작은 arg가 보낼 탭이에요.
const logicTab = (x) => (x?.do === "screen" ? LOGIC_TAB[x.arg] : null) ?? LOGIC_TAB[x?.do];

export function sectionOf(body) {
  let rec;
  try {
    rec = typeof body === "string" ? JSON.parse(body) : body;
  } catch {
    return { scope: null, tabs: [] };
  }
  const pieces = rec?.pieces ?? [];
  const kinds = new Set(pieces.map((p) => p.kind));
  const extra = [...kinds].filter((k) => k !== "parts" && k !== "logic" && k !== "tabView");
  if (!pieces.length || extra.length || !(kinds.has("parts") || kinds.has("logic") || kinds.has("tabView"))) {
    const tabs = tabsOf(rec);
    return { scope: scopeOf(tabs), tabs };
  }
  // A 갈래와 같은 규칙: 부품 작품은 parts 조각 하나에, 모든 줄이 한 단위(설계도 id 앞 두 마디, cal.head.title → cal.head) 안.
  const unit = partUnitOf(rec);
  if (!kinds.has("logic") && !unit) {
    const tabs = tabsOf(rec);
    return { scope: scopeOf(tabs), tabs };
  }
  const out = new Set();
  if (unit && !unit.startsWith("part:")) out.add({ cal: "calendar", memo: "memo", money: "money" }[unit.split(".")[0]]);
  for (const p of pieces) {
    if (p.kind === "parts") for (const l of p.parts ?? []) { const t = PART_TAB[String(l.id ?? "").split(".")[0]]; if (t) out.add(t); }
    if (p.kind === "logic") {
      for (const b of p.buttons ?? []) { const t = LOGIC_TAB[b.where] ?? logicTab(b); if (t) out.add(t); }
      for (const r of p.rules ?? []) { const t = LOGIC_TAB[r.on] ?? logicTab(r); if (t) out.add(t); }
    }
  }
  return { scope: kinds.has("logic") ? "logic" : "part", tabs: [...out].sort(), ...(kinds.has("logic") ? {} : { unit }) };
}

/**
 * 1.39 [MKC-01] 장터 분류 다시 (사용자 2026-11-07 "테마는 모든 것을 꾸민 모습, 각 탭별로 기능이나 디자인 한 제작물, 도구 또한 분류"):
 * 맨 위 세 구간 테마 · 탭별 작품 · 도구. 탭별 작품은 꾸미기(모양)·기능(틀·묶음·규칙)으로, 도구(옛 부품·규칙·단추)는 부품·자동 규칙·단추로 나눠요.
 * - LOOK_PIECES: 모양만 바꾸는 조각 (BuiltInThemes.LOOK_KINDS와 근무 꾸미기). 나머지는 기능.
 */
export const LOOK_PIECES = ["screenLook", "calView", "calTheme", "memoLook", "moneyLook", "widget", "photoLook", "layout", "cards", "parts", "decor", "tabView", "shiftLook"];
/** 도구 종류 (scope part·logic). */
export const TOOL_KINDS = ["part", "rule", "button"];
/** 탭별 작품·테마가 무엇을 바꾸는지: "look" 꾸미기만, "func" 기능만, "both" 둘 다; 조각이 없으면 null. */
export function madeOf(body) {
  let rec;
  try {
    rec = typeof body === "string" ? JSON.parse(body) : body;
  } catch {
    return null;
  }
  const kinds = (rec?.pieces ?? []).map((p) => p?.kind).filter(Boolean);
  if (!kinds.length) return null;
  const look = kinds.some((k) => LOOK_PIECES.includes(k));
  const func = kinds.some((k) => !LOOK_PIECES.includes(k));
  return look && func ? "both" : look ? "look" : "func";
}
/** 도구의 종류 (부품·자동 규칙·단추): 규칙과 단추를 함께 담으면 둘 다예요. 도구가 아니면 빈 목록. */
export function toolsOf(body, scope) {
  if (scope !== "part" && scope !== "logic") return [];
  let rec;
  try {
    rec = typeof body === "string" ? JSON.parse(body) : body;
  } catch {
    return [];
  }
  const out = new Set();
  for (const p of rec?.pieces ?? []) {
    if (p?.kind === "parts" || p?.kind === "tabView") out.add("part");
    if (p?.kind === "logic") {
      if ((p.rules ?? []).length) out.add("rule");
      if ((p.buttons ?? []).length) out.add("button");
    }
  }
  return TOOL_KINDS.filter((t) => out.has(t));
}

/**
 * 1.39 [FX-01] 프로 문 (core formula/FxPro.uses, the same list in the same order): the 1.39 기능 장치 a work's 규칙·단추 use, each needing 프로
 * to put in and to use. Empty when everything in it is free (모양, 1.38까지의 규칙·단추).
 */
export const FX_PARTS = ["values", "valueAct", "valueRead", "outside", "notifyButtons", "chain", "valueRule", "history", "time", "reach", "look"];
/** core LogicVerb.NEW_SCREENS: screens a 화면 열기 reaches from 1.39 on ([FX-21]). */
const FX_NEW_SCREENS = ["sleep", "night"];
/** core FxPro.NEW_LOOKS: 1.39 새 화면 모양 부품 (piece kind, or a prefix ending in "."), [FX-22]. */
export const FX_NEW_LOOKS = [
  // G8 FLOW-21 밤 화면·아침 한 장 (설계도 부품)
  "night", "night.",
];
const fxNewLook = (id) => typeof id === "string" && FX_NEW_LOOKS.some((k) => id === k || (k.endsWith(".") && id.startsWith(k)));
/** core FxPro.SINCE_APP: the first app (versionCode) that reads them; a work with one is kept from older apps (min_app). */
export const FX_SINCE_APP = 53;
const FX_AGAIN = ["again10", "again60"];
const FX_HISTORY = ["PAST", "STREAK", "TOTAL", "MEAN", "SINCE"];
/**
 * 사용자 2026-11-07 18:38 '하나씩 무료 맛보기' (core FxPro.TASTE): the first 값 칸 when it is a number, and the first 단추 위젯 button
 * counting it (or doing a 1.38 thing), are free; the rest of the 1.39 기능 장치 is 프로.
 */
export function fxPartsOf(body) {
  let rec;
  try {
    rec = typeof body === "string" ? JSON.parse(body) : body;
  } catch {
    return [];
  }
  const out = new Set();
  const reads = (src, text, add) => {
    const calls = fxCallKeys(src, text);
    if (calls.includes("VALUE")) add("valueRead");
    if (calls.some((c) => FX_HISTORY.includes(c))) add("history");
  };
  const add = (k) => out.add(k);
  const act = (a, taste) => {
    if (VALUE_VERBS.includes(a.do) && !(taste && a.arg === taste && (a.do === "valAdd" || a.do === "valSet"))) add("valueAct");
    if ((a.then ?? []).some((t) => !FX_AGAIN.includes(t))) add("notifyButtons");
    if (a.after !== undefined) add("time");
    if (a.do === "screen" && FX_NEW_SCREENS.includes(a.arg)) add("reach");
    if (["notify", "memoLine", "newMemo", "valText"].includes(a.do)) { reads(a.arg, true, add); reads(a.text, true, add); }
  };
  for (const p of rec?.pieces ?? []) {
    if (fxNewLook(p?.kind) || (p?.kind === "parts" && (p.parts ?? []).some((x) => fxNewLook(x?.id)))) add("look");
    if (p?.kind !== "logic") continue;
    const bs = p.buttons ?? [];
    const rs = p.rules ?? [];
    const vs = p.values ?? [];
    const taste = vs[0] && (vs[0].kind ?? "num") === "num" ? vs[0].name : null;
    if (vs.length > 1 || (vs.length === 1 && !taste)) add("values");
    const firstWidget = bs.find((b) => b.where === "widget");
    for (const b of bs) {
      if (b.where === "tile" || b.where === "shortcut" || (b.where === "widget" && b !== firstWidget)) add("outside");
      act(b, taste);
      for (const a of [...(b.more ?? []), ...(b.else ?? [])]) act(a, null);
      if ((b.more ?? []).length || (b.else ?? []).length || b.if) add("chain");
      reads(b.show, false, add);
      reads(b.if, false, add);
    }
    for (const r of rs) {
      for (const a of [r, ...(r.more ?? []), ...(r.else ?? [])]) act(a, null);
      if ((r.more ?? []).length || (r.else ?? []).length) add("chain");
      if (r.on === "value") add("valueRule");
      if (r.on === "every") add("time");
      if (r.on === "alarmOff") add("reach");
      reads(r.when, false, add);
    }
  }
  return FX_PARTS.filter((k) => out.has(k));
}

/** A list row as the app reads it (no owner, no hash of the owner). */
export function listItem(r, now) {
  const look = r.body ? lookOf(r.body) : null;
  const { scope, tabs, unit } = r.body ? sectionOf(r.body) : { scope: null, tabs: [] };
  const made = r.body && (scope === "tab" || scope === "theme") ? madeOf(r.body) : null;
  const tools = r.body ? toolsOf(r.body, scope) : [];
  const fx = r.body ? fxPartsOf(r.body) : [];
  return {
    // 1.39 [FX-01] 프로 기능 장치: the card says '프로 기능' and what.
    ...(fx.length ? { fx } : {}),
    ...(unit ? { unit } : {}),
    ...(made ? { made } : {}),
    ...(tools.length ? { tools } : {}),
    ...(look ? { look } : {}),
    // [MKX-01] Which tabs it changes and whether it is a 테마 or one tab's.
    ...(tabs.length ? { tabs } : {}),
    ...(scope ? { scope } : {}),
    code: r.code, title: r.title, summary: r.summary, styles: r.styles.split(","), cover: JSON.parse(r.cover), nick: r.nick ?? null,
    version: r.version, likes: r.likes, dislikes: r.dislikes, users: r.applied, kept: keptPercent(r.applied, r.kept7),
    official: r.official === 1, publicData: r.public_data === 1, parent: r.parent ?? null, at: new Date(r.updated_at).toISOString(),
    ...(r.showcase === 1 ? { showcase: true } : {}),
    ...(r._newMaker ? { newMaker: true } : {}),
    // [RCX-05] Who made it, as the public maker id only (never the install).
    ...(r.maker ? { maker: r.maker } : {}),
    ...(r.created_at > now - 7 * DAY ? { fresh: true } : {}),
  };
}

/**
 * [RCP-30] How far an install is trusted: 0 new (first seen < 3 days ago or nothing approved), 1 once a recipe was approved or a
 * week has passed, 2 with three approved and nothing hidden. [first] is set the first time the install comes to the 나눔 판.
 */
export async function recipeTrust(d, env, dev, now) {
  await d.run(env, "INSERT OR IGNORE INTO recipe_trust (device, first_at) VALUES (?, ?)", dev, now);
  const t = await d.one(env, "SELECT * FROM recipe_trust WHERE device = ?", dev);
  const { level, days } = trustLevel(t, now);
  return { level, days, approved: t.approved, weight: level <= 0 || days < 3 ? 0.5 : 1 };
}

/** [RCP-30] The level of a recipe_trust row (none: 0, just come), without writing anything. */
export function trustLevel(t, now) {
  if (!t) return { level: 0, days: 0, hidden: 0 };
  const days = (now - t.first_at) / DAY;
  // [RCX-28] With no 승인 any more, time on the 나눔 판 without anything hidden is what earns it.
  const level = (t.approved >= 3 || days >= 14) && t.hidden === 0 ? 2 : t.approved >= 1 || days >= 3 ? 1 : 0;
  return { level, days, hidden: t.hidden };
}

async function readDevice(d, request, body) {
  const id = d.deviceId(body?.device ?? request.headers.get("x-device"));
  return id ? d.deviceHash(id) : null;
}

const SORTS = ["good", "hot", "new", "official", "data", "showcase"];

/**
 * 1.39 [TASK-07] Parts and states an older app cannot read (its RecipeCodec refuses the whole recipe on a name it does not know), each
 * with the first versionCode that reads it. A maker's recipe that uses one is listed only to apps that new ([partsMinApp] → min_app).
 */
export const PARTS_SINCE = { ids: { "widget.task": 53, "widget.task.check": 53, "widget.task.add": 53 }, states: { late: 53, done: 53 } };

/** 1.39 [TASK-07] The versionCode a recipe's parts lines need ([PARTS_SINCE]), or null when any app reads them. */
export function partsMinApp(recipe) {
  let need = 0;
  const walk = (v) => {
    if (Array.isArray(v)) { v.forEach(walk); return; }
    if (!v || typeof v !== "object") return;
    if (typeof v.id === "string" && PARTS_SINCE.ids[v.id]) need = Math.max(need, PARTS_SINCE.ids[v.id]);
    if (typeof v.on === "string" && PARTS_SINCE.states[v.on] && typeof v.id === "string" && PART_IDS.includes(v.id)) need = Math.max(need, PARTS_SINCE.states[v.on]);
    Object.values(v).forEach(walk);
  };
  walk(recipe);
  return need || null;
}

/** 1.36 [MKS-01] The list's app= (the phone's versionCode), 0 when absent or odd. */
export function appCodeOf(url) {
  const n = Number(url.searchParams.get("app"));
  return Number.isInteger(n) && n > 0 && n < 1_000_000 ? n : 0;
}

/** 1.36 [MKS-01] An operator upload's 출시 대기 settings: {hold, releaseAt (ms|null), minApp (int|null), batch (text|null)}; null on a bad one. */
export function releaseOf(body, now) {
  const hold = body.hold === true || body.releaseAt !== undefined && body.releaseAt !== null;
  let releaseAt = null;
  if (body.releaseAt !== undefined && body.releaseAt !== null) {
    releaseAt = typeof body.releaseAt === "number" ? body.releaseAt : Date.parse(String(body.releaseAt));
    if (!Number.isFinite(releaseAt) || releaseAt < now - DAY || releaseAt > now + 366 * DAY) return null;
  }
  let minApp = null;
  if (body.minApp !== undefined && body.minApp !== null) {
    if (!Number.isInteger(body.minApp) || body.minApp < 1 || body.minApp >= 1_000_000) return null;
    minApp = body.minApp;
  }
  let batch = null;
  if (body.batch !== undefined && body.batch !== null) {
    batch = String(body.batch).trim().slice(0, 40);
    if (!/^[0-9A-Za-z가-힣._ -]{1,40}$/.test(batch)) return null;
  }
  return { hold, releaseAt, minApp, batch };
}

/** GET /api/recipes: one kind of work's plaza, a page at a time, the same for every phone (edge-cached). */
/** 1.36 [VIL-18] How long a maker's first work counts as a 새 창작자's. */
export const NEW_MAKER_DAYS = 14;

/** 1.36 [VIL-18] [list] in its order, but each block of [block] holds one [isNew] when one is left further down (moved up to its end). */
export function withNewMakers(list, isNew, block = 10) {
  const rest = [...list];
  const out = [];
  while (rest.length) {
    const take = rest.splice(0, block);
    if (take.length === block && !take.some(isNew)) {
      const i = rest.findIndex(isNew);
      if (i >= 0) {
        const [n] = rest.splice(i, 1);
        rest.unshift(take.pop());
        take.push(n);
      }
    }
    out.push(...take.map((r) => (isNew(r) ? Object.assign(r, { _newMaker: true }) : r)));
  }
  return out;
}

/** 1.38 장터 검색: a kind of work's everyday names → its style key. */
export const STYLE_WORDS = { 교대: "SHIFT", 교대근무: "SHIFT", 일근: "DAY", 일근직: "DAY", 알바: "HOURLY", 아르바이트: "HOURLY",
  현장: "FIELD", 자영업: "BUSINESS", 가게: "BUSINESS", 사장님: "BUSINESS", 투자: "INVEST" };

/** A search box's words: split on spaces, empty ones dropped, at most 4 (a whole sentence would be 30 LIKEs). */
export function searchWords(q) {
  const words = String(q ?? "").trim().split(/\s+/).filter(Boolean);
  return words.length ? words.slice(0, 4) : [];
}

async function recipeList(d, env, url) {
  const style = url.searchParams.get("style");
  const sort = SORTS.includes(url.searchParams.get("sort")) ? url.searchParams.get("sort") : "hot";
  const q = (url.searchParams.get("q") ?? "").trim().slice(0, 30);
  const cursor = Math.max(0, Math.min(600, Number(url.searchParams.get("cursor")) || 0));
  const now = Date.now();
  const where = ["r.state = 'visible'", "r.scope = 'public'"];
  const args = [];
  // 1.36 [MKS-01] An app sends its versionCode as app=; one that sends none (before 1.36) never sees a min_app recipe it cannot read.
  where.push("(r.min_app IS NULL OR r.min_app <= ?)");
  args.push(appCodeOf(url));
  if (style && ALL_STYLES.has(style)) {
    where.push("(',' || r.styles || ',') LIKE ?");
    args.push(`%,${style},%`);
  }
  if (sort === "official") where.push("r.official = 1");
  if (sort === "data") where.push("r.public_data = 1");
  // [RCX-32] 보여주기: the operator's showcases, newest first.
  if (sort === "showcase") where.push("r.showcase = 1");
  if (q) {
    // 1.38 (장터 그림 LOW, PC 확인: '교대 가계부'가 안 나와요): each word on its own (all must match, at most 4), and a kind of work's
    // name also finds the works made for that kind of work, so '교대 가계부' is a 가계부 for 교대 and not the one phrase.
    for (const w of searchWords(q)) {
      const like = `%${w.replace(/[%_]/g, "")}%`;
      const st = STYLE_WORDS[w];
      where.push(`(r.title LIKE ? OR r.summary LIKE ? OR r.cover LIKE ? OR r.title_skel LIKE ?${st ? " OR (',' || r.styles || ',') LIKE ?" : ""})`);
      args.push(like, like, like, `%${nameSkeleton(w)}%`);
      if (st) args.push(`%,${st},%`);
    }
  }
  // 좋은 순 and 요즘 뜨는 are worked out here from the counts (a few hundred rows a plaza); 새로 is by time.
  const rows0 = await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner WHERE ${where.join(" AND ")}
    ORDER BY r.updated_at DESC LIMIT 600`, ...args);
  // [FREE-08] 종류로 보기: 스킨·배치·메모장·가계부·규칙.
  // [MKX-01] 1.36 두 구간: scope=theme, or scope=tab with tab=<one of MARKET_TABS> (or none for every tab's); kind is then left aside.
  // 1.36 [MKX-05] and scope=part / scope=logic (부품·규칙·단추), each with an optional tab= of the tab it touches.
  // 1.39 [MKC-01] scope=tool: 도구 (부품과 규칙·단추를 함께), tool=part|rule|button으로 좁혀요. 탭별 작품은 made=look|func (둘 다 담은 것은 양쪽에).
  const scope0 = url.searchParams.get("scope");
  const scope = MARKET_SCOPES.includes(scope0) || scope0 === "tool" ? scope0 : null;
  const tab = scope && scope !== "theme" && [...MARKET_TABS, "app"].includes(url.searchParams.get("tab")) ? url.searchParams.get("tab") : null;
  // A 갈래 '부품 하나': scope=part&unit=cal.head.
  const unit = scope === "part" && /^(part:)?[a-z]+(\.[A-Za-z]+)?$/.test(url.searchParams.get("unit") ?? "") ? url.searchParams.get("unit") : null;
  const made = (scope === "tab" || scope === "theme") && ["look", "func"].includes(url.searchParams.get("made")) ? url.searchParams.get("made") : null;
  const tool = scope === "tool" && TOOL_KINDS.includes(url.searchParams.get("tool")) ? url.searchParams.get("tool") : null;
  const kind = !scope && RECIPE_KINDS[url.searchParams.get("kind")] ? url.searchParams.get("kind") : null;
  // 1.39 [FX-01] fx=1: only the works with a 프로 기능 장치 (the '✦ 프로 기능' chip), with any scope or none.
  // 1.39 [FX-01] fx=1 기능 작품만(✦ 프로 기능), fx=0 무료로 다 되는 것만 (장터 MKD-11).
  const fxWant = url.searchParams.get("fx");
  const rows1 = fxWant === "1" ? rows0.filter((r) => fxPartsOf(r.body).length > 0) : fxWant === "0" ? rows0.filter((r) => fxPartsOf(r.body).length === 0) : rows0;
  const rows = scope ? rows1.filter((r) => {
    const s = sectionOf(r.body);
    const inScope = scope === "tool" ? s.scope === "part" || s.scope === "logic" : s.scope === scope;
    if (!inScope) return false;
    if (tab && !(scope === "tab" ? s.tabs[0] === tab : s.tabs.includes(tab))) return false;
    if (unit && s.unit !== unit) return false;
    if (made) { const m = madeOf(r.body); if (m !== made && m !== "both") return false; }
    if (tool && !toolsOf(r.body, s.scope).includes(tool)) return false;
    return true;
  }) : kind ? rows1.filter((r) => carries(r.body, RECIPE_KINDS[kind])) : rows1;
  let scored = rows;
  if (sort === "good") {
    scored = rows.map((r) => ({ r, s: rankGood(r.wlikes, r.wdislikes, r.applied, r.kept7) })).filter((x) => x.s !== null)
      .sort((a, b) => b.s - a.s).map((x) => x.r);
  } else if (sort === "hot") {
    scored = rows.map((r) => ({ r, s: rankHot(r.wlikes, r.applied, (now - r.created_at) / HOUR) })).sort((a, b) => b.s - a.s).map((x) => x.r);
  }
  // 1.36 [VIL-18] 새 창작자 자리: in 요즘 뜨는 and 좋은 순, each ten has at least one first work of a new maker (Roblox-style cold start).
  const counts = new Map();
  for (const r of rows0) counts.set(r.owner, (counts.get(r.owner) ?? 0) + 1);
  const newMaker = (r) => r.owner !== "operator" && counts.get(r.owner) === 1 && now - r.created_at < NEW_MAKER_DAYS * DAY;
  if (sort === "hot" || sort === "good") scored = withNewMakers(scored, newMaker);
  const page = scored.slice(cursor, cursor + RECIPE_LIMITS.pageSize);
  // 1.39 [MKP-01] 장터 로딩 (사용자 2026-11-07 "장터에서 테마 로딩이 너무 느린데"): body=1 sends each work's written form and hash with its
  // card, so the app draws a page of cards from this one (edge-cached) answer instead of asking for each work alone.
  const withBody = url.searchParams.get("body") === "1";
  // 1.39 [PLZC-40] 만든 분 표식·팔로워 수(장터 카드의 만든 분 줄).
  const marks = await makerMarks(d, env, page.map((r) => r.maker), now);
  return d.json({ style: style ?? null, sort, ...(kind ? { kind } : {}), ...(scope ? { scope } : {}), ...(tab ? { tab } : {}),
    // 1.39 [PAYP-08] A work put up before 1.39 is served without a company pay part.
    items: await Promise.all(page.map(async (r) => ({ ...listItem(r, now), ...markFields(marks, r.maker),
      ...(withBody ? await sharedPayOut(r.body, r.hash) : {}) }))),
    next: cursor + RECIPE_LIMITS.pageSize < scored.length ? cursor + RECIPE_LIMITS.pageSize : null },
  200, { "cache-control": `public, max-age=${RECIPE_LIMITS.edgeSeconds}` });
}

/**
 * 1.39 판 정리 (사용자 2026-11-07 "서버에 숨긴처리 하는 로직이 있는지 확인하고 없다면 신설"): the operator's works a new 판 replaces are
 * taken off the 장터 with hidden_reason 'retired', never deleted. They leave the lists, 찾기 and 추천 (those read state 'visible'), but
 * whoever has the code (one already put in, a link) still opens it, sees its new 판 and keeps 쓰는 중 counted. A hide by the operator
 * or by reports ('admin', 'flags') still closes it to everyone but its maker.
 */
export const RETIRED = "retired";
const openable = (r) => r.state === "visible" || (r.state === "hidden" && r.hidden_reason === RETIRED);

/** GET /api/recipes/:code — the recipe for anyone with its code (link-only too), its own for the maker whatever its state. */
async function show(d, env, request, code, url) {
  const dev = await readDevice(d, request, null);
  const r = await d.one(env, "SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner WHERE r.code = ?", code);
  const mine = Boolean(r && dev && r.owner === dev);
  if (!r || r.state === "deleted" || (!mine && (!openable(r) || r.scope === "private"))) return d.json({ error: "not_found" }, 404);
  const ver = Number(url.searchParams.get("v"));
  let version = { version: r.version, hash: r.hash, body: r.body };
  if (Number.isInteger(ver) && ver > 0 && ver !== r.version) {
    const old = await d.one(env, "SELECT version, hash, body FROM recipe_versions WHERE code = ? AND version = ?", code, ver);
    if (old) version = old;
  }
  // 1.39 [PAYP-08] Someone else's: without a company pay part (the maker's own as it was sent).
  if (!mine) Object.assign(version, await sharedPayOut(version.body, version.hash));
  const myVote = dev ? await d.one(env, "SELECT value, reason FROM recipe_votes WHERE code = ? AND device = ?", code, dev) : null;
  const out = { ...listItem(r, Date.now()), ...markFields(await makerMarks(d, env, [r.maker]), r.maker), ...version, scope: r.scope, mine, ...(myVote ? { myVote: myVote.value, myReason: myVote.reason } : {}),
    // 1.39 판 정리: off the 장터, still the wearer's (the app says so and keeps what is put in).
    ...(r.state === "hidden" && r.hidden_reason === RETIRED ? { retired: true } : {}) };
  if (mine) {
    // The maker sees where it stands and every 안좋아요's reason, without who (결정 D107, 처리방침 3.8).
    const reasons = await d.all(env, "SELECT reason, opinion, at FROM recipe_votes WHERE code = ? AND value = -1 AND held = 0 ORDER BY at DESC LIMIT 100", code);
    const tally = {};
    for (const x of reasons) tally[x.reason] = (tally[x.reason] ?? 0) + 1;
    out.state = r.state;
    out.note = r.note || null;
    out.reasons = { tally, opinions: reasons.filter((x) => x.opinion).map((x) => ({ reason: x.reason, text: x.opinion, at: new Date(x.at).toISOString() })) };
    if (r.state === "hidden") out.hidden = r.hidden_reason;
    // 1.36 [GRW-08] 새싹 첫 사진·신고로 가린 사진: 만든 분께만 '사진 확인 중'.
    const photos = await waitingPhotos(env, r.body, r.cover);
    out.photosWaiting = photos.pending;
    out.photosHeld = photos.held;
  }
  return d.json(out);
}

function readScope(v) {
  return ["public", "link", "private"].includes(v) ? v : "public";
}

/** POST /api/recipes — a new recipe (RCP-29); its first public ones wait for the operator (D108). */
async function publish(d, env, request) {
  if (await d.paused(env, "PLAZA_WRITE")) return d.json({ error: "paused" }, 503);
  const body = await d.readJson(request, RECIPE.maxBytes + 2048);
  const dev = await readDevice(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.publish", now))) return d.json({ error: "not_yet", field: "recipe.publish" }, 403);
  const muted = await d.one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", dev, now);
  if (muted) return d.json({ error: "muted", until: new Date(muted.until).toISOString() }, 403);
  let clean;
  try {
    clean = await cleanRecipe(body.recipe);
  } catch (e) {
    if (e instanceof RecipeError) return d.json({ error: e.code, field: e.path }, e.code === "too_big" ? 413 : 400);
    throw e;
  }
  const trust = await recipeTrust(d, env, dev, now);
  const net = await d.network(request, env, now);
  const hit = (await writeLimitHit(d, env, dev, "recipe", now))
    ?? (await d.overLimit(env, now, await d.networkCheck(env, dev, net, "recipe", RECIPE_LIMITS.perNetworkDay, now)));
  if (hit) return hit;
  const scope = readScope(body.scope);
  // 1.39 [PAYP-08] What others can read carries no company pay part.
  if (scope !== "private") Object.assign(clean, await sharedOnly(clean));
  // 1.36 [SEC-11] Play Integrity before a write others see; a 나만 보기 recipe is the maker's own (off until INTEGRITY_MODE is set).
  if (scope === "public") { const gate = d.integrity ? await d.integrity(env, dev, now) : null; if (gate) return gate; }
  // 1.39 [FX-17] A 기능 작품 others can see needs its maker's 프로 (the app asks first; this is the same line on the server).
  if (scope !== "private" && d.hasPro && fxPartsOf(clean.text).length && !(await d.hasPro(env, dev, now))) return d.json({ error: "fx_pro", field: "recipe" }, 403);
  const r = clean.recipe;
  const skel = nameSkeleton(r.title);
  // 사칭 (S4): someone else's recipe with a name that looks the same is refused; the maker's own may share it.
  const same = await d.one(env, "SELECT code FROM recipes WHERE title_skel = ? AND owner != ? AND state IN ('visible', 'pending') LIMIT 1", skel, dev);
  if (same && scope === "public") return d.json({ error: "name_like", field: "title", code: same.code }, 409);
  if (r.parent && !(await d.one(env, `SELECT 1 AS ok FROM recipes WHERE code = ? AND (state = 'visible' OR (state = 'hidden' AND hidden_reason = '${RETIRED}'))`, r.parent))) {
    return d.json({ error: "parent", field: "parent" }, 400);
  }
  const state = scope === "public" && trust.approved < RECIPE_LIMITS.reviewFirst ? "pending" : "visible";
  let code = newCode();
  while (await d.one(env, "SELECT 1 AS x FROM recipes WHERE code = ?", code)) code = newCode();
  await d.batch(env, [
    d.stmt(env, `INSERT INTO recipes (code, owner, title, title_skel, summary, styles, cover, scope, state, parent, version, hash, body, created_at, updated_at, maker, min_app)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?)`, code, dev, r.title, skel, r.summary ?? "", r.styles.join(","), JSON.stringify(r.cover), scope,
    state, r.parent ?? null, clean.hash, clean.text, now, now, await makerId(dev),
    // 1.39 [FX-01] An app before 1.39 cannot read a 기능 장치, so it never lists the work; parts keep their own minApp (TASK-07).
    Math.max(partsMinApp(r) ?? 0, fxPartsOf(clean.text).length ? FX_SINCE_APP : 0) || null),
    d.stmt(env, "INSERT OR IGNORE INTO recipe_makers (device, maker, at) VALUES (?, ?, ?)", dev, await makerId(dev), now),
    d.stmt(env, "INSERT INTO recipe_versions (code, version, hash, body, created_at) VALUES (?, 1, ?, ?, ?)", code, clean.hash, clean.text, now),
  ]);
  return d.json({ code, version: 1, hash: clean.hash, state, scope, trust: trust.level }, 201);
}

async function owned(d, env, request, code, body) {
  const dev = await readDevice(d, request, body);
  if (!dev) return [null, d.json({ error: "device", field: "device" }, 400)];
  const r = await d.one(env, "SELECT * FROM recipes WHERE code = ? AND state != 'deleted'", code);
  if (!r) return [null, d.json({ error: "not_found" }, 404)];
  if (r.owner !== dev) return [null, d.json({ error: "not_yours" }, 403)];
  return [r, null, dev];
}

/** POST /api/recipes/:code/versions — the maker's 새 판; the code and its fields stay (Anki·HA에서 배운 것). */
async function newVersion(d, env, request, code) {
  if (await d.paused(env, "PLAZA_WRITE")) return d.json({ error: "paused" }, 503);
  const body = await d.readJson(request, RECIPE.maxBytes + 2048);
  const [r, err, dev] = await owned(d, env, request, code, body);
  if (err) return err;
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.publish", now))) return d.json({ error: "not_yet", field: "recipe.publish" }, 403);
  let clean;
  try {
    clean = await cleanRecipe(body.recipe);
  } catch (e) {
    if (e instanceof RecipeError) return d.json({ error: e.code, field: e.path }, e.code === "too_big" ? 413 : 400);
    throw e;
  }
  const scope = body.scope === undefined ? r.scope : readScope(body.scope);
  // 1.39 [PAYP-08]
  if (scope !== "private") Object.assign(clean, await sharedOnly(clean));
  // 1.36 [SEC-11] Play Integrity before a write others see (off until INTEGRITY_MODE is set).
  if (scope === "public") { const gate = d.integrity ? await d.integrity(env, dev, now) : null; if (gate) return gate; }
  // 1.39 [FX-17]
  if (scope !== "private" && d.hasPro && fxPartsOf(clean.text).length && !(await d.hasPro(env, dev, now))) return d.json({ error: "fx_pro", field: "recipe" }, 403);
  const trust = await recipeTrust(d, env, dev, now);
  // [RCX-28] Up at once like a first 판; one the operator or 신고 hid stays hidden.
  const state = r.state === "hidden" ? "hidden" : scope === "public" && trust.approved < RECIPE_LIMITS.reviewFirst ? "pending" : "visible";
  if (clean.hash === r.hash) {
    if (scope === r.scope) return d.json({ error: "same", field: "recipe" }, 409);
    // [BLU-07] PC 8차 (올린 판을 나만 보기로 바꿀 길이 없음): the same 판 put somewhere else — no new version, the code and its talk stay.
    await d.run(env, "UPDATE recipes SET scope = ?, state = ?, updated_at = ? WHERE code = ?", scope, state, now, code);
    return d.json({ code, version: r.version, hash: r.hash, state, scope });
  }
  const hit = await d.overLimit(env, now, [["device", `recipev|${dev}`, 10, DAY]]);
  if (hit) return hit;
  const v = r.version + 1;
  const rec = clean.recipe;
  await d.batch(env, [
    d.stmt(env, `UPDATE recipes SET title = ?, title_skel = ?, summary = ?, styles = ?, cover = ?, scope = ?, state = ?, version = ?, hash = ?, body = ?,
      updated_at = ?, note = '', min_app = MAX(COALESCE(min_app, 0), COALESCE(?, 0)) WHERE code = ?`, rec.title, nameSkeleton(rec.title), rec.summary ?? "", rec.styles.join(","), JSON.stringify(rec.cover), scope,
    state, v, clean.hash, clean.text, now, Math.max(partsMinApp(rec) ?? 0, fxPartsOf(clean.text).length ? FX_SINCE_APP : 0) || null, code),
    d.stmt(env, "INSERT INTO recipe_versions (code, version, hash, body, created_at) VALUES (?, ?, ?, ?, ?)", code, v, clean.hash, clean.text, now),
  ]);
  return d.json({ code, version: v, hash: clean.hash, state, scope });
}

/** DELETE /api/recipes/:code — gone from the plaza at once with its votes, counts and talk (처리방침 3.8: 지우면 삭제). */
async function remove(d, env, request, code) {
  const [, err] = await owned(d, env, request, code, null);
  if (err) return err;
  await d.batch(env, dropRecipe(d, env, code));
  return d.json({ code, deleted: true });
}

export function dropRecipe(d, env, code) {
  return [
    d.stmt(env, "DELETE FROM recipes WHERE code = ?", code),
    d.stmt(env, "DELETE FROM recipe_versions WHERE code = ?", code),
    d.stmt(env, "DELETE FROM recipe_votes WHERE code = ?", code),
    d.stmt(env, "DELETE FROM recipe_counts WHERE code = ?", code),
    d.stmt(env, "DELETE FROM recipe_flags WHERE code = ?", code),
    d.stmt(env, "DELETE FROM post_votes WHERE post_id IN (SELECT id FROM posts WHERE company_id = 'recipe' AND grp = ?)", code),
    d.stmt(env, "DELETE FROM post_flags WHERE post_id IN (SELECT id FROM posts WHERE company_id = 'recipe' AND grp = ?)", code),
    d.stmt(env, "DELETE FROM posts WHERE company_id = 'recipe' AND grp = ?", code),
  ];
}

/** A 안좋아요's reason: one of [DISLIKE_REASONS] and 5~120 letters, the same filter as everything else (결정 D107). */
export function readReason(body) {
  if (typeof body.reason !== "string" || !Object.hasOwn(DISLIKE_REASONS, body.reason)) return { error: "reason" };
  if (typeof body.opinion !== "string") return { error: "opinion" };
  const t = cleanText(body.opinion, true, 4);
  const n = graphemes(t);
  if (n < RECIPE_LIMITS.opinion[0] || n > RECIPE_LIMITS.opinion[1]) return { error: "opinion" };
  const why = textProblem(t);
  if (why) return { error: why };
  return { reason: body.reason, opinion: t };
}

/** POST /api/recipes/:code/vote — 좋아요 (1), 안좋아요 (-1, with its reason) or taking it back (null). */
async function recipeVote(d, env, request, code) {
  const body = await d.readJson(request, 2048);
  const dev = await readDevice(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  if (body.value !== null && body.value !== 1 && body.value !== -1) return d.json({ error: "value", field: "value" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.vote", now))) return d.json({ error: "not_yet", field: "recipe.vote" }, 403);
  const r = await d.one(env, "SELECT code, owner, state, scope FROM recipes WHERE code = ?", code);
  if (!r || r.state !== "visible") return d.json({ error: "not_found" }, 404);
  if (r.owner === dev) return d.json({ error: "own" }, 400);
  let reason = null;
  if (body.value === -1) {
    reason = readReason(body);
    if (reason.error) return d.json({ error: reason.error, field: reason.error === "reason" ? "reason" : "opinion" }, 400);
  }
  // 1.39 [CARE-02] 마을 쉬기 중에는 좋아요·안좋아요를 쉬고, 의견 쉬기 중에는 안좋아요에 붙이는 의견 글을 쉬어요(거두기는 언제나 돼요).
  if (body.value !== null) {
    const held = await careGate(d, env, dev, reason?.opinion ? "opinion" : "community", now);
    if (held) return held;
  }
  const net = await d.network(request, env, now);
  const hit = await d.overLimit(env, now, [["device", `rvote|${dev}|${Math.floor(now / HOUR)}`, RECIPE_LIMITS.votesPerHour, HOUR]]);
  if (hit) return hit;
  const trust = await recipeTrust(d, env, dev, now);
  // S8: a burst from one network waits; it counts nowhere until the operator lets it through.
  const burst = await d.allow(env, `rvotenet|${net.now}`, RECIPE_LIMITS.networkVotesHour, HOUR, now);
  const held = burst ? 0 : 1;
  const before = await d.one(env, "SELECT value, weight, held FROM recipe_votes WHERE code = ? AND device = ?", code, dev);
  const stmts = [];
  if (before && !before.held) {
    stmts.push(d.stmt(env, before.value === 1 ? "UPDATE recipes SET likes = likes - 1, wlikes = wlikes - ? WHERE code = ?"
      : "UPDATE recipes SET dislikes = dislikes - 1, wdislikes = wdislikes - ? WHERE code = ?", before.weight, code));
  }
  if (body.value === null) {
    stmts.push(d.stmt(env, "DELETE FROM recipe_votes WHERE code = ? AND device = ?", code, dev));
  } else {
    stmts.push(d.stmt(env, `INSERT INTO recipe_votes (code, device, value, weight, reason, opinion, held, net, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (code, device) DO UPDATE SET value = excluded.value, weight = excluded.weight, reason = excluded.reason, opinion = excluded.opinion,
        held = excluded.held, net = excluded.net, at = excluded.at`, code, dev, body.value, trust.weight, reason?.reason ?? null, reason?.opinion ?? null,
    held, net.now, now));
    if (!held) {
      stmts.push(d.stmt(env, body.value === 1 ? "UPDATE recipes SET likes = likes + 1, wlikes = wlikes + ? WHERE code = ?"
        : "UPDATE recipes SET dislikes = dislikes + 1, wdislikes = wdislikes + ? WHERE code = ?", trust.weight, code));
    }
  }
  await d.batch(env, stmts);
  const after = await d.one(env, "SELECT likes, dislikes FROM recipes WHERE code = ?", code);
  return d.json({ likes: after.likes, dislikes: after.dislikes, myVote: body.value, ...(held ? { held: true } : {}) });
}

/** POST /api/recipes/:code/applied — {kind: "applied" | "kept"}: a day's total, nothing about the phone (S8, 처리방침 3.8). */
async function applied(d, env, request, code) {
  const body = await d.readJson(request, 512);
  if (body.kind !== "applied" && body.kind !== "kept") return d.json({ error: "kind", field: "kind" }, 400);
  const now = Date.now();
  const net = await d.network(request, env, now);
  if (!(await d.allow(env, `rapplied|${net.day}`, RECIPE_LIMITS.appliedPerNetworkDay, DAY, now))) return d.tooMany("network", DAY - (now % DAY));
  const r = await d.one(env, `SELECT code FROM recipes WHERE code = ? AND (state = 'visible' OR (state = 'hidden' AND hidden_reason = '${RETIRED}'))`, code);
  if (!r) return d.json({ error: "not_found" }, 404);
  const col = body.kind === "applied" ? "applied" : "kept7";
  // 1.36 보안 점검 M9 (PC 확인 7525ccff "넣었다 되돌려도 쓰는 분이 늘어요"): 쓰는 분 and 7일 유지 weigh in 좋은 순 and 요즘 뜨는, and
  // they come with no phone (S8), so one line could push a recipe up 200 a day. A few a recipe a line (a family's phones), then
  // answered as kept but not counted, so the app has nothing to try again.
  if (!(await d.allow(env, `rappliedone|${net.day}|${code}|${col}`, RECIPE_LIMITS.appliedPerRecipeNetworkDay, DAY, now))) {
    return d.json({ ok: true, counted: false });
  }
  await d.batch(env, [
    d.stmt(env, `INSERT INTO recipe_counts (code, day, ${col}) VALUES (?, ?, 1) ON CONFLICT (code, day) DO UPDATE SET ${col} = ${col} + 1`, code, kstDayOf(now)),
    d.stmt(env, `UPDATE recipes SET ${col} = ${col} + 1 WHERE code = ?`, code),
  ]);
  return d.json({ ok: true });
}

/** POST /api/recipes/:code/flag — 신고; three hide it until the operator looks (임시 조치, 30일 안 판단). */
async function recipeFlag(d, env, request, code) {
  const body = await d.readJson(request, 1024);
  const dev = await readDevice(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const reason = RECIPE_FLAG_REASONS.includes(body.reason) ? body.reason : "other";
  const now = Date.now();
  const hit = await d.overLimit(env, now, [["device", `rflag|${dev}`, 20, DAY]]);
  if (hit) return hit;
  // 1.36 [SEC-11] Play Integrity before a write others see (off until INTEGRITY_MODE is set).
  { const gate = d.integrity ? await d.integrity(env, dev, now) : null; if (gate) return gate; }
  const r = await d.one(env, "SELECT code, owner, state, flags, body, cover FROM recipes WHERE code = ?", code);
  if (!r || r.state !== "visible") return d.json({ error: "not_found" }, 404);
  if (r.owner === dev) return d.json({ error: "own" }, 400);
  const res = await d.run(env, "INSERT OR IGNORE INTO recipe_flags (code, device, reason, at) VALUES (?, ?, ?, ?)", code, dev, reason, now);
  if (!res?.meta?.changes) return d.json({ flagged: true, already: true });
  // 1.36 [SEC-05] 보안 점검 M3: one network counts once a day for one recipe, so ids made on one line cannot hide others' work.
  // [SEC-16] M10 (PC 확인 F 13차): and a 새싹 install's report (under 3 days, nothing approved) is kept for the operator but does not
  // count, as for pictures (SEC-08) and fonts (SEC-10), so new ids on new lines cannot hide it either. [counts] is what a recipe's
  // report passes on to its pictures (F).
  await d.run(env, "INSERT OR IGNORE INTO recipe_trust (device, first_at) VALUES (?, ?)", dev, now);
  const trust = trustLevel(await d.one(env, "SELECT * FROM recipe_trust WHERE device = ?", dev), now);
  const net = await d.network(request, env, now);
  const why = trust.level < 1 ? "new" : (await d.allow(env, `rflagnet|${net.day}|${code}`, 1, DAY, now)) ? null : "net";
  const counts = why === null;
  // F 1.36 [GRW-08] (with F): a recipe's report is its pictures' report too, counted as the recipe's is.
  if (d.flagRecipeImages) await d.flagRecipeImages(env, r.body, r.cover, dev, reason, now, counts);
  if (!counts) {
    await d.run(env, "UPDATE recipe_flags SET counted = 0, why = ? WHERE code = ? AND device = ?", why, code, dev);
    return d.json({ flagged: true });
  }
  const flags = r.flags + 1;
  const hide = flags >= RECIPE_LIMITS.flagsToHide;
  await d.batch(env, [
    d.stmt(env, `UPDATE recipes SET flags = ?${hide ? ", state = 'hidden', hidden_reason = 'flags', hidden_at = ?" : ""} WHERE code = ?`,
      ...(hide ? [flags, now, code] : [flags, code])),
    ...(hide ? [d.stmt(env, "INSERT INTO recipe_actions (code, action, reason, actor, at) VALUES (?, 'hide', 'flags', '', ?)", code, now),
      d.stmt(env, "UPDATE recipe_trust SET hidden = hidden + 1 WHERE device = ?", r.owner)] : []),
  ]);
  if (hide) await addStrike(d, env, r.owner, "recipe", code, now);
  return d.json({ flagged: true, hidden: hide });
}

/** GET /api/recipes/mine — this install's recipes in every state, with the reasons of their 안좋아요 counted. */
async function mine(d, env, request) {
  const dev = await readDevice(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const rows = await d.all(env, "SELECT * FROM recipes WHERE owner = ? AND state != 'deleted' ORDER BY updated_at DESC LIMIT 100", dev);
  const trust = await recipeTrust(d, env, dev, now);
  const lim = await writeLeft(d, env, dev, "recipe", now);
  return d.json({ trust: trust.level, leftToday: lim.limit ? lim.limit.left : null, ...lim, reviewLeft: Math.max(0, RECIPE_LIMITS.reviewFirst - trust.approved),
    items: await Promise.all(rows.map(async (r) => {
      // 1.36 [GRW-08] PC 6ec395bf: 목록 카드에도 '사진 확인 중'·'사진이 신고로 잠시 가려졌어요'.
      const p = await waitingPhotos(env, r.body, r.cover);
      return { ...listItem(r, now), state: r.state, scope: r.scope, note: r.note || null, hash: r.hash, photos: p.held ? "held" : p.pending ? "pending" : null };
    })) });
}

// ---- a recipe's talk (RCP-35) ----

const talkGrp = (code) => code;

/** GET /api/recipes/:code/talk — oldest first, with 만든 사람 and 쓰는 중 N일 marks; a 안좋아요's reasons only on one's own post. */
async function recipeTalk(d, env, request, code) {
  const dev = await readDevice(d, request, null);
  const r = await d.one(env, "SELECT code, owner, state, scope, hidden_reason FROM recipes WHERE code = ?", code);
  if (!r || (!openable(r) && r.owner !== dev)) return d.json({ error: "not_found" }, 404);
  const now = Date.now();
  const [rows, nicks, myVotes, muted] = await Promise.all([
    d.all(env, "SELECT * FROM posts WHERE company_id = ? AND grp = ? ORDER BY created_at, rowid LIMIT 300", RECIPE_TABLE, talkGrp(code)),
    d.all(env, "SELECT device, nick FROM nicks WHERE device IN (SELECT device FROM posts WHERE company_id = ? AND grp = ?)", RECIPE_TABLE, talkGrp(code)),
    dev ? d.all(env, `SELECT v.post_id, v.ok FROM post_votes v JOIN posts p ON p.id = v.post_id WHERE v.device = ? AND p.company_id = ? AND p.grp = ?`,
      dev, RECIPE_TABLE, talkGrp(code)) : [],
    dev ? d.one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", dev, now) : null,
  ]);
  const nick = new Map(nicks.map((n) => [n.device, n.nick]));
  const voted = new Map(myVotes.map((v) => [v.post_id, v.ok === 1 ? 1 : -1]));
  const hidden = await d.hiddenAuthors(env, request);
  // [PRF-05] 1.36 차단한 분의 이야기도 빼요 (recipe_blocks holds public maker ids; a post's writer is matched by the same one-way id).
  const blocked = dev ? new Set((await d.all(env, "SELECT maker FROM recipe_blocks WHERE device = ?", dev)).map((b) => b.maker)) : new Set();
  if (blocked.size) for (const p of rows) if (blocked.has(await makerId(p.device))) hidden.add(p.device);
  const key = await d.authorKey(env);
  const numbers = new Map();
  const mineIds = rows.filter((p) => p.device === dev).map((p) => p.id);
  const reasons = mineIds.length ? await d.all(env, `SELECT post_id, why, note FROM post_votes WHERE ok = 0 AND post_id IN (${mineIds.map(() => "?").join(",")})`, ...mineIds) : [];
  const posts = [];
  const dropped = new Set();
  for (const p of rows) {
    if (!numbers.has(p.device)) numbers.set(p.device, numbers.size + 1);
    const own = p.device === dev;
    if (p.status !== "shown" && !own) {
      dropped.add(p.id);
      continue;
    }
    if (!own && (hidden.has(p.device) || (p.parent && dropped.has(p.parent)))) {
      dropped.add(p.id);
      continue;
    }
    posts.push({
      id: p.id, who: numbers.get(p.device), nick: nick.get(p.device) ?? null, body: p.body, at: new Date(p.created_at).toISOString(),
      kind: p.kind, parent: p.parent ?? null, mine: own, maker: p.device === r.owner, usingDays: p.using_days ?? null,
      likes: p.agree ?? 0, dislikes: p.disagree ?? 0, ...(voted.has(p.id) ? { myVote: voted.get(p.id) } : {}),
      ...(own ? { reasons: reasons.filter((x) => x.post_id === p.id).map((x) => ({ reason: x.why, text: x.note })) } : { author: await d.authorToken(key, p.device) }),
      ...(p.status === "shown" ? {} : { hidden: true }),
    });
  }
  // 1.36 [LIM-10] How many more today only while strikes restrict this install; otherwise nothing, there is no cap.
  const used = dev ? await writeLeft(d, env, dev, "rtalk", now) : {};
  return d.json({ code, posts, ...(muted ? { mutedUntil: new Date(muted.until).toISOString() } : {}),
    ...(used.limit ? { left: used.limit.left, limit: used.limit } : {}) });
}

/** POST /api/recipes/:code/talk — {body, kind, parent?, usingDays?}. */
async function talkPost(d, env, request, code) {
  if (await d.paused(env, "PLAZA_WRITE")) return d.json({ error: "paused" }, 503);
  const body = await d.readJson(request, 4096);
  const dev = await readDevice(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.talk", now))) return d.json({ error: "not_yet", field: "recipe.talk" }, 403);
  const r = await d.one(env, "SELECT code, owner, state, styles FROM recipes WHERE code = ?", code);
  if (!r || (r.state !== "visible" && r.owner !== dev)) return d.json({ error: "not_found" }, 404);
  if (typeof body.body !== "string") return d.json({ error: "body", field: "body" }, 400);
  const t = cleanText(body.body, true, 20);
  if (graphemes(t) < 2 || graphemes(t) > RECIPE_LIMITS.talk) return d.json({ error: "body", field: "body" }, 400);
  const why = textProblem(t) ?? (r.styles.split(",").includes("INVEST") && investAdvice(t) ? "invest_advice" : null);
  if (why) return d.json({ error: why, field: "body" }, 400);
  const muted = await d.one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", dev, now);
  if (muted) return d.json({ error: "muted", until: new Date(muted.until).toISOString() }, 403);
  // 1.39 [CARE-02] 의견 쉬기·마을 쉬기 중이면 글을 쉬어 가요.
  const held = (await careGate(d, env, dev, "opinion", now)) ?? (await careRepeat(d, env, dev, t, now));
  if (held) return held;
  const net = await d.network(request, env, now);
  const hit = (await writeLimitHit(d, env, dev, "rtalk", now)) ?? (await d.overLimit(env, now, await d.networkCheck(env, dev, net, "rtalk", 300, now)));
  if (hit) return hit;
  let parent = null;
  if (body.parent !== undefined && body.parent !== null) {
    const top = typeof body.parent === "string" ? await d.one(env, "SELECT id, parent FROM posts WHERE id = ? AND company_id = ? AND grp = ?",
      body.parent, RECIPE_TABLE, talkGrp(code)) : null;
    if (!top) return d.json({ error: "parent", field: "parent" }, 400);
    parent = top.parent ?? top.id;
  }
  const kind = TALK_KINDS.includes(body.kind) ? body.kind : "opinion";
  const using = Number.isInteger(body.usingDays) && body.usingDays >= 0 && body.usingDays <= 3650 ? body.usingDays : null;
  const id = `p${d.randomId(15)}`;
  await d.run(env, `INSERT INTO posts (id, company_id, grp, device, body, status, flags, kept, hide_reason, created_at, hidden_at, parent, kind, change,
    report_id, agree, disagree, using_days) VALUES (?, ?, ?, ?, ?, 'shown', 0, 0, NULL, ?, NULL, ?, ?, NULL, NULL, 0, 0, ?)`,
  id, RECIPE_TABLE, talkGrp(code), dev, t, now, parent, kind, using);
  return d.json({ id, at: new Date(now).toISOString() }, 201);
}

/** POST /api/recipes/talk/:post/vote — 좋아요·안좋아요 on a talk post; a 안좋아요 carries its reason to the writer only. */
async function talkVote(d, env, request, postId) {
  const body = await d.readJson(request, 2048);
  const dev = await readDevice(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  if (body.value !== null && body.value !== 1 && body.value !== -1) return d.json({ error: "value", field: "value" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.vote", now))) return d.json({ error: "not_yet", field: "recipe.vote" }, 403);
  const p = await d.one(env, "SELECT * FROM posts WHERE id = ? AND company_id = ?", postId, RECIPE_TABLE);
  if (!p || p.status !== "shown") return d.json({ error: "not_found" }, 404);
  if (p.device === dev) return d.json({ error: "own" }, 400);
  let reason = null;
  if (body.value === -1) {
    reason = readReason(body);
    if (reason.error) return d.json({ error: reason.error, field: reason.error === "reason" ? "reason" : "opinion" }, 400);
  }
  // 1.39 [CARE-02] 작품 좋아요와 같이(이야기 좋아요·안좋아요).
  if (body.value !== null) {
    const held = await careGate(d, env, dev, reason?.opinion ? "opinion" : "community", now);
    if (held) return held;
  }
  const hit = await d.overLimit(env, now, [["device", `rvote|${dev}|${Math.floor(now / HOUR)}`, RECIPE_LIMITS.votesPerHour, HOUR]]);
  if (hit) return hit;
  const before = await d.one(env, "SELECT ok FROM post_votes WHERE post_id = ? AND device = ?", postId, dev);
  const stmts = [];
  if (before) stmts.push(d.stmt(env, `UPDATE posts SET ${before.ok ? "agree = agree - 1" : "disagree = disagree - 1"} WHERE id = ?`, postId));
  if (body.value === null) stmts.push(d.stmt(env, "DELETE FROM post_votes WHERE post_id = ? AND device = ?", postId, dev));
  else {
    stmts.push(d.stmt(env, `INSERT INTO post_votes (post_id, device, ok, at, why, note) VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT (post_id, device) DO UPDATE SET ok = excluded.ok, at = excluded.at, why = excluded.why, note = excluded.note`,
    postId, dev, body.value === 1 ? 1 : 0, now, reason?.reason ?? null, reason?.opinion ?? null));
    stmts.push(d.stmt(env, `UPDATE posts SET ${body.value === 1 ? "agree = agree + 1" : "disagree = disagree + 1"} WHERE id = ?`, postId));
  }
  await d.batch(env, stmts);
  const after = await d.one(env, "SELECT agree, disagree FROM posts WHERE id = ?", postId);
  return d.json({ likes: after.agree, dislikes: after.disagree, myVote: body.value });
}

/** The 나눔 판's public routes, or null for a path that is not one of them. */
export async function recipeRoute(d, request, env, path, url, ctx) {
  const method = request.method;
  if (path === "/api/recipes" && method === "GET") return d.edgeCached(request, ctx, RECIPE_LIMITS.edgeSeconds, () => recipeList(d, env, url));
  if (path === "/api/recipes" && method === "POST") return publish(d, env, request);
  if (path === "/api/recipes/mine" && method === "GET") return mine(d, env, request);
  let m = path.match(/^\/api\/recipes\/talk\/(p[A-Za-z0-9]{15})\/vote$/);
  if (m && method === "POST") return talkVote(d, env, request, m[1]);
  m = path.match(/^\/api\/recipes\/([2-9A-HJ-NP-Z]{8})(\/[a-z]+)?$/);
  if (!m) return null;
  const [, code, sub] = m;
  // 1.39 [MKP-01] A work read with no phone named (a 장터 card's picture, the web page) is the same for everyone: kept a minute at the edge.
  if (!sub && method === "GET" && !request.headers.get("x-device")) {
    return d.edgeCached(request, ctx, RECIPE_LIMITS.edgeSeconds, async () => {
      const res = await show(d, env, request, code, url);
      if (res.status !== 200) return res;
      const headers = new Headers(res.headers);
      headers.set("cache-control", `public, max-age=${RECIPE_LIMITS.edgeSeconds}`);
      return new Response(res.body, { status: 200, headers });
    });
  }
  if (!sub && method === "GET") return show(d, env, request, code, url);
  if (!sub && method === "DELETE") return remove(d, env, request, code);
  if (sub === "/versions" && method === "POST") return newVersion(d, env, request, code);
  if (sub === "/vote" && method === "POST") return recipeVote(d, env, request, code);
  if (sub === "/applied" && method === "POST") return applied(d, env, request, code);
  if (sub === "/flag" && method === "POST") return recipeFlag(d, env, request, code);
  if (sub === "/talk" && method === "GET") return recipeTalk(d, env, request, code);
  if (sub === "/talk" && method === "POST") return talkPost(d, env, request, code);
  return null;
}

// ---- the operator's tools (RCP-36) ----

/**
 * /api/admin/recipes…: 검토 대기 (oldest first; past 24 hours marked late), 신고된 것, 임시 조치 기록, 월간 처리 수치; approving,
 * returning with a note, hiding, showing, deleting, the 공식 mark, and the operator's own (공개 자료) recipes.
 */
export async function recipeAdmin(d, request, env, path, url, now, actorName) {
  const method = request.method;
  const act = (code, action, reason = "") => d.stmt(env, "INSERT INTO recipe_actions (code, action, reason, actor, at) VALUES (?, ?, ?, ?, ?)",
    code, action, String(reason).slice(0, 200), actorName, now);
  if (path === "/recipes" && method === "GET") {
    const tab = url.searchParams.get("tab") ?? "queue";
    // 1.36 [GRW-12] PC 20ab0254: 사진 검토·고친 판에서 '작품 열기' — 작품 하나만 (지운 것은 빼고).
    const code = (url.searchParams.get("code") ?? "").toUpperCase();
    if (tab === "one" && !/^[2-9A-HJ-NP-Z]{8}$/.test(code)) return d.json({ error: "code", field: "code" }, 400);
    // 1.36 [SEC-16] M10: 신고된 also lists a recipe whose reports all went to the operator only (none counted toward hiding it).
    const where = { queue: "state = 'pending'", flagged: "(flags > 0 OR EXISTS (SELECT 1 FROM recipe_flags f WHERE f.code = recipes.code)) AND state != 'deleted'",
      hidden: `state = 'hidden' AND COALESCE(hidden_reason, '') != '${RETIRED}'`, retired: `state = 'hidden' AND hidden_reason = '${RETIRED}'`, visible: "state = 'visible'", official: "official = 1", showcase: "showcase = 1",
      one: "code = ? AND state != 'deleted'", waiting: "state = 'waiting'" }[tab]
      ?? "state = 'pending'";
    // 1.36 클라우드 관리자 앱 확인 (출시 작품 267개 중 200개만 보이고 구간 수가 틀려요): 출시 대기 lists the whole batch.
    const rows = await d.all(env, `SELECT * FROM recipes WHERE ${where} ORDER BY ${tab === "queue" ? "updated_at ASC" : "updated_at DESC"} LIMIT ${tab === "waiting" || tab === "retired" ? 1200 : 200}`, ...(tab === "one" ? [code] : []));
    // D1 takes at most 100 bound values in one query, so the codes go in groups (a list of 200 failed on the real server).
    const byCodes = async (sql) => {
      const out = [];
      for (let i = 0; i < rows.length; i += 90) {
        const part = rows.slice(i, i + 90).map((r) => r.code);
        out.push(...await d.all(env, sql(part.map(() => "?").join(",")), ...part));
      }
      return out;
    };
    // PC 확인 보안 6차: how many of each reason counted toward hiding it too (the operator's own were shown as if they did).
    const flags = await byCodes((marks) => `SELECT code, reason, COUNT(*) AS n, SUM(counted) AS counted FROM recipe_flags WHERE code IN (${marks})
      GROUP BY code, reason`);
    const quiet = await byCodes((marks) => `SELECT code, why, COUNT(*) AS n FROM recipe_flags WHERE counted = 0 AND code IN (${marks})
      GROUP BY code, why`);
    const quietOf = (code) => {
      const q = quiet.filter((x) => x.code === code);
      const n = (why) => q.find((x) => x.why === why)?.n ?? 0;
      return q.length ? { fresh: n("new"), net: n("net") } : null;
    };
    return d.json({ tab, ...(tab === "one" ? { code } : {}), items: rows.map((r) => ({ ...listItem(r, now), section: sectionOf(r.body).scope, state: r.state, scope: r.scope, body: r.body, hash: r.hash, note: r.note,
      ...(r.release_at ? { releaseAt: new Date(r.release_at).toISOString() } : {}), ...(r.min_app ? { minApp: r.min_app } : {}), ...(r.batch ? { batch: r.batch } : {}),
      late: r.state === "pending" && now - r.updated_at > DAY, flags: flags.filter((f) => f.code === r.code).map((f) => ({ reason: f.reason, n: f.n, counted: f.counted ?? f.n })),
      quiet: quietOf(r.code), hidden: r.hidden_reason ?? null })) });
  }
  if (path === "/recipes/actions" && method === "GET") {
    const rows = await d.all(env, "SELECT * FROM recipe_actions ORDER BY id DESC LIMIT 300");
    return d.json({ items: rows.map((a) => ({ ...a, at: new Date(a.at).toISOString() })) });
  }
  if (path === "/recipes/month" && method === "GET") {
    const month = /^\d{4}-\d{2}$/.test(url.searchParams.get("month") ?? "") ? url.searchParams.get("month") : new Date(now + 9 * HOUR).toISOString().slice(0, 7);
    const from = Date.parse(`${month}-01T00:00:00+09:00`);
    const to = new Date(from);
    to.setUTCMonth(to.getUTCMonth() + 1);
    const rows = await d.all(env, "SELECT action, COUNT(*) AS n FROM recipe_actions WHERE at >= ? AND at < ? GROUP BY action", from, to.getTime());
    const made = await d.one(env, "SELECT COUNT(*) AS n FROM recipes WHERE created_at >= ? AND created_at < ?", from, to.getTime());
    return d.json({ month, made: made.n, actions: Object.fromEntries(rows.map((r) => [r.action, r.n])) });
  }
  if (path === "/recipes" && method === "POST") {
    // The operator's own: 공개 자료 or a 시작 레시피, 공식 marked, never waiting for 검토.
    // 1.36 [MKS-01] hold/releaseAt put it in 출시 대기 instead of on the 장터; minApp keeps it from apps that cannot read it;
    // replace: true takes the operator's recipe of the same name (a 출시 묶음 put in again after its format moved) to a new 판.
    const body = await d.readJson(request, RECIPE.maxBytes + 2048);
    let clean;
    try {
      clean = await cleanRecipe(body.recipe);
    } catch (e) {
      if (e instanceof RecipeError) return d.json({ error: e.code, field: e.path }, 400);
      throw e;
    }
    const r = clean.recipe;
    const rel = releaseOf(body, now);
    if (!rel) return d.json({ error: "release", field: "releaseAt" }, 400);
    const state = rel.hold ? "waiting" : "visible";
    const same = body.replace === true ? await d.one(env, `SELECT code, version, hash, state FROM recipes WHERE owner = 'operator' AND title_skel = ?
      AND state != 'deleted' ORDER BY created_at DESC LIMIT 1`, nameSkeleton(r.title)) : null;
    if (same) {
      if (same.hash === clean.hash && (same.state === state || same.state === "visible")) {
        await d.run(env, "UPDATE recipes SET release_at = ?, min_app = ?, batch = ? WHERE code = ?", rel.releaseAt, rel.minApp, rel.batch, same.code);
        return d.json({ code: same.code, hash: clean.hash, version: same.version, state: same.state, same: true });
      }
      const v = same.version + 1;
      // A 판 already on the 장터 stays there; one still waiting takes the new settings.
      const next = same.state === "visible" ? "visible" : state;
      await d.batch(env, [
        d.stmt(env, `UPDATE recipes SET title = ?, title_skel = ?, summary = ?, styles = ?, cover = ?, state = ?, public_data = ?, showcase = ?, version = ?,
          hash = ?, body = ?, updated_at = ?, release_at = ?, min_app = ?, batch = ? WHERE code = ?`, r.title, nameSkeleton(r.title), r.summary ?? "",
        r.styles.join(","), JSON.stringify(r.cover), next, body.publicData === true ? 1 : 0, body.showcase === true ? 1 : 0, v, clean.hash, clean.text, now,
        rel.releaseAt, rel.minApp, rel.batch, same.code),
        d.stmt(env, "INSERT INTO recipe_versions (code, version, hash, body, created_at) VALUES (?, ?, ?, ?, ?)", same.code, v, clean.hash, clean.text, now),
        act(same.code, "official", `새 판 ${v}${rel.batch ? ` · ${rel.batch}` : ""}`),
      ]);
      return d.json({ code: same.code, hash: clean.hash, version: v, state: next });
    }
    let code = newCode();
    while (await d.one(env, "SELECT 1 AS x FROM recipes WHERE code = ?", code)) code = newCode();
    await d.batch(env, [
      d.stmt(env, `INSERT INTO recipes (code, owner, title, title_skel, summary, styles, cover, scope, state, official, public_data, showcase, version, hash, body, created_at, updated_at, decided_at,
        release_at, min_app, batch)
        VALUES (?, 'operator', ?, ?, ?, ?, ?, 'public', ?, 1, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)`, code, r.title, nameSkeleton(r.title), r.summary ?? "",
      r.styles.join(","), JSON.stringify(r.cover), state, body.publicData === true ? 1 : 0, body.showcase === true ? 1 : 0, clean.hash, clean.text, now, now, now,
      rel.releaseAt, rel.minApp, rel.batch),
      d.stmt(env, "INSERT INTO recipe_versions (code, version, hash, body, created_at) VALUES (?, 1, ?, ?, ?)", code, clean.hash, clean.text, now),
      act(code, state === "waiting" ? "waiting" : "official", [body.publicData === true ? "공개 자료" : body.showcase === true ? "보여주기" : "시작 레시피", rel.batch].filter(Boolean).join(" · ")),
    ]);
    return d.json({ code, hash: clean.hash, state }, 201);
  }
  // 1.36 [MKS-01] 출시 대기 묶음: how many wait in each, and 지금 공개 / 예약 (at) / 예약 풀기 (at: null) for one batch or every waiting one.
  if (path === "/recipes/release" && method === "GET") {
    const rows = await d.all(env, `SELECT COALESCE(batch, '') AS batch, COUNT(*) AS n, MIN(release_at) AS at, MAX(min_app) AS minApp FROM recipes
      WHERE state = 'waiting' GROUP BY COALESCE(batch, '') ORDER BY batch`);
    return d.json({ items: rows.map((x) => ({ batch: x.batch || null, n: x.n, releaseAt: x.at ? new Date(x.at).toISOString() : null, minApp: x.minApp ?? null })) });
  }
  if (path === "/recipes/release" && method === "POST") {
    const body = await d.readJson(request, 1024);
    const one = body.batch === undefined || body.batch === null ? null : String(body.batch);
    const where = one === null ? "state = 'waiting'" : "state = 'waiting' AND COALESCE(batch, '') = ?";
    const args = one === null ? [] : [one];
    if (body.at !== undefined) {
      const at = body.at === null ? null : Date.parse(String(body.at));
      if (at !== null && (!Number.isFinite(at) || at < now || at > now + 366 * DAY)) return d.json({ error: "release", field: "at" }, 400);
      const res = await d.run(env, `UPDATE recipes SET release_at = ? WHERE ${where}`, at, ...args);
      await d.run(env, "INSERT INTO recipe_actions (code, action, reason, actor, at) VALUES ('-', 'schedule', ?, ?, ?)",
        `${one ?? "모두"} · ${at ? new Date(at).toISOString() : "예약 풀기"}`, actorName, now);
      return d.json({ scheduled: res.meta?.changes ?? 0, at: at ? new Date(at).toISOString() : null });
    }
    const n = await releaseWaiting(d, env, now, where, args, actorName);
    return d.json({ released: n });
  }
  // 1.38 [STM-09] 판이 바뀌며 내리는 운영자 작품을 제목(이나 코드)으로 한 번에 숨겨요. 지우지 않아요.
  // 1.39 판 정리: 이유는 'retired'라 이미 넣은 분은 코드로 계속 열고(목록·찾기·추천에서만 빠짐), show-titles로 한 번에 되돌려요.
  // 한 번에 1,200개, 문장은 90개씩 묶어(D1 한 번에 값 100개, 한 요청 문장 수 한도) 몇 번만 보내요. dry: 몇 개인지와 없는 제목만.
  if ((path === "/recipes/hide-titles" || path === "/recipes/show-titles") && method === "POST") {
    const hiding = path === "/recipes/hide-titles";
    const body = await d.readJson(request, 512 * 1024);
    const clean = (a) => (Array.isArray(a) ? a.filter((t) => typeof t === "string" && t.trim()).map((t) => t.trim()) : []);
    const titles = clean(body?.titles).slice(0, 1200);
    const codes = clean(body?.codes).map((c) => c.toUpperCase()).filter((c) => /^[2-9A-HJ-NP-Z]{8}$/.test(c)).slice(0, 1200);
    if (titles.length === 0 && codes.length === 0) return d.json({ error: "titles" }, 400);
    const note = typeof body.note === "string" ? body.note.trim().slice(0, 200) : "";
    // 1.38 정리로 이미 'admin'으로 숨긴 운영자 작품도 다시 보내면 'retired'로 바뀌어, 넣은 분이 다시 열 수 있어요.
    const from = hiding ? "(state IN ('visible', 'waiting') OR (state = 'hidden' AND hidden_reason = 'admin'))" : `state = 'hidden' AND hidden_reason = '${RETIRED}'`;
    const skels = [...new Set(titles.map(nameSkeleton))];
    const found = new Map();
    const look = async (col, keys) => {
      for (let i = 0; i < keys.length; i += 90) {
        const part = keys.slice(i, i + 90);
        for (const r of await d.all(env, `SELECT code, title, state FROM recipes WHERE owner = 'operator' AND ${from} AND ${col} IN (${part.map(() => "?").join(",")})`, ...part)) found.set(r.code, r);
      }
    };
    await look("title_skel", skels);
    await look("code", codes);
    const rows = [...found.values()];
    const knownSkel = new Set(rows.map((r) => nameSkeleton(r.title)));
    const knownCode = new Set(rows.map((r) => r.code));
    const missing = [...titles.filter((t) => !knownSkel.has(nameSkeleton(t))), ...codes.filter((c) => !knownCode.has(c))];
    if (body.dry !== true && rows.length) {
      const stmts = [];
      for (let i = 0; i < rows.length; i += 90) {
        const part = rows.slice(i, i + 90).map((r) => r.code);
        const marks = part.map(() => "?").join(",");
        stmts.push(hiding
          ? d.stmt(env, `UPDATE recipes SET state = 'hidden', hidden_reason = '${RETIRED}', hidden_at = ?, note = ? WHERE code IN (${marks})`, now, note, ...part)
          : d.stmt(env, `UPDATE recipes SET state = 'visible', hidden_reason = NULL, hidden_at = NULL, note = '' WHERE code IN (${marks})`, ...part));
        stmts.push(d.stmt(env, `INSERT INTO recipe_actions (code, action, reason, actor, at) SELECT code, ?, ?, ?, ? FROM recipes WHERE code IN (${marks})`,
          hiding ? "hide" : "show", (note || (hiding ? "판 정리" : "판 정리 되돌림")).slice(0, 200), actorName, now, ...part));
      }
      await d.batch(env, stmts);
    }
    const n = body.dry === true ? 0 : rows.length;
    return d.json({ [hiding ? "hidden" : "shown"]: n, found: rows.length, missing, items: rows.slice(0, 1200).map((r) => ({ code: r.code, title: r.title })) });
  }
  const m = path.match(/^\/recipes\/([2-9A-HJ-NP-Z]{8})\/(approve|return|hide|show|delete|official|showcase)$/);
  if (m && method === "POST") {
    const [, code, what] = m;
    const body = await d.readJson(request, 2048);
    const r = await d.one(env, "SELECT * FROM recipes WHERE code = ?", code);
    if (!r) return d.json({ error: "not_found" }, 404);
    const note = typeof body.note === "string" ? body.note.trim().slice(0, 200) : "";
    const stmts = [];
    let strike = null;
    let unstrike = null;
    if (what === "approve") {
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'visible', decided_at = ?, note = '' WHERE code = ?", now, code));
      if (r.state === "pending") stmts.push(d.stmt(env, "UPDATE recipe_trust SET approved = approved + 1 WHERE device = ?", r.owner));
    } else if (what === "return") {
      if (!note) return d.json({ error: "note", field: "note" }, 400);
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'returned', decided_at = ?, note = ? WHERE code = ?", now, note, code));
      stmts.push(d.stmt(env, "UPDATE recipe_trust SET returned = returned + 1 WHERE device = ?", r.owner));
    } else if (what === "hide") {
      // 1.36 [LIM-10] Hidden by the operator counts like reports hiding it (not again when reports already did).
      if (r.state !== "hidden") strike = r.owner;
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'hidden', hidden_reason = 'admin', hidden_at = ?, note = ? WHERE code = ?", now, note, code));
      if (r.state !== "hidden") stmts.push(d.stmt(env, "UPDATE recipe_trust SET hidden = hidden + 1 WHERE device = ?", r.owner));
    } else if (what === "show") {
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'visible', hidden_reason = NULL, hidden_at = NULL, flags = 0, note = '' WHERE code = ?", code));
      stmts.push(d.stmt(env, "DELETE FROM recipe_flags WHERE code = ?", code));
      stmts.push(d.stmt(env, "DELETE FROM write_strikes WHERE ref = ?", code));
      // 1.36 [SEC-16] M10: shown again, its maker's mark for it goes too (it kept 단골 away for good) and a restriction it started ends.
      if (r.state === "hidden") {
        stmts.push(d.stmt(env, "UPDATE recipe_trust SET hidden = MAX(0, hidden - 1) WHERE device = ?", r.owner));
        unstrike = r.owner;
      }
    } else if (what === "official") {
      stmts.push(d.stmt(env, "UPDATE recipes SET official = ? WHERE code = ?", body.on === false ? 0 : 1, code));
    } else if (what === "showcase") {
      // [RCX-32] Any visible public recipe can be shown as what 나눔 판 can do; off takes it back out.
      stmts.push(d.stmt(env, "UPDATE recipes SET showcase = ? WHERE code = ?", body.on === false ? 0 : 1, code));
    } else {
      stmts.push(...dropRecipe(d, env, code));
    }
    // 1.36 [SEC-16] (PC 확인 F 13차): a recipe the reports hid, shown again, says why in 임시 조치 기록 when the operator wrote nothing.
    stmts.push(act(code, what, note || (what === "show" && r.hidden_reason === "flags" ? "신고가 맞지 않았어요" : "")));
    await d.batch(env, stmts);
    if (strike) await operatorStrike(d, env, strike, "recipe", code, now);
    // 1.39 [CARE-06] Hidden by reports, looked at and kept hidden: its waiting strike counts now (마을 돌봄).
    if (what === "hide" && r.state === "hidden") await confirmStrike(d, env, code, now);
    if (unstrike) await strikeTakenBack(d, env, unstrike, now);
    return d.json({ code, done: what });
  }
  if (path === "/recipes/votes/release" && method === "POST") {
    // S8: held votes the operator looked at and found real.
    const body = await d.readJson(request, 512);
    if (typeof body.code !== "string" || !CODE.test(body.code)) return d.json({ error: "code" }, 400);
    const held = await d.all(env, "SELECT value, weight FROM recipe_votes WHERE code = ? AND held = 1", body.code);
    const likes = held.filter((v) => v.value === 1);
    const dislikes = held.filter((v) => v.value === -1);
    await d.batch(env, [
      d.stmt(env, "UPDATE recipe_votes SET held = 0 WHERE code = ? AND held = 1", body.code),
      d.stmt(env, `UPDATE recipes SET likes = likes + ?, dislikes = dislikes + ?, wlikes = wlikes + ?, wdislikes = wdislikes + ? WHERE code = ?`,
        likes.length, dislikes.length, likes.reduce((s, v) => s + v.weight, 0), dislikes.reduce((s, v) => s + v.weight, 0), body.code),
      act(body.code, "release", `${held.length}`),
    ]);
    return d.json({ released: held.length });
  }
  return null;
}

/** 이 휴대폰이 보낸 정보 모두 지우기: its recipes (with their votes and talk), its votes on others' (off their counts), its trust. */
export async function forgetRecipes(d, env, dev) {
  const own = await d.all(env, "SELECT code FROM recipes WHERE owner = ?", dev);
  const votes = await d.all(env, "SELECT code, value, weight, held FROM recipe_votes WHERE device = ?", dev);
  const stmts = own.flatMap((r) => dropRecipe(d, env, r.code));
  for (const v of votes) {
    if (v.held) continue;
    stmts.push(d.stmt(env, v.value === 1 ? "UPDATE recipes SET likes = likes - 1, wlikes = wlikes - ? WHERE code = ?"
      : "UPDATE recipes SET dislikes = dislikes - 1, wdislikes = wdislikes - ? WHERE code = ?", v.weight, v.code));
  }
  stmts.push(d.stmt(env, "DELETE FROM recipe_votes WHERE device = ?", dev));
  stmts.push(d.stmt(env, "UPDATE recipes SET flags = flags - 1 WHERE code IN (SELECT code FROM recipe_flags WHERE device = ? AND counted = 1)", dev));
  stmts.push(d.stmt(env, "DELETE FROM recipe_flags WHERE device = ?", dev));
  stmts.push(d.stmt(env, "DELETE FROM recipe_trust WHERE device = ?", dev));
  if (stmts.length) await d.batch(env, stmts);
  return own.length;
}

/**
 * 1.36 [MKS-01] 출시 대기 → 장터: the waiting rows [where] picks, at once, each logged. They come up as new (updated_at now) so 새로 순
 * shows them on the day; their min_app still keeps them from older apps.
 */
async function releaseWaiting(d, env, now, where, args, actor) {
  const rows = await d.all(env, `SELECT code FROM recipes WHERE ${where} LIMIT 400`, ...args);
  if (!rows.length) return 0;
  await d.batch(env, rows.flatMap((r) => [
    d.stmt(env, "UPDATE recipes SET state = 'visible', release_at = NULL, created_at = ?, updated_at = ?, decided_at = ? WHERE code = ? AND state = 'waiting'",
      now, now, now, r.code),
    d.stmt(env, "INSERT INTO recipe_actions (code, action, reason, actor, at) VALUES (?, 'release', '', ?, ?)", r.code, actor, now),
  ]));
  return rows.length;
}

/** Hidden recipes go 90 days after they were hidden (처리방침 3.8), from the hourly job. 1.36 [MKS-01] and 예약된 출시 대기 comes up. */
export async function pruneRecipes(d, env, now) {
  await releaseWaiting(d, env, now, "state = 'waiting' AND release_at IS NOT NULL AND release_at <= ?", [now], "예약");
  // 1.39 판 정리로 내린 운영자 작품('retired')은 넣은 분이 계속 열어서 지우지 않아요(사용자 작품이 아니라 처리방침 3.8의 가린 것이 아니에요).
  const old = await d.all(env, `SELECT code FROM recipes WHERE state = 'hidden' AND COALESCE(hidden_reason, '') != '${RETIRED}' AND hidden_at < ? LIMIT 50`, now - 90 * DAY);
  if (old.length) await d.batch(env, old.flatMap((r) => dropRecipe(d, env, r.code)));
  return old.length;
}
