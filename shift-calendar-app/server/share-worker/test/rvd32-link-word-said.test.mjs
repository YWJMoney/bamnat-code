// 2.0.0 [RVD-32] PC-170 (통합 4차 PC, 총괄 11-13): a shop that sent its days only got '가나다식당의 근무표' with '출근 알람은 아니에요' and
// '가족 근무를 내 달력 옆에서 바로'. Now a link that does not say what it is speaks in the middle words (보낸 분의 근무표, no 출근,
// no 가족), and from 처리방침 3.14 the sender's app may say it (word: 근무표·영업일·일정·달력, core scheduleWord). The report
// form's 신고 보내기 is 44px (조건 9). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, linkWord, cardWord, validate, renderIcs, SCHEDULE_WORDS } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-F946N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
const call = (env, path, init = {}, ua = ANDROID) => worker.fetch(new Request(`https://s.bamnat.app${path}`,
  { ...init, headers: { "cf-connecting-ip": "203.0.113.89", "user-agent": ua, ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const json = (body, method = "POST", token = null) => ({ method, body: JSON.stringify(body),
  headers: { "content-type": "application/json", ...(token ? { "x-delete-token": token } : {}) } });

const types = [{ id: 1, name: "오픈", short: "오", color: "#1E88E5", time: "09:00-18:00" }, { id: 3, name: "휴무", short: "휴", color: "#9E9E9E", off: true }];
const days = [{ date: "2026-11-21", type: 1 }, { date: "2026-11-22", type: 3 }];
const shop = { v: 2, title: "가나다식당", ttlDays: 30, shows: [], ics: true, types, days, audience: "family" };
const ON = "2026-11-01";
const make = async (env, body) => {
  const made = await (await call(env, "/api/share", json(body))).json();
  return { ...made, page: await (await call(env, `/s/${made.id}`)).text(), stored: JSON.parse(env.SHARES.map.get(made.id)).schedule };
};

test("[RVD-32] the sender's word: one of four, anything else dropped (not refused), and shows still win", () => {
  assert.deepEqual(SCHEDULE_WORDS, ["근무표", "영업일", "일정", "달력"]);
  for (const word of SCHEDULE_WORDS) assert.equal(validate({ ...shop, word }).schedule.word, word);
  for (const word of ["가족", "근무 표", 3, null, "<b>영업일</b>"]) {
    const r = validate({ ...shop, word });
    assert.equal(r.error, undefined, String(word));
    assert.equal(r.schedule.word, undefined, String(word));
  }
  assert.equal(linkWord({ shows: [], word: "영업일" }), "영업일");
  assert.equal(linkWord({ shows: [], word: "달력" }), "달력");
  assert.equal(linkWord({ shows: ["pay"], payKind: "sales", word: "근무표" }), "영업일", "what came along says more than the word");
  assert.equal(linkWord({ shows: ["jobs"], word: "근무표" }), "일정");
  assert.equal(linkWord({ shows: [], word: "가족" }), "근무표");
  // The card: 가족 근무 only for a 근무표 its app said, sent to 가족·연인.
  assert.equal(cardWord({ shows: [] }), "근무표");
  assert.equal(cardWord({ shows: [], audience: "family" }), "근무표", "an unsaid link is not taken for family");
  assert.equal(cardWord({ shows: [], word: "근무표", audience: "family" }), "가족");
  assert.equal(cardWord({ shows: [], word: "근무표", audience: "coworker" }), "근무표");
  assert.equal(cardWord({ shows: [], word: "영업일", audience: "family" }), "영업일");
});

test("[RVD-32] before 3.14 the word is not kept, and a shop's days-only link speaks in the middle words", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id, page, stored } = await make(env, { ...shop, word: "영업일" });
  assert.equal(stored.word, undefined, "nothing new is kept before 처리방침 3.14");
  assert.match(page, /<h1>가나다식당의 근무표<\/h1>/);
  assert.match(page, /<p class="t">보낸 분의 근무표를 내 달력 옆에서 바로<\/p>/);
  assert.match(page, /밤낮에 이 근무표를 받아 두면/);
  assert.match(page, /바뀌어요\. 알람은 아니에요/);
  assert.ok(!page.includes("출근"), "no 출근 beside a shop's name");
  assert.ok(!page.includes("가족 근무"), "no 가족 근무 beside a shop's name");
  // Its ended page too.
  const record = JSON.parse(env.SHARES.map.get(id));
  env.SHARES.map.set(id, JSON.stringify({ ...record, expiresAt: new Date(Date.now() - 1000).toISOString() }));
  const gone = await (await call(env, `/s/${id}`)).text();
  assert.match(gone, /보낸 분의 근무표를 내 달력 옆에서 바로/);
  assert.ok(!gone.includes("가족 근무"));
});

test("[RVD-32] from 3.14 a shop that says 영업일 is a 영업일 with its days only, on create and on update", async () => {
  readsForget();
  const env = { SHARES: kv(), POLICY_314_FROM: ON };
  const made = await make(env, { ...shop, word: "영업일" });
  assert.equal(made.stored.word, "영업일");
  assert.match(made.page, /<h1>가나다식당의 영업일<\/h1>/);
  assert.match(made.page, /<p class="t">가게 영업일을 내 달력 옆에서 바로<\/p>/);
  assert.match(made.page, /<meta property="og:title" content="영업일이 왔어요">/);
  assert.match(made.page, /영업일이 바뀔 때 캘린더도/);
  for (const w of ["근무표", "출근", "가족 근무"]) assert.ok(!made.page.includes(w), w);
  // The app's automatic update carries it too; an older server's link gets it on its next update.
  const off = { SHARES: kv() };
  const old = await (await call(off, "/api/share", json(shop))).json();
  env.SHARES.map.set(old.id, off.SHARES.map.get(old.id));
  assert.equal((await call(env, `/api/share/${old.id}`, json({ ...shop, word: "영업일" }, "PUT", old.deleteToken))).status, 200);
  assert.equal(JSON.parse(env.SHARES.map.get(old.id)).schedule.word, "영업일");
  assert.match(await (await call(env, `/s/${old.id}`)).text(), /<h1>가나다식당의 영업일<\/h1>/);
  // Off again (a dashboard value must never turn it on early): an update drops it.
  off.SHARES.map.set(old.id, env.SHARES.map.get(old.id));
  assert.equal((await call(off, `/api/share/${old.id}`, json({ ...shop, word: "영업일" }, "PUT", old.deleteToken))).status, 200);
  assert.equal(JSON.parse(off.SHARES.map.get(old.id)).schedule.word, undefined);
});

test("[RVD-32] from 3.14 a 근무표 its app said is a 가족 근무 for family and keeps 출근; 달력 for an investor", async () => {
  readsForget();
  const env = { SHARES: kv(), POLICY_314_FROM: ON };
  const shift = { ...shop, title: "민지", types: [{ id: 1, name: "주간", short: "주", color: "#1E88E5", time: "07:00-19:00" }, types[1]] };
  const kin = (await make(env, { ...shift, word: "근무표" })).page;
  assert.match(kin, /<h1>민지의 근무표<\/h1>/);
  assert.match(kin, /<p class="t">가족 근무를 내 달력 옆에서 바로<\/p>/);
  assert.match(kin, /출근 알람은 아니에요/);
  const mate = (await make(env, { ...shift, word: "근무표", audience: "coworker" })).page;
  assert.match(mate, /<p class="t">보낸 분의 근무표를 내 달력 옆에서 바로<\/p>/);
  assert.match(mate, /출근 알람은 아니에요/, "a 근무표 its app said, whoever it went to");
  const invest = await make(env, { ...shift, word: "달력" });
  assert.match(invest.page, /<h1>민지의 달력<\/h1>/);
  assert.match(invest.page, /<p class="t">보낸 분의 달력을 내 것 옆에서 바로<\/p>/);
  assert.match(invest.page, /밤낮에 이 달력을 받아 두면/);
  assert.match(invest.page, /<meta property="og:title" content="달력이 왔어요">/);
  assert.match(invest.page, /읽기 전용 달력이에요\./);
  assert.ok(!invest.page.includes("출근") && !invest.page.includes("근무표"));
  assert.match(renderIcs(invest.stored, "x", null), /X-WR-CALNAME:민지의 달력/);
});

test("[RVD-32] the report form: 신고 보내기 and each reason are 44px to press (조건 9)", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { page } = await make(env, shop);
  assert.match(page, /<button type="submit">신고 보내기<\/button>/);
  // A floor, not a fixed line: at 200% the words grow and the row with them (조건 12).
  assert.match(page, /\.report summary\{[^}]*min-height:44px;box-sizing:border-box/);
  assert.match(page, /\.report label\{[^}]*min-height:44px;box-sizing:border-box/);
  assert.match(page, /\.report button\{[^}]*min-height:44px;box-sizing:border-box/);
});
