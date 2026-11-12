// 2.0.0 갈래 Q1 [RVQ-26..] 검토 ★ 믿음 줄: 작품 새 판의 검사(SVC-15), 비슷한 이름(SVC-51), 돌려준 작품(SVC-52), 지난 판(SVC-23) 등.
// 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";

/** After 처리방침 3.8 (나눔 판 쓰기) is in force. */
const AFTER = Date.parse("2026-11-30T03:00:00Z");

const recipe = (extra = {}) => ({
  format: "bamnat-recipe", v: 1, title: "야간 많은 교대 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
  ...extra,
});
const up = (call, n, extra = {}, scope = "public") => call("POST", "/api/recipes", { device: device(n), recipe: recipe(extra), scope }, from(n));
const again = (call, n, code, extra = {}, scope) => call("POST", `/api/recipes/${code}/versions`,
  { device: device(n), recipe: recipe(extra), ...(scope ? { scope } : {}) }, from(n));
const open = (call, code, n, q = "") => call("GET", `/api/recipes/${code}${q}`, undefined, { "x-device": device(n) });

test("[RVQ-26] SVC-51 남의 '나만 보기'·'링크로만' 작품과 같은 이름으로도 공개할 수 있고, 그 작품의 코드는 돌려주지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const mine = await up(call, 1, { title: "병동 3교대 달력" }, "private");
  const link = await up(call, 2, { title: "현장 단가 모음" }, "link");
  assert.equal(mine.status, 201);
  assert.equal(link.status, 201);
  // 앞: 409 name_like + 남의 링크 작품 코드.
  const a = await up(call, 3, { title: "병동 3교대 달력" });
  assert.equal(a.status, 201, JSON.stringify(a.data));
  const b = await up(call, 4, { title: "현장  단가 모음" });
  assert.equal(b.status, 201, JSON.stringify(b.data));
  // 공개 작품과 같은 이름은 그대로 막고, 그때만 코드를 알려 줘요.
  const c = await up(call, 5, { title: "병동 3교대  달력" });
  assert.equal(c.status, 409);
  assert.equal(c.data.code, a.data.code);
  // 운영자 작품은 범위와 상관없이(출시 대기 중이어도) 막지만, 아직 공개 전이라 코드는 알려 주지 않아요.
  const admin = await signIn(call);
  const op = await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "밤낮 출시 새 테마" }), hold: true, batch: "2.0.0" }, admin);
  assert.equal(op.data.state, "waiting");
  const d = await up(call, 6, { title: "밤낮 출시 새 테마" });
  assert.equal(d.status, 409);
  assert.equal(d.data.code, undefined);
});

test("[RVQ-26] SVC-51 '나만 보기' 작품은 코드만 알아서는 좋아요·신고·이야기 읽기·쓰기·이야기 좋아요가 안 돼요(만든 분은 돼요)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const w = await up(call, 1, { title: "나만 보는 메모 틀" }, "private");
  const code = w.data.code;
  const post = await call("POST", `/api/recipes/${code}/talk`, { device: device(1), body: "만든 사람 메모예요", kind: "opinion" }, from(1));
  assert.equal(post.status, 201);
  const x = { "x-device": device(2) };
  assert.equal((await open(call, code, 2)).status, 404);
  assert.equal((await call("POST", `/api/recipes/${code}/vote`, { device: device(2), value: 1 }, from(2))).status, 404);
  assert.equal((await call("POST", `/api/recipes/${code}/flag`, { device: device(2), reason: "spam" }, from(2))).status, 404);
  assert.equal((await call("GET", `/api/recipes/${code}/talk`, undefined, x)).status, 404);
  assert.equal((await call("POST", `/api/recipes/${code}/talk`, { device: device(2), body: "들어왔어요", kind: "opinion" }, from(2))).status, 404);
  assert.equal((await call("POST", `/api/recipes/talk/${post.data.id}/vote`, { device: device(2), value: 1 }, from(2))).status, 404);
  // 쓰는 분 수는 세지 않아요(공개로 바꿔도 부풀린 숫자가 따라가지 않게).
  const ap = await call("POST", `/api/recipes/${code}/applied`, { kind: "applied" }, from(2));
  assert.equal(ap.data.counted, false);
  assert.equal(DB.db.prepare("SELECT applied FROM recipes WHERE code = ?").get(code).applied, 0);
  // 만든 분은 그대로.
  assert.equal((await call("GET", `/api/recipes/${code}/talk`, undefined, { "x-device": device(1) })).status, 200);
  assert.equal((await open(call, code, 1)).status, 200);
  // 링크로만 작품은 코드가 있으면 열리고 이야기도 돼요(바뀐 것 없음).
  const l = await up(call, 3, { title: "링크로 나눈 세트" }, "link");
  assert.equal((await call("POST", `/api/recipes/${l.data.code}/vote`, { device: device(4), value: 1 }, from(4))).status, 200);
});

test("[RVQ-26] SVC-15 새 판도 올리기와 같은 검사: 남의 공개 작품 이름으로 바꾸기, 말 못 하기, 쓰기 제한", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const famous = await up(call, 1, { title: "인기 교대 테마" });
  const plain = await up(call, 2, { title: "평범한 이름" });
  assert.equal(plain.status, 201);
  // 사칭: 앞에서는 새 판으로 이름을 바꾸면 그대로 올라갔어요.
  const swap = await again(call, 2, plain.data.code, { title: "인기  교대 테마" });
  assert.equal(swap.status, 409);
  assert.equal(swap.data.error, "name_like");
  assert.equal(swap.data.code, famous.data.code);
  // 내 작품 이름을 그대로 두고 내용만 바꾸는 것은 돼요(자기 이름과 견주지 않아요).
  assert.equal((await again(call, 2, plain.data.code, { title: "평범한 이름", summary: "설명을 고쳤어요" })).status, 200);
  // 나만 보기로 바꾸는 새 판은 이름을 견주지 않아요.
  assert.equal((await again(call, 2, plain.data.code, { title: "인기 교대 테마" }, "private")).status, 200);
  // 말 못 하기 중이면 남이 보는 새 판은 안 돼요(나만 보기 판은 돼요).
  const dev2 = DB.db.prepare("SELECT owner FROM recipes WHERE code = ?").get(plain.data.code).owner;
  DB.db.prepare("INSERT INTO muted (device, until, reason) VALUES (?, ?, 'test')").run(dev2, AFTER + 86400e3);
  const muted = await again(call, 2, plain.data.code, { title: "평범한 이름 둘" }, "public");
  assert.equal(muted.status, 403);
  assert.equal(muted.data.error, "muted");
  assert.equal((await again(call, 2, plain.data.code, { title: "나만 고치는 판" }, "private")).status, 200);
  DB.db.prepare("DELETE FROM muted").run();
  // 신고 누적 쓰기 제한(하루 2번)이 새 판에도.
  DB.db.prepare("INSERT INTO write_limits (device, step, since, until, per_day, times) VALUES (?, 2, ?, ?, 2, 1)").run(dev2, AFTER, AFTER + 7 * 86400e3);
  const sent = [];
  for (const i of [1, 2, 3]) sent.push((await again(call, 2, plain.data.code, { title: "평범한 이름", summary: `고침 ${i}` }, "public")).status);
  assert.deepEqual(sent, [200, 200, 429]);
});

test("[RVQ-26] SVC-52 돌려준 작품은 공개 범위만 바꿔서는 다시 보이지 않고, 내용을 고친 새 판이면 올라가 관리자 '다시 올라온 것'에 보여요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  const w = await up(call, 1, { title: "돌려받을 작품" });
  const code = w.data.code;
  await call("POST", `/api/admin/recipes/${code}/return`, { note: "개인 연락처를 빼 주세요" }, admin);
  const before = DB.db.prepare("SELECT updated_at FROM recipes WHERE code = ?").get(code).updated_at;
  t.mock.timers.tick(60e3);
  // 공개 → 링크 → 공개: 앞에서는 한 번에 visible로 돌아왔어요.
  const toLink = await again(call, 1, code, { title: "돌려받을 작품" }, "link");
  assert.equal(toLink.status, 200);
  assert.equal(toLink.data.state, "returned");
  const back = await again(call, 1, code, { title: "돌려받을 작품" }, "public");
  assert.equal(back.data.state, "returned");
  const row = DB.db.prepare("SELECT state, updated_at FROM recipes WHERE code = ?").get(code);
  assert.equal(row.state, "returned");
  assert.equal(row.updated_at, before, "범위만 바꾸면 새로 순에서 올라가지 않아요");
  assert.equal((await call("GET", "/api/recipes?sort=new&style=SHIFT")).data.items.length, 0);
  // 내용을 고친 새 판: 바로 보이고, 관리자에게 '돌려준 뒤 다시 올라온 것'으로.
  const fixed = await again(call, 1, code, { title: "돌려받을 작품", summary: "연락처를 뺐어요" });
  assert.equal(fixed.data.state, "visible");
  const tab = await call("GET", "/api/admin/recipes?tab=again", undefined, admin);
  assert.deepEqual(tab.data.items.map((i) => i.code), [code]);
  // 괜찮아요(올림)를 누르면 목록에서 빠져요.
  await call("POST", `/api/admin/recipes/${code}/approve`, {}, admin);
  assert.equal((await call("GET", "/api/admin/recipes?tab=again", undefined, admin)).data.items.length, 0);
  // 범위 바꾸기도 하루 몫에 들어가요.
  const w2 = await up(call, 3, { title: "범위만 자주 바꾸기" });
  const codes = [];
  for (let i = 0; i < 11; i++) codes.push((await again(call, 3, w2.data.code, { title: "범위만 자주 바꾸기" }, i % 2 ? "public" : "link")).status);
  assert.equal(codes.filter((s) => s === 429).length, 1, codes.join(","));
});

test("[RVQ-26] SVC-23 지난 판은 만든 분만 열고, 다른 분은 ?v=로 물어도 지금 판을 받아요. 만든 분·운영자는 지난 판을 지울 수 있어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const w = await up(call, 1, { title: "고친 작품", summary: "예전 설명: 고치기 전 문장" });
  const code = w.data.code;
  const v2 = await again(call, 1, code, { title: "고친 작품", summary: "새 설명" });
  assert.equal(v2.data.version, 2);
  // 다른 분: ?v=1을 물어도 2판.
  const other = await open(call, code, 2, "?v=1");
  assert.equal(other.status, 200);
  assert.equal(other.data.version, 2);
  assert.equal(other.data.hash, v2.data.hash);
  assert.doesNotMatch(other.data.body, /예전 설명/);
  // 휴대폰 이름 없이(가장자리 캐시 길) 물어도 같아요.
  const bare = await call("GET", `/api/recipes/${code}?v=1`);
  assert.equal(bare.data.version, 2);
  // 만든 분은 1판을 볼 수 있어요.
  const own = await open(call, code, 1, "?v=1");
  assert.equal(own.data.version, 1);
  assert.match(own.data.body, /예전 설명/);
  // 만든 분의 '지난 판 지우기'.
  assert.equal((await call("DELETE", `/api/recipes/${code}/versions`, undefined, { "x-device": device(2) })).status, 403);
  const drop = await call("DELETE", `/api/recipes/${code}/versions`, undefined, { "x-device": device(1) });
  assert.equal(drop.data.dropped, 1);
  assert.equal((await open(call, code, 1, "?v=1")).data.version, 2);
  // 운영자는 판 하나 또는 지난 판 모두를 지우고, 처리 기록에 남아요.
  await again(call, 1, code, { title: "고친 작품", summary: "세 번째" });
  await again(call, 1, code, { title: "고친 작품", summary: "네 번째" });
  const admin = await signIn(call);
  assert.equal((await call("POST", `/api/admin/recipes/${code}/past`, { version: 4 }, admin)).status, 400, "지금 판은 못 지워요");
  assert.equal((await call("POST", `/api/admin/recipes/${code}/past`, { version: 2 }, admin)).data.dropped, 1);
  assert.deepEqual(DB.db.prepare("SELECT version FROM recipe_versions WHERE code = ? ORDER BY version").all(code).map((r) => r.version), [3, 4]);
  assert.equal((await call("POST", `/api/admin/recipes/${code}/past`, {}, admin)).data.dropped, 1);
  const log = (await call("GET", "/api/admin/recipes/actions", undefined, admin)).data.items.filter((a) => a.action === "past");
  assert.deepEqual(log.map((a) => a.reason), ["지난 판 1개", "2판"]);
});

// ---- 프로필·사진·글꼴 [RVQ-27] ----

const me = (call, n, body) => call("POST", "/api/recipes/makers/me", { device: device(n), ...body }, from(n));
const page = (call, id, n) => call("GET", `/api/recipes/makers/${id}`, undefined, { "x-device": device(n) });
const flag = (call, id, n, reason = "bio") => call("POST", `/api/recipes/makers/${id}/flag`, { device: device(n), reason }, from(n));
/** 처리방침 3.12 시행일이 지났어요(관리자 앱 › 기능 관리 › 일정): 표식(plaza.people)과 '확인한 신고만 세기'가 켜져요. */
async function policy312(call, admin) {
  const r = await call("POST", "/api/admin/policy", { confirm: true, version: "3.12", posted: "2026-10-31", effective: "2026-11-07", heldUntil: "2026-11-07", why: "시험" }, admin);
  assert.equal(r.status, 200, JSON.stringify(r.data));
}

/** Phones known on the 나눔 판 for 3 days (their reports count), then the clock moves on. */
async function known(call, t, ns) {
  for (const n of ns) await call("GET", "/api/recipes/mine", undefined, { "x-device": device(n) });
  t.mock.timers.tick(3 * 86400e3 + 1000);
}

test("[RVQ-27] SVC-24 운영자가 가린 소개는 한 글자 바꿔도 다시 보이지 않고 확인을 기다려요. 신고로 가려진 소개는 하루 3번까지 고쳐요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  const m = (await me(call, 1, { bio: "주말마다 테마 만들어요" })).data.maker;
  await call("POST", `/api/admin/recipes/makers/${m}/bio-hide`, { note: "광고 문구" }, admin);
  // 한 글자 바꾸기: 앞에서는 바로 다시 보였어요.
  const fixed = await me(call, 1, { bio: "주말마다 테마 만들어요!" });
  assert.equal(fixed.data.bioHidden, true);
  assert.equal(fixed.data.bioChecking, true);
  assert.equal((await page(call, m, 2)).data.bio, undefined);
  const mine = (await page(call, m, 1)).data;
  assert.deepEqual([mine.bioHidden, mine.bioHiddenBy, mine.bioChecking], [true, "operator", true]);
  // 관리자 프로필 신고 목록 맨 위에 '고친 소개 확인 대기'로.
  const list = (await call("GET", "/api/admin/recipes/makers/flagged", undefined, admin)).data.items;
  assert.deepEqual(list.map((x) => [x.maker, x.bioChecking, x.flags]), [[m, true, 0]]);
  await call("POST", `/api/admin/recipes/makers/${m}/bio-show`, {}, admin);
  assert.equal((await page(call, m, 2)).data.bio, "주말마다 테마 만들어요!");
  // 만든 분 알림(내 프로필 최근 알림)에 '소개가 다시 보여요'.
  const inbox = (await call("GET", "/api/recipes/makers/me/inbox", undefined, { "x-device": device(1) })).data;
  assert.ok(inbox.bioBack);
  // 처리 기록에 누가 무엇을 왜.
  const log = (await call("GET", "/api/admin/recipes/actions", undefined, admin)).data.items.filter((a) => a.target === "maker");
  assert.deepEqual(log.map((a) => [a.action, a.reason, a.actor]), [["bio-show", "고친 소개를 보고 다시 보이게 함", "최고 관리자"], ["bio-hide", "광고 문구", "최고 관리자"]]);
  // 신고로 가려진 소개(1)는 고치면 바로 보이지만, 가려진 채 고치기는 하루 3번까지.
  const m3 = (await me(call, 3, { bio: "현장 단가표 나눠요" })).data.maker;
  await known(call, t, [4, 5, 6]);
  for (const n of [4, 5, 6]) await flag(call, m3, n);
  assert.deepEqual([(await page(call, m3, 3)).data.bioHiddenBy, (await page(call, m3, 7)).data.bio], ["flags", undefined]);
  const tries = [];
  for (const i of [1, 2, 3, 4]) {
    DB.db.prepare("UPDATE recipe_makers SET bio_hidden = 1 WHERE maker = ?").run(m3);
    tries.push((await me(call, 3, { bio: `단가표 나눠요 ${i}` })).status);
  }
  assert.deepEqual(tries, [200, 200, 200, 429]);
  assert.equal((await page(call, m3, 3)).data.bio, "단가표 나눠요 3");
});

test("[RVQ-27] SVC-58·T22b-04 소개를 가릴 때 쓴 신고는 처리됨: 고친 소개는 새 신고 셋이어야 다시 가려지고, 같은 폰이 거듭 눌러도 경고가 쌓이지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  await policy312(call, admin);
  const m = (await me(call, 1, { bio: "야간 근무 테마 만들어요" })).data.maker;
  await known(call, t, [2, 3, 4, 5, 6, 7, 8]);
  for (const n of [2, 3, 4]) await flag(call, m, n);
  assert.equal((await page(call, m, 1)).data.bioHidden, true);
  const strikes = () => DB.db.prepare("SELECT COUNT(*) AS n FROM write_strikes WHERE kind = 'bio'").get().n;
  assert.equal(strikes(), 1);
  // 소개를 고치면 다시 보여요.
  await me(call, 1, { bio: "야간 근무 테마를 만들어요" });
  assert.equal((await page(call, m, 9)).data.bio, "야간 근무 테마를 만들어요");
  // 앞: 신고 한 번이면 다시 가려지고 경고가 하나 더 쌓였어요.
  await flag(call, m, 5);
  assert.equal((await page(call, m, 9)).data.bio, "야간 근무 테마를 만들어요");
  assert.equal(strikes(), 1);
  // 같은 폰이 다음 날 또 눌러도 한 번이에요.
  t.mock.timers.tick(86400e3);
  const again = await flag(call, m, 5);
  assert.equal(again.data.already, true);
  // 새 신고 셋이 되어야 다시 가려지고, 대기 경고는 하나만.
  await flag(call, m, 6);
  await flag(call, m, 7);
  assert.equal((await page(call, m, 9)).data.bio, undefined);
  assert.equal(strikes(), 1, "확인 기다리는 경고는 하나만");
  // 운영자가 '소개 다시 보이기'를 누르면 신고가 비고 그 경고도 거둬요.
  await call("POST", `/api/admin/recipes/makers/${m}/bio-show`, {}, admin);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM recipe_maker_flags WHERE maker = ?").get(m).n, 0);
  assert.equal(strikes(), 0);
  // 처리된 신고를 한 폰도 고친 소개를 다시 신고할 수 있어요(새 신고로 세요).
  await flag(call, m, 2);
  assert.equal(DB.db.prepare("SELECT counted, settled FROM recipe_maker_flags WHERE maker = ? AND device != ''").all(m).length, 1);
});

test("[RVQ-27] SVC-59 ⭐는 이웃 이상 팔로워만, 한 네트워크의 하루 팔로우에 한도, 🏅는 운영자가 고른 분만(센 분은 관리자 후보)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  await policy312(call, admin);
  const w = await up(call, 1, { title: "인기 받을 작품" });
  const m = (await page(call, (await me(call, 1, { face: "🐻" })).data.maker, 1)).data.maker;
  // 새 설치 30대가 한 네트워크에서 팔로우: 앞에서는 바로 ⭐.
  const net = { "cf-connecting-ip": "198.51.100.200" };
  for (let n = 100; n < 130; n++) await call("POST", `/api/recipes/makers/${m}/follow`, { device: device(n) }, net);
  const card = async () => (await call("GET", `/api/recipes/${w.data.code}`, undefined, { "x-device": device(2) })).data;
  // 표식은 처리방침 3.12 깃발 뒤에 붙어요(시험 서버 기본은 켜짐인지 따라).
  const c0 = await card();
  assert.equal(c0.followers, 30);
  assert.equal(c0.mark, undefined, "새 설치 팔로우로는 ⭐가 붙지 않아요");
  // 한 네트워크 하루 300번에서 멈춰요.
  assert.equal(DB.db.prepare("UPDATE counters SET count = 300 WHERE key LIKE '%follownet|%'").run().changes, 1);
  assert.equal((await call("POST", `/api/recipes/makers/${m}/follow`, { device: device(131) }, net)).status, 429);
  // 사흘이 지나 이웃이 된 팔로워는 세요(팔로우할 때 나눔 판 시계가 시작돼요).
  t.mock.timers.tick(3 * 86400e3 + 1000);
  assert.equal((await card()).mark, "popular");
  // 쓰는 분이 가장 많아도 🏅는 운영자가 고른 뒤에만.
  for (let n = 10; n < 14; n++) await call("POST", `/api/recipes/${w.data.code}/applied`, { kind: "applied" }, from(n));
  assert.deepEqual((await call("GET", "/api/recipes/makers/month")).data.items, []);
  const adminMonth = (await call("GET", "/api/admin/recipes/makers/month", undefined, admin)).data;
  assert.deepEqual(adminMonth.candidates.ALL.map((c) => c.maker), [m]);
  await call("POST", "/api/admin/recipes/makers/month", { style: "ALL", makers: [m] }, admin);
  const picked = (await call("GET", "/api/recipes/makers/month")).data.items;
  assert.deepEqual(picked.map((x) => [x.maker, x.picked, x.users]), [[m, true, 4]]);
  const log = (await call("GET", "/api/admin/recipes/actions", undefined, admin)).data.items.find((a) => a.target === "month");
  assert.equal(log.reason, m);
});

test("[RVQ-27] SVC-61 사진·요청·모음·글꼴 운영 조치도 처리 기록에 누가·왜와 함께 남고, 사진 가리기는 운영자 경고로 세요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  const owner = "a".repeat(64);
  DB.db.prepare("INSERT INTO recipe_images (id, owner, data, bytes, state, at) VALUES ('0123456789abcdef', ?, 'AA==', 1, 'held', ?)").run(owner, AFTER);
  await call("POST", "/api/admin/recipes/images/0123456789abcdef/hide", { note: "얼굴이 보여요" }, admin);
  const s = DB.db.prepare("SELECT kind, hidden_by FROM write_strikes WHERE device = ?").get(owner);
  assert.deepEqual([s.kind, s.hidden_by], ["image", "operator"]);
  const q = await call("POST", "/api/recipes/requests", { device: device(3), style: "SHIFT", body: "야간 많은 달력 테마가 있으면 좋겠어요" }, from(3));
  if (q.status === 201) await call("POST", `/api/admin/recipes/requests/${q.data.id}/hide`, { note: "중복" }, admin);
  const log = (await call("GET", "/api/admin/recipes/actions", undefined, admin)).data.items;
  const img = log.find((a) => a.target === "image");
  assert.deepEqual([img.action, img.reason, img.actor], ["image-hide", "얼굴이 보여요", "최고 관리자"]);
  if (q.status === 201) assert.equal(log.find((a) => a.target === "request")?.action, "request-hide");
});

test("[RVQ-27] SVC-63 사진·글꼴 파일·프로필 읽기에도 네트워크 몫, 글꼴 올리기는 한도를 먼저 봐요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  await call("GET", "/api/recipes");
  DB.db.prepare("INSERT INTO recipe_images (id, owner, data, bytes, state, at) VALUES ('0123456789abcdef', 'x', 'AA==', 1, 'ok', ?)").run(AFTER);
  const got = await call("GET", "/api/recipes/images/0123456789abcdef");
  assert.equal(got.status, 200);
  // 사진 몫(시간당 2만)을 다 쓰면 막혀요. 여럿이 함께 세는 값(50번에 한 번 D1에 더함)이 생긴 뒤 그 값을 넘겨 둬요.
  for (let i = 0; i < 50; i++) await call("GET", "/api/recipes/images/0123456789abcdef");
  assert.equal(DB.db.prepare("UPDATE counters SET count = 20001 WHERE key LIKE 'sc|asset|%'").run().changes, 1);
  const seen = [];
  for (let i = 0; i < 60; i++) seen.push((await call("GET", "/api/recipes/images/0123456789abcdef")).status);
  assert.ok(seen.includes(429), seen.join(","));
  // 다른 공개 읽기 몫과는 따로예요.
  assert.equal((await call("GET", "/api/recipes")).status, 200);
  // 프로필도 공개 읽기 몫(시간당 3,000)에 들어가요.
  for (let i = 0; i < 50; i++) await call("GET", "/api/recipes/makers/operator");
  DB.db.prepare("UPDATE counters SET count = 3001 WHERE key LIKE 'sc|pub|%'").run();
  const prof = [];
  for (let i = 0; i < 60; i++) prof.push((await call("GET", "/api/recipes/makers/operator")).status);
  assert.ok(prof.includes(429), prof.join(","));
  // 글꼴 올리기: 하루 몫이 찬 폰은 8MB를 풀기 전에 429(앞에서는 다 풀고 검사한 뒤에야 셌어요).
  const ttf = "A".repeat(1024);
  const first = await call("POST", "/api/recipes/fonts", { device: device(1), name: "내 글꼴", license: "ofl", ttf }, from(1));
  assert.equal(first.status, 400, "글꼴이 아닌 것은 몫을 센 뒤 거절");
  DB.db.prepare("UPDATE counters SET count = 999 WHERE key LIKE 'rfont|%'").run();
  const capped = await call("POST", "/api/recipes/fonts", { device: device(1), name: "내 글꼴", license: "ofl", ttf }, from(1));
  assert.equal(capped.status, 429);
  // 8MB를 넘는 글은 풀지 않고 413.
  const big = "A".repeat(Math.ceil(8 * 1024 * 1024 * 4 / 3) + 100);
  DB.db.prepare("DELETE FROM counters WHERE key LIKE 'rfont|%'").run();
  assert.equal((await call("POST", "/api/recipes/fonts", { device: device(2), name: "큰 글꼴", license: "ofl", ttf: big }, from(2))).status, 413);
});
