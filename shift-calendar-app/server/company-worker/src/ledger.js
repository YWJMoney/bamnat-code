import { all, batch, can, iso, json, readJson, stmt } from "./index.js";
import { DAY, HOUR } from "./units.js";
import { DEV, HELD_FOR_PERSON, TOPIC_NAMES, slaKey, slaTargets, triage } from "./inquiries.js";
import { PILE_NAMES, compareVersions, grams, known, likeness, sortInbox, titleFrom, version } from "./inbox.js";
import { isOut } from "./desk.js";
import { askOwnerOf, replyRiskOf } from "./ask-check.js";
import { DONE_REASONS, WAIT_REASONS, WORK, WORK_GROUPS, WORK_GROUP_NAMES, WORK_STATE_NAMES, WORK_TYPES, workList } from "./work.js";
import { errorLine, recordRowErrors } from "./ops-watch.js";
import { REVIEWS } from "./store-reviews.js";
import { marketHealthRows } from "./market-health.js";
import { CRASH_ASK_DRAFT, CRASH_ASK_DRAFT_REPLY, crashOf, crashRepeats, diagMark, repeatPlaces, replyCarriesRecord } from "./crash-repeat.js";

// ---- 1.38 [DESK-02][DESK-03] 운영 장부: 들어온 모든 일을 한 줄씩, 한 화면에서 (설계 docs/plans/1.37-문의-개발-운영장부-설계.md 2장) ----
//
// 한 줄 = 한 건. 문의(질문·문제·제안·회사 방식)와 개발사항, 1.36 할 일함의 모든 종류(스튜디오 요구·신고·사진·검토·운영)가 같은 표에 와요.
//   번호   K-0001처럼 짧게(말로 부르기 쉽게). 처음 장부에 올라온 순서로 한 번 붙으면 바뀌지 않아요(ledger_lines).
//   요약   Claude(또는 관리자)가 쓴 한 줄, 없으면 문의 첫 문장. 화면 경로([설정 > …])와 앱 판.
//   겹침   이전 개발사항·전에 답한 문의·자주 쓰는 답 중 비슷한 것 3개까지, 점수와 이유(같은 주제·같은 화면·고친 판).
//   묶음   묶인 개발사항과 그 개발사항에 묶인 문의 수.
//   단계   접수 → 분류됨 → (답함 | 개발 대기 → Claude에게 넘김 → 개발 중 → 고침·출시 전 → 출시됨·답 남음 → 출시됨·답 보냄) → 닫힘, 반려.
//   답     안 함 · 보냄(무엇을, 누가) · 최고 관리자 확인(환불·법·개인정보처럼 Claude가 혼자 보내지 않는 것).
//   담당   다음에 움직일 쪽: Claude · 최고 관리자 · 관리자 · 문의한 분(답 기다림) · 출시(Play에 올리고 나간 판 저장).
//   기한   문의는 답 목표, 법·안전은 24시간, 신고·사진 등은 할 일함 기한, 기다림은 7일.
//   기록   누가(사람·Claude·자동)·언제·무엇을, 마지막 상태 바꿈은 되돌리기.
// [DESK-03] 상태(새로·보는 중·기다림·끝)와 끝낼 이유는 할 일함의 work_states를 그대로 써서 정리함·할 일함·장부가 한 기록이에요.

export const LEDGER = {
  /** 닫힌 줄을 며칠까지 보여 줄지(기본, 가장 길게). */
  days: 30,
  maxDays: 180,
  /** 한 번에 읽는 문의·개발사항 수. */
  inquiries: 600,
  devs: 400,
  /** 겹침 후보: 몇 개까지, 이 점수 아래는 빼요. 같은 주제·같은 화면이면 조금 더해요. */
  overlaps: 3,
  overlapFloor: 0.3,
  pathBonus: 0.05,
  /** 법·안전 신고는 이 안에 봐요. */
  safetyHours: 24,
  /** 한 번에 보내는 줄 수(CSV는 모두). */
  page: 300,
  summary: 120,
  /** 2.0.0 [RVA-50] 스토어 리뷰: 한 번에 읽는 수, 별 1~2개에 답할 목표(시간). */
  reviews: 200,
  reviewHours: 24,
};

export const LEDGER_SCHEMA = [
  // 줄 번호(K-0001)와 Claude가 쓴 한 줄 요약. key는 할 일함과 같은 'type:id'.
  `CREATE TABLE IF NOT EXISTS ledger_lines (key TEXT PRIMARY KEY, n INTEGER NOT NULL UNIQUE, at INTEGER NOT NULL, summary TEXT NOT NULL DEFAULT '',
    summary_by TEXT NOT NULL DEFAULT '', summary_at INTEGER)`,
];

/** 단계 이름. 순서가 곧 흐름이에요. */
export const STAGES = {
  intake: "접수", sorted: "분류됨", answered: "답함", devwait: "개발 대기", handed: "Claude에게 넘김", doing: "개발 중", built: "고침 · 출시 전",
  notify: "출시됨 · 답 남음", shipped: "출시됨 · 답 보냄", waiting: "기다림", rejected: "반려", closed: "닫힘",
};

/** 다음에 움직일 쪽. */
export const TURNS = { claude: "Claude 차례", owner: "최고 관리자 확인", admin: "관리자", asker: "문의한 분 답 기다림", ship: "출시 기다림" };

/** 보기(거르기). 설계 2장 '보는 법'. */
export const VIEWS = { today: "오늘 할 것", claude: "Claude 차례", owner: "최고 관리자 확인", dev: "개발 중", ship: "출시 대기", late: "기한 넘음",
  open: "열린 것 모두", closed: "닫힘", all: "모두" };

/** 줄의 종류(할 일함 종류에 장부만의 이름을 얹어요). 문의는 kind로 더 나눠요. */
const LINE_KINDS = { bug: "문제", question: "질문", idea: "제안", company: "회사 방식", other: "기타", studio: "스튜디오 요구", billing: "결제·환불" };
export const LEDGER_TYPES = { ...WORK_TYPES, store_review: "스토어 리뷰", market_broken: "깨진 작품" };
/** 2.0.0 [RVA-50] 검토 U23-14·I23b-03: 스토어 리뷰도 '문의·요구' 묶음의 한 줄이에요. */
const LEDGER_GROUPS = { ...WORK_GROUPS, ask: [...WORK_GROUPS.ask, "store_review"], report: [...WORK_GROUPS.report, "market_broken"] };

/**
 * 1.38 [DESK-02] 설계 5-3: Claude가 혼자 보내지 않고 최고 관리자에게 묻는 것. 말로만 가려요(새로 남기는 것 없음).
 * 이름은 장부의 '답' 칸에 그대로 보여요. 1.40 [RVD-07] 가리는 식은 src/ask-check.js 한 곳(예문 장부 test/ask-corpus.json).
 */
export function replyRisk(text) {
  return replyRiskOf(text);
}

/** 문의 하나가 최고 관리자 확인이 필요한지: { key, name } 또는 null. 회사 방식은 회사 이름이 나와서 늘 물어요. */
export function askOwner(kind, text) {
  if (kind === "company") return { key: "company", name: "회사 방식 공개" };
  // 2.0.0 [RVQ-30] 앱의 '결제·환불'로 보낸 문의는 글과 상관없이 사람이 먼저 봐요.
  if (kind === "billing") return { key: "money", name: "환불·결제·보상" };
  return askOwnerOf(text);
}

/** 앱이 문의 앞에 붙이는 화면 경로 '[설정 > 알람]'. */
export function screenPath(text) {
  const m = String(text ?? "").match(/^\s*\[([^\]\n]{1,40})\]/);
  return m ? m[1].trim() : null;
}

const ledgerDay = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);
const clip = (t, n) => {
  const c = [...String(t ?? "").replace(/\s+/g, " ").trim()];
  return c.length > n ? `${c.slice(0, n - 1).join("")}…` : c.join("");
};
const vOf = (v) => {
  const x = version(v);
  return x ? x.join(".") : null;
};
/** 기록에 남은 이름이 사람인지 Claude인지 자동인지. */
const whoKind = (name) => (name === "Claude" ? "claude" : !name || name === "자동 정리" || name === "앱" ? "auto" : "person");

/**
 * 1.38 [DESK-02] 겹침 후보: 이전 개발사항(모든 상태)·전에 답한 문의·자주 쓰는 답 중 비슷한 것 3개까지, 이유와 함께.
 * 같은 주제면 조금, 같은 화면 경로면 조금 더해요(설계 3장 '화면 경로 가산'). 이미 묶인 개발사항은 빼요.
 */
export function overlaps(q, k, skip = null) {
  const g = grams(String(q.text ?? "").replace(/^\s*\[[^\]\n]{1,40}\]\s*/, ""));
  const topic = triage(q.kind, q.text, q.info).topic;
  const path = screenPath(q.text);
  const found = [];
  for (const d of k.devs) {
    if (d.id === skip) continue;
    const byText = likeness(g, d.g);
    // 1.39 [DESK-05] 출시 노트 후보: 그 개발사항의 업데이트 소식 한 줄과 더 닮았으면 그 점수와 '소식' 이유로.
    const byNews = d.ng ? likeness(g, d.ng) : 0;
    const s = Math.max(byText, byNews);
    if (s < LEDGER.overlapFloor) continue;
    const gone = d.status === "done" && d.released_in && isOut(d.released_in, k.shipped);
    const why = [byNews > byText ? `${gone ? `${d.released_in} 업데이트 소식` : "나갈 소식"} ‘${clip(d.news, 30)}’ ${Math.round(byNews * 100)}%` : `글 ${Math.round(s * 100)}%`];
    why.push(d.status === "done" && d.released_in ? (gone ? `${d.released_in}에서 고침` : `${d.released_in} 출시 전`) : DEV.statuses[d.status] ?? d.status);
    found.push({ type: "dev", id: d.id, title: d.title, status: d.status, releasedIn: d.released_in ?? null, ...(byNews > byText ? { note: d.news } : {}), score: s, why });
  }
  for (const a of k.answered) {
    if (a.id === q.id) continue;
    let s = likeness(g, a.g, a.topic === topic);
    if (s < LEDGER.overlapFloor) continue;
    const why = [`글 ${Math.round(likeness(g, a.g) * 100)}%`];
    if (a.topic === topic && topic !== "etc") why.push(`같은 주제(${TOPIC_NAMES[topic]})`);
    if (path && screenPath(a.first) === path) {
      s = Math.min(1, s + LEDGER.pathBonus);
      why.push(`같은 화면 [${path}]`);
    }
    found.push({ type: "inquiry", id: a.id, title: titleFrom(a.first), score: s, why: [...why, "전에 답함"] });
  }
  for (const t of k.templates) {
    const s = likeness(g, t.g, t.topic === topic);
    if (s < LEDGER.overlapFloor) continue;
    found.push({ type: "template", id: t.id, title: t.label, score: s, why: [`글 ${Math.round(s * 100)}%`, "자주 쓰는 답"] });
  }
  return found.sort((a, b) => b.score - a.score).slice(0, LEDGER.overlaps).map((o) => ({ ...o, score: Math.round(o.score * 100) / 100, why: o.why.join(" · ") }));
}

async function linesOf(env, keys, ats) {
  const have = new Map();
  const parts = [];
  for (let i = 0; i < keys.length; i += 90) parts.push(keys.slice(i, i + 90));
  // 1.39 [ADM3-52] 조각마다 차례로가 아니라 한꺼번에.
  for (const got of await Promise.all(parts.map((part) => all(env, `SELECT key, n, summary, summary_by, summary_at FROM ledger_lines WHERE key IN (${part.map(() => "?").join(",")})`, ...part)))) {
    for (const r of got) have.set(r.key, r);
  }
  const fresh = keys.filter((k) => !have.has(k)).sort((a, b) => (ats.get(a) ?? 0) - (ats.get(b) ?? 0) || a.localeCompare(b));
  for (let i = 0; i < fresh.length; i += 50) {
    const part = fresh.slice(i, i + 50);
    // 한 묶음 안에서 차례로 들어가니 MAX(n) + 1이 겹치지 않아요.
    await batch(env, part.map((k) => stmt(env, "INSERT OR IGNORE INTO ledger_lines (key, n, at) VALUES (?, (SELECT COALESCE(MAX(n), 0) + 1 FROM ledger_lines), ?)", k, ats.get(k) ?? 0)));
    for (const r of await all(env, `SELECT key, n, summary, summary_by, summary_at FROM ledger_lines WHERE key IN (${part.map(() => "?").join(",")})`, ...part)) have.set(r.key, r);
  }
  return have;
}

const lineNo = (n) => `K-${String(n).padStart(4, "0")}`;

/**
 * 1.38 [DESK-02] 장부의 모든 줄. 열린 것은 모두, 닫힌 것은 [days]일 안에 바뀐 것만. 권한 없는 칸은 읽지도 않아요(할 일함과 같아요).
 * 할 일함 목록을 먼저 읽어 '다시 열림'(끝냈는데 새 움직임·7일 남음)을 맞춰 두고, 문의·개발사항은 장부에서 더 넓게(답한 것·묶인 것·닫힌 것까지) 읽어요.
 */
export async function ledgerList(env, actor, now, { days = LEDGER.days } = {}) {
  const since = now - Math.min(LEDGER.maxDays, Math.max(1, days)) * DAY;
  const asks = can(actor, "inquiries");
  const devs = can(actor, "dev");
  // 1.39 [ADM3-52] 할 일함 목록(다시 열림 맞추기)과 장부 읽기를 한꺼번에(실서버 결정함·장부가 DB를 차례로 오가 3초 넘었어요).
  // 할 일함 상태(work_states)는 둘 다 끝난 뒤에 읽어요.
  const [work, [qs, ds, sla, k, rv]] = await Promise.all([workList(env, actor, now), Promise.all([
    asks ? all(env, `SELECT i.id, i.kind, i.info, i.device, i.status, i.created_at, i.updated_at, i.same, i.assignee_name, i.handed_in, i.sorted_at,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first,
        (SELECT GROUP_CONCAT(m.text, ' ') FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user') AS words,
        (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last,
        (SELECT m.at FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last_at,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin' ORDER BY m.id DESC LIMIT 1) AS said,
        (SELECT m.by FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin' ORDER BY m.id DESC LIMIT 1) AS said_by,
        (SELECT m.at FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin' ORDER BY m.id DESC LIMIT 1) AS said_at
      FROM inquiries i WHERE i.kind != 'studio' AND (i.status != 'closed' OR i.updated_at > ?) ORDER BY i.updated_at DESC LIMIT ${LEDGER.inquiries}`, since) : [],
    asks || devs ? all(env, `SELECT d.id, d.title, d.body, d.status, d.reason, d.released_in, d.handed_at, d.assignee_name, d.created_by_name, d.created_at, d.updated_at,
        (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks
      FROM dev_items d WHERE d.deleted_at IS NULL AND (d.status IN ('todo', 'doing', 'done') OR d.updated_at > ?)
      ORDER BY d.updated_at DESC LIMIT ${LEDGER.devs}`, since) : [],
    asks ? slaTargets(env) : {},
    asks ? known(env) : null,
    // 2.0.0 [RVA-50] 검토 U23-14·I23b-03: 스토어 리뷰도 장부 한 줄(문의와 한 대기열). 답 안 한 것은 모두, 답한 것은 기간 안에서만.
    asks ? all(env, `SELECT id, stars, text, app, at, reply, reply_at, replied_by, draft, draft_by, status FROM store_reviews
      WHERE status <> 'replied' OR reply_at > ? ORDER BY at DESC LIMIT ${LEDGER.reviews}`, since).catch(() => []) : [],
  ])]);
  // 2.0.0 [RVA-56] 검토 SX-04: 장터 건강의 깨진 작품(마지막 결과)도 장부 줄로. 다음 결과에서 나으면 사라져요.
  const brokenWorks = await marketHealthRows(env, actor).catch(() => []);
  const shipped = k?.shipped ?? null;
  const devById = new Map(ds.map((d) => [d.id, d]));
  // 문의마다 묶인 개발사항(가장 최근 것)과 '나왔어요'를 보낸 때.
  const links = new Map();
  const qIds = qs.map((q) => q.id);
  // 정리함 판단은 묶음 읽기와 함께 시작해요(서로 기다리지 않아요).
  const sortJob = asks ? sortInbox(env, k) : null;
  const parts = [];
  for (let i = 0; i < qIds.length; i += 90) parts.push(qIds.slice(i, i + 90));
  // 2.0.0 [RVQ-34] 검토 U22-03: 서버가 멈춘 답(사람이 '지금 보내기'를 눌러야 가는 보류 답)도 같이 읽어 줄에 보여요.
  const personHeld = new Map();
  const [linkRows, heldRows] = await Promise.all([
    Promise.all(parts.map((part) => all(env, `SELECT l.item_id, l.inquiry_id, l.told_at, l.at FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL
        WHERE l.inquiry_id IN (${part.map(() => "?").join(",")}) ORDER BY l.at`, ...part))),
    Promise.all(parts.map((part) => all(env, `SELECT id, inquiry_id, text, by, ship_item FROM held_replies WHERE until >= ? AND inquiry_id IN (${part.map(() => "?").join(",")}) ORDER BY id`,
      HELD_FOR_PERSON, ...part).catch(() => []))),
  ]);
  for (const got of linkRows) for (const l of got) links.set(l.inquiry_id, l);
  for (const got of heldRows) for (const h of got) if (!personHeld.has(h.inquiry_id)) personHeld.set(h.inquiry_id, h);
  // 묶였지만 장부 범위 밖(오래된) 개발사항도 묶음 칸에는 보여요.
  const missing = [...new Set([...links.values()].map((l) => l.item_id))].filter((id) => !devById.has(id));
  for (let i = 0; i < missing.length; i += 90) {
    const part = missing.slice(i, i + 90);
    for (const d of await all(env, `SELECT d.id, d.title, d.body, d.status, d.reason, d.released_in, d.handed_at, d.assignee_name, d.created_by_name, d.created_at, d.updated_at,
        (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d WHERE d.id IN (${part.map(() => "?").join(",")})`, ...part)) devById.set(d.id, { ...d, outside: true });
  }
  // 정리함 판단(일곱 묶음)은 겹침 판단으로 장부 줄에 들어가요 [DESK-03].
  const sort = sortJob ? await sortJob : null;
  const piles = new Map();
  if (sort) {
    for (const p of Object.keys(sort.piles)) {
      for (const x of sort.piles[p]) {
        const base = { pile: p, pileName: PILE_NAMES[p], match: x.match ?? null, reply: x.reply ?? "", back: Boolean(x.back) };
        if (p === "new") {
          for (const id of x.group) piles.set(id, { ...base, group: x.group, groupTitle: x.title, lead: id === x.id });
        } else piles.set(x.id, base);
      }
    }
  }

  // 2.0.0 [RVA-63] HQ-124: 닫힘 문의의 멈춤 기록과 7일 안 되풀이 묶음. 보낸 분 번호는 묶는 데만 쓰고 줄에는 싣지 않아요.
  const crashes = new Map();
  for (const q of qs) {
    try {
      const info = JSON.parse(q.info || "{}");
      const c = crashOf(q.kind, q.first, info);
      if (c) crashes.set(q.id, { ...c, device: q.device, model: info.model ?? null, android: info.android ?? null, app: info.app ?? null, at: q.created_at });
    } catch { /* 읽지 못한 줄은 아래에서 */ }
  }
  const repeats = crashRepeats([...crashes].map(([id, c]) => ({ id, ...c })), now);
  const places = repeatPlaces(repeats);

  const rows = [];
  // 2.0.0 [RVA-37] 검토 I24b-02: 한 줄을 읽다 오류가 나면 그 줄만 '읽지 못한 줄'로 두고 장부는 계속(ADMX-01처럼 문의 하나가 장부·오늘을 멈추지 않게).
  const bad = [];
  const unreadable = (key, type, id, e, extra = {}) => {
    bad.push({ key, e });
    rows.push({ key, type, id, kind: null, kindName: type === "dev" ? "개발사항" : "문의", title: type === "dev" ? "(읽지 못한 개발사항)" : "(읽지 못한 문의)",
      text: "", path: null, app: null, same: 0, stage: "intake", dueAt: null, unreadable: errorLine(e), ...extra });
  };
  for (const q of qs) {
    try {
      const info = JSON.parse(q.info || "{}");
      const text = q.first ?? "";
      const link = links.get(q.id) ?? null;
      const d = link ? devById.get(link.item_id) ?? null : null;
      // 2.0.0 [RVQ-34] 검토 SVC-28: 서버(needsPerson)처럼 문의한 분의 글 전부로 판정해요(첫 글만 보면 이어 쓴 '환불해 주세요'를 놓쳤어요).
      const owner = askOwner(q.kind, q.words ?? text);
      const tri = triage(q.kind, text, info);
      const heldOne = personHeld.get(q.id);
      const held = heldOne ? { id: heldOne.id, text: clip(heldOne.text, 600), by: heldOne.by, ship: Boolean(heldOne.ship_item),
        why: owner?.name ?? replyRisk(heldOne.text) ?? "보류한 뒤 새 글·다른 답" } : null;
      const waitingAdmin = (q.status === "open" && q.last === "user") || Boolean(held);
      let stage;
      if (q.status === "closed") stage = "closed";
      else if (d) {
        if (d.status === "rejected") stage = "rejected";
        else if (d.status === "todo") stage = d.handed_at ? "handed" : "devwait";
        else if (d.status === "doing") stage = "doing";
        else if (d.status === "done" && !isOut(d.released_in, shipped)) stage = "built";
        else stage = link.told_at || q.last === "admin" ? "shipped" : "notify";
      } else if (q.last === "admin") stage = "answered";
      else stage = piles.has(q.id) ? "sorted" : "intake";
      rows.push({
        key: `inquiry:${q.id}`, type: "inquiry", id: q.id, kind: q.kind, kindName: LINE_KINDS[q.kind] ?? q.kind, topic: tri.topic, topicName: tri.topicName,
        urgent: tri.urgency === "urgent", title: titleFrom(text), text: clip(text, 600), path: screenPath(text), app: vOf(info.app), model: info.model ?? null,
        at: q.created_at, last: q.last_at ?? q.updated_at, same: q.same ?? 0, status: q.status,
        waitingAdmin, ownerAsk: owner, held, handedIn: q.handed_in ?? null, skipped: q.sorted_at ? { at: q.sorted_at, back: piles.get(q.id)?.back ?? false } : null,
        sort: piles.get(q.id) ?? null,
        bundle: d ? { id: d.id, title: d.title, status: d.status, statusName: DEV.statuses[d.status] ?? d.status, releasedIn: d.released_in ?? null,
          out: d.status === "done" && isOut(d.released_in, shipped), asks: d.asks, told: Boolean(link.told_at) } : null,
        said: q.said ? { text: clip(q.said, 300), by: q.said_by ?? "", at: q.said_at } : null,
        assignee: q.assignee_name ?? null, stage,
        dueAt: waitingAdmin && (!d || stage === "notify") ? Math.min(q.updated_at + (sla[slaKey(q.kind)] ?? 24) * HOUR, owner?.key === "law" ? q.updated_at + LEDGER.safetyHours * HOUR : Infinity) : null,
        overlapsOf: waitingAdmin && !d ? { text, kind: q.kind, info, id: q.id } : null,
        // [RVA-69] reply: 2.0.0부터는 기록 부탁을 답장(점검 기록도 보내기)으로.
        crash: crashes.has(q.id) ? { record: crashes.get(q.id).record, recordName: crashes.get(q.id).recordName,
          ...(crashes.get(q.id).record !== "has" && replyCarriesRecord(crashes.get(q.id).app) ? { reply: true } : {}) } : null,
        repeat: places.get(q.id) ?? null,
        // 2.0.0 [RVA-64] HQ-132: 점검 기록은 내용 없이 표식만(열쇠·Claude 장부도 '왔는지·멈춤 기록 있는지'를 알게).
        diag: diagMark(info.diag),
      });
    } catch (e) {
      unreadable(`inquiry:${q.id}`, "inquiry", q.id, e, { at: q.created_at, last: q.updated_at, status: q.status });
    }
  }
  for (const d of devById.values()) {
    if (d.outside) continue;
    try {
      let stage;
      if (d.status === "rejected") stage = "rejected";
      else if (d.status === "todo") stage = d.handed_at ? "handed" : "devwait";
      else if (d.status === "doing") stage = "doing";
      else if (d.status === "done" && !isOut(d.released_in, shipped)) stage = "built";
      else stage = "closed";
      // 오래전에 나간 개발사항은 닫힌 것으로 기간 안에서만.
      if (stage === "closed" && d.updated_at <= since) continue;
      rows.push({ key: `dev:${d.id}`, type: "dev", id: d.id, kind: null, kindName: "개발사항", title: d.title, text: clip(d.body, 600), path: null,
        app: null, at: d.created_at, last: d.updated_at, same: 0, status: d.status, releasedIn: d.released_in ?? null, out: d.status === "done" && isOut(d.released_in, shipped),
        bundle: { id: d.id, title: d.title, status: d.status, statusName: DEV.statuses[d.status] ?? d.status, releasedIn: d.released_in ?? null,
          out: d.status === "done" && isOut(d.released_in, shipped), asks: d.asks },
        handed: Boolean(d.handed_at), reason: d.reason || "", assignee: d.assignee_name ?? null, createdBy: d.created_by_name, stage, dueAt: null });
    } catch (e) {
      unreadable(`dev:${d.id}`, "dev", d.id, e, { at: d.created_at, last: d.updated_at, status: d.status });
    }
  }
  for (const v of rv) {
    try {
      const replied = v.status === "replied";
      const low = v.stars <= REVIEWS.urgentStars;
      rows.push({ key: `store_review:${v.id}`, type: "store_review", id: v.id, kind: null, kindName: "스토어 리뷰", stars: v.stars,
        title: `별 ${v.stars}개 · ${v.text ? titleFrom(v.text) : "(글 없이 별점만)"}`, text: clip(v.text, 600), path: null, app: v.app || null,
        at: v.at, last: v.reply_at ?? v.at, same: 0, status: v.status, link: "#/reviews",
        review: { draft: clip(v.draft, REVIEWS.replyMax), draftBy: v.draft_by || "" },
        said: replied && v.reply_at ? { text: clip(v.reply, 300), by: v.replied_by || "", at: v.reply_at } : null,
        // 답 초안이 있으면 Play로 보내기(최고 관리자)만 남아요.
        stage: replied ? "answered" : v.draft || v.status === "seen" ? "sorted" : "intake",
        dueAt: !replied && low ? v.at + LEDGER.reviewHours * HOUR : null });
    } catch (e) {
      unreadable(`store_review:${v.id}`, "store_review", v.id, e, { at: v.at, last: v.at, status: v.status, kindName: "스토어 리뷰", title: "(읽지 못한 리뷰)" });
    }
  }
  rows.push(...brokenWorks);
  for (const x of work) {
    if (x.type === "inquiry" || x.type === "dev") continue;
    rows.push({ key: x.key, type: x.type, id: x.id, kind: null, kindName: x.typeName, title: x.title, text: "", path: null, app: null,
      at: Date.parse(x.at), last: Date.parse(x.at), same: 0, n: x.n, meta: x.meta, link: x.link, assignee: x.assignee?.name ?? null,
      stage: "intake", dueAt: x.due ? Date.parse(x.due) : null });
  }

  // 할 일함 상태(같은 표 work_states)와 줄 번호.
  const keys = rows.map((r) => r.key);
  const states = new Map();
  const keyParts = [];
  for (let i = 0; i < keys.length; i += 90) keyParts.push(keys.slice(i, i + 90));
  const [stateRows, lines] = await Promise.all([
    Promise.all(keyParts.map((part) => all(env, `SELECT * FROM work_states WHERE key IN (${part.map(() => "?").join(",")})`, ...part))),
    linesOf(env, keys, new Map(rows.map((r) => [r.key, r.at]))),
  ]);
  for (const got of stateRows) for (const r of got) states.set(r.key, r);
  const kstEnd = Date.parse(`${ledgerDay(now)}T00:00:00+09:00`) + DAY;
  const out = rows.map((r) => {
    try {
      return lineOut(r);
    } catch (e) {
      bad.push({ key: r.key, e });
      return { key: r.key, id: r.id, no: lines.get(r.key) ? lineNo(lines.get(r.key).n) : null, n: lines.get(r.key)?.n ?? 0, type: r.type,
        typeName: Object.hasOwn(LEDGER_TYPES, r.type) ? LEDGER_TYPES[r.type] : "줄", kind: null, kindName: "", group: "ops", title: "(읽지 못한 줄)", summary: "",
        summaryBy: "", text: "", path: null, app: null, model: null, topic: null, topicName: null, urgent: false, same: 0, n2: 0, meta: null, link: null,
        at: iso(Number(r.at) || now), last: iso(Number(r.last) || Number(r.at) || now), age: 0, overlap: [], sort: null, skipped: null, handedIn: null, handed: false,
        bundle: null, releasedIn: null, reason: "", stage: "intake", stageName: STAGES.intake, answer: null, ownerAsk: null, turn: "owner", turnName: TURNS.owner,
        holder: null, due: null, late: false, dueToday: false, state: "new", stateName: WORK_STATE_NAMES.new, stateReason: "", stateReasonName: "", note: "",
        reopened: 0, closed: false, lastMove: null, unreadable: errorLine(e) };
    }
  });
  if (bad.length) await recordRowErrors(env, "ledger", bad, now);
  // 묶음마다 줄 번호(K-…)를 붙여 화면이 그 줄로 바로 갈 수 있게.
  const noOf = new Map(out.map((r) => [r.key, r.no]));
  const groups = repeats.map((g) => ({ ...g, first: iso(g.first), last: iso(g.last), lines: g.ids.map((id) => ({ key: `inquiry:${id}`, no: noOf.get(`inquiry:${id}`) ?? null })) }));
  return { rows: out, shipped, auto: sort?.auto ?? null, unreadable: bad.length, crashRepeats: groups };

  function lineOut(r) {
    const s = states.get(r.key);
    const state = s?.state ?? "new";
    const line = lines.get(r.key);
    let stage = r.stage;
    // 원래 칸에서 끝난 것이 아니라 장부에서 끝낸 줄은 닫힘(이유와 함께). 기다림·보는 중은 단계 위에 얹어 보여요.
    if (state === "done" && !["shipped", "rejected"].includes(stage)) stage = "closed";
    else if (r.type !== "inquiry" && r.type !== "dev") stage = state === "waiting" ? "waiting" : state === "seeing" ? "sorted" : stage;
    else if (stage === "intake" && state === "seeing") stage = "sorted";
    // 문의는 답을 보내 '답함'이 된 것, 묶인 개발사항이 반려됐거나 나가서 안내까지 간 것이 닫힘이에요. 개발이 남은 문의는 답을 받았어도 열려 있어요.
    const closed = stage === "closed" || (r.type === "inquiry" ? r.status !== "open" && ["answered", "rejected", "shipped"].includes(stage)
      : r.type === "store_review" ? r.status === "replied" : stage === "rejected");
    // 다음에 움직일 쪽.
    let turn = null;
    if (!closed && state !== "done") {
      if (r.type === "inquiry") {
        if (stage === "built") turn = "ship";
        else if (["devwait", "handed", "doing"].includes(stage)) turn = "claude";
        else if (r.held) turn = "owner";
        else if (r.waitingAdmin) turn = r.ownerAsk ? "owner" : "claude";
        else if (stage === "notify") turn = "claude";
      } else if (r.type === "dev") turn = stage === "built" ? "ship" : ["devwait", "handed", "doing"].includes(stage) ? "claude" : null;
      else if (r.type === "studio_need") turn = "claude";
      // [RVA-50] 리뷰: 초안은 Claude(열쇠로 POST /reviews/<id>/draft), Play로 보내기는 최고 관리자.
      else if (r.type === "store_review") turn = r.review?.draft ? "owner" : "claude";
      // [RVA-56] 깨진 작품은 개발(스튜디오·장터 갈래)이 고쳐요.
      else if (r.type === "market_broken") turn = "claude";
      else turn = "admin";
      if (state === "waiting") turn = s.reason === "user" ? "asker" : s.reason === "dev" ? "claude" : turn;
      // 2.0.0 [RVA-37] 읽지 못한 줄은 사람이 봐요(Claude·자동 답이 손대지 않게, 오늘 보기에 올라와요).
      if (r.unreadable) turn = "owner";
    }
    // 담당: 누가 잡고 있으면 그 사람, 아니면 차례.
    const holder = s && state !== "new" && state !== "done" && s.by_name ? s.by_name : r.assignee && whoKind(r.assignee) === "person" ? r.assignee : null;
    const due = state === "waiting" ? s.at + WORK.backDays * DAY : r.dueAt;
    const lastMove = [
      s ? { at: s.at, by: s.by_name || "자동", what: s.state === "done" ? `끝냄 · ${DONE_REASONS[s.reason] ?? s.reason}` : WORK_STATE_NAMES[s.state] } : null,
      r.said ? { at: r.said.at, by: r.said.by || "운영자", what: "답 보냄" } : null,
      line?.summary_at ? { at: line.summary_at, by: line.summary_by, what: "요약" } : null,
      r.type === "dev" ? { at: r.last, by: r.assignee || r.createdBy || "", what: DEV.statuses[r.status] ?? r.status } : null,
    ].filter(Boolean).sort((a, b) => b.at - a.at)[0] ?? null;
    const answer = r.type === "store_review"
      ? r.said ? { key: "sent", name: "보냄", text: r.said.text, by: r.said.by, byKind: whoKind(r.said.by), at: iso(r.said.at) }
        : r.review?.draft ? { key: "draft", name: "초안 있음", text: r.review.draft, by: r.review.draftBy, byKind: whoKind(r.review.draftBy) }
        : { key: "none", name: "안 함" }
      : r.type !== "inquiry" ? null
      // 2.0.0 [RVQ-34] 검토 U22-03: 적어 둔 답이 사람 확인을 기다려요(돈·개인정보… 또는 보류한 뒤 바뀐 것). 장부에서 바로 '지금 보내기'.
      : r.held ? { key: "person", name: "사람 확인 대기", why: r.held.why, text: r.held.text, by: r.held.by, held: r.held.id, ship: r.held.ship }
      : r.said && (!r.waitingAdmin) ? { key: "sent", name: "보냄", text: r.said.text, by: r.said.by, byKind: whoKind(r.said.by), at: iso(r.said.at) }
      : r.ownerAsk && r.waitingAdmin ? { key: "owner", name: "최고 관리자 확인", why: r.ownerAsk.name, ...(r.said ? { before: r.said.text } : {}) }
      : { key: "none", name: r.said ? "새 말에 아직" : "안 함", ...(r.said ? { before: r.said.text, by: r.said.by } : {}) };
    return {
      key: r.key, id: r.id, no: line ? lineNo(line.n) : null, n: line?.n ?? 0, type: r.type, typeName: LEDGER_TYPES[r.type] ?? r.type, kind: r.kind, kindName: r.kindName,
      group: Object.keys(LEDGER_GROUPS).find((g) => LEDGER_GROUPS[g].includes(r.type)) ?? "ops", ...(r.stars ? { stars: r.stars } : {}),
      title: r.title, summary: line?.summary || "", summaryBy: line?.summary_by || "", text: r.text, path: r.path, app: r.app, model: r.model ?? null,
      topic: r.topic ?? null, topicName: r.topicName ?? null, urgent: Boolean(r.urgent), same: r.same, n2: r.n ?? 0, meta: r.meta ?? null, link: r.link ?? null,
      at: iso(r.at), last: iso(Math.max(r.last ?? r.at, lastMove?.at ?? 0)), age: Math.max(0, Math.floor((now - r.at) / HOUR)),
      overlap: r.overlapsOf && k ? overlaps(r.overlapsOf, k, r.bundle?.id) : [],
      sort: r.sort ?? null, skipped: r.skipped ? { at: iso(r.skipped.at), back: r.skipped.back } : null, handedIn: r.handedIn ?? null, handed: r.handed ?? false,
      bundle: r.bundle ?? null, releasedIn: r.releasedIn ?? r.bundle?.releasedIn ?? null, reason: r.reason ?? "",
      stage, stageName: STAGES[stage], answer, ownerAsk: r.ownerAsk ?? null,
      turn, turnName: turn ? TURNS[turn] : null, holder,
      due: due ? iso(due) : null, late: Boolean(turn && due && due < now), dueToday: Boolean(turn && due && due < kstEnd),
      state, stateName: WORK_STATE_NAMES[state], stateReason: s && state !== "new" ? s.reason : "",
      stateReasonName: state === "waiting" ? WAIT_REASONS[s?.reason] ?? "" : state === "done" ? DONE_REASONS[s?.reason] ?? "" : "",
      note: s?.note ?? "", reopened: s?.reopened ?? 0, closed: closed || state === "done",
      lastMove: lastMove ? { at: iso(lastMove.at), by: lastMove.by, byKind: whoKind(lastMove.by), what: lastMove.what } : null,
      ...(r.unreadable ? { unreadable: r.unreadable } : {}),
      ...(r.crash ? { crash: r.crash } : {}), ...(r.repeat ? { repeat: r.repeat } : {}), ...(r.diag ? { diag: r.diag } : {}),
    };
  }
}

/** 오래된 것이 위로: 기한 넘김 → 다시 열림 → 최고 관리자 확인 → 기한 가까운 순 → 오래된 순. 닫힌 것은 최근 것이 위로. */
export function ledgerOrder(a, b) {
  if (a.closed !== b.closed) return a.closed ? 1 : -1;
  if (a.closed) return b.last.localeCompare(a.last);
  return Number(b.late) - Number(a.late) || Number(b.reopened > 0 && b.state === "new") - Number(a.reopened > 0 && a.state === "new")
    || Number(b.turn === "owner") - Number(a.turn === "owner") || Number(b.urgent) - Number(a.urgent)
    || (a.due && b.due ? a.due.localeCompare(b.due) : a.due ? -1 : b.due ? 1 : a.at.localeCompare(b.at));
}

/** 보기 하나에 드는지. */
export function inView(r, view, now) {
  const fresh = Date.parse(r.at) > now - DAY;
  switch (view) {
    case "today": return !r.closed && Boolean(r.turn) && !["asker", "ship"].includes(r.turn) && (r.late || r.dueToday || (r.reopened > 0 && r.state === "new") || fresh || r.turn === "owner");
    case "claude": return !r.closed && r.turn === "claude";
    case "owner": return !r.closed && r.turn === "owner";
    case "dev": return !r.closed && ["devwait", "handed", "doing"].includes(r.stage);
    case "ship": return !r.closed && (r.stage === "built" || r.stage === "notify");
    case "late": return !r.closed && r.late;
    case "closed": return r.closed;
    case "all": return true;
    default: return !r.closed;
  }
}

/** 판 거르기: 문의는 앱 판, 개발사항은 고친 판. '1.37'처럼 앞만 적어도 돼요. */
const inVersion = (r, v) => !v || [r.app, r.releasedIn].some((x) => x && (x === v || x.startsWith(`${v}.`)));

function matches(r, q) {
  if (!q) return true;
  const t = q.toLowerCase();
  return [r.no, r.title, r.summary, r.text, r.path, r.id, r.bundle?.id, r.bundle?.title].some((x) => x && String(x).toLowerCase().includes(t));
}

/** 거른 줄과 숫자. */
export async function ledgerQuery(env, actor, now, params) {
  const view = VIEWS[params.get("view")] ? params.get("view") : "today";
  const type = LEDGER_TYPES[params.get("type")] ? params.get("type") : null;
  const group = LEDGER_GROUPS[params.get("group")] ? params.get("group") : null;
  const v = /^\d{1,2}(\.\d{1,2}){0,2}$/.test(params.get("v") ?? "") ? params.get("v") : null;
  const q = String(params.get("q") ?? "").trim().slice(0, 60);
  const days = Number(params.get("days")) || LEDGER.days;
  const { rows, shipped, auto, crashRepeats: repeats } = await ledgerList(env, actor, now, { days });
  const open = rows.filter((r) => !r.closed);
  const counts = Object.fromEntries(Object.keys(VIEWS).map((k) => [k, rows.filter((r) => inView(r, k, now)).length]));
  const items = rows.filter((r) => inView(r, view, now) && (!type || r.type === type) && (!group || r.group === group) && inVersion(r, v) && matches(r, q)).sort(ledgerOrder);
  const groups = Object.fromEntries(Object.keys(LEDGER_GROUPS).map((g) => [g, rows.filter((r) => inView(r, view, now) && r.group === g).length]));
  const versions = [...new Set(rows.flatMap((r) => [r.app, r.releasedIn]).filter(Boolean))]
    .sort((a, b) => compareVersions(version(b), version(a))).slice(0, 12);
  const start = Date.parse(`${ledgerDay(now)}T00:00:00+09:00`);
  const doneToday = await all(env, "SELECT by_name, COUNT(*) AS n FROM work_log WHERE action = 'done' AND at >= ? GROUP BY by_name ORDER BY n DESC", start);
  return {
    now: iso(now), view, type, group, v, q, days: Math.min(LEDGER.maxDays, Math.max(1, days)), shipped, auto,
    counts: { ...counts, groups, open: open.length, inToday: rows.filter((r) => Date.parse(r.at) >= start).length,
      doneToday: doneToday.reduce((n, r) => n + r.n, 0), byWho: doneToday.map((r) => ({ name: r.by_name || "앱", n: r.n })) },
    total: items.length, items, versions,
    // 2.0.0 [RVA-63] HQ-124: 운영 장부 맨 위 '같은 판에서 닫힘 되풀이'.
    crashRepeats: repeats ?? [], crashDraft: CRASH_ASK_DRAFT, crashDraftReply: CRASH_ASK_DRAFT_REPLY,
    names: { views: VIEWS, stages: STAGES, turns: TURNS, states: WORK_STATE_NAMES, wait: WAIT_REASONS, done: DONE_REASONS, types: LEDGER_TYPES, groups: WORK_GROUP_NAMES,
      piles: PILE_NAMES },
    rules: { backDays: WORK.backDays, safetyHours: LEDGER.safetyHours, skipBackDays: 7 },
  };
}

// ---- CSV [DESK-02] ----

const CSV_COLS = [["번호", (r) => r.no], ["들어온 날", (r) => r.at.slice(0, 10)], ["종류", (r) => r.kindName], ["요약", (r) => r.summary || r.title],
  ["화면 경로", (r) => r.path], ["앱 판", (r) => r.app], ["겹침", (r) => r.overlap.map((o) => `${o.title}(${Math.round(o.score * 100)}%)`).join(" / ")],
  ["묶음", (r) => (r.bundle && r.type === "inquiry" ? `${r.bundle.id} ${r.bundle.title}` : r.type === "dev" ? `문의 ${r.bundle?.asks ?? 0}건` : "")],
  ["단계", (r) => r.stageName], ["상태", (r) => `${r.stateName}${r.stateReasonName ? ` · ${r.stateReasonName}` : ""}`],
  ["답", (r) => (r.answer ? `${r.answer.name}${r.answer.why ? `(${r.answer.why})` : ""}` : "")], ["담당", (r) => r.holder ?? r.turnName ?? ""],
  ["기한", (r) => (r.due ? r.due.slice(0, 16).replace("T", " ") : "")], ["기한 넘음", (r) => (r.late ? "예" : "")],
  ["마지막 기록", (r) => (r.lastMove ? `${r.lastMove.at.slice(0, 10)} ${r.lastMove.by} ${r.lastMove.what}` : "")], ["메모", (r) => r.note]];

const csvCell = (v) => {
  const t = String(v ?? "");
  // 셀 수식으로 읽히지 않게(=, +, -, @로 시작하면 앞에 ').
  const safe = /^[=+\-@\t\r]/.test(t) ? `'${t}` : t;
  return /[",\n\r]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
};

export function ledgerCsv(items) {
  return `﻿${[CSV_COLS.map(([h]) => h).join(","), ...items.map((r) => CSV_COLS.map(([, f]) => csvCell(f(r))).join(","))].join("\r\n")}\r\n`;
}

// ---- 기록과 되돌리기 ----

async function lineLog(env, key) {
  const [type, id] = key.split(":");
  const out = [];
  const add = (at, by, what, extra = {}) => out.push({ at: iso(at), by: by || "자동", byKind: whoKind(by), what, ...extra });
  const work = await all(env, "SELECT * FROM work_log WHERE key = ? ORDER BY id DESC LIMIT 60", key);
  for (const w of work) {
    const what = w.action === "reopen" ? `다시 열림 · ${w.reason === "moved" ? "끝낸 뒤 새 움직임" : `${WORK.backDays}일 지나도 남아 있음`}`
      : w.action === "summary" ? `요약 · ${w.note}` : w.action === "undo" ? `되돌림 · ${WORK_STATE_NAMES[w.before] ?? w.before}에서`
      : `${WORK_STATE_NAMES[w.action] ?? w.action}${DONE_REASONS[w.reason] || WAIT_REASONS[w.reason] ? ` · ${DONE_REASONS[w.reason] ?? WAIT_REASONS[w.reason]}` : ""}${w.note ? ` · ${w.note}` : ""}`;
    add(w.at, w.by_name, what, { log: w.id });
  }
  if (type === "inquiry") {
    const [msgs, notes, ls] = await Promise.all([
      all(env, "SELECT sender, text, by, at FROM inquiry_messages WHERE inquiry_id = ? ORDER BY id DESC LIMIT 40", id),
      all(env, "SELECT by, text, at FROM inquiry_notes WHERE inquiry_id = ? ORDER BY id DESC LIMIT 20", id),
      all(env, "SELECT l.item_id, l.by, l.at, l.told_at, d.title FROM dev_links l JOIN dev_items d ON d.id = l.item_id WHERE l.inquiry_id = ?", id),
    ]);
    for (const m of msgs) add(m.at, m.sender === "user" ? "문의한 분" : m.by || "운영자", m.sender === "user" ? `문의 · ${clip(m.text, 120)}` : `답 · ${clip(m.text, 120)}`,
      m.sender === "user" ? { byKind: "asker" } : {});
    for (const n of notes) add(n.at, n.by, `메모 · ${clip(n.text, 120)}`);
    for (const l of ls) {
      add(l.at, l.by, `개발사항에 묶음 · ${l.item_id} ${clip(l.title, 60)}`);
      if (l.told_at) add(l.told_at, "자동", "고친 판이 나왔다고 알림");
    }
  }
  if (type === "dev") {
    for (const h of await all(env, "SELECT at, actor, action, detail FROM dev_history WHERE item_id = ? ORDER BY id DESC LIMIT 40", id)) {
      let d = {};
      try { d = JSON.parse(h.detail); } catch { /* 옛 기록 */ }
      const what = { create: "만듦", status: `상태 · ${DEV.statuses[d.to] ?? d.to ?? ""}${d.version ? ` ${d.version}` : ""}`, link: "문의 묶음", unlink: "문의 풀기", note: `기록 · ${clip(d.text ?? d.note ?? "", 120)}`,
        edit: "고침", delete: "지움", restore: "되살림", assign: "맡음", handed: "Claude에게 넘김", reply: "묶인 문의에 답" }[h.action] ?? h.action;
      add(h.at, h.actor, what);
    }
  }
  out.sort((a, b) => b.at.localeCompare(a.at));
  // 마지막 상태 바꿈 하나만 되돌릴 수 있어요.
  const undo = work.find((w) => !["reopen", "summary"].includes(w.action));
  return { key, items: out.slice(0, 80), undo: undo ? { log: undo.id, to: undo.action === "undo" ? null : undo.before, what: WORK_STATE_NAMES[undo.action] ?? undo.action } : null };
}

/**
 * 이 관리자(또는 열쇠)가 볼 수 있는 줄인지. 2.0.0 [RVQ-52] 검토 SVC-22: 줄 기록·상태·되돌리기·요약을 부를 때마다 장부 전체(문의 수백 줄과 정리함
 * 판단)를 다시 만들던 것을, 그 줄 번호로 필요한 줄만 봐요. 문의·개발사항은 장부와 같은 조건(권한·스튜디오 요구 아님·닫힌 지 오래되지 않음)을
 * 그 id로만 묻고, 그 밖의 할 일(신고·작품 등)은 할 일함 목록만 읽어요.
 */
async function visible(env, actor, now, keys) {
  const since = now - LEDGER.maxDays * DAY;
  const ok = new Set();
  const ids = (type) => keys.filter((k) => k.startsWith(`${type}:`)).map((k) => k.slice(type.length + 1));
  const marks = (list) => list.map(() => "?").join(",");
  const inq = can(actor, "inquiries") ? ids("inquiry").slice(0, 90) : [];
  if (inq.length) {
    for (const r of await all(env, `SELECT id FROM inquiries WHERE id IN (${marks(inq)}) AND kind != 'studio' AND (status != 'closed' OR updated_at > ?)`, ...inq, since)) ok.add(`inquiry:${r.id}`);
  }
  const dev = can(actor, "inquiries") || can(actor, "dev") ? ids("dev").filter((x) => /^\d{1,12}$/.test(x)).map(Number).slice(0, 90) : [];
  if (dev.length) {
    for (const r of await all(env, `SELECT id FROM dev_items WHERE id IN (${marks(dev)}) AND deleted_at IS NULL AND (status IN ('todo', 'doing', 'done') OR updated_at > ?)`, ...dev, since)) ok.add(`dev:${r.id}`);
  }
  // 2.0.0 [RVA-50] 스토어 리뷰 줄: 장부와 같은 조건(답 안 한 것은 모두, 답한 것은 기간 안)을 그 번호로만.
  const rv = can(actor, "inquiries") ? ids("store_review").slice(0, 90) : [];
  if (rv.length) {
    for (const r of await all(env, `SELECT id FROM store_reviews WHERE id IN (${marks(rv)}) AND (status <> 'replied' OR reply_at > ?)`, ...rv, since).catch(() => [])) {
      ok.add(`store_review:${r.id}`);
    }
  }
  // 2.0.0 [RVA-56] 깨진 작품 줄: 장터 건강의 마지막 결과.
  if (keys.some((k) => k.startsWith("market_broken:"))) for (const x of await marketHealthRows(env, actor).catch(() => [])) ok.add(x.key);
  const own = (k) => ["inquiry:", "dev:", "store_review:", "market_broken:"].some((p) => k.startsWith(p));
  if (keys.some((k) => !own(k))) {
    for (const x of await workList(env, actor, now)) if (x.type !== "inquiry" && x.type !== "dev") ok.add(x.key);
  }
  return keys.filter((k) => ok.has(k));
}

// [RVA-50] Play 리뷰 번호는 'gp:…'처럼 콜론이 있고 길어요.
const KEY_RE = /^[a-z_]{2,20}:[A-Za-z0-9._:-]{1,120}$/;

async function setStates(env, actor, now, keys, state, reason, note, action = state, before = null) {
  const known_ = await all(env, `SELECT key, state, reason FROM work_states WHERE key IN (${keys.map(() => "?").join(",")})`, ...keys);
  const was = new Map(known_.map((r) => [r.key, r.state]));
  const by = actor.name ?? "";
  await batch(env, keys.flatMap((k) => [
    stmt(env, `INSERT INTO work_states (key, state, reason, note, by_id, by_name, at, seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(key) DO UPDATE SET state = excluded.state, reason = excluded.reason, note = excluded.note, by_id = excluded.by_id, by_name = excluded.by_name, at = excluded.at`,
    k, state, reason, note, actor.id ?? "", by, now, now),
    stmt(env, "INSERT INTO work_log (key, action, before, reason, note, by_id, by_name, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", k, action, before ?? was.get(k) ?? "new",
      reason, note, actor.id ?? "", by, now),
  ]));
  return Object.fromEntries(keys.map((k) => [k, was.get(k) ?? "new"]));
}

/** /api/admin/ledger 길. 없는 길이면 null. */
export async function ledgerRoute(request, env, url, path, now) {
  const method = request.method;
  const actor = env.actor;
  if (path === "/ledger" && method === "GET") {
    const d = await ledgerQuery(env, actor, now, url.searchParams);
    const page = Math.max(0, Number(url.searchParams.get("page")) || 0);
    // 2.0.0 [RVA-48] 검토 ADMX-08·I24a-04(도약 42): 관리자 앱은 limit=50으로 50줄씩 이어 받아요(1.79MB를 한 번에 받던 것). 열쇠·CSV는 그대로 300줄·모두.
    const size = Math.min(LEDGER.page, Math.max(10, Number(url.searchParams.get("limit")) || LEDGER.page));
    const more = d.items.length > (page + 1) * size;
    return json({ ...d, items: d.items.slice(page * size, (page + 1) * size), more, next: more ? page + 1 : null, page, size,
      lateTotal: d.items.filter((r) => r.late).length });
  }
  if (path === "/ledger.csv" && method === "GET") {
    const d = await ledgerQuery(env, actor, now, url.searchParams);
    return new Response(ledgerCsv(d.items), { headers: { "content-type": "text/csv; charset=utf-8",
      "content-disposition": `attachment; filename="bamnat-ledger-${ledgerDay(now)}.csv"`, "cache-control": "no-store" } });
  }
  if (path === "/ledger/log" && method === "GET") {
    const key = url.searchParams.get("key") ?? "";
    if (!KEY_RE.test(key) || !(await visible(env, actor, now, [key])).length) return json({ error: "not_found" }, 404);
    return json(await lineLog(env, key));
  }
  if (path === "/ledger/state" && method === "POST") {
    // {keys, state, reason, note}: 할 일함과 같은 규칙(끝낼 때·기다릴 때 이유는 꼭).
    const body = await readJson(request, 16 * 1024);
    const keys = Array.isArray(body.keys) ? [...new Set(body.keys.filter((k) => typeof k === "string" && KEY_RE.test(k)))].slice(0, 100) : [];
    if (!keys.length) return json({ error: "keys", field: "keys" }, 400);
    if (!["new", "seeing", "waiting", "done"].includes(body.state)) return json({ error: "state", field: "state" }, 400);
    const reason = typeof body.reason === "string" ? body.reason : "";
    if (body.state === "done" && !DONE_REASONS[reason]) return json({ error: "reason", field: "reason" }, 400);
    if (body.state === "waiting" && !WAIT_REASONS[reason]) return json({ error: "reason", field: "reason" }, 400);
    const note = typeof body.note === "string" ? [...body.note.trim()].slice(0, 300).join("") : "";
    const ok = await visible(env, actor, now, keys);
    if (!ok.length) return json({ error: "not_found" }, 404);
    const before = await setStates(env, actor, now, ok, body.state, body.state === "new" || body.state === "seeing" ? "" : reason, note);
    return json({ changed: ok, before });
  }
  if (path === "/ledger/undo" && method === "POST") {
    // 마지막 상태 바꿈을 그 앞 상태(그때 이유까지)로 되돌려요. 되돌림도 기록에 남아요.
    const body = await readJson(request, 1024);
    const key = typeof body.key === "string" && KEY_RE.test(body.key) ? body.key : null;
    if (!key || !(await visible(env, actor, now, [key])).length) return json({ error: "not_found" }, 404);
    const logs = await all(env, "SELECT * FROM work_log WHERE key = ? AND action NOT IN ('reopen', 'summary') ORDER BY id DESC LIMIT 2", key);
    const last = logs[0];
    if (!last || last.action === "undo") return json({ error: "nothing" }, 409);
    const to = last.before || "new";
    // 앞 상태의 이유와 메모는 그 앞 기록에서(없으면 비워요).
    const prev = logs[1] && logs[1].action === to ? logs[1] : null;
    await setStates(env, actor, now, [key], to, prev?.reason ?? "", prev?.note ?? "", "undo", last.action);
    return json({ key, state: to });
  }
  if (path === "/ledger/summary" && method === "POST") {
    // 한 줄 요약(Claude·관리자). 원문은 그대로, 줄에서 눌러 봐요.
    const body = await readJson(request, 4096);
    const key = typeof body.key === "string" && KEY_RE.test(body.key) ? body.key : null;
    if (!key || !(await visible(env, actor, now, [key])).length) return json({ error: "not_found" }, 404);
    const text = [...String(body.text ?? "").replace(/\s+/g, " ").trim()].slice(0, LEDGER.summary).join("");
    await batch(env, [stmt(env, "UPDATE ledger_lines SET summary = ?, summary_by = ?, summary_at = ? WHERE key = ?", text, actor.name ?? "", now, key),
      stmt(env, "INSERT INTO work_log (key, action, before, reason, note, by_id, by_name, at) VALUES (?, 'summary', '', '', ?, ?, ?, ?)", key, text || "(지움)", actor.id ?? "", actor.name ?? "", now)]);
    return json({ key, summary: text });
  }
  return null;
}
