// ---- 2.0.0 [RVQ-16] 검토 SRV-15: 서버 비밀값으로 잠그기 ----
//
// 관리자 2단계 키(D1)와 하루 백업(R2)의 비밀 칸을 AES-GCM으로 잠가요. 열쇠는 Worker 비밀값에서만 나와서 D1·R2가 새어도 열 수 없어요.
//   SEAL_KEY      있으면 이것으로 잠가요(16자 이상). 바꿀 때는 옛 값을 SEAL_KEY_OLD에 두면 옛것도 열려요.
//   RATE_SALT     SEAL_KEY가 없으면 이것(실서버에 늘 있음). 둘 다 없는 로컬 서버는 설정 표의 소금을 써요.
// 칸마다 [label]이 달라 같은 비밀값에서도 2단계 키와 백업 열쇠가 서로 다른 열쇠예요. 잠근 값은 "s1." + base64url(iv 12바이트 + 암호문).

import { all, run, salt } from "./index.js";

const sealEnc = new TextEncoder();
const sealDec = new TextDecoder();
const sealKeyCache = new Map();
export const SEALED = /^s1\.[A-Za-z0-9_-]{24,}$/;

const sealB64 = (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const sealUnb64 = (text) => Uint8Array.from(atob(text.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));

async function sealKeyOf(label, secret) {
  const id = `${label}|${secret}`;
  let k = sealKeyCache.get(id);
  if (!k) {
    const raw = await crypto.subtle.digest("SHA-256", sealEnc.encode(`seal|${id}`));
    k = await crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt", "decrypt"]);
    sealKeyCache.set(id, k);
  }
  return k;
}

/** The secrets a seal may use, the one that seals first. */
async function sealSecrets(env) {
  const own = [env.SEAL_KEY, env.SEAL_KEY_OLD].filter((s) => typeof s === "string" && s.length >= 16);
  return [...own, await salt(env)];
}

/** Whether a real secret (SEAL_KEY or RATE_SALT) seals, not the local server's own salt. */
export const sealFromSecret = (env) => (typeof env.SEAL_KEY === "string" && env.SEAL_KEY.length >= 16) || (typeof env.RATE_SALT === "string" && env.RATE_SALT !== "");

export async function seal(env, label, text) {
  const [first] = await sealSecrets(env);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await sealKeyOf(label, first), sealEnc.encode(String(text))));
  return `s1.${sealB64(new Uint8Array([...iv, ...sealed]))}`;
}

/** The text inside [value], or null when no key here opens it (a secret changed without SEAL_KEY_OLD). */
export async function unseal(env, label, value) {
  if (!SEALED.test(String(value ?? ""))) return null;
  const bytes = sealUnb64(value.slice(3));
  for (const s of await sealSecrets(env)) {
    try {
      return sealDec.decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: bytes.slice(0, 12) }, await sealKeyOf(label, s), bytes.slice(12)));
    } catch {
      // Sealed with another secret.
    }
  }
  return null;
}

// ---- 관리자 2단계 키(admin_totp.secret) ----

/** 2.0.0 [RVQ-16] The authenticator secret in [stored]: opened when sealed, as it is when it is still an old plain one; null when no key opens it. */
export async function totpOf(env, stored) {
  const v = String(stored ?? "");
  if (/^[A-Z2-7]{16,64}$/.test(v)) return v;
  return unseal(env, "totp", v);
}

export const totpSealed = (env, secret) => seal(env, "totp", secret);

/** 2.0.0 [RVQ-16] Seals the secrets still kept plain (made before 2.0.0), from the hourly job; nothing to do afterwards. */
export async function sealLegacyTotp(env) {
  const rows = await all(env, "SELECT admin, secret FROM admin_totp WHERE secret NOT LIKE 's1.%' LIMIT 20");
  for (const r of rows) {
    if (!/^[A-Z2-7]{16,64}$/.test(r.secret)) continue;
    await run(env, "UPDATE admin_totp SET secret = ? WHERE admin = ? AND secret = ?", await totpSealed(env, r.secret), r.admin, r.secret);
  }
  return rows.length;
}
