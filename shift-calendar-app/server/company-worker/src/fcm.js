import { all, deviceHash, deviceId, json, overLimit, readJson, run } from "./index.js";
import { countFailure } from "./ops-watch.js";
import { DAY, HOUR } from "./units.js";

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
  // 2.0.0 [RVQ-51] 검토 SVC-71·I22bb-04: 문의 답 알림이 못 간 문의(설치 ID·토큰 없이 문의 번호만). why: busy(구글이 바쁨·잠깐 안 닿음, 다시 보내요),
  // gone(앱을 지웠거나 알림 토큰이 바뀜), error(그 밖). 보내지면 지워지고, 30일이 지나거나 문의가 지워지면 함께 지워져요.
  `CREATE TABLE IF NOT EXISTS push_fails (inquiry_id TEXT PRIMARY KEY, why TEXT NOT NULL, tries INTEGER NOT NULL, next_at INTEGER, at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS push_fails_next ON push_fails (next_at)",
];
/** [RVQ-51] 다시 보내는 사이(첫 실패 뒤 5분, 30분, 2시간). 그 뒤엔 멈추고 관리자 문의 화면에 '알림이 못 갔어요'. */
export const PUSH_RETRY = { waits: [5 * 60 * 1000, 30 * 60 * 1000, 2 * HOUR], batch: 50 };
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
  // 2.0.0 [RVQ-10] 검토 SRV-12 (라): 지우기도 같은 한도(설치마다 시간에 20번).
  const hit = await overLimit(env, now, [["device", `pushtok|${dev}`, FCM_LIMITS.perHour, HOUR]]);
  if (hit) return hit;
  if (request.method === "DELETE") {
    await run(env, "DELETE FROM push_phones WHERE device = ?", dev);
    return json({ ok: true });
  }
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
  // 2.0.0 [RVQ-51] 검토 SVC-71: 문의마다 어떻게 됐는지(보내짐·다시 보낼 것·못 감)를 push_fails에 남겨요.
  const asked = new Map();
  try {
    // D1 binds at most 100 values in one query, so the questions go in groups of 90.
    const seen = new Map();
    for (let i = 0; i < ids.length; i += 90) {
      const part = ids.slice(i, i + 90);
      for (const p of await all(env, `SELECT DISTINCT p.device, p.token, i.id FROM push_phones p JOIN inquiries i ON i.device = p.device
        WHERE i.id IN (${part.map(() => "?").join(",")})`, ...part)) {
        seen.set(`${p.device}\u0000${p.token}`, { device: p.device, token: p.token });
        if (!asked.has(p.device)) asked.set(p.device, []);
        asked.get(p.device).push(p.id);
      }
    }
    const result = new Map();
    const sent = await fcmSend(env, [...seen.values()], { kind: "inquiry" }, now, null, result);
    await pushOutcome(env, asked, result, now);
    return sent;
  } catch (e) {
    console.error("fcm", e && e.stack ? e.stack : String(e));
    await pushOutcome(env, asked, new Map([...asked.keys()].map((d) => [d, "busy"])), now).catch(() => {});
    return 0;
  }
}

/** 2.0.0 [RVQ-51] 검토 SVC-71: 보내진 문의는 지우고, 못 간 문의는 까닭과 다음에 다시 볼 때를 적어요(busy만 다시, 세 번까지). */
async function pushOutcome(env, asked, result, now) {
  const stmts = [];
  for (const [device, inquiryIds] of asked) {
    const why = result.get(device) ?? "busy";
    for (const id of inquiryIds) {
      if (why === "ok") {
        stmts.push(env.DB.prepare("DELETE FROM push_fails WHERE inquiry_id = ?").bind(id));
        continue;
      }
      stmts.push(env.DB.prepare(`INSERT INTO push_fails (inquiry_id, why, tries, next_at, at) VALUES (?1, ?2, 1, ?3, ?4)
        ON CONFLICT (inquiry_id) DO UPDATE SET why = ?2, tries = tries + 1,
          next_at = CASE WHEN ?2 = 'busy' AND tries + 1 <= ?5 THEN ?4 + (CASE tries WHEN 1 THEN ?7 ELSE ?8 END) ELSE NULL END, at = ?4`)
        .bind(id, why, why === "busy" ? now + PUSH_RETRY.waits[0] : null, now, PUSH_RETRY.waits.length, ...PUSH_RETRY.waits));
    }
  }
  if (stmts.length) await env.DB.batch(stmts);
  const failed = [...asked.keys()].filter((d) => (result.get(d) ?? "busy") !== "ok").length;
  if (failed) await countFailure(env, "push", now);
}

/** 2.0.0 [RVQ-51] 검토 SVC-71·I22bb-04: 5분 작업. 다시 볼 때가 된 '바빠서 못 간' 문의 알림을 다시 보내요(한 번에 50건). */
export async function pushRetry(env, now = Date.now()) {
  if (!env.FCM_SERVICE_ACCOUNT) return 0;
  const due = await all(env, "SELECT inquiry_id FROM push_fails WHERE next_at IS NOT NULL AND next_at <= ? ORDER BY next_at LIMIT ?", now, PUSH_RETRY.batch);
  if (!due.length) return 0;
  // 다시 보내는 동안 겹쳐 돌지 않게 먼저 다음 때를 비워 둬요(실패하면 pushOutcome이 다시 적어요).
  await env.DB.batch(due.map((r) => env.DB.prepare("UPDATE push_fails SET next_at = NULL WHERE inquiry_id = ?").bind(r.inquiry_id)));
  return fcmTell(env, due.map((r) => r.inquiry_id), now);
}

/**
 * One wordless data message to each of [phones] ({device, token}); a token Google says is gone is forgotten. 2.0.0 [RVN-04][SVC-91] [ok], when
 * given, collects the devices Google took a message for, so a caller marks only those as told.
 */
async function fcmSend(env, phones, data, now, ok = null, result = null) {
  if (!phones.length) return 0;
  const { token, project } = await fcmAccess(env, now);
  let sent = 0;
  for (const p of phones) {
    // 2.0.0 [RVQ-51] 검토 SVC-71: 한 폰이 안 닿아도(네트워크) 나머지는 보내요.
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(project)}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ message: { token: p.token, data, android: { priority: "high", ttl: "86400s" } } }),
    }).catch(() => null);
    if (res?.ok) {
      sent++;
      ok?.add(p.device);
      result?.set(p.device, "ok");
      continue;
    }
    const why = res ? await fcmWhy(res) : "busy";
    // 2.0.0 [RVQ-51] 검토 SVC-71: 토큰은 구글이 '없는 토큰'(404 UNREGISTERED)이나 '토큰이 틀림'(400)이라고 한 때만 지워요. 전에는 400이면 모두 지워,
    // 보낸 글의 모양이 틀린 날(우리 잘못)에도 모든 폰의 토큰이 사라졌어요. 429·5xx는 다시 보내요.
    if (why === "gone") await run(env, "DELETE FROM push_phones WHERE device = ? AND token = ?", p.device, p.token);
    // 같은 폰의 다른 토큰이 보내졌으면 그 폰은 받은 것으로 둬요.
    if (result && result.get(p.device) !== "ok") result.set(p.device, why);
  }
  return sent;
}

/** 2.0.0 [RVQ-51]: 구글의 실패 답을 gone(토큰이 없거나 틀림)·busy(다시 보낼 것)·error(그 밖)로. */
async function fcmWhy(res) {
  if (res.status === 429 || res.status >= 500) return "busy";
  if (res.status === 404) return "gone";
  const body = await res.json().catch(() => null);
  const err = body?.error ?? {};
  const codes = (err.details ?? []).map((d) => d?.errorCode).filter(Boolean);
  if (codes.includes("UNREGISTERED")) return "gone";
  if (res.status === 400 && (codes.includes("INVALID_ARGUMENT") || err.status === "INVALID_ARGUMENT") && /registration token/i.test(String(err.message ?? ""))) return "gone";
  if (codes.includes("QUOTA_EXCEEDED") || codes.includes("UNAVAILABLE") || codes.includes("INTERNAL")) return "busy";
  return "error";
}

/**
 * 1.39 [PLZC-41] The phones of [devices] (install hashes) hear "something new in 광장" ({kind}). Never throws; nothing without the
 * secret or a token.
 */
export async function fcmTellDevices(env, devices, kind, now = Date.now(), ok = null) {
  const list = [...new Set((devices ?? []).filter(Boolean))].slice(0, 200);
  if (!env.FCM_SERVICE_ACCOUNT || !list.length) return 0;
  try {
    const phones = [];
    for (let i = 0; i < list.length; i += 90) {
      const part = list.slice(i, i + 90);
      phones.push(...(await all(env, `SELECT device, token FROM push_phones WHERE device IN (${part.map(() => "?").join(",")})`, ...part)));
    }
    return await fcmSend(env, phones, { kind }, now, ok);
  } catch (e) {
    console.error("fcm", e && e.stack ? e.stack : String(e));
    return 0;
  }
}

/** For tests: forget the cached access token. */
export function fcmForget() {
  fcmCached = null;
}

/**
 * 2.0.0 [RVQ-16] 검토 SRV-09: 앱이 소식·문의 답을 물을 때 이 설치의 알림 토큰과 같은 근무 알림에 '아직 쓰는 중'을 30일에 한 번 적어요.
 * 정기 정리가 180일 동안 적히지 않은 것(앱을 지운 휴대폰)을 지워요. 30일 안에 적은 것은 고치지 않아 보통은 쓰는 줄이 0이에요.
 */
export async function stillHere(env, dev, now) {
  if (!dev) return;
  await env.DB.batch([
    env.DB.prepare("UPDATE push_phones SET at = ? WHERE device = ? AND at < ?").bind(now, dev, now - 30 * DAY),
    env.DB.prepare("UPDATE plaza_watch SET at = ? WHERE device = ? AND at < ?").bind(now, dev, now - 30 * DAY),
  ]);
}
