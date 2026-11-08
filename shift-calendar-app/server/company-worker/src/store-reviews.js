import { all, audit, json, notifyAdmins, one, readJson, refuse, run } from "./index.js";
import { playReady, playToken } from "./money.js";
import { DAY } from "./units.js";

// ---- 1.38 [ADM3-18] 스토어 리뷰 받은함 ----
//
// 보고서 3-2항 O4. PLAY_SERVICE_ACCOUNT가 있으면 매시 작업이 Play Developer API(reviews.list, 최근 7일만 줘요)로 새 리뷰를 가져와요.
// 남기는 것: 리뷰 번호·별점·글·앱 판·언어·때. 작성자 이름·기기 모델은 남기지 않아요(답에 필요 없고, Play에서 늘 볼 수 있어요).
// Claude(운영 열쇠)는 답 초안만 적을 수 있고, Play로 보내기는 최고 관리자만 해요(바깥으로 나가는 일이라 사람이 눌러요).
// 180일 지난 리뷰는 지워요.

export const REVIEWS = {
  api: "https://androidpublisher.googleapis.com/androidpublisher/v3/applications",
  scope: "https://www.googleapis.com/auth/androidpublisher",
  keepDays: 180,
  replyMax: 350,
  /** 이 별점 이하는 바로 알려요. */
  urgentStars: 2,
};

export const REVIEW_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS store_reviews (id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL DEFAULT '', app TEXT NOT NULL DEFAULT '',
    lang TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL, reply TEXT NOT NULL DEFAULT '', reply_at INTEGER, replied_by TEXT NOT NULL DEFAULT '',
    draft TEXT NOT NULL DEFAULT '', draft_by TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'new', fetched_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS store_reviews_list ON store_reviews (status, at)",
];

const reviewClean = (v, n) => String(v ?? "").replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, " ").trim().slice(0, n);

/** reviews.list 한 줄을 남길 모양으로. 사용자 글이 없으면 null. */
export function readReview(r) {
  const user = r?.comments?.find((c) => c.userComment)?.userComment;
  const dev = r?.comments?.find((c) => c.developerComment)?.developerComment;
  if (!r?.reviewId || !user) return null;
  const sec = Number(user.lastModified?.seconds);
  return {
    id: reviewClean(r.reviewId, 120), stars: Math.min(5, Math.max(1, Number(user.starRating) || 1)), text: reviewClean(user.text, 4000), app: reviewClean(user.appVersionName, 20),
    lang: reviewClean(user.reviewerLanguage, 10), at: Number.isFinite(sec) ? sec * 1000 : Date.now(),
    reply: dev ? reviewClean(dev.text, 1000) : "", replyAt: dev?.lastModified?.seconds ? Number(dev.lastModified.seconds) * 1000 : null,
  };
}

/** 매시 작업: 새 리뷰를 가져와 남기고, 별 1~2개 새 리뷰는 바로 알려요. 서비스 계정이 없으면 아무것도 안 해요. */
export async function reviewTick(env, now = Date.now()) {
  const account = playReady(env);
  if (!account) return { skipped: "no_play" };
  const pkg = env.PLAY_PACKAGE || "com.bamnat.app";
  const token = await playToken(env, account, now, REVIEWS.scope);
  let next = null;
  let fresh = 0;
  let urgent = 0;
  for (let page = 0; page < 5; page++) {
    const u = new URL(`${REVIEWS.api}/${encodeURIComponent(pkg)}/reviews`);
    u.searchParams.set("maxResults", "100");
    if (next) u.searchParams.set("token", next);
    const res = await fetch(u, { headers: { authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(8000) });
    if (res.status === 403) throw new Error("play_denied: Play Console › 사용자 및 권한에서 서비스 계정에 '리뷰 보기·답하기'를 주세요");
    if (!res.ok) throw new Error(`play_reviews ${res.status}`);
    const body = await res.json();
    for (const raw of body.reviews ?? []) {
      const r = readReview(raw);
      if (!r) continue;
      const had = await one(env, "SELECT id, text FROM store_reviews WHERE id = ?", r.id);
      await run(env, `INSERT INTO store_reviews (id, stars, text, app, lang, at, reply, reply_at, status, fetched_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
        ON CONFLICT (id) DO UPDATE SET stars = ?2, text = ?3, app = ?4, at = ?6, reply = CASE WHEN ?7 <> '' THEN ?7 ELSE reply END,
        reply_at = COALESCE(?8, reply_at), status = CASE WHEN ?7 <> '' AND status <> 'replied' THEN 'replied' WHEN text <> ?3 AND status = 'replied' THEN 'new' ELSE status END,
        fetched_at = ?10`, r.id, r.stars, r.text, r.app, r.lang, r.at, r.reply, r.replyAt, r.reply ? "replied" : "new", now);
      if (!had) {
        fresh += 1;
        if (r.stars <= REVIEWS.urgentStars && !r.reply) urgent += 1;
      }
    }
    next = body.tokenPagination?.nextPageToken ?? null;
    if (!next) break;
  }
  await run(env, "DELETE FROM store_reviews WHERE at < ?", now - REVIEWS.keepDays * DAY);
  if (urgent) await notifyAdmins(env, "reviews", { title: "낮은 별점 리뷰", body: `별 1~2개 새 리뷰 ${urgent}개. 초안을 보고 답해 주세요`, url: "/admin/#/reviews", urgent: true }, now);
  else if (fresh) await notifyAdmins(env, "reviews", { title: "새 스토어 리뷰", body: `새 리뷰 ${fresh}개`, url: "/admin/#/reviews" }, now);
  return { fresh, urgent };
}

export async function reviewList(env, now = Date.now(), status = "open") {
  const where = status === "all" ? "" : status === "replied" ? "WHERE status = 'replied'" : "WHERE status <> 'replied'";
  const rows = await all(env, `SELECT * FROM store_reviews ${where} ORDER BY CASE WHEN status <> 'replied' AND stars <= 2 THEN 0 ELSE 1 END, at DESC LIMIT 200`);
  const stats = await all(env, "SELECT stars, COUNT(*) AS n FROM store_reviews WHERE at >= ? GROUP BY stars", now - 30 * DAY);
  const total = stats.reduce((n, s) => n + s.n, 0);
  return {
    ready: Boolean(playReady(env)),
    items: rows.map((r) => ({ id: r.id, stars: r.stars, text: r.text, app: r.app, lang: r.lang, at: r.at, reply: r.reply, replyAt: r.reply_at, repliedBy: r.replied_by,
      draft: r.draft, draftBy: r.draft_by, status: r.status })),
    month: { total, avg: total ? Math.round((stats.reduce((n, s) => n + s.stars * s.n, 0) / total) * 10) / 10 : null,
      byStars: Object.fromEntries([1, 2, 3, 4, 5].map((s) => [s, stats.find((x) => x.stars === s)?.n ?? 0])) },
    waiting: (await one(env, "SELECT COUNT(*) AS n FROM store_reviews WHERE status <> 'replied'"))?.n ?? 0,
    replyMax: REVIEWS.replyMax,
  };
}

async function reply(env, id, text, now) {
  const account = playReady(env);
  if (!account) refuse("play", "play");
  const r = await one(env, "SELECT id FROM store_reviews WHERE id = ?", id);
  if (!r) return json({ error: "not_found" }, 404);
  const body = reviewClean(text, REVIEWS.replyMax + 1);
  if (!body || body.length > REVIEWS.replyMax) refuse("text", "text");
  const pkg = env.PLAY_PACKAGE || "com.bamnat.app";
  const token = await playToken(env, account, now, REVIEWS.scope);
  const res = await fetch(`${REVIEWS.api}/${encodeURIComponent(pkg)}/reviews/${encodeURIComponent(id)}:reply`, {
    method: "POST", headers: { authorization: `Bearer ${token}`, "content-type": "application/json" }, body: JSON.stringify({ replyText: body }),
    signal: AbortSignal.timeout(8000),
  });
  if (!res.ok) return json({ error: "play", status: res.status }, 502);
  await run(env, "UPDATE store_reviews SET reply = ?, reply_at = ?, replied_by = ?, status = 'replied', draft = '' WHERE id = ?", body, now, env.actor?.name ?? "관리자", id);
  await audit(env, now, "review_reply", id.slice(0, 40), body.slice(0, 80));
  return json({ ok: true });
}

/** GET /reviews, POST /reviews/<id>/draft {text}, POST /reviews/<id>/reply {text}(최고 관리자), POST /reviews/<id>/seen, POST /reviews/fetch. */
export async function reviewRoute(request, env, url, path, method, now) {
  if (path === "/reviews" && method === "GET") return json(await reviewList(env, now, url.searchParams.get("status") ?? "open"));
  if (path === "/reviews/fetch" && method === "POST") {
    if (!env.actor?.owner) return json({ error: "no_permission", area: "owner" }, 403);
    try {
      return json(await reviewTick(env, now));
    } catch (e) {
      return json({ error: String(e?.message ?? e).startsWith("play_denied") ? "play_denied" : "play" }, 502);
    }
  }
  const m = path.match(/^\/reviews\/([A-Za-z0-9_:.-]{4,120})\/(draft|reply|seen)$/);
  if (!m || method !== "POST") return null;
  const body = await readJson(request, 4096);
  if (m[2] === "reply") {
    if (!env.actor?.owner) return json({ error: "no_permission", area: "owner" }, 403);
    return reply(env, m[1], body?.text, now);
  }
  if (m[2] === "draft") {
    const text = reviewClean(body?.text, REVIEWS.replyMax);
    const r = await run(env, "UPDATE store_reviews SET draft = ?, draft_by = ? WHERE id = ?", text, env.actor?.name ?? "관리자", m[1]);
    if (!r?.meta?.changes) return json({ error: "not_found" }, 404);
    await audit(env, now, "review_draft", m[1].slice(0, 40), text.slice(0, 80));
    return json({ ok: true });
  }
  const r = await run(env, "UPDATE store_reviews SET status = 'seen' WHERE id = ? AND status = 'new'", m[1]);
  return json({ ok: Boolean(r?.meta?.changes) });
}

/** 매일 아침 요약용: 답 안 한 리뷰 수, 그중 별 1~2개. */
export async function reviewCounts(env) {
  try {
    const r = await one(env, `SELECT COUNT(*) AS n, SUM(CASE WHEN stars <= ? THEN 1 ELSE 0 END) AS low FROM store_reviews WHERE status <> 'replied'`, REVIEWS.urgentStars);
    return { waiting: r?.n ?? 0, low: r?.low ?? 0 };
  } catch {
    return { waiting: 0, low: 0 };
  }
}

