// 2.0.0 [RVQ-57] 검토 I22c-02: 팀 방의 실시간과 웹 알림. 열린 페이지는 방의 WebSocket으로 '바뀌었어요'를 듣고, 알림(깃발 team.push,
// 처리방침 3.15)은 켠 분에게만 세 가지(요청·답, 리더 결정, 마감·다시 열림·마감 하루 전)를 보내요. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import worker, { readsForget, TeamRound, TEAM_LIVE_JS, TEAM_SW_JS } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}

/** Like Cloudflare's: one object per name, one request at a time, its open WebSockets, storage that waits a turn. */
function rooms(env) {
  const objects = new Map();
  const tick = () => new Promise((r) => setTimeout(r, 1));
  return {
    objects,
    idFromName: (name) => name,
    get(name) {
      if (!objects.has(name)) {
        const data = new Map();
        let alarm = null;
        const sockets = [];
        const storage = {
          data, alarm: () => alarm,
          async get(k) { return data.has(k) ? structuredClone(data.get(k)) : undefined; },
          async delete(k) { data.delete(k); },
          async list({ prefix }) { await tick(); return new Map([...data].filter(([k]) => k.startsWith(prefix)).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, structuredClone(v)])); },
          async put(entries) { await tick(); for (const [k, v] of Object.entries(entries)) data.set(k, structuredClone(v)); },
          async deleteAll() { data.clear(); },
          async getAlarm() { return alarm; }, async setAlarm(t) { alarm = t; }, async deleteAlarm() { alarm = null; },
        };
        const state = { storage, getWebSockets: () => sockets, acceptWebSocket: (ws) => sockets.push(ws) };
        objects.set(name, { storage, sockets, room: new TeamRound(state, env), line: Promise.resolve() });
      }
      const o = objects.get(name);
      return { fetch(url, init) { const run = o.line.then(() => o.room.fetch(url instanceof Request ? url : new Request(url, init))); o.line = run.catch(() => {}); return run; } };
    },
  };
}

const page = (heard) => ({ send: (m) => heard.push(m) });
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.58", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const form = (env, path, body) => call(env, path, { method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const jsonPost = (env, path, body) => call(env, path, { method: "POST", body: JSON.stringify(body), headers: { "content-type": "application/json" } });
const next = new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth() + 1, 1));
const month = next.toISOString().slice(0, 7);
const days = new Date(Date.UTC(next.getUTCFullYear(), next.getUTCMonth() + 1, 0)).getUTCDate();
const row = (l) => Array.from({ length: days }, (_, i) => (i === 6 ? l : "O"));
const askRound = (env, rules = {}) => call(env, "/api/ask", { method: "POST", body: JSON.stringify({
  team: "6병동", month: "2026-12", members: ["가", "나", "다"], shifts: [{ id: 1, label: "D", color: "#2f7d5b" }],
  rules: { weekdayCap: 1, weekendCap: 3, kinds: ["OFF", "LEAVE", "SHIFT"], ...rules },
}) });
const swapRound = (env) => call(env, "/api/swap", { method: "POST", body: JSON.stringify({ team: "6병동", month, people: ["가", "나", "다"],
  labels: [row("D"), row("N"), row("O")], ok: { 0: { 7: [1, 2] }, 1: { 7: [0] }, 2: { 7: [0] } } }) });

const b64 = (bytes) => Buffer.from(bytes).toString("base64url");
async function vapid() {
  const k = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign"]);
  const jwk = await crypto.subtle.exportKey("jwk", k.privateKey);
  return { VAPID_PUBLIC_KEY: b64(new Uint8Array(await crypto.subtle.exportKey("raw", k.publicKey))), VAPID_PRIVATE_KEY: jwk.d, VAPID_SUBJECT: "mailto:team@bamnat.invalid" };
}
async function browser(n) {
  const k = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  return { endpoint: `https://push.example/send/${n}`, keys: { p256dh: b64(new Uint8Array(await crypto.subtle.exportKey("raw", k.publicKey))), auth: b64(crypto.getRandomValues(new Uint8Array(16))) } };
}
/** Pushes the room sends, by browser; the push service answers [status]. */
function pushService(t, status = 201) {
  const sent = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    if (String(url).startsWith("https://push.example/")) { sent.push({ url: String(url), headers: init.headers }); return new Response(null, { status }); }
    return real(url, init);
  };
  t.after(() => { globalThis.fetch = real; });
  return sent;
}

test("[RVQ-57] the share server's push.js is the company server's, line for line", () => {
  const mine = readFileSync(new URL("../src/push.js", import.meta.url), "utf8").split("\n").slice(1).join("\n");
  assert.equal(mine, readFileSync(new URL("../../company-worker/src/push.js", import.meta.url), "utf8"));
});

test("[RVQ-57] I22c-02 실시간: a room round's page runs the one script and its open pages hear every change; a KV round's page has none", async () => {
  readsForget();
  const env = { SHARES: kv() };
  env.TEAM_ROUND = rooms(env);
  const { id, token, members } = await (await askRound(env)).json();
  const [a, b] = members.map((m) => m.key);
  const res = await call(env, `/a/${a}`);
  const csp = res.headers.get("content-security-policy");
  assert.match(csp, /script-src 'self'; connect-src 'self'/);
  const body = await res.text();
  assert.match(body, /<script src="\/team-live\.js" defer><\/script>/);
  assert.match(body, new RegExp(`data-live="/a/${a}/live"`));
  assert.match(body, /class="live-bar panel" hidden/);
  assert.doesNotMatch(body, /알림 받기/, "no 알림 before 처리방침 3.15 (flag off)");
  // The page's WebSocket asks for an upgrade; without one it is refused, and a KV round has no room to join.
  assert.equal((await call(env, `/a/${a}/live`)).status, 426);
  const heard = [];
  const room = env.TEAM_ROUND.objects.get(`ask:${id}`);
  room.sockets.push(page(heard));
  await form(env, `/a/${b}/pick`, "d=2026-12-07&k=OFF");
  assert.deepEqual(heard, ["changed"], "another member's pick reaches the open page");
  await call(env, `/api/ask/${id}`, { method: "PUT", body: JSON.stringify({ closed: true }), headers: { "x-delete-token": token } });
  assert.deepEqual(heard, ["changed", "changed"], "the leader's close too");
  // Reading the page again in place (x-live) does not count as a new open.
  assert.equal((await call(env, `/a/${a}`, { headers: { "x-live": "1" } })).status, 200);
  await call(env, `/api/ask/${id}`, { method: "DELETE", headers: { "x-delete-token": token } });
  assert.equal(heard.at(-1), "gone", "deleting the round tells the page to read again (it is gone)");
  // Without the binding: as before, no script and the old rules.
  const plain = { SHARES: kv() };
  const old = await (await askRound(plain)).json();
  const oldPage = await call(plain, `/a/${old.members[0].key}`);
  assert.doesNotMatch(oldPage.headers.get("content-security-policy"), /script-src/);
  assert.doesNotMatch(await oldPage.text(), /team-live\.js/);
  assert.equal((await call(plain, `/a/${old.members[0].key}/live`, { headers: { upgrade: "websocket" } })).status, 404);
  // The two files.
  const js = await call(env, "/team-live.js");
  assert.match(js.headers.get("content-type"), /javascript/);
  assert.equal(await js.text(), TEAM_LIVE_JS);
  const sw = await call(env, "/team-sw.js");
  assert.equal(sw.headers.get("service-worker-allowed"), "/");
  assert.equal(await sw.text(), TEAM_SW_JS);
  assert.doesNotThrow(() => new Function(TEAM_LIVE_JS));
  assert.doesNotThrow(() => new Function(TEAM_SW_JS));
});

test("[RVQ-57] I22c-02 웹 알림: off until team.push and the VAPID keys; then only the asked, the asker and the decided hear, encrypted", async (t) => {
  readsForget();
  const env = { SHARES: kv(), ...(await vapid()), SHARE_ADMIN_TOKEN: "share-admin-token-for-tests-58" };
  env.TEAM_ROUND = rooms(env);
  const sent = pushService(t);
  const { id, token, people } = await (await swapRound(env)).json();
  const [a, b, c] = people.map((p) => p.key);
  // Flag off: no button, and the server takes no address.
  assert.doesNotMatch(await (await call(env, `/w/${b}`)).text(), /알림 받기/);
  assert.equal((await jsonPost(env, `/w/${b}/push`, { sub: await browser(1) })).status, 403);
  // The company server turns it on (처리방침 3.15 in force); 근무표 링크 새로 만들기 stays as it was.
  const admin = { authorization: `Bearer ${env.SHARE_ADMIN_TOKEN}`, "content-type": "application/json" };
  await call(env, "/api/admin/flags", { method: "PUT", body: JSON.stringify({ createOff: true }), headers: admin });
  const on = await (await call(env, "/api/admin/flags", { method: "PUT", body: JSON.stringify({ teamPush: true }), headers: admin })).json();
  assert.deepEqual(on, { createOff: true, teamPush: true });
  const pageB = await (await call(env, `/w/${b}`)).text();
  assert.match(pageB, /data-push="[A-Za-z0-9_-]{80,}"/);
  assert.match(pageB, /<button type="button" class="get line" data-on="">알림 받기<\/button>/);
  // Bad addresses are refused; a good one is kept in the room, not on KV.
  assert.equal((await jsonPost(env, `/w/${b}/push`, { sub: { endpoint: "http://push.example/x", keys: { p256dh: "a", auth: "b" } } })).status, 400);
  assert.equal((await jsonPost(env, `/w/${b}/push`, { sub: await browser("b") })).status, 200);
  assert.equal((await jsonPost(env, `/w/${a}/push`, { sub: await browser("a") })).status, 200);
  assert.match(await (await call(env, `/w/${b}`)).text(), /data-on="1">알림 끄기/);
  const room = env.TEAM_ROUND.objects.get(`swap:${id}`);
  assert.ok(room.storage.data.has("p:1") && room.storage.data.has("p:0"));
  assert.ok(![...env.SHARES.map.values()].some((v) => String(v).includes("push.example")), "no address on KV");
  // 가 asks 나: only 나 hears, encrypted and signed.
  await form(env, `/w/${a}/ask`, "d=7&q=1");
  assert.deepEqual(sent.map((s) => s.url), ["https://push.example/send/b"]);
  assert.equal(sent[0].headers["content-encoding"], "aes128gcm");
  assert.match(sent[0].headers.authorization, /^vapid t=.+, k=/);
  // 나 answers: 가 hears. 다 (no 알림) hears nothing ever.
  await form(env, `/w/${b}/reply`, "from=0&d=7&a=yes");
  assert.equal(sent.at(-1).url, "https://push.example/send/a");
  // The leader decides: the asker hears.
  await call(env, `/api/swap/${id}`, { method: "PUT", body: JSON.stringify({ decide: [{ p: 0, d: 7, state: "done" }] }), headers: { "x-delete-token": token } });
  assert.equal(sent.length, 3);
  assert.equal(sent.at(-1).url, "https://push.example/send/a");
  // The leader closes: everyone with 알림 hears once.
  await call(env, `/api/swap/${id}`, { method: "PUT", body: JSON.stringify({ closed: true }), headers: { "x-delete-token": token } });
  assert.deepEqual(sent.slice(3).map((s) => s.url).sort(), ["https://push.example/send/a", "https://push.example/send/b"]);
  assert.ok(!sent.some((s) => s.url.endsWith("/c")));
  // 알림 끄기 lets the address go.
  assert.equal((await jsonPost(env, `/w/${b}/push`, { off: true })).status, 200);
  assert.ok(!room.storage.data.has("p:1"));
  void c;
});

test("[RVQ-57] I22c-02 마감 하루 전: once, to those with 알림 who have not asked; an address the push service forgot is let go", async (t) => {
  readsForget();
  const env = { SHARES: kv(), ...(await vapid()) };
  env.TEAM_ROUND = rooms(env);
  env.SHARES.map.set("flag:team_push", "1");
  const closesAt = Date.now() + 30 * 3600 * 1000;
  const { id, members } = await (await askRound(env, { closesAt })).json();
  const [a, b, c] = members.map((m) => m.key);
  const sent = pushService(t, 201);
  for (const [k, n] of [[a, "a"], [b, "b"], [c, "c"]]) assert.equal((await jsonPost(env, `/a/${k}/push`, { sub: await browser(n) })).status, 200);
  await form(env, `/a/${a}/pick`, "d=2026-12-07&k=OFF");
  const room = env.TEAM_ROUND.objects.get(`ask:${id}`);
  assert.equal(room.storage.alarm(), closesAt - 24 * 3600 * 1000, "the room wakes a day before 마감");
  t.mock.timers.enable({ apis: ["Date"], now: closesAt - 23 * 3600 * 1000 });
  globalThis.fetch = ((inner) => async (url, init) => (String(url).endsWith("/c") ? new Response(null, { status: 410 }) : inner(url, init)))(globalThis.fetch);
  await room.room.alarm();
  assert.deepEqual(sent.map((s) => s.url), ["https://push.example/send/b"], "가 has asked; 다's address is gone (410)");
  assert.ok(!room.storage.data.has("p:2"), "the forgotten address is let go");
  assert.ok(room.storage.alarm() > closesAt, "next: the round's end");
  await room.room.alarm();
  assert.equal(sent.length, 1, "once for that 마감");
  t.mock.timers.setTime(Date.parse(JSON.parse(env.SHARES.map.get(`ask:${id}`)).expiresAt) + 61 * 1000);
  await room.room.alarm();
  assert.equal(room.storage.data.size, 0, "the round's end empties the room");
});
