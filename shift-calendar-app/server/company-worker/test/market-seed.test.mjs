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

test("[STM-09] 판 정리: 운영자 작품을 제목으로 한 번에 숨기고(dry는 세기만), 사용자 작품·없는 제목은 건드리지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  for (const title of ["옛 알람 하나", "옛 위젯 하나", "남길 작품"]) {
    assert.equal((await call("POST", "/api/admin/recipes", { recipe: recipe(title) }, admin)).status, 201);
  }
  const dry = await call("POST", "/api/admin/recipes/hide-titles", { titles: ["옛 알람 하나", "옛 위젯 하나", "없는 작품"], dry: true }, admin);
  assert.deepEqual([dry.data.hidden, dry.data.found, dry.data.missing], [0, 2, ["없는 작품"]]);
  assert.equal(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=52")).length, 3);
  const out = await call("POST", "/api/admin/recipes/hide-titles", { titles: ["옛 알람 하나", "옛 위젯 하나"], note: "1.38 장터 정리" }, admin);
  assert.equal(out.data.hidden, 2);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=52")), ["남길 작품"]);
  // 1.39: 판 정리로 내린 것은 '가린 것'이 아니라 '판 정리로 내림'에 따로 보여요.
  assert.equal((await call("GET", "/api/admin/recipes?tab=retired", undefined, admin)).data.items.length, 2);
  assert.equal((await call("GET", "/api/admin/recipes?tab=hidden", undefined, admin)).data.items.length, 0);
  assert.equal((await call("POST", "/api/admin/recipes/hide-titles", { titles: [] }, admin)).status, 400);
  assert.equal((await call("POST", "/api/admin/recipes/hide-titles", { titles: ["남길 작품"] })).status >= 400, true);
});

test("1.39 판 정리: 내린 작품은 목록·찾기·추천에서 빠지지만 코드로는 열리고(넣은 분), 지우지 않고, 한 번에 되돌려요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  let admin = await signIn(call);
  const made = {};
  for (const title of ["옛 테마 하나", "옛 테마 둘", "새 테마"]) {
    made[title] = (await call("POST", "/api/admin/recipes", { recipe: recipe(title) }, admin)).data.code;
  }
  await call("POST", `/api/admin/recipes/${made["옛 테마 둘"]}/showcase`, { on: true }, admin);
  // 계획 파일은 제목, 코드도 돼요.
  const out = await call("POST", "/api/admin/recipes/hide-titles", { titles: ["옛 테마 하나"], codes: [made["옛 테마 둘"]], note: "1.39 장터 정리" }, admin);
  assert.deepEqual([out.data.hidden, out.data.missing], [2, []]);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=53")), ["새 테마"]);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=53&q=" + encodeURIComponent("옛 테마"))), []);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=showcase&app=53")), []);
  // 이미 넣은 분: 코드로 열리고 '내려감'이라고, 쓰는 분도 세요.
  const opened = await call("GET", `/api/recipes/${made["옛 테마 하나"]}`);
  assert.equal(opened.status, 200);
  assert.equal(opened.data.retired, true);
  assert.equal((await call("POST", `/api/recipes/${made["옛 테마 하나"]}/applied`, { kind: "kept" })).status, 200);
  // 90일이 지나도 지우지 않아요 (가린 사용자 작품만 지워요).
  const later = AFTER + 120 * 86_400_000;
  t.mock.timers.setTime(later);
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: later }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.allSettled(waits);
  assert.equal((await call("GET", `/api/recipes/${made["옛 테마 하나"]}`)).status, 200);
  // 운영자가 하나를 가리면(문제가 있어서) 넣은 분에게도 닫혀요. (넉 달이 지나 다시 들어가요.)
  admin = await signIn(call);
  assert.equal((await call("POST", `/api/admin/recipes/${made["옛 테마 둘"]}/hide`, { note: "문제" }, admin)).status, 200);
  assert.equal((await call("GET", `/api/recipes/${made["옛 테마 둘"]}`)).status, 404);
  // 되돌리기: 판 정리로 내린 것만 (운영자가 가린 것은 그대로).
  const dry = await call("POST", "/api/admin/recipes/show-titles", { titles: ["옛 테마 하나", "옛 테마 둘"], dry: true }, admin);
  assert.deepEqual([dry.data.shown, dry.data.found, dry.data.missing], [0, 1, ["옛 테마 둘"]]);
  const back = await call("POST", "/api/admin/recipes/show-titles", { titles: ["옛 테마 하나"] }, admin);
  assert.equal(back.data.shown, 1);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=53")).sort(), ["새 테마", "옛 테마 하나"].sort());
  // 1.38처럼 'admin'으로 숨긴 운영자 작품도 다시 보내면 판 정리로 바뀌어 넣은 분이 열 수 있어요.
  const again = await call("POST", "/api/admin/recipes/hide-titles", { titles: ["옛 테마 둘"] }, admin);
  assert.equal(again.data.hidden, 1);
  assert.equal((await call("GET", `/api/recipes/${made["옛 테마 둘"]}`)).status, 200);
});

test("1.39 판 정리 belowApp: 그 판 미만 옛 앱의 장터에는 계속 보이고, 새 앱에서만 빠져요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  for (const title of ["옛 테마 하나", "옛 테마 둘"]) assert.equal((await call("POST", "/api/admin/recipes", { recipe: recipe(title) }, admin)).status, 201);
  const out = await call("POST", "/api/admin/recipes/hide-titles", { titles: ["옛 테마 하나"], belowApp: 53, note: "1.39 장터 정리" }, admin);
  assert.equal(out.data.hidden, 1);
  await call("POST", "/api/admin/recipes/hide-titles", { titles: ["옛 테마 둘"] }, admin);
  // 1.38(52) 앱과 app=을 안 보내는 아주 옛 앱에는 '하나'가 남고, 53 앱에는 둘 다 빠져요. belowApp이 없는 '둘'은 모든 앱에서 빠져요.
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=52")), ["옛 테마 하나"]);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new")), ["옛 테마 하나"]);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=53")), []);
  const listed = (await call("GET", "/api/admin/recipes?tab=retired", undefined, admin)).data.items;
  assert.deepEqual(listed.map((i) => [i.title, i.retiredBelow ?? null]).sort(), [["옛 테마 둘", null], ["옛 테마 하나", 53]]);
  // 다시 보내면 belowApp만 새로 적어요(이제 모든 앱에서 빠짐).
  await call("POST", "/api/admin/recipes/hide-titles", { titles: ["옛 테마 하나"] }, admin);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=52")), []);
  // 되돌리면 모두에게 다시.
  await call("POST", "/api/admin/recipes/show-titles", { titles: ["옛 테마 하나", "옛 테마 둘"] }, admin);
  assert.equal(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=53")).length, 2);
});

test("[MKS-05] 나간 판 저장이 그 판 앱부터 읽는 출시 대기 작품을 함께 공개해요 (예약·다음 판·minApp 없는 것은 그대로)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const admin = await signIn(call);
  const put = (title, extra) => call("POST", "/api/admin/recipes", { recipe: recipe(title), hold: true, ...extra }, admin);
  await put("1.39 정상급 테마", { batch: "1.39 정상급 작품", minApp: 53 });
  await put("1.38 남은 작품", { batch: "옛 묶음", minApp: 52 });
  await put("1.40 작품", { batch: "1.40", minApp: 54 });
  await put("판 없는 작품", { batch: "아무 판" });
  await put("예약한 작품", { batch: "예약", minApp: 53 });
  const at = new Date(AFTER + 30 * 86400_000).toISOString();
  assert.equal((await call("POST", "/api/admin/recipes/release", { batch: "예약", at }, admin)).data.scheduled, 1);
  // 판 번호를 모르면(옛 표에도, Play 트랙에도 없으면) 아무것도 공개하지 않아요.
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.39.0", dry: true }, admin)).data.market, 0);
  // Play 트랙에서 읽은 판 번호(워치 100053은 빼고 53)로 셈해요.
  await env.DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('play_track', ?)").bind(JSON.stringify({ done: "1.39.0", doneCodes: [100053, 53] })).run();
  const dry = (await call("POST", "/api/admin/desk/ship", { version: "1.39.0", dry: true }, admin)).data;
  assert.deepEqual([dry.code, dry.market], [53, 2]);
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=53")), []);
  const shipped = (await call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, admin)).data;
  assert.equal(shipped.market, 2);
  assert.deepEqual(new Set(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=53"))), new Set(["1.39 정상급 테마", "1.38 남은 작품"]));
  // 1.38 앱에는 53 작품이 여전히 안 보여요.
  assert.deepEqual(titles(await call("GET", "/api/recipes?style=SHIFT&sort=new&app=52")), ["1.38 남은 작품"]);
  const left = (await call("GET", "/api/admin/recipes/release", undefined, admin)).data.items.map((b) => b.batch).sort();
  assert.deepEqual(left, ["1.40", "아무 판", "예약"]);
  // 관리자가 판 번호를 직접 주면 그 번호로.
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.40.0", code: 54 }, admin)).data.market, 1);
});
