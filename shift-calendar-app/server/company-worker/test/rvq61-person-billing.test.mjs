// 2.0.0 [RVQ-30] PC-215: 관리자 앱 '이 휴대폰'의 '결제·환불 문의 N번'은 결제 기록 기준(isBillingRecord)과 한 잣대예요.
// '휴가 결제 올렸는데…'(우리 결제가 아니에요)는 세지 않고, '프로 샀는데 광고가 나와요'는 세요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from } from "./helpers.mjs";
import { personView } from "../src/person.js";

test("[RVQ-30] PC-215 '이 휴대폰'의 결제·환불 문의 수는 결제 기록과 같은 낱말 기준이에요", async (t) => {
  const { env, call } = server({ t });
  const dev = device(51);
  const send = (text) => call("POST", "/api/inquiries", { v: 1, device: dev, kind: "other", text, info: { app: "2.0.0 (54)" } }, from(51));
  for (const text of ["휴가 결제 올렸는데 달력에 안 보여요", "야간 수당 청구할 때 쓸 기록이 있어요", "프로 샀는데 광고가 나와요"]) {
    assert.equal((await send(text)).status, 201, text);
    t.mock.timers?.tick?.(1);
  }
  const kept = (await env.DB.prepare("SELECT DISTINCT device FROM inquiries").all()).results;
  assert.equal(kept.length, 1, "one phone");
  const view = await personView(env, kept[0].device, Date.now());
  const line = view.flags.find((f) => /결제·환불 문의/.test(f.text));
  assert.equal(line?.text, "결제·환불 문의 1번", "only the one about our own product");
});
