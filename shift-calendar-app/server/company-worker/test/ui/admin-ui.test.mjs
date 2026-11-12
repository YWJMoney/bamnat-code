// [ADMUX-01]~[ADMUX-08] The developer app itself, run in a pretend browser (happy-dom) against the worker on
// an in-memory D1: it signs in, opens every screen, and the things that live only in the page work —
// filters in the address, drafts that outlive a reload, the bulk bar, undo, the palette, signing in again.
// `npm run test:ui` (needs `npm ci` once for happy-dom).
import { test } from "node:test";
import assert from "node:assert/strict";
import { Window } from "happy-dom";
import worker from "../../src/index.js";
import { adminFile } from "../../src/admin-app.js";
import { SERVER_VERSION } from "../../src/version.js";
import { server, signIn, report, setup, device, from, ORIGIN, PASSWORD } from "../helpers.mjs";

const APP_JS = await adminFile("/admin/app.js").text();
const HTML = await adminFile("/admin/").text();
const BODY = HTML.slice(HTML.indexOf("<body>") + 6, HTML.indexOf("</body>"));

const open = [];
test.after(async () => {
  for (const w of open) await w.happyDOM.close();
});

/** A browser tab on the developer app: its fetch goes to the worker, with the sign-in cookie kept like a browser does. */
async function tab(env, { hash = "", storage = null, cookie = null } = {}) {
  const window = new Window({ url: `${ORIGIN}/admin/${hash}`, width: 1280, height: 900,
    settings: { disableJavaScriptFileLoading: true, disableCSSFileLoading: true, disableIframePageLoading: true } });
  open.push(window);
  const jar = { cookie };
  const errors = [];
  window.addEventListener("error", (e) => errors.push(e.error ?? e.message));
  window.fetch = async (input, init = {}) => {
    const url = new URL(typeof input === "string" ? input : input.url, ORIGIN);
    const headers = new Headers(init.headers ?? {});
    if (jar.cookie) headers.set("cookie", jar.cookie);
    headers.set("cf-connecting-ip", "203.0.113.9");
    if (init.method && init.method !== "GET") headers.set("origin", ORIGIN);
    const res = await worker.fetch(new Request(url, { method: init.method ?? "GET", headers, body: init.body }), env);
    const set = res.headers.get("set-cookie");
    if (set) jar.cookie = /Max-Age=0/.test(set) ? null : set.split(";")[0];
    const body = res.status === 304 ? null : await res.text();
    return new window.Response(body, { status: res.status, headers: Object.fromEntries(res.headers) });
  };
  if (storage) for (const [k, v] of Object.entries(storage)) window.localStorage.setItem(k, v);
  window.structuredClone ??= structuredClone;
  window.document.body.innerHTML = BODY;
  window.eval(APP_JS);
  const t = {
    window, document: window.document, jar, errors,
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

async function signedIn(env) {
  const t = await tab(env);
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
  for (const s of ["home", "desk", "inquiries", "dev", "templates", "sla", "review", "versions", "community", "companies", "shares", "adopt", "flags", "types", "notices", "chores",
    "gates", "metrics", "effects", "audit", "admins", "rewards", "server", "account", "inbox", "ops", "settings", "more",
    // 1.38 [ADM3] 새 화면들.
    "flow", "cockpit", "incident", "reviews", "requests", "launch", "materials", "health", "money", "errors", "backup", "keys", "growth",
    // 1.39 [LOOP] 자동 순환·결정함·요금 판단.
    "loop", "decide", "tiers"]) {
    await t.go(`#/${s}`);
    const main = t.$("#main");
    assert.ok(main.textContent.trim().length > 10, `${s} shows something`);
    assert.ok(!t.$("#main .failed"), `${s} loaded`);
    assert.equal(t.$$("#main [style]").length, 0, `${s}: no style attribute (CSP)`);
  }
  assert.deepEqual(t.errors, []);
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
  const store = Object.fromEntries(Object.entries(t.window.localStorage).filter(([k]) => k.startsWith("bamnat-admin-draft:")));
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
  assert.ok(!Object.keys(again.window.localStorage).some((k) => k.startsWith("bamnat-admin-draft:")));
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
  assert.match(t.text('form[data-form="notice"] .help'), /1\.22\.0\(번호 33\)/, "the newest app a question came from");
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
  await u.click('#main [data-act="inbox-snooze"]');
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
  assert.match(u.text("#main"), new RegExp(`회사 1 · 쓰는 규칙 \\d+ · 서버 ${SERVER_VERSION.replaceAll(".", "\\.")} · 관리자 앱 빌드`));
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
  assert.match(main, /보상 받음 1명/);
  assert.match(main, /친구 1명이 각자 7일 이상 쓰면 프로 1개월, 1명 더 채울 때마다 또 1개월 · 상한 없음/);
  assert.ok(main.includes(made.data.code), "the code most friends came by");
  assert.match(main, /최근 30일 몰림 1번/);
  assert.deepEqual(t.errors, []);
});

test("[INVITE-08] 관리 › 보상 › 친구 초대 (1.27 설정): the developer sets how many friends, days, months and the cap", async () => {
  const { env, DB } = await world();
  const t = await signedIn(env);
  await t.go("#/rewards");
  await t.settle(30);
  const card = () => [...t.document.querySelectorAll("#main section.card")].find((c) => c.querySelector("h2")?.textContent === "친구 초대");
  assert.ok(card(), "the 친구 초대 card");
  assert.match(card().textContent, /친구 5명이 각자 7일 이상 쓰면 프로 1개월, 5명 더 채울 때마다 또 1개월 · 상한 없음/);
  assert.match(card().textContent, /1년 상한\s+−\s+없음/);
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
  // The strip on 문의 says what waits, and opens it filtered.
  await t.go("#/inquiries");
  const strip = t.$("#waiting a");
  assert.match(strip.textContent, /답 대기\s*3/);
  assert.equal(strip.getAttribute("href"), "#/inquiries?sort=sla");
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
  assert.ok(u.$('#main details.more-tiles[data-key="home:tiles"] .tiles a.tile'));
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
  const t = await signedIn(env);
  await t.go("#/sort");
  assert.equal(t.window.location.hash, "#/desk?view=claude&group=ask", "정리함 lives in 운영 장부 now");
  assert.ok(t.$$("#main .rows.ledger > li").length >= 1, "the waiting questions are lines");
  assert.match(t.text("#main .rows.ledger"), /K-\d{4}/, "every line has its number");
  assert.ok(t.$("#main .lg-head"), "a head row for the columns");
  assert.ok(t.$("#sort-tools .handoff-card"), "Claude에게 넘기기 is there");
  // A line's answer can be changed before it goes; what goes is what the box says.
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
  const first = t.$("#main .rows.ledger > li").dataset.key;
  await t.click(`#main li[data-key="${first}"] [data-act="ledger-done"]`);
  assert.match(t.text("dialog"), /끝낸 이유를 꼭/);
  t.$('dialog input[name="reason"][value="duplicate"]').checked = true;
  await t.click('dialog form[data-form="ledger-done"] button[type="submit"]');
  await t.idle();
  assert.ok(!t.$(`#main li[data-key="${first}"]`), "it left 열린 것");
  await t.click('[data-act="ledger-view"][data-key="closed"]');
  await t.idle();
  assert.match(t.text(`#main li[data-key="${first}"]`), /끝 · 중복/);
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
  const t = await signedIn(env);
  await t.go("#/desk?view=claude&group=ask");
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
  const back = t1.$('[data-act="recipe-tab"][data-key="images"]:not(.chip)');
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
  assert.match(t.text("#deploy-line"), /회사 1\.39\.\d+ 로컬/);
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
