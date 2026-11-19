// 2.0.0 [RVQ-34] 검토 SVC-08·SVC-09·SVC-28·U22-02·U22-03·I22a-01: 자동 순환이 사람 없이 보내는 답을 믿을 수 있게. `npm test`.
// 출시 안내는 보낼 편지함으로(실제로 간 것만 '알렸음'), 보류한 답은 나가기 전에 다시 보고 한 번만, 장부와 서버가 같은 판정을 써요.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { server, device, from, signIn } from "./helpers.mjs";
import { releaseHeld } from "../src/inquiries.js";
import { generateKeyPairSync } from "node:crypto";
import { EFFECT, effectOf, effectTick, effectVerdict } from "../src/loop.js";

const NOW = Date.parse("2026-11-20T01:00:00Z");
const MIN = 60e3;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const ask = (call, n, text, kind = "bug") => call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "1.39.0 (53)" } }).then((r) => r.data.id);
const thread = (call, id, n) => call("GET", `/api/inquiries/${id}`, undefined, { "x-device": device(n) }).then((r) => r.data);
const more = (call, id, n, text) => call("POST", `/api/inquiries/${id}/messages`, { text }, { "x-device": device(n) });
async function fiveMinutes(env, now = Date.now()) {
  const waits = [];
  await worker.scheduled({ cron: "*/5 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
}
const adminSaid = (DB, id) => DB.db.prepare("SELECT text, by FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'admin' ORDER BY id").all(id);
const told = (DB, id) => DB.db.prepare("SELECT told_at FROM dev_links WHERE inquiry_id = ?").get(id).told_at;

/** 1.39.0이 나간 서버에 2.0.0으로 고친 개발사항 하나, 묶인 문의 [texts]. 자동 출시 안내가 보류를 끝내고 5분 틱에 나가게 해 둬요. */
async function shipped(t, texts) {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const s = server();
  const owner = await signIn(s.call);
  await s.call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  const ids = [];
  for (const [i, text] of texts.entries()) ids.push(await ask(s.call, i + 1, text));
  const item = (await s.call("POST", "/api/admin/dev", { title: "주간 알람 늦음", inquiry: ids[0] }, owner)).data.id;
  for (const q of ids.slice(1)) await s.call("POST", `/api/admin/dev/${item}/links`, { inquiry: q }, owner);
  await s.call("POST", `/api/admin/dev/${item}`, { status: "done", releasedIn: "2.0.0", news: "주간 근무 알람이 제때 울려요" }, owner);
  s.DB.db.prepare("INSERT INTO loop_ships (version, seen_at, until, status, by) VALUES ('2.0.0', ?, ?, 'waiting', '자동')").run(NOW - 2 * HOUR, NOW - MIN);
  return { ...s, owner, ids, item };
}

test("[RVQ-34] SVC-08 출시 안내는 실제로 간 문의만 '알렸음'이고, 사람이 봐야 할 문의는 사람이 보낼 답으로 올라가 '해결됐나요'가 먼저 열리지 않아요", async (t) => {
  const { call, DB, env, owner, ids: [plain, money], item } = await shipped(t, ["주간 근무 알람이 30분 늦게 울려요",
    "주간 알람이 늦게 울려요. 그리고 지난달 결제한 프로 1년 환불해 주세요"]);
  await fiveMinutes(env, NOW);
  // 보통 문의: 답이 가고 '알렸음', 앱에 '해결됐나요'.
  assert.match(adminSaid(DB, plain).at(-1).text, /2\.0\.0 버전이 나왔어요/);
  assert.ok(told(DB, plain));
  assert.equal((await thread(call, plain, 1)).askFixed, true);
  // 돈 이야기가 든 문의: 답이 가지 않았으니 '알렸음'도 '해결됐나요'도 없고, 사람이 보낼 보류 답으로 올라가요.
  assert.deepEqual(adminSaid(DB, money), []);
  assert.equal(told(DB, money), null);
  assert.equal((await thread(call, money, 2)).askFixed, false);
  const held = DB.db.prepare("SELECT * FROM held_replies WHERE inquiry_id = ?").get(money);
  assert.equal(held.ship_item, item);
  assert.ok(held.until > NOW + 365 * 24 * HOUR, "사람이 누를 때까지 가지 않아요");
  assert.deepEqual(DB.db.prepare("SELECT inquiry_id, state FROM ship_outbox WHERE version = '2.0.0' ORDER BY state").all().map((r) => [r.inquiry_id, r.state]),
    [[money, "person"], [plain, "sent"]]);
  // [U22-02] 자동 순환 한 줄: 안내 1/2건 · 사람 확인 1, 공지는 올림.
  const loop = (await call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.deepEqual([loop.outbox.total, loop.outbox.sent, loop.outbox.person, loop.outbox.failed], [2, 1, 1, 0]);
  assert.equal(loop.shipped.pending, undefined);
  assert.ok(loop.shipped.noticeId);
  // [U22-03] 장부: 그 줄이 '사람 확인 대기(환불·결제·보상)'와 적어 둔 답, 최고 관리자 차례.
  const row = (await call("GET", "/api/admin/ledger?view=all", undefined, owner)).data.items.find((r) => r.id === money);
  assert.equal(row.answer.key, "person");
  assert.equal(row.answer.why, "환불·결제·보상");
  assert.equal(row.answer.held, held.id);
  assert.equal(row.answer.ship, true);
  assert.equal(row.turn, "owner");
  // 사람이 보내면 그때 '알렸음'과 편지함 '보냄'.
  t.mock.timers.tick(30 * MIN);
  assert.equal((await call("POST", `/api/admin/inquiries/${money}/held/${held.id}`, { send: true }, owner)).status, 200);
  assert.match(adminSaid(DB, money).at(-1).text, /2\.0\.0 버전이 나왔어요/);
  assert.ok(told(DB, money));
  assert.equal(DB.db.prepare("SELECT state FROM ship_outbox WHERE inquiry_id = ?").get(money).state, "sent");
  // 다음 판이 나가도 이미 알린 문의에는 다시 가지 않아요.
  assert.equal((await call("GET", "/api/admin/loop", undefined, owner)).data.outbox.sent, 2);
});

test("[RVQ-34] SVC-08 사람이 '보내지 않기'로 한 출시 안내는 다음 판에도 다시 올라오지 않아요", async (t) => {
  const { call, DB, env, owner, ids: [money] } = await shipped(t, ["알람이 늦어요. 결제한 프로 환불해 주세요"]);
  await fiveMinutes(env, NOW);
  const held = DB.db.prepare("SELECT id FROM held_replies WHERE inquiry_id = ?").get(money);
  assert.equal((await call("POST", `/api/admin/inquiries/${money}/held/${held.id}`, { cancel: true }, owner)).status, 200);
  assert.equal(DB.db.prepare("SELECT state FROM ship_outbox WHERE inquiry_id = ?").get(money).state, "gone");
  // 다음 판 2.0.1이 나가도 그 문의·개발사항에는 다시 넣지 않아요.
  DB.db.prepare("INSERT INTO loop_ships (version, seen_at, until, status, by) VALUES ('2.0.1', ?, ?, 'waiting', '자동')").run(NOW, NOW + MIN);
  t.mock.timers.tick(10 * MIN);
  await fiveMinutes(env, Date.now());
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM ship_outbox WHERE inquiry_id = ? AND version = '2.0.1'").get(money).n, 0);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM held_replies WHERE inquiry_id = ?").get(money).n, 0);
});

test("[RVQ-34] U22-02 실패한 안내는 다섯 번 뒤 '실패'로 남고, 자동 순환의 '다시 보내기'가 다시 보내요(이미 간 것은 다시 안 가요)", async (t) => {
  const { call, DB, env, owner, ids: [a, b] } = await shipped(t, ["주간 근무 알람이 늦어요", "야간 근무 알람이 늦어요"]);
  // b에 답하는 길이 막힌 것처럼(문의 표 쓰기가 실패): 그 문의만 늘 던져요.
  DB.db.exec(`CREATE TRIGGER no_b BEFORE UPDATE ON inquiries WHEN NEW.id = '${b}' AND NEW.status = 'answered' BEGIN SELECT RAISE(ABORT, 'busy'); END`);
  for (let i = 0; i < 6; i++) {
    await fiveMinutes(env, Date.now());
    t.mock.timers.tick(5 * MIN);
  }
  assert.ok(told(DB, a));
  assert.equal(told(DB, b), null);
  const row = DB.db.prepare("SELECT state, tries FROM ship_outbox WHERE inquiry_id = ?").get(b);
  assert.deepEqual([row.state, row.tries], ["failed", 5]);
  let loop = (await call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.deepEqual([loop.outbox.sent, loop.outbox.failed, loop.shipped.pending ?? false], [1, 1, false]);
  // 길이 다시 열리고 '다시 보내기'.
  DB.db.exec("DROP TRIGGER no_b");
  const r = await call("POST", "/api/admin/loop/ship/2.0.0/retry", {}, owner);
  assert.equal(r.status, 200);
  assert.deepEqual([r.data.outbox.sent, r.data.outbox.failed], [2, 0]);
  assert.ok(told(DB, b));
  assert.equal(adminSaid(DB, a).length, 1, "이미 간 문의에는 다시 가지 않아요");
  // 나간 판이 아닌 판·열쇠는 안 돼요.
  assert.equal((await call("POST", "/api/admin/loop/ship/1.38.0/retry", {}, owner)).status, 404);
  const ops = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  assert.equal((await call("POST", "/api/admin/loop/ship/2.0.0/retry", {}, { authorization: `Bearer ${ops.key}`, "x-admin": "1" })).status, 403);
  loop = (await call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.equal(loop.outbox.failed, 0);
});

test("[RVQ-34] U22-02 공지를 못 올린 판은 '공지 안 나감'이 남고, 다시 보내기가 공지를 올려요", async (t) => {
  const { call, DB, env, owner } = await shipped(t, ["주간 근무 알람이 늦어요"]);
  // 공지 칸이 꽉 찬 것처럼(올린 공지가 한도만큼 아직 보이는 중).
  const full = Array.from({ length: 20 }, (_, i) => ({ id: `nfull${i}`, title: "공지", body: "공지", level: "info", from: NOW - HOUR, until: NOW + 30 * 24 * HOUR }));
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('notices', ?)").run(JSON.stringify(full));
  await fiveMinutes(env, NOW);
  let loop = (await call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.equal(loop.shipped.noticeWanted, true);
  assert.equal(loop.shipped.noticeId, null);
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('notices', '[]')").run();
  const r = (await call("POST", "/api/admin/loop/ship/2.0.0/retry", {}, owner)).data;
  assert.match(r.notice.title, /2\.0\.0 업데이트 소식/);
  loop = (await call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.ok(loop.shipped.noticeId);
  // 한 번 더 눌러도 공지는 하나예요.
  await call("POST", "/api/admin/loop/ship/2.0.0/retry", {}, owner);
  assert.equal((await call("GET", "/api/config")).data.notices.filter((n) => /2\.0\.0 업데이트 소식/.test(n.title)).length, 1);
});

test("[RVQ-34] SVC-09 보류한 답은 나가기 전에 다시 봐요: 그 사이 새 글·다른 답이 생기거나 돈 이야기가 붙으면 사람이 보내요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB, env } = server();
  const owner = await signIn(call);
  const ops = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${ops.key}`, "x-admin": "1" };
  const q1 = await ask(call, 1, "주간 알람 소리를 바꾸고 싶어요", "question");
  const q2 = await ask(call, 2, "달력 글자를 키우고 싶어요", "question");
  const q3 = await ask(call, 3, "메모 색을 바꾸고 싶어요", "question");
  for (const q of [q1, q2, q3]) assert.equal((await call("POST", `/api/admin/inquiries/${q}/reply`, { text: "설정 › 알람에서 바꿀 수 있어요. 밤낮 운영팀" }, claude)).status, 200);
  // 보류한 뒤: q1은 돈 이야기를 이어 쓰고, q2에는 사람이 먼저 답하고, q3은 그대로.
  t.mock.timers.tick(2 * MIN);
  await more(call, q1, 1, "그리고 어제 결제가 두 번 됐어요 환불해 주세요");
  await call("POST", `/api/admin/inquiries/${q2}/reply`, { text: "설정 › 화면에서 글자 크기를 바꿀 수 있어요." }, owner);
  t.mock.timers.tick(10 * MIN);
  await fiveMinutes(env, Date.now());
  assert.equal(adminSaid(DB, q1).length, 0, "돈 이야기가 붙은 문의에는 가지 않아요");
  assert.equal(adminSaid(DB, q2).length, 1, "사람의 답 뒤에 낡은 답이 이어 가지 않아요");
  assert.equal(adminSaid(DB, q3).length, 1);
  const person = DB.db.prepare("SELECT inquiry_id FROM held_replies ORDER BY inquiry_id").all().map((r) => r.inquiry_id);
  assert.deepEqual(person, [q1, q2].sort());
  const why = DB.db.prepare("SELECT target, detail FROM audit WHERE action = 'inquiry_reply_held' AND detail LIKE '사람이 보내기%'").all();
  assert.ok(why.some((a) => a.target === q1 && /환불·결제·보상/.test(a.detail)));
  assert.ok(why.some((a) => a.target === q2 && /다른 답이 먼저/.test(a.detail)));
  // 사람이 보고 '지금 보내기'를 누른 답은 다시 멈추지 않아요.
  const h1 = DB.db.prepare("SELECT id FROM held_replies WHERE inquiry_id = ?").get(q1).id;
  assert.equal((await call("POST", `/api/admin/inquiries/${q1}/held/${h1}`, { send: true }, owner)).status, 200);
  assert.equal(adminSaid(DB, q1).length, 1);
});

test("[RVQ-34] SVC-09 5분 틱과 매시 단계·'지금 보내기'가 겹쳐도 보류한 답은 한 번만 가요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB, env } = server();
  const owner = await signIn(call);
  const ops = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const q = await ask(call, 1, "주간 알람 소리를 바꾸고 싶어요", "question");
  await call("POST", `/api/admin/inquiries/${q}/reply`, { text: "설정 › 알람에서 바꿀 수 있어요. 밤낮 운영팀" }, { authorization: `Bearer ${ops.key}`, "x-admin": "1" });
  t.mock.timers.tick(11 * MIN);
  const now = Date.now();
  const sent = await Promise.all([releaseHeld(env, now), releaseHeld(env, now), releaseHeld(env, now)]);
  assert.equal(sent.reduce((a, b) => a + b, 0), 1);
  assert.equal(adminSaid(DB, q).length, 1);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM audit WHERE action = 'inquiry_reply_sent' AND target = ?").get(q).n, 1);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM held_replies").get().n, 0);
});

test("[RVQ-34] SVC-28 장부의 '최고 관리자 확인'은 서버처럼 문의한 분의 글 전부로 가려요(이어 쓴 '환불해 주세요'도)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const q = await ask(call, 1, "주간 알람이 늦게 울려요");
  await more(call, q, 1, "그리고 프로 결제한 거 환불해 주세요");
  const row = (await call("GET", "/api/admin/ledger?view=all", undefined, owner)).data.items.find((r) => r.id === q);
  assert.equal(row.ownerAsk?.key, "money");
  assert.equal(row.turn, "owner");
  const decide = (await call("GET", "/api/admin/loop/decide", undefined, owner)).data;
  assert.ok(decide.asks.some((a) => a.id === q));
});

test("[RVQ-34] I22a-02 열쇠 답의 판정 꼬리표: 'none'만 바로 보내기가 되고, 꼬리표가 없으면 기본 보류, 위험 분류·모르는 꼬리표는 사람이 보내요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  // 질문 더미는 최고 관리자가 '바로 보내기'로 켜 둔 상태.
  await call("POST", "/api/admin/loop/switches", { instant: ["faq"] }, owner);
  const ops = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${ops.key}`, "x-admin": "1" };
  const qs = [];
  for (let i = 1; i <= 4; i++) qs.push(await ask(call, i, `주간 알람 소리를 바꾸고 싶어요 ${i}`, "question"));
  const reply = (q, check) => call("POST", `/api/admin/inquiries/${q}/reply`, { text: "설정 › 알람에서 바꿀 수 있어요. 밤낮 운영팀", pile: "faq", ...(check !== undefined ? { check } : {}) }, claude);
  for (const [q, check] of [[qs[0], "none"], [qs[1], undefined], [qs[2], "money"], [qs[3], "모름"]]) assert.equal((await reply(q, check)).status, 200);
  const held = (q) => DB.db.prepare("SELECT until FROM held_replies WHERE inquiry_id = ?").get(q)?.until ?? null;
  const said = (q) => adminSaid(DB, q).length;
  assert.equal(said(qs[0]), 1, "꼬리표 none + 바로 보내기 더미: 바로 가요");
  assert.equal(held(qs[1]), NOW + 10 * MIN, "꼬리표 없음: 10분 보류");
  assert.ok(held(qs[2]) > NOW + 365 * 24 * HOUR, "Claude가 돈 문의로 판정: 사람이 보내요");
  assert.ok(held(qs[3]) > NOW + 365 * 24 * HOUR, "읽을 수 없는 꼬리표: 사람이 보내요");
  const audits = DB.db.prepare("SELECT target, detail FROM audit WHERE action = 'inquiry_reply_held'").all();
  assert.match(audits.find((a) => a.target === qs[1]).detail, /판정 꼬리표 없음/);
  assert.match(audits.find((a) => a.target === qs[2]).detail, /Claude 판정: 환불·결제·보상/);
});

test("[RVQ-34] SVC-31 스튜디오 부탁 안내도 문의 답 규칙을 거쳐요: 돈 이야기가 든 부탁은 사람이 보내는 답으로 남아요", async (t) => {
  const AFTER = Date.parse("2026-11-30T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env, DB } = server({ plaza: true });
  const admin = await signIn(call);
  const studio = (n, text) => call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "studio", text,
    info: { app: "2.0.0 (54)", model: "Pixel", android: "16", studio: "SHIFT" } }, from(n)).then((r) => r.data.id);
  const ASK = "[스튜디오 › 달력 칸 › 색] 달력 칸 배경에 그라데이션을 넣고 싶어요";
  const a = await studio(1, ASK);
  const b = await studio(2, ASK);
  const c = await studio(3, `${ASK}. 프로 결제했는데 이게 안 되면 환불해 주세요`);
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: AFTER }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
  const id = (await call("GET", "/api/admin/growth/needs", undefined, admin)).data.items[0].id;
  const r = (await call("POST", `/api/admin/growth/needs/${id}`, { action: "official", version: "2.0.0" }, admin)).data;
  assert.equal(r.told, 3);
  for (const q of [a, b]) assert.match(adminSaid(DB, q).at(-1).text, /2\.0\.0 판에서/);
  assert.equal(adminSaid(DB, c).length, 0, "사람이 봐야 할 문의에는 바로 가지 않아요");
  const h = DB.db.prepare("SELECT text, until FROM held_replies WHERE inquiry_id = ?").get(c);
  assert.match(h.text, /2\.0\.0 판에서/);
  assert.ok(h.until > AFTER + 365 * 24 * HOUR);
  assert.equal(DB.db.prepare("SELECT status FROM inquiries WHERE id = ?").get(c).status, "open", "사람이 보려던 문의가 '답함'으로 바뀌지 않아요");
});

test("[RVQ-34] SVC-32 돌려준 500도 '서버 오류'와 묶음에 같이 들고, 쉬는 기능의 503·바깥 서버 502·504는 오류가 아니라 따로 세요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB, env } = server();
  const owner = await signIn(call);
  const { countServerAnswer } = await import("../src/index.js");
  // 쉬는 기능: 회사 제보를 꺼 두면 503.
  await call("GET", "/api/config");
  DB.db.prepare("INSERT OR REPLACE INTO flag_overrides (key, force, at) VALUES ('company.report', 'off', ?)").run(NOW);
  const paused = await call("POST", "/api/reports", { v: 1, device: device(1), company: "x", setup: {} });
  // 던지지 않고 500을 돌려준 답(어느 길이든 같은 함수가 세요)과 바깥 서버가 안 닿은 504.
  await countServerAnswer(env, "/api/recipes/ABCD2345", 500);
  await countServerAnswer(env, "/api/billing/verify", 504);
  const d = (await call("GET", "/api/admin/watch/errors", undefined, owner)).data;
  const today = d.days.length - 1;
  assert.equal(d.daily[today], 1, "묶음의 오늘 수");
  assert.equal(d.loose, 0, "날마다 센 서버 오류와 묶음이 같아요");
  assert.ok(d.groups.some((g) => g.route.startsWith("/api/recipes") && /던진 오류 없이 돌려준 서버 오류/.test(g.message)));
  assert.equal(d.rest.far, 1);
  assert.equal(paused.status, 503);
  assert.equal(d.rest.busy, 1);
});

// ── SVC-19·I22b-04 출시 감지 세 겹 확인 ──────────────────────────────────────────────────────────────────────────
const kst = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);
const phonesOn = (env, ms, app, n) => env.DB.db.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)").run(kst(ms), app, n);

/** Play 서비스 계정과 프로덕션 트랙(loop.test.mjs와 같은 흉내). [track]을 바꾸면 다음 읽기에 그대로 나와요. */
function fakePlay(t, track) {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const account = JSON.stringify({ client_email: "bamnat@example.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init = {}) => {
    url = String(url);
    if (url === "https://oauth2.googleapis.com/token") return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }));
    if (url.endsWith("/edits") && init.method === "POST") return new Response(JSON.stringify({ id: "edit1" }));
    if (url.endsWith("/edits/edit1/tracks/production")) return new Response(JSON.stringify({ track: "production", releases: track.releases }));
    if (url.endsWith("/edits/edit1") && init.method === "DELETE") return new Response(null, { status: 204 });
    return new Response("{}", { status: 404 });
  };
  t.after(() => { globalThis.fetch = real; });
  return account;
}
async function hourly(env, now = Date.now()) {
  for (const cron of ["17 * * * *", "47 * * * *"]) {
    const waits = [];
    await worker.scheduled({ cron, scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
    await Promise.all(waits);
  }
}

test("[RVQ-34] SVC-19 나갔다고 볼 기준은 테스터 수(스위치·관리자 앱에 연결된 폰 중 큰 쪽)보다 커요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const track = { releases: [{ name: "54 (2.0.0)", versionCodes: ["54"], status: "completed" }] };
  const s = server({ PLAY_SERVICE_ACCOUNT: fakePlay(t, track) });
  const owner = await signIn(s.call);
  await s.call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  // 스위치 기본은 12대, 20대로 올리면 그 수가 기준이 돼요(잘못된 수는 받지 않아요).
  assert.equal((await s.call("GET", "/api/admin/loop", undefined, owner)).data.testers, 12);
  assert.equal((await s.call("POST", "/api/admin/loop/switches", { testers: 501 }, owner)).status, 400);
  assert.equal((await s.call("POST", "/api/admin/loop/switches", { testers: 20 }, owner)).status, 200);
  assert.equal((await s.call("GET", "/api/admin/loop", undefined, owner)).data.testers, 20);
  // 관리자 앱에 연결된 폰(관리자·가족)이 더 많으면 그 수가 기준이에요.
  const add = s.DB.db.prepare("INSERT INTO admin_devices (id, device, admin, admin_name, status, created_at, approved_at) VALUES (?, ?, 'a', '관리자', ?, ?, ?)");
  for (let i = 0; i < 40; i++) add.run(`ad${i}`, `dev${i}`, "approved", NOW, NOW);
  add.run("adx", "devx", "pending", NOW, null);
  // 100%를 본 날 이 판 40대: 기준 41대(테스터 40 + 1)에 못 미쳐요.
  phonesOn(s.env, NOW, "2.0.0", 40);
  await hourly(s.env);
  let loop = (await s.call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.deepEqual([loop.play.seen.testers, loop.play.seen.need, loop.play.seen.live], [40, 41, false]);
  assert.equal(loop.ships[0].status, "review");
  phonesOn(s.env, NOW, "2.0.0", 41);
  await hourly(s.env);
  loop = (await s.call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.equal(loop.ships[0].status, "waiting", "41대면 나갔다고 봐요");
});

test("[RVQ-34] I22b-04 검토 중에 미리 받은 폰은 빼고, Play에서 멈춘 판은 휴대폰이 많아도 보류 안내를 세우지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const track = { releases: [{ name: "54 (2.0.0)", versionCodes: ["54"], status: "completed" }] };
  const s = server({ PLAY_SERVICE_ACCOUNT: fakePlay(t, track) });
  const owner = await signIn(s.call);
  await s.call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  // 검토 중 사흘 동안 테스터 25대가 이미 2.0.0을 썼어요.
  for (let i = 1; i <= 3; i++) phonesOn(s.env, NOW - i * DAY, "2.0.0", 25);
  await hourly(s.env);
  let loop = (await s.call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.deepEqual([loop.play.seen.before, loop.play.seen.phones], [25, 0]);
  // 100% 뒤 50대: 늘어난 건 25대라 아직이에요(예전에는 50대로 나갔다고 봤어요).
  phonesOn(s.env, NOW, "2.0.0", 50);
  await hourly(s.env);
  assert.equal((await s.call("GET", "/api/admin/loop", undefined, owner)).data.ships[0].status, "review");
  // Play에서 멈춘 판(halted)은 휴대폰이 많아도 기다려요.
  track.releases = [{ name: "54 (2.0.0)", versionCodes: ["54"], status: "halted", userFraction: 1 }, { name: "53 (1.39.0)", versionCodes: ["53"], status: "completed" }];
  phonesOn(s.env, NOW, "2.0.0", 200);
  t.mock.timers.tick(HOUR);
  await hourly(s.env);
  loop = (await s.call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.equal(loop.ships[0].status, "review", "멈춘 판은 나간 판이 아니에요");
  // 다시 100% 완료로 돌아오면 그때 보류 안내가 서요.
  track.releases = [{ name: "54 (2.0.0)", versionCodes: ["54"], status: "completed" }];
  t.mock.timers.tick(HOUR);
  await hourly(s.env);
  assert.equal((await s.call("GET", "/api/admin/loop", undefined, owner)).data.ships[0].status, "waiting");
});

// ── SVC-20·SVC-33 효과 판정 ────────────────────────────────────────────────────────────────────────────────
test("[RVQ-34] SVC-20 효과는 표본이 넉넉하고 95% 구간이 ±10%를 넘을 때만 움직인 것이고, 좋은 방향은 개발사항이 정해요", () => {
  const base = { events: "pay_open", beforeDays: 1000, afterDays: 1000 };
  // 작은 표본(앞뒤 합 20)은 '아직 몰라요'(예전에는 2 → 18이면 '좋아졌어요').
  assert.equal(effectVerdict({ ...base, before: 2, after: 18, good: "up" }).effect, "unknown");
  // 넉넉하고 크게 늘었어요: 늘면 좋으면 좋아짐, 줄면 좋으면 나빠짐, 방향을 안 정했으면 아직 몰라요(이유와 함께).
  assert.equal(effectVerdict({ ...base, before: 100, after: 200, good: "up" }).effect, "better");
  assert.equal(effectVerdict({ ...base, before: 100, after: 200, good: "down" }).effect, "worse");
  const loose = effectVerdict({ ...base, before: 100, after: 200 });
  assert.equal(loose.effect, "unknown");
  assert.match(loose.why, /좋은 방향\(늘면·줄면\)을 정하지 않았어요/);
  // 거의 같으면(구간이 ±10% 안) 그대로, 구간이 넓어 걸치면 아직 몰라요.
  assert.equal(effectVerdict({ events: "pay_open", beforeDays: 100000, afterDays: 100000, before: 10000, after: 10100, good: "up" }).effect, "same");
  const wide = effectVerdict({ ...base, before: 40, after: 46, good: "up" });
  assert.equal(wide.effect, "unknown");
  assert.match(wide.why, /표본이 작아 구간이 넓어요/);
  // 숫자가 좋아져도 같은 문의가 다시 오면 그대로예요.
  assert.equal(effectVerdict({ ...base, before: 100, after: 200, good: "up", back: 2, yes: 1, asks: 3 }).effect, "same");
});

test("[RVQ-34] SVC-20 좋은 방향(good)은 개발사항 고치기와 저장소 동기화로 적고, 이상한 값은 받지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  const id = (await call("POST", "/api/admin/dev", { title: "정산 열기 길 줄이기" }, owner)).data.id;
  assert.equal((await call("POST", `/api/admin/dev/${id}`, { events: ["pay_open"], good: "up" }, owner)).status, 200);
  const one = async (x) => (await call("GET", `/api/admin/dev/${x}`, undefined, owner)).data;
  assert.equal((await one(id)).good, "up");
  assert.equal((await call("POST", `/api/admin/dev/${id}`, { good: "sideways" }, owner)).data.field, "good");
  // 저장소 줄(ops/dev-status.jsonl)의 good: 맞는 값만, 아니면 비워요.
  const sync = (await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data;
  const key = { authorization: `Bearer ${sync.key}`, "x-admin": "1" };
  const r = (await call("POST", "/api/admin/dev/sync", { sha: "abc1234def", items: [
    { id, track: "RVQ-34", status: "doing", size: "polish", route: "app", events: ["pay_open"], good: "down" },
    { track: "RVQ-34b", title: "급여 칸 줄이기", status: "doing", size: "polish", route: "app", events: ["pay_edit"], good: "left" },
  ] }, key)).data;
  assert.equal(r.errors ?? 0, 0, JSON.stringify(r));
  assert.equal((await one(id)).good, "down");
  const list = (await call("GET", "/api/admin/dev?status=doing", undefined, owner)).data.items;
  const made = list.find((x) => x.title === "급여 칸 줄이기");
  assert.equal((await one(made.id)).good ?? "", "");
});

test("[RVQ-34] SVC-33 효과 판정은 닿기를 기다리는 줄이 많아도 멈추지 않고, 닿을 길이 없는 줄은 '잴 수 없어요'로 빼요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { env, DB, call } = server();
  await signIn(call);
  const add = DB.db.prepare(`INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, created_at, updated_at, route, released_in, live_at)
    VALUES (?, ?, '', 'done', '', 'a', '관리자', ?, ?, ?, ?, ?)`);
  // 다음 판(9.9.9)을 기다리는 줄 250개가 먼저(예전 200줄 창이 여기서 찼어요).
  for (let i = 0; i < 250; i++) add.run(`dw${String(i).padStart(14, "0")}`, `기다림 ${i}`, NOW - 30 * DAY, NOW - 30 * DAY + i, "app", "9.9.9", null);
  // 고친 판도 서버로 닿은 때도 없는 앱 줄 하나.
  add.run("dnever000000000", "판 없음", NOW - 29 * DAY, NOW - 29 * DAY, "app", "", null);
  // 서버로 16일 전에 닿은 줄 하나가 맨 뒤.
  add.run("dserver00000000", "서버 고침", NOW - 20 * DAY, NOW - 20 * DAY, "server", "", NOW - 16 * DAY);
  await effectTick(env, NOW);
  const row = (id) => DB.db.prepare("SELECT effect, effect_note FROM dev_items WHERE id = ?").get(id);
  assert.notEqual(row("dserver00000000").effect, "", "맨 뒤 줄도 재요");
  assert.equal(row("dnever000000000").effect, "skip");
  assert.match(row("dnever000000000").effect_note, /고친 판\(반영 버전\)도 서버로 닿은 때도 없어요/);
  assert.equal(row("dw00000000000000").effect, "", "기다리는 줄은 그대로 기다려요");
  assert.deepEqual([effectOf({ status: "done", effect: "skip", effect_note: row("dnever000000000").effect_note }).name, EFFECT.names.unknown], ["잴 수 없어요", "아직 몰라요"]);
});
