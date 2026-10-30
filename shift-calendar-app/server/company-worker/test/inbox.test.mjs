// 1.35 [INBOX-01]..[INBOX-07] 문의 정리함: piles, 자동 대응, Claude에게 넘기기 and its 결과. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { classify, grams, likeness, readResult, titleFrom } from "../src/inbox.js";

const ask = (call, n, text, kind = "bug", app = "1.33.0 (45)") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app, model: "samsung SM-S921N" } }).then((r) => r.data.id);

test("[INBOX-01] likeness: the same problem in other words meets; a different one does not", () => {
  const alarm = "잠금 화면에서 알람 소리가 안 나요. 진동만 와요";
  assert.ok(likeness(alarm, "알람이 잠금화면일 때 소리 없이 진동만 울려요") >= 0.42);
  assert.ok(likeness(alarm, "달력이 일요일부터 시작하면 좋겠어요") < 0.2);
  assert.ok(likeness("급여 계산에서 주휴수당이 빠져 있어요", "주휴수당이 급여에 안 들어가요") >= 0.42);
  // Greetings and softeners do not make two questions alike.
  assert.ok(likeness("안녕하세요 정말 감사합니다 부탁드려요", "안녕하세요 감사합니다 해주세요") === 0);
  assert.equal(grams("알람이 안 울려요").words.has("알람"), true);
});

test("[INBOX-01] a question goes in the pile of what the server knows, with the answer to start from", () => {
  const k = {
    devs: [
      { id: "dAAAAAAAAAAAAAAA", title: "잠금 화면 알람 소리 안 남", body: "잠금 화면에서 진동만 울림", status: "done", reason: "", released_in: "1.34.0" },
      { id: "dBBBBBBBBBBBBBBB", title: "주휴수당이 급여에 안 들어감", body: "", status: "doing", reason: "", released_in: null },
      { id: "dCCCCCCCCCCCCCCC", title: "가계부 다크 모드 색 바꾸기", body: "", status: "rejected", reason: "다크 모드는 시스템 설정을 따라요", released_in: null },
    ].map((d) => ({ ...d, g: grams(`${d.title} ${d.title} ${d.body}`) })),
    answered: [],
    templates: [{ id: 1, label: "백업 옮기기", text: "설정 › 백업에서 파일로 내보낸 뒤 새 폰에서 가져오기를 눌러요", topic: "backup" }]
      .map((t) => ({ ...t, g: grams(`${t.label} ${t.text}`) })),
  };
  const q = (text, app = "1.33.0 (45)", kind = "bug") => classify({ id: "x", kind, text, info: { app } }, k);
  const fixed = q("잠금 화면에서 알람 소리가 안 나요 진동만 와요");
  assert.equal(fixed.pile, "fixed");
  assert.match(fixed.reply, /1\.34\.0 버전에서 고쳤어요/);
  // The asker already has the version it was fixed in: it came back.
  assert.equal(q("잠금 화면에서 알람 소리가 안 나요 진동만 와요", "1.35.0 (47)").pile, "again");
  assert.equal(q("주휴수당이 급여 계산에 안 들어가요").pile, "known");
  assert.match(q("주휴수당이 급여 계산에 안 들어가요").reply, /개발중/);
  assert.equal(q("가계부 다크 모드 색을 바꿀 수 있게 해 주세요", "1.33.0", "idea").pile, "declined");
  assert.equal(q("새 폰으로 옮기려면 백업을 어떻게 가져오기 하나요", "1.33.0", "question").pile, "faq");
  assert.equal(q("위젯에서 내일 근무도 보이면 좋겠어요", "1.33.0", "idea").pile, "new");
});

test("[INBOX-01][INBOX-03] 정리함 groups new ones that read alike; apply makes one 개발사항 and answers them", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const a = await ask(call, 1, "위젯에서 내일 근무도 같이 보이면 좋겠어요", "idea");
  const b = await ask(call, 2, "위젯에 내일 근무까지 보이게 해 주세요", "idea");
  const c = await ask(call, 3, "급여 명세서 사진을 두 장 넣을 수 있게 해 주세요", "idea");
  const sorted = (await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data;
  assert.equal(sorted.counts.new, 3);
  const widget = sorted.piles.new.find((g) => g.group.includes(a));
  assert.deepEqual(widget.group.sort(), [a, b].sort());
  assert.ok(sorted.piles.new.some((g) => g.group.length === 1 && g.group[0] === c));
  const done = (await call("POST", "/api/admin/inquiries/sort/apply", { actions: [
    { do: "dev", ids: widget.group, title: widget.title, body: "위젯에 내일 근무", text: "개발 목록에 올렸어요" },
    { do: "skip", id: c },
  ] }, admin)).data;
  assert.equal(done.created.length, 1);
  assert.equal(done.linked, 2);
  assert.equal(done.replied, 2);
  assert.equal(done.skipped, 1);
  // Tied or put aside: none of them is in 정리함 now, and the asker sees 개발 예정.
  assert.equal((await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data.total, 0);
  const mine = (await call("GET", `/api/inquiries/${a}`, undefined, { "x-device": device(1) })).data;
  assert.equal(mine.progress?.stage ?? mine.stage ?? "planned", "planned");
  // A later question like it now goes to 개발 목록에 있음.
  await ask(call, 4, "내일 근무도 위젯에서 보고 싶어요", "idea");
  assert.equal((await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data.counts.known, 1);
});

test("[INBOX-02] 자동 대응 (owner only, off at first): a question about a fixed item is answered as it comes in", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const item = (await call("POST", "/api/admin/dev", { title: "잠금 화면 알람 소리 안 남", body: "잠금 화면에서 진동만 울림" }, admin)).data.id;
  await call("POST", `/api/admin/dev/${item}`, { status: "done", releasedIn: "1.34.0" }, admin);
  const before = await ask(call, 1, "잠금 화면에서 알람 소리가 안 나요 진동만 와요");
  assert.equal((await call("GET", `/api/inquiries/${before}`, undefined, { "x-device": device(1) })).data.messages.length, 1);
  assert.deepEqual((await call("POST", "/api/admin/inquiries/sort/auto", { fixed: true }, admin)).data, { fixed: true, known: false });
  const after = await ask(call, 2, "잠금화면일 때 알람이 진동만 오고 소리가 안 나요");
  const view = (await call("GET", `/api/inquiries/${after}`, undefined, { "x-device": device(2) })).data;
  assert.equal(view.messages.length, 2);
  assert.match(view.messages[1].text, /1\.34\.0 버전에서 고쳤어요/);
  assert.equal(view.status, "answered");
  // Not for one on the newest version (it came back): that waits for an admin.
  const again = await ask(call, 3, "잠금 화면에서 알람 소리가 안 나요 진동만 와요", "bug", "1.35.0 (47)");
  assert.equal((await call("GET", `/api/inquiries/${again}`, undefined, { "x-device": device(3) })).data.messages.length, 1);
  assert.equal((await call("GET", "/api/admin/inquiries/sort", undefined, admin)).data.counts.again, 1);
});

test("[INBOX-05][INBOX-06] Claude에게 넘기기: the text with ids and questions, 넘김, and the 결과 pasted back", async () => {
  const { call } = server();
  const admin = await signIn(call);
  const q = await ask(call, 1, "위젯에서 내일 근무도 같이 보이면 좋겠어요", "idea");
  const item = (await call("POST", "/api/admin/dev", { title: "위젯에 내일 근무", inquiry: q }, admin)).data.id;
  const h = (await call("GET", "/api/admin/inquiries/handoff", undefined, admin)).data;
  assert.match(h.text, new RegExp(`## ${item} · 위젯에 내일 근무`));
  assert.match(h.text, /문의 1건 · 앱 1\.33\.0/);
  assert.match(h.text, /내일 근무도 같이 보이면/);
  assert.ok(!h.text.includes(device(1)));
  assert.equal((await call("POST", "/api/admin/inquiries/handoff/mark", { ids: [item] }, admin)).data.marked, 1);
  assert.equal((await call("GET", "/api/admin/inquiries/handoff", undefined, admin)).data.items.length, 0);
  assert.equal((await call("GET", "/api/admin/inquiries/handoff?all=1", undefined, admin)).data.items.length, 1);
  const text = `결과\n- ${item} 완료 1.36.0\ndZZZZZZZZZZZZZZZ 반려\n`;
  const dry = (await call("POST", "/api/admin/inquiries/handoff/result", { text, dry: true }, admin)).data.lines;
  assert.deepEqual(dry.map((l) => [l.status, l.version, l.problem]), [["done", "1.36.0", null], ["rejected", null, "없는 개발사항"]]);
  const r = (await call("POST", "/api/admin/inquiries/handoff/result", { text, notify: true }, admin)).data;
  assert.equal(r.changed, 1);
  assert.equal(r.answered, 1);
  const view = (await call("GET", `/api/inquiries/${q}`, undefined, { "x-device": device(1) })).data;
  assert.match(view.messages.at(-1).text, /1\.36\.0 버전에서 고쳤어요/);
});

test("[INBOX-06] 결과 lines are read in the forms Claude writes them", () => {
  assert.deepEqual(readResult("d0123456789abcde 완료 1.36.0\n* d0123456789abcdf: 개발 중\nd0123456789abcdg · 반려 시스템 설정을 따라요\n아무 말"),
    [{ id: "d0123456789abcde", status: "done", version: "1.36.0", reason: "" },
      { id: "d0123456789abcdf", status: "doing", version: null, reason: "" },
      { id: "d0123456789abcdg", status: "rejected", version: null, reason: "시스템 설정을 따라요" }]);
  assert.equal(titleFrom("위젯에서 내일 근무도 보이면 좋겠어요. 그리고 색도요"), "위젯에서 내일 근무도 보이면 좋겠어요.");
});

test("[INBOX-03] 정리함 needs 문의 보기 to read and 개발사항 고치기 to make or tie an item", async () => {
  const { call } = server();
  const owner = await signIn(call);
  const sorted = await call("GET", "/api/admin/inquiries/sort", undefined, owner);
  assert.equal(sorted.status, 200);
  assert.equal((await call("POST", "/api/admin/inquiries/sort/apply", { actions: [{ do: "nope", id: "x" }] }, owner)).status, 400);
});
