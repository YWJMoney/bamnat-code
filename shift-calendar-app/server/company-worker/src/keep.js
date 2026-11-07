import { all, audit, json, notifyAdmins, one, randomId, readJson, refuse, run } from "./index.js";
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
  /** 한 번의 매시 작업에서 백업할 표 수와 표당 한 번에 읽는 줄 수. */
  backupTablesPerRun: 12,
  backupPage: 2000,
  /** 백업하지 않는 표: 금방 지워지는 것과 찾기 색인(다시 만들어짐). */
  backupSkip: /^(counters|requests|sqlite_|_cf_|d1_|company_fts)/,
};

export const KEEP_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS data_requests (id TEXT PRIMARY KEY, kind TEXT NOT NULL, mark TEXT NOT NULL DEFAULT '', inquiry_id TEXT, summary TEXT NOT NULL DEFAULT '',
    opened_at INTEGER NOT NULL, due_at INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'open', note TEXT NOT NULL DEFAULT '', by TEXT NOT NULL DEFAULT '',
    closed_at INTEGER, warned TEXT)`,
  "CREATE INDEX IF NOT EXISTS data_requests_open ON data_requests (status, due_at)",
];

const keepClean = (v, n) => String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, n);

export async function requestList(env, now = Date.now(), status = "open") {
  const rows = await all(env, `SELECT * FROM data_requests ${status === "all" ? "" : status === "done" ? "WHERE status <> 'open'" : "WHERE status = 'open'"}
    ORDER BY status = 'open' DESC, due_at ASC LIMIT 200`);
  return {
    kinds: Object.fromEntries(Object.entries(KEEP.kinds).map(([k, v]) => [k, { name: v.name, days: v.days, why: v.why }])),
    items: rows.map((r) => ({ id: r.id, kind: r.kind, name: KEEP.kinds[r.kind]?.name ?? r.kind, mark: r.mark, inquiry: r.inquiry_id, summary: r.summary, openedAt: r.opened_at,
      dueAt: r.due_at, status: r.status, note: r.note, by: r.by, closedAt: r.closed_at, late: r.status === "open" && r.due_at < now,
      soon: r.status === "open" && r.due_at >= now && r.due_at - now < KEEP.warnDays * DAY })),
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

const backupReady = (env) => Boolean(env.BACKUP && typeof env.BACKUP.put === "function");

async function gzip(text) {
  const stream = new Blob([text]).stream().pipeThrough(new CompressionStream("gzip"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

async function readProgress(env) {
  try {
    return JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'backup_progress'"))?.value ?? "null");
  } catch {
    return null;
  }
}

const saveProgress = (env, p) => run(env, "INSERT INTO settings (key, value) VALUES ('backup_progress', ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value", JSON.stringify(p));

/** 매시 작업: 오늘 백업을 시작하거나 이어서 해요. 끝난 날은 아무것도 안 해요. */
export async function backupTick(env, now = Date.now(), { force = false } = {}) {
  if (!backupReady(env)) return { skipped: "no_r2" };
  const day = kstDay(now);
  let p = await readProgress(env);
  const hour = new Date(now + 9 * HOUR).getUTCHours();
  if (!p || p.day !== day) {
    if (!force && hour < KEEP.backupHour) return { skipped: "early" };
    const tables = (await all(env, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")).map((t) => t.name).filter((n) => !KEEP.backupSkip.test(n));
    p = { day, tables, done: [], rows: {}, bytes: 0, startedAt: now, doneAt: null };
  }
  if (p.doneAt) return { skipped: "done" };
  let n = 0;
  for (const t of p.tables) {
    if (p.done.includes(t)) continue;
    if (n >= KEEP.backupTablesPerRun) break;
    if (!/^[A-Za-z0-9_]+$/.test(t)) {
      p.done.push(t);
      continue;
    }
    const lines = [];
    for (let off = 0; ; off += KEEP.backupPage) {
      const rows = await all(env, `SELECT * FROM "${t}" LIMIT ${KEEP.backupPage} OFFSET ${off}`);
      for (const r of rows) lines.push(JSON.stringify(r));
      if (rows.length < KEEP.backupPage) break;
    }
    const body = await gzip(lines.join("\n"));
    await env.BACKUP.put(`d1/${day}/${t}.ndjson.gz`, body, { httpMetadata: { contentType: "application/gzip" } });
    p.done.push(t);
    p.rows[t] = lines.length;
    p.bytes += body.byteLength;
    n += 1;
  }
  if (p.done.length >= p.tables.length) {
    p.doneAt = now;
    await env.BACKUP.put(`d1/${day}/manifest.json`, JSON.stringify({ day, tables: p.rows, bytes: p.bytes, startedAt: p.startedAt, doneAt: now }), {
      httpMetadata: { contentType: "application/json" } });
    // 14일 지난 날짜 폴더를 지워요.
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

export async function backupView(env, now = Date.now()) {
  const p = await readProgress(env);
  const ready = backupReady(env);
  const days = [];
  if (ready) {
    try {
      const list = await env.BACKUP.list({ prefix: "d1/", delimiter: "/" });
      for (const pre of (list.delimitedPrefixes ?? []).sort().reverse().slice(0, 14)) days.push(pre.slice(3, 13));
    } catch {
      // R2를 읽지 못했어요.
    }
  }
  return {
    ready, progress: p ? { day: p.day, done: p.done?.length ?? 0, of: p.tables?.length ?? 0, bytes: p.bytes ?? 0, doneAt: p.doneAt ?? null } : null, days,
    lastOk: p?.doneAt ? p.day : days[0] ?? null, keepDays: KEEP.backupKeepDays, hour: KEEP.backupHour,
    timeTravel: "D1 되돌리기: Cloudflare › D1 › bamnat-company › Time Travel에서 시각을 골라 되돌려요(무료 7일·유료 30일). 명령: npx wrangler d1 time-travel restore bamnat-company --timestamp=<시각>",
    late: ready && (!p || (p.day !== kstDay(now) && new Date(now + 9 * HOUR).getUTCHours() >= KEEP.backupHour + 2) || (p.day === kstDay(now) && !p.doneAt && now - p.startedAt > 6 * HOUR)),
  };
}

/** GET·POST /data-requests…, GET /backup, POST /backup/run. 최고 관리자만(areaFor 기본값). */
export async function keepRoute(request, env, url, path, method, now) {
  if (path === "/data-requests" && method === "GET") return json(await requestList(env, now, url.searchParams.get("status") ?? "open"));
  if (path === "/data-requests" && method === "POST") return makeRequest(request, env, now);
  const m = path.match(/^\/data-requests\/(r[A-Za-z0-9]{15})$/);
  if (m && method === "POST") return closeRequest(request, env, m[1], now);
  if (path === "/backup" && method === "GET") return json(await backupView(env, now));
  if (path === "/backup/run" && method === "POST") {
    if (!backupReady(env)) return json({ error: "no_r2" }, 409);
    const r = await backupTick(env, now, { force: true });
    await audit(env, now, "backup_run", r.day ?? "", `${r.done ?? 0}/${r.of ?? 0}`);
    return json(r);
  }
  return null;
}
