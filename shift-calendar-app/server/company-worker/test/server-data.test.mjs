// [SRV-03] docs/server-data.md lists every table the company server makes and every key the share server
// writes, so a new table cannot go out without its reason and its deletion. `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const doc = read("../../../docs/server-data.md");
/** The first cell of each table row in the document, without its backticks. */
const listed = new Set([...doc.matchAll(/^\| `([^`]+)`/gm)].map((m) => m[1]));

test("[SRV-03] every table the company server makes has a line in docs/server-data.md", () => {
  // 2.0.0 [RVQ-16] 검토 SRV-10: SCHEMA와 몇 파일만 보던 것을 src 아래 모든 파일로(그 사이 55개 표가 문서 밖에 있었어요). 줄은 보관 장부
  // (src/retention.js)에서 만들어지고, 장부와 문서가 같은지는 test/retention.test.mjs가 봐요.
  const tables = [];
  for (const f of readdirSync(new URL("../src/", import.meta.url), { recursive: true }).map(String).filter((x) => x.endsWith(".js"))) {
    for (const m of read(`../src/${f}`).matchAll(/CREATE (?:VIRTUAL )?TABLE IF NOT EXISTS (\w+)/g)) tables.push(m[1]);
  }
  assert.ok(tables.length > 100, "found the tables");
  const missing = tables.filter((t) => !listed.has(t));
  assert.deepEqual(missing, [], "docs/server-data.md needs a line for these tables");
  // And no line for a table that is not there any more (the old sessions table is dropped).
  const gone = [...listed].filter((t) => /^[a-z_]+$/.test(t) && !tables.includes(t));
  assert.deepEqual(gone, [], "lines for tables the server no longer makes");
});

test("[SRV-03] every kind of key the share server writes has a line in docs/server-data.md", () => {
  const src = read("../../share-worker/src/index.js");
  const prefixes = new Set([...src.matchAll(/[`"](rl|rr|rp|report|stats):/g)].map((m) => m[1]));
  // allow(env, request, "rr", …) builds its keys from the prefix it is given.
  for (const m of src.matchAll(/allow\(env, request, "(\w+)"/g)) prefixes.add(m[1]);
  assert.ok(prefixes.has("report") && prefixes.has("stats") && prefixes.has("rp"));
  for (const p of prefixes) assert.ok(doc.includes(`\`${p}:`), `docs/server-data.md needs the ${p}: keys`);
  assert.ok(doc.includes("`<링크 id>`"), "the links themselves");
});
