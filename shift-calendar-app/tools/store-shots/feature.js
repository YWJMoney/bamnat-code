// Play feature graphic 1024x500: brand on the left, two phones on the right.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const dir = __dirname;
const [left, right] = process.argv.slice(2);
const font = w => `url('file://${dir}/fonts/Pretendard-${w}.woff2') format('woff2')`;
const faces = [['Medium', 500], ['Bold', 700], ['ExtraBold', 800]].map(([n, w]) => `@font-face{font-family:P;font-weight:${w};src:${font(n)};}`).join('');
let ticks = '';
for (let i = 0; i < 24; i++) {
  const a = (i * 15 - 90) * Math.PI / 180, major = i % 6 === 0, r1 = 26.66, r2 = major ? 30.97 : 29.4;
  ticks += `<line x1="${54 + r1 * Math.cos(a)}" y1="${54 + r1 * Math.sin(a)}" x2="${54 + r2 * Math.cos(a)}" y2="${54 + r2 * Math.sin(a)}" stroke="#F2ECE1" stroke-opacity="${major ? 1 : .5}" stroke-width="${major ? .98 : .59}" stroke-linecap="round"/>`;
}
const logo = `<svg width="96" height="96" viewBox="20 20 68 68">${ticks}<circle cx="54" cy="54" r="21.95" fill="none" stroke="#F2ECE1" stroke-width=".98"/><path d="M32.05,54a21.95,21.95 0,0 1,43.9,0z" fill="#F2ECE1"/><circle cx="54" cy="64.58" r="1.57" fill="#C2A36B"/></svg>`;
const phone = (img, x, y, rot, z) => `<div class="ph" style="left:${x}px;top:${y}px;transform:rotate(${rot}deg);z-index:${z}"><div class="sc"><img src="file://${path.resolve(dir, img)}"></div></div>`;
const html = `<!doctype html><html><head><meta charset="utf-8"><style>${faces}
*{margin:0;padding:0;box-sizing:border-box}html,body{width:1024px;height:500px;overflow:hidden}
body{font-family:P,sans-serif;color:#F2ECE1;word-break:keep-all;position:relative;
 background:radial-gradient(700px 500px at 80% 10%,#34417C 0%,rgba(52,65,124,0) 65%),radial-gradient(600px 400px at 10% 110%,#2B2449 0%,rgba(43,36,73,0) 70%),linear-gradient(135deg,#1B2038,#15192B)}
.l{position:absolute;left:72px;top:0;bottom:0;display:flex;flex-direction:column;justify-content:center;width:470px}
.row{display:flex;align-items:center;gap:20px}
.name{font-weight:800;font-size:78px;letter-spacing:-.03em}
.tag{margin-top:26px;font-weight:700;font-size:32px;line-height:1.35;letter-spacing:-.02em}
.tag em{font-style:normal;color:#E2C690}
.sub{margin-top:16px;font-weight:500;font-size:21px;color:rgba(242,236,225,.68)}
.ph{position:absolute;width:236px;height:520px;border-radius:36px;padding:8px;background:linear-gradient(160deg,#2C3148,#0A0C14 45%,#151827);box-shadow:0 30px 60px rgba(0,0,0,.5),0 0 0 1.5px rgba(255,255,255,.1) inset}
.sc{width:100%;height:100%;border-radius:29px;overflow:hidden;background:#fff}.sc img{width:100%;display:block}
</style></head><body>
<div class="l"><div class="row">${logo}<div class="name">밤낮</div></div>
<div class="tag">근무표·<em>근무 알람</em>·급여부터<br><em>팀·직원 근무표</em>까지 한 앱에서</div>
<div class="sub">교대 근무자 · 팀 리더 · 사장님 모두</div></div>
${phone(right, 790, 70, 6, 1)}${phone(left, 590, 40, -4, 2)}
</body></html>`;
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1024, height: 500 }, deviceScaleFactor: 1 });
  const f = path.join(dir, 'out', 'feature.html');
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, html);
  await p.goto('file://' + f);
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(150);
  await p.screenshot({ path: path.join(dir, 'out', 'feature.png') });
  await b.close();
})();
