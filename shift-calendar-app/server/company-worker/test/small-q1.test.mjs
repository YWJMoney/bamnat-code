// 2.0.0 갈래 Q1 [RVQ-13][RVQ-14] 검토 SRV-16·18·21·22·23, U21-02·06, I21a-01: 작은 서버 정리와 서버 상태 맨 위 줄. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { deployState } from "../src/index.js";
import { countFailure, errorState, watchDay } from "../src/ops-watch.js";
import { server, report, device, from, signIn, ORIGIN } from "./helpers.mjs";

const NOW = Date.parse("2026-11-20T03:20:00Z");

test("[RVQ-13] SRV-21 /api/events는 깃발 저장소를 한 번만 읽어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server();
  await call("GET", "/api/terms");
  let reads = 0;
  const batch = DB.batch.bind(DB);
  env.DB = { ...DB, prepare: DB.prepare.bind(DB), batch: (l) => { if (l.length === 4) reads++; return batch(l); } };
  const body = { v: 1, app: "2.0.0", style: "SHIFT", keep: [{ day: "2026-11-18", win: 7, events: ["open"] }],
    days: [{ day: "2026-11-19", events: { open: 1, life_d7: 1, disc_seen: 1 }, recent: [] }],
    lookups: [{ kind: "search", ms: 300 }], setup: { taps: 12, style: "SHIFT" } };
  assert.equal((await call("POST", "/api/events", body, from(1))).status, 204);
  assert.ok(reads <= 1, `깃발 저장소 ${reads}번`);
});

test("[RVQ-13] SRV-22 (가) 내 글을 지우면 답글·신고·맞아요·투표와 정해지지 않은 제안 제보도 함께", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server({ plaza: true });
  const write = (n, body, extra = {}) => call("POST", "/api/posts", { device: device(n), company: "style-shift", setup: "room", body, ...extra }, from(n));
  const top = (await write(1, "야간 끝나고 무엇을 먹나요")).data.id;
  const reply = (await write(2, "저는 국밥이요", { parent: top })).data.id;
  assert.ok(top && reply);
  DB.db.prepare("INSERT INTO post_votes (post_id, device, ok, at) VALUES (?, 'v1', 1, ?), (?, 'v2', 1, ?)").run(top, NOW, reply, NOW);
  DB.db.prepare("INSERT INTO post_flags (post_id, device, reason, at) VALUES (?, 'f1', 'abuse', ?)").run(reply, NOW);
  const gone = await call("DELETE", `/api/posts/${top}`, undefined, { "x-device": device(1) });
  assert.equal(gone.status, 204);
  const left = (sql, ...v) => DB.db.prepare(sql).get(...v).n;
  assert.equal(left("SELECT COUNT(*) AS n FROM posts WHERE id IN (?, ?)", top, reply), 0);
  assert.equal(left("SELECT COUNT(*) AS n FROM post_votes WHERE post_id IN (?, ?)", top, reply), 0);
  assert.equal(left("SELECT COUNT(*) AS n FROM post_flags WHERE post_id IN (?, ?)", top, reply), 0);
});

test("[RVQ-13] SRV-22 (다) 같은 회사 이름을 동시에 처음 보내도 회사는 하나", async () => {
  const { call, DB } = server();
  await call("GET", "/api/terms");
  const sent = await Promise.all([1, 2, 3].map((n) => call("POST", "/api/reports", report(n, { company: "가상동시물산" }), from(n))));
  assert.ok(sent.every((r) => r.status === 201 || r.status === 200), JSON.stringify(sent.map((r) => r.status)));
  assert.equal(new Set(sent.map((r) => r.data.company.id)).size, 1);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM companies WHERE name = '가상동시물산'").get().n, 1);
  // 관리자가 만드는 것도 겹치면 409.
  const owner = await signIn(call);
  assert.equal((await call("POST", "/api/admin/companies", { name: "가상동시물산" }, owner)).status, 409);
});

test("[RVQ-13] SRV-22 (라) 길이 머리말 없이 큰 본문은 한도에서 끊어 413", async () => {
  const { call, env } = server();
  await call("GET", "/api/terms");
  let pulled = 0;
  const chunk = new TextEncoder().encode(`"${"가".repeat(3000)}`);
  const body = new ReadableStream({
    start(c) { c.enqueue(new TextEncoder().encode('{"device":"x","text":')); },
    pull(c) { pulled++; if (pulled > 200) c.close(); else c.enqueue(chunk); },
  });
  const res = await worker.fetch(new Request(`${ORIGIN}/api/reports`, { method: "POST", body, duplex: "half",
    headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.9" } }), env);
  assert.equal(res.status, 413);
  assert.ok(pulled < 50, `한도를 넘은 뒤에도 ${pulled}조각을 읽었어요`);
});

test("[RVQ-13] SRV-22 (마) 가장자리 사본은 s-maxage로 [seconds]만큼, 휴대폰이 받는 답의 max-age는 그대로", async () => {
  const { call, env } = server();
  await call("GET", "/api/terms");
  const kept = new Map();
  globalThis.caches = { default: { match: async (r) => kept.get(r.url)?.clone(), put: async (r, res) => { kept.set(r.url, res); } } };
  try {
    const res = await worker.fetch(new Request(`${ORIGIN}/api/companies/top`, { headers: { "cf-connecting-ip": "203.0.113.9" } }), env);
    assert.match(res.headers.get("cache-control"), /max-age=600/);
    assert.doesNotMatch(res.headers.get("cache-control"), /s-maxage/);
    const copy = [...kept.values()][0];
    assert.match(copy.headers.get("cache-control"), /s-maxage=600/);
    // 나눠 갖지 않는 답은 두지 않아요.
    const page = await worker.fetch(new Request(`${ORIGIN}/api/terms`, { headers: { "cf-connecting-ip": "203.0.113.9" } }), env);
    assert.equal(page.status, 200);
  } finally {
    delete globalThis.caches;
  }
});

test("[RVQ-13] SRV-23 workers.dev 주소의 관리자 길은 거절, 앱 길은 그대로, 고장 난 날은 ADMIN_ON_WORKERS_DEV=1로", async () => {
  const { call, env } = server();
  await call("GET", "/api/terms");
  const dev = (path, e = env) => worker.fetch(new Request(`https://bamnat-company.example.workers.dev${path}`, { headers: { "cf-connecting-ip": "203.0.113.9" } }), e);
  for (const p of ["/admin/", "/api/admin/badges", "/api/admin/health?deep=1"]) {
    const r = await dev(p);
    assert.equal(r.status, 404, p);
    assert.equal((await r.json()).error, "use_domain");
  }
  assert.ok([200, 204].includes((await dev("/api/hello")).status));
  assert.notEqual((await dev("/admin/", { ...env, ADMIN_ON_WORKERS_DEV: "1" })).status, 404);
});

test("[RVQ-13] SRV-18·U21-06 묶이지 않은 오류를 묶기 전·묶기 실패로 나누고, 정기 작업 실패는 따로 세요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env, DB } = server();
  await call("GET", "/api/terms");
  const day = watchDay(NOW);
  DB.db.prepare("INSERT INTO counters (key, count, expires) VALUES (?, 5, ?)").run(`errors|${day}`, NOW + 86400e3 * 3);
  await countFailure(env, "errfail", NOW);
  await countFailure(env, "errfail", NOW);
  for (let i = 0; i < 3; i++) await countFailure(env, "joberr", NOW);
  const st = await errorState(env, NOW);
  assert.equal(st.loose, 5);
  assert.deepEqual(st.split, { before: 3, failed: 2, jobs: 3 });
});

test("[RVQ-14] U21-02·SRV-16 서버 상태 맨 위: 판·배포 번호·스키마 올림 시간·되돌릴 판과 깊은 점검 셋", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const meta = (id) => ({ id: `${id}-0000-4000-8000-000000000000`, tag: "abcdef123456", timestamp: new Date(NOW).toISOString() });
  const { call, env } = server({ CF_VERSION_METADATA: meta("11111111") });
  const owner = await signIn(call);
  const d = (await call("GET", "/api/admin/server", undefined, owner)).data.deploy;
  assert.equal(d.id, "11111111");
  assert.ok(d.schemaUp && d.schemaUp.ms >= 0, "처음 표를 올린 시간이 남아요");
  assert.deepEqual([d.checks.read, d.checks.write], [true, true]);
  assert.equal(d.prev, null);
  // 새 판이 올라오면(같은 DB, 새 배포 번호) 앞 판이 '되돌릴 판'으로.
  const next = await import("../src/index.js?isolate=q1-deploy");
  const env2 = { ...env, CF_VERSION_METADATA: meta("22222222") };
  await next.default.fetch(new Request(`${ORIGIN}/api/hello`), env2);
  const after = await next.deployState(env2, NOW);
  assert.equal(after.id, "22222222");
  assert.match(after.prev ?? "", /11111111/);
  assert.equal(typeof deployState, "function");
});

test("[RVQ-15] SRV-17 법적 깃발은 조건 고치기로 처리방침 조건을 빼도 다시 붙고, 시행 전에는 서버도 꺼진 채", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-10-20T03:00:00Z") });
  const { call } = server();
  const owner = await signIn(call);
  // 3.12 시행 전(10-20). 처리방침 조건을 빼고 동의만 남겨 보려 해요.
  const r = await call("POST", "/api/admin/flags/stats.life/conditions", { conditions: [{ type: "consent", key: "usage" }], why: "시험: 조건 줄이기" }, owner);
  assert.equal(r.status, 200, r.text);
  const kept = r.data.override?.conditions ?? r.data.flag?.override?.conditions ?? r.data.conditions?.map((x) => x.c);
  const text = JSON.stringify(kept ?? r.data);
  assert.match(text, /"type":"policy","version":"3.12"/);
  assert.match(text, /"type":"date","policy":"3.12"/);
  const row = (await call("GET", "/api/admin/flags", undefined, owner)).data.flags.find((f) => f.key === "stats.life");
  assert.equal(row.on, false);
});

test("[RVQ-15] SRV-20 꾸밀 수 있는 숫자의 신호는 Play와 맞춰 보고, 급변·Play 그대로면 사람만 개발사항으로", async (t) => {
  const { obsRules, obsMerge } = await import("../src/observe.js");
  const days = (n, v) => Array.from({ length: n }, (_, i) => ({ day: `2026-11-${String(i + 1).padStart(2, "0")}`, n: v }));
  const b = { active: { play: days(14, 100) }, sample: { keep: { d1: { base: 50, rate: 20 } }, features: [] } };
  const found = obsRules(b).filter((f) => f.kind === "keep-d1");
  assert.equal(found.length, 1);
  assert.deepEqual([found[0].src, found[0].play, found[0].doubt], ["stats", 0, true], "Play 하루 사용자는 그대로");
  const list = obsMerge([], found, NOW);
  assert.equal(list[0].human, true);
  // Play도 같이 30% 줄었으면 의심은 풀리고, 처음 본 지 하루가 지나면 자동 처리도 돼요.
  const fell = { active: { play: [...days(7, 100), ...days(7, 70)] }, sample: b.sample };
  const again = obsMerge(list, obsRules(fell).filter((f) => f.kind === "keep-d1"), NOW + 2 * 86400e3);
  assert.equal(again[0].doubt, false);
  assert.equal(again[0].human, false);
  // Play에서 온 신호에는 붙지 않아요.
  assert.ok(obsRules({ quality: { vitals: { crash: 1.2, anr: null } } }).every((f) => !f.src));

  // 운영 열쇠(Claude)는 사람 확인을 기다리는 신호를 개발사항으로 넘기지 못해요. 사람은 돼요.
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  const key = (await call("POST", "/api/admin/keys", { label: "Claude 프로젝트" }, owner)).data.key;
  const claude = { authorization: `Bearer ${key}`, "x-admin": "1" };
  DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").run(JSON.stringify({ list }));
  const k = list[0].key;
  const byKey = await call("POST", "/api/admin/observe/signals", { key: k, act: "dev" }, claude);
  assert.equal(byKey.status, 409);
  assert.equal(byKey.data.error, "human_check");
  assert.equal((await call("POST", "/api/admin/observe/signals", { key: k, act: "watch" }, claude)).status, 200);
  assert.equal((await call("POST", "/api/admin/observe/signals", { key: k, act: "dev" }, owner)).status, 200);
});
