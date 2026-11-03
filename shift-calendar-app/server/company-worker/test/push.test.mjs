// 1.36 [PUSH-01] 문의 답 바로 알리기: the phone's FCM token, and one wordless data message when an answer is written. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { server, device, from, signIn } from "./helpers.mjs";
import { fcmForget } from "../src/fcm.js";

const TOKEN = "fcm-token-for-phone-one-0123456789abcdef:APA91b";
const ask = (call, n, text) => call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "question", text, info: { app: "1.36.0", model: "P", android: "16" } }, from(n));

test("[PUSH-01] a phone's token is kept; an answer sends it one data message with no words, and a gone token is forgotten", async (t) => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const sa = JSON.stringify({ client_email: "push@test.iam.gserviceaccount.com", project_id: "bamnat-test",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const sent = [];
  let gone = false;
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith("https://oauth2.googleapis.com/")) return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }), { status: 200 });
    if (u.startsWith("https://fcm.googleapis.com/")) {
      sent.push({ url: u, auth: init.headers.authorization, body: JSON.parse(init.body) });
      return new Response("{}", { status: gone ? 404 : 200 });
    }
    return real(url, init);
  };
  t.after(() => { globalThis.fetch = real; fcmForget(); });
  const { call, env } = server({ plaza: true, FCM_SERVICE_ACCOUNT: sa });
  const owner = await signIn(call);
  // A bad token is refused; a good one kept (and replaced, not doubled).
  assert.equal((await call("POST", "/api/push/phone", { device: device(1), token: "short" }, from(1))).data.error, "token");
  assert.equal((await call("POST", "/api/push/phone", { device: device(1), token: TOKEN }, from(1))).data.on, true);
  await call("POST", "/api/push/phone", { device: device(1), token: TOKEN }, from(1));
  assert.equal((await env.DB.prepare("SELECT COUNT(*) AS n FROM push_phones").first()).n, 1);
  const q = (await ask(call, 1, "급여 화면에서 야간 수당이 안 보여요")).data;
  await ask(call, 2, "다른 휴대폰의 질문이에요 알람이 늦어요");
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "확인해 볼게요" }, owner);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].url, "https://fcm.googleapis.com/v1/projects/bamnat-test/messages:send");
  assert.equal(sent[0].auth, "Bearer ya29.test");
  assert.deepEqual(sent[0].body.message.data, { kind: "inquiry" });
  assert.equal(sent[0].body.message.token, TOKEN);
  // Nothing of the question or the answer goes to Google.
  assert.ok(!JSON.stringify(sent[0].body).includes("확인해") && !JSON.stringify(sent[0].body).includes("야간"));
  // Google says the token is gone: it is forgotten, and the answer was saved anyway.
  gone = true;
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "고쳤어요, 업데이트해 주세요" }, owner);
  assert.equal((await env.DB.prepare("SELECT COUNT(*) AS n FROM push_phones").first()).n, 0);
  assert.equal((await env.DB.prepare("SELECT COUNT(*) AS n FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'admin'").bind(q.id).first()).n, 2);
  // The phone turns it off.
  await call("POST", "/api/push/phone", { device: device(1), token: TOKEN }, from(1));
  await call("DELETE", "/api/push/phone", { device: device(1) }, from(1));
  assert.equal((await env.DB.prepare("SELECT COUNT(*) AS n FROM push_phones").first()).n, 0);
});

test("[PUSH-01] without the secret nothing is sent and the token is still kept for later", async () => {
  const { call, env } = server({ plaza: true });
  assert.equal((await call("POST", "/api/push/phone", { device: device(3), token: TOKEN }, from(3))).data.on, false);
  assert.equal((await env.DB.prepare("SELECT COUNT(*) AS n FROM push_phones").first()).n, 1);
});
