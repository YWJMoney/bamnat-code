// 1.39 갈래 G7 (관리자 앱·운영): [ADM3-37] 오늘 › 할 일에서 같은 문의 묶기, [DESK-05] 출시 노트 후보·낱말 사전. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { bundleInquiries } from "../src/inbox.js";

const ask = (call, n, text, kind = "bug") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "1.38.0 (52)", model: "SM-F956N" } }).then((r) => r.data.id);

test("[ADM3-37] bundleInquiries: alike questions under the first (nearest due); others stay one by one; screen path is not a likeness", () => {
  const items = [
    { type: "inquiry", id: "a", kind: "bug", title: "잠금 화면에서 알람 소리가 안 나요" },
    { type: "dev", id: "d1", title: "잠금 화면에서 알람 소리가 안 나요" },
    { type: "inquiry", id: "b", kind: "bug", title: "잠금화면 알람 소리가 안나요 진동만 와요" },
    { type: "inquiry", id: "c", kind: "question", title: "[설정 > 알람] 급여일을 바꾸고 싶어요" },
    { type: "inquiry", id: "d", kind: "bug", title: "[설정 > 알람] 위젯이 안 바뀌어요" },
  ];
  const texts = new Map(items.filter((x) => x.type === "inquiry").map((x) => [x.id, x.title]));
  bundleInquiries(items, texts);
  assert.deepEqual(items.find((x) => x.id === "a").others, ["b"]);
  assert.equal(items.find((x) => x.id === "b").bundle, "a");
  assert.equal(items.find((x) => x.id === "d1").bundle, undefined, "a 개발사항 is not an inquiry");
  assert.equal(items.find((x) => x.id === "d").bundle, undefined, "the same screen alone does not tie two questions");
  assert.equal(items.find((x) => x.id === "c").others, undefined);
  assert.deepEqual(items.map((x) => x.id), ["a", "d1", "b", "c", "d"], "the order stays");
});

test("[ADM3-37] /today: the same question three times is one bundle; queue and alerts still see each", async () => {
  const { call } = server();
  const admin = await signIn(call);
  await ask(call, 1, "잠금 화면에서 알람 소리가 안 나요");
  await ask(call, 2, "잠금 화면에서 알람 소리가 안 나요 진동만 와요");
  await ask(call, 3, "잠금화면 알람 소리가 안나요");
  await ask(call, 4, "급여 명세서를 엑셀로 받고 싶어요", "idea");
  const o = (await call("GET", "/api/admin/today", undefined, admin)).data;
  const asks = o.todo.items.filter((x) => x.type === "inquiry");
  assert.equal(asks.length, 4, "every question is still a line (큐 모드·할 일함)");
  const lead = asks.find((x) => x.others?.length);
  assert.equal(lead.others.length, 2);
  assert.equal(asks.filter((x) => x.bundle === lead.id).length, 2);
  assert.equal(asks.find((x) => /엑셀/.test(x.title)).bundle, undefined);
});

test("[DESK-05] 같은 뜻 낱말 사전: 월급·임금은 급여, 울려·소리는 알람으로 만나요; 알림은 따로예요", async () => {
  const { grams, likeness } = await import("../src/inbox.js");
  assert.ok(grams("월급 계산이 틀려요").words.has("급여"));
  assert.ok(grams("임금이 이상해요").words.has("급여"));
  assert.ok(grams("아침에 안 울려요").words.has("알람"));
  assert.ok(!grams("문의 답 알림이 안 와요").words.has("알람"), "알림 is another feature");
  assert.ok(likeness("월급날 위젯에 안 보여요", "급여일이 위젯에 안 보여요") > likeness("월급날 위젯에 안 보여요", "급여일이 위젯에 안 보여요".replace("급여일", "지급일")));
  assert.ok(likeness("나이트 근무 스케줄이 하루 밀려요", "야간 근무표가 하루 밀려요") >= 0.42);
});

test("[DESK-05] 출시 노트 후보: a question like a shipped 업데이트 소식 meets that 개발사항, with the 소식 as its reason", async () => {
  const { grams } = await import("../src/inbox.js");
  const { overlaps } = await import("../src/ledger.js");
  const dev = { id: "dN", title: "SettingsWidgetPayday 표시 버그", body: "", status: "done", reason: "", released_in: "1.38.0",
    news: "위젯에서 급여일까지 남은 날을 볼 수 있어요" };
  const k = { shipped: "1.38.0", devs: [{ ...dev, g: grams(`${dev.title} ${dev.title} ${dev.body}`), ng: grams(dev.news) }], answered: [], templates: [] };
  const [o] = overlaps({ id: "q1", kind: "idea", text: "위젯에서 월급날까지 남은 날을 보고 싶어요", info: {} }, k);
  assert.equal(o?.id, "dN");
  assert.equal(o.note, dev.news);
  assert.match(o.why, /1\.38\.0 업데이트 소식/);
  assert.match(o.why, /1\.38\.0에서 고침/);
});
