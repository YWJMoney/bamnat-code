// 기능별 · 근무·급여 22 (기획서 4-3, 4-10 22~24). 근무·급여 탭 하나만 바꿔요.
// 지금 도는 것: 통계 카드(3개월 평균·지난달 대비·주별·근무 종류 비율 …), 알바 시급 규칙(주휴·야간 가산·5인 이상·세금).
// pay.* 부품의 when·{fx}는 1.36 고침(급여 줄 조건·이달급여 채우기)이 합쳐지면 돌아요.
import { c, H, recipe, parts } from "./lib.mjs";

const NOTE = "근무·급여 화면 모양과 카드만 바뀌어요. 근무 기록과 급여 설정은 그대로예요";
const PAID = ["SHIFT", "DAY", "HOURLY"];

function pay(s) {
  const p = { bg: "#FFFFFF", card: "#FFFFFF", card2: "#F3F5FA", ink: "#222222", accent: "#3F51B5", ...s.p };
  const styles = s.styles ?? PAID;
  const lines = [
    { id: "pay.head", ...(p.titleFont ? { font: p.titleFont } : {}), weight: 700, ink: c(p.ink) },
    { id: "pay.total", ...(p.headPaint ? { paint: p.headPaint } : { fill: c(p.accent) }), r: p.r ?? 18, pad: 16, ink: c(p.headInk ?? "#FFFFFF"), size: p.totalSize ?? 30, weight: 800,
      ...(p.numFont ? { font: p.numFont } : {}), ...(p.glow ? { tshadow: 8, tshadowC: c(p.glow), tshadowX: 0, tshadowY: 0 } : {}) },
    { id: "pay.row", padY: 6 }, { id: "pay.row", on: "out", ink: c("#C62828") }, { id: "pay.row", on: "in", ink: c(p.income ?? "#1B5E20") },
    ...(s.parts ?? []), ...(s.dated ?? []),
  ];
  const payLook = styles.map((k) => `${k}~tt:${s.big ?? "xl"}|pl:${H(p.card2)}|mn:${H(p.accent)}|rw:${s.rows ?? "roomy"}|op:1|dy:1`).join("/");
  const pieces = [
    { kind: "screenLook", screens: `stats~bg:${H(p.bg)}|cd:${H(p.card)}|ik:${H(p.ink)}|ac:${H(p.accent)}${p.bgPaint ? `|fl:${p.bgPaint}` : ""}`, pay: payLook },
    s.stats ? { kind: "cards", stats: s.stats } : null, s.hourly ? { kind: "hourly", ...s.hourly } : null, parts(lines),
  ];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? NOTE, styles, cover: s.cover, inputs: s.inputs, pieces }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

const WAGE = [{ id: "i1", label: "시급", kind: "money", default: 10320, min: 0, hint: "내 시급으로 바꿔요" }];
const goal = (def) => ({ id: "pay.total", when: `이달급여 >= 칸("목표 급여", ${def})`, paint: "lg.120.FFFFD54F,FFFFA000", ink: c("#3E2723"), motion: "shimmer" });

export default [
  pay({
    title: "이달 예상 급여 큰 카드", showcase: false, summary: "예상 급여를 큰 숫자 카드로, 목표를 넘으면 금빛(고침 뒤)",
    wow: "급여 화면 맨 위가 큰 숫자 카드가 되고, 이달 예상 급여가 목표를 넘는 순간 카드가 금빛으로 바뀌어요(고침 뒤). 바로 아래 지난달 대비가 서요.",
    changes: ["큰 숫자 합계 카드", "지난달 대비·3개월 평균 카드", "목표 넘으면 금빛(고침 뒤)"], cover: ["#1A237E", "#FFD54F", "💰"],
    p: { accent: "#283593", headPaint: "lg.120.FF1A237E,FF3949AB", numFont: "bebasneue", totalSize: 44 }, stats: "pay,compare,three,tiles", dated: [goal(3000000)],
    inputs: [{ id: "i1", label: "목표 급여", kind: "money", default: 3000000, min: 0 }],
  }),
  pay({
    title: "야간·연장 분해", summary: "급여 항목을 넉넉한 줄로, 더하는 것은 초록·빼는 것은 빨강",
    wow: "급여 한 줄 한 줄이 넉넉해지고 야간·연장·휴일 같은 더하는 항목은 초록, 세금·보험처럼 빼는 항목은 빨강이라 어디서 얼마가 붙는지 바로 보여요. 근무 종류 비율 카드가 함께 서요.",
    changes: ["항목 줄 넉넉하게, 더하기·빼기 색", "근무 종류 비율·주별 카드"], cover: ["#E8F5E9", "#C62828", "🧩"],
    p: { accent: "#2E7D32", income: "#2E7D32" }, stats: "pay,kinds,weeks",
  }),
  pay({
    title: "주휴 조건 진행", styles: ["HOURLY"], summary: "주별 카드로 이번 주 15시간을 넘었는지, 주휴 계산은 켜서",
    wow: "주별 카드가 이번 주 일한 시간을 보여 주고, 주휴수당 계산이 켜져 있어 15시간을 넘긴 주는 급여에 주휴가 붙어요. 받을 때 시급만 넣어요.",
    changes: ["주별·급여 카드 맨 위", "주휴·야간 50% 가산·휴게 법정", "시급 칸"], cover: ["#FFF8E1", "#F9A825", "✅"],
    p: { accent: "#F9A825", headInk: "#3E2723" }, stats: "weeks,pay,tiles", inputs: WAGE,
    hourly: { wage: { input: "i1" }, weeklyPay: true, legalBreaks: true, night: 50, five: true, breakPay: "UNPAID", rounding: "AT_END" },
  }),
  pay({
    title: "3개월 평균", summary: "최근 3개월 평균과 흐름 카드, 들쭉날쭉한 달을 한눈에",
    wow: "3개월 평균 카드와 흐름 카드가 맨 위에 서서, 이번 달이 평소보다 많은지 적은지 바로 보여요.",
    changes: ["3개월 평균·흐름·지난달 대비 카드"], cover: ["#E3F2FD", "#1565C0", "📈"],
    p: { accent: "#1565C0" }, stats: "three,trend,compare,pay",
  }),
  pay({
    title: "투잡 근무지별", styles: ["HOURLY", "DAY"], summary: "근무 종류 비율과 주별 카드, 두 곳 일을 색으로 나눠",
    wow: "근무 종류 비율 카드가 두 곳 일의 비율을, 주별 카드가 주마다 시간을 보여 줘요. 급여 줄은 근무지 색으로 넉넉하게 나뉘어요.",
    changes: ["근무 종류 비율·주별 카드", "넉넉한 줄"], cover: ["#EDE7F6", "#FF7043", "🔀"],
    p: { accent: "#5E35B1" }, stats: "kinds,weeks,pay",
  }),
  pay({
    title: "시간당 실수령", styles: ["HOURLY"], summary: "세금 3.3%를 떼고 시급을 넣어, 실제 손에 쥐는 돈",
    wow: "3.3% 원천징수로 계산해 급여 카드에 실제 받을 돈이 서고, 빼는 항목이 빨갛게 보여요. 시급만 바꾸면 그대로 맞춰져요.",
    changes: ["3.3% 세금 계산", "급여·주별 카드", "시급 칸"], cover: ["#ECEFF1", "#37474F", "🧾"],
    p: { accent: "#37474F" }, stats: "pay,weeks", inputs: WAGE,
    hourly: { wage: { input: "i1" }, weeklyPay: true, tax: "WITHHOLDING_3_3", rounding: "AT_END" },
  }),
  pay({
    title: "지난달 대비", summary: "지난달과 견준 카드를 맨 위에, 오른 것은 초록",
    wow: "지난달 대비 카드가 맨 위에서 이번 달이 얼마나 늘었는지 줄었는지 보여 주고, 연간 카드로 1년 흐름까지 이어져요.",
    changes: ["지난달 대비·연간·흐름 카드"], cover: ["#E8F5E9", "#43A047", "⚖️"],
    p: { accent: "#43A047" }, stats: "compare,year,trend",
  }),
  pay({
    title: "목표 급여 진행", summary: "이달 예상 급여가 목표에 닿으면 합계 카드가 금빛(고침 뒤)",
    wow: "받을 때 목표 급여를 적어 두면, 이달 예상 급여가 목표를 넘는 날부터 합계 카드가 금빛으로 반짝여요(고침 뒤). 그전까지는 차분한 남색이에요.",
    changes: ["목표 칸", "목표 넘으면 금빛 합계(고침 뒤)", "급여·3개월 평균 카드"], cover: ["#283593", "#FFD54F", "🎯"],
    p: { accent: "#283593" }, stats: "pay,three", dated: [goal(2500000)], inputs: [{ id: "i1", label: "목표 급여", kind: "money", default: 2500000, min: 0 }],
  }),
  pay({
    title: "근무 종류 비율", styles: ["SHIFT"], summary: "주간·저녁·야간 비율 카드를 맨 위에",
    wow: "근무 종류 비율 카드가 맨 위에서 이번 달 야간이 얼마나 되는지 보여 주고, 주별 카드로 야간이 몰린 주를 찾아요.",
    changes: ["근무 종류 비율·주별·타일 카드"], cover: ["#311B92", "#B39DDB", "🥧"],
    p: { accent: "#5E35B1" }, stats: "kinds,weeks,tiles",
  }),
  pay({
    title: "주 52시간 막대", styles: ["DAY", "SHIFT"], summary: "주별 카드로 52시간 가까운 주를, 타일 카드로 이달 시간을",
    wow: "주별 카드가 주마다 일한 시간을 막대로 보여 주고 타일 카드가 이달 합계를 보여 줘요. 52시간 근처 주가 바로 보여요.",
    changes: ["주별·타일·흐름 카드"], cover: ["#FFEBEE", "#D32F2F", "📊"],
    p: { accent: "#D32F2F" }, stats: "weeks,tiles,trend",
  }),
  pay({
    title: "급여 명세 꾸밈", summary: "급여 화면을 명세서처럼, 모노 숫자와 얇은 선",
    wow: "급여 화면이 종이 명세서처럼 바뀌어요. 항목 줄은 모노 숫자, 합계는 이중선 안에 서요.",
    changes: ["명세서 모양 합계·줄", "모노 숫자", "급여 카드"], cover: ["#FAFAFA", "#212121", "📃", 0],
    p: { accent: "#FFFFFF", headInk: "#212121", numFont: "robotomono", card2: "#FFFFFF" }, stats: "pay,compare",
    parts: [{ id: "pay.total", line: "double", lineW: 3, lineC: c("#212121"), shadow: 0 }, { id: "pay.row", font: "robotomono", line: "solid", lineW: 0.5, lineC: c("#E0E0E0") }],
  }),
  pay({
    title: "다크 리포트", summary: "검정 바탕 리포트, 합계 숫자가 빛나는 급여 화면",
    wow: "급여 화면이 검은 리포트가 되고 합계 숫자가 은은하게 빛나요. 3개월 평균과 흐름 카드가 데이터 리포트처럼 서요.",
    changes: ["검정 리포트 화면", "빛나는 합계 숫자", "3개월 평균·흐름 카드"], cover: ["#000000", "#00E5FF", "🌌"],
    p: { bg: "#0B0D12", card: "#151821", card2: "#1C2030", ink: "#E8ECF5", accent: "#00B8D4", headPaint: "lg.120.FF0B0D12,FF1C2030", headInk: "#7DF9FF", glow: "#00E5FF", numFont: "orbitron" },
    stats: "pay,three,trend",
  }),
  pay({
    title: "연차 남은 날", styles: ["DAY", "SHIFT"], summary: "타일 카드로 연차 남은 날, 연간 카드로 쓴 흐름",
    wow: "타일 카드가 이번 달 근무·휴가를 숫자로 보여 주고, 연간 카드가 1년 동안 쉰 날의 흐름을 보여 줘요.",
    changes: ["타일·연간 카드", "휴가 하늘색 화면"], cover: ["#E0F7FA", "#00ACC1", "🏖️"],
    p: { accent: "#00ACC1" }, stats: "tiles,year,pay",
  }),
  pay({
    title: "근무 패턴 요약", styles: ["SHIFT"], summary: "주별·근무 종류·타일을 한 화면에 촘촘하게",
    wow: "주별·근무 종류·타일 카드를 촘촘한 줄로 한 화면에 모아 이번 달 근무 모양을 한눈에 봐요.",
    changes: ["촘촘한 줄", "주별·근무 종류·타일 카드"], cover: ["#ECEFF1", "#00897B", "🧭"], rows: "compact",
    p: { accent: "#00897B" }, stats: "weeks,kinds,tiles,trend",
  }),
  pay({
    title: "야간 가산 알바", styles: ["HOURLY"], summary: "밤 10시~새벽 6시 50% 가산과 주휴, 5인 이상 가게 기준",
    wow: "야간(22시~6시) 50% 가산, 주휴수당, 5인 이상 사업장 기준이 켜져 있어 밤 근무가 많은 달도 받을 돈이 맞아요. 시급만 넣으면 돼요.",
    changes: ["야간 50%·주휴·5인 이상", "급여·주별 카드", "밤 네온 화면"], cover: ["#1A0033", "#FF2BD6", "🌙"],
    p: { bg: "#0E0718", card: "#1A0E2B", card2: "#241338", ink: "#F3E5F5", accent: "#FF2BD6", headPaint: "lg.120.FF2A0845,FF6A0080", glow: "#FF80EA" },
    stats: "pay,weeks", inputs: WAGE, hourly: { wage: { input: "i1" }, night: 50, weeklyPay: true, five: true, legalBreaks: true, breakPay: "UNPAID" },
  }),
  pay({
    title: "4대보험 실수령 알바", styles: ["HOURLY"], summary: "4대보험을 떼는 알바의 실수령, 휴게는 법대로",
    wow: "4대보험을 떼는 곳이면 급여에서 보험료가 빨간 줄로 빠지고 실수령이 합계에 서요. 휴게 시간은 법정대로 빼요.",
    changes: ["4대보험 계산", "법정 휴게", "급여·지난달 대비 카드"], cover: ["#E3F2FD", "#0D47A1", "🛡️"],
    p: { accent: "#0D47A1" }, stats: "pay,compare", inputs: WAGE,
    hourly: { wage: { input: "i1" }, weeklyPay: true, legalBreaks: true, tax: "INSURANCE_4", partTime: true },
  }),
  pay({
    title: "파스텔 급여", summary: "급여 화면을 말랑한 파스텔로, 합계는 분홍 카드",
    wow: "급여 합계가 분홍 파스텔 카드가 되고 항목 줄이 넉넉해져요. 주별 카드가 이번 주를 귀엽게 보여 줘요.",
    changes: ["분홍 합계 카드", "넉넉한 줄", "급여·주별 카드"], cover: ["#F8BBD0", "#B39DDB", "🍭"],
    p: { bg: "#FFF8FB", accent: "#F06292", headPaint: "lg.120.FFF48FB1,FFCE93D8", titleFont: "jua", numFont: "jua", r: 26 }, stats: "pay,weeks",
  }),
  pay({
    title: "세리프 급여 리포트", summary: "잡지처럼 큰 세리프 숫자와 아이보리 바탕",
    wow: "급여 합계가 잡지 표지처럼 큰 세리프 숫자로 서고, 3개월 평균·연간 카드가 차분하게 이어져요.",
    changes: ["큰 세리프 합계", "아이보리 화면", "3개월 평균·연간 카드"], cover: ["#F5F0E6", "#3E2723", "🖋️"],
    p: { bg: "#F8F4EA", card: "#FFFDF8", ink: "#1E1E1E", accent: "#5D4037", numFont: "playfair", totalSize: 40 }, stats: "pay,three,year",
  }),
  pay({
    title: "한 손 급여", summary: "합계는 아래쪽 크게, 줄은 큰 글씨로 엄지가 닿게",
    wow: "급여 합계 카드가 크고 줄 글씨가 커서 한 손으로 넘겨 봐도 읽혀요. 타일 카드가 가장 먼저 서요.",
    changes: ["큰 줄 글씨", "타일·급여 카드"], cover: ["#E8F5E9", "#43A047", "👍"], big: "xl",
    p: { accent: "#43A047" }, stats: "tiles,pay", parts: [{ id: "pay.row", size: 17, padY: 10 }],
  }),
  pay({
    title: "공수 합계판", styles: ["FIELD"], summary: "일당·공수 화면을 굵은 숫자와 형광 노랑으로",
    wow: "현장 일당 화면의 합계가 콘크리트 위 형광 노랑 숫자로 서고, 줄마다 공수가 굵게 보여요. 근무 끝 공수 계산은 그대로예요.",
    changes: ["콘크리트 합계 카드", "굵은 공수 숫자"], cover: ["#455A64", "#FFEB3B", "🧱"],
    p: { accent: "#F9A825", headPaint: "tx.concrete.FF607D8B.80", headInk: "#FFEB3B", numFont: "blackhansans" },
    dated: [{ id: "pay.row", when: "금액 >= 200000", weight: 800, ink: c("#F57F17") }],
  }),
  pay({
    title: "플랫폼 건수판", styles: ["FIELD"], summary: "배달·대행 플랫폼 수입 화면을 민트 대시보드로",
    wow: "플랫폼 수입 화면이 민트 대시보드가 되고, 큰 금액 줄은 굵게 남아요(고침 뒤). 합계가 큰 숫자로 서요.",
    changes: ["민트 대시보드 합계", "큰 금액 줄 굵게(고침 뒤)"], cover: ["#00BFA5", "#263238", "🛵"],
    p: { accent: "#00BFA5", headPaint: "lg.120.FF00897B,FF00BFA5", numFont: "oswald" },
    dated: [{ id: "pay.row", when: "금액 >= 100000", weight: 800 }],
  }),
  pay({
    title: "연간 한눈 급여", summary: "연간 카드를 맨 위에, 열두 달 흐름과 지난달 대비",
    wow: "연간 카드가 맨 위에서 열두 달 급여 흐름을 막대로 보여 주고, 지난달 대비와 3개월 평균이 이어져요.",
    changes: ["연간·지난달 대비·3개월 평균 카드"], cover: ["#ECEFF1", "#1E88E5", "📅"],
    p: { accent: "#1E88E5" }, stats: "year,compare,three",
  }),
];
