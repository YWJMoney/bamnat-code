// 1.38 [STM-09] 1.36 장터 작품 정리: 1.36 점검표(기존작품-점검표)의 '다듬기' 테마·탭 작품에 밤낮 배경과 색 아이콘을 얹어 같은 제목의 새 판으로,
// '내림'과 새 알람 25로 바뀐 옛 알람은 내릴 목록으로. 규칙·부품의 '다듬기'는 보기 점수(규칙은 0점 고정)만 낮았던 것이라 그대로 둬요.
//   node docs/recipes/market-138-studio/make/refresh.mjs
//   → docs/recipes/market-138-refresh/works.json (같은 제목 새 판). 다음은 `node tools/market-seed.mjs --set 138r`(minApp 없음: 1.36 앱도 ph·그림 꾸밈을 읽어요).
//   → docs/recipes/market-138-refresh-plan.json (내림·새 판·그대로 목록)
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { PALETTE, use } from "./mat.mjs";
import { JUDGE } from "./refresh-judge.mjs";

const RECIPES = fileURLToPath(new URL("../..", import.meta.url));
const old = JSON.parse(readFileSync(RECIPES + "market-136.json", "utf8"));

// 표지 그림 글자 → 어울리는 밤낮 아이콘(없으면 구역 기본).
const BY_EMOJI = {
  "😴": "zzz", "🛌": "bed", "🌅": "sunrise", "🔥": "trophy", "🌡️": "chartup", "🗒️": "notebook", "💝": "hearts", "🎯": "flag", "📏": "calendar",
  "📆": "calendar", "🎉": "fireworks", "🎨": "palette", "✍️": "pencil", "🖌️": "brush", "💡": "bulb", "🪙": "coin", "🦺": "hardhat", "🧳": "suitcase",
  "🏆": "trophy", "📒": "notebook", "📈": "chartup", "🌱": "sprout", "🏷️": "pricetag", "📌": "pin", "🩺": "stethoscope", "📝": "pencil",
  "☑️": "checkcircle", "🎓": "gradcap", "🟨": "notebook", "❗": "bell", "📍": "pin", "💰": "moneybag", "🧱": "hardhat", "🛵": "scooter",
  "⏱️": "stopwatch", "🖤": "moon", "🧁": "cupcake", "🌧️": "rain", "📅": "calendar", "🛑": "bell", "⏳": "hourglass", "🎬": "film", "🚚": "truck",
  "💼": "briefcase", "🪚": "hammer", "◻️": "clipboard", "🐷": "piggy", "🧸": "bear", "🫧": "sparkles", "👑": "crown", "🕰️": "clock",
  "🌗": "daynight", "🍂": "fallenleaves", "🗞️": "book", "🌲": "pine", "🌊": "wave", "📊": "chartup", "🥐": "croissant", "📡": "receipt",
  "☕": "coffee", "📸": "camera", "📦": "box", "📓": "notebook", "💗": "heartbeat", "📚": "bookstack", "⚫": "coin", "🤍": "sun",
  "🗂️": "clipboard", "🏖️": "plane", "🌸": "blossom", "⏰": "alarm", "✏️": "pencil", "🔀": "wallet", "🏪": "store", "🔁": "calcheck",
  "🧾": "receipt", "🧮": "calculator", "🧰": "wrench", "🗺️": "mappin", "📜": "book", "🦉": "owl", "💅": "sparkles",
};
const TAB_AT = { "기능·달력": "cal.week", "기능·메모": "bg.memo", "기능·가계부": "bg.money", "기능·근무급여": "bg.stats", "기능·위젯": "widget" };

const hex = (s) => [1, 3, 5].map((i) => parseInt(s.slice(i, i + 2), 16));
const argb = (s) => hex("#" + s.slice(2));
const dist = (a, b) => a.reduce((n, v, i) => n + (v - b[i]) ** 2, 0);
const lum = ([r, g, b]) => (0.299 * r + 0.587 * g + 0.114 * b) / 255;
const taken = new Map();
/** The 밤낮 배경 nearest the work's own screen colour, light with light and dark with dark; one 배경 used at most three times. */
function nearest(bg, accent) {
  const dark = lum(bg) < 0.45;
  const pick = Object.entries(PALETTE).filter(([, p]) => p.dark === dark && (taken.get(p.id) ?? 0) < 3)
    .map(([key, p]) => [key, dist(hex(p.bg), bg) + 0.6 * dist(hex(p.accent), accent ?? bg)])
    .sort((a, b) => a[1] - b[1])[0][0];
  taken.set(PALETTE[pick].id, (taken.get(PALETTE[pick].id) ?? 0) + 1);
  return pick;
}

function withPhoto(screens, key) {
  const dark = PALETTE[key].dark;
  return screens.split("/").map((seg) => {
    if (/\|ph:|~ph:/.test(seg)) return seg;
    const [name, rest] = seg.split("~");
    const dim = name === "alarm" ? 60 : dark ? 15 : 0;
    // 사진 위에서 카드가 비치게: 불투명 카드는 그대로 두고 바탕만 사진으로.
    return `${name}~${rest.replace(/^(bg:[0-9A-F]{8})/, `$1|ph:${use(key)}|dm:${dim}`)}${rest.startsWith("bg:") ? "" : `|ph:${use(key)}|dm:${dim}`}`;
  }).join("/");
}

const hide = [], refresh = [], keep = [], entries = [];
for (const x of old) {
  const r = structuredClone(x.recipe);
  const j = JUDGE[r.title];
  if (!j) { hide.push({ title: r.title, sec: "알람", why: "새 알람 작품 25개로 바꿔요(근무 종류별 분·끄기 방식이 더 고르게)" }); continue; }
  if (j.judge === "내림") { hide.push({ title: r.title, sec: j.sec, why: `1.36 점검 내림(보기 ${j.look}·쓸모 ${j.inter})` }); continue; }
  if (j.judge === "유지" || j.sec.startsWith("규칙") || j.sec.startsWith("부품")) {
    keep.push({ title: r.title, sec: j.sec, why: j.judge === "유지" ? "1.36 점검 유지" : "보기 점수만 낮았던 규칙·부품, 하는 일은 그대로 좋아요" });
    continue;
  }
  const sl = r.pieces.find((p) => p.kind === "screenLook");
  const first = sl ? sl.screens.split("/")[0] : null;
  const bg = first?.match(/bg:([0-9A-F]{8})/)?.[1];
  const ac = first?.match(/ac:([0-9A-F]{8})/)?.[1];
  const tone = r.cover?.a ? [(r.cover.a >> 16) & 255, (r.cover.a >> 8) & 255, r.cover.a & 255] : [240, 240, 245];
  const key = nearest(bg ? argb(bg) : tone, ac ? argb(ac) : tone);
  const p = PALETTE[key];
  if (sl) sl.screens = withPhoto(sl.screens, key);
  else if (j.sec.startsWith("테마")) {
    const H = (h) => "FF" + h.slice(1).toUpperCase();
    r.pieces.unshift({ kind: "screenLook", screens: `all~bg:${H(p.bg)}|ph:${use(key)}|dm:${p.dark ? 15 : 0}|cd:${H(p.card)}|ik:${H(p.ink)}|ac:${H(p.accent)}` });
  }
  const at = TAB_AT[j.sec] ?? "cal.week";
  const icon = BY_EMOJI[r.cover?.emoji] ?? (at === "widget" ? "calendar" : "star");
  const item = at === "cal.week" ? { photo: use(icon), at, x: 571, y: 500, w: 18, h: 18 }
    : at === "widget" ? { photo: use(icon), at, x: 905, y: 140, w: 22, h: 22 }
    : { photo: use(icon), at, x: 880, y: 70, w: 52, h: 52, rot: 8 };
  const dec = r.pieces.find((q) => q.kind === "decor");
  if (dec) dec.items = [...dec.items, item].slice(0, 40);
  else r.pieces.push({ kind: "decor", items: [item] });
  if (r.desc && !r.desc.includes("밤낮 배경")) r.desc = r.desc.replace(/\n적용하면 바뀌는 것:\n/, `\n적용하면 바뀌는 것:\n· 바탕에 밤낮 배경 '${p.name}'과 색 아이콘\n`);
  refresh.push({ title: r.title, sec: j.sec, bg: `${key} ${p.name}`, icon });
  entries.push({ recipe: r, ...(x.showcase ? { showcase: true } : {}) });
}

mkdirSync(RECIPES + "market-138-refresh", { recursive: true });
writeFileSync(RECIPES + "market-138-refresh/works.json", JSON.stringify(entries, null, 1) + "\n");
writeFileSync(RECIPES + "market-138-refresh-plan.json", JSON.stringify({ hide, refresh, keep }, null, 1) + "\n");
console.log(`1.36 작품 ${old.length}: 내림 ${hide.length} · 새 판 ${refresh.length} · 그대로 ${keep.length}`);
