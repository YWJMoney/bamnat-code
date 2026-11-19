// 2.0.0 [RVQ-60] PC-206: 열쇠로 부른 '나간 판 저장'이 아는 판. 판 번호 표(APP_CODES, 1.28.0까지)는 원격 파일·출시 대기 작품의 몫도 정해서
// 넓히지 않아요. 그 밖의 판은 Play에서 보거나 그 판으로 쓰는 휴대폰이 생겨야 알아요. 로컬 서버 --seed는 이 빌드의 판으로 쓰는 휴대폰을
// 며칠 넣어 Q1-500 12번이 그 판으로 돼요. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { server } from "./helpers.mjs";
import { knownVersion } from "../src/desk.js";
import { APP_CODES } from "../src/index.js";

test("[RVQ-60] PC-206 판 번호 표에 없는 판은 쓰는 휴대폰이 생기면 알아요, 로컬 --seed는 이 빌드의 판을 넣어요", async (t) => {
  const { env, call } = server({ t });
  await call("GET", "/api/terms");
  assert.equal(APP_CODES["2.0.0"], undefined, "not in the table");
  assert.equal(await knownVersion(env, "1.28.0"), true);
  assert.equal(await knownVersion(env, "2.0.0"), false);
  await env.DB.prepare("INSERT INTO usage_days (day, app, phones) VALUES ('2026-11-18', '2.0.0', 2)").run();
  assert.equal(await knownVersion(env, "2.0.0"), true, "phones on it make it known, as on the real server");
  // The local server's --seed puts this build's own version in (Q1-500 12번).
  assert.match(readFileSync(new URL("./local-server.mjs", import.meta.url), "utf8"), /INSERT OR IGNORE INTO usage_days \(day, app, phones\) VALUES \(\?, \?, 2\)", day, APP_NOW/);
});
