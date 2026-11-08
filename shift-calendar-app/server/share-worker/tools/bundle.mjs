// 1.39 [ADM3-43] One worker.js to paste into the Cloudflare dashboard (Edit code): src/index.js with every file it imports
// from src/ (now src/version.js) put in place of the import lines, each once. The dashboard has only worker.js, so a
// copy of src/index.js alone fails there with 'No such module "version.js"' (2026-11-08 실배포에서 일어남).
//   npm run bundle            -> dist/worker.js
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const IMPORT = /^import \{ [\w, ]+ \} from "\.\/([\w/-]+\.js)";$/gm;

export function bundle() {
  const done = new Set(["index.js"]);
  const inline = (code) => code.replace(IMPORT, (_, file) => {
    if (done.has(file)) return "";
    done.add(file);
    const body = inline(readFileSync(join(root, "src", file), "utf8")).replace(/^export (async function|function|const|let|class) /gm, "$1 ");
    return `// ---- src/${file} ----\n${body}\n// ---- end of src/${file} ----`;
  });
  const out = inline(readFileSync(join(root, "src/index.js"), "utf8"));
  if (/^\s*import\s/m.test(out)) throw new Error("an import is left that the bundle cannot follow");
  return out;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  mkdirSync(join(root, "dist"), { recursive: true });
  const out = join(root, "dist/worker.js");
  writeFileSync(out, bundle());
  console.log(`wrote ${out}`);
}
