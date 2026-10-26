// [ADMIN-18] The developer app's pure helpers (src/admin-lib.js) and what the app file promises: every
// server error in plain words, no inline style (the CSP forbids it), colour pairs readable. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { adminLib } from "../src/admin-lib.js";
import { adminFile } from "../src/admin-app.js";

const L = adminLib();
const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

test("[ADMIN-18] every error code the server can send has words in the app", () => {
  const src = read("../src/index.js");
  const codes = new Set([
    ...[...src.matchAll(/error: "([A-Za-z_]+)"/g)].map((m) => m[1]),
    ...[...src.matchAll(/\b[rR]efuse\("([A-Za-z_]+)"/g)].map((m) => m[1]),
    ...[...src.matchAll(/code: "([A-Za-z_]+)"/g)].map((m) => m[1]),
    // refuse(field, field) and refuse(key, key): the field names plainText and pick() are given.
    ...[...src.matchAll(/plainText\([^,]+, [^,]+, "([A-Za-z_]+)"/g)].map((m) => m[1]),
    ...[...src.matchAll(/pick\("([A-Za-z]+)"/g)].map((m) => m[1]),
    // refuse(why, …): what PERSONAL finds.
    ...[...src.slice(src.indexOf("const PERSONAL = ["), src.indexOf("];", src.indexOf("const PERSONAL = ["))).matchAll(/\["([a-z]+)"/g)].map((m) => m[1]),
  ]);
  assert.ok(codes.size > 80, `found ${codes.size} codes`);
  const missing = [...codes].filter((c) => !Object.hasOwn(L.ERRORS, c));
  assert.deepEqual(missing, [], "src/admin-lib.js ERRORS needs words for these");
  assert.equal(L.errorText("nope"), "처리하지 못했어요. 잠시 뒤 다시 해 주세요");
  for (const [code, text] of Object.entries(L.ERRORS)) assert.ok(!/[A-Za-z]{4,}/.test(text.replace(/[A-Z_]{3,}|versionCode|Cloudflare|Play|Excel|MONEY_STATS|SHARE_\w+|PLAY_\w+|ADMIN_\w+|Ctrl/g, "")), `${code} reads as Korean`);
});

test("[ADMUX-07] the app's script has no inline style, which the page's CSP would block", async () => {
  const js = await adminFile("/admin/app.js").text();
  assert.ok(js.length > 1000);
  assert.ok(!/style="/.test(js), "no style=\"…\" in markup");
  assert.ok(!L.barChart([1, 2, 3], { labels: ["a", "b", "c"], before: [1, 1, 1] }).includes("style="));
});

test("[ADMUX-02] a screen and its filters go into the address and back", () => {
  const cases = [
    ["inquiries", null, { status: "answered", who: "mine", topic: "alarm", q: "알람 소리", sort: "sla" }],
    ["dev", "d123", { status: "done", q: "" }],
    ["audit", null, { actor: "a123456789012345", kind: "inquiries", from: "2026-10-01", to: "2026-10-23" }],
    ["home", null, {}],
  ];
  for (const [name, id, params] of cases) {
    const hash = L.routeHash(name, id, params);
    const back = L.parseRoute(hash);
    assert.equal(back.name, name);
    assert.equal(back.id, id);
    for (const [k, v] of Object.entries(params)) assert.equal(back.params[k], v, `${hash} ${k}`);
  }
  assert.equal(L.routeHash("inquiries", null, { status: "open", sort: "default" }), "#/inquiries", "defaults stay out");
  assert.equal(L.routeHash("inquiries", null, { q: "a&b=c" }), "#/inquiries?q=a%26b%3Dc");
  // Unknown filters and broken escapes are dropped, not thrown.
  assert.deepEqual(L.parseRoute("#/inquiries?evil=1&q=%E0%A4").params, L.FILTERS.inquiries);
  assert.equal(L.parseRoute("").name, "home");
  // 놓친 것 holds the status to waiting.
  assert.equal(L.parseRoute("#/inquiries?status=closed&who=late").params.status, "open");
});

// AN 1.28 시작
const OWNER = { owner: true, perms: {} };
const ASKER = { owner: false, perms: { inquiries: "edit" } };
const PLAZA = { owner: false, perms: { review: "edit", community: "view", companies: "view" } };

test("[ADMUX-30] six hubs: every pre-1.28 screen has a place, and an admin sees only the hubs and tabs they may use", () => {
  assert.deepEqual(L.HUBS.map((h) => h.label), ["오늘", "문의", "광장", "출시", "분석", "관리"]);
  // 1.28 통합계획 2-2, plus 기능별 효과 (AN-D2).
  assert.deepEqual(L.HUBS.map((h) => h.tabs), [["home"], ["inquiries", "dev", "templates", "sla"], ["review", "versions", "community", "companies", "shares", "adopt"],
    ["flags", "types", "notices", "chores", "gates"], ["metrics", "effects", "audit"], ["admins", "rewards", "server", "account"]]);
  const tabs = L.HUBS.flatMap((h) => h.tabs);
  assert.equal(new Set(tabs).size, tabs.length, "each tab once");
  for (const t of tabs) assert.ok(L.TABS[t]?.label, `${t} has a name`);
  // The 15 menus of 1.27 all land somewhere (받은함 → 오늘, 운영 → 출시, 설정 → its cards' new tabs).
  for (const old of ["home", "inbox", "review", "inquiries", "dev", "companies", "versions", "community", "rewards", "shares", "ops", "metrics", "admins", "audit", "settings"]) {
    assert.ok(L.hubOf(old), `${old} has a hub`);
  }
  assert.deepEqual(L.visibleHubs(OWNER).map((h) => h.key), ["today", "ask", "plaza", "release", "insight", "manage"]);
  assert.deepEqual(L.visibleHubs(ASKER).map((h) => [h.key, h.tabs]), [["today", ["home"]], ["ask", ["inquiries", "templates"]], ["manage", ["account"]]]);
  assert.deepEqual(L.visibleHubs(PLAZA).map((h) => h.key), ["today", "plaza", "manage"]);
  assert.deepEqual(L.visibleHubs(PLAZA).find((h) => h.key === "plaza").tabs, ["review", "versions", "community", "companies"]);
  // 운영 keeps 기능 관리 until the server knows the flags area (AN-D3); 설정 alone opens 보상 for 친구 초대 (AN-D4).
  assert.ok(L.tabVisible("flags", { perms: { ops: "view" } }));
  assert.ok(L.tabVisible("rewards", { perms: { settings: "view" } }));
  assert.ok(!L.tabVisible("server", ASKER) && L.tabVisible("server", OWNER));
});

test("[ADMUX-30] the phone's bottom tabs: 오늘 and three hubs, then 더 보기; all of them when they fit", () => {
  const all = L.visibleHubs(OWNER);
  assert.deepEqual(L.bottomHubs(all), { tabs: ["today", "ask", "plaza", "release"], lit: "today", back: false, more: true });
  assert.deepEqual(L.bottomHubs(all, ["insight", "ask"], "audit"), { tabs: ["today", "insight", "ask", "plaza"], lit: "insight", back: false, more: true });
  assert.deepEqual(L.bottomHubs(all, [], "admins"), { tabs: ["today", "ask", "plaza", "release"], lit: "more", back: true, more: true });
  assert.deepEqual(L.bottomHubs(all, [], "more").lit, "more");
  assert.deepEqual(L.bottomHubs(L.visibleHubs(ASKER), [], "templates"), { tabs: ["today", "ask", "manage"], lit: "ask", back: false, more: false });
});

test("[ADMUX-34] badges count only what the admin must do, each thing once, and 더 보기 is only its hubs", () => {
  const o = { inquiries: 2, devTodo: 3, pending: 4, asks: 1, flagged: 0, flaggedPosts: 2, versionConflicts: 1, opsLeft: 5, owed: 1, admins: { waiting: 1, phones: 1 } };
  assert.equal(L.hubBadge("ask", o, OWNER), 5);
  assert.equal(L.hubBadge("plaza", o, OWNER), 7, "여러 판 is on its tab, not added again");
  assert.equal(L.tabBadge("versions", o, OWNER), 1);
  assert.equal(L.hubBadge("release", o, OWNER), 5);
  assert.equal(L.hubBadge("insight", o, OWNER), 0);
  assert.equal(L.hubBadge("manage", o, OWNER), 3);
  assert.equal(L.hubBadge("today", o, OWNER), 0, "오늘 does not count the others again");
  // Without 처리 (devStatus) 접수 is not this admin's to clear; numbers of areas they cannot see are not theirs.
  assert.equal(L.hubBadge("ask", o, ASKER), 2);
  assert.equal(L.hubBadge("plaza", o, ASKER), 0);
  assert.deepEqual(L.waiting("plaza", o, OWNER).map((x) => `${x.label} ${x.n}`), ["검토 5", "여러 판 1", "신고 글 2"]);
  assert.deepEqual(L.waiting("today", o, OWNER).map((x) => `${x.label} ${x.n}`), ["문의 5", "광장 7", "출시 5", "관리 3"]);
});

test("[ADMUX-35] old addresses go to the new places with their filters, and this device's choices move once", () => {
  const go = (h) => { const r = L.redirect(L.parseRoute(h)); return L.routeHash(r.name, r.id, r.params); };
  assert.equal(go("#/inbox?view=mine"), "#/home?view=mine");
  assert.equal(go("#/inbox"), "#/home");
  assert.equal(go("#/ops"), "#/flags");
  assert.equal(go("#/ops?month=2026-11"), "#/chores?month=2026-11");
  assert.equal(go("#/review?show=asks"), "#/review?show=asks");
  assert.equal(go("#/companies/abc"), "#/companies/abc");
  const p = L.migratePrefs({ theme: "dark", tabs: ["inbox", "audit", "ops", "review"], views: [{ name: "내 것", hash: "#/inbox?view=mine" }, { name: "답함", hash: "#/inquiries?status=answered" }] });
  assert.deepEqual(p.tabs, ["insight", "release", "plaza"]);
  assert.deepEqual(p.views.map((v) => v.hash), ["#/home?view=mine", "#/inquiries?status=answered"]);
  assert.equal(p.v, 2);
  assert.equal(p.theme, "dark");
  assert.equal(L.migratePrefs(p), p, "only once");
});
// AN 1.28 끝

test("[ADMUX-07] word by word before and after, escaped", () => {
  assert.equal(L.wordDiff("알람이 늦게 울려요", "알람이 가끔 늦게 울려요"), "알람이 <ins>가끔 </ins>늦게 울려요");
  assert.equal(L.wordDiff("a b", "a c"), "a <del>b</del><ins>c</ins>");
  assert.equal(L.wordDiff("<b>", "<i>"), "<del>&lt;b&gt;</del><ins>&lt;i&gt;</ins>");
  assert.equal(L.wordDiff("", "새 글"), "<ins>새 글</ins>");
});

test("[ADMUX-07] CSV for a spreadsheet: quoted where needed, formulas never run, Korean kept", () => {
  const out = L.csv([{ a: "=1+1", b: 'say "hi", ok' }, { a: "알람", b: null }], [["가", (r) => r.a], ["나", (r) => r.b]]);
  assert.equal(out, '﻿가,나\r\n\'=1+1,"say ""hi"", ok"\r\n알람,\r\n');
});

test("[ADMUX-07] chart axes use round steps", () => {
  assert.deepEqual(L.ticks(0), [0, 1]);
  assert.deepEqual(L.ticks(7), [0, 2, 4, 6, 8]);
  assert.equal(L.ticks(100).at(-1), 100);
  assert.ok(L.ticks(100).every((t, i, a) => i === 0 || t - a[i - 1] === a[1]));
  assert.ok(L.ticks(1234).at(-1) >= 1234);
  const svg = L.barChart([0, 5, 10], { labels: ["1일", "2일", "3일"], name: "새 문의" });
  assert.match(svg, /<table class="sr">/);
  assert.equal((svg.match(/<rect class="bar"/g) ?? []).length, 3);
});

test("[ADMUX-05] the colour tokens read at 4.5:1 or more on their backgrounds, light and dark", async () => {
  const css = await adminFile("/admin/app.css").text();
  const tokens = (block) => Object.fromEntries([...block.matchAll(/--([a-z-]+):\s*(#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})\b)/g)].map((m) => [m[1], m[2]]));
  const light = tokens(css.slice(css.indexOf(":root"), css.indexOf("}", css.indexOf(":root"))));
  const darkAt = css.indexOf("[data-theme=\"dark\"]");
  const dark = { ...light, ...tokens(css.slice(darkAt, css.indexOf("}", darkAt))) };
  for (const [name, t] of [["light", light], ["dark", dark]]) {
    for (const fg of ["fg", "muted", "faint"]) {
      assert.ok(L.contrast(t[fg], t.bg) >= 4.5, `${name} --${fg} ${t[fg]} on --bg ${t.bg}: ${L.contrast(t[fg], t.bg).toFixed(2)}`);
      assert.ok(L.contrast(t[fg], t.surface) >= 4.5, `${name} --${fg} on --surface: ${L.contrast(t[fg], t.surface).toFixed(2)}`);
    }
  }
  assert.equal(Math.round(L.contrast("#000000", "#FFFFFF")), 21);
});

test("[ADMUX-04] keys: g and a letter go places, a text box only takes the ones with Ctrl", () => {
  assert.deepEqual(L.keyAction("g"), { act: "pending", key: "g" });
  assert.deepEqual(L.keyAction("i", {}, false, "g"), { act: "go", to: "inquiries" });
  assert.equal(L.keyAction("z", {}, false, "g"), null);
  assert.deepEqual(L.keyAction("j"), { act: "move", by: 1 });
  assert.deepEqual(L.keyAction("3"), { act: "template", n: 3 });
  assert.equal(L.keyAction("j", {}, true), null, "typing j in a box is a j");
  assert.deepEqual(L.keyAction("k", { ctrl: true }, true), { act: "palette" });
  assert.deepEqual(L.keyAction("Enter", { meta: true }, true), { act: "send" });
  assert.deepEqual(L.keyAction("Escape", {}, true), { act: "escape" });
  for (const go of Object.values(L.GO)) assert.ok(L.KEYS.some(([k]) => k.includes("g ")), go);
});

test("[ADMUX-04] the palette finds entries holding every word", () => {
  const entries = [{ label: "문의", hint: "화면" }, { label: "개발사항", hint: "화면" }, { label: "알람이 늦게 울려요", hint: "문의" }];
  assert.deepEqual(L.paletteFilter(entries, "문의").map((e) => e.label), ["문의", "알람이 늦게 울려요"]);
  assert.deepEqual(L.paletteFilter(entries, "알람 문의").map((e) => e.label), ["알람이 늦게 울려요"]);
  assert.equal(L.paletteFilter(entries, "").length, 3);
});

test("[ADMIN-16] saved answers: filled in from the question, its topic first", () => {
  assert.equal(L.fillTemplate("{앱버전}에서 {주제} 문제를 고쳤어요 ({오늘})", { app: "1.22.0 (33)", topic: "알람", today: "10월 23일" }),
    "1.22.0에서 알람 문제를 고쳤어요 (10월 23일)");
  assert.equal(L.fillTemplate("{앱버전}", {}), "최신");
  const list = [{ id: 1, topic: "", uses: 9 }, { id: 2, topic: "alarm", uses: 0 }, { id: 3, topic: "", uses: 2 }];
  assert.deepEqual(L.sortTemplates(list, "alarm").map((t) => t.id), [2, 1, 3]);
});

test("[ADMUX-01] a slow answer for a screen the admin left is not drawn", () => {
  const seq = L.sequencer();
  const a = seq.next("screen");
  const b = seq.next("screen");
  assert.equal(seq.current("screen", a), false);
  assert.equal(seq.current("screen", b), true);
  assert.equal(seq.current("other", 1), false);
});

test("[ADMUX-06] answer targets as words", () => {
  assert.deepEqual(L.slaText(30), { text: "30분 남음", tone: "warn" });
  assert.deepEqual(L.slaText(300), { text: "5시간 남음", tone: "" });
  assert.deepEqual(L.slaText(-90), { text: "목표 1시간 넘김", tone: "bad" });
  assert.equal(L.slaText(null), null);
  assert.deepEqual(L.appVersion("1.22.0 (33)"), [33, "1.22.0"]);
});

test("[ADMIN-15] permissions in one line, yes-or-no areas as such", () => {
  const areas = { review: "검토", devStatus: "개발사항 처리", audit: "활동 기록" };
  assert.equal(L.permSummary({ review: "edit", devStatus: "edit", audit: "view" }, areas, ["devStatus"]), "검토 고치기 · 개발사항 처리 · 활동 기록 보기");
  assert.equal(L.permSummary({}, areas), "권한 없음");
  assert.equal(L.can({ owner: false, perms: { review: "view" } }, "review", "edit"), false);
  assert.equal(L.can({ owner: true, perms: {} }, "review", "edit"), true);
});

test("[ADMUX-11] waits in plain words, and a question goes out without mail, phone or amounts", () => {
  assert.equal(L.waited(0), "1시간 안 기다림");
  assert.equal(L.waited(5), "5시간째 기다림");
  assert.equal(L.waited(50), "2일째 기다림");
  assert.equal(L.waited(null), "");
  assert.equal(L.maskPersonal("a.b@mail.com 010-1234-5678 로 연락, 월급 2,500,000원\n안 맞아요"), "••• ••• 로 연락, 월급 ••• 안 맞아요");
});

test("[ADMUX-09][ADMUX-10] same department side by side, why points-full waits, one count for 검토", () => {
  const a = { company: { id: "c" }, dept: "간호", at: "2026-10-01", lines: [["야간 수당", "50%"], ["주휴", "있음"]] };
  const b = { company: { id: "c" }, dept: "간호", at: "2026-10-05", lines: [["야간 수당", "30%"], ["주휴", "있음"]] };
  const lone = { company: { id: "c" }, dept: "원무", at: "2026-10-02", lines: [["주휴", "있음"]] };
  const m = L.setupDiff([b, a, lone]);
  assert.deepEqual(m.get(a), { label: "제보 1", diff: "다른 점: 야간 수당 50%" });
  assert.deepEqual(m.get(b), { label: "제보 2", diff: "다른 점: 야간 수당 30%" });
  assert.equal(m.has(lone), false);
  assert.equal(L.waitReason({ points: 2, threshold: 5 }), "");
  assert.equal(L.waitReason({ points: 5, threshold: 5, agree: 60 }), "달라요가 많아 개발자 확인 대기");
  assert.equal(L.waitReason({ points: 5, threshold: 5, turnedDown: true }), "전에 거절된 설정이라 개발자 확인 대기");
  assert.equal(L.waitReason({ points: 6, threshold: 5, agree: 90 }), "개발자 확인 대기");
  assert.equal(L.reviewCount({ pending: 2, asks: 1, flagged: 3 }), 6);
  assert.equal(L.reviewCount(null), 0);
});
