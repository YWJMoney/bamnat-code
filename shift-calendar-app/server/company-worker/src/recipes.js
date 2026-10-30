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

// ---- the format (core recipe/*.kt) ----

export const RECIPE = {
  format: "bamnat-recipe", version: 1, maxBytes: 32 * 1024, maxDepth: 6, maxPieces: 12, maxInputs: 20, maxTokens: 12,
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

const LAYOUT_LINE = /^-?[A-Za-z][A-Za-z0-9]{0,23}(,-?[A-Za-z][A-Za-z0-9]{0,23}){0,39}$/;
const layoutLine = (s) => (s === "" || LAYOUT_LINE.test(s) ? null : "layout");

const companyCheck = (text) => {
  try {
    const v = JSON.parse(text);
    return v && typeof v === "object" && !Array.isArray(v) && v.format === "bamnat-company" ? null : "setup";
  } catch {
    return "setup";
  }
};

/** core PieceKind: key, the kinds of work it fits, its fields. */
export const RECIPE_PIECES = {
  shiftLook: { styles: ["SHIFT", "DAY", "HOURLY"], fields: [I("items", 12, [T("name", 20, { min: 1 }), P("role", ROLES),
    T("label", 2, { min: 1, check: (s) => (graphemes(s) > 2 ? "label" : null) }), C("color"), E("emoji")], { min: 1 })] },
  calTheme: { styles: STYLES, fields: [C("today", { optional: true }), C("sat", { optional: true }), C("sun", { optional: true }),
    C("holiday", { optional: true }), P("cell", ["fill", "tint", "dot"], { optional: true })] },
  alarms: { styles: ["SHIFT", "DAY", "HOURLY"], fields: [I("byRole", 6, [P("role", ROLES), NS("before", 4, 0, 720, { optional: false })]),
    N("snooze", 1, 30, { optional: true }), N("ring", 1, 30, { optional: true }), F("gradual"), F("vibrate"),
    P("mission", ["none", "math", "shake"], { optional: true })] },
  memoTpl: { styles: STYLES, fields: [I("items", 8, [T("title", 30, { min: 1 }), E("emoji"), TS("check", 20, 60),
    T("body", 300, { lines: true, optional: true }), T("repeat", 4, { optional: true, check: (s) => (s === "" || REPEAT.test(s) ? null : "repeat") })], { min: 1 })] },
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
  layout: { styles: STYLES, fields: [TS("tabs", 5, 10), T("calendar", 400, { optional: true, guard: false, check: layoutLine })] },
  cards: { styles: STYLES, fields: [T("stats", 400, { optional: true, guard: false, check: layoutLine }),
    T("money", 400, { optional: true, guard: false, check: layoutLine }), T("memo", 400, { optional: true, guard: false, check: layoutLine })] },
  widget: { styles: STYLES, fields: [N("alpha", 30, 100, { optional: true }), P("theme", ["system", "light", "dark"], { optional: true }), F("memo")] },
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
    const why = textProblem(t);
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
  const out = { format: RECIPE.format, v: RECIPE.version, title };
  if (summary) out.summary = summary;
  if (desc) out.desc = desc;
  out.styles = styles;
  out.cover = cover;
  if (tokens.length) out.tokens = tokens;
  const kept = inputs.filter((i) => used.has(i.id)).map((i) => {
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
  /** 신뢰 0단계 하루 2개, 1단계부터 4개 (S9; 1.35 [RCX-28] 승인 없이 올라가니 조금 넉넉히). */
  perDay: [2, 4, 4],
  perNetworkDay: 60,
  /**
   * D108 (1.34) had a phone's first 3 public recipes wait for the operator. 1.35 [RCX-28] (사용자 2026-10-30 "3개는 관리자 승인을
   * 본다한 부분은 폐지해줘 자율성이 떨어져"): 0, a recipe is up at once; 신고 3번이면 먼저 가려지고(flagsToHide) 운영자가 봄.
   */
  reviewFirst: 0,
  votesPerHour: 60,
  /** S8: more votes than this from one network in an hour wait (held) and count nowhere until the operator lets them. */
  networkVotesHour: 30,
  talkPerDay: 10,
  appliedPerNetworkDay: 200,
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
];

export function newCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  let s = "";
  for (const b of bytes) s += CODE_CHARS[b % 32];
  return s;
}

const kstDayOf = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);

/** A list row as the app reads it (no owner, no hash of the owner). */
export function listItem(r, now) {
  return {
    code: r.code, title: r.title, summary: r.summary, styles: r.styles.split(","), cover: JSON.parse(r.cover), nick: r.nick ?? null,
    version: r.version, likes: r.likes, dislikes: r.dislikes, users: r.applied, kept: keptPercent(r.applied, r.kept7),
    official: r.official === 1, publicData: r.public_data === 1, parent: r.parent ?? null, at: new Date(r.updated_at).toISOString(),
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
  const days = (now - t.first_at) / DAY;
  // [RCX-28] With no 승인 any more, time on the 나눔 판 without anything hidden is what earns it.
  const level = (t.approved >= 3 || days >= 14) && t.hidden === 0 ? 2 : t.approved >= 1 || days >= 3 ? 1 : 0;
  return { level, days, approved: t.approved, weight: level <= 0 || days < 3 ? 0.5 : 1 };
}

async function readDevice(d, request, body) {
  const id = d.deviceId(body?.device ?? request.headers.get("x-device"));
  return id ? d.deviceHash(id) : null;
}

const SORTS = ["good", "hot", "new", "official", "data"];

/** GET /api/recipes: one kind of work's plaza, a page at a time, the same for every phone (edge-cached). */
async function recipeList(d, env, url) {
  const style = url.searchParams.get("style");
  const sort = SORTS.includes(url.searchParams.get("sort")) ? url.searchParams.get("sort") : "hot";
  const q = (url.searchParams.get("q") ?? "").trim().slice(0, 30);
  const cursor = Math.max(0, Math.min(600, Number(url.searchParams.get("cursor")) || 0));
  const now = Date.now();
  const where = ["r.state = 'visible'", "r.scope = 'public'"];
  const args = [];
  if (style && ALL_STYLES.has(style)) {
    where.push("(',' || r.styles || ',') LIKE ?");
    args.push(`%,${style},%`);
  }
  if (sort === "official") where.push("r.official = 1");
  if (sort === "data") where.push("r.public_data = 1");
  if (q) {
    where.push("(r.title LIKE ? OR r.summary LIKE ? OR r.cover LIKE ? OR r.title_skel LIKE ?)");
    const like = `%${q.replace(/[%_]/g, "")}%`;
    args.push(like, like, like, `%${nameSkeleton(q)}%`);
  }
  // 좋은 순 and 요즘 뜨는 are worked out here from the counts (a few hundred rows a plaza); 새로 is by time.
  const rows = await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner WHERE ${where.join(" AND ")}
    ORDER BY r.updated_at DESC LIMIT 600`, ...args);
  let scored = rows;
  if (sort === "good") {
    scored = rows.map((r) => ({ r, s: rankGood(r.wlikes, r.wdislikes, r.applied, r.kept7) })).filter((x) => x.s !== null)
      .sort((a, b) => b.s - a.s).map((x) => x.r);
  } else if (sort === "hot") {
    scored = rows.map((r) => ({ r, s: rankHot(r.wlikes, r.applied, (now - r.created_at) / HOUR) })).sort((a, b) => b.s - a.s).map((x) => x.r);
  }
  const page = scored.slice(cursor, cursor + RECIPE_LIMITS.pageSize);
  return d.json({ style: style ?? null, sort, items: page.map((r) => listItem(r, now)),
    next: cursor + RECIPE_LIMITS.pageSize < scored.length ? cursor + RECIPE_LIMITS.pageSize : null },
  200, { "cache-control": `public, max-age=${RECIPE_LIMITS.edgeSeconds}` });
}

/** GET /api/recipes/:code — the recipe for anyone with its code (link-only too), its own for the maker whatever its state. */
async function show(d, env, request, code, url) {
  const dev = await readDevice(d, request, null);
  const r = await d.one(env, "SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner WHERE r.code = ?", code);
  const mine = Boolean(r && dev && r.owner === dev);
  if (!r || r.state === "deleted" || (!mine && (r.state !== "visible" || r.scope === "private"))) return d.json({ error: "not_found" }, 404);
  const ver = Number(url.searchParams.get("v"));
  let version = { version: r.version, hash: r.hash, body: r.body };
  if (Number.isInteger(ver) && ver > 0 && ver !== r.version) {
    const old = await d.one(env, "SELECT version, hash, body FROM recipe_versions WHERE code = ? AND version = ?", code, ver);
    if (old) version = old;
  }
  const myVote = dev ? await d.one(env, "SELECT value, reason FROM recipe_votes WHERE code = ? AND device = ?", code, dev) : null;
  const out = { ...listItem(r, Date.now()), ...version, scope: r.scope, mine, ...(myVote ? { myVote: myVote.value, myReason: myVote.reason } : {}) };
  if (mine) {
    // The maker sees where it stands and every 안좋아요's reason, without who (결정 D107, 처리방침 3.8).
    const reasons = await d.all(env, "SELECT reason, opinion, at FROM recipe_votes WHERE code = ? AND value = -1 AND held = 0 ORDER BY at DESC LIMIT 100", code);
    const tally = {};
    for (const x of reasons) tally[x.reason] = (tally[x.reason] ?? 0) + 1;
    out.state = r.state;
    out.note = r.note || null;
    out.reasons = { tally, opinions: reasons.filter((x) => x.opinion).map((x) => ({ reason: x.reason, text: x.opinion, at: new Date(x.at).toISOString() })) };
    if (r.state === "hidden") out.hidden = r.hidden_reason;
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
  const hit = await d.overLimit(env, now, [["device", `recipe|${dev}`, RECIPE_LIMITS.perDay[trust.level], DAY],
    ["network", `recipe|${net.day}`, RECIPE_LIMITS.perNetworkDay, DAY]]);
  if (hit) return hit;
  const scope = readScope(body.scope);
  const r = clean.recipe;
  const skel = nameSkeleton(r.title);
  // 사칭 (S4): someone else's recipe with a name that looks the same is refused; the maker's own may share it.
  const same = await d.one(env, "SELECT code FROM recipes WHERE title_skel = ? AND owner != ? AND state IN ('visible', 'pending') LIMIT 1", skel, dev);
  if (same && scope === "public") return d.json({ error: "name_like", field: "title", code: same.code }, 409);
  if (r.parent && !(await d.one(env, "SELECT 1 AS ok FROM recipes WHERE code = ? AND state = 'visible'", r.parent))) {
    return d.json({ error: "parent", field: "parent" }, 400);
  }
  const state = scope === "public" && trust.approved < RECIPE_LIMITS.reviewFirst ? "pending" : "visible";
  let code = newCode();
  while (await d.one(env, "SELECT 1 AS x FROM recipes WHERE code = ?", code)) code = newCode();
  await d.batch(env, [
    d.stmt(env, `INSERT INTO recipes (code, owner, title, title_skel, summary, styles, cover, scope, state, parent, version, hash, body, created_at, updated_at, maker)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)`, code, dev, r.title, skel, r.summary ?? "", r.styles.join(","), JSON.stringify(r.cover), scope,
    state, r.parent ?? null, clean.hash, clean.text, now, now, await makerId(dev)),
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
  if (clean.hash === r.hash) return d.json({ error: "same", field: "recipe" }, 409);
  const hit = await d.overLimit(env, now, [["device", `recipev|${dev}`, 10, DAY]]);
  if (hit) return hit;
  const scope = body.scope === undefined ? r.scope : readScope(body.scope);
  const trust = await recipeTrust(d, env, dev, now);
  // [RCX-28] Up at once like a first 판; one the operator or 신고 hid stays hidden.
  const state = r.state === "hidden" ? "hidden" : scope === "public" && trust.approved < RECIPE_LIMITS.reviewFirst ? "pending" : "visible";
  const v = r.version + 1;
  const rec = clean.recipe;
  await d.batch(env, [
    d.stmt(env, `UPDATE recipes SET title = ?, title_skel = ?, summary = ?, styles = ?, cover = ?, scope = ?, state = ?, version = ?, hash = ?, body = ?,
      updated_at = ?, note = '' WHERE code = ?`, rec.title, nameSkeleton(rec.title), rec.summary ?? "", rec.styles.join(","), JSON.stringify(rec.cover), scope,
    state, v, clean.hash, clean.text, now, code),
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
  const r = await d.one(env, "SELECT code FROM recipes WHERE code = ? AND state = 'visible'", code);
  if (!r) return d.json({ error: "not_found" }, 404);
  const col = body.kind === "applied" ? "applied" : "kept7";
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
  const r = await d.one(env, "SELECT code, owner, state, flags FROM recipes WHERE code = ?", code);
  if (!r || r.state !== "visible") return d.json({ error: "not_found" }, 404);
  if (r.owner === dev) return d.json({ error: "own" }, 400);
  const res = await d.run(env, "INSERT OR IGNORE INTO recipe_flags (code, device, reason, at) VALUES (?, ?, ?, ?)", code, dev, reason, now);
  if (!res?.meta?.changes) return d.json({ flagged: true, already: true });
  const flags = r.flags + 1;
  const hide = flags >= RECIPE_LIMITS.flagsToHide;
  await d.batch(env, [
    d.stmt(env, `UPDATE recipes SET flags = ?${hide ? ", state = 'hidden', hidden_reason = 'flags', hidden_at = ?" : ""} WHERE code = ?`,
      ...(hide ? [flags, now, code] : [flags, code])),
    ...(hide ? [d.stmt(env, "INSERT INTO recipe_actions (code, action, reason, actor, at) VALUES (?, 'hide', 'flags', '', ?)", code, now),
      d.stmt(env, "UPDATE recipe_trust SET hidden = hidden + 1 WHERE device = ?", r.owner)] : []),
  ]);
  return d.json({ flagged: true, hidden: hide });
}

/** GET /api/recipes/mine — this install's recipes in every state, with the reasons of their 안좋아요 counted. */
async function mine(d, env, request) {
  const dev = await readDevice(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const rows = await d.all(env, "SELECT * FROM recipes WHERE owner = ? AND state != 'deleted' ORDER BY updated_at DESC LIMIT 100", dev);
  const trust = await recipeTrust(d, env, dev, now);
  return d.json({ trust: trust.level, leftToday: null, reviewLeft: Math.max(0, RECIPE_LIMITS.reviewFirst - trust.approved),
    items: rows.map((r) => ({ ...listItem(r, now), state: r.state, scope: r.scope, note: r.note || null, hash: r.hash })) });
}

// ---- a recipe's talk (RCP-35) ----

const talkGrp = (code) => code;

/** GET /api/recipes/:code/talk — oldest first, with 만든 사람 and 쓰는 중 N일 marks; a 안좋아요's reasons only on one's own post. */
async function recipeTalk(d, env, request, code) {
  const dev = await readDevice(d, request, null);
  const r = await d.one(env, "SELECT code, owner, state, scope FROM recipes WHERE code = ?", code);
  if (!r || (r.state !== "visible" && r.owner !== dev)) return d.json({ error: "not_found" }, 404);
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
  const used = dev ? await d.one(env, "SELECT count FROM counters WHERE key = ? AND expires > ?", `rtalk|${dev}`, now) : null;
  return d.json({ code, posts, ...(muted ? { mutedUntil: new Date(muted.until).toISOString() } : {}),
    ...(dev ? { left: Math.max(0, RECIPE_LIMITS.talkPerDay - (used?.count ?? 0)) } : {}) });
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
  const net = await d.network(request, env, now);
  const hit = await d.overLimit(env, now, [["device", `rtalk|${dev}`, RECIPE_LIMITS.talkPerDay, DAY], ["network", `rtalk|${net.day}`, 300, DAY]]);
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
    const where = { queue: "state = 'pending'", flagged: "flags > 0 AND state != 'deleted'", hidden: "state = 'hidden'", visible: "state = 'visible'", official: "official = 1" }[tab]
      ?? "state = 'pending'";
    const rows = await d.all(env, `SELECT * FROM recipes WHERE ${where} ORDER BY ${tab === "queue" ? "updated_at ASC" : "updated_at DESC"} LIMIT 200`);
    const flags = rows.length ? await d.all(env, `SELECT code, reason, COUNT(*) AS n FROM recipe_flags WHERE code IN (${rows.map(() => "?").join(",")})
      GROUP BY code, reason`, ...rows.map((r) => r.code)) : [];
    return d.json({ tab, items: rows.map((r) => ({ ...listItem(r, now), state: r.state, scope: r.scope, body: r.body, hash: r.hash, note: r.note,
      late: r.state === "pending" && now - r.updated_at > DAY, flags: flags.filter((f) => f.code === r.code).map((f) => ({ reason: f.reason, n: f.n })),
      hidden: r.hidden_reason ?? null })) });
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
    // The operator's own: 공개 자료 or a 시작 레시피, 공식 marked, never waiting.
    const body = await d.readJson(request, RECIPE.maxBytes + 2048);
    let clean;
    try {
      clean = await cleanRecipe(body.recipe);
    } catch (e) {
      if (e instanceof RecipeError) return d.json({ error: e.code, field: e.path }, 400);
      throw e;
    }
    const r = clean.recipe;
    let code = newCode();
    while (await d.one(env, "SELECT 1 AS x FROM recipes WHERE code = ?", code)) code = newCode();
    await d.batch(env, [
      d.stmt(env, `INSERT INTO recipes (code, owner, title, title_skel, summary, styles, cover, scope, state, official, public_data, version, hash, body, created_at, updated_at, decided_at)
        VALUES (?, 'operator', ?, ?, ?, ?, ?, 'public', 'visible', 1, ?, 1, ?, ?, ?, ?, ?)`, code, r.title, nameSkeleton(r.title), r.summary ?? "",
      r.styles.join(","), JSON.stringify(r.cover), body.publicData === true ? 1 : 0, clean.hash, clean.text, now, now, now),
      d.stmt(env, "INSERT INTO recipe_versions (code, version, hash, body, created_at) VALUES (?, 1, ?, ?, ?)", code, clean.hash, clean.text, now),
      act(code, "official", body.publicData === true ? "공개 자료" : "시작 레시피"),
    ]);
    return d.json({ code, hash: clean.hash }, 201);
  }
  const m = path.match(/^\/recipes\/([2-9A-HJ-NP-Z]{8})\/(approve|return|hide|show|delete|official)$/);
  if (m && method === "POST") {
    const [, code, what] = m;
    const body = await d.readJson(request, 2048);
    const r = await d.one(env, "SELECT * FROM recipes WHERE code = ?", code);
    if (!r) return d.json({ error: "not_found" }, 404);
    const note = typeof body.note === "string" ? body.note.trim().slice(0, 200) : "";
    const stmts = [];
    if (what === "approve") {
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'visible', decided_at = ?, note = '' WHERE code = ?", now, code));
      if (r.state === "pending") stmts.push(d.stmt(env, "UPDATE recipe_trust SET approved = approved + 1 WHERE device = ?", r.owner));
    } else if (what === "return") {
      if (!note) return d.json({ error: "note", field: "note" }, 400);
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'returned', decided_at = ?, note = ? WHERE code = ?", now, note, code));
      stmts.push(d.stmt(env, "UPDATE recipe_trust SET returned = returned + 1 WHERE device = ?", r.owner));
    } else if (what === "hide") {
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'hidden', hidden_reason = 'admin', hidden_at = ?, note = ? WHERE code = ?", now, note, code));
      stmts.push(d.stmt(env, "UPDATE recipe_trust SET hidden = hidden + 1 WHERE device = ?", r.owner));
    } else if (what === "show") {
      stmts.push(d.stmt(env, "UPDATE recipes SET state = 'visible', hidden_reason = NULL, hidden_at = NULL, flags = 0, note = '' WHERE code = ?", code));
      stmts.push(d.stmt(env, "DELETE FROM recipe_flags WHERE code = ?", code));
    } else if (what === "official") {
      stmts.push(d.stmt(env, "UPDATE recipes SET official = ? WHERE code = ?", body.on === false ? 0 : 1, code));
    } else {
      stmts.push(...dropRecipe(d, env, code));
    }
    stmts.push(act(code, what, note));
    await d.batch(env, stmts);
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
  stmts.push(d.stmt(env, "UPDATE recipes SET flags = flags - 1 WHERE code IN (SELECT code FROM recipe_flags WHERE device = ?)", dev));
  stmts.push(d.stmt(env, "DELETE FROM recipe_flags WHERE device = ?", dev));
  stmts.push(d.stmt(env, "DELETE FROM recipe_trust WHERE device = ?", dev));
  if (stmts.length) await d.batch(env, stmts);
  return own.length;
}

/** Hidden recipes go 90 days after they were hidden (처리방침 3.8), from the hourly job. */
export async function pruneRecipes(d, env, now) {
  const old = await d.all(env, "SELECT code FROM recipes WHERE state = 'hidden' AND hidden_at < ? LIMIT 50", now - 90 * DAY);
  if (old.length) await d.batch(env, old.flatMap((r) => dropRecipe(d, env, r.code)));
  return old.length;
}
