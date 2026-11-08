// 1.39 [IOS-02..06] 아이폰 팀원·직원: the pages they get keep working and let them take their days with them.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, platformOf, feedButton, flushStats } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-F946N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1";
const IPHONE_KAKAO = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 10.8.5";
const PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const call = (env, path, init = {}, ua = IPHONE) => worker.fetch(new Request(`https://s.bamnat.app${path}`,
  { ...init, headers: { "cf-connecting-ip": "203.0.113.77", "user-agent": ua, ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const json = (body, method = "POST", headers = {}) => ({ method, body: JSON.stringify(body), headers: { "content-type": "application/json", ...headers } });

const team = { v: 2, title: "6병동 지영", ttlDays: 90, shows: ["memos"], ics: true, tz: "Asia/Seoul",
  types: [{ id: 1, name: "데이", short: "D", color: "#3B82F6", time: "07:00-15:00" }, { id: 9, name: "오프", short: "O", color: "#9E9E9E", off: true }],
  days: [{ date: "2026-12-01", type: 1, memo: "바뀜: O→D" }, { date: "2026-12-02", type: 9, memo: "같이 쉬어요: 민지" }] };

test("platformOf: iPhone (Safari and KakaoTalk's browser), Android, anything else", () => {
  assert.equal(platformOf(IPHONE), "ios");
  assert.equal(platformOf(IPHONE_KAKAO), "ios");
  assert.equal(platformOf(ANDROID), "android");
  assert.equal(platformOf(PC), "other");
  assert.equal(platformOf(""), "other");
});

test("IOS-02: a link with its calendar address on offers 내 캘린더에 넣기 by phone, and never calls it an alarm", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/share", json(team))).json();
  const ios = await (await call(env, `/s/${id}`)).text();
  assert.match(ios, new RegExp(`href="webcal://s\\.bamnat\\.app/s/${id}\\.ics"`));
  assert.match(ios, /아이폰 캘린더에 넣기/);
  assert.match(ios, /출근 알람은 아니에요/);
  assert.match(ios, /Safari로 열기/);
  assert.match(ios, new RegExp(`https://s\\.bamnat\\.app/s/${id}\\.ics`), "the address for other calendar apps, in the page's own scheme");
  assert.doesNotMatch(ios, /<script/i);
  // The card at the end does not promise an iPhone the app's 달력·알람.
  assert.doesNotMatch(ios, /근무 알람까지<\/p>/);
  assert.match(ios, /밤낮은 Android 근무 달력 앱이에요/);
  const android = await (await call(env, `/s/${id}`, {}, ANDROID)).text();
  assert.match(android, /구글 캘린더에 넣기/);
  assert.match(android, new RegExp(`calendar\\.google\\.com/calendar/r\\?cid=webcal%3A%2F%2Fs\\.bamnat\\.app%2Fs%2F${id}\\.ics`));
  assert.doesNotMatch(android, /Safari로 열기/);
  // The address the button names is the feed that is really there.
  const feed = await call(env, `/s/${id}.ics`);
  assert.equal(feed.status, 200);
  assert.match(await feed.text(), /SUMMARY:데이/);
  // Off: no button.
  const { id: plain } = await (await call(env, "/api/share", json({ ...team, ics: undefined, tz: undefined }))).json();
  assert.doesNotMatch(await (await call(env, `/s/${plain}`)).text(), /캘린더에 넣기/);
  assert.equal(feedButton({ ics: true }, null, "ios"), "", "renderSchedule without a served address shows none");
});

test("IOS-03: on an iPhone the extras sent for the app are named as Android-only, with no '앱에서 열면'", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const setup = JSON.stringify({ format: "bamnat-company", name: "가나병원" });
  const { id } = await (await call(env, "/api/share", json({ ...team, audience: "coworker", setup }))).json();
  const ios = await (await call(env, `/s/${id}`)).text();
  assert.match(ios, /회사 근무 설정도 함께 왔어요\. 이건 Android 밤낮 앱에서만 받을 수 있고, 근무는 이 페이지에서 그대로 볼 수 있어요/);
  assert.doesNotMatch(ios, /밤낮 앱에서 열면/);
  assert.doesNotMatch(ios, /가나병원/, "the setup itself is never shown");
  const android = await (await call(env, `/s/${id}`, {}, ANDROID)).text();
  assert.match(android, /밤낮 앱에서 열면 기본급만 넣고 바로 쓸 수 있어요/);
});

test("IOS-04: with the leader's 피곤해요 합계 on, the page presses once a day with no name, the same one as the app", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const link = await (await call(env, "/api/share", json(team))).json();
  // Not tied: no panel, and the form says nothing happened.
  assert.doesNotMatch(await (await call(env, `/s/${link.id}`)).text(), /오늘 피곤해요/);
  const box = await (await call(env, "/api/tired", json({ team: "6병동" }))).json();
  assert.equal((await call(env, `/api/share/${link.id}/tired`, json({ box: box.id }, "PUT", { "x-delete-token": link.deleteToken }))).status, 204);
  let page = await (await call(env, `/s/${link.id}`)).text();
  assert.match(page, new RegExp(`<form method="post" action="/s/${link.id}/tired"><button class="pick">오늘 피곤해요</button>`));
  assert.match(page, /누가 눌렀는지는 리더도 몰라요/);
  const press = await call(env, `/s/${link.id}/tired`, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: "" });
  assert.equal(press.status, 303);
  assert.equal(press.headers.get("location"), `/s/${link.id}?t=1`);
  page = await (await call(env, `/s/${link.id}?t=1`)).text();
  assert.match(page, /팀에 ‘피곤해요’ 하나를 더했어요/);
  assert.doesNotMatch(page, /<button class="pick">오늘 피곤해요/);
  // The app's press the same day is the same one: no second count.
  assert.equal((await call(env, `/api/share/${link.id}/tired`, { method: "POST" }, ANDROID)).status, 204);
  const box1 = JSON.parse(env.SHARES.map.get(`tired:${box.id}`));
  assert.equal(Object.values(box1.days).reduce((a, n) => a + n, 0), 1);
  assert.match(await (await call(env, `/s/${link.id}`)).text(), /오늘은 이미 알렸어요/);
  // The leader ends the box: the panel goes.
  assert.equal((await call(env, `/api/tired/${box.id}`, { method: "DELETE", headers: { "x-delete-token": box.token } })).status, 204);
  assert.doesNotMatch(await (await call(env, `/s/${link.id}`)).text(), /피곤해요 알리기/);
  const gone = await call(env, `/s/${link.id}/tired`, { method: "POST", body: "" });
  assert.equal(gone.headers.get("location"), `/s/${link.id}?t=off`);
});

test("IOS-05: pages carry the home-screen icon and a short name; the iPhone gets the 홈 화면에 추가 line", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/share", json(team))).json();
  const ios = await (await call(env, `/s/${id}`)).text();
  assert.match(ios, /<link rel="apple-touch-icon" href="\/apple-touch-icon\.png">/);
  assert.match(ios, /<meta name="apple-mobile-web-app-title" content="근무표">/);
  assert.match(ios, /홈 화면에 추가/);
  assert.doesNotMatch(await (await call(env, `/s/${id}`, {}, ANDROID)).text(), /아이폰: 아래 공유 단추/);
  const icon = await call(env, "/apple-touch-icon.png");
  assert.equal(icon.status, 200);
  assert.equal(icon.headers.get("content-type"), "image/png");
  const bytes = new Uint8Array(await icon.arrayBuffer());
  assert.deepEqual([...bytes.slice(1, 4)], [0x50, 0x4e, 0x47]);
  assert.equal(new DataView(bytes.buffer).getUint32(16), 180);
  const clock = await (await call(env, "/api/clock", json({ name: "지영", place: "가나카페", ttlDays: 60 }))).json();
  const clockPage = await (await call(env, `/c/${clock.id}`)).text();
  assert.match(clockPage, /<meta name="apple-mobile-web-app-title" content="출퇴근">/);
  assert.match(clockPage, /‘홈 화면에 추가’를 누르면 아이콘으로 바로 열려요/);
});

test("IOS-06: views are counted by kind of phone, team pages apart, preview robots not at all", async (t) => {
  readsForget();
  t.mock.timers.enable({ apis: ["Date"], now: new Date("2026-12-01T03:00:00Z") });
  const env = { SHARES: kv(), SHARE_ADMIN_TOKEN: "a".repeat(24) };
  const { id } = await (await call(env, "/api/share", json(team))).json();
  await call(env, `/s/${id}`, {}, IPHONE);
  await call(env, `/s/${id}`, {}, IPHONE_KAKAO);
  await call(env, `/s/${id}`, {}, ANDROID);
  await call(env, `/s/${id}`, {}, "facebookexternalhit/1.1");
  const clock = await (await call(env, "/api/clock", json({ name: "지영", place: "", ttlDays: 60 }))).json();
  await call(env, `/c/${clock.id}`, {}, IPHONE);
  await call(env, `/c/${clock.id}`, {}, PC);
  await flushStats(env);
  const s = await (await call(env, "/api/admin/stats?days=1", { headers: { authorization: `Bearer ${"a".repeat(24)}` } })).json();
  assert.equal(s.views, 4, "the old count is as it was");
  assert.deepEqual(s.phones, { views_ios: 2, team_ios: 1, views_android: 1, team_android: 0, views_other: 0, team_other: 1 });
  assert.equal(s.clocks, 1);
  assert.doesNotMatch(JSON.stringify(s), new RegExp(id));
});
