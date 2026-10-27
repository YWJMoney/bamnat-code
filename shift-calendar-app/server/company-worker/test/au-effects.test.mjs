// AU 1.29 [NXT-07] AM 4-3: problem reports split by whether the flag was on for the phone that sent them, from what the
// server already keeps with a question (its time and app version, 처리방침 3.2 1-3항), nothing new asked of the phone.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn } from "./helpers.mjs";

const DAY = 24 * 3600e3;

/** A problem report written straight into the database at [at], with [app] as its app line (none when null). */
function bug(DB, n, at, app) {
  const id = `b${String(n).padStart(21, "0")}`;
  const info = JSON.stringify(app ? { app } : {});
  DB.db.prepare("INSERT INTO inquiries (id, device, kind, info, status, created_at, updated_at) VALUES (?, ?, 'bug', ?, 'open', ?, ?)")
    .run(id, `h${n}`, info, at, at);
}

test("[NXT-07] /effects splits the reports around a turn into on, off, from an app outside the range, and unknown", async (t) => {
  const t0 = Date.parse("2026-11-01T03:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: t0 });
  const { call, DB } = server();
  const owner = await signIn(call);
  // The server's first look at every flag (any flag write runs flagTick): not a turn.
  assert.equal((await call("POST", "/api/admin/flags/type.field/range", { minApp: 1, maxApp: null, why: "처음 보기" }, owner)).status, 200);
  t.mock.timers.tick(3 * DAY);
  const t1 = Date.now();
  // plaza.write turns off now (a turn the server sees), and only app versions 40 and up keep it.
  assert.equal((await call("POST", "/api/admin/flags/plaza.write/force", { force: "off", why: "글 신고가 몰려요" }, owner)).status, 200);
  assert.equal((await call("POST", "/api/admin/flags/plaza.write/range", { minApp: 40, maxApp: null, why: "40부터" }, owner)).status, 200);
  await call("GET", "/api/config");
  bug(DB, 1, t1 - DAY, "1.29.0 (40)"); // before the turn, on, inside the range → on
  bug(DB, 2, t1 - DAY, "1.28.0 (39)"); // before the turn, but outside the range → off
  bug(DB, 3, t1 + DAY / 2, "1.29.0 (40)"); // after the turn → off
  bug(DB, 4, t1 + DAY / 2, null); // no app line → unknown
  bug(DB, 5, t0 - DAY, "1.29.0 (40)"); // before the turn too (on); before the server first looked at type.shift → unseen there
  t.mock.timers.tick(DAY);
  const e = (await call("GET", "/api/admin/effects", undefined, owner)).data;
  const pw = e.flags.find((f) => f.key === "plaza.write");
  assert.equal(pw.state, "forced_off");
  assert.ok(pw.compare, "the server saw it turn");
  assert.deepEqual(pw.compare.split, { on: 2, maybe: 0, off: 2, unknown: 1, unseen: 0 });
  // The plain before/after counts stay as they were.
  assert.equal(pw.compare.bugsBefore + pw.compare.bugsAfter, 5);
  // type.shift was only looked at, never turned: what came before that look is unseen, what came after is on.
  const shift = e.flags.find((f) => f.key === "type.shift");
  assert.equal(shift.compare.split.unseen, 1);
  assert.equal(shift.compare.split.on, 3);
  assert.equal(shift.compare.split.unknown, 1);
  // stats.more waits on consents only the phone knows: what would be on is "maybe", never "on".
  const sm = e.flags.find((f) => f.key === "stats.more");
  if (sm.compare) assert.equal(sm.compare.split.on, 0);
});
