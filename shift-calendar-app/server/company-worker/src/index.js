// [COMM-04] 밤낮 company rules: what coworkers at one company share, so the next person sets up in a
// minute. One Cloudflare Worker with one D1 database (binding DB); the developer app is at /admin.
//
// GET    /api/companies?q=        search by name; spaces, (주) and 주식회사 do not count [COMM-01]
// GET    /api/companies/:id       a company's setups: adopted ones, and ones coworkers are still checking
// POST   /api/reports             a coworker's setup, without any amounts [COMM-02]
// GET    /api/reports/:id         where a report stands: points, adopted or not, the reward
// DELETE /api/reports/:id         header X-Device: the sender takes their report back
// DELETE /api/mine                header X-Device: everything this install sent, gone
// POST   /api/votes               맞았어요 / 달라요 on a setup [COMM-03]
// GET    /api/board?tab=&q=        every company's rule sets: ones still being voted on, or adopted; q finds a company [COMM-07]
// GET    /api/companies/:id/setups/:setup/posts   the talk on one rule set [COMM-06]
// POST   /api/posts               a post in that talk; DELETE /api/posts/:id takes it back (header X-Device)
// POST   /api/posts/:id/flag      신고: three people hide a post until the developer looks
// *      /api/inquiries...        header X-Device: questions and problem reports from the app [ADMIN-02]
// GET    /api/inquiries/public    모두의 문의: questions sent as public, with the developer's answers
// POST   /api/inquiries/:id/same  나도 그래요; /flag 신고: three people hide a question until the developer looks
// GET    /admin/                  the developer app, installable on a phone [ADMIN-01][ADMIN-03]
// GET    /admin/join#<invite>     an invited admin installs the app and makes an account [ADMIN-04]
// GET    /api/admin/session       signed in or not, as a plain answer for the developer app's first screen [ADMUX-11]
// *      /api/admin/*             behind a sign-in: the owner's password (secret ADMIN_PASSWORD), or an
//                                 admin account the owner invited by email and approved, with only the
//                                 permissions the owner gave it
// *      /api/admin/dev...        개발사항 gathered from questions, each with its own history [ADMIN-10][ADMIN-11]
// *      /api/admin/templates...  자주 쓰는 답 shared by admins; inquiries also take 내가 맡기 and notes [ADMIN-12]
// GET    /api/config              the 정식 출시 switch and what goes with it, for every app once a day [MON-02];
//                                 AM 1.28 [FLAG-22] with flags (overrides), policy (처리방침 일정) and serverTime
// *      /api/admin/flags...      AM 1.28 [FLAG-21] 기능 관리: overrides, confirmations, /types, /policy, /checks
// GET    /api/passes              header X-Device: this install's 프로 passes and admin phone link [MON-08][MON-09]
// POST   /api/passes/code         a recovery code for the backup; /claim moves passes to a new phone with it
// POST   /api/admin-link          a six-digit code from the developer app ties this phone to that admin
// POST   /api/purchases           a Play purchase token, checked with Play when PLAY_SERVICE_ACCOUNT is set [MON-03]
// POST   /api/consents            the user's answer before a free trial turns paid (전자상거래법 제13조 6항) [MON-06]
// POST   /api/events              [STAT-01] counts of what consenting phones did each day, no install id
// POST   /api/photo-wrong         AQ 1.29 [PHOTO-10] '사진이 달라요' counts: day, screen, reason, app version. No photo, no text, no install id
// GET    /api/plaza/news?since=   [COMM-10] header X-Device: my rule changed, my rule or proposal adopted
// *      /api/members             [PLAZA-07] header X-Device (GET) or body device: the companies this phone joined; POST joins, DELETE leaves
// GET    /api/companies/:id/room  [PLAZA-09] header X-Device, members only: my version, the department's others, 안정
//                                 [PLAZA-08] GET /api/companies/:id, its history and talk give pay only to members (X-Device)
// GET    /api/inquiries/:id/photos/:n  [SRV-06] header X-Device: a photo sent with one's own question
// GET    /api/invites             [INVITE-05] header X-Device: my invite code, the rules and how far my friends are; POST /code
//                                 makes the code, /use reports the code this phone came by, /seen once a day it is opened
//                                 [INVITE-02][INVITE-03]; GET /api/invites/rules the rules, with no install [INVITE-08]
// GET/POST /api/admin/settings/invite [INVITE-08] the developer app's 친구 초대 rules
// GET    /api/companies/all       SP1 1.30 [SPD-06] every company with its 대표 주기 line, ETag (If-None-Match → 304)
//                                 SP1 1.30 [SPD-05] GET /api/companies/:id?with=room,talk adds a member's room and room talk;
//                                 [SPD-08] POST /api/members?with=page adds the page; [SPD-02] header x-d1-bookmark both ways
// GET    /i/:code                 [INVITE-02] the invite link: on to the Play listing with the code as install referrer
//
// A report carries no pay amounts, names or contacts; one that looks like it does is refused, not
// cleaned. No IP address is stored: limits and "the same network within an hour" use hashes salted
// with the secret RATE_SALT, cleared within two hours; what stays is a random mark shared by sends
// from one network within the hour. The app's install id is kept only as a hash, so a sender counts
// once and can take a report back.

import { adminFile, adminBuild } from "./admin-app.js";
import { adminLib } from "./admin-lib.js";
import { pushReady, sendPush } from "./push.js";
// AX 1.30 [ADMX-02] 판 차이 (src/plaza-diff.js) and [ADMX-01][ADMX-06][ADMX-07][ADMX-08][ADMX-10] the developer app's
// trends, alerts, company table, search and alert pushes (src/admin-insight.js).
import { lineChanges, factsOf } from "./plaza-diff.js";
import { trendQueries, trendFrom, alertsFrom, adminSearch, metricExtras } from "./admin-insight.js";
// 1.28 통합 [FLAG-34] the flag book, made from the app's core FlagCatalog (never edited by hand).
import { CORE_FEATURES, CORE_FLAGS, CORE_FREE, CORE_POLICY } from "./flag-catalog.js";
// SP1 1.30 [SPD-01][SPD-02][SPD-04][SPD-06] what the plaza's fast reads share.
import { spDbKey, spSession, spBookmarked, spNoCache, spKept, spPairChunks, spInMarks, spEtag, spNotModified } from "./read-fast.js";

const LIMITS = {
  reportBytes: 32 * 1024,
  reportsPerDay: 10,
  reportsPerNetworkDay: 30,
  votesPerHour: 60,
  company: 40,
  dept: 20,
  note: 500,
  // A way of paying the app cannot set yet needs room to explain it and give an example.
  ask: 1500,
  // [COMM-06] A post in the talk on a rule set.
  post: 500,
  postsPerDay: 20,
  postsPerNetworkDay: 60,
  flagsToHide: 3,
  muteDays: 30,
  hiddenPostDays: 90,
  // [COMM-08] 운영 규칙 3항: a post the flags hid is looked at within 30 days (정보통신망법 제44조의2 제4항);
  // the home screen warns a week before.
  hiddenReviewDays: 30,
  loginFailures: 5,
  lockMs: 60 * 60 * 1000,
  sessionMs: 7 * 24 * 60 * 60 * 1000,
  // [ADMIN-15] A wrong password answers 150 ms later for each earlier miss; a sign-in in use runs on
  // a week at a time, for at most 30 days from the password.
  loginDelayMs: 150,
  sessionMaxMs: 30 * 24 * 60 * 60 * 1000,
  // [ADMIN-04] An invite link works for a week; a mailed code for 15 minutes and five tries.
  inviteMs: 7 * 24 * 60 * 60 * 1000,
  codeMs: 15 * 60 * 1000,
  codeTries: 5,
  joinsPerHour: 20,
  email: 120,
  adminName: 30,
};

/** Until changed in the developer app. */
// [COMM-07] adoptAgree: the share of 맞았어요 among everyone who voted that a rule set needs to be adopted on its own.
// [PLAZA-10] firstMonths: the pass for a company's or department's first setup with its pay; at most firstCapYear a year.
const DEFAULTS = { adoptPoints: 3, rewardMonths: 3, adoptAgree: 75, adoptPersons: 3, amendPersons: 3, autoReward: 1, rewardCapMonths: 3, rewardCapDays: 90,
  firstMonths: 1, firstCapYear: 3 };

/** The newest company settings file this server reads (CompanyShare.VERSION in the app). */
// [PAYS-03] 3 carries 감시·단속 승인, 5인 미만 공휴일, 유급 휴게 and allowances paid instead of a premium.
const SETUP_VERSION = 3;

/** Why a report was not adopted, as the sender's app shows it. */
const REASONS = {
  different: "같은 회사 분들이 알려 준 설정과 달라요",
  unclear: "정보가 부족해서 확인하기 어려워요",
  wrong: "실제와 다른 정보로 보여요",
  duplicate: "이미 같은 내용이 있어요",
  inappropriate: "알맞지 않은 내용이 들어 있어요",
};

/** [EXTRA-06] The developer's answer to a way of paying the app cannot set yet, as the sender's app shows it. */
const ANSWERS = {
  planned: "알려 주신 방식을 앱에 넣을게요. 업데이트되면 바로 설정할 수 있어요",
  possible: "지금 앱으로도 맞출 수 있는 방식이에요. 연장 휴무일 근무 지정과 급여 설정을 다시 봐 주세요",
  later: "지금은 넣기 어려워요. 같은 방식을 알려 주는 분이 많아지면 다시 볼게요",
  unclear: "내용만으로는 알기 어려워요. 예시를 더 넣어 다시 보내 주세요",
};

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

/**
 * [ADMIN-04] What an admin account can be given, area by area: nothing, a look, or changes too. The owner
 * (signed in with ADMIN_PASSWORD) has everything and alone manages admins. Activity can only be looked at.
 */
const AREAS = {
  review: "검토",
  inquiries: "문의",
  // [ADMIN-10] 개발사항: what admins gather from questions for the developer. Adding, changing and deleting
  // is "dev"; setting 개발중·완료·반려 is its own switch, the owner's until given.
  dev: "개발사항",
  devStatus: "개발사항 처리",
  companies: "회사·규칙",
  rewards: "보상",
  settings: "채택 설정",
  // [COMM-06] The talk on rule sets: what people flagged, hiding, showing again and muting a writer.
  community: "토론",
  // [PLZ-06] Taking an adopted rule down (내리기) is its own switch: only an admin given it, or the owner.
  takedown: "광장 규칙 내리기",
  // [SRV-04][ADMIN-14] 공지, 기능 스위치 and 이달 할 일; [ADMIN-13] the gate numbers; [SRV-05] reported share links.
  ops: "운영(공지·기능 스위치·이달 할 일)",
  metrics: "지표",
  shares: "공유 링크 신고",
  audit: "활동 기록",
  // AM 1.28 [FLAG-27] 기능 관리(다크머지): looking and changing conditions; forcing a feature off is its own yes or no,
  // the owner's until given (결정 D97). Forcing on and legal confirmations stay the owner's.
  flags: "기능 관리",
  flagOff: "기능 강제로 끄기",
};
const LEVELS = ["none", "view", "edit"];
const VIEW_ONLY = ["audit"];
/** [ADMIN-10] Areas that are a yes or no: a look at them means nothing, so "view" is stored as "none". */
const TOGGLES = ["devStatus", "takedown", "flagOff"];
/**
 * [ADMIN-10] What an account made before an area existed gets: every admin may add, change and delete
 * 개발사항, so the admin already invited keeps working without a visit to the admins screen. 처리 stays
 * the owner's until given.
 */
// AM 1.28 [FLAG-27] Admins from before 1.28 who could see 운영 see 기능 관리 too (the switches moved there); changing stays
// the owner's to give.
const SINCE = { dev: () => "edit", flags: (p) => (p?.ops === "view" || p?.ops === "edit" ? "view" : "none") };
const PRESETS = {
  manager: { name: "운영 관리자", perms: { review: "edit", inquiries: "edit", dev: "edit", devStatus: "edit", companies: "edit", rewards: "edit", settings: "edit", community: "edit", takedown: "edit", ops: "edit", metrics: "view", shares: "edit", audit: "view", flags: "edit", flagOff: "none" } },
  reviewer: { name: "검토 담당", perms: { review: "edit", inquiries: "none", dev: "edit", devStatus: "none", companies: "view", rewards: "none", settings: "none", community: "edit", takedown: "none", ops: "none", metrics: "none", shares: "edit", audit: "none", flags: "none", flagOff: "none" } },
  support: { name: "문의 담당", perms: { review: "view", inquiries: "edit", dev: "edit", devStatus: "none", companies: "view", rewards: "none", settings: "none", community: "view", takedown: "none", ops: "view", metrics: "none", shares: "view", audit: "none", flags: "view", flagOff: "none" } },
  viewer: { name: "보기 전용", perms: { review: "view", inquiries: "view", dev: "view", devStatus: "none", companies: "view", rewards: "view", settings: "view", community: "view", takedown: "none", ops: "view", metrics: "view", shares: "view", audit: "view", flags: "view", flagOff: "none" } },
};
const OWNER = { id: "owner", name: "최고 관리자", email: "", owner: true, perms: Object.fromEntries(Object.keys(AREAS).map((a) => [a, VIEW_ONLY.includes(a) ? "view" : "edit"])) };

export default {
  async fetch(request, env, ctx) {
    try {
      return await route(request, env, ctx);
    } catch (e) {
      if (e instanceof Refuse) return json({ error: e.code, field: e.field }, e.status);
      console.error(e && e.stack ? e.stack : String(e));
      // [ADMUX-16] 지표 › 오류 counts these; a count that cannot be written changes nothing.
      if (env.DB) await countError(env).catch(() => {});
      return json({ error: "server" }, 500);
    } finally {
      if (env.DB) ctx?.waitUntil?.(flushMeter(env, Date.now()).catch(() => {}));
      // AN 1.28 [ADMUX-37] Anything sent may change what the developer app's badges count.
      if (request.method !== "GET" && request.method !== "HEAD") badgesChanged();
    }
  },
  // Hourly (wrangler.toml [triggers], or the dashboard's Cron Triggers): what is promised to go, goes on
  // time even when nobody sends anything.
  async scheduled(event, env, ctx) {
    if (!env.DB) return;
    ctx.waitUntil(ready(env).then(() => prune(env, Date.now())).then(() => stopUnconsentedTrials(env, Date.now()))
      .then(() => recheckLapsed(env, Date.now())).then(() => autoRest(env, Date.now())).then(() => syncShareSwitch(env))
      // AM 1.28 [FLAG-25][FLAG-28] flags turning on by date, the launch, the day-before news.
      .then(() => flagTick(env, Date.now())).then(() => alertTick(env, Date.now())).finally(badgesChanged)); // AX 1.30 [ADMX-10]
  },
};

/**
 * Deletes what the server only keeps for a while: network hashes after two hours, spent limits, sign-ins,
 * invites and codes, questions with nothing new for 180 days, hidden posts after 90 days, ended 쓰기 막기
 * and the admin log after a year, usage totals after 400 days and a closed question's photos after 90.
 */
export async function prune(env, now) {
  const invite = await inviteSettings(env);
  await batch(env, [
    stmt(env, "UPDATE reports SET net = NULL WHERE net IS NOT NULL AND created_at < ?", now - 2 * HOUR),
    stmt(env, "UPDATE votes SET net = NULL WHERE net IS NOT NULL AND at < ?", now - 2 * HOUR),
    stmt(env, "UPDATE rates SET net = NULL WHERE net IS NOT NULL AND created_at < ?", now - 2 * HOUR),
    stmt(env, "UPDATE rate_votes SET net = NULL WHERE net IS NOT NULL AND at < ?", now - 2 * HOUR),
    stmt(env, "DELETE FROM counters WHERE expires < ?", now),
    stmt(env, "DELETE FROM admin_sessions WHERE expires < ?", now),
    stmt(env, "DELETE FROM invites WHERE used_at IS NULL AND expires < ?", now),
    stmt(env, "DELETE FROM codes WHERE expires < ?", now),
    ...dropInquiries(env, "updated_at < ?", now - INQUIRY.keepMs),
    // [COMM-06] A hidden post nobody restored is gone after 90 days, and a 30-day 쓰기 막기 when it ends.
    stmt(env, "DELETE FROM post_flags WHERE post_id IN (SELECT id FROM posts WHERE status = 'hidden' AND hidden_at < ?)", now - LIMITS.hiddenPostDays * DAY),
    stmt(env, "DELETE FROM posts WHERE status = 'hidden' AND hidden_at < ?", now - LIMITS.hiddenPostDays * DAY),
    stmt(env, "INSERT INTO plaza_results (device, kind, post_id, result, company_id, at) SELECT device, 'unmuted', NULL, 'ended', NULL, until FROM muted WHERE until < ?", now),
    stmt(env, "DELETE FROM muted WHERE until < ?", now),
    stmt(env, "DELETE FROM plaza_results WHERE at < ?", now - 30 * DAY),
    // [PLAZA-07] A company left is forgotten once its 30 days are over.
    stmt(env, "DELETE FROM members WHERE left = 1 AND since < ?", now - MEMBER.waitDays * DAY),
    stmt(env, "DELETE FROM requests WHERE expires < ?", now),
    stmt(env, "DELETE FROM audit WHERE at < ?", now - 365 * DAY),
    // [MON-03][MON-09] Spent link codes, and purchases 30 days after they ended.
    stmt(env, "DELETE FROM link_codes WHERE expires < ?", now),
    stmt(env, "DELETE FROM subs WHERE expiry IS NOT NULL AND expiry < ?", now - MONEY.subKeepMs),
    // [MON-06][MON-11] Consent answers are the record of a contract: kept 5 years (전자상거래법 시행령 제6조), then gone.
    stmt(env, "DELETE FROM consents WHERE at < ?", now - MONEY.consentKeepMs),
    // [INVITE-04][INVITE-08] An invite never counted goes 30 days after its period of use could end; a counted one after
    // a year (so the same phone is not counted again, and a cap).
    stmt(env, "DELETE FROM invite_uses WHERE status NOT IN ('credited', 'capped', 'qualified') AND created_at < ?", now - inviteDeadline(invite)),
    stmt(env, "DELETE FROM invite_uses WHERE status IN ('credited', 'capped', 'qualified') AND created_at < ?", now - INVITE.keepCounted),
    // [STAT-01] Day totals after 400 days (a year and its month before, to compare); [SRV-06] a closed
    // question's photos 90 days after it closed, its words stay until the question goes.
    stmt(env, "DELETE FROM usage_days WHERE day < ?", kstDay(now - USAGE.keepDays * DAY)),
    stmt(env, "DELETE FROM usage_events WHERE day < ?", kstDay(now - USAGE.keepDays * DAY)),
    // AM 1.28 [FLAG-30]
    stmt(env, "DELETE FROM usage_flags WHERE day < ?", kstDay(now - USAGE.keepDays * DAY)),
    stmt(env, "DELETE FROM usage_startup WHERE day < ?", kstDay(now - USAGE.keepDays * DAY)), // AO 1.28 [PERF-17]
    stmt(env, "DELETE FROM photo_wrong WHERE day < ?", kstDay(now - USAGE.keepDays * DAY)), // AQ 1.29 [PHOTO-10]
    stmt(env, "DELETE FROM inquiry_photos WHERE inquiry_id IN (SELECT id FROM inquiries WHERE status = 'closed' AND updated_at < ?)",
      now - PHOTO.keepAfterCloseMs),
  ]);
}

/**
 * [ADMIN-11][ADMIN-12] Deletes the questions [where] picks with everything hung on them: messages, marks,
 * admins' notes and links to a 개발사항 (the item stays). The questions themselves go last.
 */
function dropInquiries(env, where, ...params) {
  const of = `inquiry_id IN (SELECT id FROM inquiries WHERE ${where})`;
  return [
    ...["inquiry_messages", "inquiry_marks", "inquiry_notes", "dev_links", "inquiry_photos"].map((t) => stmt(env, `DELETE FROM ${t} WHERE ${of}`, ...params)),
    stmt(env, `DELETE FROM inquiries WHERE ${where}`, ...params),
  ];
}

/**
 * [SRV-08] The writes an app may send twice (a timeout, then again from its queue) answer the second time
 * with the first answer when they carry the same X-Request-Id, so a post, question, report or flag goes in once.
 */
const ONCE = [/^\/api\/posts$/, /^\/api\/posts\/p[A-Za-z0-9]{15}\/flag$/, /^\/api\/inquiries$/, /^\/api\/inquiries\/[A-Za-z0-9]{22}\/(messages|flag)$/,
  /^\/api\/reports$/, /^\/api\/rates$/];
const REQUEST_ID = /^[A-Za-z0-9-]{16,64}$/;

async function once(request, env, path, handle) {
  const id = request.headers.get("x-request-id");
  if (request.method !== "POST" || !id || !REQUEST_ID.test(id) || !ONCE.some((r) => r.test(path))) return handle();
  const now = Date.now();
  const key = (await sha256(`request|${path}|${id}`)).slice(0, 40);
  const seen = await one(env, "SELECT status, body FROM requests WHERE key = ? AND expires > ?", key, now);
  if (seen) return new Response(seen.body, { status: seen.status, headers: { "content-type": "application/json; charset=utf-8", "x-replayed": "1" } });
  const res = await handle();
  // Kept only when it went in (or was refused for good): a busy server or a limit is worth another try.
  if (res.status < 500 && res.status !== 429) {
    const body = await res.clone().text();
    if (body.length <= 8192) {
      await run(env, "INSERT OR IGNORE INTO requests (key, status, body, expires) VALUES (?, ?, ?, ?)", key, res.status, body, now + DAY);
    }
  }
  return res;
}

async function route(request, env, ctx) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;
  // [SRV-08] Any write through this Worker may change 모두의 문의, so its kept rows are read afresh.
  if (method !== "GET") publicRows.delete(env.DB ?? env);
  // AS 1.29 [SRCH-01] and may add a name whose 초성 and reading are not there yet.
  if (method !== "GET") namesFilled.delete(env.DB ?? env);
  // SP1 1.30 [SPD-04] and may change any public list this Worker keeps a short copy of.
  if (method !== "GET") spLists.delete(env.DB ?? env);
  // SP1 1.30 [SPD-02] The plaza's reads and writes go through a D1 session: the nearest copy, as fresh as the app's bookmark.
  const s = spSession(env, request, path);
  if (s) env = s.env;
  if (path.startsWith("/api/") && !path.startsWith("/api/admin") && method === "POST" && env.DB && request.headers.get("x-request-id")) {
    await ready(env);
    return spBookmarked(await once(request, env, path, () => routeOnce(request, env, url, ctx)), s);
  }
  return spBookmarked(await routeOnce(request, env, url, ctx), s);
}

/**
 * [SRV-08] Reading the public lists is held to [PUBLIC_PER_HOUR] an hour per network, counted in this
 * Worker's memory: a write per read would spend D1's daily writes (100,000) faster than a bot spends its
 * reads. Approximate across Workers, enough to stop one caller from emptying the day's reads.
 */
const PUBLIC_PER_HOUR = 600;
const PUBLIC_READS = [/^\/api\/companies$/, /^\/api\/board$/, /^\/api\/rates$/, /^\/api\/companies\/[A-Za-z0-9]{12}\/history$/,
  /^\/api\/inquiries\/public(\/[A-Za-z0-9]{22})?$/, /^\/api\/plazas$/,
  // AS 1.29 [SRCH-04]
  /^\/api\/companies\/top$/,
  // SP1 1.30 [SPD-06]
  /^\/api\/companies\/all$/,
  // AI 1.27 [PLAZA-33] A kind of work's plaza is read by any phone with the app, so it is held to the same count.
  /^\/api\/companies\/style-[a-z]+\/setups\/room\/posts$/];
const publicReads = new Map();

async function publicAllowed(request, env, path) {
  if (request.method !== "GET" || !PUBLIC_READS.some((r) => r.test(path))) return true;
  const now = Date.now();
  const hour = Math.floor(now / HOUR);
  const key = `${(await network(request, env, now)).now}|${hour}`;
  const n = (publicReads.get(key) ?? 0) + 1;
  publicReads.set(key, n);
  if (publicReads.size > 20000) for (const k of publicReads.keys()) if (!k.endsWith(`|${hour}`)) publicReads.delete(k);
  return n <= PUBLIC_PER_HOUR;
}

async function routeOnce(request, env, url, ctx) {
  const path = url.pathname;
  const method = request.method;
  if (!(await publicAllowed(request, env, path))) return json({ error: "rate_limited" }, 429);
  if (path === "/" || path === "/robots.txt") {
    return new Response("User-agent: *\nDisallow: /\n", { headers: { "content-type": "text/plain; charset=utf-8" } });
  }
  if (path === "/admin") return Response.redirect(`${url.origin}/admin/`, 301);
  if (path.startsWith("/admin/")) return adminFile(path, request);
  // AE 1.26: the invite link needs no database.
  if (path.startsWith("/i/") && method === "GET") return invitePage(path.slice(3));
  if (path === "/api/admin/health" && method === "GET") return json(health(env));
  if (!env.DB) return json({ error: "no_database" }, 503);
  await ready(env);
  if (path.startsWith("/api/admin/")) return admin(request, env, url);
  // AS 1.29 [SRCH-03][SRCH-04] A short edge copy of a search, and the most used companies for the app to keep.
  if (path === "/api/companies" && method === "GET") return edgeCached(request, ctx, SEARCH_EDGE_SECONDS, () => search(env, url));
  if (path === "/api/companies/top" && method === "GET") return edgeCached(request, ctx, TOP_EDGE_SECONDS, () => topCompanies(env, url));
  // AS 1.29 끝
  // SP1 1.30 시작 [SPD-06] Every company in one list with an ETag (304 when the app has it); [SPD-04] the same public answers
  // kept a short while at the edge and in this Worker; [SPD-05] ?with=room,talk: a member's page, room and room talk at once.
  if (path === "/api/companies/all" && method === "GET") {
    return spNotModified(request, await edgeCached(request, ctx, ALL_EDGE_SECONDS, () => allCompanies(env)));
  }
  if (path === "/api/terms" && method === "GET") {
    // One read, not kept in memory (the developer's new settings show at once); the edge keeps it ten minutes where it can.
    return edgeCached(request, ctx, TERMS_EDGE_SECONDS, async () => json(await terms(env), 200, TERMS_CACHE));
  }
  let m = path.match(/^\/api\/companies\/([A-Za-z0-9]{12})$/);
  if (m && method === "GET") return company(env, m[1], request, url);
  // SP1 1.30 끝
  // [PLAZA-07][PLAZA-09] 회사 참여 and the company's room.
  if (path === "/api/members") return members(request, env);
  m = path.match(/^\/api\/companies\/([A-Za-z0-9]{12})\/room$/);
  if (m && method === "GET") return room(request, env, m[1]);
  if (path === "/api/reports" && method === "POST") return report(request, env);
  m = path.match(/^\/api\/reports\/([A-Za-z0-9]{22})$/);
  if (m && method === "GET") return reportStatus(env, m[1]);
  if (m && method === "DELETE") return takeBack(request, env, m[1]);
  if (path === "/api/mine" && method === "DELETE") return forgetMine(request, env);
  if (path === "/api/votes" && method === "POST") return vote(request, env);
  // SP1 1.30 [SPD-04] the same list for everyone (no X-Device), kept a short while.
  if (path === "/api/board" && method === "GET") {
    return edgeCached(request, ctx, BOARD_EDGE_SECONDS, async () => json(await listKept(env, request, `board?${sortedQuery(url)}`, BOARD_MEMO_MS,
      () => board(env, url)), 200, BOARD_CACHE));
  }
  // AI 1.27 [PLAZA-32][PLAZA-33] 회사 정보 광장 and the plazas of each kind of work: the same talk, grp 'room'.
  m = path.match(ROOM_PATH);
  if (m && method === "GET") return talk(request, env, m[1], ROOM);
  m = path.match(/^\/api\/companies\/([A-Za-z0-9]{12})\/setups\/([0-9a-f]{16})\/posts$/);
  if (m && method === "GET") return talk(request, env, m[1], m[2]);
  if (path === "/api/posts" && method === "POST") return post(request, env);
  m = path.match(/^\/api\/posts\/(p[A-Za-z0-9]{15})(\/flag)?$/);
  if (m && method === "DELETE" && !m[2]) return takePostBack(request, env, m[1]);
  if (m && method === "POST" && m[2]) return flag(request, env, m[1]);
  m = path.match(/^\/api\/posts\/(p[A-Za-z0-9]{15})\/vote$/);
  if (m && method === "POST") return postVote(request, env, m[1]);
  if (path === "/api/uses" && (method === "POST" || method === "DELETE")) return useRule(request, env, method === "POST");
  if (path === "/api/nick" && method === "POST") return setNick(request, env);
  if (path === "/api/plazas" && method === "GET") {
    return edgeCached(request, ctx, PLAZAS_EDGE_SECONDS, async () => json(await listKept(env, request, "plazas", PLAZAS_EDGE_SECONDS * 1000,
      () => plazas(env)), 200, PLAZAS_CACHE));
  }
  m = path.match(/^\/api\/companies\/([A-Za-z0-9]{12})\/history$/);
  if (m && method === "GET") return history(env, m[1], url, request);
  if (path === "/api/rates" && method === "GET") return rates(request, env, url);
  if (path === "/api/rates" && method === "POST") return addRate(request, env);
  m = path.match(/^\/api\/rates\/([A-Za-z0-9]{22})(\/vote)?$/);
  if (m && method === "POST" && m[2]) return rateVote(request, env, m[1]);
  if (m && method === "DELETE" && !m[2]) return takeRateBack(request, env, m[1]);
  if (path === "/api/activity" && method === "GET") return activity(request, env);
  if (path === "/api/inquiries" || path.startsWith("/api/inquiries/")) return inquiries(request, env, path);
  if (path === "/api/config" && method === "GET") return json(await config(env));
  if (path === "/api/events" && method === "POST") return usage(request, env);
  if (path === "/api/photo-wrong" && method === "POST") return photoWrong(request, env); // AQ 1.29 [PHOTO-10]
  if (path === "/api/plaza/news" && method === "GET") return plazaNews(request, env, url);
  if (path === "/api/digest" && method === "GET") return digest(request, env, url);
  if (path.startsWith("/api/passes") || path === "/api/admin-link" || path === "/api/purchases" || path === "/api/consents") {
    return money(request, env, path);
  }
  if (path === "/api/invites" || path.startsWith("/api/invites/")) return invites(request, env, path, url);
  return json({ error: "not_found" }, 404);
}

// ---- storage ----

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS companies (id TEXT PRIMARY KEY, name TEXT NOT NULL, norm TEXT NOT NULL,
    hidden INTEGER NOT NULL DEFAULT 0, merged_into TEXT, created_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS companies_norm ON companies (norm)",
  "CREATE TABLE IF NOT EXISTS aliases (norm TEXT PRIMARY KEY, company_id TEXT NOT NULL, name TEXT NOT NULL)",
  "CREATE INDEX IF NOT EXISTS aliases_company ON aliases (company_id)",
  `CREATE TABLE IF NOT EXISTS reports (id TEXT PRIMARY KEY, company_id TEXT NOT NULL, dept TEXT NOT NULL,
    dept_norm TEXT NOT NULL, grp TEXT NOT NULL, setup TEXT NOT NULL, verified INTEGER NOT NULL, note TEXT NOT NULL,
    device TEXT NOT NULL, net TEXT, cluster TEXT NOT NULL, points INTEGER NOT NULL, status TEXT NOT NULL,
    rule_id TEXT, reason TEXT, created_at INTEGER NOT NULL, decided_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS reports_group ON reports (company_id, grp)",
  "CREATE INDEX IF NOT EXISTS reports_status ON reports (status, created_at)",
  "CREATE INDEX IF NOT EXISTS reports_device ON reports (device)",
  `CREATE TABLE IF NOT EXISTS votes (company_id TEXT NOT NULL, grp TEXT NOT NULL, device TEXT NOT NULL,
    cluster TEXT NOT NULL, ok INTEGER NOT NULL, verified INTEGER NOT NULL, why TEXT, net TEXT, at INTEGER NOT NULL,
    PRIMARY KEY (company_id, grp, device))`,
  `CREATE TABLE IF NOT EXISTS rules (id TEXT PRIMARY KEY, company_id TEXT NOT NULL, dept TEXT NOT NULL,
    dept_norm TEXT NOT NULL, grp TEXT NOT NULL, version INTEGER NOT NULL, setup TEXT NOT NULL, source TEXT NOT NULL,
    adopted_at INTEGER NOT NULL, withdrawn INTEGER NOT NULL DEFAULT 0, superseded INTEGER NOT NULL DEFAULT 0)`,
  "CREATE INDEX IF NOT EXISTS rules_company ON rules (company_id, dept_norm)",
  `CREATE TABLE IF NOT EXISTS rewards (id TEXT PRIMARY KEY, report_id TEXT NOT NULL UNIQUE, rule_id TEXT NOT NULL,
    device TEXT NOT NULL, months INTEGER NOT NULL, status TEXT NOT NULL, memo TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL, paid_at INTEGER)`,
  "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
  "CREATE TABLE IF NOT EXISTS counters (key TEXT PRIMARY KEY, count INTEGER NOT NULL, expires INTEGER NOT NULL)",
  `CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, action TEXT NOT NULL,
    target TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '')`,
  // [ADMIN-04] Admin accounts, the emails the owner allowed, and who is signed in (the owner is "owner").
  `CREATE TABLE IF NOT EXISTS admins (id TEXT PRIMARY KEY, email TEXT NOT NULL UNIQUE, name TEXT NOT NULL, pw TEXT NOT NULL,
    perms TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, approved_at INTEGER, last_login INTEGER)`,
  `CREATE TABLE IF NOT EXISTS invites (email TEXT PRIMARY KEY, perms TEXT NOT NULL, note TEXT NOT NULL DEFAULT '',
    token TEXT NOT NULL UNIQUE, expires INTEGER NOT NULL, created_at INTEGER NOT NULL, used_at INTEGER)`,
  `CREATE TABLE IF NOT EXISTS admin_sessions (token TEXT PRIMARY KEY, admin TEXT NOT NULL, expires INTEGER NOT NULL,
    created_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS admin_sessions_admin ON admin_sessions (admin)",
  `CREATE TABLE IF NOT EXISTS codes (email TEXT PRIMARY KEY, code TEXT NOT NULL, expires INTEGER NOT NULL,
    tries INTEGER NOT NULL DEFAULT 0)`,
  // [COMM-06] The talk on a rule set. A writer is kept as the install's hash only, shown to others as 참여자 N
  // of that one talk, so posts in two talks cannot be tied together by anyone reading them.
  `CREATE TABLE IF NOT EXISTS posts (id TEXT PRIMARY KEY, company_id TEXT NOT NULL, grp TEXT NOT NULL, device TEXT NOT NULL,
    body TEXT NOT NULL, status TEXT NOT NULL, flags INTEGER NOT NULL DEFAULT 0, kept INTEGER NOT NULL DEFAULT 0,
    hide_reason TEXT, created_at INTEGER NOT NULL, hidden_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS posts_talk ON posts (company_id, grp, created_at)",
  "CREATE INDEX IF NOT EXISTS posts_status ON posts (status, flags)",
  "CREATE INDEX IF NOT EXISTS posts_device ON posts (device)",
  `CREATE TABLE IF NOT EXISTS post_flags (post_id TEXT NOT NULL, device TEXT NOT NULL, reason TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (post_id, device))`,
  "CREATE TABLE IF NOT EXISTS muted (device TEXT PRIMARY KEY, until INTEGER NOT NULL, reason TEXT NOT NULL)",
  // [ADMIN-02] Questions from the app and the developer's answers.
  `CREATE TABLE IF NOT EXISTS inquiries (id TEXT PRIMARY KEY, device TEXT NOT NULL, kind TEXT NOT NULL, info TEXT NOT NULL,
    status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS inquiries_device ON inquiries (device, updated_at)",
  "CREATE INDEX IF NOT EXISTS inquiries_status ON inquiries (status, updated_at)",
  `CREATE TABLE IF NOT EXISTS inquiry_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, inquiry_id TEXT NOT NULL,
    sender TEXT NOT NULL, text TEXT NOT NULL, by TEXT, at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS inquiry_messages_inquiry ON inquiry_messages (inquiry_id, id)",
  // [ADMIN-02] 나도 그래요 (kind same) and 신고 (kind flag) on a public question, once per install each.
  `CREATE TABLE IF NOT EXISTS inquiry_marks (inquiry_id TEXT NOT NULL, device TEXT NOT NULL, kind TEXT NOT NULL,
    reason TEXT, at INTEGER NOT NULL, PRIMARY KEY (inquiry_id, device, kind))`,
  "CREATE INDEX IF NOT EXISTS inquiry_marks_device ON inquiry_marks (device)",
  // [ADMIN-10] 개발사항: what admins gather from questions for the developer. A deleted one keeps its row
  // (deleted_at) so its history stays and the owner can bring it back. Who made or holds it is kept by
  // id and by name, so the name still reads after an account is removed.
  `CREATE TABLE IF NOT EXISTS dev_items (id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'todo', reason TEXT NOT NULL DEFAULT '', created_by TEXT NOT NULL, created_by_name TEXT NOT NULL,
    assignee TEXT, assignee_name TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS dev_items_status ON dev_items (status, updated_at)",
  // [ADMIN-11] The questions behind an item; how many is how many people want it.
  `CREATE TABLE IF NOT EXISTS dev_links (item_id TEXT NOT NULL, inquiry_id TEXT NOT NULL, by TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (item_id, inquiry_id))`,
  "CREATE INDEX IF NOT EXISTS dev_links_inquiry ON dev_links (inquiry_id)",
  // [ADMIN-10] Every change to an item, by whom, before and after (detail is JSON); shown on the item.
  `CREATE TABLE IF NOT EXISTS dev_history (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT NOT NULL, at INTEGER NOT NULL,
    actor_id TEXT NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '{}')`,
  "CREATE INDEX IF NOT EXISTS dev_history_item ON dev_history (item_id, id)",
  // [ADMIN-12] Notes admins leave each other on a question; never shown in the app.
  `CREATE TABLE IF NOT EXISTS inquiry_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, inquiry_id TEXT NOT NULL, by TEXT NOT NULL,
    text TEXT NOT NULL, at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS inquiry_notes_inquiry ON inquiry_notes (inquiry_id, id)",
  // [ADMIN-12] Answers every admin can drop into a reply with one tap.
  `CREATE TABLE IF NOT EXISTS reply_templates (id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT NOT NULL, text TEXT NOT NULL,
    by TEXT NOT NULL, at INTEGER NOT NULL)`,
  // Sign-ins before admin accounts kept only a token; they end once, and the owner signs in again.
  "DROP TABLE IF EXISTS sessions",
  // [MON-08] A recovery code per install, as a hash, to move its passes to a new phone once.
  "CREATE TABLE IF NOT EXISTS pass_codes (hash TEXT PRIMARY KEY, device TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)",
  // [MON-09] Codes the developer app shows an admin, and the phones tied to admins.
  "CREATE TABLE IF NOT EXISTS link_codes (code TEXT PRIMARY KEY, admin TEXT NOT NULL, admin_name TEXT NOT NULL, expires INTEGER NOT NULL)",
  `CREATE TABLE IF NOT EXISTS admin_devices (id TEXT PRIMARY KEY, device TEXT NOT NULL UNIQUE, admin TEXT NOT NULL, admin_name TEXT NOT NULL,
    status TEXT NOT NULL, created_at INTEGER NOT NULL, approved_at INTEGER, seen_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS admin_devices_admin ON admin_devices (admin)",
  // [MON-03] A subscription Play confirmed, kept while it lasts and 30 days after (consent and reward deferral need it).
  `CREATE TABLE IF NOT EXISTS subs (hash TEXT PRIMARY KEY, device TEXT NOT NULL, token TEXT NOT NULL, product TEXT NOT NULL,
    base_plan TEXT NOT NULL DEFAULT '', state TEXT NOT NULL, expiry INTEGER, trial INTEGER NOT NULL DEFAULT 0,
    auto_renew INTEGER NOT NULL DEFAULT 0, consent_at INTEGER, stopped_at INTEGER, created_at INTEGER NOT NULL, checked_at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS subs_device ON subs (device)",
  "CREATE INDEX IF NOT EXISTS subs_trial ON subs (trial, auto_renew, expiry)",
  // [MON-06] Answers before a trial turns paid, kept as the law's evidence.
  `CREATE TABLE IF NOT EXISTS consents (id INTEGER PRIMARY KEY AUTOINCREMENT, device TEXT NOT NULL, kind TEXT NOT NULL,
    detail TEXT NOT NULL, at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS consents_device ON consents (device)",
  // [PLZ-01] 맞아요·달라요 on a post, once per install.
  `CREATE TABLE IF NOT EXISTS post_votes (post_id TEXT NOT NULL, device TEXT NOT NULL, ok INTEGER NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (post_id, device))`,
  "CREATE INDEX IF NOT EXISTS post_votes_device ON post_votes (device)",
  // [PLZ-08] Phones that use a rule set (applied it in the app), once each: 쓰는 사람.
  `CREATE TABLE IF NOT EXISTS uses (company_id TEXT NOT NULL, grp TEXT NOT NULL, device TEXT NOT NULL, at INTEGER NOT NULL,
    PRIMARY KEY (company_id, grp, device))`,
  "CREATE INDEX IF NOT EXISTS uses_device ON uses (device)",
  // [PLZ-08] A name the user chose to be shown by on the plaza, instead of 참여자 N. Optional.
  "CREATE TABLE IF NOT EXISTS nicks (device TEXT PRIMARY KEY, nick TEXT NOT NULL, norm TEXT NOT NULL UNIQUE, at INTEGER NOT NULL)",
  // [PLZ-14] 임금표: market rates people share for a job and region (일당·시급·월급), never one person's pay slip.
  `CREATE TABLE IF NOT EXISTS rates (id TEXT PRIMARY KEY, job TEXT NOT NULL, region TEXT NOT NULL, trade TEXT NOT NULL,
    trade_norm TEXT NOT NULL, unit TEXT NOT NULL, year INTEGER NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '',
    device TEXT NOT NULL, cluster TEXT NOT NULL, net TEXT, status TEXT NOT NULL, created_at INTEGER NOT NULL, decided_at INTEGER,
    agree INTEGER NOT NULL DEFAULT 0, disagree INTEGER NOT NULL DEFAULT 0)`,
  "CREATE INDEX IF NOT EXISTS rates_key ON rates (job, region, trade_norm, unit, year)",
  "CREATE INDEX IF NOT EXISTS rates_device ON rates (device)",
  `CREATE TABLE IF NOT EXISTS rate_votes (rate_id TEXT NOT NULL, device TEXT NOT NULL, ok INTEGER NOT NULL, cluster TEXT NOT NULL,
    net TEXT, at INTEGER NOT NULL, PRIMARY KEY (rate_id, device))`,
  "CREATE INDEX IF NOT EXISTS rate_votes_device ON rate_votes (device)",
  // [STAT-01] What consenting phones sent, added up per day and app version: how many phones sent that
  // day, and per event how many of them did it (phones), how often (total) and how many did it within
  // the last 28 days (recent). No install id, address or time of day: nothing ties a row to a phone.
  "CREATE TABLE IF NOT EXISTS usage_days (day TEXT NOT NULL, app TEXT NOT NULL, phones INTEGER NOT NULL, PRIMARY KEY (day, app))",
  `CREATE TABLE IF NOT EXISTS usage_events (day TEXT NOT NULL, app TEXT NOT NULL, event TEXT NOT NULL, phones INTEGER NOT NULL,
    total INTEGER NOT NULL, recent INTEGER NOT NULL, PRIMARY KEY (day, app, event))`,
  // [SRV-06] Photos sent with a question (JPEG as base64, the metadata taken out), at most two.
  `CREATE TABLE IF NOT EXISTS inquiry_photos (inquiry_id TEXT NOT NULL, n INTEGER NOT NULL, data TEXT NOT NULL, bytes INTEGER NOT NULL,
    at INTEGER NOT NULL, PRIMARY KEY (inquiry_id, n))`,
  // L 1.23 시작
  // [COMM-11] What became of a flagged post, for the phones that flagged it and the one that wrote it, and
  // the end of a 쓰기 막기: read once by 광장 소식, deleted after 30 days.
  `CREATE TABLE IF NOT EXISTS plaza_results (device TEXT NOT NULL, kind TEXT NOT NULL, post_id TEXT, result TEXT NOT NULL,
    company_id TEXT, at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS plaza_results_device ON plaza_results (device, at)",
  // [SRV-08] A request the app may send again after a timeout (X-Request-Id): the first answer is given
  // back instead of a second post, question or flag. Only the hash of the id and the answer, for a day.
  `CREATE TABLE IF NOT EXISTS requests (key TEXT PRIMARY KEY, status INTEGER NOT NULL, body TEXT NOT NULL, expires INTEGER NOT NULL)`,
  // L 1.23 끝
  // H 1.23 시작
  // [ADMIN-15] The log is read by time and by what it is about (and, after COLUMNS, by who did it); rewards by status.
  "CREATE INDEX IF NOT EXISTS audit_at ON audit (at)",
  "CREATE INDEX IF NOT EXISTS audit_target ON audit (target)",
  "CREATE INDEX IF NOT EXISTS rewards_status ON rewards (status, created_at)",
  // [ADMIN-19] Browsers that asked for web push: one row per browser, by a hash of its push address.
  `CREATE TABLE IF NOT EXISTS push_subscriptions (id TEXT PRIMARY KEY, admin TEXT NOT NULL, endpoint TEXT NOT NULL, p256dh TEXT NOT NULL,
    auth TEXT NOT NULL, kinds TEXT NOT NULL, quiet_from INTEGER, quiet_to INTEGER, created_at INTEGER NOT NULL, sent_at INTEGER)`,
  "CREATE INDEX IF NOT EXISTS push_subscriptions_admin ON push_subscriptions (admin)",
  // H 1.23 끝
  // AE 1.26 시작
  // [INVITE-02] One invite code per install, with the phone mark of the phone that made it (to see a self-invite).
  "CREATE TABLE IF NOT EXISTS invite_codes (code TEXT PRIMARY KEY, device TEXT NOT NULL UNIQUE, phone TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)",
  // [INVITE-03][INVITE-04][INVITE-08] A phone that came by an invite, once per install and once per phone mark: waiting
  // (in its period of use), qualified (used it, counting toward the inviter's next months), credited or capped (in a
  // group that gave months, or over the cap; batch is that reward's id), expired (never came back) or refused (self).
  `CREATE TABLE IF NOT EXISTS invite_uses (invitee TEXT PRIMARY KEY, phone TEXT NOT NULL UNIQUE, code TEXT NOT NULL, inviter TEXT NOT NULL,
    source TEXT NOT NULL, first_day TEXT NOT NULL, status TEXT NOT NULL, reason TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, decided_at INTEGER,
    batch TEXT)`,
  "CREATE INDEX IF NOT EXISTS invite_uses_inviter ON invite_uses (inviter, status)",
  // AE 1.26 끝
  // AF 1.26 시작
  // [PLAZA-07] The companies a phone joined (결정 D79): one per calendar (cal is the app's own calendar id, or "app" from an
  // app before 1.26), changed again only 30 days after [since], two at a time. [left] keeps the 30 days after leaving.
  `CREATE TABLE IF NOT EXISTS members (device TEXT NOT NULL, cal TEXT NOT NULL, company_id TEXT NOT NULL, since INTEGER NOT NULL,
    left INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (device, cal))`,
  "CREATE INDEX IF NOT EXISTS members_company ON members (company_id, left)",
  // AF 1.26 끝
  // AM 1.28 시작
  // [FLAG-21] What the developer app overrides per flag: a force (on/off, until when, 다시 볼 날), changed conditions,
  // why, who and when; auto 1 for the hourly job's own rests. [FLAG-21] 확인했어요 on facts only a person knows.
  `CREATE TABLE IF NOT EXISTS flag_overrides (key TEXT PRIMARY KEY, force TEXT, why TEXT NOT NULL DEFAULT '', conditions TEXT, until INTEGER,
    review_at TEXT, by_name TEXT NOT NULL DEFAULT '', by_id TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL, auto INTEGER NOT NULL DEFAULT 0)`,
  "CREATE TABLE IF NOT EXISTS flag_checks (key TEXT PRIMARY KEY, by_name TEXT NOT NULL, by_id TEXT NOT NULL, at INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '')",
  // [FLAG-21] Every change with what it was before and after (the activity log keeps a line; this keeps it whole).
  `CREATE TABLE IF NOT EXISTS flag_history (id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL, at INTEGER NOT NULL, action TEXT NOT NULL,
    actor TEXT NOT NULL DEFAULT '', actor_id TEXT NOT NULL DEFAULT '', before TEXT, after TEXT, why TEXT NOT NULL DEFAULT '')`,
  "CREATE INDEX IF NOT EXISTS flag_history_key ON flag_history (key, id)",
  // [FLAG-25][FLAG-28] What the server last saw of each flag (on or not, since when) and the day it warned of.
  "CREATE TABLE IF NOT EXISTS flag_state (key TEXT PRIMARY KEY, on_now INTEGER NOT NULL, since INTEGER NOT NULL, warned TEXT)",
  // [FLAG-30] Per day and app version: how many phones that sent their flag list had each flag on ('*': how many sent one).
  "CREATE TABLE IF NOT EXISTS usage_flags (day TEXT NOT NULL, app TEXT NOT NULL, flag TEXT NOT NULL, phones INTEGER NOT NULL, PRIMARY KEY (day, app, flag))",
  // AM 1.28 끝
];

/** Columns added after a server was first deployed; adding one that is already there is skipped. */
const COLUMNS = [
  "ALTER TABLE audit ADD COLUMN actor TEXT NOT NULL DEFAULT ''",
  // [ADMIN-02] 모두의 문의: a question sent as public, why it is hidden, its 신고, whether the developer kept it, 나도 그래요.
  "ALTER TABLE inquiries ADD COLUMN public INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE inquiries ADD COLUMN hidden TEXT",
  "ALTER TABLE inquiries ADD COLUMN flags INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE inquiries ADD COLUMN kept INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE inquiries ADD COLUMN same INTEGER NOT NULL DEFAULT 0",
  // [ADMIN-12] 내가 맡기: the admin answering a question, by id and by name.
  "ALTER TABLE inquiries ADD COLUMN assignee TEXT",
  "ALTER TABLE inquiries ADD COLUMN assignee_name TEXT",
  // [INQ-03] The user's word on the answers: 1 도움이 됐어요, -1 아쉬워요.
  "ALTER TABLE inquiries ADD COLUMN rating INTEGER",
  // [INQ-02] The app version a finished 개발사항 went out in, for the asker's 반영됨 step.
  "ALTER TABLE dev_items ADD COLUMN released_in TEXT",
  // [MON-08] A reward Play gave as a later renewal of a running subscription, not as a pass.
  "ALTER TABLE rewards ADD COLUMN deferred INTEGER NOT NULL DEFAULT 0",
  // [PLZ-12] What kind of place it is (company, hospital, topic for rules not tied to one place), its job plaza and region.
  "ALTER TABLE companies ADD COLUMN kind TEXT NOT NULL DEFAULT 'company'",
  "ALTER TABLE companies ADD COLUMN job TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE companies ADD COLUMN region TEXT NOT NULL DEFAULT ''",
  // [PLZ-02] A setup sent to change the rule in use (its id), and the post that proposed it.
  "ALTER TABLE reports ADD COLUMN amends TEXT",
  "ALTER TABLE reports ADD COLUMN post_id TEXT",
  // [PLZ-01][PLZ-05][PLZ-09] Replies, the kind of post, a structured change and its votes.
  "ALTER TABLE posts ADD COLUMN parent TEXT",
  "ALTER TABLE posts ADD COLUMN kind TEXT NOT NULL DEFAULT 'talk'",
  "ALTER TABLE posts ADD COLUMN change TEXT",
  "ALTER TABLE posts ADD COLUMN report_id TEXT",
  "ALTER TABLE posts ADD COLUMN agree INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE posts ADD COLUMN disagree INTEGER NOT NULL DEFAULT 0",
  // H 1.23 시작
  // [ADMIN-17] Who did it by id, so 내가 한 일 and one admin's activity survive a change of name.
  "ALTER TABLE audit ADD COLUMN actor_id TEXT NOT NULL DEFAULT ''",
  // [ADMIN-16] A saved answer's topic (문의 주제, '' for any) and how often it was sent.
  "ALTER TABLE reply_templates ADD COLUMN topic TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE reply_templates ADD COLUMN uses INTEGER NOT NULL DEFAULT 0",
  // H 1.23 끝
  // X 1.25 시작
  // [PLAZA-03] The 초성 of a name ("ㅅㅅㅈㅈ"), filled by search when empty, so 초성 alone finds a company.
  "ALTER TABLE companies ADD COLUMN cho TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE aliases ADD COLUMN cho TEXT NOT NULL DEFAULT ''",
  // X 1.25 끝
  // AS 1.29 시작
  // [SRCH-02] A name as said in Korean letters ("지에스칼텍스" for GS칼텍스), filled by search when empty, so the reading finds it.
  "ALTER TABLE companies ADD COLUMN said TEXT NOT NULL DEFAULT ''",
  "ALTER TABLE aliases ADD COLUMN said TEXT NOT NULL DEFAULT ''",
  // AS 1.29 끝
  // AI 1.27 시작
  // [PLAZA-30] What a 명세서 tick rested on ("2026-09|match" or "2026-09|gap"), and an older app's tick with nothing behind it.
  "ALTER TABLE reports ADD COLUMN proof TEXT",
  "ALTER TABLE reports ADD COLUMN claim INTEGER NOT NULL DEFAULT 0",
  "ALTER TABLE votes ADD COLUMN claim INTEGER NOT NULL DEFAULT 0",
  // AI 1.27 끝
  // 1.28 통합 [FLAG-36] AL-D3: an override's app versionCode range (판단 2단계 '앱 판 밖').
  "ALTER TABLE flag_overrides ADD COLUMN min_app INTEGER",
  "ALTER TABLE flag_overrides ADD COLUMN max_app INTEGER",
  // AT 1.29 시작
  // [ADMCP-03] 복사함: when a question was last copied for development and by whom (the activity log keeps each copy).
  "ALTER TABLE inquiries ADD COLUMN copied_at INTEGER",
  "ALTER TABLE inquiries ADD COLUMN copied_by TEXT",
  // AT 1.29 끝
];

/** Indexes on columns COLUMNS adds, so they are made after it. */
const LATE_INDEXES = [
  // H 1.23 시작
  "CREATE INDEX IF NOT EXISTS inquiries_assignee ON inquiries (assignee, status)",
  "CREATE INDEX IF NOT EXISTS audit_actor ON audit (actor_id, at)",
  // H 1.23 끝
  // AN 1.28 시작
  // [ADMUX-38] What the developer app's badges and 오늘 read most: questions by the day they came, the open ones
  // people found unhelpful (only those rows), posts the flags put in front of an admin (only those rows), and
  // the activity log by kind of action.
  "CREATE INDEX IF NOT EXISTS inquiries_created ON inquiries (created_at)",
  "CREATE INDEX IF NOT EXISTS inquiries_bad ON inquiries (status) WHERE rating = -1",
  "CREATE INDEX IF NOT EXISTS posts_flagged ON posts (flags, created_at) WHERE kept = 0 AND (flags > 0 OR hide_reason = 'flags')",
  "CREATE INDEX IF NOT EXISTS audit_action ON audit (action, id)",
  // AN 1.28 끝
  // AO 1.28 시작 [PERF-17] How long the app took to open on consenting phones, as counts per bucket: per day, app version,
  // kind (cold: a new process, warm: it was running) and step (container wait, screen, first month). No phone, no time.
  `CREATE TABLE IF NOT EXISTS usage_startup (day TEXT NOT NULL, app TEXT NOT NULL, kind TEXT NOT NULL, step TEXT NOT NULL,
    bucket INTEGER NOT NULL, phones INTEGER NOT NULL, PRIMARY KEY (day, app, kind, step, bucket))`,
  // AO 1.28 끝
  // AT 1.29 시작 [ADMCP-04] 복사 전 문제 제보 for the badges and the list's strip: only the rows nobody copied yet.
  "CREATE INDEX IF NOT EXISTS inquiries_uncopied ON inquiries (kind, status) WHERE copied_at IS NULL",
  // AT 1.29 끝
  // AQ 1.29 시작 [PHOTO-10] '사진이 달라요' presses: per day, app version, screen and reason, how many. No phone, no photo, no text.
  `CREATE TABLE IF NOT EXISTS photo_wrong (day TEXT NOT NULL, app TEXT NOT NULL, screen TEXT NOT NULL, reason TEXT NOT NULL,
    total INTEGER NOT NULL, PRIMARY KEY (day, app, screen, reason))`,
  // AQ 1.29 끝
  // SP1 1.30 시작 [SPD-03] A version's senders and its proposed post (history), and the proposals of a talk: reports by the rule
  // they made and posts by the report they carry. votes, uses and members need none: their primary keys start with the columns
  // the plaza reads by (company_id, grp / device).
  "CREATE INDEX IF NOT EXISTS reports_rule ON reports (rule_id) WHERE rule_id IS NOT NULL",
  "CREATE INDEX IF NOT EXISTS posts_report ON posts (report_id) WHERE report_id IS NOT NULL",
  // SP1 1.30 끝
];

const schemas = new WeakMap();

/** Changes whenever SCHEMA, COLUMNS or LATE_INDEXES does, so a server made by this code skips making tables again. */
const SCHEMA_VERSION = (() => {
  let h = 5381;
  for (const c of [...SCHEMA, ...COLUMNS, ...LATE_INDEXES].join("\n")) h = ((h * 33) ^ c.charCodeAt(0)) >>> 0;
  return h.toString(36);
})();

/**
 * Tables are made on first use, so setting up the server is binding an empty D1 database. A fresh copy of
 * the worker starts often (a quiet server is woken by each visit), so once the tables match this code it
 * only reads one setting instead of going over every table again.
 */
function ready(env) {
  // SP1 1.30 [SPD-02] once per database, also when this request reads it through a session.
  const key = spDbKey(env);
  let done = schemas.get(key);
  if (!done) {
    done = env.DB.prepare("SELECT value FROM settings WHERE key = 'schema'").first("value").catch(() => null).then(async (made) => {
      if (made === SCHEMA_VERSION) return;
      await batch(env, SCHEMA.map((sql) => env.DB.prepare(sql)));
      for (const sql of COLUMNS) {
        try {
          await env.DB.prepare(sql).run();
        } catch (e) {
          if (!/duplicate column/i.test(String(e?.message ?? e))) throw e;
        }
      }
      await env.DB.batch(LATE_INDEXES.map((sql) => env.DB.prepare(sql)));
      await env.DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('schema', ?)").bind(SCHEMA_VERSION).run();
    // AM 1.28 [FLAG-23] the 1.27 switches become flag overrides, once per database.
    }).then(() => migrateFlags(env)).catch((e) => {
      schemas.delete(key);
      throw e;
    });
    schemas.set(key, done);
  }
  return done;
}

const stmt = (env, sql, ...params) => env.DB.prepare(sql).bind(...params);
const one = (env, sql, ...params) => stmt(env, sql, ...params).first();
const all = async (env, sql, ...params) => (await stmt(env, sql, ...params).all()).results;
const run = async (env, sql, ...params) => meterAdd(env, await stmt(env, sql, ...params).run());
const batch = async (env, list) => {
  const results = await env.DB.batch(list);
  for (const r of results) meterAdd(env, r);
  return results;
};

/**
 * [SRV-10] The rows this Worker wrote, as D1 reports them, kept in memory and added to the day's
 * `meter|YYYY-MM-DD` counter (D1's free day runs on UTC) at most every five minutes. The hourly
 * job reads it to rest features before the free limit (N8). An isolate that ends early loses at
 * most five minutes' count, so the meter reads a little low, never high.
 */
const meters = new WeakMap();
const METER_FLUSH_MS = 5 * 60 * 1000;

function meterAdd(env, r, n = Number(r?.meta?.rows_written ?? r?.meta?.changes ?? 0) || 0) {
  if (!env?.DB || !n) return r;
  // SP1 1.30 [SPD-02] counted for the database, not the request's session.
  const m = meters.get(spDbKey(env)) ?? { n: 0, at: Date.now() };
  m.n += n;
  meters.set(spDbKey(env), m);
  return r;
}

const meterDay = (now) => new Date(now).toISOString().slice(0, 10);

/** Adds what was counted here to the day's meter; [force] does it now (the hourly job). */
export async function flushMeter(env, now, force = false) {
  const m = meters.get(spDbKey(env));
  if (!m || !m.n || (!force && now - m.at < METER_FLUSH_MS)) return;
  const n = m.n;
  m.n = 0;
  m.at = now;
  await env.DB.prepare(`INSERT INTO counters (key, count, expires) VALUES (?1, ?2, ?3)
    ON CONFLICT (key) DO UPDATE SET count = count + ?2`).bind(`meter|${meterDay(now)}`, n + 1, now + 3 * DAY).run();
}

const SETTING_KEYS = "'adopt_points', 'reward_months', 'adopt_agree', 'adopt_persons', 'amend_persons', 'auto_reward', 'reward_cap_months', 'reward_cap_days', 'first_months', 'first_cap_year'";

async function settings(env) {
  return readSettings(await all(env, `SELECT key, value FROM settings WHERE key IN (${SETTING_KEYS})`));
}

function readSettings(rows) {
  const read = (key, fallback) => {
    const n = Number(rows.find((r) => r.key === key)?.value);
    return Number.isInteger(n) ? n : fallback;
  };
  return { adoptPoints: read("adopt_points", DEFAULTS.adoptPoints), rewardMonths: read("reward_months", DEFAULTS.rewardMonths),
    adoptAgree: read("adopt_agree", DEFAULTS.adoptAgree),
    // [PLZ-03] How many different people adopt a rule on their own, and an amendment of one in use (결정 D32).
    adoptPersons: read("adopt_persons", DEFAULTS.adoptPersons), amendPersons: read("amend_persons", DEFAULTS.amendPersons),
    // [PLZ-03] Rewards go out as passes by themselves, up to a cap per phone (결정 D33).
    autoReward: read("auto_reward", DEFAULTS.autoReward), rewardCapMonths: read("reward_cap_months", DEFAULTS.rewardCapMonths),
    rewardCapDays: read("reward_cap_days", DEFAULTS.rewardCapDays),
    // [PLAZA-10] The new-information pass (결정 D79, AF-D5).
    firstMonths: read("first_months", DEFAULTS.firstMonths), firstCapYear: read("first_cap_year", DEFAULTS.firstCapYear) };
}

// SP1 1.30 [SPD-03] The server's own salt, read once per Worker: before 1.30 every public read without RATE_SALT wrote it again
// (INSERT OR IGNORE) and read it back, two D1 calls and a write before the answer.
const salts = new WeakMap();

async function salt(env) {
  if (typeof env.RATE_SALT === "string" && env.RATE_SALT) return env.RATE_SALT;
  // Until the secret is set the server keeps a salt of its own; the developer app asks for RATE_SALT.
  const key = spDbKey(env);
  let kept = salts.get(key);
  if (!kept) {
    kept = (async () => {
      const had = await one(env, "SELECT value FROM settings WHERE key = 'salt'");
      if (had) return had.value;
      await run(env, "INSERT OR IGNORE INTO settings (key, value) VALUES ('salt', ?)", randomId(32));
      return (await one(env, "SELECT value FROM settings WHERE key = 'salt'")).value;
    })();
    kept.catch(() => salts.delete(key));
    salts.set(key, kept);
  }
  return kept;
}

/** Salted hashes of the caller's address: this hour's, last hour's, the day's and one for logins. */
async function network(request, env, now) {
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  const s = await salt(env);
  const hash = async (what) => (await sha256(`${s}|${what}|${ip}`)).slice(0, 32);
  const hour = Math.floor(now / HOUR);
  return {
    now: await hash(`net:${hour}`),
    prev: await hash(`net:${hour - 1}`),
    day: await hash(`day:${Math.floor(now / DAY)}`),
    login: await hash("login"),
  };
}

/** Counts one use against [limit] for the window; false once the window's uses are spent. */
async function allow(env, key, limit, windowMs, now) {
  const row = await one(env, `INSERT INTO counters (key, count, expires) VALUES (?1, 1, ?2)
    ON CONFLICT (key) DO UPDATE SET
      count = CASE WHEN expires <= ?3 THEN 1 ELSE count + 1 END,
      expires = CASE WHEN expires <= ?3 THEN ?2 ELSE expires END
    RETURNING count`, key, now + windowMs, now);
  meterAdd(env, null, 1);
  return row.count <= limit;
}

const deviceId = (v) => (typeof v === "string" && /^[A-Za-z0-9-]{16,64}$/.test(v) ? v : null);

/** An install as the server keeps it: a hash of the app's random id, never the id itself. */
const deviceHash = async (id) => (await sha256(`device|${id}`)).slice(0, 40);

/**
 * [ADMIN-04] Who did it comes from the request: admin() puts the signed-in admin on env.actor. [ADMIN-17]
 * Only the name is written (the email stays on the admins screen), with the id for 내가 한 일. Old rows
 * are cleaned by prune() each hour, not here, so a change costs one statement.
 */
async function audit(env, now, action, target, detail = "") {
  await run(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, ?, ?, ?, ?, ?)", now, action, target,
    String(detail).slice(0, 300), env.actor ? actorName(env) : "", env.actor?.id ?? "");
}

// ---- names and what may not be shared ----

/** A name as search sees it: no spaces, marks or company forms such as (주), 주식회사, Co., Ltd. */
export function normName(s) {
  return String(s ?? "")
    .normalize("NFKC")
    .toLowerCase()
    .replace(/\((주|株|유|사|재|합)\)|주식회사|유한회사|유한책임회사|합자회사|합명회사|사단법인|재단법인|\b(co|ltd|inc|corp|llc)\b\.?/g, "")
    .replace(/[^\p{L}\p{N}]/gu, "");
}

const PERSONAL = [
  ["email", /[^\s@]+@[^\s@]+\.[a-z]{2,}/i],
  ["phone", /(\+?82[\s.-]?0?|0)\d{1,2}[\s.-]?\d{3,4}[\s.-]?\d{4}/],
  ["amount", /\d[\d,.]*\s*(원|만\s*원|천\s*원|억\s*원|won\b|krw\b)|[일이삼사오육칠팔구십백천]+\s*만\s*원|\d{1,3}(,\d{3})+|\d{5,}/i],
];

/** Why a text cannot be shared: it holds an email, a phone number or an amount of money. */
export function personal(text) {
  for (const [code, re] of PERSONAL) if (re.test(text)) return code;
  return null;
}

// ---- a setup: what a report may hold ----

class Refuse extends Error {
  constructor(code, field, status = 400) {
    super(code);
    this.code = code;
    this.field = field;
    this.status = status;
  }
}

const refuse = (code, field) => {
  throw new Refuse(code, field);
};

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const BASES = ["LEGAL", "FIXED_PER_TIME", "PERCENT_OF_BASE", "FIXED_PER_HOUR", "HOURLY_RATE", "TIERED", "UNIT"];
const UNITS = ["ORDINARY", "BASE_SHARE", "PER_HOUR", "PER_TIME"];
const CADENCES = ["MONTHLY", "PER_WORK_DAY", "PER_NIGHT_SHIFT", "PER_SHIFT_TYPE", "BONUS"];
const BREAK_PAY = ["UNPAID", "PAID", "AS_WORK"];
/** The premiums an allowance can stand in for, in the app's order. */
const COVERABLE = ["OVERTIME", "NIGHT", "HOLIDAY"];
const KIND_KEY = /^(\d{1,6}|OVERTIME|SPECIAL|COVER)$/;
const LEGACY_KINDS = { OVERTIME: "1", SPECIAL: "2", COVER: "3" };
const SCOPE = /^(all|over:\d{1,4}|law|night|window:([01]\d|2[0-3]):[0-5]\d-([01]\d|2[0-3]):[0-5]\d|holiday|rest)$/;
const TIMING = /^(B,([01]\d|2[0-3]):[0-5]\d,([01]\d|2[0-3]):[0-5]\d,\d{1,3},(-|([01]\d|2[0-3]):[0-5]\d)|A,\d{1,4}|P,\d{1,4}|S,(-|\d{1,6}(;\d{1,6})*))$/;

const given = (v) => v !== undefined && v !== null;
const object = (v, field) => (v && typeof v === "object" && !Array.isArray(v) ? v : refuse("setup", field));
const list = (v, max, field) => (Array.isArray(v) && v.length <= max ? v : refuse("setup", field));
const bool = (v, field) => (typeof v === "boolean" ? v : refuse("setup", field));
const int = (v, lo, hi, field) => (Number.isInteger(v) && v >= lo && v <= hi ? v : refuse("setup", field));
const real = (v, lo, hi, field) => (typeof v === "number" && Number.isFinite(v) && v >= lo && v <= hi ? v : refuse("setup", field));
const oneOf = (v, choices, field) => (choices.includes(v) ? v : refuse("setup", field));
const time = (v, field) => (typeof v === "string" && TIME.test(v) ? v : refuse("setup", field));

function str(v, max, field, { empty = true, lines = false, code = "setup" } = {}) {
  if (typeof v !== "string") refuse(code, field);
  const s = v.replace(lines ? /[\u0000-\u0009\u000b-\u001f\u007f]/g : /[\u0000-\u001f\u007f]/g, "").trim();
  if (s.length > max || (!empty && !s)) refuse(code, field);
  return s;
}

function date(v, field) {
  const ok = typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(`${v}T00:00:00Z`));
  return ok ? v : refuse("setup", field);
}

/**
 * [COMM-02] A company settings file (CompanyShare in the app) cut down to the fields the app reads,
 * each one checked. Anything carrying money is refused rather than dropped, so a bug in the app shows
 * up instead of the server quietly keeping something the sender never saw. [texts] collects every
 * name in it for the personal-information check.
 */
export function cleanSetup(raw, texts = []) {
  const say = (field, s) => {
    if (s) texts.push([field, s]);
    return s;
  };
  object(raw, "setup");
  if (raw.format !== "bamnat-company") refuse("setup", "format");
  if (int(raw.v, 1, 99, "v") > SETUP_VERSION) refuse("setup_version", "v");
  const shiftTypes = list(raw.shiftTypes, 24, "shiftTypes").map((t) => {
    object(t, "shiftTypes");
    return {
      name: say("shiftTypes", str(t.name, 40, "shiftTypes", { empty: false })),
      label: say("shiftTypes", str(t.label ?? "", 8, "shiftTypes")),
      color: int(t.color ?? 4284246976, 0, 4294967295, "shiftTypes"),
      start: given(t.start) ? time(t.start, "shiftTypes") : null,
      end: given(t.end) ? time(t.end, "shiftTypes") : null,
      break: int(t.break ?? 0, 0, 720, "shiftTypes"),
      breakStart: given(t.breakStart) ? time(t.breakStart, "shiftTypes") : null,
      off: bool(t.off ?? false, "shiftTypes"),
      icon: given(t.icon) ? str(t.icon, 32, "shiftTypes") : null,
    };
  });
  const names = new Set(shiftTypes.map((t) => t.name));
  if (shiftTypes.length === 0 || names.size !== shiftTypes.length) refuse("setup", "shiftTypes");
  const setup = {
    app: typeof raw.app === "string" ? raw.app.slice(0, 40) : "com.bamnat.app",
    format: "bamnat-company",
    v: raw.v,
    company: "",
    shiftTypes,
  };

  const kindIds = new Set();
  if (given(raw.extraKinds)) {
    const lines = str(raw.extraKinds, 4000, "extraKinds", { lines: true }).split("\n");
    if (lines[0].trim() !== "v2" || lines.length > 41) refuse("setup", "extraKinds");
    const kept = lines.slice(1).filter((l) => l.trim()).map((line) => {
      const f = line.split("|");
      if (f.length !== 8) refuse("setup", "extraKinds");
      const id = int(Number(f[0]), 1, 999999, "extraKinds");
      if (kindIds.has(id)) refuse("setup", "extraKinds");
      kindIds.add(id);
      const name = say("extraKinds", str(f[1], 12, "extraKinds", { empty: false }));
      const short = say("extraKinds", str(f[2], 2, "extraKinds"));
      const color = int(Number(f[3]), 0, 4294967295, "extraKinds");
      if (!TIMING.test(f[4])) refuse("setup", "extraKinds");
      // Shift ids differ from phone to phone; the shifts a 대근 covers travel by name below.
      const timing = f[4].startsWith("S,") ? "S,-" : f[4];
      return [id, name, short, color, timing, oneOf(f[5], ["BY_DAY", "HOLIDAY"], "extraKinds"),
        oneOf(f[6], ["0", "1"], "extraKinds"), oneOf(f[7], ["0", "1"], "extraKinds")].join("|");
    });
    setup.extraKinds = ["v2", ...kept].join("\n");
    const shifts = {};
    const sets = {};
    if (given(raw.extraKindShifts)) {
      for (const [k, n] of Object.entries(object(raw.extraKindShifts, "extraKindShifts"))) {
        if (kindIds.has(Number(k)) && names.has(n)) shifts[k] = n;
      }
    }
    if (given(raw.extraKindShiftSets)) {
      for (const [k, a] of Object.entries(object(raw.extraKindShiftSets, "extraKindShiftSets"))) {
        const covered = [...new Set(list(a, 24, "extraKindShiftSets").filter((n) => names.has(n)))];
        if (kindIds.has(Number(k)) && covered.length) sets[k] = covered;
      }
    }
    setup.extraKindShifts = shifts;
    setup.extraKindShiftSets = sets;
  }

  if (given(raw.rotation)) {
    const r = object(raw.rotation, "rotation");
    const steps = list(r.steps, 60, "rotation").map((s) => (s === null ? null : names.has(s) ? s : refuse("setup", "rotation")));
    if (steps.length === 0) refuse("setup", "rotation");
    setup.rotation = { anchor: date(r.anchor, "rotation"), steps };
  }
  // [PLAZA-01] Weekday and part-time work by the day of the week (Monday first), and a roster that comes every
  // month (간호): kept only when sent, so every setup from before keeps its fingerprint.
  if (given(raw.weekly)) {
    const w = list(raw.weekly, 7, "weekly");
    if (w.length !== 7) refuse("setup", "weekly");
    setup.weekly = w.map((s) => (s === null ? null : names.has(s) ? s : refuse("setup", "weekly")));
  }
  if (bool(raw.roster ?? false, "roster")) setup.roster = true;

  if (given(raw.pay)) {
    const p = object(raw.pay, "pay");
    if (p.withAmounts === true) refuse("amount", "pay");
    const pay = {
      fiveOrMore: bool(p.fiveOrMore, "pay"),
      monthlyHours: int(p.monthlyHours, 1, 400, "pay"),
      holidayMode: oneOf(p.holidayMode, ["FULL", "PREMIUM_ONLY"], "pay"),
      dailyLimit: int(p.dailyLimit ?? -1, -1, 1440, "pay"),
      weeklyLimit: int(p.weeklyLimit ?? -1, -1, 10080, "pay"),
      scheduleIsRegular: bool(p.scheduleIsRegular ?? false, "pay"),
      weekStart: oneOf(p.weekStart ?? "MONDAY", DAYS, "pay"),
      period: period(p.period),
      withAmounts: false,
      items: given(p.items) ? list(p.items, 30, "items").map((i) => item(i, names, say)) : [],
    };
    if (given(p.flexPeriod)) pay.flexPeriod = oneOf(p.flexPeriod, ["MONTH", "QUARTER"], "pay");
    if (given(p.rules)) pay.rules = rules(p.rules, names, kindIds, say);
    setup.pay = pay;
  }
  // [PAYS-03] Only a setup that uses the newer choices is 3, so apps before them can still read the rest.
  setup.v = usesNewer(setup.pay) ? 3 : Math.min(setup.v, 2);
  return setup;
}

/** Whether [pay] keeps any choice only version 3 of the file has. */
function usesNewer(pay) {
  const r = pay?.rules;
  return Boolean(r && (r.supervisory || r.holidaysU5 || r.breakPay) || pay?.items.some((i) => i.covers));
}

function period(raw) {
  if (!given(raw)) return { endDay: -1, payDay: -1, payNextMonth: false };
  const o = object(raw, "pay");
  return {
    endDay: int(o.endDay ?? -1, -1, 31, "pay"),
    payDay: int(o.payDay ?? -1, -1, 31, "pay"),
    payNextMonth: bool(o.payNextMonth ?? false, "pay"),
  };
}

/** A kind named in the rules; one the file does not list could land on someone else's own kind. */
function kindRef(key, kindIds) {
  if (typeof key !== "string" || !KIND_KEY.test(key)) refuse("setup", "rules");
  const id = LEGACY_KINDS[key] ?? String(Number(key));
  return Number(id) <= 3 || kindIds.has(Number(id)) ? id : null;
}

function rules(raw, names, kindIds, say) {
  const r = object(raw, "rules");
  const rate = (v) => (given(v) ? real(v, 0, 5, "rules") : null);
  const out = {
    ot: rate(r.ot),
    night: rate(r.night),
    hw: rate(r.hw),
    ho: rate(r.ho),
    restDay: oneOf(r.restDay ?? "LAW", ["LAW", "HOLIDAY", "OVERTIME"], "rules"),
    offDay: oneOf(r.offDay ?? "HOLIDAY", ["HOLIDAY", "OVERTIME"], "rules"),
    weeklyHolidayDay: oneOf(r.weeklyHolidayDay ?? "UNKNOWN", ["FIRST_REST", "UNKNOWN", ...DAYS], "rules"),
    publicHolidays: bool(r.publicHolidays ?? true, "rules"),
    roundUnit: int(r.roundUnit ?? 1, 1, 60, "rules"),
    round: oneOf(r.round ?? "DOWN", ["UP", "NEAREST", "DOWN"], "rules"),
    weeklyHoliday: bool(r.weeklyHoliday ?? true, "rules"),
    // Leave days are the sender's own, never the company's.
    annualLeave: null,
    leaveCarry: 0,
    holidayLeaveFree: bool(r.holidayLeaveFree ?? false, "rules"),
    holidayByStart: bool(r.holidayByStart ?? false, "rules"),
    extraByShift: {},
    extra: {},
  };
  // [PAYS-03] Kept only when set, so a setup without them keeps its fingerprint and reads as before.
  if (bool(r.supervisory ?? false, "rules")) out.supervisory = true;
  if (bool(r.holidaysU5 ?? false, "rules")) out.holidaysU5 = true;
  // [HOLI-01][PLAZA-01] 1.25 V: 교대제라 상관없음 is told from 대체휴일 (both unpaid 빨간날) only by this, kept only then.
  if (out.publicHolidays === false && r.holidayWork === "SHIFT_NORMAL") out.holidayWork = "SHIFT_NORMAL";
  const breakPay = oneOf(r.breakPay ?? "UNPAID", BREAK_PAY, "rules");
  if (breakPay !== "UNPAID") out.breakPay = breakPay;
  if (given(r.publicHolidayAs)) {
    const kind = kindRef(String(r.publicHolidayAs), kindIds);
    if (kind) out.publicHolidayAs = kind;
  }
  if (given(r.nightStart)) out.nightStart = time(r.nightStart, "rules");
  if (given(r.nightEnd)) out.nightEnd = time(r.nightEnd, "rules");
  if (given(r.extra)) {
    for (const [k, e] of Object.entries(object(r.extra, "rules"))) {
      const kind = kindRef(k, kindIds);
      if (kind) out.extra[kind] = extraRule(e, say);
    }
  }
  if (given(r.extraByShift)) {
    for (const [k, byShift] of Object.entries(object(r.extraByShift, "rules"))) {
      const kind = kindRef(k, kindIds);
      if (!kind) continue;
      const kept = {};
      for (const [n, e] of Object.entries(object(byShift, "rules"))) if (names.has(n)) kept[n] = extraRule(e, say);
      out.extraByShift[kind] = kept;
    }
  }
  return out;
}

function extraRule(raw, say) {
  const e = object(raw, "rules");
  // The company's way, never its money: amounts per time or hour and 1회 수당 stay on the phone.
  if ((e.amount ?? 0) !== 0 || (e.bonus ?? 0) !== 0) refuse("amount", "rules");
  const out = {
    basis: oneOf(e.basis, BASES, "rules"),
    multiplier: real(e.multiplier ?? 1.5, 0, 10, "rules"),
    amount: 0,
    min: int(e.min ?? 0, 0, 1440, "rules"),
    night: bool(e.night ?? true, "rules"),
    percent: real(e.percent ?? 0.1, 0, 10, "rules"),
    cover: int(e.cover ?? -1, -1, 1440, "rules"),
    premiums: bool(e.premiums ?? false, "rules"),
    bonus: 0,
    unit: oneOf(e.unit ?? "ORDINARY", UNITS, "rules"),
    unitMin: int(e.unitMin ?? 480, 30, 1440, "rules"),
    adds: given(e.adds) ? list(e.adds, 10, "rules").map((p) => premium(p, say)) : [],
  };
  if (given(e.dayTiers)) out.dayTiers = tiers(e.dayTiers);
  if (given(e.nightTiers)) out.nightTiers = tiers(e.nightTiers);
  return out;
}

function tiers(v) {
  return list(v, 8, "rules").map((t) => {
    object(t, "rules");
    return { min: int(t.min, 0, 1440, "rules"), premium: real(t.premium, 0, 5, "rules") };
  });
}

function premium(raw, say) {
  const p = object(raw, "rules");
  const key = str(p.key, 8, "rules", { empty: false });
  if (!/^[A-Za-z0-9_]+$/.test(key)) refuse("setup", "rules");
  if (typeof p.scope !== "string" || !SCOPE.test(p.scope)) refuse("setup", "rules");
  return {
    key,
    name: say("rules", str(p.name ?? "", 12, "rules")),
    rate: real(p.rate, 0, 3, "rules"),
    on: bool(p.on ?? false, "rules"),
    scope: p.scope,
    offDays: bool(p.offDays ?? false, "rules"),
  };
}

function item(raw, names, say) {
  const i = object(raw, "items");
  const a = i.amount;
  // Allowance names and how they are paid travel; what anyone is paid does not.
  if (given(a) && (typeof a !== "object" || (Array.isArray(a.entries) && a.entries.length > 0) || given(a.raise))) refuse("amount", "items");
  const out = {
    id: str(i.id, 24, "items", { empty: false }),
    name: say("items", str(i.name, 20, "items", { empty: false })),
    amount: { entries: [], raise: null },
    cadence: oneOf(i.cadence ?? "MONTHLY", CADENCES, "items"),
    types: given(i.types) ? [...new Set(list(i.types, 24, "items").filter((n) => names.has(n)))] : [],
    ordinary: bool(i.ordinary ?? false, "items"),
    taxable: bool(i.taxable ?? true, "items"),
    months: given(i.months) ? [...new Set(list(i.months, 12, "items").map((m) => int(m, 1, 12, "items")))].sort((x, y) => x - y) : [],
    fixedOvertime: int(i.fixedOvertime ?? 0, 0, 100000, "items"),
    festivals: given(i.festivals) ? [...new Set(list(i.festivals, 2, "items").map((f) => oneOf(f, ["SEOLLAL", "CHUSEOK"], "items")))] : [],
    festivalBefore: bool(i.festivalBefore ?? false, "items"),
  };
  // Left out when there is none: the app reads a present value as a number.
  if (given(i.percentOfBase)) out.percentOfBase = real(i.percentOfBase, 0, 50, "items");
  // [PAYS-03] The premiums it is paid instead of; kept only when not what the app assumes anyway
  // (a 고정 연장수당 stands in for 연장, any other allowance for none).
  if (given(i.covers)) {
    const picked = new Set(list(i.covers, COVERABLE.length, "items").map((k) => oneOf(k, COVERABLE, "items")));
    const covers = COVERABLE.filter((k) => picked.has(k));
    const usual = out.fixedOvertime > 0 && out.cadence === "MONTHLY" ? ["OVERTIME"] : [];
    if (covers.join() !== usual.join()) out.covers = covers;
  }
  return out;
}

/** A report body as sent by the app, checked; throws [Refuse] with what is wrong. */
export function readReport(body) {
  object(body, "body");
  if (body.v !== 1) refuse("version", "v");
  // [PLZ-12] The job plaza and region it belongs to; a rule for a whole job (범용) needs no company.
  const job = given(body.job) ? oneOf(body.job, Object.keys(JOBS), "job") : "";
  const region = given(body.region) && body.region !== "" ? oneOf(body.region, Object.keys(REGIONS), "region") : "";
  const topic = body.topic === true;
  if (topic && !job) refuse("job", "job");
  const company = topic ? topicName(job, region) : str(body.company, LIMITS.company, "company", { empty: false, code: "company" });
  if (normName(company).length < 2) refuse("company", "company");
  const amends = given(body.amends) ? (typeof body.amends === "string" && /^[A-Za-z0-9]{16}$/.test(body.amends) ? body.amends : refuse("amends", "amends")) : null;
  const dept = given(body.dept) ? str(body.dept, LIMITS.dept, "dept", { code: "dept" }) : "";
  if (typeof body.verified !== "boolean") refuse("verified", "verified");
  // [EXTRA-06] A way the app cannot set yet: the setup is only as close as the sender could get.
  if (given(body.ask) && typeof body.ask !== "boolean") refuse("ask", "ask");
  const ask = body.ask === true;
  const note = given(body.note) ? str(body.note, ask ? LIMITS.ask : LIMITS.note, "note", { lines: true, code: "note" }) : "";
  if (ask && !note) refuse("note", "note");
  const texts = [];
  const setup = cleanSetup(body.setup, texts);
  for (const [field, text] of [["company", company], ["dept", dept], ["note", note], ...texts]) {
    const why = personal(text);
    if (why) refuse(why, field);
  }
  // AI 1.27 [PLAZA-30]: 명세서와 맞춰 봤어요 counts only with the month the phone matched and whether it matched.
  const proof = readProof(body.proof);
  return { company, dept, deptNorm: normName(dept), verified: body.verified && proofCounts(proof), claim: body.verified && !proof, proof, note, setup, ask,
    job, region, topic, amends };
}

// ---- 직군별 광장 [PLZ-12][PLZ-13][PLZ-14] ----

/** The job plazas (결정 D38): every company, hospital and topic sits in one, and the board can show one. */
export const JOBS = {
  shift: "교대 근무",
  factory: "제조·생산",
  nurse: "간호사·병원",
  public: "공무원",
  police: "경찰",
  fire: "소방",
  care: "요양·돌봄",
  security: "경비·시설",
  transport: "운수·운전",
  hourly: "알바·시급",
  construction: "건설 일용직·기능공",
  field: "현장·프리랜서",
  shop: "자영업",
};

/** 시·도 by the code 통계청 gives them (행정구역 분류), so the app and the server agree on the same key. */
export const REGIONS = {
  "11": "서울", "21": "부산", "22": "대구", "23": "인천", "24": "광주", "25": "대전", "26": "울산", "29": "세종",
  "31": "경기", "32": "강원", "33": "충북", "34": "충남", "35": "전북", "36": "전남", "37": "경북", "38": "경남", "39": "제주",
};

/** A rule for a whole job, not one company: "간호사·병원 공통", "건설 일용직·기능공 공통 (서울)". */
const topicName = (job, region) => `${JOBS[job]} 공통${region ? ` (${REGIONS[region]})` : ""}`;

// ---- the same rule ----

/**
 * [COMM-03] What makes two reports "the same rule": shift times and their order, the kinds of extra
 * work and every pay rule; not what anyone called their shifts, their colors, or which day of the
 * rotation their team is on.
 */
function canonical(setup) {
  const sigs = new Map(setup.shiftTypes.map((t) => [t.name, t.off ? "off" : `${t.start ?? ""}-${t.end ?? ""}/${t.break}@${t.breakStart ?? ""}`]));
  const sig = (name) => (name === null ? "-" : sigs.get(name) ?? "?");
  const kinds = kindLines(setup).map((k) => ({
    id: k.id,
    sig: { timing: k.timing.startsWith("S,") ? "S" : k.timing, covers: k.covers.map(sig).sort(), offDay: k.offDay, counts: k.counts },
  }));
  // Built-in kinds keep their number; a company's own are numbered by what they are.
  const keys = new Map(kinds.filter((k) => Number(k.id) <= 3).map((k) => [k.id, k.id]));
  kinds.filter((k) => Number(k.id) > 3).sort((a, b) => cmp(stable(a.sig), stable(b.sig))).forEach((k, i) => keys.set(k.id, `c${i}`));
  const kindOf = (id) => keys.get(String(id)) ?? (Number(id) <= 3 ? String(id) : `?${id}`);
  const rule = (e) => ({ ...e, adds: e.adds.map((a) => ({ ...a, name: normName(a.name) })).sort((a, b) => cmp(a.key, b.key)) });
  const p = setup.pay;
  const r = p?.rules;
  return {
    cycle: smallestRotation((setup.rotation?.steps ?? []).map(sig)),
    // [PLAZA-01] Only when there, so a setup without them reads as it always did.
    ...(setup.weekly ? { weekly: setup.weekly.map(sig) } : {}),
    ...(setup.roster ? { roster: true } : {}),
    kinds: kinds.map((k) => ({ key: kindOf(k.id), ...k.sig })).sort((a, b) => cmp(a.key, b.key)),
    pay: p ? {
      fiveOrMore: p.fiveOrMore,
      monthlyHours: p.monthlyHours,
      holidayMode: p.holidayMode,
      dailyLimit: p.dailyLimit,
      weeklyLimit: p.weeklyLimit,
      scheduleIsRegular: p.scheduleIsRegular,
      flexPeriod: p.flexPeriod ?? null,
      weekStart: p.weekStart,
      period: p.period,
      rules: r ? {
        ...r,
        offDay: null,
        publicHolidayAs: r.publicHolidayAs ? kindOf(r.publicHolidayAs) : null,
        extra: Object.fromEntries(Object.entries(r.extra).map(([k, e]) => [kindOf(k), rule(e)])),
        extraByShift: Object.fromEntries(Object.entries(r.extraByShift).map(([k, by]) =>
          [kindOf(k), Object.fromEntries(Object.entries(by).map(([n, e]) => [sig(n), rule(e)]))])),
      } : null,
      items: p.items.map((i) => ({
        name: normName(i.name),
        cadence: i.cadence,
        ordinary: i.ordinary,
        taxable: i.taxable,
        months: i.months,
        percentOfBase: i.percentOfBase ?? null,
        fixedOvertime: i.fixedOvertime,
        festivals: [...i.festivals].sort(),
        festivalBefore: i.festivalBefore,
        types: i.types.map(sig).sort(),
        // Only when set, so allowances from before version 3 keep their fingerprint.
        ...(i.covers ? { covers: i.covers } : {}),
      })).sort((a, b) => cmp(stable(a), stable(b))),
    } : null,
  };
}

export async function fingerprint(setup) {
  return sha256(stable(canonical(setup)));
}

const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

function smallestRotation(steps) {
  let best = null;
  for (let i = 0; i < steps.length; i++) {
    const turned = [...steps.slice(i), ...steps.slice(0, i)];
    const key = turned.join("\u0001");
    if (best === null || key < best.key) best = { key, turned };
  }
  return best ? best.turned : [];
}

/** JSON with keys in order, so equal values give equal text. */
function stable(v) {
  if (Array.isArray(v)) return `[${v.map(stable).join(",")}]`;
  if (v && typeof v === "object") return `{${Object.keys(v).sort().map((k) => `${JSON.stringify(k)}:${stable(v[k])}`).join(",")}}`;
  return JSON.stringify(v ?? null);
}

function kindLines(setup) {
  return (setup.extraKinds ?? "").split("\n").slice(1).filter(Boolean).map((line) => {
    const [id, name, , , timing, offDay, counts, hidden] = line.split("|");
    const covers = setup.extraKindShiftSets?.[id] ?? (setup.extraKindShifts?.[id] ? [setup.extraKindShifts[id]] : []);
    return { id, name, timing, offDay, counts: counts === "1", hidden: hidden === "1", covers };
  });
}

// ---- a setup in words, for the developer app ----

const KIND_NAMES = { 1: "잔업", 2: "특근", 3: "대근" };
const WEEKDAYS = { MONDAY: "월", TUESDAY: "화", WEDNESDAY: "수", THURSDAY: "목", FRIDAY: "금", SATURDAY: "토", SUNDAY: "일" };
const pct = (x) => `${Math.round(x * 1000) / 10}%`;
const hours = (m) => (m % 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m / 60}시간`);

/** [ADMIN-01] A setup in a few lines of plain Korean, to review at a glance: [label, text] pairs. */
export function describe(setup) {
  const lines = [];
  const add = (label, text) => {
    if (text) lines.push([label, text]);
  };
  add("근무", setup.shiftTypes.map((t) => (t.off ? `${t.name}(쉬는 날)`
    : `${t.name} ${t.start ?? "?"}~${t.end ?? "?"}${t.break ? ` 휴게 ${t.break}분` : ""}`)).join(" · "));
  if (setup.rotation) add(`순서 ${setup.rotation.steps.length}일`, setup.rotation.steps.map((s) => s ?? "빈 날").join(" "));
  // [PLAZA-01] The same words as the app's SetupFacts.
  if (setup.weekly) add("요일별 근무", setup.weekly.map((s, i) => `${"월화수목금토일"[i]} ${s ?? "쉼"}`).join(" · "));
  if (setup.roster) add("근무표", "매달 근무표(순서 없음)");
  const kinds = kindLines(setup);
  const kindName = (id) => kinds.find((k) => k.id === String(id))?.name ?? KIND_NAMES[id] ?? `종류 ${id}`;
  add("연장·휴무일 근무", kinds.filter((k) => !k.hidden).map((k) => `${k.name}(${timingText(k)})`).join(" · "));
  const p = setup.pay;
  if (p) {
    add("사업장", [p.fiveOrMore ? "5인 이상" : "5인 미만", `월 ${p.monthlyHours}시간`,
      p.flexPeriod ? `탄력 ${p.flexPeriod === "MONTH" ? "1개월" : "3개월"}` : "", p.rules?.supervisory ? "감시·단속 승인" : ""].filter(Boolean).join(" · "));
    const r = p.rules;
    if (r) {
      if (!p.fiveOrMore && r.holidaysU5) add("공휴일", "유급휴일로 줌 (5인 미만)");
      if (r.breakPay) add("휴게", r.breakPay === "PAID" ? "유급 (일한 시간은 아님)" : "일한 시간으로 침");
      add("가산율", [["연장", r.ot], ["야간", r.night], ["휴일", r.hw], ["휴일 8시간 넘게", r.ho]]
        .filter(([, v]) => v !== null).map(([n, v]) => `${n} ${pct(v)}`).join(" · ") || "법대로");
      if (r.nightStart && r.nightEnd) add("야간 시간", `${r.nightStart}~${r.nightEnd}`);
      add("쉬는 날 근무", { LAW: "법대로", HOLIDAY: "휴일근로로", OVERTIME: "연장근로로" }[r.restDay]);
      add("주휴일", r.weeklyHolidayDay === "FIRST_REST" ? "쉬는 날 중 첫날" : WEEKDAYS[r.weeklyHolidayDay] ? `${WEEKDAYS[r.weeklyHolidayDay]}요일` : "");
      // [HOLI-01] 빨간날 근무, one of four ways; the same words as the app's SetupFacts.holidayText.
      if (p.fiveOrMore || r.publicHolidayAs) add("빨간날 근무", holidayText(r, r.publicHolidayAs ? kindName(r.publicHolidayAs) : null));
      for (const [k, e] of Object.entries(r.extra)) add(kindName(k), ruleText(e));
      for (const [k, by] of Object.entries(r.extraByShift)) for (const [n, e] of Object.entries(by)) add(`${kindName(k)} · ${n}`, ruleText(e));
    }
    if (p.items.length) add("수당", p.items.map(itemText).join(" · "));
  }
  return lines;
}

function holidayText(r, kind) {
  const mode = r.publicHolidays === false ? (r.holidayWork === "SHIFT_NORMAL" ? "SHIFT_NORMAL" : "SUBSTITUTE") : r.publicHolidayAs ? "AS_EXTRA" : "PREMIUM";
  const head = { PREMIUM: "휴일 근무 가산", AS_EXTRA: `${kind ?? "특근"}처럼`, SUBSTITUTE: "대체휴일", SHIFT_NORMAL: "교대제라 상관없음" }[mode];
  return head + (r.holidayByStart && (mode === "PREMIUM" || mode === "AS_EXTRA") ? ", 밤샘은 시작한 날로" : "");
}

function timingText(k) {
  const f = k.timing.split(",");
  if (f[0] === "B") return `${f[1]}~${f[2]}`;
  if (f[0] === "A") return `근무 뒤 ${hours(Number(f[1]))}`;
  if (f[0] === "P") return `근무 앞 ${hours(Number(f[1]))}`;
  return k.covers.length ? `${k.covers.join("·")} 대신` : "모든 근무 대신";
}

function ruleText(e) {
  switch (e.basis) {
    case "LEGAL": return "법대로";
    case "FIXED_PER_TIME": return "1회 정액 (금액은 각자 넣어요)";
    case "FIXED_PER_HOUR": return "시간당 정액 (금액은 각자 넣어요)";
    case "PERCENT_OF_BASE": return `${e.cover > 0 ? hours(e.cover) : "한 번"}에 기본급 ${pct(e.percent)}${e.premiums ? " + 법정 가산" : ""}`;
    case "HOURLY_RATE": return `통상시급 × ${pct(e.multiplier)}`;
    case "TIERED": return `시간 구간별 가산 ${(e.dayTiers ?? []).map((t) => `${hours(t.min)}부터 +${pct(t.premium)}`).join(", ")}`.trim();
    default: {
      const unit = {
        ORDINARY: "통상시급",
        BASE_SHARE: `${hours(e.unitMin)}에 기본급 ${pct(e.percent)}`,
        PER_HOUR: "시간당 정액 (금액은 각자)",
        PER_TIME: `1회 정액 (금액은 각자)${e.cover > 0 ? ` ${hours(e.cover)}까지` : ""}`,
      }[e.unit];
      const adds = e.adds.filter((a) => a.on).map((a) => `${a.name || a.key} ${pct(a.rate)}${scopeText(a.scope)}${a.offDays ? " (쉬는 날만)" : ""}`);
      return [unit, ...adds].join(" + ") + (e.min > 0 ? ` · 최소 ${hours(e.min)}` : "");
    }
  }
}

function scopeText(scope) {
  if (scope.startsWith("over:")) return ` (${hours(Number(scope.slice(5)))} 넘게)`;
  if (scope.startsWith("window:")) return ` (${scope.slice(7).replace("-", "~")})`;
  return "";
}

function itemText(i) {
  const how = {
    MONTHLY: "매달",
    PER_WORK_DAY: "근무일마다",
    PER_NIGHT_SHIFT: "야간마다",
    PER_SHIFT_TYPE: `${i.types.join("·") || "정한 근무"}마다`,
    BONUS: i.months.length ? `${i.months.join("·")}월` : "정한 달",
  }[i.cadence];
  const covers = i.covers ? (i.covers.length ? `${i.covers.map((k) => ({ OVERTIME: "연장", NIGHT: "야간", HOLIDAY: "휴일" })[k]).join("·")} 가산 대신` : "가산과 따로") : "";
  const parts = [how, given(i.percentOfBase) ? `기본급 ${pct(i.percentOfBase)}` : "", i.festivals.map((f) => (f === "SEOLLAL" ? "설" : "추석")).join("·"), covers];
  return `${i.name}(${parts.filter(Boolean).join(" ")})`;
}

// AX 1.30 [ADMX-02] The whole lines as before (added, removed) and, from src/plaza-diff.js, the one-line summary and the
// field-by-field facts with their word tokens: every caller (검토, 여러 판, 광장 others·history) gets them.
function diffLines(before, after) {
  return lineChanges(before, after);
}

// ---- what everyone can read and send ----

/** What the app tells someone before they send: the points a setup needs and the reward once it is adopted. */
async function terms(env) {
  const { adoptPoints, rewardMonths, adoptAgree, firstMonths } = await settings(env);
  // [PLAZA-10] firstMonths: what a company's first setup with its pay earns, said on the app's 나누기 card.
  return { threshold: adoptPoints, rewardMonths, agree: adoptAgree, firstMonths };
}

async function search(env, url) {
  const raw = String(url.searchParams.get("q") ?? "");
  if (isChoseong(raw)) return json({ companies: await searchChoseong(env, raw.replace(/\s/g, "").slice(0, 20)) }, 200, SEARCH_CACHE);
  const q = normName(raw).slice(0, 40);
  if (q.length < 2) return json({ companies: [] }, 200, SEARCH_CACHE);
  const esc = (t) => `%${t.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  const like = esc(q);
  // AS 1.29 [SRCH-02] The name as said in Korean letters too: 지에스칼텍스 finds GS칼텍스, gs칼텍스 still finds it by its letters.
  const said = spoken(raw).slice(0, 60);
  const saidLike = said.length >= 2 ? esc(said) : like;
  // Names from before 1.29 get their reading beside the search, not before it.
  const [rows] = await Promise.all([all(env, `
    SELECT c.id, c.name, c.norm, c.said,
      (SELECT a.name FROM aliases a WHERE a.company_id = c.id AND (a.norm LIKE ?1 ESCAPE '\\' OR a.said LIKE ?3 ESCAPE '\\')
        ORDER BY a.norm = ?2 DESC LIMIT 1) AS alias,
      EXISTS (SELECT 1 FROM aliases a WHERE a.company_id = c.id AND a.norm = ?2) AS alias_exact,
      (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS adopted,
      (SELECT COUNT(DISTINCT p.grp) FROM reports p WHERE p.company_id = c.id AND p.status = 'pending') AS pending
    FROM companies c
    WHERE c.hidden = 0 AND c.merged_into IS NULL
      AND (c.norm LIKE ?1 ESCAPE '\\' OR c.said LIKE ?3 ESCAPE '\\'
        OR EXISTS (SELECT 1 FROM aliases a WHERE a.company_id = c.id AND (a.norm LIKE ?1 ESCAPE '\\' OR a.said LIKE ?3 ESCAPE '\\')))
    LIMIT 200`, like, q, saidLike), fillNames(env)]);
  const exact = (c) => (c.norm === q || c.said === said || c.alias_exact === 1 ? 1 : 0);
  const starts = (c) => (c.norm.startsWith(q) || (said.length >= 2 && String(c.said ?? "").startsWith(said)) ? 1 : 0);
  const companies = rows
    // [PLAZA-10] One that coworkers are still checking shows up too (결정 D79): a company's first setup, even with only its
    // shifts, is there for the next person to find and use at once.
    .filter((c) => c.adopted > 0 || c.pending > 0)
    .sort((a, b) => exact(b) - exact(a) || (b.adopted > 0) - (a.adopted > 0) || starts(b) - starts(a) ||
      a.name.length - b.name.length || a.name.localeCompare(b.name, "ko"))
    .slice(0, 20)
    .map((c) => ({ id: c.id, name: c.name, alias: c.norm.includes(q) || (said.length >= 2 && String(c.said ?? "").includes(said)) ? null : c.alias,
      adopted: c.adopted, pending: c.pending, ...(c.adopted === 0 ? { first: true } : {}) }));
  // AS 1.29 [SRCH-01] Every company listed with its 대표 주기 and users, read for all of them at once. Before 1.29 only the
  // first five had it, read one company after another (41 reads in a row for eight companies): an answer the app gave
  // up waiting for, and a company 6th or lower with no 대표 주기 at all.
  await withLeads(env, companies);
  return json({ companies }, 200, SEARCH_CACHE);
}

// ---- [PLAZA-03] 초성 찾기 ----

const CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";

/**
 * The 초성 of each Hangul syllable of [s] (U+AC00..U+D7A3 is (초성 × 21 + 중성) × 28 + 종성, so 초성 = (code − 0xAC00) / 588,
 * The Unicode Standard 3.12); other letters and digits as they are. The app's core Choseong is the same rule.
 */
export function choseong(s) {
  let out = "";
  for (const ch of String(s ?? "")) {
    const c = ch.codePointAt(0);
    if (c >= 0xac00 && c <= 0xd7a3) out += CHO[Math.floor((c - 0xac00) / 588)];
    else if (CHO.includes(ch)) out += ch;
    else if (/[\p{L}\p{N}]/u.test(ch)) out += ch.toLowerCase();
  }
  return out;
}

/** Whether [q] is 초성 only (two or more), typed as compatibility jamo (ㅅ is U+3145). */
export function isChoseong(q) {
  const t = String(q ?? "").replace(/\s/g, "");
  return t.length >= 2 && [...t].every((ch) => CHO.includes(ch));
}

/**
 * Names whose 초성 (and, AS 1.29 [SRCH-02], Korean reading) were not worked out yet (new, renamed, or from before 1.25 / 1.29)
 * get them, a batch at a time.
 */
async function fillNames(env) {
  // AS 1.29 [SRCH-01] Once a look found nothing to fill, this Worker does not look again for a while (a write here looks at once).
  const key = spDbKey(env);
  if (Date.now() - (namesFilled.get(key) ?? 0) < NAMES_FILLED_MS) return;
  const [cs, as] = await Promise.all([
    all(env, "SELECT id, name, norm, cho, said FROM companies WHERE cho = '' OR said = '' LIMIT 500"),
    all(env, "SELECT norm, name, cho, said FROM aliases WHERE cho = '' OR said = '' LIMIT 500"),
  ]);
  if (!cs.length && !as.length) {
    namesFilled.set(key, Date.now());
    return;
  }
  await batch(env, [
    ...cs.map((c) => stmt(env, "UPDATE companies SET cho = ?, said = ? WHERE id = ?", c.cho || choseong(c.norm) || "-", c.said || spoken(c.name) || "-", c.id)),
    ...as.map((a) => stmt(env, "UPDATE aliases SET cho = ?, said = ? WHERE norm = ?", a.cho || choseong(a.norm) || "-", a.said || spoken(a.name) || "-", a.norm)),
  ]);
}

async function searchChoseong(env, q) {
  await fillNames(env);
  const like = `%${q}%`;
  const rows = await all(env, `
    SELECT c.id, c.name, c.cho,
      (SELECT a.name FROM aliases a WHERE a.company_id = c.id AND a.cho LIKE ?1 LIMIT 1) AS alias,
      (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS adopted,
      (SELECT COUNT(DISTINCT p.grp) FROM reports p WHERE p.company_id = c.id AND p.status = 'pending') AS pending
    FROM companies c
    WHERE c.hidden = 0 AND c.merged_into IS NULL
      AND (c.cho LIKE ?1 OR EXISTS (SELECT 1 FROM aliases a WHERE a.company_id = c.id AND a.cho LIKE ?1))
    LIMIT 200`, like);
  // [PLAZA-10] 초성 are a guess, so companies with a rule in use come first; a first setup still being checked is found too.
  const out = rows.filter((c) => c.adopted > 0 || c.pending > 0)
    .sort((a, b) => (b.adopted > 0) - (a.adopted > 0) || b.cho.startsWith(q) - a.cho.startsWith(q) || a.name.length - b.name.length ||
      a.name.localeCompare(b.name, "ko"))
    .slice(0, 20)
    .map((c) => ({ id: c.id, name: c.name, alias: c.cho.includes(q) ? null : c.alias, adopted: c.adopted, pending: c.pending,
      ...(c.adopted === 0 ? { first: true } : {}) }));
  // AH 1.27 [PLAZA-14] AS 1.29 [SRCH-01] every one, read at once.
  await withLeads(env, out);
  return out;
}

async function company(env, id, request, url = null) {
  const r = await companyData(env, id, request, readWith(url));
  return json(r.body, r.status);
}

/** SP1 1.30 [SPD-05] What `?with=` asks for besides the page: room, talk (a member's only). */
const readWith = (url) => new Set(String(url?.searchParams.get("with") ?? "").split(",").map((w) => w.trim()).filter(Boolean));

/**
 * A company's page as {status, body}. SP1 1.30 [SPD-01] Two rounds of reads whatever the number of versions: the company, who
 * asks and its lists together, then every version's state, 대표 report, opinions and pay marks together (before 1.30 eight or
 * nine rounds, 33 statements for a company with three versions). [known] is who asks when the caller already knows (a join).
 * [SPD-05] [parts] room and talk start beside the second round, for a member.
 */
async function companyData(env, id, request, parts = new Set(), known = null) {
  const now = Date.now();
  // AS 1.29 [SRCH-05] The page's reads go together, not one setup after another (the same answer, in the same order).
  const [c, who, { adoptPoints }, aliasRows, rules, groups, downRows, stable] = await Promise.all([
    one(env, "SELECT * FROM companies WHERE id = ?", id),
    // [PLAZA-08] Pay only for a phone that joined this company; anyone else (an app before 1.26 too) gets the shifts.
    known ?? viewer(request, env, id),
    settings(env),
    all(env, "SELECT name FROM aliases WHERE company_id = ? ORDER BY name", id),
    all(env, "SELECT * FROM rules WHERE company_id = ? AND withdrawn = 0 AND superseded = 0 ORDER BY dept_norm", id),
    all(env, `SELECT grp, MAX(created_at) AS last FROM reports WHERE company_id = ? AND status = 'pending'
      GROUP BY grp ORDER BY last DESC LIMIT 30`, id),
    // What the developer took down or turned down (blocker()'s withdrawn and rejected), for every version at once.
    all(env, "SELECT grp FROM rules WHERE company_id = ?1 AND withdrawn = 1 UNION SELECT grp FROM reports WHERE company_id = ?1 AND status = 'rejected'", id),
    stableFacts(env, id),
  ]);
  if (!c || c.hidden) return { status: 404, body: { error: "not_found" } };
  if (c.merged_into) return { status: 200, body: { mergedInto: c.merged_into } };
  const view = (text) => (who.member ? shared(text, c.name) : shiftsOnly(shared(text, c.name)));
  const words = (setup) => (who.member ? describe(setup) : shiftLines(describe(setup)));
  const waitingGroups = groups.filter((g) => !rules.some((r) => r.grp === g.grp));
  const pair = (grp) => ({ company: c.id, grp });
  const extra = who.member && (parts.has("room") || parts.has("talk")) ? Promise.all([
    parts.has("room") ? roomData(env, c, who, now) : null,
    parts.has("talk") ? talkData(request, env, c.id, ROOM, { c, who }) : null,
  ]) : null;
  // Its failure is the answer's, below; not a second one if the page's own reads fail first.
  extra?.catch(() => {});
  const [facts, reps] = await Promise.all([readStates(env, [...rules, ...waitingGroups].map((r) => pair(r.grp))),
    representatives(env, waitingGroups.map((g) => pair(g.grp)))]);
  // What a setup tells about its pay: whether it has one, for a non-member that it is hidden, and [stable] once it settled
  // (결정 D79: three phones on it and no other version for 30 days).
  const mark = (deptNorm, grp, text) => {
    const has = JSON.parse(text).pay !== undefined;
    const st = stable(deptNorm, grp, facts.uses(c.id, grp), now);
    return { pay: has, ...(has && !who.member ? { payHidden: true } : {}), ...(st.stable ? { stable: true } : {}) };
  };
  const setups = rules.map((r) => ({ ...facts.state(c.id, r.grp, adoptPoints, true), dept: r.dept, version: r.version, adoptedAt: iso(r.adopted_at),
    setup: view(r.setup), lines: words(JSON.parse(r.setup)), ...mark(r.dept_norm, r.grp, r.setup) }));
  const down = new Set(downRows.map((r) => r.grp));
  const pending = [];
  for (const g of waitingGroups) {
    const s = facts.state(c.id, g.grp, adoptPoints, false);
    const rep = reps.get(pairKey(c.id, g.grp));
    if (s.persons === 0 || !rep) continue;
    // What the developer turned down is not offered to coworkers again (a department with a rule in use waits for it anyway).
    if (!rules.some((r) => r.dept_norm === rep.dept_norm) && down.has(g.grp)) continue;
    pending.push({ s, rep });
  }
  // The likeliest first, and a few for each department are enough.
  pending.sort((a, b) => b.s.points - a.s.points);
  const shown = new Map();
  const picked = [];
  for (const { s, rep } of pending) {
    const n = shown.get(rep.dept_norm) ?? 0;
    if (n >= 3) continue;
    shown.set(rep.dept_norm, n + 1);
    picked.push({ s, rep });
  }
  setups.push(...picked.map(({ s, rep }) => ({ ...s, dept: rep.dept, setup: view(rep.setup), lines: words(JSON.parse(rep.setup)),
    ...mark(rep.dept_norm, rep.grp, rep.setup) })));
  // AH 1.27 [PLAZA-14][PLAZA-15] Each department's 대표 주기, doubted versions and 맞아요·달라요 with this phone's own.
  markLeads(setups, (grp) => facts.opinions(c.id, grp, who.dev));
  const body = { id: c.id, name: c.name, kind: c.kind ?? "company", job: c.job ?? "", region: c.region ?? "", aliases: aliasRows.map((a) => a.name),
    setups, member: who.member, ...(who.limited ? { limited: true } : {}), join: { perPhone: MEMBER.perPhone, waitDays: MEMBER.waitDays } };
  // SP1 1.30 [SPD-05] The room (as /room answers it) and the room's talk (as /setups/room/posts does), keys an older app never reads.
  if (extra) {
    const [roomR, talkR] = await extra;
    if (roomR?.status === 200) body.room = roomR.body;
    if (talkR?.status === 200) body.talk = talkR.body;
  }
  return { status: 200, body };
}

/**
 * SP1 1.30 [SPD-01] stableOf() for every version of one company from two reads: when each version came and the department's
 * last other version and adoption. stable(deptNorm, grp, uses, now) then answers as stableOf does.
 */
async function stableFacts(env, companyId) {
  const [sent, ruled] = await Promise.all([
    all(env, `SELECT grp, dept_norm, MIN(created_at) AS born, MAX(CASE WHEN status IN ('pending', 'adopted', 'replaced') THEN created_at END) AS last
      FROM reports WHERE company_id = ? GROUP BY grp, dept_norm`, companyId),
    all(env, "SELECT dept_norm, MAX(adopted_at) AS at FROM rules WHERE company_id = ? GROUP BY dept_norm", companyId),
  ]);
  return (deptNorm, grp, uses, now) => {
    let born = null;
    let other = null;
    for (const r of sent) {
      if (r.grp === grp && (born === null || r.born < born)) born = r.born;
      if (r.dept_norm === deptNorm && r.grp !== grp && r.last !== null && (other === null || r.last > other)) other = r.last;
    }
    const since = Math.max(born ?? 0, other ?? 0, ruled.find((r) => r.dept_norm === deptNorm)?.at ?? 0);
    return { stable: uses >= MEMBER.stablePhones && since > 0 && now - since >= MEMBER.stableDays * DAY, uses, since };
  };
}

const shared = (text, name) => ({ ...JSON.parse(text), company: name });

// AF 1.26 시작
// ---- [PLAZA-07][PLAZA-08][PLAZA-09][PLAZA-10] 회사별 광장 (결정 D79) ----
//
// A company's pay way is for the people who picked that company: they joined it by applying its setup, sending their
// own or saying so. Joining is a threshold, not a lock (anyone may pick a company), so it is kept high: one company
// per calendar, another only 30 days later, two per phone, and a phone reading too much gets the shifts only for the
// day. Everyone else, and apps before 1.26, still get shift times and their order, with "급여 방식 있음 · 확인 n명".

const MEMBER = { perPhone: 2, waitDays: 30, readsPerDay: 120, joinsPerNetworkDay: 30, stablePhones: 3, stableDays: 30 };
const CAL = /^[A-Za-z0-9_-]{1,24}$/;
/** What a calendar is called here: the app's own id for it, or "app" from an app that does not say. */
const calOf = (v) => (typeof v === "string" && CAL.test(v) ? v : "app");

/** The lines of describe() that are about shifts and their order, never pay. */
const SHIFT_LABELS = new Set(["근무", "요일별 근무", "근무표", "연장·휴무일 근무"]);
export const shiftLines = (lines) => lines.filter(([label]) => SHIFT_LABELS.has(label) || label.startsWith("순서 "));

/** A setup with its pay taken out, for a phone that did not join the company. */
export function shiftsOnly(setup) {
  const { pay, ...rest } = setup;
  return rest;
}

const memberReads = new Map();

/**
 * Who is asking about [companyId]: their install (X-Device) and whether they joined it. A member reading pay more than
 * [MEMBER.readsPerDay] times a day reads as anyone else until the next day ([limited]), counted in memory like the
 * public lists so a read costs no write.
 */
async function viewer(request, env, companyId) {
  const id = deviceId(request?.headers.get("x-device"));
  if (!id) return { dev: null, member: false };
  const dev = await deviceHash(id);
  const row = await one(env, "SELECT 1 AS ok FROM members WHERE device = ? AND company_id = ? AND left = 0 LIMIT 1", dev, companyId);
  if (!row) return { dev, member: false };
  return memberRead(dev);
}

/** A member's read of pay, counted in memory: past [MEMBER.readsPerDay] a day it reads as anyone else ([limited]). */
function memberRead(dev) {
  const day = Math.floor(Date.now() / DAY);
  const key = `${dev}|${day}`;
  const n = (memberReads.get(key) ?? 0) + 1;
  memberReads.set(key, n);
  if (memberReads.size > 20000) for (const k of memberReads.keys()) if (!k.endsWith(`|${day}`)) memberReads.delete(k);
  return n > MEMBER.readsPerDay ? { dev, member: false, limited: true } : { dev, member: true };
}

/**
 * [PLAZA-09] Whether [grp] of a department settled: at least [MEMBER.stablePhones] phones use it and nothing changed for
 * [MEMBER.stableDays] days (no other version sent, no version adopted, and it is itself that old). [since] is the last change.
 */
async function stableOf(env, companyId, deptNorm, grp, now) {
  const row = await one(env, `SELECT
      (SELECT COUNT(*) FROM uses WHERE company_id = ?1 AND grp = ?3) AS uses,
      (SELECT MIN(created_at) FROM reports WHERE company_id = ?1 AND grp = ?3) AS born,
      (SELECT MAX(created_at) FROM reports WHERE company_id = ?1 AND dept_norm = ?2 AND grp != ?3 AND status IN ('pending', 'adopted', 'replaced')) AS other,
      (SELECT MAX(adopted_at) FROM rules WHERE company_id = ?1 AND dept_norm = ?2) AS adopted`, companyId, deptNorm, grp);
  const since = Math.max(row.born ?? 0, row.other ?? 0, row.adopted ?? 0);
  return { stable: row.uses >= MEMBER.stablePhones && since > 0 && now - since >= MEMBER.stableDays * DAY, uses: row.uses, since };
}

/**
 * [PLAZA-07] Joins [companyId] for one calendar of the phone [dev]. A calendar keeps its company 30 days before another;
 * a phone is in two at most. What came of it, as the app shows it: joined, or why not and until when.
 */
async function joinCompany(env, dev, companyId, cal, now) {
  const rows = await all(env, "SELECT cal, company_id, since, left FROM members WHERE device = ?", dev);
  const here = rows.find((r) => r.cal === cal);
  const same = rows.find((r) => r.company_id === companyId && r.left === 0);
  if (same) return { joined: true, company: companyId, since: iso(same.since), changeAfter: iso(same.since + MEMBER.waitDays * DAY) };
  if (here && here.company_id !== companyId && now - here.since < MEMBER.waitDays * DAY) {
    return { joined: false, error: "member_wait", until: iso(here.since + MEMBER.waitDays * DAY) };
  }
  const others = new Set(rows.filter((r) => r.left === 0 && r.cal !== cal).map((r) => r.company_id));
  if (others.size >= MEMBER.perPhone) return { joined: false, error: "member_limit", limit: MEMBER.perPhone };
  const since = here && here.company_id === companyId ? here.since : now;
  await run(env, `INSERT INTO members (device, cal, company_id, since, left) VALUES (?, ?, ?, ?, 0)
    ON CONFLICT (device, cal) DO UPDATE SET company_id = excluded.company_id, since = excluded.since, left = 0`, dev, cal, companyId, since);
  return { joined: true, company: companyId, since: iso(since), changeAfter: iso(since + MEMBER.waitDays * DAY) };
}

/** [PLAZA-07] GET (header X-Device) the companies joined; POST {device, company, cal} joins; DELETE {device, company} leaves. */
async function members(request, env) {
  const now = Date.now();
  if (request.method === "GET") {
    const dev = await installOf(request);
    const rows = await all(env, `SELECT m.cal, m.company_id, m.since, m.left, c.name FROM members m JOIN companies c ON c.id = m.company_id
      WHERE m.device = ? ORDER BY m.since`, dev);
    return json({ members: rows.map((r) => ({ company: { id: r.company_id, name: r.name }, cal: r.cal, since: iso(r.since),
      changeAfter: iso(r.since + MEMBER.waitDays * DAY), ...(r.left ? { left: true } : {}) })), perPhone: MEMBER.perPhone, waitDays: MEMBER.waitDays });
  }
  if (request.method !== "POST" && request.method !== "DELETE") return json({ error: "not_found" }, 404);
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (typeof body.company !== "string" || !/^[A-Za-z0-9]{12}$/.test(body.company)) refuse("company", "company");
  const dev = await deviceHash(device);
  const c = await follow(env, body.company);
  if (!c || c.hidden) return json({ error: "not_found" }, 404);
  if (request.method === "DELETE") {
    // The 30 days stay: leaving and joining another company at once would get round them.
    await run(env, "UPDATE members SET left = 1 WHERE device = ? AND company_id = ?", dev, c.id);
    return json({ joined: false, company: c.id });
  }
  const net = await network(request, env, now);
  if (!(await allow(env, `join|${net.day}`, MEMBER.joinsPerNetworkDay, DAY, now))) return json({ error: "rate_limited" }, 429);
  const r = await joinCompany(env, dev, c.id, calOf(body.cal), now);
  // SP1 1.30 [SPD-08] With ?with=page (or "with": "page"), the company's page as the member now reads it: 이 설정 쓰기 and 이 급여
  // 방식 쓰기 go on without asking for the page again. An app before 1.30 asks for neither and gets the answer as before.
  const asked = new URL(request.url).searchParams.get("with") ?? body.with;
  if (r.joined && typeof asked === "string" && asked.split(",").includes("page")) {
    r.page = (await companyData(env, c.id, request, new Set(), memberRead(dev))).body;
  }
  return json(r, r.joined ? 200 : 409);
}

/**
 * [PLAZA-09] A company's room, for a phone that joined it: the version it uses (else its own, else the department's 대표),
 * the department's other versions with what differs and who stands behind them, and whether it settled. The app makes
 * 고친 점 cards from the files, so only members ever get them.
 */
async function room(request, env, companyId) {
  // SP1 1.30 [SPD-01] Where and who together; then the room's reads in two rounds (roomData).
  const [c, asked] = await Promise.all([follow(env, companyId), viewer(request, env, companyId)]);
  if (!c || c.hidden) return json({ error: "not_found" }, 404);
  const who = c.id === companyId ? asked : await viewer(request, env, c.id);
  if (!who.dev) refuse("device", "device");
  if (!who.member) return json({ error: who.limited ? "rate_limited" : "member" }, who.limited ? 429 : 403);
  const r = await roomData(env, c, who, Date.now());
  return json(r.body, r.status);
}

/** [PLAZA-09] The room of [c] for a member [who], as {status, body}: its lists together, then every version's state together. */
async function roomData(env, c, who, now) {
  const [{ adoptPoints }, live, mine, stable] = await Promise.all([
    settings(env),
    all(env, `SELECT grp, dept, dept_norm, setup, version, 'adopted' AS status, adopted_at AS at FROM rules
      WHERE company_id = ?1 AND withdrawn = 0 AND superseded = 0
    UNION ALL SELECT r.grp, r.dept, r.dept_norm, r.setup, NULL, 'pending', MAX(r.created_at) FROM reports r
      WHERE r.company_id = ?1 AND r.status = 'pending'
        AND NOT EXISTS (SELECT 1 FROM reports x WHERE x.company_id = r.company_id AND x.grp = r.grp AND x.status = 'rejected')
        AND NOT EXISTS (SELECT 1 FROM rules w WHERE w.company_id = r.company_id AND w.grp = r.grp AND (w.withdrawn = 1 OR w.superseded = 0))
      GROUP BY r.grp`, c.id),
    one(env, `SELECT grp FROM uses WHERE company_id = ?1 AND device = ?2
    UNION ALL SELECT grp FROM reports WHERE company_id = ?1 AND device = ?2 AND status IN ('pending', 'adopted') LIMIT 1`, c.id, who.dev),
    stableFacts(env, c.id),
  ]);
  if (!live.length) return { status: 200, body: { company: { id: c.id, name: c.name }, member: true, base: null, others: [], stable: false } };
  // Before 1.30 one version after another (for … await state): nine rounds for a room of four versions.
  const facts = await readStates(env, live.map((v) => ({ company: c.id, grp: v.grp })));
  const states = new Map(live.map((v) => [v.grp, facts.state(c.id, v.grp, adoptPoints, v.status === "adopted")]));
  const best = [...live].sort((a, b) => (b.status === "adopted") - (a.status === "adopted") || states.get(b.grp).support - states.get(a.grp).support)[0];
  const base = live.find((v) => v.grp === mine?.grp) ?? best;
  const baseSetup = JSON.parse(base.setup);
  const st = stable(base.dept_norm, base.grp, facts.uses(c.id, base.grp), now);
  const view = (v) => ({ ...states.get(v.grp), dept: v.dept, version: v.version, at: iso(v.at), setup: shared(v.setup, c.name) });
  const others = live.filter((v) => v.grp !== base.grp && v.dept_norm === base.dept_norm).map((v) => ({
    ...view(v), changes: diffLines(describe(baseSetup), describe(JSON.parse(v.setup))),
  })).sort((a, b) => b.persons - a.persons || b.support - a.support);
  return { status: 200, body: {
    company: { id: c.id, name: c.name },
    member: true,
    base: { ...view(base), mine: mine?.grp === base.grp, first: base.status === "pending" && states.get(base.grp).senders <= 1 },
    others,
    stable: st.stable,
    stableRule: { phones: MEMBER.stablePhones, days: MEMBER.stableDays, uses: st.uses, since: st.since ? iso(st.since) : null },
  } };
}

/**
 * [PLAZA-10] A company's or department's first setup with its pay is new information (결정 D79): its sender gets the pass
 * of [firstMonths], once a day and [firstCapYear] times a year at most, besides the plaza's own cap. Past them it waits as
 * 'owed' for the developer. A setup with only shifts, the same setup again or a rule for a whole job earns nothing.
 */
async function newInfo(env, c, r, reportId, dev, now) {
  if (!r.setup.pay || c.kind === "topic") return null;
  const before = await one(env, `SELECT
      EXISTS (SELECT 1 FROM reports WHERE company_id = ?1 AND dept_norm = ?2 AND id != ?3 AND instr(setup, '"pay":') > 0
        AND status IN ('pending', 'adopted', 'replaced', 'rejected')) AS reported,
      EXISTS (SELECT 1 FROM rules WHERE company_id = ?1 AND dept_norm = ?2) AS ruled`, c.id, r.deptNorm, reportId);
  if (before.reported || before.ruled) return null;
  const { firstMonths, firstCapYear, rewardCapMonths, rewardCapDays, autoReward } = await settings(env);
  if (firstMonths <= 0) return null;
  const id = `w${reportId}`;
  await run(env, `INSERT OR IGNORE INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at)
    VALUES (?, ?, 'first', ?, ?, 'owed', '새 정보', ?, NULL)`, id, reportId, dev, firstMonths, now);
  const got = await one(env, `SELECT
      (SELECT COUNT(*) FROM rewards WHERE device = ?1 AND rule_id = 'first' AND status = 'paid' AND paid_at > ?2) AS today,
      (SELECT COUNT(*) FROM rewards WHERE device = ?1 AND rule_id = 'first' AND status = 'paid' AND paid_at > ?3) AS year`,
  dev, now - DAY, now - 365 * DAY);
  if (autoReward && got.today < 1 && got.year < firstCapYear) await payEach(env, [{ id, device: dev, months: firstMonths }], { rewardCapMonths, rewardCapDays }, now);
  const w = await one(env, "SELECT months, status FROM rewards WHERE id = ?", id);
  return { months: w.months, status: w.status };
}

/**
 * [PLAZA-09] 광장 소식 for members: other versions sent in their companies since [since] and still being checked, not one
 * they use or sent. One adopted in place of theirs is the "changed" news already.
 */
function roomNews(env, dev, since) {
  return stmt(env, `SELECT m.company_id, c.name, COUNT(DISTINCT r.grp) AS n, MAX(r.created_at) AS at FROM members m
      JOIN reports r ON r.company_id = m.company_id AND r.device != m.device AND r.status = 'pending' AND r.created_at > ?2
      JOIN companies c ON c.id = m.company_id
    WHERE m.device = ?1 AND m.left = 0
      AND r.grp NOT IN (SELECT grp FROM uses WHERE device = ?1 AND company_id = m.company_id)
      AND r.grp NOT IN (SELECT grp FROM reports WHERE device = ?1 AND company_id = m.company_id)
    GROUP BY m.company_id ORDER BY at DESC LIMIT 10`, dev, since);
}
// AF 1.26 끝

// AI 1.27 시작
// ---- [PLAZA-30] 명세서와 맞춰 봤어요, with what it rests on ----
//
// The app ticks it only when this calendar matched a pay slip (결정 AI-D1~D4): the month and whether the month the app worked
// out came within the match's tolerance. No amount, no total and no photo comes with it (D68). An older app's tick has
// nothing behind it, so it counts as a plain report or 맞았어요 and is kept as a claim the developer can see.

const PROOF_MONTH = /^(\d{4})-(0[1-9]|1[0-2])$/;
const PROOF_MONTHS = 13;

/** The proof an app sent, or null; a malformed one is refused, as anything else malformed is. */
export function readProof(raw, now = Date.now()) {
  if (!given(raw)) return null;
  object(raw, "proof");
  const m = typeof raw.month === "string" ? raw.month.match(PROOF_MONTH) : null;
  if (!m || typeof raw.match !== "boolean") refuse("proof", "proof");
  const d = new Date(now + 9 * HOUR);
  const back = (d.getUTCFullYear() - Number(m[1])) * 12 + (d.getUTCMonth() + 1 - Number(m[2]));
  if (back < 0 || back > PROOF_MONTHS) refuse("proof", "proof");
  return { month: raw.month, match: raw.match };
}

/** Whether [proof] makes a 명세서 tick count: a month that matched. */
export const proofCounts = (proof) => Boolean(proof?.match);

const proofText = (proof) => (proof ? `${proof.month}|${proof.match ? "match" : "gap"}` : null);

// ---- [PLAZA-32][PLAZA-33] 회사 정보 광장 and the plazas of each kind of work ----
//
// The talk of a company's people among themselves, and of the kinds of work with no company (현장, 가게, 알바, 투자), in the
// posts table that rule talks use (결정 AI-D5): grp 'room', so 신고 3번 가림, the developer's 가리기·지우기·30일 막기,
// 이 사람 글 안 보기, 내 것 지우기 and 광장 소식 are the same. A company's room is for the phones that joined it (AI-D6),
// counted like its pay reads; a kind's room for any phone with the app, held to the public reads an hour (AI-D7).

export const ROOM = "room";
export const STYLE_ROOMS = { "style-field": "현장 광장", "style-shop": "가게 광장", "style-hourly": "알바 광장", "style-invest": "투자 이야기" };
const ROOM_LIMITS = { perDay: 10, perNetworkDay: 30, shown: 100 };
/** [PLAZA-33] What the 투자 이야기 does not take: 리딩방, promised returns, moves to a chat room (결정 AI-D8). */
const INVEST_ADVICE = /리딩\s*방|리딩해|수익\s*(보장|확정)|원금\s*보장|무조건\s*(올라|오릅|상승|수익)|(카톡|카카오톡|텔레그램|오픈\s*채팅|단톡)\s*방?\s*(으로|에|들어|입장|초대)|급등\s*주\s*(추천|알려)|종목\s*(추천|찍어|알려)\s*(드|줄|해)/;
export const ROOM_PATH = /^\/api\/companies\/([A-Za-z0-9]{12}|style-(?:field|shop|hourly|invest))\/setups\/room\/posts$/;

/** The place of a room: a company (not hidden) or a kind of work's plaza; null when there is none. */
async function roomPlace(env, id) {
  if (Object.hasOwn(STYLE_ROOMS, id)) return { id, name: STYLE_ROOMS[id], style: true };
  const c = await follow(env, id);
  return !c || c.hidden || c.kind === "topic" ? null : c;
}

/** Who may read [place]'s room: a company's members (their reads counted as viewer() counts them), anyone with the app for a kind's plaza. */
async function roomViewer(request, env, place) {
  if (place.style) {
    const id = deviceId(request.headers.get("x-device"));
    return id ? { dev: await deviceHash(id), member: true } : { dev: null, member: false };
  }
  return viewer(request, env, place.id);
}

/** A post in a room: the same words as any post (no amounts, contacts or names), a reply under the same room's post. */
async function roomPost(request, env, body) {
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (typeof body.company !== "string") refuse("company", "company");
  const place = await roomPlace(env, body.company);
  if (!place) return json({ error: "not_found" }, 404);
  const text = str(body.body, LIMITS.post, "body", { empty: false, lines: true, code: "body" }).trim();
  if (text.length < 2) refuse("body", "body");
  const why = personal(text);
  if (why) refuse(why, "body");
  if (place.id === "style-invest" && INVEST_ADVICE.test(text)) refuse("invest_advice", "body");
  const now = Date.now();
  const dev = await deviceHash(device);
  if (!place.style && !(await one(env, "SELECT 1 AS ok FROM members WHERE device = ? AND company_id = ? AND left = 0", dev, place.id))) {
    return json({ error: "member" }, 403);
  }
  const muted = await one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", dev, now);
  if (muted) return json({ error: "muted", until: iso(muted.until) }, 403);
  const net = await network(request, env, now);
  if (!(await allow(env, `room|${dev}`, ROOM_LIMITS.perDay, DAY, now)) ||
      !(await allow(env, `room|${net.day}`, ROOM_LIMITS.perNetworkDay, DAY, now))) {
    return json({ error: "rate_limited" }, 429);
  }
  let parent = null;
  if (given(body.parent)) {
    const top = typeof body.parent === "string" ? await one(env, "SELECT id, parent FROM posts WHERE id = ? AND company_id = ? AND grp = ?",
      body.parent, place.id, ROOM) : null;
    if (!top) refuse("parent", "parent");
    parent = top.parent ?? top.id;
  }
  const kind = given(body.kind) ? oneOf(body.kind, ["talk", "question"], "kind") : "talk";
  const id = `p${randomId(15)}`;
  await run(env, `INSERT INTO posts (id, company_id, grp, device, body, status, flags, kept, hide_reason, created_at, hidden_at,
      parent, kind, change, report_id, agree, disagree)
    VALUES (?, ?, ?, ?, ?, 'shown', 0, 0, NULL, ?, NULL, ?, ?, NULL, NULL, 0, 0)`, id, place.id, ROOM, dev, text, now, parent, kind);
  return json({ id, at: iso(now) }, 201);
}

/** [PLAZA-35] 광장 소식: new posts in the rooms of the companies this phone joined, not its own, once a day in the digest. */
function roomPostNews(env, dev, since) {
  return stmt(env, `SELECT m.company_id, c.name, COUNT(*) AS n, MAX(p.created_at) AS at FROM members m
      JOIN posts p ON p.company_id = m.company_id AND p.grp = 'room' AND p.status = 'shown' AND p.device != m.device AND p.created_at > ?2
      JOIN companies c ON c.id = m.company_id
    WHERE m.device = ?1 AND m.left = 0 GROUP BY m.company_id ORDER BY at DESC LIMIT 10`, dev, since);
}
// AI 1.27 끝
// AH 1.27 시작
// ---- [PLAZA-14][PLAZA-15] 대표 주기와 맞아요·달라요 (계획 1.27-AH, AH-D4·D7·D8·D9) ----
//
// Each department (사업장) of a company has one 대표 주기 to offer first: of its versions that too many people did not
// call wrong, the adopted one, else the one with the most 지지 (support(), the score the 대표 판 is already decided by).
// A company's first setup is its 대표 주기 at once (사용자 2026-10-25 "대표주기가 됩니다!"); the reward still comes only
// with adoption or new information, so a made-up company earns nothing. Too many 달라요 take a version out of the lead,
// and the next trusted one leads instead.

/** At least [votes] votes (맞아요 and 달라요) and more than [share] percent 달라요: the version leads no longer (AH-D7). */
export const DOUBT = { votes: 5, share: 40 };
/** Where a 달라요 says it is wrong (AH-D9): the order, the times, the pay items. Older apps send wrong, old or name. */
export const WHY_PARTS = ["order", "time", "pay"];
export const VOTE_WHY = ["wrong", "name", "old", ...WHY_PARTS];

/** [PLAZA-15] Whether a score (persons who stand behind it, [bad] who said 달라요) has too many 달라요 to lead. */
export const doubted = (s) => s.persons + s.bad >= DOUBT.votes && s.bad * 100 > DOUBT.share * (s.persons + s.bad);

/**
 * [PLAZA-14] The 대표 주기 of one department among [versions] ({id, adopted, support, doubted}, in the order they came):
 * not doubted, adopted first, then the most 지지; a tie keeps the earlier. Null when every one is doubted.
 */
export function leadOf(versions) {
  let best = null;
  for (const v of versions) {
    if (v.doubted) continue;
    if (!best || (v.adopted && !best.adopted) || (v.adopted === best.adopted && v.support > best.support)) best = v;
  }
  return best?.id ?? null;
}

/** [PLAZA-15] The 맞아요·달라요 on one version: counts, 달라요 by where, and this phone's own vote when it gave one. */
async function opinions(env, companyId, grp, dev) {
  const rows = await all(env, "SELECT device, ok, why FROM votes WHERE company_id = ? AND grp = ?", companyId, grp);
  const why = { order: 0, time: 0, pay: 0, other: 0 };
  let ok = 0;
  let bad = 0;
  let mine = null;
  for (const r of rows) {
    if (r.ok) ok++;
    else {
      bad++;
      why[WHY_PARTS.includes(r.why) ? r.why : "other"]++;
    }
    if (dev && r.device === dev) mine = r.ok ? "ok" : (r.why ?? "wrong");
  }
  return { ok, bad, why, ...(mine ? { mine } : {}) };
}

/**
 * [PLAZA-14][PLAZA-15] company()'s setups marked: each with its opinions, doubted ones, and each department's lead.
 * SP1 1.30 [SPD-01] [opinionOf] answers from the votes company() already read for its states (opinions() read them again).
 */
function markLeads(setups, opinionOf) {
  const byDept = new Map();
  for (const s of setups) {
    s.opinion = opinionOf(s.id);
    if (doubted(s)) {
      s.doubted = true;
      // A version people say is wrong has not settled, whatever its age.
      delete s.stable;
    }
    const k = normName(s.dept ?? "");
    byDept.set(k, [...(byDept.get(k) ?? []), s]);
  }
  for (const list of byDept.values()) {
    const id = leadOf(list.map((s) => ({ id: s.id, adopted: s.status === "adopted", support: s.support, doubted: Boolean(s.doubted) })));
    for (const s of list) if (s.id === id) s.lead = true;
  }
}

/**
 * [PLAZA-14] A company's 대표 주기 per department, lighter than company(): for the search's one line. Each has the shifts
 * and their order (never pay values), whether a pay way is there, and who stands behind it.
 */
async function leads(env, companyId) {
  const rows = await all(env, `SELECT grp, dept, dept_norm, setup, 1 AS adopted FROM rules WHERE company_id = ?1 AND withdrawn = 0 AND superseded = 0
    UNION ALL SELECT r.grp, r.dept, r.dept_norm, r.setup, 0 FROM reports r WHERE r.company_id = ?1 AND r.status = 'pending'
      AND NOT EXISTS (SELECT 1 FROM reports x WHERE x.company_id = r.company_id AND x.grp = r.grp AND x.status = 'rejected')
      AND NOT EXISTS (SELECT 1 FROM rules w WHERE w.company_id = r.company_id AND w.grp = r.grp)
    GROUP BY r.grp`, companyId);
  const versions = [];
  for (const r of rows) {
    const s = await score(env, companyId, r.grp);
    const extra = await one(env, `SELECT (SELECT COUNT(*) FROM uses WHERE company_id = ?1 AND grp = ?2) AS uses,
      (SELECT COUNT(*) FROM reports WHERE company_id = ?1 AND grp = ?2 AND status IN ('pending', 'adopted') AND verified = 1) AS checked`, companyId, r.grp);
    versions.push({ id: r.grp, dept: r.dept, deptNorm: r.dept_norm, adopted: r.adopted === 1, setup: JSON.parse(r.setup),
      persons: s.persons, bad: s.bad, uses: extra.uses, doubted: doubted(s),
      support: support({ uses: extra.uses, ok: s.persons, bad: s.bad, verified: extra.checked }) });
  }
  const out = [];
  for (const k of new Set(versions.map((v) => v.deptNorm))) {
    const list = versions.filter((v) => v.deptNorm === k);
    const id = leadOf(list);
    const v = list.find((x) => x.id === id) ?? list[0];
    out.push({ ...v, doubted: id === null });
  }
  return out;
}

/** [PLAZA-14] The search's line for a company: its main 대표 주기 (회사 전체 first) and how many phones use the company. */
async function searchLead(env, companyId) {
  const all_ = await leads(env, companyId);
  const main = all_.find((l) => l.deptNorm === "") ?? [...all_].sort((a, b) => b.persons - a.persons)[0];
  const users = await one(env, `SELECT COUNT(*) AS n FROM (SELECT device FROM uses WHERE company_id = ?1
    UNION SELECT device FROM reports WHERE company_id = ?1 AND status IN ('pending', 'adopted'))`, companyId);
  if (!main) return { users: users.n };
  const offs = main.setup.shiftTypes.filter((t) => t.off).map((t) => t.name);
  return {
    users: users.n,
    sites: all_.length,
    lead: { setup: main.id, dept: main.dept, adopted: main.adopted, steps: main.setup.rotation?.steps ?? null, weekly: main.setup.weekly ?? null,
      roster: main.setup.roster === true, offs, pay: main.setup.pay !== undefined, ok: main.persons, bad: main.bad,
      ...(main.doubted ? { doubted: true } : {}), ...payWayOf(all_, main) },
  };
}

// AP 1.29 — [PAYF-08] 급여 방식 찾기: the company's pay way even when its main 대표 주기 has none (the pay came with another
// 사업장's). Only which setup, its department and how many said 맞아요 (결정 D79: never its values). Older apps read lead.pay only.
export function payWayOf(all_, main) {
  if (!main || main.setup.pay !== undefined) return {};
  const other = all_.filter((l) => l !== main && l.setup.pay !== undefined && !l.doubted).sort((a, b) => b.persons - a.persons)[0];
  return other ? { payWay: { setup: other.id, dept: other.dept, ok: other.persons } } : {};
}
// AP 1.29 끝

/** [PLAZA-15] Whether the department's rule in use has too many 달라요: then another version may take over by its own right. */
async function activeDoubted(env, companyId, active) {
  return Boolean(active) && doubted(await score(env, companyId, active.grp));
}

/** [PLAZA-15] After a 달라요 on a department's rule, the versions waiting there may now take its place (settle decides). */
async function settleDept(env, companyId, deptNorm, now) {
  const waiting = await all(env, "SELECT DISTINCT grp FROM reports WHERE company_id = ? AND dept_norm = ? AND status = 'pending'", companyId, deptNorm);
  for (const w of waiting) await settle(env, companyId, w.grp, now);
}
// AH 1.27 끝

// AS 1.29 시작
// [SRCH-01..04] 1.29 묶음 AS (사용자 2026-10-27): "광장에 대표판이 있는데 회사 검색 시 뜨지 않는다 … 안 뜨는 게 아니라 늦게 떠서
// 안 뜨는 것처럼 보인다". The search read each listed company's 대표 주기 one company and one version after another
// (searchLead: about five reads per company, only the first five), so an answer took long enough for the app to give up on
// it (the app's 2.5 s), and a company below the fifth came without its 대표 주기. Now every listed company's line is made
// from five reads in all, sent together; a name is found by its Korean reading too; a search keeps a short edge copy; and
// the most used companies are a list of their own for the app to keep, so a first user sees them before any answer.

/** [SRCH-03] How long a search's answer may be kept at the edge (and by anything between): short, as a new setup should show. */
export const SEARCH_EDGE_SECONDS = 30;
/** [SRCH-04] The most used companies change slowly. */
export const TOP_EDGE_SECONDS = 600;
const SEARCH_CACHE = { "cache-control": `public, max-age=${SEARCH_EDGE_SECONDS}, stale-while-revalidate=300` };
const TOP_CACHE = { "cache-control": `public, max-age=${TOP_EDGE_SECONDS}, stale-while-revalidate=3600` };
/** [SRCH-04] How many companies the list of the most used has at most (about 300 bytes each). */
export const TOP_MAX = 60;

const namesFilled = new WeakMap();
const NAMES_FILLED_MS = 5 * 60 * 1000;

const LETTERS = {
  a: "에이", b: "비", c: "씨", d: "디", e: "이", f: "에프", g: "지", h: "에이치", i: "아이", j: "제이", k: "케이", l: "엘", m: "엠",
  n: "엔", o: "오", p: "피", q: "큐", r: "알", s: "에스", t: "티", u: "유", v: "브이", w: "더블유", x: "엑스", y: "와이", z: "제트",
};
/** English words read as a word, not letter by letter: the same list as the app's core CompanyMatch. */
const WORDS = [["hyundai", "현대"], ["samsung", "삼성"], ["hanwha", "한화"], ["lotte", "롯데"], ["doosan", "두산"], ["posco", "포스코"],
  ["emart", "이마트"], ["korail", "코레일"], ["kepco", "한전"], ["hanjin", "한진"], ["oil", "오일"], ["steel", "스틸"], ["power", "파워"],
  ["chemical", "케미칼"], ["logis", "로지스"], ["tech", "테크"], ["eps", "이피에스"]];
const WORD_OF = new Map(WORDS);

function readWord(w) {
  if (!w) return "";
  if (WORD_OF.has(w)) return WORD_OF.get(w);
  for (const [en, ko] of WORDS) {
    const at = w.indexOf(en);
    if (at >= 0 && en.length >= 3) return readWord(w.slice(0, at)) + ko + readWord(w.slice(at + en.length));
  }
  return [...w].map((c) => LETTERS[c] ?? c).join("");
}

/**
 * [SRCH-02] How a name is said in Korean letters, as normName leaves it otherwise: GS칼텍스 → 지에스칼텍스, S-OIL → 에스오일.
 * The app's core CompanyMatch.spoken does the same (AH-I1), so the phone and the server find the same companies.
 */
export function spoken(s) {
  return normName(s).replace(/[a-z]+/g, readWord);
}

/** [SRCH-01] The search's 대표 주기 line for each of [companies], added to it (see searchLeads). */
async function withLeads(env, companies) {
  const lines = await searchLeads(env, companies.map((c) => c.id));
  for (const c of companies) Object.assign(c, lines.get(c.id) ?? { users: 0 });
}

/**
 * [SRCH-01] What searchLead says for one company, for many at once: the versions, what was sent and voted on them, who uses
 * them and the company's users, five reads sent together whatever the number of companies or versions. The same answer as
 * searchLead (a test holds them together); leadOf, doubted, support and tally decide as before.
 */
export async function searchLeads(env, ids, every = false) {
  const out = new Map();
  if (!ids.length) return out;
  const marks = ids.map((_, i) => `?${i + 1}`).join(", ");
  // SP1 1.30 [SPD-06] [every]: the whole list (/api/companies/all) reads every company's rows at once, not 1,000 marks (D1 binds 100).
  const of = (column) => (every ? "1 = 1" : `${column} IN (${marks})`);
  const values = every ? [] : ids;
  const [rows, sent, votes, used, users] = await Promise.all([
    all(env, `SELECT company_id, grp, dept, dept_norm, setup, 1 AS adopted FROM rules WHERE ${of("company_id")} AND withdrawn = 0 AND superseded = 0
      UNION ALL SELECT r.company_id, r.grp, r.dept, r.dept_norm, r.setup, 0 FROM reports r WHERE ${of("r.company_id")} AND r.status = 'pending'
        AND NOT EXISTS (SELECT 1 FROM reports x WHERE x.company_id = r.company_id AND x.grp = r.grp AND x.status = 'rejected')
        AND NOT EXISTS (SELECT 1 FROM rules w WHERE w.company_id = r.company_id AND w.grp = r.grp)
      GROUP BY r.company_id, r.grp`, ...values),
    all(env, `SELECT company_id, grp, device, points, cluster, verified FROM reports WHERE ${of("company_id")} AND status IN ('pending', 'adopted')`, ...values),
    all(env, `SELECT company_id, grp, device, ok, verified, cluster FROM votes WHERE ${of("company_id")}`, ...values),
    all(env, `SELECT company_id, grp, COUNT(*) AS n FROM uses WHERE ${of("company_id")} GROUP BY company_id, grp`, ...values),
    all(env, `SELECT company_id, COUNT(*) AS n FROM (SELECT company_id, device FROM uses WHERE ${of("company_id")}
      UNION SELECT company_id, device FROM reports WHERE ${of("company_id")} AND status IN ('pending', 'adopted')) GROUP BY company_id`, ...values),
  ]);
  const at = (c, g) => `${c}\u0001${g}`;
  const backing = new Map();
  const doubters = new Map();
  const checked = new Map();
  const add = (m, k, v) => m.set(k, [...(m.get(k) ?? []), v]);
  for (const r of sent) {
    add(backing, at(r.company_id, r.grp), { device: r.device, points: r.points, cluster: r.cluster });
    if (r.verified === 1) checked.set(at(r.company_id, r.grp), (checked.get(at(r.company_id, r.grp)) ?? 0) + 1);
  }
  for (const v of votes) {
    if (v.ok === 1) add(backing, at(v.company_id, v.grp), { device: v.device, points: v.verified === 1 ? 2 : 1, cluster: v.cluster });
    else add(doubters, at(v.company_id, v.grp), v.device);
  }
  const uses = new Map(used.map((u) => [at(u.company_id, u.grp), u.n]));
  const userCount = new Map(users.map((u) => [u.company_id, u.n]));
  const byCompany = new Map();
  for (const r of rows) {
    const k = at(r.company_id, r.grp);
    const s = tally(backing.get(k) ?? [], doubters.get(k) ?? []);
    const n = uses.get(k) ?? 0;
    add(byCompany, r.company_id, { id: r.grp, dept: r.dept, deptNorm: r.dept_norm, adopted: r.adopted === 1, setup: JSON.parse(r.setup),
      persons: s.persons, bad: s.bad, uses: n, doubted: doubted(s),
      support: support({ uses: n, ok: s.persons, bad: s.bad, verified: checked.get(k) ?? 0 }) });
  }
  for (const id of ids) {
    const versions = byCompany.get(id) ?? [];
    const leadsOf = [];
    for (const k of new Set(versions.map((v) => v.deptNorm))) {
      const list = versions.filter((v) => v.deptNorm === k);
      const lead = leadOf(list);
      const v = list.find((x) => x.id === lead) ?? list[0];
      leadsOf.push({ ...v, doubted: lead === null });
    }
    out.set(id, leadLine(leadsOf, userCount.get(id) ?? 0));
  }
  return out;
}

/** [PLAZA-14] searchLead's answer from a company's department leads and its users. */
function leadLine(all_, users) {
  const main = all_.find((l) => l.deptNorm === "") ?? [...all_].sort((a, b) => b.persons - a.persons)[0];
  if (!main) return { users };
  const offs = main.setup.shiftTypes.filter((t) => t.off).map((t) => t.name);
  return {
    users,
    sites: all_.length,
    lead: { setup: main.id, dept: main.dept, adopted: main.adopted, steps: main.setup.rotation?.steps ?? null, weekly: main.setup.weekly ?? null,
      roster: main.setup.roster === true, offs, pay: main.setup.pay !== undefined, ok: main.persons, bad: main.bad,
      ...(main.doubted ? { doubted: true } : {}), ...payWayOf(all_, main) },
  };
}

/** [SRCH-01] The one-company way searchLeads replaced, kept for the test that holds the two to the same answer. */
export const searchLeadOne = (env, companyId) => searchLead(env, companyId);

const topMemo = new WeakMap();

/**
 * [SRCH-04] GET /api/companies/top: the companies the most phones use or sent (each phone once), with their 대표 주기 line as the
 * search gives it, at most [TOP_MAX] (`?n=` fewer). Made at most every ten minutes per Worker and kept at the edge as long:
 * the app keeps it after its first month is drawn (Wi-Fi, not 절전), so the companies most people look for show on the
 * first letter, before any answer. Nothing about a phone is in it.
 */
async function topCompanies(env, url) {
  const n = Math.min(TOP_MAX, Math.max(1, Number.parseInt(url.searchParams.get("n") ?? `${TOP_MAX}`, 10) || TOP_MAX));
  const key = spDbKey(env);
  const kept = topMemo.get(key);
  if (kept && Date.now() - kept.at < TOP_EDGE_SECONDS * 1000) return json({ ...kept.body, companies: kept.body.companies.slice(0, n) }, 200, TOP_CACHE);
  const rows = await all(env, `SELECT c.id, c.name, u.n,
      (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS adopted,
      (SELECT COUNT(DISTINCT p.grp) FROM reports p WHERE p.company_id = c.id AND p.status = 'pending') AS pending
    FROM (SELECT company_id, COUNT(*) AS n FROM (SELECT company_id, device FROM uses
      UNION SELECT company_id, device FROM reports WHERE status IN ('pending', 'adopted')) GROUP BY company_id ORDER BY n DESC LIMIT ?) u
    JOIN companies c ON c.id = u.company_id
    WHERE c.hidden = 0 AND c.merged_into IS NULL AND c.kind != 'topic'
    ORDER BY u.n DESC, c.name`, TOP_MAX * 2);
  const companies = rows.filter((c) => c.adopted > 0 || c.pending > 0).slice(0, TOP_MAX)
    .map((c) => ({ id: c.id, name: c.name, alias: null, adopted: c.adopted, pending: c.pending, ...(c.adopted === 0 ? { first: true } : {}) }));
  await withLeads(env, companies);
  const body = { companies, at: new Date().toISOString() };
  topMemo.set(key, { at: Date.now(), body });
  return json({ ...body, companies: companies.slice(0, n) }, 200, TOP_CACHE);
}

/**
 * [SRCH-03] A public GET answered from the edge's copy (Cloudflare's Cache API) when one is there and still fresh, else made
 * and kept for [seconds]. Only answers that are the same for everyone come here (no X-Device). Off where there is no Cache
 * API (the tests on Node, a local run), and a cache that fails never fails the answer.
 */
async function edgeCached(request, ctx, seconds, make) {
  const cache = globalThis.caches?.default;
  if (!cache) return make();
  const url = new URL(request.url);
  // One copy for every way of writing the same search.
  const q = url.searchParams.get("q");
  if (q !== null) url.searchParams.set("q", isChoseong(q) ? q.replace(/\s/g, "") : normName(q));
  url.searchParams.sort();
  const key = new Request(url.toString(), { method: "GET" });
  try {
    // SP1 1.30 [SPD-04] After its own write the app asks past the copy (Cache-Control: no-cache); the fresh answer is kept again.
    const hit = spNoCache(request) ? null : await cache.match(key);
    if (hit) return hit;
  } catch {
    // Read afresh.
  }
  const res = await make();
  if (res.status === 200) {
    const copy = res.clone();
    const put = cache.put(key, copy).catch(() => {});
    if (ctx?.waitUntil) ctx.waitUntil(put);
    else await put;
  }
  return res;
}
// AS 1.29 끝


async function state(env, companyId, grp, threshold, adopted) {
  // AS 1.29 [SRCH-05] Its five reads go together.
  const [s, posts, who, names, span] = await Promise.all([
    score(env, companyId, grp),
    one(env, "SELECT COUNT(*) AS n FROM posts WHERE company_id = ? AND grp = ? AND status = 'shown'", companyId, grp),
    // [PLZ-08] Who stands behind it: how many sent it, how many checked it on a pay slip, how many use it,
    // and the names they chose to be shown by (no one else's).
    one(env, `SELECT
        (SELECT COUNT(*) FROM uses WHERE company_id = ?1 AND grp = ?2) AS uses,
        (SELECT COUNT(*) FROM reports WHERE company_id = ?1 AND grp = ?2 AND status IN ('pending', 'adopted')) AS senders,
        (SELECT COUNT(*) FROM reports WHERE company_id = ?1 AND grp = ?2 AND status IN ('pending', 'adopted') AND verified = 1)
          + (SELECT COUNT(*) FROM votes WHERE company_id = ?1 AND grp = ?2 AND ok = 1 AND verified = 1) AS checked,
        (SELECT COUNT(*) FROM reports WHERE company_id = ?1 AND grp = ?2 AND status IN ('pending', 'adopted') AND verified = 1) AS sentChecked`, companyId, grp),
    all(env, `SELECT n.nick FROM reports r JOIN nicks n ON n.device = r.device
        WHERE r.company_id = ? AND r.grp = ? AND r.status IN ('pending', 'adopted') ORDER BY r.created_at LIMIT 5`, companyId, grp).then((l) => l.map((n) => n.nick)),
    // [COMM-11] N7 두 달 이상 확인됨: pay-slip checks from at least two networks in two different months.
    one(env, `SELECT COUNT(DISTINCT cluster) AS nets, COUNT(DISTINCT strftime('%Y-%m', at / 1000, 'unixepoch')) AS months FROM (
        SELECT cluster, created_at AS at FROM reports WHERE company_id = ?1 AND grp = ?2 AND status IN ('pending', 'adopted') AND verified = 1
        UNION ALL SELECT cluster, at FROM votes WHERE company_id = ?1 AND grp = ?2 AND ok = 1 AND verified = 1)`, companyId, grp),
  ]);
  return { id: grp, status: adopted ? "adopted" : "pending", points: s.points, persons: s.persons, bad: s.bad, threshold,
    ...trust(s, threshold), posts: posts.n, uses: who.uses, senders: who.senders, checked: who.checked, by: names,
    // [PLAZA-05] What decides the 대표 판 of a department (D74): phones using it, people behind it, less 달라요.
    support: support({ uses: who.uses, ok: s.persons, bad: s.bad, verified: who.sentChecked }),
    ...(span.nets >= 2 && span.months >= 2 ? { checkedLong: true } : {}) };
}

// SP1 1.30 시작
// [SPD-01] 1.30 묶음 SP1: state() for many versions at once. The plaza's lists asked state() one version after another (규칙 광장
// 투표 중: 20 versions × (대표 report + five reads) = 142 statements in 45 rounds, 1.5 to 7 seconds with D1 one region away).
// readStates reads the same rows for every (company, grp) pair in four statements sent together and answers as state(),
// opinions() and stableOf()'s uses do (a test holds them to the same answers).

const pairKey = (companyId, grp) => `${companyId}\u0001${grp}`;

/**
 * [SPD-01] What state() reads, for every pair in [pairs] ({company, grp}) at once: the reports behind each version (with the
 * names their senders chose), its votes, its shown posts and its users. Returns state(), opinions() and uses() over those rows.
 */
async function readStates(env, pairs) {
  const read = await Promise.all(spPairChunks(pairs).map((chunk) => {
    const { cs, gs, values } = spInMarks(chunk);
    const where = `company_id IN (${cs}) AND grp IN (${gs})`;
    return Promise.all([
      all(env, `SELECT r.company_id, r.grp, r.device, r.points, r.cluster, r.verified, r.created_at, n.nick FROM reports r
          LEFT JOIN nicks n ON n.device = r.device
        WHERE r.company_id IN (${cs}) AND r.grp IN (${gs}) AND r.status IN ('pending', 'adopted') ORDER BY r.created_at, r.rowid`, ...values),
      all(env, `SELECT company_id, grp, device, ok, verified, why, cluster, at FROM votes WHERE ${where}`, ...values),
      all(env, `SELECT company_id, grp, COUNT(*) AS n FROM posts WHERE ${where} AND status = 'shown' GROUP BY company_id, grp`, ...values),
      all(env, `SELECT company_id, grp, COUNT(*) AS n FROM uses WHERE ${where} GROUP BY company_id, grp`, ...values),
    ]);
  }));
  const sent = new Map();
  const voted = new Map();
  const posts = new Map();
  const uses = new Map();
  const add = (m, k, v) => (m.has(k) ? m.get(k).push(v) : m.set(k, [v]));
  for (const [r, v, p, u] of read) {
    for (const x of r) add(sent, pairKey(x.company_id, x.grp), x);
    for (const x of v) add(voted, pairKey(x.company_id, x.grp), x);
    for (const x of p) posts.set(pairKey(x.company_id, x.grp), x.n);
    for (const x of u) uses.set(pairKey(x.company_id, x.grp), x.n);
  }
  return {
    uses: (companyId, grp) => uses.get(pairKey(companyId, grp)) ?? 0,
    /** As opinions(): the 맞아요·달라요 on one version, 달라요 by where, and [dev]'s own. */
    opinions(companyId, grp, dev) {
      const why = { order: 0, time: 0, pay: 0, other: 0 };
      let ok = 0;
      let bad = 0;
      let mine = null;
      for (const r of voted.get(pairKey(companyId, grp)) ?? []) {
        if (r.ok) ok++;
        else {
          bad++;
          why[WHY_PARTS.includes(r.why) ? r.why : "other"]++;
        }
        if (dev && r.device === dev) mine = r.ok ? "ok" : (r.why ?? "wrong");
      }
      return { ok, bad, why, ...(mine ? { mine } : {}) };
    },
    /** As state(env, companyId, grp, threshold, adopted). */
    state(companyId, grp, threshold, adopted) {
      const k = pairKey(companyId, grp);
      const reports = sent.get(k) ?? [];
      const votes = voted.get(k) ?? [];
      const backing = votes.filter((v) => v.ok === 1);
      const s = tally([...reports.map((r) => ({ device: r.device, points: r.points, cluster: r.cluster })),
        ...backing.map((v) => ({ device: v.device, points: v.verified === 1 ? 2 : 1, cluster: v.cluster }))],
      votes.filter((v) => v.ok === 0).map((v) => v.device));
      const n = uses.get(k) ?? 0;
      const sentChecked = reports.filter((r) => r.verified === 1).length;
      // [COMM-11] N7 두 달 이상 확인됨: pay-slip checks from at least two networks in two different months.
      const proofs = [...reports.filter((r) => r.verified === 1).map((r) => [r.cluster, r.created_at]),
        ...backing.filter((v) => v.verified === 1).map((v) => [v.cluster, v.at])];
      const nets = new Set(proofs.map(([cluster]) => cluster)).size;
      const months = new Set(proofs.map(([, at]) => new Date(Math.floor(at / 1000) * 1000).toISOString().slice(0, 7))).size;
      return { id: grp, status: adopted ? "adopted" : "pending", points: s.points, persons: s.persons, bad: s.bad, threshold,
        ...trust(s, threshold), posts: posts.get(k) ?? 0, uses: n, senders: reports.length,
        checked: sentChecked + backing.filter((v) => v.verified === 1).length,
        by: reports.filter((r) => r.nick !== null && r.nick !== undefined).slice(0, 5).map((r) => r.nick),
        support: support({ uses: n, ok: s.persons, bad: s.bad, verified: sentChecked }),
        ...(nets >= 2 && months >= 2 ? { checkedLong: true } : {}) };
    },
  };
}

/** [SPD-01] representative() for every pair at once: the report each version is shown from (a checked one, else the newest). */
async function representatives(env, pairs) {
  const out = new Map();
  const read = await Promise.all(spPairChunks(pairs).map((chunk) => {
    const { cs, gs, values } = spInMarks(chunk);
    return all(env, `SELECT * FROM (SELECT r.*, ROW_NUMBER() OVER (PARTITION BY r.company_id, r.grp
        ORDER BY r.verified DESC, r.created_at DESC, r.rowid) AS sp_rank
      FROM reports r WHERE r.company_id IN (${cs}) AND r.grp IN (${gs}) AND r.status IN ('pending', 'adopted')) WHERE sp_rank = 1`, ...values);
  }));
  for (const rows of read) for (const r of rows) out.set(pairKey(r.company_id, r.grp), r);
  return out;
}

/**
 * [SPD-01] state() for many versions in one round: [pairs] {company, grp, adopted}; a Map by `company\u0001grp`. For the plaza's
 * lists and the developer app's screens of many versions.
 */
export async function statesFor(env, pairs, threshold) {
  const facts = await readStates(env, pairs);
  return new Map(pairs.map((p) => [pairKey(p.company, p.grp), facts.state(p.company, p.grp, threshold, Boolean(p.adopted))]));
}

/** [SPD-01] The one-version reads statesFor replaced, kept for the test that holds them to the same answers. */
export const stateOne = (env, companyId, grp, threshold, adopted) => state(env, companyId, grp, threshold, adopted);
export const opinionsOne = (env, companyId, grp, dev) => opinions(env, companyId, grp, dev);
export const stableOne = (env, companyId, deptNorm, grp, now) => stableOf(env, companyId, deptNorm, grp, now);
export const representativeOne = (env, companyId, grp) => representative(env, companyId, grp);
export { readStates, representatives, stableFacts };

// [SPD-04] The public lists that are the same for everyone: kept at the edge (Cache API, where the Worker has one: a custom
// domain) and, as workers.dev keeps nothing there, a short while in this Worker's memory. A write through this Worker forgets
// its copies (route()); the app asks past them after its own write (Cache-Control: no-cache).
export const BOARD_EDGE_SECONDS = 20;
export const PLAZAS_EDGE_SECONDS = 300;
export const TERMS_EDGE_SECONDS = 600;
const BOARD_MEMO_MS = 15 * 1000;
const BOARD_CACHE = { "cache-control": `public, max-age=${BOARD_EDGE_SECONDS}, stale-while-revalidate=120` };
const PLAZAS_CACHE = { "cache-control": `public, max-age=${PLAZAS_EDGE_SECONDS}, stale-while-revalidate=3600` };
const TERMS_CACHE = { "cache-control": `public, max-age=${TERMS_EDGE_SECONDS}, stale-while-revalidate=3600` };
const spLists = new WeakMap();

function listKept(env, request, key, ms, make) {
  const db = spDbKey(env);
  if (!spLists.has(db)) spLists.set(db, new Map());
  return spKept(spLists.get(db), key, ms, make, spNoCache(request));
}

const sortedQuery = (url) => {
  const q = new URLSearchParams(url.searchParams);
  q.sort();
  return q.toString();
};

// [SPD-06] Every company the app can find, with its 대표 주기 line as the search gives it: the app keeps it and finds a company
// as the user types without asking (0 requests). Public information only, as /top: names, other names, how they are said, counts.
export const ALL_EDGE_SECONDS = 300;
const ALL_CACHE = { "cache-control": `public, max-age=${ALL_EDGE_SECONDS}, stale-while-revalidate=3600` };
const allMemo = new WeakMap();

async function allCompanies(env) {
  const key = spDbKey(env);
  let kept = allMemo.get(key);
  // Not made afresh for no-cache: reading every company is the one costly read here, and five minutes old is fresh enough.
  if (!kept || Date.now() - kept.at >= ALL_EDGE_SECONDS * 1000) {
    const [rows, aliasRows] = await Promise.all([
      all(env, `SELECT c.id, c.name, c.norm, c.said, c.cho,
          (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS adopted,
          (SELECT COUNT(DISTINCT p.grp) FROM reports p WHERE p.company_id = c.id AND p.status = 'pending') AS pending
        FROM companies c WHERE c.hidden = 0 AND c.merged_into IS NULL AND c.kind != 'topic'`),
      all(env, `SELECT a.company_id, a.name FROM aliases a JOIN companies c ON c.id = a.company_id
        WHERE c.hidden = 0 AND c.merged_into IS NULL ORDER BY a.name`),
      fillNames(env),
    ]);
    const named = (v, made) => (v && v !== "-" ? v : made);
    const companies = rows.filter((c) => c.adopted > 0 || c.pending > 0).map((c) => ({ id: c.id, name: c.name,
      aliases: aliasRows.filter((a) => a.company_id === c.id).map((a) => a.name),
      said: named(c.said, spoken(c.name)), cho: named(c.cho, choseong(c.norm)),
      adopted: c.adopted, pending: c.pending, ...(c.adopted === 0 ? { first: true } : {}) }));
    const lines = await searchLeads(env, companies.map((c) => c.id), true);
    for (const c of companies) Object.assign(c, lines.get(c.id) ?? { users: 0 });
    companies.sort((a, b) => b.users - a.users || a.name.localeCompare(b.name, "ko"));
    kept = { at: Date.now(), companies, etag: await spEtag(JSON.stringify(companies)) };
    allMemo.set(key, kept);
  }
  return json({ companies: kept.companies, n: kept.companies.length, at: iso(kept.at) }, 200, { ...ALL_CACHE, etag: kept.etag });
}
// SP1 1.30 끝

/**
 * [PLAZA-05] 지지 (결정 D74, X-D3), the one score for which 판 of a department should be its 대표 판: phones that use it,
 * people who sent or backed it ([ok]) and pay-slip checks of a waiting one ([verified]), weighed by the Wilson share of
 * 맞았어요 among 맞았어요·달라요. The developer's 같은 회사 여러 판 screen recommends by the same score ([ADMUX-18]), so the
 * plaza's own choice and the recommendation never disagree. One decimal.
 */
export const support = (c) => Math.round(10 * (c.uses + c.ok + (c.verified ?? 0)) * (0.5 + wilson(c.ok, c.ok + c.bad) / 200)) / 10;

/** [PLAZA-05] Whether [grp] has more 지지 than [active], the department's 대표 판; a tie keeps the one in use. */
async function outweighs(env, companyId, grp, active) {
  const uses = (g) => one(env, "SELECT COUNT(*) AS n FROM uses WHERE company_id = ? AND grp = ?", companyId, g);
  // A pay-slip check counts on either side, as on the developer's screen.
  const checked = (g) => one(env, "SELECT COUNT(*) AS n FROM reports WHERE company_id = ? AND grp = ? AND status IN ('pending', 'adopted') AND verified = 1", companyId, g);
  const [mine, theirs, mineUses, theirUses, mineChecked, theirChecked] = await Promise.all([score(env, companyId, grp), score(env, companyId, active.grp),
    uses(grp), uses(active.grp), checked(grp), checked(active.grp)]);
  return support({ uses: mineUses.n, ok: mine.persons, bad: mine.bad, verified: mineChecked.n })
    > support({ uses: theirUses.n, ok: theirs.persons, bad: theirs.bad, verified: theirChecked.n });
}

/**
 * [COMM-07] How far people trust a rule set, 0 to 100: the share who said 맞았어요 among all who voted,
 * times how much of the adoption points it has. [agree] is that share alone, as a percent.
 */
export function trust(s, threshold) {
  const voters = s.persons + s.bad;
  const agree = voters ? s.persons / voters : 0;
  return { trust: Math.round(100 * agree * Math.min(1, s.points / threshold)), agree: Math.round(100 * agree) };
}

async function report(request, env) {
  if (await paused(env, "COMPANY_REPORT")) return json({ error: "paused" }, 503);
  const body = await readJson(request, LIMITS.reportBytes);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  const r = readReport(body);
  const now = Date.now();
  const net = await network(request, env, now);
  const dev = await deviceHash(device);
  if (!(await allow(env, `report|${dev}`, LIMITS.reportsPerDay, DAY, now)) ||
      !(await allow(env, `report|${net.day}`, LIMITS.reportsPerNetworkDay, DAY, now))) {
    return json({ error: "rate_limited" }, 429);
  }
  const c = await companyFor(env, r.company, now, r);
  const grp = (await sha256(`${r.deptNorm}|${await fingerprint(r.setup)}`)).slice(0, 16);
  await forgetNetworks(env, now);
  if (r.ask) return askDeveloper(env, c, grp, r, dev, net, now);
  // The same install sending the same setup again gets its earlier report back.
  const again = await one(env, "SELECT id FROM reports WHERE company_id = ? AND grp = ? AND device = ? AND status IN ('pending', 'adopted')", c.id, grp, dev);
  // [PLAZA-07] Who sends a company's setup joins it (결정 D79), the same again or not.
  const member = c.kind === "topic" ? null : await joinCompany(env, dev, c.id, calOf(body.cal), now);
  if (again) return json({ ...(await reportView(env, again.id)), again: true, first: false, newInfo: false, ...(member ? { member } : {}) });
  const cluster = await clusterFor(env, c.id, net);
  const active = await activeRule(env, c.id, r.deptNorm);
  const adopted = active?.grp === grp;
  // [PLAZA-10] The first version of this company's department from anyone: shown at once as 제보 1 · 확인 중.
  const first = !active && !(await one(env, `SELECT 1 FROM reports WHERE company_id = ? AND dept_norm = ? AND device != ?
    AND status IN ('pending', 'adopted') LIMIT 1`, c.id, r.deptNorm, dev));
  const id = randomId(22);
  await batch(env, [
    // A newer setup from the same sender for the same place takes the place of the older one.
    stmt(env, "UPDATE reports SET status = 'replaced', decided_at = ? WHERE company_id = ? AND dept_norm = ? AND device = ? AND status = 'pending'",
      now, c.id, r.deptNorm, dev),
    stmt(env, `INSERT INTO reports (id, company_id, dept, dept_norm, grp, setup, verified, note, device, net, cluster, points, status,
      rule_id, reason, created_at, decided_at, amends) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)`,
      id, c.id, r.dept, r.deptNorm, grp, JSON.stringify(r.setup), r.verified ? 1 : 0, r.note, dev, net.now, cluster, r.verified ? 2 : 1,
      adopted ? "adopted" : "pending", adopted ? active.id : null, now, adopted ? now : null,
      // [PLZ-02] Only a change of the rule in use counts as one.
      !adopted && active && r.amends === active.id ? active.id : null),
  ]);
  // AI 1.27 [PLAZA-30] What the 명세서 tick rested on: the month and whether it matched, or an older app's bare claim.
  if (r.proof || r.claim) await run(env, "UPDATE reports SET proof = ?, claim = ? WHERE id = ?", proofText(r.proof), r.claim ? 1 : 0, id);
  if (!adopted) await settle(env, c.id, grp, now);
  const reward = await newInfo(env, c, r, id, dev, now);
  const view = await reportView(env, id);
  return json({ ...view, first, newInfo: reward !== null, ...(reward ? { reward } : {}), ...(member ? { member } : {}) }, 201);
}

/**
 * [EXTRA-06] A way of paying the app cannot set yet. Only the developer reads it: it never counts for a
 * setup or waits to be adopted, since the setup in it is only as close as the sender could get.
 */
async function askDeveloper(env, c, grp, r, dev, net, now) {
  const again = await one(env, "SELECT id FROM reports WHERE company_id = ? AND grp = ? AND device = ? AND status = 'ask' AND note = ?",
    c.id, grp, dev, r.note);
  if (again) return json({ ...(await reportView(env, again.id)), again: true });
  const id = randomId(22);
  await run(env, `INSERT INTO reports (id, company_id, dept, dept_norm, grp, setup, verified, note, device, net, cluster, points, status,
    rule_id, reason, created_at, decided_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'ask', NULL, NULL, ?, NULL)`,
  id, c.id, r.dept, r.deptNorm, grp, JSON.stringify(r.setup), r.verified ? 1 : 0, r.note, dev, net.now, await clusterFor(env, c.id, net), now);
  return json(await reportView(env, id), 201);
}

async function companyFor(env, name, now, r = {}) {
  const norm = normName(name);
  const found = (await one(env, "SELECT id FROM companies WHERE norm = ? ORDER BY merged_into IS NOT NULL, created_at LIMIT 1", norm))
    ?? (await one(env, "SELECT company_id AS id FROM aliases WHERE norm = ?", norm));
  if (found) {
    const c = await follow(env, found.id);
    // [PLZ-12] A place without a plaza takes the first one a sender says; a developer can change it.
    if (c && ((!c.job && r.job) || (!c.region && r.region))) {
      await run(env, "UPDATE companies SET job = CASE WHEN job = '' THEN ? ELSE job END, region = CASE WHEN region = '' THEN ? ELSE region END WHERE id = ?",
        r.job ?? "", r.region ?? "", c.id);
      return one(env, "SELECT * FROM companies WHERE id = ?", c.id);
    }
    return c;
  }
  const id = randomId(12);
  const kind = r.topic ? "topic" : r.job === "nurse" ? "hospital" : "company";
  await run(env, `INSERT INTO companies (id, name, norm, hidden, merged_into, created_at, kind, job, region, said) VALUES (?, ?, ?, 0, NULL, ?, ?, ?, ?, ?)`,
    id, name, norm, now, kind, r.job ?? "", r.region ?? "", spoken(name) || "-");
  return one(env, "SELECT * FROM companies WHERE id = ?", id);
}

/** A company, or the one it was merged into. */
async function follow(env, id) {
  let c = await one(env, "SELECT * FROM companies WHERE id = ?", id);
  for (let i = 0; c?.merged_into && i < 8; i++) c = await one(env, "SELECT * FROM companies WHERE id = ?", c.merged_into);
  return c;
}

/** Network hashes only ever link sends within the hour; two hours on they are gone. */
function forgetNetworks(env, now) {
  return batch(env, [
    stmt(env, "UPDATE reports SET net = NULL WHERE net IS NOT NULL AND created_at < ?", now - 2 * HOUR),
    stmt(env, "UPDATE votes SET net = NULL WHERE net IS NOT NULL AND at < ?", now - 2 * HOUR),
    stmt(env, "DELETE FROM counters WHERE expires < ?", now),
  ]);
}

/**
 * The mark shared by sends to this company from one network within the hour: a random id, so the
 * network itself is not kept. Coworkers on the company Wi-Fi still count as people of their own, but
 * a setup only they agree on is not adopted without the developer, nor is one phone reinstalled.
 */
async function clusterFor(env, companyId, net) {
  const row = await one(env, `SELECT cluster FROM reports WHERE company_id = ?1 AND net IN (?2, ?3)
    UNION ALL SELECT cluster FROM votes WHERE company_id = ?1 AND net IN (?2, ?3) LIMIT 1`, companyId, net.now, net.prev);
  return row?.cluster ?? randomId(12);
}

/**
 * Points of one setup: each install once, at 2 when they checked it against a pay slip, and the
 * networks they sent from. Someone who says 달라요 does not count for it.
 */
async function score(env, companyId, grp) {
  // AS 1.29 [SRCH-05] both reads together.
  const [rows, doubters] = await Promise.all([all(env, `
    SELECT device, points, cluster FROM reports WHERE company_id = ?1 AND grp = ?2 AND status IN ('pending', 'adopted')
    UNION ALL
    SELECT device, CASE WHEN verified = 1 THEN 2 ELSE 1 END, cluster FROM votes WHERE company_id = ?1 AND grp = ?2 AND ok = 1`, companyId, grp),
  all(env, "SELECT device FROM votes WHERE company_id = ? AND grp = ? AND ok = 0", companyId, grp).then((l) => l.map((v) => v.device))]);
  return tally(rows, doubters);
}

/**
 * The score from what was sent and voted, read for many setups at once: [rows] are the reports still
 * pending or adopted and the 맞았어요 votes (device, points, cluster), [doubterDevices] the 달라요 votes.
 */
function tally(rows, doubterDevices) {
  const doubters = new Set(doubterDevices);
  const people = new Map();
  const networks = new Set();
  for (const r of rows) {
    if (doubters.has(r.device)) continue;
    people.set(r.device, Math.max(people.get(r.device) ?? 0, r.points));
    networks.add(r.cluster);
  }
  return { points: [...people.values()].reduce((sum, p) => sum + p, 0), persons: people.size, networks: networks.size, bad: doubters.size };
}

/** The report a setup is shown and adopted from: one checked against a pay slip, else the newest. */
const representative = (env, companyId, grp) => one(env, `SELECT * FROM reports WHERE company_id = ? AND grp = ?
  AND status IN ('pending', 'adopted') ORDER BY verified DESC, created_at DESC LIMIT 1`, companyId, grp);

const activeRule = (env, companyId, deptNorm) => one(env, `SELECT * FROM rules WHERE company_id = ? AND dept_norm = ?
  AND withdrawn = 0 AND superseded = 0 ORDER BY version DESC LIMIT 1`, companyId, deptNorm);

/** [COMM-03] Adopts a setup once different people agree on it, unless it is the developer's call. */
async function settle(env, companyId, grp, now) {
  const { adoptPoints, adoptAgree, adoptPersons, amendPersons } = await settings(env);
  const s = await score(env, companyId, grp);
  if (s.points < adoptPoints || s.networks < 2) return;
  // [COMM-07] Too many 달라요 for the 맞았어요: the developer decides, however many points it has.
  if (s.persons * 100 < adoptAgree * (s.persons + s.bad)) return;
  const rep = await representative(env, companyId, grp);
  if (!rep) return;
  const why = await blocker(env, companyId, grp, rep.dept_norm);
  if (!why) {
    // [PLZ-03] At least this many different people (결정 D32).
    if (s.persons >= adoptPersons) await adopt(env, companyId, grp, "auto", now);
    return;
  }
  // [PLZ-02] A setup sent to change the rule in use takes its place as the next version once enough
  // people agree, without the developer: the rule grows by what people find, and the old version stays
  // in the history. Anything else for a department with a rule waits for the developer as before.
  if (why !== "active" || s.persons < amendPersons) return;
  const active = await activeRule(env, companyId, rep.dept_norm);
  const amends = await one(env, `SELECT 1 FROM reports WHERE company_id = ? AND grp = ? AND status = 'pending' AND amends = ? LIMIT 1`,
    companyId, grp, active?.id ?? "");
  if (amends) return adopt(env, companyId, grp, "amend", now);
  // [PLAZA-05] Another setup for the same department (U26: "같은 회사 2개") becomes its 대표 판 once it is adopted
  // by its own right and more people stand behind it than behind the one in use (결정 D74). The one it replaces
  // is kept as an earlier version in the history, never deleted, and its users hear of it in 광장 소식.
  if (!active || s.persons < adoptPersons || await turnedDown(env, companyId, grp)) return;
  // AH 1.27 [PLAZA-15] A rule in use with too many 달라요 does not need to be outweighed: the next trusted version takes over.
  if ((await activeDoubted(env, companyId, active)) || (await outweighs(env, companyId, grp, active))) await adopt(env, companyId, grp, "rival", now);
}

/** [PLAZA-05] The developer took this setup down or turned it down: it never takes a department over by itself. */
async function turnedDown(env, companyId, grp) {
  const row = await one(env, `SELECT
      EXISTS (SELECT 1 FROM rules WHERE company_id = ?1 AND grp = ?2 AND withdrawn = 1) AS withdrawn,
      EXISTS (SELECT 1 FROM reports WHERE company_id = ?1 AND grp = ?2 AND status = 'rejected') AS rejected`, companyId, grp);
  return Boolean(row.withdrawn || row.rejected);
}

/**
 * Why a setup waits for the developer however many agree: another rule is in use for its department
 * ("active"), or the developer took this setup down ("withdrawn") or turned it down ("rejected").
 */
async function blocker(env, companyId, grp, deptNorm) {
  const row = await one(env, `SELECT
      EXISTS (SELECT 1 FROM rules WHERE company_id = ?1 AND dept_norm = ?3 AND withdrawn = 0 AND superseded = 0) AS active,
      EXISTS (SELECT 1 FROM rules WHERE company_id = ?1 AND grp = ?2 AND withdrawn = 1) AS withdrawn,
      EXISTS (SELECT 1 FROM reports WHERE company_id = ?1 AND grp = ?2 AND status = 'rejected') AS rejected`, companyId, grp, deptNorm);
  return row.active ? "active" : row.withdrawn ? "withdrawn" : row.rejected ? "rejected" : null;
}

/** Makes a setup the company's rule for its department; each install that sent it earns the reward. */
async function adopt(env, companyId, grp, source, now) {
  const rep = await representative(env, companyId, grp);
  if (!rep) return null;
  const { rewardMonths } = await settings(env);
  const last = await one(env, "SELECT MAX(version) AS v FROM rules WHERE company_id = ? AND dept_norm = ?", companyId, rep.dept_norm);
  const ruleId = randomId(16);
  const stmts = [
    stmt(env, "UPDATE rules SET superseded = 1 WHERE company_id = ? AND dept_norm = ? AND superseded = 0", companyId, rep.dept_norm),
    stmt(env, `INSERT INTO rules (id, company_id, dept, dept_norm, grp, version, setup, source, adopted_at, withdrawn, superseded)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)`, ruleId, companyId, rep.dept, rep.dept_norm, grp, (last?.v ?? 0) + 1, rep.setup, source, now),
    stmt(env, "UPDATE reports SET status = 'adopted', rule_id = ?, decided_at = ? WHERE company_id = ? AND grp = ? AND status = 'pending'",
      ruleId, now, companyId, grp),
  ];
  if (rewardMonths > 0) {
    stmts.push(stmt(env, `
      INSERT OR IGNORE INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at)
      SELECT 'w' || id, id, rule_id, device, ?, 'owed', '', ?, NULL FROM reports WHERE company_id = ? AND grp = ? AND rule_id = ?`,
    rewardMonths, now, companyId, grp, ruleId));
  }
  await batch(env, stmts);
  await payOut(env, ruleId, now);
  return ruleId;
}

/**
 * [PLZ-03] Rewards for an adopted rule go out by themselves (결정 D33): the first three phones that sent
 * it, and whoever proposed it in the talk, get their months as a pass at once. A phone past the cap
 * (months in the last days set) keeps its reward 'owed' for an admin to look at, and so does everyone
 * after the first three. Voters get nothing: rewarding votes would buy them.
 */
async function payOut(env, ruleId, now) {
  const { autoReward, rewardCapMonths, rewardCapDays } = await settings(env);
  if (!autoReward) return;
  const owed = await all(env, `SELECT w.id, w.device, w.months, p.post_id FROM rewards w JOIN reports p ON p.id = w.report_id
    WHERE w.rule_id = ? AND w.status = 'owed' ORDER BY p.post_id IS NULL, p.created_at LIMIT 3`, ruleId);
  await payEach(env, owed, { rewardCapMonths, rewardCapDays }, now);
}

/**
 * Gives each owed reward of [owed] (id, device, months) as setReward does by hand: a later renewal from
 * Play for someone already paying, a pass for anyone else, unless the phone is past the cap.
 */
async function payEach(env, owed, { rewardCapMonths, rewardCapDays }, now) {
  for (const w of owed) {
    if (w.device.startsWith("gone")) continue;
    // [INVITE-04] Invites have their own cap and do not use this one up.
    const got = await one(env, "SELECT COALESCE(SUM(months), 0) AS m FROM rewards WHERE device = ? AND status = 'paid' AND paid_at > ? AND rule_id != 'invite'",
      w.device, now - rewardCapDays * DAY);
    if (got.m + w.months > rewardCapMonths) continue;
    let deferred = null;
    try {
      deferred = await deferFor(env, w.device, w.months, now);
    } catch (e) {
      console.error(`defer ${w.id}: ${e?.code ?? e}`);
    }
    const memo = deferred ? `자동 지급 · Play 결제 미룸(다음 결제 ${iso(deferred).slice(0, 10)})` : "자동 지급";
    await run(env, "UPDATE rewards SET status = 'paid', paid_at = ?, memo = ?, deferred = ? WHERE id = ? AND status = 'owed'",
      now, memo, deferred ? 1 : 0, w.id);
    await audit(env, now, "reward_auto", w.id, `${w.months}개월${deferred ? " 미룸" : ""}`);
  }
}

async function reportView(env, id) {
  const r = await one(env, "SELECT * FROM reports WHERE id = ?", id);
  if (!r) return null;
  const c = await one(env, "SELECT id, name FROM companies WHERE id = ?", r.company_id);
  const { adoptPoints, rewardMonths } = await settings(env);
  const s = await score(env, r.company_id, r.grp);
  const rule = r.rule_id ? await one(env, "SELECT withdrawn FROM rules WHERE id = ?", r.rule_id) : null;
  const reward = await one(env, "SELECT months, status FROM rewards WHERE report_id = ?", r.id);
  const ask = r.status === "ask" || r.status === "answered";
  return {
    id: r.id,
    status: r.status === "adopted" && rule?.withdrawn === 1 ? "withdrawn" : r.status,
    ask,
    company: { id: c.id, name: c.name },
    dept: r.dept,
    setup: r.grp,
    verified: r.verified === 1,
    points: ask ? 0 : s.points,
    persons: ask ? 0 : s.persons,
    threshold: adoptPoints,
    // Enough coworkers agree, and the developer is checking it.
    review: r.status === "pending" && s.persons >= 2 && s.points >= adoptPoints,
    createdAt: iso(r.created_at),
    decidedAt: r.decided_at ? iso(r.decided_at) : null,
    reason: r.reason ? { code: r.reason, text: (ask ? ANSWERS : REASONS)[r.reason] ?? "" } : null,
    reward: reward ? { months: reward.months, status: reward.status } : null,
    rewardMonths,
  };
}

async function reportStatus(env, id) {
  const view = await reportView(env, id);
  return view ? json(view) : json({ error: "not_found" }, 404);
}

async function takeBack(request, env, id) {
  const device = deviceId(request.headers.get("x-device"));
  const r = await one(env, "SELECT device FROM reports WHERE id = ?", id);
  if (!r) return json({ error: "not_found" }, 404);
  if (!device || (await deviceHash(device)) !== r.device) return json({ error: "forbidden" }, 403);
  // [PLZ-04] An adopted rule belongs to everyone who checked it: taking it back only cuts the sender's
  // tie to it (결정 D34). The rule, its versions and its score stay; a pass already given stays theirs.
  const full = await one(env, "SELECT status FROM reports WHERE id = ?", id);
  if (full.status === "adopted") {
    await batch(env, [
      stmt(env, "DELETE FROM rewards WHERE report_id = ? AND status = 'owed'", id),
      stmt(env, "UPDATE reports SET device = ? WHERE id = ?", `gone${randomId(12)}`, id),
    ]);
    return json({ kept: true, text: "채택된 규칙은 모두가 쓰는 규칙이라 남아요. 보낸 분과의 연결만 지웠어요." });
  }
  await batch(env, [stmt(env, "DELETE FROM rewards WHERE report_id = ?", id), stmt(env, "DELETE FROM reports WHERE id = ?", id)]);
  return new Response(null, { status: 204 });
}

/** Everything one install sent: reports, rewards not yet given and its 맞았어요·달라요. */
async function forgetMine(request, env) {
  const device = deviceId(request.headers.get("x-device"));
  if (!device) return json({ error: "device", field: "device" }, 400);
  const dev = await deviceHash(device);
  // [PLZ-04] Adopted rules and the posts that proposed them stay, cut loose from this install (결정 D34).
  const gone = `gone${randomId(12)}`;
  await batch(env, [
    stmt(env, `UPDATE posts SET device = ?2 WHERE device = ?1 AND report_id IN (SELECT id FROM reports WHERE device = ?1 AND status = 'adopted')`, dev, gone),
    stmt(env, "UPDATE reports SET device = ? WHERE device = ? AND status = 'adopted'", gone, dev),
    // [PLZ-01] Their votes on posts come off the posts' counts.
    stmt(env, "UPDATE posts SET agree = agree - 1 WHERE id IN (SELECT post_id FROM post_votes WHERE device = ? AND ok = 1)", dev),
    stmt(env, "UPDATE posts SET disagree = disagree - 1 WHERE id IN (SELECT post_id FROM post_votes WHERE device = ? AND ok = 0)", dev),
    stmt(env, "DELETE FROM post_votes WHERE device = ?", dev),
    stmt(env, "DELETE FROM uses WHERE device = ?", dev),
    // [PLAZA-07] The companies it joined go at once, the 30 days with them.
    stmt(env, "DELETE FROM members WHERE device = ?", dev),
    stmt(env, "DELETE FROM nicks WHERE device = ?", dev),
    stmt(env, "DELETE FROM rate_votes WHERE device = ?", dev),
    stmt(env, "UPDATE rates SET device = ? WHERE device = ? AND status = 'adopted'", gone, dev),
    stmt(env, "DELETE FROM rates WHERE device = ?", dev),
  ]);
  const [, reports, votes, , posts, , , , , , , , asked] = await batch(env, [
    stmt(env, "DELETE FROM rewards WHERE device = ?", dev),
    stmt(env, "DELETE FROM reports WHERE device = ?", dev),
    stmt(env, "DELETE FROM votes WHERE device = ?", dev),
    stmt(env, "DELETE FROM counters WHERE key IN (?, ?)", `report|${dev}`, `post|${dev}`),
    // [COMM-06] Their posts and their 신고 go too; a post they flagged counts one fewer.
    stmt(env, "DELETE FROM posts WHERE device = ?", dev),
    stmt(env, "UPDATE posts SET flags = flags - 1 WHERE id IN (SELECT post_id FROM post_flags WHERE device = ?)", dev),
    stmt(env, "DELETE FROM post_flags WHERE device = ? OR post_id NOT IN (SELECT id FROM posts)", dev),
    // [ADMIN-11][ADMIN-12] Their questions, with what admins attached to them.
    ...dropInquiries(env, "device = ?", dev),
    // [ADMIN-02] Their 나도 그래요 and 신고 on others' questions go too, and those count one fewer.
    stmt(env, `UPDATE inquiries SET same = same - 1 WHERE id IN (SELECT inquiry_id FROM inquiry_marks WHERE device = ? AND kind = 'same')`, dev),
    stmt(env, `UPDATE inquiries SET flags = flags - 1 WHERE id IN (SELECT inquiry_id FROM inquiry_marks WHERE device = ? AND kind = 'flag')`, dev),
    stmt(env, "DELETE FROM inquiry_marks WHERE device = ? OR inquiry_id NOT IN (SELECT id FROM inquiries)", dev),
    // [MON-08][MON-09] Its recovery code and admin phone link. Purchases and consent answers stay as the
    // law's record of a sale until they expire (처리방침 보관 기간).
    stmt(env, "DELETE FROM pass_codes WHERE device = ?", dev),
    stmt(env, "DELETE FROM admin_devices WHERE device = ?", dev),
    // [INVITE-04] Its invite code and an invite not yet counted go; a counted one keeps only the phone mark for its
    // year, so the same phone does not count again (처리방침 1-12).
    stmt(env, "DELETE FROM invite_codes WHERE device = ?", dev),
    stmt(env, "DELETE FROM invite_uses WHERE invitee = ? AND status NOT IN ('credited', 'capped', 'qualified')", dev),
    stmt(env, "UPDATE invite_uses SET invitee = ? WHERE invitee = ?", `${gone}i`, dev),
    stmt(env, "UPDATE invite_uses SET inviter = ? WHERE inviter = ?", gone, dev),
  ]);
  return json({ reports: reports.meta.changes, votes: votes.meta.changes, posts: posts.meta.changes, inquiries: asked.meta.changes });
}

async function vote(request, env) {
  const body = await readJson(request, 2048);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (typeof body.company !== "string" || !/^[A-Za-z0-9]{12}$/.test(body.company)) refuse("company", "company");
  if (typeof body.setup !== "string" || !/^[0-9a-f]{16}$/.test(body.setup)) refuse("setup", "setup");
  if (typeof body.ok !== "boolean") refuse("ok", "ok");
  // AH 1.27 [PLAZA-15] 달라요 says where: the order, the times or the pay items (AH-D9).
  const why = body.ok ? null : VOTE_WHY.includes(body.why) ? body.why : "wrong";
  // AI 1.27 [PLAZA-30] A pay-slip 맞았어요 needs the month the phone matched; an older app's bare one counts as a plain 맞았어요.
  const proof = readProof(body.proof);
  const verified = body.ok && body.verified === true && proofCounts(proof);
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `vote|${net.now}`, LIMITS.votesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
  const c = await follow(env, body.company);
  if (!c || c.hidden) return json({ error: "not_found" }, 404);
  const grp = body.setup;
  const rule = await one(env, "SELECT id FROM rules WHERE company_id = ? AND grp = ? AND withdrawn = 0 AND superseded = 0", c.id, grp);
  const pending = rule ? null : await one(env, "SELECT id FROM reports WHERE company_id = ? AND grp = ? AND status = 'pending' LIMIT 1", c.id, grp);
  if (!rule && !pending) return json({ error: "not_found" }, 404);
  const dev = await deviceHash(device);
  await forgetNetworks(env, now);
  const cluster = await clusterFor(env, c.id, net);
  await run(env, `INSERT INTO votes (company_id, grp, device, cluster, ok, verified, why, net, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (company_id, grp, device) DO UPDATE SET cluster = excluded.cluster, ok = excluded.ok, verified = excluded.verified,
      why = excluded.why, net = excluded.net, at = excluded.at`, c.id, grp, dev, cluster, body.ok ? 1 : 0, verified ? 1 : 0, why, net.now, now);
  await run(env, "UPDATE votes SET claim = ? WHERE company_id = ? AND grp = ? AND device = ?", body.ok && body.verified === true && !proof ? 1 : 0, c.id, grp, dev);
  if (body.ok && !rule) await settle(env, c.id, grp, now);
  // AH 1.27 [PLAZA-15] Too many 달라요 on the rule in use: a version waiting in its department may take its place now.
  if (!body.ok && rule) await settleDept(env, c.id, (await one(env, "SELECT dept_norm FROM rules WHERE id = ?", rule.id)).dept_norm, now);
  const adopted = rule ?? (await one(env, "SELECT id FROM rules WHERE company_id = ? AND grp = ? AND withdrawn = 0 AND superseded = 0", c.id, grp));
  return json(await state(env, c.id, grp, (await settings(env)).adoptPoints, Boolean(adopted)));
}

// ---- rule sets everyone can see, vote on and talk about [COMM-06][COMM-07] ----

/** Why a post is flagged, as the app offers it. */
const FLAGS = {
  abuse: "욕설·비방",
  personal: "개인 정보가 있어요",
  spam: "광고·도배",
  false: "일부러 틀린 정보",
  // AI 1.27 [PLAZA-33] 투자 이야기: a 종목 추천 or 리딩 (결정 AI-D8).
  advice: "종목 추천·리딩",
};

/** Why the developer hid a post, as its writer's app shows it. */
const HIDE_REASONS = {
  abuse: "욕설이나 비방이 있어요",
  personal: "개인 정보가 있어요",
  spam: "광고나 도배예요",
  offtopic: "이 규칙과 상관없는 글이에요",
  // AI 1.27 [PLAZA-33]
  advice: "종목 추천이나 리딩 글이에요",
  flags: "신고가 모여 개발자가 확인하고 있어요",
};

/**
 * [COMM-07] Every company's rule sets in one list: ones people are still voting on (newest activity
 * first), or adopted ones anyone can use (newest first). What the developer turned down or took down,
 * and hidden companies, are not here.
 */
async function board(env, url) {
  const tab = url.searchParams.get("tab") === "adopted" ? "adopted" : "voting";
  const offset = Math.min(600, Math.max(0, Number.parseInt(url.searchParams.get("from") ?? "0", 10) || 0));
  const size = 20;
  // A company's name or another name for it, written any way 회사 찾기 accepts; one letter narrows nothing.
  const q = normName(url.searchParams.get("q")).slice(0, 40);
  const like = q.length < 2 ? "" : `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  const named = `(?3 = '' OR c.norm LIKE ?3 ESCAPE '\\' OR EXISTS (SELECT 1 FROM aliases a WHERE a.company_id = c.id AND a.norm LIKE ?3 ESCAPE '\\'))
    AND (?4 = '' OR c.job = ?4) AND (?5 = '' OR c.region = ?5)`;
  // [PLZ-12] One job plaza, one region, or all of them.
  const job = Object.hasOwn(JOBS, url.searchParams.get("job") ?? "") ? url.searchParams.get("job") : "";
  const region = Object.hasOwn(REGIONS, url.searchParams.get("region") ?? "") ? url.searchParams.get("region") : "";
  // [PLZ-05] Newest first, or the most trusted, or the most used.
  const sort = ["trust", "used"].includes(url.searchParams.get("sort")) ? url.searchParams.get("sort") : "recent";
  // SP1 1.30 [SPD-01] The settings beside the list, then every listed version's state and 대표 report together: two rounds
  // (before 1.30 a page of 20 waited for each version in turn, 142 statements in 45 rounds).
  const [{ adoptPoints }, rows] = await Promise.all([settings(env), tab === "adopted"
    ? all(env, `SELECT r.company_id, r.grp, r.dept, r.setup, r.version, r.adopted_at AS at, c.name, c.kind, c.job, c.region
        FROM rules r JOIN companies c ON c.id = r.company_id
        WHERE r.withdrawn = 0 AND r.superseded = 0 AND c.hidden = 0 AND c.merged_into IS NULL AND ${named}
        ORDER BY ${sort === "used" ? "(SELECT COUNT(*) FROM uses u WHERE u.company_id = r.company_id AND u.grp = r.grp) DESC," : ""} r.adopted_at DESC
        LIMIT ?1 OFFSET ?2`, size + 1, offset, like, job, region)
    : all(env, `SELECT p.company_id, p.grp, c.name, c.kind, c.job, c.region,
          MAX(MAX(p.created_at),
            COALESCE((SELECT MAX(v.at) FROM votes v WHERE v.company_id = p.company_id AND v.grp = p.grp), 0),
            COALESCE((SELECT MAX(t.created_at) FROM posts t WHERE t.company_id = p.company_id AND t.grp = p.grp AND t.status = 'shown'), 0)) AS at
        FROM reports p JOIN companies c ON c.id = p.company_id
        WHERE p.status = 'pending' AND c.hidden = 0 AND c.merged_into IS NULL AND ${named}
          AND NOT EXISTS (SELECT 1 FROM reports x WHERE x.company_id = p.company_id AND x.grp = p.grp AND x.status = 'rejected')
          AND NOT EXISTS (SELECT 1 FROM rules w WHERE w.company_id = p.company_id AND w.grp = p.grp AND (w.withdrawn = 1 OR w.superseded = 0))
        GROUP BY p.company_id, p.grp ORDER BY at DESC LIMIT ?1 OFFSET ?2`, size + 1, offset, like, job, region)]);
  const page = rows.slice(0, size);
  const pairs = page.map((r) => ({ company: r.company_id, grp: r.grp }));
  const [facts, reps] = await Promise.all([readStates(env, pairs), tab === "adopted" ? null : representatives(env, pairs)]);
  const items = [];
  for (const r of page) {
    const rep = tab === "adopted" ? r : reps.get(pairKey(r.company_id, r.grp));
    if (!rep) continue;
    const s = facts.state(r.company_id, r.grp, adoptPoints, tab === "adopted");
    if (s.persons === 0 && tab === "voting") continue;
    const lines = describe(JSON.parse(rep.setup));
    items.push({
      company: { id: r.company_id, name: r.name, kind: r.kind ?? "company", job: r.job ?? "", region: r.region ?? "" },
      dept: rep.dept,
      ...s,
      version: r.version ?? null,
      at: iso(r.at),
      gist: lines.find(([label]) => label === "근무")?.[1] ?? "",
      cycle: lines.find(([label]) => label.startsWith("순서"))?.[0] ?? "",
      pay: lines.some(([label]) => label === "사업장"),
    });
  }
  // [PLZ-05] The most trusted first, within the page the database gave.
  if (sort === "trust") items.sort((a, b) => b.trust - a.trust || b.persons - a.persons);
  return { tab, ...(like ? { q } : {}), ...(job ? { job } : {}), ...(region ? { region } : {}), sort, items,
    next: rows.length > size ? offset + size : null, threshold: adoptPoints };
}

/**
 * A rule set people may talk about: adopted and in use, or still gathering votes. [past] also takes an
 * older version a newer one replaced, whose talk stays readable as part of the rule's history ([PLZ-04]).
 */
async function talkable(env, companyId, grp, past = false) {
  // AI 1.27 [PLAZA-32] A room is there as long as its company (or kind of work) is.
  if (grp === ROOM) return roomPlace(env, companyId);
  const c = await follow(env, companyId);
  if (!c || c.hidden) return null;
  const live = await one(env, `SELECT
      EXISTS (SELECT 1 FROM rules WHERE company_id = ?1 AND grp = ?2 AND withdrawn = 0 AND (superseded = 0 OR ?3)) AS rule,
      EXISTS (SELECT 1 FROM reports WHERE company_id = ?1 AND grp = ?2 AND status = 'pending') AS pending`, c.id, grp, past ? 1 : 0);
  return live.rule || live.pending ? c : null;
}

/**
 * [COMM-06] The talk on one rule set, oldest first. Each writer is 참여자 N of this talk only, with what
 * they said about the rule set: 올린 분 (they sent it), 맞았어요 or 달라요. With X-Device, the caller's own
 * posts are marked, and their own hidden ones show with why.
 */
async function talk(request, env, companyId, grp) {
  const r = await talkData(request, env, companyId, grp);
  return json(r.body, r.status);
}

/**
 * The talk as {status, body}. SP1 1.30 [SPD-01] Three rounds whatever the number of posts: where the talk is, who asks and
 * whether it is open; then its posts with everything said about their writers; then the states of the proposals among them
 * (before 1.30 sixteen reads one after another). [known] {c, who}: a company's room for a member the caller already knows.
 */
async function talkData(request, env, companyId, grp, known = null) {
  const room = grp === ROOM;
  const device = deviceId(request.headers.get("x-device"));
  const openOf = (id) => one(env, `SELECT
      EXISTS (SELECT 1 FROM rules WHERE company_id = ?1 AND grp = ?2 AND withdrawn = 0) AS rule,
      EXISTS (SELECT 1 FROM rules WHERE company_id = ?1 AND grp = ?2 AND withdrawn = 0 AND superseded = 0) AS current,
      EXISTS (SELECT 1 FROM reports WHERE company_id = ?1 AND grp = ?2 AND status = 'pending') AS pending`, id, grp);
  // AI 1.27 [PLAZA-32] A company's room is only for its members; a kind's plaza for any phone with the app (AI-D6·D7).
  const style = room && Object.hasOwn(STYLE_ROOMS, companyId);
  let [c, who, live] = known ? [known.c, known.who, null] : await Promise.all([
    // AI 1.27 [PLAZA-32] A room is there as long as its company (or kind of work) is.
    room ? roomPlace(env, companyId) : follow(env, companyId),
    // [PLAZA-08] A proposal's lines about pay are for the company's members (결정 D79, AF-D6); its shift lines are for all.
    style ? roomViewer(request, env, { style: true }) : viewer(request, env, companyId),
    // A rule set people may talk about (talkable): adopted and in use, still gathering votes, or [PLZ-04] an older version.
    room ? null : openOf(companyId),
  ]);
  if (!c || c.hidden) return { status: 404, body: { error: "not_found" } };
  if (!style && c.id !== companyId) [who, live] = await Promise.all([viewer(request, env, c.id), room ? null : openOf(c.id)]);
  if (!room && !(live.rule || live.pending)) return { status: 404, body: { error: "not_found" } };
  if (room && !who.member) {
    return { status: who.limited ? 429 : who.dev ? 403 : 400, body: { error: who.limited ? "rate_limited" : who.dev ? "member" : "device" } };
  }
  const me = device ? (who.dev ?? await deviceHash(device)) : null;
  const member = who.member;
  const lines = (change) => (member ? change : change.filter((l) => l.field === "shift" || l.field === "order"));
  const now = Date.now();
  // [COMM-09] Writers this phone chose not to see, from its own list (X-Hide); the server keeps no list.
  const sealed = authorKey(env).then(async (key) => ({ key, hidden: await hiddenAuthors(key, request.headers.get("x-hide")) }));
  const [rows, sentRows, voteRows, checkedRows, myVoteRows, nickRows, { adoptPoints }, reviewRows, proposalRows, muted, used, { key, hidden }] = await Promise.all([
    room
      ? all(env, `SELECT * FROM (SELECT *, rowid AS r FROM posts WHERE company_id = ? AND grp = ? ORDER BY created_at DESC, rowid DESC LIMIT ${ROOM_LIMITS.shown})
          ORDER BY created_at, r`, c.id, grp)
      : all(env, "SELECT * FROM posts WHERE company_id = ? AND grp = ? ORDER BY created_at, rowid LIMIT 500", c.id, grp),
    all(env, `SELECT device FROM reports WHERE company_id = ? AND grp = ? AND status IN ('pending', 'adopted')`, c.id, grp),
    all(env, "SELECT device, ok, verified FROM votes WHERE company_id = ? AND grp = ?", c.id, grp),
    all(env, "SELECT device FROM reports WHERE company_id = ? AND grp = ? AND verified = 1", c.id, grp),
    // [PLZ-01][PLZ-08] My votes on these posts, and the names writers chose to be shown by.
    me ? all(env, `SELECT v.post_id, v.ok FROM post_votes v JOIN posts p ON p.id = v.post_id
      WHERE v.device = ? AND p.company_id = ? AND p.grp = ?`, me, c.id, grp) : [],
    all(env, `SELECT n.device, n.nick FROM nicks n WHERE n.device IN
      (SELECT device FROM posts WHERE company_id = ? AND grp = ?)`, c.id, grp),
    settings(env),
    // [COMM-11] A post someone flagged as 개인 정보 shows dimmed with 확인 중 until the developer looks.
    all(env, `SELECT DISTINCT f.post_id FROM post_flags f JOIN posts p ON p.id = f.post_id
      WHERE p.company_id = ? AND p.grp = ? AND p.kept = 0 AND f.reason = 'personal'`, c.id, grp),
    // [SRV-08] The proposals' setups in one read, not one per post.
    all(env, `SELECT r.id, r.grp, r.status FROM reports r WHERE r.id IN
      (SELECT report_id FROM posts WHERE company_id = ? AND grp = ? AND report_id IS NOT NULL)`, c.id, grp),
    me ? one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", me, now) : null,
    // [COMM-11] How many more posts today, so the app can say so before a refusal.
    me ? one(env, "SELECT count FROM counters WHERE key = ? AND expires > ?", `${room ? "room" : "post"}|${me}`, now) : null,
    sealed,
  ]);
  const dropped = new Set();
  let hiddenByYou = 0;
  const senders = new Set(sentRows.map((r) => r.device));
  const votes = new Map(voteRows.map((v) => [v.device, v]));
  const verified = new Set(checkedRows.map((r) => r.device));
  const numbers = new Map();
  const posts = [];
  const myVotes = new Map(myVoteRows.map((v) => [v.post_id, v.ok === 1]));
  const nicks = new Map(nickRows.map((n) => [n.device, n.nick]));
  const review = new Set(reviewRows.map((r) => r.post_id));
  const proposals = new Map(proposalRows.map((r) => [r.id, r]));
  // [PLZ-02] Where each proposal's setup stands, every one in one round.
  const facts = proposals.size ? await readStates(env, [...proposals.values()].map((r) => ({ company: c.id, grp: r.grp }))) : null;
  const states = new Map();
  for (const p of rows) {
    if (!numbers.has(p.device)) numbers.set(p.device, numbers.size + 1);
    const mine = p.device === me;
    if (p.status !== "shown" && !mine) continue;
    // A hidden writer's posts go, and the replies under them with them, so no answer hangs without its question.
    if (!mine && (hidden.has(p.device) || (p.parent && dropped.has(p.parent)))) {
      if (hidden.has(p.device)) hiddenByYou++;
      dropped.add(p.id);
      continue;
    }
    const v = votes.get(p.device);
    // [PLZ-02] A proposal shows where its setup stands: points, people, adopted.
    let proposal = null;
    if (p.report_id) {
      const r = proposals.get(p.report_id);
      if (r) {
        const key = `${r.grp}|${r.status === "adopted"}`;
        if (!states.has(key)) states.set(key, facts.state(c.id, r.grp, adoptPoints, r.status === "adopted"));
        proposal = { setup: r.grp, ...states.get(key), lines: [] };
      }
    }
    posts.push({
      id: p.id,
      who: numbers.get(p.device),
      nick: nicks.get(p.device) ?? null,
      stance: senders.has(p.device) ? "sender" : v ? (v.ok ? "ok" : "bad") : null,
      verified: verified.has(p.device) || v?.verified === 1,
      body: p.body,
      at: iso(p.created_at),
      mine,
      // [COMM-09] Who wrote it, sealed afresh for each post: the app can say 이 사람 글 안 보기 with it, but
      // two posts' marks never match, so nobody can tie a writer's posts together from them.
      ...(mine ? {} : { author: await authorToken(key, p.device) }),
      parent: p.parent ?? null,
      kind: p.kind ?? "talk",
      change: p.change ? lines(JSON.parse(p.change)) : null,
      agree: p.agree ?? 0,
      disagree: p.disagree ?? 0,
      ...postTrust(p),
      ...(myVotes.has(p.id) ? { myVote: myVotes.get(p.id) } : {}),
      ...(proposal ? { proposal } : {}),
      ...(p.status === "shown" ? {} : { hidden: { code: p.hide_reason ?? "flags", text: HIDE_REASONS[p.hide_reason ?? "flags"] ?? "" } }),
      ...(p.status === "shown" && review.has(p.id) ? { review: true } : {}),
    });
  }
  // [PLZ-04] An older version's talk is read only; the app points to the version in use.
  const past = !room && !(live.current || live.pending);
  const perDay = room ? ROOM_LIMITS.perDay : LIMITS.postsPerDay;
  return { status: 200, body: { company: { id: c.id, name: c.name }, setup: grp, posts, member, ...(room ? { room: c.style ? "style" : "company" } : {}),
    ...(past ? { past: true } : {}), ...(muted ? { mutedUntil: iso(muted.until) } : {}),
    ...(hiddenByYou ? { hiddenByYou } : {}), ...(me ? { left: { posts: Math.max(0, perDay - (used?.count ?? 0)) } } : {}) } };
}

/** [COMM-06] A post in the talk on a rule set: no email, phone number or amount, like everything else sent here. */
async function post(request, env) {
  if (await paused(env, "PLAZA_WRITE")) return json({ error: "paused" }, 503);
  const body = await readJson(request, LIMITS.reportBytes + 4096);
  // AI 1.27 [PLAZA-32][PLAZA-33] A post in a company's or a kind of work's room.
  if (body?.setup === ROOM) return roomPost(request, env, body);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (typeof body.company !== "string" || !/^[A-Za-z0-9]{12}$/.test(body.company)) refuse("company", "company");
  if (typeof body.setup !== "string" || !/^[0-9a-f]{16}$/.test(body.setup)) refuse("setup", "setup");
  const text = str(body.body, LIMITS.post, "body", { empty: false, lines: true, code: "body" }).trim();
  if (text.length < 2) refuse("body", "body");
  const why = personal(text);
  if (why) refuse(why, "body");
  const now = Date.now();
  const dev = await deviceHash(device);
  const muted = await one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", dev, now);
  if (muted) return json({ error: "muted", until: iso(muted.until) }, 403);
  const c = await talkable(env, body.company, body.setup);
  if (!c) return json({ error: "not_found" }, 404);
  const net = await network(request, env, now);
  if (!(await allow(env, `post|${dev}`, LIMITS.postsPerDay, DAY, now)) ||
      !(await allow(env, `post|${net.day}`, LIMITS.postsPerNetworkDay, DAY, now))) {
    return json({ error: "rate_limited" }, 429);
  }
  // [PLZ-05] A reply goes under a post of the same talk; replies to replies go under the same first post.
  let parent = null;
  if (given(body.parent)) {
    const top = typeof body.parent === "string" ? await one(env, "SELECT id, parent FROM posts WHERE id = ? AND company_id = ? AND grp = ?",
      body.parent, c.id, body.setup) : null;
    if (!top) refuse("parent", "parent");
    parent = top.parent ?? top.id;
  }
  const kind = given(body.kind) ? oneOf(body.kind, ["talk", "question", "proposal"], "kind") : "talk";
  const change = kind === "proposal" ? readChange(body.change) : null;
  const id = `p${randomId(15)}`;
  // [PLZ-02][PLZ-09] A proposal carries the whole setup as it should be: it is sent like a report, as a
  // change of the rule this talk is about when that rule is in use, and the post's 맞아요 count for it.
  let reportId = null;
  let proposal = null;
  if (kind === "proposal" && given(body.proposal)) {
    const texts = [];
    const setup = cleanSetup(body.proposal, texts);
    for (const [field, t] of texts) {
      const why = personal(t);
      if (why) refuse(why, field);
    }
    const base = await one(env, `SELECT dept, dept_norm FROM rules WHERE company_id = ?1 AND grp = ?2
      UNION ALL SELECT dept, dept_norm FROM reports WHERE company_id = ?1 AND grp = ?2 LIMIT 1`, c.id, body.setup);
    const grp = (await sha256(`${base.dept_norm}|${await fingerprint(setup)}`)).slice(0, 16);
    if (grp === body.setup) refuse("same", "proposal");
    const active = await activeRule(env, c.id, base.dept_norm);
    reportId = randomId(22);
    await run(env, `INSERT INTO reports (id, company_id, dept, dept_norm, grp, setup, verified, note, device, net, cluster, points, status,
      rule_id, reason, created_at, decided_at, amends, post_id) VALUES (?, ?, ?, ?, ?, ?, 0, '', ?, ?, ?, 1, 'pending', NULL, NULL, ?, NULL, ?, ?)`,
    reportId, c.id, base.dept, base.dept_norm, grp, JSON.stringify(setup), dev, net.now, await clusterFor(env, c.id, net), now,
    active && active.grp === body.setup ? active.id : null, id);
    proposal = grp;
  }
  await run(env, `INSERT INTO posts (id, company_id, grp, device, body, status, flags, kept, hide_reason, created_at, hidden_at,
      parent, kind, change, report_id, agree, disagree)
    VALUES (?, ?, ?, ?, ?, 'shown', 0, 0, NULL, ?, NULL, ?, ?, ?, ?, 0, 0)`, id, c.id, body.setup, dev, text, now,
  parent, kind, change ? JSON.stringify(change) : null, reportId);
  if (proposal) await settle(env, c.id, proposal, now);
  return json({ id, at: iso(now), ...(proposal ? { proposal } : {}) }, 201);
}

/**
 * [PLZ-09] What a proposal says is different, in fields anyone can read without opening the setup:
 * up to six lines of what (one of CHANGE_FIELDS), how it is now, how it should be, and why. No amounts
 * (the same rule as everything else sent here), so 기본급의 10% rather than a sum.
 */
const CHANGE_FIELDS = {
  shift: "근무 시간", order: "근무 순서", break: "휴게", overtime: "연장 근무", night: "야간", holiday: "휴일 근무",
  allowance: "수당", bonus: "상여", leave: "연차·휴가", other: "그 밖의 것",
};

function readChange(raw) {
  const lines = list(raw ?? [], 6, "change");
  if (!lines.length) refuse("change", "change");
  return lines.map((l, i) => {
    object(l, `change.${i}`);
    const out = {
      field: oneOf(l.field, Object.keys(CHANGE_FIELDS), `change.${i}.field`),
      now: str(l.now ?? "", 80, `change.${i}.now`, { code: "change" }),
      want: str(l.want, 80, `change.${i}.want`, { empty: false, code: "change" }),
      why: str(l.why ?? "", 160, `change.${i}.why`, { lines: true, code: "change" }),
    };
    for (const [k, v] of Object.entries(out)) {
      const why = k === "field" ? null : personal(v);
      if (why) refuse(why, `change.${i}.${k}`);
    }
    return out;
  });
}

/**
 * [PLZ-01] How far people trust a post, 0 to 100, from its 맞아요·달라요: the share who agree, held back
 * until a few have voted (3), so one vote never reads as 100.
 */
export function postTrust(p) {
  const n = (p.agree ?? 0) + (p.disagree ?? 0);
  return { trust: n ? Math.round((100 * (p.agree ?? 0)) / Math.max(n, 3)) : 0 };
}

/**
 * [PLZ-01] 맞아요 (ok true), 달라요 (false) or taking the vote back (null) on someone's post. On a
 * proposal it is also a vote on the proposed setup, so a proposal people agree with is adopted like any rule.
 */
async function postVote(request, env, id) {
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (body.ok !== null && typeof body.ok !== "boolean") refuse("ok", "ok");
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `vote|${net.now}`, LIMITS.votesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
  const dev = await deviceHash(device);
  const p = await one(env, "SELECT * FROM posts WHERE id = ?", id);
  if (!p || p.status !== "shown") return json({ error: "not_found" }, 404);
  if (p.device === dev) return json({ error: "own" }, 400);
  const before = await one(env, "SELECT ok FROM post_votes WHERE post_id = ? AND device = ?", id, dev);
  const stmts = [];
  if (before) stmts.push(stmt(env, `UPDATE posts SET ${before.ok ? "agree = agree - 1" : "disagree = disagree - 1"} WHERE id = ?`, id));
  if (body.ok === null) {
    stmts.push(stmt(env, "DELETE FROM post_votes WHERE post_id = ? AND device = ?", id, dev));
  } else {
    stmts.push(stmt(env, `INSERT INTO post_votes (post_id, device, ok, at) VALUES (?, ?, ?, ?)
      ON CONFLICT (post_id, device) DO UPDATE SET ok = excluded.ok, at = excluded.at`, id, dev, body.ok ? 1 : 0, now));
    stmts.push(stmt(env, `UPDATE posts SET ${body.ok ? "agree = agree + 1" : "disagree = disagree + 1"} WHERE id = ?`, id));
  }
  await batch(env, stmts);
  if (p.report_id) {
    const r = await one(env, "SELECT company_id, grp, status FROM reports WHERE id = ?", p.report_id);
    if (r) {
      if (body.ok === null) {
        await run(env, "DELETE FROM votes WHERE company_id = ? AND grp = ? AND device = ?", r.company_id, r.grp, dev);
      } else {
        await forgetNetworks(env, now);
        const cluster = await clusterFor(env, r.company_id, net);
        await run(env, `INSERT INTO votes (company_id, grp, device, cluster, ok, verified, why, net, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT (company_id, grp, device) DO UPDATE SET cluster = excluded.cluster, ok = excluded.ok, verified = excluded.verified,
            why = excluded.why, net = excluded.net, at = excluded.at`, r.company_id, r.grp, dev, cluster, body.ok ? 1 : 0,
        body.ok && body.verified === true && proofCounts(readProof(body.proof)) ? 1 : 0, body.ok ? null : "wrong", net.now, now);
        if (body.ok && r.status === "pending") await settle(env, r.company_id, r.grp, now);
      }
    }
  }
  const after = await one(env, "SELECT agree, disagree FROM posts WHERE id = ?", id);
  return json({ agree: after.agree, disagree: after.disagree, ...postTrust(after), myVote: body.ok });
}

/** [PLZ-08] This phone uses a rule set (applied it in the app), or stopped: 쓰는 사람, counted once. */
async function useRule(request, env, on) {
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (typeof body.company !== "string" || !/^[A-Za-z0-9]{12}$/.test(body.company)) refuse("company", "company");
  if (typeof body.setup !== "string" || !/^[0-9a-f]{16}$/.test(body.setup)) refuse("setup", "setup");
  const dev = await deviceHash(device);
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `vote|${net.now}`, LIMITS.votesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
  const c = await talkable(env, body.company, body.setup);
  if (!c) return json({ error: "not_found" }, 404);
  if (on) await run(env, "INSERT OR IGNORE INTO uses (company_id, grp, device, at) VALUES (?, ?, ?, ?)", c.id, body.setup, dev, now);
  else await run(env, "DELETE FROM uses WHERE company_id = ? AND grp = ? AND device = ?", c.id, body.setup, dev);
  // [PLAZA-07] Applying a company's setup is joining it (결정 D79), from an app before 1.26 too.
  const member = on && c.kind !== "topic" ? await joinCompany(env, dev, c.id, calOf(body.cal), now) : null;
  const n = await one(env, "SELECT COUNT(*) AS n FROM uses WHERE company_id = ? AND grp = ?", c.id, body.setup);
  return json({ uses: n.n, using: on, ...(member ? { member } : {}) });
}

/**
 * [PLZ-08] The name a phone is shown by on the plaza (2~12 letters, no contact or amount, not taken by
 * another phone, not 관리자·개발자·운영자), or none with an empty name.
 */
async function setNick(request, env) {
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  const dev = await deviceHash(device);
  const nick = str(body.nick ?? "", 12, "nick", { code: "nick" }).trim();
  if (!nick) {
    await run(env, "DELETE FROM nicks WHERE device = ?", dev);
    return json({ nick: null });
  }
  const norm = normName(nick);
  if ([...nick].length < 2 || norm.length < 2 || /관리자|개발자|운영자|밤낮|admin/i.test(nick)) refuse("nick", "nick");
  const why = personal(nick);
  if (why) refuse(why, "nick");
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `nick|${net.day}`, 20, DAY, now))) return json({ error: "rate_limited" }, 429);
  const taken = await one(env, "SELECT device FROM nicks WHERE norm = ?", norm);
  if (taken && taken.device !== dev) return json({ error: "taken", field: "nick" }, 409);
  await run(env, `INSERT INTO nicks (device, nick, norm, at) VALUES (?, ?, ?, ?)
    ON CONFLICT (device) DO UPDATE SET nick = excluded.nick, norm = excluded.norm, at = excluded.at`, dev, nick, norm, now);
  return json({ nick });
}

/** [PLZ-12] The plazas and how much is in each: adopted rule sets and ones being voted on, per job. */
async function plazas(env) {
  // SP1 1.30 [SPD-09] Its three counts together.
  const [rows, voting, rates] = await Promise.all([
    all(env, `SELECT c.job,
      COUNT(DISTINCT CASE WHEN r.id IS NOT NULL THEN r.id END) AS adopted
    FROM companies c LEFT JOIN rules r ON r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0
    WHERE c.hidden = 0 AND c.merged_into IS NULL GROUP BY c.job`),
    all(env, `SELECT c.job, COUNT(DISTINCT p.company_id || p.grp) AS n FROM reports p JOIN companies c ON c.id = p.company_id
    WHERE p.status = 'pending' AND c.hidden = 0 GROUP BY c.job`),
    all(env, "SELECT job, COUNT(*) AS n FROM rates WHERE status = 'adopted' GROUP BY job"),
  ]);
  const count = (list, job, key = "n") => list.find((r) => r.job === job)?.[key] ?? 0;
  return {
    jobs: Object.entries(JOBS).map(([key, name]) => ({ key, name, adopted: count(rows, key, "adopted"), voting: count(voting, key), rates: count(rates, key) })),
    regions: Object.entries(REGIONS).map(([key, name]) => ({ key, name })),
    changeFields: Object.entries(CHANGE_FIELDS).map(([key, name]) => ({ key, name })),
    rateUnits: Object.entries(RATE_UNITS).map(([key, u]) => ({ key, name: u.name })),
  };
}

/**
 * [PLZ-04][PLZ-05] Every version a department's rule went through, newest first: how it was adopted
 * (people, an amendment, the developer), when, what changed from the one before, and whether it was
 * taken down. Adopted versions never disappear; this is how the rule grew.
 */
async function history(env, companyId, url, request) {
  // SP1 1.30 [SPD-09] Where and who together, then the department and its versions (with each one's senders and proposed post)
  // together: two rounds, not two per version.
  const grp = url.searchParams.get("setup") ?? "";
  const [c, asked] = await Promise.all([follow(env, companyId), viewer(request, env, companyId)]);
  if (!c || c.hidden) return json({ error: "not_found" }, 404);
  // [PLAZA-08] What each version paid, only for a phone that joined the company.
  const who = c.id === companyId ? asked : await viewer(request, env, c.id);
  const words = (setup) => (who.member ? describe(setup) : shiftLines(describe(setup)));
  const deptOf = `SELECT dept_norm FROM rules WHERE company_id = ?1 AND grp = ?2
    UNION ALL SELECT dept_norm FROM reports WHERE company_id = ?1 AND grp = ?2 LIMIT 1`;
  const [dept, rules] = await Promise.all([one(env, deptOf, c.id, grp), all(env, `SELECT r.*,
      (SELECT p.post_id FROM reports p WHERE p.rule_id = r.id AND p.post_id IS NOT NULL LIMIT 1) AS sp_post,
      (SELECT COUNT(*) FROM reports s WHERE s.rule_id = r.id) AS sp_senders
    FROM rules r WHERE r.company_id = ?1 AND r.dept_norm = (${deptOf}) ORDER BY r.version`, c.id, grp)]);
  if (!dept) return json({ error: "not_found" }, 404);
  const versions = [];
  let before = null;
  for (const r of rules) {
    const setup = JSON.parse(r.setup);
    versions.push({
      version: r.version, setup: r.grp, source: r.source, adoptedAt: iso(r.adopted_at),
      state: r.withdrawn ? "withdrawn" : r.superseded ? "superseded" : "active",
      senders: r.sp_senders, post: r.sp_post ?? null,
      // [PLAZA-05] What the version was, so the app can compare it and put it back on a phone.
      body: who.member ? shared(r.setup, c.name) : shiftsOnly(shared(r.setup, c.name)),
      changes: before ? diffLines(words(before), words(setup)) : [],
    });
    before = setup;
  }
  return json({ company: { id: c.id, name: c.name }, versions: versions.reverse(), member: who.member });
}

// ---- [PLZ-14] 임금표: market rates for a job and region ----

/**
 * What a rate is per, and the amounts it may be: tight enough to catch a 0 too many or too few, wide
 * enough for 기능공 일당 in 서울 or a 전문의 월급. 최저시급 is well above the hour floor.
 */
const RATE_UNITS = {
  day: { name: "일당", min: 50000, max: 1000000 },
  hour: { name: "시급", min: 5000, max: 200000 },
  month: { name: "월급", min: 500000, max: 20000000 },
};

/** The year it is in Korea, which a rate is for. */
const koreaYear = (now) => new Date(now + 9 * HOUR).getUTCFullYear();

/** A rate further than this from the adopted middle of its kind waits for an admin however many agree. */
const RATE_FAR = 2;

async function rateCluster(env, net) {
  const row = await one(env, `SELECT cluster FROM rates WHERE net IN (?1, ?2)
    UNION ALL SELECT cluster FROM rate_votes WHERE net IN (?1, ?2) LIMIT 1`, net.now, net.prev);
  return row?.cluster ?? randomId(12);
}

/** The middle of sorted amounts (the lower of the two middle ones for an even count). */
const middle = (sorted) => sorted[Math.floor((sorted.length - 1) / 2)];

function rateView(r, me, myVotes, nicks) {
  return {
    id: r.id, job: r.job, region: r.region, trade: r.trade, unit: r.unit, year: r.year, amount: r.amount, note: r.note,
    status: r.status, agree: r.agree, disagree: r.disagree, ...postTrust(r), at: iso(r.created_at),
    mine: r.device === me, nick: nicks.get(r.device) ?? null,
    ...(myVotes.has(r.id) ? { myVote: myVotes.get(r.id) } : {}),
    // [COMM-11] Why the sender's own entry waits: far from the others, so a person looks first.
    ...(r.device === me && r.status === "review" ? { waitingWhy: "far" } : {}),
  };
}

/**
 * [PLZ-14][PLZ-15] The rates of one job (and region, trade, unit): every rate people sent and its
 * 맞아요·달라요, newest first, and for each kind (region, trade, unit, year) the adopted ones' low,
 * middle and high, which is what the app shows as 광장 시세. With X-Device the caller's are marked.
 */
async function rates(request, env, url) {
  const p = url.searchParams;
  const job = Object.hasOwn(JOBS, p.get("job") ?? "") ? p.get("job") : "";
  const region = Object.hasOwn(REGIONS, p.get("region") ?? "") ? p.get("region") : "";
  const unit = Object.hasOwn(RATE_UNITS, p.get("unit") ?? "") ? p.get("unit") : "";
  const trade = normName(p.get("trade")).slice(0, 20);
  const device = deviceId(request.headers.get("x-device"));
  const me = device ? await deviceHash(device) : null;
  const where = `status IN ('pending', 'adopted', 'review') AND (?1 = '' OR job = ?1) AND (?2 = '' OR region = ?2)
    AND (?3 = '' OR unit = ?3) AND (?4 = '' OR trade_norm = ?4)`;
  // SP1 1.30 [SPD-09] The four reads together.
  const [rows, adopted, voteRows, nickRows] = await Promise.all([
    all(env, `SELECT * FROM rates WHERE ${where} ORDER BY created_at DESC LIMIT 200`, job, region, unit, trade),
    all(env, `SELECT job, region, trade, trade_norm, unit, year, amount FROM rates WHERE ${where} AND status = 'adopted'
    ORDER BY amount LIMIT 2000`, job, region, unit, trade),
    me ? all(env, "SELECT rate_id, ok FROM rate_votes WHERE device = ?", me) : [],
    all(env, `SELECT device, nick FROM nicks WHERE device IN (SELECT device FROM rates WHERE ${where})`, job, region, unit, trade),
  ]);
  const kinds = new Map();
  for (const r of adopted) {
    const key = [r.job, r.region, r.trade_norm, r.unit, r.year].join("|");
    if (!kinds.has(key)) kinds.set(key, { job: r.job, region: r.region, trade: r.trade, unit: r.unit, year: r.year, amounts: [] });
    kinds.get(key).amounts.push(r.amount);
  }
  const myVotes = new Map(voteRows.map((v) => [v.rate_id, v.ok === 1]));
  const nicks = new Map(nickRows.map((n) => [n.device, n.nick]));
  return json({
    ...(job ? { job } : {}), ...(region ? { region } : {}), ...(unit ? { unit } : {}),
    groups: [...kinds.values()].map(({ amounts, ...k }) => ({ ...k, n: amounts.length, low: amounts[0], mid: middle(amounts), high: amounts[amounts.length - 1] }))
      .sort((a, b) => b.year - a.year || b.n - a.n),
    entries: rows.map((r) => rateView(r, me, myVotes, nicks)),
  });
}

function readRate(body, now) {
  const year = koreaYear(now);
  const unit = oneOf(body.unit, Object.keys(RATE_UNITS), "unit");
  const u = RATE_UNITS[unit];
  const out = {
    job: oneOf(body.job, Object.keys(JOBS), "job"),
    region: oneOf(body.region ?? "", ["", ...Object.keys(REGIONS)], "region"),
    trade: str(body.trade, 20, "trade", { empty: false, code: "trade" }).trim(),
    unit,
    year: int(body.year ?? year, year - 1, year + 1, "year"),
    amount: int(body.amount, u.min, u.max, "amount"),
    note: str(body.note ?? "", 100, "note", { code: "note" }).trim(),
  };
  if ([...out.trade].length < 2) refuse("trade", "trade");
  for (const k of ["trade", "note"]) {
    const why = personal(out[k]);
    if (why) refuse(why, k);
  }
  return out;
}

/**
 * [PLZ-14] A market rate: what a trade is paid in a region per day, hour or month, as heard or as the
 * sender is paid, never with a name or a pay slip. One per phone for each kind; sending again changes it
 * while it is not adopted.
 */
async function addRate(request, env) {
  if (await paused(env, "PLAZA_WRITE")) return json({ error: "paused" }, 503);
  const body = await readJson(request, 2048);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  const now = Date.now();
  const r = readRate(body, now);
  const dev = await deviceHash(device);
  const muted = await one(env, "SELECT until FROM muted WHERE device = ? AND until > ?", dev, now);
  if (muted) return json({ error: "muted", until: iso(muted.until) }, 403);
  const net = await network(request, env, now);
  if (!(await allow(env, `rate|${dev}`, LIMITS.reportsPerDay, DAY, now)) ||
      !(await allow(env, `rate|${net.day}`, LIMITS.reportsPerNetworkDay, DAY, now))) {
    return json({ error: "rate_limited" }, 429);
  }
  const norm = normName(r.trade);
  const before = await one(env, `SELECT id, status FROM rates WHERE device = ? AND job = ? AND region = ? AND trade_norm = ? AND unit = ? AND year = ?
    AND status IN ('pending', 'adopted', 'review')`, dev, r.job, r.region, norm, r.unit, r.year);
  if (before && before.status !== "pending") return json({ error: "adopted", id: before.id }, 409);
  let id = before?.id;
  if (id) {
    // What others agreed to was the old amount: their votes go with it.
    await batch(env, [
      stmt(env, "UPDATE rates SET trade = ?, amount = ?, note = ?, agree = 0, disagree = 0, created_at = ? WHERE id = ?", r.trade, r.amount, r.note, now, id),
      stmt(env, "DELETE FROM rate_votes WHERE rate_id = ?", id),
    ]);
  } else {
    id = randomId(22);
    await run(env, `INSERT INTO rates (id, job, region, trade, trade_norm, unit, year, amount, note, device, cluster, net, status, created_at, decided_at, agree, disagree)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, NULL, 0, 0)`,
    id, r.job, r.region, r.trade, norm, r.unit, r.year, r.amount, r.note, dev, await rateCluster(env, net), net.now, now);
  }
  return json({ id, status: "pending", at: iso(now) }, before ? 200 : 201);
}

/** [PLZ-14] 맞아요 (ok true), 달라요 (false) or taking it back (null) on someone's rate. */
async function rateVote(request, env, id) {
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (body.ok !== null && typeof body.ok !== "boolean") refuse("ok", "ok");
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `vote|${net.now}`, LIMITS.votesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
  const dev = await deviceHash(device);
  const r = await one(env, "SELECT * FROM rates WHERE id = ?", id);
  if (!r || !["pending", "adopted", "review"].includes(r.status)) return json({ error: "not_found" }, 404);
  if (r.device === dev) return json({ error: "own" }, 400);
  const before = await one(env, "SELECT ok FROM rate_votes WHERE rate_id = ? AND device = ?", id, dev);
  const stmts = [];
  if (before) stmts.push(stmt(env, `UPDATE rates SET ${before.ok ? "agree = agree - 1" : "disagree = disagree - 1"} WHERE id = ?`, id));
  if (body.ok === null) {
    stmts.push(stmt(env, "DELETE FROM rate_votes WHERE rate_id = ? AND device = ?", id, dev));
  } else {
    stmts.push(stmt(env, `INSERT INTO rate_votes (rate_id, device, ok, cluster, net, at) VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT (rate_id, device) DO UPDATE SET ok = excluded.ok, cluster = excluded.cluster, net = excluded.net, at = excluded.at`,
    id, dev, body.ok ? 1 : 0, await rateCluster(env, net), net.now, now));
    stmts.push(stmt(env, `UPDATE rates SET ${body.ok ? "agree = agree + 1" : "disagree = disagree + 1"} WHERE id = ?`, id));
  }
  await batch(env, stmts);
  if (body.ok && r.status === "pending") await settleRate(env, id, now);
  const after = await one(env, "SELECT agree, disagree, status FROM rates WHERE id = ?", id);
  return json({ agree: after.agree, disagree: after.disagree, ...postTrust(after), status: after.status, myVote: body.ok });
}

/**
 * [PLZ-14] Adopts a rate the way a rule is (결정 D32): enough different people from more than one
 * network agree, and few enough say 달라요. A rate far from what is already adopted for its kind waits
 * for an admin ('review'). The sender earns the reward, given at once like a rule's (결정 D33).
 */
async function settleRate(env, id, now) {
  const set = await settings(env);
  const r = await one(env, "SELECT * FROM rates WHERE id = ?", id);
  if (!r || r.status !== "pending") return;
  const votes = await all(env, "SELECT device, ok, cluster FROM rate_votes WHERE rate_id = ?", id);
  const s = tally([{ device: r.device, points: 1, cluster: r.cluster }, ...votes.filter((v) => v.ok).map((v) => ({ ...v, points: 1 }))],
    votes.filter((v) => !v.ok).map((v) => v.device));
  if (s.persons < set.adoptPersons || s.networks < 2) return;
  if (s.persons * 100 < set.adoptAgree * (s.persons + s.bad)) return;
  const same = (await all(env, `SELECT amount FROM rates WHERE job = ? AND region = ? AND trade_norm = ? AND unit = ? AND year = ? AND status = 'adopted'
    ORDER BY amount`, r.job, r.region, r.trade_norm, r.unit, r.year)).map((x) => x.amount);
  const mid = same.length >= 3 ? middle(same) : null;
  if (mid && (r.amount > mid * RATE_FAR || r.amount * RATE_FAR < mid)) {
    await run(env, "UPDATE rates SET status = 'review' WHERE id = ?", id);
    return;
  }
  await adoptRate(env, r, set, now);
}

async function adoptRate(env, r, set, now) {
  await run(env, "UPDATE rates SET status = 'adopted', decided_at = ? WHERE id = ?", now, r.id);
  if (set.rewardMonths <= 0 || r.device.startsWith("gone")) return;
  await run(env, `INSERT OR IGNORE INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at)
    VALUES (?, ?, 'rate', ?, ?, 'owed', '', ?, NULL)`, `w${r.id}`, r.id, r.device, set.rewardMonths, now);
  if (set.autoReward) await payEach(env, [{ id: `w${r.id}`, device: r.device, months: set.rewardMonths }], set, now);
}

/** [PLZ-14] Takes my rate back; an adopted one stays as everyone's, only my tie to it goes (결정 D34). */
async function takeRateBack(request, env, id) {
  const device = deviceId(request.headers.get("x-device"));
  const r = await one(env, "SELECT device, status FROM rates WHERE id = ?", id);
  if (!r) return json({ error: "not_found" }, 404);
  if (!device || (await deviceHash(device)) !== r.device) return json({ error: "forbidden" }, 403);
  if (r.status === "adopted") {
    await batch(env, [
      stmt(env, "DELETE FROM rewards WHERE report_id = ? AND status = 'owed'", id),
      stmt(env, "UPDATE rates SET device = ? WHERE id = ?", `gone${randomId(12)}`, id),
    ]);
    return json({ kept: true, text: "채택된 시세는 모두가 쓰는 정보라 남아요. 보낸 분과의 연결만 지웠어요." });
  }
  await batch(env, [
    stmt(env, "DELETE FROM rate_votes WHERE rate_id = ?", id),
    stmt(env, "DELETE FROM rewards WHERE report_id = ?", id),
    stmt(env, "DELETE FROM rates WHERE id = ?", id),
  ]);
  return new Response(null, { status: 204 });
}

/**
 * [PLZ-08] 내 활동: what this phone sent to the plaza and where it stands, its proposals with their
 * 맞아요·달라요, its rates, rewards and the name it is shown by. Only for the phone itself (X-Device).
 */
async function activity(request, env) {
  const dev = await installOf(request);
  // SP1 1.30 [SPD-09] Everything of this phone together, then every report's score in one round (before 1.30 two reads per
  // report one after another: a hundred rounds for someone who sent fifty).
  const [{ adoptPoints }, reports, posts, mine, rewardRows, nick] = await Promise.all([
    settings(env),
    all(env, `SELECT r.id, r.company_id, r.grp, r.dept, r.status, r.created_at, c.name, u.version, u.withdrawn
    FROM reports r JOIN companies c ON c.id = r.company_id LEFT JOIN rules u ON u.id = r.rule_id
    WHERE r.device = ? ORDER BY r.created_at DESC LIMIT 50`, dev),
    all(env, `SELECT p.id, p.company_id, p.grp, p.kind, p.agree, p.disagree, p.body, p.created_at, c.name
    FROM posts p JOIN companies c ON c.id = p.company_id WHERE p.device = ? AND p.status = 'shown' AND p.kind = 'proposal'
    ORDER BY p.created_at DESC LIMIT 30`, dev),
    all(env, "SELECT * FROM rates WHERE device = ? ORDER BY created_at DESC LIMIT 50", dev),
    all(env, "SELECT months, status, rule_id, created_at, paid_at FROM rewards WHERE device = ? ORDER BY created_at DESC LIMIT 50", dev),
    one(env, "SELECT nick FROM nicks WHERE device = ?", dev),
  ]);
  const facts = await readStates(env, reports.map((r) => ({ company: r.company_id, grp: r.grp })));
  const sent = reports.map((r) => {
    const s = facts.state(r.company_id, r.grp, adoptPoints, false);
    return { id: r.id, company: { id: r.company_id, name: r.name }, dept: r.dept, setup: r.grp,
      status: r.status === "adopted" && r.withdrawn ? "withdrawn" : r.status, version: r.version ?? null,
      persons: s.persons, bad: s.bad, threshold: adoptPoints, uses: s.uses, at: iso(r.created_at) };
  });
  return json({
    nick: nick?.nick ?? null,
    reports: sent,
    proposals: posts.map((p) => ({ id: p.id, company: { id: p.company_id, name: p.name }, setup: p.grp, body: p.body,
      agree: p.agree, disagree: p.disagree, ...postTrust(p), at: iso(p.created_at) })),
    rates: mine.map((r) => rateView(r, dev, new Map(), new Map())),
    rewards: rewardRows.map((w) => ({ months: w.months, status: w.status, why: rewardWhy(w.rule_id),
      at: iso(w.created_at), paidAt: w.paid_at ? iso(w.paid_at) : null })),
  });
}

async function takePostBack(request, env, id) {
  const device = deviceId(request.headers.get("x-device"));
  const p = await one(env, "SELECT device FROM posts WHERE id = ?", id);
  if (!p) return json({ error: "not_found" }, 404);
  if (!device || (await deviceHash(device)) !== p.device) return json({ error: "forbidden" }, 403);
  await batch(env, [stmt(env, "DELETE FROM post_flags WHERE post_id = ?", id), stmt(env, "DELETE FROM posts WHERE id = ?", id)]);
  return new Response(null, { status: 204 });
}

/**
 * [COMM-06] 신고. Each install once per post; enough of them hide it until the developer looks, unless
 * the developer already looked and kept it.
 */
async function flag(request, env, id) {
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  const reason = Object.hasOwn(FLAGS, body.reason) ? body.reason : "abuse";
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `vote|${net.now}`, LIMITS.votesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
  const dev = await deviceHash(device);
  const p = await one(env, "SELECT * FROM posts WHERE id = ?", id);
  if (!p || p.status !== "shown") return json({ error: "not_found" }, 404);
  if (p.device === dev) return json({ error: "own" }, 400);
  const added = await run(env, "INSERT OR IGNORE INTO post_flags (post_id, device, reason, at) VALUES (?, ?, ?, ?)", id, dev, reason, now);
  if (added.meta.changes) {
    await run(env, `UPDATE posts SET flags = flags + 1,
        status = CASE WHEN kept = 0 AND flags + 1 >= ?2 THEN 'hidden' ELSE status END,
        hide_reason = CASE WHEN kept = 0 AND flags + 1 >= ?2 THEN 'flags' ELSE hide_reason END,
        hidden_at = CASE WHEN kept = 0 AND flags + 1 >= ?2 THEN ?3 ELSE hidden_at END
      WHERE id = ?1`, id, LIMITS.flagsToHide, now);
    const after = await one(env, "SELECT status FROM posts WHERE id = ?", id);
    if (after.status === "hidden") {
      await batch(env, plazaResults(env, p, "hidden", "hidden", now));
      await audit(env, now, "post_autohide", id, `${p.company_id}/${p.grp}`);
      await notifyAdmins(env, "flagged", { title: "신고로 가려진 글", body: "신고가 쌓여 글 하나가 가려졌어요", url: "/admin/#/community" }, now);
    }
  }
  return json({ ok: true });
}

// ---- questions from the app [ADMIN-02] ----
//
// 설정 > 문의하기 sends here instead of opening a mail app, and the developer answers in /admin; the
// answer shows in the app. Like a report it carries no amount, contact or name: those are refused, not
// cleaned. What goes with it (app version, phone model, Android, the alarm check if the user ticks it)
// is shown in the app before sending. Only the install's hash is kept, so only that phone can write to,
// or delete, the thread, and a thread with nothing new for 180 days is deleted. A question sent as
// public is also in 모두의 문의 (below), without anything that went with it.

const INQUIRY = {
  // [INQ-01] Since 1.14.1 suggestions, anything else and a company's way the app cannot set come here too.
  kinds: ["question", "bug", "idea", "company", "other"],
  text: 2000,
  diag: 6000,
  pay: 3000,
  setup: 3000,
  bytes: 16 * 1024,
  perDay: 5,
  perNetworkDay: 20,
  messagesPerHour: 20,
  // [INQ-04] A question waiting on an admin longer than this is 놓친 문의.
  lateMs: DAY,
  // Each side of a thread; a longer back and forth is better as a new question.
  messages: 40,
  keepMs: 180 * DAY,
};

/** What the app may send with a question, as the developer app shows it: label, field, longest. */
const INQUIRY_INFO = [
  ["앱", "app", 40],
  ["기기", "model", 80],
  ["Android", "android", 40],
  ["알람 점검", "diag", INQUIRY.diag],
  // 급여 > 계산이 이상해요: the month's rules and each line's hours and rate, never an amount.
  ["급여 계산", "pay", INQUIRY.pay],
  // [INQ-01] 회사 방식 알려 주기: the kind's settings in words, never an amount.
  ["회사 설정", "setup", INQUIRY.setup],
];

/** [INQ-01] A company's way names the workplace: it stays with the developer, whatever the app asks. */
const PRIVATE_KINDS = ["company"];

// ---- sorting questions without reading each one [ADMIN-02] ----
//
// Every question gets a topic and an urgency from its words, its kind and what came with it, worked
// out when the list is read, so better words sort old questions too and nothing new is stored. The
// developer filters by topic and sees the urgent ones first instead of opening each to sort it.

/** Topic, label and the words that point to it; the most matched topic wins, the first on a tie. */
// [INQ-05] The app sorts with the same table: core/ops/InquiryTopics.kt (a test keeps them alike).
export const INQUIRY_TOPICS = [
  ["alarm", "알람", /알람|울리|울려|울림|소리|진동|깨워|스누즈|다시\s*울/g],
  ["pay", "급여 계산", /급여|월급|시급|일당|연봉|수당|가산|명세서|통상임금|주휴|연차|퇴직|세금|4대\s*보험|계산/g],
  ["schedule", "근무표·달력", /근무표|달력|캘린더|교대|패턴|근무\s*유형|대근|휴무|비번|야간|주간|오프|일정|메모/g],
  ["share", "공유", /공유|링크|상대방|가족|동기화|받은\s*사람|보낸\s*사람/g],
  ["company", "회사 설정", /회사|부서|규칙|투표|토론|채택/g],
  ["backup", "백업·기기 이동", /백업|복원|새\s*(폰|휴대폰|기기)|기기\s*변경|옮기|가져오기|내보내기/g],
  ["billing", "결제", /결제|구독|프리미엄|환불|구매|광고|유료|무료/g],
  ["app", "앱 동작", /멈춰|멈춤|멈추|꺼져|꺼짐|튕|강제\s*종료|오류|에러|버그|안\s*열|안\s*켜|느려|느림|로딩|하얀\s*화면|검은\s*화면|위젯|워치|잠금\s*화면|배터리|업데이트/g],
];
const TOPIC_NAMES = Object.fromEntries([...INQUIRY_TOPICS.map(([key, label]) => [key, label]), ["etc", "기타"]]);

/** Words that make a problem urgent: an alarm that did not ring, an app that will not open, money paid. */
const URGENT = /안\s*울|못\s*(들|일어)|놓쳤|늦게\s*울|지각|안\s*열|안\s*켜|튕|강제\s*종료|꺼져|데이터.{0,6}(사라|없어|날아)|사라졌|환불|결제.{0,6}(안|됐|두\s*번)/;

/** What the list and a thread show for sorting: topic, its label and how soon it needs an answer. */
function triage(kind, text, info) {
  const t = String(text ?? "");
  let topic = "etc";
  let best = 0;
  for (const [key, , re] of INQUIRY_TOPICS) {
    const n = t.match(re)?.length ?? 0;
    if (n > best) [topic, best] = [key, n];
  }
  // What came with it says more than any word: 계산이 이상해요 sends the pay check, the alarm form its check.
  if (kind === "company" || info?.setup) topic = "company";
  else if (info?.pay) topic = "pay";
  else if (best === 0 && info?.diag) topic = "alarm";
  const urgency = ["idea", "company", "other"].includes(kind) ? "low" : URGENT.test(t) ? "urgent" : "normal";
  return { topic, topicName: TOPIC_NAMES[topic], urgency };
}

async function inquiries(request, env, path) {
  const method = request.method;
  const device = deviceId(request.headers.get("x-device"));
  if (path === "/api/inquiries" && method === "POST") return ask(request, env);
  if (path === "/api/inquiries/public" && method === "GET") return publicList(env, new URL(request.url).searchParams, device, request.headers.get("x-hide"));
  const pub = path.match(/^\/api\/inquiries\/public\/([A-Za-z0-9]{22})$/);
  if (pub && method === "GET") return publicThread(env, pub[1], device, request.headers.get("x-hide"));
  const mark = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})\/(same|flag)$/);
  if (mark && method === "POST") return inquiryMark(request, env, mark[1], mark[2]);
  if (!device) return json({ error: "device", field: "device" }, 400);
  const dev = await deviceHash(device);
  if (path === "/api/inquiries" && method === "GET") {
    const rows = await all(env, `SELECT i.*, (SELECT COUNT(*) FROM inquiry_photos p WHERE p.inquiry_id = i.id) AS photos
      FROM inquiries i WHERE i.device = ? ORDER BY i.updated_at DESC LIMIT 50`, dev);
    const out = [];
    for (const r of rows) out.push(await inquiryView(env, r));
    return json({ inquiries: out });
  }
  const rated = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})\/rating$/);
  if (rated && method === "POST") return inquiryRating(request, env, rated[1], dev);
  // [SRV-06] A photo sent with one's own question; someone else's reads as missing.
  const photo = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})\/photos\/([12])$/);
  if (photo && method === "GET") {
    const owner = await one(env, "SELECT device FROM inquiries WHERE id = ?", photo[1]);
    return owner && owner.device === dev ? photoFile(env, photo[1], Number(photo[2])) : json({ error: "not_found" }, 404);
  }
  const m = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})(\/messages)?$/);
  if (!m) return json({ error: "not_found" }, 404);
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", m[1]);
  // Someone else's question reads as missing, so ids cannot be tried out.
  if (!row || row.device !== dev) return json({ error: "not_found" }, 404);
  if (!m[2] && method === "GET") return json(await inquiryView(env, row));
  if (!m[2] && method === "DELETE") {
    await batch(env, dropInquiries(env, "id = ?", row.id));
    return new Response(null, { status: 204 });
  }
  if (m[2] && method === "POST") {
    const body = await readJson(request, INQUIRY.bytes);
    const text = inquiryText(body.text);
    const now = Date.now();
    if (!(await allow(env, `inquiry_msg|${dev}`, INQUIRY.messagesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
    const count = await one(env, "SELECT COUNT(*) AS n FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'user'", row.id);
    if (count.n >= INQUIRY.messages) return json({ error: "too_many", field: "text" }, 409);
    await batch(env, [
      stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)", row.id, text, now),
      stmt(env, "UPDATE inquiries SET status = 'open', updated_at = ? WHERE id = ?", now, row.id),
    ]);
    return json(await inquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", row.id)), 201);
  }
  return json({ error: "not_found" }, 404);
}

/** [INQ-03] 도움이 됐어요 (good: true) or 아쉬워요 (false) on the answers to one's own question; null takes it back. */
async function inquiryRating(request, env, id, dev) {
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", id);
  if (!row || row.device !== dev) return json({ error: "not_found" }, 404);
  const body = await readJson(request, 1024);
  if (body.good !== true && body.good !== false && body.good !== null) refuse("good", "good");
  const answered = await one(env, "SELECT COUNT(*) AS n FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'admin'", id);
  if (!answered.n) return json({ error: "not_answered", field: "good" }, 409);
  await run(env, "UPDATE inquiries SET rating = ? WHERE id = ?", body.good === null ? null : body.good ? 1 : -1, id);
  return json(await inquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", id)));
}

/** A new question: what the user typed, and what they agreed to send with it. */
async function ask(request, env) {
  // [SRV-06] Room for two photos as base64 next to the words.
  const body = await readJson(request, INQUIRY.bytes + PHOTO.max * Math.ceil((PHOTO.bytes * 4) / 3) + 1024);
  if (body.v !== 1) refuse("version", "v");
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (!INQUIRY.kinds.includes(body.kind)) refuse("kind", "kind");
  const text = inquiryText(body.text);
  const info = inquiryInfo(body.info);
  const now = Date.now();
  const net = await network(request, env, now);
  const dev = await deviceHash(device);
  if (!(await allow(env, `inquiry|${dev}`, INQUIRY.perDay, DAY, now)) ||
      !(await allow(env, `inquiry|${net.day}`, INQUIRY.perNetworkDay, DAY, now))) {
    return json({ error: "rate_limited" }, 429);
  }
  const photos = await readPhotos(env, body.photos);
  const id = randomId(22);
  await batch(env, [
    // [SRV-06] Photos stay with the developer even on a public question.
    ...photos.list.map((data, i) => stmt(env, "INSERT INTO inquiry_photos (inquiry_id, n, data, bytes, at) VALUES (?, ?, ?, ?, ?)",
      id, i + 1, data.base64, data.bytes, now)),
    // Only an app that says so on its form sends public: true; a question from an older app stays the developer's.
    stmt(env, "INSERT INTO inquiries (id, device, kind, info, status, public, created_at, updated_at) VALUES (?, ?, ?, ?, 'open', ?, ?, ?)",
      id, dev, body.kind, JSON.stringify(info), body.public === true && !PRIVATE_KINDS.includes(body.kind) ? 1 : 0, now, now),
    stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)", id, text, now),
    // Threads with nothing new for half a year go, answers, notes and 개발사항 links and all.
    ...dropInquiries(env, "updated_at < ?", now - INQUIRY.keepMs),
    stmt(env, "DELETE FROM counters WHERE expires < ?", now),
  ]);
  // [ADMIN-19] Admins who asked for it hear of a new question (a bug or a problem counts as urgent); nothing
  // happens without VAPID keys, and a push that fails never fails the question.
  await notifyAdmins(env, ["bug", "problem"].includes(body.kind) ? "urgent" : "inquiry", {
    title: "새 문의",
    body: `${({ bug: "문제 신고", problem: "문제 신고", question: "질문", idea: "건의·제안", company: "회사 방식" })[body.kind] ?? "문의"} 1건`,
    url: "/admin/#/inquiries",
  }, now);
  const view = await inquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", id));
  // Photos the server could not take this time (switched off or full): the question went without them.
  return json({ ...view, ...(photos.skipped ? { photosSkipped: photos.skipped } : {}) }, 201);
}

/** What the user typed: up to 2000 characters, without an email, phone number or amount. */
function inquiryText(v) {
  if (typeof v !== "string") refuse("text", "text");
  const text = v.normalize("NFC").replace(/\r\n?/g, "\n").trim();
  if (!text || [...text].length > INQUIRY.text) refuse("text", "text");
  const why = personal(text);
  if (why) refuse(why, "text");
  return text;
}

/**
 * What goes with a question, only the fields the app shows before sending. The alarm check is made by
 * the app, not typed: its times and shift names are what it is for, so only its length is checked.
 */
function inquiryInfo(v) {
  if (v === undefined || v === null) return {};
  if (typeof v !== "object" || Array.isArray(v)) refuse("info", "info");
  const out = {};
  for (const [, key, longest] of INQUIRY_INFO) {
    const x = v[key];
    if (x === undefined || x === null || x === "") continue;
    if (typeof x !== "string" || [...x].length > longest) refuse("info", key);
    if (key !== "diag" && personal(x) === "email") refuse("info", key);
    if ((key === "pay" || key === "setup") && personal(x)) refuse("info", key);
    out[key] = x.replace(/[\u0000-\u0008\u000b-\u001f\u007f]/g, "");
  }
  return out;
}

const messagesOf = (env, id) => all(env, "SELECT sender, text, by, at FROM inquiry_messages WHERE inquiry_id = ? ORDER BY id", id);

/** A question as the app sees it; [admin] adds what was sent with it and who answered. */
async function inquiryView(env, row, admin = false) {
  const messages = await messagesOf(env, row.id);
  const view = {
    id: row.id,
    kind: row.kind,
    status: row.status,
    createdAt: iso(row.created_at),
    updatedAt: iso(row.updated_at),
    messages: messages.map((m) => ({ from: m.sender, text: m.text, at: iso(m.at), ...(admin && m.by ? { by: m.by } : {}) })),
    public: row.public === 1,
    same: row.same ?? 0,
    ...(row.hidden ? { hidden: { code: row.hidden, text: INQUIRY_HIDE[row.hidden] ?? "" } } : {}),
    rating: row.rating ?? 0,
    progress: await inquiryProgress(env, row, messages),
    // [SRV-06] How many photos came with it; each one is read on its own address.
    photos: row.photos ?? (await one(env, "SELECT COUNT(*) AS n FROM inquiry_photos WHERE inquiry_id = ?", row.id)).n,
  };
  if (!admin) return view;
  const info = JSON.parse(row.info);
  const context = {};
  for (const [label, key] of INQUIRY_INFO) if (info[key]) context[label] = info[key];
  const [reasons, notes, items] = (await batch(env, [
    stmt(env, `SELECT reason, COUNT(*) AS n FROM inquiry_marks WHERE inquiry_id = ? AND kind = 'flag'
      GROUP BY reason ORDER BY n DESC, reason`, row.id),
    stmt(env, "SELECT by, text, at FROM inquiry_notes WHERE inquiry_id = ? ORDER BY id", row.id),
    stmt(env, `SELECT d.id, d.title, d.status, (SELECT COUNT(*) FROM dev_links x JOIN inquiries xi ON xi.id = x.inquiry_id WHERE x.item_id = d.id) AS links FROM dev_links l
      JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL WHERE l.inquiry_id = ? ORDER BY l.at`, row.id),
  ])).map((r) => r.results);
  return {
    ...view, appVersion: info.app ?? null, context, waitingHours: row.status === "open" ? Math.floor((Date.now() - row.updated_at) / HOUR) : null, ...triage(row.kind, messages.find((m) => m.sender === "user")?.text, info),
    flags: row.flags ?? 0, kept: row.kept === 1, reasons: reasons.map((r) => ({ code: r.reason, text: FLAGS[r.reason] ?? r.reason, n: r.n })),
    // [ADMIN-12] Who is answering it and what admins told each other; [ADMIN-11] the 개발사항 it is in.
    assignee: row.assignee ? { id: row.assignee, name: row.assignee_name ?? "" } : null,
    notes: notes.map((n) => ({ by: n.by, text: n.text, at: iso(n.at) })),
    ...(env.actor && can(env.actor, "dev") ? { dev: items.map((d) => ({ id: d.id, title: d.title, status: d.status, links: d.links })) } : {}),
  };
}

/**
 * [INQ-02] Where a question stands, as the asker sees it: 접수 (received), 확인 중 (an admin took it),
 * 답변 (answered), 개발 목록 (in a 개발사항 being made) and 반영 (in a finished one, with its version). The
 * items themselves stay the admins': only the stage and the version go to the app.
 */
async function inquiryProgress(env, row, messages) {
  const items = await all(env, `SELECT d.status, d.released_in FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL
    WHERE l.inquiry_id = ?`, row.id);
  const done = items.filter((d) => d.status === "done");
  if (done.length) return { stage: "released", version: done.map((d) => d.released_in).find(Boolean) ?? null };
  if (items.some((d) => d.status === "todo" || d.status === "doing")) return { stage: items.some((d) => d.status === "doing") ? "building" : "planned" };
  const answered = messages.some((m) => m.sender === "admin");
  const last = messages[messages.length - 1];
  if (answered && last?.sender === "admin") return { stage: "answered" };
  if (row.assignee) return { stage: "checking" };
  return { stage: answered ? "checking" : "received" };
}

/** Who answered: the signed-in admin's name once there are admin accounts, else "admin". */
function actorName(env) {
  const a = env.actor;
  const name = typeof a === "string" ? a : a && typeof a === "object" ? a.name : null;
  return typeof name === "string" && name ? name.slice(0, 40) : "admin";
}

/** /api/admin/inquiries: the list, one thread, an answer and open or closed. */
// ---- 모두의 문의 [ADMIN-02] ----
//
// A question sent from an app that says so on its form is public: anyone with the app reads it and the
// developer's answers, says 나도 그래요 or 신고s it. The writer is never named, and what went with the
// question (app, phone, alarm check, pay check) stays with the developer. A question sent before was
// promised to the developer only, so it stays private. Three 신고 hide it until the developer looks.

/** Why a public question is hidden, as its writer's app shows it. */
const INQUIRY_HIDE = {
  abuse: "욕설이나 비방이 있어요",
  personal: "개인 정보가 있어요",
  spam: "광고나 도배예요",
  flags: "신고가 모여 개발자가 확인하고 있어요",
};

const PUBLIC_PAGE = 30;
const PUBLIC_CACHE_MS = 60 * 1000;
const publicRows = new WeakMap();

/** What anyone sees of a public question in the list; [author] the writer sealed for 이 사람 글 안 보기 (COMM-11). */
function publicItem(r, me, sameByMe, author) {
  return {
    id: r.id,
    kind: r.kind,
    status: r.status,
    preview: [...(r.first ?? "")].slice(0, 160).join(""),
    answers: r.answers,
    same: r.same,
    sameByMe: sameByMe.has(r.id),
    mine: r.device === me,
    ...(author ? { author } : {}),
    createdAt: iso(r.created_at),
    updatedAt: iso(r.updated_at),
    ...triage(r.kind, r.first, JSON.parse(r.info)),
  };
}

/** 모두의 문의, newest activity first or most 나도 그래요 first, one topic or all, a page at a time. */
async function publicList(env, params, device, hide) {
  const me = device ? await deviceHash(device) : null;
  const topic = Object.hasOwn(TOPIC_NAMES, params.get("topic") ?? "") ? params.get("topic") : null;
  const order = params.get("sort") === "same" ? "i.same DESC, i.updated_at DESC" : "i.updated_at DESC";
  const offset = Math.max(0, Math.min(Number.parseInt(params.get("after") ?? "0", 10) || 0, 1000));
  // [SRV-08] The 500 rows behind 모두의 문의 are read once a minute per Worker and order, not on every look.
  const cacheKey = `${order}`;
  const cached = publicRows.get(env.DB ?? env)?.get(cacheKey);
  const rows = cached && cached.until > Date.now() ? cached.rows : await all(env, `SELECT i.id, i.kind, i.status, i.info, i.same, i.device, i.created_at, i.updated_at,
      (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first,
      (SELECT COUNT(*) FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin') AS answers
    FROM inquiries i WHERE i.public = 1 AND i.hidden IS NULL ORDER BY ${order} LIMIT 500`);
  if (!cached || cached.rows !== rows) {
    if (!publicRows.has(env.DB ?? env)) publicRows.set(env.DB ?? env, new Map());
    publicRows.get(env.DB ?? env).set(cacheKey, { rows, until: Date.now() + PUBLIC_CACHE_MS });
  }
  const sameByMe = new Set(me ? (await all(env, "SELECT inquiry_id FROM inquiry_marks WHERE device = ? AND kind = 'same'", me))
    .map((r) => r.inquiry_id) : []);
  // [COMM-11] 이 사람 글 안 보기 in 모두의 문의 too, the same way as the plaza: the server keeps no list.
  const key = await authorKey(env);
  const hidden = await hiddenAuthors(key, hide);
  const kept = rows.filter((r) => r.device === me || !hidden.has(r.device));
  const hiddenByYou = rows.length - kept.length;
  const items = await Promise.all(kept.map(async (r) => publicItem(r, me, sameByMe, r.device === me ? null : await authorToken(key, r.device))));
  const topics = {};
  for (const i of items) topics[i.topic] = (topics[i.topic] ?? 0) + 1;
  const shown = items.filter((i) => !topic || i.topic === topic);
  const page = shown.slice(offset, offset + PUBLIC_PAGE);
  return json({ topics, topicNames: TOPIC_NAMES, inquiries: page, next: offset + PUBLIC_PAGE < shown.length ? offset + PUBLIC_PAGE : null,
    ...(hiddenByYou ? { hiddenByYou } : {}) });
}

/** One public question with every message; its writer also sees it while hidden, with why. */
async function publicThread(env, id, device, hide) {
  const me = device ? await deviceHash(device) : null;
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ? AND public = 1", id);
  if (!row || (row.hidden && row.device !== me)) return json({ error: "not_found" }, 404);
  const key = await authorKey(env);
  if (row.device !== me && (await hiddenAuthors(key, hide)).has(row.device)) return json({ error: "hidden_by_you" }, 404);
  const messages = await messagesOf(env, row.id);
  const marks = me ? await all(env, "SELECT kind FROM inquiry_marks WHERE inquiry_id = ? AND device = ?", row.id, me) : [];
  return json({
    id: row.id,
    kind: row.kind,
    status: row.status,
    same: row.same,
    sameByMe: marks.some((m) => m.kind === "same"),
    flaggedByMe: marks.some((m) => m.kind === "flag"),
    mine: row.device === me,
    ...(row.device === me ? {} : { author: await authorToken(key, row.device) }),
    createdAt: iso(row.created_at),
    updatedAt: iso(row.updated_at),
    messages: messages.map((m) => ({ from: m.sender === "user" ? "user" : "admin", text: m.text, at: iso(m.at) })),
    ...triage(row.kind, messages.find((m) => m.sender === "user")?.text, JSON.parse(row.info)),
    ...(row.hidden ? { hidden: { code: row.hidden, text: INQUIRY_HIDE[row.hidden] ?? "" } } : {}),
  });
}

/** 나도 그래요 (on: false takes it back) or 신고, once per install; not on one's own question. */
async function inquiryMark(request, env, id, kind) {
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `vote|${net.now}`, LIMITS.votesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
  const dev = await deviceHash(device);
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ? AND public = 1 AND hidden IS NULL", id);
  if (!row) return json({ error: "not_found" }, 404);
  if (row.device === dev) return json({ error: "own" }, 400);
  if (kind === "same") {
    await batch(env, [
      body.on === false
        ? stmt(env, "DELETE FROM inquiry_marks WHERE inquiry_id = ? AND device = ? AND kind = 'same'", id, dev)
        : stmt(env, "INSERT OR IGNORE INTO inquiry_marks (inquiry_id, device, kind, reason, at) VALUES (?, ?, 'same', NULL, ?)", id, dev, now),
      stmt(env, "UPDATE inquiries SET same = (SELECT COUNT(*) FROM inquiry_marks WHERE inquiry_id = ?1 AND kind = 'same') WHERE id = ?1", id),
    ]);
    const after = await one(env, "SELECT same FROM inquiries WHERE id = ?", id);
    return json({ same: after.same, sameByMe: body.on !== false });
  }
  const reason = Object.hasOwn(FLAGS, body.reason) && body.reason !== "false" ? body.reason : "abuse";
  const added = await run(env, "INSERT OR IGNORE INTO inquiry_marks (inquiry_id, device, kind, reason, at) VALUES (?, ?, 'flag', ?, ?)", id, dev, reason, now);
  if (added.meta.changes) {
    await run(env, `UPDATE inquiries SET flags = flags + 1,
        hidden = CASE WHEN kept = 0 AND flags + 1 >= ?2 THEN 'flags' ELSE hidden END WHERE id = ?1`, id, LIMITS.flagsToHide);
    const after = await one(env, "SELECT hidden FROM inquiries WHERE id = ?", id);
    if (after.hidden) {
      await audit(env, now, "inquiry_autohide", id);
      await notifyAdmins(env, "flagged", { title: "신고로 가려진 문의", body: "신고가 쌓여 모두의 문의 하나가 가려졌어요", url: "/admin/#/inquiries" }, now);
    }
  }
  return json({ ok: true });
}

async function adminInquiries(request, env, url, path, now) {
  const method = request.method;
  if (path === "/inquiries" && method === "GET") return json(await inquiryList(env, url.searchParams, env.actor?.id ?? ""));
  if (path === "/inquiries/bulk" && method === "POST") return inquiryBulk(request, env, now);
  // AT 1.29 [ADMCP-01][ADMCP-03] 문의 복사: read whole (seeing is enough), then 복사함 (changing questions).
  if (path === "/inquiries/export" && method === "GET") return json(await inquiryExport(env, url.searchParams));
  if (path === "/inquiries/copied" && method === "POST") return inquiryCopied(request, env, now);
  // [SRV-06] A photo sent with a question, for an admin who may read questions.
  const photo = path.match(/^\/inquiries\/([A-Za-z0-9]{22})\/photos\/([12])$/);
  if (photo && method === "GET") return photoFile(env, photo[1], Number(photo[2]));
  const m = path.match(/^\/inquiries\/([A-Za-z0-9]{22})(\/reply|\/status|\/visibility|\/assign|\/notes|\/typing)?$/);
  if (!m) return json({ error: "not_found" }, 404);
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", m[1]);
  if (!row) return json({ error: "not_found" }, 404);
  if (!m[2] && method === "GET") return json(await adminInquiryView(env, row));
  if (method !== "POST" || !m[2]) return json({ error: "not_found" }, 404);
  const body = await readJson(request, INQUIRY.bytes);
  if (m[2] === "/typing") {
    // [ADMIN-16] 답 쓰는 중: lasts 30 seconds unless sent again, so a closed tab stops showing by itself.
    const key = `typing|${row.id}|${env.actor.id}|${actorName(env).replace(/\|/g, "")}`;
    if (body.on === false) await run(env, "DELETE FROM counters WHERE key = ?", key);
    else await run(env, "INSERT OR REPLACE INTO counters (key, count, expires) VALUES (?, 1, ?)", key, now + 30 * 1000);
    return json({ ok: true });
  }
  if (m[2] === "/reply") {
    await replyTo(env, row, body, now);
  } else if (m[2] === "/assign") {
    // [ADMIN-12] 내가 맡기, or let it go (to: null); one tap, so two admins do not answer the same question.
    if (body.to !== "me" && body.to !== null) refuse("to", "to");
    // Letting go is for whoever holds it, or the owner; nobody releases someone else's question.
    if (!mayAssign(env.actor, row, body.to)) throw new Refuse("no_permission", "assignee", 403);
    await run(env, "UPDATE inquiries SET assignee = ?, assignee_name = ? WHERE id = ?", body.to ? env.actor.id : null, body.to ? actorName(env) : null, row.id);
    await audit(env, now, "inquiry_assign", row.id, body.to ? actorName(env) : "");
  } else if (m[2] === "/notes") {
    // [ADMIN-12] A note for the other admins: never in the app's view of the question.
    const text = plainText(body.text, DEV.note, "text", true);
    await run(env, "INSERT INTO inquiry_notes (inquiry_id, by, text, at) VALUES (?, ?, ?, ?)", row.id, actorName(env), text, now);
  } else if (m[2] === "/visibility") {
    // 모두의 문의: hide (with a reason the writer's app shows) or show again; either way 신고 no longer hides it.
    if (!row.public) refuse("public", "action");
    if (body.action === "hide") {
      const reason = Object.hasOwn(INQUIRY_HIDE, body.reason) && body.reason !== "flags" ? body.reason : "abuse";
      await run(env, "UPDATE inquiries SET hidden = ?, kept = 1 WHERE id = ?", reason, row.id);
      await audit(env, now, "inquiry_hide", row.id, reason);
    } else if (body.action === "show") {
      await batch(env, [
        stmt(env, "UPDATE inquiries SET hidden = NULL, kept = 1, flags = 0 WHERE id = ?", row.id),
        stmt(env, "DELETE FROM inquiry_marks WHERE inquiry_id = ? AND kind = 'flag'", row.id),
      ]);
      await audit(env, now, "inquiry_show", row.id);
    } else {
      refuse("action", "action");
    }
  } else {
    if (!["open", "closed"].includes(body.status)) refuse("status", "status");
    // Opening or closing is the developer's bookkeeping; it does not keep a thread from its 180 days.
    await run(env, "UPDATE inquiries SET status = ? WHERE id = ?", body.status, row.id);
    await audit(env, now, "inquiry_status", row.id, body.status);
  }
  return json(await adminInquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", row.id)));
}

const mayAssign = (actor, row, to) => to !== null || !row.assignee || row.assignee === actor.id || actor.owner;

/**
 * An admin's answer to one question. [ADMIN-15] The same words from the same admin within a minute are
 * one answer, so a double tap or a resend after a slow network does not reach the asker twice.
 * [ADMIN-16] An answer sent from a saved one counts as a use of it.
 */
async function replyTo(env, row, body, now, audited = true) {
  if (typeof body.text !== "string" || !body.text.trim() || [...body.text.trim()].length > INQUIRY.text) refuse("text", "text");
  const text = body.text.normalize("NFC").replace(/\r\n?/g, "\n").trim();
  const status = body.close === true ? "closed" : "answered";
  const again = await one(env, `SELECT id FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'admin' AND text = ? AND by = ? AND at > ?
    ORDER BY id DESC LIMIT 1`, row.id, text, actorName(env), now - 60 * 1000);
  if (again) return false;
  await batch(env, [
    stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, ?, ?)", row.id, text, actorName(env), now),
    // [ADMIN-12] Whoever answers a question nobody took has taken it.
    stmt(env, `UPDATE inquiries SET status = ?, updated_at = ?, assignee = COALESCE(assignee, ?),
      assignee_name = CASE WHEN assignee IS NULL THEN ? ELSE assignee_name END WHERE id = ?`, status, now, env.actor?.id ?? null, actorName(env), row.id),
    ...(Number.isInteger(body.template) ? [stmt(env, "UPDATE reply_templates SET uses = uses + 1 WHERE id = ?", body.template)] : []),
    // Sent, so no longer 답 쓰는 중.
    stmt(env, "DELETE FROM counters WHERE key LIKE ?", `typing|${row.id}|${env.actor?.id ?? ""}|%`),
  ]);
  if (audited) await audit(env, now, "inquiry_reply", row.id, status);
  return true;
}

/** [ADMIN-16] What can be done to many questions at once, and what it needs besides 문의 고치기. */
const BULK = { assign: null, unassign: null, close: null, open: null, reply: null, link: "dev" };

/**
 * [ADMIN-16] Up to 100 questions at once: take them, let them go, close, open again, send each the same
 * answer, or link them to a 개발사항. What one admin may not do to one of them (let go of someone else's)
 * is skipped and counted, the rest goes through, and the activity log gets one line for the lot.
 */
async function inquiryBulk(request, env, now) {
  const body = await readJson(request, INQUIRY.bytes + 4096);
  if (!Object.hasOwn(BULK, body.action)) refuse("action", "action");
  const ids = Array.isArray(body.ids) ? [...new Set(body.ids)] : [];
  if (!ids.length || ids.length > DEV.bulk || ids.some((id) => typeof id !== "string" || !/^[A-Za-z0-9]{22}$/.test(id))) refuse("ids", "ids");
  if (BULK[body.action] && !can(env.actor, BULK[body.action], "edit")) return json({ error: "no_permission", area: BULK[body.action] }, 403);
  let item = null;
  if (body.action === "link") {
    item = typeof body.item === "string" ? await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL", body.item) : null;
    if (!item) return json({ error: "not_found" }, 404);
  }
  const rows = await all(env, `SELECT * FROM inquiries WHERE id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  let done = 0;
  let skipped = ids.length - rows.length;
  const a = env.actor;
  for (const row of rows) {
    if (body.action === "assign" || body.action === "unassign") {
      const to = body.action === "assign" ? "me" : null;
      if (!mayAssign(a, row, to)) {
        skipped++;
        continue;
      }
      await run(env, "UPDATE inquiries SET assignee = ?, assignee_name = ? WHERE id = ?", to ? a.id : null, to ? actorName(env) : null, row.id);
    } else if (body.action === "close" || body.action === "open") {
      await run(env, "UPDATE inquiries SET status = ? WHERE id = ?", body.action === "close" ? "closed" : "open", row.id);
    } else if (body.action === "reply") {
      if (!(await replyTo(env, row, { text: body.text, close: body.close === true, template: body.template }, now, false))) {
        skipped++;
        continue;
      }
    } else {
      const r = await run(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", item.id, row.id, actorName(env), now);
      if (r.meta.changes) await devEvent(env, item.id, now, "link", { inquiry: row.id }).run();
    }
    done++;
  }
  if (item) await run(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, item.id);
  await audit(env, now, "inquiry_bulk", item ? item.title : "", `${body.action} ${done}${skipped ? ` (${skipped} 건너뜀)` : ""}`);
  return json({ done, skipped });
}

/**
 * [ADMIN-16][ADMIN-17] One question for an admin: what inquiryView gives, with the saved answers (its
 * topic's first, then the most used) when this admin may answer, who else is writing to it, and what
 * admins did to it, for the timeline.
 */
/**
 * [ADMUX-07] The question's audit lines (in the order written) with its 개발사항 links put in by time. Two
 * from the same millisecond cannot be told apart by time, so a link goes after an assign or a reply of
 * that moment and before a status change or a hide (a question is linked, then closed).
 */
function timeline(events, links) {
  const late = new Set(["inquiry_status", "inquiry_hide", "inquiry_show"]);
  const out = [];
  const queue = [...links].sort((x, y) => x.at - y.at);
  for (const e of events) {
    while (queue.length && (queue[0].at < e.at || (queue[0].at === e.at && late.has(e.action)))) {
      const l = queue.shift();
      out.push({ at: iso(l.at), action: "dev_link", detail: l.title, item: l.id, by: l.by });
    }
    out.push({ at: iso(e.at), action: e.action, detail: e.detail, by: plainActor(e.actor) });
  }
  for (const l of queue) out.push({ at: iso(l.at), action: "dev_link", detail: l.title, item: l.id, by: l.by });
  return out;
}

async function adminInquiryView(env, row) {
  const view = await inquiryView(env, row, true);
  const now = Date.now();
  const [events, links, typing] = (await batch(env, [
    stmt(env, `SELECT at, action, detail, actor FROM audit WHERE target = ? AND action IN ('inquiry_assign', 'inquiry_status', 'inquiry_reply',
      'inquiry_hide', 'inquiry_show') ORDER BY id LIMIT 200`, row.id),
    stmt(env, "SELECT l.at, l.by, d.id, d.title FROM dev_links l JOIN dev_items d ON d.id = l.item_id WHERE l.inquiry_id = ? ORDER BY l.at", row.id),
    stmt(env, "SELECT key FROM counters WHERE key LIKE ? AND expires > ?", `typing|${row.id}|%`, now),
  ])).map((r) => r.results);
  const out = {
    ...view,
    events: timeline(events, links),
    typing: typingByInquiry(typing, env.actor?.id ?? "")[row.id] ?? [],
    // AT 1.29 [ADMCP-03][ADMCP-06] 복사함, the phone's short mark and how many other questions it sent.
    ...copyFields(row),
    sameSender: (await one(env, "SELECT COUNT(*) AS n FROM inquiries WHERE device = ? AND id != ?", row.device, row.id)).n,
  };
  if (view.waitingHours !== null) {
    const last = view.messages[view.messages.length - 1];
    out.slaLeft = last?.from === "user" ? slaLeft(row.kind, row.updated_at, await slaTargets(env), now) : null;
  }
  if (env.actor && can(env.actor, "inquiries", "edit")) {
    const list = (await templates(env, now)).templates;
    out.templates = list.sort((x, y) => (y.topic === view.topic) - (x.topic === view.topic) || y.uses - x.uses || x.id - y.id);
  }
  return out;
}

/** [ADMIN-17] Who did it, as a name: rows written before 1.23 carried "이름 <메일>", shown without the mail. */
const plainActor = (actor) => String(actor ?? "").replace(/\s*<[^<>]*@[^<>]*>\s*$/, "");

/**
 * [ADMIN-16] How a list is paged: [after] rows are skipped (the last answer's next), [limit] come, 50
 * unless asked for fewer or up to 100.
 */
function page(params, fallback = 50) {
  const after = Number(params.get("after"));
  const limit = Number(params.get("limit"));
  return { after: Number.isInteger(after) && after > 0 ? after : 0, limit: Number.isInteger(limit) && limit > 0 ? Math.min(limit, 100) : fallback };
}

/** One page of [list] and where the next starts, or null at the end. */
const paged = (list, { after, limit }) => ({ items: list.slice(after, after + limit), next: after + limit < list.length ? after + limit : null });

/** [ADMUX-06] How long a question of each kind may wait for an answer, in hours (설정 › 답 목표). */
const SLA = { keys: { slaBug: 8, slaQuestion: 24, slaIdea: 72, slaOther: 72 }, least: 1, most: 168 };
const slaKey = (kind) => (kind === "bug" || kind === "problem" ? "slaBug" : kind === "question" ? "slaQuestion" : kind === "idea" ? "slaIdea" : "slaOther");
const slaSetting = (key) => key.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`);

const slaTargets = async (env) => readSla(await all(env, SLA_SQL));

function readSla(rows) {
  const out = {};
  for (const [k, fallback] of Object.entries(SLA.keys)) {
    const n = Number(rows.find((r) => r.key === slaSetting(k))?.value);
    out[k] = Number.isInteger(n) && n >= SLA.least && n <= SLA.most ? n : fallback;
  }
  return out;
}

/** [ADMUX-06] Minutes until a waiting question passes its kind's target (below 0: past it), or null. */
const slaLeft = (kind, waitingSince, targets, now) => (waitingSince === null ? null : Math.round((waitingSince + targets[slaKey(kind)] * HOUR - now) / 60000));

/** The sorts the questions list offers; the default is the one it always had. */
const INQUIRY_SORTS = ["default", "sla", "oldest", "newest", "same"];

/**
 * Questions waiting first, then the newest; [q] finds words in what was asked or answered. [ADMIN-16]
 * Up to 1000 are read in one go, counted by topic before the topic filter and paged after sorting.
 */
async function inquiryList(env, params, actorId = "") {
  // [ADMUX-02] 놓친 것 is only ever a waiting question, so the status filter follows it.
  const late = params.get("late") === "1" ? Date.now() - INQUIRY.lateMs : 0;
  const status = late ? "open" : ["open", "answered", "closed"].includes(params.get("status")) ? params.get("status") : null;
  const q = String(params.get("q") ?? "").trim().slice(0, 40);
  const topic = Object.hasOwn(TOPIC_NAMES, params.get("topic") ?? "") ? params.get("topic") : null;
  // [ADMIN-12] mine: the ones I took; free: the ones nobody took yet.
  const who = ["mine", "free"].includes(params.get("who")) ? params.get("who") : "";
  const sort = INQUIRY_SORTS.includes(params.get("sort")) ? params.get("sort") : "default";
  const like = `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  // AT 1.29 [ADMCP-02] One kind (문제 신고만), copied or not yet (복사함), and one sending phone (같은 기기).
  const kind = INQUIRY.kinds.includes(params.get("kind")) ? params.get("kind") : null;
  const copied = ["no", "yes"].includes(params.get("copied")) ? params.get("copied") : "";
  const sender = /^[0-9a-f]{6}$/.test(params.get("sender") ?? "") ? params.get("sender") : "";
  const now = Date.now();
  const [rows, statusRows, slaRows, typing, fresh] = (await batch(env, [stmt(env, `SELECT i.*,
      (SELECT COUNT(*) FROM inquiry_messages m WHERE m.inquiry_id = i.id) AS count,
      (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first,
      (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last,
      (SELECT COUNT(*) FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL WHERE l.inquiry_id = i.id) AS dev,
      (SELECT COUNT(*) FROM inquiry_notes n WHERE n.inquiry_id = i.id) AS notes
    FROM inquiries i
    WHERE (?1 IS NULL OR i.status = ?1)
      AND (?2 = '' OR EXISTS (SELECT 1 FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.text LIKE ?3 ESCAPE '\\')
        OR i.info LIKE ?3 ESCAPE '\\' OR i.id = ?2)
      AND (?4 = '' OR (?4 = 'mine' AND i.assignee = ?5) OR (?4 = 'free' AND i.assignee IS NULL))
      AND (?6 = 0 OR (i.status = 'open' AND i.updated_at < ?6))
      AND (?7 IS NULL OR i.kind = ?7)
      AND (?8 = '' OR (?8 = 'no' AND i.copied_at IS NULL) OR (?8 = 'yes' AND i.copied_at IS NOT NULL))
      AND (?9 = '' OR substr(i.device, 1, 6) = ?9)
    ORDER BY i.status = 'open' DESC, i.updated_at DESC LIMIT 1000`, status, q, like, who, actorId, late, kind, copied, sender),
  stmt(env, "SELECT status, COUNT(*) AS n FROM inquiries GROUP BY status"),
  slaQuery(env),
  stmt(env, "SELECT key FROM counters WHERE key LIKE 'typing|%' AND expires > ?", now),
  // AT 1.29 [ADMCP-04] 복사 전 문제 제보, whatever the filter: how many, and how many came today.
  stmt(env, FRESH_BUGS_SQL, todayStart(now))])).map((r) => r.results);
  const counts = { open: 0, answered: 0, closed: 0 };
  for (const r of statusRows) {
    if (Object.hasOwn(counts, r.status)) counts[r.status] = r.n;
  }
  const targets = readSla(slaRows);
  const writing = typingByInquiry(typing, actorId);
  const listed = rows.map((r) => {
    const info = JSON.parse(r.info);
    const waitingSince = r.status === "open" && r.last === "user" ? r.updated_at : null;
    return {
      id: r.id,
      kind: r.kind,
      status: r.status,
      preview: [...(r.first ?? "")].slice(0, 120).join(""),
      count: r.count,
      waiting: r.last === "user",
      appVersion: info.app ?? null,
      createdAt: iso(r.created_at),
      updatedAt: iso(r.updated_at),
      ...triage(r.kind, r.first, info),
      public: r.public === 1,
      hidden: r.hidden ?? null,
      flags: r.flags ?? 0,
      same: r.same ?? 0,
      assignee: r.assignee ? { id: r.assignee, name: r.assignee_name ?? "" } : null,
      dev: r.dev,
      notes: r.notes,
      // [INQ-04] How long the user has waited on an answer, in hours; null when it is not their turn to wait.
      waitingHours: waitingSince === null ? null : Math.floor((now - waitingSince) / HOUR),
      // [ADMUX-06] Minutes left before this kind's answer target; below 0 once past it.
      slaLeft: slaLeft(r.kind, waitingSince, targets, now),
      rating: r.rating ?? 0,
      // [ADMIN-16] Another admin writing an answer to it right now.
      typing: writing[r.id] ?? [],
      // AT 1.29 [ADMCP-03] 복사함 (when, by whom) and [ADMCP-06] a short mark of the phone that sent it.
      ...copyFields(r),
    };
  });
  // How many of this view are in each topic, so the filter shows where the questions pile up.
  const topics = {};
  for (const i of listed) topics[i.topic] = (topics[i.topic] ?? 0) + 1;
  const rank = { urgent: 0, normal: 1, low: 2 };
  // Among those waiting for an answer: urgent first, then the ones most people said 나도 그래요 to; the rest
  // stay newest first.
  const waits = (i) => (i.status === "open" ? 0 : 1);
  const left = (i) => i.slaLeft ?? Infinity;
  const order = {
    default: (a, b) => waits(a) - waits(b) || (waits(a) === 0 ? rank[a.urgency] - rank[b.urgency] || b.same - a.same : 0),
    sla: (a, b) => left(a) - left(b) || waits(a) - waits(b),
    oldest: (a, b) => a.updatedAt.localeCompare(b.updatedAt),
    newest: (a, b) => b.updatedAt.localeCompare(a.updatedAt),
    same: (a, b) => b.same - a.same || waits(a) - waits(b),
  }[sort];
  const shown = listed.filter((i) => !topic || i.topic === topic).sort(order);
  const { items, next } = paged(shown, page(params));
  return { counts, topics, topicNames: TOPIC_NAMES, inquiries: items, total: shown.length, next, sort, sla: targets,
    // [ADMUX-02] What the server took the filter to be, so the app shows the status 놓친 것 fixed.
    status: status ?? "", late: Boolean(late),
    // AT 1.29 [ADMCP-02][ADMCP-04] The new filters as taken, and 복사 전 문제 제보 for the strip above the list.
    kind: kind ?? "", copied, sender, fresh: { bugs: fresh[0]?.n ?? 0, today: fresh[0]?.today ?? 0 } };
}

// AT 1.29 시작
// [ADMCP-01]~[ADMCP-06] 문의 복사: a question (or many) read whole to copy into a development task, 복사함 marked
// on the ones copied (with, if asked, a 개발사항 made from them and their status changed in the same step), and
// the open problem reports nobody copied yet counted for the badges and the list.

/** Open or answered 문제 신고 nobody copied yet, and how many of them came since ?1 (today's start). */
const FRESH_BUGS_SQL = `SELECT COUNT(*) AS n, COALESCE(SUM(created_at >= ?1), 0) AS today FROM inquiries
  WHERE kind = 'bug' AND copied_at IS NULL AND status != 'closed'`;

const todayStart = (now) => Date.parse(`${kstDay(now)}T00:00:00+09:00`);

/**
 * [ADMCP-03] 복사함 as the lists and a question show it, and [ADMCP-06] the sending phone as six letters of the
 * hash the server already keeps: enough to see that two questions came from one phone, nothing that names it.
 */
const copyFields = (row) => ({
  copied: row.copied_at ? { at: iso(row.copied_at), by: row.copied_by ?? "" } : null,
  sender: String(row.device ?? "").slice(0, 6),
});

/** At most this many questions in one copy, like the other bulk steps (DEV.bulk). */
const COPY_MOST = 100;

/** The ids in [v] (an array, or "a,b,c" from an address): 1 to 100 question ids, each once, or a refusal. */
function copyIds(v) {
  const list = Array.isArray(v) ? v : String(v ?? "").split(",").filter(Boolean);
  const ids = [...new Set(list)];
  if (!ids.length || ids.length > COPY_MOST || ids.some((id) => typeof id !== "string" || !/^[A-Za-z0-9]{22}$/.test(id))) refuse("ids", "ids");
  return ids;
}

/**
 * [ADMCP-01][ADMCP-02] GET /inquiries/export?ids=: the questions whole (thread, what came with it, notes, the
 * timeline), in the order asked, for the app to write as one text. Reading only: 복사함 is marked by /copied
 * once the text is really on the clipboard. Anyone who may see questions may copy them.
 */
async function inquiryExport(env, params) {
  const ids = copyIds(params.get("ids"));
  const rows = await all(env, `SELECT * FROM inquiries WHERE id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  const byId = new Map(rows.map((r) => [r.id, r]));
  const out = [];
  for (const id of ids) {
    const row = byId.get(id);
    if (!row) continue;
    const { templates, typing, ...view } = await adminInquiryView(env, row);
    out.push(view);
  }
  return { inquiries: out, missing: ids.length - out.length };
}

/**
 * [ADMCP-03] POST /inquiries/copied {ids, dev?: {title, body}, item?, status?, assign?}: 복사함 on each (now, by
 * me), and in the same step, when asked, a new 개발사항 made from them or an existing one they join (both need
 * 개발사항 고치기), closed or opened again, and taken by me where nobody holds them. One activity line for the lot.
 */
async function inquiryCopied(request, env, now) {
  const body = await readJson(request, 32 * 1024);
  const ids = copyIds(body.ids);
  if (body.status !== undefined && body.status !== null && !["open", "closed"].includes(body.status)) refuse("status", "status");
  const wantsDev = (body.dev !== undefined && body.dev !== null) || (body.item !== undefined && body.item !== null);
  if (wantsDev && !can(env.actor, "dev", "edit")) return json({ error: "no_permission", area: "dev" }, 403);
  const rows = await all(env, `SELECT id, assignee FROM inquiries WHERE id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  if (!rows.length) return json({ error: "not_found" }, 404);
  const name = actorName(env);
  const a = env.actor;
  let item = null;
  if (body.item !== undefined && body.item !== null) {
    item = typeof body.item === "string" ? await one(env, "SELECT id, title FROM dev_items WHERE id = ? AND deleted_at IS NULL", body.item) : null;
    if (!item) return json({ error: "not_found" }, 404);
  } else if (body.dev !== undefined && body.dev !== null) {
    if (typeof body.dev !== "object" || Array.isArray(body.dev)) refuse("title", "title");
    const title = plainText(body.dev.title, DEV.title, "title", true);
    const text = plainText(body.dev.body, DEV.body, "body");
    item = { id: `d${randomId(15)}`, title };
    await batch(env, [
      stmt(env, `INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, assignee, assignee_name, created_at, updated_at, deleted_at)
        VALUES (?, ?, ?, 'todo', '', ?, ?, NULL, NULL, ?, ?, NULL)`, item.id, title, text, a.id, name, now, now),
      devEvent(env, item.id, now, "create", { title, body: cut(text) }),
    ]);
    await audit(env, now, "dev_create", title, item.id);
  }
  const found = rows.map((r) => r.id);
  const marks = found.map(() => "?").join(", ");
  const stmts = [stmt(env, `UPDATE inquiries SET copied_at = ?, copied_by = ? WHERE id IN (${marks})`, now, name, ...found)];
  if (body.status === "open" || body.status === "closed") stmts.push(stmt(env, `UPDATE inquiries SET status = ? WHERE id IN (${marks})`, body.status, ...found));
  // Only the ones nobody holds: copying is no reason to take a question from another admin.
  const free = body.assign === true ? rows.filter((r) => !r.assignee).map((r) => r.id) : [];
  if (free.length) {
    stmts.push(stmt(env, `UPDATE inquiries SET assignee = ?, assignee_name = ? WHERE id IN (${free.map(() => "?").join(", ")})`, a.id, name, ...free));
  }
  await batch(env, stmts);
  let linked = 0;
  if (item) {
    for (const id of found) {
      const r = await run(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", item.id, id, name, now);
      if (r.meta.changes) {
        linked++;
        await devEvent(env, item.id, now, "link", { inquiry: id }).run();
      }
    }
    await run(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, item.id);
  }
  const status = body.status === "open" || body.status === "closed" ? body.status : null;
  const steps = [`${found.length}건`, item ? `개발사항 ${linked}건 연결` : "", status ? (status === "closed" ? "닫음" : "다시 엶") : "",
    free.length ? `${free.length}건 맡음` : ""].filter(Boolean).join(" · ");
  await audit(env, now, "inquiry_copy", item ? item.title : "", steps);
  return json({ marked: found.length, skipped: ids.length - found.length, copied: { at: iso(now), by: name },
    item: item ? { id: item.id, title: item.title } : null, linked, status, assigned: free.length });
}
// AT 1.29 끝

const SLA_SQL = `SELECT key, value FROM settings WHERE key IN (${Object.keys(SLA.keys).map((k) => `'${slaSetting(k)}'`).join(", ")})`;
const slaQuery = (env) => env.DB.prepare(SLA_SQL);

/** [ADMIN-16] typing|<question>|<admin id>|<name> rows as names per question, leaving out [me]. */
function typingByInquiry(rows, me) {
  const out = {};
  for (const { key } of rows) {
    const [, id, admin, ...name] = key.split("|");
    if (admin === me) continue;
    (out[id] ??= []).push(name.join("|"));
  }
  return out;
}

// ---- 개발사항: what admins gather from questions for the developer [ADMIN-10][ADMIN-11] ----
//
// Any admin with "dev" adds an item (from a question in one tap, or by hand), changes or deletes it, and
// links more questions to it; how many questions are linked is how many people asked. Setting 개발중,
// 완료 or 반려 needs "devStatus" as well (the owner has it and gives it). Every change is written to the
// item's own history with who made it and what it was before, and deleting only marks the item, so the
// owner can bring it back with its history whole.

const DEV = {
  statuses: { todo: "접수", doing: "개발중", done: "완료", rejected: "반려" },
  title: 100,
  body: 4000,
  reason: 300,
  note: 1000,
  // Before and after of a long text in the history; enough to see what changed.
  kept: 1000,
  bulk: 100,
  list: 2000,
};

/** [ADMIN-12] What a new server's admins start with in 자주 쓰는 답; they add their own and delete these. */
const DEFAULT_TEMPLATES = [
  ["확인 중", "알려 주셔서 고마워요. 지금 확인하고 있어요. 고치는 대로 다시 알려 드릴게요."],
  ["고쳤어요", "말씀하신 문제를 고쳤어요. 앱을 최신 버전으로 업데이트한 뒤 다시 확인해 주세요."],
  ["더 알려 주세요", "정확히 확인하려면 조금 더 알려 주세요. 언제, 어떤 화면에서, 무엇을 눌렀을 때 생겼는지 적어 주시면 큰 도움이 돼요."],
  ["설정 안내", "설정에서 바로 바꿀 수 있어요. 설정 > 해당 항목에서 원하는 값으로 바꿔 보세요."],
  ["개발 예정", "좋은 의견 고마워요. 개발할 목록에 올려 두었어요. 반영되면 다시 알려 드릴게요."],
  ["반영했어요", "말씀해 주신 내용을 반영했어요. 앱을 최신 버전으로 업데이트하면 쓸 수 있어요."],
];

/** Text an admin typed: control characters out, lines kept, [max] letters; [required] refuses an empty one. */
function plainText(v, max, field, required = false) {
  if (v === undefined || v === null) v = "";
  if (typeof v !== "string") refuse(field, field);
  const text = v.normalize("NFC").replace(/\r\n?/g, "\n").replace(/[\u0000-\u0008\u000b-\u001f\u007f]/g, "").trim();
  if ((required && !text) || [...text].length > max) refuse(field, field);
  return text;
}

const cut = (text) => ([...text].length > DEV.kept ? `${[...text].slice(0, DEV.kept).join("")}…` : text);

/** One line of an item's history, by the signed-in admin. */
const devEvent = (env, id, now, action, detail = {}) => stmt(env,
  "INSERT INTO dev_history (item_id, at, actor_id, actor, action, detail) VALUES (?, ?, ?, ?, ?, ?)",
  id, now, env.actor?.id ?? "", actorName(env), action, JSON.stringify(detail));

async function devRoute(request, env, url, path, now) {
  const method = request.method;
  if (path === "/dev" && method === "GET") return json(await devList(env, url.searchParams));
  if (path === "/dev" && method === "POST") return devCreate(request, env, now);
  if (path === "/dev/bulk" && method === "POST") return devBulk(request, env, now);
  const m = path.match(/^\/dev\/(d[A-Za-z0-9]{15})(\/delete|\/restore|\/links|\/links\/remove|\/notes|\/reply)?$/);
  if (!m) return json({ error: "not_found" }, 404);
  const row = await one(env, "SELECT * FROM dev_items WHERE id = ?", m[1]);
  if (!row) return json({ error: "not_found" }, 404);
  if (!m[2] && method === "GET") return json(await devView(env, row));
  if (method !== "POST") return json({ error: "not_found" }, 404);
  const body = await readJson(request, 16 * 1024);
  if (m[2] === "/restore") {
    // Only the owner brings back what someone deleted, so a deletion cannot be undone behind their back.
    if (!env.actor.owner) return json({ error: "no_permission", area: "owner" }, 403);
    if (row.deleted_at === null) return json(await devView(env, row));
    await batch(env, [
      stmt(env, "UPDATE dev_items SET deleted_at = NULL, updated_at = ? WHERE id = ?", now, row.id),
      devEvent(env, row.id, now, "restore"),
    ]);
    await audit(env, now, "dev_restore", row.title, row.id);
  } else if (row.deleted_at !== null) {
    return json({ error: "deleted" }, 409);
  } else if (!m[2]) {
    const changed = await devUpdate(env, row, body, now);
    if (changed === "stale") return json({ error: "changed", item: await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)) }, 409);
  } else if (m[2] === "/delete") {
    await batch(env, [
      stmt(env, "UPDATE dev_items SET deleted_at = ?, updated_at = ? WHERE id = ?", now, now, row.id),
      devEvent(env, row.id, now, "delete", { title: row.title }),
    ]);
    await audit(env, now, "dev_delete", row.title, row.id);
  } else if (m[2] === "/links" || m[2] === "/links/remove") {
    // Linking shows a question to whoever opens the item, so it takes a look at questions too.
    if (!can(env.actor, "inquiries")) return json({ error: "no_permission", area: "inquiries" }, 403);
    const inquiry = typeof body.inquiry === "string" && /^[A-Za-z0-9]{22}$/.test(body.inquiry) ? body.inquiry : refuse("inquiry", "inquiry");
    if (m[2] === "/links") {
      if (!(await one(env, "SELECT id FROM inquiries WHERE id = ?", inquiry))) return json({ error: "not_found" }, 404);
      const r = await run(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", row.id, inquiry, actorName(env), now);
      if (r.meta.changes) {
        await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id), devEvent(env, row.id, now, "link", { inquiry })]);
        await audit(env, now, "dev_link", row.title, inquiry);
      }
    } else {
      const r = await run(env, "DELETE FROM dev_links WHERE item_id = ? AND inquiry_id = ?", row.id, inquiry);
      if (r.meta.changes) {
        await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id), devEvent(env, row.id, now, "unlink", { inquiry })]);
        await audit(env, now, "dev_unlink", row.title, inquiry);
      }
    }
  } else if (m[2] === "/notes") {
    const text = plainText(body.text, DEV.note, "text", true);
    await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id), devEvent(env, row.id, now, "note", { text })]);
  } else {
    return devReply(env, row, body, now);
  }
  return json(await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)));
}

/** A new item, by hand or from a question (inquiry): the question is linked in the same step. */
async function devCreate(request, env, now) {
  const body = await readJson(request, 16 * 1024);
  const title = plainText(body.title, DEV.title, "title", true);
  const text = plainText(body.body, DEV.body, "body");
  let inquiry = null;
  if (body.inquiry !== undefined && body.inquiry !== null) {
    if (!can(env.actor, "inquiries")) return json({ error: "no_permission", area: "inquiries" }, 403);
    inquiry = typeof body.inquiry === "string" && /^[A-Za-z0-9]{22}$/.test(body.inquiry) ? body.inquiry : refuse("inquiry", "inquiry");
    if (!(await one(env, "SELECT id FROM inquiries WHERE id = ?", inquiry))) return json({ error: "not_found" }, 404);
  }
  const id = `d${randomId(15)}`;
  const a = env.actor;
  await batch(env, [
    stmt(env, `INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, assignee, assignee_name, created_at, updated_at, deleted_at)
      VALUES (?, ?, ?, 'todo', '', ?, ?, NULL, NULL, ?, ?, NULL)`, id, title, text, a.id, actorName(env), now, now),
    devEvent(env, id, now, "create", { title, body: cut(text) }),
    ...(inquiry ? [stmt(env, "INSERT INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", id, inquiry, actorName(env), now),
      devEvent(env, id, now, "link", { inquiry })] : []),
  ]);
  await audit(env, now, "dev_create", title, id);
  return json(await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", id)), 201);
}

/**
 * Title, body, who holds it and its status, in one call; each change is one line of history with what
 * it was. With [since] (the updatedAt the admin's screen showed), a change made meanwhile by someone
 * else is not written over: "stale", and the screen shows the newer item.
 */
async function devUpdate(env, row, body, now) {
  if (body.since !== undefined && body.since !== iso(row.updated_at)) return "stale";
  const stmts = [];
  const notes = [];
  const edit = {};
  const set = {};
  if (body.title !== undefined) {
    const title = plainText(body.title, DEV.title, "title", true);
    if (title !== row.title) [edit.title, set.title] = [[row.title, title], title];
  }
  if (body.body !== undefined) {
    const text = plainText(body.body, DEV.body, "body");
    if (text !== row.body) [edit.body, set.body] = [[cut(row.body), cut(text)], text];
  }
  if (Object.keys(edit).length) {
    stmts.push(devEvent(env, row.id, now, "edit", edit));
    notes.push(["dev_edit", Object.keys(edit).join(" ")]);
  }
  if (body.assignee !== undefined) {
    if (body.assignee !== "me" && body.assignee !== null) refuse("assignee", "assignee");
    const to = body.assignee ? { id: env.actor.id, name: actorName(env) } : null;
    if ((to?.id ?? null) !== row.assignee) {
      set.assignee = to?.id ?? null;
      set.assignee_name = to?.name ?? null;
      stmts.push(devEvent(env, row.id, now, "assign", { assignee: [row.assignee_name ?? "", to?.name ?? ""] }));
    }
  }
  if (body.releasedIn !== undefined) {
    // [INQ-02] The version a finished item went out in, e.g. 1.18.0; blank clears it.
    const v = body.releasedIn === null || body.releasedIn === "" ? null : String(body.releasedIn).trim();
    if (v !== null && !/^\d{1,2}\.\d{1,2}\.\d{1,3}$/.test(v)) refuse("releasedIn", "releasedIn");
    if (v !== (row.released_in ?? null)) {
      set.released_in = v;
      stmts.push(devEvent(env, row.id, now, "release", { releasedIn: [row.released_in ?? "", v ?? ""] }));
    }
  }
  if (body.status !== undefined) {
    const next = devStatus(env, body.status, body.reason);
    if (next.status !== row.status || next.reason !== row.reason) {
      [set.status, set.reason] = [next.status, next.reason];
      stmts.push(devEvent(env, row.id, now, "status", { status: [row.status, next.status], ...(next.reason ? { reason: next.reason } : {}) }));
      notes.push(["dev_status", `${row.status}→${next.status}`]);
    }
  }
  if (!stmts.length) return false;
  const cols = Object.keys(set);
  // Written only over the item as this admin read it: a change that landed meanwhile makes it "stale",
  // and then nothing (not even a history line) is written.
  const updated = await run(env, `UPDATE dev_items SET ${[...cols.map((c) => `${c} = ?`), "updated_at = ?"].join(", ")} WHERE id = ? AND updated_at = ?`,
    // Always a new updated_at, even in the same millisecond as the last change, so a second writer of that
    // moment no longer matches it.
    ...cols.map((c) => set[c]), Math.max(now, row.updated_at + 1), row.id, row.updated_at);
  if (!updated.meta.changes) return "stale";
  await batch(env, stmts);
  // The activity screen shows the title; the id goes with what changed.
  for (const [action, detail] of notes) await audit(env, now, action, set.title ?? row.title, `${detail} ${row.id}`);
  return true;
}

/** A status this admin may set: devStatus is needed, and 반려 says why (the reason is kept with it). */
function devStatus(env, status, reason) {
  if (!can(env.actor, "devStatus", "edit")) throw new Refuse("no_permission", "devStatus", 403);
  if (!Object.hasOwn(DEV.statuses, status)) refuse("status", "status");
  const why = status === "rejected" ? plainText(reason, DEV.reason, "reason", true) : "";
  return { status, reason: why };
}

/** The owner (or whoever was given 처리) sets one status on many items at once. */
async function devBulk(request, env, now) {
  const body = await readJson(request, 16 * 1024);
  const next = devStatus(env, body.status, body.reason);
  if (!Array.isArray(body.ids) || !body.ids.length || body.ids.length > DEV.bulk || body.ids.some((x) => typeof x !== "string" || !/^d[A-Za-z0-9]{15}$/.test(x))) {
    refuse("ids", "ids");
  }
  const ids = [...new Set(body.ids)];
  const rows = await all(env, `SELECT id, status, reason FROM dev_items WHERE deleted_at IS NULL AND id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  const change = rows.filter((r) => r.status !== next.status || r.reason !== next.reason).sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
  if (change.length) {
    await batch(env, change.flatMap((r) => [
      stmt(env, "UPDATE dev_items SET status = ?, reason = ?, updated_at = ? WHERE id = ?", next.status, next.reason, now, r.id),
      devEvent(env, r.id, now, "status", { status: [r.status, next.status], ...(next.reason ? { reason: next.reason } : {}), bulk: change.length }),
    ]));
    await audit(env, now, "dev_bulk", `${change.length}개`, `${next.status}: ${change.map((r) => r.id).join(" ")}`);
  }
  return json({ changed: change.length, ids: change.map((r) => r.id) });
}

/**
 * [ADMIN-11] One answer to every linked question at once, typically when an item is 완료: the admin
 * reads the prefilled text and sends it. [ids] limits it to some of them; [close] closes them too.
 */
async function devReply(env, row, body, now) {
  if (!can(env.actor, "inquiries", "edit")) return json({ error: "no_permission", area: "inquiries" }, 403);
  const text = plainText(body.text, INQUIRY.text, "text", true);
  const linked = (await all(env, "SELECT l.inquiry_id AS id FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ?", row.id)).map((r) => r.id);
  const pick = Array.isArray(body.ids) ? linked.filter((id) => body.ids.includes(id)) : linked;
  if (!pick.length) refuse("ids", "ids");
  const status = body.close === true ? "closed" : "answered";
  await batch(env, [
    ...pick.flatMap((id) => [
      stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, ?, ?)", id, text, actorName(env), now),
      stmt(env, "UPDATE inquiries SET status = ?, updated_at = ? WHERE id = ?", status, now, id),
    ]),
    stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id),
    devEvent(env, row.id, now, "reply", { count: pick.length, text: cut(text), close: status === "closed" }),
  ]);
  await audit(env, now, "dev_reply", row.title, `${pick.length} ${status} ${row.id}`);
  return json({ answered: pick.length, item: await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)) });
}

/** The list: one status, open (접수 and 개발중), all, deleted ones, mine, or words in the title or body. */
async function devList(env, params) {
  const status = Object.hasOwn(DEV.statuses, params.get("status") ?? "") || ["open", "all", "deleted"].includes(params.get("status")) ? params.get("status") : "all";
  const q = String(params.get("q") ?? "").trim().slice(0, 40);
  const like = `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  const mine = params.get("who") === "mine" ? env.actor.id : "";
  const [rows, countRows] = (await batch(env, [
    // Only links to questions still kept count.
    stmt(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id) AS links,
        (SELECT COUNT(*) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id AND i.status = 'open') AS waiting
      FROM dev_items d
      WHERE ((?1 = 'deleted' AND d.deleted_at IS NOT NULL) OR (?1 != 'deleted' AND d.deleted_at IS NULL
          AND (?1 = 'all' OR d.status = ?1 OR (?1 = 'open' AND d.status IN ('todo', 'doing')))))
        AND (?2 = '' OR d.title LIKE ?3 ESCAPE '\\' OR d.body LIKE ?3 ESCAPE '\\')
        AND (?4 = '' OR d.assignee = ?4)
      ORDER BY d.updated_at DESC LIMIT ${DEV.list}`, status, q, like, mine),
    stmt(env, `SELECT CASE WHEN deleted_at IS NULL THEN status ELSE 'deleted' END AS s, COUNT(*) AS n FROM dev_items GROUP BY s`),
  ])).map((r) => r.results);
  const counts = { todo: 0, doing: 0, done: 0, rejected: 0, deleted: 0 };
  for (const r of countRows) if (Object.hasOwn(counts, r.s)) counts[r.s] = r.n;
  counts.open = counts.todo + counts.doing;
  counts.all = counts.open + counts.done + counts.rejected;
  // Still to do: most asked for first. Finished: newest first.
  const rank = { doing: 0, todo: 1, done: 2, rejected: 3 };
  const open = (r) => r.status === "todo" || r.status === "doing";
  rows.sort((a, b) => (["all", "open"].includes(status) ? rank[a.status] - rank[b.status] : 0) || (open(a) && open(b) ? b.links - a.links : 0) || b.updated_at - a.updated_at);
  // [ADMIN-16] A page at a time, after sorting all of the view.
  const { items, next } = paged(rows, page(params));
  return { counts, statuses: DEV.statuses, items: items.map(devItem), next, total: rows.length };
}

const devItem = (r) => ({
  id: r.id,
  title: r.title,
  // The whole body too, so 일괄 복사 hands developers everything without opening each item.
  body: r.body,
  preview: [...r.body].slice(0, 160).join(""),
  status: r.status,
  reason: r.reason,
  releasedIn: r.released_in ?? null,
  links: r.links ?? 0,
  waiting: r.waiting ?? 0,
  createdBy: { id: r.created_by, name: r.created_by_name },
  assignee: r.assignee ? { id: r.assignee, name: r.assignee_name ?? "" } : null,
  createdAt: iso(r.created_at),
  updatedAt: iso(r.updated_at),
  deletedAt: r.deleted_at ? iso(r.deleted_at) : null,
});

/** One item with its whole history and the questions behind it (their words only for who may read questions). */
async function devView(env, row) {
  const [links, history] = (await batch(env, [
    stmt(env, `SELECT l.inquiry_id, l.by, l.at, i.kind, i.status, i.updated_at,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first
      FROM dev_links l LEFT JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ? ORDER BY l.at`, row.id),
    stmt(env, "SELECT id, at, actor, action, detail FROM dev_history WHERE item_id = ? ORDER BY id DESC", row.id),
  ])).map((r) => r.results);
  const read = can(env.actor, "inquiries");
  return {
    ...devItem({ ...row, links: links.filter((l) => l.kind).length, waiting: links.filter((l) => l.status === "open").length }),
    statuses: DEV.statuses,
    inquiries: read ? links.filter((l) => l.kind).map((l) => ({
      id: l.inquiry_id, kind: l.kind, status: l.status, preview: [...(l.first ?? "")].slice(0, 160).join(""),
      updatedAt: iso(l.updated_at), linkedBy: l.by, linkedAt: iso(l.at),
    })) : [],
    history: history.map((h) => {
      let detail = {};
      try {
        detail = JSON.parse(h.detail);
      } catch {
        // An old line keeps its action; nothing more to show.
      }
      // The words sent to questions are theirs to read who may read questions, like the previews above.
      if (!read && h.action === "reply" && detail && typeof detail === "object") delete detail.text;
      return { id: h.id, at: iso(h.at), actor: h.actor, action: h.action, detail };
    }),
  };
}

// ---- 자주 쓰는 답, shared by every admin [ADMIN-12] ----

async function templates(env, now) {
  // A new server starts with a few; once they are written, deleting one keeps it deleted. The key goes in
  // first and only the call that put it there seeds, so two admins opening the list at once add them once.
  const first = await run(env, "INSERT OR IGNORE INTO settings (key, value) VALUES ('templates', '1')");
  if (first.meta.changes) {
    await batch(env, DEFAULT_TEMPLATES.map(([label, text]) => stmt(env, "INSERT INTO reply_templates (label, text, by, at) VALUES (?, ?, '', ?)", label, text, now)));
  }
  const rows = await all(env, "SELECT id, label, text, by, topic, uses FROM reply_templates ORDER BY id");
  return { templates: rows.map((r) => ({ id: r.id, label: r.label, text: r.text, by: r.by, topic: r.topic ?? "", uses: r.uses ?? 0 })), topicNames: TOPIC_NAMES };
}

/** [ADMIN-16] A saved answer's topic: one of the question topics, or '' for any question. */
function templateTopic(v) {
  if (v === undefined || v === null || v === "") return "";
  if (typeof v !== "string" || !Object.hasOwn(TOPIC_NAMES, v)) refuse("topic", "topic");
  return v;
}

async function templateRoute(request, env, path, now) {
  const method = request.method;
  if (path === "/templates" && method === "GET") return json(await templates(env, now));
  if (path === "/templates" && method === "POST") {
    await templates(env, now);
    const body = await readJson(request, 8 * 1024);
    const label = plainText(body.label, 20, "label", true);
    const text = plainText(body.text, INQUIRY.text, "text", true);
    const topic = templateTopic(body.topic);
    if ((await one(env, "SELECT COUNT(*) AS n FROM reply_templates")).n >= 40) return json({ error: "too_many" }, 409);
    await run(env, "INSERT INTO reply_templates (label, text, by, at, topic) VALUES (?, ?, ?, ?, ?)", label, text, actorName(env), now, topic);
    await audit(env, now, "template_add", label);
    return json(await templates(env, now), 201);
  }
  // [ADMIN-16] A saved answer changed in place, keeping how often it was used.
  const edit = path.match(/^\/templates\/(\d{1,9})$/);
  if (edit && method === "POST") {
    const row = await one(env, "SELECT label FROM reply_templates WHERE id = ?", Number(edit[1]));
    if (!row) return json({ error: "not_found" }, 404);
    const body = await readJson(request, 8 * 1024);
    const label = plainText(body.label, 20, "label", true);
    const text = plainText(body.text, INQUIRY.text, "text", true);
    await run(env, "UPDATE reply_templates SET label = ?, text = ?, topic = ?, by = ?, at = ? WHERE id = ?", label, text, templateTopic(body.topic),
      actorName(env), now, Number(edit[1]));
    await audit(env, now, "template_edit", label, row.label === label ? "" : `${row.label} → ${label}`);
    return json(await templates(env, now));
  }
  const m = path.match(/^\/templates\/(\d{1,9})\/remove$/);
  if (m && method === "POST") {
    const row = await one(env, "SELECT label FROM reply_templates WHERE id = ?", Number(m[1]));
    if (!row) return json({ error: "not_found" }, 404);
    await run(env, "DELETE FROM reply_templates WHERE id = ?", Number(m[1]));
    await audit(env, now, "template_remove", row.label);
    return json(await templates(env, now));
  }
  return json({ error: "not_found" }, 404);
}

// ---- the developer app's API [ADMIN-01] ----

const COOKIE = "__Host-bamnat_admin";
const cookie = (value, maxAge) => `${COOKIE}=${value}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=${maxAge}`;

/**
 * What is still missing on a new server, shown on the login screen, with why; never a name or value that
 * was set. [ADMIN-15] Only what the login screen needs: which optional services are connected is the
 * owner's to see, after signing in (serverState).
 */
function health(env) {
  const why = { password: secretIssue(env, "ADMIN_PASSWORD", 10), salt: secretIssue(env, "RATE_SALT", 1) };
  return { database: Boolean(env.DB), password: why.password === null, salt: why.salt === null, why };
}

/** [ADMIN-15] The setup screen's full state, for the owner: health plus the optional services. */
function serverState(env) {
  // [MON-03] PLAY_SERVICE_ACCOUNT is optional: without it purchases are not checked with Play.
  // [SRV-05] SHARE_BASE_URL and SHARE_ADMIN_TOKEN are optional: without them 공유 링크 신고 says how to connect.
  // [ADMIN-19] VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY are optional: without them there is no web push.
  return { ...health(env), mail: mailReady(env), play: playReady(env) !== null, share: shareAdmin(env) !== null, push: pushReady(env) !== null };
}

/**
 * Why a secret is not usable: "short", "store" (a Secrets Store binding, not a runtime secret), "name"
 * (set under a name that differs only in case or spaces) or "missing"; null when it is fine.
 */
function secretIssue(env, name, least) {
  const v = env[name];
  if (typeof v === "string") return v.length >= least ? null : "short";
  if (v && typeof v === "object" && typeof v.get === "function") return "store";
  return Object.keys(env).some((k) => k !== name && k.replace(/\s+/g, "").toUpperCase() === name) ? "name" : "missing";
}

/**
 * [ADMIN-04] Which area a developer-app call belongs to: null for what every signed-in admin may do,
 * "owner" for managing admins and anything not listed, so a new route stays the owner's until it is.
 */
function areaFor(path) {
  if (path === "/overview" || path === "/inbox" || path === "/logout" || path === "/me" || path.startsWith("/me/")) return null;
  // AN 1.28 [ADMUX-37] The badges, 오늘 and the shared words: each reads only the areas the admin has.
  if (path === "/badges" || path === "/today" || path === "/basics") return null;
  // AX 1.30 [ADMX-08] One search: each part reads only what the admin may see (adminSearch).
  if (path === "/search") return null;
  if (path === "/queue" || path.startsWith("/groups/") || path.startsWith("/asks/")) return "review";
  if (path.startsWith("/inquiries") || path.startsWith("/templates")) return "inquiries";
  if (path === "/dev" || path.startsWith("/dev/")) return "dev";
  if (path.startsWith("/companies") || path.startsWith("/rules/") || path === "/versions") return "companies";
  if (path.startsWith("/rewards")) return "rewards";
  if (path === "/settings" || path === "/settings/sla" || path === "/settings/invite") return "settings";
  if (path.startsWith("/posts") || path.startsWith("/rates")) return "community";
  // AM 1.28 [FLAG-27] the switches are flags now: 기능 관리 (forcing off checks flagOff as well).
  if (path === "/switches" || path === "/flags" || path.startsWith("/flags/") || path === "/types" || path === "/policy" || path.startsWith("/checks/")) return "flags";
  if (path.startsWith("/notices") || path === "/ops") return "ops";
  // 1.28 통합 [ADMUX-40] 분석 › 기능별 효과 reads flag numbers beside the other numbers.
  if (path === "/metrics" || path === "/remote" || path === "/effects") return "metrics";
  if (path.startsWith("/shares")) return "shares";
  if (path === "/audit") return "audit";
  return "owner";
}

const can = (actor, area, level = "view") => actor.owner || (LEVELS.indexOf(actor.perms[area] ?? "none") >= LEVELS.indexOf(level));

async function admin(request, env, url) {
  const path = url.pathname.slice("/api/admin".length);
  const method = request.method;
  const now = Date.now();
  if (method !== "GET") {
    // Changes come only from the developer app on this address; a page elsewhere cannot add the header.
    const origin = request.headers.get("origin");
    if (request.headers.get("x-admin") !== "1" || (origin && origin !== url.origin)) return json({ error: "forbidden" }, 403);
  }
  if (path === "/login" && method === "POST") return login(request, env, now);
  // [ADMIN-15] The invite token goes in the body, out of logs and history. [ADMUX-11] The 1.23 GET /join?t= is gone
  // (the developer app has used POST /join/info since 1.23).
  if (path === "/join/info" && method === "POST") return inviteInfo(request, env, url, now);
  if (path === "/join/code" && method === "POST") return joinCode(request, env, now);
  if (path === "/join" && method === "POST") return join(request, env, now);
  const session = await signedIn(request, env, now);
  // [ADMUX-11] Opening the app asks this first: signed out is an answer, not a 401 in the browser's console.
  if (path === "/session" && method === "GET") return json({ signedIn: Boolean(session) }, 200, { "cache-control": "no-store" });
  if (!session) return json({ error: "login" }, 401);
  let res = await signedRoute(request, env, url, path, method, now, session);
  // [ADMIN-16] A list or screen that did not change since the app last had it is a 304 with no body.
  if (method === "GET" && res.status === 200 && (res.headers.get("content-type") ?? "").startsWith("application/json")) {
    const body = await res.text();
    const tag = `"${(await sha256(`etag|${body}`)).slice(0, 27)}"`;
    const headers = new Headers(res.headers);
    headers.set("etag", tag);
    res = (request.headers.get("if-none-match") ?? "").split(/\s*,\s*/).includes(tag)
      ? new Response(null, { status: 304, headers })
      : new Response(body, { status: 200, headers });
  }
  if (session.renewed && !res.headers.has("set-cookie")) {
    res = new Response(res.body, res);
    res.headers.set("set-cookie", session.renewed);
  }
  return res;
}

async function signedRoute(request, env, url, path, method, now, session) {
  const actor = session.actor;
  const area = areaFor(path);
  if (area === "owner" ? !actor.owner : area && !can(actor, area, method === "GET" ? "view" : "edit")) {
    return json({ error: "no_permission", area }, 403);
  }
  // Every change below is recorded under the admin who made it (audit reads env.actor).
  env = Object.assign(Object.create(env), { actor });
  if (path === "/logout" && method === "POST") {
    await run(env, "DELETE FROM admin_sessions WHERE token = ?", session.token);
    return json({ ok: true }, 200, { "set-cookie": cookie("", 0) });
  }
  if (path === "/me" && method === "GET") return json(me(actor));
  // [MON-09] A code for this admin's own phone.
  if (path === "/me/link-code" && method === "POST") return linkCode(env, actor, now);
  if (path === "/me/password" && method === "POST") return changePassword(request, env, actor, session.token, now);
  // [ADMIN-19] This browser's web push.
  if (path === "/me/push" && method === "GET") return json(await pushView(env, actor));
  if (path === "/me/push" && method === "POST") return pushSubscribe(request, env, actor, now);
  if (path === "/me/push/remove" && method === "POST") return pushUnsubscribe(request, env, actor);
  if (path === "/me/push/test" && method === "POST") return json(await pushTest(env, actor, now));
  if (path === "/me/logout-all" && method === "POST") {
    await run(env, "DELETE FROM admin_sessions WHERE admin = ?", actor.id);
    await audit(env, now, "logout_all", actor.id);
    return json({ ok: true }, 200, { "set-cookie": cookie("", 0) });
  }
  // AT 1.29 [ADMCP-07] AN-D6: the 1.27 app's /overview and /inbox are gone; the app reads /badges, /today and /basics
  // since 1.28. A tab still open from back then gets 410 with where things went and this build; reloading it gets the new app.
  if ((path === "/overview" || path === "/inbox") && method === "GET") {
    return json({ error: "moved", to: path === "/overview" ? "/api/admin/badges" : "/api/admin/today", build: adminBuild() }, 410);
  }
  // AN 1.28 [ADMUX-37] What 1.27's /overview was, in three: numbers for the menu, the 오늘 screen, and what rarely changes.
  if (path === "/badges" && method === "GET") return json(await badges(env, actor, now));
  if (path === "/today" && method === "GET") return json(await today(env, actor, now));
  if (path === "/basics" && method === "GET") return json(await basics(env));
  // AX 1.30 [ADMX-08] 찾기·이동: companies, questions, 개발사항 and admins in one ask.
  if (path === "/search" && method === "GET") return json(await adminSearch(env, actor, url.searchParams.get("q"), { can, normName }));
  if (path === "/effects" && method === "GET") return json(await flagEffects(env, actor, now));
  // [ADMIN-15] Which optional services are connected: the owner's (areaFor gives "owner").
  if (path === "/server" && method === "GET") return json(serverState(env));
  if (path === "/audit" && method === "GET") return json(await auditLog(env, url));
  if (path === "/admins" && method === "GET") return json(await adminList(env));
  if (path === "/invites" && method === "POST") return invite(request, env, url, now);
  if (path === "/invites/remove" && method === "POST") return removeInvite(request, env, now);
  let m = path.match(/^\/admins\/(a[A-Za-z0-9]{15})(\/remove)?$/);
  if (m && method === "POST") return m[2] ? removeAdmin(env, m[1], now) : updateAdmin(request, env, m[1], now);
  if (path === "/queue" && method === "GET") return json(await queue(env));
  m = path.match(/^\/groups\/([A-Za-z0-9]{12})\/([0-9a-f]{16})\/(adopt|reject|reopen)$/);
  if (m && method === "POST") return m[3] === "reopen" ? reopen(env, m[1], m[2], now) : decide(request, env, m[1], m[2], m[3], now);
  m = path.match(/^\/asks\/([A-Za-z0-9]{22})$/);
  if (m && method === "POST") return answer(request, env, m[1], now);
  if (path === "/companies" && method === "GET") return json(await companies(env, url.searchParams.get("q"), url.searchParams));
  if (path === "/companies" && method === "POST") return createCompany(request, env, now);
  m = path.match(/^\/companies\/([A-Za-z0-9]{12})(\/aliases|\/aliases\/remove|\/merge)?$/);
  if (m && method === "GET" && !m[2]) return companyInfo(env, m[1]);
  if (m && method === "POST") {
    if (!m[2]) return updateCompany(request, env, m[1], now);
    if (m[2] === "/aliases") return addAlias(request, env, m[1], now);
    if (m[2] === "/aliases/remove") return removeAlias(request, env, m[1], now);
    return merge(request, env, m[1], now);
  }
  m = path.match(/^\/rules\/([A-Za-z0-9]{16})$/);
  if (m && method === "POST") return setRule(request, env, m[1], now);
  // [ADMUX-18] 같은 회사 여러 판, and an earlier version made the department's rule again.
  if (path === "/versions" && method === "GET") return json(await versions(env, url.searchParams.get("show")));
  m = path.match(/^\/rules\/([A-Za-z0-9]{16})\/promote$/);
  if (m && method === "POST") return promote(env, m[1], now);
  if (path === "/inquiries" || path.startsWith("/inquiries/")) return adminInquiries(request, env, url, path, now);
  if (path === "/templates" || path.startsWith("/templates/")) return templateRoute(request, env, path, now);
  if (path === "/dev" || path.startsWith("/dev/")) return devRoute(request, env, url, path, now);
  if (path === "/rewards" && method === "GET") return json(await rewards(env, url.searchParams.get("status"), url.searchParams));
  m = path.match(/^\/rewards\/(w[A-Za-z0-9]{22})$/);
  if (m && method === "POST") return setReward(request, env, m[1], now);
  if (path === "/settings" && method === "POST") return saveSettings(request, env, now);
  // [ADMUX-06] 답 목표 (SLA) per kind of question.
  if (path === "/settings/sla" && method === "POST") return saveSla(request, env, now);
  // [INVITE-08] 친구 초대: how many friends, how many days of use, how many months, the cap.
  if (path === "/settings/invite") return inviteSettingsRoute(request, env, method, now);
  // [MON-10] The 정식 출시 switch and [MON-09] admins' phones are the owner's (areaFor gives "owner").
  if (path === "/launch" && method === "GET") return json(await launchView(env));
  // AM 1.28 [FLAG-25] 정식 출시 is the billing flag now: the switch is retired, GET still answers.
  if (path === "/launch" && method === "POST") return json({ error: "retired", field: "launch" }, 410);
  // AM 1.28 [FLAG-21] 기능 관리.
  if (path === "/flags" || path.startsWith("/flags/") || path === "/types" || path === "/policy" || path.startsWith("/checks/")) {
    return flagRoute(request, env, url, path, method, actor, now);
  }
  if (path === "/devices" && method === "GET") return json(await phoneList(env));
  m = path.match(/^\/devices\/(d[A-Za-z0-9]{15})\/(approve|remove)$/);
  if (m && method === "POST") return setPhone(env, m[1], m[2], now);
  if (path === "/posts" && method === "GET") return json(await postList(env, url));
  // [ADMUX-19] 광장 운영 일괄: many posts or 임금표 entries at once, each handled as it is alone.
  if (path === "/posts/bulk" && method === "POST") return plazaBulk(request, env, now, "posts");
  if (path === "/rates/bulk" && method === "POST") return plazaBulk(request, env, now, "rates");
  m = path.match(/^\/posts\/(p[A-Za-z0-9]{15})$/);
  if (m && method === "POST") return moderate(request, env, m[1], now);
  // [PLZ-14] 임금표 to look at, and an admin's call on one.
  if (path === "/rates" && method === "GET") return json(await rateList(env, url));
  m = path.match(/^\/rates\/([A-Za-z0-9]{22})$/);
  if (m && method === "POST") return setRate(request, env, actor, m[1], now);
  // [SRV-04] 공지 and 기능 스위치, [ADMIN-14] 이달 할 일, [ADMIN-13] 지표, [SRV-05] 공유 링크 신고.
  if (path === "/notices" && method === "GET") return json(await noticeList(env, now));
  if (path === "/notices" && method === "POST") return saveNotice(request, env, now);
  m = path.match(/^\/notices\/(n[A-Za-z0-9]{9})\/remove$/);
  if (m && method === "POST") return removeNotice(env, m[1], now);
  if (path === "/switches" && method === "POST") return setSwitch(request, env, now);
  if (path === "/ops" && method === "GET") return json(await opsMonth(env, url.searchParams.get("month"), now));
  if (path === "/ops" && method === "POST") return checkOps(request, env, now);
  if (path === "/metrics" && method === "GET") {
    const m = await metrics(env, url, now);
    m.startup = await startupMetrics(env, m.from, m.to); // AO 1.28 [PERF-17]
    m.photoWrong = await photoWrongMetrics(env, m.from, m.to); // AQ 1.29 [PHOTO-10]
    Object.assign(m, await metricExtras(env, m.from, m.to)); // AX 1.30 [ADMX-07] releases, compare
    return json(m);
  }
  // [ADMUX-15] 원격 갱신 배포 현황 (?fresh=1 skips the ten minutes it is kept).
  if (path === "/remote" && method === "GET") return json(await remoteState(env, now, url.searchParams.get("fresh") === "1"));
  if (path === "/shares" && method === "GET") return shareReports(env);
  m = path.match(/^\/shares\/([A-Za-z0-9]{16,32})\/remove$/);
  if (m && method === "POST") return removeShare(request, env, m[1], now);
  return json({ error: "not_found" }, 404);
}

/**
 * Five wrong passwords from one network lock it out for an hour from the fifth; for an admin account,
 * five on that account lock the account too, from any network. With an email it is an admin account,
 * without one the owner's password.
 */
async function login(request, env, now) {
  const body = await readJson(request, 1024);
  const net = await network(request, env, now);
  const keys = [`login|${net.login}`];
  const email = body.email === undefined ? null : cleanEmail(body.email);
  if (email) keys.push(`login|mail|${(await sha256(`mail|${email}`)).slice(0, 32)}`);
  else if (body.email !== undefined) return json({ error: "email" }, 400);
  const password = env.ADMIN_PASSWORD;
  if (!email && (typeof password !== "string" || password.length < 10)) return json({ error: "not_set" }, 503);
  let worst = null;
  for (const key of keys) {
    const row = await one(env, "SELECT count, expires FROM counters WHERE key = ? AND expires > ?", key, now);
    if (row && (!worst || row.count > worst.count)) worst = row;
  }
  const everyone = await one(env, "SELECT count FROM counters WHERE key = 'login|all' AND expires > ?", now);
  // [ADMIN-15] Under a wide guessing run (50 wrong in the hour) only a network or account that was wrong
  // this hour is shut out: the owner on a clean network still gets in, so the run cannot lock everyone.
  if ((worst?.count ?? 0) >= LIMITS.loginFailures || ((everyone?.count ?? 0) >= 10 * LIMITS.loginFailures && (worst?.count ?? 0) > 0)) {
    return json({ error: "locked", until: iso(worst?.expires ?? now + LIMITS.lockMs) }, 429);
  }
  let account = null;
  let right = false;
  if (email) {
    account = await one(env, "SELECT * FROM admins WHERE email = ?", email);
    // A made-up email takes as long as a real one, so the time says nothing about who has an account.
    right = await checkPassword(typeof body.password === "string" ? body.password : "", account?.pw ?? DUMMY_HASH) && Boolean(account);
  } else {
    right = typeof body.password === "string" && (await sameSecret(body.password, password));
  }
  if (!right) {
    let count = 0;
    for (const key of keys) {
      await allow(env, key, Infinity, LIMITS.lockMs, now);
      const row = await one(env, "SELECT count FROM counters WHERE key = ?", key);
      if (row.count >= LIMITS.loginFailures) await run(env, "UPDATE counters SET expires = ? WHERE key = ?", now + LIMITS.lockMs, key);
      count = Math.max(count, row.count);
    }
    await allow(env, "login|all", Infinity, LIMITS.lockMs, now);
    // [ADMIN-15] The email tried is kept only as a short hash, and only for 30 days: enough to see that one
    // account is being guessed at, without a list of addresses someone typed.
    await batch(env, [stmt(env, "DELETE FROM audit WHERE action = 'login_failed' AND at < ?", now - 30 * DAY)]);
    await audit(env, now, "login_failed", email ? `mail:${(await sha256(`audit|${email}`)).slice(0, 10)}` : "");
    // Each wrong try from the same place answers a little later, so guessing is slow even before the lock.
    await new Promise((done) => setTimeout(done, Math.min(count - 1, 4) * LIMITS.loginDelayMs));
    return json({ error: count >= LIMITS.loginFailures ? "locked" : "wrong", left: Math.max(0, LIMITS.loginFailures - count) }, 401);
  }
  // A right password on an account the owner has not approved, or has paused, still does not get in.
  if (account && account.status !== "active") return json({ error: account.status === "pending" ? "pending" : "suspended" }, 403);
  const token = randomId(40);
  const who = account ? account.id : OWNER.id;
  await batch(env, [
    ...keys.map((key) => stmt(env, "DELETE FROM counters WHERE key = ?", key)),
    stmt(env, "DELETE FROM admin_sessions WHERE expires < ?", now),
    stmt(env, "INSERT INTO admin_sessions (token, admin, expires, created_at) VALUES (?, ?, ?, ?)",
      await sha256(`session|${token}`), who, now + LIMITS.sessionMs, now),
    ...(account ? [stmt(env, "UPDATE admins SET last_login = ? WHERE id = ?", now, account.id)] : []),
  ]);
  await audit(Object.assign(Object.create(env), { actor: account ? actorOf(account) : OWNER }), now, "login", "");
  return json({ ok: true }, 200, { "set-cookie": cookie(token, LIMITS.sessionMs / 1000) });
}

/** The session and who it belongs to, when the cookie holds a live one of an active admin. */
async function signedIn(request, env, now) {
  const token = (request.headers.get("cookie") ?? "").split(/;\s*/).find((c) => c.startsWith(`${COOKIE}=`))?.slice(COOKIE.length + 1);
  if (!token || !/^[A-Za-z0-9]{40}$/.test(token)) return null;
  const hash = await sha256(`session|${token}`);
  // The account is read with the session on every call, so a pause or a change of permissions applies at once.
  const row = await one(env, `SELECT s.admin AS session_admin, s.expires AS session_expires, s.created_at AS session_made, a.* FROM admin_sessions s
    LEFT JOIN admins a ON a.id = s.admin AND a.status = 'active' WHERE s.token = ?`, hash);
  if (!row || row.session_expires <= now) return null;
  const actor = row.session_admin === OWNER.id ? OWNER : row.id ? actorOf(row) : null;
  if (!actor) return null;
  // [ADMIN-15] Used with less than half the week left, it runs on for another week, never past 30 days.
  let renewed = null;
  const end = Math.min(now + LIMITS.sessionMs, row.session_made + LIMITS.sessionMaxMs);
  if (row.session_expires - now < LIMITS.sessionMs / 2 && end > row.session_expires) {
    await run(env, "UPDATE admin_sessions SET expires = ? WHERE token = ?", end, hash);
    renewed = cookie(token, Math.floor((end - now) / 1000));
  }
  return { token: hash, actor, renewed };
}

const actorOf = (a) => ({ id: a.id, name: a.name, email: a.email, owner: false, perms: readPerms(a.perms) });

// [ADMIN-15] With the yes-or-no areas and the look-only ones, so the app draws each as the server reads it.
const me = (actor) => ({ id: actor.id, name: actor.name, email: actor.email, owner: actor.owner, perms: actor.perms, areas: AREAS,
  toggles: TOGGLES, viewOnly: VIEW_ONLY });

// ---- web push to admins [ADMIN-19] ----
//
// Only with VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY set (src/push.js). Each admin turns it on per browser,
// picks which kinds of news and a quiet time (KST hours, nothing sent then). What is sent says only what
// happened and where to look: never a question's words, an email or a device.

/** What can be sent, and the area an admin needs to get it ("owner" for the owner only). */
const PUSH_KINDS = {
  inquiry: { label: "새 문의", area: "inquiries" },
  urgent: { label: "급한 문의(문제 신고·결제)", area: "inquiries" },
  flagged: { label: "신고로 가려진 글", area: "community" },
  join: { label: "승인을 기다리는 관리자", area: "owner" },
  // AM 1.28 [FLAG-28] 결정 D99: a legal flag the day before it turns on by itself, and once it did.
  flags: { label: "기능 관리(결제·광고 같은 법적 기능이 켜지기 하루 전·켜짐)", area: "flags" },
  // AX 1.30 [ADMX-10] 오늘's 주의 필요, each at most once a day (alertTick); an area list is any one of them.
  late: { label: "기한 넘긴 일", area: ["inquiries", "review", "community", "dev"] },
  versions: { label: "정할 판·모양이 다른 판", area: ["companies", "review"] },
  doubt: { label: "달라요 급증·달라요 많은 규칙", area: "review" },
  spike: { label: "문제 신고·서버 오류 급증", area: ["inquiries", "metrics"] },
};
const PUSH = { perAdmin: 5, hosts: /^(fcm\.googleapis\.com|updates\.push\.services\.mozilla\.com|push\.services\.mozilla\.com|web\.push\.apple\.com|[a-z0-9-]+\.notify\.windows\.com|[a-z0-9-]+\.push\.apple\.com)$/ };

const mayGet = (actor, kind) => {
  const area = PUSH_KINDS[kind].area;
  return area === "owner" ? actor.owner : [area].flat().some((a) => can(actor, a));
};

/**
 * AX 1.30 [ADMX-10] Hourly: what 오늘's 주의 필요 shows (as the owner sees it) goes out as web push to the admins who asked
 * for its kind, each alert once a KST day. Without push keys nothing is read.
 */
export async function alertTick(env, now) {
  if (!pushReady(env)) return;
  const t = await today(env, OWNER, now);
  const day = kstDay(now);
  let sent = {};
  try {
    const v = JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'ax_alerts'"))?.value ?? "{}");
    if (v.day === day) sent = v.sent ?? {};
  } catch {
    sent = {};
  }
  const fresh = (t.alerts ?? []).filter((a) => !sent[a.key]);
  if (!fresh.length) return;
  for (const a of fresh) await notifyAdmins(env, a.kind, { title: "주의 필요", body: a.title, url: `/admin/${a.to}` }, now);
  await run(env, "INSERT INTO settings (key, value) VALUES ('ax_alerts', ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value",
    JSON.stringify({ day, sent: { ...sent, ...Object.fromEntries(fresh.map((a) => [a.key, 1])) } }));
}

/** A KST hour of [now] inside quiet hours [from]..[to] (to may be past midnight: 22..7). */
function quietNow(from, to, now) {
  if (from === null || to === null || from === to) return false;
  const h = new Date(now + 9 * HOUR).getUTCHours();
  return from < to ? h >= from && h < to : h >= from || h < to;
}

async function pushView(env, actor) {
  const keys = pushReady(env);
  const rows = await all(env, "SELECT id, kinds, quiet_from, quiet_to, created_at, sent_at FROM push_subscriptions WHERE admin = ? ORDER BY created_at", actor.id);
  return {
    ready: Boolean(keys), publicKey: keys?.publicKey ?? null,
    kinds: Object.entries(PUSH_KINDS).filter(([k]) => mayGet(actor, k)).map(([key, k]) => ({ key, label: k.label })),
    subscriptions: rows.map((r) => ({ id: r.id, kinds: r.kinds.split(",").filter(Boolean), quiet: r.quiet_from === null ? null : { from: r.quiet_from, to: r.quiet_to },
      createdAt: iso(r.created_at), sentAt: r.sent_at ? iso(r.sent_at) : null })),
  };
}

/** The browser's PushSubscription as JSON, checked: an https address at a known push service and its two keys. */
function readSubscription(v) {
  const sub = v && typeof v === "object" ? v : {};
  let url = null;
  try {
    url = new URL(String(sub.endpoint ?? ""));
  } catch {
    refuse("subscription", "endpoint");
  }
  if (url.protocol !== "https:" || !PUSH.hosts.test(url.hostname) || url.port || String(sub.endpoint).length > 1000) refuse("subscription", "endpoint");
  const p256dh = sub.keys?.p256dh;
  const auth = sub.keys?.auth;
  if (typeof p256dh !== "string" || !/^[A-Za-z0-9_-]{80,100}$/.test(p256dh) || typeof auth !== "string" || !/^[A-Za-z0-9_-]{16,32}$/.test(auth)) {
    refuse("subscription", "keys");
  }
  return { endpoint: String(sub.endpoint), p256dh, auth };
}

async function pushSubscribe(request, env, actor, now) {
  if (!pushReady(env)) return json({ error: "push" }, 409);
  const body = await readJson(request, 4096);
  const sub = readSubscription(body.subscription);
  const kinds = Array.isArray(body.kinds) ? [...new Set(body.kinds)] : Object.keys(PUSH_KINDS);
  if (kinds.some((k) => !Object.hasOwn(PUSH_KINDS, k))) refuse("kind", "kinds");
  let quiet = [null, null];
  if (body.quiet) {
    const { from, to } = body.quiet;
    if (![from, to].every((h) => Number.isInteger(h) && h >= 0 && h <= 23)) refuse("quiet", "quiet");
    quiet = [from, to];
  }
  const id = (await sha256(`push|${sub.endpoint}`)).slice(0, 32);
  const mine = await all(env, "SELECT id FROM push_subscriptions WHERE admin = ?", actor.id);
  if (!mine.some((r) => r.id === id) && mine.length >= PUSH.perAdmin) refuse("too_many", "subscription");
  await run(env, `INSERT INTO push_subscriptions (id, admin, endpoint, p256dh, auth, kinds, quiet_from, quiet_to, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (id) DO UPDATE SET admin = excluded.admin, p256dh = excluded.p256dh, auth = excluded.auth, kinds = excluded.kinds,
      quiet_from = excluded.quiet_from, quiet_to = excluded.quiet_to`,
  id, actor.id, sub.endpoint, sub.p256dh, sub.auth, kinds.filter((k) => mayGet(actor, k)).join(","), quiet[0], quiet[1], now);
  return json(await pushView(env, actor));
}

async function pushUnsubscribe(request, env, actor) {
  const body = await readJson(request, 2048);
  const id = typeof body.id === "string" && /^[0-9a-f]{32}$/.test(body.id) ? body.id
    : typeof body.endpoint === "string" ? (await sha256(`push|${body.endpoint}`)).slice(0, 32) : refuse("subscription", "id");
  await run(env, "DELETE FROM push_subscriptions WHERE id = ? AND admin = ?", id, actor.id);
  return json(await pushView(env, actor));
}

/** A test to every browser of this admin, quiet time or not; how many got it and how many were gone. */
async function pushTest(env, actor, now) {
  const keys = pushReady(env);
  if (!keys) return { sent: 0, gone: 0, ready: false };
  const rows = await all(env, "SELECT * FROM push_subscriptions WHERE admin = ?", actor.id);
  return { ...(await deliver(env, keys, rows, { title: "밤낮 관리자", body: "알림이 잘 와요", url: "/admin/", tag: "test" }, now)), ready: true };
}

/** Sends [message] to [rows]; a browser the push service says is gone (404, 410) is forgotten. */
async function deliver(env, keys, rows, message, now) {
  let sent = 0;
  let gone = 0;
  for (const r of rows) {
    const status = await sendPush(keys, r, message, now, message.urgent ? "high" : "normal");
    if (status === 404 || status === 410) {
      gone++;
      await run(env, "DELETE FROM push_subscriptions WHERE id = ?", r.id);
    } else if (status >= 200 && status < 300) {
      sent++;
      await run(env, "UPDATE push_subscriptions SET sent_at = ? WHERE id = ?", now, r.id);
    }
  }
  return { sent, gone };
}

/**
 * [ADMIN-19] News of [kind] to every admin's browser that asked for it, outside its quiet time and only
 * for admins who may see it. Without VAPID secrets (or with nobody asking) it returns at once; it never
 * throws, so whatever called it goes on as if nothing was sent.
 * 1.28 통합 [FLAG-37] 결정 D101: [ignoreQuiet] sends inside quiet time too — only a legal flag's day-before and turned-on
 * news (D99's safety net is pointless if it arrives after the payments started); every other kind keeps quiet time.
 */
export async function notifyAdmins(env, kind, message, now = Date.now(), { ignoreQuiet = false } = {}) {
  try {
    const keys = pushReady(env);
    if (!keys || !env.DB || !Object.hasOwn(PUSH_KINDS, kind)) return { sent: 0, gone: 0 };
    // An urgent question is a question too: it goes once to those who asked for either.
    const also = kind === "urgent" ? "inquiry" : kind;
    const rows = await all(env, `SELECT s.*, a.perms, a.status FROM push_subscriptions s LEFT JOIN admins a ON a.id = s.admin
      WHERE (',' || s.kinds || ',') LIKE ? OR (',' || s.kinds || ',') LIKE ?`, `%,${kind},%`, `%,${also},%`);
    const to = rows.filter((r) => {
      const actor = r.admin === OWNER.id ? OWNER : r.status === "active" ? { owner: false, perms: readPerms(r.perms) } : null;
      return actor && mayGet(actor, kind) && (ignoreQuiet || !quietNow(r.quiet_from, r.quiet_to, now));
    });
    if (!to.length) return { sent: 0, gone: 0 };
    return await deliver(env, keys, to, { tag: kind, ...message }, now);
  } catch (e) {
    console.error(e && e.stack ? e.stack : String(e));
    return { sent: 0, gone: 0 };
  }
}

// ---- admin accounts [ADMIN-04] ----

const EMAIL = /^[^\s@<>()",;:]{1,64}@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;

function cleanEmail(v) {
  const email = typeof v === "string" ? v.trim().toLowerCase() : "";
  return email.length <= LIMITS.email && EMAIL.test(email) ? email : null;
}

/** Permissions as stored and as sent: every area present, unknown areas and levels dropped. */
function readPerms(raw) {
  let p = raw;
  if (typeof raw === "string") {
    try {
      p = JSON.parse(raw);
    } catch {
      p = {};
    }
  }
  const out = {};
  for (const area of Object.keys(AREAS)) {
    const level = p && LEVELS.includes(p[area]) ? p[area] : SINCE[area]?.(p) ?? "none";
    out[area] = VIEW_ONLY.includes(area) && level === "edit" ? "view" : TOGGLES.includes(area) && level === "view" ? "none" : level;
  }
  return out;
}

function permsFrom(body) {
  if (!body.perms || typeof body.perms !== "object" || Array.isArray(body.perms)) refuse("perms", "perms");
  for (const [area, level] of Object.entries(body.perms)) {
    if (!Object.hasOwn(AREAS, area) || !LEVELS.includes(level)) refuse("perms", area);
  }
  return readPerms(body.perms);
}

function adminName(v) {
  const name = typeof v === "string" ? v.replace(/[\u0000-\u001f\u007f<>]/g, "").trim() : "";
  if (!name || name.length > LIMITS.adminName) refuse("name", "name");
  return name;
}

/** At least 10 letters, not one letter over and over, and not the email itself. */
function newPassword(v, email) {
  if (typeof v !== "string" || v.length < 10 || v.length > 200) refuse("password", "password");
  if (new Set(v).size < 4 || v.toLowerCase() === email || v.toLowerCase() === email.split("@")[0]) refuse("weak_password", "password");
  return v;
}

// PBKDF2 at a cost a Worker's CPU limit allows; the count is stored with each hash so it can rise later.
const PBKDF2_ROUNDS = 20000;
const b64 = (bytes) => btoa(String.fromCharCode(...bytes));

async function pbkdf2(password, saltBytes, rounds) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: saltBytes, iterations: rounds }, key, 256);
  return new Uint8Array(bits);
}

async function hashPassword(password) {
  const saltBytes = crypto.getRandomValues(new Uint8Array(16));
  return `pbkdf2$${PBKDF2_ROUNDS}$${b64(saltBytes)}$${b64(await pbkdf2(password, saltBytes, PBKDF2_ROUNDS))}`;
}

const DUMMY_HASH = `pbkdf2$${PBKDF2_ROUNDS}$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=`;

async function checkPassword(password, stored) {
  const [kind, rounds, saltText, hashText] = String(stored).split("$");
  if (kind !== "pbkdf2") return false;
  const got = await pbkdf2(password, Uint8Array.from(atob(saltText), (c) => c.charCodeAt(0)), Number(rounds));
  const want = Uint8Array.from(atob(hashText), (c) => c.charCodeAt(0));
  let diff = got.length ^ want.length;
  for (let i = 0; i < Math.min(got.length, want.length); i++) diff |= got[i] ^ want[i];
  return diff === 0;
}

function mailReady(env) {
  return typeof env.MAIL_API_KEY === "string" && env.MAIL_API_KEY.length > 0 && Boolean(cleanEmail(String(env.MAIL_FROM ?? "").replace(/^.*<|>$/g, "")));
}

/**
 * Sends one plain mail through Resend when MAIL_API_KEY and MAIL_FROM are set; false when mail is off or
 * the send failed. Nothing else on the server sends mail.
 */
async function sendMail(env, to, subject, text) {
  if (!mailReady(env)) return false;
  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { authorization: `Bearer ${env.MAIL_API_KEY}`, "content-type": "application/json" },
      body: JSON.stringify({ from: env.MAIL_FROM, to: [to], subject, text }),
    });
    return res.ok;
  } catch {
    return false;
  }
}

async function adminList(env) {
  const admins = await all(env, "SELECT id, email, name, perms, status, created_at, approved_at, last_login FROM admins ORDER BY created_at DESC");
  const invites = await all(env, "SELECT email, perms, note, expires, created_at, used_at FROM invites ORDER BY created_at DESC");
  const now = Date.now();
  return {
    admins: admins.map((a) => ({
      id: a.id, email: a.email, name: a.name, perms: readPerms(a.perms), status: a.status, createdAt: iso(a.created_at),
      approvedAt: a.approved_at ? iso(a.approved_at) : null, lastLogin: a.last_login ? iso(a.last_login) : null,
    })),
    invites: invites.map((i) => ({
      email: i.email, perms: readPerms(i.perms), note: i.note, createdAt: iso(i.created_at), expiresAt: iso(i.expires),
      state: i.used_at ? "joined" : i.expires <= now ? "expired" : "waiting",
    })),
    areas: AREAS,
    viewOnly: VIEW_ONLY,
    toggles: TOGGLES,
    presets: PRESETS,
    mail: mailReady(env),
  };
}

/**
 * The owner allows one email, with the permissions its account will start with. The link is shown
 * once; the owner sends it to that email, and only that email can make an account with it.
 */
async function invite(request, env, url, now) {
  const body = await readJson(request, 2048);
  const email = cleanEmail(body.email);
  if (!email) refuse("email", "email");
  const perms = permsFrom(body);
  const note = typeof body.note === "string" ? body.note.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 100) : "";
  if (await one(env, "SELECT id FROM admins WHERE email = ?", email)) return json({ error: "exists" }, 409);
  const token = randomId(32);
  await run(env, `INSERT INTO invites (email, perms, note, token, expires, created_at, used_at) VALUES (?, ?, ?, ?, ?, ?, NULL)
    ON CONFLICT (email) DO UPDATE SET perms = excluded.perms, note = excluded.note, token = excluded.token, expires = excluded.expires,
      created_at = excluded.created_at, used_at = NULL`,
    email, JSON.stringify(perms), note, await sha256(`invite|${token}`), now + LIMITS.inviteMs, now);
  await audit(env, now, "invite", email, Object.entries(perms).filter(([, l]) => l !== "none").map(([a, l]) => `${a}:${l}`).join(" "));
  return json({ email, perms, expiresAt: iso(now + LIMITS.inviteMs), link: `${url.origin}/admin/join#${token}`, mail: mailReady(env) }, 201);
}

async function removeInvite(request, env, now) {
  const email = cleanEmail((await readJson(request, 1024)).email);
  const r = email ? await run(env, "DELETE FROM invites WHERE email = ? AND used_at IS NULL", email) : null;
  if (!r?.meta.changes) return json({ error: "not_found" }, 404);
  await audit(env, now, "invite_remove", email);
  return json({ ok: true });
}

async function inviteFor(env, token, now) {
  if (typeof token !== "string" || !/^[A-Za-z0-9]{32}$/.test(token)) return null;
  const row = await one(env, "SELECT * FROM invites WHERE token = ?", await sha256(`invite|${token}`));
  return row && !row.used_at && row.expires > now ? row : null;
}

/** Too many tries at invite links from one network in an hour, so links cannot be guessed. */
async function joinLimited(request, env, now) {
  const net = await network(request, env, now);
  return !(await allow(env, `join|${net.now}`, LIMITS.joinsPerHour, HOUR, now));
}

/** What the person who opened an invite link sees before making an account. */
async function inviteInfo(request, env, url, now) {
  if (await joinLimited(request, env, now)) return json({ error: "rate_limited" }, 429);
  const token = request.method === "POST" ? (await readJson(request, 1024)).token : url.searchParams.get("t");
  const row = await inviteFor(env, token, now);
  if (!row) return json({ error: "invite" }, 404);
  return json({ email: row.email, perms: readPerms(row.perms), areas: AREAS, expiresAt: iso(row.expires), mail: mailReady(env) });
}

/** With mail set up, a six-digit code goes to the invited email, so the account is made from that mailbox. */
async function joinCode(request, env, now) {
  if (await joinLimited(request, env, now)) return json({ error: "rate_limited" }, 429);
  const body = await readJson(request, 1024);
  const row = await inviteFor(env, body.token, now);
  if (!row) return json({ error: "invite" }, 404);
  if (!mailReady(env)) return json({ error: "no_mail" }, 409);
  const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
  await run(env, `INSERT INTO codes (email, code, expires, tries) VALUES (?, ?, ?, 0)
    ON CONFLICT (email) DO UPDATE SET code = excluded.code, expires = excluded.expires, tries = 0`,
    row.email, await sha256(`code|${row.email}|${code}`), now + LIMITS.codeMs);
  const sent = await sendMail(env, row.email, "[밤낮 관리자] 메일 확인 코드",
    `밤낮 관리자 계정을 만들려면 아래 코드를 입력해 주세요.\n\n${code}\n\n15분 동안 쓸 수 있어요. 직접 요청하지 않았다면 이 메일은 지워 주세요.`);
  if (!sent) return json({ error: "mail_failed" }, 502);
  return json({ ok: true, expiresAt: iso(now + LIMITS.codeMs) });
}

/**
 * An invited person makes their account. It waits for the owner's approval before it can sign in, and
 * starts with the permissions the owner chose when inviting.
 */
async function join(request, env, now) {
  if (await joinLimited(request, env, now)) return json({ error: "rate_limited" }, 429);
  const body = await readJson(request, 2048);
  const row = await inviteFor(env, body.token, now);
  if (!row) return json({ error: "invite" }, 404);
  const name = adminName(body.name);
  const password = newPassword(body.password, row.email);
  if (mailReady(env)) {
    const c = await one(env, "SELECT * FROM codes WHERE email = ?", row.email);
    if (!c || c.expires <= now || c.tries >= LIMITS.codeTries) return json({ error: "code_expired" }, 400);
    if (typeof body.code !== "string" || (await sha256(`code|${row.email}|${body.code.trim()}`)) !== c.code) {
      await run(env, "UPDATE codes SET tries = tries + 1 WHERE email = ?", row.email);
      return json({ error: "code", left: Math.max(0, LIMITS.codeTries - c.tries - 1) }, 400);
    }
  }
  if (await one(env, "SELECT id FROM admins WHERE email = ?", row.email)) return json({ error: "exists" }, 409);
  const id = `a${randomId(15)}`;
  await batch(env, [
    stmt(env, `INSERT INTO admins (id, email, name, pw, perms, status, created_at, approved_at, last_login)
      VALUES (?, ?, ?, ?, ?, 'pending', ?, NULL, NULL)`, id, row.email, name, await hashPassword(password), row.perms, now),
    stmt(env, "UPDATE invites SET used_at = ? WHERE email = ?", now, row.email),
    stmt(env, "DELETE FROM codes WHERE email = ?", row.email),
  ]);
  await audit(Object.assign(Object.create(env), { actor: { name, email: row.email } }), now, "join", row.email);
  const owner = cleanEmail(env.OWNER_EMAIL);
  if (owner) await sendMail(env, owner, "[밤낮 관리자] 승인을 기다리는 관리자", `${name} (${row.email}) 님이 관리자 계정을 만들었어요. 개발자 앱의 관리자 화면에서 승인해 주세요.`);
  // [ADMIN-19] The owner's browsers hear of it too, without the email.
  await notifyAdmins(env, "join", { title: "승인을 기다리는 관리자", body: `${name} 님이 관리자 계정을 만들었어요`, url: "/admin/#/admins" }, now);
  return json({ ok: true, status: "pending" }, 201);
}

/** The owner approves, pauses, resumes or changes what an admin can do; it applies on their next call. */
async function updateAdmin(request, env, id, now) {
  const body = await readJson(request, 2048);
  const a = await one(env, "SELECT * FROM admins WHERE id = ?", id);
  if (!a) return json({ error: "not_found" }, 404);
  const stmts = [];
  const notes = [];
  if (body.perms !== undefined) {
    const perms = permsFrom(body);
    stmts.push(stmt(env, "UPDATE admins SET perms = ? WHERE id = ?", JSON.stringify(perms), id));
    notes.push(Object.entries(perms).filter(([, l]) => l !== "none").map(([k, l]) => `${k}:${l}`).join(" ") || "none");
  }
  if (body.status !== undefined) {
    if (!["active", "suspended"].includes(body.status)) refuse("status", "status");
    if (body.status === "active" && a.status === "pending") stmts.push(stmt(env, "UPDATE admins SET approved_at = ? WHERE id = ?", now, id));
    stmts.push(stmt(env, "UPDATE admins SET status = ? WHERE id = ?", body.status, id));
    if (body.status === "suspended") stmts.push(stmt(env, "DELETE FROM admin_sessions WHERE admin = ?", id));
    notes.push(body.status === "active" ? (a.status === "pending" ? "approve" : "resume") : "suspend");
  }
  if (!stmts.length) refuse("status", "status");
  await batch(env, stmts);
  await audit(env, now, "admin_update", a.email, notes.join(" / "));
  return json(await adminList(env));
}

async function removeAdmin(env, id, now) {
  const a = await one(env, "SELECT email FROM admins WHERE id = ?", id);
  if (!a) return json({ error: "not_found" }, 404);
  await batch(env, [
    stmt(env, "DELETE FROM admin_sessions WHERE admin = ?", id),
    stmt(env, "DELETE FROM admins WHERE id = ?", id),
    // What they had taken on is free again for the others; an item's history says so.
    stmt(env, "UPDATE inquiries SET assignee = NULL, assignee_name = NULL WHERE assignee = ?", id),
    stmt(env, `INSERT INTO dev_history (item_id, at, actor_id, actor, action, detail)
      SELECT id, ?, ?, ?, 'assign', json_object('assignee', json_array(COALESCE(assignee_name, ''), '')) FROM dev_items WHERE assignee = ?`,
      now, env.actor?.id ?? "", actorName(env), id),
    stmt(env, "UPDATE dev_items SET assignee = NULL, assignee_name = NULL WHERE assignee = ?", id),
    // The email can be invited again later.
    stmt(env, "DELETE FROM invites WHERE email = ?", a.email),
    // [MON-09] Their phones stop getting 프로 with them.
    stmt(env, "DELETE FROM admin_devices WHERE admin = ?", id),
    stmt(env, "DELETE FROM link_codes WHERE admin = ?", id),
    // [ADMIN-19] Their browsers stop getting web push.
    stmt(env, "DELETE FROM push_subscriptions WHERE admin = ?", id),
  ]);
  await audit(env, now, "admin_remove", a.email);
  return json(await adminList(env));
}

/** An admin changes their own password; their other sign-ins end. The owner's lives in Cloudflare. */
async function changePassword(request, env, actor, token, now) {
  if (actor.owner) return json({ error: "owner_password" }, 409);
  const body = await readJson(request, 1024);
  const a = await one(env, "SELECT * FROM admins WHERE id = ?", actor.id);
  if (typeof body.current !== "string" || !(await checkPassword(body.current, a.pw))) return json({ error: "wrong" }, 400);
  const next = newPassword(body.next, a.email);
  await batch(env, [
    stmt(env, "UPDATE admins SET pw = ? WHERE id = ?", await hashPassword(next), a.id),
    stmt(env, "DELETE FROM admin_sessions WHERE admin = ? AND token != ?", a.id, token),
  ]);
  await audit(env, now, "password", a.id);
  return json({ ok: true });
}

/** [ADMIN-17] The activity log's 종류 filter: which actions each one holds (a trailing * is a prefix). */
const AUDIT_KINDS = {
  login: ["login", "login_failed", "logout_all", "password"],
  admins: ["invite*", "join", "admin_*", "device_*"],
  review: ["adopt", "reject", "reject_undo", "answer"],
  companies: ["company_*", "alias_*", "merge", "rule_*"],
  rewards: ["reward_*", "pass_move", "trial_stop"],
  community: ["post_*", "rate_*"],
  inquiries: ["inquiry_*", "template_*"],
  dev: ["dev_*"],
  ops: ["notice_*", "switch_*", "ops_*", "share_remove", "launch_*"],
  settings: ["settings", "sla"],
  // AM 1.28 [FLAG-21]
  flags: ["flag_*", "policy_edit", "launch_*", "type_*", "switch_*", "auto_rest", "auto_resume"],
};

/** A KST day (YYYY-MM-DD) as the time it starts, or null. */
const kstStart = (d) => (typeof d === "string" && /^\d{4}-\d{2}-\d{2}$/.test(d) && Number.isFinite(Date.parse(`${d}T00:00:00+09:00`)) ? Date.parse(`${d}T00:00:00+09:00`) : null);

/** [ADMIN-17] someone@example.com as s•••@example.com, for whoever is not the owner. */
const maskEmail = (e) => e.replace(/^([^@])[^@]*@/, "$1•••@");

/**
 * Activity, newest first, 50 at a time; [before] continues from an entry's id. [ADMIN-17] Filtered by
 * who (an admin's id, "owner" or "me"), 종류 and KST days [from]..[to]; each entry says in words what it
 * was about (a company's name, a question's first words, a 개발사항) and where to open it.
 */
async function auditLog(env, url) {
  const p = url.searchParams;
  const before = Number(p.get("before"));
  const actor = p.get("actor") === "me" ? env.actor?.id ?? "" : /^(owner|a[A-Za-z0-9]{15})$/.test(p.get("actor") ?? "") ? p.get("actor") : "";
  const kind = Object.hasOwn(AUDIT_KINDS, p.get("kind") ?? "") ? p.get("kind") : "";
  const from = kstStart(p.get("from"));
  const to = kstStart(p.get("to"));
  const where = ["a.id < ?"];
  const args = [Number.isInteger(before) && before > 0 ? before : Number.MAX_SAFE_INTEGER];
  if (actor) {
    where.push("a.actor_id = ?");
    args.push(actor);
  }
  if (kind) {
    where.push(`(${AUDIT_KINDS[kind].map((k) => (k.endsWith("*") ? "a.action LIKE ? ESCAPE '\\'" : "a.action = ?")).join(" OR ")})`);
    args.push(...AUDIT_KINDS[kind].map((k) => (k.endsWith("*") ? `${k.slice(0, -1).replace(/_/g, "\\_")}%` : k)));
  }
  if (from !== null) {
    where.push("a.at >= ?");
    args.push(from);
  }
  if (to !== null) {
    where.push("a.at < ?");
    args.push(to + DAY);
  }
  const [rows, admins] = (await batch(env, [
    stmt(env, `${AUDIT_SELECT} WHERE ${where.join(" AND ")} ORDER BY a.id DESC LIMIT 51`, ...args),
    stmt(env, "SELECT id, name, email FROM admins ORDER BY name"),
  ])).map((r) => r.results);
  const list = rows.slice(0, 50);
  const owner = Boolean(env.actor?.owner);
  const byEmail = new Map(admins.map((x) => [x.email, x.name]));
  return {
    entries: list.map((r) => {
      const email = /^[^\s@]+@[^\s@]+$/.test(r.target) ? r.target : null;
      const about = email ? { label: byEmail.get(email) ?? maskEmail(email), link: "#/admins" } : auditAbout(r);
      return { id: r.id, at: iso(r.at), action: r.action, target: email && !owner ? maskEmail(email) : r.target, detail: auditDetail(r), actor: plainActor(r.actor),
        actorId: r.actor_id || null, ...about };
    }),
    more: rows.length > 50,
    kinds: Object.keys(AUDIT_KINDS),
    actors: [{ id: "owner", name: OWNER.name }, ...admins.map((x) => ({ id: x.id, name: x.name }))],
  };
}

/** [ADMIN-17] What an entry's detail says, in words where it was stored as data. */
function auditDetail(r) {
  if (r.action !== "company_update") return r.detail;
  try {
    const b = JSON.parse(r.detail);
    const out = [];
    if (typeof b.name === "string") out.push(`이름 → ${b.name}`);
    if (typeof b.hidden === "boolean") out.push(b.hidden ? "검색에서 숨김" : "검색에 보임");
    for (const k of ["kind", "job", "region"]) if (typeof b[k] === "string") out.push(`${{ kind: "종류", job: "직군", region: "지역" }[k]} → ${b[k]}`);
    return out.join(" · ") || r.detail;
  } catch {
    return r.detail;
  }
}

/**
 * [ADMIN-17] Activity rows with the names behind their ids read in the same statement: the company of a
 * setup, post or company change, the first words of a question, whether a 개발사항 is still there.
 */
const AUDIT_SELECT = `SELECT a.id, a.at, a.action, a.target, a.detail, a.actor, a.actor_id,
    (SELECT c.name FROM companies c WHERE c.id = substr(CASE WHEN a.action = 'post_autohide' THEN a.detail ELSE a.target END, 1, 12)) AS company_name,
    (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = CASE WHEN a.action IN ('dev_link', 'dev_unlink') THEN a.detail
      WHEN substr(a.action, 1, 8) = 'inquiry_' THEN a.target END ORDER BY m.id LIMIT 1) AS inquiry_first,
    (SELECT 1 FROM dev_items d WHERE d.id = a.detail) AS dev_kept,
    -- AX 1.30 [ADMX-01] a reward's company (reward_auto and the others name the reward's id)
    (SELECT c.name FROM rewards w JOIN reports p ON p.id = w.report_id JOIN companies c ON c.id = p.company_id
      WHERE substr(a.action, 1, 7) = 'reward_' AND w.id = a.target) AS reward_company
  FROM audit a`;

/** [ADMIN-17] What an entry was about, in words, and where it opens (or null). */
function auditAbout(r) {
  const words = (t) => [...(t ?? "")].slice(0, 40).join("");
  if (r.action.startsWith("inquiry_") && /^[A-Za-z0-9]{22}$/.test(r.target)) {
    return r.inquiry_first !== null ? { label: `문의 "${words(r.inquiry_first)}"`, link: `#/inquiries/${r.target}` } : { label: "지워진 문의", link: null };
  }
  if (r.action.startsWith("dev_")) return { label: r.target, link: r.dev_kept ? `#/dev/${r.detail}` : null };
  // AX 1.30 [ADMX-01] A reward by the company it was for; its id (a hash) is never the words.
  if (r.action.startsWith("reward_")) return { label: r.reward_company ? `${r.reward_company} 보상` : "보상", link: "#/rewards" };
  if (r.company_name !== null && r.company_name !== undefined) {
    const review = ["adopt", "reject", "reject_undo", "answer"].includes(r.action);
    const post = r.action.startsWith("post_");
    return { label: r.company_name, link: post ? "#/community" : review ? "#/review" : `#/companies/${r.target.slice(0, 12)}` };
  }
  return { label: "", link: null };
}

/**
 * AN 1.28 [ADMUX-37] The counts behind the developer app's menu numbers, each with the area an admin needs to see
 * it. /badges reads them in one trip (AT 1.29: /overview, which read them too, is gone).
 */
const BADGE_SQL = {
  pending: ["review", "SELECT COUNT(*) AS n FROM (SELECT DISTINCT company_id, grp FROM reports WHERE status = 'pending')"],
  asks: ["review", "SELECT COUNT(*) AS n FROM reports WHERE status = 'ask'"],
  flagged: ["review", `SELECT COUNT(*) AS n FROM rules r WHERE r.withdrawn = 0 AND r.superseded = 0
      AND EXISTS (SELECT 1 FROM votes v WHERE v.company_id = r.company_id AND v.grp = r.grp AND v.ok = 0)`],
  inquiries: ["inquiries", "SELECT COUNT(*) AS n FROM inquiries WHERE status = 'open'"],
  devTodo: ["dev", "SELECT COUNT(*) AS n FROM dev_items WHERE deleted_at IS NULL AND status = 'todo'"],
  owed: ["rewards", "SELECT COUNT(*) AS n FROM rewards WHERE status = 'owed'"],
  flaggedPosts: ["community", "SELECT COUNT(*) AS n FROM posts WHERE kept = 0 AND (flags > 0 OR hide_reason = 'flags')"],
  // [ADMUX-18] Departments with two or more live versions: setups waiting beside the rule in use, or two waiting.
  versionConflicts: ["companies", `SELECT COUNT(*) AS n FROM (SELECT company_id, dept_norm, COUNT(DISTINCT grp) AS g FROM reports
      WHERE status = 'pending' GROUP BY company_id, dept_norm) p WHERE p.g >= 2 OR EXISTS (SELECT 1 FROM rules r
      WHERE r.company_id = p.company_id AND r.dept_norm = p.dept_norm AND r.withdrawn = 0 AND r.superseded = 0)`],
  waiting: ["owner", "SELECT COUNT(*) AS n FROM admins WHERE status = 'pending'"],
  // [MON-09] Admins' phones waiting for the owner's 승인.
  phones: ["owner", "SELECT COUNT(*) AS n FROM admin_devices WHERE status = 'pending'"],
  // AT 1.29 [ADMCP-04] 문제 신고 not closed that nobody copied for development yet (오늘's tile and 문의's strip).
  bugsUncopied: ["inquiries", "SELECT COUNT(*) AS n FROM inquiries WHERE kind = 'bug' AND copied_at IS NULL AND status != 'closed'"],
};

// AN 1.28 시작

/**
 * [ADMUX-37] An admin's badge numbers are kept this long in the worker's memory, per database and per admin
 * with their permissions; any request that sends something (badgesChanged) makes the next read fresh.
 */
const BADGES = { keepMs: 15_000, maxKept: 200 };
let badgeEpoch = 0;
const badgeMemory = new WeakMap();

function badgesChanged() {
  badgeEpoch++;
}

/**
 * [ADMUX-37] The developer app's menu numbers and who is signed in, nothing else: what the app polls every
 * 30 or 90 seconds and reads beside (never before) the screen's own data.
 */
async function badges(env, actor, now) {
  let kept = badgeMemory.get(env.DB);
  if (!kept) badgeMemory.set(env.DB, (kept = new Map()));
  const key = `${actor.id}|${actor.owner ? 1 : 0}|${JSON.stringify(actor.perms ?? {})}`;
  const hit = kept.get(key);
  if (hit && hit.epoch === badgeEpoch && now - hit.at < BADGES.keepMs) return hit.data;
  const epoch = badgeEpoch;
  // AM 1.28 [FLAG-22] billingLive is the billing flag by the server's view; the flag store rides in the same trip.
  const q = Object.fromEntries(Object.entries(FLAG_STORE_SQL).map(([k, sql]) => [k, [sql]]));
  for (const [k, [area, sql]] of Object.entries(BADGE_SQL)) if (area === "owner" ? actor.owner : can(actor, area)) q[k] = [sql];
  if (can(actor, "ops")) q.opsDone = ["SELECT value FROM settings WHERE key = ?", `ops:${kstDay(now).slice(0, 7)}`];
  const keys = Object.keys(q);
  const res = await batch(env, keys.map((k) => stmt(env, ...q[k])));
  const got = Object.fromEntries(keys.map((k, i) => [k, res[i].results]));
  const store = storeFrom(got.flagOverrides, got.flagChecks, got.flagPolicy, got.flagState);
  const data = { me: me(actor), build: adminBuild(), billingLive: serverEval(store, "billing", now).on };
  for (const k of keys) if (BADGE_SQL[k] && k !== "waiting" && k !== "phones") data[k] = got[k][0].n;
  if (actor.owner) data.admins = { waiting: got.waiting[0].n, phones: got.phones[0].n };
  if (got.opsDone) data.opsLeft = opsItems(kstDay(now).slice(0, 7), got.opsDone[0]?.value).filter((i) => !i.done).length;
  // 1.28 통합 [ADMUX-40][FLAG-28] 출시 › 기능 관리's number (flagsWaiting, the same as 1.27 /overview's flagsDue) and the
  // legal flags on for under 24 hours (오늘's top card), for admins who may see 기능 관리.
  Object.assign(data, await flagOverview(env, actor, now, store));
  if (kept.size >= BADGES.maxKept) kept.clear();
  kept.set(key, { at: now, epoch, data });
  return data;
}

/**
 * [ADMUX-33][ADMUX-37] The 오늘 screen, read only while it is shown: one 할 일 list (받은함's, nearest deadline
 * first), the numbers above it, who holds how many questions and the recent activity.
 */
async function today(env, actor, now) {
  const start = Date.parse(`${kstDay(now)}T00:00:00+09:00`);
  const q = {
    doneToday: ["SELECT COUNT(*) AS n FROM audit WHERE actor_id = ? AND at >= ? AND action NOT IN ('login', 'login_failed')", actor.id, start],
  };
  for (const [k, v] of Object.entries(inboxQueries(actor, now))) q[`inbox_${k}`] = Array.isArray(v) ? v : [v];
  if (can(actor, "audit")) q.recent = [`${AUDIT_SELECT} WHERE a.action NOT IN ('login', 'login_failed') ORDER BY a.id DESC LIMIT 10`];
  if (can(actor, "inquiries")) {
    q.inquiriesToday = ["SELECT COUNT(*) AS n FROM inquiries WHERE created_at >= ?", start];
    q.inquiriesYesterday = ["SELECT COUNT(*) AS n FROM inquiries WHERE created_at >= ? AND created_at < ?", start - DAY, start];
    q.inquiriesBad = ["SELECT COUNT(*) AS n FROM inquiries WHERE rating = -1 AND status != 'closed'"];
    q.assigned = ["SELECT assignee AS id, assignee_name AS name, COUNT(*) AS n FROM inquiries WHERE status = 'open' AND assignee IS NOT NULL GROUP BY assignee ORDER BY n DESC"];
  }
  if (can(actor, "review")) q.reports = ["SELECT COUNT(*) AS n FROM reports"];
  if (can(actor, "community")) q.posts = ["SELECT COUNT(*) AS n FROM posts WHERE status = 'shown'"];
  if (can(actor, "companies")) {
    q.companies = ["SELECT COUNT(*) AS n FROM companies WHERE merged_into IS NULL"];
    q.rules = ["SELECT COUNT(*) AS n FROM rules WHERE withdrawn = 0 AND superseded = 0"];
    q.versionConflicts = [BADGE_SQL.versionConflicts[1]]; // AX 1.30 [ADMX-01] 정할 판 for 주의 필요
  }
  // AX 1.30 [ADMX-01][ADMX-07] Seven days of each count for the tiles' lines and the alerts, in the same trip.
  Object.assign(q, trendQueries({ inquiries: can(actor, "inquiries"), review: can(actor, "review"), community: can(actor, "community"),
    metrics: can(actor, "metrics") }, start));
  const keys = Object.keys(q);
  const res = await batch(env, keys.map((k) => stmt(env, ...q[k])));
  const got = Object.fromEntries(keys.map((k, i) => [k, res[i].results]));
  const inbox = Object.fromEntries(keys.filter((k) => k.startsWith("inbox_")).map((k) => [k.slice(6), got[k]]));
  const out = { todo: inboxItems(inbox, actor, now), doneToday: got.doneToday[0].n };
  for (const k of ["inquiriesToday", "inquiriesYesterday", "inquiriesBad", "reports", "posts", "companies", "rules"]) if (got[k]) out[k] = got[k][0].n;
  if (inbox.inquiries) {
    // How many waiting questions reach their answer target before today ends.
    const sla = readSla(inbox.sla);
    out.slaLateToday = inbox.inquiries.filter((r) => r.last === "user" && r.updated_at + sla[slaKey(r.kind)] * HOUR < start + DAY).length;
    out.assigned = got.assigned.map((a) => ({ id: a.id, name: a.name ?? "", n: a.n }));
  }
  if (got.recent) {
    out.recent = got.recent.map((r) => ({ id: r.id, at: iso(r.at), action: r.action, target: /@/.test(r.target) && !actor.owner ? maskEmail(r.target) : r.target,
      detail: auditDetail(r), actor: plainActor(r.actor), ...auditAbout(r) }));
  }
  // AX 1.30 [ADMX-01][ADMX-07] trend: 7 days per count (today last); alerts: what needs a look now (주의 필요).
  out.trend = trendFrom(got, start);
  out.alerts = alertsFrom({ trend: out.trend, todo: out.todo.items, now, versions: got.versionConflicts?.[0]?.n ?? 0, shape: got.ax_shape?.[0]?.n ?? 0 });
  if (got.versionConflicts) out.versionConflicts = got.versionConflicts[0].n;
  return out;
}

/** [ADMUX-37] What the screens share and rarely changes: the 채택 settings, the answer targets and the reason words. */
async function basics(env) {
  const [settings, sla] = await batch(env, [env.DB.prepare(`SELECT key, value FROM settings WHERE key IN (${SETTING_KEYS})`), env.DB.prepare(SLA_SQL)]);
  return { settings: readSettings(settings.results), sla: readSla(sla.results), doubt: DOUBT, reasons: REASONS, answers: ANSWERS,
    hideReasons: HIDE_REASONS, flagReasons: FLAGS };
}

// AN 1.28 끝

/** Rows of [list] grouped by company and setup. */
function byGroup(list) {
  const out = new Map();
  for (const r of list) {
    const key = `${r.company_id}/${r.grp}`;
    if (!out.has(key)) out.set(key, []);
    out.get(key).push(r);
  }
  return out;
}

/** The score of each setup in [reports] (pending or adopted) and [votes], keyed company/setup. */
function tallies(reports, votes) {
  const sent = byGroup(reports);
  const voted = byGroup(votes);
  const keys = new Set([...sent.keys(), ...voted.keys()]);
  const out = new Map();
  for (const key of keys) {
    const v = voted.get(key) ?? [];
    const rows = [...(sent.get(key) ?? []).map((r) => ({ device: r.device, points: r.points, cluster: r.cluster })),
      ...v.filter((x) => x.ok === 1).map((x) => ({ device: x.device, points: x.verified === 1 ? 2 : 1, cluster: x.cluster }))];
    out.set(key, tally(rows, v.filter((x) => x.ok === 0).map((x) => x.device)));
  }
  return out;
}

const NO_SCORE = { points: 0, persons: 0, networks: 0, bad: 0 };

/**
 * Setups waiting for a decision, the ones with most behind them first, and rules people doubt. Everything
 * is read in one trip to the database, however many setups wait.
 */
async function queue(env) {
  // The same 60 setups in every statement: one order with nothing left to chance, read in one batch.
  const waiting = `WITH g AS (SELECT company_id, grp, MAX(created_at) AS last FROM reports WHERE status = 'pending'
    GROUP BY company_id, grp ORDER BY last DESC, company_id, grp LIMIT 60)`;
  const doubted = `WITH f AS (SELECT r.id FROM rules r WHERE r.withdrawn = 0 AND r.superseded = 0
    AND EXISTS (SELECT 1 FROM votes v WHERE v.company_id = r.company_id AND v.grp = r.grp AND v.ok = 0) ORDER BY r.id LIMIT 30),
    fg AS (SELECT DISTINCT r.company_id, r.grp FROM rules r JOIN f ON f.id = r.id)`;
  const [settingRows, groups, companyRows, reports, votes, posts, ruleRows, rejected, flaggedRows, flaggedReports, flaggedVotes, askRows] =
    (await batch(env, [
      env.DB.prepare(`SELECT key, value FROM settings WHERE key IN (${SETTING_KEYS})`),
      env.DB.prepare(`${waiting} SELECT * FROM g`),
      env.DB.prepare(`${waiting} SELECT id, name, hidden FROM companies WHERE id IN (SELECT company_id FROM g)`),
      env.DB.prepare(`${waiting} SELECT r.* FROM reports r JOIN g ON g.company_id = r.company_id AND g.grp = r.grp
        WHERE r.status IN ('pending', 'adopted')`),
      env.DB.prepare(`${waiting} SELECT v.company_id, v.grp, v.device, v.ok, v.verified, v.cluster FROM votes v
        JOIN g ON g.company_id = v.company_id AND g.grp = v.grp`),
      env.DB.prepare(`${waiting} SELECT p.company_id, p.grp, COUNT(*) AS n FROM posts p JOIN g ON g.company_id = p.company_id AND g.grp = p.grp
        WHERE p.status = 'shown' GROUP BY p.company_id, p.grp`),
      env.DB.prepare(`${waiting} SELECT id, company_id, dept_norm, grp, version, setup, withdrawn, superseded FROM rules
        WHERE company_id IN (SELECT company_id FROM g)`),
      env.DB.prepare(`${waiting} SELECT DISTINCT r.company_id, r.grp FROM reports r JOIN g ON g.company_id = r.company_id AND g.grp = r.grp
        WHERE r.status = 'rejected'`),
      env.DB.prepare(`${doubted}
        SELECT r.id, r.company_id, r.grp, r.dept, r.version, r.setup, c.name,
          SUM(CASE WHEN v.ok = 0 AND v.why = 'name' THEN 1 ELSE 0 END) AS names,
          SUM(CASE WHEN v.ok = 0 AND v.why = 'old' THEN 1 ELSE 0 END) AS old
        FROM f JOIN rules r ON r.id = f.id JOIN companies c ON c.id = r.company_id
          JOIN votes v ON v.company_id = r.company_id AND v.grp = r.grp
        GROUP BY r.id`),
      env.DB.prepare(`${doubted} SELECT r.company_id, r.grp, r.device, r.points, r.cluster FROM reports r
        JOIN fg ON fg.company_id = r.company_id AND fg.grp = r.grp WHERE r.status IN ('pending', 'adopted')`),
      env.DB.prepare(`${doubted} SELECT v.company_id, v.grp, v.device, v.ok, v.verified, v.cluster FROM votes v
        JOIN fg ON fg.company_id = v.company_id AND fg.grp = v.grp`),
      env.DB.prepare(`SELECT p.id, p.company_id, p.dept, p.note, p.verified, p.setup, p.created_at, c.name
        FROM reports p JOIN companies c ON c.id = p.company_id WHERE p.status = 'ask' ORDER BY p.created_at LIMIT 60`),
    ])).map((r) => r.results);
  const { adoptPoints, adoptAgree } = readSettings(settingRows);
  const companiesById = new Map(companyRows.map((c) => [c.id, c]));
  const sent = byGroup(reports);
  const scores = tallies(reports, votes);
  const postCounts = new Map(posts.map((p) => [`${p.company_id}/${p.grp}`, p.n]));
  const turnedDown = new Set(rejected.map((r) => `${r.company_id}/${r.grp}`));
  const out = [];
  for (const g of groups) {
    const key = `${g.company_id}/${g.grp}`;
    const c = companiesById.get(g.company_id);
    const mine = sent.get(key) ?? [];
    // The report a setup is shown and adopted from, as representative() picks it.
    const rep = [...mine].sort((a, b) => b.verified - a.verified || b.created_at - a.created_at)[0];
    const s = scores.get(key) ?? NO_SCORE;
    const pending = mine.filter((r) => r.status === "pending").sort((a, b) => a.created_at - b.created_at);
    const rules = ruleRows.filter((r) => r.company_id === g.company_id);
    const active = rules.filter((r) => r.dept_norm === rep.dept_norm && r.withdrawn === 0 && r.superseded === 0)
      .sort((a, b) => b.version - a.version)[0] ?? null;
    // As blocker() decides it.
    const why = active ? "active" : rules.some((r) => r.grp === g.grp && r.withdrawn === 1) ? "withdrawn" : turnedDown.has(key) ? "rejected" : null;
    const lines = describe(JSON.parse(rep.setup));
    out.push({
      company: { id: c.id, name: c.name, hidden: c.hidden === 1 },
      dept: rep.dept,
      setup: g.grp,
      points: s.points,
      persons: s.persons,
      networks: s.networks,
      bad: s.bad,
      threshold: adoptPoints,
      ...trust(s, adoptPoints),
      // [COMM-07] More 달라요 than the setting allows: auto adoption waits for the developer.
      doubted: s.persons * 100 < adoptAgree * (s.persons + s.bad),
      posts: postCounts.get(key) ?? 0,
      // Enough points, but another rule is in use, this one was turned down, everyone sent it from one
      // network or too many say 달라요: only the developer decides.
      blocked: (Boolean(why) || s.networks < 2 || s.persons * 100 < adoptAgree * (s.persons + s.bad)) && s.persons >= 2 && s.points >= adoptPoints,
      turnedDown: why === "withdrawn" || why === "rejected" ? why : null,
      replaces: active ? { id: active.id, version: active.version } : null,
      // AI 1.27 [PLAZA-30] claim: an older app's 명세서 tick with nothing behind it, counted as a plain report.
      reports: pending.map((r) => ({ id: r.id, verified: r.verified === 1, ...(r.claim === 1 ? { claim: true } : {}), note: r.note, at: iso(r.created_at) })),
      lines,
      changes: active ? diffLines(describe(JSON.parse(active.setup)), lines) : null,
      // AX 1.30 [ADMX-04] BD 1.30 [PLZD-04] keeps a setup shaped unlike its department's rule as pending with reason 'shape'.
      ...(mine.some((r) => r.status === "pending" && r.reason === "shape") ? { shape: true } : {}),
    });
  }
  out.sort((a, b) => b.blocked - a.blocked || b.points - a.points || cmp(a.reports[0].at, b.reports[0].at));
  const flaggedScores = tallies(flaggedReports, flaggedVotes);
  const flagged = flaggedRows.map((r) => {
    const s = flaggedScores.get(`${r.company_id}/${r.grp}`) ?? NO_SCORE;
    return { rule: r.id, company: { id: r.company_id, name: r.name }, dept: r.dept, version: r.version, good: s.persons, bad: s.bad,
      names: r.names, old: r.old, lines: describe(JSON.parse(r.setup)) };
  });
  flagged.sort((a, b) => b.bad - a.bad);
  const asks = askRows.map((a) => ({
    id: a.id, company: { id: a.company_id, name: a.name }, dept: a.dept, note: a.note, verified: a.verified === 1, at: iso(a.created_at),
    lines: describe(JSON.parse(a.setup)),
  }));
  return { groups: out, flagged, asks };
}

/**
 * [EXTRA-06] The developer's answer to a way the app cannot set yet. One the app will learn can earn
 * the sender the same reward as an adopted setup.
 */
async function answer(request, env, id, now) {
  const body = await readJson(request, 1024);
  if (!Object.hasOwn(ANSWERS, body.answer)) refuse("answer", "answer");
  const r = await one(env, "SELECT id, device FROM reports WHERE id = ? AND status = 'ask'", id);
  if (!r) return json({ error: "not_found" }, 404);
  const { rewardMonths } = await settings(env);
  const stmts = [stmt(env, "UPDATE reports SET status = 'answered', reason = ?, decided_at = ? WHERE id = ?", body.answer, now, id)];
  const reward = body.answer === "planned" && body.reward === true && rewardMonths > 0;
  if (reward) {
    stmts.push(stmt(env, `INSERT OR IGNORE INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at)
      VALUES (?, ?, '', ?, ?, 'owed', '', ?, NULL)`, `w${id}`, id, r.device, rewardMonths, now));
  }
  await batch(env, stmts);
  await audit(env, now, "answer", id, `${body.answer}${reward ? "+reward" : ""}`);
  return json({ ok: true, reward });
}

async function decide(request, env, companyId, grp, action, now) {
  const waiting = await one(env, "SELECT COUNT(*) AS n FROM reports WHERE company_id = ? AND grp = ? AND status = 'pending'", companyId, grp);
  if (!waiting.n) return json({ error: "not_found" }, 404);
  if (action === "adopt") {
    const rule = await adopt(env, companyId, grp, "admin", now);
    await audit(env, now, "adopt", `${companyId}/${grp}`, rule);
    return json({ ok: true, rule });
  }
  const body = await readJson(request, 1024);
  const reason = Object.hasOwn(REASONS, body.reason) ? body.reason : "unclear";
  await run(env, "UPDATE reports SET status = 'rejected', reason = ?, decided_at = ? WHERE company_id = ? AND grp = ? AND status = 'pending'",
    reason, now, companyId, grp);
  await audit(env, now, "reject", `${companyId}/${grp}`, reason);
  return json({ ok: true });
}

/**
 * [ADMIN-15] 되돌리기 for a 거절: within 10 minutes the reports that 거절 turned down wait for review
 * again, as if it never happened. Later, or for an older 거절, it is too late (a sender's app may have
 * shown it already).
 */
async function reopen(env, companyId, grp, now) {
  const last = await one(env, "SELECT MAX(decided_at) AS at FROM reports WHERE company_id = ? AND grp = ? AND status = 'rejected'", companyId, grp);
  if (!last?.at) return json({ error: "not_found" }, 404);
  if (now - last.at > REOPEN_MS) return json({ error: "too_late" }, 409);
  const r = await run(env, `UPDATE reports SET status = 'pending', reason = NULL, decided_at = NULL
    WHERE company_id = ? AND grp = ? AND status = 'rejected' AND decided_at = ?`, companyId, grp, last.at);
  await audit(env, now, "reject_undo", `${companyId}/${grp}`, String(r.meta.changes));
  return json({ ok: true, reopened: r.meta.changes });
}

const REOPEN_MS = 10 * 60 * 1000;

/** [ADMIN-16] Rows read with one more than a page: the page, and where the next one starts. */
const sqlPage = (rows, { after, limit }) => ({ items: rows.slice(0, limit), next: rows.length > limit ? after + limit : null });

/** [ADMIN-16] A company name typed into a list's search, as LIKE on companies.norm ('' for none). */
function companyLike(q) {
  const n = normName(q).slice(0, 40);
  return n ? `%${n.replace(/[\\%_]/g, (c) => `\\${c}`)}%` : "";
}

/** AX 1.30 [ADMX-06] The company table's sorts: a column, biggest first (이름 in order), newest company last to break ties. */
const COMPANY_SORTS = {
  new: "c.created_at DESC", name: "c.name, c.created_at DESC", rules: "rules DESC, c.created_at DESC", pending: "pending DESC, c.created_at DESC",
  reports: "reports DESC, c.created_at DESC", members: "members DESC, c.created_at DESC", uses: "uses30 DESC, c.created_at DESC",
  bad: "(bad * 1.0 / (ok + bad + 1)) DESC, bad DESC, c.created_at DESC",
};

async function companies(env, query, params = new URLSearchParams()) {
  const pg = page(params);
  const sort = Object.hasOwn(COMPANY_SORTS, params.get("sort") ?? "") ? params.get("sort") : "new";
  const q = normName(query).slice(0, 40);
  const like = `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  const rows = await all(env, `
    SELECT c.*,
      (SELECT COUNT(*) FROM aliases a WHERE a.company_id = c.id) AS aliases,
      (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 0) AS rules,
      (SELECT COUNT(DISTINCT p.grp) FROM reports p WHERE p.company_id = c.id AND p.status = 'pending') AS pending,
      (SELECT COUNT(*) FROM reports p WHERE p.company_id = c.id) AS reports,
      (SELECT COUNT(*) FROM members m WHERE m.company_id = c.id AND m.left = 0) AS members,
      -- AX 1.30 [ADMX-06] the table's other numbers: earlier versions, phones that applied a rule in 30 days, 맞았어요·달라요.
      (SELECT COUNT(*) FROM rules r WHERE r.company_id = c.id AND r.withdrawn = 0 AND r.superseded = 1) AS previous,
      (SELECT COUNT(*) FROM uses u WHERE u.company_id = c.id AND u.at >= ?5) AS uses30,
      (SELECT COUNT(*) FROM votes v WHERE v.company_id = c.id AND v.ok = 1) AS ok,
      (SELECT COUNT(*) FROM votes v WHERE v.company_id = c.id AND v.ok = 0) AS bad
    FROM companies c
    WHERE ?1 = '' OR c.norm LIKE ?2 ESCAPE '\\' OR EXISTS (SELECT 1 FROM aliases a WHERE a.company_id = c.id AND a.norm LIKE ?2 ESCAPE '\\')
    ORDER BY c.merged_into IS NOT NULL, ${COMPANY_SORTS[sort]} LIMIT ?3 OFFSET ?4`, q, like, pg.limit + 1, pg.after, Date.now() - 30 * DAY);
  const { items, next } = sqlPage(rows, pg);
  return {
    next,
    companies: items.map((c) => ({
      id: c.id, name: c.name, hidden: c.hidden === 1, mergedInto: c.merged_into, aliases: c.aliases, rules: c.rules,
      pending: c.pending, reports: c.reports, createdAt: iso(c.created_at), members: c.members,
      previous: c.previous, uses30: c.uses30, ok: c.ok, bad: c.bad,
    })),
    sort,
  };
}

async function companyInfo(env, id) {
  // One trip for the whole page, however many rules the company has.
  const [[c], intoRows, aliasRows, ruleRows, reports, votes, [pending], [joined]] = (await batch(env, [
    stmt(env, "SELECT * FROM companies WHERE id = ?", id),
    stmt(env, "SELECT m.id, m.name FROM companies c JOIN companies m ON m.id = c.merged_into WHERE c.id = ?", id),
    stmt(env, "SELECT name FROM aliases WHERE company_id = ? ORDER BY name", id),
    stmt(env, "SELECT * FROM rules WHERE company_id = ? ORDER BY dept_norm, version DESC", id),
    stmt(env, "SELECT company_id, grp, device, points, cluster FROM reports WHERE company_id = ? AND status IN ('pending', 'adopted')", id),
    // AH 1.27 [PLAZA-15] with where each 달라요 says it is wrong.
    stmt(env, "SELECT company_id, grp, device, ok, verified, cluster, why FROM votes WHERE company_id = ?", id),
    stmt(env, "SELECT COUNT(DISTINCT grp) AS n FROM reports WHERE company_id = ? AND status = 'pending'", id),
    // [PLAZA-13] Phones that joined it (결정 D79).
    stmt(env, "SELECT COUNT(*) AS n FROM members WHERE company_id = ? AND left = 0", id),
  ])).map((r) => r.results);
  if (!c) return json({ error: "not_found" }, 404);
  const into = intoRows[0] ?? null;
  const aliases = aliasRows.map((a) => a.name);
  const scores = tallies(reports, votes);
  // AX 1.30 [ADMX-02] Each version against the department's version before it (rows are by department, newest first).
  const earlier = (r, i) => ruleRows.slice(i + 1).find((x) => x.dept_norm === r.dept_norm) ?? null;
  const rules = ruleRows.map((r, i) => {
    const s = scores.get(`${id}/${r.grp}`) ?? NO_SCORE;
    const before = earlier(r, i);
    return {
      ...(before ? { changesFromPrevious: { version: before.version, ...diffLines(describe(JSON.parse(before.setup)), describe(JSON.parse(r.setup))) } } : {}),
      id: r.id, dept: r.dept, version: r.version, source: r.source, adoptedAt: iso(r.adopted_at), withdrawn: r.withdrawn === 1,
      superseded: r.superseded === 1, ok: s.persons, bad: s.bad, lines: describe(JSON.parse(r.setup)),
      // AH 1.27 [PLAZA-15] 달라요 by where (순서·시간·급여 항목·그 밖), and whether too many took it out of the lead.
      why: Object.fromEntries([...WHY_PARTS, "other"].map((k) => [k, votes.filter((v) => v.grp === r.grp && !v.ok &&
        (k === "other" ? !WHY_PARTS.includes(v.why) : v.why === k)).length])),
      ...(doubted(s) && !r.withdrawn && !r.superseded ? { doubted: true } : {}),
    };
  });
  // [PLAZA-13] Whether a rule in use settled (결정 D79: three phones on it, nothing new for 30 days).
  const now = Date.now();
  for (const [i, r] of ruleRows.entries()) {
    if (!r.withdrawn && !r.superseded && (await stableOf(env, id, r.dept_norm, r.grp, now)).stable) rules[i].stable = true;
  }
  return json({
    id: c.id, name: c.name, hidden: c.hidden === 1, mergedInto: into, createdAt: iso(c.created_at), aliases, rules, pending: pending.n,
    members: joined.n,
  });
}

function companyName(body) {
  const name = typeof body.name === "string" ? body.name.replace(/[\u0000-\u001f\u007f]/g, "").trim() : "";
  if (!name || name.length > LIMITS.company || normName(name).length < 2) refuse("company", "name");
  return name;
}

/** The company already called [norm], by its name or by another name. */
async function named(env, norm) {
  return (await one(env, "SELECT id, name FROM companies WHERE norm = ? AND merged_into IS NULL LIMIT 1", norm))
    ?? (await one(env, "SELECT c.id, c.name FROM aliases a JOIN companies c ON c.id = a.company_id WHERE a.norm = ?", norm));
}

async function createCompany(request, env, now) {
  const name = companyName(await readJson(request, 1024));
  const norm = normName(name);
  const taken = await named(env, norm);
  if (taken) return json({ error: "exists", company: taken }, 409);
  const id = randomId(12);
  await run(env, "INSERT INTO companies (id, name, norm, hidden, merged_into, created_at, said) VALUES (?, ?, ?, 0, NULL, ?, ?)", id, name, norm, now, spoken(name) || "-");
  await audit(env, now, "company_create", id, name);
  return json({ id, name }, 201);
}

async function updateCompany(request, env, id, now) {
  const body = await readJson(request, 1024);
  const c = await one(env, "SELECT * FROM companies WHERE id = ?", id);
  if (!c) return json({ error: "not_found" }, 404);
  const stmts = [];
  if (body.name !== undefined) {
    const name = companyName(body);
    const norm = normName(name);
    const taken = await named(env, norm);
    if (taken && taken.id !== id) return json({ error: "exists", company: taken }, 409);
    // The old name keeps finding the company.
    if (norm !== c.norm) stmts.push(stmt(env, "INSERT OR IGNORE INTO aliases (norm, company_id, name) VALUES (?, ?, ?)", c.norm, id, c.name));
    stmts.push(stmt(env, "DELETE FROM aliases WHERE norm = ? AND company_id = ?", norm, id));
    stmts.push(stmt(env, "UPDATE companies SET name = ?, norm = ?, cho = '', said = '' WHERE id = ?", name, norm, id));
  }
  if (body.hidden !== undefined) {
    if (typeof body.hidden !== "boolean") refuse("hidden", "hidden");
    stmts.push(stmt(env, "UPDATE companies SET hidden = ? WHERE id = ?", body.hidden ? 1 : 0, id));
  }
  if (stmts.length) await batch(env, stmts);
  await audit(env, now, "company_update", id, JSON.stringify(body));
  return companyInfo(env, id);
}

async function addAlias(request, env, id, now) {
  const name = companyName(await readJson(request, 1024));
  const norm = normName(name);
  const c = await one(env, "SELECT * FROM companies WHERE id = ?", id);
  if (!c) return json({ error: "not_found" }, 404);
  const taken = await named(env, norm);
  if (taken && taken.id !== id) return json({ error: "exists", company: taken }, 409);
  if (norm !== c.norm) await run(env, "INSERT OR IGNORE INTO aliases (norm, company_id, name) VALUES (?, ?, ?)", norm, id, name);
  await audit(env, now, "alias_add", id, name);
  return companyInfo(env, id);
}

async function removeAlias(request, env, id, now) {
  const body = await readJson(request, 1024);
  await run(env, "DELETE FROM aliases WHERE norm = ? AND company_id = ?", normName(body.name), id);
  await audit(env, now, "alias_remove", id, String(body.name ?? ""));
  return companyInfo(env, id);
}

/** One company found under two names: everything moves to [into] and the name stays as another name. */
async function merge(request, env, id, now) {
  const body = await readJson(request, 1024);
  const from = await one(env, "SELECT * FROM companies WHERE id = ?", id);
  const into = typeof body.into === "string" && /^[A-Za-z0-9]{12}$/.test(body.into) ? await follow(env, body.into) : null;
  if (!from || !into) return json({ error: "not_found" }, 404);
  if (from.merged_into || into.id === from.id) return json({ error: "merge" }, 400);
  // Where both have a rule for a department, the one already in use at [into] stays.
  const theirs = new Set((await all(env, "SELECT dept_norm FROM rules WHERE company_id = ? AND withdrawn = 0 AND superseded = 0", into.id))
    .map((r) => r.dept_norm));
  const mine = await all(env, "SELECT id, dept_norm FROM rules WHERE company_id = ? AND withdrawn = 0 AND superseded = 0", from.id);
  const stmts = mine.filter((r) => theirs.has(r.dept_norm)).map((r) => stmt(env, "UPDATE rules SET superseded = 1 WHERE id = ?", r.id));
  stmts.push(stmt(env, "UPDATE aliases SET company_id = ? WHERE company_id = ?", into.id, from.id));
  if (from.norm !== into.norm) stmts.push(stmt(env, "INSERT OR IGNORE INTO aliases (norm, company_id, name) VALUES (?, ?, ?)", from.norm, into.id, from.name));
  stmts.push(
    stmt(env, "UPDATE reports SET company_id = ? WHERE company_id = ?", into.id, from.id),
    stmt(env, "UPDATE OR IGNORE votes SET company_id = ? WHERE company_id = ?", into.id, from.id),
    stmt(env, "DELETE FROM votes WHERE company_id = ?", from.id),
    stmt(env, "UPDATE rules SET company_id = ? WHERE company_id = ?", into.id, from.id),
    stmt(env, "UPDATE companies SET merged_into = ? WHERE id = ? OR merged_into = ?", into.id, from.id, from.id),
  );
  await batch(env, stmts);
  // Reports that now stand together may be enough to adopt.
  for (const g of await all(env, "SELECT DISTINCT grp FROM reports WHERE company_id = ? AND status = 'pending'", into.id)) {
    await settle(env, into.id, g.grp, now);
  }
  await audit(env, now, "merge", from.id, into.id);
  return companyInfo(env, into.id);
}

async function setRule(request, env, id, now) {
  const body = await readJson(request, 1024);
  if (typeof body.withdrawn !== "boolean") refuse("withdrawn", "withdrawn");
  const r = await one(env, "SELECT * FROM rules WHERE id = ?", id);
  if (!r) return json({ error: "not_found" }, 404);
  // [PLZ-06] Taking an adopted rule down is for the owner and admins given 광장 규칙 내리기 (결정 D35);
  // nobody else, the sender included, can make it disappear.
  if (!can(env.actor, "takedown", "edit")) return json({ error: "no_permission", area: "takedown" }, 403);
  if (body.withdrawn) {
    await run(env, "UPDATE rules SET withdrawn = 1 WHERE id = ?", id);
  } else {
    // A department uses one rule; one adopted since stays unless the developer withdraws it first.
    const active = r.superseded ? null : await activeRule(env, r.company_id, r.dept_norm);
    if (active && active.id !== id) return json({ error: "active" }, 409);
    await batch(env, [
      stmt(env, "UPDATE rules SET withdrawn = 0 WHERE id = ?", id),
      // Coworkers who sent the same setup meanwhile join it.
      ...(r.superseded ? [] : [stmt(env, `UPDATE reports SET status = 'adopted', rule_id = ?, decided_at = ?
        WHERE company_id = ? AND grp = ? AND status = 'pending'`, id, now, r.company_id, r.grp)]),
    ]);
  }
  await audit(env, now, body.withdrawn ? "rule_withdraw" : "rule_restore", id);
  return companyInfo(env, r.company_id);
}

async function rewards(env, status, params = new URLSearchParams()) {
  const wanted = ["owed", "paid", "void"].includes(status) ? status : "owed";
  const pg = page(params);
  // [ADMIN-16] A company's name finds its rewards; a rate's reward has no company and is left out then.
  const like = companyLike(params.get("q"));
  const rows = await all(env, `
    SELECT w.id, w.months, w.status, w.memo, w.created_at, w.paid_at, w.rule_id, w.deferred, p.dept, c.id AS company_id, c.name AS company, u.version
      , t.trade, t.unit, t.region, t.job
    FROM rewards w LEFT JOIN reports p ON p.id = w.report_id LEFT JOIN companies c ON c.id = p.company_id LEFT JOIN rules u ON u.id = w.rule_id
      LEFT JOIN rates t ON t.id = w.report_id
    WHERE w.status = ?1 AND (?2 = '' OR c.norm LIKE ?2 ESCAPE '\\') ORDER BY w.created_at DESC LIMIT ?3 OFFSET ?4`, wanted, like, pg.limit + 1, pg.after);
  // [INVITE-06] How invites went, beside the rewards they gave, in the same trip as the counts.
  const [counts, invites] = await rewardCounts(env, !params.get("after"), Date.now());
  const { items, next } = sqlPage(rows, pg);
  return {
    invites,
    status: wanted,
    next,
    counts: Object.fromEntries(counts.map((x) => [x.status, x.n])),
    rewards: items.map((w) => ({
      id: w.id, months: w.months, status: w.status, memo: w.memo,
      // [PLZ-14] A rate's reward names the rate instead of a company.
      company: w.trade ? { id: "", name: `임금표 · ${JOBS[w.job] ?? ""} ${REGIONS[w.region] ?? "전국"}` }
        : w.rule_id === "invite" ? { id: "", name: "친구 초대" } : { id: w.company_id, name: w.company ?? "" },
      invite: w.rule_id === "invite",
      dept: w.trade ? `${w.trade} ${RATE_UNITS[w.unit]?.name ?? ""}` : w.dept, rate: Boolean(w.trade),
      version: w.version, ask: w.rule_id === "", createdAt: iso(w.created_at), paidAt: w.paid_at ? iso(w.paid_at) : null, deferred: Boolean(w.deferred),
    })),
  };
}

async function setReward(request, env, id, now) {
  const body = await readJson(request, 2048);
  if (!["owed", "paid", "void"].includes(body.status)) refuse("status", "status");
  const memo = typeof body.memo === "string" ? body.memo.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 200) : null;
  // [MON-08] How long, chosen when it is given: 회사 방식 3, 제안 1, 큰 개선 6~12 (수익화 확정 4장 7).
  if (given(body.months) && (!Number.isInteger(body.months) || body.months < 1 || body.months > MONEY.passMonths)) refuse("months", "months");
  const r = await one(env, "SELECT * FROM rewards WHERE id = ?", id);
  if (!r) return json({ error: "not_found" }, 404);
  const months = body.status === "paid" && given(body.months) ? body.months : r.months;
  // Someone already paying gets the months as a later renewal from Play; anyone else a pass.
  let deferred = null;
  if (body.status === "paid" && r.status !== "paid") {
    try {
      deferred = await deferFor(env, r.device, months, now);
    } catch (e) {
      console.error(`defer ${id}: ${e?.code ?? e}`);
    }
  }
  const note = deferred ? `Play 결제 미룸(다음 결제 ${iso(deferred).slice(0, 10)})${memo ? ` · ${memo}` : ""}` : memo;
  const paidAt = body.status === "paid" ? (r.status === "paid" ? r.paid_at : now) : null;
  const wasDeferred = body.status === "paid" ? (deferred ? 1 : r.status === "paid" ? r.deferred : 0) : 0;
  await run(env, "UPDATE rewards SET status = ?, months = ?, memo = COALESCE(?, memo), paid_at = ?, deferred = ? WHERE id = ?",
    body.status, months, note, paidAt, wasDeferred, id);
  await audit(env, now, `reward_${body.status}`, id, `${months}개월${deferred ? " 미룸" : ""}${memo ? ` ${memo}` : ""}`);
  return json({ ok: true, months, deferred: Boolean(deferred) });
}

async function saveSettings(request, env, now) {
  const body = await readJson(request, 1024);
  if (!Number.isInteger(body.adoptPoints) || body.adoptPoints < 2 || body.adoptPoints > 20) refuse("adoptPoints", "adoptPoints");
  if (!Number.isInteger(body.rewardMonths) || body.rewardMonths < 0 || body.rewardMonths > 24) refuse("rewardMonths", "rewardMonths");
  // [COMM-07] Left out by a developer app from before it, the agreement share stays as it was.
  const agree = body.adoptAgree ?? (await settings(env)).adoptAgree;
  if (!Number.isInteger(agree) || agree < 50 || agree > 100) refuse("adoptAgree", "adoptAgree");
  // [PLZ-03] The plaza's own bars (결정 D32·D33); a developer app from before them leaves them as they were.
  const was = await settings(env);
  const pick = (key, lo, hi) => {
    const v = body[key] ?? was[key];
    if (!Number.isInteger(v) || v < lo || v > hi) refuse(key, key);
    return v;
  };
  const extra = { adopt_persons: pick("adoptPersons", 2, 10), amend_persons: pick("amendPersons", 2, 10), auto_reward: pick("autoReward", 0, 1),
    reward_cap_months: pick("rewardCapMonths", 1, 24), reward_cap_days: pick("rewardCapDays", 30, 365),
    first_months: pick("firstMonths", 0, 12), first_cap_year: pick("firstCapYear", 0, 12) };
  await batch(env, [
    stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('adopt_points', ?)", String(body.adoptPoints)),
    stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('reward_months', ?)", String(body.rewardMonths)),
    stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('adopt_agree', ?)", String(agree)),
    ...Object.entries(extra).map(([k, v]) => stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", k, String(v))),
  ]);
  // A lower bar may already be met.
  for (const g of await all(env, "SELECT DISTINCT company_id, grp FROM reports WHERE status = 'pending' LIMIT 200")) {
    await settle(env, g.company_id, g.grp, now);
  }
  for (const t of await all(env, "SELECT id FROM rates WHERE status = 'pending' ORDER BY created_at DESC LIMIT 200")) await settleRate(env, t.id, now);
  await audit(env, now, "settings", "", `${body.adoptPoints}/${body.rewardMonths}/${agree}% · ${extra.adopt_persons}명/${extra.amend_persons}명 · 자동 ${extra.auto_reward ? "켬" : "끔"} ${extra.reward_cap_months}개월/${extra.reward_cap_days}일`);
  return json(await settings(env));
}

/** [ADMUX-06] 답 목표: hours a question of each kind may wait, 1 to 168; a key left out stays as it was. */
async function saveSla(request, env, now) {
  const body = await readJson(request, 1024);
  const was = await slaTargets(env);
  const next = {};
  for (const k of Object.keys(SLA.keys)) {
    const v = body[k] ?? was[k];
    if (!Number.isInteger(v) || v < SLA.least || v > SLA.most) refuse("sla", k);
    next[k] = v;
  }
  await batch(env, Object.entries(next).map(([k, v]) => stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", slaSetting(k), String(v))));
  await audit(env, now, "sla", "", `문제 ${next.slaBug} · 질문 ${next.slaQuestion} · 제안 ${next.slaIdea} · 그 밖 ${next.slaOther}시간`);
  return json({ sla: next });
}

/** [PLZ-14] Rates for admins: "review" (far from the rest, waiting), "pending", "adopted", "hidden". */
async function rateList(env, url) {
  const status = ["review", "pending", "adopted", "hidden"].includes(url.searchParams.get("status")) ? url.searchParams.get("status") : "review";
  const pg = page(url.searchParams);
  // [ADMIN-16] A rate has no company: q finds the trade (직종) typed in it.
  const q = String(url.searchParams.get("q") ?? "").trim().slice(0, 40);
  const like = q ? `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%` : "";
  const rows = await all(env, `SELECT * FROM rates WHERE status = ?1 AND (?2 = '' OR trade LIKE ?2 ESCAPE '\\')
    ORDER BY created_at DESC LIMIT ?3 OFFSET ?4`, status, like, pg.limit + 1, pg.after);
  const counts = await all(env, "SELECT status, COUNT(*) AS n FROM rates GROUP BY status");
  const { items, next } = sqlPage(rows, pg);
  return {
    status,
    next,
    counts: Object.fromEntries(counts.map((x) => [x.status, x.n])),
    rates: items.map((r) => ({ id: r.id, job: r.job, jobName: JOBS[r.job] ?? r.job, region: r.region, regionName: REGIONS[r.region] ?? "전국",
      trade: r.trade, unit: r.unit, unitName: RATE_UNITS[r.unit]?.name ?? r.unit, year: r.year, amount: r.amount, note: r.note,
      agree: r.agree, disagree: r.disagree, writer: r.device.slice(0, 6), at: iso(r.created_at), decidedAt: r.decided_at ? iso(r.decided_at) : null })),
  };
}

/**
 * [PLZ-14] An admin adopts a rate waiting for review, hides one, or brings one back to voting. Taking an
 * adopted rate down needs 광장 규칙 내리기 like a rule (결정 D35).
 */
async function setRate(request, env, actor, id, now) {
  const body = await readJson(request, 1024);
  const status = oneOf(body.status, ["adopted", "hidden", "pending"], "status");
  const out = await rateTo(env, actor, id, status, now);
  return json(out, out.error === "not_found" ? 404 : out.error ? 403 : 200);
}

/** One 임금표 entry to [status], for one or [ADMUX-19] many. */
async function rateTo(env, actor, id, status, now) {
  const r = await one(env, "SELECT * FROM rates WHERE id = ?", id);
  if (!r) return { error: "not_found" };
  if (r.status === "adopted" && status !== "adopted" && !can(actor, "takedown", "edit")) return { error: "no_permission", area: "takedown" };
  if (status === "adopted" && r.status !== "adopted") await adoptRate(env, r, await settings(env), now);
  else await run(env, "UPDATE rates SET status = ?, decided_at = ? WHERE id = ?", status, now, id);
  await audit(env, now, `rate_${status}`, id, `${r.trade} ${r.amount}`);
  return { ok: true, status };
}

// ---- the talk on rule sets, for the developer [COMM-06] ----

/**
 * Posts to look at: "flagged" (신고가 있거나 신고로 가려졌는데 아직 아무도 안 본 것, the most flagged first),
 * "hidden", or "all" newest first; with company and setup, one talk whole. A writer shows as the first
 * six letters of the install's hash, enough to see the same writer again and nothing more.
 */
async function postList(env, url) {
  const status = ["flagged", "hidden", "all", "room"].includes(url.searchParams.get("status")) ? url.searchParams.get("status") : "flagged";
  const company = url.searchParams.get("company");
  const grp = url.searchParams.get("setup");
  // AI 1.27 [PLAZA-32] One room whole too: a company's (its id) or a kind of work's (style-…), setup "room".
  const one_talk = company && (/^[A-Za-z0-9]{12}$/.test(company) || Object.hasOwn(STYLE_ROOMS, company)) && grp && (/^[0-9a-f]{16}$/.test(grp) || grp === ROOM);
  const only = status === "room" ? "AND p.grp = 'room'" : "";
  const pg = page(url.searchParams);
  const like = companyLike(url.searchParams.get("q"));
  const rows = one_talk
    ? await all(env, `SELECT p.*, c.name FROM posts p LEFT JOIN companies c ON c.id = p.company_id
        WHERE p.company_id = ? AND p.grp = ? ORDER BY p.created_at, p.rowid LIMIT 500`, company, grp)
    : status === "flagged"
      ? await all(env, `SELECT p.*, c.name FROM posts p LEFT JOIN companies c ON c.id = p.company_id
          WHERE p.kept = 0 AND (p.flags > 0 OR p.hide_reason = 'flags') AND (?1 = '' OR c.norm LIKE ?1 ESCAPE '\\')
          ORDER BY p.flags DESC, p.created_at DESC LIMIT ?2 OFFSET ?3`, like, pg.limit + 1, pg.after)
      : await all(env, `SELECT p.*, c.name FROM posts p LEFT JOIN companies c ON c.id = p.company_id
          WHERE (?1 = '' OR c.norm LIKE ?1 ESCAPE '\\') ${status === "hidden" ? "AND p.status = 'hidden'" : ""} ${only}
          ORDER BY p.created_at DESC LIMIT ?2 OFFSET ?3`, like, pg.limit + 1, pg.after);
  // [ADMIN-16] One talk comes whole; the lists come a page at a time.
  const next = one_talk ? null : sqlPage(rows, pg).next;
  if (!one_talk && rows.length > pg.limit) rows.length = pg.limit;
  // What each post needs besides itself, for all of them in one trip.
  const ids = JSON.stringify(rows.map((p) => p.id));
  const talks = JSON.stringify([...new Map(rows.map((p) => [`${p.company_id}/${p.grp}`, [p.company_id, p.grp]])).values()]);
  const pairs = "SELECT json_extract(value, '$[0]') AS company_id, json_extract(value, '$[1]') AS grp FROM json_each(?)";
  const [ruleDepts, reportDepts, flagRows, mutedRows] = rows.length ? (await batch(env, [
    stmt(env, `SELECT r.company_id, r.grp, r.dept FROM rules r JOIN (${pairs}) t ON t.company_id = r.company_id AND t.grp = r.grp
      ORDER BY r.version DESC`, talks),
    stmt(env, `SELECT r.company_id, r.grp, r.dept FROM reports r JOIN (${pairs}) t ON t.company_id = r.company_id AND t.grp = r.grp`, talks),
    stmt(env, `SELECT post_id, reason, COUNT(*) AS n FROM post_flags WHERE post_id IN (SELECT value FROM json_each(?))
      GROUP BY post_id, reason ORDER BY n DESC, reason`, ids),
    stmt(env, `SELECT device, until FROM muted WHERE until > ? AND device IN (SELECT value FROM json_each(?))`, Date.now(),
      JSON.stringify([...new Set(rows.map((p) => p.device))])),
  ])).map((r) => r.results) : [[], [], [], []];
  const depts = new Map();
  // The newest rule's department, else any report's, as before.
  for (const r of [...ruleDepts, ...reportDepts]) {
    const key = `${r.company_id}/${r.grp}`;
    if (!depts.has(key)) depts.set(key, r.dept);
  }
  const mutedUntil = new Map(mutedRows.map((m) => [m.device, m.until]));
  const out = [];
  for (const p of rows) {
    const key = `${p.company_id}/${p.grp}`;
    const reasons = flagRows.filter((f) => f.post_id === p.id);
    const muted = mutedUntil.has(p.device) ? { until: mutedUntil.get(p.device) } : null;
    out.push({
      id: p.id,
      company: { id: p.company_id, name: p.name ?? STYLE_ROOMS[p.company_id] ?? p.company_id },
      setup: p.grp,
      ...(p.grp === ROOM ? { room: Object.hasOwn(STYLE_ROOMS, p.company_id) ? "style" : "company" } : {}),
      dept: depts.get(key) ?? "",
      body: p.body,
      writer: p.device.slice(0, 6),
      at: iso(p.created_at),
      status: p.status,
      kept: p.kept === 1,
      flags: p.flags,
      reasons: reasons.map((r) => ({ code: r.reason, text: FLAGS[r.reason] ?? r.reason, n: r.n })),
      hidden: p.status === "hidden" ? { code: p.hide_reason, text: HIDE_REASONS[p.hide_reason] ?? "", at: p.hidden_at ? iso(p.hidden_at) : null } : null,
      mutedUntil: muted ? iso(muted.until) : null,
    });
  }
  return { status: one_talk ? "talk" : status, posts: out, next };
}

/**
 * [COMM-11] What became of a post, for 광장 소식: each phone that flagged it hears hidden, kept or deleted;
 * its writer hears shown or deleted (a hidden post they can already see, with why, in the talk).
 */
function plazaResults(env, p, flagged, writer, now) {
  return [
    ...(flagged ? [stmt(env, `INSERT INTO plaza_results (device, kind, post_id, result, company_id, at)
      SELECT device, 'flag_result', ?1, ?2, ?3, ?4 FROM post_flags WHERE post_id = ?1`, p.id, flagged, p.company_id, now)] : []),
    ...(writer ? [stmt(env, "INSERT INTO plaza_results (device, kind, post_id, result, company_id, at) VALUES (?, 'post_result', ?, ?, ?, ?)",
      p.device, p.id, writer, p.company_id, now)] : []),
  ];
}

/**
 * hide (with a reason the writer's app shows), show (kept: flags no longer hide it), delete, or mute:
 * the writer cannot post for 30 days, and their post is hidden.
 */
async function moderate(request, env, id, now) {
  const out = await moderatePost(env, id, await readJson(request, 1024), now);
  return json(out, out.error ? 404 : 200);
}

/** One post's handling, for one post or [ADMUX-19] many; the answer for one, or { error } when it is gone. */
async function moderatePost(env, id, body, now) {
  const p = await one(env, "SELECT * FROM posts WHERE id = ?", id);
  if (!p) return { error: "not_found" };
  const target = `${p.company_id}/${p.grp}/${id}`;
  if (body.action === "hide" || body.action === "mute") {
    const reason = Object.hasOwn(HIDE_REASONS, body.reason) && body.reason !== "flags" ? body.reason : "abuse";
    const stmts = [...plazaResults(env, p, "hidden", p.status === "hidden" ? null : "hidden", now),
      stmt(env, "UPDATE posts SET status = 'hidden', hide_reason = ?, hidden_at = ?, kept = 1 WHERE id = ?", reason, now, id)];
    if (body.action === "mute") {
      stmts.push(stmt(env, `INSERT INTO muted (device, until, reason) VALUES (?, ?, ?)
        ON CONFLICT (device) DO UPDATE SET until = excluded.until, reason = excluded.reason`, p.device, now + LIMITS.muteDays * DAY, reason));
    }
    await batch(env, stmts);
    await audit(env, now, body.action === "mute" ? "post_mute" : "post_hide", target, reason);
  } else if (body.action === "show") {
    await batch(env, [
      ...plazaResults(env, p, "kept", p.status === "hidden" ? "shown" : null, now),
      stmt(env, "UPDATE posts SET status = 'shown', hide_reason = NULL, hidden_at = NULL, kept = 1, flags = 0 WHERE id = ?", id),
      stmt(env, "DELETE FROM post_flags WHERE post_id = ?", id),
      stmt(env, "DELETE FROM muted WHERE device = ?", p.device),
    ]);
    await audit(env, now, "post_show", target);
  } else if (body.action === "delete") {
    await batch(env, [...plazaResults(env, p, "deleted", "deleted", now),
      stmt(env, "DELETE FROM post_flags WHERE post_id = ?", id), stmt(env, "DELETE FROM posts WHERE id = ?", id)]);
    await audit(env, now, "post_delete", target);
    return { deleted: true };
  } else {
    refuse("action", "action");
  }
  const after = await one(env, "SELECT id, status, hide_reason, kept FROM posts WHERE id = ?", id);
  return { id, status: after.status, kept: after.kept === 1, hidden: after.hide_reason };
}

// ---- input and output ----

// ---- 정식 출시, 프로 이용권, 관리자 휴대폰, 구매 확인 [MON-02][MON-03][MON-06][MON-08][MON-09][MON-10] ----

const MONEY = {
  // [MON-10] Phones installed before the launch get this many months of 프로 from it, until changed.
  thanksMonths: 1,
  // [MON-07] Days after install without ads; the app has the same default.
  adQuietDays: 3,
  // [MON-06] Days before a trial ends that the app asks for consent (결정 D17: day 23 of 30).
  trialAskDays: 7,
  // [MON-09] A code from the developer app ties a phone to an admin for ten minutes, once; two phones each.
  linkMs: 10 * 60 * 1000,
  linkCodesPerHour: 10,
  linkTriesPerHour: 10,
  phonesPerAdmin: 2,
  // [MON-08] A recovery code moves passes to a new phone once.
  codesPerDay: 10,
  claimsPerHour: 10,
  passMonths: 12,
  // [MON-03] What the server keeps of a purchase, and for how long after it ends.
  subKeepMs: 30 * DAY,
  // [MON-11] 계약 또는 청약철회 등에 관한 기록: 5년 (전자상거래법 시행령 제6조 제1항).
  consentKeepMs: 5 * 365 * DAY,
  freeFeatures: 30,
};

/** The app's names for features the server may open to everyone; never the other way round. */
const FEATURE = /^[A-Z][A-Z_]{1,39}$/;

async function launchState(env) {
  // AM 1.28 [FLAG-22] billingLive and freeFeatures come from the flags now (billing by the server's view, free.* forced on).
  const [rows, store] = await Promise.all([all(env, "SELECT key, value FROM settings WHERE key IN ('launched_at', 'thanks_months')"), flagStore(env)]);
  const get = (k) => rows.find((r) => r.key === k)?.value;
  const thanks = Number(get("thanks_months"));
  const launched = Number(get("launched_at"));
  const now = Date.now();
  const legacy = legacyFlags(store, now);
  return {
    billingLive: serverEval(store, "billing", now).on,
    launchedAt: Number.isFinite(launched) && launched > 0 ? launched : null,
    thanksMonths: Number.isInteger(thanks) && thanks >= 0 ? thanks : MONEY.thanksMonths,
    freeFeatures: legacy.free,
    off: legacy.off,
    store,
  };
}

/**
 * [MON-02] What every app reads once a day: the 정식 출시 switch and what goes with it. No device, nothing kept.
 * AM 1.28 [FLAG-22] 1.28 apps read flags (only what the server overrides), policy and serverTime; apps up to 1.27 keep
 * reading billingLive, freeFeatures and off, which the server works out from the same flags.
 */
async function config(env) {
  const now = Date.now();
  const [s, notices] = await Promise.all([launchState(env), noticeRows(env)]);
  // The first answer that finds billing on records the launch, if the hourly job has not yet.
  const launchedAt = s.launchedAt ?? (s.billingLive ? await recordLaunch(env, s.store, now) : null);
  return {
    v: 1, billingLive: s.billingLive, launchedAt: launchedAt ? iso(launchedAt) : null, thanksMonths: s.thanksMonths,
    freeFeatures: s.freeFeatures, adQuietDays: MONEY.adQuietDays, trialAskDays: MONEY.trialAskDays,
    // [SRV-04] 공지 running now (the app picks the ones for its version) and what is switched off.
    notices: notices.filter((n) => n.from <= now && now < n.until).map(noticeView), off: s.off,
    flags: configFlags(s.store, now), policy: configPolicy(s.store), serverTime: iso(now),
  };
}

async function launchView(env) {
  const s = await launchState(env);
  const n = await one(env, "SELECT COUNT(*) AS n FROM subs WHERE state IN ('ACTIVE', 'GRACE') AND expiry > ?", Date.now());
  return { ...(await config(env)), play: playReady(env) !== null, subscribers: n.n, launched: s.launchedAt !== null };
}

/** [MON-08] A date [months] calendar months after [ms], on the last day of a shorter month when needed. */
export function addMonths(ms, months) {
  const d = new Date(ms);
  const day = d.getUTCDate();
  d.setUTCDate(1);
  d.setUTCMonth(d.getUTCMonth() + months);
  const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
  d.setUTCDate(Math.min(day, last));
  return d.getTime();
}

/**
 * [MON-08] Passes given to one install, stacked: each starts when the one before ends, when it was given,
 * or at the launch, whichever is last, so months given while everything was free are not used up.
 * A reward Play paid as a deferred renewal is not a pass.
 */
export function stack(rows, launchedAt) {
  const given = rows.filter((r) => r.status === "paid" && !r.deferred).sort((a, b) => a.paid_at - b.paid_at);
  const months = given.reduce((n, r) => n + r.months, 0);
  if (!launchedAt) return { until: null, waiting: months, items: given };
  let until = 0;
  for (const r of given) until = addMonths(Math.max(until, r.paid_at, launchedAt), r.months);
  return { until: given.length ? until : null, waiting: 0, items: given };
}

async function installOf(request) {
  const id = deviceId(request.headers.get("x-device"));
  if (!id) throw new Refuse("device", "device");
  return deviceHash(id);
}

/** [MON-08][MON-09] What this install has: its passes, its admin link and whether it holds a recovery code. */
async function passes(env, dev, now) {
  const [rows, link, code, s] = await Promise.all([
    all(env, "SELECT months, status, paid_at, deferred, rule_id FROM rewards WHERE device = ?", dev),
    one(env, "SELECT status, admin_name, admin FROM admin_devices WHERE device = ?", dev),
    one(env, "SELECT created_at FROM pass_codes WHERE device = ?", dev),
    launchState(env),
  ]);
  if (link) await run(env, "UPDATE admin_devices SET seen_at = ? WHERE device = ?", now, dev);
  const p = stack(rows, s.launchedAt);
  return {
    until: p.until ? iso(p.until) : null,
    waitingMonths: p.waiting,
    items: p.items.map((r) => ({ months: r.months, at: iso(r.paid_at), why: rewardWhy(r.rule_id) })),
    deferred: rows.filter((r) => r.status === "paid" && r.deferred).length,
    // AM 1.28 [FLAG-26] owner: the link code was the owner's, so the app shows every way of working (숨긴 근무 유형 포함).
    admin: link ? { status: link.status, name: link.admin_name, owner: link.admin === OWNER.id } : null,
    code: Boolean(code),
    checkedAt: iso(now),
  };
}

const passCode = () => randomId(16).toUpperCase().replace(/(.{4})(?=.)/g, "$1-");
const passCodeHash = async (code) => (await sha256(`pass|${String(code).toUpperCase().replace(/[^A-Z0-9]/g, "")}`)).slice(0, 40);

/** [MON-08] /api/passes, /api/passes/code, /api/passes/claim and /api/admin-link, all for one install. */
async function money(request, env, path) {
  const method = request.method;
  const now = Date.now();
  const dev = await installOf(request);
  if (path === "/api/passes" && method === "GET") return json(await passes(env, dev, now));
  if (path === "/api/passes/code" && method === "POST") {
    if (!(await allow(env, `passcode|${dev}`, MONEY.codesPerDay, DAY, now))) return json({ error: "rate_limited" }, 429);
    // A new code replaces the old one; it is shown once and kept only as a hash.
    const code = passCode();
    await batch(env, [
      stmt(env, "DELETE FROM pass_codes WHERE device = ?", dev),
      stmt(env, "INSERT INTO pass_codes (hash, device, created_at) VALUES (?, ?, ?)", await passCodeHash(code), dev, now),
    ]);
    return json({ code }, 201);
  }
  if (path === "/api/passes/claim" && method === "POST") {
    const body = await readJson(request, 1024);
    const net = await network(request, env, now);
    if (!(await allow(env, `claim|${net.day}`, MONEY.claimsPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
    if (typeof body.code !== "string" || body.code.length > 40) refuse("code", "code");
    const hash = await passCodeHash(body.code);
    const row = await one(env, "SELECT device FROM pass_codes WHERE hash = ?", hash);
    if (!row) return json({ error: "code", field: "code" }, 404);
    if (row.device !== dev) {
      // The passes move; the old phone keeps nothing and the code is spent.
      await batch(env, [
        stmt(env, "UPDATE rewards SET device = ? WHERE device = ?", dev, row.device),
        stmt(env, "DELETE FROM pass_codes WHERE device IN (?, ?)", row.device, dev),
        // [INVITE-02] The invite code moves with them, so links already sent count here.
        stmt(env, "UPDATE OR IGNORE invite_codes SET device = ? WHERE device = ?", dev, row.device),
        stmt(env, "UPDATE invite_uses SET inviter = ? WHERE inviter = ?", dev, row.device),
      ]);
      await audit(env, now, "pass_move", dev.slice(0, 8), row.device.slice(0, 8));
    }
    return json(await passes(env, dev, now));
  }
  if (path === "/api/admin-link" && method === "POST") return linkPhone(request, env, dev, now);
  if (path === "/api/admin-link" && method === "DELETE") {
    await run(env, "DELETE FROM admin_devices WHERE device = ?", dev);
    return new Response(null, { status: 204 });
  }
  if (path === "/api/purchases" && method === "POST") return purchase(request, env, dev, now);
  if (path === "/api/consents" && method === "POST") return consent(request, env, dev, now);
  return json({ error: "not_found" }, 404);
}

// AE 1.26 시작
// ---- 친구 초대 [INVITE-02][INVITE-03][INVITE-04][INVITE-08] ----
// The person who invited gets months of 프로 each time [friends] friends' phones installed the app by the invite,
// finished the first setup and used it for the period: opened again on a Korean day at least [days] days after the
// day they told the server (research/1.26-AE-초대-조사.md, 결정 D81·D82). The developer app sets the numbers
// (설정 › 친구 초대). The friend gets nothing, and a store rating or review is never part of it (Play: User
// Ratings, Reviews, and Installs).

const INVITE = {
  // [INVITE-08] Until changed in the developer app: 5 friends, 7 days, 1 month, no cap (0).
  friends: 5,
  days: 7,
  months: 1,
  capMonths: 0,
  // A cap, when one is set, counts the months of the last 365 days.
  capDays: 365,
  // A friend who has not used the app for the period by [days] + 30 days after telling is let go.
  graceDays: 30,
  // A counted friend (its phone mark, so the phone is not counted again) is kept a year.
  keepCounted: 365 * DAY,
  codesPerDay: 5,
  usesPerHour: 10,
  usesPerDevice: 5,
  seenPerDay: 30,
  // AG 1.27: more friends of one code than this from one network in an hour are noted (not refused).
  burstPerHour: 2,
  package: "com.bamnat.app",
};
/** [INVITE-08] What the developer app may set, and the settings rows they are kept in. */
const INVITE_LIMITS = { friends: [1, 50], days: [1, 60], months: [1, 12], capMonths: [0, 120] };
const INVITE_KEYS = { friends: "invite_friends", days: "invite_days", months: "invite_months", capMonths: "invite_cap_months" };
const INVITE_SETTINGS_SQL = `SELECT key, value FROM settings WHERE key IN (${Object.values(INVITE_KEYS).map((k) => `'${k}'`).join(", ")})`;

/** [INVITE-08] The invite rules from their settings rows, each within its limits, else its default. */
function readInviteSettings(rows) {
  return Object.fromEntries(Object.entries(INVITE_KEYS).map(([name, key]) => {
    const n = Number(rows.find((r) => r.key === key)?.value);
    const [lo, hi] = INVITE_LIMITS[name];
    return [name, Number.isInteger(n) && n >= lo && n <= hi ? n : INVITE[name]];
  }));
}

const inviteSettings = async (env) => readInviteSettings(await all(env, INVITE_SETTINGS_SQL));

/** Days a friend has to come back in, from the day it told the server: the period and 30 more. */
const inviteDeadline = (set) => (set.days + INVITE.graceDays) * DAY;

// No 0/O, 1/I: a code read out or typed by hand. 32 letters, so a random byte maps evenly.
const INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const INVITE_CODE = /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/;
/** What the app sends as the phone mark: SHA-256 of the app-scoped Android ID, never the id itself. */
const PHONE_MARK = /^[0-9a-f]{64}$/;

const inviteCode = () => [...crypto.getRandomValues(new Uint8Array(8))].map((b) => INVITE_CHARS[b % 32]).join("");

/** A code as a person may type it: "abcd-2345" is ABCD2345. */
export const inviteCodeOf = (v) => {
  const c = typeof v === "string" ? v.toUpperCase().replace(/[^A-Z0-9]/g, "") : "";
  return INVITE_CODE.test(c) ? c : null;
};

/** The phone mark as the server keeps it: hashed again with the server's secret salt. */
async function phoneMark(env, v) {
  if (typeof v !== "string" || !PHONE_MARK.test(v)) return null;
  return (await sha256(`${await salt(env)}|phone|${v}`)).slice(0, 40);
}

/** [INVITE-02] The Play listing with the code as the install referrer (Play Install Referrer API). */
export function storeLink(code) {
  const referrer = `utm_source=bamnat_invite&utm_content=${code}`;
  return `https://play.google.com/store/apps/details?id=${INVITE.package}&referrer=${encodeURIComponent(referrer)}`;
}

/**
 * AG 1.27 [INVITE-13] The Play app itself by an Android intent link, the referrer kept as on the web link, and the web
 * link when the Play app is missing (Chrome's intent: links; 확인 필요 for each messenger's own browser).
 */
export function playIntent(code, store = storeLink(code)) {
  const referrer = encodeURIComponent(`utm_source=bamnat_invite&utm_content=${code}`);
  return `intent://details?id=${INVITE.package}&referrer=${referrer}#Intent;scheme=market;package=com.android.vending;` +
    `S.browser_fallback_url=${encodeURIComponent(store)};end`;
}

/** Why a reward was given, as the app names it. */
const rewardWhy = (ruleId) => (ruleId === "invite" ? "invite" : ruleId === "rate" ? "rate" : ruleId ? "rule" : "idea");

/** Whole days from one Korean date ("2026-10-25") to another. */
const dayGap = (from, to) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY);

/**
 * [INVITE-02] /i/<code>: a small page that sends an Android phone on to the Play listing with the code, and
 * shows the code elsewhere. It keeps nothing and does not look the code up, so it tells nobody which codes exist.
 */
function invitePage(raw) {
  const code = inviteCodeOf(raw);
  if (!code) return new Response("Not found", { status: 404, headers: { "content-type": "text/plain; charset=utf-8" } });
  const store = storeLink(code);
  // AG 1.27 [INVITE-13] A messenger's own browser (카카오톡, 네이버, 인스타그램, 페이스북, 라인) may open the Play web page,
  // where an install can lose the referrer: there the Play app is opened straight, with the web page to fall back to.
  const app = playIntent(code, store);
  const shown = `${code.slice(0, 4)}-${code.slice(4)}`;
  const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>밤낮 초대</title>
<style>body{font-family:system-ui,sans-serif;margin:0;padding:32px 16px;background:#f6f7fb;color:#1b1d24}main{max-width:420px;margin:auto}
a.b{display:block;text-align:center;background:#3b5bdb;color:#fff;padding:14px;border-radius:12px;text-decoration:none;font-weight:600}
code{font-size:1.4em;letter-spacing:.08em}@media (prefers-color-scheme:dark){body{background:#14161c;color:#e8e9ee}}</style></head>
<body><main><h1>밤낮 근무 달력</h1><p>친구가 밤낮을 같이 쓰자고 초대했어요. 이 링크로 Play 스토어에서 받으면 앱이 초대를 저절로 알아보고, 첫 설정 마지막에 친구에게 알릴 수 있어요.</p>
<p><a class="b" id="go" href="${store}">Play 스토어에서 받기</a></p>
<p>안드로이드 휴대폰에서 열어 주세요. 링크로 받지 못했다면 설치하고 7일 안에 앱의 설정 › 프로 › ‘초대 코드 넣기’에 <code>${shown}</code>를 넣어 주세요.</p>
<p><small>이 페이지는 아무것도 저장하지 않아요.</small></p></main>
<script>var u=navigator.userAgent,a=/Android/i.test(u),w=/KAKAOTALK|NAVER|Instagram|FBAN|FBAV|FB_IAB|Line\//i.test(u);
if(a&&w)document.getElementById("go").href=${JSON.stringify(app)};
if(a)location.replace(w?${JSON.stringify(app)}:${JSON.stringify(store)});</script></body></html>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=3600", "referrer-policy": "no-referrer" } });
}

/** Months the inviter was given for invites since [since]. */
async function inviteMonths(env, dev, since = 0) {
  const r = await one(env, `SELECT COALESCE(SUM(months), 0) AS m FROM rewards WHERE device = ? AND rule_id = 'invite' AND status = 'paid'
    AND paid_at > ?`, dev, since);
  return r.m;
}

/** [INVITE-08] The rules as the app says them, before anything is sent. */
const inviteRules = (set) => ({ need: set.friends, days: set.days, months: set.months, capMonths: set.capMonths });

/**
 * [INVITE-05][INVITE-08] What the inviter sees: the code and link, the rules, friends counted toward the next
 * months ([progress] of [need]), friends still in their period ([waiting]), friends counted in all and the months
 * they gave. "3/5명 · 2명은 사용 기간 채우는 중".
 */
async function inviteView(env, dev, origin, now) {
  const set = await inviteSettings(env);
  const [mine, counts, months, recent, used] = await Promise.all([
    one(env, "SELECT code FROM invite_codes WHERE device = ?", dev),
    all(env, "SELECT status, COUNT(*) AS n FROM invite_uses WHERE inviter = ? AND (status != 'waiting' OR created_at > ?) GROUP BY status",
      dev, now - inviteDeadline(set)),
    inviteMonths(env, dev),
    set.capMonths ? inviteMonths(env, dev, now - INVITE.capDays * DAY) : 0,
    one(env, "SELECT status FROM invite_uses WHERE invitee = ?", dev),
  ]);
  const n = (s) => counts.find((c) => c.status === s)?.n ?? 0;
  return {
    code: mine?.code ?? null,
    link: mine ? `${origin}/i/${mine.code}` : null,
    ...inviteRules(set),
    progress: n("qualified"),
    waiting: n("waiting"),
    friends: n("credited") + n("capped") + n("qualified"),
    given: months,
    capReached: set.capMonths > 0 && recent + set.months > set.capMonths,
    // The invite this phone itself came by, as this phone reported it.
    used: used?.status ?? null,
  };
}

/**
 * [INVITE-08] Each [friends] friends who used the app for the period give the inviter [months] of 프로, the oldest
 * first, as often as they add up (a lowered number may give more than one at once). One statement takes a group,
 * so two friends finishing at once cannot both use the same ones. Over a cap, the group is counted without months.
 */
async function settleInvites(env, inviter, set, now) {
  for (let i = 0; i < 50; i++) {
    const have = await one(env, "SELECT COUNT(*) AS n FROM invite_uses WHERE inviter = ? AND status = 'qualified'", inviter);
    if (have.n < set.friends) return;
    const recent = set.capMonths ? await inviteMonths(env, inviter, now - INVITE.capDays * DAY) : 0;
    const capped = inviter.startsWith("gone") || (set.capMonths > 0 && recent + set.months > set.capMonths);
    const id = `i${randomId(21)}`;
    const took = await run(env, `UPDATE invite_uses SET status = ?, batch = ? WHERE invitee IN (SELECT invitee FROM invite_uses
      WHERE inviter = ? AND status = 'qualified' ORDER BY decided_at, invitee LIMIT ?)
      AND (SELECT COUNT(*) FROM invite_uses WHERE inviter = ? AND status = 'qualified') >= ?`,
      capped ? "capped" : "credited", id, inviter, set.friends, inviter, set.friends);
    if (!took.meta.changes) return;
    if (capped) {
      await audit(env, now, "invite_capped", id, inviter.slice(0, 8));
      continue;
    }
    let deferred = null;
    try {
      deferred = await deferFor(env, inviter, set.months, now);
    } catch (e) {
      console.error(`defer invite: ${e?.code ?? e}`);
    }
    const memo = `친구 초대 ${took.meta.changes}명`;
    await run(env, `INSERT OR IGNORE INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at, deferred)
      VALUES (?, ?, 'invite', ?, ?, 'paid', ?, ?, ?, ?)`, id, `invite:${id}`, inviter, set.months,
      deferred ? `${memo} · Play 결제 미룸(다음 결제 ${iso(deferred).slice(0, 10)})` : memo, now, now, deferred ? 1 : 0);
    await audit(env, now, "invite_credit", id, inviter.slice(0, 8));
  }
}

/** [INVITE-08] A lowered number of friends may already be met: settles the inviters it reached. */
async function settleAllInvites(env, set, now) {
  const due = await all(env, `SELECT inviter FROM invite_uses WHERE status = 'qualified' GROUP BY inviter HAVING COUNT(*) >= ? LIMIT 200`, set.friends);
  for (const d of due) await settleInvites(env, d.inviter, set, now);
}

/**
 * [INVITE-02][INVITE-03] /api/invites, /api/invites/code, /api/invites/use and /api/invites/seen, all for one install;
 * [INVITE-08] /api/invites/rules for anyone.
 */
async function invites(request, env, path, url) {
  const method = request.method;
  const now = Date.now();
  // [INVITE-08] The rules the 프로 screen says before the user agrees to anything: no install number goes.
  if (path === "/api/invites/rules" && method === "GET") {
    return json(inviteRules(await inviteSettings(env)), 200, { "cache-control": "public, max-age=3600" });
  }
  const dev = await installOf(request);
  if (path === "/api/invites" && method === "GET") return json(await inviteView(env, dev, url.origin, now));
  if (path === "/api/invites/code" && method === "POST") {
    const body = await readJson(request, 1024);
    const mark = await phoneMark(env, body.phone);
    if (!mark) refuse("phone", "phone");
    if (!(await allow(env, `invitecode|${dev}`, INVITE.codesPerDay, DAY, now))) return json({ error: "rate_limited" }, 429);
    // One code per install, kept as it is: a link already sent keeps working.
    const had = await one(env, "SELECT code FROM invite_codes WHERE device = ?", dev);
    if (had) {
      await run(env, "UPDATE invite_codes SET phone = ? WHERE device = ?", mark, dev);
    } else {
      for (let i = 0; i < 5; i++) {
        const r = await run(env, "INSERT OR IGNORE INTO invite_codes (code, device, phone, created_at) VALUES (?, ?, ?, ?)", inviteCode(), dev, mark, now);
        if (r.meta.changes) break;
      }
    }
    return json(await inviteView(env, dev, url.origin, now), had ? 200 : 201);
  }
  if (path === "/api/invites/use" && method === "POST") {
    const body = await readJson(request, 1024);
    const net = await network(request, env, now);
    if (!(await allow(env, `inviteuse|${net.day}`, INVITE.usesPerHour, HOUR, now)) ||
        !(await allow(env, `inviteuse|${dev}`, INVITE.usesPerDevice, DAY, now))) {
      return json({ error: "rate_limited" }, 429);
    }
    const code = inviteCodeOf(body.code);
    if (!code) refuse("code", "code");
    if (!["referrer", "typed"].includes(body.source)) refuse("source", "source");
    const mark = await phoneMark(env, body.phone);
    if (!mark) refuse("phone", "phone");
    const set = await inviteSettings(env);
    const before = await one(env, "SELECT status, reason FROM invite_uses WHERE invitee = ?", dev);
    if (before) return json({ status: before.status, reason: before.reason || null, days: set.days, daysLeft: null });
    const owner = await one(env, "SELECT device, phone FROM invite_codes WHERE code = ?", code);
    if (!owner) return json({ error: "code", field: "code" }, 404);
    // [INVITE-04] A phone counts once, whoever invited it and however often the app was installed again.
    if (await one(env, "SELECT 1 FROM invite_uses WHERE phone = ?", mark)) {
      await audit(env, now, "invite_refused", code, "phone");
      return json({ status: "refused", reason: "phone", days: set.days, daysLeft: null });
    }
    const self = owner.device === dev || owner.phone === mark;
    await run(env, `INSERT OR IGNORE INTO invite_uses (invitee, phone, code, inviter, source, first_day, status, reason, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`, dev, mark, code, owner.device, body.source, kstDay(now), self ? "refused" : "waiting", self ? "self" : "", now);
    if (self) await audit(env, now, "invite_refused", code, "self");
    // AG 1.27 [INVITE-10] Many friends of one code from one network in an hour (the hour's hashed network, kept the
    // hour as the other counters) are let in, as coworkers share a hospital's or a factory's Wi-Fi, and noted with
    // the code only for the developer app's summary (결정 AG-D5).
    else if (!(await allow(env, `invitenet|${net.now}|${code}`, INVITE.burstPerHour, HOUR, now))) await audit(env, now, "invite_burst", code, "");
    return json({ status: self ? "refused" : "waiting", reason: self ? "self" : null, days: set.days, daysLeft: self ? null : set.days }, 201);
  }
  if (path === "/api/invites/seen" && method === "POST") {
    if (!(await allow(env, `inviteseen|${dev}`, INVITE.seenPerDay, DAY, now))) return json({ error: "rate_limited" }, 429);
    const row = await one(env, "SELECT * FROM invite_uses WHERE invitee = ?", dev);
    if (!row) return json({ error: "not_found" }, 404);
    const set = await inviteSettings(env);
    const answer = (status, daysLeft = null) => json({ status, reason: row.reason || null, days: set.days, daysLeft });
    if (row.status !== "waiting") return answer(row.status);
    if (now - row.created_at > inviteDeadline(set)) {
      await run(env, "UPDATE invite_uses SET status = 'expired', decided_at = ? WHERE invitee = ? AND status = 'waiting'", now, dev);
      return answer("expired");
    }
    // [INVITE-03][INVITE-08] 사용: the app opened again on a Korean day (the server's clock, not the phone's) at least
    // [days] after the day it told the server. The app sends this once a day it is opened.
    const used = dayGap(row.first_day, kstDay(now));
    if (used < set.days) return answer("waiting", set.days - used);
    await run(env, "UPDATE invite_uses SET status = 'qualified', decided_at = ? WHERE invitee = ? AND status = 'waiting'", now, dev);
    await settleInvites(env, row.inviter, set, now);
    return answer((await one(env, "SELECT status FROM invite_uses WHERE invitee = ?", dev))?.status ?? "qualified");
  }
  return json({ error: "not_found" }, 404);
}

/** [INVITE-08] 설정 › 친구 초대 in the developer app: GET the rules, POST changes (a key left out stays as it was). */
async function inviteSettingsRoute(request, env, method, now) {
  if (method === "GET") return json(await inviteSettings(env));
  if (method !== "POST") return json({ error: "not_found" }, 404);
  const body = await readJson(request, 1024);
  const was = await inviteSettings(env);
  const set = Object.fromEntries(Object.entries(INVITE_LIMITS).map(([key, [lo, hi]]) => {
    const v = body[key] ?? was[key];
    if (!Number.isInteger(v) || v < lo || v > hi) refuse(key, key);
    return [key, v];
  }));
  await batch(env, Object.entries(INVITE_KEYS).map(([name, key]) =>
    stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", key, String(set[name]))));
  await audit(env, now, "invite_settings", "", `${set.friends}명 · ${set.days}일 · ${set.months}개월 · 상한 ${set.capMonths ? `${set.capMonths}개월` : "없음"}`);
  await settleAllInvites(env, set, now);
  return json(set);
}

/**
 * [INVITE-06] For the developer app's 보상 screen: the rewards by status and, when [withInvites], how invites went,
 * the rules, and the codes most friends came by. One trip, so the screen stays at three (ADMIN-16).
 */
async function rewardCounts(env, withInvites, now) {
  const [rewardRows, counts, refused, top, codes, setRows, bursts] = (await batch(env, [
    stmt(env, "SELECT status, COUNT(*) AS n FROM rewards GROUP BY status"),
    stmt(env, "SELECT status, COUNT(*) AS n FROM invite_uses GROUP BY status"),
    stmt(env, "SELECT detail, COUNT(*) AS n FROM audit WHERE action = 'invite_refused' AND at > ? GROUP BY detail", now - 30 * DAY),
    stmt(env, `SELECT code, COUNT(*) AS friends, SUM(status IN ('credited', 'capped', 'qualified')) AS counted, SUM(status = 'waiting') AS waiting
      FROM invite_uses WHERE status IN ('credited', 'capped', 'qualified', 'waiting') GROUP BY code ORDER BY friends DESC LIMIT 5`),
    stmt(env, "SELECT COUNT(*) AS n FROM invite_codes"),
    stmt(env, INVITE_SETTINGS_SQL),
    // AG 1.27 [INVITE-10] 몰림: friends of one code from one network in an hour, the last 30 days.
    stmt(env, "SELECT target AS code, COUNT(*) AS n FROM audit WHERE action = 'invite_burst' AND at > ? GROUP BY target ORDER BY n DESC LIMIT 5", now - 30 * DAY),
  ])).map((r) => r.results ?? []);
  const n = (s) => counts.find((c) => c.status === s)?.n ?? 0;
  return [rewardRows, withInvites ? {
    codes: codes[0]?.n ?? 0, credited: n("credited"), capped: n("capped"), qualified: n("qualified"), waiting: n("waiting"),
    expired: n("expired"), rules: readInviteSettings(setRows),
    refused: Object.fromEntries(refused.map((r) => [r.detail, r.n])),
    top: top.map((t) => ({ code: t.code, friends: t.friends, counted: t.counted ?? 0, waiting: t.waiting ?? 0 })),
    bursts: bursts.reduce((a, b) => a + b.n, 0),
    burstCodes: bursts.map((b) => ({ code: b.code, n: b.n })),
  } : undefined];
}
// AE 1.26 끝

// ---- 관리자 휴대폰 [MON-09] ----

/** The developer app makes a code for the signed-in admin's own phone. */
async function linkCode(env, actor, now) {
  if (!(await allow(env, `linkcode|${actor.id}`, MONEY.linkCodesPerHour, HOUR, now))) return json({ error: "rate_limited" }, 429);
  let code;
  for (let i = 0; i < 5 && !code; i++) {
    const n = crypto.getRandomValues(new Uint32Array(1))[0] % 1000000;
    const c = String(n).padStart(6, "0");
    if (!(await one(env, "SELECT 1 FROM link_codes WHERE code = ? AND expires > ?", c, now))) code = c;
  }
  if (!code) return json({ error: "busy" }, 503);
  await batch(env, [
    stmt(env, "DELETE FROM link_codes WHERE admin = ? OR expires < ?", actor.id, now),
    stmt(env, "INSERT INTO link_codes (code, admin, admin_name, expires) VALUES (?, ?, ?, ?)", code, actor.id, actor.name, now + MONEY.linkMs),
  ]);
  return json({ code, expiresAt: iso(now + MONEY.linkMs) }, 201);
}

async function linkPhone(request, env, dev, now) {
  const body = await readJson(request, 1024);
  const net = await network(request, env, now);
  if (!(await allow(env, `link|${net.day}`, MONEY.linkTriesPerHour, HOUR, now)) ||
      !(await allow(env, `link|${dev}`, MONEY.linkTriesPerHour, HOUR, now))) {
    return json({ error: "rate_limited" }, 429);
  }
  if (typeof body.code !== "string" || !/^\d{6}$/.test(body.code)) refuse("code", "code");
  const row = await one(env, "SELECT * FROM link_codes WHERE code = ? AND expires > ?", body.code, now);
  if (!row) return json({ error: "code", field: "code" }, 404);
  const owner = row.admin === OWNER.id;
  if (!owner && !(await one(env, "SELECT 1 FROM admins WHERE id = ? AND status = 'active'", row.admin))) {
    await run(env, "DELETE FROM link_codes WHERE code = ?", body.code);
    return json({ error: "code", field: "code" }, 404);
  }
  const others = await one(env, "SELECT COUNT(*) AS n FROM admin_devices WHERE admin = ? AND device != ?", row.admin, dev);
  if (others.n >= MONEY.phonesPerAdmin) return json({ error: "too_many", field: "code" }, 409);
  const status = owner ? "approved" : "pending";
  await batch(env, [
    stmt(env, "DELETE FROM link_codes WHERE code = ?", body.code),
    stmt(env, "DELETE FROM admin_devices WHERE device = ?", dev),
    stmt(env, `INSERT INTO admin_devices (id, device, admin, admin_name, status, created_at, approved_at, seen_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)`, `d${randomId(15)}`, dev, row.admin, row.admin_name, status, now, owner ? now : null, now),
  ]);
  await audit(env, now, "device_link", row.admin_name, status);
  return json({ status, name: row.admin_name }, 201);
}

async function phoneList(env) {
  const rows = await all(env, "SELECT id, admin, admin_name, status, created_at, approved_at, seen_at FROM admin_devices ORDER BY created_at DESC");
  return {
    devices: rows.map((d) => ({
      id: d.id, admin: d.admin, adminName: d.admin_name, status: d.status, createdAt: iso(d.created_at),
      approvedAt: d.approved_at ? iso(d.approved_at) : null, seenAt: d.seen_at ? iso(d.seen_at) : null,
    })),
    perAdmin: MONEY.phonesPerAdmin,
  };
}

async function setPhone(env, id, action, now) {
  const d = await one(env, "SELECT * FROM admin_devices WHERE id = ?", id);
  if (!d) return json({ error: "not_found" }, 404);
  if (action === "approve") await run(env, "UPDATE admin_devices SET status = 'approved', approved_at = ? WHERE id = ?", now, id);
  else await run(env, "DELETE FROM admin_devices WHERE id = ?", id);
  await audit(env, now, action === "approve" ? "device_approve" : "device_remove", d.admin_name);
  return json(await phoneList(env));
}

// ---- 구매 확인과 체험 전환 동의 [MON-03][MON-06] ----

const PLAY = {
  scope: "https://www.googleapis.com/auth/androidpublisher",
  token: "https://oauth2.googleapis.com/token",
  api: "https://androidpublisher.googleapis.com/androidpublisher/v3/applications",
  states: {
    SUBSCRIPTION_STATE_ACTIVE: "ACTIVE", SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "GRACE", SUBSCRIPTION_STATE_CANCELED: "CANCELED",
    SUBSCRIPTION_STATE_ON_HOLD: "ON_HOLD", SUBSCRIPTION_STATE_PAUSED: "PAUSED", SUBSCRIPTION_STATE_EXPIRED: "EXPIRED",
    SUBSCRIPTION_STATE_PENDING: "PENDING", SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED: "EXPIRED",
  },
};
const PRODUCT = /^[a-z0-9_.]{1,40}$/;
/** [SRV-07] How long before a trial turns paid an unanswered one is stopped, how long a first phone keeps its token, rechecks an hour. */
const TRIAL_STOP_BEFORE = 2 * HOUR;
const REBIND_AFTER = 7 * DAY;
const RECHECK_BATCH = 20;
const playTokens = new WeakMap();

/** The service account the owner pasted as the secret PLAY_SERVICE_ACCOUNT, or null; its key is never shown. */
function playReady(env) {
  if (typeof env.PLAY_SERVICE_ACCOUNT !== "string" || !env.PLAY_SERVICE_ACCOUNT.trim()) return null;
  try {
    const a = JSON.parse(env.PLAY_SERVICE_ACCOUNT);
    return typeof a.client_email === "string" && typeof a.private_key === "string" ? a : null;
  } catch {
    return null;
  }
}

const b64url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlText = (text) => b64url(new TextEncoder().encode(text));

/** An OAuth token for the Play Developer API from the service account (JWT bearer, RS256), kept until near its end. */
async function playToken(env, account, now, scope = PLAY.scope) {
  // [ADMUX-16] One token per scope: purchases (androidpublisher) and Play's vitals (playdeveloperreporting).
  const tokens = playTokens.get(env.DB ?? env) ?? {};
  const kept = tokens[scope];
  if (kept && kept.until > now + 60_000) return kept.token;
  const pem = account.private_key.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const iat = Math.floor(now / 1000);
  const head = b64urlText(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64urlText(JSON.stringify({ iss: account.client_email, scope, aud: PLAY.token, iat, exp: iat + 3600 }));
  const sig = b64url(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${head}.${claims}`)));
  const res = await fetch(PLAY.token, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${head}.${claims}.${sig}`,
  });
  if (!res.ok) throw new Refuse("play_auth", "play", 502);
  const t = await res.json();
  playTokens.set(env.DB ?? env, { ...tokens, [scope]: { token: t.access_token, until: now + (Number(t.expires_in) || 3600) * 1000 } });
  return t.access_token;
}

async function playCall(env, method, path, body) {
  const account = playReady(env);
  if (!account) throw new Refuse("play_off", "play", 503);
  const pkg = env.PLAY_PACKAGE || "com.bamnat.app";
  const res = await fetch(`${PLAY.api}/${encodeURIComponent(pkg)}${path}`, {
    method,
    headers: { authorization: `Bearer ${await playToken(env, account, Date.now())}`, ...(body ? { "content-type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 404 || res.status === 410) throw new Refuse("purchase", "token", 404);
  if (!res.ok) throw new Refuse("play", "play", 502);
  const text = await res.text();
  return text ? JSON.parse(text) : {};
}

/** [MON-03] subscriptionsv2.get read the way the app needs it. */
export function readSub(p, product) {
  const items = Array.isArray(p.lineItems) ? p.lineItems : [];
  const item = items.find((i) => i.productId === product) ?? items[0] ?? {};
  const expiry = Date.parse(item.expiryTime ?? "");
  return {
    state: PLAY.states[p.subscriptionState] ?? "PENDING",
    expiry: Number.isFinite(expiry) ? expiry : null,
    trial: Boolean(item.offerPhase?.freeTrial),
    autoRenew: item.autoRenewingPlan?.autoRenewEnabled === true,
    basePlan: item.offerDetails?.basePlanId ?? "",
    acknowledged: p.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    etag: typeof p.etag === "string" ? p.etag : "",
  };
}

/**
 * [MON-03] The app sends what Play gave it after a purchase and on each start. With the owner's service
 * account the server asks Play itself and keeps the token while the subscription lasts (for consent and
 * reward deferral); without one it keeps nothing and the app goes by the phone's Play.
 */
async function purchase(request, env, dev, now) {
  const body = await readJson(request, 4096);
  if (typeof body.product !== "string" || !PRODUCT.test(body.product)) refuse("product", "product");
  if (typeof body.token !== "string" || body.token.length < 10 || body.token.length > 1000) refuse("token", "token");
  if (!playReady(env)) return json({ verified: false });
  const s = readSub(await playCall(env, "GET", `/purchases/subscriptionsv2/tokens/${encodeURIComponent(body.token)}`), body.product);
  const hash = (await sha256(`sub|${body.token}`)).slice(0, 40);
  // [SRV-07] A token already tied to another install stays with it while that install still checks in: a
  // leaked token cannot move the consent record or reward deferrals to someone else's phone. After a week
  // without the first phone (a new phone with the same Google account) it moves, and the log says so.
  const bound = await one(env, "SELECT device, checked_at FROM subs WHERE hash = ?", hash);
  if (bound && bound.device !== dev) {
    if (now - bound.checked_at < REBIND_AFTER) {
      await audit(env, now, "purchase_rebind_refused", body.product, hash.slice(0, 8));
      return json({ verified: false });
    }
    await audit(env, now, "purchase_rebind", body.product, hash.slice(0, 8));
  }
  await run(env, `INSERT INTO subs (hash, device, token, product, base_plan, state, expiry, trial, auto_renew, created_at, checked_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (hash) DO UPDATE SET device = excluded.device, state = excluded.state, expiry = excluded.expiry, trial = excluded.trial,
      auto_renew = excluded.auto_renew, base_plan = excluded.base_plan, checked_at = excluded.checked_at`,
  hash, dev, body.token, body.product, s.basePlan, s.state, s.expiry, s.trial ? 1 : 0, s.autoRenew ? 1 : 0, now, now);
  const row = await one(env, "SELECT consent_at, stopped_at FROM subs WHERE hash = ?", hash);
  return json({
    verified: true, state: s.state, expiry: s.expiry ? iso(s.expiry) : null, trial: s.trial, autoRenew: s.autoRenew,
    acknowledged: s.acknowledged, consentAt: row.consent_at ? iso(row.consent_at) : null, stoppedAt: row.stopped_at ? iso(row.stopped_at) : null,
  });
}

/**
 * [MON-06] The user's own answer before a trial turns paid (전자상거래법 제13조 6항): "keep" or "cancel",
 * with the price and the date they were shown. Kept as evidence; the trial's record says consent was given.
 */
async function consent(request, env, dev, now) {
  const body = await readJson(request, 2048);
  // [SRV-07] A phone that could not send the answer at once sends it later from its queue, with the moment
  // the user tapped: that moment is the consent, as long as it is not in the future or before the trial.
  const tapped = Date.parse(body.answeredAt ?? "");
  const at = Number.isFinite(tapped) && tapped <= now + 5 * 60 * 1000 && tapped >= now - 40 * DAY ? Math.min(tapped, now) : now;
  if (!["keep", "cancel"].includes(body.answer)) refuse("answer", "answer");
  if (typeof body.product !== "string" || !PRODUCT.test(body.product)) refuse("product", "product");
  const price = typeof body.price === "string" ? body.price.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 40) : "";
  const ends = Date.parse(body.trialEnd ?? "");
  if (!price || !Number.isFinite(ends)) refuse("detail", "price");
  if (!(await allow(env, `consent|${dev}`, 20, DAY, now))) return json({ error: "rate_limited" }, 429);
  const detail = JSON.stringify({ answer: body.answer, product: body.product, price, trialEnd: iso(ends), shown: String(body.shown ?? "").slice(0, 300),
    ...(at !== now ? { answeredAt: iso(at), receivedAt: iso(now) } : {}) });
  await batch(env, [
    stmt(env, "INSERT INTO consents (device, kind, detail, at) VALUES (?, 'trial_paid', ?, ?)", dev, detail, at),
    ...(body.answer === "keep" ? [stmt(env, "UPDATE subs SET consent_at = ? WHERE device = ? AND trial = 1 AND product = ?", at, dev, body.product)] : []),
  ]);
  return json({ ok: true, at: iso(at) }, 201);
}

/**
 * [MON-06] Hourly: a trial ending within [TRIAL_STOP_BEFORE] whose user has not said "keep" does not turn
 * paid. The next renewal is stopped as if the user had cancelled (they can resubscribe in Play); without
 * the service account nothing can be stopped from here and the app keeps asking instead.
 * [SRV-07] Two hours, not a day: a "keep" tapped on the subway waits in the phone's queue and reaches the
 * server on the next connection, and the hourly run still has two tries before Play charges.
 */
export async function stopUnconsentedTrials(env, now) {
  if (!playReady(env)) return 0;
  const due = await all(env, `SELECT hash, token, product, expiry FROM subs WHERE trial = 1 AND auto_renew = 1 AND consent_at IS NULL
    AND stopped_at IS NULL AND state IN ('ACTIVE', 'GRACE') AND expiry > ? AND expiry <= ?`, now, now + TRIAL_STOP_BEFORE);
  let stopped = 0;
  for (const s of due) {
    try {
      await playCall(env, "POST", `/purchases/subscriptionsv2/tokens/${encodeURIComponent(s.token)}:cancel`,
        { cancellationContext: { cancellationType: "USER_REQUESTED_STOP_RENEWALS" } });
      await run(env, "UPDATE subs SET stopped_at = ?, auto_renew = 0 WHERE hash = ?", now, s.hash);
      await audit(env, now, "trial_stop", s.product, iso(s.expiry));
      stopped++;
    } catch (e) {
      console.error(`trial stop ${s.hash.slice(0, 8)}: ${e?.code ?? e}`);
    }
  }
  return stopped;
}

/**
 * [SRV-07] Daily per subscription: one Play says is still running past its end is asked again, so a
 * renewal, a grace period or a hold is known here even when the phone has not opened the app. At most
 * [RECHECK_BATCH] an hour, each no more than once a day.
 */
export async function recheckLapsed(env, now) {
  if (!playReady(env)) return 0;
  const due = await all(env, `SELECT hash, token, product FROM subs WHERE state IN ('ACTIVE', 'GRACE') AND expiry IS NOT NULL
    AND expiry < ? AND checked_at < ? ORDER BY checked_at LIMIT ?`, now, now - DAY, RECHECK_BATCH);
  let checked = 0;
  for (const r of due) {
    try {
      const s = readSub(await playCall(env, "GET", `/purchases/subscriptionsv2/tokens/${encodeURIComponent(r.token)}`), r.product);
      await run(env, "UPDATE subs SET state = ?, expiry = ?, auto_renew = ?, checked_at = ? WHERE hash = ?",
        s.state, s.expiry, s.autoRenew ? 1 : 0, now, r.hash);
      checked++;
    } catch (e) {
      // A token Play no longer knows has ended; anything else is tried again tomorrow.
      if (e instanceof Refuse && e.code === "purchase") await run(env, "UPDATE subs SET state = 'EXPIRED', checked_at = ? WHERE hash = ?", now, r.hash);
      else await run(env, "UPDATE subs SET checked_at = ? WHERE hash = ?", now, r.hash);
      console.error(`recheck ${r.hash.slice(0, 8)}: ${e?.code ?? e}`);
    }
  }
  return checked;
}

/**
 * [MON-08] A reward for someone paying a running subscription is given as a later renewal (Play's
 * deferral) instead of a pass running beside what they already pay for. Null when it cannot be.
 */
async function deferFor(env, dev, months, now) {
  if (!playReady(env)) return null;
  const sub = await one(env, `SELECT * FROM subs WHERE device = ? AND trial = 0 AND auto_renew = 1 AND state = 'ACTIVE' AND expiry > ?
    ORDER BY expiry DESC LIMIT 1`, dev, now);
  if (!sub) return null;
  const path = `/purchases/subscriptionsv2/tokens/${encodeURIComponent(sub.token)}`;
  const fresh = readSub(await playCall(env, "GET", path), sub.product);
  if (fresh.state !== "ACTIVE" || !fresh.expiry) return null;
  const seconds = Math.round((addMonths(fresh.expiry, months) - fresh.expiry) / 1000);
  const r = await playCall(env, "POST", `${path}:defer`, { deferralContext: { etag: fresh.etag, deferDuration: `${seconds}s` } });
  const next = Date.parse(r.itemExpiryTimeDetails?.[0]?.expiryTime ?? "") || addMonths(fresh.expiry, months);
  await run(env, "UPDATE subs SET expiry = ?, checked_at = ? WHERE hash = ?", next, now, sub.hash);
  return next;
}

// ---- running the service, 1.22 [SRV-04][SRV-05][SRV-06][STAT-01][COMM-09][COMM-10][ADMIN-13][ADMIN-14] ----

/** A day as people in Korea count it (KST, no daylight saving), "2026-10-22". */
const kstDay = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);

/**
 * [SRV-04] What the developer app can switch off for every phone at once. The app hides the way in and
 * says it is resting; the server stops its own part too, so an older app cannot go around it.
 */
const SWITCHES = {
  PLAZA_WRITE: "광장 글쓰기(글·답글·제안·임금표)",
  COMPANY_REPORT: "회사 규칙 보내기",
  INQUIRY_PHOTOS: "문의 사진 받기",
  SHARE_CREATE: "근무표 링크 새로 만들기",
  USAGE_STATS: "사용 통계 받기",
  PLAZA_NEWS: "광장 소식 알림",
};

/** [SRV-04] A notice the app shows as one line over the calendar; tapping it opens the rest. */
const NOTICE = { max: 10, title: 40, body: 500, levels: ["info", "important"], longestDays: 90 };

/**
 * [SRV-09] Who a notice is for, chosen on the phone: the server only checks the shape and sends every
 * notice to every app, which keeps those that fit (maker, Android version, way of working, alarms on).
 * Nothing about the phone comes here. And what its button opens in the app, or an https page.
 */
const AUDIENCE = {
  makers: 10, maker: /^[a-z0-9][a-z0-9 _-]{0,19}$/,
  workTypes: ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"],
  features: ["ALARM", "SHARE", "PAY", "PLAZA", "BACKUP"],
  sdk: [21, 99],
};
const NOTICE_ACTIONS = ["ALARM_CHECK", "BACKUP", "PRO", "INQUIRY", "SHARE", "PLAZA", "WHATS_NEW", "URL"];

/** [SRV-09] A notice's audience as the developer app sends it; null for everyone. */
export function readAudience(a) {
  if (a === undefined || a === null) return null;
  if (typeof a !== "object" || Array.isArray(a)) refuse("audience", "audience");
  const out = {};
  if (a.makers !== undefined) {
    if (!Array.isArray(a.makers) || a.makers.length > AUDIENCE.makers) refuse("audience", "makers");
    const makers = a.makers.map((m) => String(m).trim().toLowerCase());
    if (!makers.every((m) => AUDIENCE.maker.test(m))) refuse("audience", "makers");
    if (makers.length) out.makers = [...new Set(makers)];
  }
  for (const k of ["minSdk", "maxSdk"]) {
    if (a[k] === undefined || a[k] === null) continue;
    if (!Number.isInteger(a[k]) || a[k] < AUDIENCE.sdk[0] || a[k] > AUDIENCE.sdk[1]) refuse("audience", k);
    out[k] = a[k];
  }
  if (out.minSdk && out.maxSdk && out.maxSdk < out.minSdk) refuse("audience", "maxSdk");
  for (const [k, allowed] of [["workTypes", AUDIENCE.workTypes], ["features", AUDIENCE.features]]) {
    if (a[k] === undefined) continue;
    if (!Array.isArray(a[k]) || !a[k].every((v) => allowed.includes(v))) refuse("audience", k);
    if (a[k].length) out[k] = [...new Set(a[k])];
  }
  return Object.keys(out).length ? out : null;
}

/** [SRV-09] A notice's button: a place in the app, or an https page with a short label. */
export function readAction(a) {
  if (a === undefined || a === null) return null;
  if (typeof a !== "object" || !NOTICE_ACTIONS.includes(a.kind)) refuse("action", "action");
  const label = a.label === undefined || a.label === null || a.label === "" ? null : plainText(a.label, 12, "action", true);
  if (a.kind !== "URL") return { kind: a.kind, ...(label ? { label } : {}) };
  let url;
  try {
    url = new URL(String(a.url ?? ""));
  } catch {
    refuse("action", "url");
  }
  if (url.protocol !== "https:" || String(a.url).length > 300) refuse("action", "url");
  return { kind: "URL", url: url.toString(), ...(label ? { label } : {}) };
}

/** [SRV-04] The notices as stored. */
async function noticeRows(env) {
  const row = await one(env, "SELECT value FROM settings WHERE key = 'notices'");
  try {
    const v = JSON.parse(row?.value ?? "[]");
    return (Array.isArray(v) ? v : []).filter((n) => n && typeof n.id === "string" && Number.isFinite(n.from) && Number.isFinite(n.until));
  } catch {
    return [];
  }
}

async function opsState(env) {
  // AM 1.28 [FLAG-24] what is switched off is the six switch flags forced off.
  await migrateFlags(env);
  const [notices, forced] = await Promise.all([noticeRows(env), all(env, "SELECT key, until FROM flag_overrides WHERE force = 'off'")]);
  const now = Date.now();
  return { notices, off: forced.filter((r) => SWITCH_FLAGS[r.key] && !(r.until && r.until <= now)).map((r) => SWITCH_FLAGS[r.key]) };
}

/** [SRV-04] Whether the developer switched [name] off. AM 1.28 [FLAG-24]: the flag's force, read alone. */
const paused = (env, name) => switchOff(env, name);

const noticeView = (n) => ({ id: n.id, title: n.title, body: n.body, level: n.level, from: iso(n.from), until: iso(n.until),
  minApp: n.minApp ?? null, maxApp: n.maxApp ?? null, audience: n.audience ?? null, action: n.action ?? null });

/** A notice as the developer app sends it; the app version range is the app's versionCode, either end open. */
function readNotice(body, now) {
  const title = plainText(body.title, NOTICE.title, "title", true);
  const text = plainText(body.body, NOTICE.body, "body", true);
  const level = NOTICE.levels.includes(body.level) ? body.level : refuse("level", "level");
  const from = Date.parse(body.from ?? "");
  const until = Date.parse(body.until ?? "");
  if (!Number.isFinite(from)) refuse("from", "from");
  if (!Number.isFinite(until) || until <= from || until < now || until - from > NOTICE.longestDays * DAY) refuse("until", "until");
  const version = (v, field) => {
    if (v === undefined || v === null || v === "") return null;
    return Number.isInteger(v) && v > 0 && v < 1_000_000 ? v : refuse("app", field);
  };
  const minApp = version(body.minApp, "minApp");
  const maxApp = version(body.maxApp, "maxApp");
  if (minApp !== null && maxApp !== null && maxApp < minApp) refuse("app", "maxApp");
  return { title, body: text, level, from, until, minApp, maxApp, audience: readAudience(body.audience), action: readAction(body.action) };
}

async function noticeList(env, now) {
  const s = await opsState(env);
  return {
    // [ADMUX-07] The newest app this server has heard from, for the notice form's 앱 버전 칸.
    latestApp: await latestApp(env, now),
    notices: s.notices.sort((a, b) => b.from - a.from).map((n) => ({ ...noticeView(n), state: now < n.from ? "later" : now < n.until ? "on" : "ended" })),
    switches: Object.entries(SWITCHES).map(([key, label]) => ({ key, label, off: s.off.includes(key) })),
  };
}

/** [ADMUX-07] The highest app version (name and versionCode) in questions of the last 60 days, or null. */
async function latestApp(env, now) {
  const rows = await all(env, `SELECT DISTINCT json_extract(info, '$.app') AS app FROM inquiries WHERE created_at > ? AND json_valid(info)
    ORDER BY created_at DESC LIMIT 300`, now - 60 * DAY);
  let best = null;
  for (const { app } of rows) {
    const m = String(app ?? "").match(/^\s*([\d.]+)\s*\((\d+)\)/);
    if (m && (!best || Number(m[2]) > best.code)) best = { name: m[1], code: Number(m[2]) };
  }
  return best;
}

/** Adds a notice, or changes one with the same id. Ended ones are dropped when the list is full. */
async function saveNotice(request, env, now) {
  const body = await readJson(request, 4096);
  const n = readNotice(body, now);
  const s = await opsState(env);
  let list = s.notices.filter((x) => x.until > now - 30 * DAY);
  const id = typeof body.id === "string" && /^n[A-Za-z0-9]{9}$/.test(body.id) ? body.id : null;
  if (id && !list.some((x) => x.id === id)) return json({ error: "not_found" }, 404);
  if (id) list = list.map((x) => (x.id === id ? { id, ...n } : x));
  else {
    if (list.length >= NOTICE.max) list = list.filter((x) => x.until > now);
    if (list.length >= NOTICE.max) refuse("too_many", "notices");
    list.push({ id: `n${randomId(9)}`, ...n });
  }
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('notices', ?)", JSON.stringify(list));
  await audit(env, now, id ? "notice_edit" : "notice_add", n.title);
  return json(await noticeList(env, now));
}

async function removeNotice(env, id, now) {
  const s = await opsState(env);
  const gone = s.notices.find((x) => x.id === id);
  if (!gone) return json({ error: "not_found" }, 404);
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('notices', ?)", JSON.stringify(s.notices.filter((x) => x.id !== id)));
  await audit(env, now, "notice_remove", gone.title);
  return json(await noticeList(env, now));
}

/** [FLAG-38] What a 끄기 from a developer app cached before 1.28 (no why) is recorded with. */
const OLD_APP_WHY = "옛 관리자 앱에서 끔";

/**
 * [SRV-04] 기능 스위치, kept for the 운영 screen's old buttons: AM 1.28 [FLAG-24] it forces the switch's flag off (with why)
 * or back to its conditions, under the same rule as 기능 관리 (결정 D97: forcing off is the owner's or flagOff's).
 */
async function setSwitch(request, env, now) {
  const body = await readJson(request, 1024);
  if (!Object.hasOwn(SWITCHES, body.key)) refuse("key", "key");
  if (typeof body.off !== "boolean") refuse("off", "off");
  const key = Object.keys(SWITCH_FLAGS).find((k) => SWITCH_FLAGS[k] === body.key);
  // 1.28 통합 [FLAG-38] A developer app cached before 1.28 sends no why: its 끄기 still works, and the record says where it came from.
  const given = typeof body.why === "string" ? body.why.trim() : "";
  const old = body.off && !given;
  const res = await setFlagForce(new Request("https://switch/", { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ force: body.off ? "off" : null, why: old ? OLD_APP_WHY : given }) }), env, env.actor, key, now);
  if (res.status !== 200) return res;
  await audit(env, now, body.off ? "switch_off" : "switch_on", body.key, old ? `${SWITCHES[body.key]} · ${OLD_APP_WHY}` : SWITCHES[body.key]);
  return json(await noticeList(env, now));
}

/**
 * [ADMIN-14] The year's chores, month by month, the same list as docs/ops-calendar.md (a test keeps the
 * two together). Shown on the developer app's home screen in place of mail (결정 D1: no mail key).
 */
const OPS_CALENDAR = [
  [1, "rates-new-year", "새해 최저임금·4대 보험 요율이 앱 계산과 맞는지 확인", "최저임금법 제10조 제2항, 국민건강보험법 시행령 제44조, 노인장기요양보험법 시행령 제4조"],
  [1, "wages-jan", "1월 공표 시중노임단가를 remote/v1/wages.json에 넣기(판 올리고 서명)", "대한건설협회 시중노임단가(1월·9월 공표)"],
  [1, "holidays-this-year", "올해 공휴일·대체공휴일이 달력과 맞는지 확인", "관공서의 공휴일에 관한 규정 제2조·제3조"],
  [3, "tax-table", "근로소득 간이세액표가 바뀌었으면 remote/v1/taxtable.json 고치기(판 올리고 서명)", "소득세법 시행령 별표 2(2026년 개정은 3월 1일 이후 원천징수분부터)"],
  [3, "privacy-review", "처리방침·데이터 보안 답안을 지금 서버와 대조", "개인정보 보호법 제30조, docs/server-data.md"],
  [3, "incident-drill", "유출 대응 절차서를 한 번 따라 해 보기(연락 순서·공지 문구)", "개인정보 보호법 제34조, docs/incident-response.md"],
  [4, "android-preview", "새 Android 미리보기에서 알람·알림 점검", "Play 대상 API 수준 요건(매년 8월 31일)"],
  [5, "store-listing", "스토어 등록정보·화면 사진이 지금 앱과 맞는지 확인", "Google Play 메타데이터 정책"],
  [6, "pension-prepare", "7월부터 쓰는 국민연금 기준소득월액 상하한 준비", "국민연금법 시행령 제5조"],
  [6, "labor-law-amendment", "2027-06-10 시행 근로기준법 개정(조문 번호 이동, 시간 단위 연차)에 맞춰 앱의 법 조문 글귀와 연차 계산을 확인", "근로기준법 개정(2027-06-10 시행), docs/tracker.csv LEAVE-12"],
  [7, "pension-apply", "국민연금 기준소득월액 상하한을 remote/v1/rates.json에 넣어 7월 급여부터 맞추기(판 올리고 서명)", "국민연금법 시행령 제5조"],
  [8, "minwage-next", "다음 해 최저임금 결정(8월 5일까지)을 앱에 넣을 준비", "최저임금법 제8조 제1항"],
  [8, "target-api", "대상 API 수준 마감(8월 31일) 전에 업데이트 출시", "Google Play 대상 API 수준 요건"],
  [9, "wages-sep", "9월 공표 시중노임단가를 remote/v1/wages.json에 넣기(판 올리고 서명)", "대한건설협회 시중노임단가(1월·9월 공표)"],
  [9, "holidays-next", "다음 해 공휴일·대체공휴일·선거일 확인", "관공서의 공휴일에 관한 규정"],
  [10, "health-next", "다음 해 건강보험료율이 결정되면 remote/v1/rates.json에 넣기(결정 시기는 해마다 확인)", "국민건강보험법 시행령 제44조"],
  [11, "ltc-next", "다음 해 장기요양보험료율이 결정되면 remote/v1/rates.json에 넣기(2027년 요율은 결정되는 대로)", "노인장기요양보험법 시행령 제4조"],
  [12, "release-new-year", "새해 요율·최저임금·공휴일을 넣은 업데이트를 1월 1일 전에 출시", "위 항목들"],
  [12, "server-data", "서버 최소 데이터 점검(지울 것은 지우기)", "개인정보 보호법 제21조, docs/server-data.md"],
].map(([month, key, text, source]) => ({ month, key, text, source }));

/** One month's chores with which of them are ticked ([done] is the stored list of keys). */
function opsItems(month, done) {
  let ticked = [];
  try {
    ticked = JSON.parse(done ?? "[]");
  } catch {
    ticked = [];
  }
  const m = Number(month.slice(5, 7));
  return OPS_CALENDAR.filter((i) => i.month === m).map((i) => ({ key: i.key, text: i.text, source: i.source, done: ticked.includes(i.key) }));
}

const MONTH = /^20\d\d-(0[1-9]|1[0-2])$/;

async function opsMonth(env, month, now) {
  const m = MONTH.test(month ?? "") ? month : kstDay(now).slice(0, 7);
  const row = await one(env, "SELECT value FROM settings WHERE key = ?", `ops:${m}`);
  return { month: m, items: opsItems(m, row?.value) };
}

async function checkOps(request, env, now) {
  const body = await readJson(request, 1024);
  if (!MONTH.test(body.month ?? "")) refuse("month", "month");
  const item = OPS_CALENDAR.find((i) => i.key === body.key && i.month === Number(body.month.slice(5, 7)));
  if (!item) refuse("key", "key");
  if (typeof body.done !== "boolean") refuse("done", "done");
  const row = await one(env, "SELECT value FROM settings WHERE key = ?", `ops:${body.month}`);
  const was = opsItems(body.month, row?.value).filter((i) => i.done).map((i) => i.key);
  const next = body.done ? [...new Set([...was, item.key])] : was.filter((k) => k !== item.key);
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", `ops:${body.month}`, JSON.stringify(next));
  await audit(env, now, body.done ? "ops_done" : "ops_undo", body.month, item.text);
  return json(await opsMonth(env, body.month, now));
}

// [STAT-01] Usage statistics (결정 D2·D42): only phones whose user said yes send, once a day, the days
// before today as event names and counts. The app keeps the same list (core UsageEvents.kt); a name the
// server does not know is skipped, so a newer app still sends the rest.

/**
 * [STAT-05] Up to 120 bundles a day from one network: a mobile carrier puts thousands of phones behind
 * one address, so the old 6 dropped most of them. The same bundle sent twice (a retry) counts once a day.
 */
const USAGE = { days: 7, keepDays: 400, count: 1000, bundlesPerNetworkDay: 120, bytes: 8 * 1024 };

/** Event names and what the dashboard calls them. */
export const EVENTS = {
  open: "앱 열기",
  pay_view: "급여 화면",
  pay_setup: "급여 설정 마침",
  pay_report: "급여 리포트",
  paywall_view: "프로 화면 보기",
  trial_start: "무료 체험 시작",
  subscribe: "구독 시작",
  pro_sub: "프로 구독 중",
  share_made: "근무표 링크 만들기",
  share_received: "받은 근무표 넣기",
  plaza_post: "광장 글쓰기",
  plaza_apply: "광장 규칙 적용",
  company_report: "회사 규칙 보내기",
  inquiry: "문의 보내기",
  backup: "백업 만들기",
  alarm_rang: "근무 알람 울림",
  ics_export: "캘린더로 내보내기",
  // [STAT-06] 1.23: a notice opened (how often, never which or by whom).
  notice_open: "공지 열기",
};

async function usage(request, env) {
  // Switched off, the server takes nothing and the app does not try again until tomorrow.
  if (await paused(env, "USAGE_STATS")) return new Response(null, { status: 204 });
  const body = await readJson(request, USAGE.bytes);
  if (body.v !== 1) refuse("version", "v");
  if (typeof body.app !== "string" || !/^\d{1,2}\.\d{1,3}\.\d{1,3}$/.test(body.app)) refuse("app", "app");
  if (!Array.isArray(body.days) || body.days.length < 1 || body.days.length > USAGE.days) refuse("days", "days");
  const now = Date.now();
  const net = await network(request, env, now);
  if (!(await allow(env, `events|${net.day}`, USAGE.bundlesPerNetworkDay, DAY, now))) return json({ error: "rate_limited" }, 429);
  // [STAT-05] A bundle already counted today (the phone sent it again after a timeout) is not counted twice.
  const bundle = (await sha256(`events|${kstDay(now)}|${JSON.stringify(body)}`)).slice(0, 32);
  if (!(await allow(env, `bundle|${bundle}`, 1, DAY, now))) return json({ ok: true, again: true });
  // A phone's day can be a day off Korea's; one ahead of tomorrow or older than the week is not a day it kept.
  // [STAT-05] Such a day is left out and the rest counted, instead of refusing the whole bundle.
  const latest = kstDay(now + DAY);
  const oldest = kstDay(now - (USAGE.days + 1) * DAY);
  const days = new Set();
  const rows = [];
  let skipped = 0;
  for (const d of body.days) {
    if (!d || typeof d !== "object" || typeof d.day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(d.day)) refuse("day", "day");
    if (d.day > latest || d.day < oldest || days.has(d.day)) {
      skipped++;
      continue;
    }
    days.add(d.day);
    if (!d.events || typeof d.events !== "object" || Array.isArray(d.events)) refuse("events", "events");
    const recent = d.recent ?? [];
    if (!Array.isArray(recent) || recent.length > Object.keys(EVENTS).length * 2) refuse("recent", "recent");
    const counts = new Map();
    for (const [name, n] of Object.entries(d.events)) {
      if (!Number.isInteger(n) || n < 1 || n > USAGE.count) refuse("count", "events");
      if (Object.hasOwn(EVENTS, name)) counts.set(name, n);
    }
    const lately = new Set(recent.filter((e) => typeof e === "string" && Object.hasOwn(EVENTS, e)));
    for (const e of counts.keys()) lately.add(e);
    for (const e of lately) rows.push([d.day, e, counts.has(e) ? 1 : 0, counts.get(e) ?? 0, 1]);
  }
  const app = body.app;
  if (days.size === 0) return json({ ok: true, skipped });
  // AM 1.28 [FLAG-30] Which flags were on (only phones with stats.more on send it): per day, the list sent with the day or,
  // once for the bundle, flagsOn at the top for its newest day. '*' counts the phones that sent a list at all.
  const listOf = (v) => {
    if (v === undefined || v === null) return null;
    if (!Array.isArray(v) || v.length > 200) refuse("flagsOn", "flagsOn");
    return [...new Set(v.filter((k) => typeof k === "string" && k.length <= 60 && flagDef(k)))];
  };
  const flagDays = new Map();
  const top = listOf(body.flagsOn);
  if (top) flagDays.set([...days].sort().at(-1), top);
  for (const d of body.days) if (days.has(d.day) && d.flagsOn !== undefined) flagDays.set(d.day, listOf(d.flagsOn) ?? []);
  const flagRows = [...flagDays].flatMap(([day, keys]) => [[day, "*"], ...keys.map((k) => [day, k])]);
  const stmts = [...days].map((day) => stmt(env, `INSERT INTO usage_days (day, app, phones) VALUES (?, ?, 1)
    ON CONFLICT (day, app) DO UPDATE SET phones = phones + 1`, day, app));
  // AO 1.28 [PERF-17] Startup times, counted once on the bundle's newest day; a wrong part is left out, the rest counts.
  const startupDay = [...days].sort().at(-1);
  for (const [kind, step, bucket] of startupRows(body.startup)) {
    stmts.push(stmt(env, `INSERT INTO usage_startup (day, app, kind, step, bucket, phones) VALUES (?, ?, ?, ?, ?, 1)
      ON CONFLICT (day, app, kind, step, bucket) DO UPDATE SET phones = phones + 1`, startupDay, app, kind, step, bucket));
  }
  // D1 binds at most 100 values a query: 16 rows of six.
  for (let i = 0; i < rows.length; i += 16) {
    const part = rows.slice(i, i + 16);
    stmts.push(stmt(env, `INSERT INTO usage_events (day, app, event, phones, total, recent) VALUES ${part.map(() => "(?, ?, ?, ?, ?, ?)").join(", ")}
      ON CONFLICT (day, app, event) DO UPDATE SET phones = phones + excluded.phones, total = total + excluded.total, recent = recent + excluded.recent`,
    ...part.flatMap(([day, e, phones, total, recent]) => [day, app, e, phones, total, recent])));
  }
  for (let i = 0; i < flagRows.length; i += 25) {
    const part = flagRows.slice(i, i + 25);
    stmts.push(stmt(env, `INSERT INTO usage_flags (day, app, flag, phones) VALUES ${part.map(() => "(?, ?, ?, 1)").join(", ")}
      ON CONFLICT (day, app, flag) DO UPDATE SET phones = phones + 1`, ...part.flatMap(([day, k]) => [day, app, k])));
  }
  await batch(env, stmts);
  return skipped ? json({ ok: true, skipped }) : new Response(null, { status: 204 });
}

// AO 1.28 시작 ------------------------------------------------------------------------------------------------
/**
 * [PERF-16][PERF-17] The bucket edges (ms) the app rounds a startup time up to (app diag/StartupStats.BUCKETS): only
 * these values are taken, so no phone's exact time is kept. 20000 means over 15 seconds.
 */
export const STARTUP_BUCKETS = [100, 200, 300, 400, 500, 700, 1000, 1500, 2000, 3000, 4000, 6000, 10000, 15000, 20000];
/** What each kind of opening may carry: a cold one from the process start, a warm one from the screen. */
const STARTUP_STEPS = { cold: ["container", "activity", "firstMonth"], warm: ["container", "firstMonth"] };

/**
 * [PERF-17] `startup: {container, activity, firstMonth, warm: {container, firstMonth}}` → [kind, step, bucket] rows.
 * Anything else (a missing part, a number off the edges, an unknown key) is left out without refusing the bundle.
 */
export function startupRows(startup) {
  const rows = [];
  if (!startup || typeof startup !== "object" || Array.isArray(startup)) return rows;
  const take = (kind, part) => {
    if (!part || typeof part !== "object" || Array.isArray(part)) return;
    for (const step of STARTUP_STEPS[kind]) {
      const v = part[step];
      if (Number.isInteger(v) && STARTUP_BUCKETS.includes(v)) rows.push([kind, step, v]);
    }
  };
  take("cold", startup);
  take("warm", startup.warm);
  return rows;
}

/** [PERF-17] The bucket where [share] of the phones are at or under, from {bucket → phones}. */
function bucketQuantile(hist, total, share) {
  let seen = 0;
  for (const b of STARTUP_BUCKETS) {
    seen += hist.get(b) ?? 0;
    if (seen >= total * share) return b;
  }
  return STARTUP_BUCKETS.at(-1);
}

const appOrder = (a, b) => {
  const pa = a.split(".").map(Number);
  const pb = b.split(".").map(Number);
  for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pb[i] - pa[i];
  return 0;
};

/**
 * [PERF-17] 지표 › 앱 시작 시간: per app version (newest first, at most 6) and kind, each step's phones, middle value and
 * 90th percentile (the bucket's upper edge: "at most"). Under 30 phones a version says 데이터 부족 in the admin app.
 */
async function startupMetrics(env, from, to) {
  const rows = await all(env, `SELECT app, kind, step, bucket, SUM(phones) AS phones FROM usage_startup
    WHERE day BETWEEN ? AND ? GROUP BY app, kind, step, bucket`, from, to);
  const byApp = new Map();
  for (const r of rows) {
    const key = `${r.app}|${r.kind}|${r.step}`;
    if (!byApp.has(r.app)) byApp.set(r.app, new Map());
    const hist = byApp.get(r.app);
    if (!hist.has(key)) hist.set(key, new Map());
    hist.get(key).set(r.bucket, (hist.get(key).get(r.bucket) ?? 0) + r.phones);
  }
  const versions = [...byApp.keys()].sort(appOrder).slice(0, 6).map((app) => {
    const out = { app };
    for (const [kind, steps] of Object.entries(STARTUP_STEPS)) {
      const part = {};
      for (const step of steps) {
        const hist = byApp.get(app).get(`${app}|${kind}|${step}`);
        if (!hist) continue;
        const phones = [...hist.values()].reduce((n, v) => n + v, 0);
        part[step] = { phones, p50: bucketQuantile(hist, phones, 0.5), p90: bucketQuantile(hist, phones, 0.9) };
      }
      if (Object.keys(part).length) out[kind] = part;
    }
    return out;
  });
  return { versions, few: 30 };
}
// AO 1.28 끝 --------------------------------------------------------------------------------------------------

// AQ 1.29 시작 ------------------------------------------------------------------------------------------------
/**
 * [PHOTO-09][PHOTO-10] The screens with a '사진이 달라요' button and the reasons it offers, the same codes as the app's
 * core/stats/PhotoWrong.kt (PhotoScreen, PhotoWrongReason). Anything else is refused.
 */
export const PHOTO_SCREENS = {
  roster: "근무표 사진", payslip: "명세서 사진", rules: "회사 규칙 사진", pay_table: "호봉표 사진",
  invest: "투자 캡처", tax: "세금 서류 사진", receipt: "영수증 사진",
};
export const PHOTO_REASONS = {
  numbers: "숫자가 틀려요", missing: "빠진 칸이 있어요", extra: "없는 칸이 생겼어요", names: "이름이나 종류가 틀려요",
  nothing: "거의 읽지 못했어요", other: "다른 점이 있어요",
};
/** Rows a bundle may carry, days a phone keeps them (the app's PhotoWrongBook), presses a row, bundles a network a day. */
const PHOTO_WRONG = { rows: 60, keepDays: 60, count: 99, perNetworkDay: 30, bytes: 8 * 1024 };

/**
 * [PHOTO-10] `{v:1, app:"1.29.0", counts:[{day, screen, reason, n}]}`, sent by the app once 처리방침 3.3 lets it (flag
 * photo.wrong). Added to the day's totals; the same bundle sent again the same day (after a timeout) counts once.
 */
async function photoWrong(request, env) {
  const body = await readJson(request, PHOTO_WRONG.bytes);
  if (body.v !== 1) refuse("version", "v");
  if (typeof body.app !== "string" || !/^\d{1,2}\.\d{1,3}\.\d{1,3}$/.test(body.app)) refuse("app", "app");
  if (!Array.isArray(body.counts) || body.counts.length < 1 || body.counts.length > PHOTO_WRONG.rows) refuse("counts", "counts");
  const now = Date.now();
  const latest = kstDay(now + DAY);
  const oldest = kstDay(now - (PHOTO_WRONG.keepDays + 1) * DAY);
  const rows = new Map();
  for (const c of body.counts) {
    if (!c || typeof c !== "object" || Array.isArray(c)) refuse("count", "counts");
    if (typeof c.day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(c.day) || c.day > latest || c.day < oldest) refuse("day", "day");
    if (typeof c.screen !== "string" || !Object.hasOwn(PHOTO_SCREENS, c.screen)) refuse("screen", "screen");
    if (typeof c.reason !== "string" || !Object.hasOwn(PHOTO_REASONS, c.reason)) refuse("reason", "reason");
    if (!Number.isInteger(c.n) || c.n < 1 || c.n > PHOTO_WRONG.count) refuse("n", "n");
    const key = `${c.day}|${c.screen}|${c.reason}`;
    rows.set(key, Math.min(PHOTO_WRONG.count, (rows.get(key) ?? 0) + c.n));
  }
  const net = await network(request, env, now);
  if (!(await allow(env, `photo-wrong|${net.day}`, PHOTO_WRONG.perNetworkDay, DAY, now))) return json({ error: "rate_limited" }, 429);
  const bundle = (await sha256(`photo-wrong|${kstDay(now)}|${JSON.stringify(body)}`)).slice(0, 32);
  if (!(await allow(env, `bundle|${bundle}`, 1, DAY, now))) return json({ ok: true, again: true });
  const list = [...rows].map(([k, n]) => [...k.split("|"), n]);
  const stmts = [];
  // D1 binds at most 100 values a query: 20 rows of five.
  for (let i = 0; i < list.length; i += 20) {
    const part = list.slice(i, i + 20);
    stmts.push(stmt(env, `INSERT INTO photo_wrong (day, app, screen, reason, total) VALUES ${part.map(() => "(?, ?, ?, ?, ?)").join(", ")}
      ON CONFLICT (day, app, screen, reason) DO UPDATE SET total = total + excluded.total`,
    ...part.flatMap(([day, screen, reason, n]) => [day, body.app, screen, reason, n])));
  }
  await batch(env, stmts);
  return new Response(null, { status: 204 });
}

/** [PHOTO-10] 지표 › 사진이 달라요: totals per day, per screen (with its reasons) and per app version, from..to. */
async function photoWrongMetrics(env, from, to) {
  const rows = await all(env, `SELECT day, app, screen, reason, SUM(total) AS total FROM photo_wrong
    WHERE day BETWEEN ? AND ? GROUP BY day, app, screen, reason`, from, to);
  const days = new Map();
  const screens = new Map();
  const apps = new Map();
  let total = 0;
  for (const r of rows) {
    total += r.total;
    days.set(r.day, (days.get(r.day) ?? 0) + r.total);
    apps.set(r.app, (apps.get(r.app) ?? 0) + r.total);
    if (!screens.has(r.screen)) screens.set(r.screen, { screen: r.screen, label: PHOTO_SCREENS[r.screen] ?? r.screen, total: 0, reasons: {} });
    const s = screens.get(r.screen);
    s.total += r.total;
    s.reasons[r.reason] = (s.reasons[r.reason] ?? 0) + r.total;
  }
  return {
    total,
    days: [...days].sort((a, b) => a[0].localeCompare(b[0])).map(([day, n]) => ({ day, total: n })),
    screens: [...screens.values()].sort((a, b) => b.total - a.total),
    apps: [...apps].sort((a, b) => appOrder(a[0], b[0])).map(([app, n]) => ({ app, total: n })),
    reasons: PHOTO_REASONS,
  };
}
// AQ 1.29 끝 --------------------------------------------------------------------------------------------------

/**
 * [ADMIN-13] The gate numbers from what consenting phones sent over the last [days] full days. A share
 * is phone-days that did it within 28 days over all phone-days: about how many of the phones in use
 * do it, without knowing which phones they are. Under 30 phones a day it says 데이터 부족.
 * [ADMUX-13]~[ADMUX-16] 1.25: any period up to 180 days (from·to), how many phones on which app, the
 * plaza, how fast questions get their first answer, server errors and, with Play's numbers, crashes.
 */
async function metrics(env, url, now) {
  const r = metricRange(url, now);
  const { from, to, days } = r;
  const start = Date.parse(`${from}T00:00:00+09:00`);
  const end = Date.parse(`${to}T00:00:00+09:00`) + DAY;
  const lastWeek = kstDay(Date.parse(`${to}T00:00:00+09:00`) - 6 * DAY);
  const byDay = (col) => `strftime('%Y-%m-%d', (${col} + 32400000) / 1000, 'unixepoch')`;
  const [dayRows, eventRows, subs, beforeRows, versionRows, reportRows, ruleRows, postRows, flagRows, hiddenRows, useRows, rateRows, inquiryRows, errorRows, bugRows, slaRows] = (await batch(env, [
    stmt(env, "SELECT day, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY day ORDER BY day", from, to),
    stmt(env, `SELECT event, SUM(phones) AS phones, SUM(total) AS total, SUM(recent) AS recent FROM usage_events
      WHERE day BETWEEN ? AND ? GROUP BY event`, from, to),
    stmt(env, `SELECT SUM(CASE WHEN trial = 1 THEN 1 ELSE 0 END) AS trials, SUM(CASE WHEN trial = 0 THEN 1 ELSE 0 END) AS paid,
      SUM(CASE WHEN auto_renew = 0 THEN 1 ELSE 0 END) AS ending FROM subs WHERE state IN ('ACTIVE', 'GRACE') AND expiry > ?`, now),
    // [ADMUX-07] The period just before, day by day, for the chart's dashed line.
    stmt(env, "SELECT day, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY day", r.beforeFrom, r.beforeTo),
    // [ADMUX-13] Which app the phones of the period's last seven days run.
    stmt(env, "SELECT app, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY app ORDER BY phones DESC", lastWeek, to),
    // [ADMUX-14] The plaza, day by day.
    stmt(env, `SELECT ${byDay("created_at")} AS day, COUNT(*) AS n, COUNT(DISTINCT device) AS people FROM reports WHERE created_at >= ? AND created_at < ? GROUP BY day`, start, end),
    stmt(env, `SELECT ${byDay("adopted_at")} AS day, COUNT(*) AS n FROM rules WHERE adopted_at >= ? AND adopted_at < ? GROUP BY day`, start, end),
    stmt(env, `SELECT ${byDay("created_at")} AS day, COUNT(*) AS n FROM posts WHERE created_at >= ? AND created_at < ? GROUP BY day`, start, end),
    stmt(env, `SELECT ${byDay("at")} AS day, COUNT(*) AS n FROM post_flags WHERE at >= ? AND at < ? GROUP BY day`, start, end),
    stmt(env, "SELECT COUNT(*) AS n FROM posts WHERE hide_reason = 'flags' AND hidden_at >= ? AND hidden_at < ?", start, end),
    stmt(env, `SELECT ${byDay("at")} AS day, COUNT(*) AS n FROM uses WHERE at >= ? AND at < ? GROUP BY day`, start, end),
    stmt(env, "SELECT COUNT(*) AS n, SUM(CASE WHEN status = 'adopted' THEN 1 ELSE 0 END) AS adopted FROM rates WHERE created_at >= ? AND created_at < ?", start, end),
    // [ADMUX-14] Each question of the period and when it first got an answer.
    stmt(env, `SELECT i.kind, i.created_at, i.rating, (SELECT MIN(m.at) FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin') AS first
      FROM inquiries i WHERE i.created_at >= ? AND i.created_at < ?`, start, end),
    // [ADMUX-16] Requests the worker answered with 500, counted per Korean day.
    stmt(env, "SELECT key, count FROM counters WHERE key BETWEEN ? AND ?", `errors|${from}`, `errors|${to}`),
    stmt(env, `SELECT COALESCE(json_extract(info, '$.app'), '') AS app, COUNT(*) AS n FROM inquiries
      WHERE kind = 'bug' AND created_at >= ? AND created_at < ? AND json_valid(info) GROUP BY app ORDER BY n DESC LIMIT 10`, start, end),
    env.DB.prepare(SLA_SQL),
  ])).map((x) => x.results);
  const was = new Map(beforeRows.map((x) => [x.day, x.phones]));
  const phoneDays = dayRows.reduce((n, x) => n + x.phones, 0);
  const daily = dayRows.length ? Math.round(phoneDays / dayRows.length) : 0;
  const ev = new Map(eventRows.map((x) => [x.event, x]));
  const share = (e) => (phoneDays ? (ev.get(e)?.recent ?? 0) / phoneDays : null);
  const enough = daily >= 30;
  const gate = (name, rule, e, least) => {
    const v = share(e);
    return { name, rule, value: v === null ? null : Math.round(v * 1000) / 10, status: !enough || v === null ? "few" : v * 100 >= least ? "pass" : "fail" };
  };
  const s = subs[0] ?? {};
  const week = dayRows.filter((x) => x.day >= lastWeek);
  const vitals = await playVitals(env, now);
  const vital = (name, rule, key, least) => {
    const v = vitals?.[key];
    if (v === undefined || v === null) return { name, rule, value: null, status: vitals?.error ? "denied" : "play" };
    return { name, rule, value: Math.round(v * 10000) / 100, status: vitals.users < 100 ? "few" : v * 100 < least ? "pass" : "fail" };
  };
  const dayList = [];
  for (let d = start; d < end; d += DAY) dayList.push(kstDay(d));
  const series = (rows) => {
    const m = new Map(rows.map((x) => [x.day, x.n]));
    return dayList.map((day) => m.get(day) ?? 0);
  };
  const total = (rows) => rows.reduce((n, x) => n + x.n, 0);
  const sla = readSla(slaRows);
  return {
    from, to, days, phoneDays, daily, enough,
    series: dayRows.map((x) => ({ day: x.day, phones: x.phones })),
    // The same day [days] earlier for each day of the series, 0 when nothing came that day.
    before: dayRows.map((x) => was.get(kstDay(Date.parse(`${x.day}T00:00:00+09:00`) - days * DAY)) ?? 0),
    events: Object.entries(EVENTS).map(([event, label]) => {
      const e = ev.get(event);
      return { event, label, phones: e?.phones ?? 0, total: e?.total ?? 0, share: share(event) === null ? null : Math.round(share(event) * 1000) / 10 };
    }),
    gates: [
      // [ADMUX-16] Play's own numbers when the service account may read them; until then the row says where to look.
      { gate: "G0", ...vital("비정상 종료", "1.09% 미만(내부 0.5%)", "crash", 1.09) },
      { gate: "G0", ...vital("ANR", "0.47% 미만(내부 0.2%)", "anr", 0.47) },
      { gate: "G0", name: "테스터", rule: "12명이 14일", value: null, status: "play" },
      { gate: "G1", ...gate("급여 화면을 본 사용자", "30% 이상", "pay_view", 30) },
      { gate: "G2", ...gate("근무표 링크를 만든 사용자", "15% 이상", "share_made", 15) },
      { gate: "G2", ...gate("유료 구독 사용자", "2.0% 이상", "pro_sub", 2) },
    ],
    funnel: {
      paywall: ev.get("paywall_view")?.phones ?? 0, trial: ev.get("trial_start")?.total ?? 0, subscribe: ev.get("subscribe")?.total ?? 0,
      trials: s.trials ?? 0, paid: s.paid ?? 0, ending: s.ending ?? 0,
    },
    shares: await shareStats(env, days),
    // [ADMUX-13] Phone-days only (결정 D42): the same phone on seven days counts seven times, so a week is an average.
    active: {
      daily, week: week.length ? Math.round(week.reduce((n, x) => n + x.phones, 0) / week.length) : 0,
      most: dayRows.reduce((m, x) => (x.phones > (m?.phones ?? -1) ? x : m), null),
      versions: appVersions(versionRows),
    },
    dayList,
    plaza: {
      reports: total(reportRows), people: reportRows.reduce((n, x) => n + x.people, 0), adopted: total(ruleRows), posts: total(postRows),
      flags: total(flagRows), hidden: hiddenRows[0]?.n ?? 0, applied: total(useRows), rates: rateRows[0]?.n ?? 0, ratesAdopted: rateRows[0]?.adopted ?? 0,
      series: { reports: series(reportRows), adopted: series(ruleRows), posts: series(postRows), flags: series(flagRows), applied: series(useRows) },
    },
    inquiries: inquiryStats(inquiryRows, sla, dayList, now),
    errors: {
      server: series(errorRows.map((x) => ({ day: x.key.slice(7), n: x.count }))),
      serverTotal: errorRows.reduce((n, x) => n + x.count, 0),
      bugs: bugRows.map((x) => ({ app: x.app || "모름", n: x.n })),
      play: vitals ? { users: vitals.users ?? null, day: vitals.day ?? null, error: vitals.error ?? null } : null,
    },
  };
}

// AA 1.25 시작
/** [ADMUX-13] The days asked: 7 to 90 back from yesterday, or from·to dates (at most 180 days, none after yesterday). */
export function metricRange(url, now) {
  const yesterday = kstDay(now - DAY);
  const dateOf = (k) => {
    const v = url.searchParams.get(k);
    return v && /^\d{4}-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(`${v}T00:00:00+09:00`)) ? v : null;
  };
  let from = dateOf("from");
  let to = dateOf("to");
  let days;
  if (from && to) {
    if (to > yesterday) to = yesterday;
    if (from > to) from = to;
    days = Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY) + 1;
    if (days > METRIC_MAX_DAYS) {
      days = METRIC_MAX_DAYS;
      from = kstDay(Date.parse(`${to}T00:00:00+09:00`) - (days - 1) * DAY);
    }
  } else {
    days = Math.min(90, Math.max(7, Number.parseInt(url.searchParams.get("days") ?? "28", 10) || 28));
    to = yesterday;
    from = kstDay(now - days * DAY);
  }
  const first = Date.parse(`${from}T00:00:00+09:00`);
  return { from, to, days, beforeFrom: kstDay(first - days * DAY), beforeTo: kstDay(first - DAY) };
}

const METRIC_MAX_DAYS = 180;

/**
 * [ADMUX-15] Each app version's code, for "can this phone take that remote file" (minApp is a code). The
 * phones send only the name (usage stats), so the codes of releases are listed here and ones questions
 * carried ("1.24.0 (35)") are learned on top.
 */
const APP_CODES = { "1.17.2": 28, "1.18.0": 29, "1.19.0": 30, "1.20.0": 31, "1.21.0": 32, "1.22.0": 33, "1.23.0": 34, "1.24.0": 35, "1.25.0": 36, "1.26.0": 37, "1.27.0": 38, "1.28.0": 39 };

function appVersions(rows) {
  const all = rows.reduce((n, x) => n + x.phones, 0);
  return rows.map((x) => ({ app: x.app, code: APP_CODES[x.app] ?? null, phones: x.phones, share: all ? Math.round((1000 * x.phones) / all) / 10 : 0 }));
}

/** The value at [q] (0..1) of sorted [list], or null for none. */
const quantile = (list, q) => (list.length ? list[Math.min(list.length - 1, Math.max(0, Math.ceil(q * list.length) - 1))] : null);

/** [ADMUX-14] How the period's questions were answered: counts, first-answer minutes, kept targets, the users' word. */
export function inquiryStats(rows, sla, dayList, now) {
  const waits = rows.filter((x) => x.first !== null).map((x) => Math.round((x.first - x.created_at) / 60000)).sort((a, b) => a - b);
  // A question still unanswered counts against the target once its time is up.
  const due = rows.filter((x) => x.first !== null || now - x.created_at > sla[slaKey(x.kind)] * HOUR);
  const kept = due.filter((x) => x.first !== null && x.first - x.created_at <= sla[slaKey(x.kind)] * HOUR).length;
  const good = rows.filter((x) => x.rating === 1).length;
  const bad = rows.filter((x) => x.rating === -1).length;
  const kinds = new Map();
  for (const x of rows) {
    const k = kinds.get(x.kind) ?? { kind: x.kind, n: 0, answered: 0, waits: [] };
    k.n++;
    if (x.first !== null) {
      k.answered++;
      k.waits.push(Math.round((x.first - x.created_at) / 60000));
    }
    kinds.set(x.kind, k);
  }
  const per = (pick) => {
    const m = new Map();
    for (const x of rows) {
      const at = pick(x);
      if (at === null) continue;
      const d = kstDay(at);
      m.set(d, (m.get(d) ?? 0) + 1);
    }
    return dayList.map((d) => m.get(d) ?? 0);
  };
  return {
    received: rows.length, answered: waits.length, median: quantile(waits, 0.5), p90: quantile(waits, 0.9),
    kept: due.length ? Math.round((1000 * kept) / due.length) / 10 : null, due: due.length,
    good, bad, rated: good + bad ? Math.round((1000 * good) / (good + bad)) / 10 : null,
    kinds: [...kinds.values()].sort((a, b) => b.n - a.n).map((k) => ({ kind: k.kind, n: k.n, answered: k.answered,
      median: quantile(k.waits.sort((a, b) => a - b), 0.5), target: sla[slaKey(k.kind)] })),
    series: { received: per((x) => x.created_at), answered: per((x) => x.first) },
  };
}

/** [ADMUX-16] One more request the worker could not answer (500) on today's Korean day; kept 400 days. */
async function countError(env) {
  const now = Date.now();
  await env.DB.prepare(`INSERT INTO counters (key, count, expires) VALUES (?1, 1, ?2)
    ON CONFLICT (key) DO UPDATE SET count = count + 1`).bind(`errors|${kstDay(now)}`, now + 400 * DAY).run();
}

/**
 * [ADMUX-16] Play's user-perceived crash and ANR rates over 28 days (Play Developer Reporting API), with the
 * same service account as the purchase checks once Play Console gives it "앱 정보 보기". Asked at most every
 * six hours; Play's daily numbers come a day or two late. Null when no account is set.
 */
const VITALS = { scope: "https://www.googleapis.com/auth/playdeveloperreporting", api: "https://playdeveloperreporting.googleapis.com/v1beta1/apps",
  keepMs: 6 * HOUR };
const vitalsKept = new WeakMap();

export async function playVitals(env, now) {
  const account = playReady(env);
  if (!account) return null;
  const kept = vitalsKept.get(env.DB ?? env);
  if (kept && kept.until > now) return kept.value;
  const pkg = env.PLAY_PACKAGE || "com.bamnat.app";
  // Daily rows are Los Angeles days (the only zone Play takes); ten days back is enough for the latest one.
  const la = (ms) => {
    const [y, m, d] = new Date(ms - 8 * HOUR).toISOString().slice(0, 10).split("-").map(Number);
    return { year: y, month: m, day: d, timeZone: { id: "America/Los_Angeles" } };
  };
  const ask = async (token, set, metric) => {
    const res = await fetch(`${VITALS.api}/${encodeURIComponent(pkg)}/${set}:query`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ timelineSpec: { aggregationPeriod: "DAILY", startTime: la(now - 10 * DAY), endTime: la(now) },
        metrics: [metric, "distinctUsers"] }),
      signal: AbortSignal.timeout(SHARE_TIMEOUT_MS),
    });
    if (!res.ok) throw new Refuse(res.status === 403 ? "play_denied" : "play", "play", 502);
    const rows = (await res.json()).rows ?? [];
    const last = rows.at(-1);
    if (!last) return { rate: null, users: 0, day: null };
    const val = (name) => Number(last.metrics?.find((x) => x.metric === name)?.decimalValue?.value);
    const t = last.startTime ?? {};
    return { rate: Number.isFinite(val(metric)) ? val(metric) : null, users: Number.isFinite(val("distinctUsers")) ? val("distinctUsers") : 0,
      day: t.year ? `${t.year}-${String(t.month).padStart(2, "0")}-${String(t.day).padStart(2, "0")}` : null };
  };
  let value;
  try {
    const token = await playToken(env, account, now, VITALS.scope);
    const [crash, anr] = await Promise.all([ask(token, "crashRateMetricSet", "userPerceivedCrashRate28dUserWeighted"),
      ask(token, "anrRateMetricSet", "userPerceivedAnrRate28dUserWeighted")]);
    value = { crash: crash.rate, anr: anr.rate, users: crash.users, day: crash.day };
  } catch (e) {
    value = { error: e?.code === "play_denied" ? "denied" : "unreachable" };
  }
  vitalsKept.set(env.DB ?? env, { value, until: now + (value.error ? HOUR : VITALS.keepMs) });
  return value;
}

/**
 * [ADMUX-15] 원격 갱신 배포 현황: each signed file on the site the app reads (remote/v1 on GitHub Pages, or
 * REMOTE_BASE_URL), whether its Ed25519 signature checks out with the keys the app trusts, and how many of
 * the phones of the last seven days (usage stats) run an app new enough (minApp) to take it. The app never
 * says which version it uses (결정 AA-D2), so this is what can reach phones, not what has.
 */
export const REMOTE = {
  kinds: ["presets", "rates", "wages", "taxtable", "publicpay", "holidays", "taxes", "alarm-guide", "cycles"],
  base: "https://ywjmoney.github.io/-/remote/v1",
  // The same keys as core RemoteKeys.TRUSTED (a test reads the Kotlin file). [REMOTE-08] The last two are the
  // 2026-10-26 key and its spare (결정 D83); their signatures are in <kind>.json.sig2.
  keys: ["6e6J4SWfNWAY8142S4+UDOjBJz+4/njmfjxATwAl55I=", "fzoA27EsvTUDa4EA/xtL/z4039vz1TSwmTr48lksmXQ=",
    "DjM9UzLI9MlnEWqpweAItpsvGvzNsFo8FNcai5M/NPY=", "ey5slM4bow7nLuljDW6ZCc2pVVIUN0o5dFkWsBQUc/E="],
  keepMs: 10 * 60 * 1000,
};
const remoteKept = new WeakMap();

const unb64 = (text) => Uint8Array.from(atob(text.trim()), (c) => c.charCodeAt(0));

async function signedBy(bytes, signature) {
  let sig;
  try {
    sig = unb64(signature);
  } catch {
    return false;
  }
  for (const k of REMOTE.keys) {
    try {
      const key = await crypto.subtle.importKey("raw", unb64(k), { name: "Ed25519" }, false, ["verify"]);
      if (await crypto.subtle.verify({ name: "Ed25519" }, key, sig, bytes)) return true;
    } catch {
      // A runtime without Ed25519 cannot say; the file shows as not checked.
    }
  }
  return false;
}

async function remoteState(env, now, fresh = false) {
  const kept = remoteKept.get(env.DB ?? env);
  if (!fresh && kept && kept.until > now) return kept.value;
  const base = (typeof env.REMOTE_BASE_URL === "string" && /^https:\/\/[^\s]+$/.test(env.REMOTE_BASE_URL.trim())
    ? env.REMOTE_BASE_URL.trim() : REMOTE.base).replace(/\/+$/, "");
  const get = async (path) => {
    try {
      const res = await fetch(`${base}/${path}`, { signal: AbortSignal.timeout(SHARE_TIMEOUT_MS), headers: { "cache-control": "no-cache" } });
      return res.ok ? new Uint8Array(await res.arrayBuffer()) : null;
    } catch {
      return null;
    }
  };
  const week = await all(env, "SELECT app, SUM(phones) AS phones FROM usage_days WHERE day BETWEEN ? AND ? GROUP BY app",
    kstDay(now - 7 * DAY), kstDay(now - DAY));
  // Codes the questions carried ("1.24.0 (35)"), on top of the list of releases.
  const learned = await all(env, `SELECT DISTINCT json_extract(info, '$.app') AS app FROM inquiries
    WHERE json_valid(info) AND created_at > ? LIMIT 200`, now - 90 * DAY);
  const codes = { ...APP_CODES };
  for (const { app } of learned) {
    const m = /^(\d{1,2}\.\d{1,3}\.\d{1,3}) \((\d{1,6})\)$/.exec(app ?? "");
    if (m) codes[m[1]] = Number(m[2]);
  }
  const phones = week.reduce((n, x) => n + x.phones, 0);
  const files = await Promise.all(REMOTE.kinds.map(async (kind) => {
    // [REMOTE-08] .sig2 (1.27 key) first, the old .sig for apps up to 1.26 second.
    const [bytes, sig2, sig1] = await Promise.all([get(`${kind}.json`), get(`${kind}.json.sig2`), get(`${kind}.json.sig`)]);
    if (!bytes) return { kind, found: false };
    let f = null;
    try {
      f = JSON.parse(new TextDecoder().decode(bytes));
    } catch {
      return { kind, found: true, readable: false };
    }
    const minApp = Number.isInteger(f.minApp) ? f.minApp : null;
    const able = week.filter((x) => codes[x.app] !== undefined && (minApp === null || codes[x.app] >= minApp)).reduce((n, x) => n + x.phones, 0);
    const unknown = week.filter((x) => codes[x.app] === undefined).reduce((n, x) => n + x.phones, 0);
    return {
      kind, found: true, readable: f.kind === kind && f.format === 1, version: f.version ?? null, issued: f.issued ?? null, minApp,
      source: typeof f.source === "string" ? f.source.slice(0, 200) : "", signed: Boolean(sig2 || sig1),
      valid: (sig2 ? await signedBy(bytes, new TextDecoder().decode(sig2)) : false) || (sig1 ? await signedBy(bytes, new TextDecoder().decode(sig1)) : false),
      oldApps: sig1 ? await signedBy(bytes, new TextDecoder().decode(sig1)) : false,
      reach: phones ? Math.round((1000 * able) / phones) / 10 : null, unknown,
    };
  }));
  const value = { base, at: iso(now), phones, files };
  remoteKept.set(env.DB ?? env, { value, until: now + REMOTE.keepMs });
  return value;
}

/** [ADMUX-19] Up to 100 posts (show·hide·delete) or 임금표 entries (adopted·hidden·pending) in one go. */
async function plazaBulk(request, env, now, what) {
  const body = await readJson(request, 8 * 1024);
  const pattern = what === "posts" ? /^p[A-Za-z0-9]{15}$/ : /^[A-Za-z0-9]{22}$/;
  if (!Array.isArray(body.ids) || !body.ids.length || body.ids.length > DEV.bulk || body.ids.some((x) => typeof x !== "string" || !pattern.test(x))) {
    refuse("ids", "ids");
  }
  const ids = [...new Set(body.ids)];
  let action;
  if (what === "posts") {
    action = oneOf(body.action, ["show", "hide", "delete"], "action");
    if (action === "delete" && !can(env.actor, "community", "edit")) return json({ error: "no_permission", area: "community" }, 403);
  } else {
    action = oneOf(body.action, ["adopted", "hidden", "pending"], "action");
  }
  const done = [];
  const skipped = [];
  for (const id of ids) {
    const out = what === "posts" ? await moderatePost(env, id, { action, reason: body.reason }, now) : await rateTo(env, env.actor, id, action, now);
    (out.error ? skipped : done).push(out.error ? { id, error: out.error } : id);
  }
  await audit(env, now, `${what === "posts" ? "post" : "rate"}_bulk`, `${done.length}개`, `${action}${skipped.length ? ` (${skipped.length} 건너뜀)` : ""}`);
  return json({ done, skipped });
}

/** [ADMUX-18] Wilson score lower bound (95%) of [ok] among [n], as a percent: a share that does not jump on two votes. */
export function wilson(ok, n) {
  if (!n) return 0;
  const z = 1.96;
  const p = ok / n;
  const lower = (p + (z * z) / (2 * n) - z * Math.sqrt((p * (1 - p) + (z * z) / (4 * n)) / n)) / (1 + (z * z) / n);
  return Math.round(1000 * Math.max(0, lower)) / 10;
}

/**
 * [ADMUX-18] 같은 회사 여러 판: per company and department, the rule in use, the setups waiting to replace it
 * and the earlier versions, side by side with how many phones took each (uses), 맞았어요·달라요, trust, the
 * Wilson share and what differs from the rule in use. show "conflict" (default): two or more live ones;
 * "history": also departments whose only others are earlier versions.
 */
async function versions(env, show) {
  const [ruleRows, groupRows, reports, votes, useRows, postRows, companyRows, settingRows] = (await batch(env, [
    env.DB.prepare("SELECT id, company_id, dept, dept_norm, grp, version, setup, source, adopted_at, withdrawn, superseded FROM rules WHERE withdrawn = 0"),
    env.DB.prepare(`SELECT company_id, grp, dept, dept_norm, setup, verified, created_at FROM reports WHERE status = 'pending'
      ORDER BY verified DESC, created_at DESC`),
    env.DB.prepare("SELECT company_id, grp, device, points, cluster, verified FROM reports WHERE status IN ('pending', 'adopted')"),
    env.DB.prepare("SELECT company_id, grp, device, ok, verified, cluster FROM votes"),
    env.DB.prepare("SELECT company_id, grp, COUNT(*) AS n FROM uses GROUP BY company_id, grp"),
    env.DB.prepare("SELECT company_id, grp, COUNT(*) AS n FROM posts WHERE status = 'shown' GROUP BY company_id, grp"),
    env.DB.prepare("SELECT id, name FROM companies"),
    env.DB.prepare(`SELECT key, value FROM settings WHERE key IN (${SETTING_KEYS})`),
  ])).map((r) => r.results);
  const { adoptPoints } = readSettings(settingRows);
  const scores = tallies(reports, votes);
  const used = new Map(useRows.map((u) => [`${u.company_id}/${u.grp}`, u.n]));
  // [PLAZA-05] Pay-slip-checked reports of every 판, the rule in use too, as the plaza's own 지지 counts them.
  const checked = new Map();
  for (const r of reports) if (r.verified === 1) checked.set(`${r.company_id}/${r.grp}`, (checked.get(`${r.company_id}/${r.grp}`) ?? 0) + 1);
  const talk = new Map(postRows.map((u) => [`${u.company_id}/${u.grp}`, u.n]));
  const names = new Map(companyRows.map((c) => [c.id, c.name]));
  const depts = new Map();
  const at = (company, norm, dept) => {
    const key = `${company}/${norm}`;
    if (!depts.has(key)) depts.set(key, { company: { id: company, name: names.get(company) ?? "" }, dept, list: [] });
    return depts.get(key);
  };
  const card = (company, grp, more) => {
    const sc = scores.get(`${company}/${grp}`) ?? NO_SCORE;
    return { setup: grp, uses: used.get(`${company}/${grp}`) ?? 0, ok: sc.persons, bad: sc.bad, points: sc.points, ...trust(sc, adoptPoints),
      wilson: wilson(sc.persons, sc.persons + sc.bad), posts: talk.get(`${company}/${grp}`) ?? 0, verified: checked.get(`${company}/${grp}`) ?? 0, ...more };
  };
  for (const r of ruleRows) {
    at(r.company_id, r.dept_norm, r.dept).list.push(card(r.company_id, r.grp, {
      state: r.superseded ? "previous" : "active", rule: r.id, version: r.version, source: r.source, at: iso(r.adopted_at), raw: r.setup }));
  }
  const seen = new Set();
  for (const g of groupRows) {
    const key = `${g.company_id}/${g.grp}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const reportsOf = groupRows.filter((x) => x.company_id === g.company_id && x.grp === g.grp);
    at(g.company_id, g.dept_norm, g.dept).list.push(card(g.company_id, g.grp, {
      state: "pending", reports: reportsOf.length,
      at: iso(Math.min(...reportsOf.map((x) => x.created_at))), raw: g.setup }));
  }
  const out = [];
  let conflicts = 0;
  for (const d of depts.values()) {
    const live = d.list.filter((c) => c.state !== "previous");
    const conflict = live.length >= 2;
    if (conflict) conflicts++;
    if (d.list.length < 2 || (!conflict && show !== "history")) continue;
    const active = d.list.find((c) => c.state === "active");
    const base = active ? describe(JSON.parse(active.raw)) : null;
    // AX 1.30 [ADMX-02][ADMX-03] The rule in use is compared with the version before it (1.29 showed nothing on it and the
    // difference backwards on that earlier one); a waiting setup with the rule in use; an earlier version says what
    // 다시 대표로 would change. Each card has its facts for the 비교 표.
    const prior = d.list.filter((c) => c.state === "previous").sort((a, b) => b.version - a.version)[0] ?? null;
    // The one most people took and trust: phones that use it, people who sent or backed it, over the Wilson share.
    const score = support;
    const best = live.length ? live.reduce((a, b) => (score(b) > score(a) ? b : a)) : null;
    const order = { active: 0, pending: 1, previous: 2 };
    out.push({
      company: d.company, dept: d.dept, conflict,
      candidates: d.list.sort((a, b) => order[a.state] - order[b.state] || (b.version ?? 0) - (a.version ?? 0) || b.at.localeCompare(a.at)).map((c) => {
        const lines = describe(JSON.parse(c.raw));
        const { raw, ...rest } = c;
        const from = c === active ? prior : active;
        const changes = !from ? null : c === active ? diffLines(describe(JSON.parse(prior.raw)), lines) : diffLines(base, lines);
        return { ...rest, lines, facts: factsOf(lines), changes, ...(from ? { against: { state: from.state, version: from.version ?? null } } : {}),
          recommended: Boolean(conflict && c === best) };
      }),
    });
  }
  out.sort((a, b) => b.conflict - a.conflict || a.company.name.localeCompare(b.company.name));
  return { conflicts, show: show === "history" ? "history" : "conflict", items: out };
}

/**
 * [ADMUX-18] An earlier version back as the department's rule: a new version with its setup (source
 * "promote"), the rule in use becomes an earlier one. Nothing is deleted; phones on the replaced one hear
 * of the new version through 광장 소식 like any other.
 */
async function promote(env, id, now) {
  if (!can(env.actor, "takedown", "edit")) return json({ error: "no_permission", area: "takedown" }, 403);
  const r = await one(env, "SELECT * FROM rules WHERE id = ?", id);
  if (!r) return json({ error: "not_found" }, 404);
  if (!r.superseded || r.withdrawn) return json({ error: "not_previous" }, 409);
  const last = await one(env, "SELECT MAX(version) AS v FROM rules WHERE company_id = ? AND dept_norm = ?", r.company_id, r.dept_norm);
  const ruleId = randomId(16);
  await batch(env, [
    stmt(env, "UPDATE rules SET superseded = 1 WHERE company_id = ? AND dept_norm = ? AND superseded = 0", r.company_id, r.dept_norm),
    stmt(env, `INSERT INTO rules (id, company_id, dept, dept_norm, grp, version, setup, source, adopted_at, withdrawn, superseded)
      VALUES (?, ?, ?, ?, ?, ?, ?, 'promote', ?, 0, 0)`, ruleId, r.company_id, r.dept, r.dept_norm, r.grp, (last?.v ?? 0) + 1, r.setup, now),
  ]);
  await audit(env, now, "rule_promote", ruleId, `v${r.version} → v${(last?.v ?? 0) + 1}`);
  return companyInfo(env, r.company_id);
}

/**
 * [ADMUX-17] 할 일 받은함: every thing waiting on this admin's areas in one list, the nearest deadline
 * first, then the oldest. A deadline is the question's answer target, 30 days to look at a post the flags
 * hid (운영 규칙), a week for a setup or 임금표 entry to be decided, the month's end for its chores.
 */
function inboxQueries(actor, now) {
  const q = {};
  if (can(actor, "inquiries")) {
    q.inquiries = `SELECT i.id, i.kind, i.updated_at, i.assignee, i.assignee_name, i.same,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first,
        (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last
      FROM inquiries i WHERE i.status = 'open' ORDER BY i.updated_at LIMIT 300`;
    q.sla = SLA_SQL;
  }
  if (can(actor, "review")) {
    q.groups = `SELECT p.company_id, p.grp, c.name, MIN(p.dept) AS dept, COUNT(*) AS n, MIN(p.created_at) AS since
      FROM reports p JOIN companies c ON c.id = p.company_id WHERE p.status = 'pending' GROUP BY p.company_id, p.grp ORDER BY since LIMIT 100`;
    q.asks = "SELECT p.id, p.dept, p.created_at, c.name FROM reports p JOIN companies c ON c.id = p.company_id WHERE p.status = 'ask' ORDER BY p.created_at LIMIT 50";
    q.doubted = `SELECT r.id, r.dept, r.version, c.name, MAX(v.at) AS at, COUNT(*) AS n FROM rules r JOIN companies c ON c.id = r.company_id
      JOIN votes v ON v.company_id = r.company_id AND v.grp = r.grp AND v.ok = 0 WHERE r.withdrawn = 0 AND r.superseded = 0 GROUP BY r.id LIMIT 50`;
  }
  if (can(actor, "community")) {
    q.posts = `SELECT p.id, p.body, p.flags, p.status, p.hide_reason, p.hidden_at, p.created_at, c.name,
        (SELECT MIN(f.at) FROM post_flags f WHERE f.post_id = p.id) AS flagged
      FROM posts p JOIN companies c ON c.id = p.company_id WHERE p.kept = 0 AND (p.flags > 0 OR p.hide_reason = 'flags') LIMIT 100`;
    q.rates = "SELECT id, trade, amount, unit, created_at FROM rates WHERE status = 'pending' ORDER BY created_at LIMIT 50";
  }
  if (can(actor, "dev")) {
    q.dev = "SELECT id, title, status, assignee, created_at FROM dev_items WHERE deleted_at IS NULL AND status = 'todo' ORDER BY created_at LIMIT 50";
  }
  if (can(actor, "rewards")) q.owed = "SELECT COUNT(*) AS n, MIN(created_at) AS since FROM rewards WHERE status = 'owed'";
  if (can(actor, "ops")) q.ops = ["SELECT value FROM settings WHERE key = ?", `ops:${kstDay(now).slice(0, 7)}`];
  if (actor.owner) {
    q.admins = "SELECT id, name, email, created_at FROM admins WHERE status = 'pending'";
    q.phones = "SELECT id, admin_name, created_at FROM admin_devices WHERE status = 'pending'";
  }
  return q;
}

/** [ADMUX-17] The list from what [inboxQueries] read (inside /today's one trip). */
function inboxItems(got, actor, now) {
  const items = [];
  const add = (item) => items.push({ due: null, ...item, at: iso(item.at), ...(item.due ? { due: iso(item.due) } : {}) });
  const short = (t) => [...String(t ?? "")].slice(0, 80).join("");
  if (got.inquiries) {
    const sla = readSla(got.sla);
    for (const r of got.inquiries) {
      if (r.last !== "user") continue;
      add({ type: "inquiry", id: r.id, kind: r.kind, title: short(r.first) || "(내용 없음)", at: r.updated_at, due: r.updated_at + sla[slaKey(r.kind)] * HOUR,
        same: r.same ?? 0, mine: r.assignee === actor.id, assignee: r.assignee ? { id: r.assignee, name: r.assignee_name ?? "" } : null });
    }
  }
  for (const g of got.groups ?? []) {
    add({ type: "group", id: `${g.company_id}.${g.grp}`, title: `${g.name}${g.dept ? ` ${g.dept}` : ""}`, n: g.n, at: g.since, due: g.since + INBOX.decideDays * DAY });
  }
  for (const a of got.asks ?? []) add({ type: "ask", id: a.id, title: `${a.name}${a.dept ? ` ${a.dept}` : ""}`, at: a.created_at, due: a.created_at + INBOX.decideDays * DAY });
  for (const d of got.doubted ?? []) add({ type: "doubted", id: d.id, title: `${d.name}${d.dept ? ` ${d.dept}` : ""} v${d.version}`, n: d.n, at: d.at });
  for (const p of got.posts ?? []) {
    const hidden = p.status === "hidden" && p.hide_reason === "flags";
    add({ type: hidden ? "hidden_post" : "flagged_post", id: p.id, title: short(p.body), company: p.name, n: p.flags,
      at: hidden ? p.hidden_at : p.flagged ?? p.created_at, due: hidden ? p.hidden_at + LIMITS.hiddenReviewDays * DAY : null });
  }
  for (const r of got.rates ?? []) add({ type: "rate", id: r.id, title: `${r.trade} ${r.amount.toLocaleString("ko-KR")}원`, at: r.created_at, due: r.created_at + INBOX.decideDays * DAY });
  for (const d of got.dev ?? []) add({ type: "dev", id: d.id, title: short(d.title), at: d.created_at, mine: d.assignee === actor.id });
  if (got.owed?.[0]?.n) add({ type: "rewards", id: "owed", title: "드릴 보상", n: got.owed[0].n, at: got.owed[0].since });
  if (got.ops) {
    const month = kstDay(now).slice(0, 7);
    const [y, m] = month.split("-").map(Number);
    const monthEnd = Date.parse(`${m === 12 ? y + 1 : y}-${String(m === 12 ? 1 : m + 1).padStart(2, "0")}-01T00:00:00+09:00`);
    for (const i of opsItems(month, got.ops[0]?.value)) {
      if (!i.done) add({ type: "ops", id: i.key, title: i.text, at: Date.parse(`${month}-01T00:00:00+09:00`), due: monthEnd });
    }
  }
  for (const a of got.admins ?? []) add({ type: "admin", id: a.id, title: `${a.name} (${maskEmail(a.email)})`, at: a.created_at });
  for (const p of got.phones ?? []) add({ type: "phone", id: p.id, title: `${p.admin_name}의 휴대폰`, at: p.created_at });
  items.sort((a, b) => (a.due && b.due ? a.due.localeCompare(b.due) : a.due ? -1 : b.due ? 1 : a.at.localeCompare(b.at)));
  const today = Date.parse(`${kstDay(now)}T00:00:00+09:00`) + DAY;
  const counts = { today: 0, week: 0, all: items.length, late: 0, mine: items.filter((i) => i.mine).length };
  for (const i of items) {
    const due = i.due ? Date.parse(i.due) : null;
    if (due !== null && due < now) counts.late++;
    if (due !== null && due < today) counts.today++;
    if (due !== null && due < today + 6 * DAY) counts.week++;
  }
  return { now: iso(now), counts, items: items.slice(0, 200) };
}

const INBOX = { decideDays: 7 };
// AA 1.25 끝

// [COMM-09] 이 사람 글 안 보기 without a list on the server. Each post carries its writer sealed with a key
// only this server has (AES-GCM, a fresh nonce each time), so the marks of two posts never match and
// reading them ties nothing together. The phone keeps the marks it chose and sends them back (X-Hide);
// the server opens them and leaves those writers out.

const HIDE = { max: 50, token: /^[A-Za-z0-9_-]{64}$/ };
const authorKeys = new Map();

async function sealKey(secret) {
  let key = authorKeys.get(secret);
  if (!key) {
    const raw = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`author|${secret}`));
    key = await crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
    authorKeys.set(secret, key);
  }
  return key;
}

/**
 * [SRV-08] The keys for 이 사람 글 안 보기: the first seals, all of them open. With its own secret
 * AUTHOR_KEY, changing RATE_SALT after a leak no longer empties every phone's list; the key it replaced
 * (AUTHOR_KEY_OLD, or the salt before AUTHOR_KEY was set) still opens the marks phones already keep.
 */
async function authorKey(env) {
  const own = typeof env.AUTHOR_KEY === "string" && env.AUTHOR_KEY.length >= 16 ? env.AUTHOR_KEY : null;
  const old = typeof env.AUTHOR_KEY_OLD === "string" && env.AUTHOR_KEY_OLD.length >= 16 ? env.AUTHOR_KEY_OLD : null;
  const secrets = own ? [own, old ?? await salt(env)] : [await salt(env)];
  const keys = await Promise.all(secrets.map(sealKey));
  return { seal: keys[0], open: keys };
}

const unb64url = (text) => Uint8Array.from(atob(text.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));

async function authorToken(key, device) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const plain = Uint8Array.from(device.match(/../g), (h) => Number.parseInt(h, 16));
  const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key.seal, plain));
  return b64url(new Uint8Array([...iv, ...sealed]));
}

/** The writers behind the marks in [header]; a mark this server did not make is skipped. */
async function hiddenAuthors(key, header) {
  const out = new Set();
  const marks = String(header ?? "").split(",").map((m) => m.trim()).filter((m) => HIDE.token.test(m)).slice(0, HIDE.max);
  for (const m of marks) {
    const bytes = unb64url(m);
    for (const k of key.open) {
      try {
        const plain = new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: bytes.slice(0, 12) }, k, bytes.slice(12)));
        out.add([...plain].map((b) => b.toString(16).padStart(2, "0")).join(""));
        break;
      } catch {
        // Sealed with another key, or not a mark at all.
      }
    }
  }
  return out;
}

/**
 * [COMM-10] 광장 소식 for one phone since [since]: a rule set it uses was replaced by a newer version, and
 * what it sent (a rule set, a proposal, an 임금표 entry) was adopted. Asked once a day; nothing is kept.
 */
async function plazaNews(request, env, url) {
  const dev = await installOf(request);
  const now = Date.now();
  if (await paused(env, "PLAZA_NEWS")) return json({ items: [], now: iso(now), paused: true });
  return json({ items: await newsItems(env, dev, newsSince(url, now), now), now: iso(now) });
}

const newsSince = (url, now) => {
  const asked = Date.parse(url.searchParams.get("since") ?? "");
  return Math.max(Number.isFinite(asked) ? asked : now - DAY, now - 14 * DAY);
};

/**
 * [SRV-10] N3 밤낮 소식 in one ask a day: the plaza news, new answers to this phone's questions and the
 * notices that began since, so the app shows one notification for all of it. The same one install id as
 * the plaza news; links about to end are known on the phone and never asked here.
 */
async function digest(request, env, url) {
  const dev = await installOf(request);
  const now = Date.now();
  const since = newsSince(url, now);
  const [plaza, answers, ops] = await Promise.all([
    paused(env, "PLAZA_NEWS").then((off) => (off ? null : newsItems(env, dev, since, now))),
    all(env, `SELECT m.inquiry_id AS id, COUNT(*) AS n, MAX(m.at) AS at FROM inquiry_messages m JOIN inquiries i ON i.id = m.inquiry_id
      WHERE i.device = ? AND m.sender = 'admin' AND m.at > ? AND m.at <= ? GROUP BY m.inquiry_id ORDER BY at DESC LIMIT 10`, dev, since, now),
    opsState(env),
  ]);
  return json({
    plaza: plaza ?? [],
    ...(plaza ? {} : { plazaPaused: true }),
    answers: answers.map((a) => ({ inquiry: a.id, count: a.n, at: iso(a.at) })),
    notices: ops.notices.filter((n) => n.from > since && n.from <= now && now < n.until).map(noticeView),
    now: iso(now),
  });
}

async function newsItems(env, dev, since, now) {
  const [changed, adopted, rates, results, rooms, roomPosts] = (await batch(env, [
    stmt(env, `SELECT n.company_id, c.name, n.dept, n.version, n.adopted_at AS at FROM uses u
        JOIN rules o ON o.company_id = u.company_id AND o.grp = u.grp AND o.superseded = 1
        JOIN rules n ON n.company_id = o.company_id AND n.dept_norm = o.dept_norm AND n.superseded = 0 AND n.withdrawn = 0 AND n.adopted_at > ?
        JOIN companies c ON c.id = n.company_id
      WHERE u.device = ? AND NOT EXISTS (SELECT 1 FROM uses x WHERE x.device = u.device AND x.company_id = n.company_id AND x.grp = n.grp)
      GROUP BY n.id ORDER BY n.adopted_at DESC LIMIT 10`, since, dev),
    stmt(env, `SELECT r.company_id, c.name, r.dept, r.post_id, u.version, r.decided_at AS at FROM reports r
        JOIN companies c ON c.id = r.company_id LEFT JOIN rules u ON u.id = r.rule_id
      WHERE r.device = ? AND r.status = 'adopted' AND r.decided_at > ? ORDER BY r.decided_at DESC LIMIT 10`, dev, since),
    stmt(env, "SELECT trade, region, decided_at AS at FROM rates WHERE device = ? AND status = 'adopted' AND decided_at > ? ORDER BY decided_at DESC LIMIT 10",
      dev, since),
    // [COMM-11] What became of posts this phone flagged or wrote, and the end of its 쓰기 막기.
    stmt(env, `SELECT x.kind, x.result, x.post_id, x.company_id, c.name, x.at FROM plaza_results x LEFT JOIN companies c ON c.id = x.company_id
      WHERE x.device = ? AND x.at > ? AND x.at <= ? ORDER BY x.at DESC LIMIT 20`, dev, since, now),
    roomNews(env, dev, since),
    roomPostNews(env, dev, since),
  ])).map((r) => r.results);
  const items = [
    ...changed.map((r) => ({ kind: "changed", company: { id: r.company_id, name: r.name }, dept: r.dept, version: r.version, at: iso(r.at) })),
    ...adopted.map((r) => ({ kind: r.post_id ? "proposal" : "adopted", company: { id: r.company_id, name: r.name }, dept: r.dept,
      version: r.version ?? null, at: iso(r.at) })),
    ...rates.map((r) => ({ kind: "rate", trade: r.trade, region: r.region, at: iso(r.at) })),
    ...results.map((r) => ({ kind: r.kind, result: r.result, ...(r.post_id ? { post: r.post_id } : {}),
      ...(r.company_id ? { company: { id: r.company_id, name: r.name ?? "" } } : {}), at: iso(r.at) })),
    // [PLAZA-09] Something new in a company this phone joined: n other versions to look at.
    ...rooms.map((r) => ({ kind: "room", company: { id: r.company_id, name: r.name }, count: r.n, at: iso(r.at) })),
    // AI 1.27 [PLAZA-35] New posts in 회사 정보 광장, n of them in one line.
    ...roomPosts.map((r) => ({ kind: "room_posts", company: { id: r.company_id, name: r.name }, count: r.n, at: iso(r.at) })),
  ].sort((a, b) => b.at.localeCompare(a.at));
  return items;
}

// [SRV-06] Photos with a question: what the phone made (JPEG, long side 1600px, no metadata) checked again.

const PHOTO = { max: 2, bytes: 700 * 1024, totalBytes: 150 * 1024 * 1024, keepAfterCloseMs: 90 * DAY };

/**
 * A JPEG without what could say where or on what it was taken: APP1~APP15 (Exif, XMP, maker notes) and
 * comments go, the picture stays. Null when it is not a whole JPEG.
 */
export function cleanJpeg(bytes) {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return null;
  const keep = [bytes.subarray(0, 2)];
  let i = 2;
  while (i + 4 <= bytes.length) {
    if (bytes[i] !== 0xff) return null;
    const marker = bytes[i + 1];
    if (marker === 0xff) {
      i += 1;
      continue;
    }
    if (marker === 0xda) {
      // Start of scan: the picture itself, to the end.
      keep.push(bytes.subarray(i));
      const out = new Uint8Array(keep.reduce((n, p) => n + p.length, 0));
      let at = 0;
      for (const p of keep) {
        out.set(p, at);
        at += p.length;
      }
      return out;
    }
    const length = (bytes[i + 2] << 8) | bytes[i + 3];
    if (length < 2 || i + 2 + length > bytes.length) return null;
    if (!((marker >= 0xe1 && marker <= 0xef) || marker === 0xfe)) keep.push(bytes.subarray(i, i + 2 + length));
    i += 2 + length;
  }
  return null;
}

/** The photos of a new question, or why they were left out ("paused", "full"); a bad photo is refused. */
async function readPhotos(env, raw) {
  if (raw === undefined || raw === null) return { list: [] };
  if (!Array.isArray(raw) || raw.length > PHOTO.max) refuse("photos", "photos");
  const list = raw.map((p) => {
    if (typeof p !== "string" || !/^[A-Za-z0-9+/]+=*$/.test(p) || p.length > Math.ceil((PHOTO.bytes * 4) / 3) + 4) refuse("photo", "photos");
    let bytes;
    try {
      bytes = Uint8Array.from(atob(p), (c) => c.charCodeAt(0));
    } catch {
      refuse("photo", "photos");
    }
    const clean = cleanJpeg(bytes);
    if (!clean || clean.length > PHOTO.bytes) refuse("photo", "photos");
    let text = "";
    for (let i = 0; i < clean.length; i += 0x8000) text += String.fromCharCode(...clean.subarray(i, i + 0x8000));
    return { base64: btoa(text), bytes: clean.length };
  });
  if (!list.length) return { list };
  if (await paused(env, "INQUIRY_PHOTOS")) return { list: [], skipped: "paused" };
  const used = await one(env, "SELECT COALESCE(SUM(bytes), 0) AS n FROM inquiry_photos");
  if (used.n + list.reduce((n, p) => n + p.bytes, 0) > PHOTO.totalBytes) return { list: [], skipped: "full" };
  return { list };
}

async function photoFile(env, id, n) {
  const row = await one(env, "SELECT data FROM inquiry_photos WHERE inquiry_id = ? AND n = ?", id, n);
  if (!row) return json({ error: "not_found" }, 404);
  return new Response(Uint8Array.from(atob(row.data), (c) => c.charCodeAt(0)), {
    headers: { "content-type": "image/jpeg", "cache-control": "private, no-store", "x-content-type-options": "nosniff",
      "content-security-policy": "default-src 'none'" },
  });
}

/**
 * [SRV-08] 근무표 링크 새로 만들기 switched off in the developer app reaches the share server too, so an
 * older app or a direct call cannot make links while it rests. Hourly, only when it changed.
 */
export async function syncShareSwitch(env) {
  if (!shareAdmin(env)) return false;
  const off = (await opsState(env)).off.includes("SHARE_CREATE");
  const was = (await one(env, "SELECT value FROM settings WHERE key = 'share_create_synced'"))?.value;
  if (was === String(off)) return false;
  try {
    await shareCall(env, "PUT", "/api/admin/flags", { createOff: off });
  } catch (e) {
    console.error(`share switch: ${e?.code ?? e}`);
    return false;
  }
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('share_create_synced', ?)", String(off));
  return true;
}

/**
 * [SRV-10] N8 쉬는 기능 자동: when today's D1 writes pass 90% of the free day (D1_WRITES_PER_DAY, 100,000
 * unless set), the hourly job rests 사용 통계 받기, and an hour later still over, 문의 사진 받기; when the
 * share server's KV writes pass 90% of KV_WRITES_PER_DAY (1,000), 근무표 링크 새로 만들기. Each comes with a
 * notice for the day. Next day, below the line, it turns back on what it turned off itself, never a
 * switch the developer turned off by hand.
 */
const AUTO_REST = { share: 0.9, d1: ["USAGE_STATS", "INQUIRY_PHOTOS"], kv: "SHARE_CREATE" };

export async function autoRest(env, now) {
  await flushMeter(env, now, true);
  const day = meterDay(now);
  const used = (await one(env, "SELECT count FROM counters WHERE key = ?", `meter|${day}`))?.count ?? 0;
  const d1Budget = Number(env.D1_WRITES_PER_DAY) > 0 ? Number(env.D1_WRITES_PER_DAY) : 100000;
  const kvBudget = Number(env.KV_WRITES_PER_DAY) > 0 ? Number(env.KV_WRITES_PER_DAY) : 1000;
  let kv = 0;
  if (shareAdmin(env)) {
    try {
      const st = await shareCall(env, "GET", "/api/admin/stats?days=1");
      kv = Number(st?.days?.[0]?.writes ?? 0) || 0;
    } catch {
      kv = 0;
    }
  }
  const s = await opsState(env);
  const rested = JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'auto_rested'"))?.value ?? "[]");
  let off = [...s.off];
  let notices = [...s.notices];
  let mine = rested.filter((r) => off.includes(r.key));
  const turnOff = async (key, why) => {
    off = [...new Set([...off, key])];
    mine.push({ key, day, notice: `n${randomId(9)}` });
    notices.push({ id: mine.at(-1).notice, title: "잠시 쉬는 기능이 있어요", body: `오늘 사용이 많아 '${SWITCHES[key]}'를 내일까지 쉬어요. 다른 기능은 그대로예요.`,
      level: "info", from: now, until: now + DAY, minApp: null, maxApp: null, audience: null, action: null });
    await audit(env, now, "auto_rest", key, why);
  };
  const back = [];
  // A new day below the line: what the job rested goes back on.
  for (const r of mine.filter((r) => r.day !== day)) {
    if ((r.key === AUTO_REST.kv ? kv < kvBudget * AUTO_REST.share : used < d1Budget * AUTO_REST.share)) back.push(r);
  }
  if (back.length) {
    off = off.filter((k) => !back.some((r) => r.key === k));
    notices = notices.filter((n) => !back.some((r) => r.notice === n.id));
    mine = mine.filter((r) => !back.includes(r));
    for (const r of back) await audit(env, now, "auto_resume", r.key, SWITCHES[r.key]);
  }
  let changed = back.length > 0;
  if (used >= d1Budget * AUTO_REST.share) {
    const next = AUTO_REST.d1.find((k) => !off.includes(k));
    // One more each hour while it stays over, so the gentlest goes first.
    const lastToday = mine.filter((r) => r.day === day && AUTO_REST.d1.includes(r.key)).length;
    if (next && (lastToday === 0 || !mine.some((r) => r.day === day && r.hour === Math.floor(now / HOUR)))) {
      await turnOff(next, `D1 ${used}/${d1Budget}`);
      mine.at(-1).hour = Math.floor(now / HOUR);
      changed = true;
    }
  }
  if (kv >= kvBudget * AUTO_REST.share && !off.includes(AUTO_REST.kv)) {
    await turnOff(AUTO_REST.kv, `KV ${kv}/${kvBudget}`);
    changed = true;
  }
  if (!changed) return { used, kv, rested: mine.map((r) => r.key) };
  notices = notices.filter((n) => n.until > now - 30 * DAY).slice(-NOTICE.max);
  // AM 1.28 [FLAG-24] Rests are forced-off flags marked auto; going back on lifts only those, never a force by hand.
  const flagOf = (k) => Object.keys(SWITCH_FLAGS).find((f) => SWITCH_FLAGS[f] === k);
  await batch(env, [
    ...off.filter((k) => !s.off.includes(k)).map((k) => stmt(env, `INSERT INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto)
      VALUES (?, 'off', '오늘 사용이 많아 내일까지 쉬어요(자동)', NULL, NULL, NULL, '', 'system', ?, 1)
      ON CONFLICT (key) DO UPDATE SET force = 'off', why = excluded.why, until = NULL, by_name = '', by_id = 'system', at = excluded.at, auto = 1`, flagOf(k), now)),
    ...s.off.filter((k) => !off.includes(k)).map((k) => stmt(env, "UPDATE flag_overrides SET force = NULL, at = ? WHERE key = ? AND auto = 1", now, flagOf(k))),
    stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('notices', ?)", JSON.stringify(notices)),
    stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('auto_rested', ?)", JSON.stringify(mine)),
  ]);
  return { used, kv, rested: mine.map((r) => r.key) };
}

// [SRV-05][STAT-04] The share server, from here: its reported links, taking one down, its day totals.
// Both workers hold the same secret SHARE_ADMIN_TOKEN; this one also SHARE_BASE_URL. Without them the
// developer app says how to connect, and nothing is called.

function shareAdmin(env) {
  const base = typeof env.SHARE_BASE_URL === "string" ? env.SHARE_BASE_URL.trim().replace(/\/+$/, "") : "";
  const token = typeof env.SHARE_ADMIN_TOKEN === "string" ? env.SHARE_ADMIN_TOKEN.trim() : "";
  return /^https:\/\/[A-Za-z0-9.-]+$/.test(base) && token.length >= 20 ? { base, token } : null;
}

/** [SRV-08] Five seconds at most: a share server that hangs does not hold the developer app's request. */
const SHARE_TIMEOUT_MS = 5000;

async function shareCall(env, method, path, body) {
  const s = shareAdmin(env);
  if (!s) return null;
  let res;
  try {
    res = await fetch(`${s.base}${path}`, {
      method,
      headers: { authorization: `Bearer ${s.token}`, ...(body ? { "content-type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(SHARE_TIMEOUT_MS),
    });
  } catch {
    throw new Refuse("share_server", "share", 504);
  }
  if (res.status === 204) return {};
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Refuse(data.error === "not_found" ? "not_found" : "share_server", "share", res.status === 404 ? 404 : 502);
  return data;
}

async function shareReports(env) {
  if (!shareAdmin(env)) return json({ connected: false, reports: [] });
  const data = await shareCall(env, "GET", "/api/admin/reports");
  const s = shareAdmin(env);
  return json({ connected: true, reports: (data.reports ?? []).map((r) => ({ ...r, url: `${s.base}/s/${r.id}` })) });
}

async function removeShare(request, env, id, now) {
  const body = await readJson(request, 1024);
  const reason = plainText(body.reason ?? "운영 규칙 위반", 60, "reason", true);
  if (!shareAdmin(env)) return json({ error: "share_server" }, 409);
  // [SHR-13] The reason goes with it, so the sharer's app can say why (kept with the removed mark, 90 days).
  await shareCall(env, "DELETE", `/api/admin/share/${id}`, { reason });
  await audit(env, now, "share_remove", id, reason);
  return shareReports(env);
}

async function shareStats(env, days) {
  if (!shareAdmin(env)) return null;
  try {
    return await shareCall(env, "GET", `/api/admin/stats?days=${days}`);
  } catch {
    return null;
  }
}

// AM 1.28 시작
// ---- 기능 플래그(다크머지) [FLAG-20]~[FLAG-33] ----
//
// Every feature that ships switched off, waits for a date, a policy or a person's confirmation, or can be switched off
// in an incident is one row of FLAG_CATALOG (the same keys as the app's core FlagCatalog, 묶음 AL; a test compares the
// keys with the contract, 통합계획 2-1). The phone decides by itself from the conditions it was built with; the server
// only sends what overrides them (/api/config flags) and keeps who changed what, when and why. The order of the
// decision is in src/admin-lib.js evalFlag, which this server and the developer app's 미리 보기 share.

const FL = adminLib();

/** The six 기능 스위치 of 1.22 (SWITCHES) as flags, for apps up to 1.27 that read /api/config off. */
const SWITCH_FLAGS = { "plaza.write": "PLAZA_WRITE", "company.report": "COMPANY_REPORT", "inquiry.photos": "INQUIRY_PHOTOS",
  "share.create": "SHARE_CREATE", "usage.stats": "USAGE_STATS", "plaza.news": "PLAZA_NEWS" };

const TYPE_FLAGS = { SHIFT: "type.shift", DAY: "type.day", HOURLY: "type.hourly", FIELD: "type.field", BUSINESS: "type.business", INVEST: "type.invest" };

/**
 * [FLAG-20] The catalog. 이름·하는 일·꺼 둔 이유·근거·위험·기본값·조건·정리 예정 판 are what the developer app shows and the
 * conditions the app ships with. 1.28 통합 [FLAG-34]: they come from the app's core FlagCatalog through the generated
 * src/flag-catalog.js (core FlagCatalogExportTest writes it and fails while it is old), so there is one book.
 */
export const FLAG_CATALOG = CORE_FLAGS;

/** The keys of the contract (통합계획 2-1) and the integrator's stats.more; free.<FEATURE> are made on demand. */
export const FLAG_KEYS = FLAG_CATALOG.map((f) => f.key);

// AU 1.29
/**
 * [FLAG-20] 프로 features the server may open to everyone (app FeatureGate.Feature), as free.<FEATURE>.
 * [NXT-03] The names are core's ProFeatures (CORE_FEATURES, made by FlagCatalogExportTest), so the developer app and
 * the phone's 진단 name a free.<FEATURE> row the same way.
 */
const FREE_FEATURES = CORE_FEATURES;
// AU 1.29 끝
/** [FLAG-34] free.<FEATURE>, the same pattern as core FlagCatalog.free. */
const FREE_KEY = new RegExp(CORE_FREE.pattern);

/** A flag by key: the catalog's, or a free.<FEATURE> made from core's words and the feature's Korean name; null for anything else. */
function flagDef(key) {
  const known = FLAG_CATALOG.find((f) => f.key === key);
  if (known) return known;
  const m = FREE_KEY.exec(key ?? "");
  if (!m) return null;
  const label = FREE_FEATURES[m[1]] ?? m[1];
  const fill = (t) => t.replaceAll(CORE_FREE.slot, label);
  const { pattern, slot, ...row } = CORE_FREE;
  return { ...row, key, name: fill(CORE_FREE.name), what: fill(CORE_FREE.what), whyOff: fill(CORE_FREE.whyOff) };
}

/**
 * [FLAG-33] 처리방침 판마다 게시일·시행일·동의 없이도 시행되는 날(heldUntil). The developer app can change these (the owner),
 * so a date moved again needs no app release. unfavourable: 이용자에게 불리한 변경(30일 전 고지, 처리방침 9항).
 * [FLAG-34] The versions the app ships with (core PolicyCalendar.SEED: 3.1, 3.2) take their dates from it; 3.2's are a
 * placeholder until 순번 459 fixes them (관리자 앱에서 고쳐요).
 */
const POLICY_UNFAVOURABLE = { "2.5": true, "3.0": true, "3.1": false, "3.2": true, "3.3": false };
const POLICY_CALENDAR = [
  { version: "2.5", posted: "2026-10-22", effective: "2026-11-21", heldUntil: null },
  { version: "3.0", posted: "2026-10-25", effective: "2026-11-24", heldUntil: null },
  ...CORE_POLICY,
].map((p) => ({ ...p, unfavourable: POLICY_UNFAVOURABLE[p.version] ?? false }));

/** [FLAG-25] The newest release this server knows (APP_CODES), to tell which flags are past their 정리 예정 판. */
const latestRelease = () => Object.keys(APP_CODES).sort(FL.versionCompare).at(-1);

const FLAG_LIMITS = { why: 300, untilDays: 365, history: 100 };

const flagMigrations = new WeakMap();

/**
 * [FLAG-23] Once per database: the 1.27 settings become overrides. 정식 출시 on → billing and ads forced on (the
 * same as before: both started together), 무료로 연 기능 → free.<FEATURE> forced on, 기능 스위치 off → forced off (the
 * hourly job's own rests keep auto). The old rows stay, unread, and a second run changes nothing.
 */
function migrateFlags(env) {
  let done = flagMigrations.get(env.DB);
  if (!done) {
    done = (async () => {
      if ((await one(env, "SELECT value FROM settings WHERE key = 'flags_migrated'"))?.value) return;
      const rows = await all(env, "SELECT key, value FROM settings WHERE key IN ('billing_live', 'free_features', 'switches_off', 'auto_rested')");
      const get = (k) => rows.find((r) => r.key === k)?.value;
      const list = (k) => {
        try {
          const v = JSON.parse(get(k) ?? "[]");
          return Array.isArray(v) ? v : [];
        } catch {
          return [];
        }
      };
      const now = Date.now();
      const put = [];
      const add = (key, force, why, auto = 0) => put.push(stmt(env, `INSERT OR IGNORE INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto)
        VALUES (?, ?, ?, NULL, NULL, NULL, '옮김(1.28)', 'system', ?, ?)`, key, force, why, now, auto));
      if (get("billing_live") === "1") {
        add("billing", "on", "1.27까지의 정식 출시 스위치가 켜져 있었어요");
        add("ads", "on", "1.27까지의 정식 출시 스위치가 켜져 있었어요");
      }
      for (const f of list("free_features")) if (typeof f === "string" && FEATURE.test(f)) add(`free.${f}`, "on", "1.27까지 무료로 연 기능이었어요");
      const auto = new Set(list("auto_rested").map((r) => r?.key));
      for (const s of list("switches_off")) {
        const key = Object.keys(SWITCH_FLAGS).find((k) => SWITCH_FLAGS[k] === s);
        if (key) add(key, "off", auto.has(s) ? "오늘 사용이 많아 내일까지 쉬어요(자동)" : "1.27까지의 기능 스위치로 꺼져 있었어요", auto.has(s) ? 1 : 0);
      }
      put.push(stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('flags_migrated', ?)", iso(now)));
      await batch(env, put);
      if (put.length > 1) {
        await run(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, 'flag_migrate', '', ?, '', '')", now, `${put.length - 1}개를 기능 관리로 옮겼어요`);
      }
    })().catch((e) => {
      flagMigrations.delete(env.DB);
      throw e;
    });
    flagMigrations.set(env.DB, done);
  }
  return done;
}

/** An override row as the API and evalFlag see it. */
function readOverride(r) {
  if (!r) return null;
  let conditions = null;
  try {
    conditions = r.conditions ? JSON.parse(r.conditions) : null;
  } catch {
    conditions = null;
  }
  return { force: r.force === "on" || r.force === "off" ? r.force : null, why: r.why ?? "", conditions: Array.isArray(conditions) ? conditions : null,
    until: r.until ? iso(r.until) : null, reviewAt: r.review_at ?? null, by: r.by_name ?? "", byId: r.by_id ?? "", at: iso(r.at), auto: r.auto === 1,
    // 1.28 통합 [FLAG-36] AL-D3: outside this versionCode range the phone turns the flag off (판단 2단계).
    minApp: r.min_app ?? null, maxApp: r.max_app ?? null };
}

/** [FLAG-33] The calendar with the owner's changes, and versions the owner added. */
function mergePolicy(raw) {
  let over = {};
  try {
    over = JSON.parse(raw ?? "{}") ?? {};
  } catch {
    over = {};
  }
  const out = POLICY_CALENDAR.map((p) => ({ ...p, ...(over[p.version] ?? {}), changed: Object.hasOwn(over, p.version) }));
  for (const [version, p] of Object.entries(over)) if (!out.some((x) => x.version === version)) out.push({ version, heldUntil: null, unfavourable: false, ...p, changed: true });
  return out.sort((a, b) => FL.versionCompare(a.version, b.version));
}

/** [FLAG-21] Everything a decision needs, in one trip: overrides, confirmations, the policy calendar, what the server saw. */
const FLAG_STORE_SQL = {
  flagOverrides: "SELECT * FROM flag_overrides",
  flagChecks: "SELECT key, by_name, by_id, at, note FROM flag_checks",
  flagPolicy: "SELECT value FROM settings WHERE key = 'policy_overrides'",
  flagState: "SELECT key, on_now, since, warned FROM flag_state",
};

async function flagStore(env) {
  await migrateFlags(env);
  const [o, c, p, s] = (await batch(env, Object.values(FLAG_STORE_SQL).map((sql) => env.DB.prepare(sql)))).map((r) => r.results);
  return storeFrom(o, c, p, s);
}

/** The store from the rows of FLAG_STORE_SQL (also read inside the home screen's one batch). */
function storeFrom(o, c, p, s) {
  return {
    overrides: new Map(o.map((r) => [r.key, readOverride(r)])),
    checks: Object.fromEntries(c.map((r) => [r.key, { by: r.by_name, byId: r.by_id, at: iso(r.at), note: r.note ?? "" }])),
    policy: mergePolicy(p[0]?.value),
    state: new Map(s.map((r) => [r.key, r])),
  };
}

/** Every flag there is: the catalog and the free.<FEATURE> rows that have an override. */
const flagKeys = (store) => [...FLAG_KEYS, ...[...store.overrides.keys()].filter((k) => !FLAG_KEYS.includes(k) && flagDef(k))];

/** The server's view of [key] at [now]: conditions that depend on a phone do not hold it back. */
const serverEval = (store, key, now = Date.now()) => FL.evalFlag(flagDef(key), store.overrides.get(key) ?? null,
  { now, policy: store.policy, checks: store.checks, phone: null });

/** [FLAG-24] Whether one of the six switch flags is forced off now; read on every write it guards. */
async function switchOff(env, name) {
  await migrateFlags(env);
  const key = Object.keys(SWITCH_FLAGS).find((k) => SWITCH_FLAGS[k] === name);
  const r = await one(env, "SELECT force, until FROM flag_overrides WHERE key = ?", key);
  return FL.forceOf(r ? { force: r.force, until: r.until ? iso(r.until) : null } : null, Date.now()) === "off";
}

/** The manual keys a condition list waits on (inside '또는' too). */
const manualKeys = (list) => [...new Set((list ?? []).flatMap((c) => (c.type === "manual" ? [c.key] : c.type === "any" ? manualKeys(c.of) : [])))];

/**
 * [FLAG-22] What /api/config says about flags: only what the server overrides, as 통합계획 2-1 shapes them, plus
 * confirmed (AL-D2): the manual facts the developer confirmed, for every flag that waits on one (a phone with no
 * answer from the server treats a manual condition as unknown, so such a flag is always sent).
 */
function configFlags(store, now) {
  const out = {};
  for (const key of flagKeys(store)) {
    const def = flagDef(key);
    const o = store.overrides.get(key) ?? null;
    const force = FL.forceOf(o, now);
    const manual = manualKeys(o?.conditions ?? def.conditions);
    const ranged = o?.minApp != null || o?.maxApp != null;
    if (!force && !o?.conditions && !manual.length && !ranged) continue;
    const confirmed = Object.fromEntries(manual.filter((k) => store.checks[k]).map((k) => [k, store.checks[k].at]));
    // 1.28 통합 [FLAG-36] minApp/maxApp (AL-D3) go with every flag sent; null is no end.
    out[key] = { force, why: force ? o.why : "", conditions: o?.conditions ?? null, until: force && o.until ? o.until : null,
      minApp: o?.minApp ?? null, maxApp: o?.maxApp ?? null, confirmed };
  }
  return out;
}

/** [FLAG-22] The calendar as the app reads it. */
const configPolicy = (store) => store.policy.map((p) => ({ version: p.version, posted: p.posted, effective: p.effective, heldUntil: p.heldUntil ?? null }));

/** [FLAG-22] For apps up to 1.27: free.<FEATURE> forced on, and the six switches forced off by their old names. */
function legacyFlags(store, now) {
  const free = [];
  const off = [];
  for (const key of flagKeys(store)) {
    const m = FREE_KEY.exec(key);
    if (m && serverEval(store, key, now).on) free.push(m[1]);
    if (SWITCH_FLAGS[key] && FL.forceOf(store.overrides.get(key), now) === "off") off.push(SWITCH_FLAGS[key]);
  }
  return { free, off };
}

/**
 * [FLAG-25] The first moment billing is on by the server's view is the launch (launchedAt): thank-you months and passes
 * count from it. Written once; never moved again, whatever happens to the flag later.
 */
async function recordLaunch(env, store, now) {
  if (!serverEval(store, "billing", now).on) return null;
  const had = await one(env, "SELECT value FROM settings WHERE key = 'launched_at'");
  if (had && Number(had.value) > 0) return Number(had.value);
  await run(env, "INSERT OR IGNORE INTO settings (key, value) VALUES ('launched_at', ?)", String(now));
  const kept = Number((await one(env, "SELECT value FROM settings WHERE key = 'launched_at'"))?.value);
  if (kept === now) await audit(env, now, "launch_recorded", "billing", `정식 출시일 ${iso(now)}`);
  return kept;
}

/**
 * [FLAG-25][FLAG-28] The hourly job (17분) and every change: forces whose until passed end, a flag the server sees turn on
 * or off is written down (when, for the 24 hours of 홈 경고), the launch is recorded, and admins with 기능 관리 hear of a
 * legal flag the day before it turns on by itself and once it did (D99).
 */
export async function flagTick(env, now = Date.now(), { push = true } = {}) {
  let store = await flagStore(env);
  const ended = [...store.overrides.entries()].filter(([, o]) => (o.force && o.until && Date.parse(o.until) <= now));
  for (const [key, o] of ended) {
    await run(env, "UPDATE flag_overrides SET force = NULL, until = NULL WHERE key = ?", key);
    await flagHistory(env, now, key, "flag_expire", o, { ...o, force: null, until: null }, "정한 기한이 끝났어요", { system: true });
  }
  if (ended.length) store = await flagStore(env);
  const news = [];
  for (const key of flagKeys(store)) {
    const def = flagDef(key);
    const ev = serverEval(store, key, now);
    const was = store.state.get(key);
    if (!was) {
      await run(env, "INSERT OR IGNORE INTO flag_state (key, on_now, since, warned) VALUES (?, ?, ?, NULL)", key, ev.on ? 1 : 0, now);
    } else if ((was.on_now === 1) !== ev.on) {
      await run(env, "UPDATE flag_state SET on_now = ?, since = ? WHERE key = ?", ev.on ? 1 : 0, now, key);
      await run(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, ?, ?, ?, '', '')", now,
        ev.on ? "flag_auto_on" : "flag_auto_off", key, ev.reason.slice(0, 300));
      if (def.risk === "LEGAL" && ev.on) news.push({ title: `${def.name}이(가) 켜졌어요`, body: "24시간 동안 홈 맨 위에서 한 번에 강제로 끌 수 있어요.", url: `/admin/#/flags/${key}`, legal: true });
    }
    // D99: the day before a legal flag would turn on by itself (its date comes, nothing else left).
    if (def.risk === "LEGAL" && !ev.on && ev.state === "waiting") {
      const tomorrow = serverEval(store, key, now + DAY);
      const day = kstDay(now + DAY);
      if (tomorrow.on && was?.warned !== day) {
        await run(env, "UPDATE flag_state SET warned = ? WHERE key = ?", day, key);
        news.push({ title: `내일 ${def.name}이(가) 저절로 켜져요`, body: `${day} 0시(한국 시간)에 켜져요. 미루려면 기능 관리에서 강제로 꺼 주세요.`, url: `/admin/#/flags/${key}`, legal: true });
      }
    }
  }
  await recordLaunch(env, store, now);
  // [FLAG-37] D101: these two are a legal flag's, so they go inside an admin's quiet time too.
  if (push) for (const { legal, ...n } of news) await notifyAdmins(env, "flags", { ...n, urgent: true }, now, { ignoreQuiet: legal === true });
  return news;
}

/** [FLAG-21] One change, kept whole (before and after) for the flag's own history, and a line in 활동 기록. */
async function flagHistory(env, now, key, action, before, after, why, { system = false } = {}) {
  const who = system ? "" : env.actor ? actorName(env) : "";
  const id = system ? "" : env.actor?.id ?? "";
  await run(env, "INSERT INTO flag_history (key, at, action, actor, actor_id, before, after, why) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", key, now, action,
    who, id, before === null || before === undefined ? null : JSON.stringify(before), after === null || after === undefined ? null : JSON.stringify(after), why ?? "");
  await run(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, ?, ?, ?, ?, ?)", now, action, key, String(why ?? "").slice(0, 300), who, id);
}

/** [FLAG-30] Of the phones that sent which flags were on (stats.more), how many had each, per flag, over [days] days. */
async function flagShares(env, now, days = 7) {
  const from = kstDay(now - days * DAY);
  const rows = await all(env, "SELECT flag, SUM(phones) AS phones FROM usage_flags WHERE day >= ? GROUP BY flag", from);
  const base = rows.find((r) => r.flag === "*")?.phones ?? 0;
  return { base, share: Object.fromEntries(rows.filter((r) => r.flag !== "*").map((r) => [r.flag, base ? Math.round((1000 * r.phones) / base) / 10 : null])) };
}

/** [FLAG-21][ADMUX-21] One flag as the list and the detail show it. */
function flagRow(store, key, now, shares) {
  const def = flagDef(key);
  const o = store.overrides.get(key) ?? null;
  const ev = serverEval(store, key, now);
  const conds = o?.conditions ?? def.conditions;
  const release = latestRelease();
  const legal = def.risk === "LEGAL";
  const manual = (c) => manualKeys([c]);
  return {
    key, name: def.name, group: def.group, what: def.what, whyOff: def.whyOff, basis: def.basis, risk: def.risk, default: def.default, sunset: def.sunset,
    owner: def.owner, since: def.since, switchKey: SWITCH_FLAGS[key] ?? null, conditions: conds, catalogConditions: def.conditions, override: o,
    on: ev.on, state: ev.state, source: ev.source, reason: ev.reason, next: ev.next, daysLeft: FL.daysLeft(ev.next, now),
    checklist: ev.conditions.map((x) => ({ label: x.label, met: x.met, type: x.c.type, keys: manual(x.c) })),
    warnings: FL.noticeWarnings(def, conds, store.policy),
    sunsetDue: Boolean(def.sunset && release && FL.versionCompare(release, def.sunset) >= 0),
    reviewDue: Boolean(o?.reviewAt && o.reviewAt <= kstDay(now)),
    onShare: shares?.share?.[key] ?? null,
    onSince: store.state.get(key)?.since ? iso(store.state.get(key).since) : null,
    alert: Boolean(legal && ev.on && store.state.get(key)?.since && now - store.state.get(key).since < DAY),
    manualKeys: manualKeys(conds),
  };
}

/** Who may do what to a flag (D97): the owner everything; 기능 강제로 끄기 (flagOff) for force off; the rest by area. */
function flagMay(actor, def, step, current) {
  if (actor.owner) return true;
  const legal = def.risk === "LEGAL";
  if (step === "on") return false;
  if (step === "off") return can(actor, "flagOff", "edit");
  if (step === "clear") return current === "on" ? can(actor, "flagOff", "edit") : !legal && can(actor, "flagOff", "edit");
  if (step === "conditions") return !legal && can(actor, "flags", "edit");
  return false;
}

/** What the screens tell an admin about their own reach, so buttons lock with the reason instead of failing. */
const flagReach = (actor) => ({ owner: Boolean(actor.owner), edit: can(actor, "flags", "edit"), off: Boolean(actor.owner || can(actor, "flagOff", "edit")) });

/** [ADMUX-21] The list, with the calendar and the confirmations. */
async function flagList(env, actor, now) {
  const store = await flagStore(env);
  const shares = await flagShares(env, now);
  return {
    serverTime: iso(now), release: latestRelease(), reach: flagReach(actor), policy: store.policy, checks: store.checks, manualNames: FL.MANUAL_NAMES,
    freeFeatures: FREE_FEATURES, statsPhones: shares.base,
    flags: flagKeys(store).map((k) => flagRow(store, k, now, shares)),
  };
}

/** [ADMUX-22] One flag: the row, its whole history, phones it is on for by app version, and problems before and after. */
async function flagDetail(env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "not_found" }, 404);
  const store = await flagStore(env);
  const shares = await flagShares(env, now);
  const row = flagRow(store, key, now, shares);
  const pivot = store.state.get(key)?.since ?? now;
  const from = kstDay(pivot - 7 * DAY);
  const to = kstDay(pivot + 7 * DAY);
  const [history, versions, bugs, errors, series] = (await batch(env, [
    stmt(env, "SELECT * FROM flag_history WHERE key = ? ORDER BY id DESC LIMIT ?", key, FLAG_LIMITS.history),
    stmt(env, `SELECT f.app, SUM(f.phones) AS on_phones, (SELECT SUM(b.phones) FROM usage_flags b WHERE b.flag = '*' AND b.app = f.app AND b.day >= ?) AS base
      FROM usage_flags f WHERE f.flag = ? AND f.day >= ? GROUP BY f.app ORDER BY on_phones DESC`, kstDay(now - 7 * DAY), key, kstDay(now - 7 * DAY)),
    stmt(env, "SELECT created_at FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ? AND created_at < ?", pivot - 7 * DAY, pivot + 7 * DAY),
    stmt(env, "SELECT key, count FROM counters WHERE key >= ? AND key <= ?", `errors|${from}`, `errors|${to}`),
    stmt(env, `SELECT f.day, f.phones, (SELECT SUM(b.phones) FROM usage_flags b WHERE b.flag = '*' AND b.day = f.day) AS base
      FROM (SELECT day, SUM(phones) AS phones FROM usage_flags WHERE flag = ? AND day >= ? GROUP BY day) f ORDER BY f.day`, key, kstDay(now - 28 * DAY)),
  ])).map((r) => r.results);
  const errBefore = errors.filter((e) => e.key.slice(7) < kstDay(pivot)).reduce((n, e) => n + e.count, 0);
  const errAfter = errors.filter((e) => e.key.slice(7) >= kstDay(pivot)).reduce((n, e) => n + e.count, 0);
  const parse = (v) => {
    try {
      return v ? JSON.parse(v) : null;
    } catch {
      return null;
    }
  };
  return json({
    ...row, serverTime: iso(now), reach: flagReach(actor), policy: store.policy, checks: store.checks, manualNames: FL.MANUAL_NAMES,
    statsPhones: shares.base, launchedAt: key === "billing" ? (await launchState(env)).launchedAt : undefined,
    thanksMonths: key === "billing" ? (await launchState(env)).thanksMonths : undefined,
    history: history.map((h) => ({ id: h.id, at: iso(h.at), action: h.action, actor: h.actor || "시스템", actorId: h.actor_id || null, before: parse(h.before), after: parse(h.after), why: h.why })),
    versions: versions.map((v) => ({ app: v.app, code: APP_CODES[v.app] ?? null, phones: v.on_phones, share: v.base ? Math.round((1000 * v.on_phones) / v.base) / 10 : null })),
    series: series.map((x) => ({ day: x.day, share: x.base ? Math.round((1000 * x.phones) / x.base) / 10 : null, phones: x.phones })),
    compare: { pivot: iso(pivot), days: 7, bugsBefore: bugs.filter((b) => b.created_at < pivot).length, bugsAfter: bugs.filter((b) => b.created_at >= pivot).length,
      errorsBefore: errBefore, errorsAfter: errAfter, measured: store.state.has(key) },
  });
}

/** A day (YYYY-MM-DD, KST) or a moment as ms, at most a year ahead and after now; null when not given. */
function readUntil(v, field, now) {
  if (v === undefined || v === null || v === "") return null;
  const ms = typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) ? Date.parse(`${v}T00:00:00+09:00`) : Date.parse(String(v));
  if (!Number.isFinite(ms) || ms <= now || ms > now + FLAG_LIMITS.untilDays * DAY) refuse(field, field);
  return ms;
}

/**
 * 1.28 통합 [FLAG-36] AL-D3: the app versionCode range of a flag (both null: every version), with why. Outside it the
 * phone turns the flag off before anything else but a force off (판단 2단계). Who may: as for its conditions.
 */
async function setFlagRange(request, env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "flag", field: "key" }, 404);
  if (!flagMay(actor, def, "conditions")) return json({ error: "no_permission", area: def.risk === "LEGAL" ? "owner" : "flags" }, 403);
  const body = await readJson(request, 2048);
  const code = (v, field) => (v === undefined || v === null || v === "" ? null : Number.isInteger(v) && v > 0 && v < 1_000_000 ? v : refuse("app", field));
  const minApp = code(body.minApp, "minApp");
  const maxApp = code(body.maxApp, "maxApp");
  if (minApp !== null && maxApp !== null && maxApp < minApp) refuse("app", "maxApp");
  const why = plainText(body.why, FLAG_LIMITS.why, "why", true);
  const store = await flagStore(env);
  const before = store.overrides.get(key) ?? null;
  await run(env, `INSERT INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto, min_app, max_app) VALUES (?, NULL, ?, NULL, NULL, NULL, ?, ?, ?, 0, ?, ?)
    ON CONFLICT (key) DO UPDATE SET min_app = excluded.min_app, max_app = excluded.max_app, by_name = excluded.by_name, by_id = excluded.by_id, at = excluded.at`,
  key, why, actorName(env), actor.id, now, minApp, maxApp);
  const after = readOverride(await one(env, "SELECT * FROM flag_overrides WHERE key = ?", key));
  await flagHistory(env, now, key, "flag_range", before, after, why);
  await flagTick(env, now);
  return flagDetail(env, actor, key, now);
}

/** [FLAG-21][FLAG-27][ADMUX-23] Force on, force off (reason required, 다시 볼 날), or back to the conditions. */
async function setFlagForce(request, env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "flag", field: "key" }, 404);
  const body = await readJson(request, 4096);
  const force = body.force === "on" || body.force === "off" ? body.force : body.force === null ? null : refuse("force", "force");
  const store = await flagStore(env);
  const before = store.overrides.get(key) ?? null;
  const current = FL.forceOf(before, now);
  const step = force ?? "clear";
  if (!flagMay(actor, def, step, current)) {
    return json({ error: "no_permission", area: step === "off" || (step === "clear" && current === "on") ? "flagOff" : "owner" }, 403);
  }
  const why = plainText(body.why, FLAG_LIMITS.why, "why", force !== null);
  const until = force ? readUntil(body.until, "flag_until", now) : null;
  const realDay = (v) => typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(`${v}T00:00:00Z`))
    && new Date(`${v}T00:00:00Z`).toISOString().slice(0, 10) === v;
  const review = force === "off" && body.reviewAt ? (realDay(body.reviewAt) && body.reviewAt >= kstDay(now) && body.reviewAt <= kstDay(now + FLAG_LIMITS.untilDays * DAY)
    ? body.reviewAt : refuse("review_at", "reviewAt")) : null;
  // [FLAG-32] At least one way of working stays to choose.
  if (force === "off" && def.group === "type") {
    const left = Object.values(TYPE_FLAGS).filter((k) => k !== key && serverEval(store, k, now).on);
    if (!left.length) return json({ error: "last_type", field: "key" }, 409);
  }
  await run(env, `INSERT INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, 0)
    ON CONFLICT (key) DO UPDATE SET force = excluded.force, why = excluded.why, until = excluded.until, review_at = excluded.review_at,
      by_name = excluded.by_name, by_id = excluded.by_id, at = excluded.at, auto = 0`,
  key, force, why, until, review, actorName(env), actor.id, now);
  const after = readOverride(await one(env, "SELECT * FROM flag_overrides WHERE key = ?", key));
  const action = def.group === "type" && force !== "on" ? (force === "off" ? "type_hide" : "type_show") : force === "off" ? "flag_force_off" : force === "on" ? "flag_force_on" : "flag_clear";
  await flagHistory(env, now, key, action, before, after, why || (force === null ? "조건대로 되돌림" : ""));
  await flagTick(env, now);
  return flagDetail(env, actor, key, now);
}

/** [FLAG-21][ADMUX-23] The conditions changed (checked), or back to the app's own (null). */
async function setFlagConditions(request, env, actor, key, now) {
  const def = flagDef(key);
  if (!def) return json({ error: "flag", field: "key" }, 404);
  if (!flagMay(actor, def, "conditions")) return json({ error: "no_permission", area: def.risk === "LEGAL" ? "owner" : "flags" }, 403);
  const body = await readJson(request, 8192);
  let conditions = null;
  if (body.conditions !== null && body.conditions !== undefined) {
    const checked = FL.checkConditions(body.conditions);
    if (checked.error) return json({ error: "conditions", field: "conditions", detail: checked.error }, 400);
    const unknown = JSON.stringify(checked.list).match(/"type":"manual","key":"([^"]+)"/g)?.map((m) => m.slice(23, -1)).filter((k) => !Object.hasOwn(FL.MANUAL_NAMES, k)) ?? [];
    if (unknown.length) return json({ error: "conditions", field: "conditions", detail: `모르는 확인 이름이에요: ${unknown[0]}` }, 400);
    conditions = checked.list;
  }
  const why = plainText(body.why, FLAG_LIMITS.why, "why", true);
  const store = await flagStore(env);
  const before = store.overrides.get(key) ?? null;
  await run(env, `INSERT INTO flag_overrides (key, force, why, conditions, until, review_at, by_name, by_id, at, auto) VALUES (?, NULL, ?, ?, NULL, NULL, ?, ?, ?, 0)
    ON CONFLICT (key) DO UPDATE SET conditions = excluded.conditions, by_name = excluded.by_name, by_id = excluded.by_id, at = excluded.at`,
  key, why, conditions ? JSON.stringify(conditions) : null, actorName(env), actor.id, now);
  const after = readOverride(await one(env, "SELECT * FROM flag_overrides WHERE key = ?", key));
  await flagHistory(env, now, key, "flag_conditions", before, after, why);
  await flagTick(env, now);
  return flagDetail(env, actor, key, now);
}

/** [FLAG-21][FLAG-27] 확인했어요 on a manual condition (or taken back): the owner's when any legal flag waits on it. */
async function setFlagCheck(request, env, actor, name, now) {
  if (!Object.hasOwn(FL.MANUAL_NAMES, name)) return json({ error: "manual", field: "key" }, 404);
  // The flags that wait on it now: the catalog's conditions or the ones the developer app put in their place.
  const store = await flagStore(env);
  const users = flagKeys(store).map(flagDef).filter((f) => manualKeys(store.overrides.get(f.key)?.conditions ?? f.conditions).includes(name));
  const legal = users.some((f) => f.risk === "LEGAL");
  if (!actor.owner && (legal || !can(actor, "flags", "edit"))) return json({ error: "no_permission", area: legal ? "owner" : "flags" }, 403);
  const body = await readJson(request, 2048);
  if (typeof body.done !== "boolean") refuse("done", "done");
  const note = plainText(body.note, FLAG_LIMITS.why, "note");
  const was = await one(env, "SELECT * FROM flag_checks WHERE key = ?", name);
  if (body.done) {
    await run(env, "INSERT OR REPLACE INTO flag_checks (key, by_name, by_id, at, note) VALUES (?, ?, ?, ?, ?)", name, actorName(env), actor.id, now, note);
  } else {
    await run(env, "DELETE FROM flag_checks WHERE key = ?", name);
  }
  for (const f of users.length ? users : [{ key: name }]) {
    await flagHistory(env, now, f.key, body.done ? "flag_check" : "flag_uncheck", was ? { check: name, at: iso(was.at), by: was.by_name } : null,
      body.done ? { check: name, at: iso(now), by: actorName(env) } : null, `${FL.MANUAL_NAMES[name]}${note ? ` · ${note}` : ""}`);
  }
  await flagTick(env, now);
  return json(await flagList(env, actor, now));
}

/** [FLAG-33] The owner moves a policy version's dates (or adds a version), with why. */
async function setPolicy(request, env, actor, now) {
  if (!actor.owner) return json({ error: "no_permission", area: "owner" }, 403);
  const body = await readJson(request, 2048);
  if (!/^\d{1,2}\.\d{1,2}$/.test(body.version ?? "")) refuse("policy", "version");
  const day = (v, field, optional = false) => (optional && (v === null || v === undefined || v === "") ? null
    : typeof v === "string" && /^20\d\d-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(`${v}T00:00:00Z`)) ? v : refuse("policy", field));
  const posted = day(body.posted, "posted");
  const effective = day(body.effective, "effective");
  const heldUntil = day(body.heldUntil, "heldUntil", true);
  if (effective < posted || (heldUntil && heldUntil < effective)) refuse("policy", "effective");
  const why = plainText(body.why, FLAG_LIMITS.why, "why", true);
  const raw = (await one(env, "SELECT value FROM settings WHERE key = 'policy_overrides'"))?.value;
  let over = {};
  try {
    over = JSON.parse(raw ?? "{}") ?? {};
  } catch {
    over = {};
  }
  const before = mergePolicy(raw).find((p) => p.version === body.version) ?? null;
  over[body.version] = { posted, effective, heldUntil, unfavourable: Boolean(body.unfavourable) };
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)", JSON.stringify(over));
  await flagHistory(env, now, `policy.${body.version}`, "policy_edit", before, over[body.version], why);
  await flagTick(env, now);
  return json(await flagList(env, actor, now));
}

/** [FLAG-25] 감사 보상 개월 (thanksMonths), the owner's, now on billing's screen. */
async function setThanks(request, env, actor, now) {
  if (!actor.owner) return json({ error: "no_permission", area: "owner" }, 403);
  const body = await readJson(request, 1024);
  if (!Number.isInteger(body.thanksMonths) || body.thanksMonths < 0 || body.thanksMonths > 12) refuse("thanksMonths", "thanksMonths");
  const before = (await launchState(env)).thanksMonths;
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('thanks_months', ?)", String(body.thanksMonths));
  if (before !== body.thanksMonths) await flagHistory(env, now, "billing", "launch_thanks", { thanksMonths: before }, { thanksMonths: body.thanksMonths }, `감사 보상 ${body.thanksMonths}개월`);
  return flagDetail(env, actor, "billing", now);
}

/** [FLAG-32][ADMUX-26] The six ways of working, shown or hidden. */
async function typeList(env, actor, now) {
  const store = await flagStore(env);
  return {
    reach: flagReach(actor),
    types: Object.entries(TYPE_FLAGS).map(([style, key]) => ({ style, key, ...flagRow(store, key, now, null), label: FL.WORK_NAMES[style] })),
  };
}

/** [FLAG-21]~[FLAG-33] /api/admin/flags, /flags/:key(/force|/conditions|/thanks), /checks/:name, /policy, /types. */
async function flagRoute(request, env, url, path, method, actor, now) {
  if (path === "/flags" && method === "GET") return json(await flagList(env, actor, now));
  if (path === "/policy" && method === "POST") return setPolicy(request, env, actor, now);
  if (path === "/types" && method === "GET") return json(await typeList(env, actor, now));
  if (path === "/types" && method === "POST") {
    const body = await readJson(request, 2048);
    const key = TYPE_FLAGS[body.type] ?? refuse("type", "type");
    if (typeof body.show !== "boolean") refuse("show", "show");
    const res = await setFlagForce(new Request(request.url, { method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ force: body.show ? null : "off", why: body.why ?? "" }) }), env, actor, key, now);
    return res.status === 200 ? json(await typeList(env, actor, now)) : res;
  }
  let m = path.match(/^\/checks\/([a-z0-9.]{3,60})$/);
  if (m && method === "POST") return setFlagCheck(request, env, actor, m[1], now);
  m = path.match(/^\/flags\/([A-Za-z0-9._]{2,50})(\/force|\/conditions|\/range|\/thanks)?$/);
  if (!m || !flagDef(m[1])) return json({ error: "not_found" }, 404);
  if (method === "GET" && !m[2]) return flagDetail(env, actor, m[1], now);
  if (method === "POST" && m[2] === "/force") return setFlagForce(request, env, actor, m[1], now);
  if (method === "POST" && m[2] === "/conditions") return setFlagConditions(request, env, actor, m[1], now);
  if (method === "POST" && m[2] === "/range") return setFlagRange(request, env, actor, m[1], now);
  if (method === "POST" && m[2] === "/thanks" && m[1] === "billing") return setThanks(request, env, actor, now);
  return json({ error: "not_found" }, 404);
}

/** [ADMUX-27][FLAG-28] The home screen's part: legal flags on for less than 24 hours, and how many flags want a look. */
export async function flagOverview(env, actor, now = Date.now(), given = null) {
  if (!can(actor, "flags")) return {};
  const store = given ?? (await flagStore(env));
  const rows = flagKeys(store).map((k) => flagRow(store, k, now, null));
  const due = rows.filter((r) => r.reviewDue || r.warnings.length || r.sunsetDue
    || (r.state === "waiting" && r.checklist.every((c) => c.met !== false || c.type === "manual"))).length;
  return {
    flagAlerts: rows.filter((r) => r.alert && r.state !== "forced_off").map((r) => ({ key: r.key, name: r.name, since: r.onSince, reason: r.reason })),
    // flagsWaiting: the same number by the name AN's 출시 hub badge reads.
    flagsDue: due, flagsWaiting: due,
  };
}
/**
 * 1.28 통합 [ADMUX-40][FLAG-30] 분석 › 기능별 효과: every flag with the share of phones it was on for (stats.more, last
 * 7 days) and, once the server saw it turn on or off, problem reports and server errors 7 days before and after,
 * the same numbers the flag's own page shows (flagDetail), for all flags in one trip.
 */
async function flagEffects(env, actor, now) {
  const store = await flagStore(env);
  const shares = await flagShares(env, now);
  const rows = flagKeys(store).map((k) => flagRow(store, k, now, shares));
  const measured = rows.filter((r) => store.state.get(r.key)?.since);
  const pivots = measured.map((r) => store.state.get(r.key).since);
  const lo = pivots.length ? Math.min(...pivots) - 7 * DAY : now;
  const hi = pivots.length ? Math.max(...pivots) + 7 * DAY : now;
  const [bugs, errors, flips] = pivots.length ? (await batch(env, [
    stmt(env, "SELECT created_at, info FROM inquiries WHERE kind IN ('bug', 'problem') AND created_at >= ? AND created_at < ?", lo, hi),
    stmt(env, "SELECT key, count FROM counters WHERE key >= ? AND key <= ?", `errors|${kstDay(lo)}`, `errors|${kstDay(hi)}`),
    // AU 1.29 [NXT-07]: whether `since` is a turn the server saw (flagTick's audit line) or only when it first looked.
    stmt(env, "SELECT target, at FROM audit WHERE action IN ('flag_auto_on', 'flag_auto_off') AND at >= ?", lo + 7 * DAY),
  ])).map((r) => r.results) : [[], [], []];
  const around = (pivot) => {
    const b = bugs.filter((x) => x.created_at >= pivot - 7 * DAY && x.created_at < pivot + 7 * DAY);
    const e = errors.filter((x) => x.key.slice(7) >= kstDay(pivot - 7 * DAY) && x.key.slice(7) <= kstDay(pivot + 7 * DAY));
    return { pivot: iso(pivot), days: 7, bugsBefore: b.filter((x) => x.created_at < pivot).length, bugsAfter: b.filter((x) => x.created_at >= pivot).length,
      errorsBefore: e.filter((x) => x.key.slice(7) < kstDay(pivot)).reduce((n, x) => n + x.count, 0),
      errorsAfter: e.filter((x) => x.key.slice(7) >= kstDay(pivot)).reduce((n, x) => n + x.count, 0) };
  };
  return {
    serverTime: iso(now), statsPhones: shares.base, flagsReach: can(actor, "flags"),
    flags: rows.map((r) => ({ key: r.key, name: r.name, group: r.group, risk: r.risk, state: r.state, on: r.on, onShare: r.onShare,
      compare: store.state.get(r.key)?.since ? { ...around(store.state.get(r.key).since), split: bugSplit(store, r, bugs, flips) } : null })),
  };
}
// AM 1.28 끝

// AU 1.29
/**
 * [NXT-07] AM 4-3: the problem reports of the same 7 days before and after, split by whether the flag was on for the
 * phone that sent them, as far as the server can tell from what it already keeps (처리방침 3.2 1-3항: the time the
 * report came and the app version that goes with every question), nothing new asked of the phone:
 * - off: sent before the server saw the flag turn on (after it saw it turn off), or from an app version outside the
 *   flag's range (the override's minApp/maxApp, an `app` condition);
 * - on: sent while it was on, from a version inside the range, and nothing about the phone decides it;
 * - maybe: as on, but a condition the server cannot see (동의·프로필·근무 유형·나눠 켜기) decides it per phone;
 * - unknown: no app version on the question (an old app, or a question typed by mail);
 * - unseen: sent before the server first looked at the flag (its first look is not a turn, so the state before is unknown).
 * The 기능 상태 lines a user may attach in the 점검 기록 are not read for this: 1-3항 keeps them for answering that question.
 */
function bugSplit(store, row, bugs, flips = []) {
  const st = store.state.get(row.key);
  const since = st?.since;
  const nowOn = st?.on_now === 1;
  const turned = flips.some((f) => f.target === row.key && f.at === since);
  const def = flagDef(row.key);
  const o = store.overrides.get(row.key) ?? null;
  const conds = o?.conditions ?? def?.conditions ?? [];
  const lo = o?.minApp ?? def?.minApp ?? null;
  const hi = o?.maxApp ?? def?.maxApp ?? null;
  const appConds = conds.filter((c) => c.type === "app");
  const inRange = (app) => (lo == null || app >= lo) && (hi == null || app <= hi)
    && appConds.every((c) => (c.min == null || app >= c.min) && (c.max == null || app <= c.max));
  const varies = row.checklist.some((x) => x.met === "phone");
  const out = { on: 0, maybe: 0, off: 0, unknown: 0, unseen: 0 };
  for (const b of bugs) {
    if (since == null || b.created_at < since - 7 * DAY || b.created_at >= since + 7 * DAY) continue;
    if (b.created_at < since && !turned) { out.unseen++; continue; }
    const app = appCode(b.info);
    if (app == null) { out.unknown++; continue; }
    const onThen = b.created_at >= since ? nowOn : !nowOn;
    if (!onThen || !inRange(app)) out.off++;
    else if (varies) out.maybe++;
    else out.on++;
  }
  return out;
}

/** [NXT-07] The versionCode in a question's app line ("1.28.0 (39)" → 39), or null. */
function appCode(info) {
  let app;
  try { app = JSON.parse(info ?? "{}").app; } catch { return null; }
  const m = /\((\d+)\)\s*$/.exec(typeof app === "string" ? app : "");
  return m ? Number(m[1]) : null;
}
// AU 1.29 끝

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff", ...headers },
  });
}

async function readJson(request, maxBytes) {
  if (!(request.headers.get("content-type") || "").startsWith("application/json")) throw new Refuse("content_type", "body");
  if (Number(request.headers.get("content-length") || 0) > maxBytes) throw new Refuse("too_large", "body", 413);
  const text = await request.text();
  if (new TextEncoder().encode(text).length > maxBytes) throw new Refuse("too_large", "body", 413);
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    throw new Refuse("bad_json", "body");
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new Refuse("bad_json", "body");
  return body;
}

const iso = (ms) => new Date(ms).toISOString();

function randomId(length) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  let out = "";
  // 256 % 57 leaves a small bias; skipping the top values keeps every character equally likely.
  for (const b of bytes) out += b < 228 ? ID_CHARS[b % 57] : "";
  return out.length >= length ? out.slice(0, length) : out + randomId(length - out.length);
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Compares digests, so the time taken says nothing about how much of the password was right. */
async function sameSecret(given, secret) {
  const [a, b] = await Promise.all([sha256(`pw|${given}`), sha256(`pw|${secret}`)]);
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
