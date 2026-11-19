// 1.39 [MKW-07] 프로 작품 (사용자 11-07 21:10 '최고 전문 디자이너 수준', 21:35 '디자인·편의·기능·위젯이 기본틀과 확연히 다르게').
// 무료 테마(themes-*.mjs)는 모양과 1.38 규칙까지예요. 프로 작품은 한 사람의 하루를 하나의 '도구'로 묶어요:
//   ① 그 일에만 있는 숫자를 세는 값 칸(FX-03) ② 앱을 열지 않고 누르는 홈 화면 단추 위젯·빠른 설정·바로가기(FX-04, 목표 막대·지난 7기간 FX-14)
//   ③ 알림 안의 단추(FX-05) ④ 이어지는 동작·아니면(FX-11) ⑤ 값이 목표에 닿으면(FX-12)·근무 중 몇 분마다(FX-15) ⑥ 알람을 끄면(FX-21)
// 모양은 무료 테마와 겹치지 않는 미술 방향 하나씩. 근무 판단(대근·특근)은 앱이 하는 근무종류·근무시간을 그대로 읽어요(스튜디오 연동 스레드 #365).
import { c } from "../../market-136/make/lib.mjs";
import { C, T } from "../../market-136/make/kit.mjs";
import { theme, cellLook, lg, rg, sg, pt, tx, gl, so, sh } from "./art.mjs";

/** A button with an id, so a 알림 can carry it (then). */
const btn = (id, label, emoji, where, act, more) => ({ id, label, emoji, where, ...act, ...(more ? { more } : {}) });
/**
 * 달력 머리의 '바로 단추' (꾸밈 층 tap): 무료 테마엔 없는 프로 작품의 얼굴. 홈 화면 위젯과 같은 단추를 달력 위에서도 한 번에 눌러요.
 */
// 그려 보기: 제목 줄 가운데에 두면 큰 제목 글꼴과 겹치고, 머리 칠(줄무늬·단색) 아래에 깔리면 안 보여요. 머리 아랫단 여백(꾸밈 글씨가 있으면 앱이
// 위아래를 넉넉히 둬요)의 오른쪽에, 칠 위로(over) 올려요. 그림자는 글씨가 두 번 찍혀 보여 빼요.
const chip = (tap, text, fill, ink, o = {}) => ({ shape: "pill", at: "cal.head", x: o.x ?? 850, y: o.y ?? 690, w: o.w ?? 104, h: 26, r: 13, fill: c(fill), fillA: o.fillA ?? 100,
  ...(o.line ? { line: "solid", lineW: 1.5, lineC: c(o.line) } : {}), text, size: 11, weight: 800, ink: c(ink), over: true, tap });

export default [
  // ---------- 교대: 야간 회복 ----------
  theme({
    title: "야간 회복 스테이션", style: "SHIFT", mood: "어두운", emoji: "🛰️", top: true, kitRules: false,
    summary: "홈 화면에서 물·잠을 한 번에 적고, 야간 끝엔 회복 순서가 알림으로 오는 교대 프로",
    wow: "깊은 남색 화면에 상태등처럼 근무가 떠요. 홈 화면 단추로 물 한 잔·잠들기를 앱을 열지 않고 적고, 물 6잔을 채우면 막대가 차올라요. 야간이 끝나면 '잠들기' 단추가 달린 알림이 오고, 알람을 끄면 몇 시간 잤는지 알려 줘요. 이달 야간 수는 저절로 세어 메모에 남아요.",
    changes: ["홈 화면 물·잠 단추(목표 막대·지난 7일)", "야간 끝 알림 속 '잠들기' 단추", "알람 끄면 잔 시간", "이달 야간 자동 세기", "근무 중 2시간마다 물(근무 시간에만)", "상태등 칸·궤도 머리"],
    p: { bg: "#070B1A", card: "#0F1630", ink: "#E6ECFF", sub: "#93A0C8", accent: "#7CF0FF", onAccent: "#04121A", line: "#1E2A52", dark: true,
      sat: "#8FB4FF", sun: "#FF8A9A", day: "#7CF0FF", eve: "#FFC27A", night: "#B49CFF", off: "#3A4366", today: "#7CF0FF", income: "#7CF0C0", expense: "#FF9AA8" },
    font: { body: "ibmplexsanskr", title: "orbit", date: "spacemono", memo: "ibmplexsanskr", num: "jetbrainsmono" },
    paint: {
      screen: rg(80, 0, 140, ["#1B2A6B"], ["#070B1A", 255, 75]),
      card: gl("#0F1630", 0xE6, 18),
      head: lg(90, ["#7CF0FF", 0x22], ["#B49CFF", 0x18], ["#070B1A", 0x00]),
      tabs: gl("#0B1124", 0xF0, 20),
      today: rg(50, 50, 70, ["#7CF0FF", 0x50], ["#7CF0FF", 0x00]),
      total: lg(120, "#18265A", "#2B1C5E"),
      paper: tx("noise", "#0F1630", 20),
    },
    r: { card: 18, cell: 12, tabs: 26, btn: 20 },
    glow: "#7CF0FF", titleSize: 26, titleTrack: 2, weekTrack: 16,
    tabsLine: { c: "#26346A" }, cardLine: { c: "#1E2A52" },
    cal: { size: "tall", badge: "small" },
    looks: [
      cellLook({ cr: 12, dp: "tl", ds: "m", dw: "b", mk: "dot", mp: "below", pd: 2, bg: "#0F1630", ba: 80 }),
      cellLook({ cr: 12, dp: "tl", ds: "m", dw: "b", dc: "#B49CFF", mk: "dot", mp: "below", pd: 2, fl: lg(180, ["#B49CFF", 0x10], ["#B49CFF", 0x40]) }),
      cellLook({ cr: 12, dp: "tl", ds: "m", dc: "#4B557A", mk: "none", pd: 2 }),
    ],
    pattern: "shift:night=1,off=2",
    roleIcons: { day: "sun", eve: "sunset", night: "moon", off: "sleepmask" },
    cells: [
      { id: "cal.cell", when: '근무종류 = "야간" 그리고 연속근무 >= 3', line: "solid", lineW: 2, lineC: c("#B49CFF"), motion: "pulse" },
      { id: "cal.cell", when: "쉬는날 그리고 이번주야간 >= 3", fill: c("#7CF0C0"), fillA: 18 },
      { id: "cal.cell.date", when: "연속근무 >= 5", ink: c("#FF8A9A"), weight: 900 },
      C.holidayWork("#FFC27A"),
    ],
    decor: [
      { shape: "ring", at: "bg.calendar", x: 860, y: 70, w: 220, h: 220, line: "dot", lineW: 1, lineC: c("#7CF0FF"), fillA: 0, alpha: 60 },
      { shape: "circle", at: "bg.calendar", x: 960, y: 70, w: 14, h: 14, fill: c("#B49CFF"), fillA: 100 },
      chip("water", "물 +1잔", "#7CF0FF", "#04121A"),
    ],
    values: [
      { name: "물 잔", reset: "day", goal: 6, unit: "잔" },
      { name: "잠", reset: "never", kind: "time" },
      { name: "이달 야간", reset: "month", unit: "번" },
    ],
    buttons: [
      btn("water", "물 한 잔", "💧", "widget", { do: "valAdd", arg: "물 잔", by: 1 }),
      btn("sleep", "잠들어요", "🌙", "widget", { do: "valStamp", arg: "잠" }, [{ do: "screen", arg: "sleep" }]),
      btn("sleept", "잠들어요", "🌙", "tile", { do: "valStamp", arg: "잠" }, [{ do: "screen", arg: "sleep" }]), // [RVM-14] PC-35
    ],
    rules: [
      { name: "야간 끝 회복", on: "end", role: "night", do: "valAdd", arg: "이달 야간", by: 1,
        more: [{ do: "memoLine", arg: "이달 야간 {값(\"이달 야간\")}번째 · 물 {값(\"물 잔\")}잔" },
          { do: "notify", arg: "야간 끝, 수고했어요. 커튼 치고 잠들 때 단추를 눌러요", then: ["sleep", "again60"] }] },
      { name: "근무 중 물", on: "every", min: 120, during: "work", when: '값("물 잔") < 6', do: "notify", arg: "물 {값(\"물 잔\")}잔째예요. 한 잔 더 마셔요", then: ["water", "again10"] },
      { name: "물 다 마심", on: "value", of: "물 잔", edge: "goal", do: "notify", arg: "오늘 물 6잔을 채웠어요. {연속(\"물 잔\")}일째 이어 가요" },
      { name: "알람 끄면 잔 시간", on: "alarmOff", when: '지난시간("잠") < 16', do: "notify", arg: "{지난시간(\"잠\")}시간 잤어요. 오늘 {근무}예요" },
    ],
    tpls: [T.sbar, T.sleep],
    memo: { layout: "list", lines: 3 },
    money: { mark: "dot", dayHead: "line" },
    widget: { text: "m" },
    motion: "tn:fade|td:pulse|sp:n",
  }),

  // ---------- 일근: 집중 ----------
  theme({
    title: "딥워크 데스크", style: "DAY", mood: "차분한", emoji: "🎧", top: true, kitRules: false,
    summary: "50분 집중 단추와 커피 한 번 적기, 하루 집중 막대가 홈 화면에 서는 직장인 프로",
    wow: "따뜻한 회색 종이 위 먹색 글씨, 한 가지 귤색 포인트만 남긴 조용한 책상이에요. 홈 화면 '집중 시작'을 누르면 50분 뒤 쉬라는 알림이 오고, 그 알림에서 바로 다음 집중을 시작해요. 하루 집중 4번 막대와 지난 7일이 위젯에 쌓이고, 커피 단추는 누르는 그 자리에서 가계부 카페 줄까지 적어요.",
    changes: ["홈 화면 집중 단추(50분 뒤 알림, 목표 4번 막대·지난 7일)", "알림 속 '다음 집중' 단추", "커피 한 번에 가계부", "퇴근 때 오늘 집중 기록", "먹색·귤색 한 점 지면"],
    p: { bg: "#EFEDE8", card: "#FBFAF7", ink: "#1B1B1A", sub: "#62605A", accent: "#E8641E", onAccent: "#FFFFFF", line: "#D9D5CC",
      sat: "#3A5A8C", sun: "#C23B22", day: "#1B1B1A", other: "#8C877C", long: "#E8641E", off: "#C9C4B8", band: "#F4F2EC", alarmBg: "#1B1B1A", alarmCard: "#2A2926" },
    font: { body: "notosanskr", title: "hahmlet", date: "dmserif", memo: "notoserifkr", num: "dmserif" },
    paint: { screen: tx("paper", "#EFEDE8", 14), card: so("#FBFAF7"), tabs: so("#1B1B1A"), total: so("#1B1B1A"), paper: tx("paper", "#FBFAF7", 12) },
    r: { card: 6, cell: 4, tabs: 30, btn: 30 },
    tabsShadow: 0, shadow: 1, titleSize: 30, titleTrack: -1, titleWeight: 700, weekTrack: 24,
    cardLine: { c: "#D9D5CC" }, cellLine: { c: "#D9D5CC", w: 0.5 },
    cal: { size: "tall", badge: "line", grid: "line" },
    looks: [cellLook({ cr: 4, dp: "tl", ds: "l", dw: "b", mk: "line", mp: "bottom", pd: 3, ff: "dmserif" }),
      cellLook({ cr: 4, dp: "tl", ds: "l", dc: "#B5AFA2", mk: "none", pd: 3, ff: "dmserif" })],
    pattern: "shift:off=1",
    parts: [{ id: "app.tab", ink: c("#8C877C") }, { id: "app.tab", on: "sel", ink: c("#FFFFFF"), fill: c("#E8641E") }],
    cells: [
      { id: "cal.cell", when: "근무시간 >= 10", line: "solid", lineW: 1.5, lineC: c("#E8641E") },
      { id: "cal.cell.date", when: '요일 = "금" 그리고 아님 쉬는날', ink: c("#E8641E") },
    ],
    decor: [{ shape: "line_h", at: "cal.head", x: 40, y: 900, w: 160, h: 4, fill: c("#E8641E"), fillA: 100 }, chip("focus", "집중 시작", "#1B1B1A", "#FFFFFF")],
    values: [
      { name: "집중", reset: "day", goal: 4, unit: "번" },
      { name: "커피", reset: "day", unit: "잔" },
    ],
    buttons: [
      btn("focus", "집중 시작", "🎧", "widget", { do: "valAdd", arg: "집중", by: 1 },
        [{ do: "notify", arg: "50분 집중 끝. 5분 쉬고 다시 시작해요 ({값(\"집중\")}번째)", after: 50, then: ["focus"] }]),
      btn("coffee", "커피", "☕", "widget", { do: "valAdd", arg: "커피", by: 1 }, [{ do: "addMoney", arg: "카페", amount: 4500 }]),
      btn("focust", "집중 시작", "🎧", "shortcut", { do: "valAdd", arg: "집중", by: 1 }, [{ do: "notify", arg: "50분 집중 끝. 잠깐 쉬어요", after: 50 }]),
    ],
    rules: [
      { name: "퇴근 집중 기록", on: "end", do: "memoLine", arg: "오늘 집중 {값(\"집중\")}번 · 커피 {값(\"커피\")}잔 · 7일 평균 {값평균(\"집중\", 7)}번" },
      { name: "집중 4번", on: "value", of: "집중", edge: "goal", do: "notify", arg: "오늘 깊게 4번 일했어요. 남은 시간은 가볍게 가요" },
      { name: "금요일 주간 회고", on: "daily", at: "17:30", when: '요일 = "금"', do: "newMemo", arg: "주간 회고" },
    ],
    tpls: [T.meeting ?? T.weekly, T.weekly].filter(Boolean),
    memo: { layout: "list", lines: 3 },
    money: { mark: "dot", dayHead: "line" },
    widget: { text: "m" },
    motion: "tn:fade|td:glow|sp:n",
  }),
  // ---------- 알바·라이더: 건수 ----------
  theme({
    title: "라이더 콜보드", styles: ["HOURLY", "FIELD"], mood: "선명한", emoji: "🛵", top: true, kitRules: false,
    summary: "홈 화면 +1건 단추와 목표 막대, 끝나면 오늘 건수·7일 평균이 오는 배달·알바 프로",
    wow: "검정 아스팔트 위 형광 노랑 차선처럼 또렷한 화면이에요. 홈 화면 '+1건'이나 알림 막대 빠른 설정을 누르면 앱을 열지 않고 건수가 올라가고, 목표 30건 막대와 지난 7일이 위젯에 쌓여요. 근무가 끝나면 오늘 건수와 7일 평균이 메모에 남고, 쉬기 단추는 20분 뒤 다시 나가자고 알려 줘요.",
    changes: ["홈 화면 +1건(목표 막대·지난 7일)", "알림 막대 빠른 설정 +1건", "쉬기 20분 타이머", "근무 끝 건수·7일 평균 기록", "목표 채우면 축하, 3일 연속이면 한 번 더", "아스팔트·차선 화면"],
    p: { bg: "#111214", card: "#1B1C20", ink: "#F4F4F0", sub: "#A3A49E", accent: "#E8FF3A", onAccent: "#111214", line: "#2C2E33", dark: true,
      sat: "#7FB8FF", sun: "#FF7A6B", day: "#E8FF3A", eve: "#FFB03A", night: "#7FB8FF", off: "#45474D", today: "#E8FF3A", income: "#B8F56B", expense: "#FF8A7A" },
    font: { body: "notosanskr", title: "bebasneue", date: "oswald", memo: "notosanskr", num: "bebasneue" },
    paint: {
      screen: tx("concrete", "#111214", 14),
      head: pt("stripes", ["#E8FF3A", 0x26], "#111214", 60, 45),
      tabs: so("#0B0B0D"),
      today: lg(180, ["#E8FF3A", 0x00], ["#E8FF3A", 0x55]),
      total: lg(90, "#1B1C20", "#2A2B10"),
    },
    r: { card: 6, cell: 6, tabs: 8, btn: 6 },
    titleSize: 30, titleTrack: 2, weekTrack: 18, tabsLine: { c: "#E8FF3A", a: 60 }, cardLine: { c: "#2C2E33" },
    cal: { size: "tall", badge: "small" },
    looks: [cellLook({ cr: 6, dp: "tl", ds: "l", dw: "b", mk: "line", mp: "bottom", pd: 2, ff: "oswald" }),
      cellLook({ cr: 6, dp: "tl", ds: "l", dc: "#55575E", mk: "none", pd: 2, ff: "oswald" })],
    pattern: "shift:off=1",
    cells: [
      { id: "cal.cell", when: "근무시간 >= 9", line: "solid", lineW: 2, lineC: c("#E8FF3A") },
      { id: "cal.cell", when: "수입 > 0", fill: c("#B8F56B"), fillA: 16 },
      C.holidayWork("#FFB03A"),
    ],
    decor: [{ shape: "dash_h", at: "cal.head", x: 500, y: 940, w: 400, h: 6, fill: c("#E8FF3A"), fillA: 90 }, chip("plus", "+1건", "#E8FF3A", "#111214")],
    values: [{ name: "건수", reset: "day", goal: 30, unit: "건" }],
    buttons: [
      btn("plus", "+1건", "🛵", "widget", { do: "valAdd", arg: "건수", by: 1 }),
      btn("rest", "쉬기 20분", "☕", "widget", { do: "notify", arg: "20분 쉬었어요. 다시 나가 볼까요? 지금 {값(\"건수\")}건", after: 20 }),
      btn("plust", "+1건", "🛵", "tile", { do: "valAdd", arg: "건수", by: 1 }),
      btn("undo", "-1건", "↩️", "day", { do: "valAdd", arg: "건수", by: -1 }),
    ],
    rules: [
      { name: "근무 끝 건수", on: "end", do: "memoLine", arg: "오늘 {값(\"건수\")}건 · 7일 평균 {값평균(\"건수\", 7)}건 · {근무시간}시간",
        more: [{ do: "notify", arg: "오늘 {값(\"건수\")}건 했어요. 7일 합계 {값합계(\"건수\", 7)}건" }] },
      { name: "목표 채움", on: "value", of: "건수", edge: "goal", do: "notify", arg: "오늘 목표 30건 채웠어요 🎉",
        more: [{ do: "notify", arg: "{연속(\"건수\")}일 연속 목표예요. 내일은 조금 쉬어도 돼요", after: 1 }] },
      { name: "근무 중 물", on: "every", min: 90, during: "work", do: "notify", arg: "한 시간 반 달렸어요. 물 한 모금", then: ["rest", "again10"] },
    ],
    tpls: [T.dayCash ?? T.todo].filter(Boolean),
    memo: { layout: "compact", lines: 2 },
    money: { mark: "dot", dayHead: "band", amount: "l" },
    widget: { text: "l" },
    motion: "tn:slide|td:glow|sp:n",
  }),

  // ---------- 현장: 공수 ----------
  theme({
    title: "공수 카운터", style: "FIELD", mood: "밝은", emoji: "🦺", top: true, kitRules: false,
    summary: "홈 화면에서 반 공수·한 공수를 누르면 이달 공수가 쌓이고, 알람 끄면 안전 점검이 오는 현장 프로",
    wow: "안전 표지판처럼 노랑과 먹색 사선이 둘린 화면이에요. 일이 끝나면 홈 화면 '1공수'나 '0.5공수'를 눌러 이달 공수를 쌓고, 달력 칸에는 그날 일한 시간이 굵게 서요. 아침 알람을 끄면 '점검 시작' 단추가 달린 안전 점검 알림이 오고, 25일엔 이달 공수 합계를 메모로 정리해 줘요.",
    changes: ["홈 화면 1공수·0.5공수(이달 합계 막대)", "알람 끄면 안전 점검 알림(점검 시작 단추)", "25일 이달 공수 정리", "비 오는 날 꼬리표 기록", "안전 표지판 사선 화면"],
    p: { bg: "#F5F2E6", card: "#FFFFFF", ink: "#1C1C1A", sub: "#5B5A52", accent: "#1C1C1A", onAccent: "#FFD400", line: "#DCD6BE",
      sat: "#2457A6", sun: "#C9301E", day: "#E0A800", eve: "#D9772B", night: "#2D3B6B", off: "#BDB7A0", today: "#FFD400", band: "#FFF6CC", payCard: "#FFF6CC", headInk: "#1C1C1A", tabSel: "#FFD400", totalInk: "#FFD400" },
    font: { body: "notosanskr", title: "blackhansans", date: "oswald", memo: "notosanskr", num: "oswald" },
    paint: {
      screen: tx("concrete", "#F5F2E6", 10),
      head: so("#FFD400"),
      tabs: so("#1C1C1A"),
      today: so("#FFD400", 0x90),
      total: pt("stripes", ["#FFD400", 0x30], "#1C1C1A", 70, 45),
    },
    r: { card: 4, cell: 4, tabs: 4, btn: 4 },
    tabsFloat: false, titleSize: 30,
    cardLine: { c: "#1C1C1A", w: 1.5 }, cellLine: { c: "#DCD6BE", w: 0.5 },
    cal: { size: "tall", badge: "big", hours: true },
    looks: [cellLook({ cr: 4, dp: "tl", ds: "m", dw: "b", mk: "big", mp: "below", pd: 2 }),
      cellLook({ cr: 4, dp: "tl", ds: "m", dc: "#9A9483", mk: "none", pd: 2 })],
    pattern: "shift:off=1",
    cells: [
      { id: "cal.cell", when: "근무시간 >= 10", line: "solid", lineW: 2.5, lineC: c("#1C1C1A") },
      { id: "cal.cell", when: '꼬리표.포함("우천")', fill: c("#2457A6"), fillA: 14 },
      C.holidayWork("#C9301E"),
    ],
    // 노랑 머리 아래에 표지판 사선 띠(글자 위가 아니라 머리 아랫단에).
    decor: [{ shape: "tape", at: "cal.head", x: 500, y: 960, w: 400, h: 40, fill: c("#1C1C1A"), fillA: 100 }, chip("one", "1공수", "#1C1C1A", "#FFD400")],
    values: [{ name: "공수", reset: "month", goal: 22, unit: "공수" }],
    buttons: [
      btn("one", "1공수", "🦺", "widget", { do: "valAdd", arg: "공수", by: 1 }, [{ do: "memoLine", arg: "1공수 · 이달 {값(\"공수\")}공수" }]),
      btn("half", "0.5공수", "⛑️", "widget", { do: "valAdd", arg: "공수", by: 0.5 }, [{ do: "memoLine", arg: "0.5공수 · 이달 {값(\"공수\")}공수" }]),
      btn("check", "점검 시작", "✅", "day", { do: "memoTpl", arg: "작업 전 안전 점검" }),
      btn("rain", "우천 쉼", "🌧️", "day", { do: "memoLine", arg: "#우천 비로 쉼 · 이달 {값(\"공수\")}공수" }),
    ],
    rules: [
      { name: "알람 끄면 안전 점검", on: "alarmOff", do: "notify", arg: "오늘 현장 들어가기 전 안전 점검을 해요", then: ["check"] },
      { name: "25일 공수 정리", on: "daily", at: "19:00", when: "일 = 25", do: "newMemo", arg: "이달 공수 {값(\"공수\")}공수 · 일한 시간 {이달근무시간}시간" },
      { name: "공수 다 채움", on: "value", of: "공수", edge: "goal", do: "notify", arg: "이달 {값(\"공수\")}공수를 채웠어요. 수고 많았어요" },
    ],
    tpls: [T.safety, T.fieldLog].filter(Boolean),
    memo: { layout: "list", lines: 3 },
    money: { mark: "emoji", dayHead: "band" },
    widget: { text: "l" },
    motion: "tn:fade|td:pulse|sp:n",
  }),

  // ---------- 가게: 손님 카운터 ----------
  theme({
    title: "카운터 손님 장부", style: "BUSINESS", mood: "밝은", emoji: "🧾", top: true, kitRules: false,
    summary: "카드·현금·포장 손님을 홈 화면에서 세고, 마감 때 오늘 손님을 한 줄로 남기는 가게 프로",
    wow: "따뜻한 크림 종이에 초록 잉크 도장을 찍은 가게 장부예요. 계산대 옆 폰 홈 화면에서 카드·현금·포장 손님을 한 번씩 누르면 오늘 손님이 세어지고, 목표 손님 수 막대가 차요. 밤 열 시엔 오늘 손님과 지난 7일 평균이 메모에 한 줄로 남고, 마감 알림에서 바로 마감 점검을 열어요.",
    changes: ["홈 화면 카드·현금·포장 손님 단추", "손님 목표 막대·지난 7일", "밤 열 시 오늘 손님 한 줄", "마감 알림 속 마감 점검 단추", "크림 장부·초록 도장 화면"],
    p: { bg: "#F7F1E3", card: "#FFFDF6", ink: "#1E2A22", sub: "#5E6A60", accent: "#1F6B47", onAccent: "#FFFFFF", line: "#E2D8C0",
      sat: "#2D5FA0", sun: "#B8392B", off: "#C8BFA8", today: "#1F6B47", income: "#1F6B47", expense: "#B8392B", band: "#F1E8D2", tabSel: "#F7F1E3" },
    font: { body: "gowundodum", title: "gowunbatang", date: "dmserif", memo: "gowundodum", num: "dmserif" },
    paint: { screen: tx("paper", "#F7F1E3", 16), card: tx("paper", "#FFFDF6", 10), tabs: so("#1E2A22"), total: so("#1F6B47"), paper: pt("thin", ["#E2D8C0"], "#FFFDF6", 60) },
    r: { card: 10, cell: 8, tabs: 18, btn: 10 },
    titleSize: 28, cardLine: { c: "#E2D8C0" },
    cal: { size: "tall", badge: "small", spend: true },
    looks: [cellLook({ cr: 8, dp: "tl", ds: "m", dw: "b", mk: "dot", mp: "below", pd: 2, ff: "dmserif" }),
      cellLook({ cr: 8, dp: "tl", ds: "m", dc: "#A69D86", mk: "none", pd: 2, ff: "dmserif" })],
    pattern: "shift:off=1",
    cells: [
      { id: "cal.cell", when: '수입 >= 칸("하루 목표 매출", 500000)', line: "solid", lineW: 2, lineC: c("#1F6B47"), motion: "glow" },
      { id: "cal.cell.date", when: "공휴일", ink: c("#B8392B"), weight: 900 },
    ],
    decor: [{ shape: "seal", at: "bg.calendar", x: 930, y: 40, w: 56, h: 56, fill: c("#1F6B47"), fillA: 10, line: "solid", lineW: 1.5, lineC: c("#1F6B47"), rot: 12 },
      chip("card", "손님 +1", "#1F6B47", "#FFFFFF")],
    values: [
      { name: "카드 손님", reset: "day", unit: "명" },
      { name: "현금 손님", reset: "day", unit: "명" },
      { name: "포장", reset: "day", unit: "건" },
    ],
    buttons: [
      btn("card", "카드", "💳", "widget", { do: "valAdd", arg: "카드 손님", by: 1 }),
      btn("cash", "현금", "💵", "widget", { do: "valAdd", arg: "현금 손님", by: 1 }),
      btn("togo", "포장", "🥡", "widget", { do: "valAdd", arg: "포장", by: 1 }),
      btn("close", "마감 점검", "🔒", "day", { do: "memoTpl", arg: "마감 점검" }),
    ],
    rules: [
      { name: "밤 열 시 손님 정리", on: "daily", at: "22:00", do: "memoLine",
        arg: "손님 {값(\"카드 손님\") + 값(\"현금 손님\")}명(카드 {값(\"카드 손님\")} · 현금 {값(\"현금 손님\")}) · 포장 {값(\"포장\")}건",
        more: [{ do: "notify", arg: "오늘 손님 {값(\"카드 손님\") + 값(\"현금 손님\")}명. 마감 점검을 열어요", then: ["close"] }] },
      { name: "매출 적으면 한 줄", on: "money", when: "수입 > 0", do: "memoLine", arg: "오늘 매출 {원(수입)} · 이달 {원(이달수입)}" },
    ],
    inputs: [{ id: "i1", label: "하루 목표 매출", kind: "money", default: 500000, min: 0, hint: "우리 가게 하루 목표로 바꿔요" }],
    tpls: [T.closing, T.order].filter(Boolean),
    memo: { layout: "list", lines: 3 },
    money: { mark: "emoji", dayHead: "band", amount: "l" },
    widget: { text: "l" },
    motion: "tn:fade|td:glow|sp:n",
  }),

  // ---------- 투자: 원칙 ----------
  theme({
    title: "원칙 체크 터미널", style: "INVEST", mood: "어두운", emoji: "🧭", top: true, kitRules: false,
    summary: "매매·참은 충동을 홈 화면에서 세고, 하루 세 번을 넘기면 원칙부터 묻는 투자 기록 프로",
    wow: "먹색 터미널에 호박색 글자가 한 줄씩 떠요. 매매할 때 홈 화면 '매매 +1'을 누르면 오늘 세 번째부터는 원칙 점검 알림이 먼저 오고, 사고 싶던 걸 참았으면 '참았어요'를 눌러 이번 주 참은 횟수를 쌓아요. 장이 끝나면 오늘 매매와 이번 주 참은 횟수가 일지에 한 줄로 남아요.",
    changes: ["홈 화면 매매 +1·참았어요", "세 번째 매매부터 원칙 점검 알림", "장 끝 일지 한 줄", "호박색 터미널 화면"],
    p: { bg: "#0C0C0B", card: "#151513", ink: "#F3E9D2", sub: "#A89F8A", accent: "#FFB347", onAccent: "#1A1206", line: "#2A2824", dark: true,
      sat: "#7FB0FF", sun: "#FF7A6B", off: "#3F3D38", today: "#FFB347", income: "#7FE0A0", expense: "#FF8A7A" },
    font: { body: "ibmplexsanskr", title: "vt323", date: "jetbrainsmono", memo: "nanumgothiccoding", num: "jetbrainsmono" },
    paint: { screen: rg(50, 0, 140, ["#1E1A10"], ["#0C0C0B", 255, 70]), card: so("#151513"), head: pt("thin", ["#FFB347", 0x14], "#0C0C0B", 50), tabs: so("#0C0C0B"), total: lg(135, "#2A2210", "#151513"), paper: tx("noise", "#151513", 18) },
    r: { card: 2, cell: 2, tabs: 2, btn: 2 },
    tabsFloat: false, glow: "#FFB347", glowR: 6, titleSize: 28, titleTrack: 3, weekTrack: 22,
    tabsLine: { c: "#2A2824" }, cardLine: { c: "#2A2824" }, cellLine: { c: "#1F1E1B", w: 0.5 },
    cal: { size: "normal", badge: "line", grid: "line" },
    looks: [cellLook({ cr: 2, dp: "tl", ds: "m", mk: "line", mp: "bottom", pd: 2, ff: "jetbrainsmono" })],
    cells: [
      { id: "cal.cell", when: '꼬리표.포함("매매")', line: "solid", lineW: 1.5, lineC: c("#FFB347") },
      { id: "cal.cell", when: '꼬리표.포함("매매") 그리고 아님 꼬리표.포함("일지")', fill: c("#FF7A6B"), fillA: 16 },
    ],
    decor: [chip("trade", "매매 +1", "#151513", "#FFB347", { line: "#FFB347" })],
    values: [
      { name: "매매", reset: "day", unit: "번" },
      { name: "참은 충동", reset: "week", unit: "번" },
    ],
    buttons: [
      btn("trade", "매매 +1", "📈", "widget", { do: "valAdd", arg: "매매", by: 1, if: '값("매매") >= 2' },
        [{ do: "notify", arg: "오늘 {값(\"매매\")}번째 매매예요. 정한 원칙에 맞나요?", then: ["rules"] }]),
      btn("hold", "참았어요", "🧘", "widget", { do: "valAdd", arg: "참은 충동", by: 1 }),
      btn("rules", "원칙 보기", "🧭", "day", { do: "memoTpl", arg: "내 원칙 점검" }),
    ],
    rules: [
      { name: "장 끝 일지", on: "daily", at: "15:40", when: '아님 주말 그리고 아님 공휴일 그리고 값("매매") > 0', do: "memoLine",
        arg: "#매매 오늘 {값(\"매매\")}번 · 이번 주 참은 충동 {값(\"참은 충동\")}번" },
      { name: "금요일 돌아보기", on: "daily", at: "18:00", when: '요일 = "금"', do: "notify", arg: "이번 주 참은 충동 {값(\"참은 충동\")}번. 지난주 {지난값(\"참은 충동\", 1)}번이었어요" },
    ],
    tpls: [{ title: "내 원칙 점검", emoji: "🧭", check: ["정한 비중을 지켰나", "소문으로 사지 않았나", "손절 기준을 적었나"] }, T.trade].filter(Boolean),
    memo: { layout: "list", lines: 3 },
    money: { mark: "dot", dayHead: "line" },
    widget: { text: "m" },
    motion: "tn:fade|td:glow|sp:n",
  }),

  // ---------- 모든 유형: 건강 습관 ----------
  theme({
    title: "하루 루틴 트래커", mood: "파스텔", emoji: "🌿", top: true,
    summary: "물·약·걷기를 홈 화면에서 체크하고, 약 먹은 지 12시간이면 다시 알려 주는 생활 루틴 프로",
    wow: "옅은 세이지와 복숭아색이 번지는 부드러운 화면이에요. 홈 화면 단추로 물 한 잔·약 먹음·10분 걷기를 앱을 열지 않고 적고, 물 8잔 막대와 지난 7일이 쌓여요. 약을 먹은 지 12시간이 지나면 '먹었어요' 단추가 달린 알림이 오고, 근무가 있는 날엔 근무 중에만 두 시간마다 걷자고 알려 줘요.",
    changes: ["홈 화면 물·약·걷기 단추(목표 막대·지난 7일)", "약 12시간 지나면 알림 속 '먹었어요' 단추", "근무 중에만 두 시간마다 걷기", "3일 연속 목표면 칭찬", "세이지·복숭아 화면"],
    p: { bg: "#F3F6F1", card: "#FFFFFF", ink: "#23302A", sub: "#5E6D64", accent: "#4E8C6A", onAccent: "#FFFFFF", line: "#DCE6DE",
      sat: "#4A78B8", sun: "#D2644E", day: "#4E8C6A", eve: "#E39A6B", night: "#6B6FB0", off: "#C3CEC6", other: "#9AA79F", long: "#D2644E", today: "#F2A98A" },
    font: { body: "nunito", title: "gowunbatang", date: "quicksand", memo: "gowundodum", num: "quicksand" },
    paint: { screen: sh("mesh", ["#E5F0E6", "#FBE7DD", "#EEF3E9"], 1), card: gl("#FFFFFF", 0xD8, 16), tabs: gl("#FFFFFF", 0xE6, 18), total: lg(135, "#4E8C6A", "#7FB08F"), paper: tx("paper", "#FFFFFF", 10) },
    r: { card: 22, cell: 14, tabs: 28, btn: 24 },
    titleSize: 26, cardLine: { c: "#DCE6DE" },
    cal: { size: "tall", badge: "small" },
    looks: [cellLook({ cr: 14, dp: "mc", ds: "m", mk: "dot", mp: "below", pd: 2 })],
    cells: [
      { id: "cal.cell", when: "쉬는날", fill: c("#F2A98A"), fillA: 14 },
      { id: "cal.cell", when: "체크율 >= 100", line: "solid", lineW: 2, lineC: c("#4E8C6A") },
    ],
    decor: [{ shape: "leaf", at: "bg.calendar", x: 930, y: 40, w: 50, h: 50, fill: c("#4E8C6A"), fillA: 24, rot: 20 }, chip("water", "물 한 잔", "#4E8C6A", "#FFFFFF")],
    values: [
      { name: "물 잔", reset: "day", goal: 8, unit: "잔" },
      { name: "약", reset: "never", kind: "time" },
      { name: "걷기", reset: "day", goal: 3, unit: "번" },
    ],
    buttons: [
      btn("water", "물 한 잔", "💧", "widget", { do: "valAdd", arg: "물 잔", by: 1 }),
      btn("pill", "약 먹음", "💊", "widget", { do: "valStamp", arg: "약" }),
      btn("walk", "10분 걷기", "🚶", "widget", { do: "valAdd", arg: "걷기", by: 1 }),
    ],
    rules: [
      { name: "약 12시간", on: "every", min: 60, at: "08:00", until: "22:00", when: '지난시간("약") >= 12', do: "notify",
        arg: "약 먹은 지 {지난시간(\"약\")}시간 지났어요", then: ["pill", "again60"] },
      { name: "근무 중 걷기", on: "every", min: 120, during: "work", when: '값("걷기") < 3', do: "notify", arg: "두 시간 앉아 있었어요. 10분만 걸어요", then: ["walk", "again10"] },
      { name: "물 다 마심", on: "value", of: "물 잔", edge: "goal", do: "notify", arg: "오늘 물 8잔 끝. {연속(\"물 잔\")}일째예요 🌿" },
    ],
    memo: { layout: "grid2", lines: 3 },
    money: { mark: "emoji", dayHead: "band" },
    widget: { text: "m" },
    motion: "tn:fade|td:glow|sp:n",
  }),

  // ---------- 모든 유형: 잠 ----------
  theme({
    title: "잠의 정원", mood: "차분한", emoji: "🌘", top: true,
    summary: "잠들기 단추로 잠을 재고, 알람을 끄면 잔 시간과 오늘 근무가 오는 밤 루틴 프로",
    wow: "밤이 깊어질수록 짙어지는 남보라 정원 같은 화면이에요. 빠른 설정이나 홈 화면 '잠들어요'를 누르면 잠든 시각이 적히고 수면 화면이 열려요. 아침 알람을 끄면 몇 시간 잤는지와 오늘 근무가 오고, 6시간이 안 되면 그날 메모에 #짧은잠이 남아 달력 칸이 연보라로 물들어요. 밤 열한 시엔 '잠들어요' 단추가 달린 알림이 와요.",
    changes: ["빠른 설정·홈 화면 '잠들어요'(수면 화면 열림)", "알람 끄면 잔 시간·오늘 근무", "6시간 못 잔 날 #짧은잠·칸 물들임", "밤 열한 시 알림 속 단추", "남보라 정원 화면"],
    p: { bg: "#14122B", card: "#1E1B3D", ink: "#ECE8FF", sub: "#A39DCC", accent: "#C9B6FF", onAccent: "#171330", line: "#2E2A57", dark: true,
      sat: "#8FB4FF", sun: "#FF9AB0", day: "#FFD59A", eve: "#FFB08A", night: "#C9B6FF", off: "#3D3966", other: "#7D78A8", long: "#FF9AB0", today: "#C9B6FF" },
    font: { body: "gowundodum", title: "songmyung", date: "lora", memo: "gowunbatang", num: "lora" },
    paint: { screen: lg(180, "#1F1A45", "#0E0C20"), card: gl("#1E1B3D", 0xE0, 18), head: rg(85, 20, 70, ["#C9B6FF", 0x30], ["#C9B6FF", 0x00]), tabs: gl("#14122B", 0xF0, 20), total: lg(135, "#3A2F7A", "#221D4D"), paper: tx("noise", "#1E1B3D", 16) },
    r: { card: 20, cell: 16, tabs: 28, btn: 22 },
    glow: "#C9B6FF", glowR: 10, titleSize: 26, cardLine: { c: "#2E2A57" },
    cal: { size: "tall", badge: "small" },
    looks: [cellLook({ cr: 16, dp: "mc", ds: "m", mk: "dot", mp: "below", pd: 2 })],
    cells: [
      { id: "cal.cell", when: '꼬리표.포함("짧은잠")', fill: c("#C9B6FF"), fillA: 30, motion: "glow" },
      { id: "cal.cell", when: '근무종류 = "야간"', fill: c("#C9B6FF"), fillA: 18 },
    ],
    decor: [
      { shape: "moon", at: "bg.calendar", x: 920, y: 40, w: 56, h: 56, fill: c("#C9B6FF"), fillA: 70 },
      chip("sleep", "잠들어요", "#C9B6FF", "#171330"),
      { shape: "sparkle", at: "bg.calendar", x: 120, y: 60, w: 24, h: 24, fill: c("#ECE8FF"), fillA: 60 },
      { shape: "sparkle", at: "bg.calendar", x: 300, y: 30, w: 14, h: 14, fill: c("#ECE8FF"), fillA: 40 },
    ],
    values: [{ name: "잠", reset: "never", kind: "time" }],
    buttons: [
      btn("sleep", "잠들어요", "🌘", "widget", { do: "valStamp", arg: "잠" }, [{ do: "screen", arg: "sleep" }]),
      btn("sleept", "잠들어요", "🌘", "tile", { do: "valStamp", arg: "잠" }, [{ do: "screen", arg: "sleep" }]), // [RVM-14] PC-35
    ],
    rules: [
      { name: "밤 열한 시", on: "daily", at: "23:00", do: "notify", arg: "곧 잘 시간이에요. 내일 {근무}예요", then: ["sleep", "again60"] },
      { name: "잘 잔 아침", on: "alarmOff", when: '지난시간("잠") >= 6 그리고 지난시간("잠") < 16', do: "notify", arg: "{지난시간(\"잠\")}시간 잤어요. 오늘 {근무}예요" },
      { name: "짧게 잔 아침", on: "alarmOff", when: '지난시간("잠") < 6', do: "notify", arg: "{지난시간(\"잠\")}시간밖에 못 잤어요. 오늘 {근무}, 무리하지 말아요",
        more: [{ do: "memoLine", arg: "#짧은잠 {지난시간(\"잠\")}시간" }] },
    ],
    memo: { layout: "diary", lines: 5 },
    money: { mark: "dot", dayHead: "line" },
    widget: { text: "m" },
    motion: "tn:fade|td:glow|sp:n",
  }),
];
