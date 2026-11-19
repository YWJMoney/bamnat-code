// 2.0.0 [RVQ-31] 검토 SVC-27: '나도 겪어요'를 설치 번호만 바꿔 얼마든지 늘릴 수 있었어요(폰당 하루 10번만, 네트워크 한도 없음).
// 이제 네트워크 하루 한도, 같은 네트워크는 그날 그 개발사항에 한 표, 알려진 문제판과 개발 대기열 점수는 서로 다른 네트워크 수로 세요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn } from "./helpers.mjs";

const NOW = Date.parse("2026-11-20T01:00:00Z");
const HOUR = 3600e3;
const me = (call, d, n, ip) => call("POST", `/api/known/${d}/me`, { on: true, app: "2.0.0 (54)" }, { "x-device": device(n), ...from(ip) });

async function knownItem(call, owner, title) {
  const d = (await call("POST", "/api/admin/dev", { title }, owner)).data.id;
  await call("POST", `/api/admin/dev/${d}`, { publicTitle: title, known: true, status: "doing" }, owner);
  return d;
}

test("[RVQ-31] SVC-27 한 네트워크에서 설치만 바꿔 눌러도 한 표, 다른 네트워크가 눌러야 늘어요; 누른 분은 모두 안내를 받아요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call, DB } = server();
  const owner = await signIn(call);
  const d = await knownItem(call, owner, "위젯이 자정에 바로 바뀌지 않아요");
  let items;
  for (const n of [1, 2, 3, 4, 5, 6]) {
    const r = await me(call, d, n, 7);
    assert.equal(r.status, 200, `폰 ${n}`);
    items = r.data.items;
  }
  assert.equal(items[0].me, 1, "같은 네트워크 여섯 대는 한 표");
  assert.equal(items[0].mine, true);
  items = (await me(call, d, 7, 8)).data.items;
  assert.equal(items[0].me, 2);
  // 개발 대기열 점수도 서로 다른 네트워크 수로(묶인 줄은 일곱 개 그대로 보여요): 2×3 + 앱 판 1 = 7(전에는 7×3 + 1 = 22).
  const q = (await call("GET", "/api/admin/loop/queue", undefined, owner)).data.items.find((x) => x.id === d);
  assert.deepEqual([q.asks, q.linked, q.score], [2, 7, 7]);
  // 누른 일곱 분 모두 묶인 문의가 있어 고친 판 안내를 받아요.
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM dev_links WHERE item_id = ?").get(d).n, 7);
  assert.equal(DB.db.prepare("SELECT COUNT(*) AS n FROM dev_links WHERE item_id = ? AND cluster = 'x'").get(d).n, 5);
  // 네트워크 해시는 줄에 남지 않아요.
  assert.ok(!DB.db.prepare("PRAGMA table_info(dev_links)").all().some((c) => c.name === "net"));
});

test("[RVQ-31] SVC-27 네트워크 하루 한도: 새 설치는 한 시간에 몇 번만, 한 네트워크는 하루 30번까지; 다른 네트워크는 그대로 눌러요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: NOW });
  const { call } = server();
  const owner = await signIn(call);
  const items = [];
  for (let i = 0; i < 4; i++) items.push(await knownItem(call, owner, `알려진 문제 ${i + 1}`));
  // 새 설치(3일 안)는 한 네트워크에서 한 시간에 8번(30의 4분의 1)까지.
  let n = 0;
  let r;
  for (let k = 0; k < 9; k++) r = await me(call, items[0], ++n, 7);
  assert.deepEqual([r.status, r.data.error], [429, "rate_limited"]);
  assert.equal((await me(call, items[0], ++n, 9)).status, 200, "다른 네트워크는 그대로");
  // 한 시간씩 지나며 같은 네트워크에서 더 눌러요: 하루 30번이 차면 막혀요.
  let ok = 8;
  let blocked = null;
  for (let hour = 1; hour <= 4 && !blocked; hour++) {
    t.mock.timers.tick(HOUR);
    for (let k = 0; k < 8; k++) {
      r = await me(call, items[hour % 4], ++n, 7);
      if (r.status !== 200) {
        blocked = r;
        break;
      }
      ok++;
    }
  }
  // 막힌 시도(첫 시간의 아홉 번째)도 하루 수에 들어요: 8 + 1 + 21 = 30.
  assert.equal(ok, 29);
  assert.equal(blocked?.status, 429);
});
