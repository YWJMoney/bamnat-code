// 2.0.0 갈래 Q1 4e 문의: [RVQ-50] 검토 SVC-70·SVC-22·U22b-01·U22b-04·U22-08·T22b-05·I22ba-04·I22bb-02·I22bc-03,
// [RVQ-51] 검토 SVC-71·I22bb-04, [RVQ-52] 검토 SVC-66·SVC-67·SVC-22(장부). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { generateKeyPairSync } from "node:crypto";
import { server, device, from, signIn } from "./helpers.mjs";
import { fcmForget, pushRetry, PUSH_RETRY } from "../src/fcm.js";
import { INQUIRY, INQUIRY_MOVES, inquiryMove, publicRisk } from "../src/inquiries.js";

const NOW = Date.parse("2026-11-20T03:00:00Z");
const mine = (n) => ({ ...from(n), "x-device": device(n) });
const ask = (call, n, text, extra = {}) =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "question", text, info: { app: "1.39.0 (53)", model: "P", android: "16" }, ...extra }, from(n));

function jpeg() {
  const seg = (marker, body) => [0xff, marker, ((body.length + 2) >> 8) & 0xff, (body.length + 2) & 0xff, ...body];
  const text = (s) => [...new TextEncoder().encode(s)];
  return Buffer.from(Uint8Array.from([0xff, 0xd8, ...seg(0xe0, text("JFIF\0\x01\x01\0\0\x01\0\x01\0\0")), ...seg(0xdb, Array(65).fill(1)),
    ...seg(0xc0, [8, 0, 1, 0, 1, 1, 1, 0x11, 0]), ...seg(0xda, [1, 1, 0, 0, 0x3f, 0]), 0x12, 0x34, 0x56, 0xff, 0xd9])).toString("base64");
}

const b64u = (bytes) => Buffer.from(bytes).toString("base64url");

/** 관리자 웹 푸시(admin-server.test.mjs와 같은 방법): 서버의 VAPID 열쇠와, 받은 것을 풀어 읽는 브라우저. */
async function vapidKeys() {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const pub = new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey));
  const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
  return { VAPID_PUBLIC_KEY: b64u(pub), VAPID_PRIVATE_KEY: jwk.d, VAPID_SUBJECT: "mailto:ops@bamnat.example" };
}
async function browser() {
  const pair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const ua = new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey));
  const auth = crypto.getRandomValues(new Uint8Array(16));
  const hmac = async (key, data) => new Uint8Array(await crypto.subtle.sign("HMAC", await crypto.subtle.importKey("raw", key, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]), data));
  const cat = (...p) => Uint8Array.from(p.flatMap((x) => [...x]));
  const te = new TextEncoder();
  return {
    subscription: { endpoint: "https://fcm.googleapis.com/fcm/send/admin-1", keys: { p256dh: b64u(ua), auth: b64u(auth) } },
    async read(body) {
      const bytes = new Uint8Array(body);
      const salt = bytes.slice(0, 16);
      const idlen = bytes[20];
      const as = bytes.slice(21, 21 + idlen);
      const sealed = bytes.slice(21 + idlen);
      const theirs = await crypto.subtle.importKey("raw", as, { name: "ECDH", namedCurve: "P-256" }, false, []);
      const shared = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: theirs }, pair.privateKey, 256));
      const ikm = await hmac(await hmac(auth, shared), cat(te.encode("WebPush: info\0"), ua, as, [1]));
      const prk = await hmac(salt, ikm);
      const cek = (await hmac(prk, cat(te.encode("Content-Encoding: aes128gcm\0"), [1]))).slice(0, 16);
      const nonce = (await hmac(prk, cat(te.encode("Content-Encoding: nonce\0"), [1]))).slice(0, 12);
      const key = await crypto.subtle.importKey("raw", cek, "AES-GCM", false, ["decrypt"]);
      const plain = new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: nonce }, key, sealed));
      return JSON.parse(new TextDecoder().decode(plain.slice(0, -1)));
    },
  };
}

test("[RVQ-50] SVC-70·I22bb-02: 같은 요청 번호·10분 안 같은 글은 한 문의이고, 사진이 틀린 문의는 하루 한도를 쓰지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server({ plaza: true });
  const first = await ask(call, 1, "알람이 오늘 아침에 안 울렸어요", { key: "req-0001-abcdefgh" });
  assert.equal(first.status, 201);
  // 신호가 약해 다시 보냄(같은 번호, 글이 조금 달라도): 먼저 받은 것을 그대로.
  const again = await ask(call, 1, "알람이 오늘 아침에 안 울렸어요!", { key: "req-0001-abcdefgh" });
  assert.equal(again.status, 200);
  assert.equal(again.data.id, first.data.id);
  assert.equal(again.data.again, true);
  // 옛 앱(번호 없음)이 같은 글을 10분 안에 다시: 한 건.
  const old1 = await ask(call, 2, "급여 화면에서 야간 수당이 안 보여요");
  const old2 = await ask(call, 2, "급여 화면에서 야간 수당이 안 보여요");
  assert.equal(old2.data.id, old1.data.id);
  // 10분이 지나면 새 문의예요.
  t.mock.timers.tick(INQUIRY.repeatMs + 1000);
  assert.notEqual((await ask(call, 2, "급여 화면에서 야간 수당이 안 보여요")).data.id, old1.data.id);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM inquiries").get().n, 3);
  // 틀린 번호는 400, 사진이 틀리면 400이고 하루 한도(5)를 쓰지 않아요.
  assert.equal((await ask(call, 3, "번호가 이상해요 알람", { key: "x" })).data.error, "key");
  for (let i = 0; i < 6; i++) assert.equal((await ask(call, 3, `사진이 깨졌어요 ${i}번째 알람`, { photos: ["not-a-photo"] })).status, 400);
  assert.equal((await ask(call, 3, "이번엔 사진 없이 보내요 알람이 늦어요")).status, 201);
});

test("[RVQ-50] SVC-70·U22b-01·T22b-05·I22ba-04: 이어 쓴 글도 사진·급함 알림(30분에 한 번)·다시 열림", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true, ...(await vapidKeys()) });
  const owner = await signIn(call);
  const b = await browser();
  await call("POST", "/api/admin/me/push", { subscription: b.subscription, kinds: ["inquiry", "urgent"] }, owner);
  const pushed = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    if (String(url).startsWith("https://fcm.googleapis.com/fcm/send/")) { pushed.push(init.body); return new Response(null, { status: 201 }); }
    return real(url, init);
  };
  t.after(() => { globalThis.fetch = real; });
  const q = (await ask(call, 1, "위젯 날짜가 안 바뀌어요", { photos: [jpeg()] })).data;
  assert.equal(q.photos, 1);
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "위젯을 한 번 지웠다 다시 놓아 주세요" }, owner);
  const before = pushed.length;
  const told = async () => Promise.all(pushed.slice(before).map((x) => b.read(x)));
  // 답한 뒤 이어 씀(사진 두 장, 급한 말): 다시 열림 + 급함 알림 한 번.
  const r = await call("POST", `/api/inquiries/${q.id}/messages`, { text: "다시 놓았는데도 알람이 안 울려요", photos: [jpeg(), jpeg()], key: "msg-0001-abcdefgh" }, mine(1));
  assert.equal(r.status, 201);
  assert.equal(r.data.status, "open");
  assert.equal(r.data.reopened, true);
  assert.equal(r.data.photos, 3);
  assert.deepEqual(r.data.photoList.map((p) => p.n), [1, 2, 3]);
  // 같은 번호로 다시 보내면 한 번(글·사진이 늘지 않아요).
  const dup = await call("POST", `/api/inquiries/${q.id}/messages`, { text: "다시 놓았는데도 알람이 안 울려요", photos: [jpeg()], key: "msg-0001-abcdefgh" }, mine(1));
  assert.equal(dup.status, 200);
  assert.equal(dup.data.photos, 3);
  // 사진은 한 문의에 4장까지예요.
  const full = await call("POST", `/api/inquiries/${q.id}/messages`, { text: "사진 더 보내요", photos: [jpeg(), jpeg()] }, mine(1));
  assert.equal(full.status, 409);
  assert.equal(full.data.error, "photos_full");
  assert.equal(full.data.left, 1);
  // 셋째·넷째 사진도 보낸 폰과 관리자가 열어요(옛 주소 1·2만 되던 것).
  assert.equal((await call("GET", `/api/inquiries/${q.id}/photos/3`, undefined, mine(1))).status, 200);
  const notes = await told();
  assert.equal(notes.length, 1, "이어 쓴 글 알림은 한 번");
  assert.match(notes[0].title, /급함/);
  assert.match(notes[0].body, /답한 뒤 다시 쓴 문의 1건 · 사진 2장/);
  assert.ok(!notes[0].body.includes("알람이 안 울려요"), "알림에 글은 넣지 않아요");
  // 30분 안의 다른 이어 쓰기는 알림 없이, 30분 뒤면 다시 한 번.
  await call("POST", `/api/inquiries/${q.id}/messages`, { text: "혹시 몰라 하나 더 적어요" }, mine(1));
  assert.equal(pushed.length - before, 1);
  t.mock.timers.tick(INQUIRY.followNoticeMs + 1000);
  await call("POST", `/api/inquiries/${q.id}/messages`, { text: "아직 그대로예요" }, mine(1));
  assert.equal(pushed.length - before, 2);
  // 관리자 목록과 한 건 화면에 '다시 열림'.
  const list = (await call("GET", "/api/admin/inquiries", undefined, owner)).data.inquiries;
  assert.equal(list.find((x) => x.id === q.id).reopened, true);
  // 답하면 다시 열림이 풀려요.
  const done = (await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "확인했어요" }, owner)).data;
  assert.equal(done.reopened, false);
});

test("[RVQ-50] SVC-70·U22b-04: 답을 쓰는 동안 다른 답이 갔거나 새 글이 오면 409로 한 번 묻고, '그래도 보내기'면 가요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server({ plaza: true });
  const owner = await signIn(call);
  const q = (await ask(call, 1, "백업 파일을 못 찾겠어요")).data;
  const seen = (await call("GET", `/api/admin/inquiries/${q.id}`, undefined, owner)).data.messages.length;
  // 그사이 다른 관리자(여기서는 같은 계정이 옛 관리자 앱처럼 seen 없이)가 답함.
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "설정 › 백업에서 찾아요" }, owner);
  const late = await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "백업은 설정에 있어요", seen }, owner);
  assert.equal(late.status, 409);
  assert.equal(late.data.error, "changed");
  assert.equal(late.data.asked, 0);
  assert.equal(late.data.answered.length, 1);
  assert.equal(late.data.view.messages.length, 2, "새 글이 보이는 화면을 함께 줘요");
  // 보낸 분이 더 쓴 것도 알려요.
  await call("POST", `/api/inquiries/${q.id}/messages`, { text: "찾았어요 고마워요" }, mine(1));
  const more = await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "다행이에요", seen: 2 }, owner);
  assert.equal(more.data.asked, 1);
  // 읽고 그래도 보내기.
  const sent = await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "다행이에요", seen: 2, again: true }, owner);
  assert.equal(sent.status, 200);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'admin'").get(q.id).n, 2);
  // 새 글이 없으면 그냥 가요.
  assert.equal((await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "또 물어 주세요", seen: 4 }, owner)).status, 200);
});

test("[RVQ-50] SVC-22·U22-08: 앱 문의 목록은 줄마다 묻지 않고, 고친 판 안내가 간 문의엔 '어느 판에서 고쳤는지' 한 줄", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server({ plaza: true });
  const owner = await signIn(call);
  const ids = [];
  // 하루 한도(5건)라 다섯 건마다 하루를 넘겨요.
  for (let i = 0; i < 12; i++) {
    if (i && i % INQUIRY.perDay === 0) t.mock.timers.tick(86_400_000 + 1000);
    ids.push((await ask(call, 1, `문의 ${i}번째예요 알람 소리가 작아요`)).data.id);
  }
  // 하나는 개발사항에 묶이고 고친 판이 나가 안내까지 갔어요.
  const item = (await call("POST", "/api/admin/dev", { title: "알람 소리 작음", inquiry: ids[0] }, owner)).data;
  env.DB.db.prepare("UPDATE dev_items SET status = 'done', released_in = '2.0.0', live_at = ? WHERE id = ?").run(NOW, item.id);
  env.DB.db.prepare("UPDATE dev_links SET told_at = ? WHERE inquiry_id = ?").run(NOW, ids[0]);
  let asked = 0;
  const real = env.DB.prepare.bind(env.DB);
  env.DB.prepare = (sql) => { asked++; return real(sql); };
  const list = (await call("GET", "/api/inquiries", undefined, { ...mine(1), "x-app": "1.39.0 (53)" })).data.inquiries;
  env.DB.prepare = real;
  assert.equal(list.length, 12);
  assert.ok(asked < 20, `질의 ${asked}번(전에는 12줄이면 50번 넘게)`);
  const fixed = list.find((x) => x.id === ids[0]);
  assert.equal(fixed.askFixed, true);
  assert.equal(fixed.fixedLine, "2.0.0에서 고쳤어요. 앱을 2.0.0 이상으로 업데이트한 뒤 한 번 봐 주세요.");
  assert.equal(list.find((x) => x.id === ids[1]).fixedLine, undefined);
});

test("[RVQ-50] I22bc-03: 문의 상태는 표로만 움직이고, 서버가 쓰는 상태 글자는 모두 표에 있어요", () => {
  assert.equal(inquiryMove("open", "answered"), true);
  assert.equal(inquiryMove("closed", "open"), true);
  assert.equal(inquiryMove("closed", "closed"), false);
  assert.equal(inquiryMove("open", "open"), false);
  const states = new Set(Object.keys(INQUIRY_MOVES));
  for (const to of Object.values(INQUIRY_MOVES).flat()) assert.ok(states.has(to), to);
  const dir = new URL("../src/", import.meta.url);
  for (const f of readdirSync(dir).filter((x) => x.endsWith(".js"))) {
    const src = readFileSync(new URL(f, dir), "utf8");
    for (const m of src.matchAll(/UPDATE inquiries SET[^"`]*?\bstatus = '([a-z]+)'/g)) assert.ok(states.has(m[1]), `${f}: ${m[1]}`);
  }
});

test("[RVQ-51] SVC-71·I22bb-04: 구글이 바쁘면 5분·30분·2시간 뒤 다시, 토큰은 '없는 토큰'일 때만 지우고, 관리자에게 '알림 못 감'", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const sa = JSON.stringify({ client_email: "push@test.iam.gserviceaccount.com", project_id: "bamnat-test", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  let answer = () => new Response("{}", { status: 503 });
  let sends = 0;
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith("https://oauth2.googleapis.com/")) return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }), { status: 200 });
    if (u.startsWith("https://fcm.googleapis.com/")) { sends++; return answer(); }
    return real(url, init);
  };
  t.after(() => { globalThis.fetch = real; fcmForget(); });
  const { call, env } = server({ plaza: true, FCM_SERVICE_ACCOUNT: sa });
  const owner = await signIn(call);
  await call("POST", "/api/push/phone", { device: device(1), token: "fcm-token-for-phone-one-0123456789abcdef:APA91b" }, from(1));
  const q = (await ask(call, 1, "알람 소리를 바꾸고 싶어요")).data;
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "설정 › 알람에서 바꿔요" }, owner);
  let row = env.DB.db.prepare("SELECT * FROM push_fails WHERE inquiry_id = ?").get(q.id);
  assert.equal(row.why, "busy");
  assert.equal(row.next_at, NOW + PUSH_RETRY.waits[0]);
  let view = (await call("GET", `/api/admin/inquiries/${q.id}`, undefined, owner)).data;
  assert.equal(view.push.why, "busy");
  assert.ok(view.push.retry);
  // 5분 전에는 다시 보내지 않고, 5분 뒤 다시(또 바쁨 → 30분 뒤).
  assert.equal(await pushRetry(env, NOW + 60_000), 0);
  assert.equal(sends, 1);
  await pushRetry(env, NOW + PUSH_RETRY.waits[0]);
  assert.equal(sends, 2);
  row = env.DB.db.prepare("SELECT * FROM push_fails WHERE inquiry_id = ?").get(q.id);
  assert.equal(row.tries, 2);
  assert.equal(row.next_at, NOW + PUSH_RETRY.waits[0] + PUSH_RETRY.waits[1]);
  // 400이지만 토큰이 아니라 보낸 글이 틀린 것: 토큰은 남고, 다시 보내지 않아요.
  answer = () => new Response(JSON.stringify({ error: { status: "INVALID_ARGUMENT", message: "Invalid value at 'message.data'", details: [] } }), { status: 400 });
  await pushRetry(env, NOW + PUSH_RETRY.waits[0] + PUSH_RETRY.waits[1]);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM push_phones").get().n, 1, "보낸 글이 틀린 400은 토큰을 지우지 않아요");
  row = env.DB.db.prepare("SELECT * FROM push_fails WHERE inquiry_id = ?").get(q.id);
  assert.equal(row.why, "error");
  assert.equal(row.next_at, null);
  // 보내지면 기록이 지워져요.
  answer = () => new Response("{}", { status: 200 });
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "더 궁금하면 물어 주세요" }, owner);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM push_fails").get().n, 0);
  view = (await call("GET", `/api/admin/inquiries/${q.id}`, undefined, owner)).data;
  assert.equal(view.push, null);
  // 구글이 '없는 토큰'(UNREGISTERED)이라고 하면 그때만 지우고, 까닭은 gone.
  answer = () => new Response(JSON.stringify({ error: { status: "NOT_FOUND", details: [{ errorCode: "UNREGISTERED" }] } }), { status: 404 });
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "하나 더 알려 드려요" }, owner);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM push_phones").get().n, 0);
  assert.equal(env.DB.db.prepare("SELECT why FROM push_fails WHERE inquiry_id = ?").get(q.id).why, "gone");
  // 서버 상태의 실패 수.
  assert.ok(env.DB.db.prepare("SELECT SUM(count) AS n FROM counters WHERE key LIKE 'push|%'").get().n >= 3);
});

test("[RVQ-52] SVC-66: 문의 100건 복사는 표마다 한 번씩 묶어 읽고, 자주 쓰는 답 씨앗은 한 번만 써요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server({ plaza: true });
  const owner = await signIn(call);
  const ids = [];
  for (let i = 0; i < 30; i++) ids.push((await ask(call, (i % 10) + 1, `복사할 문의 ${i}번 알람이 늦어요 ${i}`, { key: `copy-${String(i).padStart(4, "0")}-abcd` })).data.id);
  let asked = 0;
  const real = env.DB.prepare.bind(env.DB);
  env.DB.prepare = (sql) => { asked++; return real(sql); };
  const r = await call("GET", `/api/admin/inquiries/export?ids=${ids.join(",")}`, undefined, owner);
  env.DB.prepare = real;
  assert.equal(r.data.inquiries.length, 30);
  assert.deepEqual(r.data.inquiries.map((x) => x.id), ids, "고른 차례 그대로");
  assert.equal(r.data.inquiries[0].sameSender, 2, "같은 폰이 보낸 다른 문의 수");
  assert.ok(asked < 40, `질의 ${asked}번(전에는 30건이면 300번 넘게)`);
  // 문의를 열 때마다 settings에 쓰지 않아요(처음 한 번 씨앗을 심은 뒤로는).
  await call("GET", `/api/admin/inquiries/${ids[0]}`, undefined, owner);
  let writes = 0;
  env.DB.prepare = (sql) => { if (/INSERT OR IGNORE INTO settings \(key, value\) VALUES \('templates'/.test(sql)) writes++; return real(sql); };
  for (let i = 0; i < 3; i++) await call("GET", `/api/admin/inquiries/${ids[i]}`, undefined, owner);
  env.DB.prepare = real;
  assert.equal(writes, 0);
});

test("[RVQ-52] SVC-67: 밤낮 Q&A 공개 글은 검사하고 따로 저장해, 답을 고쳐도 공개를 다시 저장할 때만 바뀌고 기록에 전문", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server({ plaza: true });
  const owner = await signIn(call);
  assert.equal(publicRisk("위젯", "{이름}님 안녕하세요"), "채우지 못한 칸 {이름}");
  // 채우지 못한 칸, AI 표현은 막혀요.
  const bad = await call("POST", "/api/admin/templates", { label: "위젯", text: "{이름}님, 위젯을 다시 놓아 주세요", publicQ: "위젯 날짜가 안 바뀌어요" }, owner);
  assert.equal(bad.status, 400);
  assert.equal(bad.data.error, "public_text");
  // 미리 보기(dry)는 저장하지 않고 공개될 글만.
  const dry = await call("POST", "/api/admin/templates", { label: "위젯", text: "{앱버전}에서 고쳤어요. 위젯을 다시 놓아 주세요", publicQ: "위젯 날짜가 안 바뀌어요", dry: true }, owner);
  assert.equal(dry.data.publicA, "최신 버전에서 고쳤어요. 위젯을 다시 놓아 주세요");
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM reply_templates WHERE label = '위젯'").get().n, 0);
  const added = await call("POST", "/api/admin/templates", { label: "위젯", text: "{앱버전}에서 고쳤어요. 위젯을 다시 놓아 주세요", publicQ: "위젯 날짜가 안 바뀌어요" }, owner);
  const id = added.data.templates.find((x) => x.label === "위젯").id;
  let qa = (await call("GET", "/api/qa")).data.items;
  assert.equal(qa.find((x) => x.id === id).a, "최신 버전에서 고쳤어요. 위젯을 다시 놓아 주세요");
  // 공개 칸을 그대로 두고 답만 고치면 공개 글도 같이 바뀌지만, 같은 검사를 거치고 기록에 전문이 남아요.
  await call("POST", `/api/admin/templates/${id}`, { label: "위젯", text: "위젯을 길게 눌러 다시 놓아 주세요", publicQ: "위젯 날짜가 안 바뀌어요" }, owner);
  qa = (await call("GET", "/api/qa")).data.items;
  assert.equal(qa.find((x) => x.id === id).a, "위젯을 길게 눌러 다시 놓아 주세요");
  const log = env.DB.db.prepare("SELECT detail FROM audit WHERE action = 'template_public' ORDER BY id").all().map((r) => r.detail);
  assert.equal(log.length, 2);
  assert.match(log[1], /묻는 말: 위젯 날짜가 안 바뀌어요\n답: 위젯을 길게 눌러 다시 놓아 주세요/);
  // 옛 방식으로 공개된 줄(공개 글 칸이 빈)은 검사에 걸리면 내보내지 않아요.
  env.DB.db.prepare("INSERT INTO reply_templates (label, text, by, at, topic, public_q) VALUES ('옛', '{이름}님 고마워요', '', ?, '', '옛 질문')").run(NOW);
  qa = (await call("GET", "/api/qa")).data.items;
  assert.ok(!qa.some((x) => x.q === "옛 질문"));
  // 내리면 기록에 '내림'.
  await call("POST", `/api/admin/templates/${id}`, { label: "위젯", text: "위젯을 길게 눌러 다시 놓아 주세요", publicQ: "" }, owner);
  assert.match(env.DB.db.prepare("SELECT detail FROM audit WHERE action = 'template_public' ORDER BY id DESC LIMIT 1").get().detail, /^내림: 위젯 날짜가/);
});

test("[RVQ-52] SVC-22: 장부 줄 기록·상태는 그 줄만 보고(장부 전체를 다시 만들지 않음), 권한 없는 줄은 404", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server({ plaza: true });
  const owner = await signIn(call);
  const q = (await ask(call, 1, "장부에서 볼 문의예요 알람")).data;
  let asked = 0;
  const real = env.DB.prepare.bind(env.DB);
  env.DB.prepare = (sql) => { asked++; return real(sql); };
  const r = await call("POST", "/api/admin/ledger/state", { keys: [`inquiry:${q.id}`], state: "seeing" }, owner);
  env.DB.prepare = real;
  assert.deepEqual(r.data.changed, [`inquiry:${q.id}`]);
  assert.ok(asked < 25, `질의 ${asked}번`);
  assert.equal((await call("POST", "/api/admin/ledger/state", { keys: ["inquiry:AAAAAAAAAAAAAAAAAAAAAA"], state: "seeing" }, owner)).status, 404);
  assert.equal((await call("GET", `/api/admin/ledger/log?key=inquiry:${q.id}`, undefined, owner)).status, 200);
});
