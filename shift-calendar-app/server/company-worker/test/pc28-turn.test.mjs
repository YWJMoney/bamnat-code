// 2.0.0 [PC-28] 달 넘김 날짜 문제: an app before 2.0.0 (and one that says no app=) reads a work's 스르륵·책장·움직이지 않기·움직임 끄기
// as 밀기; 2.0.0 reads it as made; nothing stored changes and a work with no such turn is served byte for byte. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import "../src/index.js";
import { cleanRecipe, turnSafeMotion, turnSafeOut, TURN_SAFE_APP } from "../src/recipes.js";
import { server, signIn, device, from } from "./helpers.mjs";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const recipe = (title, motion) => ({
  format: "bamnat-recipe", v: 1, title, summary: "달 넘김 시험", styles: ["SHIFT"],
  pieces: [
    { kind: "calTheme", today: 4289736794, sat: 4282664004, sun: 4293212469 },
    ...(motion === undefined ? [] : [{ kind: "appFeel", motion }]),
  ],
});
const motionOf = (body) => JSON.parse(body).pieces.find((p) => p.kind === "appFeel")?.motion;

test("[PC-28] the 움직임 an older app reads: only a risky 달 넘김 becomes 밀기, 움직임 끄기 keeps everything still but the month", () => {
  assert.equal(TURN_SAFE_APP, 54, "2.0.0's versionCode");
  assert.equal(turnSafeMotion("tn:fade|td:glow|sp:n"), "tn:slide|td:glow|sp:n");
  assert.equal(turnSafeMotion("tn:flip"), "tn:slide");
  assert.equal(turnSafeMotion("tn:none|td:bounce|sp:f"), "tn:slide|td:bounce|sp:f");
  assert.equal(turnSafeMotion("td:glow|tn:fade"), "td:glow|tn:slide", "where it was");
  assert.equal(turnSafeMotion("tn:fade|td:pulse|of:1"), "tn:slide|td:none|wr:none");
  assert.equal(turnSafeMotion("of:1|sp:s"), "tn:slide|td:none|wr:none|sp:s");
  for (const same of ["tn:slide|td:pulse|sp:f", "tn:zoom|td:bounce|sp:n", "tn:stack", "td:glow|sp:n", "wr:circle", "", undefined]) {
    assert.equal(turnSafeMotion(same), same);
  }
});

test("[PC-28] list and 작품 열기: before 2.0.0 or no app= reads 밀기, 2.0.0 the work as made; stored and other works unchanged", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const admin = await signIn(call);
  const put = async (title, motion) => {
    const r = await call("POST", "/api/admin/recipes", { recipe: recipe(title, motion), hold: false }, admin);
    assert.equal(r.status, 201, r.text);
    return r.data;
  };
  const fade = await put("스르륵 넘기는 달력", "tn:fade|td:glow|sp:n");
  const off = await put("움직임 끈 달력", "tn:zoom|of:1");
  const zoom = await put("다가오는 달력", "tn:zoom|td:bounce|sp:n");
  const plain = await put("움직임 없는 달력");
  const stored = Object.fromEntries((await call("GET", "/api/admin/recipes?tab=official", undefined, admin)).data.items.map((i) => [i.title, i]));
  assert.equal(motionOf(stored["스르륵 넘기는 달력"].body), "tn:fade|td:glow|sp:n");
  const list = async (q) => Object.fromEntries((await call("GET", `/api/recipes?sort=new&body=1${q}`)).data.items.map((i) => [i.title, i]));
  for (const q of ["", "&app=49", "&app=53"]) {
    const l = await list(q);
    assert.equal(motionOf(l["스르륵 넘기는 달력"].body), "tn:slide|td:glow|sp:n", q || "no app=");
    assert.equal(motionOf(l["움직임 끈 달력"].body), "tn:slide|td:none|wr:none", q || "no app=");
    // Served text and hash agree with the server's own check (and so with the app's hash of what it reads).
    for (const title of ["스르륵 넘기는 달력", "움직임 끈 달력"]) {
      const c = await cleanRecipe(l[title].body);
      assert.deepEqual([c.text, c.hash], [l[title].body, l[title].hash], title);
    }
    // A work with no risky turn: byte for byte, its stored hash.
    for (const [title, w] of [["다가오는 달력", zoom], ["움직임 없는 달력", plain]]) {
      assert.equal(l[title].body, stored[title].body);
      assert.equal(l[title].hash, w.hash);
    }
  }
  for (const q of [`&app=${TURN_SAFE_APP}`, "&app=60"]) {
    const l = await list(q);
    assert.equal(l["스르륵 넘기는 달력"].body, stored["스르륵 넘기는 달력"].body);
    assert.equal(l["스르륵 넘기는 달력"].hash, fade.hash);
    assert.equal(l["움직임 끈 달력"].hash, off.hash);
  }
  // 작품 열기: the 1.36~1.39 apps send no app= (with or without a phone), 2.0.0 sends its own.
  const open = async (code, q = "", headers = {}) => (await call("GET", `/api/recipes/${code}${q}`, undefined, headers)).data;
  for (const headers of [{}, { "x-device": device(7), ...from(7) }]) {
    const old = await open(fade.code, "", headers);
    assert.equal(motionOf(old.body), "tn:slide|td:glow|sp:n");
    const l = await list("&app=53");
    assert.equal(old.hash, l["스르륵 넘기는 달력"].hash, "the card and the work read whole agree (no '그 사이 새 판')");
    const now = await open(fade.code, `?app=${TURN_SAFE_APP}`, headers);
    assert.deepEqual([now.body, now.hash], [stored["스르륵 넘기는 달력"].body, fade.hash]);
    assert.equal((await open(zoom.code, "", headers)).hash, zoom.hash);
  }
  // 새 판 확인 tells the stored 판 as before (no new 판 is told of: a phone wearing it keeps it).
  const v = (await call("GET", `/api/recipes/versions?codes=${fade.code}`)).data.versions[fade.code];
  assert.deepEqual([v.version, v.hash], [1, fade.hash]);
  // An older 판 asked by number is served safe too. 2.0.0 [RVQ-26] 검토 SVC-23: someone else asking for a past 판 gets the one up now
  // (only its maker opens a past one), served safe all the same.
  const v2 = await call("POST", "/api/admin/recipes", { recipe: { ...recipe("스르륵 넘기는 달력", "tn:fade|td:pulse|sp:n"), summary: "둘째 판" }, hold: false, replace: true }, admin);
  assert.equal(v2.data.version, 2);
  assert.equal(motionOf((await open(fade.code, "?v=1")).body), "tn:slide|td:pulse|sp:n");
  assert.equal(motionOf((await open(fade.code, `?v=1&app=${TURN_SAFE_APP}`)).body), "tn:fade|td:pulse|sp:n");
});

test("[PC-28] the maker's own reads as made on any app, so a 새 판 made from it keeps its 움직임; someone else's is served safe", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  const made = await call("POST", "/api/recipes", { device: device(1), recipe: recipe("내 책장 달력", "tn:flip|td:ring|sp:s"), scope: "public" }, from(1));
  assert.equal(made.status, 201, made.text);
  const mine = (await call("GET", `/api/recipes/${made.data.code}`, undefined, { "x-device": device(1), ...from(1) })).data;
  assert.equal(mine.mine, true);
  assert.equal(motionOf(mine.body), "tn:flip|td:ring|sp:s");
  const theirs = (await call("GET", `/api/recipes/${made.data.code}`, undefined, { "x-device": device(2), ...from(2) })).data;
  assert.equal(theirs.mine, false);
  assert.equal(motionOf(theirs.body), "tn:slide|td:ring|sp:s");
});

test("[PC-28] every 장터 work made for 1.39: only the 25 with a risky turn change, and only in their 움직임", async () => {
  const file = fileURLToPath(new URL("../../../docs/recipes/market-139.json", import.meta.url));
  const works = JSON.parse(readFileSync(file, "utf8"));
  let changed = 0;
  for (const w of works) {
    const c = await cleanRecipe(w.recipe);
    const old = await turnSafeOut(c.text, c.hash, 53);
    const now = await turnSafeOut(c.text, c.hash, TURN_SAFE_APP);
    assert.deepEqual([now.body, now.hash], [c.text, c.hash], w.recipe.title);
    if (old.body === c.text) { assert.equal(old.hash, c.hash); continue; }
    changed++;
    const a = JSON.parse(c.text);
    const b = JSON.parse(old.body);
    const feel = (x) => x.pieces.findIndex((p) => p.kind === "appFeel");
    b.pieces[feel(b)].motion = a.pieces[feel(a)].motion;
    assert.deepEqual(b, a, `${w.recipe.title}: only its 움직임`);
    assert.match(JSON.parse(old.body).pieces[feel(a)].motion, /(^|\|)tn:slide(\||$)/);
    const again = await cleanRecipe(old.body);
    assert.deepEqual([again.text, again.hash], [old.body, old.hash]);
  }
  assert.equal(changed, 25);
});
