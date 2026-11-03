// 기능별 · 메모 22 (기획서 4-5, 4-10 1~7). 메모장 하나만 바꿔요.
// 지금 도는 것: 메모 틀의 {수식}(틀을 쓸 때 그날 값으로 채워짐), 입력 칸이 쓰는 '칸: 값' 속성으로 나누는 보드·정렬하는 표.
// memo.card의 when 줄은 1.36 고침(날짜 있는 부품 조건)이 합쳐지면 돌아요.
import { c, H, ALL, recipe, memoLook, memoTpl, tabView, parts, decor, bgShape } from "./lib.mjs";
import { D, T } from "./kit.mjs";

const NOTE = "메모장 모양과 틀만 더해요. 있던 메모는 그대로이고, 틀은 쓸 때만 메모가 돼요";

function memo(s) {
  const p = { bg: "#FFFFFF", card: "#FFFFFF", ink: "#222222", accent: "#3F51B5", ...s.p };
  const lines = [
    { id: "memo.head", ...(p.headPaint ? { paint: p.headPaint } : { fill: c(p.bg) }), pad: 8, ...(p.titleFont ? { font: p.titleFont } : {}), ink: c(p.headInk ?? p.ink) },
    { id: "memo.card", fill: c(p.card), r: p.r ?? 14, shadow: p.shadow ?? 1, ...(p.cardLine ? { line: "solid", lineW: 1, lineC: c(p.line ?? "#E0E0E0") } : {}) },
    { id: "memo.card.title", weight: 700, ...(p.memoFont ? { font: p.memoFont } : {}) },
    { id: "memo.tags", ink: c(p.accent) },
    ...(s.parts ?? []), ...(s.dated ?? []),
  ];
  const pieces = [
    { kind: "screenLook", screens: `memo~bg:${H(p.bg)}|cd:${H(p.card)}|ik:${H(p.ink)}|ac:${H(p.accent)}${p.bgPaint ? `|fl:${p.bgPaint}` : ""}` },
    memoLook(p, s.look), s.tpls?.length ? memoTpl(s.tpls) : null, s.view ? tabView(s.view) : null, parts(lines),
    s.decor?.length ? decor(s.decor) : null, ...(s.extra ?? []),
  ];
  return {
    recipe: recipe({ title: s.title, summary: s.summary, wow: s.wow, who: s.who, changes: s.changes, note: s.note ?? NOTE, styles: s.styles ?? ALL, cover: s.cover, pieces }),
    ...(s.showcase ? { showcase: true } : {}),
  };
}

export default [
  // ---- 기능 속 기능 ----
  memo({
    title: "습관 기록표", summary: "매주 월요일 새로 생기는 습관 체크와 체크 상태 보드",
    wow: "매주 월요일 이번 주 습관 체크가 새로 생기고(다른 날은 틀로 바로), 메모장은 '다 함·하는 중' 보드로 나뉘어 오늘 남은 습관만 보여요.",
    changes: ["습관 체크 틀(매주 월요일 저절로)", "체크 상태별 보드", "다 끝낸 카드 흐리게(고침 뒤)"], cover: ["#E8F5E9", "#43A047", "🔥"],
    p: { bg: "#F5FBF5", accent: "#43A047", titleFont: "jua", memoFont: "jua" },
    tpls: [{ title: "오늘 습관", emoji: "🔥", check: ["물 여덟 잔", "스트레칭", "책 10쪽", "일찍 자기"], repeat: "W1" }], view: { memo: "board", memoBy: "check" }, dated: [D.memoDone()],
  }),
  memo({
    title: "독서 진도판", summary: "읽은 쪽·전체 쪽을 칸으로 적고, 많이 읽은 책이 표 맨 위로",
    wow: "독서 틀의 '읽은 쪽' 칸을 고치면 표가 많이 읽은 순으로 다시 서요. 별점 칸으로 다 읽은 책을 매기면 별점 순으로도 볼 수 있어요.",
    changes: ["독서 기록 틀(읽은 쪽·전체 쪽·별점)", "읽은 쪽 많은 순 표", "도서관 갈색 화면"], cover: ["#EFEBE9", "#6D4C41", "📚"],
    p: { bg: "#FAF6F2", card: "#FFFFFF", accent: "#6D4C41", memoFont: "nanummyeongjo", cardLine: true },
    tpls: [T.reading], view: { memo: "table", memoSort: "-p:읽은 쪽" }, look: { layout: "list" },
  }),
  memo({
    title: "공부 시간 합산", summary: "공부 기록의 '시간' 칸으로 표를 시간 큰 순으로",
    wow: "공부할 때마다 틀에 시간만 적으면 표가 오래 공부한 순으로 쌓여요. 과목은 꼬리표로 나눠 보드로도 볼 수 있어요.",
    changes: ["공부 기록 틀(시간 칸)", "시간 큰 순 표", "꼬리표 색"], cover: ["#E3F2FD", "#1565C0", "⏳"],
    p: { bg: "#F6FAFF", accent: "#1565C0", titleFont: "dohyeon" }, tpls: [T.study], view: { memo: "table", memoSort: "-p:시간" },
  }),
  memo({
    title: "할 일 우선순위", summary: "할 일 틀의 마감·상태 칸으로, 마감 빠른 순 표와 상태 보드",
    wow: "할 일 틀로 만든 메모는 '마감' 칸이 가까운 순으로 표 맨 위에 서고, 마감이 적힌 카드는 빨간 테두리가 둘러요(고침 뒤).",
    changes: ["할 일 틀(상태·마감)", "마감 빠른 순 표", "마감 카드 테두리(고침 뒤)"], cover: ["#FFEBEE", "#E53935", "📌"],
    p: { bg: "#FFFAFA", accent: "#E53935" }, tpls: [T.todo], view: { memo: "table", memoSort: "p:마감" }, dated: [D.memoDue(), D.memoPinned()],
  }),
  memo({
    title: "감정 일기 통계", summary: "하루 일기의 기분 별점으로, 기분 낮은 날부터 다시 보기",
    wow: "일기 틀의 기분 별점이 쌓이면 표가 기분 낮은 날부터 보여 줘서, 힘들었던 날을 다시 돌아보기 쉬워요. 틀에는 그날 날짜·요일이 저절로 들어가요.",
    changes: ["하루 일기 틀(기분 별점·날짜 저절로)", "기분 낮은 순 표", "부드러운 분홍"], cover: ["#FCE4EC", "#AD1457", "💭"],
    p: { bg: "#FFF8FA", accent: "#AD1457", memoFont: "gaegu" }, tpls: [{ title: "하루 일기", emoji: "🌙", body: "{날짜} {요일}\n기분: \n고마운 것: ", asks: [{ label: "기분", type: "star" }] }],
    view: { memo: "table", memoSort: "p:기분" },
  }),
  memo({
    title: "인수인계 미완료", styles: ["SHIFT", "DAY", "HOURLY"], summary: "체크가 남은 인수인계만 '남음' 칸에, 틀에는 근무 시각이 저절로",
    wow: "메모장이 체크 상태 보드로 바뀌어 아직 남은 인수인계만 한 칸에 모여요. 인수인계 틀을 쓰면 그날 근무 이름과 시작~끝 시각이 저절로 들어가요.",
    changes: ["인수인계 틀(근무 시각 저절로)", "체크 상태 보드", "다 끝낸 카드 흐리게(고침 뒤)"], cover: ["#E0F2F1", "#00796B", "🩺"],
    p: { bg: "#F3FAF9", accent: "#00796B", cardLine: true }, tpls: [T.sbar], view: { memo: "board", memoBy: "check" }, dated: [D.memoDone()],
  }),
  memo({
    title: "장보기 금액표", summary: "장보기 메모의 금액 칸으로, 비싼 것부터 표에",
    wow: "장보기 틀에 품목과 금액만 적으면 표가 금액 큰 순으로 서서 어디서 많이 쓰는지 바로 보여요.",
    changes: ["장보기 틀(금액 칸)", "금액 큰 순 표", "시장 바구니 노랑"], cover: ["#FFFDE7", "#F9A825", "🛒"],
    p: { bg: "#FFFDF4", accent: "#F9A825" }, tpls: [{ title: "장보기", emoji: "🛒", body: "품목: \n금액: \n가게: ", asks: [{ label: "금액", type: "money" }] }],
    view: { memo: "table", memoSort: "-p:금액" },
  }),
  memo({
    title: "근무 회고 틀", styles: ["SHIFT", "DAY", "HOURLY"], summary: "틀을 열면 오늘 근무·이번 주 야간·이달 쓴 돈이 저절로 채워지는 회고",
    wow: "회고 틀을 쓰는 순간 '오늘 야간 9시간, 이번 주 야간 3번, 4일째, 이달 쓴 돈'이 그날 값으로 채워져요. 컨디션 별점과 잠 시간은 직접 골라요.",
    changes: ["근무 회고 틀(그날 값 저절로)", "수면 기록 틀", "컨디션 낮은 순 표"], cover: ["#EDE7F6", "#5E35B1", "📝"],
    p: { bg: "#F8F6FD", accent: "#5E35B1" }, tpls: [T.shiftReview, T.sleep], view: { memo: "table", memoSort: "p:컨디션" },
  }),
  // ---- 판·틀 ----
  memo({
    title: "갤러리 무드보드", summary: "메모를 큰 카드 갤러리로, 색 번호별로 모아 보기",
    wow: "메모장이 사진첩 같은 갤러리가 되고, 카드 색을 바꾸면 같은 색끼리 모여요. 무드보드 틀로 영감을 한 장씩 모아요.",
    changes: ["갤러리 판", "색별 보드로 바꿔 보기", "무드보드 틀"], cover: ["#FCE4EC", "#7E57C2", "🖼️"],
    p: { bg: "#FBFAFF", accent: "#7E57C2", r: 20 }, tpls: [{ title: "무드보드", emoji: "🖼️", body: "느낌: \n색: \n어디에 쓸까: " }], view: { memo: "gallery", memoBy: "color" }, look: { layout: "grid3" },
  }),
  memo({
    title: "회의록", styles: ["DAY", "SHIFT", "BUSINESS"], summary: "회의록 틀에 날짜·요일이 저절로, 마감 칸으로 할 일 순 표",
    wow: "회의록 틀을 열면 오늘 날짜와 요일이 채워지고, 정한 일의 마감 칸으로 표가 마감 빠른 순으로 서요.",
    changes: ["회의록 틀(날짜 저절로·마감)", "마감 순 표", "차분한 회색"], cover: ["#ECEFF1", "#455A64", "🗒️"],
    p: { bg: "#F6F8F9", accent: "#455A64", cardLine: true, shadow: 0 }, tpls: [T.meeting], view: { memo: "table", memoSort: "p:마감" },
  }),
  memo({
    title: "아침 저녁 체크", summary: "아침·저녁 체크 틀과 남은 체크만 모아 보는 보드",
    wow: "아침·저녁 체크 틀을 누르면 그날 목록이 생기고(매주 월요일엔 저절로), 메모장은 남은 체크가 있는 것과 다 끝낸 것으로 나뉘어요.",
    changes: ["아침·저녁 체크 틀(매주 반복)", "체크 상태 보드"], cover: ["#E1F5FE", "#0288D1", "☑️"],
    p: { bg: "#F5FBFF", accent: "#0288D1" },
    tpls: [{ title: "아침 체크", emoji: "🌅", check: ["물 한 잔", "약", "오늘 할 일 보기"], repeat: "W1" }, { title: "저녁 체크", emoji: "🌙", check: ["내일 준비", "가계부 적기", "일찍 눕기"], repeat: "W1" }],
    view: { memo: "board", memoBy: "check" },
  }),
  memo({
    title: "레시피 카드", summary: "요리 레시피를 카드로, 별점 높은 순",
    wow: "레시피 틀에 재료·순서·별점을 적으면 카드가 별점 높은 순으로 서요. 장보기 재료는 체크 목록이라 장 볼 때 그대로 써요.",
    changes: ["레시피 틀(별점·재료 체크)", "별점 높은 순 표", "주방 크림색 카드"], cover: ["#FFF3E0", "#E64A19", "🍳"],
    p: { bg: "#FFF9F3", accent: "#E64A19", memoFont: "gaegu", r: 18 },
    tpls: [{ title: "레시피", emoji: "🍳", body: "몇 인분: \n시간: \n별점: ", check: ["재료 1", "재료 2", "재료 3"], asks: [{ label: "별점", type: "star" }] }],
    view: { memo: "table", memoSort: "-p:별점" },
  }),
  memo({
    title: "운동 기록", summary: "운동 틀의 시간·강도 칸, 오래 한 날이 표 위로",
    wow: "운동 틀에 종목·시간·강도를 고르면 표가 오래 운동한 순으로 서요. 매주 반복 체크로 빠진 요일이 보여요.",
    changes: ["운동 틀(시간·강도)", "시간 큰 순 표", "에너지 주황"], cover: ["#FFF3E0", "#F4511E", "🏃"],
    p: { bg: "#FFFAF5", accent: "#F4511E" },
    tpls: [{ title: "운동", emoji: "🏃", body: "종목: \n시간: \n강도: 보통", asks: [{ label: "시간", type: "num" }, { label: "강도", type: "pick", choices: ["가볍게", "보통", "세게"] }] }],
    view: { memo: "table", memoSort: "-p:시간" },
  }),
  memo({
    title: "투자 원칙 카드", styles: ["INVEST"], summary: "원칙 카드를 맨 위에, 매매 일지는 감정 점수 순",
    wow: "투자 원칙 네 줄이 메모장 맨 위에 고정되고, 매매 일지는 감정 점수 낮은 순으로 모여 흔들렸던 때를 다시 보게 해요.",
    changes: ["투자 원칙 네 줄", "매매 일지 틀(감정 점수·원칙 지킴)", "감정 점수 순 표"], cover: ["#1C2833", "#F4D03F", "📜"],
    p: { bg: "#F8F7F3", accent: "#2E4053", memoFont: "nanummyeongjo", cardLine: true },
    tpls: [{ title: "매매 일지", emoji: "📈", body: "산·판 이유: \n원칙 지켰나: \n마음: ", asks: [{ label: "감정 점수", type: "star" }, { label: "원칙 지킴", type: "check" }] }],
    view: { memo: "table", memoSort: "p:감정 점수" },
    extra: [{ kind: "invest", rules: [{ text: "산 이유를 한 줄로 쓸 수 있을 때만 산다" }, { text: "나눠 산다" }, { text: "빚내서 사지 않는다" }, { text: "뉴스 본 날은 하루 기다린다" }], journal: ["산 이유", "판 이유", "감정 점수"] }],
  }),
  memo({
    title: "가게 발주 보드", styles: ["BUSINESS"], summary: "발주 메모를 상태(주문 전·주문함·받음) 보드로",
    wow: "발주 틀로 메모를 만들면 상태 칸으로 '주문 전·주문함·받음' 보드가 나뉘어, 아직 안 온 것만 한눈에 보여요.",
    changes: ["발주 틀(상태 고르기·매주)", "상태별 보드", "크래프트 색"], cover: ["#D7CCC8", "#5D4037", "📦"],
    p: { bg: "#F7F1EA", accent: "#6D4C41", bgPaint: "tx.kraft.FFF7F1EA.40" },
    tpls: [{ title: "발주", emoji: "📦", body: "상태: 주문 전\n거래처: \n품목: ", repeat: "W1", asks: [{ label: "상태", type: "pick", choices: ["주문 전", "주문함", "받음"] }] }],
    view: { memo: "board", memoBy: "p:상태" },
  }),
  memo({
    title: "현장 작업 일지", styles: ["FIELD"], summary: "현장 일지에 날짜·일한 시간이 저절로, 현장 칸별 보드",
    wow: "현장 일지 틀을 열면 그날 날짜와 일한 시간이 채워지고, 메모장은 '현장' 칸으로 나뉜 보드가 돼요.",
    changes: ["현장 일지 틀(날짜·시간 저절로)", "현장별 보드", "청사진 파랑"], cover: ["#0D47A1", "#E3F2FD", "📒"],
    p: { bg: "#F3F7FC", accent: "#1565C0", cardLine: true }, tpls: [T.fieldLog, T.invoice], view: { memo: "board", memoBy: "p:현장" },
  }),
  memo({
    title: "공부 플래너", summary: "과목 꼬리표 보드와 공부 틀, 시험 D-day 메모",
    wow: "메모장이 과목 꼬리표 보드로 나뉘고, 공부 틀의 시간이 쌓여요. 시험 틀에 날짜를 고르면 시험 날 칸으로 표가 가까운 순이에요.",
    changes: ["과목 꼬리표 보드", "공부·시험 틀", "형광펜 노랑"], cover: ["#FFF9C4", "#1E88E5", "🎓"],
    p: { bg: "#FFFEF5", accent: "#1E88E5", memoFont: "nanumpen" },
    tpls: [T.study, { title: "시험", emoji: "📝", body: "과목: \n시험 날: \n범위: ", asks: [{ label: "시험 날", type: "date" }] }], view: { memo: "board", memoBy: "tag" },
  }),
  // ---- 모양 ----
  memo({
    title: "포스트잇 벽", summary: "메모가 알록달록 포스트잇으로, 고정 메모는 금색 테두리",
    wow: "메모가 벽에 붙인 포스트잇처럼 기울어진 색 카드가 되고, 고정한 메모는 금색 테두리(고침 뒤)로 맨 위에 남아요.",
    changes: ["포스트잇 판", "손글씨", "고정 메모 금색(고침 뒤)"], cover: ["#FFF59D", "#FF8A80", "🟨"],
    p: { bg: "#FAF7F0", card: "#FFF59D", accent: "#FF7043", memoFont: "gaegu", r: 2, shadow: 3 }, look: { layout: "sticky", card: "own", corner: 2 },
    tpls: [T.todayPlan], dated: [D.memoPinned()],
  }),
  memo({
    title: "손글씨 노트", summary: "줄 노트 위 손글씨, 일기 틀은 날짜가 저절로",
    wow: "메모장이 줄 노트가 되고 모든 글이 손글씨로 보여요. 일기 틀을 열면 그날 날짜·요일이 먼저 적혀 있어요.",
    changes: ["줄 노트 바탕", "손글씨 글꼴", "일기 틀"], cover: ["#FFFDE7", "#90CAF9", "✏️"],
    p: { bg: "#FFFEF6", accent: "#42A5F5", memoFont: "nanumpen", bgPaint: "pt.thin.FFFFFEF6.FFE3F2FD.80.0", r: 4, shadow: 0 }, look: { layout: "diary" },
    tpls: [{ title: "일기", emoji: "📖", body: "{날짜} {요일}\n" }],
  }),
  memo({
    title: "미니멀 목록", summary: "카드 없이 한 줄 목록, 제목 순으로 조용하게",
    wow: "카드를 모두 걷어내고 한 줄 목록만 남겨요. 제목 순으로 서서 찾기 쉽고, 체크 틀은 한 줄짜리예요.",
    changes: ["카드 없는 한 줄 목록", "제목 순", "흑백"], cover: ["#FFFFFF", "#212121", "➖", 0],
    p: { bg: "#FFFFFF", accent: "#212121", shadow: 0, r: 0 }, look: { layout: "compact", card: "none", border: "none" }, view: { memo: "list", memoSort: "title" },
    tpls: [{ title: "메모", emoji: "➖", check: ["할 것"] }],
  }),
  memo({
    title: "주간 회고 노트", summary: "금요일마다 반복되는 회고 틀, 이번 주 일한 시간과 쓴 돈이 저절로",
    wow: "주간 회고 틀이 매주 금요일 반복되고, 쓰는 순간 이번 주 일한 시간과 이달 쓴 돈이 채워져요.",
    changes: ["주간 회고 틀(매주 금)", "그날 값 저절로", "차분한 남색"], cover: ["#E8EAF6", "#283593", "🗓️"],
    p: { bg: "#F7F8FD", accent: "#283593" }, tpls: [{ ...T.weekRetro, repeat: "W5" }], view: { memo: "list" },
  }),
  memo({
    title: "감사 일기", summary: "세 가지 감사와 기분 별점, 좋았던 날부터 다시 보기",
    wow: "감사 일기 틀이 매주 일요일 저절로 생기고 다른 날은 틀로 바로 써요. 기분 별점이 높은 날부터 표로 모아 좋은 날을 다시 꺼내 봐요.",
    changes: ["감사 일기 틀(매주 일요일 저절로, 세 가지 체크)", "기분 높은 순 표", "따뜻한 살구색"], cover: ["#FFE0B2", "#EF6C00", "🙏"],
    p: { bg: "#FFF9F2", accent: "#EF6C00", memoFont: "gowunbatang" },
    tpls: [{ title: "감사 일기", emoji: "🙏", body: "{날짜}\n기분: ", check: ["고마운 것 하나", "고마운 것 둘", "고마운 것 셋"], repeat: "W7", asks: [{ label: "기분", type: "star" }] }],
    view: { memo: "table", memoSort: "-p:기분" },
  }),
];
