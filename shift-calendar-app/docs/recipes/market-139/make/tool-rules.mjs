// 1.39 [MKW-04] 도구 › 자동 규칙 20 (1.38까지 있던 장치만, 무료). 근무가 시작·끝날 때, 매일 정한 시각, 앱을 열 때, 메모·돈을 적을 때
// 저절로 알림·메모 한 줄·틀을 여는 규칙이에요. 1.38 규칙 20과 겹치지 않게 유형마다 '그 사람이 매일 손으로 하던 일'을 골랐어요.
// 1.39 장치(값 칸·값이 바뀌면·몇 분마다·동작 줄)를 쓰는 프로 규칙 10은 FX 갈래가 합쳐진 뒤 fx-rules.mjs로 만들어요.
import { logicWork as rule } from "../../market-138-studio/make/tabs-build.mjs";

const SHIFT = ["SHIFT"], DAY = ["DAY"], HOURLY = ["HOURLY"], ROSTER = ["SHIFT", "DAY", "HOURLY"], WORK = ["SHIFT", "DAY", "HOURLY", "FIELD"];
const SHOP = ["BUSINESS"], FIELD = ["FIELD"];

export default [
  // ---- 교대 ----
  rule({ top: true, title: "야간 전 낮잠 알림", styles: SHIFT, cover: ["#161D38", "#9FB4FF", "😴"], summary: "야간 다섯 시간 전, 지금 낮잠 자 두라는 알림",
    wow: "야간 근무 다섯 시간 전에 '지금 한두 시간 자 두면 새벽이 덜 힘들어요' 알림이 와요. 이번 주 몇 번째 야간인지도 함께 알려 줘요.", changes: ["야간 전 낮잠 알림"],
    who: "야간 전에 낮잠을 자고 싶은데 자꾸 놓치는 분",
    rules: [{ name: "야간 전 낮잠", on: "start", role: "night", min: -300, do: "notify", arg: "야간 다섯 시간 전이에요. 지금 한두 시간 자 두면 좋아요 · 이번 주 야간 {이번주야간}번째" }] }),
  rule({ title: "야간 끝 빛 피하기", styles: SHIFT, cover: ["#0B1A15", "#35F08F", "🕶️"], summary: "야간 끝나면 선글라스·암막 알림",
    wow: "야간이 끝나는 시각에 '퇴근길 선글라스, 집에 가면 암막 커튼' 알림이 와요. 아침 햇빛을 피해야 낮잠이 깊어요.", changes: ["야간 끝 빛 피하기 알림"],
    rules: [{ name: "야간 끝 빛", on: "end", role: "night", min: 0, do: "notify", arg: "야간 끝! 퇴근길엔 선글라스, 집에 가면 암막 커튼부터요" }] }),
  rule({ title: "인수인계 틀 자동", styles: SHIFT, cover: ["#E0F2F1", "#1D7A7A", "🩺"], summary: "근무 끝나기 30분 전 인수인계 틀이 열려요",
    wow: "근무가 끝나기 30분 전에 그날 인수인계 틀(상황·배경·평가·요청)이 열려요. 다음 근무자에게 넘길 것을 잊지 않아요.", changes: ["끝나기 전 인수인계 틀"],
    rules: [{ name: "인수인계 틀", on: "end", min: -30, do: "memoTpl", arg: "인수인계" }] }),
  rule({ title: "야간 셋째 날 회복 알림", styles: SHIFT, cover: ["#FFF3E0", "#EF6C00", "🔋"], summary: "이번 주 야간 세 번째가 끝나면 회복 알림",
    wow: "이번 주 세 번째 야간이 끝나면 '오늘은 약속을 줄이고 푹 자요' 알림이 와요. 몸이 가장 지치는 때를 먼저 챙겨요.", changes: ["야간 셋째 회복 알림"],
    rules: [{ name: "야간 셋째 회복", on: "end", role: "night", min: 10, when: "이번주야간 >= 3", do: "notify", arg: "이번 주 야간 {이번주야간}번째가 끝났어요. 오늘은 약속을 줄이고 푹 자요" }] }),

  // ---- 일근 ----
  rule({ top: true, title: "금요일 한 주 돌아보기", styles: DAY, cover: ["#FFF8E1", "#F9A825", "📒"], summary: "금요일 퇴근할 때 한 주 돌아보기 한 줄",
    wow: "금요일 퇴근 시각에 '이번 주 일한 시간 · 이달 쓴 돈' 한 줄이 메모에 남고, 주말 계획 틀이 열려요.", changes: ["금요일 돌아보기 한 줄", "주말 계획 틀"],
    rules: [{ name: "한 주 돌아보기", on: "end", when: '요일 = "금"', do: "memoLine", arg: "이번 주 {이번주근무시간}시간 일했어요 · 이달 쓴 돈 {원(이달지출)}" },
      { name: "주말 계획", on: "end", min: 5, when: '요일 = "금"', do: "memoTpl", arg: "주말 계획" }] }),
  rule({ title: "점심 걸음 알림", styles: DAY, cover: ["#E8F5E9", "#43A047", "🚶"], summary: "출근 네 시간째, 점심 뒤 10분 걷기",
    wow: "출근하고 네 시간째에 '점심 먹고 10분만 걸어요' 알림이 와요.", changes: ["점심 걷기 알림"],
    rules: [{ name: "점심 걷기", on: "start", role: "day", min: 240, do: "notify", arg: "점심 먹고 10분만 걸어요. 오후가 가벼워져요" }] }),
  rule({ title: "52시간 넘기 전 경고", styles: ["DAY", "SHIFT"], cover: ["#FFEBEE", "#C62828", "🚨"], summary: "이번 주 48시간을 넘기면 미리 알려요",
    wow: "근무가 끝날 때 이번 주 일한 시간이 48시간을 넘었으면 '이번 주 52시간까지 몇 시간 남았어요' 알림이 와요.", changes: ["주 48시간 경고"],
    rules: [{ name: "52시간 앞", on: "end", when: "이번주근무시간 >= 48", do: "notify", arg: "이번 주 {이번주근무시간}시간 일했어요. 52시간까지 {52 - 이번주근무시간}시간 남았어요" }] }),
  rule({ title: "월요일 아침 이번 주", styles: DAY, cover: ["#E3F2FD", "#1E88E5", "🗓️"], summary: "월요일 아침 주간 계획 틀",
    wow: "월요일 아침 일곱 시 반에 주간 계획 틀이 열려요. 요일마다 할 일을 한 줄씩 적어요.", changes: ["월요일 주간 계획 틀"],
    rules: [{ name: "주간 계획", on: "daily", at: "07:30", when: '요일 = "월"', do: "memoTpl", arg: "주간 계획" }] }),

  // ---- 알바 ----
  rule({ top: true, title: "주휴 15시간 채움 알림", styles: HOURLY, cover: ["#FFF8E1", "#FFC107", "🥇"], summary: "이번 주 15시간을 채우면 주휴수당 알림",
    wow: "근무가 끝날 때 이번 주 일한 시간이 15시간을 처음 넘으면 '이번 주 주휴수당 조건을 채웠어요' 알림이 와요.", changes: ["주휴 조건 알림"],
    who: "주 15시간을 넘겼는지 늘 헷갈리는 알바생",
    rules: [{ name: "주휴 채움", on: "end", when: "이번주근무시간 >= 15 그리고 이번주근무시간 - 근무시간 < 15", do: "notify", arg: "이번 주 {이번주근무시간}시간! 주휴수당 조건(15시간)을 채웠어요" }] }),
  rule({ title: "알바 끝 번 돈 한 줄", styles: HOURLY, cover: ["#E8F5E9", "#0F9D58", "🧾"], summary: "근무 끝나면 오늘 번 돈과 이번 주 시간을 메모로",
    wow: "근무가 끝나면 '오늘 몇 시간 · 번 돈 · 이번 주 몇 시간' 한 줄이 그날 메모에 쌓여요. 사장님 정산과 맞춰 보기 쉬워요.", changes: ["근무 끝 한 줄 기록"],
    rules: [{ name: "번 돈 한 줄", on: "end", min: 5, do: "memoLine", arg: "{근무시작}~{근무끝} {근무시간}시간 · {원(그날급여)} · 이번 주 {이번주근무시간}시간" }] }),
  rule({ title: "야간 알바 가산 확인", styles: HOURLY, cover: ["#1B1E3C", "#FFD54F", "🌙"], summary: "밤 10시 넘게 일한 날 가산 확인 한 줄",
    wow: "야간 근무가 끝나면 '밤 10시~아침 6시 가산(5인 이상 가게)' 확인 한 줄이 메모에 남아요.", changes: ["야간 가산 확인 한 줄"],
    rules: [{ name: "야간 가산", on: "end", role: "night", do: "memoLine", arg: "야간 {근무시간}시간 · 5인 이상 가게면 밤 10시~6시 가산 확인" }] }),
  rule({ title: "출근 전 준비물 알림", styles: ROSTER, cover: ["#FFF3E0", "#FB8C00", "🎒"], summary: "근무 한 시간 반 전 준비물 체크 틀",
    wow: "근무 한 시간 반 전에 준비물 체크 틀(사원증·충전기·물·약)이 열려요.", changes: ["출근 전 준비물 틀"],
    rules: [{ name: "준비물", on: "start", min: -90, do: "memoTpl", arg: "근무 준비물" }] }),

  // ---- 현장 ----
  rule({ top: true, title: "작업 전 안전 점검", styles: FIELD, cover: ["#FFF8E1", "#FFD100", "⛑️"], summary: "근무 시작 10분 전 안전 점검 틀",
    wow: "현장 근무 시작 10분 전에 작업 전 안전 점검 틀(안전모·추락 방지·전기)이 열려요.", changes: ["작업 전 안전 점검 틀"],
    rules: [{ name: "안전 점검", on: "start", min: -10, do: "memoTpl", arg: "작업 전 안전 점검" }] }),
  rule({ title: "폭염 물 쉬기 알림", styles: ["FIELD", "HOURLY"], cover: ["#FFEBEE", "#E53935", "🥵"], summary: "여름 낮 근무 중 한 시간마다 물·그늘 알림",
    wow: "6~8월 근무 중 한 시간, 두 시간, 세 시간째에 '물 한 컵, 그늘에서 10분' 알림이 와요.", changes: ["여름 근무 물·그늘 알림"],
    rules: [{ name: "폭염 1시간", on: "start", min: 60, when: "월 >= 6 그리고 월 <= 8", do: "notify", arg: "한 시간째예요. 물 한 컵, 그늘에서 10분" },
      { name: "폭염 2시간", on: "start", min: 120, when: "월 >= 6 그리고 월 <= 8", do: "notify", arg: "두 시간째예요. 물 한 컵, 그늘에서 10분" },
      { name: "폭염 3시간", on: "start", min: 180, when: "월 >= 6 그리고 월 <= 8", do: "notify", arg: "세 시간째예요. 물 한 컵, 그늘에서 10분" }] }),
  rule({ title: "현장 일지 자동", styles: FIELD, cover: ["#2F4A3A", "#F5E27A", "📒"], summary: "근무 끝나면 현장 일지 틀이 열려요",
    wow: "근무가 끝나면 그날 현장 일지 틀(현장·공정·한 일·내일 할 일)이 열려요.", changes: ["근무 끝 현장 일지"],
    rules: [{ name: "현장 일지", on: "end", min: 5, do: "memoTpl", arg: "현장 일지" }] }),

  // ---- 자영업 ----
  rule({ top: true, title: "마감 정산 알림", styles: SHOP, cover: ["#FFF3E0", "#E8483B", "🔒"], summary: "밤 열 시 오늘 매출 한 줄과 마감 틀",
    wow: "밤 열 시에 '오늘 매출 · 이달 매출' 알림이 오고 마감 체크 틀(시재·청소·문단속)이 열려요.", changes: ["마감 매출 알림", "마감 틀"],
    rules: [{ name: "마감 매출", on: "daily", at: "22:00", do: "notify", arg: "오늘 매출 {원(수입)} · 이달 {원(이달수입)}" },
      { name: "마감 틀", on: "daily", at: "22:01", do: "memoTpl", arg: "마감" }] }),
  rule({ title: "부가세 달 알림", styles: SHOP, cover: ["#E3F2FD", "#1565C0", "🧾"], summary: "1월·7월 첫날 부가세 신고 달 알림",
    wow: "1월과 7월 1일 아침에 '이번 달은 부가세 신고 달이에요' 알림과 준비 메모가 생겨요.", changes: ["부가세 달 알림"],
    rules: [{ name: "부가세 달", on: "daily", at: "09:00", when: "(월 = 1 또는 월 = 7) 그리고 일 = 1", do: "notify", arg: "이번 달은 부가세 신고 달이에요. 매입 영수증을 모아 둬요" },
      { name: "부가세 메모", on: "daily", at: "09:01", when: "(월 = 1 또는 월 = 7) 그리고 일 = 1", do: "newMemo", arg: "부가세 신고 준비" }] }),
  rule({ title: "재고 점검 월요일", styles: SHOP, cover: ["#F1F8E9", "#558B2F", "📦"], summary: "월요일 아침 재고 점검 틀",
    wow: "월요일 아침 아홉 시에 재고 점검 틀(주재료·부재료·포장재·소모품)이 열려요.", changes: ["월요일 재고 틀"],
    rules: [{ name: "재고 점검", on: "daily", at: "09:00", when: '요일 = "월"', do: "memoTpl", arg: "재고 점검" }] }),

  // ---- 모든 유형 ----
  rule({ title: "큰 지출 한 번 더 생각", cover: ["#FCE4EC", "#D81B60", "🛍️"], summary: "하루 지출이 커지면 잘 쓴 건지 한 줄 묻기",
    wow: "그날 적은 지출이 많아지면 '오늘 쓴 돈, 잘 쓴 걸까요?' 알림이 오고 그날 메모에 한 줄 남길 자리가 생겨요. 후회와 만족을 적어 두면 다음 소비가 달라져요.",
    changes: ["큰 지출 날 알림", "돌아보기 한 줄"],
    rules: [{ name: "큰 지출 생각", on: "money", when: "지출 >= 100000", do: "notify", arg: "오늘 {원(지출)} 썼어요. 잘 쓴 걸까요? 한 줄 남겨 봐요" },
      { name: "큰 지출 한 줄", on: "money", when: "지출 >= 100000", do: "memoLine", arg: "오늘 {원(지출)} · 잘 쓴 것 / 아쉬운 것: " }] }),
  rule({ title: "잠들기 전 오늘 정리", cover: ["#EDE7F6", "#5E35B1", "🌛"], summary: "밤 열 시 반, 오늘 남은 체크와 쓴 돈 한 줄",
    wow: "밤 열 시 반에 '오늘 남은 체크 몇 개 · 오늘 쓴 돈'을 알려 줘요. 하루를 닫고 마음 편히 자요.", changes: ["밤 오늘 정리 알림"],
    rules: [{ name: "오늘 정리", on: "daily", at: "22:30", do: "notify", arg: "오늘 남은 체크 {남은체크}개 · 쓴 돈 {원(지출)} · 잘 자요" }] }),
];
