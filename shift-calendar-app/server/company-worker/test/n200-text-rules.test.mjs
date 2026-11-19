// 2.0.0 [RVN-22] 검토 I10bb-03: what typed text may not hold is one table. core TextRules is the 원본 and writes src/text-rules.js
// (TextRulesExportTest); the phone checks a post with it before sending and shows the very letters, and the server refuses with the
// same rules. Here the server's personal() and contact() say of core's own samples what core says. 계좌번호 is the 처리방침 3.14 N몫 ①
// condition for 방 글에 금액 받기 (roomMoney). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { TEXT_RULES, TEXT_RULE_SAMPLES } from "../src/text-rules.js";
import { personal, contact, textCode } from "../src/index.js";
import { roomMoneyFromPolicy } from "../src/flags.js";
import { server, device, from, signIn } from "./helpers.mjs";
import { inquiryText } from "../src/inquiries.js";

test("[RVN-22] I10bb-03 the server says of every sample what the phone says", () => {
  assert.ok(TEXT_RULE_SAMPLES.length >= 20, "core's samples are here");
  for (const s of TEXT_RULE_SAMPLES) {
    assert.equal(personal(s.text), s.personal, `personal: ${s.text}`);
    assert.equal(contact(s.text), s.contact, `contact: ${s.text}`);
  }
});

test("[RVN-22] the rules are core's, not written again here", () => {
  const index = readFileSync(new URL("../src/index.js", import.meta.url), "utf8");
  assert.match(index, /from "\.\/text-rules\.js"/);
  assert.ok(!/const PERSONAL = \[/.test(index), "no rule list of the server's own");
  for (const kind of ["personal", "contact"]) {
    assert.ok(TEXT_RULES[kind].some((r) => r.code === "account"), `${kind} holds 계좌번호`);
    for (const r of TEXT_RULES[kind]) new RegExp(r.source, `${r.flags}g`); // every pattern is one JavaScript reads
  }
});

test("[RVN-22] 계좌번호 is refused in an inquiry, a room's post with amounts on and anywhere else", () => {
  assert.throws(() => inquiryText("환불은 국민은행 123456 78 901234 으로 부탁드려요"), (e) => e.code === "account" && e.field === "text");
  assert.equal(contact("이번 달 야간 수당 32만 원 받았어요"), null, "with 방 글에 금액 받기 on an amount goes");
  assert.equal(contact("이번 달 32만 원, 3333-01-2345678로 보내 주세요"), "account");
  assert.equal(personal("기본급 2,060,740원"), "amount");
  // A date range, a shift time and a team's name are not a number to send money to.
  for (const t of ["2026-11-13~2026-12-13", "주간 08:00~20:00", "1조 2조 3조 4조", "4조 2교대 · 12-12-24"]) assert.equal(contact(t), null, t);
});

test("[RVN-17] an app before 2.0.0 is told 'amount' for 계좌번호 (the word it knows); 2.0.0 says it knows 'account'", async () => {
  const req = (h) => new Request("https://x.test/api/posts", { headers: h });
  assert.equal(textCode("account", req({})), "amount");
  assert.equal(textCode("account", req({ "x-text-codes": "account" })), "account");
  assert.equal(textCode("phone", req({})), "phone");
  const { call } = server();
  const body = { device: device(1), company: "style-shift", setup: "room", body: "카드 1234-5678-9012-3456 로 보내요" };
  const old = await call("POST", "/api/posts", body, from(1));
  assert.deepEqual([old.status, old.data.error, old.data.field], [400, "amount", "body"]);
  const now = await call("POST", "/api/posts", body, { ...from(1), "x-text-codes": "account" });
  assert.deepEqual([now.status, now.data.error], [400, "account"]);
});

test("[RVN-17] COMP-14 방 글에 금액 받기 starts 7 days after 처리방침 3.14 is posted (운영 규칙 3판), never before it is posted", async (t) => {
  assert.equal(roomMoneyFromPolicy(null), null);
  assert.equal(roomMoneyFromPolicy(JSON.stringify({ "3.14": { posted: "2026-11-21", effective: "2026-12-21" } })), "2026-11-28");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-27T14:59:00Z") }); // 11-27 23:59 KST
  const { call, DB } = server();
  const h = await signIn(call);
  await call("POST", "/api/admin/settings", { adoptPoints: 3, rewardMonths: 3, roomMoney: 1 }, h);
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.14": { posted: "2026-11-21", effective: "2026-12-21", heldUntil: null, unfavourable: false } })).run();
  const basics = (await call("GET", "/api/admin/basics", undefined, h)).data;
  assert.equal(basics.roomMoneyFrom, "2026-11-28");
  assert.equal((await call("GET", "/api/terms")).data.roomMoney, false, "11-27 KST: not yet");
  const post = (n) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body: "야간 수당 한 달에 50만원 정도" }, from(n));
  assert.equal((await post(1)).data.error, "amount");
  t.mock.timers.setTime(Date.parse("2026-11-27T15:00:00Z")); // 11-28 00:00 KST
  assert.equal((await call("GET", "/api/terms")).data.roomMoney, true, "11-28 KST: on");
  assert.equal((await post(2)).status, 201);
  // 계좌번호 never, with amounts on.
  const acct = await call("POST", "/api/posts", { device: device(3), company: "style-shift", setup: "room", body: "50만원 3333-01-2345678로 보내 주세요" },
    { ...from(3), "x-text-codes": "account" });
  assert.equal(acct.data.error, "account");
});
