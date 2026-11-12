// 공개 사이트 쪽 점검과 화면 사진 (2.0.0 갈래 R).
// 쓰는 법: node check_pages.js <만든 사이트 폴더> [사진 폴더] [--shots 경로,경로] [--all] [--widths 380,840,1440] [--print 경로,경로]
//  - sitemap.xml의 모든 쪽을 폭마다(기본 380·840·1440) 밝은·어두운 모드로 열어 가로 넘침, 콘솔 오류, 바깥 요청(모두 막고 셈),
//    숫자 가운데 줄바꿈(다이얼·큰 숫자), 접힌 메뉴의 초점 받는 링크를 봐요. 문제가 하나라도 있으면 끝 코드 1.
//  - 사진 폴더를 주면 --shots 쪽(없으면 대표 쪽)을 쪽 전체 사진으로 남겨요(--all이면 모든 쪽). --print 쪽은 A4 PDF도 남겨요.
// 서버로 보내는 것은 없고, 바깥 요청은 모두 막아요.
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const args = process.argv.slice(2);
const ROOT = path.resolve(args[0] || '../-');
const OUT = args[1] && !args[1].startsWith('--') ? path.resolve(args[1]) : null;
const opt = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : d; };
const WIDTHS = opt('--widths', '380,840,1440').split(',').map(Number);
const ALL = args.includes('--all');
const REPRESENT = ['', 'calc/', 'calc/salary/', 'calc/alba/', 'calc/daily/', 'calc/juhyu/', 'calc/allowance/', 'calc/leave/', 'calc/severance/',
  'shift/', 'shift/print/', 'shift/print/3j2k/', 'form/', 'form/duty/', 'holiday/', 'holiday/2027/', 'r/?c=ABCD2345', 'r/'];
const SHOTS = (opt('--shots', '') || '').split(',').filter(Boolean);
const PRINTS = (opt('--print', '') || '').split(',').filter(Boolean);

const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.json': 'application/json', '.svg': 'image/svg+xml',
  '.webp': 'image/webp', '.png': 'image/png', '.woff2': 'font/woff2', '.xml': 'application/xml', '.txt': 'text/plain', '.xlsx': 'application/octet-stream' };
function serve() {
  return new Promise((ok) => {
    const s = http.createServer((req, res) => {
      let p = decodeURIComponent(req.url.split('?')[0]);
      if (p.endsWith('/')) p += 'index.html';
      const f = path.join(ROOT, p);
      if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); res.end('404'); return; }
      res.writeHead(200, { 'content-type': TYPES[path.extname(f)] || 'application/octet-stream' });
      fs.createReadStream(f).pipe(res);
    });
    s.listen(0, '127.0.0.1', () => ok(s));
  });
}

function pagesFromSitemap() {
  const sm = fs.readFileSync(path.join(ROOT, 'sitemap.xml'), 'utf8');
  return [...sm.matchAll(/<loc>https:\/\/bamnat\.app\/(.*?)<\/loc>/g)].map((m) => m[1]).filter((u) => !/^(privacy|terms|community)\//.test(u));
}

(async () => {
  const server = await serve();
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch();
  const pages = [...new Set([...pagesFromSitemap(), 'r/?c=ABCD2345', 'r/'])];
  const shotSet = new Set(ALL ? pages : (SHOTS.length ? SHOTS : REPRESENT));
  const problems = [];
  const external = {};
  let opened = 0;
  if (OUT) fs.mkdirSync(OUT, { recursive: true });
  for (const scheme of ['light', 'dark']) {
    for (const w of WIDTHS) {
      const ctx = await browser.newContext({ viewport: { width: w, height: 900 }, colorScheme: scheme, deviceScaleFactor: 1, locale: 'ko-KR', timezoneId: 'Asia/Seoul' });
      await ctx.route('**/*', (r) => {
        const u = r.request().url();
        if (u.startsWith(base)) return r.continue();
        external[u] = (external[u] || 0) + 1; return r.abort();
      });
      for (const p of pages) {
        const page = await ctx.newPage();
        const errs = [];
        page.on('console', (m) => { if (m.type() === 'error' && !/net::ERR_FAILED|Failed to load resource/.test(m.text())) errs.push(m.text()); });
        page.on('pageerror', (e) => errs.push(String(e)));
        await page.goto(base + p, { waitUntil: 'load' });
        await page.waitForTimeout(350);
        opened++;
        // 첫 화면 다이얼은 화면에 들어와야 그려져요: 주간·야간·쉬는 날을 차례로 눌러 가운데 글을 봐요.
        const dialSplit = [];
        if (await page.$('#dial')) {
          await page.$eval('#dial', (d) => d.scrollIntoView({ block: 'center' }));
          await page.waitForTimeout(500);
          for (const b of await page.$$('.modes button')) {
            await b.click(); await page.waitForTimeout(120);
            // 큰 글(시각·'알람 없음')은 한 줄, 작은 설명은 두 줄까지. 넘으면 "19:00 / 출근", "6시간 / 반"처럼 갈려 읽혀요.
            dialSplit.push(...await page.evaluate(() => [...document.querySelectorAll('.dial .center strong, .dial .center span')].filter((el) => {
              if (!el.textContent.trim()) return false;
              const range = document.createRange(); range.selectNodeContents(el);
              const lines = new Set([...range.getClientRects()].map((x) => Math.round(x.top))).size;
              return lines > (el.tagName === 'STRONG' ? 1 : 2);
            }).map((el) => `${el.tagName === 'STRONG' ? '큰 글' : '설명'} "${el.textContent.trim()}"`)));
          }
          await page.evaluate(() => window.scrollTo(0, 0));
        }
        const r = await page.evaluate(() => {
          const out = { overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth, split: [], menuFocus: 0 };
          // 숫자 가운데에서 줄이 바뀐 큰 글자(예: "19:0 / 0")
          document.querySelectorAll('.big, .dial .center strong, .hero h1').forEach((el) => {
            const t = el.textContent.trim();
            if (!t || !/\d/.test(t)) return;
            const range = document.createRange(); range.selectNodeContents(el);
            const lines = new Set([...range.getClientRects()].map((x) => Math.round(x.top)));
            if (lines.size > 1 && /\d[:,]?\d/.test(t) && t.length <= 14) out.split.push(t);
          });
          // 접힌 휴대폰 메뉴의 링크가 초점을 받는지(보이지 않는데 Tab으로 지나감)
          const menu = document.querySelector('.site-header .menu');
          const head = document.querySelector('.site-header');
          if (menu && head && !head.classList.contains('open') && getComputedStyle(menu).display !== 'none') {
            menu.querySelectorAll('a').forEach((a) => {
              const cs = getComputedStyle(a);
              if (cs.visibility !== 'hidden' && !a.closest('[inert]') && a.tabIndex >= 0 && a.getBoundingClientRect().height === 0) out.menuFocus++;
              else if (cs.visibility !== 'hidden' && !a.closest('[inert]') && a.tabIndex >= 0 && a.closest('.menu > div').getBoundingClientRect().height < 2) out.menuFocus++;
            });
          }
          return out;
        });
        const tag = `${p || '/'} ${w} ${scheme}`;
        if (r.overflow > 0) problems.push(`${tag}: 가로 넘침 ${r.overflow}px`);
        if (r.split.length) problems.push(`${tag}: 숫자 가운데 줄바꿈 ${JSON.stringify(r.split)}`);
        if (dialSplit.length) problems.push(`${tag}: 다이얼 가운데 글이 너무 여러 줄 ${JSON.stringify([...new Set(dialSplit)])}`);
        if (r.menuFocus) problems.push(`${tag}: 접힌 메뉴 링크 ${r.menuFocus}개가 초점을 받음`);
        if (errs.length) problems.push(`${tag}: 콘솔 오류 ${JSON.stringify(errs.slice(0, 3))}`);
        if (OUT && shotSet.has(p)) {
          const name = (p.replace(/[/?=]+/g, '_').replace(/_$/, '') || 'home') + `-${w}-${scheme}.png`;
          await page.evaluate(() => { document.querySelectorAll('.reveal').forEach((e) => e.classList.add('in')); });
          await page.waitForTimeout(150);
          await page.screenshot({ path: path.join(OUT, name), fullPage: true });
        }
        if (OUT && PRINTS.includes(p) && scheme === 'light') {
          await page.emulateMedia({ media: 'print' });
          await page.pdf({ path: path.join(OUT, (p.replace(/[/?=]+/g, '_').replace(/_$/, '') || 'home') + `-${w}.pdf`), format: 'A4', landscape: true, printBackground: true });
          await page.emulateMedia({ media: 'screen' });
        }
        await page.close();
      }
      await ctx.close();
    }
  }
  await browser.close();
  server.close();
  const ext = Object.keys(external);
  console.log(`열어 본 횟수 ${opened} (쪽 ${pages.length} × 폭 ${WIDTHS.length} × 밝은·어두운)`);
  console.log(`바깥 요청(모두 막음): ${ext.length ? ext.join(', ') : '없음'}`);
  if (problems.length) { console.log(`문제 ${problems.length}개`); problems.forEach((x) => console.log(' - ' + x)); process.exit(1); }
  console.log('문제 없음');
})();
