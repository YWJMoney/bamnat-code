// 1.36 보안 점검(조사/1.36-보안-점검.md) F 갈래 몫: 사진 한도·첫 공개·신고 가중치.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { firstState, PHOTO_GUARD } from "../src/photo-guard.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400000;
const webp = (n) => Buffer.concat([Buffer.from("RIFF"), Buffer.from([30, 0, 0, 0]), Buffer.from("WEBPVP8 "), Buffer.from([10, 0, 0, 0]),
  Buffer.from([0, 0, 0, 0x9d, 0x01, 0x2a, 64, 0, 64, 0]), Buffer.alloc(16, n % 256), Buffer.from([n >> 8])]).toString("base64");
const at = async (DB, n, daysAgo) => {
  const { deviceHash, deviceId } = await import("../src/index.js");
  await DB.prepare("INSERT OR REPLACE INTO recipe_trust (device, first_at) VALUES (?, ?)").bind(await deviceHash(deviceId(device(n))), AFTER - daysAgo * DAY).run();
};

test("[SEC-07] H2: when the automatic check cannot answer, only a phone the operator already passed goes straight up", () => {
  assert.deepEqual(firstState("unchecked", 2, 0), { state: "pending", why: "first" });
  assert.deepEqual(firstState("unchecked", 1, 0), { state: "pending", why: "first" });
  assert.deepEqual(firstState("unchecked", 2, PHOTO_GUARD.lookFirst), { state: "ok", why: null });
  assert.deepEqual(firstState("pass", 2, 0), { state: "ok", why: null });
  assert.deepEqual(firstState("block", 2, 99), { state: "held", why: "check" });
});

test("[SEC-07] H1: one network sends at most a day's pictures however many installs it makes", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  let sent = 0;
  let last;
  // 12 a day per install, so three installs on one line meet the network's 30 first.
  for (let n = 1; n <= 3; n++) {
    for (let k = 0; k < 12; k++) {
      last = await call("POST", "/api/recipes/images", { device: device(n), webp: webp(n * 100 + k) }, { "cf-connecting-ip": "198.51.100.77" });
      if (last.status !== 201) break;
      sent++;
    }
  }
  assert.equal(sent, PHOTO_GUARD.perNetworkDay);
  assert.equal(last.status, 429);
  assert.equal(last.data.why, "network");
  // Another line still can.
  assert.equal((await call("POST", "/api/recipes/images", { device: device(9), webp: webp(999) }, { "cf-connecting-ip": "203.0.113.9" })).status, 201);
});

test("[SEC-08] M3: reports from new installs or a second one from the same line do not hide a picture", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  // Owner's picture goes up after the operator passes three.
  const mine = [];
  for (let k = 1; k <= 4; k++) mine.push((await call("POST", "/api/recipes/images", { device: device(1), webp: webp(k) }, { "cf-connecting-ip": "203.0.113.1" })).data);
  for (const x of mine.slice(0, 3)) await call("POST", `/api/admin/recipes/images/${x.id}/ok`, {}, admin);
  await call("POST", `/api/admin/recipes/images/${mine[3].id}/ok`, {}, admin);
  const id = mine[3].id;
  assert.equal((await call("GET", `/api/recipes/images/${id}`)).status, 200);
  const flag = (n, ip) => call("POST", `/api/recipes/images/${id}/flag`, { device: device(n), reason: "nude" }, { "cf-connecting-ip": ip });
  // Five fresh installs (새싹) report: kept for the operator, the picture stays.
  for (let n = 10; n < 15; n++) assert.equal((await flag(n, `198.51.100.${n}`)).data.held, false);
  assert.equal((await call("GET", `/api/recipes/images/${id}`)).status, 200);
  // Two 이웃 on one line: the second does not count.
  await at(DB, 20, 4);
  await at(DB, 21, 4);
  await flag(20, "198.51.100.55");
  assert.equal((await flag(21, "198.51.100.55")).data.held, false);
  assert.equal((await call("GET", `/api/recipes/images/${id}`)).status, 200);
  // A third on another line does.
  await at(DB, 22, 4);
  assert.equal((await flag(22, "203.0.113.22")).data.held, true);
  assert.equal((await call("GET", `/api/recipes/images/${id}`)).status, 404);
  // The operator still sees every report with its reason.
  const list = (await call("GET", "/api/admin/recipes/images", undefined, admin)).data;
  assert.ok(list.items.find((x) => x.id === id).reasons.nude >= 7);
});
