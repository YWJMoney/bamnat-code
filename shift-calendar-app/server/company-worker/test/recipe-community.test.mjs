// 1.35 [RCX-01]..[RCX-15] 나눔 판 완성: 계보, 새 판 확인, 요청 판, 제작자·배지·팔로우, 고른 것, 소식, 그림 표지. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";
import { weekOf } from "../src/recipe-community.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const BEFORE = Date.parse("2026-11-01T03:00:00Z");

const recipe = (extra = {}) => ({
  format: "bamnat-recipe", v: 1, title: "야간 많은 교대 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
  ...extra,
});
const up = (call, n, extra = {}, scope = "link") => call("POST", "/api/recipes", { device: device(n), recipe: recipe(extra), scope }, from(n));
const dev = (n) => ({ "x-device": device(n) });

test("[RCX-01] 계보: the recipe it was made from and those made from it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const a = (await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "원래 교대 세트" }) }, admin)).data.code;
  const b = (await up(call, 2, { title: "고친 교대 세트", parent: a })).data.code;
  const c = (await up(call, 3, { title: "또 고친 교대 세트", parent: b })).data.code;
  const l = (await call("GET", `/api/recipes/${c}/lineage`)).data;
  assert.deepEqual(l.parents.map((p) => p.code), [b, a]);
  assert.equal(l.parents[1].maker, "operator");
  assert.match(l.parents[0].maker, /^[0-9a-f]{12}$/);
  // Link-only ones are not listed as children; the official one is public.
  assert.deepEqual((await call("GET", `/api/recipes/${a}/lineage`)).data.children.map((x) => x.code), []);
});

test("[RCX-02] 새 판 확인 asks with codes only: the newest version of each, nothing about who", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = (await up(call, 1)).data.code;
  await call("POST", `/api/recipes/${a}/versions`, { device: device(1), recipe: recipe({ summary: "색을 더 밝게 바꿨어요" }) }, from(1));
  const v = (await call("GET", `/api/recipes/versions?codes=${a},NOPE,ZZZZ2222`)).data.versions;
  assert.deepEqual(Object.keys(v), [a]);
  assert.equal(v[a].version, 2);
  assert.equal(v[a].hash.length, 64);
});

test("[RCX-04] 요청 판: write, like, 만들게요, 완성 with a recipe; nothing before 3.8; amounts and links refused", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: BEFORE });
  const early = server({ plaza: true });
  assert.equal((await early.call("POST", "/api/recipes/requests", { device: device(1), style: "SHIFT", body: "간호사 3교대 알람 세트 있었으면" }, from(1))).data.error, "not_yet");
  t.mock.timers.setTime(AFTER);
  const { call } = server({ plaza: true });
  const ask = (n, body, style = "SHIFT") => call("POST", "/api/recipes/requests", { device: device(n), style, body }, from(n));
  assert.equal((await ask(1, "시급 10,320원 알바 세트")).data.error, "amount");
  assert.equal((await ask(1, "카톡방 bamnat.kr 오세요")).data.error, "link");
  const q = (await ask(1, "간호사 3교대 알람 세트 있었으면 좋겠어요")).data.id;
  assert.match(q, /^q[A-Za-z0-9]{12}$/);
  const act = (n, what, body = {}) => call("POST", `/api/recipes/requests/${q}/${what}`, { device: device(n), ...body }, from(n));
  assert.equal((await act(1, "like")).data.error, "own");
  assert.equal((await act(2, "like")).data.likes, 1);
  assert.equal((await act(3, "like")).data.likes, 2);
  assert.equal((await act(3, "like", { on: false })).data.likes, 1);
  assert.equal((await act(4, "take")).data.state, "making");
  assert.equal((await act(5, "take")).status, 409, "one maker at a time");
  const mine = (await up(call, 4, { title: "간호사 알람 세트" })).data.code;
  assert.equal((await act(4, "done", { code: "ABCD2345" })).data.error, "code");
  assert.equal((await act(4, "done", { code: mine })).data.state, "done");
  const list = (await call("GET", "/api/recipes/requests?style=SHIFT", undefined, dev(2))).data.items;
  assert.deepEqual([list[0].state, list[0].code, list[0].liked, list[0].mine], ["done", mine, true, false]);
  // The operator can hide one.
  const admin = await signIn(call);
  await call("POST", `/api/admin/recipes/requests/${q}/hide`, {}, admin);
  assert.equal((await call("GET", "/api/recipes/requests?style=SHIFT")).data.items.length, 0);
  const all = (await call("GET", "/api/admin/recipes/requests", undefined, admin)).data.items;
  assert.deepEqual([all.length, all[0].hidden, "device" in all[0]], [1, true, false]);
});

test("[RCX-05][RCX-06][RCX-07][RCX-10] 제작자 page, badges, follow, and the 소식 of new recipes and the maker's answers", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const a = (await up(call, 1, {}, "public")).data.code;
  await call("POST", `/api/admin/recipes/${a}/approve`, {}, admin);
  const me = (await call("POST", "/api/recipes/makers/me", { device: device(1), face: "🦉", color: 4282664004 }, from(1))).data;
  assert.match(me.maker, /^[0-9a-f]{12}$/);
  assert.equal((await call("POST", "/api/recipes/makers/me", { device: device(1), face: "not emoji" }, from(1))).data.error, "emoji");
  assert.equal((await call("POST", `/api/recipes/makers/${me.maker}/follow`, { device: device(1) }, from(1))).data.error, "own");
  assert.equal((await call("POST", `/api/recipes/makers/${me.maker}/follow`, { device: device(2) }, from(2))).data.following, true);
  const page = (await call("GET", `/api/recipes/makers/${me.maker}`, undefined, dev(2))).data;
  assert.deepEqual([page.face, page.followers, page.following, page.recipes.length], ["🦉", 1, true, 1]);
  assert.deepEqual(page.badges.map((b) => b.key), ["first"]);
  assert.equal(JSON.stringify(page).includes(device(1)), false, "never the install");
  // A new recipe of the followed maker, and the maker answering a question, come in the follower's 소식.
  const b = (await up(call, 1, { title: "두 번째 교대 세트" }, "public")).data.code;
  await call("POST", `/api/admin/recipes/${b}/approve`, {}, admin);
  const qid = (await call("POST", `/api/recipes/${a}/talk`, { device: device(2), body: "야간 색 바꾸는 법 알려 주세요", kind: "question" }, from(2))).data.id;
  await call("POST", `/api/recipes/${a}/talk`, { device: device(1), body: "근무 꾸미기에서 바꿔요", kind: "opinion", parent: qid }, from(1));
  const news = (await call("GET", `/api/recipes/news?since=${new Date(AFTER - 1000).toISOString()}`, undefined, dev(2))).data;
  assert.deepEqual(news.recipes.map((r) => r.code).sort(), [a, b].sort());
  assert.equal(news.replies.length, 1);
  assert.equal(news.replies[0].code, a);
  // 모두 지우기 takes the follows and the maker page.
  await call("DELETE", "/api/mine", undefined, dev(1));
  assert.equal((await call("GET", `/api/recipes/makers/${me.maker}`)).status, 404);
});

test("[RCX-08] 이번 주 고른 것: the operator's three for a kind of work; last week's until a new pick", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const codes = [];
  for (const title of ["고른 교대 하나", "고른 교대 둘"]) codes.push((await call("POST", "/api/admin/recipes", { recipe: recipe({ title }) }, admin)).data.code);
  assert.deepEqual((await call("POST", "/api/admin/recipes/picks", { style: "SHIFT", codes: [codes[1], codes[0], codes[1]] }, admin)).data.codes, [codes[1], codes[0]]);
  assert.deepEqual((await call("GET", "/api/recipes/picks?style=SHIFT")).data.items.map((i) => i.code), [codes[1], codes[0]]);
  t.mock.timers.setTime(AFTER + 7 * 86_400_000);
  assert.equal((await call("GET", "/api/recipes/picks?style=SHIFT")).data.items.length, 2, "last week's stay");
  assert.equal(weekOf(Date.parse("2026-11-30T03:00:00Z")), "2026-11-30");
});

test("[RCX-15] 그림 표지: WebP only, small, shown to others once the operator looks", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const webp = Buffer.concat([Buffer.from("RIFF"), Buffer.from([20, 0, 0, 0]), Buffer.from("WEBPVP8 "), Buffer.alloc(16)]).toString("base64");
  const png = Buffer.from("\x89PNG\r\n\x1a\n0000000000000000").toString("base64");
  assert.equal((await call("POST", "/api/recipes/images", { device: device(1), webp: png }, from(1))).data.error, "image");
  const big = Buffer.concat([Buffer.from("RIFF0000WEBP"), Buffer.alloc(170_000)]).toString("base64");
  assert.equal((await call("POST", "/api/recipes/images", { device: device(1), webp: big }, from(1))).status, 413);
  const id = (await call("POST", "/api/recipes/images", { device: device(1), webp }, from(1))).data.id;
  assert.match(id, /^[0-9a-f]{16}$/);
  assert.equal((await call("GET", `/api/recipes/images/${id}`)).status, 404, "not before it is looked at");
  assert.equal((await call("GET", `/api/recipes/images/${id}`, undefined, dev(1))).status, 200, "its maker sees it");
  const admin = await signIn(call);
  assert.equal((await call("GET", "/api/admin/recipes/images", undefined, admin)).data.items.length, 1);
  await call("POST", `/api/admin/recipes/images/${id}/ok`, {}, admin);
  assert.equal((await call("GET", `/api/recipes/images/${id}`)).status, 200);
  // A recipe may point at it.
  assert.equal((await up(call, 1, { cover: { a: 4282339765, b: 4288423856, dir: 1, emoji: "🌙", image: id } })).status, 201);
});

test("[LIN-04] 수정판 소식: someone else's public recipe made from mine comes in my 소식 with my recipe's title", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const a = (await up(call, 1, { title: "내 교대 세트" }, "public")).data.code;
  await call("POST", `/api/admin/recipes/${a}/approve`, {}, admin);
  const b = (await up(call, 2, { title: "야간 더 잘 보이게", parent: a }, "public")).data.code;
  await call("POST", `/api/admin/recipes/${b}/approve`, {}, admin);
  // My own new version of it and a link-only one are not news.
  await up(call, 1, { title: "내가 또 고친 것", parent: a }, "public");
  await up(call, 3, { title: "링크로만", parent: a });
  const since = new Date(AFTER - 1000).toISOString();
  const news = (await call("GET", `/api/recipes/news?since=${since}`, undefined, dev(1))).data;
  assert.deepEqual(news.remixes.map((r) => [r.code, r.parentTitle]), [[b, "내 교대 세트"]]);
  assert.equal(JSON.stringify(news).includes(device(2)), false, "never the install");
  assert.deepEqual((await call("GET", `/api/recipes/news?since=${since}`, undefined, dev(2))).data.remixes, []);
});
