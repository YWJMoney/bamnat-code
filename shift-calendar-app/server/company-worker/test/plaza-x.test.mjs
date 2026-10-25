// [PLAZA-01][PLAZA-03][PLAZA-05] 1.25 묶음 X: 요일별 근무·매달 근무표가 담긴 설정, 초성 찾기, 같은 부서 여러 판의
// 대표 판(결정 D74)과 쓰던 사람에게 가는 소식, 이력의 판 내용. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { choseong, isChoseong, cleanSetup, fingerprint, describe, support } from "../src/index.js";
import { server, report, setup, device, from, signIn } from "./helpers.mjs";

/** A weekday company: Monday to Friday 주간, no rotation. */
function weekday() {
  const s = setup();
  delete s.rotation;
  s.weekly = ["주간", "주간", "주간", "주간", "주간", null, null];
  return s;
}

test("[PLAZA-01] weekly days and a monthly roster travel, and setups without them keep their fingerprint", async () => {
  const before = await fingerprint(cleanSetup(setup()));
  const plain = cleanSetup({ ...setup(), weekly: undefined, roster: false });
  assert.equal(await fingerprint(plain), before);
  assert.equal("weekly" in plain || "roster" in plain, false);
  const w = cleanSetup(weekday());
  assert.deepEqual(w.weekly, ["주간", "주간", "주간", "주간", "주간", null, null]);
  assert.notEqual(await fingerprint(w), before);
  assert.deepEqual(describe(w).find(([l]) => l === "요일별 근무"), ["요일별 근무", "월 주간 · 화 주간 · 수 주간 · 목 주간 · 금 주간 · 토 쉼 · 일 쉼"]);
  const r = cleanSetup({ ...setup(), roster: true });
  assert.equal(r.roster, true);
  assert.deepEqual(describe(r).find(([l]) => l === "근무표"), ["근무표", "매달 근무표(순서 없음)"]);
  // A day names a shift the file has, or nothing; six days are not a week.
  assert.throws(() => cleanSetup({ ...setup(), weekly: ["없는 근무", null, null, null, null, null, null] }));
  assert.throws(() => cleanSetup({ ...setup(), weekly: [null, null, null, null, null, null] }));
  // Sent like any report, read back the same.
  const { call } = server();
  const a = await call("POST", "/api/reports", report(1, { setup: weekday(), company: "평일상사" }), from(1));
  assert.equal(a.status, 201);
  await call("POST", "/api/reports", report(2, { setup: weekday(), company: "평일상사" }), from(2));
  const b = await call("POST", "/api/reports", report(3, { setup: weekday(), company: "평일상사" }), from(3));
  assert.equal(b.data.status, "adopted");
  const page = (await call("GET", `/api/companies/${a.data.company.id}`)).data;
  assert.deepEqual(page.setups[0].setup.weekly, ["주간", "주간", "주간", "주간", "주간", null, null]);
});

test("[PLAZA-01][HOLI-01] 빨간날 근무 travels as one of four ways, and only 교대제라 상관없음 adds a field", async () => {
  const at = (rules) => { const s = setup(); Object.assign(s.pay.rules, rules); return cleanSetup(s); };
  const line = (c) => describe(c).find(([l]) => l === "빨간날 근무")?.[1];
  // The helper's setup pays a 빨간날 as 대근 (kind 3).
  assert.equal(line(cleanSetup(setup())), "대근처럼");
  assert.equal(line(at({ publicHolidayAs: null, holidayByStart: true })), "휴일 근무 가산, 밤샘은 시작한 날로");
  assert.equal(line(at({ publicHolidayAs: null, publicHolidays: false })), "대체휴일");
  const shift = at({ publicHolidayAs: null, publicHolidays: false, holidayWork: "SHIFT_NORMAL" });
  assert.equal(shift.pay.rules.holidayWork, "SHIFT_NORMAL");
  assert.equal(line(shift), "교대제라 상관없음");
  // A paid 빨간날 has no use for it, and a setup without it keeps its fingerprint.
  const premium = at({ publicHolidayAs: null, holidayWork: "SHIFT_NORMAL" });
  assert.equal("holidayWork" in premium.pay.rules, false);
  assert.equal(await fingerprint(premium), await fingerprint(at({ publicHolidayAs: null })));
  assert.notEqual(await fingerprint(shift), await fingerprint(at({ publicHolidayAs: null, publicHolidays: false })));
});

test("[PLAZA-03] 초성 of a name, and a search by 초성 finds companies with a rule in use", async () => {
  assert.equal(choseong("삼성전자"), "ㅅㅅㅈㅈ");
  assert.equal(choseong("서울대병원abc1"), "ㅅㅇㄷㅂㅇabc1");
  assert.equal(isChoseong("ㅅㅅㅈ"), true);
  assert.equal(isChoseong("ㅅ ㅅ"), true);
  assert.equal(isChoseong("ㅅ"), false);
  assert.equal(isChoseong("삼ㅅ"), false);
  const { call } = server();
  for (const n of [1, 2, 3]) await call("POST", "/api/reports", report(n, { company: "(주)삼성전자" }), from(n));
  // [PLAZA-10] Only pending: found too since 결정 D79, after the companies with a rule in use, as a first version.
  await call("POST", "/api/reports", report(4, { company: "삼성정밀" }), from(4));
  let found = (await call("GET", "/api/companies?q=" + encodeURIComponent("ㅅㅅㅈ"))).data.companies;
  assert.deepEqual(found.map((c) => [c.name, c.first ?? false]), [["(주)삼성전자", false], ["삼성정밀", true]]);
  found = (await call("GET", "/api/companies?q=" + encodeURIComponent("ㅅ ㅈ ㅈ"))).data.companies;
  assert.deepEqual(found.map((c) => c.name), ["(주)삼성전자"]);
  // A name changed by the developer is found by its new 초성.
  const h = await signIn(call);
  await call("POST", `/api/admin/companies/${found[0].id}`, { name: "한빛전자" }, h);
  assert.deepEqual((await call("GET", "/api/companies?q=" + encodeURIComponent("ㅎㅂ"))).data.companies.map((c) => c.name), ["한빛전자"]);
  // Words still search as before.
  assert.equal((await call("GET", "/api/companies?q=" + encodeURIComponent("한빛"))).data.companies.length, 1);
});

test("[PLAZA-05] a new 대표 판 tells the phones that used the old one, and the history gives each version's setup", async () => {
  const { call } = server();
  const first = [];
  for (const n of [1, 2, 3]) first.push(await call("POST", "/api/reports", report(n), from(n)));
  const c = first[0].data.company.id;
  const old = first[0].data.setup;
  await call("POST", "/api/uses", { device: device(9), company: c, setup: old }, from(9));
  const since = new Date(Date.now() - 1000).toISOString();
  const other = setup();
  other.shiftTypes[0].end = "18:00";
  let last;
  for (const n of [4, 5, 6, 7, 8]) last = await call("POST", "/api/reports", report(n, { setup: other }), from(n));
  assert.equal(last.data.status, "adopted");
  const news = (await call("GET", `/api/plaza/news?since=${since}`, undefined, { "x-device": device(9) })).data.items;
  assert.deepEqual(news.map((i) => [i.kind, i.version]), [["changed", 2]]);
  const h = (await call("GET", `/api/companies/${c}/history?setup=${old}`)).data.versions;
  assert.deepEqual(h.map((v) => v.source), ["rival", "auto"]);
  assert.equal(h[0].body.shiftTypes[0].end, "18:00");
  assert.equal(h[1].body.shiftTypes[0].end, "19:00");
  assert.ok(h[0].changes.added.some(([l]) => l === "근무"));
});

test("[PLAZA-05] 지지 is the developer's 여러 판 score: a pay-slip check tips a tie, and the recommended 판 is the one the plaza picks", async () => {
  // Weighed by the Wilson share: more 달라요 counts against it, and two people with no 달라요 beat one.
  assert.ok(support({ uses: 0, ok: 3, bad: 0 }) > support({ uses: 0, ok: 3, bad: 2 }));
  assert.ok(support({ uses: 0, ok: 2, bad: 0 }) > support({ uses: 0, ok: 1, bad: 0 }));
  assert.ok(support({ uses: 0, ok: 2, bad: 0, verified: 1 }) > support({ uses: 0, ok: 2, bad: 0 }));
  const { call } = server();
  const first = [];
  for (const n of [1, 2, 3]) first.push(await call("POST", "/api/reports", report(n), from(n)));
  const c = first[0].data.company.id;
  const other = setup();
  other.pay.monthlyHours = 209;
  for (const n of [4, 5]) await call("POST", "/api/reports", report(n, { setup: other }), from(n));
  const h = await signIn(call);
  const before = (await call("GET", "/api/admin/versions", undefined, h)).data.items[0];
  assert.deepEqual(before.candidates.filter((x) => x.recommended).map((x) => x.state), ["active"]);
  // A third with a pay-slip check: now it has more 지지, and the plaza makes it the 대표 판.
  const third = await call("POST", "/api/reports", report(6, { setup: other, verified: true }), from(6));
  assert.equal(third.data.status, "adopted");
  const after = (await call("GET", "/api/admin/versions?show=history", undefined, h)).data.items[0];
  assert.equal(after.candidates.find((x) => x.state === "active").setup, third.data.setup);
});
