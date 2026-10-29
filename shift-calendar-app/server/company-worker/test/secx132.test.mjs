// S 1.32 [SECX-01] (사용자 2026-10-28: "어플에서는 해당 기능들이 노출되면 안되고 조작도 되어서는 안돼 관리자 어플에서 권한에 맞게만"):
// 휴대폰 미리 보기 moved from the phone's 알람 점검 to the developer app, by role. Phones and admins are made up.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, signIn, ORIGIN } from "./helpers.mjs";

const APP = { "x-admin": "1", origin: ORIGIN };
const phone = (n) => ({ "x-device": device(n) });
const NONE = { review: "none", inquiries: "none", dev: "none", devStatus: "none", companies: "none", rewards: "none", settings: "none",
  community: "none", takedown: "none", ops: "none", metrics: "none", shares: "none", audit: "none", flags: "none", flagOff: "none" };

async function member(call, owner, perms, email, name, ip) {
  const inv = await call("POST", "/api/admin/invites", { email, perms: { ...NONE, ...perms } }, owner);
  assert.equal(inv.status, 201, inv.text);
  const token = inv.data.link.split("#")[1];
  assert.equal((await call("POST", "/api/admin/join", { token, name, password: `${name}-preview-2026` }, { ...APP, ...from(ip) })).status, 201);
  const id = (await call("GET", "/api/admin/admins", undefined, owner)).data.admins.find((a) => a.email === email).id;
  await call("POST", `/api/admin/admins/${id}`, { status: "active" }, owner);
  const res = await call("POST", "/api/admin/login", { email, password: `${name}-preview-2026` }, { ...APP, ...from(ip) });
  assert.equal(res.status, 200, res.text);
  return { id, headers: { cookie: res.headers.get("set-cookie").split(";")[0], ...APP } };
}

const link = async (call, who, n) => {
  const code = (await call("POST", "/api/admin/me/link-code", {}, who)).data.code;
  return (await call("POST", "/api/admin-link", { code }, { ...phone(n), ...from(n) })).data;
};

test("[SECX-01] previews are set in the developer app and reach only that approved phone", async () => {
  const { call } = server();
  const owner = await signIn(call);
  const flagger = await member(call, owner, { flags: "edit" }, "flag@example.com", "지수", 41);
  const viewer = await member(call, owner, { flags: "view" }, "view@example.com", "민호", 42);
  await link(call, owner, 1);
  await link(call, flagger.headers, 2);
  await link(call, viewer.headers, 3);

  // A plain phone has no admin, so nothing about flags.
  assert.equal((await call("GET", "/api/passes", undefined, phone(9))).data.admin, null);
  // The owner sees every phone; others only their own.
  const all = (await call("GET", "/api/admin/me/phones", undefined, owner)).data;
  assert.equal(all.phones.length, 3);
  assert.ok(all.flags.some((f) => f.key === "type.invest" && f.name));
  const mine = (await call("GET", "/api/admin/me/phones", undefined, flagger.headers)).data;
  assert.deepEqual(mine.phones.map((p) => p.adminName), ["지수"]);
  assert.equal(mine.edit, true);
  assert.equal((await call("GET", "/api/admin/me/phones", undefined, viewer.headers)).data.edit, false);

  const ownerPhone = all.phones.find((p) => p.adminName === "최고 관리자");
  const flaggerPhone = mine.phones[0];
  const viewerPhone = all.phones.find((p) => p.adminName === "민호");
  // A pending phone keeps its setting but gets none until approved.
  const set = await call("POST", `/api/admin/devices/${flaggerPhone.id}/previews`, { keys: ["type.invest"] }, flagger.headers);
  assert.equal(set.status, 200, set.text);
  assert.deepEqual((await call("GET", "/api/passes", undefined, phone(2))).data.admin.previews, []);
  await call("POST", `/api/admin/devices/${flaggerPhone.id}/approve`, {}, owner);
  assert.deepEqual((await call("GET", "/api/passes", undefined, phone(2))).data.admin.previews, ["type.invest"]);

  // Without 기능 관리 바꾸기, or on someone else's phone: refused.
  assert.equal((await call("POST", `/api/admin/devices/${viewerPhone.id}/previews`, { keys: ["ads"] }, viewer.headers)).status, 403);
  assert.equal((await call("POST", `/api/admin/devices/${ownerPhone.id}/previews`, { keys: ["ads"] }, flagger.headers)).status, 403);
  // Unknown keys are refused; the owner may set any phone.
  assert.equal((await call("POST", `/api/admin/devices/${ownerPhone.id}/previews`, { keys: ["nope.flag"] }, owner)).status, 400);
  assert.equal((await call("POST", `/api/admin/devices/${viewerPhone.id}/previews`, { keys: ["ads", "ads"] }, owner)).status, 200);
  const after = (await call("GET", "/api/admin/me/phones", undefined, owner)).data.phones.find((p) => p.id === viewerPhone.id);
  assert.deepEqual(after.previews, ["ads"]);
  // The owner's phone was approved at once; an empty list clears.
  await call("POST", `/api/admin/devices/${ownerPhone.id}/previews`, { keys: ["billing"] }, owner);
  assert.deepEqual((await call("GET", "/api/passes", undefined, phone(1))).data.admin.previews, ["billing"]);
  await call("POST", `/api/admin/devices/${ownerPhone.id}/previews`, { keys: [] }, owner);
  assert.deepEqual((await call("GET", "/api/passes", undefined, phone(1))).data.admin.previews, []);
});
