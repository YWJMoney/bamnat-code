// 2.0.0 갈래 N [RVN-01][RVN-02][RVN-03]: 사고 신호가 지금 사용자 수에서 거짓 경보를 내지 않고, 닫은 사고가 같은 신호로 다시 열리지 않아요.
// 검토 SVC-81·82·83·84, U22c-01, I22ca-01.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device } from "./helpers.mjs";
import { incidentSignals, incidentTick, unmuted, hourlyWatch, hourlyAgo, FIVE_STEP, INCIDENT } from "../src/incident.js";
import { recordRequest, flushWatch, watchHour } from "../src/ops-watch.js";

const HOUR = 3600 * 1000;
const T = Date.parse("2026-11-20T03:17:00Z");

async function ready() {
  const s = server();
  await s.call("GET", "/api/terms");
  return s;
}

async function inquiry(DB, n, at, kind = "bug") {
  await DB.prepare("INSERT INTO inquiries (id, device, kind, info, created_at, updated_at, status) VALUES (?, ?, ?, '{}', ?, ?, 'open')")
    .bind(`q${String(n).padStart(21, "0")}`, `h-${device(n % 1000)}`, kind, at, at).run();
}

test("SVC-82: 한 휴대폰이 문제 신고 다섯 건을 보내도 사고가 아니에요, 서로 다른 세 대부터예요", async () => {
  const { DB, env } = await ready();
  for (let i = 0; i < 5; i++) await DB.prepare("INSERT INTO inquiries (id, device, kind, info, created_at, updated_at, status) VALUES (?, 'same-phone', 'bug', '{}', ?, ?, 'open')")
    .bind(`qsame${i}`.padEnd(22, "0"), T - i * 60000, T - i * 60000).run();
  assert.deepEqual((await incidentSignals(env, T)).map((s) => s.key), []);
  assert.equal((await incidentTick(env, T)).open, null);
  for (let i = 1; i <= 2; i++) await inquiry(DB, i, T - 10 * 60000);
  const sig = await incidentSignals(env, T);
  assert.deepEqual(sig.map((s) => s.key), ["bugs"]);
  assert.equal(sig[0].n, 3, "표본은 휴대폰 수");
  assert.equal(sig[0].sure, false, "문제 신고는 의심: 밤에는 울리지 않아요");
});

test("SVC-84: 쉬게 한 기능의 503과 바깥 서버 502·504는 서버 오류로 세지 않고, 지난 한 시간은 끝난 시각 칸 하나예요", async () => {
  const { env, DB } = await ready();
  const prev = T - HOUR;
  for (let i = 0; i < 30; i++) recordRequest(env, "/api/recipes", 503, 10, prev);
  for (let i = 0; i < 30; i++) recordRequest(env, "/api/billing", 502, 10, prev);
  await flushWatch(env, prev, true);
  assert.equal((await DB.prepare("SELECT SUM(err) AS e FROM req_stats").first()).e, 0);
  for (let i = 0; i < 12; i++) recordRequest(env, "/api/board", 500, 10, prev);
  // 지금 시각 칸의 오류는 아직 끝나지 않은 칸이라 이번 판단에 들어가지 않아요.
  for (let i = 0; i < 40; i++) recordRequest(env, "/api/board", 500, 10, T);
  await flushWatch(env, T, true);
  const sig = await incidentSignals(env, T);
  assert.equal(sig[0].key, "errors");
  assert.equal(sig[0].v, 12);
  assert.equal(sig[0].sure, true);
  assert.ok(watchHour(prev) < watchHour(T));
});

test("SVC-81: 하루 사용자가 적으면 Play 비정상 종료 비율은 신호가 아니에요", async () => {
  const { env } = await ready();
  assert.deepEqual((await incidentSignals(env, T, { crash: 0.03, anr: 0.01, users: 40 })).map((s) => s.key), []);
  assert.deepEqual((await incidentSignals(env, T, { crash: 0.03, anr: 0.01, users: 400 })).map((s) => s.key), ["crash", "anr"]);
});

test("SVC-81: 닫은 사고는 같은 신호로 다음 시각에 다시 열리지 않고, 더 나빠지거나 한 번 꺼졌다 켜지면 열려요", async () => {
  const s = await ready();
  const { env, call } = s;
  const vitals = { crash: 0.02, users: 300 };
  const first = await incidentTick(env, T, vitals);
  assert.ok(first.open);
  // 관리자가 닫아요(다시 묻지 않기: 한 번 꺼질 때까지).
  const { incidentRoute } = await import("../src/incident.js");
  const req = new Request("https://x/close", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ note: "1.39.1로 고쳤어요", days: 0 }) });
  const closed = await incidentRoute(req, { ...env, actor: { name: "주인" } }, `/incident/${first.open}/close`, "POST", T + 1000, async () => vitals);
  assert.equal(closed.status, 200);
  assert.deepEqual((await closed.json()).muted, ["crash"]);
  // 한 시간 뒤 같은 28일 값: 조용해요.
  assert.equal((await incidentTick(env, T + HOUR, vitals)).open, null);
  // 1.3배 넘게 나빠지면 다시 열려요.
  const worse = await incidentTick(env, T + 2 * HOUR, { crash: 0.03, users: 300 });
  assert.ok(worse.open);
});

test("SVC-81: 꺼졌다 다시 켜진 신호는 새 일이고, 고른 날 수가 지나면 다시 물어요", () => {
  const mutes = { crash: { v: 0.02, until: null }, bugs: { v: 5, until: T + 3 * 24 * HOUR } };
  // crash가 꺼져 있으면 '다시 묻지 않기'에서 빠져요.
  const a = unmuted([{ key: "bugs", v: 6 }], mutes, T);
  assert.deepEqual(a.live, []);
  assert.deepEqual(Object.keys(a.mutes), ["bugs"]);
  const b = unmuted([{ key: "crash", v: 0.02 }, { key: "bugs", v: 6 }], a.mutes, T);
  assert.deepEqual(b.live.map((s) => s.key), ["crash"], "한 번 꺼졌던 crash는 다시 새 신호");
  const c = unmuted([{ key: "bugs", v: 6 }], b.mutes, T + 4 * 24 * HOUR);
  assert.deepEqual(c.live.map((s) => s.key), ["bugs"], "3일이 지나면 다시 물어요");
  assert.equal(INCIDENT.worse, 1.3);
});

test("SVC-83: 열린 사고에 새 종류의 신호가 붙으면 신호 글을 지금 값으로 고치고, 꺼진 신호는 '잠잠'으로 남겨요", async () => {
  const { env, DB } = await ready();
  const open = (await incidentTick(env, T, { crash: 0.02, users: 300 })).open;
  for (let i = 1; i <= 5; i++) await inquiry(DB, i, T + HOUR - 60000);
  await incidentTick(env, T + HOUR, null);
  const row = await DB.prepare("SELECT signals FROM incidents WHERE id = ?").bind(open).first();
  const sig = JSON.parse(row.signals);
  assert.deepEqual(sig.map((s) => [s.key, s.off]), [["bugs", false], ["crash", true]]);
});

test("SVC-83: 5분 작업이 멈추면 매시 작업이, 매시 작업이 멈추면 5분 작업이 알아요", async () => {
  const { env, DB } = await ready();
  await DB.prepare("INSERT INTO job_runs (step, ok_at, last_at, runs) VALUES (?, ?, ?, 1)").bind(FIVE_STEP, T - 2 * HOUR, T - 2 * HOUR).run();
  await DB.prepare("INSERT INTO job_runs (step, ok_at, last_at, runs) VALUES ('prune', ?, ?, 1)").bind(T - 4 * HOUR, T - 4 * HOUR).run();
  assert.ok((await incidentSignals(env, T)).some((s) => s.key === "stale"));
  assert.equal(await hourlyAgo(env, T), 240);
  assert.deepEqual(await hourlyWatch(env, T), { stale: true, told: true });
  assert.deepEqual(await hourlyWatch(env, T + 600000), { stale: true, told: false }, "같은 멈춤에는 한 번만 알려요");
  // 매시 작업이 다시 돌면 풀리고(80분은 아직 괜찮아요), 다음 멈춤(90분 넘김)에 다시 알려요.
  await DB.prepare("UPDATE job_runs SET last_at = ?, ok_at = ? WHERE step = 'prune'").bind(T + HOUR, T + HOUR).run();
  assert.deepEqual(await hourlyWatch(env, T + HOUR + 80 * 60000), { stale: false });
  assert.deepEqual(await hourlyWatch(env, T + HOUR + 91 * 60000), { stale: true, told: true });
});

test("/api/admin/health?jobs=1은 마지막 매시 작업이 몇 분 전인지 숫자 하나만 내요", async () => {
  const { call, DB } = await ready();
  const r = await call("GET", "/api/admin/health?jobs=1");
  assert.equal(r.status, 200);
  assert.deepEqual(Object.keys(r.data), ["hourlyAgoMin"], "상태와 분만, 판·내부 값 없이");
  await DB.prepare("INSERT INTO job_runs (step, ok_at, last_at, runs) VALUES ('prune', ?, ?, 1)").bind(Date.now() - 3 * HOUR, Date.now() - 3 * HOUR).run();
  const late = await call("GET", "/api/admin/health?jobs=1");
  assert.equal(late.status, 503);
  assert.ok(late.data.hourlyAgoMin >= 179);
});
