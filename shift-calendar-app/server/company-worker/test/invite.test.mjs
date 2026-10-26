// [INVITE-02][INVITE-03][INVITE-04][INVITE-05][INVITE-06][INVITE-08] 친구 초대: the code and its link, the friend's report
// and period of use, months for the inviter each time enough friends used the app (waiting before the launch), the
// developer app's rules, and what stops a phone counting twice: `npm test`.
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
const view = async (call, n = 1) => (await call("GET", "/api/invites", undefined, phone(n))).data;

/** The developer app sets the rules; returns what the server keeps. */
async function rules(call, body) {
  const owner = await signIn(call);
  const r = await call("POST", "/api/admin/settings/invite", body, owner);
  assert.equal(r.status, 200, r.text);
  return r.data;
}

/** One friend, one day: most tests are about something else. */
const oneByOne = (call) => rules(call, { friends: 1, days: 1 });

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

test("one code per install: asked again, the same code and link; the rules need no install", async () => {
  const { call } = server();
  // [INVITE-08] Until the developer changes them: 5 friends who use it 7 days give 1 month, with no cap.
  assert.deepEqual((await call("GET", "/api/invites/rules")).data, { need: 5, days: 7, months: 1, capMonths: 0 });
  assert.equal((await call("POST", "/api/invites/code", {}, phone(1))).data.error, "phone");
  assert.equal((await call("POST", "/api/invites/code", { phone: mark(1) })).data.error, "device");
  const c = await code(call);
  assert.match(c, /^[A-Z2-9]{8}$/);
  const again = await call("POST", "/api/invites/code", { phone: mark(1) }, phone(1));
  assert.equal(again.status, 200);
  assert.equal(again.data.code, c);
  assert.equal(again.data.link, `${ORIGIN}/i/${c}`);
  assert.deepEqual(await view(call), { code: c, link: `${ORIGIN}/i/${c}`, need: 5, days: 7, months: 1, capMonths: 0,
    progress: 0, waiting: 0, friends: 0, given: 0, capReached: false, used: null });
  // A phone without a code sees none, and the rules.
  const none = await view(call, 9);
  assert.deepEqual([none.code, none.need], [null, 5]);
});

test("five friends who each use the app seven days give the inviter a month, waiting for the launch", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const c = await code(call);
  assert.equal((await use(call, 2, "WRNGXYZ2")).status, 404);
  const first = await use(call, 2, c.toLowerCase().replace(/(.{4})/, "$1-"));
  assert.deepEqual([first.status, first.data.status, first.data.daysLeft], [201, "waiting", 7]);
  // Reporting again changes nothing.
  assert.equal((await use(call, 2, c)).data.status, "waiting");
  for (const n of [3, 4]) await use(call, n, c);
  assert.equal((await view(call, 2)).used, "waiting");

  // Six Korean days on is not yet the period, by the server's clock.
  t.mock.timers.tick(6 * DAY);
  assert.deepEqual([(await seen(call, 2)).data.status, (await seen(call, 2)).data.daysLeft], ["waiting", 1]);
  // 2026-11-01 03:00Z: seven days after 10-25 KST.
  t.mock.timers.tick(DAY);
  for (const n of [2, 3, 4]) assert.equal((await seen(call, n)).data.status, "qualified");
  for (const n of [5, 6]) await use(call, n, c);
  // "3/5명 · 2명은 사용 기간 채우는 중".
  let v = await view(call);
  assert.deepEqual([v.progress, v.need, v.waiting, v.friends, v.given], [3, 5, 2, 3, 0]);

  t.mock.timers.tick(7 * DAY);
  assert.equal((await seen(call, 5)).data.status, "qualified");
  assert.equal((await seen(call, 6)).data.status, "credited");
  assert.equal((await seen(call, 2)).data.status, "credited");
  v = await view(call);
  assert.deepEqual([v.progress, v.waiting, v.friends, v.given], [0, 0, 5, 1]);

  // Before the launch the month waits, as other reward passes do, and shows as an invite.
  const passes = (await call("GET", "/api/passes", undefined, phone(1))).data;
  assert.equal(passes.until, null);
  assert.equal(passes.waitingMonths, 1);
  assert.deepEqual(passes.items.map((i) => [i.months, i.why]), [[1, "invite"]]);
  // The friends get nothing.
  assert.equal((await call("GET", "/api/passes", undefined, phone(2))).data.items.length, 0);

  // After the launch it runs a month from the launch day.
  const owner = await signIn(call);
  await call("POST", "/api/admin/launch", { billingLive: true }, owner);
  const live = (await call("GET", "/api/passes", undefined, phone(1))).data;
  assert.equal(live.until, "2026-12-08T03:00:00.000Z");
  assert.equal(live.waitingMonths, 0);
});

test("with no cap, every five more friends give another month", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const c = await code(call);
  for (let n = 10; n < 22; n++) await use(call, n, c);
  t.mock.timers.tick(7 * DAY);
  for (let n = 10; n < 22; n++) await seen(call, n);
  const v = await view(call);
  assert.deepEqual([v.friends, v.given, v.progress, v.capReached], [12, 2, 2, false]);
  assert.equal((await call("GET", "/api/passes", undefined, phone(1))).data.waitingMonths, 2);
});

test("the developer app sets the rules, within limits; a lowered number pays at once and a cap holds", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const owner = await signIn(call);
  assert.deepEqual((await call("GET", "/api/admin/settings/invite", undefined, owner)).data, { friends: 5, days: 7, months: 1, capMonths: 0 });
  for (const [body, key] of [[{ friends: 0 }, "friends"], [{ days: 61 }, "days"], [{ months: 0 }, "months"], [{ capMonths: -1 }, "capMonths"], [{ friends: "3" }, "friends"]]) {
    const r = await call("POST", "/api/admin/settings/invite", body, owner);
    assert.deepEqual([r.status, r.data.error], [400, key]);
  }
  // Not from a page elsewhere, and not without signing in.
  assert.equal((await call("POST", "/api/admin/settings/invite", { friends: 3 })).status, 403);

  const c = await code(call);
  for (let n = 10; n < 13; n++) await use(call, n, c);
  t.mock.timers.tick(7 * DAY);
  for (let n = 10; n < 13; n++) await seen(call, n);
  assert.deepEqual([(await view(call)).progress, (await view(call)).given], [3, 0]);
  // Three friends are enough now: the three already counted give their month when it is saved.
  assert.deepEqual(await rules(call, { friends: 3, months: 2 }), { friends: 3, days: 7, months: 2, capMonths: 0 });
  assert.deepEqual([(await view(call)).progress, (await view(call)).given], [0, 2]);
  assert.deepEqual((await call("GET", "/api/invites/rules")).data, { need: 3, days: 7, months: 2, capMonths: 0 });

  // A cap of 3 months in a year: the next group of three is counted without months.
  await rules(call, { capMonths: 3, days: 1 });
  for (let n = 20; n < 23; n++) await use(call, n, c);
  t.mock.timers.tick(DAY);
  const got = [];
  for (let n = 20; n < 23; n++) got.push((await seen(call, n)).data.status);
  assert.deepEqual(got, ["qualified", "qualified", "capped"]);
  const v = await view(call);
  assert.deepEqual([v.friends, v.given, v.capReached, v.capMonths], [6, 2, true, 3]);
});

test("a friend who does not come back within the period and 30 days is let go", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call, env } = server();
  const c = await code(call);
  await use(call, 2, c);
  t.mock.timers.tick(36 * DAY);
  assert.equal((await view(call)).waiting, 1);
  t.mock.timers.tick(2 * DAY);
  assert.equal((await view(call)).waiting, 0);
  assert.equal((await seen(call, 2)).data.status, "expired");
  assert.equal((await view(call)).given, 0);
  // The hourly clean-up forgets it after that.
  await prune(env, Date.now());
  assert.equal((await seen(call, 2)).status, 404);
});

test("the same phone counts once, however often the app is installed again", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  await oneByOne(call);
  const c = await code(call);
  const other = await code(call, 5);
  await use(call, 2, c);
  t.mock.timers.tick(DAY);
  assert.equal((await seen(call, 2)).data.status, "credited");
  // Installed again: a new install id (3) on the same phone (mark 2), with the same or another inviter's code.
  const again = await use(call, 3, c, { phoneN: 2 });
  assert.deepEqual([again.data.status, again.data.reason], ["refused", "phone"]);
  assert.equal((await use(call, 4, other, { phoneN: 2, source: "typed" })).data.reason, "phone");
  assert.equal((await view(call)).friends, 1);
  assert.equal((await view(call, 5)).friends, 0);
});

test("inviting oneself does not count: the same install, or the same phone installed again", async () => {
  const { call } = server();
  const c = await code(call);
  const same = await use(call, 1, c);
  assert.deepEqual([same.data.status, same.data.reason], ["refused", "self"]);
  // The inviter's phone (mark 6) with the app installed again under a new install id.
  const c2 = await code(call, 6);
  const again = await use(call, 7, c2, { phoneN: 6 });
  assert.equal(again.data.reason, "self");
  assert.equal((await seen(call, 7)).data.status, "refused");
});

test("invites do not use up the cap of rewards for company rules", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call, DB } = server();
  await oneByOne(call);
  const c = await code(call);
  for (let n = 10; n < 14; n++) await use(call, n, c);
  t.mock.timers.tick(DAY);
  for (let n = 10; n < 14; n++) await seen(call, n);
  // Four invite months in 90 days; the rule reward cap (3 in 90 days) looks only at rule rewards.
  const dev = "(SELECT device FROM invite_codes)";
  assert.equal(DB.db.prepare(`SELECT COALESCE(SUM(months), 0) AS m FROM rewards WHERE device = ${dev} AND rule_id = 'invite'`).get().m, 4);
  assert.equal(DB.db.prepare(`SELECT COALESCE(SUM(months), 0) AS m FROM rewards WHERE device = ${dev} AND rule_id != 'invite'`).get().m, 0);
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
  await oneByOne(call);
  const c = await code(call);
  await use(call, 2, c);
  t.mock.timers.tick(DAY);
  await seen(call, 2);
  const rc = (await call("POST", "/api/passes/code", {}, phone(1))).data.code;
  await call("POST", "/api/passes/claim", { code: rc }, phone(8));
  const moved = await view(call, 8);
  assert.deepEqual([moved.code, moved.friends, moved.given], [c, 1, 1]);

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

test("the developer app's 보상 screen names invite rewards and sums up invites with the rules", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  await oneByOne(call);
  const c = await code(call);
  await use(call, 2, c);
  await use(call, 3, c);
  await use(call, 1, c);
  t.mock.timers.tick(DAY);
  await seen(call, 2);
  const owner = await signIn(call);
  const r = (await call("GET", "/api/admin/rewards?status=paid", undefined, owner)).data;
  assert.equal(r.rewards.length, 1);
  assert.deepEqual([r.rewards[0].company.name, r.rewards[0].invite, r.rewards[0].months, r.rewards[0].memo], ["친구 초대", true, 1, "친구 초대 1명"]);
  assert.equal(r.invites.codes, 1);
  assert.equal(r.invites.credited, 1);
  assert.equal(r.invites.waiting, 1);
  assert.deepEqual(r.invites.rules, { friends: 1, days: 1, months: 1, capMonths: 0 });
  assert.deepEqual(r.invites.refused, { self: 1 });
  assert.deepEqual(r.invites.top, [{ code: c, friends: 2, counted: 1, waiting: 1 }]);
  // A raw request without a device is refused, as for passes.
  assert.equal((await worker.fetch(new Request(`${ORIGIN}/api/invites`), { DB: null })).status, 503);
});

// AG 1.27 시작 [INVITE-09][INVITE-10] 사용자 리뷰 2026-10-25: "7일을 사용한 친구 5명마다 보상 … 5명은 1달 10명 채우면 또 1달 …
// 중복은 안되고 자기자신도 안돼". The months come for each five different friends who each used the app seven days,
// never for one friend's days, never twice for one friend or one phone, never for oneself.
test("each five different friends who each used seven days give one month: 5 → 1, 9 → 1, 10 → 2", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const c = await code(call);
  // One friend who keeps using the app for weeks is still one friend: no month for one person's days.
  await use(call, 10, c);
  for (let w = 0; w < 4; w++) {
    t.mock.timers.tick(7 * DAY);
    await seen(call, 10);
  }
  let v = await view(call);
  assert.deepEqual([v.progress, v.friends, v.given], [1, 1, 0]);
  // Four more: five friends, one month.
  for (let n = 11; n < 15; n++) await use(call, n, c);
  t.mock.timers.tick(7 * DAY);
  for (let n = 11; n < 15; n++) await seen(call, n);
  v = await view(call);
  assert.deepEqual([v.progress, v.friends, v.given], [0, 5, 1]);
  // Nine: still one month, four toward the next.
  for (let n = 15; n < 19; n++) await use(call, n, c);
  t.mock.timers.tick(7 * DAY);
  for (let n = 15; n < 19; n++) await seen(call, n);
  v = await view(call);
  assert.deepEqual([v.progress, v.friends, v.given], [4, 9, 1]);
  // The same friends opening again, and reporting again, change nothing.
  for (let n = 10; n < 19; n++) {
    await seen(call, n);
    await use(call, n, c);
  }
  assert.deepEqual([(await view(call)).progress, (await view(call)).given], [4, 1]);
  // The tenth: another month.
  await use(call, 19, c);
  t.mock.timers.tick(7 * DAY);
  assert.equal((await seen(call, 19)).data.status, "credited");
  v = await view(call);
  assert.deepEqual([v.progress, v.friends, v.given], [0, 10, 2]);
  assert.equal((await call("GET", "/api/passes", undefined, phone(1))).data.waitingMonths, 2);
});

test("a friend's phone counts for one inviter once, and the inviter's own phone never", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-25T03:00:00Z") });
  const { call } = server();
  const a = await code(call, 1);
  const b = await code(call, 2);
  await use(call, 10, a);
  // The same phone (mark 10) installed again and told with another inviter's code, or the same: not counted again.
  assert.equal((await use(call, 11, b, { phoneN: 10 })).data.reason, "phone");
  assert.equal((await use(call, 12, a, { phoneN: 10 })).data.reason, "phone");
  // Inviter 1 on a second install of its own phone, and inviter 2 typing its own code.
  assert.equal((await use(call, 13, a, { phoneN: 1, source: "typed" })).data.reason, "self");
  assert.equal((await use(call, 2, b)).data.reason, "self");
  t.mock.timers.tick(7 * DAY);
  for (const n of [10, 11, 12, 13]) await seen(call, n);
  assert.deepEqual([(await view(call, 1)).progress, (await view(call, 1)).friends], [1, 1]);
  assert.deepEqual([(await view(call, 2)).progress, (await view(call, 2)).friends], [0, 0]);
});

test("many friends of one code from one network in an hour are let in and noted for the developer app", async () => {
  const { call } = server();
  const c = await code(call);
  for (let n = 40; n < 45; n++) {
    const r = await call("POST", "/api/invites/use", { code: c, source: "referrer", phone: mark(n) }, { "x-device": device(n), ...from(77) });
    assert.equal(r.data.status, "waiting");
  }
  const owner = await signIn(call);
  const r = (await call("GET", "/api/admin/rewards?status=paid", undefined, owner)).data;
  // From the third friend of the same network in the hour, each is noted, with the code only.
  assert.equal(r.invites.bursts, 3);
  assert.deepEqual(r.invites.burstCodes, [{ code: c, n: 3 }]);
});

test("the invite page opens the Play app straight from a messenger's own browser, keeping the code", async () => {
  const { call } = server();
  const page = (await call("GET", "/i/ABCD2345")).text;
  assert.ok(page.includes("intent://details?id=com.bamnat.app&referrer=utm_source%3Dbamnat_invite%26utm_content%3DABCD2345#Intent;scheme=market;package=com.android.vending;"));
  assert.match(page, /KAKAOTALK/);
  assert.match(page, /저절로/);
  // Still nothing kept, and the plain Play link for every other browser.
  assert.ok(page.includes(storeLink("ABCD2345")));
});
// AG 1.27 끝
