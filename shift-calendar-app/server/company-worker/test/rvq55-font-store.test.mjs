// 2.0.0 [RVQ-55] 검토 SVC-25: 글꼴 파일을 D1에 base64 조각으로 쌓지 않고, R2 저장소(ASSETS)가 묶여 있으면 그곳에 원래 크기로 둬요.
// 옛 조각은 매시 정리 때 옮기고, 지운 글꼴의 파일도 그때 지워요. 묶지 않았으면 지금처럼 D1이에요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";
import worker, { prune } from "../src/index.js";
import { FONT_LIMITS, FONT_STORE, fontKey } from "../src/recipe-fonts.js";
import { fakeFont } from "./font-fixture.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");

/** R2 버킷 흉내: put·get·list(커서)·delete만. */
function fakeR2() {
  const objects = new Map();
  return {
    objects,
    async put(key, value) { objects.set(key, Buffer.from(value)); return { key }; },
    async get(key) {
      const v = objects.get(key);
      return v ? { body: new Blob([v]).stream(), size: v.length } : null;
    },
    async list({ prefix = "", limit = 1000, cursor } = {}) {
      const keys = [...objects.keys()].filter((k) => k.startsWith(prefix)).sort();
      const start = cursor ? Number(cursor) : 0;
      const page = keys.slice(start, start + limit);
      const truncated = start + limit < keys.length;
      return { objects: page.map((key) => ({ key })), truncated, ...(truncated ? { cursor: String(start + limit) } : {}) };
    },
    async delete(keys) { for (const k of [].concat(keys)) objects.delete(k); },
  };
}

const send = (call, n, font, name = "나눔손글씨") =>
  call("POST", "/api/recipes/fonts", { device: device(n), name, license: "ofl", ttf: font.toString("base64") }, from(n));
const file = async (env, id) => {
  const r = await worker.fetch(new Request(`https://x.test/api/recipes/fonts/${id}/file`), env);
  return { status: r.status, bytes: r.status === 200 ? Buffer.from(await r.arrayBuffer()) : null };
};

test("[RVQ-55] SVC-25 R2가 묶이면 새 글꼴은 R2에 원래 크기로, D1에는 조각이 없고 내려받기는 그대로예요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const ASSETS = fakeR2();
  const { call, env, DB } = server({ plaza: true, ASSETS });
  const font = fakeFont({ pad: FONT_LIMITS.part * 2, fill: 5 });
  const id = (await send(call, 1, font)).data.id;
  assert.equal((await DB.prepare("SELECT parts FROM recipe_fonts WHERE id = ?").bind(id).first()).parts, 0);
  assert.equal((await DB.prepare("SELECT COUNT(*) AS n FROM recipe_font_parts WHERE id = ?").bind(id).first()).n, 0);
  assert.deepEqual(ASSETS.objects.get(fontKey(id)), font, "base64가 아닌 원래 바이트");
  await call("POST", `/api/admin/recipes/fonts/${id}/ok`, {}, await signIn(call));
  assert.deepEqual((await file(env, id)).bytes, font);
});

test("[RVQ-55] SVC-25 R2가 없으면 지금처럼 D1 조각이고, 묶은 뒤 매시 정리가 옛 조각을 옮기고 지운 글꼴의 파일을 지워요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const s = server({ plaza: true });
  const fonts = [];
  for (let i = 0; i < FONT_STORE.movePerTick + 1; i++) {
    const font = fakeFont({ pad: 200 + i, fill: 10 + i });
    // 한 네트워크는 하루 3개라 휴대폰·줄을 나눠요.
    fonts.push({ font, id: (await send(s.call, 10 + i, font, `글꼴 ${i}`)).data.id });
  }
  const admin = await signIn(s.call);
  for (const f of fonts) await s.call("POST", `/api/admin/recipes/fonts/${f.id}/ok`, {}, admin);
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM recipe_fonts WHERE parts > 0").first()).n, fonts.length);

  // 이제 R2를 묶어요(같은 DB). 한 번에 movePerTick개씩 옮기고, 옮기는 동안에도 모두 내려받아져요.
  const ASSETS = fakeR2();
  const env = { ...s.env, ASSETS };
  await prune(env, Date.now());
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM recipe_fonts WHERE parts = 0").first()).n, FONT_STORE.movePerTick);
  for (const f of fonts) assert.deepEqual((await file(env, f.id)).bytes, f.font);
  await prune(env, Date.now());
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM recipe_fonts WHERE parts > 0").first()).n, 0);
  assert.equal((await s.DB.prepare("SELECT COUNT(*) AS n FROM recipe_font_parts").first()).n, 0);
  assert.equal(ASSETS.objects.size, fonts.length);

  // 줄이 없어진 글꼴(이 휴대폰 정보 지우기)의 파일은 다음 정리 때 지워요. 남은 것은 그대로.
  await s.DB.prepare("DELETE FROM recipe_fonts WHERE id = ?").bind(fonts[0].id).run();
  await prune(env, Date.now());
  assert.equal(ASSETS.objects.has(fontKey(fonts[0].id)), false);
  assert.equal(ASSETS.objects.size, fonts.length - 1);
  assert.deepEqual((await file(env, fonts[1].id)).bytes, fonts[1].font);
  assert.equal((await file(env, fonts[0].id)).status, 404);
});

test("[RVQ-55] SVC-25 DB가 한도의 80%를 넘으면 사진·글꼴(D1에 둘 때) 올리기를 쉬고, 오늘 › 주의 필요에 떠요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { opsAlertsFrom } = await import("../src/admin-ops.js");
  const { DB_LIMIT } = await import("../src/ops-watch.js");
  const s = server({ plaza: true });
  const full = JSON.stringify({ bytes: Math.ceil(DB_LIMIT * 0.81), at: AFTER });
  await s.call("GET", "/api/terms");
  await s.DB.prepare("INSERT INTO settings (key, value) VALUES ('db_size', ?)").bind(full).run();
  const font = fakeFont({ pad: 300, fill: 41 });
  const f = await send(s.call, 1, font);
  assert.equal(f.status, 503);
  assert.deepEqual(f.data, { error: "paused", field: "space" });
  const img = await s.call("POST", "/api/recipes/images", { device: device(2), webp: "UklGRg==" }, from(2));
  assert.equal(img.status, 503);
  assert.equal(img.data.field, "space");
  // 글꼴 저장소(R2)가 있으면 글꼴은 DB를 쓰지 않으니 그대로 받아요.
  const withR2 = await worker.fetch(new Request("https://x.test/api/recipes/fonts", { method: "POST", headers: { "content-type": "application/json", ...from(3) },
    body: JSON.stringify({ device: device(3), name: "R2 글꼴", license: "ofl", ttf: font.toString("base64") }) }), { ...s.env, ASSETS: fakeR2() });
  assert.equal(withR2.status, 201);
  const alerts = opsAlertsFrom(s.env, { ops_dbsize: [{ value: full }] }, AFTER);
  assert.equal(alerts.find((a) => a.key.startsWith("dbsize:"))?.title, "DB가 한도의 81%예요");
  assert.equal(opsAlertsFrom(s.env, { ops_dbsize: [{ value: JSON.stringify({ bytes: DB_LIMIT * 0.5 }) }] }, AFTER).some((a) => a.key.startsWith("dbsize:")), false);
});

test("[RVQ-55] SVC-25 확인을 기다리는 글꼴은 모두 합쳐 200MB까지예요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const s = server({ plaza: true });
  await s.call("GET", "/api/terms");
  await s.DB.prepare("INSERT INTO recipe_fonts (id, owner, name, license, bytes, parts, state, info, glyphs, fallback, at) VALUES ('u00000000000000aa', 'x', '큰 것', 'ofl', ?, 0, 'pending', '{}', '', NULL, ?)")
    .bind(FONT_LIMITS.pendingBytes - 100, AFTER).run();
  const r = await send(s.call, 1, fakeFont({ pad: 300, fill: 42 }));
  assert.equal(r.status, 503);
  assert.deepEqual(r.data, { error: "paused", field: "pending" });
  await s.DB.prepare("UPDATE recipe_fonts SET state = 'ok'").run();
  assert.equal((await send(s.call, 1, fakeFont({ pad: 300, fill: 42 }))).status, 201);
});
