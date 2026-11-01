// 1.36 [MAT-06] 내 글꼴 나누기: open fonts only, the same id as the phone's, kept in parts, three reports take it down. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";
import worker from "../src/index.js";
import { FONT_LIMITS, fontIdOf } from "../src/recipe-fonts.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const dev = (n) => ({ "x-device": device(n) });
const ttf = (n = 64, fill = 7) => Buffer.concat([Buffer.from([0, 1, 0, 0]), Buffer.alloc(n, fill)]);

test("[MAT-06] a shared font: open licence, a real font file, its id is its bytes'", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const font = ttf();
  const id = await fontIdOf(font);
  assert.match(id, /^u[0-9a-f]{16}$/);
  const post = (body, n = 1) => call("POST", "/api/recipes/fonts", { device: device(n), name: "나눔손글씨", license: "ofl", ttf: font.toString("base64"), ...body }, from(n));
  assert.equal((await post({ license: "mine" })).data.error, "license", "only fonts others may use");
  assert.equal((await post({ ttf: Buffer.from("not a font at all").toString("base64") })).data.error, "font");
  assert.equal((await post({ id: "u0000000000000000" })).data.error, "id", "the phone's id must be the bytes'");
  const r = await post({ id });
  assert.equal(r.status, 201);
  assert.equal(r.data.id, id);
  assert.equal((await post({}, 2)).data.again, true, "the same file twice is one font");
  const meta = (await call("GET", `/api/recipes/fonts/${id}`)).data;
  assert.equal(meta.name, "나눔손글씨");
  assert.equal(meta.license, "ofl");
  const file = await worker.fetch(new Request(`https://x.test/api/recipes/fonts/${id}/file`), env);
  assert.equal(file.status, 200);
  assert.equal(file.headers.get("content-type"), "font/ttf");
  assert.deepEqual(Buffer.from(await file.arrayBuffer()), font);
});

test("[MAT-06] a big font is kept in parts and comes back whole", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const font = ttf(FONT_LIMITS.part * 2, 3);
  const id = (await call("POST", "/api/recipes/fonts", { device: device(1), name: "큰 글꼴", license: "free", ttf: font.toString("base64") }, from(1))).data.id;
  assert.equal(id, await fontIdOf(font));
  const meta = (await call("GET", `/api/recipes/fonts/${id}`)).data;
  assert.equal(meta.bytes, font.length);
  const file = await worker.fetch(new Request(`https://x.test/api/recipes/fonts/${id}/file`), env);
  assert.deepEqual(Buffer.from(await file.arrayBuffer()), font, "the parts come back in order");
  const tooBig = ttf(FONT_LIMITS.bytes, 1);
  assert.equal((await call("POST", "/api/recipes/fonts", { device: device(1), name: "너무 큼", license: "ofl", ttf: tooBig.toString("base64") }, from(1))).status, 413);
});

test("[MAT-06] three reports from three phones take a font down until the operator looks; forgetting removes it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const id = (await call("POST", "/api/recipes/fonts", { device: device(1), name: "남의 글꼴", license: "ofl", ttf: ttf(80, 9).toString("base64") }, from(1))).data.id;
  const report = (n) => call("POST", `/api/recipes/fonts/${id}/report`, { device: device(n), why: "license" }, from(n));
  assert.equal((await report(1)).data.error, "own");
  await report(2); await report(2); await report(3);
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 200, "two phones are not enough");
  await report(4);
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 404, "taken down");
  assert.equal((await call("GET", `/api/recipes/fonts/${id}/file`)).status, 404);
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`, undefined, dev(1))).status, 200, "its owner still sees it");
  const admin = await signIn(call);
  const list = (await call("GET", "/api/admin/recipes/fonts", undefined, admin)).data;
  assert.equal(list.items.length, 1);
  assert.equal(list.items[0].whys.license, 3);
  await call("POST", `/api/admin/recipes/fonts/${id}/ok`, {}, admin);
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 200, "the operator put it back");
  await call("POST", `/api/admin/recipes/fonts/${id}/hide`, {}, admin);
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 404);
  // 이 휴대폰이 보낸 정보 모두 지우기 takes the font with it.
  const count = async (sql) => (await env.DB.prepare(sql).first()).n;
  assert.ok((await count("SELECT COUNT(*) AS n FROM recipe_font_parts")) > 0);
  await call("DELETE", "/api/mine", undefined, dev(1));
  assert.equal(await count("SELECT COUNT(*) AS n FROM recipe_fonts"), 0);
  assert.equal(await count("SELECT COUNT(*) AS n FROM recipe_font_parts"), 0);
  assert.equal(await count("SELECT COUNT(*) AS n FROM recipe_font_reports"), 0);
});
