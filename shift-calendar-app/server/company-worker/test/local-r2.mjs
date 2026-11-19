// 2.0.0 [RVQ-16] R2 as the daily backup uses it (put, get with body stream and text, list with prefix·delimiter·cursor, delete),
// for the tests and the local server. With a folder, each key is a file under it, so a backed-up day is the folder
// `<folder>/d1/<day>/` that `tools/backup-open.mjs` reads, as if downloaded from R2. Without one, it lives in memory.
import { mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, sep } from "node:path";

export function localR2(dir = null) {
  const store = new Map();
  const file = (key) => join(dir, ...key.split("/"));
  const read = (key) => {
    if (!dir) return store.get(key) ?? null;
    try {
      return new Uint8Array(readFileSync(file(key)));
    } catch {
      return null;
    }
  };
  const keys = () => {
    if (!dir) return [...store.keys()];
    const out = [];
    const walk = (d) => {
      let names = [];
      try {
        names = readdirSync(d);
      } catch {
        return;
      }
      for (const n of names) {
        const p = join(d, n);
        if (statSync(p).isDirectory()) walk(p);
        else out.push(relative(dir, p).split(sep).join("/"));
      }
    };
    walk(dir);
    return out;
  };
  return {
    store,
    dir,
    async put(key, body) {
      const bytes = typeof body === "string" ? new TextEncoder().encode(body) : new Uint8Array(body);
      if (!dir) return void store.set(key, bytes);
      mkdirSync(dirname(file(key)), { recursive: true });
      writeFileSync(file(key), bytes);
    },
    async get(key) {
      const v = read(key);
      if (!v) return null;
      return { body: new Blob([v]).stream(), text: async () => new TextDecoder().decode(v) };
    },
    async list({ prefix = "", delimiter } = {}) {
      const all = keys().filter((k) => k.startsWith(prefix)).sort();
      if (!delimiter) return { objects: all.map((key) => ({ key })), truncated: false };
      const pre = new Set(all.map((k) => k.slice(prefix.length)).filter((k) => k.includes(delimiter)).map((k) => prefix + k.slice(0, k.indexOf(delimiter) + 1)));
      return { objects: [], delimitedPrefixes: [...pre], truncated: false };
    },
    async delete(list) {
      for (const k of [].concat(list)) {
        if (!dir) store.delete(k);
        else rmSync(file(k), { force: true });
      }
    },
  };
}
