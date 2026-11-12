// 2.0.0 [RVQ-19]~[RVQ-23] 검토 SVC-03·SVC-11~14·SVC-35·SVC-36·T22-01·T22-03·T22-04·U22-01·I22b-01: what the leader or 사장님 sees
// about a member's link can be believed (a preview robot is not a person, a press says it was read), the guards hold whatever asks,
// and the small numbers stay small.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, scamMaskText, renderIcs, vtimezone, tiredShown, LIMITS, policy314, POLICY_314_FROM } from "../src/index.js";

function kv() {
  const map = new Map();
  let gets = 0;
  return { map, gets: () => gets, async get(k, t) { gets += 1; const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}, ip = "203.0.113.50") =>
  worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": ip, ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const post = (o, headers = {}) => ({ method: "POST", body: JSON.stringify(o), headers: { "content-type": "application/json", ...headers } });
const form = (body = "") => ({ method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const BOT = { "user-agent": "facebookexternalhit/1.1;kakaotalk-scrap/1.0;" };
const TEAMS = { "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 MicrosoftPreview/2.0 +https://aka.ms/MicrosoftPreview" };
const SCANNER = { "user-agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36" };
const PHONE = { "user-agent": "Mozilla/5.0 (Linux; Android 14; SM-F946N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 KAKAOTALK 10.4.5" };
const contract = { place: "밤낮 카페", staff: "김 하나", title: "단시간 근로자 근로계약서", days: 7, items: [{ t: "근로 시작일", b: "2026년 11월 9일부터" }] };
const schedule = (day = "2026-12-01", extra = {}) => ({ v: 1, title: "6병동 민지", ttlDays: 30, types: [{ id: 1, name: "데이", short: "D", color: "#3B82F6", time: "07:00-15:00" }],
  days: [{ date: day, type: 1 }], ...extra });
const next = new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth() + 1, 1));
const month = next.toISOString().slice(0, 7);
const days = new Date(Date.UTC(next.getUTCFullYear(), next.getUTCMonth() + 1, 0)).getUTCDate();
const row = (l) => Array.from({ length: days }, (_, i) => (i === 6 ? l : "O"));
// 처리방침 3.14가 시행된 서버(확인했어요·피곤해요 휴대폰 수가 켜짐). 끈 서버는 아래 [POLICY-314] 시험이 봐요.
const ON = { POLICY_314_FROM: "2026-01-01" };

test("[RVQ-19] SVC-03·T22-01·T22-03: a HEAD, a preview robot or a link checker does not set 열어 봄 on a contract, a call, a swap or an ask; a person does", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const robots = async (path) => {
    assert.equal((await call(env, path, { method: "HEAD" })).status, 200, `${path} HEAD`);
    for (const h of [BOT, TEAMS, SCANNER]) assert.equal((await call(env, path, { headers: h })).status, 200);
  };
  // 근로계약서: '열어 봄' on the 사장님's phone.
  const sign = await (await call(env, "/api/sign", post(contract))).json();
  const signSeen = async () => (await (await call(env, `/api/sign/${sign.id}`, { headers: { "x-delete-token": sign.token } })).json()).seenAt;
  await robots(`/k/${sign.key}`);
  assert.equal(await signSeen(), null, "pasting the link in a chat is not the staff member opening it");
  await call(env, `/k/${sign.key}`, { headers: PHONE });
  assert.ok(await signSeen());
  // 결원 메우기.
  const c = await (await call(env, "/api/call", post({ team: "6병동", title: "12/7 나이트 대신", people: ["다", "라"] }))).json();
  const callSeen = async () => (await (await call(env, `/api/call/${c.id}`, { headers: { "x-delete-token": c.token } })).json()).seen;
  await robots(`/v/${c.people[0].key}`);
  assert.deepEqual(await callSeen(), []);
  await call(env, `/v/${c.people[0].key}`, { headers: PHONE });
  assert.deepEqual(await callSeen(), [0]);
  // 바꾸기.
  const w = await (await call(env, "/api/swap", post({ team: "6병동", month, people: ["가", "나"], labels: [row("D"), row("N")], ok: { 0: { 7: [1] }, 1: { 7: [0] } } }))).json();
  const swapSeen = async () => (await (await call(env, `/api/swap/${w.id}`, { headers: { "x-delete-token": w.token } })).json()).seen;
  await robots(`/w/${w.people[1].key}`);
  assert.deepEqual(await swapSeen(), []);
  await call(env, `/w/${w.people[1].key}`, { headers: PHONE });
  assert.deepEqual(await swapSeen(), [1]);
  // 신청 받기.
  const a = await (await call(env, "/api/ask", post({ team: "6병동", month: "2026-12", members: ["가", "나"], shifts: [{ id: 1, label: "D", color: "#2f7d5b" }],
    rules: { weekdayCap: 2, weekendCap: 3, kinds: ["OFF"], holidays: [] } }))).json();
  const askSeen = async () => (await (await call(env, `/api/ask/${a.id}`, { headers: { "x-delete-token": a.token } })).json()).seen;
  await robots(`/a/${a.members[0].key}`);
  assert.deepEqual(await askSeen(), []);
  await call(env, `/a/${a.members[0].key}`, { headers: PHONE });
  assert.deepEqual(await askSeen(), [0]);
});

test("[RVQ-19] U22-01·I22b-01: a link the leader watches asks 확인했어요 once per change, from the page or the app; the leader reads it apart from 열어 봄", async () => {
  readsForget();
  const env = { SHARES: kv(), ...ON };
  const m = await (await call(env, "/api/share", post(schedule()))).json();
  const seen = () => call(env, `/api/share/${m.id}/seen`, { headers: { "x-delete-token": m.deleteToken } }).then((r) => r.json());
  // Not watched yet: no button, and a press does nothing.
  assert.doesNotMatch(await (await call(env, `/s/${m.id}`, { headers: PHONE })).text(), /확인했어요/);
  assert.deepEqual(await seen(), { seenAt: null, okAt: null, updatedAt: (await seen()).updatedAt });
  // Watched now (the leader's app asked once). The page offers the press under the month.
  const page = await (await call(env, `/s/${m.id}`, { headers: PHONE })).text();
  assert.match(page, /다 봤으면 리더에게 알려 주세요/);
  assert.match(page, new RegExp(`action="/s/${m.id}/ok"`));
  assert.doesNotMatch(page, /<script/i);
  let got = await seen();
  assert.ok(got.seenAt, "opening is 열어 봄");
  assert.equal(got.okAt, null, "but not 확인");
  const pressed = await call(env, `/s/${m.id}/ok`, form());
  assert.equal(pressed.status, 303);
  assert.equal(pressed.headers.get("location"), `/s/${m.id}?k=1`);
  assert.match(await (await call(env, `/s/${m.id}?k=1`, { headers: PHONE })).text(), /확인했다고 리더에게 알렸어요/);
  got = await seen();
  assert.ok(got.okAt);
  assert.match(await (await call(env, `/s/${m.id}`, { headers: PHONE })).text(), /\d+월 \d+일 \d{2}:\d{2}에 확인했어요/);
  // A change asks again.
  await call(env, `/api/share/${m.id}`, { method: "PUT", body: JSON.stringify(schedule("2026-12-02")), headers: { "content-type": "application/json", "x-delete-token": m.deleteToken } });
  got = await seen();
  assert.equal(got.okAt, null);
  assert.equal(got.seenAt, null);
  // The member's app: the link says it is watched, and the app may press too.
  const app = await call(env, `/api/share/${m.id}`, { headers: { "x-bamnat-quiet": "1" } });
  const body = await app.json();
  assert.equal(body.watch, true);
  assert.equal((await seen()).seenAt, null, "the app's background check is not the member looking");
  // The member opens 받은 달력 later: the app already has this version (304), and it still counts as 열어 봄.
  assert.equal((await call(env, `/api/share/${m.id}`, { headers: { "if-none-match": app.headers.get("etag") } })).status, 304);
  assert.ok((await seen()).seenAt);
  assert.equal((await call(env, `/api/share/${m.id}/ok`, { method: "POST" })).status, 204);
  assert.ok((await seen()).okAt);
  // A link nobody watches: the press goes nowhere.
  const other = await (await call(env, "/api/share", post(schedule()))).json();
  const off = await call(env, `/s/${other.id}/ok`, form());
  assert.equal(off.headers.get("location"), `/s/${other.id}?k=off`);
  assert.equal(env.SHARES.map.has(`ok:${other.id}`), false);
  assert.equal((await call(env, `/api/share/${other.id}/ok`, { method: "POST" })).status, 404);
  // The sharer ends the watched link: 열어 봄 and 확인 go with it at once.
  assert.ok(env.SHARES.map.has(`ok:${m.id}`) && env.SHARES.map.has(`seen:${m.id}`));
  assert.equal((await call(env, `/api/share/${m.id}`, { method: "DELETE", headers: { "x-delete-token": m.deleteToken } })).status, 204);
  assert.equal(env.SHARES.map.has(`ok:${m.id}`), false);
  assert.equal(env.SHARES.map.has(`seen:${m.id}`), false);
});

test("[RVQ-20] SVC-11: a team page made without the app's header keeps its words but covers an account, a phone, a link and a messenger", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const scam = { ...contract, items: [{ t: "보증금", b: "보증금은 국민 123456-12-345678로 보내 주세요. 문의 010-1234-5678, 카톡 id abc12, https://evil.ru/pay" },
    { t: "사업장", b: "사업자등록번호 123-45-67890 · 11월 12일 09:00 시작" }] };
  // The 1.39+ app is told why and fixes it.
  const refused = await call(env, "/api/sign", post(scam, { "x-bamnat-app": "54" }));
  assert.equal(refused.status, 400);
  assert.equal((await refused.json()).error, "contact");
  // Without the header (a 1.38 app, or made outside the app): kept, covered.
  const made = await call(env, "/api/sign", post(scam));
  assert.equal(made.status, 201);
  const page = await (await call(env, `/k/${(await made.json()).key}`, { headers: PHONE })).text();
  for (const gone of ["123456-12-345678", "010-1234-5678", "abc12", "evil.ru"]) assert.ok(!page.includes(gone), gone);
  assert.match(page, /보증금은 국민 \*\*\*로 보내 주세요/);
  assert.match(page, /사업자등록번호 123-45-67890 · 11월 12일 09:00 시작/, "a business number, a date and a time stay");
  assert.equal(scamMaskText("오픈채팅으로 오세요"), "***으로 오세요");
  assert.equal(scamMaskText("메일 kim@naver.com"), "메일 ***");
  assert.equal(scamMaskText("3교대 @ 6병동"), "3교대 @ 6병동");
});

test("[RVQ-20] a messenger id right after the messenger's name is covered too, and a factory line name is not", async () => {
  readsForget();
  assert.equal(scamMaskText("되시는 분은 카톡 nurse_min7로 연락 주세요"), "되시는 분은 ***로 연락 주세요");
  assert.equal(scamMaskText("텔레그램: abcd1234"), "***");
  assert.equal(scamMaskText("생산 라인 SMT01 야간 결원"), "생산 라인 SMT01 야간 결원");
  assert.equal(scamMaskText("카톡 OK면 답 주세요"), "카톡 OK면 답 주세요");
  // The 1.39 app is told why, as before.
  const env = { SHARES: kv() };
  const r = await call(env, "/api/call", post({ team: "6병동", title: "대타", detail: "카톡 nurse_min7로 연락 주세요", people: ["다"] }, { "x-bamnat-app": "54" }));
  assert.equal(r.status, 400);
  assert.equal((await r.json()).error, "contact");
  assert.equal((await call(env, "/api/call", post({ team: "3공장", title: "라인 SMT01 야간 대타", detail: "22:00~07:00", people: ["다"] }, { "x-bamnat-app": "54" }))).status, 201);
});

test("[RVQ-20] SVC-12: every request from one network counts before KV is read, whatever its way", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const m = await (await call(env, "/api/share", post(schedule()), "198.51.100.7")).json();
  const was = LIMITS.callsPerHour;
  LIMITS.callsPerHour = 5;
  try {
    readsForget();
    for (let i = 0; i < 5; i++) await call(env, `/api/share/${m.id}`, { method: "PUT", body: "{}", headers: { "x-delete-token": "wrong" } }, "198.51.100.7");
    const before = env.SHARES.gets();
    const held = await call(env, `/api/share/${m.id}/report`, post({ reason: "spam" }), "198.51.100.7");
    assert.equal(held.status, 429);
    assert.equal(env.SHARES.gets(), before, "held before KV is read");
    assert.equal((await call(env, "/api/health", {}, "198.51.100.7")).status, 200, "the health check is never held");
    assert.equal((await call(env, `/s/${m.id}`, {}, "198.51.100.8")).status, 200, "another network goes on");
  } finally {
    LIMITS.callsPerHour = was;
    readsForget();
  }
});

test("[RVQ-20] SVC-13: two Workers together hold one network to the hour's count", async () => {
  readsForget();
  const store = kv();
  // Two Workers: the same KV, each with its own memory (the limit's memory is kept per store object).
  const one = { SHARES: store, RATE_SALT: "s" };
  const two = { SHARES: { get: (k, t) => store.get(k, t), put: (k, v, o) => store.put(k, v, o), delete: (k) => store.delete(k), list: (o) => store.list(o) }, RATE_SALT: "s" };
  let ok = 0;
  for (let i = 0; i < 30; i++) {
    const r = await call(i % 2 ? two : one, "/api/share", post(schedule()), "192.0.2.77");
    if (r.status === 201) ok += 1;
    else assert.equal(r.status, 429);
  }
  assert.equal(ok, LIMITS.createsPerHour, "not twice the count");
});

test("[RVQ-21] SVC-14·T22-04: two links pressed from one phone count once, and a team where all but one pressed shows no number", async () => {
  readsForget();
  const env = { SHARES: kv(), ...ON };
  const links = [];
  for (let i = 0; i < 6; i++) links.push(await (await call(env, "/api/share", post(schedule()))).json());
  const box = await (await call(env, "/api/tired", post({ team: "6병동" }))).json();
  for (const l of links) await call(env, `/api/share/${l.id}/tired`, { method: "PUT", body: JSON.stringify({ box: box.id }), headers: { "x-delete-token": l.deleteToken } });
  const today = async () => (await (await call(env, `/api/tired/${box.id}`, { headers: { "x-delete-token": box.token } })).json()).days[0].n;
  const press = (i, phone, ip) => call(env, `/api/share/${links[i].id}/tired`, { method: "POST", headers: { "user-agent": phone } }, ip);
  // The leader presses two links of their own from one phone, then one member presses: three presses, two phones.
  await press(0, "Dalvik/2.1.0 (Linux; U; Android 14; leader)", "203.0.113.9");
  await press(1, "Dalvik/2.1.0 (Linux; U; Android 14; leader)", "203.0.113.9");
  await press(2, "Dalvik/2.1.0 (Linux; U; Android 13; member-a)", "198.51.100.20");
  assert.equal(await today(), null, "one member's press is not shown");
  await press(3, "Dalvik/2.1.0 (Linux; U; Android 14; member-b)", "198.51.100.21");
  assert.equal(await today(), 3, "three phones, two links that did not press: the number shows (phones, not presses)");
  await press(4, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)", "198.51.100.22");
  assert.equal(await today(), null, "all but one pressed: no number, so the one is not pointed at");
  // The box keeps how many links are tied, never which link pressed.
  const kept = JSON.parse(env.SHARES.map.get(`tired:${box.id}`));
  assert.equal(kept.links.length, 6);
  assert.ok(!JSON.stringify({ days: kept.days, people: kept.people }).includes(links[2].id));
  // A day from before 2.0.0 (no phone count) keeps the old rule.
  assert.equal(tiredShown({ days: { "2026-11-01": 4 } }, "2026-11-01"), 4);
  assert.equal(tiredShown({ days: { "2026-11-01": 2 } }, "2026-11-01"), null);
});

test("[RVQ-22] SVC-35·U22-09: the calendar feed defines the zone its times name", () => {
  const ics = renderIcs({ ...schedule("2026-12-01", { ics: true, tz: "Asia/Seoul" }) }, "abc", "2026-11-12T00:00:00Z");
  const lines = ics.split("\r\n");
  const at = lines.indexOf("BEGIN:VTIMEZONE");
  assert.ok(at > 0 && at < lines.indexOf("BEGIN:VEVENT"), "the zone comes before the events");
  assert.ok(lines.includes("TZID:Asia/Seoul") && lines.includes("TZOFFSETTO:+0900") && lines.includes("TZOFFSETFROM:+0900"));
  assert.ok(lines.includes("DTSTART;TZID=Asia/Seoul:20261201T070000"));
  // A zone with summer time: each change between the first and the last day.
  const ny = vtimezone("America/New_York", "2026-02-01", "2026-12-01");
  assert.deepEqual(ny.filter((l) => l.startsWith("DTSTART")), ["DTSTART:19700101T000000", "DTSTART:20260308T020000", "DTSTART:20261101T020000"]);
  assert.ok(ny.includes("BEGIN:DAYLIGHT") && ny.includes("TZOFFSETTO:-0400"));
  assert.deepEqual(vtimezone("Nowhere/Town", "2026-02-01", "2026-03-01"), [], "a zone the data does not know: as before");
});

test("[RVQ-23] SVC-36: a leader's change with one bad line keeps nothing of it", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const w = await (await call(env, "/api/swap", post({ team: "6병동", month, people: ["가", "나"], labels: [row("D"), row("N")], ok: { 0: { 7: [1] }, 1: { 7: [0] } } }))).json();
  const read = async () => (await call(env, `/api/swap/${w.id}`, { headers: { "x-delete-token": w.token } })).json();
  const bad = await call(env, `/api/swap/${w.id}`, { method: "PUT", body: JSON.stringify({ closed: true, labels: [row("N"), row("D")], decide: [{ p: 9, d: 7, state: "done" }] }),
    headers: { "content-type": "application/json", "x-delete-token": w.token } });
  assert.equal(bad.status, 400);
  assert.equal((await read()).closed, false, "the board and the switch stay as they were");
  const good = await call(env, `/api/swap/${w.id}`, { method: "PUT", body: JSON.stringify({ closed: true }), headers: { "content-type": "application/json", "x-delete-token": w.token } });
  assert.equal(good.status, 204);
  assert.equal((await read()).closed, true);
});

test("[RVQ-25] I22a-04·SVC-07: a member's page of an 80-person round reads a few keys, not one per member; two presses at once settle a minute later", async (t) => {
  readsForget();
  const env = { SHARES: kv() };
  const names = Array.from({ length: 80 }, (_, i) => `사람${i + 1}`);
  const made = await (await call(env, "/api/ask", post({ team: "6병동", month, members: names, shifts: [{ id: 1, label: "D", color: "#2f7d5b" }],
    rules: { weekdayCap: 5, weekendCap: 5, kinds: ["OFF", "LEAVE", "SHIFT"] } }))).json();
  const keys = made.members.map((x) => x.key);
  const d = `${month}-07`;
  const open = async (k) => {
    const before = env.SHARES.gets();
    const r = await call(env, `/a/${k}`, { headers: PHONE });
    assert.equal(r.status, 200);
    const page = await r.text();
    return { reads: env.SHARES.gets() - before, room: page.match(new RegExp(`href="/a/${k}\\?d=${d}#pick">(.*?)</a>`))?.[1] ?? "" };
  };
  // The first open builds the round's summary from every member (as every open did before); the next opens read it once.
  assert.ok((await open(keys[0])).reads > 80);
  const second = await open(keys[1]);
  assert.ok(second.reads <= 5, `an open reads ${second.reads} keys`);
  // A press checks 선착순 against every member's own key, and the others see it at once.
  const pick = (i, ip) => call(env, `/a/${keys[i]}/pick`, form(`d=${d}&k=OFF`), ip);
  assert.equal((await pick(2, "203.0.113.61")).status, 200);
  assert.match((await open(keys[3])).room, /1\/5/);
  // Two presses at once: each wrote the summary from what it read, so the later one has no part of the earlier (KV keeps the last).
  assert.equal((await pick(4, "203.0.113.62")).status, 200);
  const sum = JSON.parse(env.SHARES.map.get(`askx:${made.id}`));
  delete sum.parts[2];
  env.SHARES.map.set(`askx:${made.id}`, JSON.stringify(sum));
  assert.match((await open(keys[5])).room, /1\/5/, "for a minute the page may miss the other press");
  // A minute later the first open reads the truth once and settles it.
  t.mock.timers.enable({ apis: ["Date"], now: Date.now() + LIMITS.settleMs + 1000 });
  const settle = await open(keys[6]);
  assert.match(settle.room, /2\/5/);
  assert.ok(settle.reads > 80);
  assert.equal(JSON.parse(env.SHARES.map.get(`askx:${made.id}`)).settled, true);
  const after = await open(keys[7]);
  assert.match(after.room, /2\/5/);
  assert.ok(after.reads <= 5);
  // The leader's app reads every member's own key, as before.
  const read = await (await call(env, `/api/ask/${made.id}`, { headers: { "x-delete-token": made.token } })).json();
  assert.equal(read.wishes.length, 2);
  // The leader takes the round down: the summary goes too.
  assert.equal((await call(env, `/api/ask/${made.id}`, { method: "DELETE", headers: { "x-delete-token": made.token } })).status, 204);
  assert.equal(env.SHARES.map.has(`askx:${made.id}`), false);
  t.mock.timers.reset();
});

test("[RVQ-25] I22a-04: a swap page reads asks to me from the round's summary; a press, a reply and the leader's word keep it", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const people = Array.from({ length: 40 }, (_, i) => `사람${i + 1}`);
  const labels = people.map((_, i) => row(i % 2 ? "N" : "D"));
  const made = await (await call(env, "/api/swap", post({ team: "6병동", month, people, labels, ok: { 0: { 7: [1] }, 1: { 7: [0] } } }))).json();
  const keys = made.people.map((x) => x.key);
  const open = async (i) => {
    const before = env.SHARES.gets();
    const page = await (await call(env, `/w/${keys[i]}`, { headers: PHONE })).text();
    return { reads: env.SHARES.gets() - before, page };
  };
  assert.ok((await open(0)).reads > 40, "the first open builds the summary");
  assert.ok((await open(1)).reads <= 5);
  // 가 asks 나 for day 7: 나's page shows it from the summary.
  assert.equal((await call(env, `/w/${keys[0]}/ask`, form("d=7&q=1"), "203.0.113.71")).status, 200);
  let seen = await open(1);
  assert.match(seen.page, /받은 요청/);
  assert.ok(seen.reads <= 5);
  // 나 says yes; the leader approves; 나's page says so.
  assert.equal((await call(env, `/w/${keys[1]}/reply`, form("from=0&d=7&a=yes"), "203.0.113.72")).status, 200);
  assert.equal((await call(env, `/api/swap/${made.id}`, { method: "PUT", body: JSON.stringify({ decide: [{ p: 0, d: 7, state: "done" }] }), headers: { "content-type": "application/json", "x-delete-token": made.token } })).status, 204);
  seen = await open(1);
  assert.match(seen.page, /리더가 승인했어요/);
  assert.equal((await call(env, `/api/swap/${made.id}`, { method: "DELETE", headers: { "x-delete-token": made.token } })).status, 204);
  assert.equal(env.SHARES.map.has(`swapx:${made.id}`), false);
});

test("[POLICY-314] 처리방침 3.14 전(스위치 꺼짐): 확인했어요 칸·저장·watch·okAt이 없고, 피곤해요는 누른 수로 세고 표시를 남기지 않아요", async () => {
  readsForget();
  // 저장소의 기본값은 꺼짐이에요(3.14 시행일이 정해지면 한 줄에 날짜를 적어요).
  assert.equal(POLICY_314_FROM, "");
  assert.equal(policy314({}), false);
  assert.equal(policy314({ POLICY_314_FROM: "언젠가" }), false);
  // 그날 0시(한국 시간)부터 켜져요.
  const day = Date.parse("2026-12-14T00:00:00+09:00");
  assert.equal(policy314({ POLICY_314_FROM: "2026-12-14" }, day - 1), false);
  assert.equal(policy314({ POLICY_314_FROM: "2026-12-14" }, day), true);
  // 날짜가 아직 오지 않은 서버도 꺼진 것과 같아요.
  const env = { SHARES: kv(), POLICY_314_FROM: "2999-01-01" };
  const m = await (await call(env, "/api/share", post(schedule()))).json();
  const seen = () => call(env, `/api/share/${m.id}/seen`, { headers: { "x-delete-token": m.deleteToken } }).then((r) => r.json());
  // 리더 앱이 물어봐도(열어 봄은 1.38부터 그대로) okAt은 내보내지 않아요: 리더 앱은 '열어 본 사람' 줄만 보여요.
  const first = await seen();
  assert.equal("okAt" in first, false);
  await call(env, `/s/${m.id}`, { headers: PHONE });
  assert.ok((await seen()).seenAt, "열어 봄은 그대로 적어요");
  // 페이지에 확인 칸이 없고, 누르기는 저장하지 않아요(페이지 ?k=off, 앱 404 → 앱은 단추를 거둬요).
  assert.doesNotMatch(await (await call(env, `/s/${m.id}`, { headers: PHONE })).text(), /확인했어요|다 봤으면 리더에게/);
  const pressed = await call(env, `/s/${m.id}/ok`, form());
  assert.equal(pressed.headers.get("location"), `/s/${m.id}?k=off`);
  assert.equal((await call(env, `/api/share/${m.id}/ok`, { method: "POST" })).status, 404);
  assert.equal(env.SHARES.map.has(`ok:${m.id}`), false);
  assert.equal("okAt" in (await seen()), false);
  // 팀원 앱도 watch를 모르고(단추를 띄우지 않아요), 켜지는 날 etag가 바뀌어 다시 받아요.
  const off = await call(env, `/api/share/${m.id}`, { headers: { "x-bamnat-quiet": "1" } });
  assert.equal((await off.json()).watch, undefined);
  const onEnv = { SHARES: env.SHARES, ...ON };
  const on = await call(onEnv, `/api/share/${m.id}`, { headers: { "x-bamnat-quiet": "1", "if-none-match": off.headers.get("etag") } });
  assert.equal(on.status, 200, "켜진 날에는 304가 아니라 watch가 붙은 새 답이에요");
  assert.equal((await on.json()).watch, true);

  // 피곤해요: 지금 실서버처럼 누른 수로 세요. 휴대폰 표시(해시)·휴대폰 수를 남기지 않아요.
  const links = [];
  for (let i = 0; i < 6; i++) links.push(await (await call(env, "/api/share", post(schedule()))).json());
  const box = await (await call(env, "/api/tired", post({ team: "6병동" }))).json();
  for (const l of links) await call(env, `/api/share/${l.id}/tired`, { method: "PUT", body: JSON.stringify({ box: box.id }), headers: { "x-delete-token": l.deleteToken } });
  const today = async () => (await (await call(env, `/api/tired/${box.id}`, { headers: { "x-delete-token": box.token } })).json()).days[0].n;
  const press = (i, phone, ip) => call(env, `/api/share/${links[i].id}/tired`, { method: "POST", headers: { "user-agent": phone } }, ip);
  await press(0, "Dalvik/2.1.0 (Linux; U; Android 14; leader)", "203.0.113.9");
  await press(1, "Dalvik/2.1.0 (Linux; U; Android 14; leader)", "203.0.113.9");
  await press(2, "Dalvik/2.1.0 (Linux; U; Android 13; member-a)", "198.51.100.20");
  assert.equal(await today(), 3, "3.14 전에는 누른 수(링크 셋)로 세요");
  // 새 정보 없이 지키는 보호는 그대로예요: 한 링크만 빼고 모두 누른 날은 숫자가 없어요.
  await press(3, "b", "198.51.100.21");
  await press(4, "c", "198.51.100.22");
  assert.equal(await today(), null);
  const kept = JSON.parse(env.SHARES.map.get(`tired:${box.id}`));
  assert.equal(kept.marks, undefined, "휴대폰 표시를 남기지 않아요");
  assert.equal(kept.people, undefined, "휴대폰 수를 남기지 않아요");
  assert.equal(kept.links.length, 6);
});
