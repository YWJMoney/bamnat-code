import { fcmTell } from "./fcm.js";
import { all, audit, batch, can, iso, json, one, randomId, readJson, refuse, run, stmt } from "./index.js";
import { DEV, INQUIRY, actorName, cut, devEvent, devUpdate, holdReply, plainText, triage } from "./inquiries.js";
// 1.37 [ADM2-02] 나간 판: 반영 판이 Play에 나간 판 이하일 때만 '이미 고침'.
import { DESK, isOut, laterText, markTold, shippedVersion } from "./desk.js";

// ---- 문의 정리함 [INBOX-01]~[INBOX-08] (1.35, 사용자 2026-10-30 "중복이나 이미 처리된 문의는 자동 대응 … 새롭거나 신규 문제
// 개선사항을 개발에 넘겨서 … 올인원 관리 체계") ----
//
// Every question still waiting on an answer and not yet tied to a 개발사항 is weighed against what the server already
// knows — the 개발사항 (with their status and the version a finished one went out in), questions answered before and the
// saved answers — and put in one of seven piles, each with the step it calls for already written:
//   fixed     a finished 개발사항 that went out in a newer version than the asker's app → "update, it is fixed" (자동 가능)
//   known     a 개발사항 still 접수·개발중, or finished but not out yet → linked to it, "on the list" (자동 가능)
//   again     a 개발사항 finished in a version the asker already has → it came back: open it again
//   declined  a 개발사항 turned down → its reason, for the admin to answer with
//   answered  a question answered before that reads the same → that answer again
//   faq       a saved answer (자주 쓰는 답) that fits
//   new       nothing like it: grouped with the other new ones that read alike, to go to 개발사항 as one
// Nothing is answered without an admin unless the owner turned 자동 대응 on for fixed or known (settings inbox_auto).
// Only words are compared; nothing new about the asker is kept.

export const SORTING = {
  // Above this the two read as the same thing (see likeness); tuned on test/inbox.test.mjs's cases.
  same: 0.42,
  // A saved answer only has to cover the question, so a little lower.
  faq: 0.34,
  candidates: 300,
  devs: 2000,
  answered: 500,
  apply: 100,
  handoff: 60,
  // 1.37 [ADM2-09] 넘길 글의 ‘개발 기록’: 지난 개발사항 몇 개까지, 하나에 기록 몇 줄까지.
  history: 120,
  historyNotes: 3,
};

export const PILES = ["fixed", "known", "again", "declined", "answered", "faq", "new"];
export const PILE_NAMES = { fixed: "이미 고침", known: "개발 목록에 있음", again: "고친 뒤 다시 생김", declined: "반려된 요청", answered: "전에 답한 문의와 같음",
  faq: "자주 쓰는 답으로 충분", new: "새 문제·개선" };

// Words that say nothing about what is wrong: greetings, softeners, endings.
const FILLER = /안녕하세요|안녕하십니까|감사합니다|감사해요|고맙습니다|고마워요|수고하세요|수고하십니다|부탁드려요|부탁드립니다|부탁해요|해\s*주세요|해주세요|해\s*주시면|좋겠어요|좋겠습니다|좋을\s*것\s*같아요|있나요|없나요|인가요|입니다|이에요|예요|그런데|근데|혹시|제발|너무|정말|진짜|계속|가끔|자꾸|항상|요즘|어제|오늘|[ㅠㅜㅎㅋ]+|밤낮|앱|어플/g;
// Particles and endings taken off a word so 알람이·알람은·알람을 meet as 알람.
const TAIL = /(에서는|에서도|으로는|에게서|이에요|입니다|해요|돼요|되요|어요|아요|에서|으로|이랑|하고|까지|부터|처럼|보다|이나|은|는|이|가|을|를|에|로|도|만|요|의|와|과|나)$/;

/** The words of [text] with particles off, and their letter pairs, for [likeness]. */
export function grams(text) {
  const t = String(text ?? "").normalize("NFC").toLowerCase().replace(FILLER, " ").replace(/[^0-9a-z가-힣]+/g, " ").trim();
  const words = new Set();
  const pairs = new Set();
  for (let w of t.split(/\s+/)) {
    if (!w) continue;
    const bare = [...w].length > 2 ? w.replace(TAIL, "") : w;
    if (bare) w = bare;
    words.add(w);
    const c = [...w];
    if (c.length === 1) pairs.add(w);
    for (let i = 0; i < c.length - 1; i++) pairs.add(c[i] + c[i + 1]);
  }
  return { words, pairs };
}

const dice = (a, b) => {
  if (!a.size || !b.size) return 0;
  let n = 0;
  for (const x of a) if (b.has(x)) n++;
  return (2 * n) / (a.size + b.size);
};

/**
 * How alike two texts read, 0~1: letter pairs (안 울려요 ~ 안 울림) and whole words weighed alike, with a little more when
 * both are about the same thing (the same topic from [triage]).
 */
export function likeness(a, b, sameTopic = false) {
  const x = a.pairs ? a : grams(a);
  const y = b.pairs ? b : grams(b);
  const s = 0.55 * dice(x.pairs, y.pairs) + 0.45 * dice(x.words, y.words);
  return Math.min(1, s + (sameTopic ? 0.08 : 0));
}

/** "1.12.0 (18)" → [1, 12, 0]; null when there is no version. */
export function version(v) {
  const m = String(v ?? "").match(/(\d{1,2})\.(\d{1,2})\.(\d{1,3})/);
  return m ? m.slice(1).map(Number) : null;
}

/** Below 0 when a is older than b. */
export function compareVersions(a, b) {
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i];
  return 0;
}

/** The answer each pile starts with; the admin may change it before sending. */
export function replyFor(pile, match) {
  const v = match?.releasedIn;
  switch (pile) {
    case "fixed": return `말씀하신 문제는 ${v} 버전에서 고쳤어요. Play 스토어에서 밤낮을 최신 버전으로 업데이트해 주세요. 업데이트한 뒤에도 같은 일이 생기면 이 문의에 다시 알려 주세요.`;
    // 1.37 [ADM2-10] 출시 전에는 '해결하고 있어요'를 정성스럽게, 나오면 다시 알린다는 약속과 함께.
    case "known": return match?.status === "done"
      ? laterText(match?.releasedIn)
      : `알려 주셔서 고마워요. 같은 불편을 말씀해 주신 분들이 있어 지금 해결하고 있어요(지금 ${DEV.statuses[match?.status] ?? "접수"}). 고친 업데이트가 나오면 이 문의에서 바로 알려 드릴게요.`;
    case "declined": return match?.reason ? `검토해 봤는데 지금은 반영하기 어려워요. ${match.reason}` : "검토해 봤는데 지금은 반영하기 어려워요.";
    case "answered": return match?.answer ?? "";
    case "faq": return match?.text ?? "";
    case "again": return "알려 주셔서 고마워요. 고쳤던 문제가 다시 생긴 것 같아 바로 다시 살펴볼게요.";
    default: return "알려 주셔서 고마워요. 말씀해 주신 내용을 확인해 개발 목록에 올렸고, 해결하고 있어요. 고친 업데이트가 나오면 이 문의에서 바로 알려 드릴게요.";
  }
}

/** A 개발사항 title from the words of a question: its first sentence, at most 60 letters. */
export function titleFrom(text) {
  // [J135-04] 1.35: without the '[설정 > 함께 만들어요]' the app puts before a question asked from a screen.
  const first = String(text ?? "").replace(/^\s*\[[^\]\n]{1,40}\]\s*/, "").replace(/\s+/g, " ").trim().split(/(?<=[.!?。])\s|\n/)[0] ?? "";
  const c = [...first];
  return c.length > 60 ? `${c.slice(0, 59).join("")}…` : first || "새 문의";
}

/** What the server knows to weigh questions against: 개발사항, answered questions, saved answers. */
async function known(env) {
  const [devs, answered, templates, shipped] = await Promise.all([
    all(env, `SELECT id, title, body, status, reason, released_in FROM dev_items WHERE deleted_at IS NULL ORDER BY updated_at DESC LIMIT ${SORTING.devs}`),
    // Questions an admin answered (the last word theirs), with the first question and that answer.
    all(env, `SELECT i.id, i.kind, i.info,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin' ORDER BY m.id DESC LIMIT 1) AS answer
      FROM inquiries i WHERE i.status != 'open' OR EXISTS (SELECT 1 FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin')
      ORDER BY i.updated_at DESC LIMIT ${SORTING.answered}`),
    all(env, "SELECT id, label, text, topic FROM reply_templates ORDER BY id").catch(() => []),
    shippedVersion(env),
  ]);
  return {
    shipped,
    devs: devs.map((d) => ({ ...d, g: grams(`${d.title} ${d.title} ${d.body}`) })),
    answered: answered.filter((a) => a.answer && a.first).map((a) => ({ ...a, g: grams(a.first), topic: triage(a.kind, a.first, JSON.parse(a.info)).topic })),
    templates: templates.map((t) => ({ ...t, g: grams(`${t.label} ${t.text}`) })),
  };
}

/** The pile one question goes in, with what it matched and the answer to start from. */
export function classify(q, k) {
  const g = grams(q.text);
  const topic = triage(q.kind, q.text, q.info).topic;
  const app = version(q.info?.app);
  let dev = null;
  for (const d of k.devs) {
    const s = likeness(g, d.g);
    if (s >= SORTING.same && (!dev || s > dev.score)) dev = { score: s, d };
  }
  if (dev) {
    const d = dev.d;
    const match = { type: "dev", id: d.id, title: d.title, status: d.status, reason: d.reason || "", releasedIn: d.released_in ?? null, score: round(dev.score) };
    let pile;
    if (d.status === "rejected") pile = "declined";
    // 1.37 [ADM2-02] 고쳤어도 그 판이 아직 Play에 나가지 않았으면 '개발 목록에 있음'(다음 업데이트에 들어가요).
    else if (d.status === "done" && d.released_in && isOut(d.released_in, k.shipped)) {
      const fixed = version(d.released_in);
      // Unknown app version: most likely an old one, so 'update'.
      pile = !app || !fixed || compareVersions(app, fixed) < 0 ? "fixed" : "again";
    } else pile = "known";
    return { pile, match, reply: replyFor(pile, match), topic };
  }
  let prev = null;
  for (const a of k.answered) {
    if (a.id === q.id) continue;
    const s = likeness(g, a.g, a.topic === topic);
    if (s >= SORTING.same && (!prev || s > prev.score)) prev = { score: s, a };
  }
  if (prev) {
    const match = { type: "inquiry", id: prev.a.id, title: titleFrom(prev.a.first), answer: prev.a.answer, score: round(prev.score) };
    return { pile: "answered", match, reply: replyFor("answered", match), topic };
  }
  let faq = null;
  for (const t of k.templates) {
    const s = likeness(g, t.g, t.topic === topic);
    if (s >= SORTING.faq && (!faq || s > faq.score)) faq = { score: s, t };
  }
  if (faq && q.kind !== "bug") {
    const match = { type: "template", id: faq.t.id, title: faq.t.label, text: faq.t.text, score: round(faq.score) };
    return { pile: "faq", match, reply: replyFor("faq", match), topic };
  }
  return { pile: "new", match: null, reply: replyFor("new"), topic };
}

const round = (s) => Math.round(s * 100) / 100;

/** The questions waiting on an admin with no 개발사항 yet and not put aside in 정리함, newest first. */
async function waiting(env, ids = null) {
  const rows = await all(env, `SELECT i.id, i.kind, i.info, i.created_at, i.updated_at, i.same, i.handed_in,
      (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first,
      (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last
    FROM inquiries i
    WHERE i.status = 'open' AND i.sorted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL WHERE l.inquiry_id = i.id)
      ${ids ? `AND i.id IN (${ids.map(() => "?").join(", ")})` : ""}
    ORDER BY i.created_at DESC LIMIT ${SORTING.candidates}`, ...(ids ?? []));
  return rows.filter((r) => r.last === "user" && r.first).map((r) => ({ id: r.id, kind: r.kind, info: JSON.parse(r.info), text: r.first,
    at: r.created_at, same: r.same ?? 0, handedIn: r.handed_in ?? null }));
}

/** [INBOX-02] 자동 대응: which piles the server answers by itself as a question comes in (the owner's choice; off at first). */
export async function autoSettings(env) {
  const row = await one(env, "SELECT value FROM settings WHERE key = 'inbox_auto'");
  const v = row ? JSON.parse(row.value) : {};
  return { fixed: v.fixed === true, known: v.known === true };
}

/**
 * [INBOX-01] GET /inquiries/sort: every waiting question in its pile, with what it matched and the answer to start from.
 * New ones that read alike come as one group (the oldest first), so they go to 개발 as one 개발사항.
 */
export async function sortInbox(env) {
  const [list, k, auto] = await Promise.all([waiting(env), known(env), autoSettings(env)]);
  const piles = Object.fromEntries(PILES.map((p) => [p, []]));
  const fresh = [];
  for (const q of list) {
    const c = classify(q, k);
    const item = { id: q.id, kind: q.kind, preview: [...q.text].slice(0, 160).join(""), app: q.info?.app ?? null, model: q.info?.model ?? null,
      at: iso(q.at), same: q.same, ...triage(q.kind, q.text, q.info), match: c.match, reply: c.reply };
    if (c.pile === "new") fresh.push({ item, g: grams(q.text), topic: c.topic });
    else piles[c.pile].push(item);
  }
  // Group the new ones: each joins the first group whose oldest question reads like it.
  const groups = [];
  for (const f of [...fresh].reverse()) {
    const g = groups.find((x) => likeness(x.g, f.g, x.topic === f.topic) >= SORTING.same);
    if (g) g.items.push(f.item);
    else groups.push({ g: f.g, topic: f.topic, items: [f.item] });
  }
  piles.new = groups.sort((a, b) => b.items.length - a.items.length || b.items[b.items.length - 1].at.localeCompare(a.items[a.items.length - 1].at))
    .map((x) => ({ ...x.items[0], group: x.items.map((i) => i.id), others: x.items.slice(1), title: titleFrom(x.items[0].preview),
      sameTotal: x.items.reduce((n, i) => n + (i.same ?? 0), 0) }));
  const counts = Object.fromEntries(PILES.map((p) => [p, p === "new" ? piles.new.reduce((n, g) => n + g.group.length, 0) : piles[p].length]));
  return { piles, counts, names: PILE_NAMES, auto, total: list.length };
}

/**
 * [INBOX-03] POST /inquiries/sort/apply {actions: [{id, do, text?, item?, ids?, title?, body?}]}: many at once, each one of
 *   reply   answer it ([text]); with [item], tie it to that 개발사항 too (link needs 개발사항 고치기)
 *   link    tie it to [item] without answering
 *   dev     a new 개발사항 ([title], [body]) with [ids] tied to it; [text] answers them all
 *   reopen  [item] back to 접수 (it came back after being fixed; needs 처리), [id] tied and answered with [text]
 *   skip    leave it out of 정리함 (it stays in 문의 as it was)
 * One activity line for the lot.
 */
export async function applySort(request, env, now) {
  const body = await readJson(request, 256 * 1024);
  if (!Array.isArray(body.actions) || !body.actions.length || body.actions.length > SORTING.apply) refuse("actions", "actions");
  const done = await sortActions(env, body.actions, now);
  if (done instanceof Response) return done;
  await audit(env, now, "inbox_apply", `${body.actions.length}건`,
    [done.replied && `답 ${done.replied}`, done.linked && `연결 ${done.linked}`, done.created.length && `개발사항 ${done.created.length}`,
      done.reopened && `다시 접수 ${done.reopened}`, done.skipped && `빼 둠 ${done.skipped}`].filter(Boolean).join(" · "));
  return json(done);
}

/** The 정리함 steps themselves, shared by applySort and 1.37 [ADM2-08] the 결과 block's 문의 lines; a Response when not allowed. */
async function sortActions(env, actions, now) {
  const body = { actions };
  const id22 = (v) => (typeof v === "string" && /^[A-Za-z0-9]{22}$/.test(v) ? v : refuse("id", "id"));
  const devId = (v) => (typeof v === "string" && /^d[A-Za-z0-9]{15}$/.test(v) ? v : refuse("item", "item"));
  const needsDev = body.actions.some((a) => a.do === "dev" || a.do === "link" || a.do === "reopen" || (a.do === "reply" && a.item));
  if (needsDev && !can(env.actor, "dev", "edit")) return json({ error: "no_permission", area: "dev" }, 403);
  const done = { replied: 0, linked: 0, created: [], reopened: 0, skipped: 0 };
  const name = actorName(env);
  let shipped;
  for (const a of body.actions) {
    if (!["reply", "link", "dev", "reopen", "skip"].includes(a?.do)) refuse("do", "do");
    if (a.do === "dev") {
      const ids = Array.isArray(a.ids) && a.ids.length && a.ids.length <= SORTING.apply ? [...new Set(a.ids.map(id22))] : refuse("ids", "ids");
      const title = plainText(a.title, DEV.title, "title", true);
      const text = plainText(a.body, DEV.body, "body");
      const item = `d${randomId(15)}`;
      await batch(env, [
        stmt(env, `INSERT INTO dev_items (id, title, body, status, reason, created_by, created_by_name, assignee, assignee_name, created_at, updated_at, deleted_at)
          VALUES (?, ?, ?, 'todo', '', ?, ?, NULL, NULL, ?, ?, NULL)`, item, title, text, env.actor.id, name, now, now),
        devEvent(env, item, now, "create", { title, body: cut(text), from: "정리함" }),
      ]);
      done.created.push({ id: item, title });
      for (const id of ids) {
        if (await link(env, item, id, now, name)) done.linked++;
        if (a.text && (await inboxAnswer(env, id, a.text, now, name))) done.replied++;
      }
      continue;
    }
    if (a.do === "skip") {
      const r = await run(env, "UPDATE inquiries SET sorted_at = ? WHERE id = ?", now, id22(a.id));
      done.skipped += r.meta.changes;
      continue;
    }
    const id = id22(a.id);
    if (a.do === "reopen") {
      const item = devId(a.item);
      const row = await one(env, "SELECT * FROM dev_items WHERE id = ? AND deleted_at IS NULL", item);
      if (!row) return json({ error: "not_found" }, 404);
      if ((await devUpdate(env, row, { status: "todo" }, now)) === true) done.reopened++;
    }
    if (a.do === "link" || a.do === "reopen" || (a.do === "reply" && a.item)) {
      if (await link(env, devId(a.item), id, now, name)) done.linked++;
    }
    if ((a.do === "reply" || a.do === "reopen") && a.text && (await inboxAnswer(env, id, a.text, now, name))) {
      done.replied++;
      // 1.37 [ADM2-10] 이미 나간 판으로 고친 개발사항에 묶어 답했으면 그 답이 '나왔어요' 안내예요: 판을 저장할 때 다시 보내지 않아요.
      if (a.do === "reply" && a.item) {
        const it = await one(env, "SELECT status, released_in FROM dev_items WHERE id = ?", a.item);
        if (it?.status === "done" && isOut(it.released_in, shipped ??= await shippedVersion(env))) await markTold(env, a.item, [id], now);
      }
    }
  }
  return done;
}

/** Ties question [id] to 개발사항 [item]; false when it already was (or either is gone). */
async function link(env, item, id, now, by) {
  if (!(await one(env, "SELECT id FROM dev_items WHERE id = ? AND deleted_at IS NULL", item))) return false;
  if (!(await one(env, "SELECT id FROM inquiries WHERE id = ?", id))) return false;
  const r = await run(env, "INSERT OR IGNORE INTO dev_links (item_id, inquiry_id, by, at) VALUES (?, ?, ?, ?)", item, id, by, now);
  if (!r.meta.changes) return false;
  await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, item), devEvent(env, item, now, "link", { inquiry: id })]);
  return true;
}

/** An answer on question [id] by [by], the question then 답함 (taken by whoever answered when nobody held it). */
export async function inboxAnswer(env, id, text, now, by) {
  const t = plainText(text, INQUIRY.text, "text", true);
  // 1.38 [DESK-09] Claude 열쇠의 답은 10분 보류.
  if (env.actor?.key) return (await one(env, "SELECT id FROM inquiries WHERE id = ?", id)) ? holdReply(env, { id }, t, {}, now) : false;
  const r = await run(env, "UPDATE inquiries SET status = 'answered', updated_at = ?, assignee = COALESCE(assignee, ?), assignee_name = CASE WHEN assignee IS NULL THEN ? ELSE assignee_name END WHERE id = ?",
    now, env.actor?.id ?? null, by, id);
  if (!r.meta.changes) return false;
  await run(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, ?, ?)", id, t, by, now);
  // 1.36 [PUSH-01]
  await fcmTell(env, [id], now);
  return true;
}

/** [INBOX-02] POST /inquiries/sort/auto {fixed, known}: the owner turns 자동 대응 on or off per pile. */
export async function saveAuto(request, env, now) {
  if (!env.actor.owner) return json({ error: "no_permission", area: "owner" }, 403);
  const body = await readJson(request, 1024);
  const v = { fixed: body.fixed === true, known: body.known === true };
  await run(env, "INSERT OR REPLACE INTO settings (key, value) VALUES ('inbox_auto', ?)", JSON.stringify(v));
  await audit(env, now, "inbox_auto", `이미 고침 ${v.fixed ? "켬" : "끔"} · 개발 목록 ${v.known ? "켬" : "끔"}`);
  return json(v);
}

/**
 * [INBOX-02] As a question comes in: when 자동 대응 is on for its pile, it is tied to the 개발사항 it matched and answered
 * by "자동 정리" (the app shows that as any answer). Never fails the question: a failure leaves it for an admin.
 */
export async function autoAnswer(env, id, now) {
  try {
    const auto = await autoSettings(env);
    if (!auto.fixed && !auto.known) return null;
    const [q] = await waiting(env, [id]);
    if (!q) return null;
    const c = classify(q, await known(env));
    if (!((c.pile === "fixed" && auto.fixed) || (c.pile === "known" && auto.known))) return null;
    const by = "자동 정리";
    await link(env, c.match.id, id, now, by);
    await inboxAnswer(env, id, c.reply, now, by);
    await audit(env, now, "inbox_auto_reply", c.match.title, `${PILE_NAMES[c.pile]} ${id}`);
    return c.pile;
  } catch {
    return null;
  }
}

// ---- Claude에게 넘기기 [INBOX-05]~[INBOX-07] ----
//
// The owner works on the app with Claude. 개발사항 not handed over yet go out as one text to paste into Claude: each with
// its id, status, how many questions and which app versions, and the questions' own words (never the phone, a photo or
// anything that went with them but the app version and phone model). Claude ends a round with a 결과 block in the same
// ids, which pasted back sets 완료 (with the version), 개발중 or 반려 here and can answer every tied question at once.

// 1.37 [ADM2-08] (사용자 2026-11-05 "정리함에서 Claude에게 넘기기를 하고 너가 개발 결과를 주면 그것을 내가 복사 넣기를 하면 문의 대응까지
// 완벽하게") The text also carries every question still waiting in 정리함 (q + its id, the server's guess and a draft answer), and the
// 결과 block answers them too: 답, 개발(tie to an item), 새개발(a new item from several), 빼기, and a "| 답:" after a 개발사항 line.

const KIND_WORDS = { bug: "문제", question: "질문", idea: "제안", company: "회사", other: "기타" };
const handoffDay = (ms) => new Date(Number(ms) + 9 * 3600 * 1000).toISOString().slice(0, 10);
const oneLine = (t, n = 300) => String(t ?? "").replace(/\s+/g, " ").trim().slice(0, n);

/** [INBOX-05] GET /inquiries/handoff?all=1: the text, and which items it holds (all=1 takes those handed before too). */
export async function handoff(env, params, now) {
  const everything = params.get("all") === "1";
  const [items, waitingOnes, k] = await Promise.all([
    all(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d
      WHERE d.deleted_at IS NULL AND d.status IN ('todo', 'doing') ${everything ? "" : "AND d.handed_at IS NULL"}
      ORDER BY asks DESC, d.created_at LIMIT ${SORTING.handoff}`),
    waiting(env),
    known(env),
  ]);
  const asks = waitingOnes.slice(0, SORTING.handoff);
  // 1.37 [ADM2-10] (사용자 2026-11-05 "넘기는 시점과 이전에 쌓이는 시점은 명확히 분리, 기록도 남아야") 이 글은 넘김 묶음 하나예요:
  // 지금(cut)까지 들어온 것만 담고, 복사하면 묶음으로 기록돼요. 지난 묶음 뒤 새로 들어온 문의와 전에 넘겼는데 아직 답이 없는 문의를 나눠 보여요.
  const batchId = `h${randomId(9)}`;
  const log = await handoffLog(env);
  const last = log[0] ?? null;
  const fresh = asks.filter((q) => !q.handedIn);
  const again = asks.filter((q) => q.handedIn);
  const day = new Date(now + 9 * 3600 * 1000).toISOString().slice(0, 10);
  const out = [`# 밤낮 관리자 앱 → Claude (${day})`, "",
    `넘김 묶음: ${batchId} · ${handoffTime(now)} 기준. 이 뒤에 들어온 문의는 다음 묶음에 들어가요.`,
    last ? `지난 묶음: ${last.id} · ${handoffTime(last.at)} · 문의 ${last.asks} · 개발 ${last.items} · ${last.resultAt ? `결과 반영 ${handoffTime(Date.parse(last.resultAt))}` : "결과 아직 안 옴"}` : "지난 묶음: 없음(처음 넘겨요)",
    `Play에 나간 판: ${k.shipped?.v ?? "(아직 안 적음)"}. '고쳤어요·업데이트해 주세요'는 이 판 이하로 고친 것에만 써 주세요.`,
    "아래 문의에 답하고, 개발할 것은 개발사항으로 묶고, 개발사항을 개발해 주세요. 끝나면 맨 아래 ‘결과 적는 법’대로 결과를 한 덩어리로 주세요.",
    "출시 전 답은 '지금 해결하고 있어요'를 정성스럽게: 불편에 공감하고, 무엇을 하고 있는지, 고친 업데이트가 나오면 이 문의에서 바로 알려 드린다는 약속까지.",
    "개발을 마친 개발사항(완료 줄)에는 판이 나간 날 묶인 문의에 자동으로 갈 ‘출시 답’과 앱 공지 ‘업데이트 소식’에 실릴 한 줄(‘공지’, 사용자 말로 무엇이 좋아졌는지)을 꼭 미리 적어 주세요.",
    "환불·결제·보상, 법·안전 신고, 개인정보 요청, 크게 화난 민원, 약관 질문, 회사 이름이 나오는 회사 방식은 답을 적지 말고 사용자에게 먼저 물어 주세요.", ""];
  const askLine = (q) => {
    const c = classify(q, k);
    const m = c.match;
    const guess = m?.type === "dev" ? ` · 서버 판단: ${PILE_NAMES[c.pile]} ≈ ${m.id} ‘${m.title}’(${DEV.statuses[m.status] ?? m.status}${m.releasedIn ? ` ${m.releasedIn}` : ""})`
      : m ? ` · 서버 판단: ${PILE_NAMES[c.pile]} ≈ ‘${oneLine(m.title, 60)}’` : " · 서버 판단: 새 문제·개선";
    out.push(`- q${q.id} · ${KIND_WORDS[q.kind] ?? q.kind} · 받음 ${handoffTime(q.at)}${q.info?.app ? ` · 앱 ${String(q.info.app).split(" ")[0]}` : ""}${q.same ? ` · 나도 그래요 ${q.same}` : ""}${q.handedIn ? ` · 전에 넘김 ${q.handedIn}` : ""}${guess}`);
    out.push(`  문의: ${oneLine(q.text, 600)}`);
    if (c.reply) out.push(`  추천 답: ${oneLine(c.reply, 400)}`);
  };
  out.push(`## 새로 들어온 문의 (${last ? `지난 묶음 ${last.id} 뒤` : "처음"}, ${fresh.length}건)`, "");
  fresh.forEach(askLine);
  if (!fresh.length) out.push("(새로 들어온 문의가 없어요)");
  out.push("", `## 전에 넘겼는데 아직 답이 없는 문의 (${again.length}건)`, "");
  again.forEach(askLine);
  if (!again.length) out.push("(없어요)");
  out.push("", `## 개발사항 (${items.length}개)`, "");
  for (const d of items) {
    const linked = await all(env, `SELECT i.kind, i.info, (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first
      FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ? ORDER BY i.created_at LIMIT 8`, d.id);
    const versions = [...new Set(linked.map((a) => JSON.parse(a.info).app).filter(Boolean).map((v) => String(v).split(" ")[0]))];
    out.push(`### ${d.id} · ${d.title}`);
    out.push(`- 상태: ${DEV.statuses[d.status]} · 문의 ${d.asks}건${versions.length ? ` · 앱 ${versions.join(", ")}` : ""}${d.handed_at ? " · 전에 넘김" : ""}`);
    // A body made in 정리함 is the questions' own words: said once, in the 문의 lines under it.
    const said = linked.map((a) => oneLine(a.first, 1000));
    const body = String(d.body ?? "").split(/\n+/).map((l) => l.replace(/^[-*]\s*/, "").replace(/\s+/g, " ").trim()).filter((l) => l && !said.some((t) => t.startsWith(l.slice(0, 60))));
    if (body.length) out.push(`- 내용: ${body.join(" / ")}`);
    for (const a of linked) {
      const info = JSON.parse(a.info);
      out.push(`- 문의(${KIND_WORDS[a.kind] ?? a.kind}${info.model ? `, ${info.model}` : ""}${info.app ? `, ${String(info.app).split(" ")[0]}` : ""}): ${oneLine(a.first)}`);
    }
    out.push("");
  }
  if (!items.length) out.push("(새로 넘길 개발사항이 없어요)", "");
  // 1.37 [ADM2-09] (사용자 2026-11-05 "개발했던 내역도 관리를 해서 넘길 글 복사 시 개발 기록을 보고 정리해서 진행") 지난 개발사항 전부:
  // 상태·고친 판(나갔는지)·묶인 문의 수·반려 이유와 남긴 개발 기록. Claude는 새 문의를 이것과 먼저 맞춰 봐요.
  const shown = new Set(items.map((d) => d.id));
  const past = (await all(env, `SELECT d.id, d.title, d.body, d.status, d.reason, d.released_in, d.updated_at, d.handed_at,
      (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d WHERE d.deleted_at IS NULL
      ORDER BY d.updated_at DESC LIMIT ${SORTING.history + SORTING.handoff}`)).filter((d) => !shown.has(d.id)).slice(0, SORTING.history);
  const notes = new Map();
  const noteIds = [...shown, ...past.map((d) => d.id)];
  for (let i = 0; i < noteIds.length; i += 90) {
    const part = noteIds.slice(i, i + 90);
    for (const r of await all(env, `SELECT item_id, at, actor, detail FROM dev_history WHERE action = 'note' AND item_id IN (${part.map(() => "?").join(", ")}) ORDER BY id DESC`, ...part)) {
      const list = notes.get(r.item_id) ?? [];
      if (list.length < SORTING.historyNotes) list.push(`${handoffDay(r.at)} ${r.actor}: ${oneLine(JSON.parse(r.detail).text, 300)}`);
      notes.set(r.item_id, list);
    }
  }
  // 앞 ‘개발사항’ 칸의 것에도 남긴 기록을 붙여요.
  for (const d of items) {
    const list = notes.get(d.id);
    if (!list?.length) continue;
    const at = out.indexOf(`### ${d.id} · ${d.title}`);
    if (at >= 0) out.splice(at + 2, 0, ...list.map((t) => `- 기록: ${t}`));
  }
  out.push(`## 개발 기록 (지난 개발사항 ${past.length}개, 최근 바뀐 순)`,
    "새 문의는 먼저 여기와 맞춰 봐요. 고친 것과 같으면 그 개발사항에 묶고(나간 판이면 업데이트 안내, 아직이면 ‘업데이트에 들어가요’),",
    "고친 판 이상의 앱에서 다시 생겼으면 그 개발사항을 ‘개발중’으로 되돌리거나 새개발로, 반려한 것과 같으면 그 이유로 답해요.", "");
  for (const d of past) {
    const state = d.status === "done"
      ? `완료${d.released_in ? ` ${d.released_in}${isOut(d.released_in, k.shipped) ? " (나감)" : " (아직 안 나감)"}` : " (판 없음)"}`
      : d.status === "rejected" ? `반려: ${oneLine(d.reason, 120)}` : `${DEV.statuses[d.status] ?? d.status}${d.handed_at ? " · 전에 넘김" : ""}`;
    out.push(`- ${d.id} · ${d.title} · ${state} · 문의 ${d.asks}건 · ${handoffDay(d.updated_at)}`);
    const body = oneLine(String(d.body ?? "").replace(/^[-*]\s*/gm, ""), 160);
    if (body) out.push(`  내용: ${body}`);
    for (const t of notes.get(d.id) ?? []) out.push(`  기록: ${t}`);
  }
  if (!past.length) out.push("(아직 지난 개발사항이 없어요)");
  out.push("");
  out.push("## 결과 적는 법",
    "한 줄에 하나씩, 아래 모양으로만 적어요. 관리자 앱 › 문의 › 정리함 › ‘개발 결과 붙여 넣기’에 통째로 붙여 넣으면 상태가 바뀌고 문의마다 답이 가요.",
    "답은 한 줄로, 줄을 바꾸려면 \\n을 써요. 문의 줄은 쉼표로 여럿을 묶을 수 있어요. 맨 위에 묶음 줄을 꼭 적어요.", "```",
    `묶음 ${batchId}`,
    "q<문의 id> 답 보낼 답",
    "q<문의 id> 개발 d0123456789abcde | 답: 개발 목록에 올렸어요. 고쳐지면 알려 드릴게요.",
    "q<문의 id>,q<문의 id> 새개발 개발사항 제목 | 답: 보낼 답",
    "q<문의 id> 빼기",
    "d0123456789abcde 개발중",
    "d0123456789abcde 완료 1.37.0",
    "d0123456789abcde 완료 1.37.0 | 답: 묶인 문의에 보낼 답(안 적으면 기본 답)",
    "d0123456789abcde 반려 이유를 한 줄로",
    "d0123456789abcde 기록 무엇을 어디서 어떻게 고쳤는지(PR·커밋·판·확인한 것)",
    "d0123456789abcde 완료 1.37.0 | 기록: … | 답: 출시 전 답 | 출시 답: 판이 나간 날 갈 답 | 공지: 업데이트 소식 한 줄",
    "d0123456789abcde 출시답 판이 나간 날 묶인 문의에 갈 답",
    "d0123456789abcde 공지 업데이트 소식에 실릴 한 줄(80자 안)", "```",
    "개발한 개발사항에는 ‘기록’ 줄을 꼭 남겨 주세요. 다음 넘길 글의 ‘개발 기록’에 실려요.",
    "‘출시 답’과 ‘공지’는 판을 저장하는 날 쓰여요: 묶인 문의에 출시 답이 자동으로 가고, 공지 한 줄들이 ‘업데이트 소식’ 공지로 앱에 올라가요.");
  return { text: out.join("\n"), asks: asks.length, fresh: fresh.length, again: again.length, batch: batchId, cut: iso(now), askIds: asks.map((q) => q.id), log,
    items: items.map((d) => ({ id: d.id, title: d.title, status: d.status, asks: d.asks, handed: d.handed_at ? iso(d.handed_at) : null })) };
}

const handoffTime = (ms) => new Date(Number(ms) + 9 * 3600 * 1000).toISOString().slice(0, 16).replace("T", " ");

/** 1.37 [ADM2-10] 넘김 기록: 최근 묶음부터, 담은 수와 결과. */
async function handoffLog(env) {
  const rows = await all(env, `SELECT id, at, cut, by, asks, items, result_at, result FROM handoffs ORDER BY at DESC LIMIT ${DESK.handoffLog}`).catch(() => []);
  return rows.map((r) => {
    let result = null;
    try {
      result = r.result ? JSON.parse(r.result) : null;
    } catch {
      result = null;
    }
    return { id: r.id, at: r.at, cut: iso(r.cut), by: r.by, asks: JSON.parse(r.asks).length, items: JSON.parse(r.items).length,
      resultAt: r.result_at ? iso(r.result_at) : null, result };
  });
}

/** [INBOX-05] POST /inquiries/handoff/mark {ids}: 넘김 on those items (after the text really went to the clipboard). */
export async function markHanded(request, env, now) {
  const body = await readJson(request, 16 * 1024);
  const raw = body.ids ?? [];
  const ids = Array.isArray(raw) && raw.length <= SORTING.handoff && raw.every((x) => typeof x === "string" && /^d[A-Za-z0-9]{15}$/.test(x))
    ? [...new Set(raw)] : refuse("ids", "ids");
  // 1.37 [ADM2-10] 담은 문의와 묶음 id(넘길 글이 준 것)도 함께: 묶음 기록이 남고, 그 문의는 '전에 넘김'이 돼요.
  const rawAsks = body.asks ?? [];
  const asks = Array.isArray(rawAsks) && rawAsks.length <= SORTING.handoff && rawAsks.every((x) => typeof x === "string" && /^[A-Za-z0-9]{22}$/.test(x))
    ? [...new Set(rawAsks)] : refuse("asks", "asks");
  if (!ids.length && !asks.length) refuse("ids", "ids");
  const batchId = body.batch === undefined ? `h${randomId(9)}` : typeof body.batch === "string" && /^h[A-Za-z0-9]{9}$/.test(body.batch) ? body.batch : refuse("batch", "batch");
  const cut = Date.parse(body.cut ?? "");
  const stmts = [stmt(env, "INSERT OR REPLACE INTO handoffs (id, at, cut, by, asks, items) VALUES (?, ?, ?, ?, ?, ?)",
    batchId, now, Number.isFinite(cut) && cut <= now ? cut : now, actorName(env), JSON.stringify(asks), JSON.stringify(ids))];
  if (ids.length) stmts.push(stmt(env, `UPDATE dev_items SET handed_at = ? WHERE id IN (${ids.map(() => "?").join(", ")})`, now, ...ids),
    ...ids.map((id) => devEvent(env, id, now, "handoff", { batch: batchId })));
  if (asks.length) stmts.push(stmt(env, `UPDATE inquiries SET handed_in = ? WHERE id IN (${asks.map(() => "?").join(", ")})`, batchId, ...asks));
  await batch(env, stmts);
  await audit(env, now, "dev_handoff", `${batchId} · 문의 ${asks.length} · 개발 ${ids.length}`);
  return json({ marked: ids.length, asks: asks.length, batch: batchId, at: iso(now) });
}

const RESULT = /^\s*[-*•]?\s*(d[A-Za-z0-9]{15})\s*[:·\-]?\s*(완료|개발중|개발 중|반려|접수|기록|메모|출시답|출시 답|공지)(?=\s|$|[:：])\s*(.*)$/;
const ASK_LINE = /^\s*[-*•]?\s*(q[A-Za-z0-9]{22}(?:\s*,\s*q[A-Za-z0-9]{22})*)\s*[:·\-]?\s*(답|개발|새개발|새 개발|빼기|빼 두기)(?=\s|$)\s*(.*)$/;
/** "… | 답: …", "| 기록: …", 1.37 [ADM2-10] "| 출시 답: …", "| 공지: …" at the end of a line (any order): the rest, the answer, the 개발 기록,
 * the answer for the day the version ships and the 업데이트 소식 line, with \n for a new line. */
const TAILS = { 답: "답", 기록: "기록", "출시 답": "출시", 출시답: "출시", 공지: "공지" };
const splitTail = (rest) => {
  const parts = String(rest).split(/\s*\|\s*(출시\s?답|답|기록|공지)\s*[:：]\s*/);
  const tail = { 답: "", 기록: "", 출시: "", 공지: "" };
  for (let i = 1; i + 1 < parts.length; i += 2) tail[TAILS[parts[i].replace(/\s+/, " ")] ?? TAILS[parts[i]]] = parts[i + 1].replace(/\\n/g, "\n").trim();
  return [parts[0].trim(), tail.답, tail.기록, tail.출시, tail.공지];
};
const BATCH_LINE = /^\s*[-*•]?\s*묶음\s*[:：]?\s*(h[A-Za-z0-9]{9})\b/m;
const splitAnswer = (rest) => splitTail(rest).slice(0, 2);

/** The 개발사항 lines of a 결과 block: id, status and the rest (the version for 완료, the reason for 반려), and its own answer. */
export function readResult(text) {
  const out = [];
  for (const line of String(text ?? "").split(/\r?\n/)) {
    const m = line.match(RESULT);
    if (!m) continue;
    // 1.37 [ADM2-09] 기록·메모: 무엇을 어디서 어떻게 고쳤는지(PR·커밋·판), 개발사항 기록에 남고 다음 넘길 글에 실려요.
    const own = m[3].replace(/^[:：]\s*/, "").replace(/\\n/g, "\n").trim();
    if (m[2] === "기록" || m[2] === "메모") {
      out.push({ id: m[1], status: null, version: null, reason: "", note: own.slice(0, DEV.note) });
      continue;
    }
    // 1.37 [ADM2-10] 출시 답·공지 한 줄만 따로.
    if (m[2] === "출시답" || m[2] === "출시 답") {
      out.push({ id: m[1], status: null, version: null, reason: "", shipReply: own.slice(0, DESK.shipReply), empty: !own });
      continue;
    }
    if (m[2] === "공지") {
      out.push({ id: m[1], status: null, version: null, reason: "", news: own.replace(/\s+/g, " ").slice(0, DESK.news), empty: !own });
      continue;
    }
    const status = { 완료: "done", 개발중: "doing", "개발 중": "doing", 반려: "rejected", 접수: "todo" }[m[2]];
    const [rest, answer, note, shipReply, news] = splitTail(m[3]);
    const v = status === "done" ? rest.match(/\d{1,2}\.\d{1,2}\.\d{1,3}/)?.[0] ?? null : null;
    out.push({ id: m[1], status, version: v, reason: status === "rejected" ? rest.slice(0, DEV.reason) : "", ...(answer ? { text: answer } : {}), ...(note ? { note: note.slice(0, DEV.note) } : {}),
      ...(shipReply ? { shipReply: shipReply.slice(0, DESK.shipReply) } : {}), ...(news ? { news: news.replace(/\s+/g, " ").slice(0, DESK.news) } : {}) });
  }
  return out;
}

/** 1.37 [ADM2-08] The 문의 lines of a 결과 block. */
export function readAsks(text) {
  const out = [];
  for (const line of String(text ?? "").split(/\r?\n/)) {
    const m = line.match(ASK_LINE);
    if (!m) continue;
    const ids = [...new Set(m[1].split(",").map((s) => s.trim().slice(1)))];
    const verb = { 답: "reply", 개발: "link", 새개발: "dev", "새 개발": "dev", 빼기: "skip", "빼 두기": "skip" }[m[2]];
    const raw = m[3].trim();
    if (verb === "reply") out.push({ ids, do: verb, text: raw.replace(/^[:：]\s*/, "").replace(/\\n/g, "\n").trim() });
    else {
      const [rest, answer] = splitAnswer(raw);
      if (verb === "link") out.push({ ids, do: verb, item: rest.match(/d[A-Za-z0-9]{15}/)?.[0] ?? null, text: answer });
      else if (verb === "dev") out.push({ ids, do: verb, title: rest.slice(0, DEV.title), text: answer });
      else out.push({ ids, do: verb, text: "" });
    }
  }
  return out;
}

/**
 * [INBOX-06] POST /inquiries/handoff/result {text, notify?, dry?}: Claude's 결과 block applied — status, the version a
 * finished one goes out in, the reason of a 반려 — each a line of the item's history. [dry] only reads it back for the
 * screen to show first; [notify] answers each tied question still waiting (완료: update to that version; 반려: the reason;
 * a "| 답:" on the line says it instead). 1.37 [ADM2-08] Its 문의 lines answer, tie, make 개발사항 and put aside as 정리함 does.
 */
export async function applyResult(request, env, now) {
  const body = await readJson(request, 256 * 1024);
  const lines = readResult(body.text);
  const askLines = readAsks(body.text);
  if (!lines.length && !askLines.length) refuse("text", "text");
  if (askLines.length > SORTING.apply) refuse("text", "text");
  // D1 binds at most 100 values in one query; a long 결과 block is read in groups of 90.
  const rows = [];
  for (let i = 0; i < lines.length; i += 90) {
    const part = lines.slice(i, i + 90).map((l) => l.id);
    rows.push(...await all(env, `SELECT * FROM dev_items WHERE deleted_at IS NULL AND id IN (${part.map(() => "?").join(", ")})`, ...part));
  }
  const byId = new Map(rows.map((r) => [r.id, r]));
  const plan = lines.map((l) => ({ ...l, title: byId.get(l.id)?.title ?? null, found: byId.has(l.id),
    problem: !byId.has(l.id) ? "없는 개발사항" : l.status === "rejected" && !l.reason ? "반려 이유가 없어요"
      : l.status === null && l.empty ? "내용이 비어 있어요" : l.status === null && !l.note && !l.shipReply && !l.news ? "기록이 비어 있어요" : null }));
  // 1.37 [ADM2-10] 완료 줄인데 판이 나간 날 갈 출시 답·업데이트 소식 한 줄이 이 덩어리에도, 개발사항에도 없으면 알려요(막지는 않아요: 기본 답이 가요).
  for (const l of plan.filter((x) => x.status === "done" && !x.problem)) {
    const row = byId.get(l.id);
    const has = (k, col) => plan.some((x) => x.id === l.id && x[k]) || Boolean(row?.[col]);
    l.warn = [!has("shipReply", "ship_reply") && "출시 답이 없어요(기본 답이 가요)", !has("news", "news") && "공지 한 줄이 없어요"].filter(Boolean);
  }
  const batchLine = String(body.text ?? "").match(BATCH_LINE)?.[1] ?? null;
  const batchRow = batchLine ? await one(env, "SELECT id, at FROM handoffs WHERE id = ?", batchLine)
    : await one(env, "SELECT id, at FROM handoffs WHERE result_at IS NULL ORDER BY at DESC LIMIT 1").catch(() => null);
  const askIds = [...new Set(askLines.flatMap((a) => a.ids))];
  const asked = new Map();
  for (let i = 0; i < askIds.length; i += 90) {
    const part = askIds.slice(i, i + 90);
    for (const r of await all(env, `SELECT i.id, i.status, (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first
      FROM inquiries i WHERE i.id IN (${part.map(() => "?").join(", ")})`, ...part)) asked.set(r.id, r);
  }
  const items = new Set(rows.map((r) => r.id));
  const linkTo = [...new Set(askLines.filter((a) => a.do === "link" && a.item).map((a) => a.item))].filter((id) => !items.has(id));
  for (const r of linkTo.length ? await all(env, `SELECT id FROM dev_items WHERE deleted_at IS NULL AND id IN (${linkTo.map(() => "?").join(", ")})`, ...linkTo) : []) items.add(r.id);
  const askPlan = askLines.map((a) => ({ ...a, previews: a.ids.map((id) => oneLine(asked.get(id)?.first, 60)),
    problem: a.ids.some((id) => !asked.has(id)) ? "없는 문의" : a.do === "reply" && !a.text ? "답이 비어 있어요"
      : a.do === "link" && !(a.item && items.has(a.item)) ? "없는 개발사항" : a.do === "dev" && !a.title.trim() ? "제목이 없어요" : null }));
  const batchInfo = { id: batchRow?.id ?? batchLine, known: Boolean(batchRow), at: batchRow ? iso(batchRow.at) : null };
  if (body.dry === true) return json({ lines: plan, asks: askPlan, batch: batchInfo });
  if (plan.some((l) => !l.problem && l.status && l.status !== "doing" && l.status !== "todo") && !can(env.actor, "devStatus", "edit")) return json({ error: "no_permission", area: "devStatus" }, 403);
  if (plan.some((l) => !l.problem && (l.note || l.shipReply || l.news)) && !can(env.actor, "dev", "edit")) return json({ error: "no_permission", area: "dev" }, 403);
  let changed = 0;
  let answered = 0;
  let noted = 0;
  let prepared = 0;
  const shipped = await shippedVersion(env);
  for (const l of plan.filter((x) => !x.problem)) {
    if (l.note) {
      await batch(env, [stmt(env, "UPDATE dev_items SET updated_at = ? WHERE id = ?", now, l.id), devEvent(env, l.id, now, "note", { text: l.note })]);
      noted++;
    }
    const extra = { ...(l.shipReply ? { shipReply: l.shipReply } : {}), ...(l.news ? { news: l.news } : {}) };
    if (!l.status && !Object.keys(extra).length) continue;
    const row = await one(env, "SELECT * FROM dev_items WHERE id = ?", l.id);
    const r = await devUpdate(env, row, { ...(l.status ? { status: l.status } : {}), ...(l.status === "rejected" ? { reason: l.reason } : {}), ...(l.version ? { releasedIn: l.version } : {}), ...extra }, now);
    if (r === true) {
      if (l.status) changed++;
      if (Object.keys(extra).length) prepared++;
    }
    if (!l.status) continue;
    if (body.notify === true && (l.status === "done" || l.status === "rejected" || l.text)) {
      const waitingOnes = await all(env, `SELECT i.id FROM dev_links k JOIN inquiries i ON i.id = k.inquiry_id WHERE k.item_id = ? AND i.status != 'closed'`, l.id);
      // 1.37 [ADM2-02] 'Play 업데이트'는 그 판이 나갔을 때만. 나가기 전이면 "업데이트에 들어가요", 판을 저장할 때 다시 알려요.
      const text = l.text || (l.status === "done"
        ? (l.version && isOut(l.version, shipped) ? `말씀해 주신 내용을 ${l.version} 버전에서 고쳤어요. Play 스토어에서 밤낮을 최신 버전으로 업데이트해 주세요.` : laterText(l.version))
        : replyFor("declined", { reason: l.reason }));
      for (const w of waitingOnes) if (await inboxAnswer(env, w.id, text, now, actorName(env))) answered++;
      // 이미 나간 판으로 고쳤다고 답했으면, 판을 저장할 때 같은 안내를 다시 보내지 않아요.
      if (l.status === "done" && l.version && isOut(l.version, shipped)) await markTold(env, l.id, waitingOnes.map((w) => w.id), now);
    }
  }
  // 1.37 [ADM2-08] 문의 lines, after the 개발사항 ones (a line may tie a question to an item the block just set).
  const actions = [];
  for (const a of askPlan.filter((x) => !x.problem)) {
    if (a.do === "dev") actions.push({ do: "dev", ids: a.ids, title: a.title, body: "", ...(a.text ? { text: a.text } : {}) });
    // 개발 with an answer is 정리함's "reply with item": tied and answered.
    else for (const id of a.ids) actions.push({ do: a.do === "link" && a.text ? "reply" : a.do, id, ...(a.item ? { item: a.item } : {}), ...(a.text ? { text: a.text } : {}) });
  }
  let sorted = { replied: 0, linked: 0, created: [], skipped: 0 };
  if (actions.length) {
    const r = await sortActions(env, actions, now);
    if (r instanceof Response) return r;
    sorted = r;
    // Claude made these itself from the block: they are already with Claude, not 넘길 것 again.
    if (sorted.created.length) await run(env, `UPDATE dev_items SET handed_at = ? WHERE id IN (${sorted.created.map(() => "?").join(", ")})`, now, ...sorted.created.map((c) => c.id));
  }
  // 1.37 [ADM2-10] 이 결과가 어느 넘김 묶음의 것인지 남겨요.
  const summary = { changed, noted, prepared, answered: answered + sorted.replied, linked: sorted.linked, created: sorted.created.length, skipped: sorted.skipped };
  if (batchRow) await run(env, "UPDATE handoffs SET result_at = ?, result = ? WHERE id = ?", now, JSON.stringify(summary), batchRow.id);
  await audit(env, now, "dev_result", `${changed}개`, [batchRow && `묶음 ${batchRow.id}`, noted && `개발 기록 ${noted}줄`, prepared && `출시 안내 ${prepared}개`, answered && `문의 ${answered}건에 답`, sorted.replied && `문의 줄 답 ${sorted.replied}`,
    sorted.created.length && `새 개발사항 ${sorted.created.length}`].filter(Boolean).join(" · "));
  return json({ lines: plan, asks: askPlan, batch: batchInfo, changed, noted, prepared, answered: answered + sorted.replied, linked: sorted.linked, created: sorted.created, skipped: sorted.skipped });
}

/** The routes under /api/admin/inquiries/sort and /handoff; null for any other path. */
export function inboxRoute(request, env, url, path, now) {
  const method = request.method;
  if (path === "/inquiries/sort" && method === "GET") return sortInbox(env).then(json);
  if (path === "/inquiries/sort/apply" && method === "POST") return applySort(request, env, now);
  if (path === "/inquiries/sort/auto" && method === "POST") return saveAuto(request, env, now);
  if (path === "/inquiries/handoff" && method === "GET") return handoff(env, url.searchParams, now).then(json);
  if (path === "/inquiries/handoff/mark" && method === "POST") return markHanded(request, env, now);
  if (path === "/inquiries/handoff/result" && method === "POST") return applyResult(request, env, now);
  return null;
}
