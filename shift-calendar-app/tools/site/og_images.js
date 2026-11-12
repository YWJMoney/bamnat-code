// 카톡·SNS 미리보기 그림 og/<이름>.png (1200×630). build.py --og가 돌려요: node og_images.js <YWJMoney/- 폴더>
// [WEB-21] 공휴일은 해마다 그해 그림(holiday-<해>), 양식·레시피도 제 그림. 글꼴은 사이트와 같은 Pretendard(공개 저장소 assets/fonts).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(process.argv[2] || '../-');
const OUT = path.join(ROOT, 'og');
const hol = JSON.parse(fs.readFileSync(path.join(ROOT, 'remote/v1/holidays.json'), 'utf8')).data;
const this_ = new Date().getFullYear();
const md = (iso) => `${+iso.slice(5, 7)}/${+iso.slice(8)}`;
const items = {
  calc: ['근무·급여 무료 계산기', '주휴수당 · 실수령액 · 야간수당 · 연차 · 퇴직금 · 일당', ['주휴수당', '실수령액', '야간수당', '연차']],
  shift: ['교대 근무 달력 만들기', '4조2교대 · 3조2교대 · 주야비휴 · 24시간 맞교대', ['주', '야', '휴', '휴']],
  form: ['근무표 양식 무료', '간호사 듀티표 · 조별 근무표 · 알바 스케줄표 · 출역표', ['D', 'E', 'N', 'OFF']],
  recipe: ['밤낮 나눔 판 레시피', '근무 색 · 알람 · 메모 틀 · 규칙을 고른 것만', ['색', '알람', '메모', '규칙']],
};
// 공휴일 그림을 만들 해: build.py가 공휴일 쪽과 같은 해를 넘겨요(없으면 올해부터).
const years = process.argv[3] ? process.argv[3].split(',').map(Number) : Array.from({ length: hol.toYear - this_ + 1 }, (_, i) => this_ + i);
for (const y of years) {
  const days = hol.days.filter((d) => d.date.startsWith(String(y)));
  if (days.length) items[`holiday-${y}`] = [`${y}년 공휴일·연휴 달력`, `공휴일 ${days.length}일 · 교대 근무자, 공휴일에 쉬나 일하나`, days.slice(0, 4).map((d) => md(d.date))];
}
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 630 } });
  await p.goto('file://' + path.join(ROOT, 'assets/fonts/LICENSE.txt')); // 글꼴을 file:// 주소에서 읽게
  for (const [k, [t, sub, chips]] of Object.entries(items)) {
    await p.setContent(`<html><head><link rel="stylesheet" href="file://${path.join(ROOT, 'assets/fonts/pretendard.css')}"></head>
      <body style="margin:0;width:1200px;height:630px;background:#15192b;font-family:'Pretendard Variable',Pretendard,sans-serif;color:#f2ece1;display:flex;flex-direction:column;justify-content:center;padding:0 80px;box-sizing:border-box">
      <div style="font-size:30px;color:#e2c48a;font-weight:700;letter-spacing:2px">밤낮</div>
      <div style="font-size:72px;font-weight:800;margin:14px 0 18px;line-height:1.15;letter-spacing:-2px">${t}</div>
      <div style="font-size:32px;color:#cfd3e6">${sub}</div>
      <div style="display:flex;gap:16px;margin-top:44px">${chips.map((c, i) => `<div style="background:${['#2f63e0', '#6a4ce0', '#3a4366', '#3a4366'][i]};color:#fff;padding:14px 26px;border-radius:16px;font-size:30px;font-weight:700">${c}</div>`).join('')}</div>
      <div style="position:absolute;right:80px;bottom:44px;font-size:26px;color:#e2c48a">bamnat.app · 무료</div></body></html>`, { waitUntil: 'networkidle' });
    await p.evaluate(() => document.fonts.ready);
    await p.screenshot({ path: path.join(OUT, `${k}.png`) });
    console.log('og', k);
  }
  await b.close();
})();
