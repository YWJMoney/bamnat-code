// 2.0.0 [RVR-22] (검토 SD-09, 스튜디오 제작 오류 점검 S7-2) 바깥 길 시험, 회사 서버 쪽: 앱 OutsideBodiesTest가 장터 작품을 하나씩 넣은
// 폰에서 만든 몸통(app/build/outside-bodies/*.jsonl)을 이 서버의 검사기가 모두 받아요.
// - company: 회사 방식 공유(광장·회사 근무표)의 setup → cleanSetup, 글은 personal(연락처·이름) 검사까지(index.js 회사 글과 같이).
// - market: 넣은 작품을 내 판으로 다시 올리기, look: 스튜디오 › 내 테마 올리기 → cleanRecipe(글 검사·이름 사칭 검사 포함).
// BAMNAT_NEED_APP_PAYLOADS=1(CI의 앱 일)이면 파일이 없을 때 건너뛰지 않고 실패해요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
// index.js first: recipes.js → photo-guard.js → index.js is a cycle that only loads cleanly from this end.
import { personal } from "../src/index.js";
import { cleanRecipe } from "../src/recipes.js";
import { cleanSetup } from "../src/company-setup.js";

const need = process.env.BAMNAT_NEED_APP_PAYLOADS === "1";
const rows = (file) => readFileSync(file, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l));
const fileOf = (name) => new URL(`../../../app/build/outside-bodies/${name}.jsonl`, import.meta.url);
const skip = (name) => !need && !existsSync(fileOf(name)) && "run the app's OutsideBodiesTest first";
const why = (e) => `${e.code ?? e.message}${e.field ? ` (${e.field})` : ""}${e.path ? ` (${e.path})` : ""}`;

test("[RVR-22] SD-09 장터 작품을 넣은 폰의 회사 방식 공유 몸통을 cleanSetup이 모두 받아요", { skip: skip("company") }, () => {
  assert.ok(existsSync(fileOf("company")), "company.jsonl is missing: the app's OutsideBodiesTest did not write it");
  const bad = [];
  for (const r of rows(fileOf("company"))) {
    try {
      const texts = [];
      cleanSetup(JSON.parse(r.body), texts);
      for (const [field, t] of texts) {
        const p = personal(t);
        if (p) bad.push(`${r.key} ${r.title}: ${field} 글 ${p}`);
      }
    } catch (e) {
      bad.push(`${r.key} ${r.title}: 거절 ${why(e)}`);
    }
  }
  assert.deepEqual(bad.slice(0, 30), [], `${bad.length}개 작품`);
});

for (const [name, say] of [["market", "내 판으로 다시 올리기"], ["look", "내 테마 올리기"]]) {
  test(`[RVR-22] SD-09 장터 작품을 넣은 폰에서 ${say} 몸통을 cleanRecipe가 모두 받아요`, { skip: skip(name) }, async () => {
    assert.ok(existsSync(fileOf(name)), `${name}.jsonl is missing: the app's OutsideBodiesTest did not write it`);
    const all = rows(fileOf(name));
    assert.ok(all.length > 0, "no bodies");
    const bad = [];
    for (const r of all) {
      try {
        await cleanRecipe(r.body);
      } catch (e) {
        bad.push(`${r.key} ${r.title}: 거절 ${why(e)}`);
      }
    }
    assert.deepEqual(bad.slice(0, 30), [], `${bad.length}개 작품`);
  });
}
