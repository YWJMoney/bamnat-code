// 1.39 [ADM3-51] 관리자 앱 머리띠의 '지금 도는 서버 판'(사용자 2026-11-08 '현재 출시된 서버가 몇 버젼인지 위쪽에 보여지게'). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn } from "./helpers.mjs";
import { SERVER_VERSION } from "../src/version.js";
import { deployInfo } from "../src/ops-watch.js";

const META = { id: "1789720e-0000-4000-8000-000000000000", tag: "9a99201a1b2c", timestamp: "2026-11-08T14:30:00.000Z" };

test("the company server tells its version, the commit the deploy tool tagged and when; locally only the version", () => {
  assert.deepEqual(deployInfo({ CF_VERSION_METADATA: META }), { version: SERVER_VERSION, commit: "9a99201a", id: "1789720e", at: Date.parse(META.timestamp) });
  assert.deepEqual(deployInfo({}), { version: SERVER_VERSION, commit: null, id: null, at: null });
  assert.equal(deployInfo({ CF_VERSION_METADATA: { tag: "<b>x</b>" } }).commit, null, "a tag that is not a commit is not shown");
});

test("/api/admin/servers: the company's deploy and the share server's, read with the share admin token; an old share server gives its version only", async (t) => {
  const asked = [];
  let share = { status: 200, body: { version: SERVER_VERSION, commit: "abcdef12", id: "55aa55aa", at: 1793000000000 } };
  t.mock.method(globalThis, "fetch", async (url, init = {}) => {
    asked.push([String(url), init.headers?.authorization ?? null]);
    if (String(url).endsWith("/api/admin/version")) return new Response(JSON.stringify(share.body), { status: share.status });
    if (String(url).endsWith("/api/health")) return new Response(JSON.stringify({ ok: true, version: "1.38.0" }), { status: 200 });
    return new Response("", { status: 404 });
  });
  const { call } = server({ CF_VERSION_METADATA: META, SHARE_BASE_URL: "https://s.example", SHARE_ADMIN_TOKEN: "s".repeat(24) });
  const admin = await signIn(call);
  let r = (await call("GET", "/api/admin/servers", undefined, admin)).data;
  assert.deepEqual(r.company, { ok: true, version: SERVER_VERSION, commit: "9a99201a", id: "1789720e", at: Date.parse(META.timestamp) });
  assert.deepEqual(r.share, { ok: true, version: SERVER_VERSION, commit: "abcdef12", id: "55aa55aa", at: 1793000000000 });
  assert.ok(asked.some(([u, a]) => u === "https://s.example/api/admin/version" && a === `Bearer ${"s".repeat(24)}`));
  // 1.39 이 판 전 공유 서버: 관리 길이 없으면(404) 공개 health의 판만.
  share = { status: 404, body: {} };
  r = (await call("GET", "/api/admin/servers", undefined, admin)).data;
  assert.deepEqual(r.share, { ok: true, version: "1.38.0", commit: null, id: null, at: null });
  // 공유 서버가 연결 안 됐으면 null.
  const bare = server();
  const a2 = await signIn(bare.call);
  assert.equal((await bare.call("GET", "/api/admin/servers", undefined, a2)).data.share, null);
});
