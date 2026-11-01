// 1.36 [MAT-06] 내 글꼴 나누기: a font the user brought (TTF·OTF) that its maker lets others use (OFL·Apache·무료 공개, the
// user says so), kept so whoever takes a recipe naming it can draw it too. Not looked at first (a font is not a picture of
// anyone); others can report it, and three reports from three phones take it down until the operator looks.
//
// A font's id is "u" + the first 16 hex of the SHA-256 of its bytes, the same id the phone gave it, so a recipe names a font by
// what it is and the same file sent twice is one font. D1 keeps at most 2 MB in a value, so the bytes are kept in parts.
//
// 1.36 보안 점검(조사/1.36-보안-점검.md) H1·H3·M3·M7: a font is read by the phone's native font engine, so the server reads its
// table directory first and turns away one that does not hold together (or carries bytes past its tables); every font waits for
// the operator before others get it (M8: a fast path for senders with a passed font trusted words the sender writes, so it
// is gone — the day's limits keep the operator's queue small); a day's fonts are counted per network and
// for everyone, not only per install; and reports from new installs or a second one from the same network do not take it down.
import { DAY } from "./units.js";

/** The level recipe_trust gives [t] (recipes.js recipeTrust), read without writing: 0 새싹, 1 이웃, 2 단골. */
function rfTrust(t, now) {
  if (!t) return 0;
  const days = (now - t.first_at) / DAY;
  return (t.approved >= 3 || days >= 14) && t.hidden === 0 ? 2 : t.approved >= 1 || days >= 3 ? 1 : 0;
}

export const FONT_LIMITS = { bytes: 8 * 1024 * 1024, perDay: 6, part: 1_500_000, reportsToHide: 3, name: 30,
  /** 1.36 [SEC-10] H1: fonts a network may send a day, and all phones together (8 MB each at most, so 320 MB a day at most). */
  perNetworkDay: 3, allDay: 40 };
/** What the user says its maker allows; only these may be shared. */
export const FONT_LICENSES = ["ofl", "apache", "free"];
export const FONT_LICENSE_NAMES = { ofl: "SIL 오픈 폰트 라이선스(OFL)", apache: "Apache 라이선스", free: "누구나 무료로 쓰게 공개" };

export const FONT_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS recipe_fonts (id TEXT PRIMARY KEY, owner TEXT NOT NULL, name TEXT NOT NULL, license TEXT NOT NULL,
    bytes INTEGER NOT NULL, parts INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'ok', reports INTEGER NOT NULL DEFAULT 0,
    at INTEGER NOT NULL, decided_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS recipe_fonts_state ON recipe_fonts (state, at)",
  // 1.36 [SEC-10] What the font says of itself (fontInfo), as JSON, for the operator.
  "ALTER TABLE recipe_fonts ADD COLUMN info TEXT",
  // 1.36 [SEC-10] M8: the SHA-256 of its outlines and cmap (fontGlyphs), so the operator sees a font that draws the same as one
  // already shared whatever its names say.
  "ALTER TABLE recipe_fonts ADD COLUMN glyphs TEXT",
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

const rfU16 = (b, o) => (b[o] << 8) | b[o + 1];
const rfU32 = (b, o) => ((b[o] << 24) >>> 0) + (b[o + 1] << 16) + (b[o + 2] << 8) + b[o + 3];
const rfTag = (b, o) => String.fromCharCode(b[o], b[o + 1], b[o + 2], b[o + 3]);

/** The tables of each face in [b] (one, or each of a collection's): tag → {off, len}; null when the directory does not hold together. */
export function fontTables(b) {
  if (!looksLikeFont(b)) return null;
  let starts = [0];
  if (rfTag(b, 0) === "ttcf") {
    const n = b.length >= 16 ? rfU32(b, 8) : 0;
    if (n < 1 || n > 16 || 12 + n * 4 > b.length) return null;
    starts = Array.from({ length: n }, (_, i) => rfU32(b, 12 + i * 4));
  }
  const faces = [];
  for (const at of starts) {
    if (at + 12 > b.length) return null;
    const flavor = rfTag(b, at);
    if (!(flavor === "OTTO" || flavor === "true" || rfU32(b, at) === 0x00010000)) return null;
    const num = rfU16(b, at + 4);
    if (num < 6 || num > 64 || at + 12 + num * 16 > b.length) return null;
    const tables = {};
    for (let i = 0; i < num; i++) {
      const r = at + 12 + i * 16;
      const tag = rfTag(b, r);
      const off = rfU32(b, r + 8);
      const len = rfU32(b, r + 12);
      if (!/^[\x20-\x7e]{4}$/.test(tag) || tables[tag] || off < 12 || len > b.length || off + len > b.length) return null;
      tables[tag] = { off, len };
    }
    faces.push(tables);
  }
  return faces;
}

/**
 * 1.36 [SEC-10] 보안 점검 H3: null when [b] reads as a font the way a font engine will start reading it, else why not. Each face
 * has the tables every font needs, its outlines (glyf+loca or CFF), a head table with its magic number and a glyph count, and no
 * table past the end of the file. Not a full check of every table: that is the phone's isolated look (FontProbe) — this turns
 * away a file that only begins like a font.
 */
export function fontProblem(b) {
  const faces = fontTables(b);
  if (!faces) return "font";
  // 1.36 [SEC-10] M8: nothing past the last table (rounded up to 4) — bytes there are not a font, and only change its id.
  if (b.length > fontEnd(b, faces)) return "font";
  for (const t of faces) {
    for (const need of ["cmap", "head", "hhea", "hmtx", "maxp", "name"]) if (!t[need]) return "font";
    if (!(t.glyf && t.loca) && !t["CFF "] && !t.CFF2) return "font";
    if (t.head.len < 54 || rfU32(b, t.head.off + 12) !== 0x5f0f3cf5) return "font";
    if (t.maxp.len < 6 || rfU16(b, t.maxp.off + 4) < 1) return "font";
    if (t.name.len < 6) return "font";
  }
  return null;
}

/** Where the font's own bytes end: its headers and every table of every face, rounded up to 4. */
function fontEnd(b, faces) {
  const ttc = rfTag(b, 0) === "ttcf";
  const n = ttc ? rfU32(b, 8) : 1;
  let end = ttc ? 12 + n * 4 : 0;
  // A version 2 collection may sign itself: its DSIG header follows the face offsets, its DSIG table may lie anywhere.
  if (ttc && rfU16(b, 4) === 2 && 12 + n * 4 + 12 <= b.length) {
    end += 12;
    const len = rfU32(b, 12 + n * 4 + 4);
    const off = rfU32(b, 12 + n * 4 + 8);
    if (off && off + len <= b.length) end = Math.max(end, off + len);
  }
  const starts = ttc ? Array.from({ length: n }, (_, i) => rfU32(b, 12 + i * 4)) : [0];
  faces.forEach((t, i) => {
    end = Math.max(end, starts[i] + 12 + Object.keys(t).length * 16);
    for (const { off, len } of Object.values(t)) end = Math.max(end, off + len);
  });
  return Math.ceil(end / 4) * 4;
}

/**
 * 1.36 [SEC-10] M8: what the font draws, whatever its names say: the SHA-256 of the first face's outlines (glyf, or CFF / CFF2)
 * and its cmap, in hex. Two files with these the same draw the same letters.
 */
export async function fontGlyphs(b) {
  const t = fontTables(b)?.[0];
  if (!t) return null;
  const parts = ["glyf", "loca", "CFF ", "CFF2", "cmap"].filter((k) => t[k]).map((k) => b.subarray(t[k].off, t[k].off + t[k].len));
  const all = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const p of parts) { all.set(p, at); at += p.length; }
  const digest = await crypto.subtle.digest("SHA-256", all);
  return [...new Uint8Array(digest)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

function rfNameText(b, off, len, platform) {
  const s = b.subarray(off, off + len);
  if (platform === 3 || platform === 0) {
    let out = "";
    for (let i = 0; i + 1 < s.length; i += 2) out += String.fromCharCode((s[i] << 8) | s[i + 1]);
    return out;
  }
  return String.fromCharCode(...s);
}

/**
 * 1.36 [SEC-10] 보안 점검 M7: what the font says of itself in its name table — family, maker, designer, licence words and link,
 * copyright — and its OS/2 vendor, for the operator to see beside what the sender chose. Each in at most 200 characters.
 */
export function fontInfo(b) {
  const t = fontTables(b)?.[0];
  const out = {};
  if (!t?.name) return out;
  const base = t.name.off;
  const count = rfU16(b, base + 2);
  const strings = base + rfU16(b, base + 4);
  const want = { 0: "copyright", 1: "family", 8: "maker", 9: "designer", 13: "license", 14: "licenseUrl" };
  const rank = {};
  for (let i = 0; i < count && base + 6 + i * 12 + 12 <= t.name.off + t.name.len; i++) {
    const r = base + 6 + i * 12;
    const platform = rfU16(b, r);
    const lang = rfU16(b, r + 4);
    const key = want[rfU16(b, r + 6)];
    const len = rfU16(b, r + 8);
    const off = strings + rfU16(b, r + 10);
    if (!key || off + len > b.length || (platform !== 0 && platform !== 1 && platform !== 3)) continue;
    // Korean first, then English, then whatever there is.
    const score = platform === 3 && lang === 0x0412 ? 3 : platform === 3 && lang === 0x0409 ? 2 : 1;
    if ((rank[key] ?? 0) >= score) continue;
    const text = rfNameText(b, off, len, platform).replace(/[\u0000-\u001f\u007f]/g, " ").trim();
    if (!text) continue;
    rank[key] = score;
    out[key] = [...text].slice(0, 200).join("");
  }
  if (t["OS/2"] && t["OS/2"].len >= 62) {
    const v = rfTag(b, t["OS/2"].off + 58).replace(/[^\x20-\x7e]/g, "").trim();
    if (v) out.vendor = v;
  }
  return out;
}

/** The licence the font's own words name: "ofl", "apache", or null when they name neither. */
export function fontLicenseSays(info) {
  const words = `${info.license ?? ""} ${info.licenseUrl ?? ""}`;
  if (/open font licen[cs]e|scripts\.sil\.org\/ofl|openfontlicense\.org|\bOFL\b/i.test(words)) return "ofl";
  if (/apache\.org\/licenses|apache licen[cs]e/i.test(words)) return "apache";
  return null;
}

/** Foundries that sell their fonts; their font is turned away unless its own words say OFL·Apache (Noto, 본고딕 and the like do). */
export const FONT_SOLD_BY = /sandoll|산돌|yoon ?design|윤디자인|hanyang|한양정보|rix ?font|릭스|fontrix|폰트릭스|monotype|linotype|microsoft|apple inc|apple computer|bitstream|font ?bureau|hoefler/i;

/** Whether the font's words say a foundry that sells fonts made it, and they do not say OFL·Apache. */
export function fontSold(info) {
  const who = `${info.maker ?? ""} ${info.copyright ?? ""} ${info.vendor ?? ""}`;
  return FONT_SOLD_BY.test(who) && !fontLicenseSays(info);
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
  // 1.36 [SEC-10] H3: a file that only begins like a font never reaches another phone's font engine.
  if (fontProblem(bytes)) return d.json({ error: "font", field: "ttf" }, 400);
  const id = await rfIdOf(bytes);
  if (body.id != null && body.id !== id) return d.json({ error: "id", field: "id" }, 400);
  const had = await d.one(env, "SELECT id, state FROM recipe_fonts WHERE id = ?", id);
  if (had) return d.json({ id, state: ["ok", "pending"].includes(had.state) ? had.state : "hidden", again: true }, 200);
  // 1.36 [SEC-10] M7: a font its own words say a foundry sells is not shared, whatever the sender chose.
  const info = fontInfo(bytes);
  if (fontSold(info)) return d.json({ error: "sold", field: "license" }, 400);
  // 1.36 Play Integrity (공통 보안 PR): a font others will get comes from the app from Play, when the server asks for it.
  { const gate = d.integrity ? await d.integrity(env, dev, now) : null; if (gate) return gate; }
  const net = await d.network(request, env, now);
  const hit = await d.overLimit(env, now, [["device", `rfont|${dev}`, FONT_LIMITS.perDay, DAY],
    ["network", `rfontnet|${net.day}`, FONT_LIMITS.perNetworkDay, DAY], ["network", `rfontall|${Math.floor(now / DAY)}`, FONT_LIMITS.allDay, DAY]]);
  if (hit) return hit;
  // 1.36 [SEC-10] H3·M7·M8: others get it after the operator looked, always. The words of its name table are the sender's to
  // write, so they only help the operator (beside the font already shared that draws the same, fontAdmin).
  const state = "pending";
  const glyphs = await fontGlyphs(bytes);
  const parts = [];
  for (let i = 0; i < body.ttf.length; i += FONT_LIMITS.part) parts.push(body.ttf.slice(i, i + FONT_LIMITS.part));
  await d.batch(env, [
    d.stmt(env, "INSERT INTO recipe_fonts (id, owner, name, license, bytes, parts, state, info, glyphs, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
      id, dev, rfName(body.name), body.license, bytes.length, parts.length, state, JSON.stringify(info), glyphs, now),
    ...parts.map((p, i) => d.stmt(env, "INSERT INTO recipe_font_parts (id, part, data) VALUES (?, ?, ?)", id, i, p)),
  ]);
  return d.json({ id, state }, 201);
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
  const hit = await d.overLimit(env, now, [["device", `rfontrep|${dev}`, 20, DAY]]);
  if (hit) return hit;
  { const gate = d.integrity ? await d.integrity(env, dev, now) : null; if (gate) return gate; }
  // 1.36 [SEC-10] M3: a 새싹 install's report, or a second one from the same network today, is kept for the operator but does
  // not count toward taking the font down.
  const trust = rfTrust(await d.one(env, "SELECT * FROM recipe_trust WHERE device = ?", dev), now);
  const net = await d.network(request, env, now);
  const counts = trust >= 1 && (await d.allow(env, `rfontrepnet|${net.day}|${id}`, 1, DAY, now));
  const reports = f.reports + (counts ? 1 : 0);
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
    const state = ["pending", "reported", "hidden", "ok"].includes(url.searchParams.get("state")) ? url.searchParams.get("state") : "pending";
    const rows = await d.all(env, "SELECT id, name, license, bytes, state, reports, info, glyphs, at FROM recipe_fonts WHERE state = ? ORDER BY reports DESC, at DESC LIMIT 100", state);
    const items = [];
    for (const r of rows) {
      const whys = await d.all(env, "SELECT why, COUNT(*) AS n FROM recipe_font_reports WHERE id = ? GROUP BY why", r.id);
      let info = {};
      try { info = JSON.parse(r.info ?? "{}") ?? {}; } catch {}
      const { glyphs, ...row } = r;
      items.push({ ...row, info, says: fontLicenseSays(info), licenseName: FONT_LICENSE_NAMES[r.license],
        like: r.state === "ok" ? [] : await fontLike(d, env, r, info),
        whys: Object.fromEntries(whys.map((w) => [w.why, w.n])), at: new Date(r.at).toISOString() });
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

/**
 * 1.36 [SEC-10] M8 (PC 7차): shared fonts like a waiting one, for the operator — one that draws the same (its glyphs), else one whose
 * name table names the same family or copyright — with how many bytes this one has more (+) or fewer (−). At most three.
 */
async function fontLike(d, env, r, info) {
  const out = [];
  const seen = new Set([r.id]);
  const add = (f, why) => {
    if (seen.has(f.id) || out.length >= 3) return;
    seen.add(f.id);
    out.push({ id: f.id, name: f.name, why, diff: r.bytes - f.bytes });
  };
  if (r.glyphs) {
    for (const f of await d.all(env, "SELECT id, name, bytes FROM recipe_fonts WHERE state = 'ok' AND glyphs = ? LIMIT 3", r.glyphs)) add(f, "glyphs");
  }
  const keys = [["family", info.family], ["copyright", info.copyright]].filter(([, v]) => v);
  if (keys.length && out.length < 3) {
    for (const f of await d.all(env, "SELECT id, name, bytes, info FROM recipe_fonts WHERE state = 'ok' ORDER BY at DESC LIMIT 300")) {
      let fi = {};
      try { fi = JSON.parse(f.info ?? "{}") ?? {}; } catch {}
      const hit = keys.find(([k, v]) => fi[k] === v);
      if (hit) add(f, hit[0]);
    }
  }
  return out;
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
