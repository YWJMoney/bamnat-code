// 2.0.0 [RVD-25] SN-17 (스튜디오 제작 오류 조사, 뿌리 R6): the 장터 took a 회사 근무 주기 작품 by its format alone, so one the company server
// refuses could be put in and then fail to share. Now the 장터 checks it with the company server's own function (company-setup.js).
// [RVD-26] SN-16: 근무 글자 is two letters as a person counts them, up to 16 code units, on the company server too. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { server, device, from, setup } from "./helpers.mjs";
import { cleanSetup } from "../src/index.js";
import { cleanSetup as sameCleanSetup } from "../src/company-setup.js";

const AFTER = Date.parse("2026-11-30T03:00:00Z");
const file = (types) => ({ ...setup(), shiftTypes: types ?? setup().shiftTypes });

test("[RVD-25] SN-17 the 장터 takes a 회사 근무 주기 작품 only when the company server would take its file", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: AFTER });
  const { call } = server({ plaza: true });
  assert.equal(cleanSetup, sameCleanSetup);
  let n = 0;
  const put = (f) => {
    n++;
    return call("POST", "/api/recipes", { device: device(n), scope: "public", recipe: { format: "bamnat-recipe", v: 1, title: `4조 2교대 주기 ${n}`,
      summary: "", styles: ["SHIFT"], pieces: [{ kind: "company", setup: JSON.stringify(f) }] } }, from(n));
  };
  assert.equal((await put(file())).status, 201);
  const [day, ...rest] = setup().shiftTypes;
  const refused = [
    file([...Array.from({ length: 23 }, (_, i) => ({ ...day, name: `근무${i}` })), day, ...rest]), // 근무 종류 24개까지
    file([{ ...day, name: "가".repeat(41) }, ...rest]), // 이름 40자
    file([day, { ...day }, ...rest]), // 이름 겹침
    file([{ ...day, break: 721 }, ...rest]), // 쉬는 시간 720분
    { ...setup(), v: 9 }, // 서버가 모르는 판
    { ...setup(), format: "other" },
  ];
  for (const f of refused) {
    const r = await put(f);
    assert.equal(r.status, 400, JSON.stringify(f.shiftTypes?.[0]));
    assert.equal(r.data.error, "setup");
  }
  // [RVD-28] SD-08 What the company server cuts or drops (a long 근무 글자, an unknown 그림) does not keep a 작품 out, nor a name of 20 letters.
  for (const f of [file([{ ...day, label: "가나다라마바사아자" }, ...rest]), file([{ ...day, icon: "<svg>" }, ...rest]),
    JSON.parse(JSON.stringify(setup()).replaceAll("주간", "👩‍⚕️".repeat(9)))]) assert.equal((await put(f)).status, 201, JSON.stringify(f.shiftTypes[0]));
});

test("[RVD-26] SN-16 근무 글자: two letters up to 16 code units (👩‍⚕️ is one), and whatever 8 code units took before; [RVD-28] longer is cut", () => {
  const label = (l) => {
    try {
      const [day, ...rest] = setup().shiftTypes;
      return cleanSetup(file([{ ...day, label: l }, ...rest])).shiftTypes[0].label;
    } catch (e) {
      return e.code;
    }
  };
  for (const ok of ["주", "주간", "👩‍⚕️👍🏿", "🇰🇷🌙", "1️⃣2️⃣", "🧑🏻‍❤️‍💋‍🧑🏼", "DAY1", "주간근무", "가나다라마바사아", ""]) assert.equal(label(ok), ok, ok);
  // [RVD-28] SD-08 (총괄 '버리기, 거절 아님'): cut at a letter, as core ShiftRules.fitLabel, never refused.
  for (const [given, kept] of [["가나다라마바사아자", "가나"], ["👨‍👩‍👧‍👦👨‍👩‍👧‍👦", "👨‍👩‍👧‍👦"], ["👩‍⚕️👍🏿🌙", "👩‍⚕️👍🏿"]]) assert.equal(label(given), kept, given);
});
