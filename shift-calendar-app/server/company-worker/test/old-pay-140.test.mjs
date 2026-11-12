// 1.40 [PAYP-19] 공유급여 결정 1 (사용자 2026-11-07 19:21): an app up to 1.38 that joined a company still read its versions' pay part
// for compatibility; from 1.40's release day (the shipped version the server keeps) it gets the shifts only, and a notice says the
// pay way is in the newest app. Deployed before that day, nothing changes. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, report, device, from, signIn } from "./helpers.mjs";
import { OLD_PAY } from "../src/old-pay.js";

const ship = (DB, v, at = Date.now()) => DB.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('shipped_version', ?)")
  .bind(JSON.stringify({ v, at, by: "test" })).run();

test("[PAYP-19] an old app's member reads the pay part until 1.40 is out, then the shifts only, with a notice for apps up to 1.38", async () => {
  const { call, DB } = server();
  const a = await call("POST", "/api/reports", report(1, { company: "끊기정밀" }), from(1));
  const c = a.data.company.id;
  const g = a.data.setup;
  // An app before 1.39 (no X-Pay-Private) joins.
  const old = { ...from(9), "x-device": device(9) };
  assert.equal((await call("POST", "/api/members", { device: device(9), company: c, cal: "app" }, old)).data.joined, true);
  const page = async () => (await call("GET", `/api/companies/${c}`, undefined, old)).data;
  const notice = async () => (await call("GET", "/api/config")).data.notices.find((n) => n.id === OLD_PAY.noticeId);

  // Deployed early: 1.39.0 is the version out, so nothing changes.
  await ship(DB, "1.39.0");
  assert.ok((await page()).setups[0].setup.pay, "an old app's member still gets the pay part before 1.40");
  assert.equal(await notice(), undefined);

  // 1.40 is out (the release loop saved it): the pay part goes to no app.
  await ship(DB, "1.40.0");
  const cut = await page();
  assert.equal(cut.setups[0].setup.pay, undefined);
  assert.equal(cut.setups[0].pay, true, "that a pay way is there still shows");
  assert.ok(!JSON.stringify(cut).includes("가산율"));
  const n = await notice();
  assert.equal(n.maxApp, 52);
  assert.equal(n.level, "important");
  assert.ok(n.body.includes("업데이트"));
  // The developer's notices stay their own: the built-in one is not kept with them.
  const h = await signIn(call);
  assert.ok(!(await call("GET", "/api/admin/notices", undefined, h)).data.notices.some((x) => x.id === OLD_PAY.noticeId));

  // The developer can hold it (끊지 않기) and turn it on again by hand.
  const off = await call("POST", "/api/admin/old-pay", { mode: "off" }, h);
  assert.equal(off.status, 200, off.text);
  assert.equal(off.data.on, false);
  assert.ok((await page()).setups[0].setup.pay);
  assert.equal(await notice(), undefined);
  assert.equal((await call("POST", "/api/admin/old-pay", { mode: "never" }, h)).status, 400);
  await call("POST", "/api/admin/old-pay", { mode: "auto" }, h);
  assert.equal((await call("GET", "/api/admin/old-pay", undefined, h)).data.on, true);
  assert.equal((await page()).setups[0].setup.pay, undefined);
});

test("[PAYP-19] before any version is saved and with 1.39 out, auto leaves old apps as they were; on cuts at once", async () => {
  const { call } = server();
  const a = await call("POST", "/api/reports", report(1, { company: "지금끊기" }), from(1));
  const c = a.data.company.id;
  const old = { ...from(8), "x-device": device(8) };
  await call("POST", "/api/members", { device: device(8), company: c, cal: "app" }, old);
  assert.ok((await call("GET", `/api/companies/${c}`, undefined, old)).data.setups[0].setup.pay);
  const h = await signIn(call);
  const on = await call("POST", "/api/admin/old-pay", { mode: "on" }, h);
  assert.deepEqual([on.data.on, on.data.mode], [true, "on"]);
  assert.equal((await call("GET", `/api/companies/${c}`, undefined, old)).data.setups[0].setup.pay, undefined);
  assert.ok((await call("GET", "/api/config")).data.notices.some((n) => n.id === OLD_PAY.noticeId));
});
