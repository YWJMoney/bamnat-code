// Store panels in the look of the 1.37 set: chip, big headline with gold words, a zoomed UI card bleeding off the
// bottom, a floating gold-ringed callout, sparkles and a gold orbit line. 1080x1920.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const dir = __dirname;
const cfg = JSON.parse(fs.readFileSync(path.join(dir, process.argv[2] || 'panels.json'), 'utf8'));
const outDir = path.join(dir, 'out');
fs.mkdirSync(outDir, { recursive: true });
const font = w => `url('file://${dir}/fonts/Pretendard-${w}.woff2') format('woff2')`;
const faces = [['Medium', 500], ['SemiBold', 600], ['Bold', 700], ['ExtraBold', 800]]
  .map(([n, w]) => `@font-face{font-family:P;font-weight:${w};src:${font(n)};}`).join('');

const star = (x, y, s, o, c) => `<svg style="position:absolute;left:${x - s / 2}px;top:${y - s / 2}px;opacity:${o}" width="${s}" height="${s}" viewBox="0 0 20 20"><path d="M10 0C10.8 6.2 13.8 9.2 20 10C13.8 10.8 10.8 13.8 10 20C9.2 13.8 6.2 10.8 0 10C6.2 9.2 9.2 6.2 10 0Z" fill="${c}"/></svg>`;
const dot = (x, y, r, o, c) => `<div style="position:absolute;left:${x}px;top:${y}px;width:${r}px;height:${r}px;border-radius:50%;background:${c};opacity:${o}"></div>`;

/** A piece of a raw screen [x,y,w,h] drawn [width] wide. */
function crop(img, [x, y, w, h], width, extra = '') {
  const s = width / w, file = path.resolve(dir, img);
  return `<div class="crop" style="width:${width}px;height:${Math.round(h * s)}px;${extra}"><img src="file://${file}" style="width:${Math.round(p_w(file) * s)}px;margin-left:${-Math.round(x * s)}px;margin-top:${-Math.round(y * s)}px"></div>`;
}
const widths = {};
function p_w(file) { return widths[file]; }

function html(p) {
  const night = p.theme !== 'day';
  const fg = night ? '#FFFFFF' : '#1A1E33';
  const sub = night ? 'rgba(255,255,255,.78)' : 'rgba(26,30,51,.72)';
  const bg = night
    ? `radial-gradient(900px 700px at 50% 108%, rgba(120,110,190,.45), rgba(0,0,0,0) 70%), linear-gradient(180deg,#121731 0%,#1A2045 55%,#262A57 100%)`
    : `radial-gradient(800px 700px at 72% 18%, #FFE6BF 0%, rgba(255,230,191,0) 70%), radial-gradient(900px 800px at 10% 100%, #F0DDBB 0%, rgba(240,221,187,0) 70%), linear-gradient(180deg,#FBF4E8,#F3E7D3)`;
  const gold = 'linear-gradient(180deg,#F3D9A0 0%,#D7AE62 55%,#B98B3E 100%)';
  const goldDay = 'linear-gradient(180deg,#D9A74E 0%,#B98330 60%,#9C6A22 100%)';
  const chipCss = night
    ? 'background:rgba(196,160,96,.18);color:#E9C987;box-shadow:0 0 0 1.5px rgba(214,178,110,.45) inset'
    : 'background:rgba(214,170,96,.20);color:#9A6B22;box-shadow:0 0 0 1.5px rgba(186,140,62,.35) inset';
  const head = p.title.replace(/<g>/g, '<b class="g">').replace(/<\/g>/g, '</b>').split('\n').join('<br>');
  const starC = night ? '#F4DFAE' : '#D2A85C';
  const deco = (p.stars || (night
    ? [[70, 260, 30, .9], [990, 420, 22, .8], [120, 980, 26, .7], [1010, 1500, 18, .6], [930, 120, 14, .6]]
    : [[80, 300, 26, .7], [1000, 450, 20, .6], [60, 1300, 18, .5]]))
    .map(([x, y, s, o]) => star(x, y, s, o, starC)).join('') +
    (night ? [[40, 150], [210, 70], [860, 260], [1040, 640], [30, 640], [700, 90], [480, 560], [980, 900], [60, 1650], [150, 470]]
      .map(([x, y], i) => dot(x, y, i % 3 ? 3 : 4, .55, '#FFFFFF')).join('') : '');
  const orbitY = p.orbitY || 1150;
  const orbit = `<svg class="orbit" width="1080" height="1920" viewBox="0 0 1080 1920"><path d="M-40 ${orbitY + 120} C 300 ${orbitY - 40}, 760 ${orbitY - 70}, 1120 ${orbitY - 160}" fill="none" stroke="${night ? '#D9B373' : '#C69A4E'}" stroke-opacity="${night ? .55 : .5}" stroke-width="2.4"/>
    <circle cx="14" cy="${orbitY + 98}" r="9" fill="${night ? '#E5C27F' : '#C69A4E'}" opacity=".9"/><circle cx="1050" cy="${orbitY - 142}" r="8" fill="${night ? '#E5C27F' : '#C69A4E'}" opacity=".9"/></svg>`;
  const main = p.main;
  const mainHtml = `<div class="card" style="left:${(1080 - main.width) / 2}px;top:${main.top}px">${crop(p.image, main.crop, main.width)}</div>`;
  const pops = (p.pops || []).map(o => {
    const img = o.image || p.image;
    if (o.inplace) {
      const s = main.width / main.crop[2], k = o.scale || 1.08;
      const w0 = o.crop[2] * s, h0 = o.crop[3] * s;
      const l0 = (1080 - main.width) / 2 + (o.crop[0] - main.crop[0]) * s, t0 = main.top + (o.crop[1] - main.crop[1]) * s;
      o.width = Math.round(w0 * k); o.left = Math.round(l0 - (w0 * k - w0) / 2 + (o.dx || 0)); o.top = Math.round(t0 - (h0 * k - h0) / 2 + (o.dy || 0));
    }
    return `<div class="pop" style="left:${o.left}px;top:${o.top}px;${o.rotate ? `transform:rotate(${o.rotate}deg);` : ''}">${o.tag ? `<div class="tag">${o.tag}</div>` : ''}${crop(img, o.crop, o.width)}</div>`;
  }).join('');
  const badges = (p.badges || []).map(b => `<div class="badge" style="left:${b.left}px;top:${b.top}px;${b.rotate ? `transform:rotate(${b.rotate}deg);` : ''}"><i>${b.icon || '<svg width="26" height="26" viewBox="0 0 24 24"><path d="M5 12.5l4.2 4.2L19 7" fill="none" stroke="#1A1E33" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/></svg>'}</i>${b.text}</div>`).join('');
  return `<!doctype html><html><head><meta charset="utf-8"><style>${faces}
  *{box-sizing:border-box;margin:0;padding:0}html,body{width:1080px;height:1920px;overflow:hidden}
  body{font-family:P,sans-serif;background:${bg};color:${fg};position:relative;-webkit-font-smoothing:antialiased;word-break:keep-all}
  .top{position:absolute;left:0;right:0;top:${p.textTop || 150}px;text-align:center;padding:0 60px;z-index:3}
  .chip{display:inline-block;padding:13px 30px;border-radius:999px;font-weight:700;font-size:31px;letter-spacing:-.01em;${chipCss}}
  h1{margin-top:40px;font-weight:800;font-size:${p.titleSize || 104}px;line-height:1.16;letter-spacing:-.035em}
  .g{font-weight:800;background:${night ? gold : goldDay};-webkit-background-clip:text;background-clip:text;color:transparent}
  p.s{margin-top:34px;font-weight:600;font-size:38px;line-height:1.42;color:${sub};letter-spacing:-.02em}
  .orbit{position:absolute;left:0;top:0;z-index:1}
  .card{position:absolute;z-index:2;border-radius:52px 52px 0 0;overflow:hidden;background:#fff;box-shadow:0 40px 90px rgba(10,12,30,${night ? .55 : .22}),0 0 0 1px rgba(0,0,0,.04)}
  .crop{overflow:hidden;position:relative}.crop img{display:block}
  .pop{position:absolute;z-index:4;border-radius:34px;overflow:hidden;background:#fff;
    box-shadow:0 0 0 5px #E3C07E,0 0 0 9px rgba(227,192,126,.28),0 30px 70px rgba(10,12,30,.45),0 0 60px rgba(240,200,120,.45)}
  .badge{position:absolute;z-index:6;display:flex;align-items:center;gap:14px;padding:18px 30px 18px 20px;border-radius:999px;background:rgba(255,255,255,.96);color:#1A1E33;font-weight:800;font-size:36px;letter-spacing:-.02em;box-shadow:0 18px 40px rgba(10,12,30,.35),0 0 0 3px #E3C07E}
  .badge i{font-style:normal;width:52px;height:52px;border-radius:50%;background:linear-gradient(180deg,#F3D9A0,#C99A4C);color:#1A1E33;display:flex;align-items:center;justify-content:center;font-size:28px}
  .tag{position:absolute;right:18px;top:16px;z-index:5;background:linear-gradient(180deg,#F3D9A0,#C99A4C);color:#1A1E33;font-weight:800;font-size:28px;padding:8px 22px;border-radius:999px}
  </style></head><body>${deco}${orbit}
  <div class="top"><span class="chip">${p.label}</span><h1>${head}</h1>${p.sub ? `<p class="s">${p.sub.split('\n').join('<br>')}</p>` : ''}</div>
  ${mainHtml}${pops}${badges}</body></html>`;
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  const only = process.argv[3];
  for (const p of cfg.panels) {
    if (only && p.name !== only) continue;
    for (const im of [p.image, ...(p.pops || []).map(o => o.image).filter(Boolean)]) {
      const f = path.resolve(dir, im);
      if (!widths[f]) widths[f] = JSON.parse(require('child_process').execSync(`python3 -c "from PIL import Image;print(Image.open('${f}').size[0])"`).toString());
    }
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
