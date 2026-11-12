// 2.0.0 갈래 Q2 3/3 [RVA-28] 검토 ADMX-18: 활동 기록에 안쪽 코드(이유 열쇠·권한 열쇠·규칙 번호)가 그대로 보이지 않아요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, from, signIn } from "./helpers.mjs";

test("[RVA-28] 활동 기록: 거절 이유·관리자 권한은 말로, 채택한 규칙 번호는 빼고 회사 이름(누르면 검토)으로", async () => {
  const { call } = server();
  const h = await signIn(call);
  const a = await call("POST", "/api/reports", report(1, { company: "말로정밀" }), from(1));
  const b = await call("POST", "/api/reports", report(2, { company: "말로화학" }), from(2));
  assert.equal((await call("POST", `/api/admin/groups/${a.data.company.id}/${a.data.setup}/adopt`, {}, h)).status, 200);
  assert.equal((await call("POST", `/api/admin/groups/${b.data.company.id}/${b.data.setup}/reject`, { reason: "unclear" }, h)).status, 200);
  const perms = { review: "edit", inquiries: "view", dev: "none" };
  assert.equal((await call("POST", "/api/admin/invites", { email: "words@example.com", perms }, h)).status, 201);

  const log = (await call("GET", "/api/admin/audit", undefined, h)).data.entries;
  const by = (action) => log.find((e) => e.action === action);
  assert.equal(by("reject").detail, "정보가 부족해서 확인하기 어려워요");
  assert.equal(by("reject").label, "말로화학");
  assert.equal(by("adopt").detail, "", "the rule id is not words");
  assert.equal(by("adopt").link, "#/review");
  assert.equal(by("invite").detail, "검토 고치기 · 문의 보기");
  for (const e of log) assert.doesNotMatch(String(e.detail ?? ""), /\b(review|inquiries|unclear):?\b|^[A-Za-z0-9]{16}$/, `${e.action}: ${e.detail}`);
});
