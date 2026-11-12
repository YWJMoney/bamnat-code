// [ADMUX-01]~[ADMUX-08] The developer app itself, run in a pretend browser (happy-dom) against the worker on
// an in-memory D1: it signs in, opens every screen, and the things that live only in the page work —
// filters in the address, drafts that outlive a reload, the bulk bar, undo, the palette, signing in again.
// `npm run test:ui` (needs `npm ci` once for happy-dom).
import { test } from "node:test";
import assert from "node:assert/strict";
import { Window } from "happy-dom";
import { readFileSync } from "node:fs";
import worker from "../../src/index.js";
import { adminFile } from "../../src/admin-app.js";
import { SERVER_VERSION } from "../../src/version.js";
import { server, signIn, report, setup, device, from, ORIGIN, PASSWORD } from "../helpers.mjs";
import { adminLib } from "../../src/admin-lib.js";

const APP_JS = await adminFile("/admin/app.js").text();
const HTML = await adminFile("/admin/").text();
const BODY = HTML.slice(HTML.indexOf("<body>") + 6, HTML.indexOf("</body>"));

/**
 * 2.0.0 [RVA-57] 화면 묶음 파일(v/<묶음>.<해시>.js): happy-dom은 파일을 받지 않으니, 앱이 붙이는 <script src>를 그 창의 fetch(워커)로 받아
 * 실행하고 load를 알려요. 진짜 브라우저처럼 그 화면을 열 때 받아요.
 */
function loadScripts(window) {
  const head = window.document.head;
  const append = head.appendChild.bind(head);
  head.appendChild = (node) => {
    if (node.tagName !== "SCRIPT" || !node.src) return append(node);
    window.fetch(node.src).then(async (r) => {
      if (!r.ok) throw new Error(String(r.status));
      window.eval(await r.text());
      node.onload?.(new window.Event("load"));
    }).catch(() => node.onerror?.(new window.Event("error")));
    return node;
  };
}

const open = [];
test.after(async () => {
  for (const w of open) await w.happyDOM.close();
});

/** A browser tab on the developer app: its fetch goes to the worker, with the sign-in cookie kept like a browser does. */
async function tab(env, { hash = "", storage = null, cookie = null, width = 1280 } = {}) {
  const window = new Window({ url: `${ORIGIN}/admin/${hash}`, width, height: 900,
    settings: { disableJavaScriptFileLoading: true, disableCSSFileLoading: true, disableIframePageLoading: true } });
  open.push(window);
  const jar = { cookie };
  const errors = [];
  // 2.0.0 [RVA-11] 검토 I24b-01: 서버가 5xx로 답한 요청도 모아 둬요(연기 시험이 0인지 봐요).
  const broken = [];
  window.addEventListener("error", (e) => errors.push(e.error ?? e.message));
  window.fetch = async (input, init = {}) => {
    const url = new URL(typeof input === "string" ? input : input.url, ORIGIN);
    const headers = new Headers(init.headers ?? {});
    if (jar.cookie) headers.set("cookie", jar.cookie);
    headers.set("cf-connecting-ip", "203.0.113.9");
    if (init.method && init.method !== "GET") headers.set("origin", ORIGIN);
    const res = await worker.fetch(new Request(url, { method: init.method ?? "GET", headers, body: init.body }), env);
    if (res.status >= 500) broken.push(`${init.method ?? "GET"} ${url.pathname} ${res.status}`);
    const set = res.headers.get("set-cookie");
    if (set) jar.cookie = /Max-Age=0/.test(set) ? null : set.split(";")[0];
    const body = res.status === 304 ? null : await res.text();
    return new window.Response(body, { status: res.status, headers: Object.fromEntries(res.headers) });
  };
  if (storage) for (const [k, v] of Object.entries(storage)) window.localStorage.setItem(k, v);
  window.structuredClone ??= structuredClone;
  window.document.body.innerHTML = BODY;
  loadScripts(window);
  window.eval(APP_JS);
  const t = {
    window, document: window.document, jar, errors, broken,
    $: (s) => window.document.querySelector(s),
    $$: (s) => [...window.document.querySelectorAll(s)],
    text: (s) => window.document.querySelector(s)?.textContent.replace(/\s+/g, " ").trim() ?? "",
    /** Lets the app's requests and repaints finish. */
    settle: async (n = 12) => {
      // Not waitUntilComplete: the app's polling timer never completes.
      for (let i = 0; i < n; i++) await new Promise((r) => setTimeout(r, 5));
    },
    click: async (s) => {
      const el = typeof s === "string" ? window.document.querySelector(s) : s;
      assert.ok(el, `nothing to click at ${s}`);
      el.click();
      await t.settle();
    },
    /**
     * 1.28 통합 [FLAG-39] Waits until the screen's requests are answered (#main aria-busy, the progress bar) and it painted.
     * A fixed 60 ms was too short while Gradle builds ran beside the tests: the screen still showed its skeleton and
     * "[ADMUX-01] … shows something" failed now and then (chores, types). Capped, so a screen that never settles fails
     * on its own assertion instead of hanging.
     */
    idle: async (cap = 5000) => {
      const busy = () => window.document.querySelector('#main[aria-busy="true"], #progress.on');
      const end = Date.now() + cap;
      await t.settle(2);
      while (busy() && Date.now() < end) await new Promise((r) => setTimeout(r, 5));
      await t.settle(2);
    },
    go: async (h) => {
      window.location.hash = h;
      await t.settle();
      await t.idle();
    },
    type: async (s, value) => {
      const el = window.document.querySelector(s);
      el.value = value;
      el.dispatchEvent(new window.Event("input", { bubbles: true }));
      await t.settle();
    },
    key: async (key, extra = {}) => {
      window.document.activeElement?.blur?.();
      window.document.dispatchEvent(new window.KeyboardEvent("keydown", { key, bubbles: true, ...extra }));
      await t.settle();
    },
  };
  await t.settle();
  return t;
}

async function world(extra = {}) {
  const s = server(extra);
  const { call, env } = s;
  await call("POST", "/api/reports", report(1, { company: "가나다화학" }), from(1));
  for (const [n, kind, text] of [[11, "bug", "알람 소리가 안 나요"], [12, "idea", "달력이 일요일부터면 좋겠어요"], [13, "question", "급여는 어디서 봐요"]]) {
    await call("POST", "/api/inquiries", { v: 1, device: `test-device-000000${n}`, kind, text, info: { app: "1.22.0 (33)" } }, from(n));
  }
  return { ...s, env };
}

async function signedIn(env, opts = {}) {
  const t = await tab(env, opts);
  assert.ok(t.$('form[data-form="login"]'), "the sign-in screen first");
  await t.click('[data-act="login-mode"][data-key="owner"]');
  t.$("#password").value = PASSWORD;
  t.$('form[data-form="login"]').requestSubmit();
  await t.settle(30);
  assert.ok(t.$("#main"), "signed in");
  return t;
}

test("[ADMUX-01] every screen opens without an error and without a style attribute", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  // AN 1.28: the six hubs' tabs, and the 1.27 addresses that now lead to them (inbox, ops, settings).
  // 2.0.0 [RVA-11] 검토 ADM-09·I24b-01: 손으로 적은 목록 대신 모든 화면(L.TABS)을 돌고, 서버 5xx도 0이어야 해요(연기 시험).
  for (const s of [...Object.keys(adminLib().TABS), "inbox", "ops", "settings", "more"]) {
    await t.go(`#/${s}`);
    const main = t.$("#main");
    assert.ok(main.textContent.trim().length > 10, `${s} shows something`);
    assert.ok(!t.$("#main .failed"), `${s} loaded`);
    assert.equal(t.$$("#main [style]").length, 0, `${s}: no style attribute (CSP)`);
  }
  assert.deepEqual(t.errors, []);
  assert.deepEqual(t.broken, [], "no screen asked something the server answered with 5xx");
});

test("[ADMUX-02] filters live in the address and come back from it", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/inquiries");
  await t.click('[data-act="inquiry-who"][data-key="free"]');
  assert.equal(t.window.location.hash, "#/inquiries?who=free");
  await t.type("#search", "알람");
  await new Promise((r) => setTimeout(r, 300));
  await t.settle();
  assert.equal(t.window.location.hash, `#/inquiries?who=free&q=${encodeURIComponent("알람")}`);
  assert.equal(t.$$("#main .rows .row").length, 1);
  // A link copied with its filters opens the same list.
  const u = await tab(env, { hash: "#/inquiries?status=open&sort=oldest", cookie: t.jar.cookie });
  assert.equal(u.$("#inq-sort option[selected]").value, "oldest");
  // 놓친 것 is only ever a waiting question.
  await u.click('[data-act="inquiry-status"][data-key="closed"]');
  await u.click('[data-act="inquiry-who"][data-key="late"]');
  assert.match(u.window.location.hash, /who=late/);
  assert.doesNotMatch(u.window.location.hash, /status=closed/);
});

test("[ADMUX-01] a typed answer survives a repaint, a reload and a lost sign-in", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/inquiries");
  await t.click("#main .rows .row");
  await t.type("#reply", "확인하고 있어요");
  await t.click('[data-act="refresh"]');
  assert.equal(t.$("#reply").value, "확인하고 있어요", "kept across a repaint");
  // 2.0.0 [RVA-06] 초안은 관리자·화면마다 따로, 7일까지(열쇠 bamnat-admin-draft2:<관리자>:<화면>:<칸>).
  const store = Object.fromEntries(Object.entries(t.window.localStorage).filter(([k]) => k.startsWith("bamnat-admin-draft2:")));
  const again = await tab(env, { hash: t.window.location.hash, storage: store, cookie: t.jar.cookie });
  assert.equal(again.$("#reply").value, "확인하고 있어요", "kept across a reload");
  // Signed out under way: a box over the screen, and the answer is still there after.
  again.jar.cookie = null;
  await again.click('[data-act="refresh"]');
  assert.ok(again.$('dialog form[data-form="relogin"]'));
  again.$("#re-pw").value = PASSWORD;
  again.$('form[data-form="relogin"]').requestSubmit();
  await again.settle(30);
  assert.equal(again.$("dialog").open, false);
  assert.equal(again.$("#reply").value, "확인하고 있어요");
  // Sent: the box and the kept draft are empty.
  again.$('form[data-form="reply"]').requestSubmit(again.$('form[data-form="reply"] .btn.primary'));
  await again.settle(30);
  assert.equal(again.$("#reply")?.value ?? "", "");
  assert.ok(!Object.keys(again.window.localStorage).some((k) => k.startsWith("bamnat-admin-draft2:")));
});

test("[ADMIN-16] picked questions are taken at once, and a 닫기 can be taken back", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/inquiries");
  await t.click('[data-act="inquiry-pick"]');
  // Each pick repaints the list, so the next box is looked up again.
  for (let i = 0; i < 3; i++) {
    const box = t.$$("[data-pick-inquiry]")[i];
    box.checked = true;
    box.dispatchEvent(new t.window.Event("change", { bubbles: true }));
    await t.settle();
  }
  assert.match(t.text(".bulkbar b"), /3개/);
  await t.click('[data-act="inquiry-bulk"][data-key="assign"]');
  assert.match(t.text("#toast"), /3개를 처리했어요/);
  assert.equal(t.$$("#main .pill.info").filter((p) => p.textContent === "내가 맡음").length, 3);
  // Closed, then 되돌리기.
  await t.click("#main .rows .row");
  await t.click('[data-act="inquiry-state"][data-status="closed"]');
  assert.ok(t.$("#toast [data-undo]"));
  await t.click("#toast [data-undo]");
  await t.settle(20);
  const id = t.window.location.hash.split("/")[2] ?? "";
  const { call } = { call: (m, p) => worker.fetch(new Request(ORIGIN + p, { method: m, headers: { cookie: t.jar.cookie } }), env).then((r) => r.json()) };
  const list = await call("GET", "/api/admin/inquiries?status=open");
  assert.equal(list.inquiries.length, 3, `the closed one (${id}) is open again`);
});

test("[ADMUX-04] keys and the palette go places", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.key("g");
  await t.key("i");
  assert.match(t.window.location.hash, /^#\/inquiries/);
  await t.key("j");
  assert.equal(t.$$("#main .rows .row.selected").length, 1);
  await t.key("Enter");
  assert.match(t.window.location.hash, /^#\/inquiries\/[A-Za-z0-9]{22}$/);
  await t.key("k", { ctrlKey: true });
  assert.ok(t.$("dialog.palette[open]"));
  await t.type("#pal", "활동");
  assert.match(t.text("#pal-list"), /활동 기록/);
  t.$("#pal").dispatchEvent(new t.window.KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
  await t.settle();
  assert.equal(t.window.location.hash, "#/audit");
});

test("[ADMUX-03] this device's choices: colours, text size, bottom tabs", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  // AN 1.28 [ADMUX-32] 관리 › 내 계정·이 기기.
  await t.go("#/account");
  const pick = async (id, value) => {
    const el = t.$(id);
    el.value = value;
    el.dispatchEvent(new t.window.Event("change", { bubbles: true }));
    await t.settle();
  };
  await pick("#pref-theme", "dark");
  await pick("#pref-size", "large");
  assert.equal(t.document.documentElement.dataset.theme, "dark");
  assert.equal(t.document.documentElement.dataset.size, "large");
  const prefs = JSON.parse(t.window.localStorage.getItem("bamnat-admin-prefs"));
  assert.equal(prefs.theme, "dark");
  const flip = async (key, on) => {
    const box = t.$(`[data-change="pref-tab"][value="${key}"]`);
    box.checked = on;
    box.dispatchEvent(new t.window.Event("change", { bubbles: true }));
    await t.settle();
  };
  // 1.38 [ADM3] 허브가 다섯이라 모두 아래 탭에 있어요: 고를 것이 없고 그렇다고 알려 줘요.
  assert.equal(typeof flip, "function");
  assert.ok(!t.$('[data-change="pref-tab"]'), "nothing to pick");
  assert.match(t.text("#main"), /허브 5개가 모두 아래 탭에 있어요/);
  assert.deepEqual(t.$$(".tabs [data-nav]").map((a) => a.dataset.nav), ["today", "ask", "release", "insight", "manage"]);
});

test("[ADMIN-15] the join page asks for its invite with the token in the body", async () => {
  const s = await world();
  const owner = await signIn(s.call);
  const inv = await s.call("POST", "/api/admin/invites", { email: "new@example.com", perms: { review: "view" } }, owner);
  const token = inv.data.link.split("#")[1];
  const window = new Window({ url: `${ORIGIN}/admin/join#${token}` });
  open.push(window);
  const asked = [];
  window.fetch = async (input, init = {}) => {
    asked.push(`${init.method ?? "GET"} ${new URL(input, ORIGIN).pathname}${new URL(input, ORIGIN).search}`);
    const res = await worker.fetch(new Request(new URL(input, ORIGIN), { method: init.method ?? "GET", headers: { ...init.headers, origin: ORIGIN }, body: init.body }), s.env);
    return new window.Response(await res.text(), { status: res.status, headers: Object.fromEntries(res.headers) });
  };
  window.structuredClone ??= structuredClone;
  window.document.body.innerHTML = BODY;
  window.eval(APP_JS);
  for (let i = 0; i < 20; i++) await new Promise((r) => setTimeout(r, 5));
  assert.ok(asked.includes("POST /api/admin/join/info"), asked.join(", "));
  assert.ok(!asked.some((a) => a.includes(token)), "the token is in no address");
  assert.match(window.document.querySelector("main").textContent, /new@example\.com/);
});

test("[ADMUX-07] a notice shows as the phone will show it, and lists go out as CSV", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/notices");
  await t.click('[data-act="notice-new"]');
  assert.match(t.text("#nt-preview"), /제목/);
  await t.type("#nt-title", "점검 안내");
  await t.type("#nt-body", "새벽 2시에 <잠시> 멈춰요");
  assert.equal(t.text("#nt-preview b"), "점검 안내");
  assert.equal(t.$("#nt-preview span").innerHTML, "새벽 2시에 &lt;잠시&gt; 멈춰요", "escaped");
  assert.match(t.text('form[data-form="notice"] details.fold-field .help'), /1\.22\.0\(번호 33\)/, "the newest app a question came from");
  t.$("dialog").close();
  await t.settle();
  await t.go("#/inquiries");
  const csv = t.$('[data-act="csv"][data-kind="inquiries"]');
  assert.ok(csv && !csv.disabled, "three questions to take out");
  let saved = null;
  t.window.URL.createObjectURL = (blob) => { saved = blob; return "blob:x"; };
  t.window.URL.revokeObjectURL = () => {};
  await t.click(csv);
  const text = await saved.text();
  assert.match(text, /^﻿받은 때,종류,주제,상태,맡은 사람.*,본문\r\n/);
  assert.equal(text.trim().split("\r\n").length, 4);
  // [ADMUX-11] The question goes out too, so a file can be worked through, but with no mail or phone in it.
  assert.match(text, /알람 소리가 안 나요/, "the question is in the file");
  assert.ok(!/[\w.+-]+@[\w-]+\.[\w.]+/.test(text) && !/01\d-?\d{3,4}-?\d{4}/.test(text), "no mail or phone in the file");
  await t.go("#/rewards");
  assert.ok(!t.$("#main").textContent.includes("드림"), "지급함, not 드림");
});

test("[ADMUX-04] on 검토, a asks before 채택 and x opens 거절", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/review");
  assert.ok(t.$('#main [data-act="adopt"]'), "a report waits");
  await t.key("a");
  assert.match(t.text("dialog"), /채택할까요/);
  t.$("dialog").close();
  await t.settle();
  await t.key("x");
  assert.ok(t.$('dialog form[data-form="reject"]'), "the 거절 form");
});

test("[ADMUX-04] the palette offers what was opened last, and home the saved views", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/inquiries");
  await t.click("#main .rows a.row");
  await t.go("#/settings");
  await t.key("k", { ctrlKey: true });
  const first = t.$("#pal-list li");
  assert.match(first.textContent, /급여는 어디서 봐요\s*최근/);
  t.$("dialog").close();
  await t.settle();
  await t.go("#/inquiries?status=answered");
  await t.key("k", { ctrlKey: true });
  await t.type("#pal", "지금 보기 저장");
  t.$("#pal").dispatchEvent(new t.window.KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
  await t.settle();
  await t.go("#/home");
  const chip = t.$('nav[aria-label="저장한 보기"] a');
  assert.ok(chip, "the saved view is on home");
  assert.equal(chip.getAttribute("href"), "#/inquiries?status=answered");
});

// ---- 1.25 묶음 AA: 받은함, 여러 판, 지표 기간, 광장 일괄 (끝에서 끝: 화면의 단추 → 서버가 가진 것) ----

/** Remote files and Play are not reachable from a test; the page must still draw. */
/** 2.0.0 [RVA-48] 검토 ADM-23: 장부 줄은 펼칠 때 상세를 그려요. 시험도 사람처럼 줄을 펼친 뒤 그 안의 단추를 눌러요. */
function openLines(t, sel = "#main .rows.ledger > li.lg-line > details:not([open])") {
  for (const d of t.$$(sel)) {
    d.open = true;
    d.dispatchEvent(new t.window.Event("toggle"));
  }
}

function offline(t) {
  const real = globalThis.fetch;
  globalThis.fetch = async () => new Response("", { status: 404 });
  t.after(() => { globalThis.fetch = real; });
}

async function asOwner(env, t) {
  const s = await signedIn(env);
  const call = (m, p, body) => worker.fetch(new Request(ORIGIN + p, { method: m, headers: { cookie: s.jar.cookie, origin: ORIGIN, "x-admin": "1", "content-type": "application/json" },
    body: body ? JSON.stringify(body) : undefined }), env).then((r) => r.json());
  return { ...s, api: call };
}

test("[ADMUX-17][ADMUX-33] 할 일 (받은함 on 오늘) lists what waits, 맡기 takes the question, 내일 볼게요 hides it on this device only", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  // AN 1.28 [ADMUX-35] The 1.27 address lands on 오늘 › 할 일.
  await u.go("#/inbox");
  assert.equal(u.window.location.hash, "#/home");
  assert.ok(u.$$("#main .rows.inbox li").length >= 4, "questions and the report wait");
  assert.match(u.text("#main .rows.inbox"), /아무도 안 맡음/);
  await u.click('#main [data-act="inbox-assign"]');
  assert.match(u.text("#toast"), /내가 맡았어요/);
  const mine = (await u.api("GET", "/api/admin/inquiries?who=mine")).inquiries;
  assert.equal(mine.length, 1, "the server now holds the question as mine");
  const before = u.$$("#main .rows.inbox li").length;
  await u.click('#main .rows.inbox [data-act="inbox-snooze"]');
  assert.equal(u.$$("#main .rows.inbox li").length, before - 1);
  assert.match(u.window.localStorage.getItem("bamnat-admin-snooze"), /:/);
  await u.click("#toast [data-undo]");
  assert.equal(u.$$("#main .rows.inbox li").length, before);
  // One list: no second 받은함 to go to.
  assert.ok(!u.$('#main a[href="#/inbox"]'));
  assert.deepEqual(u.errors, []);
});

test("[ADM3-37] 오늘 › 할 일: 같은 말로 읽히는 문의는 한 줄, 그 아래 '비슷한 문의 N건 함께 보기'에 나머지", async (t) => {
  offline(t);
  const { env, call } = await world();
  for (const [n, text] of [[21, "알람 소리가 안 나요 진동만 와요"], [22, "알람이 소리 없이 안 나요"]]) {
    await call("POST", "/api/inquiries", { v: 1, device: `test-device-000000${n}`, kind: "bug", text, info: { app: "1.38.0 (52)" } }, from(n));
  }
  const u = await asOwner(env, t);
  await u.go("#/home");
  const bundle = u.$("#main .rows.inbox details.bundle");
  assert.ok(bundle, "the alike ones fold under one line");
  assert.match(bundle.querySelector("summary").textContent, /비슷한 문의 2건/);
  assert.equal(bundle.querySelectorAll(".bundle-list p").length, 2);
  assert.match(bundle.closest("li").querySelector(".kind").textContent, /3건/);
  assert.ok(![...u.$$("#main .rows.inbox > li .row-main a")].some((a) => /진동만 와요/.test(a.textContent)), "not a row of its own too");
  // 1.39 [ADM3-39] 발굴: one answer to the whole bundle, by choice (off by default).
  const form = bundle.closest("li").querySelector('form[data-form="quick-reply"]');
  assert.match(form.textContent, /비슷한 문의 2건에도 같은 답 보내기/);
  assert.equal(form.elements.all.checked, false);
  form.elements.text.value = "알람 소리 설정을 다음 판에서 고쳐요";
  form.elements.all.checked = true;
  form.requestSubmit(form.querySelector('[data-close="1"]'));
  await u.settle(60);
  assert.match(u.text("#toast"), /3건에 답하고 닫았어요/);
  const owner = await signIn(call);
  const list = (await call("GET", "/api/admin/inquiries?status=all", undefined, owner)).data.inquiries;
  const alarms = list.filter((q) => /알람/.test(JSON.stringify(q)));
  assert.equal(alarms.length, 3);
  for (const q of alarms) assert.equal(q.status, "closed");
  assert.ok(!u.$("#main .rows.inbox details.bundle"), "the bundle left the list");
  assert.deepEqual(u.errors, []);
});

test("[ADM3-40] 큐 모드: 비슷한 문의가 큐에 남아 있으면 같은 답을 함께 보내고 큐에서 빼요", async (t) => {
  offline(t);
  const { env, call } = await world();
  for (const [n, text] of [[21, "알람 소리가 안 나요 진동만 와요"], [22, "알람이 소리 없이 안 나요"]]) {
    await call("POST", "/api/inquiries", { v: 1, device: `test-device-000000${n}`, kind: "bug", text, info: { app: "1.38.0 (52)" } }, from(n));
  }
  const u = await asOwner(env, t);
  await u.go("#/inquiries");
  await u.click('[data-act="queue-start"]');
  await u.settle(40);
  let form = u.$('#main form[data-form="reply"]');
  for (let i = 0; i < 6 && form && !form.elements.alike; i++) {
    await u.click('[data-act="queue-skip"]');
    await u.settle(40);
    form = u.$('#main form[data-form="reply"]');
  }
  assert.ok(form?.elements.alike, "an alarm question in the queue offers its alike ones");
  assert.match(form.textContent, /큐에 남은 비슷한 문의 2건에도 같은 답/);
  const total = Number(u.text(".queue-bar b").match(/\/(\d+)/)[1]);
  form.elements.text.value = "알람 소리 설정을 다음 판에서 고쳐요";
  form.elements.alike.checked = true;
  form.requestSubmit(form.querySelector('[data-close="1"]'));
  await u.settle(60);
  assert.match(u.text("#toast"), /3건에 답하고 닫았어요/);
  const owner = await signIn(call);
  const list = (await call("GET", "/api/admin/inquiries?status=all", undefined, owner)).data.inquiries;
  for (const q of list.filter((x) => /알람/.test(JSON.stringify(x)))) assert.equal(q.status, "closed");
  const bar = u.text(".queue-bar b");
  if (bar) assert.equal(Number(bar.match(/\/(\d+)/)[1]), total - 2, "the two left the queue");
  assert.deepEqual(u.errors, []);
});

test("[FAM-04] 가족 프로 이용권: a phone not seen for 60 days says so, a fresh one is 프로 사용 중", async (t) => {
  offline(t);
  const s = server();
  const { call, env, DB } = s;
  const owner = await signIn(call);
  for (const [n, name] of [[31, "엄마"], [32, "옛 휴대폰"]]) {
    const { code } = (await call("POST", "/api/admin/family/code", { name }, owner)).data;
    await call("POST", "/api/admin-link", { code }, { "x-device": device(n), ...from(n) });
  }
  const old = Date.now() - 90 * 864e5;
  DB.db.prepare("UPDATE admin_devices SET created_at = ?, seen_at = ? WHERE admin_name = ?").run(old, old, "옛 휴대폰");
  const u = await asOwner(env, t);
  await u.go("#/account");
  const rows = [...u.$$("#main .rows li")].filter((li) => /엄마|옛 휴대폰/.test(li.textContent));
  assert.equal(rows.length, 2);
  assert.match(rows.find((li) => /엄마/.test(li.textContent)).textContent, /프로 사용 중/);
  const idle = rows.find((li) => /옛 휴대폰/.test(li.textContent)).textContent;
  assert.match(idle, /60일 넘게 안 보임/);
  assert.match(idle, /휴대폰을 바꿨다면 끊고 새 코드를/);
  assert.deepEqual(u.errors, []);
});

test("[ADM3-42] 로그인 잠금 풀기: the owner's 내 계정 shows a locked PC and unlocks it from the phone", async (t) => {
  offline(t);
  const { call, env } = server();
  const pc = { "x-admin": "1", origin: ORIGIN, ...from(7) };
  for (let i = 0; i < 5; i++) await call("POST", "/api/admin/login", { password: "not-the-password" }, pc);
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD }, pc)).status, 429);
  const u = await asOwner(env, t);
  await u.go("#/account");
  assert.match(u.text("#main [data-lock-line]"), /잠긴 곳 1곳/);
  await u.click('#main [data-act="login-unlock"]');
  assert.match(u.text("dialog"), /로그인 잠금을 풀까요/);
  u.$('dialog form[data-form="confirm"]').requestSubmit();
  await u.settle(30);
  assert.match(u.text("#toast"), /잠긴 곳 1곳을 풀었어요/);
  assert.match(u.text("#main [data-lock-line]"), /지금 잠긴 로그인이 없어요/);
  assert.ok(!u.$('#main [data-act="login-unlock"]'));
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD }, pc)).status, 200);
  assert.deepEqual(u.errors, []);
});

test("[ADM3-43] 서버 상태: company counts and versions, an old share server is 판 다름 (not red), a backup without R2 is 꺼짐", async (t) => {
  const real = globalThis.fetch;
  const robots = (u) => String(u).endsWith("/robots.txt");
  globalThis.fetch = async (u) => new Response(robots(u) ? "User-agent: *" : "", { status: robots(u) ? 200 : 404 });
  t.after(() => { globalThis.fetch = real; });
  const { call, env, DB } = server({ SHARE_BASE_URL: "https://share.example", SHARE_ADMIN_TOKEN: "x".repeat(32) });
  await call("POST", "/api/reports", report(1), from(1));
  DB.db.prepare("INSERT INTO job_runs (step, ok_at, ms, runs, last_at, ver) VALUES ('backup', ?, 3, 5, ?, '1.38.0')").run(Date.now() - 600e3, Date.now() - 600e3);
  const u = await asOwner(env, t);
  await u.go("#/server");
  await u.settle(30);
  // 2.0.0 [RVQ-14] 판은 맨 앞 '지금 판'으로 옮겼어요(검토 U21-02).
  assert.match(u.text("#main .server-sum"), new RegExp(`^지금 판 ${SERVER_VERSION.replaceAll(".", "\\.")} · .* · 회사 1 · 쓰는 규칙 \\d+ · 관리자 앱 빌드`));
  const share = [...u.$$("#main .tile")].find((x) => /공유 서버/.test(x.textContent));
  assert.match(share.textContent, /살아 있음 · 판 다름/);
  assert.ok(!share.classList.contains("hot"));
  const backup = [...u.$$("#main .rows li")].find((li) => li.querySelector("b")?.textContent === "하루 백업");
  assert.match(backup.textContent, /꺼짐/);
  assert.match(backup.textContent, /R2 묶음\(BACKUP\)이 없어 건너뛰어요/);
  assert.deepEqual(u.errors, []);
});

test("[ADMUX-18] 여러 판: 다시 대표로 asks first, then the earlier version is the rule again", async (t) => {
  offline(t);
  const s = server();
  const { call, env, DB } = s;
  const owner = await signIn(call);
  const v1 = await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/reports", report(2), from(2));
  await call("POST", "/api/reports", report(6), from(6));
  const twelve = setup();
  twelve.pay.rules.extra[3].percent = 0.12;
  const v2 = await call("POST", "/api/reports", report(3, { setup: twelve }), from(3));
  await call("POST", `/api/admin/groups/${v2.data.company.id}/${v2.data.setup}/adopt`, {}, owner);
  const u = await asOwner(env, t);
  await u.go("#/versions?show=history");
  const btn = u.$('#main [data-act="version-promote"]');
  assert.ok(btn, "the earlier version can come back");
  await u.click(btn);
  assert.match(u.text("dialog"), /v1을 다시 대표로 할까요/);
  u.$('dialog form[data-form="confirm"]').requestSubmit();
  await u.settle(30);
  assert.match(u.text("#toast"), /다시 대표로 했어요/);
  const live = DB.db.prepare("SELECT version, grp, source FROM rules WHERE superseded = 0").get();
  assert.deepEqual([live.version, live.grp, live.source], [3, v1.data.setup, "promote"]);
  assert.deepEqual(u.errors, []);
});

test("[ADMUX-13] 지표: two dates ask the server for that period and the numbers follow", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/metrics");
  // 1.38 [ADM3] 지표는 '숫자' 허브의 첫 탭이라 위에 '지금 기다리는 것' 줄이 올 수 있어요.
  const meta = () => u.$$("#main .meta").map((e) => e.textContent).join(" ");
  assert.match(meta(), /\(28일\)/);
  for (const sec of ["활성", "광장", "문의", "오류", "원격 갱신"]) assert.match(u.text("#main"), new RegExp(sec), `${sec} section`);
  // 2.0.0 [RVA-24] 날짜 두 칸은 '날짜로'를 누를 때만(기간 막대 한 줄).
  assert.equal(u.$("#mt-from"), null, "the dates fold behind 날짜로");
  await u.click('#main [data-act="metric-dates"]');
  const to = u.$("#mt-to").value;
  const from = new Date(Date.parse(to) - 2 * 864e5).toISOString().slice(0, 10);
  const el = u.$("#mt-from");
  el.value = from;
  el.dispatchEvent(new u.window.Event("change", { bubbles: true }));
  await u.settle(30);
  assert.match(meta(), /\(3일\)/);
  assert.match(u.window.location.hash, new RegExp(`from=${from}`));
  assert.ok(u.$('#main [data-act="metric-clear"]'));
  await u.click('#main [data-act="metric-clear"]');
  await u.settle(30);
  assert.match(meta(), /\(28일\)/);
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

test("[ADMUX-19] 토론: pick two posts, 가리기 asks once, both are hidden on the server", async (t) => {
  offline(t);
  const s = server({ plaza: true });
  const { call, env, DB } = s;
  const a = await call("POST", "/api/reports", report(1), from(1));
  for (const [n, body] of [[2, "첫 글이에요"], [3, "둘째 글이에요"]]) {
    await call("POST", "/api/posts", { device: device(n), company: a.data.company.id, setup: a.data.setup, body }, from(n));
  }
  const u = await asOwner(env, t);
  await u.go("#/community?show=all");
  await u.click('#main [data-act="post-pick"]');
  await u.click('#main [data-act="post-pick-all"]');
  assert.equal(u.$$("#main [data-pick-post]:checked").length, 2);
  await u.click('#main [data-act="post-bulk"][data-key="hide"]');
  assert.match(u.text("dialog"), /글 2개를 가릴까요/);
  u.$('dialog form[data-form="confirm"]').requestSubmit();
  await u.settle(30);
  assert.match(u.text("#toast"), /2개 처리했어요/);
  assert.deepEqual(DB.db.prepare("SELECT status FROM posts").all().map((p) => p.status), ["hidden", "hidden"]);
  assert.deepEqual(u.errors, []);
});

test("PC 확인 보안 11차: 글 가리기는 이유를 고르기 전에는 누를 수 없고, 고른 이유로 가려요", async (t) => {
  offline(t);
  const s = server({ plaza: true });
  const { call, env, DB } = s;
  const a = await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/posts", { device: device(2), company: a.data.company.id, setup: a.data.setup, body: "가릴 글이에요" }, from(2));
  const u = await asOwner(env, t);
  await u.go("#/community?show=all");
  await u.click('#main [data-act="post-hide"]');
  const form = u.$('dialog form[data-form="hide-post"]');
  const radios = [...form.querySelectorAll('input[name="reason"]')];
  assert.ok(radios.length >= 2);
  assert.equal(radios.filter((r) => r.checked).length, 0, "미리 골라 두지 않아요");
  assert.equal(form.querySelector('button[type="submit"]').disabled, true);
  assert.match(u.text("dialog"), /이유를 고르면 가리기를 누를 수 있어요/);
  const pick = radios[1];
  pick.checked = true;
  pick.dispatchEvent(new u.window.Event("change", { bubbles: true }));
  assert.equal(form.querySelector('button[type="submit"]').disabled, false);
  assert.doesNotMatch(u.text("dialog"), /이유를 고르면/);
  form.requestSubmit();
  await u.settle(30);
  const row = DB.db.prepare("SELECT status, hide_reason FROM posts").get();
  assert.equal(row.status, "hidden");
  assert.equal(row.hide_reason, pick.value, "고른 이유로 가려요");
  assert.deepEqual(u.errors, []);
});

// AE 1.26 시작
test("[INVITE-06] 보상 shows 친구 초대: the month given and the summary", async () => {
  const { call, env, DB } = await world();
  const mark = (n) => String(n).repeat(64);
  const made = await call("POST", "/api/invites/code", { phone: mark(1) }, { "x-device": device(1) });
  await call("POST", "/api/invites/use", { code: made.data.code, source: "referrer", phone: mark(2) }, { "x-device": device(2), ...from(2) });
  // [INVITE-08] One friend is enough here, and its first day was long ago by the server's clock.
  DB.db.prepare("INSERT INTO settings (key, value) VALUES ('invite_friends', '1')").run();
  DB.db.prepare("UPDATE invite_uses SET first_day = '2000-01-01'").run();
  assert.equal((await call("POST", "/api/invites/seen", {}, { "x-device": device(2) })).data.status, "credited");
  // AG 1.27 [INVITE-10] Friends of one code crowding from one network are noted for a look, not refused.
  DB.db.prepare("INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, 'invite_burst', ?, '', '', '')").run(Date.now(), made.data.code);
  const t = await signedIn(env);
  await t.go("#/rewards");
  await t.click('[data-act="reward-tab"][data-key="paid"]');
  await t.settle(30);
  const main = t.$("#main").textContent;
  assert.match(main, /친구 초대/);
  // 2.0.0 [RVA-26] 통합 2차 PC Q31: 지급 전이면 '보상 받음'이 아니라 '조건 채움(지급 전)', 0인 칸('상한 넘음 0명')은 없어요.
  assert.match(main, /조건 채움 1명\(지급 전 1명\)/);
  assert.doesNotMatch(main, /보상 받음|상한 넘음|(^|\D)0명/);
  // 2.0.0 [RVD-21] 처리방침 3.13이 일정에 없으면 모든 보상 합친 1년 12개월은 아직이에요(약관 1.3·처리방침 3.12는 상한 없음).
  // [RVA-26] 친구가 1명이면 '각자'가 없어요.
  assert.match(main, /친구 1명이 7일 이상 쓸 때마다 프로 1개월 · 상한 없음 · 모든 보상 합쳐 1년 12개월은 처리방침 3\.13 시행일부터/);
  assert.doesNotMatch(main, /각자/);
  // [RVD-21] 접힌 한 줄도 같아요: 3.13 전에는 '한 분에 1년 12개월까지'라고 하지 않아요.
  assert.equal(t.text('#main details[data-key="reward-how"] > summary'), "확인 뒤 지급 · 1년 12개월 상한은 처리방침 3.13 시행일부터");
  assert.match(t.text('#main details[data-key="reward-how"] .hint'), /그 전에는 친구 초대 설정의 상한만 봐요/);
  assert.ok(main.includes(made.data.code), "the code most friends came by");
  assert.match(main, /최근 30일 몰림 1번/);
  // [RVA-26] 지급하면 같은 친구가 '지급'으로 옮겨요.
  DB.db.prepare("UPDATE rewards SET status = 'paid', paid_at = ? WHERE rule_id = 'invite'").run(Date.now());
  await t.click('[data-act="refresh"]');
  await t.settle(30);
  assert.match(t.$("#main").textContent, /조건 채움 1명\(지급 1명\)/);
  assert.deepEqual(t.errors, []);
});

test("[INVITE-08] 관리 › 보상 › 친구 초대 (1.27 설정): the developer sets how many friends, days, months and the cap", async () => {
  const { env, DB } = await world();
  const t = await signedIn(env);
  await t.go("#/rewards");
  await t.settle(30);
  const card = () => [...t.document.querySelectorAll("#main section.card")].find((c) => c.querySelector("h2")?.textContent === "친구 초대");
  assert.ok(card(), "the 친구 초대 card");
  assert.match(card().textContent, /친구 5명이 각자 7일 이상 쓰면 프로 1개월, 5명 더 채울 때마다 또 1개월 · 상한 없음 · 모든 보상 합쳐 1년 12개월은 처리방침 3\.13 시행일부터/);
  assert.match(card().textContent, /초대만의 1년 상한\s+−\s+없음/);
  const step = async (key, d) => {
    t.$(`[data-act="friend-step"][data-key="${key}"][data-d="${d}"]`).click();
    await t.settle();
  };
  await step("friends", -1);
  await step("friends", -1);
  await step("days", 1);
  await step("capMonths", 1);
  assert.match(card().textContent, /친구 3명이 각자 8일 이상 쓰면 프로 1개월, 3명 더 채울 때마다 또 1개월 · 1년에 1개월까지/);
  card().querySelector('[data-act="save-friend"]').click();
  await t.settle(30);
  const rows = Object.fromEntries(DB.db.prepare("SELECT key, value FROM settings WHERE key LIKE 'invite_%'").all().map((r) => [r.key, r.value]));
  assert.deepEqual(rows, { invite_friends: "3", invite_days: "8", invite_months: "1", invite_cap_months: "1" });
  assert.match(t.text("#toast"), /친구 초대 규칙을 저장했어요/);
  assert.deepEqual(t.errors, []);
});
// AE 1.26 끝

test("[PLAZA-13] 회사·규칙: how many phones joined a company, and 안정 on a rule that settled (결정 D79)", async (t) => {
  offline(t);
  const s = server();
  const { call, env, DB } = s;
  const a = await call("POST", "/api/reports", report(1, { company: "참여화학", verified: true }), from(1));
  await call("POST", "/api/reports", report(2, { company: "참여화학" }), from(2));
  for (const n of [3, 4, 5]) await call("POST", "/api/uses", { device: device(n), company: a.data.company.id, setup: a.data.setup }, from(n));
  DB.db.prepare("UPDATE reports SET created_at = created_at - ?").run(31 * 24 * 60 * 60 * 1000);
  DB.db.prepare("UPDATE rules SET adopted_at = adopted_at - ?").run(31 * 24 * 60 * 60 * 1000);
  const u = await asOwner(env, t);
  await u.go("#/companies");
  assert.match(u.text("#main"), /참여화학.*참여 5/);
  await u.go(`#/companies/${a.data.company.id}`);
  await u.settle(20);
  assert.match(u.text("#main"), /참여한 휴대폰 5대/);
  assert.ok(u.$$("#main .pill.ok").some((p) => p.textContent === "안정"), "a settled rule says so");
  assert.deepEqual(u.errors, []);
});

test("[PLAZA-15] 회사·규칙: 맞아요·달라요 by where, 대표에서 빠짐, and the rule on the settings page (1.27 AH)", async (t) => {
  offline(t);
  const s = server();
  const { call, env } = s;
  const a = await call("POST", "/api/reports", report(1, { company: "의견화학" }), from(1));
  await call("POST", "/api/reports", report(2, { company: "의견화학" }), from(2));
  const c = a.data.company.id;
  // A third 맞아요 adopts it (three points).
  await call("POST", "/api/votes", { device: device(6), company: c, setup: a.data.setup, ok: true }, from(6));
  for (const [n, why] of [[3, "order"], [4, "order"], [5, "pay"], [7, "time"]]) {
    await call("POST", "/api/votes", { device: device(n), company: c, setup: a.data.setup, ok: false, why }, from(n));
  }
  const u = await asOwner(env, t);
  await u.go(`#/companies/${c}`);
  await u.settle(20);
  assert.match(u.text("#main"), /맞았어요 3 · 달라요 4 \(순서 2 · 시간 1 · 급여 항목 1\)/);
  assert.ok(u.$$("#main .pill.warn").some((p) => p.textContent === "대표에서 빠짐"));
  await u.go("#/adopt");
  await u.settle(20);
  assert.match(u.text("#main"), /5표 이상이고 달라요가 40%를 넘으면/);
  assert.deepEqual(u.errors, []);
});

// ---- AN 1.28 시작: 여섯 허브, 옛 주소, 메뉴 숫자, 설정 나누기, 할 일 하나로 (끝에서 끝: 화면 → 서버) ----

const SUPPORT = { review: "none", inquiries: "edit", dev: "edit", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };

/** An approved admin with [perms], signed in on a tab of their own. */
async function asMember(s, perms = SUPPORT, hash = "", email = "minji@example.com", ip = 40) {
  const APP = { "x-admin": "1", origin: ORIGIN };
  const owner = await signIn(s.call);
  const inv = await s.call("POST", "/api/admin/invites", { email, perms }, owner);
  const token = inv.data.link.split("#")[1];
  await s.call("POST", "/api/admin/join", { token, name: "민지", password: "minji-answers-2026" }, { ...APP, ...from(ip) });
  const id = (await s.call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await s.call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await s.call("POST", "/api/admin/login", { email, password: "minji-answers-2026" }, { ...APP, ...from(ip) });
  const t = await tab(s.env, { hash, cookie: res.headers.get("set-cookie").split(";")[0] });
  await t.settle(20);
  return t;
}

const sideHubs = (t) => t.$$(".side .nav > li").map((li) => li.dataset.hub);
const bottom = (t) => t.$$(".tabs [data-nav]").map((a) => a.dataset.nav);

test("[ADMUX-30] six hubs for the owner; an admin sees only the hubs and tabs they may use", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  // 1.38 [ADM3] 다섯 허브(오늘·사람과 글·출시·숫자·관리)가 모두 아래 탭에 들어가요.
  assert.deepEqual(sideHubs(t), ["today", "ask", "release", "insight", "manage"]);
  assert.deepEqual(bottom(t), ["today", "ask", "release", "insight", "manage"]);
  // A hub's tabs sit under the title, and in the sidebar under the open hub.
  await t.go("#/review");
  assert.deepEqual(t.$$("#hubtabs a").map((a) => a.dataset.tab), ["inquiries", "dev", "review", "community", "recipes", "reviews", "requests", "versions", "companies", "shares", "growth", "templates"]);
  assert.equal(t.$("#hubtabs [aria-current]").dataset.tab, "review");
  assert.ok(t.$('.side li[data-hub="ask"].open .subnav a[data-tab="companies"]'));
  assert.equal(t.$('.tabs [aria-current="page"]').dataset.nav, "ask");
  await t.go("#/home");
  assert.deepEqual(t.$$("#hubtabs a").map((a) => a.dataset.tab), ["home", "desk", "decide", "flow", "loop", "cockpit", "incident"]);
  await t.go("#/metrics");
  assert.equal(t.$('.tabs [aria-current="page"]').dataset.nav, "insight");
  // 민지 answers questions only: 오늘, 문의 (without 답 목표, which is 설정's) and 관리 (내 계정 only).
  const u = await asMember(s);
  assert.deepEqual(sideHubs(u), ["today", "ask", "manage"]);
  assert.deepEqual(bottom(u), ["today", "ask", "manage"], "every hub fits: no 더 보기");
  await u.go("#/inquiries");
  assert.deepEqual(u.$$("#hubtabs a").map((a) => a.dataset.tab), ["inquiries", "dev", "reviews", "growth", "templates"]);
  await u.go("#/account");
  assert.ok(u.$("#hubtabs").hidden, "관리 has one tab for 민지");
  // A tab 민지 cannot use goes to 오늘.
  await u.go("#/review");
  assert.equal(u.window.location.hash, "#/home");
  await u.go("#/adopt");
  assert.equal(u.window.location.hash, "#/home");
  // 준호 looks after 광장 only (검토, 토론, 회사 보기): 문의 is gone and 광장 shows just those tabs.
  const plaza = { ...SUPPORT, inquiries: "none", dev: "none", review: "edit", community: "edit", companies: "view" };
  const v = await asMember(s, plaza, "", "junho@example.com", 41);
  assert.deepEqual(sideHubs(v), ["today", "ask", "release", "manage"]);
  await v.go("#/review");
  assert.deepEqual(v.$$("#hubtabs a").map((a) => a.dataset.tab), ["review", "community", "recipes", "versions", "companies"]);
  assert.equal(Number(v.text('.tabs [data-badge="hub:ask"]')), 1, "the one report");
  await v.go("#/inquiries");
  assert.equal(v.window.location.hash, "#/home");
  assert.deepEqual([...t.errors, ...u.errors, ...v.errors], []);
});

test("[ADMUX-40] 출시 › 기능 관리 is AM's screen: a 기능 스위치 is a flag, forced off with a reason, and 분석 › 기능별 효과 lists every flag", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  await t.go("#/flags");
  assert.ok(!t.$("#main .soon"), "no placeholder left");
  assert.ok(t.$(`#main a.row[href="#/flags/plaza.write"]`), "the 1.27 switch 광장 글쓰기 is a flag row");
  await t.go("#/flags/plaza.write");
  await t.settle(20);
  await t.click('#main [data-act="flag-force"][data-force="off"]');
  t.$("#ff-why").value = "글 신고가 몰려요";
  t.$('dialog form[data-form="flag-force"]').requestSubmit();
  await t.settle(30);
  const owner = await signIn(s.call);
  const n = (await s.call("GET", "/api/admin/notices", undefined, owner)).data;
  assert.equal(n.switches.find((w) => w.key === "PLAZA_WRITE").off, true);
  await t.go("#/types");
  assert.ok(t.$('#main [data-act="type-hide"], #main [data-act="type-show"]'), "근무 유형 screen");
  await t.go("#/effects");
  await t.settle(20);
  assert.ok(t.$(`#main a.row[href="#/flags/plaza.write"]`), "효과 lists flags, linked to their page");
  assert.match(t.text("#main"), /문제 신고 \d+ → \d+/);
  assert.deepEqual(t.errors, []);
});

test("[ADMUX-35] the 1.27 addresses lead to the new tabs and keep their filters; #/settings says where things went", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/inbox?view=mine");
  assert.equal(t.window.location.hash, "#/home?view=mine");
  assert.equal(t.$('[data-act="inbox-view"][aria-pressed="true"]').dataset.key, "mine");
  await t.go("#/ops?month=2026-09");
  assert.equal(t.window.location.hash, "#/chores?month=2026-09");
  await t.go("#/ops");
  assert.equal(t.window.location.hash, "#/flags");
  await t.go("#/settings");
  const links = t.$$("#main .rows a.row").map((a) => a.getAttribute("href"));
  for (const h of ["#/adopt", "#/rewards", "#/sla", "#/account", "#/server", "#/flags"]) assert.ok(links.includes(h), h);
  assert.equal(t.$('.side [aria-current="page"]')?.dataset.nav, "manage");
  // An old address opened fresh, before signing in, too.
  const u = await tab(env, { hash: "#/inbox?view=today", cookie: t.jar.cookie });
  assert.equal(u.window.location.hash, "#/home?view=today");
  assert.deepEqual([...t.errors, ...u.errors], []);
});

test("[ADMUX-34] menu numbers: each thing counted once, 더 보기 only its own hubs", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  const badge = (k) => Number(t.text(`.tabs [data-badge="hub:${k}"]`) || 0);
  const side = (k) => Number(t.text(`.side [data-badge="hub:${k}"]`) || 0);
  // 1.38 [ADM3] 문의와 광장이 '사람과 글' 하나: 문의 셋과 제보 하나.
  assert.equal(badge("ask"), 4, "three questions and one report");
  // 오늘's number is how many things 주의 필요 lists (1.38 [ADM3-04]: the test server has no 알림 키·Play 계정, so 'setting missing' is one).
  assert.equal(side("today"), t.$$("#main .alert-list li").length, "오늘 counts only its 주의 필요");
  assert.match(t.text("#main .alert-list"), /꼭 필요한 설정/);
  assert.equal(badge("manage"), side("manage"));
  // 오늘's 할 일 says what waits in each hub, and opens it filtered.
  const strip = t.$('#waiting a[href^="#/inquiries"]');
  assert.match(strip.textContent, /사람과 글\s*4/);
  assert.equal(strip.getAttribute("href"), "#/inquiries?sort=sla");
  // 2.0.0 [RVA-24] A list screen has no line of its own above the list (HQ-03): the hub tab carries the number.
  await t.go("#/inquiries");
  assert.equal(t.$("#waiting"), null, "no strip above 문의");
  assert.equal(Number(t.text('[data-badge="tab:inquiries"]') || 0), 3, "the 문의 tab says 3");
  // An answer makes the number go down without waiting for the next poll.
  const id = (await s.call("GET", "/api/admin/inquiries", undefined, await signIn(s.call))).data.inquiries[0].id;
  await t.go(`#/inquiries/${id}`);
  t.$("#reply").value = "고쳤어요. 업데이트해 주세요";
  t.$('form[data-form="reply"]').requestSubmit(t.$('form[data-form="reply"] [data-close="1"]'));
  await t.settle(40);
  assert.equal(badge("ask"), 3);
  assert.deepEqual(t.errors, []);
});

test("[ADMUX-32] 설정 split: 채택 규칙 on 광장 and 답 목표 on 문의 save to the server; 서버 상태 on 관리", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  const owner = await signIn(s.call);
  const basics = async () => (await s.call("GET", "/api/admin/basics", undefined, owner)).data;
  const before = await basics();
  await t.go("#/adopt");
  await t.click('[data-act="step"][data-key="adoptPoints"][data-d="1"]');
  await t.click('[data-act="save-settings"]');
  assert.match(t.text("#toast"), /저장했어요/);
  assert.equal((await basics()).settings.adoptPoints, before.settings.adoptPoints + 1);
  await t.go("#/sla");
  await t.click('[data-act="sla-step"][data-key="slaBug"][data-d="1"]');
  await t.click('[data-act="save-sla"]');
  assert.equal((await basics()).sla.slaBug, before.sla.slaBug + 1);
  await t.go("#/server");
  assert.match(t.text("#main"), /서버 상태/);
  // 이 기기 is on 내 계정: colours, text size, bottom tabs, install, shortcuts.
  await t.go("#/account");
  for (const w of [/화면 색/, /글자 크기/, /아래 탭/, /단축키/]) assert.match(t.text("#main"), w);
  assert.deepEqual(t.errors, []);
});

test("[ADMUX-33] 오늘's 할 일: the filters live in the address, and 바로 답하기 answers on the server", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  await t.click('[data-act="inbox-view"][data-key="mine"]');
  assert.equal(t.window.location.hash, "#/home?view=mine");
  await t.click('[data-act="inbox-view"][data-key="all"]');
  const form = t.$('#main form[data-form="quick-reply"]');
  assert.ok(form, "a question can be answered from the list");
  const id = form.dataset.id;
  form.elements.text.value = "다음 판에서 고쳐요";
  form.requestSubmit(form.querySelector('[data-close="1"]'));
  await t.settle(40);
  assert.match(t.text("#toast"), /답하고 닫았어요/);
  const owner = await signIn(s.call);
  const q = (await s.call("GET", `/api/admin/inquiries/${id}`, undefined, owner)).data;
  assert.equal(q.status ?? q.inquiry?.status, "closed");
  assert.match(JSON.stringify(q), /다음 판에서 고쳐요/);
  assert.ok(!t.$(`#main form[data-form="quick-reply"][data-id="${id}"]`), "it left the list");
  assert.deepEqual(t.errors, []);
});

test("[ADMUX-36] 1~5 open the hubs, [ and ] the tabs, and the palette knows the old names", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.key("2");
  assert.equal(t.window.location.hash, "#/inquiries");
  await t.key("]");
  assert.equal(t.window.location.hash, "#/dev", "개발사항 sits next to 문의");
  await t.key("[");
  await t.key("[");
  assert.equal(t.window.location.hash, "#/templates", "round to the hub's last tab");
  await t.key("3");
  assert.equal(t.window.location.hash, "#/launch", "출시 opens on 출시 점검표");
  // A hub opens on the tab it was left on.
  await t.key("2");
  assert.equal(t.window.location.hash, "#/templates");
  await t.key("g");
  await t.key("b");
  assert.equal(t.window.location.hash, "#/home");
  // 1.38 [DESK-03] '운영'은 이제 운영 장부가 먼저예요(이름이 맞아서). 옛 운영 메뉴는 '기능 스위치'로, 할 일함·정리함은 장부로.
  for (const [word, to] of [["받은함", "#/home"], ["운영", "#/desk"], ["기능 스위치", "#/flags"], ["친구 초대", "#/rewards"], ["할 일함", "#/desk"], ["정리함", "#/desk"]]) {
    await t.key("k", { ctrlKey: true });
    await t.type("#pal", word);
    t.$("#pal").dispatchEvent(new t.window.KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await t.settle();
    assert.equal(t.window.location.hash, to, word);
  }
  await t.key("?");
  assert.match(t.text("dialog"), /1~5/);
  assert.deepEqual(t.errors, []);
});

test("[ADMUX-37] the screen does not wait for the menu numbers, and polling asks only for them", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  const asked = [];
  const real = t.window.fetch;
  let broken = true;
  t.window.fetch = async (input, init) => {
    const path = new URL(typeof input === "string" ? input : input.url, ORIGIN).pathname;
    asked.push(path);
    if (broken && path === "/api/admin/badges") return new t.window.Response("{}", { status: 500 });
    return real(input, init);
  };
  await t.go("#/inquiries");
  assert.equal(t.$$("#main .rows .row").length, 3, "the list came though /badges did not");
  assert.ok(!asked.includes("/api/admin/overview"), "/overview is not asked any more");
  broken = false;
  asked.length = 0;
  await t.go("#/companies");
  t.window.document.dispatchEvent(new t.window.Event("visibilitychange"));
  await t.settle();
  assert.ok(!asked.includes("/api/admin/today"), "오늘's data only on 오늘");
  assert.deepEqual(t.errors, []);
});

test("[ADMUX-35] bottom tabs and saved views kept on this device from 1.27 move to the hubs once", async () => {
  const { env } = await world();
  const old = { theme: "dark", tabs: ["inbox", "review", "ops", "audit"], views: [{ name: "내 것", hash: "#/inbox?view=mine" }, { name: "답함", hash: "#/inquiries?status=answered" }] };
  const signed = await signedIn(env);
  const t = await tab(env, { cookie: signed.jar.cookie, storage: { "bamnat-admin-prefs": JSON.stringify(old) } });
  await t.settle(20);
  const now = JSON.parse(t.window.localStorage.getItem("bamnat-admin-prefs"));
  assert.equal(now.v, 2);
  assert.equal(now.theme, "dark");
  assert.deepEqual(now.tabs, ["ask", "release", "manage"]);
  assert.deepEqual(now.views.map((v) => v.hash), ["#/home?view=mine", "#/inquiries?status=answered"]);
  assert.deepEqual(bottom(t), ["today", "ask", "release", "insight", "manage"]);
  assert.deepEqual(t.errors, []);
});
// AN 1.28 끝
// ---- AM 1.28 기능 관리 [ADMUX-21]~[ADMUX-29] ----

const MANAGER = { review: "edit", inquiries: "edit", dev: "edit", devStatus: "edit", companies: "edit", rewards: "edit", settings: "edit", community: "edit",
  takedown: "edit", ops: "edit", metrics: "view", shares: "edit", audit: "view", flags: "edit", flagOff: "none" };

/** An admin the owner invited (운영 관리자, no 기능 강제로 끄기) and approved; returns the id and the sign-in cookie. */
async function manager(call, owner) {
  const inv = await call("POST", "/api/admin/invites", { email: "minji@example.com", perms: MANAGER }, owner);
  await call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "민지", password: "minji-flags-2026" }, { "x-admin": "1", origin: ORIGIN, ...from(40) });
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === "minji@example.com").id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email: "minji@example.com", password: "minji-flags-2026" }, { "x-admin": "1", origin: ORIGIN, ...from(40) });
  return { id, cookie: res.headers.get("set-cookie").split(";")[0] };
}

test("[ADMUX-21][ADMUX-25][ADMUX-26] 기능 관리, its timeline, one flag and 근무 유형 open without an error or a style attribute", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  for (const h of ["#/flags", "#/flags?view=timeline", "#/flags/billing", "#/flags/type.shift", "#/flags/plaza.write", "#/types"]) {
    await u.go(h);
    await u.settle(20);
    assert.ok(u.$("#main").textContent.trim().length > 10, `${h} shows something`);
    assert.ok(!u.$("#main .failed"), `${h} loaded`);
    assert.equal(u.$$("#main [style]").length, 0, `${h}: no style attribute (CSP)`);
  }
  await u.go("#/flags/billing");
  await u.settle(20);
  assert.match(u.text("#title"), /결제\(프로 구독\)/);
  assert.match(u.text("#main"), /켜지는 조건/);
  assert.ok(u.$$('#main [data-act="flag-check"][data-done="1"]').length >= 2, "확인했어요 for Play's two facts");
  assert.ok(u.$('#main a[href="https://ywjmoney.github.io/-/privacy/"]'), "the basis links the policy");
  assert.ok(u.$('#main form[data-form="flag-thanks"]'), "감사 보상 lives here now");
  // The simulator answers for a pretend phone.
  assert.match(u.text("#flag-sim-out"), /켜짐|꺼짐/);
  // 설정 no longer has the 정식 출시 switch; it points here.
  await u.go("#/settings");
  await u.settle(20);
  assert.ok(!u.$('[data-act="launch-switch"]'));
  assert.ok(u.$('#main a[href="#/flags/billing"]'));
  assert.deepEqual(u.errors, []);
});

test("[ADMUX-21] the list filters by state and words, in the address", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.api("POST", "/api/admin/flags/plaza.write/force", { force: "off", why: "도배" });
  await u.go("#/flags");
  await u.settle(20);
  const all = u.$$("#main .rows .row").length;
  assert.ok(all >= 16, "every flag");
  await u.click('#main [data-act="flag-show"][data-key="forced_off"]');
  assert.equal(u.window.location.hash, "#/flags?show=forced_off");
  assert.deepEqual(u.$$("#main .rows .row b").map((b) => b.textContent), ["광장 글쓰기(글·답글·제안·임금표)"]);
  assert.match(u.text("#main .rows"), /강제로 끔/);
  await u.click('#main [data-act="flag-show"][data-key="all"]');
  await u.type("#search", "billing");
  await new Promise((r) => setTimeout(r, 300));
  await u.settle();
  assert.equal(u.$$("#main .rows .row").length, 1);
  assert.match(u.window.location.hash, /q=billing/);
  // 대기 shows the waiting flags with their pill.
  await u.type("#search", "");
  await new Promise((r) => setTimeout(r, 300));
  await u.click('#main [data-act="flag-show"][data-key="waiting"]');
  assert.ok(u.$$("#main .rows .pill.warn").some((p) => /대기/.test(p.textContent)));
  assert.deepEqual(u.errors, []);
});

test("[ADMUX-23] forcing off asks why, the server keeps it with its history; conditions are checked as typed", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/flags/plaza.write");
  await u.settle(20);
  await u.click('#main [data-act="flag-force"][data-force="off"]');
  assert.ok(u.$('dialog form[data-form="flag-force"]'));
  assert.ok(u.$("#ff-review"), "다시 볼 날");
  u.$("#ff-why").value = "도배 글이 몰려요";
  u.$('dialog form[data-form="flag-force"]').requestSubmit();
  await u.settle(30);
  assert.match(u.text("#toast"), /강제로 껐어요/);
  const d = await u.api("GET", "/api/admin/flags/plaza.write");
  assert.deepEqual([d.override.force, d.override.why, d.history[0].action], ["off", "도배 글이 몰려요", "flag_force_off"]);
  assert.match(u.text("#main"), /강제로 끔/);
  assert.ok(u.$('#main [data-act="flag-clear"]'), "the way back");
  assert.match(u.text("#main .feed"), /기능 강제로 끔/);

  // The condition editor says the first mistake while typing, and saves what it shows.
  await u.go("#/flags/leave.hourly");
  await u.settle(20);
  await u.click('#main [data-act="flag-cond"]');
  await u.type("#flag-cond", "date >= 2027-01-01\nweather sunny");
  assert.ok(u.$('#flag-cond-out [role="alert"]'));
  assert.match(u.text("#flag-cond-out"), /2째 줄/);
  await u.type("#flag-cond", "date >= 2027-01-01\nrollout 10%");
  assert.equal(u.$$("#flag-cond-out li").length, 2);
  u.$("#fc-why").value = "먼저 10%";
  u.$('dialog form[data-form="flag-cond"]').requestSubmit();
  await u.settle(30);
  const leave = await u.api("GET", "/api/admin/flags/leave.hourly");
  assert.deepEqual(leave.override.conditions, [{ type: "date", from: "2027-01-01" }, { type: "rollout", percent: 10 }]);
  assert.deepEqual(u.errors, []);
});

test("[ADMUX-28] the owner gives 기능 강제로 끄기 as a yes/no; without it the buttons are locked with the reason", async (t) => {
  offline(t);
  const s = await world();
  const owner = await signIn(s.call);
  const m = await manager(s.call, owner);
  const u = await tab(s.env, { hash: "#/flags/billing", cookie: m.cookie });
  await u.settle(20);
  const button = (label) => u.$$("#main button").find((b) => b.textContent === label);
  assert.ok(button("강제로 끄기").disabled, "강제로 끄기 locked");
  assert.match(button("강제로 끄기").getAttribute("title"), /기능 강제로 끄기/);
  assert.ok(button("강제로 켜기").disabled);
  assert.ok(u.$$("#main button").filter((b) => b.textContent === "확인했어요").every((b) => b.disabled), "legal confirmations are the owner's");
  assert.match(u.text("#main"), /최고 관리자만/);
  // A flag that is not legal: conditions are open to 기능 관리 고치기.
  await u.go("#/flags/leave.hourly");
  await u.settle(20);
  assert.equal(u.$('#main [data-act="flag-cond"]')?.disabled, false);

  // The owner's 관리자 screen: the permission is a yes/no like 개발사항 처리.
  const o = await asOwner(s.env, t);
  await o.go("#/admins");
  await o.settle(20);
  await o.click(`#main [data-act="admin-edit"][data-id="${m.id}"]`);
  const yes = o.$('dialog [data-act="level"][data-area="flagOff"][data-level="edit"]');
  assert.ok(yes, "flagOff in the dialog");
  assert.equal(yes.textContent.trim(), "줌");
  assert.ok(!o.$('dialog [data-act="level"][data-area="flagOff"][data-level="view"]'), "no 보기 for a yes/no");
  await o.click(yes);
  o.$('dialog form[data-form="admin-perms"]').requestSubmit();
  await o.settle(30);
  const perms = (await o.api("GET", "/api/admin/admins")).admins.find((a) => a.id === m.id).perms;
  assert.equal(perms.flagOff, "edit");
  // Now the same admin may force off (not on).
  await u.go("#/flags/plaza.write");
  await u.settle(20);
  assert.equal(u.$('#main [data-act="flag-force"][data-force="off"]')?.disabled, false);
  assert.deepEqual([...u.errors, ...o.errors], []);
});

test("[ADMUX-27][FLAG-28] a legal flag on for under 24 hours sits at the top of 홈 with a one-tap force off", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  // 1.28 통합: forced on from the flag's page in the app, so the menu numbers (/badges) are asked again at once.
  await u.go("#/flags/billing");
  await u.settle(20);
  await u.click('#main [data-act="flag-force"][data-force="on"]');
  u.$("#ff-why").value = "Play 승인";
  u.$('dialog form[data-form="flag-force"]').requestSubmit();
  await u.settle(30);
  await u.go("#/home");
  await u.settle(20);
  const card = u.$("#main .card.flag-alert");
  assert.ok(card, "the warning card");
  // 1.39 PC 6차 J1: '이(가)' 대신 받침대로 '가'. 여럿 켜졌으면 한 장으로 접혀도 이 줄은 그 안에 있어요.
  assert.match(card.textContent, /결제\(프로 구독\)가 켜졌어요/);
  assert.ok(u.$("#main").firstElementChild.classList.contains("flag-alert"), "at the top");
  await u.click('#main .flag-alert [data-act="flag-force"]');
  assert.match(u.$("#ff-why").value, /홈에서 바로 껐어요/);
  u.$('dialog form[data-form="flag-force"]').requestSubmit();
  await u.settle(30);
  assert.equal((await u.api("GET", "/api/admin/flags/billing")).override.force, "off");
  await u.settle(20);
  // 1.38: 3.11 takes effect 2026-11-07, so on a real clock share.look may have its own card too; billing's is the one gone.
  assert.ok(![...u.$("#main").querySelectorAll(".card.flag-alert")].some((c) => /결제\(프로 구독\)/.test(c.textContent)), "gone once forced off");
  assert.deepEqual(u.errors, []);
});

test("[ADMUX-26] 근무 유형: hide with a reason, never the last one", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  for (const type of ["INVEST", "BUSINESS", "FIELD", "HOURLY"]) await u.api("POST", "/api/admin/types", { type, show: false, why: "다듬는 중" });
  await u.go("#/types");
  await u.settle(20);
  await u.click('#main [data-act="type-hide"][data-type="DAY"]');
  u.$("#th-why").value = "일근 화면 다듬는 중";
  u.$('dialog form[data-form="type-hide"]').requestSubmit();
  await u.settle(30);
  const types = (await u.api("GET", "/api/admin/types")).types;
  assert.deepEqual(types.filter((x) => x.on).map((x) => x.style), ["SHIFT"]);
  const last = u.$$("#main button").find((b) => b.textContent === "숨기기");
  assert.ok(last.disabled);
  assert.match(last.getAttribute("title"), /마지막 남은 유형/);
  await u.click('#main [data-act="type-show"][data-type="INVEST"]');
  assert.equal((await u.api("GET", "/api/admin/types")).types.find((x) => x.style === "INVEST").on, true);
  assert.deepEqual(u.errors, []);
});

// ---- 1.28 통합 [FLAG-36][FLAG-38] ----

test("[FLAG-36] the flag's page edits its app range: saved on the server, said in words, in the history, and taken off again", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/flags/share.create");
  assert.match(u.text("#main"), /모든 앱 판에서 조건대로 켜져요/);
  await u.click('#main [data-act="flag-range"]');
  assert.ok(u.$('dialog form[data-form="flag-range"]'));
  u.$("#fr-min").value = "40";
  u.$("#fr-why").value = "39의 링크 오류";
  u.$('dialog form[data-form="flag-range"]').requestSubmit();
  await u.settle(30);
  await u.idle();
  assert.match(u.text("#toast"), /앱 판 범위를 바꿨어요/);
  const d = await u.api("GET", "/api/admin/flags/share.create");
  assert.deepEqual([d.override.minApp, d.override.maxApp, d.history[0].action, d.history[0].why], [40, null, "flag_range", "39의 링크 오류"]);
  assert.match(u.text("#main"), /앱 번호 40 이상에서만/);
  assert.match(u.text("#main .feed"), /기능 앱 판 범위 바꿈/);
  assert.equal((await u.api("GET", "/api/config")).flags["share.create"].minApp, 40);
  // 범위 없애기: back to every version.
  await u.click('#main [data-act="flag-range"]');
  const form = u.$('dialog form[data-form="flag-range"]');
  form.requestSubmit(form.querySelector('[name="reset"]'));
  await u.settle(30);
  await u.idle();
  assert.equal((await u.api("GET", "/api/admin/flags/share.create")).override.minApp, null);
  assert.match(u.text("#main"), /모든 앱 판에서 조건대로 켜져요/);
  assert.deepEqual(u.errors, []);
});

// ---- AO 1.28 [PERF-18] 지표 › 앱 시작 시간 ----

test("[PERF-18] 지표: 앱 시작 시간 shows each version's middle value and 90%, and 데이터 부족 under 30 phones", async (t) => {
  offline(t);
  const { env, DB } = await world();
  const u0 = await asOwner(env, t);
  await u0.go("#/metrics");
  assert.match(u0.text("#startup-card"), /아직 받은 시작 시간이 없어요/);
  const day = new Date(Date.now() + 9 * 3600e3 - 86400e3).toISOString().slice(0, 10);
  const ins = DB.db.prepare("INSERT INTO usage_startup VALUES (?, ?, ?, ?, ?, ?)");
  ins.run(day, "1.28.0", "cold", "firstMonth", 1000, 20);
  ins.run(day, "1.28.0", "cold", "firstMonth", 1500, 12);
  ins.run(day, "1.28.0", "cold", "firstMonth", 4000, 3);
  ins.run(day, "1.28.0", "cold", "container", 100, 35);
  ins.run(day, "1.28.0", "warm", "firstMonth", 300, 30);
  ins.run(day, "1.27.0", "cold", "firstMonth", 3000, 4);
  const u = await asOwner(env, t);
  await u.go("#/metrics");
  await u.settle(30);
  const rows = u.$$("#startup-card li").map((li) => li.textContent.replace(/\s+/g, " ").trim());
  assert.equal(rows.length, 2);
  assert.match(rows[0], /^1\.28\.0/);
  assert.match(rows[0], /1초 · 90% 1\.5초/);
  assert.match(rows[0], /준비 기다림 35대 · 가운데 0\.1초/);
  assert.match(rows[0], /다시 열기 첫 달 30대 · 가운데 0\.3초/);
  assert.doesNotMatch(rows[0], /데이터 부족/);
  assert.match(rows[1], /^1\.27\.0.*3초 · 90% 3초.*데이터 부족/);
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

// ---- AT 1.29 문의 복사 [ADMCP-01]~[ADMCP-05], 큰 글자 [ADMCP-07] (화면의 단추 → 클립보드 → 서버가 가진 것) ----

/** The browser's clipboard: [ok] false is a browser that keeps it shut (and has no old copy command either). */
function clipboard(u, ok = true) {
  const got = { text: null };
  Object.defineProperty(u.window.navigator, "clipboard", { configurable: true,
    value: { writeText: async (s) => { if (!ok) throw new Error("NotAllowedError"); got.text = s; } } });
  u.document.execCommand = () => false;
  return got;
}

test("[ADMCP-01][ADMCP-03] 복사 on a question: the box shows Markdown in both forms, the clipboard gets it, the server marks 복사함", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  const got = clipboard(u);
  const bug = (await u.api("GET", "/api/admin/inquiries?status=open&kind=bug")).inquiries[0];
  await u.go(`#/inquiries/${bug.id}`);
  assert.match(u.text("#main"), /아직 아무도 복사하지 않았어요/);
  await u.click(`#main .card-head [data-act="inquiry-copy"]`);
  await u.settle(20);
  const box = u.$("dialog #copy-text");
  assert.ok(box, "the copy box");
  assert.ok(box.readOnly, "a box to select from, not to type in");
  assert.match(box.value, /^## \[문제 신고 · 알람\] 알람 소리가 안 나요/);
  assert.match(box.value, /- 앱 1\.22\.0 \(33\)/);
  assert.match(box.value, new RegExp(`/admin/#/inquiries/${bug.id}`));
  await u.click('dialog [data-act="copy-style"][data-key="short"]');
  assert.equal(u.$("dialog #copy-text").value.split("\n").length, 2, "짧게");
  await u.click('dialog [data-act="copy-do"]');
  await u.settle(20);
  assert.equal(got.text.split("\n").length, 2, "what the box showed is on the clipboard");
  assert.match(u.text("#toast"), /1건을 복사했어요/);
  assert.ok(!u.$("dialog[open]"), "the box closes");
  const after = await u.api("GET", `/api/admin/inquiries/${bug.id}`);
  assert.equal(after.copied.by, "최고 관리자");
  assert.match(u.text("#main"), /최고 관리자님이 .*에 복사했어요/);
  // The choice is remembered on this device: the next box opens in 짧게.
  assert.equal(JSON.parse(u.window.localStorage.getItem("bamnat-admin-prefs") ?? "{}").copy?.style, "short");
  await u.key("c");
  await u.settle(20);
  assert.equal(u.$('dialog [data-act="copy-style"][aria-pressed="true"]')?.dataset.key, "short", "c opens it again, as it was left");
  assert.deepEqual(u.errors, []);
});

test("[ADMCP-02][ADMCP-03] picked questions: Shift picks a range, 선택 n개 복사 makes one text, and 개발사항·닫기 go with it", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  const got = clipboard(u);
  await u.go("#/inquiries");
  assert.ok(u.$$('#main .row-tool[data-act="inquiry-copy"]').length >= 3, "복사 on every row");
  // The strip of problem reports nobody copied, and the home tile with the same number.
  assert.match(u.text("#main .fresh"), /복사 전 문제 신고 1건/);
  await u.click('[data-act="inquiry-pick"]');
  await u.click(u.$$("label[data-pick-row]")[0]);
  const last = u.$$("label[data-pick-row]")[2];
  last.dispatchEvent(new u.window.MouseEvent("click", { bubbles: true, cancelable: true, shiftKey: true }));
  await u.settle();
  assert.match(u.text(".bulkbar b"), /3개/, "Shift picks everything between");
  assert.match(u.text('.bulkbar [data-act="inquiry-copy-picked"]'), /선택 3개 복사/);
  await u.click('.bulkbar [data-act="inquiry-copy-picked"]');
  await u.settle(20);
  const text = u.$("dialog #copy-text").value;
  assert.match(text, /^# 문의 3건 \(/);
  assert.match(text, /문제 신고 1 · 질문 1 · 건의·제안 1/);
  assert.match(text, /## 문제 신고 \(1\)[\s\S]*## 질문 \(1\)[\s\S]*## 건의·제안 \(1\)/, "by kind, problem reports first");
  const dev = u.$('dialog [data-copy-after="dev"]');
  dev.checked = true;
  dev.dispatchEvent(new u.window.Event("change", { bubbles: true }));
  await u.settle();
  assert.match(u.$("dialog #copy-dev-title").value, / 외 2건$/);
  const close = u.$('dialog [data-copy-after="close"]');
  close.checked = true;
  close.dispatchEvent(new u.window.Event("change", { bubbles: true }));
  await u.settle();
  await u.click('dialog [data-act="copy-do"]');
  await u.settle(30);
  assert.match(got.text, /^# 문의 3건/);
  assert.match(u.text("#toast"), /3건을 복사했어요 · 개발사항 ".* 외 2건"을 만들었어요 · 닫았어요/);
  const all = (await u.api("GET", "/api/admin/inquiries?status=all")).inquiries;
  assert.ok(all.every((q) => q.copied && q.status === "closed" && q.dev === 1), "every one copied, closed and in the new 개발사항");
  const items = (await u.api("GET", "/api/admin/dev?status=all")).items;
  assert.equal(items.length, 1);
  assert.equal(items[0].links, 3);
  assert.deepEqual(u.errors, []);
});

test("[ADMCP-01] a browser that keeps the clipboard shut gets the text selected to copy by hand, then 복사했어요 marks it", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  clipboard(u, false);
  await u.go("#/inquiries");
  await u.click('#main .row-tool[data-act="inquiry-copy"]');
  await u.settle(20);
  await u.click('dialog [data-act="copy-do"]');
  assert.match(u.text("dialog #copy-fallback"), /바로 복사를 막았어요/);
  assert.ok(u.$("dialog #copy-text").value.length > 20, "the text stays there to select");
  assert.equal((await u.api("GET", "/api/admin/inquiries?status=all&copied=yes")).total, 0, "nothing marked before it is copied");
  await u.click('dialog [data-act="copy-done"]');
  await u.settle(20);
  assert.equal((await u.api("GET", "/api/admin/inquiries?status=all&copied=yes")).total, 1);
  assert.match(u.text("#main .rows"), /복사함/);
  assert.deepEqual(u.errors, []);
});

test("[ADMCP-02][ADMCP-04] keys pick and copy; 모아 보기 shows the reports nobody copied; the home tile counts them", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  clipboard(u);
  await u.go("#/home");
  const tile = u.$$("#main a.tile").find((a) => /복사 전 문제 신고/.test(a.textContent));
  assert.ok(tile, "오늘 has the tile");
  assert.match(tile.textContent, /1/);
  assert.equal(tile.getAttribute("href"), "#/inquiries?status=all&kind=bug&copied=no");
  await u.go("#/inquiries");
  await u.click('#main [data-act="fresh-bugs"]');
  await u.idle();
  assert.match(u.window.location.hash, /kind=bug/);
  assert.match(u.window.location.hash, /copied=no/);
  assert.equal(u.$$("#main .rows > li").length, 1);
  assert.ok(!u.$("#main .fresh"), "the strip hides while the list is just that");
  assert.match(u.text("#main .rows"), /새/, "a report from today stands out");
  await u.click('#main [data-act="inquiry-clear"]');
  await u.idle();
  await u.key("j");
  await u.key(" ");
  assert.match(u.text(".bulkbar b"), /1개/, "Space opens picking with the row in focus");
  await u.key("j");
  await u.key(" ", { shiftKey: true });
  assert.match(u.text(".bulkbar b"), /2개/);
  await u.key("c");
  await u.settle(20);
  assert.match(u.text("dialog h2"), /문의 2건 복사/);
  assert.deepEqual(u.errors, []);
});

test("[ADMCP-02] on a phone a long press on a row opens picking with it, and does not open the question", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/inquiries");
  const row = u.$("#main [data-row]");
  const id = row.dataset.row;
  row.dispatchEvent(new u.window.PointerEvent("pointerdown", { bubbles: true, pointerType: "touch" }));
  await new Promise((r) => setTimeout(r, 600));
  await u.settle();
  assert.match(u.text(".bulkbar b"), /1개/);
  assert.ok(u.$(`[data-pick-inquiry="${id}"]`).checked, "the pressed row is picked");
  assert.match(u.window.location.hash, /^#\/inquiries(\?|$)/, "still the list");
  // A short tap is still a tap: no picking from it.
  await u.click('[data-act="inquiry-pick"]');
  const other = u.$("#main [data-row]");
  other.dispatchEvent(new u.window.PointerEvent("pointerdown", { bubbles: true, pointerType: "touch" }));
  other.dispatchEvent(new u.window.PointerEvent("pointerup", { bubbles: true, pointerType: "touch" }));
  await new Promise((r) => setTimeout(r, 600));
  assert.ok(!u.$(".bulkbar"));
  assert.deepEqual(u.errors, []);
});

test("[ADMCP-06] a question shows the other questions from its phone, one tap to list them; 자주 쓰는 답 copies", async (t) => {
  offline(t);
  const { env, call } = await world();
  await call("POST", "/api/inquiries", { v: 1, device: "test-device-00000011", kind: "question", text: "위젯은 어디서 켜요", info: { app: "1.22.0 (33)" } }, from(21));
  const u = await asOwner(env, t);
  const got = clipboard(u);
  const bug = (await u.api("GET", "/api/admin/inquiries?status=open&kind=bug")).inquiries[0];
  await u.go(`#/inquiries/${bug.id}`);
  assert.match(u.text("#main"), /이 기기\(#[0-9a-f]{6}\)에서 온 다른 문의 1건/);
  await u.click('#main [data-act="inquiry-sender"]');
  await u.idle();
  assert.match(u.window.location.hash, new RegExp(`sender=${bug.sender}`));
  assert.equal(u.$$("#main .rows > li").length, 2);
  assert.ok(u.$$("#main .chips button").some((b) => /같은 기기 #/.test(b.textContent)));
  await u.go("#/templates");
  await u.click('#main [data-act="template-copy"]');
  assert.ok(got.text && got.text.length > 5, "a saved answer on the clipboard");
  assert.deepEqual(u.errors, []);
});

test("[ADMCP-07] 아주 크게 and 크게: the inquiries list, the bulk bar and the copy box still fit (wrapping rules, no fixed widths)", async (t) => {
  offline(t);
  const { env } = await world();
  for (const size of ["large", "xlarge"]) {
    const u = await asOwner(env, t);
    clipboard(u);
    await u.go("#/account");
    const pick = u.$("#pref-size");
    assert.ok([...pick.options].some((o) => o.value === "xlarge"), "아주 크게 is offered");
    pick.value = size;
    pick.dispatchEvent(new u.window.Event("change", { bubbles: true }));
    await u.settle();
    assert.equal(u.document.documentElement.dataset.size, size);
    await u.go("#/inquiries");
    await u.click('[data-act="inquiry-pick"]');
    await u.click('[data-act="inquiry-pick-all"]');
    await u.click('.bulkbar [data-act="inquiry-copy-picked"]');
    await u.settle(20);
    assert.ok(u.$("dialog #copy-text"));
    assert.equal(u.$$("#main [style], dialog [style]").length, 0, `${size}: no style attribute`);
    assert.deepEqual(u.errors, [], size);
  }
  // Everything is in rem, so the root size scales it; what holds buttons wraps instead of running off a phone's edge.
  const css = await adminFile("/admin/app.css").text();
  assert.match(css, /:root\[data-size="xlarge"\]\{font-size:125%\}/);
  for (const sel of [".bulkbar", ".fresh", ".chips", ".waiting"]) {
    const rule = css.match(new RegExp(`(^|\\})${sel.replace(".", "\\.")}\\{([^}]*)\\}`, "m"));
    assert.ok(rule, `${sel} has a rule`);
    assert.match(rule[2], /flex-wrap:wrap/, `${sel} wraps`);
  }
  assert.match(css, /\.rows>li\.has-tool>\.row\{flex:1;min-width:0\}/, "a row with a copy button can still shrink");
  assert.match(css, /\.row-main\{flex:1;min-width:0/);
  assert.match(css, /\.copy-text\{[^}]*overflow-wrap:anywhere/);
  // (Screen-width media queries aside; a photo thumbnail of 120px wraps with the others.)
  assert.ok(!/[{;](min-)?width:\s*([2-9]\d\d|\d{4,})px/.test(css), "no wide fixed widths in px");
});
// ---- AQ 1.29 [PHOTO-10] 지표 › 사진이 달라요 ----

test("[PHOTO-10] 지표: 사진이 달라요 shows the total, each screen with its reasons, and the versions", async (t) => {
  offline(t);
  const { env, DB } = await world();
  const u0 = await asOwner(env, t);
  await u0.go("#/metrics");
  assert.match(u0.text("#photo-wrong-card"), /이 기간에 받은 횟수가 없어요/);
  const day = new Date(Date.now() + 9 * 3600e3 - 86400e3).toISOString().slice(0, 10);
  const before = new Date(Date.now() + 9 * 3600e3 - 2 * 86400e3).toISOString().slice(0, 10);
  const ins = DB.db.prepare("INSERT INTO photo_wrong VALUES (?, ?, ?, ?, ?)");
  ins.run(day, "1.29.0", "payslip", "numbers", 5);
  ins.run(day, "1.29.0", "payslip", "missing", 2);
  ins.run(before, "1.29.0", "roster", "nothing", 1);
  ins.run(day, "1.28.0", "rules", "names", 1);
  const u = await asOwner(env, t);
  await u.go("#/metrics");
  await u.settle(30);
  const card = u.text("#photo-wrong-card").replace(/\s+/g, " ");
  assert.match(card, /^사진이 달라요 ?9번/);
  const rows = u.$$("#photo-wrong-card li").map((li) => li.textContent.replace(/\s+/g, " ").trim());
  assert.equal(rows.length, 3);
  assert.match(rows[0], /^명세서 사진 ?숫자가 틀려요 5 · 빠진 칸이 있어요 2 ?7번$/);
  assert.match(card, /1\.29\.0 8번 · 1\.28\.0 1번/);
  assert.match(card, /사진과 글은 받지 않아요/);
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

// ---- AX 1.30 시작: 오늘의 숫자·주의 필요, 검토 줄, 여러 판 표, 회사 표, 전체 검색, n 키, 여러 개 거절 ----

test("[ADMX-01][ADMX-11] 오늘: the four numbers with their week, 주의 필요 or a calm line, activity in words", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/home");
  const core = u.$$("#main .tiles.core a.tile.kpi").map((a) => a.querySelector(".k").textContent);
  assert.deepEqual(core, ["답 대기", "검토 대기", "복사 전 문제 신고", "정할 판"]);
  assert.ok(u.$("#main .tiles.core .spark"), "a week's line under a number");
  assert.ok(u.$("#main .alerts") || u.$("#main .calm"), "주의 필요 says something either way");
  // The other numbers fold away; the activity never shows a code.
  assert.ok(u.$('#main details.home-fold[data-key="home:tiles"] .tiles a.tile'));
  assert.ok(!/\b[a-z]+_[a-z_]+\b/.test(u.text("#main .feed")), "no activity codes");
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

test("[ADMX-01][ADMX-10] 주의 필요 lists what is late, links to it, and 오늘 in the menu counts it", async (t) => {
  offline(t);
  const s = server();
  const { call, env, DB } = s;
  await call("POST", "/api/reports", report(1, { company: "늦은화학" }), from(1));
  // A week old: past the week a report may wait.
  DB.db.prepare("UPDATE reports SET created_at = created_at - ?").run(8 * 864e5);
  const u = await asOwner(env, t);
  await u.go("#/home");
  const items = u.$$("#main .alerts .alert-list a");
  assert.ok(items.some((a) => /기한 넘긴 일 1건/.test(a.textContent) && a.getAttribute("href") === "#/home?view=today"), u.text("#main .alerts"));
  assert.equal(u.$('[data-badge="hub:today"]').textContent, String(items.length));
  assert.match(u.document.title, /^● /);
  assert.deepEqual(u.errors, []);
});

test("[ADMX-04] 검토: one line per report that opens in place, j and Enter, a and x on the line in focus, 모양이 다른 판 empty", async (t) => {
  offline(t);
  const { env, call } = await world();
  await call("POST", "/api/reports", report(2, { company: "라마바전자" }), from(2));
  const u = await asOwner(env, t);
  await u.go("#/review");
  const rows = u.$$('#main [data-review="group"]');
  assert.equal(rows.length, 2);
  assert.ok(rows.every((d) => !d.open), "lines start shut");
  await u.key("j");
  await u.key("j");
  assert.ok(rows[1].querySelector("summary").classList.contains("selected"));
  await u.key("Enter");
  assert.ok(rows[1].open, "Enter opens the line in place");
  await u.key("x");
  const form = u.$('dialog form[data-form="reject"]');
  assert.ok(form);
  assert.equal(form.dataset.company, rows[1].querySelector('[data-act="reject"]').dataset.company, "x is for the line j chose");
  u.$("dialog").close();
  await u.settle();
  await u.click('#main [data-act="review-filter"][data-key="shape"]');
  assert.match(u.window.location.hash, /show=shape/);
  assert.match(u.text("#main .empty"), /모양이 다른 판이 없어요/);
  assert.deepEqual(u.errors, []);
});

test("[ADMX-09] 검토: pick two, 거절 once with one reason, then 되돌리기 brings both back", async (t) => {
  offline(t);
  const { env, call, DB } = await world();
  await call("POST", "/api/reports", report(2, { company: "라마바전자" }), from(2));
  const u = await asOwner(env, t);
  await u.go("#/review");
  await u.click('#main [data-act="review-pick-mode"]');
  await u.click('#main [data-act="review-pick-all"]');
  assert.match(u.text("#main .bulkbar"), /2개 골랐어요/);
  await u.click('#main [data-act="review-bulk-reject"]');
  assert.match(u.text("dialog"), /2개를 거절할까요/);
  u.$('dialog form[data-form="reject-bulk"]').requestSubmit();
  await u.settle(40);
  assert.match(u.text("#toast"), /2개를 거절했어요/);
  const status = () => DB.db.prepare("SELECT status FROM reports ORDER BY company_id").all().map((r) => r.status);
  assert.deepEqual(status(), ["rejected", "rejected"]);
  await u.click("#toast [data-undo]");
  await u.settle(40);
  assert.deepEqual(status(), ["pending", "pending"]);
  assert.deepEqual(u.errors, []);
});

test("[ADMX-02][ADMX-03] 여러 판: a table with the candidates as columns, the different cell marked with the words that changed", async (t) => {
  offline(t);
  const s = server();
  const { call, env } = s;
  const owner = await signIn(call);
  await call("POST", "/api/reports", report(1), from(1));
  await call("POST", "/api/reports", report(2), from(2));
  await call("POST", "/api/reports", report(6), from(6));
  const twelve = setup();
  twelve.pay.rules.extra[3].percent = 0.12;
  const v2 = await call("POST", "/api/reports", report(3, { setup: twelve }), from(3));
  await call("POST", `/api/admin/groups/${v2.data.company.id}/${v2.data.setup}/adopt`, {}, owner);
  const u = await asOwner(env, t);
  await u.go("#/versions?show=history");
  const heads = u.$$("#main table.cmp thead th").map((th) => th.textContent.replace(/\s+/g, " ").trim());
  assert.match(heads[1], /쓰는 판 v2/);
  assert.match(heads[2], /이전 판 v1/);
  const differs = u.$$("#main table.cmp td.differs");
  assert.equal(differs.length, 1, "only 대근 differs");
  assert.equal(differs[0].querySelector("ins").textContent, "10%");
  assert.match(u.text("#main .vsum"), /이전 판 v1와 다른 점: 대근 10%→12%/);
  // The same rows are hidden until asked for.
  const before = u.$$("#main table.cmp tbody tr").length;
  await u.click('#main [data-act="cmp-all"]');
  assert.ok(u.$$("#main table.cmp tbody tr").length > before);
  assert.ok(u.$('#main table.cmp [data-act="version-promote"][data-version="1"]'));
  assert.deepEqual(u.errors, []);
});

test("[ADMX-06][ADMX-09] 회사: a table the server sorts by a heading, kept in the address; many hidden at once and back", async (t) => {
  offline(t);
  const s = server();
  const { call, env, DB } = s;
  await call("POST", "/api/reports", report(1, { company: "가나물산" }), from(1));
  for (const n of [2, 3]) await call("POST", "/api/reports", report(n, { company: "라마바전자", dept: `부서${n}` }), from(n));
  const u = await asOwner(env, t);
  await u.go("#/companies");
  assert.equal(u.$$("#main table.list tbody tr").length, 2);
  await u.click('#main [data-act="company-sort"][data-key="reports"]');
  await u.idle();
  assert.match(u.window.location.hash, /sort=reports/);
  assert.equal(u.$("#main table.list tbody tr td.name a").textContent, "라마바전자");
  await u.click('#main [data-act="company-pick-mode"]');
  // Each pick draws the list again, so each box is found anew.
  for (const id of u.$$('#main [data-act="company-pick"]').map((b) => b.dataset.id)) await u.click(`#main [data-act="company-pick"][data-id="${id}"]`);
  await u.click('#main [data-act="company-bulk"][data-key="hide"]');
  await u.settle(30);
  const hidden = () => DB.db.prepare("SELECT SUM(hidden) AS n FROM companies").get().n;
  assert.equal(hidden(), 2);
  await u.click("#toast [data-undo]");
  await u.settle(30);
  assert.equal(hidden(), 0);
  assert.deepEqual(u.errors, []);
});

test("[ADMX-08] 찾기·이동 asks the server too: companies under their heading, opened with Enter", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/home");
  await u.key("k", { ctrlKey: true });
  const box = u.$("#pal");
  box.value = "가나다";
  box.dispatchEvent(new u.window.Event("input", { bubbles: true }));
  await u.settle(80);
  assert.ok(u.$$("#pal-list li.group-head").some((li) => /^회사 1/.test(li.textContent)), u.text("#pal-list"));
  const at = u.$$("#pal-list button.row").findIndex((b) => /가나다화학/.test(b.textContent));
  assert.ok(at >= 0);
  for (let i = 0; i < at; i++) u.document.dispatchEvent(new u.window.KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));
  box.dispatchEvent(new u.window.KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
  await u.settle(20);
  assert.match(u.window.location.hash, /^#\/companies\/[\w-]+$/);
  assert.deepEqual(u.errors, []);
});

test("[ADMX-05] n opens the next question waiting for an answer, and the next after it", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/inquiries");
  const ids = u.$$("#main [data-row]").map((el) => el.dataset.row);
  assert.ok(ids.length >= 2, "questions wait");
  await u.key("n");
  await u.idle();
  const first = u.window.location.hash;
  assert.match(first, /^#\/inquiries\/[\w-]+$/);
  await u.key("n");
  await u.idle();
  assert.notEqual(u.window.location.hash, first);
  assert.deepEqual(u.errors, []);
});
// ---- AX 1.30 끝 ----

test("[ADMX-15] back to the tab while typing an answer: the menu numbers come again, the typed answer and its box stay", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/inquiries");
  await t.click("#main .rows .row");
  await t.type("#reply", "확인하고 있어요");
  const box = t.$("#reply");
  box.focus();
  const asked = [];
  const real = t.window.fetch;
  t.window.fetch = async (input, init) => {
    asked.push(new URL(typeof input === "string" ? input : input.url, ORIGIN).pathname);
    return real(input, init);
  };
  t.window.document.dispatchEvent(new t.window.Event("visibilitychange"));
  await t.settle();
  assert.ok(asked.includes("/api/admin/badges"), "the numbers are asked again");
  assert.ok(!asked.some((p) => /^\/api\/admin\/inquiries\//.test(p)), "the question is not read again under the typing");
  assert.equal(t.$("#reply"), box, "the same box, not a repainted one");
  assert.equal(box.value, "확인하고 있어요");
  assert.deepEqual(t.errors, []);
});

test("[DESK-02][DESK-03] 운영 장부: the old 정리함 address opens it; a line opens in place with its answer, 넘길 글 is copied, 끝내기 asks why, 기록 undoes", async () => {
  const { call, env } = await world();
  // 2.0.0 [RVA-49] 1280px부터는 줄이 오른쪽 칸에 열려요. 이 시험은 그 자리에서 펼치는 폭(1024)으로 봐요.
  const t = await signedIn(env, { width: 1024 });
  await t.go("#/sort");
  assert.equal(t.window.location.hash, "#/desk?view=claude&group=ask", "정리함 lives in 운영 장부 now");
  assert.ok(t.$$("#main .rows.ledger > li").length >= 1, "the waiting questions are lines");
  assert.match(t.text("#main .rows.ledger"), /K-\d{4}/, "every line has its number");
  assert.ok(t.$("#main .lg-head"), "a head row for the columns");
  assert.ok(t.$("#sort-tools .handoff-card"), "Claude에게 넘기기 is there");
  // A line's answer can be changed before it goes; what goes is what the box says.
  openLines(t);
  const reply = t.$('#main [data-act="ledger-reply"]');
  const key = reply.dataset.key;
  const id = key.split(":")[1];
  t.$(`#lg-reply-${id}`).value = "확인했어요. 고마워요.";
  await t.click(reply);
  await t.idle();
  assert.ok(!t.$(`#main li[data-key="${key}"]`), "an answered question leaves Claude 차례");
  const asked = (await call("GET", `/api/admin/inquiries/${id}`, undefined, await signIn(call))).data;
  assert.equal(asked.messages.at(-1).text, "확인했어요. 고마워요.");
  // 넘기기: a 개발사항 made from a line shows up to copy; copying marks it handed.
  openLines(t);
  await t.click('#main [data-act="ledger-sort"]');
  await t.idle();
  let copied = null;
  t.window.navigator.clipboard.writeText = async (x) => { copied = x; };
  await t.click('[data-act="sort-handoff"]');
  await t.idle();
  assert.match(copied ?? "", /# 밤낮 관리자 앱 → Claude/);
  assert.match(t.$('[data-act="sort-handoff"]').textContent, /개발 0/);
  assert.match(t.text(".handoff-log"), /넘김 기록 \(1\)/);
  // 열린 것 모두: the 개발사항 is a line too, handed to Claude now.
  await t.click('[data-act="ledger-view"][data-key="open"]');
  await t.idle();
  assert.match(t.text("#main .rows.ledger"), /Claude에게 넘김/);
  // 끝내기 asks why; with a reason the line goes to 닫힘, and 기록 can undo it.
  const first = t.$("#main .rows.ledger > li.lg-line").dataset.key;
  openLines(t);
  await t.click(`#main li[data-key="${first}"] [data-act="ledger-done"]`);
  assert.match(t.text("dialog"), /끝낸 이유를 꼭/);
  t.$('dialog input[name="reason"][value="duplicate"]').checked = true;
  await t.click('dialog form[data-form="ledger-done"] button[type="submit"]');
  await t.idle();
  assert.ok(!t.$(`#main li[data-key="${first}"]`), "it left 열린 것");
  await t.click('[data-act="ledger-view"][data-key="closed"]');
  await t.idle();
  assert.match(t.text(`#main li[data-key="${first}"]`), /끝 · 중복/);
  openLines(t);
  await t.click(`#main li[data-key="${first}"] [data-act="ledger-log"]`);
  await t.idle();
  assert.match(t.text("dialog"), /마지막 바꿈/);
  await t.click('dialog [data-act="ledger-undo"]');
  await t.idle();
  assert.ok(!t.$(`#main li[data-key="${first}"]`), "undone: back among the open lines");
  // 보기·찾기 keep to the address.
  await t.go("#/desk?view=all&q=K-0001");
  assert.equal(t.$("#search").value, "K-0001");
  assert.equal(t.$$("#main .rows.ledger > li").length, 1);
  assert.deepEqual(t.errors, []);
});

test("[EASE-31] 운영 장부: a question a saved answer fits has '마을 Q&A로 올리기', which opens that answer with the 묻는 말 box", async () => {
  const { call, env } = server();
  const owner = await signIn(call);
  const list = (await call("POST", "/api/admin/templates", { label: "백업 옮기기", text: "설정 › 백업에서 파일로 내보낸 뒤 새 폰에서 가져오기를 눌러요", topic: "backup" }, owner)).data;
  const tpl = (list.templates ?? list).find((x) => x.label === "백업 옮기기");
  await call("POST", "/api/inquiries", { v: 1, device: device(21), kind: "question", text: "새 폰으로 옮기려면 백업을 어떻게 가져오기 하나요", info: { app: "1.37.0 (51)" } }, from(21));
  const t = await signedIn(env, { width: 1024 });
  await t.go("#/desk?view=claude&group=ask");
  openLines(t);
  const up = t.$('#main [data-act="template-edit"]');
  assert.ok(up, "the line of a question a saved answer fits offers 마을 Q&A로 올리기");
  assert.match(up.textContent, /마을 Q&A로 올리기/);
  assert.equal(up.dataset.id, String(tpl.id), "it opens the answer that fits");
  await t.click(up);
  await t.idle();
  assert.ok(t.$("#tpl-q"), "the saved answer opens with its 묻는 말 box");
  assert.deepEqual(t.errors, []);
});

test("[SEC-11] 서버 상태 shows Play Integrity: off, and require without a service account as the thing to fix", async () => {
  // PC 확인 d263030e: /server answered integrity but the screen drew none of it.
  const off = await signedIn(server().env);
  await off.go("#/server");
  assert.ok(off.$('[data-integrity="off"]'), "the part is there when off");
  assert.match(off.text("#main"), /무결성 확인 \(Play Integrity\)/);
  assert.match(off.text("#main"), /꺼짐: 남에게 보이는 쓰기/);
  assert.ok(!off.$("[data-integrity-numbers]"), "no numbers while off");

  const stuck = await signedIn(server({ INTEGRITY_MODE: "require" }).env);
  await stuck.go("#/server");
  assert.ok(stuck.$('[data-integrity="require"]'));
  assert.match(stuck.text("#main"), /지금 남에게 보이는 쓰기가 모두 막혀요/);
  assert.ok(stuck.$('[data-integrity="require"] + ul.checks li.bad'), "drawn as the thing to fix first");
  assert.match(stuck.text("[data-integrity-numbers]"), /오늘 확인을 물은 쓰기 0 · 지난 하루 확인에 실패한 휴대폰 0 · 7일 안에 확인된 휴대폰 0/);
  assert.equal(stuck.$$("#main [style]").length, 0);
  assert.deepEqual([...off.errors, ...stuck.errors], []);
});

test("[SEC-10] 마을 장터 › 글꼴: a first font waits with what it says of itself, its words drawn as words, 나누기 lets it go", async (t) => {
  offline(t);
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { fakeFont } = await import("../font-fixture.mjs");
  const s = server({ plaza: true });
  const { call, env } = s;
  const font = fakeFont({ family: "<b>굵게</b> 손글씨", maker: '"><img src=x onerror=globalThis.__xss=1>' });
  const id = (await call("POST", "/api/recipes/fonts", { device: device(1), name: "손글씨", license: "ofl", ttf: font.toString("base64") }, from(1))).data.id;
  const u = await asOwner(env, t);
  await u.go("#/recipes");
  await u.click('#main [data-act="recipe-tab"][data-key="fonts"]');
  await u.idle();
  assert.match(u.text("#main"), /확인 기다림 1/);
  assert.match(u.text("#main"), /이름 <b>굵게<\/b> 손글씨/, "the font's own name, as words");
  assert.match(u.text("#main"), /글꼴 안에 OFL이라고 적혀 있어요/);
  assert.equal(u.$$("#main b").filter((b) => b.textContent === "굵게").length, 0);
  assert.equal(u.$$('#main img[src="x"]').length, 0);
  assert.equal(globalThis.__xss, undefined);
  await u.click(`#main [data-act="recipe-font"][data-id="${id}"][data-key="ok"]`);
  await u.settle(20);
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 200, "others get it now");
  // 1.36 [SEC-10] M8: the same letters sent again under another name wait beside the one already shared.
  await call("POST", "/api/recipes/fonts", { device: device(2), name: "복사본", license: "ofl", ttf: fakeFont({ family: "새 이름", maker: "x" }).toString("base64") }, from(2));
  await u.click('#main [data-act="font-state"][data-key="pending"]');
  await u.settle(20);
  assert.match(u.text("#main"), /글자 모양이 같은 글꼴을 나누는 중: 손글씨 · [+−]\d+B/);
  assert.deepEqual(u.errors, []);
});

test("[GRW-12] 작품 열기: from 사진 검토 the recipe opens with the photo and its 보이기·가리기, and 돌아가기 leads back", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const s = server({ plaza: true });
  const webp = Buffer.concat([Buffer.from("RIFF"), Buffer.from([30, 0, 0, 0]), Buffer.from("WEBPVP8 "), Buffer.from([10, 0, 0, 0]),
    Buffer.from([0, 0, 0, 0x9d, 0x01, 0x2a, 64, 0, 64, 0]), Buffer.alloc(16, 7)]).toString("base64");
  const pic = (await s.call("POST", "/api/recipes/images", { device: device(1), webp }, from(1))).data;
  assert.equal(pic.state, "pending", "a 새싹's first photo waits for a look");
  const body = { format: "bamnat-recipe", v: 1, title: "열어 볼 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
    cover: { a: 4282339765, b: 4288423856, dir: 1, emoji: "🌙", image: pic.id },
    pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }] };
  const made = await s.call("POST", "/api/recipes", { device: device(1), recipe: body, scope: "public" }, from(1));
  assert.equal(made.status, 201);
  const code = made.data.code;
  const t1 = await signedIn(s.env);
  await t1.go("#/recipes");
  await t1.click('[data-act="recipe-tab"][data-key="images"]');
  await t1.settle(20);
  await t1.click(`[data-act="recipe-open"][data-id="${code}"]`);
  await t1.settle(20);
  assert.match(t1.text("#main"), new RegExp(`작품 ${code} 하나만`));
  assert.match(t1.text("#main"), /열어 볼 세트/);
  assert.match(t1.text("#main"), /보러 온 사진/);
  assert.ok(t1.$(`[data-act="recipe-do"][data-id="${code}"][data-key="hide"]`), "가리기 for the recipe is right there");
  await t1.click('[data-act="photo-big"]');
  assert.ok(t1.$("dialog img.photo-big"), "the photo opens big");
  await t1.click('dialog [data-act="close"]');
  await t1.click(`[data-act="recipe-image"][data-id="${pic.id}"][data-key="ok"]`);
  await t1.settle(20);
  assert.match(t1.text("#main"), /보러 온 사진.*보이게 했어요/);
  assert.ok(!t1.$(`[data-act="recipe-image"][data-id="${pic.id}"]`), "decided: the buttons go");
  const back = t1.$('[data-act="recipe-tab"][data-key="images"]:not(.seg)');
  assert.match(back.textContent, /돌아가기 · 사진 검토/);
  await t1.click(back);
  await t1.settle(20);
  assert.match(t1.text("#main"), /볼 사진이 없어요/);
  assert.deepEqual(t1.errors, []);
});

test("[STAT2-01~04] 숫자 › 한눈에: 정상도 신호, 초기 모드, 진입·머묾 고리·주마다 머묾 표, 더 보기", async (t) => {
  offline(t);
  const { call, env, DB } = server();
  await call("GET", "/api/config");
  // 처리방침 3.12 in force, so stats.life is on; ten phones came 20 days ago, six opened the next day, three a week later.
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .run(JSON.stringify({ "3.12": { posted: "2026-01-01", effective: "2026-01-02", heldUntil: null, unfavourable: false } }));
  const day = (n) => new Date(Date.now() + 9 * 3600e3 - n * 86400e3).toISOString().slice(0, 10);
  const put = DB.db.prepare("INSERT INTO usage_events (day, app, event, phones, total, recent) VALUES (?, '1.39.0', ?, ?, ?, 0)");
  put.run(day(20), "life_new", 10, 10);
  put.run(day(19), "life_d1", 6, 6);
  put.run(day(13), "life_d7", 3, 3);
  put.run(day(5), "life_back7", 2, 2);
  DB.db.prepare("INSERT INTO usage_days (day, app, phones) VALUES (?, '1.39.0', 4)").run(day(2));
  const u = await asOwner(env, t);
  await u.go("#/glance");
  await u.settle(30);
  const main = u.text("#main");
  assert.match(main, /앱 정상도/);
  assert.ok(u.$("#main .gl-hero"), "the big signal");
  assert.equal(u.$$("#main .gl-gauge").length, 4);
  assert.match(main, /초기 모드/, "fewer than 30 phones a day");
  assert.match(main, /60%/, "1일째 6 of 10");
  assert.match(main, /30%/, "7일째 3 of 10");
  assert.equal(u.$$("#main .gl-ring").length, 3);
  assert.ok(u.$("#main .gl-cohort td[data-label='1일째'].h4"), "주마다 머묾 표 with a heat class");
  assert.match(main, /회복/);
  assert.ok([...u.$$("#main .gl-more a")].some((a) => a.getAttribute("href") === "#/metrics"));
  assert.deepEqual(u.errors, []);
});

test("[ADM3-45~47] 오늘 첫 화면 한 문장 + 할 일 셋, 같은 회사일까요?, 여러 판 맨 위 한 줄", async (t) => {
  offline(t);
  const { call, env } = server();
  await call("POST", "/api/reports", report(1, { company: "lg전자 칠러공장" }), from(1));
  await call("POST", "/api/reports", report(2, { company: "LG전자(칠러)" }), from(2));
  // Two versions in one department, so 여러 판 has something to decide.
  await call("POST", "/api/reports", report(3), from(3));
  const twelve = setup();
  twelve.pay.rules.extra[3].percent = 0.12;
  await call("POST", "/api/reports", report(4, { setup: twelve }), from(4));
  const u = await asOwner(env, t);
  await u.go("#/home");
  await u.settle(30);
  const hero = u.$("#main .today-hero");
  assert.ok(hero, "the state in one sentence first");
  assert.match(u.text("#main .today-hero h2"), /있어요|없어요|할 일이/);
  assert.ok(u.$$("#main .today-three li").length <= 3);
  assert.ok(u.$("#main .alerts") || u.$("#main .calm"), "주의 필요 is still there in full");
  // 실서버 11-08 크롬: what the top card shows is folded below as '위에서 본 N개', not shown twice.
  const shownTop = u.$$("#main .today-three b").map((b) => b.textContent);
  const below = [...u.$$("#main .alerts > ul.alert-list b"), ...u.$$("#main .todo-card > ul.inbox .row-main a")].map((e) => e.textContent);
  assert.ok(shownTop.length > 0);
  assert.deepEqual(below.filter((x) => shownTop.some((top) => top === x || top.endsWith(` · ${x}`))), [], JSON.stringify({ shownTop, below }));
  assert.match(u.text("#main .shown-above summary"), /위에서 본 \d+개/);
  await u.go("#/companies");
  await u.settle(30);
  assert.match(u.text("#main .similar"), /같은 회사일까요\?/);
  assert.match(u.text("#main .similar"), /이름이 같아요/);
  await u.click('#main [data-act="similar-skip"]');
  await u.settle(30);
  assert.equal(u.$("#main .similar"), null, "'다른 회사예요' takes it away");
  await u.go("#/versions");
  await u.settle(30);
  assert.match(u.text("#main .vrec"), /항목이 달라요|숫자만 달라요/);
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

test("[ADM3-48] a new admin build opens itself once when nobody is typing, and never under typing (실서버 11-08 크롬 '새로 열기')", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  let reloads = 0;
  try {
    Object.defineProperty(t.window.location, "reload", { configurable: true, value: () => { reloads++; } });
  } catch {
    t.window.location.reload = () => { reloads++; };
  }
  // Typing an answer: the band shows, nothing reloads under the words.
  await t.go("#/inquiries");
  await t.click("#main .rows .row");
  await t.type("#reply", "확인하고 있어요");
  t.$("#reply").focus();
  const real = t.window.fetch;
  t.window.fetch = async (input, init) => {
    if (new URL(typeof input === "string" ? input : input.url, ORIGIN).pathname !== "/api/admin/badges") return real(input, init);
    // The whole answer each time (no 304), with the server saying a newer build is out.
    const headers = new Headers(init?.headers ?? {});
    headers.delete("if-none-match");
    const res = await real(input, { ...init, headers });
    const b = await res.json();
    return new t.window.Response(JSON.stringify({ ...b, build: "next-build" }), { status: 200, headers: { "content-type": "application/json" } });
  };
  t.window.document.dispatchEvent(new t.window.Event("visibilitychange"));
  await t.settle(30);
  assert.equal(reloads, 0, "not while typing");
  assert.match(t.text("#banners"), /관리자 앱이 새로 나왔어요/);
  // Not typing any more: once for this build, however often the numbers come again.
  t.$("#reply").blur();
  // The next numbers come with the 30-second poll; here the clock moves past the 15 seconds they are kept.
  const clock = t.window.Date.now.bind(t.window.Date);
  t.window.Date.now = () => clock() + 20000;
  t.window.document.dispatchEvent(new t.window.Event("visibilitychange"));
  await t.settle(30);
  t.window.document.dispatchEvent(new t.window.Event("visibilitychange"));
  await t.settle(30);
  assert.equal(reloads, 1);
  assert.equal(t.window.sessionStorage.getItem("admin:auto-reload:next-build"), "1");
  assert.deepEqual(t.errors, []);
});

test("[ADM3-49] Claude 추천 shows on 검토함·보상, 거절 starts at its reason, and 조종석 counts how often a person agreed", async (t) => {
  offline(t);
  const { call, env } = server();
  await call("POST", "/api/reports", report(1, { company: "가나다물류" }), from(1));
  const reward = "w" + "B".repeat(22);
  env.DB.db.prepare("INSERT INTO rewards (id, report_id, rule_id, device, months, status, created_at) VALUES (?, 'rp', '', 'dev', 1, 'owed', ?)").run(reward, Date.now());
  const u = await asOwner(env, t);
  const [g] = (await u.api("GET", "/api/admin/queue")).groups;
  await u.api("POST", "/api/admin/drafts", { kind: "group", company: g.company.id, grp: g.setup, pick: "reject", reason: "wrong", note: "공고의 교대와 달라요", sources: ["https://example.com/a"] });
  await u.api("POST", "/api/admin/drafts", { kind: "reward", id: reward, pick: "paid", months: 3, note: "회사 방식 채택이라 3개월" });
  await u.go("#/review");
  await u.settle(30);
  assert.match(u.text("#main .rv-body .claude-draft"), /Claude 추천 거절/);
  assert.match(u.text("#main .rv-body .claude-draft"), /공고의 교대와 달라요/);
  assert.equal(u.$("#main .rv-body .claude-draft a").getAttribute("rel"), "noopener noreferrer");
  await u.click('#main [data-act="reject"]');
  assert.equal(u.$('#dialog input[name="reason"]:checked').value, "wrong", "the 추천's reason is picked first");
  u.$('#dialog [data-act="close"]').click();
  await u.go("#/rewards");
  await u.settle(30);
  assert.match(u.text("#main .claude-draft"), /지급 · 3개월/);
  assert.match(u.text(`#main [data-act="reward"][data-status="paid"]`), /3개월/);
  await u.go("#/cockpit");
  await u.settle(30);
  assert.match(u.text("#main"), /제보·보상 추천/);
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

test("[ADM3-50] seven flags on at once are one folded card above 오늘's big card, with 이·가 by the last letter (PC 6차 J1·H1)", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  const names = ["결제(프로 구독)", "광고", "공유 급여 보기", "수면 기록", "팀 근무표", "사장님 급여", "건강 알림"];
  const real = t.window.fetch;
  t.window.fetch = async (input, init) => {
    if (new URL(typeof input === "string" ? input : input.url, ORIGIN).pathname !== "/api/admin/badges") return real(input, init);
    const headers = new Headers(init?.headers ?? {});
    headers.delete("if-none-match");
    const b = await (await real(input, { ...init, headers })).json();
    const since = new Date(Date.now() - 600e3).toISOString();
    return new t.window.Response(JSON.stringify({ ...b, flagAlerts: names.map((name, i) => ({ key: `f${i}`, name, since })) }),
      { status: 200, headers: { "content-type": "application/json" } });
  };
  // The menu numbers are kept 15 seconds; the clock moves past them so the next screen asks again.
  const clock = t.window.Date.now.bind(t.window.Date);
  t.window.Date.now = () => clock() + 20000;
  await t.go("#/companies");
  await t.go("#/home");
  await t.settle(30);
  assert.equal(t.$$("#main .card.flag-alert").length, 1, "one card, not seven");
  assert.match(t.text("#main .flag-alert summary"), /기능 7개가 저절로 켜졌어요/);
  assert.equal(t.$("#main .flag-alert details").open, false, "folded until 펼치기");
  assert.ok(t.$("#main .flag-alert + .today-hero"), "the big card right under it");
  const lines = t.$$("#main .flag-alert-list b").map((b) => b.textContent);
  assert.ok(lines.includes("결제(프로 구독)가 켜졌어요"));
  assert.ok(lines.includes("광고가 켜졌어요"));
  assert.ok(lines.includes("수면 기록이 켜졌어요"));
  assert.ok(lines.includes("건강 알림이 켜졌어요"));
  // H1: what runs (회사·규칙·서버 판·빌드) first on 서버 상태.
  await t.go("#/server");
  await t.settle(30);
  const kids = [...t.$("#main").children];
  const at = kids.findIndex((k) => k.classList.contains("server-sum"));
  assert.ok(at >= 0 && at < kids.findIndex((k) => k.matches(".card, section")), kids.map((k) => k.className).join(" | "));
  assert.match(t.text("#main .server-sum"), /관리자 앱 빌드/);
  assert.deepEqual(t.errors, []);
});

// [ADM3-51] 8차 PC 1-21: 머리띠의 서버 판. 배포 번호(버전 메타데이터)가 없는 로컬 서버는 '로컬 서버'와 커밋 자리 '로컬'.
test("[ADM3-51] the top line says which server runs: local without a deploy, the commit and deploy time on a deploy", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  await t.go("#/home");
  await t.settle(30);
  const line = t.$("#deploy-line");
  assert.ok(line && !line.hidden, "shown");
  assert.match(t.text("#deploy-line"), /로컬 서버/);
  assert.match(t.text("#deploy-line"), /회사 \d+\.\d+\.\d+ 로컬/);
  assert.equal(line.getAttribute("href"), "#/server");
  // 배포된 서버: 커밋과 배포 시각.
  const s2 = await world({ CF_VERSION_METADATA: { id: "1789720e-0000", tag: "9a99201a1b2c", timestamp: "2026-11-08T14:30:00.000Z" } });
  const t2 = await signedIn(s2.env);
  await t2.go("#/home");
  await t2.settle(30);
  assert.match(t2.text("#deploy-line"), /지금 도는 서버/);
  assert.match(t2.text("#deploy-line"), /9a99201a/);
  assert.doesNotMatch(t2.text("#deploy-line"), /로컬/);
  assert.deepEqual([...t.errors, ...t2.errors], []);
});

test("[OBS2-01~05] 한눈에 is the 관측 board: 전체 사용자 tiles say where each number comes from, a 개선 신호 becomes a 개발사항, 관측 빈틈 marks what needs the policy", async (t) => {
  offline(t);
  const { env } = server();
  const now = Date.now();
  const signal = { key: "uninstall-spike:2026-11-08", kind: "uninstall-spike", subject: "", tone: "bad", title: "앱 삭제가 늘었어요: 최근 3일 하루 6, 그 전 2주 1.5",
    why: "Play 보고서의 하루 삭제 수예요.", dev: "앱 삭제가 늘어난 이유 찾기", to: "#/glance", unit: "/일", better: "down", value: 6, base: 1.5, now: 6, first: now, last: now, at: now, state: "new" };
  const better = { ...signal, key: "store-conv:2026-10-30", kind: "store-conv", title: "스토어 방문 → 설치가 9%로 떨어졌어요", unit: "%", value: 9, after: 16, cleared: now, state: "dev", devId: "dCCCCCCCCCCCCCCC" };
  const u = await asOwner(env, t);
  env.DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").run(JSON.stringify({ list: [signal, better] }));
  await u.go("#/glance");
  await u.settle(40);
  assert.ok(u.$("#main .ob-all"), "전체 사용자");
  // No Play account here: one line says what to connect, not four empty boxes.
  assert.equal(u.$$("#main .ob-tile.need").length, 1);
  assert.match(u.text("#main .ob-tile.need"), /연결 필요/);
  assert.match(u.text("#main .ob-tile.keep"), /앱 → 서버 요청/);
  assert.match(u.text("#main .ob-tile.voice"), /문의/);
  assert.equal(u.$$("#main .ob-signal").length, 1, "the cleared one waits under 좋아진 신호");
  assert.match(u.text("#main .ob-better"), /9% → 16%/);
  // [OBS2-09] 할 일만 펼치고, 기다리면 풀리는 것(3.12 시행·1.39 출시)과 정한 것(지운 이유)은 접어요.
  assert.ok(u.$("#main .ob-gaps details.ob-gap-rest"), "folded");
  assert.match(u.text("#main .ob-gap-rest summary"), /기다리면 풀리는 것 \d · 정한 것 1/);
  assert.equal(u.$$("#main .ob-gaps > .ob-gap-list li.decided").length, 0, "정한 것 is not in the open list");
  assert.match(u.text("#main .ob-gap-rest"), /3\.12/);
  assert.match(u.text("#main .ob-gaps"), /사용자 결정/);
  await u.click('#main [data-act="obs-signal"][data-do="dev"]');
  await u.settle(40);
  const made = env.DB.db.prepare("SELECT id, title FROM dev_items ORDER BY created_at DESC LIMIT 1").get();
  assert.match(made.title, /앱 삭제가 늘어난 이유/);
  assert.match(u.text("#main .ob-signal"), /개발사항/);
  assert.ok(u.$(`#main .ob-signal a[href*="${made.id}"]`), "a link to the new 개발사항");
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

test("[OBS2-08] 유형별 흐름 and 기능과 머묾 show once 처리방침 3.12 is in force: kinds by 7th day, features against everyone", async (t) => {
  offline(t);
  const { env } = server();
  const u = await asOwner(env, t);
  const db = env.DB.db;
  db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .run(JSON.stringify({ "3.12": { posted: "2025-01-01", effective: "2025-01-02", heldUntil: null, unfavourable: false } }));
  const day = (n) => new Date(Date.now() + 9 * 3600000 - n * 86400000).toISOString().slice(0, 10);
  const kind = db.prepare("INSERT INTO usage_kinds (day, app, style, event, phones) VALUES (?, '1.39.0', ?, ?, ?)");
  kind.run(day(20), "SHIFT", "life_new", 40); kind.run(day(13), "SHIFT", "life_d7", 20);
  kind.run(day(20), "HOURLY", "life_new", 30); kind.run(day(13), "HOURLY", "life_d7", 3);
  const use = db.prepare("INSERT INTO usage_events (day, app, event, phones, total, recent) VALUES (?, '1.39.0', ?, ?, ?, ?)");
  use.run(day(20), "open", 100, 300, 100); use.run(day(20), "pay_view", 40, 60, 40);
  const keep = db.prepare("INSERT INTO usage_keep (day, app, event, win, phones) VALUES (?, '1.39.0', ?, 7, ?)");
  keep.run(day(20), "open", 50); keep.run(day(20), "pay_view", 30);
  await u.go("#/glance");
  await u.settle(40);
  assert.match(u.text("#main .ob-kinds"), /교대/);
  assert.match(u.text("#main .ob-kinds"), /알바/);
  assert.match(u.text("#main .ob-kinds"), /전체/);
  assert.match(u.text("#main .ob-keep"), /50%/, "기준: everyone that day");
  assert.match(u.text("#main .ob-keep"), /\+25%p/, "급여 보기 75% against 50%");
  assert.ok(!u.$$("#main .ob-gap-list li").some((li) => /유형\(교대/.test(li.textContent)), "by-type is answered");
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
});

// 1.40 [RVD-06] 검토 ADM-01·ADMX-04: 목록에서 처음 여는 문의의 '이 휴대폰' 패널이 미리 데우기 때 '못 불러옴'으로 굳어 다시 안 읽혔어요.
test("[RVD-06] an inquiry opened from the list reads its phone panel once, also when the screen was warmed first", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  const id = (await s.call("GET", "/api/admin/inquiries", undefined, await signIn(s.call))).data.inquiries[0].id;
  let reads = 0;
  const fetch = t.window.fetch;
  t.window.fetch = (input, init) => {
    if (String(typeof input === "string" ? input : input.url).includes("/person?")) reads++;
    return fetch(input, init);
  };
  await t.go("#/inquiries");
  await t.go(`#/inquiries/${id}`);
  await t.settle(30);
  assert.equal(reads, 1);
  assert.ok(t.$("aside.person h2"), t.text("aside.person"));
  assert.doesNotMatch(t.text("aside.person"), /불러오지 못했어요/);
  assert.deepEqual(t.errors, []);
});

// 2.0.0 [RVD-16] 사용자 결정 2026-11-12 '확인 뒤 지급 + 1년 상한': 친구 초대·만든 작품 보상도 드릴 보상에 쌓여요. 종류로 좁히고, 판단할 것과
// 이분의 1년 12개월을 줄마다 보고, 상한 넘은 것을 골라 한 번에 취소해요.
test("[RVD-16] 드릴 보상: kind chips, what to judge by, the year's cap, and picking several at once", async () => {
  const { env, DB } = await world();
  const put = (id, rule, device, months, status = "owed", paidAt = null, report = id) => DB.db.prepare(`INSERT INTO rewards (id, report_id, rule_id, device, months,
    status, memo, created_at, paid_at, deferred) VALUES (?, ?, ?, ?, ?, ?, '', ?, ?, 0)`).run(id, report, rule, device, months, status, Date.now(), paidAt);
  // [RVD-21] 1년 12개월은 처리방침 3.13 게시 + 30일부터: 일정에 3.13을 한 달 반 전 게시로 넣어 상한이 걸린 때를 봐요.
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)").run(JSON.stringify({ "3.13": { posted: "2026-10-01", effective: "2026-10-01", heldUntil: null } }));
  put("wPAIDaaaaaaaaaaaaaaaaaa", "rule-x", "full", 12, "paid", Date.now());
  put("iOVERaaaaaaaaaaaaaaaaa", "invite", "full", 1);
  put("iFREEaaaaaaaaaaaaaaaaa", "invite", "free", 1);
  put("mWORKaaaaaaaaaaaaaaaaa", "maker", "free", 1, "owed", null, "maker:tool:ABCD2345");
  const t = await signedIn(env);
  await t.go("#/rewards");
  await t.settle(30);
  const main = () => t.$("#main").textContent;
  assert.match(main(), /친구 초대 2/);
  assert.match(main(), /만든 작품 1/);
  assert.match(main(), /이분의 1년 보상 12\/12개월/);
  // 디자인 품질(총괄 11-12): 제목·'프로 1개월 · 날짜 · 종류'·근거 한 줄, '만든 작품'은 한 번만, 살펴볼 것은 주의 칩.
  const work = [...t.document.querySelectorAll("#main ul.rows > li")].find((li) => li.textContent.includes("ABCD2345"));
  assert.match(work.querySelector("b").textContent, /만든 작품 · ABCD2345/);
  assert.match(work.textContent, /프로 1개월 · .+ · 모두의 도구/);
  assert.match(work.textContent, /쓰는 분 0 · 7일 유지 0%/);
  assert.equal(work.textContent.split("만든 작품").length - 1, 1, "만든 작품 once in a row");
  assert.ok([...work.querySelectorAll(".row-tags .pill.warn")].some((x) => x.textContent === "장터에 안 보여요"));
  const over = t.$('#main ul.rows > li [data-id="iOVERaaaaaaaaaaaaaaaaa"]').closest("li");
  assert.ok([...over.querySelectorAll(".row-tags .pill.warn")].some((x) => x.textContent === "상한"));
  // 조건 2: 줄 안에는 채운 단추가 없고, 설명은 한 줄 + 펼치기, 규칙 문장은 아래 친구 초대 카드에 한 번만.
  assert.equal(t.document.querySelectorAll("#main ul.rows .btn.primary").length, 0);
  assert.equal(t.document.querySelectorAll("#main .btn.primary:not([disabled])").length, 0);
  assert.ok(t.$("#main details.tip:not([open]) > summary"), "the how-to is one line that opens");
  // [RVD-21] 3.13 + 30일이 지났으니 접힌 한 줄은 상한을 말해요.
  assert.equal(t.text('#main details[data-key="reward-how"] > summary'), "확인 뒤 지급 · 한 분에 1년 12개월까지");
  assert.equal(main().split("명이 각자").length - 1, 1, "the invite rule is said once");
  // 조건 1(총괄 11-12 2차): 구간·찾기·종류·고르기·CSV는 목록 머리 한 줄, 그 바로 밑이 드릴 보상 목록. 한 줄 설명·Claude 줄은 목록 아래.
  const order = [...t.$("#main").children].map((e) => e.matches(".list-head") ? "head" : e.matches("ul.rows") ? "rows" : e.matches(".list-foot") ? "foot" : e.matches("section.card") ? "card" : "?");
  assert.deepEqual(order.slice(0, 4), ["head", "rows", "foot", "card"], order.join(","));
  assert.ok(t.$("#main .list-head > .segmented") && t.$("#main .list-head #search") && t.$("#main .list-head #reward-kind"), "one head line");
  assert.equal(t.$('#main .list-head [data-act="csv"]').getAttribute("aria-label"), "CSV로 받기");
  assert.equal(t.$('#main .list-head [data-act="reward-pick"]').getAttribute("aria-label"), "여러 개 고르기");
  assert.ok(t.$("#main .list-foot .claude-draft-empty") && t.$("#main .list-foot details.tip"));
  // Play Integrity is off here (INTEGRITY_MODE), so '0명 확인' is not a warning, just said off.
  assert.match(main(), /친구 0명 · Play 확인 꺼짐/);
  assert.ok(!t.$('[data-act="reward"][data-status="paid"][data-id="iOVERaaaaaaaaaaaaaaaaa"]'), "no 지급하기 past the cap");
  const kind = t.$("#reward-kind");
  kind.value = "invite";
  kind.dispatchEvent(new t.window.Event("change", { bubbles: true }));
  await t.settle(30);
  assert.ok(t.$("#main .list-more.busy"), "the head says a filter is on");
  assert.equal(t.document.querySelectorAll("#main ul.rows > li").length, 2);
  await t.click('[data-act="reward-pick"]');
  await t.click('[data-act="reward-pick-cap"]');
  assert.match(t.text(".bulkbar"), /1개 골랐어요/);
  await t.click('[data-act="reward-bulk"][data-key="void"]');
  await t.settle(30);
  assert.deepEqual(DB.db.prepare("SELECT id, status FROM rewards WHERE rule_id = 'invite' ORDER BY id").all().map((r) => [r.id, r.status]),
    [["iFREEaaaaaaaaaaaaaaaaa", "owed"], ["iOVERaaaaaaaaaaaaaaaaa", "void"]]);
  assert.deepEqual(t.errors, []);
});

// 2.0.0 [RVQ-15] 검토 SRV-17: 처리방침 날짜 창은 오늘보다 앞이나 알림 기간 안으로 옮길 때만 까닭 한 줄과 확인 칸을 보여요(평소에는 짧게).
test("[RVQ-15] the policy dialog asks once more only when the date moves before today or inside the notice period", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  await t.go("#/flags");
  await t.settle(20);
  await t.click('#main [data-act="flag-view"][data-key="timeline"]');
  await t.click('#main [data-act="flag-policy"]:not([data-version])');
  const kst = (days) => new Date(Date.now() + 9 * 3600e3 + days * 86400e3).toISOString().slice(0, 10);
  const row = () => t.$("dialog [data-policy-confirm]");
  assert.ok(row()?.hidden, "새 판을 열면 확인 칸이 없어요");
  await t.type("#fp-version", "3.30");
  await t.type("#fp-posted", kst(0));
  await t.type("#fp-effective", kst(10));
  assert.ok(row().hidden, "알림 기간(7일)을 지키면 묻지 않아요");
  await t.type("#fp-effective", kst(3));
  assert.ok(!row().hidden);
  assert.match(t.text("dialog [data-policy-why]"), /3일이에요.*7일/);
  await t.type("#fp-posted", kst(-3));
  await t.type("#fp-effective", kst(-1));
  assert.match(t.text("dialog [data-policy-why]"), /오늘보다 앞/);
  // 확인하지 않고 저장하면 서버가 409로 막고, 확인하면 저장돼요.
  t.$("#fp-why").value = "시험";
  t.$('dialog form[data-form="flag-policy"]').requestSubmit();
  await t.settle(30);
  assert.ok(t.$("dialog[open]") && !row().hidden, "막히면 창과 확인 칸이 그대로");
  t.$('dialog input[name="confirm"]').checked = true;
  t.$('dialog form[data-form="flag-policy"]').requestSubmit();
  await t.settle(30);
  const owner = await signIn(s.call);
  const pol = (await s.call("GET", "/api/admin/flags", undefined, owner)).data.policy;
  assert.equal(pol.find((p) => p.version === "3.30")?.effective, kst(-1));
  assert.deepEqual(t.errors, []);
});

// 2.0.0 [RVQ-14] 검토 U21-02: 서버 상태 맨 위 한 줄 앞머리에 지금 판·배포 번호·되돌릴 판, 아래에 깊은 점검 셋. 머리띠의 배포 줄과 이름이 겹치지 않아요.
test("[RVQ-14] 서버 상태 starts with the running version, deploy, rollback version and three deep checks", async () => {
  const s = await world({ CF_VERSION_METADATA: { id: "1789720e-0000-4000-8000-000000000000", tag: "9a99201a1b2c", timestamp: "2026-11-08T14:30:00.000Z" } });
  const t = await signedIn(s.env);
  s.DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('server_prev', ?)").run("1.39.0 aaaabbbb");
  await t.go("#/server");
  await t.settle(30);
  const sum = t.text("#main .server-sum");
  assert.match(sum, /^지금 판 \d+\.\d+\.\d+ · 배포 1789720e/);
  assert.match(sum, /되돌릴 판 1\.39\.0/);
  assert.match(sum, /관리자 앱 빌드/);
  assert.equal(t.$$("#main [data-deploy] li").length, 3);
  assert.equal(t.$$("#main .deploy-line").length, 0, "머리띠의 배포 줄(#deploy-line)과 같은 이름을 쓰지 않아요");
  assert.match(t.text("#main [data-budget]"), /요금제/);
  assert.deepEqual(t.errors, []);
});

// 2.0.0 [RVQ-24] 검토 SVC-07·I22b-03: 정기 작업이 잰 공유 서버 KV 오늘 쓰기·읽기가 요금제 줄에 하루 몫의 %로, 80%를 넘으면 주의 색으로.
test("[RVQ-26] SVC-52 장터 › 다시 올라온 것: 줄에 볼 일 둘(괜찮아요·다시 돌려주기), 가리기와 나머지는 ⋯ 안", async (t) => {
  offline(t);
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { call, env } = server({ plaza: true });
  const body = (summary) => ({ format: "bamnat-recipe", v: 1, title: "봄꽃 교대 달력", summary, styles: ["SHIFT"],
    pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }] });
  const code = (await call("POST", "/api/recipes", { device: device(1), recipe: body("주간은 분홍"), scope: "public" }, from(1))).data.code;
  const u = await asOwner(env, t);
  await u.api("POST", `/api/admin/recipes/${code}/return`, { note: "연락처를 빼 주세요" });
  t.mock.timers.tick(60e3);
  await call("POST", `/api/recipes/${code}/versions`, { device: device(1), recipe: body("연락처를 뺐어요. 주간은 분홍") }, from(1));
  await u.go("#/recipes");
  await u.click('#main [data-act="recipe-tab"][data-key="again"]');
  await u.idle();
  const side = u.$("#main .recipe-rows .row-side");
  assert.ok(side, "다시 올라온 작품 한 줄");
  const shown = [...side.children].filter((e) => e.matches("button")).map((b) => b.textContent.trim());
  assert.deepEqual(shown, ["괜찮아요", "다시 돌려주기"]);
  // [RVA-35] 조건 2: 줄마다 채운 단추가 서면 목록 전체가 채운 단추 줄이 돼요. 줄 안 단추는 테두리예요.
  assert.equal(u.window.document.querySelectorAll("#main .recipe-rows .btn.primary").length, 0, "줄 안 단추는 테두리");
  const menu = [...side.querySelectorAll(".row-more .menu button")].map((b) => b.textContent.trim());
  assert.equal(menu[0], "가리기");
  assert.ok(menu.includes("지난 판 지우기") && menu.includes("지우기"), menu.join(","));
  side.querySelector('[data-key="approve"]').click();
  await u.settle(40);
  assert.equal(env.DB.db.prepare("SELECT state FROM recipes WHERE code = ?").get(code).state, "visible");
  assert.deepEqual(u.errors, []);
});

test("[RVQ-24] 서버 상태 shows the share server's KV writes and reads for today against the plan's day", async () => {
  const s = await world();
  const t = await signedIn(s.env);
  const day = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
  const put = (v) => s.DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('share_kv', ?)").run(JSON.stringify(v));
  put({ day: "2020-01-01", writes: 30000, reads: 1, at: Date.now() });
  await t.go("#/server");
  await t.settle(30);
  assert.doesNotMatch(t.text("#main [data-budget] + ul"), /공유 서버 KV/, "어제 것은 오늘 줄이 아니에요");
  put({ day, writes: 1200, reads: 290000, at: Date.now() - 4 * 60e3 });
  await t.go("#/today");
  await t.settle(30);
  await t.go("#/server");
  await t.settle(30);
  const line = t.$$("#main [data-budget] + ul li").find((li) => /공유 서버 KV/.test(li.textContent));
  assert.ok(line, "공유 KV 줄");
  assert.match(line.textContent, /공유 서버 KV 읽기 하루 몫의 8\d%\(290,000번\), 쓰기 \d%\(1,200번\)/, "몫을 더 쓴 읽기(약 33만의 87%)가 앞에");
  assert.match(line.textContent, /분 전 기준/);
  assert.equal(line.className, "todo", "80%를 넘으면 주의 색");
  assert.match(t.text("#main [data-budget] ~ .hint"), /공유 KV는 새 링크부터/);
  assert.deepEqual(t.errors, []);
});

// ---- 2.0.0 갈래 Q2 (검토 영역 23·24): 믿음·안전 ----

test("[RVA-10] a question that is gone says so, with 목록으로, not '서버가 답하지 않아요'", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/inquiries/zzzzzzzzzzzzzzzzzzzzzz");
  assert.match(t.text("#main .failed"), /지워졌거나 없는 문의예요/);
  assert.equal(t.$("#main .failed a.btn.primary")?.getAttribute("href"), "#/inquiries");
  assert.doesNotMatch(t.text("#main"), /서버가 잠시 답하지 않아요/);
});

test("[RVA-08][RVA-07] a key shown once asks before it closes, is wiped after, and the box's history step goes with it", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/keys");
  await t.click('[data-act="key-make"]');
  assert.equal(t.window.history.state?.dialog, true, "the box has a step of its own");
  t.$('form[data-form="key-make"]').requestSubmit();
  await t.settle(30);
  assert.ok(t.$("dialog [data-secret] #key-once"), "the key is shown once");
  // 닫기 before copying: asked once, still open.
  await t.click('dialog [data-act="close"]');
  assert.equal(t.$("dialog").open, true);
  assert.match(t.text("dialog .secret-warn"), /아직 복사하지 않았어요/);
  // The second time it closes, the key is gone from the page, and back is not spent on an empty step.
  await t.click('dialog [data-act="close"]');
  await t.settle(10);
  assert.equal(t.$("dialog").open, false);
  assert.equal(t.$("dialog").innerHTML, "");
  assert.ok(!t.window.history.state?.dialog, "the box's step was taken back");
});

test("[RVA-05] 인앱 업데이트 기준: an empty box keeps the value, and a change is asked with what it reaches", async () => {
  const { env, call } = await world();
  const t = await signedIn(env);
  await t.go("#/loop");
  const form = t.$('form[data-form="loop-switches"]');
  assert.ok(form, "the switches form");
  assert.match(t.text('form[data-form="loop-switches"]'), /판 번호는 앱 설정/);
  await t.type("#ls-urgent", "");
  await t.type("#ls-flex", "40");
  form.requestSubmit();
  await t.settle(20);
  assert.match(t.text("dialog"), /인앱 업데이트 기준을 바꿀까요/);
  assert.match(t.text("dialog .confirm-rows"), /0 → 40/);
  assert.doesNotMatch(t.text("dialog .confirm-rows"), /긴급/, "the empty one is not changed");
  t.$('dialog form[data-form="confirm"]').requestSubmit();
  await t.settle(30);
  const owner = await signIn(call);
  const sw = (await call("GET", "/api/admin/loop", undefined, owner)).data.switches;
  assert.deepEqual([sw.urgentMin, sw.flexibleMin], [0, 40]);
  // Letters in a box: said at the box, nothing sent.
  await t.go("#/loop");
  t.$("#ls-flex").setAttribute("type", "text");
  await t.type("#ls-flex", "4o");
  t.$('form[data-form="loop-switches"]').requestSubmit();
  await t.settle(10);
  assert.equal(t.$("#ls-flex").getAttribute("aria-invalid"), "true");
  assert.match(t.text("#ls-flex-error"), /숫자/);
});

test("[RVA-02] no browser prompt or confirm is left: the app's own boxes ask instead", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  let asked = 0;
  t.window.prompt = () => { asked++; return null; };
  t.window.confirm = () => { asked++; return false; };
  // A setting changed and not saved: the app asks in its own box, and the address stays.
  await t.go("#/sla");
  const step = t.$('[data-act="sla-step"]');
  assert.ok(step, "a step button on 답 목표");
  await t.click(step);
  t.window.location.hash = "#/home";
  await t.settle(20);
  assert.match(t.text("dialog"), /저장하지 않은 설정이 있어요/);
  assert.match(t.window.location.hash, /#\/sla/);
  t.$('dialog form[data-form="confirm"]').requestSubmit();
  await t.settle(30);
  assert.match(t.window.location.hash, /#\/home/);
  // 기능 조건 창의 '이 앱 판 이상만': 새 창 대신 같은 창 안에서 펴요. 적던 조건·이유는 그대로예요.
  await t.go("#/flags/billing");
  await t.click('[data-act="flag-cond"]');
  t.$("#fc-why").value = "적던 이유";
  await t.click('[data-act="flag-minapp"]');
  assert.equal(t.$("#flag-minapp-row").hidden, false);
  await t.type("#flag-minapp-n", "5o");
  await t.click('[data-act="flag-minapp-put"]');
  assert.equal(t.$("#flag-minapp-n").getAttribute("aria-invalid"), "true");
  await t.type("#flag-minapp-n", "54");
  await t.click('[data-act="flag-minapp-put"]');
  assert.match(t.$("#flag-cond").value, /app >= 54/);
  assert.ok(t.$('dialog form[data-form="flag-cond"]'), "still the conditions box");
  assert.equal(t.$("#fc-why").value, "적던 이유");
  assert.equal(asked, 0);
  // The app file itself has none left.
  assert.doesNotMatch(APP_JS, /window\.prompt\(|[^.\w]confirm\("/);
});

test("[RVA-01] the kept answers on the phone: only lists and numbers, not a question, money or an export, and not past a day", async () => {
  const SW = await adminFile("/admin/sw.js").text();
  const handlers = {};
  const store = new Map();
  const cache = {
    put: async (req, res) => { store.set(req.url, res); },
    match: async (req) => store.get(req.url) ?? null,
    keys: async () => [...store.keys()].map((u) => new Request(u)),
    delete: async (req) => store.delete(req.url ?? req),
  };
  const self = { location: { origin: ORIGIN }, addEventListener: (k, f) => { handlers[k] = f; }, clients: { claim: async () => {} }, skipWaiting: async () => {} };
  const caches = { open: async () => cache, keys: async () => [], delete: async () => true, match: async () => null };
  let online = true;
  const fetch = async () => {
    if (!online) throw new TypeError("offline");
    return new Response("{\"ok\":1}", { status: 200, headers: { "content-type": "application/json" } });
  };
  new Function("self", "caches", "fetch", SW)(self, caches, fetch);
  const ask = async (path) => {
    let answer = null;
    const waits = [];
    handlers.fetch({ request: new Request(`${ORIGIN}${path}`), respondWith: (p) => { answer = p; }, waitUntil: (p) => waits.push(p) });
    const res = answer ? await answer.catch(() => null) : null;
    await Promise.all(waits);
    return res;
  };
  for (const p of ["/api/admin/badges", "/api/admin/inquiries?status=open", "/api/admin/inquiries/abcdefghijklmnopqrstuv", "/api/admin/money",
    "/api/admin/ledger/export.csv", "/api/admin/inquiries?status=open&q=%EC%95%8C%EB%9E%8C", "/api/admin/audit", "/api/admin/admins"]) await ask(p);
  const kept = [...store.keys()].map((u) => new URL(u).pathname + new URL(u).search);
  assert.deepEqual(kept.sort(), ["/api/admin/badges", "/api/admin/inquiries?status=open"]);
  // Offline: the kept list answers, marked as the phone's copy; a day later it does not.
  online = false;
  const res = await ask("/api/admin/badges");
  assert.equal(res?.headers.get("x-admin-cache"), "1");
  const old = store.get(`${ORIGIN}/api/admin/badges`);
  const headers = new Headers(old.headers);
  headers.set("x-admin-kept", String(Date.now() - 25 * 3600e3));
  store.set(`${ORIGIN}/api/admin/badges`, new Response("{}", { status: 200, headers }));
  assert.equal(await ask("/api/admin/badges"), null, "older than a day is not shown");
  assert.equal(await ask("/api/admin/money"), null, "never kept, never shown offline");
});

test("[RVA-03] 경고·쉬기: Enter in the line sends nothing, a warning asks with who·what·words first, and 되돌리기 takes it back", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-10T03:00:00Z") });
  const { env, call } = await world({ plaza: true });
  const owner = await signIn(call);
  await call("POST", "/api/admin/policy", { version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험", confirm: true }, owner);
  const a = await call("POST", "/api/reports", report(2, { company: "마을화학" }), from(2));
  const post = (await call("POST", "/api/posts", { device: device(3), company: a.data.company.id, setup: a.data.setup, body: "광고 글이에요 셋" }, from(3))).data.id;
  const u = await signedIn(env);
  await u.go("#/community?show=limits");
  const find = u.$('form[data-form="care-find"]');
  find.elements.q.value = post;
  find.requestSubmit();
  await u.settle(40);
  await u.idle();
  const form = u.$('form[data-form="care-act"]');
  assert.ok(form, "the person and what to do next");
  assert.equal(form.querySelectorAll("[type=submit]").length, 0, "no submit button: Enter cannot send a warning");
  form.elements.reason.value = "spam";
  form.elements.note.value = "광고는 쉬어 가요";
  form.requestSubmit();
  await u.settle(20);
  const notes = () => call("GET", "/api/admin/care", undefined, owner).then((r) => r.data.warnings.length);
  assert.equal(await notes(), 0, "Enter sent nothing");
  await u.click('[data-act="care-do"][data-kind="warn"]');
  assert.match(u.text("dialog h2"), /이 경고를 보낼까요/);
  assert.match(u.text("dialog .confirm-rows"), /누구에게.*무엇을.*광고·도배.*앱에 보일 글.*광고는 쉬어 가요/);
  u.$('dialog form[data-form="confirm"]').requestSubmit();
  await u.settle(40);
  assert.equal(await notes(), 1);
  assert.ok(u.$(".toast [data-undo]"), "15초 되돌리기");
  await u.click(".toast [data-undo]");
  await u.settle(40);
  assert.equal(await notes(), 0, "taken back: nothing went to the phone");
  // The same reason again within the days asks plainly that it counts twice.
  await call("POST", "/api/admin/care/warn", { post, reason: "spam" }, owner);
  await u.go("#/home");
  await u.go("#/community?show=limits");
  await u.settle(40);
  await u.idle();
  const again = u.$('form[data-form="care-act"]');
  again.elements.reason.value = "spam";
  await u.click('[data-act="care-do"][data-kind="warn"]');
  assert.match(u.text("dialog h2"), /같은 이유로 경고를 이미 보냈어요/);
  assert.doesNotMatch(u.text("#main"), /post:/, "no inner names on the screen");
});

test("[RVA-11] 연기 시험: odd words in questions and a company with two versions leave every screen and every detail open", async () => {
  const { env, call } = await world();
  const owner = await signIn(call);
  // 검토 ADMX-23: words that are names on every object, emoji, control characters, a long line.
  const odd = ["constructor", "toString __proto__ valueOf", "급여 화면에서 \"is not a constructor\" 오류", "😀🌙 야간 근무 🙏", "줄\u0000바꿈\u0007소리‮거꾸로", "가".repeat(1900)];
  for (const [i, text] of odd.entries()) {
    await call("POST", "/api/inquiries", { v: 1, device: device(40 + i), kind: i % 2 ? "bug" : "idea", text, info: { app: "1.39.0 (53)" } }, from(40 + i));
  }
  // 검토 ADMX-03: '더해지는'의 '해지' 같은 평범한 말은 결제·환불 기록으로 잡히지 않아요.
  await call("POST", "/api/inquiries", { v: 1, device: device(49), kind: "idea", text: "근무가 더해지는 날을 한눈에 보고 싶어요", info: { app: "1.39.0 (53)" } }, from(49));
  await call("POST", "/api/admin/dev", { title: "급여 화면에서 \"is not a constructor\" 오류", body: "constructor" }, owner);
  // 검토 ADMX-02(T24-21): one company with two versions from two departments.
  const first = await call("POST", "/api/reports", report(5, { company: "두판정밀", dept: "1공장" }), from(5));
  const other = { ...setup(), shiftTypes: setup().shiftTypes.map((s) => (s.name === "주간" ? { ...s, start: "08:00", end: "20:00" } : s)) };
  const second = await call("POST", "/api/reports", report(6, { company: "두판정밀", dept: "2공장", setup: other }), from(6));
  const company = first.data.company.id;
  for (const r of [first, second]) assert.equal((await call("POST", `/api/admin/groups/${company}/${r.data.setup}/adopt`, {}, owner)).status, 200);
  const t = await signedIn(env);
  const inquiry = (await call("GET", "/api/admin/inquiries?status=open", undefined, owner)).data.items?.[0]?.id;
  const dev = (await call("GET", "/api/admin/dev?status=all", undefined, owner)).data.items?.[0]?.id;
  const screens = [...Object.keys(adminLib().TABS), `companies/${company}`, ...(inquiry ? [`inquiries/${inquiry}`] : []), ...(dev ? [`dev/${dev}`] : [])];
  for (const s of screens) {
    await t.go(`#/${s}`);
    assert.ok(!t.$("#main .failed"), `${s} loaded`);
    assert.ok(t.text("#main").length > 10, `${s} shows something`);
  }
  const billing = (await call("GET", "/api/admin/inquiries?status=open&billing=1", undefined, owner)).data;
  assert.ok(!JSON.stringify(billing).includes("더해지는"), "an ordinary word is not a billing question");
  await t.go(`#/companies/${company}`);
  assert.match(t.text("#main"), /1공장/);
  assert.match(t.text("#main"), /2공장/);
  assert.deepEqual(t.errors, []);
  assert.deepEqual(t.broken, []);
});

test("[RVA-23] 새 공지: 틀에서 시작하면 글·날짜·단추가 차고, 단추와 대상이 서버에 그대로 가요", async () => {
  const { env, call } = await world();
  const t = await signedIn(env);
  await t.go("#/notices");
  await t.click('[data-act="notice-new"]');
  const form = t.$('form[data-form="notice"]');
  assert.match(t.text("#nt-who-sum"), /모두에게/);
  assert.ok(t.$("#nt-act-more").hidden, "no button yet: no button boxes");
  assert.match(t.text("#nt-preview .np-acts"), /다시 보지 않기\s*확인/, "the phone's two buttons");
  // 틀: 알람 점검 부탁 fills the words, the button and who it is for.
  await t.click('[data-act="notice-template"][data-key="alarm"]');
  assert.match(t.$("#nt-title").value, /알람이 잘 울리는지/);
  assert.equal(t.$("#nt-act").value, "ALARM_CHECK");
  assert.ok(!t.$("#nt-act-more").hidden, "the label box opens with a button");
  assert.match(t.text("#nt-preview .np-acts .on"), /알람 점검 열기/, "the app's own words when the label is empty");
  assert.match(t.text("#nt-who-sum"), /알람 쓰는 분/);
  // Typed words are not thrown away by the next 틀 at once.
  await t.type("#nt-title", "직접 쓴 제목");
  await t.click('[data-act="notice-template"][data-key="check"]');
  assert.equal(t.$("#nt-title").value, "직접 쓴 제목", "asked first");
  assert.match(t.text(".tpl-ask"), /한 번 더 누르면/);
  await t.click('[data-act="notice-template"][data-key="check"]');
  assert.match(t.$("#nt-title").value, /서버 점검/);
  // A web address: only https, said at the box.
  t.$("#nt-act").value = "URL";
  t.$("#nt-act").dispatchEvent(new t.window.Event("input", { bubbles: true }));
  await t.settle();
  assert.ok(!t.$("#nt-url-box").hidden, "the address box opens");
  await t.type("#nt-url", "http://example.com/x");
  await t.type("#nt-label", "자세히");
  for (const b of t.$$('input[name="workTypes"]')) b.checked = ["SHIFT", "DAY"].includes(b.value);
  await t.type("#nt-makers", "Samsung, google");
  form.requestSubmit();
  await t.settle(20);
  assert.equal(t.$("#nt-url").getAttribute("aria-invalid"), "true");
  assert.match(t.text("#nt-url-error"), /https/);
  await t.type("#nt-url", "https://example.com/x");
  form.requestSubmit();
  await t.settle(20);
  assert.match(t.text(".sure"), /교대·일근 · samsung·google에 맞는 분/, "the ask says who");
  form.requestSubmit();
  await t.settle(30);
  const owner = await signIn(call);
  const n = (await call("GET", "/api/admin/notices", undefined, owner)).data.notices[0];
  assert.deepEqual(n.action, { kind: "URL", url: "https://example.com/x", label: "자세히" });
  assert.deepEqual(n.audience, { makers: ["samsung", "google"], workTypes: ["SHIFT", "DAY"] });
  assert.match(t.text("#main"), /교대·일근 · samsung·google · 단추 '자세히'/, "the list says who and which button");
  // 고치기 opens with them in place.
  await t.click(`[data-act="notice-edit"][data-id="${n.id}"]`);
  // (happy-dom keeps the last picked option of an earlier select with the same id; Chromium reads 'URL' here.)
  assert.ok(t.$('#nt-act option[value="URL"]').hasAttribute("selected"));
  assert.equal(t.$("#nt-url").value, "https://example.com/x");
  assert.ok(t.$('input[name="workTypes"][value="SHIFT"]').checked);
  assert.ok(t.$("details.fold-field").open, "who it is for is open when it is set");
  assert.deepEqual(t.errors, []);
});

test("[RVA-19][RVA-21] 허브의 '모두' 창, 파일 고르기 단추, 폰 폭의 판 알약", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.go("#/review");
  await t.click("#hub-all");
  assert.match(t.text("dialog h2"), /사람과 글의 모든 화면/);
  assert.ok(t.$('dialog a.chip.pick[data-tab="review"][aria-current="page"]'), "here is marked");
  await t.click('dialog [data-act="hub-go"][data-tab="community"]');
  await t.settle(20);
  assert.ok(!t.$("dialog").open, "the box closed");
  assert.equal(t.window.location.hash.split("?")[0], "#/community");
  // 레시피 › 출시 대기의 재료 파일: the app's button, not the browser's.
  await t.go("#/recipes?tab=waiting");
  const pick = t.$(".filepick");
  if (pick) {
    assert.match(pick.textContent, /파일 고르기/);
    assert.match(pick.textContent, /아직 고르지 않았어요/);
    assert.ok(pick.querySelector('input[type="file"]'), "still a file box for the form");
  }
  // The server's version is in the title bar too (shown on a phone instead of the thin line).
  const mini = t.$("#dl-mini");
  assert.ok(mini && !mini.hidden, "the pill is filled");
  assert.match(mini.getAttribute("aria-label"), /로컬 서버|지금 도는 서버/);
  assert.deepEqual(t.errors, []);
});

test("[RVA-18][RVA-20][RVA-22] 막대 배지는 한 줄, 대비 값, 마우스 올림은 마우스 화면에서만", async () => {
  const css = await adminFile("/admin/app.css").text();
  assert.match(css, /\.badge\{white-space:nowrap;overflow-wrap:normal/, "two digits never break into two lines");
  // Every :hover sits under (hover:hover): a tap leaves no hover colour on a phone (the 'dull' filled button in #465's photos).
  const stack = [];
  let bare = 0;
  for (let i = 0, start = 0; i < css.length; i++) {
    if (css[i] === "{") {
      const head = css.slice(start, i);
      if (head.includes(":hover") && !head.trim().startsWith("@") && !stack.some(Boolean)) bare++;
      stack.push(head.includes("hover:hover"));
      start = i + 1;
    } else if (css[i] === "}") {
      stack.pop();
      start = i + 1;
    } else if (css[i] === ";") start = i + 1;
  }
  assert.equal(stack.length, 0, "braces balance");
  assert.equal(bare, 0, "no :hover outside (hover:hover)");
  assert.match(css, /--on-bad:#2B0D0A/, "dark red badge words");
  assert.doesNotMatch(css, /font-size:\.(6\d|7[0-4])rem/, "nothing under 12px");
});

// ---- 2.0.0 [RVQ-16·17·18] 검토 U21-07·U21-08·I21b-01·I21b-02: 백업 되살리기 연습, 데이터 요청 대행, 보안 한 장 ----

/** R2 as the backup uses it (put·get·list·delete), in memory. */
function memoryR2() {
  const store = new Map();
  return {
    store,
    async put(key, body) { store.set(key, typeof body === "string" ? new TextEncoder().encode(body) : new Uint8Array(body)); },
    async get(key) {
      const v = store.get(key);
      return v ? { body: new Blob([v]).stream(), text: async () => new TextDecoder().decode(v) } : null;
    },
    async list({ prefix = "", delimiter } = {}) {
      const keys = [...store.keys()].filter((k) => k.startsWith(prefix)).sort();
      if (!delimiter) return { objects: keys.map((key) => ({ key })), truncated: false };
      return { objects: [], truncated: false, delimitedPrefixes: [...new Set(keys.map((k) => prefix + k.slice(prefix.length).split(delimiter)[0] + delimiter))] };
    },
    async delete(keys) { for (const k of [].concat(keys)) store.delete(k); },
  };
}

test("[RVQ-16] 검토 U21-08: 백업 shows what it seals, and 지금 연습하기 reads the last day back with rows beside today's", async (t) => {
  offline(t);
  const { env, call } = server({ BACKUP: memoryR2() });
  const owner = await signIn(call);
  for (let i = 0; i < 40; i++) {
    const r = (await call("POST", "/api/admin/backup/run", {}, owner)).data;
    if (r.skipped === "done" || r.done === r.of) break;
  }
  const u = await asOwner(env, t);
  await u.go("#/backup");
  const cards = [...u.$$("#main section.card")];
  assert.match(cards[0].textContent, /비밀 칸은 서버 비밀값 RATE_SALT로 잠가요/);
  assert.match(u.text('#main [data-key="backup-rules"] summary'), /잠그는 칸 \d+개 표 · 빼는 표 \d+개/);
  const drill = cards.find((c) => /되살리기 연습/.test(c.querySelector("h2")?.textContent ?? ""));
  assert.match(drill.textContent, /아직 안 함/);
  await u.click('#main [data-act="backup-drill"]');
  await u.idle();
  const after = [...u.$$("#main section.card")].find((c) => /되살리기 연습/.test(c.querySelector("h2")?.textContent ?? ""));
  assert.match(after.textContent, /되살릴 수 있음/);
  assert.match(after.textContent, /마지막 연습 .* · 표 \d+개 · [\d,]+줄/);
  assert.match(after.textContent, /백업을 다시 읽음 · (잠근 칸 \d+개를 지금 열쇠로 열어 봄|잠근 칸은 아직 없어요)/);
  // 표가 아니라 줄바꿈 되는 목록(좁은 폭·큰 글자에서 머리글이 세로로 서지 않게).
  const row = [...after.querySelectorAll(".drill-rows li")].find((li) => li.querySelector("b")?.textContent === "settings");
  assert.match(row.querySelector("span").textContent, /^백업 \d+ · 오늘 \d+$/, "백업 · 오늘 DB(목록과 다를 때만 목록)");
  assert.match(u.text("#main"), /R2 백업으로 되살리기: .*tools\/backup-open\.mjs/);
  assert.ok(!u.text("#main").includes("test-salt"), "the secret's name, never its value");
  assert.deepEqual(u.errors, []);
});

test("[RVQ-17] 검토 U21-07·I21b-02: 데이터 요청 › 대신 하기 exports, then forgets after one more ask, and the proof stays on the request", async (t) => {
  offline(t);
  const { env, call, DB } = server();
  const me = device(31);
  await call("POST", "/api/inquiries", { v: 1, device: me, kind: "other", text: "앱을 지웠어요. 제 자료를 모두 지워 주세요", info: { app: "2.0.0 (54)" } }, from(31));
  await call("POST", "/api/inquiries", { v: 1, device: me, kind: "bug", text: "예전 문의예요", info: { app: "2.0.0 (54)" } }, from(31));
  const ask = DB.db.prepare("SELECT q.id FROM inquiries q JOIN inquiry_messages m ON m.inquiry_id = q.id WHERE m.text LIKE '%모두 지워%'").get().id;
  const owner = await signIn(call);
  const made = (await call("POST", "/api/admin/data-requests", { kind: "correct", inquiry: ask, summary: "자료 삭제 요청" }, owner)).data.id;
  const u = await asOwner(env, t);
  await u.go("#/requests");
  await u.click(`#main [data-act="request-run"][data-id="${made}"]`);
  let form = u.$('dialog form[data-form="request-run"]');
  assert.match(form.textContent, /이 요청에 묶인 문의를 보낸 설치의 자료로 해요/);
  assert.ok(!form.querySelector('[name="code"]'), "no code asked: the question shows the install");
  assert.equal(form.querySelector('[name="do"]:checked').value, "forget", "삭제 요청이면 지우기가 먼저");
  // 열람 first: a file comes down.
  let saved = null;
  u.window.URL.createObjectURL = (blob) => { saved = blob; return "blob:x"; };
  u.window.URL.revokeObjectURL = () => {};
  form.querySelector('[name="do"][value="export"]').checked = true;
  form.requestSubmit();
  await u.idle();
  const file = JSON.parse(await saved.text());
  assert.equal(file.request, made);
  assert.ok(file.data.inquiries.length >= 2);
  assert.match(u.text("#main .proof"), /열람 파일 내보냄: \d+개 표 \d+줄/);
  // 지우기 asks once more, saying what stays.
  await u.click(`#main [data-act="request-run"][data-id="${made}"]`);
  form = u.$('dialog form[data-form="request-run"]');
  form.requestSubmit();
  await u.settle();
  const confirm = u.$('dialog form[data-form="confirm"]');
  assert.match(confirm.textContent, /이 설치의 자료를 모두 지울까요\?/);
  assert.match(confirm.textContent, /요청 문의 하나\(답을 보내려고\)/);
  confirm.requestSubmit();
  await u.idle();
  const proof = u.text("#main .proof");
  assert.match(proof, /지움: \d+개 표 \d+줄 · 남음 inquiries 1\(요청 문의\(답을 보내려고\)\)/);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM inquiries WHERE device = (SELECT device FROM inquiries WHERE id = ?)").get(ask).n, 1);
  assert.deepEqual(u.errors, []);
});

test("[RVQ-18] 검토 I21b-01: 보안 한 장 puts what to fix first, each row with where to fix it, and never a secret's value", async (t) => {
  offline(t);
  const { env } = server({ INTEGRITY_MODE: "watch", ADMIN_2FA_RESET: "1" });
  const u = await asOwner(env, t);
  await u.go("#/security");
  const rows = [...u.$$("#main .security-rows li")];
  assert.ok(rows.length >= 9, `${rows.length} rows`);
  const tones = rows.map((li) => li.className.replace("sec-", ""));
  assert.deepEqual(tones, [...tones].sort((a, b) => ["bad", "warn", "ok"].indexOf(a) - ["bad", "warn", "ok"].indexOf(b)), "고치기 → 살펴보기 → 괜찮아요");
  const byName = (re) => rows.find((li) => re.test(li.querySelector("b").textContent));
  assert.match(byName(/최고 관리자 2단계/).textContent, /ADMIN_2FA_RESET이 켜져 있어/);
  assert.match(byName(/최고 관리자 2단계/).querySelector(".pill").textContent, /고치기/);
  assert.match(byName(/ADMIN_PASSWORD/).textContent, /처음 본 날 \d{4}-\d{2}-\d{2}부터 그대로/);
  assert.match(byName(/SEAL_KEY/).textContent, /RATE_SALT로 잠가요/);
  assert.match(byName(/Play 무결성/).textContent, /보기만 해요/);
  assert.equal(byName(/하루 백업/).querySelector(".fix a").getAttribute("href"), "#/backup");
  const text = u.text("#main");
  for (const v of [PASSWORD, "test-salt"]) assert.ok(!text.includes(v), "no secret's value on the page");
  assert.match(u.text("#main .card-head"), /고칠 것 \d+/);
  assert.deepEqual(u.errors, []);
});

test("[RVR-20] 공모 다시 고르기: 저장 전 확인 창이 새로 생기는 보상과 빠지는 작품의 지급된 보상을 말하고, 고르기를 눌러야 저장돼요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-30T03:00:00Z") });
  const { env, call } = await world({ plaza: true });
  const owner = await signIn(call);
  await call("POST", "/api/admin/village/contests", { style: "SHIFT", theme: "야간 근무용 어두운 테마" }, owner);
  const look = { kind: "shiftLook", items: [{ name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] };
  const make = async (n, title) => (await call("POST", "/api/recipes", { device: device(n), scope: "public",
    recipe: { format: "bamnat-recipe", v: 1, title, summary: "어두운 달력", styles: ["SHIFT"], pieces: [look] } }, from(n))).data.code;
  const a = await make(1, "야간 다크");
  const b = await make(2, "밤 하늘");
  for (const [n, code] of [[1, a], [2, b]]) await call("POST", "/api/village/contest/enter", { device: device(n), style: "SHIFT", code }, from(n));
  await call("POST", "/api/admin/village/contests/pick", { month: "2026-11", style: "SHIFT", codes: [a] }, owner);
  for (const w of (await call("GET", "/api/admin/rewards?status=owed&kind=maker", undefined, owner)).data.rewards) {
    await call("POST", `/api/admin/rewards/${w.id}`, { status: "paid" }, owner);
  }
  t.mock.timers.setTime(Date.parse("2026-12-03T03:00:00Z")); // 12월: 11월 작품을 골라요
  const u = await signedIn(env);
  await u.go("#/community?show=village");
  await u.idle();
  const box = (code) => u.$(`input[data-pick="2026-11|SHIFT"][value="${code}"]`);
  box(a).checked = false;
  box(b).checked = true;
  await u.click('[data-act="contest-pick"][data-id="2026-11|SHIFT"]');
  await u.settle(30);
  assert.match(u.text("dialog h2"), /이달의 테마로 고를까요/);
  assert.match(u.text("dialog .confirm-rows"), /새로 생기는 보상.*밤 하늘/);
  assert.match(u.text("dialog .confirm-rows"), /빠지는 작품의 지급된 보상.*야간 다크.*거두지 못해요/);
  const picks = async () => (await call("GET", "/api/admin/village/contests", undefined, owner)).data.items.find((x) => x.month === "2026-11" && x.style === "SHIFT").picks;
  assert.deepEqual(await picks(), [a], "nothing saved before 고르기");
  u.$('dialog form[data-form="confirm"]').requestSubmit();
  await u.settle(40);
  assert.deepEqual(await picks(), [b]);
  assert.deepEqual(u.errors, []);
  assert.deepEqual(u.broken, []);
});

test("[RVA-25] 장터: 줄마다 주 동작 하나와 ⋯ 메뉴, 50개씩 이어 받기, 이름으로 찾기", async (t) => {
  offline(t);
  const s = server({ plaza: true });
  const look = { kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] };
  // 2.0.0 [RVA-48] 도약 42: 40개씩 그리던 것을 서버에서 50개씩 받아요.
  for (let i = 1; i <= 55; i++) {
    const recipe = { format: "bamnat-recipe", v: 1, title: `세트 ${String(i).padStart(2, "0")}`, summary: "야간이 잘 보여요", styles: ["SHIFT"],
      cover: { a: 4282339765, b: 4288423856, dir: 1, emoji: "🌙" }, pieces: [look] };
    const made = await s.call("POST", "/api/recipes", { device: device(100 + i), recipe, scope: "public" }, from(100 + i));
    assert.equal(made.status, 201, `recipe ${i}: ${JSON.stringify(made.data)}`);
  }
  const u = await asOwner(s.env, t);
  await u.go("#/recipes");
  const rows = () => u.$$("#main ul.recipe-rows>li");
  // Whichever tab holds them (공개 or 검토 대기), go there.
  for (const key of ["visible", "pending"]) {
    if (rows().length) break;
    const tabBtn = u.$(`#main [data-act="recipe-tab"][data-key="${key}"]`);
    if (tabBtn) {
      await u.click(tabBtn);
      await u.idle();
    }
  }
  assert.equal(rows().length, 50, "50 at a time");
  assert.match(u.text('#main [data-act="recipe-more"]'), /50\/55/);
  for (const li of rows().slice(0, 3)) {
    assert.ok(li.querySelector("details.row-more>summary[aria-label]"), "⋯ for the rest");
    assert.ok(li.querySelector("details.row-more .menu .btn.danger"), "지우기 sits in the menu");
    assert.ok(!li.querySelector(".btn.primary"), "no filled button on a row");
    // 알약은 종류 하나만(탭 아홉 개를 붙인 605px 알약이 380·200%에서 카드 밖으로 잘렸어요), 탭은 아래 작은 글로.
    for (const p of li.querySelectorAll(".row-main>.pill")) assert.doesNotMatch(p.textContent, /·/, p.textContent);
  }
  // 줄 가운데에서 연 ⋯ 메뉴는 열 때 재어 화면 안으로 옮겨요(--menu-dx).
  const css = await adminFile("/admin/app.css").text();
  assert.match(css, /details\.row-more>\.menu\{[^}]*transform:translateX\(var\(--menu-dx,0\)\)/);
  assert.match(await adminFile("/admin/app.js").text(), /setProperty\("--menu-dx"/);
  await u.click('#main [data-act="recipe-more"]');
  await u.settle();
  assert.equal(rows().length, 55);
  assert.ok(!u.$('#main [data-act="recipe-more"]'), "all shown");
  await u.type("#search", "세트 07");
  await new Promise((r) => setTimeout(r, 300));
  await u.settle();
  assert.equal(rows().length, 1, "found by title");
  assert.deepEqual(u.errors, []);
});

test("[RVA-24] 활동 기록 머리: 모든 사람·나 한 줄, 누가·종류·기간은 거르기 안, CSV는 단추 하나", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/audit");
  assert.deepEqual(u.$$('#main .list-head [data-act="audit-who"]').map((b) => b.textContent.trim()), ["모든 사람", "나"]);
  for (const id of ["au-actor", "au-kind", "au-from", "au-to"]) assert.ok(u.$(`#main .filter-panel #${id}`), `${id} behind 거르기`);
  assert.ok(u.$('#main .list-head [data-act="audit-csv"][aria-label="CSV로 받기"]'));
  await u.click('#main [data-act="audit-who"][data-key="me"]');
  await u.idle();
  assert.equal(u.$('#main [data-act="audit-who"][data-key="me"]').getAttribute("aria-pressed"), "true");
  assert.match(u.window.location.hash, /actor=me/, "kept in the address");
  assert.deepEqual(u.errors, []);
});

test("[RVA-25] 토론 카드: 결정 단추는 왼쪽 한 줄, 쓰기 막기·경고·토론 전부·지우기는 ⋯", async (t) => {
  offline(t);
  const s = server({ plaza: true });
  const a = await s.call("POST", "/api/reports", report(1), from(1));
  await s.call("POST", "/api/posts", { device: device(2), company: a.data.company.id, setup: a.data.setup, body: "단추 줄을 볼 글" }, from(2));
  const u = await asOwner(s.env, t);
  await u.go("#/community?show=all");
  const acts = u.$("#main article.card .actions.start.row-acts");
  assert.ok(acts, "one left-aligned row");
  assert.deepEqual([...acts.children].filter((c) => c.matches(".btn")).map((b) => b.textContent.trim()), ["가리기"]);
  const menu = acts.querySelector("details.row-more .menu");
  assert.deepEqual([...menu.querySelectorAll(".btn")].map((b) => b.textContent.trim()), ["30일 쓰기 막기", "경고·쉬기", "이 토론 전부", "지우기"]);
  assert.ok(menu.querySelector('.btn.danger[data-act="post-delete"]'));
  assert.ok(![...acts.querySelectorAll(".btn")].some((b) => b.matches(".primary")), "no filled button on a card");
  assert.deepEqual(u.errors, []);
});

test("[RVA-29] 원격 갱신: 휴대폰이 20대보다 적으면 비율 대신 'N대 중 M대'와 자료 부족", async (t) => {
  const dir = new URL("../../../../../remote/v1/", import.meta.url);
  const real = globalThis.fetch;
  globalThis.fetch = async (url) => {
    const name = String(url).split("/").pop();
    try {
      return new Response(readFileSync(new URL(name, dir)));
    } catch {
      return new Response("", { status: 404 });
    }
  };
  t.after(() => { globalThis.fetch = real; });
  const { env, DB } = await world();
  const day = (n) => new Date(Date.now() + 9 * 3600e3 - n * 86400e3).toISOString().slice(0, 10);
  DB.db.prepare("INSERT INTO usage_days (day, app, phones) VALUES (?, '1.39.0', 2), (?, '9.9.9', 1)").run(day(1), day(1));
  const u = await signedIn(env);
  await u.go("#/gates");
  await u.settle(40);
  const card = u.$$("#main section.card").find((c) => /원격 갱신/.test(c.textContent));
  assert.ok(card, "the card");
  assert.match(card.textContent, /3대 중 \d대/);
  assert.match(card.textContent, /자료 부족/);
  assert.doesNotMatch(card.querySelector("ul.rows").textContent, /\d%/, "no share of three phones");
  assert.deepEqual(u.errors, []);
});

test("[RVA-30] 문의 상세: 1024보다 좁으면 목록 칸 없이 '‹ 이전 1/N 다음 ›', 주소로 바로 열어도", async (t) => {
  offline(t);
  const { env } = await world();
  const first = await signedIn(env);
  await first.go("#/inquiries");
  const id = first.$('#main a.row[href^="#/inquiries/"]').getAttribute("href").split("/").pop().split("?")[0];
  const u = await signedIn(env, { width: 900 });
  await u.go(`#/inquiries/${id}`);
  await u.settle(20);
  assert.ok(!u.$("#main .split .pane-list"), "no list column at 900");
  assert.match(u.text("#main .stepnav"), /이전 \d+\/3 ?다음/);
  const wide = await signedIn(env, { width: 1100 });
  await wide.go(`#/inquiries/${id}`);
  assert.ok(wide.$("#main .split .pane-list"), "the list beside it from 1024");
  assert.deepEqual([...u.errors, ...wide.errors], []);
});

test("[RVA-31] 오늘: 첫 문장은 아래 셋에 든 종류를 모두 말하고, 정할 판 칸은 그래프 대신 한 줄", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await signedIn(env);
  await u.go("#/home");
  await u.settle(20);
  const line = u.text("#today-hero-h");
  const words = { bad: "바로 볼 것", warn: "살펴볼 것", todo: "할 일" };
  for (const li of u.$$("#main .today-three>li")) {
    const kind = Object.keys(words).find((k) => li.classList.contains(k));
    assert.ok(line.includes(words[kind]), `"${line}" names ${words[kind]}`);
  }
  const tile = u.$$("#main .tile.kpi").find((x) => /정할 판/.test(x.textContent));
  if (tile) assert.ok(tile.querySelector(".tile-go"), "a line where the chart would be");
  // The account screen counts the hubs from the same list the menu uses.
  await u.go("#/account");
  assert.match(u.text("#main"), /1~5로 허브를/);
  assert.deepEqual(u.errors, []);
});

test("[RVA-24][RVA-25][RVA-31] 380·큰 글자 모양: 보기 줄 한 줄 밀기, 기다리는 칩 둘씩, 카드 ⋯ 오른쪽으로, 큐 막대는 폰 아래, 미리 보기 칸 맞춤", async () => {
  const css = await adminFile("/admin/app.css").text();
  assert.match(css, /\.list-head>\.segmented\{[^}]*flex-wrap:nowrap;overflow-x:auto/);
  assert.match(css, /@container waiting \(width < 14rem\)\{[^}]*\.waiting b\{flex-basis:100%\}/);
  assert.match(css, /\.actions\.start details\.row-more>\.menu\{left:0;right:auto\}/);
  assert.match(css, /@media \(max-width:599\.98px\)\{\s*\.queue-bar\{position:fixed;top:auto/);
  assert.match(css, /\.sim-grid>\.fieldset\{display:flex;flex-direction:column\}/);
  // [RVQ-47] 검토 HQ-88: 840~1023은 두 칸을 나란히(격자 대신 감기는 줄: 글자 200%면 칸 폭이 모자라 위아래로).
  assert.match(css, /@media \(min-width:840px\) and \(max-width:1023\.98px\)\{\s*\.cols\{display:flex;flex-wrap:wrap/);
  assert.match(css, /\.cols>:first-child\{flex:1\.6 1 18rem\}/);
});

test("[RVA-32] 숫자 표가 쪽을 옆으로 밀지 않아요: 표는 스크롤 상자 안, 숨긴 이름표도 그 상자를 기준으로", async (t) => {
  const css = await adminFile("/admin/app.css").text();
  // HQ-60: 광장 하루 글 표(폭 868)의 숨긴 이름표(.lab, position:absolute)가 상자 밖을 기준으로 놓여 840에서 쪽이 123px 밀렸어요.
  assert.match(css, /\.table-wrap\{position:relative;max-width:100%\}/);
  assert.match(css, /\.table-wrap \.lab\{left:0;top:0\}/);
  // 상자 안 표 칸은 낱말을 지켜요('투자 이야기'가 '이야/기'로 갈라졌어요).
  assert.match(css, /\.table-wrap table\.list th,\.table-wrap table\.list td\{overflow-wrap:break-word\}/);
  // 380·200%: 칸 최소 폭이 화면보다 넓은 격자가 없어요(15rem = 480px).
  assert.doesNotMatch(css, /repeat\(auto-(fit|fill),minmax\([0-9.]+rem,1fr\)\)/);
  assert.match(css, /\.card-head>\.btn\{max-width:100%;white-space:normal/);
  // Every number table in the app sits in a scroll box (마을 › 광장 효과, 숫자 › 요금제 구간, 회사 표…).
  offline(t);
  const { env } = await world();
  const u = await signedIn(env);
  const seen = {};
  for (const hash of ["#/community?show=village", "#/companies", "#/metrics"]) {
    await u.go(hash);
    await u.settle(20);
    seen[hash] = 0;
    for (const table of u.$$("#main table.list")) {
      seen[hash]++;
      assert.ok(table.closest(".table-wrap"), `${hash}: a table outside a scroll box`);
    }
  }
  // 광장 하루 글(HQ-60)과 회사 표는 시험 자료로도 그려져요. 숫자 › 요금제 구간은 자료가 있을 때만.
  assert.ok(seen["#/community?show=village"] > 0 && seen["#/companies"] > 0, JSON.stringify(seen));
  assert.deepEqual(u.errors, []);
});

test("[RVA-33] 오늘 '지금 기다리는 것': 숫자는 보통 색, 기한을 넘긴 칸에만 빨간 '넘김 N'을 글로", async (t) => {
  const css = await adminFile("/admin/app.css").text();
  // HQ-65(조건 6·14): 숫자마다 빨강이던 칩을 보통 색으로, 빨강은 넘김·오류를 말하는 글 한 조각에만.
  assert.match(css, /\.waiting a \.n\{color:var\(--fg\)/);
  assert.match(css, /\.waiting a \.late\{color:var\(--bad\)/);
  // 380·200%에서도 칩은 둘씩(허브는 많아야 넷이라 두 줄 안).
  assert.match(css, /\.waiting a\{[^}]*flex:1 1 calc\(50% - \.4rem\)/);
  // 같은 화면의 할 일 줄: 좁은 목록에서 넓은 화면의 한 줄 규칙보다 세게, 단추 줄은 바뀌고 제목은 두 줄까지(840·200% '내일 볼게요' 잘림).
  assert.match(css, /\.rows\.inbox \.row>\.row-side\{flex-wrap:wrap;justify-content:flex-start\}\.rows\.inbox \.row-main>b\{white-space:normal\}/);
  const t0 = Date.parse("2026-11-12T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: t0 });
  const { env } = await world();
  let u = await signedIn(env);
  const ask = '#waiting a[href^="#/inquiries"]';
  const release = '#waiting a[href^="#/launch"], #waiting a[href^="#/chores"], #waiting a[href^="#/flags"]';
  assert.ok(u.$$("#waiting a").length <= 4, "one chip per other hub");
  // 막 들어온 문의 셋·제보 하나는 기한 전: 숫자 4는 보통 색, 빨강 없음(서버가 보낸 '지금'으로 재요).
  assert.match(u.text(`${ask} .n`), /^4$/);
  assert.equal(u.$$("#waiting .late").length, 0, "nothing late yet: no red");
  assert.ok(u.$(release), "출시 has a chip");
  // 열흘 뒤: 문의 셋과 제보 하나가 모두 기한을 넘겨 '사람과 글'에만 '넘김 4'. 출시의 이달 운영은 달 끝이 기한이라 아직이에요.
  t.mock.timers.setTime(t0 + 10 * 864e5);
  u = await signedIn(env);
  assert.ok(u.$(ask).classList.contains("hot"));
  assert.match(u.text(`${ask} .n`), /^4$/);
  assert.equal(u.text(`${ask} .late`), "넘김 4");
  assert.equal(u.$(release).querySelector(".late"), null, "이달 운영 is not late: no red");
  for (const a of u.$$("#waiting a:not(.hot)")) assert.equal(a.querySelector(".late"), null);
  // 이 기기에서 '내일 볼게요'로 미룬 것은 빨강에서도 빠져요.
  u.$('#main .rows.inbox [data-act="inbox-snooze"]').click();
  await u.settle(20);
  assert.equal(u.text(`${ask} .late`), "넘김 3");
  assert.deepEqual(u.errors, []);
});

test("[RVA-34] 채운 단추는 화면에 하나(조건 2): 줄·카드·고르기 줄에는 채운 단추가 없어요", async (t) => {
  offline(t);
  const { env } = await world({ plaza: true });
  const u = await signedIn(env);
  const filled = () => u.$$("#main .btn.primary").filter((b) => !b.closest("details:not([open]), dialog, [hidden]"));
  // 앞: 오늘 11(할 일 줄마다 '보기'), 마을 12(주제 저장마다), 내 계정 4, 흐름·회사·공지·관리자 2.
  for (const hash of ["#/home", "#/flow", "#/community?show=village", "#/companies", "#/notices", "#/admins", "#/account", "#/inquiries", "#/recipes"]) {
    await u.go(hash);
    await u.settle(20);
    const got = filled().map((b) => b.textContent.trim());
    assert.ok(got.length <= 1, `${hash}: ${got.length} filled (${got.join(" | ")})`);
  }
  // 옛 앱 급여 끊기의 지금 고른 것은 채운 단추가 아니라 고르기 줄의 눌린 칸이에요.
  await u.go("#/notices");
  await u.settle(20);
  const pay = u.$('#main .segmented[aria-label="옛 앱 급여 끊기"]');
  if (pay) assert.equal(pay.querySelectorAll('[aria-pressed="true"]').length, 1);
  assert.deepEqual(u.errors, []);
});

test("[RVA-35] 의미색(조건 6): 메뉴 숫자와 기다리는 일 칸은 파랑, 빨강은 오늘 답 목표를 넘긴 칸과 '바로 볼 것'에만", async (t) => {
  const css = await adminFile("/admin/app.css").text();
  // 메뉴·탭 숫자는 기다리는 일의 수라 파랑 바탕(그 위 글자색).
  assert.match(css, /\.badge\{[^}]*background:var\(--accent\);color:var\(--on-accent\)/);
  assert.match(css, /\.badge\{color:var\(--on-accent\)\}/);
  // 기다리는 일 칸(hot)은 파란 점·보통 숫자, 빨강은 bad 칸에만, 어제보다 늚(▲)은 주황.
  assert.match(css, /\.tile\.hot \.k::before\{[^}]*background:var\(--accent\)\}/);
  assert.doesNotMatch(css, /\.tile\.hot \.v\{color:var\(--bad\)\}/);
  assert.doesNotMatch(css, /\.tile\.hot \.spark polyline\{stroke:var\(--bad\)\}/);
  assert.match(css, /\.tile\.bad \.v\{color:var\(--bad\)\}/);
  assert.doesNotMatch(css, /\.delta\.up\{color:var\(--bad\)\}/);
  // 시험 자료(KST 12시): 문제 신고 문의의 답 목표(8시간)가 오늘 안에 끝나 '오늘 목표 넘김' 1 → 그 칸만 빨강.
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-12T03:00:00Z") });
  const { env } = await world();
  const u = await signedIn(env);
  const late = u.$$("#main a.tile").find((a) => /오늘 목표 넘김/.test(a.textContent));
  assert.ok(late.classList.contains("bad"), "an answer due today: red");
  assert.deepEqual(u.$$("#main a.tile.bad"), [late], "only that tile is red");
  // 답 대기·검토 대기처럼 기다리는 일이 있는 칸은 hot(파란 점)이지 빨강이 아니에요.
  const waiting = u.$$("#main a.tile.kpi.hot");
  assert.ok(waiting.length >= 2);
  for (const a of waiting) assert.ok(!a.classList.contains("bad"), a.textContent);
  assert.ok(!u.$("#main .alerts .card-head .pill.bad"), "the 주의 필요 count is a plain pill");
  assert.deepEqual(u.errors, []);
});

test("[RVA-36] 오늘은 한 손 화면: 내가 할 것 셋(옆에 내일 볼게요) + 저절로 끝난 일만 펴고, 나머지는 접힌 줄, 채운 단추는 '차례로 답하기' 하나", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/home");
  const picks = u.$$("#main .today-three li");
  assert.ok(picks.length >= 1 && picks.length <= 3, "three at most");
  for (const li of picks) {
    const later = li.querySelector('button.pick-later[data-act="inbox-snooze"]');
    assert.ok(later, "each of the three can be put off");
    assert.match(later.getAttribute("aria-label"), /^내일 볼게요: /);
    assert.equal(later.dataset.key, li.dataset.swipe);
  }
  // Everything else is one folded line each, closed at first.
  const folds = u.$$("#main details.home-fold").map((d) => [d.dataset.key, d.open]);
  assert.deepEqual(folds, [["home:todo", false], ["home:tiles", false], ["home:recent", false]]);
  assert.match(u.text('#main details[data-key="home:todo"] > summary'), /할 일 모두/);
  assert.match(u.text('#main details[data-key="home:tiles"] > summary'), /답 대기 \d+ · 검토 대기 \d+/);
  // One filled button you can see: 차례로 답하기 (the queue), not the one in the to-do list any more.
  const seen = u.$$("#main .btn.primary").filter((b) => !b.closest("details:not([open])"));
  assert.deepEqual(seen.map((b) => b.dataset.act), ["queue-start"]);
  assert.match(seen[0].textContent, /^문의 \d+건 차례로 답하기$/);
  // 남은 N개 보기 opens the folded list.
  await u.click('#main [data-act="home-fold"][data-key="home:todo"]');
  assert.ok(u.$('#main details[data-key="home:todo"]').open);
  assert.ok(u.$("#main details[data-key=\"home:todo\"] .rows.inbox li"), "the full list is inside");
  // Putting the first one off brings the next one up; 되돌리기 brings it back.
  const first = u.$("#main .today-three li").dataset.swipe;
  await u.click("#main .today-three li .pick-later");
  assert.match(u.text("#toast"), /내일 아침까지/);
  assert.notEqual(u.$("#main .today-three li").dataset.swipe, first);
  await u.click("#toast [data-undo]");
  assert.equal(u.$("#main .today-three li").dataset.swipe, first);
  assert.equal(u.$$("#main [style]").length, 0);
  assert.deepEqual(u.errors, []);
  const css = await adminFile("/admin/app.css").text();
  assert.match(css, /\.today-three li\{[^}]*touch-action:pan-y/, "a sideways push does not fight the page scroll");
  assert.match(css, /\.today-three \.t b\{[^}]*-webkit-line-clamp:2/, "titles show two lines on a phone (도약 43)");
});

test("[RVA-37] 오늘: 서버가 읽지 못해 빼 둔 줄을 원인·[그 줄 열기]·[개발사항으로] 한 장으로", async (t) => {
  offline(t);
  const { env, DB } = await world();
  const u = await asOwner(env, t);
  const id = DB.db.prepare("SELECT id FROM inquiries ORDER BY created_at LIMIT 1").get().id;
  DB.db.prepare("UPDATE inquiries SET info = ? WHERE id = ?").run("{broken", id);
  await u.go("#/desk");
  const stub = u.$$("#main .lg-line").find((li) => /읽지 못한 문의/.test(li.textContent));
  assert.ok(stub, "the ledger draws, with that one line marked");
  assert.match(stub.textContent, /읽지 못함/);
  await u.go("#/home");
  const card = u.$("#main .card.stuck");
  assert.ok(card, "오늘 says what was left out");
  assert.match(card.textContent, /운영 장부·정리함의 문의 한 줄/, "one line per line left out, however many places read it");
  assert.equal(card.querySelectorAll(".stuck-list > li").length, 1);
  assert.equal(card.querySelector("a.btn").getAttribute("href"), `#/inquiries/${id}`);
  await u.click('#main .card.stuck [data-act="stuck-dev"]');
  assert.match(u.text("#toast"), /개발사항으로 넘겼어요/);
  const devs = (await u.api("GET", "/api/admin/dev?status=all")).items;
  assert.ok(devs.some((d) => /^읽지 못한 줄\(/.test(d.title)), "the 개발사항 is made");
  assert.ok(u.$('#main .card.stuck a[href^="#/dev/"]'), "the card now points at the 개발사항");
  assert.deepEqual(u.errors, []);
});

test("[RVA-38] 자동 순환: 맨 위 한 줄 상태(저절로 도는지·다음 출시·내가 할 것)와 다음 한 걸음 하나, 카드 열 장은 접힌 줄, 개발자 말 없음", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/loop");
  await u.settle(20);
  assert.match(u.text("#main .loop-now .loop-state"), /(저절로 도는 중|일부만 저절로)\s*다음 출시 \d{1,2}\. \d{1,2}\. \(.\)\s*내가 할 것 \d+/);
  const folds = u.$$("#main details.loop-fold");
  assert.ok(folds.length >= 8, "the other cards are folded lines");
  assert.ok(folds.every((d) => !d.open), "closed at first");
  for (const d of folds) assert.ok(d.querySelector(":scope > summary .lf-t b"), "each line says what it is");
  const seen = u.$$("#main .btn.primary").filter((b) => !b.closest("details:not([open])"));
  assert.ok(seen.length <= 1, "one filled button at most: the next step");
  if (u.$("#main .loop-next")) assert.equal(u.$$("#main .loop-next .btn.primary").length, 1);
  assert.doesNotMatch(u.text("#main"), /PLAY_SERVICE_ACCOUNT|VAPID|versionCode|\(409\)|G10 \d장|ops\/dev-status|main에 합쳐지면/);
  // An urgent question comes first (the world has '알람 소리가 안 나요'): the step goes to 결정함.
  const d = await u.api("GET", "/api/admin/loop");
  if (d.urgentWaiting) assert.equal(u.$("#main .loop-next .btn.primary").getAttribute("href"), "#/decide");
  // With it closed, the local server's missing Play key is the step, and it opens the 연결 점검 line in place.
  for (const q of (await u.api("GET", "/api/admin/inquiries")).inquiries) await u.api("POST", `/api/admin/inquiries/${q.id}/status`, { status: "closed" });
  await u.go("#/home");
  await u.go("#/loop");
  await u.settle(20);
  assert.match(u.text("#main .loop-next"), /빠진 설정 \d+개: Play 출시 읽기/);
  await u.click('#main .loop-next [data-act="loop-open"][data-key="loop-check"]');
  assert.ok(u.$("#loop-check").open, "연결 점검 opened where it is");
  assert.deepEqual(u.errors, []);
});

test("[RVA-39] 자동 순환 › 연결 점검: 'Play까지 점검'을 누르면 그 시각과 '방금 다시 봤어요', 바뀐 줄은 '방금 됨 → 확인'", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/loop");
  await u.settle(20);
  // Something changes on the server after the page was read: 스스로 출시 안내 is turned off.
  await u.api("POST", "/api/admin/loop/switches", { autoShip: false });
  await u.click('#loop-check [data-act="loop-check"]');
  await u.settle(20);
  assert.ok(u.$("#loop-check").open, "stays open to show what it saw");
  assert.match(u.text("#main .check-seen"), /방금 다시 봤어요 · .+ · 바뀐 줄 1개/);
  assert.match(u.text("#loop-check"), /스스로 출시 안내[\s\S]*방금 됨 → 확인/);
  assert.match(u.text("#toast"), /다시 봤어요 · 확인할 것 \d+개 · 바뀐 줄 1개/);
  assert.deepEqual(u.errors, []);
});

test("[RVA-40] 문의 상세 '이 휴대폰': 꼭 볼 것(결제·환불 문의 N번)만 굵게, 숫자·결제·이전 문의는 '자세히' 한 줄", async (t) => {
  offline(t);
  const { env, call } = await world();
  await call("POST", "/api/inquiries", { v: 1, device: "test-device-00000011", kind: "other", text: "환불해 주세요", info: { app: "2.0.0 (54)" } }, from(11));
  const u = await asOwner(env, t);
  const list = (await u.api("GET", "/api/admin/inquiries?status=all")).inquiries;
  const id = list.find((q) => /알람 소리/.test(JSON.stringify(q))).id;
  await u.go(`#/inquiries/${id}`);
  await u.settle(30);
  const panel = u.$("#main aside.person");
  assert.ok(panel, "the phone panel");
  assert.match(u.text("#main .person-alerts"), /결제·환불 문의 1번/);
  const more = panel.querySelector("details.person-more");
  assert.ok(more && !more.open, "the rest is one folded line");
  assert.match(more.querySelector("summary").textContent, /자세히[\s\S]*문의 2 · 제보 \d+[\s\S]*이전 문의 1건/);
  assert.ok(more.querySelector("dl.lines"), "the numbers are inside");
  assert.ok(more.querySelector('a.row[href^="#/inquiries/"]'), "the other question is inside");
  assert.deepEqual(u.errors, []);
});

test("[RVA-41] 문의 상세 › 결제·환불 기록: 걸린 말('영수증')을 보여 주고 '아니에요 · 기록에서 빼기', 기록에 그 말", async (t) => {
  offline(t);
  const { env, call } = await world();
  const id = (await call("POST", "/api/inquiries", { v: 1, device: "test-device-00000021", kind: "question", text: "영수증 어디서 봐요?", info: { app: "2.0.0 (54)" } }, from(21))).data.id;
  const u = await asOwner(env, t);
  await u.go(`#/inquiries/${id}`);
  await u.settle(30);
  assert.match(u.text("#main .billing-why"), /‘영수증’라는 말 때문에 저절로 잡혔어요/);
  await u.click('#main [data-act="inquiry-billing"][data-miss="1"]');
  assert.match(u.text("dialog"), /잘못 잡은 말 ‘영수증’도 기록해/);
  u.$('dialog form[data-form="confirm"]').requestSubmit();
  await u.settle(30);
  assert.ok(!u.$("#main .billing-why"), "out of the record");
  assert.match(u.text("#main .billing-card"), /아니에요\. 마지막 글부터 180일/);
  assert.match(u.text("#main .timeline"), /자동 판정이 틀렸다고 적었어요\(‘영수증’\)/);
  assert.deepEqual(u.errors, []);
});

test("[RVA-42] 숫자 › 한눈에: 개선 신호는 '긴 흐름' 신호라고 말하고, 위에 바로 볼 것이 있으면 그 칩으로 이어 줘요", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/glance");
  await u.settle(30);
  assert.match(u.text("#main .ob-signals .hint"), /며칠 쌓인 숫자를 기준과 견줘 찾은 긴 흐름 문제예요\(위 정상도는 지금 이 순간의 숫자\)/);
  const empty = u.$("#main .ob-signals .empty-line");
  if (empty) {
    assert.match(empty.textContent, /^긴 흐름에서는 찾은 문제가 없어요\./);
    const hero = u.$("#main .gl-hero");
    if (!hero.classList.contains("ok") && u.$$("#main .gl-signals li").length) assert.match(empty.textContent, /위 '.+' \d+개는 지금 숫자라 위 칩에서 바로 열어요/);
  }
  assert.deepEqual(u.errors, []);
});

test("[RVA-43] 숫자 › 기능별 효과: 볼 숫자가 없는 기능은 같은 문장을 되풀이하지 않고 접힌 한 줄, 설명은 펴는 한 줄", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/effects");
  await u.settle(30);
  assert.doesNotMatch(u.text("#main"), /서버가 아직 켜짐·꺼짐을 보지 않았어요|켜진 휴대폰 수 모름/);
  const blank = u.$('#main details.fx-blank[data-key="fx-blank"]');
  assert.ok(blank && !blank.open, "one folded line");
  const n = Number(blank.querySelector("summary b").textContent.match(/(\d+)개/)[1]);
  assert.equal(blank.querySelectorAll(".fx-names li").length, n, "every such flag is in it, by name");
  assert.ok(u.$('#main details.tip[data-key="fx-how"]'), "the long explanation folds");
  assert.deepEqual(u.errors, []);
});

test("[RVA-44] 관리자 초대: '빠르게 고르기'만 먼저, 영역별 권한은 접힌 한 줄, 고른 결과는 한 줄로", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/admins");
  await u.settle(20);
  await u.click('#main [data-act="invite"]');
  const areas = u.$("dialog details.perm-areas");
  assert.ok(areas && !areas.open, "the 15 areas are folded");
  assert.match(u.text("dialog #perm-sum"), /^검토 담당 그대로 · 고치기 \d+ · 보기 \d+ · 없음 \d+$/);
  assert.equal(u.$('dialog [data-act="preset"][aria-pressed="true"]').dataset.key, "reviewer");
  await u.click('dialog [data-act="preset"][data-key="support"]');
  assert.match(u.text("dialog #perm-sum"), /^문의 담당 그대로/);
  assert.equal(u.$('dialog [data-act="preset"][aria-pressed="true"]').dataset.key, "support");
  // One area changed by hand: the line says so and no role chip stays pressed.
  await u.click('dialog [data-act="level"][data-area="audit"][data-level="view"]');
  assert.match(u.text("dialog #perm-sum"), /^직접 고름 · /);
  assert.equal(u.$$('dialog [data-act="preset"][aria-pressed="true"]').length, 0);
  assert.deepEqual(u.errors, []);
});

test("[RVA-45] 내 계정: 이름·권한·로그아웃만 펴고, '로그인·보안' / '이 기기' / '가족·휴대폰' 세 묶음은 상태 한 줄과 함께 접혀요", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/account");
  await u.settle(30);
  const groups = u.$$("#main details.acct-group").map((d) => [d.dataset.key, d.open]);
  assert.deepEqual(groups, [["acct-security", false], ["acct-device", false], ["acct-phones", false]]);
  assert.match(u.text('#main details[data-key="acct-security"] > summary'), /로그인·보안[\s\S]*(패스키 \d+개|패스키 없음)/);
  assert.match(u.text('#main details[data-key="acct-device"] > summary'), /이 기기[\s\S]*알림 (켜짐|꺼짐)/);
  assert.ok(u.$('#main .acct-top [data-act="logout"]'), "로그아웃 stays one tap away");
  assert.ok(u.$('#main details[data-key="acct-security"] [data-act="logout-all"]'), "모든 기기에서 로그아웃 is in 로그인·보안");
  assert.ok(u.$('#main details[data-key="acct-device"] .prefs'), "이 기기 settings are in 이 기기");
  const seen = u.$$("#main .btn.primary").filter((b) => !b.closest("details:not([open])"));
  assert.ok(seen.length <= 1);
  assert.deepEqual(u.errors, []);
});

test("[RVA-46] 운영 장부·결정함·문의 목록: 기한 넘긴 줄은 '기한 넘김 N' 머리 하나만 빨강, 줄의 칸은 굵은 보통 글자 '넘음'", async (t) => {
  const css = await adminFile("/admin/app.css").text();
  // HQ-69(조건 6·14): 줄마다 빨간 띠 + 빨간 칩이 한 화면에 열 번 나오던 것을 머리 하나로, '넘음'은 글로 남아요.
  assert.doesNotMatch(css, /\.lg-line\.late \.lg-row\{box-shadow/);
  assert.match(css, /\.pill\.late\{background:transparent;border-color:transparent;color:var\(--fg\);font-weight:800/);
  const t0 = Date.parse("2026-11-12T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: t0 });
  const { env } = await world();
  t.mock.timers.setTime(t0 + 10 * 864e5);
  const u = await signedIn(env);
  await u.go("#/desk");
  await u.settle(30);
  const list = u.$("#main .rows.ledger");
  const lines = [...list.querySelectorAll(":scope > li.lg-line")];
  const late = lines.filter((li) => li.classList.contains("late"));
  assert.ok(late.length >= 2, "several lines are late");
  const heads = [...list.querySelectorAll(":scope > li.lg-group")];
  assert.match(heads[0].textContent, new RegExp(`기한 넘김 ${late.length}`));
  assert.ok(heads[0].querySelector(".pill.bad"), "the one red mark");
  assert.equal(list.firstElementChild, heads[0], "the head comes first");
  for (const li of late) {
    assert.match(li.querySelector(".c-due").textContent, /넘음/, "'넘음' stays as words");
    assert.ok(li.querySelector(".c-due .pill.late"), "plain bold, not red");
  }
  const reds = [...list.querySelectorAll(".pill.bad")].filter((p) => p.textContent.trim() !== "급함");
  assert.equal(reds.length, 1, "besides 급함 (danger), red only on the head");
  if (lines.length > late.length) assert.match(heads[1].textContent, new RegExp(`그 밖에 ${lines.length - late.length}줄`));
  // '기한 넘음' 보기는 모두 넘긴 줄이라 머리가 없어요.
  await u.click('#main [data-act="ledger-view"][data-key="late"]');
  await u.settle(30);
  assert.equal(u.$$("#main .rows.ledger > li.lg-group").length, 0);
  assert.equal(u.$$("#main .seg .n.hot").length, 1, "one red number in the bar: 기한 넘음");
  // 사람과 글 › 문의: 목표를 넘긴 줄도 굵은 보통 글자.
  await u.go("#/inquiries");
  await u.settle(30);
  const sla = u.$$("#main .rows .row-side .pill.late");
  assert.ok(sla.length >= 1 && sla.every((p) => /넘김$/.test(p.textContent)));
  assert.equal(u.$$("#main .rows .pill.bad").filter((p) => p.textContent.trim() !== "급함").length, 0);
  // 결정함: 급함은 머리 칩 하나, 줄마다 되풀이하지 않아요.
  await u.go("#/decide");
  await u.settle(30);
  assert.ok(u.$$("#main .pill.bad").length <= 1, u.$$("#main .pill.bad").map((p) => p.textContent).join(","));
  assert.deepEqual(u.errors, []);
});

test("[RVA-48] 운영 장부: 50줄씩 이어 받기, 펼친 줄만 상세, 고르기는 그 줄만 다시 그리기, 칸마다 읽어 주는 이름", async (t) => {
  offline(t);
  const { env, call } = await world();
  for (let n = 30; n < 92; n++) {
    await call("POST", "/api/inquiries", { v: 1, device: `test-device-000000${n}`, kind: "question", text: `달력 색은 어디서 바꿔요 ${n}`, info: { app: "2.0.0 (54)" } }, from(n));
  }
  // 그 자리에서 펼치는 폭(1024)
  const u = await signedIn(env, { width: 1024 });
  await u.go("#/desk?view=open");
  await u.settle(30);
  const lines = () => u.$$("#main .rows.ledger > li.lg-line");
  assert.equal(lines().length, 50, "50 lines first");
  assert.match(u.text('#main [data-act="ledger-more"]'), /더 보기 50\/\d+/);
  // ADM-23: 닫힌 줄은 상세를 그리지 않아요. 펼치면 그 자리에서 채워요.
  assert.equal(u.$$("#main .rows.ledger .lg-detail").filter((x) => x.childElementCount).length, 0, "no detail drawn for closed lines");
  const d0 = lines()[3].querySelector("details");
  d0.open = true;
  d0.dispatchEvent(new u.window.Event("toggle"));
  assert.ok(d0.querySelector(".lg-detail [data-act=\"ledger-set\"], .lg-detail [data-act=\"ledger-done\"]"), "opened: its detail is there");
  // ADM-29: 칸마다 읽어 주는 이름.
  const li = lines()[0];
  for (const [cell, name] of [["c-st", "단계"], ["c-who", "담당"], ["c-due", "기한"]]) assert.equal(li.querySelector(`.${cell} > .sr`).textContent.trim(), name);
  // ADM-23: 고르기 하나는 그 줄과 고른 줄 막대만 다시 그려요(다른 줄은 같은 노드 그대로).
  await u.click('#main [data-act="ledger-pick-mode"]');
  const keep = lines()[10];
  await u.click(`#main li.lg-line[data-key="${lines()[2].dataset.key}"] input[data-act="ledger-pick"]`);
  assert.match(u.text("#main .bulkbar"), /1개 골랐어요/);
  assert.ok(keep.isConnected, "the other lines were not drawn again");
  assert.ok(lines()[2].querySelector('input[data-act="ledger-pick"]').checked);
  await u.click('#main [data-act="ledger-pick-mode"]');
  // 더 보기: 다음 50줄을 이어 붙여요(겹치는 줄 없이).
  await u.click('#main [data-act="ledger-more"]');
  await u.settle(30);
  const keys = lines().map((x) => x.dataset.key);
  assert.ok(keys.length > 50);
  assert.equal(new Set(keys).size, keys.length);
  assert.ok(!u.$('#main [data-act="ledger-more"]'), "all here");
  assert.deepEqual(u.errors, []);
});

test("[RVA-49] 운영 장부(노트북 1280~): 줄을 누르면 오른쪽 칸에 상세, 목록은 제자리, 닫기·같은 줄 다시 누르기로 닫혀요", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await signedIn(env, { width: 1440 });
  await u.go("#/desk?view=open");
  await u.settle(30);
  assert.ok(!u.$("#lg-side"), "no empty side pane");
  assert.equal(u.$$("#main .rows.ledger details").length, 0, "lines are buttons, not folds");
  const row = u.$$("#main .rows.ledger > li.lg-line > .lg-row")[1];
  const key = row.dataset.key;
  assert.equal(row.getAttribute("role"), "button");
  await u.click(row);
  await u.settle(10);
  assert.ok(u.$("#main .lg-split.on #lg-side"), "the side pane opened");
  assert.match(u.text("#lg-side .lg-side-head"), /K-\d{4}/);
  assert.ok(u.$("#lg-side .lg-detail [data-act]"), "its buttons are there");
  assert.equal(u.$(`#main li.lg-line[data-key="${key}"] > .lg-row`).getAttribute("aria-expanded"), "true");
  assert.equal(u.$$("#main .rows.ledger > li.lg-line.on").length, 1);
  // 다른 줄: 칸만 바뀌어요.
  const other = u.$$("#main .rows.ledger > li.lg-line > .lg-row")[2];
  await u.click(other);
  assert.equal(u.$(`#main li.lg-line[data-key="${key}"] > .lg-row`).getAttribute("aria-expanded"), "false");
  assert.match(u.text("#lg-side .lg-side-head"), new RegExp(u.$(`#main li.lg-line[data-key="${other.dataset.key}"] .c-no b`).textContent));
  await u.click('#lg-side [data-act="ledger-close"]');
  await u.settle(10);
  assert.ok(!u.$("#lg-side"), "closed");
  // 늦게 오는 답(흐름 띠 등)은 한 틀에 한 번만 다시 그려요(ADM-24): load 안의 다시 그리기는 마지막 하나뿐.
  const js = await adminFile("/admin/app.js").text();
  const load = js.slice(js.indexOf("async function load({ quiet = false } = {}) {"), js.indexOf("function renderSoon()"));
  assert.equal(load.match(/render\(\{ keepScroll: true \}\)/g)?.length, 1, "only the final render; the late ones use renderSoon");
  assert.ok((load.match(/renderSoon\(\)/g)?.length ?? 0) >= 5);
  assert.deepEqual(u.errors, []);
});

test("[RVA-50] 운영 장부: 스토어 리뷰 줄은 별 수와 답 초안을 보여 주고, 스토어 리뷰 화면에서 문의와 함께 보기로 이어져요", async (t) => {
  offline(t);
  const { env, DB } = await world();
  await DB.prepare(`INSERT INTO store_reviews (id, stars, text, app, lang, at, reply, reply_at, replied_by, draft, draft_by, status, fetched_at)
    VALUES ('gp:ui-review-0001', 2, '위젯이 안 바뀌어요', '2.0.0', 'ko', ?1, '', NULL, '', '불편을 드려 죄송해요. 밤낮 운영팀', 'Claude', 'seen', ?1)`).bind(Date.now() - 3600e3).run();
  const u = await asOwner(env, t);
  await u.go("#/desk?view=open&group=ask");
  await u.settle(30);
  const line = u.$('#main li.lg-line[data-key="store_review:gp:ui-review-0001"]');
  assert.ok(line, "the review is a ledger line");
  assert.match(line.textContent, /별 2개 · 위젯이 안 바뀌어요/);
  await u.click(line.querySelector(".lg-row"));
  await u.settle(10);
  assert.match(u.text("#main"), /답 초안/);
  assert.match(u.text("#main"), /스토어 리뷰 화면에서 고쳐 Play로 보내요/);
  await u.go("#/reviews");
  await u.settle(30);
  assert.ok(u.$('#main a[href="#/desk?view=open&group=ask"]'), "운영 장부에서 문의와 함께 보기");
  assert.deepEqual(u.errors, []);
});

test("[RVA-52] 화면 자체가 시험: 관리자 앱이 알린 화면 오류는 오류 묶음에 '화면 오류 · 관리자 앱 <화면>'으로, 그리다 넘어진 화면은 '다시 그리기' 카드", async (t) => {
  offline(t);
  const { env, DB } = await world();
  const u = await asOwner(env, t);
  const count = async () => (await DB.prepare("SELECT COUNT(*) AS n FROM error_groups WHERE route LIKE 'screen/%'").first()).n;
  const before = await count();
  await u.api("POST", "/api/admin/screen-error", { screen: "glance", where: "paint", kind: "error", message: "TypeError: x is undefined" });
  assert.equal(await count(), before + 1);
  const js = await adminFile("/admin/app.js").text();
  assert.match(js, /이 화면을 그리다 오류가 났어요/);
  assert.match(js, /"screen-again": \(\) => load\(\)/);
  await u.go("#/errors");
  await u.settle(30);
  assert.match(u.text("#main"), /화면 오류 · 관리자 앱 glance/);
  assert.deepEqual(u.errors, []);
});

test("[RVA-56] 장터 › 장터 건강: 깨진 작품은 빨간 머리 하나 아래 줄로, 달라진 작품은 접힌 목록, 운영 장부 줄에서 #/recipes?tab=health로 바로 와요", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/recipes?tab=health");
  await u.settle(30);
  assert.match(u.text("#main .empty"), /아직 장터 건강 결과가 없어요/);
  assert.ok(u.$('#main form[data-form="market-health"] input[type="file"]'), "결과 올리기");
  await u.api("POST", "/api/admin/recipes/health", { format: "bamnat-market-health", v: 1, build: "7f3a91c4", at: new Date().toISOString(), works: 516, listed: 498, opened: 497,
    broken: [{ key: "market-139#7", title: "책장 넘기기 달력", kind: "old", why: "unknown motion", app: 52 }], changed: [{ key: "market-139#2", title: "봄 테마", d: 9.1 }], unchanged: [] });
  await u.go("#/desk?view=open&group=report");
  await u.settle(30);
  assert.match(u.text('#main li.lg-line[data-key="market_broken:market-139.7"]'), /책장 넘기기 달력 · 옛 앱에서 안 열림/);
  await u.go("#/recipes?tab=health");
  await u.settle(30);
  assert.equal(u.$('#main [data-act="recipe-tab"][aria-pressed="true"]')?.dataset.key, "health");
  assert.match(u.text("#main .mh-head"), /작품 516개를 넣어 봤어요/);
  assert.equal(u.$$("#main .mh .pill.bad").length, 1, "빨강은 머리 하나");
  assert.match(u.text("#main .mh-rows"), /깨진 작품 1.*책장 넘기기 달력.*옛 앱에서 안 열림.*market-139#7 · 옛 앱 52/);
  assert.match(u.text('#main details[data-key="mh-달라진 작품"] summary'), /달라진 작품 1/);
  // 다른 보기를 고르면 주소도 따라가요.
  await u.click('#main [data-act="recipe-tab"][data-key="visible"]');
  await u.settle(20);
  assert.match(u.window.location.hash, /tab=visible/);
  assert.deepEqual(u.errors, []);
});

test("[RVA-57] 묶음 나누기: 화면 묶음은 그 화면을 열 때 받고, 받지 못하면 '서버에 닿지 않아요'와 다시 불러오기, 다시 누르면 열려요", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  const asked = [];
  let block = true;
  const real = u.window.fetch;
  u.window.fetch = async (input, init) => {
    const path = new URL(typeof input === "string" ? input : input.url, ORIGIN).pathname;
    if (path.startsWith("/admin/v/")) {
      asked.push(path.split("/").pop().split(".")[0]);
      if (block && path.startsWith("/admin/v/flags.")) return new u.window.Response("", { status: 503 });
    }
    return real(input, init);
  };
  await u.go("#/flags");
  await u.settle(30);
  assert.ok(asked.includes("flags"), "기능 관리를 열 때 그 묶음을 받아요");
  assert.match(u.text("#main .empty.failed"), /서버에 닿지 않아요/);
  block = false;
  await u.click('#main [data-act="refresh"]');
  await u.settle(30);
  assert.ok(!u.$("#main .empty.failed"), "다시 불러오면 열려요");
  assert.ok(u.$("#main [data-act]"), "기능 관리 화면");
  // 마을 장터는 장터 묶음과 기본 도구 후보(work) 묶음을 함께 기다려요.
  await u.go("#/recipes?tab=tools");
  await u.settle(30);
  assert.ok(asked.includes("market"));
  assert.ok(!u.$("#main .empty.failed"));
  assert.deepEqual(u.errors, []);
});

test("[RVA-58] 개발사항이 저장소 계획 줄뿐이면 '지금'은 빈 화면과 할 일 단추 하나, 줄은 오른쪽 알약 없이 한 줄 모양, 요금 판단은 Claude 제안별 한 줄", async (t) => {
  offline(t);
  const { env } = await world();
  const add = env.DB.db.prepare(`INSERT INTO dev_items (id, title, body, status, track, size, route, tier_ask, tier_why, created_by, created_by_name, created_at, updated_at)
    VALUES (?, ?, '저장소 줄', 'doing', ?, ?, 'app', ?, ?, 'sync', '저장소', ?, ?)`);
  const at = Date.now();
  for (let i = 0; i < 8; i++) add.run(`d${String(i).padStart(15, "0")}`, `계획 ${i}`, `RVI-${i + 1}`, "fix", "", "", at - i, at - i);
  const u = await asOwner(env, t);
  await u.go("#/dev");
  await u.settle(30);
  assert.match(u.text("#main .empty"), /지금 손댈 개발사항은 없어요.*할 일 8개는 Claude가 개발하며/);
  await u.click('#main .empty [data-act="dev-status"][data-key="open"]');
  await u.settle(30);
  assert.match(u.window.location.hash, /status=open/);
  const rows = u.window.document.querySelectorAll("#main .dev-rows > li");
  assert.equal(rows.length, 8);
  assert.equal(rows[0].querySelectorAll(".pill").length, 0, "저장소 계획 줄에는 오른쪽 알약이 없어요");
  assert.match(rows[0].querySelector("small").textContent, /^개발중 · 고침 · .*RVI-1/);
  assert.ok(!/저장소|요금/.test(rows[0].querySelector("small").textContent), "만든 이 '저장소'와 '요금 무료(기존과 같음)'은 줄에서 빼요");
  // 요금 정할 새 기능 둘: Claude 제안별로 묶어 한 줄씩, 누르면 단추가 펼쳐져요.
  add.run("dnewfeature00001", "새 기능 하나", "RVK-1", "feature", "free", "투자 장부는 무료라서", at, at);
  add.run("dnewfeature00002", "새 기능 둘", "RVK-2", "feature", "", "", at, at);
  await u.go("#/tiers");
  await u.settle(30);
  const heads = [...u.window.document.querySelectorAll("#main h2.section")].map((h) => h.textContent.replace(/\s+/g, " ").trim());
  assert.deepEqual(heads, ["Claude 제안 무료1", "제안 없음1"]);
  assert.match(u.text("#main .tier-rows summary"), /새 기능 하나.*RVK-1 · 투자 장부는 무료라서/);
  assert.ok(u.$('#main [data-act="loop-tier-copy"]'), "논의용으로 한 번에 복사");
  assert.ok(u.$('#main .tier-rows [data-act="loop-tier"][data-tier="free"]'), "정하는 단추는 줄 안(펼치면 보여요)");
  // 지금 칸에도 이 둘이 와요.
  await u.go("#/dev");
  await u.settle(30);
  assert.equal(u.window.document.querySelectorAll("#main .dev-rows > li").length, 2);
  assert.match(u.text("#main .dev-rows"), /요금 정할 것/);
  assert.deepEqual(u.errors, []);
});

// 2.0.0 [RVQ-47] 검토 HQ-88·조건 12: 제목 + 알약 여럿인 머리와 두 칸 화면이 글자 200%의 840·1440에서 한 자씩 서지 않아요.
test("[RVQ-47] 검토 HQ-88 큰 글자: 머리의 제목은 16rem을 지키고 알약 묶음은 함께 감기며, 두 칸은 칸 폭이 모자라면 위아래로", async () => {
  const css = await adminFile("/admin/app.css").text();
  assert.match(css, /\.card-head>:is\(h2,h3\)\{flex:1 1 16rem;min-width:0/);
  assert.match(css, /\.head-pills\{display:flex;flex-wrap:wrap/);
  assert.match(css, /@media \(min-width:1024px\)\{\.cols\{display:flex;flex-wrap:wrap/);
  assert.match(css, /\.home-grid\{display:flex;flex-wrap:wrap\}/);
  assert.match(css, /\.flow-cols\{grid-template-columns:repeat\(auto-fit,minmax\(min\(100%,max\(22rem,calc\(50% - \.4rem\)\)\),1fr\)\)/);
});

// 2.0.0 [RVA-61] 총괄 Q1 사진(380·글자 200%, 조건 12): 좁은 막대는 판 알약을 점으로, 제목은 한 줄. 최근 활동은 좁으면 시각을 글 위로.
test("[RVA-61] 좁은 막대(380·200%)에서 판 알약은 점, 제목은 한 줄 말줄임, 최근 활동 시각은 글 위로", async () => {
  const css = await adminFile("/admin/app.css").text();
  assert.match(css, /@container bar \(width < 22rem\)\{\s*\.dl-pill:not\(\.warn,\.bad\)\{[^}]*font-size:0/);
  assert.match(css, /@container bar \(width < 22rem\)\{[^@]*\.bar h1 \.t\{-webkit-line-clamp:1\}/);
  assert.match(css, /\.feed\{container:feed\/inline-size\}/);
  assert.match(css, /@container feed \(width < 22rem\)\{\.feed li\{grid-template-columns:1fr/);
});
