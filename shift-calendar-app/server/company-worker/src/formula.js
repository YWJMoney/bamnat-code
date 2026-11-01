// 1.36 [RUL-01] 881 수식 엔진, the server's copy of core formula/Formula.kt (사용자 2026-11-01 "수식·다중 조건, 다만 편하게").
//
// A recipe may carry a formula as a value (결정 D106 '값 + 제한된 수식'). Only these functions exist: no loop, no variable to set,
// no address, no file, no clock. The server reads formulas to refuse what the phone would refuse (cleanRecipe) and runs them in
// docs/formula-cases.json to hold the two to the same answers. This file does not import index.js.

export const FORMULA = { maxLen: 600, maxNodes: 200, maxDepth: 16, maxSteps: 4000, maxList: 60, maxText: 300 };

export class FormulaError extends Error {
  constructor(code, message, from = 0, to = from) {
    super(message);
    this.code = code;
    this.from = from;
    this.to = to;
  }
}

// ---- values: {t:"num",v} {t:"text",v} {t:"bool",v} {t:"many",v:[...]} {t:"none"}

const NONE = { t: "none" };
const num = (v) => ({ t: "num", v });
const text = (v) => ({ t: "text", v });
const bool = (v) => ({ t: "bool", v });
const many = (v) => ({ t: "many", v });
const NUMBER = /^-?[0-9]+(\.[0-9]+)?$/;

export function valueOf(x) {
  if (x === null || x === undefined) return NONE;
  if (typeof x === "boolean") return bool(x);
  if (typeof x === "number") return num(x);
  if (typeof x === "string") return text(x);
  if (Array.isArray(x)) return many(x.map(valueOf));
  if (typeof x === "object" && "t" in x) return x;
  return text(String(x));
}

function roundHalfAway(d, digits) {
  const m = Math.pow(10, digits);
  const v = Math.floor(Math.abs(d) * m + 0.5) / m;
  return d < 0 ? -v : v;
}

export function numText(d) {
  if (!Number.isFinite(d)) return "0";
  const r = roundHalfAway(d, 4);
  if (r === Math.floor(r) && Math.abs(r) < 1e15) return String(Math.trunc(r));
  let s = r.toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
  return s === "-0" ? "0" : s;
}

const truthy = (v) => {
  switch (v.t) {
    case "num": return v.v !== 0;
    case "text": return v.v.length > 0;
    case "bool": return v.v;
    case "many": return v.v.length > 0;
    default: return false;
  }
};

export function valueText(v) {
  switch (v.t) {
    case "num": return numText(v.v);
    case "text": return v.v;
    case "bool": return v.v ? "참" : "거짓";
    case "many": return v.v.map(valueText).join(", ");
    default: return "";
  }
}

function valueNumber(v) {
  switch (v.t) {
    case "num": return v.v;
    case "text": {
      let s = v.v.replaceAll(",", "").trim();
      if (s.endsWith("원")) s = s.slice(0, -1);
      s = s.trim();
      return NUMBER.test(s) ? Number(s) : null;
    }
    case "bool": return v.v ? 1 : 0;
    case "none": return 0;
    default: return null;
  }
}

const isEmpty = (v) => v.t === "none" || (v.t === "text" && v.v.length === 0) || (v.t === "many" && v.v.length === 0);

function same(a, b) {
  if (a.t === "many" || b.t === "many") return valueText(a) === valueText(b);
  if (a.t === "none" || b.t === "none") return isEmpty(a) && isEmpty(b);
  if (a.t === "num" && b.t === "num") return a.v === b.v;
  if (a.t === "bool" && b.t === "bool") return a.v === b.v;
  const x = valueNumber(a);
  const y = valueNumber(b);
  if ((a.t === "num" || b.t === "num") && x !== null && y !== null) return x === y;
  return valueText(a) === valueText(b);
}

function order(a, b) {
  const x = valueNumber(a);
  const y = valueNumber(b);
  if (x !== null && y !== null) return x < y ? -1 : x > y ? 1 : 0;
  const s = valueText(a);
  const t = valueText(b);
  return s < t ? -1 : s > t ? 1 : 0;
}

// ---- names (core FormulaName) and functions (core Fn) ----

export const FORMULA_NAMES = [
  ["날짜", "date"], ["월", "month"], ["일", "day"], ["요일", "weekday"], ["주말", "weekend"], ["공휴일", "holiday"],
  ["공휴일이름", "holidayName"], ["오늘", "isToday"], ["지난날", "isPast"], ["며칠뒤", "daysAway", ["남은날"]], ["유형", "workStyle"],
  ["근무", "shift"], ["근무종류", "shiftKind"], ["근무시작", "shiftStart"], ["근무끝", "shiftEnd"], ["근무시간", "hours"], ["쉬는날", "isOff"],
  ["휴가", "leave"], ["이번주야간", "weekNights"], ["이번주근무시간", "weekHours"], ["이달야간", "monthNights"], ["이달근무시간", "monthHours"],
  ["연속근무", "streak"],
  ["메모수", "memoCount"], ["메모", "memo"], ["꼬리표", "tags", ["태그"]], ["체크율", "checkRate"], ["남은체크", "checksLeft"],
  ["지출", "spend"], ["수입", "income"], ["지출분류", "spendKinds"], ["이달지출", "monthSpend"], ["이달수입", "monthIncome"],
  ["이달급여", "monthPay"], ["그날급여", "dayPay"],
  ["지금시각", "now"],
];
const NAME_BY = new Map();
for (const [ko, en, also = []] of FORMULA_NAMES) {
  for (const k of [ko, en.toLowerCase(), ...also]) NAME_BY.set(k, ko);
}
const mainName = (n) => NAME_BY.get(n) ?? NAME_BY.get(n.toLowerCase()) ?? null;

// [key, ko, en, min, max, lazy]
const FNS = [
  ["IF", "만약", "if", 2, 41, true], ["SWITCH", "고르기", "switch", 3, 41, true], ["AND", "모두", "all", 1, 20, true],
  ["OR", "하나라도", "any", 1, 20, true], ["NOT", "아님", "not", 1, 1], ["IFNONE", "없으면", "ifEmpty", 2, 2, true],
  ["INPUT", "칸", "input", 1, 2, true], ["CONTAINS", "포함", "contains", 2, 2], ["STARTS", "시작", "startsWith", 2, 2],
  ["ENDS", "끝", "endsWith", 2, 2], ["LEN", "길이", "len", 1, 1], ["COUNT", "개수", "count", 1, 2], ["SUM", "합", "sum", 1, 20],
  ["AVG", "평균", "avg", 1, 20], ["MAX", "최대", "max", 1, 20], ["MIN", "최소", "min", 1, 20], ["ROUND", "반올림", "round", 1, 2],
  ["FLOOR", "내림", "floor", 1, 1], ["CEIL", "올림", "ceil", 1, 1], ["ABS", "절댓값", "abs", 1, 1], ["BETWEEN", "사이", "between", 3, 3],
  ["TEXT", "글자", "text", 1, 1], ["NUM", "숫자", "num", 1, 1], ["WON", "원", "won", 1, 1], ["EMPTY", "비었나", "isEmpty", 1, 1],
  ["LEFT", "앞", "left", 2, 2], ["REPLACE", "바꾸기", "replace", 3, 3], ["JOIN", "잇기", "join", 1, 2], ["LIST", "목록", "list", 0, 20],
  ["ANYOF", "중하나", "oneOf", 2, 20],
].map(([key, ko, en, min, max, lazy = false]) => ({ key, ko, en, min, max, lazy }));
const FN_BY = new Map();
for (const f of FNS) { FN_BY.set(f.ko, f); FN_BY.set(f.en.toLowerCase(), f); }
const fnOf = (n) => FN_BY.get(n) ?? FN_BY.get(n.toLowerCase()) ?? null;

// ---- reading ----

const isNameStart = (c) => /[\p{L}_]/u.test(c);
const isNamePart = (c) => isNameStart(c) || (c >= "0" && c <= "9");
const isDigit = (c) => c >= "0" && c <= "9";
const SINGLE_OPS = new Set(["+", "-", "*", "/", "%", ">", "<", "=", "!", "&", "×", "÷", "≥", "≤", "≠"]);
const DOUBLE_OPS = new Set([">=", "<=", "!=", "<>", "==", "&&", "||"]);
const CMP = new Set(["=", "==", "!=", "<>", ">", "<", ">=", "<=", "≥", "≤", "≠"]);

function lex(src) {
  if (src.length > FORMULA.maxLen) throw new FormulaError("long", "수식이 너무 길어요.", FORMULA.maxLen, src.length);
  const out = [];
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (/\s/u.test(c)) { i++; continue; }
    if (isDigit(c)) {
      const s = i;
      while (i < src.length && isDigit(src[i])) i++;
      if (i < src.length && src[i] === "." && i + 1 < src.length && isDigit(src[i + 1])) {
        i++;
        while (i < src.length && isDigit(src[i])) i++;
      }
      const t = src.slice(s, i);
      out.push({ kind: "n", text: t, at: s, end: i, num: Number(t) });
      continue;
    }
    if ("\"“”'‘’".includes(c)) {
      const s = i;
      const close = c === "“" ? "”" : c === "‘" ? "’" : c;
      i++;
      let b = "";
      while (i < src.length && src[i] !== close && !(close === "”" && src[i] === "\"") && !(close === "\"" && src[i] === "”")) { b += src[i]; i++; }
      if (i >= src.length) throw new FormulaError("quote", "따옴표가 닫히지 않았어요.", s, src.length);
      i++;
      out.push({ kind: "s", text: b, at: s, end: i });
      continue;
    }
    if (isNameStart(c)) {
      const s = i;
      while (i < src.length && isNamePart(src[i])) i++;
      out.push({ kind: "i", text: src.slice(s, i), at: s, end: i });
      continue;
    }
    if ("(),.".includes(c)) { out.push({ kind: c, text: c, at: i, end: i + 1 }); i++; continue; }
    const two = src.slice(i, i + 2);
    let op;
    if (two.length === 2 && DOUBLE_OPS.has(two)) op = two;
    else if (SINGLE_OPS.has(c)) op = c;
    else throw new FormulaError("char", `'${c}'는 수식에 쓸 수 없는 글자예요.`, i, i + 1);
    out.push({ kind: "o", text: op, at: i, end: i + op.length });
    i += op.length;
  }
  out.push({ kind: "e", text: "", at: src.length, end: src.length });
  return out;
}

function parse(src) {
  const toks = lex(src);
  let p = 0;
  let nodes = 0;
  const peek = () => toks[p];
  const next = () => toks[p++];
  const count = (at) => { if (++nodes > FORMULA.maxNodes) throw new FormulaError("big", "수식이 너무 복잡해요.", at, at); };
  const isWord = (t, ...w) => t.kind === "i" && w.some((x) => x.toLowerCase() === t.text.toLowerCase());
  const isOp = (t, ...o) => t.kind === "o" && o.includes(t.text);
  const depth = (d, at) => { if (d > FORMULA.maxDepth) throw new FormulaError("deep", "너무 깊이 겹쳐 있어요.", at, at); };

  function or(d) {
    let a = and(d);
    while (isWord(peek(), "또는", "or") || isOp(peek(), "||")) { const t = next(); count(t.at); a = { k: "bin", op: "or", a, b: and(d), at: t.at }; }
    return a;
  }
  function and(d) {
    let a = not(d);
    while (isWord(peek(), "그리고", "and") || isOp(peek(), "&&")) { const t = next(); count(t.at); a = { k: "bin", op: "and", a, b: not(d), at: t.at }; }
    return a;
  }
  function not(d) {
    const t = peek();
    if ((isWord(t, "아님", "not") && toks[p + 1].kind !== "(") || isOp(t, "!")) {
      next(); count(t.at); depth(d + 1, t.at);
      return { k: "un", op: "not", x: not(d + 1), at: t.at };
    }
    return cmp(d);
  }
  function cmp(d) {
    const a = add(d);
    const t = peek();
    if (t.kind === "o" && CMP.has(t.text)) {
      next(); count(t.at);
      const op = { "==": "=", "<>": "!=", "≠": "!=", "≥": ">=", "≤": "<=" }[t.text] ?? t.text;
      const b = add(d);
      const u = peek();
      if (u.kind === "o" && CMP.has(u.text)) throw new FormulaError("chain", "비교는 한 번에 하나만 돼요.", u.at, u.end);
      return { k: "bin", op, a, b, at: t.at };
    }
    return a;
  }
  function add(d) {
    let a = mul(d);
    while (isOp(peek(), "+", "-", "&")) { const t = next(); count(t.at); a = { k: "bin", op: t.text, a, b: mul(d), at: t.at }; }
    return a;
  }
  function mul(d) {
    let a = unary(d);
    while (isOp(peek(), "*", "/", "%", "×", "÷")) {
      const t = next(); count(t.at);
      a = { k: "bin", op: t.text === "×" ? "*" : t.text === "÷" ? "/" : t.text, a, b: unary(d), at: t.at };
    }
    return a;
  }
  function unary(d) {
    const t = peek();
    if (isOp(t, "-")) { next(); count(t.at); depth(d + 1, t.at); return { k: "un", op: "-", x: unary(d + 1), at: t.at }; }
    return postfix(d);
  }
  function args(d, name) {
    depth(d, name.at);
    next();
    const out = [];
    if (peek().kind === ")") return [out, next().end];
    for (;;) {
      out.push(or(d));
      const t = next();
      if (t.kind === ",") continue;
      if (t.kind === ")") return [out, t.end];
      if (t.kind === "e") throw new FormulaError("paren", "괄호가 닫히지 않았어요.", name.at, t.at);
      throw new FormulaError("comma", "쉼표가 빠졌어요.", t.at, t.end);
    }
  }
  function call(fn, a, at, end, name) {
    if (a.length < fn.min || a.length > fn.max) throw new FormulaError("args", `${fn.ko}에 들어가는 값의 수가 맞지 않아요.`, name.at, end);
    return { k: "call", fn, args: a, at, end };
  }
  function postfix(d) {
    let x = primary(d);
    while (peek().kind === ".") {
      const dot = next();
      const nm = next();
      if (nm.kind !== "i") throw new FormulaError("dot", "'.' 다음에는 함수 이름이 와야 해요.", dot.at, nm.end);
      const fn = fnOf(nm.text);
      if (!fn) throw new FormulaError("fn", `'${nm.text}'라는 함수는 없어요.`, nm.at, nm.end);
      const a = [x];
      let end = nm.end;
      if (peek().kind === "(") { const [more, e] = args(d + 1, nm); a.push(...more); end = e; }
      count(nm.at);
      x = call(fn, a, x.at, end, nm);
    }
    return x;
  }
  function primary(d) {
    const t = next();
    count(t.at);
    switch (t.kind) {
      case "n": return { k: "lit", v: num(t.num), at: t.at };
      case "s":
        if (t.text.length > FORMULA.maxText) throw new FormulaError("long", "글이 너무 길어요.", t.at, t.end);
        return { k: "lit", v: text(t.text), at: t.at };
      case "(": {
        depth(d + 1, t.at);
        const x = or(d + 1);
        const c = next();
        if (c.kind !== ")") throw new FormulaError("paren", "괄호가 닫히지 않았어요.", t.at, c.at);
        return x;
      }
      case "i": {
        if (isWord(t, "참", "true", "예")) return { k: "lit", v: bool(true), at: t.at };
        if (isWord(t, "거짓", "false", "아니오")) return { k: "lit", v: bool(false), at: t.at };
        if (isWord(t, "없음", "none", "null")) return { k: "lit", v: NONE, at: t.at };
        if (peek().kind === "(") {
          const fn = fnOf(t.text);
          if (!fn) throw new FormulaError("fn", `'${t.text}'라는 함수는 없어요.`, t.at, t.end);
          const [a, end] = args(d + 1, t);
          return call(fn, a, t.at, end, t);
        }
        const name = mainName(t.text);
        if (!name) throw new FormulaError("name", `'${t.text}'라는 값은 없어요.`, t.at, t.end);
        return { k: "ref", name, at: t.at };
      }
      case "e": throw new FormulaError("end", "수식이 덜 끝났어요.", t.at, t.at);
      default: throw new FormulaError("unexpected", `'${t.text}'가 여기 올 수 없어요.`, t.at, t.end);
    }
  }

  if (src.trim().length === 0) throw new FormulaError("empty", "수식이 비어 있어요.", 0, 0);
  const n = or(0);
  const t = peek();
  if (t.kind !== "e") throw new FormulaError("extra", "수식이 끝난 뒤에 더 있어요.", t.at, t.end);
  return n;
}

/** {ok:true, root} or {ok:false, error}. */
export function parseFormula(src) {
  try {
    return { ok: true, root: parse(String(src)) };
  } catch (e) {
    if (e instanceof FormulaError) return { ok: false, error: e };
    throw e;
  }
}

/** null when it reads, else the refusal's code (cleanRecipe's check). */
export const formulaProblem = (src) => { const r = parseFormula(src); return r.ok ? null : r.error.code; };

// ---- running ----

function run(root, scope) {
  let steps = 0;
  const step = (k, at) => { steps += k; if (steps > FORMULA.maxSteps) throw new FormulaError("steps", "계산이 너무 길어요.", at); };
  const N = (v, n) => { const x = valueNumber(v); if (x === null) throw new FormulaError("type", "숫자가 와야 해요.", n.at); return x; };
  const T = (s, n) => { if (s.length > FORMULA.maxText) throw new FormulaError("long", "글이 너무 길어졌어요.", n.at); return text(s); };
  const flat = (a) => a.flatMap((v) => (v.t === "many" ? v.v : [v]));
  const nums = (a, n) => { const f = flat(a); step(f.length, n.at); return f.filter((v) => v.t !== "none").map((v) => N(v, n)); };

  function ev(n) {
    if (++steps > FORMULA.maxSteps) throw new FormulaError("steps", "계산이 너무 길어요.", n.at);
    switch (n.k) {
      case "lit": return n.v;
      case "ref": return scope.get(n.name) ?? NONE;
      case "un": return n.op === "not" ? bool(!truthy(ev(n.x))) : num(-N(ev(n.x), n));
      case "bin": return bin(n);
      default: return callFn(n);
    }
  }
  function bin(n) {
    if (n.op === "and") return bool(truthy(ev(n.a)) && truthy(ev(n.b)));
    if (n.op === "or") return bool(truthy(ev(n.a)) || truthy(ev(n.b)));
    const a = ev(n.a);
    const b = ev(n.b);
    switch (n.op) {
      case "&": return T(valueText(a) + valueText(b), n);
      case "+":
        if ((a.t === "text" && valueNumber(a) === null) || (b.t === "text" && valueNumber(b) === null)) return T(valueText(a) + valueText(b), n);
        return num(N(a, n) + N(b, n));
      case "-": return num(N(a, n) - N(b, n));
      case "*": return num(N(a, n) * N(b, n));
      case "/": { const y = N(b, n); if (y === 0) throw new FormulaError("div0", "0으로 나눌 수 없어요.", n.at); return num(N(a, n) / y); }
      case "%": { const y = N(b, n); if (y === 0) throw new FormulaError("div0", "0으로 나눌 수 없어요.", n.at); const x = N(a, n); return num(((x % y) + y) % y); }
      case "=": return bool(same(a, b));
      case "!=": return bool(!same(a, b));
      default: {
        const c = order(a, b);
        return bool(n.op === ">" ? c > 0 : n.op === "<" ? c < 0 : n.op === ">=" ? c >= 0 : c <= 0);
      }
    }
  }
  function callFn(n) {
    const f = n.fn.key;
    const A = n.args;
    if (n.fn.lazy) {
      switch (f) {
        case "IF": {
          let i = 0;
          for (; i + 1 < A.length; i += 2) if (truthy(ev(A[i]))) return ev(A[i + 1]);
          return i < A.length ? ev(A[i]) : NONE;
        }
        case "SWITCH": {
          const x = ev(A[0]);
          let i = 1;
          for (; i + 1 < A.length; i += 2) if (same(x, ev(A[i]))) return ev(A[i + 1]);
          return i < A.length ? ev(A[i]) : NONE;
        }
        case "AND": return bool(A.every((x) => truthy(ev(x))));
        case "OR": return bool(A.some((x) => truthy(ev(x))));
        case "IFNONE": { const v = ev(A[0]); return isEmpty(v) ? ev(A[1]) : v; }
        case "INPUT": { const label = valueText(ev(A[0])); return scope.input(label) ?? (A.length > 1 ? ev(A[1]) : NONE); }
        default: return NONE;
      }
    }
    const a = A.map(ev);
    let r;
    switch (f) {
      case "NOT": r = bool(!truthy(a[0])); break;
      case "CONTAINS": r = bool(a[0].t === "many" ? a[0].v.some((x) => same(x, a[1])) : valueText(a[1]).length > 0 && valueText(a[0]).includes(valueText(a[1]))); break;
      case "STARTS": r = bool(valueText(a[1]).length > 0 && valueText(a[0]).startsWith(valueText(a[1]))); break;
      case "ENDS": r = bool(valueText(a[1]).length > 0 && valueText(a[0]).endsWith(valueText(a[1]))); break;
      case "LEN": r = num(a[0].t === "many" ? a[0].v.length : a[0].t === "none" ? 0 : valueText(a[0]).length); break;
      case "COUNT": { const items = flat(a.slice(0, 1)); r = num(a.length > 1 ? items.filter((x) => same(x, a[1])).length : items.filter((x) => !isEmpty(x)).length); break; }
      case "SUM": r = num(nums(a, n).reduce((s, x) => s + x, 0)); break;
      case "AVG": { const xs = nums(a, n); r = num(xs.length ? xs.reduce((s, x) => s + x, 0) / xs.length : 0); break; }
      case "MAX": { const xs = nums(a, n); r = xs.length ? num(Math.max(...xs)) : NONE; break; }
      case "MIN": { const xs = nums(a, n); r = xs.length ? num(Math.min(...xs)) : NONE; break; }
      case "ROUND": { const dg = a.length > 1 ? Math.min(6, Math.max(0, Math.trunc(N(a[1], n)))) : 0; r = num(roundHalfAway(N(a[0], n), dg)); break; }
      case "FLOOR": r = num(Math.floor(N(a[0], n))); break;
      case "CEIL": r = num(Math.ceil(N(a[0], n))); break;
      case "ABS": r = num(Math.abs(N(a[0], n))); break;
      case "BETWEEN": { const x = N(a[0], n), lo = N(a[1], n), hi = N(a[2], n); r = bool(x >= Math.min(lo, hi) && x <= Math.max(lo, hi)); break; }
      case "TEXT": r = T(valueText(a[0]), n); break;
      case "NUM": r = num(valueNumber(a[0]) ?? 0); break;
      case "WON": {
        const v = Math.trunc(roundHalfAway(N(a[0], n), 0));
        const s = String(Math.abs(v)).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        r = text((v < 0 ? "-" : "") + s + "원");
        break;
      }
      case "EMPTY": r = bool(isEmpty(a[0])); break;
      case "LEFT": { const k = Math.max(0, Math.trunc(N(a[1], n))); r = text(Array.from(valueText(a[0])).slice(0, k).join("")); break; }
      case "REPLACE": r = valueText(a[1]).length === 0 ? text(valueText(a[0])) : T(valueText(a[0]).replaceAll(valueText(a[1]), valueText(a[2])), n); break;
      case "JOIN": r = T(flat(a.slice(0, 1)).map(valueText).join(a.length > 1 ? valueText(a[1]) : ", "), n); break;
      case "LIST": if (a.length > FORMULA.maxList) throw new FormulaError("list", "목록이 너무 길어요.", n.at); r = many(a); break;
      case "ANYOF": r = bool(a.slice(1).some((x) => same(a[0], x))); break;
      default: r = NONE;
    }
    if (r.t === "many") step(r.v.length, n.at);
    return r;
  }
  return ev(root);
}

/** A scope from plain maps: names by their main spelling, inputs by label. */
export function formulaScope(vars = {}, inputs = {}) {
  return {
    get: (name) => (Object.prototype.hasOwnProperty.call(vars, name) ? valueOf(vars[name]) : null),
    input: (label) => (Object.prototype.hasOwnProperty.call(inputs, label) ? valueOf(inputs[label]) : null),
  };
}

/** The value of [src] in [scope], or null when it does not read or fails while running. */
export function evalFormula(src, scope) {
  const p = parseFormula(src);
  if (!p.ok) return null;
  try {
    return run(p.root, scope);
  } catch (e) {
    if (e instanceof FormulaError) return null;
    throw e;
  }
}

/** core FormulaText: "{…}" formulas inside text; null when it reads, else the first problem's code. */
export function formulaTextProblem(src) {
  if (src.length > FORMULA.maxLen) return "long";
  let i = 0;
  let calcs = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === "{" && src[i + 1] === "{") { i += 2; continue; }
    if (c === "}" && src[i + 1] === "}") { i += 2; continue; }
    if (c === "{") {
      const close = src.indexOf("}", i + 1);
      if (close < 0) return "brace";
      if (++calcs > 8) return "big";
      const p = formulaProblem(src.slice(i + 1, close));
      if (p) return p;
      i = close + 1;
      continue;
    }
    if (c === "}") return "brace";
    i++;
  }
  return null;
}
