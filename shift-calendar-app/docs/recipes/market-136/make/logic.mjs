// 규칙·단추 24 (기획서 4-9, 4-10 26~32): 조각이 '규칙·단추'(logic) 하나뿐인 작품 → 장터 '규칙·단추' 구간. 꾸미기는 바꾸지 않아요.
// 알리기(notify)와 화면 열기는 데이터를 건드리지 않고, 메모 만들기·한 줄 더하기·가계부 넣기·체크 비우기는 넣을 때 무엇을 쓰는지 보여 주고
// 한 일은 '되돌리기'로 거둘 수 있어요(LogicRunner, 안전 모드에서는 쉬어요). 메모 틀 열기(memoTpl)는 받는 사람에게 그 틀이 없을 수 있어 여기서는 쓰지 않아요.
// 칸("…", 기본값)은 받는 사람이 넣을 때 정한 값이에요 (FXR-01, 입력 칸은 index.mjs가 붙여요).
import { ALL, recipe, logic } from "./lib.mjs";
import { R, B } from "./kit.mjs";

const NOTE = "꾸미기는 바뀌지 않아요. 규칙은 스튜디오 › 규칙·단추에서 하나씩 끄거나 지울 수 있고, 규칙이 쓴 것은 되돌릴 수 있어요";

function rule(s) {
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? NOTE, styles: s.styles ?? ALL, cover: s.cover,
      inputs: s.inputs, pieces: [logic({ rules: s.rules, buttons: s.buttons })] }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

const WORK = ["SHIFT", "DAY", "HOURLY", "FIELD"];

export default [
  rule({
    title: "야간 회복 지킴이", styles: ["SHIFT"], summary: "야간 끝나면 잠자리 알림, 이번 주 야간 세 번째면 회복 알림",
    wow: "야간이 끝나고 20분 뒤 '이번 주 야간 몇 번째'를 세어 잠자리를 알려 주고, 세 번째 야간부터는 다음 쉬는 날을 회복에 쓰라고 한 번 더 말해요.",
    changes: ["야간 끝 20분 뒤 잠자리 알림", "야간 세 번째부터 회복 알림", "5일 넘게 이어 일하면 출근 전 경고"], who: "야간이 이어지는 교대 근무자", cover: ["#1A237E", "#9FA8DA", "🛌"],
    rules: [R.nightSleep, R.nightRecover, R.streak], showcase: true,
  }),
  rule({
    title: "인수인계 도우미", styles: ["SHIFT", "DAY"], summary: "근무 30분 전 인수인계 메모를 만들고, 남은 체크를 알려요",
    wow: "근무 30분 전에 그날 '인수인계' 메모가 생기고, #인수인계 메모에 체크가 남아 있으면 몇 개 남았는지 알려 줘요. 근무 날 단추로 바로 만들 수도 있어요.",
    changes: ["근무 30분 전 인수인계 메모", "남은 체크 알림", "근무 날 '인수인계' 단추"], who: "병동·현장처럼 인수인계가 있는 분", cover: ["#00695C", "#B2DFDB", "🩺"],
    rules: [R.handoverNew, R.handoverLeft], buttons: [B("인수인계", "🩺", "day", { do: "newMemo", arg: "인수인계" }, "아님 쉬는날")],
  }),
  rule({
    title: "연속근무 브레이크", styles: WORK, summary: "다섯째 날부터 출근 한 시간 전 경고, 52시간이 가까우면 알림",
    wow: "쉬지 않고 다섯째 날 일하는 날은 출근 한 시간 전에 '몇 일째'를 알려 주고, 이번 주 근무가 46시간을 넘으면 52시간까지 남은 시간을 계산해 줘요.",
    changes: ["연속근무 5일째부터 경고", "주 52시간 남은 시간 알림"], who: "쉬는 날 없이 이어 일하기 쉬운 분", cover: ["#D32F2F", "#FFCDD2", "🛑"],
    rules: [R.streak, R.week52],
  }),
  rule({
    title: "알바 출퇴근 기록", styles: ["HOURLY"], summary: "근무가 끝나면 그날 메모에 시각·시간 한 줄, 15시간 넘으면 알림",
    wow: "근무가 끝나고 5분 뒤 그날 메모에 '오픈 09:00~15:00 · 6시간'처럼 한 줄이 쌓여요. 이번 주 15시간을 넘기면 주휴수당 조건을 넘겼다고 알려요.",
    changes: ["근무 끝 한 줄 기록", "주 15시간 넘김 알림"], who: "근무 시간을 직접 남겨 두고 싶은 알바", cover: ["#FBC02D", "#FFF9C4", "🕘"],
    rules: [R.dayLog, R.weekHours15], showcase: true,
  }),
  rule({
    title: "공휴일 근무 메모", styles: WORK, summary: "공휴일에 일하면 근무 끝에 '휴일 수당 확인' 한 줄",
    wow: "공휴일에 근무가 끝나면 그날 메모에 '광복절 근무 8시간: 휴일 수당 확인'이 쌓여요. 급여 받을 때 빠뜨리지 않아요.",
    changes: ["공휴일 근무 끝 메모 한 줄"], who: "공휴일에도 일하는 분", cover: ["#E53935", "#FFCDD2", "🎌"], rules: [R.holidayPay],
  }),
  rule({
    title: "근무 중 몸 챙기기", styles: WORK, summary: "근무 두 시간째 물 한 잔, 네 시간째 스트레칭 알림",
    wow: "근무가 시작되고 두 시간 뒤 물 마실 때를, 네 시간 뒤 어깨와 허리를 펼 때를 알려 줘요. 근무 날에만 울려요.",
    changes: ["근무 2시간째 물 알림", "근무 4시간째 스트레칭 알림"], who: "바빠서 물도 못 마시는 분", cover: ["#4FC3F7", "#E1F5FE", "💧"],
    rules: [R.waterBreak, R.stretch],
  }),
  rule({
    title: "출근 준비물 챙기기", styles: WORK, summary: "근무 90분 전 준비물 알림, 단추로 준비물 메모 만들기",
    wow: "근무 90분 전 '준비물 챙겨요' 알림이 오고, 근무 날 단추를 누르면 그날 '근무 준비물' 메모가 생겨요. 같은 이름의 메모 틀이 있으면 그 틀로 만들어요.",
    changes: ["근무 90분 전 알림", "근무 날 '준비물' 단추"], cover: ["#8D6E63", "#D7CCC8", "🎒"],
    rules: [{ name: "출근 전 준비물", on: "start", min: -90, do: "notify", arg: "{근무} {근무시작} 근무예요. 준비물을 챙겨요" }],
    buttons: [B("준비물", "🎒", "day", { do: "newMemo", arg: "근무 준비물" }, "아님 쉬는날")],
  }),
  rule({
    title: "금요일 주간 회고", summary: "금요일 저녁 다섯 시 반에 '주간 회고' 메모가 생겨요",
    wow: "매주 금요일 17:30에 그날 '주간 회고' 메모가 생겨요. 같은 이름의 메모 틀이 있으면 그 틀(체크 목록·질문)로 만들어져요.",
    changes: ["금요일 17:30 주간 회고 메모"], cover: ["#5C6BC0", "#C5CAE9", "🗓️"], rules: [R.friRetro],
  }),
  rule({
    title: "매달 1일 고정비 점검", summary: "1일 아침 '고정비 점검' 메모, 단추로 가계부에서 확인",
    wow: "매달 1일 아침 9시에 '고정비 점검' 메모가 생기고, 가계부의 '고정비' 단추를 누르면 가계부 화면이 열려요.",
    changes: ["1일 09:00 고정비 점검 메모", "가계부 '고정비' 단추"], cover: ["#3949AB", "#E8EAF6", "📌"],
    rules: [R.fixedCosts], buttons: [B("고정비", "📌", "money", { do: "newMemo", arg: "고정비 점검" })],
  }),
  rule({
    title: "예산 90% 경보", summary: "이달 쓴 돈이 예산의 90%를 넘으면 기록할 때 알림",
    wow: "가계부에 적을 때마다 이달 쓴 돈을 다시 계산해 예산의 90%를 넘으면 바로 알려 줘요.",
    changes: ["가계부 기록 때 예산 90% 알림"], note: "월 예산은 넣을 때 내 금액으로 정해요. " + NOTE,
    cover: ["#FB8C00", "#FFE0B2", "🚨"], rules: [R.budget90(1500000)],
  }),
  rule({
    title: "밤 9시 가계부", summary: "오늘 쓴 돈을 아직 안 적었으면 밤 9시에 알림",
    wow: "밤 9시에 오늘 가계부가 비어 있으면 알려 줘요. 적었으면 울리지 않아요.",
    changes: ["21:00 가계부 알림(적은 날은 조용)"], cover: ["#00897B", "#B2DFDB", "🌙"], rules: [R.dailyMoney],
  }),
  rule({
    title: "무지출 도전", summary: "밤 10시에 오늘 쓴 돈이 없으면 그날 메모에 '무지출' 한 줄",
    wow: "밤 10시까지 가계부에 지출이 없으면 그날 메모에 '무지출 ✓'과 이달 쓴 돈이 한 줄로 쌓여요. 달력에서 무지출 날을 세기 쉬워요.",
    changes: ["22:00 무지출 한 줄", "이달 쓴 돈 같이 기록"], cover: ["#7CB342", "#F1F8E9", "🌱"],
    rules: [{ name: "무지출 기록", on: "daily", at: "22:00", when: "지출 = 0", do: "memoLine", arg: "무지출 ✓ · 이달 쓴 돈 {원(이달지출)}" }],
  }),
  rule({
    title: "자주 쓰는 지출 단추", summary: "가계부에 커피·점심·교통 단추, 한 번 누르면 오늘 지출로",
    wow: "가계부 위에 커피·점심·교통 단추가 생겨 누르면 바로 그 금액이 오늘 지출로 들어가요. 금액은 넣을 때 내 값으로 바꿔요. 잘못 눌렀으면 되돌리기로 지워요.",
    changes: ["커피·점심·교통 단추", "누르면 오늘 지출로"], cover: ["#6D4C41", "#FFE0B2", "☕"],
    inputs: [{ id: "i1", label: "커피", kind: "money", default: 4500, min: 0, hint: "커피 한 잔" },
      { id: "i2", label: "점심", kind: "money", default: 9000, min: 0, hint: "점심 한 끼" },
      { id: "i3", label: "교통", kind: "money", default: 1500, min: 0, hint: "버스·지하철 한 번" }],
    buttons: [B("커피", "☕", "money", { do: "addMoney", arg: "카페", amount: { input: "i1" } }), B("점심", "🍚", "money", { do: "addMoney", arg: "식비", amount: { input: "i2" } }),
      B("교통", "🚌", "money", { do: "addMoney", arg: "교통", amount: { input: "i3" } })],
  }),
  rule({
    title: "돈 들어온 날 알림", summary: "수입을 적으면 그날 들어온 돈과 이달 합계를 알려요",
    wow: "가계부에 수입을 적는 순간 '들어온 돈 얼마, 이달 들어온 돈 얼마'를 계산해 알려 줘요. 돈 들어오는 날 한눈에 확인해요.",
    changes: ["수입 기록 때 이달 합계 알림"], cover: ["#F9A825", "#FFF8E1", "💰"], rules: [R.payDayNote],
  }),
  rule({
    title: "오늘 요약 한 줄", summary: "앱을 열면 오늘 근무와 이달 쓴 돈을 한 줄로 알려요",
    wow: "그날 처음 앱을 열 때 '오늘 주간 09:00~18:00 · 이달 쓴 돈 얼마'처럼 오늘 요약이 한 줄로 떠요.",
    changes: ["앱 열 때 오늘 요약"], cover: ["#3F51B5", "#E8EAF6", "☀️"],
    rules: [{ name: "오늘 요약", on: "open", do: "notify", arg: "오늘 {근무} {근무시작}~{근무끝} · 이달 쓴 돈 {원(이달지출)}" }],
  }),
  rule({
    title: "쉬는 날 할 일", summary: "쉬는 날 앱을 열면 할 일 알림, 단추로 할 일 메모",
    wow: "쉬는 날 앱을 열면 '쉬는 날이에요. 지난주 몇 시간 일했어요'와 함께 할 일을 떠올려 주고, 달력 단추로 '쉬는 날 할 일' 메모를 만들어요.",
    changes: ["쉬는 날 열 때 알림", "달력 '쉬는 날' 단추"], cover: ["#81C784", "#E8F5E9", "🌿"],
    rules: [{ name: "쉬는 날 알림", on: "open", when: "쉬는날", do: "notify", arg: "쉬는 날이에요. 이번 주 {이번주근무시간}시간 일했어요. 푹 쉬어요" }],
    buttons: [B("쉬는 날", "🌿", "cal", { do: "newMemo", arg: "쉬는 날 할 일" }, "쉬는날")],
  }),
  rule({
    title: "오늘 체크 다시", summary: "그날 체크 목록을 한 번에 비우는 단추(되돌리기 가능)",
    wow: "매일 같은 체크 목록을 쓰는 분을 위해 그날 메모의 체크를 한 번에 비우는 단추를 달았어요. 잘못 눌렀으면 되돌리기로 돌아와요.",
    changes: ["날 화면 '체크 다시' 단추"], cover: ["#43A047", "#C8E6C9", "🔁"],
    buttons: [B("체크 다시", "🔁", "day", { do: "checkReset" }, "체크율 > 0")],
  }),
  rule({
    title: "가게 오픈 마감", styles: ["BUSINESS"], summary: "아침 8시 오픈 알림, 저녁 8시 반 마감 알림, 마감 메모 단추",
    wow: "공휴일이 아닌 날 아침 8시에 오픈 준비를, 저녁 8시 반에 마감을 알려 줘요. '마감' 단추를 누르면 그날 마감 메모가 생겨요.",
    changes: ["08:00 오픈 알림", "20:30 마감 알림", "날 화면 '마감' 단추"], who: "혼자 가게를 여닫는 사장님", cover: ["#2E7D32", "#C8E6C9", "🔑"],
    rules: [R.shopOpen, R.closeCheck], buttons: [B("마감", "🔒", "day", { do: "newMemo", arg: "마감" })], showcase: true,
  }),
  rule({
    title: "매출 한 줄 장부", styles: ["BUSINESS"], summary: "매출을 적으면 그날 메모에 오늘·이달 매출 한 줄, 부가세 달 알림",
    wow: "가계부에 매출(수입)을 적으면 그날 메모에 '오늘 매출 · 이달 매출'이 한 줄로 쌓이고, 1월·7월 15일에는 부가세 신고 준비를 알려요.",
    changes: ["매출 기록 때 한 줄", "부가세 신고 달 알림"], cover: ["#00796B", "#B2DFDB", "🧾"], rules: [R.salesDay, R.vatMonth],
  }),
  rule({
    title: "현장 우천 기록", styles: ["FIELD"], summary: "#우천 메모를 쓰면 그날 '우천으로 쉼' 한 줄, 단추로 바로",
    wow: "#우천 꼬리표를 단 메모를 쓰면 그날 메모에 '우천으로 쉼 · 이달 일한 시간'이 한 줄로 쌓여요. 날 화면 '우천' 단추로 한 번에 남겨요.",
    changes: ["#우천 메모 때 한 줄", "날 화면 '우천' 단추"], who: "날씨에 따라 쉬는 현장 근무자", cover: ["#546E7A", "#B0BEC5", "🌧️"],
    rules: [R.rainMemo], buttons: [B("우천", "🌧️", "day", { do: "memoLine", arg: "우천으로 쉼 #우천" })],
  }),
  rule({
    title: "일당 바로 넣기", styles: ["FIELD"], summary: "일한 날 단추 하나로 일당을 가계부 수입에",
    wow: "근무가 있는 날 날 화면에 '일당' 단추가 생겨 누르면 넣을 때 정한 일당이 그날 수입으로 들어가요. 근무 끝에는 일한 시간이 한 줄로 남아요.",
    changes: ["근무 날 '일당' 단추", "근무 끝 한 줄 기록"], who: "일당을 받는 현장 근무자", cover: ["#FF6F00", "#FFE0B2", "💵"],
    inputs: [{ id: "i1", label: "일당", kind: "money", default: 180000, min: 0, hint: "하루 일당으로 바꿔요" }],
    rules: [R.dayLog], buttons: [B("일당", "💵", "day", { do: "addIncome", arg: "일당", amount: { input: "i1" } }, "근무시간 > 0")],
  }),
  rule({
    title: "매매 일지 지킴이", styles: ["INVEST"], summary: "#매매 날 저녁에 일지를 안 썼으면 알림, 단추로 일지",
    wow: "#매매 꼬리표를 단 날 저녁 8시 반까지 #일지가 없으면 산 이유와 마음을 남기라고 알려 줘요. '일지' 단추로 그날 매매 일지를 만들어요.",
    changes: ["매매 날 일지 알림", "날 화면 '일지' 단추"], who: "원칙대로 매매하고 싶은 투자자", cover: ["#263238", "#80CBC4", "📓"],
    rules: [R.tradeJournal], buttons: [B("일지", "📓", "day", { do: "newMemo", arg: "매매 일지" })], showcase: true,
  }),
  rule({
    title: "분기 리밸런싱", styles: ["INVEST"], summary: "3·6·9·12월 25일 '리밸런싱 점검' 메모, 매달 적립일 알림",
    wow: "분기 끝 달 25일에 '리밸런싱 점검' 메모가 생기고, 매달 적립일에 원칙대로 넣으라고 알려요.",
    changes: ["분기 끝 리밸런싱 메모", "매달 적립일 알림"], note: "적립일은 넣을 때 정해요. " + NOTE,
    cover: ["#37474F", "#CFD8DC", "⚖️"], rules: [R.quarter, R.saveDay],
  }),
  rule({
    title: "마감 날 아침 알림", summary: "#마감 꼬리표 날 아침 8시 반에 알림, 단추로 진행 한 줄",
    wow: "#마감 꼬리표를 붙인 날 아침 8시 반에 '오늘 마감이에요'를 알려 주고, 날 화면 '진행' 단추로 지금 상태를 한 줄씩 남겨요.",
    changes: ["마감 날 08:30 알림", "날 화면 '진행' 단추"], cover: ["#1E88E5", "#E3F2FD", "⏳"],
    rules: [{ name: "마감 날 알림", on: "daily", at: "08:30", when: '꼬리표.포함("마감")', do: "notify", arg: "오늘 마감이에요. 남은 체크 {남은체크}개" }],
    buttons: [B("진행", "📝", "day", { do: "memoLine", arg: "{지금시각} 진행: " }, '꼬리표.포함("마감")')],
  }),
];
