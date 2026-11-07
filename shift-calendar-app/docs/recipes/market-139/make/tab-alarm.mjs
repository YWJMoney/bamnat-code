// 1.39 [MKW-03] 탭별 › 알람 50. 달력 50과 같은 미술 방향(skins.mjs) 한 세트씩: 알람 화면(바탕·카드·큰 시각·단추 색·글꼴)을 그 방향으로
// 그리고, 근무 종류마다 몇 분 전에 몇 번 울릴지와 끄는 법(덧셈 문제·흔들기), 점점 커지는 소리, 다시 울림 간격을 그 사람의 하루에 맞춰요.
// 근무 알람 묶음(alarms)은 교대·일근·알바만 받는 조각이라, 현장·자영업·투자·모든 유형 세트는 그 미술 방향을 교대·일근·알바 알람으로 옮겼어요.
import { tabWork } from "./skins.mjs";

const NAME = { day: "주간", eve: "저녁", night: "야간", off: "쉬는 날", long: "당직", other: "재택" };
const DAYN = { day: "출근", other: "재택", long: "당직" };
const HN = { day: "오픈", eve: "마감", night: "야간" };
const mins = (m) => (m % 60 === 0 ? `${m / 60}시간` : m > 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`);

/** One alarm work: s.from (달력 세트), s.by [[role, 분, 분…]], s.mission, s.gradual, s.snooze, s.ring, s.vibrate. */
function alarm(s) {
  const names = s.style === "DAY" ? { ...NAME, ...DAYN } : s.style === "HOURLY" ? { ...NAME, ...HN } : NAME;
  const plan = s.by.map(([role, ...b]) => `${names[role]} ${b.map(mins).join("·")} 전`).join(", ");
  const how = [s.gradual ? "소리는 작게 시작해 점점 커져요." : null, s.mission === "math" ? "덧셈 문제를 풀어야 꺼져요." : s.mission === "shake" ? "휴대폰을 흔들어야 꺼져요." : null,
    s.snooze ? `다시 울림은 ${s.snooze}분 뒤예요.` : null];
  const wow = [s.lead, `${plan}에 울려요.`, ...how].filter(Boolean).join(" ");
  const tags = [s.mission === "math" ? "덧셈 문제로 끄기" : s.mission === "shake" ? "흔들어 끄기" : null, s.gradual ? "점점 커지는 소리" : null, s.snooze ? `다시 울림 ${s.snooze}분` : null].filter(Boolean);
  return tabWork("alarm", {
    ...s, wow, changes: [...(s.changes ?? []), "근무마다 알람 시각", ...tags],
    alarms: { kind: "alarms", byRole: s.by.map(([role, ...before]) => ({ role, before })), ...(s.snooze ? { snooze: s.snooze } : {}), ...(s.ring ? { ring: s.ring } : {}),
      ...(s.gradual ? { gradual: true } : {}), ...(s.vibrate !== false ? { vibrate: true } : {}), ...(s.mission ? { mission: s.mission } : {}) },
  });
}

export default [
  // ---------- 교대 (달력 세트 12 + 모든 유형 세트 6) ----------
  alarm({ from: "야간 레이더 달력", title: "야간 레이더 기상", top: true, summary: "관제 화면 같은 검은 알람, 야간은 세 시간·한 시간 전 두 번",
    lead: "알람 화면이 관제 모니터처럼 검고 큰 시각이 초록 형광으로 떠요.", changes: ["관제 모니터 알람 화면"],
    by: [["day", 90, 50], ["eve", 90], ["night", 180, 60]], gradual: true, snooze: 5 }),
  alarm({ from: "수간호사 근무표", title: "인수인계 맞춤 알람", summary: "인수인계 30분 일찍 맞춘 근무마다 두 번, 흔들어 끄기",
    lead: "인수인계 전에 여유 있게 도착하도록 근무마다 두 번 울려요.", changes: ["청록 알람 화면"], by: [["day", 100, 60], ["eve", 100, 60], ["night", 150, 70]], mission: "shake" }),
  alarm({ from: "달빛 띠 근무 달력", title: "달빛 낮잠 깨우기", summary: "야간 전 낮잠에서 깨우는 네 시간 전 알람, 소리는 점점",
    lead: "야간 전 낮잠에서 깰 시간과 나갈 시간을 따로 챙겨요.", changes: ["남색 밤하늘 알람 화면"], by: [["night", 240, 90], ["eve", 90]], gradual: true, snooze: 10 }),
  alarm({ from: "타임라인 교대표", title: "시간표 정각 알람", summary: "근무마다 정확히 한 번, 군더더기 없는 파랑 알람",
    lead: "근무마다 한 번만, 정한 시각에 또렷하게 울려요.", changes: ["파랑 알람 화면"], by: [["day", 80], ["eve", 80], ["night", 100]], ring: 3 }),
  alarm({ from: "새벽 4시 하늘", title: "새벽빛 점점 기상", summary: "새벽빛 화면, 작게 시작하는 소리, 주간은 두 번",
    lead: "새벽 주간에 놀라지 않게 작은 소리부터 시작해요.", changes: ["새벽빛 알람 화면"], by: [["day", 110, 70], ["eve", 80], ["night", 150]], gradual: true, snooze: 10 }),
  alarm({ from: "지하철 환승 달력", title: "첫차 맞춤 알람", summary: "첫차·막차 시간에 맞춘 근무마다 두 번",
    lead: "대중교통으로 출근하는 분께 맞춰 근무마다 두 번 울려요.", changes: ["노선 색 알람 화면"], by: [["day", 100, 70], ["eve", 90, 60], ["night", 120, 80]], snooze: 5 }),
  alarm({ from: "오로라 유리 칸", title: "오로라 유리 기상", top: true, summary: "오로라 위 유리 카드 알람, 야간은 덧셈 문제로 끄기",
    lead: "알람 화면 뒤로 오로라가 흐르고 시각은 유리 카드 위에 떠요.", changes: ["오로라 알람 화면"], by: [["day", 90], ["eve", 70], ["night", 180, 90]], mission: "math", gradual: true }),
  alarm({ from: "소방서 출동판", title: "출동 대기 알람", summary: "빨강 경광 화면, 흔들어 끄기, 다시 울림 3분",
    lead: "당번 날 바로 일어나도록 빨강 알람 화면에 흔들어 끄기를 걸었어요.", changes: ["빨강 알람 화면"], by: [["day", 70, 40], ["night", 90, 50]], mission: "shake", snooze: 3 }),
  alarm({ from: "4조2교대 큰 칸", title: "큰 시계 기상", summary: "시각이 아주 큰 알람, 근무마다 두 번",
    lead: "안경 없이도 보이게 큰 시각과 넉넉한 단추로 그렸어요.", changes: ["큰 시각 알람 화면"], by: [["day", 90, 60], ["eve", 90, 60], ["night", 150, 90]], ring: 5 }),
  alarm({ from: "수면 빚 달력", title: "수면 빚 갚는 알람", summary: "야간 뒤 충분히 자도록 늦게 울리는 주간, 소리는 점점",
    lead: "잠을 최대한 지키도록 출발에 꼭 필요한 시간만 남겨요.", changes: ["회청색 알람 화면"], by: [["day", 70], ["eve", 60], ["night", 120]], gradual: true, snooze: 15 }),
  alarm({ from: "체크무늬 간호 수첩", title: "분홍 수첩 알람", summary: "분홍 체크 화면, 근무마다 두 번, 덧셈 문제",
    lead: "분홍 체크 수첩 같은 알람 화면에 덧셈 문제로 잠을 깨워요.", changes: ["분홍 체크 알람 화면"], by: [["day", 100, 60], ["eve", 90], ["night", 150, 60]], mission: "math" }),
  alarm({ from: "항구 교대 해도", title: "뱃고동 기상", summary: "해도 종이 화면, 작게 시작하는 소리, 야간은 두 번",
    lead: "뱃고동처럼 낮게 시작해 커지는 알람이에요.", changes: ["해도 알람 화면"], by: [["day", 90], ["eve", 90], ["night", 180, 60]], gradual: true }),
  alarm({ from: "비 오는 날 유리창", title: "빗소리 기상", style: "SHIFT", summary: "빗방울 유리 화면, 점점 커지는 소리, 다시 울림 10분",
    lead: "빗방울 맺힌 유리창 화면에서 조용히 깨워요.", changes: ["빗방울 알람 화면"], by: [["day", 90], ["eve", 70], ["night", 150]], gradual: true, snooze: 10 }),
  alarm({ from: "우주 정거장 달력", title: "정거장 교대 호출", style: "SHIFT", summary: "별빛 화면, 야간은 덧셈 문제, 근무마다 두 번",
    lead: "정거장 교대 호출처럼 근무마다 두 번 부르고 야간은 덧셈 문제로 깨워요.", changes: ["별빛 알람 화면"], by: [["day", 90, 60], ["eve", 90, 60], ["night", 150, 90]], mission: "math" }),
  alarm({ from: "네온 사인 달력", title: "네온 야간 알람", style: "SHIFT", summary: "네온 분홍 화면, 흔들어 끄기, 야간 세 시간 전",
    lead: "네온 간판처럼 빛나는 알람 화면에 흔들어 끄기를 걸었어요.", changes: ["네온 알람 화면"], by: [["eve", 80], ["night", 180, 60]], mission: "shake" }),
  alarm({ from: "숲속 오두막 달력", title: "오두막 새소리 기상", style: "SHIFT", summary: "나무 결 화면, 아주 천천히 커지는 소리",
    lead: "오두막 나무 결 화면에서 천천히 깨워요.", changes: ["나무 결 알람 화면"], by: [["day", 100], ["eve", 80], ["night", 160]], gradual: true, snooze: 15 }),
  alarm({ from: "낮잠 고양이 달력", title: "고양이 꾹꾹 기상", style: "SHIFT", summary: "살구빛 화면, 다시 울림 5분, 근무마다 두 번",
    lead: "고양이가 꾹꾹 누르듯 5분마다 다시 깨워요.", changes: ["살구빛 알람 화면"], by: [["day", 90, 60], ["eve", 80], ["night", 150, 60]], snooze: 5 }),
  alarm({ from: "레트로 체크 피크닉", title: "체크무늬 자명종", style: "SHIFT", summary: "빨강 체크 화면, 덧셈 문제, 울림 5분",
    lead: "옛날 자명종처럼 또렷하게, 덧셈 문제를 풀어야 꺼져요.", changes: ["체크 알람 화면"], by: [["day", 80], ["eve", 80], ["night", 120]], mission: "math", ring: 5 }),

  // ---------- 일근 (달력 세트 8 + 다른 세트 6) ----------
  alarm({ from: "주간 플래너 펼침", title: "에디토리얼 모닝콜", top: true, summary: "잡지 지면 같은 세리프 알람, 출근 80·50분 전",
    lead: "알람 화면이 잡지 지면처럼 큰 세리프 시각과 붉은 선 하나로 놓여요.", changes: ["세리프 알람 화면"], by: [["day", 80, 50], ["long", 120], ["other", 40]], gradual: true }),
  alarm({ from: "칼퇴 신호등", title: "초록불 출근 알람", summary: "초록 신호 화면, 출근 두 번, 재택은 한 번",
    lead: "초록 신호처럼 기분 좋게 출근을 알려요.", changes: ["신호등 알람 화면"], by: [["day", 70, 40], ["other", 30], ["long", 100]], snooze: 5 }),
  alarm({ from: "재택·출근 반반", title: "재택·출근 따로 알람", summary: "출근 날은 한 시간 반 전, 재택 날은 30분 전",
    lead: "출근하는 날과 재택하는 날을 다른 시각에 깨워요.", changes: ["두 색 알람 화면"], by: [["day", 90, 60], ["other", 30]], gradual: true }),
  alarm({ from: "분기 목표 연간판", title: "목표 아침 알람", summary: "숲색 화면, 출근 전 한 시간 여유",
    lead: "출근 전 한 시간을 나를 위해 쓰도록 일찍 깨워요.", changes: ["숲색 알람 화면"], by: [["day", 120, 80], ["long", 140]], gradual: true, snooze: 10 }),
  alarm({ from: "사원증 스트랩", title: "사원증 출근 콜", summary: "남색 화면, 출근 두 번, 흔들어 끄기",
    lead: "남색 스트랩 같은 알람 화면에 흔들어 끄기를 걸었어요.", changes: ["남색 알람 화면"], by: [["day", 80, 50], ["long", 110]], mission: "shake" }),
  alarm({ from: "점심 메뉴 달력", title: "든든한 아침 알람", summary: "살구빛 화면, 아침 먹을 시간까지 넉넉히",
    lead: "아침을 챙겨 먹을 만큼 넉넉하게 깨워요.", changes: ["살구빛 알람 화면"], by: [["day", 100, 70], ["other", 50]], gradual: true }),
  alarm({ from: "흑백 미니멀 일정", title: "미니멀 알람", summary: "흰 화면 검은 시각, 출근 한 번",
    lead: "색을 덜어 낸 흰 화면에 시각 하나만 남겼어요.", changes: ["흑백 알람 화면"], by: [["day", 60], ["other", 30]] }),
  alarm({ from: "회의실 예약판", title: "첫 회의 알람", summary: "단정한 파랑 화면, 출근 두 번과 당직",
    lead: "첫 회의에 늦지 않게 출근 전 두 번 울려요.", changes: ["파랑 알람 화면"], by: [["day", 90, 45], ["long", 120], ["other", 30]], snooze: 5 }),
  alarm({ from: "철근 도면 달력", title: "도면 출근 알람", style: "DAY", summary: "청사진 화면, 출근 두 번, 덧셈 문제",
    lead: "청사진 도면 같은 알람 화면에 덧셈 문제를 걸었어요.", changes: ["청사진 알람 화면"], by: [["day", 90, 60], ["long", 120]], mission: "math" }),
  alarm({ from: "뉴욕 신문 달력", title: "조간신문 알람", style: "DAY", summary: "신문 지면 화면, 출근 한 시간 전",
    lead: "조간신문을 펼치듯 굵은 제목 화면으로 아침을 열어요.", changes: ["신문 알람 화면"], by: [["day", 60], ["other", 30]], gradual: true }),
  alarm({ from: "독서 기록 서가", title: "책 한 장 아침 알람", style: "DAY", summary: "명조 화면, 출근 전 책 읽을 시간까지",
    lead: "출근 전 책 몇 장 읽을 시간을 남기고 깨워요.", changes: ["명조 알람 화면"], by: [["day", 110, 80], ["other", 50]], gradual: true, snooze: 10 }),
  alarm({ from: "봄 햇살 테라조", title: "봄 햇살 기상", style: "DAY", summary: "봄빛 테라조 화면, 점점 커지는 소리",
    lead: "봄 햇살처럼 부드럽게 깨워요.", changes: ["테라조 알람 화면"], by: [["day", 80, 50], ["other", 30]], gradual: true }),
  alarm({ from: "운동 출석 히트맵", title: "아침 운동 알람", style: "DAY", summary: "초록 잔디 화면, 출근 두 시간 전, 흔들어 끄기",
    lead: "출근 전에 운동할 수 있게 두 시간 일찍, 흔들어야 꺼져요.", changes: ["잔디 알람 화면"], by: [["day", 120, 90]], mission: "shake" }),
  alarm({ from: "적립식 씨앗 달력", title: "새싹 아침 알람", style: "DAY", summary: "새싹 화면, 출근 70분 전 한 번",
    lead: "새싹 연두 화면으로 하루를 가볍게 시작해요.", changes: ["새싹 알람 화면"], by: [["day", 70], ["other", 30]], gradual: true }),

  // ---------- 알바 (달력 세트 6 + 다른 세트 12) ----------
  alarm({ from: "편의점 영수증 달력", title: "영수증 출근 알람", top: true, summary: "감열지 영수증 화면, 오픈·마감마다 두 번",
    lead: "알람 화면이 영수증 한 장처럼 점선과 고정폭 시각으로 놓여요.", changes: ["영수증 알람 화면"], by: [["day", 60, 30], ["eve", 60, 30], ["night", 90]], snooze: 5 }),
  alarm({ from: "버블티 알바 달력", title: "밀크티 알바 알람", summary: "밀크티 색 동글 화면, 오픈 한 시간 전",
    lead: "밀크티 빛 동그란 알람 화면이에요.", changes: ["밀크티 알람 화면"], by: [["day", 60], ["eve", 50]], gradual: true }),
  alarm({ from: "투잡 두 색 달력", title: "투잡 두 번 알람", summary: "오픈·마감마다 두 번, 덧셈 문제",
    lead: "일이 둘이라도 놓치지 않게 근무마다 두 번, 덧셈 문제로 깨워요.", changes: ["두 색 알람 화면"], by: [["day", 70, 40], ["eve", 60, 30], ["night", 90, 50]], mission: "math" }),
  alarm({ from: "픽셀 게임 출석부", title: "픽셀 스타트 알람", summary: "8비트 게임 화면, 흔들어 끄기",
    lead: "게임 시작 화면처럼 픽셀 시각이 깜빡이고 흔들어야 꺼져요.", changes: ["8비트 알람 화면"], by: [["day", 60], ["eve", 60], ["night", 90]], mission: "shake" }),
  alarm({ from: "주말 알바 도장판", title: "주말 도장 알람", summary: "분홍 도장판 화면, 다시 울림 5분",
    lead: "주말 늦잠을 막도록 5분마다 다시 깨워요.", changes: ["도장판 알람 화면"], by: [["day", 70], ["eve", 50]], snooze: 5 }),
  alarm({ from: "새벽 배송 루트", title: "새벽 배송 콜", summary: "밤길 형광 화면, 야간 두 번, 흔들어 끄기",
    lead: "새벽 배송 출발에 맞춰 두 번, 흔들어야 꺼져요.", changes: ["밤길 알람 화면"], by: [["night", 90, 40], ["day", 60]], mission: "shake" }),
  alarm({ from: "공사 일보 칠판", title: "칠판 새벽 알람", style: "HOURLY", summary: "칠판 화면, 오픈 두 번, 울림 5분",
    lead: "칠판 위 분필 글씨 같은 알람 화면이에요.", changes: ["칠판 알람 화면"], by: [["day", 80, 50], ["eve", 60]], ring: 5 }),
  alarm({ from: "안전 제일 표지판", title: "노랑 경보 알람", style: "HOURLY", summary: "노랑·검정 화면, 덧셈 문제",
    lead: "안전 표지처럼 눈에 띄는 노랑 화면에 덧셈 문제를 걸었어요.", changes: ["표지판 알람 화면"], by: [["day", 70], ["eve", 60], ["night", 90]], mission: "math" }),
  alarm({ from: "일당 봉투 달력", title: "새벽 인력 알람", style: "HOURLY", summary: "누런 종이 화면, 오픈 두 시간 전부터 두 번",
    lead: "새벽 일찍 나가는 날을 두 번에 나눠 깨워요.", changes: ["봉투 종이 알람 화면"], by: [["day", 120, 80]], gradual: true }),
  alarm({ from: "해 뜨는 비계", title: "해돋이 기상", style: "HOURLY", summary: "해 뜨는 빛 화면, 점점 커지는 소리",
    lead: "해 뜨는 살구빛 화면에서 천천히 깨워요.", changes: ["해돋이 알람 화면"], by: [["day", 90, 60], ["eve", 60]], gradual: true }),
  alarm({ from: "매출 온도계 달력", title: "오픈 준비 알람", style: "HOURLY", summary: "빨강 온도계 화면, 오픈 두 번, 마감 한 번",
    lead: "가게 문 열기 전에 준비할 시간을 두 번 알려요.", changes: ["온도계 알람 화면"], by: [["day", 90, 45], ["eve", 60]], snooze: 5 }),
  alarm({ from: "꽃집 계절 달력", title: "꽃시장 새벽 알람", style: "HOURLY", summary: "꽃잎 분홍 화면, 오픈 두 시간 전",
    lead: "새벽 꽃시장에 가는 날처럼 일찍, 소리는 꽃잎처럼 작게 시작해요.", changes: ["꽃잎 알람 화면"], by: [["day", 120, 90], ["eve", 50]], gradual: true }),
  alarm({ from: "오마카세 예약 장부", title: "카운터 준비 알람", style: "HOURLY", summary: "먹색·금박 화면, 마감 근무 두 번",
    lead: "먹색 카운터 같은 알람 화면에 금박 시각이 놓여요.", changes: ["먹색 알람 화면"], by: [["eve", 90, 60], ["day", 60]] }),
  alarm({ from: "장날 도장 달력", title: "장날 새벽 알람", style: "HOURLY", summary: "장터 종이 화면, 덧셈 문제, 오픈 두 번",
    lead: "장 서는 날 새벽에 놓치지 않게 덧셈 문제를 걸었어요.", changes: ["장터 종이 알람 화면"], by: [["day", 100, 60]], mission: "math" }),
  alarm({ from: "1인 카페 운영표", title: "원두 볶는 아침", style: "HOURLY", summary: "원두색 세리프 화면, 오픈 한 시간 반 전",
    lead: "커피 내릴 준비 시간까지 넉넉하게 깨워요.", changes: ["원두색 알람 화면"], by: [["day", 90], ["eve", 50]], gradual: true, snooze: 10 }),
  alarm({ from: "캔들 차트 달력", title: "차트 장 시작 알람", style: "HOURLY", summary: "검은 차트 화면, 초록 시각, 한 번",
    lead: "차트 화면처럼 검은 바탕에 초록 시각이 떠요.", changes: ["차트 알람 화면"], by: [["day", 60], ["eve", 40]] }),
  alarm({ from: "배당 금화 달력", title: "금화 아침 알람", style: "HOURLY", summary: "금빛 세리프 화면, 점점 커지는 소리",
    lead: "금빛 세리프 시각이 놓인 차분한 알람이에요.", changes: ["금빛 알람 화면"], by: [["day", 70], ["eve", 50]], gradual: true }),
  alarm({ from: "매매 일지 모눈", title: "모눈 손글씨 알람", style: "HOURLY", summary: "모눈 종이 화면, 다시 울림 10분",
    lead: "모눈 종이 위 손글씨 시각이 놓여요.", changes: ["모눈 알람 화면"], by: [["day", 60], ["eve", 50], ["night", 80]], snooze: 10 }),
];
