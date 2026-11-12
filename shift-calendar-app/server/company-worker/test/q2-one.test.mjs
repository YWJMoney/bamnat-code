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

test("[RVA-62] PC-159 활동 기록의 친구 초대 줄은 안쪽 번호 대신 초대 코드와 말로", async (t) => {
  const { call, DB } = server({ t });
  const owner = await signIn(call);
  DB.db.prepare(`INSERT INTO invite_uses (invitee, phone, code, inviter, source, first_day, status, reason, created_at, batch)
    VALUES ('friend1', 'mark1', 'AHC493YJ', 'inviterdevice0001', 'link', '2026-11-01', 'credited', '', ?, 'iJcNXrC52iGMzK2kr5bstC')`).run(TEST_NOW);
  const add = DB.db.prepare("INSERT INTO audit (at, action, target, detail, actor) VALUES (?, ?, ?, ?, '')");
  add.run(TEST_NOW, "invite_credit", "iJcNXrC52iGMzK2kr5bstC", "3bf6b2f7");
  add.run(TEST_NOW + 1, "invite_refused", "ZZZZ2222", "phone");
  const { entries } = (await call("GET", "/api/admin/audit", undefined, owner)).data;
  const credit = entries.find((e) => e.action === "invite_credit");
  assert.equal(credit.label, "코드 AHC4-93YJ");
  assert.equal(credit.detail, "", "보낸 분 휴대폰 앞자리는 보이지 않아요");
  assert.equal(credit.link, "#/rewards");
  const refused = entries.find((e) => e.action === "invite_refused");
  assert.equal(refused.label, "코드 ZZZZ-2222");
  assert.equal(refused.detail, "이미 초대받은 휴대폰");
});
