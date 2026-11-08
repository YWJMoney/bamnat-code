// 1.38 [STM-08] 기능별 작품의 '기능 속 기능' 조각: 수식 줄(lines)·메모 틀(tpls)과, 소개에 그대로 쓰는 설명(say)·바뀌는 것(tag).
// 설명은 조각이 실제로 하는 일에서만 나와서, 작품 소개가 동작과 어긋나지 않아요.
import { c } from "../../market-136/make/lib.mjs";
import { C, D, T } from "../../market-136/make/kit.mjs";

const fx = (src) => ({ fx: src });
const f = (say, tag, lines = [], more = {}) => ({ say, tag, lines: Array.isArray(lines) ? lines : [lines], ...more });

// ---- 달력 칸 (cal.*) ----
export const CAL = {
  nightDeep: (a = "#7986CB", b = "#3949AB") => f("야간이 이어질수록 칸이 한 단계씩 짙어지고 세 번째부터 반짝여요.", "야간 이어질수록 짙은 칸",
    C.nightDeep(`lg.180.FF${a.slice(1)},00FFFFFF`, `lg.180.FF${b.slice(1)},FF${a.slice(1)}`)),
  fatigue: (col) => f("쉬지 않고 이어 일한 날이 길수록 테두리가 굵어져요.", "연속근무 피로 테두리", C.fatigue(col)),
  recovery: (col) => f("그 주 야간이 세 번 넘은 뒤 쉬는 날은 회복 색으로 바뀌어요.", "회복 날 색", C.recovery(col)),
  nightMonth: () => f("이달 야간이 여덟 번을 넘으면 야간 칸 표시가 별빛처럼 빛나요.", "야간 많은 달 별빛", C.nightMonth()),
  spendHeat: (lo, mid, hi) => f("쓴 돈이 많은 날일수록 칸이 노랑 → 주황 → 빨강으로 물들어요.", "쓴 돈 열지도", C.spendHeat(lo, mid, hi)),
  hoursHeat: (col) => f("그날 일한 시간만큼 칸 색이 진해져요.", "근무 시간 진하기", C.hoursHeat(col)),
  checkRing: () => f("그날 체크 목록을 다 끝내면 초록, 하다 말면 노란 고리가 둘러져요.", "체크율 고리", C.checkRing()),
  tagLine: (tag, col) => f(`‘${tag}’ 꼬리표를 단 날은 테두리가 생겨요.`, `${tag} 날 테두리`, C.tagLine(tag, col)),
  tagFill: (tag, col, a) => f(`‘${tag}’ 꼬리표를 단 날은 칸이 칠해져요.`, `${tag} 날 칸 색`, C.tagFill(tag, col, a)),
  holidayWork: (col) => f("공휴일에 일한 날은 금색 이중 테두리가 둘러져요.", "공휴일 근무 금테", C.holidayWork(col)),
  weekendWork: (col) => f("주말에 일한 날은 점선 테두리로 남아요.", "주말 근무 점선", C.weekendWork(col)),
  week52: () => f("그 주 일한 시간이 52시간을 넘으면 그 주 날짜가 빨갛게 밑줄이 그어져요.", "주 52시간 넘은 주", C.week52()),
  week15: () => f("그 주 일한 시간이 15시간을 넘으면 그 주 근무 날짜에 금색 밑줄이 생겨요(주휴수당 조건).", "주 15시간 금색 밑줄", C.week15()),
  incomeDay: (col) => f("돈이 들어온 날은 칸에 금빛이 번지고 반짝여요.", "돈 들어온 날 금빛", C.incomeDay(col)),
  noSpend: (col) => f("한 푼도 안 쓴 지난날은 날짜가 초록으로 바뀌어 무지출 날이 모여요.", "무지출 날 초록", C.noSpend(col)),
  busyMemo: () => f("메모가 세 개 넘는 바쁜 날은 칸이 숨 쉬듯 깜빡여요.", "바쁜 날 숨쉬기", C.busyMemo()),
  leave: (col) => f("휴가를 쓴 날은 칸이 하늘색 점선으로 표시돼요.", "휴가 날 표시", C.leave(col)),
  dday: (tag = "마감") => f(`‘${tag}’ 꼬리표 날이 다가올수록 테두리가 파랑 → 주황 → 빨강으로 바뀌고 지나면 회색이 돼요.`, `${tag} 다가오는 색`, C.dday(tag)),
  todayClock: () => f("오늘 칸이 지금 시각에 따라 새벽·아침·낮·저녁 색으로 바뀌어요.", "오늘 칸 시각 색", C.todayClock()),
  salesGoal: (goal) => f("하루 매출이 목표를 넘은 날은 칸이 금빛으로 채워져요(목표는 넣을 때 정해요).", "매출 목표 금빛 칸", C.salesGoal(goal),
    { inputs: [{ label: "하루 목표 매출", kind: "money", min: 0, default: goal ?? 500000, hint: "하루 매출 목표" }] }),
  longDay: (col) => f("10시간 넘게 일한 날은 굵은 테두리가 생겨요.", "긴 날 굵은 테두리", C.longDay(col)),
  spendLine: (hi) => f("쓴 돈이 큰 날은 칸 금액이 빨갛고 굵게 남아요.", "큰 지출 빨간 금액", C.spendLine(hi)),
  early: (col) => f("7시 전에 시작하는 날은 날짜가 해 뜨는 색으로 바뀌어요.", "이른 출근 날", C.early(col)),
  overBudget: (b) => f("이달 지출이 예산을 넘으면 그날부터 날짜에 가운데 줄이 그어져요(예산은 넣을 때 정해요).", "예산 넘은 날 줄", C.overBudget(b),
    { inputs: [{ label: "월 예산", kind: "money", min: 0, default: b ?? 1500000, hint: "한 달에 쓸 돈" }] }),
  spendHours: () => f("그날 쓴 돈이 근무 몇 시간어치인지에 따라, 세 시간어치를 넘으면 칸 금액이 주황, 여덟 시간어치를 넘으면 빨강이 돼요.",
    "쓴 돈 = 근무 몇 시간", { id: "cal.cell.spend", when: "지출근무시간 >= 3", ink: fx('만약(지출근무시간 >= 8, "#D32F2F", "#EF6C00")'), weight: 800 }),
  payDayCount: () => f("다음 월급날까지 남은 날이 사흘 안쪽이면 오늘 칸에 금색 고리가 생겨요.", "월급 사흘 전 고리",
    { id: "cal.cell", on: "today", when: "월급까지 <= 3", line: "double", lineW: 2.5, lineC: c("#FFB300") }),
  dayPay: () => f("그날 일로 번 돈이 클수록 칸이 초록으로 진해져요.", "그날 번 돈 진하기",
    { id: "cal.cell", when: "그날급여 > 0", fill: c("#43A047"), fillA: fx("최소(70, 반올림(그날급여 / 3000))") }),
  balanceLow: (lo = 100000) => f("앞으로 통장 잔액이 낮아질 날은 칸 아래가 빨갛게 표시돼요.", "잔액 모자랄 날 경고",
    { id: "cal.cell", when: `아님 지난날 그리고 예상잔액 < ${lo}`, line: "dash", lineW: 1.5, lineC: c("#E53935") }),
  weekendSoft: (col) => f("주말 쉬는 날은 칸이 부드러운 색으로 쉬어요.", "주말 쉬는 날 색",
    { id: "cal.cell", when: "주말 그리고 쉬는날", fill: c(col), fillA: 35 }),
  firstDay: () => f("매달 1일은 날짜가 굵고 크게, 새 달의 시작이 보여요.", "1일 굵게", { id: "cal.cell.date", when: "일 = 1", weight: 900, size: 15 }),
  categoryDay: (kind, col) => f(`‘${kind}’에 돈을 쓴 날은 칸 아래에 그 색 줄이 생겨요.`, `${kind} 쓴 날 표시`,
    { id: "cal.cell", when: `지출분류.포함("${kind}")`, line: "solid", lineW: 1.5, lineC: c(col) }),
  memoDayDot: (col) => f("메모가 있는 날은 날짜 아래 점이 진해져요.", "메모 있는 날 점", { id: "cal.cell.memo", when: "메모수 > 0", ink: c(col), weight: 800 }),
  pastDim: () => f("지난날 중 근무가 없던 날은 흐려져서 앞날이 또렷해요.", "지난 쉬는 날 흐리게", { id: "cal.cell", when: "지난날 그리고 쉬는날", alpha: 55 }),
};

// ---- 메모 (memo.*) ----
export const MEMO = {
  due: () => f("마감 칸을 적은 메모는 빨간 테두리로 눈에 띄어요.", "마감 메모 빨간 테두리", D.memoDue()),
  done: () => f("체크를 다 끝낸 메모는 흐려져서 남은 일만 또렷해요.", "끝낸 메모 흐리게", D.memoDone()),
  pinned: () => f("고정한 메모는 금색 테두리로 맨 위에서 빛나요.", "고정 메모 금테", D.memoPinned()),
  colorTag: (n, col) => f(`색 ${n}번 메모는 그 색 띠가 왼쪽에 생겨요.`, `색 ${n}번 띠`, { id: "memo.card", when: `색번호 = ${n}`, line: "solid", lineW: 3, lineC: c(col) }),
  longBody: () => f("글이 긴 메모는 살짝 큰 카드로 보여요.", "긴 메모 크게", { id: "memo.card.body", when: "길이(내용) >= 80", size: 13 }),
  urgentTitle: (word = "급함") => f(`제목에 ‘${word}’이 들어간 메모는 제목이 빨갛고 굵어요.`, `${word} 제목 빨갛게`,
    { id: "memo.card.title", when: `포함(제목, "${word}")`, ink: c("#D32F2F"), weight: 900 }),
  todayMemo: () => f("오늘 날짜 메모는 포인트 색 테두리가 둘러져요.", "오늘 메모 테두리", { id: "memo.card", when: "오늘", line: "solid", lineW: 2, lineC: c("#3F51B5") }),
  oldDim: () => f("지난 날짜의 메모는 조금 흐려져요.", "지난 메모 흐리게", { id: "memo.card", when: "지난날 그리고 아님 고정", alpha: 70 }),
  boardFull: (n = 5) => f(`보드 한 칸에 메모가 ${n}개를 넘으면 그 칸 머리가 주황이 돼요.`, "꽉 찬 칸 주황 머리",
    { id: "memo.board.head", when: `칸수 > ${n}`, fill: c("#FFE0B2"), ink: c("#E65100") }),
};

// ---- 가계부 (money.*) ----
export const MONEY = {
  bigRow: (hi) => f("큰 지출 줄은 연한 빨강 바탕으로 눈에 띄어요.", "큰 지출 줄 강조", D.bigRow(hi)),
  bigTable: (hi) => f("표 판에서도 큰 지출 줄이 연한 빨강이에요.", "표 큰 지출 강조", D.bigTableRow(hi)),
  incomeRow: () => f("돈이 들어온 줄은 연한 초록 바탕이에요.", "수입 줄 초록", { id: "money.row", when: '종류 = "수입"', fill: c("#E8F5E9"), r: 10 }),
  catRow: (cat, col) => f(`‘${cat}’ 줄은 그 색 띠로 바로 보여요.`, `${cat} 줄 색`, { id: "money.row", when: `분류 = "${cat}"`, line: "solid", lineW: 2, lineC: c(col), r: 10 }),
  hoursRow: () => f("한 줄 금액이 근무 몇 시간어치인지, 다섯 시간을 넘는 지출은 금액이 주황이에요.", "지출 = 근무 시간",
    { id: "money.amount", on: "out", when: "지출근무시간 >= 5", ink: c("#EF6C00"), weight: 900 }),
  boardOver: (lim = 300000) => f("분류 보드에서 한 칸 합계가 기준을 넘으면 그 칸 머리가 빨개요.", "넘친 분류 빨간 머리",
    { id: "money.board.col", when: `칸합계 > ${lim}`, fill: c("#FFCDD2"), ink: c("#B71C1C") }),
  calSpend: () => f("가계부 달력 판에서 쓴 돈이 많은 날일수록 칸이 물들어요.", "가계부 달력 열지도",
    { id: "money.cal.cell", when: "지출 >= 30000", fill: fx('만약(지출 >= 100000, "#EF9A9A", 지출 >= 60000, "#FFCC80", "#FFF59D")'), fillA: 70 }),
  calNoSpend: () => f("가계부 달력 판에서 무지출 지난날은 초록 칸이에요.", "무지출 날 초록 칸",
    { id: "money.cal.cell", when: "지난날 그리고 지출 = 0", fill: c("#C8E6C9"), fillA: 70 }),
  dayOver: (hi = 50000) => f("하루 쓴 돈이 기준을 넘은 날은 날짜 머리가 빨개요.", "많이 쓴 날 빨간 머리",
    { id: "money.day", when: `지출 >= ${hi}`, fill: c("#FFEBEE"), ink: c("#C62828") }),
};

// ---- 근무·급여 (pay.*) ----
export const PAY = {
  goal: (def = 3000000) => f("이달 예상 급여가 목표를 넘으면 합계 상자가 금빛으로 빛나요(목표는 넣을 때 정해요).", "목표 급여 금빛",
    { id: "pay.total", when: `이달급여 >= 칸("목표 급여", ${def})`, paint: "lg.120.FFFFD54F,FFFFA000", ink: c("#3E2723"), motion: "shimmer" },
    { inputs: [{ label: "목표 급여", kind: "money", min: 0, default: def, hint: "이달 받고 싶은 급여" }] }),
  nightRow: () => f("이달 야간이 많으면 야간 수당 줄이 굵게 보여요.", "야간 많은 달 수당 줄", { id: "pay.row", when: "이달야간 >= 6", weight: 800, ink: c("#3949AB") }),
  overtime: () => f("이달 일한 시간이 209시간을 넘으면 합계 상자에 주황 테두리가 생겨요.", "209시간 넘은 달",
    { id: "pay.total", when: "이달근무시간 > 209", line: "solid", lineW: 3, lineC: c("#FB8C00") }),
  week15: () => f("이번 주 15시간을 넘으면 합계 상자 글자가 금색으로 바뀌어요(주휴수당 조건).", "주 15시간 금색 합계",
    { id: "pay.total", when: "이번주근무시간 >= 15", ink: c("#FFD54F") }),
};

// ---- 메모 틀 더 (T에 없는 것) ----
export const TPL = {
  ...T,
  grocery: { title: "장보기", emoji: "🛒", check: ["채소", "과일", "달걀·우유", "고기·생선", "생활용품"], body: "이달 쓴 돈 {원(이달지출)}" },
  habit: { title: "습관 체크", emoji: "🌱", check: ["물 여덟 잔", "걷기 30분", "스트레칭", "일찍 자기"], asks: [{ label: "오늘 기분", type: "star" }] },
  gratitude: { title: "감사 일기", emoji: "🙏", body: "{날짜} {요일}\n고마운 것 세 가지:\n1. \n2. \n3. " },
  workout: { title: "운동 기록", emoji: "🏃", body: "운동: \n시간: \n느낌: ", asks: [{ label: "운동 시간", type: "num" }, { label: "힘든 정도", type: "star" }] },
  meal: { title: "식단 기록", emoji: "🥗", check: ["아침", "점심", "저녁", "간식"], body: "{근무} · 물 몇 잔: " },
  mood: { title: "기분 날씨", emoji: "🌦️", body: "오늘 마음 날씨: \n왜 그랬을까: ", asks: [{ label: "기분", type: "pick", choices: ["맑음", "구름", "비", "번개"] }] },
  travel: { title: "여행 준비", emoji: "🧳", check: ["여권·신분증", "충전기", "상비약", "숙소 확인", "교통편"], asks: [{ label: "출발", type: "date" }] },
  wishlist: { title: "사고 싶은 것", emoji: "🛍️", body: "물건: \n값: \n정말 필요한가: ", asks: [{ label: "얼마나 갖고 싶나", type: "star" }, { label: "값", type: "num" }] },
  idea: { title: "아이디어", emoji: "💡", body: "떠오른 것: \n다음 한 걸음: ", asks: [{ label: "상태", type: "pick", choices: ["씨앗", "키우는 중", "열매"] }] },
  medicine: { title: "약 먹기", emoji: "💊", check: ["아침 약", "점심 약", "저녁 약"], body: "{근무} 근무 날 · 근무 시작 {근무시작}" },
  petCare: { title: "반려동물", emoji: "🐾", check: ["밥", "물", "산책", "약·영양제"], asks: [{ label: "몸무게", type: "num" }] },
  cleaning: { title: "청소 당번", emoji: "🧹", check: ["설거지", "빨래", "분리수거", "화장실", "청소기"] },
  study2: { title: "자격증 공부", emoji: "📖", body: "과목: \n오늘 범위: \n틀린 것: ", asks: [{ label: "공부 시간", type: "num" }, { label: "시험일", type: "date" }] },
  project: { title: "프로젝트", emoji: "🚀", body: "목표: \n이번 주 할 것: ", asks: [{ label: "상태", type: "pick", choices: ["준비", "하는 중", "검토", "끝"] }, { label: "마감", type: "date" }] },
  sideJob: { title: "부업 기록", emoji: "💼", body: "일감: \n번 돈: \n든 시간: ", asks: [{ label: "번 돈", type: "num" }, { label: "시간", type: "num" }] },
  sleepLog: { title: "잠 기록", emoji: "🛌", body: "{근무} 끝 {근무끝}\n잠든 시각: \n깬 시각: ", asks: [{ label: "잠 점수", type: "star" }, { label: "잔 시간", type: "num" }] },
  handover2: { title: "교대 전달", emoji: "🔁", check: ["특이 사항", "남은 일", "물품·재고", "다음 근무자 확인"], body: "{근무} {근무시작}~{근무끝}" },
  customer: { title: "단골 기록", emoji: "🤝", body: "이름: \n좋아하는 것: \n다음에 챙길 것: ", asks: [{ label: "방문 수", type: "num" }] },
  stock: { title: "재고 점검", emoji: "📋", check: ["주재료", "부재료", "포장재", "소모품"], body: "오늘 매출 {원(수입)}", repeat: "W1" },
  sitePhoto: { title: "현장 사진 메모", emoji: "📷", check: ["작업 전", "작업 중", "작업 후", "자재 영수증"], body: "{날짜} · {근무시간}시간" },
  watchlist: { title: "관심 목록", emoji: "👀", body: "이름: \n지켜보는 이유: \n살 조건: ", asks: [{ label: "관심도", type: "star" }] },
  dividend: { title: "배당 기록", emoji: "🌳", body: "어디서: \n금액: \n다시 넣을 곳: ", asks: [{ label: "금액", type: "num" }] },
  weekPlan: { title: "주간 계획", emoji: "🗓️", check: ["월", "화", "수", "목", "금", "주말"], body: "이번 주 근무 {이번주근무시간}시간" },
  monthGoal: { title: "이달 목표", emoji: "🎯", check: ["돈", "몸", "일", "나"], body: "{월}월 · 이달 쓴 돈 {원(이달지출)}" },
  recipeNote: { title: "요리 노트", emoji: "🍳", body: "재료: \n순서: \n다음엔: ", asks: [{ label: "맛", type: "star" }] },
};
