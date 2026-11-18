// 2.0.0 [RVR-13] (검토 TEST-06) 서버의 시계는 하나: SQL은 SQLite 'now'가 아니라 서버의 Date.now()를 묶어 받아요.
// 시험 시계(t.mock.timers, tools/fake-date.mjs, local-server --test-clock)와 실서버가 같은 시계를 보게 해요.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { BIG_ANSWER, server, together } from "./helpers.mjs";

const here = fileURLToPath(new URL(".", import.meta.url));
const SQL_CLOCK = /strftime\s*\([^)]*'now'|(?:date|time|datetime|julianday|unixepoch)\s*\(\s*'now'|unixepoch\s*\(\s*\)|CURRENT_(?:TIMESTAMP|DATE|TIME)\b/i;

for (const worker of ["company-worker", "share-worker"]) {
  test(`${worker}: src에 SQLite 시계('now'·CURRENT_TIMESTAMP)가 없어요`, () => {
    const dir = join(here, "..", "..", worker, "src");
    const found = [];
    for (const f of readdirSync(dir).filter((n) => n.endsWith(".js"))) {
      readFileSync(join(dir, f), "utf8").split("\n").forEach((line, i) => {
        const code = line.replace(/\/\/.*$/, "");
        if (SQL_CLOCK.test(code)) found.push(`${f}:${i + 1}`);
      });
    }
    assert.deepEqual(found, [], "SQL에서 지금 시각은 ?로 묶어 Date.now()를 넘겨요");
  });
}

test("links_tracked_from은 서버 시계로 처음 한 번 적혀요", async (t) => {
  const at = Date.parse("2027-03-02T00:00:00Z");
  t.mock.timers.enable({ apis: ["Date"], now: at });
  const { DB, call } = server();
  await call("GET", "/api/terms");
  const row = await DB.prepare("SELECT value FROM settings WHERE key = 'links_tracked_from'").first();
  assert.equal(Number(row.value), at);
  t.mock.timers.tick(86400e3);
  await DB.prepare("DELETE FROM settings WHERE key = 'schema'").run();
  await call("GET", "/api/terms");
  const again = await DB.prepare("SELECT value FROM settings WHERE key = 'links_tracked_from'").first();
  assert.equal(Number(again.value), at, "다시 표를 맞춰도 처음 적은 때 그대로");
});

test("같은 요청을 한꺼번에 보내는 도우미와 큰 응답 알림", async () => {
  const { call, big } = server();
  const answers = await together(call, 3, "GET", "/api/terms");
  assert.deepEqual(answers.map((a) => a.status), [200, 200, 200]);
  assert.deepEqual(big, [], `${BIG_ANSWER}자 넘는 답이 없어요`);
});
