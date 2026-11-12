// 2.0.0 [RVQ-16] 검토 SRV-15·U21-08: 하루 백업이 비밀 칸을 잠그고 조각으로 나눠 쓰는지, 되살리기 연습이 문제를 잡는지,
// tools/backup-open.mjs로 빈 DB에 실제로 되살아나는지. [RVQ-17] 검토 U21-07·I21b-02: 데이터 요청 대행(지우기·열람)과 증빙.
import { test } from "node:test";
import assert from "node:assert/strict";
import { gunzipSync, gzipSync } from "node:zlib";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { deviceHash } from "../src/index.js";
import { KEEP, drillTick } from "../src/keep.js";
import { openDay } from "../tools/backup-open.mjs";
import { device, server, signIn } from "./helpers.mjs";

/** R2 as the backup uses it: put, get (body stream and text), list with prefix·delimiter, delete. */
function fakeR2() {
  const store = new Map();
  return {
    store,
    async put(key, body) {
      store.set(key, typeof body === "string" ? new TextEncoder().encode(body) : new Uint8Array(body));
    },
    async get(key) {
      const v = store.get(key);
      if (!v) return null;
      return { body: new Blob([v]).stream(), text: async () => new TextDecoder().decode(v) };
    },
    async list({ prefix = "", delimiter } = {}) {
      const keys = [...store.keys()].filter((k) => k.startsWith(prefix)).sort();
      if (!delimiter) return { objects: keys.map((key) => ({ key })), truncated: false };
      const pre = new Set(keys.map((k) => k.slice(prefix.length)).filter((k) => k.includes(delimiter)).map((k) => prefix + k.slice(0, k.indexOf(delimiter) + 1)));
      return { objects: [], delimitedPrefixes: [...pre], truncated: false };
    },
    async delete(keys) {
      for (const k of [].concat(keys)) store.delete(k);
    },
  };
}

const SECRETS = { pw: "pbkdf2$100000$c2FsdA$aGFzaA", token: "fcm-token-of-a-phone-0123456789", invite: "invite-token-abcdef0123456789", sub: "play-purchase-token-xyz" };

async function seeded(extra = {}) {
  const R2 = fakeR2();
  const s = server({ BACKUP: R2, ...extra });
  const owner = await signIn(s.call);
  const now = Date.now();
  const add = (sql, ...args) => s.DB.prepare(sql).bind(...args).run();
  await add("INSERT INTO admins (id, email, name, pw, perms, status, created_at) VALUES ('a1', 'helper@example.com', '도우미', ?, '{}', 'active', ?)", SECRETS.pw, now);
  await add("INSERT INTO invites (email, perms, token, expires, created_at) VALUES ('next@example.com', '{}', ?, ?, ?)", SECRETS.invite, now + 86400000, now);
  await add("INSERT INTO push_phones (device, token, at) VALUES (?, ?, ?)", await deviceHash(device(7)), SECRETS.token, now);
  await add("INSERT INTO subs (hash, device, token, product, state, created_at, checked_at) VALUES ('h1', ?, ?, 'bamnat_pro', 'active', ?, ?)", await deviceHash(device(7)), SECRETS.sub, now, now);
  return { ...s, R2, owner, now };
}

/** Runs the day's backup to its end, as the button does (each press goes on from where the last stopped). */
async function backUp(call, owner, max = 60) {
  for (let i = 0; i < max; i++) {
    const r = await call("POST", "/api/admin/backup/run", {}, owner);
    assert.equal(r.status, 200, r.text);
    if (r.data.skipped === "done" || (r.data.of && r.data.done === r.data.of)) return i + 1;
  }
  throw new Error("the backup did not end");
}

const textOf = (R2, key) => gunzipSync(R2.store.get(key)).toString("utf8");

test("[RVQ-16] 검토 SRV-15: the day's backup seals the secret cells, leaves out what goes soon, and writes a v2 list", async () => {
  const { R2, call, owner } = await seeded();
  await backUp(call, owner);
  const keys = [...R2.store.keys()];
  const day = keys.find((k) => k.endsWith("manifest.json")).slice(3, 13);
  const manifest = JSON.parse(new TextDecoder().decode(R2.store.get(`d1/${day}/manifest.json`)));
  assert.equal(manifest.v, 2);
  for (const t of ["counters", "requests", "admin_sessions", "codes"]) assert.ok(!(t in manifest.tables), `${t} is left out`);
  assert.ok(!keys.some((k) => /company_fts/.test(k)), "the search index is made again, not kept");
  const all = keys.filter((k) => k.endsWith(".ndjson.gz")).map((k) => textOf(R2, k)).join("\n");
  for (const [what, v] of Object.entries(SECRETS)) assert.ok(!all.includes(v), `${what} is not in the backup as it is`);
  assert.ok(!all.includes("test-salt"), "the server's secret is not in the backup");
  const admins = textOf(R2, `d1/${day}/admins.1.ndjson.gz`).split("\n").map((l) => JSON.parse(l));
  assert.match(admins.find((a) => a.id === "a1").pw.$sealed, /^s1\./);
  assert.equal(admins.find((a) => a.id === "a1").email, "helper@example.com", "other cells stay as they are");
  assert.deepEqual(manifest.sealed.push_phones, ["token"]);
  assert.equal(manifest.parts.admins, 1);
  // The settings row with the local salt is sealed; others are not.
  const settings = textOf(R2, `d1/${day}/settings.1.ndjson.gz`).split("\n").map((l) => JSON.parse(l));
  assert.ok(settings.filter((r) => r.key !== "salt").every((r) => typeof r.value === "string"));
  const view = (await call("GET", "/api/admin/backup", undefined, owner)).data;
  assert.equal(view.sealKey, "RATE_SALT");
  assert.ok(view.skipped.includes("counters") && view.sealed.admins.includes("pw"));
  assert.ok(!JSON.stringify(view).includes("test-salt"), "the view names the secret, never its value");
});

test("[RVQ-16] a big table goes in parts over several hourly runs, and every row is there once", async (t) => {
  const { R2, DB, call, owner } = await seeded();
  const keep = { ...KEEP };
  t.after(() => Object.assign(KEEP, keep));
  Object.assign(KEEP, { partBytes: 4000, runBytes: 12000 });
  const now = Date.now();
  const rows = Array.from({ length: 300 }, (_, i) => DB.prepare("INSERT INTO audit (at, action, target, detail) VALUES (?, 'test', ?, ?)").bind(now - i, `t${i}`, "가".repeat(60)));
  await DB.batch(rows);
  const runs = await backUp(call, owner, 200);
  assert.ok(runs > 2, `went on over ${runs} runs`);
  const day = [...R2.store.keys()].find((k) => k.endsWith("manifest.json")).slice(3, 13);
  const manifest = JSON.parse(new TextDecoder().decode(R2.store.get(`d1/${day}/manifest.json`)));
  assert.ok(manifest.parts.audit > 2, `audit in ${manifest.parts.audit} parts`);
  const lines = Array.from({ length: manifest.parts.audit }, (_, i) => textOf(R2, `d1/${day}/audit.${i + 1}.ndjson.gz`)).join("\n").split("\n").filter(Boolean);
  assert.equal(lines.length, manifest.tables.audit);
  assert.equal(new Set(lines).size, lines.length, "no row twice");
  assert.ok(lines.length >= 300, "the 300 rows and the log of the runs before the table was read");
});

test("[RVQ-16] 검토 U21-08: the restore drill reads the last day back, and says what is wrong", async () => {
  const OLD_SALT = "the-salt-before-rotating-0123";
  const { R2, call, owner, env } = await seeded({ RATE_SALT: OLD_SALT });
  await backUp(call, owner);
  let r = await call("POST", "/api/admin/backup/drill", {}, owner);
  assert.equal(r.status, 200, r.text);
  for (let i = 0; i < 20 && !r.data.done; i++) r = await call("POST", "/api/admin/backup/drill", {}, owner);
  assert.equal(r.data.ok, true);
  let view = (await call("GET", "/api/admin/backup", undefined, owner)).data;
  assert.equal(view.drill.ok, true);
  assert.ok(view.drill.tables > 50 && view.drill.rowsTotal > 0, "every table read back");
  assert.ok(view.drill.checked >= 3, "sealed cells were opened with today's key");
  assert.equal(view.drillLate, false);
  const admins = view.drill.rows.find((x) => x.table === "admins");
  assert.equal(admins.rows, admins.want);
  assert.ok(admins.db >= admins.want, "today's rows beside the backup's");

  // A part gone and the secret changed (RATE_SALT made again, no SEAL_KEY): the next drill says both, and the admins hear of it.
  const day = view.drill.day;
  R2.store.delete(`d1/${day}/invites.1.ndjson.gz`);
  const drill = async (e) => {
    let d = await drillTick(e, Date.now(), { force: true });
    for (let i = 0; i < 20 && !d.done; i++) d = await drillTick(e, Date.now());
    return d;
  };
  assert.equal((await drill({ ...env, RATE_SALT: "a-new-salt-after-rotating" })).ok, false);
  view = (await call("GET", "/api/admin/backup", undefined, owner)).data;
  const problems = Object.fromEntries(view.drill.problems.map((x) => [x.table, x.problem]));
  assert.equal(problems.invites, "조각이 없음");
  assert.equal(problems.admins, "잠근 칸이 지금 열쇠로 안 열림");
  assert.ok(env.DB.db.prepare("SELECT detail FROM audit WHERE action = 'backup_drill'").all().some((a) => a.detail.includes("문제 있음")));

  // The old secret kept as SEAL_KEY_OLD opens it again.
  await drill({ ...env, RATE_SALT: "a-new-salt-after-rotating", SEAL_KEY_OLD: OLD_SALT });
  view = (await call("GET", "/api/admin/backup", undefined, owner)).data;
  assert.ok(!view.drill.problems.some((x) => x.table === "admins"), "the right secret opens the sealed cells");
});

test("[RVQ-16] tools/backup-open.mjs puts a day back into an empty database, with the sealed cells opened", async () => {
  const { R2, DB, call, owner } = await seeded();
  await backUp(call, owner);
  const dir = mkdtempSync(join(tmpdir(), "bamnat-backup-"));
  try {
    for (const [k, v] of R2.store) writeFileSync(join(dir, k.slice(14)), v);
    await assert.rejects(openDay(dir, []), /잠근 칸이 열리지 않아요/, "no secret: nothing half done");
    await assert.rejects(openDay(dir, ["wrong-secret"]), /잠근 칸이 열리지 않아요/);
    const { sql, counts } = await openDay(dir, ["test-salt"]);
    assert.ok(!sql.includes("s1."), "every sealed cell opened");
    // An empty server of the same version, then the SQL.
    const fresh = server();
    await fresh.call("GET", "/api/terms");
    for (const t of Object.keys(counts)) fresh.DB.db.exec(`DELETE FROM "${t}"`);
    fresh.DB.db.exec(sql);
    for (const [t, n] of Object.entries(counts)) assert.equal(Number(fresh.DB.db.prepare(`SELECT COUNT(*) AS n FROM "${t}"`).get().n), n, t);
    assert.equal(fresh.DB.db.prepare("SELECT pw FROM admins WHERE id = 'a1'").get().pw, SECRETS.pw);
    assert.equal(fresh.DB.db.prepare("SELECT token FROM push_phones").get().token, SECRETS.token);
    assert.equal(fresh.DB.db.prepare("SELECT token FROM subs WHERE hash = 'h1'").get().token, SECRETS.sub);
    assert.deepEqual(fresh.DB.db.prepare("SELECT value FROM settings WHERE key = 'salt'").get(), DB.db.prepare("SELECT value FROM settings WHERE key = 'salt'").get());
    // A part gone: it stops.
    rmSync(join(dir, "admins.1.ndjson.gz"));
    await assert.rejects(openDay(dir, ["test-salt"]), /조각이 없어요/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("[RVQ-17] 검토 U21-07·I21b-02: a request tied to a question forgets that install on the server, keeps the question to answer, and keeps the proof", async () => {
  const { call, owner, DB } = await seeded();
  const me = device(21);
  const dev = await deviceHash(me);
  assert.equal((await call("POST", "/api/inquiries", { v: 1, device: me, kind: "other", text: "제 자료를 모두 지워 주세요. 앱은 지웠어요", info: { app: "2.0.0 (54)" } })).status, 201);
  assert.equal((await call("POST", "/api/inquiries", { v: 1, device: me, kind: "bug", text: "예전에 보낸 문의예요. 알람이 늦어요", info: { app: "2.0.0 (54)" } })).status, 201);
  const ask = DB.db.prepare("SELECT q.id FROM inquiries q JOIN inquiry_messages m ON m.inquiry_id = q.id WHERE q.device = ? AND m.text LIKE '%모두 지워%'").get(dev);
  const code = (await call("POST", "/api/passes/code", {}, { "x-device": me })).data.code;
  assert.ok(code);
  const made = await call("POST", "/api/admin/data-requests", { kind: "correct", inquiry: ask.id, summary: "자료 삭제" }, owner);
  assert.equal(made.status, 200, made.text);
  const id = made.data.id;

  // 열람 first: what this install has, without secrets.
  const seen = await call("POST", `/api/admin/data-requests/${id}/run`, { do: "export" }, owner);
  assert.equal(seen.status, 200, seen.text);
  assert.ok(seen.data.file.data.inquiries.length >= 2);
  assert.ok(seen.data.file.data.inquiry_messages.some((m) => m.text.includes("모두 지워")));
  for (const [t, rows] of Object.entries(seen.data.file.data)) assert.ok(!JSON.stringify(rows).includes(dev), `${t}: the install's hash is written as 이 설치`);
  assert.ok(seen.data.proof.tables.inquiries >= 2);

  // 지우기 asks once more on the screen first.
  assert.equal((await call("POST", `/api/admin/data-requests/${id}/run`, { do: "forget" }, owner)).data.error, "confirm");
  const gone = await call("POST", `/api/admin/data-requests/${id}/run`, { do: "forget", confirm: true }, owner);
  assert.equal(gone.status, 200, gone.text);
  assert.ok(gone.data.proof.gone.inquiries >= 1, "the other question went");
  assert.equal(gone.data.proof.left.inquiries, 1, "the request's own question stays, to answer it");
  assert.equal(gone.data.proof.inquiryKept, 1);
  const list = (await call("GET", "/api/admin/data-requests", undefined, owner)).data;
  const row = list.items.find((x) => x.id === id);
  assert.equal(row.runnable, true);
  assert.deepEqual(row.proof.map((p) => p.do), ["export", "forget"]);
  // The answer still reaches the phone.
  assert.equal((await call("GET", "/api/inquiries", undefined, { "x-device": me })).data.inquiries.length, 1);
  assert.ok(DB.db.prepare("SELECT detail FROM audit WHERE action = 'data_request_run' AND target = ?").all(id).some((a) => /^지움 \d+개 표/.test(a.detail)));
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM pass_codes").get().n, 0, "the recovery code went with the rest");

  // A 환불 request is not this kind of work; a closed one does nothing; without a question it needs the recovery code.
  const refund = (await call("POST", "/api/admin/data-requests", { kind: "refund", inquiry: ask.id, summary: "환불" }, owner)).data.id;
  assert.equal((await call("POST", `/api/admin/data-requests/${refund}/run`, { do: "export" }, owner)).data.error, "kind");
  const bare = (await call("POST", "/api/admin/data-requests", { kind: "access", summary: "메일로 온 열람 요청" }, owner)).data.id;
  assert.equal((await call("POST", `/api/admin/data-requests/${bare}/run`, { do: "export" }, owner)).data.error, "no_device");
  assert.equal((await call("POST", `/api/admin/data-requests/${bare}/run`, { do: "export", code: "WRONG-CODE-0000" }, owner)).data.error, "code");
  // A recovery code finds an install with no question (the person kept the code, not the app).
  const other = device(22);
  const otherCode = (await call("POST", "/api/passes/code", {}, { "x-device": other })).data.code;
  const byCode = await call("POST", `/api/admin/data-requests/${bare}/run`, { do: "export", code: otherCode.toLowerCase() }, owner);
  assert.equal(byCode.status, 200, byCode.text);
  assert.equal(byCode.data.proof.via, "code");
  assert.equal(byCode.data.file.data.pass_codes[0].hash, "(보안 정보라 빼고 보내요)");
  assert.ok(!DB.db.prepare("SELECT proof FROM data_requests WHERE id = ?").get(bare).proof.includes(otherCode), "the code is not kept");
  assert.equal((await call("POST", `/api/admin/data-requests/${id}`, { status: "done", note: "지우고 증빙 남김" }, owner)).status, 200);
  assert.equal((await call("POST", `/api/admin/data-requests/${id}/run`, { do: "export" }, owner)).data.error, "closed");
});

test("[RVQ-18] 검토 I21b-01: 보안 한 장 notes the day a secret changed by a mark only it can make, and is the owner's alone", async () => {
  const { env, call, DB, owner } = await seeded({ SEAL_KEY: "a-seal-key-of-twenty-chars" });
  await backUp(call, owner);
  const first = (await call("GET", "/api/admin/security", undefined, owner)).data;
  const row = (d, key) => d.rows.find((r) => r.key === key);
  assert.match(row(first, "secret:ADMIN_PASSWORD").state, /^처음 본 날 \d{4}-\d{2}-\d{2}부터 그대로$/);
  assert.equal(row(first, "secret:SEAL_KEY").tone, "ok");
  assert.equal(row(first, "backup").tone, "ok", "today's backup is done");
  assert.equal(row(first, "drill").tone, "warn", "no drill yet");
  const marks = DB.db.prepare("SELECT value FROM settings WHERE key = 'secret_marks'").get().value;
  for (const v of [PASSWORD_OF(env), env.RATE_SALT, env.SEAL_KEY]) assert.ok(!marks.includes(v), "a mark, not the value");
  assert.ok(Object.values(JSON.parse(marks)).every((m) => /^[0-9a-f]{16}$/.test(m.mark)));
  // The password changed: the page says the day it changed.
  const { securityView } = await import("../src/security-board.js");
  const later = Date.now() + 3 * 86400000;
  const changed = await securityView({ ...env, ADMIN_PASSWORD: "a-new-owner-password" }, later);
  assert.match(row(changed, "secret:ADMIN_PASSWORD").state, /에 바꿈$/);
  assert.match(row(changed, "secret:RATE_SALT").state, /처음 본 날/, "the others stay as they were");
  // An old secret: 살펴보기.
  const old = await securityView({ ...env, ADMIN_PASSWORD: "a-new-owner-password" }, later + 400 * 86400000);
  assert.equal(row(old, "secret:ADMIN_PASSWORD").tone, "warn");
  assert.ok(!JSON.stringify(first).includes(env.SEAL_KEY) && !JSON.stringify(first).includes(env.RATE_SALT));
  assert.equal((await call("GET", "/api/admin/security")).status, 401);
});

const PASSWORD_OF = (env) => env.ADMIN_PASSWORD;
