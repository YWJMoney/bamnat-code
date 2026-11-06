// 1.38 [P3-B-11] 출퇴근 링크: the staff member presses, the boss's app reads with its key; no location, no address kept.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.20", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const press = (env, id, k) => call(env, `/c/${id}/punch`, { method: "POST", body: `k=${k}`, headers: { "content-type": "application/x-www-form-urlencoded" } });

test("a clock link: press in, the boss reads it with the key, a double tap counts once, out after", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const made = await call(env, "/api/clock", { method: "POST", body: JSON.stringify({ name: "박마바", place: "가나다카페" }), headers: { "content-type": "application/json" } });
  assert.equal(made.status, 201);
  const { id, url, token } = await made.json();
  assert.equal(url, `https://share.example/c/${id}`);
  const pageNow = await call(env, `/c/${id}`);
  assert.equal(pageNow.status, 200);
  const text = await pageNow.text();
  assert.match(text, /박마바님 출퇴근/);
  assert.match(text, /위치·전화번호·기기 정보는 보내지 않아요/);
  assert.doesNotMatch(text, /<script/i);
  const one = await press(env, id, "in");
  assert.equal(one.status, 200);
  assert.match(await one.text(), /출근 \d\d:\d\d 기록했어요/);
  await press(env, id, "in");
  await press(env, id, "out");
  const read = await call(env, `/api/clock/${id}`, { headers: { "x-delete-token": token } });
  assert.equal(read.status, 200);
  const got = await read.json();
  assert.deepEqual(got.punches.map((p) => p.k), ["in", "out"], "the second 출근 within a minute is the same press");
  const stored = [...env.SHARES.map.values()].join("");
  assert.doesNotMatch(stored, /203\.0\.113\.20/, "no address is kept");
  assert.doesNotMatch(stored, new RegExp(token), "only the key's hash is kept");
});

test("only the key reads or cuts a clock link; a cut link says so", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id, token } = await (await call(env, "/api/clock", { method: "POST", body: JSON.stringify({ name: "가" }) })).json();
  assert.equal((await call(env, `/api/clock/${id}`)).status, 403);
  assert.equal((await call(env, `/api/clock/${id}`, { headers: { "x-delete-token": "x".repeat(32) } })).status, 403);
  assert.equal((await call(env, `/api/clock/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  const gone = await call(env, `/c/${id}`);
  assert.equal(gone.status, 404);
  assert.match(await gone.text(), /사장님이 끊었어요/);
  assert.equal((await press(env, id, "in")).status, 404);
});

test("a bad press or a too long name is refused", async () => {
  readsForget();
  const env = { SHARES: kv() };
  assert.equal((await call(env, "/api/clock", { method: "POST", body: JSON.stringify({ name: "가".repeat(21) }) })).status, 400);
  const { id } = await (await call(env, "/api/clock", { method: "POST", body: JSON.stringify({ name: "가" }) })).json();
  assert.equal((await press(env, id, "lunch")).status, 400);
});
