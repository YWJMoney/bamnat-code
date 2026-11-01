// 1.36 [SEC-01][SEC-02] 보안 점검: IPv6 is counted by its /64, and reads are held per network.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { netOf, readsForget, LIMITS } from "../src/index.js";

function kv() {
  const map = new Map();
  return { map, async get(k, t) { const v = map.get(k); return v === undefined ? null : t === "json" ? JSON.parse(v) : v; },
    async put(k, v) { map.set(k, v); }, async delete(k) { map.delete(k); }, async list() { return { keys: [], list_complete: true }; } };
}
const get = (env, path, ip) => worker.fetch(new Request(`https://share.example${path}`, { headers: { "cf-connecting-ip": ip } }), env, { waitUntil() {} });

test("netOf: IPv4 stays, IPv6 is its /64 however it is written", () => {
  assert.equal(netOf("203.0.113.5"), "203.0.113.5");
  assert.equal(netOf("2001:db8:1:2:aaaa::1"), "2001:db8:1:2::/64");
  assert.equal(netOf("2001:0db8:0001:0002:ffff:ffff:ffff:ffff"), "2001:db8:1:2::/64");
  assert.equal(netOf("2001:db8::1"), "2001:db8:0:0::/64");
  assert.equal(netOf("::ffff:198.51.100.7"), "198.51.100.7");
  assert.equal(netOf(null), "unknown");
});

test("reads past the hour's limit from one /64 are refused; another network still reads", async () => {
  readsForget();
  const env = { SHARES: kv() };
  let last;
  for (let i = 0; i <= LIMITS.groupReadsPerHour; i++) last = await get(env, "/api/groups/ABCDEF", `2001:db8:5:6::${(i + 1).toString(16)}`);
  assert.equal(last.status, 429, "the 61st group read from the same /64 (each from a new address) is refused");
  assert.ok(last.headers.get("retry-after"));
  const other = await get(env, "/api/groups/ABCDEF", "198.51.100.9");
  assert.equal(other.status, 404, "another network reads as before (no such group)");
  readsForget();
});

test("the page answers 429 as a page past the read limit", async () => {
  readsForget();
  const env = { SHARES: kv() };
  const saved = LIMITS.readsPerHour;
  LIMITS.readsPerHour = 3;
  try {
    for (let i = 0; i < 3; i++) assert.equal((await get(env, "/s/AAAAAAAAAAAAAAAAAAAAAA", "203.0.113.77")).status, 404);
    const r = await get(env, "/s/AAAAAAAAAAAAAAAAAAAAAA", "203.0.113.77");
    assert.equal(r.status, 429);
    assert.match(r.headers.get("content-type"), /text\/html/);
  } finally { LIMITS.readsPerHour = saved; readsForget(); }
});
