// 1.39 [SPREAD-01..04] 받는 쪽 페이지 끝의 '나도 써 보기' 한 줄: after what the person came for, one card with the store link
// (where the install came from, never the link), an iPhone line instead of a dead end, and a close that stays closed.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, storeLink, spreadBack, spreadCard, OG_TEXT, SPREAD_KINDS } from "../src/index.js";

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
  assert.match(page, /가족 근무를 내 달력 옆에서 바로/);
  assert.match(page, /설치한 뒤 이 링크를 다시 누르면 앱에서 열려요/);
  const fallback = decodeURIComponent(page.match(/S\.browser_fallback_url=([^;]+);end/)[1].replace(/&amp;/g, "&"));
  assert.match(decodeURIComponent(fallback), /utm_source=bamnat_share&utm_medium=family&utm_campaign=family/);
  for (const href of page.matchAll(/play\.google\.com[^"]*/g)) assert.ok(!href[0].includes(id), "the link's id never goes to Play");
});

test("a leader's first 팀원 링크 (no 바뀜 yet) speaks to a teammate, and so does it once expired (PC SPREAD 2차)", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id } = await (await call(env, "/api/share", json({ ...sample, ttlDays: 90, team: true }))).json();
  const page = await (await call(env, `/s/${id}`)).text();
  assert.match(page, /근무가 바뀌면 내 알람도 저절로/);
  assert.match(page, /utm_medium%3Dteam/);
  assert.match(page, /<meta property="og:title" content="확정된 근무표가 왔어요">/);
  assert.equal((await call(env, "/api/share", json({ ...sample, team: "yes" }))).status, 400);
  // Expired but still kept: the teammate hears to ask the leader, with the teammate's card.
  const record = JSON.parse(env.SHARES.map.get(id));
  env.SHARES.map.set(id, JSON.stringify({ ...record, expiresAt: new Date(Date.now() - 1000).toISOString() }));
  const gone = await call(env, `/s/${id}`);
  assert.equal(gone.status, 404);
  const text = await gone.text();
  assert.match(text, /리더에게 새 링크를 부탁해 주세요/);
  assert.match(text, /근무가 바뀌면 내 알람도 저절로/);
  assert.doesNotMatch(text, /가족 근무를/);
});

test("a team leader's link speaks to a teammate", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const team = { ...sample, days: [{ date: "2026-11-01", type: 1, memo: "바뀜: 휴→주" }, { date: "2026-11-02", type: 3 }] };
  const { id } = await (await call(env, "/api/share", json({ ...team, v: 2, shows: ["memos"] }))).json();
  const page = await (await call(env, `/s/${id}`)).text();
  assert.match(page, /근무가 바뀌면 내 알람도 저절로/);
  assert.match(page, /이 링크로 시작하면/, "D9 follows only a link the app started from");
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
  // 1.39 [IOS-03] what 밤낮 is and that the link keeps working, without promising the app's 달력·알람 to an iPhone.
  assert.match(iphone, /밤낮은 Android 근무 달력 앱이에요/);
  assert.match(iphone, /이 링크는 iPhone에서도 그대로 계속 열려요/);
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

test("an unknown kind has no card", () => {
  assert.equal(spreadCard("nope", ANDROID, null), "");
});

test("[SPREAD-34] each card speaks of what the receiver gains, and promises nothing the app does not do yet", () => {
  for (const [kind, k] of Object.entries(SPREAD_KINDS)) {
    assert.doesNotMatch(k.title + k.line, /위젯/, `${kind}: a received calendar has no widget yet (SPREAD-33)`);
    assert.doesNotMatch(k.title, /나눠 보세요|보내 보세요/, `${kind}: the receiver's gain, not the sender's`);
  }
});

// ---- 1.39 [SPREAD-08] 끝난 링크 ----

test("an ended link says what to do next and still offers the app, without promising the link opens again", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const family = await call(env, `/s/${"a".repeat(20)}`);
  assert.equal(family.status, 404);
  const page = await family.text();
  assert.match(page, /보낸 분께 새 링크를 부탁해 주세요/);
  assert.match(page, /class="spread"/);
  // Cut by the sharer, the link is gone and so is whose it was: a card for anyone who keeps a schedule (PC: was the family one).
  assert.match(page, /내 근무도 달력과 알람으로/);
  assert.doesNotMatch(page, /가족 근무를/);
  assert.match(page, /utm_medium%3Dschedule/);
  assert.doesNotMatch(page, /다시 누르면 앱에서 열려요|그대로 열려요/);
  assert.match(page, new RegExp(`name="back" value="/s/${"a".repeat(20)}"`), "closing comes back here");
  for (const [path, title] of [[`/c/${"b".repeat(22)}`, "내 시급으로"], [`/a/${"b".repeat(22)}`, "확정된 근무가"], [`/v/${"b".repeat(22)}`, "바뀐 근무도"],
    [`/b/${"b".repeat(22)}`, "근무가 바뀌면"], [`/k/${"b".repeat(22)}`, "계약한 시급으로"], [`/w/${"b".repeat(22)}`, "바뀐 근무도"]]) {
    const res = await call(env, path);
    assert.equal(res.status, 404, path);
    const text = await res.text();
    assert.match(text, new RegExp(title), path);
    assert.doesNotMatch(text, /그대로 열려요/, path);
  }
  const iphone = await (await call(env, `/c/${"b".repeat(22)}`, {}, IPHONE)).text();
  assert.match(iphone, /밤낮은 Android 근무 달력 앱이에요/);
  assert.doesNotMatch(iphone, /계속 열려요/, "an ended link does not keep opening");
  const closed = await (await call(env, `/c/${"b".repeat(22)}`, { headers: { cookie: "bn_spread=off" } })).text();
  assert.doesNotMatch(closed, /class="spread"|<!--spread:/);
});

test("a link taken down for what it held keeps only its own words", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const id = "c".repeat(20);
  env.SHARES.map.set(id, JSON.stringify({ removed: true }));
  const res = await call(env, `/s/${id}`);
  assert.equal(res.status, 410);
  assert.doesNotMatch(await res.text(), /class="spread"|<!--spread:/);
});

// ---- 1.39 [SPREAD-09] 메신저 미리보기 ----

const ogOf = (page) => [...page.matchAll(/<meta (?:property|name)="((?:og|twitter):[a-z_]+|description)" content="([^"]*)">/g)]
  .map((m) => [m[1], m[2]]);

test("every receive page has a preview that names the kind of page and nothing of the people in it", async (t) => {
  // [RVD-01] The board's month (2026-12) is a near month only on a clock near it; from 2027-03 the real clock refused it.
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  readsForget();
  const env = { SHARES: kv() };
  // Everything private these links carry: names, team and workplace, shifts and times, the request's words.
  const secret = ["민지", "주간", "주", "07:00", "6병동", "다른병동", "박마바", "가나다", "김하나", "나이트", "근로계약서 갑", "가", "나", "다",
    "준호", "욕실", "매출"];
  const made = {};
  made.family = `/s/${(await (await call(env, "/api/share", json(sample))).json()).id}`;
  // 2.0.0 [RVD-30] HQ-64 A shop that sent its 매출 and a freelancer that sent jobs: the preview names the 영업일·일정, still no one.
  made.familySales = `/s/${(await (await call(env, "/api/share", json({ ...sample, v: 2, title: "가나다식당", shows: ["pay"], payKind: "sales",
    pay: [{ month: "2026-11", amount: 18450000, label: "매출" }] }))).json()).id}`;
  made.familyJobs = `/s/${(await (await call(env, "/api/share", json({ ...sample, v: 2, title: "준호", shows: ["jobs"],
    days: [{ date: "2026-11-03", jobs: [{ name: "욕실 시공", time: "09:00-17:00" }] }] }))).json()).id}`;
  const team = { ...sample, title: "민지", days: [{ date: "2026-11-01", type: 1, memo: "바뀜: 휴→주" }] };
  made.team = `/s/${(await (await call(env, "/api/share", json({ ...team, v: 2, shows: ["memos"] }))).json()).id}`;
  made.clock = `/c/${(await (await call(env, "/api/clock", json({ name: "박마바", place: "가나다카페" }))).json()).id}`;
  const ask = await (await call(env, "/api/ask", json({ team: "6병동", month: "2026-12", members: ["가", "나", "다"],
    shifts: [{ id: 1, label: "D", color: "#2f7d5b" }], rules: { weekdayCap: 2, weekendCap: 3, kinds: ["OFF"], holidays: [] } }))).json();
  made.request = `/a/${ask.members[0].key}`;
  const callMade = await (await call(env, "/api/call", json({ team: "6병동", title: "12/7 나이트 대신 들어갈 수 있나요?", detail: "22:00~07:00", people: ["다"] }))).json();
  made.call = `/v/${callMade.people[0].key}`;
  const days = 31;
  const board = await (await call(env, "/api/board", json({ team: "6병동", month: "2026-12", names: ["가", "나"],
    labels: [Array(days).fill("D"), Array(days).fill("N")], colors: { D: "#3B82F6", N: "#1E293B" } }))).json();
  made.board = new URL(board.url).pathname;
  const next = new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth() + 1, 1));
  const n = new Date(Date.UTC(next.getUTCFullYear(), next.getUTCMonth() + 1, 0)).getUTCDate();
  const row = (l) => Array.from({ length: n }, (_, i) => (i === 6 ? l : "O"));
  const swap = await (await call(env, "/api/swap", json({ team: "6병동", month: next.toISOString().slice(0, 7), people: ["가", "나", "다"],
    labels: [row("D"), row("N"), row("O")], ok: { 0: { 7: [1, 2] }, 1: { 7: [0] }, 2: { 7: [0] } } }))).json();
  made.swap = `/w/${swap.people[0].key}`;
  const { key } = await (await call(env, "/api/sign", json({ place: "가나다식당", staff: "김하나", title: "근로계약서", days: 7, items: [{ t: "임금", b: "시급 10,320원" }] }))).json();
  made.sign = `/k/${key}`;
  assert.deepEqual(Object.keys(made).sort(), Object.keys(OG_TEXT).sort(), "every page kind is looked at");
  for (const [kind, path] of Object.entries(made)) {
    const res = await call(env, path, {}, "kakaotalk-scrap/1.0; +https://devtalk.kakao.com/");
    assert.equal(res.status, 200, path);
    const og = ogOf(await res.text());
    const title = og.find(([k]) => k === "og:title");
    assert.equal(title?.[1], OG_TEXT[kind].title, `${kind}: its own title`);
    assert.ok(og.some(([k]) => k === "og:description"), kind);
    for (const [k, v] of og) {
      assert.doesNotMatch(v, /\d/, `${kind} ${k}: no date, time or count`);
      for (const word of secret) {
        // One-letter names (가·나·다) are checked as words, never as syllables inside 'Android'·'나눠' and the like.
        const hit = word.length === 1 ? new RegExp(`(^|[\\s·,])${word}([\\s·,님]|$)`).test(v) : v.includes(word);
        assert.ok(!hit, `${kind} ${k} carries "${word}": ${v}`);
      }
    }
  }
});

// ---- 1.39 [SPREAD-12] iPhone 캘린더 (IOS-02 feedButton으로 합침) ----

test("an iPhone gets one tap to subscribe in its calendar, only when the sharer turned the address on", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const on = (await (await call(env, "/api/share", json({ ...sample, ics: true, tz: "Asia/Seoul" }))).json()).id;
  const off = (await (await call(env, "/api/share", json(sample))).json()).id;
  const iphone = await (await call(env, `/s/${on}`, {}, IPHONE)).text();
  assert.match(iphone, new RegExp(`href="webcal://share\\.example/s/${on}\\.ics">아이폰 캘린더에 넣기</a>`)); // IOS-02 feedButton
  assert.ok(iphone.indexOf("아이폰 캘린더에 넣기") < iphone.indexOf('class="grid"'), "above the month, not in the card");
    assert.doesNotMatch(await (await call(env, `/s/${off}`, {}, IPHONE)).text(), /webcal:/, "never without the sharer's say");
  // Closing the card leaves the button: it is the page's, not the card's.
  assert.match(await (await call(env, `/s/${on}`, { headers: { cookie: "bn_spread=off" } }, IPHONE)).text(), /아이폰 캘린더에 넣기/);
});

