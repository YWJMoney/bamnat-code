// 1.35 [RCX-01]..[RCX-15] 나눔 판 완성 (계획/1.35-나눔판-완성-계획.md): 계보, 새 판 확인, 요청 판, 제작자 페이지·배지·팔로우,
// 이번 주 고른 것, 답 알림, 그림 표지.
//
// What a phone is known by stays the install hash it already has on the plaza; a maker is shown by [makerId], a one-way id of
// it, never the hash. 구독 is kept on the phone only: it asks for the newest version of the codes it took (no device sent), so
// the server never learns who uses which recipe. Everything that sends (요청, 팔로우, 그림) waits for 처리방침 3.8 like the rest.
import { CODE, cleanText, graphemes, listItem, makerId, oneEmoji, sha256hex, textProblem } from "./recipes.js";
import { DAY, HOUR } from "./units.js";

const RC_STYLE_KEYS = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
export const COMMUNITY_LIMITS = { requestText: [5, 200], requestsPerDay: 3, imageBytes: 64 * 1024, imagesPerDay: 5, picks: 3, versionsAsk: 50 };
export const RECIPE_BADGES = {
  first: "첫 레시피",
  users100: "쓰는 분 100명",
  remix10: "고쳐 만든 것 10개",
  requests5: "요청 완성 5번",
  helper: "공개 자료 정리 도움",
};

export const COMMUNITY_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS recipe_requests (id TEXT PRIMARY KEY, device TEXT NOT NULL, style TEXT NOT NULL, body TEXT NOT NULL,
    likes INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL DEFAULT 'waiting', maker TEXT, code TEXT, hidden INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS recipe_requests_style ON recipe_requests (style, hidden, likes)",
  `CREATE TABLE IF NOT EXISTS recipe_request_likes (id TEXT NOT NULL, device TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (id, device))`,
  `CREATE TABLE IF NOT EXISTS recipe_makers (device TEXT PRIMARY KEY, maker TEXT NOT NULL UNIQUE, face TEXT, color INTEGER,
    helper INTEGER NOT NULL DEFAULT 0, at INTEGER NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS recipe_follows (device TEXT NOT NULL, maker TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (device, maker))`,
  "CREATE INDEX IF NOT EXISTS recipe_follows_maker ON recipe_follows (maker)",
  `CREATE TABLE IF NOT EXISTS recipe_picks (week TEXT NOT NULL, style TEXT NOT NULL, rank INTEGER NOT NULL, code TEXT NOT NULL,
    PRIMARY KEY (week, style, rank))`,
  `CREATE TABLE IF NOT EXISTS recipe_images (id TEXT PRIMARY KEY, owner TEXT NOT NULL, data TEXT NOT NULL, bytes INTEGER NOT NULL,
    state TEXT NOT NULL DEFAULT 'pending', at INTEGER NOT NULL, decided_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS recipe_images_state ON recipe_images (state, at)",
];
/** [RCX-05] Each recipe knows its maker's public id, so a list row links the maker's page without the install hash. */
export const COMMUNITY_COLUMNS = ["ALTER TABLE recipes ADD COLUMN maker TEXT"];

/** The maker row of an install, made the first time it is needed. */
async function rcMakerOf(d, env, dev, now) {
  const m = await d.one(env, "SELECT * FROM recipe_makers WHERE device = ?", dev);
  if (m) return m;
  const id = await makerId(dev);
  await d.run(env, "INSERT OR IGNORE INTO recipe_makers (device, maker, at) VALUES (?, ?, ?)", dev, id, now);
  return { device: dev, maker: id, face: null, color: null, helper: 0, at: now };
}

/** A list row with its maker's id (rows made before 1.35 get theirs here once). */
async function rcWithMaker(d, env, rows, now) {
  const out = [];
  for (const r of rows) {
    let maker = r.maker;
    if (!maker) {
      maker = await makerId(r.owner);
      await d.run(env, "UPDATE recipes SET maker = ? WHERE code = ?", maker, r.code);
    }
    out.push({ ...listItem(r, now), maker });
  }
  return out;
}

async function rcReadDev(d, request, body) {
  const id = d.deviceId(body?.device ?? request.headers.get("x-device"));
  return id ? d.deviceHash(id) : null;
}

/** The KST week a pick belongs to: the Monday's date. */
export function weekOf(ms) {
  const kst = new Date(ms + 9 * HOUR);
  const day = (kst.getUTCDay() + 6) % 7;
  return new Date(kst.getTime() - day * DAY).toISOString().slice(0, 10);
}

// ---- [RCX-01] 계보 ----

/** GET /api/recipes/:code/lineage — the recipes it was made from (up to 10 back) and those made from it (public only). */
async function rcLineage(d, env, code) {
  const now = Date.now();
  const self = await d.one(env, "SELECT code, parent, state, scope FROM recipes WHERE code = ?", code);
  if (!self || self.state !== "visible" || self.scope === "private") return d.json({ error: "not_found" }, 404);
  const up = [];
  let at = self.parent;
  const seen = new Set([code]);
  while (at && up.length < 10 && !seen.has(at)) {
    seen.add(at);
    const r = await d.one(env, "SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner WHERE r.code = ?", at);
    if (!r || r.state !== "visible" || r.scope === "private") {
      up.push({ code: at, gone: true });
      break;
    }
    up.push((await rcWithMaker(d, env, [r], now))[0]);
    at = r.parent;
  }
  const kids = await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner
    WHERE r.parent = ? AND r.state = 'visible' AND r.scope = 'public' ORDER BY r.likes DESC, r.created_at DESC LIMIT 30`, code);
  return d.json({ code, parents: up, children: await rcWithMaker(d, env, kids, now) });
}

// ---- [RCX-02] 새 판 확인 (구독은 휴대폰에만) ----

/** GET /api/recipes/versions?codes=A,B — the newest version of each, nothing about who asks. */
async function rcVersions(d, env, url) {
  const codes = [...new Set((url.searchParams.get("codes") ?? "").split(",").filter((c) => CODE.test(c)))].slice(0, COMMUNITY_LIMITS.versionsAsk);
  if (!codes.length) return d.json({ versions: {} });
  const rows = await d.all(env, `SELECT code, version, hash, title, updated_at FROM recipes WHERE code IN (${codes.map(() => "?").join(",")})
    AND state = 'visible' AND scope != 'private'`, ...codes);
  return d.json({ versions: Object.fromEntries(rows.map((r) => [r.code, { version: r.version, hash: r.hash, title: r.title, at: new Date(r.updated_at).toISOString() }])) });
}

// ---- [RCX-04] 요청 판 ----

function rcRequestItem(q, dev, liked) {
  return {
    id: q.id, style: q.style, body: q.body, likes: q.likes, state: q.state, code: q.code ?? null, at: new Date(q.created_at).toISOString(),
    mine: Boolean(dev && q.device === dev), taking: Boolean(dev && q.maker === dev), ...(liked ? { liked: true } : {}),
  };
}

async function rcRequestList(d, env, request, url) {
  const dev = await rcReadDev(d, request, null);
  const style = RC_STYLE_KEYS.includes(url.searchParams.get("style")) ? url.searchParams.get("style") : null;
  const rows = await d.all(env, `SELECT * FROM recipe_requests WHERE hidden = 0 ${style ? "AND style = ?" : ""}
    ORDER BY CASE state WHEN 'done' THEN 1 ELSE 0 END, likes DESC, created_at DESC LIMIT 60`, ...(style ? [style] : []));
  const liked = dev && rows.length ? new Set((await d.all(env, `SELECT id FROM recipe_request_likes WHERE device = ? AND id IN (${rows.map(() => "?").join(",")})`,
    dev, ...rows.map((q) => q.id))).map((x) => x.id)) : new Set();
  return d.json({ items: rows.map((q) => rcRequestItem(q, dev, liked.has(q.id))) });
}

async function rcRequestPost(d, env, request) {
  if (await d.paused(env, "PLAZA_WRITE")) return d.json({ error: "paused" }, 503);
  const body = await d.readJson(request, 2048);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.talk", now))) return d.json({ error: "not_yet", field: "recipe.talk" }, 403);
  if (!RC_STYLE_KEYS.includes(body.style)) return d.json({ error: "style", field: "style" }, 400);
  if (typeof body.body !== "string") return d.json({ error: "body", field: "body" }, 400);
  const t = cleanText(body.body, true, 6);
  const n = graphemes(t);
  if (n < COMMUNITY_LIMITS.requestText[0] || n > COMMUNITY_LIMITS.requestText[1]) return d.json({ error: "body", field: "body" }, 400);
  const why = textProblem(t);
  if (why) return d.json({ error: why, field: "body" }, 400);
  const muted = await d.one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", dev, now);
  if (muted) return d.json({ error: "muted", until: new Date(muted.until).toISOString() }, 403);
  const hit = await d.overLimit(env, now, [["device", `rreq|${dev}`, COMMUNITY_LIMITS.requestsPerDay, DAY]]);
  if (hit) return hit;
  const id = `q${d.randomId(12)}`;
  await d.run(env, "INSERT INTO recipe_requests (id, device, style, body, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)", id, dev, body.style, t, now, now);
  return d.json({ id }, 201);
}

async function rcRequestAct(d, env, request, id, what) {
  const body = await d.readJson(request, 1024);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const q = await d.one(env, "SELECT * FROM recipe_requests WHERE id = ? AND hidden = 0", id);
  if (!q) return d.json({ error: "not_found" }, 404);
  if (what === "delete") {
    if (q.device !== dev) return d.json({ error: "not_yours" }, 403);
    await d.batch(env, [d.stmt(env, "DELETE FROM recipe_requests WHERE id = ?", id), d.stmt(env, "DELETE FROM recipe_request_likes WHERE id = ?", id)]);
    return d.json({ id, deleted: true });
  }
  if (!(await d.legalOpen(env, "recipe.talk", now))) return d.json({ error: "not_yet", field: "recipe.talk" }, 403);
  if (what === "like") {
    if (q.device === dev) return d.json({ error: "own" }, 400);
    const on = body.on !== false;
    const had = await d.one(env, "SELECT 1 AS x FROM recipe_request_likes WHERE id = ? AND device = ?", id, dev);
    if (on && !had) await d.batch(env, [d.stmt(env, "INSERT INTO recipe_request_likes (id, device, at) VALUES (?, ?, ?)", id, dev, now),
      d.stmt(env, "UPDATE recipe_requests SET likes = likes + 1 WHERE id = ?", id)]);
    if (!on && had) await d.batch(env, [d.stmt(env, "DELETE FROM recipe_request_likes WHERE id = ? AND device = ?", id, dev),
      d.stmt(env, "UPDATE recipe_requests SET likes = likes - 1 WHERE id = ?", id)]);
    const after = await d.one(env, "SELECT likes FROM recipe_requests WHERE id = ?", id);
    return d.json({ id, likes: after.likes, liked: on });
  }
  if (what === "take") {
    // 만들게요: one maker at a time; the same phone can let it go again.
    if (body.on === false) {
      if (q.maker !== dev || q.state !== "making") return d.json({ error: "state" }, 409);
      await d.run(env, "UPDATE recipe_requests SET state = 'waiting', maker = NULL, updated_at = ? WHERE id = ?", now, id);
      return d.json({ id, state: "waiting" });
    }
    if (q.state !== "waiting") return d.json({ error: "state" }, 409);
    await d.run(env, "UPDATE recipe_requests SET state = 'making', maker = ?, updated_at = ? WHERE id = ?", dev, now, id);
    return d.json({ id, state: "making" });
  }
  if (what === "done") {
    if (q.maker !== dev || q.state === "done") return d.json({ error: "state" }, 409);
    if (typeof body.code !== "string" || !CODE.test(body.code)) return d.json({ error: "code", field: "code" }, 400);
    const r = await d.one(env, "SELECT owner, state, scope FROM recipes WHERE code = ?", body.code);
    if (!r || r.owner !== dev || r.scope === "private" || r.state === "deleted") return d.json({ error: "code", field: "code" }, 400);
    await d.run(env, "UPDATE recipe_requests SET state = 'done', code = ?, updated_at = ? WHERE id = ?", body.code, now, id);
    return d.json({ id, state: "done", code: body.code });
  }
  return null;
}

// ---- [RCX-05][RCX-06][RCX-07] 제작자 페이지·배지·팔로우 ----

async function rcBadgesOf(d, env, dev, m, sums) {
  const out = [];
  if (sums.recipes >= 1) out.push("first");
  if (sums.users >= 100) out.push("users100");
  const remix = await d.one(env, "SELECT COUNT(*) AS n FROM recipes WHERE owner = ? AND parent IS NOT NULL AND state = 'visible'", dev);
  if (remix.n >= 10) out.push("remix10");
  const done = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_requests WHERE maker = ? AND state = 'done'", dev);
  if (done.n >= 5) out.push("requests5");
  if (m?.helper) out.push("helper");
  return out.map((k) => ({ key: k, name: RECIPE_BADGES[k] }));
}

async function rcMakerPage(d, env, request, id) {
  const dev = await rcReadDev(d, request, null);
  const now = Date.now();
  const m = await d.one(env, "SELECT * FROM recipe_makers WHERE maker = ?", id);
  const owner = id === "operator" ? "operator" : m?.device;
  if (!owner) return d.json({ error: "not_found" }, 404);
  const rows = await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner
    WHERE r.owner = ? AND r.state = 'visible' AND r.scope = 'public' ORDER BY r.likes DESC, r.updated_at DESC LIMIT 60`, owner);
  const sums = { recipes: rows.length, likes: rows.reduce((s, r) => s + r.likes, 0), users: rows.reduce((s, r) => s + r.applied, 0) };
  const nick = owner === "operator" ? "밤낮 운영" : (await d.one(env, "SELECT nick FROM nicks WHERE device = ?", owner))?.nick ?? null;
  const followers = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_follows WHERE maker = ?", id);
  const following = dev ? Boolean(await d.one(env, "SELECT 1 AS x FROM recipe_follows WHERE device = ? AND maker = ?", dev, id)) : false;
  return d.json({
    maker: id, nick, face: m?.face ?? null, color: m?.color ?? null, mine: Boolean(dev && dev === owner),
    recipes: await rcWithMaker(d, env, rows, now), likes: sums.likes, users: sums.users, followers: followers.n, following,
    badges: await rcBadgesOf(d, env, owner, m, sums),
  });
}

/** POST /api/recipes/makers/me — {device, face, color}: the maker's own 이모지 얼굴 and its colour; the page id back. */
async function rcMakerMe(d, env, request) {
  const body = await d.readJson(request, 1024);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const m = await rcMakerOf(d, env, dev, now);
  if (body.face === undefined && body.color === undefined) return d.json({ maker: m.maker, face: m.face, color: m.color });
  const face = body.face === null ? null : oneEmoji(body.face);
  if (body.face !== null && body.face !== undefined && !face) return d.json({ error: "emoji", field: "face" }, 400);
  const color = Number.isInteger(body.color) && body.color >= 0 && body.color <= 0xffffffff ? body.color : null;
  await d.run(env, "UPDATE recipe_makers SET face = ?, color = ? WHERE device = ?", face ?? null, color, dev);
  return d.json({ maker: m.maker, face, color });
}

async function rcFollow(d, env, request, id) {
  const body = await d.readJson(request, 512);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.talk", now))) return d.json({ error: "not_yet", field: "recipe.talk" }, 403);
  const m = id === "operator" ? { device: "operator" } : await d.one(env, "SELECT device FROM recipe_makers WHERE maker = ?", id);
  if (!m) return d.json({ error: "not_found" }, 404);
  if (m.device === dev) return d.json({ error: "own" }, 400);
  if (body.on === false) await d.run(env, "DELETE FROM recipe_follows WHERE device = ? AND maker = ?", dev, id);
  else {
    const n = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_follows WHERE device = ?", dev);
    if (n.n >= 200) return d.json({ error: "too_many" }, 400);
    await d.run(env, "INSERT OR IGNORE INTO recipe_follows (device, maker, at) VALUES (?, ?, ?)", dev, id, now);
  }
  return d.json({ maker: id, following: body.on !== false });
}

/** GET /api/recipes/news?since= — for this phone's 소식: new recipes of the makers it follows and the makers' answers to its questions. */
async function rcNews(d, env, request, url) {
  const dev = await rcReadDev(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const since = Math.max(now - 30 * DAY, Date.parse(url.searchParams.get("since") ?? "") || now - 7 * DAY);
  const followed = await d.all(env, "SELECT maker FROM recipe_follows WHERE device = ?", dev);
  const makers = followed.map((f) => f.maker);
  const fresh = makers.length ? await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner
    WHERE r.maker IN (${makers.map(() => "?").join(",")}) AND r.state = 'visible' AND r.scope = 'public' AND r.created_at > ?
    ORDER BY r.created_at DESC LIMIT 20`, ...makers, since) : [];
  // [RCX-10] A maker's reply under a question this phone asked on the maker's recipe.
  const replies = await d.all(env, `SELECT a.grp AS code, a.body, a.created_at, r.title FROM posts q
    JOIN posts a ON a.parent = q.id AND a.company_id = 'recipe' AND a.status = 'shown'
    JOIN recipes r ON r.code = q.grp AND a.device = r.owner
    WHERE q.company_id = 'recipe' AND q.device = ? AND q.kind = 'question' AND a.created_at > ? ORDER BY a.created_at DESC LIMIT 20`, dev, since);
  return d.json({
    recipes: await rcWithMaker(d, env, fresh, now),
    replies: replies.map((x) => ({ code: x.code, title: x.title, body: x.body.slice(0, 80), at: new Date(x.created_at).toISOString() })),
  });
}

// ---- [RCX-08] 이번 주 고른 것 ----

async function rcPicks(d, env, url) {
  const now = Date.now();
  const style = RC_STYLE_KEYS.includes(url.searchParams.get("style")) ? url.searchParams.get("style") : null;
  // This week's, or last week's until the operator picks again.
  let rows = [];
  for (const week of [weekOf(now), weekOf(now - 7 * DAY)]) {
    rows = await d.all(env, `SELECT r.*, n.nick, p.rank FROM recipe_picks p JOIN recipes r ON r.code = p.code LEFT JOIN nicks n ON n.device = r.owner
      WHERE p.week = ? AND p.style = ? AND r.state = 'visible' AND r.scope = 'public' ORDER BY p.rank`, week, style ?? "ALL");
    if (rows.length) break;
  }
  return d.json({ style, items: await rcWithMaker(d, env, rows, now) }, 200, { "cache-control": "public, max-age=300" });
}

// ---- [RCX-15] 그림 표지 ----

const RC_WEBP = (bytes) => bytes.length > 12 && String.fromCharCode(...bytes.slice(0, 4)) === "RIFF" && String.fromCharCode(...bytes.slice(8, 12)) === "WEBP";

function rcB64bytes(s) {
  try {
    const bin = atob(s);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  } catch {
    return null;
  }
}

/** POST /api/recipes/images — {device, webp}: a cover picture the phone already shrank and re-encoded (no EXIF); shown once looked at. */
async function rcImagePost(d, env, request) {
  if (await d.paused(env, "PLAZA_WRITE")) return d.json({ error: "paused" }, 503);
  const body = await d.readJson(request, Math.ceil(COMMUNITY_LIMITS.imageBytes * 1.4) + 512);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.publish", now))) return d.json({ error: "not_yet", field: "recipe.publish" }, 403);
  const bytes = typeof body.webp === "string" ? rcB64bytes(body.webp) : null;
  if (!bytes || !RC_WEBP(bytes)) return d.json({ error: "image", field: "webp" }, 400);
  if (bytes.length > COMMUNITY_LIMITS.imageBytes) return d.json({ error: "too_big", field: "webp" }, 413);
  const hit = await d.overLimit(env, now, [["device", `rimg|${dev}`, COMMUNITY_LIMITS.imagesPerDay, DAY]]);
  if (hit) return hit;
  const id = (await sha256hex(`${dev}|${now}|${body.webp.length}|${d.randomId(8)}`)).slice(0, 16);
  await d.run(env, "INSERT INTO recipe_images (id, owner, data, bytes, at) VALUES (?, ?, ?, ?, ?)", id, dev, body.webp, bytes.length, now);
  return d.json({ id, state: "pending" }, 201);
}

async function rcImageGet(d, env, request, id) {
  const img = await d.one(env, "SELECT * FROM recipe_images WHERE id = ?", id);
  if (!img) return d.json({ error: "not_found" }, 404);
  if (img.state !== "ok") {
    const dev = await rcReadDev(d, request, null);
    if (!dev || dev !== img.owner) return d.json({ error: "not_found", state: img.state === "pending" ? "pending" : undefined }, 404);
  }
  return new Response(rcB64bytes(img.data), {
    headers: { "content-type": "image/webp", "cache-control": img.state === "ok" ? "public, max-age=86400" : "no-store", "x-content-type-options": "nosniff" },
  });
}

// ---- routing ----

export async function communityRoute(d, request, env, path, url) {
  const method = request.method;
  if (path === "/api/recipes/versions" && method === "GET") return rcVersions(d, env, url);
  if (path === "/api/recipes/requests" && method === "GET") return rcRequestList(d, env, request, url);
  if (path === "/api/recipes/requests" && method === "POST") return rcRequestPost(d, env, request);
  if (path === "/api/recipes/picks" && method === "GET") return rcPicks(d, env, url);
  if (path === "/api/recipes/news" && method === "GET") return rcNews(d, env, request, url);
  if (path === "/api/recipes/makers/me" && method === "POST") return rcMakerMe(d, env, request);
  if (path === "/api/recipes/images" && method === "POST") return rcImagePost(d, env, request);
  let m = path.match(/^\/api\/recipes\/requests\/(q[A-Za-z0-9]{12})\/(like|take|done|delete)$/);
  if (m && method === "POST") return rcRequestAct(d, env, request, m[1], m[2]);
  m = path.match(/^\/api\/recipes\/makers\/([0-9a-f]{12}|operator)(\/follow)?$/);
  if (m && !m[2] && method === "GET") return rcMakerPage(d, env, request, m[1]);
  if (m && m[2] && method === "POST") return rcFollow(d, env, request, m[1]);
  m = path.match(/^\/api\/recipes\/images\/([0-9a-f]{16})$/);
  if (m && method === "GET") return rcImageGet(d, env, request, m[1]);
  m = path.match(/^\/api\/recipes\/([2-9A-HJ-NP-Z]{8})\/lineage$/);
  if (m && method === "GET") return rcLineage(d, env, m[1]);
  return null;
}

/** 운영자 도구 (1.35): 이번 주 고른 것, 그림 검토, 요청 가리기, 공개 자료 정리 도움 배지. */
export async function communityAdmin(d, request, env, path, url, now) {
  const method = request.method;
  if (path === "/recipes/picks" && method === "GET") {
    const rows = await d.all(env, "SELECT * FROM recipe_picks WHERE week = ? ORDER BY style, rank", weekOf(now));
    return d.json({ week: weekOf(now), items: rows });
  }
  if (path === "/recipes/picks" && method === "POST") {
    const body = await d.readJson(request, 2048);
    const style = body.style === "ALL" || RC_STYLE_KEYS.includes(body.style) ? body.style : null;
    if (!style) return d.json({ error: "style", field: "style" }, 400);
    const codes = Array.isArray(body.codes) ? [...new Set(body.codes.filter((c) => typeof c === "string" && CODE.test(c)))].slice(0, COMMUNITY_LIMITS.picks) : [];
    const week = weekOf(now);
    await d.batch(env, [d.stmt(env, "DELETE FROM recipe_picks WHERE week = ? AND style = ?", week, style),
      ...codes.map((c, i) => d.stmt(env, "INSERT INTO recipe_picks (week, style, rank, code) VALUES (?, ?, ?, ?)", week, style, i + 1, c))]);
    return d.json({ week, style, codes });
  }
  if (path === "/recipes/images" && method === "GET") {
    const rows = await d.all(env, "SELECT id, bytes, state, at, data FROM recipe_images WHERE state = ? ORDER BY at LIMIT 50",
      url.searchParams.get("state") === "hidden" ? "hidden" : "pending");
    return d.json({ items: rows.map((r) => ({ ...r, at: new Date(r.at).toISOString() })) });
  }
  let m = path.match(/^\/recipes\/images\/([0-9a-f]{16})\/(ok|hide)$/);
  if (m && method === "POST") {
    await d.run(env, "UPDATE recipe_images SET state = ?, decided_at = ? WHERE id = ?", m[2] === "ok" ? "ok" : "hidden", now, m[1]);
    return d.json({ id: m[1], state: m[2] === "ok" ? "ok" : "hidden" });
  }
  m = path.match(/^\/recipes\/requests\/(q[A-Za-z0-9]{12})\/(hide|show)$/);
  if (m && method === "POST") {
    await d.run(env, "UPDATE recipe_requests SET hidden = ? WHERE id = ?", m[2] === "hide" ? 1 : 0, m[1]);
    return d.json({ id: m[1], hidden: m[2] === "hide" });
  }
  m = path.match(/^\/recipes\/makers\/([0-9a-f]{12})\/helper$/);
  if (m && method === "POST") {
    const body = await d.readJson(request, 256);
    await d.run(env, "UPDATE recipe_makers SET helper = ? WHERE maker = ?", body.on === false ? 0 : 1, m[1]);
    return d.json({ maker: m[1], helper: body.on !== false });
  }
  return null;
}

/** 이 휴대폰이 보낸 정보 모두 지우기 (1.35 parts). */
export function forgetCommunity(d, env, dev) {
  return [
    d.stmt(env, "UPDATE recipe_requests SET likes = likes - 1 WHERE id IN (SELECT id FROM recipe_request_likes WHERE device = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_request_likes WHERE device = ?", dev),
    d.stmt(env, "DELETE FROM recipe_request_likes WHERE id IN (SELECT id FROM recipe_requests WHERE device = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_requests WHERE device = ?", dev),
    d.stmt(env, "UPDATE recipe_requests SET state = 'waiting', maker = NULL WHERE maker = ? AND state = 'making'", dev),
    d.stmt(env, "DELETE FROM recipe_follows WHERE device = ?", dev),
    d.stmt(env, "DELETE FROM recipe_follows WHERE maker IN (SELECT maker FROM recipe_makers WHERE device = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_makers WHERE device = ?", dev),
    d.stmt(env, "DELETE FROM recipe_images WHERE owner = ?", dev),
  ];
}
