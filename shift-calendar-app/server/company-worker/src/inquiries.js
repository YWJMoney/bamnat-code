import { FLAGS, LIMITS, PHOTO, Refuse, admin, all, audit, authorKey, authorToken, batch, can, deviceHash, deviceId, dropInquiries, hiddenAuthors, history, iso, item, json, list, me, network, networkCheck, notifyAdmins, one, overLimit, personal, photoFile, queue, randomId, readJson, readPhotos, refuse, run, stmt } from "./index.js";
import { DAY, HOUR } from "./units.js";
import { SORTING, autoAnswer, compareVersions, grams, likeness, needsPerson, needsPersonWhy, version } from "./inbox.js";
import { replyRisk } from "./ledger.js";
import { billingWordOf, isBillingRecord, replyRiskOf } from "./ask-check.js";
import { careAppealOf } from "./community-care.js";
// 1.37 [ADM2-02] 나간 판.
import { DESK, isOut, shippedVersion } from "./desk.js";
import { needFromInquiry } from "./growth.js";
import { fcmTell, stillHere } from "./fcm.js";
import { kstDay } from "./usage.js";
import { bugFixed, ideaAdopted } from "./money.js";
import { LOOP, WAITING_SQL, basisOf, effectOf, styleList, tierEffective, fixedAnswer, fixedAsk, heldLog, instantOk, phoneArrived } from "./loop.js";
// ---- questions from the app [ADMIN-02] ----
//
// 설정 > 문의하기 sends here instead of opening a mail app, and the developer answers in /admin; the
// answer shows in the app. Like a report it carries no amount, contact or name: those are refused, not
// cleaned. What goes with it (app version, phone model, Android, the alarm check if the user ticks it)
// is shown in the app before sending. Only the install's hash is kept, so only that phone can write to,
// or delete, the thread, and a thread with nothing new for 180 days is deleted. A question sent as
// public is also in 모두의 문의 (below), without anything that went with it.

export const INQUIRY = {
  // [INQ-01] Since 1.14.1 suggestions, anything else and a company's way the app cannot set come here too.
  kinds: ["question", "bug", "idea", "company", "other", "studio"],
  text: 2000,
  diag: 6000,
  pay: 3000,
  setup: 3000,
  bytes: 16 * 1024,
  perDay: 5,
  perNetworkDay: 200, // R131 1.31 [LIM-01] 20 before
  messagesPerHour: 20,
  // [INQ-04] A question waiting on an admin longer than this is 놓친 문의.
  lateMs: DAY,
  // Each side of a thread; a longer back and forth is better as a new question.
  messages: 40,
  keepMs: 180 * DAY,
  // 1.36 [INQ-07] A question about paying or a refund is the 소비자 불만·분쟁 처리 기록 the 전자상거래법 시행령
  // 제6조 keeps for three years: from its last word, and still when the asker deletes it (처리방침 3.10).
  billingKeepMs: 3 * 366 * DAY,
};

/**
 * 1.36 [INQ-07] Words that make a question a 결제·환불 record. Narrower than the 결제 topic below (광고 or 무료
 * alone is not a dispute about money), and an admin can set or clear it on the question by hand.
 */
// [RVD-05] 검토 ADMX-03: '더해지는'의 '해지', '달력 구독', '통신 요금'은 아니에요. 글을 고른 뒤 짝으로 봐요(src/ask-check.js).
export const BILLING_RECORD = { test: (text) => isBillingRecord(text) };

/**
 * 1.40 [RVD-05] 검토 ADMX-03: 옛 식('해지' 하나로)이 결제·환불 기록으로 찍은 평범한 문의를 지금 식으로 다시 봐요. 관리자가 손으로
 * 켠 것('inquiry_billing' on)은 그대로, 보낸 분이 지운 것(gone)도 그대로예요. 한 번에 [limit]건, 다 보면 settings에 적고 멈춰요.
 */
export async function rescanBilling(env, now, limit = 300) {
  if (await one(env, "SELECT value FROM settings WHERE key = 'billing_rescan'")) return 0;
  const rows = await all(env, `SELECT i.id, (SELECT GROUP_CONCAT(m.text, ' ') FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user') AS words
    FROM inquiries i WHERE i.billing_at IS NOT NULL AND i.device NOT LIKE 'gone%' AND i.id > COALESCE((SELECT value FROM settings WHERE key = 'billing_rescan_at'), '')
      AND NOT EXISTS (SELECT 1 FROM audit a WHERE a.action = 'inquiry_billing' AND a.target = i.id) ORDER BY i.id LIMIT ?`, limit);
  const off = rows.filter((r) => !isBillingRecord(r.words ?? "")).map((r) => r.id);
  const stmts = off.map((id) => stmt(env, "UPDATE inquiries SET billing_at = NULL WHERE id = ?", id));
  stmts.push(rows.length < limit
    ? stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('billing_rescan', ?)", String(now))
    : stmt(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('billing_rescan_at', ?)", rows.at(-1).id));
  await batch(env, stmts);
  if (off.length) await audit(env, now, "inquiry_billing_rescan", `${off.length}건`, "결제·환불 기록 아님(검토 ADMX-03)");
  return off.length;
}

/** 1.36 [INQ-07] The questions old enough to go: half a year without a word, three years for a 결제·환불 record. */
export const expiredInquiries = (now) => ["(billing_at IS NULL AND updated_at < ?) OR updated_at < ?", now - INQUIRY.keepMs, now - INQUIRY.billingKeepMs];

/**
 * 1.36 [INQ-07] What deleting the questions [where] picks does: an ordinary one goes with everything hung on
 * it; a 결제·환불 record leaves the asker's app (cut from the phone, off 모두의 문의) and stays with the
 * developer until its three years are over.
 */
export const forgetInquiries = (env, where, ...params) => {
  // 처리방침 3.10: keeping a deleted 결제·환불 record starts on 2026-12-02 (30 days' notice); before then, deleting deletes.
  const keep = Date.now() >= BILLING_KEEP_FROM ? 1 : 0;
  return [
    stmt(env, `UPDATE inquiries SET device = 'gone' || id, public = 0, status = 'closed' WHERE billing_at IS NOT NULL AND ${keep} = 1 AND (${where})`, ...params),
    ...dropInquiries(env, `(billing_at IS NULL OR ${keep} = 0) AND (${where})`, ...params),
  ];
};

/** 처리방침 3.10's date for 결제·환불 records kept after the asker deletes them: 2026-12-02 00:00 KST. */
export const BILLING_KEEP_FROM = Date.UTC(2026, 11, 1, 15);

/** What the app may send with a question, as the developer app shows it: label, field, longest. */
export const INQUIRY_INFO = [
  ["앱", "app", 40],
  ["기기", "model", 80],
  ["Android", "android", 40],
  ["알람 점검", "diag", INQUIRY.diag],
  // 급여 > 계산이 이상해요: the month's rules and each line's hours and rate, never an amount.
  ["급여 계산", "pay", INQUIRY.pay],
  // [INQ-01] 회사 방식 알려 주기: the kind's settings in words, never an amount.
  ["회사 설정", "setup", INQUIRY.setup],
  // 1.36 [GRW-01] 스튜디오 요구: 보낸 분의 근무 유형(SHIFT…), 장터 요청 판의 어느 칸에 올릴지.
  ["근무 유형", "studio", 12],
];

/** [INQ-01] A company's way names the workplace: it stays with the developer, whatever the app asks. */
export const PRIVATE_KINDS = ["company"];

// ---- sorting questions without reading each one [ADMIN-02] ----
//
// Every question gets a topic and an urgency from its words, its kind and what came with it, worked
// out when the list is read, so better words sort old questions too and nothing new is stored. The
// developer filters by topic and sees the urgent ones first instead of opening each to sort it.

/** Topic, label and the words that point to it; the most matched topic wins, the first on a tie. */
// [INQ-05] The app sorts with the same table: core/ops/InquiryTopics.kt (a test keeps them alike).
export const INQUIRY_TOPICS = [
  ["alarm", "알람", /알람|울리|울려|울림|소리|진동|깨워|스누즈|다시\s*울/g],
  ["pay", "급여 계산", /급여|월급|시급|일당|연봉|수당|가산|명세서|통상임금|주휴|연차|퇴직|세금|4대\s*보험|계산/g],
  // 1.35 [J135-19] 꾸미기·테마, before 근무표·달력 so a question naming 달력 칸 and 테마 alike is 꾸미기.
  ["look", "꾸미기·테마", /테마|꾸미|꾸며|꾸민|배경|글꼴|이모지|레시피|장터|마을|공방|스튜디오|입어\s*보|벗기/g],
  ["schedule", "근무표·달력", /근무표|달력|캘린더|교대|패턴|근무\s*유형|대근|휴무|비번|야간|주간|오프|일정|메모/g],
  ["share", "공유", /공유|링크|상대방|가족|동기화|받은\s*사람|보낸\s*사람/g],
  ["company", "회사 설정", /회사|부서|규칙|투표|토론|채택/g],
  ["backup", "백업·기기 이동", /백업|복원|새\s*(폰|휴대폰|기기)|기기\s*변경|옮기|가져오기|내보내기/g],
  ["billing", "결제", /결제|구독|프리미엄|환불|구매|광고|유료|무료|보상|이용권/g],
  ["app", "앱 동작", /멈춰|멈춤|멈추|멈췄|꺼져|꺼짐|닫혀|닫힘|닫혔|튕|먹통|굳어|렉|응답\s*없|강제\s*종료|오류|에러|버그|안\s*열|안\s*켜|느려|느림|로딩|하얀\s*화면|검은\s*화면|위젯|워치|잠금\s*화면|배터리|업데이트/g],
];
export const TOPIC_NAMES = Object.fromEntries([...INQUIRY_TOPICS.map(([key, label]) => [key, label]), ["etc", "기타"]]);

/** Words that make a problem urgent: an alarm that did not ring, an app that will not open, money paid. */
// 1.39 수면 긴급 넷(사용자 승인 2026-11-07): 녹음·마이크가 안 꺼짐, 방해 금지가 안 풀림, 소리 클립이 안 지워짐(깨우는 알람은 URGENT에 있음).
export const SLEEP_URGENT = /(녹음|마이크|측정).{0,8}(계속|안\s*꺼|안\s*멈|켜져)|방해\s*금지.{0,8}(안\s*풀|계속|안\s*꺼)|(소리|녹음).{0,6}(파일|클립).{0,8}(남아|안\s*지워)/;
export const URGENT = /안\s*울|못\s*(들|일어)|놓쳤|늦게\s*울|지각|안\s*열|안\s*켜|튕|강제\s*종료|꺼져|데이터.{0,6}(사라|없어|날아)|사라졌|환불|결제.{0,6}(안|됐|두\s*번)/;

/** What the list and a thread show for sorting: topic, its label and how soon it needs an answer. */
export function triage(kind, text, info) {
  const t = String(text ?? "");
  let topic = "etc";
  let best = 0;
  for (const [key, , re] of INQUIRY_TOPICS) {
    const n = t.match(re)?.length ?? 0;
    if (n > best) [topic, best] = [key, n];
  }
  // What came with it says more than any word: 계산이 이상해요 sends the pay check, the alarm form its check.
  if (kind === "company" || info?.setup) topic = "company";
  else if (info?.pay) topic = "pay";
  // 1.32.1 [STAB-14] (사용자 2026-10-29 "멈출 때마다 알람이라고 뜨는데 실제로는 다른 원인 같아"): a 점검 기록 that starts
  // with 앱 멈춤 (the app puts it first for a stop's inquiry) is 앱 동작. 1.36 Y12 (PC 재검토): any other 점검 기록 says
  // nothing of the topic (every form can send it, and it always starts with 알람 상태), so a question with no word stays 기타
  // instead of 알람 — a settings-search question about 보상 came in as 문제 신고 · 알람.
  else if (/^\[앱 멈춤/.test(info?.diag ?? "")) topic = "app";
  // 1.36 [GRW-01] 스튜디오 요구는 꾸미기예요.
  if (kind === "studio") topic = "look";
  const urgency = ["idea", "company", "other", "studio"].includes(kind) ? "low" : URGENT.test(t) || SLEEP_URGENT.test(t) ? "urgent" : "normal";
  return { topic, topicName: TOPIC_NAMES[topic], urgency };
}

export async function inquiries(request, env, path) {
  const method = request.method;
  const device = deviceId(request.headers.get("x-device"));
  if (path === "/api/inquiries" && method === "POST") return ask(request, env);
  if (path === "/api/inquiries/public" && method === "GET") return publicList(env, new URL(request.url).searchParams, device, request.headers.get("x-hide"));
  const pub = path.match(/^\/api\/inquiries\/public\/([A-Za-z0-9]{22})$/);
  if (pub && method === "GET") return publicThread(env, pub[1], device, request.headers.get("x-hide"));
  const mark = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})\/(same|flag)$/);
  if (mark && method === "POST") return inquiryMark(request, env, mark[1], mark[2]);
  if (!device) return json({ error: "device", field: "device" }, 400);
  const dev = await deviceHash(device);
  if (path === "/api/inquiries" && method === "GET") {
    // 1.39 [LOOP-15] 앱이 자기 판(x-app "1.39.0 (53)")을 보내면: 이 폰이 이미 고친 판이면 묶인 문의에 바로 안내해요(단계적 출시 중에도).
    const app = request.headers.get("x-app");
    if (app) await phoneArrived(env, dev, app, Date.now()).catch(() => 0);
    // 2.0.0 [RVQ-16] 문의 답을 묻는 휴대폰은 아직 쓰는 중(알림 토큰의 정리 기한).
    await stillHere(env, dev, Date.now()).catch(() => {});
    const rows = await all(env, `SELECT i.*, (SELECT COUNT(*) FROM inquiry_photos p WHERE p.inquiry_id = i.id) AS photos
      FROM inquiries i WHERE i.device = ? ORDER BY i.updated_at DESC LIMIT 50`, dev);
    const out = [];
    for (const r of rows) out.push(await inquiryView(env, r, false, app));
    return json({ inquiries: out });
  }
  // 1.39 [LOOP-16] 해결됐나요: 고친 판 안내를 받은 문의에 '해결됐어요'(ok true)·'아직이에요'(false).
  const fixed = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})\/fixed$/);
  if (fixed && method === "POST") {
    const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", fixed[1]);
    if (!row || row.device !== dev) return json({ error: "not_found" }, 404);
    const r = await fixedAnswer(request, env, row, Date.now());
    return r ?? json(await inquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", row.id), false, request.headers.get("x-app")));
  }
  const rated = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})\/rating$/);
  if (rated && method === "POST") return inquiryRating(request, env, rated[1], dev);
  // [SRV-06] A photo sent with one's own question; someone else's reads as missing.
  const photo = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})\/photos\/([12])$/);
  if (photo && method === "GET") {
    const owner = await one(env, "SELECT device FROM inquiries WHERE id = ?", photo[1]);
    return owner && owner.device === dev ? photoFile(env, photo[1], Number(photo[2])) : json({ error: "not_found" }, 404);
  }
  const m = path.match(/^\/api\/inquiries\/([A-Za-z0-9]{22})(\/messages)?$/);
  if (!m) return json({ error: "not_found" }, 404);
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", m[1]);
  // Someone else's question reads as missing, so ids cannot be tried out.
  if (!row || row.device !== dev) return json({ error: "not_found" }, 404);
  if (!m[2] && method === "GET") return json(await inquiryView(env, row));
  if (!m[2] && method === "DELETE") {
    await batch(env, forgetInquiries(env, "id = ?", row.id));
    return new Response(null, { status: 204 });
  }
  if (m[2] && method === "POST") {
    const body = await readJson(request, INQUIRY.bytes);
    const text = inquiryText(body.text);
    const now = Date.now();
    { const limitHit = await overLimit(env, now, [["device", `inquiry_msg|${dev}`, INQUIRY.messagesPerHour, HOUR]]); if (limitHit) return limitHit; }
    const count = await one(env, "SELECT COUNT(*) AS n FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'user'", row.id);
    if (count.n >= INQUIRY.messages) return json({ error: "too_many", field: "text" }, 409);
    await batch(env, [
      stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)", row.id, text, now),
      // 1.36 [INQ-07] A thread that turns to paying or a refund becomes a 결제·환불 record from then on.
      stmt(env, "UPDATE inquiries SET status = 'open', updated_at = ?, billing_at = COALESCE(billing_at, ?) WHERE id = ?", now,
        BILLING_RECORD.test(text) ? now : null, row.id),
    ]);
    return json(await inquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", row.id)), 201);
  }
  return json({ error: "not_found" }, 404);
}

/** [INQ-03] 도움이 됐어요 (good: true) or 아쉬워요 (false) on the answers to one's own question; null takes it back. */
export async function inquiryRating(request, env, id, dev) {
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", id);
  if (!row || row.device !== dev) return json({ error: "not_found" }, 404);
  const body = await readJson(request, 1024);
  if (body.good !== true && body.good !== false && body.good !== null) refuse("good", "good");
  const answered = await one(env, "SELECT COUNT(*) AS n FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'admin'", id);
  if (!answered.n) return json({ error: "not_answered", field: "good" }, 409);
  await run(env, "UPDATE inquiries SET rating = ? WHERE id = ?", body.good === null ? null : body.good ? 1 : -1, id);
  return json(await inquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", id)));
}

/** A new question: what the user typed, and what they agreed to send with it. */
export async function ask(request, env) {
  // [SRV-06] Room for two photos as base64 next to the words.
  const body = await readJson(request, INQUIRY.bytes + PHOTO.max * Math.ceil((PHOTO.bytes * 4) / 3) + 1024);
  if (body.v !== 1) refuse("version", "v");
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  if (!INQUIRY.kinds.includes(body.kind)) refuse("kind", "kind");
  const text = inquiryText(body.text);
  const info = inquiryInfo(body.info);
  const now = Date.now();
  const net = await network(request, env, now);
  const dev = await deviceHash(device);
  const limitHit = await overLimit(env, now, [["device", `inquiry|${dev}`, INQUIRY.perDay, DAY],
    ...(await networkCheck(env, dev, net, "inquiry", INQUIRY.perNetworkDay, now))]);
  if (limitHit) return limitHit;
  const photos = await readPhotos(env, body.photos);
  const id = randomId(22);
  await batch(env, [
    // [SRV-06] Photos stay with the developer even on a public question.
    ...photos.list.map((data, i) => stmt(env, "INSERT INTO inquiry_photos (inquiry_id, n, data, bytes, at) VALUES (?, ?, ?, ?, ?)",
      id, i + 1, data.base64, data.bytes, now)),
    // Only an app that says so on its form sends public: true; a question from an older app stays the developer's.
    stmt(env, "INSERT INTO inquiries (id, device, kind, info, status, public, created_at, updated_at, billing_at) VALUES (?, ?, ?, ?, 'open', ?, ?, ?, ?)",
      id, dev, body.kind, JSON.stringify(info), body.public === true && !PRIVATE_KINDS.includes(body.kind) ? 1 : 0, now, now,
      BILLING_RECORD.test(text) ? now : null),
    stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)", id, text, now),
    // Threads with nothing new for half a year go, answers, notes and 개발사항 links and all (결제·환불: 3 years).
    ...dropInquiries(env, ...expiredInquiries(now)),
    stmt(env, "DELETE FROM counters WHERE expires < ?", now),
  ]);
  // [ADMIN-19] Admins who asked for it hear of a new question (a bug or a problem counts as urgent); nothing
  // happens without VAPID keys, and a push that fails never fails the question.
  // 1.39 [LOOP-19] 알람이 안 울림·앱이 안 열림·데이터 사라짐·결제·수면 긴급 넷을 말하는 문의는 '급함'으로 따로, 조용한 시간에도
  // (사고·법적 기능처럼). 알림에는 문의 글을 넣지 않고 무엇에 관한 것인지만. 결정함 맨 위에도 떠요.
  const tri = triage(body.kind, text, info);
  // 1.39 G7 발굴 1-4(G10이 받음): 알림만 보고 급한지 알게, 답을 기다리는 수와 2주 안의 같은 문의가 몇 건째인지(글은 넣지 않아요).
  const piled = await waitingNote(env, id, text, tri.topic);
  if (tri.urgency === "urgent") {
    await notifyAdmins(env, "urgent", { title: "급함 문의", body: `${tri.topicName ?? "문의"} · 알람·앱 실행·데이터·결제·수면 중 하나가 안 된다는 문의 1건${piled}`,
      url: "/admin/#/decide", urgent: true }, now, { ignoreQuiet: true });
  } else {
    await notifyAdmins(env, ["bug", "problem"].includes(body.kind) ? "urgent" : "inquiry", {
      title: "새 문의",
      body: `${({ bug: "문제 신고", problem: "문제 신고", question: "질문", idea: "건의·제안", company: "회사 방식", studio: "스튜디오 요구" })[body.kind] ?? "문의"} 1건${piled}`,
      // 1.39 [ADM3-38] Opens 운영 장부 › 오늘 할 것, where a new question lands (1.38 흐름), not the old 문의 list.
      url: "/admin/#/desk?view=today",
    }, now);
  }
  // 1.35 [INBOX-02] 자동 대응: answered at once when the owner turned it on for what this one matched (never fails it).
  await autoAnswer(env, id, now);
  // 1.36 [GRW-01][GRW-02] 스튜디오 요구는 비슷한 요구와 한 묶음으로.
  const studio = await needFromInquiry(env, id, dev, body.kind, text, info, now);
  const view = await inquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", id));
  // Photos the server could not take this time (switched off or full): the question went without them.
  return json({ ...view, ...(photos.skipped ? { photosSkipped: photos.skipped } : {}), ...(studio ? { studio } : {}) }, 201);
}

/** 새 문의 알림 뒤에 붙는 말: ' · 답 기다림 N건 · 같은 문의 M건째'. 읽지 못하면 빈 글(알림은 그대로 가요). */
export async function waitingNote(env, id, text, topic) {
  try {
    const rows = await all(env, WAITING_SQL);
    const waiting = rows.filter((r) => r.last === "user").length;
    const g = grams(text);
    const since = Date.now() - 14 * DAY;
    const same = rows.filter((r) => r.id !== id && r.created_at >= since && r.first && likeness(g, grams(r.first), triage(r.kind, r.first, null).topic === topic && topic !== "etc") >= SORTING.same).length;
    return `${waiting > 1 ? ` · 답 기다림 ${waiting}건` : ""}${same ? ` · 같은 문의 ${same + 1}건째` : ""}`;
  } catch {
    return "";
  }
}

/** What the user typed: up to 2000 characters, without an email, phone number or amount. */
export function inquiryText(v) {
  if (typeof v !== "string") refuse("text", "text");
  const text = v.normalize("NFC").replace(/\r\n?/g, "\n").trim();
  if (!text || [...text].length > INQUIRY.text) refuse("text", "text");
  const why = personal(text);
  if (why) refuse(why, "text");
  return text;
}

/**
 * What goes with a question, only the fields the app shows before sending. The alarm check is made by
 * the app, not typed: its times and shift names are what it is for, so only its length is checked.
 */
export function inquiryInfo(v) {
  if (v === undefined || v === null) return {};
  if (typeof v !== "object" || Array.isArray(v)) refuse("info", "info");
  const out = {};
  for (const [, key, longest] of INQUIRY_INFO) {
    const x = v[key];
    if (x === undefined || x === null || x === "") continue;
    if (typeof x !== "string" || [...x].length > longest) refuse("info", key);
    if (key !== "diag" && personal(x) === "email") refuse("info", key);
    if ((key === "pay" || key === "setup") && personal(x)) refuse("info", key);
    out[key] = x.replace(/[\u0000-\u0008\u000b-\u001f\u007f]/g, "");
  }
  return out;
}

export const messagesOf = (env, id) => all(env, "SELECT sender, text, by, at FROM inquiry_messages WHERE inquiry_id = ? ORDER BY id", id);

/** A question as the app sees it; [admin] adds what was sent with it and who answered. */
export async function inquiryView(env, row, admin = false, app = null) {
  const messages = await messagesOf(env, row.id);
  const view = {
    id: row.id,
    kind: row.kind,
    status: row.status,
    createdAt: iso(row.created_at),
    updatedAt: iso(row.updated_at),
    messages: messages.map((m) => ({ from: m.sender, text: m.text, at: iso(m.at), ...(admin && m.by ? { by: m.by } : {}) })),
    public: row.public === 1,
    same: row.same ?? 0,
    ...(row.hidden ? { hidden: { code: row.hidden, text: INQUIRY_HIDE[row.hidden] ?? "" } } : {}),
    rating: row.rating ?? 0,
    progress: await inquiryProgress(env, row, messages, app),
    // 1.39 [LOOP-16] 해결됐나요: 0 아직 안 물음·안 답함, 1 해결됐어요, -1 아직이에요. askFixed면 앱이 두 단추를 보여요.
    fixed: row.fixed ?? 0,
    askFixed: !row.fixed && (await fixedAsk(env, row.id)),
    // [SRV-06] How many photos came with it; each one is read on its own address.
    photos: row.photos ?? (await one(env, "SELECT COUNT(*) AS n FROM inquiry_photos WHERE inquiry_id = ?", row.id)).n,
  };
  if (!admin) return view;
  const info = readInfo(row.info);
  const context = {};
  for (const [label, key] of INQUIRY_INFO) if (info[key]) context[label] = info[key];
  const [reasons, notes, items] = (await batch(env, [
    stmt(env, `SELECT reason, COUNT(*) AS n FROM inquiry_marks WHERE inquiry_id = ? AND kind = 'flag'
      GROUP BY reason ORDER BY n DESC, reason`, row.id),
    stmt(env, "SELECT by, text, at FROM inquiry_notes WHERE inquiry_id = ? ORDER BY id", row.id),
    stmt(env, `SELECT d.id, d.title, d.status, (SELECT COUNT(*) FROM dev_links x JOIN inquiries xi ON xi.id = x.inquiry_id WHERE x.item_id = d.id) AS links FROM dev_links l
      JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL WHERE l.inquiry_id = ? ORDER BY l.at`, row.id),
  ])).map((r) => r.results);
  return {
    ...view, appVersion: info.app ?? null, context, waitingHours: row.status === "open" ? Math.floor((Date.now() - row.updated_at) / HOUR) : null, ...triage(row.kind, messages.find((m) => m.sender === "user")?.text, info),
    flags: row.flags ?? 0, kept: row.kept === 1, reasons: reasons.map((r) => ({ code: r.reason, text: FLAGS[r.reason] ?? r.reason, n: r.n })),
    // [ADMIN-12] Who is answering it and what admins told each other; [ADMIN-11] the 개발사항 it is in.
    assignee: row.assignee ? { id: row.assignee, name: row.assignee_name ?? "" } : null,
    notes: notes.map((n) => ({ by: n.by, text: n.text, at: iso(n.at) })),
    ...(env.actor && can(env.actor, "dev") ? { dev: items.map((d) => ({ id: d.id, title: d.title, status: d.status, links: d.links })) } : {}),
  };
}

/**
 * [INQ-02] Where a question stands, as the asker sees it: 접수 (received), 확인 중 (an admin took it),
 * 답변 (answered), 개발 목록 (in a 개발사항 being made) and 반영 (in a finished one, with its version). The
 * items themselves stay the admins': only the stage and the version go to the app.
 */
export async function inquiryProgress(env, row, messages, app = null) {
  const items = await all(env, `SELECT d.status, d.released_in, d.route, d.live_at FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL
    WHERE l.inquiry_id = ?`, row.id);
  // 1.37 [ADM2-02] '반영'은 고친 판이 Play에 나갔을 때만. 고쳤지만 아직 안 나갔으면 '만드는 중'.
  // 1.39 [LOOP-06] 업데이트 없이 고친 것(서버·원격 자료·스위치)은 실제로 닿은 때(live_at)부터, [LOOP-15] 이 폰이 이미 고친 판이면 그때부터.
  const shipped = items.some((d) => d.status === "done") ? await shippedVersion(env) : null;
  const mine = version(String(app ?? "").split(" ")[0]);
  const out = (d) => {
    if (d.live_at) return true;
    if (!d.released_in) return !["server", "remote", "flag"].includes(d.route ?? "");
    return isOut(d.released_in, shipped) || Boolean(mine && version(d.released_in) && compareVersions(mine, version(d.released_in)) >= 0);
  };
  const done = items.filter((d) => d.status === "done" && out(d));
  if (done.length) return { stage: "released", version: done.map((d) => d.released_in).find(Boolean) ?? null };
  if (items.some((d) => d.status === "done")) return { stage: "building" };
  if (items.some((d) => d.status === "todo" || d.status === "doing")) return { stage: items.some((d) => d.status === "doing") ? "building" : "planned" };
  const answered = messages.some((m) => m.sender === "admin");
  const last = messages[messages.length - 1];
  if (answered && last?.sender === "admin") return { stage: "answered" };
  if (row.assignee) return { stage: "checking" };
  return { stage: answered ? "checking" : "received" };
}

/** Who answered: the signed-in admin's name once there are admin accounts, else "admin". */
export function actorName(env) {
  const a = env.actor;
  const name = typeof a === "string" ? a : a && typeof a === "object" ? a.name : null;
  return typeof name === "string" && name ? name.slice(0, 40) : "admin";
}

/** /api/admin/inquiries: the list, one thread, an answer and open or closed. */
// ---- 모두의 문의 [ADMIN-02] ----
//
// A question sent from an app that says so on its form is public: anyone with the app reads it and the
// developer's answers, says 나도 그래요 or 신고s it. The writer is never named, and what went with the
// question (app, phone, alarm check, pay check) stays with the developer. A question sent before was
// promised to the developer only, so it stays private. Three 신고 hide it until the developer looks.

/** Why a public question is hidden, as its writer's app shows it. */
export const INQUIRY_HIDE = {
  abuse: "욕설이나 비방이 있어요",
  personal: "개인 정보가 있어요",
  spam: "광고나 도배예요",
  flags: "신고가 모여 개발자가 확인하고 있어요",
};

export const PUBLIC_PAGE = 30;
export const PUBLIC_CACHE_MS = 60 * 1000;
export const publicRows = new WeakMap();

/** What anyone sees of a public question in the list; [author] the writer sealed for 이 사람 글 안 보기 (COMM-11). */
export function publicItem(r, me, sameByMe, author) {
  return {
    id: r.id,
    kind: r.kind,
    status: r.status,
    preview: [...(r.first ?? "")].slice(0, 160).join(""),
    answers: r.answers,
    same: r.same,
    sameByMe: sameByMe.has(r.id),
    mine: r.device === me,
    ...(author ? { author } : {}),
    createdAt: iso(r.created_at),
    updatedAt: iso(r.updated_at),
    ...triage(r.kind, r.first, readInfo(r.info)),
  };
}

/** 모두의 문의, newest activity first or most 나도 그래요 first, one topic or all, a page at a time. */
export async function publicList(env, params, device, hide) {
  const me = device ? await deviceHash(device) : null;
  const topic = Object.hasOwn(TOPIC_NAMES, params.get("topic") ?? "") ? params.get("topic") : null;
  const order = params.get("sort") === "same" ? "i.same DESC, i.updated_at DESC" : "i.updated_at DESC";
  const offset = Math.max(0, Math.min(Number.parseInt(params.get("after") ?? "0", 10) || 0, 1000));
  // [SRV-08] The 500 rows behind 모두의 문의 are read once a minute per Worker and order, not on every look.
  const cacheKey = `${order}`;
  const cached = publicRows.get(env.DB ?? env)?.get(cacheKey);
  const rows = cached && cached.until > Date.now() ? cached.rows : await all(env, `SELECT i.id, i.kind, i.status, i.info, i.same, i.device, i.created_at, i.updated_at,
      (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first,
      (SELECT COUNT(*) FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin') AS answers
    FROM inquiries i WHERE i.public = 1 AND i.hidden IS NULL ORDER BY ${order} LIMIT 500`);
  if (!cached || cached.rows !== rows) {
    if (!publicRows.has(env.DB ?? env)) publicRows.set(env.DB ?? env, new Map());
    publicRows.get(env.DB ?? env).set(cacheKey, { rows, until: Date.now() + PUBLIC_CACHE_MS });
  }
  const sameByMe = new Set(me ? (await all(env, "SELECT inquiry_id FROM inquiry_marks WHERE device = ? AND kind = 'same'", me))
    .map((r) => r.inquiry_id) : []);
  // [COMM-11] 이 사람 글 안 보기 in 모두의 문의 too, the same way as the plaza: the server keeps no list.
  const key = await authorKey(env);
  const hidden = await hiddenAuthors(key, hide);
  const kept = rows.filter((r) => r.device === me || !hidden.has(r.device));
  const hiddenByYou = rows.length - kept.length;
  const items = await Promise.all(kept.map(async (r) => publicItem(r, me, sameByMe, r.device === me ? null : await authorToken(key, r.device))));
  const topics = {};
  for (const i of items) topics[i.topic] = (topics[i.topic] ?? 0) + 1;
  const shown = items.filter((i) => !topic || i.topic === topic);
  const page = shown.slice(offset, offset + PUBLIC_PAGE);
  return json({ topics, topicNames: TOPIC_NAMES, inquiries: page, next: offset + PUBLIC_PAGE < shown.length ? offset + PUBLIC_PAGE : null,
    ...(hiddenByYou ? { hiddenByYou } : {}) });
}

/** One public question with every message; its writer also sees it while hidden, with why. */
export async function publicThread(env, id, device, hide) {
  const me = device ? await deviceHash(device) : null;
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ? AND public = 1", id);
  if (!row || (row.hidden && row.device !== me)) return json({ error: "not_found" }, 404);
  const key = await authorKey(env);
  if (row.device !== me && (await hiddenAuthors(key, hide)).has(row.device)) return json({ error: "hidden_by_you" }, 404);
  const messages = await messagesOf(env, row.id);
  const marks = me ? await all(env, "SELECT kind FROM inquiry_marks WHERE inquiry_id = ? AND device = ?", row.id, me) : [];
  return json({
    id: row.id,
    kind: row.kind,
    status: row.status,
    same: row.same,
    sameByMe: marks.some((m) => m.kind === "same"),
    flaggedByMe: marks.some((m) => m.kind === "flag"),
    mine: row.device === me,
    ...(row.device === me ? {} : { author: await authorToken(key, row.device) }),
    createdAt: iso(row.created_at),
    updatedAt: iso(row.updated_at),
    messages: messages.map((m) => ({ from: m.sender === "user" ? "user" : "admin", text: m.text, at: iso(m.at) })),
    ...triage(row.kind, messages.find((m) => m.sender === "user")?.text, readInfo(row.info)),
    ...(row.hidden ? { hidden: { code: row.hidden, text: INQUIRY_HIDE[row.hidden] ?? "" } } : {}),
  });
}

/** 나도 그래요 (on: false takes it back) or 신고, once per install; not on one's own question. */
export async function inquiryMark(request, env, id, kind) {
  const body = await readJson(request, 1024);
  const device = deviceId(body.device);
  if (!device) refuse("device", "device");
  const now = Date.now();
  const net = await network(request, env, now);
  // R131 1.31 [LIM-01] the phone's own hour first, then its network's (many phones).
  const limitHit = await overLimit(env, now, [["device", `vote|${await deviceHash(device)}|${Math.floor(now / HOUR)}`, LIMITS.votesPerHour, HOUR],
    ["network", `vote|${net.now}`, LIMITS.votesPerNetworkHour, HOUR]]);
  if (limitHit) return limitHit;
  const dev = await deviceHash(device);
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ? AND public = 1 AND hidden IS NULL", id);
  if (!row) return json({ error: "not_found" }, 404);
  if (row.device === dev) return json({ error: "own" }, 400);
  if (kind === "same") {
    await batch(env, [
      body.on === false
        ? stmt(env, "DELETE FROM inquiry_marks WHERE inquiry_id = ? AND device = ? AND kind = 'same'", id, dev)
        : stmt(env, "INSERT OR IGNORE INTO inquiry_marks (inquiry_id, device, kind, reason, at) VALUES (?, ?, 'same', NULL, ?)", id, dev, now),
      stmt(env, "UPDATE inquiries SET same = (SELECT COUNT(*) FROM inquiry_marks WHERE inquiry_id = ?1 AND kind = 'same') WHERE id = ?1", id),
    ]);
    const after = await one(env, "SELECT same FROM inquiries WHERE id = ?", id);
    return json({ same: after.same, sameByMe: body.on !== false });
  }
  const reason = Object.hasOwn(FLAGS, body.reason) && body.reason !== "false" ? body.reason : "abuse";
  const added = await run(env, "INSERT OR IGNORE INTO inquiry_marks (inquiry_id, device, kind, reason, at) VALUES (?, ?, 'flag', ?, ?)", id, dev, reason, now);
  if (added.meta.changes) {
    await run(env, `UPDATE inquiries SET flags = flags + 1,
        hidden = CASE WHEN kept = 0 AND flags + 1 >= ?2 THEN 'flags' ELSE hidden END WHERE id = ?1`, id, LIMITS.flagsToHide);
    const after = await one(env, "SELECT hidden FROM inquiries WHERE id = ?", id);
    if (after.hidden) {
      await audit(env, now, "inquiry_autohide", id);
      await notifyAdmins(env, "flagged", { title: "신고로 가려진 문의", body: "신고가 쌓여 모두의 문의 하나가 가려졌어요", url: "/admin/#/inquiries" }, now);
    }
  }
  return json({ ok: true });
}

export async function adminInquiries(request, env, url, path, now) {
  const method = request.method;
  if (path === "/inquiries" && method === "GET") return json(await inquiryList(env, url.searchParams, env.actor?.id ?? ""));
  if (path === "/inquiries/bulk" && method === "POST") return inquiryBulk(request, env, now);
  // AT 1.29 [ADMCP-01][ADMCP-03] 문의 복사: read whole (seeing is enough), then 복사함 (changing questions).
  if (path === "/inquiries/export" && method === "GET") return json(await inquiryExport(env, url.searchParams));
  if (path === "/inquiries/copied" && method === "POST") return inquiryCopied(request, env, now);
  // [SRV-06] A photo sent with a question, for an admin who may read questions.
  const photo = path.match(/^\/inquiries\/([A-Za-z0-9]{22})\/photos\/([12])$/);
  if (photo && method === "GET") return photoFile(env, photo[1], Number(photo[2]));
  // 1.38 [DESK-09] 보류 중인 Claude 답 취소·고치기.
  const held = path.match(/^\/inquiries\/([A-Za-z0-9]{22})\/held\/(\d{1,9})$/);
  if (held && method === "POST") {
    const body = await readJson(request, INQUIRY.bytes);
    const gone = await heldChange(env, held[1], Number(held[2]), body, now);
    if (gone) return gone;
    const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", held[1]);
    return row ? json(await adminInquiryView(env, row)) : json({ error: "not_found" }, 404);
  }
  const m = path.match(/^\/inquiries\/([A-Za-z0-9]{22})(\/reply|\/status|\/visibility|\/assign|\/notes|\/typing|\/billing)?$/);
  if (!m) return json({ error: "not_found" }, 404);
  const row = await one(env, "SELECT * FROM inquiries WHERE id = ?", m[1]);
  if (!row) return json({ error: "not_found" }, 404);
  if (!m[2] && method === "GET") return json(await adminInquiryView(env, row));
  if (method !== "POST" || !m[2]) return json({ error: "not_found" }, 404);
  const body = await readJson(request, INQUIRY.bytes);
  if (m[2] === "/typing") {
    // [ADMIN-16] 답 쓰는 중: lasts 30 seconds unless sent again, so a closed tab stops showing by itself.
    const key = `typing|${row.id}|${env.actor.id}|${actorName(env).replace(/\|/g, "")}`;
    if (body.on === false) await run(env, "DELETE FROM counters WHERE key = ?", key);
    else await run(env, "INSERT OR REPLACE INTO counters (key, count, expires) VALUES (?, 1, ?)", key, now + 30 * 1000);
    return json({ ok: true });
  }
  if (m[2] === "/reply") {
    // 2.0.0 [RVA-51] 문에 걸려 멈췄으면 화면이 '보냈어요' 대신 까닭을 알려요.
    if ((await replyTo(env, row, body, now)) === "held") {
      const view = await adminInquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", row.id));
      return json({ ...view, gated: view.held?.findLast?.((h) => h.person)?.why ?? { name: "사람이 먼저 볼 답" } });
    }
  } else if (m[2] === "/assign") {
    // [ADMIN-12] 내가 맡기, or let it go (to: null); one tap, so two admins do not answer the same question.
    if (body.to !== "me" && body.to !== null) refuse("to", "to");
    // Letting go is for whoever holds it, or the owner; nobody releases someone else's question.
    if (!mayAssign(env.actor, row, body.to)) throw new Refuse("no_permission", "assignee", 403);
    await run(env, "UPDATE inquiries SET assignee = ?, assignee_name = ? WHERE id = ?", body.to ? env.actor.id : null, body.to ? actorName(env) : null, row.id);
    await audit(env, now, "inquiry_assign", row.id, body.to ? actorName(env) : "");
  } else if (m[2] === "/notes") {
    // [ADMIN-12] A note for the other admins: never in the app's view of the question.
    const text = plainText(body.text, DEV.note, "text", true);
    await run(env, "INSERT INTO inquiry_notes (inquiry_id, by, text, at) VALUES (?, ?, ?, ?)", row.id, actorName(env), text, now);
  } else if (m[2] === "/billing") {
    // 1.36 [INQ-07] 결제·환불 기록: kept three years from its last word. Set by hand when the words missed it, or
    // cleared when they matched by chance; the activity log keeps who did which.
    if (typeof body.on !== "boolean") refuse("on", "on");
    await audit(env, now, "inquiry_billing", row.id, body.on ? "on" : "off");
    // 2.0.0 [RVA-41] 검토 I24a-05: 말 때문에 잘못 잡힌 것을 '아니에요'로 빼면 그 말을 남겨 판정 식을 다듬는 근거로 써요.
    if (!body.on && body.miss === true && row.billing_at) {
      const words = (await all(env, "SELECT text FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'user'", row.id)).map((m) => m.text).join(" ");
      await audit(env, now, "ask_miss", row.id, `billing '${billingWordOf(words) ?? ""}'`);
    }
    // Its asker already deleted it, and it is no record the law keeps: it goes now, as they asked.
    if (!body.on && String(row.device).startsWith("gone")) {
      await batch(env, dropInquiries(env, "id = ?", row.id));
      return json({ deleted: true });
    }
    await run(env, "UPDATE inquiries SET billing_at = ? WHERE id = ?", body.on ? row.billing_at ?? now : null, row.id);
  } else if (m[2] === "/visibility") {
    // 모두의 문의: hide (with a reason the writer's app shows) or show again; either way 신고 no longer hides it.
    if (!row.public) refuse("public", "action");
    if (body.action === "hide") {
      const reason = Object.hasOwn(INQUIRY_HIDE, body.reason) && body.reason !== "flags" ? body.reason : "abuse";
      await run(env, "UPDATE inquiries SET hidden = ?, kept = 1 WHERE id = ?", reason, row.id);
      await audit(env, now, "inquiry_hide", row.id, reason);
    } else if (body.action === "show") {
      await batch(env, [
        stmt(env, "UPDATE inquiries SET hidden = NULL, kept = 1, flags = 0 WHERE id = ?", row.id),
        stmt(env, "DELETE FROM inquiry_marks WHERE inquiry_id = ? AND kind = 'flag'", row.id),
      ]);
      await audit(env, now, "inquiry_show", row.id);
    } else {
      refuse("action", "action");
    }
  } else {
    if (!["open", "closed"].includes(body.status)) refuse("status", "status");
    // Opening or closing is the developer's bookkeeping; it does not keep a thread from its 180 days.
    await run(env, "UPDATE inquiries SET status = ? WHERE id = ?", body.status, row.id);
    await audit(env, now, "inquiry_status", row.id, body.status);
  }
  return json(await adminInquiryView(env, await one(env, "SELECT * FROM inquiries WHERE id = ?", row.id)));
}

export const mayAssign = (actor, row, to) => to !== null || !row.assignee || row.assignee === actor.id || actor.owner;

/**
 * An admin's answer to one question. [ADMIN-15] The same words from the same admin within a minute are
 * one answer, so a double tap or a resend after a slow network does not reach the asker twice.
 * [ADMIN-16] An answer sent from a saved one counts as a use of it.
 */
export async function replyTo(env, row, body, now, audited = true) {
  if (typeof body.text !== "string" || !body.text.trim() || [...body.text.trim()].length > INQUIRY.text) refuse("text", "text");
  const text = body.text.normalize("NFC").replace(/\r\n?/g, "\n").trim();
  // 1.38 [DESK-09] Claude 열쇠의 답은 바로 가지 않고 10분 '보류 중'이에요(운영 장부 설계 5-4). 사람이 그 사이 취소·고쳐요.
  if (env.actor?.key) return holdReply(env, row, text, body, now);
  // 2.0.0 [RVA-51] 검토 I24b-03: 사람이 쓴 답도 같은 문을 지나요(걸리면 '사람이 보내야 해요' 보류 답과 까닭, '지금 보내기'로 가요).
  const why = await sendGate(env, row.id, text);
  if (why) return holdForPerson(env, row.id, text, why, now, { close: body.close === true, template: Number.isInteger(body.template) ? body.template : null });
  const status = body.close === true ? "closed" : "answered";
  const again = await one(env, `SELECT id FROM inquiry_messages WHERE inquiry_id = ? AND sender = 'admin' AND text = ? AND by = ? AND at > ?
    ORDER BY id DESC LIMIT 1`, row.id, text, actorName(env), now - 60 * 1000);
  if (again) return false;
  await batch(env, [
    stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, ?, ?)", row.id, text, actorName(env), now),
    // [ADMIN-12] Whoever answers a question nobody took has taken it.
    stmt(env, `UPDATE inquiries SET status = ?, updated_at = ?, assignee = COALESCE(assignee, ?),
      assignee_name = CASE WHEN assignee IS NULL THEN ? ELSE assignee_name END WHERE id = ?`, status, now, env.actor?.id ?? null, actorName(env), row.id),
    ...(Number.isInteger(body.template) ? [stmt(env, "UPDATE reply_templates SET uses = uses + 1 WHERE id = ?", body.template)] : []),
    // Sent, so no longer 답 쓰는 중.
    stmt(env, "DELETE FROM counters WHERE key LIKE ?", `typing|${row.id}|${env.actor?.id ?? ""}|%`),
  ]);
  if (audited) await audit(env, now, "inquiry_reply", row.id, status);
  // 1.36 [PUSH-01] the asker's phone hears at once (no words go to Google).
  await fcmTell(env, [row.id], now);
  return true;
}

/**
 * 2.0.0 [RVA-51] 검토 I24b-03(ADMX-06·07): 보내는 문 하나. 사람이 쓴 답·여러 문의에 같은 답·개발사항 답·정리함·결과 붙여 넣기·Claude 열쇠·자동 정리가
 * 모두 이 검사를 지나요(전에는 길마다 검사가 달랐어요: 열쇠와 자동만 보고 사람이 쓴 답·개발사항 답은 안 봤어요).
 * 걸리는 것: 답 글의 AI·자동 답 표현이나 보안 정보(누가 쓰든), 그리고 사람이 먼저 볼 문의(환불·법·개인정보·크게 화남·약관·회사 방식·보안·사람이냐).
 * 최고 관리자가 직접 쓴 답은 그 확인이 된 것이라 문의 쪽은 지나가요. 걸리면 { key?, name, word?, reply? }(heldWhy와 같은 모양), 아니면 null.
 */
export async function sendGate(env, id, text, { typed = true } = {}) {
  const risk = replyRiskOf(text);
  if (risk) return { name: risk, reply: true };
  const a = env.actor;
  // [typed]: 최고 관리자가 그 답을 직접 쓰거나 읽고 고친 길(문의 화면·정리함). 붙여 넣은 Claude 결과는 아니에요.
  if (typed && a && typeof a === "object" && a.owner && !a.key && !a.auto) return null;
  const ask = await needsPersonWhy(env, id);
  return ask ? { key: ask.key, name: ask.name, ...(ask.word ? { word: ask.word } : {}) } : null;
}

/**
 * 2.0.0 [RVA-51] 문에 걸린 답은 어느 길에서 왔든 같은 모양으로 멈춰요: 문의 화면의 '사람이 보내야 해요' 보류 답과 까닭(heldWhy),
 * 오늘의 '사람이 보낼 답' 알림. 문의 권한이 있는 관리자가 읽고 '지금 보내기'·고치기·보내지 않기를 눌러요. 같은 답이 이미 멈춰 있으면 다시 넣지 않아요.
 * "held"를 돌려줘요(보낸 것과 따로 세요).
 */
export async function holdForPerson(env, id, text, why, now, { close = false, template = null, pile = "", by = actorName(env) } = {}) {
  if (await one(env, "SELECT id FROM held_replies WHERE inquiry_id = ? AND text = ?", id, text)) return "held";
  if (!(await one(env, "SELECT id FROM inquiries WHERE id = ?", id))) return false;
  await run(env, "INSERT INTO held_replies (inquiry_id, text, by, actor_id, close, template, at, until, pile) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, text,
    by, (typeof env.actor === "object" && env.actor?.id) || null, close ? 1 : 0, template, now, HELD_FOR_PERSON, pile);
  await audit(env, now, "inquiry_reply_held", id, `사람이 보내기(${why.name})`);
  return "held";
}

/**
 * 1.38 [DESK-09] A key's answer waits [DESK.hold] in held_replies: not in the thread, so the app sees nothing and no
 * notice goes. A day holds at most [DESK.claudeAnswersDay] (KST day); past it the key is told to stop and report.
 */
export async function holdReply(env, row, text, body, now) {
  const again = await one(env, "SELECT id FROM held_replies WHERE inquiry_id = ? AND text = ?", row.id, text);
  if (again) return false;
  const since = Date.parse(`${kstDay(now)}T00:00:00+09:00`);
  const n = (await one(env, "SELECT COUNT(*) AS n FROM held_replies WHERE at >= ?", since)).n
    + (await one(env, "SELECT COUNT(*) AS n FROM audit WHERE action = 'inquiry_reply_sent' AND at >= ?", since)).n;
  if (n >= DESK.claudeAnswersDay) throw new Refuse("answer_limit", "text", 429);
  // 1.39 [LOOP-12] 믿을수록 더 맡기기: 이 답의 종류(정리함 더미)를 최고 관리자가 '바로 보내기'로 켰으면 보류 없이 가요.
  const pile = typeof body.pile === "string" && Object.hasOwn(LOOP.piles, body.pile) ? body.pile : "";
  const kind = pile ? (await one(env, "SELECT kind FROM inquiries WHERE id = ?", row.id))?.kind : null;
  // 1.39 [LOOP-37] 사람이 봐야 할 문의(최고 관리자 확인과 같은 기준)에 Claude가 적은 답은 시간이 지나도 저절로 가지 않아요:
  // 문의 권한이 있는 관리자가 '지금 보내기'(또는 고치기·보내지 않기)를 눌러야 가요.
  // 1.39 [LOOP-38] 답 글에 AI·자동 답 표현이나 보안 정보가 있어도 사람이 보고 보내요.
  // 2.0.0 [RVA-51] 같은 문(sendGate): 걸리는 기준이 사람이 쓴 답·자동 답과 하나예요.
  const person = (await sendGate(env, row.id, text))?.name ?? null;
  const instant = !person && await instantOk(env, pile, kind, text);
  await run(env, "INSERT INTO held_replies (inquiry_id, text, by, actor_id, close, template, at, until, pile) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", row.id, text,
    actorName(env), env.actor?.id ?? null, body.close === true ? 1 : 0, Number.isInteger(body.template) ? body.template : null, now, instant ? now : person ? HELD_FOR_PERSON : now + DESK.hold, pile);
  await audit(env, now, "inquiry_reply_held", row.id, instant ? `바로 보내기(${LOOP.piles[pile]})` : person ? `사람이 보내기(${person})` : `${Math.round(DESK.hold / 60000)}분 보류`);
  if (instant) await releaseHeld(env, now);
  return true;
}

/** 1.38 [DESK-09] The answers held for [id], for the admin view: each with when it goes. */
/**
 * 2.0.0 [RVA-41] 결제·환불 기록이 된 까닭: 마지막으로 손으로 켠 기록이 있으면 관리자, 아니면 보낸 분 글에서 걸린 말.
 * 그 말이 틀렸으면 관리자가 '아니에요'로 빼고, 그 기록(ask_miss)이 판정 식을 다듬는 근거가 돼요(I24c-03).
 */
function billingWhy(events, messages) {
  const hand = [...events].reverse().find((e) => e.action === "inquiry_billing");
  if (hand?.detail === "on") return { by: "admin", who: plainActor(hand.actor) };
  const words = (messages ?? []).filter((m) => m.from === "user").map((m) => m.text).join(" ");
  return { by: "words", word: billingWordOf(words) };
}

/** 2.0.0 [RVA-41] 사람이 보내야 하는 보류 답마다 까닭: 답 글의 표현(AI·보안)이 먼저, 아니면 문의 글에서 걸린 말. */
async function heldWhy(env, row, held) {
  if (!held.some((h) => h.person)) return held;
  const ask = await needsPersonWhy(env, row.id);
  return held.map((h) => {
    if (!h.person) return h;
    const risk = replyRiskOf(h.text);
    return { ...h, why: risk ? { name: risk, reply: true } : ask ? { name: ask.name, word: ask.word, key: ask.key } : null };
  });
}

export async function heldOf(env, id) {
  const rows = await all(env, "SELECT id, text, by, close, until FROM held_replies WHERE inquiry_id = ? ORDER BY id", id).catch(() => []);
  // 1.39 [LOOP-37] person: 사람이 봐야 할 문의라 누가 보내기를 눌러야 가요.
  return rows.map((r) => ({ id: r.id, text: r.text, by: r.by, close: r.close === 1, person: r.until >= HELD_FOR_PERSON, until: r.until >= HELD_FOR_PERSON ? null : iso(r.until) }));
}

/** 1.38 [DESK-09] Held answers whose time came go into their threads as answers, and the askers hear at once. */
export async function releaseHeld(env, now) {
  const rows = await all(env, "SELECT * FROM held_replies WHERE until <= ? ORDER BY id LIMIT 50", now).catch(() => []);
  const told = [];
  for (const h of rows) {
    const row = await one(env, "SELECT id FROM inquiries WHERE id = ?", h.inquiry_id);
    const out = [stmt(env, "DELETE FROM held_replies WHERE id = ?", h.id)];
    if (row) {
      out.push(stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, ?, ?)", h.inquiry_id, h.text, h.by, now),
        stmt(env, `UPDATE inquiries SET status = ?, updated_at = ?, assignee = COALESCE(assignee, ?),
          assignee_name = CASE WHEN assignee IS NULL THEN ? ELSE assignee_name END WHERE id = ?`, h.close ? "closed" : "answered", now, h.actor_id, h.by, h.inquiry_id),
        ...(h.template ? [stmt(env, "UPDATE reply_templates SET uses = uses + 1 WHERE id = ?", h.template)] : []),
        stmt(env, "INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, 'inquiry_reply_sent', ?, '', ?, ?)", now, h.inquiry_id, h.by, h.actor_id ?? ""));
      told.push(h.inquiry_id);
    }
    await batch(env, out);
    // 1.39 [LOOP-12] 종류마다 그대로 나갔는지·고쳐서 나갔는지(바로 보내기는 따로 셈).
    if (row) await heldLog(env, h.pile, h.until <= h.at ? "instant" : h.edited ? "edited" : "sent", now);
  }
  if (told.length) await fcmTell(env, [...new Set(told)], now);
  return told.length;
}

/** 1.39 [LOOP-37] 사람이 보내야 하는 보류 답의 '때'(저절로 오지 않는 먼 날). */
export const HELD_FOR_PERSON = Date.parse("9999-01-01T00:00:00Z");

/** 1.38 [DESK-09] A person (never the key) takes a held answer back, or changes its words; its time stays. */
export async function heldChange(env, inquiryId, heldId, body, now) {
  if (env.actor?.key) throw new Refuse("no_permission", "held", 403);
  const h = await one(env, "SELECT * FROM held_replies WHERE id = ? AND inquiry_id = ?", heldId, inquiryId);
  if (!h) return json({ error: "gone" }, 404);
  // 2.0.0 [RVA-41] 검토 I24a-05: '판정이 틀렸어요'는 기록만 남겨요(답은 그대로 사람이 보내요). 판정 식을 다듬는 근거(I24c-03).
  if (body.miss === true) {
    if (!can(env.actor, "inquiries", "edit")) throw new Refuse("no_permission", "held", 403);
    const why = await needsPersonWhy(env, inquiryId);
    await audit(env, now, "ask_miss", inquiryId, why ? `owner:${why.key} '${why.word ?? ""}'` : "owner");
    return null;
  }
  // 1.39 [LOOP-37] 사람이 보내야 하는 답(또는 기다리는 답)을 지금 보내요.
  if (body.send === true) {
    if (!can(env.actor, "inquiries", "edit")) throw new Refuse("no_permission", "held", 403);
    await run(env, "UPDATE held_replies SET until = ? WHERE id = ?", now, h.id);
    await audit(env, now, "inquiry_reply_approve", inquiryId, h.by);
    await releaseHeld(env, now);
    return null;
  }
  if (body.cancel === true) {
    await run(env, "DELETE FROM held_replies WHERE id = ?", h.id);
    await audit(env, now, "inquiry_reply_cancel", inquiryId, h.by);
    await heldLog(env, h.pile, "cancelled", now);
  } else {
    if (typeof body.text !== "string" || !body.text.trim() || [...body.text.trim()].length > INQUIRY.text) refuse("text", "text");
    await run(env, "UPDATE held_replies SET text = ?, edited = 1 WHERE id = ?", body.text.normalize("NFC").replace(/\r\n?/g, "\n").trim(), h.id);
    await audit(env, now, "inquiry_reply_edit", inquiryId, h.by);
  }
  return null;
}

/** [ADMIN-16] What can be done to many questions at once, and what it needs besides 문의 고치기. */
export const BULK = { assign: null, unassign: null, close: null, open: null, reply: null, link: "dev" };

/**
 * [ADMIN-16] Up to 100 questions at once: take them, let them go, close, open again, send each the same
 * answer, or link them to a 개발사항. What one admin may not do to one of them (let go of someone else's)
 * is skipped and counted, the rest goes through, and the activity log gets one line for the lot.
 */
export async function inquiryBulk(request, env, now) {
  const body = await readJson(request, INQUIRY.bytes + 4096);
  if (!Object.hasOwn(BULK, body.action)) refuse("action", "action");
  const ids = Array.isArray(body.ids) ? [...new Set(body.ids)] : [];
  if (!ids.length || ids.length > DEV.bulk || ids.some((id) => typeof id !== "string" || !/^[A-Za-z0-9]{22}$/.test(id))) refuse("ids", "ids");
  if (BULK[body.action] && !can(env.actor, BULK[body.action], "edit")) return json({ error: "no_permission", area: BULK[body.action] }, 403);
  let item = null;
  if (body.action === "link") {
    item = typeof body.item === "string" ? await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL", body.item) : null;
    if (!item) return json({ error: "not_found" }, 404);
  }
  const rows = await all(env, `SELECT * FROM inquiries WHERE id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  let done = 0;
  let held = 0;
  let skipped = ids.length - rows.length;
  const a = env.actor;
  for (const row of rows) {
    if (body.action === "assign" || body.action === "unassign") {
      const to = body.action === "assign" ? "me" : null;
      if (!mayAssign(a, row, to)) {
        skipped++;
        continue;
      }
      await run(env, "UPDATE inquiries SET assignee = ?, assignee_name = ? WHERE id = ?", to ? a.id : null, to ? actorName(env) : null, row.id);
    } else if (body.action === "close" || body.action === "open") {
      await run(env, "UPDATE inquiries SET status = ? WHERE id = ?", body.action === "close" ? "closed" : "open", row.id);
    } else if (body.action === "reply") {
      const r = await replyTo(env, row, { text: body.text, close: body.close === true, template: body.template }, now, false);
      if (!r) {
        skipped++;
        continue;
      }
      // [RVA-51] 문에 걸려 멈춘 것은 따로 세요.
      if (r === "held") {
        held++;
        continue;
      }
    } else {
      const r = await run(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", item.id, row.id, actorName(env), now);
      if (r.meta.changes) await devEvent(env, item.id, now, "link", { inquiry: row.id }).run();
    }
    done++;
  }
  if (item) await run(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, item.id);
  await audit(env, now, "inquiry_bulk", item ? item.title : "", `${body.action} ${done}${skipped ? ` (${skipped} 건너뜀)` : ""}${held ? ` (사람이 보내기 ${held})` : ""}`);
  return json({ done, skipped, ...(held ? { held } : {}) });
}

/**
 * [ADMIN-16][ADMIN-17] One question for an admin: what inquiryView gives, with the saved answers (its
 * topic's first, then the most used) when this admin may answer, who else is writing to it, and what
 * admins did to it, for the timeline.
 */
/**
 * [ADMUX-07] The question's audit lines (in the order written) with its 개발사항 links put in by time. Two
 * from the same millisecond cannot be told apart by time, so a link goes after an assign or a reply of
 * that moment and before a status change or a hide (a question is linked, then closed).
 */
export function timeline(events, links) {
  const late = new Set(["inquiry_status", "inquiry_hide", "inquiry_show", "inquiry_billing"]);
  const out = [];
  const queue = [...links].sort((x, y) => x.at - y.at);
  for (const e of events) {
    while (queue.length && (queue[0].at < e.at || (queue[0].at === e.at && late.has(e.action)))) {
      const l = queue.shift();
      out.push({ at: iso(l.at), action: "dev_link", detail: l.title, item: l.id, by: l.by });
    }
    out.push({ at: iso(e.at), action: e.action, detail: e.detail, by: plainActor(e.actor) });
  }
  for (const l of queue) out.push({ at: iso(l.at), action: "dev_link", detail: l.title, item: l.id, by: l.by });
  return out;
}

export async function adminInquiryView(env, row) {
  const view = await inquiryView(env, row, true);
  const now = Date.now();
  const [events, links, typing] = (await batch(env, [
    stmt(env, `SELECT at, action, detail, actor FROM audit WHERE target = ? AND action IN ('inquiry_assign', 'inquiry_status', 'inquiry_reply',
      'inquiry_hide', 'inquiry_show', 'inquiry_billing', 'inquiry_reply_held', 'inquiry_reply_sent', 'inquiry_reply_cancel', 'ask_miss') ORDER BY id LIMIT 200`, row.id),
    stmt(env, "SELECT l.at, l.by, d.id, d.title FROM dev_links l JOIN dev_items d ON d.id = l.item_id WHERE l.inquiry_id = ? ORDER BY l.at", row.id),
    stmt(env, "SELECT key FROM counters WHERE key LIKE ? AND expires > ?", `typing|${row.id}|%`, now),
  ])).map((r) => r.results);
  const out = {
    ...view,
    // 1.38 [DESK-09] Claude 답 중 아직 가지 않은 것. 2.0.0 [RVA-41] 사람이 보내야 하는 답에는 왜 그런지(걸린 말)를 같이.
    held: await heldWhy(env, row, await heldOf(env, row.id)),
    events: timeline(events, links),
    typing: typingByInquiry(typing, env.actor?.id ?? "")[row.id] ?? [],
    // AT 1.29 [ADMCP-03][ADMCP-06] 복사함, the phone's short mark and how many other questions it sent.
    ...copyFields(row),
    sameSender: (await one(env, "SELECT COUNT(*) AS n FROM inquiries WHERE device = ? AND id != ?", row.device, row.id)).n,
    // 1.36 [INQ-07] A 결제·환불 record: since when, until when it is kept, and whether the asker deleted it.
    // 2.0.0 [RVA-41] 검토 U24-05·I24a-05: 왜 결제·환불 기록인지(관리자가 정했는지, 어떤 말 때문인지).
    billing: row.billing_at ? { since: iso(row.billing_at), keepUntil: iso(row.updated_at + INQUIRY.billingKeepMs), ...billingWhy(events, view.messages) } : null,
    leftByAsker: String(row.device).startsWith("gone"),
    // 1.39 [CARE-10] 마을 경고·쉬기에 대한 해명이면: 무엇에 대한 것인지와 결정.
    appeal: await careAppealOf({ one, all, run, batch, stmt, randomId }, env, row.id, now),
  };
  if (view.waitingHours !== null) {
    const last = view.messages[view.messages.length - 1];
    out.slaLeft = last?.from === "user" ? slaLeft(row.kind, row.updated_at, await slaTargets(env), now) : null;
  }
  if (env.actor && can(env.actor, "inquiries", "edit")) {
    const list = (await templates(env, now)).templates;
    out.templates = list.sort((x, y) => (y.topic === view.topic) - (x.topic === view.topic) || y.uses - x.uses || x.id - y.id);
  }
  return out;
}

/** [ADMIN-17] Who did it, as a name: rows written before 1.23 carried "이름 <메일>", shown without the mail. */
export const plainActor = (actor) => String(actor ?? "").replace(/\s*<[^<>]*@[^<>]*>\s*$/, "");

/**
 * [ADMIN-16] How a list is paged: [after] rows are skipped (the last answer's next), [limit] come, 50
 * unless asked for fewer or up to 100.
 */
export function page(params, fallback = 50) {
  const after = Number(params.get("after"));
  const limit = Number(params.get("limit"));
  return { after: Number.isInteger(after) && after > 0 ? after : 0, limit: Number.isInteger(limit) && limit > 0 ? Math.min(limit, 100) : fallback };
}

/** One page of [list] and where the next starts, or null at the end. */
export const paged = (list, { after, limit }) => ({ items: list.slice(after, after + limit), next: after + limit < list.length ? after + limit : null });

/** [ADMUX-06] How long a question of each kind may wait for an answer, in hours (설정 › 답 목표). */
export const SLA = { keys: { slaBug: 8, slaQuestion: 24, slaIdea: 72, slaOther: 72 }, least: 1, most: 168 };
export const slaKey = (kind) => (kind === "bug" || kind === "problem" ? "slaBug" : kind === "question" ? "slaQuestion" : kind === "idea" ? "slaIdea" : "slaOther");
export const slaSetting = (key) => key.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`);

export const slaTargets = async (env) => readSla(await all(env, SLA_SQL));

export function readSla(rows) {
  const out = {};
  for (const [k, fallback] of Object.entries(SLA.keys)) {
    const n = Number(rows.find((r) => r.key === slaSetting(k))?.value);
    out[k] = Number.isInteger(n) && n >= SLA.least && n <= SLA.most ? n : fallback;
  }
  return out;
}

/** [ADMUX-06] Minutes until a waiting question passes its kind's target (below 0: past it), or null. */
export const slaLeft = (kind, waitingSince, targets, now) => (waitingSince === null ? null : Math.round((waitingSince + targets[slaKey(kind)] * HOUR - now) / 60000));

/** The sorts the questions list offers; the default is the one it always had. */
export const INQUIRY_SORTS = ["default", "sla", "oldest", "newest", "same"];

/**
 * Questions waiting first, then the newest; [q] finds words in what was asked or answered. [ADMIN-16]
 * Up to 1000 are read in one go, counted by topic before the topic filter and paged after sorting.
 */
export async function inquiryList(env, params, actorId = "") {
  // [ADMUX-02] 놓친 것 is only ever a waiting question, so the status filter follows it.
  const late = params.get("late") === "1" ? Date.now() - INQUIRY.lateMs : 0;
  const status = late ? "open" : ["open", "answered", "closed"].includes(params.get("status")) ? params.get("status") : null;
  const q = String(params.get("q") ?? "").trim().slice(0, 40);
  const topic = Object.hasOwn(TOPIC_NAMES, params.get("topic") ?? "") ? params.get("topic") : null;
  // [ADMIN-12] mine: the ones I took; free: the ones nobody took yet.
  const who = ["mine", "free"].includes(params.get("who")) ? params.get("who") : "";
  const sort = INQUIRY_SORTS.includes(params.get("sort")) ? params.get("sort") : "default";
  const like = `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  // AT 1.29 [ADMCP-02] One kind (문제 신고만), copied or not yet (복사함), and one sending phone (같은 기기).
  const kind = INQUIRY.kinds.includes(params.get("kind")) ? params.get("kind") : null;
  const copied = ["no", "yes"].includes(params.get("copied")) ? params.get("copied") : "";
  const sender = /^[0-9a-f]{6}$/.test(params.get("sender") ?? "") ? params.get("sender") : "";
  // 1.36 [INQ-07] 결제·환불 기록만.
  const billing = params.get("billing") === "1" ? 1 : 0;
  const now = Date.now();
  const [rows, statusRows, slaRows, typing, fresh] = (await batch(env, [stmt(env, `SELECT i.*,
      (SELECT COUNT(*) FROM inquiry_messages m WHERE m.inquiry_id = i.id) AS count,
      (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first,
      (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last,
      (SELECT COUNT(*) FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL WHERE l.inquiry_id = i.id) AS dev,
      (SELECT COUNT(*) FROM inquiry_notes n WHERE n.inquiry_id = i.id) AS notes
    FROM inquiries i
    WHERE (?1 IS NULL OR i.status = ?1)
      AND (?2 = '' OR EXISTS (SELECT 1 FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.text LIKE ?3 ESCAPE '\\')
        OR i.info LIKE ?3 ESCAPE '\\' OR i.id = ?2)
      AND (?4 = '' OR (?4 = 'mine' AND i.assignee = ?5) OR (?4 = 'free' AND i.assignee IS NULL))
      AND (?6 = 0 OR (i.status = 'open' AND i.updated_at < ?6))
      AND (?7 IS NULL OR i.kind = ?7)
      AND (?8 = '' OR (?8 = 'no' AND i.copied_at IS NULL) OR (?8 = 'yes' AND i.copied_at IS NOT NULL))
      AND (?9 = '' OR substr(i.device, 1, 6) = ?9)
      AND (?10 = 0 OR i.billing_at IS NOT NULL)
    ORDER BY i.status = 'open' DESC, i.updated_at DESC LIMIT 1000`, status, q, like, who, actorId, late, kind, copied, sender, billing),
  stmt(env, "SELECT status, COUNT(*) AS n FROM inquiries GROUP BY status"),
  slaQuery(env),
  stmt(env, "SELECT key FROM counters WHERE key LIKE 'typing|%' AND expires > ?", now),
  // AT 1.29 [ADMCP-04] 복사 전 문제 제보, whatever the filter: how many, and how many came today.
  stmt(env, FRESH_BUGS_SQL, todayStart(now))])).map((r) => r.results);
  const counts = { open: 0, answered: 0, closed: 0 };
  for (const r of statusRows) {
    if (Object.hasOwn(counts, r.status)) counts[r.status] = r.n;
  }
  const targets = readSla(slaRows);
  const writing = typingByInquiry(typing, actorId);
  const listed = rows.map((r) => {
    const info = readInfo(r.info);
    const waitingSince = r.status === "open" && r.last === "user" ? r.updated_at : null;
    return {
      id: r.id,
      kind: r.kind,
      status: r.status,
      preview: [...(r.first ?? "")].slice(0, 120).join(""),
      count: r.count,
      waiting: r.last === "user",
      appVersion: info.app ?? null,
      createdAt: iso(r.created_at),
      updatedAt: iso(r.updated_at),
      ...triage(r.kind, r.first, info),
      public: r.public === 1,
      hidden: r.hidden ?? null,
      flags: r.flags ?? 0,
      same: r.same ?? 0,
      assignee: r.assignee ? { id: r.assignee, name: r.assignee_name ?? "" } : null,
      dev: r.dev,
      notes: r.notes,
      // [INQ-04] How long the user has waited on an answer, in hours; null when it is not their turn to wait.
      waitingHours: waitingSince === null ? null : Math.floor((now - waitingSince) / HOUR),
      // [ADMUX-06] Minutes left before this kind's answer target; below 0 once past it.
      slaLeft: slaLeft(r.kind, waitingSince, targets, now),
      rating: r.rating ?? 0,
      // [ADMIN-16] Another admin writing an answer to it right now.
      typing: writing[r.id] ?? [],
      // AT 1.29 [ADMCP-03] 복사함 (when, by whom) and [ADMCP-06] a short mark of the phone that sent it.
      ...copyFields(r),
    };
  });
  // How many of this view are in each topic, so the filter shows where the questions pile up.
  const topics = {};
  for (const i of listed) topics[i.topic] = (topics[i.topic] ?? 0) + 1;
  const rank = { urgent: 0, normal: 1, low: 2 };
  // Among those waiting for an answer: urgent first, then the ones most people said 나도 그래요 to; the rest
  // stay newest first.
  const waits = (i) => (i.status === "open" ? 0 : 1);
  const left = (i) => i.slaLeft ?? Infinity;
  const order = {
    default: (a, b) => waits(a) - waits(b) || (waits(a) === 0 ? rank[a.urgency] - rank[b.urgency] || b.same - a.same : 0),
    sla: (a, b) => left(a) - left(b) || waits(a) - waits(b),
    oldest: (a, b) => a.updatedAt.localeCompare(b.updatedAt),
    newest: (a, b) => b.updatedAt.localeCompare(a.updatedAt),
    same: (a, b) => b.same - a.same || waits(a) - waits(b),
  }[sort];
  const shown = listed.filter((i) => !topic || i.topic === topic).sort(order);
  const { items, next } = paged(shown, page(params));
  return { counts, topics, topicNames: TOPIC_NAMES, inquiries: items, total: shown.length, next, sort, sla: targets,
    // [ADMUX-02] What the server took the filter to be, so the app shows the status 놓친 것 fixed.
    status: status ?? "", late: Boolean(late),
    // AT 1.29 [ADMCP-02][ADMCP-04] The new filters as taken, and 복사 전 문제 제보 for the strip above the list.
    kind: kind ?? "", copied, sender, fresh: { bugs: fresh[0]?.n ?? 0, today: fresh[0]?.today ?? 0 } };
}

// AT 1.29 시작
// [ADMCP-01]~[ADMCP-06] 문의 복사: a question (or many) read whole to copy into a development task, 복사함 marked
// on the ones copied (with, if asked, a 개발사항 made from them and their status changed in the same step), and
// the open problem reports nobody copied yet counted for the badges and the list.

/** Open or answered 문제 신고 nobody copied yet, and how many of them came since ?1 (today's start). */
export const FRESH_BUGS_SQL = `SELECT COUNT(*) AS n, COALESCE(SUM(created_at >= ?1), 0) AS today FROM inquiries
  WHERE kind = 'bug' AND copied_at IS NULL AND status != 'closed'`;

export const todayStart = (now) => Date.parse(`${kstDay(now)}T00:00:00+09:00`);

/**
 * [ADMCP-03] 복사함 as the lists and a question show it, and [ADMCP-06] the sending phone as six letters of the
 * hash the server already keeps: enough to see that two questions came from one phone, nothing that names it.
 */
export const copyFields = (row) => ({
  copied: row.copied_at ? { at: iso(row.copied_at), by: row.copied_by ?? "" } : null,
  sender: String(row.device ?? "").slice(0, 6),
});

/** At most this many questions in one copy, like the other bulk steps (DEV.bulk). */
export const COPY_MOST = 100;

/** The ids in [v] (an array, or "a,b,c" from an address): 1 to 100 question ids, each once, or a refusal. */
export function copyIds(v) {
  const list = Array.isArray(v) ? v : String(v ?? "").split(",").filter(Boolean);
  const ids = [...new Set(list)];
  if (!ids.length || ids.length > COPY_MOST || ids.some((id) => typeof id !== "string" || !/^[A-Za-z0-9]{22}$/.test(id))) refuse("ids", "ids");
  return ids;
}

/**
 * [ADMCP-01][ADMCP-02] GET /inquiries/export?ids=: the questions whole (thread, what came with it, notes, the
 * timeline), in the order asked, for the app to write as one text. Reading only: 복사함 is marked by /copied
 * once the text is really on the clipboard. Anyone who may see questions may copy them.
 */
export async function inquiryExport(env, params) {
  const ids = copyIds(params.get("ids"));
  const rows = await all(env, `SELECT * FROM inquiries WHERE id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  const byId = new Map(rows.map((r) => [r.id, r]));
  const out = [];
  for (const id of ids) {
    const row = byId.get(id);
    if (!row) continue;
    const { templates, typing, ...view } = await adminInquiryView(env, row);
    out.push(view);
  }
  return { inquiries: out, missing: ids.length - out.length };
}

/**
 * [ADMCP-03] POST /inquiries/copied {ids, dev?: {title, body}, item?, status?, assign?}: 복사함 on each (now, by
 * me), and in the same step, when asked, a new 개발사항 made from them or an existing one they join (both need
 * 개발사항 고치기), closed or opened again, and taken by me where nobody holds them. One activity line for the lot.
 */
export async function inquiryCopied(request, env, now) {
  const body = await readJson(request, 32 * 1024);
  const ids = copyIds(body.ids);
  if (body.status !== undefined && body.status !== null && !["open", "closed"].includes(body.status)) refuse("status", "status");
  const wantsDev = (body.dev !== undefined && body.dev !== null) || (body.item !== undefined && body.item !== null);
  if (wantsDev && !can(env.actor, "dev", "edit")) return json({ error: "no_permission", area: "dev" }, 403);
  const rows = await all(env, `SELECT id, assignee FROM inquiries WHERE id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  if (!rows.length) return json({ error: "not_found" }, 404);
  const name = actorName(env);
  const a = env.actor;
  let item = null;
  if (body.item !== undefined && body.item !== null) {
    item = typeof body.item === "string" ? await one(env, "SELECT id, title FROM dev_items WHERE id = ? AND deleted_at IS NULL", body.item) : null;
    if (!item) return json({ error: "not_found" }, 404);
  } else if (body.dev !== undefined && body.dev !== null) {
    if (typeof body.dev !== "object" || Array.isArray(body.dev)) refuse("title", "title");
    const title = plainText(body.dev.title, DEV.title, "title", true);
    const text = plainText(body.dev.body, DEV.body, "body");
    item = { id: `d${randomId(15)}`, title };
    await batch(env, [
      stmt(env, `INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, assignee, assignee_name, created_at, updated_at, deleted_at)
        VALUES (?, ?, ?, 'todo', '', ?, ?, NULL, NULL, ?, ?, NULL)`, item.id, title, text, a.id, name, now, now),
      devEvent(env, item.id, now, "create", { title, body: cut(text) }),
    ]);
    await audit(env, now, "dev_create", title, item.id);
  }
  const found = rows.map((r) => r.id);
  // D1 binds at most 100 values in one statement: 100 questions plus the two set values went over, so in groups of 90.
  const stmts = [];
  for (let i = 0; i < found.length; i += 90) {
    const part = found.slice(i, i + 90);
    const marks = part.map(() => "?").join(", ");
    stmts.push(stmt(env, `UPDATE inquiries SET copied_at = ?, copied_by = ? WHERE id IN (${marks})`, now, name, ...part));
    if (body.status === "open" || body.status === "closed") stmts.push(stmt(env, `UPDATE inquiries SET status = ? WHERE id IN (${marks})`, body.status, ...part));
  }
  // Only the ones nobody holds: copying is no reason to take a question from another admin.
  const free = body.assign === true ? rows.filter((r) => !r.assignee).map((r) => r.id) : [];
  for (let i = 0; i < free.length; i += 90) {
    const part = free.slice(i, i + 90);
    stmts.push(stmt(env, `UPDATE inquiries SET assignee = ?, assignee_name = ? WHERE id IN (${part.map(() => "?").join(", ")})`, a.id, name, ...part));
  }
  await batch(env, stmts);
  let linked = 0;
  if (item) {
    for (const id of found) {
      const r = await run(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", item.id, id, name, now);
      if (r.meta.changes) {
        linked++;
        await devEvent(env, item.id, now, "link", { inquiry: id }).run();
      }
    }
    await run(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, item.id);
  }
  const status = body.status === "open" || body.status === "closed" ? body.status : null;
  const steps = [`${found.length}건`, item ? `개발사항 ${linked}건 연결` : "", status ? (status === "closed" ? "닫음" : "다시 엶") : "",
    free.length ? `${free.length}건 맡음` : ""].filter(Boolean).join(" · ");
  await audit(env, now, "inquiry_copy", item ? item.title : "", steps);
  return json({ marked: found.length, skipped: ids.length - found.length, copied: { at: iso(now), by: name },
    item: item ? { id: item.id, title: item.title } : null, linked, status, assigned: free.length });
}
// AT 1.29 끝

export const SLA_SQL = `SELECT key, value FROM settings WHERE key IN (${Object.keys(SLA.keys).map((k) => `'${slaSetting(k)}'`).join(", ")})`;
export const slaQuery = (env) => env.DB.prepare(SLA_SQL);

/** [ADMIN-16] typing|<question>|<admin id>|<name> rows as names per question, leaving out [me]. */
export function typingByInquiry(rows, me) {
  const out = {};
  for (const { key } of rows) {
    const [, id, admin, ...name] = key.split("|");
    if (admin === me) continue;
    (out[id] ??= []).push(name.join("|"));
  }
  return out;
}

// ---- 개발사항: what admins gather from questions for the developer [ADMIN-10][ADMIN-11] ----
//
// Any admin with "dev" adds an item (from a question in one tap, or by hand), changes or deletes it, and
// links more questions to it; how many questions are linked is how many people asked. Setting 개발중,
// 완료 or 반려 needs "devStatus" as well (the owner has it and gives it). Every change is written to the
// item's own history with who made it and what it was before, and deleting only marks the item, so the
// owner can bring it back with its history whole.

export const DEV = {
  statuses: { todo: "접수", doing: "개발중", done: "완료", rejected: "반려" },
  title: 100,
  body: 4000,
  reason: 300,
  note: 1000,
  // Before and after of a long text in the history; enough to see what changed.
  kept: 1000,
  bulk: 100,
  list: 2000,
};

/** [ADMIN-12] What a new server's admins start with in 자주 쓰는 답; they add their own and delete these. */
export const DEFAULT_TEMPLATES = [
  ["확인 중", "알려 주셔서 고마워요. 지금 확인하고 있어요. 고치는 대로 다시 알려 드릴게요."],
  ["고쳤어요", "말씀하신 문제를 고쳤어요. 앱을 최신 버전으로 업데이트한 뒤 다시 확인해 주세요."],
  ["더 알려 주세요", "정확히 확인하려면 조금 더 알려 주세요. 언제, 어떤 화면에서, 무엇을 눌렀을 때 생겼는지 적어 주시면 큰 도움이 돼요."],
  ["설정 안내", "설정에서 바로 바꿀 수 있어요. 설정 > 해당 항목에서 원하는 값으로 바꿔 보세요."],
  ["개발 예정", "좋은 의견 고마워요. 개발할 목록에 올려 두었어요. 반영되면 다시 알려 드릴게요."],
  ["반영했어요", "말씀해 주신 내용을 반영했어요. 앱을 최신 버전으로 업데이트하면 쓸 수 있어요."],
];

/** Text an admin typed: control characters out, lines kept, [max] letters; [required] refuses an empty one. */
export function plainText(v, max, field, required = false) {
  if (v === undefined || v === null) v = "";
  if (typeof v !== "string") refuse(field, field);
  const text = v.normalize("NFC").replace(/\r\n?/g, "\n").replace(/[\u0000-\u0008\u000b-\u001f\u007f]/g, "").trim();
  if ((required && !text) || [...text].length > max) refuse(field, field);
  return text;
}

export const cut = (text) => ([...text].length > DEV.kept ? `${[...text].slice(0, DEV.kept).join("")}…` : text);

/** One line of an item's history, by the signed-in admin. */
export const devEvent = (env, id, now, action, detail = {}) => stmt(env,
  "INSERT INTO dev_history (item_id, at, actor_id, actor, action, detail) VALUES (?, ?, ?, ?, ?, ?)",
  id, now, env.actor?.id ?? "", actorName(env), action, JSON.stringify(detail));

export async function devRoute(request, env, url, path, now) {
  const method = request.method;
  if (path === "/dev" && method === "GET") return json(await devList(env, url.searchParams));
  if (path === "/dev" && method === "POST") return devCreate(request, env, now);
  if (path === "/dev/bulk" && method === "POST") return devBulk(request, env, now);
  const m = path.match(/^\/dev\/(d[A-Za-z0-9]{15})(\/delete|\/restore|\/links|\/links\/remove|\/notes|\/reply)?$/);
  if (!m) return json({ error: "not_found" }, 404);
  const row = await one(env, "SELECT * FROM dev_items WHERE id = ?", m[1]);
  if (!row) return json({ error: "not_found" }, 404);
  if (!m[2] && method === "GET") return json(await devView(env, row));
  if (method !== "POST") return json({ error: "not_found" }, 404);
  const body = await readJson(request, 16 * 1024);
  if (m[2] === "/restore") {
    // Only the owner brings back what someone deleted, so a deletion cannot be undone behind their back.
    if (!env.actor.owner) return json({ error: "no_permission", area: "owner" }, 403);
    if (row.deleted_at === null) return json(await devView(env, row));
    await batch(env, [
      stmt(env, "UPDATE dev_items SET deleted_at = NULL, updated_at = ? WHERE id = ?", now, row.id),
      devEvent(env, row.id, now, "restore"),
    ]);
    await audit(env, now, "dev_restore", row.title, row.id);
  } else if (row.deleted_at !== null) {
    return json({ error: "deleted" }, 409);
  } else if (!m[2]) {
    const changed = await devUpdate(env, row, body, now);
    if (changed === "stale") return json({ error: "changed", item: await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)) }, 409);
  } else if (m[2] === "/delete") {
    await batch(env, [
      stmt(env, "UPDATE dev_items SET deleted_at = ?, updated_at = ? WHERE id = ?", now, now, row.id),
      devEvent(env, row.id, now, "delete", { title: row.title }),
    ]);
    await audit(env, now, "dev_delete", row.title, row.id);
  } else if (m[2] === "/links" || m[2] === "/links/remove") {
    // Linking shows a question to whoever opens the item, so it takes a look at questions too.
    if (!can(env.actor, "inquiries")) return json({ error: "no_permission", area: "inquiries" }, 403);
    const inquiry = typeof body.inquiry === "string" && /^[A-Za-z0-9]{22}$/.test(body.inquiry) ? body.inquiry : refuse("inquiry", "inquiry");
    if (m[2] === "/links") {
      if (!(await one(env, "SELECT id FROM inquiries WHERE id = ?", inquiry))) return json({ error: "not_found" }, 404);
      const r = await run(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", row.id, inquiry, actorName(env), now);
      if (r.meta.changes) {
        await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id), devEvent(env, row.id, now, "link", { inquiry })]);
        await audit(env, now, "dev_link", row.title, inquiry);
      }
    } else {
      const r = await run(env, "DELETE FROM dev_links WHERE item_id = ? AND inquiry_id = ?", row.id, inquiry);
      if (r.meta.changes) {
        await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id), devEvent(env, row.id, now, "unlink", { inquiry })]);
        await audit(env, now, "dev_unlink", row.title, inquiry);
      }
    }
  } else if (m[2] === "/notes") {
    const text = plainText(body.text, DEV.note, "text", true);
    await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id), devEvent(env, row.id, now, "note", { text })]);
  } else {
    return devReply(env, row, body, now);
  }
  return json(await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)));
}

/**
 * 2.0.0 [RVA-37] 검토 I24b-02: 문의에 붙은 기기 정보(info)를 못 읽어도 그 문의·목록이 500이 되지 않게. 못 읽으면 빈 정보에
 * unreadable 표시만(화면은 '기기 정보를 읽지 못했어요'), 운영 장부·정리함은 그 줄을 오류 묶음에 따로 적어요.
 */
export function readInfo(raw) {
  try {
    const v = JSON.parse(raw || "{}");
    return v && typeof v === "object" && !Array.isArray(v) ? v : { unreadable: true };
  } catch {
    return { unreadable: true };
  }
}

/** A new item, by hand or from a question (inquiry): the question is linked in the same step. */
export async function devCreate(request, env, now) {
  const body = await readJson(request, 16 * 1024);
  const title = plainText(body.title, DEV.title, "title", true);
  const text = plainText(body.body, DEV.body, "body");
  let inquiry = null;
  if (body.inquiry !== undefined && body.inquiry !== null) {
    if (!can(env.actor, "inquiries")) return json({ error: "no_permission", area: "inquiries" }, 403);
    inquiry = typeof body.inquiry === "string" && /^[A-Za-z0-9]{22}$/.test(body.inquiry) ? body.inquiry : refuse("inquiry", "inquiry");
    if (!(await one(env, "SELECT id FROM inquiries WHERE id = ?", inquiry))) return json({ error: "not_found" }, 404);
  }
  const id = `d${randomId(15)}`;
  const a = env.actor;
  // 1.39 [LOOP-36] 사용자 11-09 '내가 직접 개발사항에 넣은 것은 1순위': 사람이 문의 없이 바로 만든 것, 또는 사용자 말을 옮기는 Claude 열쇠가 origin "admin"으로 만든 것.
  // 사용자 카드(11-09): 최고 관리자 것만. 다른 관리자가 직접 넣은 것은 출처 '직접'이지만 보통 순서예요.
  const origin = (a.owner && !a.key && !a.auto && !inquiry) || (a.key && !a.sync && body.origin === "admin") ? "admin" : "";
  await batch(env, [
    stmt(env, `INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, assignee, assignee_name, created_at, updated_at, deleted_at, origin)
      VALUES (?, ?, ?, 'todo', '', ?, ?, NULL, NULL, ?, ?, NULL, ?)`, id, title, text, a.id, actorName(env), now, now, origin),
    devEvent(env, id, now, "create", { title, body: cut(text) }),
    ...(inquiry ? [stmt(env, "INSERT INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", id, inquiry, actorName(env), now),
      devEvent(env, id, now, "link", { inquiry })] : []),
  ]);
  await audit(env, now, "dev_create", title, id);
  return json(await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", id)), 201);
}

/**
 * Title, body, who holds it and its status, in one call; each change is one line of history with what
 * it was. With [since] (the updatedAt the admin's screen showed), a change made meanwhile by someone
 * else is not written over: "stale", and the screen shows the newer item.
 */
export async function devUpdate(env, row, body, now) {
  if (body.since !== undefined && body.since !== iso(row.updated_at)) return "stale";
  const stmts = [];
  const notes = [];
  const edit = {};
  const set = {};
  if (body.title !== undefined) {
    const title = plainText(body.title, DEV.title, "title", true);
    if (title !== row.title) [edit.title, set.title] = [[row.title, title], title];
  }
  if (body.body !== undefined) {
    const text = plainText(body.body, DEV.body, "body");
    if (text !== row.body) [edit.body, set.body] = [[cut(row.body), cut(text)], text];
  }
  // 1.37 [ADM2-10] 미리 써 두는 출시 답(판이 나간 날 묶인 문의에)과 업데이트 소식 공지 한 줄.
  if (body.shipReply !== undefined) {
    const text = body.shipReply === null ? "" : plainText(body.shipReply, 1000, "shipReply");
    if (text !== (row.ship_reply ?? "")) [edit.shipReply, set.ship_reply] = [[cut(row.ship_reply ?? ""), cut(text)], text];
  }
  if (body.news !== undefined) {
    const text = body.news === null ? "" : plainText(body.news, 80, "news");
    if (text !== (row.news ?? "")) [edit.news, set.news] = [[row.news ?? "", text], text];
  }
  // 1.39 [LOOP-17][LOOP-08] 알려진 문제판에 보일 쉬운 제목·보일지, 전달 길·크기: 저장소 동기화와 같은 칸을 관리자 앱에서도(요금은 따로, 최고 관리자만).
  if (body.publicTitle !== undefined) {
    const text = body.publicTitle ? inquiryText(String(body.publicTitle)).slice(0, LOOP.knownTitle) : "";
    if (text !== (row.public_title ?? "")) [edit.publicTitle, set.public_title] = [[row.public_title ?? "", text], text];
  }
  if (body.known !== undefined) {
    const k = body.known === true ? 1 : body.known === false ? -1 : 0;
    if (k !== (row.known ?? 0)) [edit.known, set.known] = [[row.known ?? 0, k], k];
  }
  if (body.styles !== undefined) {
    const v = styleList(body.styles);
    if (v !== (row.styles ?? "")) [edit.styles, set.styles] = [[row.styles ?? "", v], v];
  }
  // 1.39 [LOOP-36] 1순위(직접 넣은 것) 올리기·내리기: 사람, 또는 사용자 말을 옮기는 Claude 열쇠(저장소 동기화 열쇠는 못 해요).
  if (body.first !== undefined) {
    // 사용자 카드(11-09 '최고 관리자 것만'): 최고 관리자나 사용자 말을 옮기는 Claude 열쇠만.
    if (env.actor?.sync || !(env.actor?.owner || env.actor?.key)) throw new Refuse("no_permission", "first", 403);
    const v = body.first === true ? "admin" : body.first === false ? "" : refuse("first", "first");
    if (v !== (row.origin ?? "")) [edit.first, set.origin] = [[Boolean(row.origin), Boolean(v)], v];
  }
  for (const [key, names] of [["route", LOOP.routes], ["size", LOOP.sizes]]) {
    if (body[key] === undefined) continue;
    const v = body[key] === null || body[key] === "" ? "" : Object.hasOwn(names, body[key]) ? body[key] : refuse(key, key);
    if (v !== (row[key] ?? "")) [edit[key], set[key]] = [[row[key] ?? "", v], v];
  }
  if (Object.keys(edit).length) {
    stmts.push(devEvent(env, row.id, now, "edit", edit));
    notes.push(["dev_edit", Object.keys(edit).join(" ")]);
  }
  if (body.assignee !== undefined) {
    if (body.assignee !== "me" && body.assignee !== null) refuse("assignee", "assignee");
    const to = body.assignee ? { id: env.actor.id, name: actorName(env) } : null;
    if ((to?.id ?? null) !== row.assignee) {
      set.assignee = to?.id ?? null;
      set.assignee_name = to?.name ?? null;
      stmts.push(devEvent(env, row.id, now, "assign", { assignee: [row.assignee_name ?? "", to?.name ?? ""] }));
    }
  }
  if (body.releasedIn !== undefined) {
    // [INQ-02] The version a finished item went out in, e.g. 1.18.0; blank clears it.
    const v = body.releasedIn === null || body.releasedIn === "" ? null : String(body.releasedIn).trim();
    if (v !== null && !/^\d{1,2}\.\d{1,2}\.\d{1,3}$/.test(v)) refuse("releasedIn", "releasedIn");
    if (v !== (row.released_in ?? null)) {
      set.released_in = v;
      stmts.push(devEvent(env, row.id, now, "release", { releasedIn: [row.released_in ?? "", v ?? ""] }));
    }
  }
  if (body.status !== undefined) {
    const next = devStatus(env, body.status, body.reason);
    if (next.status !== row.status || next.reason !== row.reason) {
      [set.status, set.reason] = [next.status, next.reason];
      stmts.push(devEvent(env, row.id, now, "status", { status: [row.status, next.status], ...(next.reason ? { reason: next.reason } : {}) }));
      notes.push(["dev_status", `${row.status}→${next.status}`]);
    }
  }
  if (!stmts.length) return false;
  const cols = Object.keys(set);
  // Written only over the item as this admin read it: a change that landed meanwhile makes it "stale",
  // and then nothing (not even a history line) is written.
  const updated = await run(env, `UPDATE dev_items SET ${[...cols.map((c) => `${c} = ?`), "updated_at = ?"].join(", ")} WHERE id = ? AND updated_at = ?`,
    // Always a new updated_at, even in the same millisecond as the last change, so a second writer of that
    // moment no longer matches it.
    ...cols.map((c) => set[c]), Math.max(now, row.updated_at + 1), row.id, row.updated_at);
  if (!updated.meta.changes) return "stale";
  await batch(env, stmts);
  // The activity screen shows the title; the id goes with what changed.
  for (const [action, detail] of notes) await audit(env, now, action, set.title ?? row.title, `${detail} ${row.id}`);
  // 1.36 841 쓰면 쌓이는 날: 완료 gives the phones that reported it 3 days.
  if (set.status === "done") {
    await bugFixed(env, row.id, now);
    await ideaAdopted(env, row.id, now); // 1.39 [LOOP-14]
  }
  return true;
}

/** A status this admin may set: devStatus is needed, and 반려 says why (the reason is kept with it). */
export function devStatus(env, status, reason) {
  if (!can(env.actor, "devStatus", "edit")) throw new Refuse("no_permission", "devStatus", 403);
  if (!Object.hasOwn(DEV.statuses, status)) refuse("status", "status");
  const why = status === "rejected" ? plainText(reason, DEV.reason, "reason", true) : "";
  return { status, reason: why };
}

/** The owner (or whoever was given 처리) sets one status on many items at once. */
export async function devBulk(request, env, now) {
  const body = await readJson(request, 16 * 1024);
  const next = devStatus(env, body.status, body.reason);
  if (!Array.isArray(body.ids) || !body.ids.length || body.ids.length > DEV.bulk || body.ids.some((x) => typeof x !== "string" || !/^d[A-Za-z0-9]{15}$/.test(x))) {
    refuse("ids", "ids");
  }
  const ids = [...new Set(body.ids)];
  const rows = await all(env, `SELECT id, status, reason FROM dev_items WHERE deleted_at IS NULL AND id IN (${ids.map(() => "?").join(", ")})`, ...ids);
  const change = rows.filter((r) => r.status !== next.status || r.reason !== next.reason).sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
  if (change.length) {
    await batch(env, change.flatMap((r) => [
      stmt(env, "UPDATE dev_items SET status = ?, reason = ?, updated_at = ? WHERE id = ?", next.status, next.reason, now, r.id),
      devEvent(env, r.id, now, "status", { status: [r.status, next.status], ...(next.reason ? { reason: next.reason } : {}), bulk: change.length }),
    ]));
    await audit(env, now, "dev_bulk", `${change.length}개`, `${next.status}: ${change.map((r) => r.id).join(" ")}`);
    if (next.status === "done") for (const r of change) {
      await bugFixed(env, r.id, now);
      await ideaAdopted(env, r.id, now); // 1.39 [LOOP-14]
    }
  }
  return json({ changed: change.length, ids: change.map((r) => r.id) });
}

/**
 * [ADMIN-11] One answer to every linked question at once, typically when an item is 완료: the admin
 * reads the prefilled text and sends it. [ids] limits it to some of them; [close] closes them too.
 */
export async function devReply(env, row, body, now) {
  if (!can(env.actor, "inquiries", "edit")) return json({ error: "no_permission", area: "inquiries" }, 403);
  const text = plainText(body.text, INQUIRY.text, "text", true);
  const linked = (await all(env, "SELECT l.inquiry_id AS id FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ?", row.id)).map((r) => r.id);
  const pick = Array.isArray(body.ids) ? linked.filter((id) => body.ids.includes(id)) : linked;
  if (!pick.length) refuse("ids", "ids");
  // 1.38 [DESK-09] Claude 열쇠로는 문의마다 10분 보류(바로 가지 않아요).
  if (env.actor?.key) {
    let held = 0;
    for (const id of pick) if (await holdReply(env, { id }, text, { close: body.close === true }, now)) held++;
    await run(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id);
    return json({ answered: 0, held, item: await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)) });
  }
  const status = body.close === true ? "closed" : "answered";
  // 2.0.0 [RVA-51] 개발사항 답도 같은 문: 걸린 문의에는 가지 않고 '사람이 보내야 해요'로 멈춰요(나머지는 바로 가요).
  const gated = [];
  for (const id of pick) {
    const why = await sendGate(env, id, text);
    if (why && (await holdForPerson(env, id, text, why, now, { close: body.close === true }))) gated.push(id);
  }
  if (gated.length === pick.length) {
    await run(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id);
    return json({ answered: 0, held: gated.length, item: await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)) });
  }
  const sendTo = pick.filter((id) => !gated.includes(id));
  await batch(env, [
    ...sendTo.flatMap((id) => [
      stmt(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, ?, ?)", id, text, actorName(env), now),
      stmt(env, "UPDATE inquiries SET status = ?, updated_at = ? WHERE id = ?", status, now, id),
    ]),
    stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, row.id),
    devEvent(env, row.id, now, "reply", { count: sendTo.length, text: cut(text), close: status === "closed" }),
  ]);
  await audit(env, now, "dev_reply", row.title, `${sendTo.length} ${status} ${row.id}${gated.length ? ` (사람이 보내기 ${gated.length})` : ""}`);
  await fcmTell(env, sendTo, now);
  return json({ answered: sendTo.length, ...(gated.length ? { held: gated.length } : {}), item: await devView(env, await one(env, "SELECT * FROM dev_items WHERE id = ?", row.id)) });
}

/** The list: one status, open (접수 and 개발중), all, deleted ones, mine, or words in the title or body. */
export async function devList(env, params) {
  const status = Object.hasOwn(DEV.statuses, params.get("status") ?? "") || ["open", "all", "deleted"].includes(params.get("status")) ? params.get("status") : "all";
  const q = String(params.get("q") ?? "").trim().slice(0, 40);
  const like = `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
  const mine = params.get("who") === "mine" ? env.actor.id : "";
  const [rows, countRows] = (await batch(env, [
    // Only links to questions still kept count.
    stmt(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id) AS links,
        (SELECT COUNT(*) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id AND i.status = 'open') AS waiting,
        (SELECT COUNT(*) FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = d.id AND i.kind = 'idea') AS ideas
      FROM dev_items d
      WHERE ((?1 = 'deleted' AND d.deleted_at IS NOT NULL) OR (?1 != 'deleted' AND d.deleted_at IS NULL
          AND (?1 = 'all' OR d.status = ?1 OR (?1 = 'open' AND d.status IN ('todo', 'doing')))))
        AND (?2 = '' OR d.title LIKE ?3 ESCAPE '\\' OR d.body LIKE ?3 ESCAPE '\\')
        AND (?4 = '' OR d.assignee = ?4)
      ORDER BY d.updated_at DESC LIMIT ${DEV.list}`, status, q, like, mine),
    stmt(env, `SELECT CASE WHEN deleted_at IS NULL THEN status ELSE 'deleted' END AS s, COUNT(*) AS n FROM dev_items GROUP BY s`),
  ])).map((r) => r.results);
  const counts = { todo: 0, doing: 0, done: 0, rejected: 0, deleted: 0 };
  for (const r of countRows) if (Object.hasOwn(counts, r.s)) counts[r.s] = r.n;
  counts.open = counts.todo + counts.doing;
  counts.all = counts.open + counts.done + counts.rejected;
  // Still to do: most asked for first. Finished: newest first.
  const rank = { doing: 0, todo: 1, done: 2, rejected: 3 };
  const open = (r) => r.status === "todo" || r.status === "doing";
  // 1.39 [LOOP-36] 직접 넣은 것(1순위)은 끝나지 않은 동안 늘 맨 위예요.
  const first = (r) => (open(r) && r.origin === "admin" ? 0 : 1);
  rows.sort((a, b) => first(a) - first(b) || (["all", "open"].includes(status) ? rank[a.status] - rank[b.status] : 0) || (open(a) && open(b) ? b.links - a.links : 0) || b.updated_at - a.updated_at);
  // 1.39 [LOOP-34] 어디서 왔는지(문의·개선 요청·저장소 계획·숫자 신호·Claude·직접)로 세고 골라 봐요.
  const sources = Object.fromEntries(Object.keys(DEV_SOURCES).map((k) => [k, 0]));
  for (const r of rows) sources[devSource(r)]++;
  const from = Object.hasOwn(DEV_SOURCES, params.get("from") ?? "") ? params.get("from") : "";
  // 1.39 [LOOP-35] 요금 기준(새 기능·기존 기능 고침·다듬기·운영용·분류 전)으로도 세고 골라 봐요.
  const kinds = Object.fromEntries(Object.keys(LOOP.basis).map((k) => [k, 0]));
  for (const r of rows) kinds[basisOf(r).key]++;
  const kind = Object.hasOwn(LOOP.basis, params.get("kind") ?? "") ? params.get("kind") : "";
  const shown = rows.filter((r) => (!from || devSource(r) === from) && (!kind || basisOf(r).key === kind));
  // [ADMIN-16] A page at a time, after sorting all of the view.
  const { items, next } = paged(shown, page(params));
  return { counts, statuses: DEV.statuses, sources, sourceNames: DEV_SOURCES, kinds, kindNames: LOOP.basis, firsts: rows.filter((r) => first(r) === 0).length,
    items: items.map(devItem), next, total: shown.length };
}

/** 1.39 [LOOP-34] 개발사항이 어디서 왔는지. 관리자가 무엇을 왜 개발하는지 한눈에 보게(사용자 11-09). */
export const DEV_SOURCES = { inquiry: "문의", idea: "개선 요청", repo: "개발 계획", signal: "숫자 신호", claude: "Claude", admin: "직접" };
export function devSource(r) {
  // 1.39 [LOOP-36] 직접 넣은 것은 문의를 나중에 묶거나 저장소가 추적 번호를 붙여도 '직접'이에요(1순위가 흔들리지 않게).
  if (r.origin === "admin") return "admin";
  if (String(r.body ?? "").startsWith("관측 신호")) return "signal";
  if ((r.links ?? 0) > 0) return (r.ideas ?? 0) * 2 >= r.links ? "idea" : "inquiry";
  if (r.created_by_name === "저장소" || r.track) return "repo";
  if (r.created_by_name === "Claude") return "claude";
  // 1.39 전에 사람이 만든 것(origin 칸이 생기기 전)도 직접이에요.
  return String(r.created_by ?? "").startsWith("key:") || r.created_by_name === "자동" ? "claude" : "admin";
}

export const devItem = (r) => ({
  id: r.id,
  title: r.title,
  // The whole body too, so 일괄 복사 hands developers everything without opening each item.
  body: r.body,
  preview: [...r.body].slice(0, 160).join(""),
  status: r.status,
  reason: r.reason,
  releasedIn: r.released_in ?? null,
  shipReply: r.ship_reply ?? "",
  news: r.news ?? "",
  // 1.39 [LOOP-17][LOOP-08]
  publicTitle: r.public_title ?? "",
  known: r.known ?? 0,
  route: r.route ?? "",
  size: r.size ?? "",
  track: r.track ?? "",
  styles: String(r.styles ?? "").split(",").filter(Boolean),
  links: r.links ?? 0,
  waiting: r.waiting ?? 0,
  source: devSource(r),
  // 1.39 [LOOP-36] 1순위: 직접 넣은 것. [LOOP-35] 요금 기준과 실제 요금.
  first: r.origin === "admin",
  basis: basisOf(r),
  pay: tierEffective(r),
  // 1.39 [LOOP-39] 출시 2주 뒤 숫자로 본 효과.
  effect: effectOf(r),
  createdBy: { id: r.created_by, name: r.created_by_name },
  assignee: r.assignee ? { id: r.assignee, name: r.assignee_name ?? "" } : null,
  createdAt: iso(r.created_at),
  updatedAt: iso(r.updated_at),
  deletedAt: r.deleted_at ? iso(r.deleted_at) : null,
});

/** One item with its whole history and the questions behind it (their words only for who may read questions). */
export async function devView(env, row) {
  const [links, history] = (await batch(env, [
    stmt(env, `SELECT l.inquiry_id, l.by, l.at, i.kind, i.status, i.updated_at,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id LIMIT 1) AS first
      FROM dev_links l LEFT JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ? ORDER BY l.at`, row.id),
    stmt(env, "SELECT id, at, actor, action, detail FROM dev_history WHERE item_id = ? ORDER BY id DESC", row.id),
  ])).map((r) => r.results);
  const read = can(env.actor, "inquiries");
  return {
    ...devItem({ ...row, links: links.filter((l) => l.kind).length, waiting: links.filter((l) => l.status === "open").length, ideas: links.filter((l) => l.kind === "idea").length }),
    statuses: DEV.statuses,
    inquiries: read ? links.filter((l) => l.kind).map((l) => ({
      id: l.inquiry_id, kind: l.kind, status: l.status, preview: [...(l.first ?? "")].slice(0, 160).join(""),
      updatedAt: iso(l.updated_at), linkedBy: l.by, linkedAt: iso(l.at),
    })) : [],
    history: history.map((h) => {
      let detail = {};
      try {
        detail = JSON.parse(h.detail);
      } catch {
        // An old line keeps its action; nothing more to show.
      }
      // The words sent to questions are theirs to read who may read questions, like the previews above.
      if (!read && h.action === "reply" && detail && typeof detail === "object") delete detail.text;
      return { id: h.id, at: iso(h.at), actor: h.actor, action: h.action, detail };
    }),
  };
}

// ---- 자주 쓰는 답, shared by every admin [ADMIN-12] ----

export async function templates(env, now) {
  // A new server starts with a few; once they are written, deleting one keeps it deleted. The key goes in
  // first and only the call that put it there seeds, so two admins opening the list at once add them once.
  const first = await run(env, "INSERT OR IGNORE INTO settings (key, value) VALUES ('templates', '1')");
  if (first.meta.changes) {
    await batch(env, DEFAULT_TEMPLATES.map(([label, text]) => stmt(env, "INSERT INTO reply_templates (label, text, by, at) VALUES (?, ?, '', ?)", label, text, now)));
  }
  const rows = await all(env, "SELECT id, label, text, by, topic, uses, public_q FROM reply_templates ORDER BY id");
  return { templates: rows.map((r) => ({ id: r.id, label: r.label, text: r.text, by: r.by, topic: r.topic ?? "", uses: r.uses ?? 0,
    publicQ: r.public_q ?? "" })), topicNames: TOPIC_NAMES };
}

// ---- 1.38 [EASE-31] 밤낮 Q&A: 자주 쓰는 답 중 관리자가 고른 것을 마을에 공개 (오늘근무 분석 P13, 공개 관찰) ----
//
// 자주 쓰는 답은 한 사람에게 보내는 말이라, 공개하려면 관리자가 '묻는 말'(public_q)을 적어요. 그 칸이 차 있는 답만 공개되고, 답 글의
// {앱버전}·{주제}·{오늘}은 누구에게나 맞는 말로 바꿔요. 문의 글이나 보낸 분은 나가지 않아요(관리자가 쓴 답만).


export const QA = { question: 80, max: 40, cacheSeconds: 600 };

/** 공개 답 글: 한 사람에게 쓴 채움 말을 모두에게 맞는 말로. */
export function publicAnswer(text, topicName) {
  return String(text ?? "")
    .replaceAll("{앱버전}", "최신 버전")
    .replaceAll("{주제}", topicName || "이 내용")
    .replaceAll("{오늘}", "오늘")
    .trim();
}

/** GET /api/qa: 공개로 고른 자주 쓰는 답, 많이 쓴 것부터. */
export async function publicQa(env) {
  const rows = await all(env, `SELECT id, text, topic, uses, public_q, at FROM reply_templates WHERE public_q != '' ORDER BY uses DESC, id LIMIT ${QA.max}`)
    .catch(() => []);
  return {
    items: rows.map((r) => ({
      id: r.id, q: r.public_q, a: publicAnswer(r.text, TOPIC_NAMES[r.topic] ?? ""), topic: r.topic ?? "",
      topicName: TOPIC_NAMES[r.topic] ?? "", uses: r.uses ?? 0, updatedAt: iso(r.at),
    })),
  };
}

/** 1.38 [EASE-31] 공개 Q&A의 묻는 말: 비우면 공개하지 않아요. */
export function qaQuestion(v) {
  if (v === undefined || v === null) return "";
  return plainText(v, QA.question, "publicQ", false);
}

/** [ADMIN-16] A saved answer's topic: one of the question topics, or '' for any question. */
export function templateTopic(v) {
  if (v === undefined || v === null || v === "") return "";
  if (typeof v !== "string" || !Object.hasOwn(TOPIC_NAMES, v)) refuse("topic", "topic");
  return v;
}

export async function templateRoute(request, env, path, now) {
  const method = request.method;
  if (path === "/templates" && method === "GET") return json(await templates(env, now));
  if (path === "/templates" && method === "POST") {
    await templates(env, now);
    const body = await readJson(request, 8 * 1024);
    const label = plainText(body.label, 20, "label", true);
    const text = plainText(body.text, INQUIRY.text, "text", true);
    const topic = templateTopic(body.topic);
    const publicQ = qaQuestion(body.publicQ);
    if ((await one(env, "SELECT COUNT(*) AS n FROM reply_templates")).n >= 40) return json({ error: "too_many" }, 409);
    await run(env, "INSERT INTO reply_templates (label, text, by, at, topic, public_q) VALUES (?, ?, ?, ?, ?, ?)", label, text, actorName(env), now, topic, publicQ);
    await audit(env, now, "template_add", label);
    return json(await templates(env, now), 201);
  }
  // [ADMIN-16] A saved answer changed in place, keeping how often it was used.
  const edit = path.match(/^\/templates\/(\d{1,9})$/);
  if (edit && method === "POST") {
    const row = await one(env, "SELECT label FROM reply_templates WHERE id = ?", Number(edit[1]));
    if (!row) return json({ error: "not_found" }, 404);
    const body = await readJson(request, 8 * 1024);
    const label = plainText(body.label, 20, "label", true);
    const text = plainText(body.text, INQUIRY.text, "text", true);
    const publicQ = qaQuestion(body.publicQ);
    await run(env, "UPDATE reply_templates SET label = ?, text = ?, topic = ?, by = ?, at = ?, public_q = ? WHERE id = ?", label, text, templateTopic(body.topic),
      actorName(env), now, publicQ, Number(edit[1]));
    await audit(env, now, "template_edit", label, row.label === label ? (publicQ ? "공개 Q&A" : "") : `${row.label} → ${label}`);
    return json(await templates(env, now));
  }
  const m = path.match(/^\/templates\/(\d{1,9})\/remove$/);
  if (m && method === "POST") {
    const row = await one(env, "SELECT label FROM reply_templates WHERE id = ?", Number(m[1]));
    if (!row) return json({ error: "not_found" }, 404);
    await run(env, "DELETE FROM reply_templates WHERE id = ?", Number(m[1]));
    await audit(env, now, "template_remove", row.label);
    return json(await templates(env, now));
  }
  return json({ error: "not_found" }, 404);
}

