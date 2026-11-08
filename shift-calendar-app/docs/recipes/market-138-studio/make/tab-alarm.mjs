// 1.38 [STM-08] 기능별 · 알람 25. 근무 알람 설정 + 알람 화면(밤낮 배경을 어둡게). 근무 종류마다 몇 분 전에 울릴지가 작품마다 달라요.
import { alarmWork as a } from "./tabs-build.mjs";

const SHIFT = ["SHIFT"], DAY = ["DAY"], HOURLY = ["HOURLY"], ROSTER = ["SHIFT", "DAY", "HOURLY"];
const DAYW = { day: "출근", other: "재택", long: "당직" };
const HW = { day: "오픈", eve: "마감", night: "야간" };

export default [
  // ---- 교대 ----
  a({ top: true, title: "오로라 기상", styles: SHIFT, bg: "n01", font: "gowundodum", emoji: "🌌", summary: "야간은 세 시간·한 시간 전 두 번, 소리는 점점 크게",
    lead: "야간 전 낮잠에서 깰 시간과 출발 시간을 따로 챙겨요.", byRole: [["day", 90, 60], ["eve", 90], ["night", 180, 60]], gradual: true }),
  a({ title: "새벽 주간 두 번", styles: SHIFT, bg: "n09", font: "nunito", emoji: "🌠", summary: "주간은 두 번, 덧셈 문제를 풀어야 꺼져요",
    lead: "새벽 주간에 다시 잠드는 일을 막아요.", byRole: [["day", 100, 70], ["eve", 90], ["night", 120]], mission: "math" }),
  a({ title: "야간 낮잠 알람", styles: SHIFT, bg: "n05", font: "nanummyeongjo", emoji: "🌙", summary: "야간 네 시간 전에 낮잠 깨우기",
    lead: "야간 전 낮잠을 자고 일어날 때를 맞춰요.", byRole: [["night", 240, 90]], gradual: true }),
  a({ title: "흔들어 깨우기", styles: SHIFT, bg: "c05", font: "rubik", emoji: "📳", summary: "휴대폰을 흔들어야 꺼지는 교대 알람",
    lead: "누운 채로 끄고 다시 잠드는 일을 막아요.", byRole: [["day", 80], ["eve", 80], ["night", 120]], mission: "shake" }),
  a({ title: "부드러운 별빛 기상", styles: SHIFT, bg: "n08", font: "gowundodum", emoji: "⭐", summary: "작게 시작해 점점 커지는 소리, 다시 울림 10분",
    byRole: [["day", 90], ["eve", 60], ["night", 150]], gradual: true, snooze: 10 }),
  a({ title: "병동 교대 알람", styles: SHIFT, bg: "l10", font: "gowundodum", emoji: "🩺", summary: "인수인계 시간까지 넉넉하게, 근무마다 두 번",
    byRole: [["day", 100, 60], ["eve", 90, 50], ["night", 150, 70]], gradual: true }),
  a({ title: "공장 3교대 알람", styles: SHIFT, bg: "c06", font: "notosanskr", emoji: "🏭", summary: "통근 버스 시간에 맞춘 근무마다 두 번",
    byRole: [["day", 80, 50], ["eve", 80, 50], ["night", 100, 60]], ring: 5 }),
  a({ title: "밤바다 기상", styles: SHIFT, bg: "w03", font: "nunito", emoji: "🌊", summary: "파도처럼 작게 시작하는 소리, 야간은 두 번",
    byRole: [["day", 90], ["eve", 70], ["night", 180, 90]], gradual: true }),
  a({ title: "깊은 잠 깨우기", styles: SHIFT, bg: "g06", font: "ibmplexsanskr", emoji: "😪", summary: "덧셈 문제와 5분 울림, 깊이 잠드는 분께",
    lead: "깊이 잠들어 알람을 놓치는 분께 맞췄어요.", byRole: [["day", 90, 60], ["eve", 90], ["night", 150, 90]], mission: "math", ring: 5 }),

  // ---- 일근 ----
  a({ top: true, title: "민트 출근 알람", styles: DAY, bg: "g03", font: "gowundodum", emoji: "🌿", summary: "출근 80·50분 전, 당직은 두 시간 전",
    roleWord: DAYW, byRole: [["day", 80, 50], ["long", 120]], gradual: true }),
  a({ title: "재택 여유 알람", styles: DAY, bg: "m03", font: "nunito", emoji: "🏠", summary: "재택 날은 30분 전 한 번, 출근은 두 번",
    roleWord: DAYW, byRole: [["day", 80, 50], ["other", 30]], gradual: true }),
  a({ title: "당직 대비 알람", styles: DAY, bg: "m10", font: "gowunbatang", emoji: "🌙", summary: "당직 세 시간·한 시간 전, 흔들어 끄기",
    roleWord: DAYW, byRole: [["day", 70], ["long", 180, 60]], mission: "shake" }),
  a({ title: "새벽 도시 출근", styles: DAY, bg: "c07", font: "notosanskr", emoji: "🌆", summary: "먼 출근길에 맞춘 100·70분 전",
    roleWord: DAYW, byRole: [["day", 100, 70], ["long", 120]] }),
  a({ title: "덧셈 출근 알람", styles: DAY, bg: "p03", font: "nanumgothiccoding", emoji: "➕", summary: "덧셈을 풀어야 꺼지는 출근 알람",
    roleWord: DAYW, byRole: [["day", 70, 40], ["long", 100]], mission: "math" }),

  // ---- 알바 ----
  a({ top: true, title: "파스텔 오픈 알람", styles: HOURLY, bg: "b04", font: "jua", emoji: "🌅", summary: "오픈 한 시간·30분 전, 야간은 90분 전",
    roleWord: HW, byRole: [["day", 60, 30], ["eve", 60], ["night", 90, 40]] }),
  a({ title: "편의점 야간 알람", styles: HOURLY, bg: "b06", font: "rubik", emoji: "🏪", summary: "야간 두 시간·한 시간 전, 흔들어 끄기",
    roleWord: HW, byRole: [["day", 60], ["eve", 60], ["night", 120, 60]], mission: "shake" }),
  a({ title: "카페 오픈 알람", styles: HOURLY, bg: "x06", font: "jua", emoji: "☕", summary: "오픈 날만 두 번, 마감 날은 한 번",
    roleWord: HW, byRole: [["day", 70, 40], ["eve", 60]], gradual: true }),
  a({ title: "학생 알바 알람", styles: HOURLY, bg: "g04", font: "fredoka", emoji: "🎒", summary: "수업 끝나고 가는 마감 근무 90분 전",
    roleWord: HW, byRole: [["day", 60], ["eve", 90, 45]] }),
  a({ title: "배달 출발 알람", styles: HOURLY, bg: "c03", font: "rubik", emoji: "🛵", summary: "근무 30분 전 짧게, 다시 울림 3분",
    roleWord: HW, byRole: [["day", 40], ["eve", 40], ["night", 50]], snooze: 3, ring: 2 }),

  // ---- 함께 쓰는 ----
  a({ title: "벚꽃 기상", styles: ROSTER, bg: "s01", font: "gowundodum", emoji: "🌸", summary: "작게 시작하는 부드러운 기상, 근무마다 한 번",
    byRole: [["day", 80], ["eve", 70], ["night", 120], ["long", 120]], gradual: true }),
  a({ title: "눈 오는 아침", styles: ROSTER, bg: "s07", font: "quicksand", emoji: "☃️", summary: "겨울 아침엔 10분 더 일찍",
    lead: "눈 오는 날 길이 막힐 때를 생각해 넉넉히 울려요.", byRole: [["day", 100, 70], ["eve", 90], ["night", 130], ["long", 130]] }),
  a({ title: "숲속 기상", styles: ROSTER, bg: "m07", font: "gowunbatang", emoji: "🌲", summary: "안개 숲처럼 조용히 시작하는 소리",
    byRole: [["day", 75], ["eve", 75], ["night", 110], ["long", 110]], gradual: true, vibrate: false }),
  a({ title: "도시 불빛 기상", styles: ROSTER, bg: "b03", font: "ibmplexsanskr", emoji: "🌃", summary: "근무마다 두 번, 다시 울림 5분",
    byRole: [["day", 90, 60], ["eve", 90, 60], ["night", 120, 60], ["long", 120, 60]] }),
  a({ title: "아침 해 기상", styles: ROSTER, bg: "m01", font: "jua", emoji: "🌄", summary: "분홍 노을 언덕 화면에 덧셈 끄기",
    byRole: [["day", 80], ["eve", 70], ["night", 120], ["long", 120]], mission: "math", gradual: true }),
  a({ title: "길게 울리는 알람", styles: ROSTER, bg: "g05", font: "notosanskr", emoji: "🔠", summary: "근무마다 두 번, 한 번에 5분 길게 울려요",
    byRole: [["day", 80, 50], ["eve", 80, 50], ["night", 120, 60], ["long", 120, 60]], ring: 5 }),
];
