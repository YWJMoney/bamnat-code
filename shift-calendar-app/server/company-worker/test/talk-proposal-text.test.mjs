// BC 1.30 [PROP-01][ITEM-08] A proposal in the talk carries its setup for the company's members, so the app can work out
// "이 제안으로 내 월급 계산해 보기" from the post itself; the 통상시급 hours have their own field. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, setup, device, from } from "./helpers.mjs";

async function talkOn(call) {
  const a = await call("POST", "/api/reports", report(1), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  const say = (n, body, more = {}) => call("POST", "/api/posts", { device: device(n), company: c, setup: g, body, ...more }, from(n));
  const read = (n) => call("GET", `/api/companies/${c}/setups/${g}/posts`, undefined, n ? { "x-device": device(n) } : {});
  return { c, g, say, read };
}

test("[PROP-01] a member reads a proposal's setup with the post; anyone else gets no pay", async () => {
  const { call, DB } = server({ plaza: true });
  const t = await talkOn(call);
  // [FIN130-15] A company's one amount comes only once 처리방침 3.9 (3.4 brought forward) is in force: here it is (the owner moved its day back).
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('policy_overrides', ?)")
    .bind(JSON.stringify({ "3.9": { posted: "2026-10-01", effective: "2026-10-02", heldUntil: null, unfavourable: false } })).run();
  const better = setup();
  better.v = 4;
  better.pay.monthlyHours = 209;
  better.pay.items.push({ id: "meal", name: "식대", amount: { entries: [], raise: null }, cadence: "MONTHLY", types: [], ordinary: true,
    taxable: true, months: [], fixedOvertime: 0, festivals: [], festivalBefore: false, share: "FIXED", fixedAmount: 100000 });
  const change = [
    { field: "hours", now: "월 소정 시간: 180시간", want: "월 소정 시간: 209시간", why: "명세서에 209시간으로 나와요" },
    { field: "allowance", now: "식대: 없음", want: "식대: 매달 회사 공통 정액 · 통상임금", why: "" },
  ];
  const sent = await t.say(1, "기준 시간과 식대가 달라요", { kind: "proposal", change, proposal: better });
  assert.equal(sent.status, 201);
  // Phone 1 sent the company's setup, so it joined: it reads the proposal's setup, named for the company.
  const mine = (await t.read(1)).data.posts.find((p) => p.kind === "proposal");
  assert.deepEqual(mine.change.map((l) => l.field), ["hours", "allowance"]);
  const text = JSON.parse(mine.proposal.text);
  assert.equal(text.company, "예시정밀");
  assert.equal(text.pay.monthlyHours, 209);
  assert.deepEqual(text.pay.items.find((i) => i.name === "식대"), { ...better.pay.items[3] });
  // Someone who never joined sees the post and its shift lines, never the pay in it.
  const stranger = (await t.read(9)).data.posts.find((p) => p.kind === "proposal");
  assert.equal(stranger.proposal.text, undefined);
  assert.deepEqual(stranger.change, []);
  const nobody = (await t.read()).data.posts.find((p) => p.kind === "proposal");
  assert.equal(nobody.proposal.text, undefined);
});

test("[ITEM-08] the proposal fields offer 통상시급 기준 시간, and the words still take no amount", async () => {
  const { call } = server({ plaza: true });
  const t = await talkOn(call);
  const better = setup();
  better.pay.monthlyHours = 209;
  const fields = (await call("GET", "/api/plazas")).data.changeFields;
  assert.ok(fields.some((f) => f.key === "hours" && f.name === "통상시급 기준 시간"));
  const money = [{ field: "allowance", now: "식대: 없음", want: "식대: 매달 100,000원", why: "" }];
  assert.equal((await t.say(1, "식대가 있어요", { kind: "proposal", change: money, proposal: better })).data.error, "amount");
  const ok = await t.say(1, "기준 시간이 달라요", { kind: "proposal", change: [{ field: "hours", want: "209시간" }], proposal: better });
  assert.equal(ok.status, 201);
});
