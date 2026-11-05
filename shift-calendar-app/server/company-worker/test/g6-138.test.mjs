// 1.38 갈래6 (공유·위젯·신뢰): [EASE-31] 밤낮 Q&A — the developer's saved answers picked for the village, and the market search
// that reads each word (PC 확인 '교대 가계부'가 안 나와요). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../src/index.js";
import { searchWords, STYLE_WORDS } from "../src/recipes.js";
import { publicAnswer } from "../src/inquiries.js";
import { server, device, from, signIn } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");

test("[EASE-31] only answers given a public question are in /api/qa, most used first, with the placeholders said plainly", async () => {
  const { call } = server();
  const owner = await signIn(call);
  assert.deepEqual((await call("GET", "/api/qa")).data.items, [], "none picked: nothing");
  await call("POST", "/api/admin/templates", { label: "인사", text: "안녕하세요" }, owner);
  const t = await call("POST", "/api/admin/templates", { label: "위젯", text: "{앱버전}에서 위젯을 길게 누르면 바뀌어요", topic: "alarm",
    publicQ: "위젯이 안 바뀌어요" }, owner);
  assert.equal(t.status, 201, t.text);
  const res = await call("GET", "/api/qa");
  assert.equal(res.status, 200);
  assert.match(res.headers.get("cache-control"), /public, max-age=600/);
  assert.equal(res.data.items.length, 1);
  assert.equal(res.data.items[0].q, "위젯이 안 바뀌어요");
  assert.equal(res.data.items[0].a, "최신 버전에서 위젯을 길게 누르면 바뀌어요");
  // Taking the question away takes it off the village.
  const id = res.data.items[0].id;
  await call("POST", `/api/admin/templates/${id}`, { label: "위젯", text: "위젯을 길게 눌러요", topic: "alarm", publicQ: "" }, owner);
  assert.deepEqual((await call("GET", "/api/qa")).data.items, []);
  // A question too long is refused, not cut.
  const long = await call("POST", "/api/admin/templates", { label: "긴 것", text: "답", publicQ: "가".repeat(81) }, owner);
  assert.equal(long.status, 400);
  assert.equal(publicAnswer("{주제} 문의는 {오늘} 확인해요", ""), "이 내용 문의는 오늘 확인해요");
});

test("1.38 market search: each word must match, and a kind of work's name finds the works made for it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  assert.deepEqual(searchWords("  교대   가계부 "), ["교대", "가계부"]);
  assert.deepEqual(searchWords("a b c d e f"), ["a", "b", "c", "d"]);
  assert.equal(STYLE_WORDS["가게"], "BUSINESS");
  const { call } = server({ plaza: true });
  const recipe = (title, styles) => ({ format: "bamnat-recipe", v: 1, title, summary: "돈을 날마다 적어요", styles,
    pieces: [{ kind: "calTheme", today: 4289736794, sat: 4282664004, sun: 4293212469 }] });
  const a = await call("POST", "/api/recipes", { device: device(1), recipe: recipe("노랑 동전 가계부", ["SHIFT", "DAY"]), scope: "public" }, from(1));
  assert.equal(a.status, 201, a.text);
  const b = await call("POST", "/api/recipes", { device: device(2), recipe: recipe("우리 가게 장부", ["BUSINESS"]), scope: "public" }, from(2));
  assert.equal(b.status, 201, b.text);
  const find = async (q) => (await call("GET", `/api/recipes?sort=new&q=${encodeURIComponent(q)}`)).data.items.map((i) => i.title);
  assert.deepEqual(await find("교대 가계부"), ["노랑 동전 가계부"]);
  assert.deepEqual(await find("가계부 교대"), ["노랑 동전 가계부"], "in any order");
  assert.deepEqual(await find("자영업 가계부"), [], "every word must match");
  assert.deepEqual(await find("가게 장부"), ["우리 가게 장부"]);
  assert.deepEqual(await find("노랑 동전"), ["노랑 동전 가계부"], "a title's own words still find it");
});

test("[DESK-09] a key's answer waits 10 minutes: not in the app, a person can change or cancel it, then it goes", async (t) => {
  const start = Date.parse("2026-11-10T01:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: start });
  const { call, env, DB } = server();
  const owner = await signIn(call);
  const q = await call("POST", "/api/inquiries", { v: 1, device: device(1), kind: "bug", text: "알람이 늦게 울려요", info: { app: "1.37.0 (51)" } }, from(1));
  assert.equal(q.status, 201, q.text);
  const id = q.data.id ?? q.data.inquiry?.id;
  const made = await call("POST", "/api/admin/keys", { label: "시험" }, owner);
  assert.equal(made.status, 200, made.text);
  const key = { authorization: `Bearer ${made.data.key}`, "x-admin": "1" };
  const r = await call("POST", `/api/admin/inquiries/${id}/reply`, { text: "확인해 볼게요" }, key);
  assert.equal(r.status, 200, r.text);
  const mine = async () => (await call("GET", "/api/inquiries", undefined, { "x-device": device(1), ...from(1) })).data.inquiries[0].messages.filter((m) => m.from !== "user");
  assert.deepEqual(await mine(), [], "held: the asker sees nothing yet");
  const view = (await call("GET", `/api/admin/inquiries/${id}`, undefined, owner)).data;
  assert.equal(view.held.length, 1);
  // A person changes the words; the key may not.
  assert.equal((await call("POST", `/api/admin/inquiries/${id}/held/${view.held[0].id}`, { text: "바로 확인할게요" }, key)).status, 403);
  assert.equal((await call("POST", `/api/admin/inquiries/${id}/held/${view.held[0].id}`, { text: "바로 확인할게요" }, owner)).status, 200);
  const { releaseHeld } = await import("../src/inquiries.js");
  assert.equal(await releaseHeld(env, Date.now()), 0, "not before its time");
  t.mock.timers.tick(10 * 60 * 1000);
  assert.equal(await releaseHeld(env, Date.now()), 1);
  assert.deepEqual((await mine()).map((m) => m.text), ["바로 확인할게요"]);
  // A second one cancelled never goes.
  await call("POST", `/api/admin/inquiries/${id}/reply`, { text: "하나 더" }, key);
  const h2 = (await call("GET", `/api/admin/inquiries/${id}`, undefined, owner)).data.held[0];
  assert.equal((await call("POST", `/api/admin/inquiries/${id}/held/${h2.id}`, { cancel: true }, owner)).status, 200);
  t.mock.timers.tick(10 * 60 * 1000);
  assert.equal(await releaseHeld(env, Date.now()), 0);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM held_replies").get().n, 0);
});
