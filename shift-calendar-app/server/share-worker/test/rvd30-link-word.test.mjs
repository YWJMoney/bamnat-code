// 2.0.0 [RVD-30] HQ-64 (총괄 A안): a 근무표 링크 is named by what the sharer sent with it, as the app names it (core linkWord, G RVG-35).
// 매출 → 영업일, 일감·일감 수입 → 일정, anything else → 근무표. The page title, the home-screen name, the foot, the preview, the ended page
// and the calendar feed all say the same word. A link without a name is "받은 …". `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, linkWord, renderSchedule, renderIcs } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1";
const call = (env, path, init = {}, ua = IPHONE) => worker.fetch(new Request(`https://s.bamnat.app${path}`,
  { ...init, headers: { "cf-connecting-ip": "203.0.113.88", "user-agent": ua, ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const json = (body) => ({ method: "POST", body: JSON.stringify(body), headers: { "content-type": "application/json" } });

const types = [{ id: 1, name: "오픈", short: "오", color: "#1E88E5", time: "09:00-18:00" }, { id: 3, name: "휴무", short: "휴", color: "#9E9E9E", off: true }];
const shop = { v: 2, title: "가나다식당", ttlDays: 30, shows: ["pay"], payKind: "sales", ics: true, types,
  days: [{ date: "2026-11-01", type: 1 }, { date: "2026-11-02", type: 3 }], pay: [{ month: "2026-11", amount: 18450000, label: "매출" }] };
const field = { v: 2, title: "준호", ttlDays: 30, shows: ["jobs"], ics: true, types: [],
  days: [{ date: "2026-11-03", jobs: [{ name: "욕실 시공", time: "09:00-17:00" }] }] };
const family = { v: 2, title: "민지", ttlDays: 30, shows: ["memos"], ics: true, types, days: [{ date: "2026-11-01", type: 1, memo: "저녁 약속" }] };
const later = () => new Date(Date.now() + 86400000).toISOString();

test("[RVD-30] the word comes only from what the sharer ticked", () => {
  assert.equal(linkWord({ shows: ["pay"], payKind: "sales" }), "영업일");
  assert.equal(linkWord({ shows: ["pay", "memos"], payKind: "sales" }), "영업일");
  assert.equal(linkWord({ shows: ["jobs"] }), "일정");
  assert.equal(linkWord({ shows: ["pay"], payKind: "jobs" }), "일정");
  assert.equal(linkWord({ shows: ["jobs", "pay"], payKind: "jobs" }), "일정");
  // 급여 or nothing ticked: a 근무표, and so a shop that sent only its days (the app says 근무표 too).
  assert.equal(linkWord({ shows: ["pay"], payKind: "pay" }), "근무표");
  assert.equal(linkWord({ shows: ["pay"] }), "근무표", "links from before 1.13 are pay");
  assert.equal(linkWord({ shows: [] }), "근무표");
  assert.equal(linkWord({}), "근무표", "a v1 link has no shows");
  // 매출 was not ticked: its payKind says nothing.
  assert.equal(linkWord({ shows: ["memos"], payKind: "sales" }), "근무표");
});

test("[RVD-30] the page says the word in its title, home-screen name, what came along and foot", () => {
  const page = renderSchedule(shop, later());
  assert.match(page, /<title>가나다식당의 영업일<\/title>/);
  assert.match(page, /<h1>가나다식당의 영업일<\/h1>/);
  assert.match(page, /<meta name="apple-mobile-web-app-title" content="영업일">/);
  assert.match(page, /영업일과 함께 보낸 것: 매출/);
  assert.match(page, /읽기 전용 영업일이에요\./);
  assert.ok(!page.includes("근무표"), "no 근무표 on a shop's page");

  const jobs = renderSchedule(field, later());
  assert.match(jobs, /<h1>준호의 일정<\/h1>/);
  assert.match(jobs, /content="일정">/);
  assert.match(jobs, /<p class="muted">함께 보낸 것: 일정</);
  assert.match(jobs, /읽기 전용 일정이에요\./);

  const kin = renderSchedule(family, later());
  assert.match(kin, /<h1>민지의 근무표<\/h1>/);
  assert.match(kin, /근무와 함께 보낸 것: 메모/);
  assert.match(kin, /읽기 전용 근무표예요\./);
});

test("[RVD-30] a link without a name is 받은 근무표·영업일·일정, as the other phone shows it", () => {
  assert.match(renderSchedule({ ...family, title: "" }, later()), /<h1>받은 근무표<\/h1>/);
  assert.match(renderSchedule({ ...shop, title: "" }, later()), /<h1>받은 영업일<\/h1>/);
  assert.match(renderSchedule({ ...field, title: "" }, later()), /<h1>받은 일정<\/h1>/);
});

test("[RVD-30] the preview, the calendar feed and the ended page keep the word", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/share", json(shop))).json();
  const page = await (await call(env, `/s/${id}`)).text();
  assert.match(page, /<meta property="og:title" content="영업일이 왔어요">/);
  assert.match(page, /영업일이 바뀔 때 캘린더도/);
  assert.match(page, /바뀌어요\. 알람은 아니에요/, "a shop has no 출근");
  const ics = await (await call(env, `/s/${id}.ics`)).text();
  assert.match(ics, /X-WR-CALNAME:가나다식당의 영업일/);
  assert.match(renderIcs({ ...field, title: "" }, "x", null), /X-WR-CALNAME:밤낮 일정/);
  assert.match(renderIcs(family, "x", null), /X-WR-CALNAME:민지의 근무/);

  // Ended but still kept: named as its page was.
  const record = JSON.parse(env.SHARES.map.get(id));
  env.SHARES.map.set(id, JSON.stringify({ ...record, expiresAt: new Date(Date.now() - 1000).toISOString() }));
  const gone = await call(env, `/s/${id}`);
  assert.equal(gone.status, 404);
  const text = await gone.text();
  assert.match(text, /이 영업일 링크는 만료되었거나/);
  assert.match(text, /최신 영업일이 필요하면 보낸 분께/);
});
