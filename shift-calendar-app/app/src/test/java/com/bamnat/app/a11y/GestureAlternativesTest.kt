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
        "CalendarScreen.kt" to "주 보기 밀어 넘기기 → 머리의 이전·다음 주 버튼",
        "DayHeader.kt" to "하루 시트 밀어 넘기기 → 머리의 이전·다음 날 버튼",
        "MonthGrid.kt" to "끌어서 여러 날 칠하기(달력·한 달 근무 넣기) → 날짜 칸을 하나씩 눌러 칠하기(칸마다 OnClick), 한 달 근무 넣기는 아래 키패드로 한 칸씩",
        // [READ-04] 1.25 U.
        "RosterPhotoViews.kt" to "사진 위에서 날짜 줄·내 줄 누르기 → ‘어느 줄(조)이 내 것인가요?’ 버튼 목록, ‘날짜 줄이 없어요’, 한 달 근무 넣기 키패드",
        // [NAV-06] 1.26 AD.
        // [J133-46] 1.33 모든 조 보기.
        "TeamsScreen.kt" to "날짜 창 밀어 넘기기 → 창 머리의 ‘전날·다음 날’ 버튼",
        "TabBar.kt" to "메뉴를 길게 눌러 끌어 순서 바꾸기·다른 곳 눌러 저장 → 탭의 접근성 동작 ‘메뉴 순서 바꾸기·앞으로 옮기기·뒤로 옮기기·순서 저장’, 안내 줄 누르기·뒤로 가기로 저장, 설정 › 화면과 글자 › 첫 화면·메뉴 순서의 위·아래 버튼",
    )

    private val gesture = Regex("""\.pointerInput\(|detect(Tap|Drag|HorizontalDrag|VerticalDrag|Transform)Gestures""")

    @Test fun everyGestureHasAWayTalkBackCanUse() {
        val found = ui.walkTopDown().filter { it.isFile && it.extension == "kt" && gesture.containsMatchIn(it.readText()) }
            .map { it.name }.toSortedSet()
        assertEquals("새 몸짓 전용 조작은 버튼이나 접근성 동작을 함께 두고 여기에 적어요", allowed.keys.toSortedSet(), found)
    }
}
