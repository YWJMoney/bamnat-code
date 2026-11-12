// 1.39 [CARE-01~06] 마을 돌봄: 첫 가려짐에 부드러운 안내, 운영자 경고, 경고를 먼저 받은 사람에게만 의견 쉬기·마을 쉬기 N일, 풀기,
// 읽음, 소식. 운영 규칙·처리방침을 고치기 전에는 꺼져 있어요(설계 7장). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { careNext, CARE } from "../src/community-care.js";
import { server, report, device, from, signIn } from "./helpers.mjs";

// 2.0.0 [RVD-18] 마을 돌봄은 코드로 2026-12-09부터(검토 LEGAL-03): 그 뒤의 하루에서 봐요.
const NOW = Date.parse("2026-12-10T03:00:00Z");
const DAY = 24 * 3600e3;
const mine = (n) => ({ "x-device": device(n) });

async function talk(call) {
  const a = await call("POST", "/api/reports", report(1), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  const say = (n, body, parent) => call("POST", "/api/posts", { device: device(n), company: c, setup: g, body, ...(parent ? { parent } : {}) }, from(n));
  const room = (n, body) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body }, from(n));
  const like = (n, id) => call("POST", `/api/posts/${id}/vote`, { device: device(n), ok: true }, from(n));
  const standing = (n) => call("GET", "/api/community/standing", undefined, mine(n));
  const flag = (id, n) => call("POST", `/api/posts/${id}/flag`, { device: device(n), reason: "abuse" }, from(n));
  return { c, g, say, room, like, standing, flag };
}

/** 처리방침 3.12 시행일이 지났어요(관리자 앱 › 기능 관리 › 일정): 깃발이 저절로 켜져요. */
async function careOnNow(call, h) {
  const r = await call("POST", "/api/admin/policy", { version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, h);
  assert.equal(r.status, 200, JSON.stringify(r.data));
}

test("[CARE] off until the rules change: no 안내, and the admin app cannot warn or hold", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  const { say, standing } = await talk(call);
  const id = (await say(2, "광고 글이에요 하나")).data.id;
  await call("POST", `/api/admin/posts/${id}`, { action: "hide", reason: "spam" }, h);
  assert.deepEqual((await standing(2)).data, {});
  const list = (await call("GET", "/api/admin/care", undefined, h)).data;
  assert.equal(list.on, false);
  assert.equal((await call("POST", "/api/admin/care/warn", { post: id, reason: "spam" }, h)).status, 409);
});

test("[CARE-01][CARE-06] only what the operator confirmed counts: 1 안내, 2 경고, 3 a limit, then each one steps up", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  await careOnNow(call, h);
  const { say, standing, flag } = await talk(call);
  // Hidden by three reports: waits for the operator, nothing said, nothing counted.
  const a = (await say(2, "여기 사람들 다 바보예요")).data.id;
  for (const n of [3, 4, 5]) await flag(a, n);
  assert.deepEqual((await standing(2)).data, {});
  // The operator looks and keeps it hidden: now it counts, with a gentle 안내.
  await call("POST", `/api/admin/posts/${a}`, { action: "hide", reason: "abuse" }, h);
  const s = (await standing(2)).data;
  assert.deepEqual([s.warning.by, s.warning.reason, s.warning.about], ["auto", "auto_first", "post"]);
  assert.equal(s.limit, undefined);
  assert.deepEqual((await standing(3)).data, {});
  // Another phone cannot mark it seen.
  assert.equal((await call("POST", "/api/community/standing/seen", { device: device(3), id: s.warning.id })).data.ok, false);
  assert.equal((await call("POST", "/api/community/standing/seen", { device: device(2), id: s.warning.id })).data.ok, true);
  assert.equal((await standing(2)).data.warning, undefined);
  const news = (await call("GET", `/api/plaza/news?since=${new Date(NOW - DAY).toISOString()}`, undefined, mine(2))).data.items;
  assert.ok(news.some((x) => x.kind === "care" && x.result === "warn"));
  // Reports that the operator found wrong leave nothing.
  const wrong = (await say(2, "야간 끝나고 다들 잘 쉬세요")).data.id;
  for (const n of [3, 4, 5]) await flag(wrong, n);
  await call("POST", `/api/admin/posts/${wrong}`, { action: "show" }, h);
  // The second confirmed one, a day later: 경고 that one more means a few days fewer.
  t.mock.timers.tick(DAY + 1000);
  const b = (await say(2, "또 바보 같은 소리네요")).data.id;
  await call("POST", `/api/admin/posts/${b}`, { action: "hide", reason: "abuse" }, await signIn(call));
  const s2 = (await standing(2)).data;
  assert.equal(s2.warning.reason, "auto_again");
  assert.equal(s2.limit, undefined);
  // The third: 3 days, 5 a day.
  const c = (await say(2, "세 번째 나쁜 말이에요")).data.id;
  await call("POST", `/api/admin/posts/${c}`, { action: "hide", reason: "abuse" }, await signIn(call));
  assert.equal((await standing(2)).data.limit.perDay, 5);
  // After it ends, the very next confirmed one steps up (7 days, 2 a day).
  t.mock.timers.tick(3 * DAY + 1000);
  const d = (await say(2, "네 번째 나쁜 말이에요")).data.id;
  await call("POST", `/api/admin/posts/${d}`, { action: "hide", reason: "abuse" }, await signIn(call));
  assert.equal((await standing(2)).data.limit.perDay, 2);
  // The admin app sees the 안내 was read; no full install id goes out.
  const list = (await call("GET", "/api/admin/care", undefined, await signIn(call))).data;
  assert.equal(list.on, true);
  assert.ok(list.warnings.some((w) => w.seen));
  assert.equal(JSON.stringify(list).includes(device(2)), false);
});

test("[CARE-10] 해명하기: a 쉬기 explained by an inquiry, accepted by the operator, is lifted and not counted", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  await careOnNow(call, h);
  const { say, standing } = await talk(call);
  const id = (await say(2, "다들 일을 너무 못하네요")).data.id;
  await call("POST", "/api/admin/care/warn", { post: id, reason: "abuse" }, h);
  const held = (await call("POST", "/api/admin/care/hold", { post: id, scope: "opinion", days: 7, reason: "abuse" }, h)).data;
  assert.equal((await say(2, "쉬는 중이에요")).status, 403);
  const appeal = (target, text, n = 2) => call("POST", "/api/community/appeal", { device: device(n), target, text }, from(n));
  assert.equal((await appeal(held.id, "짧아요")).status, 400);
  assert.equal((await appeal(held.id, "다른 분 글을 인용한 것이었어요. 제 뜻이 아니었어요.", 3)).status, 404, "only the phone it is for");
  const sent = await appeal(held.id, "다른 분 글을 인용한 것이었어요. 제 뜻이 아니었어요.");
  assert.equal(sent.status, 201, JSON.stringify(sent.data));
  assert.equal(sent.data.answerHours, 48);
  assert.equal((await appeal(held.id, "한 번 더 해명할게요. 정말 아니에요.")).status, 409);
  // The asker sees it in 내 문의; the admin app sees it as a 해명, with what it is about.
  const q = (await call("GET", `/api/admin/inquiries/${sent.data.inquiry}`, undefined, h)).data;
  assert.equal(q.appeal.about.kind, "opinion");
  assert.equal(q.appeal.result, null);
  assert.match(q.messages[0].text, /^\[해명\] 의견 쉬기 7일/);
  // Still resting while it is read; accepted, it lifts.
  assert.equal((await call("POST", `/api/admin/care/appeal/${sent.data.inquiry}`, { result: "accepted" }, h)).data.result, "accepted");
  assert.equal((await say(2, "풀린 뒤 첫 글이에요")).status, 201);
  assert.equal((await standing(2)).data.hold, undefined);
  const person = (await call("GET", `/api/admin/care/person?post=${id}`, undefined, h)).data;
  assert.deepEqual(person.next, { kind: "opinion", days: 3 }, "the accepted 쉬기 no longer counts; the 경고 does");
});

test("[CARE-02] a hold needs a warning first (unless severe); 의견 쉬기 stops posts but not votes, 마을 쉬기 stops votes too; 풀기", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  await careOnNow(call, h);
  const { say, room, like, standing } = await talk(call);
  const mark = (await say(2, "다들 일을 너무 못하네요")).data.id;
  const other = (await say(4, "야간 끝나고 다들 잘 쉬세요")).data.id;
  // No warning yet: refused, said so.
  const early = await call("POST", "/api/admin/care/hold", { post: mark, scope: "opinion", days: 3, reason: "abuse" }, h);
  assert.equal(early.status, 409);
  assert.equal(early.data.error, "warn_first");
  // 경고 with a line, then the person finds it when 마을 opens.
  const warned = await call("POST", "/api/admin/care/warn", { post: mark, reason: "abuse", note: "서로 존중하는 말로 부탁드려요" }, h);
  assert.equal(warned.status, 200, JSON.stringify(warned.data));
  const w = (await standing(2)).data.warning;
  assert.deepEqual([w.by, w.reason, w.note], ["operator", "abuse", "서로 존중하는 말로 부탁드려요"]);
  // Days outside the list are refused.
  assert.equal((await call("POST", "/api/admin/care/hold", { post: mark, scope: "opinion", days: 45, reason: "abuse" }, h)).status, 400);
  const held = (await call("POST", "/api/admin/care/hold", { post: mark, scope: "opinion", days: 3, reason: "abuse" }, h)).data;
  assert.equal(held.kind, "opinion");
  assert.equal(held.until, new Date(NOW + 3 * DAY).toISOString());
  // 의견 쉬기: no post, reply or plaza post; a vote still goes.
  const refused = await say(2, "다시 써 볼게요");
  assert.equal(refused.status, 403);
  assert.equal(refused.data.error, "muted");
  assert.deepEqual([refused.data.hold.scope, refused.data.until], ["opinion", held.until]);
  assert.equal((await say(2, "답글도 막혀요", other)).status, 403);
  assert.equal((await room(2, "광장 글도 막혀요")).status, 403);
  assert.equal((await like(2, other)).status, 200);
  assert.equal((await standing(2)).data.hold.scope, "opinion");
  // Others write as before.
  assert.equal((await say(5, "저는 그대로 써요")).status, 201);
  // 마을 쉬기 on top: votes stop too, and the earlier 의견 쉬기 is covered.
  const wide = (await call("POST", "/api/admin/care/hold", { post: mark, scope: "community", days: 7, reason: "abuse" }, h)).data;
  assert.equal((await like(2, other)).status, 403);
  assert.equal((await standing(2)).data.hold.scope, "community");
  // 풀기 the 마을 쉬기: votes come back, the 의견 쉬기 stays until its own end.
  assert.equal((await call("POST", `/api/admin/care/${wide.id}/lift`, {}, h)).data.lifted, true);
  assert.equal((await like(2, other)).status, 200);
  assert.equal((await say(2, "아직 쉬는 중")).status, 403);
  // After 3 days the 의견 쉬기 ends by itself; the daily tidy tells once.
  t.mock.timers.tick(3 * DAY + 1000);
  assert.equal((await say(2, "다시 조심해서 써요")).status, 201);
  assert.equal((await standing(2)).data.hold, undefined);
  const person = (await call("GET", `/api/admin/care/person?post=${mark}`, undefined, h)).data;
  assert.deepEqual(person.next, { kind: "community", days: 30 });
  assert.equal(person.notes.length, 3);
});

test("[CARE-02] a severe reason may hold at once; a 마을 쉬기 refuses a new rate but never 문의", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  await careOnNow(call, h);
  const { say } = await talk(call);
  const id = (await say(2, "이건 불법 정보가 담긴 글이에요")).data.id;
  const held = await call("POST", "/api/admin/care/hold", { post: id, scope: "community", days: 7, reason: "illegal" }, h);
  assert.equal(held.status, 200, JSON.stringify(held.data));
  const rate = await call("POST", "/api/rates", { device: device(2), job: "construction", region: "31", trade: "용접", unit: "day", amount: 250000 }, from(2));
  assert.equal(rate.status, 403);
  const ask = await call("POST", "/api/inquiries", { v: 1, device: device(2), kind: "question", text: "제 글이 왜 가려졌는지 알고 싶어요", info: { app: "1.39.0 (53)" } }, from(2));
  assert.ok(ask.status < 300, JSON.stringify(ask.data));
});

test("[CARE] the suggested next step goes 경고 → 의견 3일 → 마을 7일 → 마을 30일, and old records go after 180 days", () => {
  const now = NOW;
  assert.deepEqual(careNext([], now), { kind: "warn" });
  assert.deepEqual(careNext([{ kind: "warn", at: now - DAY }], now), { kind: "opinion", days: 3 });
  assert.deepEqual(careNext([{ kind: "warn", at: now - DAY }, { kind: "opinion", at: now - DAY }], now), { kind: "community", days: 7 });
  assert.deepEqual(careNext([{ kind: "community", at: now - DAY }], now), { kind: "community", days: 30 });
  assert.deepEqual(careNext([{ kind: "community", at: now - (CARE.keepDays + 1) * DAY }], now), { kind: "warn" });
});

test("[CARE] 조사 ①③⑧: the 안내 says what, by which rule and if it was explained; a hidden post can ask to be shown; the same post twice waits", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const h = await signIn(call);
  const { say, standing } = await talk(call);
  const appeal = (target, text, n = 2) => call("POST", "/api/community/appeal", { device: device(n), target, text }, from(n));
  // Before 3.12 a 해명 is an ordinary 문의, and a post twice is fine.
  const id = (await say(2, "다들 일을 너무 못하네요")).data.id;
  assert.equal((await say(2, "다들 일을 너무 못하네요")).status, 201);
  await call("POST", `/api/admin/posts/${id}`, { action: "hide", reason: "abuse" }, h);
  assert.equal((await appeal(`post:${id}`, "같이 고쳐 보자는 뜻이었어요. 다시 봐 주세요.")).status, 409);
  await careOnNow(call, h);
  // ⑧ The same words again within ten minutes wait; later they go.
  const again = await say(3, "야간 끝나고 바로 자는 게 좋아요");
  assert.equal(again.status, 201);
  const twice = await say(3, "야간 끝나고 바로 자는 게 좋아요");
  assert.deepEqual([twice.status, twice.data.error], [409, "repeat"]);
  t.mock.timers.tick(11 * 60e3);
  assert.equal((await say(3, "야간 끝나고 바로 자는 게 좋아요")).status, 201);
  // ① What the app shows: what about, checked by a person, which rule, how long to wait for an answer.
  await call("POST", "/api/admin/care/warn", { post: id, reason: "abuse", note: "다른 분을 탓하는 말이 신고됐어요" }, h);
  let s = (await standing(2)).data;
  assert.deepEqual([s.warning.about, s.warning.checked, s.warning.rule, s.warning.appeal, s.answerHours], ["post", true, "광장 운영 규칙 6항", undefined, 48]);
  // ③ The hidden post asks to be shown (only its own phone, only while hidden); accepted, it is shown and its strike goes.
  assert.equal((await appeal(`post:${id}`, "같이 고쳐 보자는 뜻이었어요. 다시 봐 주세요.", 3)).status, 404);
  const sent = await appeal(`post:${id}`, "같이 고쳐 보자는 뜻이었어요. 다시 봐 주세요.");
  assert.equal(sent.status, 201, JSON.stringify(sent.data));
  // 1.39 발굴: the 경고·쉬기 tab lists 해명 waiting for an answer with the hours left of the promised 48.
  const waiting = (await call("GET", "/api/admin/care", undefined, h)).data.appeals;
  assert.deepEqual(waiting.map((a) => [a.inquiry, a.kind, a.hoursLeft]), [[sent.data.inquiry, "post", 48]]);
  const q = (await call("GET", `/api/admin/inquiries/${sent.data.inquiry}`, undefined, h)).data;
  assert.deepEqual([q.appeal.about.kind, q.appeal.about.status, q.appeal.about.body], ["post", "hidden", "다들 일을 너무 못하네요"]);
  assert.match(q.messages[0].text, /^\[다시 보여 주세요\] 가려진 글/);
  await call("POST", `/api/admin/care/appeal/${sent.data.inquiry}`, { result: "accepted" }, h);
  assert.deepEqual((await call("GET", "/api/admin/care", undefined, h)).data.appeals, []);
  const shown = (await call("GET", "/api/admin/posts?status=all", undefined, h)).data.posts.find((p) => p.id === id);
  assert.equal(shown.status, "shown");
  // A 해명 on the 경고 shows as waiting until the operator decides.
  await appeal(s.warning.id, "다른 분을 탓하려던 게 아니었어요. 미안해요.");
  s = (await standing(2)).data;
  assert.deepEqual(s.warning.appeal, { result: null });
});

test("[CARE] 제작자 표식 쉼(careQuietMany): 쉬는 중·풀지 않은 경고 30일·신고 누적 제한이면 쉬고, 첫 안내와 푼 것은 그대로, 꺼지면 제한만", async () => {
  const { d1 } = await import("./d1.mjs");
  const { CARE_SCHEMA, careQuietMany, careQuiet } = await import("../src/community-care.js");
  const { WRITE_LIMITS_SCHEMA, WRITE_LIMITS_COLUMNS } = await import("../src/write-limits.js");
  const env = { DB: d1() };
  for (const sql of [...WRITE_LIMITS_SCHEMA, ...WRITE_LIMITS_COLUMNS, ...CARE_SCHEMA]) {
    try { await env.DB.prepare(sql).run(); } catch { /* a column already there */ }
  }
  let on = true;
  const d = {
    all: async (e, sql, ...p) => (await e.DB.prepare(sql).bind(...p).all()).results,
    legalOpen: async () => on,
  };
  const note = (id, dev, kind, reason, at, until = null, lifted = null) => env.DB.prepare(
    "INSERT INTO care_notes (id, device, kind, reason, note, days, at, until, by, ref, lifted_at) VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, 'operator', NULL, ?)")
    .bind(id, dev, kind, reason, at, until, lifted).run();
  await note("c1", "hold", "community", "abuse", NOW - DAY, NOW + 3 * DAY);
  await note("c2", "warned", "warn", "abuse", NOW - 10 * DAY);
  await note("c3", "oldwarn", "warn", "abuse", NOW - 31 * DAY);
  await note("c4", "first", "warn", "auto_first", NOW - DAY);
  await note("c5", "lifted", "warn", "abuse", NOW - DAY, null, NOW - 1000);
  await note("c6", "ended", "opinion", "abuse", NOW - 10 * DAY, NOW - DAY);
  await env.DB.prepare("INSERT INTO write_limits (device, step, since, until, per_day, times) VALUES ('limited', 1, ?, ?, 5, 1)").bind(NOW - DAY, NOW + 2 * DAY).run();
  const all = ["hold", "warned", "oldwarn", "first", "lifted", "ended", "limited", "clean"];
  assert.deepEqual([...await careQuietMany(d, env, all, NOW)].sort(), ["hold", "limited", "warned"]);
  assert.equal(await careQuiet(d, env, "clean", NOW), false);
  // A long list is read in parts under D1's 100 values.
  const many = Array.from({ length: 250 }, (_, i) => `x${i}`).concat("hold");
  assert.deepEqual([...await careQuietMany(d, env, many, NOW)], ["hold"]);
  on = false;
  assert.deepEqual([...await careQuietMany(d, env, all, NOW)], ["limited"]);
});
