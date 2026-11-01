import { all, one, run } from "./index.js";
import { DAY } from "./units.js";

// ---- 1.36 [GRW-08] 사진 작품 세 겹 (사용자 2026-11-01 "사진 신고제 == 5-6의 세 겹", 2차 조사 5-6) ----
//
// 개발자가 사진을 하나하나 승인하던 것을 세 겹으로 바꿔요. 운영자 손은 새싹의 첫 사진과 신고·자동 검사에 걸린 것만 봐요.
//   1 자동 사전 검사   올릴 때 서버가 봐요(env.AI + PHOTO_CHECK_MODEL이 있을 때만). 걸리면 'held'로 아무에게도 안 보이고 운영자에게 가요.
//                      검사 모델은 Workers AI 목록에서 사용자가 골라 설정해요(확인 필요: 2026-11-01 검색으로는 Workers AI에 사진 성인물 분류 전용
//                      모델을 확정하지 못했어요). 설정이 없으면 'unchecked'로 두고 둘째 겹이 정해요.
//   2 믿음 단계        새싹(믿음 0)이거나 운영자가 아직 3장을 보지 않은 휴대폰은 첫 사진을 운영자가 봐요. 이웃·단골은 검사를 통과하면 바로
//                      보여요. 검사가 없을 때는 단골이거나 이미 3장이 통과된 휴대폰만 바로 보여요.
//   3 신고 즉시 가림   사진이 든 작품에 신고가 2번(작품 신고는 3번) 오면 사진부터 먼저 가리고('held') 운영자가 판단해요. 신고가 맞으면
//                      가림('hidden')과 쓰기 제한 한 번(V1 단계 규칙).
// 한국: 매출 10억 원 이상이거나 하루 평균 이용자 10만 명 이상이면 전기통신사업법 22조의5 기술적 조치 의무가 생겨요. 그때는 공식 비교 DB
// 대조를 첫째 겹에 더해요(지금은 해당 없음).

export const PHOTO_GUARD = {
  /** 운영자가 먼저 보는 사진 수: 이만큼 통과한 휴대폰부터 바로 보여요. */
  lookFirst: 3,
  /** 사진 신고가 이만큼이면 먼저 가려요(작품 신고 3번보다 빨리). */
  flagsToHold: 2,
  /** 자동 검사가 이 시간 안에 답하지 않으면 'unchecked'. */
  checkMs: 4000,
};
export const PHOTO_FLAG_REASONS = ["nude", "violence", "personal", "copy", "other"];

export const PHOTO_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS recipe_image_flags (id TEXT NOT NULL, device TEXT NOT NULL, reason TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (id, device))`,
];
export const PHOTO_COLUMNS = [
  // pass·block·unchecked, why it waits (first·check·flags) and how many reports.
  "ALTER TABLE recipe_images ADD COLUMN checked TEXT",
  "ALTER TABLE recipe_images ADD COLUMN why TEXT",
  "ALTER TABLE recipe_images ADD COLUMN flags INTEGER NOT NULL DEFAULT 0",
];

/**
 * [GRW-08] 첫째 겹. A vision model asked one question; anything but a clear SAFE/UNSAFE (or no model, or slow) is unchecked.
 * The model's answer is read as words, never run.
 */
export async function autoCheck(env, base64) {
  const model = typeof env.PHOTO_CHECK_MODEL === "string" ? env.PHOTO_CHECK_MODEL : "";
  if (!env.AI || typeof env.AI.run !== "function" || !model) return "unchecked";
  try {
    const bin = atob(base64);
    const image = Array.from(bin, (c) => c.charCodeAt(0));
    const ask = env.AI.run(model, {
      image,
      prompt: "Does this image contain nudity, sexual content, gore, or graphic violence? Answer with one word: SAFE or UNSAFE.",
      max_tokens: 4,
    });
    const out = await Promise.race([ask, new Promise((r) => setTimeout(() => r(null), PHOTO_GUARD.checkMs))]);
    const text = String(out?.response ?? out?.description ?? out?.result ?? "").toUpperCase();
    if (/UNSAFE/.test(text)) return "block";
    if (/\bSAFE\b/.test(text)) return "pass";
    return "unchecked";
  } catch (e) {
    console.error("photo check", e && e.stack ? e.stack : String(e));
    return "unchecked";
  }
}

/** [GRW-08] 둘째 겹: the state a new picture gets from the check and the uploader's trust ({ state, why }). */
export function firstState(check, trustLevel, passed) {
  if (check === "block") return { state: "held", why: "check" };
  const known = passed >= PHOTO_GUARD.lookFirst;
  if (trustLevel <= 0 && !known) return { state: "pending", why: "first" };
  if (check === "pass") return { state: "ok", why: null };
  return trustLevel >= 2 || known ? { state: "ok", why: null } : { state: "pending", why: "first" };
}

/** How many of [dev]'s pictures were shown (passed the operator or the rules) and not hidden since. */
export async function passedPhotos(env, dev) {
  return (await one(env, "SELECT COUNT(*) AS n FROM recipe_images WHERE owner = ? AND state = 'ok'", dev)).n;
}

/** [GRW-08] 셋째 겹: one report on a picture; [flagsToHold] hold it from everyone until the operator looks. */
export async function flagImage(env, id, dev, reason, now) {
  const img = await one(env, "SELECT id, owner, state, flags FROM recipe_images WHERE id = ?", id);
  if (!img || img.owner === dev || img.state === "hidden") return { held: false };
  const r = await run(env, "INSERT OR IGNORE INTO recipe_image_flags (id, device, reason, at) VALUES (?, ?, ?, ?)", id, dev,
    PHOTO_FLAG_REASONS.includes(reason) ? reason : "other", now);
  if (!r.meta.changes) return { held: img.state === "held", already: true };
  const flags = img.flags + 1;
  const hold = img.state === "ok" && flags >= PHOTO_GUARD.flagsToHold;
  await run(env, `UPDATE recipe_images SET flags = ?${hold ? ", state = 'held', why = 'flags', decided_at = ?" : ""} WHERE id = ?`, ...(hold ? [flags, now, id] : [flags, id]));
  return { held: hold || img.state === "held" };
}

/** The picture ids a recipe's text names (cover, 그림 꾸미기, 화면 배경), as recipe_images keeps them. */
export async function imagesOf(env, body) {
  const ids = [...new Set(String(body ?? "").match(/(?<![0-9a-f])[0-9a-f]{16}(?![0-9a-f])/g) ?? [])].slice(0, 20);
  if (!ids.length) return [];
  return (await all(env, `SELECT id FROM recipe_images WHERE id IN (${ids.map(() => "?").join(",")})`, ...ids)).map((r) => r.id);
}

/** [GRW-08] A report on a recipe with pictures is a report on each of its pictures too (사진은 2번이면 먼저 가려요). */
export async function flagRecipeImages(env, body, cover, dev, reason, now) {
  const map = { personal: "personal", harm: "violence", copy: "copy" };
  const ids = await imagesOf(env, `${body ?? ""} ${cover ?? ""}`);
  let held = 0;
  for (const id of ids) if ((await flagImage(env, id, dev, map[reason] ?? "other", now)).held) held++;
  return held;
}

/** [GRW-08] PC 8d290ec3: how many of a recipe's pictures wait for the operator, for the maker's '사진 확인 중'. */
export async function waitingPhotos(env, body, cover) {
  const ids = await imagesOf(env, `${body ?? ""} ${cover ?? ""}`);
  if (!ids.length) return 0;
  return (await one(env, `SELECT COUNT(*) AS n FROM recipe_images WHERE state IN ('pending', 'held') AND id IN (${ids.map(() => "?").join(",")})`, ...ids)).n;
}

export const PHOTO_WHY = { first: "새싹 첫 사진", check: "자동 검사에 걸림", flags: "신고로 먼저 가림" };
export const photoStaleMs = 2 * DAY;
