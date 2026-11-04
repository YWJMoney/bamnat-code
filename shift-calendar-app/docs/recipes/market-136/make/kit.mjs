// 작품마다 쓰는 수식 줄·규칙·메모 틀. 수식 이름은 core FormulaName(서버 formula.js)과 같아요.
// 달력 월 칸(cal.cell과 그 아래)의 when·{fx}는 지금 폰에서 계산돼요. 날짜가 있는 다른 부품(memo.card, money.row, pay.row, cal.band.slot …)의 when은
// 1.36 FXR-03부터 계산돼요(core Blueprint.DATED). 위젯·요일 줄·보드 칸처럼 날짜 없는 부품에는 조건 줄을 쓰지 않아요.
import { c, fx } from "./lib.mjs";

// ---- 달력 칸 조건 줄 (지금 돌아요) ----
export const C = {
  /** 야간이 이어질수록 칸이 짙어져요(1·2·3번째). */
  nightDeep: (paint1, paint3) => [
    { id: "cal.cell", when: '근무종류 = "야간"', paint: paint1 },
    { id: "cal.cell", when: '근무종류 = "야간" 그리고 연속근무 >= 3', paint: paint3, motion: "shimmer" },
  ],
  /** 쉬지 않고 이어 일한 날이 길수록 테두리가 굵어져요. */
  fatigue: (color = "#E53935") => ({ id: "cal.cell", when: "연속근무 >= 4", line: "solid", lineC: c(color),
    lineW: fx("만약(연속근무 >= 7, 3.5, 연속근무 >= 6, 2.5, 연속근무 >= 5, 2, 1.5)") }),
  /** 쓴 돈만큼 칸이 물들어요(열지도). */
  spendHeat: (lo = 20000, mid = 50000, hi = 100000) => ({ id: "cal.cell", when: `지출 >= ${lo}`,
    fill: fx(`만약(지출 >= ${hi}, "#EF9A9A", 지출 >= ${mid}, "#FFCC80", "#FFF59D")`), fillA: 70 }),
  /** 근무 시간만큼 칸이 진해져요. */
  hoursHeat: (color = "#1E88E5") => ({ id: "cal.cell", when: "근무시간 > 0", fill: c(color),
    fillA: fx("최소(80, 반올림(근무시간 * 6))") }),
  /** 그날 체크 목록을 다 끝내면 초록, 하다 말면 노랑. */
  checkRing: () => ({ id: "cal.cell", when: "메모수 > 0 그리고 (체크율 > 0 또는 남은체크 > 0)", line: "solid", lineW: 2,
    lineC: fx('만약(체크율 >= 100, "#43A047", "#FFB300")') }),
  /** 꼬리표가 붙은 날 테두리. */
  tagLine: (tag, color, style = "solid") => ({ id: "cal.cell", when: `꼬리표.포함("${tag}")`, line: style, lineW: 2, lineC: c(color) }),
  /** 꼬리표가 붙은 날 칸 색. */
  tagFill: (tag, color, a = 35) => ({ id: "cal.cell", when: `꼬리표.포함("${tag}")`, fill: c(color), fillA: a }),
  /** 공휴일에 일하는 날 금색 테두리. */
  holidayWork: (color = "#FFB300") => ({ id: "cal.cell", when: "공휴일 그리고 아님 쉬는날", line: "double", lineW: 2.5, lineC: c(color) }),
  /** 주말에 일하는 날 점선. */
  weekendWork: (color = "#8E24AA") => ({ id: "cal.cell", when: "주말 그리고 아님 쉬는날", line: "dash", lineW: 1.5, lineC: c(color) }),
  /** 그 주 일한 시간이 52시간을 넘으면 그 주 칸 날짜가 빨개요. */
  week52: (color = "#D32F2F") => ({ id: "cal.cell.date", when: "이번주근무시간 > 52", ink: c(color), deco: "under", weight: 800 }),
  /** 주 15시간을 넘긴 주의 근무 날(주휴수당 조건) 금색 밑줄. */
  week15: (color = "#F9A825") => ({ id: "cal.cell.date", when: "이번주근무시간 >= 15 그리고 아님 쉬는날", ink: c(color), deco: "under", weight: 800 }),
  /** 돈이 들어온 날 금빛. */
  incomeDay: (color = "#FFD54F") => ({ id: "cal.cell", when: "수입 > 0", paint: `rg.50.40.90.${color.replace("#", "FF")},00FFFFFF`, motion: "shimmer" }),
  /** 지난날인데 쓴 돈이 없으면(무지출) 날짜가 초록. */
  noSpend: (color = "#2E7D32") => ({ id: "cal.cell.date", when: "지난날 그리고 지출 = 0", ink: c(color), weight: 800 }),
  /** 메모가 세 개 넘는 날 칸이 숨 쉬어요. */
  busyMemo: () => ({ id: "cal.cell", when: "메모수 >= 3", motion: "pulse" }),
  /** 휴가 날. */
  leave: (color = "#00ACC1") => ({ id: "cal.cell", when: "아님 비었나(휴가)", fill: c(color), fillA: 30, line: "dot", lineW: 1.5, lineC: c(color) }),
  /** 마감 꼬리표 날이 다가올수록 파랑 → 주황 → 빨강, 지나면 회색. */
  dday: (tag = "마감") => ({ id: "cal.cell", when: `꼬리표.포함("${tag}")`, line: "solid", lineW: 2.5,
    lineC: fx('만약(며칠뒤 < 0, "#9E9E9E", 며칠뒤 <= 3, "#E53935", 며칠뒤 <= 7, "#FB8C00", "#1E88E5")'), motion: "ring" }),
  /** 그 주 야간이 세 번 넘는데 쉬는 날이면 '회복' 색. */
  recovery: (color = "#A5D6A7") => ({ id: "cal.cell", when: "쉬는날 그리고 이번주야간 >= 3", fill: c(color), fillA: 55 }),
  /** 오늘 칸이 지금 시각에 따라 아침·낮·저녁·밤 색. */
  todayClock: () => ({ id: "cal.cell", on: "today", when: "오늘", paint: fx('만약(지금시각 < "06:00", "lg.135.FF1A237E,FF3949AB", 지금시각 < "12:00", "lg.135.FFFFE082,FFFFB74D", 지금시각 < "18:00", "lg.135.FF81D4FA,FF4FC3F7", "lg.135.FF7E57C2,FF283593")') }),
  /** 하루 매출(가계부 수입)이 목표를 넘으면 금색 칸. */
  salesGoal: (goal = 500000) => ({ id: "cal.cell", when: `수입 >= 칸("하루 목표 매출", ${goal})`, fill: c("#FFE082"), fillA: 80, line: "solid", lineW: 2, lineC: c("#FFB300") }),
  /** 그날 일한 시간이 길면(10시간 넘게) 굵은 테두리: 현장 1.5공수 날. */
  longDay: (color = "#F57C00") => ({ id: "cal.cell", when: "근무시간 >= 10", line: "solid", lineW: 2.5, lineC: c(color) }),
  /** 그날 쓴 돈 줄을 금액에 따라 굵게·빨갛게. */
  spendLine: (hi = 50000) => ({ id: "cal.cell.spend", when: `지출 >= ${hi}`, ink: c("#D32F2F"), weight: 800 }),
  /** 근무 시작이 일찍(7시 전)이면 날짜 위 해 뜨는 색. */
  early: (color = "#FF8F00") => ({ id: "cal.cell.date", when: '아님 비었나(근무시작) 그리고 근무시작 < "07:00"', ink: c(color), weight: 800 }),
  /** 그날 이번 달 지출이 예산을 넘었으면 날짜에 가운데 줄. */
  overBudget: (budget = 1500000) => ({ id: "cal.cell.date", when: `이달지출 > 칸("월 예산", ${budget})`, deco: "strike", ink: c("#C62828") }),
  /** 이달 야간이 많은 달(8번 넘게)엔 야간 칸에 별빛. */
  nightMonth: () => ({ id: "cal.cell.mark", when: '근무종류 = "야간" 그리고 이달야간 >= 8', tshadow: 6, tshadowC: c("#FFF59D"), tshadowX: 0, tshadowY: 0 }),
};

// ---- 날짜가 있는 다른 부품의 조건 줄 (FXR-03) ----
export const D = {
  /** 마감 칸(속성)이 가까운 메모 카드 테두리. */
  memoDue: () => ({ id: "memo.card", when: '아님 비었나(칸("마감"))', line: "solid", lineW: 2, lineC: c("#E53935") }),
  /** 체크를 다 끝낸 메모 카드는 흐리게. */
  memoDone: () => ({ id: "memo.card", when: "체크율 >= 100", alpha: 55 }),
  /** 고정 메모는 금색. */
  memoPinned: () => ({ id: "memo.card", when: "고정", line: "solid", lineW: 2, lineC: c("#FFB300") }),
  /** 큰 지출 줄 강조. */
  bigRow: (hi = 50000) => ({ id: "money.row", when: `종류 = "지출" 그리고 금액 >= ${hi}`, fill: c("#FFEBEE"), r: 10 }),
  /** 분류 보드 한 칸 합계가 예산을 넘으면 빨간 머리. */
  /** 표 판의 큰 지출 줄. */
  bigTableRow: (hi = 50000) => ({ id: "money.table.row", when: `종류 = "지출" 그리고 금액 >= ${hi}`, fill: c("#FFEBEE") }),
  /** 그 주 근무 띠 칸: 야간 이어지면 짙게. */
  bandNight: () => ({ id: "cal.band.slot", when: '근무종류 = "야간" 그리고 연속근무 >= 3', fill: c("#283593"), ink: c("#FFFFFF") }),
  /** 주간 판의 하루: 쓴 돈 많으면 빨간 밑줄. */
  weeklySpend: () => ({ id: "cal.weekly.day", when: "지출 >= 50000", line: "solid", lineW: 1.5, lineC: c("#E53935") }),
};

// ---- 규칙·단추 (지금 돌아요). 데이터를 쓰는 동작은 넣을 때 묻고 되돌릴 수 있어요 ----
export const R = {
  nightSleep: { name: "야간 끝 잠자리 알림", on: "end", role: "night", min: 20, do: "notify", arg: "야간 {이번주야간}번째 끝. 암막 커튼 치고 푹 자요" },
  nightRecover: { name: "야간 세 번째 회복", on: "end", role: "night", when: "이번주야간 >= 3", do: "notify", arg: "이번 주 야간 {이번주야간}번째예요. 다음 쉬는 날은 회복이 먼저예요" },
  streak: { name: "연속근무 경고", on: "start", min: -60, when: "연속근무 >= 5", do: "notify", arg: "{연속근무}일째 이어 일해요. 물과 간식을 챙겨요" },
  handoverNew: { name: "근무 전 인수인계 새로", on: "start", min: -30, do: "newMemo", arg: "인수인계" },
  prepOpen: { name: "출근 전 준비물", on: "start", min: -90, do: "memoTpl", arg: "근무 준비물" },
  handoverLeft: { name: "인수인계 남은 것", on: "memo", when: '꼬리표.포함("인수인계") 그리고 남은체크 > 0', do: "notify", arg: "인수인계 {남은체크}개가 남았어요" },
  friRetro: { name: "금요일 주간 회고", on: "daily", at: "17:30", when: '요일 = "금"', do: "newMemo", arg: "주간 회고" },
  fixedCosts: { name: "매달 1일 고정비 점검", on: "daily", at: "09:00", when: "일 = 1", do: "newMemo", arg: "고정비 점검" },
  budget90: (b = 1500000) => ({ name: "예산 90% 알림", on: "money", when: `이달지출 >= 칸("월 예산", ${b}) * 0.9`, do: "notify", arg: "이달 쓴 돈 {원(이달지출)}. 예산의 90%를 넘었어요" }),
  holidayPay: { name: "공휴일 근무 메모", on: "end", when: "공휴일", do: "memoLine", arg: "{공휴일이름} 근무 {근무시간}시간: 휴일 수당 확인" },
  dayLog: { name: "근무 끝 한 줄 기록", on: "end", min: 5, do: "memoLine", arg: "{근무} {근무시작}~{근무끝} · {근무시간}시간" },
  closeCheck: { name: "마감 30분 전", on: "daily", at: "20:30", when: "아님 공휴일", do: "notify", arg: "곧 마감이에요. 마감 체크 목록을 열어요" },
  rainMemo: { name: "우천 날 일지", on: "memo", when: '꼬리표.포함("우천")', do: "memoLine", arg: "우천으로 쉼 · 이달 일한 시간 {이달근무시간}시간" },
  tradeJournal: { name: "매매한 날 일지 알림", on: "daily", at: "20:30", when: '꼬리표.포함("매매") 그리고 아님 꼬리표.포함("일지")', do: "notify", arg: "오늘 매매가 있었어요. 산 이유와 마음을 일지에 남겨요" },
  weekHours15: { name: "주 15시간 넘김", on: "end", when: "이번주근무시간 >= 15", do: "notify", arg: "이번 주 {이번주근무시간}시간 일했어요. 주휴수당 조건(15시간)을 넘겼어요" },
  week52: { name: "주 52시간 경고", on: "end", when: "이번주근무시간 > 46", do: "notify", arg: "이번 주 {이번주근무시간}시간이에요. 52시간까지 {52 - 이번주근무시간}시간 남았어요" },
  offTodo: { name: "쉬는 날 할 일", on: "open", when: "쉬는날", do: "memoTpl", arg: "쉬는 날 할 일" },
  openPlan: { name: "아침에 오늘 계획", on: "open", when: "아님 쉬는날 그리고 메모수 = 0", do: "memoTpl", arg: "오늘 계획" },
  shopOpen: { name: "오픈 한 시간 전", on: "daily", at: "08:00", when: "아님 공휴일", do: "notify", arg: "곧 오픈이에요. 오픈 준비 목록을 확인해요" },
  salesDay: { name: "매출 적으면 한 줄", on: "money", when: "수입 > 0", do: "memoLine", arg: "오늘 매출 {원(수입)} · 이달 {원(이달수입)}" },
  dailyMoney: { name: "밤 9시 가계부 알림", on: "daily", at: "21:00", when: "지출 = 0 그리고 아님 지난날", do: "notify", arg: "오늘 쓴 돈을 아직 안 적었어요" },
  waterBreak: { name: "근무 중 물 마시기", on: "start", min: 120, do: "notify", arg: "근무 두 시간째예요. 물 한 잔 마셔요" },
  stretch: { name: "근무 중 스트레칭", on: "start", min: 240, do: "notify", arg: "어깨와 허리를 한 번 펴요" },
  payDayNote: { name: "돈 들어온 날 알림", on: "money", when: "수입 > 0", do: "notify", arg: "들어온 돈 {원(수입)}. 이달 들어온 돈 {원(이달수입)}" },
  holdTpl: (tpl) => ({ name: "길게 눌러 틀 열기", on: "hold", do: "memoTpl", arg: tpl }),
  quarter: { name: "분기 끝 리밸런싱", on: "daily", at: "10:00", when: "중하나(월, 3, 6, 9, 12) 그리고 일 = 25", do: "newMemo", arg: "리밸런싱 점검" },
  saveDay: { name: "매달 적립일", on: "daily", at: "09:30", when: '일 = 칸("적립일", 10)', do: "notify", arg: "오늘은 적립하는 날이에요. 원칙대로 넣어요" },
  vatMonth: { name: "부가세 신고 달", on: "daily", at: "10:00", when: "중하나(월, 1, 7) 그리고 일 = 15", do: "notify", arg: "이달 25일까지 부가세 신고예요. 매출·매입 자료를 모아요" },
};

/** A button. */
export const B = (label, emoji, where, act, show) => ({ label, emoji, where, ...act, ...(show ? { show } : {}) });

// ---- 메모 틀 ({수식}은 틀을 쓸 때 그날 값으로 채워져요) ----
export const T = {
  sbar: { title: "인수인계", emoji: "🩺", check: ["S 상황: 지금 상태", "B 배경: 진단·경과", "A 평가: 변화", "R 요청: 다음 근무에 할 일", "투약·처치 시각"],
    body: "{근무} 근무 · {근무시작}~{근무끝} #인수인계" },
  prep: { title: "근무 준비물", emoji: "🎒", check: ["사원증", "충전기", "물·간식", "여벌 양말", "필기구"] },
  shiftReview: { title: "근무 회고", emoji: "📝", body: "오늘: {근무} {근무시간}시간\n이번 주 야간 {이번주야간}번 · {연속근무}일째\n이달 쓴 돈 {원(이달지출)}\n잘한 것: \n내일 바꿀 것: ",
    asks: [{ label: "컨디션", type: "star" }, { label: "잠", type: "num" }] },
  sleep: { title: "수면 기록", emoji: "😴", body: "{근무} 끝 {근무끝} · 이번 주 야간 {이번주야간}번\n잔 시간: \n깬 횟수: ", asks: [{ label: "잠 점수", type: "star" }] },
  weekRetro: { title: "주간 회고", emoji: "🗓️", body: "이번 주 일한 시간 {이번주근무시간}시간\n이달 쓴 돈 {원(이달지출)}", check: ["잘된 것 세 개", "아쉬운 것 하나", "다음 주 꼭 할 것"] },
  meeting: { title: "회의록", emoji: "🗒️", body: "{날짜} ({요일})\n참석: \n정한 것: \n할 사람: ", asks: [{ label: "마감", type: "date" }] },
  todayPlan: { title: "오늘 계획", emoji: "☀️", body: "{날짜} {요일} · {근무}", check: ["가장 중요한 일", "두 번째 일", "나를 위한 일"] },
  offTodo: { title: "쉬는 날 할 일", emoji: "🌿", body: "쉬는 날 · 지난주 일한 시간 {이번주근무시간}시간", check: ["푹 자기", "장보기", "빨래", "운동 30분", "좋아하는 것 하나"] },
  fixedCosts: { title: "고정비 점검", emoji: "📌", body: "{월}월 고정비 · 지난달까지 쓴 돈은 가계부에서", check: ["월세·관리비", "통신비", "보험", "구독 서비스", "카드값"] },
  todo: { title: "할 일", emoji: "✅", body: "상태: 할 일\n우선: 보통\n마감: ", asks: [{ label: "상태", type: "pick", choices: ["할 일", "하는 중", "끝"] }, { label: "마감", type: "date" }] },
  reading: { title: "독서 기록", emoji: "📚", body: "읽은 쪽: 0\n전체 쪽: 300\n별점: ", asks: [{ label: "별점", type: "star" }, { label: "읽은 쪽", type: "num" }] },
  diary: { title: "하루 일기", emoji: "🌙", body: "{날짜} {요일} · {근무}\n기분: \n고마운 것: ", asks: [{ label: "기분", type: "star" }] },
  closing: { title: "마감", emoji: "🔒", check: ["시재 맞추기", "청소", "재료 정리", "쓰레기", "문단속"], body: "오늘 매출 {원(수입)} · 이달 {원(이달수입)}" },
  shopOpen: { title: "오픈 준비", emoji: "🌅", check: ["간판·조명", "포스 켜기", "재료 확인", "청소 마무리"] },
  order: { title: "발주", emoji: "📦", body: "거래처: \n금액: ", check: ["재료", "포장재", "소모품"], repeat: "W1" },
  fieldLog: { title: "현장 일지", emoji: "📒", body: "{날짜} · {근무시간}시간\n현장: \n공정: \n투입 인원: ", check: ["오늘 한 일", "자재 들어온 것", "내일 할 일", "사진 찍기"] },
  safety: { title: "안전 점검", emoji: "🦺", check: ["안전모·안전화", "추락 방지", "장비 점검", "물·그늘 쉬는 곳"] },
  invoice: { title: "받을 돈", emoji: "💸", body: "일감: \n금액: \n받을 날: \n상태: 받기 전", asks: [{ label: "상태", type: "pick", choices: ["받기 전", "일부", "다 받음"] }, { label: "받을 날", type: "date" }] },
  trade: { title: "매매 일지", emoji: "📈", body: "{날짜} · 이달 쓴 돈 {원(이달지출)}\n산·판 이유: \n원칙 지켰나: \n마음: ", asks: [{ label: "감정 점수", type: "star" }, { label: "원칙 지킴", type: "check" }] },
  rebalance: { title: "리밸런싱 점검", emoji: "⚖️", check: ["목표 비중과 차이", "현금 비중", "산 이유 그대로인가", "다음 분기 계획"] },
  study: { title: "공부 기록", emoji: "🎓", body: "읽은 것: \n배운 것 한 줄: \n시간: ", asks: [{ label: "시간", type: "num" }] },
  dayCash: { title: "하루 돈 정리", emoji: "🧾", body: "{날짜} 쓴 돈 {원(지출)} · 들어온 돈 {원(수입)}\n이달 쓴 돈 {원(이달지출)}" },
};
