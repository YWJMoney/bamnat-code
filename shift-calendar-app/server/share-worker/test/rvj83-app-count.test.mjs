// 2.0.0 [RVJ-83] SPREAD-38 (처리방침 3.14 14번): a member's app that follows a watched link by itself leaves one mark (a hash of a word
// only that phone keeps); the leader reads how many of the team's links are read so, one number for them all and only from 3 up.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, LIMITS } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
let ip = 10;
const call = (env, path, init = {}) =>
  worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": `203.0.113.${ip}`, ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const post = (o) => ({ method: "POST", body: JSON.stringify(o), headers: { "content-type": "application/json" } });
const schedule = (name) => ({ v: 1, title: name, team: true, ttlDays: 30, types: [{ id: 1, name: "데이", short: "D", color: "#3B82F6", time: "07:00-15:00" }],
  days: [{ date: "2026-12-01", type: 1 }] });
const ON = { POLICY_314_FROM: "2026-01-01" };
const mark = (i) => `phone-mark-${String(i).padStart(8, "0")}`;

async function team(env, n) {
  const links = [];
  for (let i = 0; i < n; i++) {
    ip += 1;
    const m = await (await call(env, "/api/share", post(schedule(`팀원${i}`)))).json();
    // The leader's 봤어요 read watches the link (as the app does after 확정).
    await call(env, `/api/share/${m.id}/seen`, { headers: { "x-delete-token": m.deleteToken } });
    links.push({ id: m.id, token: m.deleteToken });
  }
  return links;
}
const count = async (env, links) => (await call(env, "/api/apps", post({ links }))).json();
const on = (env, l, i) => call(env, `/api/share/${l.id}/app`, post({ mark: mark(i) }));

test("[RVJ-83] the leader sees one number for the team, only from 3, never which link", async () => {
  readsForget();
  const env = { SHARES: kv(), ...ON };
  const links = await team(env, 6);
  assert.equal((await on(env, links[0], 0)).status, 204);
  assert.equal((await on(env, links[1], 1)).status, 204);
  assert.deepEqual(await count(env, links), { n: null, min: LIMITS.appMin }, "two is under the line");
  await on(env, links[2], 2);
  assert.deepEqual(await count(env, links), { n: 3, min: 3 });
  // Nothing per link: the seen read and the link itself say nothing of the mark.
  const seen = await (await call(env, `/api/share/${links[0].id}/seen`, { headers: { "x-delete-token": links[0].token } })).json();
  assert.deepEqual(Object.keys(seen).sort(), ["okAt", "seenAt", "updatedAt"]);
  assert.doesNotMatch(JSON.stringify(await (await call(env, `/api/share/${links[0].id}`)).json()), /phone-mark|"app"/);
  // What is kept is a hash, no word of the phone's.
  assert.doesNotMatch(env.SHARES.map.get(`app:${links[0].id}`), /phone-mark/);
  // Asking again the same way writes nothing.
  const before = env.SHARES.map.get(`app:${links[0].id}`);
  await count(env, links);
  assert.equal(env.SHARES.map.get(`app:${links[0].id}`), before);
});

test("[RVJ-83] a different set of links gets no number for a week: taking one out cannot tell whose mark it was", async () => {
  readsForget();
  const env = { SHARES: kv(), ...ON };
  const links = await team(env, 5);
  for (let i = 0; i < 4; i++) await on(env, links[i], i);
  assert.equal((await count(env, links)).n, 4);
  const less = links.filter((_, i) => i !== 3);
  assert.deepEqual(await count(env, less), { n: null, min: 3, rest: true });
  assert.deepEqual(await count(env, links.slice(0, 3)), { n: null, min: 3, rest: true });
  // The team as it was still reads.
  assert.equal((await count(env, links)).n, 4);
  // A week after the marks were last counted together, the new team reads (and then rests the other way).
  for (const l of links) {
    const k = `app:${l.id}`;
    const m = env.SHARES.map.get(k);
    if (m) env.SHARES.map.set(k, JSON.stringify({ ...JSON.parse(m), setAt: new Date(Date.now() - 8 * 86400 * 1000).toISOString() }));
  }
  assert.equal((await count(env, less)).n, 3);
  assert.equal((await count(env, links)).rest, true);
  // A link asked without its own token is left out (it neither counts nor changes the set).
  assert.equal((await count(env, [...less, { id: links[3].id, token: "x".repeat(32) }])).n, 3);
});

test("[RVJ-83] the phone takes its mark back; the link takes it along; another word cannot", async () => {
  readsForget();
  const env = { SHARES: kv(), ...ON };
  const links = await team(env, 4);
  for (let i = 0; i < 4; i++) await on(env, links[i], i);
  assert.equal((await count(env, links)).n, 4);
  assert.equal((await call(env, `/api/share/${links[0].id}/app`, post({ mark: mark(9), off: true }))).status, 204);
  assert.equal((await count(env, links)).n, 4, "only the phone's own word takes it back");
  await call(env, `/api/share/${links[0].id}/app`, post({ mark: mark(0), off: true }));
  assert.equal((await count(env, links)).n, 3);
  assert.equal(env.SHARES.map.has(`app:${links[0].id}`), false);
  await call(env, `/api/share/${links[1].id}`, { method: "DELETE", headers: { "x-delete-token": links[1].token } });
  assert.equal(env.SHARES.map.has(`app:${links[1].id}`), false, "모두 지우기 / 링크 끊기 takes the mark along");
  assert.equal((await count(env, links)).n, null);
  // Taking back works any day, also once the leader no longer asks.
  const off = { ...env, POLICY_314_FROM: "" };
  assert.equal((await call(off, `/api/share/${links[2].id}/app`, post({ mark: mark(2), off: true }))).status, 204);
  assert.equal(env.SHARES.map.has(`app:${links[2].id}`), false);
  // A bad word is refused before anything is read or kept.
  assert.equal((await call(env, `/api/share/${links[2].id}/app`, post({ mark: "짧아" }))).status, 400);
});

test("[RVJ-83] before 처리방침 3.14, or on a link the leader does not watch, nothing is kept and no number goes", async () => {
  readsForget();
  const off = { SHARES: kv() };
  const links = await team(off, 3);
  for (let i = 0; i < 3; i++) assert.equal((await on(off, links[i], i)).status, 404);
  assert.equal([...off.SHARES.map.keys()].some((k) => k.startsWith("app:")), false);
  assert.deepEqual(await count(off, links), { n: null, min: 3, off: true });
  // A link no leader watches (a person's own shared calendar).
  const env = { SHARES: kv(), ...ON };
  const own = await (await call(env, "/api/share", post(schedule("민지")))).json();
  assert.equal((await call(env, `/api/share/${own.id}/app`, post({ mark: mark(1) }))).status, 404);
});
