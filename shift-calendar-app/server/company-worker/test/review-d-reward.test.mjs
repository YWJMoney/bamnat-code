// 2.0.0 검토 갈래 D 2차 [RVD-13]~[RVD-16]: 보상은 관리자가 확인한 뒤 지급하고, 한 사람이 받는 보상 프로는 1년에 모두 합쳐 12달까지
// (사용자 결정 카드 2026-11-12 03:45Z '확인 뒤 지급 + 1년 상한', 검토 보고서 SVC-05·06·98). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import worker from "../src/index.js";
import { server, device, from, signIn } from "./helpers.mjs";
import { grantMakerReward } from "../src/maker-reward.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400000;
const phone = (n) => ({ "x-device": device(n), ...from(n) });
const mark = (n) => createHash("sha256").update(`bamnat-invite|android-${n}`).digest("hex");
const recipe = (title) => ({ format: "bamnat-recipe", v: 1, title, summary: "칸이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }] });
const tick = async (env, now) => {
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
};
const owed = async (call, admin, q = "") => (await call("GET", `/api/admin/rewards?status=owed${q}`, undefined, admin)).data;
const hashOf = async (DB, n) => (await DB.prepare("SELECT owner FROM recipes WHERE title = ?").bind(`작품 ${n}`).first())?.owner;

/** [n] friends of inviter 1 who each used the app a day (the rules set to one friend, one day). */
async function friends(call, admin, n, start = 10) {
  await call("POST", "/api/admin/settings/invite", { friends: 1, days: 1 }, admin);
  const code = (await call("POST", "/api/invites/code", { phone: mark(1) }, phone(1))).data.code;
  for (let i = start; i < start + n; i++) await call("POST", "/api/invites/use", { code, source: i % 2 ? "typed" : "referrer", phone: mark(i) }, phone(i));
  return code;
}
const seenAll = async (call, n, start = 10) => { for (let i = start; i < start + n; i++) await call("POST", "/api/invites/seen", {}, phone(i)); };

test("[RVD-13] a work that reaches 모두의 도구 by its numbers waits for an admin: nothing is paid by itself", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env, DB } = server({ plaza: true });
  const admin = await signIn(call);
  const code = (await call("POST", "/api/recipes", { device: device(1), recipe: recipe("작품 1"), scope: "public" }, from(1))).data.code;
  await DB.prepare("UPDATE recipes SET applied = 80, kept7 = 40 WHERE code = ?").bind(code).run();
  await tick(env, AFTER);
  const rows = (await DB.prepare("SELECT status, months, paid_at FROM rewards WHERE rule_id = 'maker'").all()).results;
  assert.deepEqual(rows.map((r) => [r.status, r.months, r.paid_at]), [["owed", 1, null]]);
  // The maker's phone has nothing to use yet, and is told it is being looked at.
  const pass = (await call("GET", "/api/passes", undefined, phone(1))).data;
  assert.equal(pass.items.some((x) => x.why === "maker"), false);
  assert.equal(pass.waitingMonths, 0);
  assert.deepEqual(pass.pending.map((p) => [p.why, p.months]), [["maker", 1]]);
  assert.match(pass.pending[0].what, /모두의 도구로 승격 · 작품 1/);
  // The admin sees the work and its numbers beside it, then pays.
  const list = await owed(call, admin);
  const w = list.rewards.find((x) => x.kind === "maker");
  assert.equal(w.company.name, "만든 작품");
  assert.deepEqual([w.work.code, w.work.title, w.work.users, w.work.kept, w.work.flags], [code, "작품 1", 80, 50, 0]);
  assert.equal((await call("POST", `/api/admin/rewards/${w.id}`, { status: "paid" }, admin)).status, 200);
  assert.ok((await call("GET", "/api/passes", undefined, phone(1))).data.items.some((x) => x.why === "maker"));
});

test("[RVD-13] SVC-98: 채택 and 이달의 주제 by someone with only the community area wait too", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { env, DB, call } = server({ plaza: true });
  await call("POST", "/api/recipes", { device: device(2), recipe: recipe("작품 2"), scope: "public" }, from(2));
  const owner = await hashOf(DB, 2);
  for (const [kind, ref] of [["adopted", "AAAA2222"], ["contest", "2026-11:AAAA2222"]]) {
    const r = await grantMakerReward(env, owner, kind, ref, AFTER);
    assert.deepEqual([r.given, r.owed], [false, true]);
  }
  const rows = (await DB.prepare("SELECT status FROM rewards WHERE rule_id = 'maker'").all()).results;
  assert.deepEqual(rows.map((r) => r.status), ["owed", "owed"]);
});

test("[RVD-14] SVC-06: a group of friends gives an owed month with what the admin needs to judge it; the friend's phone hears it is checked", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server();
  const admin = await signIn(call);
  await friends(call, admin, 2);
  // One friend proved Play's app on a real phone (Play Integrity) this week.
  const hashes = (await DB.prepare("SELECT invitee FROM invite_uses ORDER BY invitee").all()).results.map((r) => r.invitee);
  await DB.prepare("INSERT INTO integrity (device, ok, why, at) VALUES (?, 1, '', ?)").bind(hashes[0], AFTER).run();
  t.mock.timers.tick(DAY);
  await seenAll(call, 2);
  const rows = (await DB.prepare("SELECT status FROM rewards WHERE rule_id = 'invite'").all()).results;
  assert.deepEqual(rows.map((r) => r.status), ["owed", "owed"]);
  const v = (await call("GET", "/api/invites", undefined, phone(1))).data;
  assert.deepEqual([v.given, v.pending, v.friends], [0, 2, 2]);
  assert.equal((await call("GET", "/api/passes", undefined, phone(1))).data.waitingMonths, 0);
  const list = await owed(call, admin, "&kind=invite");
  assert.equal(list.rewards.length, 2);
  assert.ok(list.rewards.every((w) => w.kind === "invite" && w.invite));
  const sig = list.rewards.map((w) => w.friends).sort((a, b) => b.proven - a.proven);
  assert.deepEqual(sig.map((s) => [s.n, s.proven, s.typed]), [[1, 1, 0], [1, 0, 1]]);
  assert.deepEqual(list.kinds, { invite: 2 });
});

test("[RVD-15] one person gets at most 12 months of rewards in 365 days, all kinds together, and moving the passes does not reset it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env, DB } = server({ plaza: true });
  const admin = await signIn(call);
  await call("POST", "/api/recipes", { device: device(3), recipe: recipe("작품 3"), scope: "public" }, from(3));
  const old = await hashOf(DB, 3);
  // 11 months paid this year: a company rule's 6, an invite's 2, a maker's 3.
  for (const [rule, m, i] of [["rule-x", 6, 1], ["invite", 2, 2], ["maker", 3, 3]]) {
    await DB.prepare(`INSERT INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at, deferred)
      VALUES (?, ?, ?, ?, ?, 'paid', '', ?, ?, 0)`).bind(`wPAID${i}aaaaaaaaaaaaaaaa`, `paid:${i}`, rule, old, m, AFTER - 10 * DAY, AFTER - 10 * DAY).run();
  }
  // The passes move to a new phone with the recovery code; the work stays on the old install.
  const made = (await call("POST", "/api/passes/code", {}, { "x-device": device(3) })).data.code;
  assert.equal((await call("POST", "/api/passes/claim", { code: made }, { "x-device": device(4), ...from(4) })).status, 200);
  await grantMakerReward(env, old, "adopted", "BBBB3333", AFTER);
  const w = (await owed(call, admin)).rewards.find((x) => x.kind === "maker");
  assert.deepEqual([w.year.used, w.year.cap, w.year.left, w.capped], [11, 12, 1, true]);
  // Paying 3 months is refused with what is left; 1 month goes.
  const no = await call("POST", `/api/admin/rewards/${w.id}`, { status: "paid" }, admin);
  assert.deepEqual([no.status, no.data.error, no.data.left], [409, "cap", 1]);
  assert.equal((await call("POST", `/api/admin/rewards/${w.id}`, { status: "paid", months: 1 }, admin)).status, 200);
  // Now full: the next owed one says 상한 with nothing left, and paying it is refused.
  await grantMakerReward(env, old, "tool", "CCCC4444", AFTER);
  const full = (await owed(call, admin)).rewards.find((x) => x.kind === "maker");
  assert.deepEqual([full.year.used, full.year.left, full.capped], [12, 0, true]);
  assert.equal((await call("POST", `/api/admin/rewards/${full.id}`, { status: "paid" }, admin)).status, 409);
  // A year on, the room is back.
  t.mock.timers.tick(366 * DAY);
  assert.equal((await call("POST", `/api/admin/rewards/${full.id}`, { status: "paid" }, await signIn(call))).status, 200);
});

test("[RVD-15] the invite rules say the cap: 12 months a year unless the developer set a lower one", async () => {
  const { call } = server();
  assert.deepEqual((await call("GET", "/api/invites/rules")).data, { need: 5, days: 7, months: 1, capMonths: 12 });
  const admin = await signIn(call);
  await call("POST", "/api/admin/settings/invite", { capMonths: 3 }, admin);
  assert.equal((await call("GET", "/api/invites/rules")).data.capMonths, 3);
  await call("POST", "/api/admin/settings/invite", { capMonths: 30 }, admin);
  assert.equal((await call("GET", "/api/invites/rules")).data.capMonths, 12);
});

test("[RVD-16] many owed rewards: pick several and pay or cancel them at once; ones past the cap are skipped and said", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server();
  const admin = await signIn(call);
  await friends(call, admin, 3);
  t.mock.timers.tick(DAY);
  await seenAll(call, 3);
  const inviter = (await DB.prepare("SELECT device FROM invite_codes").first()).device;
  await DB.prepare(`INSERT INTO rewards (id, report_id, rule_id, device, months, status, memo, created_at, paid_at, deferred)
    VALUES ('wFULLaaaaaaaaaaaaaaaaaa', 'full', 'rule-y', ?, 10, 'paid', '', ?, ?, 0)`).bind(inviter, Date.now(), Date.now()).run();
  const ids = (await owed(call, admin)).rewards.map((w) => w.id);
  assert.equal(ids.length, 3);
  const r = await call("POST", "/api/admin/rewards/bulk", { ids, status: "paid" }, admin);
  assert.equal(r.status, 200, r.text);
  // 10 paid + 1 + 1 = 12; the third is past the cap and stays owed.
  assert.deepEqual([r.data.done, r.data.skipped.length, r.data.skipped[0].why], [2, 1, "cap"]);
  const left = (await owed(call, admin)).rewards;
  assert.equal(left.length, 1);
  const v = await call("POST", "/api/admin/rewards/bulk", { ids: left.map((w) => w.id), status: "void" }, admin);
  assert.deepEqual([v.data.done, v.data.skipped.length], [1, 0]);
  assert.equal((await call("POST", "/api/admin/rewards/bulk", { ids: [], status: "paid" }, admin)).status, 400);
  assert.equal((await call("POST", "/api/admin/rewards/bulk", { ids: Array(51).fill(ids[0]), status: "paid" }, admin)).status, 400);
});
