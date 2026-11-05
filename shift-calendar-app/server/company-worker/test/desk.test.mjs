// 1.37 [ADM2-01]~[ADM2-05] 운영 장부: 나간 판, 흐름(관제), Claude 운영 열쇠, 장부. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { isOut, keyAllows } from "../src/desk.js";

const ask = (call, n, text, kind = "bug", app = "1.36.1 (50)") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app, model: "samsung SM-S921N", diag: "알람 점검 기록" } }).then((r) => r.data.id);
const asker = (call, id, n) => call("GET", `/api/inquiries/${id}`, undefined, { "x-device": device(n) }).then((r) => r.data);

test("[ADM2-02] isOut: a fix is out only up to the shipped version; none written yet keeps 1.36's way", () => {
  assert.equal(isOut("1.37.0", null), true);
  assert.equal(isOut("1.37.0", { v: "1.36.1" }), false);
  assert.equal(isOut("1.37.0", "1.37.0"), true);
  assert.equal(isOut("1.36.0", "1.37.0"), true);
  assert.equal(isOut(null, "1.37.0"), false);
});

test("[ADM2-02] 나간 판: before it ships the app says 만드는 중 and the answer says it comes in the update; saving the version answers them all", async () => {
  const { call } = server();
  const admin = await signIn(call);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.36.1" }, admin)).status, 200);
  const q = await ask(call, 1, "잠금 화면에서 알람 소리가 안 나요");
  const item = (await call("POST", "/api/admin/dev", { title: "잠금 화면 알람 소리", inquiry: q }, admin)).data.id;
  const r = (await call("POST", "/api/admin/inquiries/handoff/result", { text: `${item} 완료 1.37.0`, notify: true }, admin)).data;
  assert.equal(r.answered, 1);
  let view = await asker(call, q, 1);
  assert.equal(view.progress.stage, "building");
  assert.match(view.messages.at(-1).text, /1\.37\.0 업데이트에 들어가요/);
  assert.doesNotMatch(view.messages.at(-1).text, /Play 스토어/);
  // 흐름: it waits in 고침 · 출시 전.
  let f = (await call("GET", "/api/admin/flow", undefined, admin)).data;
  assert.equal(f.shipped.v, "1.36.1");
  assert.equal(f.stages.find((s) => s.key === "built").n, 1);
  // 정리함: the same problem again is 개발 목록에 있음, not 이미 고침 (no "update" before it is out).
  await ask(call, 2, "잠금 화면에서 알람 소리가 안 나요 진동만", "bug", "1.36.1 (50)");
  const sorted = (await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data;
  assert.equal(sorted.counts.fixed, 0);
  assert.equal(sorted.counts.known, 1);
  // Saving 1.37.0: first how many, then the answers.
  const dry = (await call("POST", "/api/admin/desk/ship", { version: "1.37.0", dry: true }, admin)).data;
  assert.equal(dry.answers, 1);
  assert.equal(dry.was, "1.36.1");
  const shipped = (await call("POST", "/api/admin/desk/ship", { version: "1.37.0", notify: true }, admin)).data;
  assert.equal(shipped.answered, 1);
  view = await asker(call, q, 1);
  assert.equal(view.progress.stage, "released");
  assert.equal(view.progress.version, "1.37.0");
  assert.match(view.messages.at(-1).text, /1\.37\.0 버전이 나왔어요/);
  // Saved again: nobody is told twice.
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.37.0", notify: true }, admin)).data.answered, 0);
  f = (await call("GET", "/api/admin/flow", undefined, admin)).data;
  assert.equal(f.stages.find((s) => s.key === "built").n, 0);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.37" }, admin)).status, 400);
});

test("[ADM2-01][ADM2-05] 흐름: each stage's count, the oldest wait, 막힘, and the month's numbers", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-01T00:00:00Z") });
  const { call } = server();
  const admin = await signIn(call);
  const a = await ask(call, 1, "위젯에 내일 근무도 보이면 좋겠어요", "idea");
  await ask(call, 2, "달력 글자가 작아요", "question");
  t.mock.timers.tick(30 * 3600 * 1000);
  const f = (await call("GET", "/api/admin/flow", undefined, admin)).data;
  const answer = f.stages.find((s) => s.key === "answer");
  assert.equal(answer.n, 2);
  assert.equal(answer.hours, 30);
  assert.equal(answer.stuck, true);
  assert.equal(f.next, "answer");
  assert.equal(f.metrics.inMonth, 2);
  assert.equal(f.metrics.slaKept, 0);
  assert.equal(f.metrics.weeks.length, 8);
  await call("POST", `/api/admin/inquiries/${a}/reply`, { text: "확인했어요" }, admin);
  const g = (await call("GET", "/api/admin/flow", undefined, admin)).data;
  assert.equal(g.stages.find((s) => s.key === "answer").n, 1);
  assert.equal(g.metrics.firstAnswerHours, 30);
  assert.equal(g.metrics.weeks.at(-1).out, 1);
});

test("[ADM2-03] Claude 운영 열쇠: the owner makes it once, it reads and answers inquiries, and nothing else", async () => {
  const { call } = server();
  const owner = await signIn(call);
  const made = (await call("POST", "/api/admin/keys", { label: "Claude 프로젝트" }, owner)).data;
  assert.match(made.key, /^bko_[A-Za-z0-9]{40}$/);
  // The key shows once: the list has no key, only its name and use.
  const list = (await call("GET", "/api/admin/keys", undefined, owner)).data;
  assert.equal(list.keys.length, 1);
  assert.ok(!JSON.stringify(list).includes(made.key));
  const claude = { authorization: `Bearer ${made.key}`, "x-admin": "1" };
  const q = await ask(call, 1, "잠금 화면에서 알람 소리가 안 나요");
  const desk = await call("GET", "/api/admin/desk", undefined, claude);
  assert.equal(desk.status, 200);
  assert.equal(desk.data.sort.total, 1);
  assert.ok(desk.data.rules.length >= 3);
  assert.match(desk.data.handoff, /결과 적는 법/);
  const one = (await call("GET", `/api/admin/inquiries/${q}`, undefined, claude)).data;
  assert.equal(one.context["알람 점검"], undefined);
  assert.equal(one.context["앱"], "1.36.1 (50)");
  assert.equal((await call("POST", `/api/admin/inquiries/${q}/reply`, { text: "확인 중이에요" }, claude)).status, 200);
  assert.equal((await asker(call, q, 1)).messages.at(-1).text, "확인 중이에요");
  const audit = (await call("GET", "/api/admin/audit", undefined, owner)).data;
  assert.ok(JSON.stringify(audit).includes("Claude"));
  // Outside its ground: admins, switches, deleting, photos, the key list itself.
  for (const [m, p] of [["GET", "/api/admin/admins"], ["GET", "/api/admin/keys"], ["POST", "/api/admin/flags/x"], ["GET", "/api/admin/server"],
    ["POST", "/api/admin/inquiries/bulk"], ["GET", `/api/admin/inquiries/${q}/photos/1`], ["POST", "/api/admin/inquiries/sort/auto"]]) {
    assert.equal((await call(m, p, m === "POST" ? {} : undefined, claude)).status, 403, `${m} ${p}`);
  }
  // A wrong or cut key is a 401; a revoked one too.
  assert.equal((await call("GET", "/api/admin/desk", undefined, { authorization: "Bearer bko_wrong" })).status, 401);
  assert.equal((await call("POST", `/api/admin/keys/${made.id}/revoke`, {}, owner)).status, 200);
  assert.equal((await call("GET", "/api/admin/desk", undefined, claude)).status, 401);
  // Not the owner: no keys.
  assert.equal((await call("POST", "/api/admin/keys", {}, claude)).status, 401);
});

test("[ADM2-03] the key's ground is a fixed list of routes", () => {
  assert.equal(keyAllows("/desk", "GET"), true);
  assert.equal(keyAllows("/dev/dAAAAAAAAAAAAAAA", "POST"), true);
  assert.equal(keyAllows("/dev/dAAAAAAAAAAAAAAA/delete", "POST"), false);
  assert.equal(keyAllows("/dev/bulk", "POST"), false);
  assert.equal(keyAllows("/inquiries/AAAAAAAAAAAAAAAAAAAAAA/visibility", "POST"), false);
  assert.equal(keyAllows("/keys", "POST"), false);
  assert.equal(keyAllows("/desk", "DELETE"), false);
});

test("[ADM2] a database made by an older server takes the new tables: the ALTERs in SCHEMA no longer fail it (1.37 hotfix)", async () => {
  const { env, DB, call } = server();
  assert.equal((await call("GET", "/api/admin/session")).status, 200);
  // As if this database was made by the server before this one: a different schema version, all columns already there.
  await DB.prepare("UPDATE settings SET value = 'older' WHERE key = 'schema'").run();
  const worker = (await import("../src/index.js")).default;
  // A new DB_ROOT is a new key for ready()'s once-per-database memo, so the tables are made again as on a fresh worker copy.
  const res = await worker.fetch(new Request("https://company.example/api/admin/session"), { ...env, DB_ROOT: {} });
  assert.equal(res.status, 200);
  assert.equal((await DB.prepare("SELECT COUNT(*) AS n FROM admin_keys").first()).n, 0);
});

test("[ADM2-08] 복사 두 번으로 끝: 넘길 글에 문의가 있고, 결과 덩어리 하나로 답·묶기·새 개발사항·빼기·개발 상태가 처리돼요", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const q1 = await ask(call, 1, "백업 파일은 어디서 가져오나요?", "question");
  const q2 = await ask(call, 2, "위젯에 내일 근무도 보이면 좋겠어요", "idea");
  const q3 = await ask(call, 3, "위젯에서 내일 근무까지 보여 주세요", "idea");
  const q4 = await ask(call, 4, "ㅋㅋㅋ", "other");
  const q5 = await ask(call, 5, "잠금 화면 알람 소리가 안 나요");
  const item = (await call("POST", "/api/admin/dev", { title: "잠금 화면 알람 소리" }, admin)).data.id;
  const h = (await call("GET", "/api/admin/inquiries/handoff", undefined, admin)).data;
  assert.equal(h.asks, 5);
  for (const q of [q1, q2, q3, q4, q5]) assert.ok(h.text.includes(`q${q}`), q);
  assert.match(h.text, /추천 답:/);
  assert.match(h.text, /q<문의 id> 새개발/);
  const block = [
    "결과",
    `q${q1} 답 설정 › 백업에서 '가져오기'를 눌러요.\\n파일을 고르면 끝이에요.`,
    `q${q2}, q${q3} 새개발 위젯에 내일 근무 | 답: 개발 목록에 올렸어요.`,
    `q${q4} 빼기`,
    `q${q5} 개발 ${item} | 답: 같은 문제를 고치고 있어요.`,
    `${item} 완료 1.37.0 | 답: 1.37.0에서 고쳐요. 나오면 알려 드릴게요.`,
    "qZZZZZZZZZZZZZZZZZZZZZZ 답 없는 문의",
  ].join("\n");
  const dry = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block, dry: true }, admin)).data;
  assert.equal(dry.asks.length, 5);
  assert.equal(dry.asks.at(-1).problem, "없는 문의");
  assert.equal(dry.lines[0].text, "1.37.0에서 고쳐요. 나오면 알려 드릴게요.");
  const r = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block, notify: true }, admin)).data;
  assert.equal(r.changed, 1);
  assert.equal(r.created.length, 1);
  assert.equal(r.skipped, 1);
  // q1 답 (줄바꿈 그대로), q2·q3 새 개발사항과 답, q5 묶기와 답(문의 줄) — 개발사항 줄의 답은 q5가 묶이기 전이라 아직 없음.
  assert.equal((await asker(call, q1, 1)).messages.at(-1).text, "설정 › 백업에서 '가져오기'를 눌러요.\n파일을 고르면 끝이에요.");
  assert.equal((await asker(call, q2, 2)).messages.at(-1).text, "개발 목록에 올렸어요.");
  assert.equal((await asker(call, q3, 3)).progress.stage, "planned");
  assert.equal((await asker(call, q5, 5)).messages.at(-1).text, "같은 문제를 고치고 있어요.");
  assert.equal((await asker(call, q4, 4)).messages.length, 1);
  const sorted = (await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data;
  assert.equal(sorted.total, 0);
  // The 개발사항 Claude made from the block is already Claude's: nothing new to hand over.
  const again = (await call("GET", "/api/admin/inquiries/handoff", undefined, admin)).data;
  assert.equal(again.asks, 0);
  assert.equal(again.items.length, 0);
});

test("[ADM2-09] 개발 기록: 결과의 기록 줄이 개발사항에 남고, 다음 넘길 글의 ‘개발 기록’에 판·나감·반려 이유·기록과 함께 실려요", async () => {
  const { call } = server();
  const admin = await signIn(call);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.36.1" }, admin)).status, 200);
  const a = (await call("POST", "/api/admin/dev", { title: "잠금 화면 알람 소리", body: "잠금 화면에서 소리가 안 나요" }, admin)).data.id;
  const b = (await call("POST", "/api/admin/dev", { title: "위젯 글자 크기" }, admin)).data.id;
  const c = (await call("POST", "/api/admin/dev", { title: "달력 첫 화면에 날씨" }, admin)).data.id;
  const block = [
    `${a} 완료 1.37.0 | 기록: PR #171 커밋 abc1234, AlarmService 소리 채널 고침 | 답: 1.37.0에 들어가요.`,
    `${b} 기록 PR #170\\n위젯 글자 크기 설정 추가, 에뮬레이터 확인`,
    `${b} 완료 1.36.1`,
    `${c} 반려 날씨는 개인정보 처리방침 범위 밖이에요`,
    `${c} 기록`,
  ].join("\n");
  const dry = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block, dry: true }, admin)).data;
  assert.equal(dry.lines[0].note, "PR #171 커밋 abc1234, AlarmService 소리 채널 고침");
  assert.equal(dry.lines[0].text, "1.37.0에 들어가요.");
  assert.equal(dry.lines[1].status, null);
  assert.equal(dry.lines[1].note, "PR #170\n위젯 글자 크기 설정 추가, 에뮬레이터 확인");
  assert.equal(dry.lines.at(-1).problem, "기록이 비어 있어요");
  const r = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block }, admin)).data;
  assert.equal(r.noted, 2);
  assert.equal(r.changed, 3);
  const item = (await call("GET", `/api/admin/dev/${a}`, undefined, admin)).data;
  assert.ok(JSON.stringify(item).includes("AlarmService 소리 채널 고침"));
  const h = (await call("GET", "/api/admin/inquiries/handoff", undefined, admin)).data;
  assert.match(h.text, /## 개발 기록 \(지난 개발사항 3개/);
  assert.ok(h.text.includes(`${a} · 잠금 화면 알람 소리 · 완료 1.37.0 (아직 안 나감)`));
  assert.ok(h.text.includes(`${b} · 위젯 글자 크기 · 완료 1.36.1 (나감)`));
  assert.ok(h.text.includes(`${c} · 달력 첫 화면에 날씨 · 반려: 날씨는 개인정보 처리방침 범위 밖이에요`));
  assert.match(h.text, /기록: \d{4}-\d\d-\d\d .+: PR #171 커밋 abc1234/);
  assert.match(h.text, /내용: 잠금 화면에서 소리가 안 나요/);
  assert.match(h.text, /기록 무엇을 어디서 어떻게 고쳤는지/);
});
