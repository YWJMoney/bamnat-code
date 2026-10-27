// SP1 1.30 [SPD-01]~[SPD-09] 1.30 묶음 SP1 (사용자 2026-10-27: "여전히 조회하는 로딩이 너무 느리다. 획기적으로 속도를 빠르게 할 방법.
// 아주 중요한 문제"): the plaza's reads in a few rounds give the answers the one-version reads gave; a page, its room and talk in
// one answer; every company in one list with an ETag; the page with a join; D1 sessions and their bookmark; short copies of
// the public lists; the salt read once. Companies are made up. `npm test` (the round budgets: perf-rounds.test.mjs).
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { statesFor, stateOne, opinionsOne, stableOne, representativeOne, readStates, representatives, stableFacts } from "../src/index.js";
import { spPairChunks, spInMarks } from "../src/read-fast.js";
import { server, report, setup, device, from, signIn, ORIGIN } from "./helpers.mjs";

function cycle(steps) {
  const s = setup();
  delete s.pay;
  s.rotation = { anchor: "2026-10-01", steps };
  return s;
}
const LONG = ["주간", "주간", "주간", "휴무", "휴무", "휴무", "야간", "야간", "야간", "휴무", "휴무", "휴무"];

/** [count] companies with versions sent, checked on pay slips over two months, voted on with reasons, used, named and talked about. */
async function seed(call, DB, count = 4) {
  let n = 10;
  const out = [];
  for (let i = 1; i <= count; i++) {
    const name = `가상화학${i}`;
    const a = (await call("POST", "/api/reports", report(n++, { company: name, setup: setup(), verified: i % 2 === 0 }), from(n))).data;
    await call("POST", "/api/reports", report(n++, { company: name, setup: setup() }), from(n));
    const b = (await call("POST", "/api/reports", report(n++, { company: name, dept: "온산공장", setup: cycle(LONG), verified: true }), from(n))).data;
    const s2 = setup();
    s2.shiftTypes[0].start = "08:00";
    const c2 = (await call("POST", "/api/reports", report(n++, { company: name, setup: s2 }), from(n))).data;
    for (let k = 0; k < 4; k++) {
      await call("POST", "/api/votes", { device: device(n++), company: a.company.id, setup: a.setup, ok: k !== 2, verified: k === 1, why: k === 2 ? "time" : undefined }, from(n));
    }
    for (let k = 0; k < 3; k++) {
      await call("POST", "/api/votes", { device: device(n++), company: a.company.id, setup: c2.setup, ok: k === 0, why: k ? ["order", "pay"][k - 1] : undefined }, from(n));
    }
    await call("POST", "/api/votes", { device: device(n++), company: a.company.id, setup: b.setup, ok: true, verified: true }, from(n));
    for (let k = 0; k < 3; k++) await call("POST", "/api/uses", { device: device(n++), company: a.company.id, setup: a.setup, cal: "main" }, from(n));
    out.push({ id: a.company.id, grps: [a.setup, b.setup, c2.setup], depts: { [a.setup]: "", [b.setup]: "온산공장", [c2.setup]: "" } });
  }
  await DB.prepare("INSERT INTO nicks (device, nick, norm, at) SELECT device, 'n' || substr(device, -4), 'n' || device, 1 FROM reports").run();
  // Pay-slip checks a month and more apart, from other networks: 두 달 이상 확인됨.
  await DB.prepare("UPDATE votes SET verified = 1, at = at - 40 * 86400000 WHERE ok = 1 AND rowid % 2 = 0").run();
  await DB.prepare("UPDATE reports SET verified = 1 WHERE rowid % 3 = 0").run();
  await call("POST", "/api/posts", { device: device(n++), company: out[0].id, setup: out[0].grps[0], body: "맞아요 이 시간" }, from(n));
  return { list: out, n };
}

test("[SPD-01] states, opinions, 대표 reports and settled marks for many versions at once say what the one-version reads said", async () => {
  const { call, env, DB } = server();
  const { list } = await seed(call, DB);
  const pairs = list.flatMap((c) => c.grps.map((grp, i) => ({ company: c.id, grp, adopted: i === 0 })));
  const many = await statesFor(env, pairs, 3);
  const facts = await readStates(env, pairs);
  const reps = await representatives(env, pairs);
  const now = Date.now();
  let checkedLong = 0;
  for (const p of pairs) {
    const one = await stateOne(env, p.company, p.grp, 3, p.adopted);
    assert.deepEqual(many.get(`${p.company}\u0001${p.grp}`), one, p.grp);
    if (one.checkedLong) checkedLong++;
    for (const dev of [null, (await DB.prepare("SELECT device FROM votes WHERE company_id = ? AND grp = ? LIMIT 1").bind(p.company, p.grp).first())?.device]) {
      assert.deepEqual(facts.opinions(p.company, p.grp, dev), await opinionsOne(env, p.company, p.grp, dev));
    }
    assert.deepEqual(reps.get(`${p.company}\u0001${p.grp}`), { ...(await representativeOne(env, p.company, p.grp)), sp_rank: 1 });
    const stable = await stableFacts(env, p.company);
    const dept = list.find((c) => c.id === p.company).depts[p.grp];
    for (const at of [now, now + 40 * 86400000]) assert.deepEqual(stable(dept, p.grp, facts.uses(p.company, p.grp), at), await stableOne(env, p.company, dept, p.grp, at));
  }
  assert.ok(checkedLong > 0, "the set has a version checked over two months");
  assert.ok([...many.values()].some((s) => s.by.length > 0), "and names");
});

test("[SPD-01] many pairs go in statements of at most 90 values each, and still read every one", async () => {
  const pairs = Array.from({ length: 130 }, (_, i) => ({ company: `c${i % 70}`, grp: `g${i}` }));
  const chunks = spPairChunks(pairs);
  assert.ok(chunks.length >= 3);
  for (const c of chunks) assert.ok(c.companies.length + c.grps.length <= 90);
  assert.equal(chunks.reduce((n, c) => n + c.grps.length, 0), 130);
  const { values, cs, gs } = spInMarks({ companies: ["a", "b"], grps: ["x"] });
  assert.deepEqual([values, cs, gs], [["a", "b", "x"], "?1, ?2", "?3"]);
  const { call, env, DB } = server();
  const { list } = await seed(call, DB, 2);
  // Real pairs among 120 made-up ones: three statements of each kind, the same states.
  const real = list.flatMap((c) => c.grps.map((grp) => ({ company: c.id, grp })));
  const filler = Array.from({ length: 120 }, (_, i) => ({ company: `zz${String(i).padStart(10, "0")}`, grp: `${i}`.padStart(16, "0") }));
  const many = await statesFor(env, [...filler, ...real], 3);
  for (const p of real) assert.deepEqual(many.get(`${p.company}\u0001${p.grp}`), await stateOne(env, p.company, p.grp, 3, false));
});

test("[SPD-05] ?with=room,talk: a member's page with the room and its talk as their own requests answer them; anyone else the page", async () => {
  const { call, DB } = server();
  const { list, n } = await seed(call, DB, 2);
  const id = list[0].id;
  const dev = device(n + 1);
  await call("POST", "/api/members", { device: dev, company: id, cal: "main" }, from(n + 1));
  for (let k = 0; k < 3; k++) await call("POST", "/api/posts", { device: dev, company: id, setup: "room", body: `방 글 ${k}` }, from(n + 1));
  const me = { "x-device": dev };
  const page = await call("GET", `/api/companies/${id}`, undefined, me);
  const both = await call("GET", `/api/companies/${id}?with=room,talk`, undefined, me);
  const room = await call("GET", `/api/companies/${id}/room`, undefined, me);
  const talk = await call("GET", `/api/companies/${id}/setups/room/posts`, undefined, me);
  const sealed = (o) => JSON.parse(JSON.stringify(o), (k, v) => (k === "author" ? "*" : v));
  const { room: r, talk: t, ...rest } = both.data;
  assert.deepEqual(rest, page.data, "the page as before; an older app reads it the same");
  assert.deepEqual(r, room.data);
  assert.deepEqual(sealed(t), sealed(talk.data));
  assert.equal(t.posts.length, 3);
  const justRoom = await call("GET", `/api/companies/${id}?with=room`, undefined, me);
  assert.ok(justRoom.data.room && !("talk" in justRoom.data));
  // Not a member, or no phone: the page only.
  for (const h of [{ "x-device": device(n + 2) }, {}]) {
    const other = await call("GET", `/api/companies/${id}?with=room,talk`, undefined, h);
    assert.equal(other.status, 200);
    assert.ok(!("room" in other.data) && !("talk" in other.data));
    assert.equal(other.data.member, false);
  }
});

test("[SPD-08] joining with ?with=page answers with the page as a member reads it; without it, as before", async () => {
  const { call, DB } = server();
  const { list, n } = await seed(call, DB, 1);
  const id = list[0].id;
  const plain = await call("POST", "/api/members", { device: device(n + 1), company: id, cal: "main" }, from(n + 1));
  assert.equal(plain.status, 200);
  assert.ok(!("page" in plain.data));
  const res = await call("POST", "/api/members?with=page", { device: device(n + 2), company: id, cal: "main" }, from(n + 2));
  assert.equal(res.status, 200);
  assert.equal(res.data.joined, true);
  assert.equal(res.data.page.member, true);
  assert.equal(res.data.page.id, id);
  const read = await call("GET", `/api/companies/${id}`, undefined, { "x-device": device(n + 2) });
  assert.deepEqual(res.data.page, read.data);
  assert.ok(res.data.page.setups.some((s) => s.setup.pay), "with its pay way");
  const inBody = await call("POST", "/api/members", { device: device(n + 3), company: id, cal: "main", with: "page" }, from(n + 3));
  assert.equal(inBody.data.page.member, true);
  // A join that waits (another company within 30 days) has no page.
  const other = (await call("POST", "/api/reports", report(n + 4, { company: "가상물류" }), from(n + 4))).data.company.id;
  const wait = await call("POST", "/api/members?with=page", { device: device(n + 2), company: other, cal: "main" }, from(n + 2));
  assert.equal(wait.status, 409);
  assert.ok(!("page" in wait.data));
});

test("[SPD-06] every company in one list with its 대표 주기, other names and readings; 304 when the app has it; nothing about a phone", async () => {
  const { call, DB } = server();
  const { list } = await seed(call, DB, 3);
  await DB.prepare("INSERT INTO aliases (norm, company_id, name) VALUES ('가상케미칼', ?, '가상케미칼')").bind(list[0].id).run();
  const hidden = (await call("POST", "/api/reports", report(90, { company: "가상숨김" }), from(90))).data.company.id;
  await DB.prepare("UPDATE companies SET hidden = 1 WHERE id = ?").bind(hidden).run();
  await call("POST", "/api/reports", report(91, { topic: true, job: "construction" }), from(91));
  const res = await call("GET", "/api/companies/all");
  assert.equal(res.status, 200);
  assert.match(res.headers.get("cache-control"), /public, max-age=300/);
  const tag = res.headers.get("etag");
  assert.match(tag, /^"[0-9a-f]{24}"$/);
  const all = res.data.companies;
  assert.deepEqual(all.map((c) => c.id).sort(), list.map((c) => c.id).sort(), "no hidden company, no job topic");
  assert.equal(res.data.n, 3);
  const first = all.find((c) => c.id === list[0].id);
  assert.deepEqual(first.aliases, ["가상케미칼"]);
  assert.equal(first.said, "가상화학1");
  assert.equal(first.cho, "ㄱㅅㅎㅎ1");
  // The same line as the search gives.
  const found = (await call("GET", "/api/companies?q=" + encodeURIComponent("가상화학1"))).data.companies.find((c) => c.id === list[0].id);
  for (const k of ["lead", "users", "sites", "adopted", "pending"]) assert.deepEqual(first[k], found[k], k);
  assert.doesNotMatch(res.text, /test-device|device/);
  assert.ok(res.text.length / all.length < 600, `about ${Math.round(res.text.length / all.length)} bytes a company`);
  const again = await call("GET", "/api/companies/all", undefined, { "if-none-match": tag });
  assert.equal(again.status, 304);
  assert.equal(again.text, "");
  assert.equal(again.headers.get("etag"), tag);
  assert.equal((await call("GET", "/api/companies/all", undefined, { "if-none-match": '"0000"' })).status, 200);
});

test("[SPD-02] reads and writes of the plaza go through a D1 session: the nearest copy for anyone, the primary after a write, the bookmark back", async () => {
  const { call, DB } = server();
  const { list } = await seed(call, DB, 1);
  DB.sessions.length = 0;
  const board = await call("GET", "/api/board?tab=voting");
  assert.deepEqual(DB.sessions, ["first-unconstrained"]);
  assert.match(board.headers.get("x-d1-bookmark"), /^bm-\d+$/);
  assert.equal(board.headers.get("x-d1-region"), "APAC");
  // An app before 1.30 reads its own vote with X-Device and no bookmark: the primary.
  DB.sessions.length = 0;
  await call("GET", `/api/companies/${list[0].id}`, undefined, { "x-device": device(500) });
  assert.deepEqual(DB.sessions, ["first-primary"]);
  // A write starts on the primary; its bookmark makes the next read as fresh.
  DB.sessions.length = 0;
  const vote = await call("POST", "/api/votes", { device: device(501), company: list[0].id, setup: list[0].grps[0], ok: true }, from(201));
  const mark = vote.headers.get("x-d1-bookmark");
  assert.match(mark, /^bm-\d+$/);
  await call("GET", `/api/companies/${list[0].id}`, undefined, { "x-device": device(501), "x-d1-bookmark": mark });
  assert.deepEqual(DB.sessions, ["first-primary", mark]);
  // After its write the app asks past copies: the primary.
  DB.sessions.length = 0;
  await call("GET", "/api/board?tab=adopted", undefined, { "cache-control": "no-cache" });
  await call("GET", "/api/board?tab=adopted", undefined, { "x-d1-bookmark": "not a bookmark!" });
  assert.deepEqual(DB.sessions, ["first-primary", "first-unconstrained"]);
  // The developer app and the rest read as before.
  DB.sessions.length = 0;
  const headers = await signIn(call);
  await call("GET", "/api/admin/badges", undefined, headers);
  await call("GET", "/api/config");
  assert.deepEqual(DB.sessions, []);
});

test("[SPD-04] the public lists are kept a short while: a write here or no-cache reads afresh; at the edge, no-cache asks past the copy", async () => {
  const { call, DB } = server();
  const { list } = await seed(call, DB, 2);
  const one = await call("GET", "/api/board?tab=voting");
  assert.match(one.headers.get("cache-control"), /public, max-age=20, stale-while-revalidate=120/);
  // A change the Worker did not make waits for the copy to end, unless the app asks past it.
  await DB.prepare("UPDATE companies SET hidden = 1 WHERE id = ?").bind(list[0].id).run();
  assert.deepEqual((await call("GET", "/api/board?tab=voting")).data, one.data);
  const fresh = await call("GET", "/api/board?tab=voting", undefined, { "cache-control": "no-cache" });
  assert.ok(!fresh.data.items.some((i) => i.company.id === list[0].id));
  // A write through this Worker forgets the copies.
  await DB.prepare("UPDATE companies SET hidden = 0 WHERE id = ?").bind(list[0].id).run();
  await call("POST", "/api/votes", { device: device(700), company: list[1].id, setup: list[1].grps[2], ok: true }, from(70));
  const after = await call("GET", "/api/board?tab=voting");
  assert.ok(after.data.items.some((i) => i.company.id === list[0].id));
  assert.match((await call("GET", "/api/plazas")).headers.get("cache-control"), /max-age=300/);
  assert.match((await call("GET", "/api/terms")).headers.get("cache-control"), /max-age=600/);
  // At the edge (a custom domain's Cache API).
  const kept = new Map();
  globalThis.caches = { default: { match: async (r) => kept.get(r.url)?.clone(), put: async (r, res) => { kept.set(r.url, res); } } };
  try {
    const a = await call("GET", "/api/board?tab=adopted&from=0");
    assert.equal(kept.size, 1);
    await DB.prepare("UPDATE rules SET withdrawn = 1").run();
    assert.deepEqual((await call("GET", "/api/board?from=0&tab=adopted")).data, a.data, "the same copy, the query in another order");
    const b = await call("GET", "/api/board?tab=adopted&from=0", undefined, { "cache-control": "no-cache" });
    assert.equal(b.data.items.length, 0);
    assert.equal((await call("GET", "/api/board?tab=adopted&from=0")).data.items.length, 0, "the fresh answer is the copy now");
  } finally {
    delete globalThis.caches;
  }
});

test("[SPD-03] without RATE_SALT the server's own salt is read once per Worker, and a public read writes nothing", async () => {
  const { call, DB, env } = server();
  delete env.RATE_SALT;
  await call("GET", "/api/companies?q=" + encodeURIComponent("가상화학"));
  const salt = DB.db.prepare("SELECT value FROM settings WHERE key = 'salt'").get().value;
  let reads = 0;
  let writes = 0;
  const prepare = DB.prepare;
  DB.prepare = function (sql) {
    if (/'salt'/.test(sql)) /^\s*INSERT/i.test(sql) ? writes++ : reads++;
    return prepare.call(this, sql);
  };
  try {
    for (let i = 0; i < 3; i++) await call("GET", "/api/board?tab=adopted&from=" + i);
  } finally {
    DB.prepare = prepare;
  }
  assert.deepEqual([reads, writes], [0, 0]);
  assert.equal(DB.db.prepare("SELECT value FROM settings WHERE key = 'salt'").get().value, salt);
});

test("[SPD-03] history's and talk's lookups by rule and by report use their indexes", async () => {
  const { call, DB } = server();
  await call("GET", "/api/terms");
  const plan = (sql) => DB.db.prepare(`EXPLAIN QUERY PLAN ${sql}`).all().map((r) => r.detail).join(" | ");
  assert.match(plan("SELECT COUNT(*) FROM reports s WHERE s.rule_id = 'x'"), /reports_rule/);
  assert.match(plan("SELECT r.id FROM reports r WHERE r.id IN (SELECT report_id FROM posts WHERE company_id = 'a' AND grp = 'b' AND report_id IS NOT NULL)"), /posts_talk|posts_report/);
  assert.match(plan("SELECT 1 FROM posts WHERE report_id = 'x'"), /posts_report/);
  // votes, uses and members need no new index: their keys (and members_company) already find the rows.
  assert.match(plan("SELECT device FROM votes WHERE company_id = 'a' AND grp = 'b'"), /sqlite_autoindex_votes/);
  assert.match(plan("SELECT COUNT(*) FROM uses WHERE company_id = 'a' AND grp = 'b'"), /sqlite_autoindex_uses/);
  assert.match(plan("SELECT 1 FROM members WHERE device = 'd' AND company_id = 'a' AND left = 0"), /SEARCH members USING/);
});

test("[SPD-09] activity and history give what they gave, now read together", async () => {
  const { call, DB } = server();
  const { list, n } = await seed(call, DB, 2);
  const me = { "x-device": device(10) };
  const act = await call("GET", "/api/activity", undefined, me);
  assert.equal(act.status, 200);
  const r = act.data.reports[0];
  const page = (await call("GET", `/api/companies/${r.company.id}`)).data;
  const s = page.setups.find((x) => x.id === r.setup);
  assert.deepEqual([r.persons, r.bad, r.uses], [s.persons, s.bad, s.uses]);
  const hist = await call("GET", `/api/companies/${list[0].id}/history?setup=${list[0].grps[0]}`, undefined, { "x-device": device(n) });
  assert.equal(hist.status, 200);
  assert.equal(hist.data.versions.length, 1);
  assert.equal(hist.data.versions[0].senders, 2);
  assert.equal((await call("GET", `/api/companies/${list[0].id}/history?setup=0000000000000000`)).status, 404);
});

test("[SPD-02] without D1 sessions (an older runtime, a local run) the reads answer as before, with no bookmark", async () => {
  const { env } = server();
  const plain = { ...env, DB: { ...env.DB, withSession: undefined } };
  const res = await worker.fetch(new Request(`${ORIGIN}/api/board?tab=voting`, { headers: { "cf-connecting-ip": "203.0.113.9" } }), plain);
  assert.equal(res.status, 200);
  assert.equal(res.headers.get("x-d1-bookmark"), null);
});
