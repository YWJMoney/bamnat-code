// 2.0.0 관리자 앱 '오늘'의 거짓 경고 셋(실서버 11-19 점검): 옛 이름으로 남은 5분 작업 줄이 '멈춤'으로 뜨던 것, 저장소 동기화가 줄마다 커밋 번호를
// 다시 적어 '저절로 끝난 일'이 15780건이 되던 것, '기다림'으로 둔 문의가 오늘에서는 기한 넘김으로 남던 것. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { CRONS } from "../src/index.js";
import { RETIRED_STEPS } from "../src/ops-watch.js";
import { server, device, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-19T11:00:00Z");
const HOUR = 3600e3;
const DAY = 24 * HOUR;

async function tick(env, cron, now = Date.now()) {
  const waits = [];
  await worker.scheduled({ cron, scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
}
const ask = (call, n, text, kind = "bug") => call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "1.39.0 (53)" } }).then((r) => r.data.id);

test("옛 이름('five')으로 남은 5분 작업 줄은 멈춤으로 뜨지 않고, 매시 지우기가 치워요; 진짜 멈춘 작업은 그대로 떠요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  await tick(env, CRONS.five);
  // 단계로 나누기 전 판이 남긴 줄: 엿새 전이 마지막, 그 뒤로는 held5·ship5가 돌아요.
  DB.db.prepare("INSERT INTO job_runs (step, ok_at, ms, runs, last_at, ver) VALUES ('five', ?, 181, 58, ?, '2.0.0')").run(NOW - 6 * DAY, NOW - 6 * DAY);
  assert.deepEqual(RETIRED_STEPS, ["five"]);
  const jobsAlert = async () => (await call("GET", "/api/admin/today", undefined, owner)).data.alerts.filter((a) => a.key.startsWith("jobs:"));
  assert.deepEqual(await jobsAlert(), []);
  const board = (await call("GET", "/api/admin/watch/health", undefined, owner)).data;
  assert.ok(board.jobs.length > 3 && !board.jobs.some((j) => j.step === "five"), "서버 상태의 정기 작업 목록에 옛 이름 줄이 없어요");
  // 매시 응답 속도 정리(47분 몫)가 줄을 치워요.
  await tick(env, CRONS.second);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM job_runs WHERE step = 'five'").get().n, 0);
  // 진짜로 멈춘 작업(5시간 전이 마지막)은 여전히 '멈춤'이에요.
  DB.db.prepare("UPDATE job_runs SET ok_at = ?, last_at = ? WHERE step = 'held5'").run(NOW - 5 * HOUR, NOW - 5 * HOUR);
  const bad = await jobsAlert();
  assert.equal(bad.length, 1);
  assert.match(bad[0].meta, /보류 끝난 답 보내기\(5분\)/);
});

test("저장소 동기화: 커밋 번호만 다른 같은 줄은 '바뀜'도 기록도 아니고, 정말 바뀐 줄만 커밋 번호와 함께 남아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  const owner = await signIn(call);
  const sync = (await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data;
  const key = { authorization: `Bearer ${sync.key}`, "x-admin": "1" };
  const send = (sha, items) => call("POST", "/api/admin/dev/sync", { sha, version: "", items }, key).then((r) => r.data);
  const lines = [{ track: "OPSX-01", title: "오늘 화면 점검 줄", status: "doing", size: "fix", route: "server" }, { track: "OPSX-02", title: "다른 줄", status: "doing", size: "fix", route: "server" }];
  const first = await send("a".repeat(12), lines);
  assert.deepEqual([first.made, first.changed], [2, 2]);
  const syncRows = () => env.DB.db.prepare("SELECT COUNT(*) AS n FROM dev_history WHERE action = 'sync'").get().n;
  const before = syncRows();
  // 커밋만 달라진 같은 줄을 열여섯 번 보내도 기록은 늘지 않고 '바뀜'도 0이에요.
  let sum = 0;
  for (let i = 0; i < 16; i++) {
    t.mock.timers.tick(60e3);
    const r = await send(`${String(i).padStart(2, "0")}${"b".repeat(10)}`, lines);
    sum += r.changed;
  }
  assert.equal(sum, 0);
  assert.equal(syncRows(), before);
  // 한 줄이 정말 바뀌면 그 줄만 바뀜이고, 이때는 커밋 번호도 함께 적어요.
  const next = await send("c".repeat(12), [{ ...lines[0], size: "polish" }, lines[1]]);
  assert.equal(next.changed, 1);
  assert.equal(syncRows(), before + 1);
  const last = env.DB.db.prepare("SELECT detail FROM dev_history WHERE action = 'sync' ORDER BY id DESC LIMIT 1").get();
  assert.match(last.detail, /"size"/);
  assert.match(last.detail, /cccccccccccc/);
  // 오늘 화면의 '저절로 끝난 일'에도 커밋 번호만 바뀐 줄은 세지 않아요.
  const night = (await call("GET", "/api/admin/today", undefined, owner)).data.night.lines.find((l) => l.key === "dev");
  assert.ok(night && night.n < 10, `처음 만들 때 6건과 바뀐 1건이면 충분해요: ${JSON.stringify(night)}`);
});

test("'기다림'으로 둔 문의는 오늘에서 기한 넘김으로 뜨지 않고, 사용자가 새로 말하면 다시 세요; 끝낸 문의는 빠져요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  const owner = await signIn(call);
  const id = await ask(call, 1, "[앱이 갑자기 닫힘] 또 닫혔어요");
  t.mock.timers.tick(20 * HOUR); // 문제 문의의 답 목표는 8시간이에요.
  const today = async () => (await call("GET", "/api/admin/today", undefined, owner)).data;
  const mineItem = (d) => d.todo.items.find((x) => x.type === "inquiry" && x.id === id);
  const lateKey = (d) => d.alerts.some((a) => a.key === "late");
  let d = await today();
  assert.ok(mineItem(d) && lateKey(d), "아무 표시 없으면 기한 넘김이에요");
  assert.equal(d.slaLateToday, 1);

  // 장부·할 일함에서 '기다림'으로 두면 그날부터 7일이 기한이에요.
  assert.equal((await call("POST", "/api/admin/work", { keys: [`inquiry:${id}`], state: "waiting", reason: "other" }, owner)).status, 200);
  d = await today();
  assert.ok(mineItem(d), "할 일에는 남아 있어요(답 대기)");
  assert.equal(Date.parse(mineItem(d).due) - Date.now() > 6 * DAY, true);
  assert.equal(lateKey(d), false);
  assert.equal(d.slaLateToday, 0);

  // 둔 뒤에 사용자가 새로 말하면 기다림이 풀려요(새 말은 8시간 안에 봐야 해요).
  t.mock.timers.tick(HOUR);
  assert.equal((await call("POST", `/api/inquiries/${id}/messages`, { text: "방금 또 닫혔어요" }, { "x-device": device(1) })).status, 201);
  d = await today();
  assert.ok(mineItem(d));
  assert.ok(Date.parse(mineItem(d).due) - Date.now() <= 8 * HOUR);
  assert.equal(lateKey(d), false, "새 말은 아직 기한 안이에요");
  t.mock.timers.tick(9 * HOUR);
  assert.equal(lateKey(await today()), true, "기한이 지나면 다시 기한 넘김이에요");

  // '끝'으로 두면 새 움직임이 없는 동안 오늘에서 빠지고, 사용자가 다시 말하면 돌아와요.
  assert.equal((await call("POST", "/api/admin/work", { keys: [`inquiry:${id}`], state: "done", reason: "handled" }, owner)).status, 200);
  assert.equal(mineItem(await today()), undefined);
  t.mock.timers.tick(HOUR);
  await call("POST", `/api/inquiries/${id}/messages`, { text: "아직도 그래요" }, { "x-device": device(1) });
  assert.ok(mineItem(await today()));
  // 할 일함(workList)도 같은 기록을 따라 새 움직임이 있으니 '다시 열림'이에요.
  const work = (await call("GET", "/api/admin/work", undefined, owner)).data;
  const line = work.items.find((x) => x.key === `inquiry:${id}`);
  assert.deepEqual([line?.state, line?.reopened], ["new", 1]);
});
