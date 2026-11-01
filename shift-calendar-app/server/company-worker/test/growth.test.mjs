// 1.36 [GRW-01]~[GRW-06] 자가 발전 바퀴: 스튜디오 요구 → 묶기 → 장터 → 사용자 부품으로 풀림 → 알림, 승격 사다리, 손잡이 지도. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { server, device, from, signIn } from "./helpers.mjs";
import { parseWhere, tierOf, GROWTH } from "../src/growth.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400000;

const studio = (call, n, text, style = "SHIFT") => call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "studio", text,
  info: { app: "1.36.0 (136)", model: "Pixel", android: "16", studio: style } }, from(n));
const tick = async (env, now) => {
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
};
const recipe = (title) => ({ format: "bamnat-recipe", v: 1, title, summary: "칸이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] }] });

test("[GRW-01] where a studio ask came from is read off its first line", () => {
  assert.deepEqual(parseWhere("[스튜디오 › 달력 칸 › 색] 칸마다 그라데이션을 넣고 싶어요"), { area: "달력 칸", tool: "색", body: "칸마다 그라데이션을 넣고 싶어요" });
  assert.deepEqual(parseWhere("[스튜디오] 스티커"), { area: "", tool: "", body: "스티커" });
  assert.equal(parseWhere("그냥 글").area, "");
});

test("[GRW-01][GRW-02][GRW-03] studio asks group, go to the market at three, and every asker hears when someone solves it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const admin = await signIn(call);
  const a = (await studio(call, 1, "[스튜디오 › 달력 칸 › 색] 달력 칸 배경에 그라데이션을 넣고 싶어요")).data;
  assert.equal(a.kind, "studio");
  await studio(call, 2, "[스튜디오 › 달력 칸 › 색] 칸 배경을 그라데이션으로 넣고 싶어요");
  // The same phone twice is one asker.
  await studio(call, 2, "[스튜디오 › 달력 칸 › 색] 달력 칸 배경 그라데이션 넣고 싶어요 꼭");
  await studio(call, 4, "[스튜디오 › 머리 › 글꼴] 손글씨 글꼴을 더 많이 쓰고 싶어요");
  let needs = (await call("GET", "/api/admin/growth/needs", undefined, admin)).data;
  assert.equal(needs.items.length, 2);
  const grad = needs.items.find((n) => n.area === "달력 칸");
  assert.equal(grad.askers, 2);
  assert.equal(grad.asks, 3);
  assert.equal(grad.state, "open");
  // Two askers is not yet three: nothing on the 요청 판 after the hour.
  await tick(env, AFTER);
  assert.equal((await call("GET", "/api/recipes/requests?style=DAY")).data.items.length, 0);
  await studio(call, 3, "[스튜디오 › 달력 칸 › 색] 달력 칸 배경 그라데이션 넣고 싶어요");
  await tick(env, AFTER);
  needs = (await call("GET", "/api/admin/growth/needs", undefined, admin)).data;
  const g2 = needs.items.find((n) => n.id === grad.id);
  assert.equal(g2.state, "market");
  assert.equal(g2.askers, 3);
  const board = (await call("GET", "/api/recipes/requests?style=SHIFT")).data.items;
  const row = board.find((q) => q.id === g2.request);
  assert.equal(row.askers, 3);
  assert.match(row.body, /^\[달력 칸 › 색\]/);
  // A maker takes it and finishes it with a public recipe of their own.
  const code = (await call("POST", "/api/recipes", { device: device(9), recipe: recipe("그라데이션 칸 부품"), scope: "public" }, from(9))).data.code;
  assert.equal((await call("POST", `/api/recipes/requests/${row.id}/take`, { device: device(9) }, from(9))).data.state, "making");
  assert.equal((await call("POST", `/api/recipes/requests/${row.id}/done`, { device: device(9), code }, from(9))).data.state, "done");
  needs = (await call("GET", "/api/admin/growth/needs?state=solved", undefined, admin)).data;
  assert.equal(needs.items[0].code, code);
  // Asker 1's question got an answer with the code (the app's 새 답 알림).
  const mine = (await call("GET", "/api/inquiries", undefined, { "x-device": device(1) })).data.inquiries[0];
  assert.equal(mine.status, "answered");
  assert.match(mine.messages.at(-1).text, new RegExp(code));
});

test("[GRW-02] a request nobody made for 14 days becomes a studio need too", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const admin = await signIn(call);
  await call("POST", "/api/recipes/requests", { device: device(1), style: "DAY", body: "출근 도장 찍는 버튼 달린 달력 있었으면" }, from(1));
  await tick(env, AFTER + DAY);
  assert.equal((await call("GET", "/api/admin/growth/needs", undefined, admin)).data.items.length, 0);
  t.mock.timers.setTime(AFTER + (GROWTH.staleDays + 1) * DAY);
  await tick(env, Date.now());
  const items = (await call("GET", "/api/admin/growth/needs", undefined, await signIn(call))).data.items;
  assert.equal(items.length, 1);
  assert.equal(items[0].samples[0].src, "req");
});

test("[GRW-04][GRW-06] the admin merges, names, builds and ships a need; askers hear the version; the map and release lines", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  await studio(call, 1, "[스튜디오 › 꾸밈 층 › 스티커] 25일 칸에 돈 스티커 붙이고 싶어요");
  await studio(call, 2, "[스튜디오 › 꾸밈 층 › 도형] 머리에 별 모양 도형을 자유롭게 놓기");
  const [x, y] = (await call("GET", "/api/admin/growth/needs", undefined, admin)).data.items;
  const merged = (await call("POST", `/api/admin/growth/needs/${x.id}`, { action: "merge", into: y.id }, admin)).data;
  assert.equal(merged.askers, 2);
  assert.equal((await call("POST", `/api/admin/growth/needs/${y.id}`, { action: "title", title: "꾸밈 층에 스티커·도형 자유 배치" }, admin)).data.title, "꾸밈 층에 스티커·도형 자유 배치");
  assert.equal((await call("POST", `/api/admin/growth/needs/${y.id}`, { action: "decline" }, admin)).data.error, "note");
  assert.equal((await call("POST", `/api/admin/growth/needs/${y.id}`, { action: "build", note: "갈래 C 꾸밈 층" }, admin)).data.state, "building");
  assert.equal((await call("POST", `/api/admin/growth/needs/${y.id}`, { action: "official", version: "1.36" }, admin)).data.told, 2);
  const mine = (await call("GET", "/api/inquiries", undefined, { "x-device": device(2) })).data.inquiries[0];
  assert.match(mine.messages.at(-1).text, /1\.36 판에서 스튜디오의 기본 손잡이/);
  const map = (await call("GET", "/api/admin/growth/map?version=1.36", undefined, admin)).data;
  assert.equal(map.items[0].area, "꾸밈 층");
  assert.match(map.lines[0], /여러분이 부탁해서 생긴 꾸미기: 꾸밈 층에 스티커·도형 자유 배치\(2명\)/);
  // 문의 담당 without 장터 sees needs; the audit has each step.
  const audit = (await call("GET", "/api/admin/audit", undefined, admin)).data;
  assert.ok(JSON.stringify(audit).includes("need_official"));
});

test("[GRW-05] the ladder climbs by real users and 7-day keeping, not votes, and the admin adopts or puts off", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env, DB } = server({ plaza: true });
  const admin = await signIn(call);
  assert.equal(tierOf({ state: "visible", scope: "public", flags: 0, applied: 49, kept7: 40 }), null);
  assert.equal(tierOf({ state: "visible", scope: "public", flags: 0, applied: 60, kept7: 30 }), 2);
  assert.equal(tierOf({ state: "visible", scope: "public", flags: 0, applied: 400, kept7: 240 }), 3);
  assert.equal(tierOf({ state: "visible", scope: "public", flags: 1, applied: 400, kept7: 240 }), null);
  const a = (await call("POST", "/api/recipes", { device: device(1), recipe: recipe("주간 띠 부품"), scope: "public" }, from(1))).data.code;
  const b = (await call("POST", "/api/recipes", { device: device(2), recipe: recipe("야간 강조 부품"), scope: "public" }, from(2))).data.code;
  await DB.prepare("UPDATE recipes SET applied = 80, kept7 = 40 WHERE code = ?").bind(a).run();
  await DB.prepare("UPDATE recipes SET applied = 500, kept7 = 300 WHERE code = ?").bind(b).run();
  await tick(env, AFTER);
  const tools = (await call("GET", "/api/recipes/tools?style=SHIFT")).data.items;
  assert.deepEqual(tools.map((x) => [x.code, x.tierName]), [[b, "기본 도구 후보"], [a, "모두의 도구"]]);
  const cands = (await call("GET", "/api/admin/growth/tools", undefined, admin)).data;
  assert.deepEqual(cands.items.map((x) => x.code), [b]);
  assert.equal(cands.items[0].kept, 60);
  assert.equal((await call("POST", `/api/admin/growth/tools/${b}`, { action: "adopt" }, admin)).data.error, "version");
  await call("POST", `/api/admin/growth/tools/${b}`, { action: "adopt", version: "1.37" }, admin);
  assert.equal((await call("GET", "/api/admin/growth/tools?show=adopted", undefined, admin)).data.items[0].code, b);
  // Numbers falling take a plain tool down; an adopted one stays.
  await DB.prepare("UPDATE recipes SET applied = 80, kept7 = 1 WHERE code IN (?, ?)").bind(a, b).run();
  await tick(env, AFTER);
  assert.deepEqual((await call("GET", "/api/recipes/tools")).data.items.map((x) => x.code), [b]);
});

// ---- [GRW-08] 사진 세 겹 ----
import { firstState, PHOTO_GUARD } from "../src/photo-guard.js";

const webp = (n) => Buffer.concat([Buffer.from("RIFF"), Buffer.from([20, 0, 0, 0]), Buffer.from("WEBPVP8 "), Buffer.alloc(16, n)]).toString("base64");

test("[GRW-08] the second layer: 새싹 first pictures wait, a known phone's go up, a block never shows", () => {
  assert.deepEqual(firstState("unchecked", 0, 0), { state: "pending", why: "first" });
  assert.deepEqual(firstState("pass", 0, 0), { state: "pending", why: "first" });
  assert.deepEqual(firstState("pass", 0, PHOTO_GUARD.lookFirst), { state: "ok", why: null });
  assert.deepEqual(firstState("pass", 1, 0), { state: "ok", why: null });
  assert.deepEqual(firstState("unchecked", 1, 0), { state: "pending", why: "first" });
  assert.deepEqual(firstState("unchecked", 2, 0), { state: "ok", why: null });
  assert.deepEqual(firstState("block", 2, 9), { state: "held", why: "check" });
});

test("[GRW-08] first, check and report: an automatic check, three looked at then straight up, two reports hold it, the operator decides", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  let verdict = "SAFE";
  const AI = { run: async () => ({ response: verdict }) };
  const { call } = server({ plaza: true, AI, PHOTO_CHECK_MODEL: "@cf/test/vision" });
  const admin = await signIn(call);
  const up = async (n, k) => (await call("POST", "/api/recipes/images", { device: device(n), webp: webp(k) }, from(n))).data;
  // 새싹: the first three wait even when the check passes.
  const first = [];
  for (let k = 1; k <= 3; k++) first.push(await up(1, k));
  assert.deepEqual(first.map((x) => x.state), ["pending", "pending", "pending"]);
  let list = (await call("GET", "/api/admin/recipes/images", undefined, admin)).data;
  assert.equal(list.items[0].whyName, "새싹 첫 사진");
  for (const x of first) await call("POST", `/api/admin/recipes/images/${x.id}/ok`, {}, admin);
  const fourth = await up(1, 4);
  assert.equal(fourth.state, "ok");
  assert.equal((await call("GET", `/api/recipes/images/${fourth.id}`)).status, 200);
  // The check says UNSAFE: held from everyone, the phone hears only 'pending'.
  verdict = "UNSAFE";
  const bad = await up(1, 5);
  assert.equal(bad.state, "pending");
  assert.equal((await call("GET", `/api/recipes/images/${bad.id}`)).status, 404);
  list = (await call("GET", "/api/admin/recipes/images", undefined, admin)).data;
  assert.equal(list.items.find((x) => x.id === bad.id).whyName, "자동 검사에 걸림");
  // Two reports on a shown one hold it first; the owner cannot report their own.
  assert.equal((await call("POST", `/api/recipes/images/${fourth.id}/flag`, { device: device(1), reason: "nude" }, from(1))).data.held, false);
  await call("POST", `/api/recipes/images/${fourth.id}/flag`, { device: device(2), reason: "nude" }, from(2));
  assert.equal((await call("GET", `/api/recipes/images/${fourth.id}`)).status, 200, "one report is not enough");
  assert.equal((await call("POST", `/api/recipes/images/${fourth.id}/flag`, { device: device(3), reason: "nude" }, from(3))).data.held, true);
  assert.equal((await call("GET", `/api/recipes/images/${fourth.id}`)).status, 404);
  list = (await call("GET", "/api/admin/recipes/images", undefined, admin)).data;
  const held = list.items.find((x) => x.id === fourth.id);
  assert.equal(held.whyName, "신고로 먼저 가림");
  assert.deepEqual(held.reasons, { nude: 2 });
  // 괜찮아요 puts it back with the reports cleared.
  await call("POST", `/api/admin/recipes/images/${fourth.id}/ok`, {}, admin);
  assert.equal((await call("GET", `/api/recipes/images/${fourth.id}`)).status, 200);
});
