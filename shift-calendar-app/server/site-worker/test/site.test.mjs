// [SITE-01] `node --test test/site.test.mjs`
import { test } from "node:test";
import assert from "node:assert/strict";
import site from "../src/index.js";

const seen = [];
const GITHUB = { server: "GitHub.com", via: "1.1 varnish", "x-github-request-id": "ABCD:1234", "access-control-allow-origin": "*", "x-served-by": "cache-icn",
  "x-cache": "HIT", "x-cache-hits": "1", "x-timer": "S1", "x-fastly-request-id": "f1", "x-proxy-cache": "MISS", "cache-control": "max-age=600", etag: '"abc"' };
globalThis.fetch = async (url, init) => {
  seen.push([url, init.redirect, init.method]);
  if (url.endsWith("/-/terms")) return new Response(null, { status: 301, headers: { ...GITHUB, location: "https://ywjmoney.github.io/-/terms/" } });
  if (url.endsWith("/-/nope/") || url.includes("/-/no-such-page")) return new Response("<h1>404 There isn't a GitHub Pages site here.</h1>", { status: 404, headers: { ...GITHUB, "content-type": "text/html" } });
  return new Response(init.method === "HEAD" ? null : `page ${url}`, { status: 200, headers: { ...GITHUB, "content-type": "text/html" } });
};

test("bamnat.app shows the Pages site without leaving bamnat.app", async () => {
  const page = await site.fetch(new Request("https://bamnat.app/terms/?x=1"));
  assert.equal(page.status, 200);
  // 2.0.0 [RVN-24] SVC-110: the query stays at bamnat.app (one cached copy a page).
  assert.equal(await page.text(), "page https://ywjmoney.github.io/-/terms/");
  assert.equal(seen.at(-1)[1], "manual");
  const slash = await site.fetch(new Request("https://bamnat.app/terms"));
  assert.equal(slash.status, 301);
  assert.equal(slash.headers.get("location"), "https://bamnat.app/terms/");
  assert.equal((await site.fetch(new Request("https://bamnat.app/"))).status, 200);
  assert.equal(seen.at(-1)[0], "https://ywjmoney.github.io/-/");
});

test("www goes to bamnat.app; another app's policy and writes are not passed on", async () => {
  const www = await site.fetch(new Request("https://www.bamnat.app/privacy/"));
  assert.deepEqual([www.status, www.headers.get("location")], [301, "https://bamnat.app/privacy/"]);
  const other = await site.fetch(new Request("https://bamnat.app/privacy.html"));
  assert.equal(other.headers.get("location"), "https://bamnat.app/privacy/");
  assert.equal((await site.fetch(new Request("https://bamnat.app/", { method: "POST", body: "x" }))).status, 405);
});

test("app-ads.txt comes from the share server, not Pages", async () => {
  const ads = await site.fetch(new Request("https://bamnat.app/app-ads.txt"));
  assert.equal(ads.status, 200);
  assert.equal(seen.at(-1)[0], "https://s.bamnat.app/app-ads.txt");
  assert.match(ads.headers.get("content-type"), /text\/plain/);
});

test("[RVN-24] SVC-110 no query upstream, GitHub's own headers dropped, one canonical address, HEAD and 404 the same way", async () => {
  for (const q of ["?x=1", "?x=2", "?utm_source=bamnat_web"]) {
    const r = await site.fetch(new Request(`https://bamnat.app/calc/${q}`));
    assert.equal(r.status, 200);
    assert.equal(seen.at(-1)[0], "https://ywjmoney.github.io/-/calc/");
    assert.equal(r.headers.get("link"), '<https://bamnat.app/calc/>; rel="canonical"');
    for (const h of ["server", "via", "x-github-request-id", "access-control-allow-origin", "x-served-by", "x-cache", "x-timer", "x-fastly-request-id"]) {
      assert.equal(r.headers.get(h), null, h);
    }
    // What a browser and a cache need stays.
    assert.equal(r.headers.get("cache-control"), "max-age=600");
    assert.equal(r.headers.get("etag"), '"abc"');
    assert.equal(r.headers.get("x-content-type-options"), "nosniff");
  }
  // The slash redirect keeps the query the visitor came with (utm_* is read by the page from the address bar).
  const slash = await site.fetch(new Request("https://bamnat.app/terms?utm_source=bamnat_share"));
  assert.deepEqual([slash.status, slash.headers.get("location")], [301, "https://bamnat.app/terms/?utm_source=bamnat_share"]);
  assert.equal(slash.headers.get("server"), null);
  assert.equal(slash.headers.get("link"), null);
  // HEAD goes up as HEAD, with the same headers and no body.
  const head = await site.fetch(new Request("https://bamnat.app/shift/", { method: "HEAD" }));
  assert.equal(head.status, 200);
  assert.equal(seen.at(-1)[2], "HEAD");
  assert.equal(head.headers.get("x-github-request-id"), null);
  assert.equal(await head.text(), "");
  // A missing page stays a 404 (no canonical for it), without GitHub's headers.
  const nope = await site.fetch(new Request("https://bamnat.app/nope/?x=9"));
  assert.equal(nope.status, 404);
  assert.equal(seen.at(-1)[0], "https://ywjmoney.github.io/-/nope/");
  assert.equal(nope.headers.get("link"), null);
  assert.equal(nope.headers.get("access-control-allow-origin"), null);
});

// 2.0.0 [RVQ-55] 검토 SVC-39·U22-10.
test("a missing page is 밤낮's own 404 (칸이, one line, 처음으로), never GitHub's", async () => {
  const page = await site.fetch(new Request("https://bamnat.app/no-such-page?x=1"));
  assert.equal(page.status, 404);
  assert.match(page.headers.get("content-type"), /text\/html; charset=utf-8/);
  const html = await page.text();
  assert.doesNotMatch(html, /GitHub/);
  assert.match(html, /<title>찾는 페이지가 없어요 · 밤낮<\/title>/);
  assert.match(html, /<a class="go" href="\/">밤낮 처음으로<\/a>/);
  assert.match(html, /href="\/terms\/"[^>]*>이용약관/);
  assert.match(html, /class="kani light"/);
  assert.match(html, /class="kani dark"/);
  assert.match(html, /prefers-color-scheme:dark/);
  // 칸이 그림의 이름(clipPath)이 밝은·어두운에서 서로 달라 겹치지 않아요.
  assert.ok(html.includes('id="bday148"') && html.includes('id="bday148d"'));
  const head = await site.fetch(new Request("https://bamnat.app/no-such-page", { method: "HEAD" }));
  assert.equal(head.status, 404);
  assert.equal(await head.text(), "");
});

test("every answer carries the same security headers (pages, redirects, 404, 405, app-ads.txt)", async () => {
  const answers = [
    await site.fetch(new Request("https://bamnat.app/terms/")),
    await site.fetch(new Request("https://bamnat.app/terms")),
    await site.fetch(new Request("https://bamnat.app/no-such-page")),
    await site.fetch(new Request("https://bamnat.app/", { method: "POST", body: "x" })),
    await site.fetch(new Request("https://bamnat.app/app-ads.txt")),
  ];
  for (const r of answers) {
    assert.equal(r.headers.get("x-content-type-options"), "nosniff", String(r.status));
    assert.equal(r.headers.get("x-frame-options"), "DENY");
    assert.equal(r.headers.get("content-security-policy"), "frame-ancestors 'none'");
    assert.match(r.headers.get("strict-transport-security"), /^max-age=31536000$/);
    assert.match(r.headers.get("permissions-policy"), /camera=\(\)/);
    assert.equal(r.headers.get("referrer-policy"), "strict-origin-when-cross-origin");
  }
});
