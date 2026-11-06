// 1.38 [P3-L-13] 근무 신청 받기: the ward's paper sheet ("평일 2명, 주말 3명") as a link per member, read by the leader's app.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, askCheck } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.30", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const form = (env, path, body) => call(env, path, { method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const round = (env, extra = {}) => call(env, "/api/ask", { method: "POST", body: JSON.stringify({
  team: "6병동", month: "2026-12", members: ["가", "나", "다"],
  shifts: [{ id: 1, label: "D", color: "#2f7d5b" }, { id: 3, label: "N", color: "#3b5bdb" }],
  rules: { weekdayCap: 2, weekendCap: 3, kinds: ["OFF", "LEAVE", "SHIFT"], holidays: ["2026-12-25"] }, ...extra,
}) });

test("members ask days; a weekday takes two, the third is told it is full; the leader reads them in order", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const made = await round(env);
  assert.equal(made.status, 201);
  const { id, token, members } = await made.json();
  assert.equal(members.length, 3);
  assert.match(members[0].url, /^https:\/\/share\.example\/a\/[A-Za-z0-9]{22}$/);
  const [a, b, c] = members.map((m) => m.key);
  const first = await call(env, `/a/${a}`);
  assert.equal(first.status, 200);
  const page = await first.text();
  assert.match(page, /6병동 12월 근무 신청/);
  assert.match(page, /평일 하루 2명, 주말·공휴일 하루 3명/);
  assert.doesNotMatch(page, /<script/i);
  assert.match(await (await form(env, `/a/${a}/pick`, "d=2026-12-07&k=OFF")).text(), /12\/07 오프 신청했어요/);
  assert.equal((await form(env, `/a/${b}/pick`, "d=2026-12-07&k=LEAVE")).status, 200);
  const third = await form(env, `/a/${c}/pick`, "d=2026-12-07&k=OFF");
  assert.equal(third.status, 409);
  assert.match(await third.text(), /이 날은 자리가 다 찼어요/);
  assert.equal((await form(env, `/a/${c}/pick`, "d=2026-12-07&k=SHIFT&s=3")).status, 200, "a work wish takes no seat");
  assert.equal((await form(env, `/a/${c}/pick`, "d=2026-12-25&k=OFF")).status, 200);
  assert.equal((await form(env, `/a/${c}/note`, "note=NN+%EB%92%A4+off+%EC%9B%90%ED%95%B4%EC%9A%94")).status, 200);
  const view = await (await call(env, `/a/${c}?d=2026-12-07`)).text();
  assert.match(view, /쉬는 신청 2\/2명 · 자리가 다 찼어요/);
  assert.doesNotMatch(view, /신청한 사람: 가/, "names stay with the leader unless shown");
  const read = await call(env, `/api/ask/${id}`, { headers: { "x-delete-token": token } });
  assert.equal(read.status, 200);
  const got = await read.json();
  assert.deepEqual(got.wishes.map((w) => [w.m, w.d, w.k]).sort(), [[0, "2026-12-07", "OFF"], [1, "2026-12-07", "LEAVE"], [2, "2026-12-07", "SHIFT"], [2, "2026-12-25", "OFF"]].sort());
  assert.equal(got.wishes.find((w) => w.k === "SHIFT").s, 3);
  assert.ok(got.wishes.every((w) => typeof w.at === "string"));
  assert.deepEqual(got.notes, [{ m: 2, note: "NN 뒤 off 원해요" }]);
  assert.deepEqual(got.seen.sort(), [0, 1, 2]);
  const stored = [...env.SHARES.map.values()].join("");
  assert.doesNotMatch(stored, /203\.0\.113\.30/, "no address is kept");
  assert.doesNotMatch(stored, new RegExp(token), "only the key's hash is kept");
});

test("only the key reads, closes or deletes a round; a closed round takes nothing; cancelling frees the seat", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const { id, token, members } = await (await round(env)).json();
  const [a, b] = members.map((m) => m.key);
  assert.equal((await call(env, `/api/ask/${id}`)).status, 403);
  await form(env, `/a/${a}/pick`, "d=2026-12-08&k=OFF");
  await form(env, `/a/${a}/pick`, "d=2026-12-08&k=none");
  const after = await (await call(env, `/api/ask/${id}`, { headers: { "x-delete-token": token } })).json();
  assert.equal(after.wishes.length, 0);
  assert.equal((await call(env, `/api/ask/${id}`, { method: "PUT", body: JSON.stringify({ closed: true }), headers: { "x-delete-token": token } })).status, 204);
  const late = await form(env, `/a/${b}/pick`, "d=2026-12-09&k=OFF");
  assert.equal(late.status, 409);
  assert.match(await late.text(), /신청이 마감됐어요/);
  assert.equal((await call(env, `/api/ask/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  assert.equal((await call(env, `/a/${a}`)).status, 404);
  assert.equal([...env.SHARES.map.keys()].filter((k) => k.startsWith("ask")).length, 0, "every member's requests went with it");
});

test("a bad round, a kind not asked for or a day of another month is refused", async () => {
  readsForget();
  const env = { SHARES: kv() };
  assert.equal((await round(env, { members: ["가".repeat(21)] })).status, 400);
  assert.equal((await round(env, { rules: { kinds: [] } })).status, 400);
  assert.equal((await round(env, { month: "2026-13" })).status, 400);
  const { members } = await (await round(env)).json();
  assert.match(await (await form(env, `/a/${members[0].key}/pick`, "d=2026-12-07&k=EDU")).text(), /이 신청은 받지 않아요/);
  assert.match(await (await form(env, `/a/${members[0].key}/pick`, "d=2027-01-02&k=OFF")).text(), /이 달 날짜가 아니에요/);
  assert.match(await (await form(env, `/a/${members[0].key}/pick`, "d=2026-12-07&k=SHIFT&s=9")).text(), /고를 수 없는 근무예요/);
});

/** The same cases as core TeamAskTest, so the page and the app never disagree. */
test("askCheck says what core TeamAsk.check says", () => {
  const r = { weekdayCap: 2, weekendCap: 3, perPerson: 0, closesAt: null, kinds: ["OFF", "LEAVE", "SHIFT"], firstCome: true, holidays: ["2026-12-25"], blocked: [], showNames: false };
  const two = [{ m: "a", d: "2026-12-07", k: "OFF" }, { m: "b", d: "2026-12-07", k: "OFF" }];
  assert.equal(askCheck(r, "2026-12", two, { m: "c", d: "2026-12-07", k: "OFF" }, 0), "이 날은 자리가 다 찼어요");
  assert.equal(askCheck(r, "2026-12", [{ m: "a", d: "2026-12-05", k: "OFF" }, { m: "b", d: "2026-12-05", k: "OFF" }], { m: "c", d: "2026-12-05", k: "OFF" }, 0), null);
  assert.equal(askCheck(r, "2026-12", two, { m: "a", d: "2026-12-07", k: "LEAVE" }, 0), null);
  assert.equal(askCheck(r, "2026-12", two, { m: "c", d: "2026-12-07", k: "SHIFT" }, 0), null);
  assert.equal(askCheck({ ...r, firstCome: false }, "2026-12", two, { m: "c", d: "2026-12-07", k: "OFF" }, 0), null);
  const mine = [7, 8, 9, 10].map((n) => ({ m: "a", d: `2026-12-${String(n).padStart(2, "0")}`, k: "OFF" }));
  assert.equal(askCheck({ ...r, perPerson: 4 }, "2026-12", mine, { m: "a", d: "2026-12-20", k: "OFF" }, 0), "신청할 수 있는 날을 다 썼어요");
  assert.equal(askCheck({ ...r, closesAt: 100 }, "2026-12", [], { m: "a", d: "2026-12-07", k: "OFF" }, 100), "신청이 마감됐어요");
  assert.equal(askCheck({ ...r, blocked: ["2026-12-07"] }, "2026-12", [], { m: "a", d: "2026-12-07", k: "OFF" }, 0), "이 날은 신청을 받지 않아요");
});
