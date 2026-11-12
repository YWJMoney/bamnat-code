// 2.0.0 갈래 Q2 4/4c (검토 23·24, 도약 42): 운영 장부·장터는 50줄씩 이어 받고, 통합 찾기는 문의 글을 끝까지 훑지 않아요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, TEST_NOW } from "./helpers.mjs";
import { SEARCH_DAYS } from "../src/admin-insight.js";

const ask = (call, n, text, kind = "question") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "2.0.0 (54)" } }, from(n)).then((r) => r.data.id);

test("[RVA-48] 운영 장부·장터 50줄씩: limit=50이면 50줄과 더 있음, 다음 쪽은 이어서, 열쇠·limit 없는 읽기는 전처럼", async (t) => {
  const { call } = server({ t, plaza: true });
  const owner = await signIn(call);
  for (let n = 1; n <= 62; n++) await ask(call, n, `달력 색은 어디서 바꿔요 ${n}`);
  const first = (await call("GET", "/api/admin/ledger?view=open&limit=50", undefined, owner)).data;
  assert.equal(first.items.length, 50);
  assert.equal(first.more, true);
  assert.ok(first.total >= 62);
  assert.equal(typeof first.lateTotal, "number");
  const second = (await call("GET", "/api/admin/ledger?view=open&limit=50&page=1", undefined, owner)).data;
  assert.equal(second.items.length, first.total - 50);
  assert.equal(second.more, false);
  const keys = new Set([...first.items, ...second.items].map((r) => r.key));
  assert.equal(keys.size, first.total, "no line twice, none missing");
  // 크기: 50줄 쪽이 모두 받던 것보다 작아요.
  const all = await call("GET", "/api/admin/ledger?view=open", undefined, owner);
  assert.equal(all.data.items.length, first.total, "without limit: as before (keys, Claude)");
  assert.ok(JSON.stringify(first).length < JSON.stringify(all.data).length * 0.9);

  // 장터: limit이 있으면 50개씩, 찾기·구간은 서버가, 구간 수는 거르기 전 전체.
  const look = { kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" }] };
  for (let i = 1; i <= 55; i++) {
    const recipe = { format: "bamnat-recipe", v: 1, title: `세트 ${String(i).padStart(2, "0")}`, summary: "야간이 잘 보여요", styles: ["SHIFT"],
      cover: { a: 4282339765, b: 4288423856, dir: 1, emoji: "🌙" }, pieces: [look] };
    assert.equal((await call("POST", "/api/recipes", { device: device(200 + i), recipe, scope: "public" }, from(200 + i))).status, 201);
  }
  const tab = (await call("GET", "/api/admin/recipes?tab=visible", undefined, owner)).data.items.length ? "visible" : "queue";
  const r1 = (await call("GET", `/api/admin/recipes?tab=${tab}&limit=50`, undefined, owner)).data;
  assert.deepEqual([r1.items.length, r1.total, r1.next], [50, 55, 50]);
  assert.equal(r1.sections.all, 55);
  const r2 = (await call("GET", `/api/admin/recipes?tab=${tab}&limit=50&offset=50`, undefined, owner)).data;
  assert.deepEqual([r2.items.length, r2.next], [5, null]);
  const found = (await call("GET", `/api/admin/recipes?tab=${tab}&limit=50&q=${encodeURIComponent("세트 07")}`, undefined, owner)).data;
  assert.deepEqual(found.items.map((r) => r.title), ["세트 07"]);
  assert.equal(found.sections.all, 55, "the chips count all, not the found ones");
  const old = (await call("GET", `/api/admin/recipes?tab=${tab}`, undefined, owner)).data;
  assert.equal(old.items.length, 55, "without limit: as before");
  assert.equal(old.total, undefined);
});

test("[RVA-47] 통합 찾기(Ctrl+K): 문의 글은 최근 1년에서 찾고, 번호로 찾기는 그대로", async (t) => {
  const { call, DB } = server({ t });
  const owner = await signIn(call);
  const fresh = await ask(call, 1, "위젯 글자가 잘려요");
  const stale = await ask(call, 2, "위젯 색이 이상해요");
  // 둘째 문의의 글을 1년보다 오래전으로 옮겨요.
  await DB.prepare("UPDATE inquiry_messages SET at = ? WHERE inquiry_id = ?").bind(TEST_NOW - (SEARCH_DAYS + 30) * 864e5, stale).run();
  const res = (await call("GET", `/api/admin/search?q=${encodeURIComponent("위젯")}`, undefined, owner)).data;
  const inq = res.groups.find((g) => g.kind === "inquiries");
  assert.deepEqual(inq.items.map((x) => x.to), [`#/inquiries/${fresh}`], "only the recent one");
  assert.equal(inq.more, `#/inquiries?status=all&q=${encodeURIComponent("위젯")}`, "the whole list is one tap away");
  const byNo = (await call("GET", `/api/admin/search?q=${stale}`, undefined, owner)).data;
  assert.equal(byNo.groups[0].items[0].to, `#/inquiries/${stale}`, "a number still finds an old one");
});
