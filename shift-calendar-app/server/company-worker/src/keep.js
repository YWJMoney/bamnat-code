import { all, audit, forgetDevice, json, notifyAdmins, one, randomId, readJson, refuse, run } from "./index.js";
import { passCodeHash } from "./money.js";
import { RETENTION, RETENTION_OF, backupRule, deviceColumns, forgetWords } from "./retention.js";
import { seal, sealFromSecret, unseal } from "./seal.js";
import { DAY, HOUR } from "./units.js";
import { kstDay } from "./usage.js";

// ---- 1.38 [ADM3-16] 백업과 데이터 요청 ----
//
// 보고서 3-3항 M2·M5.
//   데이터 요청  개인정보 열람·정정·삭제·처리정지, 환불, 법·안전 신고를 '법정 기한이 있는 줄'로 따로 둬요. 문의에서 한 번에 만들고, 기한이
//               이틀 안이면 매일 한 번 알려요. 문의·장부와 섞이지 않게 관리 › 데이터 요청에 모여요. 휴대폰은 해시 앞 6자만 적어요.
//   하루 백업    R2 저장소(BACKUP 바인딩)가 있으면 새벽 4시부터 D1의 표를 하나씩 NDJSON.gz로 R2에 두고 14일 지난 것은 지워요. 표가 많으면
//               매시 작업마다 이어서 해요(한 번에 다 하지 않아 정기 작업 시간을 넘지 않게). 없으면 D1 되돌리기(Time Travel) 안내만.

export const KEEP = {
  /** 요청 종류: 이름, 기한(일), 근거. */
  kinds: {
    access: { name: "개인정보 열람", days: 10, why: "개인정보 보호법 제35조, 시행령 제41조: 10일 안에 열람" },
    correct: { name: "개인정보 정정·삭제", days: 10, why: "개인정보 보호법 제36조, 시행령 제43조: 10일 안에 조치 결과 알림" },
    stop: { name: "처리 정지·동의 철회", days: 10, why: "개인정보 보호법 제37조, 시행령 제44조: 10일 안에 조치" },
    refund: { name: "환불·청약 철회", days: 3, why: "전자상거래법 제18조: 3영업일 안에 환급(Play 결제는 Play 정책을 함께 확인)" },
    legal: { name: "법·안전 신고", days: 1, why: "정보통신망법 제44조의2: 권리 침해 신고는 지체 없이 임시 조치" },
    other: { name: "그 밖의 요청", days: 7, why: "운영 기준 7일" },
  },
  warnDays: 2,
  backupHour: 4,
  backupKeepDays: 14,
  /** 한 번의 매시 작업에서 백업할 표 수. 표마다 한 번에 읽는 줄 수·빼는 표·잠그는 칸은 보관 장부(retention.js backupRule)가 정해요. */
  backupTablesPerRun: 12,
  /** 2.0.0 [RVQ-16] 검토 SRV-15: 조각 하나(압축 전 글자 수)와 한 번의 매시 작업에서 쓰는 양. 사진 표가 커도 메모리·시간을 넘지 않게. */
  partBytes: 8 * 1024 * 1024,
  runBytes: 24 * 1024 * 1024,
  /** 검토 U21-08 되살리기 연습: 한 번에 읽는 양, 며칠마다, 표마다 열어 보는 잠근 칸 수. */
  drillBytes: 32 * 1024 * 1024,
  drillEveryDays: 7,
  drillSealedPerTable: 3,
};

export const KEEP_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS data_requests (id TEXT PRIMARY KEY, kind TEXT NOT NULL, mark TEXT NOT NULL DEFAULT '', inquiry_id TEXT, summary TEXT NOT NULL DEFAULT '',
    opened_at INTEGER NOT NULL, due_at INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'open', note TEXT NOT NULL DEFAULT '', by TEXT NOT NULL DEFAULT '',
    closed_at INTEGER, warned TEXT)`,
  "CREATE INDEX IF NOT EXISTS data_requests_open ON data_requests (status, due_at)",
];

const keepClean = (v, n) => String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, n);

/** The proof list kept on a request (newest last), or []. */
function proofOf(text) {
  try {
    const v = JSON.parse(text ?? "[]");
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}

export async function requestList(env, now = Date.now(), status = "open") {
  const rows = await all(env, `SELECT * FROM data_requests ${status === "all" ? "" : status === "done" ? "WHERE status <> 'open'" : "WHERE status = 'open'"}
    ORDER BY status = 'open' DESC, due_at ASC LIMIT 200`);
  return {
    kinds: Object.fromEntries(Object.entries(KEEP.kinds).map(([k, v]) => [k, { name: v.name, days: v.days, why: v.why }])),
    items: rows.map((r) => ({ id: r.id, kind: r.kind, name: KEEP.kinds[r.kind]?.name ?? r.kind, mark: r.mark, inquiry: r.inquiry_id, summary: r.summary, openedAt: r.opened_at,
      dueAt: r.due_at, status: r.status, note: r.note, by: r.by, closedAt: r.closed_at, late: r.status === "open" && r.due_at < now,
      soon: r.status === "open" && r.due_at >= now && r.due_at - now < KEEP.warnDays * DAY,
      // 2.0.0 [RVQ-17] 검토 U21-07: 대신 할 수 있는 요청인지와 남긴 증빙.
      runnable: ["access", "correct", "stop", "other"].includes(r.kind), proof: proofOf(r.proof) })),
    open: rows.filter((r) => r.status === "open").length,
    late: rows.filter((r) => r.status === "open" && r.due_at < now).length,
  };
}

async function makeRequest(request, env, now) {
  const body = await readJson(request, 4096);
  const kind = KEEP.kinds[body?.kind] ? body.kind : null;
  if (!kind) refuse("kind", "kind");
  let mark = /^[0-9a-f]{6}$/.test(body?.mark ?? "") ? body.mark : "";
  let inquiry = null;
  if (typeof body?.inquiry === "string" && /^[A-Za-z0-9]{8,32}$/.test(body.inquiry)) {
    const q = await one(env, "SELECT id, device FROM inquiries WHERE id = ?", body.inquiry);
    if (!q) refuse("inquiry", "inquiry");
    inquiry = q.id;
    mark ||= String(q.device ?? "").slice(0, 6);
  }
  const id = `r${randomId(15)}`;
  const opened = Number.isFinite(Date.parse(body?.openedAt ?? "")) ? Math.min(now, Date.parse(body.openedAt)) : now;
  await run(env, "INSERT INTO data_requests (id, kind, mark, inquiry_id, summary, opened_at, due_at, by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    id, kind, mark, inquiry, keepClean(body?.summary, 300), opened, opened + KEEP.kinds[kind].days * DAY, env.actor?.name ?? "관리자");
  await audit(env, now, "data_request", id, `${KEEP.kinds[kind].name}${inquiry ? ` · 문의 ${inquiry}` : ""}`);
  return json({ id });
}

async function closeRequest(request, env, id, now) {
  const body = await readJson(request, 4096);
  const status = ["done", "refused", "open"].includes(body?.status) ? body.status : null;
  if (!status) refuse("status", "status");
  const note = keepClean(body?.note, 1000);
  if (status !== "open" && !note) refuse("note", "note");
  const r = await run(env, "UPDATE data_requests SET status = ?, note = ?, closed_at = ?, by = ? WHERE id = ?", status, note, status === "open" ? null : now,
    env.actor?.name ?? "관리자", id);
  if (!r?.meta?.changes) return json({ error: "not_found" }, 404);
  await audit(env, now, "data_request_close", id, `${status} ${note.slice(0, 80)}`);
  return json({ ok: true });
}

/** 매시 작업: 기한이 이틀 안이거나 넘은 요청을 하루 한 번 알려요. */
export async function requestTick(env, now = Date.now()) {
  const day = kstDay(now);
  const rows = await all(env, "SELECT id, kind, due_at FROM data_requests WHERE status = 'open' AND due_at < ? AND (warned IS NULL OR warned <> ?)", now + KEEP.warnDays * DAY, day);
  if (!rows.length) return 0;
  const late = rows.filter((r) => r.due_at < now).length;
  await notifyAdmins(env, "requests", { title: "데이터 요청 기한", body: `${rows.length}건 기한이 ${late ? `지났거나(${late}건) ` : ""}가까워요`, url: "/admin/#/requests", urgent: late > 0 }, now);
  await env.DB.batch(rows.map((r) => env.DB.prepare("UPDATE data_requests SET warned = ? WHERE id = ?").bind(day, r.id)));
  return rows.length;
}

// ---- 하루 백업 ----
//
// 2.0.0 [RVQ-16] 검토 SRV-15·U21-08: 표마다 할 일은 보관 장부(retention.js backupRule)가 정해요. 곧 지워지거나 다시 만들 수 있는 표는 빼고,
// 비밀 칸(관리자 비밀번호 해시·알림 토큰·구매 토큰·복구 코드 해시·운영 열쇠 해시·관리자 웹 푸시 주소)은 서버 비밀값으로 잠가서 R2만 새어서는
// 열 수 없게 해요(2단계 키는 D1에서부터 잠겨 있어요). 표를 KEEP.partBytes마다 조각으로 나눠 쓰고(사진처럼 큰 줄이 많아도 메모리를 넘지
// 않게), 한 번의 매시 작업에서는 KEEP.runBytes까지만 쓰고 다음 시간에 그 자리부터 이어 가요.
// 되살리기 연습: 마지막으로 끝난 날의 조각을 모두 다시 읽어 줄마다 JSON이 열리는지, 줄 수가 목록과 같은지, 잠근 칸이 지금 열쇠로 열리는지
// 보고 오늘 DB 줄 수와 나란히 적어요. 7일마다 매시 작업이 스스로 하고, 관리 › 백업에서 지금 할 수도 있어요. 실제로 D1에 되살릴 때는
// tools/backup-open.mjs(내려받은 날 폴더 → SQL)를 써요.

const backupReady = (env) => Boolean(env.BACKUP && typeof env.BACKUP.put === "function");

async function gzip(text) {
  const stream = new Blob([text]).stream().pipeThrough(new CompressionStream("gzip"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

async function gunzip(body) {
  return new Response(body.pipeThrough(new DecompressionStream("gzip"))).text();
}

async function keepSetting(env, key) {
  try {
    return JSON.parse((await one(env, "SELECT value FROM settings WHERE key = ?", key))?.value ?? "null");
  } catch {
    return null;
  }
}

const keepSave = (env, key, v) => run(env, "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value", key, JSON.stringify(v));
const readProgress = (env) => keepSetting(env, "backup_progress");
const saveProgress = (env, p) => keepSave(env, "backup_progress", p);

/** The R2 key of a table's part (1, 2, …). */
export const backupKey = (day, table, part) => `d1/${day}/${table}.${part}.ndjson.gz`;

/** [row] as the backup keeps it: the rule's cells sealed with the server's secret (only on the rows its [where] picks). */
async function backupRow(env, row, rule) {
  if (!rule.seal.length) return row;
  if (rule.where && !Object.entries(rule.where).every(([c, vs]) => vs.includes(row[c]))) return row;
  const out = { ...row };
  for (const c of rule.seal) if (out[c] !== null && out[c] !== undefined) out[c] = { $sealed: await seal(env, "backup", String(out[c])) };
  return out;
}

/** The tables in D1 that have a name the backup can write, with what the ledger says about each. */
async function backupTables(env) {
  return (await all(env, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")).map((t) => t.name).filter((n) => /^[A-Za-z0-9_]+$/.test(n))
    .map((n) => ({ name: n, rule: backupRule(n) }));
}

/** 매시 작업: 오늘 백업을 시작하거나 이어서 해요. 끝난 날은 되살리기 연습 차례인지 봐요. */
export async function backupTick(env, now = Date.now(), { force = false } = {}) {
  if (!backupReady(env)) return { skipped: "no_r2" };
  const day = kstDay(now);
  let p = await readProgress(env);
  const hour = new Date(now + 9 * HOUR).getUTCHours();
  // 2.0.0 전에 시작한 오늘 백업(조각·잠금 없이 쓰던 것)은 처음부터 다시 해요.
  if (!p || p.day !== day || (!p.doneAt && !p.parts)) {
    if (!force && hour < KEEP.backupHour) return { skipped: "early" };
    const tables = (await backupTables(env)).filter((t) => !t.rule.skip).map((t) => t.name);
    p = { day, v: 2, tables, done: [], rows: {}, parts: {}, sealed: {}, bytes: 0, cur: null, startedAt: now, doneAt: null };
  }
  if (p.doneAt) {
    const drill = await drillTick(env, now).catch((e) => ({ error: String(e?.message ?? e).slice(0, 120) }));
    return { skipped: "done", drill };
  }
  let tables = 0;
  let written = 0;
  for (const t of p.tables) {
    if (p.done.includes(t)) continue;
    if (tables >= KEEP.backupTablesPerRun || written >= KEEP.runBytes) break;
    const rule = backupRule(t);
    const cur = p.cur?.t === t ? p.cur : { t, off: 0, part: 0, rows: 0 };
    let lines = [];
    let size = 0;
    const flush = async () => {
      if (!lines.length && cur.part > 0) return;
      cur.part += 1;
      const body = await gzip(lines.join("\n"));
      await env.BACKUP.put(backupKey(day, t, cur.part), body, { httpMetadata: { contentType: "application/gzip" } });
      p.bytes += body.byteLength;
      written += size;
      lines = [];
      size = 0;
    };
    let finished = false;
    for (;;) {
      const rows = await all(env, `SELECT * FROM "${t}" LIMIT ${rule.page} OFFSET ${cur.off}`);
      for (const r of rows) {
        const line = JSON.stringify(await backupRow(env, r, rule));
        lines.push(line);
        size += line.length;
        // 조각은 줄 사이에서 끊어요. 끊어 쓴 조각은 그대로 두고, 이 시간에 다 못 읽으면 다음 시간에 cur.off(쪽의 처음)부터예요.
        if (size >= KEEP.partBytes) await flush();
      }
      cur.off += rows.length;
      cur.rows += rows.length;
      if (rows.length < rule.page) {
        finished = true;
        break;
      }
      if (written >= KEEP.runBytes) break;
    }
    if (finished) {
      await flush();
      p.done.push(t);
      p.rows[t] = cur.rows;
      p.parts[t] = cur.part;
      if (rule.seal.length) p.sealed[t] = rule.seal;
      p.cur = null;
      tables += 1;
    } else {
      // 이 표는 다음 시간에 cur.off부터(이미 쓴 조각은 그대로).
      if (lines.length) await flush();
      p.cur = cur;
      break;
    }
  }
  if (p.done.length >= p.tables.length) {
    p.doneAt = now;
    await env.BACKUP.put(`d1/${day}/manifest.json`, JSON.stringify({ day, v: 2, tables: p.rows, parts: p.parts, sealed: p.sealed, bytes: p.bytes,
      startedAt: p.startedAt, doneAt: now }), { httpMetadata: { contentType: "application/json" } });
    // 14일 지난 날짜 폴더를 지워요(2.0.0 전의 잠그지 않은 백업도 이렇게 저절로 사라져요).
    const old = kstDay(now - KEEP.backupKeepDays * DAY);
    let cursor;
    do {
      const list = await env.BACKUP.list({ prefix: "d1/", cursor });
      const gone = list.objects.map((o) => o.key).filter((k) => k.slice(3, 13) < old);
      if (gone.length) await env.BACKUP.delete(gone);
      cursor = list.truncated ? list.cursor : undefined;
    } while (cursor);
  }
  await saveProgress(env, p);
  return { day, done: p.done.length, of: p.tables.length };
}

/** The backup days in R2, newest first. */
async function backupDays(env) {
  const days = [];
  try {
    const list = await env.BACKUP.list({ prefix: "d1/", delimiter: "/" });
    for (const pre of (list.delimitedPrefixes ?? []).sort().reverse().slice(0, KEEP.backupKeepDays)) days.push(pre.slice(3, 13));
  } catch {
    // R2를 읽지 못했어요.
  }
  return days;
}

/**
 * U21-08 되살리기 연습 한 걸음: 시작하지 않았으면 마지막으로 끝난 날(목록이 있는 날)을 고르고, 표를 차례로 KEEP.drillBytes까지 읽어요.
 * 끝낸 연습이 KEEP.drillEveryDays 안이면 [force]가 아닌 한 하지 않아요. D1에는 아무것도 쓰지 않아요(읽어서 세기만).
 */
export async function drillTick(env, now = Date.now(), { force = false } = {}) {
  if (!backupReady(env)) return { skipped: "no_r2" };
  let d = await keepSetting(env, "backup_drill");
  const fresh = Boolean(d?.doneAt) && now - d.doneAt < KEEP.drillEveryDays * DAY;
  if (!d || (d.doneAt && (force || !fresh))) {
    let manifest = null;
    for (const day of await backupDays(env)) {
      const m = await env.BACKUP.get(`d1/${day}/manifest.json`);
      if (m) {
        manifest = JSON.parse(await m.text());
        break;
      }
    }
    if (!manifest) return { skipped: "no_backup" };
    d = { day: manifest.day, startedAt: now, doneAt: null, queue: Object.keys(manifest.tables ?? {}).sort(), manifest: { tables: manifest.tables ?? {},
      parts: manifest.parts ?? {}, sealed: manifest.sealed ?? {} }, tables: {}, bytes: 0, by: env.actor?.name ?? "" };
  }
  if (d.doneAt) return { skipped: "fresh" };
  let read = 0;
  while (d.queue.length && read < KEEP.drillBytes) {
    const t = d.queue.shift();
    const want = d.manifest.tables[t] ?? 0;
    // 2.0.0 전 목록에는 조각 수가 없어요: 표 하나가 파일 하나.
    const parts = d.manifest.parts[t] ?? 0;
    const sealedCols = d.manifest.sealed[t] ?? [];
    const x = { rows: 0, want, db: -1, bad: 0, missing: 0, sealed: 0, checked: 0, opened: 0 };
    try {
      for (let part = 1; part <= Math.max(parts, 1); part++) {
        const obj = await env.BACKUP.get(parts ? backupKey(d.day, t, part) : `d1/${d.day}/${t}.ndjson.gz`);
        if (!obj) {
          x.missing += 1;
          continue;
        }
        const text = await gunzip(obj.body);
        read += text.length;
        for (const line of text ? text.split("\n") : []) {
          let row;
          try {
            row = JSON.parse(line);
          } catch {
            x.bad += 1;
            continue;
          }
          x.rows += 1;
          for (const c of sealedCols) {
            const v = row?.[c];
            if (!v || typeof v !== "object" || typeof v.$sealed !== "string") continue;
            x.sealed += 1;
            // 칸마다 다 열어 보면 오래 걸려요: 표마다 처음 몇 칸만 열어 봐요.
            if (x.checked >= KEEP.drillSealedPerTable) continue;
            x.checked += 1;
            if ((await unseal(env, "backup", v.$sealed)) !== null) x.opened += 1;
          }
        }
      }
    } catch (e) {
      x.error = String(e?.message ?? e).slice(0, 80);
    }
    x.db = Number((await one(env, `SELECT COUNT(*) AS n FROM "${t}"`).catch(() => null))?.n ?? -1);
    d.tables[t] = x;
  }
  d.bytes += read;
  if (!d.queue.length) {
    d.doneAt = now;
    d.ok = Object.values(d.tables).every((x) => !drillProblem(x));
    const n = Object.keys(d.tables).length;
    await audit(env, now, "backup_drill", d.day, `${n}개 표 · ${d.ok ? "모두 맞음" : "문제 있음"}`);
    if (!d.ok) await notifyAdmins(env, "ops", { title: "백업 되살리기 연습", body: `${d.day} 백업에 문제가 있어요`, url: "/admin/#/backup", urgent: false }, now);
  }
  await keepSave(env, "backup_drill", d);
  return { day: d.day, left: d.queue.length, done: Boolean(d.doneAt), ok: d.doneAt ? d.ok : null };
}

/** What is wrong with one table in the drill, in the admin app's words, or null. */
function drillProblem(x) {
  if (x.error) return "읽다가 멈춤";
  if (x.missing) return "조각이 없음";
  if (x.bad) return "열리지 않는 줄";
  if (x.rows !== x.want) return "줄 수가 목록과 다름";
  if (x.opened !== x.checked) return "잠근 칸이 지금 열쇠로 안 열림";
  return null;
}

/** 되살리기 연습 결과를 화면용으로: 표마다 백업 줄·목록 줄·오늘 DB 줄, 문제와 크게 줄어든 표. */
function drillView(d) {
  if (!d) return null;
  const rows = Object.entries(d.tables ?? {}).map(([t, x]) => ({ table: t, rows: x.rows, want: x.want, db: x.db, sealed: x.sealed, checked: x.checked ?? 0,
    opened: x.opened, problem: drillProblem({ checked: 0, ...x }),
    // 백업 뒤 하루 안에 절반 넘게 줄었으면 지워진 것일 수 있어요(문제는 아니고 살펴볼 것).
    shrunk: x.db >= 0 && x.want > 20 && x.db < x.want / 2 }));
  return { day: d.day, startedAt: d.startedAt, doneAt: d.doneAt, ok: d.doneAt ? !rows.some((r) => r.problem) : null, left: d.queue?.length ?? 0,
    tables: rows.length, rowsTotal: rows.reduce((n, r) => n + r.rows, 0), sealed: rows.reduce((n, r) => n + r.sealed, 0),
    checked: rows.reduce((n, r) => n + r.checked, 0), problems: rows.filter((r) => r.problem), shrunk: rows.filter((r) => r.shrunk && !r.problem), rows };
}

/** Which secret seals the backup now, by name (never its value). */
const sealKeyName = (env) => (typeof env.SEAL_KEY === "string" && env.SEAL_KEY.length >= 16 ? "SEAL_KEY" : sealFromSecret(env) ? "RATE_SALT" : "local");

export async function backupView(env, now = Date.now()) {
  const p = await readProgress(env);
  const ready = backupReady(env);
  const days = ready ? await backupDays(env) : [];
  const tables = await backupTables(env);
  const drill = drillView(await keepSetting(env, "backup_drill"));
  return {
    ready, progress: p ? { day: p.day, done: p.done?.length ?? 0, of: p.tables?.length ?? 0, bytes: p.bytes ?? 0, doneAt: p.doneAt ?? null } : null, days,
    lastOk: p?.doneAt ? p.day : days[0] ?? null, keepDays: KEEP.backupKeepDays, hour: KEEP.backupHour,
    // 2.0.0 [RVQ-16] 검토 SRV-15: 무엇을 빼고 무엇을 잠그는지, 어느 비밀값으로 잠그는지(값은 보이지 않아요).
    skipped: tables.filter((t) => t.rule.skip && !/^(sqlite_|_cf_|d1_|company_fts_)/.test(t.name)).map((t) => t.name),
    sealed: Object.fromEntries(tables.filter((t) => !t.rule.skip && t.rule.seal.length).map((t) => [t.name, t.rule.seal])),
    sealKey: sealKeyName(env),
    drill, drillEveryDays: KEEP.drillEveryDays,
    drillLate: ready && Boolean(p?.doneAt || days.length) && (!drill?.doneAt || now - drill.doneAt > (KEEP.drillEveryDays + 1) * DAY),
    timeTravel: "D1 되돌리기: Cloudflare › D1 › bamnat-company › Time Travel에서 시각을 골라 되돌려요(무료 7일·유료 30일). 명령: npx wrangler d1 time-travel restore bamnat-company --timestamp=<시각>",
    restore: "R2 백업으로 되살리기: R2에서 그날 폴더(d1/날짜/)를 내려받아 node tools/backup-open.mjs <폴더> > restore.sql (잠근 칸을 열려면 그 날 잠근 비밀값을 환경 변수 BAMNAT_BACKUP_SECRET에), 그다음 로컬 서버에서 먼저 확인하고 npx wrangler d1 execute로 넣어요.",
    late: ready && (!p || (p.day !== kstDay(now) && new Date(now + 9 * HOUR).getUTCHours() >= KEEP.backupHour + 2) || (p.day === kstDay(now) && !p.doneAt && now - p.startedAt > 6 * HOUR)),
  };
}

// ---- 2.0.0 [RVQ-17] 검토 U21-07·I21b-02: 데이터 요청 대행 ----
//
// 앱을 지운 사람도 법정 기한 안에 처리할 수 있게, 요청에 묶인 문의(그 설치에서 보낸 것)나 복구 코드로 설치 해시를 찾아 서버에서 바로 해요.
//   지우기  앱의 '모두 지우기'와 같은 일(forgetDevice). 답을 보낼 그 문의 하나만 남기고, 지운 표·줄 수와 남은 것(법정 기간·진행 중 제재)을 증빙으로.
//   열람    같은 해시로 가진 것을 JSON으로(비밀 칸과 사진 같은 큰 칸은 빼고 몇 자였는지만). 내보낸 표·줄 수를 증빙으로.
// 증빙은 data_requests.proof(JSON 배열, 최근 5번)에 남고 기록(audit)에도 남아요. 복구 코드는 해시를 찾는 데만 쓰고 저장하지 않아요.

/** The install hash a request acts on: its question's, or the one a recovery code points to. */
async function requestDevice(env, req, code) {
  if (typeof code === "string" && code.trim()) {
    const row = await one(env, "SELECT device FROM pass_codes WHERE hash = ?", await passCodeHash(code));
    return row?.device ?? null;
  }
  if (!req.inquiry_id) return null;
  return (await one(env, "SELECT device FROM inquiries WHERE id = ?", req.inquiry_id))?.device ?? null;
}

const EXPORT_CELL = 4000;

/** 열람: 이 설치가 서버에 가진 것(장부에서 설치 해시가 든 표마다, 문의는 글과 답까지). */
export async function deviceExport(env, dev) {
  const out = {};
  for (const r of RETENTION) {
    const cols = deviceColumns(r);
    if (!cols.length) continue;
    const where = cols.map(([c, a]) => (a === "contains" ? `instr(${c}, ?) > 0` : `${c} = ?`)).join(" OR ");
    const rows = await all(env, `SELECT * FROM ${r.table} WHERE ${where} LIMIT 2000`, ...cols.map(() => dev)).catch(() => []);
    if (!rows.length) continue;
    const secret = new Set([...(r.backup?.seal ?? []), "pw", "token", "hash"]);
    out[r.table] = rows.map((row) => Object.fromEntries(Object.entries(row).map(([k, v]) => {
      if (secret.has(k) && v !== null) return [k, "(보안 정보라 빼고 보내요)"];
      if (typeof v === "string" && v.length > EXPORT_CELL) return [k, `(큰 자료 ${Math.round(v.length / 1024)}KB, 따로 요청하면 보내요)`];
      if (cols.some(([c]) => c === k) && typeof v === "string" && v.includes(dev)) return [k, v.replaceAll(dev, "이 설치")];
      return [k, v];
    })));
  }
  const asks = (out.inquiries ?? []).map((q) => q.id);
  if (asks.length) {
    out.inquiry_messages = await all(env, `SELECT inquiry_id, sender, text, at FROM inquiry_messages WHERE inquiry_id IN (${asks.map(() => "?").join(", ")}) ORDER BY at LIMIT 2000`,
      ...asks).catch(() => []);
  }
  return out;
}

async function runRequest(request, env, id, now) {
  const body = await readJson(request, 4096);
  const what = ["forget", "export"].includes(body?.do) ? body.do : null;
  if (!what) refuse("do", "do");
  const req = await one(env, "SELECT * FROM data_requests WHERE id = ?", id);
  if (!req) return json({ error: "not_found" }, 404);
  if (req.status !== "open") return json({ error: "closed" }, 409);
  if (!["access", "correct", "stop", "other"].includes(req.kind)) return json({ error: "kind" }, 409);
  const dev = await requestDevice(env, req, body?.code);
  if (!dev) return json({ error: body?.code ? "code" : "no_device" }, 409);
  // 되돌릴 수 없는 일이라 화면에서 한 번 더 물은 뒤에만.
  if (what === "forget" && body?.confirm !== true) return json({ error: "confirm" }, 409);
  const by = env.actor?.name ?? "관리자";
  let proof;
  let file = null;
  if (what === "forget") {
    const r = await forgetDevice(env, dev, { keepInquiry: req.inquiry_id, count: true });
    // 남은 줄마다 왜 남았는지(요청 문의는 답을 보내려고, 나머지는 장부의 말: 법정 기간·진행 중 제재·연결만 끊음).
    const leftWhy = Object.fromEntries(Object.keys(r.proof.left).map((t) => [t, t === "inquiries" && req.inquiry_id ? "요청 문의(답을 보내려고)"
      : forgetWords(RETENTION_OF.get(t)).replace(/`/g, "")]));
    proof = { do: "forget", at: now, by, via: body?.code ? "code" : "inquiry", gone: r.proof.gone, left: r.proof.left, leftWhy, inquiryKept: req.inquiry_id ? 1 : 0 };
  } else {
    file = await deviceExport(env, dev);
    proof = { do: "export", at: now, by, via: body?.code ? "code" : "inquiry", tables: Object.fromEntries(Object.entries(file).map(([t, rows]) => [t, rows.length])) };
  }
  const list = [...proofOf(req.proof), proof].slice(-5);
  await run(env, "UPDATE data_requests SET proof = ? WHERE id = ?", JSON.stringify(list), id);
  const n = (o) => Object.values(o ?? {}).reduce((a, b) => a + b, 0);
  await audit(env, now, "data_request_run", id, what === "forget" ? `지움 ${Object.keys(proof.gone).length}개 표 ${n(proof.gone)}줄 · 남음 ${n(proof.left)}줄`
    : `열람 ${Object.keys(proof.tables).length}개 표 ${n(proof.tables)}줄`);
  return json({ ok: true, proof, file: file ? { made: new Date(now).toISOString(), request: id, data: file } : null });
}

/** GET·POST /data-requests…(…/run 대행), GET /backup, POST /backup/run, POST /backup/drill. 최고 관리자만(areaFor 기본값). */
export async function keepRoute(request, env, url, path, method, now) {
  if (path === "/data-requests" && method === "GET") return json(await requestList(env, now, url.searchParams.get("status") ?? "open"));
  if (path === "/data-requests" && method === "POST") return makeRequest(request, env, now);
  const m = path.match(/^\/data-requests\/(r[A-Za-z0-9]{15})$/);
  if (m && method === "POST") return closeRequest(request, env, m[1], now);
  // 2.0.0 [RVQ-17] 검토 U21-07·I21b-02: 지우기·열람을 서버에서 대신 하고 증빙을 남겨요.
  const mr = path.match(/^\/data-requests\/(r[A-Za-z0-9]{15})\/run$/);
  if (mr && method === "POST") return runRequest(request, env, mr[1], now);
  if (path === "/backup" && method === "GET") return json(await backupView(env, now));
  // 검토 U21-08: 되살리기 연습을 지금(마지막으로 끝난 날로 새로 시작하거나, 하던 것을 이어서).
  if (path === "/backup/drill" && method === "POST") {
    if (!backupReady(env)) return json({ error: "no_r2" }, 409);
    const r = await drillTick(env, now, { force: true });
    if (r.skipped === "no_backup") return json({ error: "no_backup" }, 409);
    await audit(env, now, "backup_drill_run", r.day ?? "", r.done ? (r.ok ? "모두 맞음" : "문제 있음") : `남은 표 ${r.left ?? 0}`);
    return json(r);
  }
  if (path === "/backup/run" && method === "POST") {
    if (!backupReady(env)) return json({ error: "no_r2" }, 409);
    const r = await backupTick(env, now, { force: true });
    await audit(env, now, "backup_run", r.day ?? "", `${r.done ?? 0}/${r.of ?? 0}`);
    return json(r);
  }
  return null;
}
