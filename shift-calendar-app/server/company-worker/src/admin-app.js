// [ADMIN-01][ADMIN-03][ADMIN-04] The developer app: one installable web app at /admin/ for phones, the
// unfolded Fold and computers. Admins sign in with an account the owner invited and approved, and see
// only the areas the owner gave them; the owner signs in with ADMIN_PASSWORD and manages admins.
//
// It imports only its pure helpers (src/admin-lib.js), so `npm run bundle` can inline both into one
// worker.js for the dashboard.

import { adminLib } from "./admin-lib.js";
// AX 1.30 [ADMX-12] The stylesheet and three groups of screens live in src/admin/; each screen group is a function sent
// to the browser as its own text next to the app, like admin-lib.
import { ADMIN_CSS } from "./admin/style.js";
import { todayViews } from "./admin/views/today.js";
import { plazaViews } from "./admin/views/plaza.js";
import { insightViews } from "./admin/views/insight.js";

const ADMIN_CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; " +
  "manifest-src 'self'; worker-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

/** The pages the app answers on: the app itself, the invite link, and the page that explains installing. */
const PAGES = ["/admin/", "/admin/join", "/admin/install"];

export function adminFile(path, request) {
  const png = ICONS[path];
  if (png) {
    return new Response(Uint8Array.from(atob(png), (c) => c.charCodeAt(0)), {
      headers: { "content-type": "image/png", "cache-control": "public, max-age=86400", "x-content-type-options": "nosniff" },
    });
  }
  const file = adminFiles()[path];
  if (!file) return new Response("Not found", { status: 404 });
  const headers = {
    "content-type": file.type,
    // Checked each time, but an unchanged file is answered with 304 and no body (ETag).
    "cache-control": "no-cache",
    etag: file.etag,
    "content-security-policy": ADMIN_CSP,
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
    "x-robots-tag": "noindex, nofollow",
    "permissions-policy": "camera=(), microphone=(), geolocation=()",
    "x-frame-options": "DENY",
  };
  const asked = request?.headers.get("if-none-match");
  if (asked && asked.split(/\s*,\s*/).some((t) => t.replace(/^W\//, "") === file.etag)) return new Response(null, { status: 304, headers });
  return new Response(file.body, { headers });
}

const hashText = (text) => {
  let h = 5381;
  for (let i = 0; i < text.length; i++) h = ((h * 33) ^ text.charCodeAt(i)) >>> 0;
  return h.toString(36);
};

// AN 1.28 [ADMUX-39] tools/bundle.mjs writes these two already made into dist/worker.js, so a fresh worker copy
// sends the app without building it first; left null (wrangler deploy, the tests), they are made on first ask.
let builtFiles = null;
let builtVersion = null;

/** AN 1.28 [ADMUX-39] The files and build name as tools/bundle.mjs puts them into dist/worker.js. */
export function adminPrebuilt() {
  return { files: adminFiles(), version: adminBuild() };
}

/** [ADMUX-08] A name for this build of the app's files; /api/admin/badges sends it as build (AT 1.29: so does the 410 of the old /overview). */
export function adminBuild() {
  builtVersion ??= hashText(String(adminApp) + String(adminLib) + adminViewsText() + String(adminWorker) + ADMIN_CSS + ADMIN_HTML + JSON.stringify(MANIFEST));
  return builtVersion;
}

/** AX 1.30 [ADMX-12] The screen groups as the app gets them: { today, plaza, insight }, each its function's own text. */
const adminViewsText = () => `{ today: ${todayViews}, plaza: ${plazaViews}, insight: ${insightViews} }`;

/**
 * [ADMUX-08] Smaller files for the phone: whole-line comments and the indentation go. Only lines that are
 * nothing but a comment are dropped, so a "//" inside a string or an address stays; the tests run the app
 * as it is sent.
 */
function slimJs(code) {
  const out = [];
  let block = false;
  for (const line of code.split("\n")) {
    const t = line.trim();
    if (block) {
      if (t.includes("*/")) block = false;
      continue;
    }
    if (t.startsWith("/*") && !t.includes("`")) {
      if (!t.includes("*/")) block = true;
      continue;
    }
    if (!t || t.startsWith("//")) continue;
    out.push(t);
  }
  return `${out.join("\n")}\n`;
}

const slimCss = (css) => css.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\s*\n\s*/g, "").replace(/\s*([{};,>])\s*/g, "$1").replace(/;}/g, "}").trim();

/** The app's files with their tags, made once per worker copy. The phone keeps the app's own files under a name that changes with them. */
function adminFiles() {
  if (builtFiles) return builtFiles;
  const version = adminBuild();
  // [ADMUX-08] The app knows its own build, so it can say when the server has a newer one.
  const app = slimJs(`(${adminApp})((${adminLib})(), ${JSON.stringify(version)}, ${adminViewsText()});`);
  const list = {
    "/admin/app.js": [app, "text/javascript; charset=utf-8"],
    "/admin/sw.js": [slimJs(`(${adminWorker})(${JSON.stringify(version)});`), "text/javascript; charset=utf-8"],
    "/admin/app.css": [slimCss(ADMIN_CSS), "text/css; charset=utf-8"],
    "/admin/manifest.webmanifest": [JSON.stringify(MANIFEST), "application/manifest+json; charset=utf-8"],
    "/admin/icon.svg": [ICON_SVG, "image/svg+xml"],
  };
  for (const page of PAGES) list[page] = [ADMIN_HTML, "text/html; charset=utf-8"];
  builtFiles = Object.fromEntries(Object.entries(list).map(([p, [body, type]]) => [p, { body, type, etag: `"${hashText(body)}"` }]));
  return builtFiles;
}

const MANIFEST = {
  id: "/admin/",
  name: "밤낮 관리자",
  short_name: "밤낮 관리",
  description: "밤낮 앱의 제보, 문의, 회사 규칙과 관리자를 관리해요",
  lang: "ko",
  start_url: "/admin/",
  scope: "/admin/",
  display: "standalone",
  // [ADMUX-05] No window-controls-overlay: the app draws no title bar of its own to put there.
  display_override: ["standalone"],
  orientation: "any",
  background_color: "#15192B",
  theme_color: "#15192B",
  categories: ["business", "productivity"],
  icons: [
    { src: "/admin/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
    { src: "/admin/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
    { src: "/admin/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    { src: "/admin/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
  ],
  shortcuts: [
    { name: "검토", url: "/admin/#/review", icons: [{ src: "/admin/icon-192.png", sizes: "192x192" }] },
    { name: "문의", url: "/admin/#/inquiries", icons: [{ src: "/admin/icon-192.png", sizes: "192x192" }] },
  ],
};

const ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><rect width="512" height="512" rx="112" fill="#15192B"/>` +
  `<mask id="m"><rect width="512" height="512" fill="#fff"/><circle cx="302" cy="220" r="120" fill="#000"/></mask>` +
  `<circle cx="241" cy="271" r="138" fill="#C2A36B" mask="url(#m)"/><circle cx="348" cy="169" r="18" fill="#C2A36B"/></svg>`;

const ADMIN_HTML = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow">
<meta name="theme-color" content="#F5F3EE" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0E111C" media="(prefers-color-scheme: dark)">
<meta name="color-scheme" content="light dark">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="밤낮 관리">
<title>밤낮 관리자</title>
<link rel="manifest" href="/admin/manifest.webmanifest">
<link rel="icon" href="/admin/icon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/admin/icon-192.png">
<link rel="stylesheet" href="/admin/app.css">
<script src="/admin/app.js" defer></script>
</head>
<body>
<div id="app"><div class="center"><div class="spinner" role="progressbar" aria-label="불러오는 중"></div></div></div>
<div id="toast" class="toast" role="status" aria-live="polite"></div>
<dialog id="dialog" aria-modal="true"></dialog>
<noscript>이 화면은 자바스크립트가 켜져 있어야 열려요.</noscript>
</body>
</html>
`;

// Sizes are in rem so the phone's text size setting scales everything together; layouts reflow instead of clipping.
// [ADMUX-03] The layout steps are in px (rail from 600, sidebar from 1024) so a folded Fold (345) is a phone and
// an unfolded one (673) has the rail whatever the text size.

/** The app in the browser. Serialized into /admin/app.js, so it uses nothing from this module. */
function adminApp(L, BUILD, M) {
  const app = document.getElementById("app");
  const toastBox = document.getElementById("toast");
  const dialog = document.getElementById("dialog");

  const P = {
    home: '<path d="M4 11.5 12 4l8 7.5V20h-5.5v-5h-5v5H4z"/>',
    review: '<path d="M4 13h4l2 3h4l2-3h4"/><path d="M5.5 5h13L20 13v6H4v-6z"/>',
    inquiries: '<path d="M4 5h16v11H9l-5 4z"/><path d="M8 9.5h8M8 12.5h5"/>',
    companies: '<path d="M4 20V5.5L12 3v17M12 9h8v11M3 20h18M7 8h2M7 12h2M7 16h2M15 13h2M15 17h2"/>',
    community: '<path d="M3.5 5h11v8h-6l-4 3.5V13h-1z"/><path d="M14.5 9h6v8h-1v3l-3.5-3h-5.5v-4"/>',
    rewards: '<path d="M4 11h16v9H4zM3 7.5h18V11H3zM12 7.5V20"/><path d="M12 7.5C10.5 4 7 4 7.5 6.5c.3 1 2 1 4.5 1M12 7.5C13.5 4 17 4 16.5 6.5c-.3 1-2 1-4.5 1"/>',
    admins: '<circle cx="9" cy="8" r="3.2"/><path d="M3.5 19c.6-3.3 2.8-5 5.5-5s4.9 1.7 5.5 5"/><path d="M16 4.8a3 3 0 0 1 0 6M17.5 14c1.7.6 2.8 2.2 3.1 5"/>',
    audit: '<path d="M12 7v5l3 2"/><circle cx="12" cy="12" r="8.5"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"/>',
    account: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20c.8-3.8 3.6-5.8 7-5.8s6.2 2 7 5.8"/>',
    more: '<circle cx="5" cy="12" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="19" cy="12" r="1.3"/>',
    refresh: '<path d="M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5"/>',
    back: '<path d="M15 5l-7 7 7 7"/>',
    chevron: '<path d="M9 5l7 7-7 7"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/>',
    share: '<path d="M12 3v12M7.5 7.5 12 3l4.5 4.5"/><path d="M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7"/>',
    mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m4 7 8 6 8-6"/>',
    install: '<path d="M12 4v11M7.5 10.5 12 15l4.5-4.5"/><path d="M5 19h14"/>',
    logout: '<path d="M14 4h5v16h-5M10 8l-4 4 4 4M6 12h10"/>',
    moon: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z"/>',
    key: '<circle cx="8" cy="15" r="4"/><path d="m11 12 8-8M16 7l2 2M14 9l2 2"/>',
    help: '<circle cx="12" cy="12" r="8.5"/><path d="M9.6 9.5a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5V14M12 17.2v.1"/>',
    dev: '<path d="M8 8 4 12l4 4M16 8l4 4-4 4M13.5 5l-3 14"/>',
    check: '<path d="M5 12.5 10 17l9-10"/>',
    // [SRV-04][ADMIN-14] 운영, [ADMIN-13] 지표, [SRV-05] 공유 링크 신고.
    ops: '<path d="M4 6h16M4 12h16M4 18h10"/><circle cx="18" cy="18" r="2.5"/>',
    metrics: '<path d="M4 20V4M4 20h16"/><path d="M8 16v-4M12 16V8M16 16v-6"/>',
    // [ADMUX-17] 받은함, [ADMUX-18] 여러 판.
    inbox: '<path d="M3.5 13.5 6 5h12l2.5 8.5V19h-17z"/><path d="M3.5 13.5H9l1 2h4l1-2h5.5"/>',
    versions: '<rect x="3.5" y="7" width="11" height="13" rx="1.5"/><path d="M8 7V4.5h12.5V17H14.5"/>',
    shares: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    // AM 1.28 [ADMUX-21] 기능 관리(스위치 둘), [ADMUX-26] 근무 유형.
    flags: '<rect x="3" y="5" width="18" height="6" rx="3"/><circle cx="15" cy="8" r="1.8"/><rect x="3" y="13" width="18" height="6" rx="3"/><circle cx="9" cy="16" r="1.8"/>',
    types: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
    // AN 1.28 [ADMUX-30] 출시: a flag on its pole.
    release: '<path d="M5.5 21V3.5"/><path d="M5.5 4.5h11.5l-2.5 4 2.5 4H5.5"/>',
  };
  // AN 1.28 시작
  /**
   * [ADMUX-30] Where each screen lives and who sees it: the six hubs and their tabs are src/admin-lib.js HUBS and
   * TABS (1.28 통합계획 2-2), shown as this admin may see them. Badges count only what the admin must do there.
   */
  const hubs = () => L.visibleHubs(S.me);
  const tabOk = (key) => L.tabVisible(key, S.me);
  const tabName = (key) => L.TABS[key]?.label ?? key;
  const hubNow = () => hubs().find((h) => h.key === L.hubOf(S.route.name)) ?? null;
  // AN 1.28 끝
  const LEVEL_NAMES = { none: "없음", view: "보기", edit: "고치기" };
  /** [ADMIN-10][ADMIN-15] Areas that are a yes or no: the server says which (me.toggles). */
  const toggles = () => S.me?.toggles ?? S.data.admins?.toggles ?? ["devStatus"];
  const DEV_NAMES = L.DEV_NAMES;
  const DEV_TONES = { todo: "info", doing: "warn", done: "ok", rejected: "bad" };
  const DEV_TO = { todo: "접수로", doing: "개발중으로", done: "완료로", rejected: "반려로" };
  /** [ADMIN-11] What 반려 usually says, one tap each; the admin can still type their own. */
  const REJECT_REASONS = ["이미 있는 기능이에요", "지금 계획과 맞지 않아요", "기술적으로 어려워요", "같은 항목이 있어요", "더 많은 요청을 기다려요"];
  /** [ADMIN-11] The answer to linked questions when an item is 완료 or 반려, ready to send after a read. */
  const DEV_REPLIES = {
    done: "말씀해 주신 내용을 반영했어요. 앱을 최신 버전으로 업데이트하면 쓸 수 있어요. 알려 주셔서 고마워요.",
    rejected: "보내 주신 의견을 꼼꼼히 살펴봤어요. 아쉽지만 지금은 넣기 어려워요. 같은 의견이 더 모이면 다시 볼게요.",
    doing: "말씀해 주신 내용을 지금 만들고 있어요. 업데이트되면 다시 알려 드릴게요.",
  };
  const KIND_NAMES = { question: "질문", bug: "문제 신고", problem: "문제 신고", idea: "건의·제안", company: "회사 방식", other: "그 밖의 이야기" };
  // [J133-40] 순번 720: the kind as the sender picked it in the app (InquiryKind), said beside the admin's name for it.
  const APP_KIND_NAMES = { question: "궁금해요", bug: "앱이 이상해요", idea: "건의·제안", company: "회사 방식 알려 주기", other: "그 밖의 이야기" };
  // [J133-40] "접수됨으로", "답변 완료로": 로 after a vowel or ㄹ, 으로 after another final consonant; (으)로 for anything else.
  const roSuffix = (w) => {
    const c = String(w ?? "").trim().slice(-1).charCodeAt(0);
    if (!(c >= 0xac00 && c <= 0xd7a3)) return "(으)로";
    const j = (c - 0xac00) % 28;
    return j === 0 || j === 8 ? "로" : "으로";
  };
  const STATUS_NAMES = { open: "답 대기", answered: "답함", closed: "닫힘" };
  // [INQ-02] The stages the asker's app shows.
  const PROGRESS_NAMES = { received: "접수됨", checking: "확인 중", answered: "답변 완료", planned: "개발 목록에 올림", building: "개발 중", released: "반영됨" };
  /** [INQ-04] How long the asker has waited: red past a day, so nothing is missed. */
  const waitPill = (q) => q.waitingHours === null || q.waitingHours === undefined || q.status !== "open" ? ""
    : `<span class="pill ${q.waitingHours >= 24 ? "bad" : q.waitingHours >= 8 ? "warn" : ""}">${L.waited(q.waitingHours)}</span>`;
  // AX 1.30 [ADMX-01] Every activity code's words live in admin-lib now (L.actionName for a code nobody named yet).
  const ACTION_NAMES = L.ACTION_NAMES;
  /** [ADMIN-17] The activity screen's 종류 filter, as the server names them. */
  const AUDIT_KIND_NAMES = { login: "로그인", admins: "관리자", review: "검토", companies: "회사·규칙", rewards: "보상", community: "토론·임금표",
    inquiries: "문의", dev: "개발사항", ops: "운영", settings: "설정", flags: "기능 관리" };
  // [ADMUX-01] Every error code the server sends has words in src/admin-lib.js (a test checks).
  const ERRORS = L.ERRORS;

  const TEMPLATES = [
    ["확인 중", "알려 주셔서 고마워요. 지금 확인하고 있어요. 고치는 대로 다시 알려 드릴게요."],
    ["고쳤어요", "말씀하신 문제를 고쳤어요. 앱을 {앱버전}보다 새 버전으로 업데이트한 뒤 다시 확인해 주세요."],
    ["더 알려 주세요", "정확히 확인하려면 조금 더 알려 주세요. 언제, 어떤 화면에서, 무엇을 눌렀을 때 생겼는지 적어 주시면 큰 도움이 돼요."],
    ["설정 안내", "설정에서 바로 바꿀 수 있어요. 설정 > 해당 항목에서 원하는 값으로 바꿔 보세요."],
  ].map(([label, text], i) => ({ id: -1 - i, label, text, topic: "", uses: 0 }));

  /**
   * [ADMIN-12][ADMIN-16] The shared answers: the question's own list (its topic first, then the most
   * used) when one is open, else the list as loaded, else the built-in ones until they come.
   */
  const templateList = () => (S.route.name === "inquiries" && S.data.inquiry?.id === S.route.id && S.data.inquiry.templates
    ? S.data.inquiry.templates : S.templates ?? TEMPLATES);

  // ---- [ADMUX-03] this device's choices, kept in the browser only ----

  const PREF_KEY = "bamnat-admin-prefs";
  const PREFS = { theme: "system", size: "normal", tabs: [], sound: false, next: true, views: [], loginMode: "account", v: 2 };
  function readPrefs() {
    try {
      const v = JSON.parse(localStorage.getItem(PREF_KEY) ?? "{}");
      const was = { ...PREFS, v: 1, ...(v && typeof v === "object" ? v : {}) };
      // AN 1.28 [ADMUX-35] Saved views and bottom tabs from before the hubs, moved once and kept.
      const now = L.migratePrefs(was);
      if (now !== was && Object.keys(v ?? {}).length) localStorage.setItem(PREF_KEY, JSON.stringify(now));
      return now;
    } catch {
      return { ...PREFS };
    }
  }
  function savePrefs() {
    try {
      localStorage.setItem(PREF_KEY, JSON.stringify(S.prefs));
    } catch {
      // A private window: the choice lasts until the tab closes.
    }
    applyPrefs();
  }
  function applyPrefs() {
    const root = document.documentElement;
    if (S.prefs.theme === "light" || S.prefs.theme === "dark") root.dataset.theme = S.prefs.theme;
    else delete root.dataset.theme;
    // AT 1.29 [ADMCP-07] 아주 크게 (125%): the style had it, the setting did not offer it.
    if (["small", "large", "xlarge"].includes(S.prefs.size)) root.dataset.size = S.prefs.size;
    else delete root.dataset.size;
  }

  // ---- [ADMUX-01] drafts: what was typed survives a repaint, a reload and a lost sign-in ----

  const DRAFT = "bamnat-admin-draft:";
  /** Boxes whose words are kept per screen; anything else is only kept across a repaint. */
  const KEEP = ["reply", "inq-note", "dev-note"];
  const draftKey = (id) => `${DRAFT}${S.route.name}/${S.route.id ?? ""}:${id}`;
  function readDraft(id) {
    try {
      return localStorage.getItem(draftKey(id)) ?? "";
    } catch {
      return "";
    }
  }
  function writeDraft(id, value) {
    try {
      if (value) localStorage.setItem(draftKey(id), value);
      else localStorage.removeItem(draftKey(id));
    } catch {
      // Not kept; the box still has it.
    }
  }
  function clearDrafts() {
    try {
      for (const k of Object.keys(localStorage)) if (k.startsWith(DRAFT)) localStorage.removeItem(k);
    } catch {
      // Nothing kept.
    }
  }

  const S = {
    phase: "boot", // boot, login, app, join, install
    me: null, overview: null, health: null, server: null,
    route: { name: "home", id: null, params: {} },
    recent: [], // [ADMUX-04] details opened this visit, for the palette
    opened: new Map(), // [ADMUX-05] <details data-key> the admin opened or shut, by key
    data: {}, // per screen
    // [ADMIN-13] 지표 기간(일), [ADMIN-14] 운영 화면의 달(YYYY-MM, 비면 이번 달).
    metricDays: 28, opsMonth: "",
    // [ADMUX-13] 지표를 날짜로 고른 기간(비면 metricDays), [ADMUX-17] 받은함 보기, [ADMUX-18] 여러 판 보기, [ADMUX-19] 토론·임금표 여러 개.
    metricFrom: "", metricTo: "", inboxView: "all", versionShow: "conflict", postPick: null, ratePick: null,
    reviewFilter: "all", inquiryStatus: "open", inquiryTopic: "", rewardTab: "owed", query: "", postFilter: "flagged", rateFilter: "review",
    // [ADMIN-12] Whose questions: everyone's, mine, nobody's. [ADMIN-10] The 개발사항 list's filter, words and picked items.
    inquiryWho: "", devStatus: "open", devQuery: "", devWho: "", devPick: null, templates: null,
    // [ADMIN-16] 문의 찾기·정렬, 여러 개 고르기. [ADMIN-17] 활동 기록 거르기. 토론·보상 찾기.
    inquiryQuery: "", inquirySort: "default", inquiryPick: null, postQuery: "", rewardQuery: "",
    audit: { actor: "", kind: "", from: "", to: "" },
    draft: null, slaDraft: null, error: "", busy: 0, online: navigator.onLine,
    join: null, // invite info on the join page
    installPrompt: null, installed: matchMedia("(display-mode: standalone)").matches || navigator.standalone === true,
    prefs: readPrefs(),
    // [ADMUX-01] the screen whose data failed to come, [ADMUX-08] a newer build, answers from the phone's copy.
    failed: "", newBuild: false, stale: false, relogin: false, needCode: null,
    // [ADMUX-04] 큐 모드: answering the waiting questions one after another.
    queue: null, cursor: -1,
    // AM 1.28 [ADMUX-21][ADMUX-24][ADMUX-25] 기능 관리: 상태 거르기, 찾기, 목록·일정, 미리 보기의 휴대폰.
    flagShow: "all", flagQuery: "", flagView: "", flagSim: null,
    // AT 1.29 [ADMCP-02][ADMCP-06] 문의 종류·복사 전만·같은 기기 거르기, the copy box open now, the row a Shift range starts at.
    inquiryKind: "", inquiryCopied: "", inquirySender: "", copy: null, pickAnchor: null, bulkMore: false,
    // AX 1.30 [ADMX-06][ADMX-09][ADMX-03][ADMX-08] 회사 순서·여러 개, 검토 여러 개, 여러 판 같은 항목도 보기, 찾기·이동의 서버 찾기, 주의 필요 수.
    companySort: "", companyPick: null, reviewPick: null, cmpAll: null, alertsN: 0,
  };

  const esc = L.esc;
  const icon = (paths) => `<svg viewBox="0 0 24 24" aria-hidden="true">${paths}</svg>`;
  const day = (iso) => (iso ? new Date(iso).toLocaleDateString("ko-KR", { month: "long", day: "numeric" }) : "");
  const moment = (iso) => (iso ? new Date(iso).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit" }) : "");
  const clock = (iso) => new Date(iso).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });
  const ago = (iso) => L.ago(iso);
  const can = (area, level = "view") => L.can(S.me, area, level);
  const visible = (n) => (n.owner ? S.me?.owner : n.area ? can(n.area) : true);
  const wide = () => matchMedia("(min-width:1024px)").matches;
  /** [ADMUX-02] A screen's address; a list keeps the filters it has now, so 뒤로 lands where the admin was. */
  const href = (name, id) => L.routeHash(name, id ?? null, id ? {} : paramsOf(name));
  const routeKey = () => `${S.route.name}/${S.route.id ?? ""}`;
  /** The pill for minutes left before an answer target (null: nobody waits). */
  const slaPill = (minutes) => {
    const t = L.slaText(minutes);
    return t ? `<span class="pill ${t.tone}">${esc(t.text)}</span>` : "";
  };
  let toastTimer = 0;
  let searchTimer = 0;
  let pendingConfirm = null;
  let gKey = "";
  let gTimer = 0;
  let typingSent = 0;
  let pollTimer = 0;
  const seq = L.sequencer();

  // ---- [ADMUX-02] filters in the address ----

  /** Which state each screen's address filters stand for. */
  const BIND = {
    inquiries: { status: "inquiryStatus", who: "inquiryWho", topic: "inquiryTopic", q: "inquiryQuery", sort: "inquirySort",
      kind: "inquiryKind", copied: "inquiryCopied", sender: "inquirySender" /* AT 1.29 [ADMCP-02] */ },
    dev: { status: "devStatus", who: "devWho", q: "devQuery" },
    review: { show: "reviewFilter" },
    community: { show: "postFilter", q: "postQuery" },
    rewards: { status: "rewardTab", q: "rewardQuery" },
    companies: { q: "query", sort: "companySort" /* AX 1.30 [ADMX-06] */ },
    metrics: { days: "metricDays", from: "metricFrom", to: "metricTo" },
    // AN 1.28 [ADMUX-33][ADMUX-35] 오늘 › 할 일 keeps 받은함's 보기, 이달 할 일 keeps 운영's month.
    chores: { month: "opsMonth" },
    home: { view: "inboxView" },
    versions: { show: "versionShow" },
    flags: { show: "flagShow", q: "flagQuery", view: "flagView" },
  };
  function paramsOf(name) {
    if (name === "audit") return { ...S.audit };
    const bind = BIND[name];
    return bind ? Object.fromEntries(Object.entries(bind).map(([k, field]) => [k, String(S[field] ?? "")])) : {};
  }
  function applyParams(route) {
    if (route.id) return;
    const p = route.params ?? {};
    if (route.name === "audit") {
      S.audit = { actor: p.actor ?? "", kind: p.kind ?? "", from: p.from ?? "", to: p.to ?? "" };
      return;
    }
    for (const [k, field] of Object.entries(BIND[route.name] ?? {})) {
      if (!Object.hasOwn(p, k)) continue;
      S[field] = field === "metricDays" ? ([7, 28, 90].includes(Number(p[k])) ? Number(p[k]) : 28) : p[k];
    }
  }
  /** Writes the filters into the address without a new history step or a reload. */
  function syncUrl() {
    if (S.phase !== "app") return;
    const next = `/admin/${href(S.route.name, S.route.id)}`;
    if (`${location.pathname}${location.hash}` !== next) history.replaceState(null, "", next);
  }

  // ---- talking to the server ----

  /** [ADMUX-01] GET answers by path with their ETag: an unchanged list comes back as 304 and no body. */
  const etags = new Map();

  async function api(path, method = "GET", body, { signal } = {}) {
    S.busy++;
    progress();
    try {
      let res;
      const cached = method === "GET" ? etags.get(path) : null;
      try {
        res = await fetch(`/api/admin${path}`, {
          method,
          credentials: "same-origin",
          signal,
          headers: method === "GET" ? (cached ? { "if-none-match": cached.etag } : {}) : { "content-type": "application/json", "x-admin": "1" },
          body: method === "GET" ? undefined : JSON.stringify(body || {}),
        });
      } catch (e) {
        if (e?.name === "AbortError") throw Object.assign(new Error("aborted"), { quiet: true, data: {} });
        throw Object.assign(new Error("offline"), { data: {} });
      }
      if (res.status === 304 && cached) return structuredClone(cached.data);
      if (method === "GET") {
        // [ADMUX-01] The service worker answers from the phone's copy when the network is gone.
        const stale = res.headers.get("x-admin-cache") === "1";
        if (stale !== S.stale) {
          S.stale = stale;
          paintBanners();
        }
      }
      const data = await res.json().catch(() => ({}));
      if (res.status === 401 && data.error === "login" && S.phase === "app") {
        // [ADMUX-01] Signed out under way: sign in again over the screen, so nothing typed is lost.
        openRelogin();
        throw Object.assign(new Error("login"), { quiet: true, data });
      }
      if (!res.ok) throw Object.assign(new Error(data.error || "server"), { data, status: res.status });
      const etag = res.headers.get("etag");
      if (method === "GET" && etag) etags.set(path, { etag, data: structuredClone(data) });
      if (method !== "GET") {
        etags.clear();
        // AN 1.28 [ADMUX-37] What was just changed may change the menu numbers: asked again, not waited for.
        badgesAt = 0;
        if (S.phase === "app") setTimeout(() => refreshBadges(), 0);
      }
      return data;
    } finally {
      S.busy--;
      progress();
    }
  }

  async function health() {
    try {
      return await (await fetch("/api/admin/health")).json();
    } catch {
      return null;
    }
  }

  function progress() {
    document.getElementById("progress")?.classList.toggle("on", S.busy > 0);
    document.getElementById("main")?.setAttribute("aria-busy", String(S.busy > 0));
  }

  /** [ADMUX-01] A short note at the bottom; with [undo], a button that takes the last step back for 8 seconds. */
  function toast(text, undo) {
    toastBox.innerHTML = `<span>${esc(text)}</span>${undo ? '<button class="undo" type="button" data-undo="1">되돌리기</button>' : ""}`;
    toastBox.classList.add("show");
    toastBox.classList.toggle("has-undo", Boolean(undo));
    S.undo = undo ?? null;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastBox.classList.remove("show", "has-undo");
      S.undo = null;
    }, undo ? 8000 : 3000);
  }

  function fail(e) {
    if (!e.quiet) toast(e.message === "server" || !ERRORS[e.message] ? L.errorText(e.message) : ERRORS[e.message]);
  }

  // ---- routes ----

  /**
   * The screen the address names. AN 1.28 [ADMUX-35] An address from before 1.28 goes to its new place with its
   * filters (#/inbox → 오늘, #/ops → 출시); a tab this admin may not see, or no known screen, is 오늘.
   */
  function readRoute() {
    const route = L.redirect(L.parseRoute(location.hash));
    const known = [...Object.keys(L.TABS), "settings", "more"];
    if (!known.includes(route.name) || (L.TABS[route.name] && !tabOk(route.name))) return { name: "home", id: null, params: {} };
    return route;
  }

  /** [ADMUX-35] After an old address was followed, the address bar shows the new one (no new history step). */
  function fixAddress() {
    const path = (h) => h.split("?")[0];
    if (path(location.hash) !== path(href(S.route.name, S.route.id))) syncUrl();
  }

  function go(name, id) {
    const next = href(name, id);
    if (location.hash === next) load();
    else location.hash = next;
  }

  // ---- AN 1.28 [ADMUX-37] the menu numbers, apart from the screens ----

  /** When the numbers were last asked for; a write sets it back to 0 so the next look asks again. */
  let badgesAt = 0;
  const BADGES_FRESH = 15000;
  const countsKey = () => JSON.stringify(Object.fromEntries(Object.entries(S.overview ?? {}).filter(([, v]) => typeof v === "number" || (v && typeof v === "object" && "waiting" in v))));

  /**
   * [ADMUX-37] The menu numbers and who is signed in (/badges): never waited for by a screen. Within 15 seconds of
   * the last answer nothing is asked unless [force]. A change of permissions draws the menu again.
   */
  async function refreshBadges(force = false) {
    if (S.phase !== "app" || (!force && Date.now() - badgesAt < BADGES_FRESH)) return;
    badgesAt = Date.now();
    try {
      const b = await api("/badges");
      const before = JSON.stringify([S.me?.perms, S.me?.owner, S.me?.name]);
      // 1.28 통합 [ADMUX-27] 오늘's top card (a legal flag just on) comes with the numbers, so a change draws 오늘 again.
      const alerts = JSON.stringify(S.overview?.flagAlerts ?? []);
      S.overview = { ...(S.overview ?? {}), ...b };
      if (!("flagAlerts" in b)) S.overview.flagAlerts = [];
      S.me = b.me;
      S.newBuild = Boolean(b.build && b.build !== BUILD);
      if (S.phase !== "app") return;
      const home = S.route.name === "home" && JSON.stringify(S.overview.flagAlerts) !== alerts;
      if (home || JSON.stringify([S.me?.perms, S.me?.owner, S.me?.name]) !== before) render({ keepScroll: true });
      else {
        paintBanners();
        updateBadges();
      }
    } catch {
      badgesAt = 0;
    }
  }

  /** [ADMUX-37] What the screens share and rarely changes (/basics): 채택 settings, answer targets, reason words. */
  async function loadBasics() {
    const b = await api("/basics");
    S.basics = true;
    S.overview = { ...(S.overview ?? {}), ...b };
  }

  /**
   * Fetches what the current screen shows, and only that. [ADMUX-01] Each call has a number: an answer for a
   * screen the admin already left is dropped instead of drawn. AN 1.28 [ADMUX-37] The menu numbers are not
   * waited for: they come on their own (refreshBadges) and only change the numbers.
   */
  async function load({ quiet = false } = {}) {
    const { name, id } = S.route;
    const key = routeKey();
    const n = seq.next("load");
    const keep = (fn) => (d) => {
      if (seq.current("load", n)) fn(d);
    };
    const jobs = [];
    refreshBadges();
    try {
      // [ADMUX-33] 오늘: the one 할 일 list and the numbers above it, only while it is shown.
      if (name === "home") jobs.push(api("/today").then(keep((d) => { S.data.today = d; S.alertsN = d.alerts?.length ?? 0; })));
      // The reason words (검토, 토론), the 채택 settings and answer targets, once.
      if (!S.basics && ["review", "community", "adopt", "sla", "versions"].includes(name)) jobs.push(loadBasics());
      if (name === "review") jobs.push(api("/queue").then(keep((d) => { S.data.queue = d; })));
      if (name === "inquiries" && (!id || wide())) jobs.push(loadInquiries(n));
      if (name === "inquiries" && id) jobs.push(api(`/inquiries/${id}`).then(keep((d) => { S.data.inquiry = d; })));
      if (name === "dev" && !id) jobs.push(loadDev(n));
      // 1.35 [INBOX-01][INBOX-05] 정리함 and what is ready to go to Claude.
      if (name === "sort") jobs.push(api("/inquiries/sort").then(keep((d) => { S.data.sort = d; })), api("/inquiries/handoff").then(keep((d) => { S.data.handoff = d; })));
      if (name === "dev" && id) jobs.push(api(`/dev/${id}`).then(keep((d) => { S.data.dev = d; })));
      // [ADMUX-30] 문의 › 자주 쓰는 답, a screen of its own now.
      if (name === "templates") jobs.push(loadTemplates({ strict: true }));
      if (name === "companies" && !id) jobs.push(api(companiesPath()).then(keep((d) => { S.data.list = d; })));
      if (name === "companies" && id) jobs.push(api(`/companies/${id}`).then(keep((d) => { S.data.company = d; })));
      if (name === "community") {
        // An id is one talk: company.setup, opened from the review screen.
        const [company, setup] = (id ?? "").split(".");
        const q = S.postQuery ? `&q=${encodeURIComponent(S.postQuery)}` : "";
        if (!id && S.postFilter === "rates") jobs.push(api(`/rates?status=${S.rateFilter}${q}`).then(keep((d) => { S.data.rates = d; })));
        else jobs.push(api(id ? `/posts?company=${company}&setup=${setup}` : `/posts?status=${S.postFilter}${q}`).then(keep((d) => { S.data.posts = d; })));
      }
      if (name === "rewards" && can("rewards")) {
        jobs.push(api(`/rewards?status=${S.rewardTab}${S.rewardQuery ? `&q=${encodeURIComponent(S.rewardQuery)}` : ""}`).then(keep((d) => { S.data.rewards = d; })));
      }
      // [INVITE-08][ADMUX-32] 친구 초대 rules, moved from 설정 to 관리 › 보상.
      if (name === "rewards" && can("settings")) jobs.push(api("/settings/invite").then(keep((d) => { S.data.inviteSettings = d; })));
      if (name === "admins") jobs.push(api("/admins").then(keep((d) => { S.data.admins = d; })), api("/devices").then(keep((d) => { S.data.phones = d; })));
      if (name === "audit") jobs.push(api(`/audit${auditQuery()}`).then(keep((d) => { S.data.audit = d; })));
      // [ADMUX-32] 운영 in tabs of 출시: 앱 공지 and 이달 할 일 (its 기능 스위치 are flags in 기능 관리 now).
      if (name === "notices") jobs.push(api("/notices").then(keep((d) => { S.data.notices = d; })));
      if (name === "chores") jobs.push(api(`/ops${S.opsMonth ? `?month=${S.opsMonth}` : ""}`).then(keep((d) => { S.data.ops = d; })));
      if (name === "metrics" || name === "gates") {
        const range = S.metricFrom && S.metricTo ? `from=${S.metricFrom}&to=${S.metricTo}` : `days=${S.metricDays}`;
        jobs.push(api(`/metrics?${name === "gates" ? "days=28" : range}`).then(keep((d) => { S.data[name === "gates" ? "gates" : "metrics"] = d; })));
        // [ADMUX-15] The remote files are read from the site by the server; a slow site does not hold the screen.
        api("/remote").then(keep((d) => { S.data.remote = d; if (S.route.name === "metrics" || S.route.name === "gates") render({ keepScroll: true }); })).catch(() => {});
      }
      if (name === "versions") jobs.push(api(`/versions?show=${S.versionShow}`).then(keep((d) => { S.data.versions = d; })));
      if (name === "shares") jobs.push(api("/shares").then(keep((d) => { S.data.shares = d; })));
      // [RCP-39] 1.34 나눔 판: the list of one tab and the month's numbers.
      if (name === "recipes") {
        // [RCX-08][RCX-15][RCX-04] 1.35 tabs read their own lists.
        const t = S.recipeTab ?? "flagged";
        // [PRF-06][PRF-10] 1.36 신고된 프로필, 이달의 제작자.
        const src = { picks: "/recipes/picks", images: "/recipes/images", requests: "/recipes/requests", makers: "/recipes/makers/flagged",
          month: "/recipes/makers/month" }[t] ?? `/recipes?tab=${t}`;
        jobs.push(api(src).then(keep((d) => { S.data.recipes = { tab: t, ...d }; })));
        jobs.push(api("/recipes/month").then(keep((d) => { S.data.recipeMonth = d; })).catch(() => {}));
        if (S.recipeLog) jobs.push(api("/recipes/actions").then(keep((d) => { S.data.recipeLog = d; })).catch(() => {}));
      }
      // [ADMIN-19] 알림 on this device, now under 관리 › 내 계정·이 기기.
      if (name === "account") jobs.push(api("/me/push").then(keep((d) => { S.data.push = d; })).catch(() => {}));
      // S 1.32 [SECX-01] 휴대폰 미리 보기, set here by role (no longer on the phone).
      if (name === "account") jobs.push(api("/me/phones").then(keep((d) => { S.data.myPhones = d; })).catch(() => {}));
      // R131 1.31 [ADMX-16]
      if (name === "account") jobs.push(api("/me/2fa").then(keep((d) => { S.data.second = { ...(S.data.second?.secret && !d.on ? S.data.second : {}), ...d }; })).catch(() => {}));
      // [ADMIN-15] The owner's server state.
      if (name === "server") jobs.push(api("/server").then(keep((d) => { S.server = d; })));
      // AM 1.28 [ADMUX-21][ADMUX-22][ADMUX-25][ADMUX-26] 출시 › 기능 관리 (list, timeline, one flag) and 근무 유형.
      if (name === "flags" && !id) jobs.push(api("/flags").then(keep((d) => { S.data.flags = d; })));
      if (name === "flags" && !id && S.flagView === "timeline" && can("ops")) api("/ops").then(keep((d) => { S.data.flagOps = d; if (S.route.name === "flags") render({ keepScroll: true }); })).catch(() => {});
      if (name === "flags" && id) jobs.push(api(`/flags/${encodeURIComponent(id)}`).then(keep((d) => { S.data.flag = d; })));
      if (name === "types") jobs.push(api("/types").then(keep((d) => { S.data.types = d; })));
      // 1.28 통합 [ADMUX-40] 분석 › 기능별 효과: each flag's share of phones and problems before and after it turned on.
      if (name === "effects") jobs.push(api("/effects").then(keep((d) => { S.data.effects = d; })));
      const results = await Promise.allSettled(jobs);
      const failed = results.find((r) => r.status === "rejected" && !r.reason?.quiet);
      if (seq.current("load", n)) {
        // [ADMUX-01] Nothing came for the screen: it says so with a button to try again, not a skeleton forever.
        S.failed = failed ? key : "";
        if (failed && !quiet) fail(failed.reason);
      }
    } catch (e) {
      if (!quiet) fail(e);
    }
    // A screen left while it loaded does not repaint over the new one.
    if (seq.current("load", n) && routeKey() === key && S.phase === "app") render({ keepScroll: true });
  }

  /** AX 1.30 [ADMX-06] The companies list as the server sorts it. */
  const companiesPath = () => `/companies?q=${encodeURIComponent(S.query)}${S.companySort ? `&sort=${S.companySort}` : ""}`;

  /** [ADMIN-16] The questions list: one page, or the next page added to the one shown ([more]). */
  async function loadInquiries(n, more = false) {
    try {
      // [INQ-04] 놓친 문의 is its own filter beside who holds it.
      const who = S.inquiryWho === "late" ? "&late=1" : S.inquiryWho ? `&who=${S.inquiryWho}` : "";
      const q = S.inquiryQuery ? `&q=${encodeURIComponent(S.inquiryQuery)}` : "";
      const after = more && S.data.inquiries?.next ? `&after=${S.data.inquiries.next}` : "";
      // AT 1.29 [ADMCP-02][ADMCP-06] 문제 신고만, 복사 전만, one phone's questions.
      const more2 = `${S.inquiryKind ? `&kind=${S.inquiryKind}` : ""}${S.inquiryCopied ? `&copied=${S.inquiryCopied}` : ""}${S.inquirySender ? `&sender=${S.inquirySender}` : ""}`;
      const d = await api(`/inquiries?status=${S.inquiryStatus}${S.inquiryTopic ? `&topic=${S.inquiryTopic}` : ""}${who}${q}${more2}&sort=${S.inquirySort}${after}`);
      if (n !== undefined && !seq.current("load", n)) return;
      S.data.inquiries = more ? { ...d, inquiries: [...S.data.inquiries.inquiries, ...d.inquiries] } : d;
      S.data.inquiriesMissing = false;
    } catch (e) {
      // The server has no inquiry box yet: the screen says so instead of failing.
      if (e.message === "not_found") S.data.inquiriesMissing = true;
      else throw e;
    }
  }

  async function loadDev(n, more = false) {
    const after = more && S.data.devList?.next ? `&after=${S.data.devList.next}` : "";
    const d = await api(`/dev?status=${S.devStatus}&q=${encodeURIComponent(S.devQuery)}${S.devWho ? `&who=${S.devWho}` : ""}${after}`);
    if (n !== undefined && !seq.current("load", n)) return;
    S.data.devList = more ? { ...d, items: [...S.data.devList.items, ...d.items] } : d;
  }

  /** [ADMIN-12] The answers every admin shares; until the server has them, the ones built in. */
  async function loadTemplates({ strict = false } = {}) {
    try {
      const d = await api("/templates");
      S.templates = d.templates;
      S.topicNames = d.topicNames;
    } catch (e) {
      S.templates = null;
      // AN 1.28 On its own screen a failure says so (with 다시 불러오기) instead of showing the built-in answers.
      if (strict) throw e;
    }
  }

  /** [ADMIN-17] The activity filters as the address the server reads. */
  const auditQuery = (before) => {
    const p = Object.entries({ ...S.audit, ...(before ? { before } : {}) }).filter(([, v]) => v);
    return p.length ? `?${p.map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join("&")}` : "";
  };

  // ---- the frame around every screen ----

  /** [ADMUX-01] What the frame was drawn for; while it holds, a repaint only redraws the screen inside. */
  let shellKey = "";

  function render({ keepScroll = false } = {}) {
    if (S.phase === "boot") return;
    if (S.phase !== "app") {
      shellKey = "";
      app.innerHTML = S.phase === "login" ? loginView() : S.phase === "join" ? joinView() : installView();
      if (S.phase === "login") document.querySelector(".gate input:not([disabled])")?.focus();
      return;
    }
    const y = window.scrollY;
    const list = hubs();
    const bottom = L.bottomHubs(list, S.prefs.tabs, S.route.name);
    const key = JSON.stringify([list.map((h) => [h.key, h.tabs]), bottom.tabs, bottom.more, S.me?.name, S.me?.owner]);
    if (key !== shellKey || !document.getElementById("main")) {
      app.innerHTML = shell(list, bottom);
      shellKey = key;
    }
    paintFrame(list, bottom);
    paintMain();
    if (keepScroll) window.scrollTo(0, y);
  }

  // AN 1.28 시작
  /** [ADMUX-30] The tab of each hub the admin was on last (this visit), so a hub opens where it was left. */
  const lastTab = {};

  /** [ADMUX-30] A hub in the sidebar, the rail or the bottom bar; its number is what waits for the admin there. */
  function hubLink(h, cls) {
    return `<a class="${cls}" href="${href(h.tabs[0])}" data-nav="${h.key}">${icon(P[h.icon])}
      <span class="nav-label">${esc(h.label)}</span><i class="badge" data-badge="hub:${h.key}"></i></a>`;
  }

  /**
   * [ADMUX-30] The frame: on a phone the bottom bar (오늘·문의·광장·출시 and 더 보기 when not every hub fits), on an
   * unfolded Fold the six-item rail, on a computer the sidebar with the open hub's tabs under it. Each hub's tabs
   * are also the row under the title (#hubtabs), hidden when the hub has only one.
   */
  function shell(list, bottom) {
    const more = { key: "more", label: "더 보기", icon: "more", tabs: ["more"] };
    const inBar = bottom.tabs.map((k) => list.find((h) => h.key === k)).filter(Boolean);
    return `
      <div id="banners"></div>
      <a class="skip" href="#main">본문으로 건너뛰기</a>
      <div class="shell">
        <aside class="side" aria-label="메뉴">
          <div class="brand-row"><img src="/admin/icon.svg" alt=""><b>밤낮 관리자</b></div>
          <ul class="nav">${list.map((h) => `<li data-hub="${h.key}">${hubLink(h, "")}${h.tabs.length > 1 ? `<ul class="subnav" aria-label="${esc(h.label)} 안 화면">${h.tabs.map((t) =>
            `<li><a href="${href(t)}" data-tab="${t}"><span>${esc(tabName(t))}</span><i class="badge" data-badge="tab:${t}"></i></a></li>`).join("")}</ul>` : ""}</li>`).join("")}</ul>
          <div class="foot">
            <a class="who" href="${href("account")}" data-tab="account">
              <span class="avatar" aria-hidden="true">${esc((S.me?.name ?? "?").slice(0, 1))}</span>
              <span><b>${esc(S.me?.name ?? "")}</b><small>${S.me?.owner ? "최고 관리자" : esc(S.me?.email ?? "")}</small></span></a>
          </div>
        </aside>
        <div class="content">
          <header class="top">
            <div class="bar" id="bar"></div>
            <nav class="hubtabs" id="hubtabs" hidden></nav>
            <div id="progress" class="progress${S.busy ? " on" : ""}"></div>
          </header>
          <main class="main" id="main" tabindex="-1"></main>
        </div>
      </div>
      <nav class="tabs" aria-label="메뉴">${inBar.map((h) => hubLink(h, "tab")).join("")}${bottom.more ? hubLink(more, "tab") : ""}</nav>`;
  }

  /** The parts of the frame that change with the screen: banners, the title bar, the hub's tabs, what is lit. */
  function paintFrame(list = hubs(), bottom = L.bottomHubs(list, S.prefs.tabs, S.route.name)) {
    paintBanners();
    const here = L.hubOf(S.route.name);
    if (here && L.TABS[S.route.name]) lastTab[here] = S.route.name;
    const { title, sub, back } = heading(bottom);
    const bar = document.getElementById("bar");
    if (bar) {
      bar.innerHTML = `${back ? `<a class="icon-btn" href="${back}" aria-label="뒤로">${icon(P.back)}</a>` : '<img class="mark" src="/admin/icon.svg" alt="">'}
        <h1 tabindex="-1" id="title">${esc(title)}${sub ? `<span class="sub">${esc(sub)}</span>` : ""}</h1>
        <button class="icon-btn" data-act="palette" aria-label="찾기·이동" title="찾기·이동 (Ctrl+K)">${icon('<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>')}</button>
        <button class="icon-btn" data-act="shortcuts" aria-label="단축키" title="단축키 (?)">${icon(P.help)}</button>
        <button class="icon-btn" data-act="refresh" aria-label="새로 고침" title="새로 고침 (R)">${icon(P.refresh)}</button>`;
    }
    // [ADMUX-30] The open hub's tabs under the title: none for a hub of one tab, or on a detail with its back button.
    const hub = list.find((h) => h.key === here);
    const row = document.getElementById("hubtabs");
    if (row) {
      const show = Boolean(hub && hub.tabs.length > 1 && !S.route.id && L.TABS[S.route.name]);
      row.hidden = !show;
      row.setAttribute("aria-label", hub ? `${hub.label} 안 화면` : "화면");
      row.innerHTML = show ? hub.tabs.map((t) => `<a href="${href(t)}" data-tab="${t}"${t === S.route.name ? ' aria-current="page"' : ""}>${esc(tabName(t))}<i class="badge" data-badge="tab:${t}"></i></a>`).join("") : "";
      row.querySelector("[aria-current]")?.scrollIntoView?.({ block: "nearest", inline: "nearest" });
    }
    for (const el of document.querySelectorAll("[data-nav]")) {
      const k = el.dataset.nav;
      const lit = el.classList.contains("tab") ? k === bottom.lit : k === here;
      if (lit) el.setAttribute("aria-current", "page");
      else el.removeAttribute("aria-current");
      const h = list.find((x) => x.key === k);
      el.setAttribute("href", k === "more" ? href("more") : h ? href(lastTab[k] && h.tabs.includes(lastTab[k]) ? lastTab[k] : h.tabs[0]) : href(k));
      el.closest("li")?.classList.toggle("open", lit && !el.classList.contains("tab"));
    }
    for (const el of document.querySelectorAll(".side [data-tab]")) {
      if (el.dataset.tab === S.route.name) el.setAttribute("aria-current", "page");
      else el.removeAttribute("aria-current");
      el.setAttribute("href", href(el.dataset.tab));
    }
    updateBadges(list, bottom);
    const left = (S.overview?.inquiries ?? 0) + (S.overview?.pending ?? 0);
    // [ADMX-11] A dot in the tab's title while something needs a look.
    document.title = `${S.alertsN ? "● " : ""}${left ? `(${left}) ` : ""}밤낮 관리자`;
  }
  // AN 1.28 끝

  /** Offline, answers from the phone's copy, a newer app, 큐 모드. */
  function paintBanners() {
    const banners = document.getElementById("banners");
    if (!banners) return;
    banners.innerHTML = `${S.online ? "" : '<div class="offline" role="status">인터넷 연결이 끊겼어요. 다시 연결되면 저절로 새로 고쳐요.</div>'}
      ${S.stale ? '<div class="banner warn" role="status">연결이 끊겨 이 기기에 저장된 내용을 보여요. 바꾸는 일은 연결된 뒤에 해 주세요.</div>' : ""}
      ${S.newBuild ? '<div class="banner" role="status">관리자 앱이 새로 나왔어요.<button class="btn small" data-act="reload">새로 열기</button></div>' : ""}`;
  }

  /**
   * [ADMUX-01] Draws the screen into the frame and keeps what the admin was doing: words typed in a box,
   * where the cursor was, which folds were open. Replies and notes also stay in the browser until sent.
   */
  function paintMain() {
    const main = document.getElementById("main");
    if (!main) return;
    const typed = new Map();
    for (const el of main.querySelectorAll("input[id],textarea[id],select[id]")) {
      if (el.type === "hidden" || el.type === "checkbox" || el.type === "radio" || el.readOnly) continue;
      const was = el.tagName === "SELECT" ? [...el.options].find((o) => o.defaultSelected)?.value ?? el.options[0]?.value : el.defaultValue;
      if (el.value !== was) typed.set(el.id, el.value);
    }
    // A <details> with data-key is remembered by that key (S.opened), the others by their summary's words.
    const keyOf = (d) => d.dataset.key || d.querySelector(":scope>summary")?.textContent.trim();
    const open = new Set([...main.querySelectorAll("details[open]")].map(keyOf));
    const shut = new Set([...main.querySelectorAll("details:not([open])")].map(keyOf));
    const focus = document.activeElement && main.contains(document.activeElement) ? document.activeElement : null;
    const at = focus?.id ? { id: focus.id, start: focus.selectionStart ?? null, end: focus.selectionEnd ?? null } : null;
    // AN 1.28 [ADMUX-31] '지금 기다리는 것' above a hub's first tab.
    main.innerHTML = waitingStrip() + view();
    rememberOpened();
    for (const id of KEEP) {
      const el = document.getElementById(id);
      if (el && !typed.has(id) && !el.value) el.value = readDraft(id);
    }
    for (const [id, value] of typed) {
      const el = document.getElementById(id);
      if (el && !el.readOnly) el.value = value;
    }
    for (const d of main.querySelectorAll("details")) {
      const t = keyOf(d);
      if (open.has(t)) d.open = true;
      else if (shut.has(t)) d.open = false;
    }
    document.getElementById("reply")?.dispatchEvent(new Event("input", { bubbles: true }));
    if (at) {
      const el = document.getElementById(at.id);
      if (el) {
        el.focus({ preventScroll: true });
        if (at.start !== null && typeof el.setSelectionRange === "function") {
          try {
            el.setSelectionRange(at.start, at.end);
          } catch {
            // Not a text box.
          }
        }
      }
    }
    if (S.cursor >= 0) markCursor();
  }

  /**
   * AN 1.28 [ADMUX-34] Every number in the frame from the last /badges: a hub's is what the admin must do there,
   * each thing once; a tab's is its own; 더 보기 adds only the hubs it holds. The '지금 기다리는 것' line follows.
   */
  function updateBadges(list = hubs(), bottom = L.bottomHubs(list, S.prefs.tabs, S.route.name)) {
    const o = S.overview ?? {};
    const rest = list.filter((h) => !bottom.tabs.includes(h.key)).reduce((n, h) => n + L.hubBadge(h, o, S.me), 0);
    for (const el of document.querySelectorAll("[data-badge]")) {
      const [kind, key] = el.dataset.badge.split(":");
      // AX 1.30 [ADMX-11] 오늘's number is how many things 주의 필요 lists (as of the last time 오늘 was opened).
      const n = kind === "hub" ? (key === "more" ? rest : key === "today" ? S.alertsN : L.hubBadge(key, o, S.me)) : kind === "tab" ? L.tabBadge(key, o, S.me) : 0;
      el.textContent = n || "";
    }
    const strip = document.getElementById("waiting");
    if (strip) strip.innerHTML = waitingItems(strip.dataset.hub);
    // [ADMUX-08] The installed app's icon shows how many questions wait.
    try {
      if (o.inquiries) navigator.setAppBadge?.(o.inquiries)?.catch?.(() => {});
      else navigator.clearAppBadge?.()?.catch?.(() => {});
    } catch {
      // Not offered here.
    }
  }

  /** [ADMUX-31] Where an item of '지금 기다리는 것' opens: the tab, already filtered to what waits. */
  function waitingLink(x) {
    if (x.tab === "inquiries") return L.routeHash("inquiries", null, { status: "open", sort: "sla" });
    if (x.tab === "dev") return L.routeHash("dev", null, { status: "todo" });
    if (x.tab === "community") return L.routeHash("community", null, { show: "flagged" });
    return href(x.tab);
  }

  function waitingItems(hubKey) {
    const list = L.waiting(hubKey, S.overview ?? {}, S.me);
    return `<b>지금 기다리는 것</b>${list.length ? list.map((x) => `<a href="${waitingLink(x)}">${esc(x.label)} <span class="n">${x.n}</span></a>`).join('<span class="dot" aria-hidden="true">·</span>')
      : '<span class="meta">없어요</span>'}`;
  }

  /** [ADMUX-31] The line above a hub's first tab; 오늘 draws its own inside the 할 일 card. */
  function waitingStrip() {
    const hub = hubNow();
    if (!hub || hub.key === "today" || S.route.id || hub.tabs[0] !== S.route.name || !hub.tabs.some((t) => L.TABS[t].badge)) return "";
    // 1.35 [ADMHE-01] On a phone the line is left out when all it says is this tab's own number (the tab already shows it).
    const only = L.waiting(hub.key, S.overview ?? {}, S.me).every((x) => x.tab === S.route.name);
    return `<p class="waiting${only ? " only-here" : ""}" id="waiting" data-hub="${hub.key}">${waitingItems(hub.key)}</p>`;
  }

  function heading(bottom) {
    const { name, id } = S.route;
    const phone = !matchMedia("(min-width:600px)").matches;
    if (name === "companies" && id) return { title: S.data.company?.name ?? "회사", sub: "회사·규칙", back: href("companies") };
    if (name === "inquiries" && id) {
      const q = S.data.inquiry;
      return { title: q ? KIND_NAMES[q.kind] ?? "문의" : "문의", sub: q ? `${STATUS_NAMES[q.status] ?? ""} · ${moment(q.createdAt)}` : "", back: href("inquiries") };
    }
    if (name === "dev" && id) {
      const d = S.data.dev;
      return { title: d && d.id === id ? d.title : "개발사항", sub: d && d.id === id ? `${DEV_NAMES[d.status]}${d.deletedAt ? " · 지움" : ""} · 문의 ${d.links}건` : "", back: href("dev") };
    }
    if (name === "community" && id) {
      const first = S.data.posts?.posts?.[0];
      return { title: first ? first.company.name : "토론", sub: first?.room ? (first.room === "style" ? "유형 광장 이야기" : "회사 정보 광장 자유 토의") : first ? `${first.dept || "회사 전체"} 규칙의 토론` : "규칙 토론", back: href("community") };
    }
    if (name === "flags" && id) return flagHeading();
    if (name === "more") return { title: "더 보기" };
    // [ADMUX-32] #/settings is now the list of where its cards went.
    if (name === "settings") return { title: "설정은 자리를 옮겼어요", back: href("account") };
    const hub = hubNow();
    // [ADMUX-30] The hub is the title; its tabs are the row under it (a hub of one tab says the tab's name instead).
    const title = hub ? (hub.tabs.length > 1 ? hub.label : tabName(name)) : tabName(name);
    // [ADMUX-03] A hub that is not one of the bottom tabs has a way back to 더 보기 on a phone.
    return { title: name === "home" ? "오늘" : title, sub: hub && hub.tabs.length > 1 && !phone ? tabName(name) : "", back: phone && bottom?.back ? href("more") : null };
  }

  /**
   * AN 1.28 [ADMUX-40] Every tab's screen in one table. 기능 관리·근무 유형 are AM's (flagsView, flagView, typesView);
   * 기능별 효과 (effectsView) reads AM's flag numbers through /effects (1.28 통합).
   */
  const VIEWS = {
    home: () => V.homeView(),
    inquiries: (id) => (id ? inquiryView() : inquiriesView()),
    dev: (id) => (id ? devView() : devListView()),
    sort: () => sortView(),
    templates: () => templatesView(),
    sla: () => slaView(),
    review: () => V.reviewView(),
    versions: () => V.versionsView(),
    community: () => communityView(),
    companies: (id) => (id ? companyView(S.data.company) : V.companiesView()),
    shares: () => sharesView(),
    recipes: () => recipesView(),
    adopt: () => adoptView(),
    flags: (id) => (id ? flagView() : flagsView()),
    types: () => typesView(),
    notices: () => noticesView(),
    chores: () => choresView(),
    gates: () => V.gatesView(),
    metrics: () => V.metricsView(),
    effects: () => effectsView(),
    audit: () => auditView(),
    admins: () => adminsView(),
    rewards: () => rewardsView(),
    server: () => serverView(),
    account: () => accountView(),
    settings: () => movedView(),
    more: () => moreView(),
  };

  function view() {
    const { name, id } = S.route;
    // [ADMUX-01] The data did not come: say so, with a way to try again, instead of a skeleton for ever.
    if (S.failed === routeKey() && !hasData()) {
      return `<div class="failed" role="alert">${icon(P.refresh)}<h2>불러오지 못했어요</h2><p>${S.online ? "서버가 잠시 답하지 않아요." : "인터넷 연결을 확인해 주세요."}</p>
        <button class="btn primary" data-act="refresh">다시 불러오기</button></div>`;
    }
    return VIEWS[name]?.(id) ?? "";
  }

  /** Whether the current screen has anything to show yet. */
  function hasData() {
    const { name, id } = S.route;
    const d = S.data;
    return Boolean({
      home: d.today, review: d.queue, inquiries: id ? d.inquiry?.id === id : d.inquiries, dev: id ? d.dev?.id === id : d.devList, sort: d.sort, templates: S.templates,
      companies: id ? d.company?.id === id : d.list, community: d.posts || d.rates, rewards: d.rewards || d.inviteSettings, admins: d.admins, audit: d.audit,
      notices: d.notices, chores: d.ops, flags: id ? d.flag?.key === id : d.flags, metrics: d.metrics, gates: d.gates, versions: d.versions, shares: d.shares, recipes: d.recipes,
      server: S.server, adopt: S.basics, sla: S.basics, types: d.types, effects: d.effects, settings: true, account: true, more: true,
    }[name]);
  }

  const empty = (title, text, action = "") => `<div class="empty">${icon(P.moon)}<h2>${esc(title)}</h2>${text ? `<p>${esc(text)}</p>` : ""}${action}</div>`;
  const skeleton = (n = 2) => Array.from({ length: n }, () => '<div class="card skeleton"></div>').join("");
  const lines = (list) => `<dl class="lines">${list.map(([label, text]) => `<div><dt>${esc(label)}</dt><dd>${esc(text)}</dd></div>`).join("")}</dl>`;
  const seg = (act, key, current, label, n) =>
    `<button class="seg" data-act="${act}" data-key="${key}" aria-pressed="${current === key}">${esc(label)}${n ? `<span class="n">${n}</span>` : ""}</button>`;
  /** [ADMIN-16] 더 보기 under a paged list. */
  const moreButton = (next, act, total, shown) => (next ? `<div class="more"><button class="btn" data-act="${act}">더 보기${total ? ` <span class="meta">${shown}/${total}</span>` : ""}</button></div>` : "");

  function permPills(perms, areas) {
    const on = Object.entries(perms ?? {}).filter(([, l]) => l !== "none");
    if (!on.length) return '<span class="pill">권한 없음</span>';
    return on.map(([a, l]) => `<span class="pill ${l === "edit" ? "info" : ""}">${esc(areas[a] ?? a)}${toggles().includes(a) ? "" : ` ${LEVEL_NAMES[l]}`}</span>`).join("");
  }

  // ---- the talk on rule sets [COMM-06] ----

  function communityView() {
    if (S.postFilter === "rates" && !S.route.id) return ratesView();
    const d = S.data.posts;
    const edit = can("community", "edit");
    const talk = Boolean(S.route.id);
    const head = talk ? `<p class="hint">이 규칙에 남긴 글 전부예요. 가려진 글도 보여요.${edit ? "" : " 보기 권한이라 가리거나 지울 수 없어요."}</p>`
      : `<div class="segmented" role="group" aria-label="보기">${seg("post-filter", "flagged", S.postFilter, "신고됨", S.overview?.flaggedPosts)}
        ${seg("post-filter", "hidden", S.postFilter, "가려짐")}${seg("post-filter", "all", S.postFilter, "모든 글")}${seg("post-filter", "room", S.postFilter, "회사·유형 광장")}${seg("post-filter", "rates", S.postFilter, "임금표")}</div>
        <div class="toolbar"><input id="search" type="search" placeholder="회사 이름으로 찾기" value="${esc(S.postQuery)}" autocomplete="off" enterkeyhint="search" aria-label="토론 찾기"></div>
        <p class="hint">앱 사용자가 회사 규칙마다 남긴 글이에요. 서로 다른 세 분이 신고하면 개발자가 볼 때까지 가려져요. 다시 보이게 한 글은 신고로 가려지지 않아요.${edit ? "" : " 보기 권한이라 가리거나 지울 수 없어요."}</p>`;
    if (!d) return head + skeleton();
    if (!d.posts.length) {
      return head + empty(S.postFilter === "flagged" && !talk ? "신고된 글이 없어요" : "글이 없어요", "신고가 오면 여기와 메뉴에 숫자로 보여요.");
    }
    // [ADMUX-19] 여러 개 고르기: the same three steps as on one post, for every picked one.
    const picking = Boolean(S.postPick) && edit;
    const n = picking ? S.postPick.size : 0;
    const tools = edit ? `<div class="toolbar end"><button class="btn" data-act="post-pick" aria-pressed="${picking}">${picking ? "선택 마침" : `${icon(P.check)}여러 개 고르기`}</button></div>` : "";
    const bar = picking ? `<div class="bulkbar" role="toolbar" aria-label="고른 글"><b>${n}개 골랐어요</b>
        <button class="btn small ghost" data-act="post-pick-all">${n === d.posts.length ? "모두 풀기" : "보이는 것 모두 고르기"}</button>
        ${[["show", "문제없음·다시 보이기"], ["hide", "가리기"], ["delete", "지우기"]].map(([k, label]) =>
          `<button class="btn small${k === "delete" ? " danger" : k === "show" ? " primary" : ""}" data-act="post-bulk" data-key="${k}"${n ? "" : " disabled"}>${label}</button>`).join("")}</div>` : "";
    return head + tools + `<div class="grid">${d.posts.map((p) => postCard(p, edit, talk, picking)).join("")}</div>${moreButton(d.next, "posts-more")}${bar}`;
  }

  /**
   * [PLZ-14] 임금표: market rates people sent. 확인 필요 is one far from the adopted ones that enough people
   * agreed to; an admin adopts it or hides it. Taking an adopted one down needs 광장 규칙 내리기.
   */
  function ratesView() {
    const d = S.data.rates;
    const edit = can("community", "edit");
    const n = d?.counts ?? {};
    const head = `<div class="segmented" role="group" aria-label="보기">${seg("post-filter", "flagged", S.postFilter, "신고됨", S.overview?.flaggedPosts)}
        ${seg("post-filter", "hidden", S.postFilter, "가려짐")}${seg("post-filter", "all", S.postFilter, "모든 글")}${seg("post-filter", "room", S.postFilter, "회사·유형 광장")}${seg("post-filter", "rates", S.postFilter, "임금표")}</div>
      <div class="chips" role="group" aria-label="임금표 상태">${[["review", "확인 필요"], ["pending", "투표 중"], ["adopted", "채택"], ["hidden", "가려짐"]]
        .map(([k, label]) => `<button type="button" class="chip pick" data-act="rate-filter" data-key="${k}" aria-pressed="${S.rateFilter === k}">${label}${n[k] ? ` ${n[k]}` : ""}</button>`).join("")}</div>
      <div class="toolbar"><input id="search" type="search" placeholder="직종으로 찾기" value="${esc(S.postQuery)}" autocomplete="off" enterkeyhint="search" aria-label="임금표 찾기"></div>
      <p class="hint">직군·지역별 일당·시급·월급 시세예요. 서로 다른 분들이 맞아요라고 하면 채택되고, 이미 채택된 시세와 두 배 넘게 차이 나면 여기서 확인을 기다려요.</p>`;
    if (!d) return head + skeleton();
    if (!d.rates.length) return head + empty("시세가 없어요", "앱에서 임금표에 시세를 올리면 여기에 보여요.");
    const won = (v) => `${Number(v).toLocaleString("ko-KR")}원`;
    // [ADMUX-19] 임금표 여러 개: 채택·가리기·투표로 되돌리기 at once (an adopted one only with 광장 규칙 내리기).
    const picking = Boolean(S.ratePick) && edit;
    const picked = picking ? S.ratePick.size : 0;
    const tools = edit ? `<div class="toolbar end"><button class="btn" data-act="rate-pick" aria-pressed="${picking}">${picking ? "선택 마침" : `${icon(P.check)}여러 개 고르기`}</button></div>` : "";
    const bar = picking ? `<div class="bulkbar" role="toolbar" aria-label="고른 시세"><b>${picked}개 골랐어요</b>
        <button class="btn small ghost" data-act="rate-pick-all">${picked === d.rates.length ? "모두 풀기" : "보이는 것 모두 고르기"}</button>
        ${[["adopted", "채택"], ["hidden", "가리기"], ["pending", "투표로 되돌리기"]].map(([k, label]) =>
          `<button class="btn small${k === "adopted" ? " primary" : k === "hidden" ? " danger" : ""}" data-act="rate-bulk" data-key="${k}"${picked ? "" : " disabled"}>${label}</button>`).join("")}</div>` : "";
    return head + tools + `<div class="grid">${d.rates.map((r) => `<article class="card${picking && S.ratePick.has(r.id) ? " picked" : ""}">
      <div class="head"><h3>${picking ? `<input class="check" type="checkbox" data-pick-rate="${r.id}"${S.ratePick.has(r.id) ? " checked" : ""} aria-label="이 시세 고르기"> ` : ""}${esc(r.trade)} <span class="dept">${esc(r.unitName)} ${esc(won(r.amount))}</span></h3><span class="pill">${esc(r.year)}년</span></div>
      <p class="meta">${esc(r.jobName)} · ${esc(r.regionName)} · 맞아요 ${r.agree} · 달라요 ${r.disagree} · 글쓴이 ${esc(r.writer)} · ${esc(ago(r.at))}</p>
      ${r.note ? `<blockquote>${esc(r.note)}</blockquote>` : ""}
      ${edit ? `<div class="actions">
        ${r.status === "adopted" ? "" : `<button class="btn primary" data-act="rate-set" data-id="${r.id}" data-key="adopted">채택</button>`}
        ${r.status === "adopted" && !can("takedown", "edit") ? "" : `${r.status === "hidden" ? "" : `<button class="btn danger" data-act="rate-set" data-id="${r.id}" data-key="hidden">가리기</button>`}
          ${r.status === "pending" ? "" : `<button class="btn" data-act="rate-set" data-id="${r.id}" data-key="pending">투표로 되돌리기</button>`}`}
      </div>` : ""}
    </article>`).join("")}</div>${moreButton(d.next, "rates-more")}${bar}`;
  }

  function postCard(p, edit, talk, picking = false) {
    const state = p.status === "hidden" ? `<span class="pill bad">가려짐</span>` : p.flags ? `<span class="pill warn">신고 ${p.flags}</span>`
      : p.kept ? '<span class="pill ok">확인함</span>' : "";
    const on = picking && S.postPick.has(p.id);
    return `<article class="card${on ? " picked" : ""}">
      <div class="head"><h3>${picking ? `<input class="check" type="checkbox" data-pick-post="${p.id}"${on ? " checked" : ""} aria-label="이 글 고르기"> ` : ""}${esc(p.company.name)}${p.room ? ` <span class="dept">${p.room === "style" ? "유형 광장" : "회사 정보 광장"}</span>` : p.dept ? ` <span class="dept">${esc(p.dept)}</span>` : ""}</h3>${state}</div>
      <p class="meta">글쓴이 ${esc(p.writer)} · ${esc(ago(p.at))}${p.mutedUntil ? ` · ${esc(day(p.mutedUntil))}까지 쓰기 막힘` : ""}</p>
      <blockquote>${esc(p.body)}</blockquote>
      ${p.reasons.length ? `<p class="meta">신고 이유: ${p.reasons.map((r) => `${esc(r.text)} ${r.n}`).join(", ")}</p>` : ""}
      ${p.hidden ? `<p class="note warn">${esc(p.hidden.text)}</p>` : ""}
      <div class="actions">
        ${talk ? "" : `<a class="btn ghost" href="${href("community", `${p.company.id}.${p.setup}`)}">${p.room ? "이 광장 글 전부" : "이 토론 전부"}</a>`}
        ${edit ? `${p.status === "hidden" || p.flags ? `<button class="btn" data-act="post-show" data-id="${p.id}">${p.status === "hidden" ? "다시 보이기" : "문제없음"}</button>` : ""}
          ${p.status === "hidden" ? "" : `<button class="btn" data-act="post-hide" data-id="${p.id}">가리기</button>`}
          <button class="btn" data-act="post-mute" data-id="${p.id}">30일 쓰기 막기</button>
          <button class="btn danger" data-act="post-delete" data-id="${p.id}">지우기</button>` : ""}
      </div>
    </article>`;
  }

  function openHidePost(id, mute) {
    const reasons = Object.entries(S.overview?.hideReasons ?? {}).filter(([key]) => key !== "flags");
    openDialog(`<form data-form="hide-post" data-id="${id}" data-mute="${mute ? 1 : 0}"><h2>${mute ? "30일 동안 쓰기를 막을까요?" : "이 글을 가릴까요?"}</h2>
      <p>${mute ? "이 글은 가려지고, 글쓴이는 30일 동안 어느 토론에도 글을 쓸 수 없어요. " : ""}글쓴이 앱에 이 이유가 보여요.</p>
      <div class="radios">${reasons.map(([key, text], i) => `<label class="radio"><input type="radio" name="reason" value="${key}"${i === 0 ? " checked" : ""}>
        <span>${esc(text)}</span></label>`).join("")}</div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">${mute ? "막기" : "가리기"}</button></div></form>`);
  }

  async function moderate(id, body, done, undo) {
    await act(() => api(`/posts/${id}`, "POST", body), done, undo);
    load();
  }

  // ---- inquiries ----

  /** [ADMIN-02] The server sorts each question by topic; a chip shows one topic, with how many there are. */
  function topicChips(d) {
    const topics = d?.topics ?? {};
    const names = d?.topicNames ?? {};
    const keys = Object.keys(names).filter((k) => topics[k] || k === S.inquiryTopic);
    if (!keys.length) return "";
    const chip = (key, label, n) => `<button type="button" class="chip pick" data-act="inquiry-topic" data-key="${key}" aria-pressed="${S.inquiryTopic === key}">${esc(label)}${n ? ` ${n}` : ""}</button>`;
    return `<div class="chips" role="group" aria-label="주제">${chip("", "전체")}${keys.map((k) => chip(k, names[k], topics[k])).join("")}</div>`;
  }

  /** [ADMIN-02] 모두의 문의: whether others see it, and what they said: 나도 그래요 and 신고. */
  function publicPills(q) {
    if (!q.public) return "";
    return (q.hidden ? '<span class="pill bad">숨김</span>' : '<span class="pill info">공개</span>') +
      (q.same ? `<span class="pill">나도 ${q.same}</span>` : "") + (q.flags ? `<span class="pill warn">신고 ${q.flags}</span>` : "");
  }

  const SORTS = [["default", "기본(급한 것 먼저)"], ["sla", "답 목표가 가까운 순"], ["oldest", "오래 기다린 순"], ["newest", "최근 순"], ["same", "나도 그래요 많은 순"]];

  /**
   * AT 1.29 [ADMCP-03][ADMCP-04] 복사함 (who, when) on a copied question; 새 on a problem report from the last day that
   * nobody copied yet, so a new one stands out in the list.
   */
  function copyPill(q) {
    if (q.copied) return `<span class="pill ok" title="${esc(moment(q.copied.at))}">복사함${q.copied.by ? ` · ${esc(q.copied.by)}` : ""}</span>`;
    const fresh = q.kind === "bug" && Date.now() - Date.parse(q.createdAt) < 24 * 3600e3;
    return fresh ? '<span class="pill info">새</span>' : "";
  }

  /** One question in a list; [compact] for the list beside an open question. */
  function inquiryRow(q, { compact = false, picking = false } = {}) {
    const current = S.route.id === q.id;
    const inner = `<span class="row-main"><b>${q.urgency === "urgent" ? '<span class="pill bad">급함</span> ' : ""}${esc(KIND_NAMES[q.kind] ?? "문의")}${q.topicName ? ` · ${esc(q.topicName)}` : ""}${!compact && q.appVersion ? ` <span class="meta">앱 ${esc(q.appVersion)}</span>` : ""}</b>
        <span class="preview">${esc(q.preview)}</span>
        ${q.typing?.length ? `<span class="typing">${esc(q.typing.join(", "))}님이 답 쓰는 중</span>` : ""}</span>
      <span class="row-side"><span>${esc(ago(q.updatedAt ?? q.createdAt))}</span>${slaPill(q.slaLeft)}
        ${compact ? "" : `${q.count > 1 ? `<span class="pill">${q.count}</span>` : ""}${publicPills(q)}
        ${q.rating ? `<span class="pill ${q.rating > 0 ? "ok" : "bad"}">${q.rating > 0 ? "도움 됐어요" : "아쉬워요"}</span>` : ""}
        ${q.dev ? '<span class="pill ok">개발사항</span>' : ""}${q.notes ? `<span class="pill gold">메모 ${q.notes}</span>` : ""}${copyPill(q) /* AT 1.29 */}`}
        ${q.assignee ? `<span class="pill${q.assignee.id === S.me.id ? " info" : ""}">${q.assignee.id === S.me.id ? "내가 맡음" : esc(q.assignee.name)}</span>` : ""}</span>`;
    if (picking) {
      const on = S.inquiryPick.has(q.id);
      return `<li><label class="row${on ? " picked" : ""}" data-pick-row="${q.id}"><input class="check" type="checkbox" data-pick-inquiry="${q.id}"${on ? " checked" : ""} aria-label="고르기">${inner}</label></li>`;
    }
    const link = `<a class="row${q.waiting ? " unread" : ""}" href="${href("inquiries", q.id)}"${current ? ' aria-current="true"' : ""} data-row="${q.id}">${inner}${icon(P.chevron)}</a>`;
    // AT 1.29 [ADMCP-01] 복사 on the row itself, beside (not inside) its link; the narrow list beside a question has none.
    return compact ? `<li>${link}</li>` : `<li class="has-tool">${link}<button type="button" class="row-tool" data-act="inquiry-copy" data-id="${q.id}" aria-label="이 문의 복사" title="복사 (c)">${icon(P.copy)}</button></li>`;
  }

  function inquiriesView() {
    if (S.data.inquiriesMissing) {
      return empty("문의함이 아직 서버에 없어요", "앱 안 문의가 켜진 서버로 업데이트하면 여기서 바로 답할 수 있어요.");
    }
    const d = S.data.inquiries;
    const counts = d?.counts ?? {};
    const edit = can("inquiries", "edit");
    // AT 1.29 [ADMCP-02] Picking is for copying too, which seeing questions is enough for; the other steps need 고치기.
    const picking = Boolean(S.inquiryPick);
    const head = `<div class="segmented" role="group" aria-label="문의 상태">
      ${seg("inquiry-status", "open", S.inquiryStatus, "답 대기", counts.open)}${seg("inquiry-status", "answered", S.inquiryStatus, "답함", counts.answered)}
      ${seg("inquiry-status", "closed", S.inquiryStatus, "닫힘", counts.closed)}</div>
      <div class="toolbar fold${S.toolsOpen || picking ? " open" : ""}"><input id="search" type="search" placeholder="문의와 답에서 찾기" value="${esc(S.inquiryQuery)}" autocomplete="off" enterkeyhint="search" aria-label="문의 찾기">
        ${edit && S.inquiryStatus === "open" ? '<button class="btn primary" data-act="queue-start">큐 모드</button>' : ""}
        <button class="btn tools-toggle" data-act="tools-toggle" aria-expanded="${Boolean(S.toolsOpen || picking)}">${S.toolsOpen || picking ? "접기" : "정렬·더"}</button>
        <span class="tool-more"><label class="sr" for="inq-sort">정렬</label><select id="inq-sort" data-change="inquiry-sort">${SORTS.map(([k, label]) => `<option value="${k}"${S.inquirySort === k ? " selected" : ""}>${label}</option>`).join("")}</select>
        <button class="btn" data-act="inquiry-pick" aria-pressed="${picking}">${picking ? "선택 마침" : `${icon(P.check)}여러 개 고르기`}</button>
        ${picking ? "" : csvButton("inquiries", S.data.inquiries?.inquiries)}</span></div>
      <div class="filterbar"><div class="chips" role="group" aria-label="맡은 사람">${[["", "모두"], ["mine", "내가 맡은 것"], ["free", "아무도 안 맡은 것"], ["late", "놓친 것(하루 넘음)"]].map(([key, label]) =>
        `<button type="button" class="chip pick" data-act="inquiry-who" data-key="${key}" aria-pressed="${S.inquiryWho === key}">${label}</button>`).join("")}</div>${kindChips()}${topicChips(d)}</div>`;
    if (!d) return head + skeleton();
    const filtered = S.inquiryQuery || S.inquiryWho || S.inquiryTopic || S.inquiryKind || S.inquiryCopied || S.inquirySender;
    if (!d.inquiries.length) {
      return head + freshStrip(d) + empty(S.inquiryQuery ? "찾는 문의가 없어요" : S.inquiryCopied === "no" ? "복사 전인 문의가 없어요. 모두 개발에 넘겼어요" : S.inquiryWho === "late" ? "하루 넘게 기다리는 문의가 없어요. 잘하고 있어요" : S.inquiryWho === "mine" ? "내가 맡은 문의가 없어요" : S.inquiryStatus === "open" ? "답을 기다리는 문의가 없어요" : "목록이 비어 있어요",
        S.inquiryQuery ? "다른 말로 찾아보세요. 보낸 분과 관리자가 쓴 글, 앱 판·기기 이름, 문의 번호를 모두 찾아요." : S.inquiryWho === "mine" ? "문의를 열어 내가 맡기를 누르거나 답하면 여기에 모여요." : "앱에서 문의가 오면 여기와 메뉴에 숫자로 보여요.",
        filtered ? '<button class="btn" data-act="inquiry-clear">조건 지우기</button>'
          : can("inquiries", "edit") && S.inquiryStatus === "open" ? '<button class="btn" data-act="templates">한가할 때 자주 쓰는 답 정리하기</button>' : "");
    }
    const n = picking ? S.inquiryPick.size : 0;
    // AT 1.29 [ADMCP-02] The bar stays at the bottom of the screen; 선택 n개 복사 is its first button.
    const found = d.total ?? d.inquiries.length;
    const bar = picking ? `<div class="bulkbar${S.bulkMore ? " open" : ""}" role="toolbar" aria-label="고른 문의"><b>${n}개 골랐어요</b>
        <button class="btn small ghost" data-act="inquiry-pick-all">${n === d.inquiries.length ? "모두 풀기" : "보이는 것 모두 고르기"}</button>
        ${found > d.inquiries.length && n < Math.min(found, 100) ? `<button class="btn small ghost" data-act="inquiry-pick-found">찾은 ${Math.min(found, 100)}건 모두 고르기</button>` : ""}
        <button class="btn small primary" data-act="inquiry-copy-picked"${n ? "" : " disabled"}>${icon(P.copy)}선택 ${n}개 복사</button>
        ${edit ? `<button class="btn small ghost more-toggle" data-act="bulk-more" aria-expanded="${Boolean(S.bulkMore)}">${S.bulkMore ? "접기" : "다른 일"}</button>
        <span class="more-acts">${[["assign", "내가 맡기"], ["unassign", "맡기 취소"], ["reply", "같은 답 보내기"], ["close", "닫기"], ["open", "다시 열기"]].map(([k, label]) =>
          `<button class="btn small" data-act="inquiry-bulk" data-key="${k}"${n ? "" : " disabled"}>${label}</button>`).join("")}
        ${can("dev", "edit") ? `<button class="btn small" data-act="inquiry-bulk" data-key="link"${n ? "" : " disabled"}>개발사항에 연결</button>` : ""}</span>` : ""}</div>` : "";
    return head + freshStrip(d) + `<p class="meta">${found}건${d.sla ? ` · 답 목표: 문제 ${d.sla.slaBug}시간, 질문 ${d.sla.slaQuestion}시간, 제안 ${d.sla.slaIdea}시간` : ""}${picking ? "" : '<span class="kbd-hint"> · 길게 누르거나 Space로 고르고, Shift로 이어서 골라요</span>'}</p>
      <ul class="rows">${d.inquiries.map((q) => inquiryRow(q, { picking })).join("")}</ul>
      ${moreButton(d.next, "inquiries-more", d.total, d.inquiries.length)}${bar}`;
  }

  // AT 1.29 시작
  /** [ADMCP-02] 문제 신고만 and the other kinds, 복사 전만, and the one phone being looked at (같은 기기). */
  function kindChips() {
    const kinds = [["", "모든 종류"], ["bug", "문제 신고"], ["question", "질문"], ["idea", "건의·제안"]];
    return `<div class="chips" role="group" aria-label="종류와 복사">${kinds.map(([key, label]) =>
      `<button type="button" class="chip pick" data-act="inquiry-kind" data-key="${key}" aria-pressed="${S.inquiryKind === key}">${label}</button>`).join("")}
      <button type="button" class="chip pick" data-act="inquiry-copied" data-key="no" aria-pressed="${S.inquiryCopied === "no"}">복사 전만</button>
      ${S.inquirySender ? `<button type="button" class="chip pick" data-act="inquiry-sender" data-key="" aria-pressed="true">같은 기기 #${esc(S.inquirySender)} ✕</button>` : ""}
      ${S.inquiryKind || S.inquiryCopied || S.inquirySender ? '<button type="button" class="chip pick" data-act="inquiry-clear">조건 지우기</button>' : ""}</div>`;
  }

  /**
   * [ADMCP-04] New problem reports stand out: how many wait to be copied (today's among them), with one tap to see
   * just those and one to copy them all. Hidden while the list already shows exactly that.
   */
  function freshStrip(d) {
    const f = d?.fresh;
    if (!f?.bugs || (S.inquiryKind === "bug" && S.inquiryCopied === "no")) return "";
    return `<div class="fresh" role="status"><span>복사 전 문제 신고 <b>${f.bugs}</b>건${f.today ? ` · 오늘 <b>${f.today}</b>건` : ""}</span>
      <button class="btn small" data-act="fresh-bugs">모아 보기</button><button class="btn small primary" data-act="fresh-copy">${icon(P.copy)}모두 복사</button></div>`;
  }
  // AT 1.29 끝

  /** [ADMIN-16] Where this question is in the list the admin came from: the ones before and after it. */
  function neighbours() {
    const list = S.data.inquiries?.inquiries ?? [];
    const i = list.findIndex((x) => x.id === S.route.id);
    return i < 0 ? { prev: null, next: null, at: -1, of: list.length } : { prev: list[i - 1]?.id ?? null, next: list[i + 1]?.id ?? null, at: i, of: list.length };
  }

  /** [ADMIN-17] What happened to a question, oldest first: the messages, admins' steps and notes together. */
  function timeline(q) {
    const items = [
      ...q.messages.map((m) => ({ at: m.at, cls: m.from === "user" ? "user" : "admin", text: m.from === "user" ? "보낸 분이 글을 남겼어요" : `${m.by && m.by !== "admin" ? m.by : "개발자"}님이 답했어요` })),
      ...(q.events ?? []).filter((e) => e.action !== "inquiry_reply").map((e) => ({ at: e.at, cls: "", text: eventText(e) })),
      ...(q.notes ?? []).map((n) => ({ at: n.at, cls: "note", text: `${n.by}님이 메모를 남겼어요` })),
    ].sort((a, b) => String(a.at).localeCompare(String(b.at)));
    return `<ul class="timeline">${items.map((x) => `<li class="${x.cls}">${esc(x.text)}<span class="when">${esc(moment(x.at))}</span></li>`).join("")}</ul>`;
  }

  function eventText(e) {
    const by = e.by || "관리자";
    if (e.action === "inquiry_assign") return e.detail ? `${e.detail}님이 맡았어요` : `${by}님이 맡기를 취소했어요`;
    if (e.action === "inquiry_status") return `${by}님이 ${e.detail === "closed" ? "닫았어요" : e.detail === "open" ? "다시 열었어요" : `상태를 ${STATUS_NAMES[e.detail] ?? e.detail}(으)로 바꿨어요`}`;
    if (e.action === "inquiry_hide") return `${by}님이 모두의 문의에서 숨겼어요`;
    if (e.action === "inquiry_show") return `${by}님이 다시 보이게 했어요`;
    if (e.action === "dev_link") return `${by}님이 개발사항 "${e.detail}"에 연결했어요`;
    return `${by} · ${ACTION_NAMES[e.action] ?? e.action}`;
  }

  function inquiryView() {
    const q = S.data.inquiry;
    const detail = !q || q.id !== S.route.id ? skeleton(2) : inquiryDetail(q);
    // [ADMIN-16] On a wide screen the list stays beside the open question.
    const list = S.data.inquiries?.inquiries;
    if (!wide() || !list?.length) return detail;
    return `<div class="split"><nav class="pane-list card" aria-label="문의 목록"><ul class="rows">${list.map((x) => inquiryRow(x, { compact: true })).join("")}</ul>
      ${moreButton(S.data.inquiries.next, "inquiries-more", S.data.inquiries.total, list.length)}</nav><div class="pane-main">${detail}</div></div>`;
  }

  function inquiryDetail(q) {
    const editable = can("inquiries", "edit");
    const ctx = Object.entries(q.context ?? q.info ?? {}).filter(([, v]) => v !== null && v !== undefined && v !== "");
    const thread = q.messages.map((m) => {
      const mine = m.from !== "user";
      return `<div class="bubble ${mine ? "admin" : "user"}">${esc(m.text)}</div>
        <span class="bubble-meta ${mine ? "admin" : ""}">${mine ? esc(m.by && m.by !== "admin" ? m.by : "개발자") : "보낸 분"} · ${esc(moment(m.at))}</span>`;
    }).join("");
    const status = { open: ["warn", "답 대기"], answered: ["ok", "답함"], closed: ["", "닫힘"] }[q.status] ?? ["", q.status];
    // [INQ-02] What the asker's app shows as the stage, so the admin answers with it in mind.
    const stage = PROGRESS_NAMES[q.progress?.stage] ?? "";
    const nb = neighbours();
    const step = nb.at >= 0 ? `<div class="stepnav"><a class="btn small ghost${nb.prev ? "" : " disabled"}" ${nb.prev ? `href="${href("inquiries", nb.prev)}"` : 'aria-disabled="true"'} title="이전 ([)">‹ 이전</a>
      <span class="meta">${nb.at + 1}/${nb.of}</span><a class="btn small ghost${nb.next ? "" : " disabled"}" ${nb.next ? `href="${href("inquiries", nb.next)}"` : 'aria-disabled="true"'} title="다음 (])">다음 ›</a></div>` : "";
    const list = templateList();
    const queueBar = S.queue ? `<div class="queue-bar" role="status"><b>큐 모드 · ${S.queue.at + 1}/${S.queue.ids.length}</b>
      <span>Ctrl+Enter 보내고 다음 · s 건너뛰기 · e 닫고 다음</span><button class="btn small" data-act="queue-skip">건너뛰기</button><button class="btn small ghost" data-act="queue-stop">끝내기</button></div>` : "";
    return `${queueBar}${step}<div class="cols">
      <section>
        <div class="card"><div class="card-head"><h2>${q.urgency === "urgent" ? '<span class="pill bad">급함</span> ' : ""}${esc(KIND_NAMES[q.kind] ?? "문의")}${q.topicName ? ` · ${esc(q.topicName)}` : ""}</h2><span class="pill ${status[0]}">${status[1]}</span>
          <button class="btn small" data-act="inquiry-copy" data-id="${q.id}" title="개발할 때 붙여 넣을 글로 복사 (c)">${icon(P.copy)}복사</button></div>
          <p class="meta">보낸 분 앱에는 <b>${esc(stage)}</b>${q.progress?.version ? ` (${esc(q.progress.version)})` : ""}${roSuffix(stage)} 보여요.${APP_KIND_NAMES[q.kind] ? ` 앱에서 고른 종류: ${esc(APP_KIND_NAMES[q.kind])}.` : ""} ${waitPill(q)}${slaPill(q.slaLeft)}${q.rating ? ` <span class="pill ${q.rating > 0 ? "ok" : "bad"}">${q.rating > 0 ? "답이 도움 됐대요" : "답이 아쉬웠대요"}</span>` : ""}</p>
          <div class="thread">${thread}</div>
          ${q.photos ? `<div class="photos">${Array.from({ length: q.photos }, (_, i) =>
            `<a href="/api/admin/inquiries/${esc(q.id)}/photos/${i + 1}" target="_blank" rel="noopener"><img src="/api/admin/inquiries/${esc(q.id)}/photos/${i + 1}" alt="보낸 분이 붙인 사진 ${i + 1}" loading="lazy"></a>`).join("")}</div>
            <p class="meta">사진은 위치 정보를 지운 채로 받았어요. 문의를 닫고 90일 뒤에 지워져요.</p>` : ""}</div>
        ${q.typing?.length ? `<p class="typing" role="status">${esc(q.typing.join(", "))}님이 지금 답을 쓰고 있어요. 겹치지 않게 확인해 주세요.</p>` : ""}
        ${editable && q.status !== "closed" ? `<form class="composer" data-form="reply">
          <label class="sr" for="reply">답</label>
          <textarea id="reply" name="text" maxlength="2000" placeholder="${q.public ? "보낸 분 앱과 모두의 문의에" : "보낸 분 앱에"} 보일 답을 적어요. 이름, 연락처, 금액은 묻지 않아요." required></textarea>
          <input type="hidden" name="template" value="">
          <div class="chips" role="group" aria-label="자주 쓰는 답">${list.map((t, i) => `<button type="button" class="chip pick" data-act="template" data-i="${i}" title="${esc(t.text.slice(0, 120))}">${i < 9 ? `<span class="kbd">${i + 1}</span> ` : ""}${esc(t.label)}</button>`).join("")}
            <button type="button" class="chip pick" data-act="templates">${icon(P.plus)}답 관리</button></div>
          <p class="saved" id="reply-saved" aria-live="polite"></p>
          <div class="actions"><span class="count" id="reply-count">0/2000</span>
            <button class="btn" type="submit" data-close="1">답하고 닫기</button><button class="btn primary" type="submit">답하기</button></div>
        </form>` : ""}
      </section>
      <aside>
        ${editable ? `<section class="card"><h2>상태</h2>
          <p class="meta">${q.assignee ? (q.assignee.id === S.me.id ? "내가 맡았어요." : `${esc(q.assignee.name)}님이 맡았어요.`) : "아직 아무도 맡지 않았어요. 답하면 내가 맡아요."}</p>
          <div class="actions start">${q.assignee?.id === S.me.id ? '<button class="btn" data-act="inquiry-assign" data-to="">맡기 취소</button>'
            : `<button class="btn" data-act="inquiry-assign" data-to="me">${q.assignee ? "내가 넘겨받기" : "내가 맡기"}</button>`}
            ${q.status === "closed" ? '<button class="btn" data-act="inquiry-state" data-status="open">다시 열기</button>'
            : '<button class="btn" data-act="inquiry-state" data-status="closed">닫기</button>'}</div></section>` : ""}
        ${copiedCard(q) /* AT 1.29 */}
        <section class="card"><h2>함께 온 정보</h2><p class="hint">보낸 분이 보내기 전에 보고 동의한 정보예요.</p>
          ${ctx.length ? `<dl class="kv">${ctx.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(typeof v === "object" ? JSON.stringify(v) : v)}</dd>`).join("")}</dl>` : '<p class="meta">없어요</p>'}</section>
        ${q.public ? `<section class="card"><h2>모두의 문의</h2>
          <p class="hint">${q.hidden ? `숨겼어요: ${esc(q.hidden.text)}. 보낸 분 앱에는 이유가 보여요.` : "앱을 쓰는 모두가 이 문의와 답을 봐요. 함께 온 정보는 개발자만 봐요."}</p>
          <div class="chips">${publicPills(q)}${(q.reasons ?? []).map((r) => `<span class="chip">${esc(r.text)} ${r.n}</span>`).join("")}</div>
          ${editable ? `<div class="actions start">${q.hidden ? '<button class="btn" data-act="inquiry-show">다시 보이기</button>'
            : `${[["abuse", "욕설·비방"], ["personal", "개인 정보"], ["spam", "광고·도배"]].map(([code, label]) =>
              `<button class="btn" data-act="inquiry-hide" data-reason="${code}">숨기기: ${label}</button>`).join("")}${q.flags && !q.kept ? '<button class="btn" data-act="inquiry-show">괜찮아요</button>' : ""}`}</div>` : ""}</section>` : ""}
        ${q.dev ? devCard(q) : ""}
        <section class="card"><h2>관리자 메모</h2><p class="hint">관리자끼리만 봐요. 보낸 분 앱에는 보이지 않아요.</p>
          ${q.notes?.length ? `<ul class="notes">${q.notes.map((n) => `<li><cite>${esc(n.by)} · ${esc(ago(n.at))}</cite>${esc(n.text)}</li>`).join("")}</ul>` : ""}
          ${editable ? `<form class="mini" data-form="inquiry-note"><label class="sr" for="inq-note">메모</label>
            <textarea id="inq-note" name="text" maxlength="1000" placeholder="예: 다음 버전에서 고칠 예정, 비슷한 문의 많음" required></textarea>
            <div class="actions"><button class="btn small" type="submit">메모 남기기</button></div></form>` : ""}</section>
        <details class="card"><summary>기록</summary>${timeline(q)}</details>
      </aside></div>`;
  }

  /**
   * AT 1.29 [ADMCP-03][ADMCP-06] On a question: whether it was copied for development (who, when), and the other
   * questions the same phone sent, one tap away.
   */
  function copiedCard(q) {
    const same = q.sameSender ? `<p class="meta">이 기기(#${esc(q.sender)})에서 온 다른 문의 ${q.sameSender}건
      <button class="btn small ghost" data-act="inquiry-sender" data-key="${esc(q.sender)}">모아 보기</button></p>` : "";
    return `<section class="card"><h2>개발로 복사</h2>
      <p class="meta">${q.copied ? `${esc(q.copied.by || "관리자")}님이 ${esc(moment(q.copied.at))}에 복사했어요.` : "아직 아무도 복사하지 않았어요."}</p>${same}</section>`;
  }

  /**
   * [ADMIN-11] On a question: the 개발사항 it is in, and one tap to add it to one (new, or one that exists).
   * Shown to whoever may see 개발사항; adding takes changing them.
   */
  function devCard(q) {
    const add = can("dev", "edit");
    return `<section class="card"><div class="card-head"><h2>개발사항</h2>${q.dev.length ? `<span class="pill ok">${q.dev.length}</span>` : ""}</div>
      ${q.dev.length ? `<ul class="rows">${q.dev.map((d) => `<li><a class="row" href="${href("dev", d.id)}"><span class="row-main"><b>${esc(d.title)}</b>
        <small>문의 ${d.links}건</small></span><span class="pill ${DEV_TONES[d.status]}">${DEV_NAMES[d.status]}</span>${icon(P.chevron)}</a></li>`).join("")}</ul>`
        : '<p class="hint">개발이 필요한 문의면 개발사항에 올려 두세요. 누가 올렸는지 남고, 같은 요청이 몇 건인지 모여요.</p>'}
      ${add ? `<div class="actions start"><button class="btn${q.dev.length ? "" : " primary"}" data-act="dev-from-inquiry">${icon(P.plus)}개발사항에 추가</button></div>` : ""}</section>`;
  }

  const firstText = (q) => q.messages.find((m) => m.from === "user")?.text ?? "";

  /**
   * [ADMIN-11] The 개발사항 stays as an improvement record after the question goes (privacy policy 2.4), so
   * the text copied from a question opens with this; the line itself is dropped when the item is added.
   */
  const SCRUB = "※ 개인을 알 수 있는 내용은 지우고 요약해 주세요";

  /** [ADMIN-11] 개발사항에 추가: a new item filled in from the question, or the question joined to one that exists. */
  async function openDevFromInquiry(mode = "new") {
    const q = S.data.inquiry;
    const text = firstText(q);
    const line = text.split("\n").find((l) => l.trim())?.trim() ?? "";
    const title = [...line].length > 50 ? `${[...line].slice(0, 50).join("")}…` : line;
    const body = `${SCRUB}\n\n${text}\n\n— ${KIND_NAMES[q.kind] ?? "문의"}${q.topicName ? ` · ${q.topicName}` : ""}${q.appVersion ? ` · 앱 ${q.appVersion}` : ""}`;
    const tabs = `<div class="segmented" role="group" aria-label="추가 방법">${seg("dev-add-mode", "new", mode, "새로 만들기")}${seg("dev-add-mode", "attach", mode, "있는 항목에 붙이기")}</div>`;
    if (mode === "new") {
      openDialog(`<form data-form="dev-new" data-inquiry="${q.id}"><h2>개발사항에 추가</h2>${tabs}
        <p>문의 내용으로 채워 두었어요. 개발사항은 문의가 지워져도 개선 기록으로 남으니, 이름·연락처·회사처럼 개인을 알 수 있는 내용은 지우고 요약해 주세요. 추가한 사람으로 내 이름이 남아요.</p>
        <div class="fieldset"><label for="dev-title">제목</label><input id="dev-title" name="title" maxlength="100" required value="${esc(title)}"></div>
        <div class="fieldset"><label for="dev-body">내용</label><textarea id="dev-body" name="body" maxlength="4000">${esc(body)}</textarea></div>
        <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">추가</button></div></form>`, "wide");
      return;
    }
    openDialog(`<div class="box"><h2>있는 항목에 붙이기</h2>${tabs}
      <p>비슷한 항목이 위에 있어요. 붙이면 그 항목의 요청 수가 늘어요.</p>
      <input id="dev-find" type="search" placeholder="제목으로 찾기" autocomplete="off" aria-label="개발사항 찾기">
      <div id="dev-found"><div class="card skeleton short"></div></div>
      <div class="actions"><button class="btn" data-act="close">닫기</button></div></div>`, "wide");
    try {
      const d = await api("/dev?status=open");
      S.devCandidates = L.likeFirst(d.items.filter((x) => !(q.dev ?? []).some((y) => y.id === x.id)), text);
    } catch (e) {
      fail(e);
      S.devCandidates = [];
    }
    showCandidates("");
  }

  function showCandidates(find) {
    const box = document.getElementById("dev-found");
    if (!box) return;
    const f = find.trim().toLowerCase();
    const list = (S.devCandidates ?? []).filter((d) => !f || d.title.toLowerCase().includes(f) || (d.body ?? "").toLowerCase().includes(f)).slice(0, 20);
    box.innerHTML = list.length ? `<ul class="rows">${list.map((d) => `<li><button class="row" data-act="dev-attach" data-id="${d.id}">
      <span class="row-main"><b>${esc(d.title)}</b><small>${DEV_NAMES[d.status]} · 문의 ${d.links}건${d.like ? " · 비슷해요" : ""}</small></span>
      <span class="pill">붙이기</span></button></li>`).join("")}</ul>`
      : '<p class="meta">붙일 항목이 없어요. 새로 만들기를 눌러 주세요.</p>';
  }

  // ---- 개발사항 [ADMIN-10][ADMIN-11] ----

  // ---- 1.35 문의 정리함 [INBOX-01]~[INBOX-07] (사용자 2026-10-30 "올인원 관리 체계") ----
  // Every waiting question already sorted by the server into what it is (이미 고침, 개발 목록에 있음, 전에 답한 것과 같음, 자주
  // 쓰는 답, 새 문제 …) with its answer written; one tap does a card, one tap does a whole pile. Above it, 자동 대응 (owner)
  // and Claude에게 넘기기: the 개발사항 not handed over yet as one text, and 개발 결과 붙여 넣기 for what comes back.

  const PILE_HELP = {
    fixed: ["이미 고친 문제예요", "고친 버전보다 오래된 앱에서 온 문의예요. 업데이트 안내로 답해요."],
    known: ["개발 목록에 있는 내용이에요", "같은 개발사항에 묶고, 진행 중이라고 답해요. 고치면 한 번에 알려요."],
    again: ["고친 뒤 다시 생겼어요", "고친 버전 이후의 앱에서 온 문의예요. 개발사항을 다시 접수해요."],
    declined: ["반려한 요청과 같아요", "반려 이유로 답해요."],
    answered: ["전에 답한 문의와 같아요", "그때 보낸 답을 다시 보내요. 고쳐서 보내도 돼요."],
    faq: ["자주 쓰는 답으로 충분해요", "저장해 둔 답을 보내요."],
    new: ["새 문제·개선이에요", "비슷한 문의끼리 묶었어요. 개발사항으로 넘기면 다음 개발에 들어가요."],
  };
  const PILE_DO = { fixed: "답 보내기", known: "묶고 답하기", again: "다시 접수하고 답하기", declined: "답 보내기", answered: "이 답 보내기", faq: "이 답 보내기", new: "개발사항으로 넘기기" };
  const PILE_TONE = { fixed: "good", known: "info", again: "bad", declined: "", answered: "info", faq: "info", new: "warn" };

  function sortView() {
    const d = S.data.sort;
    if (!d) return skeleton(3);
    const owner = S.me?.owner;
    const order = ["new", "again", "fixed", "known", "answered", "faq", "declined"];
    const strip = `<div class="pile-strip" role="list">${order.map((k) => d.counts[k] ? `<a role="listitem" class="pile-card ${PILE_TONE[k]}" href="#pile-${k}" data-act="sort-jump" data-key="${k}">
        <span class="pile-n">${d.counts[k]}</span><span class="pile-name">${esc(d.names[k])}</span></a>`
      : `<span role="listitem" class="pile-card zero"><span class="pile-n">0</span><span class="pile-name">${esc(d.names[k])}</span></span>`).join("")}</div>`;
    const auto = `<div class="card auto-card"><div class="auto-head"><b>자동 대응</b>
        <small>${owner ? "켜 두면 문의가 들어오는 순간 서버가 묶고 답해요. 답에는 ‘자동 정리’가 이름으로 붙어요." : "최고 관리자가 켜고 꺼요."}</small></div>
      ${["fixed", "known"].map((k) => `<label class="switch"><input type="checkbox" data-act="sort-auto" data-key="${k}"${d.auto[k] ? " checked" : ""}${owner ? "" : " disabled"}>
        <span class="track" aria-hidden="true"></span><span>${esc(d.names[k])}</span></label>`).join("")}</div>`;
    const body = d.total ? order.filter((k) => d.piles[k].length).map((k) => pileSection(k, d.piles[k])).join("")
      : empty("정리할 문의가 없어요", "답을 기다리는 문의가 모두 개발사항에 묶였거나 답했어요.");
    // A phone shows the piles first and 자동 대응·넘기기 under them (CSS order); the strip's last card leads there.
    const n = S.data.handoff?.items?.length ?? 0;
    const go = `<a role="listitem" class="pile-card handoff-chip${n ? " warn" : " zero"}" href="#sort-tools" data-act="sort-jump" data-key="tools">
        <span class="pile-n">${n}</span><span class="pile-name">Claude에게 넘길 것</span></a>`;
    return `<div class="sort-view"><p class="hint">답을 기다리는 문의 ${d.total}건을 서버가 미리 나눠 두었어요. 카드마다 보낼 답이 적혀 있어요. 고쳐서 보내도 되고, ‘빼 두기’를 누르면 정리함에서만 빠져요.</p>
      ${strip.replace("</div>", `${go}</div>`)}<div class="sort-top" id="sort-tools">${auto}${handoffCard()}</div>${body}</div>`;
  }

  function pileSection(k, items) {
    const [title, help] = PILE_HELP[k];
    const all = k === "new" || items.length < 2 ? "" : `<button class="btn small primary" data-act="sort-pile" data-key="${k}">${items.length}건 모두 ${esc(PILE_DO[k])}</button>`;
    return `<section class="pile" id="pile-${k}" aria-labelledby="pile-${k}-h"><div class="pile-head"><div><h3 id="pile-${k}-h">${esc(title)} <span class="pill ${PILE_TONE[k]}">${k === "new" ? items.reduce((n, g) => n + g.group.length, 0) : items.length}</span></h3>
        <small>${esc(help)}</small></div>${all}</div><ul class="rows">${items.map((x) => sortCard(k, x)).join("")}</ul></section>`;
  }

  function sortCard(k, x) {
    const m = x.match;
    const meta = [esc(KIND_NAMES[x.kind] ?? x.kind), esc(x.topicName ?? ""), x.app ? `앱 ${esc(String(x.app).split(" ")[0])}` : "", esc(ago(x.at)), x.same ? `나도 그래요 ${x.same}` : ""]
      .filter(Boolean).join(" · ");
    const matched = m ? `<small class="match">≈ ${m.type === "dev" ? `<a href="${href("dev", m.id)}">개발사항 ‘${esc(m.title)}’</a> · ${esc(DEV_NAMES[m.status] ?? "")}${m.releasedIn ? ` ${esc(m.releasedIn)}` : ""}`
      : m.type === "inquiry" ? `<a href="${href("inquiries", m.id)}">전에 답한 문의 ‘${esc(m.title)}’</a>` : `자주 쓰는 답 ‘${esc(m.title)}’`} · 비슷함 ${Math.round(m.score * 100)}%</small>` : "";
    const others = k === "new" && x.others?.length ? `<details><summary>같은 내용 ${x.others.length}건 더</summary><ul class="mini">${x.others.map((o) =>
      `<li><a href="${href("inquiries", o.id)}">${esc(o.preview)}</a> <small>${esc(o.app ? String(o.app).split(" ")[0] : "")}</small></li>`).join("")}</ul></details>` : "";
    const title = k === "new" ? `<label class="sort-field"><span>개발사항 제목</span><input id="sort-title-${x.id}" value="${esc(x.title)}" maxlength="100"></label>` : "";
    const reply = `<label class="sort-field"><span>${k === "new" ? "함께 보낼 답 (비우면 답 없이 넘겨요)" : "보낼 답"}</span><textarea id="sort-reply-${x.id}" rows="3">${esc(x.reply ?? "")}</textarea></label>`;
    return `<li class="sort-card"><div class="sort-ask"><b><a href="${href("inquiries", x.id)}">${esc(x.preview)}</a></b><small>${meta}</small>${matched}${others}</div>
      <div class="sort-answer">${title}${reply}
      <div class="actions"><button class="btn small primary" data-act="sort-do" data-key="${k}" data-id="${x.id}">${esc(k === "new" && x.group.length > 1 ? `${x.group.length}건 개발사항으로 넘기기` : PILE_DO[k])}</button>
        ${k === "new" ? `<button class="btn small" data-act="sort-reply-only" data-id="${x.id}">답만 보내기</button>` : ""}
        <button class="btn small ghost" data-act="sort-skip" data-id="${x.id}">빼 두기</button></div></div></li>`;
  }

  /** The action of one card as the server takes it (sortApply). */
  function sortAction(k, x) {
    const text = document.getElementById(`sort-reply-${x.id}`)?.value?.trim() ?? x.reply;
    if (k === "new") {
      const title = document.getElementById(`sort-title-${x.id}`)?.value?.trim() || x.title;
      return { do: "dev", ids: x.group, title, body: [x.preview, ...(x.others ?? []).map((o) => o.preview)].map((t) => `- ${t}`).join("\n").slice(0, 4000), ...(text ? { text } : {}) };
    }
    if (k === "known") return { do: "reply", id: x.id, item: x.match.id, text };
    if (k === "again") return { do: "reopen", id: x.id, item: x.match.id, text };
    return { do: "reply", id: x.id, text };
  }

  async function sortApply(actions, done) {
    const r = await act(() => api("/inquiries/sort/apply", "POST", { actions }), (r) => [r.replied && `답 ${r.replied}건`, r.linked && `묶음 ${r.linked}건`,
      r.created?.length && `개발사항 ${r.created.length}개`, r.reopened && `다시 접수 ${r.reopened}개`, r.skipped && `빼 둠 ${r.skipped}건`].filter(Boolean).join(" · ") || done);
    if (r) { S.data.sort = null; S.data.handoff = null; render({ keepScroll: true }); load(); }
  }

  /** [INBOX-05][INBOX-06] Claude에게 넘기기 and 개발 결과 붙여 넣기. */
  function handoffCard() {
    const h = S.data.handoff;
    const n = h?.items?.length ?? 0;
    const res = S.sortResult;
    const lines = res ? `<ul class="mini">${res.lines.map((l) => `<li>${esc(l.id)} · ${esc(DEV_NAMES[l.status] ?? l.status)}${l.version ? ` ${esc(l.version)}` : ""}${l.reason ? ` · ${esc(l.reason)}` : ""}
        ${l.title ? `<small>${esc(l.title)}</small>` : ""}${l.problem ? ` <span class="pill bad">${esc(l.problem)}</span>` : ""}</li>`).join("")}</ul>
      <label class="switch"><input type="checkbox" id="sort-notify" checked><span class="track" aria-hidden="true"></span><span>완료·반려된 개발사항의 문의에도 답 보내기</span></label>
      <div class="actions"><button class="btn small primary" data-act="sort-result-go"${res.lines.some((l) => !l.problem) ? "" : " disabled"}>반영하기</button>
        <button class="btn small ghost" data-act="sort-result-clear">다시 붙여 넣기</button></div>` : "";
    return `<div class="card handoff-card"><b>Claude에게 넘기기</b>
      <small>${h ? (n ? `아직 넘기지 않은 개발사항 ${n}개 (문의 ${h.items.reduce((a, i) => a + i.asks, 0)}건). 복사해서 Claude에게 붙여 넣으면 다음 개발에 들어가요.` : "새로 넘길 개발사항이 없어요.") : "불러오는 중이에요"}</small>
      <div class="actions"><button class="btn small primary" data-act="sort-handoff"${n ? "" : " disabled"}>${icon(P.copy)}넘길 글 복사 (${n})</button>
        <button class="btn small" data-act="sort-handoff-all">진행 중인 것 모두 복사</button></div>
      <details${res ? " open" : ""}><summary>개발 결과 붙여 넣기</summary>
        <p class="hint">Claude가 개발을 마치고 준 ‘결과’ 줄(예: d0123… 완료 1.36.0)을 붙여 넣어요. 먼저 무엇이 바뀌는지 보여 드려요.</p>
        ${res ? lines : `<textarea id="sort-result" rows="4" placeholder="d0123456789abcde 완료 1.36.0"></textarea>
        <div class="actions"><button class="btn small" data-act="sort-result-check">무엇이 바뀌는지 보기</button></div>`}</details></div>`;
  }

  function devListView() {
    const d = S.data.devList;
    const counts = d?.counts ?? {};
    const edit = can("dev", "edit");
    const picking = Boolean(S.devPick) && can("devStatus", "edit");
    const head = `<div class="segmented" role="group" aria-label="개발사항 상태">
        ${seg("dev-status", "open", S.devStatus, "할 일", counts.open)}${seg("dev-status", "todo", S.devStatus, "접수", counts.todo)}
        ${seg("dev-status", "doing", S.devStatus, "개발중", counts.doing)}${seg("dev-status", "done", S.devStatus, "완료", counts.done)}
        ${seg("dev-status", "rejected", S.devStatus, "반려", counts.rejected)}${seg("dev-status", "all", S.devStatus, "전체", counts.all)}
        ${seg("dev-status", "deleted", S.devStatus, "지움", counts.deleted)}</div>
      <div class="searchbar"><input id="search" type="search" placeholder="제목이나 내용으로 찾기" value="${esc(S.devQuery)}" autocomplete="off" enterkeyhint="search" aria-label="개발사항 찾기">
        ${edit && !picking ? `<button class="btn primary" data-act="dev-new">${icon(P.plus)}새 개발사항</button>` : ""}
        ${can("devStatus", "edit") ? `<button class="btn" data-act="dev-pick" aria-pressed="${picking}">${picking ? "선택 마침" : `${icon(P.check)}여러 개 고르기`}</button>` : ""}
        ${picking ? "" : csvButton("dev", d?.items)}</div>
      <div class="chips" role="group" aria-label="맡은 사람">${[["", "모두"], ["mine", "내가 맡은 것"]].map(([key, label]) =>
        `<button type="button" class="chip pick" data-act="dev-who" data-key="${key}" aria-pressed="${S.devWho === key}">${label}</button>`).join("")}</div>
      <p class="hint">문의에서 "개발사항에 추가"로 올리거나 여기서 바로 만들어요. 문의 수가 많은 것이 위에 와요. 고친 것은 모두 항목마다 기록에 남아요.${can("devStatus", "edit") ? " 여러 개 고르기로 한 번에 복사하거나 처리해요." : " 처리(개발중·완료·반려)는 최고 관리자나 처리 권한이 있는 관리자가 해요."}</p>`;
    return head + `<div id="dev-list">${devRows()}</div>`;
  }

  function devRows() {
    const d = S.data.devList;
    if (!d) return skeleton();
    const picking = Boolean(S.devPick) && can("devStatus", "edit");
    if (!d.items.length) {
      return empty(S.devQuery ? "찾는 개발사항이 없어요" : S.devStatus === "deleted" ? "지운 항목이 없어요" : "개발사항이 없어요",
        S.devQuery ? "" : "문의를 열고 개발사항에 추가를 누르면 여기에 모여요.");
    }
    const row = (x) => {
      const inner = `<span class="row-main"><b>${esc(x.title)}</b>
          <small>${[`문의 ${x.links}건${x.waiting ? ` (답 대기 ${x.waiting})` : ""}`, `${esc(x.createdBy.name)} · ${esc(ago(x.updatedAt))}`,
            x.assignee ? (x.assignee.id === S.me.id ? "내가 맡음" : `맡음 ${esc(x.assignee.name)}`) : ""].filter(Boolean).join(" · ")}</small>
          ${x.preview ? `<span class="preview">${esc(x.preview)}</span>` : ""}</span>
        <span class="pill ${DEV_TONES[x.status]}">${x.deletedAt ? "지움" : DEV_NAMES[x.status]}</span>`;
      return picking ? `<li><label class="row${S.devPick.has(x.id) ? " picked" : ""}"><input class="check" type="checkbox" data-pick="${x.id}"${S.devPick.has(x.id) ? " checked" : ""}>${inner}</label></li>`
        : `<li><a class="row" href="${href("dev", x.id)}">${inner}${icon(P.chevron)}</a></li>`;
    };
    const n = picking ? S.devPick.size : 0;
    const status = can("devStatus", "edit");
    return `<ul class="rows">${d.items.map(row).join("")}</ul>${moreButton(d.next, "dev-more", d.total, d.items.length)}
      ${picking ? `<div class="bulkbar" role="toolbar" aria-label="고른 항목"><b>${n}개 골랐어요</b>
        <button class="btn small ghost" data-act="dev-pick-all">${n === d.items.length ? "모두 풀기" : "모두 고르기"}</button>
        <button class="btn small" data-act="dev-copy"${n ? "" : " disabled"}>${icon(P.copy)}복사</button>
        ${status && S.devStatus !== "deleted" ? ["doing", "done", "rejected"].map((k) => `<button class="btn small${k === "done" ? " primary" : ""}" data-act="dev-bulk" data-status="${k}"${n ? "" : " disabled"}>${DEV_NAMES[k]}</button>`).join("") : ""}
      </div>` : ""}`;
  }

  function devView() {
    const d = S.data.dev;
    if (!d || d.id !== S.route.id) return skeleton(3);
    const edit = can("dev", "edit") && !d.deletedAt;
    const status = can("devStatus", "edit") && !d.deletedAt;
    const reply = can("inquiries", "edit") && d.inquiries.length && !d.deletedAt;
    const mine = d.assignee?.id === S.me.id;
    return `<div class="cols"><section>
      <div class="card"><div class="card-head"><h2>${esc(d.title)}</h2><span class="pill ${DEV_TONES[d.status]}">${DEV_NAMES[d.status]}</span></div>
        <p class="meta">${esc(d.createdBy.name)}님이 ${esc(moment(d.createdAt))}에 추가 · 문의 ${d.links}건${d.releasedIn ? ` · ${esc(d.releasedIn)}에 반영` : ""}${d.assignee ? ` · ${mine ? "내가 맡음" : `${esc(d.assignee.name)}님이 맡음`}` : ""}</p>
        ${d.body ? `<p class="dev-body">${esc(d.body)}</p>` : '<p class="meta">내용이 없어요.</p>'}
        ${d.status === "rejected" && d.reason ? `<p class="note bad">반려 이유: ${esc(d.reason)}</p>` : ""}
        ${d.deletedAt ? `<p class="note warn">${esc(moment(d.deletedAt))}에 지운 항목이에요. 기록은 그대로 남아 있어요.${S.me.owner ? "" : " 되살리기는 최고 관리자가 해요."}</p>` : ""}
        <div class="actions">${d.deletedAt ? (S.me.owner ? '<button class="btn primary" data-act="dev-restore">되살리기</button>' : "")
          : `${edit ? `<button class="btn danger" data-act="dev-delete">지우기</button>
            <button class="btn" data-act="dev-assign" data-to="${mine ? "" : "me"}">${mine ? "맡기 취소" : d.assignee ? "내가 넘겨받기" : "내가 맡기"}</button>
            <button class="btn" data-act="dev-edit">고치기</button>` : ""}
            <button class="btn" data-act="dev-copy-one">${icon(P.copy)}복사</button>`}</div></div>
      ${status ? `<section class="card"><h2>처리</h2><p class="hint">누르면 바로 바뀌고 기록에 남아요. 반려는 이유를 함께 남겨요.</p>
        <div class="chips" role="group" aria-label="처리 상태">${Object.keys(DEV_NAMES).map((k) =>
          `<button type="button" class="chip pick" data-act="dev-status-set" data-status="${k}" aria-pressed="${d.status === k}">${DEV_NAMES[k]}</button>`).join("")}</div>
        <form class="mini" data-form="dev-release"><label for="dev-release">반영 버전</label>
          <p class="hint">완료한 항목이 나간 앱 버전(예: 1.18.0). 연결된 문의를 보낸 분 앱에 '반영됨 1.18.0'으로 보여요.</p>
          <div class="actions start"><input id="dev-release" name="v" inputmode="decimal" pattern="\\d{1,2}\\.\\d{1,2}\\.\\d{1,3}" placeholder="1.18.0" value="${esc(d.releasedIn ?? "")}">
          <button class="btn small" type="submit">저장</button></div></form></section>` : ""}
      <section class="card"><div class="card-head"><h2>연결된 문의</h2><span class="pill">${d.links}</span></div>
        ${d.inquiries.length ? `<ul class="rows">${d.inquiries.map((q) => `<li class="item"><a class="row-main" href="${href("inquiries", q.id)}">
            <b>${esc(KIND_NAMES[q.kind] ?? "문의")} <span class="pill ${q.status === "open" ? "warn" : q.status === "answered" ? "ok" : ""}">${STATUS_NAMES[q.status] ?? q.status}</span></b>
            <small>${esc(q.preview)}</small><small>${esc(q.linkedBy)}님이 ${esc(ago(q.linkedAt))} 연결</small></a>
            ${edit ? `<button class="btn small ghost" data-act="dev-unlink" data-id="${q.id}" aria-label="연결 떼기">떼기</button>` : ""}</li>`).join("")}</ul>`
          : `<p class="hint">${d.links && !can("inquiries") ? "문의 권한이 있어야 내용을 볼 수 있어요." : "문의 화면의 개발사항에 추가로 연결해요."}</p>`}
        ${reply ? `<div class="actions start"><button class="btn${d.status === "done" ? " primary" : ""}" data-act="dev-reply">연결된 문의에 한 번에 답하기</button></div>` : ""}</section>
    </section><aside>
      ${edit ? `<form class="card mini" data-form="dev-note"><h2>메모</h2><label class="sr" for="dev-note">메모</label>
        <textarea id="dev-note" name="text" maxlength="1000" placeholder="다른 관리자에게 남길 말. 기록에 남아요." required></textarea>
        <div class="actions"><button class="btn small" type="submit">남기기</button></div></form>` : ""}
      <section class="card"><h2>기록</h2><p class="hint">누가 언제 무엇을 바꿨는지 모두 남아요.</p>
        <ul class="history">${d.history.map(historyItem).join("")}</ul></section>
    </aside></div>`;
  }

  /** One line of an item's history, in words, with what it was before. */
  function historyItem(h) {
    const x = h.detail ?? {};
    const was = (a, b) => `<span class="was">${esc(a || "없음")}</span> → ${esc(b || "없음")}`;
    const what = {
      create: () => "만들었어요",
      edit: () => [x.title ? `제목: ${L.wordDiff(x.title[0] ?? "", x.title[1] ?? "")}` : "", x.body ? `내용을 고쳤어요<details><summary>바뀐 곳</summary><blockquote class="wdiff">${L.wordDiff(x.body[0] ?? "", x.body[1] ?? "")}</blockquote></details>` : ""].filter(Boolean).join("<br>"),
      status: () => `처리: ${was(DEV_NAMES[x.status?.[0]], DEV_NAMES[x.status?.[1]])}${x.bulk > 1 ? ` <span class="meta">(${x.bulk}개 한 번에)</span>` : ""}${x.reason ? `<blockquote>${esc(x.reason)}</blockquote>` : ""}`,
      assign: () => `맡은 사람: ${was(x.assignee?.[0], x.assignee?.[1])}`,
      note: () => `<blockquote>${esc(x.text)}</blockquote>`,
      link: () => `문의를 연결했어요${can("inquiries") && x.inquiry ? ` <a href="${href("inquiries", x.inquiry)}">열기</a>` : ""}`,
      unlink: () => "문의 연결을 뗐어요",
      release: () => `반영 버전: ${was(x.releasedIn?.[0], x.releasedIn?.[1])}`,
      delete: () => "지웠어요",
      restore: () => "되살렸어요",
      reply: () => `연결된 문의 ${x.count}건에 답했어요${x.close ? "(닫음)" : ""}<blockquote>${esc(x.text)}</blockquote>`,
    }[h.action]?.() ?? esc(h.action);
    return `<li><span class="who">${esc(h.actor || "관리자")}</span><span class="when">${esc(moment(h.at))}</span><span class="what">${what}</span></li>`;
  }

  function openDevEdit() {
    const d = S.data.dev;
    openDialog(`<form data-form="dev-edit" data-since="${d.updatedAt}"><h2>개발사항 고치기</h2><p>고친 내용은 전과 후가 함께 기록에 남아요.</p>
      <div class="fieldset"><label for="dev-title">제목</label><input id="dev-title" name="title" maxlength="100" required value="${esc(d.title)}"></div>
      <div class="fieldset"><label for="dev-body">내용</label><textarea id="dev-body" name="body" maxlength="4000">${esc(d.body)}</textarea></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`, "wide");
  }

  function openDevNew() {
    openDialog(`<form data-form="dev-new"><h2>새 개발사항</h2><p>추가한 사람으로 내 이름이 남아요. 문의에서 추가하면 문의가 함께 연결돼요.</p>
      <div class="fieldset"><label for="dev-title">제목</label><input id="dev-title" name="title" maxlength="100" required></div>
      <div class="fieldset"><label for="dev-body">내용</label><textarea id="dev-body" name="body" maxlength="4000"></textarea></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">추가</button></div></form>`, "wide");
  }

  /** 반려 needs a reason: one tap on a usual one fills it in. [ids] is set for many items at once. */
  function openReason(ids) {
    openDialog(`<form data-form="dev-reject" data-ids="${ids ? ids.join(",") : ""}"><h2>${ids ? `${ids.length}개를 반려할까요?` : "반려할까요?"}</h2>
      <p>이유는 항목마다 기록에 남아요. 연결된 문의에 답할 때도 참고해요.</p>
      <div class="chips">${REJECT_REASONS.map((r, i) => `<button type="button" class="chip pick" data-act="reason-pick" data-i="${i}">${esc(r)}</button>`).join("")}</div>
      <label for="dev-reason">반려 이유</label><textarea id="dev-reason" name="reason" maxlength="300" required></textarea>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">반려</button></div></form>`);
  }

  /** [ADMIN-11] One answer to the linked questions, filled in for the item's status; the admin reads it and sends. */
  function openDevReply() {
    const d = S.data.dev;
    const open = d.inquiries.filter((q) => q.status !== "closed");
    openDialog(`<form data-form="dev-reply"><h2>연결된 문의에 답하기</h2>
      <p>고른 문의를 보낸 분들 앱에 같은 답이 가요. 읽어 보고 보내 주세요.</p>
      <div class="radios">${d.inquiries.map((q) => `<label class="radio"><input type="checkbox" name="ids" value="${q.id}"${q.status !== "closed" || !open.length ? " checked" : ""}>
        <span>${esc(q.preview.slice(0, 60))} <span class="meta">${STATUS_NAMES[q.status] ?? ""}</span></span></label>`).join("")}</div>
      <label for="dev-reply-text">답</label><textarea id="dev-reply-text" name="text" maxlength="2000" required>${esc(d.status === "done" && d.releasedIn ? DEV_REPLIES.done.replace("반영했어요.", `${d.releasedIn}에 반영했어요.`) : DEV_REPLIES[d.status] ?? DEV_REPLIES.done)}</textarea>
      <label class="switch"><input type="checkbox" name="close"${d.status === "done" || d.status === "rejected" ? " checked" : ""}><span class="track" aria-hidden="true"></span><span>답하고 닫기</span></label>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">보내기</button></div></form>`, "wide");
  }

  /**
   * [ADMIN-12][ADMIN-16] 자주 쓰는 답: every admin's, added from what was typed or from here, changed or
   * deleted. A topic puts it first on questions of that topic; {앱버전} {주제} {오늘} are filled in.
   */
  function openTemplates(editId) {
    const list = S.templates ?? [];
    const names = S.topicNames ?? S.data.inquiries?.topicNames ?? {};
    const t = editId ? list.find((x) => x.id === Number(editId)) : null;
    const typed = t ? t.text : document.getElementById("reply")?.value.trim() ?? "";
    const topic = t ? t.topic : S.data.inquiry?.topic ?? "";
    openDialog(`<form data-form="template-add" data-id="${t ? t.id : ""}"><h2>${t ? "자주 쓰는 답 고치기" : "자주 쓰는 답"}</h2><p>모든 관리자가 같이 써요. 누르면 답 칸에 들어가요. {앱버전}, {주제}, {오늘}은 그 문의에 맞게 채워져요.</p>
      ${!t && list.length ? `<ul class="rows">${list.map((x) => `<li class="item"><div class="row-main"><b>${esc(x.label)}${x.topic ? ` <span class="pill">${esc(names[x.topic] ?? x.topic)}</span>` : ""}</b>
        <small>${esc(x.text.slice(0, 80))}${x.by ? ` · ${esc(x.by)}` : ""} · ${x.uses ?? 0}번 씀</small></div>
        <button type="button" class="btn small" data-act="template-edit" data-id="${x.id}">고치기</button>
        <button type="button" class="btn small danger" data-act="template-remove" data-id="${x.id}">지우기</button></li>`).join("")}</ul>` : ""}
      <div class="fieldset"><label for="tpl-label">${t ? "이름" : "새 답 이름"}</label><input id="tpl-label" name="label" maxlength="20" required placeholder="예: 업데이트 안내" value="${esc(t?.label ?? "")}"></div>
      <div class="fieldset"><label for="tpl-topic">주제(이 주제의 문의에서 먼저 보여요)</label><div class="field"><select id="tpl-topic" name="topic"><option value="">모든 문의</option>
        ${Object.entries(names).map(([k, label]) => `<option value="${k}"${topic === k ? " selected" : ""}>${esc(label)}</option>`).join("")}</select></div></div>
      <div class="fieldset"><label for="tpl-text">내용</label><textarea id="tpl-text" name="text" maxlength="2000" required>${esc(typed)}</textarea></div>
      <div class="actions"><button class="btn" type="button" data-act="${t ? "templates" : "close"}">${t ? "목록으로" : "닫기"}</button><button class="btn primary" type="submit">${t ? "저장" : "넣기"}</button></div></form>`, "wide");
  }

  /** [ADMIN-16] One answer to every picked question. */
  function openBulkReply() {
    const n = S.inquiryPick.size;
    openDialog(`<form data-form="bulk-reply"><h2>${n}개 문의에 같은 답 보내기</h2><p>고른 문의를 보낸 분들 앱에 같은 답이 가요. {앱버전} 같은 칸은 채워지지 않으니 그대로 읽히는 말로 써 주세요.</p>
      <div class="chips">${templateList().map((t, i) => `<button type="button" class="chip pick" data-act="bulk-template" data-i="${i}">${esc(t.label)}</button>`).join("")}</div>
      <label for="bulk-text">답</label><textarea id="bulk-text" name="text" maxlength="2000" required></textarea>
      <input type="hidden" name="template" value="">
      <label class="switch"><input type="checkbox" name="close"><span class="track" aria-hidden="true"></span><span>답하고 닫기</span></label>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">보내기</button></div></form>`, "wide");
  }

  /** [ADMIN-16] The picked questions joined to one 개발사항, most alike first. */
  async function openBulkLink() {
    openDialog(`<div class="box"><h2>개발사항에 연결</h2><p>고른 문의 ${S.inquiryPick.size}개를 한 항목에 붙여요. 그 항목의 요청 수가 늘어요.</p>
      <input id="dev-find" type="search" placeholder="제목으로 찾기" autocomplete="off" aria-label="개발사항 찾기">
      <div id="dev-found"><div class="card skeleton short"></div></div>
      <div class="actions"><button class="btn" data-act="close">닫기</button></div></div>`, "wide");
    try {
      const d = await api("/dev?status=open&limit=100");
      const text = (S.data.inquiries?.inquiries ?? []).filter((q) => S.inquiryPick.has(q.id)).map((q) => q.preview).join(" ");
      S.devCandidates = L.likeFirst(d.items, text);
    } catch (e) {
      fail(e);
      S.devCandidates = [];
    }
    S.linkMode = "bulk";
    showCandidates("");
  }

  async function devSave(body, done) {
    try {
      const d = await api(`/dev/${S.route.id}`, "POST", body);
      S.data.dev = d;
      if (done) toast(done);
      render({ keepScroll: true });
      load({ quiet: true });
      return d;
    } catch (e) {
      if (e.message === "changed" && e.data?.item) {
        S.data.dev = e.data.item;
        render({ keepScroll: true });
      }
      fail(e);
      return null;
    }
  }

  /** Sets a status; 완료 with questions still waiting offers the one answer to all of them at once. */
  async function devSetStatus(status, reason) {
    const d = await devSave(reason ? { status, reason } : { status }, `${DEV_TO[status]} 바꿨어요`);
    if (d && (status === "done" || status === "rejected") && can("inquiries", "edit") && d.inquiries.some((q) => q.status !== "closed")) openDevReply();
  }

  async function devBulk(status, reason) {
    const ids = [...S.devPick];
    const r = await act(() => api("/dev/bulk", "POST", reason ? { ids, status, reason } : { ids, status }),
      (x) => `${x.changed}개를 ${DEV_TO[status]} 바꿨어요${status === "done" ? ". 항목을 열어 연결된 문의에 답할 수 있어요" : ""}`);
    if (r) {
      S.devPick = null;
      load();
    }
  }

  // ---- companies ----

  function companyView(c) {
    if (!c || c.id !== S.route.id) return skeleton(3);
    if (c.mergedInto) {
      return `<section class="card"><p>이 회사는 <a href="${href("companies", c.mergedInto.id)}">${esc(c.mergedInto.name)}</a>에 합쳐졌어요.</p></section>`;
    }
    const edit = can("companies", "edit");
    const dis = edit ? "" : " disabled";
    return `<div class="cols"><div>
      <section class="card">
        <h2>규칙</h2>
        ${c.rules.length ? c.rules.map((r) => ruleCard(r, edit)).join("") : '<p class="meta">채택된 규칙이 아직 없어요.</p>'}
      </section></div><div>
      <section class="card">
        <form data-form="rename"><label for="name">이름</label>
          <div class="field"><input id="name" name="name" value="${esc(c.name)}" maxlength="40" required${dis}>${edit ? '<button class="btn" type="submit">저장</button>' : ""}</div></form>
        <label class="switch"><input type="checkbox" data-change="hidden"${c.hidden ? " checked" : ""}${dis}><span class="track" aria-hidden="true"></span><span>회사 찾기에서 숨기기</span></label>
        <p class="meta">${day(c.createdAt)}에 생김${c.pending ? ` · 검토 대기 ${c.pending}` : ""} · 참여한 휴대폰 ${c.members ?? 0}대</p>
      </section>
      <section class="card">
        <h2>다른 이름</h2>
        <p class="hint">영문 이름, 줄임말, 예전 이름처럼 사람들이 찾을 만한 이름을 넣어 두면 회사 찾기에 잡혀요.</p>
        <div class="chips">${c.aliases.length ? c.aliases.map((a) => `<span class="chip">${esc(a)}${edit ? `<button data-act="remove-alias" data-name="${esc(a)}"
          aria-label="${esc(a)} 지우기">×</button>` : ""}</span>`).join("") : '<span class="meta">아직 없어요</span>'}</div>
        ${edit ? `<form data-form="alias"><div class="field"><input name="name" placeholder="예: Bamnat Steel, 밤스틸" maxlength="40" required aria-label="다른 이름">
          <button class="btn" type="submit">추가</button></div></form>` : ""}
      </section>
      ${edit ? `<section class="card">
        <h2>다른 회사에 합치기</h2>
        <p class="hint">같은 회사가 다른 이름으로 두 번 생겼을 때 써요. 제보와 규칙이 모두 옮겨지고 이 이름은 다른 이름으로 남아요.</p>
        <form data-form="merge-search"><div class="field"><input name="q" placeholder="합칠 회사 이름" required aria-label="합칠 회사 이름">
          <button class="btn" type="submit">찾기</button></div></form>
        <div id="merge-results"></div>
      </section>` : ""}</div></div>`;
  }

  /** AH 1.27 [PLAZA-15] " (순서 2 · 시간 1)": where the 달라요 say a rule is wrong. */
  function whyLine(why) {
    if (!why) return "";
    const parts = [["order", "순서"], ["time", "시간"], ["pay", "급여 항목"], ["other", "그 밖"]].filter(([k]) => why[k] > 0).map(([k, n]) => `${n} ${why[k]}`);
    return parts.length ? ` (${parts.join(" · ")})` : "";
  }

  function ruleCard(r, edit) {
    const [tone, label] = r.withdrawn ? ["bad", "내림"] : r.superseded ? ["", "이전 버전"] : ["ok", "쓰는 중"];
    // [PLZ-06] 내리기·다시 올리기 need 광장 규칙 내리기 (결정 D35).
    const action = !edit || !can("takedown", "edit") ? "" : r.withdrawn ? `<button class="btn" data-act="restore-rule" data-id="${r.id}">다시 올리기</button>`
      : r.superseded ? "" : `<button class="btn danger" data-act="withdraw-rule" data-id="${r.id}">내리기</button>`;
    return `<div class="rule${r.withdrawn || r.superseded ? " dim" : ""}">
      <div class="head"><div><b>${esc(r.dept || "회사 전체")}</b> <span class="ver">v${r.version}</span>
        <p class="meta">${r.source === "auto" ? "자동 채택" : r.source === "rival" ? "더 믿을 판으로 바뀜" : "직접 채택"} · ${day(r.adoptedAt)} · 맞았어요 ${r.ok} · 달라요 ${r.bad}${whyLine(r.why)}</p></div>
        <span class="pill ${tone}">${label}</span>${r.doubted ? '<span class="pill warn" title="달라요가 많아 앱에서 대표 주기로 보이지 않아요">대표에서 빠짐</span>' : ""}${r.stable ? '<span class="pill ok" title="3대 이상이 같은 값을 쓰고 30일 동안 고친 사람이 없어요">안정</span>' : ""}</div>
      ${r.changesFromPrevious?.summary ? `<p class="meta change-line">v${r.changesFromPrevious.version}와 다른 점: <b>${esc(r.changesFromPrevious.summary)}</b></p>` : ""}
      ${r.changesFromPrevious?.facts && !r.withdrawn ? `<details data-key="rc:${r.id}"><summary>이전 판과 다른 점 자세히</summary>${V.changes(r.changesFromPrevious, `v${r.changesFromPrevious.version}와 다른 점`)}</details>` : ""}
      <details><summary>내용</summary>${lines(r.lines)}</details>
      ${action ? `<div class="actions">${action}</div>` : ""}
    </div>`;
  }

  // ---- rewards ----

  function rewardsView() {
    const r = S.data.rewards;
    const counts = r?.counts ?? {};
    const edit = can("rewards", "edit");
    const segs = [["owed", "드릴 보상"], ["paid", "지급함"], ["void", "취소"]].map(([key, label]) => seg("reward-tab", key, S.rewardTab, label, counts[key])).join("");
    const body = !r || r.status !== S.rewardTab ? skeleton() : !r.rewards.length ? empty(S.rewardTab === "owed" ? "드릴 보상이 없어요" : "목록이 비어 있어요", "")
      : `<ul class="rows">${r.rewards.map((w) => `<li class="item">
        <div class="row-main"><b>${esc(w.company.name)}${w.dept ? ` · ${esc(w.dept)}` : ""}</b>
          <small>프로 ${w.months}개월 · ${day(w.createdAt)} ${w.invite ? "친구 초대" : w.ask ? "앱에 없는 방식" : "채택"}${w.version ? ` · 규칙 v${w.version}` : ""}${w.paidAt ? ` · ${day(w.paidAt)} 지급함` : ""}</small>
          ${w.deferred ? '<span class="pill ok">Play 결제 미룸</span>' : ""}
          ${w.memo ? `<small class="memo">${esc(w.memo)}</small>` : ""}</div>
        ${edit ? `<div class="actions">${w.status === "owed" ? `<button class="btn" data-act="reward" data-id="${w.id}" data-status="void">취소</button>
          <button class="btn primary" data-act="reward" data-id="${w.id}" data-status="paid" data-months="${w.months}">지급하기</button>`
          : `<button class="btn" data-act="reward" data-id="${w.id}" data-status="owed">대기로 되돌리기</button>`}</div>` : ""}
      </li>`).join("")}</ul>${moreButton(r.next, "rewards-more", r.total, r.rewards.length)}`;
    // AN 1.28 [ADMUX-32] 친구 초대 규칙 (from 설정) under the list; an admin with only 설정 sees just that.
    const rules = can("settings") ? inviteSettingsCard(can("settings", "edit")) : "";
    if (!can("rewards")) return rules;
    return `<div class="segmented" role="group" aria-label="보상 상태">${segs}</div>
      <div class="toolbar"><input id="search" type="search" placeholder="회사나 부서로 찾기" value="${esc(S.rewardQuery)}" autocomplete="off" enterkeyhint="search" aria-label="보상 찾기">${csvButton("rewards", r?.status === S.rewardTab ? r.rewards : null)}</div>
      ${inviteSummaryView(r?.invites)}
      <p class="hint">[지급하기]를 누르면 보낸 분 휴대폰에 프로 이용권이 생겨요. 이용권은 쌓이고, 정식 출시 전에 드린 것은 출시 날부터 세요. 이미 구독 중인 분은 Play의 다음 결제일이 그만큼 미뤄져요(서버에 Play 키가 있을 때). 스토어 리뷰와는 묶지 않아요.</p>${body}${rules}`;
  }

  // AE 1.26 시작
  /** [INVITE-06] 친구 초대 at a glance: counted, waiting, stopped and why, and the codes most friends came by. */
  function inviteSummaryView(v) {
    if (!v) return "";
    const why = { self: "자기 초대", phone: "같은 휴대폰" };
    const refused = Object.entries(v.refused ?? {}).map(([k, n]) => `${why[k] ?? k} ${n}`).join(" · ") || "없음";
    const top = v.top.length ? `<ul class="rows">${v.top.map((t) => `<li class="item"><div class="row-main"><b>${esc(t.code)}</b>
      <small>친구 ${t.friends}명 · 채움 ${t.counted} · 사용 기간 채우는 중 ${t.waiting}</small></div></li>`).join("")}</ul>` : "";
    return `<section class="card" aria-label="친구 초대"><h3>친구 초대</h3>
      <p>초대 코드 ${v.codes}개 · 보상 받음 ${v.credited}명 · 다음 보상 기다림 ${v.qualified ?? 0}명 · 상한 넘음 ${v.capped}명 · 사용 기간 채우는 중 ${v.waiting}명 · 끝남 ${v.expired ?? 0}명</p>
      <p class="hint">${v.rules ? `${esc(inviteRuleWords(v.rules))} ` : ""}최근 30일 막음: ${refused}. 규칙은 아래 친구 초대 카드에서 바꿔요. 같은 코드에 친구가 갑자기 몰리면 살펴봐 주세요.</p>${burstLine(v)}${top}</section>`;
  }

  /**
   * [INVITE-08] AG 1.27 [INVITE-09] The rules in one line, read as the user meant them (리뷰 2026-10-25): months for each
   * group of different friends, not for one friend's days. "친구 5명이 각자 7일 이상 쓰면 프로 1개월, 5명 더 채울 때마다 또 1개월 · 상한 없음".
   */
  function inviteRuleWords(r) {
    return `친구 ${r.friends}명이 각자 ${r.days}일 이상 쓰면 프로 ${r.months}개월, ${r.friends}명 더 채울 때마다 또 ${r.months}개월 · ` +
      `${r.capMonths ? `1년에 ${r.capMonths}개월까지` : "상한 없음"}. 같은 친구·같은 휴대폰은 한 번, 자기 초대는 안 세요.`;
  }

  /** AG 1.27 [INVITE-10] 몰림: friends of one code from one network within an hour, let in but worth a look. */
  function burstLine(v) {
    if (!v.bursts) return "";
    const codes = (v.burstCodes ?? []).map((b) => `${esc(b.code)} ${b.n}`).join(" · ");
    return `<p class="hint warn">최근 30일 몰림 ${v.bursts}번(같은 네트워크에서 한 시간에 같은 코드로 친구 3명 넘게): ${codes}. 같은 회사 와이파이일 수 있어 막지는 않았어요.</p>`;
  }

  /** [INVITE-08] 설정 › 친구 초대: the developer's numbers, applied by the server to every invite from then on. */
  function inviteSettingsCard(edit) {
    const s = S.inviteDraft ?? S.data.inviteSettings;
    if (!s) return '<section class="card"><h2>친구 초대</h2>' + skeleton(1) + "</section>";
    return `<section class="card"><h2>친구 초대</h2>
      ${stepper("friends", "보상에 필요한 친구", s.friends, "명", edit, "friend-step")}
      ${stepper("days", "친구가 써야 하는 기간", s.days, "일", edit, "friend-step")}
      ${stepper("months", "보상", s.months, "개월", edit, "friend-step")}
      ${stepper("capMonths", "1년 상한", s.capMonths ? `${s.capMonths}` : "없음", s.capMonths ? "개월" : "", edit, "friend-step")}
      <p class="hint">${esc(inviteRuleWords(s))} 쓴다는 건 친구가 초대를 알린 날부터 이 기간이 지난 날(한국 날짜, 서버 시계)에 앱을 다시 여는 거예요. 기간을 채운 친구가 모일 때마다 초대한 분께 보상을 드려요. 상한을 0으로 두면 없음이에요. 같은 휴대폰은 한 번만 세고 자기 초대는 막아요.</p>
      ${edit ? `<div class="actions">${S.inviteDraft ? '<button class="btn ghost" data-act="friend-undo">되돌리기</button>' : ""}<button class="btn primary" data-act="save-friend"${S.inviteDraft ? "" : " disabled"}>저장</button></div>` : ""}
    </section>`;
  }
  // AE 1.26 끝

  // ---- admins (the owner only) ----

  function adminsView() {
    const d = S.data.admins;
    if (!d) return skeleton(3);
    const waiting = d.admins.filter((a) => a.status === "pending");
    const members = d.admins.filter((a) => a.status !== "pending");
    const invites = d.invites.filter((i) => i.state !== "joined");
    const statusPill = (a) => (a.status === "active" ? '<span class="pill ok">사용 중</span>' : '<span class="pill bad">멈춤</span>');
    const first = !members.length && !invites.length && !waiting.length;
    return `<div class="actions start" id="admins-top"><button class="btn primary" data-act="invite">${icon(P.plus)}관리자 초대</button></div>
      <details class="card how"${first ? " open" : ""}><summary>관리자는 어떻게 들어오나요</summary>
        <ol class="steps"><li>여기서 초대할 메일과 권한을 정하면 초대 링크가 생겨요.</li>
          <li>그 링크를 그 메일로 보내요. 메일을 받은 분만 계정을 만들 수 있어요${d.mail ? " (확인 코드도 메일로 가요)" : ""}.</li>
          <li>링크를 연 분이 이름과 비밀번호를 정하고 앱을 설치해요.</li>
          <li>여기에서 승인하면 그때부터 로그인할 수 있어요.</li></ol></details>
      ${waiting.length ? `<h2 class="section">승인을 기다려요<span class="count">${waiting.length}</span></h2><ul class="rows">${waiting.map((a) => `<li class="item">
        <div class="row-main"><b>${esc(a.name)}</b><small>${esc(a.email)} · ${esc(ago(a.createdAt))} 가입</small><div class="perm-sum">${permPills(a.perms, d.areas)}</div></div>
        <div class="actions"><button class="btn danger" data-act="admin-remove" data-id="${a.id}" data-name="${esc(a.name)}">거절</button>
          <button class="btn primary" data-act="admin-approve" data-id="${a.id}">승인</button></div></li>`).join("")}</ul>` : ""}
      <h2 class="section">관리자<span class="count">${members.length + 1}</span></h2>
      <ul class="rows">
        <li class="item"><div class="row-main"><b>최고 관리자</b><small>ADMIN_PASSWORD로 로그인 · 모든 권한과 관리자 관리</small></div><span class="pill gold">최고</span></li>
        ${members.map((a) => `<li class="item">
          <div class="row-main"><b>${esc(a.name)}</b><small>${esc(a.email)}${a.lastLogin ? ` · 마지막 로그인 ${esc(ago(a.lastLogin))}` : " · 아직 로그인 안 함"}</small>
            <div class="perm-sum">${permPills(a.perms, d.areas)}</div></div>
          ${statusPill(a)}
          <div class="actions">${can("audit") ? `<a class="btn small ghost" href="${L.routeHash("audit", null, { actor: a.id })}">한 일</a>` : ""}<button class="btn small" data-act="admin-edit" data-id="${a.id}">권한</button>
            ${a.status === "active" ? `<button class="btn small" data-act="admin-status" data-id="${a.id}" data-status="suspended" data-name="${esc(a.name)}">멈추기</button>`
              : `<button class="btn small" data-act="admin-status" data-id="${a.id}" data-status="active">다시 쓰기</button>`}
            <button class="btn small danger" data-act="admin-remove" data-id="${a.id}" data-name="${esc(a.name)}">삭제</button></div></li>`).join("")}
      </ul>
      ${phonesSection()}
      <h2 class="section">초대한 메일<span class="count">${invites.length}</span></h2>
      ${invites.length ? `<ul class="rows">${invites.map((i) => `<li class="item">
        <div class="row-main"><b>${esc(i.email)}</b><small>${i.state === "expired" ? "링크 만료" : `${esc(day(i.expiresAt))}까지`}${i.note ? ` · ${esc(i.note)}` : ""}</small>
          <div class="perm-sum">${permPills(i.perms, d.areas)}</div></div>
        <span class="pill ${i.state === "expired" ? "bad" : "warn"}">${i.state === "expired" ? "만료" : "가입 전"}</span>
        <div class="actions"><button class="btn small" data-act="invite-again" data-email="${esc(i.email)}">새 링크</button>
          <button class="btn small danger" data-act="invite-remove" data-email="${esc(i.email)}">취소</button></div></li>`).join("")}</ul>`
        : '<p class="hint">가입을 기다리는 초대가 없어요.</p>'}`;
  }

  /** [MON-09] Admins' phones tied with a code from 내 정보: approved ones get 프로 free while the admin stays. */
  function phonesSection() {
    const p = S.data.phones;
    if (!p) return "";
    const pill = (d) => (d.status === "approved" ? '<span class="pill ok">프로 무료</span>' : '<span class="pill warn">승인 대기</span>');
    return `<h2 class="section">관리자 휴대폰<span class="count">${p.devices.length}</span></h2>
      <p class="hint">관리자가 관리 › 내 계정·이 기기의 '내 휴대폰 밤낮 연결'로 만든 코드를 앱에 넣으면 여기에 나와요. 승인하면 그 휴대폰은 프로를 무료로 써요. 한 사람당 ${p.perAdmin}대, 관리자를 지우면 같이 끊겨요.</p>
      ${p.devices.length ? `<ul class="rows">${p.devices.map((d) => `<li class="item">
        <div class="row-main"><b>${esc(d.adminName)}의 휴대폰</b><small>${esc(ago(d.createdAt))} 연결${d.seenAt ? ` · 마지막 확인 ${esc(ago(d.seenAt))}` : ""}</small></div>
        ${pill(d)}
        <div class="actions">${d.status === "approved" ? "" : `<button class="btn small primary" data-act="phone" data-id="${d.id}" data-key="approve">승인</button>`}
          <button class="btn small danger" data-act="phone" data-id="${d.id}" data-key="remove">끊기</button></div></li>`).join("")}</ul>`
        : '<p class="hint">연결된 휴대폰이 없어요.</p>'}`;
  }

  /**
   * S 1.32 [SECX-01] 휴대폰 미리 보기: which flags an approved admin phone sees before everyone. Only here, by role
   * (the owner for any phone, 기능 관리 바꾸기 for one's own); the phone app shows and changes nothing about flags.
   */
  function previewCard() {
    const p = S.data.myPhones;
    if (!p || !p.phones.length) return "";
    const names = new Map(p.flags.map((f) => [f.key, f.name]));
    return `<section class="card"><h2>휴대폰 미리 보기</h2>
      <p class="hint">켜기 전인 기능을 이 휴대폰에만 먼저 보여요. 사용자 휴대폰에는 기능 상태나 스위치가 보이지 않고, 여기서만 바꿔요. 바뀐 것은 그 휴대폰이 다음에 이용권을 확인할 때(앱을 열 때) 반영돼요.${p.edit ? "" : " 바꾸려면 기능 관리 ‘바꾸기’ 권한이 있어야 해요."}</p>
      <ul class="rows">${p.phones.map((d) => `<li class="item">
        <div class="row-main"><b>${esc(d.adminName)}의 휴대폰${d.mine ? " (내 휴대폰)" : ""}</b>
          <small>${d.status === "approved" ? "" : "승인 대기 · "}${d.previews.length ? esc(d.previews.map((k) => names.get(k) ?? k).join(", ")) : "미리 보기 없음"}</small></div>
        ${p.edit ? `<div class="actions"><button class="btn small" data-act="phone-previews" data-id="${d.id}">고르기</button></div>` : ""}</li>`).join("")}</ul></section>`;
  }

  function openPreviews(id) {
    const p = S.data.myPhones;
    const d = p?.phones.find((x) => x.id === id);
    if (!d) return;
    const on = new Set(d.previews);
    openDialog(`<form data-form="phone-previews" data-id="${d.id}"><h2>${esc(d.adminName)}의 휴대폰 미리 보기</h2>
      <p class="hint">고른 기능은 조건이 아직 안 맞아도 이 휴대폰에서만 켜져요. 법·처리방침 조건이 있는 기능(LEGAL)은 미리 보기로도 서버에 정보를 보내지 않아요.</p>
      <div class="radios">${p.flags.map((f) => `<label class="radio"><input type="checkbox" name="keys" value="${esc(f.key)}"${on.has(f.key) ? " checked" : ""}>
        <span>${esc(f.name)}</span></label>`).join("")}</div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`);
  }

  /** Area by area: nothing, a look, or changes too; presets fill the table in one tap. */
  function permEditor(perms, d) {
    const presets = Object.entries(d.presets ?? {});
    return `<p class="label">빠르게 고르기</p>
      <div class="chips" role="group" aria-label="역할">${presets.map(([key, p]) => `<button type="button" class="chip pick" data-act="preset" data-key="${key}">${esc(p.name)}</button>`).join("")}</div>
      <p class="label">영역별 권한</p>
      <div class="perm-table">${Object.entries(d.areas).map(([area, label]) => {
        const yesNo = (d.toggles ?? toggles()).includes(area);
        const levels = yesNo ? ["none", "edit"] : ["none", "view", "edit"];
        const cur = levels.includes(perms[area]) ? perms[area] : "none";
        // [ADMUX-03] A radio group: one stop for Tab, the arrow keys move between levels.
        return `<div class="perm"><b id="perm-l-${area}">${esc(label)}</b>
        <span class="levels" role="radiogroup" aria-labelledby="perm-l-${area}">${levels.map((l) => `<button type="button" data-act="level" data-area="${area}"
          data-level="${l}" role="radio" aria-checked="${cur === l}" aria-pressed="${cur === l}" tabindex="${cur === l ? 0 : -1}"${l === "edit" && d.viewOnly?.includes(area) ? " disabled" : ""}>${yesNo ? (l === "edit" ? "줌" : "없음") : LEVEL_NAMES[l]}</button>`).join("")}</span>
        <input type="hidden" name="perm-${area}" value="${cur}"></div>`;
      }).join("")}</div>
      <p class="help">보기는 화면을 보기만 하고, 고치기는 채택·답하기 같은 바꾸는 일까지 해요. 개발사항 처리는 개발중·완료·반려를 정하고 여러 항목을 한 번에 처리하는 권한이에요. 기능 관리 고치기는 법적이 아닌 기능의 조건을 바꾸고 확인하는 권한, 기능 강제로 끄기는 사고 때 기능을 바로 끄는 예/아니요 권한이에요(처음에는 최고 관리자만). 강제로 켜기와 결제·광고 같은 법적 확인은 최고 관리자만 해요. 관리자 관리는 최고 관리자만 해요.</p>`;
  }

  const readPermForm = (form, d) => Object.fromEntries(Object.keys(d.areas).map((a) => [a, form.elements[`perm-${a}`]?.value ?? "none"]));

  function openInvite(prefill) {
    const d = S.data.admins;
    const perms = prefill?.perms ?? d.presets.reviewer.perms;
    openDialog(`<form data-form="invite"><h2>관리자 초대</h2>
      <p>초대한 메일로만 계정을 만들 수 있어요. 가입한 뒤에도 승인해야 로그인돼요.</p>
      <div class="fieldset"><label for="inv-email">메일</label><input id="inv-email" name="email" type="email" autocomplete="off" inputmode="email"
        value="${esc(prefill?.email ?? "")}" required${prefill?.email ? " readonly" : ""}></div>
      <div class="fieldset"><label for="inv-note">메모 (선택)</label><input id="inv-note" name="note" maxlength="100" value="${esc(prefill?.note ?? "")}" placeholder="예: 검토 도와줄 민지"></div>
      ${permEditor(perms, d)}
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">초대 링크 만들기</button></div></form>`, "wide");
  }

  function openInviteLink(r) {
    const text = `밤낮 관리자로 초대해요.\n\n아래 링크를 휴대폰에서 열어 계정을 만들고 앱을 설치해 주세요. 이 링크는 ${r.email}에서만 쓸 수 있고 ${day(r.expiresAt)}까지 열려요.\n\n${r.link}`;
    const mailto = `mailto:${encodeURIComponent(r.email)}?subject=${encodeURIComponent("[밤낮 관리자] 관리자 초대")}&body=${encodeURIComponent(text)}`;
    S.inviteText = text;
    S.inviteLink = r.link;
    openDialog(`<div class="box"><h2>초대 링크가 생겼어요</h2>
      <p>${esc(r.email)}로 보내 주세요. 링크는 지금 한 번만 보여요. 잃어버리면 새 링크를 만들면 돼요.</p>
      <div class="fieldset"><label for="inv-link">초대 링크</label><input id="inv-link" type="url" readonly value="${esc(r.link)}"></div>
      <div class="actions start"><a class="btn primary" href="${esc(mailto)}">${icon(P.mail)}메일로 보내기</a>
        <button class="btn" data-act="copy-invite">${icon(P.copy)}복사</button>
        ${navigator.share ? `<button class="btn" data-act="share-invite">${icon(P.share)}공유</button>` : ""}</div>
      <p class="note info">메일 앱으로 보내야 그 메일함을 가진 분만 링크를 받아요. 메신저로 보내면 메일 확인이 되지 않아요${r.mail ? " (이 서버는 가입할 때 메일로 확인 코드도 보내요)" : ""}.</p>
      <div class="actions"><button class="btn" data-act="close">닫기</button></div></div>`, "wide");
  }

  function openAdminEdit(id, approve) {
    const d = S.data.admins;
    const a = d.admins.find((x) => x.id === id);
    if (!a) return;
    openDialog(`<form data-form="admin-perms" data-id="${id}" data-approve="${approve ? 1 : ""}"><h2>${approve ? `${esc(a.name)}님을 승인할까요?` : `${esc(a.name)}님의 권한`}</h2>
      <p>${esc(a.email)}${approve ? " · 승인하면 바로 로그인할 수 있어요. 초대할 때 고른 권한으로 시작해요." : " · 바꾸면 다음 화면부터 바로 적용돼요."}</p>
      ${permEditor(a.perms, d)}
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">${approve ? "승인" : "저장"}</button></div></form>`, "wide");
  }

  // ---- activity [ADMIN-17] ----

  function auditView() {
    const d = S.data.audit;
    const f = S.audit;
    const actors = d?.actors ?? [];
    const filters = `<div class="toolbar" role="group" aria-label="활동 기록 거르기">
        <label class="sr" for="au-actor">누가</label><select id="au-actor" data-change="audit-actor"><option value="">모든 사람</option><option value="me"${f.actor === "me" ? " selected" : ""}>나</option>
          ${actors.map((a) => `<option value="${esc(a.id)}"${f.actor === a.id ? " selected" : ""}>${esc(a.name)}</option>`).join("")}</select>
        <label class="sr" for="au-kind">종류</label><select id="au-kind" data-change="audit-kind"><option value="">모든 일</option>
          ${(d?.kinds ?? Object.keys(AUDIT_KIND_NAMES)).map((k) => `<option value="${k}"${f.kind === k ? " selected" : ""}>${esc(AUDIT_KIND_NAMES[k] ?? k)}</option>`).join("")}</select>
        <label class="sr" for="au-from">부터</label><input id="au-from" type="date" value="${esc(f.from)}" data-change="audit-from" aria-label="이날부터">
        <label class="sr" for="au-to">까지</label><input id="au-to" type="date" value="${esc(f.to)}" data-change="audit-to" aria-label="이날까지">
        ${f.actor || f.kind || f.from || f.to ? '<button class="btn small ghost" data-act="audit-clear">거르기 풀기</button>' : ""}
        <button class="btn small" data-act="audit-csv"${d?.entries?.length ? "" : " disabled"}>${icon(P.install)}CSV로 받기</button></div>`;
    if (!d) return filters + skeleton(3);
    if (!d.entries.length) return filters + empty(f.actor || f.kind || f.from || f.to ? "고른 조건에 맞는 활동이 없어요" : "아직 활동이 없어요", "채택, 답하기, 로그인 같은 일이 여기 남아요. 1년 지난 기록은 지워져요.",
      f.actor || f.kind || f.from || f.to ? '<button class="btn" data-act="audit-clear">조건 지우기</button>' : "");
    let last = "";
    const out = [];
    for (const e of d.entries) {
      const dd = new Date(e.at).toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" });
      if (dd !== last) {
        if (last) out.push("</ul></section>");
        out.push(`<h2 class="day-head">${esc(dd)}</h2><section class="card"><ul class="feed">`);
        last = dd;
      }
      const about = e.label ? (e.link ? ` <a class="meta" href="${esc(e.link)}">${esc(e.label)}</a>` : ` <span class="meta">${esc(e.label)}</span>`)
        : e.target ? ` <span class="meta">${esc(e.target)}</span>` : "";
      const who = e.actorId ? `<button class="who link" data-act="audit-who" data-id="${esc(e.actorId)}" title="이 사람이 한 일만 보기">${esc(e.actor || "관리자")}</button>`
        : `<span class="who">${esc(e.actor || "앱 사용자·시스템")}</span>`;
      out.push(`<li><span class="when">${esc(clock(e.at))}</span><span><b>${esc(ACTION_NAMES[e.action] ?? e.action)}</b>${about}
        ${e.detail ? `<span class="meta">${esc(e.detail)}</span>` : ""}</span>${who}</li>`);
    }
    out.push("</ul></section>");
    return `${filters}<p class="hint">누가 언제 무엇을 했는지 남아요. 이름을 누르면 그 사람이 한 일만 보여요. 1년 지난 기록은 지워져요.</p>${out.join("")}
      ${d.more ? '<div class="actions start"><button class="btn wide" data-act="audit-more">더 보기</button></div>' : ""}`;
  }

  // ---- [SRV-04][ADMIN-14] 운영 ----

  const NOTICE_STATE = { on: ["ok", "보이는 중"], later: ["info", "예정"], ended: ["", "끝남"] };
  const kstDate = (iso) => new Date(new Date(iso).getTime() + 9 * 3600e3).toISOString().slice(0, 10);

  /** AN 1.28 [ADMUX-32] 출시 › 이달 할 일 (1.27 운영's first card). */
  function choresView() {
    const o = S.data.ops;
    const edit = can("ops", "edit");
    if (!o) return skeleton(2);
    const [y, m] = o.month.split("-").map(Number);
    const left = o.items.filter((i) => !i.done).length;
    return `<section class="card"><div class="card-head"><h2>${y}년 ${m}월에 할 일</h2>
        <div class="actions start"><button class="icon-btn" data-act="ops-month" data-d="-1" aria-label="지난달">${icon(P.back)}</button>
        <button class="icon-btn" data-act="ops-month" data-d="1" aria-label="다음 달">${icon(P.chevron)}</button></div></div>
      <p class="hint">해마다 이 달에 하는 일이에요. ${left ? `${left}개 남았어요.` : "모두 마쳤어요."} 근거는 docs/ops-calendar.md에 있어요.</p>
      <ul class="rows">${o.items.map((i) => `<li><div class="row"><span class="row-main"><b class="wrap" title="${esc(i.text)}">${esc(i.text)}</b><small>${esc(i.source)}</small></span>
        <span class="row-side">${edit ? `<button class="btn small${i.done ? "" : " primary"}" data-act="ops-tick" data-key="${i.key}" data-done="${i.done ? "" : "1"}">${i.done ? "되돌리기" : "마침"}</button>`
          : `<span class="pill ${i.done ? "ok" : ""}">${i.done ? "마침" : "남음"}</span>`}</span></div></li>`).join("")}</ul></section>`;
  }

  /** AN 1.28 [ADMUX-32] 출시 › 앱 공지 (1.27 운영's second card). */
  function noticesView() {
    const n = S.data.notices;
    const edit = can("ops", "edit");
    if (!n) return skeleton(2);
    return `<section class="card"><div class="card-head"><h2>앱 공지</h2>${edit ? `<button class="btn small primary" data-act="notice-new">${icon(P.plus)}새 공지</button>` : ""}</div>
      <p class="hint">앱 달력 위에 한 줄로 보이고, 누르면 내용이 열려요. 사용자가 닫으면 그 공지는 다시 보이지 않아요. 앱은 하루에 한 번, 그리고 열 때 새로 받아요.</p>
      ${n.notices.length ? `<ul class="rows">${n.notices.map((x) => {
        const [tone, label] = NOTICE_STATE[x.state] ?? ["", x.state];
        const range = x.minApp || x.maxApp ? ` · 앱 ${x.minApp ?? "처음"}~${x.maxApp ?? "최신"}` : "";
        return `<li><div class="row"><span class="row-main"><b>${x.level === "important" ? '<span class="pill bad">중요</span> ' : ""}${esc(x.title)}</b>
          <small>${esc(kstDate(x.from))} ~ ${esc(kstDate(x.until))}${esc(range)}</small><small>${esc(x.body)}</small></span>
          <span class="row-side"><span class="pill ${tone}">${label}</span>${edit ? `<button class="btn small" data-act="notice-edit" data-id="${x.id}">고치기</button>
            <button class="btn small" data-act="notice-remove" data-id="${x.id}">내리기</button>` : ""}</span></div></li>`;
      }).join("")}</ul>` : '<p class="meta">지금 올린 공지가 없어요.</p>'}</section>`;
  }

  function openNotice(x) {
    const now = new Date();
    const from = x ? kstDate(x.from) : kstDate(now.toISOString());
    const until = x ? kstDate(new Date(new Date(x.until).getTime() - 1).toISOString()) : kstDate(new Date(now.getTime() + 7 * 864e5).toISOString());
    openDialog(`<form data-form="notice" data-id="${x?.id ?? ""}"><h2>${x ? "공지 고치기" : "새 공지"}</h2>
      <p>이름·연락처·금액은 적지 않아요. 앱을 쓰는 모두에게 보여요.</p>
      <div class="fieldset"><label for="nt-title">제목</label><input id="nt-title" name="title" maxlength="40" required value="${esc(x?.title ?? "")}" placeholder="예: 10월 25일 새벽 서버 점검"></div>
      <div class="fieldset"><label for="nt-body">내용</label><textarea id="nt-body" name="body" maxlength="500" required>${esc(x?.body ?? "")}</textarea></div>
      <div class="radios"><label class="radio"><input type="radio" name="level" value="info"${x?.level === "important" ? "" : " checked"}><span>알림</span></label>
        <label class="radio"><input type="radio" name="level" value="important"${x?.level === "important" ? " checked" : ""}><span>중요(색을 넣어 보여요)</span></label></div>
      <div class="fieldset"><label for="nt-from">시작(한국 시간 0시)</label><input id="nt-from" name="from" type="date" required value="${from}"></div>
      <div class="fieldset"><label for="nt-until">마지막 날(그날 밤까지, 90일 안)</label><input id="nt-until" name="until" type="date" required value="${until}"></div>
      <div class="fieldset"><label for="nt-min">앱 버전 번호부터(선택)</label><input id="nt-min" name="minApp" type="number" min="1" value="${x?.minApp ?? ""}">
        <span class="help">특정 버전에만 보일 때. ${S.data.notices?.latestApp ? `최근 문의에 온 가장 새 앱은 ${esc(S.data.notices.latestApp.name)}(번호 ${S.data.notices.latestApp.code})예요.` : "앱 설정 › 앱 정보의 괄호 안 숫자예요."}</span></div>
      <div class="fieldset"><label for="nt-max">앱 버전 번호까지(선택)</label><input id="nt-max" name="maxApp" type="number" min="1" value="${x?.maxApp ?? ""}"></div>
      <p class="label">앱에서 보이는 모양</p><div class="notice-preview" id="nt-preview" aria-live="polite">${noticePreview(x?.title ?? "", x?.body ?? "", x?.level)}</div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">${x ? "저장" : "올리기"}</button></div></form>`);
  }

  /** [ADMUX-07] One notice line as the phone shows it: the title, the first lines of the text, 중요 in colour. */
  function noticePreview(title, body, level) {
    return `<div class="np${level === "important" ? " important" : ""}"><b>${esc(title.trim() || "제목")}</b><span>${esc(body.trim() || "내용이 여기에 보여요")}</span></div>`;
  }

  // ---- [ADMUX-17] 받은함: AN 1.28 [ADMUX-33] now 오늘's 할 일 ----

  const INBOX_NAMES = { inquiry: "문의", group: "제보", ask: "앱에 없는 방식", doubted: "달라요 달린 규칙", hidden_post: "신고로 가려진 글", flagged_post: "신고된 글",
    rate: "임금표", dev: "개발사항 접수", rewards: "보상", ops: "이달 운영", admin: "승인 대기 관리자", phone: "관리자 휴대폰" };
  const SNOOZE = "bamnat-admin-snooze";

  /** [ADMUX-17] Items put off on this device until the next morning: { "type:id": until }. */
  function snoozed() {
    try {
      const all = JSON.parse(localStorage.getItem(SNOOZE) ?? "{}");
      const now = Date.now();
      return Object.fromEntries(Object.entries(all).filter(([, until]) => until > now));
    } catch {
      return {};
    }
  }

  function inboxLink(x) {
    if (x.type === "inquiry") return href("inquiries", x.id);
    if (x.type === "group") return L.routeHash("review", null, { show: "groups" });
    if (x.type === "ask") return L.routeHash("review", null, { show: "asks" });
    if (x.type === "doubted") return L.routeHash("review", null, { show: "flagged" });
    if (x.type === "hidden_post") return L.routeHash("community", null, { show: "hidden" });
    if (x.type === "flagged_post") return L.routeHash("community", null, { show: "flagged" });
    if (x.type === "rate") return L.routeHash("community", null, { show: "rates" });
    if (x.type === "dev") return href("dev", x.id);
    if (x.type === "rewards") return href("rewards");
    if (x.type === "ops") return href("chores");
    return href("admins");
  }

  function dueText(x, now) {
    if (!x.due) return "";
    const left = Math.round((Date.parse(x.due) - now) / 60000);
    const t = L.slaText(left);
    return `<span class="pill ${t.tone}">${esc(left < 0 ? t.text.replace("목표", "기한") : `기한 ${t.text}`)}</span>`;
  }

  // ---- [SRV-05] 공유 링크 신고 ----

  const SHARE_REASONS = { personal: "다른 사람의 개인정보", abuse: "욕설·괴롭힘", spam: "광고·스팸", illegal: "불법 정보", other: "그 밖의 문제" };

  // [RCP-39] 1.34 나눔 판.
  // 1.35 [RCX-28] 첫 3개 검토 is gone: 신고됨 comes first; 검토 대기 holds only what waited before (and goes when empty).
  const RECIPE_TABS = [["flagged", "신고됨"], ["visible", "새로 올라온 것"], ["hidden", "가린 것"], ["queue", "전에 기다리던 것"], ["official", "공식·공개 자료"],
    ["showcase", "보여주기"], ["picks", "이번 주 고른 것"], ["images", "그림 검토"], ["requests", "요청 판"], ["makers", "프로필 신고"], ["month", "이달의 제작자"]];
  const RECIPE_STYLES = [["ALL", "모두"], ["SHIFT", "교대"], ["DAY", "일근"], ["HOURLY", "알바"], ["FIELD", "현장"], ["BUSINESS", "자영업"], ["INVEST", "투자"]];
  const RECIPE_FLAGS = { personal: "개인 정보", fake: "사칭·거짓", harm: "해롭거나 불쾌", spam: "광고·도배", copy: "베낌", other: "그 밖" };
  const RECIPE_PIECES = { shiftLook: "근무 꾸미기", calTheme: "달력 테마", alarms: "알람 묶음", memoTpl: "메모 틀", hourly: "알바 근무지 규칙",
    field: "현장 단가표 틀", shop: "가게 영업 틀", invest: "투자 원칙·일지 틀", company: "근무 주기·급여 방식",
    bundle: "묶음", layout: "화면 배치", cards: "카드 순서", widget: "위젯 모양", photoLook: "그림 꾸미기", calView: "달력 모양", appFeel: "앱 동작",
    memoLook: "메모장 모양", moneyLook: "가계부 모양", screenLook: "화면 색·배경" };
  const RECIPE_ACTS = { approve: "올림", return: "돌려줌", hide: "가림", show: "다시 보임", delete: "지움", official: "공식", showcase: "보여주기", auto: "자동 가림" };

  /** What a recipe holds, in words: its pieces and the 받는 사람이 정할 칸 (never run, only read). */
  function recipeParts(body) {
    try {
      const r = JSON.parse(body);
      return `${(r.pieces ?? []).map((p) => esc(RECIPE_PIECES[p.kind] ?? p.kind)).join(" · ")}${r.inputs?.length ? ` · 정할 칸 ${r.inputs.length}개` : ""}`;
    } catch { return ""; }
  }

  function recipesView() {
    const d = S.data.recipes;
    const m = S.data.recipeMonth;
    const edit = can("community", "edit");
    const tab = S.recipeTab ?? "flagged";
    const chips = `<div class="chips" role="group" aria-label="보기">${RECIPE_TABS.map(([k, label]) =>
      `<button type="button" class="chip pick" data-act="recipe-tab" data-key="${k}" aria-pressed="${tab === k}">${esc(label)}</button>`).join("")}</div>`;
    const month = m ? `<p class="hint">${esc(m.month)} · 새 레시피 ${m.made}개 · ${Object.entries(m.actions ?? {}).map(([k, n]) => `${esc(RECIPE_ACTS[k] ?? k)} ${n}`).join(" · ") || "처리 없음"}</p>` : "";
    if (!d || (d.tab ?? tab) !== tab) return chips + skeleton(2);
    if (tab === "picks") return chips + recipePicksView(d, edit);
    if (tab === "images") return chips + recipeImagesView(d, edit);
    if (tab === "requests") return chips + recipeRequestsView(d, edit);
    if (tab === "makers") return chips + recipeMakersView(d, edit);
    if (tab === "month") return chips + recipeMonthView(d, edit);
    const help = {
      queue: "1.35부터 레시피는 바로 광장에 올라가요. 그 전에 검토를 기다리던 것만 여기 남아 있어요. 올리거나 고칠 점을 적어 돌려줘요.",
      flagged: "신고가 들어온 레시피예요. 신고가 3건 쌓이면 저절로 가려지고 여기서 다시 보일지 정해요.",
      hidden: "가린 레시피는 90일 뒤 지워져요.",
      visible: "광장에 보이는 레시피예요. 새로 올라온 것이 위에 와요. 이상한 것은 가리기로 내려요.",
      official: "운영자가 올린 시작 레시피와 공공 자료 레시피예요.",
      // [RCX-32]
      showcase: "장터로 이런 것까지 된다는 걸 보여 주는 레시피예요. 근무 유형마다 마을 › 장터 맨 위 ‘이런 것도 만들 수 있어요’에 크게 보여요. 아래에서 showcase-recipes.json을 붙여 넣거나, 장터의 좋은 레시피를 ‘보여주기에 넣기’로 더해요.",
    }[tab];
    const rows = d.items.length ? `<ul class="rows">${d.items.map((r) => `<li><div class="row"><span class="row-main">
        <b>${esc(r.cover?.emoji ?? "")} ${esc(r.title)}</b> <small>${esc(r.code)} · v${r.version} · ${esc(r.nick ?? "이름 없음")}${r.late ? ' · <span class="pill warn">하루 넘음</span>' : ""}</small>
        ${r.summary ? `<small>${esc(r.summary)}</small>` : ""}<small>${recipeParts(r.body)}</small>
        <small>좋아요 ${r.likes} · 안좋아요 ${r.dislikes} · 쓰는 분 ${r.users}${r.flags?.length ? ` · 신고 ${r.flags.map((f) => `${esc(RECIPE_FLAGS[f.reason] ?? f.reason)} ${f.n}`).join(", ")}` : ""}</small>
        ${r.note ? `<small>돌려준 메모: ${esc(r.note)}</small>` : ""}${r.hidden ? `<small>가린 이유: ${esc(r.hidden)}</small>` : ""}
        <details><summary>본문 보기</summary><pre class="mono">${esc(JSON.stringify(JSON.parse(r.body), null, 1))}</pre></details></span>
        ${edit ? `<span class="row-side">${r.state === "pending" ? `<button class="btn small primary" data-act="recipe-do" data-id="${r.code}" data-key="approve">올리기</button>
          <button class="btn small" data-act="recipe-return" data-id="${r.code}">돌려주기</button>` : ""}
          ${r.state === "hidden" ? `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="show">다시 보이기</button>` : `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="hide">가리기</button>`}
          ${r.official ? "" : `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="official">공식 표시</button>`}
          ${r.state === "visible" && r.scope === "public" ? `<button class="btn small" data-act="recipe-showcase" data-id="${r.code}" data-key="${r.showcase ? "off" : "on"}">${r.showcase ? "보여주기에서 빼기" : "보여주기에 넣기"}</button>` : ""}
          ${r.maker && r.maker !== "operator" ? `<button class="btn small" data-act="recipe-helper" data-id="${esc(r.maker)}">도우미 배지</button>` : ""}
          <button class="btn small danger" data-act="recipe-delete" data-id="${r.code}">지우기</button></span>` : ""}</div></li>`).join("")}</ul>`
      : empty("여기 있는 레시피가 없어요", tab === "queue" ? "검토를 기다리는 레시피가 없어요." : "");
    const upload = edit && (tab === "official" || tab === "showcase") ? `<form data-form="recipe-upload" class="card"><h3>${tab === "showcase" ? "보여주기 레시피 올리기" : "시작 레시피·공개 자료 올리기"}</h3>
      <p class="hint">앱의 ‘올라갈 내용 전부 보기’에 나오는 레시피 글(JSON)이나, 시작 레시피 파일(starter-recipes.json) 전체를 붙여 넣어요. 공공 자료라면 설명에 출처(기관·자료 이름·날짜)를 꼭 적어요. 공공누리 4유형 자료는 쓰지 않아요.</p>
      <textarea name="recipe" rows="8" required></textarea>
      ${tab === "showcase" ? `<p class="hint">showcase-recipes.json 전체를 붙여 넣으면 30개가 한 번에 올라가요. 하나만 붙여 넣어도 보여주기로 올라가요.</p><input type="hidden" name="showcase" value="on">`
        : `<label class="switch"><input type="checkbox" name="publicData"><span class="track" aria-hidden="true"></span><span>공공 자료 레시피 (공개 자료 탭)</span></label>`}
      <div class="actions"><button class="btn primary" type="submit">올리기</button></div></form>` : "";
    const log = S.recipeLog ? (S.data.recipeLog ? `<h3>처리 기록</h3><ul class="rows">${S.data.recipeLog.items.slice(0, 100).map((a) =>
      `<li><div class="row"><span class="row-main"><b>${esc(RECIPE_ACTS[a.action] ?? a.action)}</b> <small>${esc(a.code)} · ${esc(a.actor ?? "")} · ${esc(ago(a.at))}${a.reason ? ` · ${esc(a.reason)}` : ""}</small></span></div></li>`).join("")}</ul>` : skeleton(1))
      : `<button class="btn" data-act="recipe-log">처리 기록 보기</button>`;
    return `${chips}${month}<p class="hint">${esc(help)}</p>${rows}${upload}${log}`;
  }

  /** [RCX-08] 1.35 이번 주 고른 것: up to three codes for each kind of work; last week's stay until a new pick. */
  function recipePicksView(d, edit) {
    const by = (k) => d.items.filter((i) => i.style === k).map((i) => i.code);
    return `<p class="hint">${esc(d.week)} 주. 근무 유형마다 레시피 3개까지 골라 광장 맨 위에 보여요. 새로 고르기 전까지 지난주 것이 그대로 보여요. 광장에 보이는 레시피의 코드(8자)를 넣어요.</p>
      <ul class="rows">${RECIPE_STYLES.map(([k, label]) => `<li><form data-form="recipe-picks" data-key="${k}" class="row">
        <span class="row-main"><b>${esc(label)}</b><small>${by(k).length ? esc(by(k).join(", ")) : "이번 주 고른 것 없음"}</small>
        ${edit ? `<input name="codes" aria-label="${esc(label)} 레시피 코드" placeholder="코드 3개까지, 쉼표로" value="${esc(by(k).join(", "))}">` : ""}</span>
        ${edit ? `<span class="row-side"><button class="btn small primary" type="submit">저장</button></span>` : ""}</form></li>`).join("")}</ul>`;
  }

  /** [RCX-15] 1.35 그림 표지: shown to others only after a look here. */
  function recipeImagesView(d, edit) {
    if (!d.items.length) return empty("볼 그림이 없어요", "레시피 표지로 올린 그림이 여기 와요. 본 그림만 다른 분께 보여요.");
    return `<p class="hint">사람 얼굴·개인정보·광고·남의 저작물이 보이면 가려요. 괜찮으면 보이기를 눌러요.</p>
      <ul class="rows">${d.items.map((i) => `<li><div class="row"><span class="row-main">
        <img src="data:image/webp;base64,${esc(i.data)}" alt="레시피 표지 그림" width="128" height="128" class="cover-img">
        <small>${esc(i.id)} · ${Math.round(i.bytes / 1024)}KB · ${esc(ago(i.at))}</small></span>
        ${edit ? `<span class="row-side"><button class="btn small primary" data-act="recipe-image" data-id="${i.id}" data-key="ok">보이기</button>
          <button class="btn small danger" data-act="recipe-image" data-id="${i.id}" data-key="hide">가리기</button></span>` : ""}</div></li>`).join("")}</ul>`;
  }

  /** [PRF-06] 1.36 프로필 신고: 사칭·나쁜 소개. 3건이면 소개가 먼저 가려져요. Who reported is never shown. */
  function recipeMakersView(d, edit) {
    if (!d.items.length) return empty("신고된 프로필이 없어요", "앱의 프로필 › ⋮ › 신고하기로 들어온 것이 여기 와요.");
    const why = { impersonate: "사칭", bio: "나쁜 소개", spam: "광고·도배", other: "그 밖의 것" };
    return `<p class="hint">소개만 문제면 소개를 가리고, 사칭이면 프로필을 닫아요. 닫힌 프로필은 다른 분이 볼 수도 팔로우할 수도 없고, 작품은 장터에 남아요(작품은 레시피 탭에서 따로 가려요). 문제없으면 신고를 비워요.</p>
      <ul class="rows">${d.items.map((m) => `<li><div class="row"><span class="row-main"><b>${esc(m.face ?? "🙂")} ${esc(m.nick ?? "이름 없음")}</b>
        <small>${esc(m.maker)} · 신고 ${m.flags}건 · ${Object.entries(m.reasons ?? {}).map(([k, n]) => `${esc(why[k] ?? k)} ${n}`).join(", ")} · ${esc(ago(m.at))}</small>
        <small>소개: ${m.bio ? esc(m.bio) : "없음"}${m.bioHidden ? ` <span class="pill warn">${m.bioHiddenBy === "operator" ? "운영자가 소개 가림" : "신고로 소개 가림"}</span>` : ""}${m.closed ? ' <span class="pill warn">프로필 닫힘</span>' : ""}</small></span>
        ${edit ? `<span class="row-side">
          <button class="btn small" data-act="recipe-maker" data-id="${esc(m.maker)}" data-key="${m.bioHidden ? "bio-show" : "bio-hide"}">${m.bioHidden ? "소개 다시 보이기" : "소개 가리기"}</button>
          <button class="btn small ${m.closed ? "" : "danger"}" data-act="recipe-maker" data-id="${esc(m.maker)}" data-key="${m.closed ? "open" : "close"}">${m.closed ? "프로필 다시 열기" : "프로필 닫기"}</button>
          <button class="btn small" data-act="recipe-maker" data-id="${esc(m.maker)}" data-key="clear">신고 비우기</button></span>` : ""}</div></li>`).join("")}</ul>`;
  }

  /** [PRF-10] 1.36 이달의 제작자: up to three maker ids for each kind of work; with none, the most put-on makers of the month show. */
  function recipeMonthView(d, edit) {
    const by = (k) => d.items.filter((i) => i.style === k).map((i) => i.maker);
    return `<p class="hint">${esc(d.month)}. 근무 유형마다 3명까지 골라 마을 맨 위(팔로우한 분이 없는 분)에 보여요. 비워 두면 이달에 가장 많이 넣은 작품의 만든 분이 저절로 보여요. 프로필 id(12자)는 레시피 탭의 ‘도우미 배지’ 옆이나 앱의 프로필 링크 끝에 있어요.</p>
      <ul class="rows">${RECIPE_STYLES.map(([k, label]) => `<li><form data-form="recipe-month" data-key="${k}" class="row">
        <span class="row-main"><b>${esc(label)}</b><small>${by(k).length ? esc(by(k).join(", ")) : "고른 분 없음 (저절로)"}</small>
        ${edit ? `<input name="makers" aria-label="${esc(label)} 제작자 id" placeholder="프로필 id 3개까지, 쉼표로" value="${esc(by(k).join(", "))}">` : ""}</span>
        ${edit ? `<span class="row-side"><button class="btn small primary" type="submit">저장</button></span>` : ""}</form></li>`).join("")}</ul>`;
  }

  /** [RCX-04] 1.35 요청 판: what people asked to be made; hide one that breaks the rules. Who wrote it is never shown. */
  function recipeRequestsView(d, edit) {
    if (!d.items.length) return empty("요청이 없어요", "앱의 ‘이런 레시피 있었으면’에서 올린 요청이 여기 보여요.");
    const states = { waiting: "기다리는 중", making: "만드는 중", done: "완성" };
    const style = Object.fromEntries(RECIPE_STYLES);
    return `<ul class="rows">${d.items.map((q) => `<li><div class="row"><span class="row-main"><b>${esc(q.body)}</b>
        <small>${esc(style[q.style] ?? q.style)} · ${esc(states[q.state] ?? q.state)} · 좋아요 ${q.likes}${q.code ? ` · ${esc(q.code)}` : ""} · ${esc(ago(q.at))}${q.hidden ? ' · <span class="pill warn">가림</span>' : ""}</small></span>
        ${edit ? `<span class="row-side"><button class="btn small" data-act="recipe-request" data-id="${q.id}" data-key="${q.hidden ? "show" : "hide"}">${q.hidden ? "다시 보이기" : "가리기"}</button></span>` : ""}</div></li>`).join("")}</ul>`;
  }

  function sharesView() {
    const d = S.data.shares;
    if (!d) return skeleton(2);
    if (!d.connected) return empty("공유 서버가 연결되지 않았어요", "Worker 설정에 SHARE_BASE_URL과 SHARE_ADMIN_TOKEN을 넣으면 신고된 근무표 링크가 여기 보여요.");
    if (!d.reports.length) return empty("신고된 링크가 없어요", "근무표 링크 페이지와 앱의 받은 달력에서 신고하면 여기 모여요.");
    const edit = can("shares", "edit");
    return `<p class="hint">링크를 열어 확인하고, 운영 규칙에 어긋나면 내려요. 내린 링크는 근무표가 바로 지워지고 "운영 규칙에 따라 내려졌어요"로 보여요. 신고한 사람은 남지 않아요.</p>
      <ul class="rows">${d.reports.map((r) => `<li><div class="row"><span class="row-main"><b>신고 ${r.count}건</b>
        <small>${Object.entries(r.reasons ?? {}).map(([k, n]) => `${esc(SHARE_REASONS[k] ?? k)} ${n}`).join(" · ")}</small><small>마지막 신고 ${esc(ago(r.last))}</small></span>
        <span class="row-side"><a class="btn small" href="${esc(r.url)}" target="_blank" rel="noopener noreferrer">열어 보기</a>
        ${edit ? `<button class="btn small danger" data-act="share-remove" data-id="${esc(r.id)}">내리기</button>` : ""}</span></div></li>`).join("")}</ul>`;
  }

  // ---- settings, account, more ----

  /** What the server still lacks: [h] is /health on the sign-in screen, /server (with the optional services) here. */
  function healthList(h = S.health) {
    if (!h) return "";
    const todo = (name, why, missing) => ({
      short: `${name}가 10자보다 짧아요. 10자 이상으로 다시 넣고 Add variable and deploy를 눌러 주세요`,
      name: `이름이 조금 달라요. 대문자와 밑줄 그대로 ${name}로 다시 넣어 주세요`,
      store: "Secrets Store가 아니라 Worker → 설정 → 런타임 변수에 Secret으로 넣어 주세요",
    })[why] ?? missing;
    const rows = [
      [h.database, "데이터베이스(D1)가 연결돼 있어요", "D1 데이터베이스를 변수 이름 DB로 연결해 주세요 (Worker → 설정 → 바인딩)"],
      [h.password, "최고 관리자 비밀번호가 정해져 있어요", todo("ADMIN_PASSWORD", h.why?.password,
        "Worker → 설정 → 런타임 변수에 ADMIN_PASSWORD를 Secret으로, 10자 이상 넣고 Add variable and deploy를 눌러 주세요")],
      [h.salt, "RATE_SALT가 정해져 있어요", todo("RATE_SALT", h.why?.salt,
        "같은 곳에 RATE_SALT를 Secret으로 넣어 주세요 (아무 긴 글자). 넣은 뒤 이 화면을 새로 고쳐요")],
    ];
    // [ADMIN-15] The optional services only come to the owner (설정 › 서버 상태).
    if ("mail" in h) rows.push([h.mail, "가입 확인 메일을 보내요 (MAIL_API_KEY)", "선택: MAIL_API_KEY가 없으면 초대 링크를 메일로 보내 확인해요"]);
    if ("play" in h) rows.push([h.play, "Play 구매를 서버가 확인해요 (PLAY_SERVICE_ACCOUNT)", "선택: PLAY_SERVICE_ACCOUNT가 없으면 휴대폰의 Play 결제로만 판단해요"]);
    // [SRV-05] Optional: without it the 공유 링크 screen only says it is not connected.
    if ("share" in h) {
      rows.push([h.share, "공유 서버와 연결돼 있어요 (신고된 링크·링크 숫자)",
        "선택: 공유 서버의 신고를 보려면 SHARE_BASE_URL(텍스트)과 SHARE_ADMIN_TOKEN(Secret, 공유 서버와 같은 값)을 넣어 주세요"]);
    }
    // [ADMIN-19]
    if ("push" in h) {
      rows.push([h.push, "관리자 앱 알림(웹 푸시)을 보낼 수 있어요",
        "선택: 새 문의·신고를 알림으로 받으려면 VAPID_PUBLIC_KEY와 VAPID_PRIVATE_KEY를 Secret으로 넣어 주세요 (만드는 법은 README)"]);
    }
    return `<ul class="checks">${rows.map(([ok, good, todo]) =>
      `<li class="${ok ? "ok" : "todo"}"><span aria-hidden="true">${ok ? "✓" : "!"}</span>${esc(ok ? good : todo)}</li>`).join("")}</ul>`;
  }

  const SLA_NAMES = [["slaBug", "문제 신고"], ["slaQuestion", "질문"], ["slaIdea", "건의·제안"], ["slaOther", "그 밖의 이야기"]];

  // AN 1.28 시작
  /** [ADMUX-32] 광장 › 채택 규칙 (1.27 설정's 자동 채택 card): when a setup is adopted by itself and what it earns. */
  function adoptView() {
    const s = S.draft ?? S.overview?.settings ?? { adoptPoints: 3, rewardMonths: 3, adoptAgree: 75 };
    const edit = can("settings", "edit");
    const auto = (s.autoReward ?? 1) ? 1 : 0;
    return `<section class="card">
        <h2>자동 채택</h2>
        ${stepper("adoptPoints", "채택 점수", s.adoptPoints, "점", edit)}
        <p class="hint">같은 규칙을 서로 다른 2명 이상이 보내 이 점수가 되면 자동으로 채택해요. 명세서로 맞춰 본 제보는 2점, 나머지는 1점이에요.</p>
        ${stepper("adoptAgree", "맞았어요 비율", s.adoptAgree ?? 75, "%", edit)}
        <p class="hint">투표한 분 가운데 이만큼이 맞았어요라고 해야 자동으로 채택해요. 모자라면 검토에 남아 개발자가 정해요. 앱의 신뢰도는 이 비율과 점수를 함께 봐요.</p>
        <p class="hint" data-doubt>대표에서 빠지는 기준: 맞아요·달라요가 ${S.overview?.doubt?.votes ?? 5}표 이상이고 달라요가 ${S.overview?.doubt?.share ?? 40}%를 넘으면 앱의 대표 주기에서 빠지고 같은 사업장의 다음 판이 대표가 돼요. 다음 판이 채택 조건을 채우면 지지로 이기지 않아도 채택돼요.</p>
        ${stepper("rewardMonths", "채택 보상", s.rewardMonths, "개월", edit)}
        <p class="hint">채택된 정보를 보낸 분께 드릴 프로 이용 기간이에요. 0개월이면 보상을 기록하지 않아요.</p>
        ${stepper("adoptPersons", "채택에 필요한 사람", s.adoptPersons ?? 3, "명", edit)}
        <p class="hint">점수와 비율을 채워도 서로 다른 휴대폰이 이만큼은 돼야 자동으로 채택해요(결정 D32). 임금표 시세도 같아요.</p>
        ${stepper("amendPersons", "쓰는 규칙 고치기", s.amendPersons ?? 3, "명", edit)}
        <p class="hint">토론의 고치자는 제안이나 개정 제보에 이만큼 맞아요가 모이면 개발자 없이 다음 버전으로 바꿔요. 이전 버전은 이력에 남아요.</p>
        <label class="switch"><input type="checkbox" data-change="auto-reward"${auto ? " checked" : ""}${edit ? "" : " disabled"}><span class="track" aria-hidden="true"></span><span>보상 자동 지급</span></label>
        <p class="hint">켜면 채택될 때 처음 보낸 세 분과 제안한 분께 바로 프로를 드려요. 그 뒤 분들은 관리 › 보상에서 정해요. 투표한 분께는 드리지 않아요.</p>
        ${stepper("rewardCapMonths", "한 사람 상한", s.rewardCapMonths ?? 3, "개월", edit)}
        ${stepper("rewardCapDays", "상한을 세는 기간", s.rewardCapDays ?? 90, "일", edit)}
        <p class="hint">한 휴대폰이 이 기간에 자동으로 받는 보상은 상한까지예요. 넘는 보상은 보상 화면에 남아요.</p>
        ${edit ? `<div class="actions">${S.draft ? '<button class="btn ghost" data-act="settings-undo">되돌리기</button>' : ""}<button class="btn primary" data-act="save-settings"${S.draft ? "" : " disabled"}>${S.draft ? "바꾼 것 저장" : "저장"}</button></div>` : '<p class="note">보기 권한이라 바꿀 수 없어요.</p>'}
      </section>`;
  }

  /** [ADMUX-32] 문의 › 답 목표 (1.27 설정's card): the hours to a first answer, per kind of question. */
  function slaView() {
    const edit = can("settings", "edit");
    const sla = S.slaDraft ?? S.overview?.sla ?? { slaBug: 8, slaQuestion: 24, slaIdea: 72, slaOther: 72 };
    return `<section class="card"><h2>답 목표</h2>
        <p class="hint">문의 종류마다 이 시간 안에 첫 답을 하려고 해요. 목록과 오늘의 남은 시간, 놓친 문의가 이걸 따라요. 1~168시간이에요.</p>
        ${SLA_NAMES.map(([k, label]) => stepper(k, label, sla[k], "시간", edit, "sla-step")).join("")}
        ${edit ? `<div class="actions">${S.slaDraft ? '<button class="btn ghost" data-act="sla-undo">되돌리기</button>' : ""}<button class="btn primary" data-act="save-sla"${S.slaDraft ? "" : " disabled"}>저장</button></div>` : '<p class="note">보기 권한이라 바꿀 수 없어요.</p>'}
      </section>`;
  }

  /** [ADMUX-30] 문의 › 자주 쓰는 답 (1.27 had it only as a box over a question): the shared answers, by topic. */
  function templatesView() {
    const list = S.templates;
    if (!list) return skeleton(2);
    const names = S.topicNames ?? {};
    const edit = can("inquiries", "edit");
    return `<div class="toolbar"><p class="hint">모든 관리자가 같이 써요. 문의의 답 칸 아래에서 누르면 들어가고, {앱버전}·{주제}·{오늘}은 그 문의에 맞게 채워져요. 주제가 맞는 답과 많이 쓴 답이 앞에 와요.</p>
        ${edit ? `<button class="btn primary" data-act="templates">${icon(P.plus)}새 답</button>` : ""}</div>
      ${list.length ? `<ul class="rows">${list.map((x) => `<li class="item"><div class="row-main"><b>${esc(x.label)}${x.topic ? ` <span class="pill">${esc(names[x.topic] ?? x.topic)}</span>` : ""}</b>
        <small>${esc(x.text.slice(0, 120))}</small><small>${x.by ? `${esc(x.by)} · ` : ""}${x.uses ?? 0}번 씀</small></div>
        <div class="actions"><button type="button" class="btn small" data-act="template-copy" data-id="${x.id}">${icon(P.copy)}복사</button>
        ${edit ? `<button type="button" class="btn small" data-act="template-edit" data-id="${x.id}">고치기</button>
        <button type="button" class="btn small danger" data-act="template-remove" data-id="${x.id}">지우기</button>` : ""}</div></li>`).join("")}</ul>`
        : empty("자주 쓰는 답이 없어요", "새 답을 넣으면 모든 관리자의 답 칸 아래에 보여요.")}`;
  }

  /** [ADMUX-32] 관리 › 서버 상태 (the owner's): what the server has and lacks. */
  function serverView() {
    return `<section class="card"><h2>서버 상태</h2>${S.server ? healthList(S.server) : skeleton(1)}
        <p class="meta">회사 ${S.data.today?.companies ?? S.overview?.companies ?? "–"} · 쓰는 규칙 ${S.data.today?.rules ?? S.overview?.rules ?? "–"} · 앱 판 ${esc(BUILD)}</p></section>`;
  }

  /**
   * [ADMUX-32] #/settings from before 1.28: where each card went, one tap away, in this admin's tabs only. The
   * 정식 출시 card stays here for the owner until AM's 기능 관리 (billing·ads) replaces it.
   */
  function movedView() {
    const rows = [
      ["adopt", "자동 채택·맞았어요 비율·채택 보상·상한", "광장 › 채택 규칙"],
      ["rewards", "친구 초대 보상 규칙", "관리 › 보상"],
      ["sla", "문의 답 목표", "문의 › 답 목표"],
      ["account", "이 기기(화면 색·글자 크기·아래 탭·소리)·이 기기 알림·앱으로 설치·단축키", "관리 › 내 계정·이 기기"],
      ["server", "서버 상태", "관리 › 서버 상태"],
      ["flags", "기능 스위치·정식 출시(결제·광고 조건과 정식 출시일)", "출시 › 기능 관리"],
    ].filter(([t]) => tabOk(t));
    return `<p class="hint">설정 화면에 있던 것은 하는 일에 맞는 허브로 옮겼어요. 누르면 바로 가요.</p>
      <ul class="rows">${rows.map(([t, what, where]) => `<li><a class="row" href="${href(t)}"><span class="row-main"><b>${esc(what)}</b><small>${esc(where)}</small></span>${icon(P.chevron)}</a></li>`).join("")}</ul>
      ${tabOk("flags") ? `<section class="card"><h2>정식 출시 (결제·광고)</h2>
        <p class="hint">정식 출시 스위치는 1.28에서 기능 관리의 '결제(프로 구독)'와 '광고'로 바뀌었어요. 처리방침 시행일과 Play 확인이 끝나면 저절로 켜지고, 처음 켜진 순간이 정식 출시일로 남아요.</p>
        <div class="actions start"><a class="btn primary" href="${L.routeHash("flags", "billing", {})}">결제 조건 보기</a><a class="btn" href="${L.routeHash("flags", "ads", {})}">광고 조건 보기</a></div></section>` : ""}`;
  }
  // AN 1.28 끝

  /** [ADMIN-19] 알림 on this device: which news, quiet hours, a test. */
  function pushCard() {
    const d = S.data.push;
    const supported = "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
    const head = '<section class="card"><h2>이 기기 알림</h2>';
    if (!supported) {
      return `${head}<p class="hint">이 브라우저는 알림을 받을 수 없어요. 휴대폰이면 관리자 앱을 홈 화면에 설치한 뒤 열어 주세요(iPhone은 iOS 16.4 이상, 홈 화면에 추가한 앱에서만 돼요).</p></section>`;
    }
    if (!d) return `${head}${skeleton(1)}</section>`;
    if (!d.ready) {
      return `${head}<p class="hint">서버에 알림 키가 아직 없어요. ${S.me?.owner ? "README의 '관리자 앱 알림'대로 VAPID_PUBLIC_KEY와 VAPID_PRIVATE_KEY를 넣으면 켤 수 있어요." : "최고 관리자가 서버에 알림 키를 넣으면 켤 수 있어요."}</p></section>`;
    }
    const mine = d.subscriptions.find((x) => x.id === S.pushId) ?? null;
    const blocked = Notification.permission === "denied";
    const kinds = mine?.kinds ?? d.kinds.map((k) => k.key);
    const hours = Array.from({ length: 24 }, (_, h) => h);
    const quiet = mine?.quiet ?? null;
    return `${head}
      <p class="hint">새 문의, 급한 문제 신고, 신고로 가려진 글, 새 관리자 가입을 이 기기로 알려요. 내 권한으로 볼 수 있는 것만 와요.${d.subscriptions.length ? ` 지금 ${d.subscriptions.length}대에서 받고 있어요.` : ""}</p>
      ${blocked ? '<p class="note warn">이 브라우저에서 알림을 막아 두었어요. 브라우저 설정 › 사이트 설정 › 알림에서 허용한 뒤 다시 눌러 주세요.</p>' : ""}
      <form data-form="push">
        ${d.kinds.map((k) => `<label class="check-row"><input type="checkbox" name="kind" value="${k.key}"${kinds.includes(k.key) ? " checked" : ""}>${esc(k.label)}</label>`).join("")}
        <label class="check-row"><input type="checkbox" name="quiet"${quiet ? " checked" : ""}>방해 금지 시간</label>
        <div class="field"><label class="sr" for="q-from">부터</label><select id="q-from" name="from">${hours.map((h) => `<option value="${h}"${(quiet?.from ?? 23) === h ? " selected" : ""}>${h}시부터</option>`).join("")}</select>
          <label class="sr" for="q-to">까지</label><select id="q-to" name="to">${hours.map((h) => `<option value="${h}"${(quiet?.to ?? 7) === h ? " selected" : ""}>${h}시까지</option>`).join("")}</select></div>
        <p class="help">한국 시간이에요. 이 시간에는 알림을 보내지 않아요(시험 알림은 빼고).</p>
        <div class="actions start">${mine ? `<button class="btn primary" type="submit">저장</button><button class="btn" type="button" data-act="push-test">시험 알림</button>
          <button class="btn danger" type="button" data-act="push-off">이 기기 끄기</button>` : `<button class="btn primary" type="submit"${blocked ? " disabled" : ""}>이 기기에서 알림 켜기</button>`}</div>
      </form></section>`;
  }

  /** [ADMUX-03][ADMUX-05] What this device remembers: colours, text size, the bottom tabs, sounds, saved views. */
  function prefsCard() {
    const p = S.prefs;
    // AN 1.28 [ADMUX-30] The bottom tabs are hubs: 오늘 and three of the others (only when not every hub fits).
    const list = hubs();
    const bottom = L.bottomHubs(list, p.tabs, "home");
    const items = list.filter((h) => h.key !== "today");
    const opt = (v, label, cur) => `<option value="${v}"${cur === v ? " selected" : ""}>${label}</option>`;
    return `<section class="card prefs"><h2>이 기기</h2><p class="hint">이 기기와 브라우저에만 남아요. 다른 관리자에게는 보이지 않아요.</p>
      <div class="fieldset"><label for="pref-theme">화면 색</label><div class="field"><select id="pref-theme" data-change="pref-theme">${opt("system", "기기 설정을 따라요", p.theme)}${opt("light", "밝게", p.theme)}${opt("dark", "어둡게", p.theme)}</select></div></div>
      <div class="fieldset"><label for="pref-size">글자 크기</label><div class="field"><select id="pref-size" data-change="pref-size">${opt("small", "작게", p.size)}${opt("normal", "보통", p.size)}${opt("large", "크게", p.size)}${opt("xlarge", "아주 크게", p.size) /* AT 1.29 */}</select></div></div>
      ${bottom.more ? `<p class="label">휴대폰 아래 탭(오늘 말고 3개까지, 나머지는 더 보기)</p>
      <div class="chips" role="group" aria-label="아래 탭">${items.map((h) => `<label class="chip pick"><input type="checkbox" data-change="pref-tab" value="${h.key}"${bottom.tabs.includes(h.key) ? " checked" : ""}> ${esc(h.label)}</label>`).join("")}</div>` : ""}
      <label class="switch"><input type="checkbox" data-change="pref-sound"${p.sound ? " checked" : ""}><span class="track" aria-hidden="true"></span><span>새 문의가 오면 작은 소리</span></label>
      <label class="switch"><input type="checkbox" data-change="pref-next"${p.next ? " checked" : ""}><span class="track" aria-hidden="true"></span><span>문의를 닫으면 다음 문의 열기</span></label>
      <p class="label">저장한 보기</p>
      ${p.views.length ? `<ul class="rows">${p.views.map((v, i) => `<li class="item"><a class="row-main" href="${esc(v.hash)}"><b>${esc(v.name)}</b><small>${esc(v.hash)}</small></a>
        <button class="btn small ghost" data-act="view-remove" data-i="${i}">지우기</button></li>`).join("")}</ul>` : '<p class="hint">목록 화면에서 찾기·이동(Ctrl+K)의 "지금 보기 저장"으로 거른 목록을 저장해 두고 오늘에서 한 번에 열어요.</p>'}
    </section>`;
  }

  function stepper(key, label, value, unit, edit, act = "step") {
    return `<div class="stepper"><span>${label}</span><div class="stepper-ctl">
      <button class="icon-btn" data-act="${act}" data-key="${key}" data-d="-1" aria-label="${label} 줄이기"${edit ? "" : " disabled"}>−</button>
      <output aria-live="polite">${value}${unit}</output>
      <button class="icon-btn" data-act="${act}" data-key="${key}" data-d="1" aria-label="${label} 늘리기"${edit ? "" : " disabled"}>+</button></div></div>`;
  }

  function accountView() {
    const m = S.me;
    if (!m) return skeleton();
    const done = S.data.today?.doneToday ?? 0;
    return `<div class="grid two">
      <section class="card"><div class="card-head"><h2>${esc(m.name)}</h2>${m.owner ? '<span class="pill gold">최고 관리자</span>' : ""}</div>
        ${m.owner ? '<p class="hint">Cloudflare의 ADMIN_PASSWORD로 로그인했어요. 모든 화면을 쓰고 관리자를 초대·승인해요.</p>'
          : `<dl class="kv"><dt>메일</dt><dd>${esc(m.email)}</dd></dl><p class="label">권한</p><div class="perm-sum">${permPills(m.perms, m.areas ?? {})}</div>`}
      </section>
      ${can("audit") ? `<section class="card"><h2>내가 한 일</h2><p class="hint">오늘 ${done}건 처리했어요(로그인은 빼고).</p>
        <div class="actions start"><a class="btn" href="${L.routeHash("audit", null, { actor: "me" })}">내 활동 기록 보기</a></div></section>`
        : `<section class="card"><h2>내가 한 일</h2><p class="hint">오늘 ${done}건 처리했어요(로그인은 빼고).</p></section>`}
      ${m.owner ? `<section class="card"><h2>비밀번호</h2><p class="hint">최고 관리자 비밀번호는 Cloudflare 대시보드의 ADMIN_PASSWORD에서 바꿔요. 바꾸면 이 기기도 다시 로그인해요.</p></section>`
        : `<form class="card" data-form="password"><h2>비밀번호 바꾸기</h2>
          <div class="fieldset"><label for="pw-now">지금 비밀번호</label>${pwInput("pw-now", "current", "current-password")}</div>
          <div class="fieldset"><label for="pw-new">새 비밀번호</label>${pwInput("pw-new", "next", "new-password", ' minlength="10" data-strength="pw-meter"')}
            <meter id="pw-meter" class="strength" min="0" max="4" low="2" high="3" optimum="4" value="0" aria-label="비밀번호 안전도"></meter>
            <span class="help">10자 이상. 바꾸면 다른 기기의 로그인은 끝나요.</span></div>
          <div class="actions"><button class="btn primary" type="submit">바꾸기</button></div></form>`}
      ${secondCard()}
      <section class="card"><h2>내 휴대폰 밤낮 연결</h2>
        <p class="hint">관리자 휴대폰은 프로를 무료로 써요. 코드를 만들어 밤낮 앱 › 설정 › 프로 › '관리자 연결'에 10분 안에 넣어요${m.owner ? "" : ". 최고 관리자가 승인하면 시작돼요"}. 한 사람당 2대까지예요.</p>
        <div class="actions start"><button class="btn primary" data-act="link-code">연결 코드 만들기</button></div></section>
      ${previewCard()}
      <section class="card"><h2>로그인</h2><p class="hint">로그인은 쓰는 동안 저절로 이어지고, 7일 동안 안 쓰면 끝나요(길어도 30일). 휴대폰을 잃어버렸다면 모든 기기에서 로그아웃해요.</p>
        <div class="actions start"><button class="btn" data-act="logout">${icon(P.logout)}로그아웃</button>
          <button class="btn danger" data-act="logout-all">모든 기기에서 로그아웃</button></div></section>
      ${pushCard()}
      ${prefsCard()}
      <section class="card"><h2>앱으로 설치</h2>
        <p class="hint">${S.installed ? "이 기기에는 앱으로 설치돼 있어요." : "홈 화면에 두면 앱처럼 바로 열리고, 주소창 없이 넓게 보여요."}</p>
        <div class="actions start">${S.installPrompt ? `<button class="btn primary" data-act="install">${icon(P.install)}지금 설치</button>` : ""}
          <a class="btn" href="/admin/install">설치 방법 보기</a></div></section>
      <section class="card"><h2>단축키</h2><p class="hint">키보드가 있으면 <span class="kbd">1</span>~<span class="kbd">6</span>으로 허브를, <span class="kbd">[</span> <span class="kbd">]</span>로 허브 안 탭을 옮기고, <span class="kbd">?</span>로 단축키를, <span class="kbd">Ctrl</span>+<span class="kbd">K</span>로 찾기·이동을 열어요.</p>
        <div class="actions start"><button class="btn" data-act="shortcuts">단축키 보기</button></div></section>
      </div>`;
  }

  /**
   * R131 1.31 [ADMX-16] 두 번째 확인: on, an authenticator app's six digits are asked after the password. Starting shows the key
   * (and a link that opens the authenticator app on this phone); the first right code turns it on.
   */
  function secondCard() {
    const v = S.data.second;
    if (!v) return '<section class="card"><h2>두 번째 확인</h2><p class="hint">불러오는 중…</p></section>';
    if (v.on) {
      return `<form class="card" data-form="second-off"><div class="card-head"><h2>두 번째 확인</h2><span class="pill ok">켜짐</span></div>
        <p class="hint">로그인할 때 비밀번호 다음에 인증 앱의 6자리 코드를 넣어요.${v.resetSet ? " Cloudflare의 ADMIN_2FA_RESET이 1이라 지금은 최고 관리자 로그인에서 코드를 묻지 않아요. 휴대폰을 찾았으면 지워 주세요." : ""}</p>
        <div class="fieldset"><label for="second-off-code">끄려면 지금 코드</label><input id="second-off-code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required></div>
        <div class="actions start"><button class="btn danger" type="submit">끄기</button></div></form>`;
    }
    if (v.secret) {
      return `<form class="card" data-form="second-on"><h2>두 번째 확인 켜기</h2>
        <ol class="steps"><li>휴대폰의 인증 앱(Google Authenticator, Microsoft Authenticator 등)에서 '키 입력'을 골라요.</li>
          <li>아래 키를 넣어요. 이 휴대폰이면 <a href="${esc(v.uri)}">인증 앱에 바로 넣기</a>를 눌러도 돼요.</li><li>인증 앱에 나온 6자리를 넣고 켜요.</li></ol>
        <p class="code-big" aria-label="인증 앱 키">${esc(v.secret.replace(/(.{4})/g, "$1 ").trim())}</p>
        <div class="fieldset"><label for="second-on-code">인증 앱 코드</label><input id="second-on-code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required></div>
        <div class="actions start"><button class="btn primary" type="submit">켜기</button></div></form>`;
    }
    return `<section class="card"><h2>두 번째 확인</h2>
      <p class="hint">비밀번호가 새어도 다른 사람이 들어오지 못하게, 로그인할 때 휴대폰 인증 앱의 6자리 코드를 한 번 더 물어요. 휴대폰을 잃으면 ${S.me?.owner ? "Cloudflare에 ADMIN_2FA_RESET=1을 넣으면 코드 없이 들어와요." : "최고 관리자에게 계정을 다시 만들어 달라고 해요."}</p>
      <div class="actions start"><button class="btn primary" data-act="second-start">켜기</button></div></section>`;
  }

  /** [ADMUX-03] A password box with a 보기 button beside it. */
  const pwInput = (id, name, auto, extra = "") => `<div class="pw"><input id="${id}" name="${name}" type="password" autocomplete="${auto}" required${extra}>
    <button class="btn small ghost show" type="button" data-act="pw-show" data-for="${id}" aria-pressed="false" aria-label="비밀번호 보기">보기</button></div>`;

  /** [ADMUX-30] 더 보기 on a phone: the hubs not in the bottom bar (분석·관리 by default), each with its tabs. */
  function moreView() {
    const list = hubs();
    const bottom = L.bottomHubs(list, S.prefs.tabs, "more");
    const o = S.overview ?? {};
    const rest = list.filter((h) => !bottom.tabs.includes(h.key));
    return `${rest.map((h) => `<h2 class="section">${icon(P[h.icon])}${esc(h.label)}${L.hubBadge(h, o, S.me) ? `<span class="count">${L.hubBadge(h, o, S.me)}</span>` : ""}</h2>
      <ul class="rows">${h.tabs.map((t) => `<li><a class="row" href="${href(t)}"><span class="row-main"><b>${esc(tabName(t))}</b></span>
        ${L.tabBadge(t, o, S.me) ? `<i class="badge">${L.tabBadge(t, o, S.me)}</i>` : ""}${icon(P.chevron)}</a></li>`).join("")}</ul>`).join("")}
      <div class="actions start"><button class="btn wide" data-act="logout">${icon(P.logout)}로그아웃</button></div>`;
  }

  // ---- sign in, invite and install pages ----

  function loginView() {
    const h = S.health;
    const owner = S.prefs.loginMode === "owner";
    const usable = !owner || !h || (h.database && h.password);
    return `<main class="gate">
      <div class="brand"><img src="/admin/icon.svg" alt="" width="76" height="76"><h1>밤낮 관리자</h1><p>제보, 문의, 회사 규칙을 관리해요.</p></div>
      <div class="segmented" role="group" aria-label="로그인 방법">${seg("login-mode", "account", S.prefs.loginMode, "관리자 계정")}${seg("login-mode", "owner", S.prefs.loginMode, "최고 관리자")}</div>
      <form class="card" data-form="login">
        ${owner || S.needCode ? "" : `<div class="fieldset"><label for="email">메일</label><input id="email" name="email" type="email" autocomplete="username" inputmode="email" required></div>`}
        ${S.needCode ? "" : `<div class="fieldset"><label for="password">${owner ? "최고 관리자 비밀번호" : "비밀번호"}</label>
          ${pwInput("password", "password", "current-password", usable ? "" : " disabled")}</div>`}
        ${usable ? "" : '<p class="hint">서버가 비밀번호를 읽으면 입력할 수 있어요. 아래 서버 준비를 마친 뒤 새로 고쳐 주세요.</p>'}
        ${S.needCode ? `<div class="fieldset"><label for="login-code">인증 앱 코드</label><input id="login-code" name="code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required>
          <span class="help">두 번째 확인을 켠 계정이에요. 인증 앱의 6자리 숫자를 넣어요.</span></div>` : ""}
        <p class="error" role="alert">${esc(S.error)}</p>
        <button class="btn primary wide" type="submit"${usable ? "" : " disabled"}>로그인</button>
        ${S.needCode ? '<button class="btn ghost wide" type="button" data-act="login-restart">비밀번호부터 다시</button>' : ""}
      </form>
      ${owner ? "" : '<p class="alt">계정은 최고 관리자가 보낸 초대 링크로만 만들 수 있어요.</p>'}
      ${owner && h && !(h.database && h.password && h.salt) ? `<section class="card"><h2>서버 준비</h2>${healthList()}</section>` : ""}
      ${S.installed ? "" : '<p class="alt"><a href="/admin/install">휴대폰에 앱으로 설치하기</a></p>'}
    </main>`;
  }


  function installSteps() {
    const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);
    return ios ? `<ol class="steps"><li>Safari에서 이 화면을 열어요.</li><li>아래 공유 버튼(□↑)을 눌러요.</li><li>홈 화면에 추가를 눌러요.</li></ol>`
      : `<ol class="steps"><li>Chrome이나 삼성 인터넷에서 이 화면을 열어요.</li><li>${S.installPrompt ? "아래 앱 설치 버튼을 누르거나, " : ""}메뉴(⋮)에서 앱 설치 또는 홈 화면에 추가를 눌러요.</li>
          <li>홈 화면의 밤낮 관리 아이콘으로 열어요.</li></ol>`;
  }

  function installView() {
    return `<main class="gate">
      <div class="brand"><img src="/admin/icon.svg" alt="" width="76" height="76"><h1>밤낮 관리자 설치</h1><p>스토어 없이 이 주소에서 바로 설치해요.</p></div>
      <section class="card">${S.installed ? '<p class="note ok">이 기기에는 이미 앱으로 설치돼 있어요.</p>' : installSteps()}
        ${S.installPrompt ? `<button class="btn primary wide" data-act="install">${icon(P.install)}앱 설치</button>` : ""}</section>
      <section class="card"><h2>처음이라면</h2><p class="hint">관리자 계정은 최고 관리자가 보낸 초대 링크로 만들어요. 초대 메일의 링크를 먼저 열어 주세요.</p></section>
      <p class="alt"><a href="/admin/">로그인 화면으로</a></p></main>`;
  }

  function joinView() {
    const j = S.join;
    if (!j) return '<div class="center"><div class="spinner" role="progressbar" aria-label="불러오는 중"></div></div>';
    if (j.error) {
      return `<main class="gate"><div class="brand"><img src="/admin/icon.svg" alt="" width="76" height="76"><h1>초대 링크를 열 수 없어요</h1></div>
        <section class="card"><p>${esc(ERRORS[j.error] ?? ERRORS.invite)}</p></section><p class="alt"><a href="/admin/">로그인 화면으로</a></p></main>`;
    }
    if (j.done) {
      return `<main class="gate"><div class="brand"><img src="/admin/icon.svg" alt="" width="76" height="76"><h1>계정을 만들었어요</h1>
        <p>최고 관리자가 승인하면 로그인할 수 있어요.</p></div>
        <section class="card"><ol class="steps"><li class="done">초대 메일 확인</li><li class="done">계정 만들기</li><li>앱 설치</li><li>승인 뒤 로그인</li></ol></section>
        <section class="card"><h2>앱으로 설치</h2>${S.installed ? '<p class="note ok">이 기기에는 이미 설치돼 있어요.</p>' : installSteps()}
          ${S.installPrompt ? `<button class="btn primary wide" data-act="install">${icon(P.install)}앱 설치</button>` : ""}</section>
        <p class="alt"><a href="/admin/">로그인 화면으로</a></p></main>`;
    }
    return `<main class="gate">
      <div class="brand"><img src="/admin/icon.svg" alt="" width="76" height="76"><h1>관리자 초대</h1><p>${esc(j.email)}로 초대받았어요.</p></div>
      <section class="card"><ol class="steps"><li class="done">초대 메일 확인</li><li>계정 만들기</li><li>앱 설치</li><li>승인 뒤 로그인</li></ol>
        <p class="label">받을 권한</p><div class="perm-sum">${permPills(j.perms, j.areas)}</div></section>
      <form class="card" data-form="join">
        <div class="fieldset"><label for="j-email">메일</label><input id="j-email" type="email" value="${esc(j.email)}" readonly autocomplete="username"></div>
        <div class="fieldset"><label for="j-name">이름</label><input id="j-name" name="name" maxlength="30" required autocomplete="name" placeholder="다른 관리자에게 보일 이름"></div>
        <div class="fieldset"><label for="j-pw">비밀번호</label>${pwInput("j-pw", "password", "new-password", ' minlength="10" data-strength="j-meter"')}
          <meter id="j-meter" class="strength" min="0" max="4" low="2" high="3" optimum="4" value="0" aria-label="비밀번호 안전도"></meter>
          <span class="help">10자 이상, 메일 주소와 다르게요.</span></div>
        <div class="fieldset"><label for="j-pw2">비밀번호 한 번 더</label>${pwInput("j-pw2", "password2", "new-password", ' minlength="10"')}</div>
        ${j.mail ? `<div class="fieldset"><label for="j-code">메일 확인 코드</label><div class="field"><input id="j-code" name="code" inputmode="numeric" maxlength="6" pattern="[0-9]{6}" required
          autocomplete="one-time-code"><button class="btn" type="button" data-act="join-code">${j.codeSent ? "다시 보내기" : "코드 보내기"}</button></div>
          <span class="help">${j.codeSent ? `${esc(j.email)}로 6자리 코드를 보냈어요. 15분 동안 쓸 수 있어요.` : "버튼을 누르면 초대받은 메일로 코드가 가요."}</span></div>` : ""}
        <p class="error" role="alert">${esc(S.error)}</p>
        <button class="btn primary wide" type="submit">계정 만들기</button>
      </form></main>`;
  }

  // ---- dialogs ----

  function openDialog(html, size = "") {
    dialog.className = size;
    dialog.innerHTML = html;
    if (!dialog.open) {
      dialog.showModal();
      // [ADMUX-10] The phone's back closes the box first: it gets a step of its own in the history, same address.
      if (!history.state?.dialog) history.pushState({ dialog: true }, "", location.href);
    }
    dialog.querySelector("input:not([type=radio]):not([type=hidden]):not([readonly]),textarea")?.focus();
  }

  function confirmBox(title, text, label, then, tone = "danger") {
    pendingConfirm = then;
    openDialog(`<form data-form="confirm"><h2>${esc(title)}</h2><p>${esc(text)}</p>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn ${tone}" type="submit">${esc(label)}</button></div></form>`);
  }

  function openReject(company, setup) {
    const reasons = Object.entries(S.overview?.reasons ?? {});
    openDialog(`<form data-form="reject" data-company="${company}" data-setup="${setup}"><h2>거절할까요?</h2><p>보낸 분 앱에 이 이유가 보여요.</p>
      <div class="radios">${reasons.map(([key, text], i) => `<label class="radio"><input type="radio" name="reason" value="${key}"${i === 0 ? " checked" : ""}>
        <span>${esc(text)}</span></label>`).join("")}</div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">거절하기</button></div></form>`);
  }

  function openAnswer(id) {
    const answers = Object.entries(S.overview?.answers ?? {});
    const months = S.overview?.settings?.rewardMonths ?? 0;
    openDialog(`<form data-form="answer" data-id="${id}"><h2>어떻게 답할까요?</h2><p>보낸 분 앱에 이 답이 보여요.</p>
      <div class="radios">${answers.map(([key, text], i) => `<label class="radio"><input type="radio" name="answer" value="${key}"${i === 0 ? " checked" : ""}>
        <span>${esc(text)}</span></label>`).join("")}</div>
      ${months && can("rewards", "edit") ? `<label class="switch"><input type="checkbox" name="reward" checked><span class="track" aria-hidden="true"></span>
        <span>앱에 넣을 방식이면 프로 ${months}개월 보상 기록</span></label>` : ""}
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">답하기</button></div></form>`);
  }

  function openNewCompany() {
    openDialog(`<form data-form="new-company"><h2>회사 만들기</h2><p>제보가 오기 전에 이름과 다른 이름을 정해 둘 때 써요.</p>
      <label for="new-name">회사 이름</label><input id="new-name" name="name" maxlength="40" required>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">만들기</button></div></form>`);
  }

  function openPaid(id, months = 3) {
    // [MON-08] 회사 방식 채택 3개월, 제안 1개월, 큰 개선 6개월~1년 (수익화 확정 4장 7).
    const choices = [[1, "제안 채택"], [3, "회사 방식 채택"], [6, "큰 개선"], [12, "아주 큰 개선"]];
    openDialog(`<form data-form="paid" data-id="${id}"><h2>프로 이용권 드리기</h2><p>보낸 분 휴대폰에 이용권이 생겨요. 이미 받은 이용권 뒤에 이어져요.</p>
      <p class="label">기간</p><div class="chips" role="radiogroup" aria-label="기간">${choices.map(([n, why]) => `<label class="chip pick"><input type="radio" name="months" value="${n}"${Number(months) === n ? " checked" : ""}> ${n}개월 · ${why}</label>`).join("")}</div>
      <label for="memo">메모 (선택)</label><input id="memo" name="memo" maxlength="200" placeholder="예: 근태 방식 제보 채택">
      <p class="help">보낸 분의 이름이나 연락처는 적지 마세요.</p>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">드리기</button></div></form>`);
  }

  /** [ADMUX-01] Signed out while working: sign in again in a box over the screen, which stays as it was. */
  function openRelogin() {
    if (S.relogin) return;
    S.relogin = true;
    const owner = S.me?.owner;
    openDialog(`<form data-form="relogin"><h2>다시 로그인해 주세요</h2><p>로그인이 끝났어요. 적던 글과 화면은 그대로 있어요.</p>
      ${owner ? "" : `<div class="fieldset"><label for="re-email">메일</label><input id="re-email" name="email" type="email" autocomplete="username" value="${esc(S.me?.email ?? "")}" required></div>`}
      <div class="fieldset"><label for="re-pw">${owner ? "최고 관리자 비밀번호" : "비밀번호"}</label>${pwInput("re-pw", "password", "current-password")}</div>
      <div class="fieldset"><label for="re-code">인증 앱 코드(두 번째 확인을 켰을 때만)</label><input id="re-code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6"></div>
      <p class="error" role="alert" id="re-error"></p>
      <div class="actions"><button class="btn" type="button" data-act="relogin-out">로그인 화면으로</button><button class="btn primary" type="submit">로그인</button></div></form>`);
  }

  /** [ADMUX-04] 찾기·이동: screens, saved views, the questions in the list, and a few steps, by typing a few letters. */
  /** [ADMUX-04] The last details opened (this visit), first in the palette. */
  function rememberOpened() {
    const { name, id } = S.route;
    if (!id || !["inquiries", "dev", "companies"].includes(name) || !hasData()) return;
    const q = name === "inquiries" ? S.data.inquiry : null;
    const label = q ? `${KIND_NAMES[q.kind] ?? "문의"} · ${[...(q.messages?.[0]?.text ?? "")].slice(0, 40).join("")}` : heading().title;
    const to = L.routeHash(name, id, {});
    S.recent = [{ label, hint: "최근 연 것", kind: "최근", to }, ...S.recent.filter((x) => x.to !== to)].slice(0, 6);
  }

  function paletteEntries() {
    const out = S.recent.filter((x) => x.to !== L.routeHash(S.route.name, S.route.id, {}));
    // AN 1.28 [ADMUX-36] Every tab as 허브 › 탭, found also by the menu names it had before 1.28.
    for (const h of hubs()) {
      for (const t of h.tabs) out.push({ label: h.tabs.length > 1 ? `${h.label} › ${tabName(t)}` : h.label, hint: `화면 ${L.TABS[t].old ?? ""}`, kind: "화면", to: href(t) });
    }
    for (const v of S.prefs.views) out.push({ label: v.name, hint: "저장한 보기", kind: "보기", to: v.hash });
    for (const q of S.data.inquiries?.inquiries ?? []) out.push({ label: q.preview.slice(0, 60) || "(내용 없음)", hint: `문의 ${KIND_NAMES[q.kind] ?? ""} ${q.topicName ?? ""}`, kind: "문의", to: href("inquiries", q.id) });
    for (const x of S.data.devList?.items ?? []) out.push({ label: x.title, hint: "개발사항", kind: "개발사항", to: href("dev", x.id) });
    for (const c of S.data.list?.companies ?? []) out.push({ label: c.name, hint: "회사", kind: "회사", to: href("companies", c.id) });
    out.push(
      { label: "새로 고침", hint: "refresh", kind: "할 일", act: "refresh" },
      { label: "단축키 보기", hint: "keys", kind: "할 일", act: "shortcuts" },
      { label: "화면 색 바꾸기(밝게·어둡게)", hint: "테마 theme dark light", kind: "할 일", act: "theme-next" },
      { label: "로그아웃", hint: "logout", kind: "할 일", act: "logout" },
    );
    if (!S.route.id && (BIND[S.route.name] || S.route.name === "audit") && S.route.name !== "home") out.push({ label: "지금 보기 저장", hint: "save view", kind: "할 일", act: "view-save" });
    if (can("inquiries", "edit")) out.push({ label: "큐 모드로 답하기", hint: "queue", kind: "할 일", act: "queue-start" });
    return out;
  }

  function openPalette() {
    S.palette = { q: "", at: 0 };
    openDialog(`<div class="box" role="combobox" aria-expanded="true" aria-haspopup="listbox" aria-owns="pal-list">
      <label class="sr" for="pal">찾기·이동</label><input id="pal" type="search" autocomplete="off" placeholder="화면, 문의, 회사, 할 일 찾기" aria-controls="pal-list" aria-autocomplete="list">
      <ul class="rows" id="pal-list" role="listbox" aria-label="찾은 것"></ul>
      <p class="hint">↑↓로 고르고 Enter로 열어요. Esc로 닫아요.</p></div>`, "palette");
    paintPalette();
  }

  function paintPalette() {
    const box = document.getElementById("pal-list");
    if (!box || !S.palette) return;
    // AX 1.30 [ADMX-08] What this screen knows first, then the server's answer for the same words, a heading per kind.
    const local = L.paletteFilter(paletteEntries(), S.palette.q, S.palette.server?.q === S.palette.q ? 8 : 12);
    const seen = new Set(local.map((e) => e.to).filter(Boolean));
    const server = [];
    if (S.palette.server?.q === S.palette.q) {
      for (const g of S.palette.server.groups) {
        const items = g.items.filter((x) => !seen.has(x.to));
        if (!items.length) continue;
        items.forEach((x, j) => server.push({ label: x.label, hint: x.hint, kind: g.label, to: x.to, head: j === 0 ? `${g.label} ${g.total}` : "" }));
        if (g.total > g.items.length && g.more) server.push({ label: `${g.label} ${g.total}개 모두 보기`, kind: g.label, to: g.more });
      }
    }
    const list = [...local, ...server];
    S.palette.list = list;
    S.palette.at = Math.min(S.palette.at, Math.max(0, list.length - 1));
    const waiting = S.palette.q.trim().length >= 2 && S.palette.server?.q !== S.palette.q;
    box.innerHTML = list.length ? list.map((e, i) => `${e.head ? `<li class="group-head" role="presentation">${esc(e.head)}</li>` : ""}<li><button class="row" type="button" role="option" id="pal-${i}" data-act="palette-go" data-i="${i}" aria-selected="${i === S.palette.at}">
      <span class="row-main"><b>${esc(e.label)}</b>${e.head !== undefined && e.hint ? `<small class="hint2">${esc(e.hint)}</small>` : ""}</span><small class="kind">${esc(e.kind)}</small></button></li>`).join("")
      + (waiting ? '<li class="meta" role="presentation">서버에서 더 찾는 중이에요…</li>' : "")
      : waiting ? '<li class="meta">찾는 중이에요…</li>' : '<li class="meta">찾는 것이 없어요.</li>';
    document.getElementById("pal")?.setAttribute("aria-activedescendant", list.length ? `pal-${S.palette.at}` : "");
  }

  function paletteGo(i) {
    const e = S.palette?.list?.[i];
    if (!e) return;
    dialog.close();
    S.palette = null;
    if (e.act) ACTIONS[e.act]?.({});
    else if (location.hash === e.to) load();
    else location.hash = e.to;
  }

  function openShortcuts() {
    // AN 1.28 [ADMUX-36] 1~6 are the hubs, always the same number whatever the admin sees.
    const shown = new Set(hubs().map((h) => h.key));
    openDialog(`<div class="box"><h2>단축키</h2>
      <div class="keys">${L.KEYS.map(([k, what]) => `<span class="kbd">${esc(k)}</span><span>${esc(what)}</span>`).join("")}
        ${L.HUBS.map((h, i) => (shown.has(h.key) ? `<span class="kbd">${i + 1}</span><span>${esc(h.label)} (문의 답 칸에서는 자주 쓰는 답)</span>` : "")).join("")}
        <span class="kbd">R</span><span>새로 고침 (문의를 연 동안은 답 칸으로)</span></div>
      <div class="actions"><button class="btn" data-act="close">닫기</button></div></div>`, "wide");
  }


  // ---- what taps do ----

  /**
   * Runs one step and says how it went. [undo], given the answer, is what 되돌리기 on the note does.
   * A form being sent is emptied once it went through, with what was kept of it in the browser.
   */
  async function act(work, done, undo) {
    try {
      const r = await work();
      const form = S.submitting;
      if (form?.isConnected && !form.closest("dialog")) {
        for (const id of KEEP) if (form.querySelector(`#${id}`)) writeDraft(id, "");
        form.reset();
      }
      if (done) toast(typeof done === "function" ? done(r) : done, undo ? () => undo(r) : undefined);
      return r;
    } catch (e) {
      fail(e);
      return null;
    }
  }

  async function setRule(id, withdrawn) {
    const c = await act(() => api(`/rules/${id}`, "POST", { withdrawn }), withdrawn ? "규칙을 내렸어요. 회사 찾기에서 바로 빠져요" : "규칙을 다시 올렸어요");
    if (c && S.route.name === "companies") S.data.company = c;
    load();
  }

  async function setReward(id, status, memo, months) {
    const body = { status, ...(memo === undefined ? {} : { memo }), ...(months ? { months } : {}) };
    await act(() => api(`/rewards/${id}`, "POST", body),
      (r) => status === "paid" ? (r?.deferred ? `Play 다음 결제를 ${r.months}개월 미뤘어요` : `프로 ${r?.months ?? months}개월 이용권을 드렸어요`)
        : status === "void" ? "보상을 취소했어요" : "대기로 되돌렸어요",
      // [ADMUX-01] 취소 is taken back in one tap; 지급함 is not (the pass is on the phone already).
      status === "void" ? () => setReward(id, "owed") : undefined);
    load();
  }

  /** [ADMUX-04] 큐 모드: the next waiting question, or the end of the queue. */
  function queueNext(skip = false) {
    const qm = S.queue;
    if (!qm) return;
    qm.at += 1;
    if (skip) qm.skipped += 1;
    if (qm.at >= qm.ids.length) {
      const n = qm.ids.length - qm.skipped;
      S.queue = null;
      toast(`큐를 마쳤어요. ${n}건 처리${qm.skipped ? `, ${qm.skipped}건 건너뜀` : ""}`);
      go("inquiries");
      return;
    }
    go("inquiries", qm.ids[qm.at]);
  }

  /** [ADMIN-16] 답 쓰는 중, so another admin opening the same question sees it; at most every 20 seconds. */
  function pingTyping(on) {
    if (S.route.name !== "inquiries" || !S.route.id || !can("inquiries", "edit")) return;
    const now = Date.now();
    if (on && now - typingSent < 20000) return;
    if (!on && !typingSent) return;
    typingSent = on ? now : 0;
    api(`/inquiries/${S.route.id}/typing`, "POST", { on }).catch(() => {});
  }

  /** [ADMIN-16] The step for the picked questions; skipped ones (someone else's) are counted. */
  async function inquiryBulk(action, extra = {}) {
    const ids = [...S.inquiryPick];
    const r = await act(() => api("/inquiries/bulk", "POST", { action, ids, ...extra }),
      (x) => `${x.done}개를 처리했어요${x.skipped ? `. ${x.skipped}개는 건너뛰었어요(다른 관리자가 맡았거나 이미 같은 답)` : ""}`);
    if (r) {
      S.inquiryPick = null;
      S.data.inquiries = null;
      load();
    }
    return r;
  }

  /** [ADMIN-17] The activity lines as a CSV file Excel opens (UTF-8 with BOM, formulas made plain text). */
  /**
   * [ADMUX-07] The list on screen as a CSV for a spreadsheet. Only the columns without personal details: no
   * contact, memo or install id, just what, when and how it stands. [ADMUX-11] The question's text goes too, with
   * mail addresses, phone numbers and amounts made ••• first, so the rows can be read without opening each.
   */
  const CSV_KINDS = {
    audit: () => [S.data.audit?.entries, S.data.audit?.more, "audit", [["시각", (e) => new Date(e.at).toLocaleString("ko-KR")], ["누가", (e) => e.actor || "앱 사용자·시스템"],
      ["한 일", (e) => ACTION_NAMES[e.action] ?? e.action], ["대상", (e) => e.label || e.target || ""], ["내용", (e) => e.detail ?? ""]]],
    inquiries: () => [S.data.inquiries?.inquiries, S.data.inquiries?.next, "inquiries", [["받은 때", (q) => new Date(q.createdAt).toLocaleString("ko-KR")],
      ["종류", (q) => KIND_NAMES[q.kind] ?? q.kind], ["주제", (q) => q.topicName ?? ""], ["상태", (q) => STATUS_NAMES[q.status ?? S.inquiryStatus] ?? ""],
      ["맡은 사람", (q) => q.assignee?.name ?? ""], ["앱 버전", (q) => q.appVersion ?? ""], ["나도 그래요", (q) => q.count ?? 1], ["개발사항", (q) => (q.dev ? "있음" : "")],
      ["본문", (q) => L.maskPersonal(q.text ?? q.preview ?? "")]]],
    dev: () => [S.data.devList?.items, S.data.devList?.next, "dev", [["제목", (x) => x.title], ["처리", (x) => DEV_NAMES[x.status] ?? x.status],
      ["연결된 문의", (x) => x.links ?? 0], ["답 대기", (x) => x.waiting ?? 0], ["맡은 사람", (x) => x.assignee?.name ?? ""], ["반영 버전", (x) => x.releasedIn ?? ""],
      ["고친 때", (x) => new Date(x.updatedAt).toLocaleString("ko-KR")]]],
    rewards: () => [S.data.rewards?.rewards, S.data.rewards?.next, "rewards", [["회사", (w) => w.company?.name ?? ""], ["부서", (w) => w.dept ?? ""],
      ["개월", (w) => w.months], ["종류", (w) => (w.ask ? "앱에 없는 방식" : "채택")], ["상태", (w) => ({ owed: "드릴 보상", paid: "지급함", void: "취소" })[w.status] ?? w.status],
      ["생긴 날", (w) => day(w.createdAt)], ["지급한 날", (w) => (w.paidAt ? day(w.paidAt) : "")]]],
  };

  const csvButton = (kind, rows) => `<button class="btn small" data-act="csv" data-kind="${kind}"${rows?.length ? "" : " disabled"}>${icon(P.install)}CSV로 받기</button>`;

  function downloadCsv(kind = "audit") {
    const [rows, more, name, cols] = CSV_KINDS[kind]?.() ?? [];
    if (!rows?.length) return;
    const url = URL.createObjectURL(new Blob([L.csv(rows, cols)], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    // ASCII file names: some browsers save a Korean one as "download".
    a.download = `bamnat-${name}-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast(`${rows.length}줄을 받았어요${more ? ". 더 보기로 늘린 만큼만 들어가요" : ""}`);
  }

  /** [ADMIN-19] This browser's subscription id, as the server makes it (sha256 of push|endpoint). */
  async function pushIdOf(endpoint) {
    const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`push|${endpoint}`));
    return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 32);
  }

  async function currentPush() {
    try {
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.getSubscription();
      S.pushId = sub ? await pushIdOf(sub.endpoint) : null;
      return sub;
    } catch {
      S.pushId = null;
      return null;
    }
  }

  /** [ADMUX-08] A short tone for a new question, made here (no sound file to fetch). */
  function chime() {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.frequency.value = 880;
      g.gain.setValueAtTime(0.08, ctx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.35);
      o.connect(g).connect(ctx.destination);
      o.start();
      o.stop(ctx.currentTime + 0.4);
    } catch {
      // No sound here.
    }
  }

  /** Filters changed on a list: the address follows, the old answer is dropped and the list comes again. */
  function refilter(dataKey) {
    if (dataKey) S.data[dataKey] = null;
    syncUrl();
    render({ keepScroll: true });
    load();
  }

  async function copy(text, done = "복사했어요") {
    try {
      await navigator.clipboard.writeText(text);
      toast(done);
    } catch {
      document.getElementById("inv-link")?.select();
      toast("링크를 길게 눌러 복사해 주세요");
    }
  }

  // ---- AT 1.29 시작: 문의 복사 [ADMCP-01]~[ADMCP-05] ----
  // One question or the ones picked, as Markdown to paste into development: a box shows the text (짧게·자세히,
  // 같은 제보 묶기, 분류별로), 복사 puts it on the clipboard (a box to select by hand where the browser will not),
  // and then 복사함 is marked with, if chosen, a 개발사항 made from them, 내가 맡기 and 닫기 in the same step.

  /** Text on the clipboard: the browser's clipboard, else the old copy command on the box; false when neither works. */
  async function toClipboard(text, box) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      try {
        box?.select();
        return Boolean(document.execCommand?.("copy"));
      } catch {
        return false;
      }
    }
  }

  const copyText = () => L.inquiryCopyText(S.copy?.list ?? [], { style: S.copy?.style, group: S.copy?.group, byKind: S.copy?.byKind, base: location.origin });

  /** The copy box for [ids] (in the list's order); a question open on the screen is used as it is, the rest are read whole. */
  async function openCopy(ids) {
    if (!ids.length) return;
    const p = S.prefs.copy ?? {};
    S.copy = { ids, list: null, style: p.style === "short" ? "short" : "full", group: p.group !== false, byKind: p.byKind !== false, failed: false };
    paintCopy();
    const open = S.data.inquiry;
    if (ids.length === 1 && open?.id === ids[0] && open.messages) {
      S.copy.list = [open];
    } else {
      const r = await act(() => api(`/inquiries/export?ids=${ids.join(",")}`));
      if (!r || !S.copy) return dialog.close();
      S.copy.list = r.inquiries;
    }
    paintCopy();
  }

  function paintCopy() {
    const c = S.copy;
    if (!c) return;
    // A title being typed survives a repaint of the box (a box ticked or the style changed).
    const typed = document.getElementById("copy-dev-title")?.value;
    if (typed !== undefined) c.devTitle = typed;
    const n = c.ids.length;
    const edit = can("inquiries", "edit");
    const dev = edit && can("dev", "edit");
    const item = c.list && c.dev ? L.copyDevItem(c.list, location.origin) : null;
    const html = `<div class="box copybox"><h2>문의 ${n}건 복사</h2>
      <p class="hint">개발할 때 그대로 붙여 넣는 마크다운이에요. 사진은 들어가지 않고 개수와 관리자 앱 링크만 들어가요. 밖에 붙여 넣는다면 개인을 알 수 있는 내용이 없는지 한 번 봐 주세요.</p>
      <div class="segmented" role="group" aria-label="복사 꼴">${seg("copy-style", "short", c.style, "짧게")}${seg("copy-style", "full", c.style, "자세히")}</div>
      ${n > 1 ? `<label class="check-row"><input type="checkbox" data-copy-opt="group"${c.group ? " checked" : ""}> 같은 제보 묶기</label>
        <label class="check-row"><input type="checkbox" data-copy-opt="byKind"${c.byKind ? " checked" : ""}> 분류별로 정렬(문제 신고 먼저)</label>` : ""}
      <label class="sr" for="copy-text">복사할 글</label>
      ${c.list ? `<textarea id="copy-text" class="copy-text" readonly spellcheck="false">${esc(copyText())}</textarea>` : '<div class="card skeleton"></div>'}
      ${c.failed ? '<p class="note" id="copy-fallback" role="alert">이 브라우저는 바로 복사를 막았어요. 위 글 상자를 길게 눌러(컴퓨터는 Ctrl+A, Ctrl+C) 복사한 뒤 <b>복사했어요</b>를 눌러 주세요.</p>' : ""}
      ${edit ? `<fieldset class="after"><legend>복사하면 함께</legend>
        <p class="meta">복사함 표시(누가·언제)는 늘 남아요.</p>
        ${dev ? `<label class="check-row"><input type="checkbox" data-copy-after="dev"${c.dev ? " checked" : ""}> 개발사항으로 만들기${n > 1 ? "(모두 한 항목에)" : ""}</label>
          ${item ? `<div class="fieldset"><label for="copy-dev-title">개발사항 제목</label><input id="copy-dev-title" maxlength="100" value="${esc(c.devTitle ?? item.title)}"></div>` : ""}` : ""}
        <label class="check-row"><input type="checkbox" data-copy-after="assign"${c.assign ? " checked" : ""}> 아무도 안 맡은 것은 내가 맡기</label>
        <label class="check-row"><input type="checkbox" data-copy-after="close"${c.close ? " checked" : ""}> 닫기</label></fieldset>`
        : '<p class="meta">복사함 표시는 문의 고치기 권한이 있어야 남아요.</p>'}
      <div class="actions"><button class="btn" type="button" data-act="close">닫기</button>
        ${c.failed ? '<button class="btn primary" type="button" data-act="copy-done">복사했어요</button>'
          : `<button class="btn primary" type="button" data-act="copy-do"${c.list ? "" : " disabled"}>${icon(P.copy)}복사</button>`}</div></div>`;
    openDialog(html, "wide");
  }

  /** The copy box's choice remembered on this device, so the next copy starts the same. */
  function keepCopyPrefs() {
    S.prefs.copy = { style: S.copy.style, group: S.copy.group, byKind: S.copy.byKind };
    savePrefs();
  }

  /** After the text is on the clipboard: 복사함 and the chosen steps on the server, then the list shows them. */
  async function copied() {
    const c = S.copy;
    if (!c?.list) return;
    keepCopyPrefs();
    const ids = c.list.map((q) => q.id);
    const n = ids.length;
    if (!can("inquiries", "edit")) {
      dialog.close();
      toast(`${n}건을 복사했어요. 개발할 때 붙여 넣으세요`);
      return;
    }
    const devTitle = document.getElementById("copy-dev-title")?.value.trim();
    const item = c.dev ? L.copyDevItem(c.list, location.origin) : null;
    const body = { ids, ...(item ? { dev: { title: devTitle || item.title, body: item.body } } : {}), ...(c.assign ? { assign: true } : {}), ...(c.close ? { status: "closed" } : {}) };
    const r = await act(() => api("/inquiries/copied", "POST", body),
      (x) => `${n}건을 복사했어요${x?.item ? ` · 개발사항 "${x.item.title}"을 만들었어요` : ""}${x?.status === "closed" ? " · 닫았어요" : ""}. 개발할 때 붙여 넣으세요`);
    dialog.close();
    if (!r) return;
    const mark = (q) => (ids.includes(q.id) ? { ...q, copied: r.copied, ...(r.status ? { status: r.status } : {}) } : q);
    if (S.data.inquiries?.inquiries) S.data.inquiries = { ...S.data.inquiries, inquiries: S.data.inquiries.inquiries.map(mark) };
    if (S.data.inquiry && ids.includes(S.data.inquiry.id)) S.data.inquiry = mark(S.data.inquiry);
    if (S.inquiryPick) S.inquiryPick = new Set();
    render({ keepScroll: true });
    refreshBadges(true);
    if (r.item || r.status || r.assigned) load({ quiet: true });
  }

  /** The picked questions in the order the list shows them. */
  const pickedIds = () => (S.data.inquiries?.inquiries ?? []).map((q) => q.id).filter((id) => S.inquiryPick?.has(id))
    .concat([...(S.inquiryPick ?? [])].filter((id) => !(S.data.inquiries?.inquiries ?? []).some((q) => q.id === id)));

  /** [ADMCP-02] 찾은 n건 모두 고르기: the rest of the filtered list's pages, up to the 100 one copy takes. */
  async function pickFound() {
    while (S.data.inquiries?.next && S.data.inquiries.inquiries.length < 100) {
      const before = S.data.inquiries.inquiries.length;
      await loadInquiries(undefined, true);
      if (S.data.inquiries.inquiries.length === before) break;
    }
    S.inquiryPick = new Set(S.data.inquiries.inquiries.slice(0, 100).map((q) => q.id));
    render({ keepScroll: true });
  }

  /** [ADMCP-02] Shift on a row picks every row between it and the one picked last, as a list on a computer does. */
  function pickRange(to) {
    const ids = (S.data.inquiries?.inquiries ?? []).map((q) => q.id);
    const a = ids.indexOf(S.pickAnchor);
    const b = ids.indexOf(to);
    if (a < 0 || b < 0) return false;
    for (const id of ids.slice(Math.min(a, b), Math.max(a, b) + 1)) S.inquiryPick.add(id);
    S.pickAnchor = to;
    render({ keepScroll: true });
    return true;
  }

  /** [ADMCP-02] Picking from a key or a long press: the pick mode opens with [id] picked, or [id] flips. */
  function togglePick(id) {
    if (!id) return;
    if (!S.inquiryPick) S.inquiryPick = new Set();
    if (S.inquiryPick.has(id)) S.inquiryPick.delete(id);
    else S.inquiryPick.add(id);
    S.pickAnchor = id;
    render({ keepScroll: true });
  }
  // ---- AT 1.29 끝 ----

  // ---- AM 1.28 기능 관리 ----
  // [ADMUX-21]~[ADMUX-29] 기능 관리(다크머지): the list with its filters, one flag in full, the calendar of dates (timeline),
  // the ways of working, and the home screen's warning for a legal flag that just turned on. The screens read
  // /api/admin/flags, /flags/<key> and /types; who may press what comes from the server (reach) and every locked
  // button says why. AN's 출시 hub points at the routes flags and types (통합계획 2-2).

  const FLAG_STATE = { forced_off: ["bad", "강제로 끔"], forced_on: ["info", "강제로 켬"], on: ["ok", "켜짐"], waiting: ["warn", "대기"], off: ["", "꺼짐"] };
  const FLAG_SHOW = [["all", "전체"], ["waiting", "대기"], ["on", "켜짐"], ["forced_off", "강제로 끔"], ["cleanup", "정리 대상"]];
  const FLAG_GROUPS = { money: "결제·광고", type: "근무 유형", law: "법 시행", server: "서버 기능", stats: "사용 통계", free: "무료로 연 프로 기능" };
  /** 1.22's 기능 스위치 by their flag (운영 screen's card links each to its flag). */
  const SWITCH_FLAGS = { PLAZA_WRITE: "plaza.write", COMPANY_REPORT: "company.report", INQUIRY_PHOTOS: "inquiry.photos", SHARE_CREATE: "share.create",
    USAGE_STATS: "usage.stats", PLAZA_NEWS: "plaza.news" };
  const PRIVACY_URL = "https://ywjmoney.github.io/-/privacy/";
  const LOCK = {
    off: "강제로 끄기는 최고 관리자나 '기능 강제로 끄기' 권한을 받은 관리자만 해요(결정 D97).",
    on: "강제로 켜기는 최고 관리자만 해요.",
    legal: "법적 기능이라 조건 바꾸기·확인·되돌리기는 최고 관리자만 해요.",
    edit: "조건을 바꾸려면 기능 관리 고치기 권한이 있어야 해요.",
  };

  const flagLink = (key) => L.routeHash("flags", key, {});
  const md = (d) => (d ? `${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일` : "");
  const statePill = (r) => {
    const [tone, label] = FLAG_STATE[r.state] ?? ["", r.state];
    const left = r.state === "waiting" && r.daysLeft !== null && r.daysLeft !== undefined && r.daysLeft >= 0 ? ` · ${r.daysLeft === 0 ? "오늘" : `${r.daysLeft}일 남음`}` : "";
    return `<span class="pill ${tone}">${label}${left}</span>`;
  };
  const riskPill = (r) => (r.risk === "LEGAL" ? '<span class="pill gold" title="결제·광고·새로 보내는 정보처럼 법적 조건이 붙은 기능">법적</span>' : "");
  const flagPills = (r) => [statePill(r), riskPill(r), r.warnings?.length ? '<span class="pill bad">고지 기간 확인</span>' : "",
    r.sunsetDue ? '<span class="pill">정리 대상</span>' : "", r.reviewDue ? '<span class="pill warn">다시 볼 날</span>' : ""].join("");

  /** What a step needs, by the reach the server sent: null when allowed, else the reason the button is locked. */
  function flagLock(r, reach, step) {
    if (reach.owner) return null;
    const legal = r.risk === "LEGAL";
    const forced = r.override?.force && !(r.override.until && Date.parse(r.override.until) <= Date.now()) ? r.override.force : null;
    if (step === "on") return LOCK.on;
    if (step === "off") return reach.off ? null : LOCK.off;
    if (step === "clear") return forced === "on" ? (reach.off ? null : LOCK.off) : legal ? LOCK.legal : reach.off ? null : LOCK.off;
    if (step === "conditions") return legal ? LOCK.legal : reach.edit ? null : LOCK.edit;
    if (step === "check") return legal ? LOCK.legal : reach.edit ? null : LOCK.edit;
    return null;
  }
  const lockedBtn = (label, lock, attrs, tone = "") => `<button class="btn small${tone ? ` ${tone}` : ""}" ${lock ? `disabled title="${esc(lock)}"` : attrs}>${esc(label)}</button>`;

  // ---- the list [ADMUX-21] ----

  function flagsView() {
    const d = S.data.flags;
    const timeline = S.flagView === "timeline";
    const head = `<div class="segmented" role="group" aria-label="보기">${seg("flag-view", "", S.flagView, "목록")}${seg("flag-view", "timeline", S.flagView, "일정")}</div>`;
    if (!d) return head + skeleton(3);
    if (timeline) return head + flagTimeline(d);
    const rows = d.flags;
    const match = (r) => ({ all: true, waiting: r.state === "waiting", on: r.state === "on" || r.state === "forced_on", forced_off: r.state === "forced_off", cleanup: r.sunsetDue }[S.flagShow] ?? true);
    const q = S.flagQuery.trim().toLowerCase();
    const found = rows.filter(match).filter((r) => !q || `${r.name} ${r.key} ${r.what}`.toLowerCase().includes(q));
    const counts = Object.fromEntries(FLAG_SHOW.map(([k]) => [k, rows.filter((r) => ({ all: true, waiting: r.state === "waiting", on: r.state === "on" || r.state === "forced_on",
      forced_off: r.state === "forced_off", cleanup: r.sunsetDue })[k]).length]));
    const alerts = rows.filter((r) => r.warnings?.length);
    const filters = `<div class="chips" role="group" aria-label="상태">${FLAG_SHOW.map(([k, label]) =>
        `<button type="button" class="chip pick" data-act="flag-show" data-key="${k}" aria-pressed="${S.flagShow === k}">${label}${counts[k] ? ` ${counts[k]}` : ""}</button>`).join("")}</div>
      <div class="searchbar"><input id="search" type="search" placeholder="이름이나 키로 찾기(예: billing, 결제)" value="${esc(S.flagQuery)}" autocomplete="off" enterkeyhint="search" aria-label="기능 찾기"></div>
      <p class="hint">앱 안에 들어 있는 기능이 언제 저절로 켜지는지(날짜·처리방침·확인), 지금 왜 그런지 보여요. 서버는 덮어쓸 것만 보내고, 휴대폰은 인터넷이 없어도 조건대로 켜요. 법적 기능은 강제로 켜도 처리방침 조건을 계속 지켜요.${d.statsPhones ? ` 켜진 비율은 지난 7일 '사용 통계 더 보내기'를 보낸 휴대폰 ${d.statsPhones}대 기준이에요.` : ""}</p>
      ${alerts.length ? `<p class="note bad" role="status">고지 기간이 모자란 기능이 ${alerts.length}개 있어요: ${alerts.map((r) => esc(r.name)).join(", ")}. 처리방침 9항(7일, 불리하면 30일)을 확인해 주세요.</p>` : ""}`;
    if (!found.length) return head + filters + empty(q ? "찾는 기능이 없어요" : "이 상태의 기능이 없어요", "", '<button class="btn" data-act="flag-show" data-key="all">전체 보기</button>');
    const groups = [...new Set(found.map((r) => r.group))];
    return head + filters + groups.map((g) => `<h2 class="day-head">${esc(FLAG_GROUPS[g] ?? g)}</h2><ul class="rows">${found.filter((r) => r.group === g).map((r) =>
      `<li><a class="row" href="${flagLink(r.key)}"><span class="row-main"><b>${esc(r.name)}</b><small>${esc(r.key)}${r.onShare !== null && r.onShare !== undefined ? ` · 휴대폰 ${r.onShare}%에서 켜짐` : ""}</small>
        <span class="preview">${esc(r.reason)}</span></span><span class="row-side pills">${flagPills(r)}</span>${icon(P.chevron)}</a></li>`).join("")}</ul>`).join("");
  }

  // ---- the calendar [ADMUX-25] ----

  function flagTimeline(d) {
    const today = L.flagDay(Date.now());
    const from = L.flagDay(Date.now() - 14 * 864e5);
    const items = [];
    const add = (day, text, to, tone, kind) => day && day >= from && items.push({ day, text, to, tone, kind });
    for (const r of d.flags) {
      for (const c of r.conditions.flatMap((c) => (c.type === "any" ? c.of : [c]))) {
        if (c.type !== "date") continue;
        const day = L.dateFrom(c, d.policy);
        if (day && day > today) add(day, `${r.name}: ${L.conditionLabel(c, d.policy)} 조건`, flagLink(r.key), r.risk === "LEGAL" ? "gold" : "info", "날짜 조건");
      }
      if (r.override?.until) add(L.flagDay(Date.parse(r.override.until)), `${r.name}: 강제${r.override.force === "on" ? " 켬" : " 끔"} 기한 끝`, flagLink(r.key), "warn", "강제 기한");
      if (r.override?.reviewAt) add(r.override.reviewAt, `${r.name}: 강제로 끈 것 다시 보기`, flagLink(r.key), "warn", "다시 볼 날");
    }
    for (const p of d.policy) {
      add(p.posted, `처리방침 ${p.version} 게시`, null, "", "처리방침");
      add(p.effective, `처리방침 ${p.version} 시행`, null, "info", "처리방침");
      if (p.heldUntil) add(p.heldUntil, `처리방침 ${p.version}: 동의하지 않은 휴대폰에도 시행`, null, "info", "처리방침");
    }
    items.sort((a, b) => a.day.localeCompare(b.day));
    let month = "";
    const out = [];
    for (const x of items) {
      const m = x.day.slice(0, 7);
      if (m !== month) {
        if (month) out.push("</ul>");
        out.push(`<h2 class="day-head">${Number(m.slice(0, 4))}년 ${Number(m.slice(5))}월</h2><ul class="rows agenda">`);
        month = m;
      }
      const n = L.daysLeft(x.day);
      const when = x.day === today ? "오늘" : n > 0 ? `D-${n}` : "지남";
      const inner = `<span class="date">${md(x.day)}<small>${when}</small></span><span class="row-main"><b class="wrap">${esc(x.text)}</b></span><span class="pill ${x.tone}">${esc(x.kind)}</span>`;
      out.push(`<li>${x.to ? `<a class="row" href="${x.to}">${inner}</a>` : `<div class="row">${inner}</div>`}</li>`);
    }
    if (month) out.push("</ul>");
    const o = S.data.flagOps;
    const chores = o ? `<section class="card"><div class="card-head"><h2>이달 운영 할 일</h2><a class="btn small ghost" href="${href("chores")}">이달 할 일에서 보기</a></div>
      <ul class="rows">${o.items.map((i) => `<li><div class="row"><span class="row-main"><b class="wrap">${esc(i.text)}</b><small>${esc(i.source)}</small></span>
        <span class="pill ${i.done ? "ok" : ""}">${i.done ? "마침" : "남음"}</span></div></li>`).join("")}</ul></section>` : "";
    const owner = d.reach.owner;
    const policy = `<section class="card"><div class="card-head"><h2>처리방침 일정</h2>${owner ? '<button class="btn small" data-act="flag-policy">판 더하기·날짜 고치기</button>' : ""}</div>
      <p class="hint">휴대폰은 앱에 든 날짜를 쓰다가 서버가 고친 날짜로 바꿔요. 시행일이 바뀌면 그 판에 기댄 기능도 함께 움직여요.${owner ? "" : " 고치기는 최고 관리자만 해요."}</p>
      <ul class="rows">${d.policy.map((p) => `<li><div class="row"><span class="row-main"><b>처리방침 ${esc(p.version)}${p.changed ? ' <span class="pill info">고침</span>' : ""}</b>
        <small>게시 ${esc(p.posted)} · 시행 ${esc(p.effective)}${p.heldUntil ? ` · 동의 없이 ${esc(p.heldUntil)}` : ""}${p.unfavourable ? " · 불리한 변경(30일 전 고지)" : ""}</small></span>
        ${owner ? `<button class="btn small ghost" data-act="flag-policy" data-version="${esc(p.version)}">고치기</button>` : ""}</div></li>`).join("")}</ul></section>`;
    return `${out.length ? out.join("") : empty("다가오는 날짜가 없어요", "날짜 조건, 강제 기한, 다시 볼 날, 처리방침 날짜가 여기에 모여요.")}<div class="grid two">${policy}${chores}</div>`;
  }

  // ---- one flag [ADMUX-22][ADMUX-23][ADMUX-24] ----

  function flagHeading() {
    const r = S.data.flag;
    const ok = r && r.key === S.route.id;
    return { title: ok ? r.name : "기능", sub: ok ? `${r.key} · ${(FLAG_STATE[r.state] ?? ["", ""])[1]}` : "", back: href("flags") };
  }

  function flagView() {
    const r = S.data.flag;
    if (!r || r.key !== S.route.id) return skeleton(3);
    const reach = r.reach;
    const share = r.onShare !== null && r.onShare !== undefined ? `휴대폰 ${r.onShare}%` : null;
    const top = `<section class="card${r.alert ? " flag-alert" : ""}"><div class="card-head"><h2>지금 상태</h2><span class="pills">${flagPills(r)}</span></div>
      <p class="lead">${esc(r.reason)}</p>
      ${r.next ? `<p class="meta">다음 날짜: ${md(r.next)}(${r.daysLeft === 0 ? "오늘" : `${r.daysLeft}일 남음`}) 0시, 한국 시간</p>` : ""}
      ${r.override?.force ? `<p class="note ${r.override.force === "off" ? "bad" : "info"}">${esc(r.override.by || "시스템")} · ${esc(moment(r.override.at))}에 강제로 ${r.override.force === "off" ? "껐어요" : "켰어요"}${r.override.auto ? "(사용량 자동 쉼)" : ""}: ${esc(r.override.why)}${r.override.until ? ` · ${esc(moment(r.override.until))}까지` : ""}${r.override.reviewAt ? ` · 다시 볼 날 ${esc(r.override.reviewAt)}` : ""}</p>` : ""}
      ${r.alert ? `<p class="note bad" role="status">켜진 지 24시간이 안 됐어요(${esc(ago(r.onSince))}). 문제가 보이면 바로 강제로 꺼 주세요.</p>` : ""}
      ${r.warnings.map((w) => `<p class="note warn">${esc(w)}</p>`).join("")}
      ${r.sunsetDue ? `<p class="note">정리 예정 판(${esc(r.sunset)})이 나왔어요. 두 판 넘게 켜져 있었다면 코드에서 이 플래그를 걷어 내요.</p>` : ""}
      <div class="actions start">
        ${r.state === "forced_off" ? lockedBtn("강제 끔 풀기", flagLock(r, reach, "clear"), `data-act="flag-clear" data-key="${esc(r.key)}"`, "primary")
          : lockedBtn("강제로 끄기", flagLock(r, reach, "off"), `data-act="flag-force" data-key="${esc(r.key)}" data-force="off"`, "danger")}
        ${r.override?.force === "on" ? lockedBtn("강제 켬 풀기", flagLock(r, reach, "clear"), `data-act="flag-clear" data-key="${esc(r.key)}"`)
          : r.state !== "forced_off" ? lockedBtn("강제로 켜기", flagLock(r, reach, "on"), `data-act="flag-force" data-key="${esc(r.key)}" data-force="on"`) : ""}
        ${lockedBtn("조건 고치기", flagLock(r, reach, "conditions"), `data-act="flag-cond" data-key="${esc(r.key)}"`)}</div>
      ${[["off", "강제로 끄기"], ["on", "강제로 켜기"], ["conditions", "조건 고치기"]].map(([s, label]) => [label, flagLock(r, reach, s)]).filter(([, l]) => l)
        .map(([label, l]) => `<p class="help">${esc(label)}: ${esc(l)}</p>`).join("")}</section>`;

    const checks = r.checks ?? {};
    const lockCheck = flagLock(r, reach, "check");
    const list = `<section class="card"><h2>켜지는 조건</h2>
      ${r.override?.conditions ? `<p class="note info">앱에 든 조건 대신 관리자가 바꾼 조건이에요(${esc(r.override.by)} · ${esc(moment(r.override.at))}).</p>` : ""}
      ${r.checklist.length ? `<ul class="checklist">${r.checklist.map((c) => {
        const cls = c.met === true ? "met" : c.met === "phone" ? "phone" : "unmet";
        const mark = c.met === true ? "✓" : c.met === "phone" ? "~" : "○";
        const what = c.met === true ? "채움" : c.met === "phone" ? `휴대폰마다 달라요${share ? ` · 지금 켜진 ${share}` : ""}` : "남음";
        const manual = c.keys.map((k) => {
          const got = checks[k];
          return got ? `<small>${esc(r.manualNames?.[k] ?? k)}: ${esc(got.by || "관리자")} · ${esc(moment(got.at))}에 확인${got.note ? ` · ${esc(got.note)}` : ""}</small>
              ${lockedBtn("확인 취소", lockCheck, `data-act="flag-check" data-name="${esc(k)}" data-done=""`, "ghost")}`
            : lockedBtn("확인했어요", lockCheck, `data-act="flag-check" data-name="${esc(k)}" data-done="1"`, "primary");
        }).join("");
        return `<li class="${cls}"><span class="mark" aria-hidden="true">${mark}</span><span class="c-main">${esc(c.label)}<small>${what}</small></span>${manual}</li>`;
      }).join("")}</ul>` : `<p class="meta">조건이 없어요. ${r.default ? "기본으로 켜져 있어요." : "기본으로 꺼져 있고, 강제로 켜야 열려요."}</p>`}
      ${lockCheck && r.manualKeys.length ? `<p class="help">확인: ${esc(lockCheck)}</p>` : ""}
      ${flagRangeBlock(r, reach)}</section>`;

    const b = r.basis ?? {};
    const about = `<section class="card"><h2>무엇이고 왜 꺼 뒀나</h2>
      <dl class="kv"><dt>하는 일</dt><dd>${esc(r.what)}</dd><dt>꺼 둔 이유</dt><dd>${esc(r.whyOff)}</dd>
        <dt>근거</dt><dd>${[b.policy ? `<a href="${PRIVACY_URL}" target="_blank" rel="noopener">처리방침 ${esc(b.policy)}</a>` : "", b.law ? esc(b.law) : "", b.note ? esc(b.note) : "",
          b.decision ? `결정 ${esc(b.decision)}` : "", b.req ? esc(b.req) : ""].filter(Boolean).join(" · ") || "–"}</dd>
        <dt>담당·만든 판</dt><dd>${esc(r.owner)} · ${esc(r.since)}${r.sunset ? ` · 정리 예정 ${esc(r.sunset)}` : ""}</dd>
        ${r.switchKey ? `<dt>옛 이름</dt><dd>기능 스위치 ${esc(r.switchKey)}(1.27까지의 앱은 이 이름으로 읽어요)</dd>` : ""}</dl></section>`;

    const phones = `<section class="card"><h2>휴대폰</h2>
      ${r.statsPhones ? `<p class="lead">${share ? `지난 7일 ${share}에서 켜져 있었어요` : "아직 켜진 휴대폰이 없어요"}</p><p class="meta">'사용 통계 더 보내기'를 보낸 휴대폰 ${r.statsPhones}대 기준(휴대폰 구별 없음)</p>
        ${r.series.length ? L.barChart(r.series.map((x) => x.share ?? 0), { labels: r.series.map((x) => x.day.slice(5).replace("-", "/")), name: "날마다 켜진 휴대폰 비율(%)" }) : ""}
        ${r.versions.length ? `<ul class="rows">${r.versions.map((v) => `<li><div class="row"><span class="row-main"><b>${esc(v.app)}</b><small>${v.code ? `번호 ${v.code}` : "번호 모름"}</small></span>
          <span class="row-side"><b>${v.share ?? "–"}%</b><span class="meta">${v.phones}대</span></span></div></li>`).join("")}</ul>` : ""}`
        : '<p class="meta">아직 켜진 기능 목록을 보낸 휴대폰이 없어요. \'사용 통계 더 보내기\'(처리방침 3.2)가 시행되고 동의한 휴대폰부터 와요.</p>'}
      <h3 class="section">켜지기 전후 ${r.compare.days}일</h3>
      ${r.compare.measured ? `<dl class="kv"><dt>문제 신고</dt><dd>${r.compare.bugsBefore} → ${r.compare.bugsAfter}${r.compare.bugsAfter > r.compare.bugsBefore ? ' <span class="pill warn">늘었어요</span>' : ""}</dd>
        <dt>서버 오류</dt><dd>${r.compare.errorsBefore} → ${r.compare.errorsAfter}${r.compare.errorsAfter > r.compare.errorsBefore ? ' <span class="pill warn">늘었어요</span>' : ""}</dd></dl>
        <p class="help">${esc(moment(r.compare.pivot))}(서버가 마지막으로 켜짐·꺼짐을 본 때) 앞뒤예요. 켠 휴대폰과 안 켠 휴대폰을 나눠 세지는 않아요.</p>` : '<p class="meta">서버가 아직 이 기능의 켜짐·꺼짐을 기록하지 않았어요(매시 17분).</p>'}</section>`;

    const billing = r.key === "billing" ? `<section class="card"><h2>정식 출시</h2>
      <dl class="kv"><dt>정식 출시일</dt><dd>${r.launchedAt ? esc(moment(new Date(r.launchedAt).toISOString())) : "아직(처음 켜지는 순간 서버가 한 번 적어요)"}</dd></dl>
      <form data-form="flag-thanks"><div class="fieldset"><label for="flag-thanks">테스트 참여자 감사 보상(개월)</label>
        <input id="flag-thanks" name="thanks" type="number" min="0" max="12" value="${r.thanksMonths ?? 1}"${reach.owner ? "" : " disabled"}>
        <span class="help">정식 출시 전에 설치한 휴대폰에 출시일부터 드리는 프로 개월 수예요. ${reach.owner ? "" : "최고 관리자만 바꿔요."}</span></div>
        ${reach.owner ? '<div class="actions"><button class="btn" type="submit">저장</button></div>' : ""}</form></section>` : "";

    const sim = flagSimCard(r);
    const hist = `<section class="card"><h2>기록</h2>${r.history.length ? `<ul class="feed">${r.history.map((h) => `<li><span class="when">${esc(moment(h.at))}</span>
      <span><b>${esc(ACTION_NAMES[h.action] ?? h.action)}</b> <span class="meta">${esc(flagSummary(h.before))} → ${esc(flagSummary(h.after))}</span>${h.why ? `<span class="meta">${esc(h.why)}</span>` : ""}</span>
      <span class="who">${esc(h.actor)}</span></li>`).join("")}</ul>` : '<p class="meta">아직 바꾼 적이 없어요.</p>'}</section>`;
    return `${top}<div class="grid two">${list}${about}${phones}${billing}${sim}</div>${hist}`;
  }

  /**
   * 1.28 통합 [FLAG-36] AL-D3 앱 판 범위: the versionCode range the flag works in (판단 2단계). Outside it a phone keeps it
   * off whatever else holds, so a feature that breaks on one version can be kept from it without a force for everyone.
   */
  function flagRangeBlock(r, reach) {
    const lo = r.override?.minApp ?? null;
    const hi = r.override?.maxApp ?? null;
    const lock = flagLock(r, reach, "conditions");
    return `<h3 class="section">앱 판 범위</h3>
      <p class="meta">${lo == null && hi == null ? "모든 앱 판에서 조건대로 켜져요." : `앱 번호 ${esc(L.appRange(lo, hi))}에서만 켜질 수 있어요. 그 밖의 판은 다른 조건과 상관없이 꺼져요.`}</p>
      <div class="actions start">${lockedBtn("앱 판 범위 고치기", lock, `data-act="flag-range" data-key="${esc(r.key)}"`)}</div>
      ${lock ? `<p class="help">앱 판 범위: ${esc(lock)}</p>` : ""}`;
  }

  function openFlagRange(key) {
    const r = S.data.flag;
    const lo = r.override?.minApp ?? "";
    const hi = r.override?.maxApp ?? "";
    openDialog(`<form data-form="flag-range" data-key="${esc(key)}"><h2>${esc(r.name)}의 앱 판 범위</h2>
      <p>앱 번호(versionCode)가 이 범위 밖인 휴대폰은 이 기능을 꺼요. 강제로 끈 것 다음으로 먼저 따져요. 비우면 그쪽 끝이 없어요.</p>
      <div class="fieldset"><label for="fr-min">이 번호부터</label><input id="fr-min" name="minApp" type="number" min="1" max="999999" inputmode="numeric" value="${esc(lo)}" placeholder="예: 39"></div>
      <div class="fieldset"><label for="fr-max">이 번호까지</label><input id="fr-max" name="maxApp" type="number" min="1" max="999999" inputmode="numeric" value="${esc(hi)}" placeholder="비우면 끝 없음"></div>
      <div class="fieldset"><label for="fr-why">이유(필수)</label><input id="fr-why" name="why" maxlength="300" required></div>
      <div class="actions">${lo !== "" || hi !== "" ? '<button class="btn ghost" type="submit" name="reset" value="1" formnovalidate>범위 없애기</button>' : ""}
        <button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`);
  }

  /** One side of a history line in words. */
  function flagSummary(o) {
    if (!o) return "없음";
    if (o.check) return `${S.data.flag?.manualNames?.[o.check] ?? o.check} 확인`;
    if (o.thanksMonths !== undefined) return `${o.thanksMonths}개월`;
    if (o.effective) return `게시 ${o.posted} · 시행 ${o.effective}${o.heldUntil ? ` · 동의 없이 ${o.heldUntil}` : ""}`;
    const parts = [];
    if (o.force) parts.push(`강제로 ${o.force === "on" ? "켬" : "끔"}`);
    if (o.conditions) parts.push(`조건 ${L.conditionText(o.conditions).replace(/\n/g, " · ")}`);
    if (o.minApp != null || o.maxApp != null) parts.push(`앱 판 ${L.appRange(o.minApp, o.maxApp)}`);
    return parts.join(", ") || "조건대로";
  }

  /** [ADMUX-24] 미리 보기: one pretend phone through the same evaluator, to see what a change does before it is made. */
  function flagSimCard(r) {
    const sim = S.flagSim ?? {};
    const today = sim.day || L.flagDay(Date.now());
    const opt = (v, label, cur) => `<option value="${v}"${cur === v ? " selected" : ""}>${label}</option>`;
    return `<section class="card"><h2>미리 보기</h2><p class="hint">이런 휴대폰이면 어떻게 될지 서버와 같은 판단기로 봐요. 아무것도 바뀌지 않아요.</p>
      <form id="flag-sim" class="sim-grid" aria-describedby="flag-sim-out">
        <div class="fieldset"><label for="sim-day">날짜</label><input id="sim-day" type="date" data-change="flag-sim" name="day" value="${esc(today)}"></div>
        <div class="fieldset"><label for="sim-app">앱 번호</label><input id="sim-app" type="number" min="1" data-change="flag-sim" name="app" value="${esc(sim.app ?? "39")}"></div>
        <div class="fieldset"><label for="sim-agreed">동의한 처리방침</label><input id="sim-agreed" data-change="flag-sim" name="agreed" value="${esc(sim.agreed ?? "")}" placeholder="예: 3.1"></div>
        <div class="fieldset"><label for="sim-installed">설치한 날</label><input id="sim-installed" type="date" data-change="flag-sim" name="installed" value="${esc(sim.installed ?? "")}"></div>
        ${Object.entries(L.CONSENT_NAMES).map(([k, label], i) => `<div class="fieldset"><label for="sim-c${i}">${esc(label)}</label><select id="sim-c${i}" data-change="flag-sim" name="consent:${k}">
          ${opt("", "아직 안 물음", sim[`consent:${k}`] ?? "")}${opt("yes", "동의", sim[`consent:${k}`] ?? "")}${opt("no", "거절", sim[`consent:${k}`] ?? "")}</select></div>`).join("")}
        <div class="fieldset"><label for="sim-type">근무 유형</label><select id="sim-type" data-change="flag-sim" name="workType">${L.WORK_TYPES.map((w) => opt(w, L.WORK_NAMES[w], sim.workType ?? "SHIFT")).join("")}</select></div>
        <div class="fieldset"><label for="sim-bucket">설치 자리(0~99)</label><input id="sim-bucket" type="number" min="0" max="99" data-change="flag-sim" name="bucket" value="${esc(sim.bucket ?? "50")}"></div>
        <label class="check-row"><input type="checkbox" data-change="flag-sim" name="preview"${sim.preview ? " checked" : ""}>관리자 휴대폰 미리 보기</label>
      </form><div id="flag-sim-out" class="cond-out" aria-live="polite">${flagSimText(r)}</div></section>`;
  }

  function flagSimText(r) {
    const s = S.flagSim ?? {};
    const day = s.day || L.flagDay(Date.now());
    const consents = {};
    for (const k of Object.keys(L.CONSENT_NAMES)) if (s[`consent:${k}`]) consents[k] = s[`consent:${k}`] === "yes";
    const phone = { app: Number(s.app ?? 39), agreed: s.agreed || null, installed: s.installed || null, consents, workType: s.workType ?? "SHIFT",
      profile: [], bucket: Number(s.bucket ?? 50), admin: Boolean(s.preview), preview: Boolean(s.preview) };
    const ev = L.evalFlag({ conditions: r.catalogConditions, default: r.default, risk: r.risk }, r.override,
      { now: Date.parse(`${day}T12:00:00+09:00`), policy: r.policy, checks: r.checks ?? {}, phone });
    return `<p class="lead"><span class="pill ${ev.on ? "ok" : "warn"}">${ev.on ? "켜짐" : "꺼짐"}</span> ${esc(ev.reason)}</p>
      <ul class="checklist">${ev.conditions.map((c) => `<li class="${c.met === true ? "met" : "unmet"}"><span class="mark" aria-hidden="true">${c.met === true ? "✓" : "○"}</span><span class="c-main">${esc(c.label)}</span></li>`).join("")}</ul>`;
  }

  // ---- dialogs ----

  function openFlagForce(key, force, why = "") {
    const r = S.data.flag?.key === key ? S.data.flag : S.data.flags?.flags.find((x) => x.key === key) ?? S.overview?.flagAlerts?.find((x) => x.key === key) ?? { key, name: key };
    const off = force === "off";
    const tomorrow = L.flagDay(Date.now() + 864e5);
    openDialog(`<form data-form="flag-force" data-key="${esc(key)}" data-force="${force}"><h2>${esc(r.name)}을(를) 강제로 ${off ? "끌까요" : "켤까요"}?</h2>
      <p>${off ? "모든 휴대폰에서 다음 확인 때(하루 안, 앱을 열 때) 꺼지고, 서버도 바로 그 기능을 받지 않아요. 조건이 다 차도 켜지지 않아요."
        : "조건과 상관없이 켜요. 법적 기능은 강제로 켜도 처리방침 조건이 남은 휴대폰에서는 꺼져 있어요."}</p>
      <div class="fieldset"><label for="ff-why">이유(필수, 기록에 남아요)</label><textarea id="ff-why" name="why" maxlength="300" required placeholder="${off ? "예: 결제 화면 오류 신고가 몰려요" : "예: Play 승인을 받았어요"}">${esc(why)}</textarea></div>
      ${off ? `<div class="fieldset"><label for="ff-review">다시 볼 날(선택)</label><input id="ff-review" name="reviewAt" type="date" min="${L.flagDay(Date.now())}">
        <span class="help">그날 목록과 메뉴 숫자로 다시 보라고 알려요. 저절로 켜지지는 않아요.</span></div>` : ""}
      <div class="fieldset"><label for="ff-until">이날 0시에 저절로 풀기(선택)</label><input id="ff-until" name="until" type="date" min="${tomorrow}">
        <span class="help">비우면 풀 때까지 그대로예요. 1년 안으로 골라요.</span></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn ${off ? "danger" : "primary"}" type="submit">강제로 ${off ? "끄기" : "켜기"}</button></div></form>`);
  }

  function openFlagConditions(key) {
    const r = S.data.flag;
    const text = L.conditionText(r.conditions);
    openDialog(`<form data-form="flag-cond" data-key="${esc(key)}"><h2>${esc(r.name)}의 켜지는 조건</h2>
      <p>한 줄에 조건 하나, 모두 채우면 켜져요. 'or'로 이으면 그중 하나면 돼요. 저장하면 휴대폰은 앱에 든 조건 대신 이 조건을 써요.</p>
      <div class="fieldset"><label for="flag-cond">조건</label><textarea id="flag-cond" name="conditions" spellcheck="false" aria-describedby="flag-cond-out">${esc(text)}</textarea>
        <span class="help">예: date >= 2026-11-10 · date >= policy 3.1 · policy 3.1 · consent ads? · profile pay.setup · app 39..45 · workType SHIFT,DAY · rollout 10% · manual play.production · admin</span></div>
      <div id="flag-cond-out" class="cond-out" aria-live="polite">${flagCondPreview(text)}</div>
      <div class="fieldset"><label for="fc-why">이유(필수)</label><input id="fc-why" name="why" maxlength="300" required></div>
      <div class="actions">${r.override?.conditions ? '<button class="btn ghost" type="submit" name="reset" value="1" formnovalidate>앱 조건으로 되돌리기</button>' : ""}
        <button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`, "wide");
  }

  /** The editor's lines as the checklist will say them, or the first mistake. */
  function flagCondPreview(text) {
    const got = L.parseConditions(text);
    if (got.error) return `<p class="note bad" role="alert">${esc(got.error)}</p>`;
    const unknown = JSON.stringify(got.list).match(/"type":"manual","key":"([^"]+)"/g)?.map((m) => m.slice(23, -1)).filter((k) => !Object.hasOwn(L.MANUAL_NAMES, k)) ?? [];
    if (unknown.length) return `<p class="note bad" role="alert">모르는 확인 이름이에요: ${esc(unknown[0])}. ${Object.keys(L.MANUAL_NAMES).join(", ")} 가운데에서 골라요.</p>`;
    if (!got.list.length) return '<p class="note">조건이 없으면 기본값을 따라요.</p>';
    return `<ol class="lines">${got.list.map((c) => `<li>${esc(L.conditionLabel(c, S.data.flag?.policy ?? []))}</li>`).join("")}</ol>`;
  }

  function openFlagPolicy(version) {
    const p = (S.data.flags?.policy ?? []).find((x) => x.version === version) ?? null;
    openDialog(`<form data-form="flag-policy"><h2>${p ? `처리방침 ${esc(p.version)} 날짜` : "처리방침 판 더하기"}</h2>
      <p>게시일·시행일을 바꾸면 그 판에 기대는 기능(날짜 조건·동의)도 같이 움직여요. 휴대폰은 다음 확인 때 따라와요.</p>
      <div class="fieldset"><label for="fp-version">판</label><input id="fp-version" name="version" required pattern="\\d{1,2}\\.\\d{1,2}" value="${esc(p?.version ?? "")}"${p ? " readonly" : ""} placeholder="예: 3.3"></div>
      <div class="fieldset"><label for="fp-posted">게시일</label><input id="fp-posted" name="posted" type="date" required value="${esc(p?.posted ?? "")}"></div>
      <div class="fieldset"><label for="fp-effective">시행일</label><input id="fp-effective" name="effective" type="date" required value="${esc(p?.effective ?? "")}"></div>
      <div class="fieldset"><label for="fp-held">동의하지 않은 휴대폰에도 시행하는 날(선택)</label><input id="fp-held" name="heldUntil" type="date" value="${esc(p?.heldUntil ?? "")}"></div>
      <label class="check-row"><input type="checkbox" name="unfavourable"${p?.unfavourable ? " checked" : ""}>이용자에게 불리한 변경(30일 전에 알려요)</label>
      <div class="fieldset"><label for="fp-why">이유(필수)</label><input id="fp-why" name="why" maxlength="300" required></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`);
  }

  // ---- 근무 유형 [ADMUX-26] ----

  function typesView() {
    const d = S.data.types;
    if (!d) return skeleton(2);
    const shown = d.types.filter((t) => t.on).length;
    const lock = d.reach.off ? null : LOCK.off;
    return `<p class="hint">숨긴 유형은 첫 질문, 근무 형태 화면, 새 달력 만들기, 직업 찾기, 둘러보기 예시에서만 빠져요. 이미 그 유형을 쓰는 달력·백업 복원·회사 파일은 그대로 열리고, 최고 관리자로 연결한 휴대폰에는 '숨김'으로 보여요. 휴대폰은 하루 안에 따라와요.</p>
      <ul class="rows">${d.types.map((t) => {
        const last = t.on && shown <= 1;
        const button = t.on ? lockedBtn("숨기기", lock ?? (last ? "마지막 남은 유형은 숨길 수 없어요." : null), `data-act="type-hide" data-type="${t.style}"`)
          : lockedBtn("보이기", lock, `data-act="type-show" data-type="${t.style}"`, "primary");
        return `<li class="item"><span class="row-main"><b>${esc(t.label)}</b><small>${esc(t.key)}${t.override?.force === "off" ? ` · ${esc(t.override.by || "관리자")}: ${esc(t.override.why)}` : ""}</small></span>
          <span class="pill ${t.on ? "ok" : "warn"}">${t.on ? "보임" : "숨김"}</span>${button}<a class="btn small ghost" href="${flagLink(t.key)}">자세히</a></li>`;
      }).join("")}</ul>
      ${lock ? `<p class="note">${esc(lock)}</p>` : '<p class="help">하나는 늘 보여요. 숨기기·보이기는 모두 기록에 남아요.</p>'}`;
  }

  // ---- home [ADMUX-27][FLAG-28] ----

  /** A legal flag that turned on in the last 24 hours: at the top of 홈, with a one-tap force off (결정 D99). */
  function flagAlertCard() {
    const list = S.overview?.flagAlerts ?? [];
    if (!list.length) return "";
    return list.map((a) => `<section class="card flag-alert" role="status"><div class="card-head"><h2>${esc(a.name)}이(가) 켜졌어요</h2><span class="pill bad">${esc(ago(a.since))}</span></div>
      <p class="hint">법적 기능이 조건을 채워 저절로 켜졌어요. 24시간 동안 여기 보여요. 결제·광고에 문제가 보이면 바로 강제로 꺼 주세요.</p>
      <div class="actions start">${can("flagOff", "edit") ? `<button class="btn danger" data-act="flag-force" data-key="${esc(a.key)}" data-force="off" data-why="켜진 뒤 문제가 보여 홈에서 바로 껐어요">강제로 끄기</button>` : ""}
        <a class="btn" href="${flagLink(a.key)}">자세히</a></div></section>`).join("");
  }

  // ---- 분석 › 기능별 효과 (1.28 통합) [ADMUX-40][FLAG-30] ----

  /**
   * Each flag's share of phones (stats.more, last 7 days) and problem reports and server errors in the 7 days
   * before and after the server saw it turn on or off: the flag page's numbers for all flags side by side.
   */
  function effectsView() {
    const d = S.data.effects;
    if (!d) return skeleton(3);
    const rows = [...d.flags].sort((a, b) => (b.compare ? Date.parse(b.compare.pivot) : 0) - (a.compare ? Date.parse(a.compare.pivot) : 0)
      || (b.onShare ?? -1) - (a.onShare ?? -1));
    const up = (a, b) => (b > a ? ' <span class="pill warn">늘었어요</span>' : "");
    const line = (r) => {
      const c = r.compare;
      const share = r.onShare !== null && r.onShare !== undefined ? `휴대폰 ${r.onShare}%에서 켜짐` : "켜진 휴대폰 수 모름";
      // AU 1.29 [NXT-07]: the same reports split by whether the flag was on for the phone (문의 시각·앱 판으로만).
      const sp = c?.split;
      const split = sp ? ` · 켜진 휴대폰 ${sp.on}${sp.maybe ? `(+휴대폰마다 다름 ${sp.maybe})` : ""} / 꺼진 휴대폰 ${sp.off}${sp.unknown + sp.unseen ? ` / 모름 ${sp.unknown + sp.unseen}` : ""}` : "";
      const nums = c ? `<span class="preview">${esc(moment(c.pivot))} 앞뒤 ${c.days}일 · 문제 신고 ${c.bugsBefore} → ${c.bugsAfter}${up(c.bugsBefore, c.bugsAfter)}${split} · 서버 오류 ${c.errorsBefore} → ${c.errorsAfter}${up(c.errorsBefore, c.errorsAfter)}</span>`
        : '<span class="preview">서버가 아직 켜짐·꺼짐을 보지 않았어요</span>';
      const inner = `<span class="row-main"><b>${esc(r.name)}</b><small>${esc(r.key)} · ${share}</small>${nums}</span><span class="row-side pills">${statePill(r)}${riskPill(r)}</span>`;
      return `<li>${d.flagsReach ? `<a class="row" href="${flagLink(r.key)}">${inner}${icon(P.chevron)}</a>` : `<div class="row">${inner}</div>`}</li>`;
    };
    const measured = rows.filter((r) => r.compare);
    const rest = rows.filter((r) => !r.compare);
    return `<p class="hint">기능마다 켜진 휴대폰 비율(지난 7일 '사용 통계 더 보내기'를 보낸 휴대폰${d.statsPhones ? ` ${d.statsPhones}대` : ""} 기준, 휴대폰 구별 없음)과
        서버가 켜짐·꺼짐을 본 때 앞뒤 7일의 문제 신고·서버 오류를 나란히 봐요. 문제 신고는 보낸 시각과 앱 판으로 그 기능이 켜진 휴대폰·꺼진 휴대폰으로도 나눠요(동의·근무 유형처럼 휴대폰만 아는 조건은 '휴대폰마다 다름').${d.flagsReach ? " 줄을 누르면 그 기능의 자세한 기록이 열려요." : ""}</p>
      ${d.statsPhones ? "" : '<p class="note">아직 켜진 기능 목록을 보낸 휴대폰이 없어요. 처리방침 3.2가 시행되고 동의한 휴대폰부터 와요.</p>'}
      ${measured.length ? `<h2 class="day-head">켜짐·꺼짐을 본 기능</h2><ul class="rows">${measured.map(line).join("")}</ul>` : ""}
      ${rest.length ? `<h2 class="day-head">아직 바뀐 적 없는 기능</h2><ul class="rows">${rest.map(line).join("")}</ul>` : ""}`;
  }

  const FLAG_ACTIONS = {
    "flag-show": ({ key }) => {
      S.flagShow = key;
      syncUrl();
      render({ keepScroll: true });
    },
    "flag-view": ({ key }) => {
      S.flagView = key;
      syncUrl();
      render({ keepScroll: true });
      load();
    },
    "flag-force": ({ key, force, why }) => openFlagForce(key, force, why ?? ""),
    "flag-clear": ({ key }) => {
      const r = S.data.flag;
      confirmBox("강제를 풀까요?", `${r?.name ?? key}이(가) 다시 조건대로 켜지고 꺼져요. 지금 조건이면 ${r?.checklist?.every((c) => c.met !== false) ? "켜져요" : "꺼진 채예요"}.`, "풀기", async () => {
        const d = await act(() => api(`/flags/${encodeURIComponent(key)}/force`, "POST", { force: null }), "강제를 풀었어요");
        if (d) S.data.flag = d;
        render({ keepScroll: true });
        load({ quiet: true });
      }, "primary");
    },
    "flag-cond": ({ key }) => openFlagConditions(key),
    "flag-range": ({ key }) => openFlagRange(key),
    "flag-check": ({ name, done }) => {
      const label = S.data.flag?.manualNames?.[name] ?? name;
      if (done) {
        openDialog(`<form data-form="flag-check" data-name="${esc(name)}"><h2>${esc(label)}, 확인했나요?</h2>
          <p>확인하면 이 조건을 기다리는 기능이 다른 조건을 채운 대로 바로 켜질 수 있어요. 누가 언제 확인했는지 남아요.</p>
          <div class="fieldset"><label for="fk-note">메모(선택)</label><input id="fk-note" name="note" maxlength="300" placeholder="예: Play Console 출시 개요에서 봄"></div>
          <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">확인했어요</button></div></form>`);
        return;
      }
      confirmBox(`${label} 확인을 취소할까요?`, "이 조건을 기다리는 기능이 다시 대기로 돌아가요.", "확인 취소", async () => {
        await act(() => api(`/checks/${encodeURIComponent(name)}`, "POST", { done: false }), "확인을 취소했어요");
        load();
      });
    },
    "flag-policy": ({ version }) => openFlagPolicy(version ?? null),
    "type-show": async ({ type }) => {
      const d = await act(() => api("/types", "POST", { type, show: true }), "다시 보여요. 휴대폰은 하루 안에 따라와요");
      if (d) S.data.types = d;
      render({ keepScroll: true });
    },
    "type-hide": ({ type }) => openDialog(`<form data-form="type-hide" data-type="${esc(type)}"><h2>${esc(L.WORK_NAMES[type] ?? type)} 유형을 숨길까요?</h2>
      <p>새로 고르는 곳에서만 빠지고, 이미 쓰는 달력은 그대로예요.</p>
      <div class="fieldset"><label for="th-why">이유(필수)</label><input id="th-why" name="why" maxlength="300" required placeholder="예: 화면을 다듬는 중이에요"></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">숨기기</button></div></form>`),
  };

  const FLAG_FORMS = {
    "flag-force": async (form) => {
      const data = new FormData(form);
      const { key, force } = form.dataset;
      const body = { force, why: String(data.get("why") ?? "").trim(), until: data.get("until") || null, ...(force === "off" ? { reviewAt: data.get("reviewAt") || null } : {}) };
      const d = await act(() => api(`/flags/${encodeURIComponent(key)}/force`, "POST", body), force === "off" ? "강제로 껐어요. 휴대폰은 하루 안에 따라와요" : "강제로 켰어요");
      if (!d) return;
      dialog.close();
      if (S.route.name === "flags") S.data.flag = d;
      render({ keepScroll: true });
      load({ quiet: true });
    },
    "flag-cond": async (form, submitter) => {
      const reset = submitter?.name === "reset";
      const why = String(new FormData(form).get("why") ?? "").trim() || (reset ? "앱에 든 조건으로 되돌림" : "");
      let conditions = null;
      if (!reset) {
        const got = L.parseConditions(form.elements.conditions.value);
        if (got.error) return toast(got.error);
        conditions = got.list;
      }
      const d = await act(() => api(`/flags/${encodeURIComponent(form.dataset.key)}/conditions`, "POST", { conditions, why }), reset ? "앱 조건으로 되돌렸어요" : "조건을 바꿨어요");
      if (!d) return;
      dialog.close();
      S.data.flag = d;
      render({ keepScroll: true });
    },
    "flag-range": async (form, submitter) => {
      const reset = submitter?.name === "reset";
      const data = new FormData(form);
      const num = (k) => (reset || !String(data.get(k) ?? "").trim() ? null : Number(data.get(k)));
      const why = String(data.get("why") ?? "").trim() || (reset ? "앱 판 범위를 없앰" : "");
      const d = await act(() => api(`/flags/${encodeURIComponent(form.dataset.key)}/range`, "POST", { minApp: num("minApp"), maxApp: num("maxApp"), why }),
        reset ? "앱 판 범위를 없앴어요" : "앱 판 범위를 바꿨어요. 휴대폰은 하루 안에 따라와요");
      if (!d) return;
      dialog.close();
      S.data.flag = d;
      render({ keepScroll: true });
    },
    "flag-check": async (form) => {
      const r = await act(() => api(`/checks/${encodeURIComponent(form.dataset.name)}`, "POST", { done: true, note: String(new FormData(form).get("note") ?? "").trim() }), "확인했어요");
      if (!r) return;
      dialog.close();
      load();
    },
    "flag-thanks": async (form) => {
      const d = await act(() => api("/flags/billing/thanks", "POST", { thanksMonths: Number(new FormData(form).get("thanks")) }), "저장했어요");
      if (d) S.data.flag = d;
      render({ keepScroll: true });
    },
    "flag-policy": async (form) => {
      const data = new FormData(form);
      const body = { version: data.get("version"), posted: data.get("posted"), effective: data.get("effective"), heldUntil: data.get("heldUntil") || null,
        unfavourable: data.get("unfavourable") === "on", why: String(data.get("why") ?? "").trim() };
      const d = await act(() => api("/policy", "POST", body), "처리방침 일정을 고쳤어요");
      if (!d) return;
      dialog.close();
      S.data.flags = d;
      render({ keepScroll: true });
    },
    "type-hide": async (form) => {
      const d = await act(() => api("/types", "POST", { type: form.dataset.type, show: false, why: String(new FormData(form).get("why") ?? "").trim() }), "숨겼어요. 휴대폰은 하루 안에 따라와요");
      if (!d) return;
      dialog.close();
      S.data.types = d;
      render({ keepScroll: true });
    },
  };

  const FLAG_CHANGES = {
    "flag-sim": () => {
      const form = document.getElementById("flag-sim");
      const out = document.getElementById("flag-sim-out");
      if (!form || !out || !S.data.flag) return;
      const data = new FormData(form);
      S.flagSim = Object.fromEntries([...data.entries()].map(([k, v]) => [k, String(v)]));
      S.flagSim.preview = data.get("preview") === "on";
      out.innerHTML = flagSimText(S.data.flag);
    },
  };

  /** Typing in the condition editor: what it will say, or the first mistake, as the admin types. */
  function flagInput(t) {
    if (t.id !== "flag-cond") return;
    const out = document.getElementById("flag-cond-out");
    if (out) out.innerHTML = flagCondPreview(t.value);
  }
  // ---- AM 1.28 기능 관리 끝 ----

  const ACTIONS = {
    // 1.35 [INBOX-01]~[INBOX-07] 문의 정리함.
    "sort-do": ({ key, id }) => {
      const x = S.data.sort?.piles[key]?.find((i) => i.id === id);
      if (x) sortApply([sortAction(key, x)], "처리했어요");
    },
    "sort-reply-only": ({ id }) => {
      const x = S.data.sort?.piles.new.find((i) => i.id === id);
      const text = document.getElementById(`sort-reply-${id}`)?.value?.trim();
      if (!x || !text) return toast("보낼 답을 적어 주세요");
      sortApply([{ do: "reply", id, text }], "답했어요");
    },
    // The hash is the app's route, so a pile is reached by scrolling to it, not by its address.
    // 1.35 [ADMHE-01] A phone's list toolbar: search and 큐 모드 in one line, the rest behind 정렬·더.
    "tools-toggle": () => { S.toolsOpen = !S.toolsOpen; render({ keepScroll: true }); },
    "sort-jump": ({ key }) => document.getElementById(key === "tools" ? "sort-tools" : `pile-${key}`)?.scrollIntoView({ behavior: "smooth", block: "start" }),
    "sort-skip": ({ id }) => sortApply([{ do: "skip", id }], "정리함에서 뺐어요"),
    "sort-pile": ({ key }) => {
      const items = S.data.sort?.piles[key] ?? [];
      if (!items.length) return;
      // Read the cards now: what the admin changed in them is what goes, not what the server first wrote.
      const actions = items.map((x) => sortAction(key, x));
      confirmBox(`${items.length}건 모두 ${PILE_DO[key]}`, "카드마다 적힌 답이 그대로 가요. 보낸 답은 되돌릴 수 없어요.", PILE_DO[key],
        () => sortApply(actions, "처리했어요"), "primary");
    },
    "sort-auto": async ({ key }, el) => {
      // The click is default-prevented, so the box springs back: the state is drawn again from the answer.
      const on = el.checked;
      const r = await act(() => api("/inquiries/sort/auto", "POST", { ...(S.data.sort?.auto ?? {}), [key]: on }), on ? "자동 대응을 켰어요" : "자동 대응을 껐어요");
      if (r && S.data.sort) S.data.sort.auto = r;
      render({ keepScroll: true });
    },
    "sort-handoff": async (_, __, all = false) => {
      const h = all ? await act(() => api("/inquiries/handoff?all=1")) : S.data.handoff;
      if (!h?.items?.length) return toast("넘길 개발사항이 없어요");
      if (!(await toClipboard(h.text))) return toast("복사하지 못했어요. 다시 해 주세요");
      await act(() => api("/inquiries/handoff/mark", "POST", { ids: h.items.map((i) => i.id) }), `개발사항 ${h.items.length}개를 복사했어요. Claude에게 붙여 넣어 주세요`);
      S.data.handoff = null; load();
    },
    "sort-handoff-all": (a, el) => ACTIONS["sort-handoff"](a, el, true),
    "sort-result-check": async () => {
      const text = document.getElementById("sort-result")?.value ?? "";
      if (!text.trim()) return toast("결과 줄을 붙여 넣어 주세요");
      const r = await act(() => api("/inquiries/handoff/result", "POST", { text, dry: true }));
      if (r) { S.sortResult = { ...r, text }; render({ keepScroll: true }); }
    },
    "sort-result-clear": () => { S.sortResult = null; render({ keepScroll: true }); },
    "sort-result-go": async () => {
      const res = S.sortResult;
      if (!res) return;
      const notify = document.getElementById("sort-notify")?.checked === true;
      const r = await act(() => api("/inquiries/handoff/result", "POST", { text: res.text, notify }), (r) => `개발사항 ${r.changed}개 반영${r.answered ? ` · 문의 ${r.answered}건에 답` : ""}`);
      if (r) { S.sortResult = null; S.data.sort = null; S.data.handoff = null; load(); }
    },
    ...FLAG_ACTIONS,
    refresh: () => load(),
    close: () => dialog.close(),
    shortcuts: () => openShortcuts(),
    reload: () => location.reload(),
    palette: () => openPalette(),
    "palette-go": ({ i }) => paletteGo(Number(i)),
    "theme-next": () => {
      S.prefs.theme = { system: "light", light: "dark", dark: "system" }[S.prefs.theme] ?? "system";
      savePrefs();
      toast({ system: "기기 설정 색을 따라요", light: "밝게 보여요", dark: "어둡게 보여요" }[S.prefs.theme]);
    },
    "view-save": () => {
      const name = `${tabName(S.route.name)}${S.route.name === "inquiries" && S.inquiryQuery ? ` "${S.inquiryQuery}"` : ""} ${new Date().toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" })}`;
      S.prefs.views = [...S.prefs.views.filter((v) => v.hash !== href(S.route.name)), { name, hash: href(S.route.name) }].slice(-12);
      savePrefs();
      toast("이 보기를 저장했어요. 오늘과 찾기·이동(Ctrl+K)에서 바로 열어요");
    },
    "view-remove": ({ i }) => {
      S.prefs.views = S.prefs.views.filter((_, k) => k !== Number(i));
      savePrefs();
      render({ keepScroll: true });
    },
    "pw-show": ({ for: id }, el) => {
      const box = document.getElementById(id);
      if (!box) return;
      const show = box.type === "password";
      box.type = show ? "text" : "password";
      el.setAttribute("aria-pressed", String(show));
      el.textContent = show ? "숨기기" : "보기";
      el.setAttribute("aria-label", show ? "비밀번호 숨기기" : "비밀번호 보기");
    },
    "relogin-out": () => {
      dialog.close();
      signedOut();
    },
    // [ADMUX-04] 큐 모드
    "queue-start": async () => {
      const r = await act(() => api("/inquiries?status=open&sort=sla&limit=100"));
      const ids = (r?.inquiries ?? []).filter((q) => q.waiting && !(q.assignee && q.assignee.id !== S.me.id)).map((q) => q.id);
      if (!r) return;
      if (!ids.length) return toast("지금 답할 문의가 없어요. 다른 관리자가 맡은 것은 빼요");
      S.queue = { ids, at: 0, skipped: 0 };
      S.data.inquiries = { ...r, inquiries: r.inquiries.filter((q) => ids.includes(q.id)) };
      go("inquiries", ids[0]);
    },
    "queue-skip": () => queueNext(true),
    "queue-stop": () => {
      S.queue = null;
      toast("큐 모드를 끝냈어요");
      render({ keepScroll: true });
    },
    "queue-assign": async ({ id }) => {
      await act(() => api(`/inquiries/${id}/assign`, "POST", { to: "me" }), "내가 맡았어요", () => api(`/inquiries/${id}/assign`, "POST", { to: null }).then(() => load()));
      load();
    },
    // [ADMIN-16] 문의 여러 개
    "inquiry-pick": () => {
      S.inquiryPick = S.inquiryPick ? null : new Set();
      render({ keepScroll: true });
    },
    // AT 1.29 시작 [ADMCP-01]~[ADMCP-06]
    "inquiry-copy": ({ id }) => openCopy([id || S.route.id].filter(Boolean)),
    "inquiry-copy-picked": () => openCopy(pickedIds()),
    "bulk-more": () => {
      S.bulkMore = !S.bulkMore;
      render({ keepScroll: true });
    },
    "inquiry-pick-found": () => act(pickFound),
    "copy-style": ({ key }) => {
      if (!S.copy) return;
      S.copy.style = key === "short" ? "short" : "full";
      paintCopy();
    },
    "copy-do": async () => {
      const box = document.getElementById("copy-text");
      if (!box || !S.copy?.list) return;
      if (await toClipboard(box.value, box)) return copied();
      // [ADMCP-01] The browser keeps the clipboard shut (an old phone browser, no permission): the text to select by hand.
      S.copy.failed = true;
      paintCopy();
      const again = document.getElementById("copy-text");
      again?.focus();
      again?.select();
    },
    "copy-done": () => copied(),
    "inquiry-kind": ({ key }) => {
      S.inquiryKind = key;
      S.inquiryPick = null;
      refilter("inquiries");
    },
    "inquiry-copied": ({ key }) => {
      S.inquiryCopied = S.inquiryCopied === key ? "" : key;
      refilter("inquiries");
    },
    "inquiry-sender": ({ key }) => {
      S.inquirySender = key;
      if (key) {
        // Every question that phone sent, whatever its state.
        S.inquiryStatus = "all";
        S.inquiryWho = "";
        return go("inquiries");
      }
      refilter("inquiries");
    },
    "fresh-bugs": () => {
      Object.assign(S, { inquiryKind: "bug", inquiryCopied: "no", inquiryStatus: "all", inquiryWho: "", inquiryTopic: "", inquiryQuery: "", inquirySender: "" });
      refilter("inquiries");
    },
    "fresh-copy": async () => {
      const r = await act(() => api("/inquiries?status=all&kind=bug&copied=no&sort=newest&limit=100"));
      const ids = (r?.inquiries ?? []).filter((q) => q.status !== "closed").map((q) => q.id);
      if (ids.length) openCopy(ids);
    },
    "template-copy": ({ id }) => {
      const t = (S.templates ?? []).find((x) => String(x.id) === String(id));
      if (t) copy(t.text, `"${t.label}"을 복사했어요`);
    },
    // AT 1.29 끝
    "inquiry-pick-all": () => {
      const items = S.data.inquiries?.inquiries ?? [];
      S.inquiryPick = S.inquiryPick.size === items.length ? new Set() : new Set(items.map((x) => x.id));
      render({ keepScroll: true });
    },
    "inquiry-bulk": ({ key }) => {
      if (!S.inquiryPick?.size) return;
      if (key === "reply") return openBulkReply();
      if (key === "link") return openBulkLink();
      const n = S.inquiryPick.size;
      if (key === "close") return confirmBox(`${n}개 문의를 닫을까요?`, "보낸 분 앱에서 닫힌 문의로 보여요. 다시 열 수 있어요.", "닫기", () => inquiryBulk("close"));
      return inquiryBulk(key);
    },
    "bulk-template": ({ i }) => {
      const t = templateList()[Number(i)];
      const box = document.getElementById("bulk-text");
      if (!t || !box) return;
      box.value = L.fillTemplate(t.text, { today: new Date().toLocaleDateString("ko-KR") });
      if (t.id > 0) dialog.querySelector('input[name="template"]').value = String(t.id);
      box.focus();
    },
    "inquiries-more": () => act(() => loadInquiries(undefined, true)).then(() => render({ keepScroll: true })),
    "dev-more": () => act(() => loadDev(undefined, true)).then(() => render({ keepScroll: true })),
    "rewards-more": async () => {
      const r = S.data.rewards;
      const next = await act(() => api(`/rewards?status=${S.rewardTab}${S.rewardQuery ? `&q=${encodeURIComponent(S.rewardQuery)}` : ""}&after=${r.next}`));
      if (next) S.data.rewards = { ...next, rewards: [...r.rewards, ...next.rewards] };
      render({ keepScroll: true });
    },
    "posts-more": async () => {
      const d = S.data.posts;
      const q = S.postQuery ? `&q=${encodeURIComponent(S.postQuery)}` : "";
      const next = await act(() => api(`/posts?status=${S.postFilter}${q}&after=${d.next}`));
      if (next) S.data.posts = { ...next, posts: [...d.posts, ...next.posts] };
      render({ keepScroll: true });
    },
    "rates-more": async () => {
      const d = S.data.rates;
      const q = S.postQuery ? `&q=${encodeURIComponent(S.postQuery)}` : "";
      const next = await act(() => api(`/rates?status=${S.rateFilter}${q}&after=${d.next}`));
      if (next) S.data.rates = { ...next, rates: [...d.rates, ...next.rates] };
      render({ keepScroll: true });
    },
    // [ADMIN-17]
    "audit-who": ({ id }) => {
      S.audit = { ...S.audit, actor: id };
      refilter("audit");
    },
    "audit-clear": () => {
      S.audit = { actor: "", kind: "", from: "", to: "" };
      refilter("audit");
    },
    "audit-csv": () => downloadCsv("audit"),
    csv: ({ kind }) => downloadCsv(kind),
    // [ADMUX-06]
    "sla-step": ({ key, d }) => {
      const s = { ...(S.slaDraft ?? S.overview?.sla ?? {}) };
      const by = (s[key] ?? 24) >= 48 ? 12 : (s[key] ?? 24) >= 12 ? 2 : 1;
      s[key] = Math.min(168, Math.max(1, (s[key] ?? 24) + Number(d) * by));
      S.slaDraft = s;
      render({ keepScroll: true });
    },
    // [INVITE-08]
    "friend-step": ({ key, d }) => {
      const s = { ...(S.inviteDraft ?? S.data.inviteSettings ?? {}) };
      const lim = { friends: [1, 50], days: [1, 60], months: [1, 12], capMonths: [0, 120] }[key];
      if (!lim) return;
      s[key] = Math.min(lim[1], Math.max(lim[0], (s[key] ?? lim[0]) + Number(d)));
      S.inviteDraft = s;
      render({ keepScroll: true });
    },
    "friend-undo": () => {
      S.inviteDraft = null;
      render({ keepScroll: true });
    },
    "save-friend": async () => {
      const r = await act(() => api("/settings/invite", "POST", S.inviteDraft), "친구 초대 규칙을 저장했어요. 서버가 바로 따라요");
      if (r) {
        S.data.inviteSettings = r;
        S.inviteDraft = null;
      }
      render({ keepScroll: true });
    },
    "sla-undo": () => {
      S.slaDraft = null;
      render({ keepScroll: true });
    },
    "save-sla": async () => {
      const r = await act(() => api("/settings/sla", "POST", S.slaDraft), "답 목표를 저장했어요. 목록과 홈이 바로 따라요");
      if (r) {
        S.overview.sla = r.sla;
        S.slaDraft = null;
      }
      render({ keepScroll: true });
    },
    "settings-undo": () => {
      S.draft = null;
      render({ keepScroll: true });
    },
    // [ADMIN-19]
    "push-test": async () => {
      const r = await act(() => api("/me/push/test", "POST", {}));
      if (r) toast(r.sent ? `시험 알림을 ${r.sent}대로 보냈어요` : "보낼 기기가 없어요. 이 기기 알림을 다시 켜 주세요");
    },
    "push-off": async () => {
      const sub = await currentPush();
      const d = await act(() => api("/me/push/remove", "POST", sub ? { endpoint: sub.endpoint } : { id: S.pushId }), "이 기기 알림을 껐어요");
      try {
        await sub?.unsubscribe();
      } catch {
        // Already gone.
      }
      S.pushId = null;
      if (d) S.data.push = d;
      render({ keepScroll: true });
    },
    "template-edit": ({ id }) => openTemplates(id),
    "login-mode": ({ key }) => {
      // [ADMUX-03] The sign-in screen opens on the way chosen last time.
      S.prefs.loginMode = key;
      savePrefs();
      S.error = "";
      render();
    },
    "review-filter": ({ key }) => {
      S.reviewFilter = key;
      syncUrl();
      render({ keepScroll: true });
    },
    // [ADMUX-01] 채택 puts a rule in front of every user, so it is asked once.
    adopt: ({ company, setup }) => confirmBox("이 제보를 채택할까요?", "바로 회사 찾기에 나오고 같은 회사를 쓰는 분들 앱에 들어가요. 나중에 규칙을 내릴 수는 있어요.", "채택",
      () => act(() => api(`/groups/${company}/${setup}/adopt`, "POST"), "채택했어요. 이제 회사 찾기에 나와요").then(() => load()), "primary"),
    reject: ({ company, setup }) => openReject(company, setup),
    answer: ({ id }) => openAnswer(id),
    "new-company": () => openNewCompany(),
    "remove-alias": async ({ name }) => {
      const c = await act(() => api(`/companies/${S.route.id}/aliases/remove`, "POST", { name }));
      if (c) S.data.company = c;
      render({ keepScroll: true });
    },
    "withdraw-rule": ({ id }) => confirmBox("이 규칙을 내릴까요?", "회사 찾기에서 바로 빠져요. 나중에 다시 올릴 수 있어요.", "내리기", () => setRule(id, true)),
    "restore-rule": ({ id }) => setRule(id, false),
    "merge-into": ({ id, name }) => confirmBox(`${S.data.company.name}을 ${name}에 합칠까요?`, "제보와 규칙이 모두 옮겨지고 되돌릴 수 없어요.", "합치기", async () => {
      const c = await api(`/companies/${S.route.id}/merge`, "POST", { into: id });
      S.data.company = c;
      toast("합쳤어요");
      go("companies", c.id);
    }),
    "post-filter": ({ key }) => {
      S.postFilter = key;
      S.postPick = null;
      S.ratePick = null;
      S.data.rates = null;
      refilter("posts");
    },
    "rate-filter": ({ key }) => {
      S.rateFilter = key;
      refilter("rates");
    },
    "rate-set": async ({ id, key }) => {
      await act(() => api(`/rates/${id}`, "POST", { status: key }), key === "adopted" ? "채택했어요" : key === "hidden" ? "가렸어요" : "투표로 되돌렸어요");
      S.data.rates = null;
      load();
    },
    "post-show": ({ id }) => moderate(id, { action: "show" }, "다시 보이게 했어요. 신고로는 더 가려지지 않아요"),
    "post-hide": ({ id }) => openHidePost(id, false),
    "post-mute": ({ id }) => openHidePost(id, true),
    "post-delete": ({ id }) => confirmBox("이 글을 지울까요?", "지운 글은 되돌릴 수 없어요. 글쓴이 앱에서도 사라져요.", "지우기", () => moderate(id, { action: "delete" }, "지웠어요")),
    "reward-tab": ({ key }) => {
      S.rewardTab = key;
      refilter("rewards");
    },
    reward: ({ id, status, months }) => (status === "paid" ? openPaid(id, months) : setReward(id, status)),
    step: ({ key, d }) => {
      const s = { adoptAgree: 75, ...(S.draft ?? S.overview?.settings ?? { adoptPoints: 3, rewardMonths: 3 }) };
      const RANGES = { adoptPoints: [2, 20, 1], adoptAgree: [50, 100, 5], adoptPersons: [2, 10, 1], amendPersons: [2, 10, 1],
        rewardCapMonths: [1, 24, 1], rewardCapDays: [30, 365, 30] };
      const DEFAULT = { adoptPersons: 3, amendPersons: 3, autoReward: 1, rewardCapMonths: 3, rewardCapDays: 90 };
      const [lo, hi, by] = RANGES[key] ?? [0, 24, 1];
      s[key] = Math.min(hi, Math.max(lo, (s[key] ?? DEFAULT[key]) + Number(d) * by));
      S.draft = s;
      render({ keepScroll: true });
    },
    "save-settings": async () => {
      const s = await act(() => api("/settings", "POST", S.draft), "저장했어요");
      if (s) {
        S.overview.settings = s;
        S.draft = null;
      }
      render({ keepScroll: true });
    },
    "inquiry-status": ({ key }) => {
      S.inquiryStatus = key;
      if (S.inquiryWho === "late" && key !== "open") S.inquiryWho = "";
      S.inquiryPick = null;
      refilter("inquiries");
    },
    "inquiry-topic": ({ key }) => {
      S.inquiryTopic = key;
      refilter("inquiries");
    },
    template: ({ i }) => {
      // [ADMIN-16] A saved answer goes in filled for this question: {앱버전}, {주제}, {오늘}.
      const box = document.getElementById("reply");
      const t = templateList()[Number(i)];
      if (!box || !t) return;
      const q = S.data.inquiry;
      const text = L.fillTemplate(t.text, { app: q?.appVersion ?? "", topic: q?.topicName ?? "", today: new Date().toLocaleDateString("ko-KR") });
      box.value = (box.value ? `${box.value.trimEnd()}\n\n` : "") + text;
      if (t.id > 0) box.form.elements.template.value = String(t.id);
      box.dispatchEvent(new Event("input", { bubbles: true }));
      box.focus();
    },
    "inquiry-clear": () => {
      S.inquiryQuery = "";
      S.inquiryWho = "";
      S.inquiryTopic = "";
      // AT 1.29 [ADMCP-02]
      S.inquiryKind = "";
      S.inquiryCopied = "";
      S.inquirySender = "";
      refilter("inquiries");
    },
    "inquiry-who": ({ key }) => {
      S.inquiryWho = key;
      // [ADMUX-02] 놓친 것 is only ever a waiting question.
      if (key === "late") S.inquiryStatus = "open";
      refilter("inquiries");
    },
    "inquiry-assign": async ({ to }) => {
      const id = S.route.id;
      const q = await act(() => api(`/inquiries/${id}/assign`, "POST", { to: to || null }), to ? "내가 맡았어요" : "맡기를 취소했어요");
      if (q) S.data.inquiry = q;
      render({ keepScroll: true });
      load({ quiet: true });
    },
    templates: async () => {
      if (!S.templates) await loadTemplates();
      // AN 1.28 On 문의 › 자주 쓰는 답 the list is the screen: the box only adds one ("목록으로" closes it).
      if (S.route.name === "templates" && dialog.open && dialog.querySelector('form[data-form="template-add"][data-id]:not([data-id=""])')) return dialog.close();
      openTemplates();
    },
    "template-remove": ({ id }) => confirmBox("이 답을 지울까요?", "모든 관리자의 목록에서 빠져요.", "지우기", async () => {
      const r = await act(() => api(`/templates/${id}/remove`, "POST"), "지웠어요");
      if (r) {
        S.templates = r.templates;
        if (S.data.inquiry?.templates) S.data.inquiry.templates = S.data.inquiry.templates.filter((t) => t.id !== Number(id));
        if (S.route.name !== "templates") openTemplates();
        render({ keepScroll: true });
      }
    }),
    "dev-from-inquiry": () => openDevFromInquiry("new"),
    "dev-add-mode": ({ key }) => openDevFromInquiry(key),
    "dev-attach": async ({ id }) => {
      if (S.linkMode === "bulk") {
        dialog.close();
        await inquiryBulk("link", { item: id });
        return;
      }
      const r = await act(() => api(`/dev/${id}/links`, "POST", { inquiry: S.route.id }), (d) => `"${d.title}"에 붙였어요. 문의 ${d.links}건이에요`);
      if (r) {
        dialog.close();
        load();
      }
    },
    "dev-status": ({ key }) => {
      S.devStatus = key;
      refilter("devList");
    },
    "dev-who": ({ key }) => {
      S.devWho = key;
      refilter("devList");
    },
    "dev-new": () => openDevNew(),
    "dev-pick": () => {
      // [ADMIN-10] 여러 개 고르기 (일괄 복사·처리) is for the owner and admins given 처리.
      S.devPick = S.devPick || !can("devStatus", "edit") ? null : new Set();
      render({ keepScroll: true });
    },
    "dev-pick-all": () => {
      const items = S.data.devList?.items ?? [];
      S.devPick = S.devPick.size === items.length ? new Set() : new Set(items.map((x) => x.id));
      render({ keepScroll: true });
    },
    "dev-copy": () => {
      const items = (S.data.devList?.items ?? []).filter((x) => S.devPick.has(x.id));
      copy(L.devCopyText(items), `${items.length}개를 복사했어요. 메신저나 메모에 붙여 넣으세요`);
    },
    "dev-copy-one": () => copy(L.devCopyText([S.data.dev]), "복사했어요"),
    "dev-bulk": ({ status }) => {
      if (!S.devPick?.size) return;
      if (status === "rejected") openReason([...S.devPick]);
      else devBulk(status);
    },
    "dev-edit": () => openDevEdit(),
    "dev-assign": ({ to }) => devSave({ assignee: to || null }, to ? "내가 맡았어요" : "맡기를 취소했어요"),
    "dev-status-set": ({ status }) => {
      if (status === S.data.dev.status) return;
      if (status === "rejected") openReason(null);
      else devSetStatus(status);
    },
    "reason-pick": ({ i }) => {
      const box = document.getElementById("dev-reason");
      box.value = REJECT_REASONS[Number(i)];
      box.focus();
    },
    "dev-delete": () => confirmBox("이 개발사항을 지울까요?", "목록에서 빠지고 기록은 남아요. 최고 관리자가 되살릴 수 있어요.", "지우기", async () => {
      const d = await api(`/dev/${S.route.id}/delete`, "POST");
      S.data.dev = d;
      toast("지웠어요");
      go("dev");
    }),
    "dev-restore": async () => {
      const d = await act(() => api(`/dev/${S.route.id}/restore`, "POST"), "되살렸어요");
      if (d) S.data.dev = d;
      render({ keepScroll: true });
    },
    "dev-unlink": ({ id }) => confirmBox("이 문의를 떼어 낼까요?", "문의는 그대로 있고 이 개발사항과의 연결만 끊겨요. 기록에 남아요.", "떼기", async () => {
      S.data.dev = await api(`/dev/${S.route.id}/links/remove`, "POST", { inquiry: id });
      toast("떼어 냈어요");
      render({ keepScroll: true });
    }),
    "dev-reply": () => openDevReply(),
    "inquiry-hide": async ({ reason }) => {
      const q = await act(() => api(`/inquiries/${S.route.id}/visibility`, "POST", { action: "hide", reason }), "숨겼어요. 보낸 분 앱에 이유가 보여요");
      if (q) S.data.inquiry = q;
      load();
    },
    "inquiry-show": async () => {
      const q = await act(() => api(`/inquiries/${S.route.id}/visibility`, "POST", { action: "show" }), "다시 보이게 했어요. 신고로는 더 가려지지 않아요");
      if (q) S.data.inquiry = q;
      load();
    },
    "inquiry-state": async ({ status }) => {
      const id = S.route.id;
      const reopen = () => api(`/inquiries/${id}/status`, "POST", { status: "open" }).then(() => load());
      const q = await act(() => api(`/inquiries/${id}/status`, "POST", { status }), status === "closed" ? "문의를 닫았어요" : "문의를 다시 열었어요",
        status === "closed" ? reopen : undefined);
      if (!q) return;
      S.data.inquiry = q;
      // [ADMUX-04] Closed in 큐 모드, or with 닫으면 다음 문의 on: the next one opens.
      if (status === "closed" && S.queue) return queueNext();
      const next = neighbours().next;
      if (status === "closed" && S.prefs.next && next) return go("inquiries", next);
      load();
    },
    invite: () => openInvite(),
    "invite-again": ({ email }) => {
      const i = S.data.admins.invites.find((x) => x.email === email);
      openInvite(i);
    },
    "invite-remove": ({ email }) => confirmBox(`${email} 초대를 취소할까요?`, "보낸 링크로는 더 이상 계정을 만들 수 없어요.", "초대 취소", async () => {
      await api("/invites/remove", "POST", { email });
      toast("초대를 취소했어요");
      load();
    }),
    "copy-invite": () => copy(S.inviteLink),
    "share-invite": async () => {
      try {
        await navigator.share({ title: "밤낮 관리자 초대", text: S.inviteText });
      } catch {
        // Closed without sharing.
      }
    },
    preset: ({ key }) => {
      const p = S.data.admins.presets[key];
      for (const [area, level] of Object.entries(p.perms)) setLevel(area, level);
    },
    level: ({ area, level }) => setLevel(area, level),
    "admin-approve": ({ id }) => openAdminEdit(id, true),
    // [MON-09]
    phone: async ({ id, key }) => {
      const go = async () => {
        const d = await act(() => api(`/devices/${id}/${key}`, "POST", {}), key === "approve" ? "승인했어요. 그 휴대폰은 프로를 무료로 써요" : "끊었어요");
        if (d) S.data.phones = d;
        load();
      };
      if (key === "remove") confirmBox("이 휴대폰을 끊을까요?", "그 휴대폰은 다음 확인 때부터 무료 등급이 돼요.", "끊기", go);
      else go();
    },
    "login-restart": () => {
      S.needCode = null;
      S.error = "";
      render();
    },
    // R131 1.31 [ADMX-16]
    "second-start": async () => {
      const r = await act(() => api("/me/2fa", "POST", { do: "start" }));
      if (!r) return;
      S.data.second = { on: false, secret: r.secret, uri: r.uri };
      render({ keepScroll: true });
      document.getElementById("second-on-code")?.focus();
    },
    "phone-previews": ({ id }) => openPreviews(id),
    "link-code": async () => {
      const r = await act(() => api("/me/link-code", "POST", {}));
      if (!r) return;
      openDialog(`<div class="box"><h2>연결 코드</h2><p class="code-big" aria-label="연결 코드">${esc(r.code.replace(/(\d{3})(\d{3})/, "$1 $2"))}</p>
        <p class="hint">${esc(clock(r.expiresAt))}까지 한 번 쓸 수 있어요. 밤낮 앱 › 설정 › 프로 › '관리자 연결'에 넣어 주세요.</p>
        <div class="actions"><button class="btn" data-act="close">닫기</button></div></div>`);
    },
    "admin-edit": ({ id }) => openAdminEdit(id, false),
    "admin-status": ({ id, status, name }) => {
      const run = async () => {
        S.data.admins = await api(`/admins/${id}`, "POST", { status });
        toast(status === "active" ? "다시 쓸 수 있어요" : "멈췄어요. 그 기기들은 바로 로그아웃돼요");
        render({ keepScroll: true });
        load({ quiet: true });
      };
      if (status === "suspended") confirmBox(`${name}님을 멈출까요?`, "바로 로그아웃되고, 다시 쓰기를 누를 때까지 로그인할 수 없어요.", "멈추기", run);
      else act(run);
    },
    "admin-remove": ({ id, name }) => confirmBox(`${name}님을 삭제할까요?`, "계정이 지워지고 바로 로그아웃돼요. 다시 쓰려면 새로 초대해야 해요.", "삭제", async () => {
      S.data.admins = await api(`/admins/${id}/remove`, "POST");
      toast("삭제했어요");
      render({ keepScroll: true });
      load({ quiet: true });
    }),
    // [SRV-04][ADMIN-14]
    "ops-month": ({ d }) => {
      const [y, m] = (S.data.ops?.month ?? new Date().toISOString().slice(0, 7)).split("-").map(Number);
      const next = new Date(Date.UTC(y, m - 1 + Number(d), 1));
      S.opsMonth = next.toISOString().slice(0, 7);
      syncUrl();
      load();
    },
    "ops-tick": async ({ key, done }) => {
      const o = await act(() => api("/ops", "POST", { month: S.data.ops.month, key, done: Boolean(done) }), done ? "마쳤어요" : "되돌렸어요");
      if (o) S.data.ops = o;
      load();
    },
    "notice-new": () => openNotice(null),
    "notice-edit": ({ id }) => openNotice(S.data.notices?.notices.find((x) => x.id === id)),
    "notice-remove": ({ id }) => confirmBox("공지를 내릴까요?", "앱은 다음에 새로 받을 때 이 공지를 빼요.", "내리기", async () => {
      const n = await act(() => api(`/notices/${id}/remove`, "POST", {}), "공지를 내렸어요");
      if (n) S.data.notices = n;
      render({ keepScroll: true });
    }),
    // [ADMIN-13]
    "metric-days": ({ key }) => {
      S.metricDays = Number(key);
      S.metricFrom = "";
      S.metricTo = "";
      refilter("metrics");
    },
    // [ADMUX-13] Back to the 7·28·90 buttons.
    "metric-clear": () => {
      S.metricFrom = "";
      S.metricTo = "";
      refilter("metrics");
    },
    // [ADMUX-15] Read the remote files again now, not from the ten minutes the server keeps them.
    "remote-refresh": async () => {
      const d = await act(() => api("/remote?fresh=1"), "원격 자료를 다시 읽었어요");
      if (d) S.data.remote = d;
      render({ keepScroll: true });
    },
    // [ADMUX-17] 받은함
    "inbox-view": ({ key }) => {
      S.inboxView = key;
      syncUrl();
      render({ keepScroll: true });
    },
    "inbox-assign": async ({ id }) => {
      await act(() => api(`/inquiries/${id}/assign`, "POST", { to: "me" }), "내가 맡았어요", () => api(`/inquiries/${id}/assign`, "POST", { to: null }).then(() => load()));
      load();
    },
    "inbox-snooze": ({ key }) => {
      const all = snoozed();
      // Until 6 in the morning of the next Korean day.
      const kst = new Date(Date.now() + 9 * 3600e3);
      all[key] = Date.UTC(kst.getUTCFullYear(), kst.getUTCMonth(), kst.getUTCDate() + 1, 6) - 9 * 3600e3;
      try {
        localStorage.setItem(SNOOZE, JSON.stringify(all));
      } catch {
        // A private window keeps nothing; the item simply stays.
      }
      toast("내일 아침까지 이 기기에서 숨겼어요", () => {
        const back = snoozed();
        delete back[key];
        try {
          localStorage.setItem(SNOOZE, JSON.stringify(back));
        } catch {
          // As above.
        }
        render({ keepScroll: true });
      });
      render({ keepScroll: true });
    },
    "inbox-unsnooze": () => {
      try {
        localStorage.removeItem(SNOOZE);
      } catch {
        // Nothing kept.
      }
      render({ keepScroll: true });
    },
    // [ADMUX-18] 여러 판
    "versions-show": ({ key }) => {
      S.versionShow = key;
      refilter("versions");
    },
    "version-promote": ({ id, version }) => confirmBox(`v${version}을 다시 대표로 할까요?`,
      "지금 쓰는 판은 이전 판으로 남고, v" + version + "과 같은 설정이 새 판으로 올라가요. 지금 판을 쓰는 분들은 광장 소식으로 알게 돼요. 언제든 다시 바꿀 수 있어요.", "다시 대표로",
      async () => {
        await act(() => api(`/rules/${id}/promote`, "POST"), "이전 판을 다시 대표로 했어요");
        load();
      }, "primary"),
    // [ADMUX-19] 토론 글·임금표 여러 개
    "post-pick": () => {
      S.postPick = S.postPick ? null : new Set();
      render({ keepScroll: true });
    },
    "post-pick-all": () => {
      const items = S.data.posts?.posts ?? [];
      S.postPick = S.postPick.size === items.length ? new Set() : new Set(items.map((x) => x.id));
      render({ keepScroll: true });
    },
    "post-bulk": ({ key }) => {
      const ids = [...(S.postPick ?? [])];
      if (!ids.length) return;
      const go = async () => {
        const r = await act(() => api("/posts/bulk", "POST", { ids, action: key, ...(key === "hide" ? { reason: "abuse" } : {}) }),
          (d) => `${d.done.length}개 처리했어요${d.skipped.length ? `, ${d.skipped.length}개는 이미 없어서 건너뛰었어요` : ""}`);
        if (r) S.postPick = new Set();
        load();
      };
      if (key === "delete") confirmBox(`글 ${ids.length}개를 지울까요?`, "지운 글은 되돌릴 수 없어요. 글쓴이 앱에서도 사라져요.", "지우기", go);
      else if (key === "hide") confirmBox(`글 ${ids.length}개를 가릴까요?`, "가린 이유는 '욕설·비방'으로 글쓴이 앱에 보여요. 다른 이유는 글마다 가리기로 골라 주세요.", "가리기", go);
      else go();
    },
    "rate-pick": () => {
      S.ratePick = S.ratePick ? null : new Set();
      render({ keepScroll: true });
    },
    "rate-pick-all": () => {
      const items = S.data.rates?.rates ?? [];
      S.ratePick = S.ratePick.size === items.length ? new Set() : new Set(items.map((x) => x.id));
      render({ keepScroll: true });
    },
    "rate-bulk": async ({ key }) => {
      const ids = [...(S.ratePick ?? [])];
      if (!ids.length) return;
      const r = await act(() => api("/rates/bulk", "POST", { ids, action: key }),
        (d) => `${d.done.length}개 처리했어요${d.skipped.length ? `, ${d.skipped.length}개는 권한이 없거나 없어서 건너뛰었어요` : ""}`);
      if (r) S.ratePick = new Set();
      S.data.rates = null;
      load();
    },
    // [RCP-39]
    "recipe-tab": ({ key }) => { S.recipeTab = key; S.data.recipes = null; load(); },
    "recipe-log": () => { S.recipeLog = true; load(); },
    // [RCX-15][RCX-04][RCX-07] 1.35
    "recipe-image": async ({ id, key }) => { await act(() => api(`/recipes/images/${id}/${key}`, "POST", {}), key === "ok" ? "보이게 했어요" : "가렸어요"); load(); },
    "recipe-maker": async ({ id, key }) => {
      const said = { "bio-hide": "소개를 가렸어요", "bio-show": "소개가 다시 보여요", close: "프로필을 닫았어요", open: "프로필을 다시 열었어요", clear: "신고를 비웠어요" }[key];
      await act(() => api(`/recipes/makers/${id}/${key}`, "POST", {}), said); load();
    },
    "recipe-request": async ({ id, key }) => { await act(() => api(`/recipes/requests/${id}/${key}`, "POST", {}), key === "hide" ? "가렸어요" : "다시 보이게 했어요"); load(); },
    "recipe-helper": ({ id }) => confirmBox("이 만든 분께 도우미 배지를 줄까요?", "다른 분의 질문에 성실히 답해 온 분께 드려요. 만든 분 페이지에 배지가 보여요.", "배지 주기", async () => {
      await act(() => api(`/recipes/makers/${id}/helper`, "POST", { on: true }), "도우미 배지를 드렸어요");
      load();
    }),
    "recipe-do": async ({ id, key }) => {
      await act(() => api(`/recipes/${id}/${key}`, "POST", {}), { approve: "광장에 올렸어요", hide: "가렸어요", show: "다시 보이게 했어요", official: "공식으로 표시했어요" }[key] ?? "했어요");
      load();
    },
    // [RCX-32] 보여주기에 넣기·빼기.
    "recipe-showcase": async ({ id, key }) => {
      await act(() => api(`/recipes/${id}/showcase`, "POST", { on: key === "on" }), key === "on" ? "보여주기에 넣었어요" : "보여주기에서 뺐어요");
      load();
    },
    "recipe-delete": ({ id }) => confirmBox("이 레시피를 지울까요?", "모든 판과 이야기가 지워지고 되돌릴 수 없어요. 이미 넣은 분들의 달력은 그대로예요.", "지우기", async () => {
      await act(() => api(`/recipes/${id}/delete`, "POST", {}), "지웠어요");
      load();
    }),
    "recipe-return": ({ id }) => openDialog(`<form data-form="recipe-return" data-id="${id}"><h2>무엇을 고치면 될까요?</h2>
      <p>만든 분 앱에 이 메모가 보여요. 고쳐서 다시 올리면 검토로 돌아와요.</p><textarea name="note" rows="3" maxlength="200" required></textarea>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">돌려주기</button></div></form>`),
    // [SRV-05]
    "share-remove": ({ id }) => confirmBox("이 링크를 내릴까요?", "근무표가 바로 지워지고 되돌릴 수 없어요. 공유한 분은 새 링크를 만들 수 있어요.", "내리기", async () => {
      const d = await act(() => api(`/shares/${id}/remove`, "POST", { reason: "운영 규칙 위반" }), "링크를 내렸어요");
      if (d) S.data.shares = d;
      render({ keepScroll: true });
    }),
    "audit-more": async () => {
      const d = S.data.audit;
      const next = await act(() => api(`/audit${auditQuery(d.entries[d.entries.length - 1].id)}`));
      if (next) S.data.audit = { ...next, entries: [...d.entries, ...next.entries] };
      render({ keepScroll: true });
    },
    install: async () => {
      const p = S.installPrompt;
      if (!p) return;
      p.prompt();
      const choice = await p.userChoice.catch(() => null);
      S.installPrompt = null;
      if (choice?.outcome === "accepted") toast("설치했어요. 홈 화면에서 열 수 있어요");
      render({ keepScroll: true });
    },
    "join-code": async () => {
      S.error = "";
      const r = await act(() => api("/join/code", "POST", { token: S.join.token }));
      if (r) {
        S.join.codeSent = true;
        const keep = readJoinForm();
        render();
        fillJoinForm(keep);
        document.getElementById("j-code")?.focus();
      }
    },
    logout: async () => {
      try {
        await api("/logout", "POST");
      } catch {
        // Signed out either way.
      }
      signedOut(true);
    },
    "logout-all": () => confirmBox("모든 기기에서 로그아웃할까요?", "이 기기를 포함해 이 계정으로 로그인한 모든 기기가 다시 로그인해야 해요.", "로그아웃", async () => {
      await api("/me/logout-all", "POST");
      signedOut(true);
    }),
  };

  function setLevel(area, level) {
    const form = dialog.querySelector("form");
    const input = form?.elements[`perm-${area}`];
    if (!input) return;
    const btn = form.querySelector(`[data-area="${area}"][data-level="${level}"]`);
    const value = btn?.disabled ? "view" : level;
    input.value = value;
    form.querySelectorAll(`[data-area="${area}"]`).forEach((b) => {
      const on = b.dataset.level === value;
      b.setAttribute("aria-pressed", String(on));
      b.setAttribute("aria-checked", String(on));
      b.tabIndex = on ? 0 : -1;
    });
  }

  /**
   * Back to the sign-in screen. [forget]: signed out on purpose, so what this device kept of the
   * admin's work (the service worker's copies of answers, unsent drafts) goes too.
   */
  function signedOut(forget = false) {
    S.phase = "login";
    S.me = null;
    S.overview = null;
    S.basics = false;
    badgesAt = 0;
    S.data = {};
    S.error = "";
    S.queue = null;
    S.relogin = false;
    etags.clear();
    clearInterval(pollTimer);
    if (forget) {
      clearDrafts();
      navigator.serviceWorker?.controller?.postMessage({ type: "forget" });
    }
    history.replaceState(null, "", "/admin/");
    render();
  }

  const readJoinForm = () => Object.fromEntries(["j-name", "j-pw", "j-pw2"].map((id) => [id, document.getElementById(id)?.value ?? ""]));
  const fillJoinForm = (v) => Object.entries(v).forEach(([id, value]) => { const el = document.getElementById(id); if (el) el.value = value; });

  const FORMS = {
    ...FLAG_FORMS,
    login: async (form) => {
      S.error = "";
      const data = new FormData(form);
      const body = S.prefs.loginMode === "owner" ? { password: data.get("password") } : { email: data.get("email"), password: data.get("password") };
      // R131 1.31 [ADMX-16] The code the second time round, with the password typed the first time.
      if (S.needCode) Object.assign(body, S.needCode, { code: data.get("code") });
      try {
        await api("/login", "POST", body);
        S.needCode = null;
        await enter();
      } catch (e) {
        if (e.message === "code_needed" || (e.message === "code_wrong" && S.needCode)) {
          S.needCode = { password: body.password, ...(body.email ? { email: body.email } : {}) };
          S.error = e.message === "code_wrong" ? ERRORS.code_wrong : "";
          render();
          document.getElementById("login-code")?.focus();
          return;
        }
        const left = e.data?.left;
        S.error = e.message === "wrong" ? `${S.prefs.loginMode === "owner" ? "비밀번호가" : "메일이나 비밀번호가"} 달라요. ${left}번 더 틀리면 1시간 동안 잠겨요.`
          : e.message === "locked" ? "여러 번 틀려서 1시간 동안 잠겼어요. 나중에 다시 해 주세요."
          : e.message === "pending" ? "최고 관리자의 승인을 기다리고 있어요. 승인되면 로그인할 수 있어요."
          : e.message === "suspended" ? "사용이 멈춘 계정이에요. 최고 관리자에게 물어봐 주세요."
          : e.message === "not_set" ? "최고 관리자 비밀번호가 아직 없어요. 아래 서버 준비를 봐 주세요."
          : ERRORS[e.message] || "로그인하지 못했어요. 잠시 뒤 다시 해 주세요.";
        const email = form.elements.email?.value;
        render();
        if (email && document.getElementById("email")) document.getElementById("email").value = email;
      }
    },
    // [ADMUX-01] Signed in again over the screen: the box closes and the screen carries on.
    // R131 1.31 [ADMX-16]
    "second-on": async (form) => {
      const r = await act(() => api("/me/2fa", "POST", { do: "confirm", code: new FormData(form).get("code") }));
      if (!r) return;
      S.data.second = r;
      toast("두 번째 확인을 켰어요. 다음 로그인부터 코드를 물어요");
      render({ keepScroll: true });
    },
    "second-off": async (form) => {
      const r = await act(() => api("/me/2fa", "POST", { do: "off", code: new FormData(form).get("code") }));
      if (!r) return;
      S.data.second = r;
      toast("두 번째 확인을 껐어요");
      render({ keepScroll: true });
    },
    relogin: async (form) => {
      const data = new FormData(form);
      const body = S.me?.owner ? { password: data.get("password") } : { email: data.get("email"), password: data.get("password") };
      if (data.get("code")) body.code = data.get("code");
      try {
        await api("/login", "POST", body);
        S.relogin = false;
        dialog.close();
        toast("다시 로그인했어요");
        load();
      } catch (e) {
        const box = document.getElementById("re-error");
        if (box) box.textContent = e.message === "wrong" ? "메일이나 비밀번호가 달라요." : ERRORS[e.message] ?? "로그인하지 못했어요.";
        if (e.message === "code_needed") document.getElementById("re-code")?.focus();
      }
    },
    confirm: async () => {
      const then = pendingConfirm;
      pendingConfirm = null;
      dialog.close();
      try {
        await then?.();
      } catch (e) {
        fail(e);
      }
    },
    // S 1.32 [SECX-01]
    "phone-previews": async (form) => {
      const keys = new FormData(form).getAll("keys");
      dialog.close();
      const d = await act(() => api(`/devices/${form.dataset.id}/previews`, "POST", { keys }), keys.length ? "저장했어요. 그 휴대폰이 앱을 열면 반영돼요" : "미리 보기를 모두 껐어요");
      if (d) S.data.myPhones = d;
      render({ keepScroll: true });
    },
    reject: async (form) => {
      const reason = new FormData(form).get("reason");
      const { company, setup } = form.dataset;
      dialog.close();
      // [ADMIN-15] A 거절 can be taken back for 10 minutes (the server reopens the reports).
      await act(() => api(`/groups/${company}/${setup}/reject`, "POST", { reason }), "거절했어요. 보낸 분 앱에 이유가 보여요",
        () => act(() => api(`/groups/${company}/${setup}/reopen`, "POST"), "거절을 되돌렸어요. 다시 검토를 기다려요").then(() => load()));
      load();
    },
    answer: async (form) => {
      const data = new FormData(form);
      dialog.close();
      await act(() => api(`/asks/${form.dataset.id}`, "POST", { answer: data.get("answer"), reward: data.get("reward") === "on" }),
        (r) => (r.reward ? "답했어요. 보상 탭에 지급 대기로 올렸어요" : "답했어요. 보낸 분 앱에 보여요"));
      load();
    },
    "new-company": async (form) => {
      const c = await act(() => api("/companies", "POST", { name: new FormData(form).get("name") }), "만들었어요");
      if (c) {
        dialog.close();
        go("companies", c.id);
      }
    },
    // [RCP-39]
    "recipe-return": async (form) => {
      const id = form.dataset.id;
      const note = String(new FormData(form).get("note") ?? "").trim();
      if (!note) return;
      dialog.close();
      await act(() => api(`/recipes/${id}/return`, "POST", { note }), "돌려줬어요. 만든 분 앱에 메모가 보여요");
      load();
    },
    // [RCX-08]
    "recipe-picks": async (form) => {
      const codes = String(new FormData(form).get("codes") ?? "").toUpperCase().split(/[\s,]+/).filter(Boolean);
      await act(() => api("/recipes/picks", "POST", { style: form.dataset.key, codes }), codes.length ? `${codes.length}개 골랐어요` : "이번 주 고른 것을 비웠어요");
      load();
    },
    // [PRF-10]
    "recipe-month": async (form) => {
      const makers = String(new FormData(form).get("makers") ?? "").toLowerCase().split(/[\s,]+/).filter(Boolean);
      await act(() => api("/recipes/makers/month", "POST", { style: form.dataset.key, makers }), makers.length ? `${makers.length}명 골랐어요` : "비웠어요. 저절로 보여요");
      load();
    },
    "recipe-upload": async (form) => {
      const f = new FormData(form);
      let parsed;
      try { parsed = JSON.parse(String(f.get("recipe") ?? "")); } catch { toast("레시피 글(JSON)을 읽지 못했어요"); return; }
      // One recipe, or docs/recipes/starter-recipes.json whole ([{publicData, recipe}, …]): each goes up on its own.
      const list = Array.isArray(parsed) ? parsed : [{ recipe: parsed, publicData: f.get("publicData") === "on", showcase: f.get("showcase") === "on" }];
      let done = 0;
      const failed = [];
      for (const x of list) {
        // [RCX-32] showcase-recipes.json's own {showcase: true}, or the 보여주기 tab's form.
        try { await api("/recipes", "POST", { recipe: x.recipe, publicData: x.publicData === true, showcase: x.showcase === true || f.get("showcase") === "on" }); done++; }
        catch (e) { failed.push(x.recipe?.title ?? "?"); }
      }
      toast(`${done}개 올렸어요${failed.length ? ` · 못 올린 것 ${failed.length}개: ${failed.slice(0, 3).join(", ")}` : ""}`);
      if (done) { form.reset(); S.data.recipes = null; load(); }
    },
    "hide-post": (form) => {
      const mute = form.dataset.mute === "1";
      const id = form.dataset.id;
      dialog.close();
      moderate(id, { action: mute ? "mute" : "hide", reason: new FormData(form).get("reason") },
        mute ? "가리고 30일 동안 쓰기를 막았어요" : "가렸어요. 글쓴이 앱에 이유가 보여요",
        mute ? undefined : () => moderate(id, { action: "show" }, "다시 보이게 했어요"));
    },
    paid: (form) => {
      dialog.close();
      const data = new FormData(form);
      setReward(form.dataset.id, "paid", data.get("memo"), Number(data.get("months")) || undefined);
    },
    // [MON-10] 감사 보상 개월 수와 서버가 무료로 연 기능.
    // [SRV-04] Dates are Korean days: from 0시 of the first to the end of the last.
    notice: async (form) => {
      const data = new FormData(form);
      const at = (d, plus = 0) => new Date(Date.parse(`${d}T00:00:00+09:00`) + plus).toISOString();
      const num = (v) => (String(v ?? "").trim() ? Number(v) : null);
      const body = {
        ...(form.dataset.id ? { id: form.dataset.id } : {}),
        title: data.get("title"), body: data.get("body"), level: data.get("level"),
        from: at(data.get("from")), until: at(data.get("until"), 864e5), minApp: num(data.get("minApp")), maxApp: num(data.get("maxApp")),
      };
      const n = await act(() => api("/notices", "POST", body), form.dataset.id ? "공지를 고쳤어요" : "공지를 올렸어요. 앱은 하루 안에 받아요");
      if (!n) return;
      S.data.notices = n;
      dialog.close();
      render({ keepScroll: true });
    },
    rename: async (form) => {
      const c = await act(() => api(`/companies/${S.route.id}`, "POST", { name: new FormData(form).get("name") }), "이름을 바꿨어요. 예전 이름으로도 찾아져요");
      if (c) S.data.company = c;
      render({ keepScroll: true });
    },
    alias: async (form) => {
      const c = await act(() => api(`/companies/${S.route.id}/aliases`, "POST", { name: new FormData(form).get("name") }));
      if (c) S.data.company = c;
      render({ keepScroll: true });
    },
    "merge-search": async (form) => {
      const box = document.getElementById("merge-results");
      const r = await act(() => api(`/companies?q=${encodeURIComponent(new FormData(form).get("q"))}`));
      if (!r || !box) return;
      const found = r.companies.filter((c) => c.id !== S.route.id && !c.mergedInto);
      box.innerHTML = found.length ? `<ul class="rows">${found.map((c) => `<li><button class="row" data-act="merge-into" data-id="${c.id}" data-name="${esc(c.name)}">
        <span class="row-main"><b>${esc(c.name)}</b><small>규칙 ${c.rules} · 제보 ${c.reports}</small></span><span class="pill">여기로 합치기</span></button></li>`).join("")}</ul>`
        : '<p class="meta">찾는 회사가 없어요.</p>';
    },
    reply: async (form, submitter) => {
      const text = form.elements.text.value.trim();
      if (!text) return toast(ERRORS.text);
      const close = submitter?.dataset.close === "1";
      const template = Number(form.elements.template?.value) || undefined;
      const id = S.route.id;
      const q = await act(() => api(`/inquiries/${id}/reply`, "POST", { text, ...(close ? { close: true } : {}), ...(template ? { template } : {}) }),
        close ? "답하고 닫았어요. 보낸 분 앱에 보여요" : "답했어요. 보낸 분 앱에 보여요");
      if (!q) return;
      typingSent = 0;
      S.data.inquiry = q;
      // [ADMUX-04] 큐 모드 goes on to the next question; 닫으면 다음 문의 does the same after 답하고 닫기.
      if (S.queue) return queueNext();
      const next = neighbours().next;
      if (close && S.prefs.next && next) return go("inquiries", next);
      render();
      window.scrollTo(0, document.body.scrollHeight);
      load({ quiet: true });
    },
    // AN 1.28 [ADMUX-33] 바로 답하기 on 오늘's 할 일: the same answer as on the question, without opening it.
    "quick-reply": async (form, submitter) => {
      const text = form.elements.text.value.trim();
      if (!text) return toast(ERRORS.text);
      const close = submitter?.dataset.close === "1";
      const q = await act(() => api(`/inquiries/${form.dataset.id}/reply`, "POST", { text, ...(close ? { close: true } : {}) }),
        close ? "답하고 닫았어요. 보낸 분 앱에 보여요" : "답했어요. 보낸 분 앱에 보여요");
      if (q) load({ quiet: true });
    },
    "inquiry-note": async (form) => {
      const q = await act(() => api(`/inquiries/${S.route.id}/notes`, "POST", { text: form.elements.text.value }), "메모를 남겼어요");
      if (q) {
        S.data.inquiry = q;
        render({ keepScroll: true });
      }
    },
    "template-add": async (form) => {
      const id = form.dataset.id;
      const body = { label: form.elements.label.value, text: form.elements.text.value, topic: form.elements.topic.value };
      const r = await act(() => api(id ? `/templates/${id}` : "/templates", "POST", body), id ? "고쳤어요" : "넣었어요. 답 칸 아래에서 바로 써요");
      if (!r) return;
      S.templates = r.templates;
      // The open question's list (its topic first) comes again with the change.
      if (S.route.name === "inquiries" && S.route.id) {
        S.data.inquiry = await api(`/inquiries/${S.route.id}`).catch(() => S.data.inquiry);
      }
      if (id && S.route.name !== "templates") openTemplates();
      else dialog.close();
      render({ keepScroll: true });
    },
    // [ADMIN-16]
    "bulk-reply": async (form) => {
      const text = form.elements.text.value.trim();
      if (!text) return toast(ERRORS.text);
      const template = Number(form.elements.template.value) || undefined;
      dialog.close();
      await inquiryBulk("reply", { text, close: form.elements.close.checked, ...(template ? { template } : {}) });
    },
    // [ADMIN-19] Turned on here: the browser asks, makes a subscription for the server's key, and the server keeps it.
    push: async (form) => {
      const d = S.data.push;
      const kinds = [...form.querySelectorAll('input[name="kind"]:checked')].map((x) => x.value);
      const quiet = form.elements.quiet.checked ? { from: Number(form.elements.from.value), to: Number(form.elements.to.value) } : null;
      let sub = await currentPush();
      if (!sub) {
        const allowed = await Notification.requestPermission().catch(() => "denied");
        if (allowed !== "granted") {
          toast("알림을 허용해야 받을 수 있어요");
          return render({ keepScroll: true });
        }
        try {
          const key = Uint8Array.from(atob(d.publicKey.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
          const reg = await navigator.serviceWorker.ready;
          sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
        } catch {
          return toast("이 브라우저가 알림 등록을 받지 않았어요. 앱으로 설치한 뒤 다시 해 주세요");
        }
      }
      const r = await act(() => api("/me/push", "POST", { subscription: sub.toJSON(), kinds, quiet }), "이 기기 알림을 저장했어요");
      if (r) {
        S.data.push = r;
        S.pushId = await pushIdOf(sub.endpoint);
      }
      render({ keepScroll: true });
    },
    "dev-new": async (form) => {
      const body = { title: form.elements.title.value, body: form.elements.body.value };
      if (form.dataset.inquiry) {
        body.inquiry = form.dataset.inquiry;
        body.body = body.body.replace(SCRUB, "").replace(/^\s+/, "");
      }
      const d = await act(() => api("/dev", "POST", body), form.dataset.inquiry ? "개발사항에 추가했어요. 추가한 사람으로 내 이름이 남아요" : "추가했어요");
      if (!d) return;
      dialog.close();
      if (form.dataset.inquiry) load();
      else go("dev", d.id);
    },
    "dev-edit": async (form) => {
      const d = await devSave({ title: form.elements.title.value, body: form.elements.body.value, since: form.dataset.since }, "고쳤어요. 기록에 남았어요");
      // Closed once saved, or when someone else's change came in instead (the screen shows it now).
      if (d || S.data.dev.updatedAt !== form.dataset.since) dialog.close();
    },
    "dev-release": async (form) => {
      const v = form.elements.v.value.trim();
      await devSave({ releasedIn: v || null, since: S.data.dev.updatedAt }, v ? `${v}에 반영으로 적었어요` : "반영 버전을 지웠어요");
    },
    "dev-note": async (form) => {
      const r = await act(() => api(`/dev/${S.route.id}/notes`, "POST", { text: form.elements.text.value }), "메모를 남겼어요");
      if (r) {
        S.data.dev = r;
        render({ keepScroll: true });
      }
    },
    "dev-reject": (form) => {
      const reason = form.elements.reason.value.trim();
      if (!reason) return toast(ERRORS.reason);
      dialog.close();
      if (form.dataset.ids) devBulk("rejected", reason);
      else devSetStatus("rejected", reason);
    },
    "dev-reply": async (form) => {
      const ids = [...form.querySelectorAll('input[name="ids"]:checked')].map((x) => x.value);
      if (!ids.length) return toast(ERRORS.ids);
      const r = await act(() => api(`/dev/${S.route.id}/reply`, "POST", { text: form.elements.text.value, ids, close: form.elements.close.checked }),
        (x) => `문의 ${x.answered}건에 답했어요. 보낸 분들 앱에 보여요`);
      if (r) {
        dialog.close();
        S.data.dev = r.item;
        render({ keepScroll: true });
        load({ quiet: true });
      }
    },
    invite: async (form) => {
      const d = S.data.admins;
      const r = await act(() => api("/invites", "POST", {
        email: form.elements.email.value, note: form.elements.note.value, perms: readPermForm(form, d),
      }));
      if (r) {
        openInviteLink(r);
        load({ quiet: true });
      }
    },
    "admin-perms": async (form) => {
      const d = S.data.admins;
      const body = { perms: readPermForm(form, d) };
      if (form.dataset.approve) body.status = "active";
      const r = await act(() => api(`/admins/${form.dataset.id}`, "POST", body), form.dataset.approve ? "승인했어요. 이제 로그인할 수 있어요" : "권한을 바꿨어요");
      if (r) {
        dialog.close();
        S.data.admins = r;
        render({ keepScroll: true });
        load({ quiet: true });
      }
    },
    password: async (form) => {
      const r = await act(() => api("/me/password", "POST", { current: form.elements.current.value, next: form.elements.next.value }), "비밀번호를 바꿨어요");
      if (r) form.reset();
    },
    join: async (form) => {
      S.error = "";
      const data = new FormData(form);
      const keep = readJoinForm();
      if (data.get("password") !== data.get("password2")) {
        S.error = "두 비밀번호가 달라요.";
      } else {
        try {
          await api("/join", "POST", { token: S.join.token, name: data.get("name"), password: data.get("password"), code: data.get("code") ?? undefined });
          S.join.done = true;
          history.replaceState(null, "", "/admin/join");
        } catch (e) {
          S.error = e.message === "code" ? `확인 코드가 달라요. ${e.data?.left ?? 0}번 더 할 수 있어요.`
            : e.message === "code_expired" ? "확인 코드가 만료됐어요. 코드를 다시 보내 주세요."
            : e.message === "exists" ? "이 메일로 만든 계정이 이미 있어요. 로그인 화면에서 로그인해 주세요."
            : ERRORS[e.message] || "계정을 만들지 못했어요. 잠시 뒤 다시 해 주세요.";
        }
      }
      render();
      if (!S.join.done) fillJoinForm(keep);
    },
  };

  // ---- events ----

  /** [ADMUX-01] A button doing its work shows it and takes no second tap until it is done. */
  async function busy(el, work) {
    if (el?.getAttribute?.("aria-busy") === "true") return;
    const button = el?.tagName === "BUTTON" ? el : null;
    button?.setAttribute("aria-busy", "true");
    try {
      await work();
    } finally {
      button?.removeAttribute("aria-busy");
    }
  }

  // AT 1.29 시작 [ADMCP-02] Shift on a picked list picks a range; a long press on a question's row (a phone) opens picking
  // with it, and the tap that ends the press does not open the question.
  document.addEventListener("click", (e) => {
    const row = e.target.closest?.("label[data-pick-row]");
    if (row && e.shiftKey && S.inquiryPick && S.pickAnchor && pickRange(row.dataset.pickRow)) e.preventDefault();
    if (longPressed && e.target.closest?.("[data-row]")) {
      longPressed = false;
      e.preventDefault();
      e.stopPropagation();
    }
  }, true);
  let pressTimer = 0;
  let longPressed = false;
  document.addEventListener("pointerdown", (e) => {
    const row = e.target.closest?.("[data-row]");
    longPressed = false;
    clearTimeout(pressTimer);
    if (!row || S.route.name !== "inquiries" || S.route.id || e.pointerType === "mouse") return;
    pressTimer = setTimeout(() => {
      longPressed = true;
      navigator.vibrate?.(15);
      togglePick(row.dataset.row);
    }, 500);
  });
  for (const type of ["pointerup", "pointercancel", "pointermove"]) {
    document.addEventListener(type, (e) => {
      if (type !== "pointermove" || Math.abs(e.movementX ?? 0) + Math.abs(e.movementY ?? 0) > 6) clearTimeout(pressTimer);
    });
  }
  document.addEventListener("contextmenu", (e) => {
    if (longPressed && e.target.closest?.("[data-row]")) e.preventDefault();
  });
  // AT 1.29 끝

  document.addEventListener("click", (e) => {
    if (e.target.closest("[data-undo]")) {
      const undo = S.undo;
      S.undo = null;
      toastBox.classList.remove("show", "has-undo");
      if (undo) Promise.resolve(undo()).catch(fail);
      return;
    }
    const el = e.target.closest("[data-act]");
    if (!el || !ACTIONS[el.dataset.act] || el.disabled) return;
    e.preventDefault();
    busy(el, () => ACTIONS[el.dataset.act](el.dataset, el));
  });
  document.addEventListener("submit", (e) => {
    const form = e.target.closest("form[data-form]");
    if (!form) return;
    e.preventDefault();
    if (form.dataset.sending) return;
    form.dataset.sending = "1";
    const submitter = e.submitter ?? form.querySelector("[type=submit]");
    submitter?.setAttribute("aria-busy", "true");
    S.submitting = ["merge-search", "login", "relogin", "confirm"].includes(form.dataset.form) ? null : form;
    Promise.resolve(FORMS[form.dataset.form]?.(form, e.submitter)).catch(fail).finally(() => {
      S.submitting = null;
      delete form.dataset.sending;
      submitter?.removeAttribute("aria-busy");
    });
  });

  /** Changes that are a setting, not a form: [data-change] names which. */
  const CHANGES = {
    ...FLAG_CHANGES,
    hidden: async (t) => {
      if (S.route.name !== "companies") return;
      const c = await act(() => api(`/companies/${S.route.id}`, "POST", { hidden: t.checked }),
        (r) => (r.hidden ? "회사 찾기에서 숨겼어요" : "다시 회사 찾기에 나와요"));
      if (c) S.data.company = c;
      render({ keepScroll: true });
    },
    // [ADMUX-13] A period by dates: both set, the 7·28·90 buttons step aside.
    "metric-from": (t) => {
      S.metricFrom = t.value;
      if (!S.metricTo) S.metricTo = S.data.metrics?.to ?? "";
      refilter("metrics");
    },
    "metric-to": (t) => {
      S.metricTo = t.value;
      if (!S.metricFrom) S.metricFrom = S.data.metrics?.from ?? "";
      refilter("metrics");
    },
    "inquiry-sort": (t) => {
      S.inquirySort = t.value;
      refilter("inquiries");
    },
    "audit-actor": (t) => {
      S.audit.actor = t.value;
      refilter("audit");
    },
    "audit-kind": (t) => {
      S.audit.kind = t.value;
      refilter("audit");
    },
    "audit-from": (t) => {
      S.audit.from = t.value;
      refilter("audit");
    },
    "audit-to": (t) => {
      S.audit.to = t.value;
      refilter("audit");
    },
    "auto-reward": (t) => {
      S.draft = { adoptAgree: 75, ...(S.draft ?? S.overview?.settings ?? {}), autoReward: t.checked ? 1 : 0 };
      render({ keepScroll: true });
    },
    "pref-theme": (t) => {
      S.prefs.theme = t.value;
      savePrefs();
    },
    "pref-size": (t) => {
      S.prefs.size = t.value;
      savePrefs();
    },
    "pref-sound": (t) => {
      S.prefs.sound = t.checked;
      savePrefs();
      if (t.checked) chime();
    },
    "pref-next": (t) => {
      S.prefs.next = t.checked;
      savePrefs();
    },
    "pref-tab": (t) => {
      // AN 1.28 [ADMUX-30] 오늘 and three hubs: a fourth one in takes the place of the one that has been there longest.
      const list = hubs();
      let now = L.bottomHubs(list, S.prefs.tabs, "home").tabs.filter((k) => k !== "today");
      let out = "";
      if (t.checked) {
        now = [...now.filter((k) => k !== t.value), t.value];
        if (now.length > 3) out = now.shift();
      } else {
        now = now.filter((k) => k !== t.value);
      }
      S.prefs.tabs = now;
      savePrefs();
      const name = (k) => L.HUBS.find((h) => h.key === k)?.label ?? k;
      if (out) toast(`아래 탭은 오늘과 3개까지라 ${name(out)} 대신 ${name(t.value)}을(를) 넣었어요`);
      render({ keepScroll: true });
    },
  };

  document.addEventListener("change", (e) => {
    const t = e.target;
    // [ADMIN-10] Picking 개발사항 for 일괄 복사·처리.
    if (t.dataset?.pick && S.devPick) {
      if (t.checked) S.devPick.add(t.dataset.pick);
      else S.devPick.delete(t.dataset.pick);
      const box = document.getElementById("dev-list");
      if (box) box.innerHTML = devRows();
      return;
    }
    // [ADMUX-19] Picking posts and 임금표 entries.
    for (const [attr, field] of [["pickPost", "postPick"], ["pickRate", "ratePick"]]) {
      if (t.dataset?.[attr] && S[field]) {
        if (t.checked) S[field].add(t.dataset[attr]);
        else S[field].delete(t.dataset[attr]);
        render({ keepScroll: true });
        return;
      }
    }
    // [ADMIN-16] Picking questions.
    if (t.dataset?.pickInquiry && S.inquiryPick) {
      if (t.checked) S.inquiryPick.add(t.dataset.pickInquiry);
      else S.inquiryPick.delete(t.dataset.pickInquiry);
      S.pickAnchor = t.dataset.pickInquiry; // AT 1.29 [ADMCP-02] where a Shift range starts
      render({ keepScroll: true });
      return;
    }
    // AT 1.29 [ADMCP-01][ADMCP-03] The copy box's choices: the text changes at once, the steps after are remembered.
    if (t.dataset?.copyOpt && S.copy) {
      S.copy[t.dataset.copyOpt] = t.checked;
      return paintCopy();
    }
    if (t.dataset?.copyAfter && S.copy) {
      S.copy[t.dataset.copyAfter] = t.checked;
      return paintCopy();
    }
    const run = CHANGES[t.dataset?.change];
    if (run) Promise.resolve(run(t)).catch(fail);
  });

  /** [ADMIN-16] Words typed in a list's search box: the list comes again a quarter second after the last key. */
  const SEARCH = {
    dev: () => ["devQuery", "devList", () => loadDev()],
    companies: () => ["query", "list", async () => { S.data.list = await api(companiesPath()); }],
    inquiries: () => ["inquiryQuery", "inquiries", () => loadInquiries()],
    community: () => ["postQuery", S.postFilter === "rates" ? "rates" : "posts", () => load({ quiet: true })],
    rewards: () => ["rewardQuery", "rewards", () => load({ quiet: true })],
    // AM 1.28 [ADMUX-21] 기능 관리 finds in the list it has.
    flags: () => ["flagQuery", "flags", async () => {}],
  };

  /**
   * AX 1.30 [ADMX-12] The screens that live in src/admin/views/: each group gets what the app shares (A) and gives
   * back its screens, and its own buttons and forms, which join ACTIONS and FORMS.
   */
  const A = {
    S, L, esc, icon, P, day, moment, ago, can, href, seg, empty, skeleton, lines, api, act, load, render, toast, fail, openDialog, dialog,
    confirmBox, syncUrl, refilter, go, flagAlertCard, waitingItems, snoozed, inboxLink, dueText, INBOX_NAMES, KIND_NAMES, toggles, wide,
  };
  const V = { ...M.today(A), ...M.plaza(A), ...M.insight(A) };
  Object.assign(ACTIONS, V.actions ?? {});
  Object.assign(FORMS, V.forms ?? {});

  // toggle does not bubble, so it is caught on the way down.
  document.addEventListener("toggle", (e) => {
    if (e.target instanceof HTMLDetailsElement && e.target.dataset.key) S.opened.set(e.target.dataset.key, e.target.open);
  }, true);

  /**
   * [ADMUX-04] a, x and s on 검토: 채택 or 거절 for the card in focus (the first one if none), s to the next
   * card. 채택 still asks first.
   */
  function reviewKey(act) {
    // AX 1.30 [ADMX-04] The line j/k is on, else the one in focus, else the first; s goes to the next line.
    const items = [...document.querySelectorAll("#main [data-review]")];
    const cursorItem = rowsOnScreen()[S.cursor]?.closest?.("[data-review]");
    const focused = document.activeElement?.closest?.("[data-review]");
    const at = items.indexOf(cursorItem ?? focused);
    if (!act) {
      const next = items[at + 1] ?? items[0];
      if (!next) return;
      const rows = rowsOnScreen();
      S.cursor = rows.indexOf(next.querySelector("summary"));
      markCursor();
      next.querySelector("summary")?.focus();
      return;
    }
    if (!can("review", "edit")) return;
    const item = items[Math.max(at, 0)];
    item?.querySelector(`[data-act="${act}"]`)?.click();
  }

  document.addEventListener("input", (e) => {
    const t = e.target;
    if (t.dataset?.strength) {
      const m = document.getElementById(t.dataset.strength);
      if (m) m.value = L.strength(t.value);
    }
    const nf = t.closest?.('form[data-form="notice"]');
    if (nf) {
      const v = (name) => nf.elements[name]?.value ?? "";
      const box = document.getElementById("nt-preview");
      if (box) box.innerHTML = noticePreview(v("title"), v("body"), nf.querySelector('input[name="level"]:checked')?.value);
    }
    if (KEEP.includes(t.id) && t.closest("#main")) {
      writeDraft(t.id, t.value);
      if (t.id === "reply") {
        const saved = document.getElementById("reply-saved");
        if (saved) saved.textContent = t.value ? "적는 중인 답은 이 기기에 저장돼요" : "";
        pingTyping(Boolean(t.value.trim()));
      }
    }
    if (t.id === "reply") {
      const n = document.getElementById("reply-count");
      if (n) n.textContent = `${t.value.length}/2000`;
      t.style.height = "auto";
      t.style.height = `${Math.min(t.scrollHeight + 4, window.innerHeight * 0.5)}px`;
    }
    if (t.id === "pal" && S.palette) {
      S.palette.q = t.value;
      S.palette.at = 0;
      // AX 1.30 [ADMX-08] Two letters or more also ask the server (회사·문의·개발사항·관리자), a quarter second after the last key.
      clearTimeout(searchTimer);
      const q = t.value;
      if (q.trim().length >= 2) {
        searchTimer = setTimeout(() => {
          api(`/search?q=${encodeURIComponent(q.trim())}`).then((d) => {
            if (!S.palette || S.palette.q !== q) return;
            S.palette.server = { q, groups: d.groups ?? [] };
            paintPalette();
          }).catch(() => {
            if (S.palette?.q === q) S.palette.server = { q, groups: [] };
            paintPalette();
          });
        }, 250);
      }
      return paintPalette();
    }
    if (t.id === "dev-find") return showCandidates(t.value);
    if (t.id === "flag-cond") return flagInput(t);
    if (t.id !== "search") return;
    const plan = SEARCH[S.route.name]?.();
    if (!plan) return;
    const [field, , fetchIt] = plan;
    S[field] = t.value;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(async () => {
      syncUrl();
      await act(fetchIt);
      render({ keepScroll: true });
    }, 250);
  });

  // ---- [ADMUX-04] keys ----

  /** The rows a list shows, for j and k. */
  // AX 1.30 [ADMX-04] 검토's lines are the <summary> of a row that opens in place.
  const rowsOnScreen = () => [...document.querySelectorAll("#main .rows > li > .row, #main .rows > li.item, #main .queue-list > li, #main .rows > li > details > summary.row")];

  function markCursor() {
    const rows = rowsOnScreen();
    rows.forEach((r, i) => r.classList.toggle("selected", i === S.cursor));
    const el = rows[S.cursor];
    if (el) el.scrollIntoView({ block: "nearest" });
  }

  function moveCursor(by) {
    const rows = rowsOnScreen();
    if (!rows.length) return;
    S.cursor = Math.max(0, Math.min(rows.length - 1, (S.cursor < 0 ? (by > 0 ? -1 : rows.length) : S.cursor) + by));
    markCursor();
  }

  function openCursor() {
    const el = rowsOnScreen()[S.cursor];
    // [ADMX-04] Enter on a 검토 line opens it in place (and shuts it again).
    if (el?.matches?.("summary")) {
      el.parentElement.open = !el.parentElement.open;
      return;
    }
    const link = el?.matches("a,button") ? el : el?.querySelector("a,button");
    link?.click();
  }

  /**
   * AX 1.30 [ADMX-05] The next question that waits for an answer after the one open (or the line in focus), from the
   * list on screen; with no list yet, the list of waiting questions, the oldest deadline first.
   */
  function nextWaiting() {
    const list = (S.data.inquiries?.inquiries ?? []).filter((q) => q.status === "open");
    const here = S.route.name === "inquiries" && S.route.id ? S.route.id
      : rowsOnScreen()[S.cursor]?.querySelector?.("[data-row],[data-pick-row]")?.dataset?.row ?? null;
    const at = list.findIndex((q) => q.id === here);
    const next = list.slice(at + 1).find((q) => q.id !== here) ?? list.find((q) => q.id !== here);
    if (next) return go("inquiries", next.id);
    if (S.route.name === "inquiries" && !S.route.id) return toast("답을 기다리는 문의가 없어요");
    location.hash = L.routeHash("inquiries", null, { status: "open", sort: "sla" });
  }

  /** What a key does on the screen it was pressed on. */
  function onKey(a, e) {
    const detail = S.route.name === "inquiries" && S.route.id;
    const editInq = can("inquiries", "edit");
    const stop = () => e.preventDefault();
    switch (a.act) {
      case "palette":
        stop();
        return openPalette();
      case "send": {
        const box = document.getElementById("reply");
        if (e.target === box && box.form) {
          stop();
          box.form.requestSubmit(box.form.querySelector(".btn.primary"));
        }
        return;
      }
      case "escape":
        if (S.queue && !dialog.open) return ACTIONS["queue-stop"]();
        return;
      case "pending":
        gKey = "g";
        clearTimeout(gTimer);
        gTimer = setTimeout(() => { gKey = ""; }, 1200);
        return;
      case "go": {
        if (a.to === "settings" || tabOk(a.to)) {
          stop();
          go(a.to);
        }
        return;
      }
      case "move":
        stop();
        return moveCursor(a.by);
      case "open":
        if (S.cursor >= 0) {
          stop();
          openCursor();
        }
        return;
      case "back": {
        const back = document.querySelector("#bar a.icon-btn");
        if (back) {
          stop();
          back.click();
        }
        return;
      }
      case "step": {
        if (detail) {
          const nb = neighbours();
          const to = a.by > 0 ? nb.next : nb.prev;
          if (to) {
            stop();
            go("inquiries", to);
          }
          return;
        }
        // AN 1.28 [ADMUX-36] Elsewhere [ and ] go through the open hub's tabs.
        const tabs = hubNow()?.tabs ?? [];
        const at = tabs.indexOf(S.route.name);
        if (at >= 0 && !S.route.id && tabs.length > 1) {
          stop();
          go(tabs[(at + a.by + tabs.length) % tabs.length]);
        }
        return;
      }
      case "search": {
        const s = document.getElementById("search");
        if (s) {
          stop();
          s.focus();
        }
        return;
      }
      case "keys":
        stop();
        return openShortcuts();
      // AT 1.29 [ADMCP-01][ADMCP-02] c: copy the open question, the picked ones, or the row in focus; Space picks that row.
      case "copy": {
        if (S.route.name !== "inquiries") return;
        const row = rowsOnScreen()[S.cursor]?.querySelector?.("[data-row],[data-pick-row]") ?? rowsOnScreen()[S.cursor];
        const at = row?.dataset?.row ?? row?.dataset?.pickRow;
        const ids = detail ? [S.route.id] : S.inquiryPick?.size ? pickedIds() : at ? [at] : [];
        if (!ids.length) return;
        stop();
        return openCopy(ids);
      }
      case "pick": {
        if (S.route.name !== "inquiries" || detail || S.cursor < 0) return;
        const row = rowsOnScreen()[S.cursor];
        const el = row?.matches?.("[data-row],[data-pick-row]") ? row : row?.querySelector?.("[data-row],[data-pick-row]");
        const id = el?.dataset?.row ?? el?.dataset?.pickRow;
        if (!id) return;
        stop();
        if (e.shiftKey && S.inquiryPick && S.pickAnchor) return pickRange(id);
        return togglePick(id);
      }
      case "assign":
        if (S.route.name === "review") return reviewKey("adopt");
        if (detail && editInq && S.data.inquiry && !S.data.inquiry.assignee) return ACTIONS["inquiry-assign"]({ to: "me" });
        return;
      case "reply":
        // [ADMUX-04] r: the answer box on a question, a fresh list anywhere else.
        stop();
        if (detail) return document.getElementById("reply")?.focus();
        return load();
      case "close":
        if (detail && editInq && S.data.inquiry?.status !== "closed") return ACTIONS["inquiry-state"]({ status: "closed" });
        return;
      case "reject":
        if (S.route.name === "review") return reviewKey("reject");
        if (detail && S.queue) return ACTIONS["inquiry-state"]({ status: "closed" });
        return;
      // AX 1.30 [ADMX-05] n: the next question waiting for an answer.
      case "next":
        stop();
        return nextWaiting();
      case "skip":
        if (S.route.name === "review") return reviewKey(null);
        if (S.queue) return queueNext(true);
        return;
      case "queue":
        if (!editInq) return;
        return S.queue ? ACTIONS["queue-stop"]() : ACTIONS["queue-start"]();
      case "template": {
        if (detail && document.getElementById("reply") && templateList()[a.n - 1]) {
          stop();
          return ACTIONS.template({ i: a.n - 1 });
        }
        // AN 1.28 [ADMUX-36] 1~6 are the hubs everywhere else, always the same number; a hub opens on its last tab.
        const hub = L.HUBS[a.n - 1] && hubs().find((h) => h.key === L.HUBS[a.n - 1].key);
        if (!detail && hub) {
          stop();
          go(lastTab[hub.key] && hub.tabs.includes(lastTab[hub.key]) ? lastTab[hub.key] : hub.tabs[0]);
        }
        return;
      }
      default:
    }
  }

  document.addEventListener("keydown", (e) => {
    if (S.phase !== "app") return;
    // The palette's own keys.
    if (S.palette && dialog.open) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        S.palette.at = Math.max(0, Math.min((S.palette.list?.length ?? 1) - 1, S.palette.at + (e.key === "ArrowDown" ? 1 : -1)));
        return paintPalette();
      }
      if (e.key === "Enter" && e.target.id === "pal") {
        e.preventDefault();
        return paletteGo(S.palette.at);
      }
      return;
    }
    // [ADMUX-03] The level buttons of a permission are a radio group: arrows move and pick.
    if (e.target.getAttribute?.("role") === "radio" && ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key)) {
      const group = [...e.target.parentElement.querySelectorAll('[role="radio"]:not(:disabled)')];
      const i = group.indexOf(e.target) + (e.key === "ArrowRight" || e.key === "ArrowDown" ? 1 : -1);
      const next = group[(i + group.length) % group.length];
      e.preventDefault();
      setLevel(next.dataset.area, next.dataset.level);
      next.focus();
      return;
    }
    if (dialog.open && e.key !== "Escape") return;
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName) || e.target.isContentEditable;
    const a = L.keyAction(e.key, { ctrl: e.ctrlKey, meta: e.metaKey, alt: e.altKey, shift: e.shiftKey }, typing, gKey);
    if (gKey && a?.act !== "pending") gKey = "";
    if (a) onKey(a, e);
  });
  dialog.addEventListener("click", (e) => {
    // A tap outside the box closes it.
    if (e.target === dialog) dialog.close();
  });
  dialog.addEventListener("close", () => {
    // [ADMUX-10] Closed another way: its history step becomes a plain one, so back is not spent on nothing later.
    if (history.state?.dialog) history.replaceState(null, "", location.href);
    S.palette = null;
    S.linkMode = "";
    if (S.relogin) {
      // Closed without signing in: the sign-in screen, since nothing else would work.
      S.relogin = false;
      signedOut();
    }
  });
  window.addEventListener("popstate", () => {
    if (dialog.open && !S.relogin && !history.state?.dialog) dialog.close();
  });
  window.addEventListener("hashchange", () => {
    if (S.phase !== "app") return;
    const before = S.route;
    const next = readRoute();
    const moved = before.name !== next.name || before.id !== next.id;
    // [ADMUX-01] Settings changed and not saved: asked before they are dropped.
    if (moved && ((S.draft && before.name === "adopt") || (S.slaDraft && before.name === "sla")) && !confirm("저장하지 않은 설정이 있어요. 버리고 나갈까요?")) {
      history.replaceState(null, "", `/admin/${href(before.name, before.id)}`);
      return;
    }
    if (moved && before.name === "inquiries" && before.id) pingTyping(false);
    // [ADMUX-10] A sheet or a question box belongs to the screen it was opened on (점검 보고서 중-14).
    if (moved && dialog.open && !S.relogin) dialog.close();
    S.route = next;
    applyParams(next);
    // AN 1.28 [ADMUX-35] An old address shows as the new one.
    fixAddress();
    if (moved) {
      S.draft = null;
      S.slaDraft = null;
      S.cursor = -1;
      if (next.name !== "inquiries") S.inquiryPick = null;
      // 큐 모드 ends once the admin goes somewhere else than a question of the queue.
      if (S.queue && !(next.name === "inquiries" && S.queue.ids.includes(next.id))) S.queue = null;
      render();
      window.scrollTo(0, 0);
      document.getElementById("title")?.focus({ preventScroll: true });
    }
    load();
    schedule();
  });
  window.addEventListener("online", () => {
    S.online = true;
    if (S.phase === "app") {
      paintBanners();
      load({ quiet: true });
    }
  });
  window.addEventListener("offline", () => {
    S.online = false;
    if (S.phase === "app") paintBanners();
  });
  window.addEventListener("beforeunload", (e) => {
    // Typed answers are kept in the browser; only settings would be lost.
    if (S.draft || S.slaDraft) {
      e.preventDefault();
      e.returnValue = "";
    }
  });

  /**
   * [ADMUX-08] Fresh numbers while the app is open: every 30 seconds on 오늘 and on a question (where someone else
   * may be answering), every 90 seconds elsewhere, never while the tab is hidden. A new question can chime
   * (관리 › 내 계정·이 기기). AN 1.28 [ADMUX-37] Only the numbers (/badges) are asked; an open question comes again
   * too, and 오늘 when its numbers moved.
   */
  function schedule() {
    clearInterval(pollTimer);
    const fast = S.route.name === "home" || (S.route.name === "inquiries" && S.route.id);
    pollTimer = setInterval(poll, fast ? 30000 : 90000);
  }

  async function poll() {
    if (document.visibilityState !== "visible" || S.phase !== "app" || !S.online || dialog.open) return;
    const before = S.overview?.inquiries ?? 0;
    const was = countsKey();
    await refreshBadges(true);
    const typingNow = document.activeElement?.closest?.("#main") && /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName);
    if (S.route.name === "inquiries" && S.route.id && !typingNow) await load({ quiet: true });
    else if (S.route.name === "home" && !typingNow && countsKey() !== was) await load({ quiet: true });
    if (S.prefs.sound && (S.overview?.inquiries ?? 0) > before) chime();
  }

  // R131 1.31 [ADMX-15] Back to the tab while a reply was being typed: the numbers come again, the screen is left as it is.
  const typing = () => Boolean(document.activeElement?.closest?.("#main")) && /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName ?? "");
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && S.phase === "app") (typing() || dialog.open ? refreshBadges(true) : load({ quiet: true }));
    if (document.visibilityState === "hidden") pingTyping(false);
  });
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    S.installPrompt = e;
    if (S.phase !== "boot") render({ keepScroll: true });
  });
  window.addEventListener("appinstalled", () => {
    S.installed = true;
    S.installPrompt = null;
  });

  /**
   * Opens the app. AN 1.28 [ADMUX-37] Who is signed in and the menu numbers come first (/badges, light), the frame
   * is drawn, then the screen asks for its own data; the shared words come beside it.
   */
  async function enter() {
    const b = await api("/badges");
    badgesAt = Date.now();
    S.overview = { ...b };
    S.me = b.me;
    S.newBuild = Boolean(b.build && b.build !== BUILD);
    S.phase = "app";
    S.route = readRoute();
    applyParams(S.route);
    if (!location.hash) history.replaceState(null, "", `/admin/${href(S.route.name)}`);
    else fixAddress();
    render();
    load({ quiet: true });
    schedule();
    loadBasics().then(() => { if (S.phase === "app") render({ keepScroll: true }); }).catch(() => {});
    if ("serviceWorker" in navigator && "PushManager" in window) currentPush();
    // R131 1.31 [ADMX-14] The morning push (하루 3분 정리) opens here with ?start=queue: straight into 큐 모드.
    if (new URLSearchParams(location.search).get("start") === "queue") {
      history.replaceState(null, "", `/admin/${location.hash}`);
      if (can("inquiries", "edit")) ACTIONS["queue-start"]();
    }
  }

  (async () => {
    applyPrefs();
    if ("serviceWorker" in navigator) navigator.serviceWorker.register("/admin/sw.js", { scope: "/admin/" }).catch(() => {});
    const path = location.pathname;
    if (path === "/admin/join") {
      S.phase = "join";
      const token = location.hash.replace(/^#/, "");
      render();
      try {
        // [ADMIN-15] The token goes in the body, so it is in no server log or proxy's address line.
        const info = await api("/join/info", "POST", { token });
        S.join = { ...info, token };
      } catch (e) {
        S.join = { error: e.message === "rate_limited" ? "rate_limited" : "invite" };
      }
      render();
      return;
    }
    if (path === "/admin/install") {
      S.phase = "install";
      render();
      return;
    }
    // What the server still lacks only shows on the login screen, so it is not waited for before signing in.
    const checked = health().then((h) => { S.health = h; return h; });
    try {
      // [ADMUX-11] Asked first, so a signed-out start shows the login without a 401 in the console.
      if (!(await api("/session")).signedIn) throw new Error("login");
      await enter();
    } catch {
      await checked;
      S.phase = "login";
      render();
    }
  })();
}

/**
 * The developer app's service worker. The app's own files open from the phone at once and are refreshed
 * in the background for the next time, so opening the app does not wait on the network; a new version
 * of the app changes [version], and the phone then fetches every file again. [ADMUX-01] What the API
 * says is fetched fresh each time; the last answer of each GET is kept only so that a lost connection
 * still shows something (marked x-admin-cache), and signing out deletes it. [ADMIN-19] Pushes become
 * notifications that open the screen they are about.
 */
function adminWorker(version) {
  const CACHE = `bamnat-admin-${version}`;
  const API = "bamnat-admin-api";
  const SHELL = ["/admin/", "/admin/app.js", "/admin/app.css", "/admin/icon.svg", "/admin/manifest.webmanifest", "/admin/install"];
  self.addEventListener("install", (event) => {
    event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL.map((u) => new Request(u, { cache: "reload" })))).then(() => self.skipWaiting()));
  });
  self.addEventListener("activate", (event) => {
    event.waitUntil(caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE && k !== API).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()));
  });
  self.addEventListener("fetch", (event) => {
    const url = new URL(event.request.url);
    if (event.request.method !== "GET" || url.origin !== self.location.origin) return;
    if (url.pathname.startsWith("/api/admin/")) {
      // Sign-in state, photos and the setup check are never kept.
      if (/^\/api\/admin\/(health|join|inquiries\/[^/]+\/photos)/.test(url.pathname)) return;
      const key = new Request(url.href);
      event.respondWith(fetch(event.request).then(async (res) => {
        if (res.status === 200) {
          const copy = res.clone();
          event.waitUntil(caches.open(API).then((c) => c.put(key, copy)));
        } else if (res.status === 401) {
          event.waitUntil(caches.delete(API));
        }
        return res;
      }, async () => {
        const hit = await caches.open(API).then((c) => c.match(key));
        if (!hit) throw new TypeError("offline");
        const headers = new Headers(hit.headers);
        headers.set("x-admin-cache", "1");
        headers.delete("etag");
        return new Response(await hit.blob(), { status: 200, headers });
      }));
      return;
    }
    if (!url.pathname.startsWith("/admin/")) return;
    const fresh = fetch(event.request).then(async (res) => {
      if (res.ok) await caches.open(CACHE).then((c) => c.put(event.request, res.clone()));
      return res;
    });
    event.waitUntil(fresh.catch(() => {}));
    event.respondWith(caches.open(CACHE).then((c) => c.match(event.request, { ignoreSearch: true }))
      .then((hit) => hit ?? fresh.catch(() => caches.match("/admin/"))));
  });
  self.addEventListener("message", (event) => {
    if (event.data?.type === "forget") event.waitUntil(caches.delete(API));
  });
  self.addEventListener("push", (event) => {
    let m = {};
    try {
      m = event.data ? event.data.json() : {};
    } catch {
      m = { body: event.data?.text() ?? "" };
    }
    event.waitUntil(self.registration.showNotification(m.title || "밤낮 관리자", {
      body: m.body || "", tag: m.tag || "admin", renotify: Boolean(m.urgent), requireInteraction: Boolean(m.urgent),
      icon: "/admin/icon-192.png", badge: "/admin/icon-192.png", data: { url: m.url || "/admin/" },
    }));
  });
  self.addEventListener("notificationclick", (event) => {
    event.notification.close();
    const target = new URL(event.notification.data?.url || "/admin/", self.location.origin);
    if (target.origin !== self.location.origin || !target.pathname.startsWith("/admin/")) return;
    event.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((list) => {
      const open = list.find((c) => new URL(c.url).pathname.startsWith("/admin/"));
      if (open) return open.focus().then((c) => c.navigate?.(target.href) ?? c);
      return self.clients.openWindow(target.href);
    }));
  });
}

const ICONS = {
  "/admin/icon-192.png": "iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAMAAABlApw1AAAASFBMVEXOrXDMqm/Hp23Do2vCo2vBo2vBomvAoWq/oWq8nmmojmF/bVJHQT0gIi8YGywXGywWGisWGSsVGSsUGSsSFyoNEygKECcIDyast4RLAAAGVklEQVR42u2c23LrKgxAAbtgc68N+P//9EC6mzTNpYCJHc8RT57OtNVCEkICgaaDDwQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAewFYc2AAq83kw3xQAKutD2FWUjl7QIAo/eKUFByLbeVvAmD1HLwSnCKM5GKPZkJWu8VI3iHcj1gs+mhOHMWPkx+lH9iAuTfl/Fqb/QDMFMUfERlY31MyFjuwjb6zLH4y+wBY7WeZxKd9HIzIUGhAxkffEULO3uwBYOZF8m/x+wEVO4CO4ncIIcJlMNsDaK8Fwf/Ej/PP9WwL5z+qjw7DwAiuJqgGsGZRHFHW/xsMlxqQdWokw9cv91hWWhGq9t4gKR6+xe/jClQ6hzoI9P0Hov7cphowzgnUnae/p31XOoUnBdCzAkmlCuoAtNMcsfN/Twoo9uDoAeQyAxVLwAqAk/wX86mbwAiAdwK4I39FDE4aoLsA3MofLag4hqXBLzZEO7WVD5hb+Wl5DPi1Cg2Ie7vNKmRv5a/Uv50cR2OyIjqkbdRGccB68Vv+qIGqNTAupHEnQhkjaKyNY8UAerknf40FnaxxFpxgMgrla/MIVGq3P5eOiwWFuv9vpzBJKdXiNtqNXkfPC4AMtROoJx+80/V5aBmA8Rz/NqBkQ8rXS2CMWZNGozIHkOhW/moX2Dwnvm9AMQwLb6YjANxbgdbsArYGiJuXru/vAdT78MYAdz045WKHMKHr7e8hAfhdANqNG9dz6wCuE6irVfQgAEHc9YCjAKQY0NEDAzyIAUfSwH0XPgqA9ap/MI6xCv3MXw8ZBx4FgaMAPF6DDrIXMkE+VMAhdqPPXOAQ+YDx4rEGDpCR2Vlz8sgF1ubEmwA88+GdvRjlWdATH15TF9oMIDwF2NUJsgAe7+RW1UbfB2DPSJDpA+KZCe1qQ5kA/ClA7QnN+wDUnJG9FUD9Me+7AAx4LzduBFBxUv9eADV3JbaMA+F5HKi7rfJGgazyvtB7AewVjlvsRi9L6Q5GlAtAPv4E2KfClZ3QPMvILivR9ktpXk786Tj524Z2cYMGVYmfVoTl1gS5ADIPoN+cILeskuPFuxBkl9d5hhefCezbAeQ6wRdBmMy7AfxRl7gioEi4mus/L71yZmeVaUORgCGuys1Iu/mVPpBvQ6eINsrgipRgzKK0eyHAw2Pi+wQdFqrAE6z2QQr3Sg1MU14wvpjRKJ03Ok98F5RAdetvNkBuLDsrgSAufZj/9AVj/KJEj/hr40Duhu6HEgZCuDRLVMNDBmv0HELqwqmuCuSbUFZWc50gMIx49IXgrb69FxeFN84vWgqGyFB9VpsPUKyChBANiQmp3BL8bIzW5jRS59VnFD6cev9wN9D6fWzBhaeilfRiSB1GlEeIuNAskSON+OGdkkl6RBhblQsVANhpLlmIfqghMuCe89Rw9TXiFx8JitJ/9RANqHoLWHTpL38/ccPAeoIRQjiN9BUNhw7fPSAM13bQFN4bNTVGdAkNbBjY10gftM0BSRmAU6yjfeuxKhMtu3pcGs0yd07c1JfECm+vrzKiR9d1qPKbNYPa2d6/PFo/GFqVhBY3QHg1dqytA6yq5xW3oKQb7B+0ofxxBbVbApyiQU/bya/dqgS6po9saUawXv66Tr5WBCf5V1aR6nopIwFlbyF/bTdr9ORueAf5a/uJoyeP6+IBo0g0kL+6o1unhvSh3hFivim8a1DAq+6p196KajOiqXK02BYl1PpXDcwc5IAYq5p+xFWj87QVzzJYE5TApNiOWJ96P12jIvyqlz20Cz9exsguVXRCtTGf1QCTMWEWYz7CB6OYcLm4dmcga1+30f9eh6EZDDG7T6+QeN/yAGT9+0LpfR7JybnC8CgjHnqMRiF9ME1PPxq88JRqs+6rxhPFZDcUH+mnHUZ9qtL51s+gNXljy6au4FRlS6UeEk3lqvzQEYzwGKV3izPNz55avXJmohqCTcW2sTsXgPCpABRlF1L5xdtXHP61e2fu0+jZh3AqGQrBT+NUi1PaL8GZFx1dtn3pz2g9uUsN1Pv05WajzcsOXtu/tWi/qs+XOrT9nF444LFIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPi/AfwHwjw/dxGi3n0AAAAASUVORK5CYII=",
  "/admin/icon-512.png": "iVBORw0KGgoAAAANSUhEUgAAAgAAAAIACAMAAADDpiTIAAAASFBMVEXPrXDMqm7JqG3FpWzDo2vCo2vBo2vBomvAoWq/oWq0mGaHdFVQSEEmJzEYGywXGywWGisVGisWGSsVGSsUGSsRFioNEigJECcZgtFfAAASH0lEQVR42u2d6ZqbuBZFQZiIUVJKBt7/TVvClaqkkxpsMxzB2n/udXfydZX30pkQUubRqZXxFQAAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQAAAAXwEAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAKA5eSsscMVc88IQPTeD9M0GQMBZwPgJS78cZp837VNN7zg7pkAcMZex2nsu2B+qbJ2uDrcPQ0Awf0Q9k1c+SrLVJ21E/6fBgBn3BCXftsUWV7ouor+e/w/BwAvce0Pwf06y6L5uizrrCH+nwQAa67z2i/D0q/rYH5QrRozWJw9PgAvxo2TiWs/L+vqR3lTpZoe/08AgAuLf+jbJg9rvyrfpAvdjwZfjw6ANUNY/KHkL6q3tf8aALoJ/48OgA2xf178+jXvvyk2APh/cACsjbFfx8Vfln/5P5L/jw2AtePYtSq7/H/x3wpAM9AAHhmAV/tD7C//li4LCsBDA+DMzf6/Y/9rAhBVALogAFhSofLv2/wj+0UVAM5YNwT5+HQaAJax3wf7yw/tl1QABPOnUKgGhf8dvXEAsETpf+2arPzIfkETAOvnhxNtE9V2ffhoAOD56B/sL+oP7RczAXgxk+vaS5blRZDKM9V0/SS3GkgCABujf67+0fj9kQAkPAJ0PpKal3Vd6aCqqrXKmm4Q+3giBQDMOHR1Vn0c/WMLqDoJHaAdhkjqHz+qrousFdufZkks/8+jv5wEYAfT/INUXedNL3REnSWw/Nvi0+g/PwMU0QG46P8/Sa1VLTQGCAfA2bj8y0+jf6wAcgkdgPPDB/7fCBBZB8gGwAxjV+d1+aX/IkZAZmqzD3/WkAWuRIC7w79ps8tXy39+BiBgddmxy6vPyhSROxUEA3AL/19kfzkV4E9/bdQnAOhLSAIOAO4K/6H6+9J+KRWgmbqsTqBTSQWAOfx/Wf0Jiq1ubFX1BagCQ4BUAMwc/n98w3+tQnX1U8AIoL+kQWoKAIT0/53qX9LX+mUGmJsVAPjeanJTlxff8z8EAC8gAIQeoM2rr4sVca8sSQTADqGh1lWZUgAIP/QXJUAEQGAfIBAAM4Tyr9JlUgHADX1TfPUzV6oTt2lZHgCh/G+y74X/ecAmIwAAwHL+903+bf9DAPgpIQAAwILtX62+7b+czgoAdvFf0GyFInAH/yVNV2kDd/C/lPEYkEHQXv4L2QfwmgIYBW++/qX0gLcykIdBT/d/d/ovK6fyOPjJ7y/0Uff5L+wbZUPIkynU3Ot/qVUvqa1mS9hTcxR3x/z311illbWi2BT6VBd9r//ylhTbwtdZOx9LWk7lxZDHC2hV3eu+uAzgeTXscf/zUpepZ4AbAbwc+kD+7+uiKtPPALc6gNfD7/3Kru6ODQC/Hwgg8gvlgIiNCkC5Z4JyRMy9BcAj/ovcXPGGAIdEfd//sdcXfb//Mp+svOc1jon7fgGgHgoAufhTgTkocr0CQOxkPT1lO/v/WAFQlj+KnnPBkwfADn1d6Ef8F/JKOAA8OwJq88cCQAIlAACsmAC4GeQIAMQEcNHlgxGgm4gAqQPwcAKQur0KAO5MAHlVPhgA4iuBKGkA4uaJQj8KADVg8gA8PAKiBjwEAHbs1KPrX9gbIQDwWAvQPlwByH4UCAArjwCkvmcNAPdUgFfTPJEBRL5nDQBbVYA0AckD8K3zVADguAA8GQDYDJA4ACEAlE8JANIG4NkAQBeYNgBzANBPAZADQMIAPB0A2A+WNAD26QDAfrCkAXg+AAh/JwAAvqoAHt4HBAAHAOD5AMAkOGUA4lOA4rkAwCAwZQDiRrAnAwAApAyAHRtVAcBpAYgbgZ71HwASBmCBEhAAEgZggR4QAFIG4LmdYABwgAjQPl8CAECyANixV08nAABIF4BFSkAASBaAn1fbLBEBtGqsZxScHgD2iddBeRh0AAAWygAAkCgAT70PzIaQ9AFYZAz8ikA/UAUmB8BSGYBNoanWAG6RHoBt4YkCYMe+WMZ/XgxJEoBlngMAQMIRoM0XqgHjLcykgMQAePaNYGbBiQPwxb2ad8+C2RacGADLNYGMApME4GVolhoD8XJgggAsWQLcjokDgKQAWHAOzEGRKQKw4BSAPjDNCLDYFOB2WPTwgnkJAbDEK4G0AQkDYId+Oft5HJQgAAu8E8rTgIQBWHIMxDA4TQAWrAE5IyI5ABadA/7aFQYAyQCw7ByQIiA5AOJuoB/lsgAwC0wKgC5fOANQBKQEwLKDYB4IJghAuzQAFAFppYB24RRQVqqlDUgGgOW7QG6PTQmA5btA7g5MDACzPABMg9MBYPkxADkgMQCWHgPQB6QGgFoeAPaFJQPACnOgOQdU5IBUAGhXAIAccHYAKtVccfC8KYCdgUlFgKpcIwfwTDiRLqDN1wCAUcDJAaAMPDsAWjXe/8TF0wLAE6GzAxA6QfqAMwNAJ3h6AHgofG4ACAGnB4AQcG4ACAGnB4AQIB6AlZ4Gvh8ezyxAOgDdmgDEp8KMA88bARgHnh0A7hBKIAWsVwSyLyCFLmCNXcG/hYCSV4WlA7BqBJgPjsRLuQC4oa8uulw1CVAHigZghXcD/94cRhIQHAFWBiC0gswD5QKw7iyYJAAAJAHhAKw8CXpLAkyDxALQrQ4A4yDRKaBbOwXMm8QpA6QCsPB1AVwjkRgAC18YQhmQGgDeDa1aPwdQBogFYIs2gDJANADdNgAUdT9CgDwAtmkDYhJQjWEeJLEG6OuL3oSAvBk4R14cAN7/bNQmAMRCcHQQIA2ArapAWgGxAHRbAcCDQYkArHJc8EetgGafuDgA1t8U9McmUQgQVwRusCUAAiQDsGEVCAEyI0C3WQqAAIEAbPRAEAKkArBtEfCLAMtESAwAW04CXglQ7eh5LiAFADf05bbSVdZOA2lACABbbQr53xYhw9NhKQBsngPmqXDTUwoKASBOg3W5NQF5TSkoBICQBLZ6JPw7AYXqRgoBEQBsOwx8fzSUUQjIACDkgO0jwK9CwNIPCkgB2/cBrxsFCzlpwJ4YgD36gNsRMmXW9jJqQTPKenlp4wiw9lExH+lHSAOdgKGQ9VPXi3p7Kdv295+2fR7wRzeQ7x4EXFj+rbD3F7cFIB4YWO8DQKljENi1EjDXqW+k7VjdFoCtnwn/Kwj4nb5/ayfTFkraiYbZxotgl1HA+9OhvO78Lh1hiP5j12TxQENZY8mNAYhlYLlbCIhBIOaB0bit7R+mvs1VLe9c640BiCFgrzLwtRJQeRv7Abe1/Ze8quKx1tdTR4DVjw3+xkygylUoBTZDYLa/q7OyEvnSUrb5f3FodiagrHRWzgjYrVZ/k13qeY9SKe4Mm80B2LMTfC8FyhmB0a68HO28+pusqPXr/hRxmxM2B2DfTvB3BHTbjdN1vUzgjBvn1f9qfzzUXN6p9tungH07wT8QUG1np8Hal5UW/9C1+t3+eL+ZvLMrtgdgg5Ojv4tAlWdN24/LVwPO+LD4u1ZlZf3+q4q84XB7AMSEgGhJrbKq7cw0+uUYCKF/mHyM/Ur//nvKvOBwBwDkhIB5LnDJ86bt/MyAWyDyB/eHUPipEPv/7HZkXnG6AwCSQsBtMFBkKjDwcwr1gHmiIHDGXsfoflvGxf8/xoXecLoHAKJCwC0MaDUzEOqB8WofiAQu/CU/TJMJkV9neVn//etVeQcA77MASSHgjYGYC3ozhkgQDXXfigUv4Y+GsB/+kg9LP0R+Vdb/GnRJveJ4FwB++mujdFlKY6Aq8qxo2kBBWMzTEDEw1rp/kuCctYESe43ej30XzC+zPET+D8ecMu+z2AUAiSHg9XlxCAR5ltchFIRYMHs7DsPVRrPfFD9dh2EM/3YaTfQ+rvx56euPdyYLPbpsHwDi/uC8KkUqQFAVwc9ch1gQOOj6kBXG2e1fCp9sf3O+ber4Zwv9ifml5JPsdwJg/4eCn+8hreq6uhR5Hta1appIQhvdbt/+T1M387/O1SX+2a/ymdyT63YCQFor+EEsmL0tCpXn0ew3hU+qKEL/WFeV/s7hd4JvuN8LgO0OD17g3bJAQmThTeFT+If3jBrkHlezFwBi68B13k1rJ6lvpmX7/af33xmyVQiRfKXhfgDE4wLOEgAEn1e2YwRIoQ5c5FmD6LvtdwQg7g06QRLQZdELvtd6RwC8kT0MWLACNESA0yaBeIWNdwBw1iQQO4BR8uEkuwIQkkDoBPSxE4DwE4v3BeDw46B4g5Xsw4l2BiAeIX1gAqp4i6EDgE+fCRy4DJg7QOEH1O0NwKHLgBSuL9sdgAOXAfILABEAHLYMqHPxBYAMANzgGnU8Aqo0LjIXAEB8LlgXRysEU7m9UgIAsRDUxcEKwSqR+2tFADCfGnGsECB5E5BAAA7XCkT/07jDXggAB2sF6iw0AGmcTy8FAOcPRECtkvFfDADeXYfmIATUKokGUBgA3g7mGAQk5b8gAI5CQPQ/oZvqBAHgzREISMx/UQAcgYDU/JcFQPoEJOe/MABSJyA9/6UBkDYBof9P7qZiaQCEXmBIdSIUL6hM7opScQB4ex3brNJJ+m/Su6RYHgBxKtzlye0P0FXWugQvqRYIgHd26nSRVhqoStWOQ4L3E2cifyoz9XVSu8TqIu9kXE17DADiHqHQDPxIxv+87qY0b6gXCkAgwLXzPUsppP/5fvo0/RcLQGgHx65IIg1Ul6y1Y6L+ywUgloJJpIFa6W66Wg8AK6QBIz4NvIZ/5wFglbmw9DRQF6H7Tzb8iwfgNQ1UldjlH6v/dMO/fABuaUAJDQJx+Scd/lMAwNvr1DUSK4H5LvpxMN4DwLppwEymDZWAlrb887D8rfUAsH4auMZKQNTDgUpnzQGWfyIAhCAwDl2d6UpO8adak+bsP0kAQiXgp75VuYg8oGuVtd10hOWfDgBxJjB1bSagFAi1f9MNo3EeALbPA7/dxb6T/WXWhOjvrPcAsHkecJPfF4Fgfxlq/6vxHgB2yQOxFNgLgR+v9g/mxQPAjqXAjMDm42Edcv/Nfuc9AOxZCswI5GpTBOIN4/UB7U8QgBsCpmvUP+/oXif26zxvgv3j4exPEoCIwHUaurbaJBNUIfarpjPTYK33ACBDL8aPcybIL3W1aubXKsT+bgiVv/MeACQ1hTZmgrbOlF4pFei6UplquxD73aEq/2MAcMsEU9+1KjJQLe9+kcXMPxw09h8AgJAJYhgYQioIXpX1Yi8U6ipGftW0IfOHxe+8BwDBYcCNkwtxQGd5DAT6efOrIn91P2R+64+t7AC/g40V4RAYaFSWF2Vd6x+Pr/zitvR7N43D4d0/CAAzA26YYk0Ys0GmLlV9XyzQVVj4pYorP7o/TqM31nkPACnlAnsNxpkYCeo8C7FgxqDSn4Ggo/PB+iJ4n+vgfReKvlD1nWHtHw2AuSYMgWCcYkkQY4GOGORFACHE9jrC8Kb69rksovOBlWb23gSAhrMs/SMCMAcCa4wPq3gazIxB2zRVHl2OMLwpfsxUNL5po/X9/Deu9lzmHxKAXxRYN0RTx6GPIHTtDMO7uuh7WPN9jBhx3Z/Q+wMDcEsIMRhEDoYpmhxsftftczA+Om/sOb0/OgC/ooFzNphsos/vev3szuv8aQBAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAPgKAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAEAAgAEAAgAAAAQACAAQACAAQACAAQACAAAABAAIABAAIABAAIABAAIAAAAEAAgAEAAgAAICvAAAQAKCz6j9r5bu6z+vB6gAAAABJRU5ErkJggg==",
};
