// 1.38 [P3-X-06] 근로계약서 링크 서명: the staff member reads the items and signs by typing their name; the boss's app reads it and
// takes the page down. Nothing but the contract, the typed name and the time is kept.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.41", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const sign = (env, key, body) => call(env, `/k/${key}/sign`, { method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const contract = { place: "밤낮 카페", staff: "김 하나", title: "단시간 근로자 근로계약서", days: 7,
  items: [{ t: "근로 시작일", b: "2026년 11월 9일부터" }, { t: "임금", b: "시급 10,320원\n지급일: 매월 10일" }] };

test("the staff member reads, signs with their name, and the boss reads the signature then takes it down", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const made = await call(env, "/api/sign", { method: "POST", body: JSON.stringify(contract) });
  assert.equal(made.status, 201);
  const { id, token, key, url } = await made.json();
  assert.equal(url, `https://share.example/k/${key}`);
  const before = await (await call(env, `/api/sign/${id}`, { headers: { "x-delete-token": token } })).json();
  assert.equal(before.seenAt, null);
  assert.equal(before.signed, null);
  const page = await (await call(env, `/k/${key}`)).text();
  assert.match(page, /단시간 근로자 근로계약서/);
  assert.match(page, /시급 10,320원<br>지급일: 매월 10일/);
  assert.doesNotMatch(page, /<script/i);
  assert.equal((await sign(env, key, "name=김하나")).status, 400, "no 동의, no signature");
  const wrong = await sign(env, key, "name=" + encodeURIComponent("박둘") + "&agree=1");
  assert.equal(wrong.status, 400);
  assert.match(await wrong.text(), /이름\(김 하나\)과 같게/);
  const ok = await sign(env, key, "name=" + encodeURIComponent("김하나") + "&agree=1");
  assert.equal(ok.status, 200);
  assert.match(await ok.text(), /서명했어요/);
  assert.equal((await sign(env, key, "name=" + encodeURIComponent("김하나") + "&agree=1")).status, 409, "signed once");
  const after = await (await call(env, `/api/sign/${id}`, { headers: { "x-delete-token": token } })).json();
  assert.ok(after.seenAt);
  assert.equal(after.signed.name, "김하나");
  const stored = [...env.SHARES.map.values()].join("");
  assert.doesNotMatch(stored, /203\.0\.113\.41/);
  assert.doesNotMatch(stored, new RegExp(token));
  assert.equal((await call(env, `/api/sign/${id}`, { method: "DELETE", headers: { "x-delete-token": token } })).status, 204);
  assert.equal((await call(env, `/k/${key}`)).status, 404);
  assert.equal([...env.SHARES.map.keys()].filter((k) => k.startsWith("sign")).length, 0);
});

test("only the token reads a contract; a bad one is refused; it lasts a month at most", async () => {
  readsForget();
  const env = { SHARES: kv() };
  assert.equal((await call(env, "/api/sign", { method: "POST", body: JSON.stringify({ ...contract, items: [] }) })).status, 400);
  assert.equal((await call(env, "/api/sign", { method: "POST", body: JSON.stringify({ ...contract, items: [{ t: "가", b: "x".repeat(601) }] }) })).status, 400);
  const made = await (await call(env, "/api/sign", { method: "POST", body: JSON.stringify({ ...contract, days: 400 }) })).json();
  assert.ok(Date.parse(made.expiresAt) - Date.now() <= 30 * 86400 * 1000 + 5000);
  assert.equal((await call(env, `/api/sign/${made.id}`)).status, 403);
  assert.equal((await call(env, `/api/sign/${made.id}`, { headers: { "x-delete-token": "x".repeat(32) } })).status, 403);
});
