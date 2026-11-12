// 2.0.0 [RVQ-02] 검토 SRV-04 (마): 'DB 판은 앞으로만'을 사람이 기억하지 않아도 되게. 배포 도구(build, 토큰 없음)가 지금 도는 판의
// 커밋을 받아 와서 이 파일을 불러요.
//   node tools/schema-compat.mjs <앞 판 company-worker 폴더>
// 같은 빈 DB에서
//   1. 이 판(새 코드)이 표를 만들어요(deep health).
//   2. 앞 판 코드가 그 DB에서 매일 쓰는 길을 돌아요: health, hello, config, 약관, 회사 찾기, 광장 목록, 장터 목록, 매시 작업, 5분 작업.
//      500이 하나라도 나오거나 정기 작업이 던지면 실패예요(되돌리면 앞 판 코드가 새 표 위에서 돌기 때문).
//   3. 이 판이 다시 올라와도(앞 판이 표 표시를 바꿔 둔 뒤) 깨지지 않는지 봐요.
// 실서버·바깥에 닿지 않아요(바깥으로 가는 fetch는 막아요). 비밀값은 이 실행에서만 쓰는 가짜예요.
import { resolve, join } from "node:path";
import { pathToFileURL, fileURLToPath } from "node:url";
import { existsSync } from "node:fs";
import { randomBytes } from "node:crypto";
import { d1 } from "../test/d1.mjs";

const here = resolve(fileURLToPath(import.meta.url), "../..");
const oldDir = resolve(process.argv[2] ?? "");
if (!process.argv[2] || !existsSync(join(oldDir, "src/index.js"))) {
  console.error("쓰는 법: node tools/schema-compat.mjs <앞 판 company-worker 폴더>");
  process.exit(2);
}

const ORIGIN = "https://compat.example";
// 바깥(푸시·메일·Play·공유 서버)으로 나가는 요청은 이 확인에서 모두 막아요.
globalThis.fetch = async () => new Response("{}", { status: 503 });

const DB = d1();
const env = { DB, ADMIN_PASSWORD: randomBytes(18).toString("base64url"), RATE_SALT: randomBytes(12).toString("hex") };
const ctx = () => {
  const waits = [];
  return { waits, waitUntil: (p) => waits.push(Promise.resolve(p).catch((e) => e)), passThroughOnException() {} };
};

const failures = [];
async function call(worker, who, method, path, body) {
  const c = ctx();
  let status = 0;
  try {
    const res = await worker.fetch(new Request(ORIGIN + path, {
      method,
      headers: { "cf-connecting-ip": "203.0.113.9", ...(body ? { "content-type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    }), env, c);
    status = res.status;
    await res.arrayBuffer().catch(() => {});
  } catch (e) {
    failures.push(`${who} ${method} ${path}: 던짐 ${e?.message ?? e}`);
    return 0;
  }
  await Promise.all(c.waits);
  if (status >= 500) failures.push(`${who} ${method} ${path}: ${status}`);
  return status;
}

async function cron(worker, who, schedule) {
  const c = ctx();
  try {
    await worker.scheduled({ cron: schedule, scheduledTime: Date.now() }, env, c);
    const errs = (await Promise.all(c.waits)).filter((x) => x instanceof Error);
    for (const e of errs) failures.push(`${who} 정기 작업 ${schedule}: ${e.message}`);
  } catch (e) {
    failures.push(`${who} 정기 작업 ${schedule}: 던짐 ${e?.message ?? e}`);
  }
}

const fresh = (await import(pathToFileURL(join(here, "src/index.js")).href)).default;
const old = (await import(pathToFileURL(join(oldDir, "src/index.js")).href)).default;

// 1. 새 코드가 표를 만들어요.
if ((await call(fresh, "새 판", "GET", "/api/admin/health?deep=1")) !== 200) failures.push("새 판이 빈 DB에서 표를 만들지 못했어요");
const made = (await DB.prepare("SELECT value FROM settings WHERE key = 'schema'").first())?.value;

// 2. 앞 판 코드가 그 DB에서.
const paths = ["/api/admin/health?deep=1", "/api/hello", "/api/config", "/api/terms", "/api/companies?q=%EB%B0%A4", "/api/plaza/posts",
  "/api/market/items", "/api/known", "/admin/"];
for (const p of paths) await call(old, "앞 판", "GET", p);
await cron(old, "앞 판", "17 * * * *");
await cron(old, "앞 판", "*/5 * * * *");

// 3. 새 코드가 다시(앞 판이 표 표시를 자기 판으로 바꿔 둔 뒤). 카나리처럼 이미 떠 있던 새 isolate와 새로 뜬 isolate 둘 다.
if ((await call(fresh, "새 판(떠 있던)", "GET", "/api/admin/health?deep=1")) !== 200) failures.push("앞 판이 돈 뒤 떠 있던 새 판이 표를 다시 맞추지 못했어요");
await call(fresh, "새 판(떠 있던)", "GET", "/api/config");
const again = (await import(`${pathToFileURL(join(here, "src/index.js")).href}?isolate=2`)).default;
if ((await call(again, "새 판(새로 뜬)", "GET", "/api/admin/health?deep=1")) !== 200) failures.push("앞 판이 돈 뒤 새로 뜬 새 판이 표를 맞추지 못했어요");
for (const p of paths) await call(again, "새 판(새로 뜬)", "GET", p);

if (failures.length) {
  console.error(`스키마 호환 실패 ${failures.length}건:\n- ${failures.join("\n- ")}`);
  process.exit(1);
}
console.log(`맞음: 새 표(${made}) 위에서 앞 판 코드의 ${paths.length}개 길·정기 작업 2개가 500 없이 돌아요`);
