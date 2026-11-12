// 1.40 시작
// ---- [PAYP-19] 공유급여 결정 1: 옛 앱(1.38 이하)에 회사 판 급여 계산식 주기 끊기 (사용자 결정 2026-11-07 19:21, 1.40 출시 날) ----
//
// 1.39 앱은 판의 급여 부분을 받지 않고(X-Pay-Private) 쓸 때만 /take로 받아요. 옛 앱의 참여자는 호환 때문에 아직 받아서, 옛 앱으로
// 계산식이 새요. 끊는 날은 1.40 출시 날이에요. 그날 따로 서버를 배포하지 않아도 되게, 이 코드는 미리 나가도 아무것도 바꾸지 않고
// 서버가 '나간 판'(settings shipped_version, 자동 순환이 Play 100% 출시를 보면 저장)이 1.40.0 이상이 된 때부터 끊어요.
// 관리자는 관리 › 옛 앱 급여 끊기에서 지금 끊기(on)·끊지 않기(off)·저절로(auto)를 고를 수 있어요.

import { all, audit, json, readJson, refuse, run } from "./index.js";
import { compareVersions, version } from "./inbox.js";
import { DAY } from "./units.js";

export const OLD_PAY = {
  /** 이 판이 나가면 저절로 끊어요. */
  from: "1.40.0",
  /** 옛 앱: 1.38.0(versionCode 52)까지. 1.39(53)부터는 판의 급여를 받지 않아요. */
  maxApp: 52,
  /** 옛 앱 공지가 떠 있는 날 수. */
  days: 30,
  modes: ["auto", "on", "off"],
  /** 공지 id: 관리자 공지와 같은 꼴(n + 9자)이라 옛 앱이 그대로 읽어요. */
  noticeId: "nOldPay140",
  title: "회사 급여 방식은 최신 판에서",
  body: "회사 급여 방식은 밤낮 최신 판에서 쓸 수 있어요. Play 스토어에서 밤낮을 업데이트해 주세요. 이미 넣은 급여는 그대로예요.",
};

/** 관리자가 고른 방식([raw]: settings old_pay_cut 값): auto(기본), on, off. */
function modeOf(raw) {
  try {
    const v = JSON.parse(raw ?? "null");
    return OLD_PAY.modes.includes(v?.mode) ? v : { mode: "auto" };
  } catch {
    return { mode: "auto" };
  }
}

/** settings의 old_pay_cut·shipped_version 값([mode], [shipped], 날 글자 그대로)으로: {on, at, mode, shipped}. */
export function cutFrom(modeRaw, shippedRaw) {
  const m = modeOf(modeRaw);
  let shipped = null;
  try {
    const v = JSON.parse(shippedRaw ?? "null");
    shipped = typeof v?.v === "string" && version(v.v) ? v : null;
  } catch {
    shipped = null;
  }
  const sv = shipped?.v ?? null;
  if (m.mode === "off") return { on: false, at: null, mode: "off", shipped: sv };
  if (m.mode === "on") return { on: true, at: Number.isFinite(m.at) ? m.at : null, mode: "on", shipped: sv };
  const out = shipped ? compareVersions(version(shipped.v), version(OLD_PAY.from)) >= 0 : false;
  return { on: out, at: out && Number.isFinite(shipped.at) ? shipped.at : null, mode: "auto", shipped: sv };
}

/** 지금 옛 앱에 급여 부분을 주지 않는지(한 번 읽기). */
export async function oldPayCut(env) {
  const rows = await all(env, "SELECT key, value FROM settings WHERE key IN ('old_pay_cut', 'shipped_version')").catch(() => []);
  return cutFrom(rows.find((r) => r.key === "old_pay_cut")?.value, rows.find((r) => r.key === "shipped_version")?.value);
}

/** 끊긴 동안 옛 앱(maxApp 52)에만 보이는 공지 한 장. 저장하지 않고 config에서만 붙여요(관리자 공지 10장 한도와 따로). */
export function oldPayNotice(cut, now) {
  if (!cut.on) return null;
  const from = cut.at ?? now;
  if (now >= from + OLD_PAY.days * DAY) return null;
  return { id: OLD_PAY.noticeId, title: OLD_PAY.title, body: OLD_PAY.body, level: "important", from, until: from + OLD_PAY.days * DAY,
    minApp: null, maxApp: OLD_PAY.maxApp, audience: null, action: null };
}

/** 관리자 화면: GET 지금 상태, POST {mode} 바꾸기. */
export async function oldPayRoute(request, env, method, now) {
  if (method === "GET") return json({ ...(await oldPayCut(env)), from: OLD_PAY.from, maxApp: OLD_PAY.maxApp });
  const body = await readJson(request, 256);
  if (!OLD_PAY.modes.includes(body.mode)) refuse("mode", "mode");
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('old_pay_cut', ?)", JSON.stringify({ mode: body.mode, at: now }));
  await audit(env, now, "old_pay_cut", body.mode);
  return json({ ...(await oldPayCut(env)), from: OLD_PAY.from, maxApp: OLD_PAY.maxApp });
}
// 1.40 끝
