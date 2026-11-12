package com.bamnat.app.a11y

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [A11Y-04] A touch that only a finger can make (a drag, a swipe, a raw tap read with pointerInput) is invisible to
 * TalkBack and Switch Access: semantics carry no OnClick for it (research/1.23-리뷰/M 2-6 E3, 3-4 10번). Every
 * such place must have a way that does show up — a button, or a custom action — and is listed here with it, so
 * a new gesture-only control fails this test until its alternative is named. AccessibilityCheckTest checks what
 * the alternatives say and how big they are.
 */
class GestureAlternativesTest {
    private val ui = File("src/main/java/com/bamnat/app")

    /** File → how the same thing is done without the gesture. */
    private val allowed = mapOf(
        "ShiftTypesScreen.kt" to "끌어서 순서 바꾸기 → 옆의 ‘위로 옮기기·아래로 옮기기’ 버튼",
        "NightTimeline.kt" to "잠 그래프의 소리 점 누르기 → 아래 '들어 볼 소리' 줄의 듣기 단추, 그래프 설명(contentDescription)",
        "BarChart.kt" to "막대 누르면 말풍선 → 그래프 전체 값을 읽어 주는 설명(contentDescription)",
        "CalendarScreen.kt" to "주 보기 밀어 넘기기 → 머리의 이전·다음 주 버튼",
        "DayHeader.kt" to "하루 시트 밀어 넘기기 → 머리의 이전·다음 날 버튼",
        "MonthSwipe.kt" to "근무·급여·가계부·투자·메모 달력 등 옆으로 밀어 달 넘기기(1.39 CALX-08) → 머리의 이전 달·다음 달 버튼(‹ ›)과 달 고르기",
        "MonthGrid.kt" to "끌어서 여러 날 칠하기(달력·한 달 근무 넣기) → 날짜 칸을 하나씩 눌러 칠하기(칸마다 OnClick), 한 달 근무 넣기는 아래 키패드로 한 칸씩",
        // [READ-04] 1.25 U.
        "RosterPhotoViews.kt" to "사진 위에서 날짜 줄·내 줄 누르기 → ‘어느 줄(조)이 내 것인가요?’ 버튼 목록, ‘날짜 줄이 없어요’, 한 달 근무 넣기 키패드",
        // [NAV-06] 1.26 AD.
        // [J133-46] 1.33 모든 조 보기.
        "MemoBoard.kt" to "자유 판 조각 끌기·두 손가락 키우기·돌리기 → 고른 조각 도구 줄의 ‘← 왼쪽·오른쪽 →·↑ 올리기·↓ 내리기·크게·작게·↻ 돌리기’",
        "MemoPhotoBlocks.kt" to "사진 모서리 끌어 크기 바꾸기·크게 보기 확대 → 고른 사진 도구 줄의 ‘작게·반·크게·꽉’, 크게 보기는 닫기 단추",
        "TeamsScreen.kt" to "날짜 창 밀어 넘기기 → 창 머리의 ‘전날·다음 날’ 버튼",
        // [RCP-09] 1.34 색상환.
        "Pickers.kt" to "색 판·색상 띠 끌기 → 위의 색 버튼 줄, 16진수 칸, 띠와 판의 접근성 조절(올리기·내리기로 색상·밝기)",
        // [RCX-30] 1.35 그림 자리 맞추기.
        "PhotoLookRows.kt" to "사진 옮기기·두 손가락 확대 → 네모의 접근성 동작 ‘크게·작게·사진을 왼쪽·오른쪽·위·아래로’, 그대로 두면 가운데",
        // [ALRM-23] 1.35 새 알람 화면.
        "RingingScreen.kt" to "손잡이 밀기 → 양 끝 ‘끄기’·‘N분 뒤’ 누르기, 접근성 동작 ‘끄기·N분 뒤 다시 울림’",
        // [STU-10] 1.35 스튜디오 비교.
        "StudioScreen.kt" to "비교 단추를 누르고 있기 → 한 번 누르기(접근성 동작 ‘반반 비교 켜기·끄기’)로 고치기 전과 지금을 나란히",
        // PC 확인 Z7 1.36 비교 선 끌기.
        "TryOn.kt" to "반반 비교 선 끌기 → 선 손잡이의 접근성 동작 ‘선을 왼쪽으로·오른쪽으로’, 처음엔 가운데",
        // [LAY-11] 1.36 꾸밈 단추.
        "DecorDraw.kt" to "꾸밈을 눌러 규칙 단추 누르기 → 그 부분의 접근성 동작 ‘○○ 꾸밈 단추 누르기’, 같은 단추가 놓인 다른 자리",
        // [LAY-04] 1.36 꾸밈 층 판.
        "DecorEditor.kt" to "판에서 꾸밈 끌기·두 손가락 크기·돌리기·눌러 고르기 → 레이어 목록에서 골라, 고치기의 1dp 화살표·크기·돌리기 버튼, 고급 보기의 숫자 칸",
        // [CNV-13] 1.37 펼친 줄: 바깥을 누르거나 줄 안의 칩을 누르면 접혀요.
        "FoldRow.kt" to "바깥 누르기·칩 누른 뒤 저절로 접기 → 줄 끝의 ‘접기 ▴’ 단추",
        // [BLU-06] 1.36 부품 도구 −/+ 길게 누르기.
        "FreeLookScreen.kt" to "−/+를 누르고 있어 빠르게 바꾸기 → 한 번 누르기(접근성 onClick 한 칸씩), 옆의 밀대",
        // [TAB-02] 1.36 누락 점검 N9 보드 카드 끌어 옮기기.
        "MemoViews.kt" to "보드 카드 손잡이 끌어 다른 칸에 놓기 → 같은 손잡이 한 번 누르기(‘다른 칸으로 옮기기’)로 옮길 칸 목록, 옮긴 뒤 되돌리기",
        "TabBar.kt" to "메뉴를 길게 눌러 끌어 순서 바꾸기·다른 곳 눌러 저장 → 탭의 접근성 동작 ‘메뉴 순서 바꾸기·앞으로 옮기기·뒤로 옮기기·순서 저장’, 안내 줄 누르기·뒤로 가기로 저장, 설정 › 화면과 글자 › 아래 메뉴 채우기·순서의 위·아래 버튼",
        // [BLU-09] 1.36 미리 보기에서 누른 부품 열기.
        // [CNV-02] 1.37 캔버스 두 손가락 확대·이동, 두 번 눌러 맞춤.
        "StudioCanvas.kt" to "두 손가락 확대·이동, 옆 빈 곳 두 번 누르기 → 캔버스 구석의 ‘작게 보기·배율(100%)·크게 보기·고른 곳 확대·맞춤’ 단추, 층 목록에서 부품 고르기",
        // [MKV-01] 1.37 장터 카드 그림 위 누르기 덮개(예시 화면의 누르기·스크롤이 카드를 가로채지 않게).
        "MarketShot.kt" to "그림 누르기 → 그림을 감싼 카드·줄 자체의 누르기(‘테마 보기’ 등 접근성 onClick)",
        // [PAYIN-01] 1.37 명세서 사진 두 손가락 확대·이동·두 번 눌러 확대.
        "PinnedPhoto.kt" to "두 손가락 확대·이동·두 번 누르기 → 사진 위 ‘사진 크게·작게’ 단추와 ‘사진 접기·펴기’",
        // [MKC-02][MKC-03] 1.39 도구 전후 해 보기: 그림·단추 화면을 누르고 있으면 넣기 전.
        "ToolTryout.kt" to "누르고 있으면 넣기 전 모습 → 위의 ‘지금·반반·넣으면’ 칩(부품), ‘지금·넣으면’ 칩(단추), 비교 선은 TryOn의 접근성 동작",
        "PartSpot.kt" to "미리 보기 위 부품 누르기 → 부품 도구의 부품 칩(묶음 제목 아래 모든 부품)과 상태 칩, 고정 줄의 상태 고르기",
        // [RVP-59] 2.0.0 말랑 칸이: 누르면 말랑하게 눌렸다 돌아와요. 하는 일이 없는 꾸밈이라 대안이 따로 필요 없어요.
        "Kani.kt" to "칸이 누르기(말랑하게 눌리는 움직임뿐, 하는 일 없음) → 할 일은 옆의 단추, 칸이는 '칸이, 기분'으로 읽어 줘요",
        // [RVM-18] 2.0.0 PC-46: 작품 미리 그림의 달력 머리는 넣은 뒤와 같은 머리를 그리지만, 누르기는 막아요(하는 일 없음).
        "RecipeStudio.kt" to "미리 그림의 달력 머리 누르기를 받아 버림(하는 일 없음) → 머리는 '○월 달력 머리 미리 그림' 한 줄로 읽어 줘요",
        // 2.0.0 [RVK-24] (총괄 #472 리뷰) Rows that slide (SwipeToDismissBox) are gestures too.
        "LedgerList.kt" to "내역 줄을 밀어 오늘로 복사·지우기 → 줄을 눌러 여는 돈 적기 창의 '오늘 날짜로 복사'·'지우기', 접근성 동작 '오늘로 복사·지우기'",
        "PendingRecords.kt" to "확인 대기 줄을 밀어 확인·지우기 → 줄 오른쪽 ✓ '확인'(48dp), 줄을 눌러 여는 돈 적기 창의 저장·'지우기'",
        "OwedScreen.kt" to "받을 돈 줄을 밀어 다 받음·알림 문자 → 줄을 눌러 여는 창의 '알림 문자'·받은 돈 적기, 접근성 동작 '다 받음·알림 문자 보내기'",
        "InvestAlertsCard.kt" to "찾은 거래 줄을 밀어 적기·넘기기 → 줄 오른쪽 ✓ '적기'(48dp), 줄을 눌러 여는 매매 창의 저장·'넘기기', 접근성 동작 '적기·넘기기'",
    )

    private val gesture = Regex("""\.pointerInput\(|detect(Tap|Drag|HorizontalDrag|VerticalDrag|Transform)Gestures|SwipeToDismissBox\(""")

    @Test fun everyGestureHasAWayTalkBackCanUse() {
        val found = ui.walkTopDown().filter { it.isFile && it.extension == "kt" && gesture.containsMatchIn(it.readText()) }
            .map { it.name }.toSortedSet()
        assertEquals("새 몸짓 전용 조작은 버튼이나 접근성 동작을 함께 두고 여기에 적어요", allowed.keys.toSortedSet(), found)
    }
}
