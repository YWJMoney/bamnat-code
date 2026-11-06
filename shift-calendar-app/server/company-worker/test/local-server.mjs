// [COMM-04][ADMIN-01] The worker on a plain Node server with SQLite, for the app's round-trip test and
// to try the developer app at http://localhost:8788/admin/ before deploying:
//   ADMIN_PASSWORD=<10+ letters> npm run local -- 8788 [--seed] [--db local.sqlite]
// --seed fills it with made-up coworkers' reports, so every screen has something on it.
import http from "node:http";
import worker from "../src/index.js";
import { d1 } from "./d1.mjs";
import { report, setup } from "./helpers.mjs";

const args = process.argv.slice(2);
const port = Number(args.find((a) => /^\d+$/.test(a)) || 8788);
const file = args.includes("--db") ? args[args.indexOf("--db") + 1] : ":memory:";
const env = { DB: d1(file), RATE_SALT: "local", ADMIN_PASSWORD: process.env.ADMIN_PASSWORD };
// 1.36 [SEC-11] Play Integrity on the PC: INTEGRITY_MODE=watch or require to see the app ask (no PLAY_SERVICE_ACCOUNT here, so
// the server answers not_set and the app shows why it cannot post).
if (process.env.INTEGRITY_MODE) env.INTEGRITY_MODE = process.env.INTEGRITY_MODE;
if (!env.ADMIN_PASSWORD) console.log("ADMIN_PASSWORD is not set: the developer app shows what is missing.");

const send = (path, body, ip, method = "POST") => worker.fetch(new Request(`http://localhost:${port}${path}`, {
  method,
  headers: { "content-type": "application/json", "cf-connecting-ip": ip },
  body: JSON.stringify(body),
}), env).then((r) => r.json());

async function seed() {
  // Adopted by two coworkers, then one says it is out of date.
  const a = await send("/api/reports", report(1, { company: "(주)예시정밀", verified: true, note: "10월 명세서랑 맞춰 봤어요" }), "10.0.0.1");
  await send("/api/reports", report(2, { company: "예시정밀" }), "10.0.0.2");
  await send("/api/votes", { device: "seed-device-00000009", company: a.company.id, setup: a.setup, ok: false, why: "old" }, "10.0.0.9");
  // A new version of it: enough coworkers, but the rule in use is the developer's call.
  const newer = setup();
  newer.pay.rules.extra[3].percent = 0.12;
  newer.pay.rules.extra[3].adds[1].rate = 0.6;
  await send("/api/reports", report(3, { company: "예시정밀", setup: newer, verified: true, note: "11월부터 대근이 기본급 12%로 올랐어요" }), "10.0.0.3");
  await send("/api/reports", report(4, { company: "예시정밀", setup: newer }), "10.0.0.4");
  // Waiting for coworkers.
  const day = setup();
  day.shiftTypes = [
    { name: "주간", label: "주", color: 4280391411, start: "08:30", end: "17:30", break: 60, breakStart: "12:00", off: false },
    { name: "휴일", label: "휴", color: 4288585374, break: 0, off: true },
  ];
  day.rotation = { anchor: "2026-10-05", steps: ["주간", "주간", "주간", "주간", "주간", "휴일", "휴일"] };
  day.extraKindShiftSets = { 3: ["주간"] };
  day.pay.items = day.pay.items.slice(0, 2);
  day.pay.rules.publicHolidayAs = null;
  const talk = await send("/api/reports", report(5, { company: "가나다화학", dept: "품질팀", setup: day }), "10.0.0.5");
  await send("/api/reports", report(6, { company: "라마바전자", dept: "생산1팀", setup: day, verified: true }), "10.0.0.6");
  // Turned down once, sent again.
  const odd = setup();
  odd.pay.monthlyHours = 160;
  const r = await send("/api/reports", report(7, { company: "사아자물류", setup: odd }), "10.0.0.7");
  const login = await worker.fetch(new Request(`http://localhost:${port}/api/admin/login`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-admin": "1", "cf-connecting-ip": "10.0.0.100" },
    body: JSON.stringify({ password: env.ADMIN_PASSWORD }),
  }), env);
  if (login.ok) {
    const cookie = login.headers.get("set-cookie").split(";")[0];
    await worker.fetch(new Request(`http://localhost:${port}/api/admin/groups/${r.company.id}/${r.setup}/reject`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-admin": "1", cookie },
      body: JSON.stringify({ reason: "unclear" }),
    }), env);
  }
  await send("/api/reports", report(8, { company: "사아자물류", setup: odd, verified: true }), "10.0.0.8");
  if (login.ok) await seedAdmins(login.headers.get("set-cookie").split(";")[0]);
  // A way the app cannot set yet.
  await send("/api/reports", report(9, {
    company: "예시정밀", dept: "포장팀", ask: true,
    note: "어떻게: 대근을 4시간만 해도 8시간 근무 뒤에 하면 기본급의 10%가 나와요\n예시: 주간 근무 뒤 19~23시 대근이면 기본급의 10%",
  }), "10.0.0.10");
  // [COMM-06] A talk on 가나다화학's rule set: one post flagged once, one flagged by three people and hidden.
  const say = (n, body) => send("/api/posts", { device: `seed-device-0000002${n}`, company: talk.company.id, setup: talk.setup, body }, `10.0.1.${n}`);
  await say(1, "품질팀은 토요일도 격주로 나와요. 휴일이 하나 더 있어야 할 것 같아요");
  await send("/api/votes", { device: "seed-device-00000022", company: talk.company.id, setup: talk.setup, ok: true }, "10.0.1.2");
  await say(2, "저는 이게 맞아요. 격주 토요일은 특근으로 넣고 있어요");
  const rude = await say(3, "이런 거 올리는 사람들 다 회사 끄나풀임");
  const odd2 = await say(4, "옆 공장 이야기인데 거기는 4조 3교대래요");
  for (const n of [5, 6, 7]) {
    await send(`/api/posts/${rude.id}/flag`, { device: `seed-device-0000003${n}`, reason: "abuse" }, `10.0.2.${n}`);
  }
  await send(`/api/posts/${odd2.id}/flag`, { device: "seed-device-00000038", reason: "spam" }, "10.0.2.8");
  // [ADMIN-02] Questions from the app: one with the alarm check attached, one idea.
  await send("/api/inquiries", {
    v: 1, device: "seed-device-00000011", kind: "bug", text: "잠금 화면에서 알람 소리가 가끔 안 나요. 진동만 와요",
    info: { app: "1.12.0 (18)", model: "samsung SM-S928N", android: "16 (API 36)", diag: "알람 사용: 켬\n놓쳤을 수 있는 알람: 10.17 06:00" },
  }, "10.0.0.11");
  await send("/api/inquiries", {
    v: 1, device: "seed-device-00000012", kind: "idea", text: "달력이 일요일부터 시작하면 좋겠어요",
    info: { app: "1.12.0 (18)" },
  }, "10.0.0.12");
  // [ADMUX-07] 56 days of made-up phone counts, so 지표 draws its chart with the period before.
  // [ADMUX-13] On two app versions as the phones send them, with the events behind the gates.
  const kst = (ms) => new Date(ms + 9 * 3600e3).toISOString().slice(0, 10);
  const run = (sql, ...v) => env.DB.prepare(sql).bind(...v).run();
  for (let i = 1; i <= 56; i++) {
    const day = kst(Date.now() - i * 864e5);
    const phones = 30 + ((i * 7) % 23) + (i < 29 ? 8 : 0);
    const newer = i < 10 ? Math.round(phones * 0.7) : 0;
    await run("INSERT INTO usage_days (day, app, phones) VALUES (?, '1.24.0', ?)", day, newer);
    await run("INSERT INTO usage_days (day, app, phones) VALUES (?, '1.23.0', ?)", day, phones - newer);
    for (const [event, share] of [["open", 0.9], ["pay_view", 0.35], ["share_made", 0.08], ["plaza_apply", 0.05], ["inquiry", 0.02], ["backup", 0.1]]) {
      const n = Math.max(1, Math.round(phones * share));
      await run("INSERT INTO usage_events (day, app, event, phones, total, recent) VALUES (?, '1.23.0', ?, ?, ?, ?)", day, event, Math.round(n / 3), n, n);
    }
  }
  // 1.38 Phase 3 분석판: a few days of 팀·가게 events and made-up 팀·직원 요금제 purchases, so the card has something on it.
  for (let i = 1; i <= 20; i++) {
    const day = kst(Date.now() - i * 864e5);
    for (const [event, n] of [["manage_open", 6], ["team_made", i % 4 === 0 ? 1 : 0], ["team_confirm", i % 3 === 0 ? 2 : 0], ["team_links", i % 3 === 0 ? 2 : 0],
      ["ledger_closed", i % 7 === 0 ? 1 : 0], ["slip_sent", i % 7 === 0 ? 3 : 0], ["plan_view_team", 2], ["plan_view_staff", 1], ["plan_buy_tap", i % 2],
      ["manage_hint_shown", 5], ["manage_hint_open", 1]]) {
      if (n) await run("INSERT INTO usage_events (day, app, event, phones, total, recent) VALUES (?, '1.38.0', ?, ?, ?, ?)", day, event, n, n, n);
    }
  }
  for (const [i, product, plan, trial, renew] of [[1, "bamnat_team", "c15-yearly", 1, 1], [2, "bamnat_team", "c15-monthly", 0, 1], [3, "bamnat_team", "c30-yearly", 0, 1],
    [4, "bamnat_team", "c50-monthly", 0, 0], [5, "bamnat_staff", "c5-yearly", 0, 1], [6, "bamnat_staff", "c10-monthly", 0, 1]]) {
    await run(`INSERT OR IGNORE INTO subs (hash, device, token, product, base_plan, state, expiry, trial, auto_renew, created_at, checked_at)
      VALUES (?, ?, 'seed', ?, ?, 'ACTIVE', ?, ?, ?, ?, ?)`, `seed-sub-${i}`, `seed-device-s${i}`, product, plan, Date.now() + 20 * 864e5, trial, renew, Date.now() - i * 2 * 864e5, Date.now());
  }
  await run(`INSERT OR IGNORE INTO year_passes (hash, device, order_id, state, purchased_at, created_at, checked_at, product)
    VALUES ('seed-pass-1', 'seed-device-p1', '', 'ACTIVE', ?, ?, ?, 'bamnat_team_pass_15')`, Date.now() - 5 * 864e5, Date.now(), Date.now());
  if (login.ok) await seedVersions(login.headers.get("set-cookie").split(";")[0], a);
  // [ADMUX-14] Four weeks of questions with their first answers and the askers' word, and the plaza spread over them.
  const kinds = ["bug", "question", "idea", "bug", "other"];
  for (let i = 0; i < 24; i++) {
    const at = Date.now() - (i + 1) * 864e5 + (i % 5) * 3600e3;
    const id = `seedQ${String(i).padStart(17, "0")}`;
    const kind = kinds[i % kinds.length];
    const app = i < 8 ? "1.24.0 (35)" : "1.23.0 (34)";
    await run(`INSERT INTO inquiries (id, device, kind, info, status, public, created_at, updated_at, rating) VALUES (?, ?, ?, ?, 'answered', 0, ?, ?, ?)`,
      id, `seed-device-q${i}`, kind, JSON.stringify({ app }), at, at, i % 4 === 0 ? -1 : i % 3 === 0 ? null : 1);
    await run("INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'user', ?, NULL, ?)", id, `지난 문의 ${i + 1}`, at);
    await run("INSERT INTO inquiry_messages (inquiry_id, sender, text, by, at) VALUES (?, 'admin', '확인했어요', 'owner', ?)", id, at + ((i * 97) % 600 + 20) * 60e3);
  }
  for (const t of ["reports", "posts"]) await run(`UPDATE ${t} SET created_at = created_at - (rowid % 20 + 1) * 86400000`);
  await run("UPDATE post_flags SET at = at - (rowid % 9 + 1) * 86400000");
  for (let i = 0; i < 30; i++) {
    await run("INSERT OR IGNORE INTO uses (company_id, grp, device, at) VALUES (?, ?, ?, ?)", a.company.id, a.setup, `seed-device-u${i}`, Date.now() - (i % 25 + 1) * 864e5);
  }
  // [ADMUX-16] A few requests the server could not answer.
  for (const i of [3, 11, 12]) await run("INSERT INTO counters (key, count, expires) VALUES (?, ?, ?)", `errors|${kst(Date.now() - i * 864e5)}`, i % 4 + 1, Date.now() + 400 * 864e5);
  await seedRecipes(run);
  console.log("seeded: 5 market works without days (알바 2·현장·가게·교대), 3 자랑 글 with a card, 이달의 주제 for 알바·가게·교대, 예시정밀 v1·v2 and a third version waiting, 2 waiting elsewhere, 1 turned down, 1 way the app cannot set, a talk with 2 flagged posts, 26 questions, 56 days of usage");
}

/**
 * PC 확인 0efe9dbc: 장터 themes for 알바·현장·가게 that bring colours but no kinds of day and no cover, so 오늘의 하나, the list and
 * 넘겨 보기 show each kind of work's example week. Sent a month on (처리방침 3.8 is in force then), then dated back and shown.
 */
async function seedRecipes(run) {
  const realNow = Date.now;
  const later = realNow() + 31 * 864e5;
  Date.now = () => later;
  const made = [];
  try {
    const one = (n, title, summary, style, pieces) => send("/api/recipes", {
      device: `seed-device-0000004${n}`, scope: "public", recipe: { format: "bamnat-recipe", v: 1, title, summary, styles: [style], pieces },
    }, `10.0.3.${n}`);
    made.push(await one(1, "알바 하루 정리 테마", "일한 날이 또렷한 따뜻한 색", "HOURLY", [
      { kind: "screenLook", screens: "all~bg:FFFFF8EE|cd:FFFFFFFF|ik:FF2B2B2B|ac:FF2E7D32" }, { kind: "calTheme", today: 4281236786 }]));
    made.push(await one(2, "현장 흙빛 달력", "현장 근무 날이 잘 보여요", "FIELD", [{ kind: "calTheme", today: 4285353025, sun: 4291176488 }]));
    made.push(await one(3, "가게 영업일 테마", "영업일과 쉬는 날을 한눈에", "BUSINESS", [
      { kind: "screenLook", screens: "all~bg:FFEFF7F6|cd:FFFFFFFF|ik:FF1F2D2B|ac:FF00897B" }, { kind: "calTheme", today: 4278225275 }]));
    // PC 확인 38d63451: 넘겨 보기 has more than one to flip, of more than one kind of work.
    made.push(await one(4, "알바 밤 근무 테마", "어두운 바탕에 일한 날만 밝게", "HOURLY", [
      { kind: "screenLook", screens: "all~bg:FF1E1E2A|cd:FF2A2A3A|ik:FFF2F2F2|ac:FFFFB74D" }]));
    made.push(await one(5, "교대 새벽빛 테마", "주·야·휴를 새벽 하늘 색으로", "SHIFT", [
      { kind: "screenLook", screens: "all~bg:FFF3F0FA|cd:FFFFFFFF|ik:FF2B2540|ac:FF5E35B1" }, { kind: "calTheme", today: 4284298673 }]));
  } finally {
    Date.now = realNow;
  }
  const codes = [];
  for (const [i, r] of made.entries()) {
    const code = r.code ?? r.recipe?.code;
    codes.push(code);
    if (!code) { console.log("seed recipe not made:", JSON.stringify(r)); continue; }
    const at = realNow() - (i + 2) * 864e5;
    await run("UPDATE recipes SET state = 'visible', created_at = ?, updated_at = ?, decided_at = ? WHERE code = ?", at, at, at, code);
  }
  const [hourly, , shop, hourlyNight, shift] = codes;
  // 자랑 글 with its maker's theme as a card, on 알바·가게·교대 광장.
  Date.now = () => later;
  const shows = [];
  try {
    for (const [n, room, body, code] of [[1, "style-hourly", "제가 만든 테마예요. 일한 날이 잘 보여서 좋아요", hourly],
      [3, "style-shop", "가게 영업일 테마 만들어 봤어요", shop], [5, "style-shift", "새벽 근무 많은 분께 맞을 거예요", shift]]) {
      if (!code) continue;
      const r = await send("/api/posts", { device: `seed-device-0000004${n}`, company: room, setup: "room", body, kind: "show", attach: code }, `10.0.3.${n}`);
      if (r.id) shows.push(r.id); else console.log("seed show post not made:", JSON.stringify(r));
    }
  } finally {
    Date.now = realNow;
  }
  for (const [i, id] of shows.entries()) await run("UPDATE posts SET created_at = ? WHERE id = ?", realNow() - (i + 1) * 3600e3, id);
  // 이달의 주제 for 알바·가게·교대 with an entry each, and last month's picks, so 공방's card shows both.
  const month = new Date(realNow() + 9 * 3600e3).toISOString().slice(0, 7);
  const [y, m] = month.split("-").map(Number);
  const last = m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, "0")}`;
  for (const [style, theme, entry, picked] of [["HOURLY", "일한 날이 한눈에", hourly, hourlyNight], ["BUSINESS", "영업일이 반가운 달력", shop, shop],
    ["SHIFT", "야간이 편한 달력", shift, shift]]) {
    await run("INSERT OR REPLACE INTO village_contests (month, style, theme, picks, at) VALUES (?, ?, ?, '[]', ?)", month, style, theme, realNow());
    if (entry) await run("INSERT OR IGNORE INTO village_entries (month, style, code, device, at) VALUES (?, ?, ?, 'seed', ?)", month, style, entry, realNow());
    if (picked) await run("INSERT OR REPLACE INTO village_contests (month, style, theme, picks, at) VALUES (?, ?, ?, ?, ?)", last, style, "지난달 주제", JSON.stringify([picked]), realNow());
  }
}

/**
 * [ADMUX-18] 예시정밀's rule in two versions (v1, then the 12% one as v2) and a third setup waiting beside it,
 * so 여러 판 has a department to decide and an earlier version to bring back.
 */
async function seedVersions(cookie, first) {
  const adopt = (company, grp) => worker.fetch(new Request(`http://localhost:${port}/api/admin/groups/${company}/${grp}/adopt`, {
    method: "POST", headers: { "content-type": "application/json", "x-admin": "1", cookie }, body: "{}",
  }), env);
  await adopt(first.company.id, first.setup);
  const queue = await worker.fetch(new Request(`http://localhost:${port}/api/admin/queue`, { headers: { cookie } }), env).then((r) => r.json());
  const v2 = queue.groups.find((g) => g.company.id === first.company.id && g.setup !== first.setup && !g.dept);
  if (v2) await adopt(first.company.id, v2.setup);
  const third = setup();
  third.pay.rules.extra[3].percent = 0.15;
  await send("/api/reports", report(13, { company: "예시정밀", setup: third, note: "우리 조는 대근 15%예요" }), "10.0.0.13");
  await send("/api/reports", report(14, { company: "예시정밀", setup: third }), "10.0.0.14");
}

/** [ADMIN-04] One approved admin (minji@example.com, password minji-review-2026), one waiting, one invite out. */
async function seedAdmins(cookie) {
  const asOwner = (path, body) => worker.fetch(new Request(`http://localhost:${port}/api/admin${path}`, {
    method: "POST", headers: { "content-type": "application/json", "x-admin": "1", cookie }, body: JSON.stringify(body),
  }), env).then((r) => r.json());
  const reviewer = { review: "edit", inquiries: "edit", companies: "view", rewards: "none", settings: "none", audit: "view" };
  for (const [email, name, approve] of [["minji@example.com", "민지", true], ["junho@example.com", "준호", false]]) {
    const inv = await asOwner("/invites", { email, perms: reviewer, note: approve ? "검토 담당" : "" });
    // A second --seed on a kept database: the admin is there already (409 exists), so there is no invite to join.
    if (!inv.link) continue;
    await worker.fetch(new Request(`http://localhost:${port}/api/admin/join`, {
      method: "POST", headers: { "content-type": "application/json", "x-admin": "1", "cf-connecting-ip": "10.0.0.20" },
      body: JSON.stringify({ token: inv.link.split("#")[1], name, password: "minji-review-2026" }),
    }), env);
    if (approve) {
      const list = await worker.fetch(new Request(`http://localhost:${port}/api/admin/admins`, { headers: { cookie } }), env).then((r) => r.json());
      await asOwner(`/admins/${list.admins.find((a) => a.email === email).id}`, { status: "active" });
    }
  }
  await asOwner("/invites", { email: "seojin@example.com", perms: reviewer, note: "문의 답변" });
}

if (args.includes("--seed")) await seed();

// AE 1.26: --test-clock lets an app test move this server's clock on (POST /__test/clock {days}), as for a
// friend's second day [INVITE-03]. Only this local server has it; the worker itself never does.
const testClock = args.includes("--test-clock");
if (testClock) {
  const realNow = Date.now.bind(Date);
  let offset = 0;
  Date.now = () => realNow() + offset;
  globalThis.__moveClock = (days) => { offset += days * 24 * 60 * 60 * 1000; };
}

http.createServer(async (req, res) => {
  if (testClock && req.method === "POST" && req.url.startsWith("/__test/clock")) {
    globalThis.__moveClock(Number(new URL(req.url, "http://x").searchParams.get("days") || 1));
    res.writeHead(204);
    res.end();
    return;
  }
  // 1.36 [GRW-02][GRW-03][GRW-05] POST /__test/tick: the hourly work now (스튜디오 요구 묶음 → 장터, 승격 사다리), so a phone test needn't wait.
  if (testClock && req.method === "POST" && req.url.startsWith("/__test/tick")) {
    const waits = [];
    // 1.38 [ADM3-05] ?cron=5 runs the five-minute work instead (보류가 끝난 Claude 답 보내기).
    const cron = new URL(req.url, "http://x").searchParams.get("cron") === "5" ? "*/5 * * * *" : "17 * * * *";
    await worker.scheduled({ cron, scheduledTime: Date.now() }, env, { waitUntil: (p) => waits.push(p) });
    await Promise.all(waits);
    res.writeHead(204);
    res.end();
    return;
  }
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const headers = new Headers();
  for (const [k, v] of Object.entries(req.headers)) if (typeof v === "string") headers.set(k, v);
  // 1.36 PC 시험: with --test-clock, x-test-ip stands for another network (두 에뮬레이터가 한 회선으로 보이니 회선 규칙을 볼 때).
  headers.set("cf-connecting-ip", (testClock && typeof req.headers["x-test-ip"] === "string" && req.headers["x-test-ip"]) || req.socket.remoteAddress || "unknown");
  const body = ["GET", "HEAD"].includes(req.method) ? undefined : Buffer.concat(chunks);
  const out = await worker.fetch(new Request(`http://${req.headers.host}${req.url}`, { method: req.method, headers, body, redirect: "manual" }), env);
  const head = {};
  out.headers.forEach((v, k) => { head[k] = v; });
  res.writeHead(out.status, head);
  res.end(Buffer.from(await out.arrayBuffer()));
}).listen(port, "127.0.0.1", () => console.log(`company worker on http://localhost:${port} (developer app: /admin/)`));
