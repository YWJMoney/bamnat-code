// 2.0.0 갈래 N [RVN-04][RVN-05]: 광장 알림이 차단한 분의 글로 오지 않고, 구글이 받은 휴대폰만 '알렸음'으로 적고, 오래 기다린 분부터
// 알려요(SVC-91·92). 할 일함 줄은 그 칸 권한이 있어야 바꾸고 봐요(SVC-97). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";
import { makerId } from "../src/recipes.js";
import { deviceHash } from "../src/index.js";
import { PLAZA_PUSH, plazaPushAfter } from "../src/plaza-push.js";
import { workAllowed } from "../src/work.js";

const NOW = Date.parse("2026-11-20T03:00:00Z");
const HOUR = 3600e3;
const as = (n) => ({ ...from(n), "x-device": device(n) });
const token = (n) => `fcm-token-for-phone-${n}-0123456789abcdef:APA91b`;
const write = (call, n, body, extra = {}) =>
  call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));

/** A server with 3.12 on and a fake Google; [fail] holds the tokens Google refuses for now. */
async function plaza(t) {
  const { generateKeyPairSync } = await import("node:crypto");
  const { fcmForget } = await import("../src/fcm.js");
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const sa = JSON.stringify({ client_email: "push@test.iam.gserviceaccount.com", project_id: "bamnat-test",
    private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const sent = [];
  const fail = new Set();
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith("https://oauth2.googleapis.com/")) return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }), { status: 200 });
    if (u.startsWith("https://fcm.googleapis.com/")) {
      const m = JSON.parse(init.body).message;
      if (fail.has(m.token)) return new Response("{}", { status: 503 });
      sent.push(m);
      return new Response("{}", { status: 200 });
    }
    return real(url, init);
  };
  t.after(() => { globalThis.fetch = real; fcmForget(); });
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const s = server({ FCM_SERVICE_ACCOUNT: sa });
  const h = await signIn(s.call);
  const r = await s.call("POST", "/api/admin/policy", { version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, h);
  assert.equal(r.status, 200, JSON.stringify(r.data));
  return { ...s, sent, fail };
}

async function block(DB, who, whom) {
  await DB.prepare("INSERT INTO recipe_blocks (device, maker, at) VALUES (?, ?, ?)")
    .bind(await deviceHash(device(who)), await makerId(await deviceHash(device(whom))), NOW).run();
}

test("SVC-92: 차단한 분의 답·질문은 알리지 않고, 내 글의 답 수에서도 빠져요", async (t) => {
  const { call, DB, sent } = await plaza(t);
  for (const n of [1, 2, 3]) await call("POST", "/api/push/phone", { device: device(n), token: token(n) }, from(n));
  // 1 blocked 2. 2's answer to 1's question does not ring 1's phone; 3's does.
  await block(DB, 1, 2);
  const q = (await write(call, 1, "야간 끝나고 몇 시간 자요?", { kind: "question" })).data.id;
  await write(call, 2, "저는 여섯 시간이요", { parent: q });
  assert.equal(sent.length, 0, "a blocked writer's answer");
  await write(call, 3, "낮잠을 두 번 나눠 자요", { parent: q });
  assert.deepEqual(sent.map((m) => [m.token, m.data.kind]), [[token(1), "plaza_answer"]]);
  // 내 글: 답 수 is what 1 sees when it opens the question, so 2's reply is not counted.
  const mine = (await call("GET", "/api/posts/mine", undefined, as(1))).data.items.find((p) => p.id === q);
  assert.equal(mine.replies, 1);
  await write(call, 1, "고마워요", { parent: q });
  assert.equal((await call("GET", "/api/posts/mine", undefined, as(1))).data.items.find((p) => p.id === q).replies, 2, "my own reply counts");
  // A watcher who blocked the asker does not hear of its 근무형태 question.
  await call("POST", "/api/plaza/watch", { device: device(3), room: "style-shift", form: "FOUR_TEAM" }, from(3));
  await call("POST", "/api/plaza/watch", { device: device(2), room: "style-shift", form: "FOUR_TEAM" }, from(2));
  await block(DB, 3, 1);
  sent.length = 0;
  await write(call, 1, "4조 2교대 야간 이틀째 어떻게 버텨요?", { kind: "question", tag: { s: "SHIFT", f: "FOUR_TEAM" } });
  assert.deepEqual(sent.map((m) => m.token), [token(2)]);
});

test("SVC-91: 구글이 받지 못한 휴대폰은 '알렸음'으로 적지 않아 다음 질문 때 알려요", async (t) => {
  const { call, DB, sent, fail } = await plaza(t);
  for (const n of [1, 2, 3]) await call("POST", "/api/push/phone", { device: device(n), token: token(n) }, from(n));
  for (const n of [2, 3]) await call("POST", "/api/plaza/watch", { device: device(n), room: "style-shift", form: "FOUR_TEAM" }, from(n));
  fail.add(token(3));
  await write(call, 1, "4조 2교대 야간 이틀째 어떻게 버텨요?", { kind: "question", tag: { s: "SHIFT", f: "FOUR_TEAM" } });
  assert.deepEqual(sent.map((m) => m.token), [token(2)]);
  const marked = (await DB.prepare("SELECT device FROM plaza_pushed WHERE kind = 'plaza_question'").all()).results.map((r) => r.device);
  assert.deepEqual(marked, [await deviceHash(device(2))], "only the phone Google took");
  // Google is back: the next question within 3 hours reaches 3 (not told yet) and not 2 (told).
  fail.clear();
  t.mock.timers.setTime(NOW + HOUR);
  await write(call, 1, "4조 2교대 주간 끝나고 운동하세요?", { kind: "question", tag: { s: "SHIFT", f: "FOUR_TEAM" } });
  assert.deepEqual(sent.map((m) => m.token), [token(2), token(3)]);
});

test("SVC-91: 지켜보는 분이 많으면 가장 오래 못 들은 분부터, 3시간 안에 들은 분은 빼고 골라요", async (t) => {
  const { call, DB, env, sent } = await plaza(t);
  const room = "style-shift";
  const writer = await deviceHash(device(900));
  const many = PLAZA_PUSH.watchers + 20;
  // 220 watchers, the first 20 watching longest; the newest 200 were all told an hour ago.
  for (let i = 0; i < many; i++) {
    const dev = `watcher-${String(i).padStart(4, "0")}`;
    await DB.prepare("INSERT INTO plaza_watch (device, room, form, at) VALUES (?, ?, 'FOUR_TEAM', ?)").bind(dev, room, NOW - (many - i) * 60e3).run();
    await DB.prepare("INSERT INTO push_phones (device, token, at) VALUES (?, ?, ?)").bind(dev, `${dev}-token-0123456789abcdef:APA91b`, NOW).run();
    if (i >= 20) await DB.prepare("INSERT INTO plaza_pushed (device, kind, at) VALUES (?, 'plaza_question', ?)").bind(dev, NOW - HOUR).run();
  }
  const told = await plazaPushAfter(env, { room, writer, parent: null, kind: "question", form: "FOUR_TEAM", on: true }, NOW);
  assert.equal(told.devices.length, 20, "only those not told within 3 hours");
  assert.ok(told.devices.every((d) => Number(d.slice(-4)) < 20));
  assert.equal(sent.length, 20);
  // Three hours on, all 220 are due; the 200 picked are the longest waiting (told an hour before the others), not the newest 200.
  sent.length = 0;
  const later = NOW + 3 * HOUR + 1;
  const next = await plazaPushAfter(env, { room, writer, parent: null, kind: "question", form: "FOUR_TEAM", on: true }, later);
  assert.equal(next.devices.length, PLAZA_PUSH.watchers);
  assert.ok(next.devices.every((d) => Number(d.slice(-4)) >= 20), "those told an hour before the first 20 come first");
});

// ---- [RVN-05] SVC-97 할 일함: 다른 칸의 줄 상태·기록은 그 칸 권한이 있어야 바꾸고 봐요 ----

const APP = { "x-admin": "1", origin: ORIGIN };
const NONE = { review: "none", inquiries: "none", dev: "none", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };

async function member(call, owner, perms, email, name, ip) {
  const inv = await call("POST", "/api/admin/invites", { email, perms: { ...NONE, ...perms } }, owner);
  assert.equal(inv.status, 201, inv.text);
  const tokenOf = inv.data.link.split("#")[1];
  assert.equal((await call("POST", "/api/admin/join", { token: tokenOf, name, password: `${name}-copies-2026` }, { ...APP, ...from(ip) })).status, 201);
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: `${name}-copies-2026` }, { ...APP, ...from(ip) });
  assert.equal(res.status, 200, res.text);
  return { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
}

test("SVC-97: 문의 권한이 없는 관리자는 한 번 상태가 적힌 문의 줄도 바꾸지 못하고, 기록도 그 칸 것만 봐요", async (t) => {
  const after = Date.parse("2026-11-30T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: after });
  const { call, DB } = server({ plaza: true });
  const owner = await signIn(call);
  const q = (await call("POST", "/api/inquiries", { v: 1, device: device(1), kind: "bug", text: "알람이 안 울려요", info: { app: "1.39.0", model: "P", android: "16" } }, from(1))).data;
  const key = `inquiry:${q.id}`;
  assert.equal((await call("POST", "/api/admin/work", { keys: [key], state: "seeing", note: "전화번호 확인 필요" }, owner)).status, 200);
  const shop = await member(call, owner, { community: "edit" }, "shop@example.com", "shop", 31);
  // The line has a state now; before 2.0.0 that was enough for anyone signed in.
  assert.equal((await call("POST", "/api/admin/work", { keys: [key], state: "done", reason: "handled" }, shop)).status, 404);
  assert.equal((await DB.prepare("SELECT state FROM work_states WHERE key = ?").bind(key).first()).state, "seeing");
  assert.equal((await call("GET", `/api/admin/work/log?key=${encodeURIComponent(key)}`, undefined, shop)).status, 404);
  assert.equal((await call("GET", "/api/admin/work/log", undefined, shop)).data.items.length, 0, "no other area's notes");
  assert.equal((await call("GET", "/api/admin/work/log", undefined, owner)).data.items[0].note, "전화번호 확인 필요");
  assert.equal(workAllowed({ owner: false, perms: { inquiries: "view" } }, key), true);
  assert.equal(workAllowed({ owner: false, perms: { community: "edit" } }, "admin:x"), false, "admin lines are the owner's");
  assert.equal(workAllowed({ owner: false, perms: {} }, "nope:x"), false);
});

test("SVC-97: 할 일함을 두 번 동시에 읽어도 '다시 열림'은 한 줄이에요", async (t) => {
  const after = Date.parse("2026-11-30T03:00:00Z");
  const DAYMS = 86400e3;
  t.mock.timers.enable({ apis: ["Date"], now: after });
  const { call, DB } = server({ plaza: true });
  let owner = await signIn(call);
  const q = (await call("POST", "/api/inquiries", { v: 1, device: device(1), kind: "question", text: "달력에서 근무 바꾸는 법", info: { app: "1.39.0", model: "P", android: "16" } }, from(1))).data;
  const key = `inquiry:${q.id}`;
  await call("POST", "/api/admin/work", { keys: [key], state: "done", reason: "answered" }, owner);
  t.mock.timers.setTime(after + 8 * DAYMS);
  owner = await signIn(call);
  await Promise.all([call("GET", "/api/admin/work", undefined, owner), call("GET", "/api/admin/work/count", undefined, owner)]);
  const reopens = (await DB.prepare("SELECT COUNT(*) AS n FROM work_log WHERE key = ? AND action = 'reopen'").bind(key).first()).n;
  assert.equal(reopens, 1);
  assert.equal((await DB.prepare("SELECT reopened FROM work_states WHERE key = ?").bind(key).first()).reopened, 1);
});
