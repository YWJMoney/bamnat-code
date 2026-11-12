// 2.0.0 갈래 Q1 [RVQ-11][RVQ-12] 검토 SVC-07·SRV-13·SRV-14·I21a-03: 요금제에 맞춘 하루 몫, 정기 작업 한 호출의 D1 질의 수, 매시 작업을 17분·47분으로
// 나누기, 5분 작업도 단계마다 기록, 광장 글 급증이면 1시간 쉬기. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import worker, { CF_PLANS, CRONS, SECOND_HALF, SURGE, autoRest, budgetState, cfPlan, hourlySteps, plazaSurge } from "../src/index.js";
import { JOB_NAMES } from "../src/ops-watch.js";
import { hourlyAgo } from "../src/incident.js";
import { server, device, from, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-20T03:20:00Z");
const HOUR = 3600e3;

async function tick(env, cron, now = Date.now()) {
  const waits = [];
  await worker.scheduled({ cron, scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
}

test("[RVQ-12] SVC-07 wrangler.toml의 cron이 코드가 아는 세 가지와 같아요", () => {
  const toml = readFileSync(new URL("../wrangler.toml", import.meta.url), "utf8");
  const crons = JSON.parse(toml.match(/^crons = (\[.*\])$/m)[1]);
  assert.deepEqual(crons.sort(), Object.values(CRONS).sort());
});

test("[RVQ-12] SVC-07 매시 작업은 17분 몫과 47분 몫으로 겹치지 않게 나뉘고, 모르는 cron이면 모두", () => {
  const env = {};
  const names = (half) => hourlySteps(env, half).map(([s]) => s);
  const all = names(0);
  assert.deepEqual([...names(1), ...names(2)].sort(), [...all].sort());
  assert.equal(names(1).filter((s) => names(2).includes(s)).length, 0);
  for (const s of SECOND_HALF) assert.ok(all.includes(s), `${s}는 매시 작업에 있어야 해요`);
  // 순서가 서로 기대는 것: 지우기 → 쉬기는 17분, Play 읽기 → 출시 안내는 47분.
  assert.ok(names(1).indexOf("prune") < names(1).indexOf("rest"));
  assert.ok(names(2).indexOf("play") < names(2).indexOf("ship"));
  for (const s of [...all, "held5", "ship5", "surge"]) assert.ok(JOB_NAMES[s], `${s}의 이름`);
});

test("[RVQ-12] SRV-14 5분 작업도 단계마다 기록되고, 실패하면 오늘의 주의에 떠요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  await tick(env, CRONS.five);
  const rows = DB.db.prepare("SELECT step, ok_at, fail_at FROM job_runs WHERE step IN ('held5', 'ship5', 'surge') ORDER BY step").all();
  assert.deepEqual(rows.map((r) => [r.step, Boolean(r.ok_at), r.fail_at]), [["held5", true, null], ["ship5", true, null], ["surge", true, null]]);
  // [RVN-02] 5분 작업의 단계는 '매시 작업이 몇 분 전에 돌았나'에 들어가지 않아요(매시 작업이 멈춘 것을 가리지 않게).
  assert.equal(await hourlyAgo(env, NOW), null);
  // 광장 글 급증 살피기가 실패하면(여기서는 표를 지워서) 그 단계만 실패로 남고 다른 단계는 돌아요.
  DB.db.exec("ALTER TABLE flag_overrides RENAME TO flag_overrides_gone");
  t.mock.timers.tick(5 * 60e3);
  await tick(env, CRONS.five);
  DB.db.exec("ALTER TABLE flag_overrides_gone RENAME TO flag_overrides");
  const surge = DB.db.prepare("SELECT ok_at, fail_at, error FROM job_runs WHERE step = 'surge'").get();
  assert.ok(surge.fail_at > surge.ok_at && surge.error);
  assert.ok(DB.db.prepare("SELECT ok_at FROM job_runs WHERE step = 'held5'").get().ok_at >= NOW + 5 * 60e3);
  const alerts = (await call("GET", "/api/admin/today", undefined, owner)).data.alerts;
  assert.ok(alerts.some((a) => a.key.startsWith("jobs:") && /광장 글 급증 살피기/.test(a.meta)), JSON.stringify(alerts.map((a) => a.key)));
});

test("[RVQ-12] SVC-07 정기 작업 한 호출의 D1 질의 수를 세어 서버 상태에 보이고, 상한의 80%를 넘으면 붉게", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  await tick(env, CRONS.first);
  await tick(env, CRONS.second);
  const q = JSON.parse(DB.db.prepare("SELECT value FROM settings WHERE key = 'job_queries'").get().value);
  assert.ok(q.hour1.n > 10 && q.hour2.n > 5, JSON.stringify(q));
  const st = (await call("GET", "/api/admin/server", undefined, owner)).data.budget;
  assert.equal(st.plan, "Workers Paid");
  assert.equal(st.queries.limit, 1000);
  assert.equal(st.queries.top, Math.max(q.hour1.top, q.hour2.top));
  assert.ok(!(await call("GET", "/api/admin/today", undefined, owner)).data.alerts.some((a) => a.key.startsWith("budget:")));
  // 한 번 크게 튄 호출(가짜로 900번)은 그날 안에 계속 보여요.
  q.hour2 = { ...q.hour2, top: 900 };
  DB.db.prepare("UPDATE settings SET value = ? WHERE key = 'job_queries'").run(JSON.stringify(q));
  const heavy = (await call("GET", "/api/admin/today", undefined, owner)).data.alerts.find((a) => a.key.startsWith("budget:queries"));
  assert.equal(heavy?.tone, "bad");
  assert.match(heavy.title, /900번/);
});

test("[RVQ-12] SVC-07 유료 요금제(기본)는 무료 하루 몫에서 쉬지 않고, 유료 몫의 80%면 알리고 90%면 쉬어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  assert.equal(cfPlan(env).d1WritesDay, CF_PLANS.paid.d1WritesDay);
  assert.equal(cfPlan({ CF_PLAN: "free" }).d1WritesDay, 100000);
  assert.equal(cfPlan({ CF_PLAN: "free", D1_WRITES_PER_DAY: "5000" }).d1WritesDay, 5000);
  const day = new Date(NOW).toISOString().slice(0, 10);
  const meter = (n) => DB.db.prepare("INSERT OR REPLACE INTO counters (key, count, expires) VALUES (?, ?, ?)").run(`meter|${day}`, n, NOW + 72 * HOUR);
  meter(95000);
  assert.deepEqual((await autoRest(env, NOW)).rested, [], "무료 몫(10만)의 95%는 유료에서 아무 일도 아니에요");
  meter(Math.ceil(CF_PLANS.paid.d1WritesDay * 0.85));
  const warn = (await call("GET", "/api/admin/today", undefined, owner)).data.alerts.find((a) => a.key.startsWith("budget:d1"));
  assert.match(warn?.title ?? "", /하루 몫의 85%/);
  assert.deepEqual((await autoRest(env, NOW)).rested, []);
  meter(Math.ceil(CF_PLANS.paid.d1WritesDay * 0.91));
  assert.deepEqual((await autoRest(env, NOW)).rested, ["USAGE_STATS"]);
});

test("[RVQ-24] SVC-07·I22b-03 공유 서버 KV 읽기도 하루 몫으로 봐요: 80%면 오늘 할 일, 90%면 새 링크를 쉬고 다음 날 몫 아래면 다시 켜요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  let stats = { writes: 100, reads: 0 };
  t.mock.method(globalThis, "fetch", async (url) => {
    if (String(url).includes("/api/admin/stats")) return Response.json({ days: [{ day: new Date(Date.now() + 9 * HOUR).toISOString().slice(0, 10), ...stats }] });
    return new Response(null, { status: 204 });
  });
  const { call, env } = server({ SHARE_BASE_URL: "https://share.example", SHARE_ADMIN_TOKEN: "share-admin-token-0123456789" });
  const owner = await signIn(call);
  const plan = cfPlan(env);
  assert.equal(plan.kvReadsDay, CF_PLANS.paid.kvReadsDay);
  // 아직 잰 적 없음: 요금제 줄에는 하루 몫만.
  assert.equal((await budgetState(env, NOW)).kv.writes, undefined);
  stats = { writes: 100, reads: Math.ceil(plan.kvReadsDay * 0.85) };
  assert.deepEqual((await autoRest(env, NOW)).rested, [], "85%는 알리기만");
  const kv = (await budgetState(env, NOW)).kv;
  assert.equal(kv.writes, 100);
  assert.equal(kv.reads, stats.reads);
  assert.ok(kv.share >= 0.85 && kv.share < 0.86, "쓰기와 읽기 중 큰 쪽");
  const warn = (await call("GET", "/api/admin/today", undefined, owner)).data.alerts.find((a) => a.key.startsWith("budget:kv"));
  assert.match(warn?.title ?? "", /공유 서버 KV 읽기가 하루 몫의 85%/);
  stats = { writes: 100, reads: Math.ceil(plan.kvReadsDay * 0.91) };
  assert.deepEqual((await autoRest(env, NOW)).rested, ["SHARE_CREATE"]);
  // 다음 날(한국 날짜) 몫 아래: 다시 켜요.
  const next = NOW + 24 * HOUR;
  t.mock.timers.setTime(next);
  stats = { writes: 10, reads: 10 };
  assert.deepEqual((await autoRest(env, next)).rested, []);
});

test("[RVQ-11] SRV-13 광장 글이 갑자기 몰리면 5분 작업이 광장 글쓰기를 1시간 쉬고, 기한이 지나면 저절로 켜져요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server({ plaza: true });
  const owner = await signIn(call);
  const write = (n, body) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body }, from(n));
  // 쓰기 시도는 사본들이 함께 세요(10번에 한 번 D1).
  for (let i = 0; i < 10; i++) assert.equal((await write(1 + (i % 3), `오늘도 야간이에요 ${i}`)).status, 201);
  const win = Math.floor(NOW / HOUR);
  assert.equal(DB.db.prepare("SELECT count FROM counters WHERE key = ?").get(`sc|plazaw|${win}`).count, 10);
  assert.equal((await plazaSurge(env, NOW)).rested, undefined, "평소 수준");
  DB.db.prepare("UPDATE counters SET count = ? WHERE key = ?").run(SURGE.min + 5, `sc|plazaw|${win}`);
  const r = await plazaSurge(env, NOW);
  assert.equal(r.rested, true);
  assert.equal((await write(4, "급증 중에 쓰기")).status, 503);
  const cfg = (await call("GET", "/api/config")).data;
  assert.ok(cfg.off.includes("PLAZA_WRITE"));
  assert.match(cfg.notices.at(-1).body, /1시간 쉬어요/);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM audit WHERE action = 'auto_rest' AND target = 'PLAZA_WRITE'").get().n, 1);
  assert.equal((await plazaSurge(env, NOW + 60e3)).already, true, "이미 쉬는 중이면 다시 쓰지 않아요");
  // 1시간이 지나면 기한이 끝나 저절로 켜져요.
  t.mock.timers.setTime(NOW + HOUR + 1000);
  assert.equal((await write(4, "다시 써요")).status, 201);
  // 관리자가 손으로 다시 켰으면 6시간은 다시 쉬지 않아요.
  DB.db.prepare("UPDATE counters SET count = ? WHERE key = ?").run(SURGE.min + 5, `sc|plazaw|${win}`);
  t.mock.timers.setTime(NOW + 10 * 60e3);
  await call("POST", "/api/admin/switches", { key: "PLAZA_WRITE", off: false, why: "시험: 사람이 다시 켬" }, owner);
  const hand = DB.db.prepare("SELECT force, by_id FROM flag_overrides WHERE key = 'plaza.write'").get();
  assert.notEqual(hand.by_id, "system");
  assert.equal((await plazaSurge(env, NOW + 15 * 60e3)).hand, true);
});

test("[RVQ-11] SRV-13 평소 값은 시간마다 천천히 따라가서, 원래 바쁜 광장은 그만큼 선이 높아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server({ plaza: true });
  await call("GET", "/api/terms");
  await plazaSurge(env, NOW);
  const win = Math.floor(NOW / HOUR);
  // 지난 몇 시간 내내 시간에 200번씩이었다고 해요.
  let at = NOW;
  for (let h = 1; h <= 30; h++) {
    DB.db.prepare("INSERT OR REPLACE INTO counters (key, count, expires) VALUES (?, 200, ?)").run(`sc|plazaw|${win + h - 1}`, NOW + 48 * HOUR);
    at = NOW + h * HOUR;
    await plazaSurge(env, at);
  }
  const rate = JSON.parse(DB.db.prepare("SELECT value FROM settings WHERE key = 'plaza_rate'").get().value).rate;
  assert.ok(rate >= 190 && rate <= 200, String(rate));
  DB.db.prepare("INSERT OR REPLACE INTO counters (key, count, expires) VALUES (?, ?, ?)").run(`sc|plazaw|${win + 30}`, SURGE.min + 5, NOW + 48 * HOUR);
  const r = await plazaSurge(env, at);
  assert.equal(r.line, SURGE.times * rate);
  assert.equal(r.rested, undefined, "평소 200번인 곳의 605번은 급증이 아니에요(선은 8배)");
});
