// SP1 1.30 [SPD-07] 1.30 묶음 SP1 (사용자 2026-10-27: "여전히 조회하는 로딩이 너무 느리다 … 아주 중요한 문제"): how many times each read
// of the app waits for D1, held to a budget so a later change cannot quietly go back to one version after another. Every
// statement waits [MS] here, as a D1 a region away does; statements sent together wait once, so the time over [MS] is the
// number of rounds. Each path is asked twice and the shorter counts (a busy test machine only adds time). D1 binds at most 100
// values a statement; a statement with more fails the test. Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { server, report, setup, device, from } from "./helpers.mjs";

const MS = 25;

/** The worker's DB with every statement counted, its binds checked and [MS] of wait each; sessions go through it too. */
function timed(DB) {
  const counter = { n: 0 };
  const wait = () => new Promise((r) => setTimeout(r, MS));
  const wrap = (s) => ({
    ...s,
    plain: s,
    bind: (...v) => {
      assert.ok(v.length <= 100, `D1 binds at most 100 values: ${v.length} in ${s.sql?.slice(0, 80)}`);
      return wrap(s.bind(...v));
    },
    first: async (c) => { counter.n++; await wait(); return s.first(c); },
    all: async () => { counter.n++; await wait(); return s.all(); },
    run: async () => { counter.n++; await wait(); return s.run(); },
  });
  // A batch is one statement to D1: its parts run as they are, not counted again.
  const db = { ...DB, prepare: (sql) => wrap(DB.prepare(sql)), batch: async (l) => { counter.n++; await wait(); return DB.batch(l.map((x) => x.plain ?? x)); } };
  return { counter, DB: db };
}

function cycle(steps) {
  const s = setup();
  delete s.pay;
  s.rotation = { anchor: "2026-10-01", steps };
  return s;
}
const LONG = ["주간", "주간", "주간", "휴무", "휴무", "휴무", "야간", "야간", "야간", "휴무", "휴무", "휴무"];

/** R2's measuring set: 12 companies with an adopted version, a 사업장 one and another whole-company one waiting, votes and users, talk. */
async function seed() {
  const { call, env } = server();
  let n = 10;
  const ids = [];
  const setups = [];
  for (let i = 1; i <= 12; i++) {
    const name = `가상화학${i}`;
    const a = (await call("POST", "/api/reports", report(n++, { company: name, setup: setup() }), from(n))).data;
    await call("POST", "/api/reports", report(n++, { company: name, setup: setup() }), from(n));
    await call("POST", "/api/reports", report(n++, { company: name, dept: "온산공장", setup: cycle(LONG) }), from(n));
    const s2 = setup();
    s2.shiftTypes[0].start = "08:00";
    await call("POST", "/api/reports", report(n++, { company: name, setup: s2 }), from(n));
    for (let k = 0; k < 3; k++) await call("POST", "/api/votes", { device: device(n++), company: a.company.id, setup: a.setup, ok: k !== 2, why: k === 2 ? "time" : undefined }, from(n));
    for (let k = 0; k < 3; k++) await call("POST", "/api/uses", { device: device(n++), company: a.company.id, setup: a.setup, cal: "main" }, from(n));
    ids.push(a.company.id);
    setups.push(a.setup);
  }
  const dev = device(n++);
  await call("POST", "/api/members", { device: dev, company: ids[0], cal: "main" }, from(n));
  for (let k = 0; k < 8; k++) await call("POST", "/api/posts", { device: dev, company: ids[0], setup: setups[0], body: `토론 글 ${k}` }, from(n));
  for (let k = 0; k < 8; k++) await call("POST", "/api/posts", { device: dev, company: ids[0], setup: "room", body: `방 글 ${k}` }, from(n));
  await call("POST", "/api/rates", { device: dev, job: "factory", region: "31", trade: "용접", unit: "day", amount: 250000 }, from(n));
  return { call, env, ids, setups, dev, n };
}

const state = await seed();
const t = timed(state.env.DB);
const e = { ...state.env, DB: t.DB };
// The schema check of a fresh worker copy is not a read's.
await worker.fetch(new Request("https://company.example/api/config"), e);

/** Statements and rounds of one request: the shorter of two runs. [fresh] asks past every kept copy, as after a write. */
async function cost(path, { headers = {}, method = "GET", body } = {}) {
  let best = null;
  for (let i = 0; i < 2; i++) {
    t.counter.n = 0;
    const started = Date.now();
    const res = await worker.fetch(new Request("https://company.example" + path, { method, body: body && JSON.stringify(body),
      headers: { "cf-connecting-ip": "203.0.113.77", "cache-control": "no-cache", ...(body ? { "content-type": "application/json" } : {}), ...headers } }), e);
    const ms = Date.now() - started;
    assert.ok(res.status < 300, `${path} ${res.status} ${await res.text()}`);
    const r = { stmts: t.counter.n, rounds: Math.round(ms / MS) };
    if (!best || r.rounds < best.rounds) best = r;
  }
  return best;
}

// [path, budget rounds, budget statements, headers]. R2's targets (board voting ≤5, adopted ≤4, company ≤4, room ≤4,
// talk ≤4, search ≤3, top ≤2, history ≤3) and the 1.30 additions. Measured with this file's set (1.30.0): before → after in
// docs/plans/1.30-SP1-개발계획.md.
const { ids, setups, dev } = state;
const member = { "x-device": dev };
const BUDGET = [
  ["board voting", "/api/board?tab=voting", 5, 10],
  ["board adopted", "/api/board?tab=adopted", 4, 10],
  ["board adopted q", "/api/board?tab=adopted&q=" + encodeURIComponent("가상"), 4, 10],
  ["company (anyone)", `/api/companies/${ids[0]}`, 4, 16],
  ["company (member)", `/api/companies/${ids[0]}`, 4, 16, member],
  ["company + room + talk", `/api/companies/${ids[0]}?with=room,talk`, 4, 40, member],
  ["room", `/api/companies/${ids[0]}/room`, 4, 14, member],
  ["talk on a version", `/api/companies/${ids[0]}/setups/${setups[0]}/posts`, 4, 18, member],
  ["talk in the room", `/api/companies/${ids[0]}/setups/room/posts`, 4, 18, member],
  ["search", "/api/companies?q=" + encodeURIComponent("가상화학"), 3, 10],
  ["top", "/api/companies/top", 2, 8],
  ["all companies", "/api/companies/all", 3, 10],
  ["history", `/api/companies/${ids[0]}/history?setup=${setups[0]}`, 3, 6, member],
  ["activity", "/api/activity", 3, 12, member],
  ["rates", "/api/rates?job=factory", 2, 5, member],
  ["plazas", "/api/plazas", 2, 4],
  ["terms", "/api/terms", 1, 2],
];

for (const [label, path, rounds, stmts, headers] of BUDGET) {
  test(`[SPD-07] ${label}: at most ${rounds} rounds and ${stmts} statements`, async () => {
    const c = await cost(path, { headers });
    assert.ok(c.rounds <= rounds, `${label}: ${c.rounds} rounds (${c.stmts} statements), budget ${rounds}`);
    assert.ok(c.stmts <= stmts, `${label}: ${c.stmts} statements, budget ${stmts}`);
  });
}

test("[SPD-07][SPD-08] joining with ?with=page answers with the page in at most 6 rounds", async () => {
  const c = await cost("/api/members?with=page", { method: "POST", body: { device: device(9000), company: ids[3], cal: "main" } });
  assert.ok(c.rounds <= 6, `${c.rounds} rounds (${c.stmts} statements)`);
});

test("[SPD-07] a list kept in this Worker answers a second time without D1", async () => {
  await cost("/api/board?tab=voting&sort=used");
  t.counter.n = 0;
  const res = await worker.fetch(new Request("https://company.example/api/board?sort=used&tab=voting", { headers: { "cf-connecting-ip": "203.0.113.77" } }), e);
  assert.equal(res.status, 200);
  assert.equal(t.counter.n, 0, "the same list (its query in another order) reads nothing");
  assert.match(res.headers.get("cache-control"), /public, max-age=20/);
});
