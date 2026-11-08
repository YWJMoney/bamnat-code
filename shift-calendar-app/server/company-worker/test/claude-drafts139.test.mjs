// 1.39 [ADM3-49] Claude 초안(실서버 둘러보기 B1·C2, 사용자 11-08 '클로드 열쇠로 제보 검토 보상 초안까지'): 운영 열쇠는 검토함·회사·보상을
// 읽고 추천만 적어요. 채택·거절·지급·합치기는 열쇠로 막혀 있고, 사람이 정하면 추천과 같았는지 남아 조종석 일치율이 돼요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, signIn, report, from } from "./helpers.mjs";

async function withKey(call) {
  const owner = await signIn(call);
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  return { owner, claude: { authorization: `Bearer ${made.key}`, "x-admin": "1" } };
}

const REWARD = "w" + "A".repeat(22);

test("[ADM3-49] the key reads 검토함·회사·보상 and writes a 추천, but adopting, paying and merging stay with a person", async () => {
  const { call, DB } = server();
  const { owner, claude } = await withKey(call);
  await call("POST", "/api/reports", report(1, { company: "lg전자 칠러공장" }), from(1));
  await call("POST", "/api/reports", report(2, { company: "LG전자(칠러)" }), from(2));
  DB.db.prepare("INSERT INTO rewards (id, report_id, rule_id, device, months, status, created_at) VALUES (?, 'rp', '', 'dev', 1, 'owed', ?)").run(REWARD, Date.now());
  for (const path of ["/api/admin/queue", "/api/admin/companies?q=", "/api/admin/companies/similar", "/api/admin/versions", "/api/admin/rewards", "/api/admin/drafts"]) {
    assert.equal((await call("GET", path, undefined, claude)).status, 200, path);
  }
  const [g] = (await call("GET", "/api/admin/queue", undefined, claude)).data.groups;
  const [pair] = (await call("GET", "/api/admin/companies/similar", undefined, claude)).data.pairs;
  // What decides is not for the key.
  for (const [path, body] of [[`/api/admin/groups/${g.company.id}/${g.setup}/adopt`, {}], [`/api/admin/groups/${g.company.id}/${g.setup}/reject`, { reason: "unclear" }],
    [`/api/admin/rewards/${REWARD}`, { status: "paid" }], [`/api/admin/companies/${pair.from.id}/merge`, { into: pair.into.id }],
    ["/api/admin/companies/similar/skip", { a: pair.from.id, b: pair.into.id }]]) {
    assert.equal((await call("POST", path, body, claude)).status, 403, path);
  }
  // A 추천 with its public sources; an http address or a reject without a reason is refused.
  const draft = { kind: "group", company: g.company.id, grp: g.setup, pick: "adopt", note: "회사 채용 공고의 4조 2교대와 같아요. 명세서와 맞춰 본 달이 있어요.",
    sources: ["https://example.com/notice/123"] };
  assert.equal((await call("POST", "/api/admin/drafts", { ...draft, sources: ["http://example.com"] }, claude)).status, 400);
  assert.equal((await call("POST", "/api/admin/drafts", { ...draft, pick: "reject" }, claude)).status, 400);
  assert.equal((await call("POST", "/api/admin/drafts", { ...draft, grp: "0123456789abcdef" }, claude)).status, 404, "no such group waiting");
  const wrote = await call("POST", "/api/admin/drafts", draft, claude);
  assert.equal(wrote.status, 200, JSON.stringify(wrote.data));
  assert.equal(wrote.data.draft.by, "Claude");
  const q = (await call("GET", "/api/admin/queue", undefined, owner)).data.groups.find((x) => x.setup === g.setup);
  assert.equal(q.draft.pick, "adopt");
  assert.deepEqual(q.draft.sources, ["https://example.com/notice/123"]);
  // A person adopts: the same as the 추천, written down, and the draft is gone.
  assert.equal((await call("POST", `/api/admin/groups/${g.company.id}/${g.setup}/adopt`, {}, owner)).status, 200);
  assert.equal(DB.db.prepare("SELECT detail FROM audit WHERE action = 'claude_draft_done'").get().detail, "같음");
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM loop_drafts").get().n, 0);
  // A reward: Claude says give, a person cancels: 다름.
  assert.equal((await call("POST", "/api/admin/drafts", { kind: "reward", id: REWARD, pick: "paid", months: 3, note: "채택된 회사 방식이라 3개월" }, claude)).status, 200);
  assert.equal((await call("GET", "/api/admin/rewards", undefined, owner)).data.rewards.find((w) => w.id === REWARD).draft.months, 3);
  assert.equal((await call("POST", `/api/admin/rewards/${REWARD}`, { status: "void" }, owner)).status, 200);
  // A merge: shown on the pair, a person says 다른 회사예요.
  assert.equal((await call("POST", "/api/admin/drafts", { kind: "merge", from: pair.from.id, into: pair.into.id, pick: "merge", note: "같은 공장 이름이에요" }, claude)).status, 200);
  assert.equal((await call("GET", "/api/admin/companies/similar", undefined, owner)).data.pairs[0].draft.pick, "merge");
  assert.equal((await call("POST", "/api/admin/companies/similar/skip", { a: pair.into.id, b: pair.from.id }, owner)).status, 200);
  const stats = (await call("GET", "/api/admin/cockpit", undefined, owner)).data.drafts;
  assert.equal(stats.done, 3);
  assert.equal(stats.same, 1);
  assert.equal(stats.agree, 33);
  assert.equal(stats.gate.ready, false);
  assert.equal(stats.open, 0);
});

test("[ADM3-49] an unknown kind is refused, and a 추천 can be taken back", async () => {
  const { call, DB } = server();
  const { claude } = await withKey(call);
  DB.db.prepare("INSERT INTO rewards (id, report_id, rule_id, device, months, status, created_at) VALUES (?, 'rp', '', 'dev', 1, 'owed', ?)").run(REWARD, Date.now());
  assert.equal((await call("POST", "/api/admin/drafts", { kind: "reward", id: REWARD, pick: "paid", note: "드려요" }, claude)).status, 200);
  assert.equal((await call("POST", "/api/admin/drafts", { kind: "nope", id: REWARD, pick: "paid", note: "x" }, claude)).status, 400);
  // Taking it back.
  assert.equal((await call("POST", "/api/admin/drafts", { kind: "reward", id: REWARD, pick: null }, claude)).data.draft, null);
  assert.equal((await call("GET", "/api/admin/drafts", undefined, claude)).data.drafts.length, 0);
});
