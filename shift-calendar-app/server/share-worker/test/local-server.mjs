// [SRV-31] 1.36: the share Worker on a plain Node server with an in-memory KV, so a debug build tested against the PC never puts a
// link on the real s.bamnat.app:
//   npm run local -- 8790
// A debug build whose company server is the PC (-Pbamnat.companyBaseUrl=http://10.0.2.2:…) sends links to http://10.0.2.2:8790.
import http from "node:http";
import worker from "../src/index.js";

const args = process.argv.slice(2);
const port = Number(args.find((a) => /^\d+$/.test(a)) || 8790);

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
}).listen(port, () => console.log(`share worker on http://localhost:${port} (in memory; the emulator reaches it at http://10.0.2.2:${port})`));
