// 2.0.0 갈래 Q2 4/4 (검토 23·24): 자동 순환 점검 줄의 말과, 자동 판정이 '왜' 걸렸는지·'아니에요' 기록.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { askOwner } from "../src/ledger.js";
import { billingWordOf } from "../src/ask-check.js";

const ask = (call, n, text, kind = "bug") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "2.0.0 (54)" } }).then((r) => r.data.id);

// 주인이 읽는 화면에 개발자 말(환경 변수 이름·HTTP 번호·versionCode·문서 장 번호·ISO 시각)이 나오지 않아요.
const DEV_WORDS = /PLAY_SERVICE_ACCOUNT|VAPID|BAMNAT_|versionCode|\(409\)|G10 \d장|dev-status|\d{4}-\d\d-\d\dT\d\d:/;

test("[RVA-38] 자동 순환 연결 점검: 줄마다 주인의 말로(Play 연결 열쇠·판 번호), 때는 한국 시간 '11. 12. 15:04'", async (t) => {
  const { call } = server({ t });
  const owner = await signIn(call);
  const c = (await call("GET", "/api/admin/loop/check", undefined, owner)).data;
  assert.ok(c.lines.length >= 6);
  for (const x of c.lines) assert.doesNotMatch(`${x.text} ${x.fix ?? ""}`, DEV_WORDS, x.name);
  assert.match(c.lines.find((x) => x.key === "play").text, /Play 연결 열쇠/);
  assert.match(c.lines.find((x) => x.key === "update").fix ?? "", /판 번호/);
  // 동기화 열쇠를 만든 뒤 한 번 쓰면 '마지막 사용'이 한국 시간 꼴로 보여요.
  const key = (await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data.key;
  await call("POST", "/api/admin/loop/sync", { lines: [] }, { authorization: `Bearer ${key}`, "x-admin": "1" });
  const again = (await call("GET", "/api/admin/loop/check", undefined, owner)).data;
  const sync = again.lines.find((x) => x.key === "syncKey");
  assert.equal(sync.state, "ok");
  assert.match(sync.text, /있음 · (아직 안 씀|마지막 사용 \d{1,2}\. \d{1,2}\. \d\d:\d\d)/);
});

test("[RVA-41] 결제·환불 기록과 사람이 보낼 답: 걸린 말을 보여 주고, '아니에요'는 그 말을 기록으로 남겨요", async (t) => {
  const { call } = server({ t });
  const owner = await signIn(call);
  // 판정 식이 걸린 말을 돌려줘요.
  assert.equal(askOwner("bug", "프로 결제했는데 환불해 주세요").word, "프로 결제");
  assert.equal(askOwner("bug", "알람이 안 울려요"), null);
  assert.equal(billingWordOf("구독 해지했는데 요금이 두 번 나갔어요"), "구독 해지");
  assert.equal(billingWordOf("급여가 더해지는 게 이상해요"), null);

  // 말 때문에 잡힌 결제·환불 기록: 화면에 그 말, '아니에요'로 빼면 기록(ask_miss)에 그 말이 남아요.
  const paid = await ask(call, 1, "영수증 어디서 봐요? 달력 색 바꾸는 법도요", "question");
  const v1 = (await call("GET", `/api/admin/inquiries/${paid}`, undefined, owner)).data;
  assert.deepEqual([v1.billing.by, v1.billing.word], ["words", "영수증"]);
  const off = await call("POST", `/api/admin/inquiries/${paid}/billing`, { on: false, miss: true }, owner);
  assert.equal(off.status, 200);
  assert.equal(off.data.billing, null);
  const miss = off.data.events.find((e) => e.action === "ask_miss");
  assert.ok(miss, "the miss is on the question's record");
  assert.equal(miss.detail, "billing '영수증'");
  // 손으로 정한 것은 '관리자가 정했어요'(말이 아니라서 '아니에요'도 없어요).
  const hand = await ask(call, 2, "위젯이 안 보여요");
  const on = (await call("POST", `/api/admin/inquiries/${hand}/billing`, { on: true }, owner)).data;
  assert.equal(on.billing.by, "admin");

  // 사람이 보내야 하는 Claude 답: 문의 글에서 걸린 말과 기준 이름, 답 글의 표현이면 그 이름.
  const claude = { authorization: `Bearer ${(await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data.key}`, "x-admin": "1" };
  const money = await ask(call, 3, "프로 결제했는데 환불해 주세요", "other");
  await call("POST", `/api/admin/inquiries/${money}/reply`, { text: "확인해 볼게요" }, claude);
  const v2 = (await call("GET", `/api/admin/inquiries/${money}`, undefined, owner)).data;
  assert.equal(v2.held[0].person, true);
  assert.deepEqual(v2.held[0].why, { name: "환불·결제·보상", word: "프로 결제", key: "money" });
  const plain = await ask(call, 4, "알람 소리가 작아요");
  await call("POST", `/api/admin/inquiries/${plain}/reply`, { text: "저는 AI 상담원이라 자동으로 답해 드려요" }, claude);
  const v3 = (await call("GET", `/api/admin/inquiries/${plain}`, undefined, owner)).data;
  assert.deepEqual(v3.held[0].why, { name: "답에 AI·자동 답 표현", reply: true });

  // '판정이 틀렸어요'는 기록만: 답은 그대로 사람이 보내기를 기다려요. 열쇠로는 못 해요.
  assert.equal((await call("POST", `/api/admin/inquiries/${money}/held/${v2.held[0].id}`, { miss: true }, claude)).status, 403);
  const after = await call("POST", `/api/admin/inquiries/${money}/held/${v2.held[0].id}`, { miss: true }, owner);
  assert.equal(after.status, 200);
  assert.equal(after.data.held.length, 1, "still waiting for a person");
  assert.equal(after.data.events.find((e) => e.action === "ask_miss")?.detail, "owner:money '프로 결제'");
});
