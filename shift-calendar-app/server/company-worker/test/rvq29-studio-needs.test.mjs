// 2.0.0 [RVQ-29] 검토 SVC-17·T22-07: 나만 보는 스튜디오 문의의 첫 문장이 거름 없이 '밤낮 운영' 이름으로 장터 요청 판에 올라갔어요.
// 이제 서로 다른 네트워크도 셋이어야 모이고, 셋이 되면 '올리기 전 확인'으로 와서 운영자·Claude가 요청 판 거름을 지난 제목으로 다듬어 올려요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { server, device, from, signIn } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400000;
const studio = (call, n, ip, text) => call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "studio", text,
  info: { app: "2.0.0 (54)", model: "Pixel", android: "16", studio: "SHIFT" } }, from(ip));
const tick = async (env, now) => {
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
};
const ASK = "[스튜디오 › 달력 칸 › 색] 우리 팀장님 몰래 달력 칸 배경에 그라데이션을 넣고 싶어요";

test("[RVQ-29] SVC-17 한 네트워크에서 설치만 바꿔 셋을 채워도 모이지 않고, 앱의 'N/3명'도 서로 다른 네트워크로 세요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env, DB } = server({ plaza: true });
  const admin = await signIn(call);
  for (const n of [1, 2, 3]) {
    const r = (await studio(call, n, 7, ASK)).data;
    assert.equal(r.studio.askers, 1, "같은 네트워크는 하나로");
  }
  await tick(env, AFTER);
  let need = (await call("GET", "/api/admin/growth/needs", undefined, admin)).data.items[0];
  assert.deepEqual([need.state, need.askers, need.nets], ["open", 3, 1]);
  // 다른 네트워크 둘이 더하면 셋이에요. 그래도 장터에는 저절로 오르지 않아요.
  assert.equal((await studio(call, 4, 8, ASK)).data.studio.askers, 2);
  assert.equal((await studio(call, 5, 9, ASK)).data.studio.askers, 3);
  await tick(env, AFTER);
  need = (await call("GET", "/api/admin/growth/needs", undefined, admin)).data.items[0];
  assert.deepEqual([need.state, need.stateName, need.nets, need.titled], ["ready", "올리기 전 확인", 3, false]);
  assert.equal((await call("GET", "/api/recipes/requests?style=SHIFT")).data.items.length, 0);
  // 네트워크 해시는 줄에 남지 않아요(처리방침 2시간): 그 묶음에 그날 처음인 네트워크만 새 무리, 같은 네트워크의 다음 부탁은 'x'(세지 않음).
  const cols = DB.db.prepare("PRAGMA table_info(studio_need_links)").all().map((c) => c.name);
  assert.ok(!cols.includes("net"));
  const clusters = DB.db.prepare("SELECT cluster FROM studio_need_links ORDER BY at").all().map((l) => l.cluster);
  assert.equal(clusters.filter((c) => c === "x").length, 2);
  assert.equal(new Set(clusters.filter((c) => c !== "x")).size, 3);
  t.mock.timers.setTime(AFTER + 3 * DAY);
  await tick(env, Date.now());
  assert.equal((await call("GET", "/api/admin/growth/needs", undefined, admin)).data.items[0].nets, 3);
});

test("[RVQ-29] SVC-17 올리기 전 확인: 부탁 글 그대로는 안 되고, 다듬은 제목도 요청 판 거름(연락처·금액·링크·단톡방)을 지나야 올라가요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const admin = await signIn(call);
  for (const n of [1, 2, 3]) await studio(call, n, n, ASK);
  await tick(env, AFTER);
  const id = (await call("GET", "/api/admin/growth/needs", undefined, admin)).data.items[0].id;
  const market = (title) => call("POST", `/api/admin/growth/needs/${id}`, { action: "market", ...(title !== undefined ? { title } : {}) }, admin);
  let r = await market();
  assert.deepEqual([r.status, r.data.error, r.data.field], [400, "title_first", "title"]);
  for (const [bad, why] of [["달력 칸 그라데이션 010-1234-5678", "phone"], ["칸 배경 bit.ly/abc", "link"], ["오픈 채팅방에서 같이 만들어요", "chat"]]) {
    r = await market(bad);
    assert.deepEqual([r.status, r.data.error], [400, why], bad);
  }
  assert.equal((await call("POST", `/api/admin/growth/needs/${id}`, { action: "title", title: "자세한 건 www.example.com" }, admin)).data.error, "link");
  r = await market("달력 칸 배경 그라데이션");
  assert.deepEqual([r.status, r.data.state, r.data.titled], [200, "market", true]);
  const row = (await call("GET", "/api/recipes/requests?style=SHIFT")).data.items[0];
  assert.equal(row.body, "[달력 칸 › 색] 달력 칸 배경 그라데이션");
  assert.doesNotMatch(row.body, /팀장님/);
  // 내렸다 다시 올리면 다듬은 제목 그대로(같은 줄을 다시 보여요).
  assert.equal((await call("POST", `/api/admin/growth/needs/${id}`, { action: "unmarket" }, admin)).data.state, "open");
  assert.equal((await market()).data.state, "market");
  assert.equal((await call("GET", "/api/recipes/requests?style=SHIFT")).data.items.length, 1);
});

test("[RVQ-29] SVC-17 Claude 열쇠는 '올리기 전 확인'을 읽고 제목을 다듬어 올리기만 해요(하지 않기·합치기·정식은 사람)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const owner = await signIn(call);
  for (const n of [1, 2, 3]) await studio(call, n, n, ASK);
  await tick(env, AFTER);
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${made.key}`, "x-admin": "1" };
  const list = (await call("GET", "/api/admin/growth/needs?state=ready", undefined, claude)).data;
  assert.equal(list.items.length, 1);
  const id = list.items[0].id;
  for (const action of ["decline", "merge", "official", "build", "unmarket", "reopen"]) {
    assert.equal((await call("POST", `/api/admin/growth/needs/${id}`, { action, note: "x", version: "2.0.0" }, claude)).status, 403, action);
  }
  const r = (await call("POST", `/api/admin/growth/needs/${id}`, { action: "market", title: "달력 칸 배경 그라데이션" }, claude)).data;
  assert.equal(r.state, "market");
  assert.match((await call("GET", "/api/admin/desk", undefined, claude)).data.rules.join("\n"), /올리기 전 확인/);
});
