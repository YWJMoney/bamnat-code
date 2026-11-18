// 2.0.0 갈래 Q1 [RVQ-10][RVQ-11] 검토 SRV-12·SRV-13·SRV-11·T21-03·I21c-03: 인증도 한도도 없던 무거운 길에 한도, Worker 메모리에만 세던
// 한도를 사본들이 함께, 깐 지 3일 넘은 설치에도 사람이 닿지 않는 안전 천장(무결성을 통과한 설치는 빼고), 마을 찾기는 내 유형 광장 먼저.
// 모두 지어낸 글이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { CEILING, deviceHash, networkCheck, network, sharedCount } from "../src/index.js";
import { d1 } from "./d1.mjs";
import { server, device, from, ORIGIN } from "./helpers.mjs";

const NOW = Date.parse("2026-11-20T03:00:00Z");
const DAY = 24 * 3600e3;

function roomWriter(call) {
  return (n, room, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: room, setup: "room", body, ...extra }, from(n));
}

test("[RVQ-10] T21-03 마을 찾기: mine=내 유형 광장이면 그 광장 글이 먼저(표시 mine), 옛 앱은 지금처럼 최근 순", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const write = roomWriter(call);
  assert.equal((await write(1, "style-day", "야간 없는 일근도 잠이 부족해요")).status, 201);
  t.mock.timers.tick(60_000);
  assert.equal((await write(2, "style-shift", "야간 끝나고 잠드는 법")).status, 201);
  t.mock.timers.tick(60_000);
  assert.equal((await write(3, "style-shift", "야간 전에 낮잠")).status, 201);
  const find = (extra = "") => call("GET", `/api/village/search?q=${encodeURIComponent("야간")}${extra}`, undefined, from(9));
  const old = (await find()).data.posts;
  assert.deepEqual(old.map((p) => p.room), ["style-shift", "style-shift", "style-day"], "옛 앱: 최근 순 그대로");
  assert.ok(old.every((p) => !("mine" in p)), "옛 앱이 모르는 칸은 보내지 않아요");
  const day = (await find("&mine=style-day")).data.posts;
  assert.deepEqual(day.map((p) => [p.room, p.mine]), [["style-day", true], ["style-shift", false], ["style-shift", false]]);
  // 모르는 값은 없는 것처럼.
  assert.ok((await find("&mine=company-1")).data.posts.every((p) => !("mine" in p)));
});

test("[RVQ-10] SRV-12 (가) 마을 찾기는 같은 말을 1분 동안 다시 훑지 않고, 공개 읽기 한도(네트워크마다 시간에 3,000번)에 들어가요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const write = roomWriter(call);
  await write(1, "style-shift", "주간 근무 끝나고 운동해요");
  const find = () => call("GET", `/api/village/search?q=${encodeURIComponent("주간 근무")}`, undefined, from(9));
  assert.equal((await find()).data.posts.length, 1);
  await write(2, "style-shift", "주간 근무 다음 날 쉬어요");
  assert.equal((await find()).data.posts.length, 1, "1분 안에는 메모리에서");
  t.mock.timers.tick(61_000);
  assert.equal((await find()).data.posts.length, 2, "1분이 지나면 다시 훑어요");
  let last;
  for (let i = 0; i < 3000; i++) last = await find();
  assert.equal(last.status, 429);
  assert.equal(last.data.why, "network");
  // 다른 네트워크는 그대로예요.
  assert.equal((await call("GET", `/api/village/search?q=${encodeURIComponent("주간 근무")}`, undefined, from(10))).status, 200);
});

test("[RVQ-10] SRV-12 (마) 사본 여럿이 함께 세요: 한 사본의 오차는 [step]보다 작아요", async () => {
  const other = await import("../src/index.js?isolate=q1-shared");
  const env = { DB: d1() };
  await env.DB.prepare("CREATE TABLE counters (key TEXT PRIMARY KEY, count INTEGER NOT NULL, expires INTEGER NOT NULL)").run();
  let a;
  let b;
  for (let i = 0; i < 5; i++) a = await sharedCount(env, "q1|x", DAY, NOW, 5);
  assert.equal(a, 5);
  for (let i = 0; i < 5; i++) b = await other.sharedCount(env, "q1|x", DAY, NOW, 5);
  assert.equal(b, 10, "두 번째 사본은 첫 사본이 더한 것을 받아요");
  assert.equal(await sharedCount(env, "q1|x", DAY, NOW, 5), 6, "첫 사본은 다음에 더할 때까지 자기 것만 더 알아요(오차 < step)");
  // step 1이면 언제나 정확해요(받기 하루 12번처럼).
  for (let i = 0; i < 3; i++) await sharedCount(env, "q1|one", DAY, NOW, 1);
  assert.equal(await other.sharedCount(env, "q1|one", DAY, NOW, 1), 4);
  // 창이 바뀌면 새로.
  assert.equal(await sharedCount(env, "q1|one", DAY, NOW + DAY, 1), 1);
});

test("[RVQ-10] SRV-12 (다) 패스키 로그인 시작은 네트워크마다 시간에 30번", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  const pub = { "x-admin": "1", origin: ORIGIN };
  for (let i = 0; i < 30; i++) assert.equal((await call("POST", "/api/admin/passkey/start", {}, pub)).status, 200);
  const hit = await call("POST", "/api/admin/passkey/start", {}, pub);
  assert.equal(hit.status, 429);
  assert.equal(hit.data.why, "network");
  t.mock.timers.tick(3600e3);
  assert.equal((await call("POST", "/api/admin/passkey/start", {}, pub)).status, 200);
});

test("[RVQ-10] SRV-12 (라) 알림 토큰 지우기도 설치마다 시간에 20번", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server({ plaza: true });
  for (let i = 0; i < 20; i++) assert.equal((await call("DELETE", "/api/push/phone", { device: device(1) }, from(1))).status, 200);
  assert.equal((await call("DELETE", "/api/push/phone", { device: device(1) }, from(1))).status, 429);
  assert.equal((await call("POST", "/api/push/phone", { device: device(1), token: "x".repeat(40) }, from(1))).status, 429, "같은 몫");
  assert.equal((await call("DELETE", "/api/push/phone", { device: device(2) }, from(2))).status, 200);
});

test("[RVQ-11] SRV-13 깐 지 3일 넘은 설치도 대화 글은 시간에 120번이 천장, 무결성을 통과한 설치는 천장 없이", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true, INTEGRITY_MODE: "watch" });
  const write = roomWriter(call);
  for (const n of [1, 2]) assert.equal((await write(n, "style-shift", `처음 인사해요 ${n}`)).status, 201);
  t.mock.timers.tick(3 * DAY + 1000);
  // 2번은 Play Integrity를 통과했어요(앱이 일주일에 한 번 보내는 판정).
  await DB.prepare("INSERT INTO integrity (device, ok, why, at) VALUES (?, 1, '', ?)").bind(await deviceHash(device(2)), Date.now()).run();
  for (let i = 0; i < CEILING.talk.deviceHour; i++) {
    const r = await write(1, "style-shift", `오늘 근무 이야기 ${i}번째 줄이에요`);
    assert.equal(r.status, 201, `${i}: ${r.text}`);
  }
  const over = await write(1, "style-shift", "한 시간에 너무 많이 썼어요");
  assert.equal(over.status, 429);
  assert.equal(over.data.why, "device");
  for (let i = 0; i < CEILING.talk.deviceHour + 5; i++) assert.equal((await write(2, "style-shift", `증명된 휴대폰 ${i}번째 줄`)).status, 201);
  t.mock.timers.tick(3600e3);
  assert.equal((await write(1, "style-shift", "한 시간 지나면 다시 써요")).status, 201);
});

test("[RVQ-11] I21c-03 천장 목록: 무결성 확인이 꺼진 동안(기본)은 표를 읽지 않고 모두 천장, 새 휴대폰은 지금 한도도 함께", async () => {
  const DB = d1();
  const req = new Request("https://company.example/", { headers: { "cf-connecting-ip": "198.51.100.7" } });
  const env = { DB, RATE_SALT: "test-salt" };
  // 표가 없어도(확인이 꺼진 서버) 읽지 않으니 오류가 아니에요.
  await DB.prepare("CREATE TABLE IF NOT EXISTS device_seen (device TEXT PRIMARY KEY, first INTEGER, last INTEGER)").run();
  await DB.prepare("CREATE TABLE IF NOT EXISTS write_limits (device TEXT, until INTEGER)").run();
  await DB.prepare("CREATE TABLE IF NOT EXISTS muted (device TEXT, until INTEGER)").run();
  const net = await network(req, env, NOW);
  const dev = await deviceHash(device(1));
  const fresh = await networkCheck(env, dev, net, "report", 400, NOW);
  assert.deepEqual(fresh.map(([why, key, n]) => [why, key.split("|").slice(0, 2).join("|"), n]), [
    ["network", `report|${net.now}`, 100],
    ["device", "ceil|report", CEILING.deviceHour],
    ["device", "ceil|report", CEILING.deviceDay],
    ["network", "ceil|report", CEILING.networkHour],
  ]);
  const aged = await networkCheck(env, dev, net, "post", 400, NOW + 4 * DAY);
  assert.deepEqual(aged.map(([why, , n]) => [why, n]), [["device", 120], ["device", 600], ["network", 600]], "대화 글은 넉넉하게, 지금 한도는 없음");
});
