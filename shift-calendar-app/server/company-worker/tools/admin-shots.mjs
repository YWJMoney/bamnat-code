// 2.0.0 [RVA-55] 검토 I23b-01: 관리자 앱 화면 사진 회귀 시험(네 폭 × 밝음·어두움, 주요 12화면).
//
// ADM-07·08 같은 배치 깨짐을 PR·통합 빌드에서 잡아요. 로컬 서버(--seed --market --test-clock)를 띄우고 헤드리스 크로미움으로 화면마다 찍어서
//   ① 배치 검사(빨강): 옆으로 넘침(가로 스크롤), 화면 오류(pageerror), 빈 본문, 본문 첫 줄이 화면 위 25% 아래에서 시작(조건 1),
//      48px(작아도 44px)보다 작은 누르는 곳이 본문에 몇 개인지(조건 9, 휴대폰 폭만 세어 요약에)
//   ② 지난 사진과 대기(실패 아님): 32×32 회색 지문(평균 해시)을 견줘 크게 달라진 화면만 요약과 changed/에.
// 사진·지문은 <out>/png, <out>/shots.json. 서버 비밀값을 쓰지 않아요(임시 비밀번호를 그 자리에서 만들어 이 서버에만 써요).
//
//   node tools/admin-shots.mjs --start [--out build/admin-shots] [--prev <지난 shots.json>] [--only home,desk] [--widths 380,1440]
//   ADMIN_PASSWORD=… node tools/admin-shots.mjs --base http://localhost:8788   (이미 띄운 로컬 서버)
import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { createRequire } from "node:module";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const arg = (name, fallback = null) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 && process.argv[i + 1] && !process.argv[i + 1].startsWith("--") ? process.argv[i + 1] : fallback;
};
const has = (name) => process.argv.includes(`--${name}`);

/** 주요 12화면(허브마다 사람이 가장 자주 여는 것). id가 필요한 화면은 목록에서 첫 줄을 골라요. */
export const SCREENS = [
  { key: "home", route: "home" }, { key: "desk", route: "desk?view=open" }, { key: "decide", route: "decide" },
  { key: "inquiries", route: "inquiries" }, { key: "inquiry", route: "inquiries/:inquiry" }, { key: "dev", route: "dev" },
  { key: "community", route: "community" }, { key: "recipes", route: "recipes" }, { key: "flags", route: "flags" },
  { key: "glance", route: "glance" }, { key: "errors", route: "errors" }, { key: "server", route: "server" },
];
export const WIDTHS = [380, 840, 1280, 1440];
export const SCHEMES = ["light", "dark"];
const HEIGHT = { 380: 820, 840: 1000, 1280: 800, 1440: 900 };

/** 32×32 회색 평균 해시(1024비트, 16진 256자). 페이지 안 캔버스로 풀어 따로 받을 꾸러미가 없어요. */
async function fingerprint(page, png) {
  return page.evaluate(async (b64) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement("canvas");
    c.width = 32;
    c.height = 32;
    const g = c.getContext("2d");
    g.drawImage(img, 0, 0, 32, 32);
    const d = g.getImageData(0, 0, 32, 32).data;
    const v = [];
    for (let i = 0; i < d.length; i += 4) v.push(0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2]);
    const avg = v.reduce((a, b) => a + b, 0) / v.length;
    let hex = "";
    for (let i = 0; i < v.length; i += 4) hex += ((v[i] > avg ? 8 : 0) | (v[i + 1] > avg ? 4 : 0) | (v[i + 2] > avg ? 2 : 0) | (v[i + 3] > avg ? 1 : 0)).toString(16);
    return hex;
  }, png.toString("base64"));
}

/** 두 지문이 다른 비트 비율(0~1). */
export function distance(a, b) {
  if (!a || !b || a.length !== b.length) return 1;
  let n = 0;
  for (let i = 0; i < a.length; i++) {
    let x = parseInt(a[i], 16) ^ parseInt(b[i], 16);
    while (x) {
      n += x & 1;
      x >>= 1;
    }
  }
  return n / (a.length * 4);
}

/** 한 화면의 배치 검사. 빨강(problems)과 요약용 숫자. */
async function layout(page, width) {
  return page.evaluate((w) => {
    const main = document.getElementById("main");
    const problems = [];
    const sw = document.scrollingElement.scrollWidth;
    if (sw > innerWidth + 1) {
      const wide = [...document.querySelectorAll("#main *")].filter((el) => el.getBoundingClientRect().right > innerWidth + 1 && getComputedStyle(el).position !== "fixed")
        .slice(0, 3).map((el) => `${el.tagName.toLowerCase()}.${String(el.className).split(" ")[0]}`);
      problems.push(`옆으로 넘침 ${sw - innerWidth}px (${wide.join(", ")})`);
    }
    if (document.querySelector(".gate")) problems.push("로그인 화면(로그인하지 못했어요)");
    else if (!main || (!main.textContent.trim() && !main.querySelector(".skeleton"))) problems.push("빈 본문");
    const first = main ? [...main.children].find((el) => el.getBoundingClientRect().height > 0) : null;
    const top = first ? Math.round(first.getBoundingClientRect().top + scrollY) : null;
    if (top !== null && top > innerHeight * 0.25) problems.push(`본문 첫 줄이 ${top}px에서 시작(화면 위 25% = ${Math.round(innerHeight * 0.25)}px)`);
    // 조건 9: 휴대폰 폭에서 본문 안 누르는 곳 가운데 44px보다 낮은 것(글 속 링크는 빼요).
    const small = w < 600 ? [...(main?.querySelectorAll("button, a.btn, [role=button], input[type=checkbox], select") ?? [])]
      .filter((el) => { const r = el.getBoundingClientRect(); return r.height > 0 && r.height < 44 && getComputedStyle(el).visibility !== "hidden"; }).length : 0;
    return { problems, top, height: document.documentElement.scrollHeight, small };
  }, width);
}

async function startServer(port) {
  const password = randomBytes(18).toString("base64url");
  const child = spawn(process.execPath, ["--disable-warning=ExperimentalWarning", "test/local-server.mjs", String(port), "--seed", "--market", "--test-clock"],
    { cwd: ROOT, env: { ...process.env, ADMIN_PASSWORD: password }, stdio: ["ignore", "ignore", "inherit"] });
  // localhost: 로그인 쿠키(Secure)를 http에서도 받는 곳이에요(127.0.0.1은 받지 않아요).
  const base = `http://localhost:${port}`;
  for (let i = 0; i < 80; i++) {
    try {
      if ((await fetch(`${base}/admin/`)).ok) return { base, password, child };
    } catch {
      // 아직 뜨는 중
    }
    await new Promise((r) => setTimeout(r, 250));
  }
  child.kill();
  throw new Error("local server did not start");
}

async function main() {
  const out = path.resolve(arg("out", path.join(ROOT, "build", "admin-shots")));
  const only = arg("only")?.split(",") ?? null;
  const widths = arg("widths")?.split(",").map(Number) ?? WIDTHS;
  const screens = SCREENS.filter((s) => !only || only.includes(s.key));
  let server = null;
  let base = arg("base");
  let password = process.env.ADMIN_PASSWORD;
  if (has("start") || !base) {
    server = await startServer(Number(arg("port", 8797)));
    ({ base, password } = server);
  }
  if (!password) throw new Error("ADMIN_PASSWORD (or --start)");
  // CI는 그 자리에서 깐 playwright, 이 저장소의 시험 환경은 전역에 깔린 것(NODE_PATH)을 써요.
  const { chromium } = await import("playwright").catch(() => createRequire(import.meta.url)("playwright"));
  const browser = await chromium.launch(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {});
  fs.mkdirSync(path.join(out, "png"), { recursive: true });
  const shots = {};
  const red = [];
  const small = [];
  try {
    // 화면에 넣을 번호(문의 상세).
    const probe = await browser.newContext();
    await probe.request.post(`${base}/api/admin/login`, { headers: { "x-admin": "1", "content-type": "application/json", origin: base }, data: { password } });
    let list = await (await probe.request.get(`${base}/api/admin/inquiries?status=all`)).json().catch(() => ({}));
    // 새로 띄운 서버(--seed)에는 문의가 없어요: 앱처럼 하나 보내 상세 화면을 찍어요.
    if (!list.inquiries?.length) {
      const made = await probe.request.post(`${base}/api/inquiries`, { headers: { "content-type": "application/json" },
        data: { v: 1, device: "test-device-shots001", kind: "question", text: "[설정 > 알람] 알람 소리를 바꾸고 싶어요. 어디서 바꿔요?", info: { app: "2.0.0 (54)" } } });
      if (!made.ok()) console.error(`문의를 넣지 못했어요: ${made.status()} ${(await made.text()).slice(0, 120)}`);
      list = await (await probe.request.get(`${base}/api/admin/inquiries?status=all`)).json().catch(() => ({}));
    }
    const ids = { inquiry: list.inquiries?.[0]?.id ?? "" };
    if (!ids.inquiry) console.error(`문의 번호를 찾지 못했어요: ${JSON.stringify(list).slice(0, 200)}`);
    await probe.close();
    const helper = await (await browser.newContext()).newPage();
    for (const scheme of SCHEMES) {
      for (const w of widths) {
        const ctx = await browser.newContext({ viewport: { width: w, height: HEIGHT[w] ?? 900 }, colorScheme: scheme, locale: "ko-KR", timezoneId: "Asia/Seoul",
          hasTouch: w < 600, reducedMotion: "reduce" });
        await ctx.request.post(`${base}/api/admin/login`, { headers: { "x-admin": "1", "content-type": "application/json", origin: base }, data: { password } });
        const page = await ctx.newPage();
        const errors = [];
        page.on("pageerror", (e) => errors.push(String(e.message ?? e).split("\n")[0]));
        for (const s of screens) {
          const route = s.route.replace(":inquiry", ids.inquiry);
          if (route.endsWith("/")) continue;
          errors.length = 0;
          await page.goto(`${base}/admin/#/${route}`);
          await page.waitForLoadState("networkidle").catch(() => {});
          await page.waitForTimeout(400);
          await page.mouse.move(0, 0);
          const png = await page.screenshot({ fullPage: false });
          const name = `${s.key}-${w}-${scheme}`;
          fs.writeFileSync(path.join(out, "png", `${name}.png`), png);
          const lay = await layout(page, w);
          const problems = [...lay.problems, ...errors.map((e) => `화면 오류: ${e}`)];
          shots[name] = { hash: await fingerprint(helper, png), top: lay.top, height: lay.height, small: lay.small, problems };
          if (problems.length) red.push(`${name}: ${problems.join(" · ")}`);
          if (lay.small) small.push(`${name} ${lay.small}`);
        }
        await ctx.close();
      }
    }
  } finally {
    await browser.close();
    server?.child.kill();
  }
  fs.writeFileSync(path.join(out, "shots.json"), JSON.stringify({ at: new Date().toISOString(), shots }, null, 1));
  // 지난 사진과 견주기(실패 아님).
  const prevFile = arg("prev");
  const lines = [`## 관리자 앱 화면 사진 (${Object.keys(shots).length}장)`];
  if (prevFile && prevFile !== "-" && fs.existsSync(prevFile)) {
    const prev = JSON.parse(fs.readFileSync(prevFile, "utf8")).shots ?? {};
    const changed = Object.entries(shots).map(([k, v]) => [k, distance(v.hash, prev[k]?.hash)]).filter(([, d]) => d > 0.08).sort((a, b) => b[1] - a[1]);
    fs.mkdirSync(path.join(out, "changed"), { recursive: true });
    for (const [k] of changed) fs.copyFileSync(path.join(out, "png", `${k}.png`), path.join(out, "changed", `${k}.png`));
    lines.push(changed.length ? `지난 사진과 크게 달라진 화면 ${changed.length}장(사진은 changed/): ${changed.slice(0, 20).map(([k, d]) => `${k} ${Math.round(d * 100)}%`).join(", ")}`
      : "지난 사진과 크게 달라진 화면이 없어요.");
  } else lines.push("지난 사진이 없어 이번 지문만 남겨요.");
  if (small.length) lines.push(`휴대폰 폭에서 44px보다 낮은 누르는 곳(조건 9, 참고): ${small.join(", ")}`);
  lines.push(red.length ? `### 배치 문제 ${red.length}\n${red.map((x) => `- ${x}`).join("\n")}` : "배치 문제 없음(넘침·빈 본문·화면 오류·첫 줄 위치).");
  console.log(lines.join("\n\n"));
  process.exitCode = red.length ? 1 : 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error(e);
    process.exitCode = 2;
  });
}
