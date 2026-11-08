// 1.39 [ADM3-41] 관리자 앱 › 서버 상태가 공유 서버가 살아 있는지 보는 곳: 아무것도 읽지 않고 { ok: true }만.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import worker from "../src/index.js";
import { SERVER_VERSION } from "../src/version.js";

test("/api/health answers ok without reading anything", async () => {
  const env = { SHARES: { async get() { throw new Error("no read"); }, async put() { throw new Error("no write"); } } };
  const res = await worker.fetch(new Request("https://share.example/api/health", { headers: { "cf-connecting-ip": "203.0.113.42" } }), env, { waitUntil() {} });
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true, version: SERVER_VERSION });
  assert.equal(res.headers.get("cache-control"), "no-store");
  const head = await worker.fetch(new Request("https://share.example/api/health", { method: "HEAD" }), env, { waitUntil() {} });
  assert.equal(head.status, 200);
});

// 1.39 [ADM3-43] 판을 올릴 때 이 서버의 판도 같이 올려요. 관리자 앱이 회사·공유 서버 판을 견줘요.
test("the share server's version is the app's version", () => {
  const props = readFileSync(new URL("../../../gradle.properties", import.meta.url), "utf8");
  assert.equal(SERVER_VERSION, props.match(/^bamnat\.versionName=(.+)$/m)[1].trim());
});

// 1.39 관리자 앱 머리띠: 판·커밋·배포 시각은 관리 열쇠로만, 공개 health는 판만.
test("/api/admin/version tells the deploy behind the token; without metadata only the version", async () => {
  const { deployInfo } = await import("../src/index.js");
  const token = "t".repeat(24);
  const meta = { id: "0f1e2d3c-aaaa-bbbb-cccc-111122223333", tag: "9a99201a1b2c", timestamp: "2026-11-08T14:30:00.000Z" };
  const env = { SHARE_ADMIN_TOKEN: token, CF_VERSION_METADATA: meta, SHARES: { async get() { return null; } } };
  const call = (auth) => worker.fetch(new Request("https://share.example/api/admin/version", { headers: auth ? { authorization: `Bearer ${auth}` } : {} }), env, { waitUntil() {} });
  assert.equal((await call(null)).status, 403);
  const ok = await call(token);
  assert.equal(ok.status, 200);
  assert.deepEqual(await ok.json(), { version: SERVER_VERSION, commit: "9a99201a", id: "0f1e2d3c", at: Date.parse(meta.timestamp) });
  assert.deepEqual(deployInfo({}), { version: SERVER_VERSION, commit: null, id: null, at: null });
  assert.deepEqual(deployInfo({ CF_VERSION_METADATA: { tag: "not a sha", timestamp: "x" } }), { version: SERVER_VERSION, commit: null, id: null, at: null });
  const pub = await worker.fetch(new Request("https://share.example/api/health"), env, { waitUntil() {} });
  assert.deepEqual(await pub.json(), { ok: true, version: SERVER_VERSION }, "the public health says no commit");
});
