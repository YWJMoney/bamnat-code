import { all, deviceHash, deviceId, json, overLimit, readJson, run } from "./index.js";
import { HOUR } from "./units.js";

// ---- 1.36 [PUSH-01] 문의 답 바로 알리기 (사용자 2026-11-01 "계획해서 개발 진행해줘", 계획/1.36-문의답-바로알림.md) ----
//
// The phone gives its Firebase Cloud Messaging token once it has asked something; when an answer is written (by an admin,
// 자동 대응, 스튜디오 요구가 풀림·기본 도구·거절), the server sends that phone one *data* message {kind: "inquiry"} with no
// words of the question or answer in it. The phone then asks /api/inquiries itself and shows its own notice, as the
// 3-hourly job did. So Google carries only "this phone has something new", never what.
// Only with the secret FCM_SERVICE_ACCOUNT (the Firebase service account JSON); without it nothing is sent and the phone
// keeps checking by itself (15 minutes for a day, then every 3 hours).

export const FCM_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS push_phones (device TEXT PRIMARY KEY, token TEXT NOT NULL, at INTEGER NOT NULL)`,
];
export const FCM_LIMITS = { tokenMax: 4096, perHour: 20 };

const FCM_TOKEN = /^[A-Za-z0-9_:.\-]{20,4096}$/;

/** POST /api/push/phone {device, token} — keep this phone's token; DELETE /api/push/phone {device} — forget it. */
export async function fcmRoute(request, env, path) {
  if (path !== "/api/push/phone") return null;
  if (request.method !== "POST" && request.method !== "DELETE") return null;
  const body = await readJson(request, FCM_LIMITS.tokenMax + 256);
  const id = deviceId(body?.device);
  if (!id) return json({ error: "device", field: "device" }, 400);
  const dev = await deviceHash(id);
  const now = Date.now();
  if (request.method === "DELETE") {
    await run(env, "DELETE FROM push_phones WHERE device = ?", dev);
    return json({ ok: true });
  }
  const hit = await overLimit(env, now, [["device", `pushtok|${dev}`, FCM_LIMITS.perHour, HOUR]]);
  if (hit) return hit;
  if (typeof body.token !== "string" || !FCM_TOKEN.test(body.token)) return json({ error: "token", field: "token" }, 400);
  await run(env, `INSERT INTO push_phones (device, token, at) VALUES (?, ?, ?)
    ON CONFLICT (device) DO UPDATE SET token = excluded.token, at = excluded.at`, dev, body.token, now);
  return json({ ok: true, on: Boolean(env.FCM_SERVICE_ACCOUNT) });
}

let fcmCached = null;

const fcmB64 = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const fcmText = (s) => fcmB64(new TextEncoder().encode(s));

/** The service account's OAuth access token (RS256 JWT → oauth2.googleapis.com), kept until a minute before it ends. */
async function fcmAccess(env, now) {
  const sa = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  if (fcmCached && fcmCached.email === sa.client_email && fcmCached.until > now + 60_000) return { token: fcmCached.token, project: sa.project_id };
  const pem = String(sa.private_key).replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const iat = Math.floor(now / 1000);
  const head = fcmText(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = fcmText(JSON.stringify({ iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token", iat, exp: iat + 3600 }));
  const sig = fcmB64(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${head}.${claim}`)));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${head}.${claim}.${sig}`,
  });
  if (!res.ok) throw new Error(`oauth ${res.status}`);
  const t = await res.json();
  fcmCached = { email: sa.client_email, token: t.access_token, until: now + (Number(t.expires_in) || 3600) * 1000 };
  return { token: t.access_token, project: sa.project_id };
}

/**
 * [PUSH-01] The phones that asked [inquiryIds] hear "something new" at once. Never throws: an answer is saved whether or not
 * Google takes the message; a token Google says is gone is forgotten.
 */
export async function fcmTell(env, inquiryIds, now = Date.now()) {
  const ids = [...new Set((inquiryIds ?? []).filter(Boolean))].slice(0, 200);
  if (!env.FCM_SERVICE_ACCOUNT || !ids.length) return 0;
  try {
    // D1 binds at most 100 values in one query, so the questions go in groups of 90.
    const seen = new Map();
    for (let i = 0; i < ids.length; i += 90) {
      const part = ids.slice(i, i + 90);
      for (const p of await all(env, `SELECT DISTINCT p.device, p.token FROM push_phones p JOIN inquiries i ON i.device = p.device
        WHERE i.id IN (${part.map(() => "?").join(",")})`, ...part)) seen.set(`${p.device}\u0000${p.token}`, p);
    }
    return await fcmSend(env, [...seen.values()], { kind: "inquiry" }, now);
  } catch (e) {
    console.error("fcm", e && e.stack ? e.stack : String(e));
    return 0;
  }
}

/** One wordless data message to each of [phones] ({device, token}); a token Google says is gone is forgotten. */
async function fcmSend(env, phones, data, now) {
  if (!phones.length) return 0;
  const { token, project } = await fcmAccess(env, now);
  let sent = 0;
  for (const p of phones) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(project)}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ message: { token: p.token, data, android: { priority: "high", ttl: "86400s" } } }),
    });
    if (res.ok) sent++;
    else if (res.status === 404 || res.status === 400) await run(env, "DELETE FROM push_phones WHERE device = ? AND token = ?", p.device, p.token);
  }
  return sent;
}

/**
 * 1.39 [PLZC-41] The phones of [devices] (install hashes) hear "something new in 광장" ({kind}). Never throws; nothing without the
 * secret or a token.
 */
export async function fcmTellDevices(env, devices, kind, now = Date.now()) {
  const list = [...new Set((devices ?? []).filter(Boolean))].slice(0, 200);
  if (!env.FCM_SERVICE_ACCOUNT || !list.length) return 0;
  try {
    const phones = [];
    for (let i = 0; i < list.length; i += 90) {
      const part = list.slice(i, i + 90);
      phones.push(...(await all(env, `SELECT device, token FROM push_phones WHERE device IN (${part.map(() => "?").join(",")})`, ...part)));
    }
    return await fcmSend(env, phones, { kind }, now);
  } catch (e) {
    console.error("fcm", e && e.stack ? e.stack : String(e));
    return 0;
  }
}

/** For tests: forget the cached access token. */
export function fcmForget() {
  fcmCached = null;
}
