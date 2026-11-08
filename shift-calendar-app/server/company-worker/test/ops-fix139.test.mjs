// 1.39 [ADM3-43] 1.39 실서버 점검(11-08)에서 나온 관리자 앱·서버 고침: 첫 요청의 표 만들기, 정기 작업 꺼짐·판, 오류 수 맞추기,
// 서버 상태의 회사 수·판, 공유 서버 판 다름.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import worker from "../src/index.js";
import { SERVER_VERSION } from "../src/version.js";
import { jobRow, sharePing } from "../src/ops-watch.js";
import { server, signIn, report, from } from "./helpers.mjs";

test("the company server's version is the app's version", () => {
  const props = readFileSync(new URL("../../../gradle.properties", import.meta.url), "utf8");
  assert.equal(SERVER_VERSION, props.match(/^bamnat\.versionName=(.+)$/m)[1].trim());
});

test("after a deploy only the columns not there yet are added, not every ALTER again", async () => {
  const { call, DB } = server();
  await call("GET", "/api/config");
  // A new server version: its schema mark differs, and one column (job_runs.ver) is not there yet.
  await DB.prepare("UPDATE settings SET value = 'old' WHERE key = 'schema'").run();
  await DB.prepare("ALTER TABLE job_runs DROP COLUMN ver").run();
  const alters = [];
  const DB2 = Object.create(DB);
  DB2.prepare = (sql) => {
    if (/^\s*ALTER\s+TABLE/i.test(sql)) alters.push(sql.trim());
    return DB.prepare(sql);
  };
  const env = { DB: DB2, RATE_SALT: "test-salt" };
  const res = await worker.fetch(new Request("https://company.example/api/config"), env);
  assert.equal(res.status, 200);
  assert.deepEqual(alters, ["ALTER TABLE job_runs ADD COLUMN ver TEXT NOT NULL DEFAULT ''"]);
  assert.equal(await DB.prepare("SELECT value FROM settings WHERE key = 'schema'").first("value") === "old", false);
});

test("a backup with no R2 bucket is off, not fine; each job row says which server version ran it", () => {
  const now = Date.parse("2026-11-08T06:00:00Z");
  const row = { step: "backup", ok_at: now - 3600e3, fail_at: null, error: "", ms: 3, runs: 20, fails: 0, ver: "1.39.0" };
  const off = jobRow(row, now, {});
  assert.equal(off.state, "off");
  assert.match(off.off, /R2/);
  assert.equal(jobRow(row, now, { BACKUP: { put() {} } }).state, "ok");
  assert.equal(jobRow({ ...row, step: "prune" }, now, {}).ver, "1.39.0");
});

test("서버 상태 reads the company and rule counts and the server version itself", async () => {
  const { call } = server();
  const owner = await signIn(call);
  await call("POST", "/api/reports", report(1), from(1));
  const d = (await call("GET", "/api/admin/server", undefined, owner)).data;
  assert.equal(d.companies, 1);
  assert.equal(typeof d.rules, "number");
  assert.equal(d.version, SERVER_VERSION);
});

test("errors counted per day but not in a group show as loose; 판 건강 says how many are grouped", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const day = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
  await DB.prepare("INSERT INTO counters (key, count, expires) VALUES (?, 39, ?)").bind(`errors|${day}`, Date.now() + 86400e3).run();
  const errs = (await call("GET", "/api/admin/watch/errors", undefined, owner)).data;
  assert.equal(errs.loose, 39);
  const health = (await call("GET", "/api/admin/health/versions", undefined, owner)).data;
  assert.equal(health.serverErrors, 39);
  assert.equal(health.groupedErrors, 0);
});

test("the share server's version comes back from /api/health; an old one is still alive", async (t) => {
  const real = globalThis.fetch;
  t.after(() => { globalThis.fetch = real; });
  globalThis.fetch = async (u) => String(u).endsWith("/api/health") ? new Response(JSON.stringify({ ok: true, version: "1.39.0" }), { status: 200 }) : new Response("", { status: 404 });
  assert.deepEqual({ ...(await sharePing({ SHARE_BASE_URL: "https://s.example" })), ms: 0 }, { ok: true, status: 200, ms: 0, version: "1.39.0" });
});
