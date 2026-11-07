// 1.39 [MKW-04] 도구 › 단추 18 (1.38까지 있던 장치만, 무료). 달력 날짜 아래·메모장·가계부에 한 번에 누르는 단추를 놓아요.
// 1.38 단추와 겹치지 않게 유형마다 하루에 여러 번 손으로 적던 것을 골랐어요. 금액은 넣을 때 내 값으로 정해요.
// 1.39 장치(값 칸 세기 단추·단추 위젯·알림 단추·바로가기)를 쓰는 프로 단추 12는 FX 갈래가 합쳐진 뒤 fx-buttons.mjs로 만들어요.
import { B } from "../../market-136/make/kit.mjs";
import { logicWork as rule } from "../../market-138-studio/make/tabs-build.mjs";

const SHIFT = ["SHIFT"], DAY = ["DAY"], HOURLY = ["HOURLY"], ROSTER = ["SHIFT", "DAY", "HOURLY"], FIELD = ["FIELD"], SHOP = ["BUSINESS"], INVEST = ["INVEST"];
const money = (id, label, def) => ({ id, label, kind: "money", default: def, min: 0 });
const BTN_NOTE = "꾸미기는 바뀌지 않아요. 단추는 스튜디오 › 규칙·단추에서 지우거나 이름·금액을 바꿀 수 있고, 단추가 적은 것은 되돌릴 수 있어요";
const btn = (s) => rule({ note: BTN_NOTE, ...s });

export default [
  // ---- 교대 ----
  btn({ top: true, title: "야식 한 번에 적기", styles: SHIFT, cover: ["#161D38", "#FFB74D", "🍜"], summary: "야간 날 달력에 야식 단추, 누르면 식비 한 줄",
    wow: "야간 근무인 날에만 달력 날짜 아래 '야식' 단추가 보여요. 누르면 야식 지출이 바로 들어가요.", changes: ["야간 날 야식 단추"],
    buttons: [B("야식", "🍜", "day", { do: "addMoney", arg: "야식", amount: { input: "i1" } }, '근무종류 = "야간"')], inputs: [money("i1", "야식 값", 8000)] }),
  btn({ title: "퇴근 택시 단추", styles: SHIFT, cover: ["#FFF8E1", "#FBC02D", "🚕"], summary: "저녁·야간 날 택시비를 한 번에",
    wow: "저녁·야간 근무 날 달력에 '택시' 단추가 생겨 누르면 교통비가 한 줄 들어가요.", changes: ["저녁·야간 택시 단추"],
    buttons: [B("택시", "🚕", "day", { do: "addMoney", arg: "택시", amount: { input: "i1" } }, '근무종류 = "야간" 또는 근무종류 = "저녁"')], inputs: [money("i1", "택시비", 15000)] }),
  btn({ title: "근무 바꾸기 메모 단추", styles: SHIFT, cover: ["#E3F2FD", "#1E88E5", "🔁"], summary: "날짜 아래 단추로 근무 바꾸기 틀",
    wow: "달력 날짜 아래 '바꾸기' 단추를 누르면 근무 바꾸기 틀(바꿀 날·사람·돌려받을 날)이 그날 메모로 열려요.", changes: ["근무 바꾸기 단추"],
    buttons: [B("바꾸기", "🔁", "day", { do: "memoTpl", arg: "근무 바꾸기" })] }),

  // ---- 일근 ----
  btn({ top: true, title: "커피·점심 두 단추", styles: DAY, cover: ["#EFEBE9", "#6D4C41", "☕"], summary: "출근 날 달력에 커피·점심 단추",
    wow: "출근하는 날 달력 날짜 아래 커피와 점심 단추가 생겨요. 누르면 바로 한 줄씩 들어가요.", changes: ["출근 날 커피 단추", "출근 날 점심 단추"],
    buttons: [B("커피", "☕", "day", { do: "addMoney", arg: "카페", amount: { input: "i1" } }, "아님 쉬는날"),
      B("점심", "🍱", "day", { do: "addMoney", arg: "점심", amount: { input: "i2" } }, "아님 쉬는날")],
    inputs: [money("i1", "커피 값", 4500), money("i2", "점심 값", 10000)] }),
  btn({ title: "회의록 바로 열기", styles: DAY, cover: ["#E8EAF6", "#3949AB", "🗒️"], summary: "메모장 위 회의록 단추",
    wow: "메모장 위에 '회의록' 단추가 생겨 누르면 오늘 날짜가 들어간 회의록 틀이 열려요.", changes: ["회의록 단추"],
    buttons: [B("회의록", "🗒️", "memo", { do: "memoTpl", arg: "회의록" })] }),
  btn({ title: "야근 식대 단추", styles: DAY, cover: ["#FFF3E0", "#EF6C00", "🌃"], summary: "10시간 넘는 날 저녁 식대 단추",
    wow: "그날 근무가 10시간을 넘는 날에만 '야근 식대' 단추가 보여요.", changes: ["긴 날 식대 단추"],
    buttons: [B("야근 식대", "🌃", "day", { do: "addMoney", arg: "식비", amount: { input: "i1" } }, "근무시간 >= 10")], inputs: [money("i1", "저녁 값", 12000)] }),

  // ---- 알바 ----
  btn({ top: true, title: "알바 끝 한 줄 단추", styles: HOURLY, cover: ["#E8F5E9", "#0F9D58", "⏱️"], summary: "날짜 아래 단추로 오늘 근무 한 줄",
    wow: "달력 날짜 아래 '기록' 단추를 누르면 '몇 시~몇 시 · 몇 시간 · 번 돈' 한 줄이 그날 메모에 남아요.", changes: ["근무 한 줄 단추"],
    buttons: [B("기록", "⏱️", "day", { do: "memoLine", arg: "{근무시작}~{근무끝} {근무시간}시간 · {원(그날급여)}" }, "아님 쉬는날")] }),
  btn({ title: "식대·교통 알바 단추", styles: HOURLY, cover: ["#FFF8E1", "#F9A825", "🚌"], summary: "가계부에 식대·교통 단추",
    wow: "가계부 위에 식대와 교통 단추가 생겨 근무 날 쓰는 돈을 한 번에 적어요.", changes: ["식대 단추", "교통 단추"],
    buttons: [B("식대", "🍙", "money", { do: "addMoney", arg: "식비", amount: { input: "i1" } }), B("교통", "🚌", "money", { do: "addMoney", arg: "교통", amount: { input: "i2" } })],
    inputs: [money("i1", "식대", 7000), money("i2", "교통비", 2800)] }),
  btn({ title: "비상금 한 번에", styles: HOURLY, cover: ["#E3F2FD", "#1E88E5", "🛟"], summary: "가계부 단추로 비상금 봉투에",
    wow: "가계부 위 '비상금' 단추를 누르면 정한 금액이 비상금 봉투에 들어가요.", changes: ["비상금 봉투 단추"],
    buttons: [B("비상금", "🛟", "money", { do: "envelope", arg: "비상금", amount: { input: "i1" } })], inputs: [money("i1", "한 번에 넣을 돈", 10000)] }),

  // ---- 현장 ----
  btn({ top: true, title: "일당 들어옴 단추", styles: FIELD, cover: ["#F3E2C3", "#8A5A2B", "💵"], summary: "날짜 아래 단추로 일당 수입 한 줄",
    wow: "근무한 날 달력 날짜 아래 '일당' 단추가 생겨 누르면 그날 일당이 수입으로 들어가요.", changes: ["일당 수입 단추"],
    buttons: [B("일당", "💵", "day", { do: "addIncome", arg: "일당", amount: { input: "i1" } }, "아님 쉬는날")], inputs: [money("i1", "일당", 180000)] }),
  btn({ title: "유류비·식대 현장 단추", styles: FIELD, cover: ["#FFF8E1", "#FFD100", "⛽"], summary: "가계부에 유류비·식대 단추",
    wow: "가계부 위에 유류비와 식대 단추가 생겨요. 현장 오가는 돈을 한 번에 적어요.", changes: ["유류비 단추", "식대 단추"],
    buttons: [B("유류비", "⛽", "money", { do: "addMoney", arg: "유류비", amount: { input: "i1" } }), B("식대", "🍚", "money", { do: "addMoney", arg: "식대", amount: { input: "i2" } })],
    inputs: [money("i1", "유류비", 30000), money("i2", "식대", 9000)] }),
  btn({ title: "현장 사진 메모 단추", styles: FIELD, cover: ["#2F4A3A", "#F5E27A", "📷"], summary: "메모장 위 현장 사진 틀 단추",
    wow: "메모장 위 '현장 사진' 단추를 누르면 작업 전·중·후 사진 칸이 있는 틀이 열려요.", changes: ["현장 사진 틀 단추"],
    buttons: [B("현장 사진", "📷", "memo", { do: "memoTpl", arg: "현장 사진 메모" })] }),

  // ---- 자영업 ----
  btn({ top: true, title: "매출 세 단추", styles: SHOP, cover: ["#FFEBEE", "#E8483B", "🧾"], summary: "가계부에 카드·현금·배달 매출 단추",
    wow: "가계부 위에 카드·현금·배달 매출 단추가 생겨요. 누르면 금액만 넣고 바로 수입 한 줄이 들어가요.", changes: ["카드 매출 단추", "현금 매출 단추", "배달 매출 단추"],
    buttons: [B("카드", "💳", "money", { do: "addIncome", arg: "카드 매출" }), B("현금", "💵", "money", { do: "addIncome", arg: "현금 매출" }),
      B("배달", "🛵", "money", { do: "addIncome", arg: "배달 매출" })] }),
  btn({ title: "재료 장보기 단추", styles: SHOP, cover: ["#F1F8E9", "#558B2F", "🥬"], summary: "가계부에 재료 지출 단추",
    wow: "가계부 위 '재료' 단추를 누르면 금액만 넣고 재료 지출이 들어가요.", changes: ["재료 지출 단추"],
    buttons: [B("재료", "🥬", "money", { do: "addMoney", arg: "재료" })] }),
  btn({ title: "부가세 봉투 단추", styles: SHOP, cover: ["#E3F2FD", "#1565C0", "📮"], summary: "단추 한 번에 부가세 봉투에 떼어 두기",
    wow: "가계부 위 '부가세' 단추를 누르면 정한 금액이 부가세 봉투에 들어가요. 신고 달에 한꺼번에 내지 않아도 돼요.", changes: ["부가세 봉투 단추"],
    buttons: [B("부가세", "📮", "money", { do: "envelope", arg: "부가세", amount: { input: "i1" } })], inputs: [money("i1", "한 번에 떼어 둘 돈", 50000)] }),

  // ---- 투자 ----
  btn({ title: "매매 일지 단추", styles: INVEST, cover: ["#0B0F14", "#22C55E", "📈"], summary: "날짜 아래 단추로 매매 일지 틀",
    wow: "달력 날짜 아래 '일지' 단추를 누르면 그날 매매 일지 틀(이유·원칙·마음)이 열려요.", changes: ["매매 일지 단추"],
    buttons: [B("일지", "📈", "day", { do: "memoTpl", arg: "매매 일지" })] }),

  // ---- 모든 유형 ----
  btn({ title: "기분 한 줄 단추", cover: ["#FFF3E0", "#FB8C00", "🙂"], summary: "날짜 아래 좋음·지침 두 단추",
    wow: "달력 날짜 아래 '좋음'·'지침' 단추가 생겨 누르면 그날 메모에 기분 한 줄이 남아요. 한 달 뒤 달력에서 기분 흐름이 보여요.", changes: ["좋음 단추", "지침 단추"],
    buttons: [B("좋음", "😊", "day", { do: "memoLine", arg: "기분 좋음 · {근무} #기분" }), B("지침", "😮‍💨", "day", { do: "memoLine", arg: "지침 · {근무} {근무시간}시간 #기분" })] }),
  btn({ title: "체크 다시 시작 단추", cover: ["#E8F5E9", "#43A047", "♻️"], summary: "메모장 위 단추로 매일 체크 다시",
    wow: "메모장 위 '다시' 단추를 누르면 반복해 쓰는 체크 목록이 모두 빈칸으로 돌아가요. 매일 같은 체크를 새로 시작해요.", changes: ["체크 다시 시작 단추"],
    buttons: [B("다시", "♻️", "memo", { do: "checkReset" })] }),
];
