// [INVITE-02][INVITE-03][INVITE-04][INVITE-05][INVITE-06] 친구 초대: the code and its link, the friend's report and second
// day, a month for the inviter (waiting before the launch), and what stops a phone counting twice: `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";
import worker, { inviteCodeOf, storeLink, prune } from "../src/index.js";

const DAY = 24 * 60 * 60 * 1000;
const phone = (n) => ({ "x-device": device(n), ...from(n) });
/** The phone mark as the app sends it: SHA-256 of a made-up Android ID. */
const mark = (n) => createHash("sha256").update(`bamnat-invite|android-${n}`).digest("hex");

/** Inviter 1 makes a code; returns it. */
async function code(call, n = 1, phoneN = n) {
  const r = await call("POST", "/api/invites/code", { phone: mark(phoneN) }, phone(n));
  assert.equal(r.status, 201, r.text);
  return r.data.code;
}

const use = (call, n, c, { source = "referrer", phoneN = n } = {}) =>
  call("POST", "/api/invites/use", { code: c, source, phone: mark(phoneN) }, phone(n));
const seen = (call, n) => call("POST", "/api/invites/seen", {}, phone(n));

test("a code is eight plain letters, typed any way, and its link goes to Play with the referrer", async () => {
  assert.equal(inviteCodeOf("abcd-2345"), "ABCD2345");
  assert.equal(inviteCodeOf("ABCD 234"), null);
  // 0, O, 1 and I are never in a code, so a code read out loud is not mistaken.
  assert.equal(inviteCodeOf("ABCD0O1I"), null);
  assert.equal(storeLink("ABCD2345"),
    "https://play.google.com/store/apps/details?id=com.bamnat.app&referrer=utm_source%3Dbamnat_invite%26utm_content%3DABCD2345");

  const { call } = server();
  const page = await call("GET", "/i/ABCD2345");
  assert.equal(page.status, 200);
  assert.match(page.text, /ABCD-2345/);
  assert.ok(page.text.includes(storeLink("ABCD2345")));
  assert.match(page.text, /아무것도 저장하지 않아요/);
  assert.equal((await call("GET", "/i/nope")).status, 404);
});

test("one code per install: asked again, the same code and link", async () => {
  const { call } = server();
  assert.equal((await call("POST", "/api/invites/code", {}, phone(1))).data.error, "phone");
  assert.equal((await call("POST", "/api/invites/code", { phone: mark(1) })).data.error, "device");
  const c = await code(call);
  assert.match(c, /^[A-Z2-9]{8}$/);
  const again = await call("POST", "/api/invites/code", { phone: mark(1) }, phone(1));
  assert.equal(again.status, 200);
  assert.equal(again.data.code, c);
  assert.equal(again.data.link, `${ORIGIN}/i/${c}`);
  const view = (await call("GET", "/api/invites", undefined, phone(1))).data;
  assert.deepEqual(view, { code: c, link: `${ORIGIN}/i/${c}`, friends: 0, months: 0, waiting: 0, capMonths: 12, capReached: false, used: null });
  // A phone without a code sees none.
  assert.equal((await call("GET", "/api/invites", undefined, phone(9))).data.code, null);
});

test("a friend who comes back another day gives the inviter a month, waiting for the launch", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const c = await code(call);
  assert.equal((await use(call, 2, "WRNGXYZ2")).status, 404);
  const first = await use(call, 2, c.toLowerCase().replace(/(.{4})/, "$1-"));
  assert.deepEqual([first.status, first.data.status], [201, "waiting"]);
  // Reporting again changes nothing.
  assert.equal((await use(call, 2, c)).data.status, "waiting");
  assert.equal((await call("GET", "/api/invites", undefined, phone(1))).data.waiting, 1);
  assert.equal((await call("GET", "/api/invites", undefined, phone(2))).data.used, "waiting");

  // The same Korean day, even 23 hours on by UTC from midnight, is not a second day.
  assert.equal((await seen(call, 2)).data.status, "waiting");
  t.mock.timers.tick(12 * 60 * 60 * 1000); // 2026-10-25 15:00Z = 10-26 00:00 KST
  assert.equal((await seen(call, 2)).data.status, "credited");
  assert.equal((await seen(call, 2)).data.status, "credited");

  const view = (await call("GET", "/api/invites", undefined, phone(1))).data;
  assert.equal(view.friends, 1);
  assert.equal(view.months, 1);
  assert.equal(view.waiting, 0);
  // Before the launch the month waits, as other reward passes do, and shows as an invite.
  const passes = (await call("GET", "/api/passes", undefined, phone(1))).data;
  assert.equal(passes.until, null);
  assert.equal(passes.waitingMonths, 1);
  assert.deepEqual(passes.items.map((i) => [i.months, i.why]), [[1, "invite"]]);
  // The friend gets nothing.
  assert.equal((await call("GET", "/api/passes", undefined, phone(2))).data.items.length, 0);

  // After the launch it runs a month from the launch day.
  const owner = await signIn(call);
  await call("POST", "/api/admin/launch", { billingLive: true }, owner);
  const live = (await call("GET", "/api/passes", undefined, phone(1))).data;
  assert.equal(live.until, "2026-11-25T15:00:00.000Z");
  assert.equal(live.waitingMonths, 0);
});

test("the second day must come within 30 days, by the server's clock", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call, env } = server();
  const c = await code(call);
  await use(call, 2, c);
  t.mock.timers.tick(31 * DAY);
  assert.equal((await seen(call, 2)).data.status, "expired");
  assert.equal((await call("GET", "/api/invites", undefined, phone(1))).data.months, 0);
  // The hourly clean-up forgets it after that.
  await prune(env, Date.now());
  assert.equal((await seen(call, 2)).status, 404);
});

test("the same phone counts once, however often the app is installed again", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const c = await code(call);
  const other = await code(call, 5);
  await use(call, 2, c);
  t.mock.timers.tick(DAY);
  assert.equal((await seen(call, 2)).data.status, "credited");
  // Installed again: a new install id (3) on the same phone (mark 2), with the same or another inviter's code.
  const again = await use(call, 3, c, { phoneN: 2 });
  assert.deepEqual([again.data.status, again.data.reason], ["refused", "phone"]);
  assert.equal((await use(call, 4, other, { phoneN: 2, source: "typed" })).data.reason, "phone");
  assert.equal((await call("GET", "/api/invites", undefined, phone(1))).data.friends, 1);
  assert.equal((await call("GET", "/api/invites", undefined, phone(5))).data.friends, 0);
});

test("inviting oneself does not count: the same install, or the same phone installed again", async () => {
  const { call } = server();
  const c = await code(call);
  const same = await use(call, 1, c);
  assert.deepEqual([same.data.status, same.data.reason], ["refused", "self"]);
  // The inviter's phone (mark 1) with the app installed again under a new install id.
  const c2 = await code(call, 6);
  const again = await use(call, 7, c2, { phoneN: 6 });
  assert.equal(again.data.reason, "self");
  assert.equal((await seen(call, 7)).data.status, "refused");
});

test("an inviter gets at most 12 months in 365 days; more friends still count", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const c = await code(call);
  for (let n = 10; n < 23; n++) await use(call, n, c);
  t.mock.timers.tick(DAY);
  const got = [];
  for (let n = 10; n < 23; n++) got.push((await seen(call, n)).data.status);
  assert.deepEqual(got, [...Array(12).fill("credited"), "capped"]);
  const view = (await call("GET", "/api/invites", undefined, phone(1))).data;
  assert.deepEqual([view.friends, view.months, view.capReached], [13, 12, true]);
  assert.equal((await call("GET", "/api/passes", undefined, phone(1))).data.waitingMonths, 12);
});

test("invites do not use up the cap of rewards for company rules", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call, DB } = server();
  const c = await code(call);
  for (let n = 10; n < 14; n++) await use(call, n, c);
  t.mock.timers.tick(DAY);
  for (let n = 10; n < 14; n++) await seen(call, n);
  // Four invite months in 90 days; the rule reward cap (3 in 90 days) looks only at rule rewards.
  const sum = DB.db.prepare("SELECT COALESCE(SUM(months), 0) AS m FROM rewards WHERE device = (SELECT device FROM invite_codes) AND rule_id != 'invite'").get();
  assert.equal(sum.m, 0);
});

test("a friend's report is held to a few tries an hour per network", async () => {
  const { call } = server();
  await code(call);
  const codes = [];
  for (let i = 0; i < 11; i++) codes.push((await call("POST", "/api/invites/use", { code: "ABCDEFGH", source: "typed", phone: mark(30 + i) },
    { "x-device": device(30 + i), ...from(77) })).status);
  assert.deepEqual(codes.slice(0, 10), Array(10).fill(404));
  assert.equal(codes[10], 429);
  assert.equal((await call("POST", "/api/invites/use", { code: "ABCDEFGH", source: "link", phone: mark(2) }, phone(2))).data.error, "source");
  assert.equal((await call("POST", "/api/invites/use", { code: "ABCDEFGH", source: "typed", phone: "x" }, phone(3))).data.error, "phone");
});

test("a recovery code moves the invite code too; 모두 지우기 keeps only a counted phone's mark", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call, DB, env } = server();
  const c = await code(call);
  await use(call, 2, c);
  t.mock.timers.tick(DAY);
  await seen(call, 2);
  const rc = (await call("POST", "/api/passes/code", {}, phone(1))).data.code;
  await call("POST", "/api/passes/claim", { code: rc }, phone(8));
  const moved = (await call("GET", "/api/invites", undefined, phone(8))).data;
  assert.deepEqual([moved.code, moved.friends, moved.months], [c, 1, 1]);

  // The friend wipes what it sent: its install is cut loose, the phone mark stays for the year.
  assert.equal((await call("DELETE", "/api/mine", undefined, phone(2))).status, 200);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM invite_uses WHERE invitee LIKE 'gone%'").get().n, 1);
  assert.equal((await use(call, 3, c, { phoneN: 2 })).data.reason, "phone");
  // The inviter wipes: its code goes, and so does the link.
  await call("DELETE", "/api/mine", undefined, phone(8));
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM invite_codes").get().n, 0);
  assert.equal((await use(call, 4, c)).status, 404);
  // A year on, the counted phone's mark is gone too.
  t.mock.timers.tick(366 * DAY);
  await prune(env, Date.now());
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM invite_uses").get().n, 0);
});

test("the developer app's 보상 screen names invite rewards and sums up invites", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const c = await code(call);
  await use(call, 2, c);
  await use(call, 3, c);
  await use(call, 1, c);
  t.mock.timers.tick(DAY);
  await seen(call, 2);
  const owner = await signIn(call);
  const r = (await call("GET", "/api/admin/rewards?status=paid", undefined, owner)).data;
  assert.equal(r.rewards.length, 1);
  assert.deepEqual([r.rewards[0].company.name, r.rewards[0].invite, r.rewards[0].months, r.rewards[0].memo], ["친구 초대", true, 1, "친구 초대"]);
  assert.equal(r.invites.codes, 1);
  assert.equal(r.invites.credited, 1);
  assert.equal(r.invites.waiting, 1);
  assert.deepEqual(r.invites.refused, { self: 1 });
  assert.deepEqual(r.invites.top, [{ code: c, friends: 2, credited: 1, waiting: 1 }]);
  // A raw request without a device is refused, as for passes.
  assert.equal((await worker.fetch(new Request(`${ORIGIN}/api/invites`), { DB: null })).status, 503);
});
