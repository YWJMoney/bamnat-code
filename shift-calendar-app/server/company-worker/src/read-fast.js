// SP1 1.30 [SPD-01][SPD-02][SPD-04][SPD-06] 1.30 묶음 SP1 (사용자 2026-10-27: "여전히 조회하는 로딩이 너무 느리다. 획기적으로
// 속도를 빠르게 할 방법. 아주 중요한 문제"): what the plaza's reads share, kept apart from src/index.js so the bundles editing that
// file the same week touch fewer of its lines. Nothing here reads a table by itself; src/index.js passes what it needs.
//
// tools/bundle.mjs puts this file in place of its import line, so its names share src/index.js's scope: each starts with `sp`.

/**
 * [SPD-02] The D1 reads and writes that go through one D1 session (Sessions API, read replication): the plaza's public lists and
 * a company's pages, and the writes whose answers the app reads again at once. A session reads from the nearest copy of the
 * database when it may, and its bookmark (header `x-d1-bookmark` both ways) makes the next request at least as fresh as this one.
 * The developer app and everything else read the database as before.
 */
export const SP_SESSION_READS = [
  /^\/api\/companies$/,
  /^\/api\/companies\/(top|all)$/,
  /^\/api\/companies\/[A-Za-z0-9]{12}(\/room|\/history)?$/,
  /^\/api\/companies\/([A-Za-z0-9]{12}|style-[a-z]+)\/setups\/(room|[0-9a-f]{16})\/posts$/,
  /^\/api\/(board|plazas|terms|members)$/,
];
export const SP_SESSION_WRITES = [
  /^\/api\/(reports|votes|posts|uses|members|nick)$/,
  /^\/api\/posts\/p[A-Za-z0-9]{15}(\/flag|\/vote)?$/,
  /^\/api\/reports\/[A-Za-z0-9]{22}$/,
];
/** A bookmark as D1 makes one (an opaque word); anything else is ignored, never an error. */
const SP_BOOKMARK = /^[A-Za-z0-9._:-]{1,200}$/;

/** The database a memo of this Worker belongs to: the bound one, also when a request reads it through a session. */
export const spDbKey = (env) => env.DB_ROOT ?? env.DB ?? env;

/**
 * [SPD-02] The request's env with DB as a D1 session, or null when the path does not use one or D1 has no sessions (the tests on
 * Node, an older runtime). Which copy the session starts on:
 * - the app's bookmark, when it sends one: at least as fresh as what it last read or wrote;
 * - else the primary for a write, a read that names a phone (X-Device: an app before 1.30 reads its own vote or post right after
 *   sending it, without a bookmark) or asks past any copy (`Cache-Control: no-cache`, sent after a write);
 * - else any copy (`first-unconstrained`): the same public list for everyone.
 */
export function spSession(env, request, path) {
  const db = env.DB;
  if (!db || typeof db.withSession !== "function") return null;
  const get = request.method === "GET";
  if (!(get ? SP_SESSION_READS : SP_SESSION_WRITES).some((r) => r.test(path))) return null;
  const mark = request.headers.get("x-d1-bookmark");
  const fresh = !get || request.headers.has("x-device") || spNoCache(request);
  const start = fresh ? "first-primary" : "first-unconstrained";
  let session = null;
  try {
    session = db.withSession(get && mark && SP_BOOKMARK.test(mark) ? mark : start);
  } catch {
    // A bookmark this database does not know (another database, a very old one): start as if there were none.
    try {
      session = db.withSession(start);
    } catch {
      return null;
    }
  }
  const served = { region: null, primary: false };
  return { env: { ...env, DB: spWatched(session, served), DB_ROOT: db }, session, served };
}

/** A session that notes where D1 answered from (meta.served_by_region), for the `x-d1-region` header a person can check. */
function spWatched(session, served) {
  const note = (r) => {
    const m = r?.meta;
    if (m?.served_by_region && !served.region) served.region = String(m.served_by_region);
    if (m?.served_by_primary) served.primary = true;
    return r;
  };
  const wrap = (s) => ({
    inner: s,
    bind: (...v) => wrap(s.bind(...v)),
    first: (c) => s.first(c),
    all: async () => note(await s.all()),
    run: async () => note(await s.run()),
    raw: (o) => s.raw(o),
  });
  return {
    prepare: (sql) => wrap(session.prepare(sql)),
    batch: async (list) => (await session.batch(list.map((s) => s.inner ?? s))).map(note),
    getBookmark: () => session.getBookmark?.() ?? null,
  };
}

/** [SPD-02] The answer with the session's bookmark (and where D1 answered from); an answer from the edge's copy as it is. */
export function spBookmarked(res, s) {
  if (!s) return res;
  let mark = null;
  try {
    mark = s.session.getBookmark?.() ?? null;
  } catch {
    // No bookmark: the next request starts as if it had none.
  }
  if (!mark && !s.served.region) return res;
  const set = (r) => {
    if (mark) r.headers.set("x-d1-bookmark", String(mark));
    if (s.served.region) r.headers.set("x-d1-region", `${s.served.region}${s.served.primary ? ";primary" : ""}`);
    return r;
  };
  try {
    return set(res);
  } catch {
    // Headers of an answer from the cache cannot change; its copy's can.
    return set(new Response(res.body, res));
  }
}

/** [SPD-04] The app asks past every kept copy after its own write: `Cache-Control: no-cache`. */
export const spNoCache = (request) => /\bno-cache\b/i.test(request.headers.get("cache-control") ?? "");

/**
 * [SPD-04] A short memo of answers in this Worker's memory: on workers.dev the edge cache (Cache API) keeps nothing, so a busy
 * isolate answers the same public list from here. [keep] maps key → {at, value}; a write through this Worker clears it.
 */
export async function spKept(keep, key, ms, make, fresh = false) {
  const now = Date.now();
  const kept = keep.get(key);
  if (!fresh && kept && now - kept.at < ms) return kept.value;
  const value = await make();
  if (keep.size >= 300) for (const [k, v] of keep) if (now - v.at >= ms) keep.delete(k);
  if (keep.size >= 300) keep.clear();
  keep.set(key, { at: now, value });
  return value;
}

/**
 * [SPD-01] (company, grp) pairs in groups a D1 statement can bind: D1 takes at most 100 values a statement, and the reads ask
 * `company_id IN (…) AND grp IN (…)`, so each group has at most [most] companies and setups together.
 */
export function spPairChunks(pairs, most = 90) {
  const out = [];
  let cur = null;
  const seen = new Set();
  for (const p of pairs) {
    const k = `${p.company}\u0001${p.grp}`;
    if (seen.has(k)) continue;
    seen.add(k);
    const extra = (cur && !cur.companies.has(p.company) ? 1 : 0) + (cur && !cur.grps.has(p.grp) ? 1 : 0);
    if (!cur || cur.companies.size + cur.grps.size + extra > most) {
      cur = { companies: new Set(), grps: new Set() };
      out.push(cur);
    }
    cur.companies.add(p.company);
    cur.grps.add(p.grp);
  }
  return out.map((c) => ({ companies: [...c.companies], grps: [...c.grps] }));
}

/** [SPD-01] The `IN (?, …)` marks of one chunk and its values: companies first as ?1.., then setups. */
export function spInMarks(chunk) {
  const cs = chunk.companies.map((_, i) => `?${i + 1}`).join(", ");
  const gs = chunk.grps.map((_, i) => `?${chunk.companies.length + i + 1}`).join(", ");
  return { cs, gs, values: [...chunk.companies, ...chunk.grps] };
}

/** [SPD-06] A strong ETag for a text: the same text, the same tag. */
export async function spEtag(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return `"${[...new Uint8Array(digest)].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("")}"`;
}

/** [SPD-06] 304 with no body when the app already has this answer (If-None-Match names its ETag), else the answer. */
export function spNotModified(request, res) {
  const tag = res.headers.get("etag");
  const asked = request.headers.get("if-none-match");
  if (res.status !== 200 || !tag || !asked) return res;
  if (!asked.split(",").map((t) => t.trim().replace(/^W\//, "")).includes(tag)) return res;
  const headers = new Headers();
  for (const h of ["etag", "cache-control", "x-d1-bookmark", "x-d1-region"]) if (res.headers.get(h)) headers.set(h, res.headers.get(h));
  return new Response(null, { status: 304, headers });
}
