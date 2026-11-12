// 2.0.0 [RVR-22] (검토 SD-09, 스튜디오 제작 오류 점검 S7-2) 바깥 길 시험, 공유 서버 쪽: 앱 OutsideBodiesTest가 장터 작품을 하나씩 넣은
// 폰에서 만든 링크 만들기·자동 갱신 몸통(app/build/outside-bodies/link·refresh.jsonl)을 이 서버의 검사기(validate)가 모두 받아요.
// 받아도 보낸 사람이 고른 것(근무 종류·이름·글자·그림, 테마, 근무 설정, 회사 설정)이 빠지거나 잘리면 실패예요: 받는 사람에게는 말없이
// 사라지니까요(서버가 버리고 자르는 것은 앱 밖에서 온 몸통을 위한 것, 갈래 D SD-08).
// BAMNAT_NEED_APP_PAYLOADS=1(CI의 앱 일)이면 파일이 없을 때 건너뛰지 않고 실패해요(RVR-19와 같은 규칙).
import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { validate } from "../src/index.js";

const need = process.env.BAMNAT_NEED_APP_PAYLOADS === "1";
const rows = (file) => readFileSync(file, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l));

/** What [body] asked to show that [out] (validate's answer) lost, in words. */
export function lost(body, out) {
  const s = out.schedule;
  const missing = [];
  if ((s.types?.length ?? 0) !== (body.types?.length ?? 0)) missing.push(`근무 종류 ${body.types?.length ?? 0} → ${s.types?.length ?? 0}`);
  const icons = (body.types ?? []).filter((t) => t.icon != null).length;
  const kept = (s.types ?? []).filter((t) => t.icon != null).length;
  if (kept !== icons) missing.push(`근무 그림 ${icons} → ${kept}`);
  // The app sends names and labels already fitted (core ShiftRules.fitName·fitLabel): the server cuts none of them (갈래 D, SD-08).
  const byId = new Map((s.types ?? []).map((t) => [t.id, t]));
  for (const t of body.types ?? []) {
    const o = byId.get(t.id);
    if (!o) continue;
    if (o.name !== t.name) missing.push(`근무 이름 잘림 '${t.name}' → '${o.name}'`);
    if (t.short != null && o.short !== t.short) missing.push(`근무 글자 잘림 '${t.short}' → '${o.short}'`);
  }
  for (const [key, say] of [["look", "테마"], ["kit", "근무 설정"], ["setup", "회사 설정"]]) {
    if (body[key] != null && s[key] == null) missing.push(`${say} 빠짐`);
  }
  return missing;
}

for (const name of ["link", "refresh"]) {
  const file = new URL(`../../../app/build/outside-bodies/${name}.jsonl`, import.meta.url);
  test(`[RVR-22] SD-09 장터 작품을 넣은 폰의 ${name === "link" ? "링크 만들기" : "자동 갱신"} 몸통을 모두 받고 빠뜨리지 않아요`,
    { skip: !need && !existsSync(file) && "run the app's OutsideBodiesTest first" }, () => {
      assert.ok(existsSync(file), `${name}.jsonl is missing: the app's OutsideBodiesTest did not write it (did it run, or fail?)`);
      const all = rows(file);
      assert.ok(all.length > 0, "no bodies");
      const bad = [];
      for (const r of all) {
        const body = JSON.parse(r.body);
        const out = validate(body);
        if (out.error) bad.push(`${r.key} ${r.title}: 거절 ${out.error}`);
        else for (const m of lost(body, out)) bad.push(`${r.key} ${r.title}: ${m}`);
      }
      assert.deepEqual(bad.slice(0, 30), [], `${bad.length}개 작품`);
    });
}
