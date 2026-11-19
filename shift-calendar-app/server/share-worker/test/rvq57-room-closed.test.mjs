// 2.0.0 [RVQ-57] 검토 I22c-02(사용자 11-19: 앱 없는 팀원 실시간 방은 2.1): 방(Durable Object) 바닥 클래스(갈래 D, rvd33-do-floor)는 이름만 남아 있고
// 이 판의 어떤 길도 부르지 않아요. 바인딩이 있어도 신청·바꾸기 라운드는 옛 KV 길 그대로, 손님 페이지에 실시간·알림 단추는 없어요.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) =>
  worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.9", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const post = (o) => ({ method: "POST", body: JSON.stringify(o), headers: { "content-type": "application/json" } });

test("[RVQ-57] TEAM_ROUND is never called: rounds stay on KV, the guest page has no live or alert parts", async () => {
  const touched = [];
  const trap = { get() { touched.push("get"); throw new Error("방을 부르면 안 돼요"); }, idFromName() { touched.push("id"); throw new Error("방을 부르면 안 돼요"); } };
  const env = { SHARES: kv(), TEAM_ROUND: trap, VAPID_PUBLIC_KEY: "x" };
  const a = await (await call(env, "/api/ask", post({ team: "6병동", month: "2026-12", members: ["가", "나"], shifts: [{ id: 1, label: "D", color: "#2f7d5b" }],
    rules: { weekdayCap: 2, weekendCap: 3, kinds: ["OFF"], holidays: [] } }))).json();
  assert.ok(a.id, "만들어져요");
  const page = await call(env, `/a/${a.members[0].key}`, { headers: { "user-agent": "Mozilla/5.0 (Linux; Android 14) Chrome/120 Mobile Safari/537.36" } });
  const html = await page.text();
  assert.doesNotMatch(html, /WebSocket|serviceWorker|알림 받기|pushManager/, "실시간·웹 알림 조각이 없어요");
  assert.equal((await call(env, `/a/${a.members[0].key}/live`)).status, 404);
  assert.equal((await call(env, `/a/${a.members[0].key}/push`, post({ off: true }))).status, 404);
  assert.deepEqual(touched, [], "TEAM_ROUND를 건드리지 않았어요");
});
