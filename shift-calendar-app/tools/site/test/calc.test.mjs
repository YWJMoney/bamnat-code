// 공개 웹 계산기 공식 시험 [WEB-23]: calc/calc.js를 브라우저 없이 불러 calc-cases.json의 기대값과 맞춰요.
// 같은 사례를 앱 core 시험(WebCalcParityTest)도 봐요. 돌리기: node --test tools/site/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(here, '..');
const remote = path.resolve(site, '../../../remote/v1');
const cases = JSON.parse(fs.readFileSync(path.join(site, 'calc-cases.json'), 'utf8'));

export function loadCalc(file = path.join(site, 'static/calc/calc.js')) {
  const ctx = { console, URLSearchParams, Intl, Math, Date, JSON, BigInt, Promise, Object, Array, String, Number, isNaN, parseFloat };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(file, 'utf8'), ctx, { filename: file });
  const B = ctx.Bamnat;
  const rates = JSON.parse(fs.readFileSync(path.join(remote, 'rates.json'), 'utf8')).data;
  B.data.rates = rates.rates;
  B.data.pensionBases = rates.pensionBases;
  B.data.taxTables = JSON.parse(fs.readFileSync(path.join(remote, 'taxtable.json'), 'utf8')).data.tables;
  return B;
}

const B = loadCalc();
const pick = (o, keys) => Object.fromEntries(keys.map((k) => [k, o[k]]));
// vm 안에서 만든 배열은 프로토타입이 달라 deepEqual이 틀렸다고 해요. 글로 바꿨다 되돌려요.
const plain = (x) => JSON.parse(JSON.stringify(x));

test('실수령 (4대보험·간이세액표)', () => {
  for (const c of cases.takeHome) {
    const r = B.f.takeHome({ gross: c.gross, free: c.free, family: c.family, kids: c.kids, day: c.day });
    const got = { ...pick(r.ins, ['pension', 'health', 'care', 'employment']), tax: r.tax, local: r.local, net: r.net };
    assert.deepEqual(pick(got, Object.keys(c.expect)), c.expect, c.name);
    assert.equal(r.net, c.gross - r.ded, c.name);
  }
});

test('3.3% 원천징수는 버림 (반올림하지 않아요)', () => {
  for (const c of cases.withholding) {
    const r = B.f.withholding(c.amount);
    assert.deepEqual({ income: r.income, local: r.local }, c.expect, String(c.amount));
  }
});

test('일용 세금: 날마다·한꺼번에 받을 때 소액부징수', () => {
  for (const c of cases.daily) {
    const r = B.f.daily({ pay: c.pay, days: c.days, paid: c.paid, emp: false, day: '2026-11-01' });
    assert.deepEqual({ income: r.income, local: r.local }, c.expect, c.name);
  }
});

test('주휴 시간', () => {
  for (const c of cases.juhyu) assert.equal(B.f.juhyu(10320, c.week).holMin, c.expect.holMin, String(c.week));
});

test('퇴직금: 달력으로 만 1년, 3개월 날수', () => {
  const d = B.d;
  for (const c of cases.severance) {
    const r = B.f.severance({ hire: d.parse(c.hire), last: d.parse(c.last), monthPay: c.threeMonthPay / 3 });
    const got = { eligible: r.fullYear, averageDaily: r.daily, amount: r.amount };
    assert.deepEqual(pick(got, Object.keys(c.expect)), c.expect, c.name);
  }
});

test('연차: 민법 제160조 기간, 회계연도 비례', () => {
  const d = B.d;
  for (const c of cases.leave) {
    let lots = B.f.leaveLots(d.parse(c.hire), d.parse(c.until), c.basis, c.fiscalMonth).map((l) => [l.kind, l.days, d.iso(l.on)]);
    if (c.only === 'year') lots = lots.filter((l) => l[0] === 'year');
    if (c.only === 'fiscal') lots = lots.filter((l) => l[0] !== 'month');
    if (c.last) lots = lots.slice(-1);
    assert.deepEqual(plain(lots), c.expect, c.name);
  }
});

test('날짜: 달 끝·윤년', () => {
  const d = B.d;
  assert.equal(d.iso(d.plusMonths(d.parse('2026-01-31'), 1)), '2026-02-28');
  assert.equal(d.iso(d.plusMonths(d.parse('2028-01-31'), 1)), '2028-02-29');
  assert.equal(d.iso(d.plusMonths(d.parse('2026-05-31'), -3)), '2026-02-28');
  assert.equal(d.iso(d.plusYears(d.parse('2024-02-29'), 1)), '2025-02-28');
  assert.equal(d.parse('2026-02-30'), null);
});

test('칸 읽기: 전각 숫자·쉼표는 읽고, 음수·지수는 숫자가 아님', () => {
  assert.equal(B.num('１０３２０'), 10320);
  assert.equal(B.num('3,000,000원'), 3000000);
  assert.equal(B.num(''), 0);
  assert.ok(Number.isNaN(B.num('-5')));
  assert.ok(Number.isNaN(B.num('1e5')));
  assert.equal(B.read(3000000), '300만 원');
  assert.equal(B.read(12345678), '1,234만 5,678원');
  assert.equal(B.fmt(3 * 4.9), '14.7');
  assert.equal(B.fmt(0.0719 * 50, 3), '3.595', '요율 셋째 자리를 3.6으로 줄이지 않아요');
  assert.equal(B.fmt(0.018 * 50, 2), '0.9');
  assert.equal(B.minus(0), '0원');
});

test('조사: 받침을 보고 골라요', () => {
  assert.equal(B.josa('4조 2교대 8일', '는/은'), '4조 2교대 8일은');
  assert.equal(B.josa('주야비휴', '를/을'), '주야비휴를');
  assert.equal(B.josa('소방 21일', '를/을'), '소방 21일을');
});

test('국민연금: 자료에 없는 해는 법이 정한 요율', () => {
  assert.equal(B.pensionRateOn('2026-11-01'), 0.095);
  assert.equal(B.pensionRateOn('2027-05-01'), 0.1);
  assert.equal(B.pensionRateOn('2028-01-01'), 0.105);
  assert.equal(B.pensionRateOn('2040-01-01'), 0.13);
});

test('Play 링크 출처는 늘 bamnat_web, 방문 출처는 utm_term에만', () => {
  const link = decodeURIComponent(B.playLink('juhyu', 'calc').split('referrer=')[1]);
  assert.equal(link, 'utm_source=bamnat_web&utm_medium=calc&utm_campaign=juhyu');
});

// 2.0.0 [RVR-32] (I50b-04) 그만두거나 쉴 때. 금액은 고용보험법 제45·46조·별표 1, 시행령 제68조(대통령령 제35934호), 고용노동부고시 제2026-67호, 고용24.
test('실업급여: 60%, 2026년 이직 상한 68,100원, 최저시급 80% 하한, 받는 날 수', () => {
  // 월 300만 원 석 달(90일): 평균 100,000원 × 60% = 60,000원 < 하한 10,320 × 8 × 80% = 66,048원.
  let r = B.f.jobless({ left: '2026-03-31', pay3: 9000000, days3: 90, hours: 8, years: 1, minWage: 10320 });
  assert.deepEqual([r.avg, r.floor, r.daily, r.byFloor, r.days, r.total], [100000, 66048, 66048, true, 150, 9907200]);
  // 월 500만 원: 평균 164,835원 → 상한 113,500원 × 60% = 68,100원, 50세 이상 10년 이상 270일.
  r = B.f.jobless({ left: '2026-06-30', pay3: 15000000, days3: 91, hours: 8, older: true, years: 4, minWage: 10320 });
  assert.deepEqual([r.daily, r.capped, r.days, r.total, r.month], [68100, true, 270, 18387000, 2043000]);
  // 2025년 이직은 옛 상한 66,000원, 그해 최저시급 10,030원(하한 64,192원).
  r = B.f.jobless({ left: '2025-12-31', pay3: 15000000, days3: 92, hours: 8, minWage: 10030, years: 0 });
  assert.deepEqual([r.daily, r.floor, r.days], [66000, 64192, 120]);
  // 하루 4시간이면 하한도 절반. 통상임금이 평균임금보다 크면 통상임금.
  r = B.f.jobless({ left: '2026-11-12', pay3: 3000000, days3: 92, hours: 4, ordinaryDaily: 50000, minWage: 10320, years: 2 });
  assert.deepEqual([r.byOrdinary, r.base, r.floor, r.daily, r.days], [true, 50000, 33024, 33024, 180]);
  assert.equal(B.f.jobless({ left: '2019-09-30', pay3: 1, days3: 90 }), null);
  assert.equal(B.f.jobless({ left: '2027-01-04', pay3: 9000000, days3: 90, minWage: 10700 }).sure, false);
});

test('출산전후휴가: 처음 60일은 회사, 마지막 30일은 고용보험 220만 원까지', () => {
  assert.deepEqual(pick(B.f.maternity({ month: 3000000 }), ['company', 'insured', 'total', 'cut']), { company: 6000000, insured: 2200000, total: 8200000, cut: true });
  assert.deepEqual(pick(B.f.maternity({ month: 2000000 }), ['total', 'full', 'cut']), { total: 6000000, full: 6000000, cut: false });
  // 둘 이상: 120일, 처음 75일 회사, 마지막 45일은 880만 원 ÷ 120일 × 45일 = 330만 원까지.
  assert.deepEqual(pick(B.f.maternity({ month: 3000000, kind: 'multi' }), ['days', 'company', 'insured']), { days: 120, company: 7500000, insured: 3300000 });
});

test('육아휴직: 1~3개월 250만, 4~6개월 200만, 7개월부터 80% 160만, 6+6·한부모', () => {
  const r = B.f.parental({ month: 3000000, months: 12 });
  assert.deepEqual(Array.from(r.months.slice(0, 7)), [2500000, 2500000, 2500000, 2000000, 2000000, 2000000, 1600000]);
  assert.equal(r.total, 7500000 + 6000000 + 9600000);
  assert.equal(B.f.parental({ month: 1800000, months: 8 }).months[7], 1440000);
  assert.deepEqual(Array.from(B.f.parental({ month: 5000000, months: 6, kind: 'sixsix' }).months), [2500000, 2500000, 3000000, 3500000, 4000000, 4500000]);
  assert.deepEqual(Array.from(B.f.parental({ month: 3500000, months: 4, kind: 'single' }).months), [3000000, 3000000, 3000000, 2000000]);
  assert.equal(B.f.parental({ month: 3000000, months: 30 }).months.length, 18);
});

// 2.0.0 [RVR-31] (I50c-01·03, SPREAD-30) 웹 → 앱 이어받기: core WebHandoff가 읽는 모양과 같아요(WebHandoffTest).
test('이어받기: 순서·첫날·한 번만·시급만 utm_content에, 고른 것만', () => {
  assert.equal(B.handoff({ p: 'DNBO', s: '2026-11-10', o: 1, w: 10320, t: 'slip' }), 'p-DNBO_s-20261110_o-1_w-10320_t-slip');
  assert.equal(B.handoff({ p: 'DNBO', s: '2026-11-10', o: 0 }), 'p-DNBO_s-20261110');
  assert.equal(B.handoff({ w: 10320.4 }), 'w-10320');
  // 순서 없는 첫날·한 번만, 글자 밖 순서, 0원·백만 원 시급, 모르는 갈 곳은 싣지 않아요.
  assert.equal(B.handoff({ s: '2026-11-10', o: 1 }), '');
  assert.equal(B.handoff({ p: 'DNBQ', w: 0 }), '');
  assert.equal(B.handoff({ w: 1000000, t: 'home' }), '');
  assert.equal(B.handoff(null), '');
});

test('이어받기: 받기 링크에 실리고, 명세서 링크만 갈 곳을 실어요', () => {
  const ref = (campaign) => decodeURIComponent(B.playLink(campaign, 'calc').split('referrer=')[1]);
  try {
    B.carry = { w: 10320 };
    assert.equal(ref('alba'), 'utm_source=bamnat_web&utm_medium=calc&utm_campaign=alba&utm_content=w-10320');
    assert.equal(ref('slip_check'), 'utm_source=bamnat_web&utm_medium=calc&utm_campaign=slip_check&utm_content=w-10320_t-slip');
    B.carry = {};
    assert.equal(ref('salary'), 'utm_source=bamnat_web&utm_medium=calc&utm_campaign=salary');
    assert.equal(ref('slip_check'), 'utm_source=bamnat_web&utm_medium=calc&utm_campaign=slip_check&utm_content=t-slip');
    // 따로 utm_content를 준 링크는 그것만(덮어쓰지 않아요).
    B.carry = { p: 'DN' };
    assert.match(decodeURIComponent(B.playLink('x', 'shift', '&utm_content=abc')), /utm_content=abc$/);
  } finally { B.carry = {}; }
});

// 2.0.0 [I50b-01·T50-09] 사장님 인건비: core LaborCost와 같은 사례(WebCalcParityTest.labor).
const hm = (t) => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
test('사장님 인건비: 그달 요일·일요일 주휴·사업주 4대보험', () => {
  for (const c of cases.labor) {
    const r = B.f.labor({ month: c.month, wage: c.wage, days: c.days.map((d) => [d[0], hm(d[1]), hm(d[2])]), breaks: c.breaks, insured: c.insured });
    assert.deepEqual(pick(r, Object.keys(c.expect)), c.expect, c.name);
    assert.equal(r.total, r.base + r.holiday + r.insurance, c.name);
  }
});

test('사장님 인건비: 주 15시간 안 되면 국민연금·건강보험 사업주 몫이 없어요(웹만)', () => {
  const days = [[6, hm('11:00'), hm('15:00')], [7, hm('11:00'), hm('15:00')]];
  const off = B.f.labor({ month: '2026-11', wage: 10320, days, breaks: true, insured: true, shortOff: true });
  assert.equal(off.short, true);
  assert.deepEqual([off.ins.pension, off.ins.health, off.ins.care], [0, 0, 0]);
  assert.ok(off.ins.employment > 0);
  const all = B.f.labor({ month: '2026-11', wage: 10320, days, breaks: true, insured: true });
  assert.ok(all.ins.pension > 0, '앱(core)처럼 shortOff 없이는 그대로');
  assert.equal(B.f.employer(0, '2026-11-01').total, 0);
  assert.equal(B.f.employer(2146560, '2026-11-01').employmentRate.toFixed(4), '0.0115');
});

// 2.0.0 [I50b-03] 한 주 연장·야간·휴일: core MinuteClassifier 기본값과 같은 사례(WebCalcParityTest.week).
test('한 주: 하루 8시간·주 40시간 중 큰 쪽, 휴일도 40시간을 채워요', () => {
  for (const c of cases.week) {
    const days = c.days.map((d) => {
      if (!d) return null;
      const s = hm(d[0]), e = hm(d[1]);
      return { s, e, rest: d[2] != null ? d[2] : B.f.legalBreak(B.f.span(s, e)) };
    });
    const r = B.f.week({ days, holidays: c.holidays.map(Boolean) });
    assert.deepEqual(pick(r, Object.keys(c.expect)), c.expect, c.name);
    assert.equal(r.worked, r.regular + r.overtime + r.hWithin + r.hOver, c.name);
  }
});

test('한 주: 가산은 5명 미만이면 없고, 52시간을 넘으면 알려요', () => {
  const day = { s: hm('09:00'), e: hm('21:00'), rest: 60 };
  const r = B.f.week({ days: [day, day, day, day, day, day, null], holidays: [0, 0, 0, 0, 0, 0, 1], wage: 10000 });
  assert.equal(r.over52, true);
  assert.equal(Math.round(r.ext), Math.round(r.overtime / 60 * 10000 * 0.5));
  const small = B.f.week({ days: [day, day, day, day, day, day, null], holidays: [0, 0, 0, 0, 0, 0, 1], wage: 10000, small: true });
  assert.equal(small.ext + small.nightP + small.holP, 0);
  assert.equal(small.sum, small.base);
  assert.equal(B.f.nightOf(hm('22:00'), hm('22:00') + 540), 480);
  assert.equal(B.f.legalBreak(239), 0);
  assert.equal(B.f.legalBreak(240), 30);
  assert.equal(B.f.legalBreak(509), 30);
  assert.equal(B.f.legalBreak(510), 60);
});

// 2.0.0 [I50b-02] 통상시급(시행령 제6조)과 명세서 한 줄(제56조), 회사가 쓴 시수 거꾸로 찾기.
test('통상임금: 기본급 + 매달 수당 + 정기상여 ÷ 12를 기준시간으로, 원 단위 반올림', () => {
  for (const c of cases.ordinary) {
    const o = B.f.ordinary(c.in);
    assert.deepEqual(pick(o, Object.keys(c.expect)), c.expect, c.name);
    for (const s of c.slips) {
      const r = B.f.slip({ kind: s.kind, minutes: s.minutes, paid: s.paid, small: s.small, hourly: o.hourly, month: o.month, base: c.in.base });
      assert.deepEqual(pick(r, Object.keys(s.expect)), s.expect, `${c.name} · ${s.kind}`);
    }
  }
});

test('명세서: 몇 원 차이(반올림·10원 절사)는 맞아요, 시수가 동떨어지면 짐작하지 않아요', () => {
  const near = B.f.slip({ kind: 'ext', minutes: 600, paid: 154750, hourly: 10320, month: 2156880, base: 2156880 });
  assert.equal(near.want, 154800);
  assert.equal(near.ok, true);
  const odd = B.f.slip({ kind: 'ext', minutes: 600, paid: 120000, hourly: 10320, month: 2156880, base: 2156880 });
  assert.equal(odd.ok, false);
  assert.equal(odd.of, undefined, '8,000원은 흔한 시수로 나눈 값이 아니에요');
});

// 2.0.0 [RVR-35] (검토 T50-04) 혼자 하는 가게: 그달 문 여는 날(쉬는 요일·공휴일·더 쉬는 날)과 하루 매출 목표.
test('가게: 문 여는 날은 쉬는 요일과 공휴일을 한 번씩만 빼요', () => {
  const hols = JSON.parse(fs.readFileSync(path.join(remote, 'holidays.json'), 'utf8')).data.days.map((h) => h.date);
  // 2026년 11월: 일요일 1·8·15·22·29일
  assert.deepEqual(plain(B.f.openDays({ month: '2026-11', off: '7', holidays: hols })), { days: 30, open: 25, weekly: 5, holidays: 0, extra: 0 });
  // 2026년 10월: 일요일 4번, 공휴일 3·5·9일(개천절이 토요일이라도 일요일만 쉬는 가게는 공휴일로 쉬어요), 더 쉬는 날 2일
  assert.deepEqual(plain(B.f.openDays({ month: '2026-10', off: '7', holOff: true, holidays: hols, extra: 2 })), { days: 31, open: 22, weekly: 4, holidays: 3, extra: 2 });
  // 2026년 5월: 부처님오신날(24일)이 일요일이라 쉬는 요일로 한 번만, 노동절·어린이날·대체공휴일 3일
  assert.deepEqual(plain(B.f.openDays({ month: '2026-05', off: '7', holOff: true, holidays: hols })), { days: 31, open: 23, weekly: 5, holidays: 3, extra: 0 });
  // 월·일 쉬고 공휴일엔 열어요. 더 쉬는 날이 문 여는 날보다 많으면 0일
  assert.equal(B.f.openDays({ month: '2026-11', off: '17', holidays: hols }).open, 20);
  assert.equal(B.f.openDays({ month: '2026-11', off: '1234567', holidays: hols, extra: 3 }).open, 0);
});

test('가게: 하루 매출 목표는 고정비와 가져갈 돈이 남는 매출 ÷ 문 여는 날, 줄을 더하면 한 달 매출', () => {
  const a = B.f.shop({ fixed: 2000000, want: 3000000, cost: 35, open: 25 });
  assert.deepEqual(pick(a, ['ok', 'sales', 'cost', 'fee', 'vat', 'daily', 'more']), { ok: true, sales: 7692308, cost: 2692308, fee: 0, vat: 0, daily: 307700, more: 12900 });
  // 일반과세자·수수료 2%: 받은 돈 = 부가세 뺀 매출 × 1.1, 부가세는 재료에 낸 부가세를 빼고(부가가치세법 제30·38조)
  const b = B.f.shop({ fixed: 2000000, want: 3000000, cost: 35, fee: 2, vat: true, open: 24 });
  assert.deepEqual(pick(b, ['sales', 'cost', 'fee', 'vat', 'daily', 'more']), { sales: 8757962, cost: 3065287, fee: 175159, vat: 517516, daily: 365000, more: 15800 });
  for (const r of [a, b]) assert.ok(Math.abs(r.cost + r.fee + r.vat + r.fixed + r.want - r.sales) <= 2, '줄의 합이 한 달 매출');
  assert.equal(B.f.shop({ fixed: 1000000, want: 1000000, cost: 70, fee: 30, open: 20 }).ok, false, '원가와 수수료가 매출을 다 먹으면 셀 수 없어요');
  assert.equal(B.f.shop({ fixed: 1000000, want: 0, cost: 0, open: 0 }).daily, null);
});
