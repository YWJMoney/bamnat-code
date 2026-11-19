package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.stats.FactsSince
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 2.0.0 갈래 H 12묶음: 야간 said only where it reaches (PC-162·PC-210), and figures from the month the app came into use (PC-212). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH12ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    /** [RVH-93] 검토 PC-210: an 일근 without a pay setup is not told of 야간 under 이달 비교; a 교대 still is. */
    @Test fun dayCompareHasNoNight() {
        for ((look, q, scale) in looks) shoot("rvh12_61_day_compare_$look", q, scale, style(WorkStyle.DAY) {
            tab(3, wide = look.startsWith("c") || look.startsWith("d"))
            reveal("${Today.month().monthValue}월 비교", exact = true, across = 0.5f)
            // The card's last line is under the fold at 200%: read on to it.
            reveal("급여를 설정하면 급여도 비교해요.", exact = true, across = 0.5f)
            assertTrue("일근에게 야간 말이 없어요", !shows("야간과 급여도"))
            assertTrue("급여 설정 안내는 급여만: ${shownWords().takeLast(30)}", appears("급여를 설정하면 급여도 비교해요.", exact = true) || appears("급여는 세금 떼기 전 예상이에요.", exact = true))
        })
    }

    /** [RVH-92] 검토 PC-162: an 일근's 근무표 점검 기준 keeps 휴식 and 연속 근무, without the 야간 limits or the 간호 야간 source. */
    @Test fun dayRestRulesHaveNoNights() {
        val seen = mutableMapOf<String, List<String>>()
        for ((look, q, scale) in looks) shoot("rvh12_62_day_rest_rules_$look", q, scale, style(WorkStyle.DAY) {
            content {
                com.bamnat.app.ui.calendar.RestRulesSheet(com.bamnat.core.schedule.RestRules(), com.bamnat.core.schedule.RotationSpeed.NONE,
                    onDismiss = {}, nights = false) {}
            }
            settle()
            seen[look] = shownWords()
        })
        for ((look, words) in seen) {
            val all = words.joinToString(" ")
            assertTrue("$look: 연속 근무 ${words.take(30)}", "연속 근무" in all && "근무 사이 휴식" in all)
            assertTrue("$look: 야간 없음", "야간" !in all && "병원" !in all)
        }
    }

    /** [RVH-95] 검토 HQ-157: today's date circle holds its number at 200% (the photo is the calendar's top). */
    @Test fun todayCircleHoldsItsNumber() {
        for ((look, q, scale) in looks) shoot("rvh12_63_today_circle_$look", q, scale, style(WorkStyle.SHIFT) {
            settle()
        })
    }

    /** [RVH-96] 검토 PC-08: a leave taken with no hire date reads '아직 세지 않았어요', never '-1일' / '남은 -1일'. */
    @Test fun leaveWithoutHireDateIsNotADebt() {
        for ((look, q, scale) in looks) shoot("rvh12_64_leave_no_hire_$look", q, scale, style(WorkStyle.DAY) {
            twoOnTwoOff()
            runBlocking {
                val repo = app.container.scheduleRepository
                val pattern = repo.snapshot.first()
                val day = (3..20).map { Today.date().minusDays(it.toLong()) }.first { !pattern.resolver.resolve(it).isOff }
                repo.setLeave(day, com.bamnat.core.model.Leave())
            }
            content {
                com.bamnat.app.ui.leave.LeaveScreen(com.bamnat.app.ui.leave.LeaveViewModel(app.container, com.bamnat.app.data.PersonalCalendar.MAIN), {}, {})
            }
            settle()
            val words = shownWords().joinToString(" ")
            assertTrue("$look: 아직 세지 않았어요 ${shownWords().take(30)}", "아직 세지 않았어요" in words)
            assertTrue("$look: 마이너스 없음", !Regex("남은 -|^-\\d").containsMatchIn(words) && " -1일" !in words)
        })
    }

    /** [RVH-94] 검토 PC-212: the order filled before the app came into use is not counted; days written by hand move the month back. */
    @Test fun factsStartWhenTheAppDid() {
        val today = LocalDate.of(2026, 11, 19)
        val first = PatternTimeline.first(WorkPattern.Rotation(today, listOf(1L, 1L, null, null)), today)
        val plain = ScheduleSnapshot(emptyList(), listOf(first), emptyList())
        assertEquals(YearMonth.of(2026, 11), FactsSince.month(today, plain))
        assertEquals(LocalDate.of(2026, 11, 1), FactsSince.clip(plain, YearMonth.of(2026, 11)).patterns.single().startDate)
        val written = plain.copy(entries = listOf(DayEntry(LocalDate.of(2026, 8, 3), alarmSkipped = true)))
        assertEquals(YearMonth.of(2026, 8), FactsSince.month(today, written))
        // No install day known (an old phone, a test): nothing is cut.
        assertEquals(first.startDate, FactsSince.clip(plain, FactsSince.month(null, plain)).patterns.single().startDate)
    }

    /** [RVH-99] 검토 PC-40: a 대근 on a rest day is filled in the year and counted as the calendar's summary counts it. */
    @Test fun yearFillsAndCountsWorkOnARestDay() {
        for ((look, q, scale) in looks) shoot("rvh12_65_year_rest_day_cover_$look", q, scale, style(WorkStyle.SHIFT) {
            twoOnTwoOff()
            val today = Today.date().minusMonths(2) // [RVH-99] a month the photo shows without scrolling
            val off = runBlocking {
                val repo = app.container.scheduleRepository
                val pattern = repo.snapshot.first()
                val day = (0..27).map { today.withDayOfMonth(1).plusDays(it.toLong()) }.first { pattern.resolver.resolve(it).isOff }
                repo.addExtra(day, com.bamnat.core.model.ExtraEntry(com.bamnat.core.model.ExtraKind.COVER_ID, LocalTime.of(9, 0), LocalTime.of(18, 0)))
                day
            }
            content { com.bamnat.app.ui.calendar.YearScreen(com.bamnat.app.ui.calendar.YearViewModel(app.container, com.bamnat.app.data.PersonalCalendar.MAIN), {}) }
            settle()
            val s = runBlocking { app.container.scheduleRepository.snapshot.first() }
            val days = (1..today.lengthOfMonth()).associate { today.withDayOfMonth(it) to s.resolver.resolve(today.withDayOfMonth(it)) }
            val summary = days.values.count { it.totalMinutes > 0 }
            assertTrue("$look: 쉬는 날 대근($off)이 센 일에 들어가요", days.getValue(off).isOff && days.getValue(off).totalMinutes > 0)
            assertEquals("$look: 해 달력의 달 일수 = 달력 아래 요약", summary, com.bamnat.app.ui.calendar.yearWorkDays(java.time.YearMonth.from(today), days))
        })
    }

    /** [RVH-100] 검토 PC-216 (조건 10): leaving the 근무 고치기 window with a change asks once; nothing is saved on its own. */
    @Test fun leavingTheKindEditorWithAChangeAsks() {
        for ((look, q, scale) in looks) shoot("rvh12_66_kind_editor_leave_$look", q, scale, style(WorkStyle.SHIFT) {
            val day = runBlocking { app.container.scheduleRepository.shiftTypes.first().first { it.name == "주간" } }
            var saved = 0
            var dismissed = 0
            content { com.bamnat.app.ui.shifts.ShiftTypeEditorDialog(day, onDismiss = { dismissed++ }, onSave = { saved++ }, onDelete = {}) }
            settle()
            // A change, then the X: asked, not saved, not left (the photo is of the question).
            typeInto("이름 (예: 주간, 야간, 오픈)", "주간 B")
            tapText("닫기", exact = true)
            check(appears("저장하지 않고 나갈까요?", exact = true)) { "$look: 나가기 전에 묻지 않았어요 ${shownWords().take(20)}" }
            assertEquals("$look: 묻기 전에 저장했어요", 0, saved)
            assertEquals("$look: 묻기 전에 나갔어요", 0, dismissed)
        })
    }

    /** [RVH-100] '나가기' leaves without saving; with no change the X just leaves, without asking. */
    @Test fun theKindEditorLeavesWithoutSavingOrAsksNothingWhenUnchanged() {
        val (look, q, scale) = looks.first()
        shoot("rvh12_66b_kind_editor_leave_$look", q, scale, style(WorkStyle.SHIFT) {
            val day = runBlocking { app.container.scheduleRepository.shiftTypes.first().first { it.name == "주간" } }
            var saved = 0
            var dismissed = 0
            content { com.bamnat.app.ui.shifts.ShiftTypeEditorDialog(day, onDismiss = { dismissed++ }, onSave = { saved++ }, onDelete = {}) }
            settle()
            tapText("닫기", exact = true)
            assertEquals("바꾼 것이 없으면 묻지 않고 닫아요", 1, dismissed)
            assertTrue("바꾼 것이 없는데 물었어요", !shows("저장하지 않고 나갈까요?", exact = true))
            typeInto("이름 (예: 주간, 야간, 오픈)", "주간 B")
            tapText("닫기", exact = true)
            check(appears("저장하지 않고 나갈까요?", exact = true))
            tapText("나가기", exact = true)
            settle()
            assertEquals("나가기는 저장하지 않아요", 0, saved)
            assertEquals("나가기는 닫아요", 2, dismissed)
        })
    }
}
