// 1.38 [STM-01..04] 밤낮 재료: 운영자만 넣고, 출시 대기 → 공개, 그림은 작품 그림처럼 누구나 받아요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { server, signIn } from "./helpers.mjs";
import worker from "../src/index.js";
import { webpSize } from "../src/studio-materials.js";

const heart = readFileSync(new URL("./fixtures/material-heart.webp", import.meta.url));
const thumb = readFileSync(new URL("./fixtures/material-g01-thumb.webp", import.meta.url));

test("[STM-01] webpSize reads a lossy and an alpha WebP", () => {
  assert.deepEqual(webpSize(heart), { w: 256, h: 256 });
  assert.deepEqual(webpSize(thumb), { w: 216, h: 456 });
  assert.equal(webpSize(Buffer.from("RIFF0000WEBPVP8 not really a picture")), null);
});

test("[STM-02] the operator puts materials in waiting, opens them with the app version, and phones fetch the picture", async () => {
  const { call, env } = server({ plaza: true });
  const admin = await signIn(call);
  const items = [
    { kind: "icon", pack: "마음·축하", name: "하트", ord: 1, webp: heart.toString("base64") },
    { kind: "bg", pack: "그라데이션", name: "복숭아 안개", ord: 1, webp: thumb.toString("base64"), thumb: thumb.toString("base64"), light: 0.91, free: false, minApp: 52 },
  ];
  assert.equal((await call("POST", "/api/admin/recipes/materials", { items })).status, 403, "only the developer app");
  const put = await call("POST", "/api/admin/recipes/materials", { items }, admin);
  assert.equal(put.status, 201);
  const [icon, bg] = put.data.ids;
  assert.match(icon, /^[0-9a-f]{16}$/);
  assert.equal((await call("POST", "/api/admin/recipes/materials", { items: [{ ...items[0], kind: "sticker" }] }, admin)).data.error, "item");
  assert.equal((await call("POST", "/api/admin/recipes/materials", { items: [{ ...items[0], webp: Buffer.from("x").toString("base64") }] }, admin)).data.error, "image");

  // 출시 대기: 휴대폰 목록에는 아직 없어요.
  assert.deepEqual((await call("GET", "/api/recipes/materials?app=52")).data.items, []);
  const all = (await call("GET", "/api/admin/recipes/materials", undefined, admin)).data;
  assert.equal(all.counts.waiting, 2);

  assert.equal((await call("POST", "/api/admin/recipes/materials/state", { state: "ok", all: true }, admin)).status, 200);
  const list = (await call("GET", "/api/recipes/materials?app=52")).data.items;
  assert.deepEqual(list.map((x) => x.id).sort(), [icon, bg].sort());
  const b = list.find((x) => x.id === bg);
  assert.equal(b.free, false);
  assert.equal(b.w, 216);
  assert.equal(b.thumb, bg, "the same bytes are one picture");
  // 1.37 앱(51)에는 1.38 재료가 안 보여요.
  assert.deepEqual((await call("GET", "/api/recipes/materials?app=51")).data.items.map((x) => x.id), [icon]);

  // 그림은 작품 그림 받기로 누구나(1.36 앱도).
  const res = await worker.fetch(new Request(`https://x.test/api/recipes/images/${icon}`), env);
  assert.equal(res.status, 200);
  assert.deepEqual(Buffer.from(await res.arrayBuffer()), heart);

  // 숨기기는 묶음째로.
  await call("POST", "/api/admin/recipes/materials/state", { state: "hidden", pack: "마음·축하" }, admin);
  assert.deepEqual((await call("GET", "/api/recipes/materials?app=52")).data.items.map((x) => x.id), [bg]);
  // 다시 넣어도 공개 상태는 그대로예요.
  await call("POST", "/api/admin/recipes/materials", { items: [items[1]] }, admin);
  assert.deepEqual((await call("GET", "/api/recipes/materials?app=52")).data.items.map((x) => x.id), [bg]);
});

test("[STM-10] 무료·광고 나누기: 묶음마다 모두 무료·모두 광고·앞쪽 3분의 1만 무료", async () => {
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const items = [heart, thumb].map((b, i) => ({ kind: "icon", pack: "날씨", name: `날씨 ${i}`, ord: i, webp: b.toString("base64") }));
  const ids = (await call("POST", "/api/admin/recipes/materials", { items }, admin)).data.ids;
  const n = new Set(ids).size;
  assert.equal(n, 2);
  await call("POST", "/api/admin/recipes/materials/state", { state: "ok", all: true }, admin);
  const third = await call("POST", "/api/admin/recipes/materials/free", { pack: "날씨", mode: "third" }, admin);
  assert.deepEqual([third.data.free, third.data.paid], [Math.ceil(n / 3), n - Math.ceil(n / 3)]);
  assert.equal((await call("GET", "/api/admin/recipes/materials", undefined, admin)).data.items.filter((x) => x.free).length, Math.ceil(n / 3));
  assert.equal((await call("POST", "/api/admin/recipes/materials/free", { pack: "날씨", mode: "none" }, admin)).data.free, 0);
  assert.equal((await call("POST", "/api/admin/recipes/materials/free", { pack: "날씨", mode: "all" }, admin)).data.paid, 0);
  assert.equal((await call("POST", "/api/admin/recipes/materials/free", { pack: "날씨", mode: "some" }, admin)).status, 400);
  assert.equal((await call("POST", "/api/admin/recipes/materials/free", { pack: "날씨", mode: "all" })).status, 403);
});
