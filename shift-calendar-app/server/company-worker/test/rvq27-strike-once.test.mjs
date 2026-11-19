// 2.0.0 [RVQ-27] 검토 SVC-58, D·총괄 11-19: 실서버는 처리방침 3.12 시행(11-09)부터 신고로 가려진 것의 '확인 대기' 벌점을 쌓아요. 고치기
// 전 코드는 같은 글이 다시 가려질 때 대기 줄을 또 쌓았고, 운영자가 두 번 확인하면 두 줄이 다 셌어요. 이 고침은 줄을 지우지 않고 셀 때
// 가려진 것(사람·종류·글) 하나에 한 번만 세요. 실서버에 남은 모양(같은 글의 대기 줄 여럿, 이미 두 번 확인되어 센 줄 둘)을 그대로 넣어 봐요.
// `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, device, from, signIn } from "./helpers.mjs";

const DAY = 24 * 3600e3;
const mine = (n) => ({ "x-device": device(n) });

async function plaza(call, env) {
  const a = await call("POST", "/api/reports", report(1), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  let k = 0;
  const say = async (n, body = `나쁜 말 ${++k}번째예요`) => {
    const r = await call("POST", "/api/posts", { device: device(n), company: c, setup: g, body }, from(n));
    assert.equal(r.status, 201, JSON.stringify(r.data));
    return r.data.id;
  };
  const flag = (id, n) => call("POST", `/api/posts/${id}/flag`, { device: device(n), reason: "abuse" }, from(n));
  const read = (n) => call("GET", `/api/companies/${c}/setups/${g}/posts`, undefined, mine(n));
  const keep = async (id) => {
    const r = await call("POST", `/api/admin/posts/${id}`, { action: "hide", reason: "abuse" }, await signIn(call));
    assert.equal(r.status, 200, JSON.stringify(r.data));
  };
  const care = async () => (await call("GET", "/api/admin/care", undefined, await signIn(call))).data;
  const rows = (ref) => env.DB.db.prepare("SELECT device, kind, ref, at, confirmed, hidden_by FROM write_strikes WHERE ref = ? ORDER BY id").all(ref);
  /** 고치기 전 코드가 남긴 줄: [ref]의 첫 줄을 그대로 하나 더(같은 사람·종류·글, [confirmed]). */
  const copy = (ref, confirmed, at) => {
    const r = rows(ref)[0];
    env.DB.db.prepare("INSERT INTO write_strikes (device, kind, ref, at, hidden_by, confirmed) VALUES (?, ?, ?, ?, ?, ?)").run(r.device, r.kind, r.ref, at, r.hidden_by, confirmed);
  };
  const reportHide = async (id) => { for (const n of [3, 4, 5]) await flag(id, n); };
  return { say, read, keep, care, rows, copy, reportHide };
}

async function policy312(call) {
  const r = await call("POST", "/api/admin/policy", { confirm: true, version: "3.12", posted: "2026-11-09", effective: "2026-11-09", heldUntil: "2026-12-09", why: "시험" }, await signIn(call));
  assert.equal(r.status, 200, JSON.stringify(r.data));
}

test("[RVQ-27] 같은 글의 확인 대기 줄이 여럿이어도 대기 수는 하나, 운영자가 두 번 확인해도 한 번만 세요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-20T03:00:00Z") });
  const { call, env } = server({ plaza: true });
  await policy312(call);
  const p = await plaza(call, env);
  const a = await p.say(2, "여기 사람들 다 바보예요");
  await p.reportHide(a);
  // 실서버 모양: 고치기 전 코드가 같은 글에 대기 줄을 둘 더 쌓았어요.
  p.copy(a, 0, Date.now() + 1000);
  p.copy(a, 0, Date.now() + 2000);
  assert.equal(p.rows(a).filter((r) => r.confirmed === 0).length, 3);
  assert.equal((await p.care()).unconfirmed.n, 1, "가려진 글 하나 = 확인 대기 1건");
  // 운영자가 그대로 가리기: 셋이 함께 확인돼요. 한 번 더 눌러도(같은 화면에서 두 번, 다른 관리자) 더 세지 않아요.
  await p.keep(a);
  await p.keep(a);
  assert.deepEqual(p.rows(a).map((r) => r.confirmed), [1, 1, 1], "줄은 지우지 않아요");
  assert.equal((await p.care()).unconfirmed.n, 0);
  // 1.36 셈(30일 두 번이면 첫 제한)에서 아직 하나예요. 다른 글 하나가 더 확인돼야 첫 제한이에요.
  assert.equal((await p.read(2)).data.limit, undefined);
  const b = await p.say(2, "또 바보 같은 소리네요");
  await p.reportHide(b);
  await p.keep(b);
  const limit = (await p.read(2)).data.limit;
  assert.deepEqual([limit.perDay, limit.n], [5, 2], "앱이 말하는 수도 가려진 것 둘");
  const list = (await call("GET", "/api/admin/write-limits", undefined, await signIn(call))).data;
  assert.equal(list.items.find((r) => r.now).strikes, 2, "관리자 앱 제한 줄의 수도 둘");
});

test("[RVQ-27] 이미 두 번 확인되어 두 줄이 센 실서버 모양도, 12-09 마을 돌봄 셈에서는 한 번이에요", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: Date.parse("2026-11-25T03:00:00Z") });
  const { call, env } = server({ plaza: true });
  await policy312(call);
  const p = await plaza(call, env);
  const a = await p.say(2, "여기 사람들 다 바보예요");
  await p.reportHide(a);
  // 고치기 전 코드: 대기 줄이 둘 쌓였고, 운영자가 두 번 확인해 둘 다 셌어요(confirmed 1이 둘).
  p.copy(a, 0, Date.now() + 1000);
  env.DB.db.prepare("UPDATE write_strikes SET confirmed = 1 WHERE ref = ?").run(a);
  assert.equal(p.rows(a).filter((r) => r.confirmed === 1).length, 2);
  // 고치기 전이면 이것만으로 1.36의 '30일 두 번'이 차서 제한이었어요. 이제는 하나예요.
  // (제한은 다음 벌점 때 셈이 돌아요: 같은 글을 운영자가 한 번 더 확인해도 새로 세지 않아요.)
  await p.keep(a);
  assert.equal((await p.read(2)).data.limit, undefined);
  // 12-09 00:00 KST(12-08 15:00Z) 마을 돌봄이 켜진 뒤: 셋째 기준(60일)으로 셈. 같은 글 둘 + 새 글 하나 = 가려진 것 둘이라 아직 제한 없음(안내·경고만).
  t.mock.timers.tick(Date.parse("2026-12-09T03:00:00Z") - Date.now());
  assert.equal((await p.care()).on, true);
  const b = await p.say(2, "또 바보 같은 소리네요");
  await p.reportHide(b);
  await p.keep(b);
  assert.equal((await p.read(2)).data.limit, undefined, "가려진 것 둘: 고치기 전 셈(셋)이면 여기서 첫 제한이었어요");
  const c = await p.say(2, "계속 바보 같아요");
  await p.reportHide(c);
  await p.keep(c);
  const limit = (await p.read(2)).data.limit;
  assert.equal(limit.n, 3, "가려진 것 셋째에서 첫 제한");
  // 관리자 앱 사람 찾기의 '가려진 수'도 셋.
  const person = await call("GET", `/api/admin/care/person?post=${a}`, undefined, await signIn(call));
  assert.equal(person.status, 200, JSON.stringify(person.data));
  assert.equal(person.data.strikes, 3);
});
