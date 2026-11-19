// 2.0.0 [RVQ-28] 검토 SVC-10: 운영 열쇠·저장소 동기화로 적는 '공지 한 줄'·출시 답과, 열쇠로 부르는 나간 판 저장. `npm test`.
// 사람 확인 없이 모든 사용자에게 나가는 글이라 답 글 검사를 지나야 적히고, 열쇠로 부른 저장은 Play·서버가 아는 판만 10분 보류 뒤에 나가요.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { server, device, signIn } from "./helpers.mjs";
import { broadcastRisk } from "../src/desk.js";

const NOW = Date.parse("2026-11-20T01:00:00Z");
const HOUR = 3600e3;
const DAY = 24 * HOUR;
const kst = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);
const ask = (call, n, text) => call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "bug", text, info: { app: "1.39.0 (53)", model: "samsung SM-S921N" } }).then((r) => r.data.id);
const thread = (call, id, n) => call("GET", `/api/inquiries/${id}`, undefined, { "x-device": device(n) }).then((r) => r.data);
async function fiveMinutes(env, now = Date.now()) {
  const waits = [];
  await worker.scheduled({ cron: "*/5 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
}
const audits = (DB, action) => DB.db.prepare("SELECT target, detail FROM audit WHERE action = ?").all(action);

test("[RVQ-28] SVC-10 글 검사: AI·자동 답 표현, 보안 정보, 링크·전화·이메일·단톡방은 걸리고 기능 설명의 금액·'AI 근무표 읽기'는 지나요", () => {
  for (const bad of ["AI 상담원이 답해 드려요", "자동 답변이에요", "자세한 건 bamnat.app/help 에서", "궁금하면 010-1234-5678로", "help@example.com 으로 보내 주세요",
    "오픈 채팅방에서 알려 드려요", "서버 주소가 바뀌었어요", "관리자 페이지에서 고쳤어요"]) assert.ok(broadcastRisk(bad), bad);
  for (const ok of ["주간 근무 알람이 제때 울려요", "시급 10,030원부터 주휴수당까지 맞게 계산해요", "AI 근무표 읽기가 더 정확해졌어요", "", null]) assert.equal(broadcastRisk(ok), null, String(ok));
});

test("[RVQ-28] SVC-10 개발사항의 공지 한 줄·출시 답은 글 검사를 지나야 적히고(사람·열쇠 모두), 저장소 동기화는 그 칸만 빼고 상태는 반영해요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  const id = (await call("POST", "/api/admin/dev", { title: "주간 알람 늦음" }, owner)).data.id;
  let r = await call("POST", `/api/admin/dev/${id}`, { news: "자세한 건 오픈 채팅방에서 알려 드려요" }, owner);
  assert.equal(r.status, 400);
  assert.deepEqual([r.data.error, r.data.field], ["notice_risk", "news"]);
  const ops = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${ops.key}`, "x-admin": "1" };
  r = await call("POST", `/api/admin/dev/${id}`, { shipReply: "AI 상담원이 자동으로 답변드려요. 업데이트해 주세요." }, claude);
  assert.deepEqual([r.status, r.data.error, r.data.field], [400, "notice_risk", "shipReply"]);
  assert.equal((await call("POST", `/api/admin/dev/${id}`, { news: "주간 근무 알람이 제때 울려요" }, claude)).status, 200);
  assert.equal(DB.db.prepare("SELECT news FROM dev_items WHERE id = ?").get(id).news, "주간 근무 알람이 제때 울려요");
  // 저장소 동기화(dev-status.jsonl): 걸린 칸만 빼고 상태·판은 반영해요.
  const sync = (await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data;
  const res = (await call("POST", "/api/admin/dev/sync", { sha: "abc1234def", version: "2.0.0", items: [
    { id, track: "LOOP-90", status: "done", size: "fix", route: "app", news: "문의는 help@example.com으로", shipReply: "기다려 주셔서 고마워요. 고친 2.0.0이 나왔어요." },
  ] }, { authorization: `Bearer ${sync.key}`, "x-admin": "1" })).data;
  assert.equal(res.errors, 0);
  assert.equal(res.held, 1);
  assert.deepEqual(res.results[0].held, ["news"]);
  const row = DB.db.prepare("SELECT status, released_in, news, ship_reply FROM dev_items WHERE id = ?").get(id);
  assert.deepEqual([row.status, row.released_in, row.news], ["done", "2.0.0", "주간 근무 알람이 제때 울려요"]);
  assert.match(row.ship_reply, /고친 2\.0\.0이 나왔어요/);
  assert.match(audits(DB, "loop_sync")[0].detail, /글 검사로 뺀 줄 1/);
});

test("[RVQ-28] SVC-10 판이 나갈 때 다시 봐요: 걸린 공지 줄은 빼고, 걸린 출시 답은 기본 답으로 보내고, 미리 보기에 뺀 것을 보여요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  const q = await ask(call, 1, "주간 근무 알람이 30분 늦게 울려요");
  const good = (await call("POST", "/api/admin/dev", { title: "주간 알람 늦음", inquiry: q }, owner)).data.id;
  const bad = (await call("POST", "/api/admin/dev", { title: "달력 글자 크기" }, owner)).data.id;
  await call("POST", `/api/admin/dev/${good}`, { status: "done", releasedIn: "2.0.0", news: "주간 근무 알람이 제때 울려요" }, owner);
  await call("POST", `/api/admin/dev/${bad}`, { status: "done", releasedIn: "2.0.0" }, owner);
  // 이 검사 전에 적혀 있던 줄(옛 서버)처럼 바로 넣어요.
  DB.db.prepare("UPDATE dev_items SET news = ? WHERE id = ?").run("자세한 건 bamnat.app/help 에서 봐요", bad);
  DB.db.prepare("UPDATE dev_items SET ship_reply = ? WHERE id = ?").run("AI 상담원이 자동으로 답변드려요. 고친 판이 나왔어요.", good);
  const dry = (await call("POST", "/api/admin/desk/ship", { version: "2.0.0", dry: true }, owner)).data;
  assert.deepEqual(dry.blocked.map((b) => [b.id, b.what]).sort(), [[bad, "news"], [good, "shipReply"]].sort());
  assert.doesNotMatch(dry.notice.body, /bamnat\.app/);
  assert.match(dry.notice.body, /주간 근무 알람이 제때 울려요/);
  const shipped = (await call("POST", "/api/admin/desk/ship", { version: "2.0.0", notify: true }, owner)).data;
  assert.equal(shipped.answered, 1);
  const last = (await thread(call, q, 1)).messages.at(-1).text;
  assert.match(last, /2\.0\.0 버전이 나왔어요/);
  assert.doesNotMatch(last, /AI/);
  const notices = (await call("GET", "/api/config")).data.notices;
  assert.ok(notices.some((n) => /2\.0\.0 업데이트 소식/.test(n.title) && !/bamnat\.app/.test(n.body)));
  assert.match(audits(DB, "shipped_version").at(-1).detail, /글 검사로 뺀 것 2/);
});

test("[RVQ-28] SVC-10 열쇠로 부른 나간 판 저장: 아는 판만, 바로 나가지 않고 10분 보류(멈추기·지금 보내기), 스스로 안내가 꺼지면 사람이 보내요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  const q = await ask(call, 1, "주간 근무 알람이 30분 늦게 울려요");
  const id = (await call("POST", "/api/admin/dev", { title: "주간 알람 늦음", inquiry: q }, owner)).data.id;
  await call("POST", `/api/admin/dev/${id}`, { status: "done", releasedIn: "2.0.0", news: "주간 근무 알람이 제때 울려요" }, owner);
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${made.key}`, "x-admin": "1" };
  // Play도 서버도 못 본 판은 받지 않아요(미리 보기는 돼요).
  let r = await call("POST", "/api/admin/desk/ship", { version: "2.0.0", notify: true }, claude);
  assert.deepEqual([r.status, r.data.error], [409, "version_unknown"]);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "2.0.0", dry: true }, claude)).data.answers, 1);
  assert.deepEqual([(await call("POST", "/api/admin/desk/ship", { version: "1.38.0", notify: true }, claude)).data.error], ["version_unknown"]);
  // 이 판으로 쓰는 휴대폰이 있으면 서버가 아는 판이에요.
  DB.db.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)").run(kst(NOW - DAY), "2.0.0", 4);
  r = await call("POST", "/api/admin/desk/ship", { version: "2.0.0", notify: true }, claude);
  assert.equal(r.status, 202);
  assert.deepEqual([r.data.held, r.data.status, r.data.answers, r.data.until], [true, "waiting", 1, new Date(NOW + 10 * 60e3).toISOString()]);
  // 아직 나간 판도 답도 그대로예요. 관리자 앱 › 자동 순환에 '요청한 이'와 함께 보여요.
  assert.equal((await call("GET", "/api/admin/flow", undefined, owner)).data.shipped.v, "1.39.0");
  assert.doesNotMatch((await thread(call, q, 1)).messages.at(-1).text, /버전이 나왔어요/);
  const ships = (await call("GET", "/api/admin/loop", undefined, owner)).data.ships;
  assert.deepEqual([ships[0].version, ships[0].status, ships[0].by], ["2.0.0", "waiting", "Claude 요청"]);
  assert.equal(audits(DB, "ops_key_ship").length, 1);
  // 다시 불러도 보류 시계는 그대로, 기록·알림도 한 번.
  t.mock.timers.tick(5 * 60e3);
  r = await call("POST", "/api/admin/desk/ship", { version: "2.0.0", notify: true }, claude);
  assert.equal(r.data.until, new Date(NOW + 10 * 60e3).toISOString());
  assert.equal(audits(DB, "ops_key_ship").length, 1);
  await fiveMinutes(env);
  assert.equal((await call("GET", "/api/admin/flow", undefined, owner)).data.shipped.v, "1.39.0", "아직 10분이 안 됐어요");
  t.mock.timers.tick(5 * 60e3);
  await fiveMinutes(env);
  assert.equal((await call("GET", "/api/admin/flow", undefined, owner)).data.shipped.v, "2.0.0");
  assert.match((await thread(call, q, 1)).messages.at(-1).text, /2\.0\.0 버전이 나왔어요/);
  const done = (await call("GET", "/api/admin/loop", undefined, owner)).data.ships[0];
  assert.deepEqual([done.status, done.by], ["done", "Claude 요청"]);
  // 이미 나간 판은 다시 보내지 않아요.
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "2.0.0" }, claude)).data.error, "already");

  // 사람이 멈춘 판은 열쇠로 다시 세우지 못해요.
  DB.db.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)").run(kst(NOW - DAY), "2.0.1", 2);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "2.0.1" }, claude)).status, 202);
  assert.equal((await call("POST", "/api/admin/loop/ship/2.0.1/cancel", {}, owner)).status, 200);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "2.0.1" }, claude)).data.error, "cancelled");
  // 열쇠는 '지금 보내기'·'멈추기'를 못 해요(사람 몫).
  assert.equal((await call("POST", "/api/admin/loop/ship/2.0.1/now", {}, claude)).status, 403);

  // 스스로 출시 안내가 꺼져 있으면 보류가 끝나도 나가지 않고, 사람이 '지금 보내기'를 눌러요.
  await call("POST", "/api/admin/loop/switches", { autoShip: false }, owner);
  DB.db.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)").run(kst(NOW - DAY), "2.0.2", 2);
  r = await call("POST", "/api/admin/desk/ship", { version: "2.0.2" }, claude);
  assert.deepEqual([r.status, r.data.auto], [202, false]);
  t.mock.timers.tick(20 * 60e3);
  await fiveMinutes(env);
  assert.equal((await call("GET", "/api/admin/flow", undefined, owner)).data.shipped.v, "2.0.0");
  assert.equal((await call("POST", "/api/admin/loop/ship/2.0.2/now", {}, owner)).status, 200);
  assert.equal((await call("GET", "/api/admin/flow", undefined, owner)).data.shipped.v, "2.0.2");
  // 사람 관리자의 '나간 판 저장'은 지금처럼 바로예요.
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "2.0.3" }, owner)).status, 200);
  assert.equal((await call("GET", "/api/admin/flow", undefined, owner)).data.shipped.v, "2.0.3");
  // 규칙 글이 실제 권한과 맞아요.
  const rules = (await call("GET", "/api/admin/desk", undefined, claude)).data.rules.join("\n");
  assert.match(rules, /10분 보류/);
  assert.match(rules, /공지 직접 올리기/);
});

test("[RVQ-28] SVC-10 Play 트랙에서 본 판도 아는 판이에요(단계적 출시 중인 판 포함)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${made.key}`, "x-admin": "1" };
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('play_track', ?)").run(JSON.stringify({ done: "1.39.0", rolling: { v: "2.0.0", fraction: 0.2 }, halted: null, at: NOW }));
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "2.0.0" }, claude)).status, 202);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "2.0.9" }, claude)).data.error, "version_unknown");
});
