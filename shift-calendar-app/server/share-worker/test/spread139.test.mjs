// 1.39 [SPREAD-01..04] 받는 쪽 페이지 끝의 '나도 써 보기' 한 줄: after what the person came for, one card with the store link
// (where the install came from, never the link), an iPhone line instead of a dead end, and a close that stays closed.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, storeLink, spreadBack, spreadCard } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-F946N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";
const KAKAO = ANDROID + " KAKAOTALK 10.8.5";
const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1";
const PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const call = (env, path, init = {}, ua = ANDROID) => worker.fetch(new Request(`https://share.example${path}`,
  { ...init, headers: { "cf-connecting-ip": "203.0.113.61", "user-agent": ua, ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const json = (body) => ({ method: "POST", body: JSON.stringify(body), headers: { "content-type": "application/json" } });
const form = (body) => ({ method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });

const sample = { v: 1, title: "민지", ttlDays: 30,
  types: [{ id: 1, name: "주간", short: "주", color: "#1E88E5", time: "07:00-19:00" }, { id: 3, name: "휴무", short: "휴", color: "#9E9E9E", off: true }],
  days: [{ date: "2026-11-01", type: 1 }, { date: "2026-11-02", type: 3 }] };

test("the store link says where the install came from and nothing about the link", () => {
  const link = storeLink("clock");
  assert.ok(link.startsWith("https://play.google.com/store/apps/details?id=com.bamnat.app&referrer="));
  const referrer = decodeURIComponent(link.split("&referrer=")[1]);
  assert.equal(referrer, "utm_source=bamnat_share&utm_medium=clock&utm_campaign=boss");
  assert.doesNotMatch(referrer, /bamnat_invite/, "never read as a friend's invite");
  assert.match(decodeURIComponent(storeLink("nope")), /utm_medium=family&utm_campaign=family/);
});

test("a family link: the card comes after the month and the 앱에서 보기 store fallback carries the source, not the id", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/share", json(sample))).json();
  const page = await (await call(env, `/s/${id}`)).text();
  assert.doesNotMatch(page, /<script/i);
  assert.doesNotMatch(page, /<!--spread:/, "the slot is always filled or emptied");
  const card = page.indexOf('class="spread"');
  assert.ok(card > page.indexOf("읽기 전용 근무표예요"), "after the schedule and its foot");
  assert.match(page, /내 근무표도 이렇게 나눠 보세요/);
  assert.match(page, /설치한 뒤 이 링크를 다시 누르면 앱에서 열려요/);
  const fallback = decodeURIComponent(page.match(/S\.browser_fallback_url=([^;]+);end/)[1].replace(/&amp;/g, "&"));
  assert.match(decodeURIComponent(fallback), /utm_source=bamnat_share&utm_medium=family&utm_campaign=family/);
  for (const href of page.matchAll(/play\.google\.com[^"]*/g)) assert.ok(!href[0].includes(id), "the link's id never goes to Play");
});

test("a team leader's link speaks to a teammate", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const team = { ...sample, days: [{ date: "2026-11-01", type: 1, memo: "바뀜: 휴→주" }, { date: "2026-11-02", type: 3 }] };
  const { id } = await (await call(env, "/api/share", json({ ...team, v: 2, shows: ["memos"] }))).json();
  const page = await (await call(env, `/s/${id}`)).text();
  assert.match(page, /내 근무만 달력에 넣고 근무 알람까지/);
  assert.match(page, /utm_campaign%3Dteam/);
});

test("the clock page: buttons first, the card last; iPhone and PC get no dead end", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/clock", json({ name: "박마바", place: "가나다카페" }))).json();
  const android = await (await call(env, `/c/${id}`)).text();
  assert.ok(android.indexOf('class="spread"') > android.indexOf('class="buttons"'), "never above 출근·퇴근");
  assert.match(android, /내 시급으로 이번 달 월급 미리 보기/);
  assert.match(android, /밤낮 받기 · 무료/);
  assert.match(android, /앱을 받은 뒤에도 이 링크는 그대로 열려요/);
  assert.match(android, /utm_medium%3Dclock%26utm_campaign%3Dboss/);
  const iphone = await (await call(env, `/c/${id}`, {}, IPHONE)).text();
  assert.match(iphone, /밤낮은 Android 앱이에요. 이 링크는 iPhone에서도 그대로 계속 열려요/);
  assert.doesNotMatch(iphone, /밤낮 받기/);
  const pc = await (await call(env, `/c/${id}`, {}, PC)).text();
  assert.match(pc, /Play 스토어에서 보기/);
  // After a press the page still ends with the card, and its close goes back to the page itself.
  const pressed = await (await call(env, `/c/${id}/punch`, form("k=in"))).text();
  assert.match(pressed, /출근 \d\d:\d\d 기록했어요/);
  assert.match(pressed, new RegExp(`name="back" value="/c/${id}"`));
});

test("a messenger's own browser opens the Play app with the source kept", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/clock", json({ name: "가" }))).json();
  const page = await (await call(env, `/c/${id}`, {}, KAKAO)).text();
  assert.match(page, /href="intent:\/\/details\?id=com\.bamnat\.app&amp;referrer=utm_source%3Dbamnat_share/);
  assert.match(page, /scheme=market;package=com\.android\.vending/);
});

test("closing the card keeps it closed in this browser and only goes back to a receive page", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/clock", json({ name: "가" }))).json();
  const hid = await call(env, "/spread/hide", form(`back=${encodeURIComponent(`/c/${id}`)}`));
  assert.equal(hid.status, 303);
  assert.equal(hid.headers.get("location"), `/c/${id}`);
  const cookie = hid.headers.get("set-cookie");
  assert.match(cookie, /^bn_spread=off; Max-Age=31536000; Path=\/; HttpOnly; SameSite=Lax; Secure$/);
  const again = await (await call(env, `/c/${id}`, { headers: { cookie: "x=1; bn_spread=off" } })).text();
  assert.doesNotMatch(again, /class="spread"/);
  assert.doesNotMatch(again, /<!--spread:/);
  assert.match(again, /출퇴근/, "the page itself is all there");
  assert.equal((await call(env, "/spread/hide", form("back=https://evil.example/"))).status, 404);
  assert.equal((await call(env, "/spread/hide", form("back=/api/clock/" + id))).status, 404);
  assert.equal(spreadBack(`/w/${"a".repeat(22)}/reply`), `/w/${"a".repeat(22)}`);
  assert.equal(spreadBack("/s/abc"), null);
});

test("on the PC's plain-http server the close cookie is not marked Secure (Chrome would drop it)", async () => {
  readsForget();
  const res = await worker.fetch(new Request("http://10.0.2.2:8790/spread/hide", { method: "POST", body: `back=/c/${"a".repeat(22)}`,
    headers: { "content-type": "application/x-www-form-urlencoded", "cf-connecting-ip": "203.0.113.62" } }), { SHARES: kv() }, { waitUntil() {} });
  assert.doesNotMatch(res.headers.get("set-cookie"), /Secure/);
});

test("the contract page shows the card only once it is signed", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const contract = { place: "밤낮 카페", staff: "김하나", title: "근로계약서", days: 7, items: [{ t: "임금", b: "시급 10,320원" }] };
  const { key } = await (await call(env, "/api/sign", json(contract))).json();
  assert.doesNotMatch(await (await call(env, `/k/${key}`)).text(), /class="spread"/, "nothing beside an unsigned contract");
  const signed = await (await call(env, `/k/${key}/sign`, form("name=" + encodeURIComponent("김하나") + "&agree=1"))).text();
  assert.match(signed, /서명했어요/);
  assert.match(signed, /계약한 시급으로 이번 달 월급 미리 보기/);
});

test("pages without a card and gone links are left as they were", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const gone = await call(env, `/c/${"a".repeat(22)}`);
  assert.equal(gone.status, 404);
  assert.doesNotMatch(await gone.text(), /class="spread"/);
  assert.equal(spreadCard("nope", ANDROID, null), "");
});
