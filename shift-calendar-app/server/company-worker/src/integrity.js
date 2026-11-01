import { DAY, HOUR } from "./units.js";

// ---- 1.36 [SEC-11] 보안 점검 L4: Play Integrity (설치가 진짜 Play 앱·진짜 휴대폰인지) ----
//
// The server knew an install only by the random id the app makes, and a script can make as many as it likes: that let one person
// fill the server with fonts and pictures, age ids until their pictures showed without a look, and hide others' work with reports
// from ids of their own. Now the writes others see (올리기, 사진, 신고, 친구 초대) can ask once a week that the install show
// a Play Integrity verdict: the app from Play, on a real phone. A script has neither.
//
//   INTEGRITY_MODE  off      (default) nothing is asked: until the owner sets the service account up for Play Integrity.
//                   watch    asked and kept, nothing refused: the developer app's 서버 상태 counts how many phones would be.
//                   require  the writes below answer 428 integrity until the install proved itself in the last 7 days; the app
//                            then asks Play for a verdict (about a second) and sends the write again by itself.
//
// The verdict is asked with a nonce made of the install id and the Korean day, so a token is good for that install only and only
// that day or the next. PLAY_SERVICE_ACCOUNT (already there for purchases) must be allowed Play Integrity: Play Console › 앱 무결성 ›
// Google Cloud 프로젝트 연결, and the API turned on in that project (docs: 사용자가-해야-할-일).

export const INTEGRITY = {
  scope: "https://www.googleapis.com/auth/playintegrity",
  api: "https://playintegrity.googleapis.com/v1",
  okMs: 7 * DAY,
  failRetryMs: HOUR,
  tokenMax: 8000,
  perNetworkHour: 30,
  perDeviceDay: 10,
  /** How far the token's time may be from the server's. */
  skewMs: 2 * HOUR,
};

export const INTEGRITY_SCHEMA = [
  "CREATE TABLE IF NOT EXISTS integrity (device TEXT PRIMARY KEY, ok INTEGER NOT NULL, why TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL)",
];

export function integrityMode(env) {
  const m = String(env.INTEGRITY_MODE ?? "").trim().toLowerCase();
  return m === "watch" || m === "require" ? m : "off";
}

const integrityDay = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);

async function integrityHash(text) {
  const d = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
  return btoa(String.fromCharCode(...d)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** The nonce the app asks Play with: the install id and the Korean day, so a token proves that install that day only. */
export const integrityNonce = (rawDevice, ms) => integrityHash(`bamnat-integrity|${rawDevice}|${integrityDay(ms)}`);

/**
 * Whether a decoded verdict passes for [rawDevice] at [now]: the reason when not. The app must be Play's own build of this package
 * and the phone must meet device integrity (an emulator or a rooted phone with a changed system does not).
 */
export async function verdictProblem(payload, rawDevice, pkg, now) {
  const r = payload?.requestDetails ?? {};
  if (r.requestPackageName !== pkg) return "package";
  const nonces = [await integrityNonce(rawDevice, now), await integrityNonce(rawDevice, now - DAY)];
  if (!nonces.includes(r.nonce)) return "nonce";
  const at = Number(r.timestampMillis);
  if (!Number.isFinite(at) || Math.abs(now - at) > INTEGRITY.skewMs) return "time";
  if (payload?.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") return "app";
  const dev = payload?.deviceIntegrity?.deviceRecognitionVerdict ?? [];
  if (!Array.isArray(dev) || !(dev.includes("MEETS_DEVICE_INTEGRITY") || dev.includes("MEETS_STRONG_INTEGRITY"))) return "device";
  return null;
}

/**
 * The integrity part of a write others will see: null to go on, or the 428 answer. [d] is the server's helpers (index.js), [dev] the
 * install's hash. In watch mode a miss is only counted.
 */
export async function integrityGate(d, env, dev, now) {
  const mode = integrityMode(env);
  if (mode === "off" || !dev || dev === "operator") return null;
  const row = await d.one(env, "SELECT ok, at FROM integrity WHERE device = ?", dev);
  if (row?.ok === 1 && now - row.at < INTEGRITY.okMs) return null;
  // 서버 상태 counts the writes that came without a proof: in watch the ones it would have asked, in require the ones it asked.
  await d.allow(env, `integritymiss|${integrityDay(now)}`, Infinity, 2 * DAY, now);
  if (mode === "watch") return null;
  const failedLately = row && row.ok === 0 && now - row.at < INTEGRITY.failRetryMs;
  return d.json({ error: "integrity", field: "integrity", ...(failedLately ? { failed: true } : {}) }, 428);
}

/** What 서버 상태 shows of it. */
export async function integrityState(d, env, now, ready) {
  const miss = await d.one(env, "SELECT count FROM counters WHERE key = ? AND expires > ?", `integritymiss|${integrityDay(now)}`, now);
  const ok = await d.one(env, "SELECT COUNT(*) AS n FROM integrity WHERE ok = 1 AND at > ?", now - INTEGRITY.okMs);
  // Installs whose last verdict failed in the last day: in require they cannot write what others see.
  const failed = await d.one(env, "SELECT COUNT(*) AS n FROM integrity WHERE ok = 0 AND at > ?", now - DAY);
  return { mode: integrityMode(env), ready, missToday: miss?.count ?? 0, provenWeek: ok?.n ?? 0, failedDay: failed?.n ?? 0 };
}

/**
 * POST /api/integrity {device, token}: Play's verdict for this install, decoded with the service account and kept a week.
 * [play] gives { account, token(scope), pkg } (money.js), or null when no service account is set.
 */
export async function integrityRoute(d, request, env, play) {
  const now = Date.now();
  const body = await d.readJson(request, INTEGRITY.tokenMax + 256);
  const raw = d.deviceId(body?.device ?? request.headers.get("x-device"));
  if (!raw) return d.json({ error: "device", field: "device" }, 400);
  const dev = await d.deviceHash(raw);
  const net = await d.network(request, env, now);
  const hit = await d.overLimit(env, now, [["network", `integrity|${net.now}`, INTEGRITY.perNetworkHour, HOUR], ["device", `integrity|${dev}`, INTEGRITY.perDeviceDay, DAY]]);
  if (hit) return hit;
  if (typeof body.token !== "string" || body.token.length < 20 || body.token.length > INTEGRITY.tokenMax) return d.json({ error: "token", field: "token" }, 400);
  const p = play(env);
  if (!p) return d.json({ ok: false, why: "not_set" }, 503);
  let payload;
  try {
    const res = await fetch(`${INTEGRITY.api}/${encodeURIComponent(p.pkg)}:decodeIntegrityToken`, {
      method: "POST",
      headers: { authorization: `Bearer ${await p.token(INTEGRITY.scope)}`, "content-type": "application/json" },
      body: JSON.stringify({ integrity_token: body.token }),
    });
    if (!res.ok) return d.json({ ok: false, why: "play" }, 502);
    payload = (await res.json())?.tokenPayloadExternal;
  } catch {
    return d.json({ ok: false, why: "play" }, 502);
  }
  const why = await verdictProblem(payload, raw, p.pkg, now);
  await d.run(env, `INSERT INTO integrity (device, ok, why, at) VALUES (?, ?, ?, ?)
    ON CONFLICT (device) DO UPDATE SET ok = excluded.ok, why = excluded.why, at = excluded.at`, dev, why ? 0 : 1, why ?? "", now);
  return d.json({ ok: !why, ...(why ? { why } : {}), until: why ? null : new Date(now + INTEGRITY.okMs).toISOString() }, why ? 403 : 200);
}
