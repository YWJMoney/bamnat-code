// 밤낮 색 아이콘 200: set1~3의 그림을 Chromium(Playwright)으로 256px 투명 PNG로 그려요. 쓰는 법은 ../make.py.
import { createRequire } from 'module'; import fs from 'fs';
import { reset, wrap } from './kit.mjs';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PWROOT + '/playwright');
const sets = process.argv[2].split(',');
const all = [];
for (const s of sets) { const m = await import('./' + s + '.mjs'); all.push(...Object.values(m)[0]); }
const svgs = all.map(([id, name, group, fn]) => { reset(); const raw = wrap(fn()); const svg = raw.replaceAll('id="', `id="${id}_`).replaceAll('url(#', `url(#${id}_`); return { id, name, group, svg }; });
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1400, height: 400 }, deviceScaleFactor: 1 });
const cells = svgs.map(s => `<div style="width:100px;text-align:center;font:11px sans-serif"><div style="width:84px;height:84px;margin:auto">${s.svg.replace('<svg ', '<svg width="84" height="84" ')}</div>${s.id}</div>`).join('');
await p.setContent(`<body style="margin:0"><div id=w style="display:flex;flex-wrap:wrap;gap:6px;background:#fff;padding:8px;width:1340px">${cells}</div><div style="display:flex;flex-wrap:wrap;gap:6px;background:#1d1830;color:#ccc;padding:8px;width:1340px">${cells}</div></body>`);
await p.screenshot({ path: process.argv[3], fullPage: true });
// 개별 PNG(투명 256)
if (process.argv[4]) {
  fs.mkdirSync(process.argv[4], { recursive: true });
  const q = await b.newPage({ viewport: { width: 256, height: 256 }, deviceScaleFactor: 1 });
  for (const s of svgs) {
    await q.setContent(`<html><body style="margin:0;background:transparent">${s.svg.replace('<svg ', '<svg width="256" height="256" ')}</body></html>`);
    await q.screenshot({ path: `${process.argv[4]}/${s.id}.png`, omitBackground: true, clip: { x: 0, y: 0, width: 256, height: 256 } });
  }
}
fs.writeFileSync(process.argv[5] ?? 'icons.json', JSON.stringify(svgs.map(({ id, name, group }) => ({ id, name, group })), null, 0));
await b.close(); console.log(svgs.length);
