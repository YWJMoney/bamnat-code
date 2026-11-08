// 1.39 [MKW-03] 탭별 › 가계부 50. 달력 50과 같은 미술 방향(skins.mjs) 한 세트씩: 가계부 바탕·날짜 띠·줄·금액을 그 방향으로 그리고,
// 그 유형에 맞는 카드 순서·보기 판(목록·달력·보드·표)과, 줄·그날 값(금액·분류·지출·지출근무시간·칸합계, 1.36~1.38 수식, 무료)에
// 따라 바뀌는 줄을 더해요. 색은 바탕 밝기에 맞춰 옅게 깔아(fillA) 밝은·어두운 작품 모두에서 글자가 읽혀요.
import { c } from "../../market-136/make/lib.mjs";
import { lg, pt, so } from "./art.mjs";
import { tabWork, skin } from "./skins.mjs";

// ---- 줄·그날 값에 따라 바뀌는 가계부 (작품마다 두세 개) ----
const f = (say, tag, lines, inputs) => ({ say, tag, lines: Array.isArray(lines) ? lines : [lines], ...(inputs ? { inputs } : {}) });
const F = {
  big: (hi = 50000, col = "#E53935") => f("큰 지출 줄은 붉은 기운이 깔려 바로 눈에 띄어요.", "큰 지출 줄 강조",
    { id: "money.row", when: `종류 = "지출" 그리고 금액 >= ${hi}`, fill: c(col), fillA: 16, line: "solid", lineW: 1, lineC: c(col), lineA: 50, r: 10 }),
  inc: (col = "#2E9E5B") => f("돈이 들어온 줄은 초록 기운이 깔려요.", "수입 줄 초록", { id: "money.row", when: '종류 = "수입"', fill: c(col), fillA: 16, r: 10 }),
  cat: (cat, col) => f(`‘${cat}’ 줄은 그 색 테두리로 바로 보여요.`, `${cat} 줄 색`, { id: "money.row", when: `분류 = "${cat}"`, line: "solid", lineW: 2, lineC: c(col), r: 10 }),
  hours: (col = "#EF6C00") => f("지출 한 줄이 근무 다섯 시간어치를 넘으면 금액이 주황으로 굵어져요. 쓴 돈을 일한 시간으로 느껴요.", "지출 = 근무 시간",
    { id: "money.amount", on: "out", when: "지출근무시간 >= 5", ink: c(col), weight: 900 }),
  dayOver: (hi = 50000, col = "#E53935") => f("하루 쓴 돈이 많은 날은 날짜 띠 아래 붉은 선이 그어져요.", "많이 쓴 날 붉은 선",
    { id: "money.day", when: `지출 >= ${hi}`, line: "solid", lineW: 2, lineC: c(col) }),
  dayIncome: (col = "#2E9E5B") => f("돈이 들어온 날은 날짜 띠가 초록 테두리로 빛나요.", "들어온 날 초록 띠",
    { id: "money.day", when: "수입 > 0", line: "solid", lineW: 2, lineC: c(col), motion: "glow" }),
  heat: () => f("가계부 달력 판에서 쓴 돈이 많은 날일수록 칸이 노랑 → 주황 → 빨강으로 물들어요.", "가계부 달력 열지도", [
    { id: "money.cal.cell", when: "지출 >= 30000", fill: c("#FFD54F"), fillA: 45 },
    { id: "money.cal.cell", when: "지출 >= 60000", fill: c("#FF9800"), fillA: 50 },
    { id: "money.cal.cell", when: "지출 >= 100000", fill: c("#E53935"), fillA: 50 }]),
  noSpend: (col = "#43A047") => f("가계부 달력 판에서 한 푼도 안 쓴 지난날은 초록 칸이 돼요.", "무지출 날 초록 칸",
    { id: "money.cal.cell", when: "지난날 그리고 지출 = 0", fill: c(col), fillA: 40 }),
  boardOver: (lim = 300000) => f("분류 보드에서 한 칸 합계가 많아지면 그 칸에 붉은 테두리가 둘러져요.", "넘친 분류 붉은 테",
    { id: "money.board.col", when: `칸합계 > ${lim}`, line: "solid", lineW: 2, lineC: c("#E53935") }),
  tableBig: (hi = 50000) => f("표 판에서도 큰 지출 줄은 붉은 기운이 깔려요.", "표 큰 지출 강조",
    { id: "money.table.row", when: `종류 = "지출" 그리고 금액 >= ${hi}`, fill: c("#E53935"), fillA: 14 }),
  payday: () => f("월급날이 사흘 안으로 다가오면 맨 위 합계 머리가 반짝여요.", "월급 사흘 전 반짝",
    { id: "money.head", when: "월급까지 >= 0 그리고 월급까지 <= 3", motion: "shimmer" }),
  budget: (def = 1500000) => f("이달 쓴 돈이 한 달 예산(넣을 때 정해요)을 넘으면 맨 위 머리에 붉은 테두리가 둘러져요.", "예산 넘은 달 붉은 테",
    { id: "money.head", when: `이달지출 > 칸("한 달 예산", ${def})`, line: "solid", lineW: 2.5, lineC: c("#E53935") },
    [{ label: "한 달 예산", kind: "money", min: 0, default: def, hint: "이달 쓸 돈의 한도" }]),
  saved: (col = "#2E9E5B") => f("이달 들어온 돈이 쓴 돈보다 많으면 맨 위 머리에 초록 테두리가 둘러져요.", "남는 달 초록 테",
    { id: "money.head", when: "이달수입 > 이달지출", line: "solid", lineW: 2, lineC: c(col) }),
};

const CARDS = {
  SHIFT: ["hero,shiftspend,pace,recent", "hero,ahead,budget,recent", "shiftspend,hero,upcoming", "pace,hero,story,recent"],
  DAY: ["hero,budget,upcoming,cardbill", "hero,card25,cardbill,recent", "pace,budget,hero,subs", "hero,accounts,ahead"],
  HOURLY: ["hero,income,ahead,recent", "ahead,hero,pace", "hero,tax,income,recent"],
  FIELD: ["work,owed,freelance,hero", "hero,work,owed,recent", "freelance,hero,work"],
  BUSINESS: ["hero,salessplit,cardbill,work", "salessplit,hero,upcoming,recent", "hero,work,owed,salessplit"],
  INVEST: ["hero,accounts,ahead,story", "accounts,hero,pace,recent"],
  ALL: ["hero,pace,budget,recent", "hero,envelopes,pace", "story,hero,found,recent", "hero,subs,upcoming,repeat", "envelopes,hero,ahead"],
};

/** One money work: s.from (달력 세트), s.f (바뀌는 줄), s.cards, s.view, s.ledger, the words. */
function money(s) {
  const k = skin(s.from);
  const style = s.style ?? k.style;
  const feats = s.f ?? [];
  const wow = [s.lead, ...feats.map((x) => x.say)].filter(Boolean).join(" ");
  const cards = s.cards ?? CARDS[style ?? "ALL"][s.ci ?? 0];
  return tabWork("money", {
    ...s, ...(s.style ? { style: s.style } : {}), wow, changes: [...(s.changes ?? []), ...feats.map((x) => x.tag)],
    parts: [...(s.parts ?? []), ...feats.flatMap((x) => x.lines)],
    inputs: feats.flatMap((x) => x.inputs ?? []).map((x, i) => ({ ...x, id: `i${i + 1}` })),
    ...(s.view ? { view: { money: s.view } } : {}),
    tabExtra: [{ kind: "cards", money: cards }, s.ledger ? { kind: "ledger", ...s.ledger } : null].filter(Boolean),
    note: s.ledger ? "가계부 모양과 분류·예산 틀을 더해요. 적은 돈 기록은 그대로이고, 분류·예산은 넣은 뒤 내 것으로 고쳐요" : undefined,
  });
}

// 가계부 틀(분류·예산·봉투): 금액은 받는 사람이 고쳐요.
const LEDGER = {
  shift: { costs: ["식비", "야식", "카페", "교통", "택시", "수면용품", "건강", "생활", "통신", "구독"], incomes: ["급여", "야간수당", "부수입"],
    budgets: [{ category: "야식", amount: 100000 }, { category: "택시", amount: 80000 }] },
  office: { costs: ["점심", "카페", "교통", "경조사", "자기계발", "생활", "통신", "구독", "쇼핑"], incomes: ["급여", "상여", "부수입"],
    budgets: [{ category: "점심", amount: 200000 }, { category: "카페", amount: 60000 }] },
  part: { costs: ["식비", "교통", "카페", "학비", "생활", "통신"], incomes: ["알바비", "주휴수당", "용돈"],
    envelopes: [{ name: "비상금", emoji: "🛟", share: 10 }, { name: "여행", emoji: "✈️", share: 10 }] },
  field: { costs: ["식대", "유류비", "공구", "작업복", "숙소", "교통", "간식"], incomes: ["일당", "공수", "현장 수당"],
    budgets: [{ category: "유류비", amount: 200000 }] },
  shop: { costs: ["재료", "포장재", "임대료", "공과금", "인건비", "카드수수료", "배달수수료", "광고"], incomes: ["카드 매출", "현금 매출", "배달 매출", "이체 매출"],
    envelopes: [{ name: "부가세", emoji: "🧾", share: 10 }, { name: "종합소득세", emoji: "📮", share: 5 }] },
  saver: { costs: ["식비", "카페", "교통", "생활", "쇼핑", "취미", "구독"], incomes: ["급여", "부수입"],
    envelopes: [{ name: "비상금", emoji: "🛟", share: 10 }, { name: "모으기", emoji: "🌱", share: 15 }, { name: "여행", emoji: "🧳", share: 5 }] },
};

export default [
  // ---------- 교대 12 ----------
  money({ from: "야간 레이더 달력", title: "야간 레이더 지출 관제", top: true, summary: "관제 모니터처럼 검은 가계부, 근무 다섯 시간어치 넘는 지출은 주황 경보",
    lead: "가계부가 관제실 모니터가 되어 금액이 초록 형광으로 떠요.", changes: ["관제 모니터 가계부", "초록 형광 금액"],
    parts: [{ id: "money.amount", tshadow: 6, tshadowC: c("#35F08F"), tshadowX: 0, tshadowY: 0 }], f: [F.hours(), F.dayOver(40000, "#FF5252")] }),
  money({ from: "수간호사 근무표", title: "병동 살림 장부", summary: "명세서처럼 정갈한 표 판, 큰 지출 줄만 붉게", view: "table",
    lead: "가계부가 병동 장부처럼 얇은 표 선으로 정리돼요.", changes: ["표 판", "청록 머리"], ci: 1, f: [F.tableBig(), F.payday()], ledger: LEDGER.shift }),
  money({ from: "달빛 띠 근무 달력", title: "야식 줄이기 달빛 장부", summary: "남색 밤 가계부, 야식·택시 줄이 달빛 테두리로",
    lead: "밤하늘 남색 위에 날짜 띠가 달빛처럼 은은해요.", changes: ["밤하늘 가계부"], ci: 0, f: [F.cat("야식", "#9FB4FF"), F.cat("택시", "#FFD166"), F.big(40000, "#FF6B6B")] }),
  money({ from: "타임라인 교대표", title: "근무별 소비 막대", summary: "근무별 소비 카드가 맨 위, 많이 쓴 날 붉은 선",
    lead: "야간 다음 날과 쉬는 날 소비가 어떻게 다른지 카드로 먼저 보여요.", changes: ["근무별 소비 먼저"], ci: 2, f: [F.dayOver(), F.hours()] }),
  money({ from: "새벽 4시 하늘", title: "새벽빛 무지출 달력", summary: "가계부를 새벽빛 달력 판으로, 안 쓴 날은 초록 칸", view: "cal",
    lead: "가계부 달력 칸이 새벽빛 보라에서 살구로 물들어요.", changes: ["달력 판"], ci: 3, f: [F.noSpend("#7BC8A4"), F.heat()] }),
  money({ from: "지하철 환승 달력", title: "교통비 환승 장부", summary: "교통·택시 줄이 노선 색 테두리, 노선도 같은 굵은 가계부",
    lead: "가계부 줄마다 노선 색 띠가 지나가요.", changes: ["노선 색 줄"], ci: 0,
    parts: [{ id: "money.row", line: "solid", lineW: 0.5, lineC: c("#D0D0D0") }], f: [F.cat("교통", "#00A84D"), F.cat("택시", "#EF7C1C"), F.big()] }),
  money({ from: "오로라 유리 칸", title: "오로라 유리 가계부", top: true, summary: "흐르는 오로라 위 유리 날짜 띠, 들어온 날은 띠가 빛나요",
    lead: "가계부 뒤로 오로라가 흐르고 날짜 띠는 김 서린 유리예요.", changes: ["오로라 바탕", "유리 날짜 띠"], ci: 1,
    paint: { band: "gl.80121A38.18" }, f: [F.dayIncome("#5EF2C2"), F.big(50000, "#FF7AA2"), F.saved("#5EF2C2")] }),
  money({ from: "소방서 출동판", title: "출동 대기 지출판", summary: "빨강 사선 머리, 하루 많이 쓴 날 경광 선", view: "board",
    lead: "분류 보드가 출동판처럼 굵게 서고 맨 위 머리는 빨강 사선이에요.", changes: ["분류 보드", "빨강 사선 머리"], ci: 2,
    paint: { head: pt("stripes", "#D7261E", "#B81E17", 240, 45) }, p: { headInk: "#FFFFFF" }, f: [F.boardOver(), F.dayOver(50000, "#FFC400")] }),
  money({ from: "4조2교대 큰 칸", title: "돋보기 없는 가계부", summary: "금액이 크고 줄 간격이 넉넉한, 돋보기 없이 읽는 가계부",
    lead: "금액이 크고 줄 사이가 넉넉해 한눈에 읽혀요.", changes: ["큰 금액", "넉넉한 줄"], ci: 0, money: { rows: "roomy", amount: "l", dayHead: "big" },
    parts: [{ id: "money.amount", size: 18 }], f: [F.big(), F.inc()] }),
  money({ from: "수면 빚 달력", title: "피곤한 날 소비 일지", summary: "근무 다섯 시간어치 넘는 지출은 주황, 지친 날 충동 소비를 비춰요",
    lead: "회청색 차분한 가계부에 쓴 돈이 일한 시간으로 보여요.", changes: ["회청색 가계부"], ci: 2, f: [F.hours(), F.cat("야식", "#7A8FB8"), F.dayOver(40000)] }),
  money({ from: "체크무늬 간호 수첩", title: "분홍 체크 가계부", summary: "분홍 체크 수첩에 손글씨 금액, 남는 달은 초록 테",
    lead: "가계부가 분홍 체크 수첩이 되고 금액은 손글씨예요.", changes: ["분홍 체크 바탕", "손글씨 금액"], ci: 1, f: [F.saved(), F.big(40000, "#C2185B")], ledger: LEDGER.saver }),
  money({ from: "항구 교대 해도", title: "선원 항해 장부", summary: "해도 종이 위 남색 가계부, 한 달 예산을 넘으면 머리에 붉은 테",
    lead: "가계부가 오래된 항해 장부처럼 물결 종이 위에 놓여요.", changes: ["해도 종이 바탕"], ci: 1, f: [F.budget(), F.inc("#2F5D7C")] }),

  // ---------- 일근 8 ----------
  money({ from: "주간 플래너 펼침", title: "에디토리얼 가계부", top: true, summary: "잡지 지면처럼 큰 세리프 금액, 붉은 가는 선만 남긴 가계부",
    lead: "금액이 잡지 표지처럼 큰 세리프로 서고 줄 사이엔 붉은 가는 선만 남아요.", changes: ["세리프 금액", "붉은 가는 선"],
    parts: [{ id: "money.row", line: "solid", lineW: 0.5, lineC: c("#C0392B"), lineA: 40 }], f: [F.budget(2000000), F.big(100000, "#C0392B")] }),
  money({ from: "칼퇴 신호등", title: "점심값 신호등", summary: "예산 넘은 달은 머리에 빨간불, 점심·카페 줄이 신호 색", ci: 0,
    lead: "가계부가 초록 신호로 시작하고 예산을 넘으면 빨간불이 켜져요.", changes: ["신호 색 줄"], f: [F.budget(), F.cat("점심", "#F29900"), F.cat("카페", "#2E7D32")], ledger: LEDGER.office }),
  money({ from: "재택·출근 반반", title: "재택·출근 지출 반반", summary: "하늘·살구 두 색 날짜 띠, 출근 날 교통·점심이 보여요", ci: 2,
    lead: "날짜 띠가 하늘색과 살구색으로 번져 출근 날 소비가 눈에 들어와요.", changes: ["두 색 날짜 띠"], paint: { band: lg(90, "#DCEEFF", "#FFE6D6") },
    f: [F.cat("교통", "#4A90D9"), F.cat("점심", "#F08A5D")] }),
  money({ from: "분기 목표 연간판", title: "분기 저축 계획표", summary: "모으기 봉투와 흐름 카드 먼저, 남는 달은 초록 테", cards: "envelopes,pace,hero",
    lead: "숲색 가계부에 봉투와 이달 흐름 카드가 먼저 놓여요.", changes: ["봉투 먼저"], f: [F.saved(), F.inc()], ledger: LEDGER.saver }),
  money({ from: "사원증 스트랩", title: "카드값 미리 보는 장부", summary: "다음 카드값·카드 공제 카드 먼저, 큰 지출은 남색 테두리", ci: 1,
    lead: "남색 스트랩 줄무늬 머리 아래 카드값 카드가 먼저 서요.", changes: ["줄무늬 머리"], paint: { head: pt("stripes", "#24408E", "#1B3170", 120, 0) }, p: { headInk: "#FFFFFF" },
    f: [F.big(100000, "#24408E"), F.payday()] }),
  money({ from: "점심 메뉴 달력", title: "점심 식권 장부", summary: "식권처럼 점선 줄, 점심값이 쌓이는 게 보이는 달력 판", view: "cal",
    lead: "가계부 달력 칸이 식권처럼 점선 테를 둘러요.", changes: ["점선 칸 달력 판"], ci: 2,
    parts: [{ id: "money.cal.cell", line: "dash", lineW: 1, lineC: c("#E9A23B") }], f: [F.heat(), F.noSpend()] }),
  money({ from: "흑백 미니멀 일정", title: "미니멀 가계부", summary: "흰 바탕 검은 금액, 꼭 필요한 줄만", ci: 3,
    lead: "색을 덜어 내고 금액과 가는 선만 남겼어요.", changes: ["흑백 가계부"], money: { mark: "dot", rows: "normal" },
    parts: [{ id: "money.row", line: "solid", lineW: 0.5, lineC: c("#EFEFEF") }], f: [F.big(100000, "#111111")] }),
  money({ from: "회의실 예약판", title: "경비 정산 표", summary: "표 판으로 보는 업무 경비, 큰 지출 줄 강조", view: "table", ci: 0,
    lead: "가계부가 정산 표처럼 줄 맞춰 놓여요.", changes: ["표 판"], f: [F.tableBig(30000), F.cat("교통", "#2B6CB0")], ledger: LEDGER.office }),

  // ---------- 알바 6 ----------
  money({ from: "편의점 영수증 달력", title: "영수증 가계부", top: true, summary: "감열지 영수증처럼 점선으로 끊긴 가계부, 시간어치 지출은 주황",
    lead: "가계부 줄마다 영수증 점선이 지나가고 금액은 고정폭 숫자예요.", changes: ["영수증 점선 줄", "고정폭 금액"],
    parts: [{ id: "money.row", line: "dash", lineW: 1, lineC: c("#BDBDBD") }, { id: "money.head", line: "dash", lineW: 1.5, lineC: c("#0F9D58") }], f: [F.hours(), F.inc("#0F9D58")] }),
  money({ from: "버블티 알바 달력", title: "버블티 용돈 기입장", summary: "밀크티 색 동글동글 가계부, 들어온 날은 띠가 빛나요", ci: 1,
    lead: "가계부가 밀크티 빛 동그란 카드로 바뀌어요.", changes: ["밀크티 색 줄"], money: { mark: "emoji", rows: "roomy" }, f: [F.dayIncome("#B07A55"), F.cat("카페", "#E3B98F")], ledger: LEDGER.part }),
  money({ from: "투잡 두 색 달력", title: "투잡 수입 두 색 장부", summary: "두 일의 수입이 두 색 줄로, 시간어치 지출 경고", ci: 2,
    lead: "수입 카드가 먼저 서고 들어온 줄은 파랑 기운이 깔려요.", changes: ["수입 먼저"], f: [F.inc("#2962FF"), F.hours("#FF6D00")] }),
  money({ from: "픽셀 게임 출석부", title: "픽셀 코인 가계부", summary: "8비트 게임 화면 같은 가계부, 무지출 날은 초록 칸", view: "cal", ci: 0,
    lead: "가계부 달력 판이 게임 맵처럼 네모 칸으로 서요.", changes: ["8비트 달력 판"], parts: [{ id: "money.cal.cell", r: 0, line: "solid", lineW: 1, lineC: c("#5BFF7A"), lineA: 40 }],
    f: [F.noSpend("#5BFF7A"), F.heat()] }),
  money({ from: "주말 알바 도장판", title: "주말 도장 저금통", summary: "모으기 봉투 먼저, 남는 주말마다 초록 테", cards: "envelopes,income,hero",
    lead: "도장판처럼 동그란 가계부에 모으기 봉투가 먼저 놓여요.", changes: ["봉투 먼저"], f: [F.saved("#E85A8A"), F.dayIncome("#E85A8A")], ledger: LEDGER.part }),
  money({ from: "새벽 배송 루트", title: "배송 유류비 장부", summary: "어두운 길 위 형광 줄, 유류비·식대 줄이 형광 테두리", ci: 1,
    lead: "가계부가 밤길 내비처럼 검고 금액은 형광 연두예요.", changes: ["밤길 가계부"], f: [F.cat("유류비", "#D4FF3A"), F.cat("식비", "#5AD1FF"), F.dayOver(40000, "#FF7A59")] }),

  // ---------- 현장 5 ----------
  money({ from: "공사 일보 칠판", title: "현장 칠판 장부", top: true, summary: "분필로 적은 칠판 가계부, 일감 입금·받을 돈 카드 먼저",
    lead: "가계부가 현장 칠판이 되고 금액은 분필 글씨예요.", changes: ["칠판 가계부", "분필 금액"], f: [F.dayIncome("#F5E27A"), F.big(100000, "#FF8A65")], ledger: LEDGER.field }),
  money({ from: "철근 도면 달력", title: "자재비 도면 장부", summary: "청사진 모눈 위 표 판, 큰 자재비 줄 강조", view: "table", ci: 1,
    lead: "가계부가 청사진 도면처럼 흰 선 표로 놓여요.", changes: ["도면 표 판"], f: [F.tableBig(100000)] }),
  money({ from: "안전 제일 표지판", title: "안전 노랑 가계부", summary: "노랑·검정 사선 머리, 받을 돈 카드 먼저", ci: 1,
    lead: "맨 위 머리가 안전 표지처럼 노랑·검정 사선이에요.", changes: ["안전 사선 머리"], paint: { head: pt("stripes", "#FFD100", "#141414", 220, 45) }, p: { headInk: "#141414" },
    f: [F.big(100000), F.inc("#2E7D32")] }),
  money({ from: "일당 봉투 달력", title: "일당 봉투 장부", summary: "누런 봉투 종이에 붓글씨 금액, 들어온 날 띠가 빛나요", ci: 0,
    lead: "가계부가 일당 봉투처럼 누런 종이 위에 놓여요.", changes: ["봉투 종이 바탕"], f: [F.dayIncome("#8A5A2B"), F.cat("식대", "#C47A2C")], ledger: LEDGER.field }),
  money({ from: "해 뜨는 비계", title: "새벽 현장 지출판", summary: "해 뜨는 빛 날짜 띠, 분류 보드로 보는 현장 지출", view: "board", ci: 2,
    lead: "분류 보드가 해 뜨는 살구빛으로 서요.", changes: ["분류 보드"], f: [F.boardOver(200000)] }),

  // ---------- 자영업 5 ----------
  money({ from: "매출 온도계 달력", title: "매출 온도계 장부", top: true, summary: "매출 나눠 보기 카드 먼저, 매출 들어온 날은 띠가 빨갛게 데워져요",
    lead: "가계부가 온도계처럼 빨강으로 데워지고 카드·현금·배달 매출 비율이 먼저 보여요.", changes: ["매출 비율 먼저"],
    f: [F.dayIncome("#E8483B"), F.cat("재료", "#FF9F43")], ledger: LEDGER.shop }),
  money({ from: "꽃집 계절 달력", title: "꽃집 매입 장부", summary: "꽃잎 분홍 가계부, 재료·포장재 줄이 꽃 색", ci: 1,
    lead: "가계부가 꽃잎 분홍 종이에 놓여요.", changes: ["꽃잎 바탕"], f: [F.cat("재료", "#D9668A"), F.cat("포장재", "#7BAE7F")], ledger: LEDGER.shop }),
  money({ from: "오마카세 예약 장부", title: "카운터 금박 장부", summary: "먹색 바탕 금박 금액, 표 판으로 보는 매입·매출", view: "table", ci: 2,
    lead: "가계부가 먹색 장부가 되고 금액은 금박 세리프예요.", changes: ["먹색·금박 표"], f: [F.tableBig(200000)] }),
  money({ from: "장날 도장 달력", title: "장날 매상 달력", summary: "가계부 달력 판에 매상이 큰 날 붉은 도장색", view: "cal", ci: 0,
    lead: "가계부 달력 칸이 장터 종이처럼 놓이고 매상 많은 날이 물들어요.", changes: ["달력 판"], f: [F.heat(), F.dayIncome("#A8322D")] }),
  money({ from: "1인 카페 운영표", title: "카페 원두 장부", summary: "원두색 세리프 가계부, 분류 보드로 보는 재료·공과금", view: "board", ci: 1,
    lead: "분류 보드가 원두 빛 카드로 서요.", changes: ["분류 보드"], f: [F.boardOver(500000), F.cat("공과금", "#6F4E37")] }),

  // ---------- 투자 4 ----------
  money({ from: "캔들 차트 달력", title: "캔들 생활비 장부", top: true, summary: "검은 차트 화면에 들어온 줄은 초록, 큰 지출은 빨강",
    lead: "가계부가 차트 화면처럼 검고 수입은 초록, 큰 지출은 빨강으로 갈려요.", changes: ["차트 화면 가계부"], f: [F.inc("#22C55E"), F.big(100000, "#EF4444")] }),
  money({ from: "적립식 씨앗 달력", title: "씨앗 생활비 일지", summary: "새싹 색 가계부, 남는 달 초록 테두리", ci: 1,
    lead: "새싹 연두 가계부에 통장 카드가 먼저 놓여요.", changes: ["새싹 가계부"], f: [F.saved("#6BA34A"), F.budget()] }),
  money({ from: "배당 금화 달력", title: "금화 장부", summary: "세리프 금빛 금액, 들어온 날은 금빛 띠", ci: 0,
    lead: "금액이 금화처럼 세리프 금빛으로 놓여요.", changes: ["금빛 금액"], f: [F.dayIncome("#B8860B"), F.big()] }),
  money({ from: "매매 일지 모눈", title: "모눈 가계부", summary: "모눈 종이 위 손글씨 표 판", view: "table", ci: 1,
    lead: "가계부가 모눈 종이 표가 되고 금액은 손글씨예요.", changes: ["모눈 표 판"], f: [F.tableBig()] }),

  // ---------- 모든 유형 10 ----------
  money({ from: "비 오는 날 유리창", title: "빗방울 유리 가계부", summary: "빗방울 맺힌 어두운 유리 위 가계부, 남는 달 초록 테", ci: 0,
    lead: "가계부 뒤로 빗방울이 흐르고 금액은 따뜻한 등불 색이에요.", changes: ["빗방울 바탕"], f: [F.saved("#7FD1AE"), F.big(50000, "#FF8A80")] }),
  money({ from: "봄 햇살 테라조", title: "테라조 봉투 가계부", summary: "봄빛 테라조 무늬, 모으기 봉투가 먼저", ci: 1,
    lead: "가계부 바탕이 봄빛 테라조 돌 무늬예요.", changes: ["테라조 바탕"], f: [F.saved("#5FA87A"), F.inc()], ledger: LEDGER.saver }),
  money({ from: "뉴욕 신문 달력", title: "신문 경제면 가계부", summary: "신문 지면처럼 굵은 제목과 가는 줄, 지난달 이야기 카드 먼저", ci: 2,
    lead: "가계부가 경제면처럼 굵은 제목 아래 가는 줄로 정리돼요.", changes: ["신문 지면"], parts: [{ id: "money.row", line: "solid", lineW: 0.5, lineC: c("#111111"), lineA: 25 }], f: [F.big(100000, "#111111")] }),
  money({ from: "네온 사인 달력", title: "네온 영수증", summary: "네온 분홍 금액, 많이 쓴 날 네온 선", ci: 3,
    lead: "금액이 네온 사인처럼 분홍빛으로 번져요.", changes: ["네온 금액"], parts: [{ id: "money.amount", tshadow: 8, tshadowC: c("#FF4FB5"), tshadowX: 0, tshadowY: 0 }],
    f: [F.dayOver(50000, "#FF4FB5"), F.cat("구독", "#4FD8FF")] }),
  money({ from: "숲속 오두막 달력", title: "오두막 장작 장부", summary: "나무 결 가계부, 들어온 날 장작불 띠", ci: 4,
    lead: "가계부가 오두막 나무 결 위에 놓이고 금액은 등잔 빛이에요.", changes: ["나무 결 바탕"], f: [F.dayIncome("#E9A65A"), F.saved("#9CCC65")] }),
  money({ from: "우주 정거장 달력", title: "우주 정거장 보급 장부", summary: "별빛 바탕 달력 판, 무지출 날은 보라 칸", view: "cal", ci: 0,
    lead: "가계부 달력 판이 정거장 창 밖 별빛 위에 떠요.", changes: ["별빛 달력 판"], f: [F.noSpend("#A78BFA"), F.heat()] }),
  money({ from: "레트로 체크 피크닉", title: "피크닉 장바구니", summary: "빨강 체크 머리, 분류 보드로 장보기", view: "board", ci: 3,
    lead: "맨 위 머리가 피크닉 체크 천이고 분류 보드가 바구니처럼 서요.", changes: ["체크 머리", "분류 보드"], f: [F.boardOver(200000), F.cat("식비", "#D7263D")] }),
  money({ from: "독서 기록 서가", title: "책값 서가 장부", summary: "서가 종이 위 명조 가계부, 구독·책값 줄이 초록 테", ci: 3,
    lead: "가계부가 책 사이 종이처럼 명조로 놓여요.", changes: ["명조 가계부"], f: [F.cat("구독", "#2F5D4A"), F.cat("취미", "#8A6D3B")], ledger: LEDGER.saver }),
  money({ from: "운동 출석 히트맵", title: "무지출 히트맵", top: true, summary: "가계부 달력 판이 운동 잔디처럼, 안 쓴 날이 초록으로 쌓여요", view: "cal", ci: 1,
    lead: "가계부 달력 판이 잔디 히트맵처럼 네모 칸으로 놓여요.", changes: ["잔디 달력 판"], parts: [{ id: "money.cal.cell", r: 4 }], f: [F.noSpend("#1F9D55"), F.heat()] }),
  money({ from: "낮잠 고양이 달력", title: "고양이 간식 장부", summary: "살구빛 동글 가계부, 남는 달 초록 테", ci: 4,
    lead: "가계부가 낮잠 자는 고양이처럼 부드러운 살구빛이에요.", changes: ["살구빛 가계부"], f: [F.saved("#5FA87A"), F.cat("반려동물", "#E89A6B")] }),
];
