// 1.39 [PAYP-01..08] 공유 급여 비공개 (사용자 2026-11-07 "급여 계산식은 보여지지 않게 … 좋아요나 맞아요 많은순으로 위로 … 보완요청 …
// 관리자앱에서는 볼수 있게"): no phone reads a version's pay part or its lines, a 1.39 app takes it only when it puts the way in,
// versions go by 맞아요, 보완 요청 reaches the versions' senders, and a recipe others read carries no company pay part. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { sortByOk, TAKES_PER_DAY } from "../src/index.js";
import { sharedPayOut } from "../src/recipes.js";
import { server, report, setup, device, from, signIn } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const app = (n) => ({ ...from(n), "x-device": device(n), "x-pay-private": "1" });

/** 처리방침 3.12 in the owner's calendar, in force: plaza.fix_request turns on by itself. */
async function policy312(DB, effective = "2026-11-01") {
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.12": { posted: "2026-10-01", effective, heldUntil: null, unfavourable: false } })).run();
}

test("[PAYP-01][PAYP-03] a 1.39 app reads no pay, members too, and takes a version's pay only after joining, a few times a day", async () => {
  const { call } = server();
  const a = await call("POST", "/api/reports", report(1, { company: "가림정밀" }), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  // Phone 9 has not joined: no take.
  const before = await call("POST", `/api/companies/${c}/setups/${g}/take`, { device: device(9) }, from(9));
  assert.deepEqual([before.status, before.data.error], [403, "member"]);
  const joined = await call("POST", "/api/members", { device: device(9), company: c, cal: "app", with: "page" }, app(9));
  assert.equal(joined.data.joined, true);
  // The page that came with the join, and the page read again: shifts, that a pay way is there, never the pay.
  for (const page of [joined.data.page, (await call("GET", `/api/companies/${c}`, undefined, app(9))).data]) {
    assert.equal(page.member, true);
    assert.equal(page.setups[0].setup.pay, undefined);
    assert.deepEqual([page.setups[0].pay, page.setups[0].payPrivate], [true, true]);
    assert.ok(!JSON.stringify(page).includes("가산율"));
  }
  // The room too: the versions' shifts, never their pay.
  const room = (await call("GET", `/api/companies/${c}/room`, undefined, app(9))).data;
  assert.equal(room.member, true);
  assert.equal(room.base.setup.pay, undefined);
  assert.ok(!JSON.stringify(room).includes("가산율"));
  // Putting the way in: the pay part, named for the company.
  const took = await call("POST", `/api/companies/${c}/setups/${g}/take`, { device: device(9) }, from(9));
  assert.equal(took.status, 200, took.text);
  assert.equal(took.data.setup, g);
  const text = JSON.parse(took.data.text);
  assert.equal(text.company, "가림정밀");
  assert.equal(text.pay.rules.extra[3].unit, "BASE_SHARE");
  // A version with no pay part has none to take.
  const shifts = setup();
  delete shifts.pay;
  const b = await call("POST", "/api/reports", report(2, { company: "가림정밀", dept: "2공장", setup: shifts }), from(2));
  assert.equal((await call("POST", `/api/companies/${c}/setups/${b.data.setup}/take`, { device: device(9) }, from(9))).data.error, "no_pay");
  // A few a day, counted in memory: past it, 429.
  let last;
  for (let i = 0; i < TAKES_PER_DAY; i++) last = await call("POST", `/api/companies/${c}/setups/${g}/take`, { device: device(9) }, from(9));
  assert.deepEqual([last.status, last.data.error], [429, "rate_limited"]);
  // The developer app still reads the whole version.
  const h = await signIn(call);
  const queue = (await call("GET", "/api/admin/queue", undefined, h)).data;
  assert.ok(JSON.stringify(queue).includes("가산율"), "the admin app sees the pay lines");
});

test("[PAYP-02] each department's versions go by 맞아요, a doubted or turned-down one last; departments keep their order", () => {
  const v = (id, dept, persons, more = {}) => ({ id, dept, persons, opinion: { ok: 0 }, uses: 0, ...more });
  const list = [v("a", "", 3, { uses: 10 }), v("b", "", 5), v("c", "2공장", 1), v("d", "", 9, { doubted: true }), v("e", "2공장", 4), v("f", "", 5, { uses: 2 })];
  assert.deepEqual(sortByOk(list).map((s) => s.id), ["f", "b", "a", "d", "e", "c"]);
});

test("[PAYP-05][PAYP-06] 보완 요청 waits for 처리방침 3.12, says where without values, and reaches the version's senders", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const a = await call("POST", "/api/reports", report(1, { company: "보완상사" }), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  const fix = (n, more = {}) => call("POST", "/api/posts", { device: device(n), company: c, setup: g, body: "야간 시간이 달라요",
    kind: "fix", change: [{ field: "night" }, { field: "night" }, { field: "hours" }], ...more }, from(n));
  // Before 3.12 is in the calendar: closed.
  assert.deepEqual([(await fix(5)).status, (await fix(5)).data.error], [403, "closed"]);
  await policy312(DB);
  const sent = await fix(5);
  assert.equal(sent.status, 201, sent.text);
  // Only fields from the list, no reply.
  assert.equal((await fix(6, { change: [{ field: "money" }] })).status, 400);
  assert.equal((await fix(6, { parent: sent.data.id })).status, 400);
  // The talk shows where, the page how many are open.
  const talk = (await call("GET", `/api/companies/${c}/setups/${g}/posts`, undefined, { "x-device": device(8) })).data;
  const post = talk.posts.find((p) => p.kind === "fix");
  assert.deepEqual(post.change, [{ field: "night" }, { field: "hours" }]);
  assert.equal((await call("GET", `/api/companies/${c}`)).data.setups[0].fixes, 1);
  // The admin app has them under 보완 요청, where in words.
  const owner = await signIn(call);
  const listed = (await call("GET", "/api/admin/posts?status=fix", undefined, owner)).data.posts;
  assert.deepEqual(listed.map((p) => [p.kind, p.fix]), [["fix", ["야간", "통상시급 기준 시간"]]]);
  // The sender hears of it in 밤낮 소식; the writer does not hear of their own.
  const news = async (n) => (await call("GET", `/api/plaza/news?since=${new Date(AFTER - 3600_000).toISOString()}`, undefined, { "x-device": device(n) })).data.items;
  const mine = (await news(1)).find((i) => i.kind === "fix");
  assert.deepEqual([mine.company.name, mine.setup, mine.count], ["보완상사", g, 1]);
  assert.equal((await news(5)).find((i) => i.kind === "fix"), undefined);
  // The sender answers: the request is no longer open, and its writer hears of the answer.
  t.mock.timers.tick(60_000);
  const answer = await call("POST", "/api/posts", { device: device(1), company: c, setup: g, body: "명세서 보니 22시부터가 맞아요", parent: sent.data.id }, from(1));
  assert.equal(answer.status, 201, answer.text);
  assert.equal((await call("GET", `/api/companies/${c}`)).data.setups[0].fixes, undefined);
  const heard = (await news(5)).find((i) => i.kind === "fix_answer");
  assert.deepEqual([heard.setup, heard.count], [g, 1]);
});

test("[PAYP-08] a recipe others read carries no company pay part; 나만 보기 keeps it, and an older one is served without it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const file = { format: "bamnat-company", ...setup(), company: "레시피정밀" };
  const recipe = (title) => ({ format: "bamnat-recipe", v: 1, title, summary: "우리 회사 4조 2교대", styles: ["SHIFT"],
    pieces: [{ kind: "company", setup: JSON.stringify(file) }] });
  const pub = await call("POST", "/api/recipes", { device: device(1), recipe: recipe("4조 2교대 주기"), scope: "public" }, from(1));
  assert.equal(pub.status, 201, pub.text);
  const shown = (await call("GET", `/api/recipes/${pub.data.code}`)).data;
  const piece = JSON.parse(shown.body).pieces[0];
  assert.equal(JSON.parse(piece.setup).pay, undefined);
  assert.deepEqual(JSON.parse(piece.setup).rotation, file.rotation);
  // 나만 보기: the maker's own, as sent.
  const own = await call("POST", "/api/recipes", { device: device(1), recipe: recipe("내 회사 설정"), scope: "private" }, from(1));
  const back = (await call("GET", `/api/recipes/${own.data.code}`, undefined, { "x-device": device(1) })).data;
  assert.ok(JSON.parse(JSON.parse(back.body).pieces[0].setup).pay);
  // One put up before 1.39 with its pay: served without it, its hash for what is served.
  DB.db.prepare("UPDATE recipes SET body = ?, scope = 'public' WHERE code = ?").run(back.body, own.data.code);
  const old = (await call("GET", `/api/recipes/${own.data.code}`, undefined, { "x-device": device(7) })).data;
  assert.equal(JSON.parse(JSON.parse(old.body).pieces[0].setup).pay, undefined);
  assert.notEqual(old.hash, back.hash);
  const same = await sharedPayOut(old.body, old.hash);
  assert.equal(same.body, old.body);
});
