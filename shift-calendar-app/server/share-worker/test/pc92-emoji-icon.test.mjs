// 2.0.0 [RVD-22] PC-92 (통합 2차 PC 검증, 높음): a phone whose shift has an emoji icon (근무 종류 › 아이콘 › 이모지, or a 근무 꾸미기 work such as
// 야간 회복 스테이션 ☀️🌙🛌) sends "e:" + the emoji since 1.34 (ShiftIcon.emojiKey). The server took only a-z0-9 and answered type_icon, so the
// link was never made. Now any one emoji the app's picker gives is kept; anything else is still refused. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { validate, iconOk } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init,
  headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.44", ...(init.headers ?? {}) } }), env, { waitUntil() {} });

/** What ShareLink sends for a 교대 phone that put 근무 꾸미기 on: v1, the icons as the shift types hold them. */
const link = (icons) => ({
  v: 1, title: "민지", ttlDays: 30,
  types: icons.map((icon, i) => ({ id: i + 1, name: `근무${i + 1}`, short: String(i + 1), color: "#1E88E5", time: "07:00-19:00", ...(icon == null ? {} : { icon }) })),
  days: icons.map((_, i) => ({ date: `2026-12-0${i + 1}`, type: i + 1 })),
});

test("[RVD-22] PC-92 a shift with an emoji icon ('e:🌙', 'e:☀️') makes its link, and the receiving app reads the same icon back", async () => {
  const env = { SHARES: kv() };
  const res = await call(env, "/api/share", { method: "POST", body: JSON.stringify(link(["e:☀️", "e:🌙", "e:🛌", "moon", null])) });
  assert.equal(res.status, 201);
  const made = await res.json();
  const read = await (await call(env, `/api/share/${made.id}`)).json();
  assert.deepEqual(read.schedule.types.map((t) => t.icon), ["e:☀️", "e:🌙", "e:🛌", "moon", null]);
  // The page and the calendar feed never print the icon.
  const page = await (await call(env, `/s/${made.id}`)).text();
  assert.ok(!page.includes("e:🌙"));
});

test("[RVD-22] PC-92 the picker's longest forms pass: skin tones, ZWJ, flags, keycaps, with or without VS16", () => {
  for (const e of ["🧑🏻‍❤️‍💋‍🧑🏼", "👩🏽‍⚕️", "🏳️‍⚧️", "🇰🇷", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "#️⃣", "1️⃣", "☀", "❤️", "👍🏿"]) {
    assert.ok(iconOk(`e:${e}`), e);
    assert.equal(validate(link([`e:${e}`])).error, undefined, e);
  }
});

test("[RVD-22] PC-92 anything that is not one emoji is not kept: [RVD-28] SD-08 it is dropped and the link is made (총괄 '버리기, 거절 아님')", () => {
  const bad = ["e:", "e:a", "e:<b>", "e:🌙<script>", "e:🌙\"", "e:🌙'", "e:🌙 ", "e: 🌙", "e:🌙\n", "e:\u0000", "e:12", "e:#", "e:🇰",
    "e:🌙🌙", "e:🌙\u200D", "e:\u200D🌙", "e:&lt;", "x:🌙", "E:🌙", "Moon", "<svg>", "e:" + "🌙\u200D".repeat(6) + "🌙", 7];
  for (const icon of bad) {
    assert.equal(iconOk(icon), false, JSON.stringify(icon));
    const v = validate(link([icon]));
    assert.equal(v.error, undefined, JSON.stringify(icon));
    assert.equal(v.schedule.types[0].icon, null, JSON.stringify(icon));
  }
});
