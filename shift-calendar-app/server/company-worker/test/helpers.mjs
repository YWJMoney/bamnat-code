// [COMM-04] A worker on an in-memory D1 and a company settings file as the app sends it, for the tests.
import worker from "../src/index.js";
import { d1 } from "./d1.mjs";

export const ORIGIN = "https://company.example";
export const PASSWORD = "night-and-day-admin";

/**
 * 2.0.0 [I60a-01] (검토 TEST-05·07의 날짜 폭탄 4건은 모두 시계를 고정하지 않았거나 고정과 진짜 시계가 섞였어요) 시험의 기본 시각.
 * 날짜에 걸린 약속(새해, 공휴일 표 끝, 처리방침 시행일 12-07·12-09·12-12)을 모두 지난 평범한 평일 한낮(KST 2027-03-17 수 12:00)이에요.
 * 시험 DB의 SQL 'now'도 이 시계를 따라요(d1.mjs).
 */
export const TEST_NOW = Date.parse("2027-03-17T03:00:00Z");

/**
 * [I60a-01] 이 시험의 시계를 [at](기본 [TEST_NOW])에 멈춰요. 시험이 이미 시계를 켰으면 그 시계를 그대로 써요.
 * 움직이려면 `t.mock.timers.tick(ms)`나 `setTime`.
 */
export function fixClock(t, at = TEST_NOW) {
  const now = typeof at === "string" ? Date.parse(at) : at;
  try {
    t.mock.timers.enable({ apis: ["Date"], now });
  } catch (e) {
    if (e?.code !== "ERR_INVALID_STATE") throw e; // 이미 켠 시계
  }
  return Date.now();
}

/**
 * A worker with its own empty database; [call] sends one request and reads the answer. Tests written
 * before 1.21 adopt with two people and give rewards by hand; [plaza] true keeps the server's own
 * defaults instead (three people, rewards given by themselves, 결정 D32·D33).
 *
 * 2.0.0 [I60a-01] [t]를 주면 시계를 [at](기본 [TEST_NOW])에 고정한 채 시작해요: `server({ t })`. 새 시험 파일은 이렇게 써요
 * (clock-rules.test.mjs가 진짜 시계로 도는 파일이 늘지 않게 봐요).
 */
export function server({ plaza = false, t = undefined, at = TEST_NOW, ...extra } = {}) {
  if (t) fixClock(t, at);
  const DB = d1();
  const env = { DB, RATE_SALT: "test-salt", ADMIN_PASSWORD: PASSWORD, ...extra };
  let seeded = plaza || !env.DB;
  const call = async (method, path, body, headers = {}) => {
    if (!seeded) {
      seeded = true;
      await worker.fetch(new Request(`${ORIGIN}/api/terms`), env);
      // [PLAZA-10] and no pass for new information, which 1.26 added.
      for (const [k, v] of [["adopt_persons", "2"], ["amend_persons", "2"], ["auto_reward", "0"], ["first_months", "0"]]) {
        await DB.prepare("INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)").bind(k, v).run();
      }
    }
    const res = await worker.fetch(new Request(ORIGIN + path, {
      method,
      headers: {
        ...(body === undefined ? {} : { "content-type": "application/json" }),
        "cf-connecting-ip": "203.0.113.5",
        ...headers,
      },
      body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body),
      redirect: "manual",
    }), env);
    const text = res.status === 204 ? "" : await res.text();
    // 2.0.0 [RVR-13] (검토 TEST-06) 실서버에서만 아픈 큰 응답을 시험에서 먼저 봐요: 256KB를 넘으면 경로와 크기를 적어 두고 알려요.
    if (text.length > BIG_ANSWER) {
      big.push({ path, bytes: text.length });
      console.warn(`큰 응답: ${method} ${path} ${Math.round(text.length / 1024)}KB (휴대폰 데이터와 D1 읽기 한도를 확인)`);
    }
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      // Not JSON: files of the developer app.
    }
    return { status: res.status, headers: res.headers, data, text };
  };
  const big = [];
  return { env, DB, call, big };
}

/** [RVR-13] 이보다 큰 답(글자 수)은 시험이 알려 줘요. */
export const BIG_ANSWER = 256 * 1024;

/**
 * 2.0.0 [RVR-13] (검토 TEST-06) 같은 요청 [n]개를 한꺼번에 보내요. 실서버(D1)는 요청이 겹쳐 돌아요: '읽고 나서 쓰는' 한도·잠금이
 * 한 번에 들어온 요청으로 넘어가는지(SRV-08 같은 것) 시험이 보게 해요. 답은 보낸 순서대로.
 */
export async function together(call, n, method, path, body, headers = {}) {
  return Promise.all(Array.from({ length: n }, () => call(method, path, body, headers)));
}

/** An install id as the app makes one: random, never tied to a person. */
export const device = (n) => `test-device-${String(n).padStart(8, "0")}`;

/** Another phone: its own install and its own network. */
export const from = (n) => ({ "cf-connecting-ip": `198.51.100.${n}` });

/** Signs in to the developer app; returns the headers its requests carry. */
/**
 * 1.36 [SEC-16] 보안 점검 M10: a report on the 나눔 판 counts toward hiding only from an install past 새싹 (3 days known). These phones
 * become known, and the mocked clock moves 3 days on.
 */
export async function seasoned(call, t, ns) {
  for (const n of ns) await call("GET", "/api/recipes/mine", undefined, { "x-device": device(n) });
  t.mock.timers.tick(3 * 24 * 60 * 60 * 1000 + 1000);
}

export async function signIn(call) {
  const res = await call("POST", "/api/admin/login", { password: PASSWORD }, { "x-admin": "1", origin: ORIGIN });
  if (res.status !== 200) throw new Error(`login ${res.status} ${res.text}`);
  const token = res.headers.get("set-cookie").split(";")[0];
  return { cookie: token, "x-admin": "1", origin: ORIGIN };
}

/**
 * A 4조 2교대 company's settings file, as CompanyShare.export writes it with amounts left out.
 * Made up for the tests: no real company's rules.
 */
export function setup() {
  return {
    app: "com.bamnat.app",
    format: "bamnat-company",
    v: 2,
    company: "예시정밀",
    shiftTypes: [
      { name: "주간", label: "주", color: 4280391411, start: "07:00", end: "19:00", break: 60, breakStart: "12:00", off: false },
      { name: "야간", label: "야", color: 4284246976, start: "19:00", end: "07:00", break: 60, off: false, icon: "moon" },
      { name: "휴무", label: "휴", color: 4288585374, break: 0, off: true },
    ],
    extraKinds: "v2\n1|잔업|잔|4293431308|A,120|BY_DAY|0|0\n2|특근|특|4292882737|B,09:00,18:00,60,12:00|BY_DAY|1|0\n" +
      "3|대근|대|4278099822|S,-|HOLIDAY|1|0\n101|조기출근|조|4286611584|P,60|BY_DAY|0|0",
    extraKindShifts: {},
    extraKindShiftSets: { 3: ["주간", "야간"] },
    rotation: { anchor: "2026-10-01", steps: ["주간", "주간", "휴무", "휴무", "야간", "야간", "휴무", "휴무"] },
    pay: {
      fiveOrMore: true,
      monthlyHours: 180,
      holidayMode: "FULL",
      dailyLimit: -1,
      weeklyLimit: -1,
      scheduleIsRegular: true,
      weekStart: "MONDAY",
      period: { endDay: -1, payDay: 25, payNextMonth: false },
      rules: {
        ot: null, night: null, hw: null, ho: null,
        restDay: "HOLIDAY", offDay: "HOLIDAY", weeklyHolidayDay: "FIRST_REST", publicHolidays: true,
        roundUnit: 1, round: "DOWN", weeklyHoliday: true, annualLeave: null, leaveCarry: 0,
        publicHolidayAs: "3", holidayLeaveFree: false, holidayByStart: false, nightStart: "22:00", nightEnd: "06:00",
        extraByShift: {},
        extra: {
          3: {
            basis: "UNIT", multiplier: 1.5, amount: 0, min: 0, night: true, percent: 0.1, cover: -1, premiums: false, bonus: 0,
            dayTiers: [], nightTiers: [], unit: "BASE_SHARE", unitMin: 480,
            adds: [
              { key: "overtime", name: "연장 가산", rate: 0.5, on: true, scope: "over:480", offDays: false },
              { key: "night", name: "야간 가산", rate: 0.5, on: true, scope: "night", offDays: false },
              { key: "holiday", name: "휴일 가산", rate: 0.5, on: false, scope: "holiday", offDays: false },
              { key: "rest", name: "휴무일 가산", rate: 0.5, on: false, scope: "rest", offDays: false },
            ],
          },
          101: { basis: "LEGAL", multiplier: 1.5, amount: 0, min: 0, night: true, percent: 0.1, cover: -1, premiums: false, bonus: 0,
            dayTiers: [], nightTiers: [], unit: "ORDINARY", unitMin: 480, adds: [] },
        },
      },
      withAmounts: false,
      items: [
        { id: "bonus", name: "상여", amount: { entries: [], raise: null }, cadence: "BONUS", types: [], ordinary: false, taxable: true,
          months: [2, 4, 6, 8, 10, 12], percentOfBase: 1, fixedOvertime: 0, festivals: [], festivalBefore: false },
        { id: "license", name: "자격수당", amount: { entries: [], raise: null }, cadence: "MONTHLY", types: [], ordinary: true, taxable: true,
          months: [], fixedOvertime: 0, festivals: [], festivalBefore: false },
        { id: "night", name: "야간수당", amount: { entries: [], raise: null }, cadence: "PER_SHIFT_TYPE", types: ["야간"], ordinary: false,
          taxable: true, months: [], fixedOvertime: 0, festivals: [], festivalBefore: false },
      ],
    },
  };
}

/** A report body: the app's install id, the company as the sender typed it, and the file. */
export function report(n, extra = {}) {
  // AI 1.27 [PLAZA-30] A 명세서 tick from 1.27 comes with the month it matched; a test of an older app passes proof: null.
  const proof = extra.verified === true && !("proof" in extra) ? { proof: slipProof() } : {};
  return { v: 1, device: device(n), company: "예시정밀", dept: "", verified: false, note: "", setup: setup(), ...proof, ...extra };
}

/** AI 1.27 [PLAZA-30] Last month (Korean time), matched: what the app sends with a 명세서 tick. */
export function slipProof(match = true, back = 1) {
  const d = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const m = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - back, 1));
  return { month: `${m.getUTCFullYear()}-${String(m.getUTCMonth() + 1).padStart(2, "0")}`, match };
}

/**
 * AT 1.29 [ADMCP-07] AN-D6: 1.27's /api/admin/overview is gone. What it answered is read from the three calls the app
 * has used since 1.28 (/badges, /today, /basics), so tests written against it read the same numbers from where they
 * live now; [status] and [headers] are /badges' (sign-in, renewal).
 */
export async function adminHome(call, headers) {
  const b = await call("GET", "/api/admin/badges", undefined, headers);
  if (b.status !== 200) return b;
  const t = (await call("GET", "/api/admin/today", undefined, headers)).data;
  const s = (await call("GET", "/api/admin/basics", undefined, headers)).data;
  return { ...b, data: { ...s, ...t, ...b.data, inboxLate: t.todo.counts.late } };
}
