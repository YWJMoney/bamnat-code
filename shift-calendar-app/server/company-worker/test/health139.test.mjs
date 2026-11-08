// 1.39 [ADM3-41] 서버 상태의 공유 서버: 1.38 이하 공유 서버엔 /api/health가 없어(404) '답 없음'이 잘못 떴어요.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../src/index.js"; // ops-watch.js is loaded through the worker (it reads index.js on import)
import { sharePing } from "../src/ops-watch.js";

const env = { SHARE_BASE_URL: "https://share.example/" };
function answering(t, table) {
  const seen = [];
  t.mock.method(globalThis, "fetch", async (url) => {
    const path = new URL(url).pathname;
    seen.push(path);
    const r = table[path];
    if (r instanceof Error) throw r;
    return new Response(r?.body ?? "", { status: r?.status ?? 404 });
  });
  return seen;
}

test("a 1.39 share server answers /api/health", async (t) => {
  const seen = answering(t, { "/api/health": { status: 200, body: '{"ok":true}' } });
  const p = await sharePing(env);
  assert.equal(p.ok, true);
  assert.equal(p.old, undefined);
  assert.deepEqual(seen, ["/api/health"]);
});

test("an older share server (no /api/health) that is up is alive, marked old", async (t) => {
  const seen = answering(t, { "/robots.txt": { status: 200, body: "User-agent: *" } });
  const p = await sharePing(env);
  assert.equal(p.ok, true);
  assert.equal(p.old, true);
  assert.deepEqual(seen, ["/api/health", "/robots.txt"]);
});

test("a server error or no answer is still 답 없음", async (t) => {
  answering(t, { "/api/health": { status: 503 } });
  assert.equal((await sharePing(env)).ok, false);
  t.mock.restoreAll();
  answering(t, { "/api/health": new TypeError("fetch failed") });
  assert.equal((await sharePing(env)).ok, false);
  t.mock.restoreAll();
  answering(t, {});
  const gone = await sharePing(env);
  assert.equal(gone.ok, false, "404 and no robots.txt either: not our share server");
});

test("no SHARE_BASE_URL: nothing to ping", async () => {
  assert.equal(await sharePing({}), null);
});
