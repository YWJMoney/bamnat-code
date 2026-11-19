// 1.39 [ADM3-44~47] 실서버 둘러보기(11-08) B·C에서 1.39로 고른 것: 오늘 › 주의 필요 네 줄(A9·B4·B8·B9), Claude 읽기(B10),
// 비슷한 회사 합치기 제안(B2). 화면 쪽(여러 판 한 줄 B3, 오늘 첫 화면 S7)은 test/ui/admin-ui.test.mjs [ADM3-45~47]. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn, report, from } from "./helpers.mjs";
import { sameish, simpleName } from "../src/company-similar.js";
import { opsAlertsFrom, LOGIN_TRIES, REWARD_DAYS } from "../src/admin-ops.js";

test("[ADM3-44] 주의 필요: 연달아 틀린 로그인, 오래 기다린 보상, 오래 안 쓴 관리자, 패스키 없음", () => {
  const now = Date.parse("2026-11-08T07:00:00Z");
  const got = {
    ops_login_tries: [{ places: 1, n: LOGIN_TRIES }],
    ops_owed: [{ n: 2, since: now - (REWARD_DAYS + 3) * 864e5 }],
    ops_idle_admins: [{ n: 1 }],
    ops_passkey: [{ n: 0 }],
  };
  const list = opsAlertsFrom({ VAPID_PUBLIC_KEY: "x", VAPID_PRIVATE_KEY: "x", VAPID_SUBJECT: "x", MAIL_API_KEY: "x", PLAY_SERVICE_ACCOUNT: "x" }, got, now);
  const by = (p) => list.find((a) => a.key.startsWith(p));
  assert.match(by("login-tries").title, /로그인 실패가 연달아 있어요\(1곳, 3번\)/);
  assert.equal(by("login-tries").kind, "spike", "it goes to the phone like a spike");
  assert.match(by("owed").title, /드릴 보상 2개가 7일 넘게/);
  assert.match(by("owed").meta, /10일째/);
  assert.equal(by("owed").kind, "late");
  assert.equal(by("idle-admins").to, "#/admins");
  assert.equal(by("passkey").kind, "nudge", "not pushed to a phone");
  // With a passkey and nothing else, none of them.
  assert.equal(opsAlertsFrom({}, { ops_passkey: [{ n: 1 }], ops_login_tries: [{ places: 0, n: 0 }], ops_owed: [{ n: 0 }], ops_idle_admins: [{ n: 0 }] }, now)
    .filter((a) => /^(login-tries|owed|idle-admins|passkey)/.test(a.key)).length, 0);
});

test("[ADM3-44] 오늘 reads them from the database: failed tries before the lock, a reward owed for 8 days", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const now = Date.now();
  DB.db.prepare("INSERT INTO counters (key, count, expires) VALUES ('login|someplace', 3, ?)").run(now + 3600e3);
  DB.db.prepare(`INSERT INTO rewards (id, report_id, rule_id, device, months, status, created_at) VALUES ('rw1', 'rp1', 'ru1', 'dev', 1, 'owed', ?)`).run(now - 8 * 864e5);
  const alerts = (await call("GET", "/api/admin/today", undefined, owner)).data.alerts;
  assert.ok(alerts.some((a) => /로그인 실패가 연달아/.test(a.title)), JSON.stringify(alerts));
  assert.ok(alerts.some((a) => /드릴 보상 1개가 7일 넘게/.test(a.title)));
  // 2.0.0 [RVQ-05] 검토 U21-05: 두 번째 확인도 패스키도 없으면 '패스키 없음' 대신 붉은 '비밀번호 하나로만' 줄이 맨 위에 와요.
  assert.ok(alerts.some((a) => a.key === "owner:single" && a.tone === "bad"));
});

test("[ADM3-44] B10: Claude's reads with the key are counted apart from what it changed", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  await call("GET", "/api/admin/cockpit", undefined, owner);
  const day = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
  DB.db.prepare(`INSERT INTO admin_keys (id, hash, label, created_at, expires_at, day, n, writes) VALUES ('k1', 'h1', 'Claude', ?, ?, ?, 9, 2)`)
    .run(Date.now() - 1000, Date.now() + 864e5, day);
  const d = (await call("GET", "/api/admin/cockpit", undefined, owner)).data;
  assert.equal(d.reads, 7);
});

test("[ADM3-45] 비슷한 회사: 실서버의 lg전자 세 이름은 짝, 다른 제품·짧은 이름은 아님", () => {
  const n = (x) => simpleName(x.normalize("NFKC").toLowerCase().replace(/[^\p{L}\p{N}]/gu, ""));
  assert.ok(sameish(n("lg전자 칠러공장"), n("LG전자(칠러)")));
  assert.ok(sameish(n("lg전자 평택칠러공장"), n("lg전자 칠러공장")));
  assert.equal(sameish(n("lg전자 칠러"), n("lg전자 세탁기")), null);
  assert.equal(sameish(n("삼성전자"), n("삼성전기")), null);
  assert.equal(sameish(n("한화"), n("한화오션")), null, "two letters are too short to say");
});

test("[ADM3-45] 비슷한 회사 짝: the busier one stays, 합치기 uses the company merge, '다른 회사예요' is not asked again", async () => {
  const { call } = server();
  const owner = await signIn(call);
  await call("POST", "/api/reports", report(1, { company: "lg전자 칠러공장" }), from(1));
  await call("POST", "/api/reports", report(2, { company: "lg전자 칠러공장" }), from(2));
  await call("POST", "/api/reports", report(3, { company: "LG전자(칠러)" }), from(3));
  await call("POST", "/api/reports", report(4, { company: "lg전자 세탁기" }), from(4));
  const d = (await call("GET", "/api/admin/companies/similar", undefined, owner)).data;
  assert.equal(d.pairs.length, 1, JSON.stringify(d.pairs));
  const [p] = d.pairs;
  assert.equal(p.into.reports, 2);
  assert.equal(p.from.name, "LG전자(칠러)");
  const skip = await call("POST", "/api/admin/companies/similar/skip", { a: p.from.id, b: p.into.id }, owner);
  assert.equal(skip.status, 200);
  assert.equal(skip.data.pairs.length, 0);
  assert.equal((await call("GET", "/api/admin/companies/similar", undefined, owner)).data.pairs.length, 0);
  assert.equal((await call("POST", "/api/admin/companies/similar/skip", { a: "x", b: "y" }, owner)).status, 400);
});
