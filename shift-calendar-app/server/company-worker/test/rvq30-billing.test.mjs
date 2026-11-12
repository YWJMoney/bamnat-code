// 2.0.0 [RVQ-30] 검토 SVC-21: 결제·환불 기록(전자상거래법 3년 보관)을 낱말 하나로 정해 빠뜨리기도, 지나치게 잡기도 했어요. `npm test`.
// 이제 틀림없는 말·다른 뜻으로 쓰인 자리를 뺀 낱말, 문의 종류(앱의 '결제·환불'), 관리자 표시로 정해요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { isBillingRecord } from "../src/ask-check.js";
import { rescanBilling2 } from "../src/inquiries.js";


const ask = (call, n, text, kind = "question") => call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, public: true, info: { app: "2.0.0 (54)" } }).then((r) => r.data);

test("[RVQ-30] SVC-21 놓쳤던 결제 문의(프로 샀는데·광고 제거권·돈만 나가고·Play 주문 번호)는 기록이에요", () => {
  for (const t of ["프로 샀는데 적용이 안 돼요", "광고 제거 1년권 샀는데 광고가 나와요", "GPA.3312-4415-2233-12345 확인 부탁드려요", "돈만 나가고 프로가 안 돼요",
    "1년 이용권 영수증을 메일로 받고 싶어요", "인앱 결제 오류가 나요", "구글 플레이에서 결제 됐다는데 앱은 무료래요", "카드사에서 두 번 청구됐어요",
    "프리미엄 구입했는데 기기 바꾸니 없어졌어요", "주문번호 보내 드려요", "I was charged twice", "결제가 안 돼요", "과금이 됐어요"]) assert.equal(isBillingRecord(t), true, t);
});

test("[RVQ-30] SVC-21 같은 낱말의 다른 뜻(회사 결재 오타·수당 청구·가계부 영수증·카드 결제일·장보기 구매)은 기록이 아니에요", () => {
  for (const t of ["휴가 결제 올렸는데 근무표에 안 떠요", "팀장님 결제 받으면 근무 바꾸는데 메모 기능 있나요", "결제라인에 올렸어요", "야간 수당 청구할 때 쓸 근무 기록을 뽑고 싶어요",
    "출장비 청구 날짜 알림", "산재 보험 청구 서류 메모", "영수증 사진으로 가계부 넣기가 안 돼요", "카드 결제일을 가계부에 설정하고 싶어요", "장보기 구매 목록을 메모로 쓰고 싶어요",
    "가계부에 구매한 물건 적는 칸이 있나요", "프로필 사진을 바꾸고 싶어요", "프로젝트 비용 청구 일정", "돈 나가는 날 알림이 필요해요"]) assert.equal(isBillingRecord(t), false, t);
});

test("[RVQ-30] SVC-21 앱의 '결제·환불'로 보낸 문의는 글과 상관없이 기록이고, 모두의 문의에 안 나가고, 사람이 먼저 봐요", async (t) => {
  const { call, env } = server({ t });
  const admin = await signIn(call);
  const q = await ask(call, 1, "어제 산 게 안 보여요", "billing");
  assert.equal(q.kind, "billing");
  assert.equal(q.public, false, "운영자만 봐요");
  const row = env.DB.db.prepare("SELECT billing_at FROM inquiries WHERE id = ?").get(q.id);
  assert.ok(row.billing_at);
  const seen = (await call("GET", `/api/admin/inquiries/${q.id}`, undefined, admin)).data;
  assert.ok(seen.billing);
  assert.equal(seen.topic ?? seen.triage?.topic ?? "billing", "billing");
  const line = (await call("GET", "/api/admin/ledger?view=owner", undefined, admin)).data.items.find((r) => r.id === q.id);
  assert.ok(line, "최고 관리자 확인 줄");
  assert.equal(line.kindName, "결제·환불");
  assert.equal(line.answer.why, "환불·결제·보상");
});

test("[RVQ-30] SVC-21 남아 있는 문의를 새 기준으로 한 번 더 봐요: 빠뜨린 것은 켜고 잘못 잡은 것은 끄고, 관리자가 정한 것은 그대로", async (t) => {
  const { call, env } = server({ t });
  const admin = await signIn(call);
  const missed = (await ask(call, 1, "프로 샀는데 적용이 안 돼요")).id;
  const wrong = (await ask(call, 2, "휴가 결제 올렸는데 근무표에 안 떠요")).id;
  const byHand = (await ask(call, 3, "휴가 결제 올렸는데 근무표가 안 바뀌어요 확인해 주세요")).id;
  const db = env.DB.db;
  const row = (id) => db.prepare("SELECT billing_at, created_at FROM inquiries WHERE id = ?").get(id);
  // 옛 기준이 남긴 그대로: 빠뜨린 것은 꺼져 있고, 잘못 잡은 것은 켜져 있어요. 하나는 관리자가 손으로 켰어요.
  db.prepare("UPDATE inquiries SET billing_at = NULL WHERE id = ?").run(missed);
  db.prepare("UPDATE inquiries SET billing_at = 1 WHERE id = ?").run(wrong);
  assert.equal((await call("POST", `/api/admin/inquiries/${byHand}/billing`, { on: true }, admin)).status, 200);
  assert.deepEqual(await rescanBilling2(env, Date.now()), { on: 1, off: 1 });
  assert.equal(row(missed).billing_at, row(missed).created_at);
  assert.equal(row(wrong).billing_at, null);
  assert.ok(row(byHand).billing_at);
  // 한 번만이에요.
  db.prepare("UPDATE inquiries SET billing_at = 1 WHERE id = ?").run(wrong);
  assert.deepEqual(await rescanBilling2(env, Date.now()), { on: 0, off: 0 });
});
