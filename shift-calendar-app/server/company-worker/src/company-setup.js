// 2.0.0 [RVD-25] SN-17 (스튜디오 제작 오류 조사, 뿌리 R6 '한 규칙표'): what a company settings file may hold, in its own file so the 장터 server
// (recipes.js, a 회사 근무 주기 작품) checks it with the very function the company server does. Moved from index.js as it was; index.js
// imports it back and still exports what it exported.
import { readItemShare } from "./plaza-items.js";
import { graphemes } from "./letters.js";

/** The newest company settings file this server reads (CompanyShare.VERSION in the app). */
// [PAYS-03] 3 carries 감시·단속 승인, 5인 미만 공휴일, 유급 휴게 and allowances paid instead of a premium.
// BC 1.30 [ITEM-04] 4 carries who sets each allowance's amount (회사 공통 · 사람마다 · %) and a company's one amount (결정 D102).
export const SETUP_VERSION = 4;

// ---- a setup: what a report may hold ----

export class Refuse extends Error {
  constructor(code, field, status = 400) {
    super(code);
    this.code = code;
    this.field = field;
    this.status = status;
  }
}

export const refuse = (code, field) => {
  throw new Refuse(code, field);
};

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const BASES = ["LEGAL", "FIXED_PER_TIME", "PERCENT_OF_BASE", "FIXED_PER_HOUR", "HOURLY_RATE", "TIERED", "UNIT"];
const UNITS = ["ORDINARY", "BASE_SHARE", "PER_HOUR", "PER_TIME"];
const CADENCES = ["MONTHLY", "PER_WORK_DAY", "PER_NIGHT_SHIFT", "PER_SHIFT_TYPE", "BONUS"];
const BREAK_PAY = ["UNPAID", "PAID", "AS_WORK"];
/** The premiums an allowance can stand in for, in the app's order. */
const COVERABLE = ["OVERTIME", "NIGHT", "HOLIDAY"];
const KIND_KEY = /^(\d{1,6}|OVERTIME|SPECIAL|COVER)$/;
const LEGACY_KINDS = { OVERTIME: "1", SPECIAL: "2", COVER: "3" };
const SCOPE = /^(all|over:\d{1,4}|law|night|window:([01]\d|2[0-3]):[0-5]\d-([01]\d|2[0-3]):[0-5]\d|holiday|rest)$/;
const TIMING = /^(B,([01]\d|2[0-3]):[0-5]\d,([01]\d|2[0-3]):[0-5]\d,\d{1,3},(-|([01]\d|2[0-3]):[0-5]\d)|A,\d{1,4}|P,\d{1,4}|S,(-|\d{1,6}(;\d{1,6})*))$/;

export const given = (v) => v !== undefined && v !== null;
const object = (v, field) => (v && typeof v === "object" && !Array.isArray(v) ? v : refuse("setup", field));
export const list = (v, max, field) => (Array.isArray(v) && v.length <= max ? v : refuse("setup", field));
const bool = (v, field) => (typeof v === "boolean" ? v : refuse("setup", field));
const int = (v, lo, hi, field) => (Number.isInteger(v) && v >= lo && v <= hi ? v : refuse("setup", field));
const real = (v, lo, hi, field) => (typeof v === "number" && Number.isFinite(v) && v >= lo && v <= hi ? v : refuse("setup", field));
const oneOf = (v, choices, field) => (choices.includes(v) ? v : refuse("setup", field));
const time = (v, field) => (typeof v === "string" && TIME.test(v) ? v : refuse("setup", field));

function str(v, max, field, { empty = true, lines = false, code = "setup" } = {}) {
  if (typeof v !== "string") refuse(code, field);
  const s = v.replace(lines ? /[\u0000-\u0009\u000b-\u001f\u007f]/g : /[\u0000-\u001f\u007f]/g, "").trim();
  if (s.length > max || (!empty && !s)) refuse(code, field);
  return s;
}

function date(v, field) {
  const ok = typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(`${v}T00:00:00Z`));
  return ok ? v : refuse("setup", field);
}

/** 2.0.0 [RVD-20] HQ-08 하루 두 구간 (결정 19번, 설계 4-4절, 갈래 H #451): the keys a shift type carries the gap and the break apart in. */
const TWO_PART_KEYS = ["gapStart", "gap", "rest", "restStart"];
/** [RVD-20] HQ-08 As the app's WorkTime: each part at least 30 minutes, the gap at least 30. */
const MIN_PART = 30;
const minutesOf = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3));
const clock = (m) => `${String(Math.floor((m % (24 * 60)) / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
const DAY_MIN = 24 * 60;

/**
 * 2.0.0 [RVD-20] HQ-08 A shift worked in two parts (11:00~15:00 and 17:00~21:00): the file stays v4, so an app before 2.0.0 reads "start"~"end"
 * with "break" (the break and the gap together, from "breakStart" = the gap's start) as one part, and a 2.0.0 app reads "gapStart"·"gap"
 * (the gap) and "rest"·"restStart" (the break alone) apart. Kept only when they agree with what the older app reads; anything else is
 * refused, as money-bearing fields are (a bug in the app shows up instead of two apps working out different hours from one file).
 */
function twoParts(t, type) {
  const gapStart = time(t.gapStart, "shiftTypes");
  const gap = int(t.gap, MIN_PART, DAY_MIN - 2 * MIN_PART, "shiftTypes");
  const rest = int(t.rest ?? 0, 0, 720, "shiftTypes");
  const restStart = given(t.restStart) ? time(t.restStart, "shiftTypes") : null;
  if (type.off || type.start === null || type.end === null) refuse("setup", "shiftTypes");
  // Minutes from the first start, a shift past midnight read as the app reads it (WorkTime.offset·spanMinutes).
  const span = (minutesOf(type.end) - minutesOf(type.start) + DAY_MIN) % DAY_MIN || DAY_MIN;
  const first = (minutesOf(gapStart) - minutesOf(type.start) + DAY_MIN) % DAY_MIN;
  if (first < MIN_PART || first + gap + MIN_PART > span) refuse("setup", "shiftTypes");
  if (type.break !== rest + gap || type.breakStart !== gapStart) refuse("setup", "shiftTypes");
  return { gapStart, gap, rest, restStart };
}

/**
 * 2.0.0 [RVD-26] SN-16 근무 글자: two letters as a person counts them (an emoji made of several, 👩‍⚕️, is one; letters.js), up to 16 code
 * units, as the app and a 작품 keep it. What 8 code units took before still goes, so no file the server took before is refused now.
 */
function shiftLabel(v) {
  const s = str(v ?? "", 16, "shiftTypes");
  return s.length <= 8 || graphemes(s) <= 2 ? s : refuse("setup", "shiftTypes");
}

/**
 * [COMM-02] A company settings file (CompanyShare in the app) cut down to the fields the app reads,
 * each one checked. Anything carrying money is refused rather than dropped, so a bug in the app shows
 * up instead of the server quietly keeping something the sender never saw. [texts] collects every
 * name in it for the personal-information check.
 */
export function cleanSetup(raw, texts = []) {
  const say = (field, s) => {
    if (s) texts.push([field, s]);
    return s;
  };
  object(raw, "setup");
  if (raw.format !== "bamnat-company") refuse("setup", "format");
  if (int(raw.v, 1, 99, "v") > SETUP_VERSION) refuse("setup_version", "v");
  const shiftTypes = list(raw.shiftTypes, 24, "shiftTypes").map((t) => {
    object(t, "shiftTypes");
    const two = TWO_PART_KEYS.some((k) => given(t[k]));
    const type = {
      name: say("shiftTypes", str(t.name, 40, "shiftTypes", { empty: false })),
      label: say("shiftTypes", shiftLabel(t.label)),
      color: int(t.color ?? 4284246976, 0, 4294967295, "shiftTypes"),
      start: given(t.start) ? time(t.start, "shiftTypes") : null,
      end: given(t.end) ? time(t.end, "shiftTypes") : null,
      // 2.0.0 [RVD-20] HQ-08 With a gap, "break" is the break and the gap together (what an app before 2.0.0 reads), so it may pass 12 hours.
      break: int(t.break ?? 0, 0, two ? 720 + 24 * 60 : 720, "shiftTypes"),
      breakStart: given(t.breakStart) ? time(t.breakStart, "shiftTypes") : null,
      off: bool(t.off ?? false, "shiftTypes"),
      icon: given(t.icon) ? str(t.icon, 32, "shiftTypes") : null,
    };
    return two ? { ...type, ...twoParts(t, type) } : type;
  });
  const names = new Set(shiftTypes.map((t) => t.name));
  if (shiftTypes.length === 0 || names.size !== shiftTypes.length) refuse("setup", "shiftTypes");
  const setup = {
    app: typeof raw.app === "string" ? raw.app.slice(0, 40) : "com.bamnat.app",
    format: "bamnat-company",
    v: raw.v,
    company: "",
    shiftTypes,
  };

  const kindIds = new Set();
  if (given(raw.extraKinds)) {
    const lines = str(raw.extraKinds, 4000, "extraKinds", { lines: true }).split("\n");
    if (lines[0].trim() !== "v2" || lines.length > 41) refuse("setup", "extraKinds");
    const kept = lines.slice(1).filter((l) => l.trim()).map((line) => {
      const f = line.split("|");
      if (f.length !== 8) refuse("setup", "extraKinds");
      const id = int(Number(f[0]), 1, 999999, "extraKinds");
      if (kindIds.has(id)) refuse("setup", "extraKinds");
      kindIds.add(id);
      const name = say("extraKinds", str(f[1], 12, "extraKinds", { empty: false }));
      const short = say("extraKinds", str(f[2], 2, "extraKinds"));
      const color = int(Number(f[3]), 0, 4294967295, "extraKinds");
      if (!TIMING.test(f[4])) refuse("setup", "extraKinds");
      // Shift ids differ from phone to phone; the shifts a 대근 covers travel by name below.
      const timing = f[4].startsWith("S,") ? "S,-" : f[4];
      return [id, name, short, color, timing, oneOf(f[5], ["BY_DAY", "HOLIDAY"], "extraKinds"),
        oneOf(f[6], ["0", "1"], "extraKinds"), oneOf(f[7], ["0", "1"], "extraKinds")].join("|");
    });
    setup.extraKinds = ["v2", ...kept].join("\n");
    const shifts = {};
    const sets = {};
    if (given(raw.extraKindShifts)) {
      for (const [k, n] of Object.entries(object(raw.extraKindShifts, "extraKindShifts"))) {
        if (kindIds.has(Number(k)) && names.has(n)) shifts[k] = n;
      }
    }
    if (given(raw.extraKindShiftSets)) {
      for (const [k, a] of Object.entries(object(raw.extraKindShiftSets, "extraKindShiftSets"))) {
        const covered = [...new Set(list(a, 24, "extraKindShiftSets").filter((n) => names.has(n)))];
        if (kindIds.has(Number(k)) && covered.length) sets[k] = covered;
      }
    }
    setup.extraKindShifts = shifts;
    setup.extraKindShiftSets = sets;
  }

  if (given(raw.rotation)) {
    const r = object(raw.rotation, "rotation");
    const steps = list(r.steps, 60, "rotation").map((s) => (s === null ? null : names.has(s) ? s : refuse("setup", "rotation")));
    if (steps.length === 0) refuse("setup", "rotation");
    setup.rotation = { anchor: date(r.anchor, "rotation"), steps };
  }
  // [PLAZA-01] Weekday and part-time work by the day of the week (Monday first), and a roster that comes every
  // month (간호): kept only when sent, so every setup from before keeps its fingerprint.
  if (given(raw.weekly)) {
    const w = list(raw.weekly, 7, "weekly");
    if (w.length !== 7) refuse("setup", "weekly");
    setup.weekly = w.map((s) => (s === null ? null : names.has(s) ? s : refuse("setup", "weekly")));
  }
  if (bool(raw.roster ?? false, "roster")) setup.roster = true;

  if (given(raw.pay)) {
    const p = object(raw.pay, "pay");
    if (p.withAmounts === true) refuse("amount", "pay");
    const pay = {
      fiveOrMore: bool(p.fiveOrMore, "pay"),
      monthlyHours: int(p.monthlyHours, 1, 400, "pay"),
      holidayMode: oneOf(p.holidayMode, ["FULL", "PREMIUM_ONLY"], "pay"),
      dailyLimit: int(p.dailyLimit ?? -1, -1, 1440, "pay"),
      weeklyLimit: int(p.weeklyLimit ?? -1, -1, 10080, "pay"),
      scheduleIsRegular: bool(p.scheduleIsRegular ?? false, "pay"),
      weekStart: oneOf(p.weekStart ?? "MONDAY", DAYS, "pay"),
      period: period(p.period),
      withAmounts: false,
      items: given(p.items) ? list(p.items, 30, "items").map((i) => item(i, names, say)) : [],
    };
    if (given(p.flexPeriod)) pay.flexPeriod = oneOf(p.flexPeriod, ["MONTH", "QUARTER"], "pay");
    if (given(p.rules)) pay.rules = rules(p.rules, names, kindIds, say);
    setup.pay = pay;
  }
  // [PAYS-03] Only a setup that uses the newer choices is 3, so apps before them can still read the rest.
  // BC 1.30 [ITEM-04] Only one that says who sets an amount (회사 공통 · %) is 4: 1.29 asks to update rather than read it as 사람마다.
  setup.v = setup.pay?.items.some((i) => i.share) ? 4 : usesNewer(setup.pay) ? 3 : Math.min(setup.v, 2);
  return setup;
}

/** Whether [pay] keeps any choice only version 3 of the file has. */
function usesNewer(pay) {
  const r = pay?.rules;
  return Boolean(r && (r.supervisory || r.holidaysU5 || r.breakPay) || pay?.items.some((i) => i.covers));
}

function period(raw) {
  if (!given(raw)) return { endDay: -1, payDay: -1, payNextMonth: false };
  const o = object(raw, "pay");
  return {
    endDay: int(o.endDay ?? -1, -1, 31, "pay"),
    payDay: int(o.payDay ?? -1, -1, 31, "pay"),
    payNextMonth: bool(o.payNextMonth ?? false, "pay"),
  };
}

/** A kind named in the rules; one the file does not list could land on someone else's own kind. */
function kindRef(key, kindIds) {
  if (typeof key !== "string" || !KIND_KEY.test(key)) refuse("setup", "rules");
  const id = LEGACY_KINDS[key] ?? String(Number(key));
  return Number(id) <= 3 || kindIds.has(Number(id)) ? id : null;
}

function rules(raw, names, kindIds, say) {
  const r = object(raw, "rules");
  const rate = (v) => (given(v) ? real(v, 0, 5, "rules") : null);
  const out = {
    ot: rate(r.ot),
    night: rate(r.night),
    hw: rate(r.hw),
    ho: rate(r.ho),
    restDay: oneOf(r.restDay ?? "LAW", ["LAW", "HOLIDAY", "OVERTIME"], "rules"),
    offDay: oneOf(r.offDay ?? "HOLIDAY", ["HOLIDAY", "OVERTIME"], "rules"),
    weeklyHolidayDay: oneOf(r.weeklyHolidayDay ?? "UNKNOWN", ["FIRST_REST", "UNKNOWN", ...DAYS], "rules"),
    publicHolidays: bool(r.publicHolidays ?? true, "rules"),
    roundUnit: int(r.roundUnit ?? 1, 1, 60, "rules"),
    round: oneOf(r.round ?? "DOWN", ["UP", "NEAREST", "DOWN"], "rules"),
    weeklyHoliday: bool(r.weeklyHoliday ?? true, "rules"),
    // Leave days are the sender's own, never the company's.
    annualLeave: null,
    leaveCarry: 0,
    holidayLeaveFree: bool(r.holidayLeaveFree ?? false, "rules"),
    holidayByStart: bool(r.holidayByStart ?? false, "rules"),
    extraByShift: {},
    extra: {},
  };
  // [PAYS-03] Kept only when set, so a setup without them keeps its fingerprint and reads as before.
  if (bool(r.supervisory ?? false, "rules")) out.supervisory = true;
  if (bool(r.holidaysU5 ?? false, "rules")) out.holidaysU5 = true;
  // [HOLI-01][PLAZA-01] 1.25 V: 교대제라 상관없음 is told from 대체휴일 (both unpaid 빨간날) only by this, kept only then.
  if (out.publicHolidays === false && r.holidayWork === "SHIFT_NORMAL") out.holidayWork = "SHIFT_NORMAL";
  const breakPay = oneOf(r.breakPay ?? "UNPAID", BREAK_PAY, "rules");
  if (breakPay !== "UNPAID") out.breakPay = breakPay;
  if (given(r.publicHolidayAs)) {
    const kind = kindRef(String(r.publicHolidayAs), kindIds);
    if (kind) out.publicHolidayAs = kind;
  }
  if (given(r.nightStart)) out.nightStart = time(r.nightStart, "rules");
  if (given(r.nightEnd)) out.nightEnd = time(r.nightEnd, "rules");
  if (given(r.extra)) {
    for (const [k, e] of Object.entries(object(r.extra, "rules"))) {
      const kind = kindRef(k, kindIds);
      if (kind) out.extra[kind] = extraRule(e, say);
    }
  }
  if (given(r.extraByShift)) {
    for (const [k, byShift] of Object.entries(object(r.extraByShift, "rules"))) {
      const kind = kindRef(k, kindIds);
      if (!kind) continue;
      const kept = {};
      for (const [n, e] of Object.entries(object(byShift, "rules"))) if (names.has(n)) kept[n] = extraRule(e, say);
      out.extraByShift[kind] = kept;
    }
  }
  return out;
}

function extraRule(raw, say) {
  const e = object(raw, "rules");
  // The company's way, never its money: amounts per time or hour and 1회 수당 stay on the phone.
  if ((e.amount ?? 0) !== 0 || (e.bonus ?? 0) !== 0) refuse("amount", "rules");
  const out = {
    basis: oneOf(e.basis, BASES, "rules"),
    multiplier: real(e.multiplier ?? 1.5, 0, 10, "rules"),
    amount: 0,
    min: int(e.min ?? 0, 0, 1440, "rules"),
    night: bool(e.night ?? true, "rules"),
    percent: real(e.percent ?? 0.1, 0, 10, "rules"),
    cover: int(e.cover ?? -1, -1, 1440, "rules"),
    premiums: bool(e.premiums ?? false, "rules"),
    bonus: 0,
    unit: oneOf(e.unit ?? "ORDINARY", UNITS, "rules"),
    unitMin: int(e.unitMin ?? 480, 30, 1440, "rules"),
    adds: given(e.adds) ? list(e.adds, 10, "rules").map((p) => premium(p, say)) : [],
  };
  // 1.38 [PAYX-49] 이 일만의 시수 (교육 243시간 while 연장 is 180): a company rule, so it is shared; left out when it follows 월 소정.
  if (given(e.hours)) out.hours = int(e.hours, 100, 300, "rules");
  if (given(e.dayTiers)) out.dayTiers = tiers(e.dayTiers);
  if (given(e.nightTiers)) out.nightTiers = tiers(e.nightTiers);
  return out;
}

function tiers(v) {
  return list(v, 8, "rules").map((t) => {
    object(t, "rules");
    return { min: int(t.min, 0, 1440, "rules"), premium: real(t.premium, 0, 5, "rules") };
  });
}

function premium(raw, say) {
  const p = object(raw, "rules");
  const key = str(p.key, 8, "rules", { empty: false });
  if (!/^[A-Za-z0-9_]+$/.test(key)) refuse("setup", "rules");
  if (typeof p.scope !== "string" || !SCOPE.test(p.scope)) refuse("setup", "rules");
  return {
    key,
    name: say("rules", str(p.name ?? "", 12, "rules")),
    rate: real(p.rate, 0, 3, "rules"),
    on: bool(p.on ?? false, "rules"),
    scope: p.scope,
    offDays: bool(p.offDays ?? false, "rules"),
  };
}

export function item(raw, names, say) {
  const i = object(raw, "items");
  const a = i.amount;
  // Allowance names and how they are paid travel; what anyone is paid does not.
  if (given(a) && (typeof a !== "object" || (Array.isArray(a.entries) && a.entries.length > 0) || given(a.raise))) refuse("amount", "items");
  const out = {
    id: str(i.id, 24, "items", { empty: false }),
    name: say("items", str(i.name, 20, "items", { empty: false })),
    amount: { entries: [], raise: null },
    cadence: oneOf(i.cadence ?? "MONTHLY", CADENCES, "items"),
    types: given(i.types) ? [...new Set(list(i.types, 24, "items").filter((n) => names.has(n)))] : [],
    ordinary: bool(i.ordinary ?? false, "items"),
    taxable: bool(i.taxable ?? true, "items"),
    months: given(i.months) ? [...new Set(list(i.months, 12, "items").map((m) => int(m, 1, 12, "items")))].sort((x, y) => x - y) : [],
    fixedOvertime: int(i.fixedOvertime ?? 0, 0, 100000, "items"),
    festivals: given(i.festivals) ? [...new Set(list(i.festivals, 2, "items").map((f) => oneOf(f, ["SEOLLAL", "CHUSEOK"], "items")))] : [],
    festivalBefore: bool(i.festivalBefore ?? false, "items"),
  };
  // Left out when there is none: the app reads a present value as a number.
  if (given(i.percentOfBase)) out.percentOfBase = real(i.percentOfBase, 0, 50, "items");
  // BC 1.30 [ITEM-04] 회사 공통 · 사람마다 · % and the company's one amount, only with FIXED (결정 D102).
  readItemShare(i, out, (code) => refuse(code, "items"));
  // [PAYS-03] The premiums it is paid instead of; kept only when not what the app assumes anyway
  // (a 고정 연장수당 stands in for 연장, any other allowance for none).
  if (given(i.covers)) {
    const picked = new Set(list(i.covers, COVERABLE.length, "items").map((k) => oneOf(k, COVERABLE, "items")));
    const covers = COVERABLE.filter((k) => picked.has(k));
    const usual = out.fixedOvertime > 0 && out.cadence === "MONTHLY" ? ["OVERTIME"] : [];
    if (covers.join() !== usual.join()) out.covers = covers;
  }
  return out;
}

// index.js uses these too.
export { shiftLabel, TIME, DAYS, BASES, UNITS, CADENCES, BREAK_PAY, COVERABLE, KIND_KEY, LEGACY_KINDS, SCOPE, TIMING, object, bool, int, real, oneOf, time, str, date, TWO_PART_KEYS, MIN_PART, minutesOf, clock, DAY_MIN, twoParts, usesNewer, period, kindRef, rules, extraRule, tiers, premium };
