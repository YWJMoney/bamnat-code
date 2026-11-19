// 2.0.0 갈래 N [RVN-22] 검토 I10bc-01 변화분만 받는 한 번의 동기: the day's /api/digest carries, when the app asks for it (`with`), what it
// used to ask apart afterwards: the questions with a new answer (as /api/inquiries shows them), my room posts once one was answered (as
// /api/posts/mine), the 마을 돌봄 standing, and the company list's two tags. Only what changed since; an app that does not ask gets the
// old answer. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { DIGEST_PARTS } from "../src/index.js";
import { server, device, from, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-20T03:00:00Z");
const mine = (n) => ({ "x-device": device(n) });
const write = (call, n, body, extra = {}) =>
  call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
const digest = async (call, n, since, parts = DIGEST_PARTS.join(",")) =>
  (await call("GET", `/api/digest?since=${new Date(since).toISOString()}${parts ? `&with=${parts}` : ""}`, undefined, mine(n))).data;

test("[RVN-22] I10bc-01 one digest: the answered question as 내 문의 shows it, my posts once answered, standing and the list's tags", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const h = await signIn(call);
  const q = (await call("POST", "/api/inquiries", { v: 1, device: device(1), kind: "bug", text: "알람이 늦게 울려요", info: { app: "2.0.0 (54)" } }, from(1))).data;
  const other = (await call("POST", "/api/inquiries", { v: 1, device: device(1), kind: "idea", text: "주간 색을 바꾸고 싶어요" }, from(1))).data;
  const post = (await write(call, 1, "야간 끝나고 잠 어떻게 자요?", { kind: "question" })).data.id;
  const since = Date.now();
  t.mock.timers.tick(1000);
  await call("POST", `/api/admin/inquiries/${q.id}/reply`, { text: "배터리 설정을 봐 주세요" }, h);
  await write(call, 2, "낮잠 두 번 나눠 자요", { parent: post });
  t.mock.timers.tick(1000);

  const d = await digest(call, 1, since);
  // The answered question only, whole, as /api/inquiries has it (the other, unanswered one is not sent again).
  assert.deepEqual(d.inquiries.map((i) => i.id), [q.id]);
  const list = (await call("GET", "/api/inquiries", undefined, mine(1))).data.inquiries;
  assert.deepEqual(d.inquiries[0], list.find((i) => i.id === q.id));
  assert.ok(list.some((i) => i.id === other.id));
  // My room posts, as 내 글 reads them, since one was answered.
  assert.deepEqual(d.posts, (await call("GET", "/api/posts/mine", undefined, mine(1))).data.items);
  assert.equal(d.posts.find((p) => p.id === post).replies, 1);
  // The 마을 돌봄 standing as the village reads it, and the list's two tags.
  assert.deepEqual(d.standing, (await call("GET", "/api/community/standing", undefined, mine(1))).data);
  assert.equal(typeof d.companies.names, "string");
  assert.equal(typeof d.companies.counts, "string");
  const names = await call("GET", "/api/companies/all?part=names");
  assert.equal(names.headers.get("etag"), d.companies.names);

  // Nothing changed since: no questions, no posts (the standing and tags are always small).
  const after = await digest(call, 1, Date.parse(d.now));
  assert.deepEqual(after.inquiries, []);
  assert.equal(after.posts, undefined);
  // Someone else's phone hears nothing of mine.
  const two = await digest(call, 2, since);
  assert.deepEqual(two.inquiries, []);
  assert.equal(two.posts, undefined, "phone 2 answered, it was not answered");
});

test("[RVN-22] I10bc-01 an app that does not ask gets the digest it always got; an unknown part is left out", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const since = Date.now();
  t.mock.timers.tick(1000);
  const old = await digest(call, 1, since, "");
  for (const k of ["inquiries", "posts", "standing", "companies"]) assert.equal(old[k], undefined, k);
  const odd = await digest(call, 1, since, "secrets,standing");
  assert.equal(odd.secrets, undefined);
  assert.ok(odd.standing);
});
