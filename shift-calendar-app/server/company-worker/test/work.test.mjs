// 1.36 [GRW-07] 할 일함: 한 대기열, 상태(새로→보는 중→기다림→끝), 끝낼 때 이유, 다시 열림, 기록, 권한. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400000;
const ask = (call, n, kind, text) => call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "1.36.0", model: "P", android: "16" } }, from(n));

test("[GRW-07] one list from every source, states with reasons, nothing lost: a done item comes back when it moves or is left", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  let admin = await signIn(call);
  const q = (await ask(call, 1, "question", "달력에서 근무를 바꾸는 법을 알고 싶어요")).data;
  await ask(call, 2, "studio", "[스튜디오 › 달력 칸 › 색] 칸 배경 그라데이션");
  let w = (await call("GET", "/api/admin/work", undefined, admin)).data;
  const keys = w.items.map((x) => x.key);
  assert.ok(keys.includes(`inquiry:${q.id}`));
  // The studio ask is in its group, not a second line of its own.
  assert.equal(w.items.filter((x) => x.type === "studio_need").length, 1);
  assert.equal(w.items.filter((x) => x.type === "inquiry").length, 1);
  assert.ok(w.items.filter((x) => ["inquiry", "studio_need"].includes(x.type)).every((x) => x.state === "new"));
  const open0 = w.counts.open;
  const key = `inquiry:${q.id}`;
  // 끝 needs a reason; 기다림 too.
  assert.equal((await call("POST", "/api/admin/work", { keys: [key], state: "done" }, admin)).data.error, "reason");
  assert.equal((await call("POST", "/api/admin/work", { keys: [key], state: "waiting", reason: "x" }, admin)).data.error, "reason");
  assert.deepEqual((await call("POST", "/api/admin/work", { keys: [key], state: "seeing" }, admin)).data.before, { [key]: "new" });
  await call("POST", "/api/admin/work", { keys: [key], state: "done", reason: "answered", note: "도움말 링크" }, admin);
  w = (await call("GET", "/api/admin/work", undefined, admin)).data;
  assert.equal(w.counts.open, open0 - 1);
  assert.equal(w.summary.doneToday, 1);
  assert.equal((await call("GET", "/api/admin/work?state=done", undefined, admin)).data.items[0].reasonName, "답함");
  // Still waiting in its source a week later: it comes back as 다시 열림, at the top.
  t.mock.timers.setTime(AFTER + 8 * DAY);
  admin = await signIn(call);
  w = (await call("GET", "/api/admin/work", undefined, admin)).data;
  const back = w.items.find((x) => x.key === key);
  assert.equal(back.state, "new");
  assert.equal(back.reopened, 1);
  assert.equal(w.counts.reopened, 1);
  assert.equal(w.items[0].late || w.items[0].reopened > 0, true);
  const log = (await call("GET", `/api/admin/work/log?key=${encodeURIComponent(key)}`, undefined, admin)).data.items;
  assert.deepEqual(log.map((x) => x.action), ["reopen", "done", "seeing"]);
  assert.equal(log[0].reasonName, "7일 지나도 남아 있음");
  // The asker writes again after 끝: back at once.
  await call("POST", "/api/admin/work", { keys: [key], state: "done", reason: "answered" }, admin);
  t.mock.timers.setTime(AFTER + 8 * DAY + 60000);
  await call("POST", `/api/inquiries/${q.id}/messages`, { v: 1, device: device(1), text: "그래도 안 돼요" }, { ...from(1), "x-device": device(1) });
  w = (await call("GET", "/api/admin/work", undefined, admin)).data;
  assert.equal(w.items.find((x) => x.key === key).reopened, 2);
});

test("[GRW-07] waiting goes late after a week; group filters; an unknown key is refused", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  let admin = await signIn(call);
  const q = (await ask(call, 1, "bug", "알람이 안 울려요")).data;
  const key = `inquiry:${q.id}`;
  await call("POST", "/api/admin/work", { keys: [key], state: "waiting", reason: "user" }, admin);
  let w = (await call("GET", "/api/admin/work?state=waiting", undefined, admin)).data;
  assert.equal(w.items[0].reasonName, "사용자 답 기다림");
  assert.equal(w.items[0].late, false);
  assert.equal((await call("GET", "/api/admin/work?group=report", undefined, admin)).data.items.length, 0);
  t.mock.timers.setTime(AFTER + 8 * DAY);
  admin = await signIn(call);
  w = (await call("GET", "/api/admin/work?state=late", undefined, admin)).data;
  assert.equal(w.items[0].key, key);
  assert.equal((await call("POST", "/api/admin/work", { keys: ["inquiry:nope"], state: "seeing" }, admin)).status, 404);
});
