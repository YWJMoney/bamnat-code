// 1.39 [ADM3-41] 관리자 앱 › 서버 상태가 공유 서버가 살아 있는지 보는 곳: 아무것도 읽지 않고 { ok: true }만.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

test("/api/health answers ok without reading anything", async () => {
  const env = { SHARES: { async get() { throw new Error("no read"); }, async put() { throw new Error("no write"); } } };
  const res = await worker.fetch(new Request("https://share.example/api/health", { headers: { "cf-connecting-ip": "203.0.113.42" } }), env, { waitUntil() {} });
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  assert.equal(res.headers.get("cache-control"), "no-store");
  const head = await worker.fetch(new Request("https://share.example/api/health", { method: "HEAD" }), env, { waitUntil() {} });
  assert.equal(head.status, 200);
});
