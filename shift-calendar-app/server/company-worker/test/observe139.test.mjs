// 1.39 [OBS2-01~06] 관측(사용자 11-08 '사용자 유입 이탈 등 모든 상황을 관측', '통계를 통한 완전한 자가 발전', '수집할 데이터 … 통찰'):
// Play 보고서(UTF-16 CSV)를 읽어 날짜 줄·출처·판으로, 규칙이 개선 신호를 찾고, 신호는 개발사항이 되고, 숫자가 돌아오면 '좋아짐'과
// 그때 값이 남아요. 관측 빈틈은 처리방침이 필요한 질문을 따로 표시해요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { server, signIn } from "./helpers.mjs";
import { obsBucket, obsCsv, obsGaps, obsMerge, obsParse, obsRules, obsText, OBS } from "../src/observe.js";

const DAY = 86_400_000;
/** Play 보고서처럼 UTF-16LE + BOM. */
const utf16 = (text) => {
  const body = Buffer.from(text, "utf16le");
  return new Uint8Array(Buffer.concat([Buffer.from([0xff, 0xfe]), body]));
};
const days = (end, n) => Array.from({ length: n }, (_, i) => new Date(Date.parse(`${end}T00:00:00Z`) - (n - 1 - i) * DAY).toISOString().slice(0, 10));

function installsCsv(list, spike = false) {
  return ["Date,Package Name,Daily Device Installs,Daily Device Uninstalls,Daily Device Upgrades,Total User Installs,Daily User Installs,Daily User Uninstalls,Active Device Installs,Install events,Update events,Uninstall events",
    ...list.map((d, i) => `${d},com.bamnat.app,4,1,0,${100 + i},4,1,${60 + i},5,2,${spike && i >= list.length - 3 ? 8 : 1}`)].join("\n");
}
const storeCsv = (list) => ["Date,Package name,Traffic source,Search term,UTM source,UTM campaign,Store listing acquisitions,Store listing visitors,Store listing conversion rate",
  ...list.flatMap((d) => [`${d},com.bamnat.app,Google Play search,"근무표, 교대",,,6,40,15.00%`, `${d},com.bamnat.app,Third-party referrals,,bamnat_web,calc,2,5,40.00%`])].join("\n");
const versionsCsv = (list) => ["Date,Package Name,App Version Code,Daily Device Installs,Daily Device Uninstalls,Daily Device Upgrades,Active Device Installs,Install events,Update events,Uninstall events",
  ...list.flatMap((d, i) => [`${d},com.bamnat.app,52,1,0,0,30,1,0,0`, ...(i >= 3 ? [`${d},com.bamnat.app,53,1,0,1,10,1,1,0`] : [])])].join("\n");
const ratingsCsv = (list) => ["Date,Package Name,Daily Average Rating,Total Average Rating", ...list.map((d, i) => `${d},com.bamnat.app,${i % 2 ? "NA" : "4.50"},4.41`)].join("\n");

test("[OBS2-01] Play 보고서 CSV(UTF-16)를 날짜 줄·출처·판·별점으로 읽어요", () => {
  assert.equal(obsBucket({ PLAY_REPORTS_BUCKET: "gs://pubsite_prod_rev_0123456789/stats/installs" }), "pubsite_prod_rev_0123456789");
  assert.equal(obsBucket({ PLAY_REPORTS_BUCKET: "Bad Name!" }), null);
  const list = days("2026-11-06", 20);
  const text = obsText(utf16(installsCsv(list)));
  assert.equal(obsCsv(text)[0]["active device installs"], "60");
  // 따옴표 안 쉼표도 한 칸이에요.
  assert.equal(obsCsv(storeCsv(list))[0]["search term"], "근무표, 교대");
  const got = obsParse({ installs: [text], store: [obsText(utf16(storeCsv(list)))], versions: [versionsCsv(list)], ratings: [ratingsCsv(list)] });
  assert.equal(got.days.length, 20);
  assert.equal(got.lastDay, "2026-11-06");
  const last = got.days.at(-1);
  assert.deepEqual([last.installs, last.uninstalls, last.base, last.visitors, last.acq], [5, 1, 79, 45, 8]);
  assert.equal(got.days[0].rating, 4.5);
  assert.equal(got.days[1].rating, null, "NA는 없음");
  assert.equal(got.days[1].total, 4.41);
  assert.deepEqual(got.sources.map((s) => s.source), ["Google Play search", "utm:bamnat_web"]);
  assert.equal(got.sources[0].acq, 6 * 20);
  assert.deepEqual(got.versions.rows, [{ code: 53, n: 10 }, { code: 52, n: 30 }]);
  assert.equal(got.versions.first[53], list[3]);
});

test("[OBS2-04] 규칙이 삭제 급증·새 판 멈춤·찾았는데 없던 말을 찾고, 신호는 합쳐지고 사라지면 '좋아짐'과 그때 값을 남겨요", () => {
  const list = days("2026-11-06", 20);
  const p = obsParse({ installs: [installsCsv(list, true)], versions: [versionsCsv(list)] });
  const board = { play: { ...p, state: "ok" }, active: { play: [], config: [] }, quality: { vitals: null }, sample: { daily: 3, features: [] },
    voice: { misses: [{ word: "교대 바꾸기 요청", place: "settings", placeName: "설정 검색", n: 4 }] } };
  const found = obsRules(board);
  const on = Object.fromEntries(found.filter((f) => f.on).map((f) => [f.kind, f]));
  assert.ok(on["uninstall-spike"], JSON.stringify(found.map((f) => [f.kind, f.on, f.value, f.base])));
  assert.equal(on["uninstall-spike"].value, 8);
  assert.ok(on["version-stuck"], "53은 16일째 25%");
  assert.equal(on["version-stuck"].subject, "53");
  assert.match(on["search-miss"].title, /교대 바꾸기 요청/);
  assert.equal(found.find((f) => f.kind === "net-loss").on, false, "설치가 더 많아요");
  const t0 = Date.parse("2026-11-07T03:00:00Z");
  let list1 = obsMerge([], found, t0);
  assert.equal(list1.filter((s) => s.state === "new").length, 3);
  const spike = list1.find((s) => s.kind === "uninstall-spike");
  // 한 시간 뒤 같은 것을 다시 찾으면 새로 생기지 않고 고쳐져요.
  list1 = obsMerge(list1, found, t0 + 3_600_000);
  assert.equal(list1.length, 3);
  // 개발사항으로 넘긴 뒤, 삭제가 돌아오고 사흘이 지나면 '좋아짐'과 그때 값.
  list1.find((s) => s.key === spike.key).state = "dev";
  const calm = found.map((f) => (f.kind === "uninstall-spike" ? { ...f, on: false, value: 1 } : f));
  list1 = obsMerge(list1, calm, t0 + 3_600_000 * 2);
  list1 = obsMerge(list1, calm, t0 + OBS.clearMs + 3_600_000 * 2);
  const done = list1.find((s) => s.key === spike.key);
  assert.ok(done.cleared);
  assert.equal(done.value, 8);
  assert.equal(done.after, 1);
  // 다시 나타나면 같은 줄을 다시 열고(개발사항 그대로) 몇 번째인지 세요.
  list1 = obsMerge(list1, found, t0 + OBS.clearMs + DAY);
  const back = list1.find((s) => s.key === spike.key);
  assert.equal(back.cleared, null);
  assert.equal(back.back, 1);
  assert.equal(back.state, "dev");
  // '아님'으로 둔 것은 30일 동안 다시 띄우지 않아요.
  const miss = list1.find((s) => s.kind === "search-miss");
  Object.assign(miss, { state: "dismiss", at: t0 + OBS.clearMs + DAY });
  list1 = obsMerge(list1, found, t0 + OBS.clearMs + 2 * DAY);
  assert.equal(list1.filter((s) => s.kind === "search-miss").length, 1);
  assert.equal(list1.find((s) => s.kind === "search-miss").state, "dismiss");
});

test("[OBS2-05] 관측 빈틈: 연결이 빠진 것은 고치는 법, 새로 모을 것은 처리방침·사용자 결정, 이벤트 없는 개발사항", () => {
  const gaps = obsGaps({ play: { state: "nobucket" }, quality: { vitals: null }, sample: { daily: 1, lifeOn: false },
    dev: { noEvents: [{ id: "d1", title: "첫 설정 줄이기" }], noEventsCount: 4 } });
  const keys = gaps.map((g) => g.key);
  assert.ok(keys.includes("play-bucket"));
  assert.match(gaps.find((g) => g.key === "play-bucket").how, /PLAY_REPORTS_BUCKET/);
  assert.ok(keys.includes("sample-small"));
  assert.ok(keys.includes("life-off"));
  assert.match(gaps.find((g) => g.key === "dev-events").title, /4개/);
  assert.deepEqual(gaps.filter((g) => g.policy).map((g) => g.key), ["by-type", "feature-keep", "why-leave"]);
});

test("[OBS2-01][OBS2-04] 관측 판·매시 작업·신호 → 개발사항, Claude 열쇠도; Play 보고서는 서비스 계정 토큰으로 저장소에서 읽어요", async (t) => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const account = JSON.stringify({ client_email: "bamnat@example.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const now = Date.now();
  const end = new Date(now - 2 * DAY).toISOString().slice(0, 10);
  const list = days(end, 24);
  const asked = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init = {}) => {
    url = String(url);
    if (url === "https://oauth2.googleapis.com/token") return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }));
    if (url.startsWith("https://storage.googleapis.com/")) {
      asked.push(decodeURIComponent(url.split("/o/")[1].split("?")[0]));
      assert.equal(new Headers(init.headers).get("authorization"), "Bearer tok");
      const ym = end.slice(0, 7).replace("-", "");
      if (url.includes(`_${ym}_overview.csv`) && url.includes("installs_")) return new Response(utf16(installsCsv(list, true)));
      if (url.includes(`_${ym}_traffic_source.csv`)) return new Response(utf16(storeCsv(list)));
      return new Response("not found", { status: 404 });
    }
    if (url.startsWith("https://playdeveloperreporting.googleapis.com/")) return new Response(JSON.stringify({ rows: [] }));
    return new Response("{}", { status: 404 });
  };
  t.after(() => { globalThis.fetch = real; });
  const { call, DB } = server({ PLAY_SERVICE_ACCOUNT: account, PLAY_REPORTS_BUCKET: "pubsite_prod_rev_42" });
  const owner = await signIn(call);
  const b = (await call("GET", "/api/admin/observe", undefined, owner)).data;
  assert.equal(b.play.state, "ok", JSON.stringify(b.play));
  assert.ok(asked.some((p) => p.startsWith("stats/installs/installs_com.bamnat.app_")), asked.join(" "));
  assert.equal(b.play.days.at(-1).day, end);
  assert.equal(b.play.days.at(-1).uninstalls, 8);
  assert.ok(b.found.some((f) => f.kind === "uninstall-spike" && f.on));
  assert.ok(!b.gaps.some((g) => g.key === "play-bucket"));
  // 매시 작업이 신호를 쌓고, 숫자 메뉴의 배지가 그 수를 세요.
  const { observeTick } = await import("../src/observe.js");
  assert.ok(await observeTick({ DB, PLAY_SERVICE_ACCOUNT: account, PLAY_REPORTS_BUCKET: "pubsite_prod_rev_42" }, now) >= 1);
  const badges = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  assert.ok(badges.obsNew >= 1, JSON.stringify(badges));
  const sigs = (await call("GET", "/api/admin/observe/signals", undefined, owner)).data.list;
  const spike = sigs.find((s) => s.kind === "uninstall-spike");
  // Claude 열쇠: 읽고, 개발사항으로 넘겨요.
  const made = (await call("POST", "/api/admin/keys", { label: "Claude" }, owner)).data;
  const claude = { authorization: `Bearer ${made.key}`, "x-admin": "1" };
  assert.equal((await call("GET", "/api/admin/observe", undefined, claude)).status, 200);
  assert.equal((await call("POST", "/api/admin/observe/signals", { key: spike.key, act: "nope" }, claude)).status, 400);
  const r = await call("POST", "/api/admin/observe/signals", { key: spike.key, act: "dev" }, claude);
  assert.equal(r.status, 200, JSON.stringify(r.data));
  const dev = DB.db.prepare("SELECT title, body, created_by_name FROM dev_items WHERE id = ?").get(r.data.signal.devId);
  assert.match(dev.title, /앱 삭제가 늘어난 이유/);
  assert.match(dev.body, new RegExp(`관측 신호 ${spike.key}`));
  assert.equal((await call("POST", "/api/admin/observe/signals", { key: spike.key, act: "dev" }, claude)).status, 409, "두 번 만들지 않아요");
  assert.equal(DB.db.prepare("SELECT detail FROM audit WHERE action = 'obs_signal'").get().detail.startsWith("개발사항으로 d"), true);
  const after = (await call("GET", "/api/admin/badges", undefined, owner)).data;
  assert.equal(after.obsNew, badges.obsNew - 1);
});

test("[OBS2-02][OBS2-03] Play 계정이 없어도 판은 서버 요청 수와 빈틈으로 열려요", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const hour = new Date(Date.now() + 9 * 3_600_000 - DAY).toISOString().slice(0, 13);
  DB.db.prepare("INSERT INTO req_stats (hour, route, n, err, ms_sum, ms_max) VALUES (?, '/api/config', 42, 0, 100, 10)").run(hour);
  const b = (await call("GET", "/api/admin/observe", undefined, owner)).data;
  assert.equal(b.play.state, "off");
  assert.equal(b.active.config.at(-1).n, 42);
  assert.ok(b.gaps.some((g) => g.key === "play-account"));
});
