package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PhoneDayView
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVH-84] 검토 PC-52·HQ-129: the floating 빠른 입력 never lies on the page's words or buttons, and the room under the month
 * (under 모든 조·ⓘ·월) is there only while the button rides in it. Each look's overlaps go to build/reports/rvh11-fab.txt.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH11ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )
    private val found = mutableListOf<String>()

    /** What shows under the button: words and buttons whose box it lies on (by more than 4dp each way), the page itself aside. */
    private fun Screen.under(where: String): Boolean {
        settle()
        val m = app.resources.displayMetrics
        val all = nodes()
        val fab = all.firstOrNull { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "빠른 입력" } ||
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it == "빠른 입력" } }?.boundsInWindow
            ?: return false
        val edge = 4 * m.density
        val hits = all.filter { n ->
            val r = n.boundsInWindow
            val words = n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            words.isNotEmpty() && "빠른 입력" !in words && r.height < m.heightPixels * 0.6f &&
                minOf(r.right, fab.right) - maxOf(r.left, fab.left) > edge && minOf(r.bottom, fab.bottom) - maxOf(r.top, fab.top) > edge
        }.map { n -> (n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString("|").take(30) }
        if (hits.isNotEmpty()) found += "$where: $hits"
        return true
    }

    private fun report(name: String) {
        val f = java.io.File("build/reports/rvh11-fab.txt").apply { parentFile?.mkdirs() }
        f.appendText("== $name\n" + found.joinToString("\n") + "\n")
    }

    /** The month with 오늘의 흐름 open: at the page's top, read on a little, and with the flow's own top up (as I's 결정 19 photos). */
    @Test fun dockedUnderTheMonth() {
        for ((look, q, scale) in looks) shoot("rvh11_55_fab_dock_$look", q, scale, style(WorkStyle.SHIFT) {
            runBlocking { app.container.settingsRepository.update { it.copy(sleepTips = true, calendarOnly = false, viewAsked = true) } }
            settle()
            under("$look 위")
            runCatching { tapAction("오늘의 흐름 펼치기") }
            under("$look 흐름 펼침")
            readOn(250); under("$look 조금 내림")
            scrollDown(1500); under("$look 다시 위")
        })
        report("dock"); assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /** 보기 › 주: a day opened under its week (the day's extra work and its 빼기 at the bottom). */
    @Test fun weekDayOpen() {
        for ((look, q, scale) in looks) shoot("rvh11_56_week_day_$look", q, scale, style(WorkStyle.DAY) {
            runBlocking { app.container.settingsRepository.update { it.copy(phoneDayView = PhoneDayView.WEEK, sleepTips = true, calendarOnly = false, viewAsked = true) } }
            settle()
            tapText(com.bamnat.app.ui.Fmt.dayTitle(today.plusDays(1)), prefix = true)
            under("$look 날 열림")
        })
        report("week"); assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /** 달력만 크게 보기 (the new phone's look): nothing under the month, so no room kept for the button there. */
    @Test fun calendarOnly() {
        val gaps = mutableListOf<String>()
        for ((look, q, scale) in looks) shoot("rvh11_57_calendar_only_$look", q, scale, style(WorkStyle.SHIFT) {
            runBlocking { app.container.settingsRepository.update { it.copy(calendarOnly = true, viewAsked = true) } }
            settle()
            under("$look")
            val m = app.resources.displayMetrics
            val row = nodes().firstOrNull { n -> n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("달력 판 고르기") } }
            gaps += "$look: 보기 줄 아래 ${row?.boundsInWindow?.bottom?.let { Math.round(it / m.density) }}dp"
        })
        java.io.File("build/reports/rvh11-fab.txt").appendText(gaps.joinToString("\n", postfix = "\n"))
        report("only"); assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /**
     * 2.0.0 [RVH-86] 검토 PC-168 (가게: 알람이 없는 날에 '이 날 알람 끄기'): a day's long-press menu offers 알람 끄기 only on a day that
     * rings. 주간 rings here and 야간 does not, as a shop's 영업 kind with no alarm.
     */
    @Test fun holdOffersAlarmOnlyWhereItRings() = shoot("rvh11_58_hold_alarm", FOLDED, style(WorkStyle.SHIFT) {
        val repo = app.container.scheduleRepository
        runBlocking { repo.shiftTypes.first().filter { it.hasTimes }.forEach { repo.saveShiftType(it.copy(alarmEnabled = it.name == "주간")) } }
        settle()
        val snap = runBlocking { repo.snapshot.first() }
        val month = (1..today.lengthOfMonth()).map { today.withDayOfMonth(it) }.map { snap.resolver.resolve(it) }.filter { it.isWorkDay }
        val rings = month.first { com.bamnat.app.ui.calendar.plannedAlarms(snap, it).isNotEmpty() }.date
        val quiet = month.first { com.bamnat.app.ui.calendar.plannedAlarms(snap, it).isEmpty() }.date
        longPressText(com.bamnat.app.ui.Fmt.dayTitle(rings), prefix = true)
        assertTrue("알람이 울리는 ${rings}에는 '이 날 알람 끄기'가 있어요", inPopup("이 날 알람 끄기"))
        back(); settle()
        // Last, so the photo is the quiet day's menu.
        longPressText(com.bamnat.app.ui.Fmt.dayTitle(quiet), prefix = true)
        assertTrue("길게 누른 메뉴가 떠요", inPopup("자세히 열기"))
        assertTrue("알람이 없는 ${quiet}에는 '이 날 알람 끄기'가 없어요", !inPopup("이 날 알람 끄기", ms = 300))
    })

    /**
     * 2.0.0 [RVH-87] 검토 PC-163 (주 평균 12시간 28분 알바에게 '기준 52시간'·'주 52시간 기준'): an 알바 of 월·수·금 4시간 30분 reads
     * 주휴 기준 주 15시간 on 3개월 평균 and 주별 근무시간, and no 52시간; a 교대 calendar keeps its 52.
     */
    @Test fun hourlyWeeksReadFifteen() {
        for ((look, q, scale) in looks) shoot("rvh11_59_hourly_weeks_$look", q, scale, style(WorkStyle.HOURLY) {
            val repo = app.container.scheduleRepository
            runBlocking {
                val off = repo.shiftTypes.first().first { it.isOff }.id
                val part = repo.saveShiftType(com.bamnat.core.model.ShiftType(0, "알바", "알", 0xFF2F80ED,
                    java.time.LocalTime.of(10, 0), java.time.LocalTime.of(14, 30)))
                val start = today.minusMonths(4).with(java.time.DayOfWeek.MONDAY)
                val days = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY)
                repo.savePattern(com.bamnat.core.model.WorkPattern.Weekly(start, java.time.DayOfWeek.entries.associateWith { if (it in days) part else off }))
                // Only the 알바 days: the seed's extra work (a 41-hour week) would rightly bring the 52-hour words back.
                repo.snapshot.first().entries.forEach { repo.restoreDay(it.date, null) }
            }
            settle()
            tab(3, wide = look.startsWith("c") || look.startsWith("d"))
            reveal("주별 근무시간", across = 0.5f)
            assertTrue("주별 머리가 주휴 기준이에요: ${shownWords().take(40)}", shows("주별 근무시간 (주휴 기준 주 15시간)", exact = true))
            assertTrue("52시간 말이 없어요", !shows("52시간"))
        })
    }

    /**
     * 2.0.0 [RVH-89] 검토 PC-12 (가게 영업 고치기 화면에 '근무 시간'·'근로기준법 휴게'): a shop's 영업 kind in its own words, without the
     * worker's 근로기준법 break line or the 야간 가산 break start. The same window for 근무 keeps them.
     */
    @Test fun shopKindEditorSpeaksShop() {
        val open = com.bamnat.core.model.ShiftType(9, "점심 영업", "점", 0xFF00A88F, java.time.LocalTime.of(11, 0), java.time.LocalTime.of(20, 0))
        val seen = mutableMapOf<String, List<String>>()
        for ((look, q, scale) in looks) shoot("rvh11_60_shop_kind_$look", q, scale, style(WorkStyle.BUSINESS) {
            content { com.bamnat.app.ui.shifts.ShiftTypeEditorDialog(open, onDismiss = {}, onSave = {}, onDelete = null, noun = "영업") }
            settle()
            seen[look] = shownWords()
        })
        for ((look, words) in seen) {
            val all = words.joinToString(" ")
            assertTrue("$look: 영업 시간 ${words.take(30)}", "영업 시간" in all)
            assertTrue("$look: 근로기준법 없음", "근로기준법" !in all && "야간" !in all && "근무 시간" !in all)
        }
    }
}
