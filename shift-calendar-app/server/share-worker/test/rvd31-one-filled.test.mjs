// 2.0.0 [RVD-31] HQ-68 디자인 품질 조건 2 (채운 단추는 화면에 하나): a received 근무표 page filled 밤낮 앱에서 보기, 캘린더에 넣기 and the
// card's 밤낮 받기 all alike. Now Android fills only 앱에서 보기 (it opens the app, or its store page when there is none), an iPhone or a
// computer only 캘린더에 넣기, and the card at the bottom is never filled on any page. Every button is at least 44px. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, feedButton, spreadCard } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const UA = {
  android: "Mozilla/5.0 (Linux; Android 14; SM-F946N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
  ios: "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1",
  pc: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
};
const call = (env, path, init = {}, ua = UA.android) => worker.fetch(new Request(`https://s.bamnat.app${path}`,
  { ...init, headers: { "cf-connecting-ip": "203.0.113.31", "user-agent": ua, ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const json = (body) => ({ method: "POST", body: JSON.stringify(body), headers: { "content-type": "application/json" } });
const link = { v: 2, title: "민지", ttlDays: 30, shows: [], ics: true,
  types: [{ id: 1, name: "데이", short: "D", color: "#1E88E5", time: "07:00-15:00" }], days: [{ date: "2026-11-01", type: 1 }] };

/** The page's filled buttons: 앱에서 보기 (.app a) and every a.get that is not a line. */
const filled = (page) => [
  ...[...page.matchAll(/<p class="app"><a [^>]*>([^<]+)<\/a>/g)].map((m) => m[1]),
  ...[...page.matchAll(/<a class="get" [^>]*>([^<]+)<\/a>/g)].map((m) => m[1]),
];

test("[RVD-31] a received 근무표 page fills one button: 앱에서 보기 on Android, 캘린더에 넣기 elsewhere", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/share", json(link))).json();
  const android = await (await call(env, `/s/${id}`)).text();
  assert.deepEqual(filled(android), ["밤낮 앱에서 보기"]);
  assert.match(android, /<a class="get line" href="https:\/\/calendar\.google\.com[^"]*">구글 캘린더에 넣기<\/a>/);
  assert.match(android, /<a class="get line" href="[^"]*">밤낮 받기 · 무료<\/a>/, "the card is still there, as a line");
  assert.deepEqual(filled(await (await call(env, `/s/${id}`, {}, UA.ios)).text()), ["아이폰 캘린더에 넣기"]);
  assert.deepEqual(filled(await (await call(env, `/s/${id}`, {}, UA.pc)).text()), ["구글 캘린더에 넣기"]);
  // Without the calendar address the Android page has its one button, and an iPhone none (nothing there is the thing to do).
  const { id: plain } = await (await call(env, "/api/share", json({ ...link, ics: false }))).json();
  assert.deepEqual(filled(await (await call(env, `/s/${plain}`)).text()), ["밤낮 앱에서 보기"]);
  assert.deepEqual(filled(await (await call(env, `/s/${plain}`, {}, UA.ios)).text()), []);
});

test("[RVD-31] the card is never filled, on any page, and the buttons are 44px", async () => {
  for (const kind of ["family", "team", "board", "request", "call", "swap", "clock", "sign"]) {
    const card = spreadCard(kind, UA.android, null);
    assert.match(card, /<a class="get line" /, kind);
    assert.doesNotMatch(card, /<a class="get" /, kind);
  }
  assert.match(feedButton({ ics: true }, "s.bamnat.app/s/x.ics", "android", "https", false), /class="get line"/);
  assert.match(feedButton({ ics: true }, "s.bamnat.app/s/x.ics", "ios"), /class="get"/, "filled unless told otherwise");
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/share", json(link))).json();
  const page = await (await call(env, `/s/${id}`)).text();
  for (const rule of [/\.app a\{[^}]*min-height:44px/, /\.feed \.get\{[^}]*min-height:44px/, /\.spread \.get\{[^}]*min-height:44px/]) assert.match(page, rule);
  // A line keeps the filled one's size (a 1px border in place of 1px of padding) and reads on both themes (muted, not the faint line).
  assert.match(page, /\.get\.line\{background:none;color:var\(--fg\);border:1px solid var\(--muted\);padding:9px 15px\}/);
});
