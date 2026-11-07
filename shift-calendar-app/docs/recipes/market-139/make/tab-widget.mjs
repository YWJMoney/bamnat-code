// 1.39 [MKW-03] 탭별 › 위젯 50. 달력 50과 같은 미술 방향(skins.mjs) 한 세트씩, 홈 화면 위젯 모양 하나가 밤낮 위젯 모두(오늘·가계부·디데이·
// 단추·주·달)를 함께 꾸며요(크기는 장터 카드가 적어요). 값 칸·단추 위젯(1.39 FX)을 쓰는 프로 위젯은 FX 갈래가 합쳐진 뒤 따로 만들어요.
import { c } from "../../market-136/make/lib.mjs";
import { widgetPic } from "../../market-138-studio/make/mat.mjs";
import { tabWork, skin } from "./skins.mjs";

// 홈 화면 위젯은 앱 화면처럼 그릴 수 없어서(런처 위젯), 위젯이 실제로 그리는 것만 써요: 위젯 바탕색·투명도·모서리, 머리·칸의 글자색·굵기·
// 칸 색(근무 종류·오늘·공휴일·토·일), 밤낮 배경 그림(photoLook widget), 그림으로 한 번 그려 까는 꾸밈 층(모양·아이콘·글씨·재료 그림).
// 그라데이션·무늬·테두리는 꾸밈 층의 모양과 밤낮 배경 그림으로 만들어요.
import { use } from "../../market-138-studio/make/mat.mjs";

const W = (o) => ({ at: "widget", ...o });
/** 꾸밈 층 디자인 조각 (위젯 한 장을 1000×1000으로 보고 놓아요). */
const L = {
  frame: (col, w = 2, r = 22) => [W({ shape: "frame", x: 500, y: 500, w: 100, h: 100, full: true, line: "solid", lineW: w, lineC: c(col), r })],
  double: (col, r = 22) => [W({ shape: "frame", x: 500, y: 500, w: 100, h: 100, full: true, line: "double", lineW: 3, lineC: c(col), r })],
  dashed: (col, r = 4) => [W({ shape: "frame", x: 500, y: 500, w: 100, h: 100, full: true, line: "dash", lineW: 1.5, lineC: c(col), r })],
  band: (col, a = 100, y = 20, h = 6) => [W({ shape: "rect", x: 500, y, w: 400, h, fill: c(col), fillA: a })],
  glow: (a, b, alpha = 35) => [W({ shape: "blob1", x: 860, y: 160, w: 150, h: 120, fill: c(a), fillA: alpha }), W({ shape: "blob2", x: 120, y: 880, w: 170, h: 120, fill: c(b), fillA: alpha })],
  radar: (col) => [W({ shape: "ring", x: 880, y: 300, w: 90, h: 90, lineW: 1, lineC: c(col), alpha: 45 }), W({ shape: "ring", x: 880, y: 300, w: 55, h: 55, lineW: 1, lineC: c(col), alpha: 45 }),
    W({ shape: "ring", x: 880, y: 300, w: 20, h: 20, lineW: 1, lineC: c(col), alpha: 60 })],
  stars: (col) => [[820, 120, 10], [930, 260, 7], [700, 70, 6], [960, 80, 5]].map(([x, y, w]) => W({ shape: "star4", x, y, w, h: w, fill: c(col), fillA: 80 })),
  tape: (col) => [W({ shape: "tape", x: 120, y: 40, w: 46, h: 14, rot: 345, fill: c(col), fillA: 70 }), W({ shape: "tape", x: 880, y: 40, w: 46, h: 14, rot: 15, fill: c(col), fillA: 70 })],
  dots: (col) => [[80, 940], [140, 940], [200, 940]].map(([x, y]) => W({ shape: "circle", x, y, w: 6, h: 6, fill: c(col), fillA: 70 })),
  word: (text, col, font, x = 860, y = 90, size = 9) => [W({ text, x, y, w: 90, h: 16, size, weight: 800, ink: c(col), ...(font ? { font } : {}) })],
  wave: (col) => [W({ shape: "wave", x: 500, y: 960, w: 400, h: 22, fill: c(col), fillA: 30 })],
};

const ROLE_SAY = "근무 종류마다 칸 색이 달라 이번 주가 한눈에 보여요.";
const S = (day, eve, night, off) => ({ day, eve, night, off });
const D = (day, other, long, off) => ({ day, other, long, off });

/** One widget work: s.from (달력 세트), s.bg 밤낮 배경 그림, s.fill 위젯 바탕색, s.roles {role: 색}, s.icons [재료 아이콘], s.deco [꾸밈 조각], s.look (위젯 조각 덧쓰기). */
function widget(s) {
  const k = skin(s.from);
  const dark = s.p?.dark ?? k.p.dark;
  const roles = Object.entries(s.roles ?? {}).map(([role, col]) => ({ id: "widget.cell", on: `role.${role}`, fill: c(col), fillA: s.roleA ?? (dark ? 45 : 35) }));
  const decor = [
    ...(s.deco ?? []).flat(),
    ...(s.icons ?? []).map((key, i) => widgetPic(key, i === 0 ? { x: 905, y: 140, w: s.iconW ?? 22, h: s.iconW ?? 22 } : { x: 905 - i * 70, y: 140, w: 18, h: 18 })),
  ];
  const says = [s.lead, s.bg ? "위젯 바탕에 밤낮이 그린 배경이 깔려요." : null, s.roles ? ROLE_SAY : null,
    s.icons?.length ? "밤낮이 직접 그린 색 아이콘이 위젯 구석에 떠요." : null, "오늘 칸은 포인트 색, 공휴일 날짜엔 밑줄이 생겨요."];
  return tabWork("widget", {
    ...s, wow: says.filter(Boolean).join(" "), changes: [...(s.changes ?? []), ...(s.roles ? ["근무 종류마다 칸 색"] : []), "오늘 칸 포인트 색", "공휴일 밑줄"],
    parts: [
      { id: "widget", ...(s.fill ? { fill: c(s.fill) } : {}), ...(s.fillA !== undefined ? { fillA: s.fillA } : {}), pad: 10, ...(s.r !== undefined ? { r: s.r } : {}) },
      ...(s.head ? [{ id: "widget.head", ...s.head }] : []),
      { id: "widget.cell", r: s.cellR ?? 8 },
      { id: "widget.cell", on: "dim", alpha: 40 },
      { id: "widget.cell", on: "holiday", deco: "under", weight: 800 },
      ...roles, ...(s.parts ?? []),
    ],
    ...(decor.length ? { decor } : {}),
    widget: { memo: true, ...(s.fill ? { bg: c(s.fill) } : {}), ...(s.look ?? {}) },
    tabExtra: s.bg ? [{ kind: "photoLook", widget: use(s.bg) }] : [],
  });
}


export default [
  // ---------- 교대 12 ----------
  widget({ from: "야간 레이더 달력", title: "야간 레이더 위젯", top: true, summary: "관제 화면 같은 검은 위젯, 레이더 고리와 초록 야간 칸",
    lead: "위젯이 관제실 모니터처럼 검고 구석에서 초록 레이더 고리가 퍼져요.", changes: ["관제 모니터 위젯", "레이더 고리"],
    fill: "#06100D", head: { ink: c("#35F08F"), weight: 900 }, deco: [L.radar("#35F08F"), L.frame("#35F08F", 1, 18)], roles: S("#3DDC97", "#FFB74D", "#35F08F", "#3A4A44") }),
  widget({ from: "수간호사 근무표", title: "병동 근무 위젯", summary: "위쪽 청록 띠, 근무마다 칸 색, 청진기 아이콘", lead: "위젯 위쪽에 청록 띠가 지나가는 차트 같은 위젯이에요.",
    changes: ["청록 띠"], fill: "#FFFFFF", head: { ink: c("#1D7A7A") }, deco: [L.band("#1D7A7A", 100, 12, 8)], roles: S("#5CC2B4", "#F4A261", "#3D5A80", "#D5E0E0"), icons: ["stethoscope"] }),
  widget({ from: "달빛 띠 근무 달력", title: "달빛 밤하늘 위젯", summary: "초승달 밤 그림 위 위젯, 별 반짝임", lead: "위젯 바탕에 초승달 뜬 밤하늘이 깔리고 구석에 작은 별이 떠요.",
    changes: ["초승달 밤 바탕", "작은 별"], bg: "n05", deco: [L.stars("#FFF3C4")], roles: S("#FFE082", "#FFAB91", "#9FB4FF", "#4A5578"), icons: ["crescent"] }),
  widget({ from: "타임라인 교대표", title: "파랑 막대 위젯", summary: "흰 바탕에 각진 파랑 막대 칸", lead: "칸이 각진 막대처럼 서서 시간표를 보는 듯해요.",
    changes: ["각진 막대 칸"], fill: "#FFFFFF", r: 12, cellR: 2, deco: [L.band("#3D5AFE", 100, 975, 4)], roles: S("#3D5AFE", "#FF6D00", "#1A237E", "#E0E4F0"), roleA: 30 }),
  widget({ from: "새벽 4시 하늘", title: "새벽빛 위젯", summary: "새벽 오로라 그림 위 위젯", lead: "위젯 바탕이 새벽 오로라 그림으로 물들어요.",
    changes: ["새벽 오로라 바탕"], bg: "n03", roles: S("#FFD3A5", "#F7A8C0", "#8E7CF0", "#E8E4F4"), icons: ["sunrise"] }),
  widget({ from: "지하철 환승 달력", title: "노선도 위젯", summary: "노선 색 칸과 아래 노선 띠 세 줄", lead: "근무 칸이 지하철 노선 색으로 칠해지고 아래로 노선 띠가 지나가요.",
    changes: ["노선 색 칸", "노선 띠"], fill: "#FFFFFF", cellR: 12, deco: [L.band("#00A84D", 100, 955, 3), L.band("#EF7C1C", 100, 970, 3), L.band("#996CAC", 100, 985, 3)],
    roles: S("#00A84D", "#EF7C1C", "#996CAC", "#D0D0D0"), roleA: 55, icons: ["train"] }),
  widget({ from: "오로라 유리 칸", title: "오로라 유리 위젯", top: true, summary: "초록 오로라 그림 위 반투명 위젯", lead: "위젯 바탕에 초록 오로라가 깔리고 구석에 별빛이 떠요.",
    changes: ["오로라 바탕"], bg: "n01", deco: [L.stars("#E0FFF4")], roles: S("#5EF2C2", "#FFB86B", "#B28CFF", "#2A3350"), icons: ["sparkles"] }),
  widget({ from: "소방서 출동판", title: "출동판 위젯", summary: "빨강 테두리 위젯, 당번 칸 빨강", lead: "위젯 둘레에 빨강 테두리가 둘러지고 위쪽엔 빨강 띠가 지나가요.",
    changes: ["빨강 테두리", "빨강 띠"], fill: "#FFFFFF", deco: [L.frame("#D7261E", 3, 20), L.band("#D7261E", 100, 15, 6)], roles: S("#D7261E", "#FFC400", "#1B1B1B", "#E6E6E6"), icons: ["firetruck"] }),
  widget({ from: "4조2교대 큰 칸", title: "큰 글자 근무 위젯", summary: "글자가 크고 칸이 넉넉한, 한눈에 읽는 위젯", lead: "위젯 글자가 크게 놓여 멀리서도 읽혀요.",
    changes: ["큰 글자"], fill: "#FFFFFF", look: { text: "l" }, head: { size: 16, weight: 900 }, roles: S("#5C8DF6", "#F6A05C", "#1B3A8C", "#E3E7EF") }),
  widget({ from: "수면 빚 달력", title: "수면 빚 위젯", summary: "한겨울 밤 그림 위 차분한 위젯, 잠 아이콘", lead: "위젯 바탕에 고요한 한겨울 밤이 깔려요.",
    changes: ["한겨울 밤 바탕"], bg: "n10", roles: S("#A7C4E0", "#C9B79C", "#2F4A66", "#E4E9EE"), icons: ["zzz"] }),
  widget({ from: "체크무늬 간호 수첩", title: "분홍 체크 위젯", summary: "말랑 체크 그림 바탕, 위쪽 마스킹 테이프", lead: "위젯 바탕이 분홍 체크 천이고 위 모서리에 마스킹 테이프가 붙어요.",
    changes: ["체크 바탕", "마스킹 테이프"], bg: "x06", deco: [L.tape("#F7A8C0")], roles: S("#FFB3C7", "#FFD6A5", "#B39DDB", "#F1E4EA"), icons: ["heart"] }),
  widget({ from: "항구 교대 해도", title: "밤바다 해도 위젯", summary: "밤바다 물결 그림, 배 아이콘", lead: "위젯 바탕에 밤바다 물결이 깔려요.",
    changes: ["밤바다 바탕"], bg: "w03", roles: S("#7FB3D5", "#E5B567", "#2F5D7C", "#E3E9EE"), icons: ["ship"] }),

  // ---------- 일근 8 ----------
  widget({ from: "주간 플래너 펼침", title: "에디토리얼 위젯", top: true, summary: "큰 머리 글자와 붉은 가는 테", lead: "위젯 머리가 잡지 표지처럼 크고 둘레엔 붉은 가는 테가 둘러져요.",
    changes: ["큰 머리", "붉은 가는 테"], fill: "#FFFFFF", head: { size: 17, weight: 900 }, deco: [L.frame("#C0392B", 0.8, 6), L.word("WEEKLY", "#C0392B", "dmserif")], r: 6,
    roles: D("#F2D7D5", "#D6EAF8", "#C0392B", "#F4F4F4") }),
  widget({ from: "칼퇴 신호등", title: "신호등 위젯", summary: "출근 초록·당직 주황, 구석에 신호등 세 점", lead: "근무 칸이 신호등 색으로 놓이고 구석에 신호등 불빛 세 점이 켜져요.",
    changes: ["신호등 칸", "신호등 점"], fill: "#FFFFFF", deco: [[W({ shape: "circle", x: 800, y: 110, w: 8, h: 8, fill: c("#E53935") }), W({ shape: "circle", x: 860, y: 110, w: 8, h: 8, fill: c("#F29900") }),
      W({ shape: "circle", x: 920, y: 110, w: 8, h: 8, fill: c("#2E7D32") })]], roles: D("#2E7D32", "#90CAF9", "#F29900", "#ECEFF1"), roleA: 40 }),
  widget({ from: "재택·출근 반반", title: "출근·재택 반반 위젯", summary: "하늘·살구 두 빛 번짐", lead: "위젯 두 모서리에 하늘빛과 살구빛이 번져요.",
    changes: ["두 빛 번짐"], fill: "#FFFDFB", deco: [L.glow("#BFE0FF", "#FFD3B8", 60)], roles: D("#4A90D9", "#F08A5D", "#8E6CC0", "#EEEAE6"), icons: ["house"] }),
  widget({ from: "분기 목표 연간판", title: "숲색 목표 위젯", summary: "민트 아침 산 그림, 숲색 머리", lead: "위젯 바탕에 민트빛 아침 산이 깔려요.",
    changes: ["아침 산 바탕"], bg: "m03", head: { ink: c("#1E5236") }, roles: D("#A5D6A7", "#B3E5FC", "#2E7D4F", "#EEF3EF"), icons: ["sprout"] }),
  widget({ from: "사원증 스트랩", title: "스트랩 위젯", summary: "위쪽 남색 스트랩 띠 두 줄", lead: "위젯 위쪽에 사원증 스트랩 같은 남색 띠 두 줄이 지나가요.",
    changes: ["스트랩 띠"], fill: "#F4F6FB", deco: [L.band("#24408E", 100, 14, 5), L.band("#E9A23B", 100, 30, 2)], roles: D("#24408E", "#7FB3D5", "#E9A23B", "#E6E9F2"), icons: ["badge"] }),
  widget({ from: "점심 메뉴 달력", title: "점심 도시락 위젯", summary: "살구빛 동글 위젯, 밥그릇 아이콘", lead: "위젯이 도시락 뚜껑처럼 동그랗고 따뜻해요.",
    changes: ["동글 위젯"], fill: "#FFF4E6", r: 28, cellR: 14, deco: [L.dots("#E9A23B")], roles: D("#FFD08A", "#B8E0D2", "#F4A3A3", "#F5EFE6"), icons: ["ricebowl"] }),
  widget({ from: "흑백 미니멀 일정", title: "미니멀 위젯", summary: "흰 바탕 검은 글자, 가는 테 하나", lead: "색을 덜어 내고 글자와 가는 테만 남겼어요.",
    changes: ["흑백 위젯"], fill: "#FFFFFF", r: 4, cellR: 0, deco: [L.frame("#111111", 0.8, 4)], parts: [{ id: "widget.cell", on: "today", fill: c("#111111"), ink: c("#FFFFFF") }] }),
  widget({ from: "회의실 예약판", title: "회의실 예약 위젯", summary: "단정한 파랑 머리, 출근·재택·당직 칸 색", lead: "위젯이 회의실 예약판처럼 단정한 파랑 머리로 정리돼요.",
    changes: ["파랑 머리"], fill: "#FFFFFF", head: { ink: c("#2B6CB0") }, cellR: 4, deco: [L.band("#2B6CB0", 100, 975, 4)], roles: D("#90CDF4", "#C6F6D5", "#2B6CB0", "#EDF2F7"), icons: ["calendar"] }),

  // ---------- 알바 6 ----------
  widget({ from: "편의점 영수증 달력", title: "영수증 위젯", top: true, summary: "감열지 영수증처럼 점선 테, 영수증 아이콘", lead: "위젯이 영수증 한 장처럼 점선 테로 끊기고 구석에 작은 바코드 줄이 서요.",
    changes: ["영수증 점선 테"], fill: "#FFFFFF", r: 2, cellR: 2, deco: [L.dashed("#9E9E9E", 2), [60, 90, 110, 150, 170, 200].map((x, i) => W({ shape: "line_v", x: 800 + x * 0.6, y: 930, w: 2, h: 14, lineW: i % 2 ? 1 : 2, lineC: c("#424242") }))],
    roles: S("#B9F6CA", "#FFE0B2", "#C5CAE9", "#F5F5F5"), icons: ["receipt"] }),
  widget({ from: "버블티 알바 달력", title: "버블티 위젯", summary: "밀크티 색 동글 위젯, 바닥에 펄 세 알", lead: "위젯이 밀크티 빛으로 동그랗고 바닥에 펄이 굴러요.",
    changes: ["밀크티 위젯", "펄 세 알"], fill: "#F6EBDD", r: 28, cellR: 14, deco: [L.dots("#5D4037")], roles: S("#E3B98F", "#F7C8A0", "#8D6E63", "#F3EAE2"), icons: ["bubbletea"] }),
  widget({ from: "투잡 두 색 달력", title: "투잡 두 색 위젯", summary: "파랑·주황 두 일이 칸 색과 위쪽 반반 띠로", lead: "두 일의 근무가 파랑과 주황 칸으로 갈리고 위쪽 띠도 반반이에요.",
    changes: ["두 색 칸", "반반 띠"], fill: "#FFFFFF", deco: [[W({ shape: "rect", x: 250, y: 15, w: 200, h: 6, fill: c("#2962FF") }), W({ shape: "rect", x: 750, y: 15, w: 200, h: 6, fill: c("#FF6D00") })]],
    roles: S("#2962FF", "#FF6D00", "#311B92", "#E8EAF6"), roleA: 45 }),
  widget({ from: "픽셀 게임 출석부", title: "8비트 위젯", summary: "검은 픽셀 화면, 네모 칸, 초록 테", lead: "위젯이 8비트 게임 화면처럼 각지고 칸은 픽셀 네모예요.",
    changes: ["픽셀 위젯"], fill: "#1B1E3C", r: 0, cellR: 0, deco: [L.frame("#5BFF7A", 2, 0), L.word("1UP", "#5BFF7A", "pressstart", 870, 90, 7)], roles: S("#5BFF7A", "#FFD54F", "#7C4DFF", "#2A2C55"), icons: ["gamepad"] }),
  widget({ from: "주말 알바 도장판", title: "도장판 위젯", summary: "분홍 동그란 도장 칸", lead: "위젯 칸이 도장판처럼 동그래요.",
    changes: ["동그란 칸"], fill: "#FFF6F9", cellR: 16, deco: [L.tape("#E85A8A")], roles: S("#E85A8A", "#FFB3C7", "#9575CD", "#FBEFF3"), roleA: 45, icons: ["star"] }),
  widget({ from: "새벽 배송 루트", title: "밤길 내비 위젯", summary: "검은 바탕 형광 연두 머리, 길 띠", lead: "위젯이 밤길 내비처럼 검고 아래로 형광 길 띠가 지나가요.",
    changes: ["밤길 위젯", "형광 길 띠"], fill: "#111418", head: { ink: c("#D4FF3A") }, deco: [L.band("#D4FF3A", 100, 980, 3)], roles: S("#D4FF3A", "#5AD1FF", "#FF7A59", "#2A2E36"), icons: ["truck"] }),

  // ---------- 현장 5 ----------
  widget({ from: "공사 일보 칠판", title: "칠판 위젯", top: true, summary: "칠판 모눈 그림에 나무 테", lead: "위젯이 현장 칠판이 되고 둘레엔 나무 테가 둘러져요.",
    changes: ["칠판 바탕", "나무 테"], bg: "p08", deco: [L.frame("#8A5A2B", 4, 10)], parts: [{ id: "widget.cell", on: "today", fill: c("#F5E27A"), ink: c("#1E2B22") }], icons: ["hardhat"] }),
  widget({ from: "철근 도면 달력", title: "청사진 위젯", summary: "청사진 파랑 바탕에 흰 가는 테와 치수선", lead: "위젯이 청사진 도면처럼 파랑 위에 흰 선으로 그려져요.",
    changes: ["청사진 바탕", "흰 치수선"], fill: "#1F4E8C", r: 4, cellR: 0, deco: [L.frame("#FFFFFF", 1, 2), [W({ shape: "line_h", x: 500, y: 960, w: 300, h: 4, lineW: 1, lineC: c("#FFFFFF"), alpha: 60 })]], icons: ["wrench"] }),
  widget({ from: "안전 제일 표지판", title: "안전 표지 위젯", summary: "노랑 테두리와 검정 띠", lead: "위젯 둘레가 안전 표지처럼 노랑 굵은 테예요.",
    changes: ["노랑 굵은 테"], fill: "#FFFFFF", deco: [L.frame("#FFD100", 5, 16), L.band("#141414", 100, 980, 4)], head: { ink: c("#141414"), weight: 900 }, icons: ["hardhat"] }),
  widget({ from: "일당 봉투 달력", title: "누런 봉투 위젯", summary: "크래프트지 그림 바탕", lead: "위젯이 일당 봉투처럼 누런 크래프트지예요.",
    changes: ["크래프트지 바탕"], bg: "p05", head: { ink: c("#5D3A1A") }, icons: ["banknote"] }),
  widget({ from: "해 뜨는 비계", title: "해돋이 위젯", summary: "주황 해넘이 산 그림 위 위젯", lead: "위젯 바탕에 해 뜨는 산 능선이 깔려요.",
    changes: ["해돋이 산 바탕"], bg: "m04", icons: ["sunrise"] }),

  // ---------- 자영업 5 ----------
  widget({ from: "매출 온도계 달력", title: "매출 온도계 위젯", top: true, summary: "구석에 온도계 막대, 빨강 머리", lead: "위젯 머리가 온도계 빨강이고 구석에 온도계 막대가 서요.",
    changes: ["온도계 머리", "온도계 막대"], fill: "#FFFFFF", head: { ink: c("#E8483B") },
    deco: [[W({ shape: "pill", x: 950, y: 600, w: 8, h: 60, fill: c("#F3D6D3") }), W({ shape: "pill", x: 950, y: 680, w: 8, h: 34, fill: c("#E8483B") }), W({ shape: "circle", x: 950, y: 850, w: 14, h: 14, fill: c("#E8483B") })]],
    icons: ["chartup"] }),
  widget({ from: "꽃집 계절 달력", title: "꽃잎 위젯", summary: "벚꽃 바람 그림 위 위젯", lead: "위젯 바탕에 벚꽃 잎이 흩날려요.",
    changes: ["벚꽃 바탕"], bg: "s01", r: 24, icons: ["tulip", "blossom"] }),
  widget({ from: "오마카세 예약 장부", title: "먹색 금박 위젯", summary: "먹색 바탕에 금박 이중 테", lead: "위젯이 먹색 카운터처럼 깊고 둘레엔 금박 이중 테가 둘러져요.",
    changes: ["먹색 바탕", "금박 이중 테"], fill: "#141210", head: { ink: c("#D4AF63") }, deco: [L.double("#D4AF63", 14)], icons: ["sushi"] }),
  widget({ from: "장날 도장 달력", title: "장날 위젯", summary: "한지 그림 바탕, 붉은 도장 머리", lead: "위젯이 한지 위에 놓이고 머리는 붉은 도장 색이에요.",
    changes: ["한지 바탕"], bg: "p07", head: { ink: c("#A8322D"), weight: 900 }, deco: [[W({ shape: "seal", x: 900, y: 860, w: 26, h: 26, fill: c("#A8322D"), fillA: 80 })]], icons: ["store"] }),
  widget({ from: "1인 카페 운영표", title: "원두 위젯", summary: "크림색 바탕 원두색 머리, 커피 아이콘", lead: "위젯이 크림색 위에 원두 빛 머리로 차분해요.",
    changes: ["원두색 머리"], fill: "#FBF6EF", head: { ink: c("#6F4E37") }, deco: [L.band("#6F4E37", 100, 975, 4)], icons: ["coffee"] }),

  // ---------- 투자 4 ----------
  widget({ from: "캔들 차트 달력", title: "캔들 차트 위젯", top: true, summary: "검은 차트 화면, 구석에 초록·빨강 캔들", lead: "위젯이 차트 화면처럼 검고 구석에 초록·빨강 캔들이 서요.",
    changes: ["차트 위젯", "캔들 꾸밈"], fill: "#0B0F14", cellR: 2,
    deco: [[[840, 22, "#22C55E"], [880, 34, "#EF4444"], [920, 28, "#22C55E"], [960, 40, "#22C55E"]].map(([x, h, col]) => W({ shape: "rect", x, y: 880, w: 6, h, fill: c(col) }))], icons: ["chartup"] }),
  widget({ from: "적립식 씨앗 달력", title: "새싹 위젯", summary: "초록 잎 그림 위 위젯", lead: "위젯 바탕에 초록 잎이 깔려요.",
    changes: ["초록 잎 바탕"], bg: "s05", icons: ["sprout"] }),
  widget({ from: "배당 금화 달력", title: "금화 위젯", summary: "금빛 이중 테 세리프 위젯", lead: "위젯 둘레가 금화처럼 금빛 이중 테예요.",
    changes: ["금빛 이중 테"], fill: "#FFFDF5", deco: [L.double("#B8860B", 18)], head: { ink: c("#8A6408") }, icons: ["coins"] }),
  widget({ from: "매매 일지 모눈", title: "모눈 위젯", summary: "모눈 공책 그림 바탕", lead: "위젯 바탕이 모눈 공책이에요.",
    changes: ["모눈 바탕"], bg: "p03", cellR: 0, icons: ["pencil"] }),

  // ---------- 모든 유형 10 ----------
  widget({ from: "비 오는 날 유리창", title: "빗방울 위젯", summary: "밤 창 물방울 그림 위 위젯", lead: "위젯이 비 오는 밤 창처럼 물방울이 맺혀요.",
    changes: ["밤 창 물방울 바탕"], bg: "l10", icons: ["rain"] }),
  widget({ from: "봄 햇살 테라조", title: "테라조 위젯", summary: "테라조 그림 바탕", lead: "위젯 바탕이 봄빛 테라조 돌 무늬예요.",
    changes: ["테라조 바탕"], bg: "x02", icons: ["blossom"] }),
  widget({ from: "뉴욕 신문 달력", title: "신문 위젯", summary: "신문 제호 같은 굵은 머리와 이중 줄", lead: "위젯 머리가 신문 제호처럼 굵고 아래로 이중 줄이 지나가요.",
    changes: ["신문 제호", "이중 줄"], fill: "#FBFAF6", r: 0, cellR: 0, head: { size: 17, weight: 900 },
    deco: [L.band("#111111", 100, 960, 2), L.band("#111111", 100, 975, 2), L.word("DAILY", "#111111", "abrilfatface")] }),
  widget({ from: "네온 사인 달력", title: "네온 위젯", top: true, summary: "네온 분홍 그림 바탕, 네온 테", lead: "위젯 바탕에 네온 분홍 빛이 번지고 둘레엔 네온 테가 둘러져요.",
    changes: ["네온 바탕", "네온 테"], bg: "b06", deco: [L.frame("#FF4FB5", 2, 20)], icons: ["sparkles"] }),
  widget({ from: "숲속 오두막 달력", title: "오두막 위젯", summary: "안개 숲 산 그림, 등잔 아이콘", lead: "위젯 바탕에 안개 낀 숲이 깔려요.",
    changes: ["안개 숲 바탕"], bg: "m07", icons: ["pine", "lamp"] }),
  widget({ from: "우주 정거장 달력", title: "우주 정거장 위젯", summary: "은하수 그림 위 위젯, 행성 아이콘", lead: "위젯 바탕이 은하수가 흐르는 우주예요.",
    changes: ["은하수 바탕"], bg: "n06", deco: [L.stars("#E9E1FF")], icons: ["planet"] }),
  widget({ from: "레트로 체크 피크닉", title: "피크닉 위젯", summary: "빨강 체크 그림 위, 딸기 아이콘", lead: "위젯 위에 피크닉 체크 천이 깔려요.",
    changes: ["체크 천 바탕"], bg: "x06", deco: [L.tape("#D7263D")], icons: ["strawberry"] }),
  widget({ from: "독서 기록 서가", title: "서가 위젯", summary: "오래된 종이 그림, 책 아이콘", lead: "위젯이 책 사이 오래된 종이처럼 차분해요.",
    changes: ["오래된 종이 바탕"], bg: "p10", head: { ink: c("#2F5D4A") }, icons: ["book"] }),
  widget({ from: "운동 출석 히트맵", title: "잔디 위젯", summary: "네모 칸, 위쪽 초록 잔디 줄", lead: "위젯 칸이 잔디 히트맵처럼 네모나고 위쪽에 잔디 줄이 서요.",
    changes: ["잔디 칸"], fill: "#FFFFFF", cellR: 3,
    deco: [[0.25, 0.5, 0.8, 0.35, 1, 0.6, 0.9].map((a, i) => W({ shape: "rect", x: 640 + i * 45, y: 90, w: 8, h: 8, r: 2, fill: c("#1F9D55"), fillA: Math.round(a * 100) }))], icons: ["dumbbell"] }),
  widget({ from: "낮잠 고양이 달력", title: "낮잠 고양이 동글 위젯", summary: "살구빛 동글 위젯, 고양이 아이콘", lead: "위젯이 낮잠 자는 고양이처럼 동글고 부드러워요.",
    changes: ["동글 위젯"], fill: "#FFF3EA", r: 28, cellR: 14, deco: [L.glow("#FFD8C2", "#FFE9D6", 70)], icons: ["cat"] }),
];
