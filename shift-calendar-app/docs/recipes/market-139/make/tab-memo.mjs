// 1.39 [MKW-03] 탭별 › 메모 50. 달력 50과 같은 미술 방향(skins.mjs) 한 세트씩: 메모장 모양(목록·두 줄·세 줄·포스트잇·일기·촘촘)과
// 보기 판(목록·보드·표·달력·갤러리), 메모 카드·쓰기 종이를 그 방향으로 그리고, 그 유형의 하루에 맞는 메모 틀과 메모 값(마감·체크율·고정·
// 색번호·제목·오늘·지난날, 1.36~1.38 수식, 무료)에 따라 바뀌는 카드를 더해요. 틀은 쓸 때만 메모가 돼요.
import { c } from "../../market-136/make/lib.mjs";
import { T } from "../../market-136/make/kit.mjs";
import { TPL } from "../../market-138-studio/make/feats.mjs";
import { lg, pt } from "./art.mjs";
import { tabWork } from "./skins.mjs";

const f = (say, tag, lines) => ({ say, tag, lines: Array.isArray(lines) ? lines : [lines] });
const F = {
  due: (col = "#E53935") => f("마감 칸을 적은 메모는 붉은 테두리로 눈에 띄어요.", "마감 메모 붉은 테", { id: "memo.card", when: '아님 비었나(칸("마감"))', line: "solid", lineW: 2, lineC: c(col) }),
  done: () => f("체크를 다 끝낸 메모는 흐려져서 남은 일만 또렷해요.", "끝낸 메모 흐리게", { id: "memo.card", when: "체크율 >= 100", alpha: 50 }),
  half: (col = "#FFB300") => f("체크를 반쯤 한 메모는 노란 점선 테두리로 '하는 중'이 보여요.", "하는 중 노란 점선",
    { id: "memo.card", when: "체크율 >= 50 그리고 체크율 < 100", line: "dash", lineW: 1.5, lineC: c(col) }),
  pinned: (col = "#FFB300") => f("고정한 메모는 금색 테두리로 맨 위에서 빛나요.", "고정 메모 금테", { id: "memo.card", when: "고정", line: "solid", lineW: 2, lineC: c(col), motion: "glow" }),
  color: (n, col, name) => f(`색 ${n}번(${name}) 메모는 그 색 테두리가 굵게 둘러져요.`, `${name} 메모 테`, { id: "memo.card", when: `색번호 = ${n}`, line: "solid", lineW: 3, lineC: c(col) }),
  urgent: (word = "급함", col = "#E53935") => f(`제목에 ‘${word}’이 들어간 메모는 제목이 붉고 굵어요.`, `${word} 제목 붉게`,
    { id: "memo.card.title", when: `포함(제목, "${word}")`, ink: c(col), weight: 900 }),
  today: (col) => f("오늘 날짜 메모는 포인트 색 테두리가 둘러져요.", "오늘 메모 테두리", { id: "memo.card", when: "오늘", line: "solid", lineW: 2, lineC: c(col) }),
  old: () => f("지난 날짜의 메모는 조금 흐려져서 앞으로 할 일이 또렷해요.", "지난 메모 흐리게", { id: "memo.card", when: "지난날 그리고 아님 고정", alpha: 65 }),
  full: (n = 5, col = "#FB8C00") => f(`보드 한 칸에 메모가 ${n}개를 넘으면 그 칸 머리에 주황 테두리가 생겨요.`, "꽉 찬 칸 주황 머리",
    { id: "memo.board.head", when: `칸수 > ${n}`, line: "solid", lineW: 2, lineC: c(col) }),
  long: () => f("글이 긴 메모는 본문이 조금 커져 읽기 편해요.", "긴 메모 크게", { id: "memo.card.body", when: "길이(내용) >= 80", size: 13 }),
};

// 이 판에서 더한 틀 (밤낮 말로 새로 씀)
const N = {
  nightNote: { title: "야간 메모", emoji: "🌙", body: "{근무} · {근무시작}~{근무끝}\n이번 주 야간 {이번주야간}번째\n특이 사항: ", check: ["물 마시기", "눈 쉬기", "퇴근 후 바로 자기"] },
  handBag: { title: "출근 가방", emoji: "👜", check: ["사원증", "충전기", "텀블러", "약", "이어폰"] },
  shiftSwap: { title: "근무 바꾸기", emoji: "🔁", body: "바꿀 날: \n바꿀 사람: \n돌려받을 날: ", asks: [{ label: "바꿀 날", type: "date" }, { label: "확정", type: "check" }] },
  oneLine: { title: "한 줄 일기", emoji: "✏️", body: "{날짜} {요일} · {근무}\n", asks: [{ label: "오늘 기분", type: "pick", choices: ["좋음", "보통", "지침"] }] },
  partShift: { title: "알바 근무 기록", emoji: "🧾", body: "{날짜} · {근무시간}시간\n특이 사항: ", check: ["출근 찍기", "퇴근 찍기", "마감 정리"] },
  siteSafe: { title: "작업 전 안전 점검", emoji: "⛑️", check: ["안전모·안전화", "추락 방지", "전기 확인", "휴식 시간"] },
  shopNote: { title: "손님 요청", emoji: "🛎️", body: "손님: \n요청: \n언제까지: ", asks: [{ label: "상태", type: "pick", choices: ["받음", "준비", "완료"] }] },
  rule: { title: "내 원칙 점검", emoji: "🧭", check: ["정한 비중 지킴", "소문으로 사지 않음", "기록 남김"], asks: [{ label: "마음 점수", type: "star" }] },
  weekend: { title: "주말 계획", emoji: "🧺", check: ["쉬기", "만날 사람", "할 일 하나"], body: "{날짜} {요일}" },
};

/** One memo work: s.from (달력 세트), s.f, s.tpls (틀), s.look (메모장 모양), s.view (보기 판), the words. */
function memo(s) {
  const feats = s.f ?? [];
  const wow = [s.lead, ...feats.map((x) => x.say)].filter(Boolean).join(" ");
  return tabWork("memo", {
    ...s, wow, changes: [...(s.changes ?? []), ...feats.map((x) => x.tag), ...(s.tpls?.length ? [`메모 틀 ${s.tpls.length}개`] : [])],
    parts: [...(s.parts ?? []), ...feats.flatMap((x) => x.lines)],
    memo: s.look, ...(s.view ? { view: s.view } : {}),
  });
}

export default [
  // ---------- 교대 12 ----------
  memo({ from: "야간 레이더 달력", title: "야간 레이더 메모판", top: true, summary: "관제 화면 같은 검은 메모판, 마감 메모는 붉은 경보 테두리",
    lead: "메모 카드가 관제 모니터 창처럼 검고 제목은 초록 형광으로 떠요.", changes: ["관제 모니터 카드"], look: { layout: "grid2", lines: 3 },
    parts: [{ id: "memo.card", line: "solid", lineW: 1, lineC: c("#35F08F"), lineA: 40 }, { id: "memo.card.title", ink: c("#35F08F") }],
    f: [F.due("#FF5252"), F.pinned("#35F08F")], tpls: [N.nightNote, T.sbar] }),
  memo({ from: "수간호사 근무표", title: "인수인계 차트", summary: "표 판으로 보는 인수인계, 끝낸 메모는 흐리게", view: { memo: "table", memoBy: "shift" },
    lead: "메모가 병동 차트처럼 표로 정리되고 근무별로 묶여요.", changes: ["표 판", "근무별 묶기"], f: [F.done(), F.due()], tpls: [T.sbar, TPL.handover2, TPL.medicine] }),
  memo({ from: "달빛 띠 근무 달력", title: "달빛 일기장", summary: "남색 밤하늘 일기장, 오늘 메모는 달빛 테두리", look: { layout: "diary", lines: 6 },
    lead: "메모장이 밤하늘 남색 일기장이 되고 글씨는 부드러운 명조예요.", changes: ["일기장 모양"], f: [F.today("#C7D2FF"), F.old()], tpls: [TPL.sleepLog, N.oneLine] }),
  memo({ from: "타임라인 교대표", title: "교대 할 일 보드", summary: "체크 상태별 보드, 꽉 찬 칸은 주황 머리", view: { memo: "board", memoBy: "check" },
    lead: "메모가 '할 것·하는 중·끝' 보드로 서고 카드는 파랑 막대처럼 깔끔해요.", changes: ["체크 보드"], f: [F.full(), F.half("#3D5AFE")], tpls: [T.prep, N.shiftSwap] }),
  memo({ from: "새벽 4시 하늘", title: "새벽 감정 일기", summary: "새벽빛 포스트잇, 반쯤 한 메모는 노란 점선", look: { layout: "sticky", lines: 4 },
    lead: "메모가 새벽빛 포스트잇처럼 살짝 기울어 붙어요.", changes: ["포스트잇 모양"], f: [F.half(), F.today("#8E7CF0")], tpls: [TPL.mood, TPL.gratitude] }),
  memo({ from: "지하철 환승 달력", title: "환승 메모 노선도", summary: "꼬리표별 보드가 노선처럼, 급함 제목은 붉게", view: { memo: "board", memoBy: "tag" },
    lead: "꼬리표마다 노선 색 칸으로 메모가 나뉘어요.", changes: ["꼬리표 보드"], parts: [{ id: "memo.board.head", line: "solid", lineW: 3, lineC: c("#00A84D") }],
    f: [F.urgent(), F.full(6)], tpls: [N.handBag, T.todo] }),
  memo({ from: "오로라 유리 칸", title: "오로라 유리 메모", top: true, summary: "오로라 위 김 서린 유리 카드, 고정 메모는 빛나는 테두리", look: { layout: "grid2", lines: 4 },
    lead: "메모 카드가 오로라 위에 떠 있는 유리판이에요.", changes: ["유리 카드"], paint: { memoCard: "gl.80121A38.18" },
    f: [F.pinned("#5EF2C2"), F.color(2, "#B28CFF", "보라")], tpls: [N.nightNote, TPL.idea] }),
  memo({ from: "소방서 출동판", title: "출동 일지", summary: "빨강 머리 촘촘한 목록, 마감·급함이 바로 보여요", look: { layout: "compact", lines: 2 },
    lead: "메모가 출동 일지처럼 촘촘하게 쌓이고 머리는 빨강이에요.", changes: ["촘촘한 목록"], f: [F.due("#FFC400"), F.urgent("출동", "#D7261E")], tpls: [TPL.handover2, N.handBag] }),
  memo({ from: "4조2교대 큰 칸", title: "돋보기 없는 메모장", summary: "제목이 크고 줄이 넉넉한 메모장", look: { layout: "list", title: "l", lines: 5 },
    lead: "제목이 크고 줄 사이가 넉넉해 돋보기 없이 읽혀요.", changes: ["큰 제목"], parts: [{ id: "memo.card.body", size: 15 }], f: [F.done(), F.pinned()], tpls: [TPL.medicine, T.prep] }),
  memo({ from: "수면 빚 달력", title: "잠 기록 노트", summary: "잠 기록 틀과 회청색 일기, 지난 메모는 흐리게", look: { layout: "diary", lines: 5 },
    lead: "잠 기록을 남기기 좋은 회청색 일기장이에요.", changes: ["잠 일기"], f: [F.old(), F.today("#4A6A8A")], tpls: [TPL.sleepLog, N.nightNote, TPL.habit] }),
  memo({ from: "체크무늬 간호 수첩", title: "분홍 간호 수첩", summary: "분홍 체크 포스트잇, 색 1번 메모는 분홍 테", look: { layout: "sticky", lines: 4 },
    lead: "메모가 분홍 체크 수첩 위 손글씨 포스트잇이 돼요.", changes: ["손글씨 포스트잇"], f: [F.color(1, "#E8789A", "분홍"), F.done()], tpls: [T.sbar, TPL.medicine, N.shiftSwap] }),
  memo({ from: "항구 교대 해도", title: "항해 일지", summary: "해도 종이 위 표 판, 날짜별 항해 기록", view: { memo: "table" },
    lead: "메모가 오래된 항해 일지처럼 표 줄로 남아요.", changes: ["항해 표"], f: [F.old(), F.long()], tpls: [TPL.handover2, N.oneLine] }),

  // ---------- 일근 8 ----------
  memo({ from: "주간 플래너 펼침", title: "에디토리얼 노트", top: true, summary: "잡지 지면 같은 세리프 카드, 마감 메모는 붉은 테", look: { layout: "grid2", lines: 5, title: "l" },
    lead: "메모 카드가 잡지 지면처럼 큰 세리프 제목과 붉은 가는 선으로 놓여요.", changes: ["세리프 카드"],
    parts: [{ id: "memo.card", line: "solid", lineW: 0.5, lineC: c("#C0392B"), lineA: 40 }], f: [F.due("#C0392B"), F.long()], tpls: [T.meeting, T.todayPlan, TPL.weekPlan] }),
  memo({ from: "칼퇴 신호등", title: "칼퇴 할 일 신호등", summary: "체크 보드에 반쯤 한 일은 노랑, 끝낸 일은 흐리게", view: { memo: "board", memoBy: "check" },
    lead: "할 일이 신호등 색 보드로 나뉘어요.", changes: ["체크 보드"], f: [F.half("#F29900"), F.done()], tpls: [T.todayPlan, T.todo] }),
  memo({ from: "재택·출근 반반", title: "재택·출근 메모", summary: "하늘·살구 두 색 카드, 색 번호로 재택·출근 나누기", look: { layout: "grid2", lines: 3 },
    lead: "메모 카드가 하늘색(출근)과 살구색(재택) 두 테두리로 나뉘어요.", changes: ["두 색 카드"],
    f: [F.color(1, "#4A90D9", "하늘"), F.color(2, "#F08A5D", "살구")], tpls: [N.handBag, T.meeting] }),
  memo({ from: "분기 목표 연간판", title: "분기 목표 노트", summary: "목표 틀과 상태 보드, 마감 메모 붉은 테", view: { memo: "board", memoBy: "p:상태" },
    lead: "메모가 상태 칸(준비·하는 중·검토·끝)별 보드로 서요.", changes: ["상태 보드"], f: [F.due(), F.full(4)], tpls: [TPL.project, TPL.monthGoal] }),
  memo({ from: "사원증 스트랩", title: "업무 메모 카드", summary: "남색 줄무늬 머리 카드, 급함 제목 붉게", look: { layout: "list", lines: 3 },
    lead: "메모 머리가 사원증 스트랩처럼 남색 줄무늬예요.", changes: ["줄무늬 머리"], paint: { memoCard: pt("stripes", "#F4F6FB", "#E8ECF6", 120, 0) },
    f: [F.urgent(), F.today("#24408E")], tpls: [T.meeting, T.todo] }),
  memo({ from: "점심 메뉴 달력", title: "점심 메뉴 갤러리", summary: "사진 갤러리 판으로 보는 맛집 메모", view: { memo: "gallery" },
    lead: "메모가 사진 갤러리로 서서 다녀온 점심집이 한눈에 보여요.", changes: ["갤러리 판"], f: [F.pinned("#E9A23B")], tpls: [TPL.recipeNote, TPL.meal] }),
  memo({ from: "흑백 미니멀 일정", title: "미니멀 메모", summary: "흰 바탕 검은 글씨, 카드 없이 줄만", look: { layout: "list", card: "none", lines: 2 },
    lead: "카드 테두리를 덜어 내고 글자와 가는 줄만 남겼어요.", changes: ["카드 없는 목록"], f: [F.done(), F.old()], tpls: [T.todayPlan] }),
  memo({ from: "회의실 예약판", title: "회의록 정리 표", summary: "회의록 틀과 표 판, 마감 메모 붉은 테", view: { memo: "table", memoSort: "title" },
    lead: "회의록이 제목 순 표로 정리돼요.", changes: ["회의록 표"], f: [F.due()], tpls: [T.meeting, TPL.project] }),

  // ---------- 알바 6 ----------
  memo({ from: "편의점 영수증 달력", title: "영수증 메모", top: true, summary: "감열지 영수증처럼 점선으로 끊긴 메모, 고정폭 글씨",
    lead: "메모 카드가 영수증 한 장처럼 점선 테두리로 끊겨요.", changes: ["영수증 카드"], look: { layout: "list", lines: 4 },
    parts: [{ id: "memo.card", line: "dash", lineW: 1, lineC: c("#9E9E9E"), r: 2 }], f: [F.done(), F.today("#0F9D58")], tpls: [N.partShift, T.closing] }),
  memo({ from: "버블티 알바 달력", title: "버블티 레시피 노트", summary: "밀크티 색 동글 포스트잇, 레시피·근무 기록", look: { layout: "sticky", lines: 4 },
    lead: "메모가 밀크티 빛 동그란 포스트잇으로 붙어요.", changes: ["동글 포스트잇"], f: [F.pinned("#B07A55"), F.half()], tpls: [TPL.recipeNote, N.partShift] }),
  memo({ from: "투잡 두 색 달력", title: "투잡 메모 두 칸", summary: "색 번호로 두 일을 나눈 보드", view: { memo: "board", memoBy: "color" },
    lead: "메모가 두 일의 색으로 나뉜 보드에 놓여요.", changes: ["색 보드"], f: [F.full(6), F.due()], tpls: [N.partShift, TPL.sideJob] }),
  memo({ from: "픽셀 게임 출석부", title: "퀘스트 로그", summary: "8비트 게임 퀘스트처럼 체크 보드, 끝낸 퀘스트는 흐리게", view: { memo: "board", memoBy: "check" },
    lead: "할 일이 게임 퀘스트 로그처럼 네모 카드로 쌓여요.", changes: ["퀘스트 보드"], parts: [{ id: "memo.card", r: 0, line: "solid", lineW: 1, lineC: c("#5BFF7A") }],
    f: [F.done(), F.half("#FFD54F")], tpls: [TPL.habit, TPL.study2] }),
  memo({ from: "주말 알바 도장판", title: "주말 도장 메모", summary: "도장판 같은 동글 카드, 주말 계획 틀", look: { layout: "grid2", lines: 2 },
    lead: "메모가 도장 칸처럼 두 줄로 동글동글 놓여요.", changes: ["두 줄 동글 카드"], f: [F.done(), F.color(1, "#E85A8A", "분홍")], tpls: [N.weekend, N.partShift] }),
  memo({ from: "새벽 배송 루트", title: "배송 루트 메모", summary: "어두운 길 위 형광 카드, 급함 제목 형광 주황", look: { layout: "compact", lines: 2 },
    lead: "메모가 밤길 내비처럼 촘촘하게 쌓이고 제목은 형광 연두예요.", changes: ["밤길 촘촘 목록"], f: [F.urgent("급함", "#FF7A59"), F.old()], tpls: [N.partShift, TPL.sideJob] }),

  // ---------- 현장 5 ----------
  memo({ from: "공사 일보 칠판", title: "현장 일보 칠판", top: true, summary: "분필 글씨 칠판 메모, 작업 전 안전 점검 틀", look: { layout: "grid2", lines: 4 },
    lead: "메모 카드가 현장 칠판이 되고 글씨는 분필 붓글씨예요.", changes: ["칠판 카드"], f: [F.done(), F.due("#FF8A65")], tpls: [T.fieldLog, N.siteSafe, TPL.sitePhoto] }),
  memo({ from: "철근 도면 달력", title: "도면 작업 표", summary: "청사진 표 판, 공정별 메모", view: { memo: "table" },
    lead: "메모가 청사진 도면처럼 흰 선 표로 놓여요.", changes: ["도면 표"], f: [F.long(), F.old()], tpls: [T.fieldLog, TPL.sitePhoto] }),
  memo({ from: "안전 제일 표지판", title: "안전 점검 메모", summary: "노랑·검정 표지판 카드, 마감 메모 붉은 테", look: { layout: "list", lines: 3 },
    lead: "메모 카드가 안전 표지처럼 노랑 테두리로 둘러져요.", changes: ["표지판 카드"], parts: [{ id: "memo.card", line: "solid", lineW: 2, lineC: c("#FFD100") }],
    f: [F.due(), F.done()], tpls: [N.siteSafe, T.safety] }),
  memo({ from: "일당 봉투 달력", title: "일당 수첩", summary: "누런 봉투 종이 일기, 하루 일한 기록", look: { layout: "diary", lines: 5 },
    lead: "메모장이 누런 봉투 종이 일기장이 돼요.", changes: ["봉투 종이 일기"], f: [F.old(), F.long()], tpls: [T.fieldLog, N.oneLine] }),
  memo({ from: "해 뜨는 비계", title: "새벽 공정 보드", summary: "상태별 공정 보드, 꽉 찬 칸 주황", view: { memo: "board", memoBy: "p:상태" },
    lead: "공정 메모가 상태 칸별 보드로 서요.", changes: ["공정 보드"], f: [F.full(4), F.done()], tpls: [TPL.project, N.siteSafe] }),

  // ---------- 자영업 5 ----------
  memo({ from: "매출 온도계 달력", title: "가게 운영 보드", top: true, summary: "손님 요청 상태 보드, 마감 메모 붉은 테", view: { memo: "board", memoBy: "p:상태" },
    lead: "손님 요청이 받음·준비·완료 보드로 나뉘어요.", changes: ["요청 보드"], f: [F.due(), F.full(5, "#E8483B")], tpls: [N.shopNote, T.shopOpen, T.closing] }),
  memo({ from: "꽃집 계절 달력", title: "꽃집 주문 카드", summary: "꽃잎 분홍 포스트잇, 주문·단골 메모", look: { layout: "sticky", lines: 4 },
    lead: "주문 메모가 꽃잎 분홍 포스트잇으로 붙어요.", changes: ["꽃잎 포스트잇"], f: [F.due("#D9668A"), F.pinned()], tpls: [TPL.customer, T.order] }),
  memo({ from: "오마카세 예약 장부", title: "예약 손님 장부", summary: "먹색 표 판에 금박 제목, 손님 메모", view: { memo: "table", memoSort: "title" },
    lead: "손님 메모가 먹색 장부 표로 정리돼요.", changes: ["먹색 표"], f: [F.today("#D4AF63")], tpls: [TPL.customer, N.shopNote] }),
  memo({ from: "장날 도장 달력", title: "장날 재고 수첩", summary: "메모 달력 판으로 보는 재고 점검", view: { memo: "cal" },
    lead: "메모가 장날 달력 판에 놓여 언제 무엇을 챙겼는지 보여요.", changes: ["메모 달력 판"], f: [F.done()], tpls: [TPL.stock, T.closing] }),
  memo({ from: "1인 카페 운영표", title: "카페 레시피 카드", summary: "원두색 세리프 카드 두 줄, 레시피·마감 틀", look: { layout: "grid2", lines: 4 },
    lead: "메모 카드가 원두 빛 세리프 레시피 카드가 돼요.", changes: ["레시피 카드"], f: [F.pinned("#6F4E37"), F.long()], tpls: [TPL.recipeNote, T.closing, TPL.stock] }),

  // ---------- 투자 4 ----------
  memo({ from: "캔들 차트 달력", title: "차트 매매 일지", top: true, summary: "검은 차트 화면 표 판, 원칙 점검 틀", view: { memo: "table" },
    lead: "매매 일지가 차트 화면처럼 검은 표로 남아요.", changes: ["차트 표"], f: [F.old(), F.long()], tpls: [T.trade, N.rule] }),
  memo({ from: "적립식 씨앗 달력", title: "씨앗 관찰 노트", summary: "새싹 색 일기장, 관심 목록 틀", look: { layout: "diary", lines: 4 },
    lead: "메모장이 새싹 연두 관찰 일기가 돼요.", changes: ["관찰 일기"], f: [F.today("#6BA34A")], tpls: [TPL.watchlist, N.rule] }),
  memo({ from: "배당 금화 달력", title: "배당 기록 카드", summary: "금빛 세리프 카드, 고정 메모 금테", look: { layout: "grid2", lines: 3 },
    lead: "메모 카드가 금화처럼 세리프 금빛 제목으로 놓여요.", changes: ["금빛 카드"], f: [F.pinned("#B8860B")], tpls: [TPL.dividend, T.rebalance] }),
  memo({ from: "매매 일지 모눈", title: "모눈 원칙 노트", summary: "모눈 종이 손글씨, 원칙 점검 틀", look: { layout: "list", lines: 5 },
    lead: "메모가 모눈 종이 위 손글씨로 적혀요.", changes: ["모눈 손글씨"], f: [F.done(), F.long()], tpls: [N.rule, T.trade] }),

  // ---------- 모든 유형 10 ----------
  memo({ from: "비 오는 날 유리창", title: "빗소리 일기", summary: "빗방울 유리 위 일기장, 오늘 메모는 등불 테", look: { layout: "diary", lines: 6 },
    lead: "메모장 뒤로 빗방울이 흐르고 글씨는 등불처럼 따뜻해요.", changes: ["빗방울 일기"], f: [F.today("#FFC774"), F.old()], tpls: [TPL.mood, N.oneLine] }),
  memo({ from: "봄 햇살 테라조", title: "테라조 습관 카드", summary: "봄빛 테라조 두 줄 카드, 습관 체크", look: { layout: "grid2", lines: 2 },
    lead: "메모 카드가 테라조 돌판처럼 두 줄로 놓여요.", changes: ["두 줄 카드"], f: [F.done(), F.half("#F28C8C")], tpls: [TPL.habit, TPL.workout] }),
  memo({ from: "뉴욕 신문 달력", title: "신문 스크랩북", summary: "신문 지면 같은 갤러리, 굵은 제목", view: { memo: "gallery" },
    lead: "메모가 신문 스크랩처럼 굵은 제목과 사진으로 서요.", changes: ["스크랩 갤러리"], f: [F.pinned("#111111")], tpls: [TPL.idea, T.reading] }),
  memo({ from: "네온 사인 달력", title: "네온 아이디어 벽", summary: "네온 분홍 포스트잇, 급함은 네온 주황", look: { layout: "sticky", lines: 3 },
    lead: "메모가 네온 사인처럼 빛나는 포스트잇이 돼요.", changes: ["네온 포스트잇"], parts: [{ id: "memo.card.title", tshadow: 6, tshadowC: c("#FF4FB5"), tshadowX: 0, tshadowY: 0 }],
    f: [F.urgent("급함", "#FFB74D"), F.color(3, "#4FD8FF", "하늘")], tpls: [TPL.idea, TPL.project] }),
  memo({ from: "숲속 오두막 달력", title: "오두막 독서 일기", summary: "나무 결 일기장, 독서 기록 틀", look: { layout: "diary", lines: 5 },
    lead: "메모장이 오두막 나무 결 위 일기장이에요.", changes: ["나무 결 일기"], f: [F.long(), F.old()], tpls: [T.reading, TPL.gratitude] }),
  memo({ from: "우주 정거장 달력", title: "정거장 미션 보드", summary: "별빛 위 체크 보드, 꽉 찬 칸 보라 머리", view: { memo: "board", memoBy: "check" },
    lead: "할 일이 정거장 미션처럼 체크 보드에 떠요.", changes: ["미션 보드"], f: [F.full(5, "#A78BFA"), F.done()], tpls: [TPL.project, T.todo] }),
  memo({ from: "레트로 체크 피크닉", title: "피크닉 준비 목록", summary: "빨강 체크 카드, 여행·장보기 틀", look: { layout: "grid2", lines: 4 },
    lead: "메모 카드가 피크닉 체크 천 위에 놓여요.", changes: ["체크 천 카드"], paint: { memoCard: pt("check", ["#FFD9D0", 0x90], "#FFF4F0", 60) },
    f: [F.done(), F.half("#D7263D")], tpls: [TPL.travel, TPL.grocery, N.weekend] }),
  memo({ from: "독서 기록 서가", title: "서가 독서 카드", top: true, summary: "명조 갤러리로 꽂힌 책 메모, 다 읽은 책은 흐리게", view: { memo: "gallery" },
    lead: "독서 메모가 서가에 꽂힌 책처럼 갤러리로 서요.", changes: ["서가 갤러리"], f: [F.done(), F.long()], tpls: [T.reading, TPL.wishlist] }),
  memo({ from: "운동 출석 히트맵", title: "운동 기록 달력", summary: "메모 달력 판에 운동 기록, 끝낸 날 흐리게", view: { memo: "cal" },
    lead: "운동 메모가 달력 판에 놓여 출석이 보여요.", changes: ["메모 달력 판"], f: [F.done(), F.today("#1F9D55")], tpls: [TPL.workout, TPL.habit] }),
  memo({ from: "낮잠 고양이 달력", title: "고양이 돌봄 수첩", summary: "살구빛 동글 포스트잇, 반려동물 돌봄 틀", look: { layout: "sticky", lines: 4 },
    lead: "메모가 낮잠 자는 고양이처럼 부드러운 살구빛 포스트잇이에요.", changes: ["살구빛 포스트잇"], f: [F.done(), F.pinned("#E89A6B")], tpls: [TPL.petCare, TPL.cleaning] }),
];
