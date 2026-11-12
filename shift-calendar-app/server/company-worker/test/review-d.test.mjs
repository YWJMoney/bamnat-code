// 개발 전 전체 검토(2026-11-12) 갈래 D: 서버·관리자 앱 고침마다 먼저 재현한 시험. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { grams } from "../src/inbox.js";

const ask = (call, n, text, kind = "bug", app = "1.39.0 (53)") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app } }).then((r) => r.data.id);

// ---- [RVD-03] 검토 ADMX-01: 낱말이 객체의 물려받은 이름이면 장부 전체가 500 ----

test("[RVD-03] grams takes words that are names on every object (constructor, toString, __proto__) as plain words", () => {
  const g = grams("급여 화면에서 is not a constructor 오류 toString valueOf hasOwnProperty __proto__ 월급");
  for (const w of ["constructor", "tostring", "valueof", "hasownproperty", "proto", "급여"]) assert.ok(g.words.has(w), w);
});

test("[RVD-03] an inquiry or a dev item with 'constructor' in it leaves 오늘, 운영 장부, the desk and the handoff open", async () => {
  const { call } = server();
  const admin = await signIn(call);
  await ask(call, 1, "{{constructor.constructor('x')}} 알람이 안 울려요");
  assert.equal((await call("POST", "/api/admin/dev", { title: '급여 화면에서 "is not a constructor" 오류' }, admin)).status, 201);
  for (const path of ["/api/admin/today", "/api/admin/ledger?view=all", "/api/admin/ledger?view=claude", "/api/admin/desk", "/api/admin/inquiries/handoff"]) {
    const r = await call("GET", path, undefined, admin);
    assert.equal(r.status, 200, `${path} ${r.text}`);
  }
});

// ---- [RVD-04] 검토 ADMX-02: 화면 묶음 둘이 같은 이름을 내놓아 뒤의 것이 덮음(판 2개 회사 상세가 빈 화면) ----

test("[RVD-04] no two admin screen groups give the same name, so V.changes stays plaza's 다른 점 drawing", async () => {
  const names = ["today", "plaza", "insight", "work", "flow", "ledger", "ops", "loop", "glance"];
  // Each group only defines its functions when called; A's parts are never run here.
  const A = new Proxy({}, { get: (_, k) => (k === "S" ? {} : k === "INBOX_NAMES" || k === "KIND_NAMES" ? {} : () => "") });
  const seen = new Map();
  const shared = new Set(["actions", "forms", "changeHandlers"]);
  for (const n of names) {
    const make = (await import(`../src/admin/views/${n}.js`))[`${n}Views`];
    for (const key of Object.keys(make(A))) {
      if (shared.has(key)) continue;
      assert.ok(!seen.has(key), `${key} from ${n} hides the one from ${seen.get(key)}`);
      seen.set(key, n);
    }
  }
  assert.equal(seen.get("changes"), "plaza");
});

// ---- [RVD-07] 검토 SVC-02·TEST-11: 사람이 봐야 하는 문의와 사람 없이 가면 안 되는 답, 예문 장부로 ----

import { readFileSync } from "node:fs";
import { askOwner, replyRisk } from "../src/ledger.js";
const CORPUS = JSON.parse(readFileSync(new URL("./ask-corpus.json", import.meta.url), "utf8"));

test("[RVD-07] every risky inquiry in the corpus goes to a person, under its own reason", () => {
  const missed = [];
  for (const [key, list] of Object.entries(CORPUS.ask)) for (const t of list) {
    const got = askOwner("bug", t)?.key ?? null;
    if (got !== key) missed.push(`${key} ← ${got}: ${t}`);
  }
  assert.deepEqual(missed, []);
});

test("[RVD-07] ordinary inquiries of shift workers are not held for a person", () => {
  assert.deepEqual(CORPUS.plain.map((t) => [t, askOwner("bug", t)?.key ?? null]).filter(([, k]) => k), []);
});

test("[RVD-07] replyRisk stops what says AI or opens the server, and lets the app's own words go", () => {
  assert.deepEqual(CORPUS.reply.risky.filter((t) => !replyRisk(t)), []);
  assert.deepEqual(CORPUS.reply.plain.filter((t) => replyRisk(t)), []);
});

// ---- [RVD-05] 검토 ADMX-03: '더해지는'의 '해지'가 결제·환불 기록(3년 보관)이 됨 ----

import { isBillingRecord } from "../src/ask-check.js";
import { rescanBilling } from "../src/inquiries.js";

test("[RVD-05] ordinary pay and alarm questions are no 결제·환불 record; a refund still is", () => {
  for (const t of ["야간 수당이 두 번 더해지는 것 같아요", "근무가 정해지면 알려 주세요", "알람 해지는 어디서 해요", "달력 구독 링크를 넣고 싶어요", "통신 요금 날짜를 가계부에 넣고 싶어요", "불편해지네요"])
    assert.equal(isBillingRecord(t), false, t);
  for (const t of ["결제했는데 프로가 안 켜져요", "환 불 해 주세요", "구독 해지하고 싶어요", "이용권 환불", "돈이 두 번 빠져나갔어요", "요금이 두 번 나갔어요"])
    assert.equal(isBillingRecord(t), true, t);
});

test("[RVD-05] a new inquiry with '더해지는' keeps no billing mark, and marks the old rule put on are taken off once (not the ones set by hand)", async () => {
  const { call, env } = server();
  const admin = await signIn(call);
  const plain = await ask(call, 1, "야간 수당이 두 번 더해지는 것 같아요");
  const refund = await ask(call, 2, "결제했는데 환불해 주세요");
  const byHand = await ask(call, 3, "근무가 정해지면 알려 주세요");
  const row = (id) => env.DB.db.prepare("SELECT billing_at FROM inquiries WHERE id = ?").get(id);
  assert.equal(row(plain).billing_at, null);
  assert.ok(row(refund).billing_at);
  // As the old rule left them: the plain one marked, and one an admin marked by hand.
  env.DB.db.prepare("UPDATE inquiries SET billing_at = 1 WHERE id = ?").run(plain);
  assert.equal((await call("POST", `/api/admin/inquiries/${byHand}/billing`, { on: true }, admin)).status, 200);
  assert.equal(await rescanBilling(env, Date.now()), 1);
  assert.equal(row(plain).billing_at, null);
  assert.ok(row(refund).billing_at);
  assert.ok(row(byHand).billing_at);
  // Once: it does not run again.
  env.DB.db.prepare("UPDATE inquiries SET billing_at = 1 WHERE id = ?").run(plain);
  assert.equal(await rescanBilling(env, Date.now()), 0);
});

// ---- [RVD-07] 검토 ADMX-06·07: 넘길 글과 '개발 결과 붙여 넣기'도 같은 문으로 ----

const thread = (call, id, n) => call("GET", `/api/inquiries/${id}`, undefined, { "x-device": device(n) }).then((r) => r.data);

test("[RVD-07] the handoff text gives no draft answer for an inquiry a person must see, and says why", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const refund = await ask(call, 1, "결제했는데 환불해 주세요");
  const human = await ask(call, 2, "지금 답하는 분 사람이세요?");
  const h = (await call("GET", "/api/admin/inquiries/handoff", undefined, admin)).data.text.split("\n");
  for (const id of [refund, human]) {
    const at = h.findIndex((l) => l.startsWith(`- q${id}`));
    assert.match(h[at], /관리자 확인/);
    assert.ok(!h[at + 2]?.includes("추천 답"), h[at + 2]);
  }
});

test("[RVD-07] 결과 붙여 넣기 holds an answer to a person-only inquiry or an answer that says AI, unless sent anyway, and names lines it could not read", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const refund = await ask(call, 1, "결제했는데 환불해 주세요");
  const plain = await ask(call, 2, "알람 소리를 바꾸고 싶어요");
  const block = [`q${refund} 답 환불해 드릴게요. 밤낮 운영팀`, `q${plain} 답 저는 AI 상담원이에요. 설정 › 알람에서 바꿔요.`, "q123 답 짧은 번호", "d12 완료 1.40.0"].join("\n");
  const dry = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block, dry: true }, admin)).data;
  assert.deepEqual(dry.asks.map((a) => Boolean(a.risk)), [true, true]);
  assert.deepEqual(dry.unread, ["q123 답 짧은 번호", "d12 완료 1.40.0"]);
  const r = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block }, admin)).data;
  assert.equal(r.answered, 0);
  assert.equal(r.held, 2);
  assert.equal((await thread(call, refund, 1)).messages.length, 1);
  // Sent anyway, by a person who read them.
  const again = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block, risky: true }, admin)).data;
  assert.equal(again.answered, 2);
});

// ---- [RVD-10] 검토 SVC-65: 같은 결과를 두 번 붙여 넣으면 답이 두 번 감 ----

test("[RVD-10] the same 결과 block twice answers once: its 묶음 was applied, and the same answer is not sent again", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const q = await ask(call, 1, "알람 소리를 바꾸고 싶어요");
  const h = (await call("GET", "/api/admin/inquiries/handoff", undefined, admin)).data;
  assert.equal((await call("POST", "/api/admin/inquiries/handoff/mark", { batch: h.batch, ids: [], asks: h.askIds, cut: h.cut }, admin)).status, 200);
  const block = [`묶음 ${h.batch}`, `q${q} 답 설정 › 알람에서 바꿀 수 있어요. 밤낮 운영팀`].join("\n");
  assert.equal((await call("POST", "/api/admin/inquiries/handoff/result", { text: block }, admin)).data.answered, 1);
  const dry = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block, dry: true }, admin)).data;
  assert.ok(dry.batch.appliedAt);
  const twice = await call("POST", "/api/admin/inquiries/handoff/result", { text: block }, admin);
  assert.equal(twice.status, 409);
  assert.equal(twice.data.error, "already_applied");
  // Without the 묶음 line (or sent again on purpose), the very same answer still goes once.
  const bare = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block.split("\n")[1] }, admin)).data;
  assert.equal(bare.answered, 0);
  const msgs = (await thread(call, q, 1)).messages;
  assert.equal(msgs.filter((m) => m.from === "admin").length, 1, JSON.stringify(msgs));
});

// ---- [RVD-08] 검토 SRV-03: 배포 뒤 health가 DB를 안 건드림 ----

test("[RVD-08] deep health reads the tables and the app's daily path, and is 503 on a database it cannot use", async () => {
  const { call, env } = server();
  const deep = await call("GET", "/api/admin/health?deep=1");
  assert.equal(deep.status, 200);
  assert.deepEqual([deep.data.schema, deep.data.config], [true, true]);
  // A database whose tables broke: the settings table is gone.
  env.DB.db.exec("DROP TABLE settings");
  const broken = await server({ plaza: true, DB: env.DB }).call("GET", "/api/admin/health?deep=1");
  assert.equal(broken.status, 503);
  assert.equal(broken.data.config === true && broken.data.schema === true, false);
  // The plain one stays as it was (the setup screen reads it).
  assert.deepEqual(Object.keys((await call("GET", "/api/admin/health")).data).sort(), ["database", "password", "salt", "version", "why"]);
});

// ---- [PC-32] 회사 서버가 도는 판을 알려 줘요(배포 도구가 이 값으로 지금 판·새 판을 봐요) ----

test("[PC-32] health says the running code's version, plain and deep, and nothing more", async () => {
  const { call } = server();
  assert.equal((await call("GET", "/api/admin/health")).data.version, SERVER_VERSION);
  assert.equal((await call("GET", "/api/admin/health?deep=1")).data.version, SERVER_VERSION);
  assert.match(SERVER_VERSION, /^\d+\.\d+\.\d+$/);
});

// ---- [RVD-09] 검토 SVC-89: 광장 답글·뽑힌 답 찾기가 글 표 전체를 훑음 ----

test("[RVD-09] replies and chosen answers are found through an index, not by reading every post", async () => {
  const { call, DB } = server();
  await call("GET", "/api/terms");
  const plan = (sql, ...p) => DB.db.prepare(`EXPLAIN QUERY PLAN ${sql}`).all(...p).map((r) => r.detail).join(" | ");
  assert.match(plan("SELECT * FROM posts WHERE parent IN (?, ?) ORDER BY created_at, rowid", "a", "b"), /posts_parent/);
  // plaza-people.js peoplePulse: questions still waiting for an answer.
  assert.match(plan(`SELECT COUNT(*) FROM posts q WHERE q.company_id = ?1 AND q.grp = ?2 AND q.status = 'shown' AND q.parent IS NULL
    AND NOT EXISTS (SELECT 1 FROM posts a WHERE a.parent = q.id AND a.status = 'shown' AND a.device <> q.device)`, "r", "g"), /posts_parent/);
  // plaza-people.js: answers of each writer that were chosen.
  const helped = plan(`SELECT a.device, COUNT(*) AS n FROM posts a CROSS JOIN posts q ON q.answer = a.id WHERE a.device IN (?, ?) AND a.status = 'shown'
    AND q.status = 'shown' AND a.device <> q.device GROUP BY a.device`, "d1", "d2");
  assert.match(helped, /posts_device.*posts_answer/);
});

// ---- [RVD-11] 검토 SVC-01: 나간 판 저장이 중간에 끊기면 남은 안내·공지가 영영 안 감 ----

import { shipNow, resumeShip, shippedVersion } from "../src/desk.js";
import { inboxAnswer } from "../src/inbox.js";
import { SERVER_VERSION } from "../src/version.js";

test("[RVD-11] one answer failing does not stop the others, the notice and the record; the version stays 'sending' and the next tick sends the rest once", async () => {
  const { call, env } = server();
  const admin = await signIn(call);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, admin)).status, 200);
  const qs = [await ask(call, 1, "잠금 화면 알람 소리가 안 나요"), await ask(call, 2, "위젯 글자가 작아요"), await ask(call, 3, "달력 첫 화면이 느려요")];
  for (const [i, q] of qs.entries()) {
    const item = (await call("POST", "/api/admin/dev", { title: `고칠 것 ${i}`, inquiry: q }, admin)).data.id;
    assert.equal((await call("POST", "/api/admin/inquiries/handoff/result", { text: `${item} 완료 1.40.0 | 공지: 고친 것 ${i}` }, admin)).status, 200);
  }
  const real = (e, id, text, at) => inboxAnswer(e, id, text, at, "자동 정리");
  let calls = 0;
  const flaky = (e, id, text, at) => (++calls === 1 ? Promise.reject(new Error("D1 잠시 오류")) : real(e, id, text, at));
  const first = await shipNow(env, "1.40.0", { notify: true, notice: true, by: "자동" }, Date.now(), flaky);
  assert.deepEqual([first.answered, first.failed, Boolean(first.notice)], [2, 1, true]);
  assert.equal((await shippedVersion(env)).pending, true);
  const again = await resumeShip(env, Date.now(), real);
  assert.deepEqual([again.answered, again.failed], [1, 0]);
  assert.equal((await shippedVersion(env)).pending, undefined);
  assert.equal(await resumeShip(env, Date.now(), real), null);
  for (const [i, q] of qs.entries()) {
    const said = (await thread(call, q, i + 1)).messages.filter((m) => m.from === "admin" && /1\.40\.0/.test(m.text) && /업데이트/.test(m.text));
    assert.ok(said.length >= 1, `q${i}`);
  }
  // The notice went up once, and the version is out for the app.
  const notices = JSON.parse(env.DB.db.prepare("SELECT value FROM settings WHERE key = 'notices'").get().value);
  assert.equal(notices.filter((n) => /1\.40\.0/.test(`${n.title} ${n.body}`)).length, 1);
});
