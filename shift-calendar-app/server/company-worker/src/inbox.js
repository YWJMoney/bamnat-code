import { all, audit, batch, can, iso, json, one, randomId, readJson, refuse, run, stmt } from "./index.js";
import { DEV, INQUIRY, actorName, cut, devEvent, devUpdate, plainText, triage } from "./inquiries.js";

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
    case "known": return match?.status === "done"
      ? "같은 내용을 이미 고쳐 두었어요. 다음 업데이트에 들어가요. 업데이트가 나오면 이 문의에서 알려 드릴게요."
      : `같은 내용이 개발 목록에 올라가 있어요(지금 ${DEV.statuses[match?.status] ?? "접수"}). 고쳐지면 이 문의에서 알려 드릴게요. 알려 주셔서 고마워요.`;
    case "declined": return match?.reason ? `검토해 봤는데 지금은 반영하기 어려워요. ${match.reason}` : "검토해 봤는데 지금은 반영하기 어려워요.";
    case "answered": return match?.answer ?? "";
    case "faq": return match?.text ?? "";
    case "again": return "알려 주셔서 고마워요. 고쳤던 문제가 다시 생긴 것 같아 바로 다시 살펴볼게요.";
    default: return "알려 주셔서 고마워요. 확인해서 개발 목록에 올렸어요. 고쳐지면 이 문의에서 알려 드릴게요.";
  }
}

/** A 개발사항 title from the words of a question: its first sentence, at most 60 letters. */
export function titleFrom(text) {
  const first = String(text ?? "").replace(/\s+/g, " ").trim().split(/(?<=[.!?。])\s|\n/)[0] ?? "";
  const c = [...first];
  return c.length > 60 ? `${c.slice(0, 59).join("")}…` : first || "새 문의";
}

/** What the server knows to weigh questions against: 개발사항, answered questions, saved answers. */
async function known(env) {
  const [devs, answered, templates] = await Promise.all([
    all(env, `SELECT id, title, body, status, reason, released_in FROM dev_items WHERE deleted_at IS NULL ORDER BY updated_at DESC LIMIT ${SORTING.devs}`),
    // Questions an admin answered (the last word theirs), with the first question and that answer.
    all(env, `SELECT i.id, i.kind, i.info,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first,
        (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin' ORDER BY m.id DESC LIMIT 1) AS answer
      FROM inquiries i WHERE i.status != 'open' OR EXISTS (SELECT 1 FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'admin')
      ORDER BY i.updated_at DESC LIMIT ${SORTING.answered}`),
    all(env, "SELECT id, label, text, topic FROM reply_templates ORDER BY id").catch(() => []),
  ]);
  return {
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
    else if (d.status === "done" && d.released_in) {
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
  const rows = await all(env, `SELECT i.id, i.kind, i.info, i.created_at, i.updated_at, i.same,
      (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first,
      (SELECT m.sender FROM inquiry_messages m WHERE m.inquiry_id = i.id ORDER BY m.id DESC LIMIT 1) AS last
    FROM inquiries i
    WHERE i.status = 'open' AND i.sorted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM dev_links l JOIN dev_items d ON d.id = l.item_id AND d.deleted_at IS NULL WHERE l.inquiry_id = i.id)
      ${ids ? `AND i.id IN (${ids.map(() => "?").join(", ")})` : ""}
    ORDER BY i.created_at DESC LIMIT ${SORTING.candidates}`, ...(ids ?? []));
  return rows.filter((r) => r.last === "user" && r.first).map((r) => ({ id: r.id, kind: r.kind, info: JSON.parse(r.info), text: r.first,
    at: r.created_at, same: r.same ?? 0 }));
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
  const id22 = (v) => (typeof v === "string" && /^[A-Za-z0-9]{22}$/.test(v) ? v : refuse("id", "id"));
  const devId = (v) => (typeof v === "string" && /^d[A-Za-z0-9]{15}$/.test(v) ? v : refuse("item", "item"));
  const needsDev = body.actions.some((a) => a.do === "dev" || a.do === "link" || a.do === "reopen" || (a.do === "reply" && a.item));
  if (needsDev && !can(env.actor, "dev", "edit")) return json({ error: "no_permission", area: "dev" }, 403);
  const done = { replied: 0, linked: 0, created: [], reopened: 0, skipped: 0 };
  const name = actorName(env);
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
    if ((a.do === "reply" || a.do === "reopen") && a.text && (await inboxAnswer(env, id, a.text, now, name))) done.replied++;
  }
  await audit(env, now, "inbox_apply", `${body.actions.length}건`,
    [done.replied && `답 ${done.replied}`, done.linked && `연결 ${done.linked}`, done.created.length && `개발사항 ${done.created.length}`,
      done.reopened && `다시 접수 ${done.reopened}`, done.skipped && `빼 둠 ${done.skipped}`].filter(Boolean).join(" · "));
  return json(done);
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
async function inboxAnswer(env, id, text, now, by) {
  const t = plainText(text, INQUIRY.text, "text", true);
  const r = await run(env, "UPDATE inquiries SET status = 'answered', updated_at = ?, assignee = COALESCE(assignee, ?), assignee_name = CASE WHEN assignee IS NULL THEN ? ELSE assignee_name END WHERE id = ?",
    now, env.actor?.id ?? null, by, id);
  if (!r.meta.changes) return false;
  await run(env, "INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', ?, ?, ?)", id, t, by, now);
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

/** [INBOX-05] GET /inquiries/handoff?all=1: the text, and which items it holds (all=1 takes those handed before too). */
export async function handoff(env, params, now) {
  const everything = params.get("all") === "1";
  const items = await all(env, `SELECT d.*, (SELECT COUNT(*) FROM dev_links l WHERE l.item_id = d.id) AS asks FROM dev_items d
    WHERE d.deleted_at IS NULL AND d.status IN ('todo', 'doing') ${everything ? "" : "AND d.handed_at IS NULL"}
    ORDER BY asks DESC, d.created_at LIMIT ${SORTING.handoff}`);
  const day = new Date(now + 9 * 3600 * 1000).toISOString().slice(0, 10);
  const out = [`# 밤낮 관리자 앱 → 개발 넘김 (${day})`, "",
    "아래 개발사항을 다음 개발에 넣어 주세요. 문의 수가 많은 것부터예요. 끝나면 맨 아래 ‘결과 적는 법’대로 결과를 적어 주세요.", ""];
  for (const d of items) {
    const asks = await all(env, `SELECT i.kind, i.info, (SELECT m.text FROM inquiry_messages m WHERE m.inquiry_id = i.id AND m.sender = 'user' ORDER BY m.id LIMIT 1) AS first
      FROM dev_links l JOIN inquiries i ON i.id = l.inquiry_id WHERE l.item_id = ? ORDER BY i.created_at LIMIT 8`, d.id);
    const versions = [...new Set(asks.map((a) => JSON.parse(a.info).app).filter(Boolean).map((v) => String(v).split(" ")[0]))];
    out.push(`## ${d.id} · ${d.title}`);
    out.push(`- 상태: ${DEV.statuses[d.status]} · 문의 ${d.asks}건${versions.length ? ` · 앱 ${versions.join(", ")}` : ""}${d.handed_at ? " · 전에 넘김" : ""}`);
    // A body made in 정리함 is the questions' own words: said once, in the 문의 lines under it.
    const said = asks.map((a) => String(a.first ?? "").replace(/\s+/g, " ").trim());
    const body = String(d.body ?? "").split(/\n+/).map((l) => l.replace(/^[-*]\s*/, "").replace(/\s+/g, " ").trim()).filter((l) => l && !said.some((t) => t.startsWith(l.slice(0, 60))));
    if (body.length) out.push(`- 내용: ${body.join(" / ")}`);
    for (const a of asks) {
      const info = JSON.parse(a.info);
      out.push(`- 문의(${({ bug: "문제", question: "질문", idea: "제안", company: "회사", other: "기타" })[a.kind] ?? a.kind}${info.model ? `, ${info.model}` : ""}${info.app ? `, ${String(info.app).split(" ")[0]}` : ""}): ${String(a.first ?? "").replace(/\s+/g, " ").slice(0, 300)}`);
    }
    out.push("");
  }
  if (!items.length) out.push("(넘길 개발사항이 없어요)", "");
  out.push("## 결과 적는 법", "한 줄에 하나씩, 개발사항 id와 결과를 적어요. 관리자 앱 › 문의 › 정리함 › ‘개발 결과 붙여 넣기’에 그대로 붙여 넣어요.", "```",
    "d0123456789abcde 완료 1.36.0", "d0123456789abcde 개발중", "d0123456789abcde 반려 이유를 한 줄로", "```");
  return { text: out.join("\n"), items: items.map((d) => ({ id: d.id, title: d.title, status: d.status, asks: d.asks, handed: d.handed_at ? iso(d.handed_at) : null })) };
}

/** [INBOX-05] POST /inquiries/handoff/mark {ids}: 넘김 on those items (after the text really went to the clipboard). */
export async function markHanded(request, env, now) {
  const body = await readJson(request, 16 * 1024);
  const ids = Array.isArray(body.ids) && body.ids.length && body.ids.length <= SORTING.handoff && body.ids.every((x) => typeof x === "string" && /^d[A-Za-z0-9]{15}$/.test(x))
    ? [...new Set(body.ids)] : refuse("ids", "ids");
  await batch(env, [stmt(env, `UPDATE dev_items SET handed_at = ? WHERE id IN (${ids.map(() => "?").join(", ")})`, now, ...ids),
    ...ids.map((id) => devEvent(env, id, now, "handoff", {}))]);
  await audit(env, now, "dev_handoff", `${ids.length}개`);
  return json({ marked: ids.length, at: iso(now) });
}

const RESULT = /^\s*[-*•]?\s*(d[A-Za-z0-9]{15})\s*[:·\-]?\s*(완료|개발중|개발 중|반려|접수)\s*(.*)$/;

/** The lines of a 결과 block: id, status and the rest (the version for 완료, the reason for 반려). */
export function readResult(text) {
  const out = [];
  for (const line of String(text ?? "").split(/\r?\n/)) {
    const m = line.match(RESULT);
    if (!m) continue;
    const status = { 완료: "done", 개발중: "doing", "개발 중": "doing", 반려: "rejected", 접수: "todo" }[m[2]];
    const rest = m[3].trim();
    const v = status === "done" ? rest.match(/\d{1,2}\.\d{1,2}\.\d{1,3}/)?.[0] ?? null : null;
    out.push({ id: m[1], status, version: v, reason: status === "rejected" ? rest.slice(0, DEV.reason) : "" });
  }
  return out;
}

/**
 * [INBOX-06] POST /inquiries/handoff/result {text, notify?, dry?}: Claude's 결과 block applied — status, the version a
 * finished one goes out in, the reason of a 반려 — each a line of the item's history. [dry] only reads it back for the
 * screen to show first; [notify] answers each tied question still waiting (완료: update to that version; 반려: the reason).
 */
export async function applyResult(request, env, now) {
  const body = await readJson(request, 64 * 1024);
  const lines = readResult(body.text);
  if (!lines.length) refuse("text", "text");
  const rows = await all(env, `SELECT * FROM dev_items WHERE deleted_at IS NULL AND id IN (${lines.map(() => "?").join(", ")})`, ...lines.map((l) => l.id));
  const byId = new Map(rows.map((r) => [r.id, r]));
  const plan = lines.map((l) => ({ ...l, title: byId.get(l.id)?.title ?? null, found: byId.has(l.id),
    problem: !byId.has(l.id) ? "없는 개발사항" : l.status === "rejected" && !l.reason ? "반려 이유가 없어요" : null }));
  if (body.dry === true) return json({ lines: plan });
  if (plan.some((l) => l.status !== "doing" && l.status !== "todo") && !can(env.actor, "devStatus", "edit")) return json({ error: "no_permission", area: "devStatus" }, 403);
  let changed = 0;
  let answered = 0;
  for (const l of plan.filter((x) => !x.problem)) {
    const row = await one(env, "SELECT * FROM dev_items WHERE id = ?", l.id);
    const r = await devUpdate(env, row, { status: l.status, ...(l.status === "rejected" ? { reason: l.reason } : {}), ...(l.version ? { releasedIn: l.version } : {}) }, now);
    if (r === true) changed++;
    if (body.notify === true && (l.status === "done" || l.status === "rejected")) {
      const waitingOnes = await all(env, `SELECT i.id FROM dev_links k JOIN inquiries i ON i.id = k.inquiry_id WHERE k.item_id = ? AND i.status != 'closed'`, l.id);
      const text = l.status === "done"
        ? (l.version ? `말씀해 주신 내용을 ${l.version} 버전에서 고쳤어요. 업데이트가 나오면 Play 스토어에서 밤낮을 업데이트해 주세요.` : "말씀해 주신 내용을 고쳤어요. 다음 업데이트에 들어가요.")
        : replyFor("declined", { reason: l.reason });
      for (const w of waitingOnes) if (await inboxAnswer(env, w.id, text, now, actorName(env))) answered++;
    }
  }
  await audit(env, now, "dev_result", `${changed}개`, answered ? `문의 ${answered}건에 답` : "");
  return json({ lines: plan, changed, answered });
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
