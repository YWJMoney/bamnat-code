// 1.38 [P3-L-15] 받기: 봤어요 (a link opened since its last change) and 병동 전체 보기 (the whole month's table, when the leader turns it on).
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.42", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const schedule = (day) => JSON.stringify({ v: 1, title: "6병동 다", ttlDays: 30, types: [{ id: 1, name: "데이", short: "D", color: "#3B82F6" }], days: [{ date: day, type: 1 }] });

test("봤어요: once asked, the link keeps when it was opened after each change", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const made = await (await call(env, "/api/share", { method: "POST", body: schedule("2026-12-01"), headers: { "content-type": "application/json" } })).json();
  const ask = () => call(env, `/api/share/${made.id}/seen`, { headers: { "x-delete-token": made.deleteToken } }).then((r) => r.json());
  assert.equal((await call(env, `/api/share/${made.id}/seen`)).status, 403);
  assert.equal((await ask()).seenAt, null);
  assert.equal((await call(env, `/s/${made.id}`)).status, 200);
  const first = await ask();
  assert.ok(first.seenAt);
  const writes = env.SHARES.map.size;
  await call(env, `/s/${made.id}`);
  assert.equal(env.SHARES.map.size, writes);
  // 다시 확정: not seen until opened again, here by the app.
  await new Promise((r) => setTimeout(r, 5));
  assert.equal((await call(env, `/api/share/${made.id}`, { method: "PUT", body: schedule("2026-12-02"), headers: { "content-type": "application/json", "x-delete-token": made.deleteToken } })).status, 200);
  assert.equal((await ask()).seenAt, null);
  assert.equal((await call(env, `/api/share/${made.id}`)).status, 200);
  assert.ok((await ask()).seenAt);
});

test("병동 전체 보기: the table and each day's count, changed in place and taken down", async (t) => {
  // [RVD-01] The board's month (2026-12) is a near month only on a clock near it; from 2027-03 the real clock refused it.
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  readsForget();
  const env = { SHARES: kv() };
  const days = 31;
  const row = (l) => Array.from({ length: days }, (_, i) => (i % 3 === 0 ? l : ""));
  const body = { team: "6병동", month: "2026-12", names: ["가", "나"], labels: [row("D"), row("N")], colors: { D: "#3B82F6", N: "#1E293B" } };
  assert.equal((await call(env, "/api/board", { method: "POST", body: JSON.stringify({ ...body, labels: [row("D")] }) })).status, 400);
  const made = await call(env, "/api/board", { method: "POST", body: JSON.stringify(body) });
  assert.equal(made.status, 201);
  const { id, token, url } = await made.json();
  assert.equal(url, `https://share.example/b/${id}`);
  const page = await (await call(env, `/b/${id}`)).text();
  assert.match(page, /6병동 12월 근무표/);
  assert.match(page, /<th class="who">가<\/th>/);
  assert.match(page, /날마다/);
  assert.doesNotMatch(page, /<script/i);
  assert.equal((await call(env, `/api/board/${id}`, { method: "PUT", body: JSON.stringify({ ...body, names: ["가", "라"] }) })).status, 403);
  assert.equal((await call(env, `/api/board/${id}`, { method: "PUT", body: JSON.stringify({ ...body, names: ["가", "라"] }), headers: { "x-delete-token": token } })).status, 204);
  assert.match(await (await call(env, `/b/${id}`)).text(), /<th class="who">라<\/th>/);
  assert.equal((await call(env, `/api/board/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  assert.equal((await call(env, `/b/${id}`)).status, 404);
});
