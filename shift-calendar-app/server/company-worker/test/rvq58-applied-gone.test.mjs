// 2.0.0 갈래 Q1 [RVQ-58] 내린 작품의 '넣었어요·7일 유지'를 옛 앱이 끝없이 다시 보내지 않게. 모두 지어낸 값이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const recipe = (title) => ({
  format: "bamnat-recipe", v: 1, title, summary: "야간이 잘 보여요", styles: ["SHIFT"],
  pieces: [{ kind: "shiftLook", items: [{ name: "주간", role: "day", label: "주", color: 4294940928, emoji: "☀️" },
    { name: "야간", role: "night", label: "야", color: 4282664004, emoji: "🌙" }] }],
});

test("[RVQ-58] 운영자가 내린 작품·없는 작품의 '넣었어요'는 404가 아니라 받았지만 세지 않음이라 옛 앱이 다시 보내지 않아요; 보이는 작품은 그대로 세요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, DB } = server({ plaza: true });
  const admin = await signIn(call);
  const shown = await call("POST", "/api/admin/recipes", { recipe: recipe("출시 테마 하나") }, admin);
  const gone = await call("POST", "/api/admin/recipes", { recipe: recipe("내릴 옛 테마") }, admin);
  assert.equal(shown.status, 201, JSON.stringify(shown.data));
  assert.equal(gone.status, 201, JSON.stringify(gone.data));
  DB.db.prepare("UPDATE recipes SET state = 'hidden', hidden_reason = 'admin' WHERE code = ?").run(gone.data.code);
  const n = (code) => ({ ...DB.db.prepare("SELECT applied, kept7 FROM recipes WHERE code = ?").get(code) });
  // 보이는 작품: 세요.
  const ok = await call("POST", `/api/recipes/${shown.data.code}/applied`, { kind: "applied" }, from(2));
  assert.equal(ok.status, 200);
  assert.equal(ok.data.counted, undefined);
  assert.equal(n(shown.data.code).applied, 1);
  // 내린 작품: 200 counted:false, 숫자는 그대로.
  for (const kind of ["applied", "kept"]) {
    const r = await call("POST", `/api/recipes/${gone.data.code}/applied`, { kind }, from(3));
    assert.equal(r.status, 200, kind);
    assert.deepEqual(r.data, { ok: true, counted: false });
  }
  assert.deepEqual(n(gone.data.code), { applied: 0, kept7: 0 });
  // 없는 코드도 같은 답이라 코드가 있는지 알려 주지 않아요.
  const none = await call("POST", "/api/recipes/ZZZZZZZZ/applied", { kind: "applied" }, from(4));
  assert.equal(none.status, 200);
  assert.deepEqual(none.data, { ok: true, counted: false });
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS c FROM recipe_counts WHERE code != ?").get(shown.data.code).c, 0);
  // 잘못된 kind는 그대로 400이에요.
  assert.equal((await call("POST", `/api/recipes/${gone.data.code}/applied`, { kind: "x" }, from(4))).status, 400);
});
