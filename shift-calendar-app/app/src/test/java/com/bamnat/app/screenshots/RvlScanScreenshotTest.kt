package com.bamnat.app.screenshots

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.imports.CalendarImporter
import com.bamnat.app.imports.CalendarSource
import com.bamnat.app.ui.RecentFill
import com.bamnat.app.ui.imports.AttendanceImportViewModel
import com.bamnat.app.ui.imports.CalendarImportViewModel
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.core.imports.DeviceCalendar
import com.bamnat.core.imports.DeviceEvent
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 2.0.0 [RVL-18][RVL-20][RVL-21][RVL-22][RVL-26][RVL-30] 갈래 L 2차의 화면 (디자인 품질 조건): 달력 위 '방금 넣은 것' 띠, 이어서 보여 주는
 * 한 달 근무 넣기, 짧은 글자를 짐작한 휴가부터 묻는 카드, 이름이 같은 두 줄, 여러 사람 근태 파일의 '누구 근태를 넣을까요?', 이름 열 없는
 * 파일의 주의 한 줄, 캘린더의 연차·여름휴가가 휴가로 날마다 들어가는 목록과 읽지 못했을 때. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlScanScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    // ---- RVL-18: 방금 넣은 것, over the calendar for 10분 ----
    private fun recentFill(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        RecentFill.recordKeeping("근태 12일을 달력에 넣었어요", PersonalCalendar.MAIN, c.appScope) { 0 }
        settle()
        assertTrue(appears("근태 12일을 달력에 넣었어요"))
    }

    @Test fun recentFillFoldLight() = shoot("rvl18_recent_fill_fold_light_100", FOLDED, recentFill())
    @Test fun recentFillFoldDarkHuge() = shoot("rvl18_recent_fill_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, recentFill())
    @Test fun recentFillOpenLight() = shoot("rvl18_recent_fill_open_light_100", UNFOLDED, recentFill())

    // ---- RVL-20: 한 달 근무 넣기 brought back after the app closed ----
    private fun resumed(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        val day = runBlocking { c.scheduleRepository.shiftTypes.first().first { !it.isOff } }
        val first = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        first.tap(1)
        repeat(4) { first.put(com.bamnat.core.imports.DutyCell.Shift(day.id)) }
        val draft = File(app.noBackupFilesDir, "roster_drafts/${PersonalCalendar.MAIN}.json")
        waitFor("the draft written") { draft.exists() }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        assertTrue(appears("적던 근무표를 이어서 보여 드려요"))
    }

    @Test fun resumedFoldLight() = shoot("rvl20_resumed_fold_light_100", FOLDED, resumed())
    @Test fun resumedFoldDarkHuge() = shoot("rvl20_resumed_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, resumed())
    @Test fun resumedOpenLight() = shoot("rvl20_resumed_open_light_100", UNFOLDED, resumed())

    // ---- RVL-21 / RVL-22: a pasted ward roster ----
    private fun pasted(text: String, then: Screen.(DutyRosterViewModel) -> Unit): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        vm.readText(text)
        waitFor("the roster read") { vm.ui.value.rows != null }
        then(vm)
        settle()
    }

    private val days = (1..30).joinToString(" ")
    private fun line(name: String, cells: List<String>) = "$name " + (0 until 30).joinToString(" ") { cells[it % cells.size] }

    /** 'V' and 'AM' on a ward roster: asked once, the leave it most likely is first. */
    private fun guessed(): Screen.() -> Unit = pasted(
        "2026년 11월 근무표\n이름 $days\n" + line("김민지", listOf("주", "주", "V", "야", "야", "휴", "AM", "휴")) + "\n" +
            line("박수진", listOf("야", "야", "휴", "휴", "주", "주", "주", "V")),
    ) { vm ->
        vm.pick(vm.ui.value.rows!!.indexOfFirst { it.label == "김민지" })
        settle()
        reveal("인가요, 근무인가요?")
        // 'V' asked as 연차 first: one 연차 chip, not that and the plain 연차 after the kinds of work as well.
        val chips = nodes().count { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.lastOrNull()?.text == "연차"
        }
        assertEquals(1, chips)
    }

    @Test fun guessedFoldLight() = shoot("rvl21_guessed_fold_light_100", FOLDED, guessed())
    @Test fun guessedFoldDarkHuge() = shoot("rvl21_guessed_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, guessed())
    @Test fun guessedOpenLight() = shoot("rvl21_guessed_open_light_100", UNFOLDED, guessed())

    /** Two 김민지 on one roster: two lines, and which is mine is asked. */
    private fun twins(): Screen.() -> Unit = pasted(
        "2026년 11월 근무표\n이름 $days\n" + line("김민지", listOf("주", "주", "야", "야", "휴", "휴")) + "\n" +
            line("김민지", listOf("야", "휴", "휴", "주", "주", "야")) + "\n" + line("박수진", listOf("휴", "주", "주", "야", "야", "휴")),
    ) { vm ->
        assertEquals(listOf("김민지", "김민지 (2)", "박수진"), vm.ui.value.rows!!.map { it.label })
    }

    @Test fun twinsFoldLight() = shoot("rvl22_twins_fold_light_100", FOLDED, twins())
    @Test fun twinsFoldDarkHuge() = shoot("rvl22_twins_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, twins())

    // ---- RVL-26: an attendance file of several people ----
    private fun attendance(csv: String, then: Screen.(AttendanceImportViewModel) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.DAY) {
        val f = File(app.cacheDir, "근태.csv").apply { writeText(csv) }
        val vm = AttendanceImportViewModel(c, PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.imports.AttendanceImportScreen(vm, onBack = {}) }
        settle()
        vm.open(Uri.fromFile(f))
        waitFor("the file read") { vm.ui.value.rows.isNotEmpty() && (vm.ui.value.people.isNotEmpty() || vm.ui.value.days.isNotEmpty()) }
        then(vm)
        settle()
    }

    private val many = buildString {
        appendLine("성명,일자,출근,퇴근")
        (2..6).forEach { d ->
            appendLine("김가나,2026-11-0$d,09:00,18:00")
            appendLine("박다라,2026-11-0$d,10:00,19:30")
            appendLine("이마바,2026-11-0$d,07:00,16:00")
        }
    }

    private fun asking(): Screen.() -> Unit = attendance(many) { vm ->
        assertEquals(listOf("김가나", "박다라", "이마바"), vm.ui.value.people)
        assertTrue(appears("누구 근태를 넣을까요?"))
    }

    @Test fun askingFoldLight() = shoot("rvl26_who_fold_light_100", FOLDED, asking())
    @Test fun askingFoldDarkHuge() = shoot("rvl26_who_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, asking())
    @Test fun askingOpenLight() = shoot("rvl26_who_open_light_100", UNFOLDED, asking())
    @Test fun pickedFoldLight() = shoot("rvl26_who_picked_fold_light_100", FOLDED, attendance(many) { vm ->
        vm.pickWho("박다라")
        waitFor("박다라's days") { vm.ui.value.days.isNotEmpty() }
        assertTrue(appears("박다라 님 근태를 넣어요"))
    })

    /** No 이름 column, and days of several rows: said once, with where to say which column is the name. */
    @Test fun sharedFoldLight() = shoot("rvl26_shared_fold_light_100", FOLDED, attendance(buildString {
        appendLine("일자,출근,퇴근")
        (2..6).forEach { d -> appendLine("2026-11-0$d,09:00,13:00"); appendLine("2026-11-0$d,14:00,18:00") }
    }) { assertTrue(appears("같은 날 줄이 5일 있어요")) })

    // ---- RVL-30: the phone's calendar ----
    private val phone = DeviceCalendar(1, "내 캘린더", "My calendar", "LOCAL")

    private fun calendar(events: List<DeviceEvent>? , then: Screen.(CalendarImportViewModel) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.DAY) {
        val source = object : CalendarSource {
            override fun allowed() = true
            override fun calendars() = listOf(phone)
            override fun events(calendarIds: Set<Long>, from: LocalDate, toInclusive: LocalDate) = events ?: error("the calendar app stopped")
        }
        val importer = CalendarImporter(app, source, c.scheduleRepository, c.memoRepository, c.database.importedEventDao())
        val vm = CalendarImportViewModel(c, importer)
        content { com.bamnat.app.ui.imports.CalendarImportScreen(vm, onBack = {}) }
        settle()
        waitFor("the calendars") { vm.ui.value.calendars.isNotEmpty() }
        vm.read()
        waitFor("the events") { vm.ui.value.proposals != null }
        then(vm)
        settle()
    }

    private fun at(m: Int, d: Int, h: Int = 0) = LocalDateTime.of(2026, m, d, h, 0)
    private val leaveEvents get() = listOf(
        DeviceEvent(1, 1, "연차", at(11, 13), at(11, 14), allDay = true),
        DeviceEvent(2, 1, "오전 반차", at(11, 17), at(11, 18), allDay = true),
        DeviceEvent(3, 1, "여름휴가", at(11, 19), at(11, 22), allDay = true),
        DeviceEvent(4, 1, "제주 여행", at(11, 27), at(11, 30), allDay = true),
        DeviceEvent(5, 1, "치과", at(11, 24, 15), at(11, 24, 16)),
    )

    private fun leave(): Screen.() -> Unit = calendar(leaveEvents) { vm ->
        assertEquals(5, vm.ui.value.proposals!!.count { it.kind == com.bamnat.core.imports.CalendarImport.As.LEAVE })
        reveal("여름휴가")
    }

    @Test fun leaveFoldLight() = shoot("rvl30_leave_fold_light_100", FOLDED, leave())
    @Test fun leaveFoldDarkHuge() = shoot("rvl30_leave_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, leave())
    @Test fun leaveOpenLight() = shoot("rvl30_leave_open_light_100", UNFOLDED, leave())
    /**
     * Reading failed: said as that, with no greyed "가져올 새 일정이 없어요" under it saying otherwise. #476 사진 검토(HQ-61): on screen without
     * scrolling (right under the card on top, before the settings), with its own 다시 읽기.
     */
    private fun failed(): Screen.() -> Unit = calendar(null) {
        assertTrue(appears("캘린더를 읽지 못했어요"))
        assertTrue(appears("다시 읽기", exact = true))
        assertTrue(gone("가져올 새 일정이 없어요"))
    }

    @Test fun failedFoldLight() = shoot("rvl30_failed_fold_light_100", FOLDED, failed())
    @Test fun failedFoldDarkHuge() = shoot("rvl30_failed_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, failed())

    // ---- RVL-17: a pay read that would turn a 월급 into a 시급, said against today's and left off ----
    private fun payCaution(): Screen.() -> Unit = {
        val now = com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.MONTHLY, 2_508_000)
        val hourly = com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.HOURLY, 10_320)
        content {
            val state = com.bamnat.app.ui.pay.rulesheet.rememberRuleSheetState {
                com.bamnat.core.pay.RuleSheet.read(listOf(com.bamnat.core.pay.RuleFinding("시급 10,320원", hourly, 0.9)), current = now)
            }
            val host = androidx.compose.runtime.remember {
                com.bamnat.app.ui.pay.rulesheet.RuleSheetHost(com.bamnat.app.ui.pay.PhotoCellsLogic.blank,
                    com.bamnat.app.data.ScheduleSnapshot(emptyList(), emptyList(), emptyList()), nov)
            }
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.bamnat.app.ui.pay.rulesheet.RuleSheetCells(state, host)
            }
        }
        settle()
        assertTrue(appears("맞을 때만 켜 주세요"))
    }

    @Test fun payCautionFoldLight() = shoot("rvl17_pay_caution_fold_light_100", FOLDED, payCaution())
    @Test fun payCautionFoldDarkHuge() = shoot("rvl17_pay_caution_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, payCaution())

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
    }
}
