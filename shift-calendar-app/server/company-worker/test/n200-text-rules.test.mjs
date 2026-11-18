// 2.0.0 [RVN-22] 검토 I10bb-03: what typed text may not hold is one table. core TextRules is the 원본 and writes src/text-rules.js
// (TextRulesExportTest); the phone checks a post with it before sending and shows the very letters, and the server refuses with the
// same rules. Here the server's personal() and contact() say of core's own samples what core says. 계좌번호 is the 처리방침 3.14 N몫 ①
// condition for 방 글에 금액 받기 (roomMoney). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { TEXT_RULES, TEXT_RULE_SAMPLES } from "../src/text-rules.js";
import { personal, contact } from "../src/index.js";
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
