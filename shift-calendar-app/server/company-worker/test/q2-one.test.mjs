// 2.0.0 갈래 Q2 4/4d (검토 23·24): 스토어 리뷰도 운영 장부 한 줄, 답을 보내는 문은 하나.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN, TEST_NOW } from "./helpers.mjs";

const APP = { "x-admin": "1", origin: ORIGIN };
const SUPPORT = { review: "none", inquiries: "edit", dev: "edit", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };
const HOUR = 3600 * 1000;

async function member(call, owner, perms = SUPPORT, email = "minji@example.com", ip = 40) {
  const inv = await call("POST", "/api/admin/invites", { email, perms }, owner);
  assert.equal(inv.status, 201, inv.text);
  const token = inv.data.link.split("#")[1];
  assert.equal((await call("POST", "/api/admin/join", { token, name: "민지", password: "minji-answers-2026" }, { ...APP, ...from(ip) })).status, 201);
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: "minji-answers-2026" }, { ...APP, ...from(ip) });
  assert.equal(res.status, 200, res.text);
  return { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
}

const ask = (call, n, text, kind = "other") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "2.0.0 (54)" } }, from(n)).then((r) => r.data.id);
const thread = (call, id, n) => call("GET", `/api/inquiries/${id}`, undefined, { "x-device": device(n) }).then((r) => r.data);

test("[RVA-50] 스토어 리뷰도 운영 장부 한 줄: 별 1~2개는 답 기한, 초안이 없으면 Claude 차례, 초안이 있으면 최고 관리자, 답한 것은 닫힘", async (t) => {
  const { call, DB } = server({ t });
  const owner = await signIn(call);
  const add = DB.prepare(`INSERT INTO store_reviews (id, stars, text, app, lang, at, reply, reply_at, replied_by, draft, draft_by, status, fetched_at)
    VALUES (?1, ?2, ?3, '2.0.0', 'ko', ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?4)`);
  await add.bind("gp:AOqpTOE-low-0001", 1, "알람이 안 울려서 지각했어요", TEST_NOW - 30 * HOUR, "", null, "", "", "", "new").run();
  await add.bind("gp:AOqpTOE-draft-0002", 4, "달력이 예뻐요", TEST_NOW - 2 * HOUR, "", null, "", "고마워요. 밤낮 운영팀", "Claude", "seen").run();
  await add.bind("gp:AOqpTOE-done-0003", 5, "좋아요", TEST_NOW - 5 * HOUR, "고마워요", TEST_NOW - HOUR, "운영자", "", "", "replied").run();

  const open = (await call("GET", "/api/admin/ledger?view=open", undefined, owner)).data;
  const rv = open.items.filter((r) => r.type === "store_review");
  assert.equal(rv.length, 2);
  const low = rv.find((r) => r.id === "gp:AOqpTOE-low-0001");
  assert.equal(low.typeName, "스토어 리뷰");
  assert.equal(low.group, "ask", "리뷰는 문의·요구 묶음");
  assert.equal(low.stars, 1);
  assert.match(low.title, /^별 1개 · 알람이 안 울려서/);
  assert.equal(low.turn, "claude");
  assert.equal(low.late, true, "별 1~2개는 24시간 안에 답");
  assert.equal(low.link, "#/reviews");
  const draft = rv.find((r) => r.id === "gp:AOqpTOE-draft-0002");
  assert.equal(draft.turn, "owner", "Play로 보내기는 최고 관리자");
  assert.equal(draft.answer.key, "draft");
  assert.equal(draft.due, null, "별 4개는 기한 없음");
  assert.ok(open.counts.groups.ask >= 2);
  // 한 대기열: 장부 상태·기록이 리뷰 줄에도 그대로(콜론이 든 긴 Play 번호 그대로).
  const set = await call("POST", "/api/admin/ledger/state", { keys: [low.key], state: "waiting", reason: "other", note: "Play에서 더 볼게요" }, owner);
  assert.equal(set.status, 200, set.text);
  assert.equal((await call("GET", `/api/admin/ledger/log?key=${encodeURIComponent(low.key)}`, undefined, owner)).status, 200);
  const closed = (await call("GET", "/api/admin/ledger?view=closed", undefined, owner)).data.items.filter((r) => r.type === "store_review");
  assert.deepEqual(closed.map((r) => [r.id, r.answer.key]), [["gp:AOqpTOE-done-0003", "sent"]]);
  // 문의 권한이 없으면 리뷰 줄도 없어요.
  const ops = await member(call, owner, { ...SUPPORT, inquiries: "none", dev: "none", ops: "edit" }, "ops@example.com", 41);
  assert.equal((await call("GET", "/api/admin/ledger?view=all", undefined, ops)).data.items.filter((r) => r.type === "store_review").length, 0);
});

test("[RVA-51] 보내는 문 하나: 사람이 쓴 답·여러 문의 답·개발사항 답·붙여 넣기·열쇠가 같은 검사를 지나고, 걸리면 같은 모양(사람이 보내야 해요 + 까닭)으로 멈춰요", async (t) => {
  const { call } = server({ t });
  const owner = await signIn(call);
  const staff = await member(call, owner);
  const key = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data.key;
  const claude = { authorization: `Bearer ${key}`, "x-admin": "1" };
  const refund = await ask(call, 1, "프로 결제했는데 환불해 주세요");
  const plain = await ask(call, 2, "알람 소리를 바꾸고 싶어요", "question");
  const heldOf = async (id) => (await call("GET", `/api/admin/inquiries/${id}`, undefined, owner)).data.held;

  // 1) 사람이 쓴 답(관리자): 환불 문의는 멈추고 까닭을 돌려줘요. 보낸 분 앱에는 아무것도 가지 않아요.
  const direct = await call("POST", `/api/admin/inquiries/${refund}/reply`, { text: "확인해 볼게요" }, staff);
  assert.equal(direct.status, 200, direct.text);
  assert.equal(direct.data.gated.key, "money");
  assert.equal((await thread(call, refund, 1)).messages.length, 1);
  const h1 = await heldOf(refund);
  assert.equal(h1.length, 1);
  assert.equal(h1[0].person, true);
  assert.equal(h1[0].why.key, "money", "문의 화면도 같은 까닭");
  // 2) 같은 문의라도 최고 관리자가 직접 쓴 답은 그 확인이 된 것이라 가요.
  const byOwner = await call("POST", `/api/admin/inquiries/${refund}/reply`, { text: "결제 내역을 확인했어요. 밤낮 운영팀" }, owner);
  assert.equal(byOwner.data.gated, undefined);
  assert.equal((await thread(call, refund, 1)).messages.at(-1).text, "결제 내역을 확인했어요. 밤낮 운영팀");
  // 3) 답 글에 AI 표현이 있으면 누가 쓰든(최고 관리자도) 멈춰요.
  const ai = await call("POST", `/api/admin/inquiries/${plain}/reply`, { text: "저는 AI 상담원이에요. 설정 › 알람에서 바꿔요." }, owner);
  assert.equal(ai.data.gated.reply, true);
  assert.equal((await thread(call, plain, 2)).messages.length, 1);
  // 4) 여러 문의에 같은 답: 걸린 것은 따로 세요.
  const another = await ask(call, 3, "환불 받고 싶어요");
  const bulk = (await call("POST", "/api/admin/inquiries/bulk", { action: "reply", ids: [another, plain], text: "설정 › 알람에서 바꿀 수 있어요. 밤낮 운영팀" }, staff)).data;
  assert.deepEqual([bulk.done, bulk.held], [1, 1]);
  assert.equal((await thread(call, another, 3)).messages.length, 1);
  assert.equal((await thread(call, plain, 2)).messages.at(-1).text, "설정 › 알람에서 바꿀 수 있어요. 밤낮 운영팀");
  // 5) 개발사항에 묶인 문의에 한 번에 답: 걸린 문의만 멈춰요.
  const both = await ask(call, 4, "환불이 안 돼요 결제 오류", "bug");
  const fine = await ask(call, 5, "위젯 글자가 잘려요", "bug");
  const item = (await call("POST", "/api/admin/dev", { title: "위젯 고치기", inquiry: both }, staff)).data.id;
  await call("POST", `/api/admin/dev/${item}/links`, { inquiry: fine }, staff);
  const dev = (await call("POST", `/api/admin/dev/${item}/reply`, { text: "다음 업데이트에서 고쳐져요. 밤낮 운영팀" }, staff)).data;
  assert.deepEqual([dev.answered, dev.held], [1, 1]);
  assert.equal((await thread(call, both, 4)).messages.length, 1);
  // 6) 열쇠(Claude): 같은 까닭으로 멈춰요(10분 보류가 아니라 사람이 보내기).
  await call("POST", `/api/admin/inquiries/${another}/reply`, { text: "환불은 Play에서 돼요. 밤낮 운영팀" }, claude);
  const k = (await heldOf(another)).find((h) => h.text.startsWith("환불은 Play"));
  assert.equal(k.person, true);
  assert.equal(k.why.key, "money");
  // 7) 붙여 넣기: 걸린 답은 버리지 않고 문의에 멈춰요. '그래도 보내기'를 고르면 그 멈춘 답이 가요.
  const third = await ask(call, 6, "결제 환불 문의드려요");
  const block = `q${third} 답 결제를 확인했어요. 밤낮 운영팀`;
  const r = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block }, owner)).data;
  assert.deepEqual([r.answered, r.held], [0, 1]);
  assert.equal((await heldOf(third)).filter((h) => h.person).length, 1);
  const go = (await call("POST", "/api/admin/inquiries/handoff/result", { text: block, risky: true }, owner)).data;
  assert.equal(go.answered, 1);
  assert.equal((await thread(call, third, 6)).messages.at(-1).text, "결제를 확인했어요. 밤낮 운영팀");
  assert.equal((await heldOf(third)).length, 0);
  // 정리함(사람이 쓰는 길)으로는 '그래도 보내기'를 몰래 넣을 수 없어요.
  const sneak = (await call("POST", "/api/admin/inquiries/sort/apply", { actions: [{ do: "reply", id: another, text: "바로 환불해 드려요. 밤낮 운영팀", confirmed: true }] }, staff)).data;
  assert.deepEqual([sneak.replied, sneak.held], [0, 1]);
});

test("[RVA-52] 화면 자체가 시험: 관리자 앱이 알린 화면 오류·빈 화면이 오류 묶음 '화면 오류'로 모이고 개선 신호가 돼요(화면 이름·빌드·첫 줄만, 한 시간 30번까지)", async (t) => {
  const { call } = server({ t });
  const owner = await signIn(call);
  const staff = await member(call, owner);
  const sent = await call("POST", "/api/admin/screen-error", { screen: "desk", where: "ledger-line", kind: "error", message: "Cannot read 'title' of 1234567 \"김민지\"", build: "abc123" }, staff);
  assert.equal(sent.status, 202, sent.text);
  await call("POST", "/api/admin/screen-error", { screen: "desk", where: "ledger-line", kind: "error", message: "Cannot read 'other' of 7654321 \"박서준\"", build: "abc123" }, staff);
  await call("POST", "/api/admin/screen-error", { screen: "glance", where: "view", kind: "blank", build: "abc123" }, owner);
  // 화면 이름이 아닌 것은 'unknown'으로(주소·글이 이름 칸에 들어가지 않아요).
  await call("POST", "/api/admin/screen-error", { screen: "../../secret 010-1234", kind: "error", message: "x" }, owner);
  const groups = (await call("GET", "/api/admin/watch/errors?status=all", undefined, owner)).data.groups;
  const desk = groups.filter((g) => g.route === "screen/desk");
  assert.equal(desk.length, 1, "같은 오류는 따옴표 안·숫자가 달라도 한 묶음");
  assert.equal(desk[0].total, 2);
  assert.doesNotMatch(desk[0].message, /김민지|박서준|1234567/);
  assert.equal(desk[0].app, "abc123");
  assert.ok(groups.some((g) => g.route === "screen/glance" && /빈 화면/.test(g.message)));
  assert.ok(groups.some((g) => g.route === "screen/unknown"));
  // 개선 신호(한눈에): 화면 오류는 한 번이어도.
  const o = (await call("GET", "/api/admin/observe", undefined, owner)).data;
  const sig = o.found.filter((f) => f.kind === "screen-error" && f.on);
  assert.ok(sig.some((f) => /'desk' 화면 오류 2번/.test(f.title)), JSON.stringify(sig.map((f) => f.title)));
  // 한 관리자가 한 시간에 30번까지.
  let limited = 0;
  for (let i = 0; i < 32; i++) {
    const r = await call("POST", "/api/admin/screen-error", { screen: "today", kind: "error", message: `boom ${i}` }, staff);
    if (r.data.limited) limited++;
  }
  assert.ok(limited >= 2);
});

test("[RVA-53] 분류 식을 장부가 다듬기: '판정이 틀렸어요'·'아니에요'로 뺀 말이 세 번 넘으면 한눈에 '분류 고칠 것' 신호", async (t) => {
  const { call } = server({ t });
  const owner = await signIn(call);
  const key = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data.key;
  const claude = { authorization: `Bearer ${key}`, "x-admin": "1" };
  for (let n = 1; n <= 3; n++) {
    const id = await ask(call, 10 + n, `구독 해지 말고 알람만 끄는 법이 궁금해요 ${n}`, "question");
    await call("POST", `/api/admin/inquiries/${id}/reply`, { text: "설정 › 알람에서 꺼요. 밤낮 운영팀" }, claude);
    const held = (await call("GET", `/api/admin/inquiries/${id}`, undefined, owner)).data.held;
    assert.equal(held[0].person, true, "'해지'에 걸려 사람이 보내기");
    assert.equal((await call("POST", `/api/admin/inquiries/${id}/held/${held[0].id}`, { miss: true }, owner)).status, 200);
  }
  const o = (await call("GET", "/api/admin/observe", undefined, owner)).data;
  const word = o.askMiss.find((m) => m.n >= 3);
  assert.ok(word, JSON.stringify(o.askMiss));
  const sig = o.found.find((f) => f.kind === "ask-miss" && f.on);
  assert.match(sig.title, /^분류 고칠 것: /);
  assert.match(sig.dev, /낱말 식과 예문 다듬기/);
});

test("[RVA-54] 숫자 → 개발 → 효과 한 줄: 개발사항이 된 신호는 뜸·개발사항·닿음·돌아옴 걸음으로 오늘·한눈에·개발사항 이야기에, 닿고 2주가 지나도 그대로면 저절로 다시 대기열", async (t) => {
  const { call, env, DB } = server({ t });
  const owner = await signIn(call);
  const DAY = 24 * HOUR;
  const signal = { key: "rating-low:2027-03-01", kind: "rating-low", subject: "", tone: "warn", title: "별점이 3.1점이에요", why: "리뷰를 봐요", dev: "별점 낮은 까닭 찾기",
    to: "#/reviews", unit: "점", better: "up", value: 3.1, base: 3.5, now: 3.1, first: TEST_NOW - 30 * DAY, last: TEST_NOW - HOUR, at: TEST_NOW - 30 * DAY, state: "new" };
  await DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('obs_signals', ?)").bind(JSON.stringify({ list: [signal] })).run();
  const made = await call("POST", "/api/admin/observe/signals", { key: signal.key, act: "dev" }, owner);
  assert.equal(made.status, 200, made.text);
  const dev = made.data.signal.devId;
  // 아직 닿지 않음: 걸음 넷 가운데 둘(뜸·개발사항)만 지났어요.
  let o = (await call("GET", "/api/admin/observe", undefined, owner)).data;
  let s = o.signals.find((x) => x.key === signal.key);
  assert.deepEqual(s.steps.map((x) => [x.key, Boolean(x.at)]), [["seen", true], ["dev", true], ["ship", false], ["wait", false]]);
  // 2.0.0에서 고쳐 나갔고(20일 전), 숫자는 그대로 → 매시 작업이 다시 대기열로.
  await DB.prepare("UPDATE dev_items SET status = 'done', released_in = '2.0.0', updated_at = ? WHERE id = ?").bind(TEST_NOW - 21 * DAY, dev).run();
  await DB.prepare("INSERT INTO audit (at, action, target, detail, actor, actor_id) VALUES (?, 'shipped_version', '2.0.0', '', '관리자', '')").bind(TEST_NOW - 20 * DAY).run();
  const { observeTick } = await import("../src/observe.js");
  await observeTick(env, TEST_NOW);
  o = (await call("GET", "/api/admin/observe", undefined, owner)).data;
  s = o.signals.find((x) => x.key === signal.key);
  assert.equal(s.state, "new");
  assert.equal(s.prevDev, dev);
  assert.equal(s.devId ?? null, null, "새 개발사항으로 다시 넘길 수 있어요");
  assert.deepEqual(s.steps.map((x) => x.key), ["seen", "dev", "ship", "stuck"]);
  assert.ok(s.steps.every((x) => x.at));
  // 오늘: 숫자를 보는 사람에게 같은 한 줄.
  const today = (await call("GET", "/api/admin/today", undefined, owner)).data;
  assert.equal(today.selfLoop[0].stuck, true);
  assert.equal(today.selfLoop[0].steps.at(-1).key, "stuck");
  // 개발사항 이야기에도 신호의 걸음.
  const story = (await call("GET", `/api/admin/story/${dev}`, undefined, owner)).data;
  assert.ok(story.events.some((e) => e.kind === "signal"));
  assert.ok(story.events.some((e) => e.kind === "stuck"));
  // 다시 개발사항으로.
  const again = await call("POST", "/api/admin/observe/signals", { key: signal.key, act: "dev" }, owner);
  assert.equal(again.status, 200, again.text);
  assert.notEqual(again.data.signal.devId, dev);
});

test("[RVA-55] 화면 사진 회귀 시험: 주요 12화면 × 네 폭 × 밝음·어두움, 지문 거리는 같은 사진 0·뒤집힌 사진 1, CI가 통합 빌드마다 돌려요", async () => {
  const { SCREENS, WIDTHS, SCHEMES, distance } = await import("../tools/admin-shots.mjs");
  assert.equal(SCREENS.length, 12);
  assert.deepEqual(WIDTHS, [380, 840, 1280, 1440]);
  assert.deepEqual(SCHEMES, ["light", "dark"]);
  const a = "f0".repeat(128);
  assert.equal(distance(a, a), 0);
  assert.equal(distance(a, "0f".repeat(128)), 1);
  assert.equal(distance(a, null), 1);
  const { readFile } = await import("node:fs/promises");
  const ci = await readFile(new URL("../../../../.github/workflows/android.yml", import.meta.url), "utf8");
  assert.match(ci, /admin-shots:[\s\S]*node tools\/admin-shots\.mjs --start --out build\/admin-shots --prev/);
});

test("[RVA-56] 장터 건강: CI가 묶은 작품 사진 대조·옛 앱 열기 결과를 올리면 깨진 작품이 운영 장부 줄(이틀 기한)로, 나으면 사라지고, 장터를 돌보지 않는 사람은 올리지도 보지도 못해요", async (t) => {
  const { call } = server({ t });
  const owner = await signIn(call);
  const report = (broken) => ({ format: "bamnat-market-health", v: 1, build: "abc1234def", at: new Date(TEST_NOW - HOUR).toISOString(), works: 3, listed: 3, opened: 2,
    broken, changed: [{ key: "market-139#2", title: "둘", d: 12.34 }], unchanged: [{ key: "market-139#3", title: "셋" }] });
  const bad = [{ key: "market-139#1", title: "하나", kind: "old", why: "unknown piece\u0000kind", app: 53 }, { key: "", title: "이름 없음", kind: "error", why: "x" }];
  assert.equal((await call("POST", "/api/admin/recipes/health", { format: "other", v: 1, build: "x" }, owner)).status, 400);
  const up = await call("POST", "/api/admin/recipes/health", report(bad), owner);
  assert.equal(up.status, 201, up.text);
  assert.equal(up.data.broken, 1);

  const got = (await call("GET", "/api/admin/recipes/health", undefined, owner)).data;
  assert.equal(got.last.build, "abc1234def");
  assert.equal(got.last.broken[0].why, "unknown piece kind");
  assert.equal(got.last.changed[0].d, 12.3);
  assert.equal(got.stale, false);
  assert.equal(got.runs.length, 1);

  const line = (await call("GET", "/api/admin/ledger?view=open", undefined, owner)).data.items.find((r) => r.type === "market_broken");
  assert.ok(line, "깨진 작품 줄");
  assert.equal(line.key, "market_broken:market-139.1");
  assert.equal(line.typeName, "깨진 작품");
  assert.match(line.title, /하나 · 옛 앱에서 안 열림/);
  assert.equal(line.link, "#/recipes?tab=health");
  assert.equal(line.due, new Date(TEST_NOW - HOUR + 48 * HOUR).toISOString(), "다음 출시를 막아요: 이틀 안");
  assert.equal(line.group, "report");
  assert.equal(line.turn, "claude");

  // 1:1 문의만 보는 사람: 올리지 못하고, 장부에도 깨진 작품 줄이 없어요.
  const helper = await member(call, owner);
  assert.equal((await call("POST", "/api/admin/recipes/health", report(bad), helper)).status, 403);
  const theirs = (await call("GET", "/api/admin/ledger?view=open", undefined, helper)).data.items ?? [];
  assert.equal(theirs.filter((r) => r.type === "market_broken").length, 0);

  // 다음 빌드에서 나으면 그 줄은 사라지고, 지난 결과 숫자는 남아요.
  assert.equal((await call("POST", "/api/admin/recipes/health", { ...report([]), build: "fed9876" }, owner)).status, 201);
  const after = (await call("GET", "/api/admin/ledger?view=open", undefined, owner)).data.items.filter((r) => r.type === "market_broken");
  assert.equal(after.length, 0);
  const runs = (await call("GET", "/api/admin/recipes/health", undefined, owner)).data.runs;
  assert.deepEqual(runs.map((r) => [r.build, r.broken]), [["fed9876", 0], ["abc1234def", 1]]);

  // 관리자 앱: 장터 › 장터 건강 보기와 #/recipes?tab=health 주소.
  const { readFile } = await import("node:fs/promises");
  const app = await readFile(new URL("../src/admin-app.js", import.meta.url), "utf8") + await readFile(new URL("../src/admin/views/market.js", import.meta.url), "utf8");
  assert.match(app, /\["health", "장터 건강"\]/);
  assert.match(app, /recipes: \{ tab: "recipeTab" \}/);
  const ci = await readFile(new URL("../../../../.github/workflows/android.yml", import.meta.url), "utf8");
  assert.match(ci, /tools\/market_health\.py --shots app\/build\/market-compare\.json/);
});

test("[RVA-57] 묶음 나누기: 첫 묶음은 압축해 150KB 아래, 화면 묶음 열한 개는 해시 이름 파일, 이름표를 못 만들면 예전처럼 한 파일", async () => {
  const { gzipSync } = await import("node:zlib");
  const { adminPrebuilt, viewNames } = await import("../src/admin-app.js");
  const { files } = adminPrebuilt();
  const app = files["/admin/app.js"].body;
  const gz = gzipSync(Buffer.from(app)).length;
  assert.ok(gz < 150 * 1024, `첫 묶음 압축 ${Math.round(gz / 1024)}KB`);
  assert.ok(Buffer.byteLength(app) < 520 * 1024, `첫 묶음 ${Math.round(Buffer.byteLength(app) / 1024)}KB (나누기 전 838KB)`);
  const groups = Object.keys(files).filter((p) => p.startsWith("/admin/v/"));
  assert.deepEqual(groups.map((p) => p.split("/").pop().split(".")[0]).sort(),
    ["flags", "flow", "glance", "insight", "ledger", "loop", "market", "ops", "plaza", "today", "work"]);
  for (const p of groups) {
    assert.match(p, /^\/admin\/v\/[a-z]+\.[0-9a-z]+\.js$/);
    assert.equal(files[p].hashed, true);
    assert.ok(app.includes(JSON.stringify(p)), `${p} is named in the first bundle`);
  }
  // 이름표: 묶음이 돌려주는 이름(기능 관리의 단추·양식·즉시 저장도).
  const { flagsViews } = await import("../src/admin/views/flags.js");
  const n = viewNames(flagsViews);
  assert.ok(n.fns.includes("flagsView") && n.fns.includes("policyConfirmRow"));
  assert.ok(n.actions.length > 10 && n.forms.length > 3 && n.changes.length >= 1);
  const { opsViews } = await import("../src/admin/views/ops.js");
  assert.ok(viewNames(opsViews).maps.opsViews.includes("incident"));
  // 같은 이름을 두 묶음이 주지 않아요(RVD-04).
  const all = [];
  for (const mod of ["today", "plaza", "insight", "work", "flow", "ledger", "ops", "loop", "glance", "market", "flags"]) {
    const m = await import(`../src/admin/views/${mod}.js`);
    const v = viewNames(Object.values(m).find((f) => typeof f === "function" && /Views$/.test(f.name)));
    all.push(...v.fns, ...Object.keys(v.maps));
  }
  assert.deepEqual(all.filter((k, i) => all.indexOf(k) !== i), []);
});

test("[RVA-58] 개발사항 580줄: 처음은 지금 볼 것(1순위·문의·숫자 신호·요금 정할 것)만, 갈래로 골라 보고, 요금 판단은 300줄 뒤의 새 기능도 빠뜨리지 않아요", async (t) => {
  const { call, DB } = server({ t });
  const owner = await signIn(call);
  const add = DB.db.prepare(`INSERT INTO dev_items (id, title, body, status, track, size, route, origin, created_by, created_by_name, created_at, updated_at)
    VALUES (?, ?, ?, 'doing', ?, ?, ?, ?, 'sync', '저장소', ?, ?)`);
  // 저장소 계획 580줄(고침·다듬기, 갈래 RVI·RVH와 적은 앞머리 몇 개). 새 기능 둘은 가장 오래된 쪽에 있어 최근 300줄 밖이에요.
  for (let i = 0; i < 580; i++) {
    const track = i < 300 ? `RVI-${i + 1}` : i < 570 ? `RVH-${i}` : `OLD${i % 3}-${i}`;
    add.run(`d${String(i).padStart(15, "0")}`, `계획 ${i}`, "저장소 줄", track, i % 2 ? "fix" : "polish", "app", "", TEST_NOW - (600 - i) * 60_000, TEST_NOW - (600 - i) * 60_000);
  }
  add.run("dnewfeature00001", "새 기능 하나", "", "RVK-1", "feature", "app", "", TEST_NOW - 900 * 60_000, TEST_NOW - 900 * 60_000);
  add.run("dnewfeature00002", "운영용 새 기능", "", "RVK-2", "feature", "server", "", TEST_NOW - 901 * 60_000, TEST_NOW - 901 * 60_000);
  add.run("dsignal000000001", "삭제가 늘었어요", "관측 신호: 삭제 급증", "", "fix", "app", "", TEST_NOW - 902 * 60_000, TEST_NOW - 902 * 60_000);
  const mine = await call("POST", "/api/admin/dev", { title: "직접 넣은 것", body: "내가 넣었어요" }, owner);
  assert.equal(mine.status, 201, mine.text);

  const now = (await call("GET", "/api/admin/dev?status=now", undefined, owner)).data;
  assert.equal(now.counts.now, 3);
  assert.equal(now.counts.open, 584);
  assert.deepEqual(now.items.map((x) => x.title), ["직접 넣은 것", "새 기능 하나", "삭제가 늘었어요"]);
  assert.equal(now.next, null);
  // 갈래(추적 번호 앞머리): 5개 이상만 따로, 적은 것은 '그 밖'.
  const open = (await call("GET", "/api/admin/dev?status=open", undefined, owner)).data;
  assert.equal(open.tracks.RVI, 300);
  assert.equal(open.tracks.RVH, 270);
  assert.equal(open.tracks.etc, 12);
  assert.equal(open.items.length, 50);
  const rvh = (await call("GET", "/api/admin/dev?status=open&track=RVH", undefined, owner)).data;
  assert.equal(rvh.total, 270);
  assert.ok(rvh.items.every((x) => x.track.startsWith("RVH-")));
  const etc = (await call("GET", "/api/admin/dev?status=open&track=etc", undefined, owner)).data;
  assert.equal(etc.total, 12);
  // 요금 판단: 정할 것은 새 기능 하나(운영용 새 기능은 묻지 않아요), 모두는 584줄을 다 셈.
  const tiers = (await call("GET", "/api/admin/loop/tiers?view=ask", undefined, owner)).data;
  assert.deepEqual(tiers.items.map((x) => x.id), ["dnewfeature00001"]);
  assert.equal(tiers.counts.all, 584);
  // Claude가 가져갈 순서도 1순위가 맨 앞이에요(400줄에서 자르지 않아요).
  const queue = (await call("GET", "/api/admin/loop/queue", undefined, owner)).data;
  assert.equal(queue.items[0].title, "직접 넣은 것");
});

test("[RVA-59] 다가오는 날짜: 날짜 약속 장부를 KST 오늘부터 남은 날 순서로, 지난 날은 빼고, 빈틈은 그 날짜까지 경고 일수 안일 때만", async (t) => {
  const { datesNow } = await import("../src/dates.js");
  const at = Date.parse("2026-11-12T16:30:00Z"); // KST 11-13 01:30
  const d = datesNow([
    { id: "b", date: "2026-11-20", what: "7일 줄", area: "앱", before: "글꼴 봐요" },
    { id: "a", date: "2026-11-13", what: "게시일", area: "사람", before: "" },
    { id: "old", date: "2026-11-12", what: "어제", area: "", before: "" },
    { id: "rates", date: "2027-01-01", what: "새해 요율", gap: { from: "2028-01-01", warnDays: 120, what: "요율 줄 없음" } },
    { id: "hol", date: "2027-12-31", what: "공휴일 끝", gap: { from: "2027-03-01", warnDays: 180, what: "공휴일 표 끝" } },
    { id: "bad", date: "11월 30일", what: "모양이 틀린 줄" },
  ], at);
  assert.equal(d.today, "2026-11-13");
  assert.deepEqual(d.upcoming.map((x) => [x.id, x.left]), [["a", 0], ["b", 7], ["rates", 49], ["hol", 413]]);
  assert.deepEqual(d.gaps.map((x) => [x.id, x.left]), [["hol", 108]]);
  assert.equal(d.soon, 2);
  // 자동 순환 답에 실려 와요(장부 줄은 저장소에서 만든 것).
  const { call } = server({ t });
  const owner = await signIn(call);
  const loop = (await call("GET", "/api/admin/loop", undefined, owner)).data;
  assert.ok(Array.isArray(loop.dates.upcoming) && Array.isArray(loop.dates.gaps));
  assert.match(loop.dates.today, /^\d{4}-\d{2}-\d{2}$/);
});

// ---- 2.0.0 [RVA-63] HQ-124: 되풀이되는 닫힘 문의 (시험 자료는 모두 지어낸 값) ----
import { CRASH_ASK_DRAFT, crashOf, crashRecord, crashRepeats, repeatPlaces } from "../src/crash-repeat.js";

test("[RVA-63] 멈춤 기록 읽기: '앱 멈춤' 칸에 줄이 있으면 있음, '기록 없음'이면 없음, 점검 기록이 없으면 안 옴", () => {
  assert.equal(crashRecord("[앱 멈춤]\n03-16 09:00 IllegalStateException in Calendar\n\n[앱 멈춤 관찰]\n기록 없음"), "has");
  assert.equal(crashRecord("[앱 멈춤]\n기록 없음\n\n[앱 멈춤 관찰]\n화면 꺼짐 3번"), "none");
  assert.equal(crashRecord("[알람]\n다음 알람 07:00\n\n[앱 멈춤]\n기록 없음"), "none", "칸이 뒤에 있어도");
  assert.equal(crashRecord("[알람]\n다음 알람 07:00"), "nostop", "2.0.0 [RVA-64] 점검 기록은 왔지만 그 칸이 없음");
  assert.equal(crashRecord(undefined), "unsent");
  // 2.0.0 모양(갈래 L RVL-84): '가장 최근 기록:'이 맨 앞, '남은 기록: N개'와 '최근 30일: …'은 늘 있어요. '(못 읽음)' 줄은 멈춘 줄이 아니에요.
  const v2 = (body, more = "") => `[앱 멈춤]\n${body}${more}`;
  assert.equal(crashRecord(v2("가장 최근 기록:\n11/18 21:04 IllegalStateException@a.b:3\n최근 30일: 멈춤 1회 · 응답 없음(ANR) 0회 · 기기 쪽 멈춤 0회 · 메모리 부족 0회\n남은 기록: 1개",
    "\n\n[점검 못 읽음]\n(못 읽음) 나머지 점검: SecurityException@a.b.c:12")), "has");
  assert.equal(crashRecord(v2("최근 30일: 멈춤 0회 · 응답 없음(ANR) 0회 · 기기 쪽 멈춤 0회 · 메모리 부족 0회\n남은 기록: 0개")), "none", "30일·남은 기록 모두 0");
  assert.equal(crashRecord(v2("남은 기록: 0개")), "none", "안드로이드 10 아래");
  assert.equal(crashRecord(v2("최근 30일: 멈춤 0회 · 응답 없음(ANR) 2회 · 기기 쪽 멈춤 0회 · 메모리 부족 0회\n남은 기록: 0개")), "has", "ANR도 멈춤");
  assert.equal(crashRecord(v2("(못 읽음) 멈춤 기록: SecurityException@a.b:1")), "unread");
  assert.equal(crashOf("bug", "앱이 닫혀요", { diag: v2("(못 읽음) 멈춤 기록: SecurityException@a.b:1") }).recordName, "멈춤 기록 못 읽음");
  // 닫힘 문의만: 오류 종류이고 말에 닫힘이 있거나 앱이 '앱 멈춤'을 앞에 붙였을 때. 느려요·아이디어는 아니에요.
  assert.equal(crashOf("bug", "앱이 갑자기 닫혀요", {}).recordName, "점검 기록 없이 옴");
  assert.equal(crashOf("question", "쓰다가 자꾸 꺼져요", { diag: "[앱 멈춤]\n기록 없음" }).record, "none");
  assert.equal(crashOf("other", "그냥 궁금해요", { diag: "[앱 멈춤]\n03-16 IllegalStateException" }).record, "has");
  assert.equal(crashOf("bug", "달력이 좀 느려요", {}), null);
  assert.equal(crashOf("idea", "앱이 닫힐 때 저장해 주세요", {}), null);
  assert.equal(crashOf("bug", "알람 소리가 작아요", { diag: "[알람]\n…" }), null);
  // 묶음: 같은 보낸 분이 먼저, 같은 기기·안드로이드·판 묶음이 같은 보낸 분 하나뿐이면 한 번만.
  const D = 86_400_000;
  const now = Date.parse("2027-03-17T03:00:00Z");
  const base = { model: "Test Fold 9", android: "17 (API 37)", app: "1.39.0", record: "none" };
  const g1 = crashRepeats([{ id: "a", device: "p1", at: now - 3 * D, ...base }, { id: "b", device: "p1", at: now - D, ...base }, { id: "c", device: "p1", at: now - 9 * D, ...base }], now);
  assert.equal(g1.length, 1);
  assert.deepEqual([g1[0].by, g1[0].n, g1[0].ids, g1[0].noRecord], ["same", 2, ["a", "b"], 2], "7일 밖(c)은 빼요");
  assert.match(g1[0].label, /^같은 보낸 분 · Test Fold 9 · 안드로이드 17 · 1\.39\.0$/);
  assert.ok(!JSON.stringify(g1).includes("p1"), "보낸 분 번호는 표지로만");
  const g2 = crashRepeats([{ id: "a", device: "p1", at: now - 2 * D, ...base }, { id: "b", device: "p2", at: now - D, ...base, record: "has" }], now);
  assert.deepEqual([g2.length, g2[0].by, g2[0].people, g2[0].noRecord], [1, "model", 2, 1]);
  // 한 분의 되풀이(p1 둘)에 다른 분 한 건이 붙은 것은 같은 보낸 분 묶음 하나만, 다른 분이 둘 넘으면 기기 묶음도.
  const one = [{ id: "a", device: "p1", at: now - 3 * D, ...base }, { id: "b", device: "p1", at: now - 2 * D, ...base }, { id: "c", device: "p2", at: now - D, ...base }];
  assert.deepEqual(crashRepeats(one, now).map((g) => g.by), ["same"]);
  assert.deepEqual(crashRepeats([...one, { id: "d", device: "p3", at: now - D / 2, ...base }], now).map((g) => [g.by, g.n]), [["model", 4], ["same", 2]]);
  assert.equal(crashRepeats([{ id: "a", device: "p1", at: now - D, ...base }], now).length, 0, "한 건은 묶음이 아니에요");
  assert.deepEqual([...repeatPlaces(g1)], [["a", { key: g1[0].key, nth: 1, n: 2 }], ["b", { key: g1[0].key, nth: 2, n: 2 }]]);
  // 답 초안: 상담원 말투, '밤낮 운영팀', 앱 안 길만(주소·코드·자동 답이라는 말 없음).
  assert.match(CRASH_ASK_DRAFT, /알람 점검/);
  assert.match(CRASH_ASK_DRAFT, /보고서 복사/);
  assert.match(CRASH_ASK_DRAFT, /밤낮 운영팀$/);
  assert.doesNotMatch(CRASH_ASK_DRAFT, /https?:|\/api|AI|자동|서버|관리자|열쇠|코드/);
});

test("[RVA-63] 되풀이되는 닫힘 문의: 운영 장부 맨 위 묶음(몇 번째·첫 날·마지막 날), 줄과 문의에 멈춤 기록 표식과 부탁 답, 숫자 › 개선 신호", async (t) => {
  const { call, DB } = server({ t });
  const owner = await signIn(call);
  const DAY = 24 * HOUR;
  const fold = { app: "1.39.0 (53)", model: "Test Fold 9", android: "17 (API 37)" };
  const send = async (n, text, kind, info, daysAgo) => {
    const r = await call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info }, from(n));
    assert.equal(r.status, 201, r.text);
    const at = TEST_NOW - daysAgo * DAY;
    await DB.prepare("UPDATE inquiries SET created_at = ?1, updated_at = ?1 WHERE id = ?2").bind(at, r.data.id).run();
    return r.data.id;
  };
  const none = "[앱 멈춤]\n기록 없음\n\n[앱 멈춤 관찰]\n화면 꺼짐 2번";
  // 같은 보낸 분(61)이 나흘 동안 세 건, 그 전 9일 전 한 건(7일 밖). 같은 기기 이름의 다른 분(62)은 멈춘 줄이 있는 한 건.
  const old = await send(61, "앱이 갑자기 닫혀요 (지난주)", "bug", { ...fold, diag: none }, 9);
  const a1 = await send(61, "앱이 갑자기 닫혀요", "bug", { ...fold, diag: none }, 4);
  const a2 = await send(61, "또 앱이 저절로 꺼졌어요", "bug", { ...fold }, 2);
  const a3 = await send(61, "오늘도 닫힘. 근무 넣다가 튕겨요", "question", { ...fold, diag: none }, 0.5);
  const b1 = await send(62, "달력 넘기다 앱이 닫혔어요", "bug", { ...fold, diag: "[앱 멈춤]\n03-16 09:00 IllegalStateException in Month\n\n[알람]\n…" }, 1);
  await send(63, "앱이 닫힐 때 적던 것을 남겨 주세요", "idea", { ...fold }, 1);
  await send(64, "달력이 좀 느려요", "bug", { ...fold }, 1);
  const c1 = await send(65, "앱이 자꾸 꺼져요", "other", { ...fold, diag: none }, 1.5);

  const led = (await call("GET", "/api/admin/ledger?view=all", undefined, owner)).data;
  assert.ok(!JSON.stringify(led).includes(device(61)), "보낸 분 번호는 화면으로 가지 않아요");
  assert.equal(led.crashRepeats.length, 2, JSON.stringify(led.crashRepeats));
  const same = led.crashRepeats.find((g) => g.by === "same");
  assert.deepEqual([same.n, same.noRecord, same.lines.map((l) => l.key)], [3, 3, [a1, a2, a3].map((id) => `inquiry:${id}`)]);
  assert.equal(same.first, new Date(TEST_NOW - 4 * DAY).toISOString());
  assert.equal(same.last, new Date(TEST_NOW - 0.5 * DAY).toISOString());
  assert.ok(same.lines.every((l) => /^K-\d{4}$/.test(l.no)));
  assert.match(same.label, /^같은 보낸 분 · Test Fold 9 · 안드로이드 17 · 1\.39\.0$/);
  const model = led.crashRepeats.find((g) => g.by === "model");
  assert.deepEqual([model.n, model.people, model.noRecord], [5, 3, 4]);
  assert.match(led.crashDraft, /보고서 복사/);
  const row = (id) => led.items.find((r) => r.key === `inquiry:${id}`);
  assert.deepEqual([row(a1).crash.recordName, row(a2).crash.recordName, row(b1).crash.recordName], ["멈춤 기록 없음", "점검 기록 없이 옴", "멈춤 기록 있음"]);
  assert.deepEqual([row(a1).repeat.nth, row(a3).repeat.nth, row(a3).repeat.n], [1, 3, 3], "같은 보낸 분 묶음의 몇 번째");
  assert.equal(row(old).repeat, undefined, "7일 밖은 묶음에 없어요");
  assert.equal(row(old).crash.record, "none", "표식은 그대로");
  assert.equal(led.items.filter((r) => r.crash).length, 6, "아이디어·느려요는 닫힘 문의가 아니에요");
  assert.deepEqual(row(c1).repeat, { key: model.key, nth: 3, n: 5 }, "같은 보낸 분 묶음이 없으면 기기 묶음의 몇 번째");

  // 문의 화면: 줄 표식, 상세의 되풀이와 부탁 답(기록이 있으면 부탁 답 없음).
  const list = (await call("GET", "/api/admin/inquiries?status=open", undefined, owner)).data.inquiries;
  assert.equal(list.find((q) => q.id === a2).crash.recordName, "점검 기록 없이 옴");
  const d3 = (await call("GET", `/api/admin/inquiries/${a3}`, undefined, owner)).data;
  assert.deepEqual([d3.crash.record, d3.crash.repeat.nth, d3.crash.repeat.n], ["none", 3, 3], "상세도 장부와 같은 7일(9일 전 것은 빼요)");
  const d1 = (await call("GET", `/api/admin/inquiries/${a1}`, undefined, owner)).data;
  assert.deepEqual([d1.crash.repeat.nth, d1.crash.repeat.n, d1.crash.repeat.noRecord], [1, 3, 3]);
  const dOld = (await call("GET", `/api/admin/inquiries/${old}`, undefined, owner)).data;
  assert.deepEqual([dOld.crash.repeat.nth, dOld.crash.repeat.n], [1, 3], "오래된 문의는 그 뒤 7일(9일 전 → 2일 전 것까지)");
  assert.equal(d3.crash.draft, CRASH_ASK_DRAFT);
  assert.ok(!JSON.stringify(d3.crash).includes(device(61)));
  const db1 = (await call("GET", `/api/admin/inquiries/${b1}`, undefined, owner)).data;
  assert.deepEqual([db1.crash.record, db1.crash.draft, db1.crash.repeat], ["has", undefined, undefined]);
  // 앱(보낸 분)에는 아무것도 더 가지 않아요.
  const mine = (await call("GET", `/api/inquiries/${a3}`, undefined, { "x-device": device(61) })).data;
  assert.equal(mine.crash, undefined);

  // 숫자 › 개선 신호 '같은 판에서 닫힘 되풀이'(묶음마다, 운영 장부로).
  const o = (await call("GET", "/api/admin/observe", undefined, owner)).data;
  const sigs = o.found.filter((f) => f.kind === "crash-repeat" && f.on);
  assert.equal(sigs.length, 2, JSON.stringify(o.found.map((f) => f.kind)));
  const s = sigs.find((x) => x.subject === same.key);
  assert.match(s.title, /^같은 판에서 닫힘 되풀이: 같은 보낸 분 · Test Fold 9 · 안드로이드 17 · 1\.39\.0 3건\(03-13~03-17\) · 멈춤 기록 없음 3건$/);
  assert.equal(s.to, "#/desk");
  assert.equal(s.tone, "bad", "세 건부터는 위험");
  assert.ok(!JSON.stringify(o.crashRepeats).includes(device(61)));
});

// ---- 2.0.0 [RVA-64] HQ-132: 열쇠로 읽는 점검 기록은 내용 없이 표식만 (시험 자료는 모두 지어낸 값) ----
import { diagMark } from "../src/crash-repeat.js";

test("[RVA-64] 열쇠로 읽는 점검 기록 표식: 문의·장부 줄·넘길 글에 '(가려짐 · n줄 · 멈춤 기록 …)'만, 내용과 멈춘 곳은 없음, 안 온 것은 표식 없음", async (t) => {
  assert.equal(diagMark(undefined), null);
  assert.equal(diagMark("  \n"), null);
  assert.equal(diagMark("[앱 멈춤]\n기록 없음\n\n[알람]\n다음 알람 07:00"), "가려짐: 운영자만 봐요 · 4줄 · 멈춤 기록 없음");
  assert.equal(diagMark("[알람]\n다음 알람 07:00"), "가려짐: 운영자만 봐요 · 2줄 · 멈춤 칸 없음");
  assert.equal(diagMark("[앱 멈춤]\n(못 읽음) 멈춤 기록: SecurityException@a.b:1"), "가려짐: 운영자만 봐요 · 2줄 · 멈춤 기록 못 읽음");

  const { call } = server({ t });
  const owner = await signIn(call);
  const key = (await call("POST", "/api/admin/keys", { label: "Claude 시험" }, owner)).data.key;
  const claude = { authorization: `Bearer ${key}`, "x-admin": "1" };
  const fold = { app: "1.39.0 (53)", model: "Test Fold 9", android: "17 (API 37)" };
  const send = async (n, text, info) => {
    const r = await call("POST", "/api/inquiries", { v: 1, device: device(n), kind: "bug", text, info }, from(n));
    assert.equal(r.status, 201, r.text);
    return r.data.id;
  };
  const crashed = "[앱 멈춤]\n가장 최근 기록:\n11/18 21:04 TestCrashException@x.y.Month:3\n최근 30일: 멈춤 1회 · 응답 없음(ANR) 0회\n남은 기록: 1개\n\n[알람]\n다음 알람 07:00";
  const has = await send(71, "앱이 갑자기 닫혀요", { ...fold, diag: crashed });
  const none = await send(72, "앱이 또 꺼졌어요", { ...fold, diag: "[앱 멈춤]\n기록 없음" });
  const unsent = await send(73, "앱이 저절로 닫혔어요", { ...fold });
  const leak = (x) => /TestCrashException|x\.y\.Month|다음 알람 07:00/.test(JSON.stringify(x));

  // 문의 하나: 운영자는 그대로, 열쇠는 표식만.
  assert.match((await call("GET", `/api/admin/inquiries/${has}`, undefined, owner)).data.context["알람 점검"], /TestCrashException/);
  const k1 = (await call("GET", `/api/admin/inquiries/${has}`, undefined, claude)).data;
  assert.equal(k1.context["알람 점검"], "(가려짐: 운영자만 봐요 · 7줄 · 멈춤 기록 있음)");
  assert.ok(!leak(k1), "기록 내용·멈춘 곳은 열쇠로 가지 않아요");
  assert.equal(k1.crash.recordName, "멈춤 기록 있음", "닫힘 문의 표식도 같은 셈");
  assert.equal((await call("GET", `/api/admin/inquiries/${none}`, undefined, claude)).data.context["알람 점검"], "(가려짐: 운영자만 봐요 · 2줄 · 멈춤 기록 없음)");
  assert.ok(!("알람 점검" in (await call("GET", `/api/admin/inquiries/${unsent}`, undefined, claude)).data.context), "안 온 것은 자리가 없어요");

  // 운영 장부 줄(Claude 차례)과 넘길 글.
  const led = await call("GET", "/api/admin/ledger?view=claude", undefined, claude);
  assert.equal(led.status, 200, led.text);
  const row = (id) => led.data.items.find((r) => r.key === `inquiry:${id}`);
  assert.deepEqual([row(has).diag, row(none).diag, row(unsent).diag], ["가려짐: 운영자만 봐요 · 7줄 · 멈춤 기록 있음", "가려짐: 운영자만 봐요 · 2줄 · 멈춤 기록 없음", undefined]);
  assert.ok(!leak(led.data));
  const hand = await call("GET", "/api/admin/inquiries/handoff", undefined, claude);
  assert.equal(hand.status, 200, hand.text);
  const text = hand.data.text;
  assert.match(text, /점검 기록: \(가려짐: 운영자만 봐요 · 7줄 · 멈춤 기록 있음\)/);
  assert.match(text, /'점검 기록' 줄은 내용을 가린 표식이에요/);
  assert.equal(text.match(/^  점검 기록: /gm).length, 2, "안 온 문의에는 그 줄이 없어요");
  assert.ok(!leak(text));
  const desk = (await call("GET", "/api/admin/desk", undefined, claude)).data;
  assert.ok(!leak(desk));
  assert.match(desk.guide ?? JSON.stringify(desk), /없다고 적지 말고|'안 왔다'고 적지 말고/);
});
