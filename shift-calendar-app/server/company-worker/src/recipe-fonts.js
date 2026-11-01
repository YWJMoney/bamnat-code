// 1.36 [MAT-06] 내 글꼴 나누기: a font the user brought (TTF·OTF) that its maker lets others use (OFL·Apache·무료 공개, the
// user says so), kept so whoever takes a recipe naming it can draw it too. Not looked at first (a font is not a picture of
// anyone); others can report it, and three reports from three phones take it down until the operator looks.
//
// A font's id is "u" + the first 16 hex of the SHA-256 of its bytes, the same id the phone gave it, so a recipe names a font by
// what it is and the same file sent twice is one font. D1 keeps at most 2 MB in a value, so the bytes are kept in parts.
import { DAY } from "./units.js";

export const FONT_LIMITS = { bytes: 8 * 1024 * 1024, perDay: 6, part: 1_500_000, reportsToHide: 3, name: 30 };
/** What the user says its maker allows; only these may be shared. */
export const FONT_LICENSES = ["ofl", "apache", "free"];
export const FONT_LICENSE_NAMES = { ofl: "SIL 오픈 폰트 라이선스(OFL)", apache: "Apache 라이선스", free: "누구나 무료로 쓰게 공개" };

export const FONT_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS recipe_fonts (id TEXT PRIMARY KEY, owner TEXT NOT NULL, name TEXT NOT NULL, license TEXT NOT NULL,
    bytes INTEGER NOT NULL, parts INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'ok', reports INTEGER NOT NULL DEFAULT 0,
    at INTEGER NOT NULL, decided_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS recipe_fonts_state ON recipe_fonts (state, at)",
  `CREATE TABLE IF NOT EXISTS recipe_font_parts (id TEXT NOT NULL, part INTEGER NOT NULL, data TEXT NOT NULL, PRIMARY KEY (id, part))`,
  `CREATE TABLE IF NOT EXISTS recipe_font_reports (id TEXT NOT NULL, device TEXT NOT NULL, why TEXT, at INTEGER NOT NULL, PRIMARY KEY (id, device))`,
];

const RF_ID = /^u[0-9a-f]{16}$/;

/** TrueType (00 01 00 00, 'true'), OpenType ('OTTO') or a collection ('ttcf'), as the phone checks too. */
export function looksLikeFont(bytes) {
  if (!bytes || bytes.length < 12) return false;
  const head = String.fromCharCode(...bytes.slice(0, 4));
  return head === "OTTO" || head === "true" || head === "ttcf" || (bytes[0] === 0 && bytes[1] === 1 && bytes[2] === 0 && bytes[3] === 0);
}

function rfBytes(s) {
  try {
    const bin = atob(s);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  } catch {
    return null;
  }
}

async function rfIdOf(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return "u" + [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}

async function rfDev(d, request, body) {
  const id = d.deviceId(body?.device ?? request.headers.get("x-device"));
  return id ? d.deviceHash(id) : null;
}

function rfName(v) {
  const s = typeof v === "string" ? v.replace(/[\u0000-\u001f\u007f<>]/g, "").trim() : "";
  return [...s].slice(0, FONT_LIMITS.name).join("") || "내 글꼴";
}

/** POST /api/recipes/fonts — {device, name, license, ttf}: one open font, at most 8 MB. Answers its id (the same as the phone's). */
async function rfPost(d, env, request) {
  if (await d.paused(env, "PLAZA_WRITE")) return d.json({ error: "paused" }, 503);
  const body = await d.readJson(request, Math.ceil(FONT_LIMITS.bytes * 1.37) + 1024);
  const dev = await rfDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.publish", now))) return d.json({ error: "not_yet", field: "recipe.publish" }, 403);
  if (!FONT_LICENSES.includes(body.license)) return d.json({ error: "license", field: "license" }, 400);
  const bytes = typeof body.ttf === "string" ? rfBytes(body.ttf) : null;
  if (!bytes || !looksLikeFont(bytes)) return d.json({ error: "font", field: "ttf" }, 400);
  if (bytes.length > FONT_LIMITS.bytes) return d.json({ error: "too_big", field: "ttf" }, 413);
  const id = await rfIdOf(bytes);
  if (body.id != null && body.id !== id) return d.json({ error: "id", field: "id" }, 400);
  const had = await d.one(env, "SELECT id, state FROM recipe_fonts WHERE id = ?", id);
  if (had) return d.json({ id, state: had.state === "ok" ? "ok" : "hidden", again: true }, 200);
  const hit = await d.overLimit(env, now, [["device", `rfont|${dev}`, FONT_LIMITS.perDay, DAY]]);
  if (hit) return hit;
  const parts = [];
  for (let i = 0; i < body.ttf.length; i += FONT_LIMITS.part) parts.push(body.ttf.slice(i, i + FONT_LIMITS.part));
  await d.batch(env, [
    d.stmt(env, "INSERT INTO recipe_fonts (id, owner, name, license, bytes, parts, at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      id, dev, rfName(body.name), body.license, bytes.length, parts.length, now),
    ...parts.map((p, i) => d.stmt(env, "INSERT INTO recipe_font_parts (id, part, data) VALUES (?, ?, ?)", id, i, p)),
  ]);
  return d.json({ id, state: "ok" }, 201);
}

/** GET /api/recipes/fonts/:id — its name, licence and size; a font taken down is gone for all but its owner. */
async function rfMeta(d, env, request, id) {
  const f = await d.one(env, "SELECT id, owner, name, license, bytes, state FROM recipe_fonts WHERE id = ?", id);
  if (!f) return d.json({ error: "not_found" }, 404);
  if (f.state !== "ok" && (await rfDev(d, request, null)) !== f.owner) return d.json({ error: "not_found", state: "hidden" }, 404);
  return d.json({ id: f.id, name: f.name, license: f.license, licenseName: FONT_LICENSE_NAMES[f.license], bytes: f.bytes, state: f.state });
}

/** GET /api/recipes/fonts/:id/file — the font itself. */
async function rfFile(d, env, request, id) {
  const f = await d.one(env, "SELECT id, owner, state, parts FROM recipe_fonts WHERE id = ?", id);
  if (!f) return d.json({ error: "not_found" }, 404);
  if (f.state !== "ok" && (await rfDev(d, request, null)) !== f.owner) return d.json({ error: "not_found" }, 404);
  const rows = await d.all(env, "SELECT data FROM recipe_font_parts WHERE id = ? ORDER BY part", id);
  if (rows.length !== f.parts) return d.json({ error: "not_found" }, 404);
  const bytes = rfBytes(rows.map((r) => r.data).join(""));
  return new Response(bytes, {
    headers: { "content-type": "font/ttf", "cache-control": f.state === "ok" ? "public, max-age=86400" : "no-store", "x-content-type-options": "nosniff" },
  });
}

/** POST /api/recipes/fonts/:id/report — {device, why}: once a phone; three phones take it down until the operator looks. */
async function rfReport(d, env, request, id) {
  const body = await d.readJson(request, 1024);
  const dev = await rfDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const f = await d.one(env, "SELECT id, owner, state, reports FROM recipe_fonts WHERE id = ?", id);
  if (!f) return d.json({ error: "not_found" }, 404);
  if (f.owner === dev) return d.json({ error: "own" }, 400);
  const why = ["license", "broken", "bad"].includes(body.why) ? body.why : "license";
  const now = Date.now();
  const had = await d.one(env, "SELECT id FROM recipe_font_reports WHERE id = ? AND device = ?", id, dev);
  if (had) return d.json({ id, reported: true, again: true });
  const reports = f.reports + 1;
  const hide = f.state === "ok" && reports >= FONT_LIMITS.reportsToHide;
  await d.batch(env, [
    d.stmt(env, "INSERT INTO recipe_font_reports (id, device, why, at) VALUES (?, ?, ?, ?)", id, dev, why, now),
    d.stmt(env, `UPDATE recipe_fonts SET reports = ?${hide ? ", state = 'reported'" : ""} WHERE id = ?`, reports, id),
  ]);
  return d.json({ id, reported: true });
}

export async function fontRoute(d, request, env, path) {
  const method = request.method;
  if (path === "/api/recipes/fonts" && method === "POST") return rfPost(d, env, request);
  const m = path.match(/^\/api\/recipes\/fonts\/(u[0-9a-f]{16})(\/file|\/report)?$/);
  if (!m || !RF_ID.test(m[1])) return null;
  if (!m[2] && method === "GET") return rfMeta(d, env, request, m[1]);
  if (m[2] === "/file" && method === "GET") return rfFile(d, env, request, m[1]);
  if (m[2] === "/report" && method === "POST") return rfReport(d, env, request, m[1]);
  return null;
}

/** 운영자: 나눈 글꼴 (신고로 내려간 것 먼저), 다시 올리기·내리기. */
export async function fontAdmin(d, request, env, path, url, now) {
  const method = request.method;
  if (path === "/recipes/fonts" && method === "GET") {
    const state = ["reported", "hidden", "ok"].includes(url.searchParams.get("state")) ? url.searchParams.get("state") : "reported";
    const rows = await d.all(env, "SELECT id, name, license, bytes, state, reports, at FROM recipe_fonts WHERE state = ? ORDER BY reports DESC, at DESC LIMIT 100", state);
    const items = [];
    for (const r of rows) {
      const whys = await d.all(env, "SELECT why, COUNT(*) AS n FROM recipe_font_reports WHERE id = ? GROUP BY why", r.id);
      items.push({ ...r, licenseName: FONT_LICENSE_NAMES[r.license], whys: Object.fromEntries(whys.map((w) => [w.why, w.n])), at: new Date(r.at).toISOString() });
    }
    const counts = await d.all(env, "SELECT state, COUNT(*) AS n FROM recipe_fonts GROUP BY state");
    return d.json({ state, items, counts: Object.fromEntries(counts.map((c) => [c.state, c.n])) });
  }
  const m = path.match(/^\/recipes\/fonts\/(u[0-9a-f]{16})\/(ok|hide)$/);
  if (m && method === "POST") {
    const state = m[2] === "ok" ? "ok" : "hidden";
    await d.batch(env, [
      d.stmt(env, "UPDATE recipe_fonts SET state = ?, decided_at = ?, reports = CASE WHEN ? = 'ok' THEN 0 ELSE reports END WHERE id = ?", state, now, state, m[1]),
      ...(state === "ok" ? [d.stmt(env, "DELETE FROM recipe_font_reports WHERE id = ?", m[1])] : []),
    ]);
    return d.json({ id: m[1], state });
  }
  return null;
}

/** 이 휴대폰이 보낸 정보 모두 지우기: the fonts it shared and its reports. */
export function forgetFonts(d, env, dev) {
  return [
    d.stmt(env, "DELETE FROM recipe_font_parts WHERE id IN (SELECT id FROM recipe_fonts WHERE owner = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_font_reports WHERE id IN (SELECT id FROM recipe_fonts WHERE owner = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_fonts WHERE owner = ?", dev),
    d.stmt(env, "DELETE FROM recipe_font_reports WHERE device = ?", dev),
  ];
}

/** The same id the phone makes (for the tests). */
export const fontIdOf = rfIdOf;
