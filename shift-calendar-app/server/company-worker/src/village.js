// 1.36 마을 (조사 /mnt/project-files/조사/1.36-마을-전체-조사.md, 사용자 결정 2026-10-31 "전부 1.36"): what 마을 keeps of its own,
// apart from the plaza's posts (index.js) and the recipes' community (recipe-community.js).
// [VIL-11] 이번 주 이야기 주제: one line the operator sets for a kind of work's plaza each week (KST, from Monday); none, none shown.
import { DAY, HOUR } from "./units.js";
import { lookOf } from "./recipes.js";

export const VILLAGE_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS plaza_topics (room TEXT NOT NULL, week TEXT NOT NULL, text TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (room, week))`,
  // [VIL-19] 이달의 주제 공모: the month's theme for a kind of work, and the (up to three) picks at its end.
  `CREATE TABLE IF NOT EXISTS village_contests (month TEXT NOT NULL, style TEXT NOT NULL, theme TEXT NOT NULL, picks TEXT NOT NULL DEFAULT '[]',
    at INTEGER NOT NULL, PRIMARY KEY (month, style))`,
  // [VIL-19] What makers put in: one of their own public recipes, a few a month.
  `CREATE TABLE IF NOT EXISTS village_entries (month TEXT NOT NULL, style TEXT NOT NULL, code TEXT NOT NULL, device TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (month, style, code))`,
  "CREATE INDEX IF NOT EXISTS village_entries_device ON village_entries (device, month)",
];

export const VILLAGE_LIMITS = { topic: 60, theme: 40, entriesPerMonth: 3, picks: 3 };
/** The kinds of work a contest is for (the recipes' style keys). */
export const CONTEST_STYLES = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];

/** The month (KST) [ms] is in, as yyyy-mm, and the one before it. */
export function contestMonth(ms) {
  return new Date(ms + 9 * HOUR).toISOString().slice(0, 7);
}
export function monthBefore(month) {
  const [y, m] = month.split("-").map(Number);
  return m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, "0")}`;
}

/** The recipes [codes] that are still up and public, code → title, in one read. */
async function shownTitles(d, env, codes) {
  if (!codes.length) return new Map();
  const rows = await d.all(env, `SELECT code, title, likes, applied AS users, body FROM recipes WHERE state = 'visible' AND scope = 'public'
    AND code IN (${codes.map(() => "?").join(",")})`, ...codes);
  return new Map(rows.map(({ body, ...r }) => [r.code, { ...r, look: body ? lookOf(body) : null }]));
}

/**
 * 1.36 [VIL-19] 마을 V19 이달의 주제 공모 (조사: 포켓몬 GO 정기 행사·Dribbble 주제, 사용자 관찰/공개 정보; 832 제작자 보상·830 이달의 테마와
 * 합침), for the app: GET /api/village/contest?style= — this month's theme and what this phone put in, and last month's picks;
 * POST /api/village/contest/enter {device, style, code, on} — put one of my public recipes in (or take it out).
 */
export async function villageRoute(d, request, env, path, url, now) {
  if (path === "/api/village/contest" && request.method === "GET") {
    const style = CONTEST_STYLES.includes(url.searchParams.get("style")) ? url.searchParams.get("style") : null;
    if (!style) return d.json({ error: "style", field: "style" }, 400);
    const month = contestMonth(now);
    const last = monthBefore(month);
    const id = d.deviceId(request.headers.get("x-device"));
    const dev = id ? await d.deviceHash(id) : null;
    const [here, before, mine, n] = await Promise.all([
      d.one(env, "SELECT theme FROM village_contests WHERE month = ? AND style = ?", month, style),
      d.one(env, "SELECT theme, picks FROM village_contests WHERE month = ? AND style = ?", last, style),
      dev ? d.all(env, "SELECT code FROM village_entries WHERE month = ? AND style = ? AND device = ?", month, style, dev) : [],
      d.one(env, "SELECT COUNT(*) AS n FROM village_entries WHERE month = ? AND style = ?", month, style),
    ]);
    const picks = before ? JSON.parse(before.picks) : [];
    const titles = await shownTitles(d, env, [...picks, ...mine.map((r) => r.code)]);
    return d.json({
      month, theme: here?.theme ?? null, entries: n?.n ?? 0, mine: mine.map((r) => r.code),
      // PC 확인 V26: what I put in, by name, for the card.
      mineTitles: mine.filter((r) => titles.has(r.code)).map((r) => ({ code: r.code, title: titles.get(r.code).title })),
      ...(before && picks.length ? { last: { month: last, theme: before.theme, picks: picks.filter((c) => titles.has(c))
        // PC 확인 f39bcfbe: each pick with its look, drawn as a small picture on the card.
        .map((c) => ({ code: c, title: titles.get(c).title, ...(titles.get(c).look ? { look: titles.get(c).look } : {}) })) } } : {}),
    });
  }
  if (path === "/api/village/contest/enter" && request.method === "POST") {
    const body = await d.readJson(request, 1024);
    const id = d.deviceId(body?.device);
    if (!id) return d.json({ error: "device", field: "device" }, 400);
    const dev = await d.deviceHash(id);
    if (!CONTEST_STYLES.includes(body.style)) return d.json({ error: "style", field: "style" }, 400);
    const month = contestMonth(now);
    if (!(await d.one(env, "SELECT 1 AS x FROM village_contests WHERE month = ? AND style = ?", month, body.style))) return d.json({ error: "not_found" }, 404);
    if (body.on === false) {
      await d.run(env, "DELETE FROM village_entries WHERE month = ? AND style = ? AND code = ? AND device = ?", month, body.style, String(body.code ?? ""), dev);
      return d.json({ month, code: body.code, entered: false });
    }
    const r = typeof body.code === "string" ? await d.one(env, "SELECT owner, state, scope, styles FROM recipes WHERE code = ?", body.code) : null;
    if (!r || r.owner !== dev || r.state !== "visible" || r.scope !== "public") return d.json({ error: "code", field: "code" }, 400);
    if (r.styles && !`,${r.styles},`.includes(`,${body.style},`)) return d.json({ error: "style", field: "style" }, 400);
    const n = await d.one(env, "SELECT COUNT(*) AS n FROM village_entries WHERE month = ? AND device = ?", month, dev);
    if ((n?.n ?? 0) >= VILLAGE_LIMITS.entriesPerMonth) return d.json({ error: "too_many" }, 400);
    await d.run(env, "INSERT OR IGNORE INTO village_entries (month, style, code, device, at) VALUES (?, ?, ?, ?, ?)", month, body.style, body.code, dev, now);
    return d.json({ month, code: body.code, entered: true }, 201);
  }
  return null;
}

/** The Monday (KST) of the week [ms] is in, as yyyy-mm-dd. */
export function topicWeek(ms) {
  const kst = new Date(ms + 9 * HOUR);
  const day = (kst.getUTCDay() + 6) % 7;
  return new Date(kst.getTime() - day * DAY).toISOString().slice(0, 10);
}

/** This week's topic of [room], or null. */
export async function topicOf(d, env, room, now) {
  const row = await d.one(env, "SELECT text FROM plaza_topics WHERE room = ? AND week = ?", room, topicWeek(now)).catch(() => null);
  return row?.text ?? null;
}

/**
 * The operator's side, under /api/admin: GET /village/topics (this week's of every room), POST /village/topics {room, text}
 * (an empty text takes it off). [rooms] are the kinds of work's plazas (index.js STYLE_ROOMS).
 */
export async function villageAdmin(d, request, env, path, now, rooms) {
  if (path === "/village/contests" || path === "/village/contests/pick") return contestAdmin(d, request, env, path, now);
  if (path !== "/village/topics") return null;
  const week = topicWeek(now);
  if (request.method === "GET") {
    const rows = await d.all(env, "SELECT room, text, at FROM plaza_topics WHERE week = ?", week);
    const by = new Map(rows.map((r) => [r.room, r]));
    return d.json({ week, items: Object.entries(rooms).map(([room, name]) => ({ room, name, text: by.get(room)?.text ?? "" })) });
  }
  if (request.method !== "POST") return null;
  const body = await d.readJson(request, 1024);
  if (!Object.hasOwn(rooms, body?.room)) return d.json({ error: "room", field: "room" }, 400);
  const text = typeof body.text === "string" ? body.text.replace(/\s+/g, " ").trim() : "";
  if (text.length > VILLAGE_LIMITS.topic) return d.json({ error: "long", field: "text" }, 400);
  if (!text) await d.run(env, "DELETE FROM plaza_topics WHERE room = ? AND week = ?", body.room, week);
  else await d.run(env, `INSERT INTO plaza_topics (room, week, text, at) VALUES (?, ?, ?, ?)
    ON CONFLICT (room, week) DO UPDATE SET text = excluded.text, at = excluded.at`, body.room, week, text, now);
  return d.json({ week, room: body.room, text });
}

/**
 * [VIL-19] The operator's side of 이달의 주제 공모: GET /village/contests (this month's and last month's, each kind of work, with the
 * entries by likes), POST /village/contests {style, theme} (this month's; empty takes it off while nothing is in),
 * POST /village/contests/pick {month, style, codes} (up to three entries as 이달의 테마; the makers' 보상 goes the 832 way).
 */
async function contestAdmin(d, request, env, path, now) {
  const month = contestMonth(now);
  if (request.method === "GET" && path === "/village/contests") {
    const months = [month, monthBefore(month)];
    const [rows, entries] = await Promise.all([
      d.all(env, "SELECT month, style, theme, picks FROM village_contests WHERE month IN (?, ?)", ...months),
      d.all(env, "SELECT month, style, code, device FROM village_entries WHERE month IN (?, ?)", ...months),
    ]);
    const titles = await shownTitles(d, env, [...new Set(entries.map((e) => e.code))]);
    const items = months.flatMap((m) => CONTEST_STYLES.map((style) => {
      const c = rows.find((r) => r.month === m && r.style === style);
      const list = entries.filter((e) => e.month === m && e.style === style && titles.has(e.code))
        .map((e) => ({ code: e.code, title: titles.get(e.code).title, likes: titles.get(e.code).likes ?? 0, users: titles.get(e.code).users ?? 0, who: e.device.slice(0, 8) }))
        .sort((a, b) => b.likes - a.likes || b.users - a.users);
      return { month: m, style, theme: c?.theme ?? "", picks: c ? JSON.parse(c.picks) : [], entries: list };
    }));
    return d.json({ month, items });
  }
  if (request.method !== "POST") return null;
  const body = await d.readJson(request, 2048);
  if (!CONTEST_STYLES.includes(body?.style)) return d.json({ error: "style", field: "style" }, 400);
  if (path === "/village/contests") {
    const theme = typeof body.theme === "string" ? body.theme.replace(/\s+/g, " ").trim() : "";
    if (theme.length > VILLAGE_LIMITS.theme) return d.json({ error: "long", field: "theme" }, 400);
    if (!theme) {
      const n = await d.one(env, "SELECT COUNT(*) AS n FROM village_entries WHERE month = ? AND style = ?", month, body.style);
      if (n?.n) return d.json({ error: "state" }, 409);
      await d.run(env, "DELETE FROM village_contests WHERE month = ? AND style = ?", month, body.style);
    } else {
      await d.run(env, `INSERT INTO village_contests (month, style, theme, picks, at) VALUES (?, ?, ?, '[]', ?)
        ON CONFLICT (month, style) DO UPDATE SET theme = excluded.theme, at = excluded.at`, month, body.style, theme, now);
    }
    return d.json({ month, style: body.style, theme });
  }
  const m = typeof body.month === "string" && /^\d{4}-\d{2}$/.test(body.month) ? body.month : null;
  const codes = Array.isArray(body.codes) ? [...new Set(body.codes.filter((c) => typeof c === "string"))] : null;
  if (!m || !codes || codes.length > VILLAGE_LIMITS.picks) return d.json({ error: "codes", field: "codes" }, 400);
  const c = await d.one(env, "SELECT 1 AS x FROM village_contests WHERE month = ? AND style = ?", m, body.style);
  if (!c) return d.json({ error: "not_found" }, 404);
  const ok = codes.length ? await d.all(env, `SELECT code FROM village_entries WHERE month = ? AND style = ? AND code IN (${codes.map(() => "?").join(",")})`,
    m, body.style, ...codes) : [];
  if (ok.length !== codes.length) return d.json({ error: "codes", field: "codes" }, 400);
  await d.run(env, "UPDATE village_contests SET picks = ?, at = ? WHERE month = ? AND style = ?", JSON.stringify(codes), now, m, body.style);
  return d.json({ month: m, style: body.style, picks: codes });
}
