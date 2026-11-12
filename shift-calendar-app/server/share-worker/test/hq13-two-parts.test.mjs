// 2.0.0 [RVD-20] HQ-13 하루 두 구간·시간 연차(결정 19번, 설계 4-4절, 갈래 H #451): a link keeps a shift's gap and a day's hour leave, the page and
// a calendar app show the two parts, and an app before 2.0.0 still reads "time" and "leave" as before. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { validate, renderSchedule, renderIcs, plainLeave } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init,
  headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.43", ...(init.headers ?? {}) } }), env, { waitUntil() {} });

/** What H's ShareLinkPayload sends (ShareLinkTest.anHoursLeaveAndTwoPartsReadRightOnAnOldReceiver), plain leave as without 휴가 종류. */
const twoParts = (change = (b) => b) => change({
  v: 2, shows: [], title: "민지", ttlDays: 30,
  types: [
    { id: 4, name: "영업", short: "영", color: "#43A047", time: "11:00-22:00", gap: "15:00-17:00" },
    { id: 5, name: "야간", short: "야", color: "#5E35B1", time: "20:00-05:00", gap: "00:00-01:00" },
    { id: 9, name: "휴무", short: "휴", color: "#9E9E9E", off: true },
  ],
  days: [
    { date: "2026-12-01", type: 4, leaveMinutes: 120, leaveStart: true },
    { date: "2026-12-02", type: 4, leave: "무급 반차 5시간", leaveMinutes: 300, leaveAt: 780 },
    { date: "2026-12-03", type: 4, leave: "반차" },
    { date: "2026-12-04", type: 5 },
    { date: "2026-12-05", type: 9 },
  ],
});
const day = (s, date) => s.days.find((d) => d.date === date);
const refused = (error, change) => assert.equal(validate(twoParts(change)).error, error);

test("[RVD-20] HQ-13 a link keeps a shift's gap and a day's hour leave, and what an older app reads stays as it was", () => {
  const { schedule } = validate(twoParts());
  assert.deepEqual(schedule.types[0], { id: 4, name: "영업", short: "영", color: "#43A047", time: "11:00-22:00", off: false, icon: null, gap: "15:00-17:00" });
  assert.equal(schedule.types[1].gap, "00:00-01:00");
  assert.equal("gap" in schedule.types[2], false);
  // Two hours from the start: no "leave" text (an older app sees a worked day), the minutes and where apart.
  assert.deepEqual(day(schedule, "2026-12-01"), { date: "2026-12-01", type: 4, second: null, leave: null, leaveMinutes: 120, leaveStart: true });
  // Five hours from 13:00, 휴가 종류 not ticked: the kind goes, the hours and '반' stay (an older app reads half a day).
  assert.deepEqual(day(schedule, "2026-12-02"), { date: "2026-12-02", type: 4, second: null, leave: "반차 5시간", leaveMinutes: 300, leaveAt: 780 });
  assert.deepEqual(day(schedule, "2026-12-03"), { date: "2026-12-03", type: 4, second: null, leave: "반차" });
  // 휴가 종류 ticked: the words as the app wrote them.
  assert.equal(day(validate(twoParts((b) => ({ ...b, shows: ["leaveKinds"] }))).schedule, "2026-12-02").leave, "무급 반차 5시간");
  // A day off with only a short leave on it is still a day the link says something about.
  assert.ok(validate(twoParts((b) => ({ ...b, days: [{ date: "2026-12-01", leaveMinutes: 60 }] }))).schedule);
});

test("[RVD-20] HQ-13 the hours of a leave survive 휴가 종류 not ticked, and the leave words before 2.0.0 are what they were", () => {
  assert.deepEqual(["무급 반차 4.5시간", "유급 반차 12시간", "반차 6시간", "보상 반차 4시간"].map(plainLeave), ["반차 4.5시간", "반차 12시간", "반차 6시간", "반차 4시간"]);
  assert.deepEqual(["연차", "무급 반차", "오전 반반차", "0.75일 무급 휴가", "무급 휴가", "보상 휴가"].map(plainLeave), ["연차", "반차", "반반차", "0.75일 휴가", "휴가", "휴가"]);
  // An app before 2.0.0 (ReceivedDay.fullLeave) reads a leave without '반' or '0.75' as a whole day off: none of these is.
  for (const text of ["무급 반차 4.5시간", "유급 반차 12시간"].map(plainLeave)) assert.ok(text.includes("반"), text);
});

test("[RVD-20] HQ-13 a gap or an hour's leave that is not the app's is refused", () => {
  refused("type_gap", (b) => ({ ...b, types: [{ ...b.types[0], gap: "3pm" }, ...b.types.slice(1)] }));
  // Outside the shift, a part under 30 minutes either side, a gap under 30 minutes, on a shift without times or a day off.
  for (const gap of ["10:00-12:00", "11:10-12:00", "15:00-21:40", "15:00-15:20", "21:00-23:00", "15:00-15:00"]) {
    refused("type_gap", (b) => ({ ...b, types: [{ ...b.types[0], gap }, ...b.types.slice(1)] }));
  }
  refused("type_gap", (b) => ({ ...b, types: [{ ...b.types[0], time: null }, ...b.types.slice(1)] }));
  refused("type_gap", (b) => ({ ...b, types: [...b.types.slice(0, 2), { ...b.types[2], time: "09:00-18:00", gap: "12:00-13:00" }] }));
  // Over midnight: the gap from 00:00 fits 20:00~05:00, one from 04:40 leaves under 30 minutes after it.
  refused("type_gap", (b) => ({ ...b, types: [b.types[0], { ...b.types[1], gap: "04:40-04:50" }, b.types[2]] }));
  const leave = (d) => (b) => ({ ...b, days: [{ date: "2026-12-01", type: 4, ...d }] });
  for (const d of [{ leaveMinutes: 0 }, { leaveMinutes: 1440 }, { leaveMinutes: 90.5 }, { leaveMinutes: "120" }, { leaveMinutes: 60, leaveAt: 1440 },
    { leaveMinutes: 60, leaveAt: -1 }, { leaveMinutes: 60, leaveStart: "yes" }, { leaveMinutes: 60, leaveAt: 600, leaveStart: true },
    { leaveAt: 600 }, { leaveStart: true }]) refused("day_leave", leave(d));
});

test("[RVD-20] HQ-13 the page and a calendar app show a shift in two parts and a short leave", () => {
  const { schedule } = validate(twoParts((b) => ({ ...b, ics: true })));
  const html = renderSchedule(schedule, new Date(Date.now() + 86400000).toISOString());
  assert.match(html, /영업<\/span><span class="muted"><span class="nm">11:00~15:00<\/span> · <span class="nm">17:00~22:00<\/span><\/span>/);
  assert.match(html, /야간<\/span><span class="muted"><span class="nm">20:00~00:00<\/span> · <span class="nm">01:00~05:00<\/span><\/span>/);
  assert.match(html, /<span class="leave">휴가 2시간<\/span>/);
  assert.match(html, /<span class="leave">반차 5시간<\/span>/);
  // The hours as the app's link words say them, off the half hours in hours and minutes.
  const short = (m) => renderSchedule(validate(twoParts((b) => ({ ...b, days: [{ date: "2026-12-01", type: 4, leaveMinutes: m }] }))).schedule,
    new Date(Date.now() + 86400000).toISOString()).match(/<span class="leave">([^<]*)<\/span>/)[1];
  assert.deepEqual([30, 60, 90, 75, 210].map(short), ["휴가 30분", "휴가 1시간", "휴가 1.5시간", "휴가 1시간 15분", "휴가 3.5시간"]);
  const ics = renderIcs(schedule, "abcdefghijklmnop", null);
  const event = (uid) => ics.split("BEGIN:VEVENT").find((e) => e.includes(`UID:${uid}@`));
  assert.match(event("2026-12-01-s"), /DTSTART;TZID=Asia\/Seoul:20261201T110000\r\nDTEND;TZID=Asia\/Seoul:20261201T150000/);
  assert.match(event("2026-12-01-s2"), /DTSTART;TZID=Asia\/Seoul:20261201T170000\r\nDTEND;TZID=Asia\/Seoul:20261201T220000/);
  assert.match(event("2026-12-01-l"), /SUMMARY:휴가 2시간/);
  // Over midnight: the second part is on the next day.
  assert.match(event("2026-12-04-s"), /DTSTART;TZID=Asia\/Seoul:20261204T200000\r\nDTEND;TZID=Asia\/Seoul:20261205T000000/);
  assert.match(event("2026-12-04-s2"), /DTSTART;TZID=Asia\/Seoul:20261205T010000\r\nDTEND;TZID=Asia\/Seoul:20261205T050000/);
  // A shift in one part is one event, as before.
  const plain = renderIcs(validate({ ...twoParts(), ics: true, types: twoParts().types.map(({ gap, ...t }) => t) }).schedule, "abcdefghijklmnop", null);
  assert.equal(plain.includes("-s2@"), false);
  assert.match(plain, /DTSTART;TZID=Asia\/Seoul:20261201T110000\r\nDTEND;TZID=Asia\/Seoul:20261201T220000/);
});

test("[RVD-20] HQ-13 the app reads back what it sent; a link from an older app is stored as before", async () => {
  const env = { SHARES: kv() };
  const made = await (await call(env, "/api/share", { method: "POST", body: JSON.stringify(twoParts()) })).json();
  const read = await (await call(env, `/api/share/${made.id}`)).json();
  assert.equal(read.schedule.types[0].gap, "15:00-17:00");
  assert.deepEqual([day(read.schedule, "2026-12-01").leaveMinutes, day(read.schedule, "2026-12-01").leaveStart], [120, true]);
  assert.equal(day(read.schedule, "2026-12-02").leaveAt, 780);
  const old = { v: 1, title: "민지", ttlDays: 30, types: [{ id: 1, name: "주간", short: "주", color: "#1E88E5", time: "07:00-19:00" }],
    days: [{ date: "2026-12-01", type: 1, leave: "반차" }] };
  assert.equal(JSON.stringify(validate(old).schedule), JSON.stringify({ v: 1, title: "민지",
    types: [{ id: 1, name: "주간", short: "주", color: "#1E88E5", time: "07:00-19:00", off: false, icon: null }],
    days: [{ date: "2026-12-01", type: 1, second: null, leave: "반차" }] }));
});
