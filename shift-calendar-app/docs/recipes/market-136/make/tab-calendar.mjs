// 기능별 · 달력 22 (기획서 4-2, 4-10 8~14). 달력 탭 하나만 바꿔요: 칸 조건 줄(수식)은 지금 폰에서 그날 값으로 돌아요.
import { c, H, ALL, recipe, calTheme, parts, decor, tabView, cellSticker, cellShape, headText, headShape, baseTokens } from "./lib.mjs";
import { C, D } from "./kit.mjs";
import { calView } from "./build-theme.mjs";

const NOTE = "달력 모양만 바뀌어요. 근무·메모·가계부 기록과 다른 탭은 그대로예요";

function cal(s) {
  const p = s.p;
  const base = [
    { id: "cal.head", ...(p.headPaint ? { paint: p.headPaint } : { fill: c(p.card) }), r: p.r ?? 16, pad: 10 },
    { id: "cal.head.title", ...(p.titleFont ? { font: p.titleFont } : {}), size: p.titleSize ?? 22, weight: 800, ink: c(p.headInk ?? p.ink) },
    { id: "cal.week.day", on: "sun", ink: c(p.sun ?? "#E53935") }, { id: "cal.week.day", on: "sat", ink: c(p.sat ?? "#1E88E5") },
    { id: "cal.cell", r: p.cellR ?? 10 },
    { id: "cal.cell", on: "today", line: "solid", lineW: 2, lineC: c(p.accent), motion: p.todayMotion ?? "glow" },
    ...(p.dateFont ? [{ id: "cal.cell.date", font: p.dateFont }] : []),
  ];
  const pieces = [
    { kind: "screenLook", screens: `calendar~bg:${H(p.bg)}|cd:${H(p.card)}|ik:${H(p.ink)}|ac:${H(p.accent)}${p.bgPaint ? `|fl:${p.bgPaint}` : ""}` },
    calView(p, s.cal), calTheme(p, s.cell ?? "tint"), parts([...base, ...(s.cells ?? []), ...(s.dated ?? [])]),
    s.decor?.length ? decor(s.decor) : null, s.board ? tabView({ cal: s.board }) : null,
  ];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? NOTE, styles: s.styles ?? ALL, cover: s.cover,
      tokens: s.tokens, inputs: s.inputs, pieces }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

const ROSTER = ["SHIFT", "DAY", "HOURLY"];
const P = (o) => ({ bg: "#FFFFFF", card: "#F7F7FA", ink: "#222222", accent: "#3F51B5", ...o });

export default [
  // ---- 기능 속 기능 (그날 값으로 칸이 바뀌는 작은 기능) ----
  cal({
    title: "수면 빚 계기판", styles: ROSTER, summary: "야간·연속 근무가 쌓일수록 칸이 붉어지고 회복 날은 초록으로",
    wow: "야간이 이어질수록 칸이 붉게 쌓이고, 그 주 야간이 세 번 넘은 뒤 쉬는 날은 초록 '회복'으로 바뀌어요. 쉬지 않고 일한 날이 길면 테두리도 굵어져요.",
    changes: ["야간 1·3번째 칸 색", "회복 날 초록", "연속근무 피로 테두리"], who: "야간이 많아 잠이 모자란 분", cover: ["#B71C1C", "#1B5E20", "😴"],
    p: P({ accent: "#C62828" }), cells: [...C.nightDeep("lg.180.FFFFCDD2,FFFFFFFF", "lg.180.FFEF9A9A,FFFFCDD2"), C.recovery("#A5D6A7"), C.fatigue("#B71C1C")],
  }),
  cal({
    title: "야간 회복 표시", styles: ROSTER, summary: "이달 야간 수와 연속 근무로 피로 1~3단계를 테두리 굵기로",
    wow: "쉬지 않고 이어 일한 날 수에 따라 테두리가 1.5·2·2.5·3.5로 굵어지고, 이달 야간이 여덟 번을 넘으면 야간 칸 표시가 노랗게 빛나요.",
    changes: ["피로 단계 테두리", "야간 많은 달 표시", "쉬는 날 반짝임"], who: "피로를 눈으로 보고 쉬는 날을 지키고 싶은 분", cover: ["#311B92", "#FFF59D", "🛌"],
    p: P({ bg: "#F7F5FF", accent: "#5E35B1" }), cells: [C.fatigue("#5E35B1"), C.nightMonth(), { id: "cal.cell", on: "role.off", motion: "shimmer" }],
  }),
  cal({
    title: "오늘 칸 숨쉬기", summary: "오늘 칸이 지금 시각에 따라 새벽·아침·낮·저녁 색으로",
    wow: "앱을 열 때 지금 시각을 읽어 오늘 칸이 새벽 남색, 아침 금빛, 낮 하늘, 저녁 보라로 바뀌고 천천히 숨 쉬어요.",
    changes: ["오늘 칸 시각 색", "숨쉬는 움직임"], cover: ["#FFB74D", "#283593", "🌅"],
    p: P({ todayMotion: "pulse" }), cells: [C.todayClock()],
  }),
  cal({
    title: "근무 시간 열지도", styles: ROSTER, showcase: false, summary: "일한 시간만큼 칸이 진해지고, 52시간 넘은 주는 날짜에 밑줄",
    wow: "그날 일한 시간에 비례해 칸 파랑이 진해지고, 그 주 52시간을 넘기면 그 주 날짜가 빨간 밑줄로 바뀌어요.",
    changes: ["근무 시간 열지도", "주 52시간 밑줄", "칸 아래 근무 시간"], cover: ["#E3F2FD", "#0D47A1", "🔥"],
    p: P({ accent: "#1565C0" }), cells: [C.hoursHeat("#1565C0"), C.week52()], cal: { hours: true },
  }),
  cal({
    title: "지출 열지도", summary: "쓴 돈에 따라 칸이 노랑·주황·빨강으로, 무지출 날은 초록 날짜",
    wow: "가계부에 적은 그날 지출이 많아질수록 칸이 노랑 → 주황 → 빨강으로 바뀌어요. 한 푼도 안 쓴 지난날은 날짜가 초록이에요.",
    changes: ["지출 열지도", "무지출 날 초록 날짜", "칸 아래 쓴 돈"], cover: ["#FFF59D", "#EF9A9A", "🌡️"],
    p: P({ accent: "#EF6C00" }), cells: [C.spendHeat(), C.noSpend()], cal: { spend: true },
  }),
  cal({
    title: "메모 많은 날", summary: "메모가 세 개 넘는 날은 칸이 숨 쉬고, 체크를 다 끝내면 초록 테두리",
    wow: "메모가 세 개 넘게 쌓인 날은 칸이 천천히 숨 쉬어 바쁜 날이 눈에 띄고, 그날 체크를 다 끝내면 테두리가 초록, 하다 말면 노랑이에요.",
    changes: ["바쁜 날 숨쉬기", "체크율 테두리", "칸에 메모 세 줄"], cover: ["#E8F5E9", "#FFB300", "🗒️"],
    p: P({ accent: "#43A047" }), cells: [C.busyMemo(), C.checkRing()], cal: { memo: 3 },
  }),
  cal({
    title: "기념일 하트", summary: "#기념일 꼬리표 날엔 분홍 칸, 매년 같은 날엔 하트",
    wow: "#기념일 꼬리표를 단 날은 칸이 분홍으로 물들고, 꾸밈 층의 하트는 해마다 같은 날(받은 뒤 날짜만 바꿔요)에 저절로 붙어요.",
    changes: ["기념일 꼬리표 칸 색", "해마다 같은 날 하트", "D-day 테두리"], cover: ["#F8BBD0", "#E91E63", "💝"],
    p: P({ accent: "#E91E63", bg: "#FFF8FB" }), cells: [C.tagFill("기념일", "#F8BBD0", 80), C.dday("기념일")],
    decor: [cellSticker("💗", "m2d14"), cellSticker("💗", "m12d25")],
  }),
  cal({
    title: "마감 레이더", summary: "#마감 날 칸 테두리가 남은 날에 따라 파랑→주황→빨강",
    wow: "#마감 메모가 있는 날 테두리가 일주일 남으면 주황, 사흘 남으면 빨강으로 울리고, 지나면 회색으로 가라앉아요.",
    changes: ["마감 날 D-day 테두리", "울리는 움직임"], cover: ["#1E88E5", "#E53935", "🎯"],
    p: P({ accent: "#E53935" }), cells: [C.dday("마감")],
  }),
  // ---- 판짜기 ----
  cal({
    title: "근무 띠 대형판", styles: ROSTER, summary: "한 달 근무를 띠 한 줄로, 야간 이어지면 띠가 짙게",
    wow: "달력이 근무 띠 판으로 바뀌어 한 달 주기가 한 줄에 보여요. 날짜 칸으로 돌아가도 야간 연속은 짙게, 회복 날은 초록이에요.",
    changes: ["근무 띠 판", "띠 칸 야간 연속 표시(고침 뒤)", "회복 날"], cover: ["#263238", "#80CBC4", "📏"],
    p: P({ accent: "#00897B" }), board: "rota", cells: [C.recovery()], dated: [D.bandNight()],
  }),
  cal({
    title: "타임라인 하루판", styles: ROSTER, summary: "하루를 시간 막대로, 이른 근무 날은 날짜가 해 뜨는 색",
    wow: "달력이 타임라인 판이 되어 근무 시작~끝이 막대로 보여요. 7시 전에 시작하는 날은 날짜가 주황으로 바뀌어요.",
    changes: ["타임라인 판", "이른 근무 날짜 색"], cover: ["#FFF3E0", "#FF6F00", "⏱️"],
    p: P({ accent: "#FF6F00" }), board: "timeline", cells: [C.early()],
  }),
  cal({
    title: "연간 한눈판", summary: "열두 달을 한 화면에, 메모 있는 날은 날짜 밑줄",
    wow: "달력이 1년 판이 되어 열두 달이 한 화면에 들어와요. 날짜 칸으로 보면 메모가 있는 날 날짜에 밑줄이 그어져요.",
    changes: ["1년 판", "메모 있는 날 밑줄"], cover: ["#ECEFF1", "#37474F", "🗓️"],
    p: P({ accent: "#37474F" }), board: "yearly", cells: [{ id: "cal.cell.date", when: "메모수 > 0", deco: "under", weight: 800 }],
  }),
  cal({
    title: "주간 플래너판", summary: "한 주를 크게, 쓴 돈 많은 날은 칸 아래 금액이 빨갛게",
    wow: "달력이 주간 판이 되어 하루하루가 넓어져요. 날짜 칸으로 보면 큰 지출 날 금액이 빨갛게 굵어져요.",
    changes: ["주간 판", "큰 지출 줄 강조"], cover: ["#E8EAF6", "#3949AB", "📆"],
    p: P({ accent: "#3949AB" }), board: "weekly", cells: [C.spendLine(50000)], dated: [D.weeklySpend()], cal: { spend: true },
  }),
  // ---- 모양 ----
  cal({
    title: "공휴일 반짝", summary: "공휴일 칸은 반짝이고, 공휴일에 일하는 날은 금색 이중선",
    wow: "공휴일 칸이 반짝이고, 공휴일에 근무가 있으면 금색 이중선이 둘러 휴일 수당 챙길 날이 보여요.",
    changes: ["공휴일 반짝임", "공휴일 근무 이중선", "음력 표시"], cover: ["#FFF8E1", "#FFB300", "🎉"],
    p: P({ accent: "#FFB300" }), cells: [{ id: "cal.cell", on: "holiday", motion: "shimmer" }, C.holidayWork()], cal: { lunar: true },
  }),
  cal({
    title: "주말 무늬", summary: "토·일 칸에 사선 무늬, 주말에 일하면 보라 점선",
    wow: "주말 칸엔 은은한 사선 무늬가 깔리고, 주말에 일하는 날은 보라 점선이 둘러 쉬지 못한 주말이 보여요.",
    changes: ["주말 사선 무늬", "주말 근무 점선"], cover: ["#F3E5F5", "#8E24AA", "🎨"],
    p: P({ accent: "#8E24AA" }), cells: [{ id: "cal.cell", on: "sat", paint: "pt.stripes.FFFFFFFF.FFF3E5F5.60.45" }, { id: "cal.cell", on: "sun", paint: "pt.stripes.FFFFFFFF.FFFCE4EC.60.45" }, C.weekendWork()],
  }),
  cal({
    title: "월 머리 큰 숫자", summary: "달 이름을 큰 숫자로, 이달 쓴 돈이 예산을 넘으면 날짜에 줄",
    wow: "달력 머리에 달 이름이 포스터처럼 커져요. 이달 쓴 돈이 예산을 넘은 날부터 날짜에 가운데 줄이 그어져요.",
    changes: ["큰 숫자 달 이름", "예산 넘은 날 줄"], cover: ["#212121", "#FF5252", "🔢"],
    p: P({ accent: "#FF5252", titleFont: "anton", titleSize: 48 }), cells: [C.overBudget()],
    inputs: [{ id: "i1", label: "월 예산", kind: "money", default: 1500000, min: 0 }],
  }),
  cal({
    title: "손글씨 다이어리", summary: "손글씨 날짜와 종이 질감, 일기 쓴 날은 연필 밑줄",
    wow: "날짜가 손글씨로, 바탕은 종이 질감이에요. #일기 꼬리표 날은 날짜에 연필 밑줄이 그어져요.",
    changes: ["손글씨 날짜·제목", "종이 질감", "일기 날 밑줄"], cover: ["#FFF8E1", "#6D4C41", "✍️"],
    p: P({ bg: "#FBF6EA", card: "#FFFDF6", ink: "#3E3A33", accent: "#8D6E63", bgPaint: "tx.paper.FFFBF6EA.55", titleFont: "nanumpen", dateFont: "nanumpen" }),
    cells: [{ id: "cal.cell.date", when: '꼬리표.포함("일기")', deco: "under", ink: c("#6D4C41") }],
  }),
  cal({
    title: "수채화", summary: "칸마다 수채 번짐, 근무 날엔 물감이 진하게",
    wow: "칸마다 수채화처럼 번지는 색이 깔리고, 근무가 있는 날은 물감이 진해져 일한 날이 부드럽게 보여요.",
    changes: ["수채 번짐 칸", "근무 날 진한 물감"], cover: ["#B3E5FC", "#F8BBD0", "🖌️"],
    p: P({ accent: "#4FC3F7", cellPaint: "rg.50.50.90.4DB3E5FC,00FFFFFF", titleFont: "gowunbatang" }),
    cells: [{ id: "cal.cell", when: "아님 쉬는날 그리고 근무시간 > 0", paint: "rg.40.40.100.80F8BBD0,00FFFFFF" }],
  }),
  cal({
    title: "네온 달력", summary: "검은 칸에 네온 테두리, 바쁜 날은 깜빡이는 달력",
    wow: "검은 칸에 네온 선이 빛나고, 메모가 세 개 넘는 날은 깜빡여요. 오늘 칸은 빛이 번져요.",
    changes: ["검은 칸·네온 선", "바쁜 날 깜빡임"], cover: ["#000000", "#00E5FF", "💡"],
    p: P({ bg: "#07070F", card: "#12121F", ink: "#E8E8FF", accent: "#00E5FF", titleFont: "monoton", headInk: "#7DF9FF", offBg: "#0C0C18" }),
    cells: [{ id: "cal.cell", line: "solid", lineW: 1, lineC: c("#3A1E5C") }, C.busyMemo()], tokens: [],
  }),
  cal({
    title: "종이 질감 달력", summary: "크래프트지 칸과 도장 날짜, 지난날은 흐리게",
    wow: "칸이 크래프트지처럼 거칠고, 지나간 날은 살짝 흐려져 오늘부터 앞날이 또렷해요.",
    changes: ["크래프트지 칸", "지난날 흐리게"], cover: ["#D7CCC8", "#5D4037", "📦"],
    p: P({ bg: "#F3EBE0", card: "#EFE3D3", ink: "#3E2C1E", accent: "#8D5B2E", cellPaint: "tx.kraft.FFEFE3D3.60", titleFont: "songmyung" }),
    cells: [{ id: "cal.cell", when: "지난날 그리고 아님 오늘", alpha: 55 }],
  }),
  cal({
    title: "교대 6색 팔레트", styles: ["SHIFT"], summary: "주간·저녁·야간·당직·휴무·기타 여섯 색을 한 번에",
    wow: "근무 종류 여섯 가지가 서로 헷갈리지 않는 색으로 칸 전체를 칠하고, 야간이 세 번 이어지면 반짝임이 더해져요.",
    changes: ["근무 여섯 색", "칸 전체 칠하기", "연속 야간 반짝임"], cover: ["#FFB300", "#3949AB", "🎨"],
    p: P({ accent: "#3949AB" }), cell: "fill", cells: [{ id: "cal.cell", when: '근무종류 = "야간" 그리고 연속근무 >= 3', motion: "shimmer" }],
    extra: null,
  }),
  cal({
    title: "미니멀 점", summary: "칸 색 없이 점 하나, 근무 날만 점이 굵어지는 달력",
    wow: "칸에 색이 없고 근무 표시는 점 하나예요. 일한 시간이 10시간을 넘은 날만 테두리가 생겨요.",
    changes: ["점 표시", "긴 날 테두리"], cover: ["#FFFFFF", "#9E9E9E", "⚪", 0],
    p: P({ accent: "#212121", mark: "dot" }), cell: "dot", cells: [C.longDay("#212121")], cal: { badge: "small", mark: "dot" },
  }),
  cal({
    title: "돈 들어온 날 금색", summary: "가계부에 돈이 들어온 날 칸에 금빛이 번지는 달력",
    wow: "가계부에 수입을 적은 날은 칸 가운데서 금빛이 번져 들어온 날이 한눈에 보여요.",
    changes: ["돈 들어온 날 금빛", "칸 아래 쓴 돈"], cover: ["#FFF8E1", "#FFC107", "🪙"],
    p: P({ accent: "#FFB300" }), cells: [C.incomeDay()], cal: { spend: true },
  }),
];
