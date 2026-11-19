// 2.0.0 [RVN-24] 갈래 N 8차 (검토 22c 서버 광장·운영·푸시): 오늘 출근의 한도, 근무형태별 투표의 남은 묶음, 광장 효과 숫자, 답 모음, 출시 점검표,
// 사고 타임라인, 제작자 후보, 출시 노트 이름, 스튜디오 요구 상태. 발견마다 그 문제를 재현한 시험 하나(LOOP-21). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, report } from "./helpers.mjs";
import { releaseTick } from "../src/release-desk.js";

const NOW = Date.parse("2026-11-18T03:00:00Z");
const DAY = 24 * 3600e3;

async function policyOn(call) {
  const h = await signIn(call);
  const r = await call("POST", "/api/admin/policy", { confirm: true, version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, h);
  assert.equal(r.status, 200, JSON.stringify(r.data));
  return h;
}

test("[RVN-24] SVC-93 '오늘 야간 출근 n명' cannot be filled with new install ids from one network", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  await policyOn(call);
  const net = { "cf-connecting-ip": "198.51.100.77" };
  // A new install's hourly share of the network's day (300 / 4), then 429 with the network named.
  let last = null;
  for (let n = 1; n <= 76; n++) last = await call("POST", "/api/plaza/checkin", { device: device(n), room: "style-shift", kind: "night" }, net);
  assert.equal(last.status, 429, JSON.stringify(last.data));
  assert.equal(last.data.why, "network");
  // Taking one's own tap back is never held by the network.
  const back = await call("POST", "/api/plaza/checkin", { device: device(1), room: "style-shift", kind: null }, net);
  assert.equal(back.status, 200, JSON.stringify(back.data));
  assert.equal(back.data.counts.night, 74);
});

test("[RVN-24] SVC-93 while 무결성 확인 asks, a tap waits for Play's verdict; taking it back does not", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ INTEGRITY_MODE: "require" });
  await policyOn(call);
  const r = await call("POST", "/api/plaza/checkin", { device: device(1), room: "style-shift", kind: "night" });
  assert.equal(r.status, 428);
  assert.equal(r.data.error, "integrity");
  const back = await call("POST", "/api/plaza/checkin", { device: device(1), room: "style-shift", kind: null });
  assert.equal(back.status, 200);
});

test("[RVN-24] SVC-93 a post may be reported for a 근무 태그 that is not so", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  await policyOn(call);
  const post = await call("POST", "/api/posts", { device: device(1), company: "style-shift", setup: "room", body: "소방 3교대 이야기예요", tag: { s: "SHIFT", f: "THREE_SHIFT", i: "fire" } },
    { "cf-connecting-ip": "198.51.100.1" });
  assert.equal(post.status, 201, JSON.stringify(post.data));
  const id = post.data.post?.id ?? post.data.id;
  const flag = await call("POST", `/api/posts/${id}/flag`, { device: device(2), reason: "tag" }, { "cf-connecting-ip": "198.51.100.2" });
  assert.equal(flag.status, 200, JSON.stringify(flag.data));
  const h = await signIn(call);
  // The developer app names it in words.
  const basics = await call("GET", "/api/admin/basics", undefined, h);
  assert.equal(basics.status, 200);
  assert.equal(basics.data.flagReasons.tag, "근무 태그가 사실과 달라요");
});

test("[RVN-24] SVC-94 근무형태별 투표: a rest of 1~4 never shows as whole − shown", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  await policyOn(call);
  const ask = await call("POST", "/api/posts", { device: device(1), company: "style-shift", setup: "room", body: "야간 끝나고 바로 자요?", kind: "question",
    poll: ["바로 자요", "좀 있다 자요"] }, { "cf-connecting-ip": "198.51.100.1" });
  const id = ask.data.id;
  const vote = (n, choice, f) => call("POST", `/api/posts/${id}/poll`, { device: device(n), choice, ...(f ? { tag: { s: "SHIFT", f } } : {}) },
    { "cf-connecting-ip": `198.51.100.${n}` });
  // Five 3교대, then one 당직: before, 3교대 stood apart and the whole poll gave the 당직's choice away (6 − 5).
  for (const n of [10, 11, 12, 13, 14]) await vote(n, 0, "THREE_SHIFT");
  let r = await vote(20, 1, "ON_CALL");
  assert.deepEqual(r.data.poll.counts, [5, 1]);
  assert.equal(r.data.poll.byForm, undefined, "no 근무형태 left on its own: no split");
  // Five 4조 2교대 more: the smallest shown (3교대, 5) takes the 당직 in as 그 밖의 근무, so every shown group is 5 or more and they add up.
  for (const n of [30, 31, 32, 33]) await vote(n, 1, "FOUR_TEAM");
  r = await vote(34, 0, "FOUR_TEAM");
  assert.deepEqual(r.data.poll.byForm, [{ f: "FOUR_TEAM", counts: [1, 4] }, { f: null, counts: [5, 1] }]);
  const sum = r.data.poll.byForm.reduce((a, g) => a.map((n, i) => n + g.counts[i]), [0, 0]);
  assert.deepEqual(sum, r.data.poll.counts);
  for (const g of r.data.poll.byForm) assert.ok(g.counts[0] + g.counts[1] >= 5, JSON.stringify(g));
});

test("[RVN-24] SVC-95 광장 효과 counts what is shown, the 태그 share of top posts, and says 아직 적음 under 10", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await policyOn(call);
  const at = (n) => ({ "cf-connecting-ip": `198.51.100.${n}` });
  const ids = [];
  for (let n = 1; n <= 10; n++) {
    const r = await call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body: `야간 뒤에 뭐 드세요 ${n}`, kind: "question",
      ...(n <= 5 ? { tag: { s: "SHIFT", f: "FOUR_TEAM" } } : {}) }, at(n));
    assert.equal(r.status, 201, JSON.stringify(r.data));
    ids.push(r.data.id);
  }
  for (let n = 0; n < 5; n++) {
    await call("POST", "/api/posts", { device: device(40 + n), company: "style-shift", setup: "room", body: "저는 죽 먹어요", parent: ids[n],
      tag: { s: "SHIFT", f: "FOUR_TEAM" } }, at(40 + n));
  }
  // One more post the server hid (도배, or 신고 after the operator looked): it is no longer a post of the day.
  const spam = await call("POST", "/api/posts", { device: device(90), company: "style-shift", setup: "room", body: "광고 글이에요 연락 주세요" }, at(90));
  await DB.prepare("UPDATE posts SET status = 'hidden', hidden_at = ? WHERE id = ?").bind(NOW, spam.data.id).run();
  const r = await call("GET", "/api/admin/plaza/effect", undefined, h);
  assert.equal(r.status, 200);
  const s = r.data.items.find((x) => x.room === "style-shift");
  assert.equal(s.posts, 10, "the hidden one is not counted");
  assert.equal(s.hidden, 1);
  assert.equal(s.writers, 15);
  assert.equal(s.tagged, 50, "5 of the 10 top posts; the 5 tagged answers are not in it");
  assert.equal(s.questions, 10);
  assert.equal(s.answeredPct, 50);
  assert.equal(s.cameBackPct, null, "no first writer 14 days back yet");
  assert.equal(r.data.few, 10);
  // Every plaza in one batch, not four reads each.
  assert.equal(r.data.items.length, 6);
});

test("[RVN-24] SVC-96 답 모음: a full room still takes a new title, 활동 기록 says which answer, a deleted answer leaves by itself", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await policyOn(call);
  const at = (n) => ({ "cf-connecting-ip": `198.51.100.${n % 250}` });
  const answers = [];
  for (let n = 0; n < 31; n++) {
    const q = await call("POST", "/api/posts", { device: device(1000 + n), company: "style-shift", setup: "room", body: `야간 질문이에요 ${n}`, kind: "question" }, at(n));
    const a = await call("POST", "/api/posts", { device: device(2000 + n), company: "style-shift", setup: "room", body: `이렇게 해 보세요 ${n}`, parent: q.data.id }, at(n + 100));
    answers.push(a.data.id);
  }
  for (let n = 0; n < 30; n++) {
    t.mock.timers.tick(1000);
    const r = await call("POST", "/api/admin/plaza/picks", { answer: answers[n], title: `선배 답 ${n}` }, h);
    assert.equal(r.status, 200, JSON.stringify(r.data));
  }
  // Full: a new one is refused with the oldest named, so the operator can make room.
  const full = await call("POST", "/api/admin/plaza/picks", { answer: answers[30], title: "새 답" }, h);
  assert.equal(full.status, 409);
  assert.equal(full.data.oldest.title, "선배 답 0");
  // A pick already there takes a new title (and a topic) even so.
  const retitle = await call("POST", "/api/admin/plaza/picks", { answer: answers[5], title: "야간 뒤 바로 잠드는 법", topic: "sleep" }, h);
  assert.equal(retitle.status, 200, JSON.stringify(retitle.data));
  assert.equal(retitle.data.updated, true);
  assert.equal((await call("POST", "/api/admin/plaza/picks", { answer: answers[5], title: "제목", topic: "lotto" }, h)).data.error, "topic");
  const app = (await call("GET", "/api/plaza/picks?room=style-shift", undefined, { "x-device": device(9) })).data.items;
  assert.equal(app.length, 30);
  assert.deepEqual(app.filter((p) => p.title === "야간 뒤 바로 잠드는 법").map((p) => p.topic), ["sleep"]);
  // 활동 기록: which answer, under what title.
  const log = await DB.prepare("SELECT action, target, detail FROM audit WHERE action LIKE 'plaza_pick%' ORDER BY rowid").all();
  const rows = log.results;
  assert.equal(rows.filter((r) => r.action === "plaza_pick").length, 30);
  const titled = rows.find((r) => r.action === "plaza_pick_title");
  assert.match(titled.detail, new RegExp(`답 ${answers[5]} ‘야간 뒤 바로 잠드는 법’ · 수면`));
  assert.equal(titled.target, "style-shift");
  // The writer deletes an answer: the next listing takes its pick out and says so once; 활동 기록 marks it 자동 (no admin).
  await DB.prepare("DELETE FROM posts WHERE id = ?").bind(answers[7]).run();
  let list = await call("GET", "/api/admin/plaza/picks", undefined, h);
  assert.deepEqual(list.data.removed, [{ room: "style-shift", title: "선배 답 7" }]);
  const shift = list.data.items.find((x) => x.room === "style-shift");
  assert.equal(shift.picks.length, 29);
  assert.equal(shift.full, false);
  list = await call("GET", "/api/admin/plaza/picks", undefined, h);
  assert.deepEqual(list.data.removed, []);
  const gone = (await DB.prepare("SELECT actor, actor_id, detail FROM audit WHERE action = 'plaza_pick_gone'").all()).results;
  assert.equal(gone.length, 1);
  assert.equal(gone[0].actor_id, "");
  // Removing one says which.
  const id = shift.picks.find((p) => p.title === "선배 답 3").id;
  assert.equal((await call("DELETE", `/api/admin/plaza/picks/${id}`, undefined, h)).status, 200);
  const removed = (await DB.prepare("SELECT detail FROM audit WHERE action = 'plaza_pick_remove'").all()).results;
  assert.match(removed[0].detail, /‘선배 답 3’/);
});

test("[RVN-24] SVC-102 출시 점검표: undoing a step reopens it, 판 건강 waits a day, only 업데이트 소식 counts, a finished list stays as it was", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await signIn(call);
  assert.equal((await call("POST", "/api/admin/release", { version: "2.0.0" }, h)).status, 200);
  const step = (body) => call("POST", "/api/admin/release/2.0.0", body, h);
  const one = async () => (await call("GET", "/api/admin/release/2.0.0", undefined, h)).data;
  // An 점검 안내 is not the 업데이트 소식.
  const day = (ms) => new Date(ms).toISOString();
  await call("POST", "/api/admin/notices", { title: "서버 점검 안내", body: "새벽 2시에 잠깐 멈춰요.", level: "info", from: day(Date.now()), until: day(Date.now() + 3 * DAY) }, h);
  let v = await one();
  assert.equal(v.steps.find((s) => s.key === "notice").done, false);
  await call("POST", "/api/admin/notices", { title: "2.0.0 업데이트 소식", body: "바뀐 것을 모았어요.", level: "info", from: day(Date.now()), until: day(Date.now() + 3 * DAY), action: { kind: "WHATS_NEW" } }, h);
  v = await one();
  const notice = v.steps.find((s) => s.key === "notice");
  assert.equal(notice.done, true);
  assert.match(notice.hint, /2\.0\.0 업데이트 소식/);
  // 판 건강: no '했어요' before the shipped version is a day old.
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('shipped_version', ?)").bind(JSON.stringify({ v: "2.0.0", at: Date.now(), by: "t" })).run();
  const early = await step({ step: "health", done: true });
  assert.equal(early.status, 409);
  assert.equal(early.data.error, "health_early");
  v = await one();
  assert.equal(v.steps.find((s) => s.key === "health").ready, false);
  // Finish the rest by hand, then 판 건강 a day later: the list is done, and the 열린 점검표 count drops.
  for (const key of ["play", "flags", "materials", "market", "answers"]) assert.equal((await step({ step: key, skip: true })).status, 200);
  t.mock.timers.tick(DAY + 1000);
  const done = await step({ step: "health", done: true });
  assert.equal(done.status, 200, JSON.stringify(done.data));
  assert.equal(done.data.left, 0);
  assert.ok(done.data.doneAt);
  assert.equal((await DB.prepare("SELECT COUNT(*) AS n FROM release_runs WHERE done_at IS NULL").first()).n, 0);
  // A finished list keeps what it saw: a notice taken down later does not reopen it.
  await DB.prepare("UPDATE settings SET value = '[]' WHERE key = 'notices'").run();
  v = await one();
  assert.equal(v.left, 0);
  assert.equal(v.steps.find((s) => s.key === "notice").done, true);
  assert.match(v.steps.find((s) => s.key === "notice").hint, /2\.0\.0 업데이트 소식/);
  // Undoing a step clears 끝: it counts as open again, and the auto steps are read as they are now. I22cc-03: what the server once saw
  // (the 업데이트 소식, 굳힌 증거) stays seen for this version, so only the undone step is open.
  const undo = await step({ step: "play", done: false });
  assert.equal(undo.data.doneAt, null);
  assert.equal(undo.data.left, 1);
  assert.deepEqual(undo.data.steps.filter((s) => !s.done && !s.skipped).map((s) => s.key), ["play"]);
  assert.match(undo.data.steps.find((s) => s.key === "notice").evidence, /2\.0\.0 업데이트 소식/);
  assert.equal((await DB.prepare("SELECT COUNT(*) AS n FROM release_runs WHERE done_at IS NULL").first()).n, 1);
  const list = (await call("GET", "/api/admin/release", undefined, h)).data;
  assert.equal(list.items[0].doneAt, null);
});

test("[RVN-24] SVC-105 바꾼 것: switches, admin changes without the mail, the newest 기능 강제, a written note, and a new 공유 서버 판", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await signIn(call);
  const marks = async () => (await call("GET", `/api/admin/changes?from=${NOW - 400 * DAY}&to=${Date.now() + 1}`, undefined, h)).data.marks;
  assert.equal((await call("POST", "/api/admin/switches", { key: "PLAZA_WRITE", off: true, why: "시험" }, h)).status, 200);
  t.mock.timers.tick(1000);
  let m = await marks();
  assert.ok(m.some((x) => x.kind === "switch_off" && /^기능 끔 /.test(x.text)), JSON.stringify(m));
  assert.equal(m.filter((x) => x.kind === "flag").length, 0, "one line for one switch");
  // An admin's change says what changed, never the mail.
  await DB.prepare("INSERT INTO audit (at, action, target, detail) VALUES (?, 'admin_update', 'someone@example.com', 'suspend')").bind(Date.now()).run();
  m = await marks();
  const admin = m.find((x) => x.kind === "admin_update");
  assert.equal(admin.text, "관리자 권한 바꿈 · suspend");
  assert.ok(!m.some((x) => x.text.includes("@")));
  // 기능 강제 over a long stretch: the newest 200, not the oldest.
  const ins = DB.prepare("INSERT INTO flag_history (at, key, action) VALUES (?, ?, 'force_on')");
  for (let i = 0; i < 230; i++) await ins.bind(NOW - 300 * DAY + i * DAY, `old.${i}`).run();
  m = await marks();
  assert.ok(m.some((x) => x.text === "기능 old.229 force_on"));
  // A change the server cannot see, written by hand.
  assert.equal((await call("POST", "/api/admin/changes", { kind: "lotto", text: "x" }, h)).status, 400);
  const noted = await call("POST", "/api/admin/changes", { kind: "site", text: "사이트 2판 올림" }, h);
  assert.equal(noted.status, 200, JSON.stringify(noted.data));
  assert.ok(noted.data.marks.some((x) => x.text === "사이트 서버 배포 · 사이트 2판 올림"));
});

test("[RVN-24] SVC-105 the hourly job writes 공유 서버 배포 when the share server's 판 changes, and nothing the first time", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { DB } = server();
  const { noteShareDeploy } = await import("../src/ops-watch.js");
  const env = { DB, SHARE_BASE_URL: "https://share.example" };
  await DB.prepare("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)").run();
  await DB.prepare("CREATE TABLE IF NOT EXISTS audit (at INTEGER, action TEXT, target TEXT, detail TEXT, actor TEXT DEFAULT '', actor_id TEXT DEFAULT '')").run();
  let version = "2.0.0";
  t.mock.method(globalThis, "fetch", async () => new Response(JSON.stringify({ ok: true, version }), { status: 200, headers: { "content-type": "application/json" } }));
  assert.equal(await noteShareDeploy(env), false);
  assert.equal(await noteShareDeploy(env), false);
  version = "2.0.1";
  assert.equal(await noteShareDeploy(env), true);
  const rows = (await DB.prepare("SELECT action, target, detail FROM audit WHERE action = 'share_deployed'").all()).results;
  assert.deepEqual(rows.map((r) => [r.target, r.detail]), [["2.0.1", "2.0.0 → 2.0.1"]]);
});

test("[RVN-24] SVC-106 비슷한 회사: the hourly job finds the pairs once, the screen and '다른 회사예요' only read them, a new company is looked at again", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await signIn(call);
  const { findSimilar, similarCompanies } = await import("../src/company-similar.js");
  const add = (id, name, norm, at) => DB.prepare("INSERT INTO companies (id, name, norm, created_at) VALUES (?, ?, ?, ?)").bind(id, name, norm, at).run();
  await add("cmpAAAAAAAA1", "LG전자 칠러공장", "lg전자칠러공장", NOW - 3 * DAY);
  await add("cmpAAAAAAAA2", "LG전자 평택칠러공장", "lg전자평택칠러공장", NOW - 2 * DAY);
  assert.equal(await findSimilar({ DB }, NOW), 1);
  // The screen reads what was found: it does not compare again (no write to company_similar).
  const before = (await DB.prepare("SELECT value FROM settings WHERE key = 'company_similar'").first()).value;
  t.mock.timers.tick(60_000);
  let r = await call("GET", "/api/admin/companies/similar", undefined, h);
  assert.equal(r.data.pairs.length, 1, JSON.stringify(r.data));
  assert.equal((await DB.prepare("SELECT value FROM settings WHERE key = 'company_similar'").first()).value, before);
  // A company made after the last look: the next screen looks again.
  await add("cmpBBBBBBBB1", "한빛물류 센터", "한빛물류센터", Date.now());
  await add("cmpBBBBBBBB2", "한빛물류", "한빛물류", Date.now());
  r = await call("GET", "/api/admin/companies/similar", undefined, h);
  assert.equal(r.data.pairs.length, 2);
  // '다른 회사예요' takes that pair out without comparing again.
  const kept = (await DB.prepare("SELECT value FROM settings WHERE key = 'company_similar'").first()).value;
  const skip = await call("POST", "/api/admin/companies/similar/skip", { a: "cmpBBBBBBBB1", b: "cmpBBBBBBBB2" }, h);
  assert.equal(skip.status, 200, JSON.stringify(skip.data));
  assert.equal(skip.data.pairs.length, 1);
  assert.equal((await DB.prepare("SELECT value FROM settings WHERE key = 'company_similar'").first()).value, kept);
  // A merged company drops out of the cached pair.
  await DB.prepare("UPDATE companies SET merged_into = 'cmpAAAAAAAA1' WHERE id = 'cmpAAAAAAAA2'").run();
  assert.equal((await similarCompanies({ DB }, 12, Date.now())).pairs.length, 0);
});

test("[RVN-24] SVC-106 사람 찾기: an 8-letter mark tells two phones apart that share 6, a written 6 still finds one, and the search is a range", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await signIn(call);
  await call("GET", "/api/terms");
  const a = "abcdef12" + "0".repeat(56);
  const b = "abcdef34" + "0".repeat(56);
  const c = "123456aa" + "0".repeat(56);
  for (const [id, d] of [["qAAAAAAAAAAAA1", a], ["qAAAAAAAAAAAA2", b], ["qAAAAAAAAAAAA3", c]]) {
    await DB.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, ?, 'bug', '{}', 'open', ?, ?)").bind(id, d, NOW, NOW).run();
  }
  const find = (mark) => call("GET", `/api/admin/person?mark=${mark}`, undefined, h);
  assert.equal((await find("abcdef")).status, 409, "six letters match two phones");
  const one = await find("abcdef34");
  assert.equal(one.status, 200, JSON.stringify(one.data));
  assert.equal(one.data.mark, "abcdef34");
  assert.equal((await find("123456")).data.mark, "123456aa", "a six written down before still finds the one phone");
  assert.equal((await find("abcdef3")).status, 200);
  assert.equal((await find("zzzzzz")).status, 404);
  const { markRange } = await import("../src/units.js");
  assert.deepEqual(markRange("abcdef"), ["abcdef", "abcdefg"]);
});

test("[RVN-24] SVC-107 이달의 제작자 후보: from 30 쓰는 분 of the month, and on the 1st last month's finished ones", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call, DB } = server({ plaza: true });
  const h = await signIn(call);
  const from = (n) => ({ "cf-connecting-ip": `198.51.100.${n}` });
  const work = { format: "bamnat-recipe", v: 1, title: "야간 많은 교대 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
    pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }] };
  const up = async (n) => (await call("POST", "/api/recipes", { device: device(n), recipe: { ...work, title: `야간 세트 ${n}` }, scope: "public" }, from(n))).data.code;
  const a = await up(1);
  const b = await up(2);
  assert.notEqual(a, b);
  const ma = (await call("POST", "/api/recipes/makers/me", { device: device(1) }, from(1))).data.maker;
  const mb = (await call("POST", "/api/recipes/makers/me", { device: device(2) }, from(2))).data.maker;
  DB.db.prepare("INSERT INTO recipe_counts (code, day, applied) VALUES (?, '2026-11-29', 40)").run(a);
  DB.db.prepare("INSERT INTO recipe_counts (code, day, applied) VALUES (?, '2026-11-29', 12)").run(b);
  // The 1st of the next month: one put-on made the top three before; now it is not a candidate.
  t.mock.timers.setTime(Date.parse("2026-12-01T03:00:00Z"));
  assert.equal((await call("POST", `/api/recipes/${b}/applied`, { kind: "applied" }, from(9))).status, 200);
  const month = async () => (await call("GET", "/api/admin/recipes/makers/month", undefined, h)).data;
  let d = await month();
  assert.equal(d.month, "2026-12");
  assert.deepEqual(d.candidates.SHIFT, [], "one 쓰는 분 on the 1st");
  assert.equal(d.lastMonth, "2026-11");
  assert.deepEqual(d.last.SHIFT.map((x) => [x.maker, x.users]), [[ma, 40]], "last month's finished count, from 30 up");
  assert.deepEqual(d.last.ALL.map((x) => x.maker), [ma]);
  assert.equal(d.floor, 30);
  DB.db.prepare("UPDATE recipe_counts SET applied = 29 WHERE code = ? AND day = '2026-12-01'").run(b);
  assert.deepEqual((await month()).candidates.SHIFT, []);
  DB.db.prepare("UPDATE recipe_counts SET applied = 30 WHERE code = ? AND day = '2026-12-01'").run(b);
  d = await month();
  assert.deepEqual(d.candidates.SHIFT.map((x) => [x.maker, x.users]), [[mb, 30]]);
  assert.deepEqual(d.candidates.DAY, []);
  const { recipeMonthBefore } = await import("../src/recipe-community.js");
  assert.equal(recipeMonthBefore(Date.parse("2027-01-01T00:00:00Z")), "2026-12");
  assert.equal(recipeMonthBefore(Date.parse("2026-12-31T16:00:00Z")), "2026-12", "KST: already January");
});

test("[RVN-24] SVC-108 the release-note line names the maker only while their maker profile is open", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call, DB } = server({ plaza: true });
  const h = await signIn(call);
  const from = (n) => ({ "cf-connecting-ip": `198.51.100.${n}` });
  const work = (title) => ({ format: "bamnat-recipe", v: 1, title, summary: "칸이 잘 보여요", styles: ["SHIFT"],
    pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }] });
  const code = (await call("POST", "/api/recipes", { device: device(5), recipe: work("새벽 칸 부품"), scope: "public" }, from(5))).data.code;
  const owner = DB.db.prepare("SELECT owner FROM recipes WHERE code = ?").get(code).owner;
  DB.db.prepare("INSERT INTO nicks (device, nick, norm, at) VALUES (?, '새벽고양이', '새벽고양이', 0)").run(owner);
  DB.db.prepare("INSERT INTO recipe_tiers (code, tier, since, later, version) VALUES (?, 4, 0, 0, '2.0.0')").run(code);
  const lines = async () => (await call("GET", "/api/admin/growth/map?version=2.0.0", undefined, h)).data.lines;
  // A public work makes an open maker profile: the name the 장터 shows with the work.
  assert.deepEqual(await lines(), ["모두의 도구에서 기본이 된 것: 새벽 칸 부품(만든 분 새벽고양이)"]);
  const tools = (await call("GET", "/api/admin/growth/tools?show=adopted", undefined, h)).data.items;
  assert.equal(tools[0].named, true);
  // The maker hides the profile: the name leaves the line (and the admin sees it will).
  await call("POST", "/api/recipes/makers/me", { device: device(5), hidden: true }, from(5));
  assert.deepEqual(await lines(), ["모두의 도구에서 기본이 된 것: 새벽 칸 부품"]);
  assert.equal((await call("GET", "/api/admin/growth/tools?show=adopted", undefined, h)).data.items[0].named, false);
  // Shown again, then closed by the operator: no name either.
  await call("POST", "/api/recipes/makers/me", { device: device(5), hidden: false }, from(5));
  assert.match((await lines())[0], /새벽고양이/);
  DB.db.prepare("UPDATE recipe_makers SET closed = 1 WHERE device = ?").run(owner);
  assert.deepEqual(await lines(), ["모두의 도구에서 기본이 된 것: 새벽 칸 부품"]);
});

test("[RVN-24] SVC-109 스튜디오 요구: a move table (409 outside it), a merge into an ended group answers the moved askers, 90 a batch, the 요청 판 head stays", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call, DB } = server({ plaza: true });
  const h = await signIn(call);
  const from = (n) => ({ "cf-connecting-ip": `198.51.100.${n}` });
  const studio = (n, text) => call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "studio", text,
    info: { app: "1.36.0 (136)", model: "Pixel", android: "16", studio: "SHIFT" } }, from(n));
  await studio(1, "[스튜디오 › 꾸밈 층 › 스티커] 25일 칸에 돈 스티커 붙이고 싶어요");
  await studio(2, "[스튜디오 › 달력 칸 › 색] 주말 칸 배경을 그라데이션으로");
  const needs = (await call("GET", "/api/admin/growth/needs", undefined, h)).data.items;
  const x = needs.find((n) => n.area === "꾸밈 층");
  const y = needs.find((n) => n.area === "달력 칸");
  const act = (id, body) => call("POST", `/api/admin/growth/needs/${id}`, body, h);
  // (2) An ended group takes no '나와요' or '만드는 중'; '다시 열기' only for an ended one.
  assert.equal((await act(y.id, { action: "decline", note: "지금은 칸 그림이 무거워져요." })).data.told, 1);
  for (const body of [{ action: "official", version: "2.0.0" }, { action: "build", note: "x" }, { action: "decline", note: "또" }]) {
    const r = await act(y.id, body);
    assert.equal(r.status, 409, body.action);
    assert.equal(r.data.state, "declined");
  }
  assert.equal((await act(x.id, { action: "reopen" })).status, 409);
  // (1) Merging into the declined group: the moved asker hears that answer, not nothing.
  const merged = await act(x.id, { action: "merge", into: y.id });
  assert.equal(merged.status, 200, JSON.stringify(merged.data));
  assert.equal(merged.data.told, 1);
  const mine = (await call("GET", "/api/inquiries", undefined, { "x-device": device(1) })).data.inquiries[0];
  assert.match(mine.messages.at(-1).text, /지금은 넣기 어려워요/);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM studio_need_links WHERE need_id = ? AND told = 0").get(y.id).n, 0);
  // (4) A title change keeps the '[영역 › 도구]' head of the 요청 판 line.
  await act(y.id, { action: "reopen" });
  assert.equal((await act(y.id, { action: "market", title: "주말 칸 그라데이션 배경" })).status, 200);
  assert.equal((await act(y.id, { action: "title", title: "주말 칸에 그라데이션 배경" })).status, 200);
  const rid = DB.db.prepare("SELECT request_id FROM studio_needs WHERE id = ?").get(y.id).request_id;
  assert.equal(DB.db.prepare("SELECT body FROM recipe_requests WHERE id = ?").get(rid).body, "[달력 칸 › 색] 주말 칸에 그라데이션 배경");
  // (3) 95 askers: two batches, every one answered and marked.
  const now = Date.now();
  for (let i = 0; i < 95; i++) {
    const id = `qB${String(i).padStart(12, "0")}`;
    DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, ?, 'studio', '{}', 'open', ?, ?)").run(id, `dev${i}`, now, now);
    DB.db.prepare("INSERT INTO studio_need_links (src, ref, need_id, device, text, at, told) VALUES ('inq', ?, ?, ?, '', ?, 0)").run(id, y.id, `dev${i}`, now);
  }
  const shipped = await act(y.id, { action: "official", version: "2.0.0" });
  assert.equal(shipped.status, 200, JSON.stringify(shipped.data));
  assert.equal(shipped.data.told, 95);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM inquiry_messages WHERE inquiry_id LIKE 'qB%'").get().n, 95);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM studio_need_links WHERE need_id = ? AND told = 0").get(y.id).n, 0);
});

test("[RVN-24] SVC-106 스튜디오 요구 목록: every card's first five asks and totals in a few reads, not two a card", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  const h = await signIn(call);
  for (let i = 0; i < 12; i++) {
    const id = `nN${String(i).padStart(10, "0")}`;
    DB.db.prepare("INSERT INTO studio_needs (id, title, askers, created_at, updated_at) VALUES (?, ?, 7, ?, ?)").run(id, `요구 ${i}`, NOW, NOW - i);
    for (let k = 0; k < 7; k++) {
      DB.db.prepare("INSERT INTO studio_need_links (src, ref, need_id, device, text, at, cluster) VALUES ('inq', ?, ?, ?, ?, ?, ?)")
        .run(`r${i}-${k}`, id, `d${k}`, `부탁 ${k}`, NOW - (7 - k) * 1000, k < 3 ? "x" : `c${k % 2}`);
    }
  }
  await call("GET", "/api/admin/growth/needs", undefined, h);
  const real = DB.prepare.bind(DB);
  let reads = 0;
  DB.prepare = (sql) => { reads++; return real(sql); };
  const r = await call("GET", "/api/admin/growth/needs", undefined, h);
  DB.prepare = real;
  assert.equal(r.status, 200);
  assert.equal(r.data.items.length, 12);
  const one = r.data.items.find((x) => x.id === "nN0000000000");
  assert.deepEqual(one.samples.map((s) => s.text), ["부탁 0", "부탁 1", "부탁 2", "부탁 3", "부탁 4"]);
  assert.equal(one.asks, 7);
  assert.equal(one.nets, 2, "'x' is not counted, two clusters are");
  assert.ok(reads > 0 && reads < 20, `reads ${reads}`);
});

test("[RVN-24] SVC-111 서버 상태: the DB limit of the plan, the share token only over https, a 500 written at once and counts every 30 seconds", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { healthBoard, serverVersions, recordRequest, flushWatch } = await import("../src/ops-watch.js");
  // (1) The plan's database limit (Free 500 MB, Paid 10 GB, D1_MAX_BYTES wins).
  const free = server({ CF_PLAN: "free" });
  await free.call("GET", "/api/terms");
  const b = await healthBoard(free.env, NOW);
  assert.deepEqual([b.dbLimit, b.plan], [500 * 1024 ** 2, "Workers Free"]);
  const paid = server();
  await paid.call("GET", "/api/terms");
  assert.equal((await healthBoard(paid.env, NOW)).dbLimit, 10 * 1024 ** 3);
  const set = server({ D1_MAX_BYTES: "2147483648" });
  await set.call("GET", "/api/terms");
  assert.equal((await healthBoard(set.env, NOW)).dbLimit, 2 * 1024 ** 3);
  // (2) The admin token never goes over plain http to a share server out there.
  const sent = [];
  t.mock.method(globalThis, "fetch", async (url, init) => {
    sent.push([String(url), init?.headers?.authorization ?? null]);
    return new Response(JSON.stringify({ version: "2.0.0" }), { status: 200, headers: { "content-type": "application/json" } });
  });
  const token = "t".repeat(32);
  await serverVersions({ SHARE_BASE_URL: "http://share.example", SHARE_ADMIN_TOKEN: token });
  assert.ok(sent.length > 0);
  assert.ok(sent.every(([, auth]) => auth === null), JSON.stringify(sent));
  sent.length = 0;
  await serverVersions({ SHARE_BASE_URL: "https://share.example", SHARE_ADMIN_TOKEN: token });
  assert.equal(sent[0][1], `Bearer ${token}`);
  sent.length = 0;
  await serverVersions({ SHARE_BASE_URL: "http://127.0.0.1:8790", SHARE_ADMIN_TOKEN: token });
  assert.equal(sent[0][1], `Bearer ${token}`, "the PC's own share server");
  // (3) Counts wait 30 seconds; a 500 is written at the end of its request.
  const w = server();
  await w.call("GET", "/api/terms");
  await flushWatch(w.env, Date.now(), true);
  const rows = () => w.DB.db.prepare("SELECT SUM(n) AS n, SUM(err) AS err FROM req_stats WHERE route = '/api/x'").get();
  recordRequest(w.env, "/api/x", 200, 5, Date.now());
  assert.equal(await flushWatch(w.env, Date.now() + 10_000), 0, "ten seconds: not yet");
  recordRequest(w.env, "/api/x", 500, 5, Date.now());
  assert.ok((await flushWatch(w.env, Date.now() + 11_000)) > 0, "a 500: at once");
  assert.deepEqual({ ...rows() }, { n: 2, err: 1 });
  recordRequest(w.env, "/api/x", 200, 5, Date.now());
  assert.equal(await flushWatch(w.env, Date.now() + 20_000), 0);
  assert.ok((await flushWatch(w.env, Date.now() + 42_000)) > 0, "thirty seconds after the last write");
  assert.deepEqual({ ...rows() }, { n: 3, err: 1 });
});

test("[RVN-24] SVC-112 관측 규칙마다 켜지는 입력: active-drop, config-drop, feature-fall (켜짐과 바로 아래), and every rule has a test", async () => {
  const { obsRules } = await import("../src/observe.js");
  const fs = await import("node:fs");
  const base = { active: { play: [], config: [] }, quality: { vitals: null }, sample: { daily: 100, features: [] } };
  const on = (b, kind) => obsRules({ ...base, ...b }).find((f) => f.kind === kind);
  const series = (before, now, a, b) => [...Array(b).fill(before), ...Array(a).fill(now)].map((n) => ({ n }));
  // 하루 사용자: 7일 평균이 그 전 7일의 70% 아래, 그 전이 10명 이상.
  assert.equal(on({ active: { play: series(100, 69, 7, 7), config: [] } }, "active-drop").on, true);
  assert.equal(on({ active: { play: series(100, 71, 7, 7), config: [] } }, "active-drop").on, false, "a 29% fall is not yet");
  assert.equal(on({ active: { play: series(9, 1, 7, 7), config: [] } }, "active-drop").on, false, "fewer than 10 a day before");
  assert.equal(on({ active: { play: series(100, 50, 7, 6), config: [] } }, "active-drop"), undefined, "13 days are not enough to say");
  // 서버에 오는 횟수: 3일 평균이 그 전 10일의 60% 아래, 그 전이 하루 30번 이상.
  assert.equal(on({ active: { play: [], config: series(100, 59, 3, 10) } }, "config-drop").on, true);
  assert.equal(on({ active: { play: [], config: series(100, 61, 3, 10) } }, "config-drop").on, false);
  assert.equal(on({ active: { play: [], config: series(29, 1, 3, 10) } }, "config-drop").on, false, "fewer than 30 a day before");
  assert.match(on({ active: { play: [], config: series(100, 59, 3, 10) } }, "config-drop").caveat, /읽기 주기/);
  // 기능 쓰임: 그 전 20대 이상인 줄만(줄이 오는 것 자체가 반 넘게 준 것).
  const feature = (before) => ({ sample: { daily: 100, features: [{ event: "memo", name: "메모", now: 3, before }] } });
  assert.equal(on(feature(20), "feature-fall").on, true);
  assert.equal(on(feature(19), "feature-fall").on, false, "a small sample is not a signal");
  // Every rule obsRules can raise is named by some test (a new rule comes with the input that turns it on).
  const kinds = [...new Set([...fs.readFileSync(new URL("../src/observe.js", import.meta.url), "utf8").matchAll(/kind: "([a-z0-9-]+)"/g)].map((m) => m[1]))];
  const tests = fs.readdirSync(new URL(".", import.meta.url)).filter((f) => f.endsWith(".test.mjs"))
    .map((f) => fs.readFileSync(new URL(f, import.meta.url), "utf8")).join("\n");
  assert.ok(kinds.length >= 17, kinds.join(","));
  assert.deepEqual(kinds.filter((k) => !tests.includes(`"${k}"`)), []);
});

test("[RVN-24] T22c-06 the admin app calls one kind of work by one name: 관측, 기능 조건, 플래그 장부, 자동 순환", async () => {
  const { adminLib } = await import("../src/admin-lib.js");
  const { OBS_STYLE_NAMES } = await import("../src/observe.js");
  const { CORE_NAMES, CORE_FLAGS } = await import("../src/flag-catalog.js");
  const L = adminLib();
  assert.deepEqual(L.WORK_NAMES, OBS_STYLE_NAMES);
  assert.deepEqual(CORE_NAMES.work, OBS_STYLE_NAMES);
  assert.equal(OBS_STYLE_NAMES.HOURLY, "알바");
  assert.equal(CORE_FLAGS.find((f) => f.key === "type.hourly").name, "알바 근무 유형");
});

test("[RVN-24] I22cc-03·U22c-04 출시 점검표 fills itself each hour: the Play track, the notice, 판 건강 a day on, with when and what it saw", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB, env } = server();
  const h = await signIn(call);
  const set = (key, v) => DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)").bind(key, JSON.stringify(v)).run();
  const one = async () => (await call("GET", "/api/admin/release/2.0.0", undefined, h)).data;
  const stepOf = (v, key) => v.steps.find((s) => s.key === key);
  assert.equal((await call("POST", "/api/admin/release", { version: "2.0.0" }, h)).status, 200);
  // Without the Play key the step is a person's; with the track read, 단계적 20% is its 증거.
  assert.match(stepOf(await one(), "play").hint, /Play 연결 열쇠가 없어/);
  await set("play_track", { done: "1.39.0", rolling: { v: "2.0.0", fraction: 0.2 }, halted: null, at: Date.now() });
  const day = (ms) => new Date(ms).toISOString();
  await call("POST", "/api/admin/notices", { title: "2.0.0 업데이트 소식", body: "바뀐 것을 모았어요.", level: "info", from: day(Date.now()), until: day(Date.now() + 2 * DAY), action: { kind: "WHATS_NEW" } }, h);
  await DB.prepare("INSERT INTO studio_materials (id, kind, pack, name, w, h, state, at) VALUES ('m1', 'icon', 'p', 'a', 1, 1, 'ok', 0)").run();
  const seenAt = Date.now();
  assert.equal((await releaseTick(env, seenAt)).changed, 1);
  let v = await one();
  for (const key of ["play", "notice", "materials", "market"]) {
    const s = stepOf(v, key);
    assert.equal(s.autoDone, true, key);
    assert.equal(s.seenAt, seenAt, key);
  }
  assert.equal(stepOf(v, "play").evidence, "Play 단계적 출시 2.0.0 20%");
  assert.match(stepOf(v, "notice").evidence, /2\.0\.0 업데이트 소식/);
  assert.equal(stepOf(v, "materials").evidence, "공개 1 · 대기 0 · 숨김 0");
  // An hour later nothing new: the 증거 is not written again.
  t.mock.timers.tick(3600e3);
  assert.equal((await releaseTick(env, Date.now())).changed, 0);
  // The notice's days end: what was seen stays (it was posted for this version).
  await DB.prepare("UPDATE settings SET value = '[]' WHERE key = 'notices'").run();
  assert.equal(stepOf(await one(), "notice").done, true);
  // Shipped; 기능 일정 확인 is a person's. 판 건강 a day on with too few phones: it says why and waits.
  await set("shipped_version", { v: "2.0.0", at: Date.now(), by: "t" });
  assert.equal((await call("POST", "/api/admin/release/2.0.0", { step: "flags", done: true }, h)).status, 200);
  t.mock.timers.tick(DAY + 1000);
  const use = DB.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)");
  await releaseTick(env, Date.now());
  assert.match(stepOf(await one(), "health").hint, /이 판을 쓰는 휴대폰 숫자가 아직 없어/);
  const today = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
  await use.bind(today, "2.0.0", 10).run();
  await use.bind(today, "1.39.0", 10).run();
  await releaseTick(env, Date.now());
  v = await one();
  assert.equal(v.left, 1);
  assert.match(stepOf(v, "health").hint, /휴대폰-날이 판마다 100보다 적어/);
  // Enough phone-days on both versions and no more 문제 신고: 판 건강 is 저절로 확인, and the whole list ends by itself.
  for (let i = 0; i < 5; i++) {
    const d = new Date(Date.now() - i * DAY + 9 * 3600e3).toISOString().slice(0, 10);
    await use.bind(d, "2.0.0", 60).run();
    await use.bind(d, "1.39.0", 60).run();
  }
  const judged = Date.now();
  assert.equal((await releaseTick(env, judged)).finished, 1);
  v = await one();
  assert.equal(v.left, 0);
  assert.ok(v.doneAt);
  const health = stepOf(v, "health");
  assert.equal(health.autoDone, true);
  assert.equal(health.seenAt, judged);
  assert.match(health.evidence, /1\.39\.0보다 문제 신고·문의가 나빠지지 않았어요/);
  const log = await DB.prepare("SELECT actor, detail FROM audit WHERE action = 'release_step' AND detail LIKE '%저절로%'").all();
  assert.deepEqual(log.results.map((r) => [r.actor ?? "", r.detail]), [["", "모든 단계 끝(저절로)"]]);
});

test("[RVN-24] I22cc-03 판 건강 worse than the version before, or Play halted: the step says 나빠짐 and waits for a person", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB, env } = server();
  const h = await signIn(call);
  const set = (key, v) => DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)").bind(key, JSON.stringify(v)).run();
  const one = async () => (await call("GET", "/api/admin/release/2.0.0", undefined, h)).data;
  assert.equal((await call("POST", "/api/admin/release", { version: "2.0.0" }, h)).status, 200);
  await set("play_track", { done: "1.39.0", rolling: null, halted: "2.0.0", at: Date.now() });
  await releaseTick(env, Date.now());
  let v = await one();
  let play = v.steps.find((s) => s.key === "play");
  assert.equal(play.done, false);
  assert.match(play.bad, /출시가 멈춰 있어요/);
  assert.equal(v.bad, 1);
  await set("shipped_version", { v: "2.0.0", at: Date.now(), by: "t" });
  const use = DB.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)");
  for (let i = 0; i < 5; i++) {
    const d = new Date(Date.now() - i * DAY + 9 * 3600e3).toISOString().slice(0, 10);
    await use.bind(d, "2.0.0", 60).run();
    await use.bind(d, "1.39.0", 60).run();
  }
  const bug = DB.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, 'd', 'bug', ?, 'open', ?, ?)");
  for (let i = 0; i < 6; i++) await bug.bind(`q${i}`, JSON.stringify({ app: "2.0.0 (54)" }), Date.now() - i * 3600e3, Date.now()).run();
  t.mock.timers.tick(DAY + 1000);
  await releaseTick(env, Date.now());
  v = await one();
  const health = v.steps.find((s) => s.key === "health");
  assert.equal(health.done, false);
  assert.match(health.bad, /문제 신고가 1\.39\.0보다 많아요/);
  assert.equal(v.bad, 2);
  // A person looks and says so; the 나빠짐 is theirs to clear, and the list is not ended by the hour.
  assert.equal((await call("POST", "/api/admin/release/2.0.0", { step: "health", done: true }, h)).status, 200);
  v = await one();
  assert.equal(v.steps.find((s) => s.key === "health").bad, null);
  assert.equal(v.bad, 1);
  t.mock.timers.tick(3600e3);
  await releaseTick(env, Date.now());
  assert.equal((await one()).doneAt, null);
});

test("[RVN-24] U22c-05 스튜디오 재료: each pack shows who changed it when, and 되돌리기 puts back only what that change moved", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await signIn(call);
  const put = DB.prepare("INSERT INTO studio_materials (id, kind, pack, name, ord, w, h, free, state, at) VALUES (?, 'icon', ?, ?, ?, 1, 1, 1, ?, 0)");
  await put.bind("a000000000000001", "봄", "a", 0, "waiting").run();
  await put.bind("a000000000000002", "봄", "b", 1, "waiting").run();
  await put.bind("a000000000000003", "봄", "c", 2, "hidden").run();
  await put.bind("b000000000000001", "여름", "d", 0, "waiting").run();
  const states = async (pack) => (await DB.prepare("SELECT state FROM studio_materials WHERE pack = ? ORDER BY ord").bind(pack).all()).results.map((r) => r.state);
  const list = async () => (await call("GET", "/api/admin/recipes/materials", undefined, h)).data;
  const shown = await call("POST", "/api/admin/recipes/materials/state", { state: "ok", pack: "봄" }, h);
  assert.equal(shown.status, 200, JSON.stringify(shown.data));
  assert.ok(Number.isInteger(shown.data.log));
  let last = (await list()).last["봄"];
  assert.equal(last.what, "공개 3개");
  assert.ok(last.by, "who");
  assert.equal(last.at, NOW);
  // Undo: 출시 대기 goes back to 대기 and 숨김 to 숨김 (not 'hidden' for all, as the old opposite-state undo did).
  t.mock.timers.tick(60_000);
  const undo = await call("POST", "/api/admin/recipes/materials/undo", { log: shown.data.log }, h);
  assert.deepEqual([undo.status, undo.data.back], [200, 3]);
  assert.deepEqual(await states("봄"), ["waiting", "waiting", "hidden"]);
  last = (await list()).last["봄"];
  assert.equal(last.undoneAt, NOW + 60_000);
  assert.equal((await call("POST", "/api/admin/recipes/materials/undo", { log: shown.data.log }, h)).data.error, "already");
  // 잠금 (무료·광고) too; an older change cannot be undone over a newer one.
  const free = await call("POST", "/api/admin/recipes/materials/free", { pack: "봄", mode: "none" }, h);
  assert.equal((await list()).last["봄"].what, "무료 0 · 광고·프로 3");
  const again = await call("POST", "/api/admin/recipes/materials/state", { state: "hidden", pack: "봄" }, h);
  assert.equal((await call("POST", "/api/admin/recipes/materials/undo", { log: free.data.log }, h)).data.error, "newer");
  assert.equal((await call("POST", "/api/admin/recipes/materials/undo", { log: again.data.log }, h)).status, 200);
  assert.equal((await call("POST", "/api/admin/recipes/materials/undo", { log: free.data.log }, h)).status, 200);
  assert.deepEqual((await DB.prepare("SELECT free FROM studio_materials WHERE pack = '봄' ORDER BY ord").all()).results.map((r) => r.free), [1, 1, 1]);
  // '출시 대기 모두 공개' writes one line a pack.
  const all = await call("POST", "/api/admin/recipes/materials/state", { state: "ok", all: true }, h);
  assert.deepEqual(Object.keys(all.data.logs).sort(), ["봄", "여름"]);
  assert.equal((await list()).last["여름"].what, "공개 1개");
  const log = await DB.prepare("SELECT action FROM audit WHERE action = 'material_undo'").all();
  assert.equal(log.results.length, 3);
});

test("[RVN-24] I22cc-01 a signal made a dev item brings its 사건 꾸러미: what changed, numbers by version, first lines, error groups", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const h = await signIn(call);
  const first = NOW - 3 * 3600e3;
  const signal = { key: "delete-spike::2026-11-18", kind: "delete-spike", subject: "", tone: "bad", title: "앱 삭제가 늘었어요", why: "어제보다 두 배",
    dev: "앱 삭제가 늘어난 이유 찾기", unit: "명", value: 40, base: 20, now: 40, first, last: NOW, at: first, state: "new" };
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").bind(JSON.stringify({ list: [signal] })).run();
  // What happened around it: a switch turned off a day before, a 문제 신고 and a 1-star review after, a server error group.
  await DB.prepare("INSERT INTO audit (at, actor, action, target, detail) VALUES (?, '관리자', 'switch_off', 'PLAZA_WRITE', '광장 쓰기 · 시험')").bind(NOW - DAY).run();
  await DB.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES ('q1', 'h1', 'bug', ?, 'open', ?, ?)")
    .bind(JSON.stringify({ app: "2.0.0 (54)" }), NOW - 3600e3, NOW - 3600e3).run();
  await DB.prepare("INSERT INTO inquiry_messages (inquiry_id, sender, text, at) VALUES ('q1', 'user', ?, ?)").bind("달력이 안 열려요\n어제부터요", NOW - 3600e3).run();
  await DB.prepare("INSERT INTO store_reviews (id, stars, text, app, at, fetched_at) VALUES ('r1', 1, '업데이트 뒤로 느려요', '2.0.0', ?, ?)").bind(NOW - 7200e3, NOW).run();
  await DB.prepare("INSERT INTO error_groups (sig, route, message, app, first_at, last_at, total) VALUES ('e1', '/api/plaza/posts', 'TypeError: x', '', ?, ?, 7)").bind(NOW - DAY, NOW).run();
  await DB.prepare("INSERT INTO error_days (day, sig, count) VALUES (?, 'e1', 7)").bind(new Date(NOW + 9 * 3600e3).toISOString().slice(0, 10)).run();
  const r = await call("POST", "/api/admin/observe/signals", { key: signal.key, act: "dev" }, h);
  assert.equal(r.status, 200, JSON.stringify(r.data));
  const item = (await call("GET", `/api/admin/dev/${r.data.signal.devId}`, undefined, h)).data;
  const b = item.bundle;
  assert.ok(b, "the item carries its bundle");
  assert.equal(b.signal.title, "앱 삭제가 늘었어요");
  assert.equal(b.from, first - 2 * DAY);
  assert.ok(b.changes.some((c) => c.kind === "switch_off"), JSON.stringify(b.changes));
  assert.deepEqual(b.inquiries.map((q) => [q.kind, q.app, q.line]), [["bug", "2.0.0", "달력이 안 열려요"]]);
  assert.deepEqual(b.reviews.map((x) => [x.stars, x.line]), [[1, "업데이트 뒤로 느려요"]]);
  assert.deepEqual(b.errors.map((e) => [e.route, e.n]), [["/api/plaza/posts", 7]]);
  assert.ok(item.history.some((x) => x.action === "bundle"));
  assert.ok(!JSON.stringify(b).includes("h1"), "no install id");
});

test("[RVN-24] I22ca-03·U22c-07 a phone that turned 답 알림 off is not woken for an answer, and turning it on again brings it back", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { plazaPushAfter } = await import("../src/plaza-push.js");
  const { deviceHash } = await import("../src/index.js");
  const { call, DB } = server();
  await call("GET", "/api/terms");
  const asker = await deviceHash(device(1));
  const writer = await deviceHash(device(2));
  await DB.prepare("INSERT INTO posts (id, company_id, grp, device, body, kind, status, created_at) VALUES ('p1', 'style-shift', 'room', ?, '질문이에요', 'question', 'shown', ?)").bind(asker, NOW).run();
  const sent = [];
  t.mock.method(globalThis, "fetch", async (url, init) => {
    if (String(url).includes("oauth2")) return new Response(JSON.stringify({ access_token: "a", expires_in: 3600 }));
    sent.push(String(init?.body ?? ""));
    return new Response("{}");
  });
  const quiet = (off) => call("POST", "/api/plaza/quiet", { device: device(1), kind: "plaza_answer", off });
  assert.equal((await call("POST", "/api/plaza/quiet", { device: device(1), kind: "plaza_question", off: true })).status, 400);
  assert.deepEqual((await quiet(true)).data, { ok: true, off: true });
  const env = { DB };
  let r = await plazaPushAfter(env, { room: "style-shift", writer, parent: "p1", on: true }, NOW);
  assert.deepEqual(r, { kind: "plaza_answer", devices: [] });
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM plaza_quiet").get().n, 1);
  assert.deepEqual((await quiet(false)).data, { ok: true, off: false });
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM plaza_quiet").get().n, 0);
  // On again: the answer goes to the push sender (no FCM account here, so nobody is marked told, but it is no longer stopped before).
  r = await plazaPushAfter(env, { room: "style-shift", writer, parent: "p1", on: true }, NOW + 1);
  assert.equal(r.kind, "plaza_answer");
  // 모두 지우기 takes the line too.
  await quiet(true);
  const gone = await call("DELETE", "/api/mine", undefined, { "x-device": device(1) });
  assert.equal(gone.status, 200, JSON.stringify(gone.data));
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM plaza_quiet").get().n, 0);
});

test("[RVN-24] I22ca-02 광장 머리 숫자 come from a 요약 줄: a second open within a minute counts nothing, a post or a 출근 tap counts again", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  await policyOn(call);
  const write = (n, body, extra = {}) =>
    call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, tag: { s: "SHIFT", n: "night" }, ...extra }, from(n));
  const read = (n) => call("GET", "/api/companies/style-shift/setups/room/posts", undefined, { ...from(n), "x-device": device(n) });
  const tap = (n, kind) => call("POST", "/api/plaza/checkin", { device: device(n), room: "style-shift", kind }, from(n));
  assert.equal((await write(1, "야간 연속 사흘째 어떻게 버텨요?", { kind: "question" })).status, 201);
  // What one open asks the database: the head count (posts q …) and the 출근 count (GROUP BY kind) only when the 요약 줄 is old.
  const seen = [];
  const prepare = DB.prepare;
  DB.prepare = (sql) => (seen.push(sql), prepare(sql));
  const open = async (n) => {
    seen.length = 0;
    const p = (await read(n)).data;
    return { pulse: p.pulse, counted: seen.some((s) => s.includes("FROM posts q")), checkins: seen.some((s) => /FROM plaza_checkin WHERE day = \? AND room = \? GROUP BY kind/.test(s)), statements: seen.length };
  };
  const cold = await open(4);
  assert.deepEqual(cold.pulse, { today: 1, waiting: 1, working: 1, checkin: { counts: { day: 0, night: 0 } } });
  assert.ok(cold.counted && cold.checkins, "the first open counts");
  const warm = await open(5);
  assert.deepEqual(warm.pulse, cold.pulse, "the same numbers");
  assert.ok(!warm.counted && !warm.checkins, "a second open within a minute reads the 요약 줄 with the page");
  assert.ok(warm.statements < cold.statements, `${warm.statements} < ${cold.statements}`);
  // A new post: the next open counts again and sees it.
  assert.equal((await write(2, "암막 커튼이 제일이에요")).status, 201);
  const afterPost = await open(5);
  assert.ok(afterPost.counted);
  assert.equal(afterPost.pulse.today, 2);
  // An answer from someone else: no longer waiting, at once.
  const q = (await read(4)).data.posts.find((x) => x.body.startsWith("야간"));
  assert.equal((await write(3, "저는 낮잠을 두 번 나눠 자요", { parent: q.id })).status, 201);
  assert.equal((await open(5)).pulse.waiting, 0);
  // A 출근 tap: counted again, and my own tap rides along without being kept in the 요약 줄.
  await tap(4, "night");
  const mine = await open(4);
  assert.ok(mine.checkins);
  assert.deepEqual(mine.pulse.checkin, { counts: { day: 0, night: 1 }, mine: "night" });
  const other = await open(6);
  assert.ok(!other.checkins);
  assert.deepEqual(other.pulse.checkin, { counts: { day: 0, night: 1 } }, "another phone sees the count, not my tap");
  // Older than a minute: counted again.
  t.mock.timers.tick(61e3);
  assert.ok((await open(6)).counted);
  // A new Korean day: yesterday's 요약 줄 is not read.
  t.mock.timers.tick(DAY);
  const tomorrow = await open(6);
  assert.ok(tomorrow.counted);
  assert.equal(tomorrow.pulse.today, 0);
  DB.prepare = prepare;
  // 모두 지우기 needs nothing here: the 요약 줄 holds counts only, no phone.
  const cols = DB.db.prepare("SELECT name FROM pragma_table_info('plaza_pulse')").all().map((r) => r.name);
  assert.deepEqual(cols.sort(), ["at", "checkin", "day", "grp", "room", "today", "waiting", "working"]);
});

test("[RVN-24] K-결정-10 마을 돌봄 숫자: the hourly job counts last month once, the public page reads it from 12-09, totals only", async (t) => {
  const kst = (s) => Date.parse(`${s}+09:00`);
  t.mock.timers.enable({ apis: ["Date"], now: kst("2026-11-20T10:00:00") });
  const { careNumbersTick } = await import("../src/community-care.js");
  const { call, DB } = server();
  await call("GET", "/api/terms");
  const post = (id, reason, at) => DB.prepare(`INSERT INTO posts (id, company_id, grp, device, body, status, hide_reason, created_at, hidden_at)
    VALUES (?, 'style-shift', 'room', 'd1', '글', 'hidden', ?, ?, ?)`).bind(id, reason, at, at).run();
  await post("h0", "abuse", kst("2026-11-05T12:00:00")); // before 3.12 took effect: not counted
  await post("h1", "abuse", kst("2026-11-10T12:00:00"));
  await post("h2", "flags", kst("2026-11-30T23:59:00"));
  await post("h3", "spam", kst("2026-12-01T00:00:00")); // December's
  const note = (id, kind, at) => DB.prepare(`INSERT INTO care_notes (id, device, kind, reason, at, until, by) VALUES (?, 'd1', ?, 'abuse', ?, ?, 'operator')`)
    .bind(id, kind, at, kind === "warn" ? null : at + 3 * DAY).run();
  await note("c1", "warn", kst("2026-11-12T09:00:00"));
  await note("c2", "warn", kst("2026-11-13T09:00:00"));
  await note("c3", "opinion", kst("2026-11-14T09:00:00"));
  const appeal = (id, result, at) => DB.prepare("INSERT INTO care_appeals (id, device, target, inquiry_id, at, result, decided_at) VALUES (?, 'd1', ?, ?, ?, ?, ?)")
    .bind(id, `c${id}`, `q${id}`, at - DAY, result, at).run();
  await appeal("a1", "accepted", kst("2026-11-15T09:00:00"));
  await appeal("a2", "kept", kst("2026-11-16T09:00:00"));
  const env = { DB };
  const deps = { one: async (e, sql, ...v) => (await e.DB.prepare(sql).bind(...v).first()) ?? null, run: (e, sql, ...v) => e.DB.prepare(sql).bind(...v).run() };
  // In November nothing is kept yet (the month is not over).
  assert.deepEqual(await careNumbersTick(deps, env, Date.now()), { skipped: "before" });
  t.mock.timers.setTime(kst("2026-12-01T00:17:00"));
  assert.deepEqual(await careNumbersTick(deps, env, Date.now()), { counted: "2026-11" });
  assert.deepEqual(await careNumbersTick(deps, env, Date.now()), { kept: "2026-11" }, "counted once");
  // Before 12-09 the page gets nothing; the admin app sees it.
  let pub = await call("GET", "/api/community/numbers");
  assert.equal(pub.status, 200);
  assert.deepEqual(pub.data, { from: "2026-12-09", months: [] });
  const h = await signIn(call);
  const admin = (await call("GET", "/api/admin/care", undefined, h)).data.numbers;
  assert.equal(admin.shown, false);
  const nov = { month: "2026-11", since: "2026-11-09", hidden: 2, warned: 2, held: 1, appeals: { answered: 2, accepted: 1 } };
  assert.deepEqual(admin.months, [nov]);
  assert.equal(admin.now.hidden, 1, "이번 달 지금까지");
  // A post shown again after the count does not change November.
  await DB.prepare("UPDATE posts SET status = 'shown', hide_reason = NULL, hidden_at = NULL WHERE id = 'h1'").run();
  t.mock.timers.setTime(kst("2026-12-09T00:00:00"));
  pub = await call("GET", "/api/community/numbers");
  assert.deepEqual(pub.data, { from: "2026-12-09", months: [nov] });
  assert.equal(pub.headers.get("access-control-allow-origin"), "*", "the rules page on another address reads it");
  assert.match(pub.headers.get("cache-control"), /public, max-age=3600/);
  assert.ok(!/d1|device|c1|q/.test(JSON.stringify(pub.data.months)), "nothing that points to a person");
});

test("[RVN-24] T22c-04 현장·알바·가게 업종 무리: each takes only its own list, 투자 none, and the same-업종 lens reads them back", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { peopleTag, PEOPLE_TRADES } = await import("../src/plaza-people.js");
  const refuse = (f) => { throw new Error(f); };
  assert.deepEqual(peopleTag({ s: "FIELD", i: "build", n: "day" }, "style-field", refuse), { s: "FIELD", i: "build", n: "day" });
  assert.deepEqual(peopleTag({ s: "HOURLY", i: "conv" }, "style-hourly", refuse), { s: "HOURLY", i: "conv" });
  assert.deepEqual(peopleTag({ s: "BUSINESS", i: "beauty" }, "style-shop", refuse), { s: "BUSINESS", i: "beauty" });
  for (const [bad, room] of [[{ s: "FIELD", i: "conv" }, "style-field"], [{ s: "HOURLY", i: "semi" }, "style-hourly"], [{ s: "SHIFT", i: "build" }, "style-shift"],
    [{ s: "INVEST", i: "retail" }, "style-invest"], [{ s: "BUSINESS", i: "우리 가게" }, "style-shop"]]) {
    assert.throws(() => peopleTag(bad, room, refuse), /tag/, JSON.stringify(bad));
  }
  assert.ok(!("INVEST" in PEOPLE_TRADES));
  const { call } = server();
  await policyOn(call);
  const w = await call("POST", "/api/posts", { device: device(1), company: "style-hourly", setup: "room", body: "편의점 야간 혼자 괜찮을까요?", tag: { s: "HOURLY", i: "conv" } }, from(1));
  assert.equal(w.status, 201, JSON.stringify(w.data));
  const p = (await call("GET", "/api/companies/style-hourly/setups/room/posts", undefined, { ...from(2), "x-device": device(2) })).data.posts;
  assert.deepEqual(p[0].tag, { s: "HOURLY", i: "conv" });
});

test("[RVN-24] PC-21 고친 점: one phone that sent the same version twice stands behind it as one person", async () => {
  const { call, DB } = server({ plaza: true });
  const first = await call("POST", "/api/reports", report(1), from(1));
  assert.equal(first.status < 300, true);
  const again = await call("POST", "/api/reports", report(1), from(1));
  assert.equal(again.status < 300, true);
  const c = first.data.company.id;
  const g = first.data.setup;
  const count = () => DB.db.prepare("SELECT COUNT(*) AS n, COUNT(DISTINCT device) AS d FROM reports WHERE company_id = ? AND grp = ?").get(c, g);
  const rows = count();
  const senders = async () => (await call("GET", `/api/companies/${c}`)).data.setups.find((x) => x.id === g).senders;
  // Whatever the rows, the page counts the phone once (state() and readStates alike).
  assert.equal(await senders(), rows.d);
  assert.equal(await senders(), 1);
  // Two more phones bring it in: three people, as the version's history says too.
  await call("POST", "/api/reports", report(2), from(2));
  assert.equal(await senders(), 2);
  await call("POST", "/api/reports", report(3), from(3));
  assert.equal(await senders(), 3);
  const hist = (await call("GET", `/api/companies/${c}/history?setup=${g}`, undefined, { "x-device": device(1) })).data;
  assert.equal(hist.versions.length, 1);
  assert.equal(hist.versions[0].senders, 3);
  // The first phone sends it once more: the server keeps one row a phone. A row kept from before that rule (two from one phone)
  // still reads as one person.
  assert.equal((await call("POST", "/api/reports", report(1), from(1))).status < 300, true);
  assert.deepEqual([count().n, count().d], [3, 3]);
  const cols = DB.db.prepare("PRAGMA table_info(reports)").all().map((x) => x.name).filter((x) => x !== "id");
  DB.db.prepare(`INSERT INTO reports (id, ${cols.join(", ")}) SELECT 'legacyCopy000001', ${cols.join(", ")} FROM reports
    WHERE company_id = ? AND grp = ? ORDER BY created_at LIMIT 1`).run(c, g);
  assert.deepEqual([count().n, count().d], [4, 3], "two rows from one phone");
  assert.equal(await senders(), 3);
  const later = (await call("GET", `/api/companies/${c}/history?setup=${g}`, undefined, { "x-device": device(1) })).data;
  assert.equal(later.versions[0].senders, 3);
});
