// 2.0.0 [RVD-23] PC-94 (통합 2차 PC 검증): a calendar started from a received link ('내 근무표예요') counted the shift's 휴게 as work (주간 11시간 on
// the sender, 12 on the receiver), because the link carried only the start and end. Now a shift may carry `rest` (minutes, 0~720, 0 = 휴게 없음)
// and `restStart` ('HH:MM'); the names and ranges are the ones agreed with 갈래 G (11-12). An app before 2.0.0 ignores both. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { validate } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init,
  headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.45", ...(init.headers ?? {}) } }), env, { waitUntil() {} });

const link = (types) => ({ v: 1, title: "민지", ttlDays: 30, types, days: types.map((t, i) => ({ date: `2026-12-0${i + 1}`, type: t.id })) });
const day = { id: 1, name: "주간", short: "주", color: "#1E88E5", time: "07:00-19:00" };
const night = { id: 2, name: "야간", short: "야", color: "#5E35B1", time: "19:00-07:00" };
const off = { id: 3, name: "휴무", short: "휴", color: "#9E9E9E", off: true };
const shop = { id: 4, name: "영업", short: "영", color: "#43A047", time: "11:00-22:00", gap: "15:00-17:00" };
const typeOf = (t) => validate(link([t])).schedule?.types[0];
const error = (t) => validate(link([t])).error;

test("[RVD-23] PC-94 a shift's 휴게 goes with the link and comes back as sent; 0 stays 0, an older app's shift has no field", async () => {
  const env = { SHARES: kv() };
  const res = await call(env, "/api/share", { method: "POST", body: JSON.stringify(link([{ ...day, rest: 60, restStart: "12:00" }, { ...night, rest: 0 }, off])) });
  assert.equal(res.status, 201);
  const read = (await (await call(env, `/api/share/${(await res.json()).id}`)).json()).schedule.types;
  assert.deepEqual([read[0].rest, read[0].restStart], [60, "12:00"]);
  assert.deepEqual([read[1].rest, "restStart" in read[1]], [0, false]);
  assert.equal("rest" in read[2], false);
  // A link from an app before 2.0.0: no field, so the receiver knows it was not told.
  assert.equal("rest" in typeOf(day), false);
});

test("[RVD-23] PC-94 rest across midnight and beside a two-part shop day", () => {
  assert.deepEqual(typeOf({ ...night, rest: 60, restStart: "02:00" }), { ...night, off: false, icon: null, rest: 60, restStart: "02:00" });
  assert.equal(typeOf({ ...night, rest: 30, restStart: "06:30" }).restStart, "06:30");
  assert.equal(typeOf({ ...shop, rest: 30 }).rest, 30);
  // restStart with no rest says nothing; a stray rest on a day off or a shift without times is dropped, not refused.
  assert.equal("restStart" in typeOf({ ...day, rest: 0, restStart: "12:00" }), false);
  assert.equal("rest" in typeOf({ ...off, rest: 0 }), false);
  assert.equal("rest" in typeOf({ ...day, time: undefined, rest: 30 }), false);
});

test("[RVD-23] PC-94 a rest that is not the app's is refused (type_rest)", () => {
  for (const t of [{ ...day, rest: -1 }, { ...day, rest: 721 }, { ...day, rest: 30.5 }, { ...day, rest: "60" }, { ...day, rest: 720 },
    { ...shop, rest: 540 }, { ...day, rest: 60, restStart: "18:30" }, { ...day, rest: 60, restStart: "06:00" }, { ...day, rest: 60, restStart: "12" },
    { ...day, rest: 60, restStart: "24:00" }, { ...night, rest: 60, restStart: "06:30" }, { ...day, rest: 60, restStart: 720 }]) {
    assert.equal(error(t), "type_rest", JSON.stringify(t));
  }
});
