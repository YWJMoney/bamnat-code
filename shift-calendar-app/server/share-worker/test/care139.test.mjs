// 1.39 [CARE-07~09] 팀·사장님 링크(병동 표·근무 신청·결원·바꾸기·근로계약서·출퇴근): 페이지마다 신고 양식과 '돈을 요구하지 않아요'
// 한 줄, 운영자가 종류별로 내리기(내용은 바로 지우고 이유와 함께 410), 전화·링크·계좌·메신저 옮기기는 받지 않기(사기 막기).
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, scamProblem } from "../src/index.js";

const ADMIN = "admin-token-for-tests-0123456789";
function kv() {
  const map = new Map();
  const meta = new Map();
  return {
    map,
    async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async getWithMetadata(k) { return map.has(k) ? { value: map.get(k), metadata: meta.get(k) ?? null } : { value: null, metadata: null }; },
    async put(k, v, o = {}) { map.set(k, v); if (o.metadata) meta.set(k, o.metadata); },
    async delete(k) { map.delete(k); meta.delete(k); },
    async list({ prefix = "" } = {}) { return { keys: [...map.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name, metadata: meta.get(name) })), list_complete: true }; },
  };
}
const call = (env, path, init = {}) => worker.fetch(new Request(`https://share.example${path}`, { ...init, headers: { "cf-connecting-ip": "203.0.113.51", ...(init.headers ?? {}) } }), env, { waitUntil() {} });
const form = (body) => ({ method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const contract = { place: "밤낮 카페", staff: "김 하나", title: "단시간 근로자 근로계약서", days: 7,
  items: [{ t: "근로 시작일", b: "2026년 11월 9일부터" }, { t: "임금", b: "시급 10,320원\n지급일: 매월 10일" }, { t: "사업자", b: "123-45-67890" }] };

test("[CARE-09] phone numbers, links, account numbers and moves to a messenger are refused; what the app writes goes", async () => {
  readsForget();
  const env = { SHARES: kv() };
  assert.equal((await call(env, "/api/sign", { method: "POST", body: JSON.stringify(contract) })).status, 201);
  for (const [b, why] of [["보증금 30만 원은 110-123-456789로 보내 주세요", "account"], ["문의 010-1234-5678", "phone"],
    ["자세한 건 https://bit.ly/x", "link"], ["오픈채팅으로 오세요", "messenger"]]) {
    const r = await call(env, "/api/sign", { method: "POST", body: JSON.stringify({ ...contract, items: [...contract.items, { t: "안내", b }] }) });
    assert.equal(r.status, 400, b);
    assert.deepEqual(await r.json(), { error: "contact", why });
  }
  const bad = await call(env, "/api/call", { method: "POST", body: JSON.stringify({ team: "6병동", title: "고액 대타 텔레그램 연락", detail: "", people: ["다"] }) });
  assert.equal(bad.status, 400);
  assert.equal(scamProblem({ a: ["09:00-18:00", "2026-11-08", "시급 10,030원", "월 2,100,000원"] }), null);
});

test("[CARE-07][CARE-08] a 근로계약서 page has the report form; a report is counted by kind; the operator takes it down with why", async () => {
  readsForget();
  const env = { SHARES: kv(), SHARE_ADMIN_TOKEN: ADMIN };
  const { id, key } = await (await call(env, "/api/sign", { method: "POST", body: JSON.stringify(contract) })).json();
  const page = await (await call(env, `/k/${key}`)).text();
  assert.match(page, /이 링크 신고하기/);
  assert.match(page, /action="\/k\/[A-Za-z0-9]{22}\/report"/);
  assert.match(page, /돈이나 계좌/);
  assert.equal((await call(env, `/k/${key}/report`, form("reason=nope"))).status, 400);
  const sent = await call(env, `/k/${key}/report`, form("reason=scam"));
  assert.equal(sent.status, 200);
  assert.match(await sent.text(), /신고를 받았어요/);
  // The same network again the same day is thanked, not counted twice.
  await call(env, `/k/${key}/report`, form("reason=scam"));
  const bearer = { authorization: `Bearer ${ADMIN}` };
  const list = (await (await call(env, "/api/admin/reports", { headers: bearer })).json()).reports;
  assert.equal(list.length, 1);
  assert.deepEqual([list[0].id, list[0].kind, list[0].name, list[0].count, list[0].reasons.scam], [id, "k", "근로계약서", 1, 1]);
  assert.equal((await call(env, `/api/admin/link/k/${id}`, { method: "DELETE", headers: bearer, body: JSON.stringify({ reason: "입금을 요구하는 가짜 계약서" }) })).status, 204);
  const down = await call(env, `/k/${key}`);
  assert.equal(down.status, 410);
  const text = await down.text();
  assert.match(text, /내려졌어요/);
  assert.match(text, /입금을 요구하는 가짜 계약서/);
  assert.equal((await call(env, `/k/${key}/sign`, form("name=x&agree=1"))).status, 410);
  // The contract itself is gone; the report too.
  assert.equal([...env.SHARES.map.keys()].some((k) => k === `sign:${id}` || k.startsWith("report:")), false);
  assert.equal((await call(env, `/api/admin/link/k/${"x".repeat(22)}`, { method: "DELETE", headers: bearer })).status, 404);
  assert.equal((await call(env, `/api/admin/link/k/${id}`, { method: "DELETE" })).status, 403);
});

test("[CARE-07] every team page carries the form: 결원, 근무 신청, 바꾸기, 병동 표, 출퇴근", async () => {
  readsForget();
  const env = { SHARES: kv(), SHARE_ADMIN_TOKEN: ADMIN };
  const callMade = await (await call(env, "/api/call", { method: "POST", body: JSON.stringify({ team: "6병동", title: "12/7 나이트 대신 들어갈 수 있나요?", detail: "22:00~07:00", people: ["다"] }) })).json();
  const v = callMade.people[0].url.split("/v/")[1];
  assert.match(await (await call(env, `/v/${v}`)).text(), /action="\/v\/[A-Za-z0-9]{22}\/report"/);
  assert.equal((await call(env, `/v/${v}/report`, form("reason=spam"))).status, 200);
  const clock = await (await call(env, "/api/clock", { method: "POST", body: JSON.stringify({ name: "김 하나", place: "밤낮 카페", ttlDays: 30 }) })).json();
  const c = clock.url.split("/c/")[1];
  assert.match(await (await call(env, `/c/${c}`)).text(), /이 링크 신고하기/);
  const bearer = { authorization: `Bearer ${ADMIN}` };
  assert.equal((await call(env, `/api/admin/link/c/${clock.id}`, { method: "DELETE", headers: bearer })).status, 204);
  assert.equal((await call(env, `/c/${c}`)).status, 410);
  const kinds = (await (await call(env, "/api/admin/reports", { headers: bearer })).json()).reports.map((r) => r.kind);
  assert.deepEqual(kinds, ["v"]);
});
