// 1.35 [RCX-01]..[RCX-15] 나눔 판 완성 (계획/1.35-나눔판-완성-계획.md): 계보, 새 판 확인, 요청 판, 제작자 페이지·배지·팔로우,
// 이번 주 고른 것, 답 알림, 그림 표지.
//
// What a phone is known by stays the install hash it already has on the plaza; a maker is shown by [makerId], a one-way id of
// it, never the hash. 구독 is kept on the phone only: it asks for the newest version of the codes it took (no device sent), so
// the server never learns who uses which recipe. Everything that sends (요청, 팔로우, 그림) waits for 처리방침 3.8 like the rest.
import { CODE, cleanText, graphemes, investAdvice, listItem, makerId, oneEmoji, sha256hex, textProblem } from "./recipes.js";
import { DAY, HOUR } from "./units.js";

const RC_STYLE_KEYS = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
// 1.35 [RCX-30] 그림 꾸미기 backgrounds (1080 px) fit in 160 KB; a whole look is up to 9 pictures.
export const COMMUNITY_LIMITS = { requestText: [5, 200], requestsPerDay: 3, imageBytes: 160 * 1024, imagesPerDay: 12, picks: 3, versionsAsk: 50,
  // [PRF-01..06] 1.36 창작자 프로필: 한 줄 소개, 대표 작품, 하루 팔로우, 차단, 신고 3번이면 소개 먼저 가림.
  bio: 40, pins: 6, followsPerDay: 60, setTitle: [2, 30], setSummary: 80, setCodes: [2, 6], sets: 20, setsPerDay: 10, follows: 200, blocks: 500, flagsToHideBio: 3 };
/** [PRF-03] 쓰는 분·좋아요가 이만큼 넘으면 내 프로필 최근 알림에 한 번 (폰이 본 것을 기억해요). */
export const PROFILE_MILESTONES = [10, 50, 100, 500, 1000, 5000];
export const PROFILE_FLAG_REASONS = ["impersonate", "bio", "spam", "other"];
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
  // [PRF-05] 1.36 차단: the blocker's install and the blocked maker's public id. Never told to the one blocked.
  "CREATE TABLE IF NOT EXISTS recipe_blocks (device TEXT NOT NULL, maker TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (device, maker))",
  "CREATE INDEX IF NOT EXISTS recipe_blocks_maker ON recipe_blocks (maker)",
  // [PRF-06] 1.36 프로필 신고 (사칭·소개): to the operator, three hide the 소개 first.
  // [PRF-07] 1.36 모음: a maker's own public recipes put together (테마 하나 + 기능별 탭들), tried on and put on at once.
  `CREATE TABLE IF NOT EXISTS recipe_sets (id TEXT PRIMARY KEY, owner TEXT NOT NULL, maker TEXT NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL DEFAULT '',
    codes TEXT NOT NULL, hidden INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS recipe_sets_maker ON recipe_sets (maker, updated_at)",
  // [PRF-10] 1.36 이달의 제작자: the operator's own picks for a month and kind of work, over the counted ones.
  `CREATE TABLE IF NOT EXISTS recipe_month_makers (month TEXT NOT NULL, style TEXT NOT NULL, rank INTEGER NOT NULL, maker TEXT NOT NULL,
    PRIMARY KEY (month, style, rank))`,
  `CREATE TABLE IF NOT EXISTS recipe_maker_flags (maker TEXT NOT NULL, device TEXT NOT NULL, reason TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (maker, device))`,
];
/** [RCX-05] Each recipe knows its maker's public id, so a list row links the maker's page without the install hash. */
export const COMMUNITY_COLUMNS = ["ALTER TABLE recipes ADD COLUMN maker TEXT",
  // [RCX-32] 1.35 보여주기: the operator's showcases of what 나눔 판 can do, at the top of each kind of work's 나눔 판.
  "ALTER TABLE recipes ADD COLUMN showcase INTEGER NOT NULL DEFAULT 0",
  // [PRF-01] 1.36 창작자 프로필: 한 줄 소개, 대표 작품(코드들), 보여 줄 근무 유형, 프로필 숨기기, 운영자가 가린 소개·프로필.
  "ALTER TABLE recipe_makers ADD COLUMN bio TEXT",
  "ALTER TABLE recipe_makers ADD COLUMN pins TEXT",
  "ALTER TABLE recipe_makers ADD COLUMN style_tag TEXT",
  "ALTER TABLE recipe_makers ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE recipe_makers ADD COLUMN bio_hidden INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE recipe_makers ADD COLUMN closed INTEGER NOT NULL DEFAULT 0",
  // [PRF-12] 1.36 프로 꾸미기: 프로필 표지 그림 (그림 올리기와 같은 검토를 거친 것만 보여요).
  "ALTER TABLE recipe_makers ADD COLUMN cover TEXT",
  // [PRF-04] 1.36 팔로우는 그대로, 이분의 새 작품 알림만 끄기.
  "ALTER TABLE recipe_follows ADD COLUMN quiet INTEGER NOT NULL DEFAULT 0"];

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

/** [PRF-05] Whether [dev] blocked the maker [id], or the maker blocked [dev] (then the page is not there for them). */
async function rcBlocked(d, env, dev, id, owner) {
  if (!dev) return { mine: false, theirs: false };
  const mine = Boolean(await d.one(env, "SELECT 1 AS x FROM recipe_blocks WHERE device = ? AND maker = ?", dev, id));
  const theirs = owner && owner !== "operator" ? Boolean(await d.one(env, "SELECT 1 AS x FROM recipe_blocks WHERE device = ? AND maker = ?", owner, await makerId(dev))) : false;
  return { mine, theirs };
}

const rcPinsOf = (m) => (m?.pins ? m.pins.split(",").filter((c) => CODE.test(c)) : []);

/**
 * GET /api/recipes/makers/:id — [RCX-05] 1.35 and [PRF-01] 1.36 창작자 프로필: 소개, 대표 작품, 테마와 기능별 탭 (scopeOf), 팔로워·팔로잉 수.
 * A hidden profile (by its maker or the operator) and one whose maker blocked this phone are not there; one this phone blocked shows
 * only that, so it can be undone.
 */
async function rcMakerPage(d, env, request, id) {
  const dev = await rcReadDev(d, request, null);
  const now = Date.now();
  const m = await d.one(env, "SELECT * FROM recipe_makers WHERE maker = ?", id);
  const owner = id === "operator" ? "operator" : m?.device;
  if (!owner) return d.json({ error: "not_found" }, 404);
  const mine = Boolean(dev && dev === owner);
  const block = mine ? { mine: false, theirs: false } : await rcBlocked(d, env, dev, id, owner);
  if (!mine && (m?.hidden || m?.closed || block.theirs)) return d.json({ error: "not_found" }, 404);
  const nick = owner === "operator" ? "밤낮 운영" : (await d.one(env, "SELECT nick FROM nicks WHERE device = ?", owner))?.nick ?? null;
  if (block.mine) return d.json({ maker: id, nick, face: m?.face ?? null, color: m?.color ?? null, mine: false, blocked: true, recipes: [], themes: 0, tabs: 0,
    likes: 0, users: 0, followers: 0, followingCount: 0, following: false, badges: [], pins: [] });
  const rows = await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner
    WHERE r.owner = ? AND r.state = 'visible' AND r.scope = 'public' ORDER BY r.likes DESC, r.updated_at DESC LIMIT 60`, owner);
  const sums = { recipes: rows.length, likes: rows.reduce((s, r) => s + r.likes, 0), users: rows.reduce((s, r) => s + r.applied, 0) };
  const followers = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_follows WHERE maker = ?", id);
  const followingCount = owner === "operator" ? { n: 0 } : await d.one(env, "SELECT COUNT(*) AS n FROM recipe_follows WHERE device = ?", owner);
  const follow = dev ? await d.one(env, "SELECT quiet FROM recipe_follows WHERE device = ? AND maker = ?", dev, id) : null;
  const items = await rcWithMaker(d, env, rows, now);
  const codes = new Set(items.map((r) => r.code));
  const sets = await rcSetsOf(d, env, id, codes, mine);
  const cover = m?.cover ? await d.one(env, "SELECT id FROM recipe_images WHERE id = ? AND state = 'ok'", m.cover) : null;
  return d.json({
    sets, ...(cover ? { cover: cover.id } : {}), ...(mine && m?.cover && !cover ? { coverPending: m.cover } : {}),
    maker: id, nick, face: m?.face ?? null, color: m?.color ?? null, mine,
    ...(m?.bio && !m.bio_hidden ? { bio: m.bio } : {}),
    ...(mine && m?.bio && m.bio_hidden ? { bio: m.bio, bioHidden: true } : {}),
    ...(m?.style_tag ? { styleTag: m.style_tag } : {}),
    ...(mine ? { hidden: Boolean(m?.hidden) } : {}),
    pins: rcPinsOf(m).filter((c) => codes.has(c)),
    recipes: items, themes: items.filter((r) => r.scope === "theme").length, tabs: items.filter((r) => r.scope === "tab").length,
    likes: sums.likes, users: sums.users, followers: followers.n, followingCount: followingCount.n, following: Boolean(follow),
    ...(follow ? { quiet: follow.quiet === 1 } : {}),
    badges: await rcBadgesOf(d, env, owner, m, sums),
  });
}

/**
 * POST /api/recipes/makers/me — {device, face?, color?, bio?, pins?, styleTag?, hidden?}: [RCX-05] the maker's 이모지 얼굴 and colour,
 * and [PRF-01][PRF-02] 1.36 한 줄 소개, 대표 작품, 보여 줄 근무 유형, 프로필 숨기기. Only what is sent changes; all of it comes back.
 */
async function rcMakerMe(d, env, request) {
  const body = await d.readJson(request, 2048);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  let m = await rcMakerOf(d, env, dev, now);
  const sets = [];
  const args = [];
  if (body.face !== undefined) {
    const face = body.face === null ? null : oneEmoji(body.face);
    if (body.face !== null && !face) return d.json({ error: "emoji", field: "face" }, 400);
    sets.push("face = ?");
    args.push(face);
  }
  if (body.color !== undefined) {
    sets.push("color = ?");
    args.push(Number.isInteger(body.color) && body.color >= 0 && body.color <= 0xffffffff ? body.color : null);
  }
  if (body.bio !== undefined) {
    const bio = body.bio === null ? "" : cleanText(String(body.bio));
    if (graphemes(bio) > COMMUNITY_LIMITS.bio) return d.json({ error: "too_long", field: "bio" }, 400);
    const why = bio ? textProblem(bio) ?? (investAdvice(bio) ? "invest_advice" : null) : null;
    if (why) return d.json({ error: why, field: "bio" }, 400);
    // A new 소개 is shown again; one the operator hid stays hidden only while it is the same text.
    sets.push("bio = ?", "bio_hidden = CASE WHEN bio = ? THEN bio_hidden ELSE 0 END");
    args.push(bio || null, bio || null);
  }
  if (body.pins !== undefined) {
    const want = Array.isArray(body.pins) ? [...new Set(body.pins.filter((c) => typeof c === "string" && CODE.test(c)))].slice(0, COMMUNITY_LIMITS.pins) : [];
    const own = want.length ? await d.all(env, `SELECT code FROM recipes WHERE owner = ? AND state = 'visible' AND scope = 'public'
      AND code IN (${want.map(() => "?").join(",")})`, dev, ...want) : [];
    const ok = new Set(own.map((r) => r.code));
    sets.push("pins = ?");
    args.push(want.filter((c) => ok.has(c)).join(",") || null);
  }
  if (body.styleTag !== undefined) {
    sets.push("style_tag = ?");
    args.push(RC_STYLE_KEYS.includes(body.styleTag) ? body.styleTag : null);
  }
  if (body.cover !== undefined) {
    // [PRF-12] Only one of the maker's own uploaded pictures; it shows once the operator has looked at it.
    const img = typeof body.cover === "string" && /^[0-9a-f]{16}$/.test(body.cover)
      ? await d.one(env, "SELECT id FROM recipe_images WHERE id = ? AND owner = ? AND state != 'hidden'", body.cover, dev) : null;
    if (body.cover !== null && !img) return d.json({ error: "image", field: "cover" }, 400);
    sets.push("cover = ?");
    args.push(img?.id ?? null);
  }
  if (body.hidden !== undefined) {
    sets.push("hidden = ?");
    args.push(body.hidden === true ? 1 : 0);
  }
  if (sets.length) {
    await d.run(env, `UPDATE recipe_makers SET ${sets.join(", ")} WHERE device = ?`, ...args, dev);
    m = await d.one(env, "SELECT * FROM recipe_makers WHERE device = ?", dev);
  }
  return d.json({ maker: m.maker, face: m.face ?? null, color: m.color ?? null, bio: m.bio ?? null, bioHidden: Boolean(m.bio_hidden),
    pins: rcPinsOf(m), styleTag: m.style_tag ?? null, hidden: Boolean(m.hidden), cover: m.cover ?? null });
}

/** POST /api/recipes/makers/:id/follow — {device, on?, quiet?}: 팔로우, 그만두기, and [PRF-04] 이분 알림만 끄기. */
async function rcFollow(d, env, request, id) {
  const body = await d.readJson(request, 512);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.talk", now))) return d.json({ error: "not_yet", field: "recipe.talk" }, 403);
  const m = id === "operator" ? { device: "operator" } : await d.one(env, "SELECT device, hidden, closed FROM recipe_makers WHERE maker = ?", id);
  if (!m) return d.json({ error: "not_found" }, 404);
  if (m.device === dev) return d.json({ error: "own" }, 400);
  if (body.on === false) {
    await d.run(env, "DELETE FROM recipe_follows WHERE device = ? AND maker = ?", dev, id);
    return d.json({ maker: id, following: false });
  }
  const was = await d.one(env, "SELECT quiet FROM recipe_follows WHERE device = ? AND maker = ?", dev, id);
  const quiet = body.quiet === undefined ? was?.quiet ?? 0 : body.quiet === true ? 1 : 0;
  if (!was) {
    // [PRF-05] Not to a hidden profile or one who blocked this phone; it looks the same as not there.
    if (m.hidden || m.closed || (await rcBlocked(d, env, dev, id, m.device)).theirs) return d.json({ error: "not_found" }, 404);
    const n = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_follows WHERE device = ?", dev);
    if (n.n >= COMMUNITY_LIMITS.follows) return d.json({ error: "too_many" }, 400);
    const hit = await d.overLimit(env, now, [["device", `follow|${dev}`, COMMUNITY_LIMITS.followsPerDay, DAY]]);
    if (hit) return hit;
    await d.run(env, "INSERT OR IGNORE INTO recipe_follows (device, maker, at, quiet) VALUES (?, ?, ?, ?)", dev, id, now, quiet);
  } else await d.run(env, "UPDATE recipe_follows SET quiet = ? WHERE device = ? AND maker = ?", quiet, dev, id);
  return d.json({ maker: id, following: true, quiet: quiet === 1 });
}

/** POST /api/recipes/makers/:id/block — {device, on?}: [PRF-05] 차단. Follows both ways go; the other side is never told. */
async function rcBlock(d, env, request, id) {
  const body = await d.readJson(request, 512);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  if (id === "operator") return d.json({ error: "operator" }, 400);
  const m = await d.one(env, "SELECT device FROM recipe_makers WHERE maker = ?", id);
  if (!m) return d.json({ error: "not_found" }, 404);
  if (m.device === dev) return d.json({ error: "own" }, 400);
  if (body.on === false) {
    await d.run(env, "DELETE FROM recipe_blocks WHERE device = ? AND maker = ?", dev, id);
    return d.json({ maker: id, blocked: false });
  }
  const n = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_blocks WHERE device = ?", dev);
  if (n.n >= COMMUNITY_LIMITS.blocks) return d.json({ error: "too_many" }, 400);
  await d.batch(env, [
    d.stmt(env, "INSERT OR IGNORE INTO recipe_blocks (device, maker, at) VALUES (?, ?, ?)", dev, id, Date.now()),
    d.stmt(env, "DELETE FROM recipe_follows WHERE device = ? AND maker = ?", dev, id),
    d.stmt(env, "DELETE FROM recipe_follows WHERE device = ? AND maker = ?", m.device, await makerId(dev)),
  ]);
  return d.json({ maker: id, blocked: true });
}

/** POST /api/recipes/makers/:id/flag — {device, reason}: [PRF-06] 사칭·나쁜 소개 신고, to the operator; three hide the 소개. */
async function rcMakerFlag(d, env, request, id) {
  const body = await d.readJson(request, 512);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  if (!PROFILE_FLAG_REASONS.includes(body.reason)) return d.json({ error: "reason", field: "reason" }, 400);
  const m = await d.one(env, "SELECT device FROM recipe_makers WHERE maker = ?", id);
  if (!m) return d.json({ error: "not_found" }, 404);
  if (m.device === dev) return d.json({ error: "own" }, 400);
  await d.run(env, "INSERT OR IGNORE INTO recipe_maker_flags (maker, device, reason, at) VALUES (?, ?, ?, ?)", id, dev, body.reason, Date.now());
  const n = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_maker_flags WHERE maker = ?", id);
  if (n.n >= COMMUNITY_LIMITS.flagsToHideBio) await d.run(env, "UPDATE recipe_makers SET bio_hidden = 1 WHERE maker = ? AND bio IS NOT NULL", id);
  return d.json({ maker: id, flagged: true });
}

/** A maker row as a face in a list: id, nickname, emoji and colour. */
const rcFace = (r) => ({ maker: r.maker, nick: r.nick ?? null, face: r.face ?? null, color: r.color ?? null });

/**
 * GET /api/recipes/makers/me/following — [PRF-02] 마을 첫 화면 '팔로잉' 줄과 목록: each maker's face, whether a public recipe came in the
 * last 7 days ([fresh], newest first), 알림 끔; and the makers this phone blocked, so the 장터 can leave them out on the phone.
 */
async function rcFollowing(d, env, request) {
  const dev = await rcReadDev(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const rows = await d.all(env, `SELECT f.maker, f.quiet, f.at, m.face, m.color, m.hidden, m.closed, n.nick,
      (SELECT MAX(r.created_at) FROM recipes r WHERE r.maker = f.maker AND r.state = 'visible' AND r.scope = 'public') AS latest
    FROM recipe_follows f LEFT JOIN recipe_makers m ON m.maker = f.maker LEFT JOIN nicks n ON n.device = m.device
    WHERE f.device = ? ORDER BY latest DESC, f.at DESC LIMIT 200`, dev);
  const blocked = await d.all(env, `SELECT b.maker, m.face, m.color, n.nick FROM recipe_blocks b LEFT JOIN recipe_makers m ON m.maker = b.maker
    LEFT JOIN nicks n ON n.device = m.device WHERE b.device = ? ORDER BY b.at DESC`, dev);
  return d.json({
    items: rows.filter((r) => !r.hidden && !r.closed).map((r) => ({
      ...rcFace(r.maker === "operator" ? { ...r, nick: "밤낮 운영" } : r), quiet: r.quiet === 1,
      ...(r.latest ? { latest: new Date(r.latest).toISOString() } : {}), fresh: Boolean(r.latest && r.latest > now - 7 * DAY),
    })),
    blocked: blocked.map(rcFace),
  });
}

/**
 * GET /api/recipes/makers/me/followers — [PRF-02] 내 팔로워, for the maker alone: those with a 프로필 of their own by face and name,
 * the rest only counted (이름 없는 분 N명).
 */
async function rcFollowers(d, env, request) {
  const dev = await rcReadDev(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const me = await rcMakerOf(d, env, dev, Date.now());
  const rows = await d.all(env, `SELECT m.maker, m.face, m.color, m.hidden, n.nick, f.at FROM recipe_follows f
    LEFT JOIN recipe_makers m ON m.device = f.device LEFT JOIN nicks n ON n.device = f.device
    WHERE f.maker = ? ORDER BY f.at DESC LIMIT 500`, me.maker);
  const named = rows.filter((r) => r.maker && !r.hidden && (r.nick || r.face));
  return d.json({ total: rows.length, items: named.map((r) => ({ ...rcFace(r), at: new Date(r.at).toISOString() })), unnamed: rows.length - named.length });
}

/**
 * GET /api/recipes/makers/me/inbox?since= — [PRF-03] 내 프로필 '최근 알림', worked out when asked (nothing stored): 새 팔로워, 내 작품에 온
 * 질문, 내 것을 고친 판, and where each public recipe stands against [PROFILE_MILESTONES] with the badges, which the phone compares with
 * what it already showed.
 */
async function rcInbox(d, env, request, url) {
  const dev = await rcReadDev(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const since = Math.max(now - 30 * DAY, Date.parse(url.searchParams.get("since") ?? "") || now - 7 * DAY);
  const me = await rcMakerOf(d, env, dev, now);
  const fresh = await d.all(env, `SELECT m.maker, m.face, m.color, m.hidden, n.nick FROM recipe_follows f
    LEFT JOIN recipe_makers m ON m.device = f.device LEFT JOIN nicks n ON n.device = f.device
    WHERE f.maker = ? AND f.at > ? ORDER BY f.at DESC LIMIT 50`, me.maker, since);
  const questions = await d.all(env, `SELECT q.grp AS code, q.body, q.created_at, r.title FROM posts q JOIN recipes r ON r.code = q.grp
    WHERE q.company_id = 'recipe' AND q.kind = 'question' AND q.parent IS NULL AND q.status = 'shown' AND r.owner = ? AND q.device != ?
      AND q.created_at > ? ORDER BY q.created_at DESC LIMIT 20`, dev, dev, since);
  const remixes = await d.all(env, `SELECT r.code, r.title, r.created_at, p.title AS parent_title FROM recipes r JOIN recipes p ON p.code = r.parent
    WHERE p.owner = ? AND r.owner != ? AND r.state = 'visible' AND r.scope = 'public' AND r.created_at > ? ORDER BY r.created_at DESC LIMIT 20`, dev, dev, since);
  const own = await d.all(env, "SELECT code, title, likes, applied FROM recipes WHERE owner = ? AND state = 'visible' AND scope = 'public'", dev);
  const reached = (n) => PROFILE_MILESTONES.filter((x) => n >= x).pop() ?? null;
  const milestones = own.flatMap((r) => [["users", r.applied], ["likes", r.likes]].filter(([, n]) => reached(n))
    .map(([kind, n]) => ({ code: r.code, title: r.title, kind, n: reached(n) })));
  const sums = { recipes: own.length, likes: own.reduce((s, r) => s + r.likes, 0), users: own.reduce((s, r) => s + r.applied, 0) };
  const named = fresh.filter((r) => r.maker && !r.hidden && (r.nick || r.face));
  return d.json({
    maker: me.maker,
    followers: { count: fresh.length, items: named.slice(0, 5).map(rcFace) },
    questions: questions.map((x) => ({ code: x.code, title: x.title, body: x.body.slice(0, 80), at: new Date(x.created_at).toISOString() })),
    remixes: remixes.map((x) => ({ code: x.code, title: x.title, parentTitle: x.parent_title, at: new Date(x.created_at).toISOString() })),
    milestones,
    badges: await rcBadgesOf(d, env, dev, me, sums),
  });
}

/** GET /api/recipes/news?since= — for this phone's 소식: new recipes of the makers it follows and the makers' answers to its questions. */
async function rcNews(d, env, request, url) {
  const dev = await rcReadDev(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const since = Math.max(now - 30 * DAY, Date.parse(url.searchParams.get("since") ?? "") || now - 7 * DAY);
  // [PRF-04] 알림 끈 분은 빼요 (팔로우는 그대로).
  const followed = await d.all(env, "SELECT maker FROM recipe_follows WHERE device = ? AND quiet = 0", dev);
  const makers = followed.map((f) => f.maker);
  const fresh = makers.length ? await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner
    WHERE r.maker IN (${makers.map(() => "?").join(",")}) AND r.state = 'visible' AND r.scope = 'public' AND r.created_at > ?
    ORDER BY r.created_at DESC LIMIT 20`, ...makers, since) : [];
  // [RCX-10] A maker's reply under a question this phone asked on the maker's recipe.
  const replies = await d.all(env, `SELECT a.grp AS code, a.body, a.created_at, r.title FROM posts q
    JOIN posts a ON a.parent = q.id AND a.company_id = 'recipe' AND a.status = 'shown'
    JOIN recipes r ON r.code = q.grp AND a.device = r.owner
    WHERE q.company_id = 'recipe' AND q.device = ? AND q.kind = 'question' AND a.created_at > ? ORDER BY a.created_at DESC LIMIT 20`, dev, since);
  // [LIN-04] 1.35 수정판: someone else's new recipe made from one of this phone's (사용자 2026-10-30 "초기 제작자의 제작물 밑으로").
  const remixes = await d.all(env, `SELECT r.*, n.nick, p.title AS parent_title FROM recipes r JOIN recipes p ON p.code = r.parent
    LEFT JOIN nicks n ON n.device = r.owner
    WHERE p.owner = ? AND r.owner != ? AND r.state = 'visible' AND r.scope = 'public' AND r.created_at > ? ORDER BY r.created_at DESC LIMIT 20`, dev, dev, since);
  return d.json({
    recipes: await rcWithMaker(d, env, fresh, now),
    replies: replies.map((x) => ({ code: x.code, title: x.title, body: x.body.slice(0, 80), at: new Date(x.created_at).toISOString() })),
    remixes: (await rcWithMaker(d, env, remixes, now)).map((x, i) => ({ ...x, parentTitle: remixes[i].parent_title })),
  });
}

// ---- [PRF-07] 모음 ----

const SET_ID = /^s[0-9a-f]{12}$/;

/** A maker's 모음, each with the recipes still up in it (a 모음 with fewer than two left is not shown to others). */
async function rcSetsOf(d, env, maker, codes, mine) {
  const rows = await d.all(env, "SELECT * FROM recipe_sets WHERE maker = ? ORDER BY updated_at DESC LIMIT 20", maker);
  return rows.map((r) => {
    const all = r.codes.split(",");
    const up = all.filter((c) => codes.has(c));
    return { id: r.id, title: r.title, summary: r.summary, codes: up, ...(mine ? { missing: all.length - up.length, hidden: r.hidden === 1 } : {}) };
  }).filter((x) => mine || (!rows.find((r) => r.id === x.id).hidden && x.codes.length >= COMMUNITY_LIMITS.setCodes[0]));
}

/** Reads and checks a 모음's title, summary and codes: 2~6 of the maker's own public recipes, at most one 테마. */
async function rcReadSet(d, env, dev, body) {
  const title = cleanText(String(body.title ?? ""));
  const summary = cleanText(String(body.summary ?? ""));
  const [tMin, tMax] = COMMUNITY_LIMITS.setTitle;
  if (graphemes(title) < tMin || graphemes(title) > tMax) return { error: "title" };
  if (graphemes(summary) > COMMUNITY_LIMITS.setSummary) return { error: "summary" };
  for (const [field, t] of [["title", title], ["summary", summary]]) {
    const why = t ? textProblem(t) ?? (investAdvice(t) ? "invest_advice" : null) : null;
    if (why) return { error: why, field };
  }
  const want = Array.isArray(body.codes) ? [...new Set(body.codes.filter((c) => typeof c === "string" && CODE.test(c)))] : [];
  const [cMin, cMax] = COMMUNITY_LIMITS.setCodes;
  if (want.length < cMin || want.length > cMax) return { error: "codes" };
  const own = await d.all(env, `SELECT r.*, NULL AS nick FROM recipes r WHERE r.owner = ? AND r.state = 'visible' AND r.scope = 'public'
    AND r.code IN (${want.map(() => "?").join(",")})`, dev, ...want);
  if (own.length !== want.length) return { error: "not_yours", field: "codes" };
  const now = Date.now();
  const scopes = own.map((r) => listItem(r, now).scope);
  // One 테마 to wear and the tabs to put on it; two 테마 would fight over the same tabs (섞은 테마 규칙과 같이).
  if (scopes.filter((x) => x === "theme").length > 1) return { error: "two_themes", field: "codes" };
  return { title, summary, codes: want };
}

/** POST /api/recipes/sets — {device, title, summary?, codes}: a new 모음. */
async function rcSetPost(d, env, request) {
  if (await d.paused(env, "PLAZA_WRITE")) return d.json({ error: "paused" }, 503);
  const body = await d.readJson(request, 2048);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  if (!(await d.legalOpen(env, "recipe.publish", now))) return d.json({ error: "not_yet", field: "recipe.publish" }, 403);
  const set = await rcReadSet(d, env, dev, body);
  if (set.error) return d.json({ error: set.error, field: set.field ?? set.error }, 400);
  const n = await d.one(env, "SELECT COUNT(*) AS n FROM recipe_sets WHERE owner = ?", dev);
  if (n.n >= COMMUNITY_LIMITS.sets) return d.json({ error: "too_many" }, 400);
  const hit = await d.overLimit(env, now, [["device", `rset|${dev}`, COMMUNITY_LIMITS.setsPerDay, DAY]]);
  if (hit) return hit;
  const m = await rcMakerOf(d, env, dev, now);
  const id = "s" + (await sha256hex(`${dev}|${now}|${d.randomId(8)}`)).slice(0, 12);
  await d.run(env, "INSERT INTO recipe_sets (id, owner, maker, title, summary, codes, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    id, dev, m.maker, set.title, set.summary, set.codes.join(","), now, now);
  return d.json({ id, title: set.title, summary: set.summary, codes: set.codes }, 201);
}

/** GET /api/recipes/sets/:id — a 모음 with its recipes, for anyone (not when its maker's profile is hidden or blocked this phone). */
async function rcSetGet(d, env, request, id) {
  const dev = await rcReadDev(d, request, null);
  const s = await d.one(env, "SELECT * FROM recipe_sets WHERE id = ?", id);
  if (!s) return d.json({ error: "not_found" }, 404);
  const mine = Boolean(dev && dev === s.owner);
  const m = await d.one(env, "SELECT * FROM recipe_makers WHERE maker = ?", s.maker);
  if (!mine && (s.hidden || m?.hidden || m?.closed || (await rcBlocked(d, env, dev, s.maker, s.owner)).theirs)) return d.json({ error: "not_found" }, 404);
  const codes = s.codes.split(",");
  const rows = await d.all(env, `SELECT r.*, n.nick FROM recipes r LEFT JOIN nicks n ON n.device = r.owner WHERE r.owner = ? AND r.state = 'visible'
    AND r.scope = 'public' AND r.code IN (${codes.map(() => "?").join(",")})`, s.owner, ...codes);
  const items = await rcWithMaker(d, env, rows, Date.now());
  // 테마 first, then the tabs in the order the maker put them.
  items.sort((a, b) => (a.scope === "theme" ? -1 : 0) - (b.scope === "theme" ? -1 : 0) || codes.indexOf(a.code) - codes.indexOf(b.code));
  if (!mine && items.length < COMMUNITY_LIMITS.setCodes[0]) return d.json({ error: "not_found" }, 404);
  return d.json({ id, title: s.title, summary: s.summary, maker: s.maker, mine, items, ...(mine ? { missing: codes.length - items.length } : {}) });
}

/** POST /api/recipes/sets/:id — the maker's change ({title?, summary?, codes?}); DELETE — gone. */
async function rcSetEdit(d, env, request, id, del) {
  const body = del ? {} : await d.readJson(request, 2048);
  const dev = await rcReadDev(d, request, body);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const s = await d.one(env, "SELECT * FROM recipe_sets WHERE id = ?", id);
  if (!s) return d.json({ error: "not_found" }, 404);
  if (s.owner !== dev) return d.json({ error: "not_yours" }, 403);
  if (del) {
    await d.run(env, "DELETE FROM recipe_sets WHERE id = ?", id);
    return d.json({ id, deleted: true });
  }
  const set = await rcReadSet(d, env, dev, { title: body.title ?? s.title, summary: body.summary ?? s.summary, codes: body.codes ?? s.codes.split(",") });
  if (set.error) return d.json({ error: set.error, field: set.field ?? set.error }, 400);
  await d.run(env, "UPDATE recipe_sets SET title = ?, summary = ?, codes = ?, updated_at = ? WHERE id = ?", set.title, set.summary, set.codes.join(","), Date.now(), id);
  return d.json({ id, title: set.title, summary: set.summary, codes: set.codes });
}

// ---- [PRF-08] 팔로잉 피드 ----

/** GET /api/recipes/feed?cursor= — the public recipes of the makers this phone follows, newest first (not edge-cached: it is per phone). */
async function rcFeed(d, env, request, url) {
  const dev = await rcReadDev(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const cursor = Math.max(0, Math.min(300, Number(url.searchParams.get("cursor")) || 0));
  const size = 30;
  const rows = await d.all(env, `SELECT r.*, n.nick FROM recipes r JOIN recipe_follows f ON f.maker = r.maker AND f.device = ?
    LEFT JOIN recipe_makers m ON m.maker = r.maker LEFT JOIN nicks n ON n.device = r.owner
    WHERE r.state = 'visible' AND r.scope = 'public' AND COALESCE(m.hidden, 0) = 0 AND COALESCE(m.closed, 0) = 0
    ORDER BY r.created_at DESC LIMIT ? OFFSET ?`, dev, size + 1, cursor);
  return d.json({ items: await rcWithMaker(d, env, rows.slice(0, size), Date.now()), next: rows.length > size ? cursor + size : null },
    200, { "cache-control": "private, no-store" });
}

// ---- [PRF-09] 작품 통계 ----

/**
 * GET /api/recipes/makers/me/stats — for the maker alone: each public recipe's 쓰는 분, 좋아요, 7일 뒤에도 쓰는 비율, this week's and the
 * 28 days' 넣은 수 by day (counted per recipe and day, never per phone). The app shows the summary to all and the daily chart with 프로.
 */
async function rcStats(d, env, request) {
  const dev = await rcReadDev(d, request, null);
  if (!dev) return d.json({ error: "device", field: "device" }, 400);
  const now = Date.now();
  const kst = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);
  const from28 = kst(now - 27 * DAY);
  const from7 = kst(now - 6 * DAY);
  const own = await d.all(env, "SELECT code, title, likes, dislikes, applied, kept7, created_at FROM recipes WHERE owner = ? AND state = 'visible' AND scope = 'public' ORDER BY applied DESC LIMIT 60", dev);
  const days = own.length ? await d.all(env, `SELECT code, day, applied FROM recipe_counts WHERE day >= ? AND code IN (${own.map(() => "?").join(",")})`,
    from28, ...own.map((r) => r.code)) : [];
  const series = (code) => Array.from({ length: 28 }, (_, i) => {
    const day = kst(now - (27 - i) * DAY);
    return days.find((x) => x.code === code && x.day === day)?.applied ?? 0;
  });
  const items = own.map((r) => {
    const s = series(r.code);
    return { code: r.code, title: r.title, users: r.applied, likes: r.likes, dislikes: r.dislikes,
      kept: r.applied < 20 ? null : Math.min(100, Math.trunc((100 * r.kept7) / r.applied)), week: s.slice(-7).reduce((a, b) => a + b, 0), days: s };
  });
  return d.json({ from: from28, week: items.reduce((a, r) => a + r.week, 0), weekFrom: from7,
    users: items.reduce((a, r) => a + r.users, 0), likes: items.reduce((a, r) => a + r.likes, 0), items });
}

// ---- [PRF-10] 이달의 제작자 ----

const monthOf = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 7);

/**
 * GET /api/recipes/makers/month?style= — three makers of the month for a kind of work: the operator's picks first, then by how many
 * phones put on their recipes of that kind this month. Never the operator's own, hidden profiles or those with nothing up.
 */
async function rcMonthMakers(d, env, url) {
  const now = Date.now();
  const style = RC_STYLE_KEYS.includes(url.searchParams.get("style")) ? url.searchParams.get("style") : null;
  const month = monthOf(now);
  const picked = await d.all(env, "SELECT maker FROM recipe_month_makers WHERE month = ? AND style = ? ORDER BY rank", month, style ?? "ALL");
  const counted = await d.all(env, `SELECT r.maker, SUM(c.applied) AS n FROM recipe_counts c JOIN recipes r ON r.code = c.code
    WHERE c.day >= ? AND r.state = 'visible' AND r.scope = 'public' AND r.owner != 'operator' AND r.maker IS NOT NULL
      ${style ? "AND (',' || r.styles || ',') LIKE ?" : ""}
    GROUP BY r.maker HAVING n > 0 ORDER BY n DESC LIMIT 10`, `${month}-01`, ...(style ? [`%,${style},%`] : []));
  const ids = [...new Set([...picked.map((p) => p.maker), ...counted.map((c) => c.maker)])];
  const out = [];
  for (const id of ids) {
    if (out.length >= 3) break;
    const m = await d.one(env, `SELECT m.maker, m.face, m.color, m.bio, m.bio_hidden, n.nick FROM recipe_makers m LEFT JOIN nicks n ON n.device = m.device
      WHERE m.maker = ? AND m.hidden = 0 AND m.closed = 0`, id);
    if (!m) continue;
    out.push({ ...rcFace(m), ...(m.bio && !m.bio_hidden ? { bio: m.bio } : {}), picked: picked.some((p) => p.maker === id),
      users: counted.find((c) => c.maker === id)?.n ?? 0 });
  }
  return d.json({ month, style, items: out }, 200, { "cache-control": "public, max-age=600" });
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
  if (path === "/api/recipes/makers/me/following" && method === "GET") return rcFollowing(d, env, request);
  if (path === "/api/recipes/makers/me/followers" && method === "GET") return rcFollowers(d, env, request);
  if (path === "/api/recipes/makers/me/inbox" && method === "GET") return rcInbox(d, env, request, url);
  if (path === "/api/recipes/makers/me/stats" && method === "GET") return rcStats(d, env, request);
  if (path === "/api/recipes/makers/month" && method === "GET") return rcMonthMakers(d, env, url);
  if (path === "/api/recipes/feed" && method === "GET") return rcFeed(d, env, request, url);
  if (path === "/api/recipes/sets" && method === "POST") return rcSetPost(d, env, request);
  if (path === "/api/recipes/images" && method === "POST") return rcImagePost(d, env, request);
  let m = path.match(/^\/api\/recipes\/requests\/(q[A-Za-z0-9]{12})\/(like|take|done|delete)$/);
  if (m && method === "POST") return rcRequestAct(d, env, request, m[1], m[2]);
  m = path.match(/^\/api\/recipes\/makers\/([0-9a-f]{12}|operator)(\/follow|\/block|\/flag)?$/);
  if (m && !m[2] && method === "GET") return rcMakerPage(d, env, request, m[1]);
  if (m && m[2] === "/follow" && method === "POST") return rcFollow(d, env, request, m[1]);
  if (m && m[2] === "/block" && method === "POST") return rcBlock(d, env, request, m[1]);
  if (m && m[2] === "/flag" && method === "POST") return rcMakerFlag(d, env, request, m[1]);
  m = path.match(/^\/api\/recipes\/sets\/(s[0-9a-f]{12})$/);
  if (m && method === "GET") return rcSetGet(d, env, request, m[1]);
  if (m && method === "POST") return rcSetEdit(d, env, request, m[1], false);
  if (m && method === "DELETE") return rcSetEdit(d, env, request, m[1], true);
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
  if (path === "/recipes/requests" && method === "GET") {
    // Newest first, hidden ones too, so the operator can put one back. Never who wrote it.
    const rows = await d.all(env, "SELECT id, style, body, likes, state, code, hidden, created_at FROM recipe_requests ORDER BY created_at DESC LIMIT 100");
    return d.json({ items: rows.map((r) => ({ ...r, hidden: r.hidden === 1, at: new Date(r.created_at).toISOString() })) });
  }
  m = path.match(/^\/recipes\/requests\/(q[A-Za-z0-9]{12})\/(hide|show)$/);
  if (m && method === "POST") {
    await d.run(env, "UPDATE recipe_requests SET hidden = ? WHERE id = ?", m[2] === "hide" ? 1 : 0, m[1]);
    return d.json({ id: m[1], hidden: m[2] === "hide" });
  }
  // [PRF-06] 1.36 신고된 프로필: 소개와 이름, 신고 이유별 수 (who never); 소개만 가리기, 프로필 닫기, 되돌리기.
  if (path === "/recipes/makers/flagged" && method === "GET") {
    const rows = await d.all(env, `SELECT m.maker, m.face, m.bio, m.bio_hidden, m.closed, n.nick, COUNT(f.device) AS flags, MAX(f.at) AS last,
        GROUP_CONCAT(f.reason) AS reasons FROM recipe_maker_flags f JOIN recipe_makers m ON m.maker = f.maker LEFT JOIN nicks n ON n.device = m.device
      GROUP BY m.maker ORDER BY last DESC LIMIT 100`);
    return d.json({ items: rows.map((r) => {
      const tally = {};
      for (const x of (r.reasons ?? "").split(",").filter(Boolean)) tally[x] = (tally[x] ?? 0) + 1;
      return { maker: r.maker, nick: r.nick ?? null, face: r.face ?? null, bio: r.bio ?? null, bioHidden: r.bio_hidden === 1, closed: r.closed === 1,
        flags: r.flags, reasons: tally, at: new Date(r.last).toISOString() };
    }) });
  }
  // [PRF-10] 이달의 제작자 고르기 (빈 목록이면 센 순서로 돌아가요).
  if (path === "/recipes/makers/month" && method === "GET") {
    const rows = await d.all(env, "SELECT * FROM recipe_month_makers WHERE month = ? ORDER BY style, rank", monthOf(now));
    return d.json({ month: monthOf(now), items: rows });
  }
  if (path === "/recipes/makers/month" && method === "POST") {
    const body = await d.readJson(request, 2048);
    const style = body.style === "ALL" || RC_STYLE_KEYS.includes(body.style) ? body.style : null;
    if (!style) return d.json({ error: "style", field: "style" }, 400);
    const makers = Array.isArray(body.makers) ? [...new Set(body.makers.filter((x) => typeof x === "string" && /^[0-9a-f]{12}$/.test(x)))].slice(0, 3) : [];
    const month = monthOf(now);
    await d.batch(env, [d.stmt(env, "DELETE FROM recipe_month_makers WHERE month = ? AND style = ?", month, style),
      ...makers.map((x, i) => d.stmt(env, "INSERT INTO recipe_month_makers (month, style, rank, maker) VALUES (?, ?, ?, ?)", month, style, i + 1, x))]);
    return d.json({ month, style, makers });
  }
  m = path.match(/^\/recipes\/sets\/(s[0-9a-f]{12})\/(hide|show)$/);
  if (m && method === "POST") {
    await d.run(env, "UPDATE recipe_sets SET hidden = ? WHERE id = ?", m[2] === "hide" ? 1 : 0, m[1]);
    return d.json({ id: m[1], hidden: m[2] === "hide" });
  }
  m = path.match(/^\/recipes\/makers\/([0-9a-f]{12})\/(bio-hide|bio-show|close|open|clear)$/);
  if (m && method === "POST") {
    const [, id, what] = m;
    if (what === "clear") await d.run(env, "DELETE FROM recipe_maker_flags WHERE maker = ?", id);
    else await d.run(env, `UPDATE recipe_makers SET ${what.startsWith("bio") ? "bio_hidden" : "closed"} = ? WHERE maker = ?`,
      what === "bio-hide" || what === "close" ? 1 : 0, id);
    return d.json({ maker: id, done: what });
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
    // [PRF-05][PRF-06] 1.36 차단과 프로필 신고도, 이 폰이 한 것과 이 폰의 프로필에 대한 것 모두.
    d.stmt(env, "DELETE FROM recipe_sets WHERE owner = ?", dev),
    d.stmt(env, "DELETE FROM recipe_month_makers WHERE maker IN (SELECT maker FROM recipe_makers WHERE device = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_blocks WHERE device = ?", dev),
    d.stmt(env, "DELETE FROM recipe_blocks WHERE maker IN (SELECT maker FROM recipe_makers WHERE device = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_maker_flags WHERE device = ?", dev),
    d.stmt(env, "DELETE FROM recipe_maker_flags WHERE maker IN (SELECT maker FROM recipe_makers WHERE device = ?)", dev),
    d.stmt(env, "DELETE FROM recipe_makers WHERE device = ?", dev),
    d.stmt(env, "DELETE FROM recipe_images WHERE owner = ?", dev),
  ];
}
