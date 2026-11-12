// 1.36 보안 점검 L3 (조사/1.36-보안-점검.md): what phones send and the operator reads — 문의, 회사 이름, 글, 별명, 작품 이름·설명,
// 요구, 소개, 모음 — is drawn as words on every screen of the developer app, never as markup. The app draws with esc() and
// a CSP without inline scripts; this keeps it that way when a screen is added.
// `npm run test:ui` (needs `npm ci` once for happy-dom).
import { test } from "node:test";
import assert from "node:assert/strict";
import { Window } from "happy-dom";
import worker from "../../src/index.js";
import { adminFile } from "../../src/admin-app.js";
import { server, signIn, report, device, from, ORIGIN, PASSWORD } from "../helpers.mjs";
import { adminLib } from "../../src/admin-lib.js";

const APP_JS = await adminFile("/admin/app.js").text();
const HTML = await adminFile("/admin/").text();
const BODY = HTML.slice(HTML.indexOf("<body>") + 6, HTML.indexOf("</body>"));
const AFTER = Date.parse("2026-11-30T03:00:00Z");

const BAD = `<img src=x onerror="globalThis.__xss=1"><script>globalThis.__xss=2</script>"'><svg onload=globalThis.__xss=3><a href="javascript:globalThis.__xss=4">링크</a>`;

const open = [];
test.after(async () => {
  for (const w of open) await w.happyDOM.close();
});

async function tab(env) {
  const window = new Window({ url: `${ORIGIN}/admin/`, width: 1280, height: 900,
    settings: { disableJavaScriptFileLoading: true, disableCSSFileLoading: true, disableIframePageLoading: true } });
  open.push(window);
  const jar = { cookie: null };
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
  window.structuredClone ??= structuredClone;
  window.document.body.innerHTML = BODY;
  window.eval(APP_JS);
  const settle = async (n = 12) => { for (let i = 0; i < n; i++) await new Promise((r) => setTimeout(r, 5)); };
  const idle = async () => {
    await settle(2);
    for (let i = 0; i < 400 && window.document.querySelector('#main[aria-busy="true"], #progress.on'); i++) await new Promise((r) => setTimeout(r, 5));
    await settle(2);
  };
  await settle();
  const t = { window, document: window.document, errors, settle, idle,
    $: (s) => window.document.querySelector(s), $$: (s) => [...window.document.querySelectorAll(s)],
    go: async (h) => { window.location.hash = h; await settle(); await idle(); },
  };
  t.window.document.querySelector('[data-act="login-mode"][data-key="owner"]')?.click();
  await settle();
  t.$("#password").value = PASSWORD;
  t.$('form[data-form="login"]').requestSubmit();
  await settle(30);
  assert.ok(t.$("#main"), "signed in");
  return t;
}

/** Markup that came from the payload: an element it would make, or any on… / javascript: attribute anywhere. */
function injected(doc) {
  const out = [];
  for (const el of doc.querySelectorAll("*")) {
    for (const a of el.getAttributeNames()) {
      if (/^on/i.test(a)) out.push(`${el.tagName} ${a}`);
      if (/^(href|src|action|formaction|xlink:href)$/i.test(a) && /^\s*javascript:/i.test(el.getAttribute(a))) out.push(`${el.tagName} ${a}=javascript:`);
    }
  }
  for (const el of doc.querySelectorAll('img[src="x"], #main script, #main svg[onload], dialog script')) out.push(el.outerHTML.slice(0, 80));
  return out;
}

test("[SEC-09] L3: words phones send show as words on every screen, never as markup", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const real = globalThis.fetch;
  globalThis.fetch = async () => new Response("", { status: 404 });
  t.after(() => { globalThis.fetch = real; });
  const s = server({ plaza: true });
  const { call, env, DB } = s;
  const admin = await signIn(call);
  // What phones send, the ordinary way first.
  const a = await call("POST", "/api/reports", report(1), from(1));
  for (const n of [11, 12]) await call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "bug", text: `문의 ${n}번이에요`, info: { app: "1.36.0 (90)" } }, from(n));
  await call("POST", "/api/posts", { device: device(2), company: a.data.company.id, setup: a.data.setup, body: "첫 글이에요" }, from(2));
  const rec = (title) => ({ v: 1, kind: "theme", title, summary: "설명이에요", styles: [], body: {} });
  for (const n of [3, 4]) await call("POST", "/api/recipes", { device: device(n), recipe: rec(`작품 ${n}`), scope: "public" }, from(n));
  // Then every word the operator reads becomes the payload, as if a phone had slipped it past the checks.
  const sets = [["companies", "name"], ["inquiry_messages", "text"], ["posts", "body"], ["recipes", "title"], ["recipes", "summary"], ["recipes", "note"],
    ["recipe_requests", "body"], ["recipe_makers", "bio"], ["recipe_sets", "title"], ["recipe_sets", "summary"], ["dev_items", "title"], ["dev_items", "body"],
    ["inquiry_notes", "text"], ["nicks", "nick"],
    // 2.0.0 [RVA-11] 검토 ADM-09: 1.38·1.39에 생긴 화면이 읽는 글도(마을 돌봄, 스튜디오 요구, 운영 장부 메모, 개발 기록).
    ["care_notes", "note"], ["studio_needs", "title"], ["studio_needs", "note"], ["dev_items", "summary"]];
  for (const [table, col] of sets) {
    try {
      DB.db.prepare(`UPDATE ${table} SET ${col} = ?`).run(BAD);
    } catch (e) {
      // 그 칸이 없는 판의 표는 건너뛰어요(이름이 바뀌면 이 시험이 알려 주도록 아래에서 하나는 꼭 확인해요).
      if (!/no such (column|table)/.test(String(e?.message))) throw e;
    }
  }
  const { deviceHash, deviceId } = await import("../../src/index.js");
  for (const n of [1, 3, 4]) DB.db.prepare("INSERT OR REPLACE INTO nicks (device, nick, norm, at) VALUES (?, ?, ?, ?)").run(await deviceHash(deviceId(device(n))), BAD, `bad${n}`, AFTER);
  const inquiry = (await call("GET", "/api/admin/inquiries", undefined, admin)).data.items?.[0]?.id;

  const u = await tab(env);
  // 2.0.0 [RVA-11] 검토 ADM-09: 손으로 적은 목록 대신 관리자 앱의 모든 화면(L.TABS)을 돌아요. 새 화면이 생기면 저절로 들어와요.
  const screens = [...Object.keys(adminLib().TABS), "more",
    "community?show=all", "community?show=limits", "recipes?tab=images", `companies/${a.data.company.id}`, ...(inquiry ? [`inquiries/${inquiry}`] : [])];
  assert.ok(screens.length >= 40, `all screens (${screens.length})`);
  let seen = 0;
  for (const sc of screens) {
    await u.go(`#/${sc}`);
    assert.deepEqual(injected(u.document), [], `#/${sc} drew the payload as markup`);
    if (u.$("#main")?.textContent.includes("<img src=x")) seen++;
    // Open the first line of a list in place too, where one opens.
    const row = u.$("#main .rows .row, #main [data-act$='-open']");
    if (row) {
      row.click();
      await u.settle(20);
      assert.deepEqual(injected(u.document), [], `#/${sc} › first line drew the payload as markup`);
    }
  }
  // 2.0.0 [RVA-11] 검토 ADM-09 '배치': Claude가 준 결과 덩어리(넘김 묶음 이름·답 글·기록)도 붙여 넣은 미리 보기에서 글로만 보여요.
  await u.go("#/desk");
  const paste = u.$("#sort-result");
  assert.ok(paste, "the result paste box on 운영 장부");
  paste.value = `묶음 h${BAD}\nq0123456789abcdef 답 ${BAD}\nd0123456789abcdef 기록 ${BAD}`;
  u.$('[data-act="sort-result-check"]').click();
  await u.settle(40);
  assert.deepEqual(injected(u.document), [], "the pasted result drew the payload as markup");
  assert.equal(globalThis.__xss, undefined);
  assert.equal(u.window.__xss, undefined);
  assert.ok(seen >= 3, `the payload is shown as words on several screens (${seen})`);
  assert.deepEqual(u.errors, []);
});
