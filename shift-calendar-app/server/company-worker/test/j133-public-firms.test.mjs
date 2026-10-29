// 1.33 [J133-88] 공개 자료: the 국민연금 가입 사업장 file a company name is looked up in, read from GitHub Pages. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { publicFirms, FIRMS } from "../src/index.js";

function pages(t, files) {
  const real = globalThis.fetch;
  const asked = [];
  globalThis.fetch = async (url) => {
    const u = String(url);
    asked.push(u);
    const path = u.slice(FIRMS.base.length + 1);
    return path in files ? new Response(files[path]) : new Response("not found", { status: 404 });
  };
  t.after(() => { globalThis.fetch = real; });
  return asked;
}

const meta = JSON.stringify({ asOf: "2026-09", source: "국민연금공단 국민연금 가입 사업장 내역", url: "https://www.data.go.kr/data/15083277/fileData.do", minMembers: 5 });
const files = {
  "meta.json": meta,
  "industries.tsv": "241000\t1차 철강 제조업\n341001\t자동차 제조업\n",
  // 현 = U+D604, larger first as the tool writes it.
  "d604.tsv": "현대자동차(주)\t서울 서초구\t341001\t66776\t633\n현대제철(주)\t인천 동구\t241000\t11000\t610\n현대제철정비(주)\t충남 당진시\t241000\t40\t390\n",
};
const ask = (q) => publicFirms({}, new URL(`https://x/api/public/firms?q=${encodeURIComponent(q)}`)).then((r) => r.json());

test("[J133-88] a name finds its workplace in the record, the very name first", async (t) => {
  const asked = pages(t, files);
  const r = await ask("(주)현대 제철");
  assert.equal(r.asOf, "2026-09");
  assert.deepEqual(r.firms.map((f) => f.name), ["현대제철(주)", "현대제철정비(주)"]);
  assert.deepEqual(r.firms[0], { name: "현대제철(주)", region: "인천 동구", industry: "1차 철강 제조업", members: 11000, average: 6_100_000 });
  assert.ok(asked.some((u) => u.endsWith("/d604.tsv")));
});

test("[J133-88] too short, no file for the letter, or no record at all", async (t) => {
  pages(t, files);
  assert.deepEqual((await ask("현")).firms, []);
  assert.deepEqual((await ask("삼성전자")).firms, []);
  globalThis.fetch = async () => new Response("", { status: 404 });
  const none = await ask("현대제철");
  assert.equal(none.unavailable, true);
});

test("[J133-89] a public institution's own pay rules come with its record", async (t) => {
  const setup = JSON.stringify({ app: "com.bamnat.app", format: "bamnat-company", v: 4, company: "충남대학교병원", shiftTypes: [],
    pay: { fiveOrMore: true, monthlyHours: 209, holidayMode: "FULL", withAmounts: false, items: [] } });
  pages(t, { ...files, "cda9.tsv": "충남대학교병원\t대전 중구\t861000\t3000\t520\n",
    "rules.json": JSON.stringify({ source: "공공기관 보수규정", orgs: [{ name: "충남대학교병원", key: "충남대학교병원", rule: "보수규정",
      effective: "2026-10-15", url: "https://www.alio.go.kr/x", hours: 209, lines: [{ item: "위험근무수당", calc: "전 임직원 매월", amount: "월 70,000원", note: "" }],
      takes: ["위험근무수당"], setup }, { name: "코레일테크(주)", key: "코레일테크", rule: "보수규정", effective: "2026-08-20", url: "https://www.alio.go.kr/y",
      hours: 209, lines: [], takes: [], setup }, { name: "한국철도공사", key: "한국철도공사", aliases: ["코레일", "korail"], rule: "보수규정",
      effective: "2026-07-27", url: "https://www.alio.go.kr/z", hours: 209, lines: [], takes: [], setup }] }) });
  const full = await ask("충남대학교병원");
  assert.equal(full.firms[0].name, "충남대학교병원");
  assert.equal(full.rules[0].hours, 209);
  assert.deepEqual(full.rules[0].takes, ["위험근무수당"]);
  // The short name people say finds it too.
  assert.equal((await ask("충남대병원")).rules[0].name, "충남대학교병원");
  assert.equal((await ask("현대제철")).rules, undefined);
  // What people call it finds it, ahead of the names that only hold the word.
  assert.deepEqual((await ask("코레일")).rules.map((r) => r.name), ["한국철도공사", "코레일테크(주)"]);
});
