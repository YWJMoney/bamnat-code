// 1.38 [STM-05] 밤낮 재료(색 아이콘·배경) 넣기 도구.
//   node tools/materials-seed.mjs --check                          목록과 그림 파일을 맞춰 봐요(크기·이름·중복·id)
//   node tools/materials-seed.mjs --post http://127.0.0.1:8789     로컬 서버의 출시 대기에 넣어요(ADMIN_PASSWORD는 환경 변수로)
//   … --post <주소> --open                                          넣고 바로 공개(PC 확인용)
//   node tools/materials-seed.mjs --files <폴더>                    실서버용 재료 파일(재료-01.json …)을 써요 (1.39 E11)
// 실서버에는 이 도구로 넣지 않아요. 실서버는 사용자가 정한 뒤 관리자 앱 › 장터 › 출시 대기 › 밤낮 재료 › '재료 파일 올리기'에
// --files로 만든 파일을 골라 넣어요(관리자 로그인 그대로, 비밀번호를 도구에 주지 않음). 순서: docs/ops-market-seed.md.
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import "../src/index.js"; // 모듈 순서를 서버와 같게(순환 import)
import { MATERIAL_KINDS, MATERIAL_LIMITS, webpSize } from "../src/studio-materials.js";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = join(here, "../../../tools/materials/out");

const sha16 = (b) => createHash("sha256").update(b).digest("hex").slice(0, 16);

export function load(out = OUT) {
  const list = JSON.parse(readFileSync(join(out, "목록.json"), "utf8"));
  const problems = [];
  const seen = new Set();
  // 사용자 결정 2026-11-06: 아이콘은 모두 무료, 배경은 묶음마다 앞쪽 3분의 1(순서대로)만 무료이고 나머지는 광고 한 편(30일)·프로.
  // 관리자 앱 › 출시 대기 › 밤낮 재료의 '3분의 1 무료'와 같은 셈(ord 순)이에요.
  const freeBg = new Set();
  for (const pack of new Set(list.items.filter((m) => m.kind === "bg").map((m) => m.pack))) {
    const rows = list.items.filter((m) => m.kind === "bg" && m.pack === pack).sort((x, y) => x.ord - y.ord || (x.id < y.id ? -1 : 1));
    rows.slice(0, Math.ceil(rows.length / 3)).forEach((m) => freeBg.add(m.id));
  }
  const items = list.items.map((m) => {
    const webp = readFileSync(join(out, m.file));
    const thumb = m.thumb ? readFileSync(join(out, m.thumb)) : null;
    const size = webpSize(new Uint8Array(webp));
    const where = `${m.key} ${m.name}`;
    if (!MATERIAL_KINDS.includes(m.kind)) problems.push(`${where}: 종류 ${m.kind}`);
    if ([...m.name].length > MATERIAL_LIMITS.name || [...m.pack].length > MATERIAL_LIMITS.pack) problems.push(`${where}: 이름이 길어요`);
    if (webp.length > MATERIAL_LIMITS.bytes) problems.push(`${where}: ${webp.length} 바이트`);
    if (!size || size.w > MATERIAL_LIMITS.side || size.h > MATERIAL_LIMITS.side) problems.push(`${where}: 그림 크기`);
    if (sha16(webp) !== m.id) problems.push(`${where}: id가 그림과 달라요`);
    if (thumb && sha16(thumb) !== m.thumbId) problems.push(`${where}: 미리보기 id가 달라요`);
    if (seen.has(m.id)) problems.push(`${where}: 같은 그림이 또 있어요`);
    seen.add(m.id);
    return { kind: m.kind, pack: m.pack, name: m.name, ord: m.ord, light: m.light, free: m.kind !== "bg" || freeBg.has(m.id),
      webp: webp.toString("base64"), ...(thumb ? { thumb: thumb.toString("base64") } : {}) };
  });
  return { items, problems, license: list.license };
}

async function post(base, items, open = false) {
  const pw = process.env.ADMIN_PASSWORD;
  if (!pw) throw new Error("ADMIN_PASSWORD 가 없어요");
  if (!/^http:\/\/(127\.0\.0\.1|localhost|10\.0\.2\.2)(:\d+)?$/.test(base)) throw new Error("로컬 서버에만 넣어요");
  const login = await fetch(`${base}/api/admin/login`, { method: "POST", headers: { "content-type": "application/json", "x-admin": "1", origin: base },
    body: JSON.stringify({ password: pw }) });
  if (login.status !== 200) throw new Error(`login ${login.status}`);
  const cookie = login.headers.get("set-cookie").split(";")[0];
  let ok = 0;
  for (let i = 0; i < items.length; i += 20) {
    const batch = items.slice(i, i + 20);
    const r = await fetch(`${base}/api/admin/recipes/materials`, { method: "POST",
      headers: { "content-type": "application/json", "x-admin": "1", origin: base, cookie }, body: JSON.stringify({ items: batch }) });
    if (r.status < 300) ok += batch.length;
    else console.log(`못 넣음 ${i}~: ${r.status} ${await r.text()}`);
  }
  // --open: PC 확인용으로 바로 공개(실서버에서는 출시 날 관리자 앱에서).
  if (open) {
    const r = await fetch(`${base}/api/admin/recipes/materials/state`, { method: "POST",
      headers: { "content-type": "application/json", "x-admin": "1", origin: base, cookie }, body: JSON.stringify({ state: "ok", all: true }) });
    console.log(`공개: ${r.status} ${await r.text()}`);
  }
  return ok;
}

/** The marker the 관리자 앱's '재료 파일 올리기' looks for, so a wrong file is turned away before anything goes up. */
export const FILE_KIND = "bamnat-materials";
/** Items per file: under 5 MB each (backgrounds are the big ones), so a phone browser and the project folder take each without trouble. */
export const PER_FILE = 25;

/** 1.39 E11: the materials as files for the real server ({kind, part, of, items}), [PER_FILE] each, in ord order per pack. */
export function files(items, per = PER_FILE) {
  const out = [];
  for (let i = 0; i < items.length; i += per) out.push(items.slice(i, i + per));
  return out.map((chunk, i) => ({ kind: FILE_KIND, part: i + 1, of: out.length, items: chunk }));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const { items, problems } = load();
  const icons = items.filter((x) => x.kind === "icon").length;
  console.log(`재료 ${items.length}개 (아이콘 ${icons}, 배경 ${items.length - icons})`);
  for (const p of problems) console.log(`문제: ${p}`);
  if (problems.length) process.exit(1);
  const fi = args.indexOf("--files");
  if (fi >= 0) {
    const dir = args[fi + 1];
    if (!dir) throw new Error("--files 다음에 폴더를 적어요");
    mkdirSync(dir, { recursive: true });
    const list = files(items);
    list.forEach((f) => writeFileSync(join(dir, `재료-${String(f.part).padStart(2, "0")}.json`), JSON.stringify(f)));
    console.log(`${dir}에 재료 파일 ${list.length}개를 썼어요(파일마다 ${PER_FILE}개까지)`);
  }
  const at = args.indexOf("--post");
  if (at >= 0) console.log(`로컬 서버 출시 대기에 ${await post(args[at + 1], items, args.includes("--open"))}개 넣었어요`);
}
