// [SITE-01] `node --test test/`
import { test } from "node:test";
import assert from "node:assert/strict";
import site from "../src/index.js";

const seen = [];
globalThis.fetch = async (url, init) => {
  seen.push([url, init.redirect]);
  if (url.endsWith("/-/terms")) return new Response(null, { status: 301, headers: { location: "https://ywjmoney.github.io/-/terms/" } });
  return new Response(`page ${url}`, { status: 200, headers: { "content-type": "text/html" } });
};

test("bamnat.app shows the Pages site without leaving bamnat.app", async () => {
  const page = await site.fetch(new Request("https://bamnat.app/terms/?x=1"));
  assert.equal(page.status, 200);
  assert.equal(await page.text(), "page https://ywjmoney.github.io/-/terms/?x=1");
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
