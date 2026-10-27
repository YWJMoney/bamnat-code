// AT 1.29 [ADMCP-01]~[ADMCP-07] 문의 복사 on the server: the list's new filters and fields (종류, 복사 전, 같은 기기, 복사함,
// 복사 전 문제 신고), a question read whole for copying, 복사함 marked with the steps chosen beside it (개발사항, 맡기, 닫기),
// who may do which, and the index the counts read.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";

const APP = { "x-admin": "1", origin: ORIGIN };
const NONE = { review: "none", inquiries: "none", dev: "none", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none" };

async function member(call, owner, perms, email, name, ip) {
  const inv = await call("POST", "/api/admin/invites", { email, perms: { ...NONE, ...perms } }, owner);
  assert.equal(inv.status, 201, inv.text);
  const token = inv.data.link.split("#")[1];
  assert.equal((await call("POST", "/api/admin/join", { token, name, password: `${name}-copies-2026` }, { ...APP, ...from(ip) })).status, 201);
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: `${name}-copies-2026` }, { ...APP, ...from(ip) });
  assert.equal(res.status, 200, res.text);
  return { cookie: res.headers.get("set-cookie").split(";")[0], ...APP };
}

/** Three questions: two problem reports (one from the same phone as the question) and a question. */
async function world() {
  const s = server();
  const owner = await signIn(s.call);
  const ask = async (n, dev, kind, text, info = {}) => (await s.call("POST", "/api/inquiries", { v: 1, device: device(dev), kind, text, info: { app: "1.28.0 (39)", ...info } }, from(n))).data;
  const bug = await ask(1, 1, "bug", "알람 소리가 안 나요\n아침 6시 알람이 조용해요", { model: "SM-S918N", android: "14", diag: "알람 6:00 켜짐\n방해 금지 꺼짐\n소리 0" });
  const same = await ask(2, 2, "bug", "알람 소리가 안 나요 진짜로", { app: "1.27.0 (38)" });
  const q = await ask(3, 1, "question", "급여는 어디서 봐요");
  return { ...s, owner, bug, same, q };
}

test("[ADMCP-02][ADMCP-04][ADMCP-06] the list filters by kind, 복사 전 and one phone, finds an app version or an id, and counts new problem reports", async () => {
  const { call, owner, bug, same, q } = await world();
  const list = async (query) => (await call("GET", `/api/admin/inquiries?status=all${query}`, undefined, owner)).data;
  const all = await list("");
  assert.equal(all.total, 3);
  for (const x of all.inquiries) {
    assert.equal(x.copied, null, "nobody copied it yet");
    assert.match(x.sender, /^[0-9a-f]{6}$/);
  }
  assert.deepEqual(all.fresh, { bugs: 2, today: 2 });
  assert.deepEqual((await list("&kind=bug")).inquiries.map((x) => x.id).sort(), [bug.id, same.id].sort());
  assert.equal((await list("&kind=bug")).kind, "bug");
  assert.equal((await list("&kind=nope")).total, 3, "a kind it does not know is no filter");
  const mine = all.inquiries.find((x) => x.id === bug.id).sender;
  assert.equal(all.inquiries.find((x) => x.id === q.id).sender, mine, "the same phone, the same mark");
  assert.notEqual(all.inquiries.find((x) => x.id === same.id).sender, mine);
  assert.deepEqual((await list(`&sender=${mine}`)).inquiries.map((x) => x.id).sort(), [bug.id, q.id].sort());
  assert.equal((await list("&sender=ZZ")).total, 3, "only six small hex letters are a phone's mark");
  // [ADMCP-05] 문의 찾기 also reads what came with it (app version, phone) and a question's id.
  assert.deepEqual((await list("&q=1.27.0")).inquiries.map((x) => x.id), [same.id]);
  assert.deepEqual((await list("&q=SM-S918N")).inquiries.map((x) => x.id), [bug.id]);
  assert.deepEqual((await list(`&q=${q.id}`)).inquiries.map((x) => x.id), [q.id]);
  assert.equal((await list("&copied=no")).total, 3);
  assert.equal((await list("&copied=yes")).total, 0);
});

test("[ADMCP-01] a question is read whole for copying, in the order asked; seeing questions is enough", async () => {
  const { call, owner, bug, same, q } = await world();
  await call("POST", `/api/admin/inquiries/${bug.id}/notes`, { text: "재현돼요" }, owner);
  await call("POST", `/api/admin/inquiries/${bug.id}/reply`, { text: "확인하고 있어요" }, owner);
  const viewer = await member(call, owner, { inquiries: "view" }, "view@example.com", "보미", 41);
  const r = await call("GET", `/api/admin/inquiries/export?ids=${q.id},${bug.id}`, undefined, viewer);
  assert.equal(r.status, 200, r.text);
  assert.deepEqual(r.data.inquiries.map((x) => x.id), [q.id, bug.id]);
  assert.equal(r.data.missing, 0);
  const b = r.data.inquiries[1];
  assert.deepEqual(b.messages.map((m) => m.from), ["user", "admin"]);
  assert.equal(b.context["기기"], "SM-S918N");
  assert.equal(b.context["알람 점검"], "알람 6:00 켜짐\n방해 금지 꺼짐\n소리 0");
  assert.deepEqual(b.notes.map((n) => n.text), ["재현돼요"]);
  assert.equal(b.copied, null);
  assert.equal(b.sameSender, 1, "the question from the same phone");
  assert.ok(!("templates" in b) && !("typing" in b), "only what a copy needs");
  // A question gone since the list was read is left out and counted.
  const gone = `${"x".repeat(22)}`;
  assert.equal((await call("GET", `/api/admin/inquiries/export?ids=${same.id},${gone}`, undefined, owner)).data.missing, 1);
  for (const ids of ["", "a,b", Array.from({ length: 101 }, (_, i) => `${String(i).padStart(22, "0")}`).join(",")]) {
    assert.equal((await call("GET", `/api/admin/inquiries/export?ids=${ids}`, undefined, owner)).data.error, "ids", ids.slice(0, 10));
  }
  const nobody = await member(call, owner, { dev: "view" }, "dev@example.com", "도윤", 42);
  assert.equal((await call("GET", `/api/admin/inquiries/export?ids=${q.id}`, undefined, nobody)).status, 403);
  // 복사함 changes questions: seeing is not enough for it.
  assert.equal((await call("POST", "/api/admin/inquiries/copied", { ids: [q.id] }, viewer)).status, 403);
});

test("[ADMCP-03] 복사함 marks who copied and when, in the list, the question, the badges and the activity log", async () => {
  const { call, owner, bug, same, q } = await world();
  assert.equal((await call("GET", "/api/admin/badges", undefined, owner)).data.bugsUncopied, 2);
  const r = await call("POST", "/api/admin/inquiries/copied", { ids: [bug.id, same.id] }, owner);
  assert.equal(r.status, 200, r.text);
  assert.equal(r.data.marked, 2);
  assert.equal(r.data.copied.by, "최고 관리자");
  assert.equal(r.data.item, null);
  assert.equal(r.data.status, null);
  const one = (await call("GET", `/api/admin/inquiries/${bug.id}`, undefined, owner)).data;
  assert.deepEqual(one.copied, r.data.copied);
  assert.equal(one.status, "open", "copying alone changes nothing else");
  const list = (await call("GET", "/api/admin/inquiries?status=all", undefined, owner)).data;
  assert.deepEqual(list.fresh, { bugs: 0, today: 0 });
  assert.deepEqual(list.inquiries.filter((x) => x.copied).map((x) => x.id).sort(), [bug.id, same.id].sort());
  assert.deepEqual((await call("GET", "/api/admin/inquiries?status=all&copied=no", undefined, owner)).data.inquiries.map((x) => x.id), [q.id]);
  assert.equal((await call("GET", "/api/admin/badges", undefined, owner)).data.bugsUncopied, 0, "the kept badges are fresh after a copy");
  const line = (await call("GET", "/api/admin/audit", undefined, owner)).data.entries.find((e) => e.action === "inquiry_copy");
  assert.ok(line, "one line in the activity log");
  assert.match(JSON.stringify(line), /2건/);
  // Copied again later: the newer copy is the one shown.
  const again = (await call("POST", "/api/admin/inquiries/copied", { ids: [bug.id, "y".repeat(22)] }, owner)).data;
  assert.deepEqual([again.marked, again.skipped], [1, 1]);
  assert.equal((await call("POST", "/api/admin/inquiries/copied", { ids: ["z".repeat(22)] }, owner)).status, 404);
  assert.equal((await call("POST", "/api/admin/inquiries/copied", { ids: [bug.id], status: "lost" }, owner)).data.error, "status");
});

test("[ADMCP-03] in the same step: a 개발사항 made from them, the free ones taken and all closed; the 개발사항 needs its own permission", async () => {
  const { call, owner, bug, same, q } = await world();
  const support = await member(call, owner, { inquiries: "edit" }, "support@example.com", "민지", 43);
  const helper = await member(call, owner, { inquiries: "edit", dev: "edit" }, "helper@example.com", "준호", 44);
  // 민지 holds one already; 준호 copies all three.
  await call("POST", `/api/admin/inquiries/${q.id}/assign`, { to: "me" }, support);
  assert.equal((await call("POST", "/api/admin/inquiries/copied", { ids: [bug.id], dev: { title: "알람 소리" } }, support)).status, 403, "no 개발사항 고치기");
  const r = await call("POST", "/api/admin/inquiries/copied",
    { ids: [bug.id, same.id, q.id], dev: { title: "알람 소리가 안 나요 외 2건", body: "# 문의 3건" }, assign: true, status: "closed" }, helper);
  assert.equal(r.status, 200, r.text);
  assert.deepEqual([r.data.marked, r.data.linked, r.data.assigned, r.data.status], [3, 3, 2, "closed"]);
  assert.equal(r.data.copied.by, "준호");
  const item = (await call("GET", `/api/admin/dev/${r.data.item.id}`, undefined, owner)).data;
  assert.equal(item.title, "알람 소리가 안 나요 외 2건");
  assert.equal(item.body, "# 문의 3건");
  assert.equal(item.inquiries.length, 3);
  const views = await Promise.all([bug, same, q].map(async (x) => (await call("GET", `/api/admin/inquiries/${x.id}`, undefined, owner)).data));
  assert.deepEqual(views.map((v) => v.status), ["closed", "closed", "closed"]);
  assert.deepEqual(views.map((v) => v.assignee?.name), ["준호", "준호", "민지"], "copying never takes a question someone holds");
  assert.ok(views.every((v) => v.dev.some((d) => d.id === r.data.item.id)));
  // Joining one that exists instead.
  const other = (await call("POST", "/api/admin/dev", { title: "알람 묶음" }, helper)).data;
  const j = (await call("POST", "/api/admin/inquiries/copied", { ids: [bug.id], item: other.id }, helper)).data;
  assert.deepEqual([j.item.id, j.linked], [other.id, 1]);
  assert.equal((await call("POST", "/api/admin/inquiries/copied", { ids: [bug.id], item: "d000000000000000" }, helper)).status, 404);
  assert.equal((await call("POST", "/api/admin/inquiries/copied", { ids: [bug.id], dev: { title: "" } }, helper)).data.error, "title");
});

test("[ADMCP-04] the problem reports nobody copied are counted from their own index", async () => {
  const { call, DB } = server();
  await signIn(call);
  const names = DB.db.prepare("SELECT name FROM sqlite_master WHERE type = 'index'").all().map((r) => r.name);
  assert.ok(names.includes("inquiries_uncopied"));
  const plan = DB.db.prepare("EXPLAIN QUERY PLAN SELECT COUNT(*) AS n FROM inquiries WHERE kind = 'bug' AND copied_at IS NULL AND status != 'closed'").all()
    .map((r) => r.detail).join(" | ");
  assert.match(plan, /inquiries_uncopied/);
});
