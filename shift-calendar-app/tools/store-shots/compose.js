// Store screenshot composer: one 1080x1920 panel per entry in panels.json, app screen inside a phone frame.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const dir = __dirname;
const cfg = JSON.parse(fs.readFileSync(path.join(dir, process.argv[2] || 'panels.json'), 'utf8'));
const outDir = path.join(dir, 'out');
fs.mkdirSync(outDir, { recursive: true });

const font = w => `url('file://${dir}/fonts/Pretendard-${w}.woff2') format('woff2')`;
const faces = [['Regular', 400], ['Medium', 500], ['SemiBold', 600], ['Bold', 700], ['ExtraBold', 800]]
  .map(([n, w]) => `@font-face{font-family:P;font-weight:${w};src:${font(n)};}`).join('');

// The 밤낮 dial (brand/v2): 24 ticks, ivory day half, open night half, a gold dot.
function dial(size, ivory, gold) {
  let ticks = '';
  for (let i = 0; i < 24; i++) {
    const a = (i * 15 - 90) * Math.PI / 180, major = i % 6 === 0;
    const r1 = 26.66, r2 = major ? 30.97 : 29.4;
    ticks += `<line x1="${54 + r1 * Math.cos(a)}" y1="${54 + r1 * Math.sin(a)}" x2="${54 + r2 * Math.cos(a)}" y2="${54 + r2 * Math.sin(a)}" stroke="${ivory}" stroke-opacity="${major ? 1 : .5}" stroke-width="${major ? .98 : .59}" stroke-linecap="round"/>`;
  }
  return `<svg width="${size}" height="${size}" viewBox="20 20 68 68">${ticks}
    <circle cx="54" cy="54" r="21.95" fill="none" stroke="${ivory}" stroke-width=".98"/>
    <path d="M32.05,54a21.95,21.95 0,0 1,43.9,0z" fill="${ivory}"/>
    <circle cx="54" cy="64.58" r="1.57" fill="${gold}"/></svg>`;
}

function html(p) {
  const night = p.theme !== 'day';
  const ink = '#15192B', ivory = '#F2ECE1', gold = '#C2A36B';
  const fg = night ? ivory : ink;
  const sub = night ? 'rgba(242,236,225,.72)' : 'rgba(21,25,43,.66)';
  const bg = night
    ? `radial-gradient(1200px 900px at 85% 8%, #33407A 0%, rgba(51,64,122,0) 60%), radial-gradient(900px 700px at 0% 100%, #2A2347 0%, rgba(42,35,71,0) 70%), linear-gradient(180deg, #1A1F36 0%, ${ink} 100%)`
    : `radial-gradient(1100px 800px at 90% 0%, #FFF7E6 0%, rgba(255,247,230,0) 60%), radial-gradient(900px 700px at 0% 100%, #E6D8BE 0%, rgba(230,216,190,0) 70%), linear-gradient(180deg, #F6F1E8 0%, #EDE4D4 100%)`;
  const chipBg = night ? 'rgba(194,163,107,.16)' : 'rgba(194,163,107,.22)';
  const chipFg = night ? '#D9BE86' : '#8A6D35';
  const glow = night ? 'rgba(90,110,220,.35)' : 'rgba(150,120,60,.28)';
  const phoneTop = p.phoneTop || 640;
  const head = p.title.split('\n').map(s => `<span>${s}</span>`).join('<br>');
  const subT = (p.sub || '').split('\n').join('<br>');
  const ring = night ? `<div class="ring">${dial(760, 'rgba(242,236,225,.07)', 'rgba(0,0,0,0)')}</div>` : `<div class="ring">${dial(760, 'rgba(21,25,43,.05)', 'rgba(0,0,0,0)')}</div>`;
  return `<!doctype html><html><head><meta charset="utf-8"><style>${faces}
  *{box-sizing:border-box;margin:0;padding:0}
  html,body{width:1080px;height:1920px;overflow:hidden}
  body{font-family:P,sans-serif;background:${bg};color:${fg};position:relative;-webkit-font-smoothing:antialiased;word-break:keep-all}
  .ring{position:absolute;right:-250px;top:-230px;opacity:1}
  .top{position:absolute;left:0;right:0;top:${p.textTop || 132}px;text-align:center;padding:0 84px}
  .chip{display:inline-flex;align-items:center;gap:12px;padding:12px 26px;border-radius:999px;background:${chipBg};color:${chipFg};font-weight:700;font-size:30px;letter-spacing:.02em}
  .chip i{width:10px;height:10px;border-radius:50%;background:${gold};display:inline-block}
  h1{margin-top:34px;font-weight:800;font-size:${p.titleSize || 86}px;line-height:1.17;letter-spacing:-.025em}
  h1 em{font-style:normal;color:${night ? '#E2C690' : '#A27C35'}}
  p{margin-top:28px;font-weight:500;font-size:36px;line-height:1.45;color:${sub};letter-spacing:-.01em}
  .glow{position:absolute;left:50%;top:${phoneTop + 120}px;width:900px;height:900px;transform:translateX(-50%);border-radius:50%;background:radial-gradient(closest-side, ${glow}, rgba(0,0,0,0));filter:blur(10px)}
  .phone{position:absolute;left:50%;top:${phoneTop}px;width:800px;height:1720px;transform:translateX(-50%);border-radius:104px;background:linear-gradient(160deg,#2C3148,#0A0C14 40%,#151827);padding:20px;
    box-shadow:0 60px 120px rgba(5,7,15,${night ? .55 : .28}),0 0 0 2px rgba(255,255,255,${night ? .10 : .5}) inset}
  .screen{width:100%;height:100%;border-radius:86px;overflow:hidden;background:#000;position:relative}
  .screen img{width:100%;display:block;${p.offset ? `margin-top:-${p.offset}px;` : ''}}
  .bar{height:58px;background:${p.bar || '#fff'};display:flex;align-items:flex-end;justify-content:space-between;padding:0 50px 6px 58px;color:${p.barInk || '#1b1b1f'};font-weight:600;font-size:24px;position:relative;z-index:2}
  .bar svg{display:block}
  .cam{position:absolute;top:22px;left:50%;width:30px;height:30px;margin-left:-15px;border-radius:50%;background:#05060A;box-shadow:0 0 0 3px rgba(255,255,255,.04)}
  .brand{position:absolute;left:0;right:0;bottom:56px;display:flex;justify-content:center;align-items:center;gap:16px;color:${fg};font-weight:800;font-size:40px}
  </style></head><body>${ring}
  <div class="top"><div class="chip"><i></i>${p.label}</div><h1>${head}</h1>${p.sub ? `<p>${subT}</p>` : ''}</div>
  <div class="glow"></div>
  <div class="phone"><div class="screen"><div class="bar"><span>10:00</span><span style="display:flex;gap:10px;align-items:center"><svg width="26" height="20" viewBox="0 0 26 20"><path d="M13 19L0 5.5C3.6 2.2 8.1 0.5 13 0.5s9.4 1.7 13 5z" fill="currentColor"/></svg><svg width="22" height="22" viewBox="0 0 22 22"><path d="M21 1v20H1z" fill="currentColor"/></svg><svg width="14" height="24" viewBox="0 0 14 24"><rect x="4" y="0" width="6" height="3" rx="1" fill="currentColor"/><rect x="0" y="2.5" width="14" height="21.5" rx="2.5" fill="currentColor"/></svg></span></div><img src="file://${path.resolve(dir, p.image)}"><div class="cam"></div></div></div>
  </body></html>`;
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: cfg.width || 1080, height: cfg.height || 1920 }, deviceScaleFactor: 1 });
  for (const p of cfg.panels) {
    if (p.skip) continue;
    const file = path.join(outDir, `${p.name}.html`);
    fs.writeFileSync(file, html(p));
    await page.goto('file://' + file);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(150);
    await page.screenshot({ path: path.join(outDir, `${p.name}.png`) });
    console.log('wrote', p.name);
  }
  await browser.close();
})();
