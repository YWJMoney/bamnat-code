// 기능별 · 가계부 22 (기획서 4-4, 4-10 15~21). 가계부 탭 하나만 바꿔요.
// 지금 도는 것: 가계부 카드(이번 달 흐름 = 오늘 쓸 수 있는 돈, 예산, 근무별 소비, 다음 카드값 …)와 분류 보드·달력·표 판.
// money.row·money.board.col의 when 줄은 1.36 고침이 합쳐지면 돌아요.
import { c, H, ALL, recipe, moneyLook, tabView, parts, decor } from "./lib.mjs";
import { D } from "./kit.mjs";

const NOTE = "가계부 모양과 카드만 바뀌어요. 적은 돈 기록은 그대로예요";

function money(s) {
  const p = { bg: "#FFFFFF", card: "#FFFFFF", ink: "#222222", accent: "#3F51B5", ...s.p };
  const lines = [
    { id: "money.head", ...(p.headPaint ? { paint: p.headPaint } : { fill: c(p.card) }), r: p.r ?? 16, pad: 12, ...(p.titleFont ? { font: p.titleFont } : {}), ink: c(p.headInk ?? p.ink) },
    { id: "money.day", fill: c(p.dayFill ?? p.card), r: 10, padX: 10, weight: 700 },
    { id: "money.amount", on: "out", ink: c(p.expense ?? "#C62828"), weight: 700, ...(p.numFont ? { font: p.numFont } : {}) },
    { id: "money.amount", on: "in", ink: c(p.income ?? "#2E7D32"), weight: 700, ...(p.numFont ? { font: p.numFont } : {}) },
    ...(s.parts ?? []), ...(s.dated ?? []),
  ];
  const pieces = [
    { kind: "screenLook", screens: `money~bg:${H(p.bg)}|cd:${H(p.card)}|ik:${H(p.ink)}|ac:${H(p.accent)}${p.bgPaint ? `|fl:${p.bgPaint}` : ""}` },
    moneyLook(p, s.look), s.cards ? { kind: "cards", money: s.cards } : null, s.view ? tabView({ money: s.view }) : null, parts(lines),
    s.decor?.length ? decor(s.decor) : null, ...(s.extra ?? []),
  ];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? NOTE, styles: s.styles ?? ALL, cover: s.cover, pieces }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

export default [
  // ---- 기능 속 기능 ----
  money({
    title: "하루 쓸 돈 계산기", summary: "이번 달 흐름 카드를 맨 위에: 이대로면 월말 지출과 오늘 쓸 수 있는 돈",
    wow: "가계부를 열면 맨 위에 '이대로면 월말에 얼마, 오늘은 얼마까지'가 그날 기록으로 다시 계산돼 나와요. 예산 카드가 바로 아래에서 분류별 남은 돈을 보여요.",
    changes: ["이번 달 흐름 카드 맨 위", "예산 카드", "큰 숫자 금액"], cover: ["#E8F5E9", "#2E7D32", "🧮"],
    p: { bg: "#F5FBF6", accent: "#2E7D32", numFont: "bebasneue" }, cards: "pace,budget,hero,recent", dated: [D.bigRow()],
  }),
  money({
    title: "무지출 챌린지", summary: "가계부 달력에서 무지출 날이 비어 보이고, 이번 달 흐름으로 남은 돈",
    wow: "가계부가 달력 판이 되어 한 푼도 안 쓴 날은 빈칸으로, 쓴 날은 금액으로 채워져요. 맨 위 흐름 카드가 오늘 쓸 수 있는 돈을 알려 줘요.",
    changes: ["가계부 달력 판", "이번 달 흐름 카드", "새싹 초록"], cover: ["#F1F8E9", "#7CB342", "🌱"],
    p: { bg: "#F7FBF2", accent: "#7CB342" }, view: "cal", cards: "pace,hero",
  }),
  money({
    title: "분류별 예산 신호등", summary: "분류 보드와 예산 카드, 예산 넘은 분류 칸은 빨간 테두리(고침 뒤)",
    wow: "가계부가 분류 보드로 나뉘어 식비·교통·쇼핑이 칸마다 합쳐지고, 예산 카드가 분류별 남은 돈을 보여 줘요. 한 칸 합계가 기준을 넘으면 그 칸이 빨갛게 둘러요(고침 뒤).",
    changes: ["분류 보드", "예산 카드 맨 위", "넘은 칸 빨간 테두리(고침 뒤)"], cover: ["#FFF3E0", "#E53935", "🚦"],
    p: { bg: "#FFFAF5", accent: "#EF6C00" }, view: "board", cards: "budget,pace,hero", dated: [D.colOver(300000)],
  }),
  money({
    title: "고정비 빠짐 확인", summary: "나갈 돈·반복 지출 찾기 카드로 이달 고정비가 빠졌는지",
    wow: "'나갈 돈' 카드가 이번 달 남은 고정 지출을, '반복 지출 찾기'가 매달 나가는 것 같은 지출을 찾아 고정비에 넣자고 제안해요.",
    changes: ["나갈 돈·반복 지출 찾기·구독 점검 카드", "차분한 남색"], cover: ["#E8EAF6", "#3949AB", "📌"],
    p: { bg: "#F7F8FD", accent: "#3949AB" }, cards: "upcoming,repeat,subs,hero,recent",
  }),
  money({
    title: "저축 목표 저금통", summary: "예산 카드와 수입 내역, 돈 들어온 줄은 금빛",
    wow: "수입 내역과 예산 카드가 위에 서서 들어온 돈과 남길 돈이 한눈에 보여요. 수입 줄은 금빛 굵은 금액이에요.",
    changes: ["수입 내역·예산·이번 달 흐름 카드", "수입 금빛", "분홍 저금통 색"], cover: ["#F8BBD0", "#FFD54F", "🐷"],
    p: { bg: "#FFF7FA", accent: "#F06292", income: "#F9A825", titleFont: "jua" }, cards: "income,budget,pace,hero",
  }),
  money({
    title: "근무별 소비", styles: ["SHIFT"], summary: "야간 다음 날·쉬는 날 하루 소비를 따로 보는 교대용 가계부",
    wow: "'근무별 소비' 카드가 야간 다음 날과 쉬는 날에 하루 평균 얼마를 쓰는지 따로 보여 줘요. 야간 뒤 배달·택시가 늘어나는지 숫자로 보여요.",
    changes: ["근무별 소비 카드 맨 위", "이번 달 흐름 카드", "밤하늘 남색"], cover: ["#1A237E", "#FFB74D", "🌙"],
    p: { bg: "#F5F6FC", accent: "#3949AB" }, cards: "shiftspend,pace,hero,recent",
  }),
  money({
    title: "카드 정산 미리 보기", summary: "다음 카드값과 할부 청구 기준을 맨 위에",
    wow: "'다음 카드값' 카드가 카드마다 다음 결제 금액과 '이대로면 얼마'를 보여 주고, 할부 카드가 달마다 나갈 할부를 정리해요.",
    changes: ["다음 카드값·할부 카드", "통장·카드 잔액", "카드 은색"], cover: ["#CFD8DC", "#263238", "💳"],
    p: { bg: "#F5F7F8", accent: "#37474F", headPaint: "lg.120.FF90A4AE,FFECEFF1" }, cards: "cardbill,install,accounts,hero",
  }),
  // ---- 판·모양 ----
  money({
    title: "표 가계부", summary: "가계부를 엑셀처럼 표로, 큰 지출 줄은 분홍(고침 뒤)",
    wow: "가계부가 날짜·분류·금액 표가 되어 한 달을 스크롤 없이 훑어요. 큰 지출 줄은 분홍으로 칠해져요(고침 뒤).",
    changes: ["표 판", "촘촘한 줄", "모노 숫자"], cover: ["#FFFFFF", "#1E88E5", "📋", 0],
    p: { bg: "#FFFFFF", accent: "#1E88E5", numFont: "robotomono" }, view: "table", look: { rows: "compact", amount: "m", mark: "dot", dayHead: "line" }, dated: [D.bigRow()],
    cards: "hero,pace",
  }),
  money({
    title: "달력 가계부", summary: "가계부를 달력으로, 날마다 쓴 돈이 칸에",
    wow: "가계부가 달력 판이 되어 날마다 쓴 돈이 칸에 적혀요. 이번 달 흐름 카드가 위에서 오늘 쓸 수 있는 돈을 알려 줘요.",
    changes: ["달력 판", "이번 달 흐름 카드", "부드러운 하늘색"], cover: ["#E1F5FE", "#0288D1", "📅"],
    p: { bg: "#F5FBFF", accent: "#0288D1" }, view: "cal", cards: "pace,hero",
  }),
  money({
    title: "영수증 가계부", summary: "감열지 영수증 무늬와 모노 글꼴, 줄은 촘촘하게",
    wow: "가계부가 영수증 종이처럼 지그재그 무늬에 모노 글꼴로 바뀌고, 이번 달 흐름 카드가 영수증 맨 위 합계처럼 서요.",
    changes: ["영수증 무늬·모노 글꼴", "촘촘한 줄·점 표시", "흐름 카드"], cover: ["#FAFAFA", "#424242", "🧾", 0],
    p: { bg: "#FDFDFB", card: "#FFFFFF", accent: "#424242", bgPaint: "pt.zigzag.FFFDFDFB.FFF0F0F0.60.0", titleFont: "vt323", numFont: "jetbrainsmono" },
    look: { rows: "compact", amount: "m", mark: "dot", dayHead: "line" }, cards: "pace,hero,recent",
  }),
  money({
    title: "금액 크게", summary: "금액을 크게, 날짜 머리는 띠로. 한눈에 읽히는 가계부",
    wow: "금액 글자가 크고 굵어지고 날짜 머리가 색 띠가 되어 멀리서도 읽혀요. 맨 위 합계 카드가 이번 달 쓴 돈·들어온 돈을 크게 보여요.",
    changes: ["큰 금액·띠 날짜 머리", "이번 달 합계 카드"], cover: ["#FFF3E0", "#E65100", "🔠"],
    p: { bg: "#FFFAF4", accent: "#E65100", numFont: "anton" }, look: { amount: "l", dayHead: "big", rows: "roomy" }, cards: "hero,pace,recent",
  }),
  money({
    title: "미니멀 가계부", summary: "색과 아이콘을 빼고 숫자만, 흐름 카드 하나",
    wow: "이모지·색 띠를 모두 빼고 날짜와 금액만 남겨요. 위에는 이번 달 흐름 카드 하나만 서요.",
    changes: ["흑백 숫자", "흐름 카드 하나"], cover: ["#FFFFFF", "#000000", "▫️", 0],
    p: { bg: "#FFFFFF", accent: "#000000", expense: "#000000", income: "#000000" }, look: { mark: "none", dayHead: "line", amount: "m" }, cards: "pace,-hero,-recent",
  }),
  money({
    title: "귀여운 동전", summary: "동전 노랑 파스텔, 이모지 분류와 둥근 날짜 띠",
    wow: "분류마다 이모지가 붙고 날짜 띠가 동전처럼 둥글어요. 예산 카드가 남은 돈을 귀엽게 알려 줘요.",
    changes: ["동전 노랑 파스텔", "이모지 분류", "예산 카드"], cover: ["#FFF59D", "#FFB300", "🪙"],
    p: { bg: "#FFFDF0", accent: "#FFB300", titleFont: "jua", dayFill: "#FFF8D6" }, parts: [{ id: "money.day", r: 20 }], cards: "budget,hero,pace",
  }),
  money({
    title: "분류 색 팔레트", summary: "분류 보드에 칸마다 다른 색, 합계가 칸 머리에",
    wow: "분류 보드의 칸 머리가 색으로 나뉘고 칸마다 합계가 서요. 어떤 분류에 돈이 몰리는지 색으로 보여요.",
    changes: ["분류 보드", "칸 머리 색", "예산 카드"], cover: ["#E1BEE7", "#80DEEA", "🎨"],
    p: { bg: "#FBFAFF", accent: "#7E57C2" }, view: "board", parts: [{ id: "money.board.col", r: 14, fill: c("#F3EEFC") }], cards: "budget,hero",
  }),
  money({
    title: "주간 합계 머리", summary: "가계부 머리에 이번 달 흐름, 날짜 머리는 굵은 띠",
    wow: "가계부 맨 위 머리가 그라데이션 카드가 되고 바로 아래 이번 달 흐름이 서요. 날짜 머리 띠가 굵어 주가 바뀌는 곳이 또렷해요.",
    changes: ["그라데이션 머리", "흐름 카드", "굵은 날짜 띠"], cover: ["#3949AB", "#00ACC1", "📊"],
    p: { bg: "#F6F8FD", accent: "#3949AB", headPaint: "lg.120.FF3949AB,FF00ACC1", headInk: "#FFFFFF" }, look: { dayHead: "big" }, cards: "pace,hero,recent",
  }),
  money({
    title: "지출 열지도 가계부", summary: "가계부 달력 판에 금액, 큰 지출 줄은 분홍(고침 뒤)",
    wow: "가계부 달력에서 날마다 쓴 돈이 보이고, 목록으로 바꾸면 큰 지출 줄이 분홍으로 칠해져요(고침 뒤). 반복 지출 찾기가 숨은 고정비를 찾아요.",
    changes: ["달력 판", "반복 지출 찾기", "큰 지출 줄(고침 뒤)"], cover: ["#FFEBEE", "#EF5350", "🌡️"],
    p: { bg: "#FFF9F9", accent: "#EF5350" }, view: "cal", cards: "repeat,pace,hero", dated: [D.bigRow(80000)],
  }),
  // ---- 유형에 맞춘 가계부 ----
  money({
    title: "가게 매출 장부", styles: ["BUSINESS"], summary: "매출 나눠 보기와 일감·매출 돈 카드, 카드 수수료까지",
    wow: "'매출 나눠 보기'가 카드·현금·이체·배달앱 비율을, '일감·매출 돈'이 카드 매출 입금 예정일(정산 3일)을 보여 줘요.",
    changes: ["매출 나눠 보기·일감·매출 돈 카드", "카드 수수료 1.5%, 정산 3일", "장부 초록"], cover: ["#1B5E20", "#C8E6C9", "📒"],
    p: { bg: "#F4F9F4", accent: "#2E7D32" }, cards: "salessplit,work,hero,cardbill,recent",
    extra: [{ kind: "shop", cardFee: 1.5, settleDays: 3, costs: ["재료비", "포장재", "임대료", "공과금"] }],
  }),
  money({
    title: "현장 장비비 장부", styles: ["FIELD"], summary: "일감·매출 돈과 받을 돈, 장비·유류비 분류 보드",
    wow: "가계부가 장비·유류비·식대 분류 보드가 되고, 위에서는 '받을 돈'과 '일감·매출 돈'이 안 받은 일당과 입금을 맞춰 줘요.",
    changes: ["분류 보드", "받을 돈·일감 돈·3.3% 합계 카드", "공구함 빨강"], cover: ["#C62828", "#607D8B", "🧰"],
    p: { bg: "#FAF7F7", accent: "#C62828" }, view: "board", cards: "owed,work,freelance,hero",
    extra: [{ kind: "field", mode: "DAY_RATE", chips: [0.5, 1, 1.5], kinds: ["본작업", "보수"], costs: ["장비", "유류비", "식대", "자재비"] }],
  }),
  money({
    title: "3.3% 프리랜서 장부", styles: ["FIELD", "HOURLY"], summary: "일한 곳별 뗀 3.3%와 5월 신고 준비를 맨 위에",
    wow: "'3.3% 합계' 카드가 일한 곳마다 뗀 세금을 모아 5월 종합소득세 신고 준비를 도와요. 받을 돈 카드가 아직 안 들어온 돈을 잡아요.",
    changes: ["3.3% 합계·받을 돈·세금 카드", "프리랜서 먹색"], cover: ["#ECEFF1", "#263238", "🧾"],
    p: { bg: "#F7F8F9", accent: "#263238" }, cards: "freelance,owed,tax,hero",
  }),
  money({
    title: "투자 입출금 표", styles: ["INVEST"], summary: "투자 계좌로 넣고 뺀 돈을 표로, 통장 잔액 카드",
    wow: "가계부가 표가 되어 투자 계좌로 넣고 뺀 돈이 날짜 순으로 정리되고, 통장·카드 카드가 잔액을 보여 줘요. 시세나 종목은 다루지 않아요.",
    changes: ["표 판", "통장·카드·이번 달 흐름 카드", "짙은 초록"], cover: ["#0B3D2E", "#A5D6A7", "💼"],
    p: { bg: "#F4F8F6", accent: "#1B5E20" }, view: "table", cards: "accounts,pace,hero",
  }),
  money({
    title: "덜 적는 가계부", summary: "결제 알림 읽기·문자 붙여넣기 안내를 맨 위에, 새 기록 확인",
    wow: "맨 위 '덜 적는 법'이 결제 알림 읽기와 문자 붙여넣기를 켜는 곳을 알려 주고, '새로 들어온 기록'에서 저절로 적힌 것을 확인만 해요.",
    changes: ["덜 적는 법·새로 들어온 기록·확인할 것 카드", "산뜻한 민트"], cover: ["#E0F2F1", "#00897B", "🤖"],
    p: { bg: "#F4FBFA", accent: "#00897B" }, cards: "auto,new,ask,hero,recent",
  }),
  money({
    title: "카드 소득공제 문턱", styles: ["SHIFT", "DAY", "HOURLY"], summary: "총급여 25% 문턱까지 남은 카드 사용, 신용·체크 고르기",
    wow: "'카드 소득공제 문턱' 카드가 올해 카드 사용이 총급여의 25%를 넘었는지, 이제 체크카드로 바꿀 때인지 알려 줘요.",
    changes: ["카드 소득공제 문턱·다음 카드값 카드", "깔끔한 파랑"], cover: ["#E3F2FD", "#1565C0", "💳"],
    p: { bg: "#F5F9FF", accent: "#1565C0" }, cards: "card25,cardbill,hero",
  }),
];
