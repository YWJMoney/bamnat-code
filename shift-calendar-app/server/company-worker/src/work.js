import { all, batch, can, iso, json, legacyTodo, readJson, stmt } from "./index.js";
import { DAY, HOUR } from "./units.js";
import { PHOTO_WHY } from "./photo-guard.js";

// ---- 1.36 [GRW-07] 할 일함: 관리자 앱 혁신 개편 (사용자 2026-11-01 "지금은 관리가 아주 어렵고 불편하다. 아주 체계적이고 누락 없는
// 처리가 가능한 관리자 앱 혁신이 필요하다") ----
//
// 들어온 일이 하나도 빠지지 않고, 누가 언제 무엇을 했는지 남고, 한 화면에서 끝까지 처리돼요.
//   - 한 대기열: 오늘의 할 일(문의·검토·신고 글·임금표·개발사항·보상·이달 할 일·관리자 승인)에 1.36 마을 쪽(스튜디오 요구 묶음·
//     신고된 작품·작품 가림·사진 검토·프로필 신고·기본 도구 후보)을 더해요. 권한 없는 칸은 읽지도 않아요.
//   - 상태: 새로 → 보는 중 → 기다림(사용자 답·개발) → 끝. 끝낼 때는 이유를 골라야 해요. 원래 칸의 처리(답하기·가리기)는 그 화면에서 해요.
//   - 누락 막기: 끝냈는데 원래 칸에 새 움직임이 있으면 바로, 아무 움직임 없이 남아 있으면 7일 뒤 '다시 열림'으로 돌아와요.
//     기다림도 7일이 지나면 기한 넘김이에요. 기한은 원래 칸의 기한(문의 답 목표 등)이고, 없으면 종류마다 정한 날 수예요.
//   - 기록: 상태가 바뀔 때마다 누가·언제·무엇을·왜(work_log). 되돌리기는 앞 상태로 다시 바꾸기예요.

export const WORK = {
  /** 끝냈는데 원래 칸에 그대로 남아 있으면 이만큼 뒤 다시 와요. 기다림도 이만큼 지나면 기한 넘김. */
  backDays: 7,
  /** 기한이 따로 없는 종류의 기한(일). */
  dueDays: { studio_need: 7, recipe_flag: 3, recipe_hidden: 30, photo: 2, maker_flag: 3, tool: 14 },
  max: 400,
  /**
   * 신고처럼 처리한 뒤에도 원래 칸에 남는 종류(신고 수가 그대로 보이는 것): 시간으로는 돌아오지 않고, 새 신고가 올 때만 돌아와요.
   * 나머지는 처리하면 원래 칸에서 빠지니, 끝냈는데 7일 뒤에도 남아 있으면 빠뜨린 거예요.
   */
  stays: ["flagged_post", "hidden_post", "doubted", "recipe_flag", "maker_flag"],
};
export const WORK_STATES = ["new", "seeing", "waiting", "done"];
export const WORK_STATE_NAMES = { new: "새로", seeing: "보는 중", waiting: "기다림", done: "끝" };
export const WAIT_REASONS = { user: "사용자 답 기다림", dev: "개발 기다림", other: "그 밖의 것" };
export const DONE_REASONS = { handled: "처리함", answered: "답함", duplicate: "중복", fixed: "이미 고침", dev: "개발로 넘김", wontfix: "하지 않기로 함",
  spam: "스팸·장난", nothing: "문제없음" };
/** 할 일함의 종류와 이름(오늘의 할 일 종류 포함). */
export const WORK_TYPES = { inquiry: "문의", group: "제보 묶음", ask: "앱에 없는 방식", doubted: "달라요", flagged_post: "신고된 글", hidden_post: "신고로 가린 글",
  rate: "임금표", dev: "개발사항", rewards: "보상", ops: "이달 할 일", admin: "관리자 승인", phone: "관리자 휴대폰",
  studio_need: "스튜디오 요구", recipe_flag: "신고된 작품", recipe_hidden: "신고로 가린 작품", photo: "사진 검토", maker_flag: "프로필 신고", tool: "기본 도구 후보" };
/** 종류마다 묶음(거르기 칩). */
export const WORK_GROUPS = { ask: ["inquiry", "studio_need", "dev"], report: ["flagged_post", "hidden_post", "recipe_flag", "recipe_hidden", "photo", "maker_flag", "doubted"],
  review: ["group", "ask", "rate", "tool"], ops: ["rewards", "ops", "admin", "phone"] };
export const WORK_GROUP_NAMES = { ask: "문의·요구", report: "신고·사진", review: "검토·후보", ops: "운영" };

export const WORK_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS work_states (key TEXT PRIMARY KEY, state TEXT NOT NULL, reason TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '',
    by_id TEXT NOT NULL DEFAULT '', by_name TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL, seen_at INTEGER NOT NULL, reopened INTEGER NOT NULL DEFAULT 0)`,
  `CREATE TABLE IF NOT EXISTS work_log (id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL, action TEXT NOT NULL, before TEXT NOT NULL DEFAULT '',
    reason TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', by_id TEXT NOT NULL DEFAULT '', by_name TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS work_log_key ON work_log (key, id)",
  "CREATE INDEX IF NOT EXISTS work_log_at ON work_log (at)",
];

const short = (t) => [...String(t ?? "")].slice(0, 80).join("");

/** 1.36 마을 쪽 일: 장터를 맡은 사람(community)과 문의를 맡은 사람(inquiries)이 보는 것. */
async function villageItems(env, actor, now) {
  const out = [];
  const add = (x) => out.push({ due: null, ...x });
  const dueOf = (type, at) => at + WORK.dueDays[type] * DAY;
  const q = [];
  if (can(actor, "inquiries")) {
    q.push(["needs", "SELECT id, title, askers, state, area, created_at, updated_at FROM studio_needs WHERE state IN ('open', 'market', 'building') ORDER BY askers DESC LIMIT 100"]);
  }
  if (can(actor, "community")) {
    q.push(["flagged", "SELECT code, title, flags, updated_at, (SELECT MIN(at) FROM recipe_flags f WHERE f.code = r.code) AS since, (SELECT MAX(at) FROM recipe_flags f WHERE f.code = r.code) AS lastf FROM recipes r WHERE flags > 0 AND state = 'visible' LIMIT 100"]);
    q.push(["hidden", "SELECT code, title, flags, hidden_at FROM recipes WHERE state = 'hidden' AND hidden_reason = 'flags' LIMIT 100"]);
    q.push(["photos", "SELECT id, state, why, flags, at, decided_at FROM recipe_images WHERE state IN ('pending', 'held') ORDER BY at LIMIT 100"]);
    q.push(["makers", `SELECT f.maker, m.bio, COUNT(*) AS n, MIN(f.at) AS since, MAX(f.at) AS last FROM recipe_maker_flags f JOIN recipe_makers m ON m.maker = f.maker
      GROUP BY f.maker LIMIT 100`]);
    q.push(["tools", "SELECT t.code, t.since, r.title, r.applied FROM recipe_tiers t JOIN recipes r ON r.code = t.code WHERE t.tier = 3 AND t.later = 0 LIMIT 50"]);
  }
  if (!q.length) return out;
  const res = await batch(env, q.map(([, sql]) => stmt(env, sql)));
  const got = Object.fromEntries(q.map(([k], i) => [k, res[i].results]));
  for (const n of got.needs ?? []) {
    add({ type: "studio_need", id: n.id, title: n.title, n: n.askers, at: n.created_at, last: n.updated_at, due: n.state === "open" ? null : dueOf("studio_need", n.updated_at),
      meta: `${n.area ? `${n.area} · ` : ""}${{ open: "모이는 중", market: "장터에 올림", building: "만드는 중" }[n.state]}` });
  }
  for (const r of got.flagged ?? []) add({ type: "recipe_flag", id: r.code, title: r.title, n: r.flags, at: r.since ?? r.updated_at, last: r.lastf ?? r.updated_at, due: dueOf("recipe_flag", r.since ?? r.updated_at) });
  // 신고로 먼저 가린 작품은 30일 안에 판단해요(정보통신망법 44조의2 임시 조치).
  for (const r of got.hidden ?? []) add({ type: "recipe_hidden", id: r.code, title: r.title, n: r.flags, at: r.hidden_at, last: r.hidden_at, due: dueOf("recipe_hidden", r.hidden_at) });
  for (const p of got.photos ?? []) {
    const at = p.state === "held" ? p.decided_at ?? p.at : p.at;
    add({ type: "photo", id: p.id, title: PHOTO_WHY[p.why] ?? "사진", n: p.flags, at, last: at, due: dueOf("photo", at), meta: p.state === "held" ? "다른 분께 가려져 있어요" : "올린 분만 보여요" });
  }
  for (const m of got.makers ?? []) add({ type: "maker_flag", id: m.maker, title: short(m.bio) || "(소개 없음)", n: m.n, at: m.since, last: m.last, due: dueOf("maker_flag", m.since) });
  for (const t of got.tools ?? []) add({ type: "tool", id: t.code, title: t.title, n: t.applied, at: t.since, last: t.since, due: dueOf("tool", t.since), meta: "쓴 사람 수" });
  return out;
}

/** What the list opens for each type, as the app's route (the screen where the work itself is done). */
export function workLink(type, id) {
  return ({ studio_need: "#/growth", tool: "#/recipes", recipe_flag: "#/recipes", recipe_hidden: "#/recipes", photo: "#/recipes", maker_flag: "#/recipes" })[type] ?? null;
}

/**
 * [GRW-07] Every item with its 할 일함 state. A done item still in its source comes back when the source moved after it was done
 * (last > at) or after [backDays]; a waiting one is late after [backDays].
 */
export async function workList(env, actor, now) {
  const legacy = (await legacyTodo(env, actor, now)).map((x) => ({ ...x, at: Date.parse(x.at), due: x.due ? Date.parse(x.due) : null }));
  const items = [...legacy, ...(await villageItems(env, actor, now))];
  const keys = items.map((x) => `${x.type}:${x.id}`);
  const states = new Map();
  for (let i = 0; i < keys.length; i += 90) {
    const part = keys.slice(i, i + 90);
    for (const r of await all(env, `SELECT * FROM work_states WHERE key IN (${part.map(() => "?").join(",")})`, ...part)) states.set(r.key, r);
  }
  const back = [];
  const out = items.map((x) => {
    const key = `${x.type}:${x.id}`;
    const s = states.get(key);
    let state = s?.state ?? "new";
    let reopened = s?.reopened ?? 0;
    const last = x.last ?? x.at;
    if (state === "done" && (last > s.at || (!WORK.stays.includes(x.type) && now - s.at > WORK.backDays * DAY))) {
      state = "new";
      reopened += 1;
      back.push(stmt(env, "UPDATE work_states SET state = 'new', reopened = ?, at = ? WHERE key = ?", reopened, now, key),
        stmt(env, "INSERT INTO work_log (key, action, before, reason, at) VALUES (?, 'reopen', 'done', ?, ?)", key, last > s.at ? "moved" : "left", now));
    }
    const due = state === "waiting" ? s.at + WORK.backDays * DAY : x.due;
    return { key, type: x.type, typeName: WORK_TYPES[x.type] ?? x.type, id: x.id, title: x.title, n: x.n ?? 0, meta: x.meta ?? x.company ?? null,
      at: iso(x.at), due: due ? iso(due) : null, late: Boolean(due && due < now), age: Math.max(0, Math.floor((now - x.at) / HOUR)),
      state, stateName: WORK_STATE_NAMES[state], reason: s && state !== "new" ? s.reason : "", reasonName: state === "waiting" ? WAIT_REASONS[s?.reason] ?? "" : state === "done" ? DONE_REASONS[s?.reason] ?? "" : "",
      note: s?.note ?? "", by: s && state !== "new" ? s.by_name : "", byMe: Boolean(s && s.by_id === actor.id && state !== "new"), reopened,
      mine: Boolean(x.mine), assignee: x.assignee ?? null, link: workLink(x.type, x.id) };
  });
  if (back.length) await batch(env, back);
  return out;
}

/** 오래된 것이 위로: 기한 넘김 → 다시 열림 → 기한 가까운 순 → 오래된 순. */
export function workOrder(a, b) {
  return Number(b.late) - Number(a.late) || Number(b.reopened > 0 && b.state === "new") - Number(a.reopened > 0 && a.state === "new")
    || (a.due && b.due ? a.due.localeCompare(b.due) : a.due ? -1 : b.due ? 1 : a.at.localeCompare(b.at));
}

function groupOf(type) {
  return Object.keys(WORK_GROUPS).find((g) => WORK_GROUPS[g].includes(type)) ?? "ops";
}

export async function workRoute(request, env, url, path, now) {
  const method = request.method;
  const actor = env.actor;
  if (path === "/work" && method === "GET") {
    const all_ = await workList(env, actor, now);
    const p = url.searchParams;
    const state = p.get("state") ?? "open";
    const group = WORK_GROUPS[p.get("group")] ? p.get("group") : null;
    const type = WORK_TYPES[p.get("type")] ? p.get("type") : null;
    const items = all_.filter((x) => (state === "open" ? x.state !== "done" : state === "late" ? x.late && x.state !== "done" : state === "mine" ? (x.byMe || x.mine) && x.state !== "done" : x.state === state))
      .filter((x) => (group ? groupOf(x.type) === group : true)).filter((x) => (type ? x.type === type : true)).sort(workOrder);
    const open = all_.filter((x) => x.state !== "done");
    const start = Date.parse(`${new Date(now + 9 * HOUR).toISOString().slice(0, 10)}T00:00:00+09:00`);
    const kstEnd = start + DAY;
    // 하루 요약: 오늘 들어온 것, 오늘 끝낸 것(누가 몇 건), 남은 것.
    const doneToday = await all(env, "SELECT by_name, COUNT(*) AS n FROM work_log WHERE action = 'done' AND at >= ? GROUP BY by_name ORDER BY n DESC", start);
    const counts = {
      open: open.length, late: open.filter((x) => x.late).length, today: open.filter((x) => x.due && Date.parse(x.due) < kstEnd).length,
      new: open.filter((x) => x.state === "new").length, seeing: open.filter((x) => x.state === "seeing").length, waiting: open.filter((x) => x.state === "waiting").length,
      reopened: open.filter((x) => x.reopened > 0 && x.state === "new").length, mine: open.filter((x) => x.byMe || x.mine).length,
      done: all_.filter((x) => x.state === "done").length,
      groups: Object.fromEntries(Object.keys(WORK_GROUPS).map((g) => [g, open.filter((x) => groupOf(x.type) === g).length])),
    };
    return json({ now: iso(now), items: items.slice(0, WORK.max), counts,
      summary: { inToday: all_.filter((x) => Date.parse(x.at) >= start).length, doneToday: doneToday.reduce((n, r) => n + r.n, 0),
        byWho: doneToday.map((r) => ({ name: r.by_name || "앱", n: r.n })) },
      names: { states: WORK_STATE_NAMES, wait: WAIT_REASONS, done: DONE_REASONS, types: WORK_TYPES, groups: WORK_GROUP_NAMES }, rules: WORK });
  }
  if (path === "/work" && method === "POST") {
    // {keys: [...], state, reason, note}: one or many at once; each change goes to the log with what it was before.
    const body = await readJson(request, 16 * 1024);
    const keys = Array.isArray(body.keys) ? [...new Set(body.keys.filter((k) => typeof k === "string" && /^[a-z_]{2,20}:[A-Za-z0-9._-]{1,60}$/.test(k)))].slice(0, 100) : [];
    if (!keys.length) return json({ error: "keys", field: "keys" }, 400);
    if (!WORK_STATES.includes(body.state)) return json({ error: "state", field: "state" }, 400);
    const reason = typeof body.reason === "string" ? body.reason : "";
    if (body.state === "done" && !DONE_REASONS[reason]) return json({ error: "reason", field: "reason" }, 400);
    if (body.state === "waiting" && !WAIT_REASONS[reason]) return json({ error: "reason", field: "reason" }, 400);
    const note = typeof body.note === "string" ? [...body.note.trim()].slice(0, 300).join("") : "";
    // Only what this admin may see: a key whose type belongs to an area they lack is refused.
    const mine = new Set((await workList(env, actor, now)).map((x) => x.key));
    const known = await all(env, `SELECT key, state FROM work_states WHERE key IN (${keys.map(() => "?").join(",")})`, ...keys);
    const before = new Map(known.map((r) => [r.key, r.state]));
    const ok = keys.filter((k) => mine.has(k) || before.has(k));
    if (!ok.length) return json({ error: "not_found" }, 404);
    const by = actor.name ?? "";
    await batch(env, ok.flatMap((k) => [
      stmt(env, `INSERT INTO work_states (key, state, reason, note, by_id, by_name, at, seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(key) DO UPDATE SET state = excluded.state, reason = excluded.reason, note = excluded.note, by_id = excluded.by_id, by_name = excluded.by_name, at = excluded.at`,
      k, body.state, reason, note, actor.id ?? "", by, now, now),
      stmt(env, "INSERT INTO work_log (key, action, before, reason, note, by_id, by_name, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", k, body.state, before.get(k) ?? "new",
        reason, note, actor.id ?? "", by, now),
    ]));
    return json({ changed: ok, before: Object.fromEntries(ok.map((k) => [k, before.get(k) ?? "new"])) });
  }
  if (path === "/work/log" && method === "GET") {
    const key = url.searchParams.get("key");
    const rows = key ? await all(env, "SELECT * FROM work_log WHERE key = ? ORDER BY id DESC LIMIT 50", key)
      : await all(env, "SELECT * FROM work_log ORDER BY id DESC LIMIT 100");
    return json({ items: rows.map((r) => ({ key: r.key, action: r.action, actionName: r.action === "reopen" ? "다시 열림" : WORK_STATE_NAMES[r.action] ?? r.action,
      before: r.before, reason: r.reason, reasonName: r.action === "reopen" ? (r.reason === "moved" ? "끝낸 뒤 새 움직임" : `${WORK.backDays}일 지나도 남아 있음`)
        : DONE_REASONS[r.reason] ?? WAIT_REASONS[r.reason] ?? "", note: r.note, by: r.by_name, at: iso(r.at) })) });
  }
  // 할 일함 메뉴 숫자: 남은 일과 기한 넘김.
  if (path === "/work/count" && method === "GET") {
    const list = await workList(env, actor, now);
    const open = list.filter((x) => x.state !== "done");
    return json({ open: open.length, late: open.filter((x) => x.late).length });
  }
  return null;
}

