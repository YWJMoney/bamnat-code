// 2.0.0 [RVN-15] 갈래 N 검토 SVC-85·86·87·88, U22c-03: 관측 신호가 워치·시험판을 새 판으로 보지 않고, 한 번도 돌지 않던 '첫날 머묾'이 돌고,
// 30일 비율이 7일 줄로 부풀지 않고, 표본이 작거나 달마다 도는 숫자로 거짓 신호를 내지 않고, '거짓이었어요'가 그 규칙의 기준을 올려요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn } from "./helpers.mjs";
import { obsFeatureFalls, obsFirstDay, obsKeep, obsParse, obsPhoneRows, obsRules, obsWilson } from "../src/observe.js";

const DAY = 86_400_000;
const days = (end, n) => Array.from({ length: n }, (_, i) => new Date(Date.parse(`${end}T00:00:00Z`) - (n - 1 - i) * DAY).toISOString().slice(0, 10));
const on = (found, kind) => found.find((f) => f.kind === kind)?.on ?? false;

test("SVC-85 '새 판 멈춤'은 나간 휴대폰 판으로 보고, 워치(100000+)와 시험판은 빼요", () => {
  const list = days("2026-11-20", 20);
  const versions = { day: list.at(-1), first: { 54: list[5], 55: list[15], 100054: list[5] },
    rows: [{ code: 100054, n: 50 }, { code: 55, n: 2 }, { code: 54, n: 30 }, { code: 53, n: 10 }] };
  assert.deepEqual(obsPhoneRows(versions.rows, 54).map((r) => r.code), [54, 53]);
  assert.deepEqual(obsPhoneRows(versions.rows).map((r) => r.code), [55, 54, 53]);
  const rule = (shippedCode, rows = versions.rows) => obsRules({ shippedCode, play: { days: [{ day: list.at(-1) }], versions: { ...versions, rows } } }).find((f) => f.kind === "version-stuck");
  // 전에는 워치 100054를 '새 판'으로 보고 50/92 = 54%… 이제는 나간 54가 30/40 = 75%로 멈추지 않았어요.
  const fine = rule(54);
  assert.equal(fine.subject, "54");
  assert.equal(fine.value, 75);
  assert.equal(fine.on, false);
  assert.equal(fine.sample, 40);
  assert.match(fine.caveat, /워치/);
  // 54가 정말 멈췄으면 켜져요.
  const stuck = rule(54, [{ code: 100054, n: 50 }, { code: 54, n: 5 }, { code: 53, n: 40 }]);
  assert.equal(stuck.subject, "54");
  assert.equal(stuck.on, true);
  // 나간 판을 모르면 휴대폰 판 가운데 가장 큰 번호(워치는 여전히 빼요).
  assert.equal(rule(null).subject, "55");
  // 나간 판이 아직 아무 휴대폰에도 없으면 0%(나온 날을 몰라 켜지지는 않아요).
  assert.equal(rule(56).value, 0);
  // LOOP-33 '실제로 나갔는지'의 옮겨 간 줄에도 워치는 없어요.
  const csv = ["Date,Package Name,App Version Code,Daily Device Installs,Daily Device Upgrades,Active Device Installs",
    ...list.flatMap((d) => [`${d},com.bamnat.app,54,1,1,30`, `${d},com.bamnat.app,100054,3,3,50`])].join("\n");
  const moves = obsParse({ versions: [csv] }).versions.moves;
  assert.ok(moves.length > 0);
  assert.ok(moves.every((m) => m[1] === 54), JSON.stringify(moves.slice(0, 3)));
});

test("SVC-86 '첫날 머묾'이 진입·다음 날 줄로 계산되어 규칙이 돌아요", () => {
  const list = days("2026-11-20", 14);
  const rows = list.flatMap((d) => [{ day: d, event: "life_new", phones: 10 }, { day: d, event: "life_d1", phones: 2 }]);
  const d1 = obsFirstDay(rows, list);
  assert.equal(d1.base, 130);
  assert.equal(d1.rate, 20);
  const found = obsRules({ sample: { keep: { d1 } } });
  const k = found.find((f) => f.kind === "keep-d1");
  assert.ok(k?.on, JSON.stringify(found));
  assert.equal(k.sample, 130);
  // 표본이 작으면 아직 견주지 않아요.
  assert.equal(obsRules({ sample: { keep: { d1: { rate: 10, base: 12 } } } }).some((f) => f.kind === "keep-d1"), false);
});

test("SVC-87 '기능과 머묾'의 30일 비율은 30일 창 줄만 더해요", () => {
  const list = days("2026-11-20", 61);
  const use = list.map((d) => ({ day: d, event: "pay_view", phones: 10 }));
  const keep = list.flatMap((d) => [{ day: d, event: "pay_view", win: 7, phones: 6 }, { day: d, event: "pay_view", win: 30, phones: 4 }]);
  const open = list.map((d) => ({ day: d, event: "open", phones: 10 }));
  const r = obsKeep([...use, ...open], [...keep, ...list.map((d) => ({ day: d, event: "open", win: 30, phones: 3 }))], list, null);
  const pay = r.rows.find((x) => x.event === "pay_view");
  assert.equal(pay.rate7, 60);
  assert.equal(pay.rate30, 40, "7일 줄(6)까지 더해 100%가 되던 것");
});

test("SVC-88 표본이 작거나, 연 휴대폰이 함께 줄었거나, 달마다 도는 기능은 거짓 신호를 내지 않아요", () => {
  // 윌슨 구간
  const [lo, hi] = obsWilson(10, 100);
  assert.ok(lo > 0.05 && lo < 0.06 && hi > 0.17 && hi < 0.18, `${lo} ${hi}`);
  assert.equal(obsWilson(0, 0), null);
  // 스토어 전환: 방문 30명에서 9명 → 5명은 신호가 아니에요(전에는 켜졌어요).
  const store = (vis, acqNow, acqBefore) => {
    const list = days("2026-11-20", 28);
    return obsRules({ play: { days: list.map((d, i) => ({ day: d, visitors: vis, acq: i >= 21 ? acqNow : acqBefore })) } }).find((f) => f.kind === "store-conv");
  };
  assert.equal(store(5, 1, 2).on, false);
  assert.equal(store(5, 1, 2).sample, 35);
  assert.equal(store(40, 2, 6).on, true, "방문 280명에서 15% → 5%");
  // 삭제: 설치 기기가 같이 두 배로 늘면 급증이 아니에요.
  const spike = (grow) => {
    const list = days("2026-11-20", 17);
    return obsRules({ play: { days: list.map((d, i) => ({ day: d, uninstalls: i >= 14 ? 8 : 2, base: grow && i >= 14 ? 400 : 100 })) } }).find((f) => f.kind === "uninstall-spike");
  };
  assert.equal(spike(false).on, true);
  assert.equal(spike(true).on, false);
  assert.equal(spike(false).sample, 100);
  // 별점: 리뷰를 읽어 오면 7일 리뷰 10개는 있어야 해요.
  const rating = (week) => obsRules({ play: { days: days("2026-11-20", 7).map((d) => ({ day: d, rating: 2.5 })) }, voice: { reviews: { n: 40, week } } })
    .find((f) => f.kind === "rating-low");
  assert.equal(rating(3).on, false);
  assert.equal(rating(12).on, true);
  // 기능 쓰임: 28일, 연 휴대폰 대비.
  const list = days("2026-11-28", 28);
  const rows = (fn) => list.flatMap((d, i) => fn(i).map(([event, phones]) => ({ day: d, event, phones })));
  // 연 휴대폰도 반이 됐으면(표본이 줄어든 주) 그 기능은 그대로예요.
  assert.deepEqual(obsFeatureFalls(rows((i) => [["open", i >= 21 ? 20 : 40], ["share_made", i >= 21 ? 4 : 8]]), list), []);
  // 달마다 도는 기능: 4주 전 같은 주도 지금과 같이 적었으면 반토막이 아니에요.
  assert.deepEqual(obsFeatureFalls(rows((i) => [["open", 40], ["pay_report", i >= 21 || i < 7 ? 2 : 8]]), list), []);
  // 정말 줄었으면 나와요.
  const fell = obsFeatureFalls(rows((i) => [["open", 40], ["plaza_post", i >= 21 ? 2 : 8]]), list, { plaza_post: "광장 글쓰기" });
  assert.deepEqual(fell.map((f) => [f.event, f.name, f.now, f.before]), [["plaza_post", "광장 글쓰기", 14, 56]]);
});

test("U22c-03 신호마다 표본과 '틀릴 수 있는 까닭'이 있고, '거짓이었어요'는 그 규칙의 기준을 올려요", async () => {
  const miss = (tune, n = 4) => obsRules({ tune, voice: { misses: [{ word: "교대 바꾸기", place: "settings", placeName: "설정 검색", n }] } })
    .find((f) => f.kind === "search-miss");
  assert.equal(miss({}).on, true);
  assert.equal(miss({}).sample, 4);
  assert.ok(miss({}).caveat.length > 0);
  assert.equal(miss({ "search-miss": 1 }).on, false, "기준 3 × 1.5 = 4.5");
  assert.equal(miss({ "search-miss": 1 }, 5).on, true);
  // 모든 규칙이 표본과 까닭을 같이 내요.
  const list = days("2026-11-20", 28);
  const all = obsRules({ shippedCode: 54, play: { days: list.map((d) => ({ day: d, uninstalls: 1, installs: 3, base: 50, visitors: 10, acq: 1, rating: 4 })),
    versions: { day: list.at(-1), first: { 54: list[0] }, rows: [{ code: 54, n: 30 }] } },
  active: { play: list.map((d) => ({ day: d, n: 20 })), config: list.map((d) => ({ day: d, n: 40 })) }, quality: { vitals: { crash: 0.1, anr: 0.1, users: 300 } },
  sample: { keep: { d1: { rate: 40, base: 50 } }, features: [] }, voice: { misses: [{ word: "a", place: "settings", n: 1 }], reviews: { n: 0 } },
  disc: { shown: 60, tryRate: 10, offRate: 1 } });
  for (const f of all) assert.ok(f.caveat, `${f.kind} 까닭 없음`);
  // 경로: 아님 + wrong은 기준을 올리고 기록에 남고, 다시 열면 내려요.
  const { call, DB } = server();
  const owner = await signIn(call);
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").run(JSON.stringify({ list: [
    { key: "search-miss:settings:a:2026-11-20", kind: "search-miss", subject: "settings:a", title: "'a'를 찾았는데 없었어요", why: "", unit: "번", value: 3, base: 3, state: "new", first: 1, last: 1, at: 1 }] }));
  const r = await call("POST", "/api/admin/observe/signals", { key: "search-miss:settings:a:2026-11-20", act: "dismiss", wrong: true, note: "한 사람이 여러 번" }, owner);
  assert.equal(r.status, 200, JSON.stringify(r.data));
  assert.deepEqual(r.data.tune, { kind: "search-miss", step: 1 });
  assert.deepEqual(JSON.parse(DB.db.prepare("SELECT value FROM settings WHERE key = 'obs_tune'").get().value), { "search-miss": 1 });
  assert.match(DB.db.prepare("SELECT detail FROM audit WHERE action = 'obs_signal'").get().detail, /기준 ×1.5/);
  const back = await call("POST", "/api/admin/observe/signals", { key: "search-miss:settings:a:2026-11-20", act: "reopen" }, owner);
  assert.equal(back.data.tune.step, 0);
});

test("SVC-103 판 건강은 나간 판(또는 단계적 출시 중인 판)을 그 앞 판과, 같은 '나간 뒤 N일'끼리 견주고, 시험판은 빼요", async () => {
  const { versionHealth } = await import("../src/release-desk.js");
  const { call, DB } = server();
  await signIn(call);
  const now = Date.now();
  const kst = (ms) => new Date(ms + 9 * 3_600_000).toISOString().slice(0, 10);
  const add = (app, from, n, phones) => {
    for (let i = 0; i < n; i++) DB.db.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)").run(kst(from + i * DAY), app, phones);
  };
  const ask = (app, at, kind = "bug") => DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, 'd', ?, ?, 'open', ?, ?)")
    .run(`q${Math.random()}`, kind, JSON.stringify({ app }), at, at);
  const old = now - 40 * DAY;
  const neu = now - 5 * DAY;
  // 1.39.0은 나간 뒤 5일 동안 하루 40대(200 휴대폰-날)에 문제 신고 1, 2.0.0은 나간 뒤 5일 동안 하루 40대에 문제 신고 6.
  add("1.39.0", old, 40, 40);
  add("2.0.0", neu, 5, 40);
  // 내부 시험판 2.0.1: 몇 대, 신고 없음. 가장 높은 번호라 전에는 이것을 '새 판'으로 보고 견주지 않았어요.
  add("2.0.1", now - 2 * DAY, 2, 3);
  ask("1.39.0", old + DAY);
  for (let i = 0; i < 6; i++) ask("2.0.0", neu + DAY + i);
  DB.db.prepare("INSERT INTO audit (at, action, target, detail) VALUES (?, 'shipped_version', '1.39.0', ''), (?, 'shipped_version', '2.0.0', '')").run(old, neu);
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('shipped_version', ?)").run(JSON.stringify({ v: "2.0.0", at: neu, by: "t" }));
  const h = await versionHealth({ DB }, now, 14);
  assert.equal(h.latest, "2.0.0");
  assert.equal(h.prev, "1.39.0");
  assert.equal(h.window, 5);
  assert.equal(h.worse.length, 2, JSON.stringify(h.worse));
  assert.match(h.worse[0], /문제 신고가 1.39.0보다 많아요\(휴대폰-날 1000당 30 대 5 · 나간 뒤 5일끼리\)/);
  assert.equal(h.worse[1], "문의가 1.39.0보다 많아요(나간 뒤 5일끼리)");
  // 단계적 출시 중(자동 순환이 판을 줘요): 2.0.1을 그 앞 판과 견줘요. 아직 휴대폰-날이 적어 견주지 않아요.
  const rolling = await versionHealth({ DB }, now, 14, null, { focus: "2.0.1" });
  assert.equal(rolling.latest, "2.0.1");
  assert.deepEqual(rolling.worse, []);
});

test("SVC-104 숫자가 없는 번호도 :id, 글이 든 조각은 :text로 묶이고, 주소의 낱말은 그대로예요", async () => {
  await import("../src/index.js");
  const { routeOf } = await import("../src/ops-watch.js");
  assert.equal(routeOf("/api/inquiries/AbcdEfghJk/reply"), "/api/inquiries/:id/reply");
  assert.equal(routeOf("/api/plaza/posts/pQRStuvw"), "/api/plaza/posts/:id");
  assert.equal(routeOf("/api/companies/c123456789ab/setups"), "/api/companies/:id/setups");
  assert.equal(routeOf("/api/search/%EA%B5%90%EB%8C%80"), "/api/search/:text");
  // 낱말은 그대로(소문자만, 8자 넘어도).
  assert.equal(routeOf("/api/admin/reactions/companies"), "/api/admin/reactions/companies");
  assert.equal(routeOf("/api/community/appeal"), "/api/community/appeal");
  assert.equal(routeOf("/api/admin/observe/signals?x=1"), "/api/admin/observe/signals");
});
