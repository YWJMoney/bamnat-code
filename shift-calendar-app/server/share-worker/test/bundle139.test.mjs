// 1.39 [ADM3-43] The dashboard worker.js is one file: no import left, and it answers like src/index.js (health with the version).
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { bundle } from "../tools/bundle.mjs";
import { SERVER_VERSION } from "../src/version.js";

test("[ADM3-43] the bundle has no import and its /api/health tells the server version", async () => {
  const code = bundle();
  assert.doesNotMatch(code, /^\s*import\s/m);
  const file = join(mkdtempSync(join(tmpdir(), "share-bundle-")), "worker.js");
  writeFileSync(file, code);
  const worker = (await import(pathToFileURL(file).href)).default;
  const r = await worker.fetch(new Request("https://share.example/api/health"), {}, { waitUntil() {} });
  assert.equal(r.status, 200);
  assert.equal((await r.json()).version, SERVER_VERSION);
});
