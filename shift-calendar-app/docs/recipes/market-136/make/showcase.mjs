// 보여주기 30 (유형마다 5, 기획서 2장 '보여주기(맨 위)'). 사용자 결정(메인 스레드 2026-11-03): 1.35 보여주기 30 가운데 좋은 것을 남겨
// 새 형식으로 고치고, 새 작품과 섞어 보여 줘요.
// - 고친 1.35 작품 12개(유형마다 2): docs/recipes/showcase-recipes.json의 그 작품에 1.36 조각(달력 칸 수식 줄, 규칙·단추, 꾸밈)을 더하고
//   제목 끝에 '플러스'를 붙였어요. 같은 제목으로 올리면 장터의 1.35 작품이 새 판으로 바뀌어(replace) 1.35 앱에서 사라지니, 새 제목으로 따로 올려요.
//   1.35 보여주기 원본은 장터에 그대로 두고, 운영자가 보여주기에서만 내려요(release 문서).
// - 새 작품 18개(유형마다 3): SHOWCASE 목록의 제목. index.mjs가 이 목록만 showcase로 표시하고 나머지는 지워요.
import { readFileSync } from "node:fs";
import { c, parts, logic, decor, cellSticker } from "./lib.mjs";
import { C, R, B } from "./kit.mjs";

const OLD = JSON.parse(readFileSync(new URL("../../showcase-recipes.json", import.meta.url), "utf8"));

/** 1.35 작품 [title]에 [add]를 더한 새 판. [lines]는 '1.36에서 더한 것' 줄, [fix]는 설명 바꿀 말. */
function plus(title, { wow, lines, cells, rules, buttons, decorItems, fix = [] }) {
  const w = OLD.find((x) => x.recipe.title === title);
  if (!w) throw new Error(`1.35 보여주기에 없는 작품: ${title}`);
  const r = structuredClone(w.recipe);
  r.title = `${title} 플러스`;
  let desc = r.desc;
  for (const [a, b] of fix) desc = desc.replace(a, b);
  const at = desc.indexOf("\n주의할 점:");
  const add = lines.map((x) => `· 1.36: ${x}`).join("\n");
  desc = at < 0 ? `${desc}\n${add}` : `${desc.slice(0, at)}\n${add}${desc.slice(at)}`;
  r.desc = `${wow}\n${desc}`;
  if (cells?.length) r.pieces.push(parts(cells));
  if (rules?.length || buttons?.length) r.pieces.push(logic({ rules, buttons }));
  if (decorItems?.length) r.pieces.push(decor(decorItems));
  return { showcase: true, recipe: r };
}

export const PLUS = [
  plus("3교대 간호 풀세트", {
    wow: "1.35 간호 풀세트에 1.36의 '데이터로 움직이는 칸'과 규칙을 더했어요. 이어진 나이트는 칸이 짙어지고, 근무 30분 전 인수인계 메모가 저절로 생겨요.",
    lines: ["이어진 나이트 칸 짙게, #인수인계 날 파란 테두리, 체크 다 하면 초록 고리", "근무 30분 전 인수인계 메모, 남은 체크 알림", "나이트 끝 잠자리 알림, 나이트 칸 🌙"],
    cells: [...C.nightDeep("lg.180.FFE8EAF6,FFFFFFFF", "lg.180.FF9FA8DA,FFE8EAF6"), C.tagLine("인수인계", "#1E88E5"), C.checkRing()],
    rules: [R.handoverNew, R.handoverLeft, R.nightSleep], decorItems: [cellSticker("🌙", "role.night")],
  }),
  plus("야간 생존 키트", {
    wow: "1.35 야간 생존 키트가 피로를 눈으로 보여 줘요. 야간이 쌓이면 칸이 짙어지고, 회복할 쉬는 날은 초록으로 바뀌어요.",
    lines: ["이어진 야간 칸 짙게, 야간 많은 달 별빛, 회복 날 초록", "야간 끝 잠자리·세 번째 야간 회복 알림, 연속근무 경고"],
    cells: [...C.nightDeep("lg.180.FF283593,FF1A237E", "lg.180.FF4A148C,FF283593"), C.nightMonth(), C.recovery("#2E7D32")],
    rules: [R.nightSleep, R.nightRecover, R.streak],
  }),
  plus("칼퇴 직장인 대시보드", {
    wow: "1.35 칼퇴 대시보드에 주 52시간 감시와 금요일 회고가 붙었어요. 52시간을 넘는 주는 날짜에 빨간 밑줄이 그어져요.",
    lines: ["주 52시간 넘은 주 빨간 날짜, 공휴일 근무 금색 테두리", "금요일 17:30 주간 회고 메모, 46시간 넘으면 남은 시간 알림"],
    cells: [C.week52(), C.holidayWork()], rules: [R.friRetro, R.week52],
  }),
  plus("재택·출근 하이브리드", {
    wow: "1.35 하이브리드 판에서 재택 날이 달력에 🏠로 보이고, 근무 중 물·스트레칭 알림이 더해졌어요.",
    lines: ["재택 날 칸 초록빛과 🏠 스티커, 체크 다 한 날 초록 고리", "근무 2시간째 물, 4시간째 스트레칭 알림"],
    cells: [{ id: "cal.cell", on: "role.other", fill: c("#C8E6C9"), fillA: 45 }, C.checkRing()], rules: [R.waterBreak, R.stretch],
    decorItems: [cellSticker("🏠", "role.other")],
  }),
  plus("편의점 야간 알바 완전판", {
    wow: "1.35 편의점 야간 완전판이 근무 끝마다 출퇴근을 한 줄로 남기고, 주 15시간을 넘긴 주는 날짜가 금빛이에요.",
    lines: ["주 15시간 넘긴 주 금빛 날짜, 일한 시간만큼 진한 칸", "근무 끝 출퇴근 한 줄, 15시간 넘김 알림"],
    cells: [C.week15(), C.hoursHeat("#1565C0")], rules: [R.dayLog, R.weekHours15],
  }),
  plus("주휴수당 지킴이", {
    wow: "1.35 주휴수당 지킴이가 이제 달력에서 바로 보여요. 15시간을 넘긴 주는 근무 날짜가 금빛, 넘긴 순간 알림이 와요.",
    lines: ["주 15시간 넘긴 주 금빛 날짜, 일한 시간 농도", "15시간 넘김 알림, 근무 끝 한 줄 기록"],
    cells: [C.week15(), C.hoursHeat("#F9A825")], rules: [R.weekHours15, R.dayLog],
  }),
  plus("인테리어 반장 풀세트", {
    wow: "1.35 반장 풀세트에 '일당' 단추와 우천 기록이 붙었어요. 10시간 넘게 일한 날은 굵은 테두리, 돈 들어온 날은 금빛이에요.",
    lines: ["10시간 넘은 날 굵은 테두리, 돈 들어온 날 금빛, #우천 날 회색 점선", "근무 날 '일당' 단추로 수입 넣기, #우천 메모 한 줄"],
    cells: [C.longDay(), C.incomeDay(), C.tagLine("우천", "#78909C", "dash")], rules: [R.rainMemo],
    buttons: [B("일당", "💵", "day", { do: "addIncome", arg: "일당", amount: { input: "i1" } }, "근무시간 > 0")],
  }),
  plus("배달 라이더 대시보드", {
    wow: "1.35 라이더 대시보드에 달린 시간과 번 돈이 칸 색으로 들어왔어요. 오래 달린 날은 진하게, 돈 들어온 날은 금빛이에요.",
    lines: ["달린 시간 농도, 돈 들어온 날 금빛, 많이 쓴 날 빨간 금액", "밤 9시 가계부 알림, 매달 1일 고정비 점검 메모"],
    cells: [C.hoursHeat("#00897B"), C.incomeDay(), C.spendLine()], rules: [R.dailyMoney, R.fixedCosts],
  }),
  plus("동네 카페 운영 풀세트", {
    wow: "1.35 카페 풀세트가 문 열고 닫을 때를 알려 줘요. 매출을 적으면 오늘·이달 매출이 그날 메모에 한 줄로 쌓여요.",
    lines: ["매출 있는 날 금빛 칸, #단체 주문 날 갈색 칸", "08:00 오픈·20:30 마감 알림, 매출 한 줄 장부"],
    cells: [C.incomeDay(), C.tagFill("단체", "#8D6E63")], rules: [R.shopOpen, R.closeCheck, R.salesDay],
  }),
  plus("1인 네일·미용숍", {
    wow: "1.35 네일·미용숍 판에서 #예약 날이 분홍 테두리로 보이고, 매출 한 줄 장부와 부가세 달 알림이 더해졌어요.",
    lines: ["#예약 날 분홍 테두리, 매출 있는 날 금빛", "매출 한 줄 장부, 1·7월 부가세 신고 알림"],
    cells: [C.tagLine("예약", "#EC407A"), C.incomeDay("#F8BBD0")], rules: [R.salesDay, R.vatMonth],
  }),
  plus("가치투자 일지 판", {
    wow: "1.35 가치투자 판이 매매한 날 저녁에 일지를 권해요. #매매 날은 테두리, 일지를 쓴 날은 초록 칸이에요.",
    lines: ["#매매 날 테두리, #일지 쓴 날 초록 칸", "매매 날 일지 알림, 분기 끝 리밸런싱 메모, 날 화면 '일지' 단추"],
    cells: [C.tagLine("매매", "#455A64"), C.tagFill("일지", "#43A047", 30)], rules: [R.tradeJournal, R.quarter],
    buttons: [B("일지", "📓", "day", { do: "newMemo", arg: "매매 일지" })],
  }),
  plus("적립식 루틴 판", {
    wow: "1.35 적립식 루틴에 적립일 알림과 분기 점검이 붙었어요. #적립 날은 새싹색 칸으로 쌓여요.",
    lines: ["#적립 날 새싹색 칸", "매달 적립일 알림, 분기 끝 리밸런싱 메모"],
    cells: [C.tagFill("적립", "#AED581", 45)], rules: [R.saveDay, R.quarter],
    fix: [["내 월급날에 맞춰", "돈 들어오는 날에 맞춰"]],
  }),
];

/** 새 작품 가운데 보여주기 (유형마다 3). */
export const SHOWCASE = [
  // 교대
  "오로라 나이트", "3교대 두 번 알람", "야간 회복 지킴이",
  // 일근
  "모닝 미니멀", "일근 출근 알람", "데드라인 모드",
  // 알바
  "시급 스코어보드", "알바 출근 30분 전", "알바 출퇴근 기록",
  // 현장
  "현장 블루프린트", "공수 계산판", "일당 바로 넣기",
  // 자영업
  "가게 대시보드", "가게 오픈 마감", "작은 온라인 가게",
  // 투자
  "원칙 저널", "매매 일지 지킴이", "적립 달력",
];
