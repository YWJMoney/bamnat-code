// 1.36 [MKS-01] 출시 대기: operator works put in ahead of an app update, hidden until released, shown only to new enough apps. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { server, signIn } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const recipe = (title, extra = {}) => ({
  format: "bamnat-recipe", v: 1, title, summary: "출시 작품", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }],
  ...extra,
});
const titles = (r) => r.data.items.map((i) => i.title);

test("[MKS-01] a held work waits unseen, comes up on 지금 공개, and only for apps at its minApp or newer", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const a = await call("POST", "/api/admin/recipes", { recipe: recipe("밤하늘 교대 풀세트"), hold: true, batch: "1.36 출시 작품", minApp: 49 }, admin);
  assert.equal(a.status, 201);
  assert.equal(a.data.state, "waiting");
  await call("POST", "/api/admin/recipes", { recipe: recipe("모든 앱 작품"), hold: true, batch: "1.36 출시 작품" }, admin);
  // Nobody sees them while they wait, not even by code.
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=49")), []);
  assert.equal((await call("GET", `/api/recipes/${a.data.code}`)).status, 404);
  const waiting = await call("GET", "/api/admin/recipes?tab=waiting", undefined, admin);
  assert.equal(waiting.data.items.length, 2);
  assert.equal(waiting.data.items.find((i) => i.title === "밤하늘 교대 풀세트").minApp, 49);
  const sum = await call("GET", "/api/admin/recipes/release", undefined, admin);
  assert.deepEqual(sum.data.items.map((b) => [b.batch, b.n, b.minApp]), [["1.36 출시 작품", 2, 49]]);
  const out = await call("POST", "/api/admin/recipes/release", { batch: "1.36 출시 작품" }, admin);
  assert.equal(out.data.released, 2);
  // An app before 1.36 sends no app=: it sees the one with no minApp only.
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new")), ["모든 앱 작품"]);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=48")), ["모든 앱 작품"]);
  assert.deepEqual(new Set(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=49"))), new Set(["밤하늘 교대 풀세트", "모든 앱 작품"]));
  assert.equal((await call("GET", "/api/admin/recipes/release", undefined, admin)).data.items.length, 0);
});

test("[MKS-01] a scheduled batch comes up by itself at its time; replace keeps one work and makes a new 판", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const cron = async (at) => {
    t.mock.timers.setTime(at);
    const waits = [];
    await worker.scheduled({ cron: "17 * * * *", scheduledTime: at }, env, { waitUntil: (p) => waits.push(p) });
    await Promise.all(waits);
  };
  const admin = await signIn(call);
  const a = await call("POST", "/api/admin/recipes", { recipe: recipe("벚꽃 달력"), hold: true, batch: "봄", replace: true }, admin);
  const b = await call("POST", "/api/admin/recipes", { recipe: recipe("벚꽃 달력", { summary: "새 형식으로 다시" }), hold: true, batch: "봄", replace: true }, admin);
  assert.equal(b.data.code, a.data.code);
  assert.equal(b.data.version, 2);
  const same = await call("POST", "/api/admin/recipes", { recipe: recipe("벚꽃 달력", { summary: "새 형식으로 다시" }), hold: true, batch: "봄", replace: true }, admin);
  assert.equal(same.data.same, true);
  assert.equal((await call("GET", "/api/admin/recipes?tab=waiting", undefined, admin)).data.items.length, 1);
  const at = new Date(AFTER + 3 * 3600_000).toISOString();
  assert.equal((await call("POST", "/api/admin/recipes/release", { batch: "봄", at }, admin)).data.scheduled, 1);
  assert.equal((await call("POST", "/api/admin/recipes/release", { batch: "봄", at: "2020-01-01T00:00:00Z" }, admin)).status, 400);
  {
    await cron(AFTER + 3600_000);
    assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=49")), []);
    await cron(AFTER + 4 * 3600_000);
    assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=49")), ["벚꽃 달력"]);
  }
  // A bad setting is refused, not put up.
  assert.equal((await call("POST", "/api/admin/recipes", { recipe: recipe("틀린 판"), minApp: "1.36" }, admin)).status, 400);
});

test("[MKS-02] the 출시 작품 builder checks each work as the server will, counts its 장터 구간, and holds it for 1.36 apps", async () => {
  const { build, sectionKey, MIN_APP } = await import("../tools/market-seed.mjs");
  const good = recipe("오로라 나이트");
  const memo = { ...recipe("인수인계 보드"), pieces: [{ kind: "memoLook", layout: "grid2" }] };
  const out = await build([{ file: "a.json", recipe: good }, { file: "a.json", recipe: memo, showcase: true },
    { file: "b.json", recipe: recipe("오로라 나이트") }, { file: "b.json", recipe: { ...good, title: "" } }]);
  assert.equal(out.entries.length, 2);
  assert.equal(out.refused.length, 2);
  assert.deepEqual(out.entries.map((e) => [e.hold, e.replace, e.minApp, e.showcase ?? false]), [[true, true, MIN_APP, false], [true, true, MIN_APP, true]]);
  assert.equal(out.count["tab:calendar"], 1);
  assert.equal(out.count["tab:memo"], 1);
  assert.ok(out.short.includes("theme 0/68"));
  assert.ok(!out.short.some((s) => s.startsWith("tab:app")));
  assert.equal(sectionKey(JSON.stringify(memo)), "tab:memo");
});

test("[MKS-03] 출시 대기 lists a whole batch past 200 (267 in 1.36) and its flags, within D1's 100 bound values", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  for (let i = 0; i < 230; i++) {
    const r = await call("POST", "/api/admin/recipes", { recipe: recipe(`출시 작품 ${i + 1}번`), hold: true, batch: "1.36 출시 작품", minApp: 49 }, admin);
    assert.equal(r.status, 201, `work ${i + 1}`);
  }
  const waiting = await call("GET", "/api/admin/recipes?tab=waiting", undefined, admin);
  assert.equal(waiting.status, 200);
  assert.equal(waiting.data.items.length, 230);
  assert.equal(new Set(titles(waiting)).size, 230);
  // Released in one go; the other lists keep their 200.
  const pub = await call("POST", "/api/admin/recipes/release", { batch: "1.36 출시 작품" }, admin);
  assert.equal(pub.data.released, 230);
  assert.equal(pub.status, 200, JSON.stringify(pub.data));
  assert.equal((await call("GET", "/api/admin/recipes?tab=official", undefined, admin)).data.items.length, 200);
});
