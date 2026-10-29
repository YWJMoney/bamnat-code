// 1.34 [RCP-03][RCP-28]..[RCP-36][RCP-40] 나눔 판: the shared cases with core, and the server's routes. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { server, device, from, signIn } from "./helpers.mjs";
import { cleanRecipe, nameSkeleton, reservedName, mixedScript, rankGood, rankHot, keptPercent, recipeCanonical } from "../src/recipes.js";

const cases = JSON.parse(readFileSync(new URL("../../../docs/recipe-cases.json", import.meta.url), "utf8"));
/** After 처리방침 3.8 is in force (시행 2026-11-29 KST). */
const AFTER = Date.parse("2026-11-30T03:00:00Z");
const BEFORE = Date.parse("2026-11-01T03:00:00Z");

test("[RCP-03] the server reads every shared case as core does: the same written form and hash, or the same refusal", async () => {
  for (const c of cases.read) {
    let got;
    try {
      const r = await cleanRecipe(recipeCanonical(c.in));
      got = { ok: true, canonical: r.text, hash: r.hash };
    } catch (e) {
      got = { ok: false, code: e.code };
    }
    assert.deepEqual(got, c.want, c.name);
  }
  for (const c of cases.skeleton) assert.deepEqual({ skeleton: nameSkeleton(c.name), reserved: reservedName(c.name) }, c.want, c.name);
  for (const c of cases.mixed) assert.equal(mixedScript(c.text), c.want, c.text);
  const r6 = (x) => (x === null ? undefined : Math.round(x * 1_000_000));
  for (const c of cases.rank) {
    const want = { good: r6(rankGood(c.likes, c.dislikes, c.applied, c.kept7)), hot: r6(rankHot(c.likes, c.applied, c.hours)),
      kept: keptPercent(c.applied, c.kept7) ?? undefined };
    for (const k of Object.keys(want)) if (want[k] === undefined) delete want[k];
    assert.deepEqual(want, c.want, JSON.stringify(c));
  }
});

const recipe = (extra = {}) => ({
  format: "bamnat-recipe", v: 1, title: "야간 많은 교대 세트", summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
  ...extra,
});
const up = (call, n, extra = {}, scope = "public") => call("POST", "/api/recipes", { device: device(n), recipe: recipe(extra), scope }, from(n));

test("[RCP-37] before 처리방침 3.8 is in force nothing is kept: no recipe, vote or talk", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: BEFORE });
  const { call } = server({ plaza: true });
  const res = await up(call, 1);
  assert.equal(res.status, 403);
  assert.equal(res.data.error, "not_yet");
  // Reading the plaza is open (recipe.browse).
  assert.equal((await call("GET", "/api/recipes?style=SHIFT")).status, 200);
});

test("[RCP-29][RCP-30] a new phone's first public recipes wait for the operator; link-only ones go up at once (D108)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = await up(call, 1);
  assert.equal(a.status, 201);
  assert.equal(a.data.state, "pending");
  assert.match(a.data.code, /^[2-9A-HJ-NP-Z]{8}$/);
  assert.equal(a.data.hash.length, 64);
  // Not on the plaza and not readable by others while it waits; its maker sees it.
  assert.equal((await call("GET", "/api/recipes?style=SHIFT&sort=new")).data.items.length, 0);
  assert.equal((await call("GET", `/api/recipes/${a.data.code}`, undefined, { "x-device": device(2) })).status, 404);
  const own = await call("GET", `/api/recipes/${a.data.code}`, undefined, { "x-device": device(1) });
  assert.equal(own.data.state, "pending");
  // Level 0: one a day.
  assert.equal((await up(call, 1, { title: "두 번째" }, "link")).status, 429);
  const b = await up(call, 3, { title: "링크로만 세트" }, "link");
  assert.equal(b.data.state, "visible");
  assert.equal((await call("GET", `/api/recipes/${b.data.code}`, undefined, { "x-device": device(4) })).status, 200);
  assert.equal((await call("GET", "/api/recipes?style=SHIFT&sort=new")).data.items.length, 0, "link-only is not listed");
  // The operator approves; it is listed, and the written form hashes to what the phone was told.
  const admin = await signIn(call);
  const q = await call("GET", "/api/admin/recipes?tab=queue", undefined, admin);
  assert.equal(q.data.items.length, 1);
  assert.equal((await call("POST", `/api/admin/recipes/${a.data.code}/approve`, {}, admin)).status, 200);
  const listed = await call("GET", "/api/recipes?style=SHIFT&sort=new");
  assert.equal(listed.data.items[0].code, a.data.code);
  const got = await call("GET", `/api/recipes/${a.data.code}`, undefined, { "x-device": device(9) });
  assert.equal(got.data.hash, a.data.hash);
  assert.equal((await cleanRecipe(got.data.body)).hash, got.data.hash);
  // A returned one carries the operator's note to its maker.
  const c = await up(call, 5, { title: "고칠 레시피" });
  await call("POST", `/api/admin/recipes/${c.data.code}/return`, { note: "설명을 더 적어 주세요" }, admin);
  const back = await call("GET", "/api/recipes/mine", undefined, { "x-device": device(5) });
  assert.equal(back.data.items[0].state, "returned");
  assert.equal(back.data.items[0].note, "설명을 더 적어 주세요");
});

test("[RCP-31] 안좋아요 needs a reason and a short opinion; the count is public, the reasons the maker's only (D107)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = await up(call, 1, {}, "link");
  const code = a.data.code;
  const v = (n, body) => call("POST", `/api/recipes/${code}/vote`, { device: device(n), ...body }, from(n));
  assert.equal((await v(2, { value: -1 })).data.error, "reason");
  assert.equal((await v(2, { value: -1, reason: "thin", opinion: "별로" })).data.error, "opinion");
  assert.equal((await v(2, { value: -1, reason: "thin", opinion: "설명 bamnat.kr 참고" })).data.error, "link");
  const ok = await v(2, { value: -1, reason: "odd", opinion: "야간 색이 너무 어두워요" });
  assert.equal(ok.status, 200);
  assert.deepEqual([ok.data.likes, ok.data.dislikes], [0, 1]);
  await v(3, { value: 1 });
  assert.equal((await v(1, { value: 1 })).data.error, "own");
  const seen = await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(7) });
  assert.deepEqual([seen.data.likes, seen.data.dislikes], [1, 1]);
  assert.equal(seen.data.reasons, undefined);
  const maker = await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(1) });
  assert.deepEqual(maker.data.reasons.tally, { odd: 1 });
  assert.equal(maker.data.reasons.opinions[0].text, "야간 색이 너무 어두워요");
  assert.equal(JSON.stringify(maker.data.reasons).includes("test-device"), false, "never who");
  // Taking it back takes it off the count.
  await v(2, { value: null });
  assert.equal((await call("GET", `/api/recipes/${code}`)).data.dislikes, 0);
});

test("[RCP-31][RCP-40] a burst of votes from one network waits and counts nowhere until the operator lets it (S8)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = await up(call, 1, {}, "link");
  const same = { "cf-connecting-ip": "203.0.113.77" };
  for (let n = 10; n < 45; n++) await call("POST", `/api/recipes/${a.data.code}/vote`, { device: device(n), value: 1 }, same);
  const r = await call("GET", `/api/recipes/${a.data.code}`);
  assert.equal(r.data.likes, 30);
  const admin = await signIn(call);
  const out = await call("POST", "/api/admin/recipes/votes/release", { code: a.data.code }, admin);
  assert.equal(out.data.released, 5);
  assert.equal((await call("GET", `/api/recipes/${a.data.code}`)).data.likes, 35);
});

test("[RCP-28][RCP-40] what the server refuses: too big, too deep, an operator's name, a look-alike of someone else's", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const big = await call("POST", "/api/recipes", { device: device(1), recipe: recipe({ desc: "가".repeat(40_000) }) }, from(1));
  assert.equal(big.status, 413);
  const deep = await call("POST", "/api/recipes", { device: device(1), recipe: recipe({ pieces: [{ kind: "shiftLook", items: [{ name: "a", role: "day", label: "a", color: { input: { a: { b: 1 } } } }] }] }) }, from(1));
  assert.equal(deep.data.error, "too_deep");
  assert.equal((await up(call, 2, { title: "밤낮 공식 세트" })).data.error, "name_taken");
  assert.equal((await up(call, 2, { title: "세트‮ 야간" }, "link")).status, 201, "a direction override is cleaned away, not kept");
  const first = await up(call, 3, { title: "편한 교대" });
  assert.equal(first.status, 201);
  assert.equal((await up(call, 4, { title: "편한  교대" })).data.error, "name_like");
});

test("[RCP-35] a recipe's talk: 만든 사람 and 쓰는 중 marks, a 안좋아요's reason to the writer only, 신고 hides it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = await up(call, 1, {}, "link");
  const code = a.data.code;
  const q = await call("POST", `/api/recipes/${code}/talk`, { device: device(2), body: "야간 알람도 넣어 주실 수 있나요?", kind: "question", usingDays: 12 }, from(2));
  assert.equal(q.status, 201);
  const ans = await call("POST", `/api/recipes/${code}/talk`, { device: device(1), body: "다음 판에 넣을게요", kind: "opinion", parent: q.data.id }, from(1));
  assert.equal(ans.status, 201);
  assert.equal((await call("POST", `/api/recipes/${code}/talk`, { device: device(3), body: "오픈채팅으로 오세요" }, from(3))).data.error, "chat");
  const v = await call("POST", `/api/recipes/talk/${q.data.id}/vote`, { device: device(4), value: -1, reason: "hard", opinion: "질문이 잘 이해가 안 돼요" }, from(4));
  assert.equal(v.status, 200);
  const seen = (await call("GET", `/api/recipes/${code}/talk`, undefined, { "x-device": device(5) })).data.posts;
  assert.equal(seen[0].usingDays, 12);
  assert.equal(seen[1].maker, true);
  assert.equal(seen[0].reasons, undefined);
  const writer = (await call("GET", `/api/recipes/${code}/talk`, undefined, { "x-device": device(2) })).data.posts;
  assert.deepEqual(writer[0].reasons, [{ reason: "hard", text: "질문이 잘 이해가 안 돼요" }]);
  for (const n of [6, 7, 8]) await call("POST", `/api/posts/${q.data.id}/flag`, { device: device(n), reason: "abuse" }, from(n));
  const after = (await call("GET", `/api/recipes/${code}/talk`, undefined, { "x-device": device(5) })).data.posts;
  assert.equal(after.length, 0, "the hidden question goes, and the answer under it");
});

test("[RCP-32][RCP-33] applied counts are a day's total; three 신고 hide a recipe and it is in the 임시 조치 기록", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const a = await up(call, 1, {}, "link");
  const code = a.data.code;
  await call("POST", `/api/recipes/${code}/applied`, { kind: "applied" }, from(2));
  await call("POST", `/api/recipes/${code}/applied`, { kind: "kept" }, from(2));
  const row = await DB.prepare("SELECT * FROM recipe_counts WHERE code = ?").bind(code).first();
  assert.deepEqual([row.applied, row.kept7], [1, 1]);
  assert.equal(Object.keys(row).includes("device"), false);
  for (const n of [5, 6, 7]) await call("POST", `/api/recipes/${code}/flag`, { device: device(n), reason: "fake" }, from(n));
  assert.equal((await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(9) })).status, 404);
  const admin = await signIn(call);
  const log = await call("GET", "/api/admin/recipes/actions", undefined, admin);
  assert.equal(log.data.items[0].action, "hide");
  await call("POST", `/api/admin/recipes/${code}/show`, {}, admin);
  assert.equal((await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(9) })).status, 200);
});

test("[RCP-28] 모두 지우기 takes a phone's recipes, votes and trust; a new version keeps the code", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const a = await up(call, 1, {}, "link");
  const code = a.data.code;
  const nv = await call("POST", `/api/recipes/${code}/versions`, { device: device(1), recipe: recipe({ summary: "새 판이에요" }) }, from(1));
  assert.equal(nv.data.version, 2);
  assert.equal((await call("POST", `/api/recipes/${code}/versions`, { device: device(2), recipe: recipe({ summary: "남의 것" }) }, from(2))).status, 403);
  const old = await call("GET", `/api/recipes/${code}?v=1`, undefined, { "x-device": device(3) });
  assert.equal(old.data.version, 1);
  await call("POST", `/api/recipes/${code}/vote`, { device: device(2), value: 1 }, from(2));
  const gone = await call("DELETE", "/api/mine", undefined, { "x-device": device(1) });
  assert.equal(gone.data.recipes, 1);
  assert.equal((await DB.prepare("SELECT COUNT(*) AS n FROM recipe_votes").first()).n, 0);
  assert.equal((await DB.prepare("SELECT COUNT(*) AS n FROM recipe_versions").first()).n, 0);
});

test("[RCP-36] the operator's own recipe is 공식 and listed at once; a 공개 자료 one is on its own tab", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const made = await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "서울교통공사 4조2교대" }), publicData: true }, admin);
  assert.equal(made.status, 201);
  const list = await call("GET", "/api/recipes?style=SHIFT&sort=data");
  assert.equal(list.data.items[0].official, true);
  assert.equal(list.data.items[0].publicData, true);
  const month = await call("GET", "/api/admin/recipes/month", undefined, admin);
  assert.equal(month.data.actions.official, 1);
});

test("[RCP-40] every 시작 레시피 and 공개 자료 레시피 core wrote reads the same on the server (no refusal, the same written form)", async () => {
  const starters = JSON.parse(readFileSync(new URL("../../../docs/recipes/starter-recipes.json", import.meta.url), "utf8"));
  assert.ok(starters.length >= 66);
  for (const s of starters) {
    const r = await cleanRecipe(recipeCanonical(s.recipe));
    assert.equal(r.text, recipeCanonical(s.recipe), s.recipe.title);
  }
});
