// 1.39 [ADM3-42] 로그인 잠금 풀기: PC가 잠겨도 휴대폰에 로그인한 최고 관리자가 1시간을 기다리지 않고 풀어요(사용자 11-07 '슈퍼관리자만').
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, from, signIn, ORIGIN, PASSWORD } from "./helpers.mjs";

const APP = { "x-admin": "1", origin: ORIGIN };
const ALL = { review: "edit", inquiries: "edit", dev: "edit", devStatus: "edit", companies: "edit", rewards: "edit", settings: "edit", community: "edit", takedown: "edit", ops: "edit", metrics: "view", shares: "edit", audit: "view", flags: "edit", flagOff: "edit" };

test("the owner sees a locked network and unlocks it; nobody else can", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-07T13:00:00Z") });
  const { call, DB } = server();
  const phone = await signIn(call);
  // Nothing locked yet.
  assert.deepEqual((await call("GET", "/api/admin/login-locks", undefined, phone)).data, { locked: 0, tries: 0, wide: false, until: null });

  // A helper admin with every area still cannot see or lift the lock.
  const inv = await call("POST", "/api/admin/invites", { email: "help@example.com", perms: ALL }, phone);
  const joined = await call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "도우미", password: "helper-pass-2026" }, { ...APP, ...from(40) });
  assert.equal(joined.status, 201, joined.text);
  const id = (await call("GET", "/api/admin/admins", undefined, phone)).data.admins.find((a) => a.email === "help@example.com").id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, phone);
  const res = await call("POST", "/api/admin/login", { email: "help@example.com", password: "helper-pass-2026" }, { ...APP, ...from(40) });
  const helper = { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };

  // The PC (another network) gets it wrong five times and is locked for an hour.
  const pc = { ...APP, ...from(7) };
  for (let i = 0; i < 5; i++) await call("POST", "/api/admin/login", { password: "not-the-password" }, pc);
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD }, pc)).status, 429);

  const seen = (await call("GET", "/api/admin/login-locks", undefined, phone)).data;
  assert.deepEqual(seen, { locked: 1, tries: 0, wide: false, until: "2026-11-07T14:00:00.000Z" });
  // 오늘 › 주의 필요에도 한 줄로 보여요(최고 관리자만).
  const alerts = (await call("GET", "/api/admin/today", undefined, phone)).data.alerts;
  assert.ok(alerts.some((a) => a.title === "로그인 잠긴 곳 1곳" && a.to === "#/account"), JSON.stringify(alerts));
  assert.ok(!((await call("GET", "/api/admin/today", undefined, helper)).data.alerts ?? []).some((a) => /로그인 잠긴/.test(a.title)));

  assert.equal((await call("GET", "/api/admin/login-locks", undefined, helper)).status, 403);
  assert.equal((await call("POST", "/api/admin/login-locks/clear", {}, helper)).status, 403);
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD }, pc)).status, 429, "a helper did not unlock it");

  const done = await call("POST", "/api/admin/login-locks/clear", {}, phone);
  assert.equal(done.status, 200);
  assert.deepEqual(done.data, { ok: true, locked: 1, tries: 0, wide: false, until: "2026-11-07T14:00:00.000Z" });
  assert.deepEqual((await call("GET", "/api/admin/login-locks", undefined, phone)).data, { locked: 0, tries: 0, wide: false, until: null });
  // The PC gets in at once, well before the hour.
  t.mock.timers.tick(60 * 1000);
  assert.equal((await call("POST", "/api/admin/login", { password: PASSWORD }, pc)).status, 200);
  // It is in the activity record, with no address.
  const log = (await call("GET", "/api/admin/audit?action=login_unlock", undefined, phone)).data;
  assert.ok(JSON.stringify(log).includes("잠긴 곳 1"), JSON.stringify(log).slice(0, 400));
  assert.ok(!DB.dump().includes("198.51.100.7"), "no address kept");
});

test("a wrong try that has not locked yet is counted and cleared too", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-07T13:00:00Z") });
  const { call } = server();
  const phone = await signIn(call);
  const pc = { ...APP, ...from(8) };
  for (let i = 0; i < 2; i++) await call("POST", "/api/admin/login", { password: "not-the-password" }, pc);
  assert.deepEqual((await call("GET", "/api/admin/login-locks", undefined, phone)).data, { locked: 0, tries: 1, wide: false, until: null });
  await call("POST", "/api/admin/login-locks/clear", {}, phone);
  // A fresh five tries again, not three.
  const r = await call("POST", "/api/admin/login", { password: "not-the-password" }, pc);
  assert.deepEqual(r.data, { error: "wrong", left: 4 });
});
