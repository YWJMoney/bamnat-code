// 2.0.0 갈래 Q1 4c [RVQ-36..44·46] 검토 22b 장터 줄: 장터 색인과 찾기(SVC-04·55), 보류된 표(SVC-53·U22b-02), 판이 못 읽는 작품(SVC-54),
// 이야기 더 보기(U22-05), 요청 판 완성 확인(SVC-29·62·U22b-08·I22bb-01), 글꼴 출처·주인 여럿(SVC-64·I22bc-02). 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { IX_READING } from "../src/recipes.js";
import { fontCredit, fontIdOf } from "../src/recipe-fonts.js";
import { fakeFont } from "./font-fixture.mjs";
import { device, from, neighbour, server, signIn } from "./helpers.mjs";

/** After 처리방침 3.8 (나눔 판 쓰기) is in force: 2026-11-30 12:00 KST. */
const AFTER = Date.parse("2026-11-30T03:00:00Z");
const HOUR = 3600e3;
const DAY = 24 * HOUR;

const recipe = (extra = {}) => ({
  format: "bamnat-recipe", v: 1, title: "야간 많은 교대 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
  ...extra,
});
const up = (call, n, extra = {}, scope = "public") => call("POST", "/api/recipes", { device: device(n), recipe: recipe(extra), scope }, from(n));
const vote = (call, code, n, ip, value = 1) => call("POST", `/api/recipes/${code}/vote`,
  { device: device(n), value, ...(value === -1 ? { reason: "odd", opinion: "야간 칸 색이 잘 안 보여요" } : {}) }, from(ip));
const hourly = async (env, at) => {
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: at }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
};

test("[RVQ-39] SVC-53·U22b-02 한 네트워크의 넘친 표는 묶음 이름(해시 없이)으로 보류되고, 운영자가 묶음째 세거나 버려요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB, env } = server({ plaza: true });
  const code = (await up(call, 1)).data.code;
  // 병원 와이파이 하나: 한 시간에 한 작품 30표까지 세고, 넘친 표는 보류.
  for (let n = 100; n < 130; n++) assert.equal((await vote(call, code, n, 7)).data.held, undefined, `폰 ${n}`);
  const over = await vote(call, code, 130, 7);
  assert.deepEqual([over.status, over.data.held, over.data.likes], [200, true, 30]);
  await vote(call, code, 131, 7, -1);
  // 다른 작품은 그 네트워크도 따로 세요(한 작품 30표, 모든 작품 300표).
  const other = (await up(call, 2, { title: "현장 단가 모음" })).data.code;
  assert.equal((await vote(call, other, 132, 7)).data.held, undefined);
  const rows = DB.db.prepare("SELECT cluster, net FROM recipe_votes WHERE code = ? AND held = 1").all(code);
  assert.deepEqual(rows.map((r) => r.cluster), ["11-30 12·1", "11-30 12·1"], "KST 시간과 그 시간의 몇째 네트워크");
  assert.ok(rows.every((r) => r.net), "2시간 안에는 같은 네트워크를 같은 묶음에 넣으려고 네트워크 해시를 둬요");
  const admin = await signIn(call);
  const list = (await call("GET", "/api/admin/recipes?tab=votes", undefined, admin)).data;
  assert.deepEqual(list.held, { votes: 2, works: 1 });
  assert.deepEqual(list.items.map((r) => r.code), [code]);
  assert.deepEqual(list.items[0].heldVotes.map((h) => [h.group, h.likes, h.dislikes, h.n]), [["11-30 12·1", 1, 1, 2]]);
  assert.ok(!JSON.stringify(list).includes(rows[0].net), "해시는 관리자 화면에도 가지 않아요");
  // 다른 보기에서도 보류된 표 수가 와요.
  assert.deepEqual((await call("GET", "/api/admin/recipes?tab=flagged", undefined, admin)).data.held, { votes: 2, works: 1 });
  const one = await call("POST", "/api/admin/recipes/votes/release", { code, group: "11-30 12·1" }, admin);
  assert.deepEqual(one.data, { released: 2 });
  const after = (await call("GET", `/api/recipes/${code}`)).data;
  assert.deepEqual([after.likes, after.dislikes], [31, 1]);
  // 두 시간이 지나면 정기 정리가 네트워크 해시를 지우고 묶음 이름만 남아요.
  t.mock.timers.tick(2 * HOUR + 60e3);
  await hourly(env, Date.now());
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM recipe_votes WHERE net IS NOT NULL").get().n, 0);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM recipe_votes WHERE cluster = '11-30 12·1'").get().n, 2);
});

test("[RVQ-39] SVC-53 버리기는 그 묶음만, 하루 지난 오래된 설치의 표는 저절로 세고 새 설치의 표는 운영자를 기다려요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB, env } = server({ plaza: true });
  const code = (await up(call, 1)).data.code;
  // 오래 쓴 설치 넷(이웃)과 새 설치 둘이 한 네트워크에서.
  await neighbour(DB, 140, 141, 142, 143);
  for (let n = 100; n < 130; n++) await vote(call, code, n, 7);
  for (const n of [140, 141, 142, 143, 150, 151]) assert.equal((await vote(call, code, n, 7)).data.held, true);
  // 다음 시간 다른 네트워크의 넘친 표는 다른 묶음.
  t.mock.timers.tick(HOUR);
  for (let n = 200; n < 230; n++) await vote(call, code, n, 8);
  for (const n of [230, 231, 232]) await vote(call, code, n, 8, -1);
  const admin = await signIn(call);
  const groups = (await call("GET", "/api/admin/recipes?tab=votes", undefined, admin)).data.items[0].heldVotes.map((h) => [h.group, h.n]);
  assert.deepEqual(groups, [["11-30 12·1", 6], ["11-30 13·1", 3]]);
  const drop = await call("POST", "/api/admin/recipes/votes/drop", { code, group: "11-30 13·1" }, admin);
  assert.deepEqual(drop.data, { dropped: 3 });
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM recipe_votes WHERE held = 1").get().n, 6);
  // 하루가 지나면 이웃 넷의 표는 저절로 세요(그때 이미 3일 넘게 쓴 설치), 새 설치 둘은 그대로 기다려요.
  t.mock.timers.tick(DAY);
  await hourly(env, Date.now());
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM recipe_votes WHERE held = 1").get().n, 2);
  assert.equal((await call("GET", `/api/recipes/${code}`)).data.likes, 64);
  // 30일이 지나면 남은 보류된 표는 버려요.
  t.mock.timers.tick(30 * DAY);
  await hourly(env, Date.now());
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM recipe_votes WHERE held = 1").get().n, 0);
  const why = DB.db.prepare("SELECT action, actor FROM recipe_actions WHERE code = ? AND action = 'drop_held' ORDER BY at").all(code);
  assert.deepEqual(why.map((w) => w.actor).slice(-1), ["자동 정리"]);
});

test("[RVQ-36] SVC-04 장터 색인: 올릴 때 재 두고, 옛 규칙으로 잰 줄은 목록이 본문으로 읽고 매시 정리가 다시 재요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB, env } = server({ plaza: true });
  const code = (await up(call, 1, { cover: { a: 4282339765, b: 4294940928, emoji: "🌙" } })).data.code;
  const row = DB.db.prepare("SELECT ix_v, ix_emoji FROM recipes WHERE code = ?").get(code);
  assert.equal(row.ix_v, IX_READING);
  const list = async (q = "") => (await call("GET", `/api/recipes?sort=new${q}`)).data;
  const first = (await list()).items.find((x) => x.code === code);
  assert.ok(first, "목록에 나와요");
  // 옛 규칙의 줄(ix_v 비움)도 목록에 같은 카드로 나와요.
  DB.db.prepare("UPDATE recipes SET ix_v = NULL WHERE code = ?").run(code);
  const old = (await list()).items.find((x) => x.code === code);
  assert.deepEqual(old, first);
  await hourly(env, Date.now());
  assert.equal(DB.db.prepare("SELECT ix_v FROM recipes WHERE code = ?").get(code).ix_v, IX_READING);
});

test("[RVQ-37] SVC-55·U22b-06 글자 없는 찾기는 '낱말로 찾아 주세요'(ask), 표지 그림 하나는 그 그림만, 일상 말은 그 유형으로", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const moon = (await up(call, 1, { title: "밤 근무 모음", cover: { a: 4282339765, b: 4294940928, emoji: "🌙" } })).data.code;
  const shop = (await up(call, 2, { title: "가게 매출 장부", styles: ["SHOP"] })).data.code;
  const find = async (q) => (await call("GET", `/api/recipes?sort=new&q=${encodeURIComponent(q)}`)).data;
  const none = await find("!!! ...");
  assert.deepEqual([none.items.length, none.ask], [0, "words"]);
  const emoji = await find("🌙");
  assert.equal(emoji.ask, "words");
  assert.deepEqual(emoji.items.map((x) => x.code), [moon]);
  // 숫자 한 글자로 모든 작품의 표지(색 값)가 걸리지 않아요.
  assert.ok(!(await find("0")).items.some((x) => x.code === shop));
  assert.deepEqual((await find("간호")).items.map((x) => x.code), [moon], "간호 → 교대");
});

test("[RVQ-42] SVC-54·U22b-05 그 판이 못 읽는 작품은 보내지 않고 426 {error: update, minApp}; 만든 분과 맞는 판은 열려요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const code = (await up(call, 1)).data.code;
  DB.db.prepare("UPDATE recipes SET min_app = 54 WHERE code = ?").run(code);
  const asOld = await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(2) });
  assert.deepEqual([asOld.status, asOld.data.error, asOld.data.minApp, asOld.data.body], [426, "update", 54, undefined]);
  assert.equal((await call("GET", `/api/recipes/${code}?app=53`, undefined, { "x-device": device(2) })).status, 426);
  assert.equal((await call("GET", `/api/recipes/${code}?app=54`, undefined, { "x-device": device(2) })).status, 200);
  // I22bb-06: 만든 분은 어느 판부터 보이는지 알아요.
  const maker = await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(1) });
  assert.deepEqual([maker.status, maker.data.minApp], [200, 54]);
  // 1.39(53)가 읽는 작품은 옛 앱에도 그대로.
  DB.db.prepare("UPDATE recipes SET min_app = 53 WHERE code = ?").run(code);
  assert.equal((await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(2) })).status, 200);
});

test("[RVQ-43] U22-05 이야기는 최근 300개가 오고 그 앞은 before로 더 봐요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const code = (await up(call, 1)).data.code;
  // 320개를 바로 넣어요(쓰기 한도와 상관없이 목록만 봐요).
  const first = await call("POST", `/api/recipes/${code}/talk`, { device: device(1), body: "처음 남긴 이야기예요", kind: "opinion" }, from(1));
  assert.equal(first.status, 201, JSON.stringify(first.data));
  const tmpl = DB.db.prepare("SELECT * FROM posts WHERE id = ?").get(first.data.id);
  const cols = Object.keys(tmpl);
  const ins = DB.db.prepare(`INSERT INTO posts (${cols.join(", ")}) VALUES (${cols.map(() => "?").join(", ")})`);
  for (let i = 1; i < 320; i++) ins.run(...cols.map((c) => (c === "id" ? `${tmpl.id}${i}` : c === "created_at" ? tmpl.created_at + i * 1000 : c === "body" ? `이야기 ${i}` : tmpl[c])));
  const page = (await call("GET", `/api/recipes/${code}/talk`)).data;
  assert.equal(page.posts.length, 300);
  assert.equal(page.more, true);
  assert.equal(page.posts.at(-1).body, "이야기 319", "가장 새 것이 와요");
  const older = (await call("GET", `/api/recipes/${code}/talk?before=${encodeURIComponent(page.posts[0].at)}`)).data;
  assert.equal(older.posts.length, 20);
  assert.ok(!older.more);
  assert.equal(older.posts[0].body, "처음 남긴 이야기예요");
});

test("[RVQ-44] SVC-29·62·I22bb-01 요청 판: 세 개까지 맡고, 보이는 작품만 완성, 7일 답 없으면 저절로 완성, 14일 지난 '만드는 중'은 다시 열려요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB, env } = server({ plaza: true });
  const ask = async (n, body) => (await call("POST", "/api/recipes/requests", { device: device(n), style: "SHIFT", body }, from(n))).data.id;
  const act = (q, n, what, body = {}) => call("POST", `/api/recipes/requests/${q}/${what}`, { device: device(n), ...body }, from(n));
  const qs = [];
  for (const [i, body] of ["간호사 3교대 알람 세트", "야간 끝나고 잠 시간표", "주말 근무 가계부", "현장 단가 메모"].entries()) qs.push(await ask(10 + i, body));
  await neighbour(DB, 4);
  for (const q of qs.slice(0, 3)) assert.equal((await act(q, 4, "take")).data.state, "making");
  const four = await act(qs[3], 4, "take");
  assert.deepEqual([four.status, four.data.error, four.data.max], [409, "taking", 3]);
  // 가려진 내 작품으로는 완성할 수 없어요.
  const mine = (await up(call, 4, { title: "간호사 알람 세트" })).data.code;
  DB.db.prepare("UPDATE recipes SET state = 'hidden' WHERE code = ?").run(mine);
  assert.deepEqual([(await act(qs[0], 4, "done", { code: mine })).status], [409]);
  DB.db.prepare("UPDATE recipes SET state = 'visible' WHERE code = ?").run(mine);
  assert.equal((await act(qs[0], 4, "done", { code: mine })).data.checking, true);
  // 요청한 분이 '아니에요'면 다시 기다리는 요청으로.
  assert.equal((await act(qs[0], 10, "check", { ok: false })).data.state, "waiting");
  await act(qs[0], 4, "take");
  await act(qs[0], 4, "done", { code: mine });
  // 7일 동안 답이 없으면 저절로 완성(checked 2), 배지는 '맞아요'만 세요.
  t.mock.timers.tick(7 * DAY + HOUR);
  await hourly(env, Date.now());
  const q0 = DB.db.prepare("SELECT state, checked FROM recipe_requests WHERE id = ?").get(qs[0]);
  assert.deepEqual([q0.state, q0.checked], ["done", 2]);
  // 14일이 지난 '만드는 중'은 다시 열리고 맡은 분도 비워져요.
  t.mock.timers.tick(7 * DAY);
  await hourly(env, Date.now());
  const q1 = DB.db.prepare("SELECT state, maker, taken_at FROM recipe_requests WHERE id = ?").get(qs[1]);
  assert.deepEqual([q1.state, q1.maker, q1.taken_at], ["waiting", null, null]);
});

test("[RVQ-44] I22bb-01 스튜디오 요구로 올라온 요청의 완성은 운영자가 '맞아요'·'아니에요'로 닫아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  await call("GET", "/api/recipes/requests?style=SHIFT");
  DB.db.prepare(`INSERT INTO recipe_requests (id, device, style, body, state, created_at, updated_at) VALUES ('qAAAAAAAAAAAA', 'operator', 'ALL', '달력 칸 가운데 정렬', 'waiting', ?, ?)`)
    .run(AFTER, AFTER);
  await neighbour(DB, 4);
  const act = (what, body = {}) => call("POST", `/api/recipes/requests/qAAAAAAAAAAAA/${what}`, { device: device(4), ...body }, from(4));
  await act("take");
  const code = (await up(call, 4, { title: "가운데 달력" })).data.code;
  await act("done", { code });
  const admin = await signIn(call);
  const row = (await call("GET", "/api/admin/recipes/requests", undefined, admin)).data.items[0];
  assert.deepEqual([row.state, row.operator, typeof row.checkBy], ["check", true, "string"]);
  const no = await call("POST", "/api/admin/recipes/requests/qAAAAAAAAAAAA/check-no", {}, admin);
  assert.equal(no.data.state, "waiting");
  assert.equal((await call("POST", "/api/admin/recipes/requests/qAAAAAAAAAAAA/check-ok", {}, admin)).status, 409);
  await act("take");
  await act("done", { code });
  const ok = await call("POST", "/api/admin/recipes/requests/qAAAAAAAAAAAA/check-ok", {}, admin);
  assert.deepEqual([ok.data.state, ok.data.confirmed], ["done", true]);
  assert.equal(DB.db.prepare("SELECT checked FROM recipe_requests WHERE id = 'qAAAAAAAAAAAA'").get().checked, 1);
});

test("[RVQ-46] SVC-64·I22bc-02 글꼴: 받는 분께 만든 곳·라이선스 페이지가 가고, 같은 파일을 보낸 두 분 중 한 분이 지워도 글꼴은 남아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  // 만든 곳 줄은 평범한 글만, 라이선스 페이지는 라이선스 사이트만.
  assert.deepEqual(fontCredit({ maker: "산돌", copyright: "Copyright 2020 Sandoll", licenseUrl: "http://openfontlicense.org" }),
    { maker: "산돌", copyright: "Copyright 2020 Sandoll", licenseUrl: "https://openfontlicense.org", says: "ofl" });
  assert.equal(fontCredit({ maker: "연락 010-1234-5678", licenseUrl: "https://evil.example/ofl" }).licenseUrl, undefined);
  assert.equal(fontCredit({ maker: "연락 010-1234-5678" }).maker, undefined);
  const font = fakeFont({ maker: "밤길 글꼴 공방", copyright: "Copyright 2026 밤길" });
  const id = await fontIdOf(font);
  const post = (n, name = "밤길체") => call("POST", "/api/recipes/fonts", { device: device(n), name, license: "ofl", ttf: font.toString("base64") }, from(n));
  assert.deepEqual([(await post(1, "밤낮 공식 글꼴")).status, (await post(1, "밤낮 공식 글꼴")).data.error], [400, "name_taken"]);
  assert.equal((await post(1)).status, 201);
  assert.equal((await post(2)).data.again, true);
  const admin = await signIn(call);
  assert.equal((await call("POST", "/api/admin/recipes/fonts/u0000000000000000/ok", {}, admin)).status, 404);
  const waiting = (await call("GET", "/api/admin/recipes/fonts", undefined, admin)).data.items.find((f) => f.id === id);
  assert.equal(waiting.owners, 2);
  assert.equal(waiting.credit.maker, "밤길 글꼴 공방");
  await call("POST", `/api/admin/recipes/fonts/${id}/ok`, {}, admin);
  const meta = (await call("GET", `/api/recipes/fonts/${id}`)).data;
  assert.equal(meta.credit.maker, "밤길 글꼴 공방");
  // 처음 보낸 분이 모두 지우기를 해도 두 번째 분의 글꼴로 남아요.
  await call("DELETE", "/api/mine", undefined, { "x-device": device(1) });
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 200);
  await call("DELETE", "/api/mine", undefined, { "x-device": device(2) });
  assert.equal((await env.DB.prepare("SELECT COUNT(*) AS n FROM recipe_fonts").first()).n, 0);
  assert.equal((await env.DB.prepare("SELECT COUNT(*) AS n FROM recipe_font_owners").first()).n, 0);
});
