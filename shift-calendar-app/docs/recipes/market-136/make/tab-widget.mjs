// 기능별 · 위젯 22 (기획서 4-7). 홈 화면 위젯 하나만 바꿔요: 위젯 판(바탕·투명도·모서리·글자 크기·오늘 메모 줄), 위젯 부품(머리·칸)과 꾸밈 층.
// 위젯은 런처가 그리니(RemoteViews) 부품은 채우기·모서리·여백·글자만 그려요(docs/blueprint-format.md 8-1항). 수식(when)은 위젯 부품에서 아직 계산하지 않아서,
// 위젯 작품은 그날 근무 종류·오늘·주말·공휴일 상태로 바뀌어요(달력과 같은 상태 줄). 상태 줄이 이 구간의 '데이터로 움직이는 것'이에요.
import { c, ALL, recipe, parts, decor, widgetSticker } from "./lib.mjs";

const NOTE = "홈 화면 위젯 모양만 바뀌어요. 위젯은 다음에 새로 그려질 때(자정, 근무를 바꿀 때) 바뀌어요";

function widget(s) {
  const p = { bg: "#FFFFFF", ink: "#222222", accent: "#3F51B5", alpha: 92, r: 18, ...s.p };
  const roles = s.roles ?? {};
  const lines = [
    { id: "widget", fill: c(p.bg), fillA: p.alpha, r: p.r, ...(p.font ? { font: p.font } : {}), pad: p.pad ?? 8 },
    { id: "widget.head", ...(p.headFill ? { fill: c(p.headFill), r: Math.max(6, p.r - 6), pad: 4 } : {}), ink: c(p.headInk ?? p.ink), weight: 700,
      size: p.headSize ?? 14, ...(p.headFont ? { font: p.headFont } : {}), talign: p.headAlign ?? "start" },
    { id: "widget.cell", r: p.cellR ?? 8, ink: c(p.ink) },
    { id: "widget.cell", on: "today", fill: c(p.accent), ink: c(p.onAccent ?? "#FFFFFF"), weight: 800 },
    { id: "widget.cell", on: "sun", ink: c(p.sun ?? "#E53935") },
    { id: "widget.cell", on: "sat", ink: c(p.sat ?? "#1E88E5") },
    { id: "widget.cell", on: "dim", alpha: 40 },
    ...Object.entries(roles).map(([role, v]) => ({ id: "widget.cell", on: `role.${role}`, ...(typeof v === "string" ? { fill: c(v), fillA: 35 } : v) })),
    ...(s.holiday ? [{ id: "widget.cell", on: "holiday", ink: c(s.holiday), deco: "under", weight: 700 }] : []),
    ...(s.parts ?? []),
  ];
  const w = { kind: "widget", alpha: p.alpha, theme: p.dark ? "dark" : "light", memo: s.memo ?? true, bg: c(p.bg), ink: c(p.ink), corner: Math.min(28, p.r), text: s.text ?? "m" };
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? NOTE, styles: s.styles ?? ALL, cover: s.cover,
      pieces: [w, parts(lines), s.decor?.length ? decor(s.decor) : null] }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

const SHIFT_ROLES = (day, eve, night, off) => ({ day, eve, night, off });
const shape = (shape, color, o = {}) => ({ shape, at: "widget", x: o.x ?? 900, y: o.y ?? 100, w: o.w ?? 60, h: o.h ?? 60, fill: c(color), fillA: o.fillA ?? 25,
  ...(o.rot ? { rot: o.rot } : {}) });
const text = (t, color, o = {}) => ({ text: t, at: "widget", x: o.x ?? 820, y: o.y ?? 90, w: o.w ?? 90, h: o.h ?? 20, ink: c(color), size: o.size ?? 10, weight: 700,
  ...(o.font ? { font: o.font } : {}) });

export default [
  widget({
    title: "오늘 근무 큰 글자", summary: "오늘 칸이 크고 굵게, 근무 종류마다 칸 색이 달라요",
    wow: "위젯을 흘끗 봐도 오늘 근무가 보이게 오늘 칸을 굵은 큰 글자와 진한 색으로 채웠어요. 주간·저녁·야간·쉬는 날 칸이 저마다 다른 색이에요.",
    changes: ["오늘 칸 큰 글자", "근무 종류별 칸 색", "글자 크게", "오늘 메모 한 줄"], who: "위젯으로 근무를 확인하는 분", cover: ["#3F51B5", "#C5CAE9", "📆"],
    p: { accent: "#3F51B5" }, text: "l", roles: SHIFT_ROLES("#FFE082", "#FFAB91", "#9FA8DA", "#C8E6C9"),
    parts: [{ id: "widget.cell", on: "today", fill: c("#3F51B5"), ink: c("#FFFFFF"), weight: 900, size: 16 }], showcase: true,
  }),
  widget({
    title: "유리 위젯", summary: "반투명 유리판 위젯, 야간 날은 보랏빛",
    wow: "배경 화면이 비치는 반투명 유리판이에요. 바탕은 60%만 덮고, 야간 날 칸만 보랏빛으로 물들어요.",
    changes: ["투명도 60%", "둥근 모서리 28", "야간 칸 보랏빛"], who: "배경 화면을 살리고 싶은 분", cover: ["#E3F2FD", "#B39DDB", "🫧"],
    p: { bg: "#FFFFFF", alpha: 60, r: 28, accent: "#7E57C2" }, roles: { night: "#B39DDB", off: "#E0F2F1" },
    decor: [shape("circle", "#B39DDB", { x: 880, y: 120, w: 90, h: 90, fillA: 20 })],
  }),
  widget({
    title: "네온 위젯", summary: "검은 바탕 네온 글자, 오늘은 청록으로 빛나요",
    wow: "까만 위젯에 청록·분홍 네온 글자를 얹었어요. 오늘 칸은 청록, 야간 날은 분홍 글자예요.",
    changes: ["검은 바탕", "오늘 청록", "야간 분홍 글자"], who: "어두운 배경 화면을 쓰는 분", cover: ["#000000", "#00E5FF", "🌃"],
    p: { dark: true, bg: "#0A0A12", ink: "#E0F7FA", accent: "#00E5FF", onAccent: "#000000", headInk: "#FF4081" },
    roles: { night: { ink: c("#FF4081"), weight: 800 }, off: { ink: c("#69F0AE") } },
  }),
  widget({
    title: "종이 노트 위젯", summary: "누런 종이색에 손글씨, 쉬는 날은 연필 밑줄",
    wow: "종이 수첩을 붙여 둔 것 같은 위젯이에요. 손글씨 글꼴에 쉬는 날은 연필 밑줄이 그어져요.",
    changes: ["종이색 바탕", "손글씨 글꼴", "쉬는 날 밑줄"], who: "아날로그 다이어리 느낌을 좋아하는 분", cover: ["#FFF8E1", "#8D6E63", "📒"],
    p: { bg: "#FBF3DF", ink: "#4E342E", accent: "#8D6E63", font: "nanumpen", r: 6 }, roles: { off: { deco: "under", ink: c("#6D4C41") } },
    decor: [text("메모", "#8D6E63", { font: "nanumpen" })],
  }),
  widget({
    title: "파스텔 위젯", summary: "근무마다 파스텔 칸, 오늘은 딸기우유 분홍",
    wow: "주간 노랑, 저녁 살구, 야간 라벤더, 쉬는 날 민트, 모든 칸이 파스텔이에요. 오늘은 딸기우유 분홍으로 동그랗게.",
    changes: ["근무별 파스텔 칸", "오늘 분홍", "둥근 칸"], who: "귀여운 홈 화면을 원하는 분", cover: ["#F8BBD0", "#B2EBF2", "🍬"],
    p: { bg: "#FFFAFC", ink: "#5D4037", accent: "#F48FB1", cellR: 14 }, roles: SHIFT_ROLES("#FFF59D", "#FFCCBC", "#D1C4E9", "#B2DFDB"),
    decor: [widgetSticker("🎀")],
  }),
  widget({
    title: "다크 금색 위젯", summary: "검정 위에 금색 글자, 공휴일은 금 밑줄",
    wow: "까만 바탕에 금색 글자를 얹은 고급스러운 위젯이에요. 오늘은 금색 칸, 공휴일은 금 밑줄이에요.",
    changes: ["검정·금색", "오늘 금색 칸", "공휴일 금 밑줄", "세리프 머리 글자"], who: "차분하고 고급스러운 화면을 좋아하는 분", cover: ["#111111", "#D4AF37", "✨"],
    p: { dark: true, bg: "#111111", ink: "#F5E6B8", accent: "#D4AF37", onAccent: "#111111", headFont: "playfair", headInk: "#D4AF37" }, holiday: "#D4AF37",
    roles: { off: { ink: c("#8C7A4A") } }, showcase: true,
  }),
  widget({
    title: "미니멀 흑백 위젯", summary: "선 하나 없는 흑백, 쉬는 날만 흐리게",
    wow: "아무것도 꾸미지 않은 흑백 위젯이에요. 일하는 날은 진하게, 쉬는 날은 흐리게만 바뀌어요.",
    changes: ["흑백", "쉬는 날 흐리게", "오늘 검정 칸"], who: "깔끔한 홈 화면을 원하는 분", cover: ["#FFFFFF", "#000000", "◻️"],
    p: { bg: "#FFFFFF", ink: "#111111", accent: "#111111", r: 4 }, roles: { off: { alpha: 45 } }, memo: false,
  }),
  widget({
    title: "스티커 위젯", summary: "곰돌이 스티커와 쉬는 날 하트",
    wow: "위젯 귀퉁이에 곰돌이가 앉아 있고, 쉬는 날 칸은 분홍으로 물들어요. 오늘은 주황이에요.",
    changes: ["곰돌이·별 스티커", "쉬는 날 분홍", "오늘 주황"], who: "귀여운 것을 좋아하는 분", cover: ["#FFE0B2", "#F48FB1", "🧸"],
    p: { bg: "#FFF8F0", ink: "#5D4037", accent: "#FB8C00", font: "jua" }, roles: { off: "#F8BBD0" },
    decor: [widgetSticker("🧸", { x: 920, y: 140, w: 22, h: 22 }), widgetSticker("⭐", { x: 80, y: 900, w: 14, h: 14 })],
  }),
  widget({
    title: "야간 주간 한눈에", styles: ["SHIFT"], summary: "야간 칸은 남색 바탕 흰 글자, 주간은 햇빛 노랑",
    wow: "위젯 달력에서 야간 날은 밤하늘 남색 칸에 흰 글자, 주간은 햇빛 노랑이라 이번 달 리듬이 한눈에 보여요.",
    changes: ["야간 남색 칸", "주간 노랑 칸", "저녁 주황 칸"], who: "3교대 근무자", cover: ["#1A237E", "#FFD54F", "🌗"],
    p: { accent: "#E53935" }, roles: { night: { fill: c("#1A237E"), ink: c("#FFFFFF") }, day: { fill: c("#FFE082") }, eve: { fill: c("#FFAB91") } },
  }),
  widget({
    title: "이번 주 근무 띠", summary: "머리 띠가 진한 색으로, 오늘은 굵은 테두리 대신 진한 칸",
    wow: "위젯 머리를 진한 띠로 채워 '이번 주 근무'가 제목처럼 서고, 아래 칸은 근무 종류마다 연한 색이에요.",
    changes: ["머리 띠", "근무별 연한 칸", "오늘 진한 칸"], who: "주 위젯을 쓰는 분", cover: ["#00796B", "#B2DFDB", "📊"],
    p: { accent: "#00796B", headFill: "#00796B", headInk: "#FFFFFF", headAlign: "center" }, roles: SHIFT_ROLES("#B2DFDB", "#FFE0B2", "#C5CAE9", "#F5F5F5"),
  }),
  widget({
    title: "일근 출근 위젯", styles: ["DAY"], summary: "출근 날 파랑, 재택 날 초록, 당직 날 빨강",
    wow: "출근·재택·당직이 위젯에서 색으로 나뉘어요. 이번 주 재택이 며칠인지 위젯만 봐도 알아요.",
    changes: ["출근 파랑", "재택 초록", "당직 빨강 글자"], who: "출근과 재택이 섞인 일근", cover: ["#1E88E5", "#43A047", "💼"],
    p: { accent: "#1565C0" }, roles: { day: "#90CAF9", other: "#A5D6A7", long: { ink: c("#C62828"), weight: 800 }, off: { alpha: 50 } },
  }),
  widget({
    title: "알바 근무 날 콕", styles: ["HOURLY"], summary: "알바 가는 날만 노란 칸, 나머지는 흐리게",
    wow: "알바 가는 날만 노란 칸으로 콕 찍히고, 쉬는 날은 흐리게 물러나요. 이번 달 몇 번 가는지 바로 보여요.",
    changes: ["근무 날 노란 칸", "쉬는 날 흐리게", "레몬 바탕"], who: "주 몇 번 알바하는 분", cover: ["#FFF59D", "#F9A825", "🍋"],
    p: { bg: "#FFFDE7", ink: "#5D4037", accent: "#F9A825" }, roles: { day: "#FFF176", eve: "#FFE082", night: "#FFD54F", off: { alpha: 40 } },
  }),
  widget({
    title: "현장 출근 도장", styles: ["FIELD"], summary: "일한 날 칸에 주황 도장, 쉬는 날은 회색",
    wow: "현장에 나간 날마다 칸이 안전 주황으로 찍혀요. 이번 달 몇 날 나갔는지 위젯에서 세어 봐요.",
    changes: ["일한 날 주황 칸", "쉬는 날 회색", "안전모 스티커"], who: "일당으로 일하는 현장 근무자", cover: ["#FF6F00", "#FFE0B2", "🦺"],
    p: { bg: "#FFF8F0", ink: "#3E2723", accent: "#FF6F00" }, roles: { day: "#FFB74D", long: "#FF8A65", off: { alpha: 45 } },
    decor: [widgetSticker("⛑️")],
  }),
  widget({
    title: "가게 영업일 위젯", styles: ["BUSINESS"], summary: "문 연 날 초록, 쉬는 날 회색, 오늘은 가게 색",
    wow: "가게를 연 날과 쉬는 날이 위젯에서 색으로 나뉘어요. 오늘 칸은 가게 간판처럼 진한 초록이에요.",
    changes: ["영업일 초록", "휴무 회색", "간판 초록 오늘"], who: "가게를 하는 분", cover: ["#2E7D32", "#C8E6C9", "🏪"],
    p: { bg: "#F6FBF6", ink: "#1B5E20", accent: "#2E7D32" }, roles: { day: "#C8E6C9", long: "#A5D6A7", off: { alpha: 40 } }, holiday: "#C62828",
  }),
  widget({
    title: "투자 장 쉬는 날", styles: ["INVEST"], summary: "주말·공휴일(장 쉬는 날)은 흐리게, 평일은 또렷하게",
    wow: "장이 열리는 평일은 또렷하게, 주말과 공휴일은 흐리게 보여요. 이번 달 거래할 수 있는 날이 한눈에 들어와요.",
    changes: ["주말·공휴일 흐리게", "차분한 남색", "오늘 진한 칸"], who: "장 열리는 날을 챙기는 투자자", cover: ["#263238", "#80CBC4", "📈"],
    p: { dark: true, bg: "#14202B", ink: "#E0F2F1", accent: "#26A69A" },
    parts: [{ id: "widget.cell", on: "sat", alpha: 40 }, { id: "widget.cell", on: "sun", alpha: 40 }, { id: "widget.cell", on: "holiday", alpha: 40 }],
  }),
  widget({
    title: "공휴일 빨간 날", summary: "공휴일이 빨간 밑줄, 공휴일에 일하면 금색 칸",
    wow: "공휴일은 빨간 밑줄로, 공휴일에 근무가 잡힌 날은 금색 칸으로 보여 휴일 근무를 놓치지 않아요.",
    changes: ["공휴일 빨간 밑줄", "주말 색", "오늘 진한 빨강"], who: "공휴일 근무가 잦은 분", cover: ["#E53935", "#FFD54F", "🎌"],
    p: { accent: "#E53935" }, holiday: "#E53935", roles: { day: "#FFF8E1", eve: "#FFF8E1", night: "#FFF8E1", long: "#FFF8E1" },
  }),
  widget({
    title: "바다 위젯", summary: "파도 파랑 바탕, 쉬는 날은 모래색",
    wow: "파란 바다 같은 위젯에 쉬는 날은 모래색 칸이 떠 있어요. 오늘은 흰 물거품 칸이에요.",
    changes: ["바다 파랑 바탕", "쉬는 날 모래색", "오늘 흰 칸"], who: "시원한 색을 좋아하는 분", cover: ["#0277BD", "#FFE0B2", "🌊"],
    p: { dark: true, bg: "#01579B", ink: "#E1F5FE", accent: "#FFFFFF", onAccent: "#01579B" }, roles: { off: { fill: c("#FFE0B2"), fillA: 60, ink: c("#4E342E") } },
    decor: [widgetSticker("🐚")],
  }),
  widget({
    title: "숲 위젯", summary: "초록 숲 바탕, 일하는 날은 잎 색이 진해져요",
    wow: "초록 위젯에 일하는 날은 짙은 잎, 쉬는 날은 연두로 보여요. 오늘은 햇살 노랑이에요.",
    changes: ["숲 초록 바탕", "근무 날 짙은 초록", "오늘 햇살 노랑"], who: "자연 색을 좋아하는 분", cover: ["#1B5E20", "#DCEDC8", "🌲"],
    p: { bg: "#F1F8E9", ink: "#1B5E20", accent: "#FBC02D", onAccent: "#1B5E20" }, roles: { day: "#AED581", eve: "#9CCC65", night: "#689F38", long: "#7CB342", off: "#DCEDC8" },
  }),
  widget({
    title: "레트로 픽셀 위젯", summary: "픽셀 글꼴과 초록 화면, 야간은 깜빡이는 색",
    wow: "옛날 게임기 화면 같은 초록 픽셀 위젯이에요. 야간 날은 밝은 초록, 쉬는 날은 어두운 초록이에요.",
    changes: ["픽셀 글꼴", "게임기 초록", "야간 밝은 초록"], who: "레트로 감성을 좋아하는 분", cover: ["#0F380F", "#9BBC0F", "👾"],
    p: { dark: true, bg: "#0F380F", ink: "#9BBC0F", accent: "#9BBC0F", onAccent: "#0F380F", font: "vt323", r: 2, cellR: 0 },
    roles: { night: { ink: c("#E0F8D0"), weight: 800 }, off: { alpha: 50 } },
  }),
  widget({
    title: "세리프 달력 위젯", summary: "명조 머리 글자와 얇은 글자, 주말만 색",
    wow: "잡지 같은 명조 머리 글자에 칸 글자는 가늘게, 주말과 공휴일에만 색이 들어가요.",
    changes: ["명조 머리", "얇은 칸 글자", "주말·공휴일 색"], who: "단정한 글꼴을 좋아하는 분", cover: ["#FAFAFA", "#37474F", "🖋️"],
    p: { bg: "#FAFAF7", ink: "#263238", accent: "#37474F", headFont: "nanummyeongjo", headSize: 16 }, holiday: "#C62828",
    parts: [{ id: "widget.cell", weight: 300 }],
  }),
  widget({
    title: "큰 글자 편한 위젯", summary: "글자 크게, 바탕은 불투명, 오늘은 노란 칸",
    wow: "멀리서도 보이도록 글자를 크게 키우고 바탕을 완전히 덮었어요. 오늘은 노란 칸에 검은 글자예요.",
    changes: ["글자 크게", "불투명 바탕", "오늘 노란 칸"], who: "작은 글자가 불편한 분", cover: ["#FFEB3B", "#212121", "🔍"],
    p: { bg: "#FFFFFF", ink: "#000000", accent: "#FFEB3B", onAccent: "#000000", alpha: 100, headSize: 18 }, text: "l", roles: { off: { ink: c("#2E7D32") } },
  }),
  widget({
    title: "노을 위젯", summary: "노을 주황 바탕, 저녁 근무 날은 보랏빛",
    wow: "해 질 녘 하늘 같은 주황 위젯이에요. 저녁 근무 날은 보랏빛, 야간은 남색으로 저물어요.",
    changes: ["노을 바탕", "저녁 보라", "야간 남색"], who: "따뜻한 색을 좋아하는 분", cover: ["#FF7043", "#7E57C2", "🌇"],
    p: { bg: "#FFF3E0", ink: "#4E342E", accent: "#FF7043" }, roles: { eve: "#CE93D8", night: "#9FA8DA", day: "#FFCC80" },
    decor: [shape("circle", "#FF7043", { x: 900, y: 900, w: 120, h: 120, fillA: 18 })],
  }),
];
