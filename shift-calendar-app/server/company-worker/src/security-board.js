// ---- 2.0.0 [RVQ-18] 검토 I21b-01: 관리 › 보안 한 장 ----
//
// 혼자 운영하는 사람이 '지금 안전한가'를 10초에 봐요. 줄마다 초록(ok)·주황(warn)·빨강(bad)과 한 줄 상태, 고치는 곳(관리자 앱 안의 화면이나
// Cloudflare 설정 이름). 값은 하나도 보이지 않아요: 비밀값은 이름과 '바뀐 날'만.
//   바뀐 날  비밀값마다 서버 소금으로 만든 16자 표시(secret_marks)를 남겨 두고, 표시가 달라진 날을 '바꾼 날'로 적어요. 표시는 소금 없이는
//           되짚을 수 없고 값의 일부도 아니에요. 처음 본 날은 '처음 본 날부터 그대로'로 보여요.
import { all, OWNER, one, run, salt, sha256 } from "./index.js";
import { integrityMode } from "./integrity.js";
import { backupView } from "./keep.js";
import { DAY } from "./units.js";

export const SECURITY = {
  /** 비밀값: 이름, 왜 바꾸는지, 바꾸라고 할 나이(일). 없어도 되는 것은 optional. */
  secrets: [
    { name: "ADMIN_PASSWORD", what: "최고 관리자 비밀번호", days: 365 },
    { name: "RATE_SALT", what: "설치 해시·로그인 서명·백업 잠금의 소금", days: 730 },
    { name: "SEAL_KEY", what: "2단계 키·백업 비밀 칸 잠금", days: 730, optional: true },
    { name: "PLAY_SERVICE_ACCOUNT", what: "Play 결제·리뷰·무결성 서비스 계정 열쇠", days: 365, optional: true },
    { name: "MAIL_API_KEY", what: "메일 보내기 열쇠", days: 365, optional: true },
  ],
  /** 운영 열쇠가 곧 끝나는 날(일)과 오래 안 쓴 날(일). */
  keySoonDays: 7,
  keyIdleDays: 60,
};

async function secretMarks(env, now) {
  let kept = {};
  try {
    kept = JSON.parse((await one(env, "SELECT value FROM settings WHERE key = 'secret_marks'"))?.value ?? "{}") ?? {};
  } catch {
    kept = {};
  }
  const s = await salt(env);
  let changed = false;
  const out = {};
  for (const x of SECURITY.secrets) {
    const v = env[x.name];
    if (typeof v !== "string" || !v) {
      out[x.name] = { set: false };
      continue;
    }
    const mark = (await sha256(`secret-mark|${s}|${x.name}|${v}`)).slice(0, 16);
    const was = kept[x.name];
    if (!was || was.mark !== mark) {
      out[x.name] = { set: true, mark, since: now, first: !was };
      changed = true;
    } else out[x.name] = { set: true, mark, since: was.since, first: Boolean(was.first) };
  }
  if (changed) {
    const store = Object.fromEntries(Object.entries(out).filter(([, x]) => x.set).map(([k, x]) => [k, { mark: x.mark, since: x.since, first: x.first }]));
    await run(env, "INSERT INTO settings (key, value) VALUES ('secret_marks', ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value", JSON.stringify(store));
  }
  return out;
}

const kday = (ms) => new Date(ms + 9 * 3600e3).toISOString().slice(0, 10);

/** The page: rows of {key, name, tone, state, fix: {label, href?}}, worst first, and the counts. */
export async function securityView(env, now = Date.now()) {
  const rows = [];
  const add = (key, name, tone, state, fix) => rows.push({ key, name, tone, state, fix });

  // 최고 관리자의 2단계와 패스키, 2단계 풀기 스위치.
  const totp = await one(env, "SELECT on_at FROM admin_totp WHERE admin = ?", OWNER.id);
  if (env.ADMIN_2FA_RESET === "1") {
    add("owner2fa", "최고 관리자 2단계", "bad", "ADMIN_2FA_RESET이 켜져 있어 비밀번호만으로 들어올 수 있어요", { label: "Cloudflare › 회사 서버 › 설정 › 변수에서 ADMIN_2FA_RESET 지우기" });
  } else if (totp?.on_at) add("owner2fa", "최고 관리자 2단계", "ok", `${kday(totp.on_at)}부터 켜져 있어요`, { label: "내 계정 › 2단계", href: "#/account" });
  else add("owner2fa", "최고 관리자 2단계", "bad", "꺼져 있어요. 비밀번호가 새면 바로 들어와요", { label: "내 계정 › 2단계 켜기", href: "#/account" });

  const passkeys = await one(env, "SELECT COUNT(*) AS n, MAX(last_at) AS last FROM admin_passkeys WHERE admin = ?", OWNER.id);
  add("passkeys", "패스키", Number(passkeys?.n ?? 0) ? "ok" : "warn", Number(passkeys?.n ?? 0)
    ? `${passkeys.n}개${passkeys.last ? ` · 마지막 사용 ${kday(passkeys.last)}` : ""}` : "없어요. 패스키로 들어오면 비밀번호를 칠 일이 줄어요", { label: "내 계정 › 패스키", href: "#/account" });

  // 다른 관리자 가운데 2단계가 꺼진 사람.
  const admins = await all(env, `SELECT a.name, t.on_at FROM admins a LEFT JOIN admin_totp t ON t.admin = a.id AND t.on_at IS NOT NULL WHERE a.status = 'active'`);
  const without = admins.filter((a) => !a.on_at);
  add("admins2fa", "다른 관리자 2단계", !admins.length ? "ok" : without.length ? "warn" : "ok", !admins.length ? "다른 관리자가 없어요"
    : without.length ? `${without.length}명이 꺼져 있어요: ${without.slice(0, 3).map((a) => a.name).join(", ")}${without.length > 3 ? " 외" : ""}` : `${admins.length}명 모두 켜져 있어요`,
  { label: "관리자", href: "#/admins" });

  // 살아 있는 로그인(최고 관리자·다른 관리자).
  const sessions = await all(env, "SELECT admin, COUNT(*) AS n FROM admin_sessions WHERE expires > ? GROUP BY admin", now);
  const mine = Number(sessions.find((x) => x.admin === OWNER.id)?.n ?? 0);
  const others = sessions.filter((x) => x.admin !== OWNER.id).reduce((n, x) => n + Number(x.n), 0);
  add("sessions", "살아 있는 로그인", mine > 5 ? "warn" : "ok", `최고 관리자 ${mine}곳${others ? ` · 다른 관리자 ${others}곳` : ""}${mine > 5 ? " · 모르는 곳이 있으면 모두 끊어요" : ""}`,
    { label: "내 계정 › 로그인", href: "#/account" });

  // Claude 운영 열쇠.
  const keys = await all(env, "SELECT label, expires_at, last_at FROM admin_keys WHERE revoked_at IS NULL AND expires_at > ?", now);
  const soon = keys.filter((k) => k.expires_at - now < SECURITY.keySoonDays * DAY);
  const idle = keys.filter((k) => !k.last_at || now - k.last_at > SECURITY.keyIdleDays * DAY);
  add("keys", "Claude 운영 열쇠", idle.length ? "warn" : "ok", !keys.length ? "살아 있는 열쇠가 없어요"
    : `${keys.length}개${soon.length ? ` · ${soon.length}개 ${SECURITY.keySoonDays}일 안에 끝나요` : ""}${idle.length ? ` · ${idle.length}개는 ${SECURITY.keyIdleDays}일 넘게 안 썼어요(거두기)` : ""}`,
  { label: "Claude 열쇠", href: "#/keys" });

  // 비밀값: 있는지와 바꾼 날.
  const marks = await secretMarks(env, now);
  for (const x of SECURITY.secrets) {
    const m = marks[x.name];
    if (!m.set) {
      if (x.name === "SEAL_KEY") add(`secret:${x.name}`, `${x.name} (${x.what})`, "warn", "따로 두지 않아 RATE_SALT로 잠가요. 두면 RATE_SALT를 바꿀 때 다시 잠그지 않아도 돼요",
        { label: "Cloudflare › 회사 서버 › 설정 › 변수와 비밀에 SEAL_KEY(16자 이상) 더하기" });
      else if (!x.optional) add(`secret:${x.name}`, `${x.name} (${x.what})`, "bad", "없어요", { label: "Cloudflare › 회사 서버 › 설정 › 변수와 비밀" });
      continue;
    }
    const age = Math.floor((now - m.since) / DAY);
    add(`secret:${x.name}`, `${x.name} (${x.what})`, age > x.days ? "warn" : "ok", `${m.first ? `처음 본 날 ${kday(m.since)}부터 그대로` : `${kday(m.since)}에 바꿈`}${age > x.days ? ` · ${age}일 지났어요` : ""}`,
      { label: "Cloudflare › 회사 서버 › 설정 › 변수와 비밀(바꾼 뒤 이 화면이 날짜를 적어요)" });
  }

  // 2단계 키가 모두 잠겼는지(2.0.0 전 것은 매시 작업이 잠가요).
  const plain = Number((await one(env, "SELECT COUNT(*) AS n FROM admin_totp WHERE secret NOT LIKE 's1.%'"))?.n ?? 0);
  add("totpSealed", "2단계 키 잠금", plain ? "warn" : "ok", plain ? `${plain}개가 아직 잠기지 않았어요(다음 매시 작업이 잠가요)` : "모두 서버 비밀값으로 잠겨 있어요", { label: "기다리면 돼요" });

  // Play 무결성.
  const mode = integrityMode(env);
  add("integrity", "Play 무결성 확인", mode === "require" ? "ok" : "warn", { off: "꺼져 있어요. 스크립트가 만든 설치도 올리기·신고를 할 수 있어요", watch: "보기만 해요(막지 않음)", require: "진짜 Play 앱·진짜 휴대폰만 올리기·신고를 해요" }[mode],
    { label: mode === "off" ? "Cloudflare › 회사 서버 › 변수 INTEGRITY_MODE를 watch로(서버 상태에서 몇 대가 걸리는지 본 뒤 require)" : "서버 상태", href: mode === "off" ? undefined : "#/server" });

  // 백업과 되살리기 연습.
  const b = await backupView(env, now);
  const drill = b.drill;
  add("backup", "하루 백업", !b.ready ? "bad" : b.late ? "bad" : "ok", !b.ready ? "R2가 없어 D1 되돌리기만 있어요" : b.late ? "오늘 백업이 늦어요" : `마지막 성공 ${b.lastOk ?? "없음"}`,
    { label: "백업", href: "#/backup" });
  add("drill", "되살리기 연습", !b.ready ? "warn" : !drill?.doneAt ? "warn" : !drill.ok ? "bad" : b.drillLate ? "warn" : "ok",
    !b.ready ? "백업이 생기면 해요" : !drill?.doneAt ? "아직 끝낸 연습이 없어요" : !drill.ok ? `${drill.problems.length}개 표에 문제가 있어요` : `${kday(drill.doneAt)} · 표 ${drill.tables}개 맞음`,
    { label: "백업 › 되살리기 연습", href: "#/backup" });

  const order = { bad: 0, warn: 1, ok: 2 };
  rows.sort((a, c) => order[a.tone] - order[c.tone]);
  return { rows, bad: rows.filter((r) => r.tone === "bad").length, warn: rows.filter((r) => r.tone === "warn").length, ok: rows.filter((r) => r.tone === "ok").length, at: now };
}
