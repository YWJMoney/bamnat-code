import { FLAG_KEYS, configFlags, configPolicy, flagDef, flagStore, legacyFlags, recordLaunch, serverEval } from "./flags.js";
import { OWNER, Refuse, all, allow, answer, audit, batch, deviceHash, deviceId, given, iso, item, json, network, one, overLimit, randomId, readJson, refuse, run, salt, sha256, stmt, tooMany } from "./index.js";
import { DAY, HOUR } from "./units.js";
import { kstDay, noticeRows, noticeView } from "./usage.js";
// ---- 정식 출시, 프로 이용권, 관리자 휴대폰, 구매 확인 [MON-02][MON-03][MON-06][MON-08][MON-09][MON-10] ----

export const MONEY = {
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
export const FEATURE = /^[A-Z][A-Z_]{1,39}$/;

export async function launchState(env) {
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
export async function config(env) {
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

export async function launchView(env) {
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

export async function installOf(request) {
  const id = deviceId(request.headers.get("x-device"));
  if (!id) throw new Refuse("device", "device");
  return deviceHash(id);
}

/** [MON-08][MON-09] What this install has: its passes, its admin link and whether it holds a recovery code. */
export async function passes(env, dev, now) {
  const [rows, link, code, s] = await Promise.all([
    all(env, "SELECT months, status, paid_at, deferred, rule_id FROM rewards WHERE device = ?", dev),
    one(env, "SELECT status, admin_name, admin, previews FROM admin_devices WHERE device = ?", dev),
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
    // S 1.32 [SECX-01] previews: the flags the developer app turned on for this phone only (approved phones only).
    // The phone shows nothing about flags itself any more; the 기능 상태 card and its switches are gone.
    admin: link ? { status: link.status, name: link.admin_name, owner: link.admin === OWNER.id,
      previews: link.status === "approved" ? previewKeys(link.previews) : [] } : null,
    code: Boolean(code),
    checkedAt: iso(now),
  };
}

export const passCode = () => randomId(16).toUpperCase().replace(/(.{4})(?=.)/g, "$1-");
export const passCodeHash = async (code) => (await sha256(`pass|${String(code).toUpperCase().replace(/[^A-Z0-9]/g, "")}`)).slice(0, 40);

/** [MON-08] /api/passes, /api/passes/code, /api/passes/claim and /api/admin-link, all for one install. */
export async function money(request, env, path) {
  const method = request.method;
  const now = Date.now();
  const dev = await installOf(request);
  if (path === "/api/passes" && method === "GET") return json(await passes(env, dev, now));
  if (path === "/api/passes/code" && method === "POST") {
    { const limitHit = await overLimit(env, now, [["device", `passcode|${dev}`, MONEY.codesPerDay, DAY]]); if (limitHit) return limitHit; }
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
    { const limitHit = await overLimit(env, now, [["network", `claim|${net.day}`, MONEY.claimsPerHour, HOUR]]); if (limitHit) return limitHit; }
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

export const INVITE = {
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
export const INVITE_LIMITS = { friends: [1, 50], days: [1, 60], months: [1, 12], capMonths: [0, 120] };
export const INVITE_KEYS = { friends: "invite_friends", days: "invite_days", months: "invite_months", capMonths: "invite_cap_months" };
export const INVITE_SETTINGS_SQL = `SELECT key, value FROM settings WHERE key IN (${Object.values(INVITE_KEYS).map((k) => `'${k}'`).join(", ")})`;

/** [INVITE-08] The invite rules from their settings rows, each within its limits, else its default. */
export function readInviteSettings(rows) {
  return Object.fromEntries(Object.entries(INVITE_KEYS).map(([name, key]) => {
    const n = Number(rows.find((r) => r.key === key)?.value);
    const [lo, hi] = INVITE_LIMITS[name];
    return [name, Number.isInteger(n) && n >= lo && n <= hi ? n : INVITE[name]];
  }));
}

export const inviteSettings = async (env) => readInviteSettings(await all(env, INVITE_SETTINGS_SQL));

/** Days a friend has to come back in, from the day it told the server: the period and 30 more. */
export const inviteDeadline = (set) => (set.days + INVITE.graceDays) * DAY;

// No 0/O, 1/I: a code read out or typed by hand. 32 letters, so a random byte maps evenly.
export const INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export const INVITE_CODE = /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/;
/** What the app sends as the phone mark: SHA-256 of the app-scoped Android ID, never the id itself. */
export const PHONE_MARK = /^[0-9a-f]{64}$/;

export const inviteCode = () => [...crypto.getRandomValues(new Uint8Array(8))].map((b) => INVITE_CHARS[b % 32]).join("");

/** A code as a person may type it: "abcd-2345" is ABCD2345. */
export const inviteCodeOf = (v) => {
  const c = typeof v === "string" ? v.toUpperCase().replace(/[^A-Z0-9]/g, "") : "";
  return INVITE_CODE.test(c) ? c : null;
};

/** The phone mark as the server keeps it: hashed again with the server's secret salt. */
export async function phoneMark(env, v) {
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
export const rewardWhy = (ruleId) => (ruleId === "invite" ? "invite" : ruleId === "rate" ? "rate" : ruleId ? "rule" : "idea");

/** Whole days from one Korean date ("2026-10-25") to another. */
export const dayGap = (from, to) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY);

/**
 * [INVITE-02] /i/<code>: a small page that sends an Android phone on to the Play listing with the code, and
 * shows the code elsewhere. It keeps nothing and does not look the code up, so it tells nobody which codes exist.
 */
export function invitePage(raw) {
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
export async function inviteMonths(env, dev, since = 0) {
  const r = await one(env, `SELECT COALESCE(SUM(months), 0) AS m FROM rewards WHERE device = ? AND rule_id = 'invite' AND status = 'paid'
    AND paid_at > ?`, dev, since);
  return r.m;
}

/** [INVITE-08] The rules as the app says them, before anything is sent. */
export const inviteRules = (set) => ({ need: set.friends, days: set.days, months: set.months, capMonths: set.capMonths });

/**
 * [INVITE-05][INVITE-08] What the inviter sees: the code and link, the rules, friends counted toward the next
 * months ([progress] of [need]), friends still in their period ([waiting]), friends counted in all and the months
 * they gave. "3/5명 · 2명은 사용 기간 채우는 중".
 */
export async function inviteView(env, dev, origin, now) {
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
export async function settleInvites(env, inviter, set, now) {
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
export async function settleAllInvites(env, set, now) {
  const due = await all(env, `SELECT inviter FROM invite_uses WHERE status = 'qualified' GROUP BY inviter HAVING COUNT(*) >= ? LIMIT 200`, set.friends);
  for (const d of due) await settleInvites(env, d.inviter, set, now);
}

/**
 * [INVITE-02][INVITE-03] /api/invites, /api/invites/code, /api/invites/use and /api/invites/seen, all for one install;
 * [INVITE-08] /api/invites/rules for anyone.
 */
export async function invites(request, env, path, url) {
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
    { const limitHit = await overLimit(env, now, [["device", `invitecode|${dev}`, INVITE.codesPerDay, DAY]]); if (limitHit) return limitHit; }
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
      return tooMany("device", HOUR);
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
    { const limitHit = await overLimit(env, now, [["device", `inviteseen|${dev}`, INVITE.seenPerDay, DAY]]); if (limitHit) return limitHit; }
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
export async function inviteSettingsRoute(request, env, method, now) {
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
export async function rewardCounts(env, withInvites, now) {
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
export async function linkCode(env, actor, now) {
  { const limitHit = await overLimit(env, now, [["device", `linkcode|${actor.id}`, MONEY.linkCodesPerHour, HOUR]]); if (limitHit) return limitHit; }
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

export async function linkPhone(request, env, dev, now) {
  const body = await readJson(request, 1024);
  const net = await network(request, env, now);
  if (!(await allow(env, `link|${net.day}`, MONEY.linkTriesPerHour, HOUR, now)) ||
      !(await allow(env, `link|${dev}`, MONEY.linkTriesPerHour, HOUR, now))) {
    return tooMany("device", HOUR);
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

export async function phoneList(env) {
  const rows = await all(env, "SELECT id, admin, admin_name, status, created_at, approved_at, seen_at FROM admin_devices ORDER BY created_at DESC");
  return {
    devices: rows.map((d) => ({
      id: d.id, admin: d.admin, adminName: d.admin_name, status: d.status, createdAt: iso(d.created_at),
      approvedAt: d.approved_at ? iso(d.approved_at) : null, seenAt: d.seen_at ? iso(d.seen_at) : null,
    })),
    perAdmin: MONEY.phonesPerAdmin,
  };
}

/** S 1.32 [SECX-01] Stored 미리 보기 keys, kept only while the flag still exists. */
export function previewKeys(text) {
  let a;
  try { a = JSON.parse(text ?? "[]"); } catch { a = []; }
  return Array.isArray(a) ? [...new Set(a.filter((k) => typeof k === "string" && FLAG_KEYS.includes(k)))] : [];
}

/** S 1.32 [SECX-01] The phones this admin may set 미리 보기 on: every phone for the owner, else only their own. */
export async function myPhones(env, actor) {
  const rows = actor.owner
    ? await all(env, "SELECT id, admin, admin_name, status, previews, seen_at FROM admin_devices ORDER BY created_at DESC")
    : await all(env, "SELECT id, admin, admin_name, status, previews, seen_at FROM admin_devices WHERE admin = ? ORDER BY created_at DESC", actor.id);
  const edit = actor.owner || (actor.perms?.flags === "edit");
  return {
    edit,
    phones: rows.map((d) => ({ id: d.id, adminName: d.admin_name, mine: d.admin === actor.id, status: d.status,
      previews: previewKeys(d.previews), seenAt: d.seen_at ? iso(d.seen_at) : null })),
    flags: FLAG_KEYS.map((k) => ({ key: k, name: flagDef(k)?.name ?? k, group: flagDef(k)?.group ?? "" })),
  };
}

/**
 * S 1.32 [SECX-01] Sets which flags one admin phone sees before everyone. Only the owner, or an admin with 기능 관리
 * 바꾸기 on a phone tied to themselves; the phone picks the list up at its next 이용권 check.
 */
export async function phonePreviews(request, env, actor, id, now) {
  const body = await readJson(request, 4096);
  const d = await one(env, "SELECT * FROM admin_devices WHERE id = ?", id);
  if (!d) return json({ error: "not_found" }, 404);
  if (!actor.owner && !(actor.perms?.flags === "edit" && d.admin === actor.id)) return json({ error: "forbidden" }, 403);
  if (!Array.isArray(body.keys) || body.keys.length > FLAG_KEYS.length) refuse("keys", "keys");
  const keys = [...new Set(body.keys)];
  if (keys.some((k) => typeof k !== "string" || !FLAG_KEYS.includes(k))) refuse("keys", "keys");
  await run(env, "UPDATE admin_devices SET previews = ? WHERE id = ?", JSON.stringify(keys), id);
  await audit(env, now, "device_previews", d.admin_name, keys.join(",") || "없음");
  return json(await myPhones(env, actor));
}

export async function setPhone(env, id, action, now) {
  const d = await one(env, "SELECT * FROM admin_devices WHERE id = ?", id);
  if (!d) return json({ error: "not_found" }, 404);
  if (action === "approve") await run(env, "UPDATE admin_devices SET status = 'approved', approved_at = ? WHERE id = ?", now, id);
  else await run(env, "DELETE FROM admin_devices WHERE id = ?", id);
  await audit(env, now, action === "approve" ? "device_approve" : "device_remove", d.admin_name);
  return json(await phoneList(env));
}

// ---- 구매 확인과 체험 전환 동의 [MON-03][MON-06] ----

export const PLAY = {
  scope: "https://www.googleapis.com/auth/androidpublisher",
  token: "https://oauth2.googleapis.com/token",
  api: "https://androidpublisher.googleapis.com/androidpublisher/v3/applications",
  states: {
    SUBSCRIPTION_STATE_ACTIVE: "ACTIVE", SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "GRACE", SUBSCRIPTION_STATE_CANCELED: "CANCELED",
    SUBSCRIPTION_STATE_ON_HOLD: "ON_HOLD", SUBSCRIPTION_STATE_PAUSED: "PAUSED", SUBSCRIPTION_STATE_EXPIRED: "EXPIRED",
    SUBSCRIPTION_STATE_PENDING: "PENDING", SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED: "EXPIRED",
  },
};
export const PRODUCT = /^[a-z0-9_.]{1,40}$/;
/** [SRV-07] How long before a trial turns paid an unanswered one is stopped, how long a first phone keeps its token, rechecks an hour. */
export const TRIAL_STOP_BEFORE = 2 * HOUR;
export const REBIND_AFTER = 7 * DAY;
export const RECHECK_BATCH = 20;
export const playTokens = new WeakMap();

/** The service account the owner pasted as the secret PLAY_SERVICE_ACCOUNT, or null; its key is never shown. */
export function playReady(env) {
  if (typeof env.PLAY_SERVICE_ACCOUNT !== "string" || !env.PLAY_SERVICE_ACCOUNT.trim()) return null;
  try {
    const a = JSON.parse(env.PLAY_SERVICE_ACCOUNT);
    return typeof a.client_email === "string" && typeof a.private_key === "string" ? a : null;
  } catch {
    return null;
  }
}

export const b64url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
export const b64urlText = (text) => b64url(new TextEncoder().encode(text));

/** An OAuth token for the Play Developer API from the service account (JWT bearer, RS256), kept until near its end. */
export async function playToken(env, account, now, scope = PLAY.scope) {
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

export async function playCall(env, method, path, body) {
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
export async function purchase(request, env, dev, now) {
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
export async function consent(request, env, dev, now) {
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
  { const limitHit = await overLimit(env, now, [["device", `consent|${dev}`, 20, DAY]]); if (limitHit) return limitHit; }
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
export async function deferFor(env, dev, months, now) {
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

