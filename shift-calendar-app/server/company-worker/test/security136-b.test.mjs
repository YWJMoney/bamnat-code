// 1.36 보안 점검(조사/1.36-보안-점검.md) B 갈래 몫 [SEC-10]: 글꼴 구조 검사(H3), 이름 표·유료 글꼴(M7), 첫 공개 확인, 하루 한도(H1),
// 신고 가중치(M3).
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { FONT_LIMITS, fontInfo, fontLicenseSays, fontProblem, fontSold } from "../src/recipe-fonts.js";
import { fakeFont, OFL } from "./font-fixture.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const DAY = 86400000;
const ip = (s) => ({ "cf-connecting-ip": s });
const around = async (env, ...ns) => {
  const { deviceHash, deviceId } = await import("../src/index.js");
  for (const n of ns) await env.DB.prepare("INSERT OR REPLACE INTO recipe_trust (device, first_at) VALUES (?, ?)").bind(await deviceHash(deviceId(device(n))), AFTER - 4 * DAY).run();
};

test("[SEC-10] H3: a font is read for its tables before any phone gets it", () => {
  assert.equal(fontProblem(fakeFont()), null);
  assert.equal(fontProblem(Buffer.concat([Buffer.from([0, 1, 0, 0]), Buffer.alloc(80, 1)])), "font", "a head alone");
  for (const t of ["cmap", "head", "name", "maxp"]) assert.equal(fontProblem(fakeFont({ drop: [t] })), "font", `no ${t}`);
  assert.equal(fontProblem(fakeFont({ drop: ["glyf"] })), "font", "no outlines");
  const past = fakeFont();
  past.writeUInt32BE(past.length + 100, 12 + 8); // the first table past the end
  assert.equal(fontProblem(past), "font");
  const magic = fakeFont();
  const headAt = (() => { for (let i = 0; i < magic.readUInt16BE(4); i++) if (magic.toString("latin1", 12 + i * 16, 16 + i * 16) === "head") return magic.readUInt32BE(12 + i * 16 + 8); })();
  magic.writeUInt32BE(0, headAt + 12);
  assert.equal(fontProblem(magic), "font", "head without its magic number");
  // A collection of one good face reads; one that points past the end does not.
  const face = fakeFont();
  const ttc = Buffer.alloc(16);
  ttc.write("ttcf", 0, "latin1"); ttc.writeUInt32BE(0x00010000, 4); ttc.writeUInt32BE(1, 8); ttc.writeUInt32BE(16, 12);
  const shifted = Buffer.from(face);
  for (let i = 0; i < shifted.readUInt16BE(4); i++) shifted.writeUInt32BE(shifted.readUInt32BE(12 + i * 16 + 8) + 16, 12 + i * 16 + 8);
  assert.equal(fontProblem(Buffer.concat([ttc, shifted])), null);
  ttc.writeUInt32BE(9999, 12);
  assert.equal(fontProblem(Buffer.concat([ttc, shifted])), "font");
});

test("[SEC-10] M7: the font's own words, and a foundry that sells fonts is turned away unless they say OFL·Apache", () => {
  const info = fontInfo(fakeFont({ family: "바른 손글씨", maker: "Some Maker", vendor: "ABCD" }));
  assert.equal(info.family, "바른 손글씨");
  assert.equal(info.maker, "Some Maker");
  assert.equal(info.license, OFL);
  assert.equal(info.vendor, "ABCD");
  assert.equal(fontLicenseSays(info), "ofl");
  assert.equal(fontLicenseSays({ license: "Licensed under the Apache License, Version 2.0" }), "apache");
  assert.equal(fontLicenseSays({ license: "All rights reserved." }), null);
  assert.equal(fontSold(fontInfo(fakeFont({ maker: "Sandoll Communication", license: "All rights reserved.", url: "" }))), true);
  assert.equal(fontSold(fontInfo(fakeFont({ copyright: "© 윤디자인그룹", license: "", url: "" }))), true);
  // Noto: made by a foundry that sells fonts, under OFL.
  assert.equal(fontSold(fontInfo(fakeFont({ maker: "Monotype Imaging Inc.", license: OFL }))), false);
});

test("[SEC-10] a sold font is refused; the first waits for the operator, the next with OFL words goes straight up", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const send = (font, n = 1, at = "203.0.113.1") => call("POST", "/api/recipes/fonts", { device: device(n), name: "글꼴", license: "ofl", ttf: font.toString("base64") }, ip(at));
  const sold = await send(fakeFont({ maker: "Sandoll Communication", license: "All rights reserved.", url: "" }));
  assert.equal(sold.status, 400);
  assert.equal(sold.data.error, "sold");
  const first = await send(fakeFont({ fill: 1 }));
  assert.equal(first.data.state, "pending");
  const admin = await signIn(call);
  const list = (await call("GET", "/api/admin/recipes/fonts", undefined, admin)).data;
  assert.equal(list.state, "pending", "the operator sees what waits first");
  assert.equal(list.items[0].info.family, "나눔손글씨 붓");
  assert.equal(list.items[0].says, "ofl");
  await call("POST", `/api/admin/recipes/fonts/${first.data.id}/ok`, {}, admin);
  assert.equal((await send(fakeFont({ fill: 2 }))).data.state, "ok", "a sender with a passed font, and the font says OFL");
  assert.equal((await send(fakeFont({ fill: 3, license: "Free for personal use", url: "" }))).data.state, "pending", "its words do not say OFL·Apache");
  assert.equal((await send(fakeFont({ fill: 4 }), 2, "203.0.113.2")).data.state, "pending", "another sender's first");
});

test("[SEC-10] H1: one network sends at most three fonts a day however many installs it makes", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const send = (n, fill, at) => call("POST", "/api/recipes/fonts", { device: device(n), name: "글꼴", license: "ofl", ttf: fakeFont({ fill }).toString("base64") }, ip(at));
  for (let n = 1; n <= FONT_LIMITS.perNetworkDay; n++) assert.equal((await send(n, n, "198.51.100.40")).status, 201);
  const over = await send(9, 9, "198.51.100.40");
  assert.equal(over.status, 429);
  assert.equal(over.data.why, "network");
  assert.equal((await send(10, 10, "203.0.113.10")).status, 201, "another network still can");
});

test("[SEC-10] M3: new installs and a second report from one network do not take a font down", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call, env } = server({ plaza: true });
  const admin = await signIn(call);
  const id = (await call("POST", "/api/recipes/fonts", { device: device(1), name: "글꼴", license: "ofl", ttf: fakeFont({ fill: 5 }).toString("base64") }, ip("203.0.113.1"))).data.id;
  await call("POST", `/api/admin/recipes/fonts/${id}/ok`, {}, admin);
  const report = (n, at) => call("POST", `/api/recipes/fonts/${id}/report`, { device: device(n), why: "license" }, ip(at));
  for (let n = 10; n < 16; n++) await report(n, `198.51.100.${n}`);
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 200, "six 새싹 reports");
  await around(env, 20, 21, 22, 23);
  await report(20, "198.51.100.60");
  await report(21, "198.51.100.60");
  await report(22, "198.51.100.60");
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 200, "three from one network count once");
  await report(23, "203.0.113.23");
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 200, "two networks");
  await around(env, 24);
  await report(24, "203.0.113.24");
  assert.equal((await call("GET", `/api/recipes/fonts/${id}`)).status, 404, "three networks of 이웃");
  const list = (await call("GET", "/api/admin/recipes/fonts?state=reported", undefined, admin)).data;
  assert.equal(list.items[0].whys.license, 11, "the operator sees every report");
});
