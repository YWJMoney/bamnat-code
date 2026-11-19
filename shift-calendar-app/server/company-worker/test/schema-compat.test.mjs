// 2.0.0 [RVQ-02] 검토 SRV-04 (마): DB 판(표)은 앞으로만 가요. 배포가 되돌려지면 앞 판 코드가 새 표 위에서 돌아요.
// 배포 도구(build)는 지금 도는 판의 커밋으로 tools/schema-compat.mjs를 돌리고, 여기서는 그 틀과 늘 지킬 규칙을 봐요.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { server } from "./helpers.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const sources = () => {
  const out = [];
  const walk = (d) => {
    for (const f of readdirSync(d, { withFileTypes: true })) {
      if (f.isDirectory()) walk(join(d, f.name));
      else if (f.name.endsWith(".js")) out.push([join(d, f.name), readFileSync(join(d, f.name), "utf8")]);
    }
  };
  walk(join(root, "src"));
  return out;
};

test("new columns keep old code's inserts working: NOT NULL only with a DEFAULT", () => {
  const bad = [];
  for (const [file, text] of sources()) {
    for (const m of text.matchAll(/ALTER TABLE \w+ ADD COLUMN [^"`]+/g)) {
      if (/NOT NULL/i.test(m[0]) && !/DEFAULT/i.test(m[0])) bad.push(`${file.slice(root.length)}: ${m[0]}`);
    }
  }
  assert.deepEqual(bad, []);
});

test("tables and columns are never dropped or renamed (old code still reads them after a rollback)", () => {
  const allowed = new Set(["DROP TABLE IF EXISTS sessions"]); // 관리자 계정 전의 로그인 표(1.2x), 지금 코드도 앞 판도 읽지 않아요.
  const found = [];
  for (const [file, text] of sources()) {
    for (const m of text.matchAll(/(DROP (TABLE|COLUMN)|RENAME (TO|COLUMN))[^"`,]{0,60}/g)) {
      if (!allowed.has(m[0].trim())) found.push(`${file.slice(root.length)}: ${m[0]}`);
    }
  }
  assert.deepEqual(found, []);
});

test("every insert names its columns, so a column added later never shifts old code's values", () => {
  const bad = [];
  for (const [file, text] of sources()) {
    for (const m of text.matchAll(/INSERT (?:OR \w+ )?INTO \w+ VALUES/g)) bad.push(`${file.slice(root.length)}: ${m[0]}`);
  }
  assert.deepEqual(bad, []);
});

test("the compat check runs (this code as its own previous version)", () => {
  const out = execFileSync(process.execPath, ["--disable-warning=ExperimentalWarning", "tools/schema-compat.mjs", "."], { cwd: root, encoding: "utf8" });
  assert.match(out, /^맞음: /m);
});

test("deep health puts this code's tables back when an older copy marked the database as its own", async () => {
  const { call, DB } = server();
  assert.equal((await call("GET", "/api/admin/health?deep=1")).status, 200);
  // 카나리 동안 앞 판의 새 isolate가 표 표시를 바꿔 둔 상태.
  await DB.prepare("UPDATE settings SET value = 'older' WHERE key = 'schema'").run();
  const r = await call("GET", "/api/admin/health?deep=1");
  assert.equal(r.status, 200);
  assert.equal(r.data.schema, true);
  assert.notEqual((await DB.prepare("SELECT value FROM settings WHERE key = 'schema'").first()).value, "older");
});

test("canary: requests are counted per deployed version, and only the two asked for come back", async () => {
  const { call, env } = server({ CF_VERSION_METADATA: { id: "abcdef12-0000-4000-8000-000000000000" } });
  for (let i = 0; i < 5; i++) await call("GET", "/api/config");
  await call("GET", "/api/admin/health"); // 배포 도구의 확인은 세지 않아요
  const r = await call("GET", "/api/admin/health?canary=abcdef12&base=00000000&minutes=10");
  assert.equal(r.status, 200);
  assert.equal(r.data.canary.new.n, 6); // config 5번 + 시험 도우미가 처음에 부르는 /api/terms 1번
  assert.equal(r.data.canary.new.err, 0);
  assert.deepEqual(r.data.canary.base, {});
  // 번호를 모르면 아무 숫자도 없어요.
  const none = await call("GET", "/api/admin/health?canary=zzzz&base=");
  assert.deepEqual(none.data.canary, { new: {}, base: {} });
  // 다른 판으로 뜬 isolate의 숫자도 같은 표에 따로.
  env.CF_VERSION_METADATA = { id: "00000000-1111-4000-8000-000000000000" };
  await call("GET", "/api/config");
  const both = await call("GET", "/api/admin/health?canary=abcdef12&base=00000000");
  assert.equal(both.data.canary.base.n, 1);
});
