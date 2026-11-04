// 부품 31 (기획서 4-8): 부품 단위 하나만 극한까지. 작품마다 '부품 꾸미기' 조각 하나에, 모든 줄이 한 단위(설계도 id 앞 두 마디) 안이에요 → 장터 '부품' 구간 part:<단위>.
// 판(달력 판·메모 보기·가계부 판) 셋은 tabView 조각 하나로 cal.board·memo.view·money.board 단위예요.
// 수식(when·{fx})은 날짜가 있는 부품(core Blueprint.DATED: 달력 칸·머리·판, 메모 카드, 가계부 줄, 급여)에만 써요 (FXR-03).
// 그 밖의 단위는 오늘·주말·근무 종류·들어온/나간 돈·고른 것 같은 상태 줄로 바뀌어요.
import { c, ALL, ROSTER, recipe, parts, tabView } from "./lib.mjs";
import { C, D } from "./kit.mjs";

const NOTE = "이 부품 하나만 바뀌어요. 다른 꾸미기와 기록은 그대로예요";

function part(s) {
  const piece = s.view ? tabView(s.view) : parts(s.lines);
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? NOTE, styles: s.styles ?? ALL, cover: s.cover,
      pieces: [piece] }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

export default [
  // ---- cal.cell: 달력 칸 (수식이 지금 돌아요) ----
  part({
    title: "오늘 칸 시계", summary: "오늘 칸이 지금 시각 따라 새벽·아침·낮·저녁 색, 천천히 숨 쉬어요",
    wow: "달력을 열 때 지금 시각을 읽어 오늘 칸 하나만 새벽 남색, 아침 금빛, 낮 하늘, 저녁 보라로 칠하고 숨 쉬듯 빛나요.",
    changes: ["오늘 칸 시각 색", "숨쉬는 빛", "오늘 날짜 굵게"], cover: ["#FFB74D", "#283593", "🕰️"],
    lines: [C.todayClock(), { id: "cal.cell", on: "today", motion: "glow", r: 12 }, { id: "cal.cell.date", on: "today", weight: 900, ink: c("#FFFFFF") }],
  }),
  part({
    title: "야간 짙어지는 칸", styles: ROSTER, summary: "야간이 이어질수록 칸이 짙어지고, 야간 많은 달엔 별빛 표시",
    wow: "야간 첫날은 옅은 남색, 세 번째 이어진 야간부터는 짙은 남색에 반짝임이 더해져요. 이달 야간이 여덟 번을 넘으면 야간 표시가 별빛으로 빛나요.",
    changes: ["야간 1·3번째 색", "반짝임", "야간 많은 달 별빛"], who: "야간이 많은 교대 근무자", cover: ["#1A237E", "#FFF59D", "🌙"],
    lines: [...C.nightDeep("lg.180.FFC5CAE9,FFE8EAF6", "lg.180.FF3949AB,FF5C6BC0"), C.nightMonth()], showcase: true,
  }),
  part({
    title: "꼬리표 점 칸", summary: "#인수인계 날은 파란 테두리, #중요 날은 빨간 칸",
    wow: "메모에 꼬리표를 붙이면 그 날 칸이 바로 바뀌어요. #인수인계는 파란 테두리, #중요는 연한 빨강, #약속은 초록 점선이에요.",
    changes: ["#인수인계 파란 테두리", "#중요 빨간 칸", "#약속 초록 점선"], cover: ["#1E88E5", "#E53935", "🏷️"],
    lines: [C.tagLine("인수인계", "#1E88E5"), C.tagFill("중요", "#E53935", 30), { id: "cal.cell.mark", when: '꼬리표.포함("약속")', ink: c("#43A047"), weight: 800 }],
  }),
  part({
    title: "지출 열지도 칸", summary: "쓴 돈만큼 칸이 노랑·주황·빨강, 무지출 날은 초록 날짜",
    wow: "그날 쓴 돈이 2만·5만·10만을 넘을 때마다 칸이 노랑, 주황, 빨강으로 물들어요. 지난날 중 한 푼도 안 쓴 날은 날짜가 초록이에요.",
    changes: ["지출 3단계 칸 색", "무지출 초록 날짜", "큰 지출 빨간 금액"], cover: ["#FFF59D", "#EF9A9A", "🔥"],
    lines: [C.spendHeat(), C.noSpend(), C.spendLine()],
  }),
  part({
    title: "근무 시간 농도 칸", styles: ROSTER, summary: "일한 시간만큼 칸이 진해지고, 52시간 넘은 주는 빨간 날짜",
    wow: "그날 일한 시간에 6을 곱한 만큼 칸 색이 진해져요(최대 80%). 그 주 근무가 52시간을 넘으면 그 주 날짜에 빨간 밑줄이 그어져요.",
    changes: ["근무 시간 농도", "52시간 넘은 주 빨간 날짜"], who: "근무 시간을 지켜야 하는 분", cover: ["#1E88E5", "#D32F2F", "⏱️"],
    lines: [C.hoursHeat("#1E88E5"), C.week52()],
  }),
  part({
    title: "체크 고리 칸", summary: "그날 체크를 다 하면 초록 고리, 하다 말면 노랑, 메모 많은 날은 숨쉬기",
    wow: "그날 메모의 체크 목록을 다 끝내면 칸 테두리가 초록, 남아 있으면 노랑이에요. 메모가 세 개 넘는 날은 칸이 숨 쉬어요.",
    changes: ["체크율 고리", "바쁜 날 숨쉬기"], cover: ["#43A047", "#FFB300", "✅"],
    lines: [C.checkRing(), C.busyMemo()],
  }),
  part({
    title: "마감 D-day 칸", summary: "#마감 꼬리표 날이 다가올수록 파랑 → 주황 → 빨강, 지나면 회색",
    wow: "#마감 꼬리표를 붙인 날 칸이 일주일 넘게 남으면 파랑, 일주일 안이면 주황, 사흘 안이면 빨강으로 바뀌고 고리처럼 울려요. 지난 마감은 회색이에요.",
    changes: ["남은 날 따라 테두리 색", "고리 움직임"], cover: ["#1E88E5", "#E53935", "⏳"],
    lines: [C.dday("마감"), { id: "cal.cell.date", when: '꼬리표.포함("마감") 그리고 며칠뒤 <= 3 그리고 며칠뒤 >= 0', weight: 900, ink: c("#E53935") }],
  }),
  // ---- cal.head: 달력 머리 (그달 값으로 when) ----
  part({
    title: "큰 숫자 머리", summary: "달 이름을 40 크기 굵은 숫자로, 야간 많은 달은 보라",
    wow: "달력 머리의 달 이름을 포스터처럼 큰 숫자로 세워요. 이달 야간이 여덟 번을 넘으면 숫자가 보라로 바뀌어요.",
    changes: ["큰 굵은 달 숫자", "가운데 정렬", "야간 많은 달 보라"], cover: ["#212121", "#7E57C2", "🔢"],
    lines: [{ id: "cal.head.title", size: 40, weight: 900, font: "bebasneue", talign: "center", track: 2 },
      { id: "cal.head.title", when: "이달야간 >= 8", ink: c("#7E57C2") }, { id: "cal.head", pad: 14 }],
  }),
  part({
    title: "글상자 머리", summary: "달력 머리를 테두리 글상자로, 이달 근무가 길면 빨간 테두리",
    wow: "달력 머리가 둥근 글상자가 되어 달 이름이 카드처럼 앉아요. 이달 일한 시간이 209시간을 넘으면 테두리가 빨갛게 바뀌어요.",
    changes: ["둥근 글상자", "얇은 테두리", "긴 근무 달 빨간 테두리"], cover: ["#FFFFFF", "#455A64", "🔲"],
    lines: [{ id: "cal.head", fill: c("#FFFFFF"), r: 18, line: "solid", lineW: 1.5, lineC: c("#90A4AE"), pad: 12, out: 8, shadow: 2 },
      { id: "cal.head", when: "이달근무시간 >= 209", lineC: c("#E53935"), lineW: 2.5 }, { id: "cal.head.title", weight: 800 }],
  }),
  part({
    title: "그라데이션 머리", summary: "보라→분홍 그라데이션 머리, 돈 들어온 달은 반짝",
    wow: "달력 머리를 보라에서 분홍으로 번지는 그라데이션으로 칠하고 흰 글자를 얹었어요. 이달 들어온 돈이 있으면 반짝여요.",
    changes: ["그라데이션 머리", "흰 글자 그림자", "수입 있는 달 반짝"], cover: ["#7E57C2", "#F06292", "🌈"],
    lines: [{ id: "cal.head", paint: "lg.135.FF7E57C2,FFF06292", r: 20, pad: 12 },
      { id: "cal.head.title", ink: c("#FFFFFF"), weight: 800, tshadow: 4, tshadowC: c("#4A148C"), tshadowY: 1 },
      { id: "cal.head", when: "이달수입 > 0", motion: "shimmer" }],
  }),
  // ---- cal.week: 요일 줄 ----
  part({
    title: "요일 알약", summary: "요일마다 알약 모양, 오늘 요일은 꽉 찬 색",
    wow: "요일 줄이 알약 모양 칩이 되고, 오늘 요일만 꽉 찬 색으로 바뀌어요. 토·일은 파랑·빨강 테두리예요.",
    changes: ["알약 요일", "오늘 요일 채움", "주말 테두리"], cover: ["#E8EAF6", "#3F51B5", "💊"],
    lines: [{ id: "cal.week.day", r: 12, line: "solid", lineW: 1, lineC: c("#C5CAE9"), padY: 2 },
      { id: "cal.week.day", on: "today", fill: c("#3F51B5"), ink: c("#FFFFFF"), weight: 800 },
      { id: "cal.week.day", on: "sat", lineC: c("#1E88E5"), ink: c("#1E88E5") }, { id: "cal.week.day", on: "sun", lineC: c("#E53935"), ink: c("#E53935") }],
  }),
  part({
    title: "영문 요일 줄", summary: "작고 넓게 띄운 요일, 주말만 색, 오늘은 밑줄",
    wow: "요일 글자를 작게 줄이고 자간을 넓혀 잡지처럼 정돈했어요. 주말만 색이 있고 오늘 요일에는 밑줄이 생겨요.",
    changes: ["작은 요일 글자", "넓은 자간", "오늘 밑줄"], cover: ["#FAFAFA", "#212121", "🗞️"],
    lines: [{ id: "cal.week.day", size: 10, track: 3, weight: 600, ink: c("#757575") }, { id: "cal.week.day", on: "today", deco: "under", ink: c("#212121"), weight: 800 },
      { id: "cal.week.day", on: "sun", ink: c("#E53935") }, { id: "cal.week.day", on: "sat", ink: c("#1E88E5") }],
  }),
  // ---- 달력 판 부품 ----
  part({
    title: "근무 띠 칸", styles: ROSTER, summary: "근무 띠 판의 칸이 근무 종류마다 색, 야간 세 번째부터 짙게",
    wow: "근무 띠(주간 띠) 판에서 주간·저녁·야간·쉬는 날 칸이 저마다 색으로 나뉘어요. 야간이 세 번 이어지면 그 칸이 짙은 남색이 돼요.",
    changes: ["근무별 띠 칸 색", "둥근 띠 칸", "이어진 야간 짙게"], cover: ["#FFE082", "#283593", "🎞️"],
    lines: [{ id: "cal.band.slot", r: 8 }, { id: "cal.band.slot", on: "role.day", fill: c("#FFE082") }, { id: "cal.band.slot", on: "role.eve", fill: c("#FFAB91") },
      { id: "cal.band.slot", on: "role.night", fill: c("#9FA8DA") }, { id: "cal.band.slot", on: "role.off", fill: c("#E0E0E0"), alpha: 60 }, D.bandNight()],
  }),
  part({
    title: "주간 판 하루", summary: "주간 판의 오늘은 진한 테두리, 많이 쓴 날은 빨간 밑줄",
    wow: "한 주를 크게 보는 주간 판에서 오늘 하루가 진한 테두리로 서고, 5만 넘게 쓴 날은 빨간 줄이 그어져요.",
    changes: ["둥근 하루 칸", "오늘 진한 테두리", "큰 지출 날 빨간 줄"], cover: ["#ECEFF1", "#E53935", "🗓️"],
    lines: [{ id: "cal.weekly.day", r: 14, fill: c("#FAFAFA"), pad: 8 }, { id: "cal.weekly.day", on: "today", line: "solid", lineW: 2.5, lineC: c("#3F51B5") },
      D.weeklySpend()],
  }),
  // ---- 판 (tabView 하나) ----
  part({
    title: "교대 순번 판", styles: ["SHIFT"], summary: "달력을 순번 판으로: 사람마다 근무가 줄로 보여요",
    wow: "달력 탭을 순번(로타) 판으로 바꿔 한 달 근무가 사람별 줄로 펼쳐져요. 같이 일하는 사람과 겹치는 날이 한눈에 보여요.",
    changes: ["달력 판 → 순번 판"], who: "근무표를 짜거나 나누는 교대 근무자", cover: ["#00796B", "#B2DFDB", "📋"],
    view: { cal: "rota" }, note: "달력을 보는 판만 바뀌어요. 달력 탭 위에서 언제든 달 판으로 돌아갈 수 있어요",
  }),
  part({
    title: "메모 꼬리표 보드", summary: "메모를 꼬리표별 보드로: #인수인계·#할일·#장보기가 칸마다",
    wow: "메모 탭이 꼬리표별 보드가 되어 #인수인계, #할일, #장보기가 칸마다 쌓여요. 메모를 칸 사이로 옮기면 꼬리표가 바뀌어요.",
    changes: ["메모 보기 → 꼬리표 보드"], cover: ["#FFF8E1", "#5C6BC0", "🗂️"], view: { memo: "board", memoBy: "tag" },
    note: "메모를 보는 판만 바뀌어요. 메모 탭 위에서 목록 보기로 돌아갈 수 있어요",
  }),
  part({
    title: "가계부 분류 보드", summary: "가계부를 분류 보드로: 식비·교통·쇼핑이 칸마다 합쳐져요",
    wow: "가계부 탭이 분류별 보드가 되어 식비, 교통, 쇼핑이 칸마다 모이고 칸 머리에 합계가 보여요.",
    changes: ["가계부 판 → 분류 보드"], cover: ["#E8F5E9", "#2E7D32", "🧺"], view: { money: "board" },
    note: "가계부를 보는 판만 바뀌어요. 가계부 탭 위에서 목록으로 돌아갈 수 있어요",
  }),
  // ---- 메모 ----
  part({
    title: "꼬리표 칩", summary: "메모 꼬리표 칩이 둥근 알약, 고른 꼬리표는 꽉 찬 색",
    wow: "메모 위 꼬리표 줄이 둥근 알약 칩으로 바뀌고, 지금 고른 꼬리표는 꽉 찬 보라로 빛나요.",
    changes: ["알약 꼬리표", "고른 꼬리표 채움"], cover: ["#EDE7F6", "#7E57C2", "🏷️"],
    lines: [{ id: "memo.tags", r: 16, line: "solid", lineW: 1, lineC: c("#B39DDB"), padX: 10 }, { id: "memo.tags", on: "sel", fill: c("#7E57C2"), ink: c("#FFFFFF"), weight: 700 }],
  }),
  part({
    title: "마감 메모 카드", summary: "'마감' 칸이 있는 메모 카드에 빨간 테두리",
    wow: "메모 카드가 둥근 흰 카드가 되고, '마감' 칸을 채운 메모는 빨간 테두리로 눈에 띄어요.",
    changes: ["둥근 흰 카드", "마감 메모 빨간 테두리"], cover: ["#FFFFFF", "#E53935", "📌"],
    lines: [{ id: "memo.card", fill: c("#FFFFFF"), r: 16, shadow: 2, pad: 12 }, { id: "memo.card.title", weight: 800 }, D.memoDue()],
  }),
  part({
    title: "끝낸 메모 흐리게", summary: "체크를 다 끝낸 메모 카드는 흐리게 물러나요",
    wow: "체크 목록을 다 끝낸 메모 카드는 반쯤 흐려져 남은 일만 또렷하게 보여요. 카드는 연노랑 포스트잇이에요.",
    changes: ["포스트잇 카드", "끝낸 메모 흐리게"], cover: ["#FFF59D", "#BDBDBD", "🗒️"],
    lines: [{ id: "memo.card", fill: c("#FFF9C4"), r: 4, shadow: 3 }, { id: "memo.card.body", font: "nanumpen", size: 15 }, D.memoDone()],
  }),
  part({
    title: "고정 메모 금색", summary: "고정한 메모 카드는 금색 테두리",
    wow: "고정해 둔 메모가 금색 테두리로 다른 카드와 구분돼요. 카드 제목은 명조로 단정하게.",
    changes: ["명조 카드 제목", "고정 메모 금색 테두리"], cover: ["#FFFFFF", "#FFB300", "⭐"],
    lines: [{ id: "memo.card", r: 12 }, { id: "memo.card.title", font: "nanummyeongjo", weight: 700 }, D.memoPinned()],
  }),
  // ---- 가계부 ----
  part({
    title: "들어온 돈 나간 돈 줄", summary: "가계부 줄이 수입은 초록 바탕, 지출은 흰 바탕, 큰 지출은 분홍",
    wow: "가계부 한 줄 한 줄이 들어온 돈이면 연한 초록, 나간 돈이면 흰 바탕이에요. 5만 넘는 지출 줄은 분홍으로 물들어요.",
    changes: ["수입 줄 초록", "지출 줄 흰색", "큰 지출 분홍"], cover: ["#E8F5E9", "#FFEBEE", "💳"],
    lines: [{ id: "money.row", r: 10, padX: 8 }, { id: "money.row", on: "in", fill: c("#E8F5E9") }, { id: "money.row", on: "out", fill: c("#FFFFFF") }, D.bigRow()],
  }),
  part({
    title: "영수증 줄", summary: "가계부 줄을 영수증처럼 고정폭 글자, 지출은 점선 아래",
    wow: "가계부 줄이 영수증처럼 고정폭 글자로 바뀌고, 지출 줄 아래에 점선이 그어져요. 수입 줄은 굵게.",
    changes: ["고정폭 글자", "지출 줄 점선", "수입 줄 굵게"], cover: ["#FAFAFA", "#424242", "🧾"],
    lines: [{ id: "money.row", font: "jetbrainsmono", size: 13 }, { id: "money.row", on: "out", line: "dot", lineW: 1, lineC: c("#BDBDBD") },
      { id: "money.row", on: "in", weight: 800 }],
  }),
  part({
    title: "큰 금액 숫자", summary: "가계부 금액을 큰 숫자 글꼴로, 수입 초록·지출 빨강",
    wow: "가계부 금액만 숫자 전용 큰 글꼴로 바꿨어요. 들어온 돈은 초록, 나간 돈은 빨강이라 줄을 읽지 않아도 보여요.",
    changes: ["숫자 글꼴", "수입 초록", "지출 빨강"], cover: ["#2E7D32", "#C62828", "💯"],
    lines: [{ id: "money.amount", font: "bebasneue", size: 20 }, { id: "money.amount", on: "in", ink: c("#2E7D32") }, { id: "money.amount", on: "out", ink: c("#C62828") }],
  }),
  part({
    title: "가계부 날짜 띠", summary: "가계부 날짜 줄이 띠로, 오늘은 진한 띠, 주말은 색 글자",
    wow: "가계부의 날짜 머리가 둥근 띠가 되고, 오늘 띠만 진한 색으로 바뀌어요. 토·일 날짜는 파랑·빨강이에요.",
    changes: ["날짜 띠", "오늘 진한 띠", "주말 색"], cover: ["#E3F2FD", "#1565C0", "📅"],
    lines: [{ id: "money.day", fill: c("#F1F5F9"), r: 10, padX: 10, weight: 700 }, { id: "money.day", on: "today", fill: c("#1565C0"), ink: c("#FFFFFF") },
      { id: "money.day", on: "sat", ink: c("#1E88E5") }, { id: "money.day", on: "sun", ink: c("#E53935") }],
  }),
  part({
    title: "가계부 달력 칸", summary: "가계부 달력 판의 칸이 둥글게, 오늘은 초록 테두리",
    wow: "가계부를 달력 판으로 볼 때 칸이 둥근 타일이 되고 오늘 칸에 초록 테두리가 생겨요. 주말 칸은 연한 색이에요.",
    changes: ["둥근 타일 칸", "오늘 초록 테두리", "주말 연한 칸"], cover: ["#F1F8E9", "#43A047", "🟩"],
    lines: [{ id: "money.cal.cell", r: 10, fill: c("#FAFAFA") }, { id: "money.cal.cell", on: "today", line: "solid", lineW: 2, lineC: c("#43A047") },
      { id: "money.cal.cell", on: "sat", fill: c("#E3F2FD") }, { id: "money.cal.cell", on: "sun", fill: c("#FFEBEE") }],
  }),
  // ---- 근무·급여 ----
  part({
    title: "근무별 급여 줄", styles: ROSTER, summary: "급여 줄이 근무 종류마다 색, 12시간 넘은 날은 굵게",
    wow: "급여 화면의 날마다 줄이 주간·저녁·야간 색으로 나뉘어요. 12시간 넘게 일한 날 줄은 굵은 테두리가 생겨요.",
    changes: ["근무별 줄 색", "둥근 줄", "긴 근무 날 테두리"], cover: ["#FFE082", "#9FA8DA", "💵"],
    lines: [{ id: "pay.row", r: 10, padX: 8 }, { id: "pay.row", on: "role.day", fill: c("#FFF8E1") }, { id: "pay.row", on: "role.eve", fill: c("#FBE9E7") },
      { id: "pay.row", on: "role.night", fill: c("#E8EAF6") }, { id: "pay.row", when: "근무시간 >= 12", line: "solid", lineW: 2, lineC: c("#F57C00") }],
  }),
  part({
    title: "많이 번 날 금빛 줄", styles: ROSTER, summary: "그날 번 돈이 많은 급여 줄은 금빛",
    wow: "그날 급여가 15만을 넘은 줄은 금빛으로 반짝이고, 공휴일에 일한 줄은 빨간 글자예요. 수고한 날이 눈에 띄어요.",
    changes: ["많이 번 날 금빛", "공휴일 근무 빨간 글자"], cover: ["#FFD54F", "#FFF8E1", "🏅"],
    lines: [{ id: "pay.row", r: 12 }, { id: "pay.row", when: "그날급여 >= 150000", paint: "lg.90.FFFFF8E1,FFFFE082", motion: "shimmer" },
      { id: "pay.row", when: "공휴일 그리고 근무시간 > 0", ink: c("#C62828"), weight: 700 }],
  }),
  // ---- 위젯·앱 ----
  part({
    title: "위젯 근무 칸", summary: "위젯 칸이 근무 종류마다 색, 오늘은 진한 칸",
    wow: "홈 화면 위젯 칸이 근무 종류마다 색으로 나뉘어 이번 달 근무가 위젯에서 바로 보여요.",
    changes: ["근무별 위젯 칸 색", "오늘 진한 칸"], cover: ["#C5CAE9", "#3F51B5", "📱"],
    lines: [{ id: "widget.cell", r: 8 }, { id: "widget.cell", on: "today", fill: c("#3F51B5"), ink: c("#FFFFFF") },
      { id: "widget.cell", on: "role.day", fill: c("#FFE082"), fillA: 40 }, { id: "widget.cell", on: "role.night", fill: c("#9FA8DA"), fillA: 40 },
      { id: "widget.cell", on: "role.off", fill: c("#C8E6C9"), fillA: 40 }],
  }),
  part({
    title: "아래 탭 알약", summary: "고른 아래 탭이 알약 모양으로 빛나요",
    wow: "아래 탭 줄에서 지금 고른 탭만 둥근 알약 바탕과 굵은 글자로 떠올라요.",
    changes: ["고른 탭 알약", "굵은 글자"], cover: ["#E8EAF6", "#3F51B5", "🔘"],
    lines: [{ id: "app.tab", r: 20 }, { id: "app.tab", on: "sel", fill: c("#3F51B5"), fillA: 20, ink: c("#3F51B5"), weight: 800 }],
  }),
  part({
    title: "고른 칩 채움", summary: "앱 곳곳의 칩이 둥근 테두리, 고른 칩은 꽉 찬 색",
    wow: "필터·분류 칩이 얇은 테두리 알약이 되고, 고른 칩은 꽉 찬 초록에 흰 글자로 바뀌어요.",
    changes: ["테두리 칩", "고른 칩 채움"], cover: ["#E0F2F1", "#00897B", "💠"],
    lines: [{ id: "app.chip", r: 20, line: "solid", lineW: 1, lineC: c("#80CBC4") }, { id: "app.chip", on: "sel", fill: c("#00897B"), ink: c("#FFFFFF"), weight: 700 }],
  }),
];

