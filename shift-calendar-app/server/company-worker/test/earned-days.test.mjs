// 1.36 841 쓰면 쌓이는 날: a problem report whose 개발사항 is marked 완료 gives that phone 3 days of 프로, once per item,
// at most 14 days a month, stacked after the months from the launch: `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";
import { EARNED, kstMonthStart, stackDays } from "../src/money.js";

const DAY = 86400000;
const report = (n, kind = "bug", text = "알람이 안 울려요") => ({ v: 1, device: device(n), kind, text, info: { app: "1.36.0 (300)" } });

test("earned days stack after the months from the launch, and wait before it", () => {
  const launch = Date.parse("2026-12-01T00:00:00Z");
  const rows = [{ days: 3, given_at: Date.parse("2026-11-10T00:00:00Z") }, { days: 3, given_at: Date.parse("2026-12-20T00:00:00Z") }];
  assert.deepEqual(stackDays(null, rows, null), { until: null, waiting: 6 });
  // 3 from the launch (12-04), then 3 more from when they were given (12-20 → 12-23).
  assert.equal(new Date(stackDays(null, rows, launch).until).toISOString(), "2026-12-23T00:00:00.000Z");
  // After months that run to 2027-02-01, both go after them.
  assert.equal(new Date(stackDays(Date.parse("2027-02-01T00:00:00Z"), rows, launch).until).toISOString(), "2027-02-07T00:00:00.000Z");
  // The month starts at midnight in Korea.
  assert.equal(new Date(kstMonthStart(Date.parse("2026-12-01T10:00:00Z"))).toISOString(), "2026-11-30T15:00:00.000Z");
});

test("a fixed problem report gives its phone 3 days once, not an idea, and at most 14 a month", async () => {
  const { call } = server();
  const owner = await signIn(call);
  const bug = (await call("POST", "/api/inquiries", report(1), from(1))).data;
  const idea = (await call("POST", "/api/inquiries", report(2, "idea", "주 시작 요일을 고르고 싶어요"), from(2))).data;
  const item = (await call("POST", "/api/admin/dev", { title: "알람 안 울림", inquiry: bug.id }, owner)).data;
  await call("POST", `/api/admin/dev/${item.id}/links`, { inquiry: idea.id }, owner);
  assert.equal((await call("POST", `/api/admin/dev/${item.id}`, { status: "done" }, owner)).status, 200);
  const mine = (await call("GET", "/api/passes", undefined, { "x-device": device(1) })).data;
  assert.deepEqual(mine.earned.map((e) => [e.days, e.why]), [[EARNED.bugDays, "문제 신고가 고쳐져서"]]);
  assert.equal(mine.waitingDays, 3);
  assert.deepEqual((await call("GET", "/api/passes", undefined, { "x-device": device(2) })).data.earned, []);

  // Back to 개발중 and 완료 again: nothing more for the same item.
  const fresh = (await call("GET", `/api/admin/dev/${item.id}`, undefined, owner)).data;
  await call("POST", `/api/admin/dev/${item.id}`, { status: "doing", since: fresh.updatedAt }, owner);
  const again = (await call("GET", `/api/admin/dev/${item.id}`, undefined, owner)).data;
  await call("POST", `/api/admin/dev/${item.id}`, { status: "done", since: again.updatedAt }, owner);
  assert.equal((await call("GET", "/api/passes", undefined, { "x-device": device(1) })).data.earned.length, 1);

  // Five more fixed items in bulk: 3+3+3+3 and then 2 reach the month's 14, the sixth gives nothing.
  const ids = [];
  for (let i = 0; i < 5; i++) {
    const q = (await call("POST", "/api/inquiries", report(1, "bug", `화면이 멈춰요 ${i}번째 경우`), from(1))).data;
    ids.push((await call("POST", "/api/admin/dev", { title: `멈춤 ${i}`, inquiry: q.id }, owner)).data.id);
  }
  assert.equal((await call("POST", "/api/admin/dev/bulk", { ids, status: "done" }, owner)).status, 200);
  const all = (await call("GET", "/api/passes", undefined, { "x-device": device(1) })).data;
  assert.deepEqual(all.earned.map((e) => e.days), [3, 3, 3, 3, 2]);
  assert.equal(all.waitingDays, EARNED.monthCap);
});
