const { chromium } = require('playwright');
const OUT = (process.argv[2] || '../-') + '/og/'; // YWJMoney/- 저장소를 받아 둔 곳
const items = {
  calc: ['근무·급여 무료 계산기', '주휴수당 · 실수령액 · 야간수당 · 연차 · 퇴직금 · 일당', ['주휴수당', '실수령액', '야간수당', '연차']],
  shift: ['교대 근무 달력 만들기', '4조2교대 · 3조2교대 · 주야비휴 · 24시간 맞교대', ['주', '야', '휴', '휴']],
  holiday: ['2027년 공휴일·연휴 달력', '교대 근무자, 공휴일에 쉬나 일하나 바로 확인', ['1/1', '2/7', '3/1', '5/5']],
};
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 630 } });
  for (const [k, [t, sub, chips]] of Object.entries(items)) {
    await p.setContent(`<html><body style="margin:0;width:1200px;height:630px;background:#1d2440;font-family:'Noto Sans CJK KR','Noto Sans KR',sans-serif;color:#f6f1e4;display:flex;flex-direction:column;justify-content:center;padding:0 80px;box-sizing:border-box">
      <div style="font-size:30px;color:#e3b65b;font-weight:700;letter-spacing:2px">밤낮</div>
      <div style="font-size:74px;font-weight:800;margin:14px 0 18px;line-height:1.15">${t}</div>
      <div style="font-size:32px;color:#cfd3e6">${sub}</div>
      <div style="display:flex;gap:16px;margin-top:44px">${chips.map((c, i) => `<div style="background:${['#2f80ed','#5b3fd1','#3a4366','#3a4366'][i]};padding:14px 26px;border-radius:16px;font-size:30px;font-weight:700">${c}</div>`).join('')}</div>
      <div style="position:absolute;right:80px;bottom:44px;font-size:26px;color:#e3b65b">bamnat.app · 무료</div></body></html>`);
    await p.screenshot({ path: OUT + `${k}.png` });
  }
  await b.close();
})();
