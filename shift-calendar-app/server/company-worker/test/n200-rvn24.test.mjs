// 2.0.0 [RVN-24] 갈래 N 8차 (검토 22c 서버 광장·운영·푸시): 오늘 출근의 한도, 근무형태별 투표의 남은 묶음, 광장 효과 숫자, 답 모음, 출시 점검표,
// 사고 타임라인, 제작자 후보, 출시 노트 이름, 스튜디오 요구 상태. 발견마다 그 문제를 재현한 시험 하나(LOOP-21). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";

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
  // Undoing a step clears 끝: it counts as open again, and the auto steps are read as they are now.
  const undo = await step({ step: "play", done: false });
  assert.equal(undo.data.doneAt, null);
  assert.equal(undo.data.left, 2);
  assert.deepEqual(undo.data.steps.filter((s) => !s.done && !s.skipped).map((s) => s.key), ["play", "notice"]);
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
  const { monthBefore } = await import("../src/recipe-community.js");
  assert.equal(monthBefore(Date.parse("2027-01-01T00:00:00Z")), "2026-12");
  assert.equal(monthBefore(Date.parse("2026-12-31T16:00:00Z")), "2026-12", "KST: already January");
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
