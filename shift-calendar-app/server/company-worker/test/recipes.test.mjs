// 1.34 [RCP-03][RCP-28]..[RCP-36][RCP-40] 나눔 판: the shared cases with core, and the server's routes. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { server, device, from, signIn, seasoned } from "./helpers.mjs";
import { cleanRecipe, nameSkeleton, reservedName, mixedScript, rankGood, rankHot, keptPercent, recipeCanonical, lookOf, tabsOf, scopeOf, partUnitOf, logicOnly } from "../src/recipes.js";

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

test("[RCP-29][RCP-30][RCX-28] a public recipe is on the plaza at once (no 승인 since 1.35); link-only ones are not listed", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = await up(call, 1);
  assert.equal(a.status, 201);
  assert.equal(a.data.state, "visible");
  assert.match(a.data.code, /^[2-9A-HJ-NP-Z]{8}$/);
  assert.equal(a.data.hash.length, 64);
  assert.equal((await call("GET", `/api/recipes/${a.data.code}`, undefined, { "x-device": device(2) })).status, 200);
  // Level 0: two a day.
  assert.equal((await up(call, 1, { title: "두 번째 세트" }, "link")).status, 201);
  // 1.36 [LIM-10] No daily cap without strikes.
  assert.equal((await up(call, 1, { title: "세 번째 세트" }, "link")).status, 201);
  const b = await up(call, 3, { title: "링크로만 세트" }, "link");
  assert.equal(b.data.state, "visible");
  assert.equal((await call("GET", `/api/recipes/${b.data.code}`, undefined, { "x-device": device(4) })).status, 200);
  const listed = await call("GET", "/api/recipes?style=SHIFT&sort=new");
  assert.deepEqual(listed.data.items.map((i) => i.code), [a.data.code], "link-only is not listed");
  // Nothing waits for the operator.
  const admin = await signIn(call);
  assert.equal((await call("GET", "/api/admin/recipes?tab=queue", undefined, admin)).data.items.length, 0);
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
  const deep = await call("POST", "/api/recipes", { device: device(1), recipe: recipe({ pieces: [{ kind: "shiftLook", items: [{ name: "a", role: "day", label: "a", color: { input: { a: { b: { c: { d: 1 } } } } } }] }] }) }, from(1));
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
  await seasoned(call, t, [5, 6, 7]);
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

test("[SEC-16] 보안 점검 M10: new ids and one line cannot hide a recipe; shown again, its maker's mark and the restriction it started go", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  await seasoned(call, t, [5, 6, 7, 8]);
  const admin = await signIn(call);
  const code = (await up(call, 1, { title: "남의 작품" }, "link")).data.code;
  const other = (await up(call, 1, { title: "같은 분의 다른 작품" }, "link")).data.code;
  const flags = async () => (await DB.prepare("SELECT flags, state FROM recipes WHERE code = ?").bind(code).first());
  // Three ids just made, each on its own line: kept for the operator, none counted.
  for (const n of [2, 3, 4]) assert.equal((await call("POST", `/api/recipes/${code}/flag`, { device: device(n), reason: "fake" }, from(n))).data.flagged, true);
  assert.deepEqual({ ...(await flags()) }, { flags: 0, state: "visible" });
  // Known phones: two on one line count once.
  await call("POST", `/api/recipes/${code}/flag`, { device: device(5), reason: "fake" }, from(5));
  await call("POST", `/api/recipes/${code}/flag`, { device: device(6), reason: "fake" }, from(5));
  await call("POST", `/api/recipes/${code}/flag`, { device: device(7), reason: "fake" }, from(7));
  assert.deepEqual({ ...(await flags()) }, { flags: 2, state: "visible" });
  const list = (await call("GET", "/api/admin/recipes?tab=flagged", undefined, admin)).data.items.find((x) => x.code === code);
  assert.deepEqual(list.quiet, { fresh: 3, net: 1 });
  // PC 확인 보안 6차: the reasons with how many of them counted ("센 신고 2 · 전체 6").
  assert.deepEqual(list.flags, [{ reason: "fake", n: 6, counted: 2 }]);
  await call("POST", `/api/recipes/${code}/flag`, { device: device(8), reason: "fake" }, from(8));
  assert.equal((await flags()).state, "hidden");
  // The operator hides another of the same maker's: two strikes start a restriction.
  await call("POST", `/api/admin/recipes/${other}/hide`, { note: "광고" }, admin);
  const owner = (await DB.prepare("SELECT owner FROM recipes WHERE code = ?").bind(code).first()).owner;
  const limit = () => DB.prepare("SELECT until FROM write_limits WHERE device = ?").bind(owner).first();
  assert.ok((await limit()).until > Date.now());
  assert.equal((await DB.prepare("SELECT hidden FROM recipe_trust WHERE device = ?").bind(owner).first()).hidden, 2);
  // One hidden by reports and one by the operator: the maker's line says both.
  const mine = (await call("GET", "/api/recipes/mine", undefined, { "x-device": device(1) })).data;
  const by = (await DB.prepare("SELECT hidden_by FROM write_strikes WHERE device = ? ORDER BY id").bind(owner).all()).results.map((r) => r.hidden_by);
  assert.deepEqual(by, [null, "operator"]);
  assert.deepEqual([mine.limit.by, mine.limit.n, mine.limit.kinds], ["both", 2, ["recipe"]]);
  // Shown again: the reports were wrong, so its mark goes and the restriction it started ends.
  await call("POST", `/api/admin/recipes/${code}/show`, {}, admin);
  assert.equal((await DB.prepare("SELECT hidden FROM recipe_trust WHERE device = ?").bind(owner).first()).hidden, 1);
  assert.ok((await limit()).until <= Date.now());
  const log = (await call("GET", "/api/admin/recipes/actions", undefined, admin)).data.items.find((a) => a.code === code && a.action === "show");
  assert.equal(log.reason, "신고가 맞지 않았어요");
  // A 소개 the same way: new ids do not hide it.
  const m = (await call("POST", "/api/recipes/makers/me", { device: device(9), bio: "제 소개예요" }, from(9))).data.maker;
  for (const n of [2, 3, 4]) await call("POST", `/api/recipes/makers/${m}/flag`, { device: device(n), reason: "bio" }, from(n));
  assert.equal((await call("GET", `/api/recipes/makers/${m}`, undefined, { "x-device": device(10) })).data.bio, "제 소개예요");
});

test("[SEC-15] 보안 점검 M9: one line counts a few 넣기 and 7일 유지 a recipe a day, the rest answered but not counted", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const code = (await up(call, 1, {}, "link")).data.code;
  const other = (await up(call, 4, {}, "link")).data.code;
  for (let i = 0; i < 10; i++) {
    const res = await call("POST", `/api/recipes/${code}/applied`, { kind: "applied" }, from(2));
    assert.equal(res.status, 200);
    if (i >= 3) assert.equal(res.data.counted, false);
  }
  for (let i = 0; i < 5; i++) await call("POST", `/api/recipes/${code}/applied`, { kind: "kept" }, from(2));
  let row = await DB.prepare("SELECT applied, kept7 FROM recipes WHERE code = ?").bind(code).first();
  assert.deepEqual([row.applied, row.kept7], [3, 3]);
  // Another line, and another recipe from the same line, count as before.
  await call("POST", `/api/recipes/${code}/applied`, { kind: "applied" }, from(3));
  await call("POST", `/api/recipes/${other}/applied`, { kind: "applied" }, from(2));
  row = await DB.prepare("SELECT applied FROM recipes WHERE code = ?").bind(code).first();
  assert.equal(row.applied, 4);
  assert.equal((await DB.prepare("SELECT applied FROM recipes WHERE code = ?").bind(other).first()).applied, 1);
  // The next day the line counts again.
  t.mock.timers.tick(24 * 60 * 60 * 1000);
  await call("POST", `/api/recipes/${code}/applied`, { kind: "applied" }, from(2));
  assert.equal((await DB.prepare("SELECT applied FROM recipes WHERE code = ?").bind(code).first()).applied, 5);
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

test("[BLU-07] the same 판 sent again with another scope only moves it (PC 8차: 올린 판을 나만 보기로); the same scope is still 'same'", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const a = await up(call, 1, {}, "link");
  const code = a.data.code;
  const mine = await call("POST", `/api/recipes/${code}/versions`, { device: device(1), recipe: recipe(), scope: "private" }, from(1));
  assert.equal(mine.status, 200);
  assert.deepEqual([mine.data.version, mine.data.scope, mine.data.state], [1, "private", "visible"]);
  assert.equal((await call("POST", `/api/recipes/${code}/versions`, { device: device(1), recipe: recipe(), scope: "private" }, from(1))).data.error, "same");
  assert.equal((await call("GET", `/api/recipes/${code}`, undefined, { "x-device": device(1) })).data.scope, "private");
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

test("[RCX-32] 보여주기: the operator's showcase is listed on its own sort with its mark, and a row can be put in or taken out", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const made = await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "3교대 간호 풀세트" }), showcase: true }, admin);
  assert.equal(made.status, 201);
  const shown = await call("GET", "/api/recipes?style=SHIFT&sort=showcase");
  assert.deepEqual(shown.data.items.map((i) => [i.title, i.showcase, i.official]), [["3교대 간호 풀세트", true, true]]);
  // A user's recipe is not a showcase until the operator puts it in, and is out again when taken back.
  const a = await up(call, 1, { title: "내 야간 판" });
  assert.equal((await call("GET", "/api/recipes?style=SHIFT&sort=showcase")).data.items.length, 1);
  assert.equal((await call("POST", `/api/admin/recipes/${a.data.code}/showcase`, { on: true }, admin)).status, 200);
  assert.equal((await call("GET", "/api/recipes?style=SHIFT&sort=showcase")).data.items.length, 2);
  const tab = await call("GET", "/api/admin/recipes?tab=showcase", undefined, admin);
  assert.equal(tab.data.items.length, 2);
  await call("POST", `/api/admin/recipes/${a.data.code}/showcase`, { on: false }, admin);
  assert.deepEqual((await call("GET", "/api/recipes?style=SHIFT&sort=showcase")).data.items.map((i) => i.title), ["3교대 간호 풀세트"]);
});

test("[RCX-32] every 보여주기 판 core wrote reads the same on the server, five for each kind of work", async () => {
  const shows = JSON.parse(readFileSync(new URL("../../../docs/recipes/showcase-recipes.json", import.meta.url), "utf8"));
  assert.equal(shows.length, 30);
  for (const s of shows) {
    assert.equal(s.showcase, true);
    const r = await cleanRecipe(recipeCanonical(s.recipe));
    assert.equal(r.text, recipeCanonical(s.recipe), s.recipe.title);
  }
  for (const style of ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"]) assert.equal(shows.filter((s) => s.recipe.styles[0] === style).length, 5, style);
});

test("[FREE-07][FREE-08] 꾸미기 pieces read as core does, a list card carries the look, and 종류로 보기 keeps only that kind", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const skin = {
    kind: "screenLook", screens: "all~bg:FFFFF8EE|cd:FFFFFFFF|ik:FF2B2B2B|ac:FFE07A5F/plaza~em:1f338", pay: "SHIFT~tt:xl|pl:FFE07A5F",
  };
  const view = { kind: "calView", looks: "bg:shift|ba:30|cr:8|dp:tl|ds:l|dw:b|mk:dot|mp:bottom/bg:none|bd:thin|dp:mc", pattern: "cycle:2", today: 1 };
  const memo = { kind: "memoLook", layout: "sticky", card: "own", corner: 4, lines: 6, font: "hand" };
  const money = { kind: "moneyLook", rows: "roomy", amount: "l", dayHead: "band" };
  const good = await cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, skin, view, memo, money] })));
  assert.ok(good.text.includes('"kind":"screenLook"'));
  for (const [bad, code] of [
    [{ ...view, looks: "bg:shift|bg:none" }, "looks"], [{ ...view, pattern: "cycle:9" }, "pattern"],
    [{ ...skin, screens: "lock~bg:FF000000" }, "screens"], [{ ...skin, screens: "all~ph:../../x" }, "screens"], [{ ...skin, pay: "BOSS~tt:xl" }, "pay"],
    [{ ...memo, layout: "free" }, "pick"],
  ]) {
    await assert.rejects(cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, bad] }))), (e) => e.code !== undefined, JSON.stringify(bad));
    void code;
  }
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "벚꽃 교대 테마", pieces: [...recipe().pieces, skin, view, memo] }), showcase: true }, admin);
  await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "알람만 세트", pieces: [{ kind: "alarms", byRole: [{ role: "day", before: [60] }] }] }) }, admin);
  const all = await call("GET", "/api/recipes?style=SHIFT&sort=new");
  const themed = all.data.items.find((i) => i.title === "벚꽃 교대 테마");
  assert.equal(themed.look.bg, "FFFFF8EE");
  assert.equal(themed.look.accent, "FFE07A5F");
  assert.equal(themed.look.pattern, "cycle:2");
  assert.equal(themed.look.memo, "sticky");
  assert.equal(themed.look.days.length, 2);
  assert.equal(all.data.items.find((i) => i.title === "알람만 세트").look, undefined);
  const skins = await call("GET", "/api/recipes?style=SHIFT&sort=new&kind=skin");
  assert.deepEqual(skins.data.items.map((i) => i.title), ["벚꽃 교대 테마"]);
  const rules = await call("GET", "/api/recipes?style=SHIFT&sort=new&kind=rules");
  assert.deepEqual(rules.data.items.map((i) => i.title), ["알람만 세트"]);
});

test("[MKX-01] 장터 두 구간: a recipe's tabs, 테마 (two tabs or the whole app) or one tab's, and the list by scope and tab", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  assert.deepEqual(tabsOf({ pieces: [{ kind: "calView", size: "tall" }, { kind: "calTheme", sun: 1 }] }), ["calendar"]);
  assert.deepEqual(tabsOf({ pieces: [{ kind: "screenLook", screens: "money~bg:FF000000" }, { kind: "moneyLook", rows: "roomy" }] }), ["money"]);
  assert.deepEqual(tabsOf({ pieces: [{ kind: "screenLook", screens: "all~bg:FF000000/calendar~bg:FF111111" }] }), ["app", "calendar"]);
  assert.deepEqual(tabsOf({ pieces: [{ kind: "cards", stats: "a" }, { kind: "screenLook", screens: "pay.SHIFT~tt:xl" }] }), ["pay"]);
  assert.equal(scopeOf(["calendar"]), "tab");
  assert.equal(scopeOf(["app"]), "theme");
  assert.equal(scopeOf(["calendar", "memo"]), "theme");
  assert.equal(scopeOf([]), null);
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const theme = { kind: "screenLook", screens: "all~bg:FFFFF8EE|cd:FFFFFFFF/calendar~bg:FFFFF0F0" };
  await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "온 앱 테마", pieces: [theme, { kind: "calView", size: "tall" }] }) }, admin);
  await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "가계부 전문", pieces: [{ kind: "moneyLook", rows: "roomy" }] }) }, admin);
  await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "알람만", pieces: [{ kind: "alarms", byRole: [{ role: "day", before: [60] }] }] }) }, admin);
  const themes = await call("GET", "/api/recipes?style=SHIFT&sort=new&scope=theme&kind=skin");
  assert.deepEqual(themes.data.items.map((i) => i.title), ["온 앱 테마"]);
  assert.equal(themes.data.items[0].scope, "theme");
  const tabs = await call("GET", "/api/recipes?style=SHIFT&sort=new&scope=tab");
  assert.deepEqual(tabs.data.items.map((i) => i.title).sort(), ["가계부 전문", "알람만"].sort());
  const money = await call("GET", "/api/recipes?style=SHIFT&sort=new&scope=tab&tab=money&kind=money");
  assert.deepEqual(money.data.items.map((i) => [i.title, i.tabs]), [["가계부 전문", ["money"]]]);
});

test("[RUL-07] 규칙·단추만: a recipe of only the logic piece is its own section (scope logic), not one tab's", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const logic = { kind: "logic", rules: [{ name: "퇴근 뒤 체크 비우기", on: "end", min: 30, do: "checkReset" }],
    buttons: [{ label: "오늘 기록", emoji: "📝", where: "day", do: "memoTpl", arg: "하루 기록" }] };
  assert.equal(logicOnly({ pieces: [logic] }), true);
  assert.equal(logicOnly({ pieces: [logic, { kind: "calView", size: "tall" }] }), false);
  assert.equal(scopeOf(["alarm"], JSON.stringify({ pieces: [logic] })), "logic");
  // 누락 점검 N8: a button's id (꾸밈 층 tap), the 꾸밈 층 place and 길게 누르면.
  const n8 = { kind: "logic", buttons: [{ id: "deco1", label: "알려 줘", where: "decor", do: "notify", arg: "꾸밈" }],
    rules: [{ name: "길게 메모장", on: "hold", do: "screen", arg: "memo" }] };
  const ok = await cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, n8] })));
  for (const k of ['"id":"deco1"', '"where":"decor"', '"on":"hold"']) assert.ok(ok.text.includes(k), k);
  await assert.rejects(cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, { ...n8, buttons: [{ ...n8.buttons[0], id: "Bad-Id" }] }] }))), (e) => e.code !== undefined);
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const put = await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "퇴근 규칙·단추", pieces: [logic] }) }, admin);
  assert.ok(put.status < 300, JSON.stringify(put.data));
  await call("POST", "/api/admin/recipes", { recipe: recipe({ title: "알람만", pieces: [{ kind: "alarms", byRole: [{ role: "day", before: [60] }] }] }) }, admin);
  const got = await call("GET", "/api/recipes?style=SHIFT&sort=new&scope=logic&kind=rules");
  assert.deepEqual(got.data.items.map((i) => [i.title, i.scope]), [["퇴근 규칙·단추", "logic"]]);
  const tabs = await call("GET", "/api/recipes?style=SHIFT&sort=new&scope=tab");
  assert.deepEqual(tabs.data.items.map((i) => i.title), ["알람만"]);
});

test("[FREE-09] every 추천 테마 core wrote reads the same on the server, nine for each kind of work (전문 달력 first, TAB-05), each with its look for the list", async () => {
  const themes = JSON.parse(readFileSync(new URL("../../../docs/recipes/theme-recipes.json", import.meta.url), "utf8"));
  assert.equal(themes.length, 54);
  for (const s of themes) {
    const r = await cleanRecipe(recipeCanonical(s.recipe));
    assert.equal(r.text, recipeCanonical(s.recipe), s.recipe.title);
    const look = lookOf(r.text);
    assert.ok(look && look.bg && look.cells && look.pattern, s.recipe.title);
  }
  for (const style of ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"]) assert.equal(themes.filter((s) => s.recipe.styles[0] === style).length, 9, style);
  const experts = themes.filter((s) => s.recipe.title.endsWith("전문 달력"));
  assert.equal(experts.length, 6);
  for (const s of experts) assert.ok(s.recipe.pieces.some((p) => p.kind === "tabView"), s.recipe.title);
});

test("[FREE-10] 오늘 says when themes opened with no ad jump (yesterday twice the five days before, 10 or more)", async () => {
  const { alertsFrom } = await import("../src/admin-insight.js");
  const at = (list) => alertsFrom({ trend: { themeFree: list } }).find((a) => a.key === "themeFree");
  assert.ok(at([3, 4, 2, 3, 3, 12, 0]), "12 against ~3 a day");
  assert.equal(at([3, 4, 2, 3, 3, 9, 0]), undefined, "under 10 is quiet");
  assert.equal(at([10, 12, 11, 9, 10, 15, 0]), undefined, "steady is quiet");
  assert.match(at([0, 0, 0, 0, 0, 10, 40]).title, /어제 10번/);
});

test("[STU-38] 1.36 위젯 꾸미기: background and text colour, corners and text size read; out of range is refused", async () => {
  const w = { kind: "widget", alpha: 90, theme: "dark", bg: 4280427042, ink: 4294967295, corner: 12, text: "l" };
  const good = await cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, w] })));
  for (const k of ['"corner":12', '"text":"l"', '"bg":4280427042', '"ink":4294967295']) assert.ok(good.text.includes(k), k);
  const old = await cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, { kind: "widget", alpha: 80 }] })));
  assert.ok(old.text.includes('"alpha":80'), "예전 위젯 조각도 그대로");
  for (const bad of [{ ...w, corner: 40 }, { ...w, text: "xl" }, { ...w, bg: "red" }]) {
    await assert.rejects(cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, bad] }))), (e) => e.code !== undefined, JSON.stringify(bad));
  }
});

test("[BLU-07] 부품 하나: the unit of a recipe that is one blueprint part, and its screen", () => {
  const head = { pieces: [{ kind: "parts", parts: [{ id: "cal.head", r: 12 }, { id: "cal.head.title", font: "serif" }] }] };
  assert.equal(partUnitOf(head), "part:cal.head");
  assert.equal(partUnitOf(JSON.stringify(head)), "part:cal.head");
  assert.equal(partUnitOf({ pieces: [{ kind: "tabView", memo: "board", memoBy: "tag" }] }), "memo.view");
  assert.equal(partUnitOf({ pieces: [{ kind: "tabView", cal: "rota", money: "cal" }] }), null);
  assert.deepEqual(tabsOf(head), ["calendar"]);
  assert.equal(partUnitOf({ pieces: [{ kind: "parts", parts: [{ id: "cal.head" }, { id: "memo.card" }] }] }), null);
  assert.equal(partUnitOf({ pieces: [head.pieces[0], { kind: "calTheme", today: 1 }] }), null);
  assert.deepEqual(tabsOf({ pieces: [{ kind: "parts", parts: [{ id: "app.tab", on: "sel" }, { id: "money.amount", on: "in" }] }] }), ["app", "money"]);
});

test("[BLU-08] 글자 그림자·밑줄: the parts' letter shadow and line read; out of range is refused", async () => {
  const p = { kind: "parts", parts: [{ id: "cal.head.title", tshadow: 3, tshadowC: 4278190080, tshadowX: 0, tshadowY: -1.5, deco: "under" }] };
  const good = await cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, p] })));
  for (const k of ['"tshadow":3', '"tshadowY":-1.5', '"deco":"under"']) assert.ok(good.text.includes(k), k);
  for (const bad of [{ id: "cal.head.title", tshadow: 13 }, { id: "cal.head.title", deco: "wavy" }, { id: "cal.head.title", tshadowX: -13 }]) {
    await assert.rejects(cleanRecipe(recipeCanonical(recipe({ pieces: [...recipe().pieces, { kind: "parts", parts: [bad] }] }))), (e) => e.code !== undefined, JSON.stringify(bad));
  }
});
