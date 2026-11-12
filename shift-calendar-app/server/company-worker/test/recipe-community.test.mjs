// 1.35 [RCX-01]..[RCX-15] 나눔 판 완성: 계보, 새 판 확인, 요청 판, 제작자·배지·팔로우, 고른 것, 소식, 그림 표지. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, seasoned } from "./helpers.mjs";
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
  // 1.36 [VIL-15] The one who asked hears of it in 소식, with the recipe that answers it; nobody else does.
  const news = (n) => call("GET", "/api/recipes/news", undefined, dev(n));
  assert.deepEqual((await news(1)).data.requests.map((x) => [x.code, x.title]), [[mine, "간호사 알람 세트"]]);
  assert.deepEqual((await news(2)).data.requests, []);
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
  // A lossy WebP's header: VP8 frame tag, start code 9d 01 2a, then 512 × 512.
  const webp = Buffer.concat([Buffer.from("RIFF"), Buffer.from([30, 0, 0, 0]), Buffer.from("WEBPVP8 "), Buffer.from([10, 0, 0, 0]),
    Buffer.from([0, 0, 0, 0x9d, 0x01, 0x2a, 0x00, 0x02, 0x00, 0x02]), Buffer.alloc(8)]).toString("base64");
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
  // 1.36 PC: the owner opening their own page before putting anything in it gets an empty page of theirs.
  const own = (await call("GET", `/api/recipes/makers/${none}`, undefined, dev(2))).data;
  assert.equal(own.mine, true);
  assert.equal(own.recipes.length, 0);
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
  assert.equal(inbox.at, new Date(AFTER).toISOString(), "the server's time, the phone's next since");
  assert.ok(Date.parse(inbox.followers.latest) <= AFTER);
  // 다 봤어요 then asking from the server's [at]: nothing new comes back.
  const again = (await call("GET", `/api/recipes/makers/me/inbox?since=${inbox.at}`, undefined, dev(1))).data;
  assert.deepEqual([again.followers.count, again.questions.length, again.remixes.length], [0, 0, 0]);
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
  await seasoned(call, t, [3, 4, 5]);
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
  // 1.39 [SPREAD-02] The store link says where the install came from, never the maker.
  assert.match(page.text, /referrer=utm_source%3Dbamnat_share%26utm_medium%3Dmaker%26utm_campaign%3Drecipe/);
  assert.doesNotMatch(page.text.match(/play\.google\.com[^"]*/)[0], new RegExp(m));
  assert.equal(page.text.includes(device(1)), false);
  await call("POST", "/api/recipes/makers/me", { device: device(1), hidden: true }, from(1));
  assert.equal((await call("GET", `/m/ffffffffffff`)).status, 404);
});

test("[PRF-05][PRF-06] 리뷰: 모두 지우기로 남의 차단·운영자 닫기를 벗어나지 못하고, 새 소개는 신고를 지우고, 차단한 분의 질문은 알림에 없어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  await seasoned(call, t, [6, 7, 8]);
  const admin = await signIn(call);
  const m1 = (await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "처음 소개" }, from(1))).data.maker;
  const m2 = (await call("POST", "/api/recipes/makers/me", { device: device(2), face: "🐼" }, from(2))).data.maker;
  await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2) }, from(2));
  await call("POST", `/api/recipes/makers/${m2}/follow`, { device: device(1) }, from(1));
  // 지운 것을 숫자로 (PC 확인: '지울 것이 없었어요'라고 했음).
  const gone = (await call("DELETE", "/api/mine", undefined, dev(1))).data;
  assert.equal(gone.profile, 1);
  // PC 5차: the same phone opening 마을 again makes an empty row with the same id; still '프로필이 없어졌어요', and 목록에서 빼기 works.
  await call("POST", "/api/recipes/makers/me", { device: device(1) }, from(1));
  await call("GET", "/api/recipes/makers/me/following", undefined, dev(1));
  assert.deepEqual((await call("GET", "/api/recipes/makers/me/following", undefined, dev(2))).data.blocked.map((x) => [x.maker, x.gone]), [[m1, true]]);
  await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2), on: false }, from(2));
  assert.equal((await call("GET", "/api/recipes/makers/me/following", undefined, dev(2))).data.blocked.length, 0);
  await call("POST", "/api/recipes/makers/me", { device: device(1), face: "🦊" }, from(1));
  await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2) }, from(2));
  assert.deepEqual((await call("GET", "/api/recipes/makers/me/following", undefined, dev(2))).data.blocked.map((x) => x.gone), [undefined], "a face again: there");
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
  const own = (await call("GET", `/api/recipes/makers/${m5}`, undefined, dev(5))).data;
  assert.deepEqual([own.bioHidden, own.bioHiddenBy, own.bioHiddenWhy], [true, "flags", "bio"]);
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

test("[PRF-07][LIM-10] 모음은 하루 제한이 없고, 운영자가 소개를 가리고 프로필을 닫으면(두 번) 그분 모음 쓰기만 하루 5번으로 줄어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const a = (await up(call, 1, { title: "야간 달력 칸" }, "public")).data.code;
  const b = (await up(call, 1, { title: "야간 달력 색" }, "public")).data.code;
  const mk = (n) => call("POST", "/api/recipes/sets", { device: device(1), title: `야간 세트 ${n}번`, summary: "같이 입으면 좋아요", codes: [a, b] }, from(1));
  for (let i = 0; i < 12; i++) assert.equal((await mk(i)).status, 201, `no daily cap (${i})`);
  const me = (await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "야간 근무 꾸미기" }, from(1))).data.maker;
  await call("POST", `/api/admin/recipes/makers/${me}/bio-hide`, {}, admin);
  await call("POST", `/api/admin/recipes/makers/${me}/bio-hide`, {}, admin); // 이미 가린 것은 다시 세지 않아요
  await call("POST", `/api/admin/recipes/makers/${me}/close`, {}, admin);
  t.mock.timers.tick(25 * 3600 * 1000);
  const ids = (await call("GET", `/api/recipes/makers/${me}`, undefined, dev(1))).data.sets.map((s) => s.id);
  for (const id of ids.slice(0, 8)) await call("DELETE", `/api/recipes/sets/${id}`, undefined, dev(1));
  for (let i = 0; i < 5; i++) assert.equal((await mk(20 + i)).status, 201);
  const refused = await mk(30);
  assert.equal(refused.status, 429);
  assert.equal(refused.data.why, "device");
});

test("[V20] 보이는 믿음 단계: 새싹 → 3일 뒤 이웃 → 가려진 것 없이 14일이면 단골; 다음 단계와 쓰기 제한은 본인만", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  await up(call, 1, { title: "야간 달력 칸" }, "public");
  const me = (await call("POST", "/api/recipes/makers/me", { device: device(1), face: "🦉" }, from(1))).data.maker;
  const page = async (n) => (await call("GET", `/api/recipes/makers/${me}`, undefined, dev(n))).data;
  let p = await page(1);
  assert.deepEqual([p.trust, p.trustNext, p.limit], [0, { days: 3 }, undefined]);
  p = await page(2);
  assert.deepEqual([p.trust, p.trustNext], [0, undefined]);
  t.mock.timers.tick(3 * 86400000 + 1000);
  assert.deepEqual([(await page(1)).trust, (await page(1)).trustNext], [1, { days: 11 }]);
  t.mock.timers.tick(11 * 86400000);
  assert.deepEqual([(await page(1)).trust, (await page(1)).trustNext, (await page(3)).trust], [2, undefined, 2]);
  // 운영자가 소개를 가리고 프로필을 닫으면(두 번) 쓰기 제한이 생기고, 본인 프로필에만 보여요.
  await call("POST", "/api/recipes/makers/me", { device: device(1), bio: "야간 근무 꾸미기" }, from(1));
  const admin = await signIn(call);
  await call("POST", `/api/admin/recipes/makers/${me}/bio-hide`, {}, admin);
  await call("POST", `/api/admin/recipes/makers/${me}/close`, {}, admin);
  p = await page(1);
  assert.equal(p.trust, 1, "a hidden 소개 takes 단골 away");
  assert.deepEqual(p.trustNext, { held: true });
  assert.equal(p.limit.perDay, 5);
  assert.match(p.limit.until, /^\d{4}-/);
  assert.deepEqual([p.limit.by, p.limit.n, p.limit.kinds], ["operator", 2, ["bio", "profile"]]);
});

test("[VIL-02] 차단 하나로: 장터·프로필에서 차단한 분의 글은 유형 광장에서도 빠져요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const as = (n) => ({ ...from(n), "x-device": device(n) });
  const m1 = (await call("POST", "/api/recipes/makers/me", { device: device(1), face: "🐼" }, from(1))).data.maker;
  assert.equal((await call("POST", "/api/posts", { device: device(1), company: "style-shift", setup: "room", body: "야간 끝나고 잠 어떻게 자요?" }, from(1))).status, 201);
  assert.equal((await call("POST", "/api/posts", { device: device(3), company: "style-shift", setup: "room", body: "저는 암막 커튼 써요" }, from(3))).status, 201);
  const path = "/api/companies/style-shift/setups/room/posts";
  assert.equal((await call("GET", path, undefined, as(2))).data.posts.length, 2);
  await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2) }, from(2));
  const seen = (await call("GET", path, undefined, as(2))).data;
  assert.deepEqual([seen.posts.length, seen.hiddenByYou], [1, 1]);
  // 다른 분에게는 그대로, 풀면 다시 보여요.
  assert.equal((await call("GET", path, undefined, as(4))).data.posts.length, 2);
  await call("POST", `/api/recipes/makers/${m1}/block`, { device: device(2), on: false }, from(2));
  assert.equal((await call("GET", path, undefined, as(2))).data.posts.length, 2);
});

test("[VIL-08][VIL-09] 광장 글 꼬리표(이야기·질문·팁·자랑)와 질문의 '도움 됐어요'", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const as = (n) => ({ ...from(n), "x-device": device(n) });
  const write = (n, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
  assert.equal((await write(1, "야간 끝나고 커피 끊는 법 나눠요", { kind: "tip" })).status, 201);
  assert.equal((await write(1, "제 달력 꾸민 것 보세요", { kind: "show" })).status, 201);
  assert.equal((await write(1, "아무 꼬리표", { kind: "proposal" })).status, 400);
  const q = (await write(1, "3조2교대 잠 어떻게 나눠 자요?", { kind: "question" })).data.id;
  const a = (await write(2, "저는 4시간씩 두 번 자요", { parent: q, kind: "tip" })).data.id;
  const mine = (await write(1, "고마워요", { parent: q })).data.id;
  const path = "/api/companies/style-shift/setups/room/posts";
  const kinds = (await call("GET", path, undefined, as(3))).data.posts.map((p) => p.kind);
  assert.deepEqual(kinds, ["tip", "show", "question", "talk", "talk"], "a reply is a plain one");
  const pick = (n, reply) => call("POST", `/api/posts/${q}/answer`, { device: device(n), reply }, from(n));
  assert.equal((await pick(2, a)).status, 403, "only the question's writer");
  assert.equal((await pick(1, mine)).data.error, "own");
  assert.equal((await pick(1, a)).data.answer, a);
  const shown = (await call("GET", path, undefined, as(3))).data.posts.find((p) => p.id === q);
  assert.equal(shown.answer, a);
  assert.equal((await pick(1, null)).data.answer, null);
  assert.equal((await call("GET", path, undefined, as(3))).data.posts.find((p) => p.id === q).answer, undefined);
  // PC 확인 V1: the question's writer hears of the answer in 광장 소식 (their own 고마워요 does not count).
  const since = new Date(Date.now() - 60000).toISOString();
  const news = (n) => call("GET", `/api/plaza/news?since=${since}`, undefined, as(n)).then((r) => r.data.items.filter((i) => i.kind === "replies"));
  assert.deepEqual((await news(1)).map((i) => [i.company, i.count]), [[{ id: "style-shift", name: "교대 광장" }, 1]]);
  assert.deepEqual(await news(2), [], "answering is not news to the one who answered");
});

test("[VIL-10] 투표 글: 질문에 보기 2~4개, 한 사람 한 표, 바꾸고 거둘 수 있어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const as = (n) => ({ ...from(n), "x-device": device(n) });
  const write = (n, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
  assert.equal((await write(1, "어느 쪽이 덜 피곤해요?", { kind: "question", poll: ["주주야야"] })).status, 400, "two at least");
  assert.equal((await write(1, "어느 쪽이 덜 피곤해요?", { kind: "question", poll: ["a", "a"] })).status, 400, "different");
  assert.equal((await write(1, "어느 쪽이 덜 피곤해요?", { kind: "question", poll: ["a", "b", "c", "d", "e"] })).status, 400);
  const id = (await write(1, "어느 쪽이 덜 피곤해요?", { kind: "question", poll: [" 주주야야비비 ", "주야비"] })).data.id;
  const talk = (await write(1, "그냥 글", { poll: ["a", "b"] })).data.id;
  const vote = (n, choice, post = id) => call("POST", `/api/posts/${post}/poll`, { device: device(n), choice }, from(n));
  assert.equal((await vote(2, 0, talk)).status, 404, "only a question carries a poll");
  assert.equal((await vote(2, 2)).status, 400);
  assert.deepEqual((await vote(2, 0)).data.poll, { options: ["주주야야비비", "주야비"], counts: [1, 0], mine: 0 });
  await vote(3, 1);
  assert.deepEqual((await vote(2, 1)).data.poll.counts, [0, 2], "a changed vote moves");
  const path = "/api/companies/style-shift/setups/room/posts";
  const seen = (await call("GET", path, undefined, as(3))).data.posts.find((p) => p.id === id).poll;
  assert.deepEqual(seen, { options: ["주주야야비비", "주야비"], counts: [0, 2], mine: 1 });
  assert.deepEqual((await vote(3, null)).data.poll, { options: ["주주야야비비", "주야비"], counts: [0, 1] });
  assert.equal((await call("GET", path, undefined, as(4))).data.posts.find((p) => p.id === id).poll.mine, undefined);
  // PC 확인 5c21f3bb: the 관리자 앱's list of posts shows what the poll asked, so a 신고 on it can be judged.
  const admin = await signIn(call);
  const listed = (await call("GET", "/api/admin/posts?status=all", undefined, admin)).data.posts.find((p) => p.id === id);
  assert.deepEqual(listed.poll, ["주주야야비비", "주야비"]);
});

test("[VIL-13] 내 글: 광장에 쓴 글과 답글을 내 것에서 모아 봐요(쓴 사람만)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const write = (n, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
  const q = (await write(1, "야간 끝나고 뭐 드세요?", { kind: "question" })).data.id;
  t.mock.timers.setTime(AFTER + 60_000);
  await write(2, "저는 죽이요", { parent: q });
  const reply = (await write(1, "좋네요", { parent: q })).data.id;
  const mine = (n) => call("GET", "/api/posts/mine", undefined, { ...from(n), "x-device": device(n) });
  assert.equal((await call("GET", "/api/posts/mine", undefined, from(1))).status, 400);
  const items = (await mine(1)).data.items;
  assert.deepEqual(items.map((p) => p.id), [reply, q]);
  assert.equal(items[1].name, "교대 광장");
  assert.equal(items[1].replies, 2);
  assert.equal(items[0].parent, q);
  assert.deepEqual((await mine(3)).data.items, []);
});

test("[VIL-06] 마을 검색: 유형 광장 글과 요청을 한 번에, 회사 방 글은 빼요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const write = (n, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
  await write(1, "야간 끝나고 잠드는 법 알려 주세요", { kind: "question" });
  t.mock.timers.setTime(AFTER + 60_000);
  const top = (await write(2, "야간 전에 낮잠 두 시간", { kind: "tip" })).data.id;
  await write(3, "답글에도 야간이 있어요", { parent: top });
  await call("POST", "/api/recipes/requests", { device: device(4), style: "SHIFT", body: "야간 근무용 어두운 테마 있었으면" }, from(4));
  const find = (q) => call("GET", `/api/village/search?q=${encodeURIComponent(q)}`, undefined, from(5));
  const r = (await find("야간")).data;
  assert.equal(r.posts.length, 2, "no replies");
  assert.equal(r.posts[0].name, "교대 광장");
  assert.equal(r.posts[0].replies, 1);
  assert.ok(!("device" in r.posts[0]) && !("author" in r.posts[0]));
  assert.equal(r.requests.length, 1);
  assert.deepEqual((await find("야")).data, { posts: [], requests: [] }, "two letters at least");
});

test("[VIL-16] 자랑 글에 내 공개 작품 하나를 카드로 붙여요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const write = (n, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
  const mine = (await up(call, 1, { title: "야간 다크" }, "public")).data.code;
  const other = (await up(call, 2, { title: "남의 것" }, "public")).data.code;
  const link = (await up(call, 1, { title: "링크만" }, "link")).data.code;
  assert.equal((await write(1, "제 달력 보세요", { kind: "show", attach: other })).data.error, "attach", "only one's own");
  assert.equal((await write(1, "제 달력 보세요", { kind: "show", attach: link })).data.error, "attach", "only a public one");
  const id = (await write(1, "제 달력 보세요", { kind: "show", attach: mine })).data.id;
  const talk = (await write(1, "이야기에는 안 붙어요", { attach: mine })).data.id;
  const posts = (await call("GET", "/api/companies/style-shift/setups/room/posts", undefined, { ...from(3), "x-device": device(3) })).data.posts;
  const card = posts.find((p) => p.id === id).attach;
  assert.deepEqual([card.code, card.title], [mine, "야간 다크"]);
  // PC 확인 V27: with its look for a small picture.
  assert.equal(typeof card.look, "object");
  assert.equal(posts.find((p) => p.id === talk).attach, undefined);
});

test("[VIL-19] 이달의 주제 공모: 운영자가 주제를 정하면 내 공개 작품을 넣고, 다음 달에 고른 셋이 보여요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const see = (n, style = "SHIFT") => call("GET", `/api/village/contest?style=${style}`, undefined, { ...from(n), "x-device": device(n) });
  const enter = (n, code, extra = {}) => call("POST", "/api/village/contest/enter", { device: device(n), style: "SHIFT", code, ...extra }, from(n));
  const mine = (await up(call, 1, { title: "야간 다크" }, "public")).data.code;
  assert.equal((await see(1)).data.theme, null);
  assert.equal((await enter(1, mine)).status, 404, "no theme, nothing to enter");
  assert.equal((await call("POST", "/api/admin/village/contests", { style: "SHIFT", theme: "야간 근무용 어두운 테마" }, admin)).status, 200);
  const other = (await up(call, 2, { title: "남의 것" }, "public")).data.code;
  assert.equal((await enter(1, other)).data.error, "code", "only my own");
  assert.equal((await enter(1, mine)).status, 201);
  const seen = (await see(1)).data;
  assert.deepEqual([seen.theme, seen.entries, seen.mine], ["야간 근무용 어두운 테마", 1, [mine]]);
  assert.deepEqual(seen.mineTitles.map((m) => m.code), [mine], "PC 확인 V26: by name");
  assert.equal((await call("POST", "/api/admin/village/contests", { style: "SHIFT", theme: "" }, admin)).status, 409, "not taken off once entered");
  const list = (await call("GET", "/api/admin/village/contests", undefined, admin)).data;
  const here = list.items.find((x) => x.month === list.month && x.style === "SHIFT");
  assert.deepEqual(here.entries.map((e) => e.code), [mine]);
  assert.equal((await call("POST", "/api/admin/village/contests/pick", { month: list.month, style: "SHIFT", codes: [other] }, admin)).status, 400);
  const picked = await call("POST", "/api/admin/village/contests/pick", { month: list.month, style: "SHIFT", codes: [mine] }, admin);
  assert.equal(picked.status, 200);
  // 1.36 [GRW-09] 고르면 주인 기기의 프로 내역에 만든 작품 보상이 생기고, 다시 저장해도 두 번 가지 않아요.
  assert.equal(picked.data.rewarded, 1);
  assert.equal((await call("POST", "/api/admin/village/contests/pick", { month: list.month, style: "SHIFT", codes: [mine] }, admin)).data.rewarded, 0);
  // 2.0.0 [RVD-13] 확인 뒤 지급: the phone says it waits, then has it once an admin pays.
  assert.ok((await call("GET", "/api/passes", undefined, { "x-device": device(1) })).data.pending.some((x) => x.why === "maker" && x.what.startsWith("이달의 주제에 뽑힘 · ")));
  for (const w of (await call("GET", "/api/admin/rewards?status=owed&kind=maker", undefined, admin)).data.rewards) await call("POST", `/api/admin/rewards/${w.id}`, { status: "paid" }, admin);
  assert.ok((await call("GET", "/api/passes", undefined, { "x-device": device(1) })).data.items.some((x) => x.why === "maker" && x.what.startsWith("이달의 주제에 뽑힘 · ")));
  t.mock.timers.setTime(AFTER + 3 * 86_400_000); // December (KST): November's picks show
  const next = (await see(3)).data;
  assert.deepEqual([next.theme, next.last.theme, next.last.picks.map(({ look, ...p }) => p)], [null, "야간 근무용 어두운 테마", [{ code: mine, title: "야간 다크" }]]);
  // PC 확인 f39bcfbe: a pick brings its look, for the card's small picture.
  assert.deepEqual(next.last.picks[0].look.days.map((d) => d.l), ["주", "야"]);
});

test("[VIL-11] 이번 주 이야기 주제: 운영자가 유형 광장마다 한 줄, 다음 주엔 저절로 사라져요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const as = (n) => ({ ...from(n), "x-device": device(n) });
  const path = "/api/companies/style-shift/setups/room/posts";
  assert.equal((await call("GET", path, undefined, as(1))).data.topic, undefined);
  assert.equal((await call("POST", "/api/admin/village/topics", { room: "style-nope", text: "x" }, admin)).status, 400);
  assert.equal((await call("POST", "/api/admin/village/topics", { room: "style-shift", text: "야간 끝나고 잠드는 나만의 방법" }, admin)).status, 200);
  assert.equal((await call("GET", path, undefined, as(1))).data.topic, "야간 끝나고 잠드는 나만의 방법");
  assert.equal((await call("GET", "/api/companies/style-day/setups/room/posts", undefined, as(1))).data.topic, undefined);
  const list = (await call("GET", "/api/admin/village/topics", undefined, admin)).data.items;
  assert.equal(list.find((x) => x.room === "style-shift").text, "야간 끝나고 잠드는 나만의 방법");
  t.mock.timers.setTime(AFTER + 8 * 86_400_000);
  assert.equal((await call("GET", path, undefined, as(1))).data.topic, undefined);
});

test("[VIL-18] 새 창작자 자리: 열 개마다 첫 작품 하나가 끝에 올라와요", async () => {
  const { withNewMakers } = await import("../src/recipes.js");
  const list = Array.from({ length: 25 }, (_, i) => ({ id: i, fresh: i === 17 || i === 22 }));
  const out = withNewMakers(list, (r) => r.fresh);
  assert.deepEqual(out.slice(0, 10).map((r) => r.id), [0, 1, 2, 3, 4, 5, 6, 7, 8, 17]);
  assert.deepEqual(out.slice(10, 20).map((r) => r.id), [9, 10, 11, 12, 13, 14, 15, 16, 18, 22]);
  assert.equal(out.length, 25);
  assert.equal(out[9]._newMaker, true);
  // Already one in the ten: nothing moves.
  const two = withNewMakers(Array.from({ length: 10 }, (_, i) => ({ id: i, fresh: i === 3 })), (r) => r.fresh);
  assert.deepEqual(two.map((r) => r.id), [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
});
