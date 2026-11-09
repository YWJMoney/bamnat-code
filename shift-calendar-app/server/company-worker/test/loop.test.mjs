// 1.39 [LOOP] 문의 → 개발 → 출시 → 답이 스스로 도는 바퀴(계획 docs/plans/1.39-G10-자동순환.md). `npm test`.
// Play는 가짜 fetch로, 저장소 동기화는 동기화 열쇠로, 폰은 x-device·x-app 머리로 흉내 내요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import worker from "../src/index.js";
import { server, device, signIn, from, ORIGIN } from "./helpers.mjs";
import { impact, laterWords, nextTrain, releaseName } from "../src/loop.js";
import { keyAllows } from "../src/desk.js";
import { waitingNote } from "../src/inquiries.js";

const NOW = Date.parse("2026-11-20T01:00:00Z");
const HOUR = 3600e3;
const DAY = 24 * HOUR;
const kst = (ms) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);
/** [LOOP-32] 이 판으로 쓰는 휴대폰(어제 하루). */
const phones = (env, app, n) => env.DB.db.prepare("INSERT OR REPLACE INTO usage_days (day, app, phones) VALUES (?, ?, ?)").run(kst(Date.now() - DAY), app, n);

const ask = (call, n, text, kind = "bug", app = "1.38.0 (52)") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app } }).then((r) => r.data.id);
const mine = (call, n, app) => call("GET", "/api/inquiries", undefined, { "x-device": device(n), ...(app ? { "x-app": app } : {}) }).then((r) => r.data.inquiries);
const thread = (call, id, n) => call("GET", `/api/inquiries/${id}`, undefined, { "x-device": device(n) }).then((r) => r.data);

/** 개발사항 하나를 문의에 묶어 만들어요. */
async function item(call, admin, title, inquiries) {
  const id = (await call("POST", "/api/admin/dev", { title, inquiry: inquiries[0] }, admin)).data.id;
  for (const q of inquiries.slice(1)) await call("POST", `/api/admin/dev/${id}/links`, { inquiry: q }, admin);
  return id;
}

async function hourly(env, now = Date.now()) {
  const waits = [];
  await worker.scheduled({ cron: "17 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
}
async function fiveMinutes(env, now = Date.now()) {
  const waits = [];
  await worker.scheduled({ cron: "*/5 * * * *", scheduledTime: now }, env, { waitUntil: (p) => waits.push(p) });
  await Promise.all(waits);
}

/** Play 서비스 계정과 프로덕션 트랙을 흉내 내요. [track]을 바꾸면 다음 읽기에 그대로 나와요. */
function fakePlay(t, track) {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const account = JSON.stringify({ client_email: "bamnat@example.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const seen = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init = {}) => {
    url = String(url);
    seen.push(`${init.method ?? "GET"} ${url.replace("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/", "")}`);
    if (url === "https://oauth2.googleapis.com/token") return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }));
    if (url.endsWith("/edits") && init.method === "POST") return new Response(JSON.stringify({ id: "edit1" }));
    if (url.endsWith("/edits/edit1/tracks/production")) return new Response(JSON.stringify({ track: "production", releases: track.releases }));
    if (url.endsWith("/edits/edit1") && init.method === "DELETE") return new Response(null, { status: 204 });
    return new Response("{}", { status: 404 });
  };
  t.after(() => { globalThis.fetch = real; });
  return { account, seen };
}

test("[LOOP] 판 이름·다음 정기 출발일·영향도 점수·출시 전 답 문구", () => {
  assert.equal(releaseName({ name: "53 (1.39.0)", versionCodes: ["53"] }), "1.39.0");
  assert.equal(releaseName({ name: "", versionCodes: ["39"] }), "1.28.0");
  assert.equal(releaseName({ name: "새 판", versionCodes: ["9999"] }), null);
  const train = { start: "2026-11-20", days: 14 };
  assert.equal(nextTrain(train, Date.parse("2026-11-10T00:00:00Z")), "2026-11-20");
  assert.equal(nextTrain(train, Date.parse("2026-11-20T01:00:00Z")), "2026-11-20");
  assert.equal(nextTrain(train, Date.parse("2026-11-21T01:00:00Z")), "2026-12-04");
  assert.equal(impact({ asks: 2, same: 3, urgent: 1, versions: 2, again: 1 }), 6 + 3 + 5 + 2 + 6);
  // 출시 전 답에는 늘 '업데이트에 들어가요'가 들어가요: 판이 나가면 이 답을 받은 문의에 다시 알려요(desk.js shipTargets).
  assert.match(laterWords("1.39.1", "app", "2026-12-04"), /1\.39\.1 버전으로 12월 4일 정기 업데이트에 들어가요/);
  assert.match(laterWords("1.39.2", "hotfix", null), /곧 나올 1\.39\.2 긴급 업데이트에 들어가요/);
  // 동기화 열쇠는 /dev/sync 하나만, 운영 열쇠는 그 길을 못 써요.
  assert.equal(keyAllows("/dev/sync", "POST", "sync"), true);
  assert.equal(keyAllows("/desk", "GET", "sync"), false);
  assert.equal(keyAllows("/dev/sync", "POST", "ops"), false);
  assert.equal(keyAllows("/loop/decide", "GET", "ops"), true);
});

test("[LOOP-01][LOOP-02] Play가 100% 나간 판을 보면 1시간 보류 뒤 스스로 나간 판을 저장하고 묶인 문의에 답해요; 취소도 돼요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const track = { releases: [{ name: "53 (1.39.0)", versionCodes: ["53"], status: "inProgress", userFraction: 0.2 }, { name: "52 (1.38.0)", versionCodes: ["52"], status: "completed" }] };
  const play = fakePlay(t, track);
  const { call, env } = server({ PLAY_SERVICE_ACCOUNT: play.account });
  const admin = await signIn(call);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.38.0" }, admin)).status, 200);
  const q = await ask(call, 1, "잠금 화면에서 알람 소리가 안 나요");
  const d = await item(call, admin, "잠금 화면 알람 소리", [q]);
  assert.equal((await call("POST", `/api/admin/dev/${d}`, { status: "done", releasedIn: "1.39.0", news: "잠금 화면에서도 알람 소리가 나요" }, admin)).status, 200);

  // 단계적 출시 20%: 아직 아무것도 하지 않아요(트랙만 남겨요).
  await hourly(env);
  let loop = (await call("GET", "/api/admin/loop", undefined, admin)).data;
  assert.equal(loop.play.ready, true);
  assert.deepEqual(loop.play.rolling, { v: "1.39.0", fraction: 0.2 });
  assert.equal(loop.ships.length, 0);
  assert.ok(play.seen.includes("POST com.bamnat.app/edits") && play.seen.includes("DELETE com.bamnat.app/edits/edit1"), "수정은 열고 꼭 닫아요");
  assert.ok(!play.seen.some((s) => s.includes(":commit")), "아무것도 바꾸지 않아요");

  // [LOOP-32] 트랙 100%: Play는 검토 중인 판도 completed로 보여 줘요(1.39 실서버 11-08, 1.38.0 검토 중). 그래서 '검토 중일 수 있음'으로만 적고 아무것도 보내지 않아요.
  track.releases = [{ name: "53 (1.39.0)", versionCodes: ["53"], status: "completed" }];
  phones(env, "1.39.0", 12); // 테스트 트랙 사람 정도
  await hourly(env);
  loop = (await call("GET", "/api/admin/loop", undefined, admin)).data;
  assert.equal(loop.ships[0].version, "1.39.0");
  assert.equal(loop.ships[0].status, "review");
  assert.deepEqual(loop.play.seen, { phones: 12, need: 30, live: false });
  assert.match((await call("GET", "/api/admin/loop/check", undefined, admin)).data.lines.find((x) => x.key === "play").text, /검토 중이거나 아직 안 나갔을 수 있어요\(이 판 휴대폰 12\/30대\)/);
  t.mock.timers.tick(3 * HOUR);
  await fiveMinutes(env);
  await hourly(env);
  assert.equal((await thread(call, q, 1)).progress.stage, "building", "검토 중에는 '나왔어요'가 가지 않아요");
  assert.equal((await call("GET", "/api/admin/loop", undefined, admin)).data.ships[0].status, "review");
  // '지금 보내기'는 검토 중일 수 있는 판에는 안 돼요.
  assert.equal((await call("POST", "/api/admin/loop/ship/1.39.0/now", {}, admin)).status, 404);

  // 이 판으로 쓰는 휴대폰이 기준을 넘으면 그때 1시간 보류 안내가 서요. 그 안에는 답이 가지 않아요.
  phones(env, "1.39.0", 30);
  await hourly(env);
  loop = (await call("GET", "/api/admin/loop", undefined, admin)).data;
  assert.equal(loop.ships[0].status, "waiting");
  t.mock.timers.tick(30 * 60e3);
  await fiveMinutes(env);
  assert.equal((await thread(call, q, 1)).progress.stage, "building");
  // 다시 읽어도 하나만 서요.
  await hourly(env);
  assert.equal((await call("GET", "/api/admin/loop", undefined, admin)).data.ships.length, 1);

  // 1시간이 지나면 5분 작업이 보내요: 나간 판 저장 + 묶인 문의 답 + 업데이트 소식 공지.
  t.mock.timers.tick(31 * 60e3);
  await fiveMinutes(env);
  const view = await thread(call, q, 1);
  assert.equal(view.progress.stage, "released");
  assert.match(view.messages.at(-1).text, /1\.39\.0 버전이 나왔어요/);
  loop = (await call("GET", "/api/admin/loop", undefined, admin)).data;
  assert.equal(loop.shipped.v, "1.39.0");
  assert.equal(loop.ships[0].status, "done");
  assert.match(loop.ships[0].result, /문의 1건/);
  const notices = (await call("GET", "/api/config")).data.notices;
  assert.ok(notices.some((n) => /1\.39\.0 업데이트 소식/.test(n.title)));

  // 다음 판은 사람이 취소할 수 있어요(열쇠는 못 해요). 검토 중일 수 있는 판도 멈출 수 있어요.
  track.releases = [{ name: "54 (1.39.1)", versionCodes: ["54"], status: "completed" }];
  await hourly(env);
  assert.equal((await call("GET", "/api/admin/loop", undefined, admin)).data.ships[0].status, "review");
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, admin)).data;
  const key = { authorization: `Bearer ${made.key}`, "x-admin": "1" };
  assert.equal((await call("POST", "/api/admin/loop/ship/1.39.1/cancel", {}, key)).status, 403);
  assert.equal((await call("POST", "/api/admin/loop/ship/1.39.1/cancel", {}, admin)).data.status, "cancelled");
  t.mock.timers.tick(2 * HOUR);
  await fiveMinutes(env);
  assert.equal((await call("GET", "/api/admin/loop", undefined, admin)).data.shipped.v, "1.39.0");
  // 자동 출시를 끄면 서지도 않아요.
  assert.equal((await call("POST", "/api/admin/loop/switches", { autoShip: false }, admin)).data.autoShip, false);
  track.releases = [{ name: "55 (1.39.2)", versionCodes: ["55"], status: "completed" }];
  await hourly(env);
  assert.ok(!(await call("GET", "/api/admin/loop", undefined, admin)).data.ships.some((s) => s.version === "1.39.2"));
});

test("[LOOP-32] 검토 중 판: 사람이 '게시됨 확인'을 누르면 1시간 보류, 앞 판이 크면 기준도 커지고, 스위치를 끄면 보류가 끝나도 안 보내요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const track = { releases: [{ name: "53 (1.39.0)", versionCodes: ["53"], status: "completed" }] };
  const play = fakePlay(t, track);
  const { call, env } = server({ PLAY_SERVICE_ACCOUNT: play.account });
  const admin = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.38.0" }, admin);
  const q = await ask(call, 1, "잠금 화면에서 알람 소리가 안 나요");
  const d = await item(call, admin, "잠금 화면 알람 소리", [q]);
  await call("POST", `/api/admin/dev/${d}`, { status: "done", releasedIn: "1.39.0", news: "잠금 화면에서도 알람 소리가 나요" }, admin);
  // 앞 판 휴대폰이 1000대면 기준은 50대(5%): 40대로는 아직이에요.
  phones(env, "1.38.0", 1000);
  phones(env, "1.39.0", 40);
  await hourly(env);
  let loop = (await call("GET", "/api/admin/loop", undefined, admin)).data;
  assert.deepEqual(loop.play.seen, { phones: 40, need: 50, live: false });
  assert.equal(loop.ships[0].status, "review");
  // 열쇠는 못 누르고, 사람이 Play Console에서 보고 누르면 1시간 보류가 시작돼요.
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, admin)).data;
  assert.equal((await call("POST", "/api/admin/loop/ship/1.39.0/live", {}, { authorization: `Bearer ${made.key}`, "x-admin": "1" })).status, 403);
  const r = (await call("POST", "/api/admin/loop/ship/1.39.0/live", {}, admin)).data;
  assert.equal(r.status, "waiting");
  assert.equal((await call("POST", "/api/admin/loop/ship/1.39.0/live", {}, admin)).status, 404, "두 번은 안 돼요");
  // 보류 중에 스위치를 끄면 1시간이 지나도 스스로 보내지 않아요('지금 보내기'는 돼요).
  assert.equal((await call("POST", "/api/admin/loop/switches", { autoShip: false }, admin)).data.autoShip, false);
  t.mock.timers.tick(2 * HOUR);
  await fiveMinutes(env);
  assert.equal((await thread(call, q, 1)).progress.stage, "building");
  assert.equal((await call("POST", "/api/admin/loop/ship/1.39.0/now", {}, admin)).data.version, "1.39.0");
  assert.equal((await thread(call, q, 1)).progress.stage, "released");
  // 스위치 하나만 보내도 다른 것은 그대로예요(체크하면 바로 저장).
  const sw = (await call("POST", "/api/admin/loop/switches", { halt: false }, admin)).data;
  assert.equal(sw.autoShip, false);
  assert.equal(sw.sync, true);
  assert.equal(sw.halt, false);
});

// [LOOP-33] 사용자 2026-11-08 '30대가 떠야지 인지하는 거야?': 통계 동의 휴대폰이 적어도 Play 보고서(동의와 상관없이 Play가 세는 판별 설치)로 나간 것을 봐요.
test("[LOOP-33] 통계 동의 폰이 0대여도 트랙 100%를 본 뒤 그 판 번호로 새로 깔거나 올린 기기가 3대면 나간 것으로 보고 1시간 보류해요; 그 전 날(테스트 트랙)은 세지 않아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const track = { releases: [{ name: "53 (1.39.0)", versionCodes: ["53"], status: "completed" }] };
  const play = fakePlay(t, track);
  const { call, env } = server({ PLAY_SERVICE_ACCOUNT: play.account });
  const admin = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.38.0" }, admin);
  const reports = (moves) => env.DB.db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('play_reports', ?)")
    .run(JSON.stringify({ until: Date.now() + DAY, value: { state: "ok", at: Date.now(), versions: { day: kst(Date.now()), rows: [], first: {}, moves } } }));
  // 테스트 트랙 사람이 100%를 보기 전날 올린 것(5대)과 다른 판 번호는 세지 않아요.
  reports([[kst(Date.now() - DAY), 53, 5], [kst(Date.now()), 52, 9]]);
  await hourly(env);
  let loop = (await call("GET", "/api/admin/loop", undefined, admin)).data;
  assert.equal(loop.ships[0].status, "review");
  assert.deepEqual(loop.play.seen, { phones: 0, need: 30, live: false, play: 0, playNeed: 3 });
  // 그날부터 2대 + 다음 날 1대 = 3대: 나간 것으로 봐요.
  reports([[kst(Date.now() - DAY), 53, 5], [kst(Date.now()), 53, 2], [kst(Date.now() + DAY), 53, 1]]);
  t.mock.timers.tick(HOUR);
  await hourly(env);
  loop = (await call("GET", "/api/admin/loop", undefined, admin)).data;
  assert.equal(loop.ships[0].status, "waiting");
  const seen = env.DB.db.prepare("SELECT detail FROM audit WHERE action = 'loop_ship_seen'").get();
  assert.match(seen.detail, /Play 보고서: 이 판을 새로 깔거나 올린 기기 3대/);
});

test("[LOOP-05] 저장소 동기화: 좁은 열쇠로 dev-status 줄을 받아 개발사항을 만들고, 고친 판·다음 정기 출발일을 답해요; 요금은 못 정해요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  const owner = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.39.0" }, owner);
  const sync = (await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data;
  assert.equal(sync.scope, "sync");
  assert.equal(sync.label, "GitHub 동기화");
  assert.equal((await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data.error, "too_many");
  const key = { authorization: `Bearer ${sync.key}`, "x-admin": "1" };
  // 이 열쇠로는 다른 길이 막혀요.
  assert.equal((await call("GET", "/api/admin/desk", undefined, key)).data.error, "key_scope");
  assert.equal((await call("GET", "/api/admin/inquiries", undefined, key)).data.error, "key_scope");

  const q1 = await ask(call, 1, "주간 근무 알람이 30분 늦게 울려요");
  const d1 = await item(call, owner, "주간 알람 늦음", [q1]);
  const r = (await call("POST", "/api/admin/dev/sync", { sha: "abc1234def", version: "1.39.1", pr: 300, items: [
    { id: d1, track: "LOOP-90", status: "done", size: "fix", route: "app", news: "주간 근무 알람이 제때 울려요", tier: "free", publicTitle: "주간 근무 알람이 늦게 울려요" },
    { track: "LOOP-91", title: "달력 칸 길게 눌러 메모", status: "doing", size: "feature", route: "app", tierAsk: "pro", tierWhy: "기본 메모는 무료라 길게 누르기 바로 가기만", events: ["memo_long_press"] },
    { track: "bad one" },
  ] }, key)).data;
  assert.equal(r.made, 1);
  assert.equal(r.errors, 1);
  assert.equal(r.told, 1);
  // 열쇠로 간 답은 10분 보류예요. 보류가 끝나면 '정기 업데이트에 들어가요'가 가요.
  t.mock.timers.tick(11 * 60e3);
  await fiveMinutes(env);
  assert.match((await thread(call, q1, 1)).messages.at(-1).text, /1\.39\.1 버전으로 11월 23일 정기 업데이트에 들어가요/);
  const dev = (await call("GET", `/api/admin/dev/${d1}`, undefined, owner)).data;
  assert.equal(dev.item?.releasedIn ?? dev.releasedIn, "1.39.1");
  const list = (await call("GET", "/api/admin/dev?status=doing", undefined, owner)).data;
  const made = (list.items ?? []).find((x) => x.title === "달력 칸 길게 눌러 메모");
  assert.ok(made, "저장소 줄로 새 개발사항이 생겨요");
  // 요금은 저장소에서 정하지 못해요: 제안(tierAsk)만 남고 정한 값은 비어요.
  const tiers = (await call("GET", "/api/admin/loop/tiers", undefined, owner)).data.items;
  const feat = tiers.find((x) => x.title === "달력 칸 길게 눌러 메모");
  assert.equal(feat.tier, null);
  assert.equal(feat.ask, "pro");
  assert.equal((await call("GET", "/api/admin/loop", undefined, owner)).data.syncs[0].sha, "abc1234def");
  // 동기화를 끄면 409.
  await call("POST", "/api/admin/loop/switches", { sync: false }, owner);
  assert.equal((await call("POST", "/api/admin/dev/sync", { items: [] }, key)).data.error, "sync_off");
});

test("[LOOP-06][LOOP-15][LOOP-16] 고친 판으로 연 폰에만 바로 안내하고 해결됐나요를 물어요; 아직이에요면 다시 열려요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  await call("POST", "/api/admin/desk/ship", { version: "1.38.0" }, owner);
  const q1 = await ask(call, 1, "급여 화면에서 야간 수당이 두 번 더해져요");
  const q2 = await ask(call, 2, "야간 수당이 두 번 들어가요");
  const d = await item(call, owner, "야간 수당 두 번", [q1, q2]);
  await call("POST", `/api/admin/dev/${d}`, { status: "done", releasedIn: "1.39.0" }, owner);
  // 1.39.0으로 연 폰 1: 바로 안내, '반영', 해결됐나요.
  let list = await mine(call, 1, "1.39.0 (53)");
  assert.equal(list[0].progress.stage, "released");
  assert.match(list[0].messages.at(-1).text, /1\.39\.0 버전에 말씀해 주신 내용을 고쳐 두었어요/);
  assert.equal(list[0].askFixed, true);
  // 아직 1.38.0인 폰 2: 그대로.
  list = await mine(call, 2, "1.38.0 (52)");
  assert.equal(list[0].progress.stage, "building");
  assert.equal(list[0].askFixed, false);
  // 판이 나가도 폰 1에는 두 번 가지 않아요.
  const shipped = (await call("POST", "/api/admin/desk/ship", { version: "1.39.0", notify: true }, owner)).data;
  assert.equal(shipped.answered, 1);
  // 폰 1: 아직이에요 → 문의는 다시 기다림, 개발사항은 접수로, 대기열 점수가 올라가요.
  assert.equal((await call("POST", `/api/inquiries/${q1}/fixed`, { ok: false }, { "x-device": device(1) })).status, 200);
  const back = await thread(call, q1, 1);
  assert.equal(back.status, "open");
  assert.equal(back.fixed, -1);
  assert.match(back.messages.at(-1).text, /아직 해결되지 않았어요/);
  const item2 = (await call("GET", `/api/admin/dev/${d}`, undefined, owner)).data;
  assert.equal(item2.item?.status ?? item2.status, "todo");
  const queue = (await call("GET", "/api/admin/loop/queue", undefined, owner)).data.items;
  assert.equal(queue[0].id, d);
  assert.equal(queue[0].again, 1);
  // 결정함 '다시 봐야 할 것'에도 떠요(PC 확인 1.39 G10).
  const box = (await call("GET", "/api/admin/loop/decide", undefined, owner)).data;
  assert.deepEqual(box.back.map((x) => [x.id, x.times, x.status]), [[d, 1, "todo"]]);
  // 결정함 탭 숫자에도 세요(PC G10 사용성 제안).
  assert.equal((await call("GET", "/api/admin/badges", undefined, owner)).data.decideBack, 1);
  // 남의 문의·안내 전 문의는 못 눌러요.
  assert.equal((await call("POST", `/api/inquiries/${q1}/fixed`, { ok: true }, { "x-device": device(2) })).status, 404);
  const q3 = await ask(call, 3, "질문이 있어요", "question");
  assert.equal((await call("POST", `/api/inquiries/${q3}/fixed`, { ok: true }, { "x-device": device(3) })).data.error, "not_fixed");
  // 다시 고쳐 1.39.1로 나가면 폰 1에도 다시 안내가 가고 해결됐나요를 다시 물어요.
  await call("POST", `/api/admin/dev/${d}`, { status: "done", releasedIn: "1.39.1" }, owner);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.39.1", notify: true }, owner)).data.answered, 1);
  const again = await thread(call, q1, 1);
  assert.equal(again.fixed, 0);
  assert.equal(again.askFixed, true);
  assert.match(again.messages.at(-1).text, /1\.39\.1/);
  // 폰 2: 해결됐어요.
  assert.equal((await call("POST", `/api/inquiries/${q2}/fixed`, { ok: true }, { "x-device": device(2) })).data.fixed, 1);
  // [LOOP-22] 판별 고침 성적표: 1.39.1에 고친 것 1 · 묶인 문의 2 · 해결됐어요 1 · 아직이에요 1(다시 열린 것).
  const score = (await call("GET", "/api/admin/loop", undefined, owner)).data.scores.find((x) => x.version === "1.39.1");
  assert.deepEqual([score.items, score.asks, score.yes, score.no, score.solved], [1, 2, 1, 1, 50]);
});

test("[LOOP-19][LOOP-20] 급함 문의(알람·수면 긴급 넷)와 최고 관리자 확인 문의는 결정함 맨 위와 숫자에; 답하면 빠져요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const alarm = await ask(call, 1, "어제 새벽 알람이 안 울려서 지각했어요");
  const sleep = await ask(call, 2, "수면 측정이 아침에도 녹음이 계속 켜져 있어요");
  await ask(call, 3, "달력 색을 바꾸고 싶어요", "question");
  const money = await ask(call, 4, "결제했는데 프로가 안 열려요. 환불해 주세요", "question");
  const box = (await call("GET", "/api/admin/loop/decide", undefined, owner)).data;
  // 달력 색 질문은 급함이 아니고, 수면 긴급(녹음이 계속)과 결제 뒤 안 열림은 급함이에요.
  assert.deepEqual(box.urgent.map((x) => x.id).sort(), [alarm, sleep, money].sort());
  let b = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  assert.equal(b.decideUrgent, box.urgent.length);
  assert.equal(b.decideAsks, 1);
  // 답하면(마지막 말이 관리자) 결정함에서 빠져요.
  await call("POST", `/api/admin/inquiries/${alarm}/reply`, { text: "불편을 드려 죄송해요. 바로 살펴볼게요." }, owner);
  b = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  assert.equal(b.decideUrgent, box.urgent.length - 1);
});

test("[G7 1-4] 새 문의 알림 끝에 답 기다리는 수와 같은 문의가 몇 건째인지(글은 넣지 않아요)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  await ask(call, 1, "급여 화면에서 야간 수당이 두 번 더해져요");
  await ask(call, 2, "달력 색을 바꾸고 싶어요", "question");
  const q3 = await ask(call, 3, "야간 수당이 급여에 두 번 더해져요");
  const note = await waitingNote(env, q3, "야간 수당이 급여에 두 번 더해져요", "pay");
  assert.equal(note, " · 답 기다림 3건 · 같은 문의 2건째");
  assert.doesNotMatch(note, /야간|수당/);
  assert.equal(await waitingNote({}, q3, "x", "etc"), "", "읽지 못하면 빈 글");
});

test("[LOOP-18] 연결 점검: 무엇이 됐고 무엇이 빠졌는지 한 줄씩, Play까지 점검은 읽기만", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  const owner = await signIn(call);
  let c = (await call("GET", "/api/admin/loop/check", undefined, owner)).data;
  const byKey = Object.fromEntries(c.lines.map((x) => [x.key, x]));
  assert.equal(byKey.play.state, "bad");
  assert.equal(byKey.syncKey.state, "bad");
  assert.equal(byKey.sync.state, "warn");
  assert.equal(c.state, "bad");
  assert.ok(c.lines.every((x) => !/BEGIN|PRIVATE|eyJ/.test(x.text)), "값은 보이지 않아요");
  const play = fakePlay(t, { releases: [{ name: "1.39.0", versionCodes: ["53"], status: "completed" }] });
  env.PLAY_SERVICE_ACCOUNT = play.account;
  c = (await call("GET", "/api/admin/loop/check?play=1", undefined, owner)).data;
  assert.equal(c.lines.find((x) => x.key === "play").state, "ok");
  assert.match(c.lines.find((x) => x.key === "play").text, /1\.39\.0/);
  assert.ok(play.seen.some((x) => x.startsWith("DELETE")), "편집은 지우고 확정하지 않아요");
  assert.ok(!play.seen.some((x) => /:commit/.test(x)));
  // 읽기만이라 보류 안내가 생기지 않아요.
  assert.equal((await call("GET", "/api/admin/loop", undefined, owner)).data.ships.length, 0);
  // [LOOP-32] 읽은 것은 남아서, 새로 고쳐도 '한 번도 읽지 않았어요'로 돌아가지 않아요.
  c = (await call("GET", "/api/admin/loop/check", undefined, owner)).data;
  assert.match(c.lines.find((x) => x.key === "play").text, /마지막으로 읽음 .* Play 트랙 100% 1\.39\.0 · 검토 중이거나/);
  // 매시 Play 읽기가 실패하면 따로 한 줄.
  env.DB.db.prepare("INSERT OR REPLACE INTO job_runs (step, ok_at, fail_at, error) VALUES ('play', NULL, ?, 'play_denied: 권한')").run(Date.now());
  c = (await call("GET", "/api/admin/loop/check", undefined, owner)).data;
  assert.equal(c.lines.find((x) => x.key === "playJob").state, "bad");
});

test("[LOOP-06] 기능 스위치로 고친 것은 스위치가 켜진 때 바로 답해요(앱 업데이트 없이)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  const owner = await signIn(call);
  const q = await ask(call, 1, "돈 통계를 무료로 볼 수 없나요", "idea");
  const d = await item(call, owner, "돈 통계 무료로", [q]);
  await call("POST", "/api/admin/dev/sync", { items: [{ id: d, status: "done", size: "polish", route: "flag", flag: "free.MONEY_STATS" }] }, owner);
  await hourly(env);
  assert.equal((await thread(call, q, 1)).progress.stage, "building");
  assert.equal((await call("GET", "/api/admin/loop", undefined, owner)).data.waitingLive[0].want, "free.MONEY_STATS");
  assert.equal((await call("POST", "/api/admin/flags/free.MONEY_STATS/force", { force: "on", why: "모두에게" }, owner)).status, 200);
  await hourly(env);
  const view = await thread(call, q, 1);
  assert.equal(view.progress.stage, "released");
  assert.match(view.messages.at(-1).text, /앱 업데이트 없이 지금 바로 적용됐어요/);
});

test("[LOOP-10][LOOP-11] 요금을 정하지 않은 새 기능은 스위치를 켤 수 없고, 요금은 최고 관리자만 정해요; 관측치가 보여요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const q = await ask(call, 1, "돈 통계를 무료로 열어 주세요", "idea");
  const d = await item(call, owner, "돈 통계 열기", [q]);
  await call("POST", "/api/admin/dev/sync", { items: [{ id: d, status: "doing", size: "feature", route: "flag", flag: "free.MONEY_STATS", tierAsk: "free", tierWhy: "돈 관리는 기본" }] }, owner);
  const blocked = await call("POST", "/api/admin/flags/free.MONEY_STATS/force", { force: "on", why: "열기" }, owner);
  assert.equal(blocked.status, 409);
  assert.equal(blocked.data.error, "tier");
  assert.equal(blocked.data.item.id, d);
  const cond = await call("POST", "/api/admin/flags/free.MONEY_STATS/conditions", { conditions: [{ type: "date", from: "2026-12-01" }], why: "12월부터" }, owner);
  assert.equal(cond.data.error, "tier");
  // 결정함에 요금을 정할 것으로 올라와요.
  const box = (await call("GET", "/api/admin/loop/decide", undefined, owner)).data;
  assert.equal(box.tiers[0].id, d);
  assert.equal(box.tiers[0].ask, "free");
  const badges = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  assert.equal(badges.decideTiers, 1);
  assert.equal(badges.decideHalts, 0);
  // 관측치: 요청 수.
  const obs = (await call("GET", `/api/admin/loop/tier/${d}`, undefined, owner)).data;
  assert.equal(obs.requests.asks, 1);
  assert.equal(obs.requests.ideas, 1);
  assert.match(obs.note, /events/);
  // 열쇠(Claude)는 요금을 못 정해요.
  const k = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  assert.equal((await call("POST", `/api/admin/loop/tier/${d}`, { tier: "free" }, { authorization: `Bearer ${k.key}`, "x-admin": "1" })).status, 403);
  // 최고 관리자가 '더 논의'로 두면 아직 막혀요, 무료로 정하면 켤 수 있어요.
  assert.equal((await call("POST", `/api/admin/loop/tier/${d}`, { tier: "later", note: "다음 주에" }, owner)).status, 200);
  assert.equal((await call("POST", "/api/admin/flags/free.MONEY_STATS/force", { force: "on", why: "열기" }, owner)).status, 409);
  assert.equal((await call("POST", `/api/admin/loop/tier/${d}`, { tier: "free", note: "사용자 결정" }, owner)).status, 200);
  assert.equal((await call("POST", "/api/admin/flags/free.MONEY_STATS/force", { force: "on", why: "열기" }, owner)).status, 200);
  // [LOOP-35] 기본 칸은 '정할 것'이라, 정한 새 기능은 '정한 새 기능' 칸에서 봐요.
  assert.equal((await call("GET", "/api/admin/loop/tiers", undefined, owner)).data.items.length, 0);
  const board = (await call("GET", "/api/admin/loop/tiers?view=decided", undefined, owner)).data.items[0];
  assert.equal(board.tier, "free");
  assert.equal(board.flagOn, true);
  assert.equal(board.observe.flag.key, "free.MONEY_STATS");
  assert.equal((await call("POST", `/api/admin/loop/tier/${d}`, { tier: "gold" }, owner)).data.error, "tier");
});

test("[LOOP-17] 알려진 문제판: 여럿이 겪는 것만 보이고, 나도 겪어요는 묶인 문의 하나가 돼요; 되돌리면 지워요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  const q1 = await ask(call, 1, "위젯이 자정에 안 바뀌어요");
  const d = await item(call, owner, "위젯 자정 갱신", [q1]);
  await call("POST", "/api/admin/dev/sync", { items: [{ id: d, status: "doing", publicTitle: "위젯이 자정에 바로 바뀌지 않아요" }] }, owner);
  // 한 사람만이면 안 보여요.
  assert.equal((await call("GET", "/api/known", undefined, { "x-device": device(5) })).data.items.length, 0);
  const q2 = await ask(call, 2, "위젯 날짜가 아침까지 어제예요");
  await call("POST", `/api/admin/dev/${d}/links`, { inquiry: q2 }, owner);
  t.mock.timers.tick(61e3);
  let known = (await call("GET", "/api/known", undefined, { "x-device": device(5) })).data.items;
  assert.deepEqual(known.map((k) => [k.title, k.state, k.mine]), [["위젯이 자정에 바로 바뀌지 않아요", "fixing", false]]);
  // 폰 5가 나도 겪어요.
  known = (await call("POST", `/api/known/${d}/me`, { on: true, app: "1.38.0 (52)" }, { "x-device": device(5) })).data.items;
  assert.equal(known[0].mine, true);
  assert.equal(known[0].me, 3);
  const mineList = await mine(call, 5);
  assert.equal(mineList.length, 1);
  assert.equal(mineList[0].status, "answered");
  assert.match(mineList[0].messages.at(-1).text, /먼저 고치고 있어요/);
  // 두 번 눌러도 하나.
  await call("POST", `/api/known/${d}/me`, { on: true }, { "x-device": device(5) });
  assert.equal((await mine(call, 5)).length, 1);
  // 판이 나가면 이 문의에도 안내가 가요.
  await call("POST", `/api/admin/dev/${d}`, { status: "done", releasedIn: "1.39.0" }, owner);
  assert.equal((await call("POST", "/api/admin/desk/ship", { version: "1.39.0", notify: true }, owner)).data.answered, 3);
  assert.match((await mine(call, 5))[0].messages.at(-1).text, /1\.39\.0 버전이 나왔어요/);
  // 나도 겪어요만 누른 폰은 문제 신고 보상(3일)을 받지 않아요(knownDays 기본 0).
  const days = DB.db.prepare("SELECT device, kind, days FROM day_rewards ORDER BY kind").all();
  assert.deepEqual(days.map((r) => r.kind), ["bug", "bug"]);
  // 되돌리기: 지워요.
  known = (await call("POST", `/api/known/${d}/me`, { on: false }, { "x-device": device(5) })).data.items;
  assert.equal(known[0].mine, false);
  assert.equal((await mine(call, 5)).length, 0);
  assert.equal((await call("POST", "/api/known/dAAAAAAAAAAAAAAA/me", { on: true }, { "x-device": device(5) })).status, 404);
  assert.equal((await call("POST", `/api/known/${d}/me`, { on: true })).status, 400);
});

test("[LOOP-17][LOOP-08] 관리자 앱에서도 알려진 문제 제목·보일지·전달 길·크기를 정하고 기록에 남아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const q1 = await ask(call, 1, "잠금 화면에서 알람 소리가 작아요");
  const d = await item(call, owner, "잠금 화면 알람 소리", [q1]);
  const r = await call("POST", `/api/admin/dev/${d}`, { publicTitle: "  잠금 화면 알람 소리가 작아요 ", known: true, route: "app", size: "fix" }, owner);
  assert.equal(r.status, 200);
  assert.deepEqual([r.data.publicTitle, r.data.known, r.data.route, r.data.size], ["잠금 화면 알람 소리가 작아요", 1, "app", "fix"]);
  assert.ok(r.data.history.some((h) => h.action === "edit" && h.detail?.publicTitle));
  // 늘 보이기면 한 사람만 겪어도 보여요.
  t.mock.timers.tick(61e3);
  assert.deepEqual((await call("GET", "/api/known", undefined, { "x-device": device(5) })).data.items.map((k) => k.title), ["잠금 화면 알람 소리가 작아요"]);
  // 보일 근무 유형: 모르는 이름은 버리고, 알려진 문제판이 그대로 내려줘요(앱이 거름).
  const st = await call("POST", `/api/admin/dev/${d}`, { styles: ["SHIFT", "DAY", "교대"] }, owner);
  assert.deepEqual(st.data.styles, ["SHIFT", "DAY"]);
  t.mock.timers.tick(61e3);
  assert.deepEqual((await call("GET", "/api/known", undefined, { "x-device": device(5) })).data.items[0].styles, ["SHIFT", "DAY"]);
  assert.equal((await call("POST", `/api/admin/dev/${d}`, { route: "rocket" }, owner)).status, 400);
  const off = await call("POST", `/api/admin/dev/${d}`, { known: false, route: null }, owner);
  assert.deepEqual([off.data.known, off.data.route], [-1, ""]);
});

test("[LOOP-14] 보상 규칙 세분화: 문제 신고·다듬기·새 기능 제안·한 달 상한을 최고 관리자가 바꾸고, 회사 방식 요청 보상은 따로예요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  let rules = (await call("GET", "/api/admin/loop", undefined, owner)).data.rewards;
  assert.deepEqual(rules, { bugDays: 3, polishDays: 3, featureDays: 7, knownDays: 0, monthCap: 14 });
  rules = (await call("POST", "/api/admin/loop/rewards", { bugDays: 2, featureDays: 10, monthCap: 20 }, owner)).data;
  assert.deepEqual(rules, { bugDays: 2, polishDays: 3, featureDays: 10, knownDays: 0, monthCap: 20 });
  assert.equal((await call("POST", "/api/admin/loop/rewards", { bugDays: 31 }, owner)).data.error, "bugDays");
  const bug = await ask(call, 1, "사진 인식이 멈춰요");
  const idea = await ask(call, 2, "달력에서 주 번호를 보여 주세요", "idea");
  const d1 = await item(call, owner, "사진 인식 멈춤", [bug]);
  const d2 = await item(call, owner, "주 번호", [idea]);
  await call("POST", "/api/admin/dev/sync", { items: [{ id: d2, size: "feature" }] }, owner);
  await call("POST", `/api/admin/dev/${d1}`, { status: "done" }, owner);
  await call("POST", `/api/admin/dev/${d2}`, { status: "done" }, owner);
  const got = DB.db.prepare("SELECT kind, days FROM day_rewards ORDER BY kind").all();
  assert.deepEqual(got.map((r) => [r.kind, r.days]), [["bug", 2], ["idea", 10]]);
  // 크기를 모르는 제안은 주지 않아요.
  const idea2 = await ask(call, 3, "글꼴을 더 넣어 주세요", "idea");
  const d3 = await item(call, owner, "글꼴", [idea2]);
  await call("POST", `/api/admin/dev/${d3}`, { status: "done" }, owner);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM day_rewards WHERE kind = 'idea'").get().n, 1);
  // 회사 방식 요청 반영 보상(askMonths)은 채택 보상과 따로 저장돼요.
  const s = (await call("POST", "/api/admin/settings", { adoptPoints: 3, rewardMonths: 3, askMonths: 1 }, owner)).data;
  assert.equal(s.askMonths, 1);
  assert.equal(s.rewardMonths, 3);
});

test("[LOOP-12] 믿을수록 더 맡기기: 종류마다 그대로 나간 수를 세고, 켠 종류는 보류 없이 가요(결제 말은 빼고)", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, env } = server();
  const owner = await signIn(call);
  const k = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const key = { authorization: `Bearer ${k.key}`, "x-admin": "1" };
  const q1 = await ask(call, 1, "근무표 공유는 어디서 하나요", "question");
  assert.equal((await call("POST", `/api/admin/inquiries/${q1}/reply`, { text: "달력 › 공유에서 해요.", pile: "faq" }, key)).status, 200);
  assert.equal((await thread(call, q1, 1)).messages.length, 1, "보류 중에는 앱에 안 보여요");
  t.mock.timers.tick(11 * 60e3);
  await fiveMinutes(env);
  let auto = (await call("GET", "/api/admin/loop/autonomy", undefined, owner)).data.piles.find((p) => p.pile === "faq");
  assert.equal(auto.sent, 1);
  assert.equal(auto.streak, 1);
  // 최고 관리자가 '자주 쓰는 답'을 바로 보내기로 켜면 열쇠 답이 바로 가요.
  assert.deepEqual((await call("POST", "/api/admin/loop/switches", { instant: ["faq", "new"] }, owner)).data.instant, ["faq"]);
  const q2 = await ask(call, 2, "위젯은 어디서 켜나요", "question");
  await call("POST", `/api/admin/inquiries/${q2}/reply`, { text: "홈 화면을 길게 눌러 위젯에서 밤낮을 골라요.", pile: "faq" }, key);
  assert.equal((await thread(call, q2, 2)).messages.length, 2);
  // 결제·환불 말이 있으면 켜 두어도 보류해요.
  const q3 = await ask(call, 3, "구독 환불은 어떻게 하나요", "question");
  await call("POST", `/api/admin/inquiries/${q3}/reply`, { text: "Play 스토어 › 결제 및 구독에서 환불을 요청해요.", pile: "faq" }, key);
  assert.equal((await thread(call, q3, 3)).messages.length, 1);
  // 사람이 취소한 답은 연속을 끊어요.
  const held = (await call("GET", `/api/admin/inquiries/${q3}`, undefined, owner)).data.held;
  assert.equal(held.length, 1);
  assert.equal((await call("POST", `/api/admin/inquiries/${q3}/held/${held[0].id}`, { cancel: true }, owner)).status, 200);
  auto = (await call("GET", "/api/admin/loop/autonomy", undefined, owner)).data.piles.find((p) => p.pile === "faq");
  assert.equal(auto.cancelled, 1);
  assert.equal(auto.streak, 0);
  assert.equal(auto.quick, 1);
});

test("[LOOP-13] /api/config의 update: 최고 관리자가 정한 versionCode 기준", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  assert.deepEqual((await call("GET", "/api/config")).data.update, { urgentMin: 0, flexibleMin: 0 });
  await call("POST", "/api/admin/loop/switches", { urgentMin: 53, flexibleMin: 54 }, owner);
  assert.deepEqual((await call("GET", "/api/config")).data.update, { urgentMin: 53, flexibleMin: 54 });
  assert.equal((await call("POST", "/api/admin/loop/switches", { urgentMin: -1 }, owner)).data.error, "urgentMin");
  // 정기 출발일: 7~60일.
  const train = (await call("POST", "/api/admin/loop/train", { start: "2026-11-20", days: 14 }, owner)).data;
  assert.equal(train.next, "2026-11-20");
  assert.equal((await call("POST", "/api/admin/loop/train", { start: "2026-11-20", days: 3 }, owner)).data.error, "days");
});

test("[LOOP-34] 개발사항 출처: 문의·개선 요청·개발 계획·숫자 신호·직접으로 세고 골라 봐요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const admin = await signIn(call);
  const bug = await ask(call, 1, "알람이 안 울려요");
  const idea = await ask(call, 2, "달력에 주 번호를 보여 주세요", "idea");
  const fromBug = await item(call, admin, "알람 안 울림", [bug]);
  const fromIdea = await item(call, admin, "주 번호", [idea]);
  const own = (await call("POST", "/api/admin/dev", { title: "관리자가 바로 만든 것" }, admin)).data.id;
  const sync = (await call("POST", "/api/admin/keys", { scope: "sync" }, admin)).data;
  await call("POST", "/api/admin/dev/sync", { version: "1.39.0", sha: "a".repeat(40), items: [{ track: "PAYX-65", title: "실제 근무 유형별 급여", status: "todo", size: "feature", route: "app" }] },
    { authorization: `Bearer ${sync.key}`, "x-admin": "1" });
  let list = (await call("GET", "/api/admin/dev?status=open", undefined, admin)).data;
  const by = Object.fromEntries(list.items.map((x) => [x.title, x.source]));
  assert.equal(by["알람 안 울림"], "inquiry");
  assert.equal(by["주 번호"], "idea");
  assert.equal(by["관리자가 바로 만든 것"], "admin");
  assert.equal(by["실제 근무 유형별 급여"], "repo");
  assert.deepEqual(list.sources, { inquiry: 1, idea: 1, repo: 1, signal: 0, claude: 0, admin: 1 });
  assert.equal(list.sourceNames.repo, "개발 계획");
  list = (await call("GET", "/api/admin/dev?status=open&from=idea", undefined, admin)).data;
  assert.deepEqual(list.items.map((x) => x.id), [fromIdea]);
  assert.equal(list.total, 1);
  assert.equal(list.sources.inquiry, 1, "고른 출처와 상관없이 모두 세요");
  assert.equal((await call("GET", `/api/admin/dev/${fromBug}`, undefined, admin)).data.source, "inquiry");
  assert.equal((await call("GET", `/api/admin/dev/${own}`, undefined, admin)).data.source, "admin");
});

test("[LOOP-35] 요금은 새 기능만 물어요: 고침·다듬기는 원래 요금 그대로, 서버로만 닿는 새 기능은 운영용; 기준은 최고 관리자가 바꾸고 칸마다 걸러 봐요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const sync = (await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data;
  await call("POST", "/api/admin/dev/sync", { items: [
    { track: "TIER-01", title: "알람 소리 고침", status: "doing", size: "fix", route: "app" },
    { track: "TIER-02", title: "달력 칸 더 크게", status: "doing", size: "polish", route: "app" },
    { track: "TIER-03", title: "관리자 앱 관측", status: "doing", size: "feature", route: "server" },
    { track: "TIER-04", title: "근무별 돈 칩", status: "doing", size: "feature", route: "flag", flag: "pro.MONEY_CHIP", tierAsk: "pro", tierWhy: "가계부 위 더하기" },
    { track: "TIER-05", title: "실제 근무 유형별 급여", status: "todo", size: "feature", route: "app" },
    { track: "TIER-06", title: "크기 없는 것", status: "todo" },
  ] }, { authorization: `Bearer ${sync.key}`, "x-admin": "1" });
  let board = (await call("GET", "/api/admin/loop/tiers", undefined, owner)).data;
  assert.equal(board.view, "ask");
  assert.deepEqual(board.items.map((x) => x.track).sort(), ["TIER-04", "TIER-05"]);
  assert.deepEqual(board.counts, { ask: 2, decided: 0, existing: 2, internal: 1, unknown: 1, all: 6 });
  assert.match(board.tierHelp.free, /다시 잠그지 않아요/);
  assert.ok(board.items.every((x) => x.observe && x.basis.key === "new" && x.basis.auto));
  const existing = (await call("GET", "/api/admin/loop/tiers?view=existing", undefined, owner)).data.items;
  assert.equal(existing[0].effective.name, "무료(기존과 같음)");
  assert.equal(existing[0].observe, null, "묻지 않는 것은 관측치도 읽지 않아요");
  assert.match(existing[0].basis.why, /묻지 않아요/);
  const internal = (await call("GET", "/api/admin/loop/tiers?view=internal", undefined, owner)).data.items;
  assert.equal(internal[0].track, "TIER-03");
  // 결정함·메뉴 숫자도 같은 기준이에요.
  const box = (await call("GET", "/api/admin/loop/decide", undefined, owner)).data;
  assert.deepEqual(box.tiers.map((x) => x.track).sort(), ["TIER-04", "TIER-05"]);
  assert.equal((await call("GET", "/api/admin/badges", undefined, owner)).data.decideTiers, 2);
  // 최고 관리자가 '이미 있는 급여 계산을 넓히는 일'로 바로잡으면 묻지 않아요; 열쇠는 못 바꿔요.
  const payx = board.items.find((x) => x.track === "TIER-05").id;
  const k = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  assert.equal((await call("POST", `/api/admin/loop/basis/${payx}`, { basis: "existing" }, { authorization: `Bearer ${k.key}`, "x-admin": "1" })).status, 403);
  assert.equal((await call("POST", `/api/admin/loop/basis/${payx}`, { basis: "existing", note: "기존 급여 계산을 넓힘" }, owner)).status, 200);
  board = (await call("GET", "/api/admin/loop/tiers", undefined, owner)).data;
  assert.deepEqual(board.items.map((x) => x.track), ["TIER-04"]);
  assert.equal(board.counts.existing, 3);
  const item = (await call("GET", `/api/admin/dev/${payx}`, undefined, owner)).data;
  assert.equal(item.basis.key, "existing");
  assert.equal(item.basis.auto, false);
  assert.equal(item.pay.name, "무료(기존과 같음)");
  assert.ok(item.history.some((h) => h.action === "basis" && h.detail.note === "기존 급여 계산을 넓힘"));
  // 운영용으로 본 서버 일도 '새 기능'으로 바꾸면 물어요. 모르는 값은 막아요.
  const obs = internal[0].id;
  assert.equal((await call("POST", `/api/admin/loop/basis/${obs}`, { basis: "new" }, owner)).status, 200);
  assert.equal((await call("GET", "/api/admin/loop/tiers", undefined, owner)).data.counts.ask, 2);
  assert.equal((await call("POST", `/api/admin/loop/basis/${obs}`, { basis: "gold" }, owner)).data.error, "basis");
  // 개발사항 목록도 같은 기준으로 세고 걸러요.
  const list = (await call("GET", "/api/admin/dev?status=open&kind=existing", undefined, owner)).data;
  assert.deepEqual(list.items.map((x) => x.track).sort(), ["TIER-01", "TIER-02", "TIER-05"]);
  assert.deepEqual(list.kinds, { new: 2, existing: 3, internal: 0, unknown: 1 });
});

test("[LOOP-36] 관리자가 직접 넣은 개발사항은 1순위: 목록·자동 순환 대기열·Claude 넘김 글 맨 위, 내리고 올릴 수 있어요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const q1 = await ask(call, 1, "알람이 안 울려요");
  const q2 = await ask(call, 2, "알람이 안 울려요 저도");
  const asked = await item(call, owner, "문의 많은 알람", [q1, q2]);
  const k = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const key = { authorization: `Bearer ${k.key}`, "x-admin": "1" };
  const byClaude = (await call("POST", "/api/admin/dev", { title: "Claude가 찾은 것" }, key)).data;
  assert.equal(byClaude.first, false);
  assert.equal(byClaude.source, "claude");
  t.mock.timers.tick(60e3);
  const mine = (await call("POST", "/api/admin/dev", { title: "내가 넣은 급여 개선" }, owner)).data;
  assert.equal(mine.first, true);
  assert.equal(mine.source, "admin");
  // 사용자 말을 옮기는 Claude 열쇠는 origin "admin"으로 1순위를 만들 수 있어요(동기화 열쇠는 못 해요).
  const relayed = (await call("POST", "/api/admin/dev", { title: "사용자가 말한 것", origin: "admin" }, key)).data;
  assert.equal(relayed.first, true);
  let list = (await call("GET", "/api/admin/dev?status=open", undefined, owner)).data;
  assert.deepEqual(list.items.slice(0, 2).map((x) => x.title).sort(), ["내가 넣은 급여 개선", "사용자가 말한 것"]);
  assert.equal(list.firsts, 2);
  assert.equal(list.items[2].id, asked, "그다음은 문의 수 순");
  const queue = (await call("GET", "/api/admin/loop/queue", undefined, owner)).data.items;
  assert.ok(queue[0].first && queue[1].first);
  assert.ok(queue.findIndex((x) => x.id === asked) > 1, "점수가 높아도 직접 넣은 것 뒤");
  const handoff = (await call("GET", "/api/admin/inquiries/handoff", undefined, owner)).data;
  const text = handoff.text ?? "";
  assert.ok(text.indexOf("내가 넣은 급여 개선") < text.indexOf("문의 많은 알람"));
  assert.match(text, /1순위\(관리자가 직접 넣음/);
  // 내리면 보통 순서로, 기록에 남아요. 저장소가 추적 번호를 붙여도 1순위는 그대로예요.
  assert.equal((await call("POST", `/api/admin/dev/${relayed.id}`, { first: false }, owner)).status, 200);
  const sync = (await call("POST", "/api/admin/keys", { scope: "sync" }, owner)).data;
  await call("POST", "/api/admin/dev/sync", { items: [{ id: mine.id, track: "PAYX-99", status: "doing", size: "feature", route: "app" }] }, { authorization: `Bearer ${sync.key}`, "x-admin": "1" });
  list = (await call("GET", "/api/admin/dev?status=open", undefined, owner)).data;
  assert.equal(list.items[0].id, mine.id);
  assert.equal(list.items[0].source, "admin");
  assert.equal(list.firsts, 1);
  const hist = (await call("GET", `/api/admin/dev/${relayed.id}`, undefined, owner)).data.history;
  assert.ok(hist.some((h) => h.action === "edit" && h.detail.first?.[1] === false));
  assert.equal((await call("POST", `/api/admin/dev/${asked}`, { first: true }, { authorization: `Bearer ${sync.key}`, "x-admin": "1" })).status, 403);
  // 사용자 카드(11-09 '최고 관리자 것만'): 다른 관리자가 직접 넣은 것은 '직접'이지만 1순위가 아니고, 1순위를 올리지도 못해요.
  const APP = { "x-admin": "1", origin: ORIGIN };
  const perms = { review: "edit", inquiries: "edit", dev: "edit", devStatus: "edit", companies: "view", rewards: "none", settings: "none", community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none", flags: "none", flagOff: "none" };
  const inv = await call("POST", "/api/admin/invites", { email: "ops@example.com", perms }, owner);
  await call("POST", "/api/admin/join", { token: inv.data.link.split("#")[1], name: "운영", password: "ops-member-2026" }, { ...APP, ...from(41) });
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === "ops@example.com").id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email: "ops@example.com", password: "ops-member-2026" }, { ...APP, ...from(41) });
  const other = { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
  const theirs = (await call("POST", "/api/admin/dev", { title: "운영 관리자가 넣은 것" }, other)).data;
  assert.equal(theirs.first, false);
  assert.equal(theirs.source, "admin");
  assert.equal((await call("POST", `/api/admin/dev/${theirs.id}`, { first: true }, other)).status, 403);
  assert.equal((await call("POST", `/api/admin/dev/${theirs.id}`, { first: true }, owner)).status, 200);
});
