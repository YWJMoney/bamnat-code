// 1.39 [CARE-07~09] 팀·사장님 링크(병동 표·근무 신청·결원·바꾸기·근로계약서·출퇴근): 페이지마다 신고 양식과 '돈을 요구하지 않아요'
// 한 줄, 운영자가 종류별로 내리기(내용은 바로 지우고 이유와 함께 410), 전화·링크·계좌·메신저 옮기기는 받지 않기(사기 막기).
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { readsForget, scamFind, scamProblem } from "../src/index.js";

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
const APP139 = { "x-bamnat-app": "53" };
const form = (body) => ({ method: "POST", body, headers: { "content-type": "application/x-www-form-urlencoded" } });
const contract = { place: "밤낮 카페", staff: "김 하나", title: "단시간 근로자 근로계약서", days: 7,
  items: [{ t: "근로 시작일", b: "2026년 11월 9일부터" }, { t: "임금", b: "시급 10,320원\n지급일: 매월 10일" }, { t: "사업자", b: "123-45-67890" }] };

test("[CARE-09] phone numbers, links, account numbers and moves to a messenger are refused; what the app writes goes", async () => {
  readsForget();
  const env = { SHARES: kv() };
  assert.equal((await call(env, "/api/sign", { method: "POST", body: JSON.stringify(contract) })).status, 201);
  for (const [b, why] of [["보증금 30만 원은 110-123-456789로 보내 주세요", "account"], ["문의 010-1234-5678", "phone"],
    ["자세한 건 https://bit.ly/x", "link"], ["오픈채팅으로 오세요", "messenger"]]) {
    const r = await call(env, "/api/sign", { method: "POST", headers: APP139, body: JSON.stringify({ ...contract, items: [...contract.items, { t: "안내", b }] }) });
    assert.equal(r.status, 400, b);
    assert.deepEqual(await r.json(), { error: "contact", why, text: b.slice(0, 40), key: "b" });
  }
  const bad = await call(env, "/api/call", { method: "POST", headers: APP139, body: JSON.stringify({ team: "6병동", title: "고액 대타 텔레그램 연락", detail: "", people: ["다"] }) });
  assert.equal(bad.status, 400);
  assert.equal(scamProblem({ a: ["09:00-18:00", "2026-11-08", "시급 10,030원", "월 2,100,000원"] }), null);
});

test("[CARE-09] 1.38 이하 앱(헤더 없음·52)은 예전처럼 받아요(사용자 2026-11-08 '1.39 앱에서만')", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const body = JSON.stringify({ ...contract, items: [...contract.items, { t: "안내", b: "문의 010-1234-5678" }] });
  assert.equal((await call(env, "/api/sign", { method: "POST", body })).status, 201);
  assert.equal((await call(env, "/api/sign", { method: "POST", headers: { "x-bamnat-app": "52" }, body })).status, 201);
  assert.equal((await call(env, "/api/call", { method: "POST", body: JSON.stringify({ team: "6병동", title: "고액 대타 텔레그램 연락", detail: "", people: ["다"] }) })).status, 201);
  assert.equal((await call(env, "/api/sign", { method: "POST", headers: APP139, body })).status, 400);
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
  // The operator reads the words before deciding; no key or token comes with them.
  const peek = await (await call(env, `/api/admin/link/k/${id}`, { headers: bearer })).json();
  assert.match(peek.text, /단시간 근로자 근로계약서/);
  assert.match(peek.text, /시급 10,320원/);
  assert.equal(peek.text.includes(key), false);
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

// 7차 PC: 결원 링크가 '인터넷이 될 때 다시'라고만 했어요. 거절에 어느 글·어느 칸인지 실어 앱이 '유나 010-… 이름에서 빼 주세요'라고 말해요.
test("[CARE-09] 7차: the refusal names the line and its field (whose name), for every team link kind", async () => {
  readsForget();
  const env = { SHARES: kv() };
  assert.deepEqual(scamFind({ team: "6병동", people: ["가", "유나 010-1234-5678"] }), { why: "phone", text: "유나 010-1234-5678", key: "people" });
  assert.deepEqual(scamFind({ title: "  문의 https://bit.ly/x  " }), { why: "link", text: "문의 https://bit.ly/x", key: "title" });
  assert.equal(scamFind({ a: ["09:00-18:00", "시급 10,030원"] }), null);
  assert.equal(scamFind("x".repeat(60) + " 010-1234-5678").text.length, 40);
  const r = await call(env, "/api/call", { method: "POST", headers: APP139,
    body: JSON.stringify({ team: "밤낮 카페", title: "12/7 대신 들어갈 수 있나요?", detail: "", people: ["가", "유나 010-1234-5678"], hours: 24 }) });
  assert.equal(r.status, 400);
  assert.deepEqual(await r.json(), { error: "contact", why: "phone", text: "유나 010-1234-5678", key: "people" });
  const ask = await call(env, "/api/ask", { method: "POST", headers: APP139, body: JSON.stringify({ team: "6병동 010-1234-5678", month: "2026-12", names: ["가"] }) });
  assert.equal(ask.status, 400);
  assert.equal((await ask.json()).key, "team");
  const clock = await call(env, "/api/clock", { method: "POST", headers: APP139, body: JSON.stringify({ name: "김 하나 010-1234-5678", place: "밤낮 카페", ttlDays: 30 }) });
  assert.equal((await clock.json()).key, "name");
});

// 7차 PC ②: 개인 근무표 링크(/s/)의 제목·근무 이름도 걸러요. 메모·급여 이름은 고른 사람에게 보내는 내 글이라 그대로예요.
const schedule = { v: 1, title: "3병동 유나", ttlDays: 30,
  types: [{ id: 1, name: "주간", short: "주", color: "#1E88E5", time: "07:00-19:00" }, { id: 2, name: "휴무", short: "휴", color: "#9E9E9E", off: true }],
  days: [{ date: "2026-11-01", type: 1 }, { date: "2026-11-02", type: 2 }] };
const share = (env, method, path, body, headers = {}) => worker.fetch(new Request(`https://share.example${path}`, { method,
  headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.52", ...headers }, body: JSON.stringify(body) }), env, { waitUntil() {} });

test("[CARE-09] 7차: a schedule link (/s/) with a phone number in its title or a shift name is refused for 1.39 apps", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const bad = await share(env, "POST", "/api/share", { ...schedule, title: "3병동 유나 010-1234-5678의 근무표" }, APP139);
  assert.equal(bad.status, 400);
  assert.deepEqual(await bad.json(), { error: "contact", why: "phone", text: "3병동 유나 010-1234-5678의 근무표", key: "title" });
  const shift = await share(env, "POST", "/api/share", { ...schedule, types: [{ ...schedule.types[0], name: "주간 오픈채팅" }, schedule.types[1]] }, APP139);
  assert.equal(shift.status, 400);
  assert.equal((await shift.json()).key, "type");
  // 1.38 이하 앱은 예전처럼 받아요.
  assert.equal((await share(env, "POST", "/api/share", { ...schedule, title: "3병동 유나 010-1234-5678의 근무표" })).status, 201);
  // A link made before 1.39 with a number in its title keeps updating by itself while the title stays as it is.
  readsForget();
  const old = await (await share(env, "POST", "/api/share", { ...schedule, title: "유나 010-1234-5678" })).json();
  const keep = await share(env, "PUT", `/api/share/${old.id}`, { ...schedule, title: "유나 010-1234-5678", days: [{ date: "2026-11-01", type: 2 }] },
    { ...APP139, "x-delete-token": old.deleteToken });
  assert.equal(keep.status, 200);
  const renamed = await share(env, "PUT", `/api/share/${old.id}`, { ...schedule, title: "유나 https://bit.ly/x" }, { ...APP139, "x-delete-token": old.deleteToken });
  assert.equal(renamed.status, 400);
  assert.equal((await renamed.json()).why, "link");
  // A clean title passes for a 1.39 app.
  readsForget();
  assert.equal((await share(env, "POST", "/api/share", schedule, APP139)).status, 201);
});
