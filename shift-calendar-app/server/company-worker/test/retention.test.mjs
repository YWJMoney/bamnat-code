// 2.0.0 [RVQ-16] 검토 SRV-09·SRV-10·SRV-15·I21c-01: 보관 장부(src/retention.js) 하나가 표 목록·문서·모두 지우기·정기 정리·백업과 맞는지.
// `npm test`. 문서 표를 장부에서 다시 쓰려면 BAMNAT_WRITE_SERVER_DATA=1 node --test test/retention.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import worker, { deviceHash, prune, stmt } from "../src/index.js";
import { RETENTION, RETENTION_OF, backupRule, deviceColumns, retentionPrune, serverDataTable } from "../src/retention.js";
import { device, report, server, signIn, PASSWORD, ORIGIN } from "./helpers.mjs";

const SRC = new URL("../src/", import.meta.url);
const read = (path) => readFileSync(new URL(path, SRC), "utf8");
const srcFiles = () => readdirSync(SRC, { recursive: true }).filter((f) => String(f).endsWith(".js")).map(String);
const DAY = 24 * 60 * 60 * 1000;

/** Every table a source file makes, with the file. */
function madeTables() {
  const out = new Map();
  for (const f of srcFiles()) {
    for (const m of read(f).matchAll(/CREATE (?:VIRTUAL )?TABLE IF NOT EXISTS (\w+)/g)) out.set(m[1], f);
  }
  return out;
}

async function freshDb() {
  const s = server();
  await s.call("GET", "/api/terms");
  return s;
}

const columnsOf = (DB, table) => DB.db.prepare(`PRAGMA table_info("${table}")`).all();
const liveTables = (DB) => DB.db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'company_fts_%'")
  .all().map((r) => r.name);

test("[RVQ-16] every table the server makes has one line in the ledger, and the ledger has no line for a table that is not made", async () => {
  const made = madeTables();
  assert.ok(made.size >= 118, `found ${made.size} tables`);
  const ledger = RETENTION.map((r) => r.table);
  assert.equal(new Set(ledger).size, ledger.length, "one line a table");
  assert.deepEqual([...made.keys()].filter((t) => !RETENTION_OF.has(t)), [], "src/retention.js needs a line for these tables (what, why, when it goes, 모두 지우기, backup)");
  assert.deepEqual(ledger.filter((t) => !made.has(t)), [], "lines for tables no source file makes");
  const { DB } = await freshDb();
  assert.deepEqual(liveTables(DB).filter((t) => !RETENTION_OF.has(t)), [], "a table the server makes at start but no CREATE TABLE line shows");
});

test("[RVQ-16] each line says what, why, when it goes and how; device columns and sealed cells are real columns", async () => {
  const { DB } = await freshDb();
  const ACTIONS = ["delete", "contains", "cut", "law", "sanction"];
  for (const r of RETENTION) {
    for (const k of ["what", "why", "keep", "link"]) assert.ok(String(r[k] ?? "").trim(), `${r.table}.${k}`);
    assert.ok(Boolean(r.prune) !== Boolean(r.none), `${r.table}: prune or none, one of them`);
    const cols = new Set(columnsOf(DB, r.table).map((c) => c.name));
    if (r.table !== "company_fts") assert.ok(cols.size, r.table);
    for (const [c, a] of deviceColumns(r)) {
      assert.ok(cols.has(c), `${r.table}.${c} is a column`);
      assert.ok(ACTIONS.includes(a), `${r.table}.${c}: ${a}`);
      // A record the law keeps has an end; one tied to an install with no clean-up is one the person can remove.
      if (a === "law") assert.ok(r.prune, `${r.table}: a kept record needs its end`);
    }
    if (r.none && deviceColumns(r).length) {
      assert.ok(deviceColumns(r).every(([, a]) => ["delete", "cut", "sanction"].includes(a)), `${r.table}: tied to an install without a clean-up`);
    }
    for (const c of backupRule(r.table).seal ?? []) assert.ok(cols.has(c), `${r.table}.${c} to seal`);
    if (typeof r.prune === "string") {
      const src = read(r.prune);
      assert.ok(src.includes(`DELETE FROM ${r.table}`) || src.includes(`"${r.table}"`), `${r.prune} deletes from ${r.table}`);
    }
  }
});

test("[RVQ-16] the ledger's clean-up statements run, and docs/server-data.md shows the ledger's table", async () => {
  const { env } = await freshDb();
  await env.DB.batch(retentionPrune(stmt, env, Date.now()));
  const path = new URL("../../../docs/server-data.md", import.meta.url);
  const doc = readFileSync(path, "utf8");
  const start = doc.indexOf("<!-- RETENTION:start -->");
  const end = doc.indexOf("<!-- RETENTION:end -->");
  assert.ok(start > 0 && end > start, "the document marks where the ledger's table goes");
  const want = `<!-- RETENTION:start -->\n${serverDataTable()}\n<!-- RETENTION:end -->`;
  const have = doc.slice(start, end + "<!-- RETENTION:end -->".length);
  if (have !== want && process.env.BAMNAT_WRITE_SERVER_DATA === "1") writeFileSync(path, doc.slice(0, start) + want + doc.slice(end + "<!-- RETENTION:end -->".length));
  else assert.equal(have, want, "docs/server-data.md: BAMNAT_WRITE_SERVER_DATA=1 node --test test/retention.test.mjs");
});

/** A row in [table] with [dev] in [col] (and every other column filled), as a phone's own row would be. */
function seed(DB, table, col, value, n) {
  const cols = columnsOf(DB, table);
  const names = [];
  const values = [];
  for (const c of cols) {
    if (c.name === col) {
      names.push(c.name);
      values.push(value);
    } else if (c.notnull && c.dflt_value === null) {
      names.push(c.name);
      values.push(/INT|REAL/i.test(c.type) ? 1 : `x${n}${c.name}`);
    }
  }
  DB.db.prepare(`INSERT OR IGNORE INTO "${table}" (${names.map((x) => `"${x}"`).join(", ")}) VALUES (${names.map(() => "?").join(", ")})`).run(...values);
}

test("[RVQ-16] 검토 SRV-09: after 모두 지우기 no table keeps the install's hash, but for a sale's record and a restriction still running", async () => {
  const { call, DB } = await freshDb();
  const me = { "x-device": device(1) };
  // What a phone really sends: a company setting, a question, its push token.
  assert.equal((await call("POST", "/api/reports", report(1))).status, 201);
  assert.equal((await call("POST", "/api/inquiries", { v: 1, device: device(1), kind: "bug", text: "알람이 늦게 울려요. 확인 부탁해요", info: { app: "2.0.0 (54)" } })).status, 201);
  assert.equal((await call("POST", "/api/push/phone", { device: device(1), token: `tok${"A".repeat(150)}` })).status, 200);
  // And a row in every table the ledger ties to an install, one for each column.
  const dev = await deviceHash(device(1));
  let n = 0;
  for (const r of RETENTION) {
    for (const [c, a] of deviceColumns(r)) seed(DB, r.table, c, a === "contains" ? `x|${dev}|y` : dev, n++);
  }
  const res = await call("DELETE", "/api/mine", undefined, me);
  assert.equal(res.status, 200, res.text);
  const left = [];
  for (const t of liveTables(DB)) {
    for (const c of columnsOf(DB, t)) {
      const k = DB.db.prepare(`SELECT COUNT(*) AS n FROM "${t}" WHERE instr(CAST("${c.name}" AS TEXT), ?) > 0`).get(dev).n;
      const may = ["law", "sanction"].includes(RETENTION_OF.get(t)?.device?.[c.name]);
      if (k && !may) left.push(`${t}.${c.name} (${k})`);
    }
  }
  assert.deepEqual(left, [], "the install's hash is still in these columns");
});

test("[RVQ-16] 검토 SRV-09: the hourly clean-up ends what was kept without an end", async (t) => {
  const { env, DB } = await freshDb();
  const now = Date.parse("2026-12-01T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now });
  const add = (sql, ...a) => DB.db.prepare(sql).run(...a);
  // 알림 토큰: 앱이 180일 동안 소식·문의 답을 묻지 않은 것(묻는 앱은 30일마다 새로 적혀요).
  add("INSERT INTO push_phones (device, token, at) VALUES ('old', 't1', ?), ('asked', 't2', ?), ('new', 't3', ?)", now - 200 * DAY, now - 40 * DAY, now - DAY);
  add("INSERT INTO inquiries (id, device, kind, info, status, public, created_at, updated_at) VALUES ('q1', 'asked', 'bug', '{}', 'open', 0, ?, ?)", now, now);
  // 같은 근무 알림: 토큰이 없어진 지 30일, 또는 180일 동안 묻지 않으면 가요.
  add("INSERT INTO plaza_watch (device, room, form, at) VALUES ('old', 'style-shift', 'a', ?), ('new', 'style-shift', 'a', ?), ('gone', 'style-day', 'a', ?), ('notoken', 'style-day', 'a', ?)",
    now - 200 * DAY, now - 40 * DAY, now - DAY, now - 40 * DAY);
  add("INSERT INTO integrity (device, ok, why, at) VALUES ('a', 1, '', ?), ('b', 1, '', ?)", now - 31 * DAY, now - DAY);
  add("INSERT INTO plaza_pushed (device, kind, at) VALUES ('a', 'plaza_question', ?), ('b', 'plaza_question', ?)", now - 8 * DAY, now - DAY);
  add("INSERT INTO recipe_image_flags (id, device, reason, at) VALUES ('nopic', 'a', 'other', ?)", now);
  add("INSERT INTO studio_need_links (src, ref, need_id, device, text, at) VALUES ('inq', 'q1', 'n1', 'asked', '부탁', ?), ('inq', 'qgone', 'n1', 'x', '부탁', ?)", now, now);
  add("INSERT INTO write_strikes (device, kind, ref, at, confirmed) VALUES ('a', 'post', 'p1', ?, 1), ('b', 'post', 'p2', ?, 1)", now - 400 * DAY, now - 10 * DAY);
  await prune(env, now);
  const ids = (sql) => DB.db.prepare(sql).all().map((r) => Object.values(r)[0]).sort();
  assert.deepEqual(ids("SELECT device FROM push_phones"), ["asked", "new"]);
  assert.deepEqual(ids("SELECT device FROM plaza_watch"), ["gone", "new"], "no token for 30 days, or not asked for 180 days; a new one waits for its token");
  assert.deepEqual(ids("SELECT device FROM integrity"), ["b"]);
  assert.deepEqual(ids("SELECT device FROM plaza_pushed"), ["b"]);
  assert.deepEqual(ids("SELECT id FROM recipe_image_flags"), [], "a report on a picture that is gone");
  assert.deepEqual(ids("SELECT ref FROM studio_need_links"), ["q1"], "a link to a question that is gone");
  assert.deepEqual(ids("SELECT device FROM write_strikes"), ["b"]);
});

test("[RVQ-16] an app that asks for its news or answers keeps its token and watch fresh, once in 30 days", async (t) => {
  const { call, DB } = await freshDb();
  const now = Date.parse("2026-12-01T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now });
  const dev = await deviceHash(device(1));
  DB.db.prepare("INSERT INTO push_phones (device, token, at) VALUES (?, 't', ?)").run(dev, now - 100 * DAY);
  DB.db.prepare("INSERT INTO plaza_watch (device, room, form, at) VALUES (?, 'style-shift', 'a', ?)").run(dev, now - 10 * DAY);
  assert.equal((await call("GET", "/api/digest", undefined, { "x-device": device(1) })).status, 200);
  assert.equal(DB.db.prepare("SELECT at FROM push_phones").get().at, now);
  assert.equal(DB.db.prepare("SELECT at FROM plaza_watch").get().at, now - 10 * DAY, "fresh within 30 days: not written again");
  DB.db.prepare("UPDATE push_phones SET at = ?").run(now - 50 * DAY);
  assert.equal((await call("GET", "/api/inquiries", undefined, { "x-device": device(1) })).status, 200);
  assert.equal(DB.db.prepare("SELECT at FROM push_phones").get().at, now);
});

test("[RVQ-16] 검토 SRV-15: the 2단계 key is sealed in D1, still works, an old plain one is sealed by the hourly job, and a changed secret asks for a reset", async (t) => {
  const { totpCode } = await import("../src/totp.js");
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T01:00:00Z") });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  const started = (await call("POST", "/api/admin/me/2fa", { do: "start" }, owner)).data;
  const stored = DB.db.prepare("SELECT secret FROM admin_totp WHERE admin = 'owner'").get().secret;
  assert.match(stored, /^s1\./);
  assert.ok(!stored.includes(started.secret), "the key itself is not in D1");
  const step = () => Math.floor(Date.now() / 30000);
  assert.equal((await call("POST", "/api/admin/me/2fa", { do: "confirm", code: await totpCode(started.secret, step()) }, owner)).status, 200);
  const APP = { "x-admin": "1", origin: ORIGIN };
  t.mock.timers.setTime(Date.now() + 30000);
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD, code: await totpCode(started.secret, step()) }, APP)).status, 200);
  // A key made before 2.0.0 was kept plain: it still works and the hourly job seals it.
  DB.db.prepare("UPDATE admin_totp SET secret = ?").run(started.secret);
  await prune(env, Date.now());
  assert.match(DB.db.prepare("SELECT secret FROM admin_totp").get().secret, /^s1\./);
  t.mock.timers.setTime(Date.now() + 30000);
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD, code: await totpCode(started.secret, step()) }, APP)).status, 200);
  // RATE_SALT changed without SEAL_KEY: the key cannot be opened, so no way in by code (the owner resets with ADMIN_2FA_RESET).
  env.RATE_SALT = "another-salt";
  t.mock.timers.setTime(Date.now() + 30000);
  const locked = await call("POST", "/api/admin/login", { password: PASSWORD, code: await totpCode(started.secret, step()) }, APP);
  assert.equal(locked.data?.error, "code_reset");
  env.SEAL_KEY_OLD = undefined;
  env.ADMIN_2FA_RESET = "1";
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD }, APP)).status, 200);
  void worker;
});
