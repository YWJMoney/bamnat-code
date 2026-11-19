// 2.0.0 [RVQ-57] 검토 I22c-02: 팀 방. With the TeamRound Durable Object (binding TEAM_ROUND), a new 신청·바꾸기 round keeps its members'
// parts in one room and each press runs there alone: the last seat goes once, a reply and a cancel do not both land, and the room is
// emptied when the round goes. Without the binding (and for rounds made before) everything stays on KV as it was. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, TeamRound } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}

/** A Durable Object namespace like Cloudflare's: one object per name, its requests one at a time, storage that waits a turn. */
function rooms() {
  const objects = new Map();
  const tick = () => new Promise((r) => setTimeout(r, 1));
  const storageOf = () => {
    const data = new Map();
    let alarm = null;
    return {
      data, alarm: () => alarm,
      async list({ prefix }) { await tick(); return new Map([...data].filter(([k]) => k.startsWith(prefix)).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, structuredClone(v)])); },
      async put(entries) { await tick(); for (const [k, v] of Object.entries(entries)) data.set(k, structuredClone(v)); },
      async deleteAll() { data.clear(); },
      async getAlarm() { return alarm; }, async setAlarm(t) { alarm = t; }, async deleteAlarm() { alarm = null; },
    };
  };
  return {
    objects,
    idFromName: (name) => name,
    get(name) {
      if (!objects.has(name)) {
        const storage = storageOf();
        objects.set(name, { storage, room: new TeamRound({ storage }), line: Promise.resolve() });
      }
      const o = objects.get(name);
      return {
        fetch(url, init) {
          const run = o.line.then(() => o.room.fetch(new Request(url, init)));
          o.line = run.catch(() => {});
          return run;
        },
      };
    },
  };
}

const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.57", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const form = (env, path, body) => call(env, path, { method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const askRound = (env) => call(env, "/api/ask", { method: "POST", body: JSON.stringify({
  team: "6병동", month: "2026-12", members: ["가", "나", "다"], shifts: [{ id: 1, label: "D", color: "#2f7d5b" }],
  rules: { weekdayCap: 1, weekendCap: 3, kinds: ["OFF", "LEAVE", "SHIFT"] },
}) });
const next = new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth() + 1, 1));
const month = next.toISOString().slice(0, 7);
const days = new Date(Date.UTC(next.getUTCFullYear(), next.getUTCMonth() + 1, 0)).getUTCDate();
const row = (l) => Array.from({ length: days }, (_, i) => (i === 6 ? l : "O"));
const swapRound = (env) => call(env, "/api/swap", { method: "POST", body: JSON.stringify({ team: "6병동", month, people: ["가", "나", "다"],
  labels: [row("D"), row("N"), row("O")], ok: { 0: { 7: [1, 2] }, 1: { 7: [0] }, 2: { 7: [0] } } }) });

test("[RVQ-57] I22c-02 신청: the last weekday seat goes once when two members press at the same moment; KV keys stay unwritten", async () => {
  readsForget();
  const env = { SHARES: kv(), TEAM_ROUND: rooms() };
  const { id, token, members } = await (await askRound(env)).json();
  const [a, b, c] = members.map((m) => m.key);
  assert.equal(JSON.parse(env.SHARES.map.get(`ask:${id}`)).room, true, "a new round is a room round");
  const both = await Promise.all([form(env, `/a/${a}/pick`, "d=2026-12-07&k=OFF"), form(env, `/a/${b}/pick`, "d=2026-12-07&k=OFF")]);
  assert.deepEqual(both.map((r) => r.status).sort(), [200, 409], "one gets the seat, the other is told it is full");
  assert.match(await both.find((r) => r.status === 409).text(), /이 날은 자리가 다 찼어요/);
  // A work wish takes no seat; a note and a removal go too.
  assert.equal((await form(env, `/a/${c}/pick`, "d=2026-12-07&k=SHIFT&s=1")).status, 200);
  assert.match(await (await form(env, `/a/${c}/note`, "note=수요일 오전 병원")).text(), /한 줄을 남겼어요/);
  const read = await (await call(env, `/api/ask/${id}`, { headers: { "x-delete-token": token } })).json();
  assert.equal(read.wishes.filter((w) => w.d === "2026-12-07" && w.k === "OFF").length, 1);
  assert.deepEqual(read.notes.map((n) => n.note), ["수요일 오전 병원"]);
  assert.ok(![...env.SHARES.map.keys()].some((k) => k.startsWith(`askw:${id}`) || k.startsWith(`askx:${id}`)), "no member or summary key on KV");
  // Every page reads the room (one call), and opening it marks it read there.
  const seen = await call(env, `/a/${b}`);
  assert.equal(seen.status, 200);
  assert.match(await seen.text(), /6병동 12월 근무 신청/);
  const room = env.TEAM_ROUND.objects.get(`ask:${id}`);
  assert.ok(room.storage.alarm() >= Date.parse(JSON.parse(env.SHARES.map.get(`ask:${id}`)).expiresAt), "the room forgets at the round's end");
  assert.ok(read.seen.length >= 2, "opening a page marks it read in the room");
  // The leader deletes the round: the room is emptied.
  assert.equal((await call(env, `/api/ask/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  assert.equal(room.storage.data.size, 0);
  assert.equal(room.storage.alarm(), null);
});

test("[RVQ-57] I22c-02 바꾸기: ask, reply and cancel run one at a time in the room; the leader decides there; deleting empties it", async () => {
  readsForget();
  const env = { SHARES: kv(), TEAM_ROUND: rooms() };
  const { id, token, people } = await (await swapRound(env)).json();
  const [a, b] = people.map((p) => p.key);
  const press = (key, what, body) => form(env, `/w/${key}/${what}`, body);
  assert.match(await (await press(a, "ask", "d=7&q=1")).text(), /나님께 요청했어요/);
  assert.equal((await press(a, "ask", "d=7&q=2")).status, 409, "one open ask a day");
  // The asked one says yes while the asker takes it back: both run, one after the other, and the end agrees with what each was told.
  const [yes, back] = await Promise.all([press(b, "reply", "from=0&d=7&a=yes"), press(a, "cancel", "d=7")]);
  const read = await (await call(env, `/api/swap/${id}`, { headers: { "x-delete-token": token } })).json();
  const state = read.asks.find((x) => x.p === 0 && x.d === 7).state;
  if (yes.status === 200 && back.status === 200) assert.equal(state, "cancel", "yes then cancel: taken back");
  else if (yes.status === 409) assert.equal(state, "cancel", "cancel first: the reply finds it gone");
  else assert.fail(`yes ${yes.status} cancel ${back.status}`);
  // A new ask, a yes, and the leader's approval in the room.
  assert.equal((await press(a, "ask", "d=7&q=1")).status, 200);
  assert.equal((await press(b, "reply", "from=0&d=7&a=yes")).status, 200);
  assert.equal((await call(env, `/api/swap/${id}`, { method: "PUT", body: JSON.stringify({ decide: [{ p: 0, d: 7, state: "done" }] }), headers: { "x-delete-token": token } })).status, 204);
  const after = await (await call(env, `/api/swap/${id}`, { headers: { "x-delete-token": token } })).json();
  assert.ok(after.asks.some((x) => x.p === 0 && x.d === 7 && x.state === "done"));
  assert.match(await (await call(env, `/w/${a}`)).text(), /리더가 승인했어요/);
  assert.ok(![...env.SHARES.map.keys()].some((k) => k.startsWith(`swapr:${id}`) || k.startsWith(`swapx:${id}`)), "no member or summary key on KV");
  const room = env.TEAM_ROUND.objects.get(`swap:${id}`);
  assert.ok(room.storage.data.size > 0);
  assert.equal((await call(env, `/api/swap/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  assert.equal(room.storage.data.size, 0, "deleting the round empties its room");
});

test("[RVQ-57] I22c-02 without the binding a new round stays on KV (and a room round keeps working only while the binding is there)", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id, members } = await (await askRound(env)).json();
  assert.equal(JSON.parse(env.SHARES.map.get(`ask:${id}`)).room, undefined);
  assert.equal((await form(env, `/a/${members[0].key}/pick`, "d=2026-12-07&k=OFF")).status, 200);
  assert.ok(env.SHARES.map.has(`askw:${id}:0`), "the member's own key, as before");
});
