// 2.0.0 [RVQ-48] 검토 SVC-26·SVC-68·SVC-69·U22b-12, [RVQ-49] 검토 SVC-73·T22b-07·U22b-11·I22b-02: 갈래 Q1 4d 돈.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { addMonths, recheckLapsed, syncVoided, VOIDED, invitePreview, INVITE_KEYS } from "../src/money.js";
import { moneyBoard } from "../src/money-board.js";
import { DAY } from "../src/units.js";

const phone = (n) => ({ "x-device": device(n), "cf-connecting-ip": `203.0.113.${n}` });

async function serviceAccount() {
  const key = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true, ["sign", "verify"]);
  const der = Buffer.from(await crypto.subtle.exportKey("pkcs8", key.privateKey)).toString("base64");
  const pem = `-----BEGIN PRIVATE KEY-----\n${der.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n`;
  return JSON.stringify({ client_email: "play@example.iam.gserviceaccount.com", private_key: pem });
}

/** Play as the test says: subscriptions, one-time products and the voided list (pages of [voided]). */
function fakePlay(t, { subs = {}, passes = {}, voided = [] }) {
  const asked = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (url) => {
    const u = String(url);
    asked.push(u);
    if (u === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "play-token", expires_in: 3600 });
    let m = u.match(/\/purchases\/subscriptionsv2\/tokens\/([^/:?]+)$/);
    if (m) return subs[decodeURIComponent(m[1])] ? Response.json(subs[decodeURIComponent(m[1])]) : new Response("", { status: 410 });
    m = u.match(/\/purchases\/products\/[^/]+\/tokens\/([^/:?]+)$/);
    if (m) return passes[decodeURIComponent(m[1])] ? Response.json(passes[decodeURIComponent(m[1])]) : new Response("", { status: 410 });
    if (u.includes("/purchases/voidedpurchases?")) {
      const page = Number(new URL(u).searchParams.get("token") || 0);
      return Response.json({ voidedPurchases: voided[page] ?? [], ...(voided[page + 1] ? { tokenPagination: { nextPageToken: String(page + 1) } } : {}) });
    }
    return new Response("", { status: 404 });
  };
  t.after(() => { globalThis.fetch = real; });
  return asked;
}

const playSub = (expiry, { trial = false, plan = "monthly" } = {}) => ({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE", etag: `e-${expiry}`, acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
  lineItems: [{ productId: "bamnat_pro", expiryTime: expiry, autoRenewingPlan: { autoRenewEnabled: true },
    offerDetails: { basePlanId: plan }, offerPhase: trial ? { freeTrial: {} } : { basePrice: {} } }],
});
const playPass = (at, state = 0) => ({ purchaseState: state, purchaseTimeMillis: String(at), orderId: `GPA.3300-${at}`, acknowledgementState: 1 });
const prices = { bamnat_pro: { monthly: 3900, yearly: 39000 }, bamnat_pro_pass_1y: { pass: 45000 }, fee: 15 };

test("[RVQ-48] SVC-69: 한 달은 한국 달력으로 더해요(한국 새벽에 준 보상이 말일 넘김에서 짧지 않게)", () => {
  // 한국 3월 1일 05시(UTC 2월 28일 20시)의 한 달은 한국 4월 1일 05시예요(UTC로 더하면 3월 28일 20시 = 한국 3월 29일).
  assert.equal(new Date(addMonths(Date.parse("2026-02-28T20:00:00Z"), 1)).toISOString(), "2026-03-31T20:00:00.000Z");
  // 한국 1월 31일 08시의 한 달은 한국 2월 28일 08시.
  assert.equal(new Date(addMonths(Date.parse("2026-01-30T23:00:00Z"), 1)).toISOString(), "2026-02-27T23:00:00.000Z");
  // 한국 낮은 앞과 같아요.
  assert.equal(new Date(addMonths(Date.parse("2026-01-31T00:00:00Z"), 1)).toISOString(), "2026-02-28T00:00:00.000Z");
  assert.equal(new Date(addMonths(Date.parse("2026-11-20T09:00:00Z"), 12)).toISOString(), "2027-11-20T09:00:00.000Z");
});

test("[RVQ-48] SVC-26: 앱을 열지 않아도 환불된 1년 이용권을 서버가 Play에서 읽어 끝내고, 돈 화면은 그날 환불로 세요", async (t) => {
  const now = Date.parse("2026-11-20T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now });
  const bought = now - 10 * DAY;
  const play = { passes: { "pass-token-aaaa": playPass(bought), "pass-token-bbbb": playPass(bought) },
    voided: [[{ purchaseToken: "pass-token-aaaa", orderId: `GPA.3300-${bought}`, voidedTimeMillis: String(now - 2 * DAY), voidedSource: 1 }],
      [{ purchaseToken: "sub-token-zzzz", orderId: "GPA.9-0", voidedTimeMillis: String(now - DAY) }]] };
  const asked = fakePlay(t, play);
  const { call, env, DB } = server({ PLAY_SERVICE_ACCOUNT: await serviceAccount() });
  for (const [n, token] of [[1, "pass-token-aaaa"], [2, "pass-token-bbbb"]]) {
    const r = await call("POST", "/api/purchases", { product: "bamnat_pro_pass_1y", token }, phone(n));
    assert.equal(r.data.state, "ACTIVE");
  }
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('price_table', ?)").run(JSON.stringify(prices));
  assert.equal(await syncVoided(env, Date.now()), 2, "이용권 하나와 서버가 모르는 결제 하나");
  const q = new URL(asked.find((u) => u.includes("voidedpurchases")));
  assert.equal(q.searchParams.get("type"), "1", "구독도 함께");
  assert.equal(Number(q.searchParams.get("startTime")), now - VOIDED.days * DAY, "처음엔 29일 앞부터");
  assert.deepEqual(DB.db.prepare("SELECT state FROM year_passes ORDER BY purchased_at, hash").all().map((r) => r.state).sort(), ["ACTIVE", "EXPIRED"]);
  // 6시간 안에는 다시 묻지 않고, 다시 읽어도 같은 것을 두 번 세지 않아요.
  assert.equal(await syncVoided(env, Date.now()), 0);
  t.mock.timers.tick(VOIDED.every + 1000);
  assert.equal(await syncVoided(env, Date.now()), 0);
  const second = new URL(asked.filter((u) => u.includes("voidedpurchases")).at(-2));
  assert.equal(Number(second.searchParams.get("startTime")), now - DAY, "지난번 읽은 때 하루 앞부터");
  // 앱이 같은 이용권을 다시 보내도(Play: 환불) 환불을 또 세지 않아요.
  play.passes["pass-token-aaaa"] = playPass(bought, 1);
  await call("POST", "/api/purchases", { product: "bamnat_pro_pass_1y", token: "pass-token-aaaa" }, phone(1));
  const board = await moneyBoard(env, Date.now(), 28);
  assert.equal(board.period.refunds, 2);
  assert.equal(board.period.passes, 2);
  assert.equal(board.period.parts.refunded, 45000, "모르는 결제의 환불은 가격을 몰라 0원");
  assert.equal(board.period.amount, 90000, "산 날의 금액은 그대로, 환불은 따로");
  assert.equal(board.period.afterFee, Math.round(45000 * 0.85));
  assert.ok(board.products.some((x) => x.name === "알 수 없는 결제" && x.refunds === 1));
  assert.equal(board.now.passes, 1);
});

test("[RVQ-48] SVC-68·U22b-12: 갱신과 체험 뒤 첫 결제를 서버가 Play에 다시 물어 따로 세요", async (t) => {
  const now = Date.parse("2026-11-20T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now });
  const subs = { "sub-token-renew": playSub(new Date(now + 2 * DAY).toISOString()), "sub-token-trial": playSub(new Date(now + 3 * DAY).toISOString(), { trial: true }) };
  fakePlay(t, { subs });
  const { call, env, DB } = server({ PLAY_SERVICE_ACCOUNT: await serviceAccount() });
  await call("POST", "/api/purchases", { product: "bamnat_pro", token: "sub-token-renew" }, phone(1));
  await call("POST", "/api/purchases", { product: "bamnat_pro", token: "sub-token-trial" }, phone(2));
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('price_table', ?)").run(JSON.stringify(prices));
  // 같은 날 다시 보내도 끝나는 날이 그대로면 세지 않아요.
  await call("POST", "/api/purchases", { product: "bamnat_pro", token: "sub-token-renew" }, phone(1));
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM money_events").get().n, 0);
  // 나흘 뒤: 하나는 갱신(끝나는 날이 한 달 앞으로), 하나는 체험이 끝나 첫 결제. 앱을 열지 않아도 매시 정리가 Play에 물어요.
  t.mock.timers.tick(4 * DAY);
  subs["sub-token-renew"] = playSub(new Date(now + 32 * DAY).toISOString());
  subs["sub-token-trial"] = playSub(new Date(now + 33 * DAY).toISOString());
  DB.db.prepare("UPDATE subs SET checked_at = ?").run(Date.now() - 2 * DAY);
  assert.equal(await recheckLapsed(env, Date.now()), 2);
  assert.deepEqual(DB.db.prepare("SELECT kind, product, plan FROM money_events ORDER BY kind").all().map((r) => ({ ...r })),
    [{ kind: "convert", product: "bamnat_pro", plan: "monthly" }, { kind: "renew", product: "bamnat_pro", plan: "monthly" }]);
  // 다시 물어도(끝나는 날 그대로) 더 세지 않아요.
  DB.db.prepare("UPDATE subs SET checked_at = ?").run(Date.now() - 2 * DAY);
  await call("POST", "/api/purchases", { product: "bamnat_pro", token: "sub-token-renew" }, phone(1));
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM money_events").get().n, 2);
  const board = await moneyBoard(env, Date.now(), 28);
  assert.deepEqual([board.period.started, board.period.converted, board.period.renewed], [1, 1, 1]);
  assert.deepEqual(board.period.parts, { fresh: 3900, converted: 3900, renewed: 3900, refunded: 0 });
  assert.equal(board.period.amount, 11700);
});

test("[RVQ-48] money_events는 설치·토큰 없이 400일 뒤 지워져요(보관 장부)", async () => {
  const { RETENTION } = await import("../src/retention.js");
  const row = RETENTION.find((r) => r.table === "money_events");
  assert.ok(row);
  assert.equal(row.device, undefined, "설치 해시 열이 없어요");
  assert.deepEqual(row.prune, { where: "at < ?", days: 400 });
});

test("[RVQ-49] U22b-11: 초대 규칙은 {dry: true}로 몇 분께 몇 건·몇 달이 생기는지 쓰지 않고 보고, 저장하면 그만큼 드릴 보상", async (t) => {
  const now = Date.parse("2026-11-20T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  await call("GET", "/api/admin/settings/invite", undefined, owner);
  const add = DB.db.prepare(`INSERT INTO invite_uses (invitee, phone, code, inviter, source, first_day, status, created_at, decided_at)
    VALUES (?, ?, 'CODE1', ?, 'link', '2026-11-01', 'qualified', ?, ?)`);
  // 앞 규칙(친구 8명)으로는 아무도 못 채웠는데, 3명으로 낮추면 두 분(5명 → 1건, 7명 → 2건).
  for (let i = 0; i < 5; i++) add.run(`a${i}`, `pa${i}`, "inviter-a", now - DAY, now - DAY);
  for (let i = 0; i < 7; i++) add.run(`b${i}`, `pb${i}`, "inviter-b", now - DAY, now - DAY);
  for (const [k, v] of [["friends", 8], ["months", 1], ["capMonths", 0]]) DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)").run(INVITE_KEYS[k], String(v));
  const dry = await call("POST", "/api/admin/settings/invite", { friends: 3, dry: true }, owner);
  assert.deepEqual(dry.data.preview, { inviters: 2, rewards: 3, months: 3, capped: 0, more: false });
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM rewards").get().n, 0, "미리 보기는 아무것도 쓰지 않아요");
  assert.equal(DB.db.prepare(`SELECT value FROM settings WHERE key = '${INVITE_KEYS.friends}'`).get().value, "8");
  // 초대만의 상한 1개월이면 한 분에 한 건씩, 나머지는 달 없이.
  assert.deepEqual(await invitePreview(env, { friends: 3, days: 7, months: 1, capMonths: 1 }, Date.now()), { inviters: 2, rewards: 2, months: 2, capped: 1, more: false });
  await call("POST", "/api/admin/settings/invite", { friends: 3 }, owner);
  assert.deepEqual({ ...DB.db.prepare("SELECT COUNT(*) AS n, SUM(months) AS m FROM rewards WHERE status = 'owed' AND rule_id = 'invite'").get() }, { n: 3, m: 3 });
});

test("[RVQ-49] I22b-02: Claude가 친구 초대·모두의 도구 보상에도 초안(근거)을 적고, 지급은 사람이 눌러요", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  await call("GET", "/api/admin/rewards", undefined, owner);
  const key = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data.key;
  const claude = { authorization: `Bearer ${key}`, "x-admin": "1" };
  const now = Date.now();
  const add = DB.db.prepare(`INSERT INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at, deferred)
    VALUES (?, ?, ?, ?, 1, 'owed', '', ?, NULL, 0)`);
  add.run("iAbCdEfGhJkLmNpQrStUvW", "invite:iAbCdEfGhJkLmNpQrStUvW", "invite", "dev-hash-1", now);
  add.run("mAbCdEfGhJkLmNpQrStUv", "maker:pick:X", "maker", "dev-hash-2", now);
  for (const id of ["iAbCdEfGhJkLmNpQrStUvW", "mAbCdEfGhJkLmNpQrStUv"]) {
    const r = await call("POST", "/api/admin/drafts", { kind: "reward", id, pick: "paid", note: "친구 셋 모두 Play 확인, 몰림 없음" }, claude);
    assert.ok(r.status < 300, `${id}: ${r.status} ${JSON.stringify(r.data)}`);
  }
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM rewards WHERE status = 'owed'").get().n, 2, "지급은 여전히 사람 몫");
  const bad = await call("POST", "/api/admin/drafts", { kind: "reward", id: "x123", pick: "paid", note: "?" }, claude);
  assert.equal(bad.status, 400);
});
