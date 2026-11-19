// 2.0.0 [RVQ-59] 검토 I22c-03: 누구인지 모르게 한 사람 한 번만 세는 '쓰는 분'. 서버는 하루 표를 눈가림 서명하고(표를 보지 못함), 쓰인 표의
// 지문만 작품·숫자마다 30일 남겨요. 깃발 market.fair_count(처리방침 3.15)와 RECIPE_TICKET_KEY가 있을 때만. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";
import { flagDef } from "../src/flags.js";
import { TICKET, fairB64 as b64u, fairBig as bigOf, fairBytes as bytesOf, fairHash as fullHash, fairModPow as modPow, ticketKey } from "../src/fair-ticket.js";
import { RETENTION } from "../src/retention.js";

const BEFORE = Date.parse("2026-12-10T03:00:00Z");
const AFTER = Date.parse("2027-01-12T03:00:00Z");
const recipe = (title) => ({
  format: "bamnat-recipe", v: 1, title, summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
});
const policy315 = (DB) => DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
  .bind(JSON.stringify({ "3.15": { posted: "2026-12-11", effective: "2027-01-10", heldUntil: null, unfavourable: false } })).run();
const kst = (ms) => new Date(ms + 9 * 3600e3).toISOString().slice(0, 10);

let keyJson;
async function signingKey() {
  if (!keyJson) {
    const k = await crypto.subtle.generateKey({ name: "RSA-PSS", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
    keyJson = JSON.stringify(await crypto.subtle.exportKey("jwk", k.privateKey));
  }
  return keyJson;
}

/** What the app does (core BlindTicket): words, a random r, blinded = H(words)·r^e mod n; then sig = blindSig·r⁻¹ mod n. */
async function getTicket(call, n, day) {
  const pub = (await call("GET", "/api/recipes/ticket")).data;
  const N = bigOf(Buffer.from(pub.n, "base64url"));
  const E = bigOf(Buffer.from(pub.e, "base64url"));
  const nBytes = Math.ceil(N.toString(16).length / 2);
  const words = `bamnat-ticket|1|${day}|${b64u(crypto.getRandomValues(new Uint8Array(32)))}`;
  const r = bigOf(crypto.getRandomValues(new Uint8Array(nBytes - 1)));
  const blinded = ((await fullHash(words, nBytes)) * modPow(r, E, N)) % N;
  const res = await call("POST", "/api/recipes/ticket", { device: device(n), blinded: b64u(bytesOf(blinded, nBytes)) }, from(n));
  if (res.status !== 200) return { res };
  const inv = (a, m) => { let [o, x, y, z] = [a % m, m, 1n, 0n]; while (o > 1n) { const q = o / x; [o, x] = [x, o - q * x]; [y, z] = [z, y - q * z]; } return (y % m + m) % m; };
  const sig = (bigOf(Buffer.from(res.data.sig, "base64url")) * inv(r, N)) % N;
  return { res, ticket: { t: words, s: b64u(bytesOf(sig, nBytes)) } };
}

test("[RVQ-59] 깃발은 처리방침 3.15 줄이고, 장부에 지문 30일이 적혀 있어요", () => {
  const f = flagDef(TICKET.flag);
  assert.equal(f.risk, "LEGAL");
  assert.equal(f.default, false);
  assert.deepEqual(f.conditions.map((c) => c.policy ?? c.version), ["3.15", "3.15"]);
  const row = RETENTION.find((r) => r.table === "recipe_fair");
  assert.equal(row.prune.days, 30);
  assert.equal(row.backup, false);
  assert.equal(ticketKey({}), null);
  assert.equal(ticketKey({ RECIPE_TICKET_KEY: "{nope" }), null);
});

test("[RVQ-59] I22c-03 3.15 전에는 표가 없고 전처럼; 뒤에는 한 표가 한 작품에 한 번, 같은 와이파이의 가족도 한 사람씩", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: BEFORE });
  const { call, DB } = server({ plaza: true, RECIPE_TICKET_KEY: await signingKey() });
  const code = (await call("POST", "/api/recipes", { device: device(1), recipe: recipe("병동 야간 세트"), scope: "public" }, from(1))).data.code;
  await DB.prepare("UPDATE recipes SET state = 'visible' WHERE code = ?").bind(code).run();
  const applied = () => DB.prepare("SELECT applied, kept7 FROM recipes WHERE code = ?").bind(code).first();
  // 3.15 전: 표를 주지 않고, 표를 실어 보내도 전처럼(네트워크 한도) 세요.
  assert.deepEqual((await call("GET", "/api/recipes/ticket")).data, { on: false });
  assert.equal((await call("POST", "/api/recipes/ticket", { device: device(2), blinded: "AQAB" }, from(2))).status, 403);
  assert.deepEqual((await call("POST", `/api/recipes/${code}/applied`, { kind: "applied", ticket: { t: "x", s: "AQAB" } }, from(2))).data, { ok: true });
  assert.equal((await applied()).applied, 1);

  await policy315(DB);
  t.mock.timers.setTime(AFTER);
  const pub = (await call("GET", "/api/recipes/ticket")).data;
  assert.equal(pub.on, true);
  assert.ok(pub.n.length > 300 && pub.e === "AQAB");
  assert.equal(pub.d, undefined, "never the private parts");
  // 한 휴대폰은 하루 한 장.
  const today = kst(AFTER);
  const a = await getTicket(call, 3, today);
  assert.equal(a.res.status, 200, JSON.stringify(a.res.data));
  assert.equal((await getTicket(call, 3, today)).res.status, 429);
  // 같은 네트워크(병동 와이파이)의 다섯 분: 예전엔 셋에서 막혔지만 표가 있으면 다섯 모두.
  const ward = 40;
  const before = (await applied()).applied;
  for (let i = 0; i < 5; i++) {
    const { ticket } = await getTicket(call, 50 + i, today);
    const r = await call("POST", `/api/recipes/${code}/applied`, { kind: "applied", ticket }, from(ward));
    assert.deepEqual(r.data, { ok: true }, `member ${i}`);
  }
  assert.equal((await applied()).applied, before + 5);
  // 한 표를 다시 쓰면(다른 네트워크에서도) 세지 않아요. 7일 유지는 따로 한 번.
  assert.deepEqual((await call("POST", `/api/recipes/${code}/applied`, { kind: "applied", ticket: a.ticket }, from(60))).data, { ok: true });
  assert.deepEqual((await call("POST", `/api/recipes/${code}/applied`, { kind: "applied", ticket: a.ticket }, from(61))).data, { ok: true, counted: false });
  assert.deepEqual((await call("POST", `/api/recipes/${code}/applied`, { kind: "kept", ticket: a.ticket }, from(61))).data, { ok: true });
  assert.deepEqual({ ...(await applied()) }, { applied: before + 6, kept7: 1 });
  // 서버에는 지문·작품·숫자·날만: 표의 글자도 휴대폰도 없어요.
  const rows = (await DB.prepare("SELECT * FROM recipe_fair").all()).results;
  assert.equal(rows.length, 7);
  assert.deepEqual(Object.keys(rows[0]).sort(), ["code", "col", "day", "fp"]);
  assert.ok(rows.every((r) => !a.ticket.t.includes(r.fp) && r.fp.length === 22));
  // 위조·남의 날·서명 틀림은 표가 없는 것과 같아요(네트워크 한도로).
  const forged = { t: a.ticket.t.replace(/.$/, (c) => (c === "A" ? "B" : "A")), s: a.ticket.s };
  const old = (await getTicket(call, 70, kst(AFTER - 3 * 86400e3))).ticket;
  for (const [n, ticket] of [[80, forged], [81, old], [82, { t: a.ticket.t, s: "AQAB" }]]) {
    assert.deepEqual((await call("POST", `/api/recipes/${code}/applied`, { kind: "applied", ticket }, from(n))).data, { ok: true }, `falls back (${n})`);
  }
  assert.equal((await DB.prepare("SELECT COUNT(*) AS c FROM recipe_fair").first()).c, 7, "no 지문 for a ticket that does not check");
  // 30일 뒤 지문은 정리돼요(장부의 정기 정리).
  assert.equal(RETENTION.find((r) => r.table === "recipe_fair").prune.where, "day < ?");
});
