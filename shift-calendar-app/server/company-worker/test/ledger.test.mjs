// 1.38 [DESK-02][DESK-03] 운영 장부: 모든 종류 한 줄, 번호·겹침·묶음·단계·답·담당·기한·기록, 거르기·CSV, 정리함·할 일함을 하나로. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { askOwner, ledgerCsv, screenPath } from "../src/ledger.js";

const ask = (call, n, text, kind = "bug", app = "1.37.0 (51)") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app, model: "samsung SM-S921N", diag: "알람 점검 기록" } }).then((r) => r.data.id);
const ledger = (call, h, q = "") => call("GET", `/api/admin/ledger${q}`, undefined, h).then((r) => r.data);
const row = (d, id) => d.items.find((r) => r.id === id);

test("[DESK-02] askOwner and screenPath: what Claude must not answer alone, and the screen the app put first", () => {
  assert.equal(askOwner("bug", "결제했는데 프로가 안 켜져요 환불해 주세요").key, "money");
  assert.equal(askOwner("question", "제 개인정보 지워 주세요").key, "privacy");
  assert.equal(askOwner("bug", "이 앱 진짜 최악이에요 사기 아니에요?").key, "angry");
  assert.equal(askOwner("company", "우리 회사는 4조 2교대예요").key, "company");
  assert.equal(askOwner("idea", "보상형 광고 대신 다른 방법도 있으면 좋겠어요"), null);
  assert.equal(askOwner("bug", "알람이 안 울려요"), null);
  assert.equal(screenPath("[설정 > 알람] 소리가 안 나요"), "설정 > 알람");
  assert.equal(screenPath("소리가 안 나요"), null);
});

test("[DESK-02] one line per inquiry: a K number that stays, overlaps with reasons, Claude's turn, then 묶음 → 개발 중 → 출시 전 → 출시됨", async () => {
  const { call } = server();
  const admin = await signIn(call);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.37.0" }, admin)).status, 200);
  const old = (await call("POST", "/api/admin/dev", { title: "잠금 화면에서 알람 소리가 안 나요", body: "잠금 화면 알람 소리" }, admin)).data.id;
  const q1 = await ask(call, 1, "[설정 > 알람] 잠금 화면에서 알람 소리가 안 나요");
  const q2 = await ask(call, 2, "달력 글씨가 너무 작아요", "idea");
  let d = await ledger(call, admin, "?view=open");
  const r1 = row(d, q1);
  assert.match(r1.no, /^K-\d{4}$/);
  assert.equal(r1.path, "설정 > 알람");
  assert.equal(r1.app, "1.37.0");
  assert.equal(r1.turn, "claude");
  assert.equal(r1.answer.key, "none");
  assert.equal(r1.sort.pile, "known");
  assert.equal(r1.overlap[0].id, old, "the 개발사항 that reads the same is the first overlap");
  assert.match(r1.overlap[0].why, /글 \d+%/);
  assert.ok(r1.due, "a waiting question has its answer target");
  const no1 = r1.no;
  // The numbers stay when the list is read again, and a new line gets the next one.
  d = await ledger(call, admin, "?view=open");
  assert.equal(row(d, q1).no, no1);
  assert.ok(row(d, q2).n !== row(d, q1).n);
  // The 개발사항 itself is a line too.
  assert.equal(d.items.find((r) => r.key === `dev:${old}`).stage, "devwait");

  // 정리함 step from the line: tie to the 개발사항 and answer → 묶음, 개발 대기, the answer is 보냄 but the line stays open.
  await call("POST", "/api/admin/inquiries/sort/apply", { actions: [{ do: "reply", id: q1, item: old, text: "개발 목록에 올렸어요" }] }, admin);
  d = await ledger(call, admin, "?view=open");
  let r = row(d, q1);
  assert.equal(r.bundle.id, old);
  assert.equal(r.stage, "devwait");
  assert.equal(r.turn, "claude");
  assert.equal(r.closed, false);
  await call("POST", `/api/admin/dev/${old}`, { status: "doing" }, admin);
  assert.equal(row(await ledger(call, admin, "?view=dev"), q1).stage, "doing");
  await call("POST", "/api/admin/inquiries/handoff/result", { text: `${old} 완료 1.38.0`, notify: true }, admin);
  d = await ledger(call, admin, "?view=ship");
  r = row(d, q1);
  assert.equal(r.stage, "built");
  assert.equal(r.turn, "ship");
  assert.ok(d.items.some((x) => x.key === `dev:${old}`), "출시 대기 shows the 개발사항 too");
  await call("POST", "/api/admin/desk/ship", { version: "1.38.0", notify: true }, admin);
  d = await ledger(call, admin, "?view=closed");
  r = row(d, q1);
  assert.equal(r.stage, "shipped");
  assert.equal(r.closed, true);
  // 판 거르기: 1.37 finds the app version, 1.38 the fixed one.
  assert.ok(row(await ledger(call, admin, "?view=all&v=1.37"), q1));
  assert.ok((await ledger(call, admin, "?view=all&v=1.38")).items.some((x) => x.key === `dev:${old}`));
  // Search by number.
  assert.equal((await ledger(call, admin, `?view=all&q=${no1}`)).items[0].id, q1);
});

test("[DESK-02] money, law and privacy go to the owner; a safety report is due in 24 hours", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const money = await ask(call, 1, "결제했는데 프로가 안 켜져요. 환불해 주세요", "bug");
  const law = await ask(call, 2, "마을에 불법 촬영 사진이 올라왔어요", "other");
  const d = await ledger(call, admin, "?view=owner");
  assert.deepEqual(new Set(d.items.filter((x) => x.type === "inquiry").map((x) => x.id)), new Set([money, law]));
  assert.equal(row(d, money).answer.key, "owner");
  assert.equal(row(d, money).answer.why, "환불·결제·보상");
  const l = row(d, law);
  assert.ok(Date.parse(l.due) - Date.parse(l.at) <= 24 * 3600 * 1000);
  assert.equal((await ledger(call, admin, "?view=claude")).items.filter((x) => x.type === "inquiry").length, 0);
});

test("[DESK-03] 끝낼 이유는 하나, 되돌리기, 할 일함과 같은 기록; 빼 둔 문의는 7일 뒤 다시 와요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-05T00:00:00Z") });
  const { call } = server();
  const admin = await signIn(call);
  const q = await ask(call, 1, "위젯 색을 바꾸고 싶어요", "idea");
  const key = `inquiry:${q}`;
  assert.equal((await call("POST", "/api/admin/ledger/state", { keys: [key], state: "done" }, admin)).status, 400, "끝낼 때는 이유를 꼭");
  assert.equal((await call("POST", "/api/admin/ledger/state", { keys: [key], state: "seeing" }, admin)).status, 200);
  assert.equal(row(await ledger(call, admin, "?view=open"), q).stage, "sorted");
  assert.equal((await call("POST", "/api/admin/ledger/state", { keys: [key], state: "done", reason: "duplicate", note: "K-0001과 같음" }, admin)).status, 200);
  let r = row(await ledger(call, admin, "?view=closed"), q);
  assert.equal(r.stage, "closed");
  assert.equal(r.stateReasonName, "중복");
  // 할 일함 reads the same state.
  const w = (await call("GET", "/api/admin/work?state=done", undefined, admin)).data;
  assert.ok(w.items.some((x) => x.key === key));
  // The log has it, and 되돌리기 brings back 보는 중.
  let log = (await call("GET", `/api/admin/ledger/log?key=${encodeURIComponent(key)}`, undefined, admin)).data;
  assert.match(log.items.map((x) => x.what).join("\n"), /끝 · 중복 · K-0001과 같음/);
  assert.match(log.items.map((x) => x.what).join("\n"), /문의 · 위젯 색/);
  assert.equal(log.undo.to, "seeing");
  assert.equal((await call("POST", "/api/admin/ledger/undo", { key }, admin)).data.state, "seeing");
  r = row(await ledger(call, admin, "?view=open"), q);
  assert.equal(r.state, "seeing");
  assert.equal((await call("POST", "/api/admin/ledger/undo", { key }, admin)).status, 409, "a 되돌림 is not undone again");
  log = (await call("GET", `/api/admin/ledger/log?key=${encodeURIComponent(key)}`, undefined, admin)).data;
  assert.match(log.items[0].what, /되돌림/);

  // 빼 두기: out of 정리함 now, back after 7 days while it still waits.
  const q2 = await ask(call, 2, "메모 글씨를 굵게 하고 싶어요", "idea");
  await call("POST", "/api/admin/inquiries/sort/apply", { actions: [{ do: "skip", id: q2 }] }, admin);
  const piled = (d) => Object.values(d.piles).flat().some((x) => x.id === q2 || x.group?.includes(q2));
  assert.equal(piled((await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data), false);
  assert.equal(row(await ledger(call, admin, "?view=open"), q2).sort, null);
  t.mock.timers.tick(8 * 24 * 3600 * 1000);
  Object.assign(admin, await signIn(call));
  const back = (await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data;
  assert.ok(back.piles.new.some((g) => g.group.includes(q2)));
  r = row(await ledger(call, admin, "?view=open"), q2);
  assert.equal(r.skipped.back, true);
  assert.equal(r.sort.back, true);
});

test("[DESK-02] summary, CSV, and the Claude key: its own lines only, and it can close what it handled", async (t) => {
  // [RVD-01] A month with 이달 할 일: in February there is none, so on a real February clock the owner's line below broke.
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  const { call } = server();
  const owner = await signIn(call);
  const q = await ask(call, 1, "=HYPERLINK(\"x\") 알람 시간이 이상해요, 쉼표도 있어요");
  const key = `inquiry:${q}`;
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${made.key}`, "x-admin": "1" };
  const d = await ledger(call, claude, "?view=all");
  assert.ok(d.items.length >= 1);
  assert.ok(d.items.every((x) => ["inquiry", "dev", "studio_need"].includes(x.type)), "the key sees only 문의·개발사항·스튜디오 요구");
  assert.ok((await ledger(call, owner, "?view=all")).items.some((x) => !["inquiry", "dev", "studio_need"].includes(x.type)), "the owner sees 이달 할 일 too");
  assert.equal((await call("POST", "/api/admin/ledger/summary", { key, text: "알람 시간이 표시와 달라요" }, claude)).status, 200);
  let r = row(await ledger(call, owner, "?view=open"), q);
  assert.equal(r.summary, "알람 시간이 표시와 달라요");
  assert.equal(r.summaryBy, "Claude");
  assert.equal((await call("POST", "/api/admin/ledger/state", { keys: [key], state: "done", reason: "answered" }, claude)).status, 200);
  r = row(await ledger(call, owner, "?view=closed"), q);
  assert.equal(r.lastMove.byKind, "claude", JSON.stringify(r.lastMove));
  // CSV: BOM, header, a formula is not a formula, commas quoted.
  const csv = await call("GET", "/api/admin/ledger.csv?view=all", undefined, claude);
  assert.equal(csv.status, 200);
  assert.match(csv.headers.get("content-type"), /text\/csv/);
  // (text() drops the BOM; ledgerCsv below keeps it for Excel.)
  assert.ok(csv.text.replace(/^\uFEFF/, "").startsWith("번호,들어온 날,종류,요약"));
  assert.ok(ledgerCsv([]).startsWith("\uFEFF번호"));
  assert.ok(!/,=HYPERLINK/.test(csv.text));
  assert.equal(ledgerCsv([]).split("\r\n").length, 2);
  // Out of scope for the key: the old 할 일함 write path.
  assert.equal((await call("POST", "/api/admin/work", { keys: [key], state: "new" }, claude)).status, 403);
});
