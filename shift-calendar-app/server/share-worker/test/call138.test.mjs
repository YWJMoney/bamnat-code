// 1.38 [P3-X-01] 결원 메우기: a yes/no ask to a few people, the leader reads the answers and picks; every page then says who.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.40", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const answer = (env, key, a) => call(env, `/v/${key}/answer`, { method: "POST", body: `a=${a}`, headers: { "content-type": "application/x-www-form-urlencoded" } });

test("asked people answer; the leader reads, picks one, and each page says who is coming", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const made = await call(env, "/api/call", { method: "POST", body: JSON.stringify({ team: "6병동", title: "12/7(월) 나이트 대신 들어갈 수 있나요?", detail: "22:00~07:00", people: ["다", "라"] }) });
  assert.equal(made.status, 201);
  const { id, token, people } = await made.json();
  const [x, y] = people.map((p) => p.key);
  const first = await (await call(env, `/v/${x}`)).text();
  assert.match(first, /나이트 대신 들어갈 수 있나요/);
  assert.match(first, /다님께/);
  assert.doesNotMatch(first, /<script/i);
  assert.match(await (await answer(env, x, "no")).text(), /어렵다고 전했어요/);
  assert.match(await (await answer(env, y, "yes")).text(), /들어갈 수 있다고 전했어요/);
  const read = await (await call(env, `/api/call/${id}`, { headers: { "x-delete-token": token } })).json();
  assert.deepEqual(read.answers.map((a) => [a.p, a.a]), [[0, "no"], [1, "yes"]]);
  assert.deepEqual(read.seen, [0, 1]);
  assert.equal((await call(env, `/api/call/${id}`, { method: "PUT", body: JSON.stringify({ taken: 1 }), headers: { "x-delete-token": token } })).status, 204);
  assert.match(await (await call(env, `/v/${y}`)).text(), /라님이 들어가기로 정해졌어요/);
  assert.match(await (await call(env, `/v/${x}`)).text(), /다른 분이 들어가기로 정해졌어요/);
  assert.equal((await answer(env, x, "yes")).status, 409, "after the pick nobody answers");
  const stored = [...env.SHARES.map.values()].join("");
  assert.doesNotMatch(stored, /203\.0\.113\.40/);
  assert.doesNotMatch(stored, new RegExp(token));
  assert.equal((await call(env, `/api/call/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  assert.equal((await call(env, `/v/${x}`)).status, 404);
  assert.equal([...env.SHARES.map.keys()].filter((k) => k.startsWith("call")).length, 0);
});

test("only the key reads a call; a bad call or answer is refused", async () => {
  readsForget();
  const env = { SHARES: kv() };
  assert.equal((await call(env, "/api/call", { method: "POST", body: JSON.stringify({ team: "가", title: "", people: ["다"] }) })).status, 400);
  const { id, people } = await (await call(env, "/api/call", { method: "POST", body: JSON.stringify({ team: "가", title: "대타", people: ["다"] }) })).json();
  assert.equal((await call(env, `/api/call/${id}`)).status, 403);
  assert.equal((await answer(env, people[0].key, "maybe")).status, 400);
});
