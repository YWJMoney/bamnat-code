// 2.0.0 [RVQ-16] 검토 SRV-15·U21-08: R2 하루 백업 한 날을 D1에 다시 넣을 SQL로 바꿔요.
//
//   1. Cloudflare › R2 › bamnat-backup에서 그날 폴더(d1/YYYY-MM-DD/)의 파일을 모두 한 폴더에 내려받아요.
//   2. BAMNAT_BACKUP_SECRET에 그날 잠근 비밀값(SEAL_KEY, 없었으면 RATE_SALT)을 넣고(바꾼 지 얼마 안 됐으면 옛 값은
//      BAMNAT_BACKUP_SECRET_OLD에)
//        node tools/backup-open.mjs <내려받은 폴더> > restore.sql
//   3. 로컬 서버(npm run local)의 DB에 먼저 넣어 보고 맞으면 npx wrangler d1 execute bamnat-company --remote --file=restore.sql
//
// 백업에서 뺀 표(곧 지워지는 것·다시 만들 수 있는 것, docs/server-data.md '하루 백업' 칸)는 SQL에 없어요. 잠근 칸이 있는데 비밀값이
// 없거나 맞지 않으면 아무것도 쓰지 않고 멈춰요(반쯤 되살린 DB가 생기지 않게). 비밀값은 화면·파일에 쓰지 않아요.
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const SEALED = /^s1\.[A-Za-z0-9_-]{24,}$/;
const enc = new TextEncoder();
const dec = new TextDecoder();

const unb64 = (text) => Uint8Array.from(Buffer.from(text.replace(/-/g, "+").replace(/_/g, "/"), "base64"));

/** The backup's key for [secret], the same as src/seal.js makes for the label 'backup'. */
async function backupKeyOf(secret) {
  const raw = await crypto.subtle.digest("SHA-256", enc.encode(`seal|backup|${secret}`));
  return crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["decrypt"]);
}

async function opener(secrets) {
  const keys = [];
  for (const s of secrets.filter(Boolean)) keys.push(await backupKeyOf(s));
  return async (value) => {
    if (!SEALED.test(String(value ?? ""))) return null;
    const bytes = unb64(value.slice(3));
    for (const k of keys) {
      try {
        return dec.decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: bytes.slice(0, 12) }, k, bytes.slice(12)));
      } catch {
        // Another secret.
      }
    }
    return null;
  };
}

const quote = (v) => {
  if (v === null || v === undefined) return "NULL";
  if (typeof v === "number") return Number.isFinite(v) ? String(v) : "NULL";
  if (typeof v === "boolean") return v ? "1" : "0";
  return `'${String(v).replace(/'/g, "''")}'`;
};

/** The part files of [table] in [dir], in order (2.0.0 parts, or the one file an older backup wrote). */
function partsOf(dir, table, parts) {
  if (parts) return Array.from({ length: parts }, (_, i) => join(dir, `${table}.${i + 1}.ndjson.gz`));
  return [join(dir, `${table}.ndjson.gz`)];
}

/**
 * SQL that puts the day in [dir] back, with the cells the backup sealed opened by [secrets].
 * Throws when a part is missing, a line is not JSON, or a sealed cell does not open.
 */
export async function openDay(dir, secrets = []) {
  const manifest = JSON.parse(readFileSync(join(dir, "manifest.json"), "utf8"));
  const open = await opener(secrets);
  const out = [`-- 밤낮 회사 서버 백업 ${manifest.day} (표 ${Object.keys(manifest.tables).length}개), tools/backup-open.mjs`, "PRAGMA defer_foreign_keys = true;"];
  const counts = {};
  for (const table of Object.keys(manifest.tables).sort()) {
    if (!/^[A-Za-z0-9_]+$/.test(table)) throw new Error(`표 이름이 이상해요: ${table}`);
    let n = 0;
    for (const file of partsOf(dir, table, manifest.parts?.[table])) {
      if (!existsSync(file)) throw new Error(`조각이 없어요: ${file}`);
      const text = gunzipSync(readFileSync(file)).toString("utf8");
      for (const line of text ? text.split("\n") : []) {
        const row = JSON.parse(line);
        const cols = Object.keys(row);
        const vals = [];
        for (const c of cols) {
          let v = row[c];
          if (v && typeof v === "object" && typeof v.$sealed === "string") {
            v = await open(v.$sealed);
            if (v === null) throw new Error(`${table}.${c}의 잠근 칸이 열리지 않아요: 그날 잠근 비밀값을 BAMNAT_BACKUP_SECRET(옛 값은 _OLD)에 넣어 주세요`);
          }
          vals.push(quote(v));
        }
        out.push(`INSERT OR REPLACE INTO "${table}" (${cols.map((c) => `"${c}"`).join(", ")}) VALUES (${vals.join(", ")});`);
        n += 1;
      }
    }
    if (n !== manifest.tables[table]) throw new Error(`${table}: 백업 ${n}줄, 목록 ${manifest.tables[table]}줄`);
    counts[table] = n;
  }
  return { sql: `${out.join("\n")}\n`, counts, day: manifest.day };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const dir = process.argv[2];
  if (!dir || !existsSync(join(dir, "manifest.json"))) {
    console.error("쓰는 법: BAMNAT_BACKUP_SECRET=… node tools/backup-open.mjs <내려받은 날 폴더(manifest.json이 있는 곳)> > restore.sql");
    process.exit(2);
  }
  try {
    const { sql, counts, day } = await openDay(dir, [process.env.BAMNAT_BACKUP_SECRET, process.env.BAMNAT_BACKUP_SECRET_OLD]);
    process.stdout.write(sql);
    console.error(`${day}: 표 ${Object.keys(counts).length}개, ${Object.values(counts).reduce((a, b) => a + b, 0)}줄 (${readdirSync(dir).length}개 파일)`);
  } catch (e) {
    console.error(String(e?.message ?? e));
    process.exit(1);
  }
}
