// AS 1.29 [SRCH-01][SRCH-02][SRCH-03][SRCH-04] 1.29 묶음 AS (사용자 2026-10-27: "광장에 대표판이 있는데 회사 검색 시 뜨지 않는다",
// "안 뜨는 게 아니라 늦게 떠서 안 뜨는 것처럼 보인다"): the search gives every company it lists its 대표 주기 in a few reads
// (not a few per company), finds a company typed by its Korean reading (지에스 = GS), keeps a short edge copy, and has a list
// of the most used companies for the app to keep. Companies are made up. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { spoken, searchLeads, searchLeadOne } from "../src/index.js";
import { server, report, setup, device, from } from "./helpers.mjs";

function cycle(steps = ["주간", "주간", "휴무", "휴무", "야간", "야간", "휴무", "휴무"]) {
  const s = setup();
  delete s.pay;
  s.rotation = { anchor: "2026-10-01", steps };
  return s;
}
const LONG = ["주간", "주간", "주간", "휴무", "휴무", "휴무", "야간", "야간", "야간", "휴무", "휴무", "휴무"];

/** [count] made-up companies 가상화학N, each with a company-wide cycle (with pay for odd ones), a 사업장 cycle, votes and uses. */
async function seed(call, count = 8) {
  let n = 10;
  const ids = [];
  for (let i = 1; i <= count; i++) {
    const name = `가상화학${i}`;
    const whole = i % 2 ? setup() : cycle();
    const a = (await call("POST", "/api/reports", report(n, { company: name, setup: whole }), from(n))).data;
    n++;
    await call("POST", "/api/reports", report(n, { company: name, dept: "온산공장", setup: cycle(LONG) }), from(n));
    n++;
    await call("POST", "/api/votes", { device: device(n), company: a.company.id, setup: a.setup, ok: true }, from(n));
    n++;
    await call("POST", "/api/votes", { device: device(n), company: a.company.id, setup: a.setup, ok: false, why: "time" }, from(n));
    n++;
    await call("POST", "/api/uses", { device: device(n), company: a.company.id, setup: a.setup, cal: "main" }, from(n));
    n++;
    ids.push(a.company.id);
  }
  return ids;
}

/** env.DB with every read counted and [ms] of wait each, as a D1 a little way off answers. */
function timed(DB, ms) {
  const counter = { reads: 0 };
  const wait = () => new Promise((r) => setTimeout(r, ms));
  const wrap = (s) => ({
    ...s,
    bind: (...v) => wrap(s.bind(...v)),
    first: async (c) => { counter.reads++; await wait(); return s.first(c); },
    all: async () => { counter.reads++; await wait(); return s.all(); },
    run: async () => { counter.reads++; await wait(); return s.run(); },
  });
  return { counter, DB: { ...DB, prepare: (sql) => wrap(DB.prepare(sql)), batch: async (l) => { counter.reads++; await wait(); return DB.batch(l); } } };
}

async function measure(env, q) {
  const t = timed(env.DB, 5);
  const e = { ...env, DB: t.DB };
  // The schema check of a fresh database is not the search's.
  await worker.fetch(new Request("https://company.example/api/terms"), e);
  t.counter.reads = 0;
  const started = Date.now();
  const res = await worker.fetch(new Request("https://company.example/api/companies?q=" + encodeURIComponent(q), {
    headers: { "cf-connecting-ip": "203.0.113.77" } }), e);
  const ms = Date.now() - started;
  return { reads: t.counter.reads, ms, data: await res.json() };
}

test("[SRCH-01] every company the search lists has its 대표 주기, read in a few reads whatever the number of companies", async () => {
  const { call, env } = server();
  await seed(call, 8);
  await measure(env, "가상화학"); // the first search fills the names' 초성 and reading
  const eight = await measure(env, "가상화학");
  assert.equal(eight.data.companies.length, 8);
  assert.equal(eight.data.companies.filter((c) => c.lead).length, 8, "1.28: only the first five had it");
  for (const c of eight.data.companies) {
    assert.equal(c.users, 3, "two senders and a phone that uses it (a 맞아요 alone is not a user)");
    assert.equal(c.sites, 2);
    assert.equal(c.lead.dept, "");
  }
  // Twice the companies, the same reads: nothing is read per company any more.
  await seed(call, 16);
  await measure(env, "가상화학");
  const more = await measure(env, "가상화학");
  assert.equal(more.data.companies.filter((c) => c.lead).length, more.data.companies.length);
  assert.equal(more.reads, eight.reads);
  // 1.28 read 41 times in a row for these eight (5 ms each: 236 ms); now the search, then the five lead reads together.
  assert.ok(eight.reads <= 8, `reads ${eight.reads}`);
  console.log(`[SRCH-01] search of 8 companies: ${eight.reads} reads, ${eight.ms} ms at 5 ms a read (1.28: 41 reads, 236 ms, 5 with 대표 주기)`);
});

test("[SRCH-01] the many-company reading says just what the one-company reading said", async () => {
  const { call, env } = server();
  const ids = await seed(call, 4);
  // A doubted lead, a pending one turned down once, an adopted one and a company with nothing left: all as before.
  const [a, b, c] = ids;
  for (const n of [90, 91, 92]) await call("POST", "/api/votes", { device: device(n), company: a, setup: (await call("GET", `/api/companies/${a}`)).data.setups.find((s) => s.dept === "").id, ok: false, why: "order" }, from(n));
  const bSetup = (await call("GET", `/api/companies/${b}`)).data.setups.find((s) => s.dept === "").id;
  await call("POST", "/api/reports", report(93, { company: "가상화학2", setup: (await call("GET", `/api/companies/${b}`)).data.setups.find((s) => s.dept === "").setup }), from(93));
  await call("POST", "/api/votes", { device: device(94), company: c, setup: bSetup, ok: true }, from(94));
  const many = await searchLeads(env, ids);
  for (const id of ids) assert.deepEqual(many.get(id), await searchLeadOne(env, id), id);
  // Two behind it (a sender and a 맞아요) and four 달라요: too many to lead, as one company at a time said too.
  assert.equal(many.get(a).lead.doubted, true);
});

test("[SRCH-01] a company's 대표판 on the plaza is in the search with its line: adopted, pay, who stands behind it", async () => {
  const { call } = server();
  // Two coworkers send the same setup: adopted (two people in these tests), the plaza's 대표판.
  const first = (await call("POST", "/api/reports", report(1, { company: "(주)가상정밀", verified: true }), from(1))).data;
  await call("POST", "/api/reports", report(2, { company: "가상정밀" }), from(2));
  const board = (await call("GET", "/api/board?tab=adopted")).data.items;
  assert.equal(board.length, 1);
  for (const q of ["가상정밀", "가상 정밀", "(주)가상정", "ㄱㅅㅈㅁ"]) {
    const hit = (await call("GET", "/api/companies?q=" + encodeURIComponent(q))).data.companies.find((c) => c.id === first.company.id);
    assert.ok(hit, q);
    assert.equal(hit.lead.setup, board[0].id, `${q}: the plaza's 대표판 is the search's 대표 주기`);
    assert.equal(hit.lead.adopted, true);
    assert.equal(hit.lead.pay, true, "급여 방식 있음");
    assert.equal(hit.lead.ok, 2);
    assert.equal(hit.users, 2);
  }
});

test("[SRCH-02] a name is found by its Korean reading, as the app's public rows are", async () => {
  assert.equal(spoken("GS칼텍스"), "지에스칼텍스");
  assert.equal(spoken("S-OIL 온산"), "에스오일온산");
  assert.equal(spoken("(주)LG화학"), "엘지화학");
  assert.equal(spoken("POSCO 광양"), "포스코광양");
  assert.equal(spoken("한국전력"), "한국전력");
  const { call } = server();
  const gs = (await call("POST", "/api/reports", report(1, { company: "GV칼텍스" }), from(1))).data.company;
  const so = (await call("POST", "/api/reports", report(2, { company: "S-OIL가상" }), from(2))).data.company;
  for (const [q, id] of [["지브이칼텍스", gs.id], ["지브이칼", gs.id], ["gv칼텍스", gs.id], ["에스오일가상", so.id], ["s-oil가상", so.id]]) {
    const found = (await call("GET", "/api/companies?q=" + encodeURIComponent(q))).data.companies;
    assert.deepEqual(found.map((c) => c.id), [id], q);
    assert.ok(found[0].lead, `${q} with its 대표 주기`);
  }
  // A company from before 1.29 (no reading kept yet) is given one by the search and found the next time.
  const { call: call2, DB } = server();
  const old = (await call2("POST", "/api/reports", report(3, { company: "KT가상" }), from(3))).data.company;
  await DB.prepare("UPDATE companies SET said = '' WHERE id = ?").bind(old.id).run();
  await call2("POST", "/api/votes", { device: device(4), company: old.id, setup: "0000000000000000", ok: true }, from(4)); // any write
  await call2("GET", "/api/companies?q=" + encodeURIComponent("가상"));
  assert.deepEqual((await call2("GET", "/api/companies?q=" + encodeURIComponent("케이티가상"))).data.companies.map((c) => c.id), [old.id]);
});

test("[SRCH-03] a search's answer may be kept 30 seconds; with the Cache API it comes from the edge's copy", async () => {
  const { call, env } = server();
  await call("POST", "/api/reports", report(1, { company: "가상물류" }), from(1));
  const plain = await call("GET", "/api/companies?q=" + encodeURIComponent("가상물류"));
  assert.match(plain.headers.get("cache-control"), /public, max-age=30/);
  const kept = new Map();
  globalThis.caches = { default: {
    match: async (r) => kept.get(r.url)?.clone(),
    put: async (r, res) => { kept.set(r.url, res); },
  } };
  try {
    const t = timed(env.DB, 0);
    const e = { ...env, DB: t.DB };
    const ask = (q) => worker.fetch(new Request("https://company.example/api/companies?q=" + encodeURIComponent(q), { headers: { "cf-connecting-ip": "203.0.113.9" } }), e);
    const one = await (await ask("가상 물류")).json();
    const reads = t.counter.reads;
    // The same search written another way is the same copy, and reads nothing.
    const two = await (await ask("(주)가상물류")).json();
    assert.deepEqual(two, one);
    assert.equal(t.counter.reads, reads);
    assert.equal(kept.size, 1);
    // A company's own page is never kept (it differs for a member).
    const page = await worker.fetch(new Request(`https://company.example/api/companies/${one.companies[0].id}`), e);
    assert.equal(page.headers.get("cache-control"), "no-store");
    assert.equal(kept.size, 1);
  } finally {
    delete globalThis.caches;
  }
});

test("[SRCH-04] the most used companies, with their 대표 주기, for the app to keep; nothing about a phone", async () => {
  const { call, DB } = server();
  const ids = await seed(call, 3);
  // One more phone on the second company: it comes first.
  await call("POST", "/api/uses", { device: device(99), company: ids[1], setup: (await call("GET", `/api/companies/${ids[1]}`)).data.setups.find((s) => s.dept === "").id, cal: "main" }, from(99));
  const hidden = (await call("POST", "/api/reports", report(98, { company: "가상숨김" }), from(98))).data.company.id;
  await DB.prepare("UPDATE companies SET hidden = 1 WHERE id = ?").bind(hidden).run();
  const res = await call("GET", "/api/companies/top");
  assert.equal(res.status, 200);
  assert.match(res.headers.get("cache-control"), /max-age=600/);
  const top = res.data.companies;
  assert.equal(top[0].id, ids[1]);
  assert.equal(top[0].users, 4);
  assert.deepEqual(top.map((c) => c.id).sort(), [...ids].sort(), "a hidden company is not there");
  for (const c of top) assert.ok(c.lead && c.lead.steps, c.name);
  assert.doesNotMatch(res.text, /test-device|device/);
  assert.equal((await call("GET", "/api/companies/top?n=1")).data.companies.length, 1);
});
