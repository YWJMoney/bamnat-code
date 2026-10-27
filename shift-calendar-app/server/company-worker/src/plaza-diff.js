// AX 1.30 [ADMX-02] What changed between two versions of a rule, field by field: the one place that says it, for the
// developer app (검토, 여러 판, 회사) and the phones (광장 '달라진 점'). describe() in index.js says a setup in lines;
// here those lines are cut into finer facts (each shift, each premium, each allowance, the order day by day), the facts
// of two versions are matched by label, and a changed fact is compared word by word, so the screen can mark just the
// "60%" that became "50%" and say the whole difference in one line.
//
// Pure functions on describe()'s [label, text] pairs: no database, so tools/bundle.mjs inlines it like the others.

/** The premiums describe() may name in 가산율, in its order. */
const PD_PREMIUMS = ["연장", "야간", "휴일", "휴일 8시간 넘게"];

/** Splits [text] on " · " where no bracket is open, so "식대(매달 · 통상임금)" stays one part. */
function pdParts(text) {
  const out = [];
  let depth = 0;
  let from = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === "(") depth++;
    else if (c === ")") depth = Math.max(0, depth - 1);
    else if (depth === 0 && text.startsWith(" · ", i)) {
      out.push(text.slice(from, i));
      from = i + 3;
      i += 2;
    }
  }
  out.push(text.slice(from));
  return out.map((p) => p.trim()).filter(Boolean);
}

/**
 * "식대(매달 200,000원)" as ["식대", "매달 200,000원"]; a part with no brackets is all name. [FIN130-16] The last brackets are the
 * details: "식대(중식)(매달)" is 식대(중식)'s, not 식대's "중식)(매달".
 */
function pdNamed(part) {
  const m = part.match(/^(.*)\((.*)\)$/);
  return m ? [m[1].trim(), m[2].trim()] : [part, ""];
}

/**
 * [ADMX-02] describe()'s lines as finer facts, [label, text] each. 근무 is one fact per shift ("근무·주간"), 요일별 근무
 * one per weekday, 가산율 one per premium ("가산·야간"), 연장·휴무일 근무 one per kind ("종류·대근"), 수당 one per
 * allowance ("수당·식대"); the order is one fact "순서" whose words are its days. Every other line stays as it is.
 * Labels are unique: a second fact of the same label gets " 2".
 */
export function factsOf(lines) {
  const out = [];
  const seen = new Map();
  const add = (label, text) => {
    const n = (seen.get(label) ?? 0) + 1;
    seen.set(label, n);
    out.push([n > 1 ? `${label} ${n}` : label, text]);
  };
  for (const [label, text] of lines ?? []) {
    const t = String(text ?? "");
    if (label === "근무") {
      for (const p of pdParts(t)) {
        const off = p.match(/^(.+?)\(쉬는 날\)$/);
        if (off) add(`근무·${off[1]}`, "쉬는 날");
        else {
          const sp = p.indexOf(" ");
          add(`근무·${sp > 0 ? p.slice(0, sp) : p}`, sp > 0 ? p.slice(sp + 1) : "");
        }
      }
    } else if (label === "요일별 근무") {
      for (const p of pdParts(t)) add(`요일·${p.slice(0, 1)}`, p.slice(1).trim());
    } else if (label === "가산율") {
      // describe() names only the premiums set apart from the law: each of the four is a fact, "법대로" when unnamed.
      const set = new Map(pdParts(t === "법대로" ? "" : t).map((p) => {
        const sp = p.lastIndexOf(" ");
        return [sp > 0 ? p.slice(0, sp) : p, sp > 0 ? p.slice(sp + 1) : ""];
      }));
      for (const name of [...PD_PREMIUMS, ...[...set.keys()].filter((k) => !PD_PREMIUMS.includes(k))]) add(`가산·${name}`, set.get(name) ?? "법대로");
    } else if (label === "연장·휴무일 근무" || label === "수당") {
      for (const p of pdParts(t)) {
        const [name, inner] = pdNamed(p);
        add(`${label === "수당" ? "수당" : "종류"}·${name}`, inner);
      }
    } else if (/^순서 \d+일$/.test(label)) {
      add("순서", `${label.slice(3)}: ${t}`);
    } else add(label, t);
  }
  return out;
}

const PD_CAP = 400;

/** Words and the spaces between them, so a diff can be put back together exactly. */
const pdWords = (t) => String(t ?? "").split(/(\s+)/).filter((w) => w !== "");

/**
 * [ADMX-02] [a] to [b] word by word: [{t, kind}] with kind "same", "del" (only in a) or "add" (only in b), neighbours of
 * one kind joined. Past [CAP] words a side it is the two whole texts.
 */
export function tokenDiff(a, b) {
  const x = pdWords(a);
  const y = pdWords(b);
  if (x.length > PD_CAP || y.length > PD_CAP) return [...(a ? [{ t: String(a), kind: "del" }] : []), ...(b ? [{ t: String(b), kind: "add" }] : [])];
  const n = x.length;
  const m = y.length;
  const t = Array.from({ length: n + 1 }, () => new Uint16Array(m + 1));
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) t[i][j] = x[i] === y[j] ? t[i + 1][j + 1] + 1 : Math.max(t[i + 1][j], t[i][j + 1]);
  const out = [];
  const push = (kind, s) => {
    const last = out[out.length - 1];
    if (last && last.kind === kind) last.t += s;
    else out.push({ t: s, kind });
  };
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (x[i] === y[j]) {
      push("same", x[i]);
      i++;
      j++;
    } else if (t[i + 1][j] >= t[i][j + 1]) push("del", x[i++]);
    else push("add", y[j++]);
  }
  while (i < n) push("del", x[i++]);
  while (j < m) push("add", y[j++]);
  return out;
}

const pdShort = (s, max = 18) => {
  const c = [...String(s).trim()];
  return c.length > max ? `${c.slice(0, max - 1).join("")}…` : c.join("");
};

/** The order's days as words, past its "21일:" head. */
const pdDays = (text) => String(text).replace(/^\d+일:\s*/, "").split(/\s+/).filter(Boolean);

/** One changed fact in a few words: "60%→50%", for the order the days that moved ("4·5일째 주→야"). */
function pdChangeWords(label, before, after, tokens) {
  if (label === "순서") {
    const a = pdDays(before);
    const b = pdDays(after);
    if (a.length !== b.length) return `${a.length}일→${b.length}일`;
    const at = a.map((d, i) => (d === b[i] ? -1 : i)).filter((i) => i >= 0);
    if (!at.length) return "";
    const from = [...new Set(at.map((i) => a[i]))].join("·");
    const to = [...new Set(at.map((i) => b[i]))].join("·");
    const where = at.length > 4 ? `${at.length}일` : `${at.map((i) => i + 1).join("·")}일째`;
    return `${where} ${from}→${to}`;
  }
  const runs = [];
  let del = "";
  let add = "";
  const flush = () => {
    if (del.trim() || add.trim()) runs.push(del.trim() && add.trim() ? `${pdShort(del)}→${pdShort(add)}` : del.trim() ? `${pdShort(del)} 뺌` : `${pdShort(add)} 더함`);
    del = "";
    add = "";
  };
  for (const tk of tokens) {
    if (tk.kind === "same") {
      if (tk.t.trim()) flush();
      else if (del || add) {
        del += del ? tk.t : "";
        add += add ? tk.t : "";
      }
    } else if (tk.kind === "del") del += tk.t;
    else add += tk.t;
  }
  flush();
  return runs.length > 2 ? `${runs.slice(0, 2).join(", ")} 외 ${runs.length - 2}곳` : runs.join(", ");
}

/** [FIN130-16] An allowance's words that say who sets its amount (plaza-items itemShareText): 회사 공통 or a percent of something. */
const PD_SHARE = /회사 공통|의 \d/;

/**
 * [FIN130-16] An allowance whose amount went from the company's to each person's (or back) says so: describe() writes nothing
 * for 사람마다, so "회사 공통 100,000원" would read as taken away. The side without says "사람마다" where the share stands
 * (before "통상임금").
 */
function pdPersonal(label, before, after) {
  if (!label.startsWith("수당·")) return [before, after];
  const a = PD_SHARE.test(before);
  const b = PD_SHARE.test(after);
  if (a === b) return [before, after];
  const say = (t) => (/(^|\s)통상임금$/.test(t) ? t.replace(/(^|\s)통상임금$/, "$1사람마다 · 통상임금") : `${t ? `${t} ` : ""}사람마다`);
  return a ? [before, say(after)] : [say(before), after];
}

/** "수당·식대" → "수당"; a fact with no group is its own. */
const pdGroupOf = (label) => (label.includes("·") ? label.slice(0, label.indexOf("·")) : label);

/**
 * [ADMX-02] What changed from facts [before] to facts [after] (each [label, text], as factsOf gives them):
 * { added, removed, changed: [{label, before, after, tokens}], summary, same }. A fact whose label is in both but whose
 * text differs is changed, with its word diff; the summary says it all in one line, at most [max] parts:
 * "가산·야간 60%→50% · 대근 12%→15% · 수당 1개 더함".
 */
export function diffFacts(before, after, { max = 4 } = {}) {
  const was = new Map((before ?? []).map(([l, t]) => [l, t]));
  const now = new Map((after ?? []).map(([l, t]) => [l, t]));
  const added = (after ?? []).filter(([l]) => !was.has(l));
  const removed = (before ?? []).filter(([l]) => !now.has(l));
  const changed = (after ?? []).filter(([l, t]) => was.has(l) && was.get(l) !== t).map(([label, text]) => {
    const [from, to] = pdPersonal(label, was.get(label), text);
    const tokens = label === "순서" ? pdOrderTokens(from, to) : tokenDiff(from, to);
    return { label, before: from, after: to, tokens };
  });
  const pieces = changed.map((c) => `${c.label} ${pdChangeWords(c.label, c.before, c.after, c.tokens)}`.trim());
  const count = (list, verb) => {
    const groups = new Map();
    for (const [l] of list) groups.set(pdGroupOf(l), [...(groups.get(pdGroupOf(l)) ?? []), l]);
    for (const [g, ls] of groups) {
      const one = list.find(([l]) => l === ls[0]);
      pieces.push(ls.length > 1 ? `${g} ${ls.length}개 ${verb}` : one[1] && [...one[1]].length <= 12 ? `${ls[0]} ${one[1]} ${verb}` : `${ls[0]} ${verb}`);
    }
  };
  count(added, "더함");
  count(removed, "뺌");
  const summary = pieces.length > max ? `${pieces.slice(0, max).join(" · ")} 외 ${pieces.length - max}곳` : pieces.join(" · ");
  return { added, removed, changed, summary, same: !added.length && !removed.length && !changed.length };
}

/** The order compared day by day (not by LCS, which would slide the days): "21일:" then each day same or swapped. */
function pdOrderTokens(before, after) {
  const a = pdDays(before);
  const b = pdDays(after);
  const head = (t) => String(t).match(/^\d+일:/)?.[0] ?? "";
  const out = [];
  const push = (kind, s) => {
    const last = out[out.length - 1];
    if (last && last.kind === kind) last.t += s;
    else out.push({ t: s, kind });
  };
  if (head(before) === head(after)) push("same", `${head(after)} `);
  else {
    push("del", `${head(before)} `);
    push("add", `${head(after)} `);
  }
  const n = Math.max(a.length, b.length);
  for (let i = 0; i < n; i++) {
    const sep = i < n - 1 ? " " : "";
    if (a[i] === b[i]) push("same", `${a[i]}${sep}`);
    else {
      if (a[i] !== undefined) push("del", a[i]);
      if (b[i] !== undefined) push("add", b[i]);
      if (sep) push("same", sep);
    }
  }
  return out;
}

/**
 * [ADMX-02] The changes between two setups' describe() lines as every screen gets them. [added]/[removed] are the whole
 * lines as before 1.30 (older apps draw them); [summary] is the one line, [facts] the fine-grained added, removed and
 * changed facts with their word tokens. index.js diffLines returns this.
 */
export function lineChanges(before, after) {
  const key = ([label, text]) => `${label}\u0001${text}`;
  const was = new Set(before.map(key));
  const now = new Set(after.map(key));
  const d = diffFacts(factsOf(before), factsOf(after));
  return {
    added: after.filter((l) => !was.has(key(l))),
    removed: before.filter((l) => !now.has(key(l))),
    summary: d.summary,
    facts: { added: d.added, removed: d.removed, changed: d.changed },
  };
}
