// 공개 웹 검토 줄의 시험 [RVR-04][RVR-05] (2.0.0 총괄 묶음 확인: 줄마다 근거 시험 하나).
// 모양(줄바꿈·사진)은 check_pages.js가 폭 380·840·1440·380@200 × 밝은·어두운으로 보고, 여기서는 글·표식이 빠지지 않는지만 봐요.
// 돌리기: node --test tools/site/test/   (build.py --check가 같이 돌려요)
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(here, '..');
const remote = path.resolve(site, '../../../remote/v1');
let root;
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

before(() => {
  root = fs.mkdtempSync(path.join(os.tmpdir(), 'bamnat-site-rows-'));
  fs.cpSync(remote, path.join(root, 'remote/v1'), { recursive: true });
  const built = spawnSync('python3', [path.join(site, 'build.py'), root, '--today', '2026-11-12'], { encoding: 'utf8', env: { ...process.env, BAMNAT_SITE_TODAY: '2026-11-12' } });
  assert.equal(built.status, 0, built.stdout + built.stderr);
});
after(() => { if (root) fs.rmSync(root, { recursive: true, force: true }); });

const htmlPages = (dir = '') => fs.readdirSync(path.join(root, dir), { withFileTypes: true }).flatMap((e) => {
  const p = path.join(dir, e.name);
  if (e.isDirectory()) return ['remote', 'assets'].includes(e.name) && !dir ? [] : htmlPages(p);
  return e.name === 'index.html' ? [p] : [];
});

test('T50-05 투자: 첫 화면 소개·바닥 글·탭에 투자가 있어요', () => {
  const home = read('index.html');
  assert.match(home, /교대·일근·알바·현장·가게·투자, 그리고 근무표를 짜는 리더와 직원을 둔 사장님까지/);
  assert.match(home, /교대·일근·알바·현장·가게·투자, 사장님과 팀 리더까지/);
  const js = read('assets/home.js');
  assert.match(js, /tab: '투자', tag: '주식·ETF·코인'/);
});

test('U50-16 첫 화면 탭: 옆으로 밀지 않고 줄을 바꿔 모두 보여요', () => {
  const css = read('assets/home.css');
  assert.match(css, /\.who-tabs \{ display: flex; flex-wrap: wrap;/);
  assert.doesNotMatch(css, /\.who-tabs \{[^}]*overflow-x/);
});

test('U50-17 머리 메뉴: 지금 쪽에 aria-current가 붙어요', () => {
  const printIdx = read('shift/print/index.html');
  assert.match(printIdx, /<a href="\.\.\/\.\.\/shift\/print\/" aria-current="page">/);
  const guide = htmlPages().filter((p) => /^calc\/[^/]+\/index\.html$/.test(p));
  assert.ok(guide.length >= 5);
  for (const p of guide) assert.match(fs.readFileSync(path.join(root, p), 'utf8'), /aria-current="page"/, p);
});

test('U50-12 인쇄 달력: 근무마다 시각 칸과 범례에 시각이 찍혀요', () => {
  const page = read('shift/print/3j2k/index.html');
  assert.match(page, /id="times"/);
  assert.match(page, /function legend\(c\)\{[^}]*FULL\[x\]\+\(t\?' '\+esc\(t\):''\)/);
  assert.match(page, /placeholder="'\+\(\{D:'07–19'/);
});

test('T50-10 인쇄: 조 4개를 한 표에 나란히(팀 근무표) 찍을 수 있어요', () => {
  const page = read('shift/print/4j3k/index.html');
  assert.match(page, /function teamView\(c,start,t\)/);
  assert.match(page, /<th scope="row">'\+esc\(nm\)\+'<\/th>/);
  assert.match(page, /id="tmonth"/);
  assert.match(page, /조 수를 2부터 적어 주세요/);
});

test('WEB-24 인쇄 달력: 종이에도 한 해 요약(공휴일·야간 횟수) 한 줄이 찍혀요', () => {
  const page = read('shift/print/3j2k/index.html');
  assert.match(page, /id="psum"/);
  assert.match(page, /\$\('psum'\)\.textContent=year\+'년 '\+line\(c,r\)/);
  assert.match(page, /' · 쉬는 공휴일 '\+r\.off\.length\+' · 일하는 공휴일 '/);
  // 쪽 인쇄 CSS에서 psum이 숨겨지지 않아야 종이에 나와요.
  assert.doesNotMatch(page, /#psum\s*\{[^}]*display:\s*none/);
});

test('U50-14 알바 주간 양식: 휴게는 한 주 합계(분)라고 머리에 적혀 있어요', () => {
  const x = path.join(root, 'form/files/bamnat-alba-week.xlsx');
  const py = spawnSync('python3', ['-c',
    'import sys,openpyxl\nws=openpyxl.load_workbook(sys.argv[1]).active\nprint(" | ".join(str(c.value) for r in ws.iter_rows(min_row=1,max_row=3) for c in r if c.value))', x], { encoding: 'utf8' });
  assert.equal(py.status, 0, py.stderr);
  assert.match(py.stdout, /한 주 휴게 합계\(분\)/);
  assert.match(py.stdout, /휴게는 한 주에 쉰 시간을 모두 더해 분으로 적어요/);
});

test('WEB-20 접근성: 그림 설명은 모두 있고, 표 머리는 scope이며, 제목 단계를 건너뛰지 않아요', () => {
  for (const p of htmlPages()) {
    const html = fs.readFileSync(path.join(root, p), 'utf8');
    for (const m of html.matchAll(/<img\b[^>]*>/g)) assert.match(m[0], /\balt="/, `${p}: ${m[0].slice(0, 60)}`);
    const levels = [...html.replace(/<script[\s\S]*?<\/script>/g, '').matchAll(/<h([1-6])\b/g)].map((m) => +m[1]);
    for (let i = 1; i < levels.length; i++) assert.ok(levels[i] <= levels[i - 1] + 1, `${p}: 제목 단계 h${levels[i - 1]} → h${levels[i]}`);
    assert.ok(levels[0] === 1 || levels.length === 0, `${p}: 첫 제목이 h1이 아니에요`);
  }
  // 앱 화면 그림은 그림마다 설명이 달라요.
  const alts = [...read('index.html').matchAll(/<img[^>]*src="assets\/app\/[^"]*"[^>]*alt="([^"]+)"/g)].map((m) => m[1]);
  assert.ok(alts.length >= 5);
  assert.equal(new Set(alts).size, alts.length, '그림 설명이 겹쳐요');
  // 인쇄 표의 머리는 scope.
  assert.match(read('shift/print/4j3k/index.html'), /<th scope="col">조<\/th>/);
});

test('WEB-21 문구: 인쇄 쪽 제목은 이름만(60자 안, 설명 문장 없이), 계산하는 쪽에만 참고용 안내가 있어요', () => {
  for (const p of htmlPages('shift/print')) {
    const t = fs.readFileSync(path.join(root, p), 'utf8').match(/<title>([^<]*)<\/title>/)[1];
    assert.ok(t.length <= 60, `${p}: ${t}`);
    assert.match(t, /^2027 .+ 달력 인쇄( · 연간 근무표 PDF)? \| 밤낮$/, `${p}: ${t}`);
  }
  assert.match(read('calc/salary/index.html'), /참고용 계산이에요/);
  assert.doesNotMatch(read('index.html'), /참고용 계산이에요/);
  // 조사는 받침을 보고 고르는 시험이 calc.test.mjs '조사'에 있어요.
  assert.match(read('calc/calc.js'), /josa/);
});
