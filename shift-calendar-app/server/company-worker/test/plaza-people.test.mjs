// 1.39 [PLZC-10][PLZC-03][PLZC-12] 광장 사람 먼저: 근무 태그(정해진 값만, 방의 근무 유형만, 밤낮 n개월은 서버 셈), 지난 글 더 보기, 머리 숫자.
// 깃발 plaza.people은 처리방침 3.12에 묶여 그 전에는 태그를 받지도 주지도 않아요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { peopleTag, peopleMonths } from "../src/plaza-people.js";
import { server, device, from, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-08T03:00:00Z");
const DAY = 24 * 3600e3;
const as = (n) => ({ ...from(n), "x-device": device(n) });
const refuse = (field) => { throw new Error(field); };

async function policyOn(call) {
  const h = await signIn(call);
  const r = await call("POST", "/api/admin/policy", { version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, h);
  assert.equal(r.status, 200, JSON.stringify(r.data));
}
const write = (call, n, place, body, tag, extra = {}) =>
  call("POST", "/api/posts", { device: device(n), company: place, setup: "room", body, ...(tag ? { tag } : {}), ...extra }, from(n));
const read = (call, n, place, q = "") => call("GET", `/api/companies/${place}/setups/room/posts${q}`, undefined, as(n));

test("[PLZC-10] a tag takes only keys from the lists, and only the room's own kind of work", () => {
  assert.deepEqual(peopleTag({ s: "SHIFT", f: "FOUR_TEAM", i: "semi", n: "night" }, "style-shift", refuse), { s: "SHIFT", f: "FOUR_TEAM", i: "semi", n: "night" });
  assert.equal(peopleTag(undefined, "style-shift", refuse), null);
  for (const bad of [{ s: "DAY" }, { s: "SHIFT", f: "OFFICE" }, { s: "SHIFT", i: "우리 회사" }, { s: "SHIFT", note: "hi" }, { s: "SHIFT", m: 7 }, ["SHIFT"]]) {
    assert.throws(() => peopleTag(bad, "style-shift", refuse), /tag/, JSON.stringify(bad));
  }
  // 업종 means nothing to 알바, 근무 중 nothing to 가게.
  assert.throws(() => peopleTag({ s: "HOURLY", i: "semi" }, "style-hourly", refuse));
  assert.throws(() => peopleTag({ s: "BUSINESS", n: "day" }, "style-shop", refuse));
  assert.equal(peopleTag({ s: "SHIFT", m: true }, "style-shift", refuse).wantsMonths, true);
  assert.equal(JSON.stringify(peopleTag({ s: "SHIFT", m: true }, "style-shift", refuse)), '{"s":"SHIFT"}');
  assert.equal(peopleMonths(NOW - 95 * DAY, NOW), 3);
  assert.equal(peopleMonths(NOW, NOW), 0);
  assert.equal(peopleMonths(undefined, NOW), null);
});

test("[PLZC-10] before 3.12 a tag is neither kept nor shown", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  assert.equal((await write(call, 1, "style-shift", "야간 끝나고 잠 어떻게 자요?", { s: "SHIFT", f: "FOUR_TEAM" })).status, 201);
  const p = (await read(call, 2, "style-shift")).data;
  assert.equal(p.posts[0].tag ?? null, null);
  assert.equal(p.pulse, undefined);
});

test("[PLZC-10][PLZC-12] after 3.12: the tag is kept as written, months the server's own, the room's head line", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  await policyOn(call);
  // Months shown only when asked (m: true), counted from the first time the server saw the phone.
  assert.equal((await write(call, 1, "style-shift", "4조 2교대 야간 연속 어떻게 버텨요?", { s: "SHIFT", f: "FOUR_TEAM", n: "night", m: true }, { kind: "question" })).status, 201);
  assert.equal((await write(call, 2, "style-shift", "저는 낮잠 두 번 나눠 자요", { s: "SHIFT", f: "THREE_SHIFT" })).status, 201);
  assert.equal((await write(call, 3, "style-shift", "일근 태그", { s: "DAY" })).status, 400, "a 일근 tag in 교대 광장");
  assert.equal((await write(call, 3, "style-shift", "업종 마음대로", { s: "SHIFT", i: "우리공장" })).status, 400);
  const p = (await read(call, 4, "style-shift")).data;
  const [a, b] = [p.posts.find((x) => x.body.startsWith("4조")), p.posts.find((x) => x.body.startsWith("저는"))];
  assert.deepEqual(a.tag, { s: "SHIFT", f: "FOUR_TEAM", n: "night", m: 0 });
  assert.deepEqual(b.tag, { s: "SHIFT", f: "THREE_SHIFT" });
  // 오늘 글 2, 답 기다리는 질문 1, 근무 중 1 (the one written at work).
  assert.deepEqual(p.pulse, { today: 2, waiting: 1, working: 1 });
  // An answer from someone else: no longer waiting. The asker's own does not count.
  await call("POST", "/api/posts", { device: device(1), company: "style-shift", setup: "room", body: "저도 궁금해서 하나 더", parent: a.id }, from(1));
  assert.equal((await read(call, 4, "style-shift")).data.pulse.waiting, 1);
  await call("POST", "/api/posts", { device: device(2), company: "style-shift", setup: "room", body: "암막 커튼 추천해요", parent: a.id }, from(2));
  assert.equal((await read(call, 4, "style-shift")).data.pulse.waiting, 0);
  // [PLZC-15] 첫 글: a phone's first post in any kind of work's plaza.
  const firsts = (await read(call, 4, "style-shift")).data.posts.filter((x) => !x.parent);
  assert.deepEqual(firsts.map((x) => x.first ?? false), [true, true]);
  t.mock.timers.setTime(NOW + 60e3);
  assert.equal((await write(call, 2, "style-shift", "두 번째 글이에요", { s: "SHIFT" })).status, 201);
  assert.equal((await read(call, 4, "style-shift")).data.posts.find((x) => x.body === "두 번째 글이에요").first, undefined);
  // [PLZC-16] 도움 n: shown beside a 마을 이름 once someone else's question picked the answer.
  const ans = (await read(call, 1, "style-shift")).data.posts.find((x) => x.body === "암막 커튼 추천해요");
  assert.equal((await call("POST", `/api/posts/${a.id}/answer`, { device: device(1), reply: ans.id }, from(1))).status, 200);
  assert.equal((await call("POST", "/api/nick", { device: device(2), nick: "밤샘곰" }, from(2))).status, 200);
  const named = (await read(call, 4, "style-shift")).data.posts.find((x) => x.body === "암막 커튼 추천해요");
  assert.deepEqual([named.nick, named.helped], ["밤샘곰", 1]);
  // 일근 광장 takes its own kind of work.
  assert.equal((await write(call, 5, "style-day", "점심 몇 시에 드세요?", { s: "DAY", f: "OFFICE" })).status, 201);
  assert.deepEqual((await read(call, 4, "style-day")).data.posts[0].tag, { s: "DAY", f: "OFFICE" });
});

test("[PLZC-03] 지난 글 더 보기: posts before a time, a page at a time, with the answers under them", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW - 200 * 60e3 });
  const { call } = server();
  const ids = [];
  for (let i = 0; i < 130; i++) {
    t.mock.timers.setTime(NOW - (200 - i) * 60e3);
    const r = await write(call, 10 + (i % 40), "style-hourly", `알바 이야기 ${i}번째예요`);
    assert.equal(r.status, 201, JSON.stringify(r.data));
    ids.push(r.data.id);
  }
  t.mock.timers.setTime(NOW);
  await call("POST", "/api/posts", { device: device(1), company: "style-hourly", setup: "room", body: "옛 글에 답", parent: ids[5] }, from(1));
  const first = (await read(call, 2, "style-hourly")).data;
  assert.equal(first.more, true);
  const oldest = Math.min(...first.posts.filter((p) => !p.parent).map((p) => Date.parse(p.at)));
  const page = (await read(call, 2, "style-hourly", `?before=${new Date(oldest).toISOString()}`)).data;
  assert.equal(page.older, true);
  const tops = page.posts.filter((p) => !p.parent);
  assert.ok(tops.length > 0 && tops.length <= 60);
  assert.ok(tops.every((p) => Date.parse(p.at) < oldest));
  assert.ok(page.posts.some((p) => p.parent === ids[5]) || !tops.some((p) => p.id === ids[5]), "an answer comes with its post");
});

test("[PLZC-40] 제작자 표식: 🏅 이달의 제작자·⭐ 인기 제작자 on 장터 cards and 광장 posts, after 3.12, resting while held", async (t) => {
  // 장터 올리기 opens on 3.8's date (as in recipe-community.test.mjs).
  const LATER = Date.parse("2026-11-30T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: LATER });
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  const recipe = (title) => ({ format: "bamnat-recipe", v: 1, title, summary: "야간이 잘 보여요", styles: ["SHIFT"],
    pieces: [{ kind: "shiftLook", items: [{ name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }] });
  const up = async (n, title) => {
    const r = await call("POST", "/api/recipes", { device: device(n), recipe: recipe(title), scope: "public" }, from(n));
    assert.equal(r.status, 201, JSON.stringify(r.data));
    await call("POST", `/api/admin/recipes/${r.data.code}/approve`, {}, h);
    return r.data.code;
  };
  const a = await up(1, "올빼미 교대 세트");
  const b = await up(2, "새벽 감자 세트");
  const makerOf = async (code) => (await call("GET", `/api/recipes/${code}`)).data.maker;
  const [ma, mb] = [await makerOf(a), await makerOf(b)];
  for (let n = 10; n < 40; n++) await call("POST", `/api/recipes/makers/${mb}/follow`, { device: device(n) }, from(n));
  // Before 3.12: followers (already on the profile), no marks.
  let card = (await call("GET", `/api/recipes/${b}`)).data;
  assert.deepEqual([card.followers, card.mark], [30, undefined]);
  await policyOn(call);
  await call("POST", "/api/admin/recipes/makers/month", { style: "ALL", makers: [ma] }, h);
  t.mock.timers.setTime(LATER + 60e3);
  assert.equal((await call("GET", `/api/recipes/${a}`)).data.mark, "month");
  assert.deepEqual([(await call("GET", `/api/recipes/${b}`)).data.mark, (await call("GET", `/api/recipes/${b}`)).data.followers], ["popular", 30]);
  // On 광장, beside a 마을 이름.
  await call("POST", "/api/nick", { device: device(1), nick: "올빼미" }, from(1));
  const said = (await write(call, 1, "style-shift", "새 세트 올렸어요 써 보세요", { s: "SHIFT" })).data.id;
  assert.equal((await read(call, 5, "style-shift")).data.posts.find((p) => p.id === said).mark, "month");
  // Held by the operator: the mark rests.
  await call("POST", "/api/admin/care/warn", { post: said, reason: "abuse" }, h);
  await call("POST", "/api/admin/care/hold", { post: said, scope: "opinion", days: 7, reason: "abuse" }, h);
  assert.equal((await call("GET", `/api/recipes/${a}`)).data.mark, undefined);
  assert.equal((await read(call, 5, "style-shift")).data.posts.find((p) => p.id === said)?.mark, undefined);
});

test("[PLZC-41] 광장 답 바로 알림: an answer tells the asker, a 근무형태 question tells its watchers, one word-free message, after 3.12", async (t) => {
  const { generateKeyPairSync } = await import("node:crypto");
  const { fcmForget } = await import("../src/fcm.js");
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const sa = JSON.stringify({ client_email: "push@test.iam.gserviceaccount.com", project_id: "bamnat-test",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const sent = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith("https://oauth2.googleapis.com/")) return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }), { status: 200 });
    if (u.startsWith("https://fcm.googleapis.com/")) { sent.push(JSON.parse(init.body).message); return new Response("{}", { status: 200 }); }
    return real(url, init);
  };
  t.after(() => { globalThis.fetch = real; fcmForget(); });
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ FCM_SERVICE_ACCOUNT: sa });
  const token = (n) => `fcm-token-for-phone-${n}-0123456789abcdef:APA91b`;
  for (const n of [1, 2, 3]) await call("POST", "/api/push/phone", { device: device(n), token: token(n) }, from(n));
  const watch = (n, form) => call("POST", "/api/plaza/watch", { device: device(n), room: "style-shift", form }, from(n));
  // Before 3.12 nothing: no watching, no message.
  assert.equal((await watch(2, "FOUR_TEAM")).status, 403);
  const early = (await write(call, 1, "style-shift", "야간 끝나고 몇 시간 자요?", null, { kind: "question" })).data.id;
  await call("POST", "/api/posts", { device: device(2), company: "style-shift", setup: "room", body: "저는 여섯 시간이요", parent: early }, from(2));
  assert.equal(sent.length, 0);
  await policyOn(call);
  assert.equal((await watch(2, "OFFICE")).data.error, "form", "a 일근 form in 교대 광장");
  assert.equal((await watch(2, "FOUR_TEAM")).data.ok, true);
  // A 4조 2교대 question: the watcher hears, the writer and others do not.
  const q = (await write(call, 1, "style-shift", "4조 2교대 야간 이틀째 어떻게 버텨요?", { s: "SHIFT", f: "FOUR_TEAM" }, { kind: "question" })).data.id;
  assert.deepEqual(sent.map((m) => [m.token, m.data.kind]), [[token(2), "plaza_question"]]);
  // Another within 3 hours: not again.
  await write(call, 3, "style-shift", "4조 2교대 주간 끝나고 운동하세요?", { s: "SHIFT", f: "FOUR_TEAM" }, { kind: "question" });
  assert.equal(sent.length, 1);
  // An answer from someone else tells the asker; the asker's own does not.
  await call("POST", "/api/posts", { device: device(1), company: "style-shift", setup: "room", body: "저도 궁금해서요", parent: q }, from(1));
  assert.equal(sent.length, 1);
  await call("POST", "/api/posts", { device: device(3), company: "style-shift", setup: "room", body: "낮잠을 두 번 나눠 자요", parent: q }, from(3));
  assert.deepEqual(sent.slice(1).map((m) => [m.token, m.data.kind]), [[token(1), "plaza_answer"]]);
  // No words of the post go to Google.
  assert.ok(!JSON.stringify(sent).includes("낮잠") && !JSON.stringify(sent).includes("4조"));
  // A talk post's answer is not a question's: nothing.
  const talk = (await write(call, 1, "style-shift", "오늘 야간 끝!", { s: "SHIFT" })).data.id;
  t.mock.timers.setTime(NOW + 3600e3);
  await call("POST", "/api/posts", { device: device(3), company: "style-shift", setup: "room", body: "수고하셨어요", parent: talk }, from(3));
  assert.equal(sent.length, 2);
  // Stop watching.
  assert.equal((await call("DELETE", "/api/plaza/watch", { device: device(2), room: "style-shift" }, from(2))).data.ok, true);
});
