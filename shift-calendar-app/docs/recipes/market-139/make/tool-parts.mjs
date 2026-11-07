// 1.39 [MKW-04] 도구 › 부품 48. 지금 쓰는 테마 위에 얹는 부품 하나(설계도 한 단위만 → 장터 '도구 › 부품'). 1.38 부품 30과 겹치지 않게
// 테마 50에서 가장 반응이 좋을 디자인을 부품으로 떼어 냈어요. 밝은 화면용 줄에 어두운 화면(on: dark) 줄을 같이 두어 어느 테마에서도 읽혀요.
import { c } from "../../market-136/make/lib.mjs";
import { partWork } from "../../market-138-studio/make/tabs-build.mjs";
import { lg, rg, sg, pt, tx, gl, so } from "./art.mjs";

const ROSTER = ["SHIFT", "DAY", "HOURLY"];
const P = (title, cover, summary, wow, changes, lines, more = {}) => partWork({ title, cover, summary, wow, changes, lines, ...more });
const glowOf = (col, r = 8) => ({ tshadow: r, tshadowC: c(col), tshadowX: 0, tshadowY: 0 });

export default [
  // ---- 달력 머리 (cal.head) 7 ----
  P("레이더 달 머리", ["#06100D", "#35F08F", "📡"], "관제 모니터 같은 검은 머리, 초록 형광 달 이름",
    "달력 머리가 관제실 모니터처럼 검은 격자 위에 놓이고, 달 이름은 초록 형광으로 빛나요. 밝은 화면에서도 머리만 밤처럼 떠요.", ["격자 모니터 머리", "형광 달 이름"],
    [{ id: "cal.head", paint: pt("grid", ["#35F08F", 0x30], "#06100D", 70), r: 18, pad: 12, out: 6, line: "solid", lineW: 1, lineC: c("#35F08F"), lineA: 50 },
      { id: "cal.head.title", font: "orbitron", ink: c("#35F08F"), weight: 800, size: 20, track: 2, ...glowOf("#35F08F") },
      { id: "cal.head.nav", ink: c("#35F08F") }, { id: "cal.head.today", ink: c("#B9FFD9"), weight: 800 }, { id: "cal.head", on: "dark", lineA: 90, shadow: 8, shadowC: c("#35F08F") }], { top: true }),
  P("잡지 표지 달 머리", ["#FFFFFF", "#C0392B", "📰"], "큰 세리프 달 이름과 붉은 가는 밑줄",
    "달 이름이 잡지 표지처럼 큰 세리프로 서고, 머리 아래 붉은 가는 줄 하나가 지나가요. 어두운 화면에선 흰 세리프로 바뀌어요.", ["세리프 큰 달 이름", "붉은 밑줄"],
    [{ id: "cal.head", paint: so("#FFFFFF", 0), pad: 10, line: "solid", lineW: 0.5, lineC: c("#C0392B") },
      { id: "cal.head.title", font: "dmserif", size: 30, weight: 400, ink: c("#1C1C1C"), track: -1 }, { id: "cal.head.today", ink: c("#C0392B"), weight: 800 },
      { id: "cal.head.title", on: "dark", ink: c("#F4F1EC") }]),
  P("칠판 달 머리", ["#2F4A3A", "#F5E27A", "🧑‍🏫"], "녹색 칠판에 분필 붓글씨 달 이름",
    "달력 머리가 녹색 칠판이 되고 달 이름은 분필 붓글씨예요. 오늘 단추는 노란 분필이에요.", ["칠판 머리", "분필 글씨"],
    [{ id: "cal.head", paint: tx("noise", "#2F4A3A", 14), fill: c("#2F4A3A"), r: 10, pad: 12, out: 6, line: "solid", lineW: 6, lineC: c("#8A5A2B") },
      { id: "cal.head.title", font: "nanumbrush", size: 32, ink: c("#F5F0DC") }, { id: "cal.head.nav", ink: c("#F5F0DC") }, { id: "cal.head.today", ink: c("#F5E27A"), weight: 800 }, { id: "cal.head", on: "dark", lineC: c("#5D3A1A") }]),
  P("물결 해도 달 머리", ["#E6EEF2", "#2F5D7C", "🧭"], "물결 무늬 종이 위 명조 달 이름",
    "머리 바탕에 해도 같은 가는 물결이 깔리고 달 이름은 명조예요. 어두운 화면에선 밤바다 물결이에요.", ["물결 무늬 머리", "명조 달 이름"],
    [{ id: "cal.head", paint: pt("wave", ["#2F5D7C", 0x30], "#EAF1F4", 120), r: 16, pad: 12, out: 6 },
      { id: "cal.head.title", font: "nanummyeongjo", size: 24, weight: 800, ink: c("#1E3F57") },
      { id: "cal.head", on: "dark", paint: pt("wave", ["#7FB3D5", 0x30], "#13283A", 120) }, { id: "cal.head.title", on: "dark", ink: c("#DCEAF3") }]),
  P("메시 노을 달 머리", ["#FF9A8B", "#6A5ACD", "🎨"], "천천히 섞이는 메시 그라데이션 머리",
    "머리 바탕에 분홍·보라·살구가 천천히 섞이는 메시 그라데이션이 흘러요. 달 이름은 흰 굵은 글씨예요.", ["메시 셰이더 머리"],
    [{ id: "cal.head", paint: "sh.mesh.FFFF9A8B,FF6A5ACD,FFFFD3A5.2", r: 22, pad: 12, out: 6 },
      { id: "cal.head.title", ink: c("#FFFFFF"), weight: 900, size: 24, tshadow: 6, tshadowC: c("#66000000"), tshadowX: 0, tshadowY: 2 },
      { id: "cal.head.nav", ink: c("#FFFFFF") }, { id: "cal.head.today", ink: c("#FFFFFF"), weight: 800 }, { id: "cal.head", on: "dark", paint: "sh.mesh.FF8E3B5A,FF2E2470,FF8A5A3C.2" }]),
  P("영수증 달 머리", ["#FFFFFF", "#0F9D58", "🧾"], "감열지 점선 머리, 고정폭 달 이름",
    "머리가 영수증 머리처럼 점선 테두리에 고정폭 글씨로 놓여요.", ["점선 머리", "고정폭 달 이름"],
    [{ id: "cal.head", paint: so("#FFFFFF"), r: 2, pad: 12, out: 6, line: "dash", lineW: 1, lineC: c("#9E9E9E") },
      { id: "cal.head.title", font: "nanumgothiccoding", size: 20, weight: 800, ink: c("#212121"), track: 1 }, { id: "cal.head.today", ink: c("#0F9D58"), weight: 800 },
      { id: "cal.head", on: "dark", paint: so("#1E1F22"), lineC: c("#5F6368") }, { id: "cal.head.title", on: "dark", ink: c("#E8EAED") }]),
  P("픽셀 달 머리", ["#1B1E3C", "#5BFF7A", "👾"], "8비트 게임 제목처럼 픽셀 달 이름",
    "달 이름이 게임 시작 화면처럼 픽셀 글씨로 깜빡이고 머리는 각진 남색 판이에요.", ["픽셀 글씨", "각진 판"],
    [{ id: "cal.head", paint: so("#1B1E3C"), r: 0, pad: 12, out: 6, line: "solid", lineW: 2, lineC: c("#5BFF7A") },
      { id: "cal.head.title", font: "pressstart", size: 14, ink: c("#5BFF7A"), motion: "pulse" }, { id: "cal.head.nav", ink: c("#5BFF7A") }, { id: "cal.head.today", ink: c("#FFD54F") }, { id: "cal.head", on: "dark", paint: so("#0D0F24") }]),

  // ---- 요일 줄 (cal.week) 3 ----
  P("넓은 자간 요일", ["#FFFFFF", "#111111", "🔤"], "영문 대문자처럼 넓게 띄운 요일 글자",
    "요일 글자 사이를 넓게 띄워 잡지 같은 여백이 생겨요.", ["넓은 자간 요일"],
    [{ id: "cal.week.day", font: "josefinsans", size: 11, weight: 700, track: 10 }, { id: "cal.week.day", on: "sun", ink: c("#C0392B") }]),
  P("알약 요일 줄", ["#EEF2FF", "#3D5AFE", "💊"], "요일 줄이 알약 모양 띠로",
    "요일 줄이 둥근 알약 띠 안에 놓여 달력과 머리가 또렷이 나뉘어요. 어두운 화면에선 짙은 띠예요.", ["알약 요일 띠"],
    [{ id: "cal.week", fill: c("#EEF2FF"), r: 48, pad: 6, out: 4 }, { id: "cal.week.day", weight: 800, size: 11 },
      { id: "cal.week", on: "dark", fill: c("#262A40") }]),
  P("손글씨 요일", ["#FFF8E7", "#8D6E63", "✍️"], "요일을 손글씨로, 일요일은 붉은 펜",
    "요일 글자가 다이어리에 적은 손글씨가 되고 일요일만 붉은 펜이에요.", ["손글씨 요일"],
    [{ id: "cal.week.day", font: "gaegu", size: 14, weight: 700 }, { id: "cal.week.day", on: "sun", ink: c("#D84315") }, { id: "cal.week.day", on: "sat", ink: c("#1E88E5") }]),

  // ---- 달력 칸 (cal.cell) 9 ----
  P("레이더 야간 칸", ["#0B1A15", "#35F08F", "🛰️"], "야간 칸에 초록 테와 레이더가 도는 빛", "야간 근무 칸이 초록 테두리로 둘러지고 레이더처럼 은은히 빛나요.", ["야간 칸 초록 레이더"],
    [{ id: "cal.cell", on: "role.night", line: "solid", lineW: 1.5, lineC: c("#35F08F"), motion: "glow" }, { id: "cal.cell.mark", on: "role.night", ink: c("#35F08F") }],
    { styles: ["SHIFT", "HOURLY"], top: true }),
  P("유리 근무 칸", ["#D6E4FF", "#FFFFFF", "🫧"], "근무 칸이 뒤가 비치는 유리판", "근무가 있는 칸이 뒤 배경이 흐리게 비치는 유리판으로 떠요. 사진 배경 테마와 잘 어울려요.", ["유리 칸"],
    [{ id: "cal.cell", on: "role.day", paint: gl("#FFFFFF", 0x99, 12), r: 12 }, { id: "cal.cell", on: "role.eve", paint: gl("#FFFFFF", 0x99, 12), r: 12 },
      { id: "cal.cell", on: "role.night", paint: gl("#FFFFFF", 0x99, 12), r: 12 }, { id: "cal.cell", on: "dark", paint: gl("#202330", 0x99, 12) }], { styles: ROSTER }),
  P("도장 쉬는 날 칸", ["#FFF0F5", "#E85A8A", "🌸"], "쉬는 날에 꽃 도장 같은 동그란 칸", "쉬는 날 칸이 분홍 도장처럼 동그랗게 찍혀요.", ["쉬는 날 도장"],
    [{ id: "cal.cell", on: "role.off", fill: c("#E85A8A"), fillA: 18, r: 48, line: "dash", lineW: 1, lineC: c("#E85A8A") }], { styles: ROSTER }),
  P("벽돌 주말 칸", ["#F4E4D8", "#B5651D", "🧱"], "주말 칸에 옅은 벽돌 무늬", "토·일요일 칸에 옅은 벽돌 무늬가 깔려 주말이 한눈에 나뉘어요.", ["주말 벽돌 무늬"],
    [{ id: "cal.cell", on: "sat", paint: pt("brick", ["#B5651D", 0x22], ["#FFFFFF", 0], 80) }, { id: "cal.cell", on: "sun", paint: pt("brick", ["#B5651D", 0x22], ["#FFFFFF", 0], 80) }]),
  P("오늘 노을 칸", ["#FF7043", "#7E57C2", "🌇"], "오늘 칸에 노을 셰이더가 흘러요", "오늘 칸이 작은 노을 셰이더로 천천히 흘러 바로 눈에 들어와요.", ["노을 오늘 칸"],
    [{ id: "cal.cell", on: "today", paint: "sh.sunset.FFFF7043,FF7E57C2.3", r: 12 }, { id: "cal.cell.date", on: "today", ink: c("#FFFFFF"), weight: 900 }]),
  P("하트 공휴일 칸", ["#FFEBEE", "#E53935", "💝"], "공휴일 칸에 작은 하트 무늬", "공휴일 칸에 옅은 하트 무늬가 깔리고 날짜가 붉어요.", ["공휴일 하트 무늬"],
    [{ id: "cal.cell", on: "holiday", paint: pt("heart", ["#E53935", 0x26], ["#FFFFFF", 0], 70) }, { id: "cal.cell.date", on: "holiday", ink: c("#E53935"), weight: 800 }]),
  P("긴 근무 굵은 칸", ["#EDE7F6", "#7E57C2", "📏"], "10시간 넘는 날 칸 테두리가 굵게", "그날 근무가 10시간을 넘으면 칸 테두리가 보라로 굵어져요.", ["긴 날 굵은 테"],
    [{ id: "cal.cell", when: "근무시간 >= 10", line: "solid", lineW: 2.5, lineC: c("#7E57C2") }], { styles: ["SHIFT", "DAY", "HOURLY", "FIELD"] }),
  P("메모 많은 날 점선 칸", ["#E3F2FD", "#1E88E5", "🗂️"], "메모가 셋 넘는 날 파랑 점선", "메모가 셋 넘게 쌓인 바쁜 날 칸에 파랑 점선이 둘러져요.", ["바쁜 날 점선"],
    [{ id: "cal.cell", when: "메모수 >= 3", line: "dash", lineW: 1.5, lineC: c("#1E88E5") }]),
  P("큰 날짜 칸", ["#FFFFFF", "#212121", "🔢"], "날짜 숫자를 크고 굵게, 왼쪽 위로", "칸 날짜 숫자가 크고 굵게 왼쪽 위에 서요. 멀리서도 날짜가 먼저 읽혀요.", ["큰 날짜"],
    [{ id: "cal.cell.date", size: 16, weight: 900, font: "montserrat" }, { id: "cal.cell.date", on: "sun", ink: c("#E53935") }, { id: "cal.cell.date", on: "today", size: 17 }]),

  // ---- 달력 아래 (cal.foot) 1 ----
  P("유리 달력 발", ["#E3F2FD", "#FFFFFF", "🪟"], "달력 아래 요약 줄이 유리판", "달력 아래 요약 줄이 유리판으로 떠요. 어두운 화면에선 짙은 유리예요.", ["유리 아래 줄"],
    [{ id: "cal.foot", paint: gl("#FFFFFF", 0xB3, 20), r: 20, line: "solid", lineW: 1, lineC: c("#99FFFFFF") }, { id: "cal.foot", on: "dark", paint: gl("#202330", 0x99, 20), lineC: c("#33FFFFFF") }]),

  // ---- 아래 탭 막대 (app.tabs) 5 ----
  P("떠 있는 유리 탭 막대", ["#E8EAF6", "#FFFFFF", "🧊"], "화면 위에 뜬 둥근 유리 탭 막대", "아래 탭 막대가 화면에서 살짝 떠 있는 둥근 유리판이 돼요.", ["떠 있는 유리 막대"],
    [{ id: "app.tabs", paint: gl("#FFFFFF", 0xB3, 24), r: 28, out: 12, shadow: 8, line: "solid", lineW: 1, lineC: c("#80FFFFFF") },
      { id: "app.tabs", on: "dark", paint: gl("#1C1E26", 0xB3, 24), lineC: c("#26FFFFFF") }], { top: true }),
  P("오로라 탭 막대", ["#0D47A1", "#26A69A", "🌌"], "탭 막대에 오로라가 천천히 흘러요", "아래 탭 막대 바탕에 오로라 셰이더가 흐르고 글자는 흰색이 돼요.", ["오로라 막대"],
    [{ id: "app.tabs", paint: "sh.aurora.FF0D2B5C,FF0E6B63,FF3C2B7A.2", r: 24, out: 10, shadow: 6 }, { id: "app.tabs", on: "dark", paint: "sh.aurora.FF061A40,FF00695C,FF4527A0.2" }]),
  P("종이 결 탭 막대", ["#F5E6CC", "#8D6E63", "📜"], "크래프트지 결 탭 막대", "탭 막대가 크래프트지 결이 되어 다이어리 같은 앱과 어울려요.", ["크래프트지 막대"],
    [{ id: "app.tabs", paint: tx("kraft", "#E9D7B8", 14), r: 16, out: 8, shadow: 2 }, { id: "app.tabs", on: "dark", paint: tx("kraft", "#4A3C2C", 14) }]),
  P("네온 테 탭 막대", ["#120024", "#FF4FB5", "💡"], "검은 막대에 분홍 네온 테", "탭 막대가 검은 판에 분홍 네온 테두리를 둘러요.", ["네온 테 막대"],
    [{ id: "app.tabs", paint: so("#120024"), r: 20, out: 10, line: "solid", lineW: 1.5, lineC: c("#FF4FB5"), shadow: 10, shadowC: c("#FF4FB5") }, { id: "app.tabs", on: "dark", shadow: 14 }]),
  P("각진 신문 탭 막대", ["#FFFFFF", "#111111", "🗞️"], "위에 굵은 줄 하나만 있는 각진 막대", "탭 막대가 신문 아래 단처럼 각지고 위에 굵은 검은 줄 하나만 남아요.", ["각진 막대"],
    [{ id: "app.tabs", fill: c("#FFFFFF"), r: 0, out: 0, shadow: 0, line: "double", lineW: 2, lineC: c("#111111") },
      { id: "app.tabs", on: "dark", fill: c("#121212"), lineC: c("#EDEDED") }]),

  // ---- 고른 탭 (app.tab) 2 ----
  P("알약 고른 탭", ["#E8F0FE", "#1A73E8", "🔵"], "고른 탭 뒤에 진한 알약", "지금 고른 탭 뒤에 진한 파랑 알약이 깔리고 글자는 흰색이에요.", ["알약 고른 탭"],
    [{ id: "app.tab", on: "sel", fill: c("#1A73E8"), ink: c("#FFFFFF"), r: 48, weight: 800 }]),
  P("밑줄 고른 탭", ["#FFFFFF", "#C0392B", "➖"], "고른 탭 아래 붉은 밑줄", "고른 탭 글자가 굵어지고 아래 붉은 밑줄이 생겨요.", ["밑줄 고른 탭"],
    [{ id: "app.tab", on: "sel", deco: "under", ink: c("#C0392B"), weight: 900 }]),

  // ---- 카드 (app.card) 3 ----
  P("두꺼운 그림자 카드", ["#FFFFFF", "#111111", "🧱"], "검은 테에 아래로 밀린 그림자, 투박한 카드", "카드에 검은 테와 아래로 밀린 단단한 그림자가 생겨요. 스티커처럼 투박한 맛이에요.", ["투박한 그림자 카드"],
    [{ id: "app.card", line: "solid", lineW: 2, lineC: c("#111111"), shadow: 6, shadowC: c("#111111"), r: 12 }, { id: "app.card", on: "dark", lineC: c("#EDEDED"), shadowC: c("#000000") }]),
  P("리넨 카드", ["#F3EDE3", "#8D7B68", "🧺"], "옅은 리넨 결 카드", "카드 바탕에 옅은 리넨 결이 깔려 손으로 만진 듯한 질감이 생겨요.", ["리넨 결 카드"],
    [{ id: "app.card", paint: tx("linen", "#F3EDE3", 10), r: 16 }, { id: "app.card", on: "dark", paint: tx("linen", "#2E2A25", 10) }]),
  P("유리 카드 진하게", ["#CFD8DC", "#FFFFFF", "🪞"], "흐림이 더 깊은 유리 카드", "카드가 더 깊게 흐린 유리가 돼요. 사진 배경 위에서 글자가 또렷해요.", ["깊은 유리 카드"],
    [{ id: "app.card", paint: gl("#FFFFFF", 0xC0, 32), r: 20, line: "solid", lineW: 1, lineC: c("#99FFFFFF") }, { id: "app.card", on: "dark", paint: gl("#1C1E26", 0xC0, 32), lineC: c("#26FFFFFF") }]),

  // ---- 단추 (app.button) 2 ----
  P("그라데이션 단추", ["#7F7FD5", "#86A8E7", "🔘"], "보라에서 하늘로 번지는 둥근 단추", "앱 단추가 보라에서 하늘로 번지는 둥근 알약이 돼요.", ["그라데이션 단추"],
    [{ id: "app.button", paint: lg(90, "#7F7FD5", "#86A8E7"), ink: c("#FFFFFF"), r: 48, weight: 800 }, { id: "app.button", on: "dark", paint: lg(90, "#5A5AB8", "#5C7FC4") }]),
  P("도장 단추", ["#FFFFFF", "#A8322D", "🔴"], "붉은 도장처럼 테만 있는 단추", "앱 단추가 붉은 도장처럼 테두리와 글자만 남아요.", ["도장 단추"],
    [{ id: "app.button", fill: c("#A8322D"), fillA: 0, line: "solid", lineW: 2, lineC: c("#A8322D"), ink: c("#A8322D"), r: 8, weight: 900 }, { id: "app.button", on: "dark", lineC: c("#E57373"), ink: c("#E57373") }]),

  // ---- 메모 카드 (memo.card) 5 ----
  P("영수증 메모 카드", ["#FFFFFF", "#9E9E9E", "🧾"], "점선으로 끊긴 영수증 메모", "메모 카드가 영수증 한 장처럼 점선으로 끊기고 제목은 고정폭이에요.", ["영수증 메모"],
    [{ id: "memo.card", fill: c("#FFFFFF"), r: 2, line: "dash", lineW: 1, lineC: c("#9E9E9E") }, { id: "memo.card.title", font: "nanumgothiccoding", weight: 800 },
      { id: "memo.card", on: "dark", fill: c("#1E1F22"), lineC: c("#5F6368") }], { top: true }),
  P("칠판 메모 카드", ["#2F4A3A", "#F5F0DC", "🖍️"], "녹색 칠판에 분필 글씨 메모", "메모 카드가 작은 칠판이 되고 글씨는 분필 손글씨예요.", ["칠판 메모"],
    [{ id: "memo.card", fill: c("#2F4A3A"), r: 8, line: "solid", lineW: 3, lineC: c("#8A5A2B") }, { id: "memo.card.title", font: "gaegu", ink: c("#F5E27A"), weight: 700, size: 17 },
      { id: "memo.card.body", font: "gaegu", ink: c("#F5F0DC") }, { id: "memo.card.date", ink: c("#C8E6C9") }, { id: "memo.card", on: "dark", lineC: c("#5D3A1A") }]),
  P("마감 다가오는 메모", ["#FFF3E0", "#EF6C00", "⏰"], "마감 칸이 있는 메모에 주황 테, 체크 끝나면 흐리게", "마감 칸을 적은 메모는 주황 테두리, 체크를 다 끝낸 메모는 흐려져서 남은 일만 또렷해요.", ["마감 주황 테", "끝낸 메모 흐리게"],
    [{ id: "memo.card", when: '아님 비었나(칸("마감"))', line: "solid", lineW: 2, lineC: c("#EF6C00") }, { id: "memo.card", when: "체크율 >= 100", alpha: 50 }]),
  P("모눈 메모 카드", ["#FAFBFC", "#3E5C76", "📐"], "모눈 종이 메모 카드", "메모 카드 바탕에 옅은 모눈이 깔려 공책에 적은 느낌이에요.", ["모눈 메모"],
    [{ id: "memo.card", paint: pt("grid", ["#3E5C76", 0x1C], "#FAFBFC", 50), r: 6 }, { id: "memo.card", on: "dark", paint: pt("grid", ["#9FB8CF", 0x1C], "#1A1F26", 50) }]),
  P("네온 메모 제목", ["#120024", "#FF4FB5", "✨"], "메모 제목이 분홍 네온으로 빛나요", "메모 카드 제목이 분홍 네온사인처럼 빛나요. 어두운 테마에서 특히 예뻐요.", ["네온 제목"],
    [{ id: "memo.card.title", ink: c("#FF4FB5"), weight: 900, ...glowOf("#FF4FB5", 6) }, { id: "memo.card.title", on: "dark", tshadow: 10 }]),

  // ---- 메모 쓰기 종이 (memo.page) 2 ----
  P("원고지 쓰기 종이", ["#FFFDF7", "#C0392B", "📝"], "메모 쓰는 화면이 붉은 줄 원고지", "메모를 쓰는 종이가 옅은 미색에 붉은 줄 원고지가 돼요.", ["원고지 종이"],
    [{ id: "memo.page", fill: c("#FFFDF7"), font: "nanummyeongjo", ink: c("#2B2B2B") }, { id: "memo.page.rule", line: "solid", lineW: 1, lineC: c("#E8B4AE") },
      { id: "memo.page", on: "dark", fill: c("#22201C"), ink: c("#EDE6DA") }, { id: "memo.page.rule", on: "dark", lineC: c("#6B3A35") }]),
  P("밤 공책 쓰기 종이", ["#161D38", "#9FB4FF", "🌙"], "남색 공책에 점선 줄", "메모를 쓰는 종이가 남색 밤 공책이 되고 줄은 은은한 점선이에요.", ["밤 공책 종이"],
    [{ id: "memo.page", fill: c("#161D38"), ink: c("#E6EBFF"), font: "gowunbatang" }, { id: "memo.page.rule", line: "dot", lineW: 1, lineC: c("#3A4A9A") }, { id: "memo.page", on: "dark", fill: c("#0E1328") }]),

  // ---- 가계부 (money.*) 6 ----
  P("영수증 가계부 줄", ["#FFFFFF", "#9E9E9E", "🧾"], "가계부 줄마다 영수증 점선", "가계부 줄 사이에 영수증 점선이 지나가요.", ["점선 줄"],
    [{ id: "money.row", line: "dash", lineW: 1, lineC: c("#BDBDBD"), r: 0 }, { id: "money.row", on: "dark", lineC: c("#5F6368") }]),
  P("수입·큰 지출 색 줄", ["#E8F5E9", "#E53935", "🚦"], "수입 줄은 초록, 큰 지출 줄은 붉은 기운", "돈이 들어온 줄엔 초록, 큰 지출 줄엔 붉은 기운이 옅게 깔려요. 밝은·어두운 화면 모두 읽혀요.", ["수입 초록 줄", "큰 지출 붉은 줄"],
    [{ id: "money.row", when: '종류 = "수입"', fill: c("#2E9E5B"), fillA: 16, r: 10 }, { id: "money.row", when: '종류 = "지출" 그리고 금액 >= 50000', fill: c("#E53935"), fillA: 16, r: 10 }], { top: true }),
  P("띠 날짜 머리", ["#FFE0B2", "#FB8C00", "🎗️"], "가계부 날짜 머리가 주황 띠", "가계부 날짜 머리가 주황 띠로 굵게 서요. 많이 쓴 날은 띠 아래 붉은 선이 생겨요.", ["주황 띠 날짜", "많이 쓴 날 붉은 선"],
    [{ id: "money.day", fill: c("#FB8C00"), fillA: 22, r: 8, padX: 12, weight: 800 }, { id: "money.day", when: "지출 >= 50000", line: "solid", lineW: 2, lineC: c("#E53935") }]),
  P("유리 날짜 띠", ["#E3F2FD", "#FFFFFF", "🫧"], "가계부 날짜 머리가 유리 띠", "가계부 날짜 머리가 흐린 유리 띠가 돼요.", ["유리 날짜 띠"],
    [{ id: "money.day", paint: gl("#FFFFFF", 0x99, 16), r: 12 }, { id: "money.day", on: "dark", paint: gl("#202330", 0x99, 16) }]),
  P("메시 가계부 머리", ["#A18CD1", "#FBC2EB", "💜"], "가계부 맨 위 합계 머리에 메시 그라데이션", "가계부 맨 위 합계 머리에 보라·분홍 메시 그라데이션이 천천히 흘러요.", ["메시 합계 머리"],
    [{ id: "money.head", paint: "sh.mesh.FFA18CD1,FFFBC2EB,FF8EC5FC.2", r: 22, ink: c("#2A1B3D") }, { id: "money.head", on: "dark", paint: "sh.mesh.FF4B3A6B,FF6B3A5E,FF2E4A6B.2", ink: c("#F3E9FF") }]),
  P("월급 전 반짝 머리", ["#FFF8E1", "#FFB300", "💰"], "월급 사흘 전 가계부 머리가 반짝", "다음 월급날이 사흘 안으로 다가오면 가계부 맨 위 머리가 반짝여요.", ["월급 사흘 전 반짝"],
    [{ id: "money.head", when: "월급까지 >= 0 그리고 월급까지 <= 3", motion: "shimmer", line: "solid", lineW: 2, lineC: c("#FFB300") }], { styles: ROSTER }),

  // ---- 근무·급여 합계 (pay.total) 4 ----
  P("홀로그램 급여 상자", ["#C9D6FF", "#E2E2E2", "🪩"], "빛이 도는 홀로그램 급여 합계", "급여 합계 상자가 홀로그램처럼 빛이 도는 무지갯빛이 되고 반짝여요.", ["홀로그램 합계"],
    [{ id: "pay.total", paint: sg(50, 50, 0, "#C9D6FF", "#FBC2EB", "#B5FFFC", "#FFF1A8", "#C9D6FF"), ink: c("#1E1E2A"), motion: "shimmer", r: 22 }, { id: "pay.total", on: "dark", paint: sg(50, 50, 0, "#5A6BB0", "#9A5F8F", "#4F9C98", "#A8963F", "#5A6BB0"), ink: c("#FFFFFF") }], { styles: ["SHIFT", "DAY", "HOURLY", "FIELD"], top: true }),
  P("칠판 급여 상자", ["#2F4A3A", "#F5E27A", "🧮"], "녹색 칠판에 분필 숫자 합계", "급여 합계가 칠판에 분필로 쓴 숫자가 돼요.", ["칠판 합계"],
    [{ id: "pay.total", fill: c("#2F4A3A"), ink: c("#F5F0DC"), font: "gaegu", r: 8, line: "solid", lineW: 4, lineC: c("#8A5A2B") }, { id: "pay.total", on: "dark", lineC: c("#5D3A1A") }], { styles: ["SHIFT", "DAY", "HOURLY", "FIELD"] }),
  P("209시간 경고 상자", ["#FFF3E0", "#FB8C00", "⏱️"], "이달 209시간을 넘으면 합계 상자에 주황 테", "이달 일한 시간이 209시간을 넘으면 급여 합계 상자에 주황 테두리가 생겨요.", ["209시간 주황 테"],
    [{ id: "pay.total", when: "이달근무시간 > 209", line: "solid", lineW: 3, lineC: c("#FB8C00") }], { styles: ["SHIFT", "DAY", "HOURLY"] }),
  P("주휴 금색 합계", ["#FFF8E1", "#FFC107", "🥇"], "이번 주 15시간을 채우면 합계 글자가 금색", "이번 주 15시간을 넘기면 급여 합계 글자가 금색으로 바뀌어요(주휴수당을 받을 수 있는 주).", ["주 15시간 금색"],
    [{ id: "pay.total", when: "이번주근무시간 >= 15", ink: c("#FFC107") }], { styles: ["HOURLY"] }),

  // ---- 위젯 (widget / widget.cell) 4 ----
  P("밤하늘 남색 위젯", ["#0D2B5C", "#EAF6FF", "🌌"], "홈 화면 위젯 바탕이 깊은 남색", "홈 화면 위젯 바탕이 깊은 밤하늘 남색이 되고 글자는 밝은 하늘색이에요. 어두운 화면에선 한 단계 더 깊어져요.", ["남색 위젯"],
    [{ id: "widget", fill: c("#0D2B5C"), fillA: 92, ink: c("#EAF6FF"), r: 24 }, { id: "widget", on: "dark", fill: c("#071A3A") }], { top: true }),
  P("크림 종이 위젯", ["#F5E6CC", "#5D4037", "📜"], "홈 화면 위젯이 크림빛 종이색", "홈 화면 위젯이 크림빛 종이색이 되고 글자는 짙은 갈색이에요. 어두운 화면에선 짙은 갈색 종이에 밝은 글씨예요.", ["크림 종이 위젯"],
    [{ id: "widget", fill: c("#F5E6CC"), ink: c("#3E2723"), r: 18 }, { id: "widget", on: "dark", fill: c("#3A2E22"), ink: c("#F5E6CC") }]),
  P("먹색 각진 위젯", ["#141210", "#F4F1EC", "⬛"], "홈 화면 위젯이 먹색에 각진 모서리", "홈 화면 위젯이 먹색 바탕에 모서리가 거의 각진 단단한 판이 돼요. 밝은 화면에선 먹색, 어두운 화면에선 더 깊은 검정이에요.", ["먹색 각진 위젯"],
    [{ id: "widget", fill: c("#22201C"), ink: c("#F4F1EC"), r: 4 }, { id: "widget", on: "dark", fill: c("#0E0D0B") }]),
  P("근무 색 위젯 칸", ["#FFFFFF", "#3D5AFE", "🟦"], "홈 화면 위젯 칸이 근무 종류마다 다른 색", "홈 화면 위젯의 날짜 칸이 주간·저녁·야간·쉬는 날마다 다른 색으로 칠해져요.", ["근무 색 위젯 칸"],
    [{ id: "widget.cell", on: "role.day", fill: c("#FFC107"), fillA: 40 }, { id: "widget.cell", on: "role.eve", fill: c("#FF7043"), fillA: 40 },
      { id: "widget.cell", on: "role.night", fill: c("#3D5AFE"), fillA: 40 }, { id: "widget.cell", on: "role.off", fill: c("#9E9E9E"), fillA: 25 }], { styles: ["SHIFT", "HOURLY"] }),
];
