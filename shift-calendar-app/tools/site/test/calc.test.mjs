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
