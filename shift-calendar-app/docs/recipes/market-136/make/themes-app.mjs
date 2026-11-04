// 테마 · 앱 전체 20 (기획서 4-7-1). 앱 색·아래 탭·모서리·느낌을 한 벌로 바꿔요. 어느 유형에나 맞는 말만 써요(근무·급여 말 없음).
import { c, ALL, recipe, screenLook, calTheme, memoLook, moneyLook, widget, appFeel, baseParts, baseTokens, parts, decor, logic, memoTpl, cellSticker, headText, headShape, bgShape } from "./lib.mjs";
import { C, R, T, B } from "./kit.mjs";
import { calView } from "./build-theme.mjs";

const NOTE = "기록은 그대로예요. 규칙은 넣기 전에 한 번 묻고, 스튜디오 › 규칙·단추에서 끄거나 되돌릴 수 있어요";

function app(s) {
  const p = s.p;
  const pieces = [
    screenLook(p, null, s.screens), calView(p, s.cal), calTheme(p, s.cell ?? "tint"), memoLook(p, s.memo), moneyLook(p, s.money), widget(p, s.widget),
    appFeel(p, s.feel), parts([...baseParts(p, s.base), ...(s.app ?? []), ...(s.cells ?? [])]), s.decor?.length ? decor(s.decor) : null,
    s.rules?.length || s.buttons?.length ? logic({ rules: s.rules, buttons: s.buttons }) : null, s.tpls?.length ? memoTpl(s.tpls) : null, ...(s.extra ?? []),
  ];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who ?? "어느 일을 하든 앱 전체 느낌을 한 번에 바꾸고 싶은 분", changes: s.changes, note: s.note ?? NOTE,
      styles: s.styles ?? ALL, cover: s.cover, tokens: baseTokens(p), inputs: s.inputs, pieces }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

const SUMMARY_RULE = { name: "앱 열면 오늘 요약", on: "open", do: "notify", arg: "{월}월 {일}일 {요일}요일 · 메모 {메모수}개 · 이달 쓴 돈 {원(이달지출)}" };

export default [
  app({
    title: "한 색 모노크롬", summary: "고른 한 색으로 앱 전체를 칠하고, 체크를 끝낸 날만 진해지는 판",
    wow: "받을 때 고른 색 하나로 바탕·카드·단추·달력 머리가 모두 칠해져요. 그날 체크 목록을 다 끝낸 날은 칸이 그 색으로 진해져요.",
    changes: ["한 색 계열 바탕·카드·단추", "체크율 테두리와 끝낸 날 진한 칸", "위젯도 같은 색"], cover: ["#5E35B1", "#B39DDB", "🎨"],
    p: { bg: "#F6F3FC", card: "#FFFFFF", ink: "#2A1F45", accent: "#5E35B1", line: "#E4DCF4", headPaint: "lg.135.FF5E35B1,FF9575CD", headInk: "#FFFFFF", titleFont: "poppins", r: 18 },
    cells: [C.checkRing(), { id: "cal.cell", when: "체크율 >= 100", fill: c("#5E35B1"), fillA: 25 }],
    app: [{ id: "app.button", fill: { input: "i1" } }, { id: "app.tab", on: "sel", fill: { input: "i1" }, fillA: 15, r: 16 }],
    inputs: [{ id: "i1", label: "내 색", kind: "color", default: c("#5E35B1") }],
  }),
  app({
    title: "유리 앱", showcase: true, summary: "모든 카드와 탭 막대가 반투명 유리, 오늘 칸은 시각마다 색이 바뀌어요",
    wow: "탭 막대·카드·달력 머리가 모두 뒤가 비치는 유리예요. 오늘 칸은 지금 시각에 따라 새벽·아침·낮·저녁 색으로 바뀌어요.",
    changes: ["하늘 그라데이션 바탕과 유리 카드", "오늘 칸 시각 색", "부드러운 넘김 움직임"], cover: ["#81D4FA", "#E1BEE7", "🫧"],
    p: { bg: "#EAF4FF", card: "#FFFFFF", ink: "#1C2B3A", accent: "#3F7CF0", line: "#D5E4F7", bgPaint: "lg.160.FFD6EBFF,FFF3E5F5", headPaint: "gl.66FFFFFF.24", cellPaint: "gl.40FFFFFF.10",
      titleFont: "quicksand", r: 22, cellR: 14 },
    app: [{ id: "app.tabs", paint: "gl.80FFFFFF.24", r: 24, out: 8 }, { id: "app.card", paint: "gl.99FFFFFF.20" }, { id: "app.sheet", paint: "gl.CCFFFFFF.24", r: 28 }],
    cells: [C.todayClock()], feel: { motion: "tn:fade|td:glow|wr:circle|sp:s" },
  }),
  app({
    title: "다크 골드", summary: "검정과 금, 돈 들어온 날은 금빛이 번지는 고급 다크",
    wow: "검은 바탕에 금색 선과 세리프 제목, 가계부에 돈이 들어온 날 달력 칸은 금빛이 번져요. 쓴 돈이 많은 날은 금액이 굵게 남아요.",
    changes: ["검정·금 색", "돈 들어온 날 금빛, 큰 지출 굵게", "금색 단추·탭"], cover: ["#000000", "#C9A227", "👑"],
    p: { dark: true, bg: "#0B0B0B", card: "#171717", ink: "#F3EFE3", accent: "#C9A227", line: "#3A3220", headPaint: "lg.120.FF171717,FF2B2412", headInk: "#E6C97A", titleFont: "playfair", r: 12, cardLine: true, onAccent: "#0B0B0B" },
    cells: [C.incomeDay("#C9A227"), C.spendLine(50000)], cal: { spend: true },
  }),
  app({
    title: "말랑 파스텔", summary: "모든 모서리가 둥글고 말랑한 파스텔, 무지출 날엔 초록 날짜",
    wow: "카드·단추·탭이 모두 둥글고 말랑해요. 한 푼도 안 쓴 지난날은 날짜가 초록으로 바뀌어 무지출 날이 모여요.",
    changes: ["분홍·민트·하늘 파스텔", "큰 둥근 모서리", "무지출 날 초록 날짜"], cover: ["#F8BBD0", "#B2EBF2", "🍬"],
    p: { bg: "#FFF7FB", card: "#FFFFFF", ink: "#5A3E55", accent: "#F06292", line: "#F7DDE9", titleFont: "jua", memoFont: "jua", font: "jua", r: 26, cellR: 16, btnR: 30 },
    app: [{ id: "app.tabs", r: 28, out: 10, shadow: 4 }, { id: "app.chip", r: 24, fill: c("#FCE4EC") }], cells: [C.noSpend("#26A69A")],
    decor: [cellSticker("🍬", "today", { x: 180, y: 180 })],
  }),
  app({
    title: "네온 시티", summary: "검정 바탕에 네온 선, 바쁜 날은 칸이 숨 쉬는 밤거리",
    wow: "글자와 단추가 네온처럼 빛나고, 메모가 세 개 넘는 날 칸이 숨 쉬듯 깜빡여요. 마감 꼬리표 날은 남은 날에 따라 색이 바뀌어요.",
    changes: ["검정 바탕·분홍·하늘 네온", "바쁜 날 숨쉬기, 마감 날 D-day 색", "제목 빛 번짐"], cover: ["#0A0A12", "#00E5FF", "🌆"],
    p: { dark: true, bg: "#07070F", card: "#12121F", ink: "#E8E8FF", accent: "#00E5FF", line: "#272744", titleFont: "orbitron", titleShadow: "#00E5FF", headInk: "#7DF9FF", r: 12, onAccent: "#07070F" },
    app: [{ id: "app.bar.title", tshadow: 8, tshadowC: c("#FF2BD6"), tshadowX: 0, tshadowY: 0 }, { id: "app.button", line: "solid", lineW: 1.5, lineC: c("#FF2BD6"), motion: "glow" }],
    cells: [C.busyMemo(), C.dday("마감")],
  }),
  app({
    title: "종이 노트", summary: "종이 질감과 손글씨, 회의·약속 꼬리표 날엔 연필 점선",
    wow: "앱 전체가 종이 위에 쓴 것처럼 보여요. #약속 꼬리표 날엔 연필 점선, 메모가 많은 날은 칸이 숨 쉬어요.",
    changes: ["종이 질감 바탕과 손글씨", "약속 날 점선, 바쁜 날 숨쉬기", "포스트잇 메모장"], cover: ["#FFF8E1", "#A1887F", "📒"],
    p: { bg: "#FBF6EA", card: "#FFFDF6", ink: "#3E3A33", accent: "#8D6E63", line: "#E8DFC9", bgPaint: "tx.paper.FFFBF6EA.60", titleFont: "nanumpen", memoFont: "nanumpen", font: "nanumpen", r: 4, cellR: 2, shadow: 0, cardLine: true },
    cells: [C.tagLine("약속", "#8D6E63", "dot"), C.busyMemo()], memo: { layout: "sticky", corner: 2 },
  }),
  app({
    title: "미니멀 흑백", summary: "색을 다 빼고 글자만, 쓴 돈 많은 날만 날짜에 밑줄",
    wow: "흑백 두 색만 남기고, 그날 쓴 돈이 많으면 날짜에 밑줄 하나만 그어져요.",
    changes: ["흰 바탕·검은 글자", "그림자 없음, 얇은 선", "큰 지출 날 밑줄"], cover: ["#FFFFFF", "#000000", "🔲", 0],
    p: { bg: "#FFFFFF", card: "#FFFFFF", ink: "#000000", accent: "#000000", line: "#DADADA", titleFont: "notoserifkr", r: 0, cellR: 0, btnR: 0, shadow: 0, cardLine: true },
    cells: [{ id: "cal.cell.date", when: "지출 >= 50000", deco: "under", weight: 800 }],
  }),
  app({
    title: "둥근 큰 단추", summary: "단추·칩·탭이 크고 둥글어 누르기 쉬운 판, 오늘 할 일 단추까지",
    wow: "모든 단추가 손가락 크기로 크고 둥글어요. 달력 위 '오늘 할 일' 단추를 누르면 할 일 틀이 바로 열려요.",
    changes: ["큰 둥근 단추·칩", "탭 막대 높이 올림", "달력 위 할 일 단추"], cover: ["#FFAB91", "#FFE0B2", "🔘"],
    p: { bg: "#FFF8F4", card: "#FFFFFF", ink: "#3A2A22", accent: "#FF7043", line: "#F5DED2", titleFont: "fredoka", r: 22, btnR: 32 },
    app: [{ id: "app.button", padY: 14, padX: 24, size: 17 }, { id: "app.chip", padY: 10, padX: 16, r: 24 }, { id: "app.tabs", h: 72 }],
    buttons: [B("오늘 할 일", "✅", "cal", { do: "memoTpl", arg: "할 일" })], tpls: [T.todo], cells: [C.checkRing()],
  }),
  app({
    title: "근무 먼저 탭", styles: ["SHIFT", "DAY", "HOURLY"], summary: "아래 탭을 달력·근무·가계부·메모 순으로, 주 52시간 넘는 주는 밑줄",
    wow: "아래 탭을 근무 중심으로 다시 놓아요. 그 주 일한 시간이 52시간을 넘으면 그 주 날짜에 빨간 밑줄이 그어져요.",
    changes: ["아래 탭 순서: 달력·근무·가계부·메모", "근무 시간 열지도", "주 52시간 밑줄"], who: "근무표를 가장 자주 보는 분", cover: ["#263238", "#4FC3F7", "🧭"],
    p: { bg: "#F4F7F9", card: "#FFFFFF", ink: "#1F2A33", accent: "#0288D1", line: "#DCE4EA", titleFont: "ibmplexsanskr", r: 12 },
    cells: [C.hoursHeat("#0288D1"), C.week52()], extra: [{ kind: "layout", tabs: ["calendar", "stats", "money", "memos", "plaza", "settings"] }],
  }),
  app({
    title: "큰 글자 편한 보기", summary: "글자를 두 단계 키우고 줄 간격을 넓힌 눈 편한 판",
    wow: "앱 글자가 두 단계 커지고 줄 간격이 넓어져요. 오늘 칸은 굵은 테두리, 지난날은 흐려 오늘이 바로 찾아져요.",
    changes: ["글자 두 단계 크게", "넓은 줄 간격·높은 대비", "오늘 굵은 테두리, 지난날 흐리게"], who: "글자를 크게 보고 싶은 분", cover: ["#FFFFFF", "#1565C0", "🔎"],
    p: { bg: "#FFFFFF", card: "#F7F9FC", ink: "#0B1A2B", accent: "#1565C0", line: "#C9D6E6", titleFont: "notosanskr", titleSize: 26, r: 14 },
    app: [{ id: "app", leading: 150 }, { id: "cal.cell", on: "today", lineW: 3.5 }], cells: [{ id: "cal.cell", when: "지난날 그리고 아님 오늘", alpha: 60 }],
    feel: { textStep: 2, motion: "tn:none|td:none|sp:s" }, cal: { text: "large", size: "huge" },
  }),
  app({
    title: "한 손 모드", summary: "자주 누르는 것을 아래로, 날짜를 누르면 아래 창이 열리는 판",
    wow: "날짜를 누르면 아래에서 창이 올라와 엄지만으로 써요. 달력 머리는 낮고 탭 막대는 넓어요. 날짜를 길게 누르면 오늘 계획 틀이 열려요.",
    changes: ["날짜 누르면 아래 창", "낮은 머리, 넓은 탭 막대", "길게 눌러 오늘 계획"], cover: ["#E8F5E9", "#43A047", "👍"],
    p: { bg: "#F5FAF5", card: "#FFFFFF", ink: "#1E2D1F", accent: "#43A047", line: "#DCEBDD", titleFont: "rubik", titleSize: 18, r: 18 },
    app: [{ id: "app.tabs", h: 76, r: 24, out: 8 }, { id: "cal.head", padY: 4 }], rules: [R.holdTpl("오늘 계획")], tpls: [T.todayPlan],
    feel: { tap: "sheet", hold: "memo", motion: "tn:slide|sp:f" },
  }),
  app({
    title: "시간 따라 바뀌는 색", summary: "오늘 칸이 새벽·아침·낮·저녁 시각마다 다른 색으로",
    wow: "앱을 열 때마다 지금 시각을 읽어 오늘 칸이 새벽 남색 → 아침 금빛 → 낮 하늘 → 저녁 보라로 바뀌어요.",
    changes: ["시각에 따라 바뀌는 오늘 칸", "하늘 그라데이션 머리", "부드러운 움직임"], cover: ["#FFB74D", "#7E57C2", "🕰️"],
    p: { bg: "#FAF8FF", card: "#FFFFFF", ink: "#2A2340", accent: "#7E57C2", line: "#E6E0F5", headPaint: "lg.90.FFFFB74D,FF7E57C2", headInk: "#FFFFFF", titleFont: "montserrat", r: 18 },
    cells: [C.todayClock()],
  }),
  app({
    title: "근무 따라 바뀌는 색", styles: ["SHIFT", "DAY", "HOURLY"], summary: "야간 날은 칸이 어둡게, 쉬는 날은 밝게. 근무가 곧 색이 되는 판",
    wow: "야간 근무 날 칸은 밤하늘처럼 어두워지고, 쉬는 날은 햇살처럼 밝아져요. 세 번째 야간부터는 반짝임이 더해져요.",
    changes: ["야간 칸 밤하늘, 쉬는 날 햇살", "연속 야간 반짝임", "근무 끝 잠자리 알림(야간)"], who: "교대·일근·알바로 근무가 바뀌는 분", cover: ["#1A237E", "#FFF176", "🌗"],
    p: { bg: "#F7F8FC", card: "#FFFFFF", ink: "#1C2040", accent: "#3949AB", line: "#E0E3F2", titleFont: "montserrat", r: 16 },
    cells: [...C.nightDeep("lg.180.FF283593,FF1A237E", "sh.aurora.FF1A237E,FF651FFF,FF00BFA5.2"), { id: "cal.cell", when: "쉬는날", paint: "rg.50.50.120.FFFFF59D,00FFFFFF" }],
    rules: [R.nightSleep],
  }),
  app({
    title: "사계절", summary: "달마다 봄·여름·가을·겨울 색으로 칸이 물드는 판",
    wow: "3~5월은 벚꽃, 6~8월은 바다, 9~11월은 단풍, 12~2월은 눈 색으로 칸이 저절로 물들어요. 계절마다 테마를 바꿀 필요가 없어요.",
    changes: ["달에 따라 바뀌는 칸 색", "계절 스티커(1일)", "부드러운 파스텔"], cover: ["#F8BBD0", "#FFCC80", "🍂"],
    p: { bg: "#FFFDF9", card: "#FFFFFF", ink: "#3A332C", accent: "#EF6C00", line: "#F0E6DA", titleFont: "gowunbatang", r: 18 },
    cells: [{ id: "cal.cell", when: "참", fill: { fx: '고르기(내림((월 % 12) / 3), 0, "#E3F2FD", 1, "#FCE4EC", 2, "#E0F7FA", "#FFE0B2")' }, fillA: 70 }],
    decor: [cellSticker("🌸", "m4d1"), cellSticker("🌊", "m7d1"), cellSticker("🍁", "m10d1"), cellSticker("❄️", "m1d1")],
  }),
  app({
    title: "오늘 요약", summary: "하루 처음 앱을 열면 오늘 날짜·메모 수·이달 쓴 돈을 한 줄로",
    wow: "하루에 처음 앱을 열면 '11월 3일 월요일 · 메모 2개 · 이달 쓴 돈'이 알림 한 줄로 와요. 밤 9시까지 오늘 쓴 돈을 안 적었으면 한 번 더 알려 줘요.",
    changes: ["앱 열면 오늘 요약 알림", "밤 9시 가계부 알림", "깔끔한 청록 화면"], cover: ["#E0F2F1", "#00897B", "🗞️"],
    p: { bg: "#F4FAF9", card: "#FFFFFF", ink: "#17312E", accent: "#00897B", line: "#D7EBE8", titleFont: "notosanskr", r: 14 },
    rules: [SUMMARY_RULE, R.dailyMoney], cells: [C.spendHeat()],
  }),
  app({
    title: "스티커 테두리", summary: "주말·오늘·1일마다 스티커가 붙는 귀여운 앱",
    wow: "토요일엔 🎈, 일요일엔 🧸, 매달 1일엔 🎀, 오늘엔 ✨가 칸에 붙어요. 그날 체크를 다 끝내면 테두리가 초록으로 바뀌어요.",
    changes: ["요일·날짜 스티커", "체크율 테두리", "통통한 손글씨"], cover: ["#FFF59D", "#F48FB1", "🧸"],
    p: { bg: "#FFFDF4", card: "#FFFFFF", ink: "#4B3B2A", accent: "#FF8A65", line: "#F4E8D0", titleFont: "cutefont", memoFont: "cutefont", font: "cutefont", titleSize: 26, r: 24, cellR: 16 },
    decor: [cellSticker("🎈", "w6"), cellSticker("🧸", "w7"), cellSticker("🎀", "d1"), cellSticker("✨", "today", { x: 180, y: 180 })], cells: [C.checkRing()],
  }),
  app({
    title: "세리프 에디션", summary: "잡지처럼 큰 세리프 제목과 넓은 여백, 마감 날만 색이 도는 고급판",
    wow: "달 이름이 잡지 표지처럼 큰 세리프로 서고, 넓은 여백에 색은 거의 없어요. #마감 날만 남은 날에 따라 색이 돌아요.",
    changes: ["큰 세리프 제목과 넓은 여백", "아이보리·먹색", "마감 날 D-day 테두리"], cover: ["#F5F0E6", "#2B2B2B", "🖋️"],
    p: { bg: "#F7F3EA", card: "#FFFDF8", ink: "#1E1E1E", accent: "#7A5C3A", line: "#E4DCCB", titleFont: "abrilfatface", titleSize: 34, memoFont: "lora", r: 6, shadow: 0, cardLine: true },
    app: [{ id: "cal.head.title", track: 4, italic: true }], cells: [C.dday("마감")],
  }),
  app({
    title: "레트로 픽셀", summary: "8비트 픽셀 글꼴과 격자, 오늘 칸이 통통 튀는 게임 화면",
    wow: "달 이름과 날짜가 픽셀 글꼴로, 오늘 칸은 게임처럼 통통 튀어요. 체크를 다 끝낸 날은 초록 테두리가 '클리어'처럼 남아요.",
    changes: ["픽셀 글꼴·격자 무늬", "오늘 칸 튀는 움직임", "체크 다 끝낸 날 테두리"], cover: ["#1B1B3A", "#00E676", "👾"],
    p: { dark: true, bg: "#14142B", card: "#1F1F44", ink: "#E0FFE8", accent: "#00E676", line: "#34346A", bgPaint: "pt.grid.FF14142B.FF1F1F44.50.0", titleFont: "pressstart", titleSize: 16,
      dateFont: "silkscreen", font: "silkscreen", r: 0, cellR: 0, btnR: 0, todayMotion: "bounce", onAccent: "#14142B" },
    cells: [C.checkRing()],
  }),
  app({
    title: "숲속 초록", summary: "초록 숲 그라데이션, 무지출 날엔 새싹이 돋는 자연판",
    wow: "숲 그라데이션 머리에 이끼색 카드. 한 푼도 안 쓴 지난날은 날짜가 초록으로 바뀌고, 쓴 돈이 많은 날은 칸이 단풍색으로 물들어요.",
    changes: ["숲 그라데이션과 이끼색", "무지출 날 초록, 지출 열지도", "잎 무늬 머리"], cover: ["#1B5E20", "#A5D6A7", "🌲"],
    p: { bg: "#F3F8F2", card: "#FFFFFF", ink: "#1C3320", accent: "#2E7D32", line: "#D6E7D4", headPaint: "lg.120.FF1B5E20,FF66BB6A", headInk: "#FFFFFF", titleFont: "gowundodum", r: 18 },
    cells: [C.noSpend(), C.spendHeat()], decor: [headShape("leaf", "#C8E6C9", { x: 930, y: 300, w: 30, h: 30, fillA: 70, rot: 30 })],
  }),
  app({
    title: "바다 파랑", summary: "물결 무늬 바다색, 메모 많은 날은 칸에 물결이 이는 판",
    wow: "바다색 물결 무늬 바탕에, 메모가 세 개 넘는 날은 칸이 물결처럼 숨 쉬어요. 체크를 다 끝낸 날은 칸 테두리가 초록이에요.",
    changes: ["바다 파랑과 물결 무늬", "바쁜 날 물결, 체크율 테두리", "둥근 유리 탭"], cover: ["#0277BD", "#B3E5FC", "🌊"],
    p: { bg: "#F0F8FF", card: "#FFFFFF", ink: "#0B2E4A", accent: "#0277BD", line: "#D3E8F7", bgPaint: "pt.wave.FFF0F8FF.FFE1F2FD.120.0", headPaint: "lg.180.FF0277BD,FF4FC3F7",
      headInk: "#FFFFFF", titleFont: "comfortaa", r: 20 },
    app: [{ id: "app.tabs", paint: "gl.99FFFFFF.20", r: 24, out: 8 }], cells: [C.busyMemo(), C.checkRing()],
  }),
];
