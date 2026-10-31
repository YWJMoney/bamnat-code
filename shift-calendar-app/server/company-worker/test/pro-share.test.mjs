// 1.36 [PSH-01] 프로 나눔 1인: a buyer gives 프로 to one person by a one-time code, while they keep it: `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device } from "./helpers.mjs";

const phone = (n) => ({ "x-device": device(n) });
const DAY = 24 * 60 * 60 * 1000;

async function hashOf(n) {
  const { deviceHash, deviceId } = await import("../src/index.js");
  return deviceHash(deviceId(device(n)));
}

/** A subscription the server checked with Play, as /api/purchases keeps it. */
async function bought(DB, n, { trial = false, state = "ACTIVE", days = 30, token = `tok-${n}` } = {}) {
  const now = Date.now();
  DB.db.prepare(`INSERT INTO subs (hash, device, token, product, base_plan, state, expiry, trial, auto_renew, created_at, checked_at)
    VALUES (?, ?, ?, 'bamnat_pro', '', ?, ?, ?, 1, ?, ?)`).run(`h-${token}`, await hashOf(n), token, state, now + days * DAY, trial ? 1 : 0, now, now);
}

test("only a bought subscription can give 프로, to one person at a time, while it lasts", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2027-01-10T00:00:00Z") });
  const { call, DB } = server();
  await call("GET", "/api/passes/share", undefined, phone(1)); // makes the tables
  // A trial is not bought: no code.
  await bought(DB, 9, { trial: true });
  assert.equal((await call("POST", "/api/passes/share", {}, phone(9))).status, 400);
  assert.equal((await call("GET", "/api/passes/share", undefined, phone(9))).data.can, false);

  await bought(DB, 1);
  const made = await call("POST", "/api/passes/share", {}, phone(1));
  assert.equal(made.status, 201);
  assert.match(made.data.code, /^[A-Z2-9]{8}$/);
  // Not to oneself.
  assert.equal((await call("POST", "/api/passes/share/claim", { code: made.data.code }, phone(1))).status, 400);
  const got = await call("POST", "/api/passes/share/claim", { code: made.data.code.toLowerCase() }, phone(2));
  assert.equal(got.status, 200);
  assert.ok(got.data.shared.until);
  assert.ok((await call("GET", "/api/passes", undefined, phone(2))).data.shared.until);
  // The code works once.
  assert.equal((await call("POST", "/api/passes/share/claim", { code: made.data.code }, phone(3))).status, 400);
  const view = (await call("GET", "/api/passes/share", undefined, phone(1))).data;
  assert.equal(view.given, true);
  assert.equal(view.code, null);

  // A new code to someone else: the one before is let go.
  const next = (await call("POST", "/api/passes/share", {}, phone(1))).data.code;
  await call("POST", "/api/passes/share/claim", { code: next }, phone(3));
  assert.equal((await call("GET", "/api/passes", undefined, phone(2))).data.shared, null);
  assert.ok((await call("GET", "/api/passes", undefined, phone(3))).data.shared);

  // The buyer's subscription ends: so does the person's 프로.
  DB.db.prepare("UPDATE subs SET state = 'EXPIRED', expiry = ? WHERE device = ?").run(Date.now() - 1, await hashOf(1));
  assert.equal((await call("GET", "/api/passes", undefined, phone(3))).data.shared, null);
});

test("codes run out after a week, and either side can end it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2027-01-10T00:00:00Z") });
  const { call, DB } = server();
  await call("GET", "/api/passes/share", undefined, phone(1));
  await bought(DB, 1, { days: 60 });
  const code = (await call("POST", "/api/passes/share", {}, phone(1))).data.code;
  t.mock.timers.tick(8 * DAY);
  assert.equal((await call("POST", "/api/passes/share/claim", { code }, phone(2))).status, 400);

  const again = (await call("POST", "/api/passes/share", {}, phone(1))).data.code;
  await call("POST", "/api/passes/share/claim", { code: again }, phone(2));
  // The person gives it back.
  await call("DELETE", "/api/passes/share", undefined, phone(2));
  assert.equal((await call("GET", "/api/passes", undefined, phone(2))).data.shared, null);
  // The buyer takes it back.
  const third = (await call("POST", "/api/passes/share", {}, phone(1))).data.code;
  await call("POST", "/api/passes/share/claim", { code: third }, phone(2));
  await call("DELETE", "/api/passes/share", undefined, phone(1));
  assert.equal((await call("GET", "/api/passes", undefined, phone(2))).data.shared, null);
  assert.equal((await call("GET", "/api/passes/share", undefined, phone(1))).data.given, false);
});
