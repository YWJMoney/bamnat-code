// 1.38 [P3-X-02] 바꾸기 요청: a member asks from their link, the other says yes, the leader approves; only listed trades go.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.41", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const press = (env, key, what, body) => call(env, `/w/${key}/${what}`, { method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const next = new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth() + 1, 1));
const month = next.toISOString().slice(0, 7);
const days = new Date(Date.UTC(next.getUTCFullYear(), next.getUTCMonth() + 1, 0)).getUTCDate();
const row = (l) => Array.from({ length: days }, (_, i) => (i === 6 ? l : "O"));

async function open(env) {
  const made = await call(env, "/api/swap", { method: "POST", body: JSON.stringify({ team: "6병동", month, people: ["가", "나", "다"],
    labels: [row("D"), row("N"), row("O")], ok: { 0: { 7: [1, 2] }, 1: { 7: [0] }, 2: { 7: [0] } } }) });
  assert.equal(made.status, 201);
  return made.json();
}

test("ask, yes, approve: each page follows and the leader reads it all", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id, token, people } = await open(env);
  const [a, b, c] = people.map((p) => p.key);
  const first = await (await call(env, `/w/${a}`)).text();
  assert.match(first, /근무 바꾸기/);
  assert.match(first, /나 \(N\)/);
  assert.doesNotMatch(first, /<script/i);
  assert.match(await (await press(env, a, "ask", "d=7&q=1")).text(), /나님께 요청했어요/);
  assert.equal((await press(env, a, "ask", "d=7&q=2")).status, 409, "one open ask a day");
  assert.equal((await press(env, c, "ask", "d=7&q=1")).status, 400, "not a listed trade");
  const toB = await (await call(env, `/w/${b}`)).text();
  assert.match(toB, /받은 요청/);
  assert.match(toB, /가<\/b>님/);
  assert.match(await (await press(env, b, "reply", "from=0&d=7&a=yes")).text(), /좋다고 전했어요/);
  assert.equal((await press(env, c, "reply", "from=0&d=7&a=yes")).status, 409, "only the one asked answers");
  const read = await (await call(env, `/api/swap/${id}`, { headers: { "x-delete-token": token } })).json();
  assert.deepEqual(read.asks.map((x) => [x.p, x.q, x.d, x.state]), [[0, 1, 7, "yes"]]);
  assert.deepEqual(read.seen.sort(), [0, 1, 2]);
  const labels = [row("N"), row("D"), row("O")];
  assert.equal((await call(env, `/api/swap/${id}`, { method: "PUT", body: JSON.stringify({ decide: [{ p: 0, d: 7, state: "done" }], labels, ok: {} }), headers: { "x-delete-token": token } })).status, 204);
  const after = await (await call(env, `/w/${a}`)).text();
  assert.match(after, /리더가 승인했어요/);
  assert.match(after, /바꿀 수 있는 날이 없어요/);
  const stored = [...env.SHARES.map.values()].join("");
  assert.doesNotMatch(stored, /203\.0\.113\.41/);
  assert.doesNotMatch(stored, new RegExp(token));
  assert.equal((await call(env, `/api/swap/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  assert.equal((await call(env, `/w/${a}`)).status, 404);
  assert.equal([...env.SHARES.map.keys()].filter((k) => k.startsWith("swap")).length, 0);
});

test("cancel, closed, the key and a bad shape", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id, token, people } = await open(env);
  const [a, b] = people.map((p) => p.key);
  await press(env, a, "ask", "d=7&q=1");
  assert.match(await (await press(env, a, "cancel", "d=7")).text(), /요청을 거뒀어요/);
  assert.equal((await press(env, b, "reply", "from=0&d=7&a=yes")).status, 409);
  assert.equal((await call(env, `/api/swap/${id}`)).status, 403);
  await call(env, `/api/swap/${id}`, { method: "PUT", body: JSON.stringify({ closed: true }), headers: { "x-delete-token": token } });
  assert.equal((await press(env, a, "ask", "d=7&q=1")).status, 409);
  const bad = await call(env, "/api/swap", { method: "POST", body: JSON.stringify({ team: "가", month, people: ["가", "나"], labels: [row("D")], ok: {} }) });
  assert.equal(bad.status, 400);
  const self = await call(env, "/api/swap", { method: "POST", body: JSON.stringify({ team: "가", month, people: ["가", "나"], labels: [row("D"), row("O")], ok: { 0: { 7: [0] } } }) });
  assert.equal(self.status, 400, "nobody trades with themselves");
});
