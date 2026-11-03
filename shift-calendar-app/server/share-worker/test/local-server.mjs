// [SRV-31] 1.36: the share Worker on a plain Node server with an in-memory KV, so a debug build tested against the PC never puts a
// link on the real s.bamnat.app:
//   npm run local -- 8790 [--test-clock]
// --test-clock: POST /__test/clock?days=N moves this server's clock on N days (negative goes back), like the company local server,
// so a phone whose clock was moved for a test gets links that end when it expects. Only this local server has it.
// A debug build whose company server is the PC (-Pbamnat.companyBaseUrl=http://10.0.2.2:…) sends links to http://10.0.2.2:8790.
import http from "node:http";
import worker from "../src/index.js";

const args = process.argv.slice(2);
const port = Number(args.find((a) => /^\d+$/.test(a)) || 8790);

const testClock = args.includes("--test-clock");
let offset = 0;
if (testClock) {
  const RealDate = Date;
  const realNow = RealDate.now.bind(RealDate);
  // Both Date.now() and new Date() follow the moved clock (the worker uses both).
  globalThis.Date = class extends RealDate {
    constructor(...a) { if (a.length) super(...a); else super(realNow() + offset); }
    static now() { return realNow() + offset; }
  };
}

function memoryKv() {
  const map = new Map();
  const meta = new Map();
  return {
    async get(key, type) {
      const v = map.get(key);
      if (v === undefined) return null;
      return type === "json" ? JSON.parse(v) : v;
    },
    async getWithMetadata(key) { return { value: await this.get(key), metadata: meta.get(key) ?? null }; },
    async put(key, value, opts) {
      map.set(key, value);
      if (opts?.metadata) meta.set(key, opts.metadata); else meta.delete(key);
    },
    async delete(key) { map.delete(key); meta.delete(key); },
    async list({ prefix = "" } = {}) {
      return { keys: [...map.keys()].filter((k) => k.startsWith(prefix)).sort().map((name) => ({ name, metadata: meta.get(name) })), list_complete: true };
    },
  };
}

const env = { SHARES: memoryKv(), RATE_SALT: "local", SHARE_ADMIN_TOKEN: process.env.SHARE_ADMIN_TOKEN };
const ctx = { waitUntil: (p) => Promise.resolve(p).catch(() => {}) };

http.createServer(async (req, res) => {
  if (testClock && req.method === "POST" && req.url.startsWith("/__test/clock")) {
    offset += Number(new URL(req.url, "http://x").searchParams.get("days") || 1) * 864e5;
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ now: new Date().toISOString() }));
    return;
  }
  try {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    const body = chunks.length && req.method !== "GET" && req.method !== "HEAD" ? Buffer.concat(chunks) : undefined;
    const headers = new Headers();
    for (const [k, v] of Object.entries(req.headers)) if (typeof v === "string") headers.set(k, v);
    headers.set("cf-connecting-ip", req.socket.remoteAddress || "127.0.0.1");
    const out = await worker.fetch(new Request(`http://${req.headers.host || `localhost:${port}`}${req.url}`, { method: req.method, headers, body }), env, ctx);
    res.writeHead(out.status, Object.fromEntries(out.headers));
    res.end(Buffer.from(await out.arrayBuffer()));
  } catch (e) {
    res.writeHead(500, { "content-type": "text/plain" });
    res.end(String(e));
  }
}).listen(port, () => console.log(`share worker on http://localhost:${port} (in memory; the emulator reaches it at http://10.0.2.2:${port})` +
  (testClock ? " · test clock: POST /__test/clock?days=N" : "")));
