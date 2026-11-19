// 2.0.0 [RVD-27] SN-15 (스튜디오 제작 오류 조사): a work with 13 or more pictures never went up — a phone may send 12 a day, and each try sent
// every picture again. Now the same picture from the same phone is the one it has (not counted), a phone whose pictures already passed may send
// 48 a day, and the 429 says how many and when (갈래 M's '남은 사진은 내일 이어서 올라가요'). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { COMMUNITY_LIMITS } from "../src/recipe-community.js";
import { PHOTO_GUARD } from "../src/photo-guard.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400000;
const webp = (n) => Buffer.concat([Buffer.from("RIFF"), Buffer.from([30, 0, 0, 0]), Buffer.from("WEBPVP8 "), Buffer.from([10, 0, 0, 0]),
  Buffer.from([0, 0, 0, 0x9d, 0x01, 0x2a, 64, 0, 64, 0]), Buffer.alloc(16, n % 256), Buffer.from([n >> 8])]).toString("base64");
const known = async (DB, n, daysAgo) => {
  const { deviceHash, deviceId } = await import("../src/index.js");
  await DB.prepare("INSERT OR REPLACE INTO recipe_trust (device, first_at) VALUES (?, ?)").bind(await deviceHash(deviceId(device(n))), AFTER - daysAgo * DAY).run();
};

test("[RVD-27] SN-15 the same picture again is the one the phone has: not counted, so a 13-picture work goes up over two days", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const up = (n, k, ip = "203.0.113.1") => call("POST", "/api/recipes/images", { device: device(n), webp: webp(k) }, { "cf-connecting-ip": ip });
  // A new phone: 12 of the work's 13 today.
  const ids = [];
  for (let k = 1; k <= 12; k++) {
    const r = await up(1, k);
    assert.equal(r.status, 201, `${k} ${JSON.stringify(r.data)}`);
    ids.push(r.data.id);
  }
  const over = await up(1, 13);
  assert.equal(over.status, 429);
  assert.equal(over.data.why, "device");
  assert.deepEqual([over.data.left, over.data.limit], [0, COMMUNITY_LIMITS.imagesPerDay]);
  assert.equal(Date.parse(over.data.retryAt), AFTER + over.data.retryAfter * 1000);
  assert.equal(over.headers.get("retry-after"), String(over.data.retryAfter));
  // 다시 시도 sends all 13 again: the 12 it has come back as they were, at the limit too.
  for (let k = 1; k <= 12; k++) {
    const r = await up(1, k);
    assert.deepEqual([r.status, r.data.id, r.data.again], [200, ids[k - 1], true], String(k));
  }
  assert.equal((await up(1, 13)).status, 429);
  // The next day the last one goes up and the work is whole.
  t.mock.timers.tick(DAY + 1000);
  for (let k = 1; k <= 12; k++) assert.equal((await up(1, k)).data.id, ids[k - 1]);
  assert.equal((await up(1, 13)).status, 201);
  // Another phone's same picture is its own.
  const other = await up(2, 1, "198.51.100.2");
  assert.equal(other.status, 201);
  assert.notEqual(other.data.id, ids[0]);
});

test("[RVD-27] SN-15 a picture the operator hid is not handed back; it goes to the operator again as new", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const first = (await call("POST", "/api/recipes/images", { device: device(1), webp: webp(7) }, { "cf-connecting-ip": "203.0.113.1" })).data;
  await call("POST", `/api/admin/recipes/images/${first.id}/hide`, {}, admin);
  const again = await call("POST", "/api/recipes/images", { device: device(1), webp: webp(7) }, { "cf-connecting-ip": "203.0.113.1" });
  assert.equal(again.status, 201);
  assert.notEqual(again.data.id, first.id);
  assert.equal(again.data.state, "pending");
});

test("[RVD-27] SN-15 a phone past 새싹 whose pictures passed sends a whole work a day (48), not held to its network's 30", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  const ip = { "cf-connecting-ip": "198.51.100.77" };
  await known(DB, 1, 4);
  // Its first three pictures wait for the operator, who passes them.
  for (let k = 1; k <= PHOTO_GUARD.lookFirst; k++) {
    const r = await call("POST", "/api/recipes/images", { device: device(1), webp: webp(k) }, ip);
    assert.equal(r.status, 201);
    await call("POST", `/api/admin/recipes/images/${r.data.id}/ok`, {}, admin);
  }
  t.mock.timers.tick(DAY + 1000);
  let sent = 0;
  let last;
  for (let k = 100; k < 100 + COMMUNITY_LIMITS.imagesPerDaySure + 1; k++) {
    last = await call("POST", "/api/recipes/images", { device: device(1), webp: webp(k) }, ip);
    if (last.status !== 201) break;
    sent++;
  }
  assert.equal(sent, COMMUNITY_LIMITS.imagesPerDaySure);
  assert.deepEqual([last.status, last.data.why, last.data.left, last.data.limit], [429, "device", 0, COMMUNITY_LIMITS.imagesPerDaySure]);
  // A new phone on that line still has the line's day.
  assert.equal((await call("POST", "/api/recipes/images", { device: device(2), webp: webp(900) }, ip)).status, 201);
  // A new phone (새싹) is held to 12 as before.
  await known(DB, 3, 0);
  let fresh = 0;
  for (let k = 1000; k < 1013; k++) if ((await call("POST", "/api/recipes/images", { device: device(3), webp: webp(k) }, { "cf-connecting-ip": "203.0.113.3" })).status === 201) fresh++;
  assert.equal(fresh, COMMUNITY_LIMITS.imagesPerDay);
});
