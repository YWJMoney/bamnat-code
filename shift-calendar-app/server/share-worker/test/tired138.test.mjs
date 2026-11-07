// 1.38 [P3-L-18] 피곤해요 팀 합계: a number per day, no names, hidden under LIMITS.tiredMin.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, LIMITS } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.42", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const schedule = (day) => JSON.stringify({ v: 1, title: "6병동", ttlDays: 30, types: [{ id: 1, name: "데이", short: "D", color: "#3B82F6" }], days: [{ date: day, type: 1 }] });

test("피곤해요: tied links add one a day, the leader sees counts from the minimum up and no names", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const links = [];
  for (let i = 0; i < 4; i++) links.push(await (await call(env, "/api/share", { method: "POST", body: schedule("2026-12-01"), headers: { "content-type": "application/json" } })).json());
  // Not tied yet: nothing to say, and the app is not told to offer it.
  assert.equal((await call(env, `/api/share/${links[0].id}/tired`, { method: "POST" })).status, 404);
  assert.equal((await (await call(env, `/api/share/${links[0].id}`)).json()).tired, undefined);
  const box = await (await call(env, "/api/tired", { method: "POST", body: JSON.stringify({ team: "6병동" }) })).json();
  assert.ok(box.id && box.token);
  assert.equal((await call(env, `/api/share/${links[0].id}/tired`, { method: "PUT", body: JSON.stringify({ box: box.id }) })).status, 403);
  assert.equal((await call(env, `/api/share/${links[0].id}/tired`, { method: "PUT", body: JSON.stringify({ box: "x".repeat(22) }), headers: { "x-delete-token": links[0].deleteToken } })).status, 400);
  for (const l of links) assert.equal((await call(env, `/api/share/${l.id}/tired`, { method: "PUT", body: JSON.stringify({ box: box.id }), headers: { "x-delete-token": l.deleteToken } })).status, 204);
  assert.equal((await (await call(env, `/api/share/${links[0].id}`)).json()).tired, true);
  const read = () => call(env, `/api/tired/${box.id}`, { headers: { "x-delete-token": box.token } }).then((r) => r.json());
  assert.equal((await call(env, `/api/tired/${box.id}`)).status, 403);
  // Two say it (one twice): still under the minimum, so no number.
  for (const l of [links[0], links[0], links[1]]) assert.equal((await call(env, `/api/share/${l.id}/tired`, { method: "POST" })).status, 204);
  let got = await read();
  assert.equal(got.min, LIMITS.tiredMin);
  assert.equal(got.days.length, 7);
  assert.equal(got.days[0].n, null);
  await call(env, `/api/share/${links[2].id}/tired`, { method: "POST" });
  got = await read();
  assert.equal(got.days[0].n, 3);
  assert.doesNotMatch(JSON.stringify(got), new RegExp(links.map((l) => l.id).join("|")));
  // Untied: the link no longer counts.
  assert.equal((await call(env, `/api/share/${links[3].id}/tired`, { method: "PUT", body: JSON.stringify({ box: null }), headers: { "x-delete-token": links[3].deleteToken } })).status, 204);
  assert.equal((await call(env, `/api/share/${links[3].id}/tired`, { method: "POST" })).status, 404);
  // A schedule update keeps the tie.
  await call(env, `/api/share/${links[0].id}`, { method: "PUT", body: schedule("2026-12-02"), headers: { "content-type": "application/json", "x-delete-token": links[0].deleteToken } });
  assert.equal((await (await call(env, `/api/share/${links[0].id}`)).json()).tired, true);
  assert.equal((await call(env, `/api/tired/${box.id}`, { method: "DELETE", headers: { "x-delete-token": box.token } })).status, 204);
  assert.equal((await call(env, `/api/share/${links[1].id}/tired`, { method: "POST" })).status, 404);
});
