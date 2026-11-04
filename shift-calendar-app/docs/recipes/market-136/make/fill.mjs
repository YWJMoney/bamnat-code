// 유형마다 구간 채우기 (메인 스레드 2026-11-03 PC 셈: 앱처럼 유형으로 거르면 20개가 안 되는 구간). 같은 생성기 틀로 그 유형에 맞는 작품을 더해요.
// 유형에 맞지 않는 구간은 비워 둬요: 자영업·투자의 근무·급여, 현장·자영업·투자의 알람 탭(알람 조각은 교대·일근·알바만 받아요, 서버 RECIPE_PIECES).
// 그 유형들의 '장 시작·마감, 영업 준비, 현장 전날' 알림은 규칙·단추 구간에 넣었어요.
import { c, ROSTER } from "./lib.mjs";
import { C, D, B } from "./kit.mjs";
import { cal } from "./tab-calendar.mjs";
import { pay } from "./tab-pay.mjs";
import { money } from "./tab-money.mjs";
import { memo } from "./tab-memo.mjs";
import { alarm } from "./tab-alarm.mjs";
import { widget } from "./tab-widget.mjs";
import { rule } from "./logic.mjs";

const P = (o) => ({ bg: "#FFFFFF", card: "#F7F7FA", ink: "#222222", accent: "#3F51B5", ...o });
const FBI = ["FIELD", "BUSINESS", "INVEST"];

// ---- 달력: 현장·자영업·투자 ----
export const calendar = [
  cal({
    title: "현장 출근 도장 달력", styles: ["FIELD"], summary: "일한 날 금빛, 10시간 넘은 날 굵은 테두리, #우천 날 회색 점선",
    wow: "돈 들어온 날은 금빛으로 번지고, 10시간 넘게 일한 날은 주황 굵은 테두리, #우천 꼬리표 날은 회색 점선이에요. 이달 현장 리듬이 칸에 그대로 남아요.",
    changes: ["수입 날 금빛", "긴 날 굵은 테두리", "#우천 점선"], who: "일당으로 일하는 현장 근무자", cover: ["#FF6F00", "#FFE0B2", "🦺"],
    p: P({ bg: "#FFF8F0", accent: "#FF6F00" }), cells: [C.incomeDay(), C.longDay(), C.tagLine("우천", "#78909C", "dash")],
  }),
  cal({
    title: "출장 지역 달력", styles: ["FIELD"], summary: "#출장 날 하늘색 칸, 일한 시간만큼 진해지는 칸",
    wow: "#출장 꼬리표 날은 하늘색으로, 일한 시간이 길수록 칸이 진해져요. 멀리 나간 날과 오래 일한 날이 한눈에 겹쳐 보여요.",
    changes: ["#출장 하늘색", "일한 시간 농도"], cover: ["#0288D1", "#B3E5FC", "🧳"],
    p: P({ bg: "#F5FBFF", accent: "#0288D1" }), cells: [C.tagFill("출장", "#4FC3F7", 30), C.hoursHeat("#0277BD")],
  }),
  cal({
    title: "영업일 매출 달력", styles: ["BUSINESS"], summary: "하루 매출이 목표를 넘은 날 금색 칸, 많이 쓴 날 빨간 금액",
    wow: "가계부에 매출을 적으면 하루 목표(넣을 때 정해요)를 넘은 날이 금색 칸으로 바뀌어요. 지출이 큰 날은 칸 아래 금액이 빨갛게 서요.",
    changes: ["목표 넘은 날 금색", "큰 지출 빨간 금액", "하루 목표 칸"], who: "매출을 날마다 보는 사장님", cover: ["#FFB300", "#FFF8E1", "🏆"],
    p: P({ bg: "#FFFDF5", accent: "#FFB300" }), cells: [C.salesGoal(500000), C.spendLine(100000)], cal: { spend: true },
  }),
  cal({
    title: "예약 손님 달력", styles: ["BUSINESS"], summary: "#예약 날 분홍 테두리, #단체 날 진한 칸, 메모 많은 날 숨쉬기",
    wow: "#예약 메모가 있는 날은 분홍 테두리, #단체 주문 날은 진한 칸이에요. 메모가 세 개 넘는 바쁜 날은 칸이 숨 쉬어요.",
    changes: ["#예약 분홍 테두리", "#단체 진한 칸", "바쁜 날 숨쉬기"], cover: ["#EC407A", "#FCE4EC", "📒"],
    p: P({ bg: "#FFF8FB", accent: "#EC407A" }), cells: [C.tagLine("예약", "#EC407A"), C.tagFill("단체", "#8D6E63", 40), C.busyMemo()],
  }),
  cal({
    title: "매매 기록 달력", styles: ["INVEST"], summary: "#매매 날 테두리, 일지 쓴 날 초록 칸",
    wow: "#매매 꼬리표 날은 짙은 테두리가, 같은 날 #일지가 있으면 초록 칸이 생겨요. 매매만 하고 일지를 안 쓴 날이 바로 드러나요.",
    changes: ["#매매 테두리", "#일지 초록 칸"], who: "매매를 기록하는 투자자", cover: ["#263238", "#A5D6A7", "📈"],
    p: P({ bg: "#F6F8F7", accent: "#455A64" }), cells: [C.tagLine("매매", "#455A64"), C.tagFill("일지", "#43A047", 30)],
  }),
  cal({
    title: "배당 적립 달력", styles: ["INVEST"], summary: "#배당 날 금색, #적립 날 새싹색, 돈 들어온 날 빛",
    wow: "#배당 꼬리표 날은 금색, #적립 날은 새싹색 칸이에요. 가계부에 들어온 돈이 있는 날은 금빛이 번져요.",
    changes: ["#배당 금색", "#적립 새싹색", "수입 날 금빛"], cover: ["#C9A227", "#DCEDC8", "🌱"],
    p: P({ bg: "#FBFCF6", accent: "#7CB342" }), cells: [C.tagFill("배당", "#FFD54F", 45), C.tagFill("적립", "#AED581", 40), C.incomeDay()],
  }),
  cal({
    title: "돈 흐름 달력", styles: FBI, summary: "쓴 돈만큼 칸이 물들고, 들어온 날 금빛, 무지출 날 초록 날짜",
    wow: "그날 쓴 돈이 많을수록 노랑·주황·빨강으로 물들고, 돈이 들어온 날은 금빛, 지난날 중 안 쓴 날은 날짜가 초록이에요.",
    changes: ["지출 열지도", "수입 날 금빛", "무지출 초록 날짜"], cover: ["#FFF59D", "#2E7D32", "💸"],
    p: P({ accent: "#2E7D32" }), cells: [C.spendHeat(), C.incomeDay(), C.noSpend()],
  }),
  cal({
    title: "할 일 체크 달력", styles: FBI, summary: "체크를 다 끝낸 날 초록 고리, 남은 날 노랑, #마감 날 남은 날 색",
    wow: "그날 체크 목록을 다 끝내면 칸이 초록 고리, 남았으면 노랑이에요. #마감 꼬리표 날은 다가올수록 파랑·주황·빨강으로 바뀌어요.",
    changes: ["체크 고리", "#마감 남은 날 색"], cover: ["#43A047", "#E53935", "✅"],
    p: P({ accent: "#43A047" }), cells: [C.checkRing(), C.dday("마감")],
  }),
  cal({
    title: "꼬리표 색 달력", styles: FBI, summary: "#중요 빨간 칸, #약속 초록 표시, 메모 많은 날 숨쉬기",
    wow: "메모에 #중요를 붙이면 그 날 칸이 연한 빨강, #약속은 칸 표시가 초록으로 바뀌어요. 바쁜 날은 칸이 숨 쉬어요.",
    changes: ["#중요 빨간 칸", "#약속 초록 표시", "바쁜 날 숨쉬기"], cover: ["#E53935", "#43A047", "🏷️"],
    p: P({ accent: "#E53935" }), cells: [C.tagFill("중요", "#E53935", 25), { id: "cal.cell.mark", when: '꼬리표.포함("약속")', ink: c("#43A047"), weight: 800 }, C.busyMemo()],
  }),
];

// ---- 근무·급여: 교대·일근·알바 ----
const goal = (def) => ({ id: "pay.total", when: `이달급여 >= 칸("목표 급여", ${def})`, paint: "lg.120.FFFFD54F,FFFFA000", ink: c("#3E2723"), motion: "shimmer" });
const GOAL_IN = (def) => [{ id: "i1", label: "목표 급여", kind: "money", default: def, min: 0 }];
const payRoster = [
  pay({
    title: "야간 수당 하이라이트", summary: "야간 근무 줄은 남색 바탕, 근무 종류 비율 카드",
    wow: "급여 줄 가운데 야간 근무 날만 연한 남색 바탕으로 서서 야간이 이달 급여에 얼마나 섞였는지 보여요. 근무 종류 비율 카드가 같이 서요.",
    changes: ["야간 줄 남색", "근무 종류 비율·급여 카드"], cover: ["#1A237E", "#C5CAE9", "🌙"],
    p: { accent: "#283593" }, stats: "kinds,pay,weeks", dated: [{ id: "pay.row", when: '근무종류 = "야간"', fill: c("#E8EAF6"), weight: 700 }],
  }),
  pay({
    title: "주말 근무 표시 급여", summary: "주말에 일한 줄은 주황 바탕, 주별 카드 먼저",
    wow: "토·일에 일한 날 급여 줄이 주황 바탕으로 서요. 주별 카드가 먼저 나와 주말이 많은 주를 찾기 쉬워요.",
    changes: ["주말 근무 줄 주황", "주별·급여 카드"], cover: ["#FB8C00", "#FFE0B2", "📆"],
    p: { accent: "#EF6C00" }, stats: "weeks,pay", dated: [{ id: "pay.row", when: "주말 그리고 근무시간 > 0", fill: c("#FFF3E0") }],
  }),
  pay({
    title: "공휴일 근무 급여 줄", summary: "공휴일에 일한 줄은 빨간 글자, 수당 확인을 잊지 않게",
    wow: "공휴일에 일한 날 급여 줄이 빨간 굵은 글자로 서요. 휴일 수당이 제대로 붙었는지 한 줄씩 확인해요.",
    changes: ["공휴일 근무 줄 빨강", "급여·지난달 대비 카드"], cover: ["#E53935", "#FFEBEE", "🎌"],
    p: { accent: "#C62828" }, stats: "pay,compare", dated: [{ id: "pay.row", when: "공휴일 그리고 근무시간 > 0", ink: c("#C62828"), weight: 800 }],
  }),
  pay({
    title: "긴 근무 날 급여 줄", summary: "12시간 넘게 일한 날 줄에 주황 테두리, 이어 일한 날은 빨강",
    wow: "12시간 넘은 날 급여 줄은 주황 테두리, 다섯째 날 넘게 이어 일한 날은 빨간 테두리예요. 몸이 고된 날이 급여 화면에서도 보여요.",
    changes: ["긴 날 주황 테두리", "연속근무 빨간 테두리", "주별 카드"], cover: ["#F57C00", "#FFCDD2", "⏱️"],
    p: { accent: "#F57C00" }, stats: "weeks,pay,tiles",
    dated: [{ id: "pay.row", when: "근무시간 >= 12", line: "solid", lineW: 2, lineC: c("#F57C00") }, { id: "pay.row", when: "연속근무 >= 5", line: "solid", lineW: 2, lineC: c("#E53935") }],
  }),
  pay({
    title: "다크 급여 대시보드", summary: "검은 바탕 큰 합계, 목표를 넘으면 금빛",
    wow: "급여 화면이 검은 대시보드가 되고 합계가 큰 흰 숫자로 서요. 이달 예상 급여가 목표(넣을 때 정해요)를 넘으면 합계 카드가 금빛으로 반짝여요.",
    changes: ["검은 대시보드", "목표 넘으면 금빛", "3개월 평균·지난달 대비"], cover: ["#111111", "#FFD54F", "🖤"],
    p: { bg: "#121212", card: "#1E1E1E", card2: "#242424", ink: "#EEEEEE", accent: "#FFD54F", headInk: "#111111", numFont: "bebasneue", totalSize: 42 },
    stats: "pay,three,compare", dated: [goal(2500000)], inputs: GOAL_IN(2500000),
  }),
  pay({
    title: "파스텔 급여 노트", summary: "파스텔 카드와 둥근 줄, 많이 번 날은 금빛 줄",
    wow: "급여 화면이 파스텔 노트처럼 둥글어지고, 그날 번 돈이 15만을 넘는 날 줄은 금빛으로 칠해져요.",
    changes: ["파스텔 둥근 카드", "많이 번 날 금빛 줄", "타일·주별 카드"], cover: ["#F8BBD0", "#FFF59D", "🧁"],
    p: { bg: "#FFFAFC", card2: "#FCE4EC", accent: "#EC407A", titleFont: "jua", r: 24 }, stats: "tiles,weeks,pay",
    dated: [{ id: "pay.row", r: 14 }, { id: "pay.row", when: "그날급여 >= 150000", paint: "lg.90.FFFFF8E1,FFFFE082" }],
  }),
  pay({
    title: "근무 시간 급여 표", summary: "촘촘한 줄과 고정폭 숫자, 일한 시간 긴 줄은 굵게",
    wow: "급여 줄이 표처럼 촘촘해지고 숫자가 고정폭이라 자릿수가 맞아요. 10시간 넘게 일한 줄은 굵게 서요.",
    changes: ["촘촘한 표 줄", "고정폭 숫자", "긴 날 굵게"], cover: ["#ECEFF1", "#37474F", "📋"], rows: "compact", big: "l",
    p: { accent: "#37474F", numFont: "jetbrainsmono" }, stats: "pay,weeks", dated: [{ id: "pay.row", when: "근무시간 >= 10", weight: 800 }],
  }),
  pay({
    title: "이달 급여 목표 바", summary: "목표 급여를 정하면 넘는 날부터 합계 금빛, 연간 카드까지",
    wow: "목표 급여(넣을 때 정해요)에 이달 예상 급여가 닿으면 합계가 금빛이 되고, 연간 카드가 열두 달 흐름을 보여 줘요.",
    changes: ["목표 넘으면 금빛", "연간·3개월 평균 카드"], cover: ["#283593", "#FFD54F", "🎯"],
    p: { accent: "#3949AB", headPaint: "lg.120.FF283593,FF5C6BC0" }, stats: "pay,year,three", dated: [goal(3000000)], inputs: GOAL_IN(3000000),
  }),
];

// ---- 일감·수입: 현장 ----
const F = {
  long: { id: "pay.row", when: "근무시간 >= 10", weight: 800, ink: c("#E65100") },
  holiday: { id: "pay.row", when: "공휴일 그리고 근무시간 > 0", ink: c("#C62828"), weight: 700 },
  weekend: { id: "pay.row", when: "주말 그리고 근무시간 > 0", fill: c("#FFF3E0") },
  rain: { id: "pay.row", when: '꼬리표.포함("우천")', alpha: 50 },
  big: (v) => ({ id: "pay.row", when: `그날급여 >= ${v}`, weight: 800, line: "solid", lineW: 1.5, lineC: c("#FFB300") }),
  streak: { id: "pay.row", when: "연속근무 >= 6", line: "solid", lineW: 2, lineC: c("#E53935") },
  goal: (v) => ({ id: "pay.total", when: `이달급여 >= 칸("목표 수입", ${v})`, paint: "lg.120.FFFFD54F,FFFFA000", ink: c("#3E2723"), motion: "shimmer" }),
  month: { id: "pay.head", when: "이달근무시간 >= 200", ink: c("#C62828") },
  trip: { id: "pay.row", when: '꼬리표.포함("출장")', fill: c("#E1F5FE") },
};
const GOAL_F = (v) => [{ id: "i1", label: "목표 수입", kind: "money", default: v, min: 0, hint: "이달 벌고 싶은 돈" }];
const FN = "일감·수입 화면 모양만 바뀌어요. 일당·공수 설정과 기록은 그대로예요";
const fp = (s) => pay({ styles: ["FIELD"], note: FN, ...s });
const payField = [
  fp({ title: "형광 공수판", summary: "검정 바탕에 형광 노랑 합계, 10시간 넘은 날 굵게",
    wow: "일감·수입 화면이 검정 바탕 형광 노랑 숫자로 바뀌고, 10시간 넘게 일한 날 줄이 굵은 주황으로 서요.", changes: ["형광 합계", "긴 날 굵게"], cover: ["#212121", "#FFEB3B", "🦺"],
    p: { bg: "#151515", card: "#212121", card2: "#2A2A2A", ink: "#F5F5F5", accent: "#FFEB3B", headInk: "#111111", numFont: "blackhansans" }, dated: [F.long] }),
  fp({ title: "일당 목표 달성판", summary: "목표 수입을 넘으면 합계가 금빛, 큰 일당 날은 금 테두리",
    wow: "이달 번 돈이 목표(넣을 때 정해요)를 넘는 순간 합계가 금빛으로 반짝이고, 20만 넘게 번 날 줄은 금 테두리가 생겨요.", changes: ["목표 넘으면 금빛", "큰 일당 날 테두리", "목표 수입 칸"],
    cover: ["#FFB300", "#FFF8E1", "🎯"], p: { accent: "#F57F17", headPaint: "lg.120.FF5D4037,FF8D6E63" }, dated: [F.goal(4000000), F.big(200000)], inputs: GOAL_F(4000000) }),
  fp({ title: "우천 쉼 표시", summary: "#우천 날 줄은 흐리게, 일한 날만 또렷하게",
    wow: "비 때문에 쉰 #우천 날 줄은 반쯤 흐려지고, 일한 날 줄만 또렷해요. 이달 몇 날 공쳤는지 한눈에 보여요.", changes: ["#우천 줄 흐리게", "회색 하늘 화면"], cover: ["#546E7A", "#CFD8DC", "🌧️"],
    p: { bg: "#F4F6F7", accent: "#546E7A" }, dated: [F.rain, F.long] }),
  fp({ title: "주말 현장 표시", summary: "주말에 일한 줄은 주황 바탕, 공휴일은 빨간 글자",
    wow: "토·일에 나간 현장 줄은 주황 바탕, 공휴일에 일한 줄은 빨간 글자예요. 쉬는 날 일한 날이 수입 화면에서 바로 보여요.", changes: ["주말 줄 주황", "공휴일 빨간 글자"], cover: ["#FB8C00", "#FFEBEE", "📅"],
    p: { accent: "#EF6C00" }, dated: [F.weekend, F.holiday] }),
  fp({ title: "연속 현장 경고판", summary: "여섯째 날 넘게 이어 나간 줄은 빨간 테두리",
    wow: "쉬지 않고 여섯째 날 넘게 이어 일한 날 줄에 빨간 테두리가 생겨요. 쉬어야 할 때를 수입 화면에서도 알려요.", changes: ["연속 현장 빨간 테두리", "긴 날 굵게"], cover: ["#C62828", "#FFCDD2", "🛑"],
    p: { accent: "#C62828" }, dated: [F.streak, F.long] }),
  fp({ title: "이달 시간 경고 머리", summary: "이달 일한 시간이 200시간을 넘으면 머리 글자가 빨강",
    wow: "이달 일한 시간이 200시간을 넘으면 일감·수입 화면 머리 글자가 빨갛게 바뀌어요. 큰 일당 날 줄에는 금 테두리가 생겨요.", changes: ["200시간 넘으면 빨간 머리", "큰 일당 날 테두리"],
    cover: ["#D32F2F", "#FFF8E1", "⏳"], p: { accent: "#D32F2F" }, dated: [F.month, F.big(250000)] }),
  fp({ title: "출장 현장 수입", summary: "#출장 날 줄은 하늘색, 주말 줄은 주황",
    wow: "#출장 꼬리표를 단 날 수입 줄이 하늘색으로, 주말에 나간 줄은 주황으로 칠해져요. 멀리 다녀온 일감이 따로 보여요.", changes: ["#출장 줄 하늘색", "주말 줄 주황"], cover: ["#0288D1", "#E1F5FE", "🧳"],
    p: { accent: "#0288D1" }, dated: [F.trip, F.weekend] }),
  fp({ title: "청사진 수입판", summary: "청사진 파랑 화면과 흰 합계, 긴 날 굵게",
    wow: "일감·수입 화면이 청사진처럼 파란 모눈 위에 흰 숫자로 서요. 10시간 넘은 날 줄이 굵어요.", changes: ["청사진 화면", "흰 합계", "긴 날 굵게"], cover: ["#0D47A1", "#BBDEFB", "📐"],
    p: { bg: "#0D2A57", card: "#123A75", card2: "#174A92", ink: "#E3F2FD", accent: "#90CAF9", headInk: "#0D2A57", bgPaint: "pt.grid.FF0D2A57.FF174A92.50.0" }, dated: [F.long] }),
  fp({ title: "콘크리트 수입판", summary: "콘크리트 질감 합계와 굵은 글꼴, 공휴일 빨강",
    wow: "합계 카드가 콘크리트 질감 위 굵은 글꼴로 서고, 공휴일에 일한 줄은 빨간 글자예요.", changes: ["콘크리트 합계", "굵은 글꼴", "공휴일 빨강"], cover: ["#607D8B", "#ECEFF1", "🧱"],
    p: { accent: "#455A64", headPaint: "tx.concrete.FF78909C.80", numFont: "blackhansans" }, dated: [F.holiday, F.long] }),
  fp({ title: "안전 노랑 수입판", summary: "노랑·검정 띠 합계, 연속 현장 빨간 테두리",
    wow: "합계 카드가 안전 노랑·검정 띠가 되고, 여섯째 날 넘게 이어 일한 줄에는 빨간 테두리가 생겨요.", changes: ["노랑·검정 띠", "연속 현장 경고"], cover: ["#FFD600", "#212121", "⚠️"],
    p: { accent: "#212121", headPaint: "pt.stripes.FFFFD600.FF212121.60.45", headInk: "#FFFFFF" }, dated: [F.streak] }),
  fp({ title: "배달 수입 민트판", summary: "민트 합계와 큰 금액 날 금 테두리, 주말 주황",
    wow: "플랫폼 수입 화면이 민트 대시보드가 되고, 많이 번 날은 금 테두리, 주말 줄은 주황이에요.", changes: ["민트 합계", "많이 번 날 금 테두리", "주말 주황"], cover: ["#00BFA5", "#E0F2F1", "🛵"],
    p: { accent: "#00897B", headPaint: "lg.120.FF00897B,FF1DE9B6" }, dated: [F.big(150000), F.weekend] }),
  fp({ title: "프리랜서 건별 수입", summary: "깔끔한 흰 화면, 큰 건 금 테두리, 목표 넘으면 금빛",
    wow: "건별 수입 화면이 깔끔한 흰 카드가 되고, 큰 건은 금 테두리, 이달 목표(넣을 때 정해요)를 넘으면 합계가 금빛이에요.", changes: ["흰 카드", "큰 건 테두리", "목표 금빛"], cover: ["#FFFFFF", "#7E57C2", "💼"],
    p: { accent: "#7E57C2" }, dated: [F.big(300000), F.goal(3000000)], inputs: GOAL_F(3000000) }),
  fp({ title: "촬영 스태프 수입", summary: "어두운 슬레이트 화면, 긴 촬영 날 굵게",
    wow: "촬영장 슬레이트 같은 어두운 화면에 흰 숫자, 10시간 넘은 촬영 날 줄이 굵은 주황으로 서요.", changes: ["어두운 슬레이트", "긴 날 굵게"], cover: ["#263238", "#FF7043", "🎬"],
    p: { bg: "#1C2328", card: "#263238", card2: "#2E3B42", ink: "#ECEFF1", accent: "#FF7043", headInk: "#1C2328" }, dated: [F.long, F.holiday] }),
  fp({ title: "용달 운행 수입", summary: "도로 회색 화면, 큰 운행 날 금 테두리, 주말 주황",
    wow: "운행 수입 화면이 도로 회색과 노란 선으로 바뀌고, 많이 번 날은 금 테두리, 주말 운행은 주황이에요.", changes: ["도로 회색", "큰 운행 날 테두리", "주말 주황"], cover: ["#455A64", "#FFEB3B", "🚚"],
    p: { accent: "#FBC02D", headPaint: "lg.180.FF37474F,FF546E7A", headInk: "#FFEB3B" }, dated: [F.big(200000), F.weekend] }),
  fp({ title: "인테리어 공정 수입", summary: "나무색 화면, 긴 날 굵게, #출장 하늘색",
    wow: "나무결 같은 따뜻한 화면에 공정 수입이 서요. 긴 날은 굵게, #출장 현장은 하늘색 줄이에요.", changes: ["나무색 화면", "긴 날 굵게", "#출장 하늘색"], cover: ["#8D6E63", "#D7CCC8", "🪚"],
    p: { bg: "#FBF7F3", accent: "#6D4C41", headPaint: "lg.120.FF8D6E63,FFBCAAA4" }, dated: [F.long, F.trip] }),
  fp({ title: "큰 숫자 일당판", summary: "합계를 아주 큰 숫자로, 많이 번 날 금 테두리",
    wow: "이달 번 돈이 포스터처럼 큰 숫자로 서고, 많이 번 날 줄에 금 테두리가 생겨요.", changes: ["아주 큰 합계", "많이 번 날 테두리"], cover: ["#000000", "#FFC107", "🔢"],
    p: { accent: "#212121", numFont: "anton", totalSize: 48 }, dated: [F.big(200000)] }),
  fp({ title: "미니멀 일감 수입", summary: "흑백만, 공휴일과 긴 날만 굵게",
    wow: "아무 색 없이 흑백으로 정리하고, 공휴일과 10시간 넘은 날만 굵게 남겨요.", changes: ["흑백", "공휴일·긴 날 굵게"], cover: ["#FFFFFF", "#000000", "◻️"],
    p: { accent: "#111111", income: "#111111" }, dated: [{ ...F.holiday, ink: c("#111111") }, { ...F.long, ink: c("#111111") }] }),
  fp({ title: "새벽 현장 수입", summary: "새벽 하늘 그라데이션 합계, 연속 현장 경고",
    wow: "합계 카드가 새벽 남색에서 주황으로 번지는 하늘이 되고, 여섯째 날 넘게 이어 일한 줄에는 빨간 테두리가 생겨요.", changes: ["새벽 하늘 합계", "연속 현장 경고"], cover: ["#1A237E", "#FF8F00", "🌅"],
    p: { accent: "#FF8F00", headPaint: "lg.135.FF1A237E,FFFF8F00" }, dated: [F.streak, F.rain] }),
  fp({ title: "현장 목표 저금통", summary: "목표 수입을 넘으면 금빛 합계, 우천 날 흐리게",
    wow: "이달 목표(넣을 때 정해요)를 넘으면 합계가 금빛, #우천으로 쉰 날 줄은 흐리게 보여요.", changes: ["목표 금빛", "#우천 흐리게", "목표 수입 칸"], cover: ["#F8BBD0", "#FFD54F", "🐷"],
    p: { accent: "#EC407A", titleFont: "jua" }, dated: [F.goal(3500000), F.rain], inputs: GOAL_F(3500000) }),
];
export const payAll = [...payRoster, ...payField];

// ---- 가계부: 모든 유형 ----
export const moneyMore = [
  money({ title: "큰 지출 경고 줄", summary: "큰 지출 한 줄은 분홍 바탕, 이번 달 흐름 카드 맨 위",
    wow: "가계부에서 큰 지출 한 줄이 분홍 바탕으로 서고, 맨 위 이번 달 흐름 카드가 오늘 쓸 수 있는 돈을 보여 줘요.", changes: ["큰 지출 분홍 줄", "이번 달 흐름 카드"], cover: ["#FFEBEE", "#E53935", "🚨"],
    p: { bg: "#FFFAFA", accent: "#E53935" }, cards: "pace,hero,recent", dated: [D.bigRow()] }),
  money({ title: "수입 금빛 줄", summary: "들어온 돈 줄은 금빛, 큰 수입은 반짝",
    wow: "들어온 돈 줄이 금빛으로 칠해지고, 큰 수입 한 줄은 반짝여요. 돈 들어온 날이 목록에서 바로 보여요.", changes: ["수입 줄 금빛", "큰 수입 반짝임", "수입 내역 카드"], cover: ["#FFD54F", "#FFF8E1", "💰"],
    p: { bg: "#FFFDF6", accent: "#F9A825", income: "#F57F17" }, cards: "income,hero,recent",
    dated: [{ id: "money.row", on: "in", paint: "lg.90.FFFFF8E1,FFFFECB3" }, { id: "money.row", when: '종류 = "수입" 그리고 금액 >= 1000000', motion: "shimmer" }] }),
  money({ title: "주말 지출 표시", summary: "주말에 쓴 날 날짜 띠가 주황, 평일은 차분하게",
    wow: "가계부 날짜 띠가 주말이면서 돈을 쓴 날엔 주황으로 바뀌어요. 주말 소비가 얼마나 잦은지 목록에서 보여요.", changes: ["주말 지출 날 주황 띠", "이번 달 흐름 카드"], cover: ["#FB8C00", "#FFF3E0", "🛍️"],
    p: { bg: "#FFFBF6", accent: "#EF6C00" }, cards: "pace,hero", dated: [{ id: "money.day", when: "주말 그리고 지출 > 0", fill: c("#FFE0B2") }] }),
  money({ title: "분류 색 가계부", summary: "식비 주황·교통 파랑·카페 갈색, 분류마다 줄 색",
    wow: "가계부 줄이 분류에 따라 식비는 주황, 교통은 파랑, 카페는 갈색 바탕으로 칠해져요. 어디에 많이 쓰는지 색으로 보여요.", changes: ["분류별 줄 색", "예산 카드"], cover: ["#FFCC80", "#90CAF9", "🎨"],
    p: { bg: "#FFFFFF", accent: "#5D4037" }, cards: "budget,hero,recent",
    dated: [{ id: "money.row", when: '분류 = "식비"', fill: c("#FFF3E0") }, { id: "money.row", when: '분류 = "교통"', fill: c("#E3F2FD") }, { id: "money.row", when: '분류 = "카페"', fill: c("#EFEBE9") }] }),
];

// ---- 메모: 모든 유형 ----
export const memoMore = [
  memo({ title: "체크 진행 카드", summary: "하던 체크는 노란 테두리, 다 끝낸 카드는 흐리게",
    wow: "체크를 시작했지만 남은 카드는 노란 테두리, 다 끝낸 카드는 흐려져요. 할 일 틀로 바로 시작해요.", changes: ["하는 중 노란 테두리", "끝낸 카드 흐리게", "할 일 틀"], cover: ["#FFF8E1", "#FFB300", "🟨"],
    p: { bg: "#FFFDF7", accent: "#FFB300" }, tpls: [{ title: "할 일", emoji: "✅", check: ["첫째 일", "둘째 일", "셋째 일"] }],
    dated: [D.memoDone(), { id: "memo.card", when: "체크율 > 0 그리고 체크율 < 100", line: "solid", lineW: 2, lineC: c("#FFB300") }] }),
  memo({ title: "고정 메모 보드", summary: "꼬리표별 보드, 고정한 메모는 금색 테두리",
    wow: "메모장이 꼬리표별 보드로 나뉘고, 고정한 메모는 금색 테두리로 칸 맨 위에서 빛나요.", changes: ["꼬리표 보드", "고정 금색 테두리"], cover: ["#FFFFFF", "#FFB300", "📌"],
    p: { bg: "#FAFAFA", accent: "#FFB300" }, view: { memo: "board", memoBy: "tag" }, dated: [D.memoPinned()] }),
  memo({ title: "중요 꼬리표 카드", summary: "#중요 메모 카드는 연한 빨강, #나중에 는 흐리게",
    wow: "#중요 꼬리표를 단 메모 카드는 연한 빨강 바탕, #나중에 꼬리표는 흐리게 물러나요. 꼬리표만 붙이면 메모장이 정리돼요.", changes: ["#중요 빨간 카드", "#나중에 흐리게"], cover: ["#FFEBEE", "#E53935", "❗"],
    p: { bg: "#FFFFFF", accent: "#E53935" }, dated: [{ id: "memo.card", when: '꼬리표.포함("중요")', fill: c("#FFEBEE") }, { id: "memo.card", when: '꼬리표.포함("나중에")', alpha: 50 }] }),
  memo({ title: "오늘 메모 강조", summary: "오늘 날짜 메모는 진한 테두리, 지난 메모는 흐리게",
    wow: "오늘 날짜 메모 카드는 진한 파란 테두리로 서고, 지난날 메모는 조금 흐려져요. 오늘 할 것만 눈에 들어와요.", changes: ["오늘 메모 테두리", "지난 메모 흐리게"], cover: ["#E3F2FD", "#1565C0", "📍"],
    p: { bg: "#F7FAFF", accent: "#1565C0" }, dated: [{ id: "memo.card", when: "오늘", line: "solid", lineW: 2, lineC: c("#1565C0") }, { id: "memo.card", when: "지난날", alpha: 65 }] }),
];

// ---- 알람: 교대·일근·알바 ----
export const alarmMore = [
  alarm({ title: "한 번에 깨는 알람", styles: ROSTER, summary: "근무마다 60분 전 한 번, 덧셈 풀어야 꺼져요",
    wow: "알람은 한 번만, 대신 덧셈을 풀어야 꺼져요. 여러 번 울리는 게 싫지만 놓치고 싶지 않은 분께 맞아요.", changes: ["근무마다 60분 전", "남색 화면"], cover: ["#283593", "#C5CAE9", "➕"],
    byRole: [["day", 60], ["eve", 60], ["night", 60], ["long", 60]], mission: "math", p: { bg: "#0F1A3D", card: "#16245A", accent: "#9FA8DA" } }),
  alarm({ title: "세 번 넉넉한 알람", styles: ROSTER, summary: "근무 90·60·30분 전 세 번, 점점 크게",
    wow: "아침잠이 깊은 분을 위해 근무 90분·60분·30분 전 세 번 울려요. 소리는 작게 시작해 커져요.", changes: ["근무마다 90·60·30분 전", "새벽 보라 화면"], cover: ["#4A148C", "#E1BEE7", "🔔"],
    byRole: [["day", 90, 60, 30], ["eve", 90, 60, 30], ["night", 90, 60, 30], ["long", 90, 60, 30]], gradual: true, p: { bg: "#1F0A33", card: "#2E1050", accent: "#CE93D8" } }),
  alarm({ title: "긴 근무 전날 알람", styles: ROSTER, summary: "긴 근무(당직·24시간) 날은 세 시간 전부터 두 번",
    wow: "긴 근무 날은 몸을 준비하도록 세 시간 전과 한 시간 전에 울려요. 다른 근무는 50분 전 한 번이에요.", changes: ["긴 근무 180·60분 전", "다른 근무 50분 전", "짙은 빨강 화면"], cover: ["#8E0000", "#FFCDD2", "⏳"],
    byRole: [["long", 180, 60], ["day", 50], ["eve", 50], ["night", 50]], p: { bg: "#2A0505", card: "#3D0B0B", accent: "#EF9A9A" } }),
  alarm({ title: "저녁 근무 낮잠 알람", styles: ROSTER, summary: "저녁 근무 날 낮잠 깨우기 두 번, 흔들어서 끄기",
    wow: "저녁 근무 전 낮잠을 자도 두 시간 전과 40분 전에 깨워요. 휴대폰을 흔들어야 꺼져요.", changes: ["저녁 120·40분 전", "주간·야간 60분 전", "노을 화면"], cover: ["#FF7043", "#FFCCBC", "🌇"],
    byRole: [["eve", 120, 40], ["day", 60], ["night", 60]], mission: "shake", p: { bgPaint: "lg.180.FF3E1A10,FFBF360C", bg: "#3E1A10", accent: "#FF8A65" } }),
  alarm({ title: "커피 한 잔 알람", styles: ROSTER, summary: "근무 45분 전, 따뜻한 커피색 화면",
    wow: "커피 내릴 시간만큼 45분 전에 울려요. 따뜻한 커피색 화면이라 아침이 덜 차가워요.", changes: ["근무마다 45분 전", "커피색 화면"], cover: ["#6D4C41", "#D7CCC8", "☕"],
    byRole: [["day", 45], ["eve", 45], ["night", 45], ["long", 45]], gradual: true, p: { bg: "#3E2723", card: "#4E342E", ink: "#EFEBE9", accent: "#BCAAA4" } }),
  alarm({ title: "다시 울림 없이", styles: ROSTER, summary: "다시 울림 1분·울림 5분, 끌 때까지 이어 울려요",
    wow: "다시 울림을 1분으로 줄이고 한 번에 5분 울려서 미루기가 어려워요. 근무 35분 전이에요.", changes: ["근무마다 35분 전", "흰 화면"], cover: ["#FFFFFF", "#212121", "⏰"],
    byRole: [["day", 35], ["eve", 35], ["night", 35], ["long", 35]], snooze: 1, ring: 5, p: { bg: "#FFFFFF", card: "#F5F5F5", ink: "#111111", accent: "#111111" } }),
  alarm({ title: "별빛 야간 알람", styles: ROSTER, summary: "야간 날 150·45분 전, 별빛 화면",
    wow: "야간 근무 날 저녁잠을 깨우도록 두 시간 반 전과 45분 전에 울려요. 별빛 화면이라 밤 출근이 덜 서운해요.", changes: ["야간 150·45분 전", "다른 근무 60분 전", "별빛 화면"], cover: ["#0B1026", "#FFF59D", "✨"],
    byRole: [["night", 150, 45], ["day", 60], ["eve", 60]], gradual: true, p: { bgPaint: "pt.star.FF0B1026.FF1A237E.60.0", bg: "#0B1026", card: "#141B3F", accent: "#FFF59D" } }),
  alarm({ title: "바다 소리 알람", styles: ROSTER, summary: "점점 크게, 다시 울림 8분, 바다 파랑 화면",
    wow: "파도처럼 작게 시작해 커지는 알람이에요. 다시 울림은 8분, 근무 70분 전에 울려요.", changes: ["근무마다 70분 전", "바다 파랑 화면"], cover: ["#0277BD", "#B3E5FC", "🌊"],
    byRole: [["day", 70], ["eve", 70], ["night", 70], ["long", 70]], gradual: true, snooze: 8, p: { bgPaint: "lg.180.FF01579B,FF0288D1", bg: "#01579B", accent: "#B3E5FC" } }),
  alarm({ title: "두 번 흔들기 알람", styles: ROSTER, summary: "근무 50·20분 전 두 번, 흔들어서 끄기",
    wow: "50분 전에 한 번, 20분 전에 한 번. 둘 다 흔들어야 꺼져서 몸이 먼저 깨요.", changes: ["근무마다 50·20분 전", "주황 화면"], cover: ["#E65100", "#FFE0B2", "📳"],
    byRole: [["day", 50, 20], ["eve", 50, 20], ["night", 50, 20], ["long", 50, 20]], mission: "shake", p: { bg: "#2B1300", card: "#3E1D00", ink: "#FFF3E0", accent: "#FF9800" } }),
  alarm({ title: "벚꽃 알람", styles: ROSTER, summary: "분홍 벚꽃 화면, 근무 80분 전 부드럽게",
    wow: "분홍 벚꽃 화면에 소리가 천천히 커지는 알람이에요. 근무 80분 전, 한 번에 4분 울려요.", changes: ["근무마다 80분 전", "벚꽃 분홍 화면"], cover: ["#F8BBD0", "#FFFFFF", "🌸"],
    byRole: [["day", 80], ["eve", 80], ["night", 80], ["long", 80]], gradual: true, ring: 4, p: { bg: "#FFF0F5", card: "#FFFFFF", ink: "#880E4F", accent: "#F06292", font: "jua" } }),
];

// ---- 위젯: 모든 유형 ----
export const widgetMore = [
  widget({ title: "라벤더 위젯", summary: "연보라 바탕, 쉬는 날 민트, 오늘 진보라",
    wow: "연보라 위젯에 쉬는 날은 민트 칸, 오늘은 진보라 칸이에요.", changes: ["연보라 바탕", "쉬는 날 민트", "오늘 진보라"], cover: ["#EDE7F6", "#7E57C2", "💜"],
    p: { bg: "#F6F2FC", ink: "#4A148C", accent: "#7E57C2" }, roles: { off: "#B2DFDB" } }),
  widget({ title: "모노 위젯", summary: "고정폭 글자 흑백 위젯, 일하는 날만 굵게",
    wow: "고정폭 글자의 흑백 위젯이에요. 일하는 날만 굵고, 쉬는 날은 흐려요.", changes: ["고정폭 글자", "근무 날 굵게", "쉬는 날 흐리게"], cover: ["#FAFAFA", "#212121", "⌨️"],
    p: { bg: "#FAFAFA", ink: "#212121", accent: "#212121", font: "jetbrainsmono", r: 6 },
    roles: { day: { weight: 800 }, eve: { weight: 800 }, night: { weight: 800 }, long: { weight: 800 }, off: { alpha: 45 } } }),
  widget({ title: "귤 위젯", summary: "주황 귤색 바탕, 오늘 초록 잎 칸",
    wow: "귤색 위젯에 오늘 칸은 초록 잎색이에요. 쉬는 날은 연한 노랑이에요.", changes: ["귤색 바탕", "오늘 초록", "쉬는 날 노랑"], cover: ["#FFB74D", "#66BB6A", "🍊"],
    p: { bg: "#FFF3E0", ink: "#4E342E", accent: "#43A047" }, roles: { off: "#FFF59D" }, decor: [] }),
  widget({ title: "밤하늘 위젯", summary: "짙은 남색 바탕, 야간 날 노란 별빛 글자",
    wow: "짙은 남색 위젯에 야간 날은 노란 별빛 글자, 오늘은 하늘색 칸이에요.", changes: ["남색 바탕", "야간 별빛 글자", "오늘 하늘색"], cover: ["#0B1026", "#FFF59D", "🌌"],
    p: { dark: true, bg: "#0B1026", ink: "#E8EAF6", accent: "#81D4FA", onAccent: "#0B1026" }, roles: { night: { ink: c("#FFF59D"), weight: 800 }, off: { alpha: 50 } } }),
];

// ---- 규칙·단추 ----
export const logicMore = [
  rule({ title: "주말 계획 메모", summary: "금요일 저녁 여섯 시 '주말 계획' 메모가 생겨요",
    wow: "매주 금요일 18:00에 '주말 계획' 메모가 생겨요. 같은 이름의 메모 틀이 있으면 그 틀로 만들어져요.", changes: ["금요일 18:00 주말 계획 메모"], cover: ["#81C784", "#E8F5E9", "🗓️"],
    rules: [{ name: "주말 계획", on: "daily", at: "18:00", when: '요일 = "금"', do: "newMemo", arg: "주말 계획" }] }),
  rule({ title: "큰 지출 한 줄 메모", summary: "큰 지출을 적으면 그날 메모에 금액과 이달 합계 한 줄",
    wow: "가계부에 큰 지출을 적는 순간 그날 메모에 '큰 지출 · 이달 쓴 돈'이 한 줄로 남아요. 왜 썼는지 이어 적기 좋아요.", changes: ["큰 지출 때 메모 한 줄"], cover: ["#E53935", "#FFEBEE", "🧾"],
    rules: [{ name: "큰 지출 메모", on: "money", when: "지출 >= 100000", do: "memoLine", arg: "큰 지출 {원(지출)} · 이달 쓴 돈 {원(이달지출)}" }] }),
  rule({ title: "월말 돈 정리 알림", summary: "28일 저녁 이달 쓴 돈과 들어온 돈을 알려요",
    wow: "매달 28일 저녁 8시에 이달 쓴 돈과 들어온 돈을 계산해 알려 줘요. 다음 달 계획을 세우기 좋아요.", changes: ["28일 20:00 이달 돈 알림"], cover: ["#3949AB", "#E8EAF6", "📊"],
    rules: [{ name: "월말 정리", on: "daily", at: "20:00", when: "일 = 28", do: "notify", arg: "이달 쓴 돈 {원(이달지출)}, 들어온 돈 {원(이달수입)}이에요" }] }),
  rule({ title: "체크 다 하면 칭찬", summary: "그날 체크를 다 끝내면 칭찬 알림",
    wow: "그날 메모의 체크를 모두 끝내는 순간 '오늘 체크를 다 끝냈어요' 알림이 와요. 작은 성취가 쌓여요.", changes: ["체크 다 끝내면 알림"], cover: ["#43A047", "#C8E6C9", "🎉"],
    rules: [{ name: "체크 칭찬", on: "memo", when: "체크율 >= 100", do: "notify", arg: "오늘 체크를 다 끝냈어요. 수고했어요" }] }),
  rule({ title: "근무 끝 오늘 번 돈", styles: ROSTER, summary: "근무가 끝나면 오늘 일한 시간과 번 돈을 알려요",
    wow: "근무가 끝나고 10분 뒤 '오늘 몇 시간, 번 돈 얼마, 이달 예상 얼마'를 계산해 알려 줘요.", changes: ["근무 끝 10분 뒤 알림"], cover: ["#2E7D32", "#C8E6C9", "💵"],
    rules: [{ name: "오늘 번 돈", on: "end", min: 10, do: "notify", arg: "오늘 {근무시간}시간 · 번 돈 {원(그날급여)} · 이달 예상 {원(이달급여)}" }] }),
  rule({ title: "현장 전날 장비 점검", styles: ["FIELD"], summary: "일 시작 열두 시간 전 장비·준비물 알림",
    wow: "현장 일 시작 열두 시간 전(대개 전날 저녁)에 장비와 준비물을 챙기라고 알려요. 날 화면 '장비' 단추로 점검 메모를 만들어요.", changes: ["시작 12시간 전 알림", "날 화면 '장비' 단추"], cover: ["#F57C00", "#FFE0B2", "🧰"],
    rules: [{ name: "전날 장비", on: "start", min: -720, do: "notify", arg: "내일 {근무시작} 현장이에요. 장비와 준비물을 챙겨요" }],
    buttons: [B("장비", "🧰", "day", { do: "newMemo", arg: "장비 점검" }, "아님 쉬는날")] }),
  // 자영업
  rule({ title: "영업 준비 알림", styles: ["BUSINESS"], summary: "아침 8시 반 오픈 준비, 공휴일엔 조용히",
    wow: "공휴일이 아닌 날 아침 8시 반에 오픈 준비를 알려 줘요. 날 화면 '오픈' 단추로 오픈 준비 메모를 만들어요.", changes: ["08:30 오픈 준비 알림", "'오픈' 단추"], cover: ["#FF8F00", "#FFE0B2", "🌅"],
    rules: [{ name: "영업 준비", on: "daily", at: "08:30", when: "아님 공휴일", do: "notify", arg: "곧 영업 시작이에요. 재료와 포스를 확인해요" }],
    buttons: [B("오픈", "🔑", "day", { do: "newMemo", arg: "오픈 준비" })] }),
  rule({ title: "마감 정산 한 줄", styles: ["BUSINESS"], summary: "밤 9시 반 그날 매출·지출을 메모에 한 줄",
    wow: "밤 9시 반에 그날 매출과 지출이 그날 메모에 한 줄로 쌓여요. 마감 장부를 따로 쓰지 않아도 돼요.", changes: ["21:30 매출·지출 한 줄"], cover: ["#00796B", "#B2DFDB", "🧮"],
    rules: [{ name: "마감 정산", on: "daily", at: "21:30", do: "memoLine", arg: "매출 {원(수입)} · 지출 {원(지출)} · 이달 매출 {원(이달수입)}" }] }),
  rule({ title: "매출 목표 달성 알림", styles: ["BUSINESS"], summary: "하루 매출이 목표를 넘으면 바로 알림",
    wow: "가계부에 매출을 적다가 하루 목표(넣을 때 정해요)를 넘는 순간 축하 알림이 와요.", changes: ["목표 넘으면 알림", "하루 목표 칸"], cover: ["#FFB300", "#FFF8E1", "🏆"],
    rules: [{ name: "목표 달성", on: "money", when: '수입 >= 칸("하루 목표 매출", 500000)', do: "notify", arg: "오늘 매출 {원(수입)}, 목표를 넘었어요" }] }),
  rule({ title: "월요일 발주 메모", styles: ["BUSINESS"], summary: "월요일 아침 '발주' 메모, 단추로 바로 만들기",
    wow: "매주 월요일 아침 10시에 '발주' 메모가 생기고, 가계부 '발주' 단추로 언제든 만들 수 있어요.", changes: ["월요일 10:00 발주 메모", "가계부 '발주' 단추"], cover: ["#5D4037", "#D7CCC8", "📦"],
    rules: [{ name: "발주", on: "daily", at: "10:00", when: '요일 = "월"', do: "newMemo", arg: "발주" }], buttons: [B("발주", "📦", "money", { do: "newMemo", arg: "발주" })] }),
  // 투자
  rule({ title: "장 시작 알림", styles: ["INVEST"], summary: "평일 아침 8시 50분 장 시작 전 원칙 읽기 알림",
    wow: "주말과 공휴일이 아닌 날 8시 50분에 '곧 장이 열려요. 내 원칙을 한 번 읽어요' 알림이 와요.", changes: ["평일 08:50 알림"], cover: ["#1565C0", "#BBDEFB", "🔔"],
    rules: [{ name: "장 시작", on: "daily", at: "08:50", when: "아님 주말 그리고 아님 공휴일", do: "notify", arg: "곧 장이 열려요. 내 원칙을 한 번 읽어요" }] }),
  rule({ title: "장 마감 복기", styles: ["INVEST"], summary: "평일 오후 3시 40분 장 마감 뒤 복기 알림",
    wow: "장이 끝난 평일 15:40에 오늘 매매가 있었다면 일지를 남기라고 알려요. 날 화면 '복기' 단추로 바로 써요.", changes: ["평일 15:40 알림", "'복기' 단추"], cover: ["#37474F", "#CFD8DC", "📝"],
    rules: [{ name: "장 마감", on: "daily", at: "15:40", when: "아님 주말 그리고 아님 공휴일", do: "notify", arg: "장이 끝났어요. 매매가 있었다면 이유와 마음을 남겨요" }],
    buttons: [B("복기", "📝", "day", { do: "newMemo", arg: "매매 일지" })] }),
  rule({ title: "주말 공부 메모", styles: ["INVEST"], summary: "토요일 아침 '공부 기록' 메모가 생겨요",
    wow: "매주 토요일 아침 10시에 '공부 기록' 메모가 생겨요. 한 주 동안 읽은 것과 배운 것을 남겨요.", changes: ["토요일 10:00 공부 메모"], cover: ["#5E35B1", "#D1C4E9", "📚"],
    rules: [{ name: "주말 공부", on: "daily", at: "10:00", when: '요일 = "토"', do: "newMemo", arg: "공부 기록" }] }),
  rule({ title: "월말 자산 점검", styles: ["INVEST"], summary: "매달 28일 '자산 점검' 메모와 이달 돈 흐름 알림",
    wow: "매달 28일 저녁 '자산 점검' 메모가 생기고, 이달 들어온 돈과 쓴 돈을 함께 알려 줘요.", changes: ["28일 자산 점검 메모", "이달 돈 흐름 알림"], cover: ["#00695C", "#B2DFDB", "⚖️"],
    rules: [{ name: "자산 점검", on: "daily", at: "19:00", when: "일 = 28", do: "newMemo", arg: "자산 점검" },
      { name: "이달 흐름", on: "daily", at: "19:05", when: "일 = 28", do: "notify", arg: "이달 들어온 돈 {원(이달수입)}, 쓴 돈 {원(이달지출)}이에요" }] }),
];

