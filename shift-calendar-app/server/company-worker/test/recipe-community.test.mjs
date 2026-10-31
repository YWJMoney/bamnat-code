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

test("[PRF-01][PRF-02][PRF-04] 창작자 프로필: 소개, 대표 작품, 테마·기능별 탭 수, 팔로잉 수, 팔로잉 줄, 이분 알림만 끄기", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = (await up(call, 1, {}, "public")).data.code;
  const me = (await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "  3교대 8년차, 야간이 잘 보이는 것만 만들어요 ", pins: [a, "ZZZZZZZZ"], styleTag: "SHIFT" }, from(1))).data;
  assert.equal(me.bio, "3교대 8년차, 야간이 잘 보이는 것만 만들어요");
  assert.deepEqual(me.pins, [a], "only one's own public recipes");
  assert.equal((await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "가".repeat(41) }, from(1))).data.error, "too_long");
  assert.equal((await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "수익 보장 리딩방 초대" }, from(1))).data.field, "bio");
  // Only what is sent changes: the face leaves the 소개 as it was.
  assert.equal((await call("POST", "/api/recipes/makers/me", { device: device(1), face: "🦉" }, from(1))).data.bio, me.bio);
  await call("POST", `/api/recipes/makers/${me.maker}/follow`, { device: device(2) }, from(2));
  // Asking with nothing makes no profile; one put in (here a face) does.
  const none = (await call("POST", "/api/recipes/makers/me", { device: device(2) }, from(2))).data.maker;
  assert.equal((await call("GET", `/api/recipes/makers/${none}`, undefined, dev(1))).status, 404);
  const two = (await call("POST", "/api/recipes/makers/me", { device: device(2), face: "🐻" }, from(2))).data.maker;
  assert.equal(two, none);
  await call("POST", `/api/recipes/makers/${two}/follow`, { device: device(1) }, from(1));
  const page = (await call("GET", `/api/recipes/makers/${me.maker}`, undefined, dev(2))).data;
  assert.deepEqual([page.bio, page.styleTag, page.pins, page.followers, page.followingCount, page.following, page.quiet],
    [me.bio, "SHIFT", [a], 1, 1, true, false]);
  assert.equal(page.themes + page.tabs <= page.recipes.length, true);
  assert.equal(page.hidden, undefined, "숨기기 is the maker's own to see");
  // 팔로잉 줄: the maker with a new public recipe in 7 days is fresh.
  const following = (await call("GET", "/api/recipes/makers/me/following", undefined, dev(2))).data;
  assert.deepEqual(following.items.map((x) => [x.maker, x.face, x.fresh, x.quiet]), [[me.maker, "🦉", true, false]]);
  // 이분 알림만 끄기: still following, not in 소식.
  assert.equal((await call("POST", `/api/recipes/makers/${me.maker}/follow`, { device: device(2), quiet: true }, from(2))).data.quiet, true);
  const since = new Date(AFTER - 1000).toISOString();
  assert.deepEqual((await call("GET", `/api/recipes/news?since=${since}`, undefined, dev(2))).data.recipes, []);
  assert.equal((await call("GET", `/api/recipes/makers/${me.maker}`, undefined, dev(2))).data.following, true);
  // 프로필 숨기기: gone for others, there for the maker.
  await call("POST", "/api/recipes/makers/me", { device: device(1), hidden: true }, from(1));
  assert.equal((await call("GET", `/api/recipes/makers/${me.maker}`, undefined, dev(3))).status, 404);
  assert.equal((await call("GET", `/api/recipes/makers/${me.maker}`, undefined, dev(1))).data.hidden, true);
  assert.equal((await call("POST", `/api/recipes/makers/${me.maker}/follow`, { device: device(3) }, from(3))).status, 404);
});

test("[PRF-03] 내 프로필 최근 알림: 새 팔로워, 질문, 고친 판, 쓰는 분·좋아요 고비, 배지; 팔로워 명단은 본인만", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = (await up(call, 1, { title: "내 교대 세트" }, "public")).data.code;
  const me = (await call("POST", "/api/recipes/makers/me", { device: device(1) }, from(1))).data.maker;
  await call("POST", "/api/recipes/makers/me", { device: device(2), face: "🐻" }, from(2));
  await call("POST", `/api/recipes/makers/${me}/follow`, { device: device(2) }, from(2));
  await call("POST", `/api/recipes/makers/${me}/follow`, { device: device(3) }, from(3));
  await call("POST", `/api/recipes/${a}/talk`, { device: device(3), body: "알람도 같이 넣을 수 있나요?", kind: "question" }, from(3));
  await up(call, 4, { title: "주말 색만 바꾼 것", parent: a }, "public");
  const since = new Date(AFTER - 1000).toISOString();
  const inbox = (await call("GET", `/api/recipes/makers/me/inbox?since=${since}`, undefined, dev(1))).data;
  assert.equal(inbox.followers.count, 2);
  assert.deepEqual(inbox.followers.items.map((x) => x.face), ["🐻"], "a face only for one with a profile");
  assert.deepEqual(inbox.questions.map((x) => x.code), [a]);
  assert.deepEqual(inbox.remixes.map((x) => x.parentTitle), ["내 교대 세트"]);
  assert.deepEqual(inbox.badges.map((b) => b.key), ["first"]);
  assert.deepEqual(inbox.milestones, []);
  assert.equal(JSON.stringify(inbox).includes(device(2)) || JSON.stringify(inbox).includes(device(3)), false, "never the install");
  const fl = (await call("GET", "/api/recipes/makers/me/followers", undefined, dev(1))).data;
  assert.deepEqual([fl.total, fl.items.length, fl.unnamed], [2, 1, 1]);
});

test("[PRF-05][PRF-06] 차단과 프로필 신고: 서로 팔로우가 끊기고, 차단당한 쪽엔 없는 프로필; 신고 3번이면 소개가 먼저 가려져요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const m1 = (await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "공식 밤낮 팀이에요" }, from(1))).data.maker;
  const m2 = (await call("POST", "/api/recipes/makers/me", { device: device(2), face: "🐼" }, from(2))).data.maker;
  await call("POST", `/api/recipes/makers/${m1}/follow`, { device: device(2) }, from(2));
  await call("POST", `/api/recipes/makers/${m2}/follow`, { device: device(1) }, from(1));
  assert.equal((await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2) }, from(2))).data.blocked, true);
  // Both follows went; the blocked maker cannot see or follow the one who blocked.
  assert.equal((await call("GET", `/api/recipes/makers/${m1}`, undefined, dev(2))).data.blocked, true);
  assert.equal((await call("GET", `/api/recipes/makers/${m2}`, undefined, dev(1))).status, 404);
  assert.equal((await call("POST", `/api/recipes/makers/${m2}/follow`, { device: device(1) }, from(1))).status, 404);
  // 차단한 쪽은 먼저 풀어야 팔로우해요.
  assert.equal((await call("POST", `/api/recipes/makers/${m1}/follow`, { device: device(2) }, from(2))).data.error, "blocked");
  const f2 = (await call("GET", "/api/recipes/makers/me/following", undefined, dev(2))).data;
  assert.deepEqual([f2.items.length, f2.blocked.map((x) => x.maker)], [0, [m1]]);
  assert.equal((await call("GET", "/api/recipes/makers/me/followers", undefined, dev(2))).data.total, 0);
  // 차단한 분의 이야기는 빠져요 (다른 분에게는 그대로).
  const x = (await up(call, 3, { title: "이야기 받을 레시피" }, "public")).data.code;
  await call("POST", `/api/recipes/${x}/talk`, { device: device(1), body: "이 색 조합 좋네요", kind: "opinion" }, from(1));
  assert.equal((await call("GET", `/api/recipes/${x}/talk`, undefined, dev(2))).data.posts.length, 0);
  assert.equal((await call("GET", `/api/recipes/${x}/talk`, undefined, dev(3))).data.posts.length, 1);
  // 차단 풀기.
  await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2), on: false }, from(2));
  assert.equal((await call("GET", `/api/recipes/makers/${m2}`, undefined, dev(1))).status, 200);
  // 신고 세 번이면 소개가 가려지고, 운영자가 보고 되돌려요.
  for (const n of [3, 4, 5]) await call("POST", `/api/recipes/makers/${m1}/flag`, { device: device(n), reason: "impersonate" }, from(n));
  assert.equal((await call("GET", `/api/recipes/makers/${m1}`, undefined, dev(3))).data.bio, undefined);
  assert.equal((await call("GET", `/api/recipes/makers/${m1}`, undefined, dev(1))).data.bioHidden, true);
  const flagged = (await call("GET", "/api/admin/recipes/makers/flagged", undefined, admin)).data.items;
  assert.deepEqual(flagged.map((x) => [x.maker, x.flags, x.reasons.impersonate, x.bioHidden]), [[m1, 3, 3, true]]);
  await call("POST", `/api/admin/recipes/makers/${m1}/close`, {}, admin);
  assert.equal((await call("GET", `/api/recipes/makers/${m1}`, undefined, dev(3))).status, 404);
  await call("POST", `/api/admin/recipes/makers/${m1}/open`, {}, admin);
  await call("POST", `/api/admin/recipes/makers/${m1}/bio-show`, {}, admin);
  assert.equal((await call("GET", `/api/recipes/makers/${m1}`, undefined, dev(3))).data.bio, "공식 밤낮 팀이에요");
  // 모두 지우기 takes blocks and flags too.
  await call("DELETE", "/api/mine", undefined, dev(2));
  await call("DELETE", "/api/mine", undefined, dev(1));
  assert.deepEqual((await call("GET", "/api/admin/recipes/makers/flagged", undefined, admin)).data.items, []);
});

test("[PRF-07] 모음: 내 공개 작품 2~6개, 테마는 하나, 다른 사람 것은 안 돼요; 하나가 내려가도 남은 것으로", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = (await up(call, 1, { title: "야간 달력 칸" }, "public")).data.code;
  const b = (await up(call, 1, { title: "야간 달력 색" }, "public")).data.code;
  t.mock.timers.tick(25 * 3600 * 1000); // 하루 두 개까지라 다음 날
  const c = (await up(call, 1, { title: "세 번째 칸" }, "public")).data.code;
  const other = (await up(call, 2, { title: "남의 것" }, "public")).data.code;
  const mk = (codes, extra = {}) => call("POST", "/api/recipes/sets", { device: device(1), title: "야간 3교대 세트", summary: "같이 입으면 좋아요", codes, ...extra }, from(1));
  assert.equal((await mk([a])).data.error, "codes");
  assert.equal((await mk([a, other])).data.error, "not_yours");
  assert.equal((await mk([a, b], { title: "리딩방 초대 수익 보장" })).data.field, "title");
  const set = (await mk([a, b, c])).data;
  assert.match(set.id, /^s[0-9a-f]{12}$/);
  const got = (await call("GET", `/api/recipes/sets/${set.id}`, undefined, dev(3))).data;
  assert.deepEqual([got.title, got.items.length, got.mine], ["야간 3교대 세트", 3, false]);
  const maker = got.maker;
  assert.deepEqual((await call("GET", `/api/recipes/makers/${maker}`, undefined, dev(3))).data.sets.map((x) => x.codes.length), [3]);
  // Only the maker changes or deletes it.
  assert.equal((await call("POST", `/api/recipes/sets/${set.id}`, { device: device(2), title: "남이 바꿈" }, from(2))).status, 403);
  await call("DELETE", `/api/recipes/${c}`, undefined, dev(1));
  assert.equal((await call("GET", `/api/recipes/sets/${set.id}`, undefined, dev(3))).data.items.length, 2);
  assert.equal((await call("GET", `/api/recipes/sets/${set.id}`, undefined, dev(1))).data.missing, 1);
  await call("DELETE", `/api/recipes/${b}`, undefined, dev(1));
  assert.equal((await call("GET", `/api/recipes/sets/${set.id}`, undefined, dev(3))).status, 404, "fewer than two left");
  t.mock.timers.tick(25 * 3600 * 1000);
  assert.equal((await call("POST", `/api/recipes/sets/${set.id}`, { device: device(1), codes: [a, (await up(call, 1, { title: "새 칸" }, "public")).data.code] }, from(1))).status, 200);
  assert.equal((await call("DELETE", `/api/recipes/sets/${set.id}`, undefined, dev(1))).data.deleted, true);
});

test("[PRF-08][PRF-09][PRF-10] 팔로잉 피드, 작품 통계, 이달의 제작자", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const a = (await up(call, 1, {}, "public")).data.code;
  await up(call, 2, { title: "팔로우 안 한 분 것" }, "public");
  const m1 = (await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "교대 꾸미기" }, from(1))).data.maker;
  const m2 = (await call("POST", "/api/recipes/makers/me", { device: device(2) }, from(2))).data.maker;
  await call("POST", `/api/recipes/makers/${m1}/follow`, { device: device(3) }, from(3));
  const feed = (await call("GET", "/api/recipes/feed", undefined, dev(3)));
  assert.deepEqual(feed.data.items.map((x) => x.code), [a]);
  assert.match(feed.headers.get("cache-control"), /no-store/);
  for (const n of [4, 5, 6]) await call("POST", `/api/recipes/${a}/applied`, { device: device(n), kind: "applied" }, from(n));
  const stats = (await call("GET", "/api/recipes/makers/me/stats", undefined, dev(1))).data;
  assert.equal(stats.items[0].code, a);
  assert.equal(stats.items[0].days.length, 28);
  assert.equal(stats.week, stats.items[0].days.slice(-7).reduce((x, y) => x + y, 0));
  assert.equal(stats.week > 0, true);
  const month = (await call("GET", "/api/recipes/makers/month?style=SHIFT")).data;
  assert.deepEqual(month.items.map((x) => [x.maker, x.bio]), [[m1, "교대 꾸미기"]]);
  // The operator's pick comes first.
  await call("POST", "/api/admin/recipes/makers/month", { style: "SHIFT", makers: [m2] }, admin);
  assert.deepEqual((await call("GET", "/api/recipes/makers/month?style=SHIFT")).data.items.map((x) => [x.maker, x.picked]), [[m2, true], [m1, false]]);
});

test("[PRF-11] 프로필 링크의 공개 페이지: 이름·소개·대표 작품, 앱으로 열기; 숨긴 프로필은 없어요; 글은 그대로 찍지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  await up(call, 1, { title: "야간 <b>굵게</b>" }, "public");
  const m = (await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "3교대 & 야간 전문", face: "🦉" }, from(1))).data.maker;
  const page = await call("GET", `/m/${m}`);
  assert.equal(page.status, 200);
  assert.match(page.headers.get("content-type"), /text\/html/);
  assert.match(page.text, /3교대 &amp; 야간 전문/);
  assert.match(page.text, /야간 &lt;b&gt;굵게&lt;\/b&gt;/);
  assert.match(page.text, new RegExp(`bamnat://m/${m}`));
  assert.equal(page.text.includes(device(1)), false);
  await call("POST", "/api/recipes/makers/me", { device: device(1), hidden: true }, from(1));
  assert.equal((await call("GET", `/m/ffffffffffff`)).status, 404);
});

test("[PRF-05][PRF-06] 리뷰: 모두 지우기로 남의 차단·운영자 닫기를 벗어나지 못하고, 새 소개는 신고를 지우고, 차단한 분의 질문은 알림에 없어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const m1 = (await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "처음 소개" }, from(1))).data.maker;
  const m2 = (await call("POST", "/api/recipes/makers/me", { device: device(2), face: "🐼" }, from(2))).data.maker;
  await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2) }, from(2));
  await call("DELETE", "/api/mine", undefined, dev(1));
  await call("POST", "/api/recipes/makers/me", { device: device(1), face: "🦊" }, from(1));
  assert.equal((await call("GET", `/api/recipes/makers/${m2}`, undefined, dev(1))).status, 404, "the block stays after 지우기");
  // 운영자가 닫은 프로필은 지워도 닫힌 채로, 본인에게는 닫혔다고.
  await call("POST", `/api/admin/recipes/makers/${m2}/close`, {}, admin);
  await call("DELETE", "/api/mine", undefined, dev(2));
  assert.equal((await call("GET", `/api/recipes/makers/${m2}`, undefined, dev(3))).status, 404);
  const back = (await call("POST", "/api/recipes/makers/me", { device: device(2), face: "🐼" }, from(2))).data;
  assert.equal(back.closed, true);
  // 새 소개는 소개 신고를 지우고 다시 보여요; 운영자가 가리면 '운영자'로 보여요.
  const b = (await up(call, 5, { title: "다섯" }, "public")).data.code;
  assert.ok(b);
  const m5 = (await call("POST", "/api/recipes/makers/me", { device: device(5), bio: "나쁜 소개" }, from(5))).data.maker;
  for (const n of [6, 7, 8]) await call("POST", `/api/recipes/makers/${m5}/flag`, { device: device(n), reason: "bio" }, from(n));
  assert.equal((await call("GET", `/api/recipes/makers/${m5}`, undefined, dev(3))).data.bio, undefined);
  assert.equal((await call("POST", "/api/recipes/makers/me", { device: device(5), bio: "고친 소개" }, from(5))).data.bioHidden, false);
  assert.equal((await call("GET", `/api/recipes/makers/${m5}`, undefined, dev(3))).data.bio, "고친 소개");
  await call("POST", `/api/admin/recipes/makers/${m5}/bio-hide`, {}, admin);
  await call("POST", `/api/recipes/makers/${m5}/flag`, { device: device(9), reason: "spam" }, from(9));
  const row = (await call("GET", "/api/admin/recipes/makers/flagged", undefined, admin)).data.items.find((x) => x.maker === m5);
  assert.deepEqual([row.flags, row.bioHidden, row.bioHiddenBy], [1, true, "operator"]);
  // 차단한 분의 질문은 내 최근 알림에 없어요.
  const a = (await up(call, 4, { title: "질문 받을 작품" }, "public")).data.code;
  await call("POST", `/api/recipes/${a}/talk`, { device: device(3), body: "칸 크기는 어떻게 바꾸나요?", kind: "question" }, from(3));
  const m3 = (await call("POST", "/api/recipes/makers/me", { device: device(3), face: "🐯" }, from(3))).data.maker;
  const since = new Date(AFTER - 1000).toISOString();
  assert.equal((await call("GET", `/api/recipes/makers/me/inbox?since=${since}`, undefined, dev(4))).data.questions.length, 1);
  await call("POST", `/api/recipes/makers/${m3}/block`, { device: device(4) }, from(4));
  assert.equal((await call("GET", `/api/recipes/makers/me/inbox?since=${since}`, undefined, dev(4))).data.questions.length, 0);
});
