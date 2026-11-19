// 2.0.0 [RVD-33] 앱 없는 팀원 방(Q1 4h)의 Durable Object 바닥 판: 실서버 공유 코드에 TeamRound 클래스·TEAM_ROUND 바인딩·
// migration v1-team-round만 더해요. 이름과 태그가 Q1 4h와 같고, 손님이 보는 동작은 그대로(코드가 방을 부르지 않음)인지 봐요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import worker, { TeamRound } from "../src/index.js";
import { bundle } from "../tools/bundle.mjs";
import { SERVER_VERSION } from "../src/version.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const toml = readFileSync(join(root, "wrangler.toml"), "utf8").replace(/^\s*#.*$/gm, "");
const source = readFileSync(join(root, "src/index.js"), "utf8");

test("[RVD-33] wrangler.toml의 방 바인딩·migration이 Q1 4h와 같아요(새로 만들기만, 지우기·옮기기 없음)", () => {
  assert.match(toml, /\[\[durable_objects\.bindings\]\]\s*name = "TEAM_ROUND"\s*class_name = "TeamRound"\s*(?:\n|$)/);
  assert.match(toml, /\[\[migrations\]\]\s*tag = "v1-team-round"\s*new_sqlite_classes = \["TeamRound"\]\s*(?:\n|$)/);
  assert.equal(toml.match(/\[\[migrations\]\]/g).length, 1);
  assert.doesNotMatch(toml, /deleted_classes|renamed_classes|transferred_classes|script_name/);
});

test("[RVD-33] TeamRound를 src와 대시보드 번들 모두에서 내보내고, 빈 클래스는 저장소를 건드리지 않아요", async () => {
  assert.equal(typeof TeamRound, "function");
  const file = join(mkdtempSync(join(tmpdir(), "share-floor-")), "worker.js");
  writeFileSync(file, bundle());
  const mod = await import(pathToFileURL(file).href);
  assert.equal(typeof mod.TeamRound, "function");
  const untouchable = new Proxy({}, { get() { throw new Error("storage touched"); } });
  const room = new mod.TeamRound({ storage: untouchable, id: { toString: () => "x" } }, {});
  const r = await room.fetch(new Request("https://room.example/anything"));
  assert.equal(r.status, 404);
});

test("[RVD-33] 공유 서버 코드는 방(TEAM_ROUND)을 부르지 않아요: 바인딩이 있어도 없어도 같은 답", async () => {
  const code = source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
  assert.doesNotMatch(code, /TEAM_ROUND/);
  const kv = new Map();
  const SHARES = {
    async get(k, t) { const v = kv.get(k); return v == null ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { kv.set(k, v); },
    async delete(k) { kv.delete(k); },
    async list() { return { keys: [], list_complete: true }; },
  };
  const trap = new Proxy({}, { get() { throw new Error("TEAM_ROUND touched"); } });
  const ctx = { waitUntil() {} };
  for (const env of [{ SHARES }, { SHARES, TEAM_ROUND: trap }]) {
    const health = await worker.fetch(new Request("https://share.example/api/health"), env, ctx);
    assert.equal(health.status, 200);
    assert.equal((await health.json()).version, SERVER_VERSION);
    const missing = await worker.fetch(new Request("https://share.example/s/AAAAAAAAAAAA"), env, ctx);
    assert.equal(missing.status, 404);
  }
});
