// [MKT-09] Read-only schedule links for 밤낮. Cloudflare Worker + KV, no accounts.
//
// POST   /api/share            body: schedule JSON  -> { id, url, deleteToken, expiresAt }
// PUT    /api/share/:id        same body, header X-Delete-Token -> { id, url, expiresAt }
//                              [SHR-12] without ttlDays the link keeps its end date; with it the link stays open that long from now
// DELETE /api/share/:id        header X-Delete-Token -> 204
// GET    /api/share/:id        [SHR-02] the schedule for the app's 받은 달력 -> { schedule, expiresAt, updatedAt }
//                              [SHR-12] with an ETag; If-None-Match with it answers 304 while nothing changed
// GET    /s/:id                the schedule as a web page (noindex, no scripts)
// GET    /s/:id.ics            [FEAT-15] the same days as a calendar to subscribe to, only when the sharer turned it on
// POST   /s/:id/tired          1.39 [IOS-04] the page's own '오늘 피곤해요' form (iPhone or no app): the same once-a-day count as the app
// GET    /apple-touch-icon.png 1.39 [IOS-05] the icon an iPhone puts on its home screen for a page added there
// GET    /.well-known/assetlinks.json   lets Android open /s/ links in the app (ANDROID_CERT_SHA256)
// POST   /api/share/:id/report body {reason} -> 204   [SRV-05] the app's 신고
// POST   /s/:id/report         the page's own form (no script) -> a thank-you page
// GET    /api/admin/reports    [SRV-05] reported links, most reported first (Bearer SHARE_ADMIN_TOKEN); 1.39 [CARE-07] with kind
//                              (s 근무표, b 병동 표, a 근무 신청, v 결원, w 바꾸기, k 근로계약서, c 출퇴근)
// POST   /(b|a|v|w|k|c)/:key/report  1.39 [CARE-07] the team pages' own form -> a thank-you page
// DELETE /api/admin/link/:kind/:id    1.39 [CARE-08] takes a team link down: its content goes at once, its pages say why (410)
// GET    /api/admin/link/:kind/:id    1.39 [CARE-08] the words in it, for the operator to judge a report (no keys or tokens)
// DELETE /api/admin/share/:id  takes a link down: the page then says it was removed (410)
// GET    /api/admin/stats?days=  links made, pages seen, reports and take-downs per day [STAT-04]
// PUT    /api/admin/flags      [SRV-08] {createOff}: the company server's 근무표 링크 새로 만들기 switch
// POST   /api/groups           [SHR-14] {link, token} -> { code, expiresAt }: a 6-letter 모임 코드 holding that link
// POST   /api/groups/:code     {link, token} -> { code, expiresAt, links }: adds one's own link to a group
// DELETE /api/groups/:code/:id header X-Delete-Token -> 204: takes one's own link out
// GET    /api/groups/:code     -> { code, expiresAt, links: [id…] }: the links still open, nothing else
// POST   /api/clock            1.38 [P3-B-11] {name, place, ttlDays} -> { id, url, token, expiresAt }: a staff member's 출퇴근 링크
// GET    /api/clock/:id        header X-Delete-Token -> { punches: [{k:"in"|"out", at}], expiresAt }: the boss's app reads them
// DELETE /api/clock/:id        header X-Delete-Token -> 204: 퇴사·끊기
// GET    /c/:id                the staff member's page: 출근·퇴근 buttons (a plain form, no script, no location)
// POST   /c/:id/punch          form k=in|out -> the page again, with the time written
// POST   /api/ask              1.38 [P3-L-13] {team, month, rules, shifts, members:[name]} -> { id, token, members:[{name,key,url}] }:
//                              a 근무 신청 round, a link per member (병동 종이 신청표 "평일 2명, 주말 3명")
// GET    /api/ask/:id          header X-Delete-Token -> { wishes:[{m, d, k, s?, at}], notes:[{m, note}], seen:[m] }
// PUT    /api/ask/:id          header X-Delete-Token, {closesAt?, closed?} -> 204: 마감을 바꾸거나 닫기
// DELETE /api/ask/:id          header X-Delete-Token -> 204: the round and every member's requests are gone
// GET    /a/:key[?d=YYYY-MM-DD] the member's page: the month, each day's room ("1/2"), their own requests (a plain form, no script)
// POST   /a/:key/pick          form d, k (OFF|LEAVE|HALF|EDU|WORK|AVOID_NIGHT|SHIFT|none), s -> the page again
// POST   /a/:key/note          form note -> the page again
// POST   /api/call             1.38 [P3-X-01] {team, title, detail, people:[name], hours} -> { id, token, people:[{name,key,url}] }:
//                              a yes/no ask to some people (결원 메우기: "12/7 나이트 대신 들어갈 수 있나요?")
// GET    /api/call/:id         header X-Delete-Token -> { answers:[{p, a:"yes"|"no", at}], seen:[p], taken }
// PUT    /api/call/:id         header X-Delete-Token, {taken: p|null, closed} -> 204: who the leader chose; the pages say so
// DELETE /api/call/:id         header X-Delete-Token -> 204
// POST   /api/swap             1.38 [P3-X-02] {team, month, people:[name], labels:[[label per day]], ok:{p:{day:[q]}}}
//                              -> { id, token, expiresAt, people:[{name,key,url}] }: 바꾸기 요청, who may trade which day worked out on the phone
// GET    /api/swap/:id         header X-Delete-Token -> { asks:[{p, q, d, state, at}], seen:[p], closed }
// PUT    /api/swap/:id         header X-Delete-Token, {labels?, ok?, closed?, decide?:[{p, d, state:"done"|"denied"}]} -> 204
// DELETE /api/swap/:id         header X-Delete-Token -> 204
// GET    /w/:key               a member's page: asks to them, theirs, and the days they may trade; POST /w/:key/ask|cancel|reply
// POST   /api/sign             1.38 [P3-X-06] {place, staff, title, items:[{t, b}], days} -> { id, token, url, expiresAt }: a 근로계약서 to
//                              read and sign by typing one's name. Kept only while it is being signed (30 days at most); the
//                              signed copy lives on the boss's phone and in the PDF.
// GET    /api/sign/:id         header X-Delete-Token -> { seenAt, signed:{name, at}|null, expiresAt }
// DELETE /api/sign/:id         header X-Delete-Token -> 204: the boss's app takes it down once it has the signature
// GET    /k/:key               the staff member's page: the contract, a name box and 동의; POST /k/:key/sign form name, agree=1
// GET    /api/share/:id/seen   1.38 [P3-L-15] header X-Delete-Token -> { seenAt, updatedAt }: when the link was last opened since its
//                              last change (the page or the app). The first ask turns it on for that link; nothing but the time is kept.
// POST   /api/board            1.38 [P3-L-15] {team, month, names:[name], labels:[[label per day]], colors:{label:"#hex"}} -> { id, token, url,
//                              expiresAt }: 병동 전체 보기, the whole month's table and each day's count per shift, when the leader turns it on
// PUT    /api/board/:id        header X-Delete-Token, same body -> 204 · DELETE /api/board/:id -> 204 · GET /b/:id the page
// POST   /api/tired            1.38 [P3-L-18] {team} -> { id, token }: 피곤해요 합계 받기, a team's box of counts per day (no names)
// GET    /api/tired/:id        header X-Delete-Token -> { min, days:[{d, n|null}] }: the last 7 days, n null under LIMITS.tiredMin
// DELETE /api/tired/:id        header X-Delete-Token -> 204
// PUT    /api/share/:id/tired  header X-Delete-Token, {box: id|null} -> 204: the leader ties a member's link to the box (or unties it)
// POST   /api/share/:id/tired  the member's app, once a day (KST): adds one to today's count. A day's repeat is marked by a
//                              hash of the link and the day for 2 days; the leader never sees who.
// GET    /v/:key               the asked person's page; POST /v/:key/answer form a=yes|no -> the page again
// POST   /spread/hide          1.39 [SPREAD-01] form back=<page path>: the '나도 써 보기' line stays closed in this browser (a cookie, nothing kept here)
//                              The admin paths answer 404 until SHARE_ADMIN_TOKEN (20+ characters) is set;
//                              the company server's 개발자 앱 calls them with the same token.
//
// Only what the page shows is stored: dates, shift names, colours and times, and since v2 [SHR-01]
// only what the sharer ticked of 휴가 종류, 연장·휴무일 근무, 메모 and 급여, and since 1.13 [SHR-11] 일정 (a
// freelancer's job titles and times) and the money in the sharer's own kind (급여, 일감 수입 or 매출). The server keeps no IP
// addresses (the rate limit key is hashed and expires within the hour). Each link deletes itself
// when it expires.

import { SERVER_VERSION } from "./version.js";
// 2.0.0 [RVD-28] SD-08: 근무 종류 한 규칙표, written by core ShiftRulesExportTest (the company server reads the same file).
import { SHIFT_RULES } from "./shift-rules.js";

export const LIMITS = {
  // 1.37 [SHR-31][SHR-32] Room for a 테마 (32 KB) and 근무 설정 (24 KB) next to a company setup (24 KB) and the days.
  bodyBytes: 128 * 1024,
  days: 200,
  // [RVD-28] SD-08 How many kinds of day, from core ShiftRules (a name and a 근무 글자 too: nameOf, shortOf).
  types: SHIFT_RULES.types,
  title: 30,
  // 연차, 반차 … up to "0.75일 무급 휴가".
  leave: 12,
  // [PROV-04] 180 days and a year since 1.20 (프로 in the app).
  ttlDays: [7, 30, 90, 180, 365],
  createsPerHour: 20,
  // 1.36 [SEC-02] Reads a network may make in an hour: a whole plant on one Wi-Fi opening shared calendars stays far under it.
  readsPerHour: 3000,
  groupReadsPerHour: 60,
  // [SHR-01] What a sharer may add to v2 links.
  memo: 80,
  extras: 4,
  extraName: 10,
  payMonths: 6,
  payLabel: 10,
  payAmount: 10_000_000_000,
  // [SHR-11] A freelancer's jobs on a day: title and time only.
  jobs: 4,
  jobName: 12,
  // [SHR-20] A company setup file as the app writes it, without amounts.
  setup: 24 * 1024,
  // 1.37 [SHR-31][SHR-32] A link's 테마 and 근무 설정, each a recipe as the app writes it (RecipeCodec, core ShareKit).
  look: 32 * 1024,
  kit: 24 * 1024,
  kitPieces: 3,
  // [SRV-05] Reports: a few an hour from one caller, one per link and caller a day.
  reportsPerHour: 10,
  // A taken-down link answers "removed" this long, then is gone like any other.
  removedDays: 90,
  // [STAT-04] Day counters are kept this long, and written at most every few minutes per Worker.
  statsDays: 400,
  statsFlushMs: 5 * 60 * 1000,
  // [SHR-13] The words an operator gave when taking a link down.
  removedReason: 200,
  // [SHR-14] A 모임 코드: how many links, how many groups one caller may start or join in an hour.
  groupLinks: 12,
  groupsPerHour: 10,
  // [SRV-08] .ics: calendar apps may keep it this long.
  icsMaxAge: 300,
  // 1.38 [P3-B-11] 출퇴근 링크: the punches kept (about two months of two a day), the same button again within this many seconds
  // counts once, and the presses one network may make in an hour (a shop's Wi-Fi with a few people stays far under it).
  clockPunches: 160,
  clockSameSeconds: 60,
  clockPerHour: 60,
  clockTtlDays: 400,
  clockName: 20,
  clockPlace: 30,
  // 1.38 [P3-L-13] 근무 신청 받기: a ward, its people, a month of requests each, a line each; gone 60 days after it opens at most.
  askMembers: 80,
  askName: 20,
  askTeam: 30,
  askShifts: 12,
  askShiftLabel: 4,
  askNote: 60,
  askTtlDays: 60,
  askPerHour: 300,
  // 1.38 [P3-X-01] 결원 메우기: a few lines of what is asked, to at most a ward; open a week at most.
  callPeople: 40,
  callTitle: 40,
  callDetail: 120,
  callHours: 168,
  // 1.38 [P3-X-06] 근로계약서 링크: the items the app writes (StaffContract), a month at most.
  signItems: 16,
  signTitle: 40,
  signBody: 600,
  signDays: 30,
  // 1.38 [P3-L-15] 병동 전체 보기: a ward's month, gone 60 days after the month ends.
  boardPeople: 80,
  boardDaysAfter: 60,
  // 1.38 [P3-L-18] 피곤해요 팀 합계: counts per day only, shown to the leader from this many people up, kept this many days.
  tiredMin: 3,
  tiredDays: 30,
  tiredBoxDays: 120,
};

/** [SRV-05] Why someone reports a link (the page and the app offer the same list). */
export const REPORT_REASONS = {
  personal: "다른 사람의 개인정보",
  abuse: "욕설·괴롭힘",
  spam: "광고·스팸",
  illegal: "불법 정보",
  other: "그 밖의 문제",
};

/** 1.39 [CARE-07] 팀·사장님 링크(계약서·결원 등)의 신고 이유: 사기 의심이 맨 앞. */
export const TEAM_REPORT_REASONS = { scam: "돈·계좌·연락을 요구해요(사기 의심)", ...REPORT_REASONS };

/**
 * 1.39 [CARE-09] 팀·사장님 링크에 들어가는 글(계약서 항목, 결원 글, 팀 이름, 이름 등)에서 받지 않는 것: 전화번호, 링크·주소창 주소,
 * 계좌번호 모양(숫자 11자리 이상), 메신저로 옮기기. 앱이 만드는 계약서·결원 글에는 이런 것이 없어서, 앱을 거치지 않고 우리 주소로
 * 가짜 계약서("보증금은 아래 계좌로")를 만드는 길만 막혀요. 사업자등록번호(10자리)·날짜·시각·금액은 그대로예요.
 */
const SCAM_TEXT = [
  ["phone", /(\+?82[\s.-]?0?|0)1[016789][\s.-]?\d{3,4}[\s.-]?\d{4}|(\+?82[\s.-]?0?|0)(2|[3-6][1-5]|70)[\s.-]?\d{3,4}[\s.-]?\d{4}/],
  ["link", /https?:\/\/|www\.|\b[a-z0-9-]{2,}\.(com|net|kr|co|me|io|ly|gg|xyz|app|link|site|shop)\b|t\.me\//i],
  ["account", /(?<![\d:.])\d{2,6}(?:[\s-]\d{2,8}){1,4}(?![\d:.])/],
  ["messenger", /오픈\s*채팅|오픈\s*톡|텔레그램|카톡\s*(아이디|id)|카카오톡\s*(아이디|id)|라인\s*아이디|위챗|whats\s*app/i],
];
const digitsIn = (s) => s.replace(/\D/g, "").length;
/** 1.39 [CARE-09] 걸러내기는 1.39 앱(versionCode 53)부터만 (사용자 2026-11-08 "1.39 앱에서만 걸리게"). 1.38 이하 앱은 헤더가 없어 예전처럼 받고,
 * 1.39 앱은 `X-Bamnat-App: <versionCode>`를 보내고 거절 이유를 사람 말로 보여 줘요. */
export const SCAM_SINCE_APP = 53;
export const scamChecked = (request) => Number(request.headers.get("x-bamnat-app")) >= SCAM_SINCE_APP;

/** Why one of the strings in [value] (any depth) cannot go on a team page, or null. */
export function scamProblem(value, depth = 0) {
  if (typeof value === "string") {
    for (const [code, re] of SCAM_TEXT) {
      const m = value.match(re);
      if (!m) continue;
      // A number group is an account only when it is long enough (사업자등록번호 123-45-67890 is 10).
      if (code === "account" && digitsIn(m[0]) < 11 && !/\d{11,}/.test(value)) continue;
      return code;
    }
    if (/\d{11,}/.test(value)) return "account";
    return null;
  }
  if (depth > 6 || value == null || typeof value !== "object") return null;
  for (const v of Array.isArray(value) ? value : Object.values(value)) {
    const why = scamProblem(v, depth + 1);
    if (why) return why;
  }
  return null;
}

/**
 * 1.39 [CARE-09] 7차 PC: where in [value] the refused text is, so the app can say whose line it is ("유나 010-… 이름에 전화번호").
 * { why, text (that string, cut to 40), key (its field name, "name"·"title"·"note"…) } or null.
 */
export function scamFind(value, key = null, depth = 0) {
  if (typeof value === "string") {
    const why = scamProblem(value);
    return why ? { why, text: value.trim().slice(0, 40), ...(key ? { key } : {}) } : null;
  }
  if (depth > 6 || value == null || typeof value !== "object") return null;
  for (const [k, v] of Array.isArray(value) ? value.map((x) => [key, x]) : Object.entries(value)) {
    const hit = scamFind(v, k, depth + 1);
    if (hit) return hit;
  }
  return null;
}

/** 1.39 [CARE-09] The 400 a 1.39 app (versionCode 53+) gets for contact words in [value]; null to go on (older apps are not checked). */
export function scamRefusal(request, value) {
  if (!scamChecked(request)) return null;
  const hit = scamFind(value);
  return hit ? json({ error: "contact", ...hit }, 400) : null;
}

/** [SHR-01] The parts a sharer can tick on top of the shifts. */
export const SHOWS = ["leaveKinds", "extras", "memos", "pay", "jobs"];

/**
 * [SHR-20] 1.33 (계획 690~694): who a link is for, as the sharer picked it. A coworker's link may carry the company's setup
 * file (근무 주기·근무 시간·조·잔업 종류·급여 규칙·수당 이름; never 기본급, 입사일, 연차, 알람, 메모 or amounts but a 회사 공통
 * 정액), for the receiving app to put in. 처리방침 3.7.
 */
export const AUDIENCES = ["coworker", "family", "friend"];

/** [SHR-11] What the money in `pay` is: an employee's pay, a freelancer's job income, a shop's sales. */
/** 1.37 [SHR-31] The look pieces a link's 테마 may hold (core BuiltInThemes.LOOK_KINDS). */
export const LOOK_KINDS = ["screenLook", "calView", "calTheme", "memoLook", "moneyLook", "widget", "photoLook", "layout", "cards", "parts", "decor", "tabView"];

/**
 * 1.37 [SHR-32] What a link's 근무 설정 may hold (core ShareKit.KINDS): 근무 꾸미기, 알람 시각 틀 and the 근무 주기 as a company file with
 * no `pay` part. Never 급여 규칙 or an amount: a kit with any other piece, or a 근무 주기 with pay, is refused.
 */
export const KIT_KINDS = ["shiftLook", "alarms", "company"];

export const PAY_KINDS = { pay: "급여", jobs: "일감 수입", sales: "매출" };

/** [SHR-01] Leave as it shows without 휴가 종류 ticked: 연차 as it is, any other kind only by how much of the day is off. */
const PLAIN_LEAVE = ["연차", "반차", "반반차", "0.75일 연차", "휴가", "0.75일 휴가"];

export function plainLeave(label) {
  if (PLAIN_LEAVE.includes(label)) return label;
  // 2.0.0 [RVD-20] HQ-13 An hour's leave of half a day or more ('무급 반차 4.5시간', 설계 4-4절) keeps its hours, without its kind.
  const hourly = /반차 (\d{1,2}(?:\.\d)?시간)$/.exec(label);
  if (hourly) return `반차 ${hourly[1]}`;
  if (label.includes("반반")) return "반반차";
  if (label.includes("반")) return "반차";
  if (label.startsWith("0.75")) return "0.75일 휴가";
  return "휴가";
}

const ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

export default {
  async fetch(request, env, ctx) {
    try {
      return await spread(request, await route(request, env));
    } catch (e) {
      return json({ error: "server" }, 500);
    } finally {
      // [STAT-04] Counts reach KV in batches, so a busy link does not spend the day's writes.
      const due = flushStats(env, false);
      if (ctx?.waitUntil) ctx.waitUntil(due); else await due;
    }
  },
};

/** AdMob authorized seller line for this app (the publisher id is public). */
export const APP_ADS_TXT = "google.com, pub-4920911396563628, DIRECT, f08c47fec0942fa0";

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;
  if ((request.method === "GET" || request.method === "HEAD") && (path.startsWith("/s/") || path.startsWith("/c/") || path.startsWith("/a/") || path.startsWith("/api/ask/") || path.startsWith("/v/") || path.startsWith("/api/call/") || path.startsWith("/w/") || path.startsWith("/api/swap/") || path.startsWith("/k/") || path.startsWith("/b/") || path.startsWith("/api/sign/") || path.startsWith("/api/share/") || path.startsWith("/api/clock/") || path.startsWith("/api/groups/"))) {
    if (!readAllowed(request, "read", LIMITS.readsPerHour)) return tooManyReads(path);
    if (path.startsWith("/api/groups/") && !readAllowed(request, "group", LIMITS.groupReadsPerHour)) return tooManyReads(path);
  }
  // 1.39 [ADM3-41] 관리자 앱 › 서버 상태가 살아 있는지 보는 곳. 아무것도 읽지 않고 살아 있다는 것과 판만 답해요([ADM3-43]).
  if (path === "/api/health" && (request.method === "GET" || request.method === "HEAD")) {
    return new Response(request.method === "HEAD" ? null : JSON.stringify({ ok: true, version: SERVER_VERSION }), { headers: { "content-type": "application/json", "cache-control": "no-store" } });
  }
  if (path === "/api/share" && request.method === "POST") return create(request, env, url);
  if (path === SPREAD_HIDE && request.method === "POST") return spreadHide(request, url);
  if (path === "/api/groups" || path.startsWith("/api/groups/")) return groups(request, env, path);
  if (path.startsWith("/api/admin/")) return admin(request, env, url);
  // 1.39 [CARE-07][CARE-08] 팀·사장님 링크: 페이지의 신고 양식, 운영자가 내린 링크는 이유와 함께 410.
  const teamPage = path.match(/^\/([bavwkc])\/([A-Za-z0-9]{22})(\/[a-z]+)?$/);
  if (teamPage) {
    if (teamPage[3] === "/report" && request.method === "POST") return teamReport(request, env, teamPage[1], teamPage[2]);
    // 1.39 [IOS-06] Who opens the team pages, by kind of phone: a number per day only, never kept with the link.
    if (request.method === "GET" && !teamPage[3] && !previewBot(request)) count(env, `team_${platformOf(request.headers.get("user-agent") || "")}`);
    const down = await teamDown(env, teamPage[1], teamPage[2]);
    // (route's own `page` below is a match, so the page is made outside it.)
    if (down) return teamDownPage(down);
  }
  if (path === "/api/clock" && request.method === "POST") return clockCreate(request, env, url);
  const clockApi = path.match(/^\/api\/clock\/([A-Za-z0-9]{22})$/);
  if (clockApi && request.method === "GET") return clockRead(request, env, clockApi[1]);
  if (clockApi && request.method === "DELETE") return clockDelete(request, env, clockApi[1]);
  const clockPage = path.match(/^\/c\/([A-Za-z0-9]{22})(\/punch)?$/);
  if (clockPage && !clockPage[2] && (request.method === "GET" || request.method === "HEAD")) return clockView(env, clockPage[1], null);
  if (clockPage && clockPage[2] && request.method === "POST") return clockPunch(request, env, clockPage[1]);
  if (path === "/api/ask" && request.method === "POST") return askCreate(request, env, url);
  const askApi = path.match(/^\/api\/ask\/([A-Za-z0-9]{22})$/);
  if (askApi && request.method === "GET") return askRead(request, env, askApi[1]);
  if (askApi && request.method === "PUT") return askChange(request, env, askApi[1]);
  if (askApi && request.method === "DELETE") return askDelete(request, env, askApi[1]);
  const askPage = path.match(/^\/a\/([A-Za-z0-9]{22})(\/pick|\/note)?$/);
  if (askPage && !askPage[2] && (request.method === "GET" || request.method === "HEAD")) return askView(env, askPage[1], url.searchParams.get("d"), null);
  if (askPage && askPage[2] && request.method === "POST") return askPost(request, env, askPage[1], askPage[2].slice(1));
  if (path === "/api/swap" && request.method === "POST") return swapCreate(request, env, url);
  const swapApi = path.match(/^\/api\/swap\/([A-Za-z0-9]{22})$/);
  if (swapApi && request.method === "GET") return swapRead(request, env, swapApi[1]);
  if (swapApi && request.method === "PUT") return swapChange(request, env, swapApi[1], url);
  if (swapApi && request.method === "DELETE") return swapDelete(request, env, swapApi[1]);
  const swapPage = path.match(/^\/w\/([A-Za-z0-9]{22})(\/ask|\/cancel|\/reply)?$/);
  if (swapPage && !swapPage[2] && (request.method === "GET" || request.method === "HEAD")) return swapView(env, swapPage[1], null);
  if (swapPage && swapPage[2] && request.method === "POST") return swapPress(request, env, swapPage[1], swapPage[2].slice(1));
  if (path === "/api/board" && request.method === "POST") return boardCreate(request, env, url);
  const boardApi = path.match(/^\/api\/board\/([A-Za-z0-9]{22})$/);
  if (boardApi && request.method === "PUT") return boardUpdate(request, env, boardApi[1]);
  if (boardApi && request.method === "DELETE") return boardDelete(request, env, boardApi[1]);
  const boardPage = path.match(/^\/b\/([A-Za-z0-9]{22})$/);
  if (boardPage && (request.method === "GET" || request.method === "HEAD")) return boardView(env, boardPage[1]);
  if (path === "/api/tired" && request.method === "POST") return tiredCreate(request, env);
  const tiredApi = path.match(/^\/api\/tired\/([A-Za-z0-9]{22})$/);
  if (tiredApi && request.method === "GET") return tiredRead(request, env, tiredApi[1]);
  if (tiredApi && request.method === "DELETE") return tiredDelete(request, env, tiredApi[1]);
  const tiredLink = path.match(/^\/api\/share\/([A-Za-z0-9]{16,32})\/tired$/);
  if (tiredLink && request.method === "PUT") return tiredTie(request, env, tiredLink[1]);
  if (tiredLink && request.method === "POST") return tiredSay(request, env, tiredLink[1]);
  const tiredForm = path.match(/^\/s\/([A-Za-z0-9]{16,32})\/tired$/);
  if (tiredForm && request.method === "POST") return tiredPress(request, env, tiredForm[1]);
  const seenApi = path.match(/^\/api\/share\/([A-Za-z0-9]{16,32})\/seen$/);
  if (seenApi && request.method === "GET") return seenRead(request, env, seenApi[1]);
  if (path === "/api/sign" && request.method === "POST") return signCreate(request, env, url);
  const signApi = path.match(/^\/api\/sign\/([A-Za-z0-9]{22})$/);
  if (signApi && request.method === "GET") return signRead(request, env, signApi[1]);
  if (signApi && request.method === "DELETE") return signDelete(request, env, signApi[1]);
  const signPage = path.match(/^\/k\/([A-Za-z0-9]{22})(\/sign)?$/);
  if (signPage && !signPage[2] && (request.method === "GET" || request.method === "HEAD")) return signView(env, signPage[1], null);
  if (signPage && signPage[2] && request.method === "POST") return signPost(request, env, signPage[1]);
  if (path === "/api/call" && request.method === "POST") return callCreate(request, env, url);
  const callApi = path.match(/^\/api\/call\/([A-Za-z0-9]{22})$/);
  if (callApi && request.method === "GET") return callRead(request, env, callApi[1]);
  if (callApi && request.method === "PUT") return callChange(request, env, callApi[1]);
  if (callApi && request.method === "DELETE") return callDelete(request, env, callApi[1]);
  const callPage = path.match(/^\/v\/([A-Za-z0-9]{22})(\/answer)?$/);
  if (callPage && !callPage[2] && (request.method === "GET" || request.method === "HEAD")) return callView(env, callPage[1], null);
  if (callPage && callPage[2] && request.method === "POST") return callAnswer(request, env, callPage[1]);
  const reported = path.match(/^\/(api\/share|s)\/([A-Za-z0-9]{16,32})\/report$/);
  if (reported && request.method === "POST") return report(request, env, reported[2], reported[1] === "s");
  const api = path.match(/^\/api\/share\/([A-Za-z0-9]{16,32})$/);
  if (api && request.method === "PUT") return update(request, env, url, api[1]);
  if (api && request.method === "DELETE") return remove(request, env, api[1]);
  if (api && request.method === "GET") return read(request, env, api[1]);
  if (path === "/.well-known/assetlinks.json" && request.method === "GET") return assetLinks(env);
  const page = path.match(/^\/s\/([A-Za-z0-9]{16,32})$/);
  if (page && (request.method === "GET" || request.method === "HEAD")) return view(request, env, page[1]);
  const feed = path.match(/^\/s\/([A-Za-z0-9]{16,32})\.ics$/);
  if (feed && (request.method === "GET" || request.method === "HEAD")) return calendar(request, env, feed[1]);
  if (path === "/apple-touch-icon.png" && (request.method === "GET" || request.method === "HEAD")) return touchIcon();
  if (path === "/app-ads.txt" && (request.method === "GET" || request.method === "HEAD")) {
    // AdMob reads this from the developer website on Play (https://bamnat.app). Public values only; APP_ADS_TXT may replace it.
    const body = (env.APP_ADS_TXT || APP_ADS_TXT).trim() + "\n";
    return new Response(body, { headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "public, max-age=3600" } });
  }
  if (path === "/" || path === "/robots.txt") {
    // Shared schedules stay out of search; only app-ads.txt is open to crawlers (AdMob honours robots.txt).
    return new Response("User-agent: *\nAllow: /app-ads.txt\nDisallow: /\n", { headers: { "content-type": "text/plain; charset=utf-8" } });
  }
  return new Response("Not found", { status: 404 });
}

function tooManyReads(path) {
  const wait = String(3600 - Math.floor((Date.now() / 1000) % 3600));
  if (path.startsWith("/s/") && !path.endsWith(".ics")) {
    const res = html(page("잠시 뒤에 다시 열어 주세요", `<p class="empty">이 네트워크에서 너무 많이 열었어요. 잠시 뒤에 다시 열어 주세요.</p>`), 429);
    res.headers.set("retry-after", wait);
    return res;
  }
  const res = json({ error: "rate_limited" }, 429);
  res.headers.set("retry-after", wait);
  return res;
}

/** 1.39 [CARE-09] The words of a schedule link everyone who opens it reads first: its title and the shift names. */
const personalHead = (schedule) => ({ title: schedule?.title ?? "", type: (schedule?.types ?? []).map((t) => [t.name, t.short]) });

async function create(request, env, url) {
  // [SRV-08] Resting (the company server's switch): nothing new, whatever app asks.
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  const parsed = await readSchedule(request);
  if (parsed.error) return json({ error: parsed.error }, 400);
  // 1.39 [CARE-09] 7차 PC: a schedule link's title and shift names take no phone·link·account·messenger either (the title is the
  // page's headline and preview). Memos, extras and pay labels are the sharer's own notes to the people they chose, left as they are.
  const scam = scamRefusal(request, personalHead(parsed.schedule));
  if (scam) return scam;
  const id = randomId(22);
  const deleteToken = randomId(32);
  const record = {
    schedule: parsed.schedule,
    tokenHash: await sha256(deleteToken),
    expiresAt: expiry(parsed.ttlDays ?? 30),
    updatedAt: new Date().toISOString(),
  };
  await put(env, id, record);
  count(env, "created");
  return json({ id, url: `${url.origin}/s/${id}`, deleteToken, expiresAt: record.expiresAt }, 201);
}

async function update(request, env, url, id) {
  const record = await authorized(request, env, id);
  if (record instanceof Response) return record;
  const parsed = await readSchedule(request);
  if (parsed.error) return json({ error: parsed.error }, 400);
  // Only a changed title or shift name is checked: a link made before 1.39 with a number in its title keeps updating by itself.
  const head = personalHead(parsed.schedule);
  const scam = JSON.stringify(head) === JSON.stringify(personalHead(record.schedule)) ? null : scamRefusal(request, head);
  if (scam) return scam;
  // [SHR-12] The app sends every change by itself; only the sharer's 연장 (a ttlDays) moves the end date.
  const expiresAt = parsed.ttlDays == null ? record.expiresAt : expiry(parsed.ttlDays);
  if (Date.parse(expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  const same = JSON.stringify(parsed.schedule) === JSON.stringify(record.schedule);
  // [SHR-12] The ETag is built from updatedAt, so a change in the same millisecond as the last write
  // must still move it forward, or a subscriber keeps getting 304 for a schedule that changed.
  const stamp = new Date(Math.max(Date.now(), (Date.parse(record.updatedAt) || 0) + 1)).toISOString();
  const next = { ...record, schedule: parsed.schedule, expiresAt, updatedAt: same ? record.updatedAt : stamp };
  await put(env, id, next);
  return json({ id, url: `${url.origin}/s/${id}`, expiresAt: next.expiresAt, updatedAt: next.updatedAt });
}

async function remove(request, env, id) {
  const record = await authorized(request, env, id);
  if (record instanceof Response) return record;
  await kvDel(env, id);
  return new Response(null, { status: 204 });
}

async function authorized(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(id, "json");
  if (!record) return json({ error: "not_found" }, 404);
  // [SRV-05] A link taken down for breaking the rules cannot be edited back.
  if (record.removed) return json({ error: "removed" }, 410);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

async function view(request, env, id) {
  const record = await env.SHARES.get(id, "json");
  if (record?.removed) return html(page("내려진 링크예요", `<p class="empty">${REMOVED_TEXT}</p>`), 410);
  if (!record || Date.parse(record.expiresAt) < Date.now()) {
    // 1.39 PC (SPREAD 2차): an expired link still kept says whose it was (a leader's teammate link speaks to a teammate); one
    // the sharer cut is gone, so its card speaks to anyone who keeps a schedule.
    // [RVD-30] HQ-64 An expired link still kept is named as its page was (이 영업일 링크는 …).
    const word = record?.schedule && !record.schedule.team ? linkWord(record.schedule) : "근무표";
    return html(page("링크가 없어요", `<p class="empty">이 ${word} 링크는 만료되었거나 공유한 사람이 끊었어요.</p>${goneNext(record?.schedule?.team
      ? "최신 근무표는 리더에게 새 링크를 부탁해 주세요." : `최신 ${WORD_FORMS[word].subj} 필요하면 보낸 분께 새 링크를 부탁해 주세요.`, record?.schedule ? scheduleKind(record.schedule) : "schedule", word)}`), 404);
  }
  const phone = platformOf(request.headers.get("user-agent") || "");
  if (request.method === "GET") {
    count(env, "views");
    // 1.39 [IOS-06] The same view by kind of phone (a person, not a preview robot).
    if (!previewBot(request)) { count(env, `views_${phone}`); await markSeen(env, id, record); }
  }
  const url = new URL(request.url);
  // 1.39 [IOS-04] A leader's 피곤해요 합계 is on for this link: the page offers the press too (once a day, like the app).
  const tired = record.tired ? await tiredState(env, id, record) : null;
  // [SHR-02] On Android the page offers the app, which keeps it as a 받은 달력 next to the user's own.
  return html(renderSchedule(record.schedule, record.expiresAt, phone === "android" ? id : null, id,
    { phone, feed: `${url.host}/s/${id}.ics`, scheme: url.protocol.replace(":", ""), tired, said: url.searchParams.get("t") }));
}

/**
 * 1.39 [IOS-02][IOS-06] Which kind of phone asked: the page's words (an iPhone has no 밤낮 app) and a count per kind.
 * Never kept with a link or a person.
 */
export function platformOf(ua) {
  if (/Android/i.test(ua)) return "android";
  if (/iPhone|iPad|iPod|Macintosh.*Mobile/i.test(ua)) return "ios";
  return "other";
}

/** [SRV-08] The key the company server's switch writes while new links rest. */
const CREATE_OFF = "flag:create_off";

const REMOVED_TEXT = "이 근무표 링크는 신고를 받아 운영 규칙에 따라 내려졌어요. 공유한 분이 앱에서 새 링크를 만들 수 있어요.";

/**
 * [FEAT-15] The link's days as an iCalendar feed that Google, Apple and Outlook calendars subscribe to.
 * Only for a link whose sharer turned the address on; it says no more than the page does.
 */
async function calendar(request, env, id) {
  const record = await env.SHARES.get(id, "json");
  if (!record || record.removed || Date.parse(record.expiresAt) < Date.now() || record.schedule.ics !== true) {
    return new Response("Not found", { status: 404, headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" } });
  }
  // [SRV-08] A calendar app that has the same days gets 304, and caches for five minutes only, so a
  // link taken down leaves subscribed calendars soon after.
  const tag = `W/"${(await sha256(`ics|${record.updatedAt ?? ""}|${record.expiresAt}`)).slice(0, 20)}"`;
  const headers = {
    "content-type": "text/calendar; charset=utf-8",
    "content-disposition": `inline; filename="bamnat-${id.slice(0, 6)}.ics"`,
    "cache-control": `public, max-age=${LIMITS.icsMaxAge}`,
    "x-robots-tag": "noindex, nofollow",
    etag: tag,
  };
  if (matches(request, tag)) return new Response(null, { status: 304, headers });
  return new Response(renderIcs(record.schedule, id, record.updatedAt), { headers });
}

/** Whether If-None-Match names [tag] (weak or strong). */
function matches(request, tag) {
  const asked = (request.headers.get("if-none-match") || "").split(",").map((t) => t.trim().replace(/^W\//, ""));
  return asked.includes(tag.replace(/^W\//, ""));
}

/** [SHR-02] What the app's 받은 달력 shows: the same schedule as the page, never the token hash. */
async function read(request, env, id) {
  const record = await env.SHARES.get(id, "json");
  // [SHR-13] The sharer's app (and a receiver) is told why, in the operator's words.
  if (record?.removed) return json({ error: "removed", ...(record.reason ? { reason: record.reason } : {}), at: record.at ?? null }, 410);
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  // [SHR-12] Receivers ask often and download only when the schedule or its end date changed.
  const tag = `W/"${(await sha256(`${record.updatedAt ?? ""}|${record.expiresAt}${record.tired ? "|t" : ""}`)).slice(0, 20)}"`;
  const asked = (request.headers.get("if-none-match") || "").split(",").map((t) => t.trim().replace(/^W\//, ""));
  if (asked.includes(tag.replace(/^W\//, ""))) {
    return new Response(null, { status: 304, headers: { etag: tag, "cache-control": "no-store" } });
  }
  // 1.39 G2: the member's app checking in the background (받은 근무표 따라가기, 하루 한 번 소식) is not the member looking.
  if (request.headers.get("x-bamnat-quiet") !== "1") await markSeen(env, id, record);
  const res = json({ schedule: record.schedule, expiresAt: record.expiresAt, updatedAt: record.updatedAt ?? null, ...(record.tired ? { tired: true } : {}) });
  res.headers.set("etag", tag);
  return res;
}

/**
 * [SHR-02] Digital Asset Links, so a tapped /s/ link opens 밤낮 when it is installed. The SHA-256
 * fingerprints of the app signing certificate (Play Console > 앱 무결성) go in ANDROID_CERT_SHA256,
 * separated by commas; without them Android keeps opening the page in the browser.
 */
function assetLinks(env) {
  const prints = String(env.ANDROID_CERT_SHA256 || "").split(",").map((s) => s.trim().toUpperCase())
    .filter((s) => /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(s));
  const body = prints.length === 0 ? [] : [{
    relation: ["delegate_permission/common.handle_all_urls"],
    target: { namespace: "android_app", package_name: env.ANDROID_PACKAGE || "com.bamnat.app", sha256_cert_fingerprints: prints },
  }];
  return new Response(JSON.stringify(body), { headers: { "content-type": "application/json", "cache-control": "max-age=3600" } });
}

// ---- [SRV-05] reports and take-downs ----

/**
 * Someone who got the link reports it. Kept per link as a count by reason (in the key's metadata, so the
 * list needs no extra reads), never who reported: the caller is only a salted hash that expires in a day.
 */
async function report(request, env, id, fromPage) {
  const done = (status, text) => fromPage
    ? html(page("신고", `<p class="empty">${text}</p>`), status)
    : status === 204 ? new Response(null, { status }) : json({ error: text }, status);
  let reason;
  const type = request.headers.get("content-type") || "";
  const text = (await request.text()).slice(0, 1024);
  if (type.startsWith("application/x-www-form-urlencoded")) reason = new URLSearchParams(text).get("reason");
  else if (type.startsWith("application/json")) {
    try { reason = JSON.parse(text)?.reason; } catch { reason = null; }
  }
  if (!Object.hasOwn(REPORT_REASONS, reason ?? "")) return done(400, fromPage ? "신고 이유를 골라 주세요." : "reason");
  const record = await env.SHARES.get(id, "json");
  if (!record || record.removed || Date.parse(record.expiresAt) < Date.now()) return done(404, fromPage ? "이미 닫힌 링크예요." : "not_found");
  const ip = netOf(request.headers.get("cf-connecting-ip"));
  const day = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10);
  const once = `rp:${(await sha256(`${env.RATE_SALT || "bamnat"}:${ip}:${id}:${day}`)).slice(0, 24)}`;
  const thanks = "신고를 받았어요. 운영자가 확인하고 운영 규칙에 어긋나면 링크를 내려요. 고마워요.";
  // The same caller reporting the same link again the same day is thanked and not counted twice.
  if (await env.SHARES.get(once)) return done(fromPage ? 200 : 204, thanks);
  if (!(await allow(env, request, "rr", LIMITS.reportsPerHour))) return done(429, fromPage ? "잠시 뒤에 다시 신고해 주세요." : "rate_limited");
  await kvPut(env, once, "1", { expirationTtl: 86400 });
  const key = `report:${id}`;
  const got = await env.SHARES.getWithMetadata?.(key);
  const was = got?.metadata ?? { count: 0, reasons: {} };
  const next = { count: was.count + 1, reasons: { ...was.reasons, [reason]: (was.reasons?.[reason] ?? 0) + 1 }, last: new Date().toISOString() };
  const ttl = Math.max(60, Math.ceil((Date.parse(record.expiresAt) - Date.now()) / 1000));
  await kvPut(env, key, "1", { expirationTtl: ttl, metadata: next });
  count(env, "reports");
  return done(fromPage ? 200 : 204, thanks);
}

/** 이 서버가 지금 도는 판: 코드 판, 배포 도구가 붙인 커밋(--tag), Cloudflare 배포 번호와 시각. 로컬에는 판만 있어요. */
export function deployInfo(env) {
  const m = env?.CF_VERSION_METADATA ?? {};
  const tag = typeof m.tag === "string" && /^[0-9a-f]{7,40}$/.test(m.tag) ? m.tag.slice(0, 8) : null;
  const at = typeof m.timestamp === "string" && !Number.isNaN(Date.parse(m.timestamp)) ? Date.parse(m.timestamp) : null;
  return { version: SERVER_VERSION, commit: tag, id: typeof m.id === "string" ? m.id.slice(0, 8) : null, at };
}

/** The company server's 개발자 앱 only: a shared token in Authorization, compared as digests. */
async function admin(request, env, url) {
  const secret = typeof env.SHARE_ADMIN_TOKEN === "string" ? env.SHARE_ADMIN_TOKEN.trim() : "";
  if (secret.length < 20) return new Response("Not found", { status: 404 });
  const given = (request.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
  if ((await sha256(`admin|${given}`)) !== (await sha256(`admin|${secret}`))) return json({ error: "forbidden" }, 403);
  const path = url.pathname.slice("/api/admin".length);
  if (path === "/reports" && request.method === "GET") return json({ reports: await reports(env) });
  const m = path.match(/^\/share\/([A-Za-z0-9]{16,32})$/);
  if (m && request.method === "DELETE") return takeDown(request, env, m[1]);
  // 1.39 [CARE-08] 팀·사장님 링크 내리기(종류별).
  const team = path.match(/^\/link\/([bavwkc])\/([A-Za-z0-9]{22})$/);
  if (team && request.method === "DELETE") return teamTakeDown(request, env, team[1], team[2]);
  if (team && request.method === "GET") return teamPeek(env, team[1], team[2]);
  if (path === "/stats" && request.method === "GET") return json(await stats(env, url.searchParams.get("days")));
  // 1.39 관리자 앱 머리띠(사용자 11-08 '현재 출시된 서버가 몇 버젼인지 위쪽에'): 판·커밋·배포 시각. 공개 /api/health는 판만 그대로예요.
  if (path === "/version" && request.method === "GET") return json(deployInfo(env));
  if (path === "/flags" && request.method === "PUT") {
    let body = {};
    try { body = JSON.parse((await request.text()).slice(0, 256)); } catch { /* nothing */ }
    if (body.createOff === true) await kvPut(env, CREATE_OFF, "1");
    else await kvDel(env, CREATE_OFF);
    return json({ createOff: body.createOff === true });
  }
  return json({ error: "not_found" }, 404);
}

async function reports(env) {
  const out = [];
  let cursor;
  do {
    const page = await env.SHARES.list({ prefix: "report:", cursor });
    for (const k of page.keys) {
      if (!k.metadata) continue;
      // 1.39 [CARE-07] report:<kind>:<id> for a team link, report:<id> for a 근무표 링크 (kind s).
      const rest = k.name.slice("report:".length);
      const [kind, id] = rest.includes(":") ? rest.split(":") : ["s", rest];
      out.push({ id, kind, name: TEAM_KINDS[kind]?.name ?? "근무표 링크", ...k.metadata });
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor && out.length < 1000);
  return out.sort((a, b) => b.count - a.count || b.last.localeCompare(a.last)).slice(0, 200);
}

async function takeDown(request, env, id) {
  const record = await env.SHARES.get(id, "json");
  if (!record) return json({ error: "not_found" }, 404);
  let reason = "";
  try {
    reason = String(JSON.parse((await request.text()).slice(0, 2048))?.reason ?? "");
  } catch { /* no reason given */ }
  reason = reason.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, LIMITS.removedReason);
  if (!record.removed) {
    // Only the mark stays: the schedule itself is gone at once. [SHR-13] With the operator's reason, as long as the mark.
    await kvPut(env, id, JSON.stringify({ removed: true, at: new Date().toISOString(), ...(reason ? { reason } : {}) }),
      { expirationTtl: LIMITS.removedDays * 86400 });
    count(env, "removed");
  }
  await kvDel(env, `report:${id}`);
  return new Response(null, { status: 204 });
}

// ---- 1.39 [CARE-07][CARE-08] 팀·사장님 링크의 신고와 내리기 ----
//
// 1.38의 병동 표(b)·근무 신청(a)·결원(v)·바꾸기(w)·근로계약서(k)·출퇴근(c) 페이지에도 근무표 링크처럼 신고 양식이 있고, 운영자가 관리자 앱에서
// 내려요. 신고는 링크(라운드)마다 이유별 수만, 신고한 사람은 남지 않아요. 내리면 내용은 바로 지우고 '내렸다'는 표시와 이유만 90일 둬요.

const TEAM_KINDS = {
  b: { name: "병동 표", main: (id) => boardKey(id) },
  a: { name: "근무 신청", page: (key) => askMemberKey(key), main: (id) => askKey(id) },
  v: { name: "결원 요청", page: (key) => callPersonKey(key), main: (id) => callKey(id) },
  w: { name: "근무 바꾸기", page: (key) => swapPersonKey(key), main: (id) => swapKey(id) },
  k: { name: "근로계약서", page: (key) => signPageKey(key), main: (id) => signKey(id) },
  c: { name: "출퇴근", main: (id) => clockKey(id) },
};
const TEAM_REMOVED_TEXT = "이 링크는 신고를 받아 운영 규칙에 따라 내려졌어요. 밤낮은 링크로 돈이나 계좌를 요구하지 않아요.";
const downKey = (kind, id) => `down:${kind}:${id}`;

/** The round a page's key belongs to ({ id, record }), or null when it is gone. */
async function teamRound(env, kind, key) {
  const k = TEAM_KINDS[kind];
  if (!k) return null;
  let id = key;
  if (k.page) {
    const link = await env.SHARES.get(k.page(key), "json");
    if (!link?.id) return null;
    id = link.id;
  }
  const record = await env.SHARES.get(k.main(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return { id, record: null };
  return { id, record };
}

/** The take-down mark of the round behind a page, or null. */
async function teamDown(env, kind, key) {
  const k = TEAM_KINDS[kind];
  let id = key;
  if (k.page) {
    const link = await env.SHARES.get(k.page(key), "json");
    if (!link?.id) return null;
    id = link.id;
  }
  return env.SHARES.get(downKey(kind, id), "json");
}

async function teamReport(request, env, kind, key) {
  const done = (status, text) => html(page("신고", `<p class="empty">${esc(text)}</p>`), status);
  const reason = new URLSearchParams((await request.text()).slice(0, 512)).get("reason");
  if (!Object.hasOwn(TEAM_REPORT_REASONS, reason ?? "")) return done(400, "신고 이유를 골라 주세요.");
  const found = await teamRound(env, kind, key);
  if (!found?.record) return done(404, "이미 닫힌 링크예요.");
  const ip = netOf(request.headers.get("cf-connecting-ip"));
  const day = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10);
  const once = `rp:${(await sha256(`${env.RATE_SALT || "bamnat"}:${ip}:${kind}:${found.id}:${day}`)).slice(0, 24)}`;
  const thanks = "신고를 받았어요. 운영자가 확인하고 운영 규칙에 어긋나면 링크를 내려요. 돈이나 계좌를 요구받았다면 보내지 마세요. 고마워요.";
  if (await env.SHARES.get(once)) return done(200, thanks);
  if (!(await allow(env, request, "rr", LIMITS.reportsPerHour))) return done(429, "잠시 뒤에 다시 신고해 주세요.");
  await kvPut(env, once, "1", { expirationTtl: 86400 });
  const rkey = `report:${kind}:${found.id}`;
  const got = await env.SHARES.getWithMetadata?.(rkey);
  const was = got?.metadata ?? { count: 0, reasons: {} };
  const next = { count: was.count + 1, reasons: { ...was.reasons, [reason]: (was.reasons?.[reason] ?? 0) + 1 }, last: new Date().toISOString() };
  const ttl = Math.max(60, Math.ceil((Date.parse(found.record.expiresAt) - Date.now()) / 1000));
  await kvPut(env, rkey, "1", { expirationTtl: ttl, metadata: next });
  count(env, "reports");
  return done(200, thanks);
}

/** The operator takes a team round down: its content goes now; each of its pages says it was taken down, with why, for 90 days. */
async function teamTakeDown(request, env, kind, id) {
  const k = TEAM_KINDS[kind];
  const record = await env.SHARES.get(k.main(id), "json");
  const marked = await env.SHARES.get(downKey(kind, id), "json");
  if (!record && !marked) return json({ error: "not_found" }, 404);
  let reason = "";
  try {
    reason = String(JSON.parse((await request.text()).slice(0, 2048))?.reason ?? "");
  } catch { /* no reason given */ }
  reason = reason.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, LIMITS.removedReason);
  if (!marked) {
    await kvPut(env, downKey(kind, id), JSON.stringify({ at: new Date().toISOString(), ...(reason ? { reason } : {}) }),
      { expirationTtl: LIMITS.removedDays * 86400 });
    count(env, "removed");
  }
  // The content goes; member pages' keys stay until they end, only to find the mark.
  if (record) await kvDel(env, k.main(id));
  await kvDel(env, `report:${kind}:${id}`);
  return new Response(null, { status: 204 });
}

/**
 * GET /api/admin/link/:kind/:id: what the operator reads before taking a reported team link down. Only the words people typed (titles, notes,
 * 계약 항목), never a key, token, hash or address; 600 characters at most.
 */
async function teamPeek(env, kind, id) {
  const record = await env.SHARES.get(TEAM_KINDS[kind].main(id), "json");
  if (!record) return json({ error: "not_found" }, 404);
  const words = [];
  const walk = (v, key = "") => {
    if (words.join(" · ").length > 600) return;
    if (/token|key|hash|secret|ip|pin|sig|url|id$/i.test(key)) return;
    if (typeof v === "string") { if (v.trim() && !/^\d{4}-\d{2}-\d{2}T/.test(v)) words.push(v.trim()); return; }
    if (Array.isArray(v)) { for (const x of v) walk(x, key); return; }
    if (v && typeof v === "object") for (const [k, x] of Object.entries(v)) walk(x, k);
  };
  walk(record);
  return json({ kind, name: TEAM_KINDS[kind].name, text: words.join(" · ").slice(0, 600), expiresAt: record.expiresAt ?? null });
}

function teamDownPage(down) {
  return html(page("내려진 링크예요", `<p class="empty">${esc(TEAM_REMOVED_TEXT)}</p>${down.reason ? `<p class="muted">내린 이유: ${esc(down.reason)}</p>` : ""}`), 410);
}

/** The plain form at the foot of a team page (no script), with the line that 밤낮 never asks for money. */
function teamFoot(kind, key) {
  const options = Object.entries(TEAM_REPORT_REASONS).map(([k, v], i) =>
    `<label><input type="radio" name="reason" value="${k}"${i === 0 ? " required" : ""}> ${esc(v)}</label>`).join("");
  return `<p class="muted foot">밤낮은 링크로 돈이나 계좌, 다른 메신저로 옮기기를 요구하지 않아요. 그런 요구를 받으면 보내지 말고 신고해 주세요.</p>` +
    `<details class="report"><summary>이 링크 신고하기</summary><form method="post" action="/${kind}/${esc(key)}/report">` +
    `<p class="muted">운영자가 확인해 운영 규칙에 어긋나면 링크를 내려요. 누가 신고했는지는 남지 않아요.</p>${options}` +
    `<button type="submit">신고 보내기</button></form></details>`;
}

// ---- [SHR-14] 우리 조 모아 보기: links put together under a 6-letter code ----
//
// Each coworker shares their own link and adds it to a group with its code. The group is a list of link
// ids and nothing else: no names, no company, not who made it. Only a link's own delete token adds or
// takes it out, so nobody can add someone else's link, and a link cut or expired drops out by itself.
// The group goes when its longest link ends.

const GROUP_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const GROUP_CODE = /^[A-HJ-NP-Z2-9]{6}$/;

async function groups(request, env, path) {
  const m = path.match(/^\/api\/groups(?:\/([A-Za-z0-9]{6}))?(?:\/([A-Za-z0-9]{16,32}))?$/);
  if (!m) return json({ error: "not_found" }, 404);
  const code = m[1]?.toUpperCase();
  if (code && !GROUP_CODE.test(code)) return json({ error: "not_found" }, 404);
  if (!code && request.method === "POST") return groupStart(request, env);
  if (code && !m[2] && request.method === "POST") return groupJoin(request, env, code);
  if (code && !m[2] && request.method === "GET") return groupRead(env, code);
  if (code && m[2] && request.method === "DELETE") return groupLeave(request, env, code, m[2]);
  return json({ error: "not_found" }, 404);
}

/** The caller's own link, proved by its delete token, or the answer that refuses it. */
async function ownLink(request, env) {
  let body = {};
  try { body = JSON.parse((await request.text()).slice(0, 1024)); } catch { return { error: json({ error: "bad_json" }, 400) }; }
  const id = typeof body.link === "string" && /^[A-Za-z0-9]{16,32}$/.test(body.link) ? body.link : null;
  if (!id || typeof body.token !== "string") return { error: json({ error: "link" }, 400) };
  const record = await env.SHARES.get(id, "json");
  if (!record || record.removed || Date.parse(record.expiresAt) < Date.now()) return { error: json({ error: "not_found" }, 404) };
  if ((await sha256(body.token)) !== record.tokenHash) return { error: json({ error: "forbidden" }, 403) };
  return { id, record };
}

async function liveLinks(env, ids) {
  const out = [];
  for (const id of ids) {
    const r = await env.SHARES.get(id, "json");
    if (r && !r.removed && Date.parse(r.expiresAt) >= Date.now()) out.push({ id, expiresAt: r.expiresAt });
  }
  return out;
}

async function saveGroup(env, code, links) {
  const ends = links.reduce((a, l) => (l.expiresAt > a ? l.expiresAt : a), new Date().toISOString());
  const ttl = Math.max(60, Math.ceil((Date.parse(ends) - Date.now()) / 1000));
  await kvPut(env, `group:${code}`, JSON.stringify({ ids: links.map((l) => l.id) }), { expirationTtl: ttl });
  return ends;
}

async function groupStart(request, env) {
  if (!(await allow(env, request, "rg", LIMITS.groupsPerHour))) return json({ error: "rate_limited" }, 429);
  const own = await ownLink(request, env);
  if (own.error) return own.error;
  let code;
  for (let i = 0; i < 5 && !code; i++) {
    const c = groupCode();
    if (!(await env.SHARES.get(`group:${c}`))) code = c;
  }
  if (!code) return json({ error: "server" }, 503);
  const expiresAt = await saveGroup(env, code, [{ id: own.id, expiresAt: own.record.expiresAt }]);
  return json({ code, expiresAt, links: [own.id] }, 201);
}

async function groupJoin(request, env, code) {
  if (!(await allow(env, request, "rg", LIMITS.groupsPerHour))) return json({ error: "rate_limited" }, 429);
  const group = await env.SHARES.get(`group:${code}`, "json");
  if (!group) return json({ error: "not_found" }, 404);
  const own = await ownLink(request, env);
  if (own.error) return own.error;
  const links = (await liveLinks(env, group.ids)).filter((l) => l.id !== own.id);
  if (links.length >= LIMITS.groupLinks) return json({ error: "full" }, 409);
  links.push({ id: own.id, expiresAt: own.record.expiresAt });
  const expiresAt = await saveGroup(env, code, links);
  return json({ code, expiresAt, links: links.map((l) => l.id) });
}

async function groupRead(env, code) {
  const group = await env.SHARES.get(`group:${code}`, "json");
  if (!group) return json({ error: "not_found" }, 404);
  const links = await liveLinks(env, group.ids);
  if (links.length === 0) return json({ error: "not_found" }, 404);
  return json({ code, expiresAt: links.reduce((a, l) => (l.expiresAt > a ? l.expiresAt : a), ""), links: links.map((l) => l.id) });
}

async function groupLeave(request, env, code, id) {
  const group = await env.SHARES.get(`group:${code}`, "json");
  if (!group || !group.ids.includes(id)) return new Response(null, { status: 204 });
  const record = await env.SHARES.get(id, "json");
  // A link already cut or taken down has left by itself; otherwise only its own token takes it out.
  if (record && !record.removed && (await sha256(request.headers.get("x-delete-token") || "")) !== record.tokenHash) {
    return json({ error: "forbidden" }, 403);
  }
  const links = (await liveLinks(env, group.ids)).filter((l) => l.id !== id);
  if (links.length === 0) await kvDel(env, `group:${code}`);
  else await saveGroup(env, code, links);
  return new Response(null, { status: 204 });
}

// ---- 1.38 [P3-B-11] 출퇴근 링크 ----
// A staff member presses 출근 or 퇴근 on a page the boss sent them; the boss's app reads the times and tells the boss. Only the
// press's server time is kept: no location, no address, no device. Anyone holding the link can press it, like a paper sheet on the
// wall, so the app shows them as 기록 for the boss to put in, never as pay by themselves.

const clockKey = (id) => `clock:${id}`;

async function clockCreate(request, env, url) {
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 2048)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  const name = cleanText(body?.name, LIMITS.clockName);
  const place = cleanText(body?.place ?? "", LIMITS.clockPlace);
  if (!name || place == null) return json({ error: "bad_name" }, 400);
  const days = Number.isInteger(body?.ttlDays) ? Math.min(Math.max(body.ttlDays, 7), LIMITS.clockTtlDays) : LIMITS.clockTtlDays;
  const id = randomId(22);
  const token = randomId(32);
  const record = { name, place, tokenHash: await sha256(token), expiresAt: expiry(days), punches: [] };
  await put(env, clockKey(id), record);
  count(env, "clocks");
  return json({ id, url: `${url.origin}/c/${id}`, token, expiresAt: record.expiresAt }, 201);
}

async function clockOwned(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(clockKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

async function clockRead(request, env, id) {
  const record = await clockOwned(request, env, id);
  if (record instanceof Response) return record;
  return json({ punches: record.punches, expiresAt: record.expiresAt });
}

async function clockDelete(request, env, id) {
  const record = await clockOwned(request, env, id);
  if (record instanceof Response) return record;
  await kvDel(env, clockKey(id));
  return new Response(null, { status: 204 });
}

const KST = 9 * 3600 * 1000;
const kstTime = (iso) => new Date(Date.parse(iso) + KST).toISOString().slice(11, 16);
const kstDay = (iso) => new Date(Date.parse(iso) + KST).toISOString().slice(0, 10);

async function clockPunch(request, env, id) {
  const record = await env.SHARES.get(clockKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return clockView(env, id, null);
  const form = new URLSearchParams((await request.text()).slice(0, 256));
  const k = form.get("k");
  if (k !== "in" && k !== "out") return clockView(env, id, null, 400);
  if (!(await allow(env, request, "cp", LIMITS.clockPerHour))) return html(page("잠시 뒤에 다시 눌러 주세요", `<p class="empty">이 네트워크에서 너무 많이 눌렀어요. 잠시 뒤에 다시 눌러 주세요.</p>`), 429);
  const now = new Date().toISOString();
  const last = record.punches[record.punches.length - 1];
  // A second press of the same button within a minute (a double tap, a page sent again) is the same press.
  if (!(last && last.k === k && Date.now() - Date.parse(last.at) < LIMITS.clockSameSeconds * 1000)) {
    record.punches = [...record.punches, { k, at: now }].slice(-LIMITS.clockPunches);
    await put(env, clockKey(id), record);
    count(env, "punches");
  }
  return clockView(env, id, { k, at: last && last.k === k && Date.now() - Date.parse(last.at) < LIMITS.clockSameSeconds * 1000 ? last.at : now }, 200, record);
}

async function clockView(env, id, done, status = 200, known = null) {
  const record = known ?? await env.SHARES.get(clockKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) {
    return html(page("링크가 없어요", `<p class="empty">이 출퇴근 링크는 끝났거나 사장님이 끊었어요. 새 링크는 사장님께 받아 주세요.</p>${goneNext("", "clock")}`), 404);
  }
  const today = kstDay(new Date().toISOString());
  const todays = record.punches.filter((p) => kstDay(p.at) === today);
  const said = done ? `<p class="done">${done.k === "in" ? "출근" : "퇴근"} ${kstTime(done.at)} 기록했어요. 사장님 휴대폰에 알려요.</p>` : "";
  const list = todays.length
    ? `<ul class="punches">${todays.map((p) => `<li><span>${p.k === "in" ? "출근" : "퇴근"}</span><span>${kstTime(p.at)}</span></li>`).join("")}</ul>`
    : `<p class="muted">오늘 기록이 아직 없어요.</p>`;
  const button = (k, label) => `<form method="post" action="/c/${id}/punch"><input type="hidden" name="k" value="${k}"><button class="${k}">${label}</button></form>`;
  const body = `<h1>${esc(record.name)}님 출퇴근</h1>${record.place ? `<p class="muted">${esc(record.place)}</p>` : ""}${said}
<div class="buttons">${button("in", "출근")}${button("out", "퇴근")}</div>
<h2>오늘 (${today.slice(5).replace("-", "/")})</h2>${list}
<p class="foot muted">누른 시각만 사장님에게 가요. 위치·전화번호·기기 정보는 보내지 않아요. 잘못 눌렀으면 사장님께 말해 주세요. 사장님이 확인한 뒤 급여에 넣어요.</p>
<p class="muted small">매일 쓰는 링크예요. 브라우저 메뉴(아이폰은 아래 공유 단추)에서 ‘홈 화면에 추가’를 누르면 아이콘으로 바로 열려요.</p>${spreadSlot("clock")}`;
  // 1.39 [IOS-05] Added to the home screen, it is named 출퇴근 under the 밤낮 icon (iPhone and Android alike).
  return html(page(`${record.name}님 출퇴근`, body + teamFoot("c", id), "출퇴근", "clock"), status);
}

// ---- 1.38 [P3-L-13] 근무 신청 받기 ----
// The ward's paper sheet ("12월중 근무, 평일 2명, 주말 3명 신청") as a link per member: they tap a day, see how many asked it, and
// the leader's app reads the round with its key. Each member's requests are their own key, so two people asking at once do not
// write over each other; a day's room is read across them (KV is eventually consistent, so the last seat can go twice: the time
// asked is kept and the leader's app orders by it, core TeamAsk.order). Kept: the team's and members' names as the leader wrote
// them, the days and kinds asked, a line each, when. Never a reason (병가 is not a kind members can ask), no address, no device.

const askKey = (id) => `ask:${id}`;
const askMemberKey = (key) => `askm:${key}`;
const askWishKey = (id, m) => `askw:${id}:${m}`;
export const ASK_KINDS = { OFF: ["오프", true], LEAVE: ["연차", true], HALF: ["반차", false], EDU: ["교육", true], WORK: ["근무", false], AVOID_NIGHT: ["나이트 빼기", false], SHIFT: ["희망", false] };

function askRules(r, month) {
  const n = (v, max) => (Number.isInteger(v) && v >= 0 && v <= max ? v : null);
  const inMonth = (d) => isDate(d) && d.startsWith(month);
  if (!r || typeof r !== "object") return null;
  const kinds = Array.isArray(r.kinds) ? r.kinds.filter((k) => k in ASK_KINDS) : [];
  const days = (a) => (Array.isArray(a) ? [...new Set(a.filter(inMonth))].slice(0, 31) : []);
  const rules = {
    weekdayCap: n(r.weekdayCap ?? 0, LIMITS.askMembers), weekendCap: n(r.weekendCap ?? 0, LIMITS.askMembers), perPerson: n(r.perPerson ?? 0, 31),
    closesAt: r.closesAt == null ? null : Number.isFinite(r.closesAt) ? r.closesAt : NaN,
    kinds, firstCome: r.firstCome !== false, holidays: days(r.holidays), blocked: days(r.blocked), showNames: r.showNames === true,
  };
  if (rules.weekdayCap == null || rules.weekendCap == null || rules.perPerson == null || Number.isNaN(rules.closesAt) || kinds.length === 0) return null;
  return rules;
}

const askWeekend = (rules, d) => { const w = new Date(d + "T00:00:00Z").getUTCDay(); return w === 0 || w === 6 || rules.holidays.includes(d); };
const askCap = (rules, d) => (askWeekend(rules, d) ? rules.weekendCap : rules.weekdayCap);

/** The same words as core TeamAsk.check: why a request cannot be taken, or null. [all]: [{m, d, k}] of the round. */
/** 1.38 [P3-X-05] 연차 days a request list takes: 연차 1, 반차 0.5. */
const leaveTaken = (ws) => ws.reduce((n, w) => n + (w.k === "LEAVE" ? 1 : w.k === "HALF" ? 0.5 : 0), 0);

export function askCheck(rules, month, all, w, now, leaveLeft = null) {
  if (rules.closesAt != null && now >= rules.closesAt) return "신청이 마감됐어요";
  if (!isDate(w.d) || !w.d.startsWith(month)) return "이 달 날짜가 아니에요";
  if (!rules.kinds.includes(w.k)) return "이 신청은 받지 않아요";
  if (rules.blocked.includes(w.d)) return "이 날은 신청을 받지 않아요";
  if (leaveLeft != null && (w.k === "LEAVE" || w.k === "HALF") && leaveTaken(all.filter((o) => o.m === w.m && o.d !== w.d)) + leaveTaken([w]) > leaveLeft) return "남은 연차보다 많아요";
  if (!ASK_KINDS[w.k][1]) return null;
  const others = all.filter((o) => !(o.m === w.m && o.d === w.d));
  const cap = askCap(rules, w.d);
  if (rules.firstCome && cap > 0 && others.filter((o) => o.d === w.d && ASK_KINDS[o.k]?.[1]).length >= cap) return "이 날은 자리가 다 찼어요";
  if (rules.perPerson > 0 && others.filter((o) => o.m === w.m && ASK_KINDS[o.k]?.[1]).length >= rules.perPerson) return "신청할 수 있는 날을 다 썼어요";
  return null;
}

async function askCreate(request, env, url) {
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 16 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  const team = cleanText(body?.team, LIMITS.askTeam);
  const month = isMonth(body?.month) ? body.month : null;
  const rules = month ? askRules(body?.rules, month) : null;
  const names = Array.isArray(body?.members) ? body.members.map((m) => cleanText(m, LIMITS.askName)) : [];
  const shifts = Array.isArray(body?.shifts) ? body.shifts.slice(0, LIMITS.askShifts).map((x) => ({ id: Number.isInteger(x?.id) ? x.id : null, label: cleanText(x?.label, LIMITS.askShiftLabel), color: isColor(x?.color) ? x.color : "#888888" })) : [];
  if (!team || !month || !rules || names.length === 0 || names.length > LIMITS.askMembers || names.some((n) => !n) || shifts.some((x) => x.id == null || !x.label)) {
    return json({ error: "bad_round" }, 400);
  }
  const id = randomId(22);
  const token = randomId(32);
  const days = Number.isInteger(body?.ttlDays) ? Math.min(Math.max(body.ttlDays, 7), LIMITS.askTtlDays) : LIMITS.askTtlDays;
  // 1.38 [P3-X-05] 남은 연차 per member, worked out on the leader's phone from the 입사일 (null: not set).
  const leave = Array.isArray(body?.leave) ? body.leave : [];
  const members = names.map((name, i) => {
    const l = leave[i];
    return { name, key: randomId(22), ...(typeof l === "number" && Number.isFinite(l) && l >= 0 && l <= 60 ? { leave: Math.round(l * 100) / 100 } : {}) };
  });
  const record = { team, month, rules, shifts, members, tokenHash: await sha256(token), expiresAt: expiry(days), closed: false };
  await put(env, askKey(id), record);
  for (let i = 0; i < members.length; i++) await put(env, askMemberKey(members[i].key), { id, m: i, expiresAt: record.expiresAt });
  count(env, "asks");
  return json({ id, token, expiresAt: record.expiresAt, members: members.map((m) => ({ name: m.name, key: m.key, url: `${url.origin}/a/${m.key}` })) }, 201);
}

async function askOwned(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(askKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

/** Every member's requests of a round, as [{m, d, k, s?, at}], and the members' lines. */
async function askAll(env, id, record) {
  const wishes = [];
  const notes = [];
  const seen = [];
  for (let m = 0; m < record.members.length; m++) {
    const mine = await env.SHARES.get(askWishKey(id, m), "json");
    if (!mine) continue;
    for (const w of mine.wishes ?? []) wishes.push({ m, ...w });
    if (mine.note) notes.push({ m, note: mine.note });
    if (mine.seenAt) seen.push(m);
  }
  return { wishes, notes, seen };
}

async function askRead(request, env, id) {
  const record = await askOwned(request, env, id);
  if (record instanceof Response) return record;
  const all = await askAll(env, id, record);
  return json({ ...all, closed: record.closed, closesAt: record.rules.closesAt, expiresAt: record.expiresAt });
}

async function askChange(request, env, id) {
  const record = await askOwned(request, env, id);
  if (record instanceof Response) return record;
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 1024)); } catch { return json({ error: "bad_json" }, 400); }
  if (body?.closesAt !== undefined) {
    if (body.closesAt !== null && !Number.isFinite(body.closesAt)) return json({ error: "bad_round" }, 400);
    record.rules.closesAt = body.closesAt;
  }
  if (typeof body?.closed === "boolean") record.closed = body.closed;
  await put(env, askKey(id), record);
  return new Response(null, { status: 204 });
}

async function askDelete(request, env, id) {
  const record = await askOwned(request, env, id);
  if (record instanceof Response) return record;
  for (let m = 0; m < record.members.length; m++) {
    await kvDel(env, askWishKey(id, m));
    await kvDel(env, askMemberKey(record.members[m].key));
  }
  await kvDel(env, askKey(id));
  return new Response(null, { status: 204 });
}

async function askFind(env, key) {
  const link = await env.SHARES.get(askMemberKey(key), "json");
  if (!link) return null;
  const record = await env.SHARES.get(askKey(link.id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return null;
  return { id: link.id, m: link.m, record };
}

const askGone = () => html(page("링크가 없어요", `<p class="empty">이 신청 링크는 끝났거나 리더가 지웠어요. 새 링크는 리더에게 받아 주세요.</p>${goneNext("", "request")}`), 404);
const askOpen = (record) => !record.closed && !(record.rules.closesAt != null && Date.now() >= record.rules.closesAt);

async function askPost(request, env, key, what) {
  const found = await askFind(env, key);
  if (!found) return askGone();
  if (!(await allow(env, request, "ap", LIMITS.askPerHour))) return html(page("잠시 뒤에 다시 눌러 주세요", `<p class="empty">이 네트워크에서 너무 많이 눌렀어요. 잠시 뒤에 다시 눌러 주세요.</p>`), 429);
  const { id, m, record } = found;
  const form = new URLSearchParams((await request.text()).slice(0, 512));
  const mine = (await env.SHARES.get(askWishKey(id, m), "json")) ?? { wishes: [], note: "" };
  let said = null;
  if (!askOpen(record)) said = { bad: "신청이 마감됐어요" };
  else if (what === "note") {
    const note = cleanText(form.get("note") ?? "", LIMITS.askNote);
    if (note == null) said = { bad: `한 줄은 ${LIMITS.askNote}자까지예요` };
    else { mine.note = note; said = { ok: note ? "한 줄을 남겼어요" : "한 줄을 지웠어요" }; }
  } else {
    const d = form.get("d") ?? "";
    const k = form.get("k") ?? "";
    if (k === "none") {
      mine.wishes = mine.wishes.filter((w) => w.d !== d);
      said = { ok: `${d.slice(5).replace("-", "/")} 신청을 지웠어요` };
    } else if (!(k in ASK_KINDS)) said = { bad: "고를 수 없는 신청이에요" };
    else {
      const s = k === "SHIFT" ? Number.parseInt(form.get("s") ?? "", 10) : null;
      if (k === "SHIFT" && !record.shifts.some((x) => x.id === s)) said = { bad: "고를 수 없는 근무예요" };
      else {
        const all = (await askAll(env, id, record)).wishes;
        const no = askCheck(record.rules, record.month, all, { m, d, k }, Date.now(), record.members[m].leave ?? null);
        if (no) said = { bad: no };
        else {
          const at = mine.wishes.find((w) => w.d === d && ASK_KINDS[w.k]?.[1] && ASK_KINDS[k][1])?.at ?? new Date().toISOString();
          mine.wishes = [...mine.wishes.filter((w) => w.d !== d), { d, k, ...(s != null ? { s } : {}), at }].sort((a, b) => a.d.localeCompare(b.d)).slice(0, 31);
          said = { ok: `${d.slice(5).replace("-", "/")} ${k === "SHIFT" ? record.shifts.find((x) => x.id === s).label : ASK_KINDS[k][0]} 신청했어요` };
        }
      }
    }
  }
  if (said.ok) { await put(env, askWishKey(id, m), { ...mine, expiresAt: record.expiresAt }); count(env, "askPicks"); }
  return askView(env, key, what === "pick" ? form.get("d") : null, said, said.bad ? 409 : 200, found, mine);
}

async function askView(env, key, day, said, status = 200, known = null, mineKnown = null) {
  const found = known ?? await askFind(env, key);
  if (!found) return askGone();
  const { id, m, record } = found;
  const { rules, month } = record;
  const all = await askAll(env, id, record);
  const mine = mineKnown ?? (await env.SHARES.get(askWishKey(id, m), "json")) ?? { wishes: [], note: "" };
  if (!mine.seenAt) { mine.seenAt = new Date().toISOString(); await put(env, askWishKey(id, m), { ...mine, expiresAt: record.expiresAt }); }
  const me = record.members[m].name;
  const open = askOpen(record);
  const [y, mo] = month.split("-").map(Number);
  const length = new Date(Date.UTC(y, mo, 0)).getUTCDate();
  const firstDow = new Date(Date.UTC(y, mo - 1, 1)).getUTCDay();
  const iso = (n) => `${month}-${String(n).padStart(2, "0")}`;
  const rests = (d) => all.wishes.filter((w) => w.d === d && ASK_KINDS[w.k]?.[1]);
  const label = (w) => (w.k === "SHIFT" ? record.shifts.find((x) => x.id === w.s)?.label ?? "희망" : ASK_KINDS[w.k]?.[0] ?? "");
  const today = kstDay(new Date().toISOString());
  const cells = [];
  for (let i = 0; i < firstDow; i++) cells.push(`<div class="cell blank"></div>`);
  for (let n = 1; n <= length; n++) {
    const d = iso(n);
    const dow = (firstDow + n - 1) % 7;
    const cap = askCap(rules, d);
    const taken = rests(d).length;
    const my = mine.wishes.find((w) => w.d === d);
    const room = rules.blocked.includes(d) ? `<span class="leave">받지 않음</span>`
      : cap > 0 ? `<span class="leave${taken >= cap ? " full" : ""}">${taken >= cap ? "마감 " : ""}${taken}/${cap}</span>` : taken ? `<span class="leave">${taken}명</span>` : "";
    const chip = my ? `<span class="chip" style="background:${ASK_KINDS[my.k]?.[1] ? "#d14343" : "#3b5bdb"}">${esc(label(my))}</span>` : "";
    const cls = `cell${d === day ? " picked" : ""}${d === today ? " today" : ""}`;
    cells.push(`<a class="${cls}" href="/a/${key}?d=${d}#pick"><span class="num ${dow === 0 || rules.holidays.includes(d) ? "sun" : dow === 6 ? "sat" : ""}">${n}<span class="dow"> ${WEEKDAYS[dow]}</span></span>${chip}${room}</a>`);
  }
  const heads = ["일", "월", "화", "수", "목", "금", "토"].map((w, i) => `<div class="wd ${i === 0 ? "sun" : i === 6 ? "sat" : ""}">${w}</div>`).join("");
  const capLine = [rules.weekdayCap ? `평일 하루 ${rules.weekdayCap}명` : "", rules.weekendCap ? `주말·공휴일 하루 ${rules.weekendCap}명` : ""].filter(Boolean).join(", ");
  const closes = rules.closesAt != null ? (() => { const t = new Date(rules.closesAt + KST).toISOString(); const left = rules.closesAt - Date.now();
    return `마감 ${Number(t.slice(5, 7))}/${Number(t.slice(8, 10))} ${t.slice(11, 16)}${left > 0 ? ` (${left > 86400000 ? `${Math.floor(left / 86400000)}일` : `${Math.max(1, Math.floor(left / 3600000))}시간`} 남음)` : ""}`; })() : "";
  const restMine = mine.wishes.filter((w) => ASK_KINDS[w.k]?.[1]).length;
  const myLeave = record.members[m].leave;
  const leaveLine = myLeave != null ? `남은 연차 ${myLeave}일${leaveTaken(mine.wishes) ? ` (이번 신청 뒤 ${Math.round((myLeave - leaveTaken(mine.wishes)) * 100) / 100}일)` : ""}` : "";
  const rule = [capLine ? `쉬는 신청은 ${capLine}까지${rules.firstCome ? "(먼저 신청한 순)" : "(넘치면 리더가 공정하게 정해요)"}` : "", leaveLine,
    rules.perPerson ? `한 사람 ${rules.perPerson}일까지 (지금 ${restMine}일)` : "", closes].filter(Boolean).join(" · ");
  let panel = "";
  if (day && isDate(day) && day.startsWith(month)) {
    const my = mine.wishes.find((w) => w.d === day);
    const who = rests(day);
    const names = rules.showNames && who.length ? `<p class="muted">신청한 사람: ${who.map((w) => esc(record.members[w.m]?.name ?? "")).join(", ")}</p>` : "";
    const btn = (k, text, s = null, on = false) => `<form method="post" action="/a/${key}/pick"><input type="hidden" name="d" value="${day}"><input type="hidden" name="k" value="${k}">${s != null ? `<input type="hidden" name="s" value="${s}">` : ""}<button class="pick${on ? " on" : ""}">${esc(text)}</button></form>`;
    const choices = open && !rules.blocked.includes(day) ? [
      ...rules.kinds.filter((k) => k !== "SHIFT").map((k) => btn(k, ASK_KINDS[k][0], null, my?.k === k)),
      ...(rules.kinds.includes("SHIFT") ? record.shifts.map((x) => btn("SHIFT", `${x.label} 원해요`, x.id, my?.k === "SHIFT" && my?.s === x.id)) : []),
      ...(my ? [btn("none", "신청 지우기")] : []),
    ].join("") : `<p class="muted">${open ? "이 날은 신청을 받지 않아요." : "신청이 마감됐어요."}</p>`;
    const cap = askCap(rules, day);
    panel = `<section id="pick" class="panel"><h2>${Number(day.slice(5, 7))}/${Number(day.slice(8, 10))} ${["일", "월", "화", "수", "목", "금", "토"][new Date(day + "T00:00:00Z").getUTCDay()]}요일</h2>
<p>${cap > 0 ? `쉬는 신청 ${who.length}/${cap}명${who.length >= cap ? " · 자리가 다 찼어요" : ` · ${cap - who.length}자리 남음`}` : `쉬는 신청 ${who.length}명`}</p>${names}<div class="picks">${choices}</div></section>`;
  }
  const saidHtml = said ? `<p class="${said.bad ? "bad" : "done"}">${esc(said.bad ?? said.ok)}</p>` : "";
  const list = mine.wishes.length ? `<ul class="punches">${mine.wishes.map((w) => `<li><span>${Number(w.d.slice(5, 7))}/${Number(w.d.slice(8, 10))}</span><span>${esc(label(w))}</span></li>`).join("")}</ul>` : `<p class="muted">아직 신청한 날이 없어요. 달력에서 날을 눌러 주세요.</p>`;
  const note = open ? `<form method="post" action="/a/${key}/note" class="note"><input name="note" maxlength="${LIMITS.askNote}" value="${esc(mine.note ?? "")}" placeholder="예: NN 뒤 off 원해요"><button>남기기</button></form>
<p class="muted small">건강·가족 사정 같은 이유는 적지 말아 주세요. 필요하면 리더에게 직접 말해 주세요.</p>` : mine.note ? `<p>${esc(mine.note)}</p>` : "";
  const body = `<h1>${esc(record.team)} ${mo}월 근무 신청</h1><p class="muted">${esc(me)}님 · ${open ? "신청 받는 중" : "마감"}</p>
<p class="rule">${esc(rule)}</p>${saidHtml}${monthList(widestWord(mine.wishes.map(label)))}<div class="cal"><div class="grid">${heads}${cells.join("")}</div></div>${panel}
<h2>내 신청 ${mine.wishes.length}일</h2>${list}<h2>리더에게 한 줄</h2>${note}
<p class="foot muted">신청한 날·종류·한 줄·신청한 시각만 리더에게 가요. 다른 사람에게는 날마다 몇 명이 신청했는지만 보여요${rules.showNames ? "(리더가 이름도 보이게 했어요)" : ""}. 위치·전화번호·기기 정보는 보내지 않아요. 근무표가 확정되면 리더가 근무표 링크를 보내 줘요.</p>${spreadSlot("request")}`;
  return html(page(`${record.team} ${mo}월 근무 신청`, body + teamFoot("a", key), "밤낮", "request"), status);
}

// ---- 1.38 [P3-X-01] 결원 메우기 (and the other yes/no asks of a team) ----
// The leader's app asks a few people one thing ("12/7(월) 나이트 22:00~07:00, 대신 들어갈 수 있나요?"); each answers on their own
// page, the app reads the answers and the leader picks one, and every page then says who is coming. Each answer is its own key,
// so two people answering at once do not write over each other. Kept: the team's name, what was asked, the names asked, the answers
// and when. Nothing else.

const callKey = (id) => `call:${id}`;
const callPersonKey = (key) => `callm:${key}`;
const callAnswerKey = (id, p) => `calla:${id}:${p}`;

async function callCreate(request, env, url) {
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 8 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  const team = cleanText(body?.team, LIMITS.askTeam);
  const title = cleanText(body?.title, LIMITS.callTitle);
  const detail = cleanText(body?.detail ?? "", LIMITS.callDetail);
  const names = Array.isArray(body?.people) ? body.people.map((n) => cleanText(n, LIMITS.askName)) : [];
  if (!team || !title || detail == null || names.length === 0 || names.length > LIMITS.callPeople || names.some((n) => !n)) return json({ error: "bad_call" }, 400);
  const hours = Number.isInteger(body?.hours) ? Math.min(Math.max(body.hours, 1), LIMITS.callHours) : 48;
  const id = randomId(22);
  const token = randomId(32);
  const people = names.map((name) => ({ name, key: randomId(22) }));
  const record = { team, title, detail, people, tokenHash: await sha256(token), expiresAt: new Date(Date.now() + hours * 3600 * 1000).toISOString(), taken: null, closed: false };
  await put(env, callKey(id), record);
  for (let i = 0; i < people.length; i++) await put(env, callPersonKey(people[i].key), { id, p: i, expiresAt: record.expiresAt });
  count(env, "calls");
  return json({ id, token, expiresAt: record.expiresAt, people: people.map((x) => ({ name: x.name, key: x.key, url: `${url.origin}/v/${x.key}` })) }, 201);
}

async function callOwned(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(callKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

async function callRead(request, env, id) {
  const record = await callOwned(request, env, id);
  if (record instanceof Response) return record;
  const answers = [];
  const seen = [];
  for (let p = 0; p < record.people.length; p++) {
    const a = await env.SHARES.get(callAnswerKey(id, p), "json");
    if (!a) continue;
    if (a.seenAt) seen.push(p);
    if (a.a) answers.push({ p, a: a.a, at: a.at });
  }
  return json({ answers, seen, taken: record.taken, closed: record.closed, expiresAt: record.expiresAt });
}

async function callChange(request, env, id) {
  const record = await callOwned(request, env, id);
  if (record instanceof Response) return record;
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 512)); } catch { return json({ error: "bad_json" }, 400); }
  if (body?.taken !== undefined) {
    if (body.taken !== null && !(Number.isInteger(body.taken) && body.taken >= 0 && body.taken < record.people.length)) return json({ error: "bad_call" }, 400);
    record.taken = body.taken;
  }
  if (typeof body?.closed === "boolean") record.closed = body.closed;
  await put(env, callKey(id), record);
  return new Response(null, { status: 204 });
}

async function callDelete(request, env, id) {
  const record = await callOwned(request, env, id);
  if (record instanceof Response) return record;
  for (let p = 0; p < record.people.length; p++) {
    await kvDel(env, callAnswerKey(id, p));
    await kvDel(env, callPersonKey(record.people[p].key));
  }
  await kvDel(env, callKey(id));
  return new Response(null, { status: 204 });
}

async function callFind(env, key) {
  const link = await env.SHARES.get(callPersonKey(key), "json");
  if (!link) return null;
  const record = await env.SHARES.get(callKey(link.id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return null;
  return { id: link.id, p: link.p, record };
}

const callGone = () => html(page("요청이 끝났어요", `<p class="empty">이 요청은 끝났거나 리더가 지웠어요.</p>${goneNext("바뀐 근무가 있는지는 리더에게 물어봐 주세요.", "call")}`), 404);

async function callAnswer(request, env, key) {
  const found = await callFind(env, key);
  if (!found) return callGone();
  if (!(await allow(env, request, "ap", LIMITS.askPerHour))) return html(page("잠시 뒤에 다시 눌러 주세요", `<p class="empty">이 네트워크에서 너무 많이 눌렀어요. 잠시 뒤에 다시 눌러 주세요.</p>`), 429);
  const { id, p, record } = found;
  const form = new URLSearchParams((await request.text()).slice(0, 64));
  const a = form.get("a");
  if (a !== "yes" && a !== "no") return callView(env, key, { bad: "고를 수 없는 답이에요" }, 400, found);
  if (record.closed || record.taken != null) return callView(env, key, { bad: "이미 정해졌어요" }, 409, found);
  const mine = (await env.SHARES.get(callAnswerKey(id, p), "json")) ?? {};
  await put(env, callAnswerKey(id, p), { ...mine, a, at: new Date().toISOString(), expiresAt: record.expiresAt });
  count(env, "callAnswers");
  return callView(env, key, { ok: a === "yes" ? "들어갈 수 있다고 전했어요. 리더가 정하면 이 페이지와 근무표 링크에 나와요." : "어렵다고 전했어요. 고마워요." }, 200, found);
}

async function callView(env, key, said, status = 200, known = null) {
  const found = known ?? await callFind(env, key);
  if (!found) return callGone();
  const { id, p, record } = found;
  const mine = (await env.SHARES.get(callAnswerKey(id, p), "json")) ?? {};
  if (!mine.seenAt) await put(env, callAnswerKey(id, p), { ...mine, seenAt: new Date().toISOString(), expiresAt: record.expiresAt });
  const me = record.people[p].name;
  const done = record.taken != null;
  const state = done ? (record.taken === p ? `<p class="done">${esc(me)}님이 들어가기로 정해졌어요. 고마워요!</p>` : `<p class="muted">다른 분이 들어가기로 정해졌어요. 답해 줘서 고마워요.</p>`)
    : record.closed ? `<p class="muted">요청이 닫혔어요.</p>`
    // 1.39 G2 PC 5066b027: after an answer both big buttons stayed alike and read as 'not sent yet'. The picked one keeps its look
    // with ✓, the other shrinks and fades but can still be pressed to change the answer.
    : (() => {
      const pick = (a) => !mine.a ? "" : mine.a === a ? " picked" : " other";
      const mark = (a) => (mine.a === a ? "✓ " : "");
      return `<div class="buttons"><form method="post" action="/v/${key}/answer"><input type="hidden" name="a" value="yes"><button class="in${pick("yes")}">${mark("yes")}들어갈 수 있어요</button></form>` +
        `<form method="post" action="/v/${key}/answer"><input type="hidden" name="a" value="no"><button class="out${pick("no")}">${mark("no")}어려워요</button></form></div>` +
        (mine.a ? `<p class="muted">보낸 답: ${mine.a === "yes" ? "들어갈 수 있어요" : "어려워요"} · 다른 쪽을 누르면 바뀌어요</p>` : "");
    })();
  const saidHtml = said ? `<p class="${said.bad ? "bad" : "done"}">${esc(said.bad ?? said.ok)}</p>` : "";
  const body = `<h1>${esc(record.title)}</h1><p class="muted">${esc(record.team)} · ${esc(me)}님께</p>${record.detail ? `<p>${esc(record.detail)}</p>` : ""}${saidHtml}${state}
<p class="foot muted">답과 답한 시각만 리더에게 가요. 위치·전화번호·기기 정보는 보내지 않아요.</p>${spreadSlot("call")}`;
  return html(page(record.title, body + teamFoot("v", key), "밤낮", "call"), status);
}

// ---- 1.38 [P3-L-15] 봤어요 and 병동 전체 보기 ----
// 봤어요: a link the leader asks about keeps the time it was first opened after each change (one write per change at most).

const seenKey = (id) => `seen:${id}`;

/**
 * 1.39 G2 (숨은 문제 P1): a messenger fetching the link for its preview card (카카오톡·라인·슬랙…) or a search robot is not the member
 * opening it, so it does not count as 봤어요. A messenger's own in-app browser (KAKAOTALK, Line/) is a person and still counts.
 */
export function previewBot(request) {
  const ua = request.headers.get("user-agent") || "";
  return /kakaotalk-scrap|facebookexternalhit|slackbot|twitterbot|telegrambot|discordbot|linkedinbot|whatsapp\/|skypeuripreview|embedly|yeti\/|daumoa|googlebot|bingbot|applebot|naverbot|crawler|spider|[a-z]bot\//i.test(ua);
}

async function markSeen(env, id, record) {
  if (!record.watch) return;
  const seen = await env.SHARES.get(seenKey(id), "json");
  if (seen && Date.parse(seen.at) >= Date.parse(record.updatedAt ?? 0)) return;
  await put(env, seenKey(id), { at: new Date().toISOString(), expiresAt: record.expiresAt });
}

async function seenRead(request, env, id) {
  const record = await authorized(request, env, id);
  if (record instanceof Response) return record;
  if (!record.watch) await put(env, id, { ...record, watch: true });
  const seen = await env.SHARES.get(seenKey(id), "json");
  const at = seen && Date.parse(seen.at) >= Date.parse(record.updatedAt ?? 0) ? seen.at : null;
  return json({ seenAt: at, updatedAt: record.updatedAt ?? null });
}

// ---- 1.38 [P3-L-18] 피곤해요 팀 합계 ----
// Only a number per day is kept. A member's app adds one at most once a day; the leader sees the last week, and a day with
// fewer than LIMITS.tiredMin is shown without its number, so one tired person cannot be picked out.

const tiredKey = (id) => `tired:${id}`;

async function tiredCreate(request, env) {
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 4 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  const team = cleanText(body?.team, LIMITS.askTeam);
  if (!team) return json({ error: "bad_team" }, 400);
  const id = randomId(22);
  const token = randomId(32);
  await put(env, tiredKey(id), { team, days: {}, tokenHash: await sha256(token), expiresAt: expiry(LIMITS.tiredBoxDays) });
  return json({ id, token }, 201);
}

async function tiredOwned(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(tiredKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

async function tiredRead(request, env, id) {
  const record = await tiredOwned(request, env, id);
  if (record instanceof Response) return record;
  // Reading keeps the box alive while the leader uses it.
  if (Date.parse(record.expiresAt) - Date.now() < (LIMITS.tiredBoxDays - 7) * 86400 * 1000) await put(env, tiredKey(id), { ...record, expiresAt: expiry(LIMITS.tiredBoxDays) });
  const days = Array.from({ length: 7 }, (_, i) => kstDay(new Date(Date.now() - i * 86400 * 1000).toISOString())).map((d) => {
    const n = record.days[d] ?? 0;
    return { d, n: n >= LIMITS.tiredMin ? n : null };
  });
  return json({ min: LIMITS.tiredMin, days });
}

async function tiredDelete(request, env, id) {
  const record = await tiredOwned(request, env, id);
  if (record instanceof Response) return record;
  await kvDel(env, tiredKey(id));
  return new Response(null, { status: 204 });
}

async function tiredTie(request, env, id) {
  const record = await authorized(request, env, id);
  if (record instanceof Response) return record;
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 1024)); } catch { return json({ error: "bad_json" }, 400); }
  const box = body?.box ?? null;
  if (box !== null && !(typeof box === "string" && /^[A-Za-z0-9]{22}$/.test(box) && (await env.SHARES.get(tiredKey(box))))) return json({ error: "bad_box" }, 400);
  if ((record.tired ?? null) !== box) {
    const next = { ...record };
    if (box) next.tired = box; else delete next.tired;
    await put(env, id, next);
  }
  return new Response(null, { status: 204 });
}

async function tiredSay(request, env, id) {
  const said = await tiredAdd(request, env, id);
  if (said === "not_found" || said === "rate_limited") return json({ error: said }, said === "not_found" ? 404 : 429);
  return new Response(null, { status: 204 });
}

/** The key that marks a link's press for today (KST): the app's and the page's press are the same one. */
async function tiredOnce(env, id) {
  const day = kstDay(new Date().toISOString());
  return `tiredk:${(await sha256(`${env.RATE_SALT || "bamnat"}:${id}:${day}`)).slice(0, 24)}`;
}

/** Adds today's one for [id]'s team box: "ok", "again" (already today), "not_found" or "rate_limited". */
async function tiredAdd(request, env, id) {
  const record = await env.SHARES.get(id, "json");
  if (!record || record.removed || Date.parse(record.expiresAt) < Date.now() || !record.tired) return "not_found";
  const box = await env.SHARES.get(tiredKey(record.tired), "json");
  if (!box || Date.parse(box.expiresAt) < Date.now()) return "not_found";
  if (!(await allow(env, request, "td", LIMITS.createsPerHour))) return "rate_limited";
  const day = kstDay(new Date().toISOString());
  const once = await tiredOnce(env, id);
  if (await env.SHARES.get(once)) return "again";
  await kvPut(env, once, "1", { expirationTtl: 2 * 86400 });
  const keep = kstDay(new Date(Date.now() - LIMITS.tiredDays * 86400 * 1000).toISOString());
  const days = Object.fromEntries(Object.entries(box.days).filter(([d]) => d > keep));
  days[day] = (days[day] ?? 0) + 1;
  await put(env, tiredKey(record.tired), { ...box, days });
  count(env, "tired");
  return "ok";
}

/** 1.39 [IOS-04] For the page: null when the leader's box is gone, else whether today's press is already in. */
async function tiredState(env, id, record) {
  const box = await env.SHARES.get(tiredKey(record.tired), "json");
  if (!box || Date.parse(box.expiresAt) < Date.now()) return null;
  return { today: Boolean(await env.SHARES.get(await tiredOnce(env, id))) };
}

/** 1.39 [IOS-04] POST /s/:id/tired: the page's form, then back to the page with what happened (no script, no name). */
async function tiredPress(request, env, id) {
  const said = await tiredAdd(request, env, id);
  const t = said === "ok" || said === "again" ? "1" : said === "rate_limited" ? "wait" : "off";
  return new Response(null, { status: 303, headers: { location: `/s/${id}?t=${t}`, "cache-control": "no-store", "referrer-policy": "no-referrer" } });
}

/** 1.39 [IOS-05] The 밤낮 dial (brand/v2, the launcher icon) at 180 px, for a page an iPhone adds to its home screen. */
function touchIcon() {
  const bytes = Uint8Array.from(atob(TOUCH_ICON), (c) => c.charCodeAt(0));
  return new Response(bytes, { headers: { "content-type": "image/png", "cache-control": "public, max-age=604800", "x-content-type-options": "nosniff" } });
}

const boardKey = (id) => `board:${id}`;

function boardBody(body) {
  const team = cleanText(body?.team, LIMITS.askTeam);
  if (!team || !isMonth(body?.month)) return null;
  const [y, m] = body.month.split("-").map(Number);
  const days = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const names = Array.isArray(body?.names) ? body.names.map((n) => cleanText(n, LIMITS.askName)) : [];
  if (names.length === 0 || names.length > LIMITS.boardPeople || names.some((n) => !n)) return null;
  const grid = swapBoard({ labels: body?.labels }, names.length, days);
  if (!grid) return null;
  const colors = {};
  for (const [k, v] of Object.entries(body?.colors && typeof body.colors === "object" ? body.colors : {}).slice(0, LIMITS.askShifts * 2)) {
    const label = cleanText(k, LIMITS.askShiftLabel);
    if (label && isColor(v)) colors[label] = v;
  }
  const end = Date.UTC(y, m, 1) + LIMITS.boardDaysAfter * 86400 * 1000;
  return { team, month: body.month, names, rows: grid.rows, colors, expiresAt: new Date(end).toISOString() };
}

async function boardCreate(request, env, url) {
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 64 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  const board = boardBody(body);
  if (!board || Date.parse(board.expiresAt) < Date.now()) return json({ error: "bad_board" }, 400);
  const id = randomId(22);
  const token = randomId(32);
  await put(env, boardKey(id), { ...board, tokenHash: await sha256(token), updatedAt: new Date().toISOString() });
  count(env, "boards");
  return json({ id, token, url: `${url.origin}/b/${id}`, expiresAt: board.expiresAt }, 201);
}

async function boardOwned(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(boardKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

async function boardUpdate(request, env, id) {
  const record = await boardOwned(request, env, id);
  if (record instanceof Response) return record;
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 64 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  const board = boardBody(body);
  if (!board) return json({ error: "bad_board" }, 400);
  await put(env, boardKey(id), { ...board, tokenHash: record.tokenHash, updatedAt: new Date().toISOString() });
  return new Response(null, { status: 204 });
}

async function boardDelete(request, env, id) {
  const record = await boardOwned(request, env, id);
  if (record instanceof Response) return record;
  await kvDel(env, boardKey(id));
  return new Response(null, { status: 204 });
}

async function boardView(env, id) {
  const record = await env.SHARES.get(boardKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return html(page("팀 표가 없어요", `<p class="empty">이 표는 끝났거나 리더가 거뒀어요.</p>${goneNext("새 달 표는 리더에게 받아 주세요.", "board")}`), 404);
  const [y, m] = record.month.split("-").map(Number);
  const days = record.rows[0]?.length ?? 0;
  const wd = (d) => new Date(Date.UTC(y, m - 1, d)).getUTCDay();
  const head = Array.from({ length: days }, (_, i) => { const w = wd(i + 1); return `<th class="${w === 0 ? "sun" : w === 6 ? "sat" : ""}">${i + 1}</th>`; }).join("");
  const chip = (l) => l ? `<span class="chip" style="background:${record.colors[l] ?? "#5b5f73"}">${esc(l)}</span>` : "";
  const body = record.rows.map((r, p) => `<tr><th class="who">${esc(record.names[p])}</th>${r.map((l) => `<td>${chip(l)}</td>`).join("")}</tr>`).join("");
  // Each day's count per shift, in the order the shifts first appear.
  const order = [...new Set(record.rows.flat().filter(Boolean))];
  const counts = order.map((l) => `<tr><th class="who">${chip(l)}</th>${Array.from({ length: days }, (_, d) => `<td>${record.rows.filter((r) => r[d] === l).length || ""}</td>`).join("")}</tr>`).join("");
  const html_ = `<h1>${esc(record.team)} ${m}월 근무표</h1><p class="muted">리더가 확정한 표예요. 바뀌면 이 주소 그대로 새로 보여요.</p>
<div class="wide"><table class="ward"><thead><tr><th></th>${head}</tr></thead><tbody>${body}</tbody><tfoot><tr><th class="who">날마다</th><td colspan="${days}"></td></tr>${counts}</tfoot></table></div>
<p class="foot muted">이름과 근무 글자만 있어요. ${record.month.slice(0, 4)}년 ${m}월이 끝나고 ${LIMITS.boardDaysAfter}일 뒤에 지워져요.</p>${spreadSlot("board")}`;
  return html(page(`${record.team} ${m}월 근무표`, html_ + teamFoot("b", id), "밤낮", "board"));
}

// ---- 1.38 [P3-X-06] 근로계약서 링크 서명 ----
// The boss's phone writes the contract's items; the staff member reads them on a plain page and signs by typing their name.
// The server keeps the contract only while it waits (30 days at most) and the app deletes it once it has read the signature.

const signKey = (id) => `sign:${id}`;
const signPageKey = (key) => `signk:${key}`;
const sameName = (a, b) => a.replace(/\s+/g, "") === b.replace(/\s+/g, "");

async function signCreate(request, env, url) {
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 24 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  const place = cleanText(body?.place, LIMITS.clockPlace);
  const staff = cleanText(body?.staff, LIMITS.askName);
  const title = cleanText(body?.title, LIMITS.signTitle);
  const raw = Array.isArray(body?.items) ? body.items : [];
  // The body keeps its line breaks (one line per weekday, 임금 parts); any other control character goes.
  const lines = (v) => { if (typeof v !== "string") return null; const x = v.replace(/\r/g, "").replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, "").trim(); return x.length > LIMITS.signBody ? null : x; };
  const items = raw.map((i) => ({ t: cleanText(i?.t, LIMITS.signTitle), b: lines(i?.b ?? "") }));
  if (!place || !staff || !title || items.length === 0 || items.length > LIMITS.signItems || items.some((i) => !i.t || i.b == null)) return json({ error: "bad_sign" }, 400);
  const days = Number.isInteger(body?.days) ? Math.min(Math.max(body.days, 1), LIMITS.signDays) : 14;
  const id = randomId(22);
  const key = randomId(22);
  const token = randomId(32);
  const record = { place, staff, title, items, key, tokenHash: await sha256(token), expiresAt: new Date(Date.now() + days * 86400 * 1000).toISOString(), seenAt: null, signed: null };
  await put(env, signKey(id), record);
  await put(env, signPageKey(key), { id, expiresAt: record.expiresAt });
  count(env, "signs");
  return json({ id, token, key, url: `${url.origin}/k/${key}`, expiresAt: record.expiresAt }, 201);
}

async function signOwned(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(signKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

async function signRead(request, env, id) {
  const record = await signOwned(request, env, id);
  if (record instanceof Response) return record;
  return json({ seenAt: record.seenAt, signed: record.signed, expiresAt: record.expiresAt });
}

async function signDelete(request, env, id) {
  const record = await signOwned(request, env, id);
  if (record instanceof Response) return record;
  await kvDel(env, signPageKey(record.key));
  await kvDel(env, signKey(id));
  return new Response(null, { status: 204 });
}

async function signFind(env, key) {
  const link = await env.SHARES.get(signPageKey(key), "json");
  if (!link) return null;
  const record = await env.SHARES.get(signKey(link.id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return null;
  return { id: link.id, record };
}

const signGone = () => html(page("계약서 링크가 없어요", `<p class="empty">이 계약서 링크는 끝났거나 사장님이 거뒀어요. 서명했다면 사장님께 PDF를 받아 주세요.</p>${goneNext("", "sign")}`), 404);

async function signPost(request, env, key) {
  const found = await signFind(env, key);
  if (!found) return signGone();
  if (!(await allow(env, request, "ap", LIMITS.askPerHour))) return html(page("잠시 뒤에 다시 눌러 주세요", `<p class="empty">이 네트워크에서 너무 많이 눌렀어요. 잠시 뒤에 다시 눌러 주세요.</p>`), 429);
  const { id, record } = found;
  if (record.signed) return signView(env, key, { bad: "이미 서명했어요" }, 409, found);
  const form = new URLSearchParams((await request.text()).slice(0, 256));
  const name = cleanText(form.get("name") ?? "", LIMITS.askName) ?? "";
  if (form.get("agree") !== "1") return signView(env, key, { bad: "내용을 읽고 동의에 표시해 주세요" }, 400, found);
  if (!name || !sameName(name, record.staff)) return signView(env, key, { bad: `계약서에 적힌 이름(${record.staff})과 같게 써 주세요` }, 400, found);
  record.signed = { name, at: new Date().toISOString() };
  await put(env, signKey(id), record);
  count(env, "signed");
  return signView(env, key, { ok: "서명했어요. 사장님이 서명한 계약서 PDF를 보내 드려요." }, 200, found);
}

async function signView(env, key, said, status = 200, known = null) {
  const found = known ?? await signFind(env, key);
  if (!found) return signGone();
  const { id, record } = found;
  if (!record.seenAt) { record.seenAt = new Date().toISOString(); await put(env, signKey(id), record); }
  const items = record.items.map((i, n) => `<h2>${n + 1}. ${esc(i.t)}</h2>${i.b ? `<p>${esc(i.b).replace(/\n/g, "<br>")}</p>` : ""}`).join("");
  const saidHtml = said ? `<p class="${said.bad ? "bad" : "done"}">${esc(said.bad ?? said.ok)}</p>` : "";
  const form = record.signed
    ? `<p class="done">${esc(record.signed.name)}님이 ${kstDay(record.signed.at)} ${kstTime(record.signed.at)}에 서명했어요.</p>`
    : `<form class="sign panel" method="post" action="/k/${key}/sign"><label>이름 <input name="name" maxlength="${LIMITS.askName}" autocomplete="name" required></label>
<label><input type="checkbox" name="agree" value="1" required> 위 내용을 읽었고 동의해요</label><button class="in">서명하기</button></form>`;
  const body = `<h1>${esc(record.title)}</h1><p class="muted">${esc(record.place)} · ${esc(record.staff)}님</p>${saidHtml}${items}${form}
<p class="foot muted">이름과 서명한 시각만 사장님 앱에 가요. 서명한 계약서는 사장님 휴대폰과 PDF에만 남고, 이 페이지는 사장님이 받은 뒤나 ${kstDay(record.expiresAt)}에 지워져요.</p>${record.signed ? spreadSlot("sign") : ""}`;
  return html(page(record.title, body + teamFoot("k", key), "밤낮", "sign"), status);
}

// ---- 1.38 [P3-X-02] 바꾸기 요청 ----
// The leader's phone works out who may trade which day (the board's own checks) and puts that list here with each person's
// row of labels. A member asks from their link, the other says yes or no on theirs, and the leader approves in the app.

const swapKey = (id) => `swap:${id}`;
const swapPersonKey = (key) => `swapm:${key}`;
const swapAskKey = (id, p) => `swapr:${id}:${p}`;
const SWAP_STATES = ["asked", "yes", "no", "cancel", "done", "denied"];
const swapOpen = (s) => s === "asked" || s === "yes";

/** The leader's list of rows and allowed trades, checked to its shape: {labels:[[label per day]], ok:{p:{day:[q]}}}. */
function swapBoard(body, people, days) {
  const labels = Array.isArray(body?.labels) ? body.labels : null;
  if (!labels || labels.length !== people) return null;
  const rows = [];
  for (const row of labels) {
    if (!Array.isArray(row) || row.length !== days) return null;
    const r = row.map((l) => (l == null || l === "" ? "" : cleanText(l, LIMITS.askShiftLabel)));
    if (r.some((l) => l == null)) return null;
    rows.push(r);
  }
  const ok = {};
  const src = body?.ok && typeof body.ok === "object" ? body.ok : {};
  for (const [pk, daysMap] of Object.entries(src)) {
    const p = Number(pk);
    if (!Number.isInteger(p) || p < 0 || p >= people || !daysMap || typeof daysMap !== "object") return null;
    for (const [dk, qs] of Object.entries(daysMap)) {
      const d = Number(dk);
      if (!Number.isInteger(d) || d < 1 || d > days || !Array.isArray(qs) || qs.length > people) return null;
      if (qs.some((q) => !Number.isInteger(q) || q < 0 || q >= people || q === p)) return null;
      (ok[p] ??= {})[d] = [...new Set(qs)];
    }
  }
  return { rows, ok };
}

async function swapCreate(request, env, url) {
  if (await env.SHARES.get(CREATE_OFF)) return json({ error: "paused" }, 503);
  if (!(await allowCreate(request, env))) return json({ error: "rate_limited" }, 429);
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 128 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  const team = cleanText(body?.team, LIMITS.askTeam);
  const month = typeof body?.month === "string" && /^\d{4}-(0[1-9]|1[0-2])$/.test(body.month) ? body.month : null;
  const names = Array.isArray(body?.people) ? body.people.map((n) => cleanText(n, LIMITS.askName)) : [];
  if (!team || !month || names.length < 2 || names.length > LIMITS.askMembers || names.some((n) => !n)) return json({ error: "bad_swap" }, 400);
  const days = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)).getUTCDate();
  const board = swapBoard(body, names.length, days);
  if (!board) return json({ error: "bad_swap" }, 400);
  const id = randomId(22);
  const token = randomId(32);
  const people = names.map((name) => ({ name, key: randomId(22) }));
  // Until a week after the month ends, never more than the 신청 받기 cap.
  const end = Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 1) + 7 * 86400 * 1000;
  const expiresAt = new Date(Math.min(end, Date.now() + LIMITS.askTtlDays * 86400 * 1000)).toISOString();
  if (Date.parse(expiresAt) <= Date.now()) return json({ error: "bad_swap" }, 400);
  const record = { team, month, people, rows: board.rows, ok: board.ok, tokenHash: await sha256(token), expiresAt, closed: false };
  await put(env, swapKey(id), record);
  for (let i = 0; i < people.length; i++) await put(env, swapPersonKey(people[i].key), { id, p: i, expiresAt });
  count(env, "swaps");
  return json({ id, token, expiresAt, people: people.map((x) => ({ name: x.name, key: x.key, url: `${url.origin}/w/${x.key}` })) }, 201);
}

async function swapOwned(request, env, id) {
  const token = request.headers.get("x-delete-token") || "";
  const record = await env.SHARES.get(swapKey(id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return json({ error: "not_found" }, 404);
  if (!token || (await sha256(token)) !== record.tokenHash) return json({ error: "forbidden" }, 403);
  return record;
}

async function swapRead(request, env, id) {
  const record = await swapOwned(request, env, id);
  if (record instanceof Response) return record;
  const asks = [];
  const seen = [];
  for (let p = 0; p < record.people.length; p++) {
    const mine = await env.SHARES.get(swapAskKey(id, p), "json");
    if (!mine) continue;
    if (mine.seenAt) seen.push(p);
    for (const a of mine.asks ?? []) asks.push({ p, q: a.q, d: a.d, state: a.state, at: a.at });
  }
  return json({ asks, seen, closed: record.closed, expiresAt: record.expiresAt });
}

async function swapChange(request, env, id, url) {
  const record = await swapOwned(request, env, id);
  if (record instanceof Response) return record;
  let body;
  try { body = JSON.parse((await request.text()).slice(0, 128 * 1024)); } catch { return json({ error: "bad_json" }, 400); }
  // 1.39 [CARE-09] 전화·링크·계좌·메신저 옮기기는 팀 페이지에 싣지 않아요(사기 막기).
  const scam = scamRefusal(request, body);
  if (scam) return scam;
  if (body?.labels !== undefined) {
    const days = record.rows[0]?.length ?? 0;
    const board = swapBoard(body, record.people.length, days);
    if (!board) return json({ error: "bad_swap" }, 400);
    record.rows = board.rows;
    record.ok = board.ok;
  }
  if (typeof body?.closed === "boolean") record.closed = body.closed;
  await put(env, swapKey(id), record);
  // The leader's word on asks: {decide:[{p, d, state:"done"|"denied"}]}.
  const decide = Array.isArray(body?.decide) ? body.decide.slice(0, 100) : [];
  for (const x of decide) {
    if (!Number.isInteger(x?.p) || x.p < 0 || x.p >= record.people.length || !Number.isInteger(x?.d) || (x.state !== "done" && x.state !== "denied")) return json({ error: "bad_swap" }, 400);
    const mine = await env.SHARES.get(swapAskKey(id, x.p), "json");
    const ask = mine?.asks?.find((a) => a.d === x.d && swapOpen(a.state));
    if (!ask) continue;
    ask.state = x.state;
    ask.decidedAt = new Date().toISOString();
    await put(env, swapAskKey(id, x.p), { ...mine, expiresAt: record.expiresAt });
  }
  return new Response(null, { status: 204 });
}

async function swapDelete(request, env, id) {
  const record = await swapOwned(request, env, id);
  if (record instanceof Response) return record;
  for (let p = 0; p < record.people.length; p++) {
    await kvDel(env, swapAskKey(id, p));
    await kvDel(env, swapPersonKey(record.people[p].key));
  }
  await kvDel(env, swapKey(id));
  return new Response(null, { status: 204 });
}

async function swapFind(env, key) {
  const link = await env.SHARES.get(swapPersonKey(key), "json");
  if (!link) return null;
  const record = await env.SHARES.get(swapKey(link.id), "json");
  if (!record || Date.parse(record.expiresAt) < Date.now()) return null;
  return { id: link.id, p: link.p, record };
}

const swapGone = () => html(page("바꾸기가 끝났어요", `<p class="empty">이 바꾸기 링크는 끝났거나 리더가 지웠어요.</p>${goneNext("바뀐 근무는 리더가 새 근무표 링크로 보내 줘요.", "swap")}`), 404);

/** [P3-X-02] A member's press: ask (d, q), cancel (d), or reply to someone else's ask (from, d, a=yes|no). */
async function swapPress(request, env, key, what) {
  const found = await swapFind(env, key);
  if (!found) return swapGone();
  if (!(await allow(env, request, "ap", LIMITS.askPerHour))) return html(page("잠시 뒤에 다시 눌러 주세요", `<p class="empty">이 네트워크에서 너무 많이 눌렀어요. 잠시 뒤에 다시 눌러 주세요.</p>`), 429);
  const { id, p, record } = found;
  const form = new URLSearchParams((await request.text()).slice(0, 128));
  const d = Number(form.get("d"));
  const days = record.rows[0]?.length ?? 0;
  if (!Number.isInteger(d) || d < 1 || d > days) return swapView(env, key, { bad: "고를 수 없는 날이에요" }, 400, found);
  if (record.closed) return swapView(env, key, { bad: "리더가 바꾸기를 닫았어요" }, 409, found);
  const now = new Date().toISOString();
  if (what === "reply") {
    const from = Number(form.get("from"));
    const a = form.get("a");
    if (!Number.isInteger(from) || from < 0 || from >= record.people.length || (a !== "yes" && a !== "no")) return swapView(env, key, { bad: "고를 수 없는 답이에요" }, 400, found);
    const theirs = await env.SHARES.get(swapAskKey(id, from), "json");
    const ask = theirs?.asks?.find((x) => x.d === d && x.q === p && swapOpen(x.state));
    if (!ask) return swapView(env, key, { bad: "이미 끝난 요청이에요" }, 409, found);
    ask.state = a;
    ask.answeredAt = now;
    await put(env, swapAskKey(id, from), { ...theirs, expiresAt: record.expiresAt });
    count(env, "swapAnswers");
    return swapView(env, key, { ok: a === "yes" ? "좋다고 전했어요. 리더가 승인하면 근무표에 바로 반영돼요." : "어렵다고 전했어요." }, 200, found);
  }
  const mine = (await env.SHARES.get(swapAskKey(id, p), "json")) ?? {};
  const asks = mine.asks ?? [];
  if (what === "cancel") {
    const ask = asks.find((x) => x.d === d && swapOpen(x.state));
    if (!ask) return swapView(env, key, { bad: "거둘 요청이 없어요" }, 409, found);
    ask.state = "cancel";
    await put(env, swapAskKey(id, p), { ...mine, asks, expiresAt: record.expiresAt });
    return swapView(env, key, { ok: "요청을 거뒀어요." }, 200, found);
  }
  const q = Number(form.get("q"));
  if (!Number.isInteger(q) || !(record.ok?.[p]?.[d] ?? []).includes(q)) return swapView(env, key, { bad: "이 날은 그분과 바꿀 수 없어요" }, 400, found);
  if (asks.some((x) => x.d === d && swapOpen(x.state))) return swapView(env, key, { bad: "이 날은 이미 요청했어요. 거두고 다시 해 주세요" }, 409, found);
  if (asks.filter((x) => swapOpen(x.state)).length >= 5) return swapView(env, key, { bad: "한 번에 다섯 개까지 요청할 수 있어요" }, 409, found);
  // Old answered asks drop off first so the list stays small.
  const kept = asks.filter((x) => swapOpen(x.state) || x.state === "done").slice(-20);
  kept.push({ d, q, state: "asked", at: now });
  await put(env, swapAskKey(id, p), { ...mine, asks: kept, expiresAt: record.expiresAt });
  count(env, "swapAsks");
  return swapView(env, key, { ok: `${record.people[q].name}님께 요청했어요. 그분 링크에 바로 보여요.` }, 200, found);
}

async function swapView(env, key, said, status = 200, known = null) {
  const found = known ?? await swapFind(env, key);
  if (!found) return swapGone();
  const { id, p, record } = found;
  const mine = (await env.SHARES.get(swapAskKey(id, p), "json")) ?? {};
  if (!mine.seenAt) await put(env, swapAskKey(id, p), { ...mine, seenAt: new Date().toISOString(), expiresAt: record.expiresAt });
  const me = record.people[p].name;
  const name = (q) => esc(record.people[q]?.name ?? "?");
  const y = Number(record.month.slice(0, 4));
  const m = Number(record.month.slice(5, 7));
  const dayName = (d) => `${m}/${d}(${WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]})`;
  const label = (q, d) => esc(record.rows[q]?.[d - 1] || "·");
  const word = { asked: "답 기다림", yes: "상대가 좋대요 · 리더 승인 기다림", no: "상대가 어렵대요", cancel: "거둠", done: "리더가 승인했어요", denied: "리더가 거절했어요" };
  // Asks to me: everyone else's lists (a ward is at most 80 people, read once per open).
  const incoming = [];
  for (let o = 0; o < record.people.length; o++) {
    if (o === p) continue;
    const theirs = await env.SHARES.get(swapAskKey(id, o), "json");
    for (const a of theirs?.asks ?? []) if (a.q === p && a.state !== "cancel") incoming.push({ from: o, ...a });
  }
  const inHtml = incoming.length === 0 ? "" : `<h2>받은 요청</h2>` + incoming.map((a) => `<div class="panel"><p><b>${name(a.from)}</b>님: ${dayName(a.d)} 그분 ${label(a.from, a.d)} ↔ 내 ${label(p, a.d)}</p>` +
    (a.state === "asked" && !record.closed ? `<div class="picks"><form method="post" action="/w/${key}/reply"><input type="hidden" name="from" value="${a.from}"><input type="hidden" name="d" value="${a.d}"><input type="hidden" name="a" value="yes"><button class="pick on">좋아요</button></form>` +
      `<form method="post" action="/w/${key}/reply"><input type="hidden" name="from" value="${a.from}"><input type="hidden" name="d" value="${a.d}"><input type="hidden" name="a" value="no"><button class="pick">어려워요</button></form></div>`
      : `<p class="muted">${esc(word[a.state] ?? "")}</p>`) + `</div>`).join("");
  const sent = (mine.asks ?? []).filter((a) => a.state !== "cancel");
  const sentHtml = sent.length === 0 ? "" : `<h2>보낸 요청</h2>` + sent.map((a) => `<div class="panel"><p>${dayName(a.d)} 내 ${label(p, a.d)} ↔ <b>${name(a.q)}</b>님 ${label(a.q, a.d)} · ${esc(word[a.state] ?? "")}</p>` +
    (swapOpen(a.state) && !record.closed ? `<form method="post" action="/w/${key}/cancel"><input type="hidden" name="d" value="${a.d}"><button class="pick">요청 거두기</button></form>` : "") + `</div>`).join("");
  const options = record.ok?.[p] ?? {};
  const busyDays = new Set(sent.filter((a) => swapOpen(a.state)).map((a) => a.d));
  const rows = Object.keys(options).map(Number).sort((a, b) => a - b).filter((d) => !busyDays.has(d) && options[d].length > 0);
  const pickHtml = record.closed ? `<p class="muted">리더가 바꾸기를 닫았어요.</p>` : rows.length === 0 ? `<p class="muted">지금 규칙에 맞게 바꿀 수 있는 날이 없어요.</p>` :
    rows.map((d) => `<div class="panel"><p><b>${dayName(d)}</b> 내 ${label(p, d)}</p><form method="post" action="/w/${key}/ask"><input type="hidden" name="d" value="${d}">` +
      `<select name="q">${options[d].map((q) => `<option value="${q}">${name(q)} (${label(q, d)})</option>`).join("")}</select> <button class="pick on">바꾸기 요청</button></form></div>`).join("");
  const saidHtml = said ? `<p class="${said.bad ? "bad" : "done"}">${esc(said.bad ?? said.ok)}</p>` : "";
  const body = `<h1>${esc(record.team)} ${m}월 근무 바꾸기</h1><p class="muted">${esc(me)}님 · 근무 규칙(쉬는 시간·주 52시간)에 맞는 사람만 보여요</p>${saidHtml}${inHtml}${sentHtml}
<h2>바꿀 수 있는 날</h2>${pickHtml}
<p class="foot muted">고른 날·상대·답과 누른 시각만 리더에게 가요. 위치·전화번호·기기 정보는 보내지 않아요.</p>${spreadSlot("swap")}`;
  return html(page(`${record.team} 근무 바꾸기`, body + teamFoot("w", key), "밤낮", "swap"), status);
}

function groupCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  // 32 letters divide 256, so every letter is equally likely.
  return [...bytes].map((b) => GROUP_CHARS[b % 32]).join("");
}

// ---- [STAT-04] day counters ----

// [SRV-10] Every KV write is counted too (KV's free day is 1,000 writes), so the company server can rest
// 근무표 링크 새로 만들기 before the limit (N8). Like the rest, a rough number.
async function kvPut(env, key, value, options) {
  count(env, "writes");
  return env.SHARES.put(key, value, options);
}

async function kvDel(env, key) {
  count(env, "writes");
  return env.SHARES.delete(key);
}

const pending = new WeakMap();

function count(env, field) {
  if (!env.SHARES) return;
  const day = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10);
  let p = pending.get(env.SHARES);
  if (!p) pending.set(env.SHARES, (p = { since: Date.now(), days: {} }));
  const d = (p.days[day] ??= {});
  d[field] = (d[field] ?? 0) + 1;
}

/**
 * Adds what this Worker counted to the day keys, at most every [LIMITS.statsFlushMs] unless [now] is
 * asked. Two Workers flushing at the same moment can lose a few counts: these are rough numbers.
 */
export async function flushStats(env, now = true) {
  const p = env.SHARES && pending.get(env.SHARES);
  if (!p || Object.keys(p.days).length === 0) return;
  if (!now && Date.now() - p.since < LIMITS.statsFlushMs) return;
  pending.delete(env.SHARES);
  for (const [day, add] of Object.entries(p.days)) {
    const key = `stats:${day}`;
    const was = (await env.SHARES.get(key, "json")) ?? {};
    for (const [k, v] of Object.entries(add)) was[k] = (was[k] ?? 0) + v;
    was.writes = (was.writes ?? 0) + 1;
    await env.SHARES.put(key, JSON.stringify(was), { expirationTtl: LIMITS.statsDays * 86400 });
  }
}

/** 1.39 [IOS-06] The kinds [platformOf] tells apart. */
const PHONES = ["ios", "android", "other"];

async function stats(env, daysParam) {
  const n = Math.min(90, Math.max(1, parseInt(daysParam ?? "28", 10) || 28));
  await flushStats(env);
  const today = Date.now() + 9 * 3600 * 1000;
  const days = [];
  const raw = [];
  for (let i = n - 1; i >= 0; i--) {
    const day = new Date(today - i * 86400 * 1000).toISOString().slice(0, 10);
    const got = (await env.SHARES.get(`stats:${day}`, "json")) ?? {};
    raw.push(got);
    days.push({ day, created: got.created ?? 0, views: got.views ?? 0, reports: got.reports ?? 0, removed: got.removed ?? 0, writes: got.writes ?? 0,
      // 1.39 [IOS-06] People opening the pages, by kind of phone (근무표 /s and the team pages apart).
      phones: Object.fromEntries(PHONES.flatMap((k) => [[`views_${k}`, got[`views_${k}`] ?? 0], [`team_${k}`, got[`team_${k}`] ?? 0]])) });
  }
  // 1.39 [IOS-06] Summed from what was counted, so the team counts (clocks, asks, …) that a day's row does not list add up too
  // (they came out as null before).
  const sum = (k) => raw.reduce((a, g) => a + (g[k] ?? 0), 0);
  const phones = Object.fromEntries(PHONES.flatMap((k) => [`views_${k}`, `team_${k}`]).map((k) => [k, sum(k)]));
  // [SRV-08] Counted in each Worker and added up every few minutes: two at once can lose a few, so 대략.
  return { days, created: sum("created"), views: sum("views"), reports: sum("reports"), removed: sum("removed"), clocks: sum("clocks"), punches: sum("punches"), asks: sum("asks"), askPicks: sum("askPicks"), calls: sum("calls"), callAnswers: sum("callAnswers"), swaps: sum("swaps"), swapAsks: sum("swapAsks"), swapAnswers: sum("swapAnswers"), signs: sum("signs"), signed: sum("signed"), boards: sum("boards"), tired: sum("tired"), phones, approximate: true };
}

// ---- input ----

async function readSchedule(request) {
  const type = request.headers.get("content-type") || "";
  if (!type.startsWith("application/json")) return { error: "content_type" };
  const text = await request.text();
  if (new TextEncoder().encode(text).length > LIMITS.bodyBytes) return { error: "too_large" };
  let body;
  try { body = JSON.parse(text); } catch { return { error: "bad_json" }; }
  return validate(body);
}

/**
 * Keeps only the fields the page uses, in the shapes it expects; anything else is refused.
 * v1 is shifts and leave; v2 [SHR-01] may add what the sharer ticked: leave kinds, extra work,
 * memos and pay. A v2 field the sharer did not tick in `shows` is dropped, not stored, and
 * without 휴가 종류 a leave keeps only how much of the day it takes.
 */
export function validate(body) {
  if (!body || typeof body !== "object" || (body.v !== 1 && body.v !== 2)) return { error: "version" };
  const v2 = body.v === 2;
  let shows = [];
  if (v2) {
    if (!Array.isArray(body.shows) || body.shows.some((s) => !SHOWS.includes(s))) return { error: "shows" };
    shows = SHOWS.filter((s) => body.shows.includes(s));
  }
  // [SHR-12] No ttlDays: a new link gets 30 days, an update keeps its end date.
  const ttlDays = body.ttlDays == null ? null : LIMITS.ttlDays.includes(body.ttlDays) ? body.ttlDays : 30;
  const title = cleanText(body.title ?? "", LIMITS.title);
  if (title === null) return { error: "title" };
  if (!Array.isArray(body.types) || body.types.length > LIMITS.types) return { error: "types" };
  const types = [];
  for (const t of body.types) {
    // 2.0.0 [RVD-28] SD-08 (core ShiftRules, 근무 종류 한 규칙표): a name of 20 letters, cut to them when longer, never the link's end.
    const name = nameOf(t?.name);
    // 2.0.0 [RVD-26] SN-16: 근무 글자 as the app and a 작품 keep it, two letters (👩‍⚕️ is one) up to 16 code units ([shortOf]). [RVD-28] One whose
    // first letter alone is longer (a long joined emoji) takes the name's first letter, as the app does for an empty one.
    const short = shortOf(t?.short) ?? (typeof t?.short === "string" && t.short.trim() && name ? shortOf(letters(name)[0]) : null);
    if (!Number.isInteger(t?.id) || !name || !short || !isColor(t?.color)) return { error: "type" };
    if (t.time != null && !isTimeRange(t.time)) return { error: "type_time" };
    // [SHR-02] The shift's icon key (sun, moon …), so the app draws the badge as the sharer sees it. 2.0.0 [RVD-22] or an emoji ('e:🌙').
    // [RVD-28] SD-08 One the rule does not know is dropped and the link made (총괄 11-12 '버리기, 거절 아님'): the badge shows the 글자.
    // 2.0.0 [RVD-20] HQ-13 하루 두 구간 (설계 4-4절): where the shift stops between its two parts ('15:00-17:00'), inside `time` with 30
    // minutes or more each side, as the app's WorkTime.gapFits. A receiver before 2.0.0 reads `time` alone, first start to last end.
    if (t.gap != null && !(t.time != null && t.off !== true && isTimeRange(t.gap) && gapFits(t.time, t.gap))) return { error: "type_gap" };
    // 2.0.0 [RVD-23] PC-94: the shift's 휴게 (ShiftType.breakMinutes, minutes) and, when known, when it starts, so the receiver does not count it
    // as work. 0 is '휴게 없음' and differs from no field (an app before 2.0.0, not known). Older receivers ignore both.
    // 총괄 11-12 (PC-92의 교훈): a rest that is not the app's is dropped, never the link; the receiver then asks for the 휴게 itself.
    const rest = restOf(t);
    types.push({ id: t.id, name, short, color: t.color, time: t.time ?? null, off: t.off === true, icon: iconOk(t.icon) ? t.icon : null, ...(t.gap != null ? { gap: t.gap } : {}),
      ...rest });
  }
  const ids = new Set(types.map((t) => t.id));
  if (!Array.isArray(body.days) || body.days.length > LIMITS.days) return { error: "days" };
  // [SHR-11] A shop or a freelancer may have no shifts at all and send only the months' money.
  const moneyOnly = v2 && shows.includes("pay") && Array.isArray(body.pay) && body.pay.length > 0;
  if (body.days.length === 0 && !moneyOnly) return { error: "days" };
  const days = [];
  const seen = new Set();
  for (const d of body.days) {
    // A day may have no shift of its own: a 대근 on a day off, or in v2 only a memo or extra work.
    if (!isDate(d?.date) || seen.has(d.date) || (d?.type != null && !ids.has(d.type))) return { error: "day" };
    seen.add(d.date);
    if (d.second != null && !ids.has(d.second)) return { error: "day_second" };
    let leave = d.leave == null ? null : cleanText(d.leave, LIMITS.leave);
    if (leave === null && d.leave != null) return { error: "day_leave" };
    if (leave && !shows.includes("leaveKinds")) leave = plainLeave(leave);
    const day = { date: d.date, type: d.type ?? null, second: d.second ?? null, leave };
    // 2.0.0 [RVD-20] HQ-13 An hour's leave (설계 4-4절): its minutes, and where in the day: from a minute of the day (외출) or off the start,
    // else off the end. Below half a day "leave" is empty, so a receiver before 2.0.0 sees a worked day, never a day off.
    if (d.leaveMinutes != null) {
      if (!(Number.isInteger(d.leaveMinutes) && d.leaveMinutes > 0 && d.leaveMinutes < DAY_MINUTES)) return { error: "day_leave" };
      if (d.leaveAt != null && !(Number.isInteger(d.leaveAt) && d.leaveAt >= 0 && d.leaveAt < DAY_MINUTES)) return { error: "day_leave" };
      if (d.leaveStart != null && (typeof d.leaveStart !== "boolean" || (d.leaveStart && d.leaveAt != null))) return { error: "day_leave" };
      day.leaveMinutes = d.leaveMinutes;
      if (d.leaveAt != null) day.leaveAt = d.leaveAt;
      if (d.leaveStart === true) day.leaveStart = true;
    } else if (d.leaveAt != null || d.leaveStart != null) return { error: "day_leave" };
    // [SHR-04] The sharer's app says whether the day has no work at all as far as the link shows, for 같이 쉬는 날.
    if (typeof d.rest === "boolean") day.rest = d.rest;
    if (shows.includes("memos") && d.memo != null) {
      const memo = cleanText(d.memo, LIMITS.memo);
      if (memo === null) return { error: "day_memo" };
      if (memo) day.memo = memo;
    }
    if (shows.includes("extras") && d.extras != null) {
      if (!Array.isArray(d.extras) || d.extras.length > LIMITS.extras) return { error: "day_extras" };
      const extras = [];
      for (const e of d.extras) {
        const name = cleanText(e?.name, LIMITS.extraName);
        if (!name) return { error: "day_extra" };
        if (e.time != null && !isTimeRange(e.time)) return { error: "day_extra" };
        if (e.minutes != null && !(Number.isInteger(e.minutes) && e.minutes > 0 && e.minutes <= 1440)) return { error: "day_extra" };
        extras.push({ name, time: e.time ?? null, minutes: e.minutes ?? null });
      }
      if (extras.length) day.extras = extras;
    }
    if (shows.includes("jobs") && d.jobs != null) {
      if (!Array.isArray(d.jobs) || d.jobs.length > LIMITS.jobs) return { error: "day_jobs" };
      const jobs = [];
      for (const j of d.jobs) {
        const name = cleanText(j?.name, LIMITS.jobName);
        if (!name) return { error: "day_job" };
        if (j.time != null && !isTimeRange(j.time)) return { error: "day_job" };
        jobs.push({ name, time: j.time ?? null });
      }
      if (jobs.length) day.jobs = jobs;
    }
    if (day.type == null && day.second == null && !day.leave && !day.leaveMinutes && !day.memo && !day.extras && !day.jobs) return { error: "day" };
    days.push(day);
  }
  days.sort((a, b) => a.date.localeCompare(b.date));
  const schedule = { v: v2 ? 2 : 1, title, types, days };
  // 1.39 PC (SPREAD 2차): a team leader's 팀원 링크 says so from its first 확정 (before, only 바뀜·같이 쉬어요 notes told), so the
  // page's card and preview speak to a teammate. A mark only, nothing about the team.
  if (body.team != null && typeof body.team !== "boolean") return { error: "team" };
  if (body.team === true) schedule.team = true;
  // [FEAT-15] The sharer turned on the address calendar apps subscribe to, in the time zone of their phone.
  if (body.ics != null && typeof body.ics !== "boolean") return { error: "ics" };
  if (body.ics === true) {
    schedule.ics = true;
    const tz = body.tz ?? "Asia/Seoul";
    if (typeof tz !== "string" || !/^[A-Za-z]+(?:\/[A-Za-z0-9_+-]+){1,2}$/.test(tz) || tz.length > 40) return { error: "tz" };
    schedule.tz = tz;
  }
  if (v2) {
    schedule.shows = shows;
    if (shows.includes("pay") && body.pay != null) {
      if (!Array.isArray(body.pay) || body.pay.length > LIMITS.payMonths) return { error: "pay" };
      const pay = [];
      for (const p of body.pay) {
        const label = cleanText(p?.label ?? "", LIMITS.payLabel);
        if (!isMonth(p?.month) || !Number.isInteger(p?.amount) || p.amount < 0 || p.amount > LIMITS.payAmount || label === null) {
          return { error: "pay" };
        }
        pay.push({ month: p.month, amount: p.amount, label });
      }
      pay.sort((a, b) => a.month.localeCompare(b.month));
      schedule.pay = pay;
      const payKind = body.payKind ?? "pay";
      if (!Object.hasOwn(PAY_KINDS, payKind)) return { error: "pay_kind" };
      schedule.payKind = payKind;
    }
  }
  // [SHR-20] Who the link is for; a link with no audience is one made before 1.33.
  if (body.audience != null) {
    if (!AUDIENCES.includes(body.audience)) return { error: "audience" };
    schedule.audience = body.audience;
  }
  if (body.setup != null) {
    if (schedule.audience !== "coworker" || typeof body.setup !== "string" || body.setup.length > LIMITS.setup) return { error: "setup" };
    let file;
    try { file = JSON.parse(body.setup); } catch { return { error: "setup" }; }
    if (!file || typeof file !== "object" || Array.isArray(file) || file.format !== "bamnat-company") return { error: "setup" };
    schedule.setup = body.setup;
  }
  if (body.look != null) {
    const look = recipeText(body.look, LIMITS.look, LOOK_KINDS, LOOK_KINDS.length);
    if (look === null) return { error: "look" };
    schedule.look = look;
  }
  if (body.kit != null) {
    const kit = recipeText(body.kit, LIMITS.kit, KIT_KINDS, LIMITS.kitPieces);
    if (kit === null) return { error: "kit" };
    schedule.kit = kit;
  }
  // 1.38 [SLEEP-11] 지금 자는 중: only when the sleeper ticked it, two times (epoch millis) and nothing else. The app shows it to the
  // receiver while it is fresh; the web page (/s/:id) never shows it.
  if (body.sleepNow != null) {
    const s = sleepNow(body.sleepNow);
    if (s === null) return { error: "sleep_now" };
    schedule.sleepNow = s;
  }
  // 1.39 G5-N4 계획한 잠 시간: only when the sender ticked it, pairs of times (epoch millis) and nothing else. Like 지금 자는 중, only
  // the receiving app shows it; the web page (/s/:id) never does.
  if (body.sleepPlan != null) {
    const p = sleepPlan(body.sleepPlan);
    if (p === null) return { error: "sleep_plan" };
    schedule.sleepPlan = p;
  }
  return { schedule, ttlDays };
}

/** 1.39 G5-N4 [{from, to}] in epoch millis: at most 24, each a from before a to at most 16 hours on. Null refuses it. */
function sleepPlan(v) {
  if (!Array.isArray(v) || v.length > 24) return null;
  const out = [];
  for (const w of v) {
    if (!w || typeof w !== "object" || Array.isArray(w)) return null;
    if (Object.keys(w).some((k) => k !== "from" && k !== "to")) return null;
    if (!Number.isSafeInteger(w.from) || !Number.isSafeInteger(w.to) || w.from <= 0 || w.to <= w.from || w.to - w.from > 16 * 3600 * 1000) return null;
    out.push({ from: w.from, to: w.to });
  }
  return out;
}

/** 1.38 [SLEEP-11] {since, wakeAt?} in epoch millis: since first, the wake within 20 hours of it. Null refuses it. */
function sleepNow(v) {
  if (!v || typeof v !== "object" || Array.isArray(v)) return null;
  const keys = Object.keys(v);
  if (keys.some((k) => k !== "since" && k !== "wakeAt")) return null;
  if (!Number.isSafeInteger(v.since) || v.since <= 0) return null;
  if (v.wakeAt == null) return { since: v.since };
  if (!Number.isSafeInteger(v.wakeAt) || v.wakeAt <= v.since || v.wakeAt - v.since > 20 * 3600 * 1000) return null;
  return { since: v.since, wakeAt: v.wakeAt };
}

/**
 * 1.37 [SHR-31][SHR-32] A recipe's text as the app sent it, kept as it is when its shape fits: the recipe format, at most [maxPieces]
 * pieces, each of [kinds] and once. The receiving app reads it with its own recipe codec, which checks every field; the server only
 * makes sure nothing else rides along (a 근무 주기 with its pay part above all). Null refuses it.
 */
function recipeText(text, max, kinds, maxPieces) {
  if (typeof text !== "string" || text.length === 0 || text.length > max) return null;
  let r;
  try { r = JSON.parse(text); } catch { return null; }
  if (!r || typeof r !== "object" || Array.isArray(r) || r.format !== "bamnat-recipe") return null;
  if (!Array.isArray(r.pieces) || r.pieces.length === 0 || r.pieces.length > maxPieces) return null;
  const seen = new Set();
  for (const p of r.pieces) {
    if (!p || typeof p !== "object" || !kinds.includes(p.kind) || seen.has(p.kind)) return null;
    seen.add(p.kind);
    if (p.kind === "company") {
      if (typeof p.setup !== "string") return null;
      let file;
      try { file = JSON.parse(p.setup); } catch { return null; }
      if (!file || typeof file !== "object" || file.format !== "bamnat-company" || Object.hasOwn(file, "pay")) return null;
    }
  }
  return text;
}

function cleanText(value, max) {
  if (typeof value !== "string") return null;
  const s = value.replace(/[\u0000-\u001f\u007f]/g, "").trim();
  return s.length > max ? null : s;
}
const isColor = (c) => typeof c === "string" && /^#[0-9A-Fa-f]{6}$/.test(c);
const isMonth = (m) => typeof m === "string" && /^\d{4}-(0[1-9]|1[0-2])$/.test(m);
const isDate = (d) => typeof d === "string" && /^\d{4}-\d{2}-\d{2}$/.test(d) && !Number.isNaN(Date.parse(d + "T00:00:00Z"));
const isTimeRange = (t) => typeof t === "string" && /^([01]\d|2[0-3]):[0-5]\d-([01]\d|2[0-3]):[0-5]\d$/.test(t);
/**
 * 2.0.0 [RVD-22] PC-92: one emoji as the app's 이모지 아이콘 sends it since 1.34 (ShiftIcon.emojiKey, "e:" + the emoji from the picker,
 * Emoji 15.0). One picture, a keycap (#️⃣), a flag (🇰🇷, 🏴 with tags) or those joined by ZWJ, with the skin tone and VS16 forms; the
 * longest the picker gives is 10 code points (🧑🏻‍❤️‍💋‍🧑🏼), so 12 at most. Letters, controls, '<', quotes and spaces are refused. The page and
 * the calendar feed never print it; it goes back to the receiving app in the JSON only (an app before 1.34 draws no icon for it).
 */
/**
 * 2.0.0 [RVD-26] SN-16 (스튜디오 제작 오류 조사): the letters of [s] as a person counts them, core RecipeText.graphemes cut apart (company-worker
 * letters.js counts the same way; test/rvd26-short.test.mjs holds the two together): an emoji joined by ZWJ, with its VS16·skin tone·tags or
 * a keycap is one, and so is a flag's pair.
 */
export function letters(s) {
  const out = [];
  let lead = "";
  const cps = [...s];
  let flagHalf = false;
  // [RVD-28] SD-08 core ShiftRules.letters: marks with no letter before them go with the next (so the count is graphemes' always).
  const join = (x) => { if (out.length) out[out.length - 1] += x; else lead += x; };
  const push = (x) => { out.push(lead + x); lead = ""; };
  for (let i = 0; i < cps.length; i++) {
    const cp = cps[i].codePointAt(0);
    if (cp === 0x200d) {
      join(cps[i] + (cps[i + 1] ?? ""));
      i++;
    } else if ((cp >= 0xfe00 && cp <= 0xfe0f) || (cp >= 0x1f3fb && cp <= 0x1f3ff) || (cp >= 0xe0020 && cp <= 0xe007f) || /[\p{Mn}\p{Me}]/u.test(cps[i])) {
      join(cps[i]);
    } else if (cp >= 0x1f1e6 && cp <= 0x1f1ff) {
      if (flagHalf) join(cps[i]); else push(cps[i]);
      flagHalf = !flagHalf;
      continue;
    } else push(cps[i]);
    flagHalf = false;
  }
  return out;
}

/** [RVD-28] SD-08 core ShiftRules.cut: [s] when it has at most [max] letters in [units] code units, else as many of its first letters as fit. */
export function cutLetters(s, max, units) {
  const all = letters(s);
  if (s.length <= units && all.length <= max) return s;
  let out = "";
  for (const l of all.slice(0, max)) {
    if ((out + l).length > units) break;
    out += l;
  }
  return out;
}

/**
 * [RVD-26] SN-16 근무 글자: what 4 code units took before goes as it is; longer is its first two letters up to 16 code units (an app before
 * 2.0.0 cut it at 4, half an emoji). More is cut, not refused: a 근무 글자 never stops the link (총괄 11-12, PC-92의 교훈). Null when empty.
 * [RVD-28] SD-08 The numbers are core ShiftRules' (shift-rules.js).
 */
export function shortOf(v) {
  const { letters: max, units, before } = SHIFT_RULES.label;
  const s = cleanText(v, 200);
  if (!s) return null;
  if (s.length <= before.link) return s;
  return cutLetters(s, max, units) || null;
}

/**
 * [RVD-28] SD-08 A kind of day's name (core ShiftRules): what 20 code units took before (an app before 2.0.0 cut it so), or 20 letters up to
 * 80 code units (a 작품's 근무 꾸미기 counts letters); longer is cut to its first letters that fit, never refused. Null when empty.
 */
export function nameOf(v) {
  const { letters: max, units, before } = SHIFT_RULES.name;
  const s = cleanText(v, 2000);
  if (!s) return null;
  if (s.length <= before.link) return s;
  return cutLetters(s, max, units) || null;
}

// [RVD-28] SD-08 The pattern and the numbers are core ShiftRules' (shift-rules.js); the company server checks a 근무 그림 with the same.
const ICON_KEY = new RegExp(SHIFT_RULES.icon.key);
const EMOJI_ICON = new RegExp(SHIFT_RULES.icon.emoji, "u");
export const iconOk = (icon) => typeof icon === "string" &&
  (ICON_KEY.test(icon) || ([...icon].length - SHIFT_RULES.icon.prefix.length <= SHIFT_RULES.icon.emojiCodePoints && EMOJI_ICON.test(icon)));
const DAY_MINUTES = 24 * 60;
const minuteOf = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));
/** 2.0.0 [RVD-20] HQ-13 "HH:mm-HH:mm" as minutes of the day, the end past midnight when it is not after the start (a whole day when equal). */
function rangeMinutes(range) {
  const [from, to] = range.split("-").map(minuteOf);
  return { from, length: (to - from + DAY_MINUTES) % DAY_MINUTES || DAY_MINUTES };
}
/** 2.0.0 [RVD-20] HQ-13 The app's WorkTime.gapFits: a gap of 30 minutes or more inside the shift, with a part of 30 minutes or more each side. */
function gapFits(time, gap) {
  const shift = rangeMinutes(time);
  const between = rangeMinutes(gap);
  const first = (between.from - shift.from + DAY_MINUTES) % DAY_MINUTES;
  const { min, part } = SHIFT_RULES.gap;
  return between.length >= min && between.length < DAY_MINUTES && first >= part && first + between.length + part <= shift.length;
}
/**
 * 2.0.0 [RVD-23] PC-94 (갈래 G와 맞춤, 11-12): `rest` whole minutes 0~720, shorter than the shift (with its gap, if any); `restStart` 'HH:MM' when the
 * app knows it, starting inside the shift with the whole rest inside it. Kept only on a shift with times that is not a day off. What does not fit
 * is dropped, not refused (총괄 11-12): a phone with a 휴게 longer than the shift still makes its link, and the receiver, seeing no rest, asks for
 * it. A wrong restStart drops only itself; restStart is dropped with a rest of 0. Returns the fields to keep ({} for none).
 */
function restOf(t) {
  if (t.rest == null || t.time == null || t.off === true) return {};
  if (!Number.isInteger(t.rest) || t.rest < 0 || t.rest > SHIFT_RULES.rest.max) return {};
  const shift = rangeMinutes(t.time);
  if (t.rest + (t.gap != null ? rangeMinutes(t.gap).length : 0) >= shift.length) return {};
  if (t.restStart == null || t.rest === 0) return { rest: t.rest };
  if (typeof t.restStart !== "string" || !/^([01]\d|2[0-3]):[0-5]\d$/.test(t.restStart)) return { rest: t.rest };
  const from = (minuteOf(t.restStart) - shift.from + DAY_MINUTES) % DAY_MINUTES;
  return from + t.rest <= shift.length ? { rest: t.rest, restStart: t.restStart } : { rest: t.rest };
}
/** 2.0.0 [RVD-20] HQ-13 A shift's times as the page and a calendar app show them: two parts when it stops between them. */
function parts(t) {
  if (!t.time) return [null];
  if (!t.gap) return [t.time];
  const [start, end] = t.time.split("-");
  const [gapStart, gapEnd] = t.gap.split("-");
  return [`${start}-${gapStart}`, `${gapEnd}-${end}`];
}

// ---- storage ----

async function put(env, id, record) {
  const ttl = Math.max(60, Math.ceil((Date.parse(record.expiresAt) - Date.now()) / 1000));
  await kvPut(env, id, JSON.stringify(record), { expirationTtl: ttl });
}

function expiry(days) {
  return new Date(Date.now() + days * 86400 * 1000).toISOString();
}

/** A per-hour count of new links per caller. The key is a salted hash, so no address is stored. */
const allowCreate = (request, env) => allow(env, request, "rl", LIMITS.createsPerHour);

/**
 * [SRV-08] KV has no atomic add, so twenty requests at the same moment could all read the same count.
 * Each Worker also counts in memory (synchronously, before any await), and the larger of the two counts
 * decides: a burst from one caller reaches one Worker and is held to [max]. Across Workers the limit is
 * still approximate (KV is eventually consistent); Durable Objects would make it exact but are not on
 * the free plan this server is meant for (README).
 */
const counted = new WeakMap();

/** 1.36 [SEC-01] An IPv4 address as it is, an IPv6 address by its /64 (one subscriber's line), as the company server counts. */
export function netOf(ip) {
  const a = String(ip ?? "").trim().toLowerCase();
  if (!a.includes(":")) return a || "unknown";
  const [head, tail] = a.split("::");
  const h = head ? head.split(":") : [];
  const t = tail !== undefined && tail ? tail.split(":") : [];
  const last = (t.length ? t : h)[(t.length ? t : h).length - 1] ?? "";
  if (last.includes(".")) return last;
  const full = tail === undefined ? h : [...h, ...Array(Math.max(0, 8 - h.length - t.length)).fill("0"), ...t];
  return full.slice(0, 4).map((x) => (x || "0").replace(/^0+(?=.)/, "")).join(":") + "::/64";
}

/**
 * 1.36 [SEC-02] Reads (the page, the app's 받은 달력, the calendar feed, a group) are held to [LIMITS.readsPerHour] an hour per
 * network, counted in this Worker's memory only: a KV write per read would spend the day's writes faster than a bot its reads.
 * Before, nothing held them, and one caller could spend KV's daily reads and stop every shared calendar. A group's code is
 * also held to [LIMITS.groupReadsPerHour] tries an hour, so its 6 letters cannot be guessed through.
 */
const reads = new Map();

export function readAllowed(request, kind, max) {
  const hour = Math.floor(Date.now() / 3600000);
  const key = `${kind}|${netOf(request.headers.get("cf-connecting-ip"))}|${hour}`;
  const n = (reads.get(key) ?? 0) + 1;
  reads.set(key, n);
  if (reads.size > 20000) for (const k of reads.keys()) if (!k.endsWith(`|${hour}`)) reads.delete(k);
  return n <= max;
}

/** For tests: forget the read counts. */
export function readsForget() { reads.clear(); }

async function allow(env, request, prefix, max) {
  const ip = netOf(request.headers.get("cf-connecting-ip"));
  const hour = Math.floor(Date.now() / 3600000);
  const local = `${prefix}|${ip}|${hour}`;
  let counts = counted.get(env.SHARES);
  if (!counts) counted.set(env.SHARES, (counts = new Map()));
  const mine = (counts.get(local) ?? 0) + 1;
  counts.set(local, mine);
  if (counts.size > 5000) for (const k of counts.keys()) if (!k.endsWith(`|${hour}`)) counts.delete(k);
  if (mine > max) return false;
  // "rl" keeps the hash it always had, so a deploy does not reset the hour's count.
  const salt = prefix === "rl" ? "" : `${prefix}:`;
  const key = `${prefix}:${(await sha256(`${env.RATE_SALT || "bamnat"}:${salt}${ip}:${hour}`)).slice(0, 24)}`;
  // The first use here adds to what other Workers counted; after that this Worker's own count leads.
  const stored = parseInt((await env.SHARES.get(key)) || "0", 10);
  const n = Math.max(mine === 1 ? stored + 1 : stored, mine);
  if (n > max) return false;
  await kvPut(env, key, String(n), { expirationTtl: 3600 });
  return true;
}

function randomId(length) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  let out = "";
  // 256 % 57 leaves a small bias; rejecting the top values keeps every character equally likely.
  for (const b of bytes) out += b < 228 ? ID_CHARS[b % 57] : "";
  return out.length >= length ? out.slice(0, length) : out + randomId(length - out.length);
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// ---- 1.39 [SPREAD-01..04] 받는 쪽 페이지 끝의 '나도 써 보기' 한 줄 ----
// The person who got a link does what they came for first (보기·출퇴근·답·서명); only then, at the very end of the page, one
// small card says what 밤낮 would do for them, with a way to close it for good in this browser. The pages run no script, so
// each page leaves a slot (spreadSlot) and the answer fills it here from the phone's kind and the close cookie.

/** The Play listing. [SPREAD-02] Where an install came from rides in Play's referrer; never a link's id, key or a name. */
export const PLAY = "https://play.google.com/store/apps/details?id=com.bamnat.app";

/**
 * Each page's card: what it says and the campaign Play's 사용자 획득 보고서 groups it under.
 * 1.39 [SPREAD-34] (총괄 11-07, 마케팅 2장 옛 12번) Each says what the one who got the link gains, and only what the app does
 * today: a teammate who starts the app from their own link follows the leader's changes (D9 RosterFollow), a family member
 * keeps the schedule beside their own and gets the new one when they open it (no widget for a received calendar yet, SPREAD-33).
 */
export const SPREAD_KINDS = {
  family: { campaign: "family", title: "가족 근무를 내 달력 옆에서 바로",
    line: "밤낮에 이 근무표를 받아 두면 링크를 다시 찾지 않아도 내 달력과 바꿔 가며 볼 수 있어요. 보낸 분이 고치면 열 때 새로 받아요. 무료예요." },
  team: { campaign: "team", title: "근무가 바뀌면 내 알람도 저절로",
    line: "밤낮 앱에서 이 링크로 시작하면 내 근무가 내 달력에 들어가고 출근 전에 알람이 울려요. 리더가 근무를 고치면 내 달력과 알람도 저절로 바뀌어요." },
  board: { campaign: "team", title: "근무가 바뀌면 내 알람도 저절로",
    line: "밤낮 앱에서 리더가 보낸 내 근무 링크로 시작하면 출근 전에 알람이 울리고, 리더가 근무를 고치면 내 달력과 알람도 저절로 바뀌어요." },
  request: { campaign: "team", title: "확정된 근무가 내 달력과 알람으로",
    line: "리더가 근무표를 확정해 링크를 보내면 밤낮 앱에서 그 링크로 시작해 내 달력과 근무 알람을 한 번에 맞춰요. 리더가 고친 근무도 저절로 따라와요." },
  call: { campaign: "ask", title: "바뀐 근무도 내 달력과 알람에 바로",
    line: "밤낮에 근무를 넣어 두면 대신 들어가는 날도 한 번에 고치고, 알람이 따라 바뀌어요." },
  swap: { campaign: "ask", title: "바뀐 근무도 내 달력과 알람에 바로",
    line: "밤낮에 근무를 넣어 두면 바꾼 날도 한 번에 고치고, 알람이 따라 바뀌어요." },
  clock: { campaign: "boss", title: "내 시급으로 이번 달 월급 미리 보기",
    line: "밤낮에 시급과 일한 날을 넣으면 이번 달 받을 돈과 주휴수당을 미리 볼 수 있어요. 무료예요." },
  // A schedule link that is gone, whoever it was from: speaks to anyone who keeps a schedule.
  schedule: { campaign: "family", title: "내 근무도 달력과 알람으로",
    line: "밤낮에 근무를 넣으면 한 달 달력이 저절로 차고 출근 전에 알람이 울려요. 받은 근무표도 내 달력 옆에 둘 수 있어요. 무료예요." },
  sign: { campaign: "boss", title: "계약한 시급으로 이번 달 월급 미리 보기",
    line: "밤낮에 시급과 일한 날을 넣으면 이번 달 받을 돈과 주휴수당을 미리 볼 수 있어요. 무료예요." },
};

/** [SPREAD-02] The store page for a page kind: utm_source=bamnat_share&utm_medium=<kind>&utm_campaign=<campaign>. */
export function storeLink(kind) {
  const k = SPREAD_KINDS[kind] ? kind : "family";
  const referrer = `utm_source=bamnat_share&utm_medium=${k}&utm_campaign=${SPREAD_KINDS[k].campaign}`;
  return `${PLAY}&referrer=${encodeURIComponent(referrer)}`;
}

/**
 * A messenger's own browser (카카오톡, 네이버, 인스타그램, 페이스북, 라인) may open Play's web page, where an install loses the
 * referrer: there the Play app is opened straight, the web page to fall back to (as the 친구 초대 page does, INVITE-13).
 */
function storeHref(kind, ua) {
  const store = storeLink(kind);
  if (!/KAKAOTALK|NAVER|Instagram|FBAN|FBAV|FB_IAB|Line\//i.test(ua)) return store;
  const referrer = store.slice(store.indexOf("&referrer=") + "&referrer=".length);
  return `intent://details?id=com.bamnat.app&referrer=${referrer}#Intent;scheme=market;package=com.android.vending;` +
    `S.browser_fallback_url=${encodeURIComponent(store)};end`;
}

const SPREAD_MARK = /<!--spread:([a-z]+)(:gone)?(?::(영업일|일정))?-->/;
const SPREAD_COOKIE = "bn_spread=off";
const SPREAD_HIDE = "/spread/hide";
/** The pages a card may go back to once closed: only this server's own receive pages, never another address. */
const SPREAD_BACK = /^\/(s\/[A-Za-z0-9]{16,32}|[cavwkb]\/[A-Za-z0-9]{22})$/;

/**
 * Where a page leaves room for its card (filled or emptied by [spread]). 2.0.0 [RVD-30] A family card also keeps the page's word
 * (영업일·일정, linkWord), so a shop's page does not end on '가족 근무 … 이 근무표'. The store link stays the family one.
 */
function spreadSlot(kind, gone = false, word = null) {
  const said = kind === "family" && (word === "영업일" || word === "일정") ? `:${word}` : "";
  return SPREAD_KINDS[kind] ? `<!--spread:${kind}${gone ? ":gone" : ""}${said}-->` : "";
}

/**
 * 1.39 [SPREAD-08] A link that has ended is the page most people open late: instead of a dead end, what to do next and the
 * same card (without "다시 누르면 앱에서 열려요", since this link will not open again). A link taken down for its content (410)
 * keeps only its own words.
 */
function goneNext(next, kind, word = null) {
  return (next ? `<p class="muted">${esc(next)}</p>` : "") + spreadSlot(kind, true, word);
}

/**
 * 1.39 [SPREAD-09] What a messenger's preview card (카카오톡·라인·문자) says before the link is opened: only what kind of page it is.
 * Never a name, a team or workplace, a shift or a date (총괄 2026-11-07): the preview stays in the chat room, and a chat room
 * may hold more people than the link was meant for. The <title> keeps its words for the person who opened it; scrapers read og.
 */
export const OG_TEXT = {
  family: { title: "근무표가 왔어요", line: "보낸 분의 근무를 달력으로 볼 수 있어요." },
  // 2.0.0 [RVD-30] HQ-64 A 근무표 링크 named by what came with it (linkWord), still only the kind of page.
  familySales: { title: "영업일이 왔어요", line: "보낸 분의 영업일을 달력으로 볼 수 있어요." },
  familyJobs: { title: "일정이 왔어요", line: "보낸 분의 일정을 달력으로 볼 수 있어요." },
  team: { title: "확정된 근무표가 왔어요", line: "내 근무를 달력으로 확인해 주세요." },
  board: { title: "팀 전체 근무표가 왔어요", line: "이번 달 팀 근무표를 볼 수 있어요." },
  request: { title: "근무 신청 링크가 왔어요", line: "원하는 날을 골라 신청해 주세요." },
  call: { title: "근무를 부탁하는 요청이 왔어요", line: "들어갈 수 있는지 눌러서 답해 주세요." },
  swap: { title: "근무 바꾸기 요청이 왔어요", line: "바꿀 날을 보고 답해 주세요." },
  clock: { title: "출퇴근 기록 링크가 왔어요", line: "출근·퇴근 때 눌러 주세요." },
  sign: { title: "근로계약서 서명 요청이 왔어요", line: "계약서를 읽고 이름을 써서 서명해 주세요." },
};

function ogMeta(kind) {
  const o = OG_TEXT[kind];
  if (!o) return "";
  return `<meta property="og:type" content="website"><meta property="og:site_name" content="밤낮 근무 달력">` +
    `<meta property="og:locale" content="ko_KR"><meta property="og:title" content="${esc(o.title)}">` +
    `<meta property="og:description" content="${esc(o.line)}"><meta name="twitter:card" content="summary">` +
    `<meta name="description" content="${esc(o.line)}">`;
}

/** The page's own address for a form's answer ("/c/<id>/punch" → "/c/<id>"), or null when it is not a receive page. */
export function spreadBack(path) {
  const page = path.replace(/\/(punch|pick|note|ask|cancel|reply|answer|sign)$/, "");
  return SPREAD_BACK.test(page) ? page : null;
}

/** An iPhone or iPad (iPadOS Safari says Macintosh, with Mobile). */
function appleUa(ua) {
  return /iPhone|iPad|iPod|Macintosh.*Mobile/i.test(ua);
}

/** [SPREAD-01..04] The card for [kind] as this phone should see it. */
export function spreadCard(kind, ua, back, gone = false, word = null) {
  const base = SPREAD_KINDS[kind];
  if (!base) return "";
  // [RVD-30] HQ-64 A shop's 영업일 or a freelancer's 일정 is not a 가족 근무.
  const k = kind === "family" && (word === "영업일" || word === "일정")
    ? { ...base, title: `${word === "영업일" ? "가게 영업일" : "보낸 분의 일정"}을 내 달력 옆에서 바로`, line: base.line.replace("이 근무표를", `이 ${word}을`) }
    : base;
  const android = /Android/i.test(ua);
  const apple = !android && appleUa(ua);
  // [SPREAD-04] Back to the link after the install: a schedule link opens in the app; the others stay pages.
  const after = gone ? "" : kind === "family" || kind === "team" ? "설치한 뒤 이 링크를 다시 누르면 앱에서 열려요." : "앱을 받은 뒤에도 이 링크는 그대로 열려요.";
  const get = android
    ? `<a class="get" href="${esc(storeHref(kind, ua))}">밤낮 받기 · 무료</a>${after ? `<p class="muted small">${after}</p>` : ""}`
    // [SPREAD-03] No dead end on an iPhone: say what 밤낮 is and that this page keeps working.
    : apple ? ""
    : `<p class="muted small">밤낮은 Android 휴대폰 앱이에요. <a href="${esc(storeLink(kind))}">Play 스토어에서 보기</a></p>`;
  const close = back
    ? `<form class="x" method="post" action="${SPREAD_HIDE}"><input type="hidden" name="back" value="${esc(back)}"><button aria-label="닫기, 이 브라우저에서 다시 안 보기">×</button></form>`
    : "";
  // 1.39 [IOS-03] An iPhone is not promised what only the app does (내 달력·알람): it hears what 밤낮 is and who it is for.
  if (apple) {
    // [SPREAD-08] An ended link will not open again, so the card does not say it keeps opening.
    return `<aside class="spread">${close}<p class="t">밤낮은 Android 근무 달력 앱이에요</p><p class="muted small">${gone ? "" : "이 링크는 iPhone에서도 그대로 계속 열려요. "}` +
      `Android 휴대폰을 쓰는 가족·동료라면 밤낮 앱으로 근무를 넣고 근무 알람까지 맞출 수 있어요.</p></aside>`;
  }
  return `<aside class="spread">${close}<p class="t">${esc(k.title)}</p><p class="muted small">${esc(k.line)}</p>${get}</aside>`;
}

/** Fills (or empties) a page's slot: closed in this browser → nothing; otherwise the card for this phone. */
async function spread(request, res) {
  if (!(res.headers.get("content-type") ?? "").startsWith("text/html")) return res;
  const body = await res.text();
  const m = body.match(SPREAD_MARK);
  if (!m) return new Response(body, res);
  const closed = (request.headers.get("cookie") ?? "").split(/;\s*/).includes(SPREAD_COOKIE);
  const card = closed ? "" : spreadCard(m[1], request.headers.get("user-agent") ?? "", spreadBack(new URL(request.url).pathname), !!m[2], m[3] ?? null);
  return new Response(body.replace(SPREAD_MARK, card), res);
}

/** POST /spread/hide: the card stays closed in this browser for a year, and the page comes back as it was. */
async function spreadHide(request, url) {
  const form = new URLSearchParams((await request.text()).slice(0, 128));
  const back = spreadBack(form.get("back") ?? "");
  if (!back) return new Response("Not found", { status: 404 });
  const secure = url.protocol === "https:" ? "; Secure" : ""; // the PC's local server is plain http
  return new Response(null, { status: 303, headers: {
    location: back, "cache-control": "no-store", "referrer-policy": "no-referrer",
    "set-cookie": `${SPREAD_COOKIE}; Max-Age=31536000; Path=/; HttpOnly; SameSite=Lax${secure}`,
  } });
}

// ---- output ----

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function html(body, status = 200) {
  return new Response(body, {
    status,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "x-robots-tag": "noindex, nofollow",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
      // [SRV-05] The only form is the page's own 신고, sent back to this address.
      "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
    },
  });
}

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** [SHR-01] The words for each part a sharer can tick, in the order the page lists them. */
const SHOW_NAMES = { jobs: "일정", leaveKinds: "휴가 종류", extras: "연장·휴무일 근무", memos: "메모", pay: "급여" };

/** [SHR-11] How the page explains the money, by what it is. */
const PAY_NOTES = {
  pay: "보낸 사람의 앱이 계산한 예상 금액이라 실제 명세서와 다를 수 있어요.",
  jobs: "보낸 사람이 앱에 적은 일감 금액을 달마다 더했어요. 3.3%를 뗀 금액이에요.",
  sales: "보낸 사람이 앱에 적은 매출을 달마다 더했어요. 경비는 빼지 않은 금액이에요.",
};

const won = (n) => `${String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ",")}원`;

/**
 * [SHR-02] Opens the app on Android, or its store page when it is not installed. 1.39 [SPREAD-02] The store page carries
 * where the install came from (never the link's id).
 */
function appLink(id, kind = "family") {
  const store = encodeURIComponent(storeLink(kind));
  return `intent://s/${id}#Intent;scheme=bamnat;package=com.bamnat.app;S.browser_fallback_url=${store};end`;
}

/**
 * 1.39 [SPREAD-01] A team leader's link speaks to a teammate; any other to family. The leader's app marks its links (team) from
 * 1.39; an older app's link is known by its notes (바뀜·같이 쉬어요), which only a 다시 확정 or 같이 쉬는 사람 adds.
 */
export function scheduleKind(schedule) {
  if (schedule.team === true) return "team";
  const notes = (schedule.days ?? []).flatMap((d) => (d.memo ? String(d.memo).split(" · ") : []));
  return notes.some((n) => /^(바뀜: |같이 쉬어요: )/.test(n)) ? "team" : "family";
}

/**
 * 2.0.0 [RVD-30] HQ-64 (총괄 A안, 앱 core linkWord와 같은 규칙): what the link is a schedule of, from what the sharer sent with it and
 * nothing new. 매출을 함께 보낸 가게는 영업일, 일감·일감 수입을 보낸 사람은 일정, 나머지는 근무표 (가게·투자가 날짜만 보내면 근무표).
 */
export function linkWord(schedule) {
  const shows = Array.isArray(schedule?.shows) ? schedule.shows : [];
  const pay = shows.includes("pay") ? schedule.payKind ?? "pay" : null;
  if (pay === "sales") return "영업일";
  if (shows.includes("jobs") || pay === "jobs") return "일정";
  return "근무표";
}

/**
 * [RVD-30] The word with its endings (받침: 영업일이·일정이, 근무표가). [days] is what the page's days are (the 캘린더 이름, '…가 바뀔 때'),
 * [with] heads what came along ('일정과 함께 보낸 것: 일정' would say it twice).
 */
const WORD_FORMS = {
  근무표: { is: "근무표예요", subj: "근무표가", days: "근무", daysSubj: "근무가", daysTopic: "근무는", with: "근무와 함께 보낸 것", og: "family" },
  영업일: { is: "영업일이에요", subj: "영업일이", days: "영업일", daysSubj: "영업일이", daysTopic: "영업일은", with: "영업일과 함께 보낸 것", og: "familySales" },
  일정: { is: "일정이에요", subj: "일정이", days: "일정", daysSubj: "일정이", daysTopic: "일정은", with: "함께 보낸 것", og: "familyJobs" },
};

/**
 * Month grids for every month the days touch, weeks starting on Sunday.
 * 1.39 [IOS-02..05] [opts]: `phone` (platformOf), `feed` ("host/s/<id>.ics" when the page is served), `tired` (tiredState or null),
 * `said` (the ?t= answer of the 피곤해요 form).
 */
export function renderSchedule(schedule, expiresAt, appId = null, reportId = null, opts = {}) {
  const ios = opts.phone === "ios";
  const types = new Map(schedule.types.map((t) => [t.id, t]));
  const byDate = new Map(schedule.days.map((d) => [d.date, d]));
  const months = [...new Set(schedule.days.map((d) => d.date.slice(0, 7)))];
  const today = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10); // KST
  const grids = months.map((ym) => {
    const [y, m] = ym.split("-").map(Number);
    const first = new Date(Date.UTC(y, m - 1, 1));
    const days = new Date(Date.UTC(y, m, 0)).getUTCDate();
    const cells = [];
    for (let i = 0; i < first.getUTCDay(); i++) cells.push(`<div class="cell blank"></div>`);
    for (let d = 1; d <= days; d++) {
      const date = `${ym}-${String(d).padStart(2, "0")}`;
      const entry = byDate.get(date);
      const t = entry && types.get(entry.type);
      const s = entry?.second != null ? types.get(entry.second) : null;
      const dow = (first.getUTCDay() + d - 1) % 7;
      cells.push(
        `<div class="cell${date === today ? " today" : ""}"><span class="num${dow === 0 ? " sun" : dow === 6 ? " sat" : ""}">${d}<span class="dow"> ${WEEKDAYS[dow]}</span></span>` +
          (t ? `<span class="chip" style="background:${t.color}">${esc(t.short)}</span>` : "") +
          (s ? `<span class="chip" style="background:${s.color}">${esc(s.short)}</span>` : "") +
          (entry?.leave ? `<span class="leave">${esc(entry.leave)}</span>`
            : entry?.leaveMinutes ? `<span class="leave">휴가 ${esc(leaveLength(entry.leaveMinutes))}</span>` : "") +
          (entry?.jobs ?? []).map((j) => `<span class="extra job">${esc(j.name)}${j.time ? ` ${esc(j.time.slice(0, 5))}` : ""}</span>`).join("") +
          (entry?.extras ?? []).map((e) => `<span class="extra">${esc(e.name)}${e.minutes ? ` ${esc(hours(e.minutes))}` : ""}</span>`).join("") +
          (entry?.memo ? `<span class="memo">${esc(entry.memo)}</span>` : "") +
          `</div>`,
      );
    }
    return `<section class="cal"><h2>${y}년 ${m}월</h2><div class="grid">${WEEKDAYS.map((w, i) => `<div class="wd${i === 0 ? " sun" : i === 6 ? " sat" : ""}">${w}</div>`).join("")}${cells.join("")}</div></section>`;
  });
  const used = new Set(schedule.days.flatMap((d) => [d.type, d.second]).filter((x) => x != null));
  const legend = schedule.types.filter((t) => used.has(t.id)).map((t) =>
    // 2.0.0 [RVD-20] HQ-13 The name never breaks; a shift in two parts says both, each part whole ("11:00~15:00 · 17:00~22:00").
    `<li><span class="chip" style="background:${t.color}">${esc(t.short)}</span><span class="nm">${esc(t.name)}</span>${t.time && !t.off
      ? `<span class="muted">${parts(t).map((p) => `<span class="nm">${esc(p.replace("-", "~"))}</span>`).join(" · ")}</span>` : ""}</li>`,
  ).join("");
  const until = new Date(Date.parse(expiresAt) + 9 * 3600 * 1000).toISOString().slice(0, 10);
  // [RVD-30] HQ-64 The app's words: "민지의 영업일", and without a name "받은 영업일" (what the sharer's help says the other phone shows).
  const word = linkWord(schedule);
  const title = schedule.title ? `${schedule.title}의 ${word}` : `받은 ${word}`;
  const payKind = Object.hasOwn(PAY_KINDS, schedule.payKind ?? "") ? schedule.payKind : "pay";
  // 1.38 PC 11차 (18): a kind ticked but with nothing in it (no day has a memo) is not listed as sent.
  const present = { jobs: (d) => d.jobs?.length, leaveKinds: (d) => d.leave, extras: (d) => d.extras?.length, memos: (d) => d.memo };
  // 1.38 PC 12.4: a team leader's link carries the app's own notes ("바뀜: D→E", "같이 쉬어요: …") in the memo place; they are
  // named for what they are, and '메모' only for words the sharer wrote.
  const notes = schedule.days.flatMap((d) => (d.memo ? String(d.memo).split(" · ") : []));
  const memoNames = [
    ...(notes.some((n) => !/^(바뀜: |같이 쉬어요: )/.test(n)) ? ["메모"] : []),
    ...(notes.some((n) => n.startsWith("바뀜: ")) ? ["바뀐 날 표시"] : []),
    ...(notes.some((n) => n.startsWith("같이 쉬어요: ")) ? ["같이 쉬는 사람"] : []),
  ];
  const shows = Object.keys(SHOW_NAMES).filter((k) => (schedule.shows ?? []).includes(k))
    .filter((k) => !present[k] || schedule.days.some((d) => present[k](d)))
    .flatMap((k) => (k === "pay" ? [PAY_KINDS[payKind]] : k === "memos" ? memoNames : [SHOW_NAMES[k]]));
  const has = shows.length ? `<p class="muted">${WORD_FORMS[word].with}: ${esc(shows.join(", "))}</p>` : "";
  // [SHR-20] The page never shows the setup itself: only that the app takes it.
  // 1.39 [IOS-03] An iPhone has no 밤낮 app: it is told the extras are for the Android app and the days are all here.
  const extras = [schedule.setup && "회사 근무 설정", schedule.look && "테마", schedule.kit && "근무 설정"].filter(Boolean);
  const setupLine = ios
    ? (extras.length ? `<p class="muted">${esc(extras.join("·"))}도 함께 왔어요. 이건 Android 밤낮 앱에서만 받을 수 있고, ${WORD_FORMS[word].daysTopic} 이 페이지에서 그대로 볼 수 있어요.</p>` : "")
    : (schedule.setup ? `<p class="muted">회사 근무 설정도 함께 보냈어요. 밤낮 앱에서 열면 기본급만 넣고 바로 쓸 수 있어요.</p>` : "") +
      // 1.37 [SHR-31][SHR-32] Like the setup, the page names what came and never shows it.
      (schedule.look || schedule.kit
        ? `<p class="muted">${schedule.look && schedule.kit ? "테마와 근무 설정" : schedule.look ? "테마" : "근무 설정"}도 함께 보냈어요. 밤낮 앱에서 열면 입어 보고 골라 넣을 수 있어요.</p>`
        : "");
  const pay = schedule.pay?.length
    ? `<section class="pay"><h2>함께 보낸 ${PAY_KINDS[payKind]}</h2><ul>${schedule.pay.map((p) => {
        const [y, m] = p.month.split("-").map(Number);
        return `<li><span>${y}년 ${m}월${p.label ? ` <span class="muted">${esc(p.label)}</span>` : ""}</span><b>${won(p.amount)}</b></li>`;
      }).join("")}</ul><p class="muted">${PAY_NOTES[payKind]}</p></section>`
    : "";
  const kind = scheduleKind(schedule);
  const app = appId
    ? `<p class="app"><a href="${esc(appLink(appId, kind))}">밤낮 앱에서 보기</a><span class="muted">앱에서는 내 달력과 바꿔 가며 볼 수 있어요</span></p>`
    : "";
  return page(title, `<h1>${esc(title)}</h1>${has}${setupLine}${app}${feedButton(schedule, opts.feed, opts.phone, opts.scheme)}${tiredPanel(reportId, opts.tired, opts.said)}` +
    `${monthList(widestWord(schedule.types.filter((t) => used.has(t.id)).map((t) => t.short)))}${grids.join("")}<ul class="legend">${legend}</ul>${pay}` +
    `<p class="muted foot">밤낮 앱에서 공유한 읽기 전용 ${WORD_FORMS[word].is}. <span class="nm">${until}까지</span> 열려요.</p>${ios ? homeHint() : ""}` + spreadSlot(kind, false, word) + (reportId ? reportForm(reportId) : ""),
  word, kind === "family" ? WORD_FORMS[word].og : kind);
}

/**
 * 1.39 [IOS-02] The sharer (or the leader, IOS-01) turned the calendar address on: one button puts the days in the phone's own
 * calendar. iPhone: webcal:// opens 캘린더's 구독. Anything else: Google Calendar's add-by-address page (Android, a computer).
 * It follows changes within hours; it is not an alarm, and the page says so.
 */
export function feedButton(schedule, feed, phone, scheme = "https") {
  if (schedule.ics !== true || !feed) return "";
  const webcal = `webcal://${feed}`;
  const href = phone === "ios" ? webcal : `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(webcal)}`;
  const label = phone === "ios" ? "아이폰 캘린더에 넣기" : "구글 캘린더에 넣기";
  const word = linkWord(schedule);
  return `<div class="panel feed"><p class="feed-t"><a class="get" href="${esc(href)}">${label}</a></p>` +
    // [RVD-30] HQ-64 '영업일이 바뀔 때'·'일정이 바뀔 때' for a shop's or a freelancer's link.
    `<p class="muted small">한 번 넣어 두면 ${WORD_FORMS[word].daysSubj} 바뀔 때 캘린더도 몇 시간 안에 따라 바뀌어요. ${word === "근무표" ? "출근 " : ""}알람은 아니에요.` +
    (phone === "ios" ? ` 카카오톡 안에서 안 열리면 오른쪽 아래 메뉴에서 'Safari로 열기' 뒤 다시 눌러 주세요.` : "") +
    `</p><p class="muted small">다른 캘린더 앱: <span class="addr">${esc(scheme)}://${esc(feed)}</span></p></div>`;
}

/** 1.39 [IOS-04] '오늘 피곤해요' on the page, while the leader's 피곤해요 합계 is on for this link: no name goes, once a day. */
function tiredPanel(id, tired, said) {
  if (!id || !tired) return "";
  const note = said === "1" ? `<p class="done">팀에 ‘피곤해요’ 하나를 더했어요. 이름은 가지 않아요.</p>`
    : said === "wait" ? `<p class="bad">잠시 뒤에 다시 눌러 주세요.</p>` : "";
  const button = tired.today || said === "1"
    ? `<p class="muted small">오늘은 이미 알렸어요. 내일 다시 누를 수 있어요.</p>`
    : `<form method="post" action="/s/${esc(id)}/tired"><button class="pick">오늘 피곤해요</button></form>`;
  return `<div class="panel"><p class="t"><b>팀에 피곤해요 알리기</b></p>${note}` +
    `<p class="muted small">리더에게는 하루에 몇 명이 눌렀는지만 가요. 누가 눌렀는지는 리더도 몰라요.</p>${button}</div>`;
}

/** 1.39 [IOS-05] iPhone only: a page added to the home screen opens like an app (the icon is /apple-touch-icon.png). */
function homeHint() {
  return `<p class="muted small">아이폰: 아래 공유 단추(□↑) › ‘홈 화면에 추가’를 누르면 다음부터 아이콘 하나로 바로 열려요.</p>`;
}

/** [SRV-05] A plain form, so reporting works without scripts (the page allows none). */
function reportForm(id) {
  const options = Object.entries(REPORT_REASONS).map(([k, v], i) =>
    `<label><input type="radio" name="reason" value="${k}"${i === 0 ? " required" : ""}> ${esc(v)}</label>`).join("");
  return `<details class="report"><summary>이 링크 신고하기</summary><form method="post" action="/s/${esc(id)}/report">` +
    `<p class="muted">운영자가 확인해 운영 규칙에 어긋나면 링크를 내려요. 누가 신고했는지는 남지 않아요.</p>${options}` +
    `<button type="submit">신고 보내기</button></form></details>`;
}

/**
 * [FEAT-15] RFC 5545: each work day an event at its shift's times (or all day without them), a second
 * shift and timed extra work as events of their own, leave as an all-day event; days off are left out.
 * UIDs stay the same for a date, so a changed day replaces its event in the subscriber's calendar.
 */
export function renderIcs(schedule, id, updatedAt) {
  const types = new Map(schedule.types.map((t) => [t.id, t]));
  const tz = schedule.tz || "Asia/Seoul";
  const stamp = icsStamp(updatedAt ? new Date(updatedAt) : new Date());
  // [RVD-30] HQ-64 The subscribed calendar's name says the same word as the page ("민지의 영업일").
  const days = WORD_FORMS[linkWord(schedule)].days;
  const name = schedule.title ? `${schedule.title}의 ${days}` : `밤낮 ${days}`;
  const lines = [
    "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Bamnat//Share//KO", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
    `X-WR-CALNAME:${icsText(name)}`, `X-WR-TIMEZONE:${tz}`, "REFRESH-INTERVAL;VALUE=DURATION:PT6H", "X-PUBLISHED-TTL:PT6H",
  ];
  const event = (uid, summary, date, time, description) => {
    lines.push("BEGIN:VEVENT", `UID:${uid}@${id}.bamnat`, `DTSTAMP:${stamp}`);
    const d = date.replaceAll("-", "");
    if (time) {
      const [from, to] = time.split("-");
      const end = to <= from ? nextDay(date).replaceAll("-", "") : d;
      lines.push(`DTSTART;TZID=${tz}:${d}T${from.replace(":", "")}00`, `DTEND;TZID=${tz}:${end}T${to.replace(":", "")}00`);
    } else {
      lines.push(`DTSTART;VALUE=DATE:${d}`, `DTEND;VALUE=DATE:${nextDay(date).replaceAll("-", "")}`);
    }
    lines.push(`SUMMARY:${icsText(summary)}`);
    if (description) lines.push(`DESCRIPTION:${icsText(description)}`);
    lines.push(time ? "TRANSP:OPAQUE" : "TRANSP:TRANSPARENT", "END:VEVENT");
  };
  for (const day of schedule.days) {
    const memo = day.memo || null;
    for (const [slot, typeId] of [["s", day.type], ["t", day.second]]) {
      const t = typeId == null ? null : types.get(typeId);
      // 2.0.0 [RVD-20] HQ-13 A shift in two parts is two events; the first keeps the date's UID, so a calendar app updates it in place.
      // A second part that starts before the shift's start on the clock starts after midnight (20:00~00:00 · 01:00~05:00).
      if (t && !t.off) parts(t).forEach((time, i) => event(`${day.date}-${slot}${i ? "2" : ""}`, t.name,
        i && minuteOf(time) < minuteOf(t.time) ? nextDay(day.date) : day.date, time, slot === "s" && !i ? memo : null));
    }
    if (day.leave || day.leaveMinutes) event(`${day.date}-l`, day.leave || `휴가 ${leaveLength(day.leaveMinutes)}`, day.date, null, null);
    (day.extras ?? []).forEach((e, i) => { if (e.time) event(`${day.date}-x${i}`, e.name, day.date, e.time, null); });
    (day.jobs ?? []).forEach((j, i) => event(`${day.date}-j${i}`, j.name, day.date, j.time, null));
  }
  lines.push("END:VCALENDAR");
  return lines.map(icsFold).join("\r\n") + "\r\n";
}

function nextDay(date) {
  return new Date(Date.parse(`${date}T00:00:00Z`) + 86400 * 1000).toISOString().slice(0, 10);
}

const icsStamp = (d) => d.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
const icsText = (s) => String(s).replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\r?\n/g, "\\n");

/** Lines longer than 75 octets go on after a space, never splitting a Korean character. */
function icsFold(line) {
  const enc = new TextEncoder();
  if (enc.encode(line).length <= 75) return line;
  let out = "";
  let count = 0;
  let limit = 75;
  for (const ch of line) {
    const n = enc.encode(ch).length;
    if (count + n > limit) { out += "\r\n "; count = 0; limit = 74; }
    out += ch;
    count += n;
  }
  return out;
}

/** 90 → "1시간 30분", 120 → "2시간". */
function hours(minutes) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h && m ? `${h}시간 ${m}분` : h ? `${h}시간` : `${m}분`;
}

/**
 * 2.0.0 [RVD-20] HQ-13 The month as a list of days once its cells are narrower than what must sit in them on one line: the
 * widest word of a chip ([widest] letters, '오프', '주간'), at the page's own letter size. One letter switches below 20em (large
 * text on a folded phone), two below 25em (a folded phone at 100%), and so on, so a chip never stands letter by letter.
 */
function monthList(widest) {
  const em = Math.max(20, Math.ceil(6.1 * Math.min(widest, 8) + 12.5));
  return `<style>@container (max-width:${em}em){.cal .grid{grid-template-columns:1fr;gap:6px}.cal .wd,.cal .blank{display:none}` +
    ".cal .cell{flex-direction:row;flex-wrap:wrap;align-items:center;min-height:44px;gap:4px 10px;padding:8px 12px}.cal .num{min-width:3.6em;font-size:1em}" +
    ".cal .dow{display:inline}.cal .leave,.cal .extra,.cal .memo{text-align:left}}</style>";
}
const widestWord = (texts) => Math.max(1, ...texts.flatMap((t) => String(t).split(/\s+/)).map((w) => [...w].length));

/** 2.0.0 [RVD-20] HQ-13 An hour's leave as the app's link words say it ("2시간", "1.5시간"; "30분", "1시간 15분" off the half hours). */
const leaveLength = (m) => (m > 60 && m % 30 === 0 ? `${m / 60}시간` : hours(m));

function page(title, body, short = "밤낮", og = null) {
  // 1.39 [IOS-05] The name and icon an iPhone uses for a page added to its home screen. [SPREAD-09] og: the messenger preview.
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex,nofollow"><title>${esc(title)}</title>${ogMeta(og)}<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<meta name="apple-mobile-web-app-title" content="${esc(short)}"><style>
:root{--fg:#15192B;--muted:#5b5f73;--line:#e6e2d9;--bg:#F7F4EE;--card:#fff;--sun:#d14343;--sat:#3b5bdb;--today:#C2A36B}
@media (prefers-color-scheme:dark){:root{--fg:#F2ECE1;--muted:#a7a9b8;--line:#2a2f45;--bg:#15192B;--card:#1d2236;--sun:#ff8787;--sat:#91a7ff}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 -apple-system,"Noto Sans KR","Apple SD Gothic Neo",sans-serif}
main{max-width:720px;margin:0 auto;padding:24px 16px 48px}h1{font-size:22px;margin:0 0 16px}h2{font-size:17px;margin:24px 0 8px}
.grid{display:grid;grid-template-columns:repeat(7,1fr);gap:4px}.wd{text-align:center;font-size:12px;color:var(--muted);padding:4px 0}
.cell{background:var(--card);border:1px solid var(--line);border-radius:10px;min-height:64px;padding:4px;display:flex;flex-direction:column;align-items:center;gap:3px}
.cell.blank{background:transparent;border:none}.cell.today{border:2px solid var(--today)}
.num{font-size:12px;color:var(--muted)}.sun{color:var(--sun)}.sat{color:var(--sat)}
.chip{display:inline-block;min-width:24px;padding:1px 6px;border-radius:8px;color:#fff;font-weight:700;font-size:13px;text-align:center;text-shadow:0 0 2px rgba(0,0,0,.35)}
.leave{font-size:11px;color:var(--muted);text-align:center;word-break:keep-all;overflow-wrap:anywhere}.extra{font-size:10px;line-height:1.2;color:var(--fg);background:var(--line);border-radius:6px;padding:0 4px;text-align:center}
.job{background:#C2A36B33}.memo{font-size:10px;line-height:1.25;color:var(--muted);text-align:center;word-break:break-all;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
.app{display:flex;flex-wrap:wrap;align-items:center;gap:6px 12px;margin:0 0 8px}.app a{display:inline-block;padding:10px 16px;border-radius:999px;background:var(--today);color:#15192B;font-weight:700;text-decoration:none}
.pay ul{list-style:none;padding:0;margin:0;background:var(--card);border:1px solid var(--line);border-radius:12px}.pay li{display:flex;justify-content:space-between;gap:12px;padding:12px 14px;border-top:1px solid var(--line)}.pay li b{white-space:nowrap}.pay li:first-child{border-top:none}
.dow{display:none}.cal{container-type:inline-size}.cal .grid{grid-template-columns:repeat(7,minmax(0,1fr))}.cal .chip{word-break:keep-all}
.legend{list-style:none;padding:0;margin:20px 0 0;display:flex;flex-wrap:wrap;gap:8px 16px}
.legend li{display:flex;flex-wrap:wrap;align-items:center;gap:0 6px}.nm{white-space:nowrap}.report{margin-top:16px;font-size:13px}.report summary{color:var(--muted);cursor:pointer}.report label{display:block;padding:4px 0}.report button{margin-top:8px;padding:8px 14px;border-radius:999px;border:1px solid var(--line);background:var(--card);color:var(--fg)}.muted{color:var(--muted)}.foot{margin-top:24px;font-size:13px;word-break:keep-all}.empty{margin-top:40px}
.buttons{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:16px 0}.buttons form{margin:0}.buttons button{width:100%;min-height:96px;border-radius:20px;border:none;font-size:24px;font-weight:800;color:#fff;cursor:pointer}
.buttons .in{background:#2f7d5b}.buttons .out{background:#3b5bdb}.buttons .other{opacity:.45;min-height:64px;font-size:18px}.buttons .picked{outline:4px solid var(--today);outline-offset:2px}.done{background:var(--card);border:2px solid var(--today);border-radius:12px;padding:12px 14px;font-weight:700}
.cell.picked{outline:2px solid var(--sat)}a.cell{color:inherit;text-decoration:none}.full{color:var(--sun);font-weight:700}
.panel{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 14px;margin-top:16px}.picks{display:flex;flex-wrap:wrap;gap:8px}.picks form{margin:0}
.pick{min-height:44px;padding:8px 14px;border-radius:999px;border:1px solid var(--line);background:var(--bg);color:var(--fg);font-size:15px;cursor:pointer}.pick.on{background:var(--sat);color:#fff;border-color:var(--sat)}
.wide{overflow-x:auto;margin:0 -16px;padding:0 16px}.ward{border-collapse:collapse;font-size:12px}.ward th,.ward td{border:1px solid var(--line);padding:2px;text-align:center;min-width:26px}.ward .who{position:sticky;left:0;background:var(--bg);text-align:left;white-space:nowrap;padding:2px 6px;z-index:1}.ward .chip{min-width:20px;padding:0 3px;font-size:11px}
.sign label{display:block;margin:12px 0}.sign input[name=name]{width:100%;min-height:44px;padding:8px 12px;border-radius:10px;border:1px solid var(--line);background:var(--card);color:var(--fg);font-size:17px}.sign input[type=checkbox]{width:22px;height:22px;vertical-align:middle;margin-right:6px}.sign button{width:100%;min-height:56px;border-radius:16px;border:none;background:#2f7d5b;color:#fff;font-size:18px;font-weight:800}
.bad{background:var(--card);border:2px solid var(--sun);border-radius:12px;padding:12px 14px;font-weight:700}.rule{font-size:14px}.small{font-size:12px}
.note{display:flex;flex-wrap:wrap;gap:8px}.note input{flex:1 1 10em;min-width:0;min-height:44px;padding:8px 12px;border-radius:10px;border:1px solid var(--line);background:var(--card);color:var(--fg);font-size:15px}.note button{min-height:44px;padding:8px 14px;border-radius:999px;border:none;background:var(--today);color:#15192B;font-weight:700}
.punches{list-style:none;padding:0;margin:0;background:var(--card);border:1px solid var(--line);border-radius:12px}.punches li{display:flex;justify-content:space-between;padding:12px 14px;border-top:1px solid var(--line)}.punches li:first-child{border-top:none}
.spread{position:relative;margin-top:28px;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px 48px 14px 14px}.spread p{margin:0 0 4px}.spread .t{font-weight:700}
.spread .x{position:absolute;top:4px;right:4px;margin:0}.spread .x button{width:44px;height:44px;border:none;background:none;color:var(--muted);font-size:22px;cursor:pointer}
.spread .get{display:inline-block;margin:8px 0 4px;padding:10px 16px;border-radius:999px;background:var(--today);color:#15192B;font-weight:700;text-decoration:none}.spread a{color:var(--fg)}
.feed p{margin:4px 0}.feed .get{display:inline-block;padding:10px 16px;border-radius:999px;background:var(--today);color:#15192B;font-weight:700;text-decoration:none}.addr{user-select:all;word-break:break-all}
</style></head><body><main>${body}</main></body></html>`;
}

/** 1.39 [IOS-05] apple-touch-icon.png (180×180), drawn from app/src/main/res/drawable/ic_launcher_foreground.xml on its ink background. */
const TOUCH_ICON = "iVBORw0KGgoAAAANSUhEUgAAALQAAAC0CAIAAACyr5FlAAAQAElEQVR4nOydB1xT5/rHXyCMhBUIK5Awwt57bxERtIrV2uGoXXbYdav3dlr93zra2/b2Uz/eWzvssu2/vW2vtdUuBw5EBEE2sjdhbxJQAvchEeunciBgTs454f2aTz7H5BySc/I7z/u8z/O+z8uy5HsjDGY6tBEGQwAWB4YQLA4MIVgcGEKwODCEYHFgCMHiwBCCxYEhBIsDQwgWB4YQLA4MIVgcGEKwODCEYHFgCMHiwBCCxYEhBIsDQwgWB4YQFsJM8fJfH4Hn3W9+iDBysDiu4+UhenDjKtg4/NPJsiu1CIPFcYPunn7FRld3H8LIwT4HhhAsDgwhWBwYQjRWHCIngYmxESIZExMj+CCkoWisODzdRZFh/ohkIkP9PdxESEPRWHE0t7QLBTaIZBwd+M0tbUhD0VhxNDW3cTgGPHMuIg1LHldfXx8+CGkoGiuOnt5+iWSEVOMhFNrCR/T2DSANRZN7K41NYnulxQFBsOLSqtKy6p5eZX9soZ1NfWML0lw0OULa1CKOjw3T0tKamJiYdeexsbH0e55BSqOtrW1na3UyIxtpLoyxHND18Pd1m9Mh9Q2t4rYOHR1SzhH+bIu4vb6pdU5HBfh5hIf6IYbAGHH09Q9ERwQZGXGUP0Q6MnrkaMbYmAyRwLVrY/DHR0evKn8IfPmo8ID+/kHEEBgjjvKKuv6BodioYMRY4qJDwE2+UlmHGAKTHNLTZ3Ncne35fAvEQMB7dREJM87lIubAJHG0iDtq65uT4iMQA0mMD6uubWpv70LMgWFd2XNZeVxTY1/vuXmmlAOutLGR4fkLeYhR6BgaWyHmAA7g+Pg4+JgdnT2IOVhb8VrFndB7QoxCa8GWmjQw0HNzdjQzMzHkGHA4bI4h25DDhteHJVLJsFQikQ4PSyEgVlXbMDIyhy6JJrGAhgn6ern6+7q7uTmIHAXwgLt5hp0haqY1td3W3gW+DjwqKxsKSypKyqrRwkDDLUdosM+SRZFBAZ4QfZrm7ZslMC1TO/xpx4KiK/kF5b+fupCbV4I0F4rFwWHrW1tb1NWrOENhZWm+Jj159cpkRwdbReRcC5FCfX3Ld0eOf/fD8c6uXqRSnBwFbW1d0pERRB0Ui0PkKFyeGpeZlZ9fWI5UAZiKzZtWL0oIJ1UTfzBlUo6funDg4LdgUZAqCArwiokM/OmX0yq/beYExb0VyHdDHDomKgiexbcXA1iSFPXGa88+9dh9jo6Cyd9rYgJSbohstNBkVk9Ly9lJePfqlOiIQIiB1jXc1i8aFuILUXa4YcorKJ4+Q31XFjQBSZC4mBD4LZtb29HcAZfig307HtiQzrexRFPWQh3KmPqgG59ky7dckZaQFB8Ov2t7RzeaO2AwwPidPpdbUFyBqIYWcQ64jqCP6MhAXV1W41wGVvHMubtefXL7849aWpqrSQuzMTEZ1TBbe2eKHd8qr6BsZGRU+WPhDgHH+dTZnJLSKkQD6BIEA30MSaRglqtrGqXKXdBHNq3+1zsv+/m4aanNSiiB/JtMfiMvT+d71iy9enVMSUeEZ2YKLePJMxfLymsQPaBXV9bCnNvVM/tsRGg+3t/3qrenM6I1153VwuKKx5/dpUwro+Tpqw16hc8l0tl7blERAf//8RsCO+sJ9bic80fhFk/YWFusTk+G0NmsQ5GVOX11wqTcCkhh69Mb9+x4Wl9Pb0KNLuftIB+kiNgG+unLE+H7XrxUjJgDY8Shp8s68O72tXcundQE/GOAMK6jNeWGRIT6ebg5/nYya3x89jGtdIAZ4oCU2Ocf7gF3FTEcF5F9SJD3L8czSRq8qFoYMJ7D3Nz020NvBwd6KTOInObAKUSG+X/z2VtcU2NEe+guDi7X+D+fvunu5kh791MpFKcA/axvD70Fp4boDa3FYcg2OPThXif5NHYNUMbNiJyEnx3YDSeIaAzpPkdSQjjEMcVtc86b6LJYnx/c4+/jphk240/ASVlb8SAeeuRYxlybS21tbWhkPdxEt5nEmRXSxWHBM5v00l0du3v6BwaHlTwK1HBg33bwQDVSGeh6F3dCKLCBjN2vx88rf6DA1nrlHYvgqJq6plZxJyIT0sXRKu6oqKqztORFRwRwTU3EbZ3XxsZmPWrbM5sgPaGpylCg0Iebi4OWlnZ2btGs+3PYBmCGIYPd3NJxVC3ZfHV0ZUdHr1VVNzS3tHt5ukA+urd3YOaZ6ZCZ3PXqU1patMqZkILiBMOCfXIulcyckXYRCVetTIb9f/n9XH5BOVxSpIavp87cCtwoXh4iaGFnmPUFLfGvh98zMSG9YhOtgLslZeVj3cSJFU93J7hu5RW16rxh6JV409HR/v7Ld3y9XdHCI+9y2d33/5VWsRx6RUgfe+iuVXckabarMS1wyrZ8q+FhqaqGS6oEGlkOG2veyaMfGRjoo4XKyMho/NIHu7pVPFZ53tAoCPZ/L29ZyMpAk/Os9He+/ASiDXQRR2xU0OJERs6QVi2pydERtKnuQhdx7HzpcYSRs+PFxxA9oIU40pbEODrYaUDS9faZDIu5OiYvikQ0gBbieOrxdUjjUmvzQ3ERtmy+B9EA6sWRGBcGIWRsNm4AlwIiPXHR1Be4ol4cz25Zj7DZuAnFpXjy0XsR1VAsDi9PkY+XC8LcAiTlwaAiSlFlhNRewI+JCjYyZMtkMgj2KWMMNq1PDwlaoNVjZqWvfzA7Z/ZsLWBpYebu6hgc6D06Mto/MIRUhCqLt0hHRsaujQX5e8ZGBY+MXm1pbYdMbE1t09CwhOiQFcsSEIaA5Uvj3tl/iOhdIyOOs5NQKODb2Vrq6+kND0sglS+dy+zLWSElfG7GNbHjWwnsrOHRIu6ELPO0u/n7uv/3q3cQhpgVdz9VWjb97MhlS+P51rym5vZmcXtLS3sfCbVvSSn7BAloeJSUV4PjbWpCOIwW+ikIMyOJsWFE4jiXldffP0iqI09uTTD46gODhE1gWDD2NmYhNNiH6K2BgSGyu3iU9VZ0dLSnr9OFuYnQIG+SCvsrA2UfHBjgqa+vh2NfMwAXBy4RhRV5KRNHWNCkwcSxrxm4McIUUQRldUi9PHHsSym8PChbfZIycTgI+QijBA4OtogiKBOHs0iIMErgKKRMHNT4HBY8M309XYRRAhMTI1NTaiZqUCMOB3u+vGQWRhkmHCgyHtQ0KxaTawHjfoqSaIGhRVRAjTgM2At6lPlcYVN0uagRB8eA1nUp6AbbYCGJw4DeRUvoBodxlmPb0/dvfnCNjo4O0Q7FpVVr1m8dm67gAttAD2GUxoDA0LJYrGPf73cR2RMdKJPJ9r//9b73vkTzgkTLoU0cGh8fH4fn89kFGx95CWGI+eLg3sgw/9tJMujpzv8nnv+Rb+37DB5oXihK9RpycOMyCxz5snMSiXTad8Eqp6wkcQYUNT6HRDo5mo2NPY/ZUNw/wxJqyl5TIw6p3HKwcYd2NjjsScshlUoRFVBkOSSKZoWNMDNiaDhpOSQUWQ5qwufd3ZP1jXjmXG1thi2JrU7Al1SMwO2kqGIHNb9NQ/P1tZntceKeGAf76ymV6ppGRAXUiKO/f2hAPvdGnoHDTI9CHF3dvVevzV6ckwwos+p1jZPGw9HBDmEIcJSLo7FJjCiCMnE0ysXhQN1IFvqjGAPWsADFUXZlctFUH7qv00Yl3vJhtqXllK0uS5k4cuSrwPv7uuvhIWHTAUEgP3k91tw8ylb+okwcxaWVo6NXWSyd8BBfhLmF8FA/SKkMS6Sl1K0kSpk4ZLLx3PxS2MDimBZFTcHcvBIK532RKw7FnC2id3PlLUsSrjA5HSlJUWjqEk2LgQHp8wVJCZ9zTY0FttaKEgzgbB8/dWHa3U6dufiXJze4uTi4uzpWVNUjzBTeXs6K8OCJjGyifeKig+0FfMaUYLC0MAvy9xLYWRkachTFW6DhqKxqINofOiyV1Q0gjjvSEire/RRhpliRmgDPZeU11bVNRPtkZuW7ujgKBfyYyMAbxVvyC8s6u1QWa1elOMy5pixdVn5heXNLe0dnjzJDVH48lrHtmU0rlye+hcVxE6vuSILnI8cyZthHIh0tLK6AB5LflmCk7WytzbgmKhQHxYXx+dYWmSc+h421G7flXS5DmEkP3e+rT14HfyIs4b6enn5EHRQnRcXtXRlncmBjwz3LEUbOxnWTlwIcNWqVgehQh/TAx/9BkxWu4m4kIdWPLksrPV64bYM3PGBDR5uyCVcuImFKUjRs/OuDrxHVUC+OS/llRSWV2traT8uLXKsfEMKOh/03LHOO9LWEB2zseMSPKnnARQBfLTu3sKSsGlENLcba7HvvK3hekRZPifGI9LXwFHFvfsXb2SzCxwKpHQchPy0lFjbeP/gdogG0EEfG2ZzaumYwHi9texipHYGNoZIvks3Ol58As1FZVX/2fB6iAXQZpbfrH+/D8+LEiKSEcKRehqWyW1/sG7iK1EtqcrSiGP7OPe8hekAXcZzJzPv590wkv3tmiLiTQUn1NIGBsro+pEY4bIMd8uWIfjx2+uIlytKwf4JGq0NCHuHeu1J55lxdXdb5C5eRuugfujY+PuFmb8KSF3UcvSr75ve6i6XdSI08/9wD0ZFBkIPd9OgrEFxG9IBe68rev37lq88/Oj4+vnbDtstFV5AaYekgWwsObLR2ScZkSJ1AXvqLg3vB5dqx699ffHMU0QZ6iQPcsUMf7YkM8+/q7k27c8sMKzRrDGApfztyAMLe2blF6x58AdEJtfocEBL2dHdyd3GcYYcnn9sDyrDgmR3Yt13jq5Tq6GjDaYIy4JS3/GX3DHu6uzp5uDmpeWyH+nwOO77V8qXx7u6i+oaWGUwCtLj5BeVr0pNt+VbgmZ7PLkCay/PPPbh8adzYmAxcjfrG1hn2hNRaQlyYi7Owt6d/cEiC1II6xGFqYrg4MTI6MhCytT/9nNHW3oVmRNzWNSaTRYUHhAR6NzS2aupQj5XLEhVxnX+888mx387NvHNXdx9k8G2seLHRwTyeWVtH19Wr1xDJkC6O0GCftJQ4HW3tn387d7mo/NqYUvNzLuWXujrbu7o4QOQjN78UVIU0Cwhp7H/7RXBCfzh6au/bB5U5BC5ddW1Tq7jT28s5PNh3fGICthGZkC4OXy/XqpqGX0+cn2FtjWn5/WQWWA57IT81OeZMZq4KhylQjp+P26fv79LV1T2TeemprXvn5EnAZSwurQJlmJuZ1NQ1IzKhV2/lT0Bo6MtPXvfzduvtG3zg0VeKy6oQ84HT+fSD10xNjAuKK9Y/9IJUqsqFt1QLjYJgtwKGFOxHckIEn295x7LEy4VXmluZ3b7ERAYefO81YyPD2rqmdQ+9MDRETeENJaG1OICRkdEffz4dExlkZ2u1Ii2hsrqhpq4JMZPUJTEH3t0OXbCyKzX3bPqbCpdxJAm6iwPJO7fgtQX6e0JCf1lKbHtHN4XzfObNfWvT3tq9FTxQSJ2sf+jF4WFa2wwFAlDTFAAABVxJREFUDBAHmqyMJgP74ebi4OJsn5QQYW3JO5+dL5ONIybAZuvv3vG0YoXp46cuPPLkTjX0QlUCM8SBJqtTTkAwANIukInw9XFNTY7Nzins6aV4lOWsQFjzq0/eiAr3l8lk/9x/aOfuf8OJIIbAGHEoyMkruZhbBMEPvo3l2jtTdHR08gvLFVVN6Yaenu6zW9a/uWuruZlpd0//w1t2HDmagRgFvcQBbvysJrdF3AFXOSTIG1zUiFA/CD+XXaklOxw0VyD098VHe6EFhOxJXkHZfQ+8UKVE6SZlTl+d0EgcXp7Oq1curq5pnHXNbfDmvj18fGBwODjAy8rS/K5VS0AouXklozQYCcHlGoOH8crfNpuYGA0ODb/+z4+3/30/UZXZm+GZmW5ct2JwWEKfcB9dxAFBw0Xx4fkFZUpmUiCqWFB05fsjJ+xsrSHQ7uXhfN/daWDAa+ua4SdBVACZxWefWPfm7q1wLvBfSBc8vOXVCxcLlTwcbgl9A/3IMP9hibSzswfRAFpESIP8PWOigqCPB/4EmjvxMSF/375FYGut+O+vJzI/+eLIpbxSpC7CQ/w2rlu+dHGM4r+NzWKwFpnzGswGUbKgAK/T53KLSioR1VAvjtAgn8hw/8ysfHAt0XzR1WWtWpG0+f7VTk4CxStggb7+9ufvj5wclpAVUQAX4c70xfeuSQXTpXilurbxw0//+8NPp8bG5l//LyzYNyLM7zYviEqgWBzOTsJlS+POZeVBaBypgrSU2M0PrPGVF0xCigDrsYysi0UFReVNKkrt2gtsAvw9oiMC16Qn33gx73LZx58fhvwiUgVgPMCE/PTL6br6FkQdFIvDkMO25VtWqboIq5enaEVaYvryRZYWf6yO1tXdW1A0OS29prYJct/Kh+FdREIXkb2zSOjv6x7g584z/2MGVEdHz+GjJ7//4YTKg/oQ8Wtp7SDP7CkDrbOyt090REBCbGhQoFeAr/ut70L2a+augZWF+Y126mYuF5bnF5RnnM25kDMfJ4kpaLg4bgZy5dCPcHNzEDkK4GFtxVPywLb2rtr6ZnhUVjYUllTQYRarelhA4vgTBgZ6bs6OZlxjQ0M2h8PmGLIVqziAJZcMSyEyAdGUnt6BqtqGkRG6TCRRM8wTBzT8kJ4oLqW+p6c8isgHHXqnc4JhK1pwTY1jo4IQA4mNCqZq1fF5wzBxQKysr3+QWWYDyW1G/8BgTCTDZM0kcUB8GhzJk2eyEQOBrw1BHRtrCsp+zBvGiAOSKYlxYVXVDWJxF2Ig8LUhuJIQE4KYA2PEATlbyHOeybyEGMvZ85fMzbkebk6IITBGHBbm3KyLlxUL0iqJnp5u6pIYFksHkQBkc1KTY+a05MPQkORCToEFj4sYAjWrQ86Dc1n5aI6Ag+LkICBpqOnYmMwJ/n59S0VVnfJHqSqFpB40eXFGewG/ubVNyflkLBbrx2/2/fD1u0g54M+2iNvthTZIc9FocQj5jU1tSu7MMzf19nKBdK6VpbmShzQ1tWn2MmQaKw6emSmHY9BE5gJpTc1i+AgzrgnSUDRWHEIhXyIZ6SZz7kJnd9/o6KhQoLEti8aKQ2Bn3djcikimoVEMH4Q0FMb0VuZK2ZWawUHSRxrnF5YbGnKQhqKx4qgluXaFgo7OHkSPkeJkoMm9FcxtgsWBIQSLA0OIxvocc2V8XDa1wZhZ8GSDxXGdzq6+jz8/jOQzGBBGzsIdYIyZFexzYAjB4sAQgsWBIQSLA0MIFgeGECwODCFYHBhCsDgwhGBxYAjB4sAQgsWBIQSLA0MIFgeGECwODCFYHBhCsDgwhGBxYAjB4sAQ8j8AAAD//175CLkAAAAGSURBVAMAatbEWdbczpEAAAAASUVORK5CYII=";
