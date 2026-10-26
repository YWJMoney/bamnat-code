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
    go: async (h) => {
      window.location.hash = h;
      await t.settle();
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
  for (const s of ["home", "inbox", "versions", "review", "inquiries", "dev", "companies", "community", "rewards", "shares", "ops", "metrics", "admins", "audit", "settings", "account", "more"]) {
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
  await t.go("#/settings");
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
  // Home and three: a fourth takes the place of the one there longest.
  await flip("audit", true);
  // [ADMUX-17] 받은함 is the first tab after home since 1.25, so it is the one that makes room.
  assert.match(t.text("#toast"), /3개까지라 받은함 대신 활동 기록/);
  assert.ok(!t.$('.tabs [data-nav="inbox"]'));
  assert.ok(t.$('.tabs [data-nav="audit"]'), "활동 기록 is a bottom tab now");
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
  await t.go("#/ops");
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

test("[ADMUX-17] 받은함 lists what waits, 맡기 takes the question, 내일 볼게요 hides it on this device only", async (t) => {
  offline(t);
  const { env } = await world();
  const u = await asOwner(env, t);
  await u.go("#/inbox");
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
  // Home sends 지금 할 일 to 받은함.
  await u.go("#/home");
  assert.ok(u.$('#main a[href="#/inbox"]'));
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

test("[INVITE-08] 설정 › 친구 초대: the developer sets how many friends, days, months and the cap", async () => {
  const { env, DB } = await world();
  const t = await signedIn(env);
  await t.go("#/settings");
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
