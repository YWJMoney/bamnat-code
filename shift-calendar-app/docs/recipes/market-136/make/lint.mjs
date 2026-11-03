// 서버 검사(cleanRecipe) 밖에서 작품이 지켜야 할 것 (기획서 3장): 유형에 맞는 말, 상호작용 하나 이상, 투자 작품의 추천 말 없음.
const WRONG = {
  // 알바·현장·자영업·투자에 회사 찾기, 자영업·투자에 연차·급여 말 (사용자 검증 규칙, 2026-10-29).
  HOURLY: ["연차", "회사 찾기", "사내"],
  FIELD: ["연차", "회사 찾기", "월급", "사내"],
  BUSINESS: ["연차", "급여", "월급", "회사", "출근"],
  INVEST: ["연차", "급여", "월급", "회사", "출근", "추천 종목", "수익 보장", "무조건"],
};
const INTERACTIVE = ["logic", "memoTpl", "alarms", "hourly", "field", "shop", "invest", "tabView", "cards"];

export function lint(r) {
  const out = [];
  const text = [r.title, r.summary, r.desc].join("\n");
  for (const s of r.styles) for (const w of WRONG[s] ?? []) if (text.includes(w)) out.push(`${s}에 '${w}'`);
  const hasWhen = JSON.stringify(r.pieces).includes('"when"') || JSON.stringify(r.pieces).includes('"fx"');
  const hasRule = r.pieces.some((p) => INTERACTIVE.includes(p.kind));
  const decorOn = r.pieces.some((p) => p.kind === "decor" && p.items.some((i) => i.on));
  // 위젯·부품 작품: 그날 근무 종류·오늘·공휴일·들어온/나간 돈 같은 상태 줄도 그날 데이터로 바뀌어요(위젯 부품은 수식을 아직 계산하지 않아요).
  const stateOn = r.pieces.some((p) => p.kind === "parts" && p.parts.some((l) => /^(role\.|today$|holiday$|in$|out$|sel$)/.test(l.on ?? "")));
  if (!hasWhen && !hasRule && !decorOn && !stateOn) out.push("수식·조건·규칙이 없어요");
  if ((r.summary ?? "").length > 60) out.push("한 줄 소개가 길어요");
  return out;
}
