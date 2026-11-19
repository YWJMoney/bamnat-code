// 2.0.0 갈래 N [RVN-06]: 지난 글(?before=)로 읽은 광장 글도 그 글과 답만 다시 읽어(?post=) 창에서 열어요(COMP-01). 신고 이유에
// '성적인 내용'·'불법·위험한 내용'이 따로 있어요(COMP-10). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from } from "./helpers.mjs";

const NOW = Date.parse("2026-11-20T03:00:00Z");
const as = (n) => ({ ...from(n), "x-device": device(n) });
const write = (call, n, body, extra = {}) =>
  call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
const read = (call, n, q = "") => call("GET", `/api/companies/style-shift/setups/room/posts${q}`, undefined, as(n));

test("COMP-01: ?post= reads one top post with its answers only, on a kind of work's plaza", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const a = (await write(call, 1, "야간 끝나고 잠 어떻게 자요?", { kind: "question" })).data.id;
  t.mock.timers.tick(60e3);
  const b = (await write(call, 2, "주간 끝나고 운동해요")).data.id;
  t.mock.timers.tick(60e3);
  await write(call, 3, "낮잠 두 번 나눠 자요", { parent: a });
  t.mock.timers.tick(60e3);
  await write(call, 4, "저는 바로 자요", { parent: b });
  const r = await read(call, 5, `?post=${a}`);
  assert.equal(r.status, 200, JSON.stringify(r.data));
  assert.equal(r.data.thread, a);
  assert.deepEqual(r.data.posts.map((p) => p.body), ["야간 끝나고 잠 어떻게 자요?", "낮잠 두 번 나눠 자요"]);
  assert.equal(r.data.pulse, undefined, "one post's read has no head line");
  assert.equal(r.data.more, undefined);
  // A made-up id, or one with other characters, reads nothing or the usual page.
  assert.deepEqual((await read(call, 5, "?post=pNOTHERE12345")).data.posts, []);
  const odd = await read(call, 5, `?post=${encodeURIComponent(`${a}' OR 1=1`)}`);
  assert.equal(odd.data.thread, undefined);
  assert.equal(odd.data.posts.length, 4, "a bad id is the first page");
  // ?before= wins: a page of older posts, never one thread.
  assert.equal((await read(call, 5, `?post=${a}&before=${new Date(NOW + 3600e3).toISOString()}`)).data.thread, undefined);
});

test("COMP-01: a writer this phone hid stays out of one post's read too", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const a = (await write(call, 1, "4조 2교대 야간 이틀째 어떻게 버텨요?", { kind: "question" })).data.id;
  t.mock.timers.tick(60e3);
  await write(call, 2, "커피는 새벽 3시 전까지만", { parent: a });
  const first = (await read(call, 3, `?post=${a}`)).data.posts;
  const answer = first.find((p) => p.parent === a);
  assert.ok(answer?.author, "the answer names its writer key for hiding");
  const hidden = (await call("GET", `/api/companies/style-shift/setups/room/posts?post=${a}`, undefined,
    { ...as(3), "x-hide": answer.author })).data;
  assert.deepEqual(hidden.posts.map((p) => p.body), ["4조 2교대 야간 이틀째 어떻게 버텨요?"]);
});

test("COMP-10: 성적인 내용·불법·위험한 내용 are reasons of their own, kept as written", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  const { FLAGS } = await import("../src/index.js");
  assert.equal(FLAGS.sexual, "성적인 내용");
  assert.equal(FLAGS.illegal, "불법·위험한 내용");
  const a = (await write(call, 1, "오늘 야간 끝!")).data.id;
  for (const [n, reason] of [[2, "sexual"], [3, "illegal"], [4, "nonsense"]]) {
    const r = await call("POST", `/api/posts/${a}/flag`, { device: device(n), reason }, from(n));
    assert.equal(r.status, 200, JSON.stringify(r.data));
  }
  const kept = await env.DB.prepare("SELECT reason FROM post_flags WHERE post_id = ? ORDER BY reason").bind(a).all();
  assert.deepEqual(kept.results.map((r) => r.reason), ["abuse", "illegal", "sexual"], "an unknown reason stays 욕설·비방");
});
