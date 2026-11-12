// 홈 화면 아이콘 [I50c-02]: 공개 저장소의 assets/icon.svg(로고 다이얼)를 PNG로. 아이폰은 SVG 아이콘을 쓰지 않고 투명한 모서리를
// 검게 채우니 꽉 찬 네모(180), Android 홈 화면은 둥근 네모(192·512)와 '마스크용'(안전 원 80% 안에 다이얼을 줄여 넣음, 512).
// 돌리기: node app_icons.js <YWJMoney/- 폴더>  → static/assets/icon-*.png (build.py가 공개 폴더로 옮겨요)
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const site = process.argv[2];
if (!site) { console.error('쓰는 법: node app_icons.js <YWJMoney/- 폴더>'); process.exit(2); }
const svg = fs.readFileSync(path.join(site, 'assets/icon.svg'), 'utf8');
const square = svg.replace(/<rect width="64" height="64" rx="14"/, '<rect width="64" height="64" rx="0"');
if (square === svg) throw new Error('icon.svg 바탕 네모를 못 찾았어요');
const inner = square.replace(/^<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '');
const maskable = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" fill="#15192b"/><g transform="translate(32 32) scale(0.74) translate(-32 -32)">${inner}</g></svg>`;
const out = path.join(__dirname, 'static/assets');
const jobs = [['icon-180.png', 180, square], ['icon-192.png', 192, svg], ['icon-512.png', 512, svg], ['icon-maskable-512.png', 512, maskable]];
(async () => {
  const b = await chromium.launch();
  for (const [name, size, src] of jobs) {
    const p = await b.newPage({ viewport: { width: size, height: size } });
    await p.setContent(`<html><body style="margin:0;background:transparent"><img style="display:block;width:${size}px;height:${size}px" src="data:image/svg+xml;base64,${Buffer.from(src).toString('base64')}"></body></html>`);
    await p.screenshot({ path: path.join(out, name), omitBackground: true });
    await p.close();
    console.log(name);
  }
  await b.close();
})();
