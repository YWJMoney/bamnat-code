// 2.0.0 [RVR-34] 길잡이 글(guide/)의 계산 예시가 계산기와 같은 식인지: guide-cases.json의 기대값을 calc.js로 다시 셈해 맞춰요.
// 글의 숫자는 guide_pages.py가 이 파일의 expect에서만 가져가요(글에 숫자를 손으로 적지 않아요). 돌리기: node --test tools/site/test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadCalc } from './calc.test.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const { cases } = JSON.parse(fs.readFileSync(path.resolve(here, '../guide-cases.json'), 'utf8'));
const B = loadCalc();
const F = B.f;
const mins = (t) => { const [h, m] = t.split(':'); return +h * 60 + +m; };
const day = (s) => new Date(s + 'T00:00:00Z');

const run = {
  allowance: (i) => {
    const r = F.allowance({ wage: i.wage, start: mins(i.start), end: mins(i.end), rest: i.rest, restNight: i.restNight, holiday: i.holiday, small: false });
    return { ...r, add: r.ext + r.nightP + r.holP };
  },
  juhyu: (i) => F.juhyu(i.wage, i.weekMin),
  withholding: (i) => F.withholding(i.amount),
  daily: (i) => F.daily({ pay: i.pay, days: i.days, paid: i.paid, emp: false, day: '2026-11-01' }),
  ordinary: (i) => F.ordinary(i),
  severance: (i) => F.severance({ hire: day(i.hire), last: day(i.last), monthPay: i.monthPay }),
  yearDays: (i) => ({ days: F.yearDays(i.n) }),
  week: (i) => {
    const days = [1, 2, 3, 4, 5, 6, 7].map((d) => (i.on.includes(String(d)) ? { s: mins(i.start), e: mins(i.end), rest: i.rest } : null));
    return F.week({ days, holidays: [1, 2, 3, 4, 5, 6, 7].map((d) => i.hol.includes(String(d))), wage: i.wage, small: false });
  },
};

test('길잡이 글의 예시는 계산기와 같은 답이에요', () => {
  assert.ok(cases.length >= 25);
  for (const c of cases) {
    const r = run[c.fn](c.in);
    const got = Object.fromEntries(Object.keys(c.expect).map((k) => [k, typeof r[k] === 'number' ? Math.round(r[k]) : r[k]]));
    assert.deepEqual(got, c.expect, c.id);
  }
});

test('같은 번호의 예시는 한 번만', () => {
  const ids = cases.map((c) => c.id);
  assert.equal(new Set(ids).size, ids.length);
});
