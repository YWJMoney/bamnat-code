// [ADMUX-01]~[ADMUX-08] The developer app itself, run in a pretend browser (happy-dom) against the worker on
// an in-memory D1: it signs in, opens every screen, and the things that live only in the page work —
// filters in the address, drafts that outlive a reload, the bulk bar, undo, the palette, signing in again.
// `npm run test:ui` (needs `npm ci` once for happy-dom).
import { test } from "node:test";
import assert from "node:assert/strict";
import { Window } from "happy-dom";
import worker from "../../src/index.js";
import { adminFile } from "../../src/admin-app.js";
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

async function world() {
  const s = server();
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
  for (const s of ["home", "inquiries", "dev", "templates", "sla", "review", "versions", "community", "companies", "shares", "adopt", "flags", "types", "notices", "chores",
    "gates", "metrics", "effects", "audit", "admins", "rewards", "server", "account", "inbox", "ops", "settings", "more"]) {
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
  // AN 1.28 [ADMUX-30] 오늘 and three hubs: a fourth takes the place of the one there longest (문의, the first after 오늘).
  await flip("insight", true);
  assert.match(t.text("#toast"), /오늘과 3개까지라 문의 대신 분석/);
  assert.ok(!t.$('.tabs [data-nav="ask"]'));
  assert.ok(t.$('.tabs [data-nav="insight"]'), "분석 is a bottom tab now");
  assert.deepEqual(JSON.parse(t.window.localStorage.getItem("bamnat-admin-prefs")).tabs, ["plaza", "release", "insight"]);
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
  assert.match(u.text("#main .meta"), /\(28일\)/);
  for (const sec of ["활성", "광장", "문의", "오류", "원격 갱신"]) assert.match(u.text("#main"), new RegExp(sec), `${sec} section`);
  const to = u.$("#mt-to").value;
  const from = new Date(Date.parse(to) - 2 * 864e5).toISOString().slice(0, 10);
  const el = u.$("#mt-from");
  el.value = from;
  el.dispatchEvent(new u.window.Event("change", { bubbles: true }));
  await u.settle(30);
  assert.match(u.text("#main .meta"), /\(3일\)/);
  assert.match(u.window.location.hash, new RegExp(`from=${from}`));
  assert.ok(u.$('#main [data-act="metric-clear"]'));
  await u.click('#main [data-act="metric-clear"]');
  await u.settle(30);
  assert.match(u.text("#main .meta"), /\(28일\)/);
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
  assert.deepEqual(sideHubs(t), ["today", "ask", "plaza", "release", "insight", "manage"]);
  assert.deepEqual(bottom(t), ["today", "ask", "plaza", "release", "more"]);
  // A hub's tabs sit under the title, and in the sidebar under the open hub.
  await t.go("#/review");
  assert.deepEqual(t.$$("#hubtabs a").map((a) => a.dataset.tab), ["review", "versions", "community", "companies", "shares", "adopt"]);
  assert.equal(t.$("#hubtabs [aria-current]").dataset.tab, "review");
  assert.ok(t.$('.side li[data-hub="plaza"].open .subnav a[data-tab="adopt"]'));
  assert.equal(t.$('.tabs [aria-current="page"]').dataset.nav, "plaza");
  // A hub of one tab has no tab row; 분석 sits under 더 보기 on the phone, which is lit there.
  await t.go("#/home");
  assert.ok(t.$("#hubtabs").hidden);
  await t.go("#/metrics");
  assert.equal(t.$('.tabs [aria-current="page"]').dataset.nav, "more");
  // 민지 answers questions only: 오늘, 문의 (without 답 목표, which is 설정's) and 관리 (내 계정 only).
  const u = await asMember(s);
  assert.deepEqual(sideHubs(u), ["today", "ask", "manage"]);
  assert.deepEqual(bottom(u), ["today", "ask", "manage"], "every hub fits: no 더 보기");
  await u.go("#/inquiries");
  assert.deepEqual(u.$$("#hubtabs a").map((a) => a.dataset.tab), ["inquiries", "dev", "templates"]);
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
  assert.deepEqual(sideHubs(v), ["today", "plaza", "manage"]);
  await v.go("#/review");
  assert.deepEqual(v.$$("#hubtabs a").map((a) => a.dataset.tab), ["review", "versions", "community", "companies"]);
  assert.equal(Number(v.text('.tabs [data-badge="hub:plaza"]')), 1, "the one report");
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
  assert.equal(badge("ask"), 3, "three questions wait");
  assert.equal(badge("plaza"), 1, "one report");
  assert.equal(side("today"), 0, "오늘 adds nothing of its own: its list is the others' things");
  assert.equal(badge("more"), side("insight") + side("manage"));
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
  assert.equal(badge("ask"), 2);
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

test("[ADMUX-36] 1~6 open the hubs, [ and ] the tabs, and the palette knows the old names", async () => {
  const { env } = await world();
  const t = await signedIn(env);
  await t.key("2");
  assert.equal(t.window.location.hash, "#/inquiries");
  await t.key("]");
  assert.equal(t.window.location.hash, "#/dev");
  await t.key("[");
  await t.key("[");
  assert.equal(t.window.location.hash, "#/sla", "round to the hub's last tab");
  await t.key("4");
  assert.equal(t.window.location.hash, "#/flags");
  // A hub opens on the tab it was left on.
  await t.key("2");
  assert.equal(t.window.location.hash, "#/sla");
  await t.key("g");
  await t.key("b");
  assert.equal(t.window.location.hash, "#/home");
  for (const [word, to] of [["받은함", "#/home"], ["운영", "#/flags"], ["친구 초대", "#/rewards"]]) {
    await t.key("k", { ctrlKey: true });
    await t.type("#pal", word);
    t.$("#pal").dispatchEvent(new t.window.KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await t.settle();
    assert.equal(t.window.location.hash, to, word);
  }
  await t.key("?");
  assert.match(t.text("dialog"), /1~6/);
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
  assert.deepEqual(now.tabs, ["plaza", "release", "insight"]);
  assert.deepEqual(now.views.map((v) => v.hash), ["#/home?view=mine", "#/inquiries?status=answered"]);
  assert.deepEqual(bottom(t), ["today", "plaza", "release", "insight", "more"]);
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
  assert.match(card.textContent, /결제\(프로 구독\)이\(가\) 켜졌어요/);
  assert.ok(u.$("#main").firstElementChild.classList.contains("flag-alert"), "at the top");
  await u.click('#main .flag-alert [data-act="flag-force"]');
  assert.match(u.$("#ff-why").value, /홈에서 바로 껐어요/);
  u.$('dialog form[data-form="flag-force"]').requestSubmit();
  await u.settle(30);
  assert.equal((await u.api("GET", "/api/admin/flags/billing")).override.force, "off");
  await u.settle(20);
  assert.ok(!u.$("#main .card.flag-alert"), "gone once forced off");
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
