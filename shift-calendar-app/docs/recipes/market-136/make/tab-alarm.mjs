// 기능별 · 알람 22 (기획서 4-6). 알람 탭 하나만 바꿔요: 근무 종류마다 몇 분 전에 울릴지(두 번까지), 다시 울림·울리는 시간·점점 크게·진동·끄는 문제, 알람 화면 색.
// 알람 조각은 교대·일근·알바에만 맞아요(서버 RECIPE_PIECES). 근무 끝 알림·물 마시기 같은 '규칙 알림'은 규칙·단추 구간(logic.mjs)에 있어요:
// 규칙을 같이 실으면 앱 전체를 바꾸는 작품(테마)이 되어 이 구간에 들어오지 않아요.
import { H, recipe } from "./lib.mjs";

const NOTE = "근무 알람 설정과 알람 화면 색만 바뀌어요. 이미 켜 둔 근무 알람은 새 설정으로 다시 맞춰져요";
const MISSION = { none: "", math: "끌 때 간단한 덧셈 문제", shake: "끌 때 휴대폰 흔들기" };

export function alarm(s) {
  const p = { bg: "#101828", card: "#1D2939", ink: "#F2F4F7", accent: "#7F56D9", ...s.p };
  const screens = `alarm~bg:${H(p.bg)}|cd:${H(p.card)}|ik:${H(p.ink)}|ac:${H(p.accent)}${p.bgPaint ? `|fl:${p.bgPaint}` : ""}${p.font ? `|ft:${p.font}` : ""}`;
  const a = { kind: "alarms", byRole: s.byRole.map(([role, ...before]) => ({ role, before })), snooze: s.snooze ?? 5, ring: s.ring ?? 3,
    ...(s.gradual ? { gradual: true } : {}), ...(s.vibrate === false ? {} : { vibrate: true }), mission: s.mission ?? "none" };
  const auto = [];
  if (s.gradual) auto.push("소리가 작게 시작해 점점 커져요");
  if (MISSION[a.mission]) auto.push(MISSION[a.mission]);
  auto.push(`다시 울림 ${a.snooze}분, 한 번에 ${a.ring}분 울려요`);
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: [...s.changes, ...auto], note: s.note ?? NOTE, styles: s.styles,
      cover: s.cover, pieces: [{ kind: "screenLook", screens }, a] }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

const SHIFT = ["SHIFT"], DAY = ["DAY"], HOURLY = ["HOURLY"], ROSTER = ["SHIFT", "DAY", "HOURLY"];

export default [
  alarm({
    title: "3교대 두 번 알람", styles: SHIFT, summary: "주간·저녁·야간마다 90분 전과 30분 전, 두 번씩 깨워요",
    wow: "근무 종류마다 알람이 두 번 울려요. 첫 알람에 일어나 씻고, 두 번째 알람에 집을 나서요. 야간은 낮잠을 깨우도록 두 시간 전에 한 번 더 울려요.",
    changes: ["주간 90·30분 전", "저녁 90·30분 전", "야간 120·45분 전", "밤하늘 알람 화면"], who: "근무 시각이 매주 바뀌는 3교대", cover: ["#1A237E", "#7986CB", "⏰"],
    byRole: [["day", 90, 30], ["eve", 90, 30], ["night", 120, 45]], showcase: true,
    p: { bgPaint: "lg.180.FF0D1B4C,FF1A237E", accent: "#7986CB" },
  }),
  alarm({
    title: "나이트 낮잠 깨우기", styles: SHIFT, summary: "야간 날은 세 시간 전 낮잠 깨우기, 한 시간 전 출발 알람",
    wow: "야간 근무 날 낮잠을 자고도 늦지 않게, 세 시간 전에 한 번 깨우고 한 시간 전에 나갈 준비를 알려요. 점점 커지는 소리라 깜짝 놀라지 않아요.",
    changes: ["야간 180·60분 전", "주간·저녁 60분 전", "부드러운 보라 화면"], who: "야간 전에 낮잠을 자는 분", cover: ["#311B92", "#B39DDB", "😴"],
    byRole: [["night", 180, 60], ["day", 60], ["eve", 60]], gradual: true, p: { bg: "#1B1035", card: "#2A1B4E", accent: "#B39DDB" },
  }),
  alarm({
    title: "새벽 출근 확실히", styles: SHIFT, summary: "주간(이른 출근) 날은 덧셈 문제를 풀어야 꺼져요",
    wow: "새벽 출근 날 무심코 끄고 다시 자는 일이 없도록, 알람을 끄려면 간단한 덧셈을 풀어야 해요. 다시 울림은 3분으로 짧게 잡았어요.",
    changes: ["주간 70·40분 전", "저녁·야간 60분 전", "새벽 주황 화면"], who: "아침잠이 많은 교대 근무자", cover: ["#FF8F00", "#FFE082", "🌅"],
    byRole: [["day", 70, 40], ["eve", 60], ["night", 60]], mission: "math", snooze: 3, p: { bg: "#2B1700", card: "#3E2400", ink: "#FFF3E0", accent: "#FFB300" },
  }),
  alarm({
    title: "점점 크게 부드러운 아침", styles: ROSTER, summary: "소리가 작게 시작해 커지고, 진동이 먼저 깨워요",
    wow: "잠을 갑자기 깨우지 않게 진동이 먼저, 소리는 작게 시작해 커져요. 근무 종류마다 한 시간 전에 한 번만 울리는 단정한 알람이에요.",
    changes: ["근무마다 60분 전", "하늘빛 알람 화면"], who: "큰 알람 소리가 싫은 분", cover: ["#B3E5FC", "#E1F5FE", "🌤️"],
    byRole: [["day", 60], ["eve", 60], ["night", 60], ["long", 60]], gradual: true, ring: 5,
    p: { bg: "#E1F5FE", card: "#FFFFFF", ink: "#01579B", accent: "#0288D1" },
  }),
  alarm({
    title: "흔들어서 끄기", styles: ROSTER, summary: "휴대폰을 흔들어야 꺼지는 알람, 근무 40분 전",
    wow: "누워서 손가락으로만 끄지 못하게, 휴대폰을 몇 번 흔들어야 알람이 꺼져요. 몸이 움직이면 잠이 깨요.",
    changes: ["근무마다 40분 전", "선명한 초록 화면"], who: "알람을 끄고 다시 잠드는 분", cover: ["#00C853", "#B9F6CA", "📳"],
    byRole: [["day", 40], ["eve", 40], ["night", 40], ["long", 40]], mission: "shake", p: { bg: "#002E14", card: "#00441F", ink: "#E8F5E9", accent: "#00E676" },
  }),
  alarm({
    title: "일근 출근 알람", styles: DAY, summary: "출근 날 80분·30분 전, 당직 날은 두 시간 전",
    wow: "출근 날은 씻을 때와 나설 때 두 번, 당직 날은 미리 준비하도록 두 시간 전에 한 번 더 울려요. 재택 날은 30분 전 한 번이면 돼요.",
    changes: ["출근 80·30분 전", "재택 30분 전", "당직 120·60분 전", "단정한 남색 화면"], who: "출근과 당직이 섞인 일근", cover: ["#1E3A8A", "#93C5FD", "💼"],
    byRole: [["day", 80, 30], ["other", 30], ["long", 120, 60]], showcase: true, p: { bg: "#0B1B3F", card: "#132A5E", accent: "#93C5FD" },
  }),
  alarm({
    title: "재택 날 늦잠 허락", styles: DAY, summary: "출근 날은 일찍, 재택 날은 15분 전에만 울려요",
    wow: "출근 날은 한 시간 반 전에 깨우고, 재택 날은 일 시작 15분 전에만 가볍게 알려요. 재택 날 아침 잠 30분이 생겨요.",
    changes: ["출근 90·40분 전", "재택 15분 전", "따뜻한 베이지 화면"], who: "재택과 출근이 섞인 분", cover: ["#FFF8E1", "#A1887F", "🏠"],
    byRole: [["day", 90, 40], ["other", 15]], gradual: true, p: { bg: "#FBF6EE", card: "#FFFFFF", ink: "#4E342E", accent: "#A1887F" },
  }),
  alarm({
    title: "당직 비상 알람", styles: DAY, summary: "당직 날은 진동 먼저, 덧셈 문제로 확실히 끄기",
    wow: "당직 날은 놓치면 안 되니 두 번 울리고 덧셈을 풀어야 꺼져요. 빨간 비상 화면이라 무슨 알람인지 한눈에 알아요.",
    changes: ["당직 150·60분 전", "출근 60분 전", "빨간 비상 화면"], who: "당직·비상 대기가 있는 일근", cover: ["#B71C1C", "#FF8A80", "🚨"],
    byRole: [["long", 150, 60], ["day", 60]], mission: "math", ring: 5, p: { bg: "#2A0A0A", card: "#3D1010", ink: "#FFEBEE", accent: "#FF5252" },
  }),
  alarm({
    title: "알바 출근 30분 전", styles: HOURLY, summary: "오픈·마감·야간마다 30분 전, 지각 걱정 없이",
    wow: "알바 출근은 가까운 곳이 많으니 딱 30분 전에 한 번 울려요. 오픈 날은 이른 시각이라 50분 전에 한 번 더 깨워요.",
    changes: ["오픈 50·30분 전", "마감·야간 30분 전", "상큼한 레몬 화면"], who: "가까운 곳에서 일하는 알바", cover: ["#FFF59D", "#FBC02D", "🍋"],
    byRole: [["day", 50, 30], ["eve", 30], ["night", 30]], showcase: true, p: { bg: "#FFFDE7", card: "#FFFFFF", ink: "#5D4037", accent: "#F9A825" },
  }),
  alarm({
    title: "오픈 알바 확실히 깨우기", styles: HOURLY, summary: "오픈 날만 흔들어서 끄기, 마감 날은 부드럽게",
    wow: "가게 문을 여는 오픈 날은 놓치면 큰일이라 흔들어야 꺼지고 두 번 울려요. 마감·야간 날은 40분 전 한 번이에요.",
    changes: ["오픈 70·35분 전", "마감·야간 40분 전", "새벽 하늘 화면"], who: "오픈 근무가 있는 카페·편의점 알바", cover: ["#FF7043", "#FFCCBC", "🌅"],
    byRole: [["day", 70, 35], ["eve", 40], ["night", 40]], mission: "shake", p: { bgPaint: "lg.180.FF263238,FFBF360C", bg: "#263238", accent: "#FF7043" },
  }),
  alarm({
    title: "야간 알바 저녁잠", styles: HOURLY, summary: "야간 알바 날은 저녁잠을 깨우는 두 번 알람",
    wow: "밤 근무 전 저녁잠을 자도 걱정 없게 두 시간 전과 45분 전에 울려요. 소리가 작게 시작해 커져요.",
    changes: ["야간 120·45분 전", "오픈·마감 40분 전", "편의점 밤 남색 화면"], who: "편의점·물류 야간 알바", cover: ["#0D47A1", "#64B5F6", "🏪"],
    byRole: [["night", 120, 45], ["day", 40], ["eve", 40]], gradual: true, p: { bg: "#08152B", card: "#0F2447", accent: "#64B5F6" },
  }),
  alarm({
    title: "긴 다시 울림", styles: ROSTER, summary: "다시 울림 10분, 한 번에 5분: 천천히 일어나는 분께",
    wow: "한 번에 벌떡 못 일어나는 분을 위해 한 시간 반 전부터 넉넉히 울리고, 다시 울림 간격을 10분으로 길게 잡았어요.",
    changes: ["근무마다 90분 전", "포근한 분홍 화면"], who: "천천히 잠을 깨는 분", cover: ["#F8BBD0", "#FCE4EC", "🧸"],
    byRole: [["day", 90], ["eve", 90], ["night", 90], ["long", 90]], snooze: 10, ring: 5, gradual: true,
    p: { bg: "#FDF2F6", card: "#FFFFFF", ink: "#880E4F", accent: "#EC407A", font: "jua" },
  }),
  alarm({
    title: "짧고 단호하게", styles: ROSTER, summary: "다시 울림 2분, 한 번 1분: 미루지 못하는 알람",
    wow: "다시 울림을 2분으로 짧게, 한 번 울리는 시간도 1분이라 늘어지지 않아요. 미니멀한 흑백 화면이에요.",
    changes: ["근무마다 45분 전", "흑백 미니멀 화면"], who: "알람을 여러 번 미루는 버릇이 있는 분", cover: ["#000000", "#FFFFFF", "⚡"],
    byRole: [["day", 45], ["eve", 45], ["night", 45], ["long", 45]], snooze: 2, ring: 1, p: { bg: "#000000", card: "#111111", ink: "#FFFFFF", accent: "#FFFFFF" },
  }),
  alarm({
    title: "진동만 조용히", styles: ROSTER, summary: "소리는 작게 시작, 진동이 먼저: 같이 자는 가족을 깨우지 않게",
    wow: "같이 사는 가족이 깨지 않도록 진동이 먼저 울리고 소리는 아주 작게 시작해요. 근무 50분 전 한 번이에요.",
    changes: ["근무마다 50분 전", "어두운 회색 화면"], who: "가족과 함께 자는 교대 근무자", cover: ["#455A64", "#CFD8DC", "🤫"],
    byRole: [["day", 50], ["eve", 50], ["night", 50]], gradual: true, ring: 2, p: { bg: "#1C2328", card: "#263238", ink: "#ECEFF1", accent: "#90A4AE" },
  }),
  alarm({
    title: "병동 교대 알람", styles: SHIFT, summary: "데이 100·40분, 이브닝 70분, 나이트 150·60분 전",
    wow: "병동 교대는 인수인계 전에 도착해야 하니 데이는 두 번, 나이트는 낮잠 깨우기까지 두 번 울려요. 이브닝은 오후라 한 번이면 돼요.",
    changes: ["데이 100·40분 전", "이브닝 70분 전", "나이트 150·60분 전", "청록 병동 화면"], who: "병동 간호사·간호조무사", cover: ["#00695C", "#80CBC4", "🩺"],
    byRole: [["day", 100, 40], ["eve", 70], ["night", 150, 60]], p: { bg: "#002B26", card: "#003D36", ink: "#E0F2F1", accent: "#4DB6AC" },
  }),
  alarm({
    title: "소방 당번 알람", styles: SHIFT, summary: "당번 날 두 번, 진동과 덧셈으로 확실히",
    wow: "24시간 당번 날은 놓치면 안 되니 두 번 울리고 덧셈을 풀어야 꺼져요. 빨강·주황 불꽃 화면이에요.",
    changes: ["당번(긴 근무) 120·50분 전", "주간 60분 전", "불꽃 화면"], who: "소방·경비처럼 24시간 당번이 있는 분", cover: ["#D84315", "#FFAB91", "🚒"],
    byRole: [["long", 120, 50], ["day", 60], ["night", 60]], mission: "math", p: { bgPaint: "lg.200.FF3E0B00,FFBF360C", bg: "#3E0B00", accent: "#FF7043" },
  }),
  alarm({
    title: "공장 4조 2교대 알람", styles: SHIFT, summary: "주간·야간 12시간 근무 전 두 번씩, 통근 버스 시간에 맞춰",
    wow: "12시간 주간·야간이 번갈아 오는 4조 2교대에 맞춰, 통근 버스 시간에 늦지 않도록 두 번씩 울려요.",
    changes: ["주간 80·35분 전", "야간 120·50분 전", "공장 회색·노랑 화면"], who: "4조 2교대 생산직", cover: ["#37474F", "#FFD600", "🏭"],
    byRole: [["day", 80, 35], ["night", 120, 50], ["long", 80, 35]], p: { bg: "#1E272C", card: "#2B373D", ink: "#ECEFF1", accent: "#FFD600" },
  }),
  alarm({
    title: "주말 근무 다르게", styles: DAY, summary: "평일 출근은 한 번, 주말 특근(당직)은 두 번 넉넉히",
    wow: "주말 특근은 평소와 리듬이 달라 놓치기 쉬워요. 당직 근무로 넣은 날은 두 번 울리고, 평일 출근은 한 번이에요.",
    changes: ["출근 60분 전", "특근·당직 100·40분 전", "주말 하늘색 화면"], who: "가끔 주말 특근이 있는 일근", cover: ["#4FC3F7", "#E1F5FE", "📅"],
    byRole: [["day", 60], ["long", 100, 40]], gradual: true, p: { bg: "#EAF6FD", card: "#FFFFFF", ink: "#0D47A1", accent: "#0288D1" },
  }),
  alarm({
    title: "편의점 교대 알바", styles: HOURLY, summary: "오전·오후·야간 교대 알바 40분 전, 늦으면 흔들어 끄기",
    wow: "편의점처럼 앞 사람과 교대하는 알바는 늦으면 앞 사람이 못 가요. 40분 전과 15분 전 두 번, 흔들어서 꺼요.",
    changes: ["오픈·마감·야간 40·15분 전", "편의점 초록 화면"], who: "편의점·주유소 교대 알바", cover: ["#2E7D32", "#C8E6C9", "🏪"],
    byRole: [["day", 40, 15], ["eve", 40, 15], ["night", 40, 15]], mission: "shake", p: { bg: "#0B2410", card: "#14361A", ink: "#E8F5E9", accent: "#66BB6A" },
  }),
  alarm({
    title: "학생 알바 수업 뒤", styles: HOURLY, summary: "마감 알바 전 20분 알림, 진동만 조용히",
    wow: "수업이 끝나고 바로 가는 마감 알바라 강의실에서 소리가 나지 않게 진동과 작은 소리로 20분 전에 알려요.",
    changes: ["마감 20분 전", "오픈 60분 전", "노트 같은 줄무늬 화면"], who: "수업 뒤 알바하는 학생", cover: ["#FFFFFF", "#90CAF9", "🎒"],
    byRole: [["eve", 20], ["day", 60], ["night", 30]], gradual: true, ring: 2,
    p: { bg: "#FAFBFF", card: "#FFFFFF", ink: "#1A237E", accent: "#42A5F5", bgPaint: "pt.thin.FFFAFBFF.FFE3F2FD.80.0" },
  }),
  alarm({
    title: "네온 알람", styles: ROSTER, summary: "보라·청록 네온 화면, 근무마다 두 번",
    wow: "깜깜한 방에서 눈에 확 들어오는 네온 알람 화면이에요. 근무마다 60분·20분 전 두 번 울려요.",
    changes: ["근무마다 60·20분 전", "네온 보라 화면"], who: "화려한 화면을 좋아하는 분", cover: ["#12002B", "#00E5FF", "🌃"],
    byRole: [["day", 60, 20], ["eve", 60, 20], ["night", 60, 20], ["long", 60, 20]], p: { bgPaint: "lg.135.FF12002B,FF3A0CA3", bg: "#12002B", card: "#1E0B45", ink: "#E0F7FA", accent: "#00E5FF" },
  }),
  alarm({
    title: "숲속 아침 알람", styles: ROSTER, summary: "초록 숲 화면, 점점 커지는 소리로 근무 75분 전",
    wow: "깨자마자 보는 화면이 초록 숲이면 하루가 덜 무거워요. 소리가 작게 시작해 커지고 한 번에 4분 울려요.",
    changes: ["근무마다 75분 전", "숲 초록 화면"], who: "편안한 아침을 원하는 분", cover: ["#1B5E20", "#A5D6A7", "🌲"],
    byRole: [["day", 75], ["eve", 75], ["night", 75], ["long", 75]], gradual: true, ring: 4,
    p: { bgPaint: "lg.180.FF1B5E20,FF0B2E10", bg: "#0B2E10", card: "#14401A", ink: "#E8F5E9", accent: "#81C784" },
  }),
];
