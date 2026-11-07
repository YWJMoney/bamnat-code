// 1.39 G2: 봤어요 counts a person opening the link, not a messenger's preview robot or the member's app checking in the background.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, previewBot } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.42", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const schedule = JSON.stringify({ v: 1, title: "6병동 민지", ttlDays: 30, types: [{ id: 1, name: "데이", short: "D", color: "#3B82F6" }], days: [{ date: "2026-12-01", type: 1 }] });

async function made(env) {
  const m = await (await call(env, "/api/share", { method: "POST", body: schedule, headers: { "content-type": "application/json" } })).json();
  const ask = () => call(env, `/api/share/${m.id}/seen`, { headers: { "x-delete-token": m.deleteToken } }).then((r) => r.json());
  assert.equal((await ask()).seenAt, null); // the leader's app asks once right after 확정, which turns 봤어요 on
  return { ...m, ask };
}

test("a messenger's preview card does not count, the person tapping it does", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const m = await made(env);
  assert.equal((await call(env, `/s/${m.id}`, { headers: { "user-agent": "facebookexternalhit/1.1;kakaotalk-scrap/1.0;" } })).status, 200);
  assert.equal((await call(env, `/s/${m.id}`, { headers: { "user-agent": "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)" } })).status, 200);
  assert.equal((await m.ask()).seenAt, null);
  const kakaoBrowser = "Mozilla/5.0 (Linux; Android 14; SM-F946N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 KAKAOTALK 10.4.5";
  assert.equal((await call(env, `/s/${m.id}`, { headers: { "user-agent": kakaoBrowser } })).status, 200);
  assert.ok((await m.ask()).seenAt);
});

test("the member's app checking in the background does not count, opening the 받은 달력 does", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const m = await made(env);
  assert.equal((await call(env, `/api/share/${m.id}`, { headers: { "x-bamnat-quiet": "1" } })).status, 200);
  assert.equal((await m.ask()).seenAt, null);
  assert.equal((await call(env, `/api/share/${m.id}`)).status, 200);
  assert.ok((await m.ask()).seenAt);
});

test("a link the server no longer has answers not_found, so the leader's app can say so", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const m = await made(env);
  env.SHARES.map.delete(m.id);
  const r = await call(env, `/api/share/${m.id}/seen`, { headers: { "x-delete-token": m.deleteToken } });
  assert.equal(r.status, 404);
  assert.equal((await r.json()).error, "not_found");
});

test("previewBot: robots yes, people no", () => {
  const ua = (u) => previewBot(new Request("https://x/", { headers: { "user-agent": u } }));
  for (const bot of ["Mozilla/5.0 (compatible; Yeti/1.1; +http://naver.me/spd)", "TelegramBot (like TwitterBot)", "WhatsApp/2.23.20.0 A", "Mozilla/5.0 (compatible; Googlebot/2.1)", "Mozilla/5.0 (compatible; Daum/4.1; +http://cs.daum.net/faq/15/4118.html?faqId=28966) Daumoa"]) assert.ok(ua(bot), bot);
  for (const person of ["Mozilla/5.0 (Linux; Android 14; CUBOT X30) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36", "Mozilla/5.0 (iPhone) Line/13.0.0", ""]) assert.ok(!ua(person), person);
});
