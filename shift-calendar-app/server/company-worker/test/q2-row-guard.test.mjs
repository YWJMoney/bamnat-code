// 2.0.0 [RVA-37] 검토 I24b-02 독이 든 한 줄 격리 · I24c-01 '무엇 때문에 멈췄나': 문의 한 줄을 읽지 못해도 운영 장부·정리함·오늘·문의 목록은
// 계속 돌고, 그 줄은 오류 묶음에 원인 줄로 남아 오늘 화면이 원인·우회·넘기기를 한 장으로 보여요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";

const APP = { "x-admin": "1", origin: ORIGIN };
const NONE = { review: "none", inquiries: "none", dev: "none", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };

async function member(call, owner, perms, email, ip) {
  const inv = await call("POST", "/api/admin/invites", { email, perms: { ...NONE, ...perms } }, owner);
  const token = inv.data.link.split("#")[1];
  assert.equal((await call("POST", "/api/admin/join", { token, name: "민지", password: "minji-answers-2026" }, { ...APP, ...from(ip) })).status, 201);
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: "minji-answers-2026" }, { ...APP, ...from(ip) });
  return { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
}

const ask = (call, n, text, kind = "bug") =>
  call("POST", "/api/inquiries", { v: 1, device: device(n), kind, text, info: { app: "1.39.0 (53)", model: "samsung SM-S921N" } }).then((r) => r.data.id);

test("[RVA-37] 못 읽는 문의 한 줄은 그 줄만 빠지고 장부·정리함·오늘·문의 목록은 그대로, 오류 묶음에 원인 줄, [개발사항으로] 한 번", async () => {
  const { call, DB } = server();
  const admin = await signIn(call);
  const good = await ask(call, 1, "잠금 화면에서 알람 소리가 안 나요");
  const bad = await ask(call, 2, "달력 글씨가 너무 작아요", "idea");
  // 반쯤 쓰다 만 기기 정보(옛 판·끊긴 쓰기): 예전에는 이 한 줄 때문에 운영 장부·정리함이 통째로 500이었어요(ADMX-01과 같은 뿌리).
  DB.db.prepare("UPDATE inquiries SET info = ? WHERE id = ?").run('{"app":"1.39', bad);

  const ledger = await call("GET", "/api/admin/ledger?view=open", undefined, admin);
  assert.equal(ledger.status, 200, "the ledger still answers");
  const rows = ledger.data.items;
  assert.ok(rows.some((r) => r.id === good && !r.unreadable), "the other lines are read as before");
  const stub = rows.find((r) => r.id === bad);
  assert.ok(stub, "the line that could not be read stays on the list");
  assert.equal(stub.title, "(읽지 못한 문의)");
  assert.match(stub.unreadable, /JSON|Unterminated|Unexpected|position/i);
  assert.match(stub.no ?? "", /^K-\d{4}$/, "it keeps its K number");

  const sort = await call("GET", "/api/admin/inquiries/sort", undefined, admin);
  assert.equal(sort.status, 200, "정리함 still answers");
  const sorted = Object.values(sort.data.piles).flat().flatMap((x) => [x.id, ...(x.group ?? [])]);
  assert.ok(sorted.includes(good) && !sorted.includes(bad), "only the unreadable one is left out of 정리함 (no auto answer for it)");

  const list = await call("GET", "/api/admin/inquiries?status=open", undefined, admin);
  assert.equal(list.status, 200, "the inquiry list still answers");
  const one = await call("GET", `/api/admin/inquiries/${bad}`, undefined, admin);
  assert.equal(one.status, 200, "and the line itself opens");

  const groups = DB.db.prepare("SELECT sig, route, message, total FROM error_groups WHERE route LIKE 'row/%'").all();
  assert.ok(groups.some((g) => g.route === `row/ledger/inquiry:${bad}`), JSON.stringify(groups));
  assert.ok(groups.some((g) => g.route === `row/sort/inquiry:${bad}`), "정리함 writes its own line");
  // Read again: the same line and the same error stay one group.
  await call("GET", "/api/admin/ledger?view=open", undefined, admin);
  const again = DB.db.prepare("SELECT total FROM error_groups WHERE route = ?").get(`row/ledger/inquiry:${bad}`);
  assert.ok(again.total >= 2);

  const today = await call("GET", "/api/admin/today", undefined, admin);
  assert.equal(today.status, 200);
  const stuck = today.data.stuck.find((s) => s.where === "ledger");
  assert.equal(stuck.type, "inquiry");
  assert.equal(stuck.id, bad);
  assert.equal(stuck.whereName, "운영 장부");

  const made = await call("POST", `/api/admin/watch/errors/${stuck.sig}/dev`, {}, admin);
  assert.equal(made.status, 201);
  assert.match(made.data.dev, /^d[A-Za-z0-9]{15}$/);
  const dev = await call("GET", `/api/admin/dev/${made.data.dev}`, undefined, admin);
  assert.match(dev.data.title, /^읽지 못한 줄\(운영 장부\): /);
  // 같은 줄을 정리함에서 읽다 난 묶음도 같은 개발사항으로 묶여요.
  assert.ok(DB.db.prepare("SELECT note FROM error_groups WHERE route = ?").get(`row/sort/inquiry:${bad}`).note.startsWith(`개발사항 ${made.data.dev}`));
  assert.match(dev.data.body, new RegExp(`원인 줄 inquiry:${bad}`));
  assert.equal((await call("POST", `/api/admin/watch/errors/${stuck.sig}/dev`, {}, admin)).status, 409, "once");
  const after = (await call("GET", "/api/admin/today", undefined, admin)).data.stuck.find((s) => s.sig === stuck.sig);
  assert.equal(after.status, "seen");
  assert.match(after.note, new RegExp(`^개발사항 ${made.data.dev}`));
});

test("[RVA-37] 읽지 못한 줄은 그 줄을 볼 권한이 있는 관리자에게만, [개발사항으로]는 최고 관리자만", async () => {
  const { call, DB } = server();
  const owner = await signIn(call);
  const bad = await ask(call, 3, "알람이 늦게 울려요");
  DB.db.prepare("UPDATE inquiries SET info = ? WHERE id = ?").run("not json", bad);
  assert.equal((await call("GET", "/api/admin/ledger", undefined, owner)).status, 200);
  const sig = DB.db.prepare("SELECT sig FROM error_groups WHERE route = ?").get(`row/ledger/inquiry:${bad}`).sig;
  const support = await member(call, owner, { inquiries: "edit" }, "minji@example.com", 41);
  const reviewer = await member(call, owner, { review: "edit" }, "junho@example.com", 42);
  assert.ok((await call("GET", "/api/admin/today", undefined, support)).data.stuck.some((s) => s.sig === sig), "문의 권한이면 보여요");
  assert.ok(!((await call("GET", "/api/admin/today", undefined, reviewer)).data.stuck ?? []).length, "검토만 하는 관리자에게는 안 보여요");
  assert.equal((await call("POST", `/api/admin/watch/errors/${sig}/dev`, {}, support)).status, 403, "개발사항으로는 최고 관리자 길");
  assert.equal((await call("POST", `/api/admin/watch/errors/${sig}/dev`, {}, APP)).status, 401, "로그인 없이는 안 돼요");
});
