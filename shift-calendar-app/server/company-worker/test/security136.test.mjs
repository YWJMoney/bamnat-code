// 1.36 보안 점검 [SEC-01][SEC-03][SEC-04]: IPv6 by its /64, the admin phone's link code cannot be guessed over many networks,
// and a recipe picture says its size before a phone opens it.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, signIn } from "./helpers.mjs";
import { netOf } from "../src/index.js";
import { rcWebpSize, RC_IMAGE_SIDE } from "../src/recipe-community.js";
import { MONEY } from "../src/money.js";

test("[SEC-01] netOf: IPv4 as it is, IPv6 by its /64", () => {
  assert.equal(netOf("203.0.113.5"), "203.0.113.5");
  assert.equal(netOf("2001:db8:1:2:aaaa::1"), "2001:db8:1:2::/64");
  assert.equal(netOf("2001:0db8:0001:0002:ffff:ffff:ffff:ffff"), "2001:db8:1:2::/64");
  assert.equal(netOf("2001:db8::1"), "2001:db8:0:0::/64");
  assert.equal(netOf("::ffff:198.51.100.7"), "198.51.100.7");
  assert.equal(netOf(""), "unknown");
});

test("[SEC-01] a network limit holds a whole /64: a new address per try does not get past it", async () => {
  const { call } = server();
  let last;
  // The pass claim allows MONEY.claimsPerHour an hour per network.
  for (let i = 0; i <= MONEY.claimsPerHour; i++) {
    last = await call("POST", "/api/passes/claim", { code: "AAAA-BBBB-CCCC-DDDD" }, { "x-device": device(1), "cf-connecting-ip": `2001:db8:7:7::${(i + 1).toString(16)}` });
  }
  assert.equal(last.status, 429);
  const other = await call("POST", "/api/passes/claim", { code: "AAAA-BBBB-CCCC-DDDD" }, { "x-device": device(1), "cf-connecting-ip": "2001:db8:7:8::1" });
  assert.equal(other.status, 404, "the next /64 is another network");
});

test("[SEC-03] wrong link codes from many networks together spend every live code", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-12T03:00:00Z") });
  const { call } = server();
  const owner = await signIn(call);
  const code = (await call("POST", "/api/admin/me/link-code", {}, owner)).data.code;
  assert.match(code, /^\d{6}$/);
  const wrong = code === "000000" ? "000001" : "000000";
  let r;
  for (let i = 0; i < MONEY.linkWrongPerWindow; i++) {
    r = await call("POST", "/api/admin-link", { code: wrong }, { "x-device": device(100 + i), "cf-connecting-ip": `198.51.${i}.1` });
    assert.equal(r.status, 404);
  }
  const late = await call("POST", "/api/admin-link", { code }, { "x-device": device(7), "cf-connecting-ip": "192.0.2.200" });
  assert.equal(late.status, 429, "the window rests after the guessing run");
  t.mock.timers.tick(MONEY.linkMs + 1000);
  const spent = await call("POST", "/api/admin-link", { code }, { "x-device": device(7), "cf-connecting-ip": "192.0.2.200" });
  assert.equal(spent.status, 404, "the code made before the run was spent");
  const fresh = (await call("POST", "/api/admin/me/link-code", {}, owner)).data.code;
  const ok = await call("POST", "/api/admin-link", { code: fresh }, { "x-device": device(7), "cf-connecting-ip": "192.0.2.200" });
  assert.equal(ok.status, 201, "a new code works");
  const log = (await call("GET", "/api/admin/audit", undefined, owner)).data;
  assert.ok(JSON.stringify(log).includes("link_guess"));
});

const vp8 = (w, h) => Buffer.concat([Buffer.from("RIFF"), Buffer.from([30, 0, 0, 0]), Buffer.from("WEBPVP8 "), Buffer.from([10, 0, 0, 0]),
  Buffer.from([0, 0, 0, 0x9d, 0x01, 0x2a, w & 0xff, (w >> 8) & 0x3f, h & 0xff, (h >> 8) & 0x3f]), Buffer.alloc(8)]);
const vp8l = (w, h) => {
  const v = ((w - 1) & 0x3fff) | (((h - 1) & 0x3fff) << 14);
  return Buffer.concat([Buffer.from("RIFF"), Buffer.from([30, 0, 0, 0]), Buffer.from("WEBPVP8L"), Buffer.from([10, 0, 0, 0]),
    Buffer.from([0x2f, v & 0xff, (v >>> 8) & 0xff, (v >>> 16) & 0xff, (v >>> 24) & 0xff]), Buffer.alloc(13)]);
};
const vp8x = (w, h, anim = false) => Buffer.concat([Buffer.from("RIFF"), Buffer.from([30, 0, 0, 0]), Buffer.from("WEBPVP8X"), Buffer.from([10, 0, 0, 0]),
  Buffer.from([anim ? 0x02 : 0, 0, 0, 0, (w - 1) & 0xff, ((w - 1) >> 8) & 0xff, ((w - 1) >> 16) & 0xff, (h - 1) & 0xff, ((h - 1) >> 8) & 0xff, ((h - 1) >> 16) & 0xff]), Buffer.alloc(8)]);

test("[SEC-04] rcWebpSize reads VP8, VP8L and VP8X, and refuses an animation", () => {
  assert.deepEqual(rcWebpSize(vp8(512, 512)), { w: 512, h: 512 });
  assert.deepEqual(rcWebpSize(vp8l(511, 1080)), { w: 511, h: 1080 });
  assert.deepEqual(rcWebpSize(vp8x(16383, 16383)), { w: 16383, h: 16383 });
  assert.equal(rcWebpSize(vp8x(100, 100, true)), null);
  assert.equal(rcWebpSize(Buffer.from("RIFF0000WEBPJUNKxxxxxxxxxxxxxxxxxxxx")), null);
});

test("[SEC-04] a picture larger than the app ever makes is refused before anyone sees it", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-01T03:00:00Z") });
  const { call } = server({ plaza: true });
  const send = (b) => call("POST", "/api/recipes/images", { device: device(1), webp: b.toString("base64") }, { "x-device": device(1) });
  const bomb = await send(vp8x(16383, 16383));
  assert.equal(bomb.data?.error, "image_size", JSON.stringify(bomb.data));
  assert.equal((await send(vp8(RC_IMAGE_SIDE + 1, 10))).data?.error, "image_size");
  const fine = await send(vp8l(511, 1080));
  assert.equal(fine.status, 201, JSON.stringify(fine.data));
});

// ---- [SEC-11] Play Integrity ----
import { integrityNonce, verdictProblem, INTEGRITY } from "../src/integrity.js";

async function account() {
  const key = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const der = Buffer.from(await crypto.subtle.exportKey("pkcs8", key.privateKey)).toString("base64");
  return JSON.stringify({ client_email: "play@example.iam.gserviceaccount.com", private_key: `-----BEGIN PRIVATE KEY-----\n${der.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n` });
}

/** Play's decode as the test says: the token text is the verdict to give back, as JSON. */
function fakeIntegrity(t) {
  const real = globalThis.fetch;
  const scopes = [];
  globalThis.fetch = async (url, init = {}) => {
    if (String(url) === "https://oauth2.googleapis.com/token") {
      const c = new URLSearchParams(init.body).get("assertion").split(".")[1];
      scopes.push(JSON.parse(Buffer.from(c, "base64url").toString()).scope);
      return Response.json({ access_token: "pi-token", expires_in: 3600 });
    }
    assert.equal(String(url), "https://playintegrity.googleapis.com/v1/com.bamnat.app:decodeIntegrityToken");
    return Response.json({ tokenPayloadExternal: JSON.parse(Buffer.from(JSON.parse(init.body).integrity_token, "base64url").toString()) });
  };
  t.after(() => { globalThis.fetch = real; });
  return scopes;
}

const verdict = async (raw, now, over = {}) => Buffer.from(JSON.stringify({
  requestDetails: { requestPackageName: "com.bamnat.app", nonce: await integrityNonce(raw, now), timestampMillis: String(now) },
  appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED" },
  deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
  ...over,
})).toString("base64url");

test("[SEC-11] a verdict passes only for this package, this install and day, Play's app and a real phone", async () => {
  const now = Date.parse("2026-12-01T03:00:00Z");
  const ok = JSON.parse(Buffer.from(await verdict("dev-aaaaaaaaaaaaaaaa", now), "base64url").toString());
  assert.equal(await verdictProblem(ok, "dev-aaaaaaaaaaaaaaaa", "com.bamnat.app", now), null);
  assert.equal(await verdictProblem(ok, "dev-bbbbbbbbbbbbbbbb", "com.bamnat.app", now), "nonce", "another install");
  assert.equal(await verdictProblem(ok, "dev-aaaaaaaaaaaaaaaa", "com.bamnat.app", now + 3 * 24 * 3600 * 1000), "nonce", "days later");
  assert.equal(await verdictProblem({ ...ok, appIntegrity: { appRecognitionVerdict: "UNRECOGNIZED_VERSION" } }, "dev-aaaaaaaaaaaaaaaa", "com.bamnat.app", now), "app");
  assert.equal(await verdictProblem({ ...ok, deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_BASIC_INTEGRITY"] } }, "dev-aaaaaaaaaaaaaaaa", "com.bamnat.app", now), "device");
  assert.equal(await verdictProblem({ ...ok, deviceIntegrity: {} }, "dev-aaaaaaaaaaaaaaaa", "com.bamnat.app", now), "device");
});

test("[SEC-11] off by default: nothing is asked", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-01T03:00:00Z") });
  const { call } = server({ plaza: true });
  const r = await call("POST", "/api/recipes/images", { device: device(1), webp: vp8(512, 512).toString("base64") }, { "x-device": device(1) });
  assert.equal(r.status, 201);
});

test("[SEC-11] require: a picture waits for the verdict; once proven it goes, and a script's verdict does not", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-01T03:00:00Z") });
  const scopes = fakeIntegrity(t);
  const { call } = server({ plaza: true, INTEGRITY_MODE: "require", PLAY_SERVICE_ACCOUNT: await account() });
  const img = (n) => call("POST", "/api/recipes/images", { device: device(n), webp: vp8(512, 512).toString("base64") }, { "x-device": device(n) });
  const first = await img(1);
  assert.equal(first.status, 428);
  assert.equal(first.data.error, "integrity");
  const proof = await call("POST", "/api/integrity", { device: device(1), token: await verdict(device(1), Date.now()) });
  assert.equal(proof.status, 200, JSON.stringify(proof.data));
  assert.equal(proof.data.ok, true);
  assert.deepEqual(scopes, [INTEGRITY.scope]);
  assert.equal((await img(1)).status, 201);
  // Another install sending the first one's token: the nonce is not its own.
  const stolen = await call("POST", "/api/integrity", { device: device(2), token: await verdict(device(1), Date.now()) }, { "x-device": device(2), "cf-connecting-ip": "198.51.100.2" });
  assert.equal(stolen.status, 403);
  assert.equal(stolen.data.why, "nonce");
  const again = await img(2);
  assert.equal(again.status, 428);
  assert.equal(again.data.failed, true, "the app does not ask Play again within the hour");
  // A week later the verdict is asked again.
  t.mock.timers.tick(INTEGRITY.okMs + 1000);
  assert.equal((await img(1)).status, 428);
});

test("[SEC-11] watch: nothing refused, the misses are counted for 서버 상태", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-01T03:00:00Z") });
  const { call } = server({ plaza: true, INTEGRITY_MODE: "watch", PLAY_SERVICE_ACCOUNT: await account() });
  assert.equal((await call("POST", "/api/recipes/images", { device: device(1), webp: vp8(512, 512).toString("base64") }, { "x-device": device(1) })).status, 201);
  const owner = await signIn(call);
  const state = (await call("GET", "/api/admin/server", undefined, owner)).data;
  assert.equal(state.integrity.mode, "watch");
  assert.equal(state.integrity.ready, true);
  assert.equal(state.integrity.missToday, 1);
  assert.equal(state.integrity.failedDay, 0);
});

test("[SEC-11] require: 서버 상태 counts the writes it asked and the phones that failed", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-12-01T03:00:00Z") });
  const { call, env } = server({ plaza: true, INTEGRITY_MODE: "require" });
  assert.equal((await call("POST", "/api/recipes/images", { device: device(1), webp: vp8(512, 512).toString("base64") }, { "x-device": device(1) })).status, 428);
  const { deviceHash, deviceId } = await import("../src/index.js");
  await env.DB.prepare("INSERT INTO integrity (device, ok, why, at) VALUES (?, 0, 'device', ?)").bind(await deviceHash(deviceId(device(2))), Date.now() - 1000).run();
  const state = (await call("GET", "/api/admin/server", undefined, await signIn(call))).data.integrity;
  assert.equal(state.mode, "require");
  assert.equal(state.ready, false, "no service account");
  assert.equal(state.missToday, 1);
  assert.equal(state.failedDay, 1);
});

test("[SEC-11] the phone and the server make the same nonce (IntegrityProofTest)", async () => {
  assert.equal(await integrityNonce("test-device-00000001", Date.parse("2026-11-30T16:00:00Z")), "y1GEuqNN23C7lUy8L_ZOI4F8r-YuR9WiOlJwGAQA2Io");
});
