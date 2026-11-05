// 1.36 [RUL-01] 881 수식 엔진, the server's copy of core formula/Formula.kt (사용자 2026-11-01 "수식·다중 조건, 다만 편하게").
//
// A recipe may carry a formula as a value (결정 D106 '값 + 제한된 수식'). Only these functions exist: no loop, no variable to set,
// no address, no file, no clock. The server reads formulas to refuse what the phone would refuse (cleanRecipe) and runs them in
// docs/formula-cases.json to hold the two to the fx_same answers. This file does not import index.js.

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

const fx_NONE = { t: "none" };
const fxNum = (v) => ({ t: "num", v });
const fxText = (v) => ({ t: "text", v });
const fxBool = (v) => ({ t: "bool", v });
const fxMany = (v) => ({ t: "many", v });
const fx_NUMBER = /^-?[0-9]+(\.[0-9]+)?$/;

export function valueOf(x) {
  if (x === null || x === undefined) return fx_NONE;
  if (typeof x === "boolean") return fxBool(x);
  if (typeof x === "number") return fxNum(x);
  if (typeof x === "string") return fxText(x);
  if (Array.isArray(x)) return fxMany(x.map(valueOf));
  if (typeof x === "object" && "t" in x) return x;
  return fxText(String(x));
}

function fx_roundHalfAway(d, digits) {
  const m = Math.pow(10, digits);
  const v = Math.floor(Math.abs(d) * m + 0.5) / m;
  return d < 0 ? -v : v;
}

export function numText(d) {
  if (!Number.isFinite(d)) return "0";
  const r = fx_roundHalfAway(d, 4);
  if (r === Math.floor(r) && Math.abs(r) < 1e15) return String(Math.trunc(r));
  let s = r.toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
  return s === "-0" ? "0" : s;
}

const fx_truthy = (v) => {
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

function fx_valueNumber(v) {
  switch (v.t) {
    case "num": return v.v;
    case "text": {
      let s = v.v.replaceAll(",", "").trim();
      if (s.endsWith("원")) s = s.slice(0, -1);
      s = s.trim();
      return fx_NUMBER.test(s) ? Number(s) : null;
    }
    case "bool": return v.v ? 1 : 0;
    case "none": return 0;
    default: return null;
  }
}

const fx_isEmpty = (v) => v.t === "none" || (v.t === "text" && v.v.length === 0) || (v.t === "many" && v.v.length === 0);

function fx_same(a, b) {
  if (a.t === "many" || b.t === "many") return valueText(a) === valueText(b);
  if (a.t === "none" || b.t === "none") return fx_isEmpty(a) && fx_isEmpty(b);
  if (a.t === "num" && b.t === "num") return a.v === b.v;
  if (a.t === "bool" && b.t === "bool") return a.v === b.v;
  const x = fx_valueNumber(a);
  const y = fx_valueNumber(b);
  if ((a.t === "num" || b.t === "num") && x !== null && y !== null) return x === y;
  return valueText(a) === valueText(b);
}

function fx_order(a, b) {
  const x = fx_valueNumber(a);
  const y = fx_valueNumber(b);
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
  ["오늘쓸돈", "spendToday", ["하루쓸돈"]], ["월급까지", "daysToPay", ["입금까지"]], ["예상잔액", "balanceAhead"], ["봉투모은돈", "envelopeSaved"], ["봉투진행", "envelopeRate"], ["지출근무시간", "spendHours"],
  ["이달급여", "monthPay"], ["그날급여", "dayPay"],
  ["지금시각", "now"],
  ["제목", "title"], ["내용", "body"], ["색번호", "colorIndex"], ["고정", "pinned"], ["금액", "amount"], ["종류", "kind"], ["분류", "category"],
  ["칸이름", "columnName"], ["칸수", "columnCount"], ["칸합계", "columnSum"],
];
const fx_NAME_BY = new Map();
for (const [ko, en, also = []] of FORMULA_NAMES) {
  for (const k of [ko, en.toLowerCase(), ...also]) fx_NAME_BY.set(k, ko);
}
const fx_mainName = (n) => fx_NAME_BY.get(n) ?? fx_NAME_BY.get(n.toLowerCase()) ?? null;

// [key, ko, en, min, max, lazy]
const fx_FNS = [
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
const fx_FN_BY = new Map();
for (const f of fx_FNS) { fx_FN_BY.set(f.ko, f); fx_FN_BY.set(f.en.toLowerCase(), f); }
const fx_fnOf = (n) => fx_FN_BY.get(n) ?? fx_FN_BY.get(n.toLowerCase()) ?? null;

// ---- reading ----

const fx_isNameStart = (c) => /[\p{L}_]/u.test(c);
const fx_isNamePart = (c) => fx_isNameStart(c) || (c >= "0" && c <= "9");
const fx_isDigit = (c) => c >= "0" && c <= "9";
const fx_SINGLE_OPS = new Set(["+", "-", "*", "/", "%", ">", "<", "=", "!", "&", "×", "÷", "≥", "≤", "≠"]);
const fx_DOUBLE_OPS = new Set([">=", "<=", "!=", "<>", "==", "&&", "||"]);
const fx_CMP = new Set(["=", "==", "!=", "<>", ">", "<", ">=", "<=", "≥", "≤", "≠"]);

function fx_lex(src) {
  if (src.length > FORMULA.maxLen) throw new FormulaError("long", "수식이 너무 길어요.", FORMULA.maxLen, src.length);
  const out = [];
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (/\s/u.test(c)) { i++; continue; }
    if (fx_isDigit(c)) {
      const s = i;
      while (i < src.length && fx_isDigit(src[i])) i++;
      if (i < src.length && src[i] === "." && i + 1 < src.length && fx_isDigit(src[i + 1])) {
        i++;
        while (i < src.length && fx_isDigit(src[i])) i++;
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
    if (fx_isNameStart(c)) {
      const s = i;
      while (i < src.length && fx_isNamePart(src[i])) i++;
      out.push({ kind: "i", text: src.slice(s, i), at: s, end: i });
      continue;
    }
    if ("(),.".includes(c)) { out.push({ kind: c, text: c, at: i, end: i + 1 }); i++; continue; }
    const two = src.slice(i, i + 2);
    let op;
    if (two.length === 2 && fx_DOUBLE_OPS.has(two)) op = two;
    else if (fx_SINGLE_OPS.has(c)) op = c;
    else throw new FormulaError("char", `'${c}'는 수식에 쓸 수 없는 글자예요.`, i, i + 1);
    out.push({ kind: "o", text: op, at: i, end: i + op.length });
    i += op.length;
  }
  out.push({ kind: "e", text: "", at: src.length, end: src.length });
  return out;
}

function fx_parse(src) {
  const toks = fx_lex(src);
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
    if (t.kind === "o" && fx_CMP.has(t.text)) {
      next(); count(t.at);
      const op = { "==": "=", "<>": "!=", "≠": "!=", "≥": ">=", "≤": "<=" }[t.text] ?? t.text;
      const b = add(d);
      const u = peek();
      if (u.kind === "o" && fx_CMP.has(u.text)) throw new FormulaError("chain", "비교는 한 번에 하나만 돼요.", u.at, u.end);
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
      const fn = fx_fnOf(nm.text);
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
      case "n": return { k: "lit", v: fxNum(t.num), at: t.at };
      case "s":
        if (t.text.length > FORMULA.maxText) throw new FormulaError("long", "글이 너무 길어요.", t.at, t.end);
        return { k: "lit", v: fxText(t.text), at: t.at };
      case "(": {
        depth(d + 1, t.at);
        const x = or(d + 1);
        const c = next();
        if (c.kind !== ")") throw new FormulaError("paren", "괄호가 닫히지 않았어요.", t.at, c.at);
        return x;
      }
      case "i": {
        if (isWord(t, "참", "true", "예")) return { k: "lit", v: fxBool(true), at: t.at };
        if (isWord(t, "거짓", "false", "아니오")) return { k: "lit", v: fxBool(false), at: t.at };
        if (isWord(t, "없음", "none", "null")) return { k: "lit", v: fx_NONE, at: t.at };
        if (peek().kind === "(") {
          const fn = fx_fnOf(t.text);
          if (!fn) throw new FormulaError("fn", `'${t.text}'라는 함수는 없어요.`, t.at, t.end);
          const [a, end] = args(d + 1, t);
          return call(fn, a, t.at, end, t);
        }
        const name = fx_mainName(t.text);
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
    return { ok: true, root: fx_parse(String(src)) };
  } catch (e) {
    if (e instanceof FormulaError) return { ok: false, error: e };
    throw e;
  }
}

/** null when it reads, else the refusal's code (cleanRecipe's check). */
export const formulaProblem = (src) => { const r = parseFormula(src); return r.ok ? null : r.error.code; };

// ---- running ----

function fx_run(root, scope) {
  let steps = 0;
  const step = (k, at) => { steps += k; if (steps > FORMULA.maxSteps) throw new FormulaError("steps", "계산이 너무 길어요.", at); };
  const N = (v, n) => { const x = fx_valueNumber(v); if (x === null) throw new FormulaError("type", "숫자가 와야 해요.", n.at); return x; };
  const T = (s, n) => { if (s.length > FORMULA.maxText) throw new FormulaError("long", "글이 너무 길어졌어요.", n.at); return fxText(s); };
  const flat = (a) => a.flatMap((v) => (v.t === "many" ? v.v : [v]));
  const nums = (a, n) => { const f = flat(a); step(f.length, n.at); return f.filter((v) => v.t !== "none").map((v) => N(v, n)); };

  function ev(n) {
    if (++steps > FORMULA.maxSteps) throw new FormulaError("steps", "계산이 너무 길어요.", n.at);
    switch (n.k) {
      case "lit": return n.v;
      case "ref": return scope.get(n.name) ?? fx_NONE;
      case "un": return n.op === "not" ? fxBool(!fx_truthy(ev(n.x))) : fxNum(-N(ev(n.x), n));
      case "bin": return bin(n);
      default: return callFn(n);
    }
  }
  function bin(n) {
    if (n.op === "and") return fxBool(fx_truthy(ev(n.a)) && fx_truthy(ev(n.b)));
    if (n.op === "or") return fxBool(fx_truthy(ev(n.a)) || fx_truthy(ev(n.b)));
    const a = ev(n.a);
    const b = ev(n.b);
    switch (n.op) {
      case "&": return T(valueText(a) + valueText(b), n);
      case "+":
        if ((a.t === "text" && fx_valueNumber(a) === null) || (b.t === "text" && fx_valueNumber(b) === null)) return T(valueText(a) + valueText(b), n);
        return fxNum(N(a, n) + N(b, n));
      case "-": return fxNum(N(a, n) - N(b, n));
      case "*": return fxNum(N(a, n) * N(b, n));
      case "/": { const y = N(b, n); if (y === 0) throw new FormulaError("div0", "0으로 나눌 수 없어요.", n.at); return fxNum(N(a, n) / y); }
      case "%": { const y = N(b, n); if (y === 0) throw new FormulaError("div0", "0으로 나눌 수 없어요.", n.at); const x = N(a, n); return fxNum(((x % y) + y) % y); }
      case "=": return fxBool(fx_same(a, b));
      case "!=": return fxBool(!fx_same(a, b));
      default: {
        const c = fx_order(a, b);
        return fxBool(n.op === ">" ? c > 0 : n.op === "<" ? c < 0 : n.op === ">=" ? c >= 0 : c <= 0);
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
          for (; i + 1 < A.length; i += 2) if (fx_truthy(ev(A[i]))) return ev(A[i + 1]);
          return i < A.length ? ev(A[i]) : fx_NONE;
        }
        case "SWITCH": {
          const x = ev(A[0]);
          let i = 1;
          for (; i + 1 < A.length; i += 2) if (fx_same(x, ev(A[i]))) return ev(A[i + 1]);
          return i < A.length ? ev(A[i]) : fx_NONE;
        }
        case "AND": return fxBool(A.every((x) => fx_truthy(ev(x))));
        case "OR": return fxBool(A.some((x) => fx_truthy(ev(x))));
        case "IFNONE": { const v = ev(A[0]); return fx_isEmpty(v) ? ev(A[1]) : v; }
        case "INPUT": { const label = valueText(ev(A[0])); return scope.input(label) ?? (A.length > 1 ? ev(A[1]) : fx_NONE); }
        default: return fx_NONE;
      }
    }
    const a = A.map(ev);
    let r;
    switch (f) {
      case "NOT": r = fxBool(!fx_truthy(a[0])); break;
      case "CONTAINS": r = fxBool(a[0].t === "many" ? a[0].v.some((x) => fx_same(x, a[1])) : valueText(a[1]).length > 0 && valueText(a[0]).includes(valueText(a[1]))); break;
      case "STARTS": r = fxBool(valueText(a[1]).length > 0 && valueText(a[0]).startsWith(valueText(a[1]))); break;
      case "ENDS": r = fxBool(valueText(a[1]).length > 0 && valueText(a[0]).endsWith(valueText(a[1]))); break;
      case "LEN": r = fxNum(a[0].t === "many" ? a[0].v.length : a[0].t === "none" ? 0 : valueText(a[0]).length); break;
      case "COUNT": { const items = flat(a.slice(0, 1)); r = fxNum(a.length > 1 ? items.filter((x) => fx_same(x, a[1])).length : items.filter((x) => !fx_isEmpty(x)).length); break; }
      case "SUM": r = fxNum(nums(a, n).reduce((s, x) => s + x, 0)); break;
      case "AVG": { const xs = nums(a, n); r = fxNum(xs.length ? xs.reduce((s, x) => s + x, 0) / xs.length : 0); break; }
      case "MAX": { const xs = nums(a, n); r = xs.length ? fxNum(Math.max(...xs)) : fx_NONE; break; }
      case "MIN": { const xs = nums(a, n); r = xs.length ? fxNum(Math.min(...xs)) : fx_NONE; break; }
      case "ROUND": { const dg = a.length > 1 ? Math.min(6, Math.max(0, Math.trunc(N(a[1], n)))) : 0; r = fxNum(fx_roundHalfAway(N(a[0], n), dg)); break; }
      case "FLOOR": r = fxNum(Math.floor(N(a[0], n))); break;
      case "CEIL": r = fxNum(Math.ceil(N(a[0], n))); break;
      case "ABS": r = fxNum(Math.abs(N(a[0], n))); break;
      case "BETWEEN": { const x = N(a[0], n), lo = N(a[1], n), hi = N(a[2], n); r = fxBool(x >= Math.min(lo, hi) && x <= Math.max(lo, hi)); break; }
      case "TEXT": r = T(valueText(a[0]), n); break;
      case "NUM": r = fxNum(fx_valueNumber(a[0]) ?? 0); break;
      case "WON": {
        const v = Math.trunc(fx_roundHalfAway(N(a[0], n), 0));
        const s = String(Math.abs(v)).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        r = fxText((v < 0 ? "-" : "") + s + "원");
        break;
      }
      case "EMPTY": r = fxBool(fx_isEmpty(a[0])); break;
      case "LEFT": { const k = Math.max(0, Math.trunc(N(a[1], n))); r = fxText(Array.from(valueText(a[0])).slice(0, k).join("")); break; }
      case "REPLACE": r = valueText(a[1]).length === 0 ? fxText(valueText(a[0])) : T(valueText(a[0]).replaceAll(valueText(a[1]), valueText(a[2])), n); break;
      case "JOIN": r = T(flat(a.slice(0, 1)).map(valueText).join(a.length > 1 ? valueText(a[1]) : ", "), n); break;
      case "LIST": if (a.length > FORMULA.maxList) throw new FormulaError("list", "목록이 너무 길어요.", n.at); r = fxMany(a); break;
      case "ANYOF": r = fxBool(a.slice(1).some((x) => fx_same(a[0], x))); break;
      default: r = fx_NONE;
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
    return fx_run(p.root, scope);
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
