package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.stats.FactsSince
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
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
}
