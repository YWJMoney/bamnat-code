// 2.0.0 [RVQ-35] 검토 SVC-18·U22-07·I22c-01: 개선 신호의 '좋아짐'은 처음 기준(base0)으로 돌아왔을 때만이고, 아니면 '새 기준으로 굳음'·
// '자료 없음'으로 나눠요. 개발사항이 달린 신호는 고침이 닿은 뒤에만 판정하고, 굳었으면 저절로 대기열로 되돌아와요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn } from "./helpers.mjs";
import { OBS, OBS_OUTCOMES, obsFollow, obsMerge, obsRecovered, obsRules, observeTick } from "../src/observe.js";
import { kstDay } from "../src/usage.js";

const HOUR = 3_600_000;
const DAY = 24 * HOUR;
const T0 = Date.parse("2026-11-20T03:00:00Z");

/** '정산 열기' 쓰는 휴대폰이 반 넘게 줄어든 신호(그 전 7일 [before], 최근 7일 [now]). */
const fall = (now, before) => ({ kind: "feature-fall", src: "stats", subject: "pay_open", value: now, base: before, better: "up", unit: "대", on: true, tone: "warn",
  title: `'정산 열기' 쓰는 휴대폰이 반 넘게 줄었어요(7일 ${now}, 그 전 ${before})`, why: "최근 판에서 그 기능 가는 길·모양이 바뀌었는지 봐요.", dev: "'정산 열기' 쓰임이 줄어든 이유 찾기", to: "#/metrics" });
const off = (value) => ({ kind: "feature-fall", src: "stats", subject: "pay_open", value, base: null, better: "up", unit: "", on: false, title: "", why: "" });
const one = (list) => list.find((s) => s.kind === "feature-fall");

test("[RVQ-35] SVC-18 처음 기준(base0)을 지키고, 꺼진 뒤 읽은 값으로 돌아옴·새 기준으로 굳음·자료 없음을 나눠요", () => {
  // 처음 켜질 때 그 전 7일 40대. 그 뒤 규칙의 base(그 전 7일)는 줄어든 값으로 바뀌어도 처음 기준은 40이에요.
  let list = obsMerge([], [fall(10, 40)], T0);
  assert.equal(one(list).base0, 40);
  list = obsMerge(list, [fall(9, 22)], T0 + HOUR);
  assert.equal(one(list).base, 22, "보이는 기준은 지금 규칙대로");
  assert.equal(one(list).base0, 40, "처음 기준은 그대로");
  const last = T0 + HOUR;

  // ① 꺼진 뒤 12대: 문제 선(반토막)은 넘었지만 40의 90%(36)에는 못 미쳐요 → 새 기준으로 굳음.
  let a = obsMerge(list, [off(12)], last + HOUR);
  a = obsMerge(a, [off(12)], last + OBS.clearMs);
  assert.equal(one(a).outcome, "settled");
  assert.ok(one(a).cleared);
  assert.equal(one(a).after, 12);
  // ② 꺼진 뒤 37대(36 이상) → 돌아옴.
  let b = obsMerge(list, [off(30)], last + HOUR);
  b = obsMerge(b, [off(37)], last + OBS.clearMs);
  assert.equal(one(b).outcome, "back");
  // ③ 꺼진 뒤 한 번도 읽지 못했어요(보고서가 끊김) → 자료 없음.
  const c = obsMerge(list, [], last + OBS.clearMs);
  assert.equal(one(c).outcome, "nodata");
  assert.equal(one(c).after, 9, "그때 값은 마지막으로 본 값");
  // ④ 꺼진 직후 한 번 읽고 그 뒤로 못 읽었어요(하루 넘게 묵은 값) → 자료 없음.
  let d = obsMerge(list, [off(37)], last + HOUR);
  d = obsMerge(d, [], last + OBS.clearMs);
  assert.equal(one(d).outcome, "nodata");
  // 사흘이 안 됐으면 아직 판정하지 않아요.
  assert.equal(one(obsMerge(list, [off(37)], last + OBS.clearMs - HOUR)).cleared, undefined);
  // 판정 이름은 관리자 앱과 같은 낱말이에요.
  assert.deepEqual(OBS_OUTCOMES, { back: "돌아옴", settled: "새 기준으로 굳음", nodata: "자료 없음", old: "옛 판정" });
});

test("[RVQ-35] SVC-18 방향: 줄면 좋은 숫자는 기준보다 10%까지 높아도, 늘면 좋은 숫자는 10%까지 낮아도 돌아온 거예요", () => {
  assert.equal(obsRecovered({ better: "down", base0: 1.5, base: 3, now: 1.6 }), true, "삭제 1.5 → 1.6");
  assert.equal(obsRecovered({ better: "down", base0: 1.5, base: 3, now: 2 }), false, "1.5 → 2는 굳음");
  assert.equal(obsRecovered({ better: "up", base0: 16, now: 14.5 }), true);
  assert.equal(obsRecovered({ better: "up", base0: 16, now: 14 }), false);
  assert.equal(obsRecovered({ better: "up", base0: 0, now: 0 }), true, "기준 0(설치-삭제)은 0 이상이면 돌아옴");
  // 처음 기준이 없는 옛 줄은 그때 기준(base)으로, 둘 다 없거나 값이 없으면 모름(null → 자료 없음).
  assert.equal(obsRecovered({ better: "up", base: 16, now: 15 }), true);
  assert.equal(obsRecovered({ better: "up", base0: null, base: null, now: 15 }), null);
  assert.equal(obsRecovered({ better: "up", base0: 16, now: null }), null);
});

test("[RVQ-35] I22c-01 개발사항이 달린 신호는 고침이 닿은 뒤에만 판정하고, 굳었으면 저절로 대기열로 돌아와요", () => {
  let list = obsMerge([], [fall(10, 40)], T0);
  Object.assign(one(list), { state: "dev", devId: "dAAAAAAAAAAAAAAA" });
  // 고침이 아직 닿지 않았으면(개발 중·다음 판 기다림) 숫자가 내려와도 판정하지 않아요.
  let wait = new Map([["dAAAAAAAAAAAAAAA", null]]);
  list = obsMerge(list, [off(12)], T0 + HOUR, { reach: wait });
  list = obsMerge(list, [off(12)], T0 + 5 * DAY, { reach: wait });
  assert.equal(one(list).cleared, undefined, "개발 중에는 열린 채로");
  assert.equal(one(list).state, "dev");
  // 닿은 때(T0+5일)부터 사흘이 지나야 판정해요.
  const reached = new Map([["dAAAAAAAAAAAAAAA", T0 + 5 * DAY]]);
  list = obsMerge(list, [off(12)], T0 + 7 * DAY, { reach: reached });
  assert.equal(one(list).state, "dev", "닿은 지 이틀");
  list = obsMerge(list, [off(12)], T0 + 8 * DAY + HOUR, { reach: reached });
  const s = one(list);
  assert.equal(s.cleared, undefined, "굳었으면 닫지 않고");
  assert.deepEqual([s.state, s.stuck, s.devWas, s.devId], ["new", "settled", "dAAAAAAAAAAAAAAA", null], "대기열(새 신호)로 되돌아와요");
  // 되돌아온 신호는 사람이 고를 때까지 다시 판정하지 않아요(사흘이 또 지나도).
  list = obsMerge(list, [off(12)], T0 + 12 * DAY, { reach: reached });
  assert.equal(one(list).stuck, "settled");
  assert.equal(one(list).cleared, undefined);

  // 돌아왔으면 개발사항과 함께 '돌아옴'으로 닫아요.
  let ok = obsMerge([], [fall(10, 40)], T0);
  Object.assign(one(ok), { state: "dev", devId: "dBBBBBBBBBBBBBBB" });
  const r2 = new Map([["dBBBBBBBBBBBBBBB", T0 + DAY]]);
  ok = obsMerge(ok, [off(39)], T0 + 4 * DAY + HOUR, { reach: r2 });
  assert.deepEqual([one(ok).outcome, one(ok).devId, one(ok).after], ["back", "dBBBBBBBBBBBBBBB", 39]);
  // 다시 나타나면 판정을 지우고 처음 기준은 그대로예요.
  ok = obsMerge(ok, [fall(15, 33)], T0 + 6 * DAY, { reach: r2 });
  assert.deepEqual([one(ok).cleared, one(ok).outcome, one(ok).back, one(ok).base0, one(ok).state], [null, null, 1, 40, "dev"]);

  // 반려·지운 개발사항(닿은 때 0)은 숫자대로 바로 판정해요.
  let no = obsMerge([], [fall(10, 40)], T0);
  Object.assign(one(no), { state: "dev", devId: "dCCCCCCCCCCCCCCC" });
  no = obsMerge(no, [off(12)], T0 + OBS.clearMs + HOUR, { reach: new Map([["dCCCCCCCCCCCCCCC", 0]]) });
  assert.equal(one(no).stuck, "settled");
});

test("[RVQ-35] SVC-18 문제일 때만 목록에 오르는 신호(기능 쓰임·찾았는데 없던 말)는 열린 대상의 지금 값을 따로 읽어 넘겨요", () => {
  const stored = [
    { key: "feature-fall:pay_open:2026-11-20", kind: "feature-fall", subject: "pay_open", state: "new", last: T0 },
    { key: "search-miss:settings:교대 바꾸기:2026-11-20", kind: "search-miss", subject: "settings:교대 바꾸기", state: "watch", last: T0 },
    { key: "feature-fall:old:2026-11-01", kind: "feature-fall", subject: "old", state: "new", cleared: T0, last: T0 },
  ];
  const from7 = "2026-11-14";
  const rows = [{ day: "2026-11-10", event: "pay_open", phones: 20 }, { day: "2026-11-15", event: "pay_open", phones: 7 }, { day: "2026-11-16", event: "pay_open", phones: 5 }];
  const follow = obsFollow(stored, rows, from7, []);
  assert.deepEqual(follow, [
    { kind: "feature-fall", subject: "pay_open", value: 12, base: 20 },
    { kind: "search-miss", subject: "settings:교대 바꾸기", value: 0 },
  ], "닫힌 신호는 읽지 않고, 읽은 줄에 없는 말은 0번");
  const found = obsRules({ sample: { features: [] }, voice: { misses: [] }, follow });
  const got = found.filter((f) => f.subject === "pay_open" || f.subject === "settings:교대 바꾸기").map((f) => [f.kind, f.on, f.value, f.better]);
  assert.deepEqual(got, [["feature-fall", false, 12, "up"], ["search-miss", false, 0, "down"]]);
  // 지금도 문제라 규칙이 켜서 올린 대상은 두 번 올리지 않아요.
  const twice = obsRules({ sample: { features: [{ event: "pay_open", name: "정산 열기", now: 3, before: 30 }] }, voice: { misses: [] }, follow });
  assert.deepEqual(twice.filter((f) => f.subject === "pay_open").map((f) => f.on), [true]);
});

test("[RVQ-35] 매시 작업: 개발 중인 신호는 기다리고, 서버로 닿은 뒤 사흘에도 굳어 있으면 대기열로, 사람이 다시 개발사항으로 넘길 수 있어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: T0 });
  const { call, DB, env } = server();
  const owner = await signIn(call);
  const dev = (await call("POST", "/api/admin/dev", { title: "정산 열기 길 다시 보기" }, owner)).data.id;
  const signal = { ...fall(10, 40), key: "feature-fall:pay_open:2026-11-20", state: "dev", devId: dev, base0: 40, now: 10, first: T0, last: T0, read: T0, at: T0 };
  delete signal.on;
  delete signal.src;
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").run(JSON.stringify({ list: [signal] }));
  const stored = () => JSON.parse(DB.db.prepare("SELECT value FROM settings WHERE key = 'obs_signals'").get().value).list.find((s) => s.kind === "feature-fall");
  // 관측 판은 어제까지의 날을 봐요: 그 전 사흘에 하루 4대씩.
  const use = (at, phones) => {
    for (let i = 1; i <= 3; i++) DB.db.prepare("INSERT OR REPLACE INTO usage_events (day, app, event, phones, total, recent) VALUES (?, '2.0.0', 'pay_open', ?, 0, 0)").run(kstDay(at - i * DAY), phones);
  };
  // 개발 중(접수): 나흘 뒤에도 열린 채로, 지금 값은 읽어요.
  use(T0 + 4 * DAY, 4);
  await observeTick(env, T0 + 4 * DAY);
  assert.equal(stored().cleared, undefined);
  assert.equal(stored().now, 12);
  // 서버로 닿았어요(T0+4일). 사흘 뒤에도 12대 → 고쳤는데 그대로.
  DB.db.prepare("UPDATE dev_items SET status = 'done', route = 'server', live_at = ? WHERE id = ?").run(T0 + 4 * DAY, dev);
  use(T0 + 7 * DAY + HOUR, 4);
  await observeTick(env, T0 + 7 * DAY + HOUR);
  const s = stored();
  assert.deepEqual([s.state, s.stuck, s.devWas, s.devId ?? null], ["new", "settled", dev, null]);
  // 사람이 다시 개발사항으로: 새 개발사항이 달리고 되돌아옴 표시는 풀려요.
  t.mock.timers.setTime(T0 + 7 * DAY + 2 * HOUR);
  const me = await signIn(call);
  const again = await call("POST", "/api/admin/observe/signals", { key: s.key, act: "dev" }, me);
  assert.equal(again.status, 200, again.text);
  assert.equal(again.data.signal.stuck, null);
  assert.notEqual(again.data.signal.devId, dev);
  assert.equal(again.data.signal.state, "dev");

  // 판정한 신호를 다시 열면 판정을 지우고 사흘을 새로 세요(다음 매시에 바로 닫히지 않게).
  const done = { ...signal, key: "feature-fall:other:2026-11-20", subject: "other", state: "new", devId: null, cleared: T0, after: 39, outcome: "back" };
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").run(JSON.stringify({ list: [done] }));
  const reopen = await call("POST", "/api/admin/observe/signals", { key: done.key, act: "reopen" }, me);
  assert.equal(reopen.status, 200, reopen.text);
  assert.deepEqual([reopen.data.signal.cleared, reopen.data.signal.outcome, reopen.data.signal.last], [null, null, T0 + 7 * DAY + 2 * HOUR]);
});
