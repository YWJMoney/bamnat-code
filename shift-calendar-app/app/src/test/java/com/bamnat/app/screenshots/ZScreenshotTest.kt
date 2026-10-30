package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.MainActivity
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.core.memo.Memo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [NAV-02][NAV-03][UX-11][UX-12][UX-13][ALRM-06][SHFT-09][BAK-01] 묶음 Z's end-to-end checks and screenshots, numbers
 * 910~929, on the app itself: back goes to the screen before (never the calendar or out of the app by surprise), the
 * calendar's tools keep their places, a long memo's 맨 밑으로, a day sheet that stays open while it is scrolled, the
 * alarm wheels, deleting a shift type with 되돌리기, and moving to a new phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ZScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** The app, opened as [shoot] opens it, with the activity at hand for checks a picture cannot make. */
    private fun open(qualifiers: String, actions: Screen.(ActivityScenario<MainActivity>) -> Unit) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(1f)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle()
            Screen(scenario).actions(scenario)
        }
    }

    /** The tab chosen in the app's bar now, by its name ("근무·급여" for 통계), or null on a screen without the bar. */
    private fun Screen.tabNow(): String? {
        val names = setOf("달력", "가계부", "장부", "메모", "근무·급여", "통계", "설정")
        val chosen = nodes().filter {
            it.config.getOrNull(SemanticsProperties.Role) == Role.Tab && it.config.getOrNull(SemanticsProperties.Selected) == true
        }.mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.takeIf { t -> t in names } }
        return chosen.firstOrNull() ?: if (shownDialogs().isNotEmpty()) "(창 ${shownDialogs().size}개)" else null
    }

    private fun ActivityScenario<MainActivity>.finishing(): Boolean {
        var f = false
        onActivity { f = it.isFinishing }
        return f
    }

    /** Back as the phone sends it to what is on top: an open dialog or sheet first, else the screen. */
    private fun Screen.backOnTop() {
        val top = shownDialogs().lastOrNull()
        if (top != null) { top.onBackPressed(); settle() } else back()
    }

    // ---- 396 [NAV-02] 뒤로 = 바로 전 화면 ----

    @Test fun backGoesToTheTabBeforeAndTwiceClosesFromTheCalendar() = open(FOLDED) { scenario ->
        tab(1, wide = false)
        tab(4, wide = false)
        assertEquals("설정", tabNow())
        back()
        assertEquals("the tab before, not the calendar", "가계부", tabNow())
        back()
        assertEquals("달력", tabNow())
        assertFalse(scenario.finishing())
        // On the calendar, the first back only says so.
        back()
        assertFalse("one back on the calendar does not close the app (it says 한 번 더 누르면 닫혀요)", scenario.finishing())
        // A second back long after is a first one again.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        back()
        assertFalse("a back 3 s later only warns again", scenario.finishing())
        back()
        assertTrue("two backs close it", scenario.finishing())
    }

    @Test fun everySettingsPageBacksToTheSettingsList() = open(FOLDED) { scenario ->
        tab(4, wide = false)
        for (page in SettingsPage.entries) {
            tapText(page.title, exact = true)
            back()
            assertEquals("${page.title} → 뒤로", "설정", tabNow())
            assertTrue("${page.title} → 뒤로: the list, not the page", gone("뒤로", exact = true))
            assertFalse(scenario.finishing())
        }
    }

    @Test fun everyCalendarToolBacksToTheCalendar() = open(FOLDED) { scenario ->
        for (tool in listOf("근무표 공유", "1년 달력", "근무 주기 설정", "새 달력 만들기", "화면 꾸미기", "이번 달 전부 넣기")) {
            tapText("도구", exact = false)
            tapText(tool, exact = true)
            assertNotEquals("$tool must not open the settings tab", "설정", tabNow())
            // A screen, sheet or dialog on top, and back until the calendar: never past it.
            var n = 0
            while (tabNow() != "달력" && n++ < 3) backOnTop()
            while (shownDialogs().isNotEmpty() && n++ < 5) backOnTop()
            assertEquals("$tool → 뒤로", "달력", tabNow())
            assertFalse("$tool → 뒤로 closed the app", scenario.finishing())
        }
    }

    private fun Screen.chosen(text: String): Boolean = nodes().any { n ->
        n.config.getOrNull(SemanticsProperties.Selected) == true &&
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
    }

    @Test fun moneyPagesBackToTheSummaryNotTheCalendar() = open(FOLDED) { scenario ->
        tab(1, wide = false)
        for (page in listOf("내역", "통계")) {
            tapText(page, exact = true)
            assertTrue(chosen(page))
            back()
            assertEquals("$page → 뒤로", "가계부", tabNow())
            assertTrue("$page → 뒤로: 요약", chosen("요약"))
        }
        back()
        assertEquals("달력", tabNow())
        assertFalse(scenario.finishing())
    }

    @Test fun memoSearchClearsBeforeLeavingTheTab() = open(FOLDED) {
        tab(2, wide = false)
        typeInto("제목, 내용, #태그 검색", "회식")
        assertTrue(appears("검색어 지우기", exact = true))
        back()
        assertEquals("메모", tabNow())
        assertTrue("back cleared the search", gone("검색어 지우기", exact = true))
        back()
        assertEquals("달력", tabNow())
    }

    // ---- 397 [UX-10] 값 깜박임 ----

    /** [UX-10] Back on 근무·급여 with nothing changed: the month's numbers are there and not worked out again. */
    @Test fun statsOpenedAgainIsNotWorkedOutAgain() = open(FOLDED) {
        tab(3, wide = false)
        val once = com.bamnat.app.ui.LastValues.computed
        assertTrue("근무·급여 kept its numbers", once > 0)
        tab(0, wide = false)
        tab(3, wide = false)
        assertEquals("same inputs, no new work", once, com.bamnat.app.ui.LastValues.computed)
        assertFalse("no spinner on the second look", nodes().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null })
    }

    /** [NAV-03] 922: the tools in fixed places, the ways out in a row of their own. */
    @Test fun toolSheetFolded() = shoot("922_z_tools_fixed", FOLDED) {
        tapText("도구")
        assertTrue(appears("다른 화면으로", exact = true))
        assertTrue(appears("달력 보기 설정", exact = true))
    }

    /** [NAV-02] The 이번 달 전부 넣기 panel beside the month closes with back; the app stays. */
    @Test fun widePanelClosesWithBack() = open(UNFOLDED) { scenario ->
        tapText("도구")
        tapText("이번 달 전부 넣기", exact = true)
        while (shownDialogs().isNotEmpty()) backOnTop()
        back()
        assertEquals("달력", tabNow())
        assertFalse(scenario.finishing())
    }

    // ---- 398 [UX-11][UX-12] 메모 ----

    private fun longMemo() = runBlocking {
        c.memoRepository.save(Memo(title = "긴 인수인계", body = (1..80).joinToString("\n") { "인수인계 줄 $it" }))
    }

    private fun Screen.openLongMemo() {
        tab(2, wide = false)
        if (!shows("긴 인수인계")) tapText("일반 메모", prefix = true)
        tapText("긴 인수인계")
    }

    @Test fun memoJumpButtonShowsWhileScrolling() = shoot("910_z_memo_jump", FOLDED) {
        longMemo()
        openLongMemo()
        scrollUp(300)
        assertTrue("맨 밑으로 shows while a long memo is scrolled", appears("맨 밑으로", exact = true))
    }

    @Test fun memoJumpButtonGoesToTheEnd() = shoot("911_z_memo_jump_end", FOLDED) {
        longMemo()
        openLongMemo()
        scrollUp(300)
        tapText("맨 밑으로", exact = true)
        assertTrue("the memo's last line shows", appears("인수인계 줄 80"))
    }

    // ---- 399 [UX-13] 하루 창 ----

    /** Down from 60% of the height, where the day's groups scroll (the day's own row at the top drags the sheet, as its handle does). */
    private fun Screen.swipeDown(dp: Int) = scrollUp(-dp)

    @Test fun daySheetStaysOpenWhileItsContentIsScrolled() = shoot("912_z_day_sheet_scrolled", FOLDED) {
        openDay(dayCell)
        assertTrue(shownDialogs().isNotEmpty())
        scrollUp(250)
        // Back up past the day's top in one swipe and its fling: the sheet stays.
        swipeDown(300)
        assertTrue("the day sheet stays open", shownDialogs().isNotEmpty())
    }

    @Test fun daySheetClosesWithASwipeFromItsTop() = open(FOLDED) {
        openDay(dayCell)
        scrollUp(250)
        swipeDown(300)
        assertTrue(shownDialogs().isNotEmpty())
        // Now at the top, a new swipe down closes it as before.
        swipeDown(300)
        assertTrue("a swipe from the top closes the day", shownDialogs().isEmpty())
    }

    // ---- 400 [ALRM-06] 알람 휠 ----

    private fun Screen.dayShiftAlarm() {
        settingsPage("내 근무")
        tapText("근무 종류와 시간", exact = true)
        tapText("주간", exact = true)
        reveal("알람 추가")
        tapText("알람 추가", exact = true)
    }

    @Test fun alarmWheelsByClock() = shoot("914_z_alarm_clock", FOLDED) {
        dayShiftAlarm()
        assertTrue(appears("울릴 시각", exact = true))
        assertTrue(appears("에 울려요"))
    }

    @Test fun alarmWheelsBeforeTheStart() = shoot("915_z_alarm_before", FOLDED) {
        dayShiftAlarm()
        tapText("시작 몇 시간 전", exact = true)
        assertTrue("시작 몇 시간 전 is chosen", nodes().any { n ->
            n.config.getOrNull(SemanticsProperties.Selected) == true &&
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "시작 몇 시간 전" }
        })
    }

    @Test fun alarmPickedOnTheWheelsIsSaved() = shoot("916_z_alarm_saved", FOLDED) {
        dayShiftAlarm()
        tapText(Fmt.offset(90), exact = true)
        tapText("추가", exact = true)
        tapText("저장", exact = true)
        val day = runBlocking { c.scheduleRepository.shiftTypes.first().first { it.name == "주간" } }
        assertTrue("90분 전 is on 주간: ${day.alarms}", day.alarms.any { it.offsetMinutes == 90 })
    }

    // ---- 401 [SHFT-09] 근무 종류 삭제 ----

    private fun Screen.deleteNight() {
        settingsPage("내 근무")
        tapText("근무 종류와 시간", exact = true)
        tapText("야간 삭제", exact = true)
    }

    @Test fun deleteAsksWhereTheShiftIsUsed() = shoot("917_z_delete_ask", FOLDED) {
        deleteNight()
        assertTrue(appears("‘야간’ 근무를 삭제할까요?"))
        assertTrue(appears("근무 주기에 들어 있어요"))
    }

    @Test fun deleteMovesToAnotherTypeAndUndoPutsItBack() = shoot("918_z_delete_undo", FOLDED) {
        val before = runBlocking { c.scheduleRepository.snapshot.first() }
        val night = before.shiftTypes.first { it.name == "야간" }.id
        deleteNight()
        tapText("주간으로 바꾸기", exact = true)
        tapText("삭제", exact = true)
        val gone = runBlocking { c.scheduleRepository.snapshot.first() }
        assertTrue(gone.shiftTypes.none { it.id == night })
        assertTrue("the work order has 주간 where 야간 was", gone.resolver.resolve(today).shiftType != null)
        assertTrue(appears("되돌리기", exact = true))
        tapText("되돌리기", exact = true)
        val back = runBlocking { c.scheduleRepository.snapshot.first() }
        assertEquals(before.shiftTypes, back.shiftTypes)
        assertEquals(before.patterns, back.patterns)
        assertEquals(before.entries, back.entries)
    }

    // ---- 402 [BAK-01] 새 휴대폰으로 옮기기 ----

    @Test fun moveToANewPhone() = shoot("920_z_move_phone", FOLDED) {
        settingsPage("데이터")
        tapText("새 휴대폰으로 옮기기", exact = true)
        assertTrue(appears("옮겨지는 것", exact = true))
        assertTrue(appears("이 휴대폰에만 남는 것", exact = true))
    }

    @Test fun moveToANewPhoneHugeText() = shoot("921_z_move_phone_huge_text", PHONE, fontScale = 2f) {
        settingsPage("데이터")
        tapText("새 휴대폰으로 옮기기", exact = true)
    }

    // ---- Z 후속 [NAV-04] 남은 뒤로 가기 ----

    /** 이번 달 전부 넣기 as a sheet: back with a day typed asks, 취소 keeps the days, 버리고 닫기 closes. */
    @Test fun typingSheetAsksBeforeTypedDaysGo() = shoot("923_z_typing_discard_ask", FOLDED) {
        var closed = 0
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.DutyTypingSheet(snap, java.time.YearMonth.of(2026, 11), onDismiss = { closed++ }, onSave = {}) }
        settle()
        // Nothing typed: back closes at once, as before.
        backOnTop()
        assertEquals(1, closed)
        content { com.bamnat.app.ui.calendar.DutyTypingSheet(snap, java.time.YearMonth.of(2026, 11), onDismiss = { closed++ }, onSave = {}) }
        settle()
        tapText("주간 넣기", exact = true, scrolls = 0)
        tapText("야간 넣기", exact = true, scrolls = 0)
        backOnTop()
        assertTrue("back asks first", appears("넣은 2일을 버릴까요?", exact = true))
        assertEquals("nothing closed yet", 1, closed)
        tapText("취소", exact = true, scrolls = 0)
        assertTrue("the ask is gone", gone("넣은 2일을 버릴까요?", exact = true))
        // The days are still there: 저장 still counts them.
        reveal("2일 저장", exact = true)
        // 닫기 asks the same; 버리고 닫기 closes.
        tapText("닫기", exact = true)
        assertTrue(appears("넣은 2일을 버릴까요?", exact = true))
        tapText("버리고 닫기", exact = true, scrolls = 0)
        assertEquals(2, closed)
        content { com.bamnat.app.ui.calendar.DutyTypingSheet(snap, java.time.YearMonth.of(2026, 11), onDismiss = { closed++ }, onSave = {}) }
        settle()
        tapText("주간 넣기", exact = true, scrolls = 0)
        backOnTop()
    }

    /** Unfolded, the same keypad beside the month: back with days typed asks; with none it closes the panel. */
    @Test fun widePanelAsksBeforeTypedDaysGo() = open(UNFOLDED) { scenario ->
        tapText("도구")
        tapText("이번 달 전부 넣기", exact = true)
        repeat(5) { if (shownDialogs().isNotEmpty()) backOnTop() }
        assertTrue("every dialog closes on back: ${shownDialogs().map { it.javaClass.simpleName }}", shownDialogs().isEmpty())
        tapText("주간 넣기", exact = true)
        back()
        assertTrue("back asks first", appears("넣은 1일을 버릴까요?", exact = true))
        tapText("버리고 닫기", exact = true, scrolls = 0)
        assertTrue("the panel closed", gone("주간 넣기", exact = true))
        assertEquals("달력", tabNow())
        assertFalse(scenario.finishing())
    }

    /** 한 달 근무 넣기 (folded): back or the arrow with days typed asks; 버리고 나가기 goes back to the calendar. */
    @Test fun rosterScreenAsksBeforeLeaving() = shoot("924_z_roster_leave_ask", FOLDED) {
        tapText("도구")
        tapText("이번 달 전부 넣기", exact = true)
        assertTrue(appears("한 달 근무 넣기", exact = true))
        tapText("주간 넣기", exact = true)
        back()
        assertTrue("back asks first", appears("넣지 않고 나갈까요?", exact = true))
        tapText("취소", exact = true, scrolls = 0)
        assertTrue("still on the screen", appears("한 달 근무 넣기", exact = true))
        tapText("뒤로", exact = true, scrolls = 0)
        assertTrue("the arrow asks too", appears("넣지 않고 나갈까요?", exact = true))
        tapText("버리고 나가기", exact = true, scrolls = 0)
        assertEquals("달력", tabNow())
        assertTrue("nothing typed: back leaves at once", gone("넣지 않고 나갈까요?", exact = true))
        tapText("도구")
        tapText("이번 달 전부 넣기", exact = true)
        tapText("주간 넣기", exact = true)
        back()
    }

    /** "무슨 일 하세요?" and the first setup's first question: one back only warns, a second closes the app. */
    @Test fun theFirstSetupClosesOnlyOnASecondBack() {
        c.consentStore.forget()
        c.consentStore.availableForTest = setOf(com.bamnat.core.consent.ConsentItem.CALENDAR)
        runBlocking { c.scheduleRepository.clearPattern() }
        c.settingsRepository.update { it.copy(workStyle = null) }
        open(FOLDED) { scenario ->
            assertTrue(appears("어떤 일을 하고 계신가요?"))
            back()
            assertFalse("one back on the very first screen does not close the app", scenario.finishing())
            assertTrue(appears("어떤 일을 하고 계신가요?"))
            tapText("백업이나 회사 파일로 시작할게요", exact = true)
            tapText("허용은 나중에 할게요")
            assertTrue(appears("어떻게 일하세요?"))
            back()
            assertFalse("one back on the first question does not close the app", scenario.finishing())
            assertTrue(appears("어떻게 일하세요?"))
            back()
            assertTrue("a second back closes it", scenario.finishing())
        }
    }

    /** 받을 돈: back closes the search (and its words) first, then leaves. */
    @Test fun owedSearchClosesBeforeLeaving() = open(FOLDED) {
        var left = 0
        val vm = com.bamnat.app.ui.money.OwedViewModel(c)
        content { com.bamnat.app.ui.money.OwedScreen(vm, wide = false, onBack = { left++ }, today = today) }
        settle()
        tapText("찾기", exact = true, scrolls = 0)
        typeInto("이름, 무슨 돈, 메모로 찾기", "민지")
        assertTrue(appears("지우기", exact = true))
        back()
        assertTrue("back closed the search", gone("지우기", exact = true))
        assertEquals("and did not leave", 0, left)
    }

    // ---- Z 후속 [BAK-02] 복원 즉시 반영 ----

    /** A backup with 목록 보기 brought back while the calendar is open shows the list at once, without a restart. */
    @Test fun aRestoredListViewShowsAtOnce() {
        val view = app.getSharedPreferences("calendar_view", android.content.Context.MODE_PRIVATE)
        view.edit().putBoolean("list", true).commit()
        val file = runBlocking { c.backupManager.export() }
        view.edit().putBoolean("list", false).commit()
        open(FOLDED) {
            assertTrue("the month as a grid", appears("목록으로 보기", exact = true))
            runBlocking { c.backupManager.restore(file) }
            settle()
            assertTrue("the list, at once", appears("달력으로 보기", exact = true))
        }
    }
}
